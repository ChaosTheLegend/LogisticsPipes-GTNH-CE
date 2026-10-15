package logisticspipes.gui.modularUI.pipes.requesttable;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerIngredientProvider;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;

import logisticspipes.crafting.requesttable.gui.RequestTableGuiStyle;
import logisticspipes.crafting.requesttable.gui.RequestTableIcons;
import logisticspipes.crafting.requesttable.gui.RequestTableLayout;
import logisticspipes.crafting.requesttable.gui.RequestTableNetworkList;
import logisticspipes.crafting.requesttable.gui.RequestTableRender;
import logisticspipes.crafting.requesttable.network.RequestTableNetworkEntry;
import logisticspipes.crafting.requesttable.storage.RequestTableFluidDisplay;

/**
 * Scrollable icon grid of requestable network items and fluids.
 * <p>
 * Clicks either open the request popup, move the held stack into the table, or take stock out of the table, exactly
 * like the legacy terminal. Hovered entries are exposed to NEI for its recipe, usage and bookmark keys.
 */
public class RequestTableNetworkGridWidget extends Widget<RequestTableNetworkGridWidget>
        implements Interactable, RecipeViewerIngredientProvider, RequestTableScrollbarWidget.Scrollable {

    private static final int CELL = RequestTableLayout.PANEL_CELL;
    private static final int COLUMNS = RequestTableLayout.INVENTORY_COLUMNS;

    private final RequestTableMui gui;
    private final RequestTableNetworkList entries = new RequestTableNetworkList();
    private final Consumer<RequestTableNetworkEntry> openRequest;
    private int appliedRevision = -1;
    private int scrollRow;
    // NEI queries the hovered stack outside of draw and input passes, where the mouse is not widget-local.
    private int hoverX = -1;
    private int hoverY = -1;

    public RequestTableNetworkGridWidget(RequestTableMui gui, Consumer<RequestTableNetworkEntry> openRequest) {
        this.gui = gui;
        this.openRequest = openRequest;
        tooltipAutoUpdate(true).tooltipBuilder(tooltip -> {
            RequestTableNetworkEntry entry = getHoveredEntry();
            if (entry == null) return;
            tooltip.addFromItem(entry.getDisplayStack());
            String unit = entry.fluid() ? " mB" : "";
            tooltip.addLine(EnumChatFormatting.GRAY + "Network: " + entry.networkAmount() + unit);
            tooltip.addLine(EnumChatFormatting.GRAY + "Internal: " + entry.internalAmount() + unit);
            if (entry.craftable()) tooltip.addLine(EnumChatFormatting.GRAY + "Craftable");
            if (Interactable.hasShiftDown()) {
                tooltip.addLine(EnumChatFormatting.GRAY + "Total: " + entry.getTotalAmount() + unit);
            }
        });
    }

    /** Brings the cached list up to date with the received entries, settings, search and fluid unlock. */
    private List<RequestTableNetworkEntry> visibleEntries() {
        RequestTableSyncHandler actions = gui.getActions();
        if (appliedRevision != actions.getContentRevision()) {
            appliedRevision = actions.getContentRevision();
            entries.setEntries(actions.getEntries());
        }
        boolean reset = entries.setFluidsEnabled(gui.getTable().isFluidEnabled());
        reset |= entries.setDisplaySettings(actions.getDisplaySettings());
        reset |= entries.setSearch(gui.getSearchText());
        if (reset) scrollRow = 0;
        return entries.getVisibleEntries();
    }

    @Override
    public int getVisibleRows() {
        return Math.max(1, getArea().height / CELL);
    }

    @Override
    public int getMaxScrollRow() {
        int rows = (visibleEntries().size() + COLUMNS - 1) / COLUMNS;
        return Math.max(0, rows - getVisibleRows());
    }

    @Override
    public int getScrollRow() {
        return Math.min(scrollRow, getMaxScrollRow());
    }

    @Override
    public void setScrollRow(int row) {
        scrollRow = Math.max(0, Math.min(getMaxScrollRow(), row));
    }

    /** Finds the entry under a widget-local position. */
    public @Nullable RequestTableNetworkEntry getEntryAt(int mouseX, int mouseY) {
        if (mouseX < 0 || mouseY < 0 || mouseX >= COLUMNS * CELL || mouseY >= getVisibleRows() * CELL) return null;
        int index = (getScrollRow() + mouseY / CELL) * COLUMNS + mouseX / CELL;
        List<RequestTableNetworkEntry> visible = visibleEntries();
        return index >= 0 && index < visible.size() ? visible.get(index) : null;
    }

    public @Nullable RequestTableNetworkEntry getHoveredEntry() {
        if (!isHovering()) return null;
        return getEntryAt(hoverX, hoverY);
    }

    @Override
    public @Nullable ItemStack getStackForRecipeViewer() {
        RequestTableNetworkEntry entry = getHoveredEntry();
        return entry == null ? null : entry.getDisplayStack();
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        RequestTableNetworkEntry entry = getEntryAt(getContext().getMouseX(), getContext().getMouseY());
        boolean openOverlay = mouseButton == 2 || (Interactable.hasControlDown() && mouseButton <= 1);
        if (mouseButton <= 1 && !openOverlay && Minecraft.getMinecraft().thePlayer.inventory.getItemStack() != null) {
            gui.getActions().clickNetworkEntry(entry, mouseButton, Interactable.hasShiftDown(), true);
            return Result.SUCCESS;
        }
        if (entry == null) return Result.STOP;
        if (openOverlay || (mouseButton <= 1 && entry.internalAmount() <= 0)) {
            openRequest.accept(entry);
        } else if (mouseButton <= 1) {
            gui.getActions().clickNetworkEntry(entry, mouseButton, Interactable.hasShiftDown(), false);
        }
        return Result.SUCCESS;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {}

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        List<RequestTableNetworkEntry> visible = visibleEntries();
        int rows = getVisibleRows();
        Minecraft mc = Minecraft.getMinecraft();
        try (var ignored = RequestTableRender.guiState()) {
            RequestTableRender.slotGrid(mc, 0, 0, COLUMNS, rows);
        }
        int mouseX = context.getMouseX();
        int mouseY = context.getMouseY();
        hoverX = mouseX;
        hoverY = mouseY;
        int first = getScrollRow() * COLUMNS;
        for (int i = first; i < visible.size() && i < first + rows * COLUMNS; i++) {
            RequestTableNetworkEntry entry = visible.get(i);
            int local = i - first;
            int x = 1 + (local % COLUMNS) * CELL;
            int y = 1 + (local / COLUMNS) * CELL;
            if (isHovering() && RequestTableRender.inside(mouseX, mouseY, x - 1, y - 1, CELL, CELL)) {
                try (var ignored = RequestTableRender.guiState()) {
                    Gui.drawRect(x, y, x + 16, y + 16, 0x50ffffff);
                }
            }
            RequestTableRender.item(entry.getDisplayStack(), x, y, 100.0F, false);
            if (!entry.fluid() && entry.getTotalAmount() != 1) {
                RequestTableGuiStyle.drawCount(
                        mc.fontRenderer,
                        RequestTableGuiStyle.formatCount(entry.getTotalAmount()),
                        x,
                        y,
                        0xffffff,
                        false);
            }
            if (entry.internalAmount() > 0) {
                RequestTableGuiStyle.drawCount(
                        mc.fontRenderer,
                        entry.fluid() ? RequestTableFluidDisplay.formatAmount(entry.internalAmount())
                                : RequestTableGuiStyle.formatCount(entry.internalAmount()),
                        x + 1,
                        y + 2,
                        0xffdf80,
                        true);
            }
            if (entry.craftable()) {
                try (var ignored = RequestTableRender.guiState()) {
                    RequestTableIcons.craftable(x - 1, y + 1);
                }
            }
        }
    }

    @Override
    public void drawOverlay(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {}
}
