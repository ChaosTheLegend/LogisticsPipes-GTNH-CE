package logisticspipes.gui.modularUI.requesttable;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;

/**
 * A slot of the crafting grid of the request table. It holds real items like a workbench; while it is empty it shows
 * the ingredient the remembered recipe wants there, faded.
 */
public class RequestTableCraftSlot extends ItemSlot {

    // the fill of an item slot, laid half transparent over the ingredient to fade it
    private static final int FADE_COLOR = 0xa08b8b8b;

    // marks the slot whose ingredient a craft had none of
    private static final int MISSING_COLOR = 0x48ff3030;

    private final Supplier<ItemStack> ghost;
    private final BooleanSupplier missing;

    /**
     * @param ghost   the ingredient the recipe wants in the slot
     * @param missing whether a craft just failed for want of that ingredient
     */
    public RequestTableCraftSlot(Supplier<ItemStack> ghost, BooleanSupplier missing) {
        this.ghost = ghost;
        this.missing = missing;
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        ItemStack wanted = getSlot().getStack() == null ? ghost.get() : null;
        boolean marked = missing.getAsBoolean();
        if (wanted != null) {
            GuiDraw.drawItem(wanted, 1, 1, 16, 16, context.getCurrentDrawingZ());
        }
        if (wanted != null || marked) {
            // the item is drawn a hundred levels up, so what is laid over it ignores the depth
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            if (wanted != null) {
                GuiDraw.drawRect(1, 1, 16, 16, FADE_COLOR);
            }
            if (marked) {
                GuiDraw.drawRect(1, 1, 16, 16, MISSING_COLOR);
            }
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glColor4f(1, 1, 1, 1);
        }
        super.draw(context, widgetTheme);
    }
}
