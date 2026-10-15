package logisticspipes.crafting.requesttable.gui;

import net.minecraft.client.Minecraft;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** A small CRT and power lamp enabled by the table's permanent monitoring upgrade. */
@SideOnly(Side.CLIENT)
public final class RequestTableMonitorButton extends RequestTableButton {

    private boolean selected;

    RequestTableMonitorButton(int id, RequestTableLayout layout) {
        super(
                id,
                layout.monitorButtonX,
                layout.monitorButtonY,
                layout.monitorButtonWidth,
                layout.monitorButtonHeight,
                "");
    }

    void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    protected void drawContent(Minecraft mc, boolean hover) {
        paintContent(xPosition, yPosition, width, height, enabled, selected);
    }

    /** Draws the monitor button body over its raised bevel; shared with the ModularUI terminal. */
    public static void paintContent(int xPosition, int yPosition, int width, int height, boolean enabled,
            boolean selected) {
        if (!enabled) {
            drawRect(xPosition + 2, yPosition + 2, xPosition + width - 2, yPosition + height - 2, 0xffa0a0a0);
        } else if (selected) {
            drawRect(xPosition + 1, yPosition + 1, xPosition + width - 1, yPosition + 2, 0xffe8c66a);
        }
        RequestTableIcons.monitor(xPosition + (width - 16) / 2, yPosition + 3, enabled, true);
        drawRect(xPosition + 3, yPosition + height - 5, xPosition + 6, yPosition + height - 2, 0xff373737);
        drawRect(
                xPosition + 4,
                yPosition + height - 4,
                xPosition + 5,
                yPosition + height - 3,
                enabled ? RequestTableGuiStyle.MONITOR_POWER_ON : RequestTableGuiStyle.MONITOR_POWER_OFF);
    }
}
