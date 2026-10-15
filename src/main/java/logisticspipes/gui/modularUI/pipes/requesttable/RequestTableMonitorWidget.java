package logisticspipes.gui.modularUI.pipes.requesttable;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;

import logisticspipes.crafting.requesttable.gui.RequestTableLayout;
import logisticspipes.crafting.requesttable.gui.monitor.RequestTableMonitorModel;
import logisticspipes.crafting.requesttable.gui.monitor.RequestTableMonitorPanel;

/**
 * Crafting monitor view: request tabs, the combined resource grid and the draggable dependency tree.
 * <p>
 * Covers the whole panel so the shared monitor renderer can keep using the terminal layout's coordinates. The
 * subscription itself uses the monitor packets, which accept this GUI's container.
 */
public class RequestTableMonitorWidget extends Widget<RequestTableMonitorWidget> implements Interactable {

    private final RequestTableMui gui;
    private RequestTableMonitorModel model;
    private RequestTableMonitorPanel panel;

    public RequestTableMonitorWidget(RequestTableMui gui) {
        this.gui = gui;
        tooltipAutoUpdate(true).tooltipBuilder(tooltip -> {
            List<String> lines = panel == null ? null : panel.getTooltip();
            if (lines != null) tooltip.addStringLines(lines);
        });
    }

    private RequestTableMonitorPanel panel() {
        if (panel == null) {
            model = new RequestTableMonitorModel(gui.getTable());
            panel = new RequestTableMonitorPanel(model);
        }
        return panel;
    }

    /** Subscribes to the server's crafting jobs. */
    public void enter(int windowId) {
        panel();
        model.enter(windowId);
    }

    /** Ends the subscription. */
    public void leave() {
        if (panel == null) return;
        model.leave();
        panel.release();
    }

    public boolean isTree() {
        return panel != null && panel.isTree();
    }

    public void setTree(boolean tree) {
        panel().setTree(tree);
    }

    /** Receives a snapshot chunk of the current subscription. */
    public void chunk(long session, long transfer, int part, int parts, byte[] payload) {
        if (model != null) model.chunk(session, transfer, part, parts, payload);
    }

    private int mouseX() {
        return getContext().getMouseX();
    }

    private int mouseY() {
        return getContext().getMouseY();
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        return panel().click(mouseX(), mouseY(), mouseButton) ? Result.SUCCESS : Result.IGNORE;
    }

    @Override
    public void onMouseDrag(int mouseButton, long timeSinceClick) {
        if (mouseButton == 0) panel().move(mouseX(), mouseY());
    }

    @Override
    public boolean onMouseRelease(int mouseButton) {
        if (mouseButton == 0) panel().release();
        return false;
    }

    @Override
    public boolean onMouseScroll(UpOrDown scrollDirection, int amount) {
        panel().scroll(scrollDirection == UpOrDown.UP ? 1 : -1, mouseX(), mouseY());
        return true;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {}

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        RequestTableLayout layout = gui.getLayout();
        panel().render(layout, context.getMouseX(), context.getMouseY());
    }

    @Override
    public void drawOverlay(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {}
}
