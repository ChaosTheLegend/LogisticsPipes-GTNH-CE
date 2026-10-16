package logisticspipes.routing.astar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleBiFunction;

/**
 * The one shortest-path routine of the junction router.
 * <p>
 * A* is Dijkstra with a heuristic added to the queue priority, so there is a single implementation and the call sites
 * only differ in their arguments:
 * <ul>
 * <li>{@code heuristic == null}: plain Dijkstra ordering.</li>
 * <li>{@code heuristic != null}: A* ordering by {@code g + h}, where {@code h(n)} is the minimum of the heuristic over
 * all targets. A minimum of consistent heuristics is consistent, so a settled route is final and nothing is ever
 * re-opened.</li>
 * <li>one target: stop as soon as that target is settled.</li>
 * <li>several targets: stop as soon as every target is settled (one-to-many; shared exploration).</li>
 * <li>{@code targets == null}: settle everything reachable (used for the legacy whole-network views).</li>
 * </ul>
 * "Settled" is per flag: a target is done once every flag the source can emit has arrived over a filter-free route (see
 * {@link RouteLabel} for the filter rule). Queue entries that cannot deliver a flag still missing at any remaining
 * target are dropped, so a flag that never reaches a target does not make the search drain the component.
 * <p>
 * The search runs on the router's hot path, so apart from the {@link RouteLabel}s it returns it allocates next to
 * nothing: the priority queue is an index heap over primitive arrays in per-thread scratch, and queue entries store
 * only what the ordering needs; filters and block distance are derived from the parent label when an entry is taken.
 */
public final class JunctionSearch {

    /** Above this many targets the per-node minimum over targets costs more than the goal direction saves. */
    static final int MAX_HEURISTIC_TARGETS = 64;

    private JunctionSearch() {}

    /**
     * Per-thread scratch, reset through a touched list so a search costs O(visited), not O(graph).
     * <p>
     * Queue entries live in parallel arrays indexed by push order (which is also the final tie-break of the ordering),
     * and {@link #heap} is a binary heap of entry indices, so sifting only moves ints.
     */
    private static final class Scratch {

        /** Entry arrays are shrunk back after a search that pushed more than this many entries per graph slot. */
        private static final int SHRINK_FACTOR = 8;

        int[] closed = new int[0];
        List<?>[][] seen = new List<?>[0][];
        int[] targetSlot = new int[0];
        double[] h = new double[0];
        boolean[] touchedFlag = new boolean[0];
        int[] touched = new int[64];
        int touchedCount;

        // queue entries, by push order
        double[] entryF = new double[256];
        double[] entryG = new double[256];
        int[] entryNode = new int[256];
        int[] entryFlags = new int[256];
        RouteLabel[] entryParent = new RouteLabel[256];
        CorridorEdge[] entryVia = new CorridorEdge[256];
        int entryCount;
        int[] heap = new int[256];
        int heapSize;

        void ensure(int capacity) {
            if (closed.length >= capacity) {
                return;
            }
            int n = Math.max(capacity, closed.length * 2);
            closed = new int[n];
            seen = new List<?>[n][];
            targetSlot = new int[n];
            Arrays.fill(targetSlot, -1);
            h = new double[n];
            Arrays.fill(h, Double.NaN);
            touchedFlag = new boolean[n];
            touchedCount = 0;
        }

        void touch(int i) {
            if (!touchedFlag[i]) {
                touchedFlag[i] = true;
                if (touchedCount == touched.length) {
                    touched = Arrays.copyOf(touched, touched.length * 2);
                }
                touched[touchedCount++] = i;
            }
        }

