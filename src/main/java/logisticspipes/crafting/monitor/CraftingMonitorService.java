package logisticspipes.crafting.monitor;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import logisticspipes.LogisticsPipes;
import logisticspipes.crafting.monitor.CraftingMonitorData.Node;
import logisticspipes.crafting.monitor.CraftingMonitorData.State;
import logisticspipes.crafting.monitor.CraftingMonitorData.Summary;
import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.PacketGuards;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.crafting.requesttable.RequestTableMonitorUpdatePacket;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.resources.FluidResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.PipeRoutingConnectionType;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.routing.order.LinkedLogisticsOrderList;
import logisticspipes.routing.order.LogisticsFluidOrder;
import logisticspipes.utils.item.ItemIdentifierStack;

/** Server-thread observer. Never changes orders, routing, or the existing watched flag. */
public final class CraftingMonitorService {

    public static final CraftingMonitorService INSTANCE = new CraftingMonitorService();
    private final Map<Long, Job> jobs = new LinkedHashMap<>();
    private final Map<EntityPlayer, Subscription> subscriptions = new HashMap<>();
    private long tick;
    private long nextJob;
    private long nextTransfer;

    private CraftingMonitorService() {
    }

    /** Called once for a committed root request; observation must never fail the request. */
    public void observe(IResource root, LinkedLogisticsOrderList orders) {
        try {
            IRouter router = root.getRouter();
            if (router == null || router.getCachedPipe() == null
                || MainProxy.isClient(router.getCachedPipe().getWorld()))
                return;
            Job job = new Job(++nextJob, tick, router.getId(), root, orders);
            if (job.crafting) jobs.put(job.id, job);
        } catch (RuntimeException exception) {
            LogisticsPipes.log.warn("Unable to observe crafting request", exception);
        }
    }

    public void request(EntityPlayer player, RequestTablePipe table, int window, long session, int action, long job) {
        if (action == 2) {
            Subscription old = subscriptions.get(player);
            if (old != null && old.session == session) subscriptions.remove(player);
            return;
        }
        if (player.openContainer.windowId != window || !table.hasMonitoringUpgrade()
            || !PacketGuards.canConfigurePipe(player, table))
            return;
        Subscription subscription = subscriptions.get(player);
        if (action == 0) {
            subscription = new Subscription(table, window, session);
            subscriptions.put(player, subscription);
            sendOverview(player, subscription);
        } else if (action != 1 || subscription == null || subscription.session != session || subscription.table != table) {
            return;
        }
        if (job >= 0 && visibleJobs(table).contains(job)) {
            subscription.selected = job;
            subscription.states.clear();
            subscription.revision = -1;
            sendDetails(player, subscription);
        }
    }

    @SubscribeEvent
    public void logout(PlayerLoggedOutEvent event) {
        subscriptions.remove(event.player);
    }

    @SubscribeEvent
    public void update(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tick++;
        if (tick % 5 != 0) return;
        try {
            Iterator<Job> iterator = jobs.values().iterator();
            while (iterator.hasNext()) {
                Job job = iterator.next();
                // A removed/replaced requester must not retain dead orders indefinitely.
                if (SimpleServiceLocator.routerManager.getRouterUnsafe(job.originRouter.getSimpleID(), false)
                    != job.originRouter) {
                    iterator.remove();
                    continue;
                }
                job.sample(tick);
                if (job.completed >= 0 && tick - job.completed >= 200) iterator.remove();
            }
            Iterator<Map.Entry<EntityPlayer, Subscription>> viewers = subscriptions.entrySet().iterator();
            while (viewers.hasNext()) {
                var entry = viewers.next();
                EntityPlayer player = entry.getKey();
                Subscription subscription = entry.getValue();
                if (player.isDead || PacketGuards.getOpenRequestTable(player) != subscription.table
                    || player.openContainer.windowId != subscription.window
                    || !subscription.table.hasMonitoringUpgrade()
                    || !PacketGuards.canConfigurePipe(player, subscription.table)) {
                    viewers.remove();
                    continue;
                }
                if (tick % 20 == 0) sendOverview(player, subscription);
                sendDetails(player, subscription);
            }
        } catch (RuntimeException exception) {
            LogisticsPipes.log.warn("Unable to update crafting monitor", exception);
        }
    }

