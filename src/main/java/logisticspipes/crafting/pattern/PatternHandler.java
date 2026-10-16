package logisticspipes.crafting.pattern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.item.ItemStack;

import logisticspipes.LogisticsPipes;
import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.crafting.patternStack.PatternStackHelper;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.item.SimpleStackInventory;

public class PatternHandler {

    private final SimpleStackInventory patternInventory;
    private final Map<ItemStack, PatternRecipeSnapshot> recipesByStack = new IdentityHashMap<>();
    private List<PatternRecipeSnapshot> recipesBySlot = Collections.emptyList();
    private List<ItemStack> configuredPatterns = Collections.emptyList();
    private Set<ItemIdentifier> ingredientItems = Collections.emptySet();
    private Set<ItemIdentifier> outputItems = Collections.emptySet();
    private Set<ItemIdentifier> nonFluidOutputItems = Collections.emptySet();
    private Set<ItemIdentifier> craftedItems = Collections.emptySet();
    private Set<ItemIdentifier> nonFluidCraftedItems = Collections.emptySet();
    private List<ItemIdentifierStack> craftResults = Collections.emptyList();
    private List<ItemIdentifierStack> nonFluidCraftResults = Collections.emptyList();
    private boolean cacheDirty = true;
    private long changeVersion;

    public PatternHandler(SimpleStackInventory patternInventory) {
        this.patternInventory = patternInventory;
    }

    public int size() {
        return patternInventory.getSizeInventory();
    }

    /** Invalidates all parsed pattern data after the backing inventory or pattern NBT changed. */
    public void invalidate() {
        cacheDirty = true;
        changeVersion++;
    }

    public long getChangeVersion() {
        return changeVersion;
    }

    public ItemStack getConfiguredPatternStack(int slot) {
        if (slot < 0 || slot >= size()) {
            return null;
        }
        PatternRecipeSnapshot recipe = getRecipe(slot);
        return recipe == null ? null : recipe.getPatternStack();
    }

    public List<ItemStack> getConfiguredPatterns() {
        ensureCache();
        return configuredPatterns;
    }

    public PatternRecipeSnapshot getRecipe(int slot) {
        if (slot < 0 || slot >= size()) {
            return null;
        }
        ensureCache();
        return recipesBySlot.get(slot);
    }

    public PatternRecipeSnapshot getRecipe(ItemStack pattern) {
        if (pattern == null) {
            return null;
        }
        ensureCache();
        PatternRecipeSnapshot recipe = recipesByStack.get(pattern);
        if (recipe != null || pattern.getItem() != LogisticsPipes.LogisticsPattern) {
            return recipe;
        }
        PatternRecipeSnapshot uncached = new PatternRecipeSnapshot(pattern);
        return uncached.isConfigured() ? uncached : null;
    }

    public Set<ItemIdentifier> getIngredientItems() {
        ensureCache();
        return ingredientItems;
    }

    /** Includes byproducts so routing can discover extras without advertising them as craft recipes. */
    public Set<ItemIdentifier> getOutputItems(boolean fluidCraftingSupported) {
        ensureCache();
        return fluidCraftingSupported ? outputItems : nonFluidOutputItems;
    }

    public Set<ItemIdentifier> getCraftedItems(boolean fluidCraftingSupported) {
        ensureCache();
        return fluidCraftingSupported ? craftedItems : nonFluidCraftedItems;
    }

    public List<ItemIdentifierStack> getCraftResults(boolean fluidCraftingSupported) {
        ensureCache();
        return fluidCraftingSupported ? craftResults : nonFluidCraftResults;
    }

    public boolean isIngredient(ItemIdentifier item) {
        FluidIdentifier fluid = item != null && item.isFluidContainer() ? FluidIdentifier.get(item) : null;
        if (fluid != null) {
            return isFluidIngredient(fluid);
        }
        return getIngredientItems().contains(item);
    }

    boolean isFluidIngredient(FluidIdentifier fluid) {
        for (ItemStack pattern : getConfiguredPatterns()) {
            if (fluidIngredientAmount(pattern, fluid) > 0) {
                return true;
            }
        }
        return false;
    }

