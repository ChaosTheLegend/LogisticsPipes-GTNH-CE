package logisticspipes.routing.astar;

/**
 * Something that may have made routes in a component shorter or possible. Components keep a short, newest-first log of
 * these so a cached route can check whether any of them could actually beat it (see {@link ValidatedRoutes#isValid}),
 * instead of being thrown away on every improvement anywhere in the network.
 */
final class ImprovementEvent {

    /** A corridor {@code from -> to} of cost {@code weight} appeared, got cheaper, or gained {@code flags}. */
    static final int EDGE = 0;
    /** Junction {@code from} appeared or came back online. */
    static final int NODE = 1;
    /** Junction data changed (power providers); does not affect pair routes. */
    static final int DATA = 2;
    /** Anything may have changed: invalidates everything older (manual refresh, log truncation, new component). */
    static final int ALL = 3;

    static final int MAX_DEPTH = 256;
    static final int KEEP_ON_TRUNCATE = 128;

    final long stamp;
    final int kind;
    final int from;
    final int to;
    final double weight;
    final int flags;
    final ImprovementEvent next;
    final int depth;

    ImprovementEvent(long stamp, int kind, int from, int to, double weight, int flags, ImprovementEvent next) {
        this.stamp = stamp;
        this.kind = kind;
        this.from = from;
        this.to = to;
        this.weight = weight;
        this.flags = flags;
        this.next = next;
        depth = next == null ? 1 : next.depth + 1;
    }

    static ImprovementEvent all(long stamp) {
        return new ImprovementEvent(stamp, ALL, -1, -1, 0, RoutingFlags.ALL, null);
    }

    ImprovementEvent withNext(ImprovementEvent newNext) {
        return new ImprovementEvent(stamp, kind, from, to, weight, flags, newNext);
    }

    /** Keep the newest {@link #KEEP_ON_TRUNCATE} events and end the log with an {@link #ALL} marker. */
    static ImprovementEvent truncate(ImprovementEvent head) {
        ImprovementEvent[] keep = new ImprovementEvent[KEEP_ON_TRUNCATE];
        ImprovementEvent e = head;
        int n = 0;
        while (e != null && n < KEEP_ON_TRUNCATE) {
            keep[n++] = e;
            e = e.next;
        }
        if (e == null) {
            return head;
        }
        // anything at or before the first dropped event is unknown now
        ImprovementEvent tail = all(e.stamp);
        for (int i = n - 1; i >= 0; i--) {
            tail = keep[i].withNext(tail);
        }
        return tail;
    }
}
