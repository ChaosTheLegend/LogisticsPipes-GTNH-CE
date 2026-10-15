/*
 * "LogisticsPipes" is distributed under the terms of the Minecraft Mod Public License 1.0, or MMPL. Please check the
 * contents of the license located in http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package logisticspipes.logisticspipes;

import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.routing.ExitRoute;
import logisticspipes.routing.IRouter;
import logisticspipes.transport.LPTravelingItem.LPTravelingItemServer;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * @author Krapht This class is responsible for resolving where incoming items should go.
 */
public class RouteLayer {

    protected final IRouter _router;
    private final TransportLayer _transport;
    private final CoreRoutedPipe _pipe;

    public RouteLayer(IRouter router, TransportLayer transportLayer, CoreRoutedPipe pipe) {
        _router = router;
        _transport = transportLayer;
        _pipe = pipe;
    }

    public ForgeDirection getOrientationForItem(IRoutedItem item, ForgeDirection blocked) {
        return getOrientationForItem(item, blocked, false);
    }

    /**
     * @param splitOverflow the item is travelling in this pipe: when a destination is assigned that has room for only
     *                      part of the stack, the rest is split off here and routed again at once
     */
    public ForgeDirection getOrientationForItem(IRoutedItem item, ForgeDirection blocked, boolean splitOverflow) {

        item.checkIDFromUUID();
        // If a item has no destination, find one
        if (item.getDestination() < 0) {
            assignDestination(item, false, splitOverflow);
            _pipe.debug.log("No Destination, assigned new destination: (%s)", item.getInfo());
        }

        // If the destination is unknown / unroutable or it already arrived at its destination and somehow looped back
        if (item.getDestination() >= 0 && (!_router.hasRoute(
                item.getDestination(),
                item.getTransportMode() == TransportMode.Active,
                item.getItemIdentifierStack().getItem()) || item.getArrived())) {
            assignDestination(item, false, splitOverflow);
            _pipe.debug.log("Unreachable Destination, assigned new destination: (%s)", item.getInfo());
        }

        item.checkIDFromUUID();
        // If we still have no destination or client side unroutable, drop it
        if (item.getDestination() < 0) {
            return ForgeDirection.UNKNOWN;
        }

        // Is the destination ourself? Deliver it
        if (item.getDestinationUUID().equals(_router.getId())) {

            _transport.handleItem(item);

            if (item.getDistanceTracker() != null) {
                item.getDistanceTracker().setCurrentDistanceToTarget(0);
                item.getDistanceTracker().setDestinationReached();
            }

            if (item.getTransportMode() != TransportMode.Active && !_transport.stillWantItem(item)) {
                assignDestination(item, true, splitOverflow);
                return getOrientationForItem(item, null, splitOverflow);
            }

            item.setDoNotBuffer(true);
            item.setArrived(true);
            ForgeDirection o = _transport.itemArrived(item, blocked);
            return o != null ? o : ForgeDirection.UNKNOWN;
        }

        // Do we now know the destination?
        if (!_router.hasRoute(
                item.getDestination(),
                item.getTransportMode() == TransportMode.Active,
                item.getItemIdentifierStack().getItem())) {
            return ForgeDirection.UNKNOWN;
        }

        // Which direction should we send it
        ExitRoute exit = _router.getExitFor(
                item.getDestination(),
                item.getTransportMode() == TransportMode.Active,
                item.getItemIdentifierStack().getItem());
        if (exit == null) {
            return ForgeDirection.UNKNOWN;
        }

        if (item.getDistanceTracker() != null) {
            item.getDistanceTracker().setCurrentDistanceToTarget(exit.blockDistance);
        }

        return exit.exitOrientation;
    }

    private void assignDestination(IRoutedItem item, boolean excludeSource, boolean splitOverflow) {
        int room = SimpleServiceLocator.logisticsManager
                .assignDestinationWithLimit(item, _router.getSimpleID(), excludeSource);
        if (splitOverflow && room > 0 && item.getDestination() >= 0) {
            splitOverflow(item, room);
        }
    }

    /**
     * Keeps {@code room} items on {@code item} and injects the rest into this pipe as a new item without a destination,
     * so it is routed to the next destination with room right away. Without this, a stack sent to a small inventory
     * bounces off it and is rerouted from there, once per inventory it fills, each time travelling another hop.
     */
    private void splitOverflow(IRoutedItem item, int room) {
        ItemIdentifierStack stack = item.getItemIdentifierStack();
        if (stack.getStackSize() <= room || stack.getItem().isFluidContainer()
                || !(item instanceof LPTravelingItemServer)) {
            return;
        }
        LPTravelingItemServer original = (LPTravelingItemServer) item;
        ItemIdentifierStack rest = stack.clone();
        rest.setStackSize(stack.getStackSize() - room);
        // the original's in-transit entry reads its stack live, so the destination now counts only what it keeps
        stack.setStackSize(room);
        LPTravelingItemServer overflow = new LPTravelingItemServer(rest);
        overflow.setSpeed(original.getSpeed());
        _pipe.transport.injectItem(overflow, original.input);
    }
}
