package logisticspipes.interfaces.routing;

import net.minecraftforge.fluids.FluidStack;

import logisticspipes.utils.SinkReply.FixedPriority;

/**
 * A pipe that takes fluid passively. It has to announce the fluids it takes as interests of its router, the search for
 * a destination only asks the routers that are interested in the fluid.
 */
public interface IFluidSink {

    /**
     * @return how much of the stack this sink takes right now
     */
    int sinkAmount(FluidStack stack);

    /**
     * @return the priority this sink takes the fluid with, same scale as the item sinks. A sink with a higher priority
     *         is preferred even when it is further away.
     */
    default FixedPriority getFluidSinkPriority(FluidStack stack) {
        return FixedPriority.ItemSink;
    }
}
