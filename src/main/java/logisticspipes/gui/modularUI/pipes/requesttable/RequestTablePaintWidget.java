package logisticspipes.gui.modularUI.pipes.requesttable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;

/** Non-interactive widget drawn by a painter at its origin, with optional dynamic tooltip lines. */
public class RequestTablePaintWidget extends Widget<RequestTablePaintWidget> {

    /** Draws the widget at its origin. */
    @FunctionalInterface
    public interface Painter {

        void paint(int width, int height);
    }

    private final Painter painter;

    public RequestTablePaintWidget(Painter painter) {
        this.painter = painter;
    }

    /** Rebuilds the tooltip every frame from the given lines; no lines hide the tooltip. */
    public RequestTablePaintWidget lines(Consumer<List<String>> lines) {
        return tooltipAutoUpdate(true).tooltipBuilder(tooltip -> {
            List<String> text = new ArrayList<>();
            lines.accept(text);
            tooltip.addStringLines(text);
        });
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {}

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        painter.paint(getArea().width, getArea().height);
    }

    @Override
    public void drawOverlay(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {}

    @Override
    public boolean canHover() {
        return hasTooltip();
    }
}
