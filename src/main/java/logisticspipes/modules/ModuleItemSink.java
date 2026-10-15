package logisticspipes.modules;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.api.IMUICompatibleModule;
import logisticspipes.gui.hud.modules.HUDItemSink;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.dynamicModules.ModuleItemSinkMuiDynamic;
import logisticspipes.interfaces.IClientInformationProvider;
import logisticspipes.interfaces.IHUDModuleHandler;
import logisticspipes.interfaces.IHUDModuleRenderer;
import logisticspipes.interfaces.IInventoryUtil;
import logisticspipes.interfaces.IModuleInventoryReceive;
import logisticspipes.interfaces.IModuleWatchReciver;
import logisticspipes.modules.abstractmodules.LogisticsGuiModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.network.NewGuiHandler;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractguis.ModuleCoordinatesGuiProvider;
import logisticspipes.network.abstractguis.ModuleInHandGuiProvider;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.network.guis.module.inhand.ItemSinkInHand;
import logisticspipes.network.guis.module.inpipe.ItemSinkSlot;
import logisticspipes.network.packets.hud.HUDStartModuleWatchingPacket;
import logisticspipes.network.packets.hud.HUDStopModuleWatchingPacket;
import logisticspipes.network.packets.module.ModuleInventory;
import logisticspipes.network.packets.modules.ItemSinkDefault;
import logisticspipes.network.packets.modules.ItemSinkFuzzy;
import logisticspipes.pipes.PipeLogisticsChassi.ChassiTargetInformation;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.computers.interfaces.CCCommand;
import logisticspipes.proxy.computers.interfaces.CCType;
import logisticspipes.utils.ISimpleInventoryEventHandler;
import logisticspipes.utils.PlayerCollectionList;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.SinkReply.FixedPriority;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierInventory;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.string.StringUtils;
import logisticspipes.utils.tuples.Pair;

