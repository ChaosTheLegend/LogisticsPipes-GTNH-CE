package logisticspipes.proxy.bufferlink;

import net.minecraft.tileentity.TileEntity;

import codechicken.enderstorage.storage.item.TileEnderChest;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * EnderStorage chests: all chests with the same colors and owner share one storage object on the server, in any
 * dimension, so that object is the key.
 */
public class EnderChestBufferLinkHandler implements IBufferLinkHandler {

    @Override
    public boolean isType(TileEntity tile) {
        return tile instanceof TileEnderChest;
    }

    @Override
    public BufferContent getContent(TileEntity tile) {
        return BufferContent.ITEMS;
    }

    @Override
    public Object getSendKey(TileEntity tile, ItemIdentifierStack stack) {
        return ((TileEnderChest) tile).getStorage();
    }

    @Override
    public Object getReceiveKey(TileEntity tile) {
        return ((TileEnderChest) tile).getStorage();
    }
}
