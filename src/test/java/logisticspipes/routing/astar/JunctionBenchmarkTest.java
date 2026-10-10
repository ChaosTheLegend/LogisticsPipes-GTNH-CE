package logisticspipes.routing.astar;

import static logisticspipes.routing.astar.TestNetworks.id;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Latency benchmarks on the synthetic 50k-pipe network of {@link JunctionPerfTest}. Everything runs through the engine
 * in synchronous mode (graph edits are applied on the calling thread), so edit numbers include the snapshot publish.
 * The in-world corridor re-scan that precedes an edit in game is not part of these numbers.
 */
class JunctionBenchmarkTest {

    private static JunctionPerfTest.Network net;
    private static List<Integer> nodes;

    @BeforeAll
    static void build() {
        net = JunctionPerfTest.generate(720, 20, 12);
        nodes = TestNetworks.allNodes(net.writer.graph());
        NetworkGraph g = net.writer.graph();
        System.out.printf(
                "[bench] network: %d pipes -> %d junctions, %d corridors, %d cores%n",
                net.pipes,
                g.nodeCount(),
                g.edgeCount(),
                Runtime.getRuntime().availableProcessors());
        // JIT warm-up on a throwaway engine
        JunctionRoutingEngine warm = new JunctionRoutingEngine(net.writer);
        Random rnd = new Random(1);
        for (int i = 0; i < 20000; i++) { // ~a few seconds: reach steady-state JIT before timing
            warm.findRoute(id(randomNode(rnd)), id(randomNode(rnd)));
            if (i % 3 == 0) {
                warm.findRoutesToTargets(id(randomNode(rnd)), randomTargets(rnd, 1 + rnd.nextInt(4), -1));
            }
            if (i % 200 == 0) {
                warm.clear();
            }
        }
    }

    private static int randomNode(Random rnd) {
        return nodes.get(rnd.nextInt(nodes.size()));
    }

    /** {@code count} distinct targets; if {@code near >= 0}, only junctions within 96 blocks of it (crafting-like). */
    private static Set<JunctionId> randomTargets(Random rnd, int count, int near) {
        NetworkGraph g = net.writer.graph();
        JunctionNode center = near >= 0 ? g.node(id(near)) : null;
        Set<JunctionId> targets = new HashSet<>();
        int guard = 0;
        while (targets.size() < count && guard++ < 100_000) {
            int t = randomNode(rnd);
            if (t == near) {
                continue;
            }
            if (center != null && NetworkGraph.manhattan(center, g.node(id(t))) > 96) {
                continue;
            }
            targets.add(id(t));
        }
        return targets;
    }

    private static final class Stats {

        final String name;
        final long[] v;

        Stats(String name, long[] nanos) {
            this.name = name;
            v = nanos.clone();
            Arrays.sort(v);
        }

        double us(double p) {
            return v[Math.min(v.length - 1, (int) (p * v.length))] / 1000.0;
        }

        void print() {
            double mean = Arrays.stream(v).average().orElse(0) / 1000.0;
            System.out.printf(
                    "[bench] %-46s n=%5d  mean %8.1f us | p50 %8.1f | p90 %8.1f | p99 %8.1f | max %8.1f us%n",
                    name,
                    v.length,
                    mean,
                    us(0.5),
                    us(0.9),
                    us(0.99),
                    v[v.length - 1] / 1000.0);
        }
    }

    @Test
    void pairQueries() {
        JunctionRoutingEngine engine = new JunctionRoutingEngine(net.writer);
        Random rnd = new Random(10);
        int n = 3000;
        int[][] pairs = new int[n][];
        for (int i = 0; i < n; i++) {
            int s = randomNode(rnd);
            int t;
            do {
                t = randomNode(rnd);
            } while (t == s);
            pairs[i] = new int[] { s, t };
        }
        long[] cold = new long[n];
        for (int i = 0; i < n; i++) {
            engine.clear();
            long t0 = System.nanoTime();
            engine.findRoute(id(pairs[i][0]), id(pairs[i][1]));
            cold[i] = System.nanoTime() - t0;
        }
        for (int[] p : pairs) {
            engine.findRoute(id(p[0]), id(p[1]));
        }
        long[] hot = new long[n];
        for (int i = 0; i < n; i++) {
            long t0 = System.nanoTime();
            engine.findRoute(id(pairs[i][0]), id(pairs[i][1]));
            hot[i] = System.nanoTime() - t0;
        }
        new Stats("1) pair query, cold (first time)", cold).print();
        new Stats("2) pair query, hot (cached)", hot).print();
    }

