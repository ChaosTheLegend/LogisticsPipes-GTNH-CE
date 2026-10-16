package logisticspipes.modules;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.dynamicModules.ModuleFluidTankSupplierMuiDynamic;
import logisticspipes.interfaces.IFluidModuleHost;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.IRequest;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.interfaces.routing.IRequireReliableTransport;
import logisticspipes.modules.abstractmodules.LogisticsFluidModule;
import logisticspipes.pipes.PipeLogisticsChassi.ChassiTargetInformation;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.RequestTree;
import logisticspipes.routing.IRouter;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * Keeps a set amount of one fluid in the tank by requesting it from the network, like the fluid supplier pipe Mk2.
 */
public class ModuleFluidTankSupplier extends LogisticsFluidModule implements IRequestFluid, IRequireReliableTransport {

    private static final int TICKS_PER_REQUEST = 100;
    private static final int ENERGY_PER_REQUEST = 11;

    public final FluidTank phantomTank = new FluidTank(1);
    public int amount = 0;
    public boolean requestPartials = false;
    public int refillThreshold = 0;

    // requested and not arrived yet
    private final Map<FluidIdentifier, Integer> requested = new HashMap<>();
    private boolean lastRequestFailed = false;

    public boolean isRequestFailed() {
        return lastRequestFailed;
    }

    @Override
    public void tick() {
        IFluidModuleHost host = getHost();
        if (host == null || !_service.isNthTick(TICKS_PER_REQUEST)) {
            return;
        }
        if (phantomTank.getFluid() == null || getTankTile() == null) {
            return;
        }
        FluidIdentifier fluid = FluidIdentifier.get(phantomTank.getFluid());
        int missing = getMissingAmount(fluid) - requested.getOrDefault(fluid, 0);
        if (missing <= 0 || (refillThreshold != 0 && missing < refillThreshold)) {
            return;
        }
        if (!_service.useEnergy(ENERGY_PER_REQUEST)) {
            return;
        }
        // the slot tells the chassis which module the delivery is for
        IAdditionalTargetInformation info = new ChassiTargetInformation(getPositionInt());
        int ordered;
        if (requestPartials) {
            ordered = RequestTree.requestFluidPartial(fluid, missing, this, null, info);
        } else {
            ordered = RequestTree.requestFluid(fluid, missing, this, null, info) ? missing : 0;
        }
        lastRequestFailed = ordered <= 0;
        if (ordered > 0) {
            requested.merge(fluid, ordered, Integer::sum);
        }
    }

    private int getMissingAmount(FluidIdentifier fluid) {
        return amount - getStoredAmount(fluid);
    }

    /**
     * Only fills up to the configured amount, the rest goes back into the network.
     */
    @Override
    public int acceptFluid(FluidStack stack) {
        if (phantomTank.getFluid() == null || !phantomTank.getFluid().isFluidEqual(stack)) {
            return 0;
        }
        return fillTank(stack, getMissingAmount(FluidIdentifier.get(stack)));
    }

    /**
     * @return true if this module is set to that fluid and the tank is below the configured amount
     */
    public boolean wants(FluidStack stack) {
        return phantomTank.getFluid() != null && phantomTank.getFluid().isFluidEqual(stack)
                && getMissingAmount(FluidIdentifier.get(stack)) > 0;
    }

    private void decreaseRequested(FluidIdentifier fluid, int count) {
        Integer open = requested.get(fluid);
        if (open == null) {
            return;
        }
        if (open <= count) {
            requested.remove(fluid);
        } else {
            requested.put(fluid, open - count);
        }
    }

    private void decreaseRequested(ItemIdentifierStack container) {
        FluidStack liquid = SimpleServiceLocator.logisticsFluidManager.getFluidFromContainer(container);
        if (liquid != null) {
            decreaseRequested(FluidIdentifier.get(liquid), liquid.amount);
        }
    }

    @Override
    public void itemArrived(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        decreaseRequested(item);
    }

    @Override
    public void itemLost(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        decreaseRequested(item);
    }

    @Override
    public void sendFailed(FluidIdentifier fluid, Integer count) {
        decreaseRequested(fluid, count);
    }

    @Override
    public IRouter getRouter() {
        IFluidModuleHost host = getHost();
        return host == null ? null : host.getRouter();
    }

    @Override
    public int getID() {
        return getRouter().getSimpleID();
    }

    @Override
    public int compareTo(IRequest other) {
        return getID() - other.getID();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        phantomTank.setFluid(null);
        phantomTank.readFromNBT(nbttagcompound.getCompoundTag("tank"));
        amount = nbttagcompound.getInteger("amount");
        requestPartials = nbttagcompound.getBoolean("requestpartials");
        refillThreshold = nbttagcompound.getInteger("refillThreshold");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        nbttagcompound.setTag("tank", phantomTank.writeToNBT(new NBTTagCompound()));
        nbttagcompound.setInteger("amount", amount);
        nbttagcompound.setBoolean("requestpartials", requestPartials);
        nbttagcompound.setInteger("refillThreshold", refillThreshold);
    }

    @Override
    public LogisticsModularUI getHandGui() {
        return new ModuleFluidTankSupplierMuiDynamic(this);
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        return new ModuleFluidTankSupplierMuiDynamic(this);
    }

    @Override
    public LogisticsModularUI getPipeGui(String prefix) {
        return new ModuleFluidTankSupplierMuiDynamic(this, prefix);
    }
}
