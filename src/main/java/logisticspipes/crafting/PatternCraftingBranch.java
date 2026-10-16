package logisticspipes.crafting;

import java.util.*;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.request.IExtraPromise;
import logisticspipes.request.IPromise;
import logisticspipes.request.resources.DictResource;
import logisticspipes.request.resources.FluidResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.request.resources.ItemResource;
import logisticspipes.routing.FluidLogisticsPromise;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.routing.order.LogisticsOrder;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import lombok.Getter;

public class PatternCraftingBranch {

    private static final String REQUEST_TYPE_TAG = "requestType";
    private static final String ORIGINAL_AMOUNT_TAG = "originalAmount";
    private static final String REMAINING_AMOUNT_TAG = "remainingAmount";
    private static final String ORIGINAL_CRAFTING_AMOUNT_TAG = "originalCraftingAmount";
    private static final String REMAINING_CRAFTING_AMOUNT_TAG = "remainingCraftingAmount";
    private static final String PROMISES_TAG = "promises";
    private static final String PROMISE_TAG = "promise";
    private static final String PROVIDER_RESERVED_TAG = "providerReserved";
    private static final String EXTRA_PROMISES_TAG = "extraPromises";
    private static final String BYPRODUCTS_TAG = "byproducts";
    private static final String ORIGINAL_EXTRA_AMOUNT_TAG = "originalExtraAmount";
    private static final String SUB_REQUESTS_TAG = "subRequests";
    private static final String REFERENCE_PREFIX = "branch";
    private static final int TAG_COMPOUND = 10;

    @Getter
    private final IResource requestType;
    private final IAdditionalTargetInformation info;
    private final int originalAmount;
    private final int originalCraftingAmount;
    private final int originalCraftingSets;
    private final List<PromiseState> promises;
    private final List<ExtraState> extraPromises;
    private final List<ExtraState> byproducts;
    private final List<PatternCraftingBranch> subRequests;
    private final List<IOrderInfoProvider> liveOrders = new ArrayList<>();
    @Getter
    private int remainingAmount;
    private int remainingCraftingAmount;
    private PatternCraftingReference reference;
    private transient ModulePatternCrafting debugModule;

    /**
     * Captures the request-tree state that belongs to one staged crafting output.
     * <p>
     * The branch keeps copies of provider promises, crafting promises, extras, byproducts, and child branches so the
     * pattern pipe can request ingredients later without rebuilding the original request tree.
     */
    public PatternCraftingBranch(IResource requestType, IAdditionalTargetInformation info, List<IPromise> promises,
            List<IExtraPromise> extraPromises, List<IExtraPromise> byproducts,
            List<PatternCraftingBranch> subRequests) {
        this(
                requestType,
                info,
                requestType.getRequestedAmount(),
                requestType.getRequestedAmount(),
                copyPromiseStates(promises),
                copyExtraStates(extraPromises),
                copyExtraStates(byproducts),
                subRequests);
    }

    private PatternCraftingBranch(IResource requestType, IAdditionalTargetInformation info, int originalAmount,
            int remainingAmount, List<PromiseState> promises, List<ExtraState> extraPromises,
            List<ExtraState> byproducts, List<PatternCraftingBranch> subRequests) {
        this(
                requestType,
                info,
                originalAmount,
                remainingAmount,
                countCraftingAmount(promises),
                countCraftingAmount(promises),
                promises,
                extraPromises,
                byproducts,
                subRequests);
    }

    private PatternCraftingBranch(IResource requestType, IAdditionalTargetInformation info, int originalAmount,
            int remainingAmount, int originalCraftingAmount, int remainingCraftingAmount, List<PromiseState> promises,
            List<ExtraState> extraPromises, List<ExtraState> byproducts, List<PatternCraftingBranch> subRequests) {
        this.requestType = requestType;
        this.info = info;
        this.originalAmount = originalAmount;
        this.remainingAmount = remainingAmount;
        this.promises = promises;
        this.originalCraftingAmount = originalCraftingAmount;
        this.originalCraftingSets = countCraftingSets(promises);
        this.remainingCraftingAmount = remainingCraftingAmount;
        this.extraPromises = extraPromises;
        this.byproducts = mergeByproductStates(byproducts);
        this.subRequests = mergeCompatibleBranches(subRequests);
    }

    static PatternCraftingBranch readFromNBT(NBTTagCompound tag) {
        IResource requestType = PatternCraftingPersistence.readResource(tag.getCompoundTag(REQUEST_TYPE_TAG));
        PatternCraftingBranch branch = new PatternCraftingBranch(
                requestType,
                PatternCraftingPersistence.readTargetInfoFromParent(tag),
                tag.getInteger(ORIGINAL_AMOUNT_TAG),
                tag.getInteger(REMAINING_AMOUNT_TAG),
                tag.getInteger(ORIGINAL_CRAFTING_AMOUNT_TAG),
                tag.getInteger(REMAINING_CRAFTING_AMOUNT_TAG),
                readPromiseStates(tag.getTagList(PROMISES_TAG, TAG_COMPOUND)),
                readExtraStates(tag.getTagList(EXTRA_PROMISES_TAG, TAG_COMPOUND)),
                readExtraStates(tag.getTagList(BYPRODUCTS_TAG, TAG_COMPOUND)),
                readSubRequests(tag.getTagList(SUB_REQUESTS_TAG, TAG_COMPOUND)));
        branch.reference = PatternCraftingReference.readFromNBT(tag, REFERENCE_PREFIX);
        return branch;
    }

    /**
     * Combines equivalent sibling branches that were split while the request tree probed partial crafting capacity.
     */
    private static List<PatternCraftingBranch> mergeCompatibleBranches(List<PatternCraftingBranch> branches) {
        List<PatternCraftingBranch> merged = new ArrayList<>();
        for (PatternCraftingBranch branch : branches) {
            int index = findCompatibleBranch(merged, branch);
            if (index < 0) {
                merged.add(branch);
            } else {
                merged.set(index, merged.get(index).mergeWith(branch));
            }
        }
        return merged;
    }

    private static int findCompatibleBranch(List<PatternCraftingBranch> branches, PatternCraftingBranch candidate) {
        for (int i = 0; i < branches.size(); i++) {
            if (branches.get(i).canMergeWith(candidate)) {
                return i;
            }
        }
        return -1;
    }

    private static List<PromiseState> copyPromiseStates(List<IPromise> promises) {
        List<PromiseState> result = new ArrayList<>();
        for (IPromise promise : promises) {
            result.add(new PromiseState(promise.copy(), promise.getAmount(), false));
        }
        return result;
    }

