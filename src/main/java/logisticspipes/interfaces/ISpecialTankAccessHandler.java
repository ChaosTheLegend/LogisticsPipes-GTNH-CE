package logisticspipes.interfaces;

import java.util.Map;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.FluidStack;

import logisticspipes.utils.FluidIdentifier;

public interface ISpecialTankAccessHandler extends ISpecialTankHandler {

    Map<FluidIdentifier, Long> getAvailableLiquid(TileEntity tile);

    FluidStack drainFrom(TileEntity tile, FluidIdentifier ident, Integer amount, boolean drain);

    /**
     * @param fill false to only ask how much would be taken
     * @return the amount of the stack that was taken
     */
    int fillInto(TileEntity tile, FluidStack stack, boolean fill);
}
