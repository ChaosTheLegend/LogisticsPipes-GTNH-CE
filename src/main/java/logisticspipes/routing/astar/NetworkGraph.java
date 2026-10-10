package logisticspipes.routing.astar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleBiFunction;

/**
 * Immutable snapshot of the junction graph.
 * <p>
 * The writer never mutates a published snapshot; every structural edit builds a new one and swaps it in through an
 * {@code AtomicReference}. A query takes one snapshot reference at its start and uses nothing else, so it always sees a
 * consistent graph.
 * <p>
 * Node storage is an array indexed by {@link JunctionId#index()}. Edges are stored on their owning node; an edge id
 * encodes its owner, so {@link #edge(long)} needs no global map.
 */
public final class NetworkGraph {

    static final NetworkGraph EMPTY = new NetworkGraph(
            0,
            new JunctionNode[0],
            0,
            new int[0],
            new double[0],
            Collections.emptyMap(),
            new ChunkEdgeIndex(),
            new int[0]);

    private final long generation;
    private final JunctionNode[] nodes;
    private final int nodeCount;
    /** Component representative (a union-find slot) per junction index, -1 if absent. */
    private final int[] componentOf;
    /** Heuristic scale of each junction's component, per junction index (hot path of the search). */
    private final double[] nodeScale;
    private final Map<Integer, ComponentInfo> components;
    private final ChunkEdgeIndex chunkIndex;
    /** Junctions that carry data (power providers), ascending. */
    private final int[] dataJunctions;

    NetworkGraph(long generation, JunctionNode[] nodes, int nodeCount, int[] componentOf, double[] nodeScale,
            Map<Integer, ComponentInfo> components, ChunkEdgeIndex chunkIndex, int[] dataJunctions) {
        this.generation = generation;
        this.nodes = nodes;
        this.nodeCount = nodeCount;
        this.componentOf = componentOf;
        this.nodeScale = nodeScale;
        this.components = components;
        this.chunkIndex = chunkIndex;
        this.dataJunctions = dataJunctions;
    }

    /** Monotonic snapshot number. */
    public long generation() {
        return generation;
    }

    public int nodeCount() {
        return nodeCount;
    }

    /** One past the highest junction index this snapshot can hold. */
    public int capacity() {
        return nodes.length;
    }

    public JunctionNode node(JunctionId id) {
        return node(id.index());
    }

    JunctionNode node(int index) {
        return index >= 0 && index < nodes.length ? nodes[index] : null;
    }

    public boolean contains(JunctionId id) {
        return node(id) != null;
    }

    public boolean isActive(JunctionId id) {
        return isActive(id.index());
    }

    /**
     * The current version of a corridor recorded on an earlier snapshot, if items routed along it can still take it as
     * a scheduled hop: same ends, exit side, filters and route flag, still teleportable, and leading to an active
     * junction. {@code null} otherwise.
     */
    public CorridorEdge hopStillValid(CorridorEdge recorded) {
        CorridorEdge now = edge(recorded.id);
        if (now == null || now.travelPath == null
                || !now.to.equals(recorded.to)
                || now.exitSide != recorded.exitSide
                || (now.flags & RoutingFlags.CAN_ROUTE_TO) == 0
                || !now.filters.equals(recorded.filters)
                || !isActive(now.to.index())) {
            return null;
        }
        return now;
    }

    boolean isActive(int index) {
        JunctionNode n = node(index);
        return n != null && n.active;
    }

    public List<JunctionNode> nodes() {
        List<JunctionNode> list = new ArrayList<>(nodeCount);
        for (JunctionNode n : nodes) {
            if (n != null) {
                list.add(n);
            }
        }
        return list;
    }

    public CorridorEdge edge(long edgeId) {
        JunctionNode owner = node(CorridorEdge.ownerIndex(edgeId));
        return owner == null ? null : owner.edgeById(edgeId);
    }

    public int edgeCount() {
        int count = 0;
        for (JunctionNode n : nodes) {
            if (n != null) {
                count += n.edges.size();
            }
        }
        return count;
    }

    /** Component representative, or -1 if the junction is not in the graph. */
    public int componentOf(JunctionId id) {
        return componentOf(id.index());
    }

