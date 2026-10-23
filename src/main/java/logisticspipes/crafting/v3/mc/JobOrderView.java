package logisticspipes.crafting.v3.mc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import logisticspipes.crafting.v3.exec.CraftingDriver;
import logisticspipes.crafting.v3.exec.CraftingJob;
import logisticspipes.crafting.v3.exec.SetState;
import logisticspipes.crafting.v3.exec.Ticket;
import logisticspipes.crafting.v3.exec.TicketState;
import logisticspipes.crafting.v3.model.ResourceKey;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.routing.order.LinkedLogisticsOrderList;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.tuples.LPPosition;

/**
 * A crafting job as an order tree, so the request table's crafting monitor (which samples {@link IOrderInfoProvider}s)
 * shows v3 jobs: the delivery on top, then one entry per crafting step, deepest last. Every entry reads the live job;
 * once the job is gone, all entries report finished.
 */
public final class JobOrderView {

    private JobOrderView() {}

    public static LinkedLogisticsOrderList of(CraftingJob job) {
        LinkedLogisticsOrderList root = new LinkedLogisticsOrderList();
        root.add(new DeliveryOrder(job.id, job.target.key(), job.qty, job.requester));
        LinkedLogisticsOrderList steps = new LinkedLogisticsOrderList();
        List<CraftingJob.NodeInfo> nodes = new ArrayList<>(job.nodes());
        nodes.sort(Comparator.comparingInt((CraftingJob.NodeInfo n) -> -n.height()));
        for (CraftingJob.NodeInfo node : nodes) steps.add(new StepOrder(job.id, node));
        if (!steps.isEmpty()) root.getSubOrders().add(steps);
        return root;
    }

    static ItemIdentifierStack display(ResourceKey key, long amount) {
        int n = (int) Math.max(0, Math.min(Integer.MAX_VALUE, amount));
        if (key instanceof ItemKey) return ((ItemKey) key).id.makeStack(n);
        if (key instanceof FluidKey) return ((FluidKey) key).routingItem().makeStack(n);
        return null;
    }

    private abstract static class LiveOrder implements IOrderInfoProvider {

        final long job;
        final ResourceKey key;
        final UUID location;
        private boolean watched;

        LiveOrder(long job, ResourceKey key, UUID location) {
            this.job = job;
            this.key = key;
            this.location = location;
        }

        CraftingJob liveJob() {
            CraftingDriver driver = CraftingService.INSTANCE.driver();
            return driver == null ? null : driver.job(job);
        }

        @Override
        public ResourceType getType() {
            return ResourceType.CRAFTING;
        }

        @Override
        public int getRouterId() {
            IRouter router = CraftingService.router(location);
            return router == null ? -1 : router.getSimpleID();
        }

        @Override
        public boolean isWatched() {
            return watched;
        }

        @Override
        public void setWatched() {
            watched = true;
        }

        @Override
        public List<Float> getProgresses() {
            return Collections.emptyList();
        }

        @Override
        public byte getMachineProgress() {
            return 0;
        }

        @Override
        public ItemIdentifier getTargetType() {
            ItemIdentifierStack stack = display(key, 1);
            return stack == null ? null : stack.getItem();
        }

        @Override
        public LPPosition getTargetPosition() {
            CoreRoutedPipe pipe = CraftingService.pipeAt(location);
            return pipe == null ? null : new LPPosition(pipe.getX(), pipe.getY(), pipe.getZ());
        }
    }

    /** What still has to reach the requester. */
    private static final class DeliveryOrder extends LiveOrder {

        final long qty;

        DeliveryOrder(long job, ResourceKey key, long qty, UUID requester) {
            super(job, key, requester);
            this.qty = qty;
        }

        @Override
        public boolean isFinished() {
            CraftingJob j = liveJob();
            return j == null || j.isDelivered() || j.state().isOver();
        }

        @Override
        public ItemIdentifierStack getAsDisplayItem() {
            CraftingJob j = liveJob();
            Ticket delivery = j == null ? null : j.delivery();
            return display(key, delivery == null ? 0 : qty - delivery.delivered());
        }

        @Override
        public boolean isInProgress() {
            return !isFinished();
        }
    }

    /** One crafting step: the crafts not done yet. */
    private static final class StepOrder extends LiveOrder {

        final int node;

        StepOrder(long job, CraftingJob.NodeInfo info) {
            super(job, info.output(), info.crafter());
            this.node = info.node();
        }

        @Override
        public boolean isFinished() {
            CraftingJob j = liveJob();
            if (j == null) return true;
            for (Ticket t : j.tickets()) {
                if (t.node == node && !t.state().isFinished()) return false;
            }
            return true;
        }

        @Override
        public ItemIdentifierStack getAsDisplayItem() {
            CraftingJob j = liveJob();
            long remaining = 0;
            if (j != null) {
                for (Ticket t : j.tickets()) {
                    if (t.node == node && !t.state().isFinished()) remaining += t.crafts;
                }
            }
            return display(key, remaining);
        }

        @Override
        public boolean isInProgress() {
            CraftingJob j = liveJob();
            CraftingDriver driver = CraftingService.INSTANCE.driver();
            if (j == null || driver == null) return false;
            for (Ticket t : j.tickets()) {
                if (t.node == node && (t.state() == TicketState.INSERTED || t.state() == TicketState.COMMITTED
                        || driver.displayState(t) == SetState.READY)) {
                    return true;
                }
            }
            return false;
        }
    }
}
