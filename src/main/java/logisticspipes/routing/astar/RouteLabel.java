package logisticspipes.routing.astar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One route the search accepted: a way to reach {@link #node} that carries {@link #flags} and passes {@link #filters}.
 * <p>
 * A junction can be reached by several labels. Like the link-state router it replaces, the search keeps, per junction
 * and per {@link RoutingFlags flag}, the cheapest route carrying that flag, plus cheaper-or-equal alternatives whose
 * filter set is not a superset of one already seen (so a longer path that avoids a firewall is still offered when the
 * short one is filtered).
 */
public final class RouteLabel {

    public final JunctionId source;
    public final JunctionId node;
    public final double distance;
    /** Every flag that holds along the whole route. */
    public final int flags;
    /** The flags this label was the first to deliver to {@link #node}. */
    public final int newFlags;
    public final List<?> filters;
    public final int blockDistance;
    /** The corridor leaving the source; its exit side is where an item has to go. */
    public final CorridorEdge firstEdge;

    private final RouteLabel parent;
    private final CorridorEdge via;

    RouteLabel(JunctionId source, JunctionId node, double distance, int flags, int newFlags, List<?> filters,
            int blockDistance, RouteLabel parent, CorridorEdge via) {
        this.source = source;
        this.node = node;
        this.distance = distance;
        this.flags = flags;
        this.newFlags = newFlags;
        this.filters = filters;
        this.blockDistance = blockDistance;
        this.parent = parent;
        this.via = via;
        firstEdge = parent == null ? via : parent.firstEdge;
    }

    CorridorEdge via() {
        return via;
    }

    /** Corridors of the route, in order from the source. */
    public List<CorridorEdge> edges() {
        ArrayList<CorridorEdge> list = new ArrayList<>();
        for (RouteLabel l = this; l != null; l = l.parent) {
            list.add(l.via);
        }
        Collections.reverse(list);
        return list;
    }

    private volatile CorridorEdge[] edgeArray;

    /** {@link #edges()} as an array, built once per label. Callers must not modify it. */
    public CorridorEdge[] edgeArray() {
        CorridorEdge[] array = edgeArray;
        if (array == null) {
            array = edges().toArray(new CorridorEdge[0]);
            edgeArray = array;
        }
        return array;
    }

    /** Junctions of the route, source first. */
    public List<JunctionId> path() {
        List<CorridorEdge> edges = edges();
        List<JunctionId> path = new ArrayList<>(edges.size() + 1);
        path.add(source);
        for (CorridorEdge e : edges) {
            path.add(e.to);
        }
        return path;
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    @Override
    public String toString() {
        return "Route{" + source
                + "->"
                + node
                + ", d="
                + distance
                + ", flags="
                + flags
                + ", new="
                + newFlags
                + ", filters="
                + filters.size()
                + "}";
    }
}
