package logisticspipes.crafting.requesttable;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import logisticspipes.utils.Color;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;
import logisticspipes.utils.item.ItemStackRenderer;
import logisticspipes.utils.item.ItemStackRenderer.DisplayAmount;
import logisticspipes.utils.string.StringUtils;

/**
 * Scrollable icon grid for requestable network items and fluids.
 */
public class RequestTableNetworkGrid {

    private final RequestTableNetworkList entries = new RequestTableNetworkList();
    private int scrollRow;
    private Object[] tooltip;

    /**
     * Replaces the complete network list.
     */
    public void setEntries(List<RequestTableNetworkEntry> newEntries) {
        entries.setEntries(newEntries);
    }

    /**
     * Applies a new sort/filter state and invalidates the cached visible list once.
     */
    public void setDisplaySettings(RequestTableDisplaySettings settings) {
        if (entries.setDisplaySettings(settings)) {
            scrollRow = 0;
        }
    }

    /**
     * Updates the cached search result if the text actually changed.
     */
    public void setSearch(String search) {
        if (entries.setSearch(search)) {
            scrollRow = 0;
        }
    }

    /**
     * @return tooltip data for the hovered entry
     */
    public Object[] getTooltip() {
        return tooltip;
    }

    /**
     * Scrolls the grid by whole rows.
     */
    public void scroll(int rows, RequestTableLayout layout) {
        scrollRow = Math.max(0, Math.min(getMaxScrollRow(layout), scrollRow + rows));
    }

    /**
     * Renders the grid and updates the hover tooltip.
     */
    public void render(LogisticsBaseGuiScreen screen, RequestTableLayout layout, int mouseX, int mouseY) {
        List<RequestTableNetworkEntry> filtered = entries.getVisibleEntries();
        int columns = layout.getNetworkColumns();
        int visibleRows = layout.getVisiblePanelRows();
        int maxScroll = Math.max(0, (filtered.size() + columns - 1) / columns - visibleRows);
        scrollRow = Math.min(scrollRow, maxScroll);

        screen.drawRect(
                layout.panelLeft,
                layout.panelTop,
                layout.panelLeft + layout.panelWidth,
                layout.panelTop + layout.panelHeight,
                Color.GREY);
        drawScrollbar(screen, layout, maxScroll);

        tooltip = null;
        int first = scrollRow * columns;
        int visible = visibleRows * columns;
        for (int i = first; i < filtered.size() && i < first + visible; i++) {
            RequestTableNetworkEntry entry = filtered.get(i);
            int localIndex = i - first;
            int x = layout.panelLeft + 2 + (localIndex % columns) * RequestTableLayout.PANEL_CELL;
            int y = layout.panelTop + 2 + (localIndex / columns) * RequestTableLayout.PANEL_CELL;
            boolean hover = mouseX >= x && mouseX < x + RequestTableLayout.PANEL_CELL
                    && mouseY >= y
                    && mouseY < y + RequestTableLayout.PANEL_CELL;
            if (hover) {
                screen.drawRect(x - 1, y - 1, x + 19, y + 19, Color.BLACK);
                screen.drawRect(x, y, x + 18, y + 18, Color.DARKER_GREY);
                ItemStack tooltipStack = entry.getStack().unsafeMakeNormalStack();
                tooltipStack.stackSize = entry.getTotalAmount();
                List<String> details = new ArrayList<>();
                String unit = entry.isFluid() ? " mB" : "";
                details.add("\u00a77Network: " + entry.getNetworkAmount() + unit);
                details.add("\u00a77Internal: " + entry.getInternalAmount() + unit);
                if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
                    details.add("\u00a77Total: " + entry.getTotalAmount() + unit);
                }
                tooltip = new Object[] { mouseX, mouseY, tooltipStack, true, details };
            }
            if (entry.isFluid()) {
                screen.drawRect(x + 14, y, x + 18, y + 4, Color.BLUE);
            }
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            new ItemStackRenderer(x + 1, y + 1, 100.0F, true, false, true).setItemIdentifierStack(entry.getStack())
                    .setDisplayAmount(DisplayAmount.HIDE_ONE).renderInGui();
            drawInternalAmount(screen, entry, x, y);
        }
    }

    private void drawInternalAmount(LogisticsBaseGuiScreen screen, RequestTableNetworkEntry entry, int x, int y) {
        if (entry.getInternalAmount() <= 0) {
            return;
        }
        String amount = StringUtils.getFormatedStackSize(entry.getInternalAmount(), true);
        float scale = 0.5F;
        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, 1.0F);
        int drawX = Math.round((x + 18) / scale) - screen.getMC().fontRenderer.getStringWidth(amount);
        int drawY = Math.round((y - 1) / scale);
        screen.getMC().fontRenderer.drawStringWithShadow(amount, drawX, drawY, 0x66e0ff);
        GL11.glPopMatrix();
    }

    /**
     * Finds an entry at the given mouse position.
     */
    public RequestTableNetworkEntry getEntryAt(RequestTableLayout layout, int mouseX, int mouseY) {
        if (mouseX < layout.panelLeft || mouseX >= layout.panelLeft + layout.panelWidth
                || mouseY < layout.panelTop
                || mouseY >= layout.panelTop + layout.panelHeight) {
            return null;
        }
        List<RequestTableNetworkEntry> filtered = entries.getVisibleEntries();
        int columns = layout.getNetworkColumns();
        int column = (mouseX - layout.panelLeft - 2) / RequestTableLayout.PANEL_CELL;
        int row = (mouseY - layout.panelTop - 2) / RequestTableLayout.PANEL_CELL;
        if (column < 0 || row < 0 || column >= columns || row >= layout.getVisiblePanelRows()) {
            return null;
        }
        int index = (scrollRow + row) * columns + column;
        if (index < 0 || index >= filtered.size()) {
            return null;
        }
        return filtered.get(index);
    }

    private void drawScrollbar(LogisticsBaseGuiScreen screen, RequestTableLayout layout, int maxScroll) {
        screen.drawRect(
                layout.scrollbarX,
                layout.panelTop + 1,
                layout.scrollbarX + 5,
                layout.panelTop + layout.panelHeight - 1,
                Color.DARKER_GREY);
        int barHeight = Math.max(10, layout.panelHeight / Math.max(1, maxScroll + 1));
        int travel = Math.max(1, layout.panelHeight - 2 - barHeight);
        int barTop = layout.panelTop + 1 + (maxScroll == 0 ? 0 : travel * scrollRow / maxScroll);
        screen.drawRect(layout.scrollbarX + 1, barTop, layout.scrollbarX + 4, barTop + barHeight, Color.LIGHTER_GREY);
    }

    private int getMaxScrollRow(RequestTableLayout layout) {
        int columns = layout.getNetworkColumns();
        int rows = (entries.getVisibleEntries().size() + columns - 1) / columns;
        return Math.max(0, rows - layout.getVisiblePanelRows());
    }
}
