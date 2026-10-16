package logisticspipes.crafting;

import logisticspipes.utils.item.ItemIdentifier;

public interface IStagedProviderReservation {

    /**
     * Reserves provider stock for a staged craft before the staged crafting pipe is ready to receive the items.
     */
    void reserveStagedCrafting(ItemIdentifier item, int amount);

    /**
     * Releases provider stock when a staged craft places the real provider order or abandons the reservation.
     */
    void releaseStagedCrafting(ItemIdentifier item, int amount);
}
