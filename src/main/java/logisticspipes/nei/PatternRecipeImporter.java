package logisticspipes.nei;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.IUsageHandler;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.recipe.TemplateRecipeHandler;
import logisticspipes.crafting.pattern.DefaultPattern;
import logisticspipes.crafting.pattern.PatternRecipeImport;
import logisticspipes.crafting.pattern.ProcessingPattern;
import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.crafting.patternStack.PatternFluidStack;
import logisticspipes.crafting.patternStack.PatternItemStack;
import logisticspipes.crafting.patternStack.PatternStackHelper;
import logisticspipes.utils.FluidIdentifier;

/**
 * Converts NEI recipes into {@link PatternRecipeImport}s.
 * <p>
 * Crafting-table recipes keep their 3x3 shape and become crafting patterns; everything else becomes a processing
 * pattern with aggregated inputs and outputs. NEI fluid display stacks become fluid entries, while real fluid
 * containers (buckets, cells) stay items because the recipe consumes the container itself.
 */
public final class PatternRecipeImporter {

    private static final String CRAFTING_IDENT = "crafting";
    private static final String SMELTING_IDENT = "smelting";
    private static final String FUEL_IDENT = "fuel";
    private static final int GRID_LEFT = 25;
    private static final int GRID_TOP = 6;
    private static final int GRID_SLOT_SIZE = 18;
    private static final int GRID_SIZE = 3;

    private static String[] idents;
    private static int identHandlerCount = -1;

    private PatternRecipeImporter() {}

    /**
     * Returns every overlay identifier used by the registered NEI handlers, plus {@code null} for handlers without one.
     * <p>
     * Pattern GUIs accept any recipe, so they claim all identifiers. The list is rebuilt whenever the number of
     * registered handlers changes, since NEI may still be registering handlers when the first GUI opens.
     */
    public static String[] getAllIdents() {
        int handlerCount = GuiCraftingRecipe.craftinghandlers.size() + GuiCraftingRecipe.serialCraftingHandlers.size()
                + GuiUsageRecipe.usagehandlers.size();
        if (idents == null || handlerCount != identHandlerCount) {
            Set<String> collected = new LinkedHashSet<>();
            collected.add(null);
            collected.add(CRAFTING_IDENT);
            collected.add(SMELTING_IDENT);
            for (ICraftingHandler handler : GuiCraftingRecipe.craftinghandlers) {
                addIdent(collected, handler);
            }
            for (ICraftingHandler handler : GuiCraftingRecipe.serialCraftingHandlers) {
                addIdent(collected, handler);
            }
            for (IUsageHandler handler : GuiUsageRecipe.usagehandlers) {
                addIdent(collected, handler);
            }
            idents = collected.toArray(new String[0]);
            identHandlerCount = handlerCount;
        }
        return idents;
    }

    private static void addIdent(Set<String> collected, Object handler) {
        if (handler instanceof TemplateRecipeHandler templateHandler) {
            collected.add(templateHandler.getOverlayIdentifier());
        }
    }

    /**
     * Builds an import for the given recipe, or returns {@code null} when the recipe has no inputs or no outputs.
     */
    public static PatternRecipeImport fromRecipe(IRecipeHandler recipe, int recipeIndex) {
        String ident = recipe instanceof TemplateRecipeHandler templateHandler ? templateHandler.getOverlayIdentifier()
                : null;
        List<PositionedStack> ingredients = recipe.getIngredientStacks(recipeIndex);
        PatternRecipeImport craftingImport = CRAFTING_IDENT.equals(ident)
                ? tryCraftingGrid(ingredients, getOutputs(recipe, recipeIndex, ident, DefaultPattern.RESULT_SLOTS))
                : null;
        if (craftingImport != null) {
            return craftingImport;
        }
        List<IPatternStack> inputs = new ArrayList<>();
        for (PositionedStack ingredient : ingredients) {
            PatternStackHelper.addAggregated(inputs, toPatternStack(firstStack(ingredient)));
        }
        if (inputs.size() > ProcessingPattern.INGREDIENT_SLOTS) {
            inputs = new ArrayList<>(inputs.subList(0, ProcessingPattern.INGREDIENT_SLOTS));
        }
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            slots.add(i);
        }
        PatternRecipeImport processingImport = new PatternRecipeImport(
                true,
                inputs,
                slots,
                getOutputs(recipe, recipeIndex, ident, ProcessingPattern.RESULT_SLOTS));
        return processingImport.isEmpty() ? null : processingImport;
    }

    /**
     * Maps NEI's crafting-table positions onto the 3x3 pattern grid. Returns {@code null} if any ingredient does not
     * sit on the grid, so the caller falls back to a processing pattern.
     */
    private static PatternRecipeImport tryCraftingGrid(List<PositionedStack> ingredients, List<IPatternStack> outputs) {
        List<IPatternStack> inputs = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        Set<Integer> usedSlots = new LinkedHashSet<>();
        for (PositionedStack ingredient : ingredients) {
            IPatternStack stack = toPatternStack(firstStack(ingredient));
            if (stack == null) {
                continue;
            }
            int column = (ingredient.relx - GRID_LEFT) / GRID_SLOT_SIZE;
            int row = (ingredient.rely - GRID_TOP) / GRID_SLOT_SIZE;
            boolean onGrid = ingredient.relx >= GRID_LEFT && ingredient.rely >= GRID_TOP
                    && column < GRID_SIZE
                    && row < GRID_SIZE;
            int slot = row * GRID_SIZE + column;
            if (!onGrid || !usedSlots.add(slot)) {
                return null;
            }
            inputs.add(stack);
            slots.add(slot);
        }
        PatternRecipeImport craftingImport = new PatternRecipeImport(false, inputs, slots, outputs);
        return craftingImport.isEmpty() ? null : craftingImport;
    }

    private static List<IPatternStack> getOutputs(IRecipeHandler recipe, int recipeIndex, String ident, int limit) {
        List<IPatternStack> outputs = new ArrayList<>();
        PositionedStack result = recipe.getResultStack(recipeIndex);
        if (result != null) {
            PatternStackHelper.addAggregated(outputs, toPatternStack(firstStack(result)));
        }
        // the furnace and fuel handlers use the "other" stacks to animate the fuel, which is not an output
        if (!SMELTING_IDENT.equals(ident) && !FUEL_IDENT.equals(ident)) {
            List<PositionedStack> others = recipe.getOtherStacks(recipeIndex);
            if (others != null) {
                for (PositionedStack other : others) {
                    PatternStackHelper.addAggregated(outputs, toPatternStack(firstStack(other)));
                }
            }
        }
        return outputs.size() > limit ? new ArrayList<>(outputs.subList(0, limit)) : outputs;
    }

    private static ItemStack firstStack(PositionedStack positioned) {
        if (positioned == null) {
            return null;
        }
        if (positioned.items != null && positioned.items.length > 0 && positioned.items[0] != null) {
            return positioned.items[0];
        }
        return positioned.item;
    }

    /**
     * Converts one recipe stack. NEI fluid display stacks become fluids, real fluid containers stay items.
     */
    private static IPatternStack toPatternStack(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        if (!StackInfo.isFluidContainer(stack)) {
            FluidStack fluid = StackInfo.getFluid(stack);
            if (fluid != null && fluid.getFluid() != null && fluid.amount > 0) {
                return new PatternFluidStack(FluidIdentifier.get(fluid), fluid.amount);
            }
        }
        return PatternItemStack.fromItemStack(stack.copy());
    }
}
