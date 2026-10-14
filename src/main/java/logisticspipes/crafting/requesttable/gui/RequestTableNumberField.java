package logisticspipes.crafting.requesttable.gui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Numeric input shared by request amounts and crafting sets, preserving the caret while editing. */
@SideOnly(Side.CLIENT)
final class RequestTableNumberField extends GuiTextField {

    private final int minimum;
    private final int maximum;

    RequestTableNumberField(FontRenderer font, int x, int y, int width, int height, int digits, int minimum,
            int maximum) {
        super(font, x, y, width, height);
        this.minimum = minimum;
        this.maximum = maximum;
        setMaxStringLength(digits);
    }

    private static String digits(String text) {
        if (text == null) return "";
        var result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') result.append(c);
        }
        return result.toString();
    }

    @Override
    public void setText(String text) {
        super.setText(digits(text));
    }

    @Override
    public void writeText(String text) {
        // Filter before insertion, so vanilla keeps selection, caret and paste-length handling intact.
        super.writeText(digits(text));
    }

    int getValue() {
        try {
            return Math.max(minimum, Math.min(maximum, Integer.parseInt(getText())));
        } catch (NumberFormatException ignored) {
            return minimum;
        }
    }
}
