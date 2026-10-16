package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import logisticspipes.crafting.pattern.PatternHandler;
import logisticspipes.crafting.pattern.PatternRecipeSnapshot;
import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.crafting.patternStack.PatternStackHelper;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * Resolves pattern ingredient targets and builds concrete plans from the module buffer.
 * <p>
 * Flexible OreDict/NBT matching and per-slot satellite routing form one cohesive planning concern. Keeping that logic
 * here prevents the module lifecycle from being mixed with recipe matching details. Resolved target lists are memoized
 * for the current world tick; pattern NBT itself is provided by {@link PatternHandler}'s longer-lived snapshot cache.
 */
final class PatternCraftingIngredientPlanner {

    private final ModulePatternCrafting module;
    private final PipeItemsPatternCraftingLogistics pipe;
    private final PatternHandler patternHandler;
    private final AdjacentInventoryHandler adjacentInventory;
    private final PatternStackBufferHandler ingredientBuffer;
    private final PatternStackRequestHandler requestedIngredient;
    private final Map<ItemStack, TargetPlan> targetPlans = new IdentityHashMap<>();
    private final Map<PatternCraftingReference, CompleteSetsCache> completeSetsByOwner = new java.util.HashMap<>();
    private long targetPlanTick = Long.MIN_VALUE;

    PatternCraftingIngredientPlanner(ModulePatternCrafting module, PipeItemsPatternCraftingLogistics pipe,
            PatternHandler patternHandler, AdjacentInventoryHandler adjacentInventory,
            PatternStackBufferHandler ingredientBuffer, PatternStackRequestHandler requestedIngredient) {
        this.module = module;
        this.pipe = pipe;
        this.patternHandler = patternHandler;
        this.adjacentInventory = adjacentInventory;
        this.ingredientBuffer = ingredientBuffer;
        this.requestedIngredient = requestedIngredient;
    }

    void invalidate() {
        targetPlans.clear();
        completeSetsByOwner.clear();
        targetPlanTick = Long.MIN_VALUE;
    }

    List<PatternIngredientTarget> getIngredientTargets(ItemStack pattern) {
        TargetPlan plan = getTargetPlan(pattern);
        return plan == null ? Collections.emptyList() : plan.ingredients;
    }

    List<IPatternStack> getAggregatedIngredients(ItemStack pattern) {
        TargetPlan plan = getTargetPlan(pattern);
        return plan == null ? Collections.emptyList() : plan.aggregatedIngredients;
    }

    List<IPatternStack> getLocalAggregatedIngredients(ItemStack pattern) {
        TargetPlan plan = getTargetPlan(pattern);
        return plan == null ? Collections.emptyList() : plan.localAggregatedIngredients;
    }

    IRequestItems getSatelliteTargetForInputSlot(ItemStack pattern, int inputSlot) {
        for (PatternIngredientTarget target : getIngredientTargets(pattern)) {
            if (target.inputSlot() == inputSlot) {
                return target.itemTarget();
            }
        }
        return null;
    }

    IRequestFluid getFluidSatelliteTargetForInputSlot(ItemStack pattern, int inputSlot) {
        for (PatternIngredientTarget target : getIngredientTargets(pattern)) {
            if (target.inputSlot() == inputSlot) {
                return target.fluidTarget();
            }
        }
        return null;
    }

    boolean hasLinkedSatelliteAssignment(ItemStack pattern, int inputSlot) {
        for (PatternIngredientTarget target : getIngredientTargets(pattern)) {
            if (target.inputSlot() == inputSlot) {
                return target.itemTarget() != null || target.fluidTarget() != null;
            }
        }
        return false;
    }

    boolean hasLinkedSatelliteAssignments(ItemStack pattern) {
        TargetPlan plan = getTargetPlan(pattern);
        return plan != null && plan.hasSatelliteAssignments;
    }

    /**
     * Whether any fluid input of the pattern goes into the adjacent target rather than to a satellite.
     */
    boolean hasLocalFluidIngredients(ItemStack pattern) {
        TargetPlan plan = getTargetPlan(pattern);
        return plan != null && PatternStackHelper.containsFluid(plan.localAggregatedIngredients);
    }

    int ingredientAmount(ItemStack pattern, ItemIdentifier item) {
        TargetPlan plan = getTargetPlan(pattern);
        if (plan == null || item == null) {
            return 0;
        }
        Integer cached = plan.itemAmounts.get(item);
        if (cached != null) {
            return cached;
        }
        int amount = 0;
        for (PatternIngredientTarget ingredient : plan.ingredients) {
            if (ingredientMatchesItem(pattern, ingredient.stack(), item)) {
                amount += ingredient.stack().getAmount();
            }
        }
        plan.itemAmounts.put(item, amount);
        return amount;
    }

