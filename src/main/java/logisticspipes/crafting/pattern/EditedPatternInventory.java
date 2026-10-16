package logisticspipes.crafting.pattern;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

/**
 * Inventory view of one pattern's ingredient and result entries, backed by the pattern item's NBT.
 * <p>
 * Works for any {@link PatternSource}; the selected pattern is re-read from the source on every call.
 */
public class EditedPatternInventory implements IInventory {

    private final PatternSource source;
    private int patternSlot;

    public EditedPatternInventory(PatternSource source, int patternSlot) {
        this.source = source;
        setPatternSlot(patternSlot);
    }

    public void setPatternSlot(int patternSlot) {
        this.patternSlot = Math.max(0, Math.min(source.getPatternCount() - 1, patternSlot));
    }

    public ItemStack getPatternStack() {
        return source.getPatternStack(patternSlot);
    }

    @Override
    public int getSizeInventory() {
        return ItemPattern.MAX_ITEM_SLOT_COUNT;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return ItemPattern.fromStack(getPatternStack()).getStackInSlot(slot);
    }

    @Override
    public ItemStack decrStackSize(int slot, int count) {
        ItemStack stack = getStackInSlot(slot);
        if (stack == null) {
            return null;
        }
        ItemPattern.fromStack(getPatternStack()).setStackInSlot(slot, null);
        markDirty();
        return stack;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return getStackInSlot(slot);
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        ItemPattern.fromStack(getPatternStack()).setStackInSlot(slot, stack);
        markDirty();
    }

    @Override
    public String getInventoryName() {
        return "Pattern";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 127;
    }

    @Override
    public void markDirty() {
        source.markPatternChanged(patternSlot);
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return PatternSource.isPattern(getPatternStack());
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        // a pattern inside a pattern would nest NBT without bound (S7)
        return stack != null && !(stack.getItem() instanceof ItemPattern);
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}
}
