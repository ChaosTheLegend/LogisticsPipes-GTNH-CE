package logisticspipes.modules;

import java.util.ArrayList;
import java.util.Collection;
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
import logisticspipes.gui.hud.modules.HUDSimpleFilterModule;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.dynamicModules.ModuleEnchantmentSinkMuiDynamic;
import logisticspipes.interfaces.IClientInformationProvider;
import logisticspipes.interfaces.IHUDModuleHandler;
import logisticspipes.interfaces.IHUDModuleRenderer;
import logisticspipes.interfaces.IModuleInventoryReceive;
import logisticspipes.interfaces.IModuleWatchReciver;
import logisticspipes.modules.abstractmodules.IPrioritizedModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.modules.abstractmodules.LogisticsSimpleFilterModule;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.hud.HUDStartModuleWatchingPacket;
import logisticspipes.network.packets.hud.HUDStopModuleWatchingPacket;
import logisticspipes.network.packets.module.ModuleInventory;
import logisticspipes.pipes.modulehost.ModuleSlotTargetInformation;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.computers.interfaces.CCCommand;
import logisticspipes.proxy.computers.interfaces.CCType;
import logisticspipes.routing.astar.CategoryInterests;
import logisticspipes.routing.astar.ItemCategory;
import logisticspipes.utils.ISimpleInventoryEventHandler;
import logisticspipes.utils.PlayerCollectionList;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.SinkReply.FixedPriority;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierInventory;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.string.StringUtils;

/**
 * Takes enchanted items: any with an empty filter, else those whose item is in the filter (whatever its damage and
 * data). A filled filter wins over an empty one, as the old Mk2 won over the Mk1. It replaces both (the Mk2 is
 * deprecated, see {@code ModuleMigration}).
 */
@CCType(name = "EnchantmentSink Module")
public class ModuleEnchantmentSink extends LogisticsSimpleFilterModule
        implements IClientInformationProvider, IHUDModuleHandler, IModuleWatchReciver, ISimpleInventoryEventHandler,
        IModuleInventoryReceive, IMUICompatibleModule, IPrioritizedModule {

    private final ItemIdentifierInventory _filterInventory = new ItemIdentifierInventory(
            9,
            StringUtils.translate("gui.module.ModuleEnchantmentSink"),
            1);

    private static final CategoryInterests ENCHANTED = CategoryInterests.flag(ItemCategory.ENCHANTED);

    public ModuleEnchantmentSink() {
        _filterInventory.addListener(this);
    }

    private final IHUDModuleRenderer HUD = new HUDSimpleFilterModule(this);

    private final PlayerCollectionList localModeWatchers = new PlayerCollectionList();

    @Override
    @CCCommand(description = "Returns the FilterInventory of this Module")
    public ItemIdentifierInventory getFilterInventory() {
        return _filterInventory;
    }

    // without a filter, with a filter: the filtered one wins
    private SinkReply anyReply;
    private SinkReply filteredReply;

    @Override
    public void registerPosition(ModulePositionType slot, int positionInt) {
        super.registerPosition(slot, positionInt);
        anyReply = new SinkReply(
                FixedPriority.EnchantmentItemSink,
                0,
                true,
                false,
                1,
                0,
                new ModuleSlotTargetInformation(getPositionInt()));
        filteredReply = new SinkReply(
                FixedPriority.EnchantmentItemSink,
                1,
                true,
                false,
                1,
                0,
                new ModuleSlotTargetInformation(getPositionInt()));
    }

    private boolean hasFilter() {
        return !_filterInventory.isEmpty();
    }

    @Override
    public SinkReply sinksItem(ItemIdentifier item, int bestPriority, int bestCustomPriority, boolean allowDefault,
            boolean includeInTransit) {
        SinkReply reply = prioritized(hasFilter() ? filteredReply : anyReply);
        if (bestPriority > reply.fixedPriority.ordinal()
                || (bestPriority == reply.fixedPriority.ordinal() && bestCustomPriority >= reply.customPriority)) {
            return null;
        }
        if (hasFilter() && !_filterInventory.containsExcludeNBTItem(item.getUndamaged().getIgnoringNBT())) {
            return null;
        }
        return item.makeNormalStack(1).isItemEnchanted() ? reply : null;
    }

    @Override
    public LogisticsModule getSubModule(int slot) {
        return null;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        _filterInventory.readFromNBT(nbttagcompound, "");
        readPriority(nbttagcompound);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        _filterInventory.writeToNBT(nbttagcompound, "");
        writePriority(nbttagcompound);
    }

    @Override
    public void tick() {}

    @Override
    public List<String> getClientInformation() {
        List<String> list = new ArrayList<>();
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

    @Override
    /*
     * (non-Javadoc)
     * @see logisticspipes.modules.LogisticsModule#hasGenericInterests() Only looking for items in filter
     */
    public boolean hasGenericInterests() {
        return false;
    }

    /**
     * Without a filter every enchanted item is offered, as a category; with one, the filter's items (see
     * getSpecificInterests).
     */
    @Override
    public CategoryInterests getCategoryInterests() {
        return hasFilter() ? null : ENCHANTED;
    }

    @Override
    public List<ItemIdentifier> getSpecificInterests() {
        if (!hasFilter()) {
            return null;
        }
        Map<ItemIdentifier, Integer> mapIC = _filterInventory.getItemsAndCount();
        List<ItemIdentifier> li = new ArrayList<>(mapIC.size());
        li.addAll(mapIC.keySet());
        for (ItemIdentifier id : mapIC.keySet()) {
            li.add(id.getUndamaged());
            li.add(id.getUndamaged().getIgnoringNBT());
        }
        return li;
    }

    @Override
    public boolean interestedInAttachedInventory() {
        return false;
    }

    @Override
    public boolean interestedInUndamagedID() {
        return true;
    }

    @Override
    public boolean recievePassive() {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconTexture(IIconRegister register) {
        return register.registerIcon("logisticspipes:itemModule/ModuleEnchantmentSink");
    }

    @Override
    public boolean hasEffect() {
        return true;
    }

    @Override
    public LogisticsModularUI getHandGui() {
        return new ModuleEnchantmentSinkMuiDynamic(this);
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        return new ModuleEnchantmentSinkMuiDynamic(this);
    }

    @Override
    public LogisticsModularUI getPipeGui(String prefix) {
        return new ModuleEnchantmentSinkMuiDynamic(this, prefix);
    }
}