    public int findPatternSlotForResult(ItemIdentifier item) {
        for (int slot = 0; slot < size(); slot++) {
            ItemStack pattern = getConfiguredPatternStack(slot);
            if (pattern == null) {
                continue;
            }
            if (resultAmount(slot, item) > 0) {
                return slot;
            }
        }
        return -1;
    }

    public int resultAmount(int patternSlot, ItemIdentifier item) {
        PatternRecipeSnapshot recipe = getRecipe(patternSlot);
        if (recipe == null || item == null) {
            return 0;
        }
        int amount = 0;
        for (IPatternStack result : recipe.getCraftableOutputs()) {
            if (PatternStackHelper.matches(result, item)) {
                amount += result.getAmount();
            }
        }
        return amount;
    }

    public int fluidIngredientAmount(ItemStack pattern, FluidIdentifier fluid) {
        PatternRecipeSnapshot recipe = getRecipe(pattern);
        return recipe == null ? 0 : recipe.getFluidIngredientAmount(fluid);
    }

    public List<IPatternStack> getAggregatedInputs(ItemStack pattern) {
        PatternRecipeSnapshot recipe = getRecipe(pattern);
        return recipe == null ? Collections.emptyList() : recipe.getAggregatedInputs();
    }

    private void ensureCache() {
        if (!cacheDirty) {
            return;
        }
        recipesByStack.clear();
        List<PatternRecipeSnapshot> slotRecipes = new ArrayList<>(size());
        List<ItemStack> patterns = new ArrayList<>();
        Set<ItemIdentifier> items = new TreeSet<>();
        Set<ItemIdentifier> allOutputs = new TreeSet<>();
        Set<ItemIdentifier> nonFluidOutputs = new TreeSet<>();
        Set<ItemIdentifier> results = new TreeSet<>();
        Set<ItemIdentifier> nonFluidResults = new TreeSet<>();
        List<ItemIdentifierStack> displayResults = new ArrayList<>();
        List<ItemIdentifierStack> nonFluidDisplayResults = new ArrayList<>();
        for (int slot = 0; slot < size(); slot++) {
            ItemStack stack = patternInventory.getStackInSlot(slot);
            PatternRecipeSnapshot recipe = null;
            if (stack != null && stack.getItem() == LogisticsPipes.LogisticsPattern) {
                PatternRecipeSnapshot candidate = new PatternRecipeSnapshot(stack);
                if (candidate.isConfigured()) {
                    recipe = candidate;
                    recipesByStack.put(stack, recipe);
                    patterns.add(stack);
                    for (IPatternStack ingredient : recipe.getInputs()) {
                        ItemIdentifier item = PatternStackHelper.getRoutingItem(ingredient);
                        if (item != null) {
                            items.add(item);
                        }
                    }
                    for (IPatternStack output : recipe.getOutputs()) {
                        ItemIdentifier result = PatternStackHelper.getRoutingItem(output);
                        if (result != null) {
                            allOutputs.add(result);
                            if (!recipe.containsFluid()) {
                                nonFluidOutputs.add(result);
                            }
                        }
                    }
                    for (IPatternStack output : recipe.getCraftableOutputs()) {
                        ItemIdentifier result = PatternStackHelper.getRoutingItem(output);
                        if (result != null) {
                            results.add(result);
                            if (!recipe.containsFluid()) {
                                nonFluidResults.add(result);
                            }
                        }
                        ItemIdentifierStack display = PatternStackHelper.makeDisplayStack(output);
                        if (display != null) {
                            displayResults.add(display);
                            if (!recipe.containsFluid()) {
                                nonFluidDisplayResults.add(display);
                            }
                        }
                    }
                }
            }
            slotRecipes.add(recipe);
        }
        recipesBySlot = Collections.unmodifiableList(slotRecipes);
        configuredPatterns = Collections.unmodifiableList(patterns);
        ingredientItems = Collections.unmodifiableSet(items);
        outputItems = Collections.unmodifiableSet(allOutputs);
        nonFluidOutputItems = Collections.unmodifiableSet(nonFluidOutputs);
        craftedItems = Collections.unmodifiableSet(results);
        nonFluidCraftedItems = Collections.unmodifiableSet(nonFluidResults);
        craftResults = Collections.unmodifiableList(displayResults);
        nonFluidCraftResults = Collections.unmodifiableList(nonFluidDisplayResults);
        cacheDirty = false;
    }

}
