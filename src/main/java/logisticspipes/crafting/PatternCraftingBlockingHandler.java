package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.utils.AdjacentTile;

/**
 * Owns the blocking-mode state for one pattern crafting module.
 * <p>
 * Local adjacent batches and satellite batches are treated through the same mode policy: {@code OFF} does not block
 * other patterns, while {@code SMART} and {@code BLOCKING} keep one active pattern locked until its current target work
 * has finished.
 */
final class PatternCraftingBlockingHandler {

    private final ModulePatternCrafting module;
    private final List<SatelliteBatch> satelliteBatches = new ArrayList<>();
    private int runningCraft = -1;
    private PatternCraftingReference runningCraftReference;
    private boolean runningCraftInAdjacent;
    private long lastSatelliteRefreshTick = Long.MIN_VALUE;

    PatternCraftingBlockingHandler(ModulePatternCrafting module) {
        this.module = module;
    }

    int runningCraft() {
        return runningCraft;
    }

    boolean runningCraftInAdjacent() {
        return runningCraftInAdjacent;
    }

    PatternCraftingReference runningCraftReference() {
        return runningCraftReference;
    }

    boolean hasSatelliteBatches() {
        refreshSatelliteBatches();
        return !satelliteBatches.isEmpty();
    }

    List<Integer> satelliteBatchPatternSlots() {
        refreshSatelliteBatches();
        List<Integer> slots = new ArrayList<>();
        for (SatelliteBatch batch : satelliteBatches) {
            if (!slots.contains(batch.patternSlot())) {
                slots.add(batch.patternSlot());
            }
        }
        return slots;
    }

    boolean hasSatelliteBatchFor(int patternSlot) {
        refreshSatelliteBatches();
        return hasSatelliteBatchForWithoutRefresh(patternSlot);
    }

    boolean isPatternActive(int patternSlot) {
        return runningCraft == patternSlot || hasSatelliteBatchFor(patternSlot);
    }

    void restoreRunningCraft(int patternSlot, PatternCraftingReference reference, boolean inAdjacent) {
        boolean changed = runningCraft != patternSlot || !java.util.Objects.equals(runningCraftReference, reference)
                || runningCraftInAdjacent != (patternSlot >= 0 && inAdjacent);
        runningCraft = patternSlot;
        runningCraftReference = patternSlot >= 0 ? reference : null;
        runningCraftInAdjacent = patternSlot >= 0 && inAdjacent;
        if (changed) {
            module.markHudStateDirty();
        }
    }

    void activateFromBuffer(int patternSlot, PatternCraftingReference reference) {
        if (module.getEffectiveBlockingMode() == PipeItemsPatternCraftingLogistics.BlockingMode.OFF
                || runningCraft >= 0) {
            return;
        }
        setRunningCraft(patternSlot, reference, false);
        module.debugEvent("BUFFER", "running craft activated from buffer slot=%d", patternSlot);
    }

    boolean canReceiveForPattern(int patternSlot) {
        refreshSatelliteBatches();
        PipeItemsPatternCraftingLogistics.BlockingMode mode = module.getEffectiveBlockingMode();
        if (mode == PipeItemsPatternCraftingLogistics.BlockingMode.OFF) {
            return true;
        }
        if (hasActiveSatelliteBatchWithoutRefresh()) {
            return false;
        }
        if (isRunningCraftLocked()) {
            return runningCraft == patternSlot;
        }
        AdjacentTile connected = module.getConnectedInventoryTile();
        return connected == null || module.isInventoryEmptyFor(connected, patternSlot);
    }

    boolean shouldSkipPushFor(int patternSlot) {
        refreshSatelliteBatches();
        if (module.getEffectiveBlockingMode() == PipeItemsPatternCraftingLogistics.BlockingMode.OFF) {
            return false;
        }
        if (hasActiveSatelliteBatchWithoutRefresh()) {
            return true;
        }
        return isRunningCraftLocked() && runningCraft != patternSlot;
    }

    void markDispatched(int patternSlot, PatternCraftingReference reference, SatelliteBatch satelliteBatch) {
        if (module.getEffectiveBlockingMode() == PipeItemsPatternCraftingLogistics.BlockingMode.OFF) {
            return;
        }
        if (satelliteBatch != null) {
            satelliteBatches.add(satelliteBatch);
            setRunningCraft(patternSlot, reference, true);
            module.markHudStateDirty();
            return;
        }
        setRunningCraft(patternSlot, reference, true);
    }

    boolean isRunningCraftLocked() {
        return isRunningCraftLocked(module.getConnectedInventoryTile());
    }

    boolean isRunningCraftLocked(AdjacentTile connected) {
        refreshRunningCraftState(connected);
        if (runningCraft < 0) {
            return false;
        }
        if (hasSatelliteBatchForWithoutRefresh(runningCraft)) {
            return true;
        }
        return runningCraftInAdjacent && connected != null && !module.isInventoryEmptyFor(connected, runningCraft);
    }

