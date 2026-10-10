package logisticspipes.modules.abstractmodules;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.api.IMUICompatibleModule;
import logisticspipes.interfaces.IFluidModuleHost;
import logisticspipes.interfaces.ISpecialTankAccessHandler;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.network.abstractguis.ModuleCoordinatesGuiProvider;
import logisticspipes.network.abstractguis.ModuleInHandGuiProvider;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.item.ItemIdentifier;

/**
 * Base of the modules that work on the tank a fluid chassis points at. Fluid travels through the network as a fluid
 * container item; the module fills and drains the tank directly, the pipe itself holds no fluid.
 */
public abstract class LogisticsFluidModule extends LogisticsGuiModule implements IMUICompatibleModule {

    // a bucket, some tanks only take whole ones
    private static final int ROOM_PROBE_AMOUNT = 1000;

    /**
     * @return the pipe this module sits in, or null while it is held in hand or sits in a pipe without fluid support
     */
    protected IFluidModuleHost getHost() {
        return _service instanceof IFluidModuleHost host ? host : null;
    }

    protected TileEntity getTankTile() {
        IFluidModuleHost host = getHost();
        return host == null ? null : host.getFluidTargetTile();
    }

    protected IFluidHandler getTank() {
        return getTankTile() instanceof IFluidHandler handler ? handler : null;
    }

    public ForgeDirection getTankSide() {
        IFluidModuleHost host = getHost();
        return host == null ? ForgeDirection.UNKNOWN : host.getFluidTargetSide(getPositionInt());
    }

    /**
     * Takes fluid that was routed to the host.
     *
     * @return the amount put into the tank
     */
    public int acceptFluid(FluidStack stack) {
        return fillTank(stack, stack.amount);
    }

    protected int fillTank(FluidStack stack, int max) {
        TileEntity tile = getTankTile();
        if (tile == null || max <= 0) {
            return 0;
        }
        FluidStack toFill = stack.copy();
        toFill.amount = Math.min(stack.amount, max);
        ISpecialTankAccessHandler special = getSpecialAccessHandler(tile);
        if (special != null) {
            return special.fillInto(tile, toFill, true);
        }
        return tile instanceof IFluidHandler tank ? tank.fill(getTankSide(), toFill, true) : 0;
    }

    /**
     * @return how much of that fluid the tank still takes
     */
    protected long getTankRoom(FluidIdentifier fluid) {
        TileEntity tile = getTankTile();
        if (tile == null) {
            return 0;
        }
        ISpecialTankAccessHandler special = getSpecialAccessHandler(tile);
        if (special != null) {
            return special.fillInto(tile, fluid.makeFluidStack(Integer.MAX_VALUE), false);
        }
        if (!(tile instanceof IFluidHandler tank)) {
            return 0;
        }
        ForgeDirection side = getTankSide();
        // make sure this side can be filled at all, it may be an output tank
        if (tank.fill(side, fluid.makeFluidStack(ROOM_PROBE_AMOUNT), false) <= 0) {
            return 0;
        }
        return fluid.getFreeSpaceInsideTank(tank, side);
    }

    /**
     * @return the fluids that can be drained from the tank
     */
    protected Map<FluidIdentifier, Integer> getTankContents() {
        Map<FluidIdentifier, Integer> map = new HashMap<>();
        TileEntity tile = getTankTile();
        if (tile == null) {
            return map;
        }
        ISpecialTankAccessHandler special = getSpecialAccessHandler(tile);
        if (special != null) {
            for (Entry<FluidIdentifier, Long> entry : special.getAvailableLiquid(tile).entrySet()) {
                addClamped(map, entry.getKey(), entry.getValue());
            }
            return map;
        }
        if (!(tile instanceof IFluidHandler handler)) {
            return map;
        }
        ForgeDirection side = getTankSide();
        FluidTankInfo[] tanks = handler.getTankInfo(side);
        if (tanks == null) {
            return map;
        }
        for (FluidTankInfo tank : tanks) {
            if (tank == null || tank.fluid == null || tank.fluid.getFluidID() == 0) {
                continue;
            }
            if (handler.canDrain(side, tank.fluid.getFluid())) {
                addClamped(map, FluidIdentifier.get(tank.fluid), tank.fluid.amount);
            }
        }
        return map;
    }

