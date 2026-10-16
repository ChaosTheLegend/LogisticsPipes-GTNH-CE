package logisticspipes.crafting.patternStack;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import codechicken.nei.recipe.StackInfo;
import cpw.mods.fml.common.Loader;
import gregtech.api.util.GTUtility;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import lombok.Getter;

@Getter
public class PatternFluidStack implements IPatternStack {

    private final FluidIdentifier fluid;
    private int amount;

    public PatternFluidStack(FluidIdentifier fluid, int amount) {
        this.fluid = fluid;
        this.amount = Math.max(0, amount);
    }

    public static PatternFluidStack fromItemStack(ItemStack stack) {
        if (stack == null) return null;

        FluidStack fluid = null;

        stack.getItem();

        fluid = getFluidStack(stack, fluid);

        if (fluid == null) return null;

        fluid = fluid.copy();
        fluid.amount *= stack.stackSize;

        return new PatternFluidStack(FluidIdentifier.get(fluid), fluid.amount);
    }

    public static FluidStack getFluidStack(ItemStack stack, FluidStack fluid) {
        if (stack.getItem() instanceof IFluidContainerItem) {
            fluid = ((IFluidContainerItem) stack.getItem()).getFluid(stack);
        } else if (FluidContainerRegistry.isContainer(stack)) {
            fluid = FluidContainerRegistry.getFluidForFilledItem(stack);
        }
        if (fluid == null) {
            fluid = SimpleServiceLocator.logisticsFluidManager
                    .getFluidFromContainer(ItemIdentifierStack.getFromStack(stack));
        }
        if (fluid == null) {
            fluid = StackInfo.getFluid(stack);
        }
        return fluid;
    }

    public static PatternFluidStack readFromNBT(NBTTagCompound tag) {
        FluidStack fluidStack = FluidStack.loadFluidStackFromNBT(tag);
        if (fluidStack == null) {
            return null;
        }
        return new PatternFluidStack(FluidIdentifier.get(fluidStack), fluidStack.amount);
    }

    public static PatternFluidStack fromFluidStack(FluidStack stack) {
        if (stack == null || stack.amount <= 0) {
            return null;
        }
        return new PatternFluidStack(FluidIdentifier.get(stack), stack.amount);
    }

    @Override
    public void addAmount(int amount) {
        this.amount += amount;
    }

    public FluidStack makeFluidStack() {
        return fluid.makeFluidStack(amount);
    }

    public ItemIdentifierStack makeDisplayStack() {
        return fluid.getItemIdentifier().makeStack(amount);
    }

    @Override
    public ItemStack makeDisplayItemStack() {
        // Use the same display item as NEI, including pattern-item output previews.
        if (Loader.isModLoaded("gregtech")) {
            return GTUtility.getFluidDisplayStack(makeFluidStack(), true);
        }
        return makeDisplayStack().makeNormalStack();
    }

    @Override
    public ItemStack makePatternStack() {
        return SimpleServiceLocator.logisticsFluidManager.getFluidContainer(makeFluidStack()).makeNormalStack();
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        makeFluidStack().writeToNBT(tag);
        return tag;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setString(TYPE_TAG, TYPE_FLUID);
        tag.setTag(FLUID_TAG, writeToNBT());
    }

    @Override
    public Item getItem() {
        return fluid.getItemIdentifier().item;
    }

    @Override
    public boolean canMerge(IPatternStack other) {
        return other instanceof PatternFluidStack && fluid.equals(((PatternFluidStack) other).fluid);
    }

    @Override
    public PatternFluidStack copy() {
        return new PatternFluidStack(fluid, amount);
    }

    @Override
    public String toString() {
        return amount + "mB " + fluid;
    }
}
