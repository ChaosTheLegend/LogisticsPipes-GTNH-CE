package logisticspipes.crafting;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;

import logisticspipes.crafting.pattern.PatternHandler;
import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.crafting.patternStack.PatternFluidStack;
import logisticspipes.crafting.patternStack.PatternItemStack;
import logisticspipes.crafting.patternStack.PatternStackHelper;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.utils.AdjacentTile;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * Calculates how much ingredient data the module can safely accept or reserve.
 * <p>
 * The class deliberately contains no lifecycle or routing side effects. Expensive adjacent-inventory simulations are
 * memoized by {@link AdjacentInventoryHandler}; recipe and target lookups are served by their respective caches.
 */
final class PatternCraftingCapacity {

    private final ModulePatternCrafting module;
    private final PipeItemsPatternCraftingLogistics pipe;
    private final PatternHandler patternHandler;
    private final AdjacentInventoryHandler adjacentInventory;
    private final PatternStackBufferHandler ingredientBuffer;
    private final PatternStackRequestHandler requestedIngredient;
    private final PatternCraftingIngredientPlanner ingredientPlanner;
    private final Map<FlexibleCapacityKey, Integer> flexibleItemCapacity = new HashMap<>();
    private long capacityBufferVersion = Long.MIN_VALUE;
    private long capacityPatternVersion = Long.MIN_VALUE;

    PatternCraftingCapacity(ModulePatternCrafting module, PipeItemsPatternCraftingLogistics pipe,
            PatternHandler patternHandler, AdjacentInventoryHandler adjacentInventory,
            PatternStackBufferHandler ingredientBuffer, PatternStackRequestHandler requestedIngredient,
            PatternCraftingIngredientPlanner ingredientPlanner) {
        this.module = module;
        this.pipe = pipe;
        this.patternHandler = patternHandler;
        this.adjacentInventory = adjacentInventory;
        this.ingredientBuffer = ingredientBuffer;
        this.requestedIngredient = requestedIngredient;
        this.ingredientPlanner = ingredientPlanner;
    }

    int spaceForItem(ItemIdentifier item, boolean includeInTransit) {
        int count = 0;
        for (int slot = 0; slot < patternHandler.size(); slot++) {
            ItemStack pattern = patternHandler.getConfiguredPatternStack(slot);
            if (pattern == null || !module.isPatternCraftingSupported(pattern)
                    || ingredientPlanner.ingredientAmount(pattern, item) <= 0) {
                continue;
            }
            int requested = ingredientPlanner.requestedItemAmount(slot, pattern, item);
            count = Math.max(count, requested);
            if (module.canReceiveForPattern(slot)) {
                count = Math.max(count, spaceForPatternItem(slot, pattern, item) - requested);
            }
        }
        if (includeInTransit) {
            count -= pipe.countOnRoute(item);
        }
        return Math.max(0, count);
    }

    int spaceForFluid(FluidIdentifier fluid, boolean includeInTransit) {
        int count = 0;
        for (int slot = 0; slot < patternHandler.size(); slot++) {
            ItemStack pattern = patternHandler.getConfiguredPatternStack(slot);
            if (pattern == null || !module.isPatternCraftingSupported(pattern)
                    || patternHandler.fluidIngredientAmount(pattern, fluid) <= 0) {
                continue;
            }
            int requested = requestedIngredient.amount(slot, fluid);
            count = Math.max(count, requested);
            if (module.canReceiveForPattern(slot)) {
                count = Math.max(count, spaceForPatternFluid(slot, pattern, fluid) - requested);
            }
        }
        // Fluids travel in opaque LP containers; the item transit count cannot safely be converted back to mB here.
        return Math.max(0, count);
    }

    int spaceForArrivingItem(int patternSlot, ItemStack pattern, ItemIdentifier item) {
        int space = spaceForPatternItem(patternSlot, pattern, item);
        AdjacentTile connected = adjacentInventory.getConnected();
        if (module.getEffectiveBlockingMode() == PipeItemsPatternCraftingLogistics.BlockingMode.BLOCKING
                && connected != null
                && module.isInventoryEmptyFor(connected, patternSlot)
                && ingredientPlanner.buildBufferedIngredientPlanAfterAdding(
                        patternSlot,
                        pattern,
                        1,
                        new PatternItemStack(new ItemIdentifierStack(item, space))) != null) {
            space += ingredientPlanner.ingredientAmount(pattern, item);
        }
        module.debug("arrival item space slot=%d item=%s space=%d", patternSlot, item, space);
        return space;
    }

    int spaceForArrivingFluid(int patternSlot, ItemStack pattern, FluidIdentifier fluid) {
        int space = spaceForPatternFluid(patternSlot, pattern, fluid);
        AdjacentTile connected = adjacentInventory.getConnected();
        if (module.getEffectiveBlockingMode() == PipeItemsPatternCraftingLogistics.BlockingMode.BLOCKING
                && connected != null
                && module.isInventoryEmptyFor(connected, patternSlot)
                && ingredientPlanner.buildBufferedIngredientPlanAfterAdding(
                        patternSlot,
                        pattern,
                        1,
                        new PatternFluidStack(fluid, space)) != null
                && itemIngredientsBufferedForOneSet(patternSlot, pattern)) {
            space += patternHandler.fluidIngredientAmount(pattern, fluid);
        }
        module.debug("arrival fluid space slot=%d fluid=%s space=%d", patternSlot, fluid, space);
        return space;
    }

