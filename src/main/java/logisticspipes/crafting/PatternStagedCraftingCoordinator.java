package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import logisticspipes.crafting.pattern.PatternHandler;
import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.crafting.patternStack.PatternStackHelper;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.request.IPromise;
import logisticspipes.request.resources.DictResource;
import logisticspipes.request.resources.FluidResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.request.resources.ItemResource;
import logisticspipes.routing.FluidLogisticsPromise;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.routing.order.LogisticsFluidOrder;
import logisticspipes.routing.order.LogisticsItemOrder;
import logisticspipes.routing.order.LogisticsOrder;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * Owns the lifecycle of staged pattern crafting output orders.
 * <p>
 * The coordinator is the first stop after the request tree decides that this pipe should craft something. It validates
 * that the request has a real destination, creates the live output order, records the branch state required to request
 * ingredients later, and asks the scheduler to request any ingredient sets that can fit immediately.
 */
class PatternStagedCraftingCoordinator {

    private static final String STAGED_ORDERS_TAG = "stagedOrders";
    private static final String STANDALONE_ITEM_ORDERS_TAG = "standaloneItemOrders";
    private static final String STANDALONE_FLUID_ORDERS_TAG = "standaloneFluidOrders";
    private static final String PATTERN_SLOT_TAG = "patternSlot";
    private static final String RESULT_AMOUNT_PER_SET_TAG = "resultAmountPerSet";
    private static final String REMAINING_SETS_TAG = "remainingSets";
    private static final String OUTPUT_ORDER_TAG = "outputOrder";
    private static final String INGREDIENT_BRANCHES_TAG = "ingredientBranches";
    private static final int TAG_COMPOUND = 10;

    private final ModulePatternCrafting module;
    private final PipeItemsPatternCraftingLogistics pipe;
    private final PatternHandler patternHandler;
    private final PatternStackRequestHandler requestedIngredient;
    private final List<PatternCraftingOrder> stagedCrafts = new ArrayList<>();
    private final List<PatternCraftingOrder> outputOrders = new ArrayList<>();
    private final PatternStagedCraftingScheduler scheduler;

    PatternStagedCraftingCoordinator(ModulePatternCrafting module, PipeItemsPatternCraftingLogistics pipe,
            PatternHandler patternHandler, PatternStackRequestHandler requestedIngredient) {
        this.module = module;
        this.pipe = pipe;
        this.patternHandler = patternHandler;
        this.requestedIngredient = requestedIngredient;
        this.scheduler = new PatternStagedCraftingScheduler(module, pipe, stagedCrafts);
    }

    static List<PatternCraftingMonitorEntry> pendingMonitorEntries(NBTTagCompound tag, int restoreAttempts,
            int maxRestoreAttempts) {
        Map<UUID, List<PatternCraftingMonitorNode>> rootsByInstance = new LinkedHashMap<>();
        appendPendingStagedOrders(tag.getTagList(STAGED_ORDERS_TAG, TAG_COMPOUND), rootsByInstance);
        appendPendingOrders(tag.getTagList(STANDALONE_ITEM_ORDERS_TAG, TAG_COMPOUND), rootsByInstance);
        appendPendingOrders(tag.getTagList(STANDALONE_FLUID_ORDERS_TAG, TAG_COMPOUND), rootsByInstance);

        List<PatternCraftingMonitorEntry> entries = new ArrayList<>();
        for (Map.Entry<UUID, List<PatternCraftingMonitorNode>> instance : rootsByInstance.entrySet()) {
            entries.add(
                    PatternCraftingMonitorEntry
                            .restoring(instance.getKey(), instance.getValue(), restoreAttempts, maxRestoreAttempts));
        }
        return entries;
    }

    /**
     * Gives legacy standalone orders an identity so they remain visible and cancellable while restoration is pending.
     */
    static int assignMissingStandaloneReferences(NBTTagCompound tag) {
        if (tag == null) {
            return 0;
        }
        int assigned = assignMissingStandaloneReferences(tag.getTagList(STANDALONE_ITEM_ORDERS_TAG, TAG_COMPOUND));
        assigned += assignMissingStandaloneReferences(tag.getTagList(STANDALONE_FLUID_ORDERS_TAG, TAG_COMPOUND));
        return assigned;
    }

