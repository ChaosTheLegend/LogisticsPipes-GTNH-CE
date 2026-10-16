package logisticspipes.gui.modularUI.pipes.patterncrafting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import org.lwjgl.opengl.GL11;

/**
 * Small text helpers for slot overlays. The drawing methods must only be called on the client.
 */
final class PatternGuiDraw {

    private PatternGuiDraw() {}

    /**
     * Draws half-sized text in a corner of an 18x18 slot, on top of the rendered item.
     */
    static void drawCornerText(String text, int color, boolean top) {
        if (text == null || text.isEmpty()) {
            return;
        }
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        float scale = 0.5f;
        float width = font.getStringWidth(text) * scale;
        float x = 17 - width;
        float y = top ? 1 : 17 - font.FONT_HEIGHT * scale;
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 300);
        GL11.glScalef(scale, scale, 1);
        font.drawStringWithShadow(text, 0, 0, color);
        GL11.glPopMatrix();
        GL11.glColor4f(1, 1, 1, 1);
    }

    /**
     * Formats a fluid amount compactly: {@code 144}, {@code 2B}, {@code 1.5B}, {@code 12kB}.
     */
    static String formatFluidAmount(int amount) {
        if (amount < 1000) {
            return Integer.toString(amount);
        }
        if (amount >= 1_000_000) {
            return amount / 1_000_000 + "kB";
        }
        if (amount % 1000 == 0) {
            return amount / 1000 + "B";
        }
        if (amount < 10_000) {
            return String.format("%.1fB", amount / 1000.0);
        }
        return amount / 1000 + "B";
    }

    static String formatCount(int amount) {
        if (amount >= 1_000_000) {
            return amount / 1_000_000 + "M";
        }
        if (amount >= 10_000) {
            return amount / 1000 + "k";
        }
        return Integer.toString(amount);
    }
}
