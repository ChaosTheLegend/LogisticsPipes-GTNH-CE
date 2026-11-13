package logisticspipes.modules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.api.IMUICompatibleModule;
import logisticspipes.crafting.IStagedProviderReservation;
import logisticspipes.gui.hud.modules.HUDProviderModule;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.dynamicModules.ModuleProviderMuiDynamic;
import logisticspipes.interfaces.IClientInformationProvider;
import logisticspipes.interfaces.IHUDModuleHandler;
import logisticspipes.interfaces.IHUDModuleRenderer;
import logisticspipes.interfaces.IInventoryUtil;
import logisticspipes.interfaces.ILegacyActiveModule;
import logisticspipes.interfaces.IModuleInventoryReceive;
import logisticspipes.interfaces.IModuleWatchReciver;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.interfaces.routing.IProvideItems;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.logistics.LogisticsManager;
import logisticspipes.logisticspipes.ExtractionMode;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.modules.abstractmodules.IPrioritizedModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.modules.abstractmodules.LogisticsSneakyDirectionModule;
import logisticspipes.network.NewGuiHandler;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractguis.ModuleCoordinatesGuiProvider;
import logisticspipes.network.abstractguis.ModuleInHandGuiProvider;
import logisticspipes.network.guis.module.inhand.ProviderModuleInHand;
import logisticspipes.network.guis.module.inpipe.ProviderModuleGuiProvider;
import logisticspipes.network.packets.hud.HUDStartModuleWatchingPacket;
import logisticspipes.network.packets.hud.HUDStopModuleWatchingPacket;
import logisticspipes.network.packets.module.ModuleInventory;
import logisticspipes.pipefxhandlers.Particles;
import logisticspipes.pipes.basic.CoreRoutedPipe.ItemSendMode;
import logisticspipes.pipes.modulehost.ItemTarget;
import logisticspipes.pipes.upgrades.ModuleUpgradeManager;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.proxy.computers.interfaces.CCCommand;
import logisticspipes.proxy.computers.interfaces.CCType;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTreeNode;
import logisticspipes.request.resources.DictResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.request.resources.ItemResource;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.routing.order.LogisticsItemOrder;
import logisticspipes.routing.order.LogisticsOrder;
import logisticspipes.utils.PlayerCollectionList;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierInventory;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.string.StringUtils;

