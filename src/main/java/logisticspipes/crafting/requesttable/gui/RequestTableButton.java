package logisticspipes.crafting.requesttable.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Complete Minecraft bevels and isolated drawing state for every request-table button size. */
@SideOnly(Side.CLIENT)
public class RequestTableButton extends GuiButton {

    public RequestTableButton(int id, int x, int y, int width, int height, String label) {
        super(id, x, y, width, height, label);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) return;
        try (var ignored = RequestTableRender.guiState()) {
            field_146123_n = RequestTableRender.hovered(this, mouseX, mouseY);
            boolean hover = enabled && field_146123_n;
            RequestTableGuiStyle.raised(xPosition, yPosition, width, height, hover);
            drawContent(mc, hover);
        }
    }

    protected void drawContent(Minecraft mc, boolean hover) {
        mc.fontRenderer.drawString(
                displayString,
                xPosition + (width - mc.fontRenderer.getStringWidth(displayString)) / 2,
                yPosition + (height - 8) / 2,
                enabled ? RequestTableGuiStyle.TEXT : 0x808080);
    }
}
