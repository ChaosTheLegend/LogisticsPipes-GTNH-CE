package logisticspipes.pipes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import logisticspipes.LogisticsPipes;
import logisticspipes.api.IMUICompatiblePipeV2;
import logisticspipes.crafting.ItemMemoryChip;
import logisticspipes.crafting.ModulePatternCrafting;
import logisticspipes.crafting.PatternCraftingHudState;
import logisticspipes.crafting.PatternCraftingTargetSelector;
import logisticspipes.crafting.PipeFluidPatternSatelliteLogistics;
import logisticspipes.crafting.PipeItemsPatternSatelliteLogistics;
import logisticspipes.crafting.legacy.template.ICraftingTemplate;
import logisticspipes.crafting.v3.mc.IV3Crafter;
import logisticspipes.crafting.v3.mc.IV3CrafterHolder;
import logisticspipes.gui.hud.HUDPatternCrafting;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.pipes.patterncrafting.PipePatternCraftingMui;
import logisticspipes.interfaces.IChangeListener;
import logisticspipes.interfaces.IHeadUpDisplayRenderer;
import logisticspipes.interfaces.IHeadUpDisplayRendererProvider;
import logisticspipes.interfaces.IOrderManagerContentReceiver;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.ICraftItems;
import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.interfaces.routing.IFluidSink;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.interfaces.routing.IRequireReliableTransport;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.hud.HUDStartWatchingPacket;
import logisticspipes.network.packets.hud.HUDStopWatchingPacket;
import logisticspipes.network.packets.orderer.OrdererManagerContent;
import logisticspipes.network.packets.orderer.PatternCraftingHudContent;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.basic.fluid.FluidRoutedPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.IPromise;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTreeNode;
import logisticspipes.request.resources.IResource;
import logisticspipes.routing.ItemRoutingInformation;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.order.LogisticsFluidOrderManager;
import logisticspipes.routing.order.LogisticsOrder;
import logisticspipes.routing.order.LogisticsOrderManager;
import logisticspipes.security.SecuritySettings;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.transport.PipeFluidTransportLogistics;
import logisticspipes.utils.AdjacentTile;
import logisticspipes.utils.InventoryHelper;
import logisticspipes.utils.PlayerCollectionList;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import lombok.Getter;

/**
 * Pattern crafting pipe that can stage item and fluid ingredients while still behaving like an item crafting pipe for
 * normal order-manager and HUD interactions.
 * <p>
 * The pipe extends {@link FluidRoutedPipe} so routed fluid containers reach the fluid transport hook, but raw tank
 * insertion is disabled. Fluid ingredients are accepted through the reliable item-arrival path and buffered by the
 * crafting module as pattern ingredients.
 */
