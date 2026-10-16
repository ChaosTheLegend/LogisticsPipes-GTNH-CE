package logisticspipes.proxy.gtnh;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.items.MetaBaseItem;
import logisticspipes.interfaces.IModuleInventory;
import logisticspipes.proxy.ic2.IC2Proxy;
import logisticspipes.proxy.interfaces.ICraftingParts;
import logisticspipes.proxy.interfaces.IIC2Proxy;

/**
 * Proxy for GTNH and related mods
 */
public class GTNHProxy implements IIC2Proxy {

    private IIC2Proxy ic2Proxy;

    public GTNHProxy() {
        super();
        ic2Proxy = new IC2Proxy();
    }

    @Override
    public boolean isElectricItem(ItemStack stack) {

        if (stack == null) return false;

        if (!(stack.getItem() instanceof MetaBaseItem gtMetaItem)) {
            return (ic2Proxy.isElectricItem(stack));
        }

        double charge = gtMetaItem.getMaxCharge(stack);
        return (charge > 0);
    }

    @Override
    public boolean isSimilarElectricItem(ItemStack stack, ItemStack template) {
        if (ic2Proxy.isSimilarElectricItem(stack, template)) return true;

        if (stack == null || template == null || !isElectricItem(template)) return false;

        return template.getItem() == stack.getItem();
    }

    @Override
    public boolean isFullyCharged(ItemStack stack) {

        if (!(stack.getItem() instanceof MetaBaseItem gtMetaItem)) {
            return (ic2Proxy.isFullyCharged(stack));
        }

        return gtMetaItem.getCharge(stack) == gtMetaItem.getMaxCharge(stack);
    }

    @Override
    public boolean isFullyDischarged(ItemStack stack) {
        if (!(stack.getItem() instanceof MetaBaseItem gtMetaItem)) {
            return (ic2Proxy.isFullyDischarged(stack));
        }
        var charge = gtMetaItem.getCharge(stack);
        var tier = gtMetaItem.getTier(stack);
        return charge < tier;
    }

    @Override
    public boolean isPartiallyCharged(ItemStack stack) {
        if (!(stack.getItem() instanceof MetaBaseItem gtMetaItem)) {
            return (ic2Proxy.isPartiallyCharged(stack));
        }

        return gtMetaItem.getCharge(stack) > 0;
    }

    @Override
    public void addCraftingRecipes(ICraftingParts parts) {
        ic2Proxy.addCraftingRecipes(parts);
    }

    @Override
    public boolean hasIC2() {
        return true;
    }

    @Override
    public void registerToEneryNet(TileEntity tile) {
        ic2Proxy.registerToEneryNet(tile);
    }

    @Override
    public void unregisterToEneryNet(TileEntity tile) {
        ic2Proxy.unregisterToEneryNet(tile);
    }

    @Override
    public boolean acceptsEnergyFrom(TileEntity energy, TileEntity tile, ForgeDirection opposite) {
        return ic2Proxy.acceptsEnergyFrom(energy, tile, opposite);
    }

    @Override
    public boolean isEnergySink(TileEntity tile) {
        return ic2Proxy.isEnergySink(tile);
    }

    @Override
    public double demandedEnergyUnits(TileEntity tile) {
        return ic2Proxy.demandedEnergyUnits(tile);
    }

    @Override
    public double injectEnergyUnits(TileEntity tile, ForgeDirection opposite, double d) {
        return ic2Proxy.injectEnergyUnits(tile, opposite, d);
    }

    /**
     * GT's sided inventories hide battery slots (battery buffers, machine battery slots, battery hatches), so the
     * electric manager gets a view of just those slots. See {@link GTBatterySlotInventory}.
     */
    @Override
    public IModuleInventory getElectricItemInventory(TileEntity tile, ForgeDirection side) {
        if (tile instanceof IGregTechTileEntity gtTile) {
            IModuleInventory batterySlots = GTBatterySlotInventory.of(gtTile, side);
            if (batterySlots != null) {
                return batterySlots;
            }
        }
        return ic2Proxy.getElectricItemInventory(tile, side);
    }
}
