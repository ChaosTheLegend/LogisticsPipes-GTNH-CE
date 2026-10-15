package logisticspipes.crafting.requesttable;

/**
 * Pixel coordinates for the adaptive request table GUI.
 */
public class RequestTableLayout {

    public static final int SLOT = 18;
    public static final int PANEL_CELL = 20;

    private static final int HEADER_HEIGHT = 30;
    private static final int PLAYER_HEIGHT = 76;
    private static final int CRAFTING_HEIGHT = 68;
    private static final int BOTTOM_MARGIN = 8;

    public final int guiLeft;
    public final int guiTop;
    public final int xSize;
    public final int ySize;

    public final int titleX;
    public final int titleY;
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
    public final int storageButtonY;
    public final int storageButtonSize;
    public final int displayButtonX;
    public final int sortModeButtonY;
    public final int sortDirectionButtonY;
    public final int filterModeButtonY;
    public final int displayButtonWidth;
    public final int displayButtonHeight;

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

        titleX = guiLeft + 12;
        titleY = guiTop + 9;
        storageButtonSize = 20;
        storageButtonY = guiTop + 5;
        fluidButtonX = guiLeft + xSize - 28;
        itemButtonX = fluidButtonX - storageButtonSize - 4;

        displayButtonWidth = 44;
        displayButtonHeight = 18;
        displayButtonX = Math.max(2, guiLeft - displayButtonWidth - 2);
        sortModeButtonY = guiTop + HEADER_HEIGHT;
        sortDirectionButtonY = sortModeButtonY + displayButtonHeight + 3;
        filterModeButtonY = sortDirectionButtonY + displayButtonHeight + 3;

        searchHeight = 14;
        searchY = guiTop + 8;
        searchX = guiLeft + 92;
        searchWidth = Math.max(60, itemButtonX - searchX - 10);

        sendButtonHeight = 16;
        sendButtonWidth = 70;
        sendButtonX = itemButtonX - sendButtonWidth - 10;
        sendButtonY = guiTop + 7;

        playerLeft = guiLeft + (xSize - 162) / 2;
        playerTop = guiTop + ySize - PLAYER_HEIGHT - BOTTOM_MARGIN;

        craftingTop = playerTop - CRAFTING_HEIGHT - 6;
        craftingLeft = guiLeft + 8;
        craftingResultX = craftingLeft + 94;
        craftingResultY = craftingTop + 25;
        craftingClearSize = 10;
        craftingClearX = craftingLeft + SLOT * 3 + 2;
        craftingClearY = craftingTop + 3;
        craftingAmountWidth = 34;
        craftingAmountHeight = 14;
        craftingAmountX = craftingResultX + SLOT + 6;
        craftingAmountY = craftingResultY + 2;
        craftingRequestWidth = 34;
        craftingRequestHeight = 16;
        craftingRequestX = craftingAmountX + craftingAmountWidth + 4;
        craftingRequestY = craftingResultY + 1;

        panelLeft = guiLeft + 10;
        panelTop = guiTop + HEADER_HEIGHT;
        panelWidth = xSize - 20;
        panelHeight = Math.max(PANEL_CELL, craftingTop - panelTop - 6);
        scrollbarX = panelLeft + panelWidth - 7;
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
        return Math.max(1, (panelWidth - 9) / PANEL_CELL);
    }

    /**
     * @return number of complete storage rows that fit into the adaptive panel
     */
    public int getVisibleStorageRows() {
        return Math.max(1, (panelHeight - 3) / SLOT);
    }
}
