package logisticspipes.crafting.v3.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A recipe as the planner sees it: aggregated inputs and guaranteed outputs. Item and fluid inputs share one list
 * because {@link ResourceKey} covers both.
 * <p>
 * {@link #id()} is a content signature, so the same recipe in two crafting pipes is one pattern with two crafters.
 */
public final class Pattern {

    private final String id;
    private final List<InputSpec> inputs;
    private final List<ResourceAmount> outputs;
    private final List<ResourceAmount> chanced;

    public Pattern(String id, List<InputSpec> inputs, List<ResourceAmount> outputs, List<ResourceAmount> chanced) {
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("pattern " + id + " has no outputs");
        }
        this.id = id;
        this.inputs = Collections.unmodifiableList(aggregateInputs(inputs));
        this.outputs = Collections.unmodifiableList(aggregateOutputs(outputs));
        this.chanced = chanced == null ? Collections.emptyList() : Collections.unmodifiableList(chanced);
    }

    public String id() {
        return id;
    }

    public List<InputSpec> inputs() {
        return inputs;
    }

    /** Guaranteed outputs per craft; the first one is the primary output. */
    public List<ResourceAmount> outputs() {
        return outputs;
    }

    /** Chanced outputs, display only (plan section 1.2.2). */
    public List<ResourceAmount> chanced() {
        return chanced;
    }

    public ResourceKey primaryOutput() {
        return outputs.get(0).key();
    }

    public long yield(ResourceKey key) {
        for (ResourceAmount out : outputs) {
            if (out.key().equals(key)) return out.amount();
        }
        return 0;
    }

    /** The output key that satisfies {@code matcher}, or null. */
    public ResourceKey outputMatching(ResourceMatcher matcher) {
        for (ResourceAmount out : outputs) {
            if (matcher.matches(out.key())) return out.key();
        }
        return null;
    }

    public boolean hasNonConsumedInputs() {
        for (InputSpec in : inputs) {
            if (!in.consumed()) return true;
        }
        return false;
    }

    private static List<InputSpec> aggregateInputs(List<InputSpec> raw) {
        Map<List<Object>, InputSpec> merged = new LinkedHashMap<>();
        for (InputSpec spec : raw) {
            if (spec.qty() <= 0) continue;
            merged.merge(
                    java.util.Arrays.asList(spec.matcher(), spec.target(), spec.consumed()),
                    spec,
                    (a, b) -> new InputSpec(a.matcher(), a.qty() + b.qty(), a.consumed(), a.target()));
        }
        return new ArrayList<>(merged.values());
    }

    private static List<ResourceAmount> aggregateOutputs(List<ResourceAmount> raw) {
        Map<ResourceKey, Long> merged = new LinkedHashMap<>();
        for (ResourceAmount out : raw) {
            if (out.amount() > 0) merged.merge(out.key(), out.amount(), Long::sum);
        }
        List<ResourceAmount> result = new ArrayList<>();
        merged.forEach((k, v) -> result.add(new ResourceAmount(k, v)));
        return result;
    }

    @Override
    public String toString() {
        return "Pattern[" + outputs + " <- " + inputs + "]";
    }
}
