package logisticspipes.crafting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.utils.item.ItemIdentifierStack;

/** A client-safe snapshot of one complete, cancellable pattern-crafting instance. */
public final class PatternCraftingMonitorEntry {

    private final UUID instanceId;
    private final List<PatternCraftingMonitorNode> roots;
    private final boolean restoring;
    private final int restoreAttempts;
    private final int maxRestoreAttempts;

    public PatternCraftingMonitorEntry(UUID instanceId, List<PatternCraftingMonitorNode> roots) {
        this(instanceId, roots, false, 0, 0);
    }

    private PatternCraftingMonitorEntry(UUID instanceId, List<PatternCraftingMonitorNode> roots, boolean restoring,
            int restoreAttempts, int maxRestoreAttempts) {
        this.instanceId = instanceId;
        this.roots = Collections.unmodifiableList(new ArrayList<>(roots));
        this.restoring = restoring;
        this.restoreAttempts = Math.max(0, restoreAttempts);
        this.maxRestoreAttempts = Math.max(0, maxRestoreAttempts);
    }

    public static PatternCraftingMonitorEntry restoring(UUID instanceId, List<PatternCraftingMonitorNode> roots,
            int restoreAttempts, int maxRestoreAttempts) {
        return new PatternCraftingMonitorEntry(instanceId, roots, true, restoreAttempts, maxRestoreAttempts);
    }

    public static PatternCraftingMonitorEntry readData(LPDataInputStream data) throws IOException {
        UUID instanceId = new UUID(data.readLong(), data.readLong());
        boolean restoring = data.readBoolean();
        int restoreAttempts = data.readInt();
        int maxRestoreAttempts = data.readInt();
        int rootCount = data.readInt();
        List<PatternCraftingMonitorNode> roots = new ArrayList<>(rootCount);
        for (int i = 0; i < rootCount; i++) {
            roots.add(PatternCraftingMonitorNode.readData(data));
        }
        return new PatternCraftingMonitorEntry(instanceId, roots, restoring, restoreAttempts, maxRestoreAttempts);
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public List<PatternCraftingMonitorNode> getRoots() {
        return roots;
    }

    public ItemIdentifierStack getDisplayStack() {
        for (PatternCraftingMonitorNode root : roots) {
            if (root.getStack() != null) {
                return root.getStack();
            }
        }
        return null;
    }

    public boolean isInProgress() {
        for (PatternCraftingMonitorNode root : roots) {
            if (root.isInProgress()) {
                return true;
            }
        }
        return false;
    }

    public boolean isRestoring() {
        return restoring;
    }

    public int getRestoreAttempts() {
        return restoreAttempts;
    }

    public int getMaxRestoreAttempts() {
        return maxRestoreAttempts;
    }

    public void writeData(LPDataOutputStream data) throws IOException {
        data.writeLong(instanceId.getMostSignificantBits());
        data.writeLong(instanceId.getLeastSignificantBits());
        data.writeBoolean(restoring);
        data.writeInt(restoreAttempts);
        data.writeInt(maxRestoreAttempts);
        data.writeInt(roots.size());
        for (PatternCraftingMonitorNode root : roots) {
            root.writeData(data);
        }
    }
}
