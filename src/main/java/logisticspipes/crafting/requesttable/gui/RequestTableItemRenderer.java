package logisticspipes.crafting.requesttable.gui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Changes only the count overlay while preserving native storage-slot and drag-preview rendering. */
@SideOnly(Side.CLIENT)
final class RequestTableItemRenderer extends RenderItem {

    private RenderItem delegate;
    private boolean fluid;

    void configure(RenderItem delegate, boolean fluid) {
        this.delegate = delegate;
        this.fluid = fluid;
    }

    @Override
    public void renderItemAndEffectIntoGUI(FontRenderer font, TextureManager textures, ItemStack stack, int x, int y) {
        float previousZ = delegate.zLevel;
        delegate.zLevel = zLevel;
        try {
            delegate.renderItemAndEffectIntoGUI(font, textures, stack, x, y);
        } finally {
            delegate.zLevel = previousZ;
        }
    }

    @Override
    public void renderItemOverlayIntoGUI(FontRenderer font, TextureManager textures, ItemStack stack, int x, int y,
            String alternateText) {
        float previousZ = delegate.zLevel;
        delegate.zLevel = zLevel;
        try {
            // Keep durability bars; fluid display items render their own amount in the item renderer.
            delegate.renderItemOverlayIntoGUI(font, textures, stack, x, y, "");
        } finally {
            delegate.zLevel = previousZ;
        }
        if (stack != null && !fluid && (stack.stackSize > 1 || alternateText != null)) {
            String amount = alternateText == null ? RequestTableGuiStyle.formatCount(stack.stackSize) : alternateText;
            RequestTableGuiStyle.drawCount(font, amount, x, y, 0xffffff, false);
        }
    }
}
