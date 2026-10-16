package logisticspipes.crafting;

import logisticspipes.interfaces.routing.IProvideFluids;
import logisticspipes.request.IExtraPromise;
import logisticspipes.routing.FluidExtraPromise;
import logisticspipes.utils.FluidIdentifier;

/** Fluid byproduct promise that retains its selected extraction satellite. */
public final class PatternFluidByproductPromise extends FluidExtraPromise implements PatternByproductPromise {

    private final PatternByproductTarget byproductTarget;

    public PatternFluidByproductPromise(FluidIdentifier fluid, int amount, IProvideFluids sender, boolean provided,
            PatternByproductTarget byproductTarget) {
        super(fluid, amount, sender, provided);
        this.byproductTarget = byproductTarget;
    }

    @Override
    public PatternByproductTarget getByproductTarget() {
        return byproductTarget;
    }

    @Override
    public PatternFluidByproductPromise copy() {
        return new PatternFluidByproductPromise(getLiquid(), getAmount(), getSender(), isProvided(), byproductTarget);
    }

    @Override
    public PatternFluidByproductPromise copyWithAmount(int amount) {
        return new PatternFluidByproductPromise(getLiquid(), amount, getSender(), isProvided(), byproductTarget);
    }

    @Override
    public IExtraPromise split(int more) {
        setAmount(getAmount() - more);
        return new PatternFluidByproductPromise(getLiquid(), more, getSender(), false, byproductTarget);
    }
}
