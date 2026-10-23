package logisticspipes.crafting.v3.plan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.github.bsideup.jabel.Desugar;

import logisticspipes.crafting.v3.model.InputSpec;
import logisticspipes.crafting.v3.model.Pattern;
import logisticspipes.crafting.v3.model.ResourceKey;
import logisticspipes.crafting.v3.model.ResourceMatcher;

/**
 * Turns a request into a {@link Plan} (plan section 3). Pure logic over immutable inputs, so it can run off the server
 * thread. One instance plans one request.
 * <p>
 * Phase A picks one pattern per matcher, refusing patterns that would loop back to a matcher on the current path. Phase
 * B walks the resulting matcher/pattern DAG root first: a matcher is resolved once every consumer of it is known
 * (surplus, then providers most-constrained-first, then a craft), and a pattern node fixes its craft count and crafter
 * once all demand on it is known, then pushes demand to its inputs.
 */
public final class CraftingPlanner {

    public static final int DEFAULT_NODE_CAP = 4096;
    public static final long DEFAULT_TIME_BUDGET_MS = 5000;

    private final PatternRegistry registry;
    private final StockView stock;
    private final RouteOracle routes;
    private final int nodeCap;
    private final long deadline;

    // Phase A
    private final Map<ResourceMatcher, MatcherState> matchers = new LinkedHashMap<>();
    private final Map<String, NodeState> nodesByPattern = new HashMap<>();
    private final List<NodeState> nodeList = new ArrayList<>();
    private int expansions;
    private boolean craftOnly;

    // Phase B
    private final Map<StockSlot, Long> stockClaimed = new HashMap<>();
    private final List<NodeState> surplusProducers = new ArrayList<>();
    private final List<PlanIssue> issues = new ArrayList<>();
    private final Map<ResourceMatcher, List<StockView.Entry>> stockCache = new HashMap<>();

    public CraftingPlanner(PatternRegistry registry, StockView stock, RouteOracle routes) {
        this(registry, stock, routes, DEFAULT_NODE_CAP, DEFAULT_TIME_BUDGET_MS);
    }

    public CraftingPlanner(PatternRegistry registry, StockView stock, RouteOracle routes, int nodeCap,
            long timeBudgetMs) {
        this.registry = registry;
        this.stock = stock;
        this.routes = routes;
        this.nodeCap = nodeCap;
        this.deadline = System.nanoTime() + timeBudgetMs * 1_000_000L;
    }

    public Plan plan(PlanRequest request) {
        try {
            return planUnchecked(request);
        } catch (PlanningException e) {
            return Plan.failed(request, e.getMessage());
        }
    }

    private Plan planUnchecked(PlanRequest request) {
        if (request.qty() <= 0) throw new PlanningException("Nothing requested");
        craftOnly = request.craftOnly();
        MatcherState root = expand(request.target(), new LinkedHashSet<>());

        List<Object> order = topologicalOrder(root);
        computeStructuralHeights(order);

        InputSpec rootSpec = new InputSpec(request.target(), request.qty(), true);
        InputResolution rootResolution = new InputResolution(rootSpec, request.qty());
        root.demands.add(new Demand(null, request.requester(), request.qty(), rootResolution));

        for (Object vertex : order) {
            checkBudget();
            if (vertex instanceof MatcherState) {
                resolveMatcher((MatcherState) vertex);
            } else {
                processNode((NodeState) vertex);
            }
        }

        Map<Integer, PlanNode> nodes = new LinkedHashMap<>();
        for (Object vertex : order) {
            if (vertex instanceof NodeState) {
                NodeState n = (NodeState) vertex;
                if (n.out != null && n.out.crafts > 0) nodes.put(n.out.id, n.out);
            }
        }
        computeHeights(nodes);

        Map<ResourceKey, Long> raw = new LinkedHashMap<>();
        addRaw(raw, rootResolution);
        for (PlanNode n : nodes.values()) {
            for (InputResolution res : n.inputs) addRaw(raw, res);
        }
        return new Plan(request, rootResolution, nodes, issues, raw, null);
    }

    // ---------------------------------------------------------------- Phase A: structural expansion

