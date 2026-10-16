package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import logisticspipes.crafting.pattern.AbstractPattern;
import logisticspipes.crafting.pattern.ItemPattern;
import logisticspipes.crafting.pattern.PatternHandler;
import logisticspipes.crafting.pattern.PatternRecipeSnapshot;
import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.crafting.patternStack.PatternStackHelper;
import logisticspipes.interfaces.ISlotUpgradeManager;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.ICraftFluids;
import logisticspipes.interfaces.routing.ICraftItems;
import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.interfaces.routing.IItemSpaceControl;
import logisticspipes.interfaces.routing.IRequest;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.interfaces.routing.IRequireReliableTransport;
import logisticspipes.logistics.LogisticsManager;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipefxhandlers.Particles;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.request.ICraftingTemplate;
import logisticspipes.request.IExtraPromise;
import logisticspipes.request.IPromise;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTreeNode;
import logisticspipes.request.debug.CraftingRequestDebugManager;
import logisticspipes.request.resources.DictResource;
import logisticspipes.request.resources.FluidResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.routing.FluidLogisticsPromise;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.LogisticsDictPromise;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.routing.order.LogisticsFluidOrder;
import logisticspipes.routing.order.LogisticsItemOrder;
import logisticspipes.routing.order.LogisticsOrder;
import logisticspipes.utils.AdjacentTile;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.SinkReply.BufferMode;
import logisticspipes.utils.SinkReply.FixedPriority;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.item.SimpleStackInventory;

