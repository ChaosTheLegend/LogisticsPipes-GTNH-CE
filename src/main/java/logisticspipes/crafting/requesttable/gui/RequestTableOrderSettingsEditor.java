package logisticspipes.crafting.requesttable.gui;

import static logisticspipes.crafting.requesttable.gui.RequestTableRender.inside;

import java.util.function.Consumer;

import net.minecraft.client.gui.FontRenderer;

import org.lwjgl.input.Keyboard;

import logisticspipes.crafting.requesttable.settings.RequestTableOrderSettings;
import logisticspipes.crafting.requesttable.settings.RequestTableOrderSettings.AmountSteps;
import logisticspipes.utils.gui.GuiGraphics;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;

/** Edits a draft of the player's order steps without changing or submitting the current order. */
final class RequestTableOrderSettingsEditor {

    private static final int MARGIN = 12;
    private static final int FIELD_GAP = 6;
    private static final int ROW_STEP = 22;
    private static final int FIELD_HEIGHT = 16;
    private static final int ITEM_LABEL_TOP = 40;
    private static final int ITEM_TOP = 52;
    private static final int BUTTON_HEIGHT = 18;

    private RequestTableNumberField[] fields;
    private int left;
    private int top;
    private int width;
    private int height;
    private int fluidLabelTop;
    private int buttonTop;
    private int buttonWidth;

    boolean isOpen() {
        return fields != null;
    }

    void open(FontRenderer font, int screenWidth, int screenHeight, RequestTableOrderSettings settings) {
        int fieldWidth = Math.max(68, font.getStringWidth(Integer.toString(Integer.MAX_VALUE)) + 10);
        int columns = screenWidth >= MARGIN * 2 + fieldWidth * 4 + FIELD_GAP * 3 + 16 ? 4 : 2;
        int rows = 4 / columns;
        width = MARGIN * 2 + fieldWidth * columns + FIELD_GAP * (columns - 1);
        fluidLabelTop = ITEM_LABEL_TOP + rows * ROW_STEP + 14;
        int fluidTop = fluidLabelTop + 12;
        buttonTop = fluidTop + rows * ROW_STEP + 18;
        height = buttonTop + BUTTON_HEIGHT + 8;
        left = (screenWidth - width) / 2;
        top = (screenHeight - height) / 2;
        buttonWidth = (width - MARGIN * 2 - FIELD_GAP * 2) / 3;
        fields = new RequestTableNumberField[8];
        for (int i = 0; i < fields.length; i++) {
            int index = i % 4;
            fields[i] = new RequestTableNumberField(
                    font,
                    left + MARGIN + index % columns * (fieldWidth + FIELD_GAP),
                    top + (i < 4 ? ITEM_TOP : fluidTop) + index / columns * ROW_STEP,
                    fieldWidth,
                    FIELD_HEIGHT,
                    10,
                    1,
                    Integer.MAX_VALUE);
        }
        fill(settings);
        focus(0);
    }

    void close() {
        fields = null;
    }

    private void fill(RequestTableOrderSettings settings) {
        for (int row = 0; row < 2; row++) {
            int[] amounts = settings.getAmountSteps(row == 1);
            for (int column = 0; column < amounts.length; column++) {
                fields[row * 4 + column].setText(Integer.toString(amounts[column]));
            }
        }
    }

    void render(LogisticsBaseGuiScreen screen, int mouseX, int mouseY) {
        FontRenderer font = screen.getMC().fontRenderer;
        GuiGraphics.drawGuiBackGround(screen.getMC(), left, top, left + width, top + height, 250, true);
        RequestTableRender.textButton(font, "x", left + width - 16, top + 4, 12, 12, true, mouseX, mouseY);
        font.drawString("Order steps", left + MARGIN, top + 8, RequestTableGuiStyle.TEXT);
        font.drawString("Left to right, for + and -", left + MARGIN, top + 24, RequestTableGuiStyle.MUTED);
        font.drawString("Items", left + MARGIN, top + ITEM_LABEL_TOP, RequestTableGuiStyle.TEXT);
        font.drawString("Fluids (mB)", left + MARGIN, top + fluidLabelTop, RequestTableGuiStyle.TEXT);
        for (RequestTableNumberField field : fields) field.drawTextBox();
        boolean valid = valid();
        if (!valid) font.drawString("Positive numbers only.", left + MARGIN, top + buttonTop - 14, 0xa02020);
        drawButton(font, "Reset", 0, true, mouseX, mouseY);
        drawButton(font, "Back", 1, true, mouseX, mouseY);
        drawButton(font, "Save", 2, valid, mouseX, mouseY);
    }

    private void drawButton(FontRenderer font, String label, int index, boolean enabled, int mouseX, int mouseY) {
        RequestTableRender.textButton(
                font,
                label,
                buttonX(index),
                top + buttonTop,
                buttonWidth,
                BUTTON_HEIGHT,
                enabled,
                mouseX,
                mouseY);
    }

    void mouseClicked(int mouseX, int mouseY, int button, Consumer<RequestTableOrderSettings> save) {
        if (button != 0) return;
        if (inside(mouseX, mouseY, left + width - 16, top + 4, 12, 12)) {
            close();
        } else if (onButton(mouseX, mouseY, 0)) {
            fill(RequestTableOrderSettings.DEFAULT);
            focus(0);
        } else if (onButton(mouseX, mouseY, 1)) {
            close();
        } else if (onButton(mouseX, mouseY, 2)) {
            save(save);
        } else {
            for (RequestTableNumberField field : fields) field.mouseClicked(mouseX, mouseY, button);
        }
    }

    private int buttonX(int index) {
        return left + MARGIN + index * (buttonWidth + FIELD_GAP);
    }

    private boolean onButton(int mouseX, int mouseY, int index) {
        return inside(mouseX, mouseY, buttonX(index), top + buttonTop, buttonWidth, BUTTON_HEIGHT);
    }

    void keyTyped(char typed, int keyCode, Consumer<RequestTableOrderSettings> save) {
        switch (keyCode) {
            case Keyboard.KEY_ESCAPE -> close();
            case Keyboard.KEY_RETURN, Keyboard.KEY_NUMPADENTER -> save(save);
            case Keyboard.KEY_TAB -> {
                int current = 0;
                for (int i = 0; i < fields.length; i++) {
                    if (fields[i].isFocused()) current = i;
                }
                boolean reverse = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                focus((current + (reverse ? fields.length - 1 : 1)) % fields.length);
            }
            default -> {
                for (RequestTableNumberField field : fields) {
                    if (field.isFocused()) field.textboxKeyTyped(typed, keyCode);
                }
            }
        }
    }

    private void focus(int index) {
        for (int i = 0; i < fields.length; i++) fields[i].setFocused(i == index);
        fields[index].setCursorPositionEnd();
        fields[index].setSelectionPos(0);
    }

    private boolean valid() {
        for (RequestTableNumberField field : fields) {
            try {
                if (Integer.parseInt(field.getText()) <= 0) return false;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return true;
    }

    private void save(Consumer<RequestTableOrderSettings> save) {
        if (!valid()) return;
        save.accept(new RequestTableOrderSettings(steps(0), steps(4)));
        close();
    }

    private AmountSteps steps(int offset) {
        return new AmountSteps(
                fields[offset].getValue(),
                fields[offset + 1].getValue(),
                fields[offset + 2].getValue(),
                fields[offset + 3].getValue());
    }
}
