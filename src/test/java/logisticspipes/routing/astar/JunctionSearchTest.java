package logisticspipes.routing.astar;

import static logisticspipes.routing.astar.TestNetworks.edge;
import static logisticspipes.routing.astar.TestNetworks.id;
import static logisticspipes.routing.astar.TestNetworks.link;
import static logisticspipes.routing.astar.TestNetworks.node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.ToDoubleBiFunction;

import org.junit.jupiter.api.Test;

class JunctionSearchTest {

    private static final int[] SINGLE_FLAGS = { RoutingFlags.CAN_ROUTE_TO, RoutingFlags.CAN_REQUEST_FROM,
            RoutingFlags.CAN_POWER_FROM, RoutingFlags.CAN_POWER_SUB_SYSTEM_FROM };

    /** Two junctions connected by two corridors of different length (through 2 and through 3). */
    private static JunctionGraphWriter loop() {
        JunctionGraphWriter w = new JunctionGraphWriter();
        node(w, 1, 0, 64, 0);
        node(w, 2, 10, 64, 5);
        node(w, 3, 10, 64, -5);
        node(w, 4, 20, 64, 0);
        Map<Integer, List<EdgeSpec>> out = new HashMap<>();
        link(out, 1, 2, 15);
        link(out, 2, 4, 15);
        link(out, 1, 3, 20);
        link(out, 3, 4, 20);
        TestNetworks.apply(w, out);
        return w;
    }

    @Test
    void cheaperCorridorOfALoopIsChosenAndFlipsOnWeightChange() {
        JunctionGraphWriter w = loop();
        JunctionRoutingEngine engine = new JunctionRoutingEngine(w);
        RouteCacheEntry r = engine.findRoute(id(1), id(4));
        assertEquals(Arrays.asList(id(1), id(2), id(4)), r.path);
        assertEquals(30, r.totalWeight, 1e-9);

        // the chosen corridor gets much longer: the other side of the loop must win now
        w.setEdges(id(2), TestNetworks.withWeight(w.graph(), 2, 4, 100));
        RouteCacheEntry r2 = engine.findRoute(id(1), id(4));
        assertEquals(Arrays.asList(id(1), id(3), id(4)), r2.path);
        assertEquals(40, r2.totalWeight, 1e-9);

        // and back when the other side gets longer still
        w.setEdges(id(3), TestNetworks.withWeight(w.graph(), 3, 4, 200));
        RouteCacheEntry r3 = engine.findRoute(id(1), id(4));
        assertEquals(Arrays.asList(id(1), id(2), id(4)), r3.path);
        assertEquals(115, r3.totalWeight, 1e-9);
    }

    @Test
    void differentComponentsAreUnreachableWithoutSearch() {
        JunctionGraphWriter w = loop();
        node(w, 9, 1000, 64, 1000);
        JunctionRoutingEngine engine = new JunctionRoutingEngine(w);
        RouteCacheEntry r = engine.findRoute(id(1), id(9));
        assertFalse(r.isReachable());
        assertEquals(0, engine.totalSearches());
    }

    @Test
    void filteredShortRouteKeepsUnfilteredAlternative() {
        JunctionGraphWriter w = loop();
        List<Object> firewall = Collections.singletonList("firewall");
        List<EdgeSpec> specs = new ArrayList<>();
        for (CorridorEdge e : w.graph().node(id(2)).edges) {
            specs.add(new EdgeSpec(e.to, e.weight, e.flags, e.to.index() == 4 ? firewall : null, 15, 0, 0, null));
        }
        w.setEdges(id(2), specs);
        SearchResult r = JunctionSearch.search(w.graph(), id(1), Collections.singleton(id(4)), null);
        List<RouteLabel> routes = r.routesTo(id(4));
        assertEquals(2, routes.size(), "short filtered route and long unfiltered route: " + routes);
        assertEquals(30, routes.get(0).distance, 1e-9);
        assertEquals(firewall, routes.get(0).filters);
        assertEquals(40, routes.get(1).distance, 1e-9);
        assertTrue(routes.get(1).filters.isEmpty());
    }

