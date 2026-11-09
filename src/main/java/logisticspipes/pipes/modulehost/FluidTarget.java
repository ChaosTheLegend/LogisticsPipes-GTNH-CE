package logisticspipes.pipes.modulehost;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * A tank a fluid module works on, with the side of it the module fills and drains.
 */
public final class FluidTarget {

    public final TileEntity tile;
    public final ForgeDirection side;

    public FluidTarget(TileEntity tile, ForgeDirection side) {
        this.tile = tile;
        this.side = side;
    }

    /**
     * Same tile object and same side.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FluidTarget)) {
            return false;
        }
        FluidTarget other = (FluidTarget) o;
        return tile == other.tile && side == other.side;
    }

    @Override
    public int hashCode() {
        return 31 * System.identityHashCode(tile) + side.hashCode();
    }
}
