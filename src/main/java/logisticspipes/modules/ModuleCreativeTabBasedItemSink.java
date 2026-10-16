package logisticspipes.modules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.api.IMUICompatibleModule;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.dynamicModules.ModuleCreativeTabBasedItemSinkMuiDynamic;
import logisticspipes.interfaces.IClientInformationProvider;
import logisticspipes.interfaces.IModuleWatchReciver;
import logisticspipes.modules.abstractmodules.LogisticsGuiModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.network.NewGuiHandler;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractguis.ModuleCoordinatesGuiProvider;
import logisticspipes.network.abstractguis.ModuleInHandGuiProvider;
import logisticspipes.network.guis.module.inhand.StringBasedItemSinkModuleGuiInHand;
import logisticspipes.network.guis.module.inpipe.StringBasedItemSinkModuleGuiSlot;
import logisticspipes.network.packets.module.ItemSinkListPacket;
import logisticspipes.pipes.PipeLogisticsChassi.ChassiTargetInformation;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.PlayerCollectionList;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.SinkReply.FixedPriority;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierInventory;

public class ModuleCreativeTabBasedItemSink extends LogisticsGuiModule
        implements IClientInformationProvider, IModuleWatchReciver, IMUICompatibleModule {

    public static final int MAX_ENTRIES = 9;

    public final List<String> tabList = new LinkedList<>();
    private final Set<String> tabSet = new HashSet<>();

    // scratch, single-slot inventory used only by the MUI to identify an item's owning creative tab - never persisted
    private final ItemIdentifierInventory analyseInventory = new ItemIdentifierInventory(1, "Analyse Slot", 1);

    private final PlayerCollectionList localModeWatchers = new PlayerCollectionList();

    private SinkReply _sinkReply;

    @Override
    public void registerPosition(ModulePositionType slot, int positionInt) {
        super.registerPosition(slot, positionInt);
        _sinkReply = new SinkReply(
                FixedPriority.ModBasedItemSink,
                0,
                true,
                false,
                5,
                0,
                new ChassiTargetInformation(getPositionInt()));
    }

    @Override
    public SinkReply sinksItem(ItemIdentifier item, int bestPriority, int bestCustomPriority, boolean allowDefault,
            boolean includeInTransit) {
        if (bestPriority > _sinkReply.fixedPriority.ordinal() || (bestPriority == _sinkReply.fixedPriority.ordinal()
                && bestCustomPriority >= _sinkReply.customPriority)) {
            return null;
        }

        if (tabSet.contains(item.getCreativeTabName())) {
            if (_service.canUseEnergy(5)) {
                return _sinkReply;
            }
        }
        return null;
    }

    @Override
    protected ModuleCoordinatesGuiProvider getPipeGuiProvider() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        return NewGuiHandler.getGui(StringBasedItemSinkModuleGuiSlot.class).setNbt(nbt);
    }

    @Override
    protected ModuleInHandGuiProvider getInHandGuiProvider() {
        return NewGuiHandler.getGui(StringBasedItemSinkModuleGuiInHand.class);
    }

    @Override
    public LogisticsModule getSubModule(int slot) {
        return null;
    }

    private void buildTabSet() {
        tabSet.clear();
        tabSet.addAll(tabList);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        tabList.clear();
        int limit = nbttagcompound.getInteger("listSize");
        for (int i = 0; i < limit; i++) {
            tabList.add(nbttagcompound.getString("Mod" + i));
        }
        buildTabSet();
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        nbttagcompound.setInteger("listSize", tabList.size());
        for (int i = 0; i < tabList.size(); i++) {
            nbttagcompound.setString("Mod" + i, tabList.get(i));
        }
    }

    @Override
    public void tick() {}

    @Override
    public List<String> getClientInformation() {
        List<String> list = new ArrayList<>();
        list.add("Tabs: ");
        list.addAll(tabList);
        return list;
    }

    @Override
    public void startWatching(EntityPlayer player) {
        localModeWatchers.add(player);
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        MainProxy.sendPacketToPlayer(
                PacketHandler.getPacket(ItemSinkListPacket.class).setNbt(nbt).setModulePos(this),
                player);
    }

    @Override
    public void stopWatching(EntityPlayer player) {
        localModeWatchers.remove(player);
    }

    @Override
    public boolean hasGenericInterests() {
        return true;
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
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconTexture(IIconRegister register) {
        return register.registerIcon("logisticspipes:itemModule/ModuleCreativeTabBasedItemSink");
    }

    public ItemIdentifierInventory getAnalyseInventory() {
        return analyseInventory;
    }

    public void addTab(String tab) {
        if (!tabList.contains(tab) && tabList.size() < MAX_ENTRIES) {
            tabList.add(tab);
            buildTabSet();
        }
    }

    public void removeTab(String tab) {
        if (tabList.remove(tab)) {
            buildTabSet();
        }
    }

    @Override
    public LogisticsModularUI getHandGui() {
        return new ModuleCreativeTabBasedItemSinkMuiDynamic(this);
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        return new ModuleCreativeTabBasedItemSinkMuiDynamic(this);
    }

    @Override
    public LogisticsModularUI getPipeGui(String prefix) {
        return new ModuleCreativeTabBasedItemSinkMuiDynamic(this, prefix);
    }
}
