package logisticspipes.routing.astar;

import java.util.List;

/**
 * A compressed run of plain transport pipe between two junctions.
 * <p>
 * Edges are <b>directed</b>: pipe networks are not symmetric (one-way pipes, firewall filters and sub-system power only
 * apply in one direction), so each junction owns the corridors leaving it and the reverse corridor is a separate edge
 * owned by the other junction.
 * <p>
 * Edges are immutable because graph snapshots are immutable. "Bumping the version" means the writer replaces the edge
 * with a copy that has the same {@link #id} and {@code version + 1}; a cached route that recorded the old version then
 * no longer matches.
 */
public final class CorridorEdge {

    public final long id;
    public final JunctionId from;
    public final JunctionId to;
    /** Aggregated cost of the compressed run. */
    public final double weight;
    /** {@link RoutingFlags} mask of what may travel along this corridor. */
    public final int flags;
    /** Filters (firewall pipes) applied to anything using this corridor. Opaque to the core. */
    public final List<?> filters;
    public final int blockDistance;
    public final int exitSide;
    public final int insertSide;
    public final long[] chunks;
    /** See {@link EdgeSpec#travelPath}; {@code null} when items must be simulated along this corridor. */
    public final byte[] travelPath;
    public final long version;

    CorridorEdge(long id, JunctionId from, EdgeSpec spec, long version) {
        this.id = id;
        this.from = from;
        to = spec.to;
        weight = spec.weight;
        flags = spec.flags;
        filters = spec.filters;
        blockDistance = spec.blockDistance;
        exitSide = spec.exitSide;
        insertSide = spec.insertSide;
        chunks = spec.chunks;
        travelPath = spec.travelPath;
        this.version = version;
    }

    public boolean isTeleportable() {
        return travelPath != null;
    }

    /** Edge ids encode the owning junction in the high 32 bits so a snapshot can find an edge without a global map. */
    static long makeId(JunctionId owner, int sequence) {
        return (owner.value() << 32) | (sequence & 0xFFFFFFFFL);
    }

    static int ownerIndex(long edgeId) {
        return (int) (edgeId >>> 32);
    }

    EdgeSpec toSpec() {
        return new EdgeSpec(to, weight, flags, filters, blockDistance, exitSide, insertSide, chunks, travelPath);
    }

    @Override
    public String toString() {
        return "Edge#" + Long.toHexString(
                id) + "{" + from + "->" + to + ", w=" + weight + ", flags=" + flags + ", v=" + version + "}";
    }
}
