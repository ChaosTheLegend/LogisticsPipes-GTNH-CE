package logisticspipes.gui.modularUI.pipes.requesttable;

import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

/**
 * Digits-only text field for amounts up to {@link Integer#MAX_VALUE}, with Enter and Escape hooks.
 * <p>
 * Escape is consumed by the hook so a popup closes on its own instead of closing the whole GUI.
 */
public class RequestTableNumberFieldWidget extends TextFieldWidget {

    private static final Pattern DIGITS = Pattern.compile("[0-9]*");

    private final StringValue text;
    private final int min;
    private Runnable onEnter = () -> {};
    private Runnable onEscape;
    private Runnable onTab;

    public RequestTableNumberFieldWidget(String initial, int min) {
        this.min = min;
        text = new StringValue(initial);
        value(text);
        setPattern(DIGITS);
        setMaxLength(10);
        autoUpdateOnChange(true);
    }

    public RequestTableNumberFieldWidget onEnter(Runnable onEnter) {
        this.onEnter = onEnter;
        return this;
    }

    public RequestTableNumberFieldWidget onEscape(Runnable onEscape) {
        this.onEscape = onEscape;
        return this;
    }

    public RequestTableNumberFieldWidget onTab(Runnable onTab) {
        this.onTab = onTab;
        return this;
    }

    /** @return the typed amount clamped to the field's minimum and {@link Integer#MAX_VALUE} */
    public int getAmount() {
        String current = text.getStringValue();
        if (current == null || current.isEmpty()) return min;
        try {
            return (int) Math.max(min, Math.min(Integer.MAX_VALUE, Long.parseLong(current)));
        } catch (NumberFormatException ignored) {
            return min;
        }
    }

    /** @return {@code true} if the text is a positive number that fits into an int */
    public boolean isPositive() {
        try {
            return Integer.parseInt(text.getStringValue()) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    public void setValue(String value) {
        text.setStringValue(value);
        setText(value);
    }

    @Override
    public @NotNull Result onKeyPressed(char character, int keyCode) {
        if (isFocused()) {
            switch (keyCode) {
                case Keyboard.KEY_RETURN, Keyboard.KEY_NUMPADENTER -> {
                    onEnter.run();
                    return Result.SUCCESS;
                }
                case Keyboard.KEY_ESCAPE -> {
                    if (onEscape != null) {
                        onEscape.run();
                        return Result.SUCCESS;
                    }
                }
                case Keyboard.KEY_TAB -> {
                    if (onTab != null) {
                        onTab.run();
                        return Result.SUCCESS;
                    }
                }
                default -> {}
            }
        }
        return super.onKeyPressed(character, keyCode);
    }
}