    private MatcherState expand(ResourceMatcher matcher, Set<ResourceMatcher> path) {
        MatcherState state = matchers.get(matcher);
        if (state != null && state.expanded) return state;
        if (state == null) {
            state = new MatcherState(matcher);
            matchers.put(matcher, state);
        }
        checkBudget();
        path.add(matcher);
        for (PatternRegistry.Candidate candidate : registry.candidatesFor(matcher)) {
            if (candidate.crafters().isEmpty()) continue;
            if (++expansions > nodeCap * 8) throw new PlanningException("Request too complex (too many recipes)");
            NodeState existing = nodesByPattern.get(candidate.pattern().id());
            if (existing != null) {
                if (Collections.disjoint(existing.closure, path)) {
                    state.chosen = existing;
                    break;
                }
                state.cycleRejected = true;
                continue;
            }
            Set<ResourceMatcher> closure = tryPattern(candidate.pattern(), path);
            if (closure == null) {
                state.cycleRejected = true;
                continue;
            }
            if (nodeList.size() >= nodeCap)
                throw new PlanningException("Request too complex (more than " + nodeCap + " crafting steps)");
            NodeState node = new NodeState(nodeList.size(), candidate, closure);
            nodeList.add(node);
            nodesByPattern.put(candidate.pattern().id(), node);
            state.chosen = node;
            break;
        }
        path.remove(matcher);
        state.expanded = true;
        return state;
    }

    /** Expands the pattern's inputs; returns its closure, or null when it loops back onto {@code path}. */
    private Set<ResourceMatcher> tryPattern(Pattern pattern, Set<ResourceMatcher> path) {
        Set<ResourceMatcher> closure = new HashSet<>();
        for (InputSpec input : pattern.inputs()) {
            ResourceMatcher im = input.matcher();
            if (path.contains(im)) return null;
            MatcherState child = expand(im, path);
            closure.add(im);
            if (child.chosen != null) {
                if (!Collections.disjoint(child.chosen.closure, path)) return null;
                closure.addAll(child.chosen.closure);
            }
        }
        return closure;
    }

    /** Root-first order of the matcher/pattern DAG reachable from {@code root}. */
    private List<Object> topologicalOrder(MatcherState root) {
        List<Object> post = new ArrayList<>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Object[]> stack = new ArrayDeque<>();
        stack.push(new Object[] { root, null });
        while (!stack.isEmpty()) {
            Object[] frame = stack.peek();
            Object vertex = frame[0];
            if (frame[1] == null) {
                if (!seen.add(vertex)) {
                    stack.pop();
                    continue;
                }
                frame[1] = successors(vertex).iterator();
            }
            @SuppressWarnings("unchecked")
            java.util.Iterator<Object> it = (java.util.Iterator<Object>) frame[1];
            if (it.hasNext()) {
                Object next = it.next();
                if (!seen.contains(next)) stack.push(new Object[] { next, null });
            } else {
                stack.pop();
                post.add(vertex);
            }
        }
        Collections.reverse(post);
        return post;
    }

    private List<Object> successors(Object vertex) {
        if (vertex instanceof MatcherState) {
            NodeState chosen = ((MatcherState) vertex).chosen;
            return chosen == null ? Collections.emptyList() : Collections.singletonList(chosen);
        }
        List<Object> result = new ArrayList<>();
        for (InputSpec input : ((NodeState) vertex).candidate.pattern().inputs()) {
            result.add(matchers.get(input.matcher()));
        }
        return result;
    }

    /** Structural height: longest path to a leaf; used to order competing consumers before real heights exist. */
    private void computeStructuralHeights(List<Object> rootFirst) {
        for (int i = rootFirst.size() - 1; i >= 0; i--) {
            Object vertex = rootFirst.get(i);
            int h = 0;
            for (Object s : successors(vertex)) h = Math.max(h, structuralHeight(s) + 1);
            if (vertex instanceof MatcherState) ((MatcherState) vertex).structuralHeight = h;
            else((NodeState) vertex).structuralHeight = h;
        }
    }

    private static int structuralHeight(Object vertex) {
        return vertex instanceof MatcherState ? ((MatcherState) vertex).structuralHeight
                : ((NodeState) vertex).structuralHeight;
    }

    // ---------------------------------------------------------------- Phase B: quantities and resolution

