package logisticspipes.crafting.v3.exec;

/** Where a job's items are (plan section 2.5). */
public enum RecordState {

    /** In a provider, held for the job. Counted in the reservation index. */
    RESERVED,
    /** Needed but without a source: missing, unreachable, or lost and waiting for a retry. */
    UNSOURCED,
    /** Routed and travelling to {@code location}. */
    IN_TRANSIT,
    /** In a crafter's buffer at {@code location}. */
    BUFFERED,
    /** In the machine (or, for legacy crafters, delivered to it). */
    INSERTED,
    /** Will be produced by node {@code producedBy}. */
    EXPECTED,
    /** Buffered at {@code location} but not needed by any ticket. */
    LEFTOVER;

    /** States that still have to become BUFFERED before the ticket can run. */
    public boolean isPending() {
        return this == RESERVED || this == UNSOURCED || this == IN_TRANSIT || this == EXPECTED;
    }
}