public class ModulePatternCrafting extends LogisticsModule
        implements ICraftItems, ICraftFluids, IRequestFluid, IRequireReliableTransport, IStagedCraftingProvider {

    private static final String STAGED_CRAFTING_TAG = "patternStagedCrafting";
    private static final String STAGED_CRAFTING_RESTORE_ATTEMPTS_TAG = "patternStagedCraftingRestoreAttempts";
    private static final int RESTORED_REQUESTED_RETRY_DELAY = 8000;
    private static final int RESTORE_DEBUG_INTERVAL = 40;
    // One retry runs per server tick, so this allows roughly one minute for the routing graph to become available.
    private static final int MAX_STAGED_CRAFTING_RESTORE_ATTEMPTS = 1200;
    private static final int DEFAULT_THROTTLE_TICKS = 40;

    private final PipeItemsPatternCraftingLogistics pipe;
    private final SimpleStackInventory patternInventory = new SimpleStackInventory(9, "Patterns", 1);
    private final Map<Integer, List<IPatternStack>> requestedIngredients = new HashMap<>();
    private final Map<String, ThrottledDebugEvent> throttledDebugEvents = new HashMap<>();
    private final PatternHandler patternHandler;
    private final AdjacentInventoryHandler adjacentInventory;
    private final PatternStackBufferHandler ingredientBuffer;
    private final PatternStackRequestHandler requestedIngredient;
    private final PatternCraftingIngredientPlanner ingredientPlanner;
    private final PatternCraftingUpgradeCache upgradeCache;
    private final PatternCraftingCapacity capacity;
    private final PatternLostIngredientHandler lostIngredientHandler;
    private final PatternCraftingCancelHandler cancelHandler;
    private final PatternCraftingArrivalHandler arrivalHandler;
    private final PatternStagedCraftingCoordinator stagedCrafting;
    private final PatternCraftingBlockingHandler blockingHandler;
    private final PatternSatelliteDispatchHandler satelliteDispatchHandler;
    private final PatternCraftingBufferDispatcher bufferDispatcher;
    private final PatternCraftingHudHandler hudHandler;
    private final PatternCraftingTemplateBuilder templateBuilder;
    private final PatternCraftingResultExtractor resultExtractor;
    private SinkReply sinkReply;
    private PipeItemsPatternCraftingLogistics.BlockingMode blockingMode = PipeItemsPatternCraftingLogistics.BlockingMode.OFF;
    private boolean checkingBufferedOrders = false;
    private NBTTagCompound pendingStagedCrafting;
    private boolean pendingRequestedIngredientRestoreRetries;
    private int stagedCraftingRestoreAttempts;
    private boolean fluidPatternValidationRequired = true;
    private boolean fluidSupportInitialized;
    private boolean lastFluidCraftingSupport;

    public ModulePatternCrafting(PipeItemsPatternCraftingLogistics pipe) {
        this.pipe = pipe;
        patternHandler = new PatternHandler(patternInventory);
        adjacentInventory = new AdjacentInventoryHandler(this, pipe);
        ingredientBuffer = new PatternStackBufferHandler(this::markHudStateDirty);
        requestedIngredient = new PatternStackRequestHandler(requestedIngredients, this::markHudStateDirty);
        upgradeCache = new PatternCraftingUpgradeCache(this);
        ingredientPlanner = new PatternCraftingIngredientPlanner(
                this,
                pipe,
                patternHandler,
                adjacentInventory,
                ingredientBuffer,
                requestedIngredient);
        lostIngredientHandler = new PatternLostIngredientHandler(this, pipe, requestedIngredient);
        blockingHandler = new PatternCraftingBlockingHandler(this);
        capacity = new PatternCraftingCapacity(
                this,
                pipe,
                patternHandler,
                adjacentInventory,
                ingredientBuffer,
                requestedIngredient,
                ingredientPlanner);
        satelliteDispatchHandler = new PatternSatelliteDispatchHandler(this, pipe, adjacentInventory);
        bufferDispatcher = new PatternCraftingBufferDispatcher(
                this,
                ingredientBuffer,
                adjacentInventory,
                blockingHandler,
                satelliteDispatchHandler,
                ingredientPlanner);
        stagedCrafting = new PatternStagedCraftingCoordinator(this, pipe, patternHandler, requestedIngredient);
        cancelHandler = new PatternCraftingCancelHandler(
                this,
                pipe,
                patternHandler,
                ingredientBuffer,
                requestedIngredients,
                requestedIngredient,
                stagedCrafting,
                blockingHandler,
                lostIngredientHandler);
        arrivalHandler = new PatternCraftingArrivalHandler(
                this,
                pipe,
                patternHandler,
                ingredientBuffer,
                requestedIngredient,
                ingredientPlanner,
                cancelHandler);
        hudHandler = new PatternCraftingHudHandler(
                this,
                patternHandler,
                adjacentInventory,
                ingredientBuffer,
                requestedIngredients,
                stagedCrafting,
                blockingHandler,
                satelliteDispatchHandler);
        templateBuilder = new PatternCraftingTemplateBuilder(this, patternHandler);
        resultExtractor = new PatternCraftingResultExtractor(this, pipe, adjacentInventory);
        patternInventory.addListener(inventory -> {
            patternHandler.invalidate();
            ingredientPlanner.invalidate();
            adjacentInventory.invalidate();
            fluidPatternValidationRequired = true;
            if (pipe.container != null) {
                pipe.container.markDirty();
            }
            markHudStateDirty();
            pipe.listenedChanged();
        });
        _service = pipe;
        _world = pipe;
        registerPosition(ModulePositionType.IN_PIPE, 0);
    }

    public IInventory getPatternInventory() {
        return patternInventory;
    }

    public ItemStack getPatternStack(int slot) {
        return patternHandler.getConfiguredPatternStack(slot);
    }

    PatternRecipeSnapshot getPatternRecipe(ItemStack pattern) {
        return patternHandler.getRecipe(pattern);
    }

    public ItemStack getPatternItemStack(int slot) {
        if (slot < 0 || slot >= patternInventory.getSizeInventory()) {
            return null;
        }
        return patternInventory.getStackInSlot(slot);
    }

    public void markPatternInventoryDirty() {
        patternInventory.markDirty();
        markHudStateDirty();
    }

    public void onCraftingTargetChanged() {
        adjacentInventory.invalidate();
        ingredientPlanner.invalidate();
        markHudStateDirty();
    }

    public int assignSatelliteToAllPatternIngredients(int satelliteId, String satelliteUuid) {
        if (!hasAdvancedSatelliteUpgrade()) {
            return 0;
        }
        int changed = 0;
        for (int patternSlot = 0; patternSlot < patternInventory.getSizeInventory(); patternSlot++) {
            ItemStack patternStack = patternInventory.getStackInSlot(patternSlot);
            if (patternStack == null) {
                continue;
            }
            AbstractPattern pattern = ItemPattern.fromStack(patternStack);
            for (int inputSlot = 0; inputSlot < pattern.getIngredientSlotCount(); inputSlot++) {
                IPatternStack ingredient = pattern.getPatternStackInSlot(inputSlot);
                if (!PatternStackHelper.isSolid(ingredient)) {
                    continue;
                }
                pattern.setSatelliteTargetForInputSlot(inputSlot, satelliteId, satelliteUuid);
                changed++;
            }
        }
        if (changed > 0) {
            markPatternInventoryDirty();
        }
        return changed;
    }

    public PipeItemsPatternCraftingLogistics.BlockingMode getBlockingMode() {
        return getEffectiveBlockingMode();
    }

    public void setBlockingMode(PipeItemsPatternCraftingLogistics.BlockingMode blockingMode) {
        PipeItemsPatternCraftingLogistics.BlockingMode requestedMode = blockingMode == null
                ? PipeItemsPatternCraftingLogistics.BlockingMode.OFF
                : blockingMode;
        PipeItemsPatternCraftingLogistics.BlockingMode nextMode = adjacentInventory.isConnectedToPatternCraftingTable()
                ? PipeItemsPatternCraftingLogistics.BlockingMode.SMART
                : requestedMode;
        if (this.blockingMode != nextMode) {
            this.blockingMode = nextMode;
            if (nextMode == PipeItemsPatternCraftingLogistics.BlockingMode.OFF) {
                blockingHandler.releaseAllSatelliteBatches();
                blockingHandler.restoreRunningCraft(-1, null, false);
            }
            markHudStateDirty();
        }
    }

    public boolean isBlockingModeFixed() {
        return adjacentInventory.isConnectedToPatternCraftingTable();
    }

    PipeItemsPatternCraftingLogistics.BlockingMode getEffectiveBlockingMode() {
        if (adjacentInventory.isConnectedToPatternCraftingTable()) {
            return PipeItemsPatternCraftingLogistics.BlockingMode.SMART;
        }
        return blockingMode;
    }

    /**
     * Reports how many ingredients this module can currently receive for its configured patterns.
     * <p>
     * The result includes reserved space for ingredients this module has already requested through staged crafting, so
     * subcraft results from this same pipe can be routed back into the module buffer instead of being rejected while
     * the connected inventory is busy.
     */
    @Override
    public SinkReply sinksItem(ItemIdentifier item, int bestPriority, int bestCustomPriority, boolean allowDefault,
            boolean includeInTransit) {
        if (bestPriority > sinkReply.fixedPriority.ordinal() || (bestPriority == sinkReply.fixedPriority.ordinal()
                && bestCustomPriority >= sinkReply.customPriority)) {
            return null;
        }
        FluidIdentifier fluid = item != null && item.isFluidContainer() ? FluidIdentifier.get(item) : null;
        if (fluid != null) {
            if (!supportsFluidCrafting()) {
                debug("sink rejected fluid ingredient %s: fluid crafting upgrade missing", fluid);
                return null;
            }
            int room = capacity.spaceForFluid(fluid, includeInTransit);
            if (room <= 0) {
                debug("sink rejected fluid ingredient %s includeInTransit=%s", fluid, includeInTransit);
                return null;
            }
            debug("sink accepts fluid ingredient %s room=%d includeInTransit=%s", fluid, room, includeInTransit);
            return new SinkReply(
                    sinkReply,
                    room,
                    areAllOrdersBuffered() ? BufferMode.DESTINATION_BUFFERED : BufferMode.NONE);
        }
        int room = capacity.spaceForItem(item, includeInTransit);
        if (room <= 0) {
            debug("sink rejected item ingredient %s includeInTransit=%s", item, includeInTransit);
            return null;
        }
        debug("sink accepts item ingredient %s room=%d includeInTransit=%s", item, room, includeInTransit);
        return new SinkReply(
                sinkReply,
                room,
                areAllOrdersBuffered() ? BufferMode.DESTINATION_BUFFERED : BufferMode.NONE);
    }

    /**
     * Reports how much of a routed fluid container can currently be accepted as a pattern ingredient.
     * <p>
     * The pattern pipe advertises itself as a fluid sink so storage routing can find it, but the accepted fluid is
     * still delivered as a LogisticsFluidContainer item and buffered by this module.
     */
    public int sinkAmount(FluidStack stack) {
        if (stack == null || stack.amount <= 0) {
            return 0;
        }
        if (!supportsFluidCrafting()) {
            debug("fluid sink amount rejected %s: fluid crafting upgrade missing", FluidIdentifier.get(stack));
            return 0;
        }
        int room = capacity.spaceForFluid(FluidIdentifier.get(stack), true);
        debug("fluid sink amount check %s amount=%d room=%d", FluidIdentifier.get(stack), stack.amount, room);
        return room >= stack.amount ? stack.amount : 0;
    }

    @Override
    public void tick() {
        restoreStagedCraftingIfNeeded();
        cancelUnsupportedFluidPatternCrafts();
        scheduleRequestedIngredientRestoreRetriesIfReady();
        lostIngredientHandler.retryLostItems();
        pushBufferedIngredients();
        stagedCrafting.requestIngredients();
        clearRunningCraftIfFinished();
        resultExtractor.tick();
    }

    @Override
    public boolean hasGenericInterests() {
        return false;
    }

    /**
     * Router interests are the output items, matching {@code PipeItemsPatternCraftingLogistics#getSpecificInterests}.
     */
    @Override
    public Collection<ItemIdentifier> getSpecificInterests() {
        return getOutputItems();
    }

    /**
     * Returns every item identity this module can craft, including fluid outputs represented by their display item.
     */
    public Set<ItemIdentifier> getCraftedItems() {
        return patternHandler.getCraftedItems(supportsFluidCrafting());
    }

    public Set<ItemIdentifier> getOutputItems() {
        return patternHandler.getOutputItems(supportsFluidCrafting());
    }

    // ------- DEFAULT OVERRIDES ------ //
    // region DEFAULT OVERRIDES

    @Override
    public boolean interestedInAttachedInventory() {
        return false;
    }

    @Override
    public boolean interestedInUndamagedID() {
        return false;
    }

    @Override
    public boolean recievePassive() {
        return false;
    }

    @Override
    public void getAllItems(Map<ItemIdentifier, Integer> list, List<IFilter> filter) {}

    @Override
    public LogisticsModule getSubModule(int slot) {
        return null;
    }

    @Override
    public int getX() {
        return pipe.getX();
    }

    @Override
    public int getY() {
        return pipe.getY();
    }

    @Override
    public int getZ() {
        return pipe.getZ();
    }

    @Override
    public Map<FluidIdentifier, Integer> getAvailableFluids() {
        return Collections.emptyMap();
    }

    @Override
    public net.minecraft.util.IIcon getIconTexture(IIconRegister register) {
        return register.registerIcon("logisticspipes:itemModule/ModuleCrafter");
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        patternInventory.readFromNBT(tag, "PatternCrafting");
        patternHandler.invalidate();
        ingredientPlanner.invalidate();
        adjacentInventory.invalidate();
        fluidPatternValidationRequired = true;
        blockingMode = PipeItemsPatternCraftingLogistics.BlockingMode.values()[Math.max(
                0,
                Math.min(
                        PipeItemsPatternCraftingLogistics.BlockingMode.values().length - 1,
                        tag.getInteger("patternBlockingMode")))];
        int restoredRunningCraft = tag.getInteger("runningCraft");
        PatternCraftingReference restoredRunningReference = PatternCraftingReference.readFromNBT(tag, "runningCraft");
        boolean restoredRunningCraftInAdjacent = tag.getBoolean("runningCraftInAdjacent");
        blockingHandler.restoreRunningCraft(
                restoredRunningReference == null ? -1 : restoredRunningCraft,
                restoredRunningReference,
                restoredRunningCraftInAdjacent);
        ingredientBuffer.readFromNBT(tag);
        requestedIngredient.readFromNBT(tag);
        lostIngredientHandler.readFromNBT(tag);
        NBTTagCompound restoredStagedCrafting = tag.hasKey(STAGED_CRAFTING_TAG)
                ? (NBTTagCompound) tag.getCompoundTag(STAGED_CRAFTING_TAG).copy()
                : null;
        int assignedStandaloneReferences = PatternStagedCraftingCoordinator
                .assignMissingStandaloneReferences(restoredStagedCrafting);
        pendingStagedCrafting = PatternStagedCraftingCoordinator.hasPendingOrders(restoredStagedCrafting)
                ? restoredStagedCrafting
                : null;
        pendingRequestedIngredientRestoreRetries = !requestedIngredients.isEmpty();
        stagedCraftingRestoreAttempts = pendingStagedCrafting == null ? 0
                : Math.max(0, tag.getInteger(STAGED_CRAFTING_RESTORE_ATTEMPTS_TAG));
        markHudStateDirty();
        debugEvent(
                "PERSIST",
                "loaded module nbt bufferedSlots=%d requestedSlots=%d lostQueued=%d pendingStaged=%s assignedStandaloneReferences=%d runningCraft=%d adjacentBatch=%s",
                ingredientBuffer.size(),
                requestedIngredients.size(),
                lostIngredientHandler.size(),
                pendingStagedCrafting != null,
                assignedStandaloneReferences,
                blockingHandler.runningCraft(),
                blockingHandler.runningCraftInAdjacent());
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        patternInventory.writeToNBT(tag, "PatternCrafting");
        tag.setInteger("patternBlockingMode", blockingMode.ordinal());
        tag.setInteger("runningCraft", blockingHandler.runningCraft());
        tag.setBoolean("runningCraftInAdjacent", blockingHandler.runningCraftInAdjacent());
        if (blockingHandler.runningCraftReference() != null) {
            blockingHandler.runningCraftReference().writeToNBT(tag, "runningCraft");
        }
        ingredientBuffer.writeToNBT(tag);
        requestedIngredient.writeToNBT(tag);
        lostIngredientHandler.writeToNBT(tag);
        if (pendingStagedCrafting != null) {
            tag.setTag(STAGED_CRAFTING_TAG, pendingStagedCrafting.copy());
            tag.setInteger(STAGED_CRAFTING_RESTORE_ATTEMPTS_TAG, stagedCraftingRestoreAttempts);
        } else {
            NBTTagCompound stagedTag = new NBTTagCompound();
            stagedCrafting.writeToNBT(stagedTag);
            tag.setTag(STAGED_CRAFTING_TAG, stagedTag);
            tag.removeTag(STAGED_CRAFTING_RESTORE_ATTEMPTS_TAG);
        }
        debugEvent(
                "PERSIST",
                "saved module nbt bufferedSlots=%d requestedSlots=%d lostQueued=%d pendingStaged=%s runningCraft=%d adjacentBatch=%s",
                ingredientBuffer.size(),
                requestedIngredients.size(),
                lostIngredientHandler.size(),
                pendingStagedCrafting != null,
                blockingHandler.runningCraft(),
                blockingHandler.runningCraftInAdjacent());
    }
    // endregion

    // ------- DEBUG ------ //
    // region DEBUG

    @Override
    public void registerPosition(ModulePositionType slot, int positionInt) {
        super.registerPosition(slot, positionInt);
        sinkReply = new SinkReply(FixedPriority.ItemSink, 0, true, false, 1, 0, null);
    }

    void debug(String message, Object... args) {
        if (_service != null) {
            _service.getDebug().log("PatternCrafting: " + message, args);
        }
    }

    public void debugEvent(String category, String message, Object... args) {
        recordDebugEvent(category, formatDebugMessage(message, args));
    }

    void debugEventThrottled(String category, String message, Object... args) {
        debugEventThrottled(category, DEFAULT_THROTTLE_TICKS, message, args);
    }

    void debugEventThrottled(String category, int intervalTicks, String message, Object... args) {
        String formatted = formatDebugMessage(message, args);
        if (intervalTicks <= 0) {
            recordDebugEvent(category, formatted);
            return;
        }
        String key = category + "\n" + formatted;
        long tick = currentDebugTick();
        ThrottledDebugEvent state = throttledDebugEvents.get(key);
        if (state != null && tick - state.lastLoggedTick < intervalTicks) {
            state.suppressed++;
            return;
        }
        if (state == null) {
            state = new ThrottledDebugEvent();
            throttledDebugEvents.put(key, state);
        } else if (state.suppressed > 0) {
            formatted = formatted + " (suppressed repeats=" + state.suppressed + ")";
        }
        state.lastLoggedTick = tick;
        state.suppressed = 0;
        recordDebugEvent(category, formatted);
    }

    public void recordDebugEvent(String category, String message) {
        debug("%s", message);
        CraftingRequestDebugManager.recordPipeEvent(pipe, category, message);
    }

    private String formatDebugMessage(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message == null ? "" : message;
        }
        try {
            return String.format(message, args);
        } catch (RuntimeException ignored) {
            StringBuilder out = new StringBuilder(message == null ? "" : message);
            out.append(" args=");
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(args[i]);
            }
            return out.toString();
        }
    }

    private long currentDebugTick() {
        return currentWorldTick();
    }

    long currentWorldTick() {
        World world = pipe.getWorld();
        return world == null ? 0 : world.getTotalWorldTime();
    }
    // endregion

    private void restoreStagedCraftingIfNeeded() {
        if (pendingStagedCrafting == null) {
            return;
        }
        World world = pipe.getWorld();
        if (world != null && world.isRemote) {
            return;
        }
        if (stagedCrafting.restoreFromNBT(pendingStagedCrafting)) {
            debugEvent("STAGED", "restored staged crafting state after %d attempts", stagedCraftingRestoreAttempts + 1);
            pendingStagedCrafting = null;
            stagedCraftingRestoreAttempts = 0;
            markHudStateDirty();
            markPersistentStateDirty();
            return;
        }
        stagedCraftingRestoreAttempts++;
        if (stagedCraftingRestoreAttempts >= MAX_STAGED_CRAFTING_RESTORE_ATTEMPTS) {
            expirePendingStagedCrafting();
            return;
        }
        if (stagedCraftingRestoreAttempts % RESTORE_DEBUG_INTERVAL == 0) {
            debugEvent("STAGED", "waiting to restore staged crafting state attempts=%d", stagedCraftingRestoreAttempts);
            markPersistentStateDirty();
        }
    }

    private void expirePendingStagedCrafting() {
        Set<UUID> instances = PatternStagedCraftingCoordinator.pendingInstanceIds(pendingStagedCrafting);
        for (UUID instanceId : instances) {
            PatternCraftingInstanceRegistry.recordCancellation(instanceId);
            cancelHandler.cancelPendingInstance(instanceId);
        }
        debugEvent(
                "STAGED",
                "discarded staged crafting state after restore timeout attempts=%d instances=%s",
                stagedCraftingRestoreAttempts,
                instances);
        pendingStagedCrafting = null;
        pendingRequestedIngredientRestoreRetries = false;
        stagedCraftingRestoreAttempts = 0;
        markHudStateDirty();
        markPersistentStateDirty();
    }

    List<PatternCraftingMonitorEntry> getPendingRestoreEntries() {
        if (pendingStagedCrafting == null) {
            return Collections.emptyList();
        }
        return PatternStagedCraftingCoordinator.pendingMonitorEntries(
                pendingStagedCrafting,
                stagedCraftingRestoreAttempts,
                MAX_STAGED_CRAFTING_RESTORE_ATTEMPTS);
    }

    List<PatternCraftingMonitorEntry> getStandaloneOrderEntries() {
        Map<UUID, List<PatternCraftingMonitorNode>> rootsByInstance = new LinkedHashMap<>();
        boolean assignedReference = false;
        for (LogisticsItemOrder order : pipe.getItemOrderManager()) {
            assignedReference |= appendStandaloneOrder(order, rootsByInstance);
        }
        for (LogisticsFluidOrder order : pipe.getPatternFluidOrderManager()) {
            assignedReference |= appendStandaloneOrder(order, rootsByInstance);
        }
        if (assignedReference) {
            debugEvent("PERSIST", "assigned identities to legacy live standalone orders");
            markHudStateDirty();
            markPersistentStateDirty();
        }
        List<PatternCraftingMonitorEntry> entries = new ArrayList<>(rootsByInstance.size());
        for (Map.Entry<UUID, List<PatternCraftingMonitorNode>> entry : rootsByInstance.entrySet()) {
            entries.add(new PatternCraftingMonitorEntry(entry.getKey(), entry.getValue()));
        }
        return entries;
    }

    private boolean appendStandaloneOrder(LogisticsOrder order,
            Map<UUID, List<PatternCraftingMonitorNode>> rootsByInstance) {
        if (order == null || order.isFinished()
                || order.getAmount() <= 0
                || PatternCraftingInstanceRegistry.isTrackedOutputOrder(order)) {
            return false;
        }
        boolean assignedReference = false;
        if (order.getCraftingReference() == null) {
            order.setCraftingReference(PatternCraftingReference.createInstance());
            assignedReference = true;
        }
        ItemIdentifierStack display = order.getAsDisplayItem();
        if (display == null || display.getStackSize() <= 0) {
            return assignedReference;
        }
        ItemIdentifierStack snapshot = display.clone();
        rootsByInstance.computeIfAbsent(order.getCraftingReference().instanceId(), ignored -> new ArrayList<>()).add(
                new PatternCraftingMonitorNode(
                        snapshot,
                        0,
                        snapshot.getStackSize(),
                        order.isInProgress() || !order.getProgresses().isEmpty()));
        return assignedReference;
    }

    boolean hasStandaloneOrderInstance(UUID instanceId) {
        if (instanceId == null) {
            return false;
        }
        for (LogisticsItemOrder order : pipe.getItemOrderManager()) {
            if (isStandaloneOrderForInstance(order, instanceId)) {
                return true;
            }
        }
        for (LogisticsFluidOrder order : pipe.getPatternFluidOrderManager()) {
            if (isStandaloneOrderForInstance(order, instanceId)) {
                return true;
            }
        }
        return false;
    }

    boolean cancelStandaloneOrderInstance(UUID instanceId) {
        if (!hasStandaloneOrderInstance(instanceId)) {
            return false;
        }
        PatternCraftingInstanceRegistry.recordCancellation(instanceId);
        boolean changed = cancelHandler.cancelStandaloneInstance(instanceId);
        if (changed) {
            debugEvent("CANCEL", "cancelled standalone crafting orders instance=%s", instanceId);
            markHudStateDirty();
            markPersistentStateDirty();
        }
        return changed;
    }

    private boolean isStandaloneOrderForInstance(LogisticsOrder order, UUID instanceId) {
        return order != null && !order.isFinished()
                && order.getCraftingReference() != null
                && instanceId.equals(order.getCraftingReference().instanceId())
                && !PatternCraftingInstanceRegistry.isTrackedOutputOrder(order);
    }

    boolean hasPendingRestoreInstance(UUID instanceId) {
        return pendingStagedCrafting != null
                && PatternStagedCraftingCoordinator.pendingInstanceIds(pendingStagedCrafting).contains(instanceId);
    }

    boolean cancelPendingRestore(UUID instanceId) {
        if (!PatternStagedCraftingCoordinator.removePendingInstance(pendingStagedCrafting, instanceId)) {
            return false;
        }
        PatternCraftingInstanceRegistry.recordCancellation(instanceId);
        cancelHandler.cancelPendingInstance(instanceId);
        if (!PatternStagedCraftingCoordinator.hasPendingOrders(pendingStagedCrafting)) {
            pendingStagedCrafting = null;
            pendingRequestedIngredientRestoreRetries = false;
            stagedCraftingRestoreAttempts = 0;
        }
        debugEvent("CANCEL", "cancelled pending restored crafting instance=%s", instanceId);
        markHudStateDirty();
        markPersistentStateDirty();
        return true;
    }

    private void markPersistentStateDirty() {
        if (pipe.container != null) {
            pipe.container.markDirty();
        }
    }

    private void scheduleRequestedIngredientRestoreRetriesIfReady() {
        if (!pendingRequestedIngredientRestoreRetries || pendingStagedCrafting != null) {
            return;
        }
        pendingRequestedIngredientRestoreRetries = false;
        for (PatternStackRequestHandler.OwnedEntry entry : requestedIngredient.entries()) {
            PatternTargetInformation target = PatternTargetInformation
                    .delivery(entry.patternSlot, PatternTargetInformation.NO_INPUT_SLOT, entry.owner);
            lostIngredientHandler.queue(entry.stack, target, RESTORED_REQUESTED_RETRY_DELAY);
            debugEvent(
                    "REQUEST",
                    "restore queued requested ingredient retry reference=%s slot=%d ingredient=%s",
                    entry.owner,
                    entry.patternSlot,
                    entry.stack);
        }
    }

    boolean supportsFluidCrafting() {
        return upgradeCache.supportsFluidCrafting();
    }

    /** Returns whether configured input and output satellite assignments may be used. */
    boolean hasAdvancedSatelliteUpgrade() {
        return upgradeCache.hasAdvancedSatellite();
    }

    /** Returns whether item ingredients may bypass normal routing to pattern satellites. */
    boolean hasInstantSatelliteUpgrade() {
        return upgradeCache.hasInstantSatellite();
    }

    /**
     * Returns false for patterns with fluid entries while the pipe has no fluid crafting upgrade. Such patterns are not
     * offered to the network.
     */
    public boolean isPatternCraftingSupported(ItemStack pattern) {
        return !isFluidCraftingPattern(pattern) || supportsFluidCrafting();
    }

    private boolean isFluidCraftingPattern(ItemStack pattern) {
        PatternRecipeSnapshot recipe = patternHandler.getRecipe(pattern);
        return recipe != null && recipe.containsFluid();
    }

    private void cancelUnsupportedFluidPatternCrafts() {
        boolean supportsFluid = supportsFluidCrafting();
        if (!fluidSupportInitialized || supportsFluid != lastFluidCraftingSupport) {
            fluidSupportInitialized = true;
            lastFluidCraftingSupport = supportsFluid;
            fluidPatternValidationRequired = !supportsFluid;
            markHudStateDirty();
        }
        if (supportsFluid || !fluidPatternValidationRequired) {
            return;
        }
        fluidPatternValidationRequired = false;
        for (int slot = 0; slot < patternHandler.size(); slot++) {
            ItemStack pattern = patternHandler.getConfiguredPatternStack(slot);
            if (!isFluidCraftingPattern(pattern)) {
                continue;
            }
            if (stagedCrafting.hasPattern(slot) || requestedIngredients.containsKey(slot)
                    || ingredientBuffer.asMap().containsKey(slot)) {
                debugEventThrottled("STAGED", "cancel fluid pattern slot=%d: fluid crafting upgrade missing", slot);
                cancelPatternCraft(slot);
            }
        }
    }

    /**
     * Offers already-registered extra outputs to a request tree before new crafting work is considered.
     * <p>
     * Item extras and fluid extras are checked against their own order managers. If an extra is consumed, fulfilment
     * will remove or reduce the destinationless extra order so the byproduct is not extracted twice.
     */
    @Override
    public void canProvide(RequestTreeNode tree, RequestTree root, List<IFilter> filters) {
        IResource requested = tree.getRequestType();
        if (requested instanceof FluidResource fluidResource) {
            var manager = pipe.getPatternFluidOrderManager();
            if (!manager.hasExtras() || tree.hasBeenQueried(manager)) {
                return;
            }
            FluidIdentifier fluid = fluidResource.getFluid();
            Map<PatternByproductTarget, Integer> extras = new LinkedHashMap<>();
            for (LogisticsFluidOrder order : manager) {
                if (order.getType() == ResourceType.EXTRA && order.getFluid().equals(fluid)) {
                    extras.merge(order.getByproductTarget(), order.getAmount(), Integer::sum);
                }
            }
            for (Map.Entry<PatternByproductTarget, Integer> extra : extras.entrySet()) {
                int amount = Math.min(
                        extra.getValue()
                                - root.getPromisedByproductAmount(this, fluid.getItemIdentifier(), extra.getKey()),
                        tree.getMissingAmount());
                if (amount > 0) {
                    tree.addPromise(new PatternFluidByproductPromise(fluid, amount, this, true, extra.getKey()));
                }
            }
            tree.setQueried(manager);
            return;
        }
        var manager = pipe.getItemOrderManager();
        if (!manager.hasExtras() || tree.hasBeenQueried(manager)) {
            return;
        }
        Map<ItemIdentifier, Map<PatternByproductTarget, Integer>> extras = new LinkedHashMap<>();
        for (LogisticsItemOrder order : manager) {
            ItemIdentifier item = order.getResource().getItem();
            if (order.getType() == ResourceType.EXTRA && requested.matches(item, IResource.MatchSettings.NORMAL)) {
                extras.computeIfAbsent(item, ignored -> new LinkedHashMap<>())
                        .merge(order.getByproductTarget(), order.getAmount(), Integer::sum);
            }
        }
        for (Map.Entry<ItemIdentifier, Map<PatternByproductTarget, Integer>> item : extras.entrySet()) {
            for (Map.Entry<PatternByproductTarget, Integer> extra : item.getValue().entrySet()) {
                int amount = Math.min(
                        extra.getValue() - root.getPromisedByproductAmount(this, item.getKey(), extra.getKey()),
                        tree.getMissingAmount());
                if (amount > 0) {
                    tree.addPromise(new PatternItemByproductPromise(item.getKey(), amount, this, true, extra.getKey()));
                }
            }
        }
        tree.setQueried(manager);
    }

    /**
     * Creates an item output order for a pattern craft or for a previously registered item extra.
     * <p>
     * When an extra promise is used to satisfy a request, its destinationless extra order is removed first; the new
     * order targets the real requester and retains its byproduct origin for possible remote extraction.
     */
    @Override
    public LogisticsOrder fullFill(LogisticsPromise promise, IRequestItems destination,
            IAdditionalTargetInformation info) {
        boolean byproduct = promise instanceof IExtraPromise;
        if (byproduct) {
            DictResource extraResource;
            if (promise instanceof LogisticsDictPromise dictPromise) {
                extraResource = dictPromise.getResource().clone();
                extraResource.getItemStack().setStackSize(promise.numberOfItems);
            } else {
                extraResource = new DictResource(new ItemIdentifierStack(promise.item, promise.numberOfItems), null);
            }
            if (promise instanceof PatternByproductPromise) {
                pipe.getItemOrderManager().removeExtras(extraResource, byproductTarget(promise));
            } else {
                pipe.getItemOrderManager().removeExtras(extraResource);
            }
            markHudStateDirty();
        }
        pipe.spawnParticle(Particles.WhiteParticle, 2);
        debugEvent(
                "ORDER",
                "create item output order item=%s amount=%d destination=%s info=%s",
                promise.item,
                promise.numberOfItems,
                destination,
                info);
        LogisticsOrder order = pipe.getItemOrderManager().addOrder(
                new ItemIdentifierStack(promise.item, promise.numberOfItems),
                destination,
                ResourceType.CRAFTING,
                info);
        order.setByproduct(byproduct);
        order.setByproductTarget(byproductTarget(promise));
        return order;
    }

    /**
     * Starts a staged craft from a request-tree branch.
     * <p>
     * The output order stays in this pipe's order manager, while the branch is kept so this module can request only the
     * ingredient sets that currently fit in its buffer or connected inventory.
     */
    @Override
    public IOrderInfoProvider fullFillStagedCrafting(IPromise promise, IResource requestType,
            IAdditionalTargetInformation info, PatternCraftingBranch branch) {
        return stagedCrafting.fulfill(promise, requestType, info, branch);
    }

    /**
     * Creates a fluid output order for a pattern craft or for a previously registered fluid extra.
     * <p>
     * Consumed fluid extras are removed from the destinationless extra-order queue before the new requester-targeted
     * order is added with a persistent byproduct-origin marker.
     */
    @Override
    public IOrderInfoProvider fullFill(FluidLogisticsPromise promise, IRequestFluid destination, ResourceType type,
            IAdditionalTargetInformation info) {
        ResourceType orderType = type;
        boolean byproduct = promise instanceof IExtraPromise;
        if (byproduct) {
            if (promise instanceof PatternByproductPromise) {
                pipe.getPatternFluidOrderManager()
                        .removeExtras(promise.getLiquid(), promise.getAmount(), byproductTarget(promise));
            } else {
                pipe.getPatternFluidOrderManager().removeExtras(promise.getLiquid(), promise.getAmount());
            }
            orderType = ResourceType.CRAFTING;
            markHudStateDirty();
        }
        pipe.spawnParticle(Particles.WhiteParticle, 2);
        debugEvent(
                "ORDER",
                "create fluid output order fluid=%s amount=%d destination=%s info=%s",
                promise.getLiquid(),
                promise.getAmount(),
                destination,
                info);
        LogisticsOrder order = pipe.getPatternFluidOrderManager().addOrder(promise, destination, orderType, info);
        order.setByproduct(byproduct);
        order.setByproductTarget(byproductTarget(promise));
        return order;
    }

    @Override
    public void sendFailed(FluidIdentifier fluid, Integer amount) {
        lostIngredientHandler.fluidSendFailed(fluid, amount);
    }

    @Override
    public IRouter getRouter() {
        return pipe.getRouter();
    }

    @Override
    public void itemCouldNotBeSend(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        pipe.itemCouldNotBeSend(item, info);
    }

    @Override
    public int getID() {
        return pipe.getID();
    }

    @Override
    public int compareTo(IRequest request) {
        return getID() - request.getID();
    }

    /**
     * Registers item or fluid byproducts as destinationless extra orders.
     * <p>
     * Those orders force the extraction phase to remove extra products from the connected inventory or fluid handler,
     * then route them to storage or drop them if no storage can accept them.
     */
    @Override
    public void registerExtras(IPromise promise) {
        registerExtras(promise, null);
    }

    void registerExtras(IPromise promise, PatternCraftingReference owner) {
        LogisticsOrder order;
        if (promise instanceof FluidLogisticsPromise fluidPromise) {
            debugEvent(
                    "EXTRA",
                    "register fluid extra %s amount=%d owner=%s",
                    fluidPromise.getLiquid(),
                    fluidPromise.getAmount(),
                    owner);
            order = pipe.getPatternFluidOrderManager().addExtra(fluidPromise.getLiquid(), fluidPromise.getAmount());
        } else if (promise instanceof LogisticsDictPromise) {
            DictResource resource = ((LogisticsDictPromise) promise).getResource().clone();
            resource.getItemStack().setStackSize(promise.getAmount());
            debugEvent(
                    "EXTRA",
                    "register dict extra %s amount=%d owner=%s",
                    resource.getItem(),
                    promise.getAmount(),
                    owner);
            order = pipe.getItemOrderManager().addExtra(resource);
        } else {
            debugEvent(
                    "EXTRA",
                    "register extra %s amount=%d owner=%s",
                    promise.getItemType(),
                    promise.getAmount(),
                    owner);
            order = pipe.getItemOrderManager().addExtra(
                    new DictResource(new ItemIdentifierStack(promise.getItemType(), promise.getAmount()), null));
        }
        order.setByproduct(true);
        PatternByproductTarget target = byproductTarget(promise);
        order.setByproductTarget(target == null ? null : target.withSourceReference(owner));
        if (owner != null) {
            order.setCraftingReference(owner.createChild());
        }
        markHudStateDirty();
    }

    private PatternByproductTarget byproductTarget(IPromise promise) {
        return promise instanceof PatternByproductPromise byproductPromise ? byproductPromise.getByproductTarget()
                : null;
    }

    /**
     * Builds a crafting template for the requested item or fluid output.
     * <p>
     * The template records all local ingredients and all outputs other than the main output as byproducts. Fluid
     * outputs are matched through their fluid display item identity so the normal request tree can discover them.
     */
    @Override
    public ICraftingTemplate addCrafting(IResource toCraft) {
        return templateBuilder.addCrafting(toCraft);
    }

    @Override
    public boolean canCraft(IResource toCraft) {
        for (ItemIdentifier item : getCraftedItems()) {
            if (toCraft.matches(item, IResource.MatchSettings.NORMAL)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getTodo() {
        return pipe.getItemOrderManager().totalAmountCountInAllOrders();
    }

    @Override
    public List<ItemIdentifierStack> getConfiguredCraftResults() {
        return patternHandler.getCraftResults(supportsFluidCrafting());
    }

    /**
     * Returns the cached HUD snapshot, rebuilding it only after crafting state changed or after a short external-state
     * recheck interval.
     */
    public PatternCraftingHudState getHudState() {
        return hudHandler.getHudState();
    }

    /**
     * Reports whether callers that broadcast HUD content should ask for a fresh snapshot.
     */
    public boolean shouldRefreshHudState() {
        return hudHandler.shouldRefreshHudState();
    }

    /**
     * Invalidates the cached HUD snapshot after a crafting-visible state change.
     */
    public void markHudStateDirty() {
        hudHandler.markDirty();
    }

    /**
     * Appends the current module-side staged crafting state to the crafting request debug dump.
     */
    public void appendDebugState(StringBuilder out) {
        out.append("Pattern crafting pipe at ").append(pipe.getX()).append(", ").append(pipe.getY()).append(", ")
                .append(pipe.getZ()).append(" router=").append(pipe.getRouter().getSimpleID()).append("\n");
        out.append("  mode stored=").append(blockingMode).append(" effective=").append(getEffectiveBlockingMode())
                .append(" fixed=").append(isBlockingModeFixed()).append(" runningCraft=")
                .append(blockingHandler.runningCraft()).append(" adjacentBatch=")
                .append(blockingHandler.runningCraftInAdjacent()).append(" satelliteBatches=")
                .append(blockingHandler.satelliteBatchPatternSlots()).append("\n");
        appendConnectedInventoryDebug(out);
        appendPatternDebug(out);
        appendStackMapDebug(out, "buffered ingredients", ingredientBuffer.asMap());
        appendStackMapDebug(out, "requested ingredients", requestedIngredients);
        appendStagedCraftDebug(out);
        appendOrderDebug(out);
        out.append("  lost ingredients queued=").append(lostIngredientHandler.size()).append("\n");
    }

    @Override
    public void itemLost(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        lostIngredientHandler.itemLost(item, info);
    }

    /**
     * Accepts an ingredient that was routed to this pattern module.
     * <p>
     * Requested ingredients are accepted into the module buffer even when the adjacent inventory is currently busy.
     * Those requests were already staged against this pipe's own capacity, so asking the adjacent inventory again here
     * can bounce valid subcraft results back to storage. Non-requested overflow still uses the normal arrival capacity.
     * The routed stack is reduced to the amount that could not be accepted.
     */
    @Override
    public void itemArrived(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        arrivalHandler.itemArrived(item, info);
    }

    protected ISlotUpgradeManager getUpgradeManager() {
        if (_service == null) {
            return null;
        }
        return _service.getUpgradeManager(slot, positionInt);
    }

    /**
     * The side of the target that items and fluids are inserted through: the sneaky side, otherwise the face touching
     * the pipe. {@code tile.orientation} points from the pipe to the target, so its opposite is the touching face.
     */
    ForgeDirection getInsertionOrientation(AdjacentTile tile) {
        ISlotUpgradeManager upgradeManager = getUpgradeManager();
        if (upgradeManager != null && upgradeManager.hasSneakyUpgrade()) {
            return upgradeManager.getSneakyOrientation();
        }
        return tile.orientation.getOpposite();
    }

    /**
     * Gives up on a partly inserted set owned by {@code instanceId}, or any set when it is null.
     */
    boolean abandonPendingDispatch(UUID instanceId) {
        return bufferDispatcher.abandonPendingDispatch(instanceId);
    }

    /**
     * Pattern slot of the partly inserted set that has to finish before anything else is pushed, or -1.
     */
    int pendingDispatchSlot() {
        return bufferDispatcher.pendingDispatchSlot();
    }

    int getRunningCraftForHandler() {
        return blockingHandler.runningCraft();
    }

    /**
     * Returns whether a pattern slot is currently allowed to receive more local ingredients.
     * <p>
     * Blocking modes restrict buffering to the active craft or to an empty connected inventory.
     */
    boolean canReceiveForPattern(int patternSlot) {
        if (!isPatternCraftingSupported(patternHandler.getConfiguredPatternStack(patternSlot))) {
            return false;
        }
        return blockingHandler.canReceiveForPattern(patternSlot);
    }

    /**
     * Calculates how many complete sets can be dispatched to every configured local and satellite target.
     */
    int maxDispatchablePatternSets(PatternCraftingReference ownerReference, ItemStack pattern, int maxSets) {
        return satelliteDispatchHandler.maxDispatchableSets(ownerReference, pattern, maxSets);
    }

    /**
     * Returns the still-unreserved room for one ingredient when {@code targetSets} are allowed to be staged.
     */
    int remainingIngredientRoomForSets(int patternSlot, ItemStack pattern, IPatternStack ingredient, int targetSets) {
        return capacity.remainingIngredientRoomForSets(patternSlot, pattern, ingredient, targetSets);
    }

    /**
     * Returns all ingredients that have to be buffered by this crafting pipe before one or more pattern sets can be
     * dispatched to the local adjacent target and any configured satellites.
     */
    List<IPatternStack> getAggregatedIngredients(ItemStack pattern) {
        return ingredientPlanner.getAggregatedIngredients(pattern);
    }

    /**
     * Returns the non-satellite ingredients that are inserted into this crafting pipe's adjacent target.
     */
    List<IPatternStack> getLocalAggregatedIngredients(ItemStack pattern) {
        return ingredientPlanner.getLocalAggregatedIngredients(pattern);
    }

    /**
     * Builds ingredient request groups per pattern input slot.
     * <p>
     * Ore dictionary substitutions may choose a different concrete item for each input slot, so solid ingredients must
     * not be merged across slots even when their pattern stacks look equivalent.
     */
    List<PatternIngredientTarget> getIngredientTargets(ItemStack pattern) {
        return ingredientPlanner.getIngredientTargets(pattern);
    }

    int bufferedIngredientAmount(int patternSlot, ItemStack pattern, IPatternStack ingredient) {
        return ingredientPlanner.bufferedIngredientAmount(patternSlot, pattern, ingredient);
    }

    /**
     * Counts in-flight local ingredients using the matching rules stored on the pattern.
     */
    int requestedIngredientAmount(int patternSlot, ItemStack pattern, IPatternStack ingredient) {
        return ingredientPlanner.requestedIngredientAmount(patternSlot, pattern, ingredient);
    }

    int requestedItemAmount(int patternSlot, ItemStack pattern, ItemIdentifier item) {
        return ingredientPlanner.requestedItemAmount(patternSlot, pattern, item);
    }

    int requestedItemAmount(PatternCraftingReference owner, int patternSlot, ItemIdentifier item) {
        return ingredientPlanner.requestedItemAmount(owner, getPatternStack(patternSlot), item);
    }

    List<PatternIngredientAssignment> buildBufferedIngredientPlan(int patternSlot, ItemStack pattern, int sets) {
        return ingredientPlanner.buildBufferedIngredientPlan(patternSlot, pattern, sets);
    }

    List<PatternIngredientAssignment> buildBufferedIngredientPlan(PatternCraftingReference owner, int patternSlot,
            ItemStack pattern, int sets) {
        return ingredientPlanner.buildBufferedIngredientPlan(owner, patternSlot, pattern, sets);
    }

    /**
     * Checks whether one input slot is assigned to a linked and currently known pattern satellite pipe.
     */
    boolean hasLinkedSatelliteAssignment(ItemStack pattern, int inputSlot) {
        return ingredientPlanner.hasLinkedSatelliteAssignment(pattern, inputSlot);
    }

    /**
     * Returns true when at least one input slot of this pattern is routed to a linked pattern satellite.
     */
    boolean hasLinkedSatelliteAssignments(ItemStack pattern) {
        return ingredientPlanner.hasLinkedSatelliteAssignments(pattern);
    }

    /**
     * Resolves the linked pattern satellite assigned to one solid input slot.
     */
    IRequestItems getSatelliteTargetForInputSlot(AbstractPattern pattern, int inputSlot) {
        return pattern == null ? null
                : ingredientPlanner.getSatelliteTargetForInputSlot(pattern.getPatternStack(), inputSlot);
    }

    /**
     * Resolves the linked pattern fluid satellite assigned to one fluid input slot.
     */
    IRequestFluid getFluidSatelliteTargetForInputSlot(AbstractPattern pattern, int inputSlot) {
        return pattern == null ? null
                : ingredientPlanner.getFluidSatelliteTargetForInputSlot(pattern.getPatternStack(), inputSlot);
    }

    /**
     * Attempts to push complete buffered pattern sets into the selected adjacent inventory or fluid handler.
     * <p>
     * Blocking modes keep one active pattern slot locked only while arrived ingredients are still buffered or while the
     * adjacent target is processing a batch inserted by that slot.
     */
    private void pushBufferedIngredients() {
        bufferDispatcher.pushBufferedIngredients();
    }

    /**
     * Pushes complete buffered sets for one pattern slot into the connected crafting target.
     */
    void pushBufferedIngredientsFor(int patternSlot) {
        bufferDispatcher.pushBufferedIngredientsFor(patternSlot);
    }

    /**
     * Counts complete local ingredient sets currently buffered for one pattern slot.
     */
    int completeBufferedSets(int patternSlot) {
        return bufferDispatcher.completeBufferedSets(patternSlot);
    }

    int completeBufferedSets(PatternCraftingReference owner, int patternSlot) {
        return ingredientPlanner.completeBufferedSets(owner, patternSlot, getPatternStack(patternSlot));
    }

    /**
     * Requests ingredients for all staged crafting orders that still have room in this module or the adjacent
     * inventory.
     */
    void requestIngredientsForStagedCrafts() {
        debugEventThrottled(
                "SCHED",
                20,
                "request staged crafts trigger bufferedSlots=%d requestedSlots=%d runningCraft=%d adjacentBatch=%s",
                ingredientBuffer.size(),
                requestedIngredients.size(),
                blockingHandler.runningCraft(),
                blockingHandler.runningCraftInAdjacent());
        stagedCrafting.requestIngredientsAfterCapacityChange();
    }

    /**
     * Cancels the running request tree that contains the selected pattern slot.
     */
    public boolean cancelPatternCraft(int patternSlot) {
        boolean changed = cancelHandler.cancelPatternCraft(patternSlot);
        if (changed) {
            markPersistentStateDirty();
        }
        return changed;
    }

    boolean cancelTrackedOrder(PatternCraftingOrder order) {
        boolean changed = cancelHandler.cancelTrackedOrder(order);
        if (changed) {
            markPersistentStateDirty();
        }
        return changed;
    }

    /**
     * Cancels active staged crafts and returns all locally owned pattern inputs to storage.
     */
    public boolean returnStoredInputsToStorage() {
        return cancelHandler.returnStoredInputsToStorage();
    }

    /**
     * @return a pattern slot whose buffered arrived ingredients can be pushed as a complete set.
     */
    int findCompleteBufferedPattern() {
        return bufferDispatcher.findCompleteBufferedPattern();
    }

    /**
     * Marks the first arrived ingredient for a slot as the active blocking craft when no other slot is active.
     */
    void activateRunningCraftFromBuffer(int patternSlot, PatternCraftingReference owner) {
        blockingHandler.activateFromBuffer(patternSlot, owner);
    }

    PatternCraftingReference completeBufferOwner(int patternSlot) {
        return bufferDispatcher.findCompleteBufferedOwner(patternSlot);
    }

    /**
     * Returns whether the active slot is still locked by arrived buffered ingredients or an inserted adjacent batch.
     */
    boolean isRunningCraftLocked() {
        return blockingHandler.isRunningCraftLocked();
    }

    /**
     * Refreshes blocking state once per tick after push/request processing.
     */
    private void clearRunningCraftIfFinished() {
        bufferDispatcher.refreshRunningCraftState();
    }

    AdjacentTile getConnectedInventoryTile() {
        return adjacentInventory.getConnected();
    }

    /**
     * Blocking-mode readiness of the target for one pattern: fluid tanks only count when the pattern puts fluid into
     * the target, so leftover fluid doesn't hold up an item-only craft.
     */
    boolean isInventoryEmptyFor(AdjacentTile connected, int patternSlot) {
        ItemStack pattern = patternSlot < 0 ? null : getPatternStack(patternSlot);
        boolean includeFluids = pattern == null || ingredientPlanner.hasLocalFluidIngredients(pattern);
        return adjacentInventory.isEmpty(connected, includeFluids);
    }

    /**
     * Determines whether this crafting pipe's outstanding output orders should be reported as destination-buffered.
     * <p>
     * The network-wide can-sink lookup may route back to this same module when a pattern requests a subitem that
     * another pattern in this pipe crafts. Self-destined orders are already represented by
     * {@link #requestedIngredients}, so they are treated as locally buffered here and are not sent through
     * {@link LogisticsManager#canSink} again.
     */
    private boolean areAllOrdersBuffered() {
        if (checkingBufferedOrders) {
            return true;
        }
        checkingBufferedOrders = true;
        try {
            boolean result = true;
            for (LogisticsItemOrder order : pipe.getItemOrderManager()) {
                if (isOrderDestinationThisModule(order)) {
                    debug(
                            "buffer check treats self-destined order as buffered order=%s amount=%d",
                            order.getResource().getItem(),
                            order.getAmount());
                    continue;
                }
                if (order.getDestination() instanceof IItemSpaceControl) {
                    SinkReply reply = LogisticsManager.canSink(
                            order.getDestination().getRouter(),
                            null,
                            true,
                            order.getResource().getItem(),
                            null,
                            true,
                            false);
                    if (reply != null && reply.bufferMode == BufferMode.NONE && reply.maxNumberOfItems >= 1) {
                        debug(
                                "buffer check found unbuffered destination order=%s replyRoom=%d",
                                order.getResource().getItem(),
                                reply.maxNumberOfItems);
                        result = false;
                        break;
                    }
                } else {
                    debug(
                            "buffer check found destination without space control order=%s",
                            order.getResource().getItem());
                    result = false;
                    break;
                }
            }
            debug("buffer check result=%s", result);
            return result;
        } finally {
            checkingBufferedOrders = false;
        }
    }

    boolean isOrderDestinationThisModule(LogisticsItemOrder order) {
        IRequest destination = order.getDestination();
        return destination == this || (destination != null && destination.getRouter() == getRouter());
    }

    boolean isOrderDestinationThisModule(LogisticsFluidOrder order) {
        IRequest destination = order.getDestination();
        return destination == this || (destination != null && destination.getRouter() == getRouter());
    }

    int requestedSamePipeItemAmount(LogisticsItemOrder order) {
        if (!(order.getInformation() instanceof PatternTargetInformation)) {
            return order.getAmount();
        }
        int patternSlot = ((PatternTargetInformation) order.getInformation()).patternSlot();
        return requestedItemAmount(patternSlot, getPatternStack(patternSlot), order.getResource().getItem());
    }

    int requestedSamePipeFluidAmount(LogisticsFluidOrder order) {
        if (!(order.getInformation() instanceof PatternTargetInformation)) {
            return order.getAmount();
        }
        int patternSlot = ((PatternTargetInformation) order.getInformation()).patternSlot();
        return requestedIngredient.amount(patternSlot, order.getFluid());
    }

    private void appendConnectedInventoryDebug(StringBuilder out) {
        AdjacentTile connected = adjacentInventory.getConnected();
        if (connected == null) {
            out.append("  connected inventory: <none>\n");
            return;
        }
        if (connected.tile == null) {
            out.append("  connected inventory: <null tile> side=").append(connected.orientation).append("\n");
            return;
        }
        out.append("  connected inventory: ").append(connected.tile.getClass().getName()).append(" side=")
                .append(connected.orientation).append(" empty=").append(adjacentInventory.isEmpty(connected))
                .append(" patternTable=").append(adjacentInventory.isConnectedToPatternCraftingTable()).append("\n");
    }

    private void appendPatternDebug(StringBuilder out) {
        out.append("  patterns:\n");
        boolean found = false;
        for (int slot = 0; slot < patternHandler.size(); slot++) {
            ItemStack pattern = getPatternStack(slot);
            if (pattern == null) {
                continue;
            }
            found = true;
            out.append("    slot ").append(slot).append("\n");
            AbstractPattern configuredPattern = ItemPattern.fromStack(pattern);
            appendPatternSlots(out, configuredPattern, 0, configuredPattern.getIngredientSlotCount(), "      inputs");
            appendPatternSlots(
                    out,
                    configuredPattern,
                    configuredPattern.getResultSlotStart(),
                    configuredPattern.getItemSlotCount(),
                    "      results");
        }
        if (!found) {
            out.append("    <none>\n");
        }
    }

    private void appendPatternSlots(StringBuilder out, AbstractPattern pattern, int start, int end, String label) {
        out.append(label).append(": ");
        boolean found = false;
        for (int slot = start; slot < end; slot++) {
            IPatternStack stack = pattern.getPatternStackInSlot(slot);
            if (stack == null || stack.getAmount() <= 0) {
                continue;
            }
            if (found) {
                out.append(", ");
            }
            out.append("slot ").append(slot).append("=").append(stack);
            found = true;
        }
        if (!found) {
            out.append("<none>");
        }
        out.append("\n");
    }

    private void appendStackMapDebug(StringBuilder out, String label,
            Map<Integer, List<IPatternStack>> stacksByPattern) {
        out.append("  ").append(label).append(":\n");
        if (stacksByPattern.isEmpty()) {
            out.append("    <none>\n");
            return;
        }
        for (Map.Entry<Integer, List<IPatternStack>> entry : stacksByPattern.entrySet()) {
            out.append("    slot ").append(entry.getKey()).append(": ");
            appendInlineStacks(out, entry.getValue());
            out.append("\n");
        }
    }

    private void appendStagedCraftDebug(StringBuilder out) {
        out.append("  staged crafts:\n");
        if (pendingStagedCrafting != null) {
            out.append("    pending restore attempts=").append(stagedCraftingRestoreAttempts).append("/")
                    .append(MAX_STAGED_CRAFTING_RESTORE_ATTEMPTS).append(" instances=")
                    .append(PatternStagedCraftingCoordinator.pendingInstanceIds(pendingStagedCrafting)).append("\n");
        }
        stagedCrafting.appendDebugState(out, "    ");
    }

    private void appendOrderDebug(StringBuilder out) {
        out.append("  output orders:\n");
        boolean found = false;
        for (LogisticsItemOrder order : pipe.getItemOrderManager()) {
            found = true;
            out.append("    - ").append(order.getType()).append(" ").append(order.getAmount()).append("x ")
                    .append(order.getResource().getItem()).append(" -> router ").append(order.getRouterId())
                    .append(order.isInProgress() ? " in-progress" : "").append(order.isFinished() ? " finished" : "");
            if (order.getInformation() != null) {
                out.append(" info=").append(order.getInformation());
            }
            out.append("\n");
        }
        if (!found) {
            out.append("    <none>\n");
        }
    }

    private void appendInlineStacks(StringBuilder out, List<IPatternStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            out.append("<none>");
            return;
        }
        for (int i = 0; i < stacks.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            IPatternStack stack = stacks.get(i);
            out.append(stack == null ? "<null>" : stack.toString());
        }
    }

    public void onAllowedRemoval() {

        World world = pipe.getWorld();

        bufferDispatcher.abandonPendingDispatch(null);
        blockingHandler.retrieveAndReleaseAllSatelliteBatches();
        stagedCrafting.releaseAll();
        requestedIngredients.clear();
        lostIngredientHandler.clear();
        markHudStateDirty();

        patternInventory.dropContents(world, pipe.getX(), pipe.getY(), pipe.getZ());
        ingredientBuffer.dropContents(world, pipe.getX(), pipe.getY(), pipe.getZ());
    }

    private static class ThrottledDebugEvent {

        private long lastLoggedTick = Long.MIN_VALUE;
        private int suppressed;
    }

}
