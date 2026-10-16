package logisticspipes.nei;

import java.util.ArrayList;
import java.util.List;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiOverlayButton.ItemOverlayState;
import codechicken.nei.recipe.IRecipeHandler;
import logisticspipes.crafting.pattern.PatternRecipeImport;
import logisticspipes.gui.modularUI.pipes.patterncrafting.PatternEditorSyncHandler;

/**
 * NEI recipe transfer into the pattern editor GUIs (pattern crafting pipe and handheld pattern).
 */
public final class PatternCraftingRecipeTransfer {

    private PatternCraftingRecipeTransfer() {}

    public static String[] getIdents() {
        return PatternRecipeImporter.getAllIdents();
    }

    public static int transfer(PatternEditorSyncHandler syncHandler, IRecipeHandler recipe, int recipeIndex) {
        PatternRecipeImport recipeImport = PatternRecipeImporter.fromRecipe(recipe, recipeIndex);
        if (recipeImport == null) {
            return 0;
        }
        syncHandler.importRecipe(recipeImport);
        return 1;
    }

    /**
     * Patterns only store a description of the recipe, so ingredients never have to be present in the inventory.
     * Marking every ingredient as present keeps NEI from flagging the transfer button as "missing items".
     */
    public static List<ItemOverlayState> presenceOverlay(IRecipeHandler recipe, int recipeIndex) {
        List<ItemOverlayState> states = new ArrayList<>();
        for (PositionedStack ingredient : recipe.getIngredientStacks(recipeIndex)) {
            states.add(new ItemOverlayState(ingredient, true));
        }
        return states;
    }
}
