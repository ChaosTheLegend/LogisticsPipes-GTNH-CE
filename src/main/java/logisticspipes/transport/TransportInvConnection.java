package logisticspipes.transport;

import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.pipes.PipeItemsInvSysConnector;
import logisticspipes.proxy.bufferlink.BufferLinkManager;
import logisticspipes.routing.ItemRoutingInformation;
import logisticspipes.transport.LPTravelingItem.LPTravelingItemServer;
import logisticspipes.utils.item.ItemIdentifierStack;

public class TransportInvConnection extends PipeTransportLogistics {

    public TransportInvConnection() {
        super(true);
    }

    @Override
    protected boolean supportsFastRelay() {
        return false;
    }

    @Override
    protected boolean isItemExitable(ItemIdentifierStack stack) {
        return true;
    }

    @Override
    protected void handleTileReachedServer(LPTravelingItemServer arrivingItem, TileEntity tile, ForgeDirection dir) {
        // a buffer that moves items by itself (hull, ender chest/tank, long distance pipeline) to the connector at the
        // other end: skip the insert/extract round trip and hand the item over directly
        if (PipeItemsInvSysConnector.isBuffer(tile) && arrivingItem.getInfo() != null
                && (arrivingItem.getTransportMode() == TransportMode.Active
                        || getRoutedPipe().getTransportLayer().stillWantItem(arrivingItem))
                && ((PipeItemsInvSysConnector) container.pipe).passThroughLinkedBuffer(arrivingItem.getInfo(), tile)) {
            return;
        }
        if (!(tile instanceof IInventory) && BufferLinkManager.isLinkBuffer(tile)) {
            // nothing can be inserted into a link-only buffer (no power, no connector on the other end): send the
            // item back instead of dropping it
            reverseItem(arrivingItem);
            return;
        }
        super.handleTileReachedServer(arrivingItem, tile, dir);
    }

    @Override
    public boolean canPipeConnect(TileEntity tile, ForgeDirection side) {
        return super.canPipeConnect(tile, side) || BufferLinkManager.isLinkBuffer(tile);
    }

    @Override
    protected void insertedItemStack(ItemRoutingInformation info, TileEntity tile) {
        if (tile instanceof IInventory) {
            ((PipeItemsInvSysConnector) container.pipe).handleItemEnterInv(info, tile);
        }
    }
}