    private void resolveMatcher(MatcherState m) {
        if (m.demands.isEmpty()) return;
        claimSurplus(m);
        claimProviders(m);
        for (Demand d : m.demands) {
            long remaining = d.remaining();
            if (remaining <= 0) continue;
            NodeState producer = m.chosen;
            if (producer != null) {
                ResourceKey key = producer.candidate.pattern().outputMatching(m.matcher);
                d.resolution.sources.add(new SourceClaim.Craft(producer.id, key, remaining));
                producer.demand.merge(key, remaining, Long::sum);
                producer.consumers.add(new Consumer(d.consumer, d.location, key, remaining));
            } else {
                boolean exists = anyStockExists(m.matcher);
                d.resolution.sources.add(new SourceClaim.Unresolved(m.matcher.key(), remaining, exists));
                PlanIssue.Kind kind = exists ? PlanIssue.Kind.UNREACHABLE
                        : m.cycleRejected ? PlanIssue.Kind.CYCLE : PlanIssue.Kind.MISSING;
                issues.add(
                        new PlanIssue(
                                kind,
                                m.matcher,
                                remaining,
                                d.consumerId(),
                                kind == PlanIssue.Kind.CYCLE ? "every recipe for it loops back on itself" : null));
            }
        }
    }

    private void claimSurplus(MatcherState m) {
        for (Demand d : m.demands) {
            if (craftOnly && d.consumer == null) continue;
            for (NodeState producer : surplusProducers) {
                if (d.remaining() <= 0) break;
                if (producer == d.consumer) continue;
                PlanNode p = producer.out;
                for (Map.Entry<ResourceKey, Long> s : p.surplus.entrySet()) {
                    long have = s.getValue();
                    if (have <= 0 || !m.matcher.matches(s.getKey())) continue;
                    if (!reachable(p.crafter.id(), d.location, s.getKey())) continue;
                    if (d.consumer != null && dependsOn(producer, d.consumer)) continue;
                    long take = Math.min(have, d.remaining());
                    s.setValue(have - take);
                    d.resolution.sources.add(new SourceClaim.Surplus(p.id, s.getKey(), take));
                    p.allocations.add(new Allocation(d.consumerId(), s.getKey(), take));
                    if (d.consumer != null) d.consumer.surplusFrom.add(producer);
                    if (d.remaining() <= 0) break;
                }
            }
        }
    }

    private void claimProviders(MatcherState m) {
        List<StockView.Entry> entries = stockFor(m.matcher);
        if (entries.isEmpty()) return;
        Map<Demand, List<StockView.Entry>> reachableByDemand = new IdentityHashMap<>();
        List<Demand> open = new ArrayList<>();
        for (Demand d : m.demands) {
            if (d.remaining() <= 0 || craftOnly && d.consumer == null) continue;
            List<StockView.Entry> reachable = new ArrayList<>();
            for (StockView.Entry e : entries) {
                if (availableAfterClaims(e) > 0 && reachable(e.location(), d.location, e.key())) reachable.add(e);
            }
            reachableByDemand.put(d, reachable);
            open.add(d);
        }
        // Most constrained consumer first, then the lowest one in the tree.
        open.sort(
                Comparator.<Demand>comparingInt(d -> reachableByDemand.get(d).size())
                        .thenComparingInt(Demand::structuralHeight));
        for (Demand d : open) {
            List<StockView.Entry> reachable = reachableByDemand.get(d);
            reachable.sort(Comparator.comparingLong(this::availableAfterClaims).reversed());
            for (StockView.Entry e : reachable) {
                long need = d.remaining();
                if (need <= 0) break;
                long take = Math.min(need, availableAfterClaims(e));
                if (take <= 0) continue;
                stockClaimed.merge(new StockSlot(e.location(), e.key()), take, Long::sum);
                d.resolution.sources.add(new SourceClaim.Provider(e.location(), e.key(), take));
            }
        }
    }

