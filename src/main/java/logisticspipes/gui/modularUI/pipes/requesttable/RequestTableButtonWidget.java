package logisticspipes.gui.modularUI.pipes.requesttable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;

/**
 * Button drawn with the request table's own bevels and icons instead of the ModularUI theme.
 * <p>
 * Visibility uses the widget's enabled state; {@link #active(BooleanSupplier)} greys a visible button out.
 */
public class RequestTableButtonWidget extends Widget<RequestTableButtonWidget> implements Interactable {

    /** Draws the button at the widget origin. */
    @FunctionalInterface
    public interface Painter {

        void paint(int width, int height, boolean hover, boolean active);
    }

    private BooleanSupplier active = () -> true;
    private IntPredicate action = mouseButton -> false;
    private Painter painter = (width, height, hover, active) -> {};

    public RequestTableButtonWidget active(BooleanSupplier active) {
        this.active = active;
        return this;
    }

    /** Runs the action for a mouse button; returning {@code true} plays the click sound. */
    public RequestTableButtonWidget onClick(IntPredicate action) {
        this.action = action;
        return this;
    }

    /** Runs the action for a left click. */
    public RequestTableButtonWidget onLeftClick(Runnable action) {
        this.action = mouseButton -> {
            if (mouseButton != 0) return false;
            action.run();
            return true;
        };
        return this;
    }

    public RequestTableButtonWidget painter(Painter painter) {
        this.painter = painter;
        return this;
    }

    /** Rebuilds the tooltip every frame from the given lines; no lines hide the tooltip. */
    public RequestTableButtonWidget lines(Consumer<List<String>> lines) {
        return tooltipAutoUpdate(true).tooltipBuilder(tooltip -> {
            List<String> text = new ArrayList<>();
            lines.accept(text);
            tooltip.addStringLines(text);
        });
    }

    /** Shows the item tooltip of the given stack, if any. */
    public RequestTableButtonWidget itemTooltip(java.util.function.Supplier<net.minecraft.item.ItemStack> stack) {
        return tooltipAutoUpdate(true).tooltipBuilder(tooltip -> {
            net.minecraft.item.ItemStack shown = stack.get();
            if (shown != null) tooltip.addFromItem(shown);
        });
    }

    public boolean isActive() {
        return active.getAsBoolean();
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        if (!isActive()) return Result.STOP;
        if (action.test(mouseButton)) {
            Interactable.playButtonClickSound();
            return Result.SUCCESS;
        }
        return Result.STOP;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {}

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        painter.paint(getArea().width, getArea().height, isHovering(), isActive());
    }

    @Override
    public void drawOverlay(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {}
}
