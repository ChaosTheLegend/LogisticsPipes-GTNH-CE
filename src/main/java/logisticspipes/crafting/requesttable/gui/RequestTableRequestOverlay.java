package logisticspipes.crafting.requesttable.gui;

import static logisticspipes.crafting.requesttable.gui.RequestTableRender.inside;

import java.util.Arrays;
import java.util.Collections;
import java.util.function.Consumer;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import logisticspipes.crafting.requesttable.network.RequestTableNetworkEntry;
import logisticspipes.crafting.requesttable.settings.RequestTableOrderSettings;
import logisticspipes.crafting.requesttable.storage.RequestTableFluidDisplay;
import logisticspipes.utils.gui.GuiGraphics;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;
import lombok.Getter;

/**
 * Small modal request editor opened by clicking a network entry.
 */
public class RequestTableRequestOverlay {

    private static final int WIDTH = 184;
    private static final int HEIGHT = 102;
    private static final int DELTA_LEFT = 16;
    private static final int DELTA_STEP = 38;
    private static final int DELTA_WIDTH = 34;
    private static final int DELTA_HEIGHT = 14;
    private static final int PLUS_TOP = 33;
    private static final int ITEM_TOP = 51;
    private static final int MINUS_TOP = 81;
    private static final int ITEM_LEFT = 16;
    private static final int ALL_LEFT = 109;
    private static final int CONFIRM_LEFT = 143;
    private static final int CONFIRM_TOP = ITEM_TOP + 4;
    private static final int CONFIRM_WIDTH = 28;
    private static final int CONFIRM_HEIGHT = 18;
    private static final int HEADER_BUTTON_TOP = 4;
    private static final int HEADER_BUTTON_SIZE = 12;
    private static final int CLOSE_LEFT = WIDTH - 16;
    private static final int SETTINGS_LEFT = CLOSE_LEFT - HEADER_BUTTON_SIZE - 2;

    /**
     * -- GETTER --
     *
     * @return the selected network entry
     */
    @Getter
    private RequestTableNetworkEntry entry;
    private RequestTableNumberField amountField;
    private final RequestTableOrderSettingsEditor settingsEditor = new RequestTableOrderSettingsEditor();
    private RequestTableOrderSettings orderSettings;
    private int[] deltas;
    private int left;
    private int top;

    /**
     * Opens the overlay for a network entry.
     */
    public void open(RequestTableNetworkEntry entry, FontRenderer font, int screenWidth, int screenHeight,
            RequestTableOrderSettings settings) {
        this.entry = entry;
        orderSettings = settings;
        deltas = settings.getAmountSteps(entry.fluid());
        settingsEditor.close();
        left = (screenWidth - WIDTH) / 2;
        top = (screenHeight - HEIGHT) / 2;
        amountField = new RequestTableNumberField(
                font,
                left + 48,
                top + ITEM_TOP + 6,
                55,
                14,
                10,
                0,
                Integer.MAX_VALUE);
        amountField.setText("0");
        amountField.setFocused(true);
        amountField.setSelectionPos(0);
    }

    /**
     * @return {@code true} when the overlay is visible
     */
    public boolean isOpen() {
        return entry != null;
    }

    /**
     * Replaces the selected entry with the matching refreshed entry, keeping the user's typed amount intact.
     */
    public void updateEntry(Iterable<RequestTableNetworkEntry> entries) {
        if (!isOpen()) {
            return;
        }
        for (RequestTableNetworkEntry refreshed : entries) {
            if (refreshed.fluid() == entry.fluid() && refreshed.stack().getItem().equals(entry.stack().getItem())) {
                entry = refreshed;
                return;
            }
        }
        entry = new RequestTableNetworkEntry(entry.stack().getItem().makeStack(0), entry.fluid(), 0, 0, false);
    }

    /**
     * Closes the overlay.
     */
    public void close() {
        entry = null;
        amountField = null;
        settingsEditor.close();
    }

    /**
     * Parses the requested amount.
     */
    public int getAmount() {
        return amountField == null ? 0 : amountField.getValue();
    }

    /**
     * @return {@code true} when the current field content can be submitted
     */
    public boolean hasValidAmount() {
        return getAmount() > 0;
    }

