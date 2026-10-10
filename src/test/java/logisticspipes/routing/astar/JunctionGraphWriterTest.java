package logisticspipes.routing.astar;

import static logisticspipes.routing.astar.TestNetworks.edge;
import static logisticspipes.routing.astar.TestNetworks.id;
import static logisticspipes.routing.astar.TestNetworks.link;
import static logisticspipes.routing.astar.TestNetworks.node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JunctionGraphWriterTest {

    private static JunctionGraphWriter line(int... weights) {
        // 1 - 2 - 3 ... with the given corridor weights
        JunctionGraphWriter w = new JunctionGraphWriter();
        for (int i = 1; i <= weights.length + 1; i++) {
            node(w, i, i * 10, 64, 0);
        }
        Map<Integer, List<EdgeSpec>> out = new HashMap<>();
        for (int i = 0; i < weights.length; i++) {
            link(out, i + 1, i + 2, weights[i]);
        }
        TestNetworks.apply(w, out);
        return w;
    }

    @Test
    void splitPreservesTotalCorridorWeight() {
        JunctionGraphWriter w = line(30);
        node(w, 3, 15, 64, 0);
        NetworkGraph before = w.graph();
        CorridorEdge oldAB = before.node(id(1)).edgeTo(id(2));
        CorridorEdge oldBA = before.node(id(2)).edgeTo(id(1));

        w.splitCorridor(id(1), id(2), id(3), 12);
        NetworkGraph g = w.graph();

        CorridorEdge a3 = g.node(id(1)).edgeTo(id(3));
        CorridorEdge b3 = g.node(id(2)).edgeTo(id(3));
        CorridorEdge threeB = g.node(id(3)).edgeTo(id(2));
        CorridorEdge threeA = g.node(id(3)).edgeTo(id(1));
        assertNotNull(a3);
        assertNotNull(threeB);
        assertEquals(30, a3.weight + threeB.weight, 1e-9);
        assertEquals(30, b3.weight + threeA.weight, 1e-9);
        assertEquals(12, a3.weight, 1e-9);
        assertEquals(12, threeA.weight, 1e-9);
        // the old corridor is gone and the halves are fresh edges
        assertNull(g.node(id(1)).edgeTo(id(2)));
        assertNull(g.edge(oldAB.id));
        assertNull(g.edge(oldBA.id));
        assertTrue(a3.version >= 1 && threeB.version >= 1);
    }

    @Test
    void mergeSumsWeights() {
        JunctionGraphWriter w = line(7, 11);
        w.mergeThrough(id(2));
        NetworkGraph g = w.graph();
        assertNull(g.node(id(2)));
        CorridorEdge merged = g.node(id(1)).edgeTo(id(3));
        CorridorEdge mergedBack = g.node(id(3)).edgeTo(id(1));
        assertNotNull(merged);
        assertNotNull(mergedBack);
        assertEquals(18, merged.weight, 1e-9);
        assertEquals(18, mergedBack.weight, 1e-9);
        assertTrue(g.sameComponent(id(1), id(3)));
    }

    @Test
    void mergeRefusesNonPassThroughJunction() {
        JunctionGraphWriter w = line(7, 11);
        node(w, 4, 20, 70, 0);
        List<EdgeSpec> specs = new ArrayList<>(TestNetworks.specsOf(w.graph(), 2));
        specs.add(edge(4, 5));
        w.setEdges(id(2), specs);
        w.mergeThrough(id(2)); // degree 3: must be ignored
        assertNotNull(w.graph().node(id(2)));
    }

    @Test
    void unchangedCorridorKeepsIdAndVersion() {
        JunctionGraphWriter w = line(5, 5);
        NetworkGraph g1 = w.graph();
        CorridorEdge e1 = g1.node(id(2)).edgeTo(id(3));
        w.setEdges(id(2), TestNetworks.specsOf(g1, 2));
        NetworkGraph g2 = w.graph();
        assertSame(g1, g2, "no-op edit must not publish a new snapshot");

        w.setEdges(id(2), TestNetworks.withWeight(g2, 2, 3, 9));
        CorridorEdge e2 = w.graph().node(id(2)).edgeTo(id(3));
        assertEquals(e1.id, e2.id);
        assertEquals(e1.version + 1, e2.version);
        assertEquals(9, e2.weight, 1e-9);
        // untouched corridor keeps its version
        assertEquals(g1.node(id(2)).edgeTo(id(1)).version, w.graph().node(id(2)).edgeTo(id(1)).version);
    }

    @Test
    void severingTheOnlyConnectionSplitsComponents() {
        // two triangles joined by a single bridge 3-4
        JunctionGraphWriter w = new JunctionGraphWriter();
        for (int i = 1; i <= 6; i++) {
            node(w, i, i * 4, 64, 0);
        }
        Map<Integer, List<EdgeSpec>> out = new HashMap<>();
        link(out, 1, 2, 4);
        link(out, 2, 3, 4);
        link(out, 3, 1, 8);
        link(out, 4, 5, 4);
        link(out, 5, 6, 4);
        link(out, 6, 4, 8);
        link(out, 3, 4, 4);
        TestNetworks.apply(w, out);
        assertTrue(w.graph().sameComponent(id(1), id(6)));

        // remove a non-bridge corridor: still one component
        w.setEdges(id(1), Collections.singletonList(edge(2, 4)));
        w.setEdges(id(3), Arrays.asList(edge(2, 4), edge(4, 4)));
        assertTrue(w.graph().sameComponent(id(1), id(6)));

        // cut the bridge in both directions
        w.setEdges(id(3), Collections.singletonList(edge(2, 4)));
        w.setEdges(id(4), Arrays.asList(edge(5, 4), edge(6, 8)));
        NetworkGraph g = w.graph();
        assertFalse(g.sameComponent(id(1), id(6)));
        assertFalse(g.sameComponent(id(3), id(4)));
        assertTrue(g.sameComponent(id(1), id(3)));
        assertTrue(g.sameComponent(id(4), id(6)));

        // and reconnect
        w.setEdges(id(3), Arrays.asList(edge(2, 4), edge(4, 4)));
        assertTrue(w.graph().sameComponent(id(1), id(6)));
    }

    @Test
    void cutWithADetourNeedsNoFullRelabel() {
        // square 1-2-3-4-1: cutting one side leaves a detour
        JunctionGraphWriter w = new JunctionGraphWriter();
        for (int i = 1; i <= 4; i++) {
            node(w, i, i * 5, 64, 0);
        }
        Map<Integer, List<EdgeSpec>> out = new HashMap<>();
        link(out, 1, 2, 5);
        link(out, 2, 3, 5);
        link(out, 3, 4, 5);
        link(out, 4, 1, 5);
        TestNetworks.apply(w, out);
        long componentBefore = w.graph().componentIdentity(1);

        w.setEdges(id(1), Collections.singletonList(edge(4, 5)));
        w.setEdges(id(2), Collections.singletonList(edge(3, 5)));
        assertEquals(0, w.fullRelabels());
        assertTrue(w.detourChecks() > 0);
        assertTrue(w.graph().sameComponent(id(1), id(2)));
        assertEquals(componentBefore, w.graph().componentIdentity(2));

        // now 1-4-3-2 is a line; cutting 3-4 really splits it
        w.setEdges(id(3), Collections.singletonList(edge(2, 5)));
        w.setEdges(id(4), Collections.singletonList(edge(1, 5)));
        assertEquals(1, w.fullRelabels());
        assertFalse(w.graph().sameComponent(id(1), id(2)));
        assertTrue(w.graph().sameComponent(id(1), id(4)));
        assertTrue(w.graph().sameComponent(id(2), id(3)));
    }

    @Test
    void reusedIdDoesNotInheritTheOldComponent() {
        JunctionGraphWriter w = line(5, 5, 5, 5);
        // remove every junction's link through 3 by removing 3, then re-add id 3 somewhere unconnected
        for (int i = 1; i <= 5; i++) {
            assertTrue(w.graph().sameComponent(id(1), id(i)));
        }
        w.removeJunction(id(1));
        w.removeJunction(id(2));
        node(w, 1, 900, 1, 900);
        node(w, 2, 950, 1, 900);
        NetworkGraph g = w.graph();
        assertFalse(g.sameComponent(id(1), id(3)));
        assertFalse(g.sameComponent(id(2), id(3)));
        assertFalse(g.sameComponent(id(1), id(2)));
        assertTrue(g.sameComponent(id(3), id(5)));
    }

    @Test
    void removingAJunctionDropsCorridorsIntoIt() {
        JunctionGraphWriter w = line(5, 5);
        w.removeJunction(id(2));
        NetworkGraph g = w.graph();
        assertNull(g.node(id(2)));
        assertTrue(g.node(id(1)).edges.isEmpty());
        assertTrue(g.node(id(3)).edges.isEmpty());
        assertFalse(g.sameComponent(id(1), id(3)));

        // a new junction re-using the id does not inherit the old corridors
        node(w, 2, 500, 10, 500);
        assertFalse(w.graph().sameComponent(id(1), id(2)));
    }

    @Test
    void chunkIndexFindsCorridorsThroughAChunk() {
        JunctionGraphWriter w = new JunctionGraphWriter();
        node(w, 1, 0, 64, 0);
        node(w, 2, 40, 64, 0);
        long c0 = ChunkEdgeIndex.chunkKeyForBlock(0, 0, 0);
        long c1 = ChunkEdgeIndex.chunkKeyForBlock(0, 20, 0);
        long c2 = ChunkEdgeIndex.chunkKeyForBlock(0, 40, 0);
        w.setEdges(
                id(1),
                Collections.singletonList(
                        new EdgeSpec(id(2), 40, RoutingFlags.ALL, null, 40, 5, 4, new long[] { c0, c1, c2 })));
        long edgeId = w.graph().node(id(1)).edgeTo(id(2)).id;
        assertTrue(w.chunkIndex().edgesIn(c1).contains(edgeId));

        // the corridor is re-routed around chunk c1
        long other = ChunkEdgeIndex.chunkKeyForBlock(0, 20, 16);
        w.setEdges(
                id(1),
                Collections.singletonList(
                        new EdgeSpec(id(2), 44, RoutingFlags.ALL, null, 44, 5, 4, new long[] { c0, other, c2 })));
        assertFalse(w.chunkIndex().edgesIn(c1).contains(edgeId));
        assertTrue(w.chunkIndex().edgesIn(other).contains(edgeId));

        w.removeJunction(id(1));
        assertTrue(w.chunkIndex().edgesIn(other).isEmpty());
    }

    @Test
    void stampsTrackImprovementsSeparatelyFromChanges() {
        JunctionGraphWriter w = line(5, 5);
        NetworkGraph g1 = w.graph();
        long imp = g1.improvementStamp(1);
        long chg = g1.changeStamp(1);

        // worsening: heavier corridor
        w.setEdges(id(2), TestNetworks.withWeight(g1, 2, 3, 50));
        NetworkGraph g2 = w.graph();
        assertEquals(imp, g2.improvementStamp(1));
        assertNotEquals(chg, g2.changeStamp(1));

        // improvement: cheaper corridor
        w.setEdges(id(2), TestNetworks.withWeight(g2, 2, 3, 1));
        assertNotEquals(imp, w.graph().improvementStamp(1));
    }

    @Test
    void asyncWriterPublishes() throws Exception {
        JunctionGraphWriter w = new JunctionGraphWriter();
        w.startAsync(r -> new Thread(r, "test-writer"));
        try {
            node(w, 1, 0, 0, 0);
            node(w, 2, 5, 0, 0);
            w.setEdges(id(1), Collections.singletonList(edge(2, 5)));
            long deadline = System.currentTimeMillis() + 5000;
            while (w.graph().node(id(1)) == null || w.graph().node(id(1)).edges.isEmpty()) {
                assertTrue(System.currentTimeMillis() < deadline, "writer thread did not publish");
                Thread.sleep(5);
            }
            assertTrue(w.graph().sameComponent(id(1), id(2)));
        } finally {
            w.stopAsync();
        }
    }
}