    int componentOf(int index) {
        if (index < 0 || index >= componentOf.length || node(index) == null) {
            return -1;
        }
        return componentOf[index];
    }

    /** Union-find reachability pre-check. Different components can never reach each other. */
    public boolean sameComponent(JunctionId a, JunctionId b) {
        int ca = componentOf(a);
        return ca >= 0 && ca == componentOf(b);
    }

    public int componentCount() {
        return components.size();
    }

    private ComponentInfo info(int index) {
        int c = componentOf(index);
        return c < 0 ? null : components.get(c);
    }

    /** Identity of the junction's component, stable across merges into it and splits off it; -1 if absent. */
    long componentIdentity(int index) {
        ComponentInfo info = info(index);
        return info == null ? -1 : info.identity;
    }

    /** Changes whenever anything in the junction's component changes. */
    long changeStamp(int index) {
        ComponentInfo info = info(index);
        return info == null ? -1 : info.changeStamp;
    }

    /** Stamp of the newest improvement in the junction's component. */
    long improvementStamp(int index) {
        ComponentInfo info = info(index);
        return info == null ? -1 : info.improvementStamp();
    }

    /** Newest-first log of the component's improvements. */
    ImprovementEvent improvementLog(int index) {
        ComponentInfo info = info(index);
        return info == null ? null : info.log;
    }

    /** Per-block lower bound on corridor cost in the junction's component; 0 disables the heuristic. */
    double heuristicScale(int index) {
        return index >= 0 && index < nodeScale.length ? nodeScale[index] : 0;
    }

    /** Admissible lower bound on the cost of any route between two junctions (scaled Manhattan, 0 if unknown). */
    double lowerBound(int a, int b) {
        if (a == b) {
            return 0;
        }
        JunctionNode na = node(a);
        JunctionNode nb = node(b);
        if (na == null || nb == null || na.dimension != nb.dimension) {
            return 0;
        }
        double scale = heuristicScale(a);
        return scale <= 0 ? 0 : scale * manhattan(na, nb);
    }

    /** Junctions in the same component as {@code id} that carry data (power providers), excluding {@code id}. */
    public List<JunctionId> dataJunctionsInComponentOf(JunctionId id) {
        int c = componentOf(id);
        if (c < 0) {
            return Collections.emptyList();
        }
        List<JunctionId> list = new ArrayList<>();
        for (int j : dataJunctions) {
            if (j != id.index() && componentOf(j) == c) {
                list.add(JunctionId.of(j));
            }
        }
        return list;
    }

    public ChunkEdgeIndex chunkIndex() {
        return chunkIndex;
    }

    /**
     * Manhattan distance between two junctions, scaled by the cheapest per-block corridor cost of the component.
     * <p>
     * Pipe movement is axis-aligned, so {@code |dx| + |dy| + |dz|} blocks is a lower bound on the blocks any corridor
     * between the two junctions must cover. It only turns into a lower bound on <em>cost</em> once it is multiplied by
     * the minimum cost per block: Thermal Dynamics ducts can weigh less than 1 per block, and tesseracts or other
     * special connections link far-apart junctions for almost nothing. The writer therefore stores
     * {@code min(weight / manhattan)} over every corridor of the component as the scale (0 if any corridor crosses a
     * dimension), which keeps the heuristic admissible and consistent: for every edge {@code u->v},
     * {@code h(u) - h(v) <= scale * manhattan(u, v) <= weight(u, v)}.
     * <p>
     * If a pipe type ever gets a per-axis cost (e.g. a vertical elevator pipe), the scale must become per axis
     * ({@code dx * minHorizontal + dy * minVertical + dz * minHorizontal}) or the heuristic can overestimate and A*
     * silently stops returning shortest routes. {@code JunctionSearchTest} checks admissibility on random graphs.
     */
    public ToDoubleBiFunction<JunctionId, JunctionId> manhattanHeuristic() {
        return (a, b) -> lowerBound(a.index(), b.index());
    }

    static int manhattan(JunctionNode a, JunctionNode b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y) + Math.abs(a.z - b.z);
    }

    @Override
    public String toString() {
        return "NetworkGraph{gen=" + generation + ", nodes=" + nodeCount + "}";
    }
}
