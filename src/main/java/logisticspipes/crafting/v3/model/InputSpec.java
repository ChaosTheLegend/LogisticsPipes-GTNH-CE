package logisticspipes.crafting.v3.model;

import java.util.UUID;

import com.github.bsideup.jabel.Desugar;

/**
 * One aggregated input of a pattern: everything the pattern needs under one matcher, summed over its slots.
 *
 * @param qty      amount per craft (consumed) or amount that has to sit in the machine (not consumed)
 * @param consumed false for programmed circuits, molds, lenses and tools: they are requested once per crafter and don't
 *                 keep a machine "busy" for the blocking modes
 * @param target   where the input is delivered when that isn't the crafter itself (a legacy satellite), else null
 */
@Desugar
public record InputSpec(ResourceMatcher matcher, long qty, boolean consumed, UUID target) {

    public InputSpec(ResourceMatcher matcher, long qty, boolean consumed) {
        this(matcher, qty, consumed, null);
    }

    public ResourceKey key() {
        return matcher.key();
    }

    /** Amount needed for {@code crafts} executions when the spec is consumed. */
    public long amountFor(long crafts) {
        return consumed ? qty * crafts : qty;
    }
}
