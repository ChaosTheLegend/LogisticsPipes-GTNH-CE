package logisticspipes.crafting;

import com.github.bsideup.jabel.Desugar;

import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.interfaces.routing.IRequestItems;

/**
 * One pattern input ingredient together with the destination that should receive it.
 * <p>
 * Null item and fluid targets mean the ingredient is inserted into the crafting pipe's adjacent target. A non-null
 * target means the ingredient is buffered by the main pattern crafting pipe first and dispatched to the linked pattern
 * satellite only when a complete pattern set is ready.
 */
@Desugar
record PatternIngredientTarget(int inputSlot, IPatternStack stack, IRequestItems itemTarget,
        IRequestFluid fluidTarget) {

    boolean isLocal() {
        return itemTarget == null && fluidTarget == null;
    }

    boolean hasSatelliteTarget() {
        return !isLocal();
    }
}
