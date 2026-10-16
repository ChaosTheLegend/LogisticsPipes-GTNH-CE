package logisticspipes.interfaces;

import net.minecraft.tileentity.TileEntity;

import logisticspipes.transport.LPTravelingItem.LPTravelingItemServer;

/**
 * A routed pipe that takes the fluid out of the fluid containers that arrive for it, instead of handing them to the
 * tile as items.
 */
public interface IFluidContainerReceiver {

    /**
     * Called on the server when an item that was sent to this pipe reaches the end of it.
     *
     * @param tile the tile on the side the item leaves the pipe at
     * @return true if the item was a fluid container and is handled
     */
    boolean endReached(LPTravelingItemServer arrivingItem, TileEntity tile);
}
