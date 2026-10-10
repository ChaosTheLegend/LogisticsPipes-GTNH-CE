package logisticspipes.routing.astar;

import java.util.Collections;
import java.util.List;

/**
 * Every route from one source to its whole network.
 * <p>
 * The design caches per pair only. This exists because parts of the {@code IRouter} contract are inherently
 * whole-network views ("all routers by cost", the full route table, the power providers reachable from a pipe) and are
 * polled by GUIs, request handling and the power system. They are computed lazily on first use with the same search
 * routine ({@code targets == null}) and validated exactly like pair entries; they are never used to answer pair
 * queries.
 */
public final class SweepEntry extends ValidatedRoutes {

    /** Accepted routes in settle order (increasing cost). */
    public final List<RouteLabel> settledOrder;

    SweepEntry(NetworkGraph graph, JunctionId source, List<RouteLabel> settledOrder) {
        super(graph, source, lastEdges(settledOrder));
        this.settledOrder = Collections.unmodifiableList(settledOrder);
    }

    /** A whole-network view can be affected by any improvement anywhere in the network. */
    @Override
    boolean survives(ImprovementEvent event, NetworkGraph graph) {
        return false;
    }
}
