package logisticspipes.utils;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import logisticspipes.interfaces.ISpecialTankAccessHandler;
import logisticspipes.proxy.SimpleServiceLocator;

/**
 * The tank a fluid pipe or a fluid module works on. A tile with a special tank handler, like an ME interface, is always
 * read and changed through that handler, also when it is a fluid handler too: the handler answers for the whole of what
 * stands behind the tile, and what is read has to match what is filled and drained.
 */
public final class FluidTankUtil {

    // a bucket, some tanks only take whole ones
    private static final int ROOM_PROBE_AMOUNT = 1000;

    private FluidTankUtil() {}

    /**
     * @param side   the side of the tile that is filled
     * @param doFill false to only ask how much would be taken
     * @return the amount of the stack that was taken
     */
    public static int fill(TileEntity tile, ForgeDirection side, FluidStack stack, boolean doFill) {
        if (tile == null || stack == null || stack.amount <= 0) {
            return 0;
        }
        ISpecialTankAccessHandler special = getSpecialHandler(tile);
        if (special != null) {
            return special.fillInto(tile, stack.copy(), doFill);
        }
        return tile instanceof IFluidHandler handler ? handler.fill(side, stack.copy(), doFill) : 0;
    }

    /**
     * @param side the side of the tile that is filled
     * @return how much of that fluid the tile still takes
     */
    public static long getRoom(TileEntity tile, ForgeDirection side, FluidIdentifier fluid) {
        if (tile == null) {
            return 0;
        }
        ISpecialTankAccessHandler special = getSpecialHandler(tile);
        if (special != null) {
            return special.fillInto(tile, fluid.makeFluidStack(Integer.MAX_VALUE), false);
        }
        if (!(tile instanceof IFluidHandler handler)) {
            return 0;
        }
        // make sure this side can be filled at all, it may be an output tank
        if (handler.fill(side, fluid.makeFluidStack(ROOM_PROBE_AMOUNT), false) <= 0) {
            return 0;
        }
        // tanks may report a capacity of Integer.MAX_VALUE each, like a GT super tank set to void
        long room = 0;
        FluidTankInfo[] tanks = handler.getTankInfo(side);
        if (tanks != null) {
            for (FluidTankInfo tank : tanks) {
                if (tank == null) {
                    continue;
                }
                if (tank.fluid == null || tank.fluid.getFluidID() <= 0) {
                    room += tank.capacity;
                } else if (fluid.equals(FluidIdentifier.get(tank.fluid))) {
                    room += tank.capacity - tank.fluid.amount;
                }
            }
        }
        return room;
    }

    /**
     * @param side the side of the tile that is looked at
     * @return how much of that fluid the tile holds, drainable or not. For a tile with a special tank handler that is
     *         what the handler reports, so for an ME network the content of the whole network.
     */
    public static long getStored(TileEntity tile, ForgeDirection side, FluidIdentifier fluid) {
        Long stored = getContents(tile, side, false).get(fluid);
        return stored == null ? 0 : stored;
    }

    /**
     * @param side          the side of the tile that is looked at
     * @param drainableOnly true to leave out what that side of a fluid handler doesn't give out
     * @return the fluids in the tile and their amounts
     */
    public static Map<FluidIdentifier, Long> getContents(TileEntity tile, ForgeDirection side, boolean drainableOnly) {
        Map<FluidIdentifier, Long> contents = new LinkedHashMap<>();
        if (tile == null) {
            return contents;
        }
        ISpecialTankAccessHandler special = getSpecialHandler(tile);
        if (special != null) {
            contents.putAll(special.getAvailableLiquid(tile));
            return contents;
        }
        if (!(tile instanceof IFluidHandler handler)) {
            return contents;
        }
        FluidTankInfo[] tanks = handler.getTankInfo(side);
        if (tanks == null) {
            return contents;
        }
        for (FluidTankInfo tank : tanks) {
            if (tank == null || tank.fluid == null || tank.fluid.getFluidID() == 0) {
                continue;
            }
            if (!drainableOnly || handler.canDrain(side, tank.fluid.getFluid())) {
                contents.merge(FluidIdentifier.get(tank.fluid), (long) tank.fluid.amount, Long::sum);
            }
        }
        return contents;
    }

    /**
     * Drains up to that amount of the fluid from the tile.
     *
     * @param side the side of the tile that is drained
     * @return what was drained, or null
     */
    public static FluidStack drain(TileEntity tile, ForgeDirection side, FluidIdentifier fluid, int amount) {
        if (tile == null || amount <= 0) {
            return null;
        }
        ISpecialTankAccessHandler special = getSpecialHandler(tile);
        if (special != null) {
            return isOf(special.drainFrom(tile, fluid, amount, false), fluid)
                    ? special.drainFrom(tile, fluid, amount, true)
                    : null;
        }
        if (!(tile instanceof IFluidHandler handler)) {
            return null;
        }
        FluidStack simulated = handler.drain(side, fluid.makeFluidStack(amount), false);
        if (isOf(simulated, fluid)) {
            return handler.drain(side, fluid.makeFluidStack(simulated.amount), true);
        }
        // tanks that only answer the untyped drain
        simulated = handler.drain(side, amount, false);
        return isOf(simulated, fluid) ? handler.drain(side, simulated.amount, true) : null;
    }

    private static boolean isOf(FluidStack stack, FluidIdentifier fluid) {
        return stack != null && stack.amount > 0 && fluid.equals(FluidIdentifier.get(stack));
    }

    private static ISpecialTankAccessHandler getSpecialHandler(TileEntity tile) {
        return SimpleServiceLocator.specialTankHandler.getAccessHandlerFor(tile);
    }
}