    static Set<UUID> pendingInstanceIds(NBTTagCompound tag) {
        Set<UUID> result = new LinkedHashSet<>();
        appendPendingStagedInstanceIds(tag.getTagList(STAGED_ORDERS_TAG, TAG_COMPOUND), result);
        appendPendingInstanceIds(tag.getTagList(STANDALONE_ITEM_ORDERS_TAG, TAG_COMPOUND), result);
        appendPendingInstanceIds(tag.getTagList(STANDALONE_FLUID_ORDERS_TAG, TAG_COMPOUND), result);
        return result;
    }

    boolean hasPattern(int patternSlot) {
        for (PatternCraftingOrder order : outputOrders) {
            if (order.patternSlot == patternSlot && !order.outputOrder.isFinished()) {
                return true;
            }
        }
        return false;
    }

    void writeToNBT(NBTTagCompound tag) {
        Set<IOrderInfoProvider> savedOutputOrders = Collections.newSetFromMap(new IdentityHashMap<>());
        NBTTagList stagedOrders = writeStagedOrders(savedOutputOrders);
        NBTTagList standaloneItemOrders = writeStandaloneItemOrders(savedOutputOrders);
        NBTTagList standaloneFluidOrders = writeStandaloneFluidOrders(savedOutputOrders);
        tag.setTag(STAGED_ORDERS_TAG, stagedOrders);
        tag.setTag(STANDALONE_ITEM_ORDERS_TAG, standaloneItemOrders);
        tag.setTag(STANDALONE_FLUID_ORDERS_TAG, standaloneFluidOrders);
        module.debugEvent(
                "PERSIST",
                "saved staged crafting state stagedOrders=%d standaloneItems=%d standaloneFluids=%d trackedOutputOrders=%d",
                stagedOrders.tagCount(),
                standaloneItemOrders.tagCount(),
                standaloneFluidOrders.tagCount(),
                outputOrders.size());
    }

    static boolean removePendingInstance(NBTTagCompound tag, UUID instanceId) {
        if (tag == null || instanceId == null) {
            return false;
        }
        boolean changed = removePendingOrders(tag.getTagList(STAGED_ORDERS_TAG, TAG_COMPOUND), instanceId, true);
        changed |= removePendingOrders(tag.getTagList(STANDALONE_ITEM_ORDERS_TAG, TAG_COMPOUND), instanceId, false);
        changed |= removePendingOrders(tag.getTagList(STANDALONE_FLUID_ORDERS_TAG, TAG_COMPOUND), instanceId, false);
        return changed;
    }

    static boolean hasPendingOrders(NBTTagCompound tag) {
        return tag != null && (tag.getTagList(STAGED_ORDERS_TAG, TAG_COMPOUND).tagCount() > 0
                || tag.getTagList(STANDALONE_ITEM_ORDERS_TAG, TAG_COMPOUND).tagCount() > 0
                || tag.getTagList(STANDALONE_FLUID_ORDERS_TAG, TAG_COMPOUND).tagCount() > 0);
    }

    private static void appendPendingStagedOrders(NBTTagList list,
            Map<UUID, List<PatternCraftingMonitorNode>> rootsByInstance) {
        for (int i = 0; i < list.tagCount(); i++) {
            appendPendingOrder(list.getCompoundTagAt(i).getCompoundTag(OUTPUT_ORDER_TAG), rootsByInstance);
        }
    }

