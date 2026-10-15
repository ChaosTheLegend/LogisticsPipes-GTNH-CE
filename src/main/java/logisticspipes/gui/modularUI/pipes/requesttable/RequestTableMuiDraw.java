package logisticspipes.gui.modularUI.pipes.requesttable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import logisticspipes.crafting.requesttable.gui.RequestTableGuiStyle;
import logisticspipes.crafting.requesttable.gui.RequestTableRender;

/** Client drawing helpers of the ModularUI request table, matching the legacy GUI's look. */
final class RequestTableMuiDraw {

    private static RenderItem itemRenderer;

    private RequestTableMuiDraw() {}

    private static RenderItem itemRenderer() {
        if (itemRenderer == null) itemRenderer = new RenderItem();
        return itemRenderer;
    }

    /**
     * Draws a stack like a vanilla slot, with a compact count for stacks above the normal size.
     *
     * @param showCount {@code false} for fluid display items, which draw their own amount
     */
    static void slotStack(ItemStack stack, int x, int y, boolean showCount) {
        if (stack == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = stack.getItem().getFontRenderer(stack);
        if (font == null) font = mc.fontRenderer;
        try (var ignored = RequestTableRender.guiState()) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            RenderHelper.enableGUIStandardItemLighting();
            RenderItem renderer = itemRenderer();
            renderer.zLevel = 100.0F;
            renderer.renderItemAndEffectIntoGUI(font, mc.getTextureManager(), stack, x, y);
            // Keep durability bars; counts are drawn below in the request table's compact style.
            renderer.renderItemOverlayIntoGUI(font, mc.getTextureManager(), stack, x, y, "");
            renderer.zLevel = 0.0F;
            RenderHelper.disableStandardItemLighting();
        }
        if (showCount && stack.stackSize > 1) {
            RequestTableGuiStyle.drawCount(
                    mc.fontRenderer,
                    RequestTableGuiStyle.formatCount(stack.stackSize),
                    x,
                    y,
                    0xffffff,
                    false);
        }
    }

    /** Vanilla's white slot hover highlight. */
    static void slotHighlight(int x, int y) {
        try (var ignored = RequestTableRender.guiState()) {
            GL11.glTranslatef(0, 0, 200);
            Gui.drawRect(x, y, x + 16, y + 16, 0x80ffffff);
        }
    }

    /** The legacy scrollbar: an inset track with a raised thumb. */
    static void scrollbar(int height, int thumbTop, int thumbHeight) {
        try (var ignored = RequestTableRender.guiState()) {
            RequestTableGuiStyle.inset(0, 0, 7, height);
            RequestTableGuiStyle.raised(0, thumbTop, 7, thumbHeight, false);
        }
    }

    static void text(String text, int x, int y, int color) {
        try (var ignored = RequestTableRender.guiState()) {
            Minecraft.getMinecraft().fontRenderer.drawString(text, x, y, color);
        }
    }
}
