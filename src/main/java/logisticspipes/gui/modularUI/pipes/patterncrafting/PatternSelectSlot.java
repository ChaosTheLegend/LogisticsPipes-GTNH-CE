package logisticspipes.gui.modularUI.pipes.patterncrafting;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;

import logisticspipes.renderer.PatternItemRenderer;

/**
 * One of the nine pattern item slots of the pattern crafting pipe.
 * <p>
 * A left click with an empty cursor on an unselected slot selects it for editing instead of picking the pattern up;
 * every other click behaves like a normal slot. Patterns render their primary result, like in the old GUI.
 */
public class PatternSelectSlot extends ItemSlot {

    private static final int SELECTED_COLOR = 0xff55aaff;
    private static final int WARNING_COLOR = 0xffe03030;

    private final int patternSlot;
    private final PatternEditorState state;
    private final PatternCraftingSyncHandler actions;
    private boolean pressConsumed;

    public PatternSelectSlot(int patternSlot, PatternEditorState state, PatternCraftingSyncHandler actions) {
        this.patternSlot = patternSlot;
        this.state = state;
        this.actions = actions;
    }

    private boolean isSelected() {
        return state.getSelectedSlot() == patternSlot;
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        pressConsumed = false;
        if (mouseButton == 0 && !isSelected()
                && !Interactable.hasShiftDown()
                && getSyncHandler().getSyncManager().getCursorItem() == null) {
            actions.select(patternSlot);
            pressConsumed = true;
            return Result.SUCCESS;
        }
        return super.onMousePressed(mouseButton);
    }

    @Override
    public boolean onMouseRelease(int mouseButton) {
        if (pressConsumed) {
            pressConsumed = false;
            return true;
        }
        return super.onMouseRelease(mouseButton);
    }

    @Override
    public void onMouseDrag(int mouseButton, long timeSinceClick) {
        if (!pressConsumed) {
            super.onMouseDrag(mouseButton, timeSinceClick);
        }
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (isSelected()) {
            GuiDraw.drawRect(1, 1, 16, 16, 0x3055aaff);
        }
        PatternItemRenderer.setForceResultRender(true);
        try {
            super.draw(context, widgetTheme);
        } finally {
            PatternItemRenderer.clearForceResultRender();
        }
    }

    @Override
    protected void drawOverlay() {
        super.drawOverlay();
        if (isSelected()) {
            GuiDraw.drawBorderInsideXYWH(0, 0, 18, 18, 1, SELECTED_COLOR);
        }
        if (actions.isPatternUnsupported(patternSlot)) {
            GuiDraw.drawRect(1, 1, 4, 4, WARNING_COLOR);
        }
    }

    @Override
    public void buildTooltip(ItemStack stack, RichTooltip tooltip) {
        super.buildTooltip(stack, tooltip);
        if (actions.isPatternUnsupported(patternSlot)) {
            tooltip.addLine(IKey.lang("gui.patterncrafting.status.fluidupgrade").style(EnumChatFormatting.RED));
        }
    }
}
