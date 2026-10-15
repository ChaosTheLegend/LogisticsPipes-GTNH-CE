package logisticspipes.gui.modularUI.pipes.requesttable;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.IGuiAction;
import com.cleanroommc.modularui.api.widget.IPositioned;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.sizer.Unit;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.crafting.requesttable.RequestTableView;
import logisticspipes.crafting.requesttable.gui.RequestTableButton;
import logisticspipes.crafting.requesttable.gui.RequestTableGuiStyle;
import logisticspipes.crafting.requesttable.gui.RequestTableIconButton;
import logisticspipes.crafting.requesttable.gui.RequestTableIconButton.Icon;
import logisticspipes.crafting.requesttable.gui.RequestTableLayout;
import logisticspipes.crafting.requesttable.gui.RequestTableMonitorButton;
import logisticspipes.crafting.requesttable.gui.RequestTableRender;
import logisticspipes.crafting.requesttable.gui.upgrade.RequestTableUpgradeStatus;
import logisticspipes.crafting.requesttable.network.RequestTableNetworkEntry;
import logisticspipes.crafting.requesttable.settings.RequestTableDisplaySettings;
import logisticspipes.crafting.requesttable.upgrade.RequestTableStorageUpgradeConfig;
import logisticspipes.crafting.requesttable.upgrade.RequestTableUpgradeBranch;
import logisticspipes.crafting.requesttable.upgrade.RequestTableUpgradeMaterials.Requirement;
import logisticspipes.crafting.requesttable.upgrade.RequestTableUpgradeSession;
import logisticspipes.gui.modularUI.LogisticsPipeMUI;
import logisticspipes.request.resources.IResource;
import logisticspipes.utils.gui.GuiGraphics;
import logisticspipes.utils.string.StringUtils;
import lombok.Getter;

/**
 * ModularUI GUI of the Logistics Request Table Mk2.
 * <p>
 * One adaptive panel holds every view of the legacy GUI: the network terminal with its sidebar, the item and fluid
 * storage, the ghost crafting grid, the crafting monitor and the upgrade motherboard. Positions come from
 * {@link RequestTableLayout}, so both GUIs share their geometry. The legacy GUI stays reachable with the legacy wrench.
 */
public class RequestTableMui extends LogisticsPipeMUI {

    private static final int TEXT = 0x404040;
    private static final int MUTED = 0x686868;
    private static final String CRAFTING_GROUP = "request_table_crafting";
    private static final String MATERIAL_GROUP = "request_table_materials";

    // upgrade view geometry, matching the legacy upgrade screen
    private static final int UPGRADE_WIDTH = 286;
    private static final int UPGRADE_INVENTORY_LEFT = 62;
    private static final int UPGRADE_MATERIAL_SLOTS = RequestTableUpgradeSession.MATERIAL_SLOTS;
    private static final int UPGRADE_APPLY_LEFT = UPGRADE_INVENTORY_LEFT + UPGRADE_MATERIAL_SLOTS * 18 + 4;
    private static final int UPGRADE_REQUIREMENTS = 4;

    /** The terminal currently open on this client, for packets that are not part of its sync handler. */
    private static WeakReference<RequestTableMui> activeClient = new WeakReference<>(null);

    @Getter
    private final RequestTablePipe table;
    @Getter
    private RequestTableSyncHandler actions;
    private PanelSyncManager syncManager;
    private ModularPanel panel;

    @Getter
    private RequestTableView view = RequestTableView.NETWORK;
    private RequestTableView monitorReturnView = RequestTableView.NETWORK;
    private RequestTableLayout layout;

    private final StringValue searchText = new StringValue("");
    private TextFieldWidget search;
    private boolean searchInitialized;
    private boolean searchEdited;
    private boolean restoringSearch;
    private RequestTableNumberFieldWidget setsField;
    private RequestTableNetworkGridWidget networkGrid;
    private RequestTableStorageWidget storage;
    private RequestTableMonitorWidget monitor;
    private RequestTableUpgradeBoardWidget board;
    private RequestTableRequestDialog requestDialog;
    private int seenContentRevision = -1;

    public RequestTableMui(RequestTablePipe table) {
        super(table);
        this.table = table;
    }

    // region static client hooks

    /** @return the request table GUI open on this client, or {@code null} */
    public static RequestTableMui getActiveClient() {
        RequestTableMui gui = activeClient.get();
        return gui != null && gui.panel != null && gui.panel.isValid() && gui.panel.isOpen() ? gui : null;
    }

    /** Shows a request result popup; returns {@code false} if no request table GUI is open. */
    public static boolean handleRequestAnswer(Collection<IResource> items, boolean error, EntityPlayer player) {
        RequestTableMui gui = getActiveClient();
        if (gui == null) return false;
        RequestTableResultPopup.open(gui.panel, player, error ? "You are missing:" : "Request successful!", items);
        return true;
    }

    /** Forwards a crafting monitor snapshot chunk to the open GUI of that table. */
    public static void handleMonitorChunk(int x, int y, int z, long session, long transfer, int part, int parts,
            byte[] payload) {
        RequestTableMui gui = getActiveClient();
        if (gui != null && gui.isForTable(x, y, z)) gui.monitor.chunk(session, transfer, part, parts, payload);
    }

    private boolean isForTable(int x, int y, int z) {
        return table.container != null && table.container.xCoord == x
                && table.container.yCoord == y
                && table.container.zCoord == z;
    }

    // endregion

    @Override
    public String getId() {
        return "request_table";
    }

    @Override
    public int getWidth() {
        return RequestTableLayout.GUI_WIDTH;
    }

    @Override
    public int getHeight() {
        return RequestTableLayout.SMALL_HEIGHT;
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        // the panel is built in getPanel, it needs the sync manager and UI settings
        return widget;
    }

    @Override
    public ModularPanel getPanel(GuiData guiData, PanelSyncManager syncManager) {
        return getPanel(guiData, syncManager, null);
    }