@CCType(name = "Provider Module")
public class ModuleProvider extends LogisticsSneakyDirectionModule
        implements ILegacyActiveModule, IClientInformationProvider, IHUDModuleHandler, IModuleWatchReciver,
        IModuleInventoryReceive, IMUICompatibleModule, IStagedProviderReservation, IPrioritizedModule {

    private final ItemIdentifierInventory _filterInventory = new ItemIdentifierInventory(
            9,
            StringUtils.translate("gui.module.ModuleProvider"),
            1);

    private boolean isActive = false;

    protected boolean isExcludeFilter = false;
    protected ExtractionMode _extractionMode = ExtractionMode.Normal;

    private final Map<ItemIdentifier, Integer> displayMap = new TreeMap<>();
    private final Map<ItemIdentifier, Integer> stagedCraftingReservations = new TreeMap<>();
    public final ArrayList<ItemIdentifierStack> displayList = new ArrayList<>();
    private final ArrayList<ItemIdentifierStack> oldList = new ArrayList<>();

    private final IHUDModuleRenderer HUD = new HUDProviderModule(this);

    private final PlayerCollectionList localModeWatchers = new PlayerCollectionList();

    public ModuleProvider() {}

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        _filterInventory.readFromNBT(nbttagcompound, "");
        readPriority(nbttagcompound);
        isActive = nbttagcompound.getBoolean("isActive");
        isExcludeFilter = nbttagcompound.getBoolean("filterisexclude");
        _extractionMode = ExtractionMode.getMode(nbttagcompound.getInteger("extractionMode"));
        readLegacySide(nbttagcompound);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        _filterInventory.writeToNBT(nbttagcompound, "");
        writePriority(nbttagcompound);
        nbttagcompound.setBoolean("isActive", isActive);
        nbttagcompound.setBoolean("filterisexclude", isExcludeFilter);
        nbttagcompound.setInteger("extractionMode", _extractionMode.ordinal());
        writeLegacySide(nbttagcompound);
    }

    @Override
    protected ModuleCoordinatesGuiProvider getPipeGuiProvider() {
        return NewGuiHandler.getGui(ProviderModuleGuiProvider.class).setExtractorMode(getExtractionMode().ordinal())
                .setExclude(isExcludeFilter);
        // .setIsActive(isActive)
        // .setSneakyDirection(_sneakyDirection);
    }

    @Override
    protected ModuleInHandGuiProvider getInHandGuiProvider() {
        return NewGuiHandler.getGui(ProviderModuleInHand.class);
    }

    /**
     * Speed upgrades beyond this don't count.
     */
    public static final int MAX_SPEED = ModuleUpgradeManager.MAX_SPEED_UPGRADES;

    // per speed level: items per send, stacks per send, energy per item. 0 is the old provider, 4 the old Mk2
    private static final int[] ITEMS = { 8, 16, 32, 64, 128 };
    private static final int[] STACKS = { 1, 2, 4, 6, 8 };
    private static final int[] ENERGY = { 1, 1, 2, 2, 2 };

    /**
     * @return the speed upgrades that count, see {@link #getSpeedLevel()}
     */
    public int getSpeed() {
        return getSpeedLevel();
    }

    protected int neededEnergy() {
        return ENERGY[getSpeed()];
    }

    protected ItemSendMode itemSendMode() {
        return getSpeed() == MAX_SPEED ? ItemSendMode.Fast : ItemSendMode.Normal;
    }

    protected int itemsToExtract() {
        return ITEMS[getSpeed()];
    }

    protected int stacksToExtract() {
        return STACKS[getSpeed()];
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

    /**
     * @return the inventories this module provides from: the one a chassis points at, every one next to a provider
     *         pipe. Only the first face of a sneaky upgrade counts (decided); the extractor is the one that walks all
     *         faces.
     */
    private List<ItemTarget> getTargets() {
        if (_service == null) {
            return Collections.emptyList();
        }
        return _service.getItemTargets(_extractionMode, true, getSneakyDirection());
    }

    @Override
    public void tick() {
        applyLegacySide();
        // the provider pipe always sent every 6 ticks, a chassis module every tick
        if (slot == ModulePositionType.IN_PIPE && !_service.isNthTick(6)) {
            return;
        }
        checkUpdate(null);
        int itemsleft = itemsToExtract();
        int stacksleft = stacksToExtract();
        LogisticsItemOrder firstOrder = null;
        LogisticsItemOrder order = null;
        while (itemsleft > 0 && stacksleft > 0
                && _service.getItemOrderManager().hasOrders(ResourceType.PROVIDER)
                && (firstOrder == null || firstOrder != order)) {
            if (firstOrder == null) {
                firstOrder = order;
            }
            order = _service.getItemOrderManager().peekAtTopRequest(ResourceType.PROVIDER);
            int sent = sendStack(
                    order.getResource().stack,
                    itemsleft,
                    order.getDestination().getRouter().getSimpleID(),
                    order.getInformation());
            if (sent < 0) {
                break;
            }
            _service.spawnParticle(Particles.VioletParticle, 3);
            stacksleft -= 1;
            itemsleft -= sent;
        }
    }

    public boolean filterAllowsItem(ItemIdentifier item) {
        if (!hasFilter()) {
            return true;
        }
        boolean isFiltered = itemIsFiltered(item);
        return isExcludeFilter ^ isFiltered;
    }

    @Override
    public void onBlockRemoval() {
        while (_service.getItemOrderManager().hasOrders(ResourceType.PROVIDER)) {
            _service.getItemOrderManager().sendFailed();
        }
    }

    @Override
    public void canProvide(RequestTreeNode tree, RequestTree root, List<IFilter> filters) {
        IProvideItems provider = (IProvideItems) _service;
        if (tree.getRequestType() instanceof ItemResource) {
            ItemIdentifier item = ((ItemResource) tree.getRequestType()).getItem();
            for (IFilter filter : filters) {
                if (filter.isBlocked() == filter.isFilteredItem(item.getUndamaged()) || filter.blockProvider()) {
                    return;
                }
            }
            int canProvide = getAvailableItemCount(item) - root.getAllPromissesFor(provider, item);
            if (canProvide < 1) {
                return;
            }
            tree.addPromise(
                    new LogisticsPromise(
                            item,
                            Math.min(canProvide, tree.getMissingAmount()),
                            provider,
                            ResourceType.PROVIDER));
        } else if (tree.getRequestType() instanceof DictResource dict) {
            Map<ItemIdentifier, Integer> available = new HashMap<>();
            getAllItems(available, filters);
            for (ItemIdentifier item : available.keySet()) {
                if (!dict.matches(item, IResource.MatchSettings.NORMAL)) {
                    continue;
                }
                int canProvide = getAvailableItemCount(item) - root.getAllPromissesFor(provider, item);
                if (canProvide < 1) {
                    continue;
                }
                tree.addPromise(
                        new LogisticsPromise(
                                item,
                                Math.min(canProvide, tree.getMissingAmount()),
                                provider,
                                ResourceType.PROVIDER));
                if (tree.getMissingAmount() <= 0) {
                    break;
                }
            }
        }
    }

    @Override
    public LogisticsOrder fullFill(LogisticsPromise promise, IRequestItems destination,
            IAdditionalTargetInformation info) {
        releaseStagedCrafting(promise.item, promise.numberOfItems);
        return _service.getItemOrderManager().addOrder(
                new ItemIdentifierStack(promise.item, promise.numberOfItems),
                destination,
                ResourceType.PROVIDER,
                info);
    }

    private int getAvailableItemCount(ItemIdentifier item) {
        return getTotalItemCount(item) - _service.getItemOrderManager().totalItemsCountInOrders(item)
                - getReservedStagedCrafting(item);
    }

    @Override
    public void getAllItems(Map<ItemIdentifier, Integer> items, List<IFilter> filters) {
        Map<ItemIdentifier, Integer> added = new HashMap<>();
        for (ItemTarget target : getTargets()) {
            outer: for (Entry<ItemIdentifier, Integer> currItem : target.inventory.getItemsAndCount().entrySet()) {
                if (items.containsKey(currItem.getKey())) {
                    continue; // already provided by a previous module
                }
                if (!filterAllowsItem(currItem.getKey())) {
                    continue;
                }
                for (IFilter filter : filters) {
                    if (filter.isBlocked() == filter.isFilteredItem(currItem.getKey().getUndamaged())
                            || filter.blockProvider()) {
                        continue outer;
                    }
                }
                added.merge(currItem.getKey(), currItem.getValue(), Integer::sum);
            }
        }
        // reduce what is promised already
        for (Entry<ItemIdentifier, Integer> item : added.entrySet()) {
            int remaining = item.getValue() - _service.getItemOrderManager().totalItemsCountInOrders(item.getKey())
                    - getReservedStagedCrafting(item.getKey());
            if (remaining > 0) {
                items.put(item.getKey(), remaining);
            }
        }
    }

    /**
     * Tracks provider stock that belongs to a staged crafting tree but has not been sent yet.
     */
    @Override
    public void reserveStagedCrafting(ItemIdentifier item, int amount) {
        if (item == null || amount <= 0) {
            return;
        }
        stagedCraftingReservations.put(item, getReservedStagedCrafting(item) + amount);
    }

    /**
     * Releases staged provider stock before the real provider order is inserted into this module's order manager.
     */
    @Override
    public void releaseStagedCrafting(ItemIdentifier item, int amount) {
        if (item == null || amount <= 0) {
            return;
        }
        int remaining = getReservedStagedCrafting(item) - amount;
        if (remaining <= 0) {
            stagedCraftingReservations.remove(item);
        } else {
            stagedCraftingReservations.put(item, remaining);
        }
    }

    /**
     * Returns the staged reservation amount for an item.
     */
    private int getReservedStagedCrafting(ItemIdentifier item) {
        Integer reserved = stagedCraftingReservations.get(item);
        return reserved != null ? reserved : 0;
    }

    // returns -1 on permanently failed, don't try another stack this tick
    // returns 0 on "unable to do this delivery"
    private int sendStack(ItemIdentifierStack stack, int maxCount, int destination, IAdditionalTargetInformation info) {
        ItemIdentifier item = stack.getItem();
        for (ItemTarget target : getTargets()) {
            IInventoryUtil inv = target.inventory;
            int available = inv.itemCount(item);
            if (available == 0) {
                continue;
            }
            int wanted = Math.min(available, stack.getStackSize());
            wanted = Math.min(wanted, maxCount);
            wanted = Math.min(wanted, item.getMaxStackSize());
            IRouter dRtr = SimpleServiceLocator.routerManager.getRouterUnsafe(destination, false);
            if (dRtr == null) {
                _service.getItemOrderManager().sendFailed();
                return 0;
            }
            SinkReply reply = LogisticsManager.canSink(dRtr, null, true, stack.getItem(), null, true, false);
            boolean defersend = false;
            if (reply != null) { // some pipes are not aware of the space in the adjacent inventory, so they return null
                if (reply.maxNumberOfItems < wanted) {
                    wanted = reply.maxNumberOfItems;
                    if (wanted <= 0) {
                        _service.getItemOrderManager().deferSend();
                        return 0;
                    }
                    defersend = true;
                }
            }
            if (!_service.canUseEnergy(wanted * neededEnergy())) {
                return -1;
            }
            ItemStack removed = inv.getMultipleItems(item, wanted);
            if (removed == null || removed.stackSize == 0) {
                continue;
            }
            int sent = removed.stackSize;
            _service.useEnergy(sent * neededEnergy());
            // sent from the side of the inventory it came out of
            IRoutedItem sendedItem = _service.sendStack(removed, destination, itemSendMode(), info, target.direction);
            _service.getItemOrderManager().sendSuccessfull(sent, defersend, sendedItem);
            return sent;
        }
        _service.getItemOrderManager().sendFailed();
        return 0;
    }

    private int getTotalItemCount(ItemIdentifier item) {
        if (!filterAllowsItem(item)) {
            return 0;
        }
        int count = 0;
        for (ItemTarget target : getTargets()) {
            count += target.inventory.itemCount(item);
        }
        return count;
    }

    private boolean hasFilter() {
        return !_filterInventory.isEmpty();
    }

    private boolean itemIsFiltered(ItemIdentifier item) {
        return _filterInventory.containsItem(item);
    }

    /*** GUI STUFF ***/
    @CCCommand(description = "Returns the FilterInventory of this Module")
    public IInventory getFilterInventory() {
        return _filterInventory;
    }

    public boolean isExcludeFilter() {
        return isExcludeFilter;
    }

    public void setFilterExcluded(boolean isExcludeFilter) {
        this.isExcludeFilter = isExcludeFilter;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setIsActive(boolean isActive) {
        this.isActive = isActive;
    }

    public ExtractionMode getExtractionMode() {
        return _extractionMode;
    }

    public void setExtractionMode(int id) {
        _extractionMode = ExtractionMode.getMode(id);
    }

    public void nextExtractionMode() {
        _extractionMode = _extractionMode.next();
    }

    @Override
    public List<String> getClientInformation() {
        List<String> list = new ArrayList<>();
        list.add(!isExcludeFilter ? "Included" : "Excluded");
        list.add("Mode: " + _extractionMode.getExtractionModeString());
        list.add("Filter: ");
        list.add("<inventory>");
        list.add("<that>");
        return list;
    }

    private void checkUpdate(EntityPlayer player) {
        if (localModeWatchers.size() == 0 && player == null) {
            return;
        }
        displayList.clear();
        displayMap.clear();
        getAllItems(displayMap, new ArrayList<>(0));
        displayList.ensureCapacity(displayMap.size());
        for (Entry<ItemIdentifier, Integer> item : displayMap.entrySet()) {
            displayList.add(new ItemIdentifierStack(item.getKey(), item.getValue()));
        }
        if (!oldList.equals(displayList)) {
            oldList.clear();
            oldList.ensureCapacity(displayList.size());
            oldList.addAll(displayList);
            MainProxy.sendToPlayerList(
                    PacketHandler.getPacket(ModuleInventory.class).setIdentList(displayList).setModulePos(this)
                            .setCompressable(true),
                    localModeWatchers);
        } else if (player != null) {
            MainProxy.sendPacketToPlayer(
                    PacketHandler.getPacket(ModuleInventory.class).setIdentList(displayList).setModulePos(this)
                            .setCompressable(true),
                    player);
        }
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
        checkUpdate(player);
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
    public void handleInvContent(Collection<ItemIdentifierStack> list) {
        displayList.clear();
        displayList.addAll(list);
    }

    public int getExtractionSpeed() {
        return itemsToExtract();
    }

    @Override
    public boolean hasGenericInterests() {
        return false;
    }

    @Override
    public List<ItemIdentifier> getSpecificInterests() {
        // when filter is empty or in exclude mode, this is interested in attached inventory already
        if (isExcludeFilter || _filterInventory.isEmpty()) {
            return null;
        }
        // when items included this is only interested in items in the filter
        Map<ItemIdentifier, Integer> mapIC = _filterInventory.getItemsAndCount();
        List<ItemIdentifier> li = new ArrayList<>(mapIC.size());
        li.addAll(mapIC.keySet());
        return li;
    }

    @Override
    public boolean interestedInAttachedInventory() {
        return isExcludeFilter || _filterInventory.isEmpty(); // when items included this is only interested in items in
        // the filter
        // when items not included, we can only serve those items in the filter.
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
    @SideOnly(Side.CLIENT)
    public IIcon getIconTexture(IIconRegister register) {
        return register.registerIcon("logisticspipes:itemModule/ModuleProvider");
    }

    @Override
    public LogisticsModularUI getHandGui() {
        return new ModuleProviderMuiDynamic(this);
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        return new ModuleProviderMuiDynamic(this);
    }

    @Override
    public LogisticsModularUI getPipeGui(String prefix) {
        return new ModuleProviderMuiDynamic(this, prefix);
    }
}
