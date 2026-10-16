package logisticspipes.utils;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import logisticspipes.items.LogisticsFluidContainer;

/**
 * Fluid in the containers of the game: buckets, cells, capsules. Works with the Forge registry of filled containers and
 * with items that hold fluid themselves, so it needs no mod in particular.
 * <p>
 * The fluid container item of Logistics Pipes is not one of them: it is fluid on its way through the network and has no
 * empty form.
 */
public final class FluidContainerUtil {

    private FluidContainerUtil() {}

    /**
     * @param stack a container, its stack size doesn't matter
     * @return the fluid one such container holds, or null if it is empty or no container at all
     */
    public static FluidStack getFluid(ItemStack stack) {
        if (stack == null || stack.getItem() == null || stack.getItem() instanceof LogisticsFluidContainer) {
            return null;
        }
        if (stack.getItem() instanceof IFluidContainerItem container) {
            FluidStack fluid = container.getFluid(single(stack));
            return fluid == null || fluid.amount <= 0 ? null : fluid.copy();
        }
        FluidStack fluid = FluidContainerRegistry.getFluidForFilledItem(stack);
        return fluid == null || fluid.amount <= 0 ? null : fluid.copy();
    }

    /**
     * @param stack a filled container, its stack size doesn't matter
     * @return one such container with all of its fluid taken out, or null if nothing is left of it
     */
    public static ItemStack getEmptied(ItemStack stack) {
        if (getFluid(stack) == null) {
            return null;
        }
        ItemStack one = single(stack);
        if (one.getItem() instanceof IFluidContainerItem container) {
            container.drain(one, Integer.MAX_VALUE, true);
            return one.stackSize > 0 ? one : null;
        }
        ItemStack empty = FluidContainerRegistry.drainFluidContainer(one);
        return empty == null || empty.getItem() == null || empty.stackSize <= 0 ? null : empty;
    }

    /**
     * @param empty a container that holds nothing
     * @param fluid the fluid to put in, its amount doesn't matter
     * @return how much of the fluid it takes to fill one such container, 0 if it can't hold that fluid
     */
    public static int getCapacity(ItemStack empty, FluidStack fluid) {
        if (empty == null || empty.getItem() == null || fluid == null) {
            return 0;
        }
        if (empty.getItem() instanceof IFluidContainerItem container) {
            ItemStack one = single(empty);
            FluidStack all = fluid.copy();
            all.amount = Integer.MAX_VALUE;
            return Math.max(0, container.fill(one, all, false));
        }
        FluidStack all = fluid.copy();
        all.amount = Integer.MAX_VALUE;
        ItemStack filled = FluidContainerRegistry.fillFluidContainer(all, single(empty));
        FluidStack held = filled == null ? null : FluidContainerRegistry.getFluidForFilledItem(filled);
        return held == null ? 0 : held.amount;
    }

    /**
     * Fills one empty container to the brim.
     *
     * @param empty a container that holds nothing, its stack size doesn't matter
     * @param fluid the fluid to put in, with at least the amount the container takes
     * @return the filled container, or null if the fluid doesn't fit it or isn't enough to fill it
     */
    public static ItemStack fill(ItemStack empty, FluidStack fluid) {
        int capacity = getCapacity(empty, fluid);
        if (capacity <= 0 || fluid.amount < capacity) {
            return null;
        }
        ItemStack one = single(empty);
        if (one.getItem() instanceof IFluidContainerItem container) {
            FluidStack toFill = fluid.copy();
            toFill.amount = capacity;
            return container.fill(one, toFill, true) == capacity ? one : null;
        }
        return FluidContainerRegistry.fillFluidContainer(fluid, one);
    }

    private static ItemStack single(ItemStack stack) {
        ItemStack one = stack.copy();
        one.stackSize = 1;
        return one;
    }
}
