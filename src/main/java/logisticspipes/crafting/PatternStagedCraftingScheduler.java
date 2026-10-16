package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;

import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.routing.order.LogisticsFluidOrder;
import logisticspipes.routing.order.LogisticsItemOrder;
import logisticspipes.utils.CacheHolder.CacheTypes;

/**
 * Decides when staged pattern crafting orders may request their next ingredient sets.
 * <p>
 * The scheduler balances three constraints before it asks a {@link PatternCraftingOrder} to consume branch state:
 * remaining output work, branch ingredient availability, and local capacity in the pipe buffer or adjacent target.
 */
class PatternStagedCraftingScheduler {

    private final ModulePatternCrafting module;
    private final PipeItemsPatternCraftingLogistics pipe;
    private final List<PatternCraftingOrder> stagedCrafts;
    private final Set<Integer> requestingPatterns = new HashSet<>();
    private long lastPeriodicRequestTick = Long.MIN_VALUE;

    PatternStagedCraftingScheduler(ModulePatternCrafting module, PipeItemsPatternCraftingLogistics pipe,
            List<PatternCraftingOrder> stagedCrafts) {
        this.module = module;
        this.pipe = pipe;
        this.stagedCrafts = stagedCrafts;
    }

    /**
     * Requests ingredients for every staged order that still has room in the module or adjacent inventory.
     */
    void requestIngredients(boolean capacityChanged) {
        long tick = module.currentWorldTick();
        if (!capacityChanged && lastPeriodicRequestTick == tick) {
            return;
        }
        lastPeriodicRequestTick = tick;
        Set<Integer> patternSlots = new LinkedHashSet<>();
        for (PatternCraftingOrder order : new ArrayList<>(stagedCrafts)) {
            patternSlots.add(order.patternSlot);
        }
        for (int patternSlot : patternSlots) {
            requestIngredients(patternSlot);
        }
    }

    /**
     * Requests ingredients for one pattern slot.
     * <p>
     * The per-pattern guard allows different patterns in the same module to stage work independently while preventing
     * recursive requests for the same pattern from re-entering through branch fulfillment.
     */
    void requestIngredients(int patternSlot) {
        if (!requestingPatterns.add(patternSlot)) {
            module.debugEventThrottled("SCHED", "request ingredients slot=%d skipped: already requesting", patternSlot);
            return;
        }
        try {
            requestIngredientsGuarded(patternSlot);
        } finally {
            requestingPatterns.remove(patternSlot);
        }
    }

    private void requestIngredientsGuarded(int patternSlot) {
        for (PatternCraftingOrder order : new ArrayList<>(stagedCrafts)) {
            if (removeFinishedOrder(order)) {
                continue;
            }
            if (order.patternSlot != patternSlot) {
                continue;
            }

            ItemStack pattern = module.getPatternStack(order.patternSlot);
            if (removeOrderWithoutPattern(order, pattern) || removeFullyRequestedOrder(order)) {
                continue;
            }
            if (isBlockedByAnotherRunningCraft(order)) {
                continue;
            }

            requestOrderIngredients(order, pattern);
        }
    }

    private boolean removeFinishedOrder(PatternCraftingOrder order) {
        if (!order.outputOrder.isFinished()) {
            return false;
        }
        if (!order.isFullyRequested() && isSamePipeOutput(order)) {
            module.debugEventThrottled(
                    "SCHED",
                    60,
                    "request ingredients slot=%d kept finished same-pipe staged order until ingredients requested remainingSets=%d",
                    order.patternSlot,
                    order.remainingSets);
            return false;
        }
        module.debugEvent(
                "SCHED",
                "request ingredients slot=%d removing staged order: the order output is already satisfied remainingSets=%d",
                order.patternSlot,
                order.remainingSets);
        order.releaseReservations();
        stagedCrafts.remove(order);
        module.markHudStateDirty();
        return true;
    }

    private boolean isSamePipeOutput(PatternCraftingOrder order) {
        if (order.outputOrder instanceof LogisticsItemOrder itemOrder) {
            return module.isOrderDestinationThisModule(itemOrder)
                    && itemOrder.getInformation() instanceof PatternTargetInformation;
        }
        if (order.outputOrder instanceof LogisticsFluidOrder fluidOrder) {
            return module.isOrderDestinationThisModule(fluidOrder)
                    && fluidOrder.getInformation() instanceof PatternTargetInformation;
        }
        return false;
    }

    private boolean removeOrderWithoutPattern(PatternCraftingOrder order, ItemStack pattern) {
        if (pattern != null) {
            return false;
        }
        module.debugEvent(
                "SCHED",
                "request ingredients slot=%d removing staged order: pattern missing",
                order.patternSlot);
        order.releaseReservations();
        stagedCrafts.remove(order);
        module.markHudStateDirty();
        return true;
    }

