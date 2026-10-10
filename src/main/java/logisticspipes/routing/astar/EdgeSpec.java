package logisticspipes.routing.astar;

import java.util.Collections;
import java.util.List;

/**
 * Description of one corridor as found by a corridor scan, before the graph writer assigns it an id and a version. Two
 * specs are "the same corridor" when they have the same endpoints; {@link #sameContent} decides whether the corridor
 * actually changed.
 */
public final class EdgeSpec {

    public final JunctionId to;
    public final double weight;
    public final int flags;
    public final List<?> filters;
    public final int blockDistance;
    /** ForgeDirection ordinal of the side the corridor leaves the source junction from. */
    public final int exitSide;
    /** ForgeDirection ordinal of the side the corridor enters the destination junction from. */
    public final int insertSide;
    /** Chunk keys (see {@link ChunkEdgeIndex#chunkKey}) of every block the corridor passes through. */
    public final long[] chunks;
    /**
     * ForgeDirection ordinals of the step taken at each pipe of the corridor, starting with {@link #exitSide}, if items
     * may skip the corridor as a scheduled hop (only LP pipes, no special connections); {@code null} otherwise.
     */
    public final byte[] travelPath;

    public EdgeSpec(JunctionId to, double weight, int flags, List<?> filters, int blockDistance, int exitSide,
            int insertSide, long[] chunks) {
        this(to, weight, flags, filters, blockDistance, exitSide, insertSide, chunks, null);
    }

    public EdgeSpec(JunctionId to, double weight, int flags, List<?> filters, int blockDistance, int exitSide,
            int insertSide, long[] chunks, byte[] travelPath) {
        if (weight < 0 || Double.isNaN(weight)) {
            throw new IllegalArgumentException("Corridor weight must be non-negative: " + weight);
        }
        this.to = to;
        this.weight = weight;
        this.flags = flags;
        this.filters = filters == null ? Collections.emptyList() : filters;
        this.blockDistance = blockDistance;
        this.exitSide = exitSide;
        this.insertSide = insertSide;
        this.chunks = chunks == null ? new long[0] : chunks;
        this.travelPath = travelPath;
    }

    public EdgeSpec(JunctionId to, double weight, int flags) {
        this(to, weight, flags, null, (int) Math.ceil(weight), 6, 6, null);
    }

    boolean sameContent(CorridorEdge e) {
        return e.to.equals(to) && e.weight == weight
                && e.flags == flags
                && e.blockDistance == blockDistance
                && e.exitSide == exitSide
                && e.insertSide == insertSide
                && e.filters.equals(filters)
                && java.util.Arrays.equals(e.chunks, chunks)
                && java.util.Arrays.equals(e.travelPath, travelPath);
    }

    /**
     * Whether replacing {@code old} by this spec could make some route shorter or possible that was not before. Only
     * such changes need to invalidate cached routes that do not use the edge; pure worsening changes are caught by the
     * per-edge version check of the routes that do use it.
     */
    boolean improvesOn(CorridorEdge old) {
        return weight < old.weight || (flags & ~old.flags) != 0 || !filters.equals(old.filters);
    }
}
