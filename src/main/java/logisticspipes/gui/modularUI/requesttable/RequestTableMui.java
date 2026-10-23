package logisticspipes.gui.modularUI.requesttable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.scroll.ScrollArea;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widget.sizer.Unit;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.compat.ModularUIHelper;
import logisticspipes.crafting.requesttable.RequestTableView;
import logisticspipes.crafting.requesttable.mk3.RequestTableMk3DisplaySettings;
import logisticspipes.crafting.requesttable.mk3.RequestTableMk3DisplaySettings.FluidUnit;
import logisticspipes.crafting.requesttable.mk3.RequestTableMk3DisplaySettings.SearchMemory;
import logisticspipes.crafting.requesttable.mk3.RequestTableMk3Pipe;
import logisticspipes.crafting.requesttable.storage.RequestTableFluidStorage;
import logisticspipes.gui.modularUI.LogisticsPipeMUI;

/**
 * ModularUI GUI of the request table Mk3.
 * <p>
 * Layout, top to bottom: title and search field, the network list, the player inventory. The network list takes the
 * rows the screen has room for.
 */
public class RequestTableMui extends LogisticsPipeMUI {

    private static final int MARGIN = 8;
    private static final int WIDTH = MARGIN + RequestTableGridWidget.WIDTH + MARGIN;

    private static final int GRID_Y = 18;
    private static final int GRID_BOTTOM_MARGIN = 8;
    private static final int LABEL_HEIGHT = 11;
    private static final int CRAFTING_HEIGHT = 3 * RequestTableGridWidget.CELL + 2;
    private static final int PLAYER_INVENTORY_HEIGHT = 76;
    private static final int BOTTOM_MARGIN = 7;
    // from the bottom of the window to the top of the player inventory label and to the top of the crafting area
    private static final int INVENTORY_LABEL_BOTTOM = BOTTOM_MARGIN + PLAYER_INVENTORY_HEIGHT + 1;
    private static final int CRAFTING_BOTTOM = BOTTOM_MARGIN + PLAYER_INVENTORY_HEIGHT + LABEL_HEIGHT;
    // the part with the crafting grid and the player inventory, it is narrower than the part with the list and its
    // scrollbar
    private static final int LOWER_HEIGHT = LABEL_HEIGHT + CRAFTING_HEIGHT + CRAFTING_BOTTOM;
    private static final int LOWER_WIDTH = MARGIN + RequestTableGridWidget.COLUMNS * RequestTableGridWidget.CELL
            + MARGIN;
    // everything but the rows of the network list
    private static final int FIXED_HEIGHT = GRID_Y + GRID_BOTTOM_MARGIN + LOWER_HEIGHT;

    // the frame of the background texture: outline, two pixels of light or shadow, then the fill
    private static final int FRAME = 3;
    private static final int FRAME_OUTLINE = 0xff000000;
    private static final int FRAME_LIGHT = 0xffffffff;
    private static final int FRAME_SHADOW = 0xff555555;
    private static final int FRAME_FILL = 0xffc6c6c6;

    // NEI puts its search field under the window and hides it when less than this is left there
    private static final int SCREEN_MARGIN = 24;
    private static final int MIN_ROWS = 3;
    private static final int MAX_ROWS = 20;
    private static final int DEFAULT_ROWS = 6;

    private static final int SEND_ALL_WIDTH = 60;
    // far outside of the screen: where a storage tab waits while another view is shown. Its slots stay enabled
    // there, so a shift click in the player inventory still moves items into the table.
    private static final int OFFSCREEN = -10000;
    private static final String STORAGE_SLOT_GROUP = "request_table_storage";
    private static final int ITEM_BAR_COLOR = 0xffd6a04a;
    private static final int FLUID_BAR_COLOR = 0xff468cf0;

    private static final String LANG = "gui.requesttable.";
    private static final ResourceLocation ICONS = new ResourceLocation(
            "logisticspipes",
            "textures/gui/request_table_icons.png");
    private static final int ICON_SIZE = 16;
    private static final int ICON_SHEET_WIDTH = 64;
    private static final int ICON_SHEET_HEIGHT = 224;
    // ticks the search text has to stay unchanged before the table remembers it
    private static final int SAVE_SEARCH_DELAY = 10;
    // the row of the sheet with the icons of the two storages and of the clear button
    private static final int MISC_ICON_ROW = 8;
    private static final int BUTTON_SIZE = 18;
    private static final int SIDE_BUTTON_X = -BUTTON_SIZE - 2;
    // the index of the click mode button in the column of setting buttons
    private static final int BEHAVIOUR_BUTTONS_START = 5;
    // below the two storage buttons and a gap
    private static final int SETTING_BUTTONS_Y = GRID_Y + 2 * (BUTTON_SIZE + 2) + 6;
    private static final int TEXT_COLOR = 0x404040;

