package logisticspipes.interfaces;

import java.util.List;

import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.pipes.modulehost.FluidTarget;
import logisticspipes.routing.IRouter;
import logisticspipes.utils.FluidIdentifier;

/**
 * What a fluid module needs from the pipe it sits in: the tanks it works on and a way to send fluid into the network.
 */
public interface IFluidModuleHost extends ILPPositionProvider {

    /**
     * @return the tanks the module in that slot works on, each with the side it fills and drains; empty if none
     */
    List<FluidTarget> getFluidTargets(int moduleSlot);

    ForgeDirection getPointedOrientation();

    IRouter getRouter();

    /**
     * @return the amount of that fluid already on its way to the host
     */
    int countOnRoute(FluidIdentifier fluid);

    /**
     * @return the amount of that fluid in the tank the modules of the host have promised and not handed out yet
     */
    int getReservedFluid(FluidIdentifier fluid);

    void queueRoutedItem(IRoutedItem routedItem, ForgeDirection from);
}
