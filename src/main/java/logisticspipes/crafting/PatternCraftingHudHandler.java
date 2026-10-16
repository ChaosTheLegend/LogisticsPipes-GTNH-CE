package logisticspipes.crafting;

import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import logisticspipes.crafting.pattern.PatternHandler;
import logisticspipes.crafting.pattern.PatternRecipeSnapshot;
import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.crafting.patternStack.PatternStackHelper;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.utils.AdjacentTile;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * Builds and caches the pattern crafting HUD snapshot.
 * <p>
 * The HUD polls regularly, while most crafting state only changes on explicit events. Keeping the cache policy here
 * lets the module expose HUD data without also owning all display formatting.
 */
final class PatternCraftingHudHandler {

    private static final int STATE_RECHECK_INTERVAL = 20;

    private final ModulePatternCrafting module;
    private final PatternHandler patternHandler;
    private final AdjacentInventoryHandler adjacentInventory;
    private final PatternStackBufferHandler ingredientBuffer;
    private final Map<Integer, List<IPatternStack>> requestedIngredients;
    private final PatternStagedCraftingCoordinator stagedCrafting;
    private final PatternCraftingBlockingHandler blockingHandler;
    private final PatternSatelliteDispatchHandler satelliteDispatchHandler;

    private PatternCraftingHudState cachedState = PatternCraftingHudState.empty();
    private boolean dirty = true;
    private long lastBuildTick = Long.MIN_VALUE;

    PatternCraftingHudHandler(ModulePatternCrafting module, PatternHandler patternHandler,
            AdjacentInventoryHandler adjacentInventory, PatternStackBufferHandler ingredientBuffer,
            Map<Integer, List<IPatternStack>> requestedIngredients, PatternStagedCraftingCoordinator stagedCrafting,
            PatternCraftingBlockingHandler blockingHandler, PatternSatelliteDispatchHandler satelliteDispatchHandler) {
        this.module = module;
        this.patternHandler = patternHandler;
        this.adjacentInventory = adjacentInventory;
        this.ingredientBuffer = ingredientBuffer;
        this.requestedIngredients = requestedIngredients;
        this.stagedCrafting = stagedCrafting;
        this.blockingHandler = blockingHandler;
        this.satelliteDispatchHandler = satelliteDispatchHandler;
    }

    PatternCraftingHudState getHudState() {
        if (shouldRefreshHudState()) {
            cachedState = buildState();
            dirty = false;
            lastBuildTick = module.currentWorldTick();
        }
        return cachedState;
    }

    boolean shouldRefreshHudState() {
        return dirty || isRecheckDue();
    }

    void markDirty() {
        dirty = true;
    }

    private boolean isRecheckDue() {
        long tick = module.currentWorldTick();
        return lastBuildTick == Long.MIN_VALUE || tick - lastBuildTick >= STATE_RECHECK_INTERVAL;
    }

    private PatternCraftingHudState buildState() {
        blockingHandler.refreshRunningCraftState(module.getConnectedInventoryTile());
        PatternCraftingHudState state = new PatternCraftingHudState(module.getEffectiveBlockingMode());
        for (int slot = 0; slot < patternHandler.size(); slot++) {
            ItemStack pattern = module.getPatternStack(slot);
            if (pattern == null) {
                continue;
            }
            state.getPatterns().add(buildPatternInfo(slot, pattern));
        }
        return state;
    }

    private PatternCraftingHudState.PatternInfo buildPatternInfo(int slot, ItemStack pattern) {
        PatternRecipeSnapshot configuredPattern = patternHandler.getRecipe(pattern);
        PatternCraftingHudState.PatternInfo patternInfo = new PatternCraftingHudState.PatternInfo(slot);
        for (int inputSlot = 0; inputSlot < configuredPattern.getIngredientSlotCount(); inputSlot++) {
            IPatternStack ingredient = configuredPattern.getInput(inputSlot);
            ItemIdentifierStack display = PatternStackHelper.makeDisplayStack(ingredient);
            if (display != null) {
                patternInfo.getIngredients().add(
                        new PatternCraftingHudState.IngredientInfo(
                                display,
                                module.bufferedIngredientAmount(slot, pattern, ingredient),
                                inputSlot));
            }
        }
        for (int outputSlot = 0; outputSlot < configuredPattern.getResultSlotCount(); outputSlot++) {
            IPatternStack output = configuredPattern.getOutput(outputSlot);
            ItemIdentifierStack display = PatternStackHelper.makeDisplayStack(output);
            if (display != null) {
                patternInfo.getOutputs().add(
                        new PatternCraftingHudState.OutputInfo(
                                display,
                                stagedCrafting.remainingOutputAmount(slot, output),
                                outputSlot));
            }
        }
        patternInfo.setActive(blockingHandler.isPatternActive(slot));
        patternInfo.setStatus(getStatus(slot, pattern));
        return patternInfo;
    }