    public void clear() {
        subscriptions.clear();
        jobs.clear();
        tick = 0;
    }

    private Set<Long> visibleJobs(RequestTablePipe table) {
        Set<UUID> routers = new HashSet<>();
        routers.add(table.getRouter().getId());
        for (var route : table.getRouter().getIRoutersByCost()) {
            if (route.containsFlag(PipeRoutingConnectionType.canRequestFrom)) routers.add(route.destination.getId());
        }
        Set<Long> visible = new HashSet<>();
        for (Job job : jobs.values()) {
            if (routers.contains(job.origin)) visible.add(job.id);
        }
        return visible;
    }

    private void sendOverview(EntityPlayer player, Subscription subscription) {
        Set<Long> visible = visibleJobs(subscription.table);
        List<Job> ordered = new ArrayList<>();
        for (Job job : jobs.values()) if (visible.contains(job.id)) ordered.add(job);
        Collections.reverse(ordered);
        try (LPDataOutputStream output = new LPDataOutputStream()) {
            output.writeByte(CraftingMonitorData.OVERVIEW);
            output.writeInt(ordered.size());
            for (Job job : ordered) job.summary(tick, subscription.table.isFluidEnabled()).write(output);
            send(player, subscription, output.toByteArray());
        } catch (IOException exception) {
            LogisticsPipes.log.warn("Unable to send crafting overview", exception);
        }
    }

    private void sendDetails(EntityPlayer player, Subscription subscription) {
        Job job = jobs.get(subscription.selected);
        if (job == null) return;
        if (!visibleJobs(subscription.table).contains(job.id)) {
            subscription.selected = -1;
            sendOverview(player, subscription);
            return;
        }
        boolean fluids = subscription.table.isFluidEnabled();
        boolean structure = subscription.revision < 0 || subscription.fluids != fluids;
        if (!structure && subscription.revision == job.revision) return;
        Map<Integer, State> states = new LinkedHashMap<>();
        for (Node node : job.nodes) {
            State state = node.fluid && !fluids ? State.EMPTY : job.states.get(node.id);
            if (structure || !state.equals(subscription.states.get(node.id))) states.put(node.id, state);
        }
        try (LPDataOutputStream output = new LPDataOutputStream()) {
            output.writeByte(structure ? CraftingMonitorData.STRUCTURE : CraftingMonitorData.CHANGES);
            output.writeLong(job.id);
            output.writeLong(subscription.revision);
            output.writeLong(job.revision);
            if (structure) {
                output.writeInt(job.nodes.size());
                for (Node node : job.nodes) node.visible(fluids).write(output);
            }
            output.writeInt(states.size());
            for (var entry : states.entrySet()) {
                output.writeInt(entry.getKey());
                entry.getValue().write(output);
            }
            if (!send(player, subscription, output.toByteArray())) return;
            subscription.states.putAll(states);
            subscription.revision = job.revision;
            subscription.fluids = fluids;
        } catch (IOException exception) {
            LogisticsPipes.log.warn("Unable to send crafting details", exception);
        }
    }

    private boolean send(EntityPlayer player, Subscription subscription, byte[] payload) {
        if (payload.length > CraftingMonitorData.MAX_BYTES) return false;
        long transfer = ++nextTransfer;
        int parts = Math
            .max(1, (payload.length + CraftingMonitorData.CHUNK_BYTES - 1) / CraftingMonitorData.CHUNK_BYTES);
        for (int part = 0; part < parts; part++) {
            byte[] bytes = Arrays.copyOfRange(
                payload,
                part * CraftingMonitorData.CHUNK_BYTES,
                Math.min(payload.length, (part + 1) * CraftingMonitorData.CHUNK_BYTES));
            MainProxy.sendPacketToPlayer(
                PacketHandler.getPacket(RequestTableMonitorUpdatePacket.class)
                    .setChunk(subscription.session, transfer, part, parts, bytes)
                    .setTilePos(subscription.table.container),
                player);
        }
        return true;
    }

