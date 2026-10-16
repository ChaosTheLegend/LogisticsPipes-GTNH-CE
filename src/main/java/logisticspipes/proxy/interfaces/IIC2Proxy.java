package logisticspipes.proxy.interfaces;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.interfaces.IModuleInventory;

public interface IIC2Proxy {

    boolean isElectricItem(ItemStack stack);

    boolean isSimilarElectricItem(ItemStack stack, ItemStack template);

    boolean isFullyCharged(ItemStack stack);

    boolean isFullyDischarged(ItemStack stack);

    boolean isPartiallyCharged(ItemStack stack);

    void addCraftingRecipes(ICraftingParts parts);

    boolean hasIC2();

    void registerToEneryNet(TileEntity tile);

    void unregisterToEneryNet(TileEntity tile);

    boolean acceptsEnergyFrom(TileEntity energy, TileEntity tile, ForgeDirection opposite);

    boolean isEnergySink(TileEntity tile);

    double demandedEnergyUnits(TileEntity tile);

    double injectEnergyUnits(TileEntity tile, ForgeDirection opposite, double d);

    /**
     * Returns a view of a machine's battery slots that its normal sided inventory hides (GT battery buffers, machine
     * battery slots), for the electric manager module to insert into and extract from.
     *
     * @return the view, or null to use the normal inventory
     */
    IModuleInventory getElectricItemInventory(TileEntity tile, ForgeDirection side);
}
