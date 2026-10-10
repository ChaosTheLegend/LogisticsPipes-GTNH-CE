package logisticspipes.routing.astar;

import static logisticspipes.routing.astar.TestNetworks.edge;
import static logisticspipes.routing.astar.TestNetworks.id;
import static logisticspipes.routing.astar.TestNetworks.link;
import static logisticspipes.routing.astar.TestNetworks.node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class RouteCacheTest {

    /**
     * <pre>
     *   1 --5-- 2 --5-- 3
     *   |               |
     *   5               5
     *   |               |
     *   4 ----- 5 ----- 6      (4-5 and 5-6 weight 5)
     * </pre>
     */
    private static JunctionGraphWriter ladder() {
        JunctionGraphWriter w = new JunctionGraphWriter();
        node(w, 1, 0, 0, 0);
        node(w, 2, 5, 0, 0);
        node(w, 3, 10, 0, 0);
        node(w, 4, 0, 0, 5);
        node(w, 5, 5, 0, 5);
        node(w, 6, 10, 0, 5);
        Map<Integer, List<EdgeSpec>> out = new HashMap<>();
        link(out, 1, 2, 5);
        link(out, 2, 3, 5);
        link(out, 1, 4, 5);
        link(out, 4, 5, 5);
        link(out, 5, 6, 5);
        link(out, 3, 6, 5);
        TestNetworks.apply(w, out);
        return w;
    }

    @Test
    void repeatedQueryIsACacheHit() {
        JunctionRoutingEngine engine = new JunctionRoutingEngine(ladder());
        RouteCacheEntry a = engine.findRoute(id(1), id(3));
        RouteCacheEntry b = engine.findRoute(id(1), id(3));
        assertSame(a, b);
        assertEquals(1, engine.pairSearches());
        assertEquals(a.edgeIds().length, a.edgeVersionsAtCacheTime().length);
    }

    @Test
    void worseningOneEdgeInvalidatesOnlyRoutesUsingIt() {
        JunctionGraphWriter w = ladder();
        JunctionRoutingEngine engine = new JunctionRoutingEngine(w);
        RouteCacheEntry top = engine.findRoute(id(1), id(2)); // uses 1->2
        RouteCacheEntry bottom = engine.findRoute(id(4), id(5)); // uses 4->5 only
        long searches = engine.pairSearches();

        // make 1->2 more expensive (a worsening change)
        w.setEdges(id(1), TestNetworks.withWeight(w.graph(), 1, 2, 50));
        NetworkGraph g = w.graph();
        assertFalse(top.isValid(g), "route over the changed corridor must be stale");
        assertTrue(bottom.isValid(g), "route not using the changed corridor stays valid");

        assertSame(bottom, engine.findRoute(id(4), id(5)));
        assertEquals(searches, engine.pairSearches());
        RouteCacheEntry newTop = engine.findRoute(id(1), id(2));
        assertNotSame(top, newTop);
        assertEquals(searches + 1, engine.pairSearches());
        // 1-4-5-6-3-2 = 25 is now cheaper than the direct 50
        assertEquals(25, newTop.totalWeight, 1e-9);
    }

    @Test
    void improvementElsewhereInvalidatesRoutesOfTheComponent() {
        JunctionGraphWriter w = ladder();
        JunctionRoutingEngine engine = new JunctionRoutingEngine(w);
        RouteCacheEntry r = engine.findRoute(id(1), id(6)); // 15 via either side
        assertEquals(15, r.totalWeight, 1e-9);

        // a new shortcut 1 -> 6 that the cached route does not use
        List<EdgeSpec> specs = new ArrayList<>(TestNetworks.specsOf(w.graph(), 1));
        specs.add(edge(6, 3));
        w.setEdges(id(1), specs);
        assertFalse(r.isValid(w.graph()), "a shorter corridor elsewhere must invalidate the route");
        assertEquals(3, engine.findRoute(id(1), id(6)).totalWeight, 1e-9);
    }

    @Test
    void farAwayImprovementKeepsTheRoute() {
        JunctionGraphWriter w = ladder();
        // a long tail far away from the ladder: 3 -> 20 -> 21 -> 22
        node(w, 20, 200, 0, 0);
        node(w, 21, 300, 0, 0);
        node(w, 22, 300, 0, 100);
        List<EdgeSpec> three = new ArrayList<>(TestNetworks.specsOf(w.graph(), 3));
        three.add(edge(20, 190));
        w.setEdges(id(3), three);
        Map<Integer, List<EdgeSpec>> out = new HashMap<>();
        out.put(20, new ArrayList<>(Collections.singletonList(edge(3, 190))));
        link(out, 20, 21, 100);
        link(out, 21, 22, 100);
        TestNetworks.apply(w, out);

        JunctionRoutingEngine engine = new JunctionRoutingEngine(w);
        RouteCacheEntry r = engine.findRoute(id(1), id(6));
        long searches = engine.pairSearches();

        // shortcut 20 -> 22 far from the 1 -> 6 route: provably cannot beat a route of length 15
        List<EdgeSpec> twenty = new ArrayList<>(TestNetworks.specsOf(w.graph(), 20));
        twenty.add(edge(22, 150));
        w.setEdges(id(20), twenty);
        assertTrue(r.isValid(w.graph()), "far improvement must not invalidate");
        assertSame(r, engine.findRoute(id(1), id(6)));
        assertEquals(searches, engine.pairSearches());

        // but a route that could use it is re-searched
        RouteCacheEntry far = engine.findRoute(id(20), id(22));
        List<EdgeSpec> twentyOne = new ArrayList<>(TestNetworks.specsOf(w.graph(), 20));
        twentyOne.removeIf(e -> e.to.index() == 22);
        twentyOne.add(edge(22, 101));
        w.setEdges(id(20), twentyOne);
        assertFalse(far.isValid(w.graph()));
        assertEquals(101, engine.findRoute(id(20), id(22)).totalWeight, 1e-9);
    }

    @Test
    void editsInOtherComponentsDoNotTouchTheCache() {
        JunctionGraphWriter w = ladder();
        node(w, 10, 100, 0, 100);
        node(w, 11, 110, 0, 100);
        Map<Integer, List<EdgeSpec>> out = new HashMap<>();
        link(out, 10, 11, 10);
        TestNetworks.apply(w, out);
        JunctionRoutingEngine engine = new JunctionRoutingEngine(w);
        RouteCacheEntry r = engine.findRoute(id(1), id(6));
        w.setEdges(id(10), Collections.singletonList(edge(11, 1)));
        assertTrue(r.isValid(w.graph()));
        assertSame(r, engine.findRoute(id(1), id(6)));
    }

    @Test
    void singleFlightRunsExactlyOneSearch() throws Exception {
        Random rnd = new Random(3);
        JunctionGraphWriter w = new JunctionGraphWriter();
        TestNetworks.buildRandom(w, rnd, 2500, 0);
        JunctionRoutingEngine engine = new JunctionRoutingEngine(w);
        RouteCacheEntry first = engine.findRoute(id(1), id(2500));
        assertTrue(first.isReachable());

        // invalidate: every corridor of the route gets heavier
        for (CorridorEdge e : first.routes.get(0).edges()) {
            w.setEdges(e.from, TestNetworks.withWeight(w.graph(), e.from.index(), e.to.index(), e.weight + 1));
        }
        assertFalse(first.isValid(w.graph()));
        long before = engine.pairSearches();

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<RouteCacheEntry>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return engine.findRoute(id(1), id(2500));
            }));
        }
        start.countDown();
        Set<RouteCacheEntry> distinct = new HashSet<>();
        for (Future<RouteCacheEntry> f : results) {
            distinct.add(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertEquals(1, engine.pairSearches() - before, "exactly one search for the pair");
        assertEquals(1, distinct.size(), "every caller got the same entry");
    }

    @Test
    void staleButTraversableRouteIsServedWhileRefreshing() throws Exception {
        JunctionGraphWriter w = ladder();
        JunctionRoutingEngine engine = new JunctionRoutingEngine(w);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        engine.setExecutor(pool);
        try {
            RouteCacheEntry r = engine.findRoute(id(1), id(3));
            w.setEdges(id(1), TestNetworks.withWeight(w.graph(), 1, 2, 7));
            RouteCacheEntry served = engine.findRoute(id(1), id(3));
            assertSame(r, served, "the old route still exists, so it is served immediately");
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
            RouteCacheEntry refreshed = engine.findRoute(id(1), id(3));
            assertNotSame(r, refreshed);
            assertEquals(12, refreshed.totalWeight, 1e-9);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void brokenRouteIsNeverServedStale() {
        JunctionGraphWriter w = ladder();
        JunctionRoutingEngine engine = new JunctionRoutingEngine(w);
        engine.setExecutor(Executors.newSingleThreadExecutor());
        RouteCacheEntry r = engine.findRoute(id(1), id(3));
        // cut 1 -> 2: the cached route cannot be travelled anymore
        List<EdgeSpec> specs = new ArrayList<>(TestNetworks.specsOf(w.graph(), 1));
        specs.removeIf(e -> e.to.index() == 2);
        w.setEdges(id(1), specs);
        RouteCacheEntry now = engine.findRoute(id(1), id(3));
        assertNotSame(r, now);
        assertEquals(20, now.totalWeight, 1e-9); // 1-4-5-6-3
    }

    /**
     * Two islands joined by a new pipe: each end publishes its corridor separately, so for a moment only 2 -> 3 exists.
     * "Unreachable" cached in that window must not be served once 3 -> 2 exists (it left pipes unpowered, bug 7).
     */
    @Test
    void unreachableIsNeverServedStaleAfterAMerge() {
        JunctionGraphWriter w = new JunctionGraphWriter();
        node(w, 1, 0, 0, 0);
        node(w, 2, 5, 0, 0);
        node(w, 3, 10, 0, 0);
        node(w, 4, 15, 0, 0);
        Map<Integer, List<EdgeSpec>> out = new HashMap<>();
        link(out, 1, 2, 5);
        link(out, 3, 4, 5);
        TestNetworks.apply(w, out);
        JunctionRoutingEngine engine = new JunctionRoutingEngine(w);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        engine.setExecutor(pool);
        try {
            List<EdgeSpec> two = new ArrayList<>(TestNetworks.specsOf(w.graph(), 2));
            two.add(edge(3, 5));
            w.setEdges(id(2), two);
            assertFalse(engine.findRoute(id(4), id(1)).isReachable(), "half-merged: no way back yet");
            assertFalse(
                    engine.findRoutesToTargets(id(4), Collections.singleton(id(1))).get(id(1)).isReachable(),
                    "half-merged: no way back yet");

            List<EdgeSpec> three = new ArrayList<>(TestNetworks.specsOf(w.graph(), 3));
            three.add(edge(2, 5));
            w.setEdges(id(3), three);
            assertTrue(engine.findRoute(id(4), id(1)).isReachable());
            assertTrue(engine.findRoutesToTargets(id(4), Collections.singleton(id(1))).get(id(1)).isReachable());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void oneToManyFillsThePairCache() {
        JunctionGraphWriter w = ladder();
        JunctionRoutingEngine engine = new JunctionRoutingEngine(w);
        Set<JunctionId> targets = new HashSet<>();
        targets.add(id(3));
        targets.add(id(5));
        targets.add(id(6));
        Map<JunctionId, RouteCacheEntry> all = engine.findRoutesToTargets(id(1), targets);
        assertEquals(1, engine.multiSearches());
        assertEquals(0, engine.pairSearches());
        assertEquals(10, all.get(id(3)).totalWeight, 1e-9);
        assertEquals(10, all.get(id(5)).totalWeight, 1e-9);
        assertEquals(15, all.get(id(6)).totalWeight, 1e-9);
        assertSame(all.get(id(5)), engine.findRoute(id(1), id(5)));
        assertEquals(0, engine.pairSearches());
        // a second one-to-many over cached targets needs no search
        engine.findRoutesToTargets(id(1), targets);
        assertEquals(1, engine.multiSearches());
    }

    /**
     * Fuzz: random edits (weights up and down, corridors added and removed, junctions switched off and on, removed and
     * re-added), with cached queries after each batch compared to a from-scratch reference.
     */
    @Test
    void cachedRoutesMatchFreshSearchUnderRandomEdits() {
        Random rnd = new Random(2024);
        for (int round = 0; round < 6; round++) {
            JunctionGraphWriter w = new JunctionGraphWriter();
            int count = TestNetworks.buildRandom(w, rnd, 200, 0.2);
            JunctionRoutingEngine engine = new JunctionRoutingEngine(w);
            List<int[]> pairs = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                pairs.add(new int[] { 1 + rnd.nextInt(count), 1 + rnd.nextInt(count) });
            }
            Map<Integer, int[]> removed = new HashMap<>();
            for (int step = 0; step < 40; step++) {
                for (int k = 0; k < 1 + rnd.nextInt(4); k++) {
                    randomEdit(w, rnd, count, removed);
                }
                NetworkGraph g = w.graph();
                for (int[] p : pairs) {
                    if (p[0] == p[1] || g.node(id(p[0])) == null) {
                        continue;
                    }
                    RouteCacheEntry entry = engine.findRoute(id(p[0]), id(p[1]));
                    for (int flag : new int[] { RoutingFlags.CAN_ROUTE_TO, RoutingFlags.CAN_REQUEST_FROM }) {
                        Double expected = TestNetworks.referenceDistances(g, p[0], flag).get(p[1]);
                        double actual = Double.POSITIVE_INFINITY;
                        for (RouteLabel l : entry.routes) {
                            if ((l.flags & flag) != 0) {
                                actual = Math.min(actual, l.distance);
                            }
                        }
                        if (expected == null) {
                            assertTrue(
                                    Double.isInfinite(actual),
                                    "round " + round
                                            + " step "
                                            + step
                                            + " "
                                            + p[0]
                                            + "->"
                                            + p[1]
                                            + " flag "
                                            + flag
                                            + " should be unreachable, got "
                                            + actual);
                        } else {
                            assertEquals(
                                    expected,
                                    actual,
                                    1e-9,
                                    "round " + round + " step " + step + " " + p[0] + "->" + p[1] + " flag " + flag);
                        }
                    }
                }
            }
            assertTrue(engine.cacheHits() > 0, "the cache should have answered some queries");
        }
    }

    private static void randomEdit(JunctionGraphWriter w, Random rnd, int count, Map<Integer, int[]> removed) {
        NetworkGraph g = w.graph();
        int n = 1 + rnd.nextInt(count);
        JunctionNode node = g.node(id(n));
        int kind = rnd.nextInt(10);
        if (node == null) {
            int[] pos = removed.remove(n);
            if (pos != null) {
                node(w, n, pos[0], pos[1], pos[2]);
                int m = 1 + rnd.nextInt(count);
                JunctionNode other = g.node(id(m));
                if (other != null && m != n) {
                    int man = Math.abs(pos[0] - other.x) + Math.abs(pos[1] - other.y) + Math.abs(pos[2] - other.z);
                    w.setEdges(id(n), Collections.singletonList(edge(m, man + 1)));
                    List<EdgeSpec> back = new ArrayList<>(TestNetworks.specsOf(g, m));
                    back.removeIf(e -> e.to.index() == n);
                    back.add(edge(n, man + 2));
                    w.setEdges(id(m), back);
                }
            }
            return;
        }
        List<EdgeSpec> specs = new ArrayList<>(TestNetworks.specsOf(g, n));
        switch (kind) {
            case 0:
            case 1:
            case 2: { // weight change
                if (specs.isEmpty()) {
                    return;
                }
                int i = rnd.nextInt(specs.size());
                EdgeSpec e = specs.get(i);
                JunctionNode to = g.node(e.to);
                int man = to == null ? 0 : NetworkGraph.manhattan(node, to);
                double weight = Math.max(man, e.weight + (rnd.nextBoolean() ? 1 : -1) * rnd.nextInt(10));
                specs.set(i, new EdgeSpec(e.to, weight, e.flags));
                w.setEdges(id(n), specs);
                return;
            }
            case 3: { // remove a corridor
                if (!specs.isEmpty()) {
                    specs.remove(rnd.nextInt(specs.size()));
                    w.setEdges(id(n), specs);
                }
                return;
            }
            case 4:
            case 5: { // add a corridor
                int m = 1 + rnd.nextInt(count);
                JunctionNode to = g.node(id(m));
                if (to == null || m == n) {
                    return;
                }
                specs.removeIf(e -> e.to.index() == m);
                specs.add(edge(m, NetworkGraph.manhattan(node, to) + rnd.nextInt(5), TestNetworks.randomFlags(rnd)));
                w.setEdges(id(n), specs);
                return;
            }
            case 6: { // flag change
                if (specs.isEmpty()) {
                    return;
                }
                int i = rnd.nextInt(specs.size());
                EdgeSpec e = specs.get(i);
                specs.set(i, new EdgeSpec(e.to, e.weight, TestNetworks.randomFlags(rnd)));
                w.setEdges(id(n), specs);
                return;
            }
            case 7:
            case 8: // chunk unload / load
                w.setActive(id(n), !node.active);
                return;
            default: // pipe broken
                removed.put(n, new int[] { node.x, node.y, node.z });
                w.removeJunction(id(n));
        }
    }
}
