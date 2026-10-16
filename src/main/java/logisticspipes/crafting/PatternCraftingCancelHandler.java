package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.item.ItemStack;

import logisticspipes.crafting.pattern.PatternHandler;
import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.routing.order.LogisticsFluidOrder;
import logisticspipes.routing.order.LogisticsItemOrder;
import logisticspipes.routing.order.LogisticsOrder;
import logisticspipes.utils.CacheHolder.CacheTypes;

/** Cancels complete crafting instances and returns only their owned local inputs to storage. */
final class PatternCraftingCancelHandler {

    private final ModulePatternCrafting module;
    private final PipeItemsPatternCraftingLogistics pipe;
    private final PatternHandler patternHandler;
    private final PatternStackBufferHandler ingredientBuffer;
    private final Map<Integer, List<IPatternStack>> requestedIngredients;
    private final PatternStackRequestHandler requestedIngredient;
    private final PatternStagedCraftingCoordinator stagedCrafting;
    private final PatternCraftingBlockingHandler blockingHandler;
    private final PatternLostIngredientHandler lostIngredientHandler;

    PatternCraftingCancelHandler(ModulePatternCrafting module, PipeItemsPatternCraftingLogistics pipe,
            PatternHandler patternHandler, PatternStackBufferHandler ingredientBuffer,
            Map<Integer, List<IPatternStack>> requestedIngredients, PatternStackRequestHandler requestedIngredient,
            PatternStagedCraftingCoordinator stagedCrafting, PatternCraftingBlockingHandler blockingHandler,
            PatternLostIngredientHandler lostIngredientHandler) {
        this.module = module;
        this.pipe = pipe;
        this.patternHandler = patternHandler;
        this.ingredientBuffer = ingredientBuffer;
        this.requestedIngredients = requestedIngredients;
        this.requestedIngredient = requestedIngredient;
        this.stagedCrafting = stagedCrafting;
        this.blockingHandler = blockingHandler;
        this.lostIngredientHandler = lostIngredientHandler;
    }

    boolean cancelPatternCraft(int patternSlot) {
        if (patternSlot < 0 || patternSlot >= patternHandler.size()) {
            return false;
        }
        Set<UUID> instances = stagedCrafting.instancesForPattern(patternSlot);
        boolean changed = false;
        for (UUID instanceId : instances) {
            changed |= PatternCraftingInstanceRegistry.cancelInstance(instanceId);
        }
        if (changed) {
            module.debugEvent("CANCEL", "cancelled crafting instances=%s selectedSlot=%d", instances, patternSlot);
            pipe.getCacheHolder().trigger(CacheTypes.Inventory);
        }
        return changed;
    }

    boolean cancelTrackedOrder(PatternCraftingOrder order) {
        PatternCraftingReference reference = order.reference();
        UUID instanceId = reference.instanceId();
        boolean changed = stagedCrafting.cancelTrackedOrder(order);
        changed |= removeStandaloneOrders(instanceId);
        changed |= requestedIngredient.removeAll(reference);
        changed |= module.abandonPendingDispatch(instanceId);
        changed |= flushBufferedIngredientsToStorage(reference, order.patternSlot);
        changed |= blockingHandler.retrieveAndReleaseSatelliteBatches(instanceId);
        changed |= blockingHandler.clearRunningCraft(instanceId);
        changed |= lostIngredientHandler.removeInstance(instanceId);
        if (changed) {
            pipe.getCacheHolder().trigger(CacheTypes.Inventory);
        }
        return changed;
    }

    boolean cancelPendingInstance(UUID instanceId) {
        return cancelUntrackedInstance(instanceId, false);
    }

    boolean cancelStandaloneInstance(UUID instanceId) {
        return cancelUntrackedInstance(instanceId, true);
    }

    private boolean cancelUntrackedInstance(UUID instanceId, boolean removeOrders) {
        boolean changed = removeOrders && removeStandaloneOrders(instanceId);
        changed |= requestedIngredient.removeInstance(instanceId);
        changed |= module.abandonPendingDispatch(instanceId);
        for (PatternStackBufferHandler.OwnedEntry entry : ingredientBuffer.entries(instanceId)) {
            changed |= flushBufferedIngredientsToStorage(entry.owner, entry.patternSlot);
        }
        changed |= blockingHandler.retrieveAndReleaseSatelliteBatches(instanceId);
        changed |= blockingHandler.clearRunningCraft(instanceId);
        changed |= lostIngredientHandler.removeInstance(instanceId);
        if (changed) {
            pipe.getCacheHolder().trigger(CacheTypes.Inventory);
        }
        return changed;
    }

