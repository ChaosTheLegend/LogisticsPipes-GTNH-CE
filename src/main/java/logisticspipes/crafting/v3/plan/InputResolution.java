package logisticspipes.crafting.v3.plan;

import java.util.ArrayList;
import java.util.List;

import logisticspipes.crafting.v3.model.InputSpec;

/** How one input of a node (or the request itself, for the root) is sourced. */
public final class InputResolution {

    public final InputSpec spec;
    public final long totalQty;
    public final List<SourceClaim> sources = new ArrayList<>();

    public InputResolution(InputSpec spec, long totalQty) {
        this.spec = spec;
        this.totalQty = totalQty;
    }

    public long resolvedQty() {
        long sum = 0;
        for (SourceClaim c : sources) {
            if (!(c instanceof SourceClaim.Unresolved)) sum += c.qty();
        }
        return sum;
    }

    public long claimedQty() {
        long sum = 0;
        for (SourceClaim c : sources) sum += c.qty();
        return sum;
    }

    @Override
    public String toString() {
        return spec.matcher() + " x" + totalQty + " <- " + sources;
    }
}
