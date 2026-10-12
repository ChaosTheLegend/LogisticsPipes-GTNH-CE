package logisticspipes.crafting.requesttable.gui;

import static logisticspipes.crafting.requesttable.gui.RequestTableRender.inside;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import codechicken.nei.LayoutManager;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.crafting.requesttable.RequestTableContainer;
import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.crafting.requesttable.RequestTableView;
import logisticspipes.crafting.requesttable.gui.RequestTableIconButton.Icon;
import logisticspipes.crafting.requesttable.gui.monitor.RequestTableMonitorModel;
import logisticspipes.crafting.requesttable.gui.monitor.RequestTableMonitorPanel;
import logisticspipes.crafting.requesttable.network.RequestTableNetworkEntry;
import logisticspipes.crafting.requesttable.settings.RequestTableDisplaySettings;
import logisticspipes.crafting.requesttable.storage.RequestTableFluidSlot;
import logisticspipes.crafting.requesttable.upgrade.RequestTableStorageUpgradeConfig;
import logisticspipes.gui.popup.GuiRequestPopup;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.crafting.requesttable.RequestTableClearCraftingPacket;
import logisticspipes.network.packets.crafting.requesttable.RequestTableDisplaySettingsPacket;
import logisticspipes.network.packets.crafting.requesttable.RequestTableNetworkInteractPacket;
import logisticspipes.network.packets.crafting.requesttable.RequestTableOpenUpgradesPacket;
import logisticspipes.network.packets.crafting.requesttable.RequestTableRefreshPacket;
import logisticspipes.network.packets.crafting.requesttable.RequestTableRequestIngredientsPacket;
import logisticspipes.network.packets.crafting.requesttable.RequestTableSendStoragePacket;
import logisticspipes.network.packets.crafting.requesttable.RequestTableShiftClickPacket;
import logisticspipes.network.packets.crafting.requesttable.RequestTableSubmitPacket;
import logisticspipes.proxy.MainProxy;
import logisticspipes.request.resources.IResource;
import logisticspipes.utils.gui.GuiGraphics;
import logisticspipes.utils.gui.GuiSearchBar;
import logisticspipes.utils.gui.ISubGuiControler;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.string.StringUtils;
import lombok.Getter;

/**
 * Redesigned request table GUI with a combined item/fluid network list, internal storage views and fake crafting grid.
 */
@SideOnly(Side.CLIENT)
public class RequestTableGui extends LogisticsBaseGuiScreen {

    private static final int SEND_BUTTON = 0;
    private static final int REQUEST_INGREDIENTS_BUTTON = 1;
    private static final int SORT_MODE_BUTTON = 2;
    private static final int SORT_DIRECTION_BUTTON = 3;
    private static final int FILTER_MODE_BUTTON = 4;
    private static final int ITEM_VIEW_BUTTON = 5;
    private static final int FLUID_VIEW_BUTTON = 6;
    private static final int NETWORK_VIEW_BUTTON = 7;
    private static final int CLEAR_CRAFTING_BUTTON = 8;
    private static final int REQUEST_MESSAGES_BUTTON = 9;
    private static final int SEARCH_MODE_BUTTON = 10;
    private static final int SAVE_SEARCH_BUTTON = 11;
    private static final int TERMINAL_STYLE_BUTTON = 12;
    private static final int SHOW_ITEMS_BUTTON = 13;
    private static final int SHOW_FLUIDS_BUTTON = 14;
    private static final int CRAFTING_MONITOR_BUTTON = 15;
    private static final int ITEM_UPGRADES_BUTTON = 16;
    private static final int MONITOR_GRID_BUTTON = 17;
    private static final int MONITOR_TREE_BUTTON = 18;
    private static final int ALL_INGREDIENTS_BUTTON = 19;

    /**
     * -- GETTER --
     *
     * @return backing pipe for integration points such as NEI recipe overlays
     */
    @Getter
    private final RequestTablePipe table;
    private final EntityPlayer player;
    private final RequestTableContainer container;
    private final RequestTableNetworkGrid networkGrid = new RequestTableNetworkGrid();
    private final Map<ItemIdentifier, Integer> networkStock = new HashMap<>();
    private final RequestTableRequestOverlay requestOverlay = new RequestTableRequestOverlay();
    private final RequestTableItemRenderer storageItemRenderer = new RequestTableItemRenderer();
    private final RequestTableMonitorModel monitorModel;
    private final RequestTableMonitorPanel monitorPanel;
    private final int dimension;
    private RequestTableLayout layout;
    private GuiSearchBar search;
    private RequestTableNumberField ingredientAmountField;
    private RequestTableIconButton sendButton;
    private RequestTableIconButton requestIngredientsButton;
    private RequestTableButton allIngredientsButton;
    private RequestTableIconButton sortModeButton;
    private RequestTableIconButton sortDirectionButton;
    private RequestTableIconButton filterModeButton;
    private RequestTableIconButton requestMessagesButton;
    private RequestTableIconButton searchModeButton;
    private RequestTableIconButton saveSearchButton;
    private RequestTableIconButton terminalStyleButton;
    private RequestTableIconButton showItemsButton;
    private RequestTableIconButton showFluidsButton;
    private RequestTableIconButton itemUpgradesButton;
    private RequestTableIconButton networkButton;
    private RequestTableIconButton itemButton;
    private RequestTableIconButton fluidButton;
    private RequestTableIconButton clearCraftingButton;
    private RequestTableMonitorButton monitorButton;
    private RequestTableIconButton monitorGridButton;
    private RequestTableIconButton monitorTreeButton;
    private RequestTableView view = RequestTableView.NETWORK;
    private RequestTableView monitorReturnView = RequestTableView.NETWORK;
    private RequestTableDisplaySettings displaySettings = RequestTableDisplaySettings.DEFAULT;
    private int storageScrollRow;
    private boolean scrollbarDragging;
    private int scrollbarDragOffset;
    private boolean settingsReceived;
    private boolean searchInitialized;
    private boolean searchEdited;
    private boolean searchFocusChosen;
    private boolean restoringSearch;
    private List<Integer> playerShiftClickBatch;

    /**
     * Creates the client GUI for the new request table.
     */
    public RequestTableGui(EntityPlayer player, RequestTablePipe table) {
        super(new RequestTableContainer(player, table), RequestTableLayout.GUI_WIDTH, 300, 0, 0);
        this.player = player;
        this.table = table;
        monitorModel = new RequestTableMonitorModel(table);
        monitorPanel = new RequestTableMonitorPanel(monitorModel);
        this.container = (RequestTableContainer) inventorySlots;
        dimension = MainProxy.getDimensionForWorld(table.getWorld());
        refreshNetwork();
    }

