package logisticspipes.routing.astar;

import java.util.Arrays;

/**
 * Disjoint-set forest over union-find slots, owned by the graph writer.
 * <p>
 * Every junction gets a fresh slot when it is added and slots are never reused, so a removed junction can stay inside
 * parent chains without ever being confused with a later junction that re-uses its id. Union is by size, so merging a
 * new junction into a network keeps the network's root (and with it the network's cached routes).
 * <p>
 * Union-find cannot split; when corridors are removed and a split is possible the writer re-labels just the affected
 * component with {@link #relabel} after a BFS over it.
 */
final class UnionFind {

    private int[] parent;
    private int[] size;

    UnionFind(int capacity) {
        parent = new int[Math.max(16, capacity)];
        size = new int[parent.length];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
            size[i] = 1;
        }
    }

    void ensureCapacity(int slots) {
        if (slots <= parent.length) {
            return;
        }
        int oldLength = parent.length;
        int newLength = Math.max(slots, oldLength + (oldLength >> 1));
        parent = Arrays.copyOf(parent, newLength);
        size = Arrays.copyOf(size, newLength);
        for (int i = oldLength; i < newLength; i++) {
            parent[i] = i;
            size[i] = 1;
        }
    }

    int find(int i) {
        int root = i;
        while (parent[root] != root) {
            root = parent[root];
        }
        while (parent[i] != root) {
            int next = parent[i];
            parent[i] = root;
            i = next;
        }
        return root;
    }

    /** @return the surviving root (the larger set's), or -1 if both were already in the same set. */
    int union(int a, int b) {
        int ra = find(a);
        int rb = find(b);
        if (ra == rb) {
            return -1;
        }
        if (size[ra] < size[rb]) {
            int t = ra;
            ra = rb;
            rb = t;
        }
        parent[rb] = ra;
        size[ra] += size[rb];
        return ra;
    }

    /** Make {@code root} the representative of exactly {@code members}. */
    void relabel(int root, int[] members, int count) {
        for (int k = 0; k < count; k++) {
            parent[members[k]] = root;
        }
        parent[root] = root;
        size[root] = Math.max(1, count);
    }
}
