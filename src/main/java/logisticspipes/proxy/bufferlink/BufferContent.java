package logisticspipes.proxy.bufferlink;

import logisticspipes.items.LogisticsFluidContainer;
import logisticspipes.request.resources.FluidResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.request.resources.ItemResource;
import logisticspipes.utils.item.ItemIdentifier;

/** What a linked buffer can carry: items, fluids (as LP fluid packets) or both. */
public enum BufferContent {

    ITEMS,
    FLUIDS,
    BOTH;

    public boolean accepts(ItemIdentifier item) {
        if (this == BOTH) {
            return true;
        }
        return isFluidPacket(item) == (this == FLUIDS);
    }

    public boolean accepts(IResource resource) {
        if (this == BOTH) {
            return true;
        }
        boolean fluid = resource instanceof FluidResource
                || (resource instanceof ItemResource itemResource && isFluidPacket(itemResource.getItem()));
        return fluid == (this == FLUIDS);
    }

    private static boolean isFluidPacket(ItemIdentifier item) {
        return item != null && item.item instanceof LogisticsFluidContainer;
    }
}
