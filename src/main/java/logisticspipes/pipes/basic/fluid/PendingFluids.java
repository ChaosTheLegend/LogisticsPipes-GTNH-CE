package logisticspipes.pipes.basic.fluid;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import logisticspipes.utils.FluidIdentifier;

/**
 * Fluid that arrived at a pipe but didn't fit into the tank yet. It waits here for room instead of in a tank of the
 * pipe.
 */
public class PendingFluids implements Iterable<PendingFluids.Entry> {

    public static class Entry {

        public final FluidStack fluid;
        /** the side of the pipe the tank is on, UNKNOWN if any tank will do */
        public ForgeDirection side;

        private Entry(FluidStack fluid, ForgeDirection side) {
            this.fluid = fluid;
            this.side = side;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public void add(FluidStack fluid, ForgeDirection side) {
        if (fluid == null || fluid.amount <= 0) {
            return;
        }
        if (side == null) {
            side = ForgeDirection.UNKNOWN;
        }
        for (Entry entry : entries) {
            if (entry.side == side && entry.fluid.isFluidEqual(fluid)
                    && entry.fluid.amount <= Integer.MAX_VALUE - fluid.amount) {
                entry.fluid.amount += fluid.amount;
                return;
            }
        }
        entries.add(new Entry(fluid.copy(), side));
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public void clear() {
        entries.clear();
    }

    /**
     * The entries, emptied ones can be removed through the iterator.
     */
    @Override
    public Iterator<Entry> iterator() {
        return entries.iterator();
    }

    public long getAmount(FluidIdentifier fluid) {
        long amount = 0;
        for (Entry entry : entries) {
            if (fluid.equals(FluidIdentifier.get(entry.fluid))) {
                amount += entry.fluid.amount;
            }
        }
        return amount;
    }

    public long getTotalAmount() {
        long amount = 0;
        for (Entry entry : entries) {
            amount += entry.fluid.amount;
        }
        return amount;
    }

    public void readFromNBT(NBTTagCompound nbt, String key) {
        entries.clear();
        NBTTagList list = nbt.getTagList(key, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(tag);
            if (fluid != null && fluid.amount > 0) {
                // an absent side reads as 0, so the side is stored shifted by one
                int side = tag.getByte("pendingSide") - 1;
                entries.add(new Entry(fluid, side < 0 ? ForgeDirection.UNKNOWN : ForgeDirection.getOrientation(side)));
            }
        }
    }

    public void writeToNBT(NBTTagCompound nbt, String key) {
        NBTTagList list = new NBTTagList();
        for (Entry entry : entries) {
            NBTTagCompound tag = entry.fluid.writeToNBT(new NBTTagCompound());
            if (entry.side != ForgeDirection.UNKNOWN) {
                tag.setByte("pendingSide", (byte) (entry.side.ordinal() + 1));
            }
            list.appendTag(tag);
        }
        nbt.setTag(key, list);
    }
}