    /**
     * Renders the overlay.
     */
    public void render(LogisticsBaseGuiScreen screen, int mouseX, int mouseY) {
        if (!isOpen()) {
            return;
        }
        if (settingsEditor.isOpen()) {
            settingsEditor.render(screen, mouseX, mouseY);
            return;
        }
        GuiGraphics.drawGuiBackGround(screen.getMC(), left, top, left + WIDTH, top + HEIGHT, 250, true);
        FontRenderer font = screen.getMC().fontRenderer;
        font.drawString("Request", left + 8, top + 7, RequestTableGuiStyle.TEXT);
        RequestTableRender.textButton(
                font,
                "x",
                left + CLOSE_LEFT,
                top + HEADER_BUTTON_TOP,
                HEADER_BUTTON_SIZE,
                HEADER_BUTTON_SIZE,
                true,
                mouseX,
                mouseY);
        RequestTableGuiStyle.requestButton(
                left + SETTINGS_LEFT,
                top + HEADER_BUTTON_TOP,
                HEADER_BUTTON_SIZE,
                HEADER_BUTTON_SIZE,
                true,
                mouseX,
                mouseY);
        RequestTableIcons.gear(left + SETTINGS_LEFT + 2, top + HEADER_BUTTON_TOP + 2);
        drawStockSummary(font);
        GuiGraphics.drawBigSlotBackground(screen.getMC(), left + ITEM_LEFT, top + ITEM_TOP);

        for (int i = 0; i < deltas.length; i++) {
            drawDeltaButton(font, i, true, mouseX, mouseY);
            drawDeltaButton(font, i, false, mouseX, mouseY);
        }

        RequestTableRender.item(entry.getDisplayStack(), left + ITEM_LEFT + 5, top + ITEM_TOP + 5, 250.0F, false);
        if (entry.craftable()) {
            RequestTableIcons.craftable(left + ITEM_LEFT + 5, top + ITEM_TOP + 5);
        }

        amountField.drawTextBox();
        RequestTableRender.textButton(
                font,
                "All",
                left + ALL_LEFT,
                top + CONFIRM_TOP,
                CONFIRM_WIDTH,
                CONFIRM_HEIGHT,
                entry.networkAmount() > 0,
                mouseX,
                mouseY);
        RequestTableRender.textButton(
                font,
                "OK",
                left + CONFIRM_LEFT,
                top + CONFIRM_TOP,
                CONFIRM_WIDTH,
                CONFIRM_HEIGHT,
                hasValidAmount(),
                mouseX,
                mouseY);
        drawTooltip(font, mouseX, mouseY);
    }

    private String formatAmount(int amount) {
        return entry.fluid() ? RequestTableFluidDisplay.formatAmount(amount) : RequestTableGuiStyle.formatCount(amount);
    }

    private void drawStockSummary(FontRenderer font) {
        String internal = "In table: " + formatAmount(entry.internalAmount());
        String network = "Network: " + formatAmount(entry.networkAmount());
        int internalWidth = font.getStringWidth(internal);
        int networkWidth = font.getStringWidth(network);
        int width = WIDTH - 16;
        float scale = Math.min(1.0F, (width - 8) / (float) (internalWidth + networkWidth));
        try (var ignored = RequestTableRender.guiState()) {
            GL11.glTranslatef(left + 8, top + 21, 0);
            GL11.glScalef(scale, scale, 1);
            font.drawString(internal, 0, 0, RequestTableGuiStyle.TEXT);
            font.drawString(network, Math.round(width / scale) - networkWidth, 0, RequestTableGuiStyle.TEXT);
        }
    }

    private void drawDeltaButton(FontRenderer font, int index, boolean plus, int mouseX, int mouseY) {
        int x = left + DELTA_LEFT + index * DELTA_STEP;
        int y = top + (plus ? PLUS_TOP : MINUS_TOP);
        String label = (plus ? "+" : "-") + deltas[index];
        RequestTableRender.textButton(font, label, x, y, DELTA_WIDTH, DELTA_HEIGHT, true, mouseX, mouseY);
    }

    private void drawTooltip(FontRenderer font, int mouseX, int mouseY) {
        if (inside(mouseX, mouseY, left + 8, top + 21, WIDTH - 16, font.FONT_HEIGHT)) {
            String unit = entry.fluid() ? " mB" : "";
            GuiGraphics.drawToolTip(
                    mouseX,
                    mouseY,
                    Arrays.asList(
                            "In table: " + entry.internalAmount() + unit,
                            "Network: " + entry.networkAmount() + unit),
                    EnumChatFormatting.WHITE);
            return;
        }
        String tooltip = null;
        if (inside(
                mouseX,
                mouseY,
                left + SETTINGS_LEFT,
                top + HEADER_BUTTON_TOP,
                HEADER_BUTTON_SIZE,
                HEADER_BUTTON_SIZE)) {
            tooltip = "Configure order steps";
        } else {
            for (int i = 0; i < deltas.length; i++) {
                int x = left + DELTA_LEFT + i * DELTA_STEP;
                if (inside(mouseX, mouseY, x, top + PLUS_TOP, DELTA_WIDTH, DELTA_HEIGHT)) {
                    tooltip = "+" + deltas[i];
                } else if (inside(mouseX, mouseY, x, top + MINUS_TOP, DELTA_WIDTH, DELTA_HEIGHT)) {
                    tooltip = "-" + deltas[i];
                }
            }
        }
        if (tooltip != null) {
            GuiGraphics.drawToolTip(mouseX, mouseY, Collections.singletonList(tooltip), EnumChatFormatting.WHITE);
        }
    }

