package logisticspipes.routing.astar;

import static logisticspipes.routing.astar.TestNetworks.id;
import static logisticspipes.routing.astar.TestNetworks.node;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Collections;

import org.junit.jupiter.api.Test;

/** Corridor travel paths, which let items skip a corridor as one scheduled hop. */
class TravelPathTest {

    private static final int EAST = 5;
    private static final int WEST = 4;

    private static EdgeSpec spec(int to, int blocks, int exit, byte[] path) {
        return new EdgeSpec(id(to), blocks, RoutingFlags.ALL, null, blocks, exit, exit ^ 1, null, path);
    }

    private static JunctionGraphWriter line(byte[] ab, byte[] ba, byte[] bc, byte[] cb) {
        JunctionGraphWriter w = new JunctionGraphWriter();
        node(w, 1, 0, 64, 0);
        node(w, 2, 2, 64, 0);
        node(w, 3, 5, 64, 0);
        w.setEdges(id(1), Collections.singletonList(spec(2, 2, EAST, ab)));
        w.setEdges(id(2), java.util.Arrays.asList(spec(1, 2, WEST, ba), spec(3, 3, EAST, bc)));
        w.setEdges(id(3), Collections.singletonList(spec(2, 3, WEST, cb)));
        w.flush();
        return w;
    }

    private static byte[] steps(int dir, int n) {
        byte[] path = new byte[n];
        java.util.Arrays.fill(path, (byte) dir);
        return path;
    }

    @Test
    void mergeJoinsTravelPaths() {
        JunctionGraphWriter w = line(steps(EAST, 2), steps(WEST, 2), steps(EAST, 3), steps(WEST, 3));
        w.mergeThrough(id(2));
        CorridorEdge merged = w.graph().node(id(1)).edgeTo(id(3));
        assertNotNull(merged);
        assertArrayEquals(steps(EAST, 5), merged.travelPath);
        assertArrayEquals(steps(WEST, 5), w.graph().node(id(3)).edgeTo(id(1)).travelPath);
    }

    @Test
    void mergeWithOpaqueHalfIsOpaque() {
        JunctionGraphWriter w = line(steps(EAST, 2), steps(WEST, 2), null, steps(WEST, 3));
        w.mergeThrough(id(2));
        assertNull(w.graph().node(id(1)).edgeTo(id(3)).travelPath);
        assertNotNull(w.graph().node(id(3)).edgeTo(id(1)).travelPath);
    }

    @Test
    void travelPathChangeBumpsVersion() {
        JunctionGraphWriter w = line(steps(EAST, 2), steps(WEST, 2), steps(EAST, 3), steps(WEST, 3));
        CorridorEdge before = w.graph().node(id(1)).edgeTo(id(2));
        w.setEdges(id(1), Collections.singletonList(spec(2, 2, EAST, null)));
        w.flush();
        CorridorEdge after = w.graph().node(id(1)).edgeTo(id(2));
        assertNotEquals(before.version, after.version);
        assertNull(w.graph().hopStillValid(before));
    }

    @Test
    void hopStillValidFollowsTheCurrentEdge() {
        JunctionGraphWriter w = line(steps(EAST, 2), steps(WEST, 2), steps(EAST, 3), steps(WEST, 3));
        CorridorEdge ab = w.graph().node(id(1)).edgeTo(id(2));
        assertSame(ab, w.graph().hopStillValid(ab));
        // a longer corridor to the same junction through the same side is still the same hop
        w.setEdges(id(1), Collections.singletonList(spec(2, 4, EAST, steps(EAST, 4))));
        w.flush();
        CorridorEdge now = w.graph().hopStillValid(ab);
        assertNotNull(now);
        assertArrayEquals(steps(EAST, 4), now.travelPath);
        // gone when the target junction goes inactive
        w.setActive(id(2), false);
        w.flush();
        assertNull(w.graph().hopStillValid(ab));
    }
}