    private final RequestTableMk3Pipe table;

    // client: what the upper part of the window shows
    private RequestTableView view = RequestTableView.NETWORK;
    private int itemStorageRows = 1;
    private int fluidStorageRows = 1;

    public RequestTableMui(RequestTableMk3Pipe table) {
        super(table);
        this.table = table;
    }

    @Override
    public String getId() {
        return "request_table_mk3";
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return FIXED_HEIGHT + DEFAULT_ROWS * RequestTableGridWidget.CELL;
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        // the panel is built in getPanel, it needs the sync manager
        return widget;
    }

    @Override
    public ModularPanel getPanel(GuiData guiData, PanelSyncManager syncManager) {
        return getPanel(guiData, syncManager, null);
    }

    @Override
    public ModularPanel getPanel(GuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        RequestTableSyncHandler handler = new RequestTableSyncHandler(table);
        syncManager.syncValue("request_table", handler);
        if (settings != null) {
            // only the client has the NEI hooks, its container class must not be loaded on a dedicated server
            settings.customContainer(
                    syncManager.isClient() ? RequestTableMuiContainer.supplier(handler, table)
                            : RequestTableContainerBase.supplier(handler, table));
        }

        boolean client = syncManager.isClient();
        // the window isn't a rectangle, so the theme must not lay its own panel background under it: that one would
        // show in the cut out corner, also while the mouse is over the window
        ModularPanel panel = new ModularPanel(getId()).width(WIDTH).background(this::drawBackground)
                .disableThemeBackground(true).disableHoverThemeBackground(true);
        if (client) {
            panel.height(() -> FIXED_HEIGHT + shownRows() * RequestTableGridWidget.CELL, Unit.Measure.PIXEL);
        } else {
            panel.height(getHeight());
        }

        // the header: the search field over the network list, the name of the storage and the send button over a
        // storage tab
        RequestTableSearchField search = new RequestTableSearchField();
        search.pos(MARGIN, 4).size(WIDTH - 2 * MARGIN, 12)
                .setMaxLength(RequestTableMk3DisplaySettings.MAX_SEARCH_LENGTH).autoUpdateOnChange(true)
                .hintText(StatCollector.translateToLocal(LANG + "search"))
                .setEnabledIf(w -> view == RequestTableView.NETWORK);
        panel.child(search);
        if (client) {
            followSearch(panel, handler, search);
        }
        panel.child(
                IKey.lang(
                        () -> LANG
                                + (view == RequestTableView.FLUID_STORAGE ? "fluidstorage.title" : "itemstorage.title"))
                        .asWidget().pos(MARGIN, 6).color(TEXT_COLOR)
                        .setEnabledIf(w -> view != RequestTableView.NETWORK));
        panel.child(
                new ButtonWidget<>().pos(LOWER_WIDTH - MARGIN - SEND_ALL_WIDTH, 3).size(SEND_ALL_WIDTH, 13)
                        .overlay(IKey.lang(LANG + "sendall")).setEnabledIf(w -> view != RequestTableView.NETWORK)
                        .onMousePressed(mouseButton -> {
                            if (mouseButton != 0) return false;
                            handler.sendStorage(view == RequestTableView.FLUID_STORAGE);
                            Interactable.playButtonClickSound();
                            return true;
                        }).tooltipBuilder(tooltip -> tooltip.addLine(IKey.lang(LANG + "sendall.tip"))));
        // the storage buttons head the column left of the window, the setting buttons follow below a gap
        IWidget itemStorageButton = viewButton(panel, handler, RequestTableView.ITEM_STORAGE)
                .pos(SIDE_BUTTON_X, GRID_Y);
        IWidget fluidStorageButton = viewButton(panel, handler, RequestTableView.FLUID_STORAGE)
                .pos(SIDE_BUTTON_X, GRID_Y + BUTTON_SIZE + 2);
        panel.child(itemStorageButton);
        panel.child(fluidStorageButton);
        if (settings != null) {
            settings.getRecipeViewerSettings().addExclusionArea(itemStorageButton);
            settings.getRecipeViewerSettings().addExclusionArea(fluidStorageButton);
        }

        RequestTableAmountPopup amountPopup = new RequestTableAmountPopup(panel);
        if (client) {
            RequestTableRecipeChooser recipeChooser = new RequestTableRecipeChooser(panel, handler);
            handler.setRecipeOffer(recipeChooser::offer);
            // NEI hands a recipe over while its own screen is shown, the window for it opens from here
            RequestTableResultPopup resultPopup = new RequestTableResultPopup(panel, handler);
            handler.setUnsentListener(resultPopup::showUnsent);
            RequestTablePreviewPopup previewPopup = new RequestTablePreviewPopup(panel, handler);
            handler.setPreviewListener(previewPopup::show);
            panel.child(new Widget<>().size(0, 0).onUpdateListener(w -> {
                recipeChooser.openPending();
                resultPopup.openPending();
                previewPopup.openPending();
            }));
        }
        RequestTableGridWidget.RequestOpener requestOpener = (entry, fluid, startAmount, craft) -> amountPopup.open(
                StatCollector.translateToLocal(
                        LANG + (craft ? "popup.craft.amount" : "popup.request") + (fluid != null ? ".fluid" : "")),
                fluid != null ? null : entry.getStack().getItem().unsafeMakeNormalStack(1),
                fluid,
                startAmount,
                // a craft has no button for all the network has
                craft ? 0 : entry.getNetworkAmount(),
                craft,
                // stock is requested at once; a craft shows its preview first
                amount -> handler.preview(entry, amount, craft));
        RequestTableGridWidget grid = new RequestTableGridWidget(handler, search::getText, requestOpener)
                .pos(MARGIN, GRID_Y).setEnabledIf(w -> view == RequestTableView.NETWORK);
        if (client) {
            grid.height(() -> visibleRows() * RequestTableGridWidget.CELL, Unit.Measure.PIXEL);
        } else {
            grid.height(DEFAULT_ROWS * RequestTableGridWidget.CELL);
        }
        panel.child(grid);
        addStorageTabs(panel, syncManager, handler);
        addSettingButtons(panel, handler, settings);
        // a loaded table has its recipe but not yet what it makes
        if (table.resultInv.getStackInSlot(0) == null) {
            table.cacheRecipe();
        }
        addCraftingArea(panel, handler, amountPopup);

        panel.child(
                IKey.lang("gui.logisticspipes.inventory.title").asWidget().left(MARGIN).bottom(INVENTORY_LABEL_BOTTOM)
                        .color(TEXT_COLOR));
        panel.child(SlotGroupWidget.playerInventory(false).left(MARGIN).bottom(BOTTOM_MARGIN));
        return panel;
    }

