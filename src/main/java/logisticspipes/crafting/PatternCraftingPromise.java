package logisticspipes.crafting;

import logisticspipes.interfaces.routing.IProvideItems;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.utils.item.ItemIdentifier;
import lombok.Getter;

@Getter
public class PatternCraftingPromise extends LogisticsPromise implements PatternByproductPromise {

    private final int patternSlot;
    private final int resultAmountPerSet;
    private PatternByproductTarget byproductTarget;

    public void setByproductTarget(PatternByproductTarget target) {
        byproductTarget = target;
    }

    /** Keeps excess output tied to the same satellite as the requested part of the recipe result. */
    @Override
    public PatternItemByproductPromise split(int more) {
        numberOfItems -= more;
        return new PatternItemByproductPromise(item, more, sender, false, byproductTarget);
    }

    public PatternCraftingPromise(ItemIdentifier item, int numberOfItems, IProvideItems sender, int patternSlot,
            int resultAmountPerSet) {
        super(item, numberOfItems, sender, ResourceType.CRAFTING);
        this.patternSlot = patternSlot;
        this.resultAmountPerSet = resultAmountPerSet;
    }

    @Override
    public PatternCraftingPromise copy() {
        return copyWithAmount(numberOfItems);
    }

    /**
     * Returns a resized copy while preserving the pattern metadata needed for staged crafting.
     */
    public PatternCraftingPromise copyWithAmount(int amount) {
        PatternCraftingPromise copy = new PatternCraftingPromise(item, amount, sender, patternSlot, resultAmountPerSet);
        copy.setByproductTarget(byproductTarget);
        return copy;
    }
}