    @Override
    public ModularPanel getPanel(GuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        this.syncManager = syncManager;
        EntityPlayer player = guiData.getPlayer();
        RequestTableUpgradeSession upgrades = new RequestTableUpgradeSession(player, table);
        actions = new RequestTableSyncHandler(table, upgrades);
        syncManager.syncValue("request_table", actions);
        syncManager.registerSlotGroup(new SlotGroup(CRAFTING_GROUP, 3, false));
        syncManager.registerSlotGroup(
                new SlotGroup(MATERIAL_GROUP, UPGRADE_MATERIAL_SLOTS, SlotGroup.STORAGE_SLOT_PRIO, true));
        syncManager.addCloseListener(closing -> {
            if (NetworkUtils.isClient(closing)) {
                onClientClosed();
            } else {
                actions.onClosed(closing);
            }
        });
        if (settings != null) {
            if (syncManager.isClient()) {
                settings.customContainer(RequestTableMuiClientContainer.supplier(this));
            } else {
                settings.customContainer(() -> new RequestTableMuiContainer(table, actions::getView));
            }
        }

        panel = new ModularPanel(getId());
        panel.width(() -> panelWidth(), Unit.Measure.PIXEL).height(() -> panelHeight(), Unit.Measure.PIXEL)
                .background(panelBackground()).disableHoverThemeBackground(true);
        monitor = new RequestTableMonitorWidget(this);
        panel.child(
                monitor.pos(0, 0).widthRel(1f).heightRel(1f)
                        .setEnabledIf(w -> view == RequestTableView.CRAFTING_MONITOR));
        addTabs();
        addSidebar();
        addHeader();
        addMainArea();
        addCraftingArea();
        addUpgradeView(upgrades);
        panel.child(
                at(SlotGroupWidget.playerInventory(false), () -> playerSlotLeft(), () -> playerSlotTop())
                        .setEnabledIf(w -> view != RequestTableView.CRAFTING_MONITOR));
        panel.onUpdateListener(p -> clientTick());
        panel.listenGuiAction((IGuiAction.MouseScroll) this::scrollList);
        if (syncManager.isClient()) activeClient = new WeakReference<>(this);
        return panel;
    }

    // region geometry

    private int screenWidth() {
        return panel != null && panel.isValid() ? panel.getScreen().getScreenArea().width
                : RequestTableLayout.GUI_WIDTH;
    }

    private int screenHeight() {
        return panel != null && panel.isValid() ? panel.getScreen().getScreenArea().height
                : RequestTableLayout.SMALL_HEIGHT;
    }

    int getScreenWidth() {
        return screenWidth();
    }

    private int panelWidth() {
        return switch (view) {
            case CRAFTING_MONITOR -> Math.min(416, Math.max(RequestTableLayout.GUI_WIDTH, screenWidth() - 16));
            case UPGRADES -> UPGRADE_WIDTH;
            default -> RequestTableLayout.GUI_WIDTH;
        };
    }

    private int panelHeight() {
        if (view == RequestTableView.UPGRADES) return Math.max(220, Math.min(420, screenHeight() - 40));
        return RequestTableLayout.getGuiHeight(screenHeight(), actions.getDisplaySettings().getTerminalStyle());
    }

    /** Layout of the terminal views in panel coordinates. */
    public RequestTableLayout getLayout() {
        int width = panelWidth();
        int height = panelHeight();
        if (layout == null || layout.xSize != width || layout.ySize != height) {
            layout = new RequestTableLayout(0, 0, width, height);
        }
        return layout;
    }

    private int upgradeDetailsTop() {
        return panelHeight() - 150;
    }

    private int playerSlotLeft() {
        return view == RequestTableView.UPGRADES ? UPGRADE_INVENTORY_LEFT - 1 : getLayout().playerLeft - 1;
    }

    private int playerSlotTop() {
        return view == RequestTableView.UPGRADES ? panelHeight() - 85 : getLayout().playerTop - 1;
    }

    private static <W extends IPositioned<?>> W at(W widget, IntSupplier x, IntSupplier y) {
        widget.left(() -> x.getAsInt(), Unit.Measure.PIXEL);
        widget.top(() -> y.getAsInt(), Unit.Measure.PIXEL);
        return widget;
    }

    private static <W extends IPositioned<?>> W sized(W widget, IntSupplier width, IntSupplier height) {
        widget.width(() -> width.getAsInt(), Unit.Measure.PIXEL);
        widget.height(() -> height.getAsInt(), Unit.Measure.PIXEL);
        return widget;
    }

    private boolean isTerminalView() {
        return view != RequestTableView.CRAFTING_MONITOR && view != RequestTableView.UPGRADES;
    }

    private boolean isStorageView() {
        return view == RequestTableView.ITEM_STORAGE || view == RequestTableView.FLUID_STORAGE;
    }

    // endregion

    // region drawing helpers shared by the popups

    /** A popup button with a centered, scaled label, like the legacy request popup. */
    static RequestTableButtonWidget popupButton(Supplier<String> label, BooleanSupplier active) {
        return new RequestTableButtonWidget().active(active).painter(
                (width, height, hover, enabled) -> RequestTableRender.textButton(
                        Minecraft.getMinecraft().fontRenderer,
                        label.get(),
                        0,
                        0,
                        width,
                        height,
                        enabled,
                        hover ? 1 : -1,
                        hover ? 1 : -1));
    }

    /** A raised text button, like the legacy table and upgrade screen buttons. */
    static RequestTableButtonWidget textButton(Supplier<String> label, BooleanSupplier active) {
        return new RequestTableButtonWidget().active(active).painter(
                (width, height, hover, enabled) -> RequestTableButton
                        .paint(Minecraft.getMinecraft(), label.get(), 0, 0, width, height, enabled, hover));
    }

    private static RequestTableButtonWidget iconButton(Supplier<Icon> icon, BooleanSupplier selected) {
        return new RequestTableButtonWidget().size(20, 20).painter((width, height, hover, enabled) -> {
            try (var ignored = RequestTableRender.guiState()) {
                RequestTableGuiStyle.raised(0, 0, width, height, enabled && hover);
                RequestTableIconButton.paintContent(
                        Minecraft.getMinecraft(),
                        0,
                        0,
                        width,
                        height,
                        icon.get(),
                        enabled,
                        enabled && hover,
                        selected.getAsBoolean(),
                        null,
                        -1,
                        0);
            }
        });
    }

    private IDrawable panelBackground() {
        return (context, x, y, width, height, widgetTheme) -> {
            Minecraft mc = Minecraft.getMinecraft();
            try (var ignored = RequestTableRender.guiState()) {
                if (view == RequestTableView.UPGRADES) {
                    GuiGraphics.drawGuiBackGround(mc, x, y, x + width, y + height, 0, true);
                } else {
                    RequestTableLayout layout = getLayout();
                    GuiGraphics.drawGuiBackGround(
                            mc,
                            x + layout.centralLeft,
                            y + layout.centralTop,
                            x + layout.centralLeft + layout.centralWidth,
                            y + height,
                            0,
                            true);
                }
            }
        };
    }

    // endregion

    // region tabs and sidebar