    int spaceForPatternIngredient(int patternSlot, ItemStack pattern, IPatternStack ingredient) {
        ItemIdentifierStack solid = PatternStackHelper.asSolidStack(ingredient);
        if (solid != null) {
            return spaceForPatternItem(patternSlot, pattern, solid.getItem());
        }
        FluidIdentifier fluid = PatternStackHelper.asFluid(ingredient);
        return fluid == null ? 0 : spaceForPatternFluid(patternSlot, pattern, fluid);
    }

    int remainingIngredientRoomForSets(int patternSlot, ItemStack pattern, IPatternStack ingredient, int targetSets) {
        if (ingredient == null || ingredient.getAmount() <= 0 || targetSets <= 0) {
            return 0;
        }
        long capacity = Math.min(Integer.MAX_VALUE, (long) ingredient.getAmount() * targetSets);
        long occupied = (long) ingredientPlanner.bufferedIngredientAmount(patternSlot, pattern, ingredient)
                + ingredientPlanner.requestedIngredientAmount(patternSlot, pattern, ingredient);
        return (int) Math.max(0, capacity - occupied);
    }

    private int spaceForPatternItem(int patternSlot, ItemStack pattern, ItemIdentifier item) {
        int sets = availableTargetSets(pattern);
        int capacity = multiplyClamped(sets, ingredientPlanner.ingredientAmount(pattern, item));
        int buffered = ingredientPlanner.bufferedItemAmount(patternSlot, pattern, item);
        int room = Math.max(0, capacity - buffered);
        int result = maxAcceptedFlexibleItemAmount(patternSlot, pattern, item, room);
        module.debug(
                "pattern item capacity slot=%d item=%s sets=%d capacity=%d buffered=%d room=%d",
                patternSlot,
                item,
                sets,
                capacity,
                buffered,
                result);
        return result;
    }

    private int maxAcceptedFlexibleItemAmount(int patternSlot, ItemStack pattern, ItemIdentifier item, int room) {
        if (room <= 0 || !ingredientPlanner.requiresConcreteIngredientPlanning(pattern)) {
            return room;
        }
        refreshFlexibleCapacityCache();
        FlexibleCapacityKey key = new FlexibleCapacityKey(patternSlot, pattern, item, room);
        Integer cached = flexibleItemCapacity.get(key);
        if (cached != null) {
            return cached;
        }
        // A larger alternative stack can change the greedy slot assignment, so feasibility is not strictly monotonic.
        // Keep the descending search to preserve the existing matching semantics; parsed recipes and target plans are
        // cached, which removes the expensive NBT and satellite lookups from each attempt.
        for (int amount = room; amount > 0; amount--) {
            IPatternStack candidate = new PatternItemStack(new ItemIdentifierStack(item, amount));
            if (ingredientPlanner.buildBufferedIngredientPlanAfterAdding(patternSlot, pattern, 1, candidate) != null) {
                flexibleItemCapacity.put(key, amount);
                return amount;
            }
        }
        flexibleItemCapacity.put(key, 0);
        return 0;
    }

    private void refreshFlexibleCapacityCache() {
        long bufferVersion = ingredientBuffer.changeVersion();
        long patternVersion = patternHandler.getChangeVersion();
        if (capacityBufferVersion == bufferVersion && capacityPatternVersion == patternVersion) {
            return;
        }
        capacityBufferVersion = bufferVersion;
        capacityPatternVersion = patternVersion;
        flexibleItemCapacity.clear();
    }

    private int spaceForPatternFluid(int patternSlot, ItemStack pattern, FluidIdentifier fluid) {
        int sets = availableTargetSets(pattern);
        int capacity = multiplyClamped(sets, patternHandler.fluidIngredientAmount(pattern, fluid));
        int buffered = ingredientBuffer.amount(patternSlot, fluid);
        int result = Math.max(0, capacity - buffered);
        module.debug(
                "pattern fluid capacity slot=%d fluid=%s sets=%d capacity=%d buffered=%d room=%d",
                patternSlot,
                fluid,
                sets,
                capacity,
                buffered,
                result);
        return result;
    }

    private int availableTargetSets(ItemStack pattern) {
        if (module.getEffectiveBlockingMode() == PipeItemsPatternCraftingLogistics.BlockingMode.BLOCKING) {
            return 1;
        }
        int adjacentSets = adjacentInventory.availablePatternSets(pattern);
        return adjacentSets == Integer.MAX_VALUE ? Integer.MAX_VALUE : adjacentSets + 1;
    }

    private int multiplyClamped(int left, int right) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, (long) left * right));
    }

    private boolean itemIngredientsBufferedForOneSet(int patternSlot, ItemStack pattern) {
        for (PatternIngredientTarget ingredient : ingredientPlanner.getIngredientTargets(pattern)) {
            if (PatternStackHelper.isSolid(ingredient.stack())
                    && ingredientPlanner.bufferedIngredientAmount(patternSlot, pattern, ingredient.stack())
                            < ingredient.stack().getAmount()) {
                return false;
            }
        }
        return true;
    }

    private static final class FlexibleCapacityKey {

        private final int patternSlot;
        private final ItemStack pattern;
        private final ItemIdentifier item;
        private final int room;

        private FlexibleCapacityKey(int patternSlot, ItemStack pattern, ItemIdentifier item, int room) {
            this.patternSlot = patternSlot;
            this.pattern = pattern;
            this.item = item;
            this.room = room;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof FlexibleCapacityKey key)) {
                return false;
            }
            return patternSlot == key.patternSlot && pattern == key.pattern
                    && room == key.room
                    && item.equals(key.item);
        }

        @Override
        public int hashCode() {
            int result = 31 * patternSlot + System.identityHashCode(pattern);
            result = 31 * result + item.hashCode();
            return 31 * result + room;
        }
    }
}
