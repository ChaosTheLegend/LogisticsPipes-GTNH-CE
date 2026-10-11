package logisticspipes.crafting.requesttable;

import java.util.UUID;

import logisticspipes.interfaces.routing.IAdditionalTargetInformation;

/**
 * Marks a request at the request table as one that goes on into the inventory of the player who made it. It travels
 * with the requested items and comes back with them when they arrive.
 * <p>
 * It isn't saved with an item on its way, so what arrives after a restart stays in the table.
 */
public class RequestTablePlayerTarget implements IAdditionalTargetInformation {

    private final UUID player;

    public RequestTablePlayerTarget(UUID player) {
        this.player = player;
    }

    public UUID getPlayer() {
        return player;
    }
}
