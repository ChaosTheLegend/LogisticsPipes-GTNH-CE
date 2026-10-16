package logisticspipes.crafting.patternStack;

import java.util.ArrayList;
import java.util.List;

import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

public final class PatternStackHelper {

    private PatternStackHelper() {}

    public static List<IPatternStack> aggregate(List<? extends IPatternStack> stacks) {
        List<IPatternStack> result = new ArrayList<>();
        if (stacks == null) {
            return result;
        }
        for (IPatternStack stack : stacks) {
            addAggregated(result, stack);
        }
        return result;
    }

    /**
     * Adds a stack to a list of stacks, merging if possible. If it cannot be merged, adds it to the given list.
     *
     * @param stacks the list of stacks to add to
     * @param stack  the stack to add
     */
    public static void addAggregated(List<IPatternStack> stacks, IPatternStack stack) {
        if (stack == null || stack.getAmount() <= 0) {
            return;
        }
        for (IPatternStack existing : stacks) {
            if (existing.canMerge(stack)) {
                existing.addAmount(stack.getAmount());
                return;
            }
        }
        stacks.add(stack.copy());
    }

    public static IPatternStack copyWithAmount(IPatternStack stack, int amount) {
        if (stack == null || amount <= 0) {
            return null;
        }
        IPatternStack copy = stack.copy();
        copy.addAmount(amount - copy.getAmount());
        return copy;
    }

    public static ItemIdentifierStack asSolidStack(IPatternStack stack) {
        if (stack instanceof PatternItemStack) {
            return ((PatternItemStack) stack).getItemIdentifierStack();
        }
        return null;
    }

    public static FluidIdentifier asFluid(IPatternStack stack) {
        if (stack instanceof PatternFluidStack) {
            return ((PatternFluidStack) stack).getFluid();
        }
        return null;
    }

    public static boolean isSolid(IPatternStack stack) {
        return stack instanceof PatternItemStack;
    }

    public static boolean isFluid(IPatternStack stack) {
        return stack instanceof PatternFluidStack;
    }

    public static boolean containsFluid(List<? extends IPatternStack> stacks) {
        if (stacks == null) {
            return false;
        }
        for (IPatternStack stack : stacks) {
            if (isFluid(stack)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matches(IPatternStack stack, ItemIdentifier item) {
        if (stack == null || item == null) {
            return false;
        }
        ItemIdentifierStack solid = asSolidStack(stack);
        if (solid != null) {
            return solid.getItem().equalsForCrafting(item);
        }
        FluidIdentifier fluid = asFluid(stack);
        if (fluid == null || !item.isFluidContainer()) {
            return false;
        }
        return fluid.equals(FluidIdentifier.get(item));
    }

    public static boolean matches(IPatternStack stack, FluidIdentifier fluid) {
        FluidIdentifier stackFluid = asFluid(stack);
        return stackFluid != null && stackFluid.equals(fluid);
    }

    public static ItemIdentifier getRoutingItem(IPatternStack stack) {
        ItemIdentifierStack solid = asSolidStack(stack);
        if (solid != null) {
            return solid.getItem();
        }
        FluidIdentifier fluid = asFluid(stack);
        return fluid == null ? null : fluid.getItemIdentifier();
    }

    public static ItemIdentifierStack makeDisplayStack(IPatternStack stack) {
        ItemIdentifierStack solid = asSolidStack(stack);
        if (solid != null) {
            return solid.clone();
        }
        if (stack instanceof PatternFluidStack) {
            return ((PatternFluidStack) stack).makeDisplayStack();
        }
        return null;
    }
}
