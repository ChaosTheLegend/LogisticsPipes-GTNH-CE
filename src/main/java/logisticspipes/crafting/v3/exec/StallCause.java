package logisticspipes.crafting.v3.exec;

/** Why an UNSOURCED record has no source yet. */
public enum StallCause {
    /** Not looked at yet. */
    NONE,
    /** Nothing matching exists on the network. */
    MISSING,
    /** Matching items exist but can't be routed to where they are needed (or the chunk is unloaded). */
    UNREACHABLE
}
