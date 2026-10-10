package logisticspipes.routing.astar;

import static logisticspipes.routing.astar.TestNetworks.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Synthetic networks of 10k-50k pipes: a grid of pipe lines with routers at some crossings and along some lines. The
 * pipes are compressed into corridors the same way the corridor scan does it (walk plain pipe until a router), then
 * query latency and edit-to-next-query latency are measured. Numbers are printed; only correctness is asserted.
 */
class JunctionPerfTest {

    private static final int[][] DIRS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
    /** Defaults of Configs.LOGISTICS_DETECTION_COUNT / LOGISTICS_DETECTION_LENGTH. */
    private static final int MAX_VISITED = 100;
    private static final int MAX_LENGTH = 50;

    static final class Network {

        int pipes;
        int junctions;
        long buildNanos;
        JunctionGraphWriter writer;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    static Network generate(int size, int step, long seed) {
        Random rnd = new Random(seed);
        Set<Long> pipes = new HashSet<>();
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                if (x % step == 0 || z % step == 0) {
                    pipes.add(key(x, z));
                }
            }
        }
        // routers: 70% of the crossings, plus a few along the lines (machines hooked to the bus)
        Map<Long, Integer> routers = new HashMap<>();
        int nextId = 1;
        for (long p : pipes) {
            int x = (int) (p >> 32);
            int z = (int) p;
            boolean crossing = x % step == 0 && z % step == 0;
            if ((crossing && rnd.nextDouble() < 0.7) || (!crossing && rnd.nextDouble() < 0.004)) {
                routers.put(p, nextId++);
            }
        }

