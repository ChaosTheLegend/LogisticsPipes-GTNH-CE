package logisticspipes.proxy.bufferlink;

import net.minecraft.tileentity.TileEntity;

import codechicken.enderstorage.storage.liquid.TileEnderTank;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * EnderStorage tanks: like ender chests, tanks with the same colors and owner share one storage object, in any
 * dimension. A tank isn't an item inventory, so connectors only pass fluid packets through it, and only by handing them
 * over directly.
 */
public class EnderTankBufferLinkHandler implements IBufferLinkHandler {

    @Override
    public boolean isType(TileEntity tile) {
        return tile instanceof TileEnderTank;
    }

    @Override
    public BufferContent getContent(TileEntity tile) {
        return BufferContent.FLUIDS;
    }

    @Override
    public Object getSendKey(TileEntity tile, ItemIdentifierStack stack) {
        return ((TileEnderTank) tile).getStorage();
    }

    @Override
    public Object getReceiveKey(TileEntity tile) {
        return ((TileEnderTank) tile).getStorage();
    }
}