    @Test
    void flagsAreIntersectedAlongTheRoute() {
        // 1 -> 2 carries everything, 2 -> 3 only power; 1 -> 3 direct is long but routable
        JunctionGraphWriter w = new JunctionGraphWriter();
        node(w, 1, 0, 0, 0);
        node(w, 2, 5, 0, 0);
        node(w, 3, 10, 0, 0);
        w.setEdges(id(1), Arrays.asList(edge(2, 5), edge(3, 50)));
        w.setEdges(id(2), Collections.singletonList(edge(3, 5, RoutingFlags.CAN_POWER_FROM)));
        SearchResult r = JunctionSearch.search(w.graph(), id(1), Collections.singleton(id(3)), null);
        assertEquals(10, r.distanceTo(id(3), RoutingFlags.CAN_POWER_FROM), 1e-9);
        assertEquals(50, r.distanceTo(id(3), RoutingFlags.CAN_ROUTE_TO), 1e-9);
    }

    @Test
    void randomGraphsMatchReferencePerFlag() {
        Random rnd = new Random(1234);
        for (int round = 0; round < 20; round++) {
            JunctionGraphWriter w = new JunctionGraphWriter();
            TestNetworks.buildRandom(w, rnd, 60 + rnd.nextInt(60), 0.3);
            NetworkGraph g = w.graph();
            List<Integer> nodes = TestNetworks.allNodes(g);
            for (int q = 0; q < 10; q++) {
                int s = nodes.get(rnd.nextInt(nodes.size()));
                int t = nodes.get(rnd.nextInt(nodes.size()));
                if (s == t) {
                    continue;
                }
                SearchResult single = JunctionSearch
                        .search(g, id(s), Collections.singleton(id(t)), g.manhattanHeuristic());
                for (int flag : SINGLE_FLAGS) {
                    Double expected = TestNetworks.referenceDistances(g, s, flag).get(t);
                    double actual = single.distanceTo(id(t), flag);
                    if (expected == null) {
                        assertTrue(Double.isInfinite(actual), "flag " + flag + " should be unreachable");
                    } else {
                        assertEquals(expected, actual, 1e-9, "round " + round + " " + s + "->" + t + " flag " + flag);
                    }
                }
            }
        }
    }

    /**
     * Shared-routine parity: heuristic on/off and one/many/all targets must give identical routes; the heuristic and
     * the target set may only change how much of the graph is explored.
     */
    @Test
    void heuristicAndTargetSetNeverChangeTheAnswer() {
        Random rnd = new Random(99);
        for (int round = 0; round < 15; round++) {
            JunctionGraphWriter w = new JunctionGraphWriter();
            TestNetworks.buildRandom(w, rnd, 150, 0.2);
            NetworkGraph g = w.graph();
            List<Integer> nodes = TestNetworks.allNodes(g);
            int s = nodes.get(rnd.nextInt(nodes.size()));
            Set<JunctionId> targets = new HashSet<>();
            while (targets.size() < 6) {
                int t = nodes.get(rnd.nextInt(nodes.size()));
                if (t != s) {
                    targets.add(id(t));
                }
            }
            ToDoubleBiFunction<JunctionId, JunctionId> h = g.manhattanHeuristic();
            SearchResult multiPlain = JunctionSearch.search(g, id(s), targets, null);
            SearchResult multiAStar = JunctionSearch.search(g, id(s), targets, h);
            SearchResult all = JunctionSearch.search(g, id(s), null, null);
            for (JunctionId t : targets) {
                SearchResult singlePlain = JunctionSearch.search(g, id(s), Collections.singleton(t), null);
                SearchResult singleAStar = JunctionSearch.search(g, id(s), Collections.singleton(t), h);
                String expected = describe(singlePlain.routesTo(t));
                assertEquals(expected, describe(singleAStar.routesTo(t)), "A* vs Dijkstra, target " + t);
                assertEquals(expected, describe(multiPlain.routesTo(t)), "one-to-many vs one-to-one, target " + t);
                assertEquals(expected, describe(multiAStar.routesTo(t)), "one-to-many A*, target " + t);
                assertEquals(expected, describe(all.routesTo(t)), "exhaustive, target " + t);
            }
        }
    }

    private static String describe(List<RouteLabel> routes) {
        StringBuilder sb = new StringBuilder();
        for (RouteLabel l : routes) {
            // weights are random reals, so equal distances mean equal routes; paths are compared too
            sb.append(String.format("%.6f", l.distance)).append('/').append(l.flags).append('/').append(l.newFlags)
                    .append('/').append(l.path()).append(';');
        }
        return sb.toString();
    }

