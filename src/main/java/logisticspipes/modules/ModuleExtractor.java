package logisticspipes.modules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.ForgeDirection;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.api.IMUICompatibleModule;
import logisticspipes.gui.hud.modules.HUDExtractor;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.dynamicModules.ModuleExtractorMuiDynamic;
import logisticspipes.interfaces.IClientInformationProvider;
import logisticspipes.interfaces.IHUDModuleHandler;
import logisticspipes.interfaces.IHUDModuleRenderer;
import logisticspipes.interfaces.IInventoryUtil;
import logisticspipes.interfaces.IModuleInventoryReceive;
import logisticspipes.interfaces.IModuleWatchReciver;
import logisticspipes.logisticspipes.ExtractionMode;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.modules.abstractmodules.LogisticsSneakyDirectionModule;
import logisticspipes.network.NewGuiHandler;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractguis.ModuleCoordinatesGuiProvider;
import logisticspipes.network.abstractguis.ModuleInHandGuiProvider;
import logisticspipes.network.guis.module.inhand.ExtractorFilterModuleInHand;
import logisticspipes.network.guis.module.inpipe.ExtractorFilterModuleSlot;
import logisticspipes.network.packets.hud.HUDStartModuleWatchingPacket;
import logisticspipes.network.packets.hud.HUDStopModuleWatchingPacket;
import logisticspipes.network.packets.module.ModuleInventory;
import logisticspipes.network.packets.modules.ExtractorInclude;
import logisticspipes.network.packets.modules.ExtractorModuleMode;
import logisticspipes.pipefxhandlers.Particles;
import logisticspipes.pipes.basic.CoreRoutedPipe.ItemSendMode;
import logisticspipes.pipes.modulehost.ItemTarget;
import logisticspipes.pipes.upgrades.ModuleUpgradeManager;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.computers.interfaces.CCCommand;
import logisticspipes.proxy.computers.interfaces.CCType;
import logisticspipes.utils.ISimpleInventoryEventHandler;
import logisticspipes.utils.PlayerCollectionList;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierInventory;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.string.StringUtils;
import logisticspipes.utils.tuples.Pair;

/**
 * Pulls items out of the inventory next to it and sends them to whatever takes them. A filter of 9 items picks what it
 * pulls (included) or leaves (excluded); the default is excluded, so an empty filter pulls everything. Speed upgrades
 * make it faster, see {@link #MAX_SPEED}.
 * <p>
 * The old Mk2/Mk3 and advanced extractors are this module now: see {@code ModuleMigration}.
 */
