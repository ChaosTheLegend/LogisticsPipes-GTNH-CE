package logisticspipes.gui.modularUI.pipes.requesttable;

import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;

/** Draggable scrollbar of the network and storage grids, which scroll by whole rows. */
public class RequestTableScrollbarWidget extends Widget<RequestTableScrollbarWidget> implements Interactable {

    /** A list scrolled by whole rows. */
    public interface Scrollable {

        int getScrollRow();

        int getMaxScrollRow();

        int getVisibleRows();

        void setScrollRow(int row);
    }

    private final Supplier<Scrollable> target;
    private boolean dragging;
    private int dragOffset;

    public RequestTableScrollbarWidget(Supplier<Scrollable> target) {
        this.target = target;
    }

    private int thumbHeight(Scrollable scrollable) {
        int rows = scrollable.getVisibleRows();
        return Math.max(12, getArea().height * rows / Math.max(1, rows + scrollable.getMaxScrollRow()));
    }

    private int thumbTop(Scrollable scrollable) {
        int maxScroll = scrollable.getMaxScrollRow();
        int travel = getArea().height - thumbHeight(scrollable);
        return maxScroll == 0 ? 0 : travel * Math.min(scrollable.getScrollRow(), maxScroll) / maxScroll;
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        Scrollable scrollable = target.get();
        if (mouseButton != 0 || scrollable == null) return Result.STOP;
        if (scrollable.getMaxScrollRow() == 0) return Result.STOP;
        int mouseY = getContext().getMouseY();
        int top = thumbTop(scrollable);
        int height = thumbHeight(scrollable);
        boolean onThumb = mouseY >= top && mouseY < top + height;
        dragOffset = onThumb ? mouseY - top : height / 2;
        dragging = true;
        if (!onThumb) drag(scrollable, mouseY);
        return Result.SUCCESS;
    }

    @Override
    public void onMouseDrag(int mouseButton, long timeSinceClick) {
        Scrollable scrollable = target.get();
        if (dragging && mouseButton == 0 && scrollable != null) drag(scrollable, getContext().getMouseY());
    }

    @Override
    public boolean onMouseRelease(int mouseButton) {
        if (mouseButton == 0) dragging = false;
        return true;
    }

    private void drag(Scrollable scrollable, int mouseY) {
        int maxScroll = scrollable.getMaxScrollRow();
        int travel = getArea().height - thumbHeight(scrollable);
        int position = Math.max(0, Math.min(travel, mouseY - dragOffset));
        scrollable.setScrollRow(travel <= 0 ? 0 : Math.round((float) position * maxScroll / travel));
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {}

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        Scrollable scrollable = target.get();
        if (scrollable == null) return;
        RequestTableMuiDraw.scrollbar(getArea().height, thumbTop(scrollable), thumbHeight(scrollable));
    }

    @Override
    public void drawOverlay(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {}
}
