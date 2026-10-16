package logisticspipes.proxy.bufferlink;

import net.minecraft.tileentity.TileEntity;

import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * Recognizes a buffer that moves items on its own, so two inventory system connectors on it can hand items over
 * directly instead of inserting them at one end and extracting them at the other.
 * <p>
 * Keys are compared with {@code equals}. An item put in at the tile with send key K comes out at every tile whose
 * receive key is K.
 */
public interface IBufferLinkHandler {

    /** @return true if this handler knows the tile; the first handler that does decides for it */
    boolean isType(TileEntity tile);

    /**
     * What the buffer carries. Routes through it are limited to that, and only that is handed over.
     */
    BufferContent getContent(TileEntity tile);

    /**
     * Only called for items {@link #getContent} accepts.
     *
     * @return where an item inserted into this tile ends up, or null if it can't go anywhere right now
     */
    Object getSendKey(TileEntity tile, ItemIdentifierStack stack);

    /**
     * @return the buffer this tile hands out items from, or null if nothing can be taken out here
     */
    Object getReceiveKey(TileEntity tile);
}
