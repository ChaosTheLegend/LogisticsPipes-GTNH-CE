package logisticspipes.crafting.v3.mc;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import logisticspipes.crafting.v3.exec.KeyCodec;
import logisticspipes.crafting.v3.model.ResourceKey;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;

/** Saves item keys as item stacks and fluid keys as fluid stacks; unknown items/fluids load as null. */
public final class McKeyCodec implements KeyCodec {

    public static final McKeyCodec INSTANCE = new McKeyCodec();

    private McKeyCodec() {}

    @Override
    public void write(ResourceKey key, NBTTagCompound tag) {
        if (key instanceof ItemKey) {
            tag.setByte("kt", (byte) 0);
            NBTTagCompound item = new NBTTagCompound();
            ((ItemKey) key).id.makeNormalStack(1).writeToNBT(item);
            tag.setTag("ki", item);
        } else if (key instanceof FluidKey) {
            tag.setByte("kt", (byte) 1);
            NBTTagCompound fluid = new NBTTagCompound();
            ((FluidKey) key).id.makeFluidStack(1).writeToNBT(fluid);
            tag.setTag("kf", fluid);
        } else {
            throw new IllegalArgumentException("unknown key type " + key);
        }
    }

    @Override
    public ResourceKey read(NBTTagCompound tag) {
        if (tag.getByte("kt") == 1) {
            FluidStack stack = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag("kf"));
            return stack == null ? null : FluidKey.of(FluidIdentifier.get(stack));
        }
        if (!tag.hasKey("ki")) return null;
        ItemStack stack = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("ki"));
        return stack == null || stack.getItem() == null ? null : ItemKey.of(ItemIdentifier.get(stack));
    }
}
