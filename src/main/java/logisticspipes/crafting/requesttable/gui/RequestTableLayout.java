package logisticspipes.crafting.requesttable.gui;

import logisticspipes.crafting.requesttable.settings.RequestTableDisplaySettings;

/**
 * Pixel coordinates for the adaptive request table GUI.
 */
public class RequestTableLayout {

    public static final int GUI_WIDTH = 286;
    public static final int SLOT = 18;
    public static final int PANEL_CELL = 18;
    public static final int INVENTORY_COLUMNS = 9;
    public static final int TAB_HEIGHT = 22;
    public static final int MAIN_TAB_WIDTH = 56;
    public static final int STORAGE_TAB_WIDTH = 64;
    public static final int SMALL_ROWS = 4;
    public static final int SMALL_HEIGHT = 200 + SMALL_ROWS * SLOT;
    public static final int NEI_TOP_MARGIN = 20;
    public static final int NEI_BOTTOM_MARGIN = 20;
    public static final int TALL_HEIGHT_TRIM = 8;
    public static final int CONTENT_CRAFTING_GAP = 5;
    public static final int SIDEBAR_COLUMN_GAP = 2;
    public static final int SIDEBAR_ROW_GAP = 3;
    public static final int SIDEBAR_GUI_GAP = 7;
    private static final int MIN_GUI_HEIGHT = 198;
    private static final int TIGHT_HEIGHT = 216;
    private static final int COMPACT_HEIGHT = 242;

    private static final int PLAYER_HEIGHT = 76;
    private static final int BOTTOM_MARGIN = 8;

    public final int guiLeft;
    public final int guiTop;
    public final int xSize;
    public final int ySize;

    public final int searchX;
    public final int searchY;
    public final int searchWidth;
    public final int searchHeight;
    public final int sendButtonX;
    public final int sendButtonY;
    public final int sendButtonWidth;
    public final int sendButtonHeight;
    public final int itemButtonX;
    public final int fluidButtonX;
    public final int displayButtonX;
    public final int visibilityButtonX;
    public final int sortModeButtonY;
    public final int sortDirectionButtonY;
    public final int filterModeButtonY;
    public final int requestMessagesButtonY;
    public final int displayButtonWidth;
    public final int displayButtonHeight;
    public final int networkButtonY;
    public final int networkButtonX;
    public final int itemButtonY;
    public final int fluidButtonY;
    public final int centralLeft;
    public final int centralWidth;
    public final int centralTop;
    public final int monitorButtonX;
    public final int monitorButtonY;
    public final int monitorButtonWidth;
    public final int monitorButtonHeight;
    public final boolean compact;
    public final boolean tight;

    public final int panelLeft;
    public final int panelTop;
    public final int panelWidth;
    public final int panelHeight;
    public final int scrollbarX;

    public final int craftingLeft;
    public final int craftingTop;
    public final int craftingResultX;
    public final int craftingResultY;
    public final int craftingClearX;
    public final int craftingClearY;
    public final int craftingClearSize;
    public final int craftingAmountX;
    public final int craftingAmountY;
    public final int craftingAmountWidth;
    public final int craftingAmountHeight;
    public final int craftingRequestX;
    public final int craftingRequestY;
    public final int craftingRequestWidth;
    public final int craftingRequestHeight;
    public final int craftingAllX;
    public final int craftingAllY;
    public final int craftingAllWidth;
    public final int craftingAllHeight;

    public final int playerLeft;
    public final int playerTop;

