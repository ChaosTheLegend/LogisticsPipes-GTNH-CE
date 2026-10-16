package logisticspipes.crafting.requesttable.gui;

import java.util.Objects;

import net.minecraft.client.Minecraft;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Icon buttons and tabs with shared pixel artwork. */
@SideOnly(Side.CLIENT)
final class RequestTableIconButton extends RequestTableButton {

    private static final int TAB_LABEL_TOP = 4;
    private static final int TAB_LETTER_HEIGHT = 7;
    private static final int TAB_FILL_GAP = 1;
    private Icon icon;
    private boolean selected;
    private float fill = -1;
    private int fillColor;
    private String tabLabel;

    RequestTableIconButton(int id, int x, int y, int width, int height, Icon icon) {
        super(id, x, y, width, height, "");
        this.icon = icon;
    }

    void setIcon(Icon icon) {
        this.icon = icon;
    }

    void setSelected(boolean selected) {
        this.selected = selected;
    }

    void setFill(float fill, int color) {
        this.fill = fill;
        fillColor = color;
    }

    void setTabLabel(String label) {
        tabLabel = label;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (tabLabel == null || selected) super.drawButton(mc, mouseX, mouseY);
    }

    /** Inactive tabs are occluded by the panel; the selected tab joins its normal button pass. */
    void drawBehindPanel(Minecraft mc, int mouseX, int mouseY) {
        if (tabLabel != null && !selected) super.drawButton(mc, mouseX, mouseY);
    }

    @Override
    protected void drawContent(Minecraft mc, boolean hover) {
        if (tabLabel != null && !selected) {
            drawRect(
                    xPosition + 2,
                    yPosition + 2,
                    xPosition + width - 2,
                    yPosition + height - 2,
                    hover ? 0xffb8bec6 : 0xffaeaeae);
        }
        if (selected) {
            drawRect(xPosition + 1, yPosition + 1, xPosition + width - 1, yPosition + 2, 0xffe8c66a);
            if (tabLabel != null) {
                drawRect(xPosition + 1, yPosition + height - 2, xPosition + width - 1, yPosition + height, 0xffc6c6c6);
            }
        }
        int x = xPosition + (tabLabel == null ? (width - 16) / 2 : 5);
        int y = yPosition + (tabLabel == null ? (height - 16) / 2 : 2);
        if (Objects.requireNonNull(icon) == Icon.CLEAR) {
            int size = Math.max(1, Math.min(width, height) - 2);
            RequestTableIcons
                    .draw(icon, xPosition + (width - size) / 2, yPosition + (height - size) / 2, size, enabled);
        } else {
            boolean small = width < 18 || height < 18;
            RequestTableIcons.draw(icon, small ? xPosition + 2 : x, small ? yPosition + 2 : y, small ? 8 : 16, enabled);
        }
        if (tabLabel != null) {
            mc.fontRenderer.drawString(
                    tabLabel,
                    xPosition + 25,
                    yPosition + TAB_LABEL_TOP,
                    enabled ? RequestTableGuiStyle.TEXT : RequestTableGuiStyle.MUTED);
        }
        if (fill >= 0) {
            int left = xPosition + (tabLabel == null ? 3 : 25);
            int barWidth = xPosition + width - 3 - left;
            int length = Math.round((barWidth - 2) * Math.max(0, Math.min(1, fill)));
            int top = tabLabel == null ? yPosition + height - 7
                    : yPosition + TAB_LABEL_TOP + TAB_LETTER_HEIGHT + TAB_FILL_GAP;
            drawRect(left, top, left + barWidth, top + 5, 0xff373737);
            drawRect(left + 1, top + 1, left + barWidth - 1, top + 4, 0xff555555);
            drawRect(left + 1, top + 1, left + 1 + length, top + 4, fillColor);
        }
    }

    enum Icon {
        NETWORK,
        NAME,
        AMOUNT,
        ASCENDING,
        DESCENDING,
        BOTH,
        STORED,
        CRAFTABLE,
        SEND,
        REQUEST,
        CLEAR,
        MESSAGES_ON,
        MESSAGES_OFF,
        SEARCH_STANDARD,
        SEARCH_AUTO,
        SEARCH_NEI_AUTO,
        SEARCH_NEI_STANDARD,
        SAVE_SEARCH_ON,
        SAVE_SEARCH_OFF,
        TERMINAL_SMALL,
        TERMINAL_TALL,
        ITEMS_ON,
        ITEMS_OFF,
        FLUIDS_ON,
        FLUIDS_OFF,
        UPGRADES,
        MONITOR_GRID,
        MONITOR_TREE
    }
}
