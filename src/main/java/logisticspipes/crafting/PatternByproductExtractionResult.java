package logisticspipes.crafting;

import logisticspipes.logisticspipes.IRoutedItem;

/**
 * Result of one remote byproduct extraction, including the routed item used by order progress tracking.
 */
final class PatternByproductExtractionResult {

    private static final PatternByproductExtractionResult EMPTY = new PatternByproductExtractionResult(0, null);

    private final int amount;
    private final IRoutedItem routedItem;

    PatternByproductExtractionResult(int amount, IRoutedItem routedItem) {
        this.amount = amount;
        this.routedItem = routedItem;
    }

    static PatternByproductExtractionResult empty() {
        return EMPTY;
    }

    int amount() {
        return amount;
    }

    IRoutedItem routedItem() {
        return routedItem;
    }
}