        long start = System.nanoTime();
        JunctionGraphWriter w = new JunctionGraphWriter();
        for (Map.Entry<Long, Integer> r : routers.entrySet()) {
            int x = (int) (r.getKey() >> 32);
            int z = (int) (long) r.getKey();
            w.addJunction(id(r.getValue()), 0, x, 64, z, null, true);
        }
        // corridor compression: BFS through plain pipe from every router, stopping at routers
        for (Map.Entry<Long, Integer> r : routers.entrySet()) {
            int sx = (int) (r.getKey() >> 32);
            int sz = (int) (long) r.getKey();
            Map<Integer, EdgeSpec> found = new HashMap<>();
            Map<Long, Integer> dist = new HashMap<>();
            Map<Long, Integer> firstDir = new HashMap<>();
            ArrayDeque<Long> queue = new ArrayDeque<>();
            dist.put(r.getKey(), 0);
            queue.add(r.getKey());
            while (!queue.isEmpty()) {
                long cur = queue.poll();
                int cx = (int) (cur >> 32);
                int cz = (int) cur;
                int d = dist.get(cur);
                for (int k = 0; k < 4; k++) {
                    int nx = cx + DIRS[k][0];
                    int nz = cz + DIRS[k][1];
                    long n = key(nx, nz);
                    if (!pipes.contains(n) || dist.containsKey(n)) {
                        continue;
                    }
                    // the real corridor scan gives up after 100 pipes or a run of 50
                    if (dist.size() > MAX_VISITED || d + 1 > MAX_LENGTH) {
                        continue;
                    }
                    dist.put(n, d + 1);
                    int dir = cur == r.getKey() ? k : firstDir.get(cur);
                    firstDir.put(n, dir);
                    Integer other = routers.get(n);
                    if (other != null) {
                        if (!found.containsKey(other)) {
                            found.put(
                                    other,
                                    new EdgeSpec(
                                            id(other),
                                            d + 1,
                                            RoutingFlags.ALL,
                                            null,
                                            d,
                                            dir,
                                            k,
                                            new long[] { ChunkEdgeIndex.chunkKeyForBlock(0, sx, sz),
                                                    ChunkEdgeIndex.chunkKeyForBlock(0, nx, nz) }));
                        }
                        continue; // corridors end at routers
                    }
                    queue.add(n);
                }
            }
            w.setEdges(id(r.getValue()), new ArrayList<>(found.values()));
        }
        Network net = new Network();
        net.buildNanos = System.nanoTime() - start;
        net.pipes = pipes.size();
        net.junctions = routers.size();
        net.writer = w;
        return net;
    }

    private static double percentile(long[] values, double p) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[Math.min(sorted.length - 1, (int) (p * sorted.length))] / 1000.0;
    }

    private static void run(int size, int step, long seed) {
        Network net = generate(size, step, seed);
        NetworkGraph g = net.writer.graph();
        System.out.printf(
                "[junction-perf] %d pipes -> %d junctions, %d corridors (%.1f pipes per junction), built in %.1f ms%n",
                net.pipes,
                g.nodeCount(),
                g.edgeCount(),
                net.pipes / (double) g.nodeCount(),
                net.buildNanos / 1e6);
        assertTrue(net.pipes >= 10_000);
        assertTrue(g.nodeCount() * 10 < net.pipes, "pruning should keep far fewer junctions than pipes");

        Random rnd = new Random(seed + 1);
        List<Integer> nodes = TestNetworks.allNodes(g);
        JunctionRoutingEngine engine = new JunctionRoutingEngine(net.writer);
        int queries = 2000;
        int[][] pairs = new int[queries][];
        for (int i = 0; i < queries; i++) {
            pairs[i] = new int[] { nodes.get(rnd.nextInt(nodes.size())), nodes.get(rnd.nextInt(nodes.size())) };
        }

        // warm up the JIT without touching the engine's cache
        for (int i = 0; i < 300; i++) {
            JunctionSearch.search(
                    g,
                    id(pairs[i][0]),
                    java.util.Collections.singleton(id(pairs[i][1])),
                    g.manhattanHeuristic());
        }

        long[] cold = new long[queries];
        for (int i = 0; i < queries; i++) {
            long t0 = System.nanoTime();
            engine.findRoute(id(pairs[i][0]), id(pairs[i][1]));
            cold[i] = System.nanoTime() - t0;
        }
        long[] warm = new long[queries];
        for (int i = 0; i < queries; i++) {
            long t0 = System.nanoTime();
            engine.findRoute(id(pairs[i][0]), id(pairs[i][1]));
            warm[i] = System.nanoTime() - t0;
        }
        System.out.printf(
                "[junction-perf]   pair query cold: p50 %.1f us, p99 %.1f us | cached: p50 %.2f us, p99 %.2f us%n",
                percentile(cold, 0.5),
                percentile(cold, 0.99),
                percentile(warm, 0.5),
                percentile(warm, 0.99));

        // correctness sample against the reference
        for (int i = 0; i < 25; i++) {
            int s = pairs[i][0];
            int t = pairs[i][1];
            if (s == t) {
                continue;
            }
            Double expected = TestNetworks.referenceDistances(g, s, RoutingFlags.CAN_ROUTE_TO).get(t);
            RouteCacheEntry e = engine.findRoute(id(s), id(t));
            if (expected == null) {
                assertTrue(!e.isReachable());
            } else {
                assertEquals(expected, e.totalWeight, 1e-9);
            }
        }

        // edit -> next query: a pipe is added to a random corridor (weight + 1), then a cached pair is asked again
        int edits = 200;
        long[] publish = new long[edits];
        long[] next = new long[edits];
        for (int i = 0; i < edits; i++) {
            NetworkGraph cur = net.writer.graph();
            int n = nodes.get(rnd.nextInt(nodes.size()));
            JunctionNode node = cur.node(id(n));
            if (node.edges.isEmpty()) {
                continue;
            }
            CorridorEdge e = node.edges.get(rnd.nextInt(node.edges.size()));
            List<EdgeSpec> specs = TestNetworks.withWeight(cur, n, e.to.index(), e.weight + 1);
            long t0 = System.nanoTime();
            net.writer.setEdges(id(n), specs);
            long t1 = System.nanoTime();
            int[] p = pairs[rnd.nextInt(queries)];
            engine.findRoute(id(p[0]), id(p[1]));
            long t2 = System.nanoTime();
            publish[i] = t1 - t0;
            next[i] = t2 - t1;
        }
        System.out.printf(
                "[junction-perf]   edit publish: p50 %.1f us, p99 %.1f us | next query: p50 %.1f us, p99 %.1f us%n",
                percentile(publish, 0.5),
                percentile(publish, 0.99),
                percentile(next, 0.5),
                percentile(next, 0.99));

        // what the link-state router paid per edit: a full Dijkstra on every router of the network
        long t0 = System.nanoTime();
        int sample = 50;
        for (int i = 0; i < sample; i++) {
            JunctionSearch.search(g, id(nodes.get(rnd.nextInt(nodes.size()))), null, null);
        }
        double fullSweepMs = (System.nanoTime() - t0) / 1e6 / sample;
        System.out.printf(
                "[junction-perf]   one full network sweep: %.2f ms -> old per-edit cost for %d routers ~ %.0f ms%n",
                fullSweepMs,
                g.nodeCount(),
                fullSweepMs * g.nodeCount());
    }

    @Test
    void tenThousandPipes() {
        run(260, 12, 11);
    }

    @Test
    void fiftyThousandPipes() {
        run(720, 20, 12);
    }
}
