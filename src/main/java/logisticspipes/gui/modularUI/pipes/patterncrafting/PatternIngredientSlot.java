package logisticspipes.gui.modularUI.pipes.patterncrafting;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;

import cpw.mods.fml.common.Loader;
import logisticspipes.crafting.patternStack.PatternFluidStack;
import logisticspipes.items.LogisticsFluidContainer;

/**
 * Phantom slot showing one entry of the edited pattern.
 * <p>
 * Fluid entries show their amount in the bottom right corner, and the crafting progress (buffered ingredients or
 * requested outputs) is drawn in blue in the top right corner.
 */
public class PatternIngredientSlot extends PhantomItemSlot {

    private static final int FLUID_AMOUNT_COLOR = 0xffffff;
    private static final int PROGRESS_COLOR = 0x55aaff;

    private Supplier<String> progressText = () -> null;
    private Consumer<RichTooltip> extraTooltip = tooltip -> {};
    private Runnable satelliteClick = () -> {};
    private Runnable mainOutputClick;
    private BooleanSupplier mainOutput = () -> false;
    private Runnable badgeDraw = () -> {};

    public PatternIngredientSlot badgeDraw(Runnable draw) {
        badgeDraw = draw;
        return this;
    }

    public PatternIngredientSlot satelliteClick(Runnable action) {
        satelliteClick = action;
        return this;
    }

    public PatternIngredientSlot mainOutput(Runnable action, BooleanSupplier selected) {
        mainOutputClick = action;
        mainOutput = selected;
        return this;
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        if (mouseButton == 0 && Interactable.hasControlDown()) {
            satelliteClick.run();
            return Result.SUCCESS;
        }
        if (mouseButton == 0 && mainOutputClick != null && Interactable.hasAltDown()) {
            mainOutputClick.run();
            return Result.SUCCESS;
        }
        return super.onMousePressed(mouseButton);
    }

    /** Convert only the display; the synchronized slot keeps its LP fluid representation. */
    private static ItemStack displayStack(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof LogisticsFluidContainer)) return stack;
        PatternFluidStack fluid = PatternFluidStack.fromItemStack(stack);
        if (fluid == null) return stack;
        return fluid.makeDisplayItemStack();
    }

    @Override
    protected ItemStack getItemStackForRendering(ItemStack stack, boolean dragging) {
        return displayStack(stack);
    }

    @Override
    public ItemStack getStackForRecipeViewer() {
        return displayStack(getSlot().getStack());
    }

    @Override
    protected void drawSlotAmountText(int amount, String format) {
        ItemStack stack = getSlot().getStack();
        if (stack == null || !(stack.getItem() instanceof LogisticsFluidContainer)) {
            super.drawSlotAmountText(amount, format);
        }
    }

    public PatternIngredientSlot progressText(Supplier<String> progressText) {
        this.progressText = progressText;
        return this;
    }

    public PatternIngredientSlot extraTooltip(Consumer<RichTooltip> extraTooltip) {
        this.extraTooltip = extraTooltip;
        return this;
    }

    @Override
    protected void drawOverlay() {
        super.drawOverlay();
        if (mainOutput.getAsBoolean()) {
            GuiDraw.drawBorderInsideXYWH(0, 0, 18, 18, 1, 0xff55ff55);
        }
        badgeDraw.run();
        ItemStack stack = getSlot().getStack();
        // NEI's GregTech fluid renderer already draws the amount.
        if (!Loader.isModLoaded("gregtech") && stack != null && stack.getItem() instanceof LogisticsFluidContainer) {
            PatternFluidStack fluid = PatternFluidStack.fromItemStack(stack);
            if (fluid != null) {
                PatternGuiDraw
                        .drawCornerText(PatternGuiDraw.formatFluidAmount(fluid.getAmount()), FLUID_AMOUNT_COLOR, false);
            }
        }
        PatternGuiDraw.drawCornerText(progressText.get(), PROGRESS_COLOR, true);
    }

    @Override
    public void buildTooltip(ItemStack stack, RichTooltip tooltip) {
        super.buildTooltip(displayStack(stack), tooltip);
        if (stack != null) {
            extraTooltip.accept(tooltip);
        }
    }
}
