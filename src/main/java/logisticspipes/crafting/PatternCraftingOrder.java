package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.crafting.patternStack.PatternStackHelper;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.request.IExtraPromise;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

class PatternCraftingOrder {

    private static final String TRACKS_DISPATCH_TAG = "tracksDispatch";
    private static final String BYPRODUCT_SETS_TAG = "byproductSets";
    private static final String DISPATCHED_SETS_TAG = "dispatchedSets";
    private static final String ORIGINAL_OUTPUT_AMOUNT_TAG = "originalOutputAmount";
    private static final String INHERITED_OUTPUT_AMOUNT_TAG = "inheritedOutputAmount";
    private static final String PENDING_BYPRODUCTS_TAG = "pendingByproducts";
    private static final String PRE_REQUESTED_INGREDIENTS_TAG = "preRequestedIngredients";
    private static final String PRE_INPUT_SLOT_TAG = "inputSlot";
    private static final String PRE_AMOUNT_TAG = "amount";
    private static final int TAG_COMPOUND = 10;

    final int patternSlot;
    private final PatternCraftingReference reference;
    final int resultAmountPerSet;
    final List<PatternCraftingBranch> ingredientBranches;
    int remainingSets;
    private final List<IExtraPromise> pendingByproducts = new ArrayList<>();
    private int byproductSets;
    private int dispatchedSets;
    private int originalOutputAmount;
    private int inheritedOutputAmount;
    private boolean tracksDispatch;

    final IOrderInfoProvider outputOrder;
    private final ModulePatternCrafting module;
    private final PatternStackRequestHandler requestedIngredient;
    private final Map<Integer, Integer> preRequestedIngredients = new HashMap<>();

    PatternCraftingOrder(PatternCraftingReference reference, int patternSlot, int resultAmountPerSet,
            PatternCraftingBranch branch, IOrderInfoProvider outputOrder, ModulePatternCrafting module,
            PatternStackRequestHandler requestedIngredient) {
        this.reference = reference;
        this.patternSlot = patternSlot;
        this.resultAmountPerSet = Math.max(1, resultAmountPerSet);
        branch.bindToInstance(reference);
        branch.attachDebugModule(module);
        this.ingredientBranches = new ArrayList<>(branch.getSubRequests());
        this.outputOrder = outputOrder;
        this.module = module;
        this.requestedIngredient = requestedIngredient;
        this.remainingSets = initialRemainingSets(branch);
        for (IExtraPromise promise : branch.getByproductPromises()) {
            if (promise.getProvider() == module) {
                pendingByproducts.add(promise);
            }
        }
        byproductSets = branch.getCraftingSets();
        originalOutputAmount = outputOrder.getAsDisplayItem().getStackSize();
        inheritedOutputAmount = (int) Math
                .max(0L, (long) originalOutputAmount - (long) remainingSets * resultAmountPerSet);
        tracksDispatch = true;
        module.debugEvent(
                "REQUEST",
                "created staged order slot=%d output=%s branch=%s branchRemaining=%d resultAmountPerSet=%d remainingSets=%d ingredientBranches=%d",
                patternSlot,
                outputOrder == null ? "<none>" : outputOrder.getAsDisplayItem(),
                branch.getRequestType(),
                branch.getRemainingAmount(),
                this.resultAmountPerSet,
                this.remainingSets,
                ingredientBranches.size());
    }

    PatternCraftingOrder(PatternCraftingReference reference, int patternSlot, int resultAmountPerSet, int remainingSets,
            List<PatternCraftingBranch> ingredientBranches, IOrderInfoProvider outputOrder,
            ModulePatternCrafting module, PatternStackRequestHandler requestedIngredient) {
        this.reference = reference;
        this.patternSlot = patternSlot;
        this.resultAmountPerSet = Math.max(1, resultAmountPerSet);
        this.ingredientBranches = new ArrayList<>(ingredientBranches);
        for (PatternCraftingBranch branch : this.ingredientBranches) {
            branch.bindToInstance(reference);
            branch.attachDebugModule(module);
        }
        this.outputOrder = outputOrder;
        this.module = module;
        this.requestedIngredient = requestedIngredient;
        this.remainingSets = Math.max(0, remainingSets);
        module.debugEvent(
                "REQUEST",
                "restored staged order slot=%d output=%s restoredRemainingSets=%d resultAmountPerSet=%d ingredientBranches=%d",
                patternSlot,
                outputOrder == null ? "<none>" : outputOrder.getAsDisplayItem(),
                remainingSets,
                this.resultAmountPerSet,
                ingredientBranches.size());
    }

