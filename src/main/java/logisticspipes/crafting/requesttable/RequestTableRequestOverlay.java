package logisticspipes.crafting.requesttable;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;

import logisticspipes.utils.Color;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;
import logisticspipes.utils.item.ItemStackRenderer;
import logisticspipes.utils.item.ItemStackRenderer.DisplayAmount;

/**
 * Small modal request editor opened by clicking a network entry.
 */
public class RequestTableRequestOverlay {

    private static final int WIDTH = 184;
    private static final int HEIGHT = 86;
    private static final int[] DELTAS = { 1, 10, 100, 1000 };

    private RequestTableNetworkEntry entry;
    private GuiTextField amountField;
    private int left;
    private int top;

    /**
     * Opens the overlay for a network entry.
     */
    public void open(RequestTableNetworkEntry entry, FontRenderer font, int screenWidth, int screenHeight, int amount) {
        this.entry = entry;
        left = (screenWidth - WIDTH) / 2;
        top = (screenHeight - HEIGHT) / 2;
        amountField = new GuiTextField(font, left + 60, top + 36, 55, 14);
        amountField.setMaxStringLength(9);
        amountField.setText(amount > 0 ? Integer.toString(amount) : "");
        amountField.setFocused(true);
    }

    /**
     * Opens the overlay with an entry-sized initial amount.
     */
    public void open(RequestTableNetworkEntry entry, FontRenderer font, int screenWidth, int screenHeight) {
        open(entry, font, screenWidth, screenHeight, Math.max(1, entry.getStack().getStackSize()));
    }

    /**
     * @return {@code true} when the overlay is visible
     */
    public boolean isOpen() {
        return entry != null;
    }

    /**
     * @return the selected network entry
     */
    public RequestTableNetworkEntry getEntry() {
        return entry;
    }

    /**
     * Replaces the selected entry with the matching refreshed entry, keeping the user's typed amount intact.
     */
    public void updateEntry(Iterable<RequestTableNetworkEntry> entries) {
        if (!isOpen()) {
            return;
        }
        for (RequestTableNetworkEntry refreshed : entries) {
            if (refreshed.isFluid() == entry.isFluid()
                    && refreshed.getStack().getItem().equals(entry.getStack().getItem())) {
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
        if (amountField == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(amountField.getText()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
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
    public void render(LogisticsBaseGuiScreen screen, int internalAmount) {
        if (!isOpen()) {
            return;
        }
        screen.drawRect(left, top, left + WIDTH, top + HEIGHT, Color.BLACK);
        screen.drawRect(left + 1, top + 1, left + WIDTH - 1, top + HEIGHT - 1, Color.LIGHTER_GREY);
        drawMinecraftButton(screen, left + WIDTH - 13, top + 3, 10, 10, true);
        screen.getMC().fontRenderer.drawString("x", left + WIDTH - 10, top + 4, 0xffffff);
        screen.getMC().fontRenderer.drawString("In table: " + internalAmount, left + 8, top + 5, 0x404040);

        for (int i = 0; i < DELTAS.length; i++) {
            drawDeltaButton(screen, i, true);
            drawDeltaButton(screen, i, false);
        }

        new ItemStackRenderer(left + 29, top + 35, 250.0F, true, true, true).setItemIdentifierStack(entry.getStack())
                .setDisplayAmount(DisplayAmount.NEVER).renderInGui();

        amountField.drawTextBox();
        drawMinecraftButton(screen, left + 124, top + 34, 28, 18, hasValidAmount());
        screen.getMC().fontRenderer.drawString("OK", left + 131, top + 39, hasValidAmount() ? 0xffffff : 0xa0a0a0);
    }

    private void drawDeltaButton(LogisticsBaseGuiScreen screen, int index, boolean plus) {
        int x = left + 16 + index * 38;
        int y = plus ? top + 17 : top + 61;
        drawMinecraftButton(screen, x, y, 34, 14, true);
        String label = (plus ? "+" : "-") + DELTAS[index];
        screen.getMC().fontRenderer
                .drawString(label, x + 17 - screen.getMC().fontRenderer.getStringWidth(label) / 2, y + 3, 0xffffff);
    }

    private void drawMinecraftButton(LogisticsBaseGuiScreen screen, int x, int y, int width, int height,
            boolean enabled) {
        int fill = enabled ? 0xffc6c6c6 : 0xff7f7f7f;
        screen.drawRect(x, y, x + width, y + height, Color.BLACK);
        screen.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, fill);
        screen.drawRect(x + 1, y + 1, x + width - 2, y + 2, 0xffffffff);
        screen.drawRect(x + 1, y + 1, x + 2, y + height - 2, 0xffffffff);
        screen.drawRect(x + 1, y + height - 2, x + width - 1, y + height - 1, 0xff555555);
        screen.drawRect(x + width - 2, y + 1, x + width - 1, y + height - 1, 0xff555555);
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
        if (mouseX >= left + WIDTH - 13 && mouseX < left + WIDTH - 3 && mouseY >= top + 3 && mouseY < top + 13) {
            close();
            return true;
        }
        if (mouseX >= left + 124 && mouseX < left + 152 && mouseY >= top + 34 && mouseY < top + 52) {
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
        int x = left + 16 + index * 38;
        int y = plus ? top + 17 : top + 61;
        if (mouseX < x || mouseX >= x + 34 || mouseY < y || mouseY >= y + 14) {
            return false;
        }
        changeAmount(plus ? DELTAS[index] : -DELTAS[index]);
        return true;
    }

    private void changeAmount(int delta) {
        int amount = Math.max(0, Math.min(999999999, getAmount() + delta));
        amountField.setText(amount == 0 ? "" : Integer.toString(amount));
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
        if (keyCode == Keyboard.KEY_ESCAPE) {
            close();
            return true;
        }
        if (hasValidAmount() && (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER)) {
            submit.run();
            return true;
        }
        if (Character.isDigit(typed) || keyCode == Keyboard.KEY_BACK
                || keyCode == Keyboard.KEY_DELETE
                || keyCode == Keyboard.KEY_LEFT
                || keyCode == Keyboard.KEY_RIGHT) {
            amountField.textboxKeyTyped(typed, keyCode);
            sanitizeAmount();
        }
        amountField.setFocused(true);
        return true;
    }

    private void sanitizeAmount() {
        String text = amountField.getText();
        StringBuilder digits = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) {
                digits.append(c);
            }
        }
        amountField.setText(digits.toString());
    }
}
