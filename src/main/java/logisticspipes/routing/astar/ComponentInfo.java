package logisticspipes.routing.astar;

/**
 * Per connected component: identity, change/improvement stamps, the improvement log and the heuristic scale. Immutable.
 * <p>
 * {@link #identity} survives what cannot change the component's existing routes for the better: growing by a merge (the
 * larger side keeps it) and losing a split-off part (the largest part keeps it). Cached routes compare it instead of
 * the union-find root, which is only an internal label.
 */
final class ComponentInfo {

    final long identity;
    final long changeStamp;
    final ImprovementEvent log;
    final double heuristicScale;

    ComponentInfo(long identity, long changeStamp, ImprovementEvent log, double heuristicScale) {
        this.identity = identity;
        this.changeStamp = changeStamp;
        this.log = log;
        this.heuristicScale = heuristicScale;
    }

    static ComponentInfo fresh(long stamp) {
        return new ComponentInfo(stamp, stamp, ImprovementEvent.all(stamp), 1.0);
    }

    long improvementStamp() {
        return log == null ? 0 : log.stamp;
    }

    ComponentInfo withChange(long stamp) {
        return new ComponentInfo(identity, stamp, log, heuristicScale);
    }

    ComponentInfo withLog(ImprovementEvent newLog) {
        return new ComponentInfo(identity, changeStamp, newLog, heuristicScale);
    }

    ComponentInfo withScale(double scale) {
        return new ComponentInfo(identity, changeStamp, log, scale);
    }
}
