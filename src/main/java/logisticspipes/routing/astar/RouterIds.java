package logisticspipes.routing.astar;

import java.util.BitSet;

/** Allocator of the small, dense router ids that double as junction ids. */
public final class RouterIds {

    private static int firstFreeId = 1;
    private static final BitSet simpleIdUsedSet = new BitSet();

    private RouterIds() {}

    static synchronized int claim() {
        int idx = simpleIdUsedSet.nextClearBit(firstFreeId);
        firstFreeId = idx + 1;
        simpleIdUsedSet.set(idx);
        return idx;
    }

    static synchronized void release(int idx) {
        simpleIdUsedSet.clear(idx);
        if (idx < firstFreeId) {
            firstFreeId = idx;
        }
    }

    /** Upper bound for router ids, for sizing {@link BitSet}s and tables. */
    public static synchronized int getBiggestSimpleID() {
        return simpleIdUsedSet.size();
    }

    static synchronized void cleanup() {
        simpleIdUsedSet.clear();
        firstFreeId = 1;
    }
}