    private String getStatus(int patternSlot, ItemStack pattern) {
        if (!module.isPatternCraftingSupported(pattern)) {
            return "Waiting: fluid crafting upgrade missing";
        }
        PipeItemsPatternCraftingLogistics.BlockingMode mode = module.getEffectiveBlockingMode();
        AdjacentTile connected = adjacentInventory.getConnected();
        int bufferedSets = module.completeBufferedSets(patternSlot);
        int pendingDispatchSlot = module.pendingDispatchSlot();
        if (pendingDispatchSlot >= 0) {
            return pendingDispatchSlot == patternSlot ? "Waiting: target full, finishing a partly inserted set"
                    : "Waiting: another pattern is finishing its set";
        }
        String satelliteStatus = blockingHandler.getHudSatelliteStatus(patternSlot);
        if (satelliteStatus != null) {
            return satelliteStatus;
        }
        if (blockingHandler.runningCraft() == patternSlot && blockingHandler.runningCraftInAdjacent()) {
            return "Doing: crafting in target inventory";
        }
        if (blockingHandler.isBlockedByOtherRunningCraft(patternSlot, connected)) {
            return "Waiting: blocking slot " + (blockingHandler.runningCraft() + 1) + " is crafting";
        }
        if (bufferedSets > 0) {
            return getBufferedStatus(patternSlot, pattern, connected, mode, bufferedSets);
        }
        String pendingIngredient = getPendingIngredient(patternSlot, pattern);
        if (pendingIngredient != null) {
            return pendingIngredient;
        }
        int stagedSets = stagedCrafting.remainingSets(patternSlot);
        if (stagedSets > 0) {
            if (!module.canReceiveForPattern(patternSlot)) {
                return blockingHandler.runningCraft() >= 0
                        ? "Waiting: blocking slot " + (blockingHandler.runningCraft() + 1)
                        : "Waiting: buffer space";
            }
            return "Doing: requesting " + formatSets(stagedSets);
        }
        if (totalAmount(ingredientBuffer.asMap().get(patternSlot)) > 0) {
            return "Waiting: buffered ingredients incomplete";
        }
        return "Idle";
    }

    private String getBufferedStatus(int patternSlot, ItemStack pattern, AdjacentTile connected,
            PipeItemsPatternCraftingLogistics.BlockingMode mode, int bufferedSets) {
        if (connected == null) {
            return "Waiting: no target inventory";
        }
        if (mode != PipeItemsPatternCraftingLogistics.BlockingMode.OFF
                && !module.isInventoryEmptyFor(connected, patternSlot)) {
            return "Waiting: target inventory occupied";
        }
        if (satelliteDispatchHandler
                .findInsertableBufferedPlan(module.completeBufferOwner(patternSlot), patternSlot, pattern, bufferedSets)
                == null) {
            return "Waiting: no target space";
        }
        return "Doing: ready to insert " + formatSets(bufferedSets);
    }

    private String getPendingIngredient(int patternSlot, ItemStack pattern) {
        for (PatternIngredientTarget target : module.getIngredientTargets(pattern)) {
            IPatternStack ingredient = target.stack();
            int buffered = module.bufferedIngredientAmount(patternSlot, pattern, ingredient);
            int requested = module.requestedIngredientAmount(patternSlot, pattern, ingredient);
            if (requested <= 0) {
                continue;
            }
            if (buffered < ingredient.getAmount()) {
                return "Waiting on " + ingredientName(
                        ingredient) + " (" + buffered + "/" + ingredient.getAmount() + ", " + requested + " routed)";
            }
            return "Waiting on " + ingredientName(ingredient) + " (" + requested + " routed)";
        }
        return totalAmount(requestedIngredients.get(patternSlot)) > 0 ? "Waiting on ingredients" : null;
    }

    private String ingredientName(IPatternStack stack) {
        ItemIdentifierStack display = PatternStackHelper.makeDisplayStack(stack);
        return display == null ? "ingredient" : display.getItem().getFriendlyName();
    }

    private String formatSets(int sets) {
        return sets == 1 ? "1 set" : sets + " sets";
    }

    private int totalAmount(List<IPatternStack> stacks) {
        int amount = 0;
        if (stacks == null) {
            return amount;
        }
        for (IPatternStack stack : stacks) {
            if (stack != null) {
                amount += Math.max(0, stack.getAmount());
            }
        }
        return amount;
    }
}