    private boolean removeStandaloneOrders(UUID instanceId) {
        if (instanceId == null) {
            return false;
        }
        List<LogisticsItemOrder> itemOrders = new ArrayList<>();
        for (LogisticsItemOrder order : pipe.getItemOrderManager()) {
            if (belongsToInstance(order, instanceId) && !PatternCraftingInstanceRegistry.isTrackedOutputOrder(order)) {
                itemOrders.add(order);
            }
        }
        List<LogisticsFluidOrder> fluidOrders = new ArrayList<>();
        for (LogisticsFluidOrder order : pipe.getPatternFluidOrderManager()) {
            if (belongsToInstance(order, instanceId) && !PatternCraftingInstanceRegistry.isTrackedOutputOrder(order)) {
                fluidOrders.add(order);
            }
        }
        for (LogisticsItemOrder order : itemOrders) {
            pipe.getItemOrderManager().removeOrder(order);
            module.debugEvent(
                    "CANCEL",
                    "removed standalone item order instance=%s output=%s",
                    instanceId,
                    order.getAsDisplayItem());
        }
        for (LogisticsFluidOrder order : fluidOrders) {
            pipe.getPatternFluidOrderManager().removeOrder(order);
            module.debugEvent(
                    "CANCEL",
                    "removed standalone fluid order instance=%s output=%s",
                    instanceId,
                    order.getAsDisplayItem());
        }
        return !itemOrders.isEmpty() || !fluidOrders.isEmpty();
    }

    private boolean belongsToInstance(LogisticsOrder order, UUID instanceId) {
        return order.getCraftingReference() != null && instanceId.equals(order.getCraftingReference().instanceId());
    }

    boolean returnStoredInputsToStorage() {
        Set<UUID> instances = new HashSet<>();
        for (int slot = 0; slot < patternHandler.size(); slot++) {
            instances.addAll(stagedCrafting.instancesForPattern(slot));
        }
        boolean changed = false;
        for (UUID instanceId : instances) {
            changed |= PatternCraftingInstanceRegistry.cancelInstance(instanceId);
        }

        changed |= module.abandonPendingDispatch(null);
        Set<Integer> remainingSlots = new HashSet<>(ingredientBuffer.keySet());
        remainingSlots.addAll(requestedIngredients.keySet());
        for (int slot : remainingSlots) {
            changed |= requestedIngredient.removeAll(slot);
            changed |= flushBufferedIngredientsToStorage(slot);
        }
        changed |= blockingHandler.retrieveAndReleaseAllSatelliteBatches();
        if (blockingHandler.runningCraft() >= 0) {
            blockingHandler.restoreRunningCraft(-1, null, false);
            changed = true;
        }
        changed |= lostIngredientHandler.clear();
        if (changed) {
            module.debugEvent("CANCEL", "returned all stored pattern inputs instances=%s", instances);
            pipe.getCacheHolder().trigger(CacheTypes.Inventory);
        }
        return changed;
    }

    boolean shouldRouteLateArrivalToStorage(PatternCraftingReference reference) {
        return PatternCraftingInstanceRegistry.isCancelled(reference);
    }

    private boolean flushBufferedIngredientsToStorage(PatternCraftingReference owner, int patternSlot) {
        return sendToStorage(ingredientBuffer.removeAll(owner), patternSlot, owner);
    }

    private boolean flushBufferedIngredientsToStorage(int patternSlot) {
        return sendToStorage(ingredientBuffer.removeAll(patternSlot), patternSlot, null);
    }

    private boolean sendToStorage(List<IPatternStack> stacks, int patternSlot, PatternCraftingReference owner) {
        boolean sent = false;
        for (IPatternStack stack : stacks) {
            for (ItemStack itemStack : PatternStackBufferHandler.makeItemStacks(stack)) {
                pipe.sendStack(itemStack, -1, CoreRoutedPipe.ItemSendMode.Normal, null);
                sent = true;
                module.debugEvent(
                        "CANCEL",
                        "sent buffered ingredient to storage reference=%s slot=%d stack=%s",
                        owner,
                        patternSlot,
                        stack);
            }
        }
        return sent;
    }
}