    private boolean removeFullyRequestedOrder(PatternCraftingOrder order) {
        if (!order.isFullyRequested()) {
            return false;
        }
        module.debugEvent(
                "SCHED",
                "request ingredients slot=%d removing staged order: fully requested remainingSets=%d",
                order.patternSlot,
                order.remainingSets);
        order.releaseReservations();
        stagedCrafts.remove(order);
        module.markHudStateDirty();
        return true;
    }

    private boolean isBlockedByAnotherRunningCraft(PatternCraftingOrder order) {
        PipeItemsPatternCraftingLogistics.BlockingMode mode = module.getEffectiveBlockingMode();
        if (mode == PipeItemsPatternCraftingLogistics.BlockingMode.OFF || !module.isRunningCraftLocked()) {
            return false;
        }
        int runningCraft = module.getRunningCraftForHandler();
        if (runningCraft == order.patternSlot) {
            return false;
        }
        module.debugEventThrottled(
                "SCHED",
                100,
                "request ingredients slot=%d skipped: running craft locked by slot=%d",
                order.patternSlot,
                runningCraft);
        return true;
    }

    private void requestOrderIngredients(PatternCraftingOrder order, ItemStack pattern) {
        int branchSets = order.availableSetsFromBranches(pattern);
        int orderableSets = orderableSetsForPattern(order, pattern, branchSets);
        int sets = Math.min(order.remainingSets, orderableSets);
        sets = Math.min(sets, branchSets);
        if (sets <= 0) {
            module.debugEventThrottled(
                    "SCHED",
                    100,
                    "request ingredients slot=%d paused: no selectable sets remainingSets=%d orderableSets=%d branchSets=%d",
                    order.patternSlot,
                    order.remainingSets,
                    orderableSets,
                    branchSets);
            return;
        }
        module.debugEvent(
                "SCHED",
                "request ingredients slot=%d remainingSets=%d orderableSets=%d branchSets=%d selectedSets=%d",
                order.patternSlot,
                order.remainingSets,
                orderableSets,
                branchSets,
                sets);

        int requestedSets = order.requestIngredients(pattern, sets);
        if (requestedSets <= 0) {
            module.debugEventThrottled(
                    "SCHED",
                    "request ingredients slot=%d requested no sets selectedSets=%d",
                    order.patternSlot,
                    sets);
            return;
        }

        module.debugEvent(
                "SCHED",
                "request ingredients slot=%d requestedSets=%d remainingSets=%d",
                order.patternSlot,
                requestedSets,
                order.remainingSets);
        pipe.getCacheHolder().trigger(CacheTypes.Inventory);
        module.markHudStateDirty();
        if (order.isFullyRequested()) {
            module.debugEvent(
                    "REQUEST",
                    "request ingredients slot=%d completed staged order after request",
                    order.patternSlot);
            order.releaseReservations();
            stagedCrafts.remove(order);
            module.markHudStateDirty();
        }
    }

    /**
     * Calculates how many complete pattern sets can be ordered now without overcommitting the module buffer or adjacent
     * inventory. Requested but not-yet-arrived ingredients are subtracted so repeated recalculation only orders newly
     * freed capacity.
     */
    private int orderableSetsForPattern(PatternCraftingOrder order, ItemStack pattern, int branchSets) {
        if (!module.canReceiveForPattern(order.patternSlot)) {
            module.debugEventThrottled("SCHED", "orderable sets slot=%d result=0 cannot receive", order.patternSlot);
            return 0;
        }
        List<IPatternStack> ingredients = module.getAggregatedIngredients(pattern);
        if (ingredients.isEmpty()) {
            return 0;
        }
        int maxWantedSets = Math.min(order.remainingSets, branchSets);
        int targetSets = module.maxDispatchablePatternSets(order.reference(), pattern, maxWantedSets);
        if (targetSets <= 0) {
            module.debugEventThrottled(
                    "SCHED",
                    "orderable sets slot=%d result=0 no target capacity maxWantedSets=%d",
                    order.patternSlot,
                    maxWantedSets);
            return 0;
        }
        int sets = Integer.MAX_VALUE;
        for (IPatternStack ingredient : ingredients) {
            int room = module.remainingIngredientRoomForSets(order.patternSlot, pattern, ingredient, targetSets);
            sets = Math.min(sets, Math.max(0, room) / ingredient.getAmount());
        }
        return sets == Integer.MAX_VALUE ? 0 : Math.max(0, sets);
    }
}