    // region storage tabs

    /**
     * A scrolling grid of slots that takes the place of the network list.
     */
    private static class StorageTab extends ScrollWidget<StorageTab> {

        StorageTab(VerticalScrollData scroll) {
            super(scroll);
        }
    }

    /**
     * Scrolls a storage tab with the scrollbar of the network list instead of the thin one of ModularUI, a row with
     * each step of the wheel.
     */
    private static class StorageScroll extends VerticalScrollData {

        // the handle of the list with the pixel of the track above and below it
        private static final int HANDLE_LENGTH = RequestTableGridWidget.HANDLE_HEIGHT + 2;

        StorageScroll() {
            super(false, RequestTableGridWidget.SCROLLBAR_WIDTH);
            setScrollSpeed(RequestTableGridWidget.CELL);
        }

        @Override
        public int getScrollBarLength(ScrollArea area) {
            return HANDLE_LENGTH;
        }

        @Override
        public boolean scrollTo(ScrollArea area, int x) {
            if (dragging) {
                // the wheel steps by rows already and passes through the positions between them while it animates
                int cell = RequestTableGridWidget.CELL;
                x = Math.round((float) x / cell) * cell;
            }
            return super.scrollTo(area, x);
        }

        @SideOnly(Side.CLIENT)
        @Override
        public void drawScrollbar(ScrollArea area, ModularGuiContext context, WidgetTheme widgetTheme,
                IDrawable texture) {
            int handleTop = 1 + getScrollBarStart(area, HANDLE_LENGTH, false);
            if (dragging) {
                // the handle stays under the mouse, the slots move by whole rows
                int travel = area.h() - HANDLE_LENGTH;
                handleTop = 1 + Math.max(0, Math.min(travel, context.getMouseY() - clickOffset));
            }
            RequestTableGridWidget
                    .drawScrollbar(area.w() - RequestTableGridWidget.SCROLLBAR_WIDTH, area.h(), handleTop, true);
        }

        @SideOnly(Side.CLIENT)
        @Override
        public void drawScrollShadow(ScrollArea area, ModularGuiContext context) {
            // the slots are a grid like the list, a shadow over its last row looks like a cut off one
        }
    }

    /**
     * The item storage for the slots of the window. The storage shrinks when an upgrade is taken out, also while the
     * window is open: a slot that is gone holds nothing and takes nothing until the window is closed.
     */
    private static class StorageHandler extends InvWrapper {

        StorageHandler(IInventory inventory) {
            super(inventory);
        }

        private boolean isGone(int slot) {
            return slot < 0 || slot >= getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return isGone(slot) ? null : super.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return isGone(slot) ? stack : super.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return isGone(slot) ? null : super.extractItem(slot, amount, simulate);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (!isGone(slot)) {
                super.setStackInSlot(slot, stack);
            }
        }
    }

