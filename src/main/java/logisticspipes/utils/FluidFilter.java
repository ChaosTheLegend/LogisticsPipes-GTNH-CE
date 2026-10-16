package logisticspipes.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

/**
 * The fluids a fluid sink is set to: a row of phantom slots, each holds the kind of a fluid and no amount.
 */
public class FluidFilter {

    public static final int SLOTS = 9;

    private final FluidTank[] slots = new FluidTank[SLOTS];

    public FluidFilter() {
        for (int i = 0; i < SLOTS; i++) {
            slots[i] = new FluidTank(1);
        }
    }

    /**
     * @return the phantom tank of that slot, for the slot of a GUI
     */
    public FluidTank getTank(int slot) {
        return slots[slot];
    }

    public boolean contains(FluidStack stack) {
        for (FluidTank slot : slots) {
            if (slot.getFluid() != null && slot.getFluid().isFluidEqual(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the fluids that are set, in the order of their slots
     */
    public List<FluidIdentifier> getFluids() {
        List<FluidIdentifier> fluids = new ArrayList<>();
        for (FluidTank slot : slots) {
            if (slot.getFluid() != null) {
                fluids.add(FluidIdentifier.get(slot.getFluid()));
            }
        }
        return fluids;
    }

    /**
     * Sets the filter to these fluids. What doesn't fit into the slots is left out.
     */
    public void setFluids(Collection<FluidIdentifier> fluids) {
        for (FluidTank slot : slots) {
            slot.setFluid(null);
        }
        int slot = 0;
        for (FluidIdentifier fluid : fluids) {
            if (slot >= SLOTS) {
                break;
            }
            slots[slot++].setFluid(fluid.makeFluidStack(1));
        }
    }

    public void readFromNBT(NBTTagCompound nbt) {
        for (int i = 0; i < SLOTS; i++) {
            slots[i].setFluid(null);
            if (nbt.hasKey("filter" + i)) {
                slots[i].readFromNBT(nbt.getCompoundTag("filter" + i));
            }
        }
    }

    public void writeToNBT(NBTTagCompound nbt) {
        for (int i = 0; i < SLOTS; i++) {
            if (slots[i].getFluid() != null) {
                nbt.setTag("filter" + i, slots[i].writeToNBT(new NBTTagCompound()));
            }
        }
    }
}