    void refreshRunningCraftState(AdjacentTile connected) {
        refreshSatelliteBatches();
        if (module.getEffectiveBlockingMode() == PipeItemsPatternCraftingLogistics.BlockingMode.OFF) {
            setRunningCraft(-1, null, false);
            return;
        }
        if (runningCraft >= 0 && module.getPatternStack(runningCraft) == null) {
            module.debugEvent("BUFFER", "running craft cleared slot=%d: pattern missing", runningCraft);
            setRunningCraft(-1, null, false);
        }
        if (runningCraft >= 0 && runningCraftInAdjacent
                && !hasSatelliteBatchForWithoutRefresh(runningCraft)
                && (connected == null || module.isInventoryEmptyFor(connected, runningCraft))) {
            module.debugEvent("BUFFER", "running craft adjacent batch finished slot=%d", runningCraft);
            setRunningCraft(runningCraft, runningCraftReference, false);
        }
        if (runningCraft >= 0 && !runningCraftInAdjacent
                && module.completeBufferedSets(runningCraftReference, runningCraft) <= 0) {
            module.debugEvent("BUFFER", "running craft released slot=%d: no arrived ingredients remain", runningCraft);
            setRunningCraft(-1, null, false);
        }
        if (runningCraft < 0) {
            setRunningCraft(-1, null, false);
            int next = module.findCompleteBufferedPattern();
            if (next != -1) {
                setRunningCraft(next, module.completeBufferOwner(next), false);
                module.debugEvent("BUFFER", "running craft selected buffered slot=%d", runningCraft);
            }
        }
    }

    String getHudSatelliteStatus(int patternSlot) {
        refreshSatelliteBatches();
        if (hasSatelliteBatchForWithoutRefresh(patternSlot)) {
            return "Doing: waiting on satellites";
        }
        if (module.getEffectiveBlockingMode() != PipeItemsPatternCraftingLogistics.BlockingMode.OFF
                && hasActiveSatelliteBatchWithoutRefresh()) {
            return "Waiting: satellites reserved";
        }
        return null;
    }

    boolean isBlockedByOtherRunningCraft(int patternSlot, AdjacentTile connected) {
        if (module.getEffectiveBlockingMode() == PipeItemsPatternCraftingLogistics.BlockingMode.OFF || runningCraft < 0
                || runningCraft == patternSlot) {
            return false;
        }
        if (hasSatelliteBatchForWithoutRefresh(runningCraft)) {
            return true;
        }
        return runningCraftInAdjacent && connected != null && !module.isInventoryEmptyFor(connected, runningCraft);
    }

    boolean retrieveAndReleaseSatelliteBatches(Collection<Integer> patternSlots) {
        boolean changed = false;
        Iterator<SatelliteBatch> iterator = satelliteBatches.iterator();
        while (iterator.hasNext()) {
            SatelliteBatch batch = iterator.next();
            if (!patternSlots.contains(batch.patternSlot())) {
                continue;
            }
            batch.retrieveAndRelease();
            iterator.remove();
            changed = true;
        }
        if (changed) {
            module.markHudStateDirty();
        }
        return changed;
    }

    boolean retrieveAndReleaseSatelliteBatches(UUID instanceId) {
        boolean changed = false;
        Iterator<SatelliteBatch> iterator = satelliteBatches.iterator();
        while (iterator.hasNext()) {
            SatelliteBatch batch = iterator.next();
            if (batch.ownerReference() == null || !instanceId.equals(batch.ownerReference().instanceId())) {
                continue;
            }
            batch.retrieveAndRelease();
            iterator.remove();
            changed = true;
        }
        if (changed) {
            module.markHudStateDirty();
        }
        return changed;
    }

    boolean clearRunningCraft(UUID instanceId) {
        if (runningCraftReference == null || !instanceId.equals(runningCraftReference.instanceId())) {
            return false;
        }
        setRunningCraft(-1, null, false);
        return true;
    }

    boolean retrieveAndReleaseAllSatelliteBatches() {
        if (satelliteBatches.isEmpty()) {
            return false;
        }
        for (SatelliteBatch batch : satelliteBatches) {
            batch.retrieveAndRelease();
        }
        satelliteBatches.clear();
        module.markHudStateDirty();
        return true;
    }

    boolean releaseAllSatelliteBatches() {
        if (satelliteBatches.isEmpty()) {
            return false;
        }
        for (SatelliteBatch batch : satelliteBatches) {
            batch.release();
        }
        satelliteBatches.clear();
        module.markHudStateDirty();
        return true;
    }

    private void refreshSatelliteBatches() {
        long tick = module.currentWorldTick();
        if (lastSatelliteRefreshTick == tick) {
            return;
        }
        lastSatelliteRefreshTick = tick;
        Iterator<SatelliteBatch> iterator = satelliteBatches.iterator();
        while (iterator.hasNext()) {
            SatelliteBatch batch = iterator.next();
            if (!batch.isConsumed()) {
                continue;
            }
            module.debugEvent(
                    "BUFFER",
                    "satellite batch completed slot=%d satellites=%d",
                    batch.patternSlot(),
                    batch.size());
            batch.release();
            iterator.remove();
            module.markHudStateDirty();
        }
    }

    private boolean hasActiveSatelliteBatchWithoutRefresh() {
        return !satelliteBatches.isEmpty();
    }

    private boolean hasSatelliteBatchForWithoutRefresh(int patternSlot) {
        for (SatelliteBatch batch : satelliteBatches) {
            if (batch.patternSlot() == patternSlot) {
                return true;
            }
        }
        return false;
    }

    private void setRunningCraft(int patternSlot, PatternCraftingReference reference, boolean inAdjacent) {
        if (runningCraft == patternSlot && java.util.Objects.equals(runningCraftReference, reference)
                && runningCraftInAdjacent == inAdjacent) {
            return;
        }
        runningCraft = patternSlot;
        runningCraftReference = patternSlot >= 0 ? reference : null;
        runningCraftInAdjacent = patternSlot >= 0 && inAdjacent;
        module.markHudStateDirty();
    }

    interface SatelliteBatch {

        PatternCraftingReference ownerReference();

        int patternSlot();

        int size();

        boolean isConsumed();

        void release();

        void retrieveAndRelease();
    }
}
