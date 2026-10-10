package logisticspipes.interfaces;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.routing.IRouter;
import logisticspipes.utils.FluidIdentifier;

/**
 * What a fluid module needs from the pipe it sits in: the tank it works on and a way to send fluid into the network.
 */
public interface IFluidModuleHost extends ILPPositionProvider {

    /**
     * @return the tile the host points at if it holds fluid, else null
     */
    TileEntity getFluidTargetTile();

    /**
     * @return the side of the target tile the module in that slot fills and drains
     */
    ForgeDirection getFluidTargetSide(int moduleSlot);

    ForgeDirection getPointedOrientation();

    IRouter getRouter();

    /**
     * @return the amount of that fluid already on its way to the host
     */
    int countOnRoute(FluidIdentifier fluid);

    void queueRoutedItem(IRoutedItem routedItem, ForgeDirection from);
}
