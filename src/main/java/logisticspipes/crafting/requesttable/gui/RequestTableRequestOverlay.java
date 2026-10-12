package logisticspipes.crafting.requesttable.gui;

import static logisticspipes.crafting.requesttable.gui.RequestTableRender.inside;

import net.minecraft.client.gui.FontRenderer;

import org.lwjgl.input.Keyboard;

import logisticspipes.crafting.requesttable.network.RequestTableNetworkEntry;
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
    private static final int PLUS_TOP = 29;
    private static final int ITEM_TOP = 47;
    private static final int MINUS_TOP = 77;
    private static final int CONFIRM_LEFT = 124;
    private static final int CONFIRM_TOP = ITEM_TOP + 4;
    private static final int CONFIRM_WIDTH = 28;
    private static final int CONFIRM_HEIGHT = 18;
    private static final int[] DELTAS = {1, 10, 100, 1000};

    /**
     * -- GETTER --
     *
     * @return the selected network entry
     */
    @Getter
    private RequestTableNetworkEntry entry;
    private RequestTableNumberField amountField;
    private int left;
    private int top;

    /**
     * Opens the overlay for a network entry.
     */
    public void open(RequestTableNetworkEntry entry, FontRenderer font, int screenWidth, int screenHeight, int amount) {
        this.entry = entry;
        left = (screenWidth - WIDTH) / 2;
        top = (screenHeight - HEIGHT) / 2;
        amountField = new RequestTableNumberField(font, left + 60, top + ITEM_TOP + 6, 55, 14, 9, 0, 999999999);
        amountField.setText(Integer.toString(Math.max(0, amount)));
        amountField.setFocused(true);
        amountField.setSelectionPos(0);
    }

    /**
     * Opens the overlay with zero requested initially.
     */
    public void open(RequestTableNetworkEntry entry, FontRenderer font, int screenWidth, int screenHeight) {
        open(entry, font, screenWidth, screenHeight, 0);
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
    }

    /**
     * Closes the overlay.
     */
    public void close() {
        entry = null;
        amountField = null;
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
    public void render(LogisticsBaseGuiScreen screen) {
        if (!isOpen()) {
            return;
        }
        GuiGraphics.drawGuiBackGround(screen.getMC(), left, top, left + WIDTH, top + HEIGHT, 250, true);
        RequestTableGuiStyle.requestButton(left + WIDTH - 13, top + 3, 10, 10, true);
        screen.getMC().fontRenderer.drawString("x", left + WIDTH - 10, top + 4, 0x404040);
        String unit = entry.fluid() ? " mB" : "";
        screen.getMC().fontRenderer
            .drawString("In table: " + entry.internalAmount() + unit, left + 8, top + 5, RequestTableGuiStyle.TEXT);
        screen.getMC().fontRenderer
            .drawString("Network: " + entry.networkAmount() + unit, left + 8, top + 17, RequestTableGuiStyle.TEXT);
        GuiGraphics.drawBigSlotBackground(screen.getMC(), left + 24, top + ITEM_TOP);

        for (int i = 0; i < DELTAS.length; i++) {
            drawDeltaButton(screen, i, true);
            drawDeltaButton(screen, i, false);
        }

        RequestTableRender.item(entry.getDisplayStack(), left + 29, top + ITEM_TOP + 5, 250.0F, false);
        if (entry.craftable()) {
            RequestTableIcons.craftable(left + 29, top + ITEM_TOP + 5);
        }

        amountField.drawTextBox();
        RequestTableGuiStyle
            .requestButton(left + CONFIRM_LEFT, top + CONFIRM_TOP, CONFIRM_WIDTH, CONFIRM_HEIGHT, hasValidAmount());
        screen.getMC().fontRenderer.drawString(
            "OK",
            left + CONFIRM_LEFT + 7,
            top + CONFIRM_TOP + 5,
            hasValidAmount() ? 0x404040 : 0xc0c0c0);
    }

    private void drawDeltaButton(LogisticsBaseGuiScreen screen, int index, boolean plus) {
        int x = left + DELTA_LEFT + index * DELTA_STEP;
        int y = top + (plus ? PLUS_TOP : MINUS_TOP);
        RequestTableGuiStyle.requestButton(x, y, DELTA_WIDTH, DELTA_HEIGHT, true);
        String label = (plus ? "+" : "-") + DELTAS[index];
        screen.getMC().fontRenderer.drawString(
            label,
            x + DELTA_WIDTH / 2 - screen.getMC().fontRenderer.getStringWidth(label) / 2,
            y + 3,
            0x404040);
    }

    /**
     * Handles a mouse click.
     *
     * @return {@code true} if the click was consumed
     */
    public boolean mouseClicked(int mouseX, int mouseY, int button, Runnable submit) {
        if (!isOpen()) {
            return false;
        }
        if (button != 0) {
            return true;
        }
        if (inside(mouseX, mouseY, left + WIDTH - 13, top + 3, 10, 10)) {
            close();
            return true;
        }
        if (inside(mouseX, mouseY, left + CONFIRM_LEFT, top + CONFIRM_TOP, CONFIRM_WIDTH, CONFIRM_HEIGHT)) {
            if (!hasValidAmount()) {
                return true;
            }
            submit.run();
            return true;
        }
        for (int i = 0; i < DELTAS.length; i++) {
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
        changeAmount(plus ? DELTAS[index] : -DELTAS[index]);
        return true;
    }

    private void changeAmount(int delta) {
        int amount = Math.max(0, Math.min(999999999, getAmount() + delta));
        amountField.setText(Integer.toString(amount));
        amountField.setFocused(true);
    }

    /**
     * Handles keyboard input.
     *
     * @return {@code true} if the key was consumed
     */
    public boolean keyTyped(char typed, int keyCode, Runnable submit) {
        if (!isOpen()) {
            return false;
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
}
