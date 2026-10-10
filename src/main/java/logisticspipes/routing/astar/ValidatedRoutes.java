package logisticspipes.routing.astar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Routes computed on one snapshot, plus what is needed to decide cheaply whether they still hold on a later one.
 * <p>
 * A result stays valid on a newer snapshot when
 * <ol>
 * <li>nothing in the source's component changed at all (O(1) stamp check), or</li>
 * <li>the component is still the same one ({@link ComponentInfo#identity}), none of the improvements logged since the
 * routes were computed can beat them ({@link #survives}), and every corridor the routes use still has the version
 * recorded here and leads to an active junction.</li>
 * </ol>
 * The edge-version check alone is not enough on a graph with loops: a corridor elsewhere that became cheaper, or a new
 * corridor, can produce a shorter route that avoids every recorded edge. Worsening changes (removal, higher weight,
 * lost flag) cannot beat a route that does not use the worsened corridor, so they are never logged.
 */
public abstract class ValidatedRoutes {

    public final JunctionId source;
    final CorridorEdge[] edges;
    final long componentIdentity;
    private volatile long knownImprovementStamp;
    private volatile long knownChangeStamp;
    private volatile NetworkGraph validatedFor;
    /** Adapter-side view of these routes (e.g. converted ExitRoutes), built at most once per result. */
    private volatile Object adapterView;

    ValidatedRoutes(NetworkGraph graph, JunctionId source, Collection<CorridorEdge> usedEdges) {
        this.source = source;
        Map<Long, CorridorEdge> used = new LinkedHashMap<>();
        for (CorridorEdge e : usedEdges) {
            used.put(e.id, e);
        }
        edges = used.values().toArray(new CorridorEdge[0]);
        componentIdentity = graph.componentIdentity(source.index());
        knownImprovementStamp = graph.improvementStamp(source.index());
        knownChangeStamp = graph.changeStamp(source.index());
        validatedFor = graph;
    }

    /**
     * Whether these routes are provably unaffected by {@code event}, i.e. no route using the improved corridor or
     * junction can be as cheap as the routes kept here. Must be conservative: {@code false} when unsure.
     */
    abstract boolean survives(ImprovementEvent event, NetworkGraph graph);

    public NetworkGraph computedOn() {
        return validatedFor;
    }

    /** Ids of every corridor the routes use. */
    public long[] edgeIds() {
        long[] ids = new long[edges.length];
        for (int i = 0; i < edges.length; i++) {
            ids[i] = edges[i].id;
        }
        return ids;
    }

    /** Versions of {@link #edgeIds()} at the time the routes were computed (parallel array). */
    public long[] edgeVersionsAtCacheTime() {
        long[] v = new long[edges.length];
        for (int i = 0; i < edges.length; i++) {
            v[i] = edges[i].version;
        }
        return v;
    }

    public boolean isValid(NetworkGraph graph) {
        if (validatedFor == graph) {
            return true;
        }
        int src = source.index();
        if (graph.node(src) == null || graph.componentIdentity(src) != componentIdentity) {
            return false;
        }
        long change = graph.changeStamp(src);
        if (change == knownChangeStamp) {
            validatedFor = graph;
            return true;
        }
        long known = knownImprovementStamp;
        long improvement = graph.improvementStamp(src);
        if (improvement != known) {
            // newest first; the log always ends in an ALL marker older than anything cached for this component
            for (ImprovementEvent e = graph.improvementLog(src); e != null && e.stamp > known; e = e.next) {
                if (!survives(e, graph)) {
                    return false;
                }
            }
        }
        for (CorridorEdge recorded : edges) {
            CorridorEdge now = graph.edge(recorded.id);
            if (now == null || now.version != recorded.version || !graph.isActive(now.to.index())) {
                return false;
            }
        }
        knownImprovementStamp = improvement;
        knownChangeStamp = change;
        validatedFor = graph;
        return true;
    }

    /**
     * Whether the routes can still physically be used on {@code graph}, even if they may no longer be the shortest: all
     * corridors still exist with the same endpoints, sides, flags and filters, and lead to active junctions. Only such
     * a stale result may be served while a fresh one is computed in the background.
     */
    public boolean isTraversable(NetworkGraph graph) {
        if (graph.node(source.index()) == null) {
            return false;
        }
        if (edges.length == 0) {
            // "unreachable" has no corridors to check, and a merge can make it wrong: recompute instead of serving it
            return false;
        }
        for (CorridorEdge recorded : edges) {
            CorridorEdge now = graph.edge(recorded.id);
            if (now == null || !now.to.equals(recorded.to)
                    || now.flags != recorded.flags
                    || now.exitSide != recorded.exitSide
                    || !now.filters.equals(recorded.filters)
                    || !graph.isActive(now.to.index())) {
                return false;
            }
        }
        return true;
    }

    public Object getAdapterView() {
        return adapterView;
    }

    public void setAdapterView(Object view) {
        adapterView = view;
    }

    static List<CorridorEdge> edgesOfPaths(Collection<RouteLabel> labels) {
        List<CorridorEdge> list = new ArrayList<>();
        for (RouteLabel l : labels) {
            list.addAll(l.edges());
        }
        return list;
    }

    static List<CorridorEdge> lastEdges(Collection<RouteLabel> labels) {
        // Every parent of an accepted label is itself accepted, so the last corridor of each label covers all paths.
        List<CorridorEdge> list = new ArrayList<>(labels.size());
        for (RouteLabel l : labels) {
            list.add(l.via());
        }
        return list;
    }

    static List<JunctionId> bestPath(List<RouteLabel> labels) {
        RouteLabel best = null;
        for (RouteLabel l : labels) {
            if (best == null || l.distance < best.distance) {
                best = l;
            }
        }
        return best == null ? new ArrayList<>() : best.path();
    }
}
