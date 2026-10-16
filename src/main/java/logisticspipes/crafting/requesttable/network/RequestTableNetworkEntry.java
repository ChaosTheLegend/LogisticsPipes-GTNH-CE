package logisticspipes.crafting.requesttable.network;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.github.bsideup.jabel.Desugar;

import logisticspipes.crafting.requesttable.storage.RequestTableFluidDisplay;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * A requestable stack in the new request table list.
 * <p>
 * Fluids are represented by the LogisticsPipes fluid-container item identifier, while {@link #fluid ()} tells the
 * request code to use the fluid request path and interpret the amount as millibuckets.
 */
@Desugar
public record RequestTableNetworkEntry(ItemIdentifierStack stack, boolean fluid, int networkAmount, int internalAmount,
                                       boolean craftable) implements Comparable<RequestTableNetworkEntry> {

    /**
     * Creates a new network-list entry with separated availability information.
     *
     * @param stack          the display and request stack
     * @param fluid          whether this entry is a fluid request
     * @param networkAmount  amount currently stored in the logistics network
     * @param internalAmount amount currently stored in the request table
     * @param craftable      whether the logistics network can craft this entry
     */
    public RequestTableNetworkEntry {
    }

    /** Returns the client display item; request packets continue to use the original identifier. */
    public ItemStack getDisplayStack() {
        if (fluid) {
            FluidStack stored = SimpleServiceLocator.logisticsFluidManager.getFluidFromContainer(stack);
            if (stored != null) {
                return RequestTableFluidDisplay.of(stored, getTotalAmount());
            }
        }
        return stack.makeNormalStack();
    }

    /**
     * @return combined request-table and network amount
     */
    public int getTotalAmount() {
        return (int) Math.min(Integer.MAX_VALUE, (long) networkAmount + internalAmount);
    }

    /**
     * @return {@code true} when at least one unit is stored in the network or this request table
     */
    public boolean isStored() {
        return getTotalAmount() > 0;
    }

    @Override
    public int compareTo(RequestTableNetworkEntry other) {
        int type = Boolean.compare(fluid, other.fluid);
        if (type != 0) {
            return type;
        }
        return stack.compareTo(other.stack);
    }
}
