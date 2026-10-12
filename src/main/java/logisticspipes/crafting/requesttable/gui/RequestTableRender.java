package logisticspipes.crafting.requesttable.gui;

import java.nio.IntBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.utils.gui.GuiGraphics;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.item.ItemStackRenderer;
import logisticspipes.utils.item.ItemStackRenderer.DisplayAmount;

/** Shared drawing primitives for the request table, its popup and the upgrade board. */
@SideOnly(Side.CLIENT)
public final class RequestTableRender {

    // GUI drawing happens on the client render thread.
    private static final IntBuffer SCISSOR_BOX = BufferUtils.createIntBuffer(4);

    private RequestTableRender() {
    }

    public static ResourceLocation texture(String path) {
        return new ResourceLocation("logisticspipes", "textures/gui/requesttable/" + path + ".png");
    }

    public static State guiState() {
        return new State();
    }

    public static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public static boolean hovered(GuiButton button, int mouseX, int mouseY) {
        return button != null && button.visible
            && inside(mouseX, mouseY, button.xPosition, button.yPosition, button.width, button.height);
    }

    public static void slotGrid(Minecraft mc, int x, int y, int columns, int rows) {
        slotGrid(mc, x, y, columns, rows, columns * rows);
    }

    /** Supports partial final rows without drawing slots beyond the inventory's size. */
    public static void slotGrid(Minecraft mc, int x, int y, int columns, int rows, int slots) {
        int count = Math.min(slots, columns * rows);
        for (int i = 0; i < count; i++) {
            GuiGraphics.drawSlotBackground(
                mc,
                x + (i % columns) * RequestTableLayout.SLOT,
                y + (i / columns) * RequestTableLayout.SLOT);
        }
    }

    public static void item(ItemStack stack, int x, int y, float z, boolean ignoreDepth) {
        item(stack, x, y, z, ignoreDepth, true);
    }

    public static void item(ItemStack stack, int x, int y, float z, boolean ignoreDepth, boolean colored) {
        if (stack == null) return;
        try (var ignored = guiState()) {
            // Match vanilla inventory lighting: block models scale their normals along with the geometry.
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            // Native chest models need writes to the depth buffer for the lid to occlude the base.
            GL11.glDepthMask(true);
            new ItemStackRenderer(x, y, z, true, ignoreDepth, colored)
                .setItemIdentifierStack(ItemIdentifierStack.getFromStack(stack))
                .setDisplayAmount(DisplayAmount.NEVER).renderInGui();
        }
    }

    public static void texture(ResourceLocation texture, int x, int y, int width, int height) {
        textureRegion(texture, x, y, width, height, 0, 0, 1, 1);
    }

    /** Reveals native rows, including within a vertical animation strip, without stretching the visible part. */
    public static void textureRows(ResourceLocation texture, int x, int y, int width, int height, int visibleRows,
                                   int frame, int frames) {
        int rows = Math.min(height, visibleRows);
        if (rows <= 0) return;
        textureRegion(
            texture,
            x,
            y,
            width,
            rows,
            0,
            (double) frame / frames,
            1,
            (frame + (double) rows / height) / frames);
    }

    public static void textureRegion(ResourceLocation texture, int x, int y, int width, int height, double u0,
                                     double v0, double u1, double v1) {
        textureRegion(texture, x, y, width, height, u0, v0, u1, v1, 1.0F);
    }

    public static void textureRegion(ResourceLocation texture, int x, int y, int width, int height, double u0,
                                     double v0, double u1, double v1, float brightness) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(brightness, brightness, brightness, 1);
        var tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, 0, u0, v1);
        tessellator.addVertexWithUV(x + width, y + height, 0, u1, v1);
        tessellator.addVertexWithUV(x + width, y, 0, u1, v0);
        tessellator.addVertexWithUV(x, y, 0, u0, v0);
        tessellator.draw();
    }

    /** Clips inside any existing scissor instead of widening a parent's clip region. */
    public static void clip(Minecraft mc, int x, int y, int width, int height) {
        int scale = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
        int left = Math.max(0, x * scale);
        int bottom = Math.max(0, mc.displayHeight - (y + height) * scale);
        int right = Math.min(mc.displayWidth, (x + width) * scale);
        int top = Math.min(mc.displayHeight, mc.displayHeight - y * scale);
        if (GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) {
            SCISSOR_BOX.clear();
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, SCISSOR_BOX);
            left = Math.max(left, SCISSOR_BOX.get(0));
            bottom = Math.max(bottom, SCISSOR_BOX.get(1));
            right = Math.min(right, SCISSOR_BOX.get(0) + SCISSOR_BOX.get(2));
            top = Math.min(top, SCISSOR_BOX.get(1) + SCISSOR_BOX.get(3));
        }
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(left, bottom, Math.max(0, right - left), Math.max(0, top - bottom));
    }

    /** Restores both attributes and the model-view matrix, including on a rendering exception. */
    public static final class State implements AutoCloseable {

        private State() {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glColor4f(1, 1, 1, 1);
        }

        @Override
        public void close() {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
