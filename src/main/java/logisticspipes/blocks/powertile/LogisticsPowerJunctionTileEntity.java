package logisticspipes.blocks.powertile;

import java.util.List;

import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.gtnewhorizon.gtnhlib.blockpos.IBlockPos;

import cofh.api.energy.IEnergyHandler;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ic2.api.energy.tile.IEnergySink;
import logisticspipes.Tags;
import logisticspipes.api.ILogisticsPowerProvider;
import logisticspipes.blocks.LogisticsSolidTileEntity;
import logisticspipes.gui.hud.HUDPowerLevel;
import logisticspipes.gui.modularUI.blocks.PowerJunctionMui;
import logisticspipes.interfaces.IBlockWatchingHandler;
import logisticspipes.interfaces.IHeadUpDisplayBlockRendererProvider;
import logisticspipes.interfaces.IHeadUpDisplayRenderer;
import logisticspipes.interfaces.IPowerLevelDisplay;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.block.PowerJunctionLevel;
import logisticspipes.network.packets.hud.HUDStartBlockWatchingPacket;
import logisticspipes.network.packets.hud.HUDStopBlockWatchingPacket;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.proxy.computers.interfaces.CCCommand;
import logisticspipes.proxy.computers.interfaces.CCType;
import logisticspipes.renderer.LogisticsHUDRenderer;
import logisticspipes.utils.PlayerCollectionList;
import lombok.Getter;

@Optional.InterfaceList({ @Optional.Interface(modid = "IC2", iface = "ic2.api.energy.tile.IEnergySink"),
        @Optional.Interface(modid = "CoFHAPI|energy", iface = "cofh.api.energy.IEnergyHandler"),
        @Optional.Interface(modid = "BuildCraft|Transport", iface = "buildcraft.api.power.IPowerReceptor"), })