    private void addTabs() {
        addTab(RequestTableView.NETWORK, Icon.NETWORK, "Main", () -> getLayout().networkButtonX, -1, 0, () -> true)
                .lines(lines -> {
                    lines.add("Network");
                    lines.add(
                            table.isFluidEnabled() ? "Browse stored and craftable items and fluids."
                                    : "Browse stored and craftable items.");
                });
        addTab(
                RequestTableView.ITEM_STORAGE,
                Icon.ITEMS_ON,
                "Items",
                () -> getLayout().itemButtonX,
                0,
                0xffcf9d53,
                () -> true).lines(lines -> {
                    lines.add("Internal item storage");
                    lines.add("Used slots: " + usedItemSlots() + "/" + table.inv.getSizeInventory());
                    lines.add(table.inv.getInventoryStackLimit() + " items per slot");
                });
        addTab(
                RequestTableView.FLUID_STORAGE,
                Icon.FLUIDS_ON,
                "Fluids",
                () -> getLayout().fluidButtonX,
                1,
                0xff58a9cf,
                () -> table.isFluidEnabled()).lines(lines -> {
                    lines.add("Internal fluid storage");
                    lines.add("Used tanks: " + usedFluidTanks() + "/" + table.getFluidStorage().getSizeInventory());
                    lines.add(table.getFluidStorage().getSlotCapacity() + " mB per tank");
                });
        RequestTableButtonWidget monitorButton = new RequestTableButtonWidget()
                .active(() -> table.hasMonitoringUpgrade() && actions.isInventoryReady())
                .painter((width, height, hover, active) -> {
                    try (var ignored = RequestTableRender.guiState()) {
                        RequestTableGuiStyle.raised(0, 0, width, height, active && hover);
                        RequestTableMonitorButton
                                .paintContent(0, 0, width, height, active, view == RequestTableView.CRAFTING_MONITOR);
                    }
                })
                .onLeftClick(
                        () -> setView(
                                view == RequestTableView.CRAFTING_MONITOR ? monitorReturnView
                                        : RequestTableView.CRAFTING_MONITOR))
                .lines(lines -> {
                    lines.add("Crafting monitor");
                    if (!table.hasMonitoringUpgrade()) {
                        lines.add("Requires the monitoring upgrade.");
                        lines.add("Unlock it on the upgrade board.");
                    } else if (!actions.isInventoryReady()) {
                        lines.add("Updating upgrades...");
                    } else {
                        lines.add(
                                view == RequestTableView.CRAFTING_MONITOR ? "Close the crafting monitor view."
                                        : "Open the crafting monitor view.");
                    }
                });
        panel.child(
                sized(
                        at(monitorButton, () -> getLayout().monitorButtonX, () -> getLayout().monitorButtonY),
                        () -> getLayout().monitorButtonWidth,
                        () -> getLayout().monitorButtonHeight).setEnabledIf(w -> view != RequestTableView.UPGRADES));
    }

    /**
     * @param fluidFill -1 for no fill bar, 0 for item storage, 1 for fluid storage
     */
    private RequestTableButtonWidget addTab(RequestTableView target, Icon icon, String label, IntSupplier x,
            int fluidFill, int fillColor, BooleanSupplier visible) {
        int width = target == RequestTableView.NETWORK ? RequestTableLayout.MAIN_TAB_WIDTH
                : RequestTableLayout.STORAGE_TAB_WIDTH;
        RequestTableButtonWidget tab = new RequestTableButtonWidget()
                .active(
                        () -> target != RequestTableView.FLUID_STORAGE
                                || (table.isFluidEnabled() && actions.isInventoryReady()))
                .painter((w, h, hover, active) -> {
                    boolean selected = view == target;
                    // Inactive tabs stop at the panel, like the legacy tabs hidden behind it.
                    int height = selected ? h : getLayout().centralTop;
                    float fill = fluidFill < 0 ? -1
                            : fluidFill == 0 ? table.getItemStorageFillLevel() : table.getFluidStorageFillLevel();
                    try (var ignored = RequestTableRender.guiState()) {
                        RequestTableGuiStyle.raised(0, 0, w, height, active && hover);
                        RequestTableIconButton.paintContent(
                                Minecraft.getMinecraft(),
                                0,
                                0,
                                w,
                                height,
                                icon,
                                active,
                                active && hover,
                                selected,
                                label,
                                fill,
                                fillColor);
                    }
                }).onLeftClick(() -> setView(target));
        panel.child(
                at(tab, x, () -> 0).size(width, RequestTableLayout.TAB_HEIGHT)
                        .setEnabledIf(w -> view != RequestTableView.UPGRADES && visible.getAsBoolean()));
        return tab;
    }