@CCType(name = "Extractor Module")
public class ModuleExtractor extends LogisticsSneakyDirectionModule
        implements IClientInformationProvider, IHUDModuleHandler, IModuleWatchReciver, IModuleInventoryReceive,
        ISimpleInventoryEventHandler, IMUICompatibleModule {

    /**
     * Speed upgrades beyond this don't count.
     */
    public static final int MAX_SPEED = ModuleUpgradeManager.MAX_SPEED_UPGRADES;

    // per speed level: ticks between extractions, items per extraction, energy per item
    private static final int[] TICKS = { 20, 20, 10, 5, 1 };
    private static final int[] ITEMS = { 5, 16, 32, 64, 64 };
    private static final int[] ENERGY = { 5, 6, 7, 8, 10 };

    private int currentTick = 0;

    private final ItemIdentifierInventory _filterInventory = new ItemIdentifierInventory(
            9,
            // the title of the filter list
            StringUtils.translate("gui.module.ModuleExtractor.filter"),
            1);
    private boolean _itemsIncluded = false;

    private final IHUDModuleRenderer HUD = new HUDExtractor(this);

    // client: the face the HUD shows, as the server sent it; null until it did
    private ForgeDirection shownDirection;

    private final PlayerCollectionList localModeWatchers = new PlayerCollectionList();

    public ModuleExtractor() {
        _filterInventory.addListener(this);
    }

    @CCCommand(description = "Returns the FilterInventory of this Module")
    public ItemIdentifierInventory getFilterInventory() {
        return _filterInventory;
    }

    /**
     * @return the speed upgrades that count, see {@link #getSpeedLevel()}
     */
    public int getSpeed() {
        return getSpeedLevel();
    }

    /**
     * @return the first face the HUD shows: the one the server sent, or the module's own until it did
     */
    public ForgeDirection getShownDirection() {
        return shownDirection != null ? shownDirection : getSneakyDirection();
    }

    /**
     * Client: the first face as the server sees it. Only for showing; the module's faces stay as they are.
     */
    public void setShownDirection(ForgeDirection face) {
        shownDirection = face;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        _filterInventory.readFromNBT(nbttagcompound);
        // the basic extractors had no filter and saved no mode: excluded, they pull everything
        setItemsIncluded(nbttagcompound.hasKey("itemsIncluded") && nbttagcompound.getBoolean("itemsIncluded"));
        readLegacySide(nbttagcompound);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        _filterInventory.writeToNBT(nbttagcompound);
        nbttagcompound.setBoolean("itemsIncluded", areItemsIncluded());
        writeLegacySide(nbttagcompound);
    }

    @Override
    protected ModuleCoordinatesGuiProvider getPipeGuiProvider() {
        return NewGuiHandler.getGui(ExtractorFilterModuleSlot.class).setAreItemsIncluded(_itemsIncluded);
    }

    @Override
    protected ModuleInHandGuiProvider getInHandGuiProvider() {
        return NewGuiHandler.getGui(ExtractorFilterModuleInHand.class);
    }

    @Override
    public SinkReply sinksItem(ItemIdentifier item, int bestPriority, int bestCustomPriority, boolean allowDefault,
            boolean includeInTransit) {
        return null;
    }

    @Override
    public LogisticsModule getSubModule(int slot) {
        return null;
    }

    @Override
    public void tick() {
        applyLegacySide();
        int speed = getSpeed();
        if (++currentTick < TICKS[speed]) {
            return;
        }
        currentTick = 0;
        ItemSendMode mode = speed == MAX_SPEED ? ItemSendMode.Fast : ItemSendMode.Normal;

        // the faces of the sneaky upgrade in order: the first that has items is pulled from
        ForgeDirection[] faces = getSneakyFaces();
        if (faces.length == 0) {
            faces = new ForgeDirection[] { ForgeDirection.UNKNOWN };
        }

        if (slot == ModulePositionType.IN_PIPE) {
            // the extractor pipe, like the fluid extractor pipe: every inventory next to it, a full extraction each;
            // disconnected sides aren't targets
            Set<ForgeDirection> done = EnumSet.noneOf(ForgeDirection.class);
            for (ForgeDirection face : faces) {
                for (ItemTarget target : _service.getItemTargets(ExtractionMode.Normal, true, face)) {
                    if (!done.contains(target.direction) && target.inventory != null && !target.inventory.isEmpty()) {
                        done.add(target.direction);
                        extract(target.inventory, ITEMS[speed], ENERGY[speed], mode);
                    }
                }
            }
            return;
        }

        for (ForgeDirection face : faces) {
            ForgeDirection extractOrientation = face == ForgeDirection.UNKNOWN
                    ? _service.inventoryOrientation().getOpposite()
                    : face;
            IInventoryUtil inventory = _service.getSneakyInventory(extractOrientation, true);
            // nothing to extract through this face, try the next one
            if (inventory != null && !inventory.isEmpty()) {
                extract(inventory, ITEMS[speed], ENERGY[speed], mode);
                return;
            }
        }
    }

    private void extract(IInventoryUtil invUtil, int itemsToExtract, int energyPerItem, ItemSendMode mode) {
        Map<ItemIdentifier, Integer> items = invUtil.getItemsAndCount();
        for (Entry<ItemIdentifier, Integer> item : items.entrySet()) {
            if (!canExtract(item.getKey().makeNormalStack(1))) {
                continue;
            }
            List<Integer> jamList = new LinkedList<>();
            Pair<Integer, SinkReply> reply = _service.hasDestination(item.getKey(), true, jamList);
            if (reply == null) {
                continue;
            }

            int itemsleft = itemsToExtract;
            while (reply != null) {
                int count = Math.min(itemsleft, item.getValue());
                count = Math.min(count, item.getKey().getMaxStackSize());
                if (reply.getValue2().maxNumberOfItems > 0) {
                    count = Math.min(count, reply.getValue2().maxNumberOfItems);
                }

                while (!_service.useEnergy(energyPerItem * count) && count > 0) {
                    _service.spawnParticle(Particles.OrangeParticle, 2);
                    count--;
                }

                if (count <= 0) {
                    break;
                }

                ItemStack stackToSend = invUtil.getMultipleItems(item.getKey(), count);
                if (stackToSend == null || stackToSend.stackSize == 0) {
                    break;
                }
                count = stackToSend.stackSize;
                _service.sendStack(stackToSend, reply, mode);
                itemsleft -= count;
                if (itemsleft <= 0) {
                    break;
                }

                jamList.add(reply.getValue1());
                reply = _service.hasDestination(item.getKey(), true, jamList);
            }
            return;
        }
    }

    /**
     * @return true if the filter lets the item out: it is in the filter and the filter includes, or it isn't and the
     *         filter excludes. Damageable items match whatever their damage.
     */
    public boolean canExtract(ItemStack item) {
        for (int i = 0; i < _filterInventory.getSizeInventory(); i++) {
            ItemStack stack = _filterInventory.getStackInSlot(i);
            if ((stack != null) && (stack.getItem() == item.getItem())) {
                if (item.getItem().isDamageable()) {
                    return areItemsIncluded();
                }
                if (stack.getItemDamage() == item.getItemDamage()) {
                    return areItemsIncluded();
                }
            }
        }
        return !areItemsIncluded();
    }

    public boolean areItemsIncluded() {
        return _itemsIncluded;
    }

    public void setItemsIncluded(boolean flag) {
        _itemsIncluded = flag;
        if (!localModeWatchers.isEmpty()) {
            MainProxy.sendToPlayerList(
                    PacketHandler.getPacket(ExtractorInclude.class).setFlag(areItemsIncluded()).setModulePos(this),
                    localModeWatchers);
        }
    }

    @Override
    public List<String> getClientInformation() {
        List<String> list = new ArrayList<>(6);
        list.add(areItemsIncluded() ? "Included" : "Excluded");
        list.add(
                "Extraction: "
                        + ((getSneakyDirection() == ForgeDirection.UNKNOWN) ? "DEFAULT" : getSneakyDirection().name()));
        list.add("Filter: ");
        list.add("<inventory>");
        list.add("<that>");
        return list;
    }

    @Override
    public void InventoryChanged(IInventory inventory) {
        // watchers only exist on the server, and only for a module in a pipe
        if (!localModeWatchers.isEmpty()) {
            MainProxy.sendToPlayerList(
                    PacketHandler.getPacket(ModuleInventory.class)
                            .setIdentList(ItemIdentifierStack.getListFromInventory(inventory)).setModulePos(this),
                    localModeWatchers);
        }
    }

    @Override
    public void handleInvContent(Collection<ItemIdentifierStack> list) {
        _filterInventory.handleItemIdentifierList(list);
    }

    @Override
    public void startHUDWatching() {
        MainProxy.sendPacketToServer(PacketHandler.getPacket(HUDStartModuleWatchingPacket.class).setModulePos(this));
    }

    @Override
    public void stopHUDWatching() {
        MainProxy.sendPacketToServer(PacketHandler.getPacket(HUDStopModuleWatchingPacket.class).setModulePos(this));
    }

    @Override
    public void startWatching(EntityPlayer player) {
        localModeWatchers.add(player);
        MainProxy.sendPacketToPlayer(
                PacketHandler.getPacket(ModuleInventory.class)
                        .setIdentList(ItemIdentifierStack.getListFromInventory(_filterInventory)).setModulePos(this),
                player);
        MainProxy.sendPacketToPlayer(
                PacketHandler.getPacket(ExtractorModuleMode.class).setDirection(getSneakyDirection())
                        .setModulePos(this),
                player);
        MainProxy.sendPacketToPlayer(
                PacketHandler.getPacket(ExtractorInclude.class).setFlag(areItemsIncluded()).setModulePos(this),
                player);
    }

    @Override
    public void stopWatching(EntityPlayer player) {
        localModeWatchers.remove(player);
    }

    @Override
    public IHUDModuleRenderer getHUDRenderer() {
        return HUD;
    }

    @Override
    public boolean hasGenericInterests() {
        return false;
    }

    @Override
    public List<ItemIdentifier> getSpecificInterests() {
        return null;
    }

    @Override
    public boolean interestedInAttachedInventory() {
        return false;
    }

    @Override
    public boolean interestedInUndamagedID() {
        return false;
    }

    @Override
    public boolean recievePassive() {
        return false;
    }

    @Override
    public LogisticsModularUI getHandGui() {
        return new ModuleExtractorMuiDynamic(this);
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        return new ModuleExtractorMuiDynamic(this);
    }

    @Override
    public LogisticsModularUI getPipeGui(String prefix) {
        return new ModuleExtractorMuiDynamic(this, prefix);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconTexture(IIconRegister register) {
        return register.registerIcon("logisticspipes:itemModule/ModuleExtractor");
    }
}