    @Override
    public void initGui() {
        xSize = view == RequestTableView.CRAFTING_MONITOR
                ? Math.min(416, Math.max(RequestTableLayout.GUI_WIDTH, width - 16))
                : RequestTableLayout.GUI_WIDTH;
        ySize = RequestTableLayout.getGuiHeight(height, displaySettings.getTerminalStyle());
        super.initGui();
        updateLayout();
        buttonList.clear();
        sendButton = new RequestTableIconButton(
                SEND_BUTTON,
                layout.sendButtonX,
                layout.sendButtonY,
                layout.sendButtonWidth,
                layout.sendButtonHeight,
                Icon.SEND);
        buttonList.add(sendButton);
        requestIngredientsButton = new RequestTableIconButton(
                REQUEST_INGREDIENTS_BUTTON,
                layout.craftingRequestX,
                layout.craftingRequestY,
                layout.craftingRequestWidth,
                layout.craftingRequestHeight,
                Icon.REQUEST);
        buttonList.add(requestIngredientsButton);
        allIngredientsButton = new RequestTableButton(
                ALL_INGREDIENTS_BUTTON,
                layout.craftingAllX,
                layout.craftingAllY,
                layout.craftingAllWidth,
                layout.craftingAllHeight,
                "All");
        buttonList.add(allIngredientsButton);
        sortModeButton = new RequestTableIconButton(
                SORT_MODE_BUTTON,
                layout.displayButtonX,
                layout.sortModeButtonY,
                layout.displayButtonWidth,
                layout.displayButtonHeight,
                Icon.NAME);
        buttonList.add(sortModeButton);
        sortDirectionButton = new RequestTableIconButton(
                SORT_DIRECTION_BUTTON,
                layout.displayButtonX,
                layout.sortDirectionButtonY,
                layout.displayButtonWidth,
                layout.displayButtonHeight,
                Icon.ASCENDING);
        buttonList.add(sortDirectionButton);
        filterModeButton = new RequestTableIconButton(
                FILTER_MODE_BUTTON,
                layout.visibilityButtonX,
                layout.filterModeButtonY,
                layout.displayButtonWidth,
                layout.displayButtonHeight,
                Icon.BOTH);
        buttonList.add(filterModeButton);
        requestMessagesButton = new RequestTableIconButton(
                REQUEST_MESSAGES_BUTTON,
                layout.displayButtonX,
                layout.requestMessagesButtonY,
                layout.displayButtonWidth,
                layout.displayButtonHeight,
                Icon.MESSAGES_ON);
        buttonList.add(requestMessagesButton);
        searchModeButton = addSidebarButton(SEARCH_MODE_BUTTON, Icon.SEARCH_STANDARD);
        saveSearchButton = addSidebarButton(SAVE_SEARCH_BUTTON, Icon.SAVE_SEARCH_OFF);
        terminalStyleButton = addSidebarButton(TERMINAL_STYLE_BUTTON, Icon.TERMINAL_SMALL);
        showItemsButton = addSidebarButton(SHOW_ITEMS_BUTTON, Icon.ITEMS_ON);
        showFluidsButton = addSidebarButton(SHOW_FLUIDS_BUTTON, Icon.FLUIDS_ON);
        itemUpgradesButton = addSidebarButton(ITEM_UPGRADES_BUTTON, Icon.UPGRADES);
        monitorGridButton = addSidebarButton(MONITOR_GRID_BUTTON, Icon.MONITOR_GRID);
        monitorTreeButton = addSidebarButton(MONITOR_TREE_BUTTON, Icon.MONITOR_TREE);
        networkButton = new RequestTableIconButton(
                NETWORK_VIEW_BUTTON,
                layout.networkButtonX,
                layout.networkButtonY,
                RequestTableLayout.MAIN_TAB_WIDTH,
                RequestTableLayout.TAB_HEIGHT,
                Icon.NETWORK);
        networkButton.setTabLabel("Main");
        itemButton = new RequestTableIconButton(
                ITEM_VIEW_BUTTON,
                layout.itemButtonX,
                layout.itemButtonY,
                RequestTableLayout.STORAGE_TAB_WIDTH,
                RequestTableLayout.TAB_HEIGHT,
                Icon.ITEMS_ON);
        itemButton.setTabLabel("Items");
        fluidButton = new RequestTableIconButton(
                FLUID_VIEW_BUTTON,
                layout.fluidButtonX,
                layout.fluidButtonY,
                RequestTableLayout.STORAGE_TAB_WIDTH,
                RequestTableLayout.TAB_HEIGHT,
                Icon.FLUIDS_ON);
        fluidButton.setTabLabel("Fluids");
        clearCraftingButton = new RequestTableIconButton(
                CLEAR_CRAFTING_BUTTON,
                layout.craftingClearX,
                layout.craftingClearY,
                layout.craftingClearSize,
                layout.craftingClearSize,
                Icon.CLEAR);
        buttonList.add(networkButton);
        buttonList.add(itemButton);
        buttonList.add(fluidButton);
        buttonList.add(clearCraftingButton);
        monitorButton = new RequestTableMonitorButton(CRAFTING_MONITOR_BUTTON, layout);
        monitorButton.enabled = false;
        buttonList.add(monitorButton);
        updateDisplayButtons();
        if (search == null) {
            search = new RequestTableSearchBar();
        }
        search.reposition(layout.searchX, layout.searchY, layout.searchWidth, layout.searchHeight);
        initializeSearch();
        initIngredientAmountField();
        updateContainerLayout();
        updateDisplayButtonLayout();
    }

    private RequestTableIconButton addSidebarButton(int id, Icon icon) {
        RequestTableIconButton button = new RequestTableIconButton(
                id,
                layout.displayButtonX,
                layout.sortModeButtonY,
                layout.displayButtonWidth,
                layout.displayButtonHeight,
                icon);
        buttonList.add(button);
        return button;
    }

    /**
     * Receives the server-built network list.
     */
    public void handleNetworkContent(List<RequestTableNetworkEntry> entries,
            RequestTableDisplaySettings displaySettings) {
        boolean styleChanged = this.displaySettings.getTerminalStyle() != displaySettings.getTerminalStyle();
        this.displaySettings = displaySettings;
        networkStock.clear();
        for (RequestTableNetworkEntry entry : entries) {
            if (!entry.fluid()) networkStock.put(entry.stack().getItem(), Math.max(0, entry.networkAmount()));
        }
        settingsReceived = true;
        if (styleChanged && mc != null) {
            initGui();
        }
        initializeSearch();
        networkGrid.setDisplaySettings(displaySettings);
        networkGrid.setEntries(entries);
        requestOverlay.updateEntry(entries);
        updateDisplayButtons();
    }

    /**
     * Prevents a delayed content packet from another request table from replacing this GUI's state.
     */
    public boolean isForTable(int x, int y, int z) {
        return table.container != null && table.container.xCoord == x
                && table.container.yCoord == y
                && table.container.zCoord == z;
    }

