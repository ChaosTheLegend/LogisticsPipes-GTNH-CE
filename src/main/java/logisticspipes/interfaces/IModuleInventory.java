package logisticspipes.interfaces;

import logisticspipes.utils.transactor.ITransactor;

/**
 * An inventory view that a module reads, fills and empties itself, instead of going through the target's sided
 * inventory rules. Used for slots the sided view hides, like GT battery slots.
 */
public interface IModuleInventory extends IInventoryUtil, ITransactor {
}
