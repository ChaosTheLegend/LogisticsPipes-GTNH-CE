package logisticspipes.routing.astar;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Cached routes for one (source, destination) pair. */
public final class RouteCacheEntry extends ValidatedRoutes {

    /** Ties and float noise count as "could beat": the check must stay conservative. */
    private static final double EPSILON = 1e-9;

    public final PairKey key;
    /** Every accepted route to the destination, cheapest first (several when flags or filters differ). */
    public final List<RouteLabel> routes;
    /** Junctions of the cheapest route, source first; empty if unreachable. */
    public final List<JunctionId> path;
    /** Cost of the cheapest route, +inf if unreachable. */
    public final double totalWeight;
    /**
     * Per flag bit: the cost of the filter-free route that settled the flag at the destination (+inf if none did). Any
     * new route at least that expensive would be rejected by the search, so it cannot change these results.
     */
    private final double[] settledAt = new double[RoutingFlags.FLAG_COUNT];

    RouteCacheEntry(NetworkGraph graph, PairKey key, List<RouteLabel> routes) {
        super(graph, key.source, edgesOfPaths(routes));
        this.key = key;
        this.routes = Collections.unmodifiableList(routes);
        path = Collections.unmodifiableList(bestPath(routes));
        double best = Double.POSITIVE_INFINITY;
        Arrays.fill(settledAt, Double.POSITIVE_INFINITY);
        for (RouteLabel l : routes) {
            best = Math.min(best, l.distance);
            if (l.filters.isEmpty()) {
                for (int b = 0; b < RoutingFlags.FLAG_COUNT; b++) {
                    if ((l.newFlags & (1 << b)) != 0) {
                        settledAt[b] = Math.min(settledAt[b], l.distance);
                    }
                }
            }
        }
        totalWeight = best;
    }

    public boolean isReachable() {
        return !routes.isEmpty();
    }

    /**
     * The "ellipse" test: any route that uses the improved corridor {@code u -> v} costs at least
     * {@code h(s, u) + w + h(v, t)} with the admissible heuristic {@code h}. If that lower bound is not below the cost
     * at which every flag the corridor can carry was already settled, no such route can enter the result.
     */
    @Override
    boolean survives(ImprovementEvent e, NetworkGraph graph) {
        double bound;
        int flags;
        switch (e.kind) {
            case ImprovementEvent.DATA:
                return true;
            case ImprovementEvent.EDGE:
                bound = graph.lowerBound(key.source.index(), e.from) + e.weight
                        + graph.lowerBound(e.to, key.dest.index());
                flags = e.flags;
                break;
            case ImprovementEvent.NODE:
                bound = graph.lowerBound(key.source.index(), e.from) + graph.lowerBound(e.from, key.dest.index());
                flags = RoutingFlags.ALL;
                break;
            default:
                return false;
        }
        int src = key.source.index();
        if (e.from != src) {
            // a flag the source does not emit cannot arrive, wherever the improvement is
            JunctionNode source = graph.node(src);
            int emitted = 0;
            if (source != null) {
                for (CorridorEdge edge : source.edges) {
                    emitted |= edge.flags;
                }
            }
            flags &= emitted;
        }
        for (int b = 0; b < RoutingFlags.FLAG_COUNT; b++) {
            if ((flags & (1 << b)) != 0 && bound <= settledAt[b] + EPSILON) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "RouteCacheEntry{" + key + ", routes=" + routes.size() + ", weight=" + totalWeight + "}";
    }
}
