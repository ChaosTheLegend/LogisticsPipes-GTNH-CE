package logisticspipes.crafting.v3.exec;

/** Lifecycle of a ticket (plan section 2.4). */
public enum TicketState {

    /** Created at commit, inputs not requested yet. */
    PLANNED,
    /** Sent to the crafter; inputs are moving towards it. */
    OPEN,
    /** Every input is buffered at the crafter. */
    COMPLETE,
    /** The crafter was told to insert the set. */
    COMMITTED,
    /** The set is in the machine. */
    INSERTED,
    DONE,
    CANCELLED,
    /** The machine produced something else, or couldn't be configured. */
    WRONG_CRAFT;

    public boolean isFinished() {
        return this == DONE || this == CANCELLED;
    }

    /** States in which the crafter has a needs entry for the ticket. */
    public boolean isOpenAtCrafter() {
        return this == OPEN || this == COMPLETE || this == COMMITTED;
    }
}