    /**
     * A slot of the item storage: it holds more than a normal stack, but an item that doesn't stack stays alone.
     */
    private static class StorageSlot extends ModularSlot {

        StorageSlot(IItemHandlerModifiable items, int index) {
            super(items, index);
        }

        @Override
        public int getItemStackLimit(ItemStack stack) {
            return stack != null && stack.getMaxStackSize() == 1 ? 1 : super.getItemStackLimit(stack);
        }
    }

    /**
     * Shows the amount in a slot of the item storage as small as the network list does: the slots hold more than a
     * normal stack, a full sized number covers the item.
     */
    private static class StorageItemSlot extends ItemSlot {

        @Override
        protected void drawSlotAmountText(int amount, String format) {
            if (amount <= 1 && format == null) {
                return;
            }
            String text = RequestTableGridWidget.formatAmount(amount);
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            RequestTableGridWidget.drawCornerText(format == null ? text : format + text, 0, 0, false);
            GL11.glPopAttrib();
        }
    }

    /**
     * Shows the amount in a tank of the fluid storage as small as the network list does, in the unit the player chose.
     */
    private static class StorageFluidSlot extends FluidSlot {

        private final RequestTableSyncHandler handler;

        StorageFluidSlot(RequestTableSyncHandler handler) {
            this.handler = handler;
        }

        @Override
        protected boolean displayAmountText() {
            // the amount is drawn below instead
            return false;
        }

        @Override
        public void drawOverlay(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
            super.drawOverlay(context, widgetTheme);
            FluidStack fluid = getFluidStack();
            if (fluid == null || fluid.amount <= 0) {
                return;
            }
            boolean buckets = handler.getSettings().getFluidUnit() == FluidUnit.BUCKETS;
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            RequestTableGridWidget
                    .drawCornerText(RequestTableGridWidget.formatFluidAmount(fluid.amount, buckets), 0, 0, false);
            GL11.glPopAttrib();
        }
    }

    private static int rows(int slots) {
        return Math.max(1, (slots + RequestTableGridWidget.COLUMNS - 1) / RequestTableGridWidget.COLUMNS);
    }

    /**
     * @return the rows the upper part of the window has: all the screen has room for with the network list, only the
     *         rows of the storage with a storage tab, so that one looks like a chest
     */
    private int shownRows() {
        switch (view) {
            case ITEM_STORAGE:
                return Math.min(visibleRows(), itemStorageRows);
            case FLUID_STORAGE:
                return Math.min(visibleRows(), fluidStorageRows);
            default:
                return visibleRows();
        }
    }

    private StorageTab storageTab(PanelSyncManager syncManager, RequestTableView shownIn, int slots) {
        VerticalScrollData scroll = new StorageScroll();
        int rows = rows(slots);
        scroll.setScrollSize(rows * RequestTableGridWidget.CELL);
        StorageTab tab = new StorageTab(scroll).top(GRID_Y).width(RequestTableGridWidget.WIDTH);
        if (syncManager.isClient()) {
            tab.left(() -> view == shownIn ? MARGIN : OFFSCREEN, Unit.Measure.PIXEL);
            tab.height(() -> Math.min(visibleRows(), rows) * RequestTableGridWidget.CELL, Unit.Measure.PIXEL);
        } else {
            tab.left(OFFSCREEN).height(DEFAULT_ROWS * RequestTableGridWidget.CELL);
        }
        return tab;
    }

    /**
     * The item storage of the table as a chest and its fluid storage as a row of tanks.
     */
    private void addStorageTabs(ModularPanel panel, PanelSyncManager syncManager, RequestTableSyncHandler handler) {
        // the slots have to be the same on both sides, like in the legacy GUI the client works out the size as well
        table.updateStorageUpgrades();

        IItemHandlerModifiable items = new StorageHandler(table.inv);
        int itemSlots = table.inv.getSizeInventory();
        syncManager.registerSlotGroup(new SlotGroup(STORAGE_SLOT_GROUP, RequestTableGridWidget.COLUMNS, 100, true));
        itemStorageRows = rows(itemSlots);
        StorageTab itemTab = storageTab(syncManager, RequestTableView.ITEM_STORAGE, itemSlots);
        for (int i = 0; i < itemSlots; i++) {
            ModularSlot slot = new StorageSlot(items, i).slotGroup(STORAGE_SLOT_GROUP).ignoreMaxStackSize(true)
                    .changeListener((stack, onlyAmountChanged, isClient, init) -> {
                        if (!isClient && !init) {
                            // the amounts in the network list changed
                            table.markContentChanged();
                        }
                    });
            itemTab.child(
                    new StorageItemSlot().slot(slot).pos(
                            (i % RequestTableGridWidget.COLUMNS) * RequestTableGridWidget.CELL,
                            (i / RequestTableGridWidget.COLUMNS) * RequestTableGridWidget.CELL));
        }
        panel.child(itemTab);

        RequestTableFluidStorage fluids = table.getFluidStorage();
        int fluidSlots = fluids.getSizeInventory();
        fluidStorageRows = rows(fluidSlots);
        StorageTab fluidTab = storageTab(syncManager, RequestTableView.FLUID_STORAGE, fluidSlots);
        for (int i = 0; i < fluidSlots; i++) {
            fluidTab.child(
                    new StorageFluidSlot(handler).syncHandler(
                            new FluidSlotSyncHandler(new RequestTableSlotTank(fluids, i, table::markContentChanged)))
                            .pos(
                                    (i % RequestTableGridWidget.COLUMNS) * RequestTableGridWidget.CELL,
                                    (i / RequestTableGridWidget.COLUMNS) * RequestTableGridWidget.CELL));
        }
        panel.child(fluidTab);
    }