    private void processNode(NodeState n) {
        if (n.demand.isEmpty()) return;
        Pattern pattern = n.candidate.pattern();
        long crafts = 0;
        for (Map.Entry<ResourceKey, Long> d : n.demand.entrySet()) {
            long yield = pattern.yield(d.getKey());
            crafts = Math.max(crafts, ceilDiv(d.getValue(), yield));
        }
        if (crafts <= 0) return;
        PlanNode out = new PlanNode(n.id, pattern);
        n.out = out;
        out.crafts = crafts;
        PatternRegistry.CrafterSlot crafter = chooseCrafter(n);
        out.crafter = crafter.crafter();
        out.patternSlot = crafter.patternSlot();
        if (out.crafter.legacy()) {
            issues.add(
                    new PlanIssue(
                            PlanIssue.Kind.LEGACY_CRAFTER,
                            ResourceMatcher.exact(pattern.primaryOutput()),
                            crafts,
                            out.id,
                            out.crafter.name()));
        }
        for (Consumer c : n.consumers) {
            out.allocations.add(new Allocation(c.consumerId(), c.key, c.qty));
            if (c.location != null && !reachable(out.crafter.id(), c.location, c.key)) {
                issues.add(
                        new PlanIssue(
                                PlanIssue.Kind.UNREACHABLE,
                                ResourceMatcher.exact(c.key),
                                c.qty,
                                c.consumerId(),
                                "no route from " + out.crafter.name()));
            }
        }
        for (var o : pattern.outputs()) {
            long made = o.amount() * crafts;
            long used = n.demand.getOrDefault(o.key(), 0L);
            if (made > used) out.surplus.put(o.key(), made - used);
        }
        if (!out.surplus.isEmpty()) surplusProducers.add(n);
        for (InputSpec input : pattern.inputs()) {
            long total = input.amountFor(crafts);
            InputResolution res = new InputResolution(input, total);
            out.inputs.add(res);
            UUID deliverTo = input.target() != null ? input.target() : out.crafter.id();
            matchers.get(input.matcher()).demands.add(new Demand(n, deliverTo, total, res));
        }
    }

    /**
     * First crafter (by priority) that can reach every consumer and, for each input that can't be crafted, can be
     * reached from some stock. Falls back to the first one that reaches the consumers, then to the first one.
     */
    private PatternRegistry.CrafterSlot chooseCrafter(NodeState n) {
        List<PatternRegistry.CrafterSlot> candidates = n.candidate.crafters();
        PatternRegistry.CrafterSlot reachesConsumers = null;
        for (PatternRegistry.CrafterSlot slot : candidates) {
            UUID id = slot.crafter().id();
            boolean ok = true;
            for (Consumer c : n.consumers) {
                if (c.location != null && !reachable(id, c.location, c.key)) {
                    ok = false;
                    break;
                }
            }
            if (!ok) continue;
            if (reachesConsumers == null) reachesConsumers = slot;
            if (inputsReachable(n, id)) return slot;
        }
        return reachesConsumers != null ? reachesConsumers : candidates.get(0);
    }

    private boolean inputsReachable(NodeState n, UUID crafter) {
        for (InputSpec input : n.candidate.pattern().inputs()) {
            MatcherState m = matchers.get(input.matcher());
            if (m.chosen != null) continue;
            List<StockView.Entry> entries = stockFor(input.matcher());
            if (entries.isEmpty()) continue;
            boolean any = false;
            UUID deliverTo = input.target() != null ? input.target() : crafter;
            for (StockView.Entry e : entries) {
                if (availableAfterClaims(e) > 0 && reachable(e.location(), deliverTo, e.key())) {
                    any = true;
                    break;
                }
            }
            if (!any) return false;
        }
        return true;
    }

    /** Whether {@code from} needs anything {@code target} makes, through craft or surplus edges. */
    private boolean dependsOn(NodeState from, NodeState target) {
        Deque<NodeState> todo = new ArrayDeque<>();
        Set<NodeState> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        todo.add(from);
        while (!todo.isEmpty()) {
            NodeState cur = todo.poll();
            if (cur == target) return true;
            if (!seen.add(cur)) continue;
            for (InputSpec input : cur.candidate.pattern().inputs()) {
                NodeState child = matchers.get(input.matcher()).chosen;
                if (child != null) todo.add(child);
            }
            todo.addAll(cur.surplusFrom);
        }
        return false;
    }

    // ---------------------------------------------------------------- heights and totals

    private void computeHeights(Map<Integer, PlanNode> nodes) {
        Map<Integer, Integer> state = new HashMap<>(); // 1 = in progress, 2 = done
        for (PlanNode n : nodes.values()) height(n, nodes, state);
    }