    private void addSidebar() {
        BooleanSupplier network = () -> view == RequestTableView.NETWORK && actions.isSettingsReceived();
        Flow visibility = Flow.column().coverChildren().childPadding(RequestTableLayout.SIDEBAR_ROW_GAP)
                .collapseDisabledChild();
        visibility.child(iconButton(() -> switch (settings().getFilterMode()) {
            case STORED -> Icon.STORED;
            case CRAFTABLE -> Icon.CRAFTABLE;
            case BOTH -> Icon.BOTH;
        }, () -> false).onLeftClick(() -> applyDisplaySettings(settings().nextFilterMode())).lines(lines -> {
            lines.add("Show entries");
            lines.add(switch (settings().getFilterMode()) {
                case STORED -> "Stored";
                case CRAFTABLE -> "Craftable";
                case BOTH -> "Stored and craftable";
            });
        }).setEnabledIf(w -> network.getAsBoolean()));
        visibility.child(
                iconButton(
                        () -> settings().isShowItems() ? Icon.ITEMS_ON : Icon.ITEMS_OFF,
                        () -> settings().isShowItems())
                                .onLeftClick(() -> applyDisplaySettings(settings().toggleItems())).lines(lines -> {
                                    lines.add("Show items: " + (settings().isShowItems() ? "Yes" : "No"));
                                    lines.add("Toggle items in the Main list.");
                                }).setEnabledIf(w -> network.getAsBoolean()));
        visibility.child(
                iconButton(
                        () -> settings().isShowFluids() ? Icon.FLUIDS_ON : Icon.FLUIDS_OFF,
                        () -> settings().isShowFluids())
                                .onLeftClick(() -> applyDisplaySettings(settings().toggleFluids())).lines(lines -> {
                                    lines.add("Show fluids: " + (settings().isShowFluids() ? "Yes" : "No"));
                                    lines.add("Toggle fluids in the Main list.");
                                }).setEnabledIf(w -> network.getAsBoolean() && table.isFluidEnabled()));
        panel.child(at(visibility, () -> getLayout().visibilityButtonX, () -> getLayout().sortModeButtonY));

        Flow display = Flow.column().coverChildren().childPadding(RequestTableLayout.SIDEBAR_ROW_GAP)
                .collapseDisabledChild();
        display.child(
                iconButton(() -> Icon.MONITOR_GRID, () -> !monitor.isTree()).onLeftClick(() -> monitor.setTree(false))
                        .lines(lines -> {
                            lines.add("Grid view");
                            lines.add("Combines identical resources across all branches.");
                        }).setEnabledIf(w -> view == RequestTableView.CRAFTING_MONITOR));
        display.child(
                iconButton(() -> Icon.MONITOR_TREE, () -> monitor.isTree()).onLeftClick(() -> monitor.setTree(true))
                        .lines(lines -> {
                            lines.add("Tree view");
                            lines.add("Show crafting dependencies.");
                            lines.add("Drag to move. Mouse wheel changes zoom.");
                        }).setEnabledIf(w -> view == RequestTableView.CRAFTING_MONITOR));
        display.child(
                iconButton(() -> Icon.SEND, () -> false)
                        .onLeftClick(() -> actions.sendStorageToNetwork(view == RequestTableView.FLUID_STORAGE))
                        .lines(lines -> {
                            lines.add("Send all " + (view == RequestTableView.FLUID_STORAGE ? "fluids" : "items"));
                            lines.add("Return stored contents to the network.");
                        }).setEnabledIf(w -> isStorageView() && actions.isInventoryReady()));
        display.child(
                iconButton(
                        () -> settings().getSortMode() == RequestTableDisplaySettings.SortMode.NAME ? Icon.NAME
                                : Icon.AMOUNT,
                        () -> false).onLeftClick(() -> applyDisplaySettings(settings().nextSortMode())).lines(lines -> {
                            lines.add("Sort by");
                            lines.add(
                                    settings().getSortMode() == RequestTableDisplaySettings.SortMode.NAME ? "Item name"
                                            : "Item amount");
                        }).setEnabledIf(w -> network.getAsBoolean()));
        display.child(
                iconButton(
                        () -> settings().getSortDirection() == RequestTableDisplaySettings.SortDirection.ASCENDING
                                ? Icon.ASCENDING
                                : Icon.DESCENDING,
                        () -> false).onLeftClick(() -> applyDisplaySettings(settings().nextSortDirection()))
                                .lines(lines -> {
                                    lines.add("Sort direction");
                                    lines.add(
                                            settings().getSortDirection()
                                                    == RequestTableDisplaySettings.SortDirection.ASCENDING ? "Ascending"
                                                            : "Descending");
                                }).setEnabledIf(w -> network.getAsBoolean()));
        display.child(iconButton(() -> switch (settings().getSearchBoxMode()) {
            case AUTO -> Icon.SEARCH_AUTO;
            case NEI_SYNC_AUTO -> Icon.SEARCH_NEI_AUTO;
            case NEI_SYNC_STANDARD -> Icon.SEARCH_NEI_STANDARD;
            case STANDARD -> Icon.SEARCH_STANDARD;
        }, () -> false).onLeftClick(() -> applyDisplaySettings(settings().nextSearchBoxMode())).lines(lines -> {
            RequestTableDisplaySettings.SearchBoxMode mode = settings().getSearchBoxMode();
            lines.add("Search box mode: " + mode.getLabel());
            lines.add("Focus on opening: " + (mode.isAutoFocus() ? "Yes" : "No"));
            lines.add("Sync to NEI: " + (mode.isNeiSynced() ? "Yes (one way)" : "No"));
            lines.add("Click to cycle search mode.");
        }).setEnabledIf(w -> network.getAsBoolean()));
        display.child(
                iconButton(
                        () -> settings().isSaveSearch() ? Icon.SAVE_SEARCH_ON : Icon.SAVE_SEARCH_OFF,
                        () -> settings().isSaveSearch())
                                .onLeftClick(() -> applyDisplaySettings(settings().toggleSaveSearch())).lines(lines -> {
                                    lines.add("Save search text: " + (settings().isSaveSearch() ? "Yes" : "No"));
                                    lines.add("Remember the search when reopening this table.");
                                }).setEnabledIf(w -> network.getAsBoolean()));
        display.child(
                iconButton(() -> isTall() ? Icon.TERMINAL_TALL : Icon.TERMINAL_SMALL, this::isTall)
                        .onLeftClick(() -> applyDisplaySettings(settings().nextTerminalStyle())).lines(lines -> {
                            lines.add("Terminal size: " + (isTall() ? "Tall" : "Small"));
                            lines.add(
                                    isTall() ? "Use the available window height."
                                            : "Up to " + RequestTableLayout.SMALL_ROWS + " content rows.");
                        }).setEnabledIf(w -> actions.isSettingsReceived()));
        display.child(
                iconButton(
                        () -> settings().isRequestMessagesEnabled() ? Icon.MESSAGES_ON : Icon.MESSAGES_OFF,
                        () -> settings().isRequestMessagesEnabled())
                                .onLeftClick(() -> applyDisplaySettings(settings().toggleRequestMessages()))
                                .lines(lines -> {
                                    lines.add(
                                            "Request messages: "
                                                    + (settings().isRequestMessagesEnabled() ? "On" : "Off"));
                                    lines.add("Toggle request-result popups and chat.");
                                    lines.add("Items, fluids and crafting ingredients.");
                                }).setEnabledIf(w -> network.getAsBoolean()));
        display.child(
                iconButton(() -> Icon.UPGRADES, () -> false).onLeftClick(() -> setView(RequestTableView.UPGRADES))
                        .lines(lines -> {
                            lines.add("Table upgrades");
                            lines.add(
                                    "Item slots: "
                                            + RequestTableStorageUpgradeConfig.getTierName(table.getItemSlotTier()));
                            lines.add(
                                    "Item capacity: "
                                            + RequestTableStorageUpgradeConfig.getTierName(table.getItemSizeTier()));
                            lines.add(
                                    "Fluid tanks: "
                                            + RequestTableStorageUpgradeConfig.getTierName(table.getFluidSlotTier()));
                            lines.add(
                                    "Fluid capacity: "
                                            + RequestTableStorageUpgradeConfig.getTierName(table.getFluidSizeTier()));
                            lines.add("Consume components for permanent improvements.");
                        }).setEnabledIf(w -> actions.isInventoryReady()));
        panel.child(
                at(display, () -> getLayout().displayButtonX, () -> getLayout().sortModeButtonY)
                        .setEnabledIf(w -> view != RequestTableView.UPGRADES));
        visibility.setEnabledIf(w -> view != RequestTableView.UPGRADES);
    }

    private RequestTableDisplaySettings settings() {
        return actions.getDisplaySettings();
    }

    private boolean isTall() {
        return settings().getTerminalStyle() == RequestTableDisplaySettings.TerminalStyle.TALL;
    }

    // endregion

    // region header and main area