@CCType(name = "ItemSink Module")
public class ModuleItemSink extends LogisticsGuiModule implements IClientInformationProvider, IHUDModuleHandler,
        IModuleWatchReciver, ISimpleInventoryEventHandler, IModuleInventoryReceive, IMUICompatibleModule {

    private final ItemIdentifierInventory _filterInventory = new ItemIdentifierInventory(
            9,
            StringUtils.translate("gui.module.requestedItems"),
            1);
    private boolean _isDefaultRoute;

    private BitSet ignoreData = new BitSet(_filterInventory.getSizeInventory());
    private BitSet ignoreNBT = new BitSet(_filterInventory.getSizeInventory());

    private final IHUDModuleRenderer HUD = new HUDItemSink(this);

    private final PlayerCollectionList localModeWatchers = new PlayerCollectionList();

    public ModuleItemSink() {
        _filterInventory.addListener(this);
    }

    @CCCommand(description = "Returns the FilterInventory of this Module")
    public ItemIdentifierInventory getFilterInventory() {
        return _filterInventory;
    }

    @CCCommand(description = "Returns true if the module is a default route")
    public boolean isDefaultRoute() {
        return _isDefaultRoute;
    }

    @CCCommand(description = "Sets the default route status of this module")
    public void setDefaultRoute(Boolean isDefaultRoute) {
        _isDefaultRoute = isDefaultRoute;
        if (!localModeWatchers.isEmpty()) {
            MainProxy.sendToPlayerList(
                    PacketHandler.getPacket(ItemSinkDefault.class).setFlag(_isDefaultRoute).setModulePos(this),
                    localModeWatchers);
        }
    }

    private SinkReply _sinkReply;
    private SinkReply _sinkReplyDefault;

    @Override
    public void registerPosition(ModulePositionType slot, int positionInt) {
        super.registerPosition(slot, positionInt);
        _sinkReply = new SinkReply(
                FixedPriority.ItemSink,
                0,
                true,
                false,
                1,
                0,
                new ChassiTargetInformation(getPositionInt()));
        _sinkReplyDefault = new SinkReply(
                FixedPriority.DefaultRoute,
                0,
                true,
                true,
                1,
                0,
                new ChassiTargetInformation(getPositionInt()));
    }

    /**
     * How long an answer about room in the target inventory is trusted. Matches the router's interest refresh, so a
     * full sink drops out of the interest registry on the next refresh and comes back once room frees up.
     */
    private static final int ROOM_CACHE_TICKS = 20;

    private final Map<ItemIdentifier, Boolean> roomCache = new HashMap<>();
    private long roomCacheExpiry = 0;
    private Boolean hasFreeSlot;

    private void expireRoomCache() {
        long now = _world.getWorld().getTotalWorldTime();
        if (now >= roomCacheExpiry) {
            roomCache.clear();
            hasFreeSlot = null;
            roomCacheExpiry = now + ROOM_CACHE_TICKS;
        }
    }

    /**
     * The inventory items are inserted into. Basic pipes have no pointed direction, so fall back to the pipe's pointed
     * inventory lookup, which searches the adjacent inventories.
     */
    private IInventoryUtil targetInventory() {
        IInventoryUtil inv = _service.getSneakyInventory(false, slot, positionInt);
        return inv != null ? inv : _service.getPointedInventory(false);
    }

    /** Whether the target inventory can take at least one of {@code item}. No inventory means no room. */
    private boolean hasRoomFor(ItemIdentifier item) {
        if (_world == null || _world.getWorld() == null) {
            return true;
        }
        expireRoomCache();
        Boolean room = roomCache.get(item);
        if (room == null) {
            IInventoryUtil inv = targetInventory();
            room = inv != null && inv.roomForItem(item, 1) > 0;
            roomCache.put(item, room);
        }
        return room;
    }

    /**
     * Whether the target inventory has an empty slot, so it can take items it doesn't hold yet. Special inventories
     * (barrels, drawers, AE) don't expose slots that way and are assumed to have room. No inventory means no room.
     */
    private boolean acceptsNewItems() {
        if (_world == null || _world.getWorld() == null) {
            return true;
        }
        expireRoomCache();
        if (hasFreeSlot == null) {
            IInventoryUtil inv = targetInventory();
            hasFreeSlot = inv != null && (inv.isSpecialInventory() || hasEmptySlot(inv));
        }
        return hasFreeSlot;
    }

    private static boolean hasEmptySlot(IInventoryUtil inv) {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            if (inv.getStackInSlot(i) == null) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code reply} if the target inventory still has room for {@code item} once the items already on their way here
     * are counted, limited to that room. Without this, small inventories are promised to far more items than fit, and
     * the ones that arrive late bounce from sink to sink until only default routes are left. The limit applies even
     * with nothing on the way, so a stack is split across sinks when it is sent, not after it bounced.
     */
    private SinkReply replyIfRoom(SinkReply reply, ItemIdentifier item, boolean includeInTransit, int energy) {
        if (!hasRoomFor(item) || !_service.canUseEnergy(energy)) {
            return null;
        }
        // in a chassis, ChassiModule does the in-transit check for the whole pipe
        if (!includeInTransit || slot == ModulePositionType.SLOT || _world == null || _world.getWorld() == null) {
            return reply;
        }
        int onRoute = _service.countOnRoute(item);
        IInventoryUtil inv = targetInventory();
        if (inv == null) {
            return null;
        }
        int room = inv.roomForItem(item, onRoute + item.getMaxStackSize()) - onRoute;
        return room < 1 ? null : new SinkReply(reply, room);
    }

    @Override
    public void insertionFailed(ItemIdentifier item) {
        if (_world == null || _world.getWorld() == null) {
            return;
        }
        expireRoomCache();
        roomCache.put(item, false);
        hasFreeSlot = false;
    }

    @Override
    public SinkReply sinksItem(ItemIdentifier item, int bestPriority, int bestCustomPriority, boolean allowDefault,
            boolean includeInTransit) {
        if (_isDefaultRoute && !allowDefault) {
            return null;
        }
        if (bestPriority > _sinkReply.fixedPriority.ordinal() || (bestPriority == _sinkReply.fixedPriority.ordinal()
                && bestCustomPriority >= _sinkReply.customPriority)) {
            return null;
        }
        if (_filterInventory.containsUndamagedItem(item.getUndamaged())) {
            return replyIfRoom(_sinkReply, item, includeInTransit, 1);
        }
        if (_service.getUpgradeManager(slot, positionInt).isFuzzyUpgrade()) {
            for (Pair<ItemIdentifierStack, Integer> stack : _filterInventory) {
                if (stack == null) {
                    continue;
                }
                if (stack.getValue1() == null) {
                    continue;
                }
                ItemIdentifier ident1 = item;
                ItemIdentifier ident2 = stack.getValue1().getItem();
                if (ignoreData.get(stack.getValue2())) {
                    ident1 = ident1.getIgnoringData();
                    ident2 = ident2.getIgnoringData();
                }
                if (ignoreNBT.get(stack.getValue2())) {
                    ident1 = ident1.getIgnoringNBT();
                    ident2 = ident2.getIgnoringNBT();
                }
                if (ident1.equals(ident2)) {
                    return replyIfRoom(_sinkReply, item, includeInTransit, 5);
                }
            }
        }
        if (_isDefaultRoute) {
            if (bestPriority > _sinkReplyDefault.fixedPriority.ordinal()
                    || (bestPriority == _sinkReplyDefault.fixedPriority.ordinal()
                            && bestCustomPriority >= _sinkReplyDefault.customPriority)) {
                return null;
            }
            return replyIfRoom(_sinkReplyDefault, item, includeInTransit, 1);
        }
        return null;
    }

    @Override
    public ModuleCoordinatesGuiProvider getPipeGuiProvider() {
        return NewGuiHandler.getGui(ItemSinkSlot.class).setDefaultRoute(_isDefaultRoute).setIgnoreData(ignoreData)
                .setIgnoreNBT(ignoreNBT)
                .setHasFuzzyUpgrade(_service.getUpgradeManager(slot, positionInt).isFuzzyUpgrade());
    }

    @Override
    public ModuleInHandGuiProvider getInHandGuiProvider() {
        return NewGuiHandler.getGui(ItemSinkInHand.class);
    }

    @Override
    public LogisticsModule getSubModule(int slot) {
        return null;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        _filterInventory.readFromNBT(nbttagcompound, "");
        _isDefaultRoute = nbttagcompound.getBoolean("defaultdestination");
        if (nbttagcompound.hasKey("ignoreData")) {
            ignoreData = BitSet.valueOf(nbttagcompound.getByteArray("ignoreData"));
            ignoreNBT = BitSet.valueOf(nbttagcompound.getByteArray("ignoreNBT"));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        _filterInventory.writeToNBT(nbttagcompound, "");
        nbttagcompound.setBoolean("defaultdestination", isDefaultRoute());
        nbttagcompound.setByteArray("ignoreData", ignoreData.toByteArray());
        nbttagcompound.setByteArray("ignoreNBT", ignoreNBT.toByteArray());
    }

    @Override
    public void tick() {}

    @Override
    public List<String> getClientInformation() {
        List<String> list = new ArrayList<>();
        list.add("Default: " + (isDefaultRoute() ? "Yes" : "No"));
        list.add("Filter: ");
        list.add("<inventory>");
        list.add("<that>");
        return list;
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
                PacketHandler.getPacket(ItemSinkDefault.class).setFlag(_isDefaultRoute).setModulePos(this),
                player);
    }

    @Override
    public void stopWatching(EntityPlayer player) {
        localModeWatchers.remove(player);
    }

    @Override
    public void InventoryChanged(IInventory inventory) {
        if (MainProxy.isServer(_world.getWorld())) {
            MainProxy.sendToPlayerList(
                    PacketHandler.getPacket(ModuleInventory.class)
                            .setIdentList(ItemIdentifierStack.getListFromInventory(inventory)).setModulePos(this),
                    localModeWatchers);
        }
    }

    @Override
    public IHUDModuleRenderer getHUDRenderer() {
        return HUD;
    }

    @Override
    public void handleInvContent(Collection<ItemIdentifierStack> list) {
        _filterInventory.handleItemIdentifierList(list);
    }

    /** A full default route stops taking everything and only advertises the stacks it can still top up. */
    @Override
    public boolean hasGenericInterests() {
        return _isDefaultRoute && acceptsNewItems();
    }

    /** Filter items the target inventory has no room for aren't advertised, so full sinks drop out of routing. */
    @Override
    public List<ItemIdentifier> getSpecificInterests() {
        boolean acceptsNew = acceptsNewItems();
        if (_isDefaultRoute) {
            if (acceptsNew) {
                return null;
            }
            IInventoryUtil inv = targetInventory();
            List<ItemIdentifier> li = new ArrayList<>();
            if (inv != null) {
                for (ItemIdentifier id : inv.getItems()) {
                    if (hasRoomFor(id)) {
                        li.add(id);
                    }
                }
            }
            return li;
        }
        Map<ItemIdentifier, Integer> mapIC = _filterInventory.getItemsAndCount();
        List<ItemIdentifier> li = new ArrayList<>(mapIC.size());
        for (ItemIdentifier id : mapIC.keySet()) {
            if (acceptsNew || hasRoomFor(id)) {
                li.add(id);
                li.add(id.getUndamaged());
            }
        }
        if (_service.getUpgradeManager(slot, positionInt).isFuzzyUpgrade()) {
            for (Pair<ItemIdentifierStack, Integer> stack : _filterInventory) {
                if (stack.getValue1() == null) {
                    continue;
                }
                ItemIdentifier ident = stack.getValue1().getItem();
                if (!acceptsNew && !hasRoomFor(ident)) {
                    continue;
                }
                if (ignoreData.get(stack.getValue2())) {
                    li.add(ident.getIgnoringData());
                }
                if (ignoreNBT.get(stack.getValue2())) {
                    li.add(ident.getIgnoringNBT());
                }
                if (ignoreData.get(stack.getValue2()) && ignoreNBT.get(stack.getValue2())) {
                    li.add(ident.getIgnoringData().getIgnoringNBT());
                }
            }
        }
        return li;
    }

    @Override
    public boolean interestedInAttachedInventory() {
        return false;
        // when we are default we are interested in everything anyway, otherwise we're only interested in our filter.
    }

    @Override
    public boolean interestedInUndamagedID() {
        return false;
    }

    @Override
    public boolean recievePassive() {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconTexture(IIconRegister register) {
        return register.registerIcon("logisticspipes:itemModule/ModuleItemSink");
    }

    public void setIgnoreData(BitSet ignoreData) {
        this.ignoreData = ignoreData;
    }

    public void setIgnoreNBT(BitSet ignoreNBT) {
        this.ignoreNBT = ignoreNBT;
    }

    public boolean isIgnoreData(int pos) {
        return ignoreData.get(pos);
    }

    public boolean isIgnoreNBT(int pos) {
        return ignoreNBT.get(pos);
    }

    public void setIgnoreData(int slot, EntityPlayer player) {
        if (slot < 0 || slot >= 9) {
            return;
        }
        if (MainProxy.isClient(_world.getWorld())) {
            if (player == null) {
                MainProxy.sendPacketToServer(
                        PacketHandler.getPacket(ItemSinkFuzzy.class).setPos(slot).setNBT(false).setModulePos(this));
            }
        } else {
            ignoreData.set(slot, !ignoreData.get(slot));
            ModernPacket pak = PacketHandler.getPacket(ItemSinkFuzzy.class).setIgnoreData(ignoreData)
                    .setIgnoreNBT(ignoreNBT).setModulePos(this);
            if (player != null) {
                MainProxy.sendPacketToPlayer(pak, player);
            }
            MainProxy.sendPacketToAllWatchingChunk(
                    getX(),
                    getZ(),
                    MainProxy.getDimensionForWorld(_world.getWorld()),
                    pak);
        }
    }

    public void setIgnoreNBT(int slot, EntityPlayer player) {
        if (slot < 0 || slot >= 9) {
            return;
        }
        if (MainProxy.isClient(_world.getWorld())) {
            if (player == null) {
                MainProxy.sendPacketToServer(
                        PacketHandler.getPacket(ItemSinkFuzzy.class).setPos(slot).setNBT(true).setModulePos(this));
            }
        } else {
            ignoreNBT.set(slot, !ignoreNBT.get(slot));
            ModernPacket pak = PacketHandler.getPacket(ItemSinkFuzzy.class).setIgnoreData(ignoreData)
                    .setIgnoreNBT(ignoreNBT).setModulePos(this);
            if (player != null) {
                MainProxy.sendPacketToPlayer(pak, player);
            }
            MainProxy.sendPacketToAllWatchingChunk(
                    getX(),
                    getZ(),
                    MainProxy.getDimensionForWorld(_world.getWorld()),
                    pak);
        }
    }

    public void importFromInventory() {
        if (_service == null) {
            return;
        }
        IInventoryUtil inv = _service.getPointedInventory(false);
        if (inv == null) {
            return;
        }
        int count = 0;
        for (ItemIdentifier item : inv.getItems()) {
            _filterInventory.setInventorySlotContents(count, item.makeStack(1));
            count++;
            if (count >= _filterInventory.getSizeInventory()) {
                break;
            }
        }
    }

    @Override
    public LogisticsModularUI getHandGui() {
        return new ModuleItemSinkMuiDynamic(this);
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        return new ModuleItemSinkMuiDynamic(this);
    }

    @Override
    public LogisticsModularUI getPipeGui(String prefix) {
        return new ModuleItemSinkMuiDynamic(this, prefix);
    }
}
