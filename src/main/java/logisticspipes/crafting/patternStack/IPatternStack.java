package logisticspipes.crafting.patternStack;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public interface IPatternStack {

    String TYPE_TAG = "patternStackType";
    String TYPE_SOLID = "solid";
    String TYPE_FLUID = "fluid";
    String FLUID_TAG = "fluid";

    static IPatternStack fromItemStack(ItemStack stack) {
        PatternFluidStack fluid = PatternFluidStack.fromItemStack(stack);
        if (fluid != null) {
            return fluid;
        }
        return PatternItemStack.fromItemStack(stack);
    }

    static IPatternStack readFromNBT(NBTTagCompound tag) {
        String type = tag.getString(TYPE_TAG);
        if (TYPE_FLUID.equals(type)) {
            return PatternFluidStack.readFromNBT(tag.getCompoundTag(FLUID_TAG));
        }
        if (TYPE_SOLID.equals(type)) {
            return PatternItemStack.readFromNBT(tag);
        }

        ItemStack legacyStack = ItemStack.loadItemStackFromNBT(tag);
        return fromItemStack(legacyStack);
    }

    int getAmount();

    void addAmount(int amount);

    boolean canMerge(IPatternStack other);

    IPatternStack copy();

    ItemStack makePatternStack();

    ItemStack makeDisplayItemStack();

    void writeToNBT(NBTTagCompound tag);

    Item getItem();
}