    private int height(PlanNode n, Map<Integer, PlanNode> nodes, Map<Integer, Integer> state) {
        Integer s = state.get(n.id);
        if (s != null) {
            if (s == 1) throw new IllegalStateException("crafting plan has a cycle at " + n);
            return n.height;
        }
        state.put(n.id, 1);
        int h = 0;
        for (InputResolution res : n.inputs) {
            for (SourceClaim c : res.sources) {
                int child = c instanceof SourceClaim.Craft ? ((SourceClaim.Craft) c).child()
                        : c instanceof SourceClaim.Surplus ? ((SourceClaim.Surplus) c).producer() : -1;
                PlanNode childNode = child < 0 ? null : nodes.get(child);
                if (childNode != null) h = Math.max(h, height(childNode, nodes, state) + 1);
            }
        }
        n.height = h;
        state.put(n.id, 2);
        return h;
    }

    private static void addRaw(Map<ResourceKey, Long> raw, InputResolution res) {
        for (SourceClaim c : res.sources) {
            if (c instanceof SourceClaim.Provider) raw.merge(c.key(), c.qty(), Long::sum);
        }
    }

    // ---------------------------------------------------------------- helpers

    private List<StockView.Entry> stockFor(ResourceMatcher matcher) {
        return stockCache.computeIfAbsent(matcher, stock::find);
    }

    private long availableAfterClaims(StockView.Entry e) {
        return e.available() - stockClaimed.getOrDefault(new StockSlot(e.location(), e.key()), 0L);
    }

    private boolean anyStockExists(ResourceMatcher matcher) {
        for (StockView.Entry e : stockFor(matcher)) {
            if (availableAfterClaims(e) > 0) return true;
        }
        return false;
    }

    private boolean reachable(UUID from, UUID to, ResourceKey key) {
        return from.equals(to) || routes.canReach(from, to, key);
    }

    private void checkBudget() {
        if (System.nanoTime() > deadline) throw new PlanningException("Planning took too long");
    }

    private static long ceilDiv(long a, long b) {
        if (b <= 0) throw new IllegalStateException("pattern yields nothing of a demanded output");
        return (a + b - 1) / b;
    }

    private static final class MatcherState {

        final ResourceMatcher matcher;
        NodeState chosen;
        boolean expanded;
        boolean cycleRejected;
        int structuralHeight;
        final List<Demand> demands = new ArrayList<>();

        MatcherState(ResourceMatcher matcher) {
            this.matcher = matcher;
        }
    }

    private static final class NodeState {

        final int id;
        final PatternRegistry.Candidate candidate;
        final Set<ResourceMatcher> closure;
        int structuralHeight;
        final Map<ResourceKey, Long> demand = new LinkedHashMap<>();
        final List<Consumer> consumers = new ArrayList<>();
        final List<NodeState> surplusFrom = new ArrayList<>();
        PlanNode out;

        NodeState(int id, PatternRegistry.Candidate candidate, Set<ResourceMatcher> closure) {
            this.id = id;
            this.candidate = candidate;
            this.closure = closure;
        }
    }

    /** Demand of one consumer (a node, or the requester when {@code consumer == null}) on one matcher. */
    private static final class Demand {

        final NodeState consumer;
        final UUID location;
        final long qty;
        final InputResolution resolution;

        Demand(NodeState consumer, UUID location, long qty, InputResolution resolution) {
            this.consumer = consumer;
            this.location = location;
            this.qty = qty;
            this.resolution = resolution;
        }

        long remaining() {
            return qty - resolution.claimedQty();
        }

        int consumerId() {
            return consumer == null ? Allocation.REQUESTER : consumer.id;
        }

        int structuralHeight() {
            return consumer == null ? Integer.MAX_VALUE : consumer.structuralHeight;
        }
    }

    @Desugar
    private record Consumer(NodeState consumer, UUID location, ResourceKey key, long qty) {

        int consumerId() {
            return consumer == null ? Allocation.REQUESTER : consumer.id;
        }
    }

    @Desugar
    private record StockSlot(UUID location, ResourceKey key) {

        @Override
        public boolean equals(Object o) {
            return o instanceof StockSlot && ((StockSlot) o).location.equals(location)
                    && ((StockSlot) o).key.equals(key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(location, key);
        }
    }
}
