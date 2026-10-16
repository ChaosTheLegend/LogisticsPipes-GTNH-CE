package logisticspipes.routing.astar;

/** Route cache key: routes are cached per (source, destination) pair. */
public final class PairKey {

    public final JunctionId source;
    public final JunctionId dest;

    public PairKey(JunctionId source, JunctionId dest) {
        this.source = source;
        this.dest = dest;
    }

    public boolean involves(JunctionId id) {
        return source.equals(id) || dest.equals(id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PairKey)) {
            return false;
        }
        PairKey k = (PairKey) o;
        return source.equals(k.source) && dest.equals(k.dest);
    }

    @Override
    public int hashCode() {
        return 31 * source.hashCode() + dest.hashCode();
    }

    @Override
    public String toString() {
        return source + "->" + dest;
    }
}
