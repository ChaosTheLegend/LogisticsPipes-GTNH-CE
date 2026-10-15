package logisticspipes.crafting.requesttable;

import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * A requestable stack in the new request table list.
 * <p>
 * Fluids are represented by the LogisticsPipes fluid-container item identifier, while {@link #isFluid()} tells the
 * request code to use the fluid request path and interpret the amount as millibuckets.
 */
public class RequestTableNetworkEntry implements Comparable<RequestTableNetworkEntry> {

    private final ItemIdentifierStack stack;
    private final boolean fluid;
    private final int networkAmount;
    private final int internalAmount;
    private final boolean craftable;

    /**
     * Creates a new network-list entry.
     *
     * @param stack the display and request stack
     * @param fluid whether this entry is a fluid request
     */
    public RequestTableNetworkEntry(ItemIdentifierStack stack, boolean fluid) {
        this(stack, fluid, stack == null ? 0 : stack.getStackSize(), 0, false);
    }

    /**
     * Creates a new network-list entry with separated network and internal counts.
     *
     * @param stack          the display and request stack
     * @param fluid          whether this entry is a fluid request
     * @param networkAmount  amount currently stored in the logistics network
     * @param internalAmount amount currently stored in the request table
     */
    public RequestTableNetworkEntry(ItemIdentifierStack stack, boolean fluid, int networkAmount, int internalAmount) {
        this(stack, fluid, networkAmount, internalAmount, false);
    }

    /**
     * Creates a new network-list entry with separated availability information.
     *
     * @param stack          the display and request stack
     * @param fluid          whether this entry is a fluid request
     * @param networkAmount  amount currently stored in the logistics network
     * @param internalAmount amount currently stored in the request table
     * @param craftable      whether the logistics network can craft this entry
     */
    public RequestTableNetworkEntry(ItemIdentifierStack stack, boolean fluid, int networkAmount, int internalAmount,
            boolean craftable) {
        this.stack = stack;
        this.fluid = fluid;
        this.networkAmount = networkAmount;
        this.internalAmount = internalAmount;
        this.craftable = craftable;
    }

    /**
     * @return the stack used for rendering and packet submission
     */
    public ItemIdentifierStack getStack() {
        return stack;
    }

    /**
     * @return {@code true} when the entry represents a fluid amount in millibuckets
     */
    public boolean isFluid() {
        return fluid;
    }

    /**
     * @return amount currently stored in the logistics network
     */
    public int getNetworkAmount() {
        return networkAmount;
    }

    /**
     * @return amount currently stored in the request table
     */
    public int getInternalAmount() {
        return internalAmount;
    }

    /**
     * @return combined request-table and network amount
     */
    public int getTotalAmount() {
        return networkAmount + internalAmount;
    }

    /**
     * @return {@code true} when at least one unit is stored in the network or this request table
     */
    public boolean isStored() {
        return getTotalAmount() > 0;
    }

    /**
     * @return {@code true} when the logistics network advertises a crafting route for this entry
     */
    public boolean isCraftable() {
        return craftable;
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
