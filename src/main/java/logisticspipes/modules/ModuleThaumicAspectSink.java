package logisticspipes.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.api.IMUICompatibleModule;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.dynamicModules.ModuleThaumicAspectSinkMuiDynamic;
import logisticspipes.interfaces.IClientInformationProvider;
import logisticspipes.interfaces.IModuleWatchReciver;
import logisticspipes.modules.abstractmodules.IPrioritizedModule;
import logisticspipes.modules.abstractmodules.LogisticsGuiModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.network.NewGuiHandler;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractguis.ModuleCoordinatesGuiProvider;
import logisticspipes.network.abstractguis.ModuleInHandGuiProvider;
import logisticspipes.network.guis.module.inhand.ThaumicAspectSinkModuleInHand;
import logisticspipes.network.guis.module.inpipe.ThaumicAspectSinkModuleSlot;
import logisticspipes.network.packets.module.ThaumicAspectsSinkList;
import logisticspipes.pipes.modulehost.ModuleSlotTargetInformation;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.routing.astar.CategoryInterests;
import logisticspipes.routing.astar.CategoryInterestsCache;
import logisticspipes.routing.astar.ItemCategory;
import logisticspipes.utils.PlayerCollectionList;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.SinkReply.FixedPriority;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierInventory;

public class ModuleThaumicAspectSink extends LogisticsGuiModule
        implements IClientInformationProvider, IModuleWatchReciver, IMUICompatibleModule, IPrioritizedModule {

    public static final int MAX_ASPECTS = 9;

    // scratch, single-slot inventory the MUI uses to look up the aspects of an item; never saved
    private final ItemIdentifierInventory analyseInventory = new ItemIdentifierInventory(1, "Analyse Slot", 1);

    public final List<String> aspectList = new LinkedList<>();
    private final CategoryInterestsCache categoryInterests = new CategoryInterestsCache(ItemCategory.THAUMIC_ASPECT);

    private final PlayerCollectionList localModeWatchers = new PlayerCollectionList();

    private SinkReply _sinkReply;

    @Override
    public void registerPosition(ModulePositionType slot, int positionInt) {
        super.registerPosition(slot, positionInt);
        _sinkReply = new SinkReply(
                FixedPriority.ItemSink,
                -2,
                true,
                false,
                5,
                0,
                new ModuleSlotTargetInformation(getPositionInt()));
    }

    @Override
    public SinkReply sinksItem(ItemIdentifier item, int bestPriority, int bestCustomPriority, boolean allowDefault,
            boolean includeInTransit) {
        SinkReply reply = prioritized(_sinkReply);
        if (bestPriority > reply.fixedPriority.ordinal()
                || (bestPriority == reply.fixedPriority.ordinal() && bestCustomPriority >= reply.customPriority)) {
            return null;
        }
        if (isOfInterest(item)) {
            return reply;
        }
        return null;
    }

    private boolean isOfInterest(ItemIdentifier itemID) {
        if (itemID == null || aspectList.size() == 0) {
            return false;
        }
        ItemStack item = itemID.makeNormalStack(1);
        List<String> itemAspectList = SimpleServiceLocator.thaumCraftProxy.getListOfTagsForStack(item);
        if (itemAspectList == null || itemAspectList.size() == 0) {
            return false;
        }
        for (String s : itemAspectList) {
            if (aspectList.contains(s)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public LogisticsModule getSubModule(int slot) {
        return null;
    }

    @Override
    public void tick() {}

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        readPriority(nbttagcompound);
        aspectList.clear();
        int size = nbttagcompound.getInteger("aspectTagListSize");
        if (size <= 0) {
            return;
        }
        for (int i = 0; i < size; i++) {
            aspectList.add(nbttagcompound.getString("aspectTag" + i));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        writePriority(nbttagcompound);
        nbttagcompound.setInteger("aspectTagListSize", aspectList.size());
        if (aspectList.size() <= 0) {
            return;
        }
        for (int i = 0; i < aspectList.size(); i++) {
            nbttagcompound.setString("aspectTag" + i, aspectList.get(i));
        }
    }

    @Override
    public void startWatching(EntityPlayer player) {
        localModeWatchers.add(player);
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        MainProxy.sendPacketToPlayer(
                PacketHandler.getPacket(ThaumicAspectsSinkList.class).setTag(nbt).setModulePos(this),
                player);
    }

    @Override
    public void stopWatching(EntityPlayer player) {
        localModeWatchers.remove(player);
    }

    public void aspectListChanged() {
        if (MainProxy.isServer(_world.getWorld())) {
            NBTTagCompound nbt = new NBTTagCompound();
            writeToNBT(nbt);
            MainProxy.sendToPlayerList(
                    PacketHandler.getPacket(ThaumicAspectsSinkList.class).setTag(nbt).setModulePos(this),
                    localModeWatchers);
        } else {
            NBTTagCompound nbt = new NBTTagCompound();
            writeToNBT(nbt);
            MainProxy.sendPacketToServer(
                    PacketHandler.getPacket(ThaumicAspectsSinkList.class).setTag(nbt).setModulePos(this));
        }
    }

    @Override
    public List<String> getClientInformation() {
        List<String> info = new ArrayList<>();
        info.add("Aspects: ");
        if (aspectList.size() == 0) {
            info.add("none");
        }
        for (String s : aspectList) {
            // info.add(" - " + SimpleServiceLocator.thaumCraftProxy.getNameForTagID(aspectList.get(i)));
            info.add(" - " + s);
        }
        return info;
    }

    @Override
    protected ModuleCoordinatesGuiProvider getPipeGuiProvider() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return NewGuiHandler.getGui(ThaumicAspectSinkModuleSlot.class).setNbt(tag);
    }

    @Override
    protected ModuleInHandGuiProvider getInHandGuiProvider() {
        return NewGuiHandler.getGui(ThaumicAspectSinkModuleInHand.class);
    }

    public ItemIdentifierInventory getAnalyseInventory() {
        return analyseInventory;
    }

    /**
     * @param playerName the player looking, null to skip the check: Thaumcraft shows the aspects of an item only to a
     *                   player who scanned it, the window does the same
     * @return the aspect tags of the item, empty if it has none or the player hasn't scanned it
     */
    public static List<String> aspectsOf(ItemStack stack, String playerName) {
        if (stack == null) {
            return Collections.emptyList();
        }
        if (playerName != null && !SimpleServiceLocator.thaumCraftProxy.isScannedObject(stack, playerName)) {
            return Collections.emptyList();
        }
        List<String> aspects = SimpleServiceLocator.thaumCraftProxy.getListOfTagsForStack(stack);
        return aspects == null ? Collections.emptyList() : aspects;
    }

    /**
     * Adds the aspects that aren't in the list yet, as far as there is room. For the MUI window, which syncs the list
     * itself: no legacy packets (a module in hand has no position to put into them).
     */
    public void addAspects(List<String> aspects) {
        for (String aspect : aspects) {
            if (!aspectList.contains(aspect) && aspectList.size() < MAX_ASPECTS) {
                aspectList.add(aspect);
            }
        }
    }

    /**
     * Removes the aspect, for the MUI window (see {@link #addAspects}).
     */
    public void removeAspect(String aspect) {
        aspectList.remove(aspect);
    }

    /**
     * Sets the list, as the window's list sync does.
     */
    public void setAspects(List<String> aspects) {
        aspectList.clear();
        for (String aspect : aspects) {
            if (aspectList.size() < MAX_ASPECTS) {
                aspectList.add(aspect);
            }
        }
    }

    public void guiAddAspect(String aspect) {
        if (aspectList.contains(aspect) || aspectList.size() >= MAX_ASPECTS) {
            return;
        }
        aspectList.add(aspect);
        aspectListChanged();
    }

    public void guiRemoveAspect(String aspect) {
        if (!aspectList.contains(aspect)) {
            return;
        }
        aspectList.remove(aspect);
        aspectListChanged();
    }

    @Override
    public boolean hasGenericInterests() {
        return false;
    }

    /**
     * Only the items with one of the filter aspects are offered, instead of every item.
     */
    @Override
    public CategoryInterests getCategoryInterests() {
        return categoryInterests.get(aspectList);
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
        return register.registerIcon("logisticspipes:itemModule/ModuleThaumicAspectSink");
    }

    @Override
    public LogisticsModularUI getHandGui() {
        return new ModuleThaumicAspectSinkMuiDynamic(this);
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        return new ModuleThaumicAspectSinkMuiDynamic(this);
    }

    @Override
    public LogisticsModularUI getPipeGui(String prefix) {
        return new ModuleThaumicAspectSinkMuiDynamic(this, prefix);
    }
}
