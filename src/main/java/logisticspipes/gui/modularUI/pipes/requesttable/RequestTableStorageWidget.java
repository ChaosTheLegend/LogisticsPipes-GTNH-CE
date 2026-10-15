package logisticspipes.gui.modularUI.pipes.requesttable;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerIngredientProvider;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;

import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.crafting.requesttable.RequestTableView;
import logisticspipes.crafting.requesttable.gui.RequestTableLayout;
import logisticspipes.crafting.requesttable.gui.RequestTableRender;
import logisticspipes.crafting.requesttable.storage.RequestTableFluidDisplay;

/**
 * Virtual slots of the table's item or fluid storage.
 * <p>
 * Item slots may hold more than a normal stack, which vanilla slot packets cannot carry, so the slots are drawn here
 * and every click is applied by the server. Clicks follow vanilla slot behaviour: pick up, place, swap, shift-click,
 * hotbar keys, dropping, creative middle-click, drag distribution and double-click collection.
 */
public class RequestTableStorageWidget extends Widget<RequestTableStorageWidget>
        implements Interactable, RecipeViewerIngredientProvider, RequestTableScrollbarWidget.Scrollable {

    private static final int SLOT = RequestTableLayout.SLOT;
    private static final int COLUMNS = RequestTableLayout.INVENTORY_COLUMNS;
    private static final long DOUBLE_CLICK_MS = 250;

    private final RequestTableMui gui;
    private int scrollRow;
    private RequestTableView shownView;
    private final List<Integer> dragSlots = new ArrayList<>();
    private int dragButton = -1;
    private long lastClickTime;
    private int lastClickSlot = -1;
    // NEI queries the hovered stack outside of draw and input passes, where the mouse is not widget-local.
    private int hoverX = -1;
    private int hoverY = -1;

    public RequestTableStorageWidget(RequestTableMui gui) {
        this.gui = gui;
        tooltipAutoUpdate(true).tooltipBuilder(tooltip -> {
            ItemStack stack = getStackForRecipeViewer();
            if (stack != null) tooltip.addFromItem(stack);
        });
    }

    private boolean isFluid() {
        return gui.getView() == RequestTableView.FLUID_STORAGE;
    }

    private int size() {
        RequestTablePipe table = gui.getTable();
        return isFluid() ? table.getFluidStorage().getSizeInventory() : table.inv.getSizeInventory();
    }

    private ItemStack stackIn(int slot) {
        RequestTablePipe table = gui.getTable();
        if (isFluid()) {
            FluidStack fluid = table.getFluidStorage().getFluid(slot);
            return fluid == null ? null : RequestTableFluidDisplay.of(fluid, fluid.amount);
        }
        return table.inv.getStackInSlot(slot);
    }

    @Override
    public int getVisibleRows() {
        return Math.max(1, getArea().height / SLOT);
    }

    @Override
    public int getMaxScrollRow() {
        int rows = (size() + COLUMNS - 1) / COLUMNS;
        return Math.max(0, rows - getVisibleRows());
    }

    @Override
    public int getScrollRow() {
        if (shownView != gui.getView()) {
            // Every storage view starts at its first row.
            shownView = gui.getView();
            scrollRow = 0;
        }
        return Math.min(scrollRow, getMaxScrollRow());
    }

    @Override
    public void setScrollRow(int row) {
        getScrollRow();
        scrollRow = Math.max(0, Math.min(getMaxScrollRow(), row));
    }

    /** @return the storage slot under a widget-local position, or -1 */
    private int slotAt(int mouseX, int mouseY) {
        if (mouseX < 0 || mouseY < 0 || mouseX >= COLUMNS * SLOT || mouseY >= getVisibleRows() * SLOT) return -1;
        int slot = (getScrollRow() + mouseY / SLOT) * COLUMNS + mouseX / SLOT;
        return slot < size() ? slot : -1;
    }

    private int hoveredSlot() {
        return isHovering() ? slotAt(hoverX, hoverY) : -1;
    }

    @Override
    public @Nullable ItemStack getStackForRecipeViewer() {
        int slot = hoveredSlot();
        return slot < 0 ? null : stackIn(slot);
    }

    private boolean ready() {
        return gui.getActions().isInventoryReady();
    }

    private static ItemStack cursor() {
        return Minecraft.getMinecraft().thePlayer.inventory.getItemStack();
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        int slot = slotAt(getContext().getMouseX(), getContext().getMouseY());
        if (slot < 0 || !ready()) return Result.STOP;
        RequestTableSyncHandler actions = gui.getActions();
        if (isFluid()) {
            if (mouseButton == 0 || mouseButton == 1) actions.clickFluidStorage(slot, mouseButton);
            return Result.SUCCESS;
        }
        if (mouseButton == 2) {
            actions.clickItemStorage(slot, 2, RequestTableSyncHandler.MODE_CLONE);
            return Result.SUCCESS;
        }
        if (mouseButton != 0 && mouseButton != 1) return Result.STOP;
        if (Interactable.hasShiftDown()) {
            actions.clickItemStorage(slot, mouseButton, RequestTableSyncHandler.MODE_QUICK_MOVE);
            return Result.SUCCESS;
        }
        long now = Minecraft.getSystemTime();
        boolean doubleClick = mouseButton == 0 && slot == lastClickSlot && now - lastClickTime < DOUBLE_CLICK_MS;
        lastClickTime = now;
        lastClickSlot = slot;
        if (doubleClick) {
            // The server applies clicks in order, so the first click's pickup is already on its cursor.
            actions.clickItemStorage(slot, 0, RequestTableSyncHandler.MODE_PICKUP_ALL);
            lastClickSlot = -1;
            return Result.SUCCESS;
        }
        if (cursor() != null) {
            // Placing waits for the release, so a drag across several slots can distribute the stack.
            dragButton = mouseButton;
            dragSlots.clear();
            dragSlots.add(slot);
            return Result.SUCCESS;
        }
        actions.clickItemStorage(slot, mouseButton, RequestTableSyncHandler.MODE_PICKUP);
        return Result.SUCCESS;
    }

    @Override
    public void onMouseDrag(int mouseButton, long timeSinceClick) {
        if (dragButton < 0 || mouseButton != dragButton) return;
        int slot = slotAt(getContext().getMouseX(), getContext().getMouseY());
        if (slot >= 0 && !dragSlots.contains(slot)) dragSlots.add(slot);
    }

    @Override
    public boolean onMouseRelease(int mouseButton) {
        if (dragButton < 0 || mouseButton != dragButton) return false;
        int button = dragButton;
        dragButton = -1;
        if (dragSlots.size() > 1) {
            gui.getActions().dragIntoItemStorage(button, new ArrayList<>(dragSlots));
        } else if (!dragSlots.isEmpty()) {
            gui.getActions().clickItemStorage(dragSlots.get(0), button, RequestTableSyncHandler.MODE_PICKUP);
        }
        dragSlots.clear();
        return true;
    }

    @Override
    public @NotNull Result onKeyPressed(char typedChar, int keyCode) {
        int slot = hoveredSlot();
        if (slot < 0 || isFluid() || !ready()) return Result.IGNORE;
        Minecraft mc = Minecraft.getMinecraft();
        KeyBinding[] hotbar = mc.gameSettings.keyBindsHotbar;
        for (int i = 0; i < hotbar.length && i < 9; i++) {
            if (hotbar[i].getKeyCode() == keyCode) {
                gui.getActions().clickItemStorage(slot, i, RequestTableSyncHandler.MODE_SWAP);
                return Result.SUCCESS;
            }
        }
        if (mc.gameSettings.keyBindDrop.getKeyCode() == keyCode && cursor() == null) {
            gui.getActions()
                    .clickItemStorage(slot, Interactable.hasControlDown() ? 1 : 0, RequestTableSyncHandler.MODE_THROW);
            return Result.SUCCESS;
        }
        return Result.IGNORE;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {}

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        hoverX = context.getMouseX();
        hoverY = context.getMouseY();
        int rows = getVisibleRows();
        int first = getScrollRow() * COLUMNS;
        int size = size();
        try (var ignored = RequestTableRender.guiState()) {
            RequestTableRender.slotGrid(Minecraft.getMinecraft(), 0, 0, COLUMNS, rows, size - first);
        }
        int hovered = hoveredSlot();
        boolean fluid = isFluid();
        for (int slot = first; slot < size && slot < first + rows * COLUMNS; slot++) {
            int local = slot - first;
            int x = 1 + (local % COLUMNS) * SLOT;
            int y = 1 + (local / COLUMNS) * SLOT;
            RequestTableMuiDraw.slotStack(stackIn(slot), x, y, !fluid);
            if (slot == hovered || (dragButton >= 0 && dragSlots.size() > 1 && dragSlots.contains(slot))) {
                RequestTableMuiDraw.slotHighlight(x, y);
            }
        }
    }

    @Override
    public void drawOverlay(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {}
}