    /**
     * A button in the header that opens a storage tab, and the network list again with a second click. The bar at its
     * bottom shows how full that storage is.
     */
    private ButtonWidget<?> viewButton(ModularPanel panel, RequestTableSyncHandler handler, RequestTableView target) {
        boolean fluid = target == RequestTableView.FLUID_STORAGE;
        IDrawable icon = (context, x, y, width, height, widgetTheme) -> {
            GuiDraw.drawTexture(
                    ICONS,
                    x + (width - ICON_SIZE) / 2f,
                    y + (height - ICON_SIZE) / 2f - 1,
                    ICON_SIZE,
                    ICON_SIZE,
                    (fluid ? 1 : 0) * ICON_SIZE,
                    MISC_ICON_ROW * ICON_SIZE,
                    ICON_SHEET_WIDTH,
                    ICON_SHEET_HEIGHT);
            float fill = fluid ? table.getFluidStorageFillLevel() : table.getItemStorageFillLevel();
            int barWidth = width - 4;
            GuiDraw.drawRect(x + 2, y + height - 4, barWidth, 2, 0xff202020);
            GuiDraw.drawRect(
                    x + 2,
                    y + height - 4,
                    Math.round(barWidth * fill),
                    2,
                    fluid ? FLUID_BAR_COLOR : ITEM_BAR_COLOR);
            if (view == target) {
                GuiDraw.drawBorderInsideXYWH(x, y, width, height, 1, 0xffffffff);
            }
        };
        return new ButtonWidget<>().size(BUTTON_SIZE).overlay(icon).onMousePressed(mouseButton -> {
            if (mouseButton != 0) return false;
            view = view == target ? RequestTableView.NETWORK : target;
            handler.setShownView(view);
            if (view == RequestTableView.NETWORK) {
                handler.refresh();
            }
            // the tabs move in and out of the window
            panel.scheduleResize();
            Interactable.playButtonClickSound();
            return true;
        }).tooltipAutoUpdate(true).tooltipBuilder(tooltip -> {
            tooltip.addLine(IKey.lang(LANG + (fluid ? "fluidstorage" : "itemstorage")));
            String usage;
            if (fluid) {
                RequestTableFluidStorage fluids = table.getFluidStorage();
                usage = fluids.getStoredAmount() + " / " + fluids.getTotalCapacity() + " mB";
            } else {
                int used = 0;
                for (int i = 0; i < table.inv.getSizeInventory(); i++) {
                    if (table.inv.getStackInSlot(i) != null) {
                        used++;
                    }
                }
                usage = used + " / " + table.inv.getSizeInventory();
            }
            tooltip.addLine(
                    IKey.lang(LANG + (fluid ? "fluidstorage.usage" : "itemstorage.usage"), usage)
                            .style(EnumChatFormatting.GRAY));
        });
    }

    // endregion

    // region crafting area

