package logisticspipes.modules;

import java.util.ArrayList;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.dynamicModules.ModuleFluidExtractorMuiDynamic;
import logisticspipes.interfaces.IFluidModuleHost;
import logisticspipes.interfaces.ISpecialTankAccessHandler;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.modules.abstractmodules.LogisticsFluidModule;
import logisticspipes.pipes.modulehost.FluidTarget;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.FluidFilter;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.FluidTankUtil;
import logisticspipes.utils.tuples.Pair;

/**
 * Drains the tank and sends the fluid to whatever sink in the network takes it, like the fluid extractor pipe. A filter
 * of fluids picks what it drains (whitelist) or leaves (blacklist); an empty filter drains everything. With a sneaky
 * upgrade it drains through the first face of the upgrade only.
 */
public class ModuleFluidExtractor extends LogisticsFluidModule {

    private static final int TICKS_PER_EXTRACTION = 10;
    // per tank and extraction without speed upgrades; every speed upgrade doubles the fluid and adds 1 energy
    private static final int FLOW_RATE = 500;
    private static final int ENERGY_PER_EXTRACTION = 5;
    // extractions to skip after nothing in the network took the fluid
    private static final int IDLE_EXTRACTIONS = 4;

    private int idle = 0;

    private final FluidFilter filter = new FluidFilter();
    private boolean blacklist = false;

    public FluidFilter getFilter() {
        return filter;
    }

    public boolean isBlacklist() {
        return blacklist;
    }

    public void setBlacklist(boolean blacklist) {
        this.blacklist = blacklist;
    }

    /**
     * @return true if the filter lets the fluid be drained
     */
    public boolean allows(FluidStack fluid) {
        if (filter.getFluids().isEmpty()) {
            return true;
        }
        return filter.contains(fluid) != blacklist;
    }

    /**
     * @return what the tank would give from that side of the first fluid the filter allows, up to the amount; null if
     *         none. Tanks that can't be drained from that side (a machine's input tank) are skipped.
     */
    private FluidStack findFluid(TileEntity tile, ForgeDirection side, int amount) {
        ISpecialTankAccessHandler special = SimpleServiceLocator.specialTankHandler.getAccessHandlerFor(tile);
        Map<FluidIdentifier, Long> contents = FluidTankUtil.getContents(tile, side, true);
        for (FluidIdentifier fluid : contents.keySet()) {
            if (!allows(fluid.makeFluidStack(amount))) {
                continue;
            }
            FluidStack offer = simulateDrain(tile, special, side, fluid, amount);
            if (offer != null) {
                return offer;
            }
        }
        // tanks that list nothing but still give out on the untyped drain
        if (contents.isEmpty() && special == null && tile instanceof IFluidHandler tank) {
            FluidStack available = tank.drain(side, amount, false);
            return isFluid(available) && allows(available) ? available : null;
        }
        return null;
    }

    private static FluidStack simulateDrain(TileEntity tile, ISpecialTankAccessHandler special, ForgeDirection side,
            FluidIdentifier fluid, int amount) {
        if (special != null) {
            FluidStack simulated = special.drainFrom(tile, fluid, amount, false);
            return isOf(simulated, fluid) ? simulated : null;
        }
        if (!(tile instanceof IFluidHandler tank)) {
            return null;
        }
        FluidStack simulated = tank.drain(side, fluid.makeFluidStack(amount), false);
        if (isOf(simulated, fluid)) {
            return simulated;
        }
        // tanks that only answer the untyped drain
        simulated = tank.drain(side, amount, false);
        return isOf(simulated, fluid) ? simulated : null;
    }

    private static boolean isFluid(FluidStack stack) {
        return stack != null && stack.amount > 0 && stack.getFluid() != null;
    }

    private static boolean isOf(FluidStack stack, FluidIdentifier fluid) {
        return isFluid(stack) && fluid.equals(FluidIdentifier.get(stack));
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        filter.readFromNBT(nbt);
        blacklist = nbt.getBoolean("blacklist");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        filter.writeToNBT(nbt);
        nbt.setBoolean("blacklist", blacklist);
    }

    @Override
    public void tick() {
        IFluidModuleHost host = getHost();
        if (host == null || !_service.isNthTick(TICKS_PER_EXTRACTION)) {
            return;
        }
        if (idle > 0) {
            idle--;
            return;
        }
        boolean noTaker = false;
        boolean sent = false;
        for (FluidTarget target : getTargets()) {
            switch (extractFrom(host, target)) {
                case NO_TAKER:
                    noTaker = true;
                    break;
                case NO_ENERGY:
                    return;
                case DONE:
                    sent = true;
                    break;
                default:
                    break;
            }
        }
        // pause only when nothing went out: one tank without a taker must not slow down the others
        if (noTaker && !sent) {
            idle = IDLE_EXTRACTIONS;
        }
    }

    private enum Extraction {
        DONE,
        EMPTY,
        NO_TAKER,
        NO_ENERGY
    }

    private Extraction extractFrom(IFluidModuleHost host, FluidTarget target) {
        if (!(target.tile instanceof IFluidHandler)) {
            return Extraction.EMPTY;
        }
        int speed = getSpeedLevel();
        FluidStack available = findFluid(target.tile, target.side, FLOW_RATE << speed);
        if (available == null || available.amount <= 0) {
            return Extraction.EMPTY;
        }
        Pair<Integer, Integer> reply = SimpleServiceLocator.logisticsFluidManager
                .getBestReply(available, host.getRouter(), new ArrayList<>());
        if (reply.getValue1() == 0 || reply.getValue2() <= 0) {
            return Extraction.NO_TAKER;
        }
        if (!_service.useEnergy(ENERGY_PER_EXTRACTION + speed)) {
            return Extraction.NO_ENERGY;
        }
        FluidStack drained = FluidTankUtil.drain(
                target.tile,
                target.side,
                FluidIdentifier.get(available),
                Math.min(available.amount, reply.getValue2()));
        if (drained == null || drained.amount <= 0) {
            return Extraction.EMPTY;
        }
        sendFluid(drained, reply.getValue1(), TransportMode.Passive, null);
        return Extraction.DONE;
    }

    @Override
    public boolean hasGui() {
        return false;
    }

    @Override
    public LogisticsModularUI getHandGui() {
        return new ModuleFluidExtractorMuiDynamic(this, "");
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        return new ModuleFluidExtractorMuiDynamic(this, "");
    }

    @Override
    public LogisticsModularUI getPipeGui(String prefix) {
        return new ModuleFluidExtractorMuiDynamic(this, prefix);
    }
}
