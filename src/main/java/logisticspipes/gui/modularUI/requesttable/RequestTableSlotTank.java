package logisticspipes.gui.modularUI.requesttable;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;

import logisticspipes.crafting.requesttable.storage.RequestTableFluidStorage;

/**
 * One slot of the fluid storage of the request table as a tank, so a fluid slot widget can show and change it.
 */
public class RequestTableSlotTank implements IFluidTank {

    private final RequestTableFluidStorage storage;
    private final int slot;
    private final Runnable onChange;

    /**
     * @param onChange called after the content of the slot was changed through this tank
     */
    public RequestTableSlotTank(RequestTableFluidStorage storage, int slot, Runnable onChange) {
        this.storage = storage;
        this.slot = slot;
        this.onChange = onChange;
    }

    @Override
    public FluidStack getFluid() {
        return storage.getFluid(slot);
    }

    @Override
    public int getFluidAmount() {
        FluidStack fluid = storage.getFluid(slot);
        return fluid == null ? 0 : fluid.amount;
    }

    @Override
    public int getCapacity() {
        return storage.getSlotCapacity();
    }

    @Override
    public FluidTankInfo getInfo() {
        return new FluidTankInfo(getFluid(), getCapacity());
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        int filled = storage.fillSlot(slot, resource, doFill);
        if (doFill && filled > 0) {
            onChange.run();
        }
        return filled;
    }

    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        FluidStack drained = storage.drain(slot, maxDrain, doDrain);
        if (doDrain && drained != null && drained.amount > 0) {
            onChange.run();
        }
        return drained;
    }
}