    /**
     * Calculates all GUI coordinates from the current screen bounds.
     */
    public RequestTableLayout(int guiLeft, int guiTop, int xSize, int ySize) {
        this.guiLeft = guiLeft;
        this.guiTop = guiTop;
        this.xSize = xSize;
        this.ySize = ySize;

        compact = ySize < 266;
        tight = ySize < 226;
        displayButtonWidth = 20;
        displayButtonHeight = 20;
        visibilityButtonX = guiLeft + 7;
        displayButtonX = visibilityButtonX + displayButtonWidth + SIDEBAR_COLUMN_GAP;
        centralLeft = displayButtonX + displayButtonWidth + SIDEBAR_GUI_GAP;
        centralWidth = 190 + Math.max(0, xSize - GUI_WIDTH);
        centralTop = guiTop + TAB_HEIGHT - 4;
        networkButtonX = centralLeft + 1;
        itemButtonX = networkButtonX + MAIN_TAB_WIDTH + 2;
        fluidButtonX = itemButtonX + STORAGE_TAB_WIDTH + 2;
        networkButtonY = guiTop;
        itemButtonY = guiTop;
        fluidButtonY = guiTop;

        sortModeButtonY = centralTop + 8;
        sortDirectionButtonY = sortModeButtonY + displayButtonHeight + SIDEBAR_ROW_GAP;
        filterModeButtonY = sortModeButtonY;
        requestMessagesButtonY = sortModeButtonY + 5 * (displayButtonHeight + SIDEBAR_ROW_GAP);

        searchHeight = 12;
        int headerMargin = tight ? 1 : compact ? 2 : 4;
        searchY = guiTop + TAB_HEIGHT + headerMargin;

        sendButtonHeight = 20;
        sendButtonWidth = 20;
        sendButtonX = displayButtonX;
        sendButtonY = sortModeButtonY;

        playerLeft = centralLeft + 14;
        playerTop = guiTop + ySize - PLAYER_HEIGHT - (tight ? 6 : BOTTOM_MARGIN);
        panelLeft = playerLeft - 1;
        panelWidth = INVENTORY_COLUMNS * SLOT;

        craftingTop = playerTop - (tight ? 56 : compact ? 60 : 68);
        craftingLeft = playerLeft;
        craftingResultX = centralLeft + 88;
        craftingResultY = craftingTop + 19;
        craftingClearSize = 10;
        craftingClearX = centralLeft + 72;
        craftingClearY = craftingTop - 1;
        craftingAmountWidth = 34;
        craftingAmountHeight = 16;
        craftingAmountY = craftingTop + 17;
        craftingRequestWidth = 18;
        craftingRequestHeight = 18;
        craftingRequestX = panelLeft + panelWidth - craftingRequestWidth;
        craftingRequestY = craftingAmountY - 1;
        craftingAmountX = craftingRequestX - 4 - craftingAmountWidth;
        craftingAllWidth = 26;
        craftingAllHeight = 12;
        craftingAllX = craftingRequestX + craftingRequestWidth - craftingAllWidth;
        craftingAllY = craftingAmountY - 14;

        panelTop = searchY + searchHeight + headerMargin;
        searchX = panelLeft;
        searchWidth = panelWidth;
        panelHeight = Math
                .max(PANEL_CELL, (craftingTop - 1 - panelTop - CONTENT_CRAFTING_GAP) / PANEL_CELL * PANEL_CELL);
        scrollbarX = panelLeft + panelWidth + 3;

        monitorButtonX = centralLeft + centralWidth + 6;
        monitorButtonY = guiTop;
        monitorButtonWidth = 28;
        monitorButtonHeight = 20;
    }

    /** Leaves room for NEI's top controls and bottom search bar in both terminal styles. */
    public static int getGuiHeight(int screenHeight, RequestTableDisplaySettings.TerminalStyle style) {
        int available = Math.max(MIN_GUI_HEIGHT, screenHeight - NEI_TOP_MARGIN - NEI_BOTTOM_MARGIN);
        int target = style == RequestTableDisplaySettings.TerminalStyle.SMALL ? Math.min(SMALL_HEIGHT, available)
                : Math.max(MIN_GUI_HEIGHT, available - TALL_HEIGHT_TRIM);
        // Whole rows keep the crafting gap at five pixels in each spacing mode.
        if (target >= SMALL_HEIGHT) {
            return SMALL_HEIGHT + (target - SMALL_HEIGHT) / SLOT * SLOT;
        }
        if (target >= COMPACT_HEIGHT) {
            return COMPACT_HEIGHT + (target - COMPACT_HEIGHT) / SLOT * SLOT;
        }
        return target >= TIGHT_HEIGHT ? TIGHT_HEIGHT : MIN_GUI_HEIGHT;
    }

    /**
     * @return number of complete network rows that fit into the adaptive panel
     */
    public int getVisiblePanelRows() {
        return Math.max(1, panelHeight / PANEL_CELL);
    }

    /**
     * @return number of item cells per row in the network panel
     */
    public int getNetworkColumns() {
        return INVENTORY_COLUMNS;
    }

    /**
     * @return number of complete storage rows that fit into the adaptive panel
     */
    public int getVisibleStorageRows() {
        return Math.max(1, panelHeight / SLOT);
    }

    public int getCraftingGridTop() {
        return craftingTop;
    }

    public int getCraftableLabelY() {
        return craftingTop + 45;
    }

    public int getCraftableLabelX() {
        return centralLeft + 80;
    }

}
