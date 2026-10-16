package logisticspipes.crafting;

import com.github.bsideup.jabel.Desugar;

import logisticspipes.crafting.patternStack.IPatternStack;

/**
 * Concrete buffered ingredient selected for one pattern input slot.
 * <p>
 * The stack is the actual item or fluid that arrived, not necessarily the representative stack stored in the pattern.
 */
@Desugar
record PatternIngredientAssignment(int inputSlot, IPatternStack stack) {}
