package logisticspipes.utils;

import java.util.ArrayList;

import net.minecraftforge.fluids.FluidStack;

import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.routing.IRouter;
import logisticspipes.utils.tuples.Pair;

/**
 * Fluid leaves a pipe as a fluid container item. The pipe queues the item itself, from the side it wants.
 */
public final class FluidSendUtil {

    private FluidSendUtil() {}

    /**
     * @param info what the destination has to know about the delivery, or null
     * @return the fluid as an item on its way to that router
     */
    public static IRoutedItem createItem(FluidStack stack, int destination, TransportMode mode,
            IAdditionalTargetInformation info) {
        IRoutedItem item = SimpleServiceLocator.routedItemHelper
                .createNewTravelItem(SimpleServiceLocator.logisticsFluidManager.getFluidContainer(stack));
        item.setDestination(destination);
        item.setTransportMode(mode);
        if (info != null) {
            item.setAdditionalTargetInformation(info);
        }
        return item;
    }

    /**
     * Looks for the sink in the network that takes the fluid.
     *
     * @param stack what is to be sent, the amount the sink takes is taken out of it
     * @return that amount as an item on its way to the sink, or null if no sink takes the fluid
     */
    public static IRoutedItem createItemForSink(FluidStack stack, IRouter source) {
        if (stack.amount <= 0) {
            return null;
        }
        Pair<Integer, Integer> reply = SimpleServiceLocator.logisticsFluidManager
                .getBestReply(stack, source, new ArrayList<>());
        if (reply.getValue1() == 0 || reply.getValue2() <= 0) {
            return null;
        }
        FluidStack toSend = stack.copy();
        toSend.amount = Math.min(stack.amount, reply.getValue2());
        stack.amount -= toSend.amount;
        return createItem(toSend, reply.getValue1(), TransportMode.Passive, null);
    }
}
