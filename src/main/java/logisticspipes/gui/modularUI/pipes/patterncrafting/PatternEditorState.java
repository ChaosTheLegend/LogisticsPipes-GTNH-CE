package logisticspipes.gui.modularUI.pipes.patterncrafting;

import net.minecraft.item.ItemStack;

import logisticspipes.crafting.pattern.AbstractPattern;
import logisticspipes.crafting.pattern.EditedPatternInventory;
import logisticspipes.crafting.pattern.ItemPattern;
import logisticspipes.crafting.pattern.PatternSource;

/**
 * Per-GUI editing state of a pattern editor: which pattern of the {@link PatternSource} is being edited.
 * <p>
 * One instance exists on each side; the selection is kept in sync by {@link PatternEditorSyncHandler}.
 */
public class PatternEditorState {

    public static final int PATTERN_SLOTS = 9;

    private final PatternSource source;
    private final EditedPatternInventory editedInventory;
    private int selectedSlot;

    public PatternEditorState(PatternSource source, int selectedSlot) {
        this.source = source;
        this.selectedSlot = clamp(selectedSlot);
        this.editedInventory = new EditedPatternInventory(source, this.selectedSlot);
    }

    public PatternSource getSource() {
        return source;
    }

    /**
     * Inventory view of the selected pattern's ingredient and result slots.
     */
    public EditedPatternInventory getEditedInventory() {
        return editedInventory;
    }

    public int getSelectedSlot() {
        return selectedSlot;
    }

    public int getPatternCount() {
        return source.getPatternCount();
    }

    public void select(int slot) {
        selectedSlot = clamp(slot);
        editedInventory.setPatternSlot(selectedSlot);
    }

    public ItemStack getPatternStack() {
        return getPatternStack(selectedSlot);
    }

    public ItemStack getPatternStack(int slot) {
        return source.getPatternStack(slot);
    }

    public boolean hasPattern() {
        return isPattern(getPatternStack());
    }

    public AbstractPattern getPattern() {
        return ItemPattern.fromStack(getPatternStack());
    }

    public boolean isProcessing() {
        return ItemPattern.isProcessingPattern(getPatternStack());
    }

    /**
     * Tells the source that the selected pattern's NBT changed.
     */
    public void markChanged() {
        source.markPatternChanged(selectedSlot);
    }

    /**
     * Picks the slot the GUI should open on: the preferred slot if it holds a pattern, otherwise the first slot that
     * does.
     */
    public static int findInitialSlot(PatternSource source, int preferredSlot) {
        int preferred = Math.max(0, Math.min(source.getPatternCount() - 1, preferredSlot));
        if (isPattern(source.getPatternStack(preferred))) {
            return preferred;
        }
        for (int slot = 0; slot < source.getPatternCount(); slot++) {
            if (isPattern(source.getPatternStack(slot))) {
                return slot;
            }
        }
        return 0;
    }

    public static boolean isPattern(ItemStack stack) {
        return PatternSource.isPattern(stack);
    }

    private int clamp(int slot) {
        return Math.max(0, Math.min(source.getPatternCount() - 1, slot));
    }
}
