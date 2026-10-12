package logisticspipes.crafting.requesttable.gui;

import static net.minecraft.client.gui.Gui.drawRect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.crafting.requesttable.gui.RequestTableIconButton.Icon;

/** Pixel artwork shared by buttons, network entries and the request popup. */
@SideOnly(Side.CLIENT)
public final class RequestTableIcons {

    private static final ResourceLocation ATLAS = RequestTableRender.texture("requesttable_icons");
    private static final float CRAFTABLE_ICON_SCALE = 0.75F;

    private RequestTableIcons() {}

    /** Eight-pixel cog for compact popup controls. */
    static void gear(int x, int y) {
        drawRect(x + 1, y + 1, x + 7, y + 7, 0xff373737);
        drawRect(x + 3, y, x + 5, y + 8, 0xff373737);
        drawRect(x, y + 3, x + 8, y + 5, 0xff373737);
        drawRect(x + 2, y + 2, x + 6, y + 6, 0xff8b8b8b);
        drawRect(x + 2, y + 2, x + 5, y + 3, 0xffe4e4e4);
        drawRect(x + 3, y + 3, x + 5, y + 5, 0xff373737);
    }

    static void draw(Icon icon, int x, int y, int size, boolean enabled) {
        switch (icon) {
            case MESSAGES_ON, MESSAGES_OFF -> drawRequestMessagesIcon(x, y, icon == Icon.MESSAGES_ON);
            case MONITOR_GRID, MONITOR_TREE -> drawMonitorViewIcon(x, y, icon == Icon.MONITOR_TREE);
            case CRAFTABLE, BOTH -> drawCraftingFilterIcon(x, y, size, icon == Icon.BOTH);
            case SEARCH_STANDARD, SEARCH_AUTO, SEARCH_NEI_AUTO, SEARCH_NEI_STANDARD, SAVE_SEARCH_ON, SAVE_SEARCH_OFF, TERMINAL_SMALL, TERMINAL_TALL, ITEMS_ON, ITEMS_OFF, FLUIDS_ON, FLUIDS_OFF, UPGRADES -> drawTerminalControlIcon(
                    icon,
                    x,
                    y);
            default -> {
                int tile = switch (icon) {
                    case NETWORK -> 0;
                    case NAME -> 1;
                    case AMOUNT -> 2;
                    case ASCENDING -> 3;
                    case DESCENDING -> 4;
                    case STORED -> 6;
                    case SEND -> 8;
                    case REQUEST -> 9;
                    case CLEAR -> 10;
                    default -> throw new IllegalArgumentException("No atlas sprite for " + icon);
                };
                RequestTableRender.textureRegion(
                        ATLAS,
                        x,
                        y,
                        size,
                        size,
                        tile / 16.0,
                        0,
                        (tile + 1) / 16.0,
                        1 / 16.0,
                        enabled ? 1.0F : 0.5F);
            }
        }
    }

    private static void drawCraftingFilterIcon(int x, int y, int size, boolean stored) {
        try (var ignored = RequestTableRender.guiState()) {
            GL11.glTranslatef(x, y, 0);
            GL11.glScalef(size / 16.0F, size / 16.0F, 1);
            if (stored) {
                drawRect(1, 6, 11, 15, 0xff373737);
                drawRect(2, 7, 10, 14, 0xffcf9d53);
                drawRect(2, 9, 10, 10, 0xff73604a);
                drawRect(5, 8, 7, 12, 0xffe8c66a);
                GL11.glTranslatef(6, 1, 0);
                GL11.glScalef(1.5F, 1.5F, 1);
            } else {
                GL11.glTranslatef(2, 1, 0);
                GL11.glScalef(2, 2, 1);
            }
            drawHammer();
        }
    }

    private static void drawMonitorViewIcon(int x, int y, boolean tree) {
        if (tree) {
            drawRect(x + 7, y + 5, x + 9, y + 9, 0xff555555);
            drawRect(x + 3, y + 8, x + 13, y + 10, 0xff555555);
            drawRect(x + 3, y + 9, x + 5, y + 12, 0xff555555);
            drawRect(x + 11, y + 9, x + 13, y + 12, 0xff555555);
            RequestTableGuiStyle.inset(x + 5, y, 6, 6);
            RequestTableGuiStyle.inset(x + 1, y + 11, 6, 5);
            RequestTableGuiStyle.inset(x + 9, y + 11, 6, 5);
        } else {
            for (int row = 0; row < 2; row++) {
                for (int column = 0; column < 2; column++) {
                    RequestTableGuiStyle.inset(x + 1 + column * 8, y + 1 + row * 8, 6, 6);
                }
            }
        }
    }

