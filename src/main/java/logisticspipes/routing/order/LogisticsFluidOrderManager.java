package logisticspipes.routing.order;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import logisticspipes.crafting.PatternByproductTarget;
import logisticspipes.interfaces.IChangeListener;
import logisticspipes.interfaces.ILPPositionProvider;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.routing.FluidLogisticsPromise;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.utils.FluidIdentifier;

public class LogisticsFluidOrderManager extends LogisticsOrderManager<LogisticsFluidOrder, FluidIdentifier> {

    private static class IC
            implements LogisticsOrderLinkedList.IIdentityProvider<LogisticsFluidOrder, FluidIdentifier> {

        @Override
        public FluidIdentifier getIdentity(LogisticsFluidOrder o) {
            return o.getFluid();
        }

        @Override
        public boolean isExtra(LogisticsFluidOrder o) {
            return o instanceof LogisticsFluidOrderExtra;
        }
    }

    private static class LogisticsFluidOrderExtra extends LogisticsFluidOrder {

        public LogisticsFluidOrderExtra(FluidIdentifier liquid, Integer amount, IRequestFluid destination,
                ResourceType type, IAdditionalTargetInformation info) {
            super(liquid, amount, destination, type, info);
        }
    }

    public LogisticsFluidOrderManager(ILPPositionProvider pos) {
        super(new LogisticsOrderLinkedList<>(new IC()), pos);
    }

    public LogisticsFluidOrderManager(IChangeListener listener, ILPPositionProvider pos) {
        super(listener, pos, new LogisticsOrderLinkedList<>(new IC()));
    }

    @Override
    public void sendFailed() {
        _orders.getFirst().sendFailed();
        super.sendFailed();
    }

    /**
     * Adds a normal fluid order for a requester or staged crafting output.
     */
    public LogisticsFluidOrder addOrder(FluidLogisticsPromise promise, IRequestFluid destination, ResourceType type,
            IAdditionalTargetInformation info) {
        if (promise.getAmount() < 0) {
            throw new RuntimeException("The amount can't be less than zero");
        }
        LogisticsFluidOrder order = new LogisticsFluidOrder(
                promise.getLiquid(),
                promise.getAmount(),
                destination,
                type,
                info);
        _orders.addLast(order);
        listen();
        return order;
    }

    /**
     * Adds a destinationless extra fluid order.
     * <p>
     * The crafting module drains these orders from the adjacent fluid handler and sends the resulting fluid container
     * with destination {@code -1}, allowing normal storage routing or dropping if no sink exists.
     */
    public LogisticsFluidOrderExtra addExtra(FluidIdentifier fluid, int amount) {
        if (amount < 0) {
            throw new RuntimeException("The amount can't be less than zero");
        }
        LogisticsFluidOrderExtra order = new LogisticsFluidOrderExtra(fluid, amount, null, ResourceType.EXTRA, null);
        _orders.addLast(order);
        listen();
        return order;
    }

    /**
     * Removes fluid extra orders that have been consumed by another request.
     * <p>
     * This mirrors the item extra-order flow: when an extra promise is fulfilled as a real request, the corresponding
     * destinationless extra order is reduced or removed so the same produced fluid is not extracted twice.
     */
    public void removeExtras(FluidIdentifier fluid, int amount) {
        removeExtras(fluid, amount, null, false);
    }

    public void removeExtras(FluidIdentifier fluid, int amount, PatternByproductTarget target) {
        removeExtras(fluid, amount, target, true);
    }

    private void removeExtras(FluidIdentifier fluid, int amount, PatternByproductTarget target, boolean matchTarget) {
        int fluidsToRemove = amount;
        Iterator<LogisticsFluidOrder> iter = _orders.iterator();
        List<LogisticsFluidOrder> toRemove = new LinkedList<>();
        while (iter.hasNext()) {
            LogisticsFluidOrder order = iter.next();
            if (order.getType() != ResourceType.EXTRA) {
                continue;
            }
            if (order.getFluid().equals(fluid)
                    && (!matchTarget || Objects.equals(target, order.getByproductTarget()))) {
                if (fluidsToRemove >= order.getAmount()) {
                    fluidsToRemove -= order.getAmount();
                    toRemove.add(order);
                    if (fluidsToRemove == 0) {
                        _orders.removeAll(toRemove);
                        listen();
                        return;
                    }
                } else {
                    order.reduceAmountBy(fluidsToRemove);
                    break;
                }
            }
        }
        _orders.removeAll(toRemove);
        listen();
    }

    public Integer totalFluidsCountInOrders(FluidIdentifier fluid) {
        int itemCount = 0;
        for (LogisticsFluidOrder request : _orders) {
            if (!request.getFluid().equals(fluid)) {
                continue;
            }
            itemCount += request.getAmount();
        }
        return itemCount;
    }
}
