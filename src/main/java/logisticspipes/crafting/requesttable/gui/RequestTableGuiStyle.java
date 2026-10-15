package logisticspipes.crafting.requesttable.gui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Shared Minecraft bevels for the table, scrollbars, and request popup. */
@SideOnly(Side.CLIENT)
public final class RequestTableGuiStyle {

    public static final int TEXT = 0x404040;
    public static final int MUTED = 0x686868;
    static final int MONITOR_POWER_ON = 0xff86be65;
    static final int MONITOR_POWER_OFF = 0xff777777;

    private RequestTableGuiStyle() {}

    public static String formatCount(int amount) {
        if (amount >= 1_000_000_000) {
            return amount / 1_000_000_000 + "G";
        }
        if (amount >= 1_000_000) {
            return amount / 1_000_000 + "M";
        }
        if (amount >= 1_000) {
            return amount / 1_000 + "k";
        }
        return Integer.toString(amount);
    }

    /** Matches NEI's fluid display text scale, shadow and bottom-left placement. */
    public static void drawCount(FontRenderer font, String amount, int x, int y, int color, boolean topLeft) {
        drawCount(font, amount, x, y, color, topLeft, 0);
    }

    /** Limits a corner count when a status icon shares the top edge. */
    public static void drawCount(FontRenderer font, String amount, int x, int y, int color, boolean topLeft,
            int availableWidth) {
        float scale = font.getUnicodeFlag() ? 3.0F / 4.0F : 1.0F / 2.0F;
        if (availableWidth > 0) {
            scale = Math.min(scale, availableWidth / (float) (font.getStringWidth(amount) + 1));
        }
        try (var ignored = RequestTableRender.guiState()) {
            GL11.glTranslatef(x, y, 150.0F);
            GL11.glScalef(scale, scale, 1.0F);
            int drawX = topLeft ? Math.round(-1 / scale) : 0;
            int drawY = topLeft ? Math.round(-1 / scale) : (int) (16 / scale) - font.FONT_HEIGHT + 1;
            font.drawString(amount, drawX, drawY, color, true);
        }
    }

    public static void inset(int x, int y, int width, int height) {
        Gui.drawRect(x, y, x + width, y + height, 0xff373737);
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, 0xff8b8b8b);
        Gui.drawRect(x, y + height - 1, x + width, y + height, 0xffffffff);
        Gui.drawRect(x + width - 1, y, x + width, y + height, 0xffffffff);
    }

    public static void raised(int x, int y, int width, int height, boolean hover) {
        bevel(x, y, width, height, hover ? 0xffa8b4c2 : 0xffc6c6c6, 0xff373737);
    }

    public static void bevel(int x, int y, int width, int height, int fill, int outline) {
        Gui.drawRect(x, y, x + width, y + height, outline);
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, fill);
        Gui.drawRect(x + 1, y + 1, x + width - 2, y + 2, 0xffffffff);
        Gui.drawRect(x + 1, y + 1, x + 2, y + height - 2, 0xffffffff);
        Gui.drawRect(x + 1, y + height - 2, x + width - 1, y + height - 1, 0xff555555);
        Gui.drawRect(x + width - 2, y + 1, x + width - 1, y + height - 1, 0xff555555);
    }

    public static void requestButton(int x, int y, int width, int height, boolean enabled) {
        bevel(x, y, width, height, enabled ? 0xffc6c6c6 : 0xff7f7f7f, 0xff000000);
    }

    public static void requestButton(int x, int y, int width, int height, boolean enabled, int mouseX, int mouseY) {
        boolean hover = enabled && RequestTableRender.inside(mouseX, mouseY, x, y, width, height);
        bevel(x, y, width, height, !enabled ? 0xff7f7f7f : hover ? 0xffa8b4c2 : 0xffc6c6c6, 0xff000000);
    }

    public static void border(int x, int y, int width, int height, int color) {
        Gui.drawRect(x, y, x + width, y + 1, color);
        Gui.drawRect(x, y + height - 1, x + width, y + height, color);
        Gui.drawRect(x, y, x + 1, y + height, color);
        Gui.drawRect(x + width - 1, y, x + width, y + height, color);
    }

    static void scrollbar(RequestTableLayout layout, int scrollRow, int maxScroll) {
        int x = layout.scrollbarX;
        int y = layout.panelTop;
        int height = layout.panelHeight;
        inset(x, y, 7, height);
        int thumbHeight = getScrollbarThumbHeight(layout, maxScroll);
        int top = getScrollbarThumbTop(layout, scrollRow, maxScroll);
        raised(x, top, 7, thumbHeight, false);
    }

    static int getScrollbarThumbHeight(RequestTableLayout layout, int maxScroll) {
        int rows = layout.getVisiblePanelRows();
        return Math.max(12, layout.panelHeight * rows / Math.max(1, rows + maxScroll));
    }

    static int getScrollbarThumbTop(RequestTableLayout layout, int scrollRow, int maxScroll) {
        int travel = layout.panelHeight - getScrollbarThumbHeight(layout, maxScroll);
        return layout.panelTop + (maxScroll == 0 ? 0 : travel * scrollRow / maxScroll);
    }
}
