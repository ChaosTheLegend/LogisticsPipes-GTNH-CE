package logisticspipes.crafting.requesttable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.gui.popup.GuiRequestPopup;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.crafting.requesttable.RequestTableClearCraftingPacket;
import logisticspipes.network.packets.crafting.requesttable.RequestTableDisplaySettingsPacket;
import logisticspipes.network.packets.crafting.requesttable.RequestTableNetworkInteractPacket;
import logisticspipes.network.packets.crafting.requesttable.RequestTableRefreshPacket;
import logisticspipes.network.packets.crafting.requesttable.RequestTableRequestIngredientsPacket;
import logisticspipes.network.packets.crafting.requesttable.RequestTableSendStoragePacket;
import logisticspipes.network.packets.crafting.requesttable.RequestTableSubmitPacket;
import logisticspipes.proxy.MainProxy;
import logisticspipes.request.resources.IResource;
import logisticspipes.utils.Color;
import logisticspipes.utils.gui.GuiGraphics;
import logisticspipes.utils.gui.GuiSearchBar;
import logisticspipes.utils.gui.ISubGuiControler;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;

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

    private final RequestTablePipe table;
    private final EntityPlayer player;
    private final RequestTableContainer container;
    private final RequestTableNetworkGrid networkGrid = new RequestTableNetworkGrid();
    private final RequestTableRequestOverlay requestOverlay = new RequestTableRequestOverlay();

    private RequestTableLayout layout;
    private GuiSearchBar search;
    private GuiTextField ingredientAmountField;
    private GuiButton sendButton;
    private GuiButton requestIngredientsButton;
    private GuiButton sortModeButton;
    private GuiButton sortDirectionButton;
    private GuiButton filterModeButton;
    private RequestTableView view = RequestTableView.NETWORK;
    private RequestTableDisplaySettings displaySettings = RequestTableDisplaySettings.DEFAULT;
    private int storageScrollRow;
    private final int dimension;

    /**
     * Creates the client GUI for the new request table.
     */
    public RequestTableGui(EntityPlayer player, RequestTablePipe table) {
        super(new RequestTableContainer(player, table), 380, 240, 0, 0);
        this.player = player;
        this.table = table;
        this.container = (RequestTableContainer) inventorySlots;
        dimension = MainProxy.getDimensionForWorld(table.getWorld());
        refreshNetwork();
    }

    @Override
    public void initGui() {
        xSize = 210;
        ySize = Math.max(214, Math.min(330, height - 16));
        super.initGui();
        updateLayout();
        buttonList.clear();
        sendButton = new GuiButton(
                SEND_BUTTON,
                layout.sendButtonX,
                layout.sendButtonY,
                layout.sendButtonWidth,
                layout.sendButtonHeight,
                "Send all");
        buttonList.add(sendButton);
        requestIngredientsButton = new GuiButton(
                REQUEST_INGREDIENTS_BUTTON,
                layout.craftingRequestX,
                layout.craftingRequestY,
                layout.craftingRequestWidth,
                layout.craftingRequestHeight,
                "Req");
        buttonList.add(requestIngredientsButton);
        sortModeButton = new GuiButton(
                SORT_MODE_BUTTON,
                layout.displayButtonX,
                layout.sortModeButtonY,
                layout.displayButtonWidth,
                layout.displayButtonHeight,
                "");
        buttonList.add(sortModeButton);
        sortDirectionButton = new GuiButton(
                SORT_DIRECTION_BUTTON,
                layout.displayButtonX,
                layout.sortDirectionButtonY,
                layout.displayButtonWidth,
                layout.displayButtonHeight,
                "");
        buttonList.add(sortDirectionButton);
        filterModeButton = new GuiButton(
                FILTER_MODE_BUTTON,
                layout.displayButtonX,
                layout.filterModeButtonY,
                layout.displayButtonWidth,
                layout.displayButtonHeight,
                "");
        buttonList.add(filterModeButton);
        updateDisplayButtons();
        if (search == null) {
            search = new GuiSearchBar("new_request_table_search");
        }
        search.reposition(layout.searchX, layout.searchY, layout.searchWidth, layout.searchHeight);
        initIngredientAmountField();
        updateContainerLayout();
    }

    /**
     * Receives the server-built network list.
     */
    public void handleNetworkContent(List<RequestTableNetworkEntry> entries,
            RequestTableDisplaySettings displaySettings) {
        this.displaySettings = displaySettings;
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

    /**
     * @return backing pipe for integration points such as NEI recipe overlays
     */
    public RequestTablePipe getTable() {
        return table;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        updateLayout();
        updateContainerLayout();
        sendButton.visible = view != RequestTableView.NETWORK;
        sendButton.xPosition = layout.sendButtonX;
        sendButton.yPosition = layout.sendButtonY;
        sendButton.width = layout.sendButtonWidth;
        sendButton.height = layout.sendButtonHeight;
        requestIngredientsButton.xPosition = layout.craftingRequestX;
        requestIngredientsButton.yPosition = layout.craftingRequestY;
        requestIngredientsButton.width = layout.craftingRequestWidth;
        requestIngredientsButton.height = layout.craftingRequestHeight;
        requestIngredientsButton.visible = true;
        requestIngredientsButton.enabled = true;
        updateDisplayButtonLayout();

        GuiGraphics.drawGuiBackGround(mc, guiLeft, guiTop, right, bottom, zLevel, true);
        mc.fontRenderer.drawString("Request Table", layout.titleX, layout.titleY, 0x404040);
        drawHeader(mouseX, mouseY);
        drawMainPanel(mouseX, mouseY);
        drawCraftingArea();
        GuiGraphics.drawPlayerInventoryBackground(mc, layout.playerLeft, layout.playerTop);
    }

    private void drawHeader(int mouseX, int mouseY) {
        if (view == RequestTableView.NETWORK) {
            search.reposition(layout.searchX, layout.searchY, layout.searchWidth, layout.searchHeight);
            search.renderSearchBar();
        }
        drawStorageButton(
                layout.itemButtonX,
                layout.storageButtonY,
                table.getItemStorageFillLevel(),
                view == RequestTableView.ITEM_STORAGE,
                false);
        drawStorageButton(
                layout.fluidButtonX,
                layout.storageButtonY,
                table.getFluidStorageFillLevel(),
                view == RequestTableView.FLUID_STORAGE,
                true);
    }

    private void drawMainPanel(int mouseX, int mouseY) {
        if (view == RequestTableView.NETWORK) {
            networkGrid.setSearch(getSearchText());
            networkGrid.render(this, layout, mouseX, mouseY);
            return;
        }
        drawStoragePanel();
    }

    private void drawStoragePanel() {
        drawRect(
                layout.panelLeft,
                layout.panelTop,
                layout.panelLeft + layout.panelWidth,
                layout.panelTop + layout.panelHeight,
                Color.GREY);
        int columns = 9;
        int size = view == RequestTableView.ITEM_STORAGE ? table.inv.getSizeInventory()
                : table.getFluidStorage().getSizeInventory();
        int rows = (size + columns - 1) / columns;
        int visibleRows = layout.getVisibleStorageRows();
        int maxScroll = Math.max(0, rows - visibleRows);
        int storageTop = layout.panelTop + 3;
        int storageBottom = storageTop + visibleRows * RequestTableLayout.SLOT;
        drawStorageScrollbar(maxScroll);
        for (int i = 0; i < size; i++) {
            int x = layout.panelLeft + 4 + (i % columns) * RequestTableLayout.SLOT;
            int y = layout.panelTop + 3 + (i / columns - storageScrollRow) * RequestTableLayout.SLOT;
            if (y < storageTop || y + RequestTableLayout.SLOT > storageBottom) {
                continue;
            }
            GuiGraphics.drawSlotBackground(mc, x - 1, y - 1);
        }
    }

    private void drawStorageScrollbar(int maxScroll) {
        Gui.drawRect(
                layout.scrollbarX,
                layout.panelTop + 1,
                layout.scrollbarX + 5,
                layout.panelTop + layout.panelHeight - 1,
                Color.getValue(Color.DARKER_GREY));
        int barHeight = Math.max(10, layout.panelHeight / Math.max(1, maxScroll + 1));
        int travel = Math.max(1, layout.panelHeight - 2 - barHeight);
        int barTop = layout.panelTop + 1 + (maxScroll == 0 ? 0 : travel * storageScrollRow / maxScroll);
        Gui.drawRect(
                layout.scrollbarX + 1,
                barTop,
                layout.scrollbarX + 4,
                barTop + barHeight,
                Color.getValue(Color.LIGHTER_GREY));
    }

    private void drawCraftingArea() {
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                GuiGraphics.drawSlotBackground(mc, layout.craftingLeft + x * 18 - 1, layout.craftingTop + 6 + y * 18);
            }
        }
        GuiGraphics.drawSlotBackground(mc, layout.craftingResultX - 1, layout.craftingResultY - 1);
        Gui.drawRect(
                layout.craftingLeft + 63,
                layout.craftingTop + 31,
                layout.craftingLeft + 84,
                layout.craftingTop + 36,
                Color.getValue(Color.DARKER_GREY));
        for (int i = 0; i < 7; i++) {
            Gui.drawRect(
                    layout.craftingLeft + 80 + i,
                    layout.craftingTop + 27 + i,
                    layout.craftingLeft + 82 + i,
                    layout.craftingTop + 41 - i,
                    Color.getValue(Color.DARKER_GREY));
        }
        drawClearCraftingButton();
        if (ingredientAmountField != null) {
            ingredientAmountField.drawTextBox();
        }
    }

    private void drawClearCraftingButton() {
        int x = layout.craftingClearX;
        int y = layout.craftingClearY;
        int size = layout.craftingClearSize;
        Gui.drawRect(x, y, x + size, y + size, Color.getValue(Color.BLACK));
        Gui.drawRect(x + 1, y + 1, x + size - 1, y + size - 1, Color.getValue(Color.DARKER_GREY));
        mc.fontRenderer.drawString("x", x + 3, y + 1, 0xffffff);
    }

    private void drawStorageButton(int x, int y, float fill, boolean active, boolean fluid) {
        Gui.drawRect(x, y, x + 20, y + 20, Color.getValue(Color.BLACK));
        Gui.drawRect(x + 1, y + 1, x + 19, y + 19, Color.getValue(Color.DARKER_GREY));
        int fillHeight = Math.round(18 * fill);
        int fillColor = fluid ? 0xff3488d1 : 0xffa56a43;
        Gui.drawRect(x + 1, y + 19 - fillHeight, x + 19, y + 19, fillColor);
        if (active) {
            mc.fontRenderer.drawString("X", x + 7, y + 6, 0xffffff);
        } else if (fluid) {
            drawTankIcon(x, y);
        } else {
            drawCrateIcon(x, y);
        }
    }

    private void drawTankIcon(int x, int y) {
        Gui.drawRect(x + 6, y + 4, x + 14, y + 16, 0xffffffff);
        Gui.drawRect(x + 7, y + 5, x + 13, y + 15, 0xff4f9ee3);
        Gui.drawRect(x + 5, y + 6, x + 6, y + 14, 0xffffffff);
        Gui.drawRect(x + 14, y + 6, x + 15, y + 14, 0xffffffff);
    }

    private void drawCrateIcon(int x, int y) {
        Gui.drawRect(x + 4, y + 6, x + 16, y + 16, 0xffffffff);
        Gui.drawRect(x + 5, y + 7, x + 15, y + 15, 0xff9c6b35);
        Gui.drawRect(x + 5, y + 10, x + 15, y + 11, 0xff5b371c);
        Gui.drawRect(x + 10, y + 7, x + 11, y + 15, 0xff5b371c);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (requestOverlay.isOpen()) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            Gui.drawRect(0, 0, width, height, 0x66000000);
            requestOverlay.render(this, requestOverlay.getEntry().getInternalAmount());
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        } else if (!hasSubGui() && view == RequestTableView.NETWORK) {
            drawDisplayButtonTooltip(mouseX, mouseY);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        super.drawGuiContainerForegroundLayer(mouseX, mouseY);
        if (view == RequestTableView.NETWORK && !hasSubGui() && !requestOverlay.isOpen()) {
            GuiGraphics.displayItemToolTip(networkGrid.getTooltip(), this, zLevel, guiLeft, guiTop);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == SEND_BUTTON) {
            int mode = view == RequestTableView.FLUID_STORAGE ? 1 : 0;
            MainProxy.sendPacketToServer(
                    PacketHandler.getPacket(RequestTableSendStoragePacket.class).setInteger(mode)
                            .setTilePos(table.container));
        } else if (button.id == REQUEST_INGREDIENTS_BUTTON) {
            requestCraftingIngredients();
        } else if (button.id == SORT_MODE_BUTTON) {
            applyDisplaySettings(displaySettings.nextSortMode());
        } else if (button.id == SORT_DIRECTION_BUTTON) {
            applyDisplaySettings(displaySettings.nextSortDirection());
        } else if (button.id == FILTER_MODE_BUTTON) {
            applyDisplaySettings(displaySettings.nextFilterMode());
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (requestOverlay.mouseClicked(mouseX, mouseY, button, this::submitOverlayRequest)) {
            return;
        }
        if (button == 0 && inside(
                mouseX,
                mouseY,
                layout.craftingClearX,
                layout.craftingClearY,
                layout.craftingClearSize,
                layout.craftingClearSize)) {
            table.clearCraftingGrid();
            MainProxy.sendPacketToServer(
                    PacketHandler.getPacket(RequestTableClearCraftingPacket.class).setTilePos(table.container));
            return;
        }
        if (ingredientAmountField != null) {
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
        if (button == 0 && inside(
                mouseX,
                mouseY,
                layout.itemButtonX,
                layout.storageButtonY,
                layout.storageButtonSize,
                layout.storageButtonSize)) {
            setView(view == RequestTableView.ITEM_STORAGE ? RequestTableView.NETWORK : RequestTableView.ITEM_STORAGE);
            return;
        }
        if (button == 0 && inside(
                mouseX,
                mouseY,
                layout.fluidButtonX,
                layout.storageButtonY,
                layout.storageButtonSize,
                layout.storageButtonSize)) {
            setView(view == RequestTableView.FLUID_STORAGE ? RequestTableView.NETWORK : RequestTableView.FLUID_STORAGE);
            return;
        }
        if (view == RequestTableView.NETWORK) {
            search.handleClick(mouseX, mouseY, button);
            networkGrid.setSearch(getSearchText());
            RequestTableNetworkEntry entry = networkGrid.getEntryAt(layout, mouseX, mouseY);
            if (entry != null) {
                if (shouldOpenRequestOverlay(button)) {
                    requestOverlay.open(entry, mc.fontRenderer, width, height, button == 0 ? 64 : 1);
                } else if (button == 0 || button == 1) {
                    MainProxy.sendPacketToServer(
                            PacketHandler.getPacket(RequestTableNetworkInteractPacket.class).setFluid(entry.isFluid())
                                    .setMouseButton(button).setShift(isShiftDown()).setDimension(dimension)
                                    .setStack(entry.getStack()).setTilePos(table.container));
                }
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void keyTyped(char typed, int keyCode) {
        if (requestOverlay.keyTyped(typed, keyCode, this::submitOverlayRequest)) {
            return;
        }
        if (ingredientAmountField != null && ingredientAmountField.isFocused()) {
            if (keyCode == 1) {
                ingredientAmountField.setFocused(false);
                return;
            }
            if (keyCode == 28 || keyCode == 156) {
                requestCraftingIngredients();
                return;
            }
            ingredientAmountField.textboxKeyTyped(typed, keyCode);
            sanitizeIngredientAmountField();
            return;
        }
        if (view == RequestTableView.NETWORK && keyCode != 1 && search.handleKey(typed, keyCode)) {
            networkGrid.setSearch(getSearchText());
            return;
        }
        super.keyTyped(typed, keyCode);
    }

    @Override
    public void handleMouseInputSub() {
        if (requestOverlay.isOpen()) {
            super.handleMouseInputSub();
            Mouse.getEventDWheel();
            return;
        }
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
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
                PacketHandler.getPacket(RequestTableSubmitPacket.class).setFluid(entry.isFluid())
                        .setDimension(dimension)
                        .setStack(entry.getStack().getItem().makeStack(requestOverlay.getAmount()))
                        .setTilePos(table.container));
        requestOverlay.close();
        refreshNetwork();
    }

    private void setView(RequestTableView newView) {
        view = newView;
        storageScrollRow = 0;
        if (view == RequestTableView.NETWORK) {
            refreshNetwork();
        }
        updateContainerLayout();
    }

    private void refreshNetwork() {
        MainProxy.sendPacketToServer(
                PacketHandler.getPacket(RequestTableRefreshPacket.class).setInteger(dimension)
                        .setTilePos(table.container));
    }

    private void applyDisplaySettings(RequestTableDisplaySettings newSettings) {
        displaySettings = newSettings;
        networkGrid.setDisplaySettings(newSettings);
        updateDisplayButtons();
        MainProxy.sendPacketToServer(
                PacketHandler.getPacket(RequestTableDisplaySettingsPacket.class).setSettings(newSettings)
                        .setTilePos(table.container));
    }

    private void updateDisplayButtons() {
        if (sortModeButton == null) {
            return;
        }
        sortModeButton.displayString = displaySettings.getSortMode() == RequestTableDisplaySettings.SortMode.NAME
                ? "Name"
                : "Amount";
        sortDirectionButton.displayString = displaySettings.getSortDirection()
                == RequestTableDisplaySettings.SortDirection.ASCENDING ? "Asc" : "Desc";
        switch (displaySettings.getFilterMode()) {
            case STORED:
                filterModeButton.displayString = "Stored";
                break;
            case CRAFTABLE:
                filterModeButton.displayString = "Craft";
                break;
            case BOTH:
            default:
                filterModeButton.displayString = "Both";
                break;
        }
    }

    private void updateDisplayButtonLayout() {
        boolean visible = view == RequestTableView.NETWORK;
        sortModeButton.visible = visible;
        sortDirectionButton.visible = visible;
        filterModeButton.visible = visible;
        sortModeButton.xPosition = layout.displayButtonX;
        sortModeButton.yPosition = layout.sortModeButtonY;
        sortDirectionButton.xPosition = layout.displayButtonX;
        sortDirectionButton.yPosition = layout.sortDirectionButtonY;
        filterModeButton.xPosition = layout.displayButtonX;
        filterModeButton.yPosition = layout.filterModeButtonY;
    }

    private void drawDisplayButtonTooltip(int mouseX, int mouseY) {
        GuiButton button = getHoveredDisplayButton(mouseX, mouseY);
        if (button == null) {
            return;
        }
        List<String> tooltip;
        if (button.id == SORT_MODE_BUTTON) {
            String mode = displaySettings.getSortMode() == RequestTableDisplaySettings.SortMode.NAME ? "Item name"
                    : "Item amount";
            tooltip = Arrays.asList("Sort by", mode);
        } else if (button.id == SORT_DIRECTION_BUTTON) {
            String direction = displaySettings.getSortDirection() == RequestTableDisplaySettings.SortDirection.ASCENDING
                    ? "Ascending"
                    : "Descending";
            tooltip = Arrays.asList("Sort direction", direction);
        } else {
            String filter;
            switch (displaySettings.getFilterMode()) {
                case STORED:
                    filter = "Stored";
                    break;
                case CRAFTABLE:
                    filter = "Craftable";
                    break;
                case BOTH:
                default:
                    filter = "Stored and craftable";
                    break;
            }
            tooltip = Arrays.asList("Show entries", filter);
        }
        GuiGraphics.drawToolTip(mouseX, mouseY, tooltip, EnumChatFormatting.WHITE);
    }

    private GuiButton getHoveredDisplayButton(int mouseX, int mouseY) {
        if (isHovered(sortModeButton, mouseX, mouseY)) {
            return sortModeButton;
        }
        if (isHovered(sortDirectionButton, mouseX, mouseY)) {
            return sortDirectionButton;
        }
        return isHovered(filterModeButton, mouseX, mouseY) ? filterModeButton : null;
    }

    private boolean isHovered(GuiButton button, int mouseX, int mouseY) {
        return button != null && button.visible
                && inside(mouseX, mouseY, button.xPosition, button.yPosition, button.width, button.height);
    }

    private void initIngredientAmountField() {
        String text = ingredientAmountField == null ? "1" : ingredientAmountField.getText();
        boolean focused = ingredientAmountField != null && ingredientAmountField.isFocused();
        ingredientAmountField = new GuiTextField(
                mc.fontRenderer,
                layout.craftingAmountX,
                layout.craftingAmountY,
                layout.craftingAmountWidth,
                layout.craftingAmountHeight);
        ingredientAmountField.setMaxStringLength(5);
        ingredientAmountField.setText(text);
        ingredientAmountField.setFocused(focused);
        sanitizeIngredientAmountField();
    }

    private void sanitizeIngredientAmountField() {
        String text = ingredientAmountField.getText();
        StringBuilder digits = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) {
                digits.append(c);
            }
        }
        String sanitized = digits.toString();
        if (!sanitized.equals(text)) {
            ingredientAmountField.setText(sanitized);
        }
    }

    private int getIngredientRequestAmount() {
        String text = ingredientAmountField == null ? "" : ingredientAmountField.getText();
        if (text.isEmpty()) {
            return 1;
        }
        try {
            return Math.max(1, Math.min(9999, Integer.parseInt(text)));
        } catch (NumberFormatException ignored) {
            return 1;
        }
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

    private boolean inside(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void clampStorageScroll() {
        int columns = 9;
        int size = view == RequestTableView.FLUID_STORAGE ? table.getFluidStorage().getSizeInventory()
                : table.inv.getSizeInventory();
        int rows = (size + columns - 1) / columns;
        int visibleRows = layout.getVisibleStorageRows();
        storageScrollRow = Math.max(0, Math.min(Math.max(0, rows - visibleRows), storageScrollRow));
    }

}
