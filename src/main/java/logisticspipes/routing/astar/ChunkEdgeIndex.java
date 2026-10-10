package logisticspipes.routing.astar;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spatial index from chunk to the corridor edges that pass through it.
 * <p>
 * This is only a lookup accelerator: after a block edit (or a chunk unload) it answers "which corridors do I need to
 * re-scan?" without walking the whole graph. It is never used to invalidate routes; route invalidation is always
 * edge-based via {@link CorridorEdge#version}.
 * <p>
 * Only the graph writer mutates the index (one chunk's set at a time); any thread may read it.
 */
public final class ChunkEdgeIndex {

    private final ConcurrentHashMap<Long, Set<Long>> edgesByChunk = new ConcurrentHashMap<>();

    public static long chunkKey(int dimension, int chunkX, int chunkZ) {
        return ((long) (dimension & 0xFFFFF) << 44) | ((long) (chunkX & 0x3FFFFF) << 22) | (chunkZ & 0x3FFFFF);
    }

    public static long chunkKeyForBlock(int dimension, int x, int z) {
        return chunkKey(dimension, x >> 4, z >> 4);
    }

    /** Edge ids whose corridor passes through the chunk. The returned set is a live, read-only view. */
    public Set<Long> edgesIn(long chunkKey) {
        Set<Long> set = edgesByChunk.get(chunkKey);
        return set == null ? Collections.emptySet() : Collections.unmodifiableSet(set);
    }

    public int chunkCount() {
        return edgesByChunk.size();
    }

    void add(CorridorEdge edge) {
        for (long chunk : edge.chunks) {
            edgesByChunk.computeIfAbsent(chunk, k -> ConcurrentHashMap.newKeySet()).add(edge.id);
        }
    }

    void remove(CorridorEdge edge) {
        for (long chunk : edge.chunks) {
            edgesByChunk.computeIfPresent(chunk, (k, set) -> {
                set.remove(edge.id);
                return set.isEmpty() ? null : set;
            });
        }
    }

    void replace(CorridorEdge oldEdge, CorridorEdge newEdge) {
        if (oldEdge.chunks == newEdge.chunks || java.util.Arrays.equals(oldEdge.chunks, newEdge.chunks)) {
            return;
        }
        remove(oldEdge);
        add(newEdge);
    }

    void clear() {
        edgesByChunk.clear();
    }
}
