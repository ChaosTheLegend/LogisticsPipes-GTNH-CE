package logisticspipes.routing.astar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;

/** Helpers to build junction graphs directly through the writer (synchronous mode) and a reference shortest path. */
final class TestNetworks {

    private TestNetworks() {}

    static JunctionId id(int i) {
        return JunctionId.of(i);
    }

    static void node(JunctionGraphWriter w, int i, int x, int y, int z) {
        w.addJunction(id(i), 0, x, y, z, "junction-" + i, true);
    }

    static EdgeSpec edge(int to, double weight) {
        return new EdgeSpec(id(to), weight, RoutingFlags.ALL);
    }

    static EdgeSpec edge(int to, double weight, int flags) {
        return new EdgeSpec(id(to), weight, flags);
    }

    /** Symmetric corridor with full flags. */
    static void link(Map<Integer, List<EdgeSpec>> out, int a, int b, double weight) {
        out.computeIfAbsent(a, k -> new ArrayList<>()).add(edge(b, weight));
        out.computeIfAbsent(b, k -> new ArrayList<>()).add(edge(a, weight));
    }

    static void apply(JunctionGraphWriter w, Map<Integer, List<EdgeSpec>> out) {
        for (Map.Entry<Integer, List<EdgeSpec>> e : out.entrySet()) {
            w.setEdges(id(e.getKey()), e.getValue());
        }
    }

    static List<EdgeSpec> specsOf(NetworkGraph g, int node) {
        List<EdgeSpec> list = new ArrayList<>();
        for (CorridorEdge e : g.node(id(node)).edges) {
            list.add(e.toSpec());
        }
        return list;
    }

    static List<EdgeSpec> withWeight(NetworkGraph g, int node, int to, double weight) {
        List<EdgeSpec> list = new ArrayList<>();
        for (CorridorEdge e : g.node(id(node)).edges) {
            if (e.to.index() == to) {
                list.add(
                        new EdgeSpec(
                                e.to,
                                weight,
                                e.flags,
                                e.filters,
                                e.blockDistance,
                                e.exitSide,
                                e.insertSide,
                                e.chunks));
            } else {
                list.add(e.toSpec());
            }
        }
        return list;
    }

    /**
     * Random network: junctions on a grid of 16-block cells with random offsets, corridors to near neighbours with
     * weight {@code >= manhattan} (like real pipe runs), some missing to create loops and detours.
     */
    static int buildRandom(JunctionGraphWriter w, Random rnd, int count, double flagRestrictChance) {
        int side = (int) Math.ceil(Math.sqrt(count));
        int[][] pos = new int[count + 1][];
        for (int i = 1; i <= count; i++) {
            int gx = (i - 1) % side;
            int gz = (i - 1) / side;
            pos[i] = new int[] { gx * 16 + rnd.nextInt(8), rnd.nextInt(6), gz * 16 + rnd.nextInt(8) };
            node(w, i, pos[i][0], pos[i][1], pos[i][2]);
        }
        Map<Integer, List<EdgeSpec>> out = new HashMap<>();
        for (int i = 1; i <= count; i++) {
            int gx = (i - 1) % side;
            int[] candidates = { i + 1, i + side, i + side + 1 };
            for (int j : candidates) {
                if (j > count || (j != i + side && gx == side - 1)) {
                    continue;
                }
                if (rnd.nextDouble() < 0.15) {
                    continue;
                }
                int m = Math.abs(pos[i][0] - pos[j][0]) + Math.abs(pos[i][1] - pos[j][1])
                        + Math.abs(pos[i][2] - pos[j][2]);
                double weight = m + rnd.nextInt(12) + rnd.nextDouble();
                int flagsAB = RoutingFlags.ALL;
                int flagsBA = RoutingFlags.ALL;
                if (rnd.nextDouble() < flagRestrictChance) {
                    flagsAB = randomFlags(rnd);
                }
                if (rnd.nextDouble() < flagRestrictChance) {
                    flagsBA = randomFlags(rnd);
                }
                out.computeIfAbsent(i, k -> new ArrayList<>()).add(edge(j, weight, flagsAB));
                out.computeIfAbsent(j, k -> new ArrayList<>()).add(edge(i, weight + rnd.nextInt(3), flagsBA));
            }
        }
        apply(w, out);
        return count;
    }

    static int randomFlags(Random rnd) {
        int f = rnd.nextInt(RoutingFlags.ALL + 1);
        return f == 0 ? RoutingFlags.CAN_ROUTE_TO : f;
    }

    /**
     * Independent reference: plain Dijkstra over edges carrying {@code flag}, through active junctions only, never
     * passing through the source again. Only valid for filter-free graphs.
     */
    static Map<Integer, Double> referenceDistances(NetworkGraph g, int source, int flag) {
        Map<Integer, Double> dist = new HashMap<>();
        PriorityQueue<double[]> pq = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        JunctionNode s = g.node(id(source));
        if (s == null) {
            return dist;
        }
        for (CorridorEdge e : s.edges) {
            if ((e.flags & flag) != 0) {
                pq.add(new double[] { e.weight, e.to.index() });
            }
        }
        while (!pq.isEmpty()) {
            double[] cur = pq.poll();
            int n = (int) cur[1];
            if (n == source || dist.containsKey(n)) {
                continue;
            }
            JunctionNode node = g.node(id(n));
            if (node == null || !node.active) {
                continue;
            }
            dist.put(n, cur[0]);
            for (CorridorEdge e : node.edges) {
                if ((e.flags & flag) != 0 && !dist.containsKey(e.to.index())) {
                    pq.add(new double[] { cur[0] + e.weight, e.to.index() });
                }
            }
        }
        return dist;
    }

    static List<Integer> allNodes(NetworkGraph g) {
        List<Integer> list = new ArrayList<>();
        for (JunctionNode n : g.nodes()) {
            list.add(n.id.index());
        }
        Collections.sort(list);
        return list;
    }
}
