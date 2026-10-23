package logisticspipes.crafting.v3.exec;

public enum JobState {

    RUNNING,
    /** Winding down: nothing new starts, running sets finish, then leftovers go to storage. */
    CANCELLING,
    COMPLETED,
    CANCELLED;

    public boolean isOver() {
        return this == COMPLETED || this == CANCELLED;
    }
}