        void push(int node, double g, double f, int flags, RouteLabel parent, CorridorEdge via) {
            int e = entryCount++;
            if (e == entryF.length) {
                int n = e * 2;
                entryF = Arrays.copyOf(entryF, n);
                entryG = Arrays.copyOf(entryG, n);
                entryNode = Arrays.copyOf(entryNode, n);
                entryFlags = Arrays.copyOf(entryFlags, n);
                entryParent = Arrays.copyOf(entryParent, n);
                entryVia = Arrays.copyOf(entryVia, n);
            }
            entryF[e] = f;
            entryG[e] = g;
            entryNode[e] = node;
            entryFlags[e] = flags;
            entryParent[e] = parent;
            entryVia[e] = via;
            if (heapSize == heap.length) {
                heap = Arrays.copyOf(heap, heapSize * 2);
            }
            // sift up
            int i = heapSize++;
            while (i > 0) {
                int p = (i - 1) >>> 1;
                if (!less(e, heap[p])) {
                    break;
                }
                heap[i] = heap[p];
                i = p;
            }
            heap[i] = e;
        }

        /** Removes and returns the entry with the lowest priority, or -1 if the queue is empty. */
        int poll() {
            if (heapSize == 0) {
                return -1;
            }
            int top = heap[0];
            int last = heap[--heapSize];
            if (heapSize > 0) {
                // sift down
                int i = 0;
                int half = heapSize >>> 1;
                while (i < half) {
                    int c = 2 * i + 1;
                    int r = c + 1;
                    if (r < heapSize && less(heap[r], heap[c])) {
                        c = r;
                    }
                    if (!less(heap[c], last)) {
                        break;
                    }
                    heap[i] = heap[c];
                    i = c;
                }
                heap[i] = last;
            }
            return top;
        }

        /** Priority order: {@code f}, then {@code g}, then junction index, then push order. */
        private boolean less(int a, int b) {
            int c = Double.compare(entryF[a], entryF[b]);
            if (c != 0) {
                return c < 0;
            }
            c = Double.compare(entryG[a], entryG[b]);
            if (c != 0) {
                return c < 0;
            }
            if (entryNode[a] != entryNode[b]) {
                return entryNode[a] < entryNode[b];
            }
            return a < b;
        }

        void reset() {
            for (int k = 0; k < touchedCount; k++) {
                int i = touched[k];
                closed[i] = 0;
                seen[i] = null;
                targetSlot[i] = -1;
                h[i] = Double.NaN;
                touchedFlag[i] = false;
            }
            touchedCount = 0;
            // drop the label references so a finished search keeps nothing alive
            Arrays.fill(entryParent, 0, entryCount, null);
            Arrays.fill(entryVia, 0, entryCount, null);
            if (entryF.length > 256 && entryF.length > SHRINK_FACTOR * closed.length) {
                // one pathological search (filters) must not pin a huge queue on this thread forever
                int n = Math.max(256, closed.length);
                entryF = new double[n];
                entryG = new double[n];
                entryNode = new int[n];
                entryFlags = new int[n];
                entryParent = new RouteLabel[n];
                entryVia = new CorridorEdge[n];
                heap = new int[n];
            }
            entryCount = 0;
            heapSize = 0;
        }
    }

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    /**
     * @param graph     the snapshot to search; nothing else is read
     * @param source    start junction
     * @param targets   junctions to settle, or {@code null} for every reachable junction
     * @param heuristic lower bound on the remaining cost between two junctions, or {@code null} for Dijkstra
     */
    public static SearchResult search(NetworkGraph graph, JunctionId source, Set<JunctionId> targets,
            ToDoubleBiFunction<JunctionId, JunctionId> heuristic) {
        Scratch s = SCRATCH.get();
        s.ensure(graph.capacity());
        try {
            return run(graph, source, targets, heuristic, s);
        } finally {
            s.reset();
        }
    }

