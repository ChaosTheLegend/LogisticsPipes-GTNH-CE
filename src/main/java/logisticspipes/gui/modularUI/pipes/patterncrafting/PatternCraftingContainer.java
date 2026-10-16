package logisticspipes.gui.modularUI.pipes.patterncrafting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.gui.inventory.GuiContainer;

import com.cleanroommc.modularui.integration.nei.INEIRecipeTransfer;
import com.cleanroommc.modularui.screen.ModularContainer;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiOverlayButton;
import codechicken.nei.recipe.IRecipeHandler;
import logisticspipes.nei.PatternCraftingRecipeTransfer;

/**
 * Container of the pattern editor GUIs (pattern crafting pipe and handheld pattern). Implementing
 * {@link INEIRecipeTransfer} lets ModularUI route NEI's recipe transfer ("+" button with shift) of every recipe type to
 * the selected pattern.
 * <p>
 * The NEI logic lives in {@link PatternCraftingRecipeTransfer}, which is only loaded on the client.
 */
public class PatternCraftingContainer extends ModularContainer implements INEIRecipeTransfer<GuiContainer> {

    private final PatternEditorSyncHandler syncHandler;

    public PatternCraftingContainer(PatternEditorSyncHandler syncHandler) {
        this.syncHandler = syncHandler;
    }

    /**
     * Container factory for {@code UISettings#customContainer}. Kept here so GUI code never references this class
     * directly: {@link INEIRecipeTransfer} uses client-only classes and must not be loaded on a dedicated server.
     */
    public static Supplier<ModularContainer> supplier(PatternEditorSyncHandler syncHandler) {
        return () -> new PatternCraftingContainer(syncHandler);
    }

    @Override
    public String[] getIdents() {
        return PatternCraftingRecipeTransfer.getIdents();
    }

    @Override
    public int transferRecipe(GuiContainer gui, IRecipeHandler recipe, int recipeIndex, int multiplier) {
        return PatternCraftingRecipeTransfer.transfer(syncHandler, recipe, recipeIndex);
    }

    @Override
    public List<GuiOverlayButton.ItemOverlayState> presenceOverlay(GuiContainer gui, IRecipeHandler recipe,
            int recipeIndex) {
        return PatternCraftingRecipeTransfer.presenceOverlay(recipe, recipeIndex);
    }

    @Override
    public ArrayList<PositionedStack> positionStacks(GuiContainer gui, ArrayList<PositionedStack> stacks) {
        // pattern slots are phantom widgets, not vanilla slots, so NEI's ghost overlay has nothing to draw on
        return new ArrayList<>();
    }
}