    private static void drawTerminalControlIcon(Icon icon, int x, int y) {
        switch (icon) {
            case SEARCH_STANDARD, SEARCH_AUTO, SEARCH_NEI_AUTO, SEARCH_NEI_STANDARD -> {
                drawRect(x + 2, y + 2, x + 10, y + 10, 0xff373737);
                drawRect(x + 3, y + 3, x + 9, y + 9, 0xffa8c4d6);
                drawRect(x + 4, y + 4, x + 6, y + 6, 0xffe5f1f8);
                for (int i = 0; i < 3; i++) {
                    drawRect(x + 9 + i * 2, y + 9 + i * 2, x + 11 + i * 2, y + 11 + i * 2, 0xff73604a);
                }
                if (icon == Icon.SEARCH_AUTO || icon == Icon.SEARCH_NEI_AUTO) {
                    drawRect(x + 12, y + 1, x + 14, y + 7, 0xffd6a637);
                    drawRect(x + 10, y + 3, x + 16, y + 5, 0xffd6a637);
                }
                if (icon == Icon.SEARCH_NEI_AUTO || icon == Icon.SEARCH_NEI_STANDARD) {
                    drawRect(x + 1, y + 12, x + 8, y + 14, 0xff477dab);
                    drawRect(x + 6, y + 10, x + 8, y + 12, 0xff477dab);
                    drawRect(x + 6, y + 14, x + 8, y + 16, 0xff477dab);
                }
            }
            case SAVE_SEARCH_ON, SAVE_SEARCH_OFF -> {
                drawRect(x + 2, y + 1, x + 14, y + 15, 0xff373737);
                drawRect(x + 3, y + 2, x + 13, y + 14, 0xff5c7490);
                drawRect(x + 5, y + 2, x + 12, y + 6, 0xffdeded2);
                drawRect(x + 4, y + 9, x + 12, y + 14, 0xffdeded2);
                drawRect(x + 6, y + 10, x + 10, y + 11, 0xff777063);
            }
            case TERMINAL_SMALL, TERMINAL_TALL -> {
                boolean tall = icon == Icon.TERMINAL_TALL;
                int top = tall ? 1 : 4;
                int bottom = tall ? 15 : 12;
                drawRect(x + 1, y + top, x + 15, y + bottom, 0xff373737);
                drawRect(x + 2, y + top + 1, x + 14, y + bottom - 1, 0xffdeded2);
                for (int row = top + 2; row < bottom - 1; row += 3) {
                    drawRect(x + 4, y + row, x + 12, y + row + 1, 0xff777063);
                }
            }
            case ITEMS_ON, ITEMS_OFF -> {
                drawRect(x + 1, y + 3, x + 15, y + 14, 0xff373737);
                drawRect(x + 2, y + 4, x + 14, y + 13, 0xffcf9d53);
                drawRect(x + 2, y + 6, x + 14, y + 7, 0xff73604a);
                drawRect(x + 7, y + 5, x + 9, y + 9, 0xffe8c66a);
            }
            case FLUIDS_ON, FLUIDS_OFF -> {
                drawRect(x + 7, y + 1, x + 9, y + 4, 0xff477dab);
                drawRect(x + 5, y + 4, x + 11, y + 6, 0xff5b9bbd);
                drawRect(x + 3, y + 6, x + 13, y + 12, 0xff5b9bbd);
                drawRect(x + 4, y + 12, x + 12, y + 14, 0xff5b9bbd);
                drawRect(x + 6, y + 14, x + 10, y + 15, 0xff477dab);
                drawRect(x + 5, y + 7, x + 6, y + 11, 0xffa8d6ef);
            }
            case UPGRADES -> {
                // A socketed chip with an upward arrow echoes the motherboard's upgrade chips.
                for (int pin = 3; pin < 14; pin += 4) {
                    drawRect(x + pin, y, x + pin + 2, y + 2, 0xffb8a467);
                    drawRect(x + pin, y + 14, x + pin + 2, y + 16, 0xffb8a467);
                    drawRect(x, y + pin, x + 2, y + pin + 2, 0xffb8a467);
                    drawRect(x + 14, y + pin, x + 16, y + pin + 2, 0xffb8a467);
                }
                drawRect(x + 2, y + 2, x + 14, y + 14, 0xff373737);
                drawRect(x + 3, y + 3, x + 13, y + 13, 0xff637452);
                drawRect(x + 4, y + 4, x + 12, y + 12, 0xff29352b);
                drawRect(x + 7, y + 5, x + 9, y + 7, 0xffe4d29a);
                drawRect(x + 5, y + 7, x + 11, y + 9, 0xffe4d29a);
                drawRect(x + 7, y + 9, x + 9, y + 11, 0xffe4d29a);
            }
            default -> {}
        }
        if (icon == Icon.SAVE_SEARCH_OFF || icon == Icon.ITEMS_OFF || icon == Icon.FLUIDS_OFF) {
            for (int i = 0; i < 7; i++) {
                drawRect(x + 2 + i * 2, y + 12 - i * 2, x + 4 + i * 2, y + 14 - i * 2, 0xffb14d43);
            }
        }
    }