    @Test
    void oneToManyStopsEarlyForNearbyTargets() {
        Random rnd = new Random(7);
        JunctionGraphWriter w = new JunctionGraphWriter();
        TestNetworks.buildRandom(w, rnd, 900, 0);
        NetworkGraph g = w.graph();
        // source in a corner, targets its near neighbours on the 30x30 grid
        Set<JunctionId> targets = new HashSet<>(Arrays.asList(id(2), id(31), id(32), id(3)));
        SearchResult near = JunctionSearch.search(g, id(1), targets, null);
        SearchResult full = JunctionSearch.search(g, id(1), null, null);
        for (JunctionId t : targets) {
            assertFalse(near.routesTo(t).isEmpty(), "target " + t + " reachable");
        }
        assertTrue(
                near.settledNodes < full.settledNodes,
                "one-to-many settled " + near.settledNodes + " of " + full.settledNodes);
        assertTrue(near.settledNodes < full.settledNodes / 4, "should stop far before exhausting the graph");
    }

    @Test
    void aStarExploresLessThanDijkstra() {
        Random rnd = new Random(8);
        JunctionGraphWriter w = new JunctionGraphWriter();
        TestNetworks.buildRandom(w, rnd, 900, 0);
        NetworkGraph g = w.graph();
        Set<JunctionId> target = Collections.singleton(id(450));
        SearchResult dijkstra = JunctionSearch.search(g, id(15), target, null);
        SearchResult astar = JunctionSearch.search(g, id(15), target, g.manhattanHeuristic());
        assertEquals(
                dijkstra.distanceTo(id(450), RoutingFlags.CAN_ROUTE_TO),
                astar.distanceTo(id(450), RoutingFlags.CAN_ROUTE_TO),
                1e-9);
        assertTrue(astar.settledNodes <= dijkstra.settledNodes, astar.settledNodes + " vs " + dijkstra.settledNodes);
    }

    @Test
    void manhattanHeuristicIsAdmissible() {
        Random rnd = new Random(42);
        for (int round = 0; round < 10; round++) {
            JunctionGraphWriter w = new JunctionGraphWriter();
            TestNetworks.buildRandom(w, rnd, 120, 0);
            // include cheap "teleport" corridors (tesseract-like) that shrink the per-block cost
            NetworkGraph g0 = w.graph();
            List<Integer> nodes = TestNetworks.allNodes(g0);
            for (int k = 0; k < 3; k++) {
                int a = nodes.get(rnd.nextInt(nodes.size()));
                int b = nodes.get(rnd.nextInt(nodes.size()));
                if (a != b) {
                    List<EdgeSpec> specs = new ArrayList<>(TestNetworks.specsOf(w.graph(), a));
                    specs.removeIf(e -> e.to.index() == b);
                    specs.add(edge(b, 1));
                    w.setEdges(id(a), specs);
                }
            }
            NetworkGraph g = w.graph();
            ToDoubleBiFunction<JunctionId, JunctionId> h = g.manhattanHeuristic();
            for (int q = 0; q < 30; q++) {
                int s = nodes.get(rnd.nextInt(nodes.size()));
                Map<Integer, Double> dist = TestNetworks.referenceDistances(g, s, RoutingFlags.CAN_ROUTE_TO);
                for (Map.Entry<Integer, Double> e : dist.entrySet()) {
                    double estimate = h.applyAsDouble(id(s), id(e.getKey()));
                    assertTrue(
                            estimate <= e.getValue() + 1e-9,
                            "h(" + s + "," + e.getKey() + ")=" + estimate + " > " + e.getValue());
                }
            }
        }
    }

    @Test
    void inactiveJunctionsAreNotRoutedThrough() {
        JunctionGraphWriter w = loop();
        w.setActive(id(2), false);
        SearchResult r = JunctionSearch.search(w.graph(), id(1), Collections.singleton(id(4)), null);
        assertEquals(40, r.distanceTo(id(4), RoutingFlags.CAN_ROUTE_TO), 1e-9);
        w.setActive(id(4), false);
        r = JunctionSearch.search(w.graph(), id(1), Collections.singleton(id(4)), null);
        assertTrue(r.routesTo(id(4)).isEmpty());
    }
}