    /**
     * Copies extra promises with their original branch amount.
     */
    private static List<ExtraState> copyExtraStates(List<IExtraPromise> promises) {
        List<ExtraState> result = new ArrayList<>();
        for (IExtraPromise promise : promises) {
            result.add(new ExtraState(promise.copy()));
        }
        return result;
    }

    private static List<PromiseState> readPromiseStates(NBTTagList list) {
        List<PromiseState> result = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound stateTag = list.getCompoundTagAt(i);
            IPromise promise = PatternCraftingPersistence.readPromise(stateTag.getCompoundTag(PROMISE_TAG));
            // Provider reservation maps are runtime-only. Restored branches reserve their remaining provider promises
            // after all orders have been recreated, so this flag intentionally starts clear after loading.
            result.add(new PromiseState(promise, stateTag.getInteger(REMAINING_AMOUNT_TAG), false));
        }
        return result;
    }

    private static List<ExtraState> readExtraStates(NBTTagList list) {
        List<ExtraState> result = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound stateTag = list.getCompoundTagAt(i);
            IExtraPromise promise = PatternCraftingPersistence.readExtraPromise(stateTag.getCompoundTag(PROMISE_TAG));
            result.add(new ExtraState(promise, stateTag.getInteger(ORIGINAL_EXTRA_AMOUNT_TAG)));
        }
        return result;
    }

    private static List<PatternCraftingBranch> readSubRequests(NBTTagList list) {
        List<PatternCraftingBranch> result = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) {
            result.add(readFromNBT(list.getCompoundTagAt(i)));
        }
        return result;
    }

    /**
     * Counts how much of this branch is fulfilled by crafting promises and can therefore produce extras or byproducts.
     */
    private static int countCraftingAmount(List<PromiseState> promises) {
        int amount = 0;
        for (PromiseState promise : promises) {
            if (promise.promise.getType() == ResourceType.CRAFTING) {
                amount += promise.remainingAmount;
            }
        }
        return amount;
    }

    /**
     * Counts the crafting sets represented by the original promise amounts.
     */
    private static int countCraftingSets(List<PromiseState> promises) {
        int sets = 0;
        for (PromiseState promise : promises) {
            if (promise.promise.getType() == ResourceType.CRAFTING) {
                sets += craftingSetsForAmount(promise.promise, promise.promise.getAmount());
            }
        }
        return sets;
    }

    private static int craftingSetsForAmount(IPromise promise, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int resultAmountPerSet = resultAmountPerSet(promise);
        return (amount + resultAmountPerSet - 1) / resultAmountPerSet;
    }

    private static int resultAmountPerSet(IPromise promise) {
        if (promise instanceof PatternCraftingPromise) {
            return Math.max(1, ((PatternCraftingPromise) promise).getResultAmountPerSet());
        }
        if (promise instanceof PatternFluidCraftingPromise) {
            return Math.max(1, ((PatternFluidCraftingPromise) promise).getResultAmountPerSet());
        }
        return 1;
    }

    /**
     * Creates a promise copy with the requested amount while keeping the source promise untouched.
     */
    private static IPromise copyPromiseForAmount(IPromise promise, int amount) {
        if (promise instanceof PatternCraftingPromise) {
            return ((PatternCraftingPromise) promise).copyWithAmount(amount);
        }
        if (promise instanceof FluidLogisticsPromise) {
            return ((FluidLogisticsPromise) promise).copyWithAmount(amount);
        }
        IPromise copy = promise.copy();
        if (copy instanceof LogisticsPromise) {
            ((LogisticsPromise) copy).numberOfItems = amount;
            return copy;
        }
        if (copy.getAmount() > amount) {
            copy.split(copy.getAmount() - amount);
        }
        return copy;
    }

    /**
     * Scales {@code amount} by {@code numerator / denominator}, rounding up so partial craft sets are represented.
     */
    private static int scaleAmount(int amount, int numerator, int denominator) {
        if (amount <= 0 || numerator <= 0 || denominator <= 0) {
            return 0;
        }
        long scaled = (long) amount * numerator;
        int result = (int) (scaled / denominator);
        if (scaled % denominator != 0) {
            result++;
        }
        return Math.min(amount, result);
    }

    /**
     * Returns child branches that must be requested to satisfy this branch.
     */
    public List<PatternCraftingBranch> getSubRequests() {
        return Collections.unmodifiableList(subRequests);
    }

    /**
     * Returns the target metadata captured from the request-tree component that created this branch.
     */
    IAdditionalTargetInformation getTargetInformation() {
        return info;
    }

    /**
     * The module that receives debug events from this branch
     *
     * @param module the module
     */
    void attachDebugModule(ModulePatternCrafting module) {
        debugModule = module;
        for (PatternCraftingBranch child : subRequests) {
            child.attachDebugModule(module);
        }
    }

    private static List<ExtraState> mergeByproductStates(List<ExtraState> states) {
        List<ExtraState> result = new ArrayList<>();
        for (ExtraState state : states) {
            ExtraState matching = null;
            for (ExtraState candidate : result) {
                if (candidate.promise.getClass() == state.promise.getClass()
                        && candidate.promise.getProvider() == state.promise.getProvider()
                        && candidate.promise.getItemType().equals(state.promise.getItemType())
                        && Objects.equals(byproductTarget(candidate.promise), byproductTarget(state.promise))) {
                    matching = candidate;
                    break;
                }
            }
            if (matching == null) {
                result.add(state);
            } else {
                IExtraPromise combined = matching.promise.copy();
                combined.setAmount(matching.originalAmount + state.originalAmount);
                result.set(result.indexOf(matching), new ExtraState(combined));
            }
        }
        return result;
    }

    private static PatternByproductTarget byproductTarget(IExtraPromise promise) {
        return promise instanceof PatternByproductPromise byproduct ? byproduct.getByproductTarget() : null;
    }

    private boolean producesByproduct(IPromise promise, IExtraPromise byproduct) {
        if (promise.getType() != ResourceType.CRAFTING || promise.getProvider() != byproduct.getProvider()) {
            return false;
        }
        PatternByproductTarget target = byproductTarget(byproduct);
        if (target == null || target.getPatternSlot() < 0) {
            return true;
        }
        if (promise instanceof PatternCraftingPromise patternPromise) {
            return patternPromise.getPatternSlot() == target.getPatternSlot();
        }
        if (promise instanceof PatternFluidCraftingPromise patternPromise) {
            return patternPromise.getPatternSlot() == target.getPatternSlot();
        }
        return false;
    }

    private int byproductAmountForNext(ExtraState state, int craftingAmount) {
        int before = 0;
        int after = 0;
        int total = 0;
        int left = Math.min(craftingAmount, remainingCraftingAmount);
        for (PromiseState promise : promises) {
            if (promise.promise.getType() != ResourceType.CRAFTING) {
                continue;
            }
            int moved = Math.min(left, promise.remainingAmount);
            left -= moved;
            if (!producesByproduct(promise.promise, state.promise)) {
                continue;
            }
            int consumed = promise.promise.getAmount() - promise.remainingAmount;
            before += craftingSetsForAmount(promise.promise, consumed);
            after += craftingSetsForAmount(promise.promise, consumed + moved);
            total += craftingSetsForAmount(promise.promise, promise.promise.getAmount());
        }
        return state.amountForRange(before, after, total);
    }

    int getCraftingSets() {
        return originalCraftingSets;
    }

    List<IExtraPromise> getByproductPromises() {
        List<IExtraPromise> result = new ArrayList<>();
        for (ExtraState state : byproducts) {
            IExtraPromise promise = state.promise.copy();
            promise.setAmount(state.originalAmount);
            result.add(promise);
        }
        return result;
    }

    void bindToInstance(PatternCraftingReference ownerReference) {
        if (ownerReference == null) {
            return;
        }
        if (reference == null || !reference.belongsTo(ownerReference)) {
            reference = ownerReference.createChild();
        }
        for (PatternCraftingBranch child : subRequests) {
            child.bindToInstance(ownerReference);
        }
    }

    void writeToNBT(NBTTagCompound tag) {
        NBTTagCompound resourceTag = new NBTTagCompound();
        if (PatternCraftingPersistence.writeResource(resourceTag, requestType)) {
            tag.setTag(REQUEST_TYPE_TAG, resourceTag);
        }
        PatternCraftingPersistence.writeTargetInfo(tag, info);
        if (reference != null) {
            reference.writeToNBT(tag, REFERENCE_PREFIX);
        }
        tag.setInteger(ORIGINAL_AMOUNT_TAG, originalAmount);
        tag.setInteger(REMAINING_AMOUNT_TAG, remainingAmount);
        tag.setInteger(ORIGINAL_CRAFTING_AMOUNT_TAG, originalCraftingAmount);
        tag.setInteger(REMAINING_CRAFTING_AMOUNT_TAG, remainingCraftingAmount);
        tag.setTag(PROMISES_TAG, writePromiseStates());
        tag.setTag(EXTRA_PROMISES_TAG, writeExtraStates(extraPromises));
        tag.setTag(BYPRODUCTS_TAG, writeExtraStates(byproducts));
        tag.setTag(SUB_REQUESTS_TAG, writeSubRequests());
    }

    /**
     * Appends the remaining staged branch state to the crafting request debug dump.
     */
    public void appendDebugState(StringBuilder out, String prefix) {
        out.append(prefix).append("- Branch ").append(requestType).append(" reference=").append(reference)
                .append(" remaining=").append(remainingAmount).append("/").append(originalAmount)
                .append(" craftingRemaining=").append(remainingCraftingAmount).append("/")
                .append(originalCraftingAmount).append("\n");
        appendPromises(out, prefix + "  ");
        appendLiveOrders(out, prefix + "  ");
        appendExtraStates(out, prefix + "  ", "extras", extraPromises);
        appendExtraStates(out, prefix + "  ", "byproducts", byproducts);
        if (!subRequests.isEmpty()) {
            out.append(prefix).append("  subrequests:\n");
            for (PatternCraftingBranch subRequest : subRequests) {
                subRequest.appendDebugState(out, prefix + "    ");
            }
        }
    }

    /**
     * Checks whether this branch represents the requested item.
     */
    public boolean matches(ItemIdentifier item) {
        return requestType.matches(item, IResource.MatchSettings.NORMAL);
    }

    /**
     * Checks whether this branch represents the requested fluid.
     */
    public boolean matches(FluidIdentifier fluid) {
        return fluid != null && requestType.matches(fluid.getItemIdentifier(), IResource.MatchSettings.NORMAL);
    }

    /**
     * Builds a live renderer node from this branch and all order references that were created from it.
     */
    PatternCraftingMonitorNode toMonitorNode(Set<PatternCraftingOrder> visitedOrders) {
        int orderedAmount = getLiveOrderAmount();
        int totalAmount = Math.max(0, remainingAmount + orderedAmount);
        ItemIdentifierStack display = requestType.getDisplayItem().clone();
        display.setStackSize(totalAmount);
        PatternCraftingMonitorNode node = new PatternCraftingMonitorNode(
                display,
                remainingAmount,
                orderedAmount,
                hasInProgressOrders());
        for (PatternCraftingBranch subRequest : subRequests) {
            node.addChild(subRequest.toMonitorNode(visitedOrders));
        }
        for (IOrderInfoProvider order : liveOrders) {
            PatternCraftingOrder stagedOrder = PatternCraftingMonitorRegistry.find(order);
            if (stagedOrder == null || !visitedOrders.add(stagedOrder)) {
                continue;
            }
            PatternCraftingMonitorNode stagedNode = stagedOrder.toMonitorNode(visitedOrders);
            if (stagedNode.getStack() != null && stagedNode.getStack().getItem().equalsForCrafting(display.getItem())) {
                node.addChildren(stagedNode.getChildren());
            } else {
                node.addChild(stagedNode);
            }
        }
        return node;
    }

    void collectNestedCraftingOrders(Set<PatternCraftingOrder> nestedOrders) {
        for (PatternCraftingBranch subRequest : subRequests) {
            subRequest.collectNestedCraftingOrders(nestedOrders);
        }
        for (IOrderInfoProvider order : liveOrders) {
            PatternCraftingOrder stagedOrder = PatternCraftingMonitorRegistry.find(order);
            if (stagedOrder != null && nestedOrders.add(stagedOrder)) {
                stagedOrder.collectNestedCraftingOrders(nestedOrders);
            }
        }
    }

    private void debugBranchEvent(String category, String message, Object... args) {
        ModulePatternCrafting module = findDebugModule();
        if (module != null) {
            module.debugEvent(category, message, args);
        }
    }

    private ModulePatternCrafting findDebugModule() {
        if (debugModule != null) {
            return debugModule;
        }
        for (PromiseState promise : promises) {
            if (promise.promise.getProvider() instanceof ModulePatternCrafting) {
                return (ModulePatternCrafting) promise.promise.getProvider();
            }
        }
        for (PatternCraftingBranch child : subRequests) {
            ModulePatternCrafting module = child.findDebugModule();
            if (module != null) {
                return module;
            }
        }
        return null;
    }

    /**
     * Fulfils up to {@code amount} items from this branch and advances the branch state by the amount actually ordered.
     * <p>
     * Crafting promises pass their proportional child branch to the staged crafting pipe. Provider promises are
     * fulfilled directly, releasing any reservation that was made for this staged craft.
     */
    public int request(int amount) {
        return request(amount, null, null, info);
    }

    /**
     * Fulfils up to {@code amount} items from this branch while optionally routing the order to another requester.
     * <p>
     * Most staged pattern ingredients use the original requester stored on the branch. The override remains for legacy
     * callers that need to send a branch slice to a different item requester.
     */
    public int request(int amount, IRequestItems targetOverride, IAdditionalTargetInformation infoOverride) {
        return request(amount, targetOverride, null, infoOverride);
    }

    /**
     * Fulfils up to {@code amount} fluids from this branch while optionally routing the order to another requester.
     * <p>
     * Most staged pattern ingredients use the original requester stored on the branch. The override remains for legacy
     * callers that need to send a branch slice to a different fluid requester.
     */
    public int request(int amount, IRequestFluid targetOverride, IAdditionalTargetInformation infoOverride) {
        return request(amount, null, targetOverride, infoOverride);
    }

    private int request(int amount, IRequestItems targetOverride, IRequestFluid fluidTargetOverride,
            IAdditionalTargetInformation infoOverride) {
        int wanted = Math.min(amount, remainingAmount);
        int requested = 0;
        debugBranchEvent(
                "BRANCH",
                "branch request start resource=%s amount=%d wanted=%d remaining=%d craftingRemaining=%d promises=%d itemTarget=%s fluidTarget=%s info=%s",
                requestType,
                amount,
                wanted,
                remainingAmount,
                remainingCraftingAmount,
                promises.size(),
                targetOverride,
                fluidTargetOverride,
                infoOverride);

        for (int promiseIndex = 0; promiseIndex < promises.size() && requested < wanted; promiseIndex++) {
            PromiseState promiseState = promises.get(promiseIndex);
            int toRequest = requestAmountForPromiseBatch(promiseIndex, wanted - requested);
            if (toRequest <= 0) {
                continue;
            }
            debugBranchEvent(
                    "BRANCH",
                    "branch promise slice resource=%s index=%d promise=%s type=%s provider=%s promiseRemaining=%d toRequest=%d requested=%d/%d",
                    requestType,
                    promiseIndex,
                    promiseState.promise.getItemType(),
                    promiseState.promise.getType(),
                    promiseState.promise.getProvider(),
                    promiseState.remainingAmount,
                    toRequest,
                    requested,
                    wanted);
            IPromise promise = copyPromiseForAmount(promiseState.promise, toRequest);
            IResource request = copyRequestForTarget(toRequest, targetOverride, fluidTargetOverride);
            IAdditionalTargetInformation orderInfo = createOrderTarget(infoOverride);
            IOrderInfoProvider result;
            boolean requestSubRequestsAfterOrder = false;
            if (promise.getType() == ResourceType.CRAFTING
                    && promise.getProvider() instanceof IStagedCraftingProvider) {
                PatternCraftingBranch stagedBranch = copyForAmount(toRequest);
                stagedBranch.reserveProviderPromises();
                debugBranchEvent(
                        "BRANCH",
                        "branch staged handoff resource=%s toRequest=%d stagedRemaining=%d stagedCraftingRemaining=%d childBranches=%d provider=%s info=%s",
                        requestType,
                        toRequest,
                        stagedBranch.remainingAmount,
                        stagedBranch.remainingCraftingAmount,
                        stagedBranch.subRequests.size(),
                        promise.getProvider(),
                        infoOverride);
                result = ((IStagedCraftingProvider) promise.getProvider())
                        .fullFillStagedCrafting(promise, request, orderInfo, stagedBranch);
                if (result == null) {
                    debugBranchEvent(
                            "BRANCH",
                            "branch staged handoff rejected resource=%s toRequest=%d provider=%s",
                            requestType,
                            toRequest,
                            promise.getProvider());
                    stagedBranch.releaseProviderPromises();
                }
            } else {
                if (promise.getType() == ResourceType.CRAFTING) {
                    requestSubRequestsAfterOrder = true;
                }
                result = promise.fullFill(request, orderInfo);
            }

            if (result instanceof LogisticsOrder logisticsOrder && logisticsOrder.getCraftingReference() == null
                    && orderInfo instanceof PatternTargetInformation target
                    && target.isTracked()) {
                logisticsOrder.setCraftingReference(target.deliveryReference());
            }

            if (result == null) {
                debugBranchEvent(
                        "BRANCH",
                        "branch promise request failed resource=%s toRequest=%d type=%s provider=%s",
                        requestType,
                        toRequest,
                        promise.getType(),
                        promise.getProvider());
                continue;
            }

            debugBranchEvent(
                    "BRANCH",
                    "branch promise request accepted resource=%s toRequest=%d result=%s type=%s provider=%s",
                    requestType,
                    toRequest,
                    result.getAsDisplayItem(),
                    promise.getType(),
                    promise.getProvider());
            liveOrders.add(result);
            if (promise.getType() == ResourceType.CRAFTING) {
                if (requestSubRequestsAfterOrder) {
                    requestSubRequestsFor(toRequest);
                }
                if (promise.getProvider() instanceof IStagedCraftingProvider) {
                    reserveSubRequestsFor(toRequest);
                }
                if (promise.getProvider() instanceof IStagedCraftingProvider) {
                    registerOverflowExtrasFor(extraPromises, toRequest);
                } else {
                    registerExtrasFor(toRequest);
                }
                remainingCraftingAmount -= toRequest;
            }
            consumePromiseBatch(promiseIndex, promiseState.promise, toRequest);
            remainingAmount -= toRequest;
            requested += toRequest;
            debugBranchEvent(
                    "BRANCH",
                    "branch consumed resource=%s consumed=%d requested=%d/%d remaining=%d craftingRemaining=%d",
                    requestType,
                    toRequest,
                    requested,
                    wanted,
                    remainingAmount,
                    remainingCraftingAmount);
        }

        debugBranchEvent(
                "BRANCH",
                "branch request end resource=%s requested=%d wanted=%d remaining=%d craftingRemaining=%d",
                requestType,
                requested,
                wanted,
                remainingAmount,
                remainingCraftingAmount);
        return requested;
    }

    private IAdditionalTargetInformation createOrderTarget(IAdditionalTargetInformation infoOverride) {
        if (!(infoOverride instanceof PatternTargetInformation target) || target.orderReference() == null) {
            return infoOverride;
        }
        return PatternTargetInformation.delivery(target.patternSlot(), target.inputSlot(), target.orderReference());
    }

    private IResource copyRequestForTarget(int amount, IRequestItems targetOverride,
            IRequestFluid fluidTargetOverride) {
        if (targetOverride == null && fluidTargetOverride == null) {
            return requestType.copyForDisplayWith(amount);
        }
        if (targetOverride != null && requestType instanceof ItemResource) {
            return new ItemResource(
                    new ItemIdentifierStack(((ItemResource) requestType).getItem(), amount),
                    targetOverride);
        }
        if (targetOverride != null && requestType instanceof DictResource source) {
            DictResource copy = new DictResource(new ItemIdentifierStack(source.getItem(), amount), targetOverride);
            copy.use_od = source.use_od;
            copy.ignore_dmg = source.ignore_dmg;
            copy.ignore_nbt = source.ignore_nbt;
            copy.use_category = source.use_category;
            copy.match_same_item = source.match_same_item;
            return copy;
        }
        if (fluidTargetOverride != null && requestType instanceof FluidResource source) {
            return new FluidResource(source.getFluid(), amount, fluidTargetOverride);
        }
        return requestType.copyForDisplayWith(amount);
    }

    /**
     * Creates an immutable work branch for the next {@code amount} items without consuming this branch.
     * <p>
     * Child branches are copied by cumulative consumption instead of scaling the current remainder. This keeps repeated
     * small requests from rounding up the same child dependency over and over again.
     */
    public PatternCraftingBranch copyForAmount(int amount) {
        int copiedAmount = Math.min(amount, remainingAmount);
        IResource copiedRequest = requestType.copyForDisplayWith(copiedAmount);
        List<PromiseState> copiedPromises = copyPromiseStatesFor(copiedAmount);
        int copiedCraftingAmount = countCraftingAmount(copiedPromises);
        List<ExtraState> copiedExtras = copyOverflowExtraStatesFor(extraPromises, copiedCraftingAmount);
        List<ExtraState> copiedByproducts = copyByproductStatesFor(byproducts, copiedCraftingAmount);
        List<PatternCraftingBranch> copiedChildren = new ArrayList<>();
        List<BranchAllocation> allocations = allocateChildrenForCraftingAmount(copiedCraftingAmount);
        debugBranchEvent(
                "BRANCH",
                "branch copy slice resource=%s requested=%d copied=%d copiedCrafting=%d childAllocations=%d extras=%d byproducts=%d remaining=%d craftingRemaining=%d",
                requestType,
                amount,
                copiedAmount,
                copiedCraftingAmount,
                allocations.size(),
                copiedExtras.size(),
                copiedByproducts.size(),
                remainingAmount,
                remainingCraftingAmount);
        for (BranchAllocation allocation : allocations) {
            copiedChildren.add(allocation.branch.copyForAmount(allocation.amount));
        }
        PatternCraftingBranch copy = new PatternCraftingBranch(
                copiedRequest,
                info,
                copiedAmount,
                copiedAmount,
                copiedPromises,
                copiedExtras,
                copiedByproducts,
                copiedChildren);
        if (debugModule != null) {
            copy.attachDebugModule(debugModule);
        }
        return copy;
    }

    /**
     * Registers the extra promises and recipe byproducts that belong to the next consumed crafting slice of this
     * branch.
     */
    private void registerExtrasFor(int craftingAmount) {
        registerOverflowExtrasFor(extraPromises, craftingAmount);
        registerByproductsFor(byproducts, craftingAmount);
    }

    /**
     * Registers overproduction extras only once the requested outputs of this branch have all been assigned.
     * <p>
     * These extras come from request-tree promise splits, such as a recipe producing four sticks when only one more
     * stick is needed. Earlier slices of the same staged branch may still consume that surplus through their own output
     * orders, so routing it to storage before the final slice can starve recursive same-pipe crafts.
     */
    private void registerOverflowExtrasFor(List<ExtraState> states, int craftingAmount) {
        int consumedBefore = originalCraftingAmount - remainingCraftingAmount;
        int consumedAfter = Math.min(originalCraftingAmount, consumedBefore + craftingAmount);
        if (consumedAfter < originalCraftingAmount) {
            if (!states.isEmpty()) {
                debugBranchEvent(
                        "EXTRA",
                        "branch overflow extras delayed resource=%s craftingAmount=%d consumed=%d/%d",
                        requestType,
                        craftingAmount,
                        consumedAfter,
                        originalCraftingAmount);
            }
            return;
        }
        for (ExtraState state : states) {
            if (state.originalAmount <= 0) {
                continue;
            }
            IExtraPromise promise = state.promise.copy();
            promise.setAmount(state.originalAmount);
            registerExtra(promise, craftingAmount);
            debugBranchEvent(
                    "EXTRA",
                    "branch registered overflow extra resource=%s extra=%s amount=%d craftingAmount=%d",
                    requestType,
                    promise.getItemType(),
                    state.originalAmount,
                    craftingAmount);
        }
    }

    /**
     * Registers recipe byproducts by craft-set range. Unlike overproduction extras, true byproducts are produced by
     * each craft set and can be extracted as soon as that set has been ordered.
     */
    private void registerByproductsFor(List<ExtraState> states, int craftingAmount) {
        int consumedSetsBefore = consumedCraftingSetsForNext(0);
        int consumedSetsAfter = consumedCraftingSetsForNext(craftingAmount);
        for (ExtraState state : states) {
            int extraAmount = state.amountForRange(consumedSetsBefore, consumedSetsAfter, originalCraftingSets);
            if (extraAmount <= 0) {
                continue;
            }
            IExtraPromise promise = state.promise.copy();
            promise.setAmount(extraAmount);
            registerExtra(promise, craftingAmount);
            debugBranchEvent(
                    "EXTRA",
                    "branch registered byproduct resource=%s byproduct=%s amount=%d sets=%d->%d/%d",
                    requestType,
                    promise.getItemType(),
                    extraAmount,
                    consumedSetsBefore,
                    consumedSetsAfter,
                    originalCraftingSets);
        }
    }

    private void registerExtra(IExtraPromise promise, int craftingAmount) {
        if (reference != null && promise.getProvider() instanceof ModulePatternCrafting patternModule) {
            patternModule.registerExtras(promise, reference);
            return;
        }
        promise.registerExtras(requestType.copyForDisplayWith(Math.max(1, craftingAmount)));
    }

    /**
     * Copies the next {@code amount} items and consumes that amount from this branch.
     */
    public PatternCraftingBranch copyAndReserve(int amount) {
        int copiedAmount = Math.min(amount, remainingAmount);
        PatternCraftingBranch copy = copyForAmount(copiedAmount);
        reserve(copiedAmount);
        debugBranchEvent(
                "BRANCH",
                "branch copy and reserve resource=%s requested=%d copied=%d remaining=%d craftingRemaining=%d",
                requestType,
                amount,
                copiedAmount,
                remainingAmount,
                remainingCraftingAmount);
        return copy;
    }

    /**
     * Reserves all provider promises in this branch so separate request trees cannot consume those provider items
     * before this staged craft asks for them.
     */
    public void reserveProviderPromises() {
        for (PromiseState promise : promises) {
            if (promise.providerReserved || promise.remainingAmount <= 0) {
                continue;
            }
            if (promise.promise.getType() == ResourceType.PROVIDER
                    && promise.promise.getProvider() instanceof IStagedProviderReservation) {
                ((IStagedProviderReservation) promise.promise.getProvider())
                        .reserveStagedCrafting(promise.promise.getItemType(), promise.remainingAmount);
                promise.providerReserved = true;
                debugBranchEvent(
                        "BRANCH",
                        "branch reserved provider resource=%s promise=%s amount=%d provider=%s",
                        requestType,
                        promise.promise.getItemType(),
                        promise.remainingAmount,
                        promise.promise.getProvider());
            }
        }
        for (PatternCraftingBranch child : subRequests) {
            child.reserveProviderPromises();
        }
    }

    /**
     * Releases provider reservations that are still owned by this branch.
     */
    public void releaseProviderPromises() {
        for (PromiseState promise : promises) {
            if (!promise.providerReserved || promise.remainingAmount <= 0) {
                continue;
            }
            if (promise.promise.getType() == ResourceType.PROVIDER
                    && promise.promise.getProvider() instanceof IStagedProviderReservation) {
                ((IStagedProviderReservation) promise.promise.getProvider())
                        .releaseStagedCrafting(promise.promise.getItemType(), promise.remainingAmount);
                promise.providerReserved = false;
                debugBranchEvent(
                        "BRANCH",
                        "branch released provider resource=%s promise=%s amount=%d provider=%s",
                        requestType,
                        promise.promise.getItemType(),
                        promise.remainingAmount,
                        promise.promise.getProvider());
            }
        }
        for (PatternCraftingBranch child : subRequests) {
            child.releaseProviderPromises();
        }
    }

    /**
     * Requests the child branches required for {@code amount} items of this branch.
     */
    private void requestSubRequestsFor(int amount) {
        List<BranchAllocation> allocations = allocateChildrenForCraftingAmount(amount);
        debugBranchEvent(
                "BRANCH",
                "branch request children resource=%s amount=%d allocations=%d",
                requestType,
                amount,
                allocations.size());
        for (BranchAllocation allocation : allocations) {
            debugBranchEvent(
                    "BRANCH",
                    "branch request child parent=%s child=%s amount=%d",
                    requestType,
                    allocation.branch.requestType,
                    allocation.amount);
            allocation.branch.request(allocation.amount);
        }
    }

    /**
     * Consumes child branch capacity that is being handed to another staged crafting pipe.
     */
    private void reserveSubRequestsFor(int amount) {
        List<BranchAllocation> allocations = allocateChildrenForCraftingAmount(amount);
        debugBranchEvent(
                "BRANCH",
                "branch reserve children resource=%s amount=%d allocations=%d",
                requestType,
                amount,
                allocations.size());
        for (BranchAllocation allocation : allocations) {
            debugBranchEvent(
                    "BRANCH",
                    "branch reserve child parent=%s child=%s amount=%d",
                    requestType,
                    allocation.branch.requestType,
                    allocation.amount);
            allocation.branch.reserve(allocation.amount);
        }
    }

    /**
     * Consumes {@code amount} items from this branch without placing orders.
     */
    public void reserve(int amount) {
        int reserved = Math.min(amount, remainingAmount);
        int reservedCraftingAmount = craftingAmountForNext(reserved);
        List<BranchAllocation> childAllocations = allocateChildrenForCraftingAmount(reservedCraftingAmount);
        debugBranchEvent(
                "BRANCH",
                "branch reserve start resource=%s amount=%d reserved=%d reservedCrafting=%d remaining=%d craftingRemaining=%d childAllocations=%d",
                requestType,
                amount,
                reserved,
                reservedCraftingAmount,
                remainingAmount,
                remainingCraftingAmount,
                childAllocations.size());
        consumePromises(reserved);
        remainingAmount -= reserved;
        for (BranchAllocation allocation : childAllocations) {
            allocation.branch.reserve(allocation.amount);
        }
        debugBranchEvent(
                "BRANCH",
                "branch reserve end resource=%s reserved=%d remaining=%d craftingRemaining=%d",
                requestType,
                reserved,
                remainingAmount,
                remainingCraftingAmount);
    }

    /**
     * Calculates how much can be fulfilled as one promise operation.
     * <p>
     * Adjacent compatible staged crafting promises are merged here so one parent ingredient request becomes one staged
     * child craft. The request tree may contain several smaller promises because it balanced work while building the
     * tree, but the pattern pipe can handle the combined batch as long as the promises target the same staged provider
     * and pattern.
     */
    private int requestAmountForPromiseBatch(int startIndex, int maxAmount) {
        PromiseState first = promises.get(startIndex);
        if (first.remainingAmount <= 0) {
            return 0;
        }
        int amount = Math.min(maxAmount, first.remainingAmount);
        if (!isMergeableStagedPromise(first.promise)) {
            return amount;
        }
        for (int i = startIndex + 1; i < promises.size() && amount < maxAmount; i++) {
            PromiseState candidate = promises.get(i);
            if (candidate.remainingAmount <= 0) {
                continue;
            }
            if (!canMergePromiseBatch(first.promise, candidate.promise)) {
                break;
            }
            amount += Math.min(maxAmount - amount, candidate.remainingAmount);
        }
        return amount;
    }

    /**
     * Consumes the promise states represented by a fulfilled batch.
     */
    private void consumePromiseBatch(int startIndex, IPromise firstPromise, int amount) {
        int left = amount;
        for (int i = startIndex; i < promises.size() && left > 0; i++) {
            PromiseState current = promises.get(i);
            if (current.remainingAmount <= 0) {
                continue;
            }
            if (i != startIndex && !canMergePromiseBatch(firstPromise, current.promise)) {
                break;
            }
            int moved = Math.min(left, current.remainingAmount);
            current.remainingAmount -= moved;
            left -= moved;
        }
    }

    private boolean isMergeableStagedPromise(IPromise promise) {
        return promise.getType() == ResourceType.CRAFTING && promise.getProvider() instanceof IStagedCraftingProvider;
    }

    private boolean canMergePromiseBatch(IPromise first, IPromise candidate) {
        if (!isMergeableStagedPromise(first) || !isMergeableStagedPromise(candidate)) {
            return false;
        }
        if (first.getProvider() != candidate.getProvider() || !first.getItemType().equals(candidate.getItemType())) {
            return false;
        }
        if (first instanceof PatternCraftingPromise || candidate instanceof PatternCraftingPromise) {
            if (!(first instanceof PatternCraftingPromise firstPattern)
                    || !(candidate instanceof PatternCraftingPromise candidatePattern)) {
                return false;
            }
            return firstPattern.getPatternSlot() == candidatePattern.getPatternSlot()
                    && firstPattern.getResultAmountPerSet() == candidatePattern.getResultAmountPerSet();
        }
        if (first instanceof PatternFluidCraftingPromise || candidate instanceof PatternFluidCraftingPromise) {
            if (!(first instanceof PatternFluidCraftingPromise firstPattern)
                    || !(candidate instanceof PatternFluidCraftingPromise candidatePattern)) {
                return false;
            }
            return firstPattern.getPatternSlot() == candidatePattern.getPatternSlot()
                    && firstPattern.getResultAmountPerSet() == candidatePattern.getResultAmountPerSet();
        }
        if (first instanceof FluidLogisticsPromise || candidate instanceof FluidLogisticsPromise) {
            return first instanceof FluidLogisticsPromise && candidate instanceof FluidLogisticsPromise
                    && ((FluidLogisticsPromise) first).getLiquid()
                            .equals(((FluidLogisticsPromise) candidate).getLiquid());
        }
        return true;
    }

    /**
     * Copies promise states in request order until {@code amount} items are represented.
     */
    private List<PromiseState> copyPromiseStatesFor(int amount) {
        List<PromiseState> copiedPromises = new ArrayList<>();
        int amountLeft = amount;
        for (PromiseState promise : promises) {
            if (amountLeft <= 0) {
                break;
            }
            int copied = Math.min(amountLeft, promise.remainingAmount);
            if (copied > 0) {
                copiedPromises.add(
                        new PromiseState(
                                copyPromiseForAmount(promise.promise, copied),
                                copied,
                                promise.providerReserved));
                amountLeft -= copied;
            }
        }
        return copiedPromises;
    }

    /**
     * Consumes promise capacity in the same order the request tree selected it.
     */
    private void consumePromises(int amount) {
        int amountLeft = amount;
        for (PromiseState promise : promises) {
            if (amountLeft <= 0) {
                break;
            }
            int moved = Math.min(amountLeft, promise.remainingAmount);
            promise.remainingAmount -= moved;
            if (promise.promise.getType() == ResourceType.CRAFTING) {
                remainingCraftingAmount -= moved;
            }
            amountLeft -= moved;
        }
    }

    /**
     * Calculates child branch deltas for the next {@code craftingAmount} crafted parent items.
     * <p>
     * Child requests are created per crafting set, not per visible output item. A recipe that produces four sticks from
     * one craft still needs the full plank input when only one of those sticks is requested in the current staged
     * slice. Counting consumed crafting sets keeps split staged orders from under-allocating their recursive
     * ingredients.
     */
    private List<BranchAllocation> allocateChildrenForCraftingAmount(int craftingAmount) {
        List<BranchAllocation> allocations = new ArrayList<>();
        int parentAmount = Math.min(craftingAmount, remainingCraftingAmount);
        if (parentAmount <= 0 || originalCraftingSets <= 0) {
            return allocations;
        }
        int parentConsumedBefore = consumedCraftingSetsForNext(0);
        int parentConsumedAfter = consumedCraftingSetsForNext(parentAmount);
        debugBranchEvent(
                "BRANCH",
                "branch allocate children resource=%s craftingAmount=%d parentAmount=%d sets=%d->%d/%d remainingCrafting=%d children=%d",
                requestType,
                craftingAmount,
                parentAmount,
                parentConsumedBefore,
                parentConsumedAfter,
                originalCraftingSets,
                remainingCraftingAmount,
                subRequests.size());
        for (PatternCraftingBranch child : subRequests) {
            int childConsumedBefore = child.originalAmount - child.remainingAmount;
            int childConsumedAfter = scaleAmount(child.originalAmount, parentConsumedAfter, originalCraftingSets);
            int childAmount = Math.min(child.remainingAmount, Math.max(0, childConsumedAfter - childConsumedBefore));
            debugBranchEvent(
                    "BRANCH",
                    "branch child allocation parent=%s child=%s childConsumed=%d->%d original=%d remaining=%d allocated=%d",
                    requestType,
                    child.requestType,
                    childConsumedBefore,
                    childConsumedAfter,
                    child.originalAmount,
                    child.remainingAmount,
                    childAmount);
            if (childAmount > 0) {
                allocations.add(new BranchAllocation(child, childAmount));
            }
        }
        return allocations;
    }

    /**
     * Returns how much of the next {@code amount} visible items is backed by crafting promises.
     */
    private int craftingAmountForNext(int amount) {
        int amountLeft = Math.min(amount, remainingAmount);
        int craftingAmount = 0;
        for (PromiseState promise : promises) {
            if (amountLeft <= 0) {
                break;
            }
            int moved = Math.min(amountLeft, promise.remainingAmount);
            if (promise.promise.getType() == ResourceType.CRAFTING) {
                craftingAmount += moved;
            }
            amountLeft -= moved;
        }
        return craftingAmount;
    }

    /**
     * Counts consumed crafting sets after hypothetically consuming {@code extraCraftingAmount} more crafted items.
     */
    private int consumedCraftingSetsForNext(int extraCraftingAmount) {
        int extraLeft = Math.min(extraCraftingAmount, remainingCraftingAmount);
        int sets = 0;
        for (PromiseState state : promises) {
            if (state.promise.getType() != ResourceType.CRAFTING) {
                continue;
            }
            int original = state.promise.getAmount();
            int consumedBefore = Math.max(0, original - state.remainingAmount);
            int moved = Math.min(extraLeft, state.remainingAmount);
            int consumedAfter = consumedBefore + moved;
            sets += craftingSetsForAmount(state.promise, consumedAfter);
            extraLeft -= moved;
        }
        return sets;
    }

    private boolean canMergeWith(PatternCraftingBranch other) {
        return other != null && Objects.equals(info, other.info)
                && requestType.getClass() == other.requestType.getClass()
                && requestType.matches(other.requestType.getAsItem(), IResource.MatchSettings.NORMAL)
                && other.requestType.matches(requestType.getAsItem(), IResource.MatchSettings.NORMAL);
    }

    private PatternCraftingBranch mergeWith(PatternCraftingBranch other) {
        List<PromiseState> mergedPromises = new ArrayList<>(promises);
        mergedPromises.addAll(other.promises);
        List<ExtraState> mergedExtras = new ArrayList<>(extraPromises);
        mergedExtras.addAll(other.extraPromises);
        List<ExtraState> mergedByproducts = new ArrayList<>(byproducts);
        mergedByproducts.addAll(other.byproducts);
        List<PatternCraftingBranch> mergedChildren = new ArrayList<>(subRequests);
        mergedChildren.addAll(other.subRequests);
        return new PatternCraftingBranch(
                requestType.copyForDisplayWith(originalAmount + other.originalAmount),
                info,
                originalAmount + other.originalAmount,
                remainingAmount + other.remainingAmount,
                mergedPromises,
                mergedExtras,
                mergedByproducts,
                mergedChildren);
    }

    private NBTTagList writePromiseStates() {
        NBTTagList list = new NBTTagList();
        for (PromiseState state : promises) {
            NBTTagCompound stateTag = new NBTTagCompound();
            NBTTagCompound promiseTag = new NBTTagCompound();
            if (!PatternCraftingPersistence.writePromise(promiseTag, state.promise)) {
                continue;
            }
            stateTag.setTag(PROMISE_TAG, promiseTag);
            stateTag.setInteger(REMAINING_AMOUNT_TAG, state.remainingAmount);
            stateTag.setBoolean(PROVIDER_RESERVED_TAG, state.providerReserved);
            list.appendTag(stateTag);
        }
        return list;
    }

    private NBTTagList writeExtraStates(List<ExtraState> states) {
        NBTTagList list = new NBTTagList();
        for (ExtraState state : states) {
            NBTTagCompound stateTag = new NBTTagCompound();
            NBTTagCompound promiseTag = new NBTTagCompound();
            if (!PatternCraftingPersistence.writePromise(promiseTag, state.promise)) {
                continue;
            }
            stateTag.setTag(PROMISE_TAG, promiseTag);
            stateTag.setInteger(ORIGINAL_EXTRA_AMOUNT_TAG, state.originalAmount);
            list.appendTag(stateTag);
        }
        return list;
    }

    private NBTTagList writeSubRequests() {
        NBTTagList list = new NBTTagList();
        for (PatternCraftingBranch branch : subRequests) {
            NBTTagCompound branchTag = new NBTTagCompound();
            branch.writeToNBT(branchTag);
            list.appendTag(branchTag);
        }
        return list;
    }

    /**
     * Copies overproduction extras only onto the final staged slice that consumes this branch's requested outputs.
     */
    private List<ExtraState> copyOverflowExtraStatesFor(List<ExtraState> states, int craftingAmount) {
        List<ExtraState> copied = new ArrayList<>();
        int consumedBefore = originalCraftingAmount - remainingCraftingAmount;
        int consumedAfter = Math.min(originalCraftingAmount, consumedBefore + craftingAmount);
        if (consumedAfter < originalCraftingAmount) {
            return copied;
        }
        for (ExtraState state : states) {
            if (state.originalAmount <= 0) {
                continue;
            }
            IExtraPromise promise = state.promise.copy();
            promise.setAmount(state.originalAmount);
            copied.add(new ExtraState(promise));
        }
        return copied;
    }

    /**
     * Copies true byproduct amounts for the craft sets represented by this staged slice.
     */
    private List<ExtraState> copyByproductStatesFor(List<ExtraState> states, int craftingAmount) {
        List<ExtraState> copied = new ArrayList<>();
        for (ExtraState state : states) {
            int extraAmount = byproductAmountForNext(state, craftingAmount);
            if (extraAmount <= 0) {
                continue;
            }
            IExtraPromise promise = state.promise.copy();
            promise.setAmount(extraAmount);
            copied.add(new ExtraState(promise));
        }
        return copied;
    }

    private void appendPromises(StringBuilder out, String prefix) {
        if (promises.isEmpty()) {
            out.append(prefix).append("promises: <none>\n");
            return;
        }
        out.append(prefix).append("promises:\n");
        for (PromiseState promise : promises) {
            out.append(prefix).append("  - ").append(promise.promise.getType()).append(" ")
                    .append(promise.remainingAmount).append("x ").append(promise.promise.getItemType()).append(" from ")
                    .append(promise.promise.getProvider()).append(promise.providerReserved ? " reserved" : "")
                    .append("\n");
        }
    }

    private void appendLiveOrders(StringBuilder out, String prefix) {
        if (liveOrders.isEmpty()) {
            return;
        }
        out.append(prefix).append("live orders:\n");
        for (IOrderInfoProvider order : liveOrders) {
            out.append(prefix).append("  - ").append(order.getType()).append(" ").append(order.getAsDisplayItem())
                    .append(" router=").append(order.getRouterId()).append(order.isInProgress() ? " in-progress" : "")
                    .append(order.isFinished() ? " finished" : "").append("\n");
        }
    }

    private int getLiveOrderAmount() {
        int amount = 0;
        for (IOrderInfoProvider order : liveOrders) {
            if (order.isFinished() || order.getAsDisplayItem() == null) {
                continue;
            }
            amount += Math.max(0, order.getAsDisplayItem().getStackSize());
        }
        return amount;
    }

    private boolean hasInProgressOrders() {
        for (IOrderInfoProvider order : liveOrders) {
            if (order.isInProgress() || !order.getProgresses().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void appendExtraStates(StringBuilder out, String prefix, String label, List<ExtraState> states) {
        if (states.isEmpty()) {
            return;
        }
        out.append(prefix).append(label).append(":\n");
        for (ExtraState state : states) {
            out.append(prefix).append("  - ").append(state.promise.getAmount()).append("x ")
                    .append(state.promise.getItemType()).append(" original=").append(state.originalAmount).append("\n");
        }
    }

    private static class BranchAllocation {

        private final PatternCraftingBranch branch;
        private final int amount;

        private BranchAllocation(PatternCraftingBranch branch, int amount) {
            this.branch = branch;
            this.amount = amount;
        }
    }

    private static class PromiseState {

        private final IPromise promise;
        private int remainingAmount;
        private boolean providerReserved;

        private PromiseState(IPromise promise, int remainingAmount, boolean providerReserved) {
            this.promise = promise;
            this.remainingAmount = remainingAmount;
            this.providerReserved = providerReserved;
        }
    }

    private static class ExtraState {

        private final IExtraPromise promise;
        private final int originalAmount;

        private ExtraState(IExtraPromise promise) {
            this.promise = promise;
            this.originalAmount = promise.getAmount();
        }

        private ExtraState(IExtraPromise promise, int originalAmount) {
            this.promise = promise;
            this.originalAmount = originalAmount;
        }

        private int amountForRange(int consumedBefore, int consumedAfter, int parentAmount) {
            return scaledAmount(consumedAfter, parentAmount) - scaledAmount(consumedBefore, parentAmount);
        }

        private int scaledAmount(int consumed, int parentAmount) {
            if (originalAmount <= 0 || consumed <= 0 || parentAmount <= 0) {
                return 0;
            }
            return (int) ((long) originalAmount * consumed / parentAmount);
        }
    }
}
