package logisticspipes.routing.astar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * The single writer of the junction graph.
 * <p>
 * Edits are queued from any thread and applied in order by whoever holds the writer lock: the dedicated mutation thread
 * in asynchronous mode, or the submitting thread in synchronous mode (and in tests). Each drained batch produces
 * exactly one new immutable {@link NetworkGraph} that is published by an atomic reference swap, so readers never block
 * on writers.
 * <p>
 * Nothing is recomputed eagerly here: an edit only replaces the affected edges (bumping their version), keeps the
 * union-find components current and logs what improved (see {@link ImprovementEvent}). Routes are recomputed later, by
 * the first query that finds its cached route stale.
 */
public final class JunctionGraphWriter {

    /** Junctions a detour search may visit before a removal falls back to re-labelling the whole component. */
    static final int DETOUR_SEARCH_BUDGET = 512;

    private interface Mutation {

        void apply(JunctionGraphWriter w);
    }

    private static final class PendingEvent {

        final int node;
        final int kind;
        final int from;
        final int to;
        final double weight;
        final int flags;

        PendingEvent(int node, int kind, int from, int to, double weight, int flags) {
            this.node = node;
            this.kind = kind;
            this.from = from;
            this.to = to;
            this.weight = weight;
            this.flags = flags;
        }
    }

    private final AtomicReference<NetworkGraph> published = new AtomicReference<>(NetworkGraph.EMPTY);
    private final ConcurrentLinkedQueue<Mutation> pending = new ConcurrentLinkedQueue<>();
    private final ReentrantLock writerLock = new ReentrantLock();
    private final ChunkEdgeIndex chunkIndex = new ChunkEdgeIndex();
    private final Object wakeup = new Object();

    private volatile boolean asynchronous;
    private volatile Thread mutationThread;
    private volatile Consumer<PublishEvent> publishListener;

    // ---- writer-owned state; only touched while holding writerLock ----
    private JunctionNode[] nodes = new JunctionNode[64];
    private int[] slotOf = new int[64];
    private int nodeCount;
    private UnionFind unionFind = new UnionFind(64);
    private int nextSlot = 0;
    private final Map<Integer, ComponentInfo> components = new HashMap<>();
    private final Map<Integer, Set<Integer>> incoming = new HashMap<>();
    /** Per junction: min over its corridors of weight / manhattan, kept current so publish is O(junctions). */
    private double[] nodeMinRatio = new double[64];
    private long stampCounter = 1;
    private int edgeSequence = 1;
    private long generation = 0;
    private boolean dirty;
    /** Pairs of junctions that lost a direct connection and may have become disconnected. */
    private final List<int[]> splitChecks = new ArrayList<>();
    private final Set<Integer> changedRoots = new HashSet<>();
    private final List<PendingEvent> pendingEvents = new ArrayList<>();
    private final List<JunctionId> removedInBatch = new ArrayList<>();

    // detour search scratch
    private int[] markA = new int[0];
    private int[] markB = new int[0];
    private int markGeneration = 0;
    private int[] queueA = new int[64];
    private int[] queueB = new int[64];

    // publish scratch
    private int[] denseOfSlot = new int[0];
    private int[] denseGenerationOfSlot = new int[0];
    private int denseGeneration = 0;

    // stats
    private long detourChecks;
    private long fullRelabels;
    private final long[] eventCounts = new long[4];
    /** Junctions whose data is non-null (power providers), for {@link NetworkGraph#dataJunctionsInComponentOf}. */
    private final java.util.TreeSet<Integer> dataJunctions = new java.util.TreeSet<>();

    /** What a publish changed, for cache housekeeping. */
    public static final class PublishEvent {

        public final NetworkGraph graph;
        public final List<JunctionId> removedJunctions;

        PublishEvent(NetworkGraph graph, List<JunctionId> removedJunctions) {
            this.graph = graph;
            this.removedJunctions = removedJunctions;
        }
    }

    public NetworkGraph graph() {
        return published.get();
    }

    public ChunkEdgeIndex chunkIndex() {
        return chunkIndex;
    }

    public void setPublishListener(Consumer<PublishEvent> listener) {
        publishListener = listener;
    }

    public int pendingMutations() {
        return pending.size();
    }

    /** Removals that were proven harmless by a local detour search. */
    public long detourChecks() {
        return detourChecks;
    }

