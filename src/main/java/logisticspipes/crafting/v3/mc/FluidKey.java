package logisticspipes.crafting.v3.mc;

import logisticspipes.crafting.v3.model.ResourceKey;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;

/** A fluid (with NBT); amounts are mB. Travels inside LP fluid containers. */
public final class FluidKey implements ResourceKey {

    /** Largest amount routed in one fluid container. */
    public static final int MAX_PER_CONTAINER = 64_000;

    public final FluidIdentifier id;

    private FluidKey(FluidIdentifier id) {
        this.id = id;
    }

    public static FluidKey of(FluidIdentifier id) {
        return id == null ? null : new FluidKey(id);
    }

    @Override
    public boolean isFluid() {
        return true;
    }

    @Override
    public int maxStackSize() {
        return MAX_PER_CONTAINER;
    }

    @Override
    public String displayName() {
        return id.makeFluidStack(1).getLocalizedName();
    }

    /** The item identity LP uses for routing decisions (filters) of this fluid. */
    public ItemIdentifier routingItem() {
        return id.getItemIdentifier();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FluidKey && ((FluidKey) o).id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
