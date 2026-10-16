package logisticspipes.proxy.bufferlink;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.tileentity.TileEntity;

import cpw.mods.fml.common.Loader;
import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.tuples.LPPosition;

/**
 * Buffers that let two inventory system connectors skip the insert/extract round trip (GT hulls, GT long distance
 * pipelines, EnderStorage chests). See {@link IBufferLinkHandler}.
 */
public final class BufferLinkManager {

    private static final List<IBufferLinkHandler> handlers = new ArrayList<>();

    private BufferLinkManager() {}

    public static void load() {
        if (Loader.isModLoaded("gregtech_nh")) {
            register(new GTBufferLinkHandler());
        }
        if (Loader.isModLoaded("EnderStorage")) {
            register(new EnderChestBufferLinkHandler());
            register(new EnderTankBufferLinkHandler());
        }
    }

    public static void register(IBufferLinkHandler handler) {
        handlers.add(handler);
    }

    public static Object getSendKey(TileEntity tile, ItemIdentifierStack stack) {
        IBufferLinkHandler handler = handlerFor(tile);
        if (handler == null || !handler.getContent(tile).accepts(stack.getItem())) {
            return null;
        }
        return handler.getSendKey(tile, stack);
    }

    public static Object getReceiveKey(TileEntity tile) {
        IBufferLinkHandler handler = handlerFor(tile);
        return handler == null ? null : handler.getReceiveKey(tile);
    }

    /** True for every buffer a handler knows, including ones that aren't item inventories (ender tanks). */
    public static boolean isLinkBuffer(TileEntity tile) {
        return handlerFor(tile) != null;
    }

    /**
     * @return the filter for the direct connection of the connector at {@code connector} through this tile, or null
     */
    public static IFilter getRouteFilter(TileEntity tile, LPPosition connector) {
        IBufferLinkHandler handler = handlerFor(tile);
        if (handler == null) {
            return null;
        }
        BufferContent content = handler.getContent(tile);
        return content == BufferContent.BOTH ? null : new BufferContentRouteFilter(connector, content);
    }

    public static List<IFilter> withFilter(List<IFilter> filters, IFilter extra) {
        List<IFilter> result = new ArrayList<>(filters.size() + 1);
        result.addAll(filters);
        result.add(extra);
        return Collections.unmodifiableList(result);
    }

    private static IBufferLinkHandler handlerFor(TileEntity tile) {
        if (tile == null || tile.isInvalid()) {
            return null;
        }
        for (IBufferLinkHandler handler : handlers) {
            if (handler.isType(tile)) {
                return handler;
            }
        }
        return null;
    }
}
