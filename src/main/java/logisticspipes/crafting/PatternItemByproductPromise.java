package logisticspipes.crafting;

import logisticspipes.interfaces.routing.IProvideItems;
import logisticspipes.request.IExtraPromise;
import logisticspipes.routing.LogisticsExtraPromise;
import logisticspipes.utils.item.ItemIdentifier;

/** Item byproduct promise that retains its selected extraction satellite. */
public final class PatternItemByproductPromise extends LogisticsExtraPromise implements PatternByproductPromise {

    private final PatternByproductTarget byproductTarget;

    public PatternItemByproductPromise(ItemIdentifier item, int amount, IProvideItems sender, boolean provided,
            PatternByproductTarget byproductTarget) {
        super(item, amount, sender, provided);
        this.byproductTarget = byproductTarget;
    }

    @Override
    public PatternByproductTarget getByproductTarget() {
        return byproductTarget;
    }

    @Override
    public PatternItemByproductPromise copy() {
        return new PatternItemByproductPromise(item, numberOfItems, sender, provided, byproductTarget);
    }

    @Override
    public IExtraPromise split(int more) {
        numberOfItems -= more;
        return new PatternItemByproductPromise(item, more, sender, false, byproductTarget);
    }
}