    /**
     * The crafting grid between the list and the player inventory. It holds items like a workbench and remembers its
     * recipe; a click on the result crafts it from the grid, the storage of the table and the inventory of the player.
     */
    private void addCraftingArea(ModularPanel panel, RequestTableSyncHandler handler,
            RequestTableAmountPopup amountPopup) {
        int cell = RequestTableGridWidget.CELL;
        panel.child(
                IKey.lang(LANG + "crafting").asWidget().left(MARGIN).bottom(CRAFTING_BOTTOM + CRAFTING_HEIGHT + 1)
                        .color(TEXT_COLOR));

        ParentWidget<?> area = new ParentWidget<>().left(MARGIN).bottom(CRAFTING_BOTTOM)
                .size(RequestTableGridWidget.COLUMNS * cell, CRAFTING_HEIGHT);

        IItemHandlerModifiable craftGrid = new InvWrapper(table.craftGrid);
        for (int i = 0; i < 9; i++) {
            int slotIndex = i;
            ModularSlot slot = new ModularSlot(craftGrid, i)
                    .changeListener((stack, onlyAmountChanged, isClient, init) -> {
                        if (!isClient && !init) {
                            table.onCraftGridChanged(slotIndex, onlyAmountChanged);
                        }
                    });
            area.child(
                    new RequestTableCraftSlot(
                            () -> table.matrix.getStackInSlot(slotIndex),
                            () -> handler.isIngredientMissing(slotIndex)).slot(slot)
                                    .pos(cell + (i % 3) * cell, (i / 3) * cell));
        }
        area.child(
                new ButtonWidget<>().pos(4 * cell + 2, 0).size(10).overlay(RequestTableMui::drawClearIcon)
                        .onMousePressed(mouseButton -> {
                            if (mouseButton != 0) return false;
                            handler.clearCraftingGrid();
                            Interactable.playButtonClickSound();
                            return true;
                        }).tooltipBuilder(tooltip -> tooltip.addLine(IKey.lang(LANG + "crafting.clear"))));

        IDrawable arrow = (context, x, y, width, height, widgetTheme) -> {
            int middle = y + height / 2;
            GuiDraw.drawRect(x, middle - 2, width - 6, 4, 0xff8b8b8b);
            for (int i = 0; i < 6; i++) {
                GuiDraw.drawRect(x + width - 6 + i, middle - 6 + i, 1, 12 - 2 * i, 0xff8b8b8b);
            }
        };
        area.child(new Widget<>().pos(4 * cell + 6, cell + 3).size(20, 12).overlay(arrow));

        IItemHandlerModifiable result = new InvWrapper(table.resultInv);
        area.child(
                new RequestTableResultSlot(handler).slot(new ModularSlot(result, 0).accessibility(false, false))
                        .pos(6 * cell - 2, cell));

        // asks for the number of crafts in the same window as a request from the network does
        IDrawable requestIcon = (context, x, y, width, height, widgetTheme) -> GuiDraw.drawTexture(
                ICONS,
                x + (width - ICON_SIZE) / 2f,
                y + (height - ICON_SIZE) / 2f,
                ICON_SIZE,
                ICON_SIZE,
                ICON_SIZE,
                5 * ICON_SIZE,
                ICON_SHEET_WIDTH,
                ICON_SHEET_HEIGHT);
        area.child(
                new ButtonWidget<>().pos(7 * cell + 4, cell).size(BUTTON_SIZE).overlay(requestIcon)
                        .onMousePressed(mouseButton -> {
                            if (mouseButton != 0) return false;
                            Interactable.playButtonClickSound();
                            amountPopup.open(
                                    StatCollector.translateToLocal(LANG + "popup.ingredients"),
                                    table.resultInv.getStackInSlot(0),
                                    null,
                                    1,
                                    handler::requestIngredients);
                            return true;
                        }).tooltipBuilder(tooltip -> {
                            tooltip.addLine(IKey.lang(LANG + "crafting.request"));
                            tooltip.addLine(IKey.lang(LANG + "crafting.request.tip").style(EnumChatFormatting.GRAY));
                        }));
        panel.child(area);
    }

    private static void drawClearIcon(GuiContext context, int x, int y, int width, int height, WidgetTheme theme) {
        GuiDraw.drawTexture(
                ICONS,
                x + (width - ICON_SIZE) / 2f,
                y + (height - ICON_SIZE) / 2f,
                ICON_SIZE,
                ICON_SIZE,
                2 * ICON_SIZE,
                MISC_ICON_ROW * ICON_SIZE,
                ICON_SHEET_WIDTH,
                ICON_SHEET_HEIGHT);
    }

    // endregion

    // region setting buttons

    /**
     * Brings the remembered search text back when the window opens, gives the field the keyboard if the player wants
     * that, and passes what is typed on to NEI and to the memory of the table.
     */
    private void followSearch(ModularPanel panel, RequestTableSyncHandler handler, RequestTableSearchField search) {
        // the text the table and NEI know, null until the settings of the player arrived
        String[] known = new String[2];
        int[] unchangedTicks = new int[1];
        handler.setOnOpened(() -> {
            RequestTableMk3DisplaySettings settings = handler.getSettings();
            if (settings.getSearchMemory() == SearchMemory.REMEMBER && search.getText().isEmpty()) {
                search.replaceText(handler.getSavedSearch());
            }
            known[0] = handler.getSavedSearch();
            known[1] = "";
            if (settings.getSearchMode().autoFocus) {
                search.takeFocus();
            }
        });
        panel.child(new Widget<>().size(0, 0).onUpdateListener(w -> {
            if (known[0] == null) {
                return;
            }
            RequestTableMk3DisplaySettings settings = handler.getSettings();
            String text = search.getText();
            if (settings.getSearchMode().neiSynced) {
                if (!text.equals(known[1])) {
                    known[1] = text;
                    if (Loader.isModLoaded("NotEnoughItems")) {
                        RequestTableNeiSearch.setText(text);
                    }
                }
            } else {
                // with the mode switched on later the text goes to NEI right away
                known[1] = null;
            }
            if (settings.getSearchMemory() != SearchMemory.REMEMBER || text.equals(known[0])) {
                unchangedTicks[0] = 0;
                return;
            }
            // not for every letter: once typing paused for a moment
            if (++unchangedTicks[0] >= SAVE_SEARCH_DELAY) {
                known[0] = text;
                handler.saveSearch(text);
            }
        }));
    }

