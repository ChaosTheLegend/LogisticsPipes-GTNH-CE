package logisticspipes.proxy.bufferlink;

import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.request.resources.IResource;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.tuples.LPPosition;

/**
 * Put on the direct connection of an inventory system connector whose buffer carries only items (ender chest, item long
 * distance pipeline) or only fluids (ender tank, fluid long distance pipeline): nothing else is routed, provided or
 * crafted through it.
 */
public final class BufferContentRouteFilter implements IFilter {

    private final LPPosition connector;
    private final BufferContent content;

    public BufferContentRouteFilter(LPPosition connector, BufferContent content) {
        this.connector = connector;
        this.content = content;
    }

    @Override
    public boolean isBlocked() {
        return false; // whitelist: everything the buffer can't carry is blocked
    }

    @Override
    public boolean isFilteredItem(ItemIdentifier item) {
        return content.accepts(item);
    }

    @Override
    public boolean isFilteredItem(IResource resultItem) {
        return content.accepts(resultItem);
    }

    @Override
    public boolean blockProvider() {
        return false;
    }

    @Override
    public boolean blockCrafting() {
        return false;
    }

    @Override
    public boolean blockRouting() {
        return false;
    }

    @Override
    public boolean blockPower() {
        return false;
    }

    @Override
    public LPPosition getLPPosition() {
        return connector;
    }

    // corridor specs compare their filters, so a rescan of the same connector must give an equal filter
    @Override
    public boolean equals(Object o) {
        return o instanceof BufferContentRouteFilter other && content == other.content
                && connector.equals(other.connector);
    }

    @Override
    public int hashCode() {
        return 31 * connector.hashCode() + content.hashCode();
    }
}