    private void addHeader() {
        search = new TextFieldWidget().value(new StringValue.Dynamic(searchText::getStringValue, this::onSearchEdited))
                .autoUpdateOnChange(true).setMaxLength(256).setTextColor(0xffe0e0e0);
        search.background((context, x, y, width, height, widgetTheme) -> {
            try (var ignored = RequestTableRender.guiState()) {
                RequestTableGuiStyle.inset(x, y, width, height);
                if (search.isFocused()) {
                    net.minecraft.client.gui.Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, 0xff555555);
                }
            }
        }).disableHoverThemeBackground(true).paddingLeft(3).paddingRight(3);
        panel.child(
                sized(
                        at(search, () -> getLayout().searchX, () -> getLayout().searchY),
                        () -> getLayout().searchWidth,
                        () -> getLayout().searchHeight).setEnabledIf(w -> view == RequestTableView.NETWORK));
        panel.child(at(new RequestTablePaintWidget((width, height) -> {
            RequestTableLayout layout = getLayout();
            RequestTableMuiDraw.text(
                    view == RequestTableView.ITEM_STORAGE ? "Item storage" : "Fluid storage",
                    0,
                    (layout.searchHeight - 8) / 2,
                    TEXT);
        }), () -> getLayout().searchX, () -> getLayout().searchY).size(120, 12).setEnabledIf(w -> isStorageView()));
    }

    private void addMainArea() {
        networkGrid = new RequestTableNetworkGridWidget(this, this::openRequest);
        panel.child(
                sized(
                        at(networkGrid, () -> getLayout().panelLeft, () -> getLayout().panelTop),
                        () -> getLayout().panelWidth,
                        () -> getLayout().getVisiblePanelRows() * RequestTableLayout.PANEL_CELL)
                                .setEnabledIf(w -> view == RequestTableView.NETWORK));
        storage = new RequestTableStorageWidget(this);
        panel.child(
                sized(
                        at(storage, () -> getLayout().panelLeft, () -> getLayout().panelTop),
                        () -> getLayout().panelWidth,
                        () -> getLayout().getVisibleStorageRows() * RequestTableLayout.SLOT)
                                .setEnabledIf(w -> isStorageView()));
        panel.child(
                sized(
                        at(
                                new RequestTableScrollbarWidget(this::activeList),
                                () -> getLayout().scrollbarX,
                                () -> getLayout().panelTop),
                        () -> 7,
                        () -> getLayout().panelHeight).setEnabledIf(w -> isTerminalView()));
    }

    private RequestTableScrollbarWidget.Scrollable activeList() {
        return switch (view) {
            case NETWORK -> networkGrid;
            case ITEM_STORAGE, FLUID_STORAGE -> storage;
            default -> null;
        };
    }

    private boolean scrollList(UpOrDown direction, int amount) {
        if (!isTerminalView() || panel.getScreen().getPanelManager().getTopMostPanel() != panel) return false;
        RequestTableScrollbarWidget.Scrollable list = activeList();
        if (list == null) return false;
        list.setScrollRow(list.getScrollRow() + (direction == UpOrDown.UP ? -1 : 1));
        return true;
    }

    // endregion

    // region crafting area

    private void addCraftingArea() {
        panel.child(
                at(new RequestTablePaintWidget((width, height) -> paintCraftingDecor()), () -> 0, () -> 0).widthRel(1f)
                        .heightRel(1f).setEnabledIf(w -> isTerminalView()));
        InvWrapper matrix = new InvWrapper(table.matrix);
        for (int i = 0; i < table.matrix.getSizeInventory(); i++) {
            int column = i % 3;
            int row = i / 3;
            ItemSlot ghost = new PhantomItemSlot().slot(new ModularSlot(matrix, i).slotGroup(CRAFTING_GROUP));
            panel.child(
                    at(
                            ghost,
                            () -> getLayout().craftingLeft - 1 + column * RequestTableLayout.SLOT,
                            () -> getLayout().getCraftingGridTop() - 1 + row * RequestTableLayout.SLOT)
                                    .setEnabledIf(w -> isTerminalView()));
        }
        panel.child(
                at(
                        new RequestTableResultWidget(this),
                        () -> getLayout().craftingResultX - 5,
                        () -> getLayout().craftingResultY - 5).setEnabledIf(w -> isTerminalView()));
        panel.child(
                at(
                        iconButtonSized(() -> Icon.CLEAR).active(this::hasCraftingInputs)
                                .onLeftClick(actions::clearCraftingGrid)
                                .lines(lines -> lines.add("Clear crafting grid")),
                        () -> getLayout().craftingClearX,
                        () -> getLayout().craftingClearY).size(10, 10).setEnabledIf(w -> isTerminalView()));
        setsField = new RequestTableNumberFieldWidget("1", 1).onEnter(this::requestIngredients)
                .onEscape(() -> setsField.getContext().removeFocus());
        panel.child(
                sized(
                        at(setsField, () -> getLayout().craftingAmountX, () -> getLayout().craftingAmountY),
                        () -> getLayout().craftingAmountWidth,
                        () -> getLayout().craftingAmountHeight).setEnabledIf(w -> isTerminalView()));
        panel.child(
                at(
                        iconButtonSized(() -> Icon.REQUEST)
                                .active(() -> actions.isInventoryReady() && hasCraftingInputs())
                                .onLeftClick(this::requestIngredients).lines(lines -> {
                                    lines.add("Request ingredient sets");
                                    lines.add(setsField.getAmount() + " craft sets");
                                    lines.add("Request full sets without subtracting existing ingredients.");
                                    lines.add("Returned tools are requested once per tool slot.");
                                }),
                        () -> getLayout().craftingRequestX,
                        () -> getLayout().craftingRequestY).size(18, 18).setEnabledIf(w -> isTerminalView()));
        panel.child(
                at(
                        textButton(
                                () -> "All",
                                () -> actions.isInventoryReady() && actions.getNetworkCraftingSets() > 0)
                                        .onLeftClick(() -> {
                                            setsField.setValue(Integer.toString(actions.getNetworkCraftingSets()));
                                            if (setsField.isFocused()) setsField.getContext().removeFocus();
                                        }).lines(lines -> {
                                            lines.add("All network sets");
                                            lines.add(
                                                    actions.getNetworkCraftingSets()
                                                            + " complete sets from network stock.");
                                            lines.add("Returned tools are requested once per tool slot.");
                                            lines.add("Fill the amount, then request the ingredient sets.");
                                        }),
                        () -> getLayout().craftingAllX,
                        () -> getLayout().craftingAllY).size(26, 12).setEnabledIf(w -> isTerminalView()));
        panel.child(sized(at(new RequestTablePaintWidget((width, height) -> {
            int amount = actions.getCraftableAmount();
            RequestTableMuiDraw.text(
                    "Craftable: " + StringUtils.getFormatedStackSize(amount, true),
                    0,
                    0,
                    amount > 0 ? 0x37622e : MUTED);
        }).lines(lines -> {
            lines.add("Craftable items");
            lines.add(actions.getCraftableAmount() + " output items");
            lines.add("Table storage and your inventory.");
            lines.add("Before reusing returned containers or tools.");
        }), () -> getLayout().getCraftableLabelX(), () -> getLayout().getCraftableLabelY()),
                () -> getLayout().centralLeft + getLayout().centralWidth - getLayout().getCraftableLabelX(),
                () -> 10).setEnabledIf(w -> isTerminalView()));
    }

    private static RequestTableButtonWidget iconButtonSized(Supplier<Icon> icon) {
        return new RequestTableButtonWidget().painter((width, height, hover, enabled) -> {
            try (var ignored = RequestTableRender.guiState()) {
                RequestTableGuiStyle.raised(0, 0, width, height, enabled && hover);
                RequestTableIconButton.paintContent(
                        Minecraft.getMinecraft(),
                        0,
                        0,
                        width,
                        height,
                        icon.get(),
                        enabled,
                        enabled && hover,
                        false,
                        null,
                        -1,
                        0);
            }
        });
    }

    private void paintCraftingDecor() {
        RequestTableLayout layout = getLayout();
        try (var ignored = RequestTableRender.guiState()) {
            int arrowX = layout.craftingLeft + 54;
            int arrowY = layout.craftingResultY + 7;
            net.minecraft.client.gui.Gui.drawRect(arrowX, arrowY, arrowX + 10, arrowY + 3, 0xff707070);
            for (int i = 0; i < 5; i++) {
                net.minecraft.client.gui.Gui
                        .drawRect(arrowX + 8 + i, arrowY - 4 + i, arrowX + 9 + i, arrowY + 7 - i, 0xff707070);
            }
            Minecraft.getMinecraft().fontRenderer
                    .drawString("Sets", layout.craftingAmountX, layout.craftingAmountY - 12, TEXT);
            if (!layout.compact) {
                Minecraft.getMinecraft().fontRenderer
                        .drawString("Inventory", layout.playerLeft, layout.playerTop - 12, TEXT);
            }
        }
    }

    private boolean hasCraftingInputs() {
        for (int i = 0; i < table.matrix.getSizeInventory(); i++) {
            if (table.matrix.getStackInSlot(i) != null) return true;
        }
        return false;
    }

    private void requestIngredients() {
        if (!actions.isInventoryReady() || !hasCraftingInputs()) return;
        actions.requestIngredients(setsField.getAmount());
    }

    // endregion

    // region upgrade view

    private void addUpgradeView(RequestTableUpgradeSession upgrades) {
        BooleanSupplier upgradeView = () -> view == RequestTableView.UPGRADES;
        panel.child(
                new RequestTablePaintWidget((width, height) -> paintUpgradeDetails(upgrades)).pos(0, 0).widthRel(1f)
                        .heightRel(1f).setEnabledIf(w -> upgradeView.getAsBoolean()));
        board = new RequestTableUpgradeBoardWidget(this);
        panel.child(
                sized(at(board, () -> 8, () -> 24), () -> UPGRADE_WIDTH - 16, () -> upgradeDetailsTop() - 32)
                        .setEnabledIf(w -> upgradeView.getAsBoolean()));
        panel.child(
                textButton(() -> "Back", () -> true).pos(UPGRADE_WIDTH - 50, 5).size(42, 14)
                        .onLeftClick(() -> setView(RequestTableView.NETWORK))
                        .setEnabledIf(w -> upgradeView.getAsBoolean()));
        panel.child(
                textButton(() -> "Center", () -> true).pos(UPGRADE_WIDTH - 96, 5).size(40, 14)
                        .onLeftClick(() -> board.centerBoard()).lines(lines -> {
                            lines.add("Center on the base chip.");
                            lines.add("Drag the board to move in any direction.");
                            lines.add("Mouse wheel: move vertically.");
                            lines.add("Shift + mouse wheel: move horizontally.");
                        }).setEnabledIf(w -> upgradeView.getAsBoolean()));
        panel.child(
                textButton(() -> "-", () -> true).pos(116, 5).size(14, 14).onLeftClick(() -> board.changeZoom(false))
                        .lines(lines -> lines.add("Zoom out")).setEnabledIf(w -> upgradeView.getAsBoolean()));
        panel.child(
                textButton(() -> "+", () -> true).pos(134, 5).size(14, 14).onLeftClick(() -> board.changeZoom(true))
                        .lines(lines -> lines.add("Zoom in")).setEnabledIf(w -> upgradeView.getAsBoolean()));
        panel.child(
                textButton(() -> "All", () -> true).pos(152, 5).size(32, 14).onLeftClick(() -> board.showAll())
                        .lines(lines -> lines.add("Show the whole motherboard"))
                        .setEnabledIf(w -> upgradeView.getAsBoolean()));
        for (int i = 0; i < UPGRADE_REQUIREMENTS; i++) {
            int index = i;
            panel.child(at(new RequestTablePaintWidget((width, height) -> {}).lines(lines -> {
                List<Requirement> requirements = upgrades.getRequirements();
                if (index >= requirements.size() || isSelectedUpgradeApplied(upgrades)) return;
                Requirement requirement = requirements.get(index);
                lines.add(requirement.getCount() + "x " + requirement.getLabel());
                if (requirement.getOreName() != null) lines.add("Accepted: " + requirement.getOreName());
                lines.add("Inserted: " + upgrades.getMaterialCount(requirement) + "/" + requirement.getCount());
                lines.add("Insert materials into the slots below.");
                lines.add("Unused materials return when you leave this screen.");
            }), () -> 12 + index * 66, () -> upgradeDetailsTop() + 12).size(64, 16).setEnabledIf(
                    w -> upgradeView.getAsBoolean() && index < upgrades.getRequirements().size()
                            && !isSelectedUpgradeApplied(upgrades)));
        }
        InvWrapper materials = new InvWrapper(upgrades.getMaterials());
        for (int i = 0; i < UPGRADE_MATERIAL_SLOTS; i++) {
            int column = i;
            ModularSlot slot = new ModularSlot(materials, i).slotGroup(MATERIAL_GROUP)
                    // The server only knows the view its client reported.
                    .filter(stack -> actions.getView() == RequestTableView.UPGRADES && upgrades.matchesMaterial(stack));
            panel.child(
                    at(
                            new ItemSlot().slot(slot),
                            () -> UPGRADE_INVENTORY_LEFT - 1 + column * 18,
                            () -> upgradeDetailsTop() + 34).setEnabledIf(w -> upgradeView.getAsBoolean()));
        }
        panel.child(
                at(
                        textButton(
                                () -> isSelectedUpgradeApplied(upgrades) ? "Applied"
                                        : upgrades.getSelectedBranch().isSpecial() ? "Install" : "Apply",
                                () -> upgrades.isReady() && upgrades.canUpgrade())
                                        .onLeftClick(() -> actions.startUpgrade(false))
                                        .lines(lines -> applyTooltip(upgrades, lines)),
                        () -> UPGRADE_APPLY_LEFT,
                        () -> upgradeDetailsTop() + 34).size(46, 18).setEnabledIf(w -> upgradeView.getAsBoolean()));
        panel.child(
                at(
                        textButton(() -> "Free", () -> upgrades.isReady() && upgrades.canUpgradeCreative())
                                .onLeftClick(() -> actions.startUpgrade(true)).lines(lines -> {
                                    lines.add("Apply without materials (creative mode)");
                                    RequestTableUpgradeStatus status = selectedStatus(upgrades);
                                    lines.add(
                                            status == RequestTableUpgradeStatus.AVAILABLE
                                                    ? "Prerequisite upgrades still apply."
                                                    : status.hint(upgrades.getSelectedTier()));
                                }),
                        () -> UPGRADE_APPLY_LEFT,
                        () -> upgradeDetailsTop() + 54).size(46, 16).setEnabledIf(
                                w -> upgradeView.getAsBoolean()
                                        && Minecraft.getMinecraft().thePlayer.capabilities.isCreativeMode));
    }

    private static RequestTableUpgradeStatus selectedStatus(RequestTableUpgradeSession upgrades) {
        return RequestTableUpgradeStatus.of(upgrades, upgrades.getSelectedBranch(), upgrades.getSelectedTier());
    }

    private static boolean isSelectedUpgradeApplied(RequestTableUpgradeSession upgrades) {
        return selectedStatus(upgrades) == RequestTableUpgradeStatus.APPLIED;
    }

    private static String compact(int value) {
        if (value < 1000) return Integer.toString(value);
        double scaled = value >= 1_000_000 ? value / 1_000_000.0 : value / 1000.0;
        return String.format(Locale.ROOT, "%.1f", scaled).replace(".0", "") + (value >= 1_000_000 ? "M" : "k");
    }

    private void applyTooltip(RequestTableUpgradeSession upgrades, List<String> lines) {
        RequestTableUpgradeStatus status = selectedStatus(upgrades);
        if (status == RequestTableUpgradeStatus.APPLIED) {
            lines.add("Upgrade applied.");
            lines.add("Permanent, including after breaking and placing the table.");
            return;
        }
        lines.add(status.hint(upgrades.getSelectedTier()));
        lines.add("Permanent, including after breaking and placing the table.");
        RequestTableUpgradeBranch branch = upgrades.getSelectedBranch();
        if (!branch.isSpecial() && (status == RequestTableUpgradeStatus.PREVIOUS_REQUIRED
                || status == RequestTableUpgradeStatus.FLUID_CONTROLLER_REQUIRED)) {
            lines.add(
                    "Provides " + EnumChatFormatting.DARK_GREEN
                            + "+"
                            + upgrades.getConfig().getBonus(branch, upgrades.getSelectedTier())
                            + EnumChatFormatting.RESET
                            + " "
                            + branch.getUnit());
        }
        for (Requirement requirement : upgrades.getRequirements()) {
            lines.add(requirement.getCount() + "x " + requirement.getLabel());
        }
    }

    private void paintUpgradeDetails(RequestTableUpgradeSession upgrades) {
        Minecraft mc = Minecraft.getMinecraft();
        int detailsTop = upgradeDetailsTop();
        int height = panelHeight();
        RequestTableUpgradeBranch branch = upgrades.getSelectedBranch();
        int tier = upgrades.getSelectedTier();
        RequestTableStorageUpgradeConfig config = upgrades.getConfig();
        boolean applied = isSelectedUpgradeApplied(upgrades);
        String heading = branch.getLabel()
                + (branch.isSpecial() ? "" : " " + RequestTableStorageUpgradeConfig.getTierName(tier));
        int installed = table.getUpgradeTier(branch);
        String change = switch (selectedStatus(upgrades)) {
            case APPLIED -> branch.isSpecial() ? "Enabled"
                    : "Total: " + compact(config.getTotal(branch, installed)) + " " + branch.getUnit();
            case WAITING -> "Waiting for the server...";
            case PREVIOUS_REQUIRED -> "Install " + RequestTableStorageUpgradeConfig.getTierName(tier - 1)
                    + " first ("
                    + EnumChatFormatting.DARK_GREEN
                    + "+"
                    + compact(config.getBonus(branch, tier))
                    + EnumChatFormatting.RESET
                    + ")";
            case FLUID_CONTROLLER_REQUIRED -> "Install fluid controller first";
            case AVAILABLE -> branch.isSpecial() ? "Locked"
                    : compact(config.getTotal(branch, installed)) + " + "
                            + EnumChatFormatting.DARK_GREEN
                            + compact(config.getBonus(branch, tier))
                            + EnumChatFormatting.RESET
                            + " -> "
                            + compact(config.getTotal(branch, tier))
                            + " "
                            + branch.getUnit();
        };
        try (var ignored = RequestTableRender.guiState()) {
            mc.fontRenderer.drawString("Table upgrades", 12, 8, TEXT);
            int changeWidth = mc.fontRenderer.getStringWidth(change);
            // Long fluid formulas still leave a gap to the left-aligned upgrade name.
            int headingWidth = UPGRADE_WIDTH - 24 - changeWidth - 6;
            if (mc.fontRenderer.getStringWidth(heading) > headingWidth) {
                heading = mc.fontRenderer
                        .trimStringToWidth(heading, Math.max(0, headingWidth - mc.fontRenderer.getStringWidth("...")))
                        + "...";
            }
            mc.fontRenderer.drawString(heading, 12, detailsTop, TEXT);
            mc.fontRenderer.drawString(change, UPGRADE_WIDTH - 12 - changeWidth, detailsTop, MUTED);
            mc.fontRenderer.drawString("Inventory", UPGRADE_INVENTORY_LEFT, height - 84 - 11, TEXT);
            if (applied) {
                String contribution = switch (branch) {
                    case FLUID_CONTROLLER -> "Enables fluid storage, requests and cells.";
                    case CRAFTING_MONITORING -> "Enables crafting monitoring.";
                    default -> "Provides " + EnumChatFormatting.DARK_GREEN
                            + "+"
                            + compact(config.getBonus(branch, tier))
                            + EnumChatFormatting.RESET
                            + " "
                            + branch.getUnit();
                };
                mc.fontRenderer.drawString(contribution, 12, detailsTop + 16, TEXT);
                return;
            }
        }
        List<Requirement> requirements = upgrades.getRequirements();
        for (int i = 0; i < requirements.size(); i++) {
            Requirement requirement = requirements.get(i);
            int x = 12 + i * 66;
            ItemStack example = requirement.getExample();
            if (example != null) {
                RequestTableRender.item(example, x, detailsTop + 12, 100.0F, false);
            } else {
                RequestTableMuiDraw.text("?", x + 5, detailsTop + 16, MUTED);
            }
            int inserted = upgrades.getMaterialCount(requirement);
            RequestTableMuiDraw.text(
                    inserted + "/" + requirement.getCount(),
                    x + 19,
                    detailsTop + 16,
                    inserted >= requirement.getCount() ? 0x37622e : MUTED);
        }
    }

    // endregion

    // region state

    String getSearchText() {
        String text = searchText.getStringValue();
        return text == null ? "" : text;
    }

    private void onSearchEdited(String text) {
        String old = getSearchText();
        searchText.setStringValue(text == null ? "" : text);
        if (old.equals(getSearchText()) || restoringSearch) return;
        searchEdited = true;
        syncNeiSearch();
    }

    private void setSearch(String text) {
        restoringSearch = true;
        searchText.setStringValue(text);
        search.setText(text);
        restoringSearch = false;
    }

    private void focusSearch(boolean focused) {
        if (!search.isValid()) return;
        if (focused && view == RequestTableView.NETWORK) {
            RequestTableMuiClient.unfocusNeiSearch();
            search.getContext().focus(search);
        } else if (search.isFocused()) {
            search.getContext().removeFocus();
        }
    }

    /** Only terminal edits are sent to NEI; changes made in NEI never replace terminal text. */
    private void syncNeiSearch() {
        if (settings().getSearchBoxMode().isNeiSynced()) RequestTableMuiClient.setNeiSearch(getSearchText());
    }

    /** Applies new display settings locally and saves them on the server. */
    void applyDisplaySettings(RequestTableDisplaySettings newSettings) {
        RequestTableDisplaySettings old = settings();
        boolean styleChanged = old.getTerminalStyle() != newSettings.getTerminalStyle();
        boolean searchModeChanged = old.getSearchBoxMode() != newSettings.getSearchBoxMode();
        actions.setDisplaySettings(newSettings.rememberSearch(getSearchText()));
        if (styleChanged) panel.scheduleResize();
        if (searchModeChanged) {
            focusSearch(settings().getSearchBoxMode().isAutoFocus());
            syncNeiSearch();
        }
    }

    private void setView(RequestTableView newView) {
        if (view == newView) return;
        if (newView == RequestTableView.CRAFTING_MONITOR
                && (!actions.isInventoryReady() || !table.hasMonitoringUpgrade()))
            return;
        if (newView == RequestTableView.FLUID_STORAGE && (!actions.isInventoryReady() || !table.isFluidEnabled()))
            return;
        if (newView == RequestTableView.CRAFTING_MONITOR) monitorReturnView = view;
        if (view == RequestTableView.CRAFTING_MONITOR) monitor.leave();
        if (view == RequestTableView.UPGRADES) board.cancelDrag();
        view = newView;
        if (requestDialog != null) requestDialog.close();
        if (setsField.isValid() && setsField.isFocused()) setsField.getContext().removeFocus();
        if (view != RequestTableView.NETWORK && search.isValid() && search.isFocused()) {
            search.getContext().removeFocus();
        }
        actions.setView(view);
        if (view == RequestTableView.NETWORK) actions.refresh();
        panel.scheduleResize();
        if (view == RequestTableView.CRAFTING_MONITOR) {
            monitor.enter(syncManager.getContainer().windowId);
        }
    }

    private void openRequest(RequestTableNetworkEntry entry) {
        if (requestDialog == null) requestDialog = new RequestTableRequestDialog(this, panel);
        requestDialog.open(entry);
    }

    /** Opens the ore dictionary candidate picker for an NEI recipe. */
    public void openCandidates(ItemStack[][] stacks) {
        RequestTableCandidateDialog.open(this, panel, stacks);
    }

    /** Client-side checks the legacy GUI ran every frame. */
    private void clientTick() {
        if (!syncManager.isClient()) return;
        if (actions.getContentRevision() != seenContentRevision) {
            seenContentRevision = actions.getContentRevision();
            if (requestDialog != null) requestDialog.updateEntry(actions.getEntries());
            initializeSearch();
        }
        if (actions.isInventoryReady()) {
            if (!table.isFluidEnabled()) {
                if (view == RequestTableView.FLUID_STORAGE) setView(RequestTableView.NETWORK);
                if (requestDialog != null && requestDialog.isOpen() && requestDialog.getEntry().fluid()) {
                    requestDialog.close();
                }
            }
            if (view == RequestTableView.CRAFTING_MONITOR && !table.hasMonitoringUpgrade()) {
                setView(RequestTableView.NETWORK);
            }
        }
        int width = panelWidth();
        int height = panelHeight();
        if (layout != null && (layout.xSize != width || layout.ySize != height)) panel.scheduleResize();
    }

    /** Restores the saved search and the search focus once the first settings arrived. */
    private void initializeSearch() {
        if (searchInitialized || !actions.isSettingsReceived() || !search.isValid()) return;
        searchInitialized = true;
        if (!searchEdited) {
            setSearch(settings().isSaveSearch() ? settings().getSavedSearchText() : "");
        }
        if (!search.isFocused()) focusSearch(settings().getSearchBoxMode().isAutoFocus());
        syncNeiSearch();
    }

    private void onClientClosed() {
        monitor.leave();
        board.cancelDrag();
        if (actions.isSettingsReceived()) {
            RequestTableDisplaySettings saved = settings().rememberSearch(getSearchText());
            if (!saved.equals(settings())) actions.setDisplaySettings(saved);
        }
        if (activeClient.get() == this) activeClient = new WeakReference<>(null);
    }

    private int usedItemSlots() {
        int count = 0;
        for (int slot = 0; slot < table.inv.getSizeInventory(); slot++) {
            ItemStack stack = table.inv.getStackInSlot(slot);
            if (stack != null && stack.stackSize > 0) count++;
        }
        return count;
    }

    private int usedFluidTanks() {
        int count = 0;
        for (int tank = 0; tank < table.getFluidStorage().getSizeInventory(); tank++) {
            var fluid = table.getFluidStorage().getFluid(tank);
            if (fluid != null && fluid.amount > 0) count++;
        }
        return count;
    }

    // endregion
}