    /**
     * The column of buttons left of the window. Each one steps through the values of one setting of the player.
     */
    private void addSettingButtons(ModularPanel panel, RequestTableSyncHandler handler, UISettings settings) {
        List<IWidget> buttons = new ArrayList<>();
        buttons.add(
                settingButton(
                        handler,
                        0,
                        "sort",
                        s -> s.getSortMode().ordinal(),
                        s -> s.getSortMode().name(),
                        RequestTableMk3DisplaySettings::nextSortMode));
        buttons.add(
                settingButton(
                        handler,
                        1,
                        "direction",
                        s -> s.getSortDirection().ordinal(),
                        s -> s.getSortDirection().name(),
                        RequestTableMk3DisplaySettings::nextSortDirection));
        buttons.add(
                settingButton(
                        handler,
                        2,
                        "show",
                        s -> s.getFilterMode().ordinal(),
                        s -> s.getFilterMode().name(),
                        RequestTableMk3DisplaySettings::nextFilterMode));
        buttons.add(
                settingButton(
                        handler,
                        3,
                        "type",
                        s -> s.getTypeFilter().ordinal(),
                        s -> s.getTypeFilter().name(),
                        RequestTableMk3DisplaySettings::nextTypeFilter));
        buttons.add(
                settingButton(
                        handler,
                        4,
                        "unit",
                        s -> s.getFluidUnit().ordinal(),
                        s -> s.getFluidUnit().name(),
                        RequestTableMk3DisplaySettings::nextFluidUnit));
        buttons.add(
                settingButton(
                        handler,
                        5,
                        "click",
                        s -> s.getClickMode().ordinal(),
                        s -> s.getClickMode().name(),
                        RequestTableMk3DisplaySettings::nextClickMode));
        buttons.add(
                settingButton(
                        handler,
                        6,
                        "drop",
                        s -> s.getDropMode().ordinal(),
                        s -> s.getDropMode().name(),
                        RequestTableMk3DisplaySettings::nextDropMode));
        buttons.add(
                settingButton(
                        handler,
                        13,
                        "containers",
                        s -> s.getContainerMode().ordinal(),
                        s -> s.getContainerMode().name(),
                        RequestTableMk3DisplaySettings::nextContainerMode));
        buttons.add(
                settingButton(
                        handler,
                        7,
                        "delivery",
                        s -> s.getDeliveryMode().ordinal(),
                        s -> s.getDeliveryMode().name(),
                        RequestTableMk3DisplaySettings::nextDeliveryMode));
        // a second column left of the first one: how the search field and the request messages behave
        List<IWidget> second = new ArrayList<>();
        second.add(
                settingButton(
                        handler,
                        9,
                        "searchmode",
                        s -> s.getSearchMode().ordinal(),
                        s -> s.getSearchMode().name(),
                        RequestTableMk3DisplaySettings::nextSearchMode));
        second.add(
                settingButton(
                        handler,
                        10,
                        "searchmemory",
                        s -> s.getSearchMemory().ordinal(),
                        s -> s.getSearchMemory().name(),
                        RequestTableMk3DisplaySettings::nextSearchMemory));
        second.add(
                settingButton(
                        handler,
                        11,
                        "popupmessages",
                        s -> s.getPopupMessages().ordinal(),
                        s -> s.getPopupMessages().name(),
                        RequestTableMk3DisplaySettings::nextPopupMessages));
        second.add(
                settingButton(
                        handler,
                        12,
                        "chatmessages",
                        s -> s.getChatMessages().ordinal(),
                        s -> s.getChatMessages().name(),
                        RequestTableMk3DisplaySettings::nextChatMessages));
        for (int i = 0; i < second.size(); i++) {
            IWidget button = second.get(i);
            ((ButtonWidget<?>) button).pos(SIDE_BUTTON_X - BUTTON_SIZE - 2, SETTING_BUTTONS_Y + i * (BUTTON_SIZE + 2));
            panel.child(button);
            if (settings != null) {
                settings.getRecipeViewerSettings().addExclusionArea(button);
            }
        }
        for (int i = 0; i < buttons.size(); i++) {
            IWidget button = buttons.get(i);
            // the buttons for what is shown, a gap, then the buttons for how the table behaves
            int gap = i >= BEHAVIOUR_BUTTONS_START ? 6 : 0;
            ((ButtonWidget<?>) button).pos(SIDE_BUTTON_X, SETTING_BUTTONS_Y + i * (BUTTON_SIZE + 2) + gap);
            panel.child(button);
            if (settings != null) {
                // keeps the bookmarks of NEI off the buttons
                settings.getRecipeViewerSettings().addExclusionArea(button);
            }
        }
    }

