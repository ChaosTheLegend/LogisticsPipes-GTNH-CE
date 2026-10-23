package logisticspipes.crafting.v3.plan;

/** Planning stopped: the request is too big or took too long. The message is shown to the player. */
public class PlanningException extends RuntimeException {

    public PlanningException(String message) {
        super(message);
    }
}