    /** Registers the byproducts of only the sets handed to the machine by this order. */
    void ingredientsDispatched(int sets) {
        if (sets <= 0) {
            return;
        }
        int before = dispatchedSets;
        dispatchedSets += sets;
        if (byproductSets <= 0) {
            return;
        }
        for (IExtraPromise pending : pendingByproducts) {
            int amount = (int) ((long) pending.getAmount() * Math.min(dispatchedSets, byproductSets) / byproductSets
                    - (long) pending.getAmount() * Math.min(before, byproductSets) / byproductSets);
            if (amount > 0) {
                IExtraPromise promise = pending.copy();
                promise.setAmount(amount);
                module.registerExtras(promise, reference);
            }
        }
    }

    int extractableOutputAmount() {
        if (!tracksDispatch) {
            return outputOrder.getAsDisplayItem().getStackSize();
        }
        int consumed = originalOutputAmount - outputOrder.getAsDisplayItem().getStackSize();
        long available = inheritedOutputAmount + (long) dispatchedSets * resultAmountPerSet - consumed;
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, available));
    }

    /**
     * Returns true once all required ingredient sets were requested.
     * <p>
     * A branch with no ingredients is also complete here: the output order can still be fulfilled from already-produced
     * items in the connected inventory, but there is no additional subtree work to request.
     */
    boolean isFullyRequested() {
        return remainingSets <= 0 || ingredientBranches.isEmpty();
    }

    /**
     * Counts the recipe sets that still need ingredient requests for this staged slice.
     * <p>
     * Output orders may be split at amounts that are not recipe-set aligned. The extra items produced by an earlier
     * slice remain in the adjacent inventory and can satisfy the next output order without another ingredient set, so
     * the staged ingredient work is capped by the branch capacity that was allocated to this slice.
     */
    private int initialRemainingSets(PatternCraftingBranch branch) {
        int outputSets = (branch.getRequestType().getRequestedAmount() + resultAmountPerSet - 1) / resultAmountPerSet;
        return capRemainingSets(outputSets);
    }

    private int capRemainingSets(int sets) {
        ItemStack pattern = module.getPatternStack(patternSlot);
        if (pattern == null) {
            return sets;
        }
        int available = availableSetsFromBranches(pattern);
        int capped = Math.min(sets, available);
        if (capped != sets) {
            module.debugEvent(
                    "REQUEST",
                    "order capped remaining sets slot=%d requestedSets=%d branchAvailableSets=%d cappedSets=%d",
                    patternSlot,
                    sets,
                    available,
                    capped);
        }
        return capped;
    }

    /**
     * Calculates how many pattern sets can still be produced from the remaining request-tree branches.
     */
    int availableSetsFromBranches(ItemStack pattern) {
        int sets = Integer.MAX_VALUE;
        for (PatternIngredientTarget ingredient : module.getIngredientTargets(pattern)) {
            int available = availableFromBranches(ingredient) + preRequestedAmount(ingredient.inputSlot());
            sets = Math.min(sets, available / ingredient.stack().getAmount());
        }
        return sets == Integer.MAX_VALUE ? 0 : Math.max(0, sets);
    }

    /**
     * Requests ingredients for up to {@code sets} pattern sets and records those in-flight ingredients as reserved
     * module buffer space.
     */
    int requestIngredients(ItemStack pattern, int sets) {
        int requestedSets = sets;
        List<RequestedIngredient> requestedIngredients = new ArrayList<>();
        module.debugEvent(
                "REQUEST",
                "order request ingredients slot=%d requestedSetsStart=%d remainingSets=%d output=%s",
                patternSlot,
                sets,
                remainingSets,
                outputOrder == null ? "<none>" : outputOrder.getAsDisplayItem());
        for (PatternIngredientTarget ingredient : module.getIngredientTargets(pattern)) {
            int amountPerSet = ingredient.stack().getAmount();
            int requestedBefore = preRequestedAmount(ingredient.inputSlot());
            int missing = Math.max(0, amountPerSet * requestedSets - requestedBefore);
            BranchRequest requested = missing <= 0 ? BranchRequest.empty()
                    : requestFromBranches(ingredient.stack(), missing, ingredient.inputSlot(), null, null);
            if (requested.amount > 0) {
                addPreRequestedIngredient(ingredient.inputSlot(), requested.amount);
            }
            requestedIngredients.add(new RequestedIngredient(ingredient, requested.amount));
            module.debugEvent(
                    "REQUEST",
                    "order requested ingredient slot=%d ingredient=%s satellite=%s requested=%d preRequested=%d amountPerSet=%d",
                    patternSlot,
                    ingredient.stack(),
                    ingredient.hasSatelliteTarget(),
                    requested.amount,
                    preRequestedAmount(ingredient.inputSlot()),
                    amountPerSet);
            requestedSets = Math.min(requestedSets, preRequestedAmount(ingredient.inputSlot()) / amountPerSet);
        }
        for (RequestedIngredient requested : requestedIngredients) {
            if (requested.amount <= 0) {
                continue;
            }
            requestedIngredient.add(
                    reference,
                    patternSlot,
                    PatternStackHelper.copyWithAmount(requested.ingredient.stack(), requested.amount));
            module.debugEvent(
                    "BUFFER",
                    "order reserved requested ingredient slot=%d inputSlot=%d ingredient=%s requested=%d satellite=%s",
                    patternSlot,
                    requested.ingredient.inputSlot(),
                    requested.ingredient.stack(),
                    requested.amount,
                    requested.ingredient.hasSatelliteTarget());
        }
        commitPreRequested(pattern, requestedSets);
        remainingSets -= requestedSets;
        module.debugEvent(
                "REQUEST",
                "order request ingredients slot=%d requestedSetsFinal=%d remainingSets=%d",
                patternSlot,
                requestedSets,
                remainingSets);
        return requestedSets;
    }

    /**
     * Releases provider reservations still owned by this staged order.
     */
    void releaseReservations() {
        module.debugEvent(
                "REQUEST",
                "order release reservations slot=%d branches=%d remainingSets=%d output=%s",
                patternSlot,
                ingredientBranches.size(),
                remainingSets,
                outputOrder == null ? "<none>" : outputOrder.getAsDisplayItem());
        for (PatternCraftingBranch branch : ingredientBranches) {
            branch.releaseProviderPromises();
        }
    }

    PatternCraftingReference reference() {
        return reference;
    }

    ModulePatternCrafting module() {
        return module;
    }

    /**
     * Persists runtime-only scheduler state that is not part of the original request tree.
     */
    void writeRuntimeState(NBTTagCompound tag) {
        tag.setBoolean(TRACKS_DISPATCH_TAG, tracksDispatch);
        tag.setInteger(BYPRODUCT_SETS_TAG, byproductSets);
        tag.setInteger(DISPATCHED_SETS_TAG, dispatchedSets);
        tag.setInteger(ORIGINAL_OUTPUT_AMOUNT_TAG, originalOutputAmount);
        tag.setInteger(INHERITED_OUTPUT_AMOUNT_TAG, inheritedOutputAmount);
        NBTTagList byproducts = new NBTTagList();
        for (IExtraPromise promise : pendingByproducts) {
            NBTTagCompound promiseTag = new NBTTagCompound();
            if (PatternCraftingPersistence.writePromise(promiseTag, promise)) {
                byproducts.appendTag(promiseTag);
            }
        }
        tag.setTag(PENDING_BYPRODUCTS_TAG, byproducts);
        NBTTagList preRequested = new NBTTagList();
        for (Map.Entry<Integer, Integer> entry : preRequestedIngredients.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            NBTTagCompound entryTag = new NBTTagCompound();
            entryTag.setInteger(PRE_INPUT_SLOT_TAG, entry.getKey());
            entryTag.setInteger(PRE_AMOUNT_TAG, entry.getValue());
            preRequested.appendTag(entryTag);
        }
        if (preRequested.tagCount() > 0) {
            tag.setTag(PRE_REQUESTED_INGREDIENTS_TAG, preRequested);
        }

    }

    /**
     * Restores runtime scheduler state saved with a staged order.
     */
    void readRuntimeState(NBTTagCompound tag) {
        tracksDispatch = tag.getBoolean(TRACKS_DISPATCH_TAG);
        byproductSets = tag.getInteger(BYPRODUCT_SETS_TAG);
        dispatchedSets = tag.getInteger(DISPATCHED_SETS_TAG);
        originalOutputAmount = tag.getInteger(ORIGINAL_OUTPUT_AMOUNT_TAG);
        inheritedOutputAmount = tag.getInteger(INHERITED_OUTPUT_AMOUNT_TAG);
        pendingByproducts.clear();
        NBTTagList byproducts = tag.getTagList(PENDING_BYPRODUCTS_TAG, TAG_COMPOUND);
        for (int i = 0; i < byproducts.tagCount(); i++) {
            pendingByproducts.add(PatternCraftingPersistence.readExtraPromise(byproducts.getCompoundTagAt(i)));
        }
        preRequestedIngredients.clear();
        NBTTagList preRequested = tag.getTagList(PRE_REQUESTED_INGREDIENTS_TAG, TAG_COMPOUND);
        for (int i = 0; i < preRequested.tagCount(); i++) {
            NBTTagCompound entryTag = preRequested.getCompoundTagAt(i);
            int amount = entryTag.getInteger(PRE_AMOUNT_TAG);
            if (amount > 0) {
                preRequestedIngredients.put(entryTag.getInteger(PRE_INPUT_SLOT_TAG), amount);
            }
        }

    }

    /**
     * Appends this staged order and its ingredient branches to the crafting request debug dump.
     */
    void appendDebugState(StringBuilder out, String prefix) {
        out.append(prefix).append("- Pattern slot ").append(patternSlot).append(" reference=").append(reference)
                .append(" remainingSets=").append(remainingSets).append(" resultAmountPerSet=")
                .append(resultAmountPerSet).append(" outputOrder=")
                .append(outputOrder == null ? "<none>" : outputOrder.getAsDisplayItem()).append(" branches=")
                .append(ingredientBranches.size()).append("\n");
        if (!preRequestedIngredients.isEmpty()) {
            out.append(prefix).append("  preRequested=").append(preRequestedIngredients).append("\n");
        }
        for (PatternCraftingBranch branch : ingredientBranches) {
            branch.appendDebugState(out, prefix + "  ");
        }
    }

    /**
     * Builds a renderer node whose count follows the live output order amount.
     */
    PatternCraftingMonitorNode toMonitorNode(Set<PatternCraftingOrder> visitedOrders) {
        visitedOrders.add(this);
        ItemIdentifierStack display = outputOrder.getAsDisplayItem().clone();
        display.setStackSize(Math.max(0, display.getStackSize()));
        PatternCraftingMonitorNode node = new PatternCraftingMonitorNode(
                display,
                0,
                display.getStackSize(),
                outputOrder.isInProgress() || !outputOrder.getProgresses().isEmpty());
        for (PatternCraftingBranch branch : ingredientBranches) {
            node.addChild(branch.toMonitorNode(visitedOrders));
        }
        return node;
    }

    void collectNestedCraftingOrders(Set<PatternCraftingOrder> nestedOrders) {
        for (PatternCraftingBranch branch : ingredientBranches) {
            branch.collectNestedCraftingOrders(nestedOrders);
        }
    }

    /**
     * Returns the amount still available for one ingredient across matching branches.
     */
    private int availableFromBranches(PatternIngredientTarget ingredient) {
        int available = 0;
        for (PatternCraftingBranch branch : ingredientBranches) {
            if (branchMatches(branch, ingredient)) {
                available += branch.getRemainingAmount();
            }
        }
        return available;
    }

    private int preRequestedAmount(int inputSlot) {
        return Math.max(0, preRequestedIngredients.getOrDefault(inputSlot, 0));
    }

    private void addPreRequestedIngredient(int inputSlot, int amount) {
        if (amount <= 0) {
            return;
        }
        preRequestedIngredients.put(inputSlot, preRequestedAmount(inputSlot) + amount);
    }

    private void commitPreRequested(ItemStack pattern, int sets) {
        if (sets <= 0) {
            return;
        }
        for (PatternIngredientTarget ingredient : module.getIngredientTargets(pattern)) {
            int inputSlot = ingredient.inputSlot();
            int remaining = preRequestedAmount(inputSlot) - ingredient.stack().getAmount() * sets;
            if (remaining <= 0) {
                preRequestedIngredients.remove(inputSlot);
            } else {
                preRequestedIngredients.put(inputSlot, remaining);
            }
        }
    }

    /**
     * Places provider or staged crafting orders for an ingredient, consuming the matching branch state as it goes.
     */
    private BranchRequest requestFromBranches(IPatternStack ingredient, int amount, int inputSlot,
            IRequestItems itemTargetOverride, IRequestFluid fluidTargetOverride) {
        int requested = 0;
        for (PatternCraftingBranch branch : ingredientBranches) {
            if (requested >= amount) {
                break;
            }
            if (!branchMatches(branch, ingredient, inputSlot)) {
                continue;
            }
            int before = branch.getRemainingAmount();
            if (PatternStackHelper.isFluid(ingredient)) {
                PatternTargetInformation target = new PatternTargetInformation(patternSlot, inputSlot, reference, null);
                int branchRequested = branch.request(amount - requested, fluidTargetOverride, target);
                requested += branchRequested;
                module.debugEvent(
                        "REQUEST",
                        "branch fluid request slot=%d ingredient=%s target=%s branch=%s branchRemaining=%d->%d requested=%d total=%d/%d",
                        patternSlot,
                        ingredient,
                        fluidTargetOverride,
                        branch.getRequestType(),
                        before,
                        branch.getRemainingAmount(),
                        branchRequested,
                        requested,
                        amount);
            } else {
                PatternTargetInformation target = new PatternTargetInformation(patternSlot, inputSlot, reference, null);
                int branchRequested = branch.request(amount - requested, itemTargetOverride, target);
                requested += branchRequested;
                module.debugEvent(
                        "REQUEST",
                        "branch item request slot=%d ingredient=%s target=%s branch=%s branchRemaining=%d->%d requested=%d total=%d/%d",
                        patternSlot,
                        ingredient,
                        itemTargetOverride,
                        branch.getRequestType(),
                        before,
                        branch.getRemainingAmount(),
                        branchRequested,
                        requested,
                        amount);
            }
        }
        return new BranchRequest(requested);
    }

    /**
     * Checks whether a staged branch can provide the requested item or fluid ingredient.
     */
    private boolean branchMatches(PatternCraftingBranch branch, PatternIngredientTarget ingredient) {
        return branchMatches(branch, ingredient.stack(), ingredient.inputSlot());
    }

    private boolean branchMatches(PatternCraftingBranch branch, IPatternStack ingredient, int inputSlot) {
        if (!branchTargetsInputSlot(branch, inputSlot)) {
            return false;
        }
        FluidIdentifier fluid = PatternStackHelper.asFluid(ingredient);
        if (fluid != null) {
            return branch.matches(fluid);
        }
        ItemIdentifier item = PatternStackHelper.getRoutingItem(ingredient);
        return item != null && branch.matches(item);
    }

    private boolean branchTargetsInputSlot(PatternCraftingBranch branch, int inputSlot) {
        if (branch.getTargetInformation() instanceof PatternTargetInformation target) {
            return target.inputSlot() == inputSlot || target.inputSlot() == PatternTargetInformation.NO_INPUT_SLOT;
        }
        return true;
    }

    private static class BranchRequest {

        private final int amount;

        private BranchRequest(int amount) {
            this.amount = amount;
        }

        private static BranchRequest empty() {
            return new BranchRequest(0);
        }
    }

    private static class RequestedIngredient {

        private final PatternIngredientTarget ingredient;
        private final int amount;

        private RequestedIngredient(PatternIngredientTarget ingredient, int amount) {
            this.ingredient = ingredient;
            this.amount = amount;
        }
    }

}