    private IWidget settingButton(RequestTableSyncHandler handler, int iconRow, String name,
            ToIntFunction<RequestTableMk3DisplaySettings> state,
            Function<RequestTableMk3DisplaySettings, String> stateName,
            UnaryOperator<RequestTableMk3DisplaySettings> next) {
        IDrawable icon = (context, x, y, width, height, widgetTheme) -> GuiDraw.drawTexture(
                ICONS,
                x + (width - ICON_SIZE) / 2f,
                y + (height - ICON_SIZE) / 2f,
                ICON_SIZE,
                ICON_SIZE,
                state.applyAsInt(handler.getSettings()) * ICON_SIZE,
                iconRow * ICON_SIZE,
                ICON_SHEET_WIDTH,
                ICON_SHEET_HEIGHT);
        return new ButtonWidget<>().size(BUTTON_SIZE).overlay(icon).onMousePressed(mouseButton -> {
            if (mouseButton != 0) return false;
            handler.setSettings(next.apply(handler.getSettings()));
            Interactable.playButtonClickSound();
            return true;
        }).tooltipAutoUpdate(true).tooltipBuilder(tooltip -> {
            tooltip.addLine(IKey.lang(LANG + name));
            tooltip.addLine(
                    IKey.lang(LANG + name + "." + stateName.apply(handler.getSettings()).toLowerCase(Locale.ROOT))
                            .style(EnumChatFormatting.GRAY));
        });
    }

    // endregion

    /**
     * @return true while the upper part needs the room for a scrollbar: with the network list, and with a storage tab
     *         that has more rows than fit
     */
    private boolean hasScrollbarColumn() {
        switch (view) {
            case ITEM_STORAGE:
                return itemStorageRows > visibleRows();
            case FLUID_STORAGE:
                return fluidStorageRows > visibleRows();
            default:
                return true;
        }
    }

    /**
     * Draws the window as two joined parts: the wide one with the list and its scrollbar, and below it the narrower one
     * with the player inventory. A storage tab without a scrollbar is as narrow as the lower part, the window is a
     * plain rectangle then.
     */
    private void drawBackground(GuiContext context, int x, int y, int width, int height, WidgetTheme theme) {
        if (!hasScrollbarColumn()) {
            ModularUIHelper.BACKGROUND_TEXTURE.draw(context, x, y, LOWER_WIDTH, height, theme);
            return;
        }
        int upperHeight = height - LOWER_HEIGHT;
        int seam = y + upperHeight - FRAME;
        ModularUIHelper.BACKGROUND_TEXTURE.draw(context, x, y, width, upperHeight, theme);
        ModularUIHelper.BACKGROUND_TEXTURE.draw(context, x, seam, LOWER_WIDTH, height - upperHeight + FRAME, theme);
        // the frames of the two parts cross at the seam: open it, and close the left edge and the inner corner
        GuiDraw.drawRect(x + FRAME, seam, LOWER_WIDTH - 2 * FRAME, FRAME, FRAME_FILL);
        GuiDraw.drawRect(x, seam, 1, FRAME, FRAME_OUTLINE);
        GuiDraw.drawRect(x + 1, seam, FRAME - 1, FRAME, FRAME_LIGHT);
        GuiDraw.drawRect(x + LOWER_WIDTH - FRAME, seam, FRAME, FRAME, FRAME_SHADOW);
        GuiDraw.drawRect(x + LOWER_WIDTH - 1, seam + FRAME - 1, 1, 1, FRAME_OUTLINE);
    }

    /**
     * @return the rows of the network list the screen has room for, client only
     */
    private static int visibleRows() {
        int rows = (ClientScreen.height() - 2 * SCREEN_MARGIN - FIXED_HEIGHT) / RequestTableGridWidget.CELL;
        return Math.max(MIN_ROWS, Math.min(MAX_ROWS, rows));
    }

    /**
     * Kept apart so the client classes are only loaded on the client.
     */
    private static final class ClientScreen {

        static int height() {
            Minecraft mc = Minecraft.getMinecraft();
            return new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaledHeight();
        }
    }
}
