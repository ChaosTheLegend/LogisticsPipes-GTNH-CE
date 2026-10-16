package logisticspipes.interfaces;

/**
 * A module that works on slots the pipe's normal (sneaky) inventory doesn't expose. The chassis uses this view for its
 * room check and for inserting items routed to the module, and the module uses it for extraction.
 */
public interface IModuleInventoryOverride {

    /**
     * @return the view, or null to use the pipe's normal inventory
     */
    IModuleInventory getModuleInventory();
}