    private static final class Subscription {

        final RequestTablePipe table;
        final int window;
        final long session;
        final Map<Integer, State> states = new HashMap<>();
        long selected = -1;
        long revision = -1;
        boolean fluids;

        Subscription(RequestTablePipe table, int window, long session) {
            this.table = table;
            this.window = window;
            this.session = session;
        }
    }

    private static final class Group {

        final LinkedLogisticsOrderList orders;
        final int parent;

        Group(LinkedLogisticsOrderList orders, int parent) {
            this.orders = orders;
            this.parent = parent;
        }
    }

    private static final class Job {

        final long id;
        final long started;
        final UUID origin;
        final IRouter originRouter;
        final List<Node> nodes = new ArrayList<>();
        final Map<Integer, IOrderInfoProvider> orders = new LinkedHashMap<>();
        final Map<Integer, State> states = new LinkedHashMap<>();
        final ItemIdentifierStack result;
        final boolean fluid;
        final boolean batch;
        boolean crafting;
        long completed = -1;
        long revision;
        boolean unfulfilled;

        Job(long id, long started, UUID origin, IResource root, LinkedLogisticsOrderList list) {
            this.id = id;
            this.started = started;
            this.origin = origin;
            originRouter = root.getRouter();
            batch = root.getRequestedAmount() == 0;
            ItemIdentifierStack display = root.getDisplayItem();
            ArrayDeque<Group> pending = new ArrayDeque<>();
            pending.add(new Group(list, -1));
            while (!pending.isEmpty()) {
                Group group = pending.removeFirst();
                int groupId = nodes.size();
                nodes.add(new Node(groupId, group.parent, true, false, false, null, 0, "", ""));
                states.put(groupId, State.EMPTY);
                for (IOrderInfoProvider order : group.orders) {
                    int nodeId = nodes.size();
                    ItemIdentifierStack resource = order.getAsDisplayItem().clone();
                    if (batch && (display == null || display.getStackSize() == 0)) display = resource;
                    boolean liquid = order instanceof LogisticsFluidOrder || resource.getItem().isFluidContainer();
                    var position = order.getTargetPosition();
                    String destination = position == null ? "" : position.toString();
                    nodes.add(
                        new Node(
                            nodeId,
                            groupId,
                            false,
                            liquid,
                            false,
                            resource,
                            Math.max(0, resource.getStackSize()),
                            order.getType().name(),
                            destination));
                    orders.put(nodeId, order);
                    crafting |= order.getType() == IOrderInfoProvider.ResourceType.CRAFTING;
                }
                for (LinkedLogisticsOrderList child : group.orders.getSubOrders())
                    pending.add(new Group(child, groupId));
            }
            result = display == null ? null : display.clone();
            fluid = root instanceof FluidResource || (batch && result != null && result.getItem().isFluidContainer());
            sample(started);
        }

        void sample(long now) {
            if (completed >= 0) return;
            boolean done = true;
            boolean changed = false;
            unfulfilled = false;
            for (var entry : orders.entrySet()) {
                IOrderInfoProvider order = entry.getValue();
                List<Float> progress = order.getProgresses();
                float[] transport = new float[progress.size()];
                for (int i = 0; i < transport.length; i++) transport[i] = progress.get(i);
                State state = new State(
                    Math.max(0, order.getAsDisplayItem().getStackSize()),
                    order.isInProgress(),
                    order.isFinished(),
                    Math.max(0, Math.min(100, order.getMachineProgress())),
                    transport);
                changed |= !state.equals(states.put(entry.getKey(), state));
                done &= state.finished && transport.length == 0;
                unfulfilled |= state.finished && state.remaining > 0;
            }
            if (changed) revision++;
            if (done) {
                completed = now;
                orders.clear();
            }
        }

        Summary summary(long now, boolean fluids) {
            boolean locked = fluid && !fluids;
            return new Summary(
                id,
                locked ? null : result,
                fluid,
                locked,
                batch,
                (completed < 0 ? now : completed) - started,
                completed >= 0,
                unfulfilled);
        }
    }
}