    private static SearchResult run(NetworkGraph graph, JunctionId source, Set<JunctionId> requestedTargets,
            ToDoubleBiFunction<JunctionId, JunctionId> heuristic, Scratch s) {
        final boolean exhaustive = requestedTargets == null;
        Map<JunctionId, List<RouteLabel>> routes = new HashMap<>();
        List<RouteLabel> settledOrder = new ArrayList<>();
        Set<JunctionId> unreachable = new LinkedHashSet<>();

        JunctionNode sourceNode = graph.node(source);
        if (sourceNode == null) {
            if (!exhaustive) {
                unreachable.addAll(requestedTargets);
            }
            return new SearchResult(graph, source, routes, settledOrder, unreachable, 0, 0);
        }

        List<CorridorEdge> sourceEdges = sourceNode.edges;
        int emitMask = 0;
        for (int i = 0, n = sourceEdges.size(); i < n; i++) {
            emitMask |= sourceEdges.get(i).flags;
        }

        // ---- targets: dedupe, drop the source, pre-filter with the union-find component check
        JunctionId[] targets = new JunctionId[0];
        int[] open = new int[0];
        int[] openCount = new int[RoutingFlags.FLAG_COUNT];
        int remaining = 0;
        if (!exhaustive) {
            List<JunctionId> list = new ArrayList<>(requestedTargets.size());
            for (JunctionId t : requestedTargets) {
                if (t == null || t.equals(source)) {
                    continue;
                }
                if (t.index() < graph.capacity() && s.targetSlot[t.index()] >= 0) {
                    continue; // duplicate
                }
                if (!graph.sameComponent(source, t) || emitMask == 0) {
                    unreachable.add(t);
                    continue;
                }
                s.touch(t.index());
                s.targetSlot[t.index()] = list.size();
                list.add(t);
            }
            targets = list.toArray(new JunctionId[0]);
            open = new int[targets.length];
            Arrays.fill(open, emitMask);
            for (int b = 0; b < RoutingFlags.FLAG_COUNT; b++) {
                if ((emitMask & (1 << b)) != 0) {
                    openCount[b] = targets.length;
                }
            }
            remaining = targets.length;
            if (remaining == 0) {
                return new SearchResult(graph, source, routes, settledOrder, unreachable, 0, 0);
            }
        }
        int openUnion = exhaustive ? RoutingFlags.ALL : emitMask;
        boolean useHeuristic = heuristic != null && !exhaustive && targets.length <= MAX_HEURISTIC_TARGETS;

        // The source never routes to itself through a loop.
        int srcIdx = source.index();
        s.touch(srcIdx);
        s.closed[srcIdx] = RoutingFlags.ALL;

        for (int i = 0, n = sourceEdges.size(); i < n; i++) {
            CorridorEdge e = sourceEdges.get(i);
            if ((e.flags & openUnion) == 0) {
                continue;
            }
            int t = e.to.index();
            if (!graph.isActive(t)) {
                continue;
            }
            double h = useHeuristic ? heuristicAt(graph, s, t, targets, heuristic) : 0;
            s.push(t, e.weight, e.weight + h, e.flags, null, e);
        }

        int settledNodes = 0;
        int polled = 0;
        int cur;
        while ((cur = s.poll()) >= 0) {
            polled++;
            if (!exhaustive && remaining == 0) {
                break;
            }
            int curFlags = s.entryFlags[cur];
            if ((curFlags & openUnion) == 0) {
                continue;
            }
            int idx = s.entryNode[cur];
            s.touch(idx);
            int closed = s.closed[idx];
            int fresh = curFlags & ~closed;
            if (fresh == 0) {
                continue;
            }
            RouteLabel parent = s.entryParent[cur];
            CorridorEdge via = s.entryVia[cur];
            List<?> filters = parent == null ? via.filters : concat(parent.filters, via.filters);
            List<?>[] seen = s.seen[idx];
            if (seen != null && !hasNewInformation(filters, fresh, seen)) {
                continue;
            }

            // ---- accept
            if (closed == 0 && seen == null) {
                settledNodes++;
            }
            JunctionNode node = graph.node(idx);
            double g = s.entryG[cur];
            RouteLabel label = new RouteLabel(
                    source,
                    node.id,
                    g,
                    curFlags,
                    fresh,
                    filters,
                    parent == null ? via.blockDistance : parent.blockDistance + via.blockDistance,
                    parent,
                    via);
            settledOrder.add(label);
            int slot = exhaustive ? -1 : s.targetSlot[idx];
            if (exhaustive || slot >= 0) {
                routes.computeIfAbsent(node.id, k -> new ArrayList<>(2)).add(label);
            }

            // ---- close first, so the expansion below can skip corridors back into flags closed here
            if (filters.isEmpty()) {
                int newClosed = closed | curFlags;
                s.closed[idx] = newClosed;
                if (slot >= 0) {
                    int before = open[slot];
                    int after = before & ~newClosed;
                    if (after != before) {
                        open[slot] = after;
                        for (int b = 0; b < RoutingFlags.FLAG_COUNT; b++) {
                            int bit = 1 << b;
                            if ((before & bit) != 0 && (after & bit) == 0 && --openCount[b] == 0) {
                                openUnion &= ~bit;
                            }
                        }
                        if (after == 0 && --remaining == 0) {
                            break; // every target is settled: nothing this junction could add
                        }
                    }
                }
            } else {
                // Only filtered routes are ever compared against: a filter-free route closes its flags, and closed
                // flags are never checked again.
                if (seen == null) {
                    seen = new List<?>[RoutingFlags.FLAG_COUNT];
                    s.seen[idx] = seen;
                }
                for (int b = 0; b < RoutingFlags.FLAG_COUNT; b++) {
                    if ((fresh & (1 << b)) != 0) {
                        @SuppressWarnings("unchecked")
                        List<List<?>> lists = (List<List<?>>) seen[b];
                        if (lists == null) {
                            lists = new ArrayList<>(1);
                            seen[b] = lists;
                        }
                        lists.add(filters);
                    }
                }
            }

            // ---- expand with every flag the route carries, like the link-state router did
            List<CorridorEdge> edges = node.edges;
            for (int i = 0, n = edges.size(); i < n; i++) {
                CorridorEdge e = edges.get(i);
                int nf = curFlags & e.flags;
                if ((nf & openUnion) == 0) {
                    continue;
                }
                int t = e.to.index();
                // closed flags only grow, so an entry carrying nothing but closed flags would be dropped when polled
                if ((nf & ~s.closed[t]) == 0 || !graph.isActive(t)) {
                    continue;
                }
                double ng = g + e.weight;
                double h = useHeuristic ? heuristicAt(graph, s, t, targets, heuristic) : 0;
                s.push(t, ng, ng + h, nf, label, e);
            }
        }

        if (!exhaustive) {
            for (JunctionId t : targets) {
                if (!routes.containsKey(t)) {
                    unreachable.add(t);
                }
            }
        }
        return new SearchResult(graph, source, routes, settledOrder, unreachable, settledNodes, polled);
    }

