package logisticspipes.gui.modularUI.pipes.patterncrafting;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.utils.MouseData;
import com.cleanroommc.modularui.value.sync.PhantomItemSlotSH;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import logisticspipes.crafting.patternStack.PatternFluidStack;
import logisticspipes.items.LogisticsFluidContainer;

/**
 * Phantom slot handler for pattern entries that adjusts fluid entries in millibuckets.
 * <p>
 * Fluid entries are shown as a single fluid container item, so the default stack-size logic would multiply the whole
 * amount on every step. Clicking and scrolling a fluid entry instead changes the amount by
 * {@link #fluidStep(MouseData)}.
 */
public class PatternIngredientSlotSH extends PhantomItemSlotSH {

    public static final int FLUID_STEP = 1000;

    public PatternIngredientSlotSH(ModularSlot slot) {
        super(slot);
    }

    @Override
    protected void phantomClick(MouseData mouseData, ItemStack cursorStack) {
        PatternFluidStack fluid = getFluidEntry();
        if (fluid == null || cursorStack != null || (mouseData.mouseButton == 0 && mouseData.shift)) {
            super.phantomClick(mouseData, cursorStack);
            return;
        }
        if (mouseData.mouseButton == 0) {
            adjustFluid(fluid, -fluidStep(mouseData));
        } else if (mouseData.mouseButton == 1) {
            adjustFluid(fluid, fluidStep(mouseData));
        }
    }

    @Override
    protected void phantomScroll(MouseData mouseData) {
        PatternFluidStack fluid = getFluidEntry();
        if (fluid == null) {
            super.phantomScroll(mouseData);
            return;
        }
        adjustFluid(fluid, Integer.signum(mouseData.mouseButton) * fluidStep(mouseData));
    }

    /**
     * 1000 mB per step, 100 mB with shift, 10 mB with ctrl and 1 mB with both.
     */
    public static int fluidStep(MouseData mouseData) {
        int step = FLUID_STEP;
        if (mouseData.shift) {
            step /= 10;
        }
        if (mouseData.ctrl) {
            step /= 100;
        }
        return Math.max(1, step);
    }

    private PatternFluidStack getFluidEntry() {
        ItemStack stack = getSlot().getStack();
        if (stack == null || !(stack.getItem() instanceof LogisticsFluidContainer)) {
            return null;
        }
        return PatternFluidStack.fromItemStack(stack);
    }

    private void adjustFluid(PatternFluidStack fluid, int delta) {
        long amount = (long) fluid.getAmount() + delta;
        if (amount <= 0) {
            getSlot().putStack(null);
            return;
        }
        getSlot().putStack(
                new PatternFluidStack(fluid.getFluid(), (int) Math.min(Integer.MAX_VALUE, amount)).makePatternStack());
    }
}
