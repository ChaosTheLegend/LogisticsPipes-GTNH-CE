package logisticspipes.gui.modularUI.pipes.requesttable;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerIngredientProvider;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;

import logisticspipes.crafting.requesttable.gui.RequestTableRender;
import logisticspipes.utils.gui.GuiGraphics;

/**
 * Output of the ghost crafting grid. Clicking crafts from the table storage and the player's inventory; the server
 * performs the craft and updates the cursor. Shift-click crafts up to a stack into the player's inventory.
 */
public class RequestTableResultWidget extends Widget<RequestTableResultWidget>
        implements Interactable, RecipeViewerIngredientProvider {

    /** The big slot background is 26 pixels; the item sits five pixels inside. */
    public static final int SIZE = 26;
    private static final int ITEM_OFFSET = 5;

    private final RequestTableMui gui;
    // NEI queries the hovered stack outside of draw and input passes, where the mouse is not widget-local.
    private int hoverX = -1;
    private int hoverY = -1;

    public RequestTableResultWidget(RequestTableMui gui) {
        this.gui = gui;
        size(SIZE, SIZE);
        tooltipAutoUpdate(true).tooltipBuilder(tooltip -> {
            ItemStack result = getResult();
            if (result != null && isOverItem()) tooltip.addFromItem(result);
        });
    }

    private ItemStack getResult() {
        return gui.getTable().resultInv.getStackInSlot(0);
    }

    private boolean isOverItem() {
        return isHovering() && RequestTableRender.inside(hoverX, hoverY, ITEM_OFFSET - 1, ITEM_OFFSET - 1, 18, 18);
    }

    @Override
    public @Nullable ItemStack getStackForRecipeViewer() {
        return isOverItem() ? getResult() : null;
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        hoverX = getContext().getMouseX();
        hoverY = getContext().getMouseY();
        if (!isOverItem() || !gui.getActions().isInventoryReady() || getResult() == null) return Result.STOP;
        if (mouseButton != 0 && mouseButton != 1) return Result.STOP;
        gui.getActions().clickCraftingResult(mouseButton, Interactable.hasShiftDown());
        return Result.SUCCESS;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {}

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        hoverX = context.getMouseX();
        hoverY = context.getMouseY();
        try (var ignored = RequestTableRender.guiState()) {
            GuiGraphics.drawBigSlotBackground(Minecraft.getMinecraft(), 0, 0);
        }
        RequestTableMuiDraw.slotStack(getResult(), ITEM_OFFSET, ITEM_OFFSET, true);
        if (isOverItem()) RequestTableMuiDraw.slotHighlight(ITEM_OFFSET, ITEM_OFFSET);
    }

    @Override
    public void drawOverlay(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {}
}