    private boolean ingredientMatchesItem(ItemStack pattern, IPatternStack ingredient, ItemIdentifier item) {
        ItemIdentifierStack expected = PatternStackHelper.asSolidStack(ingredient);
        PatternRecipeSnapshot recipe = patternHandler.getRecipe(pattern);
        return expected != null && itemMatchesPatternIngredient(recipe, expected.getItem(), item);
    }

    int bufferedIngredientAmount(int patternSlot, ItemStack pattern, IPatternStack ingredient) {
        int amount = 0;
        List<IPatternStack> buffered = ingredientBuffer.asMap().get(patternSlot);
        if (buffered == null) {
            return 0;
        }
        for (IPatternStack stack : buffered) {
            if (ingredientMatchesStack(pattern, ingredient, stack)) {
                amount += stack.getAmount();
            }
        }
        return amount;
    }

    int bufferedItemAmount(int patternSlot, ItemStack pattern, ItemIdentifier item) {
        int amount = 0;
        List<IPatternStack> buffered = ingredientBuffer.asMap().get(patternSlot);
        if (buffered == null) {
            return 0;
        }
        PatternRecipeSnapshot recipe = patternHandler.getRecipe(pattern);
        for (IPatternStack stack : buffered) {
            ItemIdentifierStack bufferedItem = PatternStackHelper.asSolidStack(stack);
            if (bufferedItem != null && ingredientAmount(pattern, bufferedItem.getItem()) > 0
                    && ingredientAmount(pattern, item) > 0
                    && itemMatchesPatternIngredient(recipe, bufferedItem.getItem(), item)) {
                amount += stack.getAmount();
            }
        }
        return amount;
    }

    int requestedIngredientAmount(int patternSlot, ItemStack pattern, IPatternStack ingredient) {
        return requestedIngredient
                .amountMatching(patternSlot, requested -> ingredientMatchesStack(pattern, ingredient, requested));
    }

    int requestedItemAmount(int patternSlot, ItemStack pattern, ItemIdentifier item) {
        PatternRecipeSnapshot recipe = patternHandler.getRecipe(pattern);
        return requestedIngredient.amountMatching(patternSlot, requested -> {
            ItemIdentifierStack requestedItem = PatternStackHelper.asSolidStack(requested);
            return requestedItem != null && ingredientAmount(pattern, requestedItem.getItem()) > 0
                    && ingredientAmount(pattern, item) > 0
                    && itemMatchesPatternIngredient(recipe, requestedItem.getItem(), item);
        });
    }

    int requestedItemAmount(PatternCraftingReference owner, ItemStack pattern, ItemIdentifier item) {
        PatternRecipeSnapshot recipe = patternHandler.getRecipe(pattern);
        return requestedIngredient.amountMatching(owner, requested -> {
            ItemIdentifierStack requestedItem = PatternStackHelper.asSolidStack(requested);
            return requestedItem != null && ingredientAmount(pattern, requestedItem.getItem()) > 0
                    && ingredientAmount(pattern, item) > 0
                    && itemMatchesPatternIngredient(recipe, requestedItem.getItem(), item);
        });
    }

    int removeRequestedItem(PatternCraftingReference owner, int patternSlot, ItemStack pattern, ItemIdentifier item,
            int amount) {
        PatternRecipeSnapshot recipe = patternHandler.getRecipe(pattern);
        return requestedIngredient.removeMatching(owner, patternSlot, amount, requested -> {
            ItemIdentifierStack requestedItem = PatternStackHelper.asSolidStack(requested);
            return requestedItem != null && ingredientAmount(pattern, requestedItem.getItem()) > 0
                    && ingredientAmount(pattern, item) > 0
                    && itemMatchesPatternIngredient(recipe, requestedItem.getItem(), item);
        });
    }

    boolean requiresConcreteIngredientPlanning(ItemStack pattern) {
        PatternRecipeSnapshot recipe = patternHandler.getRecipe(pattern);
        return recipe != null && (recipe.isOreDictSubstitutionEnabled() || recipe.isIgnoreNbtEnabled());
    }

    List<PatternIngredientAssignment> buildBufferedIngredientPlan(int patternSlot, ItemStack pattern, int sets) {
        return buildBufferedIngredientPlan(patternSlot, pattern, getIngredientTargets(pattern), sets, null);
    }

    List<PatternIngredientAssignment> buildBufferedIngredientPlan(PatternCraftingReference owner, int patternSlot,
            ItemStack pattern, int sets) {
        return buildBufferedIngredientPlan(patternSlot, pattern, getIngredientTargets(pattern), sets, null, owner);
    }

    List<PatternIngredientAssignment> buildBufferedIngredientPlanAfterAdding(int patternSlot, ItemStack pattern,
            int sets, IPatternStack arrivingStack) {
        return buildBufferedIngredientPlan(patternSlot, pattern, getIngredientTargets(pattern), sets, arrivingStack);
    }