    /** Exposes virtual grid entries to NEI using the same hit detection as requests. */
    public RequestTableNetworkEntry getNetworkEntryUnderMouse(int mouseX, int mouseY) {
        if (view != RequestTableView.NETWORK || layout == null || hasSubGui() || requestOverlay.isOpen()) {
            return null;
        }
        return networkGrid.getEntryAt(layout, mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        try (var ignored = RequestTableRender.guiState()) {
            updateLayout();
            updateFluidControl();
            updateMonitorControl();
            updateContainerLayout();
            sendButton.enabled = isStorageView() && container.isInventoryReady();
            sendButton.visible = sendButton.enabled;
            sendButton.xPosition = layout.sendButtonX;
            sendButton.yPosition = layout.sendButtonY;
            sendButton.width = layout.sendButtonWidth;
            sendButton.height = layout.sendButtonHeight;
            requestIngredientsButton.xPosition = layout.craftingRequestX;
            requestIngredientsButton.yPosition = layout.craftingRequestY;
            requestIngredientsButton.width = layout.craftingRequestWidth;
            requestIngredientsButton.height = layout.craftingRequestHeight;
            requestIngredientsButton.visible = view != RequestTableView.CRAFTING_MONITOR;
            requestIngredientsButton.enabled = container.isInventoryReady() && hasCraftingInputs();
            allIngredientsButton.xPosition = layout.craftingAllX;
            allIngredientsButton.yPosition = layout.craftingAllY;
            allIngredientsButton.visible = view != RequestTableView.CRAFTING_MONITOR;
            allIngredientsButton.enabled = container.isInventoryReady() && getNetworkCraftingSets() > 0;
            updateDisplayButtonLayout();
            networkButton.setSelected(view == RequestTableView.NETWORK);
            itemButton.setSelected(view == RequestTableView.ITEM_STORAGE);
            fluidButton.setSelected(view == RequestTableView.FLUID_STORAGE);
            itemButton.setFill(table.getItemStorageFillLevel(), 0xffcf9d53);
            fluidButton.setFill(table.getFluidStorageFillLevel(), 0xff58a9cf);
            clearCraftingButton.xPosition = layout.craftingClearX;
            clearCraftingButton.yPosition = layout.craftingClearY;
            clearCraftingButton.enabled = hasCraftingInputs();
            clearCraftingButton.visible = view != RequestTableView.CRAFTING_MONITOR;
            ingredientAmountField.setVisible(view != RequestTableView.CRAFTING_MONITOR);

            networkButton.drawBehindPanel(mc, mouseX, mouseY);
            itemButton.drawBehindPanel(mc, mouseX, mouseY);
            fluidButton.drawBehindPanel(mc, mouseX, mouseY);
            GuiGraphics.drawGuiBackGround(
                    mc,
                    layout.centralLeft,
                    layout.centralTop,
                    layout.centralLeft + layout.centralWidth,
                    bottom,
                    zLevel,
                    true);
            drawHeader();
            drawMainPanel(mouseX, mouseY);
            if (view != RequestTableView.CRAFTING_MONITOR) {
                drawCraftingArea();
                if (!layout.compact) {
                    mc.fontRenderer.drawString(
                            "Inventory",
                            layout.playerLeft,
                            layout.playerTop - 12,
                            RequestTableGuiStyle.TEXT);
                }
                GuiGraphics.drawPlayerInventoryBackground(mc, layout.playerLeft, layout.playerTop);
            }
        }
    }

    private void drawHeader() {
        if (view == RequestTableView.CRAFTING_MONITOR) return;
        if (view == RequestTableView.NETWORK) {
            search.reposition(layout.searchX, layout.searchY, layout.searchWidth, layout.searchHeight);
            search.renderSearchBar();
        } else {
            String title = switch (view) {
                case ITEM_STORAGE -> "Item storage";
                case FLUID_STORAGE -> "Fluid storage";
                default -> throw new IllegalStateException("Unexpected value: " + view);
            };
            mc.fontRenderer.drawString(
                    title,
                    layout.searchX,
                    layout.searchY + (layout.searchHeight - 8) / 2,
                    RequestTableGuiStyle.TEXT);
        }
    }

    private void drawMainPanel(int mouseX, int mouseY) {
        switch (view) {
            case NETWORK -> {
                networkGrid.setSearch(getSearchText());
                networkGrid.render(this, layout, mouseX, mouseY);
            }
            case CRAFTING_MONITOR -> monitorPanel.render(layout, mouseX, mouseY);
            case ITEM_STORAGE, FLUID_STORAGE -> drawStoragePanel();
        }
    }

    private void drawStoragePanel() {
        int columns = RequestTableLayout.INVENTORY_COLUMNS;
        int size = view == RequestTableView.ITEM_STORAGE ? table.inv.getSizeInventory()
                : table.getFluidStorage().getSizeInventory();
        int rows = (size + columns - 1) / columns;
        int visibleRows = layout.getVisibleStorageRows();
        int maxScroll = Math.max(0, rows - visibleRows);
        drawStorageScrollbar(maxScroll);
        RequestTableRender.slotGrid(
                mc,
                layout.panelLeft,
                layout.panelTop,
                columns,
                visibleRows,
                size - storageScrollRow * columns);
    }

    private void drawStorageScrollbar(int maxScroll) {
        RequestTableGuiStyle.scrollbar(layout, storageScrollRow, maxScroll);
    }

    private void drawCraftingArea() {
        RequestTableRender.slotGrid(mc, layout.craftingLeft - 1, layout.getCraftingGridTop() - 1, 3, 3);
        GuiGraphics.drawBigSlotBackground(mc, layout.craftingResultX - 5, layout.craftingResultY - 5);
        int arrowX = layout.craftingLeft + 54;
        int arrowY = layout.craftingResultY + 7;
        Gui.drawRect(arrowX, arrowY, arrowX + 10, arrowY + 3, 0xff707070);
        for (int i = 0; i < 5; i++) {
            Gui.drawRect(arrowX + 8 + i, arrowY - 4 + i, arrowX + 9 + i, arrowY + 7 - i, 0xff707070);
        }
        mc.fontRenderer
                .drawString("Sets", layout.craftingAmountX, layout.craftingAmountY - 12, RequestTableGuiStyle.TEXT);
        if (ingredientAmountField != null) {
            ingredientAmountField.drawTextBox();
        }
        String amount = StringUtils.getFormatedStackSize(container.getCraftableAmount(), true);
        mc.fontRenderer.drawString(
                "Craftable: " + amount,
                layout.getCraftableLabelX(),
                layout.getCraftableLabelY(),
                container.getCraftableAmount() > 0 ? 0x37622e : RequestTableGuiStyle.MUTED);
    }

    private boolean hasCraftingInputs() {
        for (int i = 0; i < table.matrix.getSizeInventory(); i++) {
            if (table.matrix.getStackInSlot(i) != null) {
                return true;
            }
        }
        return false;
    }

    private void updateFluidControl() {
        boolean enabled = table.isFluidEnabled();
        networkGrid.setFluidsEnabled(enabled);
        fluidButton.enabled = enabled && container.isInventoryReady();
        fluidButton.visible = enabled;
        if (container.isInventoryReady() && !enabled) {
            if (view == RequestTableView.FLUID_STORAGE) {
                setView(RequestTableView.NETWORK);
            }
            if (requestOverlay.isOpen() && requestOverlay.getEntry().fluid()) {
                requestOverlay.close();
            }
        }
    }

    private void updateMonitorControl() {
        boolean installed = table.hasMonitoringUpgrade();
        if (view == RequestTableView.CRAFTING_MONITOR && container.isInventoryReady() && !installed) {
            setView(RequestTableView.NETWORK);
        }
        monitorButton.enabled = installed && container.isInventoryReady();
        monitorButton.setSelected(view == RequestTableView.CRAFTING_MONITOR);
        monitorButton.xPosition = layout.monitorButtonX;
        monitorButton.yPosition = layout.monitorButtonY;
        monitorButton.width = layout.monitorButtonWidth;
        monitorButton.height = layout.monitorButtonHeight;
    }

    private boolean isStorageView() {
        return view == RequestTableView.ITEM_STORAGE || view == RequestTableView.FLUID_STORAGE;
    }

    @Override
    protected void func_146977_a(Slot slot) {
        if (slot.inventory != table.inv && slot.inventory != table.getFluidStorage()) {
            super.func_146977_a(slot);
            return;
        }
        // Scope the count renderer to this slot so vanilla retains its drag and touchscreen previews.
        RenderItem previousRenderer = itemRender;
        storageItemRenderer.configure(previousRenderer, slot instanceof RequestTableFluidSlot);
        itemRender = storageItemRenderer;
        try {
            super.func_146977_a(slot);
        } finally {
            itemRender = previousRenderer;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (scrollbarDragging && (!Mouse.isButtonDown(0) || requestOverlay.isOpen() || hasSubGui())) {
            scrollbarDragging = false;
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (requestOverlay.isOpen()) {
            try (var ignored = RequestTableRender.guiState()) {
                Gui.drawRect(0, 0, width, height, 0x66000000);
                requestOverlay.render(this, mouseX, mouseY);
            }
        } else if (!hasSubGui()) {
            drawDisplayButtonTooltip(mouseX, mouseY);
            if (view == RequestTableView.CRAFTING_MONITOR) monitorPanel.drawTooltip(mouseX, mouseY);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case REQUEST_INGREDIENTS_BUTTON -> requestCraftingIngredients();
            case ALL_INGREDIENTS_BUTTON -> {
                ingredientAmountField.setText(Integer.toString(getNetworkCraftingSets()));
                ingredientAmountField.setCursorPositionEnd();
                ingredientAmountField.setFocused(false);
            }
            case SORT_MODE_BUTTON -> applyDisplaySettings(displaySettings.nextSortMode());
            case SORT_DIRECTION_BUTTON -> applyDisplaySettings(displaySettings.nextSortDirection());
            case FILTER_MODE_BUTTON -> applyDisplaySettings(displaySettings.nextFilterMode());
            case REQUEST_MESSAGES_BUTTON -> applyDisplaySettings(displaySettings.toggleRequestMessages());
            case SEARCH_MODE_BUTTON -> applyDisplaySettings(displaySettings.nextSearchBoxMode());
            case SAVE_SEARCH_BUTTON -> applyDisplaySettings(displaySettings.toggleSaveSearch());
            case TERMINAL_STYLE_BUTTON -> applyDisplaySettings(displaySettings.nextTerminalStyle());
            case SHOW_ITEMS_BUTTON -> applyDisplaySettings(displaySettings.toggleItems());
            case SHOW_FLUIDS_BUTTON -> applyDisplaySettings(displaySettings.toggleFluids());
            case MONITOR_GRID_BUTTON -> monitorPanel.setTree(false);
            case MONITOR_TREE_BUTTON -> monitorPanel.setTree(true);
            case ITEM_UPGRADES_BUTTON -> MainProxy
                    .sendPacketToServer(PacketHandler.getPacket(RequestTableOpenUpgradesPacket.class).setInteger(0));
            case NETWORK_VIEW_BUTTON -> setView(RequestTableView.NETWORK);
            case ITEM_VIEW_BUTTON -> setView(RequestTableView.ITEM_STORAGE);
            case FLUID_VIEW_BUTTON -> setView(RequestTableView.FLUID_STORAGE);
            case CRAFTING_MONITOR_BUTTON -> {
                if (button.enabled) {
                    setView(
                            view == RequestTableView.CRAFTING_MONITOR ? monitorReturnView
                                    : RequestTableView.CRAFTING_MONITOR);
                }
            }
            case SEND_BUTTON -> MainProxy.sendPacketToServer(
                    PacketHandler.getPacket(RequestTableSendStoragePacket.class)
                            .setInteger(view == RequestTableView.FLUID_STORAGE ? 1 : 0).setTilePos(table.container));
            case CLEAR_CRAFTING_BUTTON -> {
                table.clearCraftingGrid();
                MainProxy.sendPacketToServer(
                        PacketHandler.getPacket(RequestTableClearCraftingPacket.class).setTilePos(table.container));
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (view == RequestTableView.CRAFTING_MONITOR && monitorPanel.click(mouseX, mouseY, button)) return;
        if (scrollbarDragging) {
            return;
        }
        if (requestOverlay.mouseClicked(
                mouseX,
                mouseY,
                button,
                this::submitOverlayRequest,
                this,
                settings -> applyDisplaySettings(displaySettings.withOrderSettings(settings)))) {
            return;
        }
        searchFocusChosen = true;
        if (search != null) {
            search.onGuiClick(mouseX, mouseY);
        }
        if (view != RequestTableView.CRAFTING_MONITOR && ingredientAmountField != null) {
            ingredientAmountField.mouseClicked(mouseX, mouseY, button);
            if (inside(
                    mouseX,
                    mouseY,
                    layout.craftingAmountX,
                    layout.craftingAmountY,
                    layout.craftingAmountWidth,
                    layout.craftingAmountHeight)) {
                return;
            }
        }
        if (view != RequestTableView.CRAFTING_MONITOR
                && inside(mouseX, mouseY, layout.scrollbarX, layout.panelTop, 7, layout.panelHeight)) {
            if (button == 0) {
                startScrollbarDrag(mouseY);
            }
            return;
        }
        if (view == RequestTableView.NETWORK) {
            if (inside(mouseX, mouseY, layout.searchX, layout.searchY, layout.searchWidth, layout.searchHeight)) {
                focusSearch(true);
                search.handleClick(mouseX, mouseY, button);
                return;
            }
            networkGrid.setSearch(getSearchText());
            RequestTableNetworkEntry entry = networkGrid.getEntryAt(layout, mouseX, mouseY);
            if ((button == 0 || button == 1) && !shouldOpenRequestOverlay(button)
                    && mc.thePlayer.inventory.getItemStack() != null
                    && inside(
                            mouseX,
                            mouseY,
                            layout.panelLeft,
                            layout.panelTop,
                            layout.panelWidth,
                            layout.panelHeight)) {
                MainProxy.sendPacketToServer(
                        PacketHandler.getPacket(RequestTableNetworkInteractPacket.class).setCursorInteraction(true)
                                .setFluid(entry != null && entry.fluid()).setMouseButton(button).setShift(isShiftDown())
                                .setStack(entry == null ? null : entry.stack()).setTilePos(table.container));
                return;
            }
            if (entry != null) {
                if (shouldOpenRequestOverlay(button) || ((button == 0 || button == 1) && entry.internalAmount() <= 0)) {
                    requestOverlay.open(entry, mc.fontRenderer, width, height, displaySettings.getOrderSettings());
                } else if (button == 0 || button == 1) {
                    MainProxy.sendPacketToServer(
                            PacketHandler.getPacket(RequestTableNetworkInteractPacket.class).setFluid(entry.fluid())
                                    .setMouseButton(button).setShift(isShiftDown()).setStack(entry.stack())
                                    .setTilePos(table.container));
                }
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
        if (view == RequestTableView.CRAFTING_MONITOR) {
            if (button == 0) monitorPanel.move(mouseX, mouseY);
            return;
        }
        if (scrollbarDragging) {
            if (button == 0) {
                dragScrollbar(mouseY);
            }
            return;
        }
        super.mouseClickMove(mouseX, mouseY, button, timeSinceLastClick);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int button) {
        if (view == RequestTableView.CRAFTING_MONITOR && button == 0) monitorPanel.release();
        if (scrollbarDragging) {
            if (button == 0) {
                scrollbarDragging = false;
            }
            return;
        }
        if (button != 0 || !isShiftDown()) {
            super.mouseMovedOrUp(mouseX, mouseY, button);
            return;
        }
        // Vanilla emits one shift-click per matching stack during a Shift-double-click release.
        playerShiftClickBatch = new ArrayList<>();
        try {
            super.mouseMovedOrUp(mouseX, mouseY, button);
        } finally {
            List<Integer> slots = playerShiftClickBatch;
            playerShiftClickBatch = null;
            if (!slots.isEmpty()) sendPlayerShiftClicks(slots);
        }
    }

    private void startScrollbarDrag(int mouseY) {
        int maxScroll = getMaxScrollRow();
        if (maxScroll == 0) {
            return;
        }
        int scrollRow = view == RequestTableView.NETWORK ? networkGrid.getScrollRow() : storageScrollRow;
        int thumbHeight = RequestTableGuiStyle.getScrollbarThumbHeight(layout, maxScroll);
        int thumbTop = RequestTableGuiStyle.getScrollbarThumbTop(layout, Math.min(scrollRow, maxScroll), maxScroll);
        boolean onThumb = mouseY >= thumbTop && mouseY < thumbTop + thumbHeight;
        scrollbarDragOffset = onThumb ? mouseY - thumbTop : thumbHeight / 2;
        scrollbarDragging = true;
        if (!onThumb) {
            dragScrollbar(mouseY);
        }
    }

    private void dragScrollbar(int mouseY) {
        int maxScroll = getMaxScrollRow();
        int travel = layout.panelHeight - RequestTableGuiStyle.getScrollbarThumbHeight(layout, maxScroll);
        int position = Math.max(0, Math.min(travel, mouseY - layout.panelTop - scrollbarDragOffset));
        int row = travel <= 0 ? 0 : Math.round((float) position * maxScroll / travel);
        if (view == RequestTableView.NETWORK) {
            networkGrid.setScrollRow(row, layout);
        } else {
            storageScrollRow = row;
            updateContainerLayout();
        }
    }

    private int getMaxScrollRow() {
        if (view == RequestTableView.CRAFTING_MONITOR) {
            return 0;
        }
        return view == RequestTableView.NETWORK ? networkGrid.getMaxScrollRow(layout) : getMaxStorageScrollRow();
    }

    @Override
    protected void handleMouseClick(Slot slot, int slotId, int mouseButton, int mode) {
        if (mode == 1 && slot != null && slot.inventory == player.inventory) {
            // Player inventory indexes stay valid while a server snapshot changes the storage slot layout.
            if (playerShiftClickBatch != null) {
                playerShiftClickBatch.add(slot.getSlotIndex());
            } else {
                sendPlayerShiftClicks(Collections.singletonList(slot.getSlotIndex()));
            }
            return;
        }
        // Wait for the server's layout before sending container slot indexes.
        if (container.isInventoryReady()) {
            if (mode == 5 && (mouseButton & 3) == 2) {
                container.awaitInventory();
            }
            super.handleMouseClick(slot, slotId, mouseButton, mode);
        }
    }

    private void sendPlayerShiftClicks(List<Integer> slots) {
        container.awaitInventory();
        MainProxy.sendPacketToServer(
                PacketHandler.getPacket(RequestTableShiftClickPacket.class).setClicks(container.windowId, slots, view));
    }

    @Override
    protected void keyTyped(char typed, int keyCode) {
        if (requestOverlay.keyTyped(
                typed,
                keyCode,
                this::submitOverlayRequest,
                settings -> applyDisplaySettings(displaySettings.withOrderSettings(settings)))) {
            return;
        }
        if (view != RequestTableView.CRAFTING_MONITOR && ingredientAmountField != null
                && ingredientAmountField.isFocused()) {
            switch (keyCode) {
                case Keyboard.KEY_ESCAPE -> ingredientAmountField.setFocused(false);
                case Keyboard.KEY_RETURN, Keyboard.KEY_NUMPADENTER -> requestCraftingIngredients();
                default -> ingredientAmountField.textboxKeyTyped(typed, keyCode);
            }
            return;
        }
        if (view == RequestTableView.NETWORK && search.handleKey(typed, keyCode)) {
            networkGrid.setSearch(getSearchText());
            return;
        }
        super.keyTyped(typed, keyCode);
    }

    @Override
    public void handleMouseInputSub() {
        if (requestOverlay.isOpen()) {
            super.handleMouseInputSub();
            return;
        }
        int wheel = Mouse.getEventDWheel();
        if (view == RequestTableView.CRAFTING_MONITOR && !hasSubGui()) {
            int mouseX = Mouse.getEventX() * width / mc.displayWidth;
            int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            monitorPanel.scroll(wheel, mouseX, mouseY);
        }
        if (wheel != 0 && !scrollbarDragging && view != RequestTableView.CRAFTING_MONITOR) {
            int rows = wheel > 0 ? -1 : 1;
            if (view == RequestTableView.NETWORK) {
                networkGrid.setSearch(getSearchText());
                networkGrid.scroll(rows, layout);
            } else {
                storageScrollRow += rows;
                clampStorageScroll();
            }
        }
        super.handleMouseInputSub();
    }

    @Override
    public void resetSubGui() {
        super.resetSubGui();
        refreshNetwork();
    }

    /**
     * Opens the normal request-result popup for this GUI.
     */
    public void handleRequestAnswer(Collection<IResource> items, boolean error, ISubGuiControler control,
            EntityPlayer player) {
        while (control.hasSubGui()) {
            control = control.getSubGui();
        }
        if (error) {
            control.setSubGui(new GuiRequestPopup(player, "You are missing:", items));
        } else {
            control.setSubGui(new GuiRequestPopup(player, "Request successful!", items));
        }
    }

    private void submitOverlayRequest() {
        if (!requestOverlay.isOpen() || !requestOverlay.hasValidAmount()) {
            return;
        }
        RequestTableNetworkEntry entry = requestOverlay.getEntry();
        MainProxy.sendPacketToServer(
                PacketHandler.getPacket(RequestTableSubmitPacket.class).setFluid(entry.fluid()).setDimension(dimension)
                        .setStack(entry.stack().getItem().makeStack(requestOverlay.getAmount()))
                        .setTilePos(table.container));
        requestOverlay.close();
        refreshNetwork();
    }

    private void setView(RequestTableView newView) {
        if (view == newView) {
            return;
        }
        if (newView == RequestTableView.CRAFTING_MONITOR
                && (!container.isInventoryReady() || !table.hasMonitoringUpgrade())) {
            return;
        }
        if (newView == RequestTableView.FLUID_STORAGE && (!container.isInventoryReady() || !table.isFluidEnabled())) {
            return;
        }
        if (newView == RequestTableView.CRAFTING_MONITOR) monitorReturnView = view;
        if (view == RequestTableView.CRAFTING_MONITOR) {
            monitorModel.leave();
            monitorPanel.release();
        }
        view = newView;
        scrollbarDragging = false;
        requestOverlay.close();
        ingredientAmountField.setFocused(false);
        if (view != RequestTableView.NETWORK) {
            search.setFocus(false);
        }
        storageScrollRow = 0;
        if (view == RequestTableView.NETWORK) {
            refreshNetwork();
        }
        initGui();
        if (view == RequestTableView.CRAFTING_MONITOR) monitorModel.enter(container.windowId);
    }

    /** Receives only snapshots belonging to this GUI's current monitor subscription. */
    public void handleMonitorChunk(long session, long transfer, int part, int parts, byte[] payload) {
        monitorModel.chunk(session, transfer, part, parts, payload);
    }

    private void refreshNetwork() {
        MainProxy.sendPacketToServer(
                PacketHandler.getPacket(RequestTableRefreshPacket.class).setInteger(dimension)
                        .setTilePos(table.container));
    }

    private void applyDisplaySettings(RequestTableDisplaySettings newSettings) {
        boolean styleChanged = displaySettings.getTerminalStyle() != newSettings.getTerminalStyle();
        boolean searchModeChanged = displaySettings.getSearchBoxMode() != newSettings.getSearchBoxMode();
        displaySettings = newSettings.rememberSearch(getSearchText());
        networkGrid.setDisplaySettings(displaySettings);
        if (styleChanged) {
            initGui();
        }
        if (searchModeChanged) {
            focusSearch(displaySettings.getSearchBoxMode().isAutoFocus());
            syncNeiSearch();
        }
        updateDisplayButtons();
        sendDisplaySettings();
    }

    private void sendDisplaySettings() {
        MainProxy.sendPacketToServer(
                PacketHandler.getPacket(RequestTableDisplaySettingsPacket.class).setSettings(displaySettings)
                        .setTilePos(table.container));
    }

    private void initializeSearch() {
        if (search == null || !settingsReceived || searchInitialized) {
            return;
        }
        if (!searchEdited) {
            restoringSearch = true;
            search.setText(displaySettings.isSaveSearch() ? displaySettings.getSavedSearchText() : "");
            restoringSearch = false;
        }
        if (!searchFocusChosen) {
            focusSearch(displaySettings.getSearchBoxMode().isAutoFocus());
        }
        searchInitialized = true;
        syncNeiSearch();
    }

    private void focusSearch(boolean focused) {
        boolean focusTerminal = focused && view == RequestTableView.NETWORK;
        if (focusTerminal && LayoutManager.searchField != null) {
            LayoutManager.searchField.setFocus(false);
        }
        search.setFocus(focusTerminal);
    }

    private void handleSearchTextChange(String oldText) {
        if (oldText.equals(getSearchText())) {
            return;
        }
        networkGrid.setSearch(getSearchText());
        if (!restoringSearch) {
            searchEdited = true;
            syncNeiSearch();
        }
    }

    /** Only terminal edits are sent to NEI; changes made in NEI never replace terminal text. */
    private void syncNeiSearch() {
        if (displaySettings.getSearchBoxMode().isNeiSynced() && LayoutManager.searchField != null
                && !getSearchText().equals(LayoutManager.searchField.text())) {
            LayoutManager.searchField.setText(getSearchText());
        }
    }

    @Override
    public void onGuiClosed() {
        monitorModel.leave();
        monitorPanel.release();
        scrollbarDragging = false;
        if (search != null) {
            search.setFocus(false);
        }
        if (settingsReceived) {
            RequestTableDisplaySettings saved = displaySettings.rememberSearch(getSearchText());
            if (!saved.equals(displaySettings)) {
                displaySettings = saved;
                sendDisplaySettings();
            }
        }
        super.onGuiClosed();
    }

    private void updateDisplayButtons() {
        if (sortModeButton == null) {
            return;
        }
        requestMessagesButton
                .setIcon(displaySettings.isRequestMessagesEnabled() ? Icon.MESSAGES_ON : Icon.MESSAGES_OFF);
        requestMessagesButton.setSelected(displaySettings.isRequestMessagesEnabled());
        searchModeButton.setIcon(switch (displaySettings.getSearchBoxMode()) {
            case AUTO -> Icon.SEARCH_AUTO;
            case NEI_SYNC_AUTO -> Icon.SEARCH_NEI_AUTO;
            case NEI_SYNC_STANDARD -> Icon.SEARCH_NEI_STANDARD;
            case STANDARD -> Icon.SEARCH_STANDARD;
        });
        saveSearchButton.setIcon(displaySettings.isSaveSearch() ? Icon.SAVE_SEARCH_ON : Icon.SAVE_SEARCH_OFF);
        saveSearchButton.setSelected(displaySettings.isSaveSearch());
        boolean tall = displaySettings.getTerminalStyle() == RequestTableDisplaySettings.TerminalStyle.TALL;
        terminalStyleButton.setIcon(tall ? Icon.TERMINAL_TALL : Icon.TERMINAL_SMALL);
        terminalStyleButton.setSelected(tall);
        showItemsButton.setIcon(displaySettings.isShowItems() ? Icon.ITEMS_ON : Icon.ITEMS_OFF);
        showItemsButton.setSelected(displaySettings.isShowItems());
        showFluidsButton.setIcon(displaySettings.isShowFluids() ? Icon.FLUIDS_ON : Icon.FLUIDS_OFF);
        showFluidsButton.setSelected(displaySettings.isShowFluids());
        sortModeButton.setIcon(
                displaySettings.getSortMode() == RequestTableDisplaySettings.SortMode.NAME ? Icon.NAME : Icon.AMOUNT);
        sortDirectionButton.setIcon(
                displaySettings.getSortDirection() == RequestTableDisplaySettings.SortDirection.ASCENDING
                        ? Icon.ASCENDING
                        : Icon.DESCENDING);
        filterModeButton.setIcon(switch (displaySettings.getFilterMode()) {
            case STORED -> Icon.STORED;
            case CRAFTABLE -> Icon.CRAFTABLE;
            case BOTH -> Icon.BOTH;
        });
    }

    private void updateDisplayButtonLayout() {
        boolean enabled = view == RequestTableView.NETWORK && settingsReceived;
        sortModeButton.enabled = enabled;
        sortDirectionButton.enabled = enabled;
        filterModeButton.enabled = enabled;
        searchModeButton.enabled = enabled;
        saveSearchButton.enabled = enabled;
        showItemsButton.enabled = enabled;
        showFluidsButton.enabled = enabled && table.isFluidEnabled();
        terminalStyleButton.enabled = settingsReceived;
        requestMessagesButton.enabled = enabled;
        sendButton.enabled = isStorageView() && container.isInventoryReady();
        itemUpgradesButton.enabled = container.isInventoryReady();
        monitorGridButton.enabled = view == RequestTableView.CRAFTING_MONITOR;
        monitorTreeButton.enabled = monitorGridButton.enabled;
        monitorGridButton.setSelected(!monitorPanel.isTree());
        monitorTreeButton.setSelected(monitorPanel.isTree());
        layoutSidebarColumn(
                layout.displayButtonX,
                monitorGridButton,
                monitorTreeButton,
                sendButton,
                sortModeButton,
                sortDirectionButton,
                searchModeButton,
                saveSearchButton,
                terminalStyleButton,
                requestMessagesButton,
                itemUpgradesButton);
        layoutSidebarColumn(layout.visibilityButtonX, filterModeButton, showItemsButton, showFluidsButton);
    }

    private void layoutSidebarColumn(int x, RequestTableIconButton... buttons) {
        int top = layout.sortModeButtonY;
        for (RequestTableIconButton button : buttons) {
            button.visible = button.enabled;
            if (button.visible) {
                button.xPosition = x;
                button.yPosition = top;
                button.width = layout.displayButtonWidth;
                button.height = layout.displayButtonHeight;
                top += layout.displayButtonHeight + RequestTableLayout.SIDEBAR_ROW_GAP;
            }
        }
    }

    private void drawDisplayButtonTooltip(int mouseX, int mouseY) {
        GuiButton button = getHoveredDisplayButton(mouseX, mouseY);
        if (button == null) {
            if (view != RequestTableView.CRAFTING_MONITOR && inside(
                    mouseX,
                    mouseY,
                    layout.getCraftableLabelX(),
                    layout.getCraftableLabelY(),
                    layout.centralLeft + layout.centralWidth - layout.getCraftableLabelX(),
                    10)) {
                GuiGraphics.drawToolTip(
                        mouseX,
                        mouseY,
                        Arrays.asList(
                                "Craftable items",
                                container.getCraftableAmount() + " output items",
                                "Table storage and your inventory.",
                                "Before reusing returned containers or tools."),
                        EnumChatFormatting.WHITE);
            }
            return;
        }
        List<String> tooltip = switch (button.id) {
            case SORT_MODE_BUTTON -> {
                String mode = displaySettings.getSortMode() == RequestTableDisplaySettings.SortMode.NAME ? "Item name"
                        : "Item amount";
                yield Arrays.asList("Sort by", mode);
            }
            case SORT_DIRECTION_BUTTON -> {
                String direction = displaySettings.getSortDirection()
                        == RequestTableDisplaySettings.SortDirection.ASCENDING ? "Ascending" : "Descending";
                yield Arrays.asList("Sort direction", direction);
            }
            case FILTER_MODE_BUTTON -> {
                String filter = switch (displaySettings.getFilterMode()) {
                    case STORED -> "Stored";
                    case CRAFTABLE -> "Craftable";
                    case BOTH -> "Stored and craftable";
                };
                yield Arrays.asList("Show entries", filter);
            }
            case REQUEST_MESSAGES_BUTTON -> Arrays.asList(
                    "Request messages: " + (displaySettings.isRequestMessagesEnabled() ? "On" : "Off"),
                    "Toggle request-result popups and chat.",
                    "Items, fluids and crafting ingredients.");
            case SEARCH_MODE_BUTTON -> {
                RequestTableDisplaySettings.SearchBoxMode mode = displaySettings.getSearchBoxMode();
                yield Arrays.asList(
                        "Search box mode: " + mode.getLabel(),
                        "Focus on opening: " + (mode.isAutoFocus() ? "Yes" : "No"),
                        "Sync to NEI: " + (mode.isNeiSynced() ? "Yes (one way)" : "No"),
                        "Click to cycle search mode.");
            }
            case SAVE_SEARCH_BUTTON -> Arrays.asList(
                    "Save search text: " + (displaySettings.isSaveSearch() ? "Yes" : "No"),
                    "Remember the search when reopening this table.");
            case TERMINAL_STYLE_BUTTON -> {
                boolean tall = displaySettings.getTerminalStyle() == RequestTableDisplaySettings.TerminalStyle.TALL;
                yield Arrays.asList(
                        "Terminal size: " + (tall ? "Tall" : "Small"),
                        tall ? "Use the available window height."
                                : "Up to " + RequestTableLayout.SMALL_ROWS + " content rows.");
            }
            case SHOW_ITEMS_BUTTON -> Arrays.asList(
                    "Show items: " + (displaySettings.isShowItems() ? "Yes" : "No"),
                    "Toggle items in the Main list.");
            case MONITOR_GRID_BUTTON -> Arrays.asList("Grid view", "Combines identical resources across all branches.");
            case MONITOR_TREE_BUTTON -> Arrays
                    .asList("Tree view", "Show crafting dependencies.", "Drag to move. Mouse wheel changes zoom.");
            case SHOW_FLUIDS_BUTTON -> Arrays.asList(
                    "Show fluids: " + (displaySettings.isShowFluids() ? "Yes" : "No"),
                    "Toggle fluids in the Main list.");
            case ITEM_UPGRADES_BUTTON -> Arrays.asList(
                    "Table upgrades",
                    "Item slots: " + RequestTableStorageUpgradeConfig.getTierName(table.getItemSlotTier()),
                    "Item capacity: " + RequestTableStorageUpgradeConfig.getTierName(table.getItemSizeTier()),
                    "Fluid tanks: " + RequestTableStorageUpgradeConfig.getTierName(table.getFluidSlotTier()),
                    "Fluid capacity: " + RequestTableStorageUpgradeConfig.getTierName(table.getFluidSizeTier()),
                    "Consume components for permanent improvements.");
            case ITEM_VIEW_BUTTON -> Arrays.asList(
                    "Internal item storage",
                    "Used slots: " + usedItemSlots() + "/" + table.inv.getSizeInventory(),
                    table.inv.getInventoryStackLimit() + " items per slot");
            case FLUID_VIEW_BUTTON -> Arrays.asList(
                    "Internal fluid storage",
                    "Used tanks: " + usedFluidTanks() + "/" + table.getFluidStorage().getSizeInventory(),
                    table.getFluidStorage().getSlotCapacity() + " mB per tank");
            case CRAFTING_MONITOR_BUTTON -> table.hasMonitoringUpgrade()
                    ? Arrays.asList(
                            "Crafting monitor",
                            !monitorButton.enabled ? "Updating upgrades..."
                                    : view == RequestTableView.CRAFTING_MONITOR ? "Close the crafting monitor view."
                                            : "Open the crafting monitor view.")
                    : Arrays.asList(
                            "Crafting monitor",
                            "Requires the monitoring upgrade.",
                            "Unlock it on the upgrade board.");
            case REQUEST_INGREDIENTS_BUTTON -> Arrays.asList(
                    "Request ingredient sets",
                    getIngredientRequestAmount() + " craft sets",
                    "Request full sets without subtracting existing ingredients.",
                    "Returned tools are requested once per tool slot.");
            case CLEAR_CRAFTING_BUTTON -> Arrays.asList("Clear crafting grid");
            case ALL_INGREDIENTS_BUTTON -> Arrays.asList(
                    "All network sets",
                    getNetworkCraftingSets() + " complete sets from network stock.",
                    "Returned tools are requested once per tool slot.",
                    "Fill the amount, then request the ingredient sets.");
            case SEND_BUTTON -> Arrays.asList(
                    "Send all " + (view == RequestTableView.FLUID_STORAGE ? "fluids" : "items"),
                    "Return stored contents to the network.");
            default -> Arrays.asList(
                    "Network",
                    table.isFluidEnabled() ? "Browse stored and craftable items and fluids."
                            : "Browse stored and craftable items.");
        };
        GuiGraphics.drawToolTip(mouseX, mouseY, tooltip, EnumChatFormatting.WHITE);
    }

    private GuiButton getHoveredDisplayButton(int mouseX, int mouseY) {
        for (GuiButton button : buttonList) {
            if (button instanceof RequestTableButton && RequestTableRender.hovered(button, mouseX, mouseY)) {
                return button;
            }
        }
        return null;
    }

    private int usedItemSlots() {
        int count = 0;
        for (int slot = 0; slot < table.inv.getSizeInventory(); slot++) {
            var stack = table.inv.getStackInSlot(slot);
            if (stack != null && stack.stackSize > 0) count++;
        }
        return count;
    }

    private int usedFluidTanks() {
        int count = 0;
        var storage = table.getFluidStorage();
        for (int tank = 0; tank < storage.getSizeInventory(); tank++) {
            var fluid = storage.getFluid(tank);
            if (fluid != null && fluid.amount > 0) count++;
        }
        return count;
    }

    private void initIngredientAmountField() {
        String text = ingredientAmountField == null ? "1" : ingredientAmountField.getText();
        boolean focused = ingredientAmountField != null && ingredientAmountField.isFocused();
        ingredientAmountField = new RequestTableNumberField(
                mc.fontRenderer,
                layout.craftingAmountX,
                layout.craftingAmountY,
                layout.craftingAmountWidth,
                layout.craftingAmountHeight,
                10,
                1,
                Integer.MAX_VALUE);
        ingredientAmountField.setText(text);
        ingredientAmountField.setFocused(focused);
    }

    private int getIngredientRequestAmount() {
        return ingredientAmountField == null ? 1 : ingredientAmountField.getValue();
    }

    /** Uses the same ingredient identities and reusable-tool rules as server-side requests. */
    private int getNetworkCraftingSets() {
        return table.getNetworkCraftingSets(networkStock);
    }

    private void requestCraftingIngredients() {
        MainProxy.sendPacketToServer(
                PacketHandler.getPacket(RequestTableRequestIngredientsPacket.class)
                        .setInteger(getIngredientRequestAmount()).setTilePos(table.container));
        refreshNetwork();
    }

    private void updateContainerLayout() {
        if (layout == null) {
            return;
        }
        clampStorageScroll();
        container.layout(layout, view, storageScrollRow);
    }

    private void updateLayout() {
        layout = new RequestTableLayout(guiLeft, guiTop, xSize, ySize);
        if (ingredientAmountField != null) {
            ingredientAmountField.xPosition = layout.craftingAmountX;
            ingredientAmountField.yPosition = layout.craftingAmountY;
            ingredientAmountField.width = layout.craftingAmountWidth;
            ingredientAmountField.height = layout.craftingAmountHeight;
        }
    }

    private String getSearchText() {
        return search == null ? "" : search.getContent();
    }

    private boolean shouldOpenRequestOverlay(int button) {
        return button == 2 || (isCtrlDown() && (button == 0 || button == 1));
    }

    private boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private boolean isCtrlDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }

    private void clampStorageScroll() {
        storageScrollRow = Math.max(0, Math.min(getMaxStorageScrollRow(), storageScrollRow));
    }

    private int getMaxStorageScrollRow() {
        if (view == RequestTableView.CRAFTING_MONITOR) {
            return 0;
        }
        int columns = RequestTableLayout.INVENTORY_COLUMNS;
        int size = view == RequestTableView.FLUID_STORAGE ? table.getFluidStorage().getSizeInventory()
                : table.inv.getSizeInventory();
        int rows = (size + columns - 1) / columns;
        int visibleRows = layout.getVisibleStorageRows();
        return Math.max(0, rows - visibleRows);
    }

    private class RequestTableSearchBar extends GuiSearchBar {

        public RequestTableSearchBar() {
            super("new_request_table_search");
        }

        @Override
        public void reposition(int left, int top, int width, int height) {
            super.reposition(left, top, width, height);
            setDimensionsAndColor();
        }

        @Override
        protected void setDimensionsAndColor() {
            field.setEnableBackgroundDrawing(false);
            field.setCanLoseFocus(false);
            field.xPosition = x + 3;
            field.yPosition = y + Math.max(1, (h - 8) / 2);
            field.width = w - 6;
            field.height = 8;
            field.setTextColor(0xffe0e0e0);
        }

        @Override
        public void draw(int mouseX, int mouseY) {
            RequestTableGuiStyle.inset(x, y, w, h);
            if (isFocused()) {
                Gui.drawRect(x + 1, y + 1, x + w - 1, y + h - 1, 0xff555555);
            }
            super.draw(mouseX, mouseY);
        }

        @Override
        public void setText(String text) {
            super.setText(text);
            field.setCursorPositionEnd();
        }

        @Override
        public void onTextChange(String oldText) {
            handleSearchTextChange(oldText);
        }
    }
}
