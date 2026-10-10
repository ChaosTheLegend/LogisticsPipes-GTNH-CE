package logisticspipes.modules;

import java.util.ArrayList;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.dynamicModules.ModuleFluidInfoMuiDynamic;
import logisticspipes.interfaces.IFluidModuleHost;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.modules.abstractmodules.LogisticsFluidModule;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.tuples.Pair;

/**
 * Drains the tank and sends the fluid to whatever sink in the network takes it, like the fluid extractor pipe.
 */
public class ModuleFluidExtractor extends LogisticsFluidModule {

    private static final int TICKS_PER_EXTRACTION = 10;
    private static final int FLOW_RATE = 500;
    private static final int ENERGY_PER_EXTRACTION = 5;
    // extractions to skip after nothing in the network took the fluid
    private static final int IDLE_EXTRACTIONS = 4;

    private int idle = 0;

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
        IFluidHandler tank = getTank();
        if (tank == null) {
            return;
        }
        FluidStack available = tank.drain(getTankSide(), FLOW_RATE, false);
        if (available == null || available.amount <= 0) {
            return;
        }
        Pair<Integer, Integer> reply = SimpleServiceLocator.logisticsFluidManager
                .getBestReply(available, host.getRouter(), new ArrayList<>());
        if (reply == null || reply.getValue1() == null || reply.getValue1() == 0 || reply.getValue2() <= 0) {
            idle = IDLE_EXTRACTIONS;
            return;
        }
        if (!_service.useEnergy(ENERGY_PER_EXTRACTION)) {
            return;
        }
        FluidStack drained = drainTank(FluidIdentifier.get(available), Math.min(available.amount, reply.getValue2()));
        if (drained == null || drained.amount <= 0) {
            return;
        }
        sendFluid(drained, reply.getValue1(), TransportMode.Passive, null);
    }

    @Override
    public boolean hasGui() {
        return false;
    }

    @Override
    public LogisticsModularUI getHandGui() {
        return new ModuleFluidInfoMuiDynamic(this, "", "gui.module.ModuleFluidExtractor");
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        return new ModuleFluidInfoMuiDynamic(this, "", "gui.module.ModuleFluidExtractor");
    }

    @Override
    public LogisticsModularUI getPipeGui(String prefix) {
        return new ModuleFluidInfoMuiDynamic(this, prefix, "gui.module.ModuleFluidExtractor");
    }
}
