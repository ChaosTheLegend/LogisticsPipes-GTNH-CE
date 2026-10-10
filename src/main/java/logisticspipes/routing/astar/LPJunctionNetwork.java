package logisticspipes.routing.astar;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Server-wide owner of the junction graph and route engine used by {@link JunctionRouter}.
 * <p>
 * With {@code threads > 0} graph edits are applied on a dedicated mutation thread and stale routes are refreshed on a
 * bounded pool; with {@code threads == 0} everything runs on the calling thread.
 */
public final class LPJunctionNetwork {

    private static final JunctionGraphWriter WRITER = new JunctionGraphWriter();
    private static final JunctionRoutingEngine ENGINE = new JunctionRoutingEngine(WRITER);
    private static ExecutorService executor;

    private LPJunctionNetwork() {}

    public static JunctionGraphWriter writer() {
        return WRITER;
    }

    public static JunctionRoutingEngine engine() {
        return ENGINE;
    }

    public static synchronized void start(int threads, int priority) {
        if (threads <= 0 || executor != null) {
            return;
        }
        WRITER.startAsync(JunctionRoutingThread.factory("LogisticsPipes JunctionGraphWriter", priority));
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                threads,
                threads,
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(4096),
                JunctionRoutingThread.factory("LogisticsPipes RouteRefresh", priority),
                new ThreadPoolExecutor.AbortPolicy());
        pool.allowCoreThreadTimeOut(true);
        executor = pool;
        ENGINE.setExecutor(pool);
    }

    /** Server stop: forget every junction and cached route. Worker threads stay for the next server. */
    public static synchronized void cleanup() {
        WRITER.clear();
        ENGINE.clear();
        InterestRegistry.cleanup();
        RouterIds.cleanup();
    }

    /**
     * A chunk went away: every corridor passing through it may now be cut, so the junctions owning those corridors
     * re-scan on their next tick. The chunk index turns this into a lookup instead of a walk over every router.
     */
    public static void onChunkUnload(int dimension, int chunkX, int chunkZ) {
        Set<Long> edges = WRITER.chunkIndex().edgesIn(ChunkEdgeIndex.chunkKey(dimension, chunkX, chunkZ));
        if (edges.isEmpty()) {
            return;
        }
        NetworkGraph graph = WRITER.graph();
        Set<Integer> owners = new HashSet<>();
        for (long edgeId : edges) {
            owners.add(CorridorEdge.ownerIndex(edgeId));
        }
        for (int owner : owners) {
            JunctionNode node = graph.node(owner);
            if (node != null && node.payload instanceof JunctionRouter
                    && node.dimension == dimension
                    && ((node.x >> 4) != chunkX || (node.z >> 4) != chunkZ)) {
                ((JunctionRouter) node.payload).scheduleCorridorRecheck();
            }
        }
    }

    public static void resetStats() {
        ENGINE.resetStats();
    }

    public static List<String> describe() {
        return ENGINE.describe();
    }
}
