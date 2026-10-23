package logisticspipes.crafting.v3.model;

import com.github.bsideup.jabel.Desugar;

/** A key with a quantity (items, or mB for fluids). */
@Desugar
public record ResourceAmount(ResourceKey key, long amount) {

    @Override
    public String toString() {
        return amount + "x " + key.displayName();
    }
}
