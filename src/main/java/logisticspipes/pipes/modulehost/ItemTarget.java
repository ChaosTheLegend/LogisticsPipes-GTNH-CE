package logisticspipes.pipes.modulehost;

import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.interfaces.IInventoryUtil;

/**
 * An inventory an item module works on, seen from the side that faces the pipe, with the direction from the pipe to it:
 * items taken out of it are sent from that side.
 */
public final class ItemTarget {

    public final IInventoryUtil inventory;
    public final ForgeDirection direction;

    public ItemTarget(IInventoryUtil inventory, ForgeDirection direction) {
        this.inventory = inventory;
        this.direction = direction;
    }
}
