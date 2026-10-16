package logisticspipes.interfaces;

import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * A module of a chassis that keeps what was sent to it itself, instead of the chassis putting it into the inventory.
 */
public interface IChassiItemReceiver {

    /**
     * Called on the server when an item that was sent to this module reaches the end of the chassis.
     *
     * @return true if the module took all of the item
     */
    boolean receiveItem(ItemIdentifierStack stack);
}