@CCType(name = "LogisticsPowerJunction")
public class LogisticsPowerJunctionTileEntity extends LogisticsSolidTileEntity implements IGuiHolder<PosGuiData>,
        ILogisticsPowerProvider, IPowerLevelDisplay, IHeadUpDisplayBlockRendererProvider, IBlockWatchingHandler, IEnergySink, IEnergyHandler, PowerPoolStorage.Member {

    public Object OPENPERIPHERAL_IGNORE; // Tell OpenPeripheral to ignore this class

    public static final int IC2Multiplier = 2;
    public static final int RFDivisor = 2;
    /** What one junction adds to the capacity of its power pool. */
    public static final int MAX_STORAGE = 2000000;

    /**
     * The pool this junction stores into (server only). Junctions hold no power of their own: see
     * {@link PowerPoolStorage}. {@code null} until first used, and again after the junction left its pool.
     */
    private PowerPoolStorage pool;
    /** Energy this junction brings into a pool: what it was saved with, or its share when it left one. */
    private long savedEnergy = 0;
    // what HUD clients show: the pool's level and capacity, sent by the server (the GUI syncs its own)
    private int displayStored = 0;
    private int displayCapacity = MAX_STORAGE;
    private long lastUpdateStorage = -1;
    private long lastUpdateCapacity = -1;
    private double internalBuffer = 0;

    // small buffer to hold a fractional LP worth of RF
    private int internalRFbuffer = 0;

    private boolean addedToEnergyNet = false;

    private boolean init = false;
    private final PlayerCollectionList watcherList = new PlayerCollectionList();
    private final IHeadUpDisplayRenderer HUD;

    @Getter
    private IBlockPos pos;

    public LogisticsPowerJunctionTileEntity() {
        HUD = new HUDPowerLevel(this);
    }

    /** The pool this junction stores into; a pool of its own until the network binds it (server only). */
    @Override
    public PowerPoolStorage pool() {
        if (pool == null) {
            pool = PowerPoolStorage.single(this, savedEnergy);
            savedEnergy = 0;
        }
        return pool;
    }

    @Override
    public void setPool(PowerPoolStorage pool) {
        this.pool = pool;
    }

    @Override
    public long poolCapacity() {
        return MAX_STORAGE;
    }

    /** Leave the pool, keeping this junction's share for its NBT. */
    private void leavePool() {
        if (pool != null) {
            savedEnergy = pool.detach(this);
            pool = null;
        }
    }

    public boolean needsMorePower() {
        return pool().needsMorePower();
    }

    @Override
    public boolean useEnergy(int amount, List<Object> providersToIgnore) {
        if (providersToIgnore != null && providersToIgnore.contains(this)) {
            return false;
        }
        return pool().useEnergy(amount);
    }

    @Override
    public boolean canUseEnergy(int amount, List<Object> providersToIgnore) {
        if (providersToIgnore != null && providersToIgnore.contains(this)) {
            return false;
        }
        return pool().canUseEnergy(amount);
    }

    @Override
    public boolean useEnergy(int amount) {
        return useEnergy(amount, null);
    }

    public int freeSpace() {
        return clamp(pool().freeSpace());
    }

    private static int clamp(long value) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }

    public void updateClients() {
        PowerPoolStorage p = pool();
        long stored = p.getStored();
        long capacity = p.getCapacity();
        MainProxy.sendToPlayerList(
                PacketHandler.getPacket(PowerJunctionLevel.class).setCapacity(clamp(capacity)).setInteger(clamp(stored))
                        .setPosX(xCoord).setPosY(yCoord).setPosZ(zCoord),
                watcherList);
        lastUpdateStorage = stored;
        lastUpdateCapacity = capacity;
    }

    @Override
    public boolean canUseEnergy(int amount) {
        return canUseEnergy(amount, null);
    }

    public void addEnergy(float amount) {
        if (MainProxy.isClient(getWorld())) {
            return;
        }
        pool().addEnergy((long) amount);
    }

    @Override
    public void readFromNBT(NBTTagCompound par1nbtTagCompound) {
        super.readFromNBT(par1nbtTagCompound);
        // this junction's share of its pool when it was saved
        savedEnergy = par1nbtTagCompound.getInteger("powerLevel");
    }

    @Override
    public void writeToNBT(NBTTagCompound par1nbtTagCompound) {
        super.writeToNBT(par1nbtTagCompound);
        long share = pool != null ? pool.shareOf(this) : savedEnergy;
        par1nbtTagCompound.setInteger("powerLevel", clamp(share));
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (MainProxy.isServer(getWorld()) && !watcherList.isEmpty()) {
            PowerPoolStorage p = pool();
            if (p.getStored() != lastUpdateStorage || p.getCapacity() != lastUpdateCapacity) {
                updateClients();
            }
        }
        if (!init) {
            if (MainProxy.isClient(getWorld())) {
                LogisticsHUDRenderer.instance().add(this);
            }
            if (!addedToEnergyNet) {
                SimpleServiceLocator.IC2Proxy.registerToEneryNet(this);
                addedToEnergyNet = true;
            }
            init = true;
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (MainProxy.isServer(getWorld())) {
            leavePool();
        }
        if (MainProxy.isClient(getWorld())) {
            LogisticsHUDRenderer.instance().remove(this);
        }
        if (addedToEnergyNet) {
            SimpleServiceLocator.IC2Proxy.unregisterToEneryNet(this);
            addedToEnergyNet = false;
        }
    }

    @Override
    public void validate() {
        super.validate();
        pos = new BlockPos(xCoord, yCoord, zCoord);

        if (MainProxy.isClient(getWorld())) {
            init = false;
        }
        if (!addedToEnergyNet) {
            init = false;
        }
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (MainProxy.isServer(getWorld())) {
            leavePool();
        }
        if (MainProxy.isClient(getWorld())) {
            LogisticsHUDRenderer.instance().remove(this);
        }
        if (addedToEnergyNet) {
            SimpleServiceLocator.IC2Proxy.unregisterToEneryNet(this);
            addedToEnergyNet = false;
        }
    }

    @Override
    @CCCommand(description = "Returns the power stored in the junction's power pool")
    public int getPowerLevel() {
        if (MainProxy.isClient(getWorld())) {
            return displayStored;
        }
        return clamp(pool().getStored());
    }

    @Override
    public int getDisplayPowerLevel() {
        return getPowerLevel();
    }

    @Override
    public String getBrand() {
        return "LP";
    }

    @Override
    @CCCommand(description = "Returns the max. power the junction's power pool can store")
    public int getMaxStorage() {
        if (MainProxy.isClient(getWorld())) {
            return displayCapacity;
        }
        return clamp(pool().getCapacity());
    }

    @Override
    public int getChargeState() {
        long capacity = getMaxStorage();
        return capacity <= 0 ? 0 : (int) (getPowerLevel() * 100L / capacity);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModularScreen createScreen(PosGuiData data, ModularPanel mainPanel) {
        return new ModularScreen("LogisticsPipes", mainPanel);
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        return new PowerJunctionMui(this).buildUI(syncManager);
    }

    public void handlePowerPacket(int stored, int capacity) {
        if (MainProxy.isClient(getWorld())) {
            displayStored = stored;
            displayCapacity = capacity;
        }
    }

    @Override
    public IHeadUpDisplayRenderer getRenderer() {
        return HUD;
    }

    @Override
    public int getX() {
        return xCoord;
    }

    @Override
    public int getY() {
        return yCoord;
    }

    @Override
    public int getZ() {
        return zCoord;
    }

    @Override
    public World getWorld() {
        return getWorldObj();
    }

    @Override
    public void startWatching() {
        MainProxy.sendPacketToServer(
                PacketHandler.getPacket(HUDStartBlockWatchingPacket.class).setPosX(getX()).setPosY(getY())
                        .setPosZ(getZ()));
    }

    @Override
    public void stopWatching() {
        MainProxy.sendPacketToServer(
                PacketHandler.getPacket(HUDStopBlockWatchingPacket.class).setPosX(getX()).setPosY(getY())
                        .setPosZ(getZ()));
    }

    @Override
    public void playerStartWatching(EntityPlayer player) {
        watcherList.add(player);
        updateClients();
    }

    @Override
    public void playerStopWatching(EntityPlayer player) {
        watcherList.remove(player);
    }

    @Override
    public boolean isHUDExistent() {
        return getWorld().getTileEntity(xCoord, yCoord, zCoord) == this;
    }

    @Override
    public void func_145828_a(CrashReportCategory par1CrashReportCategory) {
        super.func_145828_a(par1CrashReportCategory);
        par1CrashReportCategory.addCrashSection("LP-Version", Tags.VERSION);
    }

    @Override
    @Optional.Method(modid = "IC2")
    public boolean acceptsEnergyFrom(TileEntity tile, ForgeDirection dir) {
        return true;
    }

    private void transferFromIC2Buffer() {
        if (freeSpace() > 0 && internalBuffer >= 1) {
            int addAmount = Math.min((int) Math.floor(internalBuffer), freeSpace());
            addEnergy(addAmount);
            internalBuffer -= addAmount;
        }
    }

    @Override
    @Optional.Method(modid = "IC2")
    public double getDemandedEnergy() {
        if (!addedToEnergyNet) {
            return 0;
        }
        transferFromIC2Buffer();
        return Math.floorDiv(
                freeSpace() + LogisticsPowerJunctionTileEntity.IC2Multiplier - 1,
                LogisticsPowerJunctionTileEntity.IC2Multiplier);
    }

    @Override
    @Optional.Method(modid = "IC2")
    public double injectEnergy(ForgeDirection directionFrom, double amount, double voltage) {
        internalBuffer += amount * LogisticsPowerJunctionTileEntity.IC2Multiplier;
        transferFromIC2Buffer();
        return 0;
    }

    @Override
    @Optional.Method(modid = "IC2")
    public int getSinkTier() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isHUDInvalid() {
        return isInvalid();
    }

    @Override
    @Optional.Method(modid = "CoFHAPI|energy")
    public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate) {
        if (freeSpace() < 1) {
            return 0;
        }
        int RFspace = freeSpace() * LogisticsPowerJunctionTileEntity.RFDivisor - internalRFbuffer;
        int RFtotake = Math.min(maxReceive, RFspace);
        if (!simulate) {
            addEnergy((float) RFtotake / LogisticsPowerJunctionTileEntity.RFDivisor);
            internalRFbuffer += RFtotake % LogisticsPowerJunctionTileEntity.RFDivisor;
            if (internalRFbuffer >= LogisticsPowerJunctionTileEntity.RFDivisor) {
                addEnergy(1);
                internalRFbuffer -= LogisticsPowerJunctionTileEntity.RFDivisor;
            }
        }
        return RFtotake;
    }

    @Override
    @Optional.Method(modid = "CoFHAPI|energy")
    public int extractEnergy(ForgeDirection from, int maxExtract, boolean simulate) {
        return 0;
    }

    @Override
    @Optional.Method(modid = "CoFHAPI|energy")
    public boolean canConnectEnergy(ForgeDirection from) {
        return true;
    }

    @Override
    @Optional.Method(modid = "CoFHAPI|energy")
    public int getEnergyStored(ForgeDirection from) {
        return clamp(pool().getStored() * LogisticsPowerJunctionTileEntity.RFDivisor + internalRFbuffer);
    }

    @Override
    @Optional.Method(modid = "CoFHAPI|energy")
    public int getMaxEnergyStored(ForgeDirection from) {
        return clamp(pool().getCapacity() * LogisticsPowerJunctionTileEntity.RFDivisor);
    }
}
