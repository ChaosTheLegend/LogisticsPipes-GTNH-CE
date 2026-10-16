package logisticspipes.crafting;

import logisticspipes.interfaces.routing.IProvideFluids;
import logisticspipes.routing.FluidLogisticsPromise;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.utils.FluidIdentifier;
import lombok.Getter;

@Getter
public class PatternFluidCraftingPromise extends FluidLogisticsPromise implements PatternByproductPromise {

    private final int patternSlot;
    private final int resultAmountPerSet;
    private PatternByproductTarget byproductTarget;

    public void setByproductTarget(PatternByproductTarget target) {
        byproductTarget = target;
    }

    /** Keeps excess output tied to the same satellite as the requested part of the recipe result. */
    @Override
    public PatternFluidByproductPromise split(int more) {
        setAmount(getAmount() - more);
        return new PatternFluidByproductPromise(getLiquid(), more, getSender(), false, byproductTarget);
    }

    public PatternFluidCraftingPromise(FluidIdentifier fluid, int amount, IProvideFluids sender, int patternSlot,
            int resultAmountPerSet) {
        super(fluid, amount, sender, ResourceType.CRAFTING);
        this.patternSlot = patternSlot;
        this.resultAmountPerSet = resultAmountPerSet;
    }

    /**
     * Copies the staged fluid promise without losing the pattern metadata used by the crafting module.
     */
    @Override
    public PatternFluidCraftingPromise copy() {
        return copyWithAmount(getAmount());
    }

    /**
     * Returns a resized copy while preserving the source pattern slot and per-set result amount.
     * <p>
     * Staged branches slice promises as ingredients are requested in batches, so the copied promise must remain a
     * pattern-fluid promise instead of degrading to a generic fluid promise.
     */
    @Override
    public PatternFluidCraftingPromise copyWithAmount(int amount) {
        PatternFluidCraftingPromise copy = new PatternFluidCraftingPromise(
                getLiquid(),
                amount,
                getSender(),
                patternSlot,
                resultAmountPerSet);
        copy.setByproductTarget(byproductTarget);
        return copy;
    }
}
