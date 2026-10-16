package logisticspipes.routing.astar;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Output of {@link JunctionSearch#search}. */
public final class SearchResult {

    public final NetworkGraph graph;
    public final JunctionId source;
    /** Accepted routes per reached target (every reached junction when the search ran over all targets). */
    public final Map<JunctionId, List<RouteLabel>> routes;
    /** Every accepted route in the order the search settled them, i.e. by increasing priority. */
    public final List<RouteLabel> settledOrder;
    /** Targets that were not reached by any route. */
    public final Set<JunctionId> unreachable;
    /** Number of distinct junctions at which at least one route was accepted. */
    public final int settledNodes;
    /** Number of queue entries taken off the priority queue, stale ones included. */
    public final int polled;

    SearchResult(NetworkGraph graph, JunctionId source, Map<JunctionId, List<RouteLabel>> routes,
            List<RouteLabel> settledOrder, Set<JunctionId> unreachable, int settledNodes, int polled) {
        this.graph = graph;
        this.source = source;
        this.routes = Collections.unmodifiableMap(routes);
        this.settledOrder = Collections.unmodifiableList(settledOrder);
        this.unreachable = Collections.unmodifiableSet(unreachable);
        this.settledNodes = settledNodes;
        this.polled = polled;
    }

    public List<RouteLabel> routesTo(JunctionId target) {
        List<RouteLabel> list = routes.get(target);
        return list == null ? Collections.emptyList() : list;
    }

    /** Shortest distance to {@code target} over routes carrying {@code flag}, or +inf. */
    public double distanceTo(JunctionId target, int flag) {
        double best = Double.POSITIVE_INFINITY;
        for (RouteLabel l : routesTo(target)) {
            if ((l.flags & flag) != 0 && l.distance < best) {
                best = l.distance;
            }
        }
        return best;
    }
}
