package logisticspipes.routing.astar;

/**
 * Identity of a junction node in the {@link NetworkGraph}.
 * <p>
 * For Logistics Pipes the value is the router's simple id, which is small and dense, so it doubles as an array index
 * inside graph snapshots. Values must therefore fit in a non-negative {@code int}.
 */
public final class JunctionId implements Comparable<JunctionId> {

    private static final int CACHE_SIZE = 4096;
    private static final JunctionId[] CACHE = new JunctionId[CACHE_SIZE];

    static {
        for (int i = 0; i < CACHE_SIZE; i++) {
            CACHE[i] = new JunctionId(i);
        }
    }

    private final long value;

    private JunctionId(long value) {
        this.value = value;
    }

    public static JunctionId of(long value) {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("JunctionId out of range: " + value);
        }
        if (value < CACHE_SIZE) {
            return CACHE[(int) value];
        }
        return new JunctionId(value);
    }

    public long value() {
        return value;
    }

    /** The value as an array index into graph snapshots. */
    public int index() {
        return (int) value;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof JunctionId && ((JunctionId) o).value == value);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    public int compareTo(JunctionId o) {
        return Long.compare(value, o.value);
    }

    @Override
    public String toString() {
        return "J" + value;
    }
}
