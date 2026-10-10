package logisticspipes.transport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.routing.astar.CorridorEdge;
import logisticspipes.routing.astar.EdgeSpec;
import logisticspipes.routing.astar.JunctionGraphWriter;
import logisticspipes.routing.astar.JunctionId;
import logisticspipes.routing.astar.RoutingFlags;
import logisticspipes.transport.ClumpTransit.BreakAction;

/** Where a clump is on its corridor, and what it does when a pipe of that corridor is removed. */
class ClumpBreakTest {

    private static final int EAST = 5;

    /** A corridor of {@code pipes} steps east from (0, 64, 0), travelled at 4 ticks per pipe from tick 100. */
    private static ItemClump clumpOn(int pipes) {
        JunctionGraphWriter w = new JunctionGraphWriter();
        w.addJunction(JunctionId.of(1), 0, 0, 64, 0, "a", true);
        w.addJunction(JunctionId.of(2), 0, pipes, 64, 0, "b", true);
        byte[] path = new byte[pipes];
        Arrays.fill(path, (byte) EAST);
        w.setEdges(
                JunctionId.of(1),
                Collections.singletonList(
                        new EdgeSpec(JunctionId.of(2), pipes, RoutingFlags.ALL, null, pipes, EAST, 4, null, path)));
        w.flush();
        CorridorEdge edge = w.graph().node(JunctionId.of(1)).edges.get(0);

        ItemClump clump = new ItemClump(1, 2, TransportMode.Default, 0.25F);
        clump.edge = edge;
        clump.srcY = 64;
        clump.departTick = 100;
        clump.ticksPerPipe = 4;
        clump.arrivalTick = 100 + 4L * pipes;
        return clump;
    }

    private static BreakAction breakAt(ItemClump clump, int broken, long now) {
        return ClumpTransit.onBreak(clump, broken, ClumpTransit.currentPipe(clump, clump.positionAt(now)));
    }

    @Test
    void positionFollowsTime() {
        ItemClump clump = clumpOn(5);
        assertEquals(0, clump.positionAt(100), 1e-6);
        assertEquals(2.5, clump.positionAt(110), 1e-6);
        assertEquals(5, clump.positionAt(200), 1e-6); // clamped at the target
    }

    @Test
    void forwardClump() {
        ItemClump clump = clumpOn(5); // at tick 110 it is in pipe 2
        assertEquals(BreakAction.FINISH, breakAt(clump, 0, 110)); // its source, behind it
        assertEquals(BreakAction.FINISH, breakAt(clump, 1, 110));
        assertEquals(BreakAction.DROP, breakAt(clump, 2, 110));
        assertEquals(BreakAction.TURN_BACK, breakAt(clump, 3, 110));
        assertEquals(BreakAction.TURN_BACK, breakAt(clump, 5, 110)); // the target junction
    }

    @Test
    void clumpStillInItsSourceDropsWhenTheSourceGoes() {
        ItemClump clump = clumpOn(5);
        assertEquals(BreakAction.DROP, breakAt(clump, 0, 101));
    }

    @Test
    void returningClumpDropsInsteadOfBouncing() {
        ItemClump clump = clumpOn(5);
        clump.returning = true;
        clump.startPos = 3.5F; // turned back in pipe 3 at tick 100
        // at tick 106 it has gone back 1.5 pipes: in pipe 2
        assertEquals(BreakAction.FINISH, breakAt(clump, 4, 106)); // behind it now
        assertEquals(BreakAction.DROP, breakAt(clump, 2, 106));
        assertEquals(BreakAction.DROP, breakAt(clump, 1, 106)); // ahead: no second turn
        assertEquals(BreakAction.DROP, breakAt(clump, 0, 106)); // its destination went away
    }

    @Test
    void pipePositionsAlongThePath() {
        byte[] path = { EAST, EAST, 1, 1 }; // 1 = UP
        assertEquals(0, ClumpTransit.indexOnPath(0, 64, 0, path, 0, 64, 0));
        assertEquals(2, ClumpTransit.indexOnPath(0, 64, 0, path, 2, 64, 0));
        assertEquals(4, ClumpTransit.indexOnPath(0, 64, 0, path, 2, 66, 0));
        assertEquals(-1, ClumpTransit.indexOnPath(0, 64, 0, path, 3, 64, 0));
        assertArrayEquals(new int[] { 2, 65, 0 }, ClumpTransit.pipeAt(0, 64, 0, path, 3));
    }
}