    int completeBufferedSets(int patternSlot, ItemStack pattern) {
        long total = 0;
        for (PatternCraftingReference owner : ingredientBuffer.owners(patternSlot)) {
            total += completeBufferedSets(owner, patternSlot, pattern);
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    int completeBufferedSets(PatternCraftingReference owner, int patternSlot, ItemStack pattern) {
        long bufferVersion = ingredientBuffer.changeVersion();
        CompleteSetsCache cached = completeSetsByOwner.get(owner);
        if (cached != null && cached.pattern == pattern && cached.bufferVersion == bufferVersion) {
            return cached.sets;
        }
        List<PatternIngredientTarget> ingredients = getIngredientTargets(pattern);
        if (ingredients.isEmpty()) {
            completeSetsByOwner.put(owner, new CompleteSetsCache(pattern, bufferVersion, 0));
            return 0;
        }
        List<IPatternStack> ownedStacks = ingredientBuffer.copyOwnedStacks(owner);
        int upperBound = Integer.MAX_VALUE;
        for (PatternIngredientTarget ingredient : ingredients) {
            upperBound = Math.min(
                    upperBound,
                    matchingAmount(pattern, ownedStacks, ingredient.stack()) / ingredient.stack().getAmount());
        }
        for (int sets = upperBound; sets > 0; sets--) {
            if (buildBufferedIngredientPlan(patternSlot, pattern, ingredients, sets, null, owner) != null) {
                completeSetsByOwner.put(owner, new CompleteSetsCache(pattern, bufferVersion, sets));
                return sets;
            }
        }
        completeSetsByOwner.put(owner, new CompleteSetsCache(pattern, bufferVersion, 0));
        return 0;
    }

    private int matchingAmount(ItemStack pattern, List<IPatternStack> stacks, IPatternStack ingredient) {
        int amount = 0;
        for (IPatternStack stack : stacks) {
            if (ingredientMatchesStack(pattern, ingredient, stack)) {
                amount += stack.getAmount();
            }
        }
        return amount;
    }

    private TargetPlan getTargetPlan(ItemStack pattern) {
        if (pattern == null || patternHandler.getRecipe(pattern) == null) {
            return null;
        }
        refreshTargetPlanTick();
        TargetPlan cached = targetPlans.get(pattern);
        if (cached != null) {
            return cached;
        }
        TargetPlan plan = buildTargetPlan(pattern);
        targetPlans.put(pattern, plan);
        return plan;
    }

    private TargetPlan buildTargetPlan(ItemStack pattern) {
        PatternRecipeSnapshot recipe = patternHandler.getRecipe(pattern);
        List<PatternIngredientTarget> ingredients = new ArrayList<>();
        List<PatternIngredientTarget> localIngredients = new ArrayList<>();
        boolean patternTable = adjacentInventory.isConnectedToPatternCraftingTable();
        boolean useSatellites = module.hasAdvancedSatelliteUpgrade() && !patternTable;
        boolean hasSatellites = false;
        for (int slot = 0; slot < recipe.getIngredientSlotCount(); slot++) {
            IPatternStack stack = recipe.getInput(slot);
            if (stack == null || stack.getAmount() <= 0) {
                continue;
            }
            IRequestItems itemTarget = null;
            IRequestFluid fluidTarget = null;
            if (useSatellites && PatternStackHelper.isSolid(stack)) {
                itemTarget = pipe.resolvePatternSatelliteTarget(
                        recipe.getItemSatelliteUuid(slot),
                        recipe.getItemSatelliteId(slot));
            } else if (useSatellites && PatternStackHelper.isFluid(stack)) {
                fluidTarget = pipe.resolvePatternFluidSatelliteTarget(
                        recipe.getFluidSatelliteUuid(slot),
                        recipe.getFluidSatelliteId(slot));
            }
            PatternIngredientTarget target = new PatternIngredientTarget(slot, stack.copy(), itemTarget, fluidTarget);
            ingredients.add(target);
            if (itemTarget == null && fluidTarget == null) {
                localIngredients.add(target);
            } else {
                hasSatellites = true;
            }
        }
        return new TargetPlan(ingredients, localIngredients, hasSatellites);
    }

    private void refreshTargetPlanTick() {
        World world = pipe.getWorld();
        long tick = world == null ? 0 : world.getTotalWorldTime();
        if (targetPlanTick == tick) {
            return;
        }
        targetPlanTick = tick;
        targetPlans.clear();
    }

    private boolean ingredientMatchesStack(ItemStack pattern, IPatternStack ingredient, IPatternStack buffered) {
        ItemIdentifierStack item = PatternStackHelper.asSolidStack(buffered);
        if (item != null) {
            return ingredientMatchesItem(pattern, ingredient, item.getItem());
        }
        FluidIdentifier fluid = PatternStackHelper.asFluid(buffered);
        return fluid != null && PatternStackHelper.matches(ingredient, fluid);
    }

    private boolean itemMatchesPatternIngredient(PatternRecipeSnapshot recipe, ItemIdentifier expected,
            ItemIdentifier actual) {
        if (expected == null || actual == null) {
            return false;
        }
        if (expected.equals(actual)) {
            return true;
        }
        if (recipe != null && recipe.isIgnoreNbtEnabled() && expected.equalsWithoutNBT(actual)) {
            return true;
        }
        return recipe != null && recipe.isOreDictSubstitutionEnabled()
                && expected.getDictIdentifiers() != null
                && actual.getDictIdentifiers() != null
                && expected.getDictIdentifiers().canMatch(actual.getDictIdentifiers(), true, false);
    }

    private List<PatternIngredientAssignment> buildBufferedIngredientPlan(int patternSlot, ItemStack pattern,
            List<PatternIngredientTarget> ingredients, int sets, IPatternStack extraStack) {
        return buildBufferedIngredientPlan(patternSlot, pattern, ingredients, sets, extraStack, null);
    }

    private List<PatternIngredientAssignment> buildBufferedIngredientPlan(int patternSlot, ItemStack pattern,
            List<PatternIngredientTarget> ingredients, int sets, IPatternStack extraStack,
            PatternCraftingReference owner) {
        if (sets <= 0 || ingredients.isEmpty()) {
            return Collections.emptyList();
        }
        List<IPatternStack> available = owner == null ? copyBufferedIngredients(patternSlot)
                : ingredientBuffer.copyOwnedStacks(owner);
        if (extraStack != null && extraStack.getAmount() > 0) {
            PatternStackHelper.addAggregated(available, extraStack);
        }
        List<PatternIngredientAssignment> assignments = new ArrayList<>();
        for (PatternIngredientTarget ingredient : ingredients) {
            long requestedAmount = (long) ingredient.stack().getAmount() * sets;
            if (requestedAmount > Integer.MAX_VALUE) {
                return null;
            }
            IPatternStack selected = takeMatchingStack(pattern, available, ingredient.stack(), (int) requestedAmount);
            if (selected == null) {
                return null;
            }
            assignments.add(new PatternIngredientAssignment(ingredient.inputSlot(), selected));
        }
        return assignments;
    }

    private List<IPatternStack> copyBufferedIngredients(int patternSlot) {
        List<IPatternStack> result = new ArrayList<>();
        List<IPatternStack> buffered = ingredientBuffer.asMap().get(patternSlot);
        if (buffered == null) {
            return result;
        }
        for (IPatternStack stack : buffered) {
            if (stack != null && stack.getAmount() > 0) {
                result.add(stack.copy());
            }
        }
        return result;
    }

    private IPatternStack takeMatchingStack(ItemStack pattern, List<IPatternStack> available, IPatternStack ingredient,
            int amount) {
        for (int i = 0; i < available.size(); i++) {
            IPatternStack candidate = available.get(i);
            if (!ingredientMatchesStack(pattern, ingredient, candidate) || candidate.getAmount() < amount) {
                continue;
            }
            IPatternStack selected = PatternStackHelper.copyWithAmount(candidate, amount);
            candidate.addAmount(-amount);
            if (candidate.getAmount() <= 0) {
                available.remove(i);
            }
            return selected;
        }
        return null;
    }

    private static final class TargetPlan {

        private final List<PatternIngredientTarget> ingredients;
        private final List<IPatternStack> aggregatedIngredients;
        private final List<IPatternStack> localAggregatedIngredients;
        private final boolean hasSatelliteAssignments;
        private final Map<ItemIdentifier, Integer> itemAmounts = new java.util.HashMap<>();

        private TargetPlan(List<PatternIngredientTarget> ingredients, List<PatternIngredientTarget> localIngredients,
                boolean hasSatelliteAssignments) {
            this.ingredients = Collections.unmodifiableList(ingredients);
            aggregatedIngredients = aggregate(ingredients);
            localAggregatedIngredients = aggregate(localIngredients);
            this.hasSatelliteAssignments = hasSatelliteAssignments;
        }

        private static List<IPatternStack> aggregate(List<PatternIngredientTarget> targets) {
            List<IPatternStack> result = new ArrayList<>();
            for (PatternIngredientTarget target : targets) {
                PatternStackHelper.addAggregated(result, target.stack());
            }
            return Collections.unmodifiableList(result);
        }
    }

    private static final class CompleteSetsCache {

        private final ItemStack pattern;
        private final long bufferVersion;
        private final int sets;

        private CompleteSetsCache(ItemStack pattern, long bufferVersion, int sets) {
            this.pattern = pattern;
            this.bufferVersion = bufferVersion;
            this.sets = sets;
        }
    }
}
