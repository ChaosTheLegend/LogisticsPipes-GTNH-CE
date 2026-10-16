package logisticspipes.crafting.patternStack;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import logisticspipes.utils.item.ItemIdentifierStack;

public class PatternItemStack implements IPatternStack {

    /**
     * Vanilla stores {@code Count} as a byte, so amounts above 127 wrap on save. The real amount is written as an int
     * next to it and preferred on load.
     */
    private static final String AMOUNT_TAG = "lpCount";

    private final ItemIdentifierStack stack;

    public PatternItemStack(ItemIdentifierStack stack) {
        this.stack = stack;
    }

    public static PatternItemStack fromItemStack(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) {
            return null;
        }
        return new PatternItemStack(ItemIdentifierStack.getFromStack(stack));
    }

    public static PatternItemStack readFromNBT(NBTTagCompound tag) {
        ItemIdentifierStack stack = readItem(tag);
        return stack == null || stack.getStackSize() <= 0 ? null : new PatternItemStack(stack);
    }

    /**
     * Writes an item stack of any amount; see {@link #readItem(NBTTagCompound)}.
     */
    public static void writeItem(NBTTagCompound tag, ItemIdentifierStack stack) {
        stack.makeNormalStack().writeToNBT(tag);
        tag.setInteger(AMOUNT_TAG, stack.getStackSize());
    }

    /**
     * Reads a stack written by {@link #writeItem}, or a plain vanilla stack from older saves.
     * <p>
     * Patterns saved by the crafting_rework_2 branch store the amount as an int {@code Count} instead of
     * {@link #AMOUNT_TAG}; {@code getInteger} reads both that and the vanilla byte.
     */
    public static ItemIdentifierStack readItem(NBTTagCompound tag) {
        ItemStack stack = ItemStack.loadItemStackFromNBT(tag);
        if (stack == null) {
            return null;
        }
        stack.stackSize = tag.hasKey(AMOUNT_TAG) ? tag.getInteger(AMOUNT_TAG) : tag.getInteger("Count");
        return ItemIdentifierStack.getFromStack(stack);
    }

    public ItemIdentifierStack getItemIdentifierStack() {
        return stack;
    }

    @Override
    public int getAmount() {
        return stack.getStackSize();
    }

    @Override
    public void addAmount(int amount) {
        stack.setStackSize(stack.getStackSize() + amount);
    }

    @Override
    public boolean canMerge(IPatternStack other) {
        return other instanceof PatternItemStack && stack.getItem().equals(((PatternItemStack) other).stack.getItem());
    }

    @Override
    public PatternItemStack copy() {
        return new PatternItemStack(stack.clone());
    }

    @Override
    public ItemStack makePatternStack() {
        return stack.makeNormalStack();
    }

    @Override
    public ItemStack makeDisplayItemStack() {
        return makePatternStack();
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        writeItem(tag, stack);
        tag.setString(TYPE_TAG, TYPE_SOLID);
    }

    @Override
    public String toString() {
        return stack.toString();
    }

    @Override
    public Item getItem() {
        return stack.getItem().item;
    }
}