    private static int assignMissingStandaloneReferences(NBTTagList list) {
        int assigned = 0;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound orderTag = list.getCompoundTagAt(i);
            if (PatternCraftingPersistence.readOrderCraftingReference(orderTag) != null) {
                continue;
            }
            PatternCraftingPersistence.writeOrderCraftingReference(orderTag, PatternCraftingReference.createInstance());
            assigned++;
        }
        return assigned;
    }

    private static void appendPendingStagedInstanceIds(NBTTagList list, Set<UUID> result) {
        for (int i = 0; i < list.tagCount(); i++) {
            appendPendingInstanceId(list.getCompoundTagAt(i).getCompoundTag(OUTPUT_ORDER_TAG), result);
        }
    }

    private static void appendPendingInstanceIds(NBTTagList list, Set<UUID> result) {
        for (int i = 0; i < list.tagCount(); i++) {
            appendPendingInstanceId(list.getCompoundTagAt(i), result);
        }
    }

    private static void appendPendingInstanceId(NBTTagCompound orderTag, Set<UUID> result) {
        PatternCraftingReference reference = PatternCraftingPersistence.readOrderCraftingReference(orderTag);
        if (reference != null) {
            result.add(reference.instanceId());
        }
    }

    private static void appendPendingOrders(NBTTagList list,
            Map<UUID, List<PatternCraftingMonitorNode>> rootsByInstance) {
        for (int i = 0; i < list.tagCount(); i++) {
            appendPendingOrder(list.getCompoundTagAt(i), rootsByInstance);
        }
    }

    private static void appendPendingOrder(NBTTagCompound orderTag,
            Map<UUID, List<PatternCraftingMonitorNode>> rootsByInstance) {
        PatternCraftingReference reference = PatternCraftingPersistence.readOrderCraftingReference(orderTag);
        ItemIdentifierStack displayStack = PatternCraftingPersistence.readOrderDisplayStack(orderTag);
        if (reference == null || displayStack == null || displayStack.getStackSize() <= 0) {
            return;
        }
        rootsByInstance.computeIfAbsent(reference.instanceId(), ignored -> new ArrayList<>())
                .add(new PatternCraftingMonitorNode(displayStack, 0, displayStack.getStackSize(), false));
    }

    void appendDebugState(StringBuilder out, String prefix) {
        if (stagedCrafts.isEmpty() && outputOrders.isEmpty()) {
            out.append(prefix).append("<none>\n");
            return;
        }
        out.append(prefix).append("active staged orders:\n");
        if (stagedCrafts.isEmpty()) {
            out.append(prefix).append("  <none>\n");
        }
        for (PatternCraftingOrder order : stagedCrafts) {
            order.appendDebugState(out, prefix + "  ");
        }
        out.append(prefix).append("tracked output orders:\n");
        if (outputOrders.isEmpty()) {
            out.append(prefix).append("  <none>\n");
        }
        for (PatternCraftingOrder order : outputOrders) {
            out.append(prefix).append("  - slot=").append(order.patternSlot).append(" active=")
                    .append(stagedCrafts.contains(order)).append(" remainingSets=").append(order.remainingSets)
                    .append(" output=")
                    .append(order.outputOrder == null ? "<none>" : order.outputOrder.getAsDisplayItem())
                    .append(order.outputOrder != null && order.outputOrder.isFinished() ? " finished" : "")
                    .append("\n");
        }
    }

    private static boolean removePendingOrders(NBTTagList list, UUID instanceId, boolean staged) {
        boolean changed = false;
        for (int i = list.tagCount() - 1; i >= 0; i--) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            NBTTagCompound orderTag = staged ? entry.getCompoundTag(OUTPUT_ORDER_TAG) : entry;
            PatternCraftingReference reference = PatternCraftingPersistence.readOrderCraftingReference(orderTag);
            if (reference != null && instanceId.equals(reference.instanceId())) {
                list.removeTag(i);
                changed = true;
            }
        }
        return changed;
    }

    IOrderInfoProvider fulfill(IPromise promise, IResource requestType, IAdditionalTargetInformation info,
            PatternCraftingBranch branch) {
        if (!hasRequestTarget(promise, requestType)) {
            module.debugEvent(
                    "STAGED",
                    "staged craft rejected without target promise=%s request=%s info=%s",
                    promise,
                    requestType,
                    info);
            return null;
        }

        module.debugEvent(
                "STAGED",
                "staged craft start promise=%s amount=%d request=%s info=%s branch=%s",
                promise.getItemType(),
                promise.getAmount(),
                requestType,
                info,
                branch == null ? "<none>" : "available");

        IOrderInfoProvider order = promise.fullFill(requestType, info);
        int patternSlot = resolvePatternSlot(promise);
        int resultAmountPerSet = resolveResultAmountPerSet(promise, patternSlot);

        module.debugEvent(
                "STAGED",
                "staged craft output order=%s patternSlot=%d resultAmountPerSet=%d",
                order == null ? "<none>" : order.getAsDisplayItem(),
                patternSlot,
                resultAmountPerSet);

        if (patternSlot >= 0 && branch != null && order != null) {
            PatternCraftingReference parentReference = info instanceof PatternTargetInformation target
                    ? target.orderReference()
                    : null;
            PatternCraftingReference reference = parentReference == null ? PatternCraftingReference.createInstance()
                    : parentReference.createChild();
            registerOrder(reference, patternSlot, resultAmountPerSet, branch, order);
        }
        return order;
    }

    void requestIngredients() {
        scheduler.requestIngredients(false);
        cleanupCompletedOutputOrders();
    }

    void requestIngredientsAfterCapacityChange() {
        scheduler.requestIngredients(true);
        cleanupCompletedOutputOrders();
    }

    private void removeOutputOrder(IOrderInfoProvider order) {
        if (order instanceof LogisticsItemOrder) {
            pipe.getItemOrderManager().removeOrder((LogisticsItemOrder) order);
        } else if (order instanceof LogisticsFluidOrder) {
            pipe.getPatternFluidOrderManager().removeOrder((LogisticsFluidOrder) order);
        }
    }

    private boolean hasRequestTarget(IPromise promise, IResource requestType) {
        if (promise instanceof FluidLogisticsPromise) {
            return requestType instanceof FluidResource && ((FluidResource) requestType).getTarget() != null;
        }
        return getRequestTarget(requestType) != null;
    }

    private IRequestItems getRequestTarget(IResource requestType) {
        if (requestType instanceof ItemResource) {
            return ((ItemResource) requestType).getTarget();
        }
        if (requestType instanceof DictResource) {
            return ((DictResource) requestType).getTarget();
        }
        return null;
    }

    private int resolvePatternSlot(IPromise promise) {
        if (promise instanceof PatternCraftingPromise) {
            return ((PatternCraftingPromise) promise).getPatternSlot();
        }
        if (promise instanceof PatternFluidCraftingPromise) {
            return ((PatternFluidCraftingPromise) promise).getPatternSlot();
        }
        return patternHandler.findPatternSlotForResult(promise.getItemType());
    }

    private int resolveResultAmountPerSet(IPromise promise, int patternSlot) {
        if (promise instanceof PatternCraftingPromise) {
            return ((PatternCraftingPromise) promise).getResultAmountPerSet();
        }
        if (promise instanceof PatternFluidCraftingPromise) {
            return ((PatternFluidCraftingPromise) promise).getResultAmountPerSet();
        }
        return Math.max(1, patternHandler.resultAmount(patternSlot, promise.getItemType()));
    }

    private NBTTagList writeStagedOrders(Set<IOrderInfoProvider> savedOutputOrders) {
        NBTTagList list = new NBTTagList();
        for (PatternCraftingOrder order : outputOrders) {
            if (order.outputOrder == null || order.outputOrder.isFinished()) {
                continue;
            }
            NBTTagCompound orderTag = new NBTTagCompound();
            orderTag.setInteger(PATTERN_SLOT_TAG, order.patternSlot);
            orderTag.setInteger(RESULT_AMOUNT_PER_SET_TAG, order.resultAmountPerSet);
            orderTag.setInteger(REMAINING_SETS_TAG, Math.max(0, order.remainingSets));
            NBTTagCompound outputTag = new NBTTagCompound();
            if (!PatternCraftingPersistence.writeOrder(outputTag, order.outputOrder)) {
                continue;
            }
            orderTag.setTag(OUTPUT_ORDER_TAG, outputTag);
            NBTTagList branches = new NBTTagList();
            for (PatternCraftingBranch branch : order.ingredientBranches) {
                NBTTagCompound branchTag = new NBTTagCompound();
                branch.writeToNBT(branchTag);
                branches.appendTag(branchTag);
            }
            orderTag.setTag(INGREDIENT_BRANCHES_TAG, branches);
            order.writeRuntimeState(orderTag);
            list.appendTag(orderTag);
            savedOutputOrders.add(order.outputOrder);
        }
        return list;
    }

    boolean restoreFromNBT(NBTTagCompound tag) {
        try {
            List<RestoredStagedOrder> restoredStagedOrders = readStagedOrders(
                    tag.getTagList(STAGED_ORDERS_TAG, TAG_COMPOUND));
            List<PatternCraftingPersistence.RestoredOrder> standaloneItemOrders = readOrders(
                    tag.getTagList(STANDALONE_ITEM_ORDERS_TAG, TAG_COMPOUND));
            List<PatternCraftingPersistence.RestoredOrder> standaloneFluidOrders = readOrders(
                    tag.getTagList(STANDALONE_FLUID_ORDERS_TAG, TAG_COMPOUND));
            module.debugEvent(
                    "PERSIST",
                    "restoring staged crafting state stagedOrders=%d standaloneItems=%d standaloneFluids=%d",
                    restoredStagedOrders.size(),
                    standaloneItemOrders.size(),
                    standaloneFluidOrders.size());

            stagedCrafts.clear();
            outputOrders.clear();

            for (PatternCraftingPersistence.RestoredOrder order : standaloneItemOrders) {
                order.create(pipe, module);
            }
            for (PatternCraftingPersistence.RestoredOrder order : standaloneFluidOrders) {
                order.create(pipe, module);
            }
            for (RestoredStagedOrder restored : restoredStagedOrders) {
                IOrderInfoProvider outputOrder = restored.outputOrder.create(pipe, module);
                PatternCraftingReference reference = restored.outputOrder.craftingReference();
                if (reference == null) {
                    removeOutputOrder(outputOrder);
                    continue;
                }
                PatternCraftingOrder order = new PatternCraftingOrder(
                        reference,
                        restored.patternSlot,
                        restored.resultAmountPerSet,
                        restored.remainingSets,
                        restored.ingredientBranches,
                        outputOrder,
                        module,
                        requestedIngredient);
                if (restored.runtimeState != null) {
                    order.readRuntimeState(restored.runtimeState);
                }
                outputOrders.add(order);
                if (!order.isFullyRequested() && !outputOrder.isFinished()) {
                    stagedCrafts.add(order);
                    for (PatternCraftingBranch branch : order.ingredientBranches) {
                        branch.reserveProviderPromises();
                    }
                }
                PatternCraftingInstanceRegistry.register(outputOrder, order);
                module.debugEvent(
                        "STAGED",
                        "restored staged craft slot=%d remainingSets=%d branches=%d output=%s",
                        order.patternSlot,
                        order.remainingSets,
                        order.ingredientBranches.size(),
                        outputOrder.getAsDisplayItem());
            }
            module.markHudStateDirty();
            return true;
        } catch (PatternCraftingPersistence.RestoreNotReadyException ignored) {
            module.debugEventThrottled("PERSIST", "restore staged crafting state postponed: routers not ready");
            return false;
        }
    }

    int remainingSets(int patternSlot) {
        int sets = 0;
        for (PatternCraftingOrder order : stagedCrafts) {
            if (order.patternSlot == patternSlot) {
                sets += Math.max(0, order.remainingSets);
            }
        }
        return sets;
    }

    int remainingOutputAmount(int patternSlot, IPatternStack output) {
        int amount = 0;
        for (PatternCraftingOrder order : outputOrders) {
            if (order.outputOrder.isFinished()) {
                continue;
            }
            if (order.patternSlot != patternSlot) {
                continue;
            }
            if (order.outputOrder.getAsDisplayItem() == null) {
                continue;
            }
            if (PatternStackHelper.matches(output, order.outputOrder.getAsDisplayItem().getItem())) {
                amount += Math.max(0, order.outputOrder.getAsDisplayItem().getStackSize());
            }
        }
        return amount;
    }

    private List<RestoredStagedOrder> readStagedOrders(NBTTagList list) {
        List<RestoredStagedOrder> result = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound orderTag = list.getCompoundTagAt(i);
            RestoredStagedOrder order = new RestoredStagedOrder();
            order.patternSlot = orderTag.getInteger(PATTERN_SLOT_TAG);
            order.resultAmountPerSet = orderTag.getInteger(RESULT_AMOUNT_PER_SET_TAG);
            order.remainingSets = orderTag.getInteger(REMAINING_SETS_TAG);
            order.outputOrder = PatternCraftingPersistence.readOrder(orderTag.getCompoundTag(OUTPUT_ORDER_TAG));
            order.runtimeState = orderTag;
            NBTTagList branches = orderTag.getTagList(INGREDIENT_BRANCHES_TAG, TAG_COMPOUND);
            for (int branch = 0; branch < branches.tagCount(); branch++) {
                order.ingredientBranches.add(PatternCraftingBranch.readFromNBT(branches.getCompoundTagAt(branch)));
            }
            result.add(order);
        }
        return result;
    }

    private List<PatternCraftingPersistence.RestoredOrder> readOrders(NBTTagList list) {
        List<PatternCraftingPersistence.RestoredOrder> result = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) {
            result.add(PatternCraftingPersistence.readOrder(list.getCompoundTagAt(i)));
        }
        return result;
    }

    /**
     * Drops completed tracking records only after the scheduler has decided that no staged ingredient work remains.
     * <p>
     * A same-pipe output can be satisfied by surplus already present in the shared crafting target. Such an order is
     * deliberately kept in {@code stagedCrafts} until its own ingredient branch has been requested, because that branch
     * produces the surplus needed by later sibling orders.
     */
    private void cleanupCompletedOutputOrders() {
        boolean changed = false;
        for (PatternCraftingOrder order : new ArrayList<>(outputOrders)) {
            if (!order.outputOrder.isFinished() || stagedCrafts.contains(order)) {
                continue;
            }
            outputOrders.remove(order);
            PatternCraftingInstanceRegistry.unregister(order);
            changed = true;
        }
        if (changed) {
            module.markHudStateDirty();
        }
    }

    void releaseAll() {
        for (PatternCraftingOrder order : new ArrayList<>(outputOrders)) {
            module.debugEvent(
                    "CANCEL",
                    "removal releases staged order slot=%d remainingSets=%d",
                    order.patternSlot,
                    order.remainingSets);
            order.releaseReservations();
            PatternCraftingInstanceRegistry.unregister(order);
        }
        stagedCrafts.clear();
        outputOrders.clear();
        module.markHudStateDirty();
    }

    Set<UUID> instancesForPattern(int patternSlot) {
        Set<UUID> instances = new java.util.HashSet<>();
        for (PatternCraftingOrder order : outputOrders) {
            if (order.patternSlot == patternSlot && order.outputOrder != null && !order.outputOrder.isFinished()) {
                instances.add(order.reference().instanceId());
            }
        }
        return instances;
    }

    boolean cancelTrackedOrder(PatternCraftingOrder order) {
        if (order == null || !outputOrders.contains(order)) {
            return false;
        }
        module.debugEvent(
                "CANCEL",
                "cancel staged order reference=%s slot=%d remainingSets=%d",
                order.reference(),
                order.patternSlot,
                order.remainingSets);
        order.releaseReservations();
        removeOutputOrder(order.outputOrder);
        PatternCraftingInstanceRegistry.unregister(order);
        stagedCrafts.remove(order);
        outputOrders.remove(order);
        module.markHudStateDirty();
        return true;
    }

    private void registerOrder(PatternCraftingReference reference, int patternSlot, int resultAmountPerSet,
            PatternCraftingBranch branch, IOrderInfoProvider order) {
        PatternCraftingOrder stagedOrder = new PatternCraftingOrder(
                reference,
                patternSlot,
                resultAmountPerSet,
                branch,
                order,
                module,
                requestedIngredient);
        stagedCrafts.add(stagedOrder);
        outputOrders.add(stagedOrder);
        PatternCraftingInstanceRegistry.register(order, stagedOrder);
        module.markHudStateDirty();
        module.debugEvent(
                "STAGED",
                "staged craft registered reference=%s slot=%d remainingSets=%d ingredientBranches=%d output=%s branch=%s branchRemaining=%d",
                reference,
                patternSlot,
                stagedOrder.remainingSets,
                stagedOrder.ingredientBranches.size(),
                order.getAsDisplayItem(),
                branch.getRequestType(),
                branch.getRemainingAmount());
        scheduler.requestIngredients(patternSlot);
    }

    private NBTTagList writeStandaloneItemOrders(Set<IOrderInfoProvider> savedOutputOrders) {
        NBTTagList list = new NBTTagList();
        for (LogisticsItemOrder order : pipe.getItemOrderManager()) {
            if (order.isFinished() || savedOutputOrders.contains(order)) {
                continue;
            }
            ensureStandaloneReference(order);
            NBTTagCompound orderTag = new NBTTagCompound();
            if (PatternCraftingPersistence.writeOrder(orderTag, order)) {
                list.appendTag(orderTag);
            }
        }
        return list;
    }

    private NBTTagList writeStandaloneFluidOrders(Set<IOrderInfoProvider> savedOutputOrders) {
        NBTTagList list = new NBTTagList();
        for (LogisticsFluidOrder order : pipe.getPatternFluidOrderManager()) {
            if (order.isFinished() || savedOutputOrders.contains(order)) {
                continue;
            }
            ensureStandaloneReference(order);
            NBTTagCompound orderTag = new NBTTagCompound();
            if (PatternCraftingPersistence.writeOrder(orderTag, order)) {
                list.appendTag(orderTag);
            }
        }
        return list;
    }

    private void ensureStandaloneReference(LogisticsOrder order) {
        if (order.getCraftingReference() != null) {
            return;
        }
        order.setCraftingReference(PatternCraftingReference.createInstance());
        module.debugEvent(
                "PERSIST",
                "assigned standalone order reference=%s output=%s",
                order.getCraftingReference(),
                order.getAsDisplayItem());
    }

    private static class RestoredStagedOrder {

        private int patternSlot;
        private int resultAmountPerSet;
        private int remainingSets;
        private PatternCraftingPersistence.RestoredOrder outputOrder;
        private NBTTagCompound runtimeState;
        private final List<PatternCraftingBranch> ingredientBranches = new ArrayList<>();
    }
}
