package logisticspipes.crafting;

import com.github.bsideup.jabel.Desugar;

import logisticspipes.interfaces.routing.IAdditionalTargetInformation;

@Desugar
public record PatternTargetInformation(int patternSlot, int inputSlot, PatternCraftingReference orderReference,
        PatternCraftingReference deliveryReference) implements IAdditionalTargetInformation {

    public static final int NO_INPUT_SLOT = -1;
    public static final int NO_PATTERN_SLOT = -1;

    public PatternTargetInformation(int patternSlot) {
        this(patternSlot, NO_INPUT_SLOT, null, null);
    }

    public PatternTargetInformation(int patternSlot, int inputSlot) {
        this(patternSlot, inputSlot, null, null);
    }

    public static PatternTargetInformation delivery(int patternSlot, int inputSlot,
            PatternCraftingReference orderReference) {
        if (orderReference == null) {
            throw new IllegalArgumentException("A crafting delivery requires an owning order reference");
        }
        return new PatternTargetInformation(patternSlot, inputSlot, orderReference, orderReference.createChild());
    }

    public boolean isTracked() {
        return orderReference != null && deliveryReference != null;
    }
}
