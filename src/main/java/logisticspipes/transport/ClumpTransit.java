package logisticspipes.transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.proxy.MainProxy;
import logisticspipes.routing.astar.ChunkEdgeIndex;
import logisticspipes.routing.astar.CorridorEdge;
import logisticspipes.routing.astar.JunctionNode;
import logisticspipes.routing.astar.LPJunctionNetwork;
import logisticspipes.routing.astar.NetworkGraph;

/**
 * What happens to clumps in flight when a pipe on their corridor is removed (server thread only).
 * <p>
 * Clumps are indexed by the chunks their corridor passes through. When an LP pipe is removed, every clump whose
 * corridor contains it compares its own position (known from time) with the pipe: a break behind it is ignored, a break
 * in the pipe it is in drops its items, and a break ahead sends it back to the junction it left. The corridors through
 * the pipe are also flagged broken until the router re-scans them, so nothing departs through the gap in the meantime.
 */
public final class ClumpTransit {

    /** How long a corridor stays flagged broken if no re-scan replaces it first (e.g. the pipe was put back). */
    private static final int BROKEN_TICKS = 100;

    private static final Map<Long, Set<ItemClump>> byChunk = new HashMap<>();
    private static final Map<Long, BrokenMark> brokenEdges = new HashMap<>();

    private ClumpTransit() {}

    private static final class BrokenMark {

        final long version;
        final long until;

        BrokenMark(long version, long until) {
            this.version = version;
            this.until = until;
        }
    }

    static void register(ItemClump clump) {
        if (clump.edge == null) {
            return;
        }
        clump.indexedChunks = clump.edge.chunks;
        for (long chunk : clump.indexedChunks) {
            byChunk.computeIfAbsent(chunk, k -> Collections.newSetFromMap(new IdentityHashMap<>())).add(clump);
        }
    }

    static void unregister(ItemClump clump) {
        long[] chunks = clump.indexedChunks;
        if (chunks == null) {
            return;
        }
        clump.indexedChunks = null;
        for (long chunk : chunks) {
            Set<ItemClump> set = byChunk.get(chunk);
            if (set != null) {
                set.remove(clump);
                if (set.isEmpty()) {
                    byChunk.remove(chunk);
                }
            }
        }
    }

    /** Whether {@code edge} lost a pipe and hasn't been re-scanned since. */
    static boolean isBroken(CorridorEdge edge, long now) {
        if (brokenEdges.isEmpty()) {
            return false;
        }
        BrokenMark mark = brokenEdges.get(edge.id);
        if (mark == null) {
            return false;
        }
        CorridorEdge current = LPJunctionNetwork.writer().graph().edge(edge.id);
        if (now > mark.until || current != null && current.version != mark.version) {
            brokenEdges.remove(edge.id);
            return false;
        }
        return true;
    }

    /** An LP pipe at the given position is about to be removed. */
    public static void onPipeRemoved(World world, int x, int y, int z) {
        if (world == null || !MainProxy.isServer(world)) {
            return;
        }
        int dim = MainProxy.getDimensionForWorld(world);
        long chunk = ChunkEdgeIndex.chunkKeyForBlock(dim, x, z);
        long now = world.getTotalWorldTime();

        NetworkGraph graph = LPJunctionNetwork.writer().graph();
        for (long id : new ArrayList<>(graph.chunkIndex().edgesIn(chunk))) {
            CorridorEdge edge = graph.edge(id);
            if (edge == null || edge.travelPath == null) {
                continue;
            }
            JunctionNode from = graph.node(edge.from);
            if (from != null && from.dimension == dim
                    && indexOnPath(from.x, from.y, from.z, edge.travelPath, x, y, z) >= 0) {
                brokenEdges.put(id, new BrokenMark(edge.version, now + BROKEN_TICKS));
            }
        }

        Set<ItemClump> clumps = byChunk.get(chunk);
        if (clumps == null) {
            return;
        }
        for (ItemClump clump : new ArrayList<>(clumps)) {
            if (clump.edge == null || clump.holder == null || clump.holder.getWorld() != world) {
                continue;
            }
            int k = indexOnPath(clump.srcX, clump.srcY, clump.srcZ, clump.edge.travelPath, x, y, z);
            if (k >= 0) {
                clump.holder.corridorBroken(clump, k, now);
            }
        }
    }

    /** What a clump does when a pipe of its corridor is removed. */
    enum BreakAction {
        /** The pipe is behind it: carry on. */
        FINISH,
        /** Drop the items in the pipe it is in now (that pipe broke, or it is already going back). */
        DROP,
        /** The pipe is ahead of it: go back to the junction it left. */
        TURN_BACK
    }

    /** Index of the pipe {@code clump} is in at {@code position}, clamped to the corridor. */
    static int currentPipe(ItemClump clump, float position) {
        return Math.min(Math.max((int) Math.floor(position), 0), clump.edge.travelPath.length - 1);
    }

    /** The break rule, for pipe index {@code broken} and the clump's current pipe index {@code current}. */
    static BreakAction onBreak(ItemClump clump, int broken, int current) {
        if (broken == current) {
            return BreakAction.DROP;
        }
        boolean ahead = clump.returning ? broken < current : broken > current;
        if (!ahead) {
            return BreakAction.FINISH;
        }
        return clump.returning ? BreakAction.DROP : BreakAction.TURN_BACK;
    }

    /**
     * Index of block {@code (x, y, z)} on a corridor that starts at {@code (sx, sy, sz)} (index 0) and takes the steps
     * of {@code path} (the last step reaches index {@code path.length}), or -1 if the corridor doesn't pass it.
     */
    static int indexOnPath(int sx, int sy, int sz, byte[] path, int x, int y, int z) {
        int cx = sx, cy = sy, cz = sz;
        if (cx == x && cy == y && cz == z) {
            return 0;
        }
        for (int i = 0; i < path.length; i++) {
            ForgeDirection dir = ForgeDirection.getOrientation(path[i]);
            cx += dir.offsetX;
            cy += dir.offsetY;
            cz += dir.offsetZ;
            if (cx == x && cy == y && cz == z) {
                return i + 1;
            }
        }
        return -1;
    }

    /** Block coordinates of pipe {@code index} on the corridor, as {x, y, z}. */
    static int[] pipeAt(int sx, int sy, int sz, byte[] path, int index) {
        int cx = sx, cy = sy, cz = sz;
        for (int i = 0; i < index && i < path.length; i++) {
            ForgeDirection dir = ForgeDirection.getOrientation(path[i]);
            cx += dir.offsetX;
            cy += dir.offsetY;
            cz += dir.offsetZ;
        }
        return new int[] { cx, cy, cz };
    }

    public static void clear() {
        byChunk.clear();
        brokenEdges.clear();
    }
}
