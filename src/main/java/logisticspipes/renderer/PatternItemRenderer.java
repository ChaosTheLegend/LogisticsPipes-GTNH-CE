package logisticspipes.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import logisticspipes.LogisticsPipes;
import logisticspipes.crafting.pattern.ItemPattern;

public class PatternItemRenderer implements IItemRenderer {

    private static final ThreadLocal<Boolean> FORCE_RESULT_RENDER = new ThreadLocal<>();

    private final RenderItem renderItem = new RenderItem();

    public static void setForceResultRender(boolean forceResultRender) {
        FORCE_RESULT_RENDER.set(forceResultRender);
    }

    public static void clearForceResultRender() {
        FORCE_RESULT_RENDER.remove();
    }

    @Override
    public boolean handleRenderType(ItemStack itemStack, ItemRenderType rendererType) {
        if (rendererType != ItemRenderType.INVENTORY || itemStack == null
                || itemStack.getItem() != LogisticsPipes.LogisticsPattern) {
            return false;
        }
        if (!isForceResultRender() && !isShiftPressed()) {
            return false;
        }
        return ItemPattern.fromStack(itemStack).getPrimaryResultStack() != null;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return false;
    }

    @Override
    public void renderItem(ItemRenderType renderType, ItemStack itemStack, Object... data) {
        ItemStack result = ItemPattern.fromStack(itemStack).getPrimaryResultStack();
        if (result == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        RenderHelper.enableGUIStandardItemLighting();
        renderItem.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), result, 0, 0);
        renderItem.renderItemOverlayIntoGUI(mc.fontRenderer, mc.getTextureManager(), result, 0, 0);
        RenderHelper.disableStandardItemLighting();
        GL11.glPopAttrib();
    }

    private boolean isForceResultRender() {
        Boolean force = FORCE_RESULT_RENDER.get();
        return force != null && force;
    }

    private boolean isShiftPressed() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }
}