    @Test
    void oneToManyQueries() {
        runOneToMany("random targets", -2);
        runOneToMany("targets within 96 blocks", 0);
    }

    private void runOneToMany(String label, int mode) {
        JunctionRoutingEngine engine = new JunctionRoutingEngine(net.writer);
        Random rnd = new Random(20 + mode);
        int n = 2000;
        int[] sources = new int[n];
        List<Set<JunctionId>> targets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            sources[i] = randomNode(rnd);
            targets.add(randomTargets(rnd, 3, mode < -1 ? -1 : sources[i]));
        }
        long[] cold = new long[n];
        for (int i = 0; i < n; i++) {
            engine.clear();
            long t0 = System.nanoTime();
            engine.findRoutesToTargets(id(sources[i]), targets.get(i));
            cold[i] = System.nanoTime() - t0;
        }
        for (int i = 0; i < n; i++) {
            engine.findRoutesToTargets(id(sources[i]), targets.get(i));
        }
        long[] hot = new long[n];
        for (int i = 0; i < n; i++) {
            long t0 = System.nanoTime();
            engine.findRoutesToTargets(id(sources[i]), targets.get(i));
            hot[i] = System.nanoTime() - t0;
        }
        new Stats("3) one-to-many x3, cold, " + label, cold).print();
        new Stats("4) one-to-many x3, hot, " + label, hot).print();
    }

    /**
     * Breaking a pipe in a corridor cuts it (both directions disappear); placing it back restores it. Measured: the
     * graph edit including publish, the next query for the pair whose cached route used that corridor, and the next
     * query for another, freshly cached pair elsewhere in the network. A break is a pure worsening, so the other pair
     * stays a hit unless its route used the cut corridor; a placement adds a corridor, which could shorten any route of
     * the network, so every cached route of the network is re-searched on its next use.
     */
    @Test
    void networkEdits() {
        JunctionRoutingEngine engine = new JunctionRoutingEngine(net.writer);
        Random rnd = new Random(30);
        int n = 500;
        List<Long> removeEdit = new ArrayList<>();
        List<Long> removeQuery = new ArrayList<>();
        List<Long> placeEdit = new ArrayList<>();
        List<Long> placeQuery = new ArrayList<>();
        List<Long> otherAfterBreak = new ArrayList<>();
        List<Long> otherAfterPlace = new ArrayList<>();
        long searchedAfterBreak = 0;
        long searchedAfterPlace = 0;
        for (int i = 0; i < n; i++) {
            NetworkGraph g = net.writer.graph();
            int a = randomNode(rnd);
            JunctionNode na = g.node(id(a));
            if (na.edges.isEmpty()) {
                continue;
            }
            int b = na.edges.get(rnd.nextInt(na.edges.size())).to.index();
            List<EdgeSpec> aOriginal = TestNetworks.specsOf(g, a);
            List<EdgeSpec> bOriginal = TestNetworks.specsOf(g, b);
            List<EdgeSpec> aCut = new ArrayList<>(aOriginal);
            aCut.removeIf(e -> e.to.index() == b);
            List<EdgeSpec> bCut = new ArrayList<>(bOriginal);
            bCut.removeIf(e -> e.to.index() == a);
            int[] other = { randomNode(rnd), randomNode(rnd) };
            engine.findRoute(id(a), id(b)); // cached route over the corridor
            engine.findRoute(id(other[0]), id(other[1])); // cached route elsewhere

            long t0 = System.nanoTime();
            net.writer.setEdges(id(a), aCut);
            net.writer.setEdges(id(b), bCut);
            long t1 = System.nanoTime();
            engine.findRoute(id(a), id(b));
            long t2 = System.nanoTime();
            removeEdit.add(t1 - t0);
            removeQuery.add(t2 - t1);

            long before = engine.pairSearches();
            long t3 = System.nanoTime();
            engine.findRoute(id(other[0]), id(other[1]));
            otherAfterBreak.add(System.nanoTime() - t3);
            searchedAfterBreak += engine.pairSearches() - before;

            long t4 = System.nanoTime();
            net.writer.setEdges(id(a), aOriginal);
            net.writer.setEdges(id(b), bOriginal);
            long t5 = System.nanoTime();
            engine.findRoute(id(a), id(b));
            long t6 = System.nanoTime();
            placeEdit.add(t5 - t4);
            placeQuery.add(t6 - t5);

            before = engine.pairSearches();
            long t7 = System.nanoTime();
            engine.findRoute(id(other[0]), id(other[1]));
            otherAfterPlace.add(System.nanoTime() - t7);
            searchedAfterPlace += engine.pairSearches() - before;
        }
        new Stats("5a) break pipe: graph edit + publish", toArray(removeEdit)).print();
        new Stats("5b) break pipe: next query over cut corridor", toArray(removeQuery)).print();
        new Stats("5c) break pipe: next query of another cached pair", toArray(otherAfterBreak)).print();
        new Stats("5d) place pipe: graph edit + publish", toArray(placeEdit)).print();
        new Stats("5e) place pipe: next query over restored corridor", toArray(placeQuery)).print();
        new Stats("5f) place pipe: next query of another cached pair", toArray(otherAfterPlace)).print();
        System.out.printf(
                "[bench]     other cached pairs re-searched: after break %d of %d, after place %d of %d%n",
                searchedAfterBreak,
                otherAfterBreak.size(),
                searchedAfterPlace,
                otherAfterPlace.size());
    }

    /**
     * Every routed pipe polls its power providers every few ticks. Simulated: 100 corridor placements in a row (each a
     * restore of a previously cut corridor, i.e. an improvement), and after each one every router asks for its power
     * table. Old way: whole-network view per router. New way: pair routes to the 5 power junctions.
     */
    @Test
    void powerPollingDuringBuilding() {
        Random rnd = new Random(50);
        Set<JunctionId> powerJunctions = randomTargets(rnd, 5, -1);
        int placements = 100;
        // precompute the edits: cut a corridor, later restore it
        List<int[]> corridors = new ArrayList<>();
        while (corridors.size() < placements) {
            int a = randomNode(rnd);
            JunctionNode na = net.writer.graph().node(id(a));
            if (!na.edges.isEmpty()) {
                corridors.add(new int[] { a, na.edges.get(rnd.nextInt(na.edges.size())).to.index() });
            }
        }
        for (int mode = 0; mode < 2; mode++) {
            JunctionRoutingEngine engine = new JunctionRoutingEngine(net.writer);
            // warm caches like a running server would have
            for (int n : nodes) {
                if (mode == 0) {
                    engine.sweep(id(n));
                } else {
                    engine.findRoutesToTargets(id(n), powerJunctions);
                }
            }
            long searchesBefore = engine.totalSearches();
            long[] perPlacement = new long[placements];
            for (int p = 0; p < placements; p++) {
                int a = corridors.get(p)[0];
                int b = corridors.get(p)[1];
                List<EdgeSpec> original = TestNetworks.specsOf(net.writer.graph(), a);
                List<EdgeSpec> cut = new ArrayList<>(original);
                cut.removeIf(e -> e.to.index() == b);
                net.writer.setEdges(id(a), cut);
                net.writer.setEdges(id(a), original); // the placement: corridor comes back
                long t0 = System.nanoTime();
                for (int n : nodes) {
                    if (mode == 0) {
                        engine.sweep(id(n));
                    } else {
                        engine.findRoutesToTargets(id(n), powerJunctions);
                    }
                }
                perPlacement[p] = System.nanoTime() - t0;
            }
            long searches = engine.totalSearches() - searchesBefore;
            new Stats(
                    (mode == 0 ? "7a) power poll via whole-network view" : "7b) power poll via routes to 5 providers")
                            + ", all routers, per placement",
                    perPlacement).print();
            System.out.printf(
                    "[bench]     %d searches for %d placements x %d routers (%.1f per placement)%n",
                    searches,
                    placements,
                    nodes.size(),
                    searches / (double) placements);
        }
    }

    private static long[] toArray(List<Long> list) {
        long[] a = new long[list.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = list.get(i);
        }
        return a;
    }

    @Test
    void parallelStress() throws Exception {
        stress(false);
        stress(true);
    }

    /**
     * 20 one-to-many queries (1-4 random targets each) released at the same instant, 150 rounds, cache cleared between
     * rounds so every query is cold. Optionally a writer thread keeps breaking and placing pipes the whole time.
     */
    private void stress(boolean withEdits) throws Exception {
        int threads = 20;
        int rounds = 150;
        JunctionRoutingEngine engine = new JunctionRoutingEngine(net.writer);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicBoolean editing = new AtomicBoolean(withEdits);
        AtomicLong edits = new AtomicLong();
        Thread editor = new Thread(() -> {
            Random rnd = new Random(77);
            while (editing.get()) {
                NetworkGraph g = net.writer.graph();
                int a = randomNode(rnd);
                if (g.node(id(a)).edges.isEmpty()) {
                    continue;
                }
                List<EdgeSpec> original = TestNetworks.specsOf(g, a);
                List<EdgeSpec> cut = new ArrayList<>(original);
                cut.remove(rnd.nextInt(cut.size()));
                net.writer.setEdges(id(a), cut);
                net.writer.setEdges(id(a), original);
                edits.addAndGet(2);
            }
        });
        if (withEdits) {
            editor.start();
        }
        long[] latencies = new long[threads * rounds];
        long[] roundWall = new long[rounds];
        Random rnd = new Random(40);
        long start = System.nanoTime();
        try {
            for (int r = 0; r < rounds; r++) {
                engine.clear();
                CountDownLatch go = new CountDownLatch(1);
                List<Future<Long>> futures = new ArrayList<>(threads);
                for (int t = 0; t < threads; t++) {
                    int source = randomNode(rnd);
                    Set<JunctionId> targets = randomTargets(rnd, 1 + rnd.nextInt(4), -1);
                    futures.add(pool.submit(() -> {
                        go.await();
                        long t0 = System.nanoTime();
                        Map<JunctionId, RouteCacheEntry> res = engine.findRoutesToTargets(id(source), targets);
                        long took = System.nanoTime() - t0;
                        assertTrue(res.size() == targets.size());
                        return took;
                    }));
                }
                long r0 = System.nanoTime();
                go.countDown();
                for (int t = 0; t < threads; t++) {
                    latencies[r * threads + t] = futures.get(t).get(60, TimeUnit.SECONDS);
                }
                roundWall[r] = System.nanoTime() - r0;
            }
        } finally {
            editing.set(false);
            editor.join();
            pool.shutdown();
        }
        double seconds = (System.nanoTime() - start) / 1e9;
        String suffix = withEdits ? ", with concurrent edits" : "";
        new Stats("6) 20 parallel one-to-many (1-4), per query" + suffix, latencies).print();
        new Stats("6) 20 parallel one-to-many (1-4), whole batch" + suffix, roundWall).print();
        System.out.printf(
                "[bench]     %d queries in %.2f s total%s%n",
                latencies.length,
                seconds,
                withEdits ? String
                        .format(", %d graph edits published meanwhile (%.0f/s)", edits.get(), edits.get() / seconds)
                        : "");
    }
}