    private static void drawRequestMessagesIcon(int x, int y, boolean on) {
        drawRect(x + 1, y + 2, x + 15, y + 12, 0xff373737);
        drawRect(x + 2, y + 3, x + 14, y + 11, 0xffeee4cc);
        drawRect(x + 3, y + 11, x + 6, y + 15, 0xff373737);
        drawRect(x + 4, y + 10, x + 5, y + 13, 0xffeee4cc);
        drawRect(x + 4, y + 5, x + 12, y + 6, 0xff777063);
        drawRect(x + 4, y + 8, x + 10, y + 9, 0xff777063);
        if (on) {
            drawRect(x + 8, y + 11, x + 10, y + 13, 0xff427b39);
            drawRect(x + 10, y + 13, x + 12, y + 15, 0xff427b39);
            drawRect(x + 12, y + 11, x + 14, y + 13, 0xff427b39);
            drawRect(x + 14, y + 9, x + 16, y + 11, 0xff427b39);
        } else {
            for (int i = 0; i < 5; i++) {
                drawRect(x + 10 + i, y + 10 + i, x + 12 + i, y + 12 + i, 0xffb14d43);
                drawRect(x + 14 - i, y + 10 + i, x + 16 - i, y + 12 + i, 0xffb14d43);
            }
        }
    }

    public static void monitor(int x, int y, boolean powered, boolean animate) {
        Gui.drawRect(x, y, x + 16, y + 11, 0xff373737);
        Gui.drawRect(x + 1, y + 1, x + 15, y + 10, powered ? 0xff475446 : 0xff626262);
        int phosphor = powered ? RequestTableGuiStyle.MONITOR_POWER_ON : 0xff8a8a8a;
        // A root and two branches suggest the crafting tree rather than generic storage.
        Gui.drawRect(x + 7, y + 2, x + 9, y + 4, phosphor);
        Gui.drawRect(x + 7, y + 4, x + 8, y + 6, phosphor);
        Gui.drawRect(x + 4, y + 5, x + 12, y + 6, phosphor);
        Gui.drawRect(x + 3, y + 6, x + 6, y + 8, phosphor);
        Gui.drawRect(x + 10, y + 6, x + 13, y + 8, phosphor);
        if (powered && animate) {
            int scan = (int) ((Minecraft.getSystemTime() / 450) % 3);
            Gui.drawRect(x + 12, y + 2 + scan, x + 13, y + 3 + scan, 0xffc8e7ae);
        }
        Gui.drawRect(x + 7, y + 11, x + 9, y + 13, 0xff555555);
        Gui.drawRect(x + 4, y + 13, x + 12, y + 14, 0xff373737);
    }

    static void craftable(int x, int y) {
        try (var ignored = RequestTableRender.guiState()) {
            GL11.glTranslatef(x + 17 - 6 * CRAFTABLE_ICON_SCALE, y - 1, 150.0F);
            GL11.glScalef(CRAFTABLE_ICON_SCALE, CRAFTABLE_ICON_SCALE, 1.0F);
            drawHammer();
        }
    }

    private static void drawHammer() {
        Gui.drawRect(1, 1, 6, 3, 0xff373737);
        Gui.drawRect(3, 2, 5, 7, 0xff373737);
        Gui.drawRect(2, 2, 4, 6, 0xff8b6b3e);
        Gui.drawRect(2, 2, 3, 6, 0xffcf9d53);
        Gui.drawRect(0, 0, 5, 2, 0xffa8b0b8);
        Gui.drawRect(0, 0, 4, 1, 0xffe4e4e4);
    }
}
