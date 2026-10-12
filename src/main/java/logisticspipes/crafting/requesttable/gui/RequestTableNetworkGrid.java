package logisticspipes.crafting.requesttable.gui;

import static logisticspipes.crafting.requesttable.gui.RequestTableRender.inside;

import java.util.List;

import net.minecraft.client.gui.Gui;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.crafting.requesttable.network.RequestTableNetworkEntry;
import logisticspipes.crafting.requesttable.settings.RequestTableDisplaySettings;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;
import lombok.Getter;

/**
 * Scrollable icon grid for requestable network items and fluids.
 */
@SideOnly(Side.CLIENT)
public class RequestTableNetworkGrid {

    private final RequestTableNetworkList entries = new RequestTableNetworkList();
    @Getter
    private int scrollRow;

    /**
     * Replaces the complete network list.
     */
    public void setEntries(List<RequestTableNetworkEntry> newEntries) {
        entries.setEntries(newEntries);
    }

    public void setFluidsEnabled(boolean enabled) {
        if (entries.setFluidsEnabled(enabled)) {
            scrollRow = 0;
        }
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
     * Scrolls the grid by whole rows.
     */
    public void scroll(int rows, RequestTableLayout layout) {
        setScrollRow(scrollRow + rows, layout);
    }

    public void setScrollRow(int row, RequestTableLayout layout) {
        scrollRow = Math.max(0, Math.min(getMaxScrollRow(layout), row));
    }

    /**
     * Renders the grid and its hover highlight. NEI handles entry tooltips and shortcuts.
     */
    public void render(LogisticsBaseGuiScreen screen, RequestTableLayout layout, int mouseX, int mouseY) {
        List<RequestTableNetworkEntry> filtered = entries.getVisibleEntries();
        int columns = layout.getNetworkColumns();
        int visibleRows = layout.getVisiblePanelRows();
        int maxScroll = Math.max(0, (filtered.size() + columns - 1) / columns - visibleRows);
        scrollRow = Math.min(scrollRow, maxScroll);

        RequestTableRender.slotGrid(screen.getMC(), layout.panelLeft, layout.panelTop, columns, visibleRows);
        RequestTableGuiStyle.scrollbar(layout, scrollRow, maxScroll);

        int first = scrollRow * columns;
        int visible = visibleRows * columns;
        for (int i = first; i < filtered.size() && i < first + visible; i++) {
            RequestTableNetworkEntry entry = filtered.get(i);
            int localIndex = i - first;
            int x = layout.panelLeft + 1 + (localIndex % columns) * RequestTableLayout.PANEL_CELL;
            int y = layout.panelTop + 1 + (localIndex / columns) * RequestTableLayout.PANEL_CELL;
            boolean hover = inside(
                mouseX,
                mouseY,
                x - 1,
                y - 1,
                RequestTableLayout.PANEL_CELL,
                RequestTableLayout.PANEL_CELL);
            if (hover) {
                Gui.drawRect(x, y, x + 16, y + 16, 0x50ffffff);
            }
            RequestTableRender.item(entry.getDisplayStack(), x, y, 100.0F, false);
            if (!entry.fluid() && entry.getTotalAmount() != 1) {
                RequestTableGuiStyle.drawCount(
                    screen.getMC().fontRenderer,
                    RequestTableGuiStyle.formatCount(entry.getTotalAmount()),
                    x,
                    y,
                    0xffffff,
                    false);
            }
            drawInternalAmount(screen, entry, x, y);
            if (entry.craftable()) {
                RequestTableIcons.craftable(x - 1, y + 1);
            }
        }
    }

    private void drawInternalAmount(LogisticsBaseGuiScreen screen, RequestTableNetworkEntry entry, int x, int y) {
        if (entry.internalAmount() <= 0) {
            return;
        }
        RequestTableGuiStyle.drawCount(
            screen.getMC().fontRenderer,
            RequestTableGuiStyle.formatCount(entry.internalAmount()),
            x,
            y,
            0xffdf80,
            true,
            entry.craftable() ? 11 : 0);
    }

    /**
     * Finds an entry at the given mouse position.
     */
    public RequestTableNetworkEntry getEntryAt(RequestTableLayout layout, int mouseX, int mouseY) {
        if (!inside(
            mouseX,
            mouseY,
            layout.panelLeft,
            layout.panelTop,
            layout.panelWidth,
            layout.getVisiblePanelRows() * RequestTableLayout.PANEL_CELL)) {
            return null;
        }
        List<RequestTableNetworkEntry> filtered = entries.getVisibleEntries();
        int columns = layout.getNetworkColumns();
        int column = (mouseX - layout.panelLeft) / RequestTableLayout.PANEL_CELL;
        int row = (mouseY - layout.panelTop) / RequestTableLayout.PANEL_CELL;
        if (column < 0 || row < 0 || column >= columns || row >= layout.getVisiblePanelRows()) {
            return null;
        }
        int index = (scrollRow + row) * columns + column;
        if (index < 0 || index >= filtered.size()) {
            return null;
        }
        return filtered.get(index);
    }

    public int getMaxScrollRow(RequestTableLayout layout) {
        int columns = layout.getNetworkColumns();
        int rows = (entries.getVisibleEntries().size() + columns - 1) / columns;
        return Math.max(0, rows - layout.getVisiblePanelRows());
    }
}