    /**
     * @return the amount of that fluid in the tank, drainable or not
     */
    protected int getStoredAmount(FluidIdentifier fluid) {
        TileEntity tile = getTankTile();
        ISpecialTankAccessHandler special = getSpecialAccessHandler(tile);
        if (special != null) {
            Long stored = special.getAvailableLiquid(tile).get(fluid);
            return stored == null ? 0 : (int) Math.min(stored, Integer.MAX_VALUE);
        }
        IFluidHandler tank = getTank();
        if (tank == null) {
            return 0;
        }
        FluidTankInfo[] tanks = tank.getTankInfo(getTankSide());
        if (tanks == null) {
            return 0;
        }
        long stored = 0;
        for (FluidTankInfo info : tanks) {
            if (info != null && info.fluid != null && fluid.equals(FluidIdentifier.get(info.fluid))) {
                stored += info.fluid.amount;
            }
        }
        return (int) Math.min(stored, Integer.MAX_VALUE);
    }

    /**
     * Drains up to that amount of the fluid from the tank.
     *
     * @return what was drained, or null
     */
    protected FluidStack drainTank(FluidIdentifier fluid, int amount) {
        TileEntity tile = getTankTile();
        if (tile == null || amount <= 0) {
            return null;
        }
        ISpecialTankAccessHandler special = getSpecialAccessHandler(tile);
        if (special != null) {
            FluidStack simulated = special.drainFrom(tile, fluid, amount, false);
            if (simulated == null || simulated.amount <= 0 || !fluid.equals(FluidIdentifier.get(simulated))) {
                return null;
            }
            return special.drainFrom(tile, fluid, amount, true);
        }
        if (!(tile instanceof IFluidHandler handler)) {
            return null;
        }
        ForgeDirection side = getTankSide();
        FluidStack simulated = handler.drain(side, fluid.makeFluidStack(amount), false);
        if (simulated != null && simulated.amount > 0 && fluid.equals(FluidIdentifier.get(simulated))) {
            return handler.drain(side, fluid.makeFluidStack(simulated.amount), true);
        }
        // tanks that only answer the untyped drain
        simulated = handler.drain(side, amount, false);
        if (simulated == null || simulated.amount <= 0 || !fluid.equals(FluidIdentifier.get(simulated))) {
            return null;
        }
        return handler.drain(side, simulated.amount, true);
    }

    /**
     * Sends fluid into the network as a fluid container.
     */
    protected IRoutedItem sendFluid(FluidStack stack, int destination, TransportMode mode,
            IAdditionalTargetInformation info) {
        IFluidModuleHost host = getHost();
        IRoutedItem item = SimpleServiceLocator.routedItemHelper
                .createNewTravelItem(SimpleServiceLocator.logisticsFluidManager.getFluidContainer(stack));
        item.setDestination(destination);
        item.setTransportMode(mode);
        if (info != null) {
            item.setAdditionalTargetInformation(info);
        }
        host.queueRoutedItem(item, host.getPointedOrientation());
        return item;
    }

    /**
     * Called before the pipe holding this module is removed.
     */
    public void onHostRemoved() {}

    private static ISpecialTankAccessHandler getSpecialAccessHandler(TileEntity tile) {
        return SimpleServiceLocator.specialTankHandler.getAccessHandlerFor(tile);
    }

    private static void addClamped(Map<FluidIdentifier, Integer> map, FluidIdentifier fluid, long amount) {
        long total = amount + map.getOrDefault(fluid, 0);
        map.put(fluid, (int) Math.min(total, Integer.MAX_VALUE));
    }

    @Override
    public SinkReply sinksItem(ItemIdentifier stack, int bestPriority, int bestCustomPriority, boolean allowDefault,
            boolean includeInTransit) {
        return null;
    }

    @Override
    public LogisticsModule getSubModule(int slot) {
        return null;
    }

    @Override
    public void tick() {}

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {}

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {}

    @Override
    public boolean hasGenericInterests() {
        return false;
    }

    @Override
    public Collection<ItemIdentifier> getSpecificInterests() {
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

    // fluid modules only have a ModularUI
    @Override
    protected ModuleCoordinatesGuiProvider getPipeGuiProvider() {
        return null;
    }

    @Override
    protected ModuleInHandGuiProvider getInHandGuiProvider() {
        return null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconTexture(IIconRegister register) {
        return register.registerIcon("logisticspipes:itemModule/" + getClass().getSimpleName());
    }
}