    /**
     * Handles a mouse click.
     *
     * @return {@code true} if the click was consumed
     */
    public boolean mouseClicked(int mouseX, int mouseY, int button, Runnable submit, LogisticsBaseGuiScreen screen,
            Consumer<RequestTableOrderSettings> saveSettings) {
        if (!isOpen()) {
            return false;
        }
        if (button != 0) {
            return true;
        }
        if (settingsEditor.isOpen()) {
            settingsEditor.mouseClicked(mouseX, mouseY, button, settings -> applySettings(settings, saveSettings));
            return true;
        }
        if (inside(
                mouseX,
                mouseY,
                left + CLOSE_LEFT,
                top + HEADER_BUTTON_TOP,
                HEADER_BUTTON_SIZE,
                HEADER_BUTTON_SIZE)) {
            close();
            return true;
        }
        if (inside(
                mouseX,
                mouseY,
                left + SETTINGS_LEFT,
                top + HEADER_BUTTON_TOP,
                HEADER_BUTTON_SIZE,
                HEADER_BUTTON_SIZE)) {
            settingsEditor.open(screen.getMC().fontRenderer, screen.width, screen.height, orderSettings);
            return true;
        }
        if (inside(mouseX, mouseY, left + ALL_LEFT, top + CONFIRM_TOP, CONFIRM_WIDTH, CONFIRM_HEIGHT)) {
            if (entry.networkAmount() > 0) {
                amountField.setText(Integer.toString(entry.networkAmount()));
                amountField.setFocused(true);
            }
            return true;
        }
        if (inside(mouseX, mouseY, left + CONFIRM_LEFT, top + CONFIRM_TOP, CONFIRM_WIDTH, CONFIRM_HEIGHT)) {
            if (!hasValidAmount()) {
                return true;
            }
            submit.run();
            return true;
        }
        for (int i = 0; i < deltas.length; i++) {
            if (clickDeltaButton(mouseX, mouseY, i, true) || clickDeltaButton(mouseX, mouseY, i, false)) {
                return true;
            }
        }
        amountField.mouseClicked(mouseX, mouseY, button);
        amountField.setFocused(true);
        return true;
    }

    private boolean clickDeltaButton(int mouseX, int mouseY, int index, boolean plus) {
        int x = left + DELTA_LEFT + index * DELTA_STEP;
        int y = top + (plus ? PLUS_TOP : MINUS_TOP);
        if (!inside(mouseX, mouseY, x, y, DELTA_WIDTH, DELTA_HEIGHT)) {
            return false;
        }
        changeAmount(plus ? deltas[index] : -deltas[index]);
        return true;
    }

    private void changeAmount(int delta) {
        int amount = (int) Math.max(0, Math.min(Integer.MAX_VALUE, (long) getAmount() + delta));
        amountField.setText(Integer.toString(amount));
        amountField.setFocused(true);
    }

    /**
     * Handles keyboard input.
     *
     * @return {@code true} if the key was consumed
     */
    public boolean keyTyped(char typed, int keyCode, Runnable submit,
            Consumer<RequestTableOrderSettings> saveSettings) {
        if (!isOpen()) {
            return false;
        }
        if (settingsEditor.isOpen()) {
            settingsEditor.keyTyped(typed, keyCode, settings -> applySettings(settings, saveSettings));
            return true;
        }
        switch (keyCode) {
            case Keyboard.KEY_ESCAPE -> close();
            case Keyboard.KEY_RETURN, Keyboard.KEY_NUMPADENTER -> {
                if (hasValidAmount()) submit.run();
            }
            default -> {
                amountField.textboxKeyTyped(typed, keyCode);
                amountField.setFocused(true);
            }
        }
        return true;
    }

    private void applySettings(RequestTableOrderSettings settings, Consumer<RequestTableOrderSettings> saveSettings) {
        orderSettings = settings;
        deltas = settings.getAmountSteps(entry.fluid());
        saveSettings.accept(settings);
    }
}