public class PipeItemsPatternCraftingLogistics extends FluidRoutedPipe
        implements ICraftItems, IRequireReliableTransport, IFluidSink, IHeadUpDisplayRendererProvider, IChangeListener,
        IOrderManagerContentReceiver, IMUICompatiblePipeV2, IV3CrafterHolder {

    public enum BlockingMode {
        OFF,
        BLOCKING,
        SMART
    }

    private static final String LINKED_PATTERN_SATELLITES_TAG = "linkedPatternSatelliteIds";
    private static final String LINKED_PATTERN_SATELLITE_UUIDS_TAG = "linkedPatternSatelliteUuids";
    private static final String LINKED_PATTERN_FLUID_SATELLITES_TAG = "linkedPatternFluidSatelliteIds";
    private static final String LINKED_PATTERN_FLUID_SATELLITE_UUIDS_TAG = "linkedPatternFluidSatelliteUuids";

    private final ModulePatternCrafting module;
    private final LogisticsFluidOrderManager fluidOrderManager;
    private final PatternCraftingTargetSelector targetSelector;
    public final LinkedList<ItemIdentifierStack> oldList = new LinkedList<>();
    public final LinkedList<ItemIdentifierStack> displayList = new LinkedList<>();
    private PatternCraftingHudState oldHudState = PatternCraftingHudState.empty();
    @Getter
    private PatternCraftingHudState hudState = PatternCraftingHudState.empty();
    public final PlayerCollectionList localModeWatchers = new PlayerCollectionList();
    private final HUDPatternCrafting HUD = new HUDPatternCrafting(this);
    private boolean doContentUpdate = true;
    private final Set<Integer> linkedPatternSatelliteIds = new TreeSet<>();
    private final Set<String> linkedPatternSatelliteUuids = new TreeSet<>();
    private final Set<Integer> linkedPatternFluidSatelliteIds = new TreeSet<>();
    private final Set<String> linkedPatternFluidSatelliteUuids = new TreeSet<>();

    public PipeItemsPatternCraftingLogistics(Item item) {
        super(new PipeFluidTransportLogistics() {

            @Override
            public boolean canPipeConnect(TileEntity tile, ForgeDirection dir) {
                if (super.canPipeConnect(tile, dir)) {
                    return true;
                }
                if (SimpleServiceLocator.pipeInformationManager.isPipe(tile, false)) {
                    return false;
                }
                if (tile instanceof IFluidHandler handler) {
                    return handler.getTankInfo(dir.getOpposite()) != null
                            && handler.getTankInfo(dir.getOpposite()).length > 0;
                }
                return false;
            }
        }, item);
        module = new ModulePatternCrafting(this);
        _orderItemManager = new logisticspipes.routing.order.LogisticsItemOrderManager(this, this);
        fluidOrderManager = new LogisticsFluidOrderManager(this, this);
        targetSelector = new PatternCraftingTargetSelector(this);
        throttleTime = 40;
    }

    @Override
    protected void onAllowedRemoval() {
        module.onAllowedRemoval();
    }

    @Override
    public void onNeighborBlockChange(int blockId) {
        targetSelector.clearCache();
        module.onCraftingTargetChanged();
        super.onNeighborBlockChange(blockId);
    }

    @Override
    public boolean disconnectPipe(TileEntity tile, ForgeDirection dir) {
        if (SimpleServiceLocator.pipeInformationManager.isPipe(tile, false)) {
            return false;
        }
        if (tile instanceof IInventory || tile instanceof IFluidHandler) {
            return !targetSelector.isSelectedInventory(tile, dir);
        }
        return true;
    }

    @Override
    protected boolean handleClick(EntityPlayer entityplayer, SecuritySettings settings) {
        if (entityplayer.isSneaking() && entityplayer.getCurrentEquippedItem() != null
                && entityplayer.getCurrentEquippedItem().getItem() == LogisticsPipes.LogisticsMemoryChip) {
            if (MainProxy.isServer(entityplayer.worldObj)) {
                if (settings == null || settings.openGui) {
                    if (!hasAdvancedSatelliteUpgrade()) {
                        entityplayer
                                .addChatComponentMessage(new ChatComponentText("Advanced Satellite Upgrade required"));
                        return true;
                    }
                    ItemMemoryChip.PatternSatelliteMode mode = ItemMemoryChip
                            .getPatternSatelliteMode(entityplayer.getCurrentEquippedItem());
                    if (mode == ItemMemoryChip.PatternSatelliteMode.APPLY_LAST_TO_RECIPE) {
                        int changed = applyLastMemorySatelliteToRecipe(entityplayer.getCurrentEquippedItem());
                        entityplayer.addChatComponentMessage(
                                new ChatComponentText(
                                        changed == 0 ? "No pattern ingredients changed"
                                                : "Assigned " + changed
                                                        + " ingredient slot"
                                                        + (changed == 1 ? "" : "s")
                                                        + " to the last satellite"));
                    } else {
                        int added = addLinkedPatternSatellites(
                                ItemMemoryChip.getPatternSatellites(entityplayer.getCurrentEquippedItem()));
                        entityplayer.addChatComponentMessage(
                                new ChatComponentText(
                                        added == 0 ? "No new pattern satellites linked"
                                                : "Linked " + added + " pattern satellite" + (added == 1 ? "" : "s")));
                    }
                } else {
                    entityplayer.addChatComponentMessage(new ChatComponentTranslation("lp.chat.permissiondenied"));
                }
            }
            return true;
        }
        if (!entityplayer.isSneaking() || !SimpleServiceLocator.toolWrenchHandler.isWrenchEquipped(entityplayer)
                || !SimpleServiceLocator.toolWrenchHandler.canWrench(entityplayer, getX(), getY(), getZ())) {
            return false;
        }
        if (MainProxy.isServer(entityplayer.worldObj)) {
            if (settings == null || settings.openGui) {
                targetSelector.cycleConnectedInventory(entityplayer);
            } else {
                entityplayer.addChatComponentMessage(new ChatComponentTranslation("lp.chat.permissiondenied"));
            }
        }
        SimpleServiceLocator.toolWrenchHandler.wrenchUsed(entityplayer, getX(), getY(), getZ());
        return true;
    }

    public AdjacentTile getConnectedInventoryTile() {
        return targetSelector.getConnectedInventoryTile();
    }

    /**
     * Returns whether the selected adjacent crafting target should be drawn as connected.
     * <p>
     * The actual transport connection stays closed so foreign blocks cannot push passive items into this pipe.
     */
    public boolean shouldRenderCraftingTargetConnection(ForgeDirection side) {
        AdjacentTile selected = getConnectedInventoryTile();
        return selected != null && selected.orientation == side;
    }

    public boolean isPatternSatelliteLinked(int satelliteId) {
        return linkedPatternSatelliteIds.contains(satelliteId);
    }

    public boolean isPatternSatelliteLinked(String satelliteUuid, int satelliteId) {
        if (satelliteUuid != null && !satelliteUuid.isEmpty() && linkedPatternSatelliteUuids.contains(satelliteUuid)) {
            return true;
        }
        return satelliteId > 0 && isPatternSatelliteLinked(satelliteId);
    }

    public PipeItemsPatternSatelliteLogistics getLinkedPatternSatellite(int satelliteId) {
        if (!isPatternSatelliteLinked(satelliteId)) {
            return null;
        }
        return PipeItemsPatternSatelliteLogistics.findById(satelliteId);
    }

    public boolean hasAdvancedSatelliteUpgrade() {
        return getOriginalUpgradeManager().isAdvancedSatelliteCrafter();
    }

    public PipeItemsPatternSatelliteLogistics getLinkedPatternSatellite(String satelliteUuid, int satelliteId) {
        if (satelliteUuid != null && !satelliteUuid.isEmpty() && linkedPatternSatelliteUuids.contains(satelliteUuid)) {
            PipeItemsPatternSatelliteLogistics satellite = PipeItemsPatternSatelliteLogistics.findByUuid(satelliteUuid);
            if (satellite != null) {
                return satellite;
            }
        }
        return getLinkedPatternSatellite(satelliteId);
    }

    /**
     * Resolves a pattern satellite stored on a pattern input slot and remembers it as linked to this pipe.
     * <p>
     * Pattern items can be edited before they are inserted into a pattern crafting pipe. In that case the pattern NBT
     * already contains the satellite UUID/id, but the pipe has not yet persisted that satellite in its own link list.
     * Resolving through this method makes the saved pattern assignment enough to route ingredients.
     */
    public PipeItemsPatternSatelliteLogistics resolvePatternSatelliteTarget(String satelliteUuid, int satelliteId) {
        if (!hasAdvancedSatelliteUpgrade()) {
            return null;
        }
        PipeItemsPatternSatelliteLogistics satellite = findPatternSatellite(satelliteUuid, satelliteId);
        if (satellite != null && getWorld() != null && MainProxy.isServer(getWorld())) {
            linkPatternSatellite(satellite.satelliteId, satellite.getSatelliteUuid());
        }
        return satellite;
    }

    /**
     * Resolves a fluid pattern satellite stored on a fluid input slot and persists the link when it is first seen.
     */
    public PipeFluidPatternSatelliteLogistics resolvePatternFluidSatelliteTarget(String satelliteUuid,
            int satelliteId) {
        if (!hasAdvancedSatelliteUpgrade()) {
            return null;
        }
        PipeFluidPatternSatelliteLogistics satellite = findPatternFluidSatellite(satelliteUuid, satelliteId);
        if (satellite != null && getWorld() != null && MainProxy.isServer(getWorld())) {
            linkPatternFluidSatellite(satellite.satelliteId, satellite.getSatelliteUuid());
        }
        return satellite;
    }

    /**
     * Adds one pattern satellite reference to the pipe's persisted link list.
     *
     * @return true when the pipe learned a new id or UUID
     */
    public boolean linkPatternSatellite(int satelliteId, String satelliteUuid) {
        return linkPatternSatellite(satelliteId, satelliteUuid, linkedPatternSatelliteIds, linkedPatternSatelliteUuids);
    }

    /**
     * Adds one fluid pattern satellite reference to the pipe's persisted link list.
     *
     * @return true when the pipe learned a new id or UUID
     */
    public boolean linkPatternFluidSatellite(int satelliteId, String satelliteUuid) {
        return linkPatternSatellite(
                satelliteId,
                satelliteUuid,
                linkedPatternFluidSatelliteIds,
                linkedPatternFluidSatelliteUuids);
    }

    private boolean linkPatternSatellite(int satelliteId, String satelliteUuid, Set<Integer> linkedIds,
            Set<String> linkedUuids) {
        boolean added = false;
        if (satelliteId > 0) {
            added |= linkedIds.add(satelliteId);
        }
        if (satelliteUuid != null && !satelliteUuid.isEmpty()) {
            added |= linkedUuids.add(satelliteUuid);
        }
        if (added) {
            refreshRender(false);
            if (container != null) {
                container.markDirty();
            }
        }
        return added;
    }

    private PipeItemsPatternSatelliteLogistics findPatternSatellite(String satelliteUuid, int satelliteId) {
        if (satelliteUuid != null && !satelliteUuid.isEmpty()) {
            PipeItemsPatternSatelliteLogistics satellite = PipeItemsPatternSatelliteLogistics.findByUuid(satelliteUuid);
            if (satellite != null) {
                return satellite;
            }
        }
        return satelliteId > 0 ? PipeItemsPatternSatelliteLogistics.findById(satelliteId) : null;
    }

    private PipeFluidPatternSatelliteLogistics findPatternFluidSatellite(String satelliteUuid, int satelliteId) {
        if (satelliteUuid != null && !satelliteUuid.isEmpty()) {
            PipeFluidPatternSatelliteLogistics satellite = PipeFluidPatternSatelliteLogistics.findByUuid(satelliteUuid);
            if (satellite != null) {
                return satellite;
            }
        }
        return satelliteId > 0 ? PipeFluidPatternSatelliteLogistics.findById(satelliteId) : null;
    }

    private int addLinkedPatternSatellites(List<ItemMemoryChip.StoredPatternSatellite> satellites) {
        int added = 0;
        if (satellites == null) {
            return added;
        }
        for (ItemMemoryChip.StoredPatternSatellite satellite : satellites) {
            if (linkPatternSatellite(satellite.id(), satellite.uuid())) {
                added++;
            }
        }
        return added;
    }

    private int applyLastMemorySatelliteToRecipe(ItemStack memoryChip) {
        int satelliteId = ItemMemoryChip.getLastPatternSatelliteId(memoryChip);
        String satelliteUuid = ItemMemoryChip.getLastPatternSatelliteUuid(memoryChip);
        if (satelliteId <= 0 && (satelliteUuid == null || satelliteUuid.isEmpty())) {
            return 0;
        }
        List<ItemMemoryChip.StoredPatternSatellite> satellites = new ArrayList<>();
        satellites.add(
                new ItemMemoryChip.StoredPatternSatellite(
                        satelliteId,
                        satelliteUuid,
                        ItemMemoryChip.getLastPatternSatelliteName(memoryChip)));
        addLinkedPatternSatellites(satellites);
        int changed = module.assignSatelliteToAllPatternIngredients(satelliteId, satelliteUuid);
        if (changed > 0) {
            doContentUpdate = true;
            if (container != null) {
                container.markDirty();
            }
        }
        return changed;
    }

    public void refreshSelectedInventoryConnection() {
        module.onCraftingTargetChanged();
        clearCache();
        triggerConnectionCheck();
        connectionUpdate();
        refreshRender(false);
    }

    @Override
    public void enabledUpdateEntity() {
        super.enabledUpdateEntity();
        if (doContentUpdate) {
            checkContentUpdate();
        }
        checkHudUpdate();
    }

    @Override
    public TextureType getCenterTexture() {
        return Textures.LOGISTICSPIPE_CRAFTER_TEXTURE;
    }

    @Override
    public LogisticsModule getLogisticsModule() {
        return module;
    }

    public ModulePatternCrafting getPatternModule() {
        return module;
    }

    @Override
    public IV3Crafter v3Crafter() {
        return module;
    }

    /** Crafting deliveries go into the module's buffer; the rest arrives as usual. */
    @Override
    public void notifyOfItemArival(ItemRoutingInformation information) {
        if (information.craftDelivery != null && MainProxy.isServer(getWorld())) {
            module.acceptDelivery(information);
        }
        super.notifyOfItemArival(information);
    }

    public void cancelPatternCraft(int patternSlot) {
        boolean changed = module.cancelPatternCraft(patternSlot);
        if (changed) {
            doContentUpdate = true;
        }
    }

    public void returnStoredInputsToStorage() {
        boolean changed = module.returnStoredInputsToStorage();
        if (changed) {
            doContentUpdate = true;
        }
    }

    /**
     * Prevents routed fluid ingredients from being stored in the inherited internal fluid tanks.
     * <p>
     * Pattern crafting needs the LogisticsFluidContainer item to arrive so the module can match it to a pattern slot
     * and record buffered ingredient state.
     */
    @Override
    public boolean canInsertToTanks() {
        return false;
    }

    /**
     * Every item that comes through is routed here, as before the fluid pipes started to relay clumps.
     */
    @Override
    public boolean relaysClumps() {
        return false;
    }

    /**
     * Disables the background side-tank transfer behavior from {@link FluidRoutedPipe}.
     * <p>
     * The selected adjacent inventory or fluid handler is managed explicitly by {@link ModulePatternCrafting}.
     */
    @Override
    public boolean canInsertFromSideToTanks() {
        return false;
    }

    /**
     * Rejects direct fluid-handler fills into this pipe.
     * <p>
     * Fluid ingredients must enter as routed fluid container items so request tracking and staged buffer accounting
     * stay consistent with item ingredients.
     */
    @Override
    public boolean canReceiveFluid() {
        return false;
    }

    /**
     * Returns the pattern-specific fluid order manager.
     * <p>
     * This manager tracks crafted fluid outputs and fluid extra orders; it is separate from the lazy manager provided
     * by the generic fluid pipe base class.
     */
    @Override
    public LogisticsFluidOrderManager getFluidOrderManager() {
        return fluidOrderManager;
    }

    /**
     * Keeps generic order-manager watching on the item manager.
     * <p>
     * Pattern crafting exposes both item and fluid orders in its custom HUD content, but callers expecting
     * CoreRoutedPipe behavior should still see the item order manager here.
     */
    @Override
    public LogisticsOrderManager<?, ?> getOrderManager() {
        return getItemOrderManager();
    }

    /**
     * Combines fluid-pipe shared-tank protection with the original item-inventory overlap protection.
     * <p>
     * Extending {@link FluidRoutedPipe} would otherwise only compare adjacent tanks, which would lose the item crafting
     * safeguard against two crafting pipes pulling from the same inventory.
     */
    @Override
    public boolean sharesInterestWith(CoreRoutedPipe other) {
        return super.sharesInterestWith(other) || sharesInventoryInterestWith(other);
    }

    /**
     * Checks whether this pipe and another routed pipe touch the same inventory.
     */
    private boolean sharesInventoryInterestWith(CoreRoutedPipe other) {
        List<IInventory> otherInventories = getConnectedInventories(other);
        if (otherInventories.isEmpty()) {
            return false;
        }
        for (IInventory inventory : getConnectedInventories(this)) {
            if (otherInventories.contains(inventory)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns normalized inventories connected to a routed pipe.
     * <p>
     * Pattern crafting pipes keep their selected target transport-disconnected so other blocks cannot push untracked
     * items into them. Their selected target is still counted here so the shared-inventory guard keeps working for
     * adjacent crafters.
     * <p>
     * Inventory wrappers are normalized through {@link InventoryHelper} to match the old CoreRoutedPipe comparison.
     */
    private List<IInventory> getConnectedInventories(CoreRoutedPipe pipe) {
        List<IInventory> inventories = new ArrayList<>();
        if (pipe instanceof PipeItemsPatternCraftingLogistics patternPipe) {
            AdjacentTile selected = patternPipe.getConnectedInventoryTile();
            if (selected != null && selected.tile instanceof IInventory) {
                inventories.add(InventoryHelper.getInventory((IInventory) selected.tile));
            }
            return inventories;
        }
        for (AdjacentTile tile : pipe.getConnectedEntities()) {
            if (tile.tile instanceof IInventory) {
                inventories.add(InventoryHelper.getInventory((IInventory) tile.tile));
            }
        }
        return inventories;
    }

    @Override
    public void canProvide(RequestTreeNode tree, RequestTree root, List<IFilter> filters) {
        module.canProvide(tree, root, filters);
    }

    @Override
    public LogisticsOrder fullFill(LogisticsPromise promise, IRequestItems destination,
            IAdditionalTargetInformation info) {
        return module.fullFill(promise, destination, info);
    }

    @Override
    public void getAllItems(Map<ItemIdentifier, Integer> list, List<IFilter> filter) {
        module.getAllItems(list, filter);
    }

    @Override
    public void registerExtras(IPromise promise) {
        module.registerExtras(promise);
    }

    @Override
    public ICraftingTemplate addCrafting(IResource type) {
        return module.addCrafting(type);
    }

    @Override
    public boolean canCraft(IResource toCraft) {
        return module.canCraft(toCraft);
    }

    @Override
    public int getTodo() {
        return module.getTodo() + fluidOrderManager.totalAmountCountInAllOrders();
    }

    @Override
    public List<ItemIdentifierStack> getConfiguredCraftResults() {
        return module.getConfiguredCraftResults();
    }

    @Override
    public Set<ItemIdentifier> getSpecificInterests() {
        return module.getOutputItems();
    }

    @Override
    public double getLoadFactor() {
        return (_orderItemManager.totalAmountCountInAllOrders() + fluidOrderManager.totalAmountCountInAllOrders()
                + 63.0) / 64.0;
    }

    @Override
    public int sinkAmount(FluidStack stack) {
        return module.sinkAmount(stack);
    }

    @Override
    public void itemLost(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        module.itemLost(item, info);
    }

    @Override
    public void itemArrived(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        module.itemArrived(item, info);
    }

    @Override
    public void listenedChanged() {
        doContentUpdate = true;
        module.markHudStateDirty();
    }

    private void checkContentUpdate() {
        doContentUpdate = false;
        LinkedList<ItemIdentifierStack> all = _orderItemManager.getContentList(getWorld());
        all.addAll(fluidOrderManager.getContentList(getWorld()));
        if (!oldList.equals(all)) {
            oldList.clear();
            oldList.addAll(all);
            MainProxy.sendToPlayerList(
                    PacketHandler.getPacket(OrdererManagerContent.class).setIdentList(all).setPosX(getX())
                            .setPosY(getY()).setPosZ(getZ()),
                    localModeWatchers);
        }
    }

    private void checkHudUpdate() {
        if (localModeWatchers.isEmptyWithoutCheck()) {
            return;
        }
        if (!module.shouldRefreshHudState()) {
            return;
        }
        PatternCraftingHudState state = module.getHudState();
        if (!oldHudState.equals(state)) {
            oldHudState = state;
            MainProxy.sendToPlayerList(
                    PacketHandler.getPacket(PatternCraftingHudContent.class).setState(state).setPosX(getX())
                            .setPosY(getY()).setPosZ(getZ()),
                    localModeWatchers);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
        targetSelector.writeToNBT(nbttagcompound);
        int[] satelliteIds = new int[linkedPatternSatelliteIds.size()];
        int index = 0;
        for (Integer satelliteId : linkedPatternSatelliteIds) {
            satelliteIds[index++] = satelliteId;
        }
        nbttagcompound.setIntArray(LINKED_PATTERN_SATELLITES_TAG, satelliteIds);
        NBTTagList satelliteUuids = new NBTTagList();
        for (String satelliteUuid : linkedPatternSatelliteUuids) {
            NBTTagCompound uuidTag = new NBTTagCompound();
            uuidTag.setString("uuid", satelliteUuid);
            satelliteUuids.appendTag(uuidTag);
        }
        nbttagcompound.setTag(LINKED_PATTERN_SATELLITE_UUIDS_TAG, satelliteUuids);
        int[] fluidSatelliteIds = new int[linkedPatternFluidSatelliteIds.size()];
        index = 0;
        for (Integer satelliteId : linkedPatternFluidSatelliteIds) {
            fluidSatelliteIds[index++] = satelliteId;
        }
        nbttagcompound.setIntArray(LINKED_PATTERN_FLUID_SATELLITES_TAG, fluidSatelliteIds);
        NBTTagList fluidSatelliteUuids = new NBTTagList();
        for (String satelliteUuid : linkedPatternFluidSatelliteUuids) {
            NBTTagCompound uuidTag = new NBTTagCompound();
            uuidTag.setString("uuid", satelliteUuid);
            fluidSatelliteUuids.appendTag(uuidTag);
        }
        nbttagcompound.setTag(LINKED_PATTERN_FLUID_SATELLITE_UUIDS_TAG, fluidSatelliteUuids);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        super.readFromNBT(nbttagcompound);
        targetSelector.readFromNBT(nbttagcompound);
        linkedPatternSatelliteIds.clear();
        for (int satelliteId : nbttagcompound.getIntArray(LINKED_PATTERN_SATELLITES_TAG)) {
            if (satelliteId > 0) {
                linkedPatternSatelliteIds.add(satelliteId);
            }
        }
        linkedPatternSatelliteUuids.clear();
        NBTTagList satelliteUuids = nbttagcompound.getTagList(LINKED_PATTERN_SATELLITE_UUIDS_TAG, 10);
        for (int i = 0; i < satelliteUuids.tagCount(); i++) {
            String satelliteUuid = satelliteUuids.getCompoundTagAt(i).getString("uuid");
            if (!satelliteUuid.isEmpty()) {
                linkedPatternSatelliteUuids.add(satelliteUuid);
            }
        }
        linkedPatternFluidSatelliteIds.clear();
        for (int satelliteId : nbttagcompound.getIntArray(LINKED_PATTERN_FLUID_SATELLITES_TAG)) {
            if (satelliteId > 0) {
                linkedPatternFluidSatelliteIds.add(satelliteId);
            }
        }
        linkedPatternFluidSatelliteUuids.clear();
        NBTTagList fluidSatelliteUuids = nbttagcompound.getTagList(LINKED_PATTERN_FLUID_SATELLITE_UUIDS_TAG, 10);
        for (int i = 0; i < fluidSatelliteUuids.tagCount(); i++) {
            String satelliteUuid = fluidSatelliteUuids.getCompoundTagAt(i).getString("uuid");
            if (!satelliteUuid.isEmpty()) {
                linkedPatternFluidSatelliteUuids.add(satelliteUuid);
            }
        }
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        super.writeData(data);
        targetSelector.writeData(data);
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        super.readData(data);
        targetSelector.readData(data);
    }

    @Override
    public void setOrderManagerContent(Collection<ItemIdentifierStack> list) {
        displayList.clear();
        displayList.addAll(list);
    }

    public void setHudState(PatternCraftingHudState state) {
        hudState = state == null ? PatternCraftingHudState.empty() : state;
    }

    public LogisticsFluidOrderManager getPatternFluidOrderManager() {
        return fluidOrderManager;
    }

    @Override
    public IHeadUpDisplayRenderer getRenderer() {
        return HUD;
    }

    @Override
    public void startWatching() {
        MainProxy.sendPacketToServer(
                PacketHandler.getPacket(HUDStartWatchingPacket.class).setInteger(1).setPosX(getX()).setPosY(getY())
                        .setPosZ(getZ()));
    }

    @Override
    public void stopWatching() {
        MainProxy.sendPacketToServer(
                PacketHandler.getPacket(HUDStopWatchingPacket.class).setInteger(1).setPosX(getX()).setPosY(getY())
                        .setPosZ(getZ()));
    }

    @Override
    public void playerStartWatching(EntityPlayer player, int mode) {
        if (mode == 1) {
            localModeWatchers.add(player);
            MainProxy.sendPacketToPlayer(
                    PacketHandler.getPacket(OrdererManagerContent.class).setIdentList(oldList).setPosX(getX())
                            .setPosY(getY()).setPosZ(getZ()),
                    player);
            PatternCraftingHudState state = module.getHudState();
            oldHudState = state;
            MainProxy.sendPacketToPlayer(
                    PacketHandler.getPacket(PatternCraftingHudContent.class).setState(state).setPosX(getX())
                            .setPosY(getY()).setPosZ(getZ()),
                    player);
        } else {
            super.playerStartWatching(player, mode);
        }
    }

    @Override
    public void playerStopWatching(EntityPlayer player, int mode) {
        super.playerStopWatching(player, mode);
        if (mode == 1) {
            localModeWatchers.remove(player);
        }
    }

    public BlockingMode getBlockingMode() {
        return module.getBlockingMode();
    }

    public void setBlockingMode(BlockingMode mode) {
        module.setBlockingMode(mode);
    }

    public boolean isBlockingModeFixed() {
        return module.isBlockingModeFixed();
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        return new PipePatternCraftingMui(this);
    }

    /**
     * Opens the MUI for legacy wrenches too; this pipe has no legacy GUI.
     * <p>
     * Called by {@link CoreRoutedPipe} on the server after the security station check.
     */
    @Override
    public void onWrenchClicked(EntityPlayer entityplayer) {
        openGui(entityplayer, this);
    }

}
