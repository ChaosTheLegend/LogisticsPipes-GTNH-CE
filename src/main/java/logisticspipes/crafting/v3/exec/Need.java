package logisticspipes.crafting.v3.exec;

import java.util.UUID;

import logisticspipes.crafting.v3.model.ResourceMatcher;

/** One aggregated input of a ticket. Needs are tracked per pattern input, not per slot (plan section 6.3). */
public final class Need {

    public final int index;
    public final ResourceMatcher matcher;
    public final long qty;
    public final boolean consumed;
    /** Delivery location when it isn't the ticket's crafter (legacy satellites); null otherwise. */
    public final UUID target;

    public Need(int index, ResourceMatcher matcher, long qty, boolean consumed) {
        this(index, matcher, qty, consumed, null);
    }

    public Need(int index, ResourceMatcher matcher, long qty, boolean consumed, UUID target) {
        this.index = index;
        this.matcher = matcher;
        this.qty = qty;
        this.consumed = consumed;
        this.target = target;
    }

    @Override
    public String toString() {
        return qty + "x " + matcher;
    }
}