    /** Removals that needed a BFS over the whole component. */
    /** Logged improvements by kind: corridor, junction, data, everything (see {@link ImprovementEvent}). */
    public long[] improvementCounts() {
        return eventCounts.clone();
    }

    /** Zero the edit statistics; the graph itself is not touched. */
    public void resetStats() {
        writerLock.lock();
        try {
            detourChecks = 0;
            fullRelabels = 0;
            Arrays.fill(eventCounts, 0);
        } finally {
            writerLock.unlock();
        }
    }

    public long fullRelabels() {
        return fullRelabels;
    }

    /**
     * Switch to asynchronous mode: edits are applied by a dedicated mutation thread created by {@code threadFactory}.
     */
    public synchronized void startAsync(java.util.concurrent.ThreadFactory threadFactory) {
        if (mutationThread != null) {
            return;
        }
        asynchronous = true;
        Thread t = threadFactory.newThread(this::mutationLoop);
        t.setDaemon(true);
        mutationThread = t;
        t.start();
    }

    public synchronized void stopAsync() {
        asynchronous = false;
        Thread t = mutationThread;
        mutationThread = null;
        if (t != null) {
            t.interrupt();
        }
        flush();
    }

    private void mutationLoop() {
        while (asynchronous && !Thread.currentThread().isInterrupted()) {
            synchronized (wakeup) {
                if (pending.isEmpty()) {
                    try {
                        wakeup.wait(50);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
            try {
                flush();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    private void submit(Mutation m) {
        pending.add(m);
        if (asynchronous) {
            synchronized (wakeup) {
                wakeup.notifyAll();
            }
        } else {
            flush();
        }
    }

    /** Apply every queued edit on the calling thread and publish one snapshot for the batch. */
    public void flush() {
        writerLock.lock();
        PublishEvent event = null;
        try {
            Mutation m;
            while ((m = pending.poll()) != null) {
                m.apply(this);
            }
            if (dirty) {
                event = publish();
            }
        } finally {
            writerLock.unlock();
        }
        Consumer<PublishEvent> listener = publishListener;
        if (event != null && listener != null) {
            listener.accept(event);
        }
    }

    /** Drop everything (server stop). */
    public void clear() {
        writerLock.lock();
        try {
            pending.clear();
            nodes = new JunctionNode[64];
            slotOf = new int[64];
            nodeMinRatio = new double[64];
            nodeCount = 0;
            unionFind = new UnionFind(64);
            nextSlot = 0;
            components.clear();
            incoming.clear();
            chunkIndex.clear();
            splitChecks.clear();
            changedRoots.clear();
            pendingEvents.clear();
            removedInBatch.clear();
            dataJunctions.clear();
            dirty = false;
            generation++;
            published.set(NetworkGraph.EMPTY);
        } finally {
            writerLock.unlock();
        }
    }

    // ------------------------------------------------------------------ public edit API

    public void addJunction(JunctionId id, int dimension, int x, int y, int z, Object payload, boolean active) {
        submit(w -> w.doAddJunction(id, dimension, x, y, z, payload, active));
    }

    public void removeJunction(JunctionId id) {
        submit(w -> w.doRemoveJunction(id));
    }

    /** Replace every corridor leaving {@code id} with {@code specs}; unchanged corridors keep id and version. */
    public void setEdges(JunctionId id, List<EdgeSpec> specs) {
        List<EdgeSpec> copy = new ArrayList<>(specs);
        submit(w -> w.doSetEdges(id, copy));
    }

    public void setActive(JunctionId id, boolean active) {
        submit(w -> w.doSetActive(id, active));
    }

    /**
     * Replace the junction's opaque data. {@code improvement} says whether the new data could give some whole-network
     * view a better answer (for example a new power provider); pair routes never depend on junction data.
     */
    public void setData(JunctionId id, Object data, boolean improvement) {
        submit(w -> w.doSetData(id, data, improvement));
    }

    /** Invalidate every cached route in the junction's component (manual network refresh). */
    public void invalidateComponent(JunctionId id) {
        submit(w -> w.doInvalidate(id));
    }

    /**
     * Graph surgery for a junction placed in the middle of a corridor: the corridor {@code a->b} (and {@code b->a} if
     * present) is replaced by {@code a->n->b}. {@code weightFromA} of the corridor goes to the first half, the rest to
     * the second, so the total is preserved. Flags carry over; filters stay on the half leaving the original source.
     */
    public void splitCorridor(JunctionId a, JunctionId b, JunctionId n, double weightFromA) {
        submit(w -> w.doSplit(a, b, n, weightFromA));
    }

    /**
     * Graph surgery for a pass-through junction of degree 2 being removed: {@code a->n->b} becomes {@code a->b} with
     * the summed weight (in both directions where they exist), and {@code n} is removed.
     */
    public void mergeThrough(JunctionId n) {
        submit(w -> w.doMerge(n));
    }

    // ------------------------------------------------------------------ bookkeeping helpers

    private void ensureCapacity(int index) {
        if (index < nodes.length) {
            return;
        }
        int newLength = Math.max(index + 1, nodes.length + (nodes.length >> 1));
        nodes = Arrays.copyOf(nodes, newLength);
        int oldLength = slotOf.length;
        slotOf = Arrays.copyOf(slotOf, newLength);
        Arrays.fill(slotOf, oldLength, newLength, -1);
        nodeMinRatio = Arrays.copyOf(nodeMinRatio, newLength);
        Arrays.fill(nodeMinRatio, oldLength, newLength, Double.POSITIVE_INFINITY);
    }

    private JunctionNode get(JunctionId id) {
        int i = id.index();
        return i < nodes.length ? nodes[i] : null;
    }

    private boolean alive(int index) {
        return index >= 0 && index < nodes.length && nodes[index] != null;
    }

    private int newSlot() {
        int slot = nextSlot++;
        unionFind.ensureCapacity(nextSlot);
        return slot;
    }

    private int root(int index) {
        return unionFind.find(slotOf[index]);
    }

    private void markChanged(int index) {
        dirty = true;
        changedRoots.add(root(index));
    }

    private void recordEvent(int node, int kind, int from, int to, double weight, int flags) {
        dirty = true;
        eventCounts[kind]++;
        pendingEvents.add(new PendingEvent(node, kind, from, to, weight, flags));
    }

    private void union(int a, int b) {
        if (!alive(a) || !alive(b)) {
            return;
        }
        int ra = root(a);
        int rb = root(b);
        int survivor = unionFind.union(slotOf[a], slotOf[b]);
        if (survivor >= 0) {
            // The larger network keeps its root, stamps and improvement log: every route that could now use the
            // smaller one goes over a corridor that was just added and logged. The smaller one's cached routes are
            // invalidated by the root change.
            int loser = survivor == ra ? rb : ra;
            components.remove(loser);
            changedRoots.remove(loser);
            changedRoots.add(survivor);
            dirty = true;
        }
    }

    private void addIncoming(JunctionId to, JunctionId from) {
        incoming.computeIfAbsent(to.index(), k -> new HashSet<>()).add(from.index());
    }

    private void removeIncoming(JunctionId to, JunctionId from) {
        Set<Integer> set = incoming.get(to.index());
        if (set != null) {
            set.remove(from.index());
            if (set.isEmpty()) {
                incoming.remove(to.index());
            }
        }
    }

    /**
     * Lowest cost per block over the junction's corridors (0 for a corridor to another dimension). The component-wide
     * minimum scales the Manhattan heuristic, see {@link NetworkGraph#manhattanHeuristic()}.
     */
    private void recomputeMinRatio(int i) {
        JunctionNode node = nodes[i];
        double min = Double.POSITIVE_INFINITY;
        if (node != null) {
            for (CorridorEdge e : node.edges) {
                int t = e.to.index();
                JunctionNode target = t < nodes.length ? nodes[t] : null;
                if (target == null) {
                    continue; // re-evaluated when the target junction is added
                }
                double ratio;
                if (target.dimension != node.dimension) {
                    ratio = 0;
                } else {
                    int m = NetworkGraph.manhattan(node, target);
                    ratio = m == 0 ? Double.POSITIVE_INFINITY : e.weight / m;
                }
                min = Math.min(min, ratio);
            }
        }
        nodeMinRatio[i] = min;
    }

    // ------------------------------------------------------------------ mutation implementations

    private void doAddJunction(JunctionId id, int dimension, int x, int y, int z, Object payload, boolean active) {
        int i = id.index();
        ensureCapacity(i);
        if (nodes[i] != null) {
            doRemoveJunction(id);
        }
        nodes[i] = new JunctionNode(id, dimension, x, y, z, Collections.emptyList(), true, active, payload, null, 0);
        nodeCount++;
        int slot = newSlot();
        slotOf[i] = slot;
        long stamp = ++stampCounter;
        components.put(slot, ComponentInfo.fresh(stamp));
        nodeMinRatio[i] = Double.POSITIVE_INFINITY;
        dirty = true;
        // Corridors other junctions already reported towards this position now lead somewhere.
        Set<Integer> in = incoming.get(i);
        if (in != null) {
            for (int from : in) {
                union(from, i);
                recomputeMinRatio(from);
            }
        }
        markChanged(i);
        recordEvent(i, ImprovementEvent.NODE, i, i, 0, RoutingFlags.ALL);
    }

    private void doRemoveJunction(JunctionId id) {
        int i = id.index();
        JunctionNode node = get(id);
        if (node == null) {
            return;
        }
        markChanged(i);
        // every former neighbour must still be connected to the others
        Set<Integer> neighbours = new LinkedHashSet<>();
        for (CorridorEdge e : node.edges) {
            chunkIndex.remove(e);
            removeIncoming(e.to, id);
            if (alive(e.to.index())) {
                neighbours.add(e.to.index());
            }
        }
        Set<Integer> in = incoming.remove(i);
        if (in != null) {
            for (int from : in) {
                if (alive(from)) {
                    neighbours.add(from);
                }
            }
        }
        neighbours.remove(i);
        nodes[i] = null;
        dataJunctions.remove(i);
        nodeCount--;
        // The pipe is gone, so corridors other junctions reported into it are gone too. Drop them now instead of
        // waiting for those junctions to re-scan; otherwise a re-used id would inherit them.
        if (in != null) {
            for (int from : in) {
                JunctionNode owner = nodes[from];
                if (owner == null) {
                    continue;
                }
                List<EdgeSpec> kept = new ArrayList<>(owner.edges.size());
                for (CorridorEdge e : owner.edges) {
                    if (!e.to.equals(id)) {
                        kept.add(e.toSpec());
                    }
                }
                doSetEdges(owner.id, kept);
            }
        }
        Integer anchor = null;
        for (int n : neighbours) {
            if (anchor == null) {
                anchor = n;
            } else {
                splitChecks.add(new int[] { anchor, n });
            }
        }
        // the slot stays in union-find chains but is never handed out again
        slotOf[i] = -1;
        nodeMinRatio[i] = Double.POSITIVE_INFINITY;
        removedInBatch.add(id);
    }

    private CorridorEdge newEdge(JunctionId from, EdgeSpec spec, long version) {
        return new CorridorEdge(CorridorEdge.makeId(from, edgeSequence++), from, spec, version);
    }

    private void doSetEdges(JunctionId id, List<EdgeSpec> specs) {
        JunctionNode node = get(id);
        if (node == null) {
            return;
        }
        int i = id.index();
        Map<JunctionId, CorridorEdge> oldByTarget = new HashMap<>();
        for (CorridorEdge e : node.edges) {
            oldByTarget.put(e.to, e);
        }
        List<CorridorEdge> newEdges = new ArrayList<>(specs.size());
        Set<JunctionId> seen = new HashSet<>();
        boolean changed = false;
        for (EdgeSpec spec : specs) {
            if (spec.to.equals(id) || !seen.add(spec.to)) {
                continue;
            }
            CorridorEdge old = oldByTarget.remove(spec.to);
            CorridorEdge edge;
            if (old == null) {
                edge = newEdge(id, spec, 1);
                chunkIndex.add(edge);
                addIncoming(spec.to, id);
                changed = true;
                recordEvent(i, ImprovementEvent.EDGE, i, spec.to.index(), spec.weight, spec.flags);
            } else if (spec.sameContent(old)) {
                edge = old;
            } else {
                edge = new CorridorEdge(old.id, id, spec, old.version + 1);
                chunkIndex.replace(old, edge);
                changed = true;
                if (spec.improvesOn(old)) {
                    // a cheaper corridor or new filters can help any flag it carries; a gained flag only that flag
                    int helped = spec.weight < old.weight || !spec.filters.equals(old.filters) ? spec.flags
                            : spec.flags & ~old.flags;
                    recordEvent(i, ImprovementEvent.EDGE, i, spec.to.index(), spec.weight, helped);
                }
            }
            newEdges.add(edge);
        }
        for (CorridorEdge removed : oldByTarget.values()) {
            chunkIndex.remove(removed);
            removeIncoming(removed.to, id);
            splitChecks.add(new int[] { i, removed.to.index() });
            changed = true;
        }
        if (!changed) {
            return;
        }
        nodes[i] = node.withEdges(newEdges, node.nextEdgeSequence);
        recomputeMinRatio(i);
        for (CorridorEdge e : newEdges) {
            union(i, e.to.index());
        }
        markChanged(i);
    }

    private void doSetActive(JunctionId id, boolean active) {
        JunctionNode node = get(id);
        if (node == null || node.active == active) {
            return;
        }
        int i = id.index();
        nodes[i] = node.withActive(active);
        markChanged(i);
        if (active) {
            recordEvent(i, ImprovementEvent.NODE, i, i, 0, RoutingFlags.ALL);
        }
    }

    private void doSetData(JunctionId id, Object data, boolean improvement) {
        JunctionNode node = get(id);
        if (node == null) {
            return;
        }
        int i = id.index();
        nodes[i] = node.withData(data);
        if (data != null) {
            dataJunctions.add(i);
        } else {
            dataJunctions.remove(i);
        }
        markChanged(i);
        if (improvement) {
            recordEvent(i, ImprovementEvent.DATA, i, i, 0, RoutingFlags.ALL);
        }
    }

    private void doInvalidate(JunctionId id) {
        if (get(id) != null) {
            markChanged(id.index());
            recordEvent(id.index(), ImprovementEvent.ALL, -1, -1, 0, RoutingFlags.ALL);
        }
    }

    private void doSplit(JunctionId a, JunctionId b, JunctionId n, double weightFromA) {
        JunctionNode na = get(a);
        JunctionNode nb = get(b);
        JunctionNode nn = get(n);
        if (na == null || nb == null || nn == null) {
            return;
        }
        CorridorEdge ab = na.edgeTo(b);
        CorridorEdge ba = nb.edgeTo(a);
        List<EdgeSpec> nEdges = new ArrayList<>();
        for (CorridorEdge e : nn.edges) {
            if (!e.to.equals(a) && !e.to.equals(b)) {
                nEdges.add(e.toSpec());
            }
        }
        // Sides at the new junction are unknown to pure graph surgery (6 = ForgeDirection.UNKNOWN); a corridor scan of
        // the new junction replaces these edges with exact data.
        if (ab != null) {
            double first = Math.min(ab.weight, Math.max(0, weightFromA));
            replaceEdgeTarget(na, ab, n, first);
            nEdges.add(halfSpec(ab, b, ab.weight - first, 6, ab.insertSide, Collections.emptyList()));
        }
        if (ba != null) {
            double first = Math.min(ba.weight, Math.max(0, ba.weight - weightFromA));
            replaceEdgeTarget(nb, ba, n, first);
            nEdges.add(halfSpec(ba, a, ba.weight - first, 6, ba.insertSide, Collections.emptyList()));
        }
        doSetEdges(n, nEdges);
    }

    private static EdgeSpec halfSpec(CorridorEdge e, JunctionId to, double weight, int exitSide, int insertSide,
            List<?> filters) {
        int blocks = e.weight > 0 ? (int) Math.round(e.blockDistance * (weight / e.weight)) : 0;
        return new EdgeSpec(to, weight, e.flags, filters, blocks, exitSide, insertSide, e.chunks);
    }

    private void replaceEdgeTarget(JunctionNode owner, CorridorEdge edge, JunctionId newTarget, double weight) {
        List<EdgeSpec> specs = new ArrayList<>();
        for (CorridorEdge e : owner.edges) {
            if (e == edge) {
                specs.add(halfSpec(e, newTarget, weight, e.exitSide, 6, e.filters));
            } else {
                specs.add(e.toSpec());
            }
        }
        doSetEdges(owner.id, specs);
    }

    private void doMerge(JunctionId n) {
        JunctionNode nn = get(n);
        if (nn == null) {
            return;
        }
        Set<Integer> in = incoming.getOrDefault(n.index(), Collections.emptySet());
        Set<JunctionId> neighbours = new HashSet<>();
        for (CorridorEdge e : nn.edges) {
            neighbours.add(e.to);
        }
        for (int from : in) {
            neighbours.add(JunctionId.of(from));
        }
        if (neighbours.size() != 2) {
            return; // only a degree-2 pass-through junction can be merged away
        }
        JunctionId[] ends = neighbours.toArray(new JunctionId[2]);
        mergeDirection(ends[0], n, ends[1]);
        mergeDirection(ends[1], n, ends[0]);
        doRemoveJunction(n);
    }

    private void mergeDirection(JunctionId a, JunctionId n, JunctionId b) {
        JunctionNode na = get(a);
        JunctionNode nn = get(n);
        if (na == null || nn == null) {
            return;
        }
        CorridorEdge an = na.edgeTo(n);
        CorridorEdge nb = nn.edgeTo(b);
        if (an == null || nb == null) {
            return;
        }
        List<Object> filters = new ArrayList<>(an.filters);
        filters.addAll(nb.filters);
        long[] chunks = concatChunks(an.chunks, nb.chunks);
        byte[] travelPath = null;
        if (an.travelPath != null && nb.travelPath != null) {
            travelPath = Arrays.copyOf(an.travelPath, an.travelPath.length + nb.travelPath.length);
            System.arraycopy(nb.travelPath, 0, travelPath, an.travelPath.length, nb.travelPath.length);
        }
        EdgeSpec merged = new EdgeSpec(
                b,
                an.weight + nb.weight,
                an.flags & nb.flags,
                filters,
                an.blockDistance + nb.blockDistance,
                an.exitSide,
                nb.insertSide,
                chunks,
                travelPath);
        CorridorEdge direct = na.edgeTo(b);
        boolean keepDirect = direct != null && direct.weight <= merged.weight;
        List<EdgeSpec> specs = new ArrayList<>();
        for (CorridorEdge e : na.edges) {
            if (e == an) {
                if (!keepDirect) {
                    specs.add(merged);
                }
            } else if (e != direct || keepDirect) {
                specs.add(e.toSpec());
            }
        }
        doSetEdges(a, specs);
    }

    private static long[] concatChunks(long[] a, long[] b) {
        long[] all = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, all, a.length, b.length);
        return Arrays.stream(all).distinct().toArray();
    }

    // ------------------------------------------------------------------ publish

    private PublishEvent publish() {
        resolveSplits();

        for (PendingEvent p : pendingEvents) {
            if (!alive(p.node)) {
                continue;
            }
            int r = root(p.node);
            ComponentInfo info = components.get(r);
            long stamp = ++stampCounter;
            ImprovementEvent head = new ImprovementEvent(
                    stamp,
                    p.kind,
                    p.from,
                    p.to,
                    p.weight,
                    p.flags,
                    info == null ? null : info.log);
            if (head.depth > ImprovementEvent.MAX_DEPTH) {
                head = ImprovementEvent.truncate(head);
            }
            components.put(r, info == null ? new ComponentInfo(stamp, stamp, head, 1.0) : info.withLog(head));
            changedRoots.add(r);
        }
        pendingEvents.clear();
        for (int root : changedRoots) {
            int r = unionFind.find(root);
            ComponentInfo info = components.get(r);
            long stamp = ++stampCounter;
            components.put(r, info == null ? ComponentInfo.fresh(stamp) : info.withChange(stamp));
        }
        changedRoots.clear();

        // Dense per-publish numbering of the live roots, so the per-junction work below stays on primitive arrays.
        int length = nodes.length;
        int[] componentOf = new int[length];
        if (denseOfSlot.length < nextSlot) {
            denseOfSlot = new int[Math.max(nextSlot, denseOfSlot.length * 2)];
            denseGenerationOfSlot = new int[denseOfSlot.length];
        }
        int gen = ++denseGeneration;
        int denseCount = 0;
        int[] denseRoots = new int[16];
        double[] denseMinRatio = new double[16];
        int[] denseOfNode = new int[length];
        for (int i = 0; i < length; i++) {
            if (nodes[i] == null) {
                componentOf[i] = -1;
                continue;
            }
            int r = root(i);
            componentOf[i] = r;
            int d;
            if (denseGenerationOfSlot[r] == gen) {
                d = denseOfSlot[r];
            } else {
                d = denseCount++;
                if (d == denseRoots.length) {
                    denseRoots = Arrays.copyOf(denseRoots, d * 2);
                    denseMinRatio = Arrays.copyOf(denseMinRatio, d * 2);
                }
                denseRoots[d] = r;
                denseMinRatio[d] = Double.POSITIVE_INFINITY;
                denseOfSlot[r] = d;
                denseGenerationOfSlot[r] = gen;
            }
            denseOfNode[i] = d;
            if (nodeMinRatio[i] < denseMinRatio[d]) {
                denseMinRatio[d] = nodeMinRatio[i];
            }
        }
        Map<Integer, ComponentInfo> snapshotComponents = new HashMap<>(denseCount * 2);
        double[] denseScale = new double[denseCount];
        for (int d = 0; d < denseCount; d++) {
            double ratio = denseMinRatio[d];
            double scale = Double.isInfinite(ratio) ? 1.0 : Math.max(0, ratio);
            denseScale[d] = scale;
            ComponentInfo info = components.get(denseRoots[d]);
            if (info == null) {
                info = ComponentInfo.fresh(++stampCounter).withScale(scale);
                components.put(denseRoots[d], info);
            } else if (info.heuristicScale != scale) {
                info = info.withScale(scale);
                components.put(denseRoots[d], info);
            }
            snapshotComponents.put(denseRoots[d], info);
        }
        // forget components that have no junctions left
        if (components.size() != snapshotComponents.size()) {
            components.keySet().retainAll(snapshotComponents.keySet());
        }
        double[] nodeScale = new double[length];
        for (int i = 0; i < length; i++) {
            if (componentOf[i] >= 0) {
                nodeScale[i] = denseScale[denseOfNode[i]];
            }
        }

        NetworkGraph graph = new NetworkGraph(
                ++generation,
                Arrays.copyOf(nodes, length),
                nodeCount,
                componentOf,
                nodeScale,
                Collections.unmodifiableMap(snapshotComponents),
                chunkIndex,
                dataJunctions.stream().mapToInt(Integer::intValue).toArray());
        published.set(graph);
        dirty = false;
        List<JunctionId> removed = new ArrayList<>(removedInBatch);
        removedInBatch.clear();
        return new PublishEvent(graph, removed);
    }

    /**
     * Union-find cannot split, so every pair of junctions that lost a direct connection is checked: still directly
     * connected the other way, or a detour found within {@link #DETOUR_SEARCH_BUDGET} junctions, means the component is
     * intact and nothing needs to happen. Only when no detour is found is the component re-labelled by a full BFS.
     */
    private void resolveSplits() {
        if (splitChecks.isEmpty()) {
            return;
        }
        Set<Integer> relabelStarts = new LinkedHashSet<>();
        for (int[] pair : splitChecks) {
            int u = pair[0];
            int v = pair[1];
            if (!alive(u) || !alive(v) || root(u) != root(v)) {
                continue;
            }
            if (nodes[u].edgeTo(nodes[v].id) != null || nodes[v].edgeTo(nodes[u].id) != null) {
                continue;
            }
            if (connectedWithin(u, v, DETOUR_SEARCH_BUDGET)) {
                detourChecks++;
                continue;
            }
            relabelStarts.add(u);
            relabelStarts.add(v);
        }
        splitChecks.clear();
        if (!relabelStarts.isEmpty()) {
            fullRelabels++;
            relabel(relabelStarts);
        }
    }

    private void forEachNeighbour(int cur, java.util.function.IntConsumer action) {
        for (CorridorEdge e : nodes[cur].edges) {
            int t = e.to.index();
            if (alive(t)) {
                action.accept(t);
            }
        }
        Set<Integer> in = incoming.get(cur);
        if (in != null) {
            for (int t : in) {
                if (alive(t)) {
                    action.accept(t);
                }
            }
        }
    }

    /**
     * Bidirectional BFS over corridors in either direction. True if {@code u} and {@code v} meet within {@code budget}
     * visited junctions; false if they cannot (one side ran out) or the budget ran out.
     */
    private boolean connectedWithin(int u, int v, int budget) {
        if (markA.length < nodes.length) {
            markA = new int[nodes.length];
            markB = new int[nodes.length];
            markGeneration = 0;
        }
        if (++markGeneration == Integer.MAX_VALUE) {
            Arrays.fill(markA, 0);
            Arrays.fill(markB, 0);
            markGeneration = 1;
        }
        final int gen = markGeneration;
        int[] headA = { 0 };
        int[] tailA = { 0 };
        int[] headB = { 0 };
        int[] tailB = { 0 };
        queueA = push(queueA, tailA, u);
        queueB = push(queueB, tailB, v);
        markA[u] = gen;
        markB[v] = gen;
        int visited = 2;
        boolean[] met = { false };
        while (headA[0] < tailA[0] && headB[0] < tailB[0]) {
            if (visited > budget) {
                return false;
            }
            boolean expandA = tailA[0] - headA[0] <= tailB[0] - headB[0];
            if (expandA) {
                int cur = queueA[headA[0]++];
                int[] added = { 0 };
                forEachNeighbour(cur, t -> {
                    if (markB[t] == gen) {
                        met[0] = true;
                    } else if (markA[t] != gen) {
                        markA[t] = gen;
                        queueA = push(queueA, tailA, t);
                        added[0]++;
                    }
                });
                visited += added[0];
            } else {
                int cur = queueB[headB[0]++];
                int[] added = { 0 };
                forEachNeighbour(cur, t -> {
                    if (markA[t] == gen) {
                        met[0] = true;
                    } else if (markB[t] != gen) {
                        markB[t] = gen;
                        queueB = push(queueB, tailB, t);
                        added[0]++;
                    }
                });
                visited += added[0];
            }
            if (met[0]) {
                return true;
            }
        }
        return false;
    }

    private static int[] push(int[] queue, int[] tail, int value) {
        if (tail[0] == queue.length) {
            queue = Arrays.copyOf(queue, queue.length * 2);
        }
        queue[tail[0]++] = value;
        return queue;
    }

    /**
     * Re-label the components containing {@code starts} by BFS. Of the parts an old component falls into, the largest
     * keeps the old root with its stamps and improvement log (a removal never makes an untouched route worse); every
     * other part gets a fresh root, so routes cached for its junctions are invalidated.
     */
    private void relabel(Set<Integer> starts) {
        boolean[] visited = new boolean[nodes.length];
        Map<Integer, List<int[]>> partsByOldRoot = new HashMap<>();
        for (int start : starts) {
            if (!alive(start) || visited[start]) {
                continue;
            }
            int oldRoot = root(start);
            int[] slots = new int[16];
            int count = 0;
            java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
            queue.add(start);
            visited[start] = true;
            while (!queue.isEmpty()) {
                int cur = queue.poll();
                if (count == slots.length) {
                    slots = Arrays.copyOf(slots, slots.length * 2);
                }
                slots[count++] = slotOf[cur];
                forEachNeighbour(cur, t -> {
                    if (!visited[t]) {
                        visited[t] = true;
                        queue.add(t);
                    }
                });
            }
            partsByOldRoot.computeIfAbsent(oldRoot, k -> new ArrayList<>()).add(Arrays.copyOf(slots, count));
        }
        for (Map.Entry<Integer, List<int[]>> entry : partsByOldRoot.entrySet()) {
            int oldRoot = entry.getKey();
            List<int[]> parts = entry.getValue();
            int largest = 0;
            for (int p = 1; p < parts.size(); p++) {
                if (parts.get(p).length > parts.get(largest).length) {
                    largest = p;
                }
            }
            // Every part gets a fresh slot as root (the old root's slot may sit in any part, or be dead); the largest
            // part inherits the component's identity, stamps and log.
            ComponentInfo oldInfo = components.remove(oldRoot);
            changedRoots.remove(oldRoot);
            for (int p = 0; p < parts.size(); p++) {
                int[] slots = parts.get(p);
                int newRoot = newSlot();
                if (p == largest && oldInfo != null) {
                    components.put(newRoot, oldInfo);
                    changedRoots.add(newRoot);
                } else {
                    components.put(newRoot, ComponentInfo.fresh(++stampCounter));
                }
                unionFind.relabel(newRoot, slots, slots.length);
            }
        }
    }
}