    /**
     * The link-state router's rule for routes through filters: a route to an already-visited junction is still useful
     * if, for one of its flags not yet closed there, no earlier route arrived with a subset of its filters.
     */
    private static boolean hasNewInformation(List<?> filters, int fresh, List<?>[] seen) {
        for (int b = 0; b < RoutingFlags.FLAG_COUNT; b++) {
            if ((fresh & (1 << b)) == 0) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<List<?>> lists = (List<List<?>>) seen[b];
            if (lists == null) {
                return true;
            }
            boolean dominated = false;
            for (int i = 0, n = lists.size(); i < n; i++) {
                if (filters.containsAll(lists.get(i))) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated) {
                return true;
            }
        }
        return false;
    }

    private static double heuristicAt(NetworkGraph graph, Scratch s, int node, JunctionId[] targets,
            ToDoubleBiFunction<JunctionId, JunctionId> heuristic) {
        double cached = s.h[node];
        if (!Double.isNaN(cached)) {
            return cached;
        }
        JunctionId id = JunctionId.of(node);
        double best = Double.POSITIVE_INFINITY;
        for (JunctionId t : targets) {
            double v = heuristic.applyAsDouble(id, t);
            if (v < best) {
                best = v;
            }
        }
        if (Double.isInfinite(best) || best < 0) {
            best = 0;
        }
        s.touch(node);
        s.h[node] = best;
        return best;
    }

    private static List<?> concat(List<?> a, List<?> b) {
        if (b.isEmpty()) {
            return a;
        }
        if (a.isEmpty()) {
            return b;
        }
        List<Object> list = new ArrayList<>(a.size() + b.size());
        list.addAll(a);
        list.addAll(b);
        return Collections.unmodifiableList(list);
    }
}
