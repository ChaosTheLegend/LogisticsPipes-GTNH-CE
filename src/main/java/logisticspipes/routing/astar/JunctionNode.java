package logisticspipes.routing.astar;

import java.util.Collections;
import java.util.List;

/**
 * A routing-relevant pipe (for Logistics Pipes: a routed pipe). Immutable; the writer creates a modified copy when
 * anything about the junction changes.
 */
public final class JunctionNode {

    public final JunctionId id;
    public final int dimension;
    public final int x;
    public final int y;
    public final int z;
    /** Outgoing corridors. */
    public final List<CorridorEdge> edges;
    /**
     * True if the junction is itself a provider/requester. Every LP router is one, so dead-end plain pipe never becomes
     * a node at all: the corridor scan simply does not report it.
     */
    public final boolean isActiveEndpoint;
    /** False while the pipe's chunk is unloaded; searches treat inactive junctions as absent. */
    public final boolean active;
    /** Owner object (the router). Opaque to the core. */
    public final Object payload;
    /** Extra per-junction data the owner wants to read back from search results (power providers). Opaque. */
    public final Object data;

    final int nextEdgeSequence;

    JunctionNode(JunctionId id, int dimension, int x, int y, int z, List<CorridorEdge> edges, boolean isActiveEndpoint,
            boolean active, Object payload, Object data, int nextEdgeSequence) {
        this.id = id;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.edges = Collections.unmodifiableList(edges);
        this.isActiveEndpoint = isActiveEndpoint;
        this.active = active;
        this.payload = payload;
        this.data = data;
        this.nextEdgeSequence = nextEdgeSequence;
    }

    JunctionNode withEdges(List<CorridorEdge> newEdges, int newNextSequence) {
        return new JunctionNode(
                id,
                dimension,
                x,
                y,
                z,
                newEdges,
                isActiveEndpoint,
                active,
                payload,
                data,
                newNextSequence);
    }

    JunctionNode withActive(boolean newActive) {
        return new JunctionNode(
                id,
                dimension,
                x,
                y,
                z,
                edges,
                isActiveEndpoint,
                newActive,
                payload,
                data,
                nextEdgeSequence);
    }

    JunctionNode withData(Object newData) {
        return new JunctionNode(
                id,
                dimension,
                x,
                y,
                z,
                edges,
                isActiveEndpoint,
                active,
                payload,
                newData,
                nextEdgeSequence);
    }

    CorridorEdge edgeTo(JunctionId target) {
        for (CorridorEdge e : edges) {
            if (e.to.equals(target)) {
                return e;
            }
        }
        return null;
    }

    CorridorEdge edgeById(long edgeId) {
        for (CorridorEdge e : edges) {
            if (e.id == edgeId) {
                return e;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "Junction{" + id
                + " @"
                + dimension
                + ":"
                + x
                + ","
                + y
                + ","
                + z
                + ", edges="
                + edges.size()
                + (active ? "" : ", inactive")
                + "}";
    }
}
