/*
 * Copyright (c) Krapht, 2011 "LogisticsPipes" is distributed under the terms of the Minecraft Mod Public License 1.0,
 * or MMPL. Please check the contents of the license located in http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package logisticspipes.transport;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.ForgeDirection;

import buildcraft.transport.TravelingItem;
import logisticspipes.LPConstants;
import logisticspipes.LogisticsPipes;
import logisticspipes.api.ILogisticsPowerProvider;
import logisticspipes.blocks.powertile.LogisticsPowerJunctionTileEntity;
import logisticspipes.config.Configs;
import logisticspipes.interfaces.IBufferItems;
import logisticspipes.interfaces.IFluidContainerReceiver;
import logisticspipes.interfaces.IInventoryUtil;
import logisticspipes.interfaces.IItemAdvancedExistance;
import logisticspipes.interfaces.IModuleInventory;
import logisticspipes.interfaces.ISlotUpgradeManager;
import logisticspipes.interfaces.ISpecialInsertion;
import logisticspipes.interfaces.ISubSystemPowerProvider;
import logisticspipes.interfaces.routing.ITargetSlotInformation;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.modules.ChassiModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.modules.abstractmodules.LogisticsModule.ModulePositionType;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.pipe.ItemBufferSyncPacket;
import logisticspipes.network.packets.pipe.ItemClumpPacket;
import logisticspipes.network.packets.pipe.PipeContentPacket;
import logisticspipes.network.packets.pipe.PipeContentRequest;
import logisticspipes.network.packets.pipe.PipePositionPacket;
import logisticspipes.pipes.PipeItemsFluidSupplier;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.pipes.PipeLogisticsChassi;
import logisticspipes.pipes.PipeLogisticsChassi.ChassiTargetInformation;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.basic.CoreUnroutedPipe;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.proxy.buildcraft.LPRoutedBCTravelingItem;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.ItemRoutingInformation;
import logisticspipes.routing.astar.CorridorEdge;
import logisticspipes.routing.astar.JunctionId;
import logisticspipes.routing.astar.JunctionNode;
import logisticspipes.routing.astar.JunctionRouter;
import logisticspipes.routing.astar.LPJunctionNetwork;
import logisticspipes.routing.astar.NetworkGraph;
import logisticspipes.routing.astar.RouteLabel;
import logisticspipes.routing.pathfinder.IPipeInformationProvider;
import logisticspipes.transport.LPTravelingItem.LPTravelingItemClient;
import logisticspipes.transport.LPTravelingItem.LPTravelingItemServer;
import logisticspipes.utils.CacheHolder.CacheTypes;
import logisticspipes.utils.InventoryHelper;
import logisticspipes.utils.OrientationsUtil;
import logisticspipes.utils.SidedInventoryMinecraftAdapter;
import logisticspipes.utils.SyncList;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.transactor.ITransactor;
import logisticspipes.utils.tuples.LPPosition;
import logisticspipes.utils.tuples.Pair;
import logisticspipes.utils.tuples.Triplet;

public class PipeTransportLogistics {

    private final int _bufferTimeOut = 20 * 2; // 2 Seconds
    public final SyncList<Triplet<ItemIdentifierStack, Pair<Integer /* Time */, Integer /* BufferCounter */>, LPTravelingItemServer>> _itemBuffer = new SyncList<>();
    private Chunk chunk;
    public LPItemList items = new LPItemList(this);
    public LogisticsTileGenericPipe container;
    public final boolean isRouted;
    public final int MAX_DESTINATION_UNREACHABLE_BUFFER = 30;

    /** Players this far from either end of a corridor are sent clump hops along it. */
    private static final int CLUMP_VIEW_DISTANCE = 64;
    /** Clumps restored from NBT wait at least this long, so the routers around them are up when they arrive. */
    private static final int RESTORED_CLUMP_MIN_DELAY = 20;
    private static final int MAX_DISPLAY_STACKS = 3;

    /** Clumps travelling towards this pipe, by arrival tick (server). They are saved and unloaded with this pipe. */
    private final PriorityQueue<ItemClump> incomingClumps = new PriorityQueue<>(ItemClump.BY_ARRIVAL);
    /** Clumps that just left this pipe and may still take items going the same way (server). */
    private final HashMap<ItemClump.Key, ItemClump> clumpDepartures = new HashMap<>();
    /** Incoming clumps read from NBT before the world was known; restored on the first tick. */
    private NBTTagList pendingClumpTags;

    public PipeTransportLogistics(boolean isRouted) {
        this.isRouted = isRouted;
    }

    public void initialize() {
        if (MainProxy.isServer(getWorld())) {
            // cache chunk for marking dirty
            chunk = getWorld().getChunkFromBlockCoords(container.xCoord, container.zCoord);
            ItemBufferSyncPacket packet = PacketHandler.getPacket(ItemBufferSyncPacket.class);
            packet.setTilePos(container);
            _itemBuffer.setPacketType(
                    packet,
                    MainProxy.getDimensionForWorld(getWorld()),
                    container.xCoord,
                    container.zCoord);
        }
    }

    public void markChunkModified(TileEntity tile) {
        if (tile != null && chunk != null) {
            // items are crossing a chunk boundary, mark both chunks modified
            if (container.xCoord >> 4 != tile.xCoord >> 4 || container.zCoord >> 4 != tile.zCoord >> 4) {
                chunk.isModified = true;
                if (tile instanceof LogisticsTileGenericPipe && ((LogisticsTileGenericPipe) tile).pipe != null
                        && ((LogisticsTileGenericPipe) tile).pipe.transport instanceof PipeTransportLogistics
                        && ((LogisticsTileGenericPipe) tile).pipe.transport.chunk != null) {
                    ((LogisticsTileGenericPipe) tile).pipe.transport.chunk.isModified = true;
                } else {
                    getWorld().getChunkFromChunkCoords(tile.xCoord, tile.zCoord).isModified = true;
                }
            }
        }
    }

    protected CoreUnroutedPipe getPipe() {
        return container.pipe;
    }

    protected CoreRoutedPipe getRoutedPipe() {
        if (!isRouted) {
            throw new UnsupportedOperationException("Can't use a Transport pipe as a routing pipe");
        }
        return (CoreRoutedPipe) container.pipe;
    }

    public void updateEntity() {
        moveSolids();
        if (MainProxy.isServer(getWorld())) {
            tickClumps();
            if (!_itemBuffer.isEmpty()) {
                List<LPTravelingItem> toAdd = new LinkedList<>();
                Iterator<Triplet<ItemIdentifierStack, Pair<Integer, Integer>, LPTravelingItemServer>> iterator = _itemBuffer
                        .iterator();
                while (iterator.hasNext()) {
                    Triplet<ItemIdentifierStack, Pair<Integer, Integer>, LPTravelingItemServer> next = iterator.next();
                    int currentTimeOut = next.getValue2().getValue1();
                    if (currentTimeOut > 0) {
                        next.getValue2().setValue1(currentTimeOut - 1);
                    } else if (next.getValue3() != null) {
                        if (getRoutedPipe().getRouter().hasRoute(
                                next.getValue3().getDestination(),
                                next.getValue3().getTransportMode() == TransportMode.Active,
                                next.getValue3().getItemIdentifierStack().getItem())
                                || next.getValue2().getValue2() > MAX_DESTINATION_UNREACHABLE_BUFFER) {
                            next.getValue3().setBufferCounter(next.getValue2().getValue2() + 1);
                            toAdd.add(next.getValue3());
                            iterator.remove();
                        } else {
                            next.getValue2().setValue2(next.getValue2().getValue2() + 1);
                            next.getValue2().setValue1(_bufferTimeOut);
                        }
                    } else {
                        LPTravelingItemServer item = SimpleServiceLocator.routedItemHelper
                                .createNewTravelItem(next.getValue1());
                        item.setDoNotBuffer(true);
                        item.setBufferCounter(next.getValue2().getValue2() + 1);
                        toAdd.add(item);
                        iterator.remove();
                    }
                }
                for (LPTravelingItem item : toAdd) {
                    this.injectItem(item, ForgeDirection.UP);
                }
            }
            _itemBuffer.sendUpdateToWaters();
        }
    }

    public void dropBuffer() {
        Iterator<Triplet<ItemIdentifierStack, Pair<Integer, Integer>, LPTravelingItemServer>> iterator = _itemBuffer
                .iterator();
        while (iterator.hasNext()) {
            ItemIdentifierStack next = iterator.next().getValue1();
            MainProxy.dropItems(
                    getWorld(),
                    next.makeNormalStack(),
                    getPipe().getX(),
                    getPipe().getY(),
                    getPipe().getZ());
            iterator.remove();
        }
    }

    public int injectItem(LPTravelingItemServer item, ForgeDirection inputOrientation) {
        return injectItem((LPTravelingItem) item, inputOrientation);
    }

    public int injectItem(LPTravelingItem item, ForgeDirection inputOrientation) {
        if (item.isCorrupted()) {
            // Safe guard - if for any reason the item is corrupted at this
            // stage, avoid adding it to the pipe to avoid further exceptions.
            return 0;
        }
        getPipe().triggerDebug();

        int originalCount = item.getItemIdentifierStack().getStackSize();

        item.input = inputOrientation;

        while (item.getPosition() >= 1.0F) {
            item.setPosition(item.getPosition() - 1.0F);
        }

        if (MainProxy.isServer(container.getWorldObj())) {
            readjustSpeed((LPTravelingItemServer) item);
            item.output = resolveDestination((LPTravelingItemServer) item);
            if (item.output == null) {
                return 0;
            }
            getPipe().debug.log(
                    "Injected Item: [%s, %s] (%s)",
                    item.input,
                    item.output,
                    ((LPTravelingItemServer) item).getInfo());
            if (tryDepart((LPTravelingItemServer) item)) {
                return originalCount - item.getItemIdentifierStack().getStackSize();
            }
        } else {
            ForgeDirection planned = item instanceof LPTravelingItemClient
                    ? ((LPTravelingItemClient) item).plannedExits.poll()
                    : null;
            item.output = planned != null ? planned : ForgeDirection.UNKNOWN;
        }

        items.add(item);

        if (MainProxy.isServer(container.getWorldObj()) && !getPipe().isOpaque()) {
            sendItemPacket((LPTravelingItemServer) item);
        }

        return originalCount - item.getItemIdentifierStack().getStackSize();
    }

    public int injectItem(IRoutedItem item, ForgeDirection inputOrientation) {
        return injectItem(
                (LPTravelingItem) SimpleServiceLocator.routedItemHelper.getServerTravelingItem(item),
                inputOrientation);
    }

    /**
     * emit the supplied item. This function assumes ownershop of the item, and you may assume that it is now either
     * buffered by the pipe or moving through the pipe.
     *
     * @param item the item that just bounced off an inventory. In the case of a pipe with a buffer, this function will
     *             alter item.
     */
    protected void reverseItem(LPTravelingItemServer item) {
        if (item.isCorrupted()) {
            // Safe guard - if for any reason the item is corrupted at this
            // stage, avoid adding it to the pipe to avoid further exceptions.
            return;
        }

        if (getPipe() instanceof IBufferItems) {
            item.getItemIdentifierStack().setStackSize(
                    ((IBufferItems) getPipe())
                            .addToBuffer(item.getItemIdentifierStack(), item.getAdditionalTargetInformation()));
            if (item.getItemIdentifierStack().getStackSize() <= 0) {
                return;
            }
        }

        // Assign new ID to update ItemStack content
        item.id = item.getNextId();

        if (item.getPosition() >= 1.0F) {
            item.setPosition(item.getPosition() - 1.0F);
        }

        item.input = item.output.getOpposite();

        readjustSpeed(item);
        item.output = resolveDestination(item);
        if (item.output == null) {
            return; // don't do anything
        } else if (item.output == ForgeDirection.UNKNOWN) {
            dropItem(item);
            return;
        }
        if (tryDepart(item)) {
            return; // stays scheduled for removal: it travels as part of a clump now
        }

        items.unscheduleRemoval(item);
        if (!getPipe().isOpaque()) {
            sendItemPacket(item);
        }
    }

    public ForgeDirection resolveDestination(LPTravelingItemServer data) {
        if (isRouted) {
            return resolveRoutedDestination(data);
        } else {
            return resolveUnroutedDestination(data);
        }
    }

    public ForgeDirection resolveUnroutedDestination(LPTravelingItemServer data) {
        List<ForgeDirection> dirs = new ArrayList<>(Arrays.asList(ForgeDirection.VALID_DIRECTIONS));
        dirs.remove(data.input.getOpposite());
        Iterator<ForgeDirection> iter = dirs.iterator();
        while (iter.hasNext()) {
            ForgeDirection dir = iter.next();
            LPPosition pos = getPipe().getLPPosition();
            pos.moveForward(dir);
            TileEntity tile = pos.getTileEntity(getWorld());
            if (!SimpleServiceLocator.pipeInformationManager.isItemPipe(tile)) {
                iter.remove();
                continue;
            }
            if (!this.container.isPipeConnected(dir)) {
                iter.remove();
            }
        }
        if (dirs.isEmpty()) {
            return ForgeDirection.UNKNOWN;
        }
        int num = new Random().nextInt(dirs.size());
        return dirs.get(num);
    }

    public ForgeDirection resolveRoutedDestination(LPTravelingItemServer data) {

        ForgeDirection blocked = null;

        if (data.getDestinationUUID() == null) {
            ItemIdentifierStack stack = data.getItemIdentifierStack();
            ItemRoutingInformation result = getRoutedPipe().getQueuedForItemStack(stack);
            if (result != null) {
                data.setInformation(result);
                data.getInfo().setItem(stack);
                blocked = data.input.getOpposite();
            }
        }

        if (data.getItemIdentifierStack() != null) {
            getRoutedPipe().relayedItem(data.getItemIdentifierStack().getStackSize());
        }

        if (data.getDestination() >= 0
                && !getRoutedPipe().getRouter().hasRoute(
                        data.getDestination(),
                        data.getTransportMode() == TransportMode.Active,
                        data.getItemIdentifierStack().getItem())
                && data.getBufferCounter() < MAX_DESTINATION_UNREACHABLE_BUFFER) {
            _itemBuffer.add(
                    new Triplet<>(
                            data.getItemIdentifierStack(),
                            new Pair<>(_bufferTimeOut, data.getBufferCounter()),
                            data));
            return null;
        }

        ForgeDirection value;
        if (getRoutedPipe().stillNeedReplace() || getRoutedPipe().initialInit()) {
            data.setDoNotBuffer(false);
            value = ForgeDirection.UNKNOWN;
        } else {
            value = getRoutedPipe().getRouteLayer().getOrientationForItem(data, blocked);
        }
        if (value == null && MainProxy.isClient(getWorld())) {
            return null;
        } else if (value == null) {
            LogisticsPipes.log.fatal("THIS IS NOT SUPPOSED TO HAPPEN!");
            return ForgeDirection.UNKNOWN;
        }
        if (value == ForgeDirection.UNKNOWN) {
            if (!data.getDoNotBuffer() && data.getBufferCounter() < 5) {
                // the stack is retried as a new item, so free any room its destination still holds for it
                releaseReservation(data);
                _itemBuffer.add(
                        new Triplet<>(
                                data.getItemIdentifierStack(),
                                new Pair<>(_bufferTimeOut, data.getBufferCounter()),
                                null));
                return null;
            }
            // out of retries: last effort is any default route with room, only then the item drops
            if (SimpleServiceLocator.logisticsManager
                    .assignDefaultRouteFor(data, getRoutedPipe().getRouter().getSimpleID())) {
                ForgeDirection retry = getRoutedPipe().getRouteLayer().getOrientationForItem(data, null);
                if (retry != null) {
                    value = retry;
                }
            }
        }

        if (value != ForgeDirection.UNKNOWN && !getRoutedPipe().getRouter().isRoutedExit(value)) {
            if (!isItemExitable(data.getItemIdentifierStack())) {
                return null;
            }
        }

        data.resetDelay();

        return value;
    }

    /** Stop counting {@code data} as in transit to its passive destination; its routing info is about to be lost. */
    private void releaseReservation(LPTravelingItemServer data) {
        if (data.getDestination() < 0 || data.getTransportMode() == TransportMode.Active || data.getInfo() == null) {
            return;
        }
        IRouter router = SimpleServiceLocator.routerManager.getRouterUnsafe(data.getDestination(), false);
        CoreRoutedPipe destination = router != null ? router.getCachedPipe() : null;
        if (destination != null) {
            destination.notifyOfReroute(data.getInfo());
        }
    }

    // ------------------------------------------------------------------ clump transport

    /**
     * Whether items relayed by this pipe may keep travelling as a clump without being routed here again. Transports
     * that act on every item passing through (entrances, inventory connectors, fluid pipes) route each item.
     */
    protected boolean supportsFastRelay() {
        return true;
    }

    private long now() {
        return getWorld().getTotalWorldTime();
    }

    /**
     * Send a routed item that is about to leave this pipe along a corridor as part of a clump instead of simulating it
     * pipe by pipe. Returns false (the item then travels the old way) unless the exit is the first corridor of a
     * teleportable route to a loaded junction.
     */
    private boolean tryDepart(LPTravelingItemServer item) {
        if (!Configs.ITEM_CLUMP_TRANSPORT || !isRouted
                || item.output == null
                || item.output == ForgeDirection.UNKNOWN
                || item.getDestination() < 0) {
            return false;
        }
        IRouter router = getRoutedPipe().getRouter();
        if (!(router instanceof JunctionRouter) || container.tilePart.getBCPipePluggable(item.output) != null) {
            return false;
        }
        RouteLabel route = ((JunctionRouter) router).getRouteFor(
                item.getDestination(),
                item.getTransportMode() == TransportMode.Active,
                item.getItemIdentifierStack().getItem());
        if (route == null || route.firstEdge.exitSide != item.output.ordinal()) {
            return false;
        }
        CorridorEdge[] path = route.edgeArray();
        NetworkGraph graph = LPJunctionNetwork.writer().graph();
        if (graph.hopStillValid(path[0]) == null) {
            return false;
        }
        if (ClumpTransit.isBroken(path[0], now())) {
            // the corridor lost a pipe and the router hasn't re-scanned it yet: wait instead of crossing the gap
            _itemBuffer.add(
                    new Triplet<>(
                            item.getItemIdentifierStack(),
                            new Pair<>(_bufferTimeOut, item.getBufferCounter()),
                            item));
            return true;
        }
        return sendAlong(item, path, 0, item.input, graph);
    }

    /**
     * Put {@code item} on corridor {@code path[hop]}, which leaves this pipe: into a clump that just left the same way,
     * or into a new one handed to the pipe at the other end.
     */
    private boolean sendAlong(LPTravelingItemServer item, CorridorEdge[] path, int hop, ForgeDirection input,
            NetworkGraph graph) {
        long now = now();
        item.setContainer(container); // where it drops or reports loss from until the clump arrives
        ItemClump.Key key = new ItemClump.Key(item.getDestination(), item.getTransportMode(), path, hop);
        ItemClump clump = clumpDepartures.get(key);
        if (clump != null && !clump.closed && now - clump.departTick <= Configs.ITEM_CLUMP_GATHER_TICKS) {
            clump.items.add(item);
            return true;
        }
        PipeTransportLogistics target = transportAt(path[hop], graph);
        if (target == null) {
            return false;
        }
        clump = new ItemClump(
                LPTravelingItem.nextId(),
                item.getDestination(),
                item.getTransportMode(),
                item.getSpeed());
        clump.items.add(item);
        startHop(clump, path, hop, input, target, now);
        clumpDepartures.put(key, clump);
        return true;
    }

    private void startHop(ItemClump clump, CorridorEdge[] path, int hop, ForgeDirection input,
            PipeTransportLogistics target, long now) {
        CorridorEdge edge = path[hop];
        clump.path = path;
        clump.hop = hop;
        clump.closed = false;
        clump.travelDirection = ForgeDirection.getOrientation(edge.travelPath[edge.travelPath.length - 1]);
        clump.departTick = now;
        int ticks = ItemClump.travelTicks(edge.travelPath.length, clump.speed);
        clump.arrivalTick = now + ticks;
        clump.edge = edge;
        clump.srcX = container.xCoord;
        clump.srcY = container.yCoord;
        clump.srcZ = container.zCoord;
        clump.returning = false;
        clump.startPos = 0;
        clump.ticksPerPipe = (float) ticks / edge.travelPath.length;
        target.receiveClump(clump);
        ClumpTransit.register(clump);
        if (chunk != null) {
            chunk.isModified = true;
        }
        if (!getPipe().isOpaque()) {
            sendClumpPacket(clump, container, edge.travelPath, input, target.container);
        }
    }

    /**
     * A pipe at index {@code broken} of the corridor {@code clump} is on (held by this pipe) was removed. Behind the
     * clump nothing happens, under it the items drop, ahead of it the clump turns back to the junction it left.
     */
    void corridorBroken(ItemClump clump, int broken, long now) {
        byte[] path = clump.edge.travelPath;
        float position = clump.positionAt(now);
        int current = ClumpTransit.currentPipe(clump, position);
        switch (ClumpTransit.onBreak(clump, broken, current)) {
            case DROP:
                dropClump(clump, ClumpTransit.pipeAt(clump.srcX, clump.srcY, clump.srcZ, path, current));
                break;
            case TURN_BACK:
                turnBack(clump, position, current, now);
                break;
            case FINISH:
            default:
                break;
        }
    }

    /** Send {@code clump} back along its corridor from {@code position} to the junction it left. */
    private void turnBack(ItemClump clump, float position, int current, long now) {
        byte[] path = clump.edge.travelPath;
        int[] here = ClumpTransit.pipeAt(clump.srcX, clump.srcY, clump.srcZ, path, current);
        TileEntity tile = getWorld().getTileEntity(clump.srcX, clump.srcY, clump.srcZ);
        PipeTransportLogistics source = tile instanceof LogisticsTileGenericPipe && !tile.isInvalid()
                && ((LogisticsTileGenericPipe) tile).pipe != null ? ((LogisticsTileGenericPipe) tile).pipe.transport
                        : null;
        if (source == null || !source.isRouted) {
            dropClump(clump, here);
            return;
        }
        incomingClumps.remove(clump);
        ClumpTransit.unregister(clump);
        clump.closed = true; // nothing joins a clump going back
        clump.path = null; // routed again from scratch when it gets there
        clump.returning = true;
        clump.startPos = position;
        clump.departTick = now;
        clump.arrivalTick = now + Math.max(1, (int) Math.ceil(position * clump.ticksPerPipe));
        clump.travelDirection = ForgeDirection.getOrientation(path[0]).getOpposite();
        source.receiveClump(clump);
        ClumpTransit.register(clump);

        TileEntity at = getWorld().getTileEntity(here[0], here[1], here[2]);
        if (current > 0 && at instanceof LogisticsTileGenericPipe) {
            byte[] back = new byte[current];
            for (int i = 0; i < current; i++) {
                back[i] = (byte) ForgeDirection.getOrientation(path[current - 1 - i]).getOpposite().ordinal();
            }
            sendClumpPacket(clump, at, back, ForgeDirection.getOrientation(path[current - 1]), tile);
        }
    }

    /** Drop the items of {@code clump} at block {@code pos}, as if they had been inside that pipe. */
    private void dropClump(ItemClump clump, int[] pos) {
        incomingClumps.remove(clump);
        ClumpTransit.unregister(clump);
        clump.closed = true;
        for (LPTravelingItemServer item : clump.items) {
            MainProxy.dropItems(getWorld(), item.getItemIdentifierStack().makeNormalStack(), pos[0], pos[1], pos[2]);
            item.itemWasLost();
        }
        clump.items.clear();
    }

    /** The loaded pipe transport of the junction {@code edge} leads to. */
    private static PipeTransportLogistics transportAt(CorridorEdge edge, NetworkGraph graph) {
        JunctionNode node = graph.node(edge.to);
        if (node == null || !(node.payload instanceof IRouter)) {
            return null;
        }
        CoreRoutedPipe pipe = ((IRouter) node.payload).getCachedPipe();
        if (pipe == null || pipe.container == null
                || pipe.container.isInvalid()
                || !(pipe.transport instanceof PipeTransportLogistics)) {
            return null;
        }
        return pipe.transport;
    }

    private void receiveClump(ItemClump clump) {
        clump.holder = this;
        incomingClumps.add(clump);
        if (chunk != null) {
            chunk.isModified = true;
        }
    }

    private void tickClumps() {
        if (pendingClumpTags != null) {
            restoreClumps();
        }
        if (!clumpDepartures.isEmpty()) {
            long now = now();
            clumpDepartures.values().removeIf(c -> c.closed || now - c.departTick > Configs.ITEM_CLUMP_GATHER_TICKS);
        }
        if (incomingClumps.isEmpty()) {
            return;
        }
        long now = now();
        while (!incomingClumps.isEmpty() && incomingClumps.peek().arrivalTick <= now) {
            ItemClump clump = incomingClumps.poll();
            clump.closed = true;
            ClumpTransit.unregister(clump);
            clumpArrived(clump);
        }
    }

    /**
     * A clump reached this pipe. If it only passes through and its next corridor still holds, it goes on without being
     * routed again; otherwise every item enters this pipe the ordinary way.
     */
    private void clumpArrived(ItemClump clump) {
        if (chunk != null) {
            chunk.isModified = true;
        }
        CorridorEdge next = relayEdge(clump);
        if (next == null) {
            for (LPTravelingItemServer item : clump.items) {
                item.setPosition(0);
                injectItem(item, clump.travelDirection);
            }
            return;
        }
        CoreRoutedPipe pipe = getRoutedPipe();
        int remainingBlocks = 0;
        for (int i = clump.hop + 1; i < clump.path.length; i++) {
            remainingBlocks += clump.path[i].blockDistance;
        }
        int count = 0;
        float speed = 0;
        for (LPTravelingItemServer item : clump.items) {
            readjustSpeed(item);
            speed = Math.max(speed, item.getSpeed());
            item.resetDelay();
            if (item.getDistanceTracker() != null) {
                item.getDistanceTracker().setCurrentDistanceToTarget(remainingBlocks);
            }
            count += item.getItemIdentifierStack().getStackSize();
        }
        pipe.relayedItem(count);
        clump.speed = speed;

        int hop = clump.hop + 1;
        ItemClump.Key key = new ItemClump.Key(clump.destination, clump.mode, clump.path, hop);
        ItemClump joined = clumpDepartures.get(key);
        long now = now();
        if (joined != null && !joined.closed && now - joined.departTick <= Configs.ITEM_CLUMP_GATHER_TICKS) {
            joined.items.addAll(clump.items);
            return;
        }
        PipeTransportLogistics target = transportAt(next, LPJunctionNetwork.writer().graph());
        if (target == null) {
            for (LPTravelingItemServer item : clump.items) {
                item.setPosition(0);
                injectItem(item, clump.travelDirection);
            }
            return;
        }
        startHop(clump, clump.path, hop, clump.travelDirection, target, now);
        clumpDepartures.put(key, clump);
    }

    /** The corridor {@code clump} can take from here without routing its items again, or {@code null}. */
    private CorridorEdge relayEdge(ItemClump clump) {
        CorridorEdge next = clump.nextEdge();
        if (next == null || !Configs.ITEM_CLUMP_TRANSPORT || !isRouted || !supportsFastRelay()) {
            return null;
        }
        CoreRoutedPipe pipe = getRoutedPipe();
        if (pipe.stillNeedReplace() || pipe.initialInit() || !(pipe.getRouter() instanceof JunctionRouter)) {
            return null;
        }
        if (!next.from.equals(((JunctionRouter) pipe.getRouter()).getJunctionId())) {
            return null;
        }
        ForgeDirection exit = ForgeDirection.getOrientation(next.exitSide);
        if (container.tilePart.getBCPipePluggable(exit) != null) {
            return null;
        }
        NetworkGraph graph = LPJunctionNetwork.writer().graph();
        if (clump.destination <= 0 || !graph.isActive(JunctionId.of(clump.destination))
                || !SimpleServiceLocator.routerManager.isRouterUnsafe(clump.destination, false)) {
            return null;
        }
        return graph.hopStillValid(next) == null || ClumpTransit.isBroken(next, now()) ? null : next;
    }

    /**
     * Tell the players near either end that {@code clump} leaves the pipe {@code from} along {@code path} (the exit
     * taken at each pipe, starting at {@code from}) towards {@code to}.
     */
    private void sendClumpPacket(ItemClump clump, TileEntity from, byte[] path, ForgeDirection input, TileEntity to) {
        World world = getWorld();
        int range = CLUMP_VIEW_DISTANCE * CLUMP_VIEW_DISTANCE;
        ItemClumpPacket packet = null;
        for (Object o : world.playerEntities) {
            if (!(o instanceof EntityPlayerMP)) {
                continue;
            }
            EntityPlayer player = (EntityPlayer) o;
            if (distanceSq(player, from) > range && distanceSq(player, to) > range) {
                continue;
            }
            if (packet == null) {
                int n = Math.min(MAX_DISPLAY_STACKS, clump.items.size());
                ItemIdentifierStack[] stacks = new ItemIdentifierStack[n];
                for (int i = 0; i < n; i++) {
                    stacks[i] = clump.items.get(i).getItemIdentifierStack();
                }
                packet = PacketHandler.getPacket(ItemClumpPacket.class).setTravelId(clump.id).setSpeed(clump.speed)
                        .setInput(input).setPath(path).setStacks(stacks);
                packet.setTilePos(from);
            }
            MainProxy.sendPacketToPlayer(packet, player);
        }
    }

    private static double distanceSq(EntityPlayer player, TileEntity tile) {
        double dx = player.posX - tile.xCoord;
        double dy = player.posY - tile.yCoord;
        double dz = player.posZ - tile.zCoord;
        return dx * dx + dy * dy + dz * dz;
    }

    public void handleItemClumpPacket(int travelId, ForgeDirection input, byte[] path, float speed,
            ItemIdentifierStack[] stacks) {
        if (path.length == 0 || stacks.length == 0) {
            return;
        }
        WeakReference<LPTravelingItemClient> ref = LPTravelingItem.clientList.get(travelId);
        LPTravelingItemClient item = ref != null ? ref.get() : null;
        if (item == null) {
            item = new LPTravelingItemClient(travelId, stacks[0]);
            LPTravelingItem.clientList.put(travelId, new WeakReference<>(item));
        } else {
            if (item.getContainer() instanceof LogisticsTileGenericPipe) {
                ((LogisticsTileGenericPipe) item.getContainer()).pipe.transport.items.scheduleRemoval(item);
                ((LogisticsTileGenericPipe) item.getContainer()).pipe.transport.items.removeScheduledItems();
            }
            item.setItem(stacks[0]);
        }
        item.setExtraStacks(stacks.length > 1 ? Arrays.copyOfRange(stacks, 1, stacks.length) : null);
        item.plannedExits.clear();
        for (int i = 1; i < path.length; i++) {
            item.plannedExits.add(ForgeDirection.getOrientation(path[i]));
        }
        item.updateInformation(
                input == null ? ForgeDirection.UNKNOWN : input,
                ForgeDirection.getOrientation(path[0]),
                speed,
                0);
        item.lastTicked = MainProxy.getGlobalTick();
        if (items.get(travelId) == null) {
            items.add(item);
        }
    }

    /** The chunk goes: the clumps heading here are saved with this pipe and must not take items any more. */
    public void onChunkUnload() {
        for (ItemClump clump : incomingClumps) {
            clump.closed = true;
            ClumpTransit.unregister(clump); // saved without its path; routed again when it arrives after the reload
        }
    }

    private void restoreClumps() {
        NBTTagList list = pendingClumpTags;
        pendingClumpTags = null;
        long now = now();
        for (int i = 0; i < list.tagCount(); i++) {
            ItemClump clump = ItemClump.readFromNBT(list.getCompoundTagAt(i), now);
            clump.arrivalTick = Math.max(clump.arrivalTick, now + RESTORED_CLUMP_MIN_DELAY);
            if (!clump.items.isEmpty()) {
                incomingClumps.add(clump);
            }
        }
    }

    private void writeClumps(NBTTagCompound nbt) {
        if (pendingClumpTags != null) {
            nbt.setTag("incomingClumps", pendingClumpTags);
            return;
        }
        if (incomingClumps.isEmpty()) {
            return;
        }
        long now = now();
        NBTTagList list = new NBTTagList();
        for (ItemClump clump : incomingClumps) {
            NBTTagCompound tag = new NBTTagCompound();
            clump.writeToNBT(tag, now);
            list.appendTag(tag);
        }
        nbt.setTag("incomingClumps", list);
    }

    public void readFromNBT(NBTTagCompound nbt) {
        if (nbt.hasKey("incomingClumps")) {
            pendingClumpTags = nbt.getTagList("incomingClumps", 10);
        }

        NBTTagList nbttaglist = nbt.getTagList("travelingEntities", 10);

        for (int j = 0; j < nbttaglist.tagCount(); ++j) {
            try {
                NBTTagCompound dataTag = nbttaglist.getCompoundTagAt(j);

                LPTravelingItem item = new LPTravelingItemServer(dataTag);

                if (item.isCorrupted()) {
                    continue;
                }

                items.scheduleLoad(item);
            } catch (Throwable t) {
                // It may be the case that entities cannot be reloaded between
                // two versions - ignore these errors.
            }
        }

        _itemBuffer.clear();

        NBTTagList nbttaglist2 = nbt.getTagList("buffercontents", 10);
        for (int i = 0; i < nbttaglist2.tagCount(); i++) {
            NBTTagCompound nbttagcompound1 = nbttaglist2.getCompoundTagAt(i);
            _itemBuffer.add(
                    new Triplet<>(
                            ItemIdentifierStack.getFromStack(ItemStack.loadItemStackFromNBT(nbttagcompound1)),
                            new Pair<>(_bufferTimeOut, 0),
                            null));
        }
    }

    public void writeToNBT(NBTTagCompound nbt) {

        {
            NBTTagList nbttaglist = new NBTTagList();

            for (LPTravelingItem item : items) {
                if (item instanceof LPTravelingItemServer) {
                    NBTTagCompound dataTag = new NBTTagCompound();
                    nbttaglist.appendTag(dataTag);
                    ((LPTravelingItemServer) item).writeToNBT(dataTag);
                }
            }

            nbt.setTag("travelingEntities", nbttaglist);
        }
        writeClumps(nbt);

        NBTTagList nbttaglist2 = new NBTTagList();

        for (Pair<ItemIdentifierStack, Pair<Integer, Integer>> stack : _itemBuffer) {
            NBTTagCompound nbttagcompound1 = new NBTTagCompound();
            stack.getValue1().makeNormalStack().writeToNBT(nbttagcompound1);
            nbttaglist2.appendTag(nbttagcompound1);
        }
        nbt.setTag("buffercontents", nbttaglist2);
    }

    public void readjustSpeed(LPTravelingItemServer item) {
        float defaultBoost;

        switch (item.getTransportMode()) {
            case Passive:
                defaultBoost = 25F;
                break;
            case Active:
                defaultBoost = 30F;
                break;
            case Unknown:
            case Default:
            default:
                defaultBoost = 20F;
                break;
        }

        if (isRouted) {
            float multiplyerSpeed = 1.0F + (0.02F * getRoutedPipe().getUpgradeManager().getSpeedUpgradeCount());
            float multiplyerPower = 1.0F + (0.03F * getRoutedPipe().getUpgradeManager().getSpeedUpgradeCount());

            float add = Math.max(item.getSpeed(), LPConstants.PIPE_NORMAL_SPEED * defaultBoost * multiplyerPower)
                    - item.getSpeed();
            if (getRoutedPipe().useEnergy((int) (add * 50 + 0.5))) {
                item.setSpeed(
                        Math.min(
                                Math.max(
                                        item.getSpeed(),
                                        LPConstants.PIPE_NORMAL_SPEED * defaultBoost * multiplyerSpeed),
                                1.0F));
            }
        }
    }

    /**
     * @return the inventory view of the chassis module an item is routed to, if that module has its own (see
     *         {@link logisticspipes.interfaces.IModuleInventoryOverride}), else null
     */
    private IModuleInventory getTargetModuleInventory(LPTravelingItemServer arrivingItem) {
        if (!(arrivingItem.getInfo().targetInfo instanceof ChassiTargetInformation target)
                || !(getRoutedPipe().getLogisticsModule() instanceof ChassiModule chassis)) {
            return null;
        }
        return chassis.getModuleInventory(target.getModuleSlot());
    }

    protected void handleTileReachedServer(LPTravelingItemServer arrivingItem, TileEntity tile, ForgeDirection dir) {
        if (isRouted && getPipe().container.tilePart.getBCPipePluggable(dir) != null
                && getPipe().container.tilePart.getBCPipePluggable(dir).isAcceptingItems(arrivingItem)) {
            LPTravelingItemServer remainingItem = getPipe().container.tilePart.getBCPipePluggable(dir)
                    .handleItem(arrivingItem);
            if (remainingItem != null) {
                getRoutedPipe().getRouter().update(true, getRoutedPipe());
                this.injectItem(remainingItem, dir);
            }
            return;
        }

        if (getPipe() instanceof PipeItemsFluidSupplier) {
            ((PipeItemsFluidSupplier) getPipe()).endReached(arrivingItem, tile);
            if (arrivingItem.getItemIdentifierStack().getStackSize() <= 0) {
                return;
            }
        }

        markChunkModified(tile);
        if (MainProxy.isServer(getWorld()) && arrivingItem.getInfo() != null && arrivingItem.getArrived() && isRouted) {
            getRoutedPipe().notifyOfItemArival(arrivingItem.getInfo());
        }
        // every item that leaves the pipe comes by here, also the ones on their way to another router and the fluid
        // the pipe sends out itself: only what was sent to this pipe is taken
        if (getPipe() instanceof IFluidContainerReceiver receiver && MainProxy.isServer(getWorld())
                && getRoutedPipe().getRouter().getSimpleID() == arrivingItem.getDestination()
                && receiver.endReached(arrivingItem, tile)) {
            return;
        }
        boolean isSpecialConnectionInformationTransition = false;
        if (MainProxy.isServer(getWorld())) {
            if (SimpleServiceLocator.specialtileconnection.needsInformationTransition(tile)) {
                isSpecialConnectionInformationTransition = true;
                SimpleServiceLocator.specialtileconnection.transmit(tile, arrivingItem);
            }
        }
        if (SimpleServiceLocator.pipeInformationManager.isItemPipe(tile)) {
            if (passToNextPipe(arrivingItem, tile)) {
                return;
            }
        } else if (tile instanceof IInventory && isRouted) {
            getRoutedPipe().getCacheHolder().trigger(CacheTypes.Inventory);

            // items.scheduleRemoval(arrivingItem);
            if (MainProxy.isServer(getWorld())) {
                // destroy the item on exit if it isn't exitable
                if (!isSpecialConnectionInformationTransition
                        && !isItemExitable(arrivingItem.getItemIdentifierStack())) {
                    return;
                }
                // last chance for chassi to back out
                if (arrivingItem instanceof IRoutedItem) {
                    if (((IRoutedItem) arrivingItem).getTransportMode() != TransportMode.Active
                            && !getRoutedPipe().getTransportLayer().stillWantItem(arrivingItem)) {
                        reverseItem(arrivingItem);
                        return;
                    }
                }

                if (getPipe() instanceof PipeItemsPatternCraftingLogistics) {
                    reverseItem(arrivingItem);
                    return;
                }

                ISlotUpgradeManager slotManager;
                {
                    ModulePositionType slot = null;
                    int positionInt = -1;
                    if (arrivingItem.getInfo().targetInfo instanceof ChassiTargetInformation) {
                        positionInt = ((ChassiTargetInformation) arrivingItem.getInfo().targetInfo).getModuleSlot();
                        slot = ModulePositionType.SLOT;
                    } else if (LPConstants.DEBUG && container.pipe instanceof PipeLogisticsChassi) {
                        System.out.println(arrivingItem);
                        new RuntimeException("[ItemInsertion] Information weren't ment for a chassi pipe")
                                .printStackTrace();
                    }
                    slotManager = getRoutedPipe().getUpgradeManager(slot, positionInt);
                }
                if (arrivingItem.getAdditionalTargetInformation() instanceof ITargetSlotInformation) {

                    ITargetSlotInformation information = (ITargetSlotInformation) arrivingItem
                            .getAdditionalTargetInformation();
                    IInventory inv = (IInventory) tile;
                    if (inv instanceof ISidedInventory) {
                        inv = new SidedInventoryMinecraftAdapter((ISidedInventory) inv, ForgeDirection.UNKNOWN, false);
                    }
                    IInventoryUtil util = SimpleServiceLocator.inventoryUtilFactory
                            .getInventoryUtil(inv, ForgeDirection.UNKNOWN);
                    if (util instanceof ISpecialInsertion) {
                        int slot = information.getTargetSlot();
                        int amount = information.getAmount();
                        if (util.getSizeInventory() > slot) {
                            ItemStack content = util.getStackInSlot(slot);
                            ItemStack toAdd = arrivingItem.getItemIdentifierStack().makeNormalStack();
                            toAdd.stackSize = Math.min(
                                    toAdd.stackSize,
                                    Math.max(0, amount - (content != null ? content.stackSize : 0)));
                            if (toAdd.stackSize > 0) {
                                if (util.getSizeInventory() > slot) {
                                    int added = ((ISpecialInsertion) util).addToSlot(toAdd, slot);
                                    arrivingItem.getItemIdentifierStack().lowerStackSize(added);
                                }
                            }
                        }
                        if (information.isLimited()) {
                            if (arrivingItem.getItemIdentifierStack().getStackSize() > 0) {
                                reverseItem(arrivingItem);
                            }
                            return;
                        }
                    }
                }
                // a module with its own view of the inventory (electric manager on GT battery slots) inserts there
                IModuleInventory moduleInventory = getTargetModuleInventory(arrivingItem);
                // sneaky insertion
                if (moduleInventory != null || !getRoutedPipe().getUpgradeManager().hasCombinedSneakyUpgrade()
                        || slotManager.hasOwnSneakyUpgrade()) {
                    ForgeDirection insertion = arrivingItem.output.getOpposite();
                    if (slotManager.hasSneakyUpgrade()) {
                        insertion = slotManager.getSneakyOrientation();
                    }
                    ITransactor transactor = moduleInventory != null ? moduleInventory
                            : InventoryHelper.getTransactorFor(tile, dir.getOpposite());
                    ItemStack added = transactor
                            .add(arrivingItem.getItemIdentifierStack().makeNormalStack(), insertion, true);

                    arrivingItem.getItemIdentifierStack().lowerStackSize(added.stackSize);

                    if (added.stackSize > 0 && arrivingItem instanceof IRoutedItem) {
                        ((IRoutedItem) arrivingItem).setBufferCounter(0);
                    }

                    ItemRoutingInformation info;

                    if (arrivingItem.getItemIdentifierStack().getStackSize() > 0) {
                        // we have some leftovers, we are splitting the stack, we need to clone the info
                        info = arrivingItem.getInfo().clone();
                        // For InvSysCon
                        info.getItem().setStackSize(added.stackSize);
                        insertedItemStack(info, tile);
                    } else {
                        info = arrivingItem.getInfo();
                        info.getItem().setStackSize(added.stackSize);
                        // For InvSysCon
                        insertedItemStack(info, tile);

                        // back to normal code, break if we've inserted everything, all items disposed of.
                        return; // every item has been inserted.
                    }
                } else {
                    ForgeDirection[] dirs = getRoutedPipe().getUpgradeManager().getCombinedSneakyOrientation();
                    for (ForgeDirection insertion : dirs) {
                        if (insertion == null) {
                            continue;
                        }
                        ItemStack added = InventoryHelper.getTransactorFor(tile, dir.getOpposite())
                                .add(arrivingItem.getItemIdentifierStack().makeNormalStack(), insertion, true);

                        arrivingItem.getItemIdentifierStack().lowerStackSize(added.stackSize);
                        if (added.stackSize > 0 && arrivingItem instanceof IRoutedItem) {
                            ((IRoutedItem) arrivingItem).setBufferCounter(0);
                        }
                        ItemRoutingInformation info;

                        if (arrivingItem.getItemIdentifierStack().getStackSize() > 0) {
                            // we have some leftovers, we are splitting the stack, we need to clone the info
                            info = arrivingItem.getInfo().clone();
                            // For InvSysCon
                            info.getItem().setStackSize(added.stackSize);
                            insertedItemStack(info, tile);
                        } else {
                            info = arrivingItem.getInfo();
                            info.getItem().setStackSize(added.stackSize);
                            // For InvSysCon
                            insertedItemStack(info, tile);
                            // back to normal code, break if we've inserted everything, all items disposed of.
                            return; // every item has been inserted.
                        }
                    }
                }

                if (arrivingItem.getItemIdentifierStack().getStackSize() > 0) {
                    LogisticsModule module = getRoutedPipe().getLogisticsModule();
                    if (module != null) {
                        module.insertionFailed(arrivingItem.getItemIdentifierStack().getItem());
                    }
                    reverseItem(arrivingItem);
                }
            }
            return; // the item is handled
        } // end of insert into IInventory
        dropItem(arrivingItem);
    }

    protected void handleTileReachedClient(LPTravelingItemClient arrivingItem, TileEntity tile) {
        if (SimpleServiceLocator.pipeInformationManager.isItemPipe(tile)) {
            passToNextPipe(arrivingItem, tile);
        }
        // Just ignore any other case
    }

    protected boolean isItemExitable(ItemIdentifierStack itemIdentifierStack) {
        if (itemIdentifierStack != null
                && itemIdentifierStack.makeNormalStack().getItem() instanceof IItemAdvancedExistance) {
            return ((IItemAdvancedExistance) itemIdentifierStack.makeNormalStack().getItem())
                    .canExistInNormalInventory(itemIdentifierStack.makeNormalStack());
        }
        return true;
    }

    protected void insertedItemStack(ItemRoutingInformation info, TileEntity tile) {}

    public boolean canPipeConnect(TileEntity tile, ForgeDirection side) {
        if (isRouted) {
            if (tile instanceof ILogisticsPowerProvider || tile instanceof ISubSystemPowerProvider) {
                ForgeDirection ori = OrientationsUtil.getOrientationOfTilewithTile(container, tile);
                if (ori != null && ori != ForgeDirection.UNKNOWN) {
                    return (!(tile instanceof LogisticsPowerJunctionTileEntity)
                            && !(tile instanceof ISubSystemPowerProvider)) || OrientationsUtil.isSide(ori);
                }
            }
            if (SimpleServiceLocator.betterStorageProxy.isBetterStorageCrate(tile)
                    || SimpleServiceLocator.factorizationProxy.isBarral(tile)
                    // || (Configs.TE_PIPE_SUPPORT && SimpleServiceLocator.thermalExpansionProxy.isItemConduit(tile) &&
                    // SimpleServiceLocator.thermalExpansionProxy.isSideFree(tile, side.getOpposite().ordinal()))
                    || (getPipe().getUpgradeManager().hasRFPowerSupplierUpgrade()
                            && SimpleServiceLocator.cofhPowerProxy.isEnergyReceiver(tile))
                    || (getPipe().getUpgradeManager().getIC2PowerLevel() > 0
                            && SimpleServiceLocator.IC2Proxy.isEnergySink(tile))) {
                return true;
            }
            if (tile instanceof ISidedInventory) {
                int[] slots = ((ISidedInventory) tile).getAccessibleSlotsFromSide(side.getOpposite().ordinal());
                return slots != null && slots.length > 0;
            }
            return SimpleServiceLocator.pipeInformationManager.isItemPipe(tile)
                    || (getPipe().isFluidPipe() && SimpleServiceLocator.pipeInformationManager.isFluidPipe(tile))
                    || (tile instanceof IInventory && ((IInventory) tile).getSizeInventory() > 0);
        } else {
            return SimpleServiceLocator.pipeInformationManager.isItemPipe(tile);
        }
    }

    private void moveSolids() {
        items.flush();
        items.scheduleAdd();
        for (LPTravelingItem item : items) {
            if (item.lastTicked >= MainProxy.getGlobalTick()) {
                continue;
            }
            item.lastTicked = MainProxy.getGlobalTick();
            item.addAge();
            item.setPosition(item.getPosition() + item.getSpeed());
            if (endReached(item)) {
                if (item.output == ForgeDirection.UNKNOWN) {
                    if (MainProxy.isServer(container.getWorldObj())) {
                        dropItem((LPTravelingItemServer) item);
                    }
                    items.scheduleRemoval(item);
                    continue;
                }
                TileEntity tile = container.getTile(item.output);
                if (items.scheduleRemoval(item)) {
                    if (MainProxy.isServer(container.getWorldObj())) {
                        handleTileReachedServer((LPTravelingItemServer) item, tile, item.output);
                    } else {
                        handleTileReachedClient((LPTravelingItemClient) item, tile);
                    }
                }
            }
        }
        items.removeScheduledItems();
        items.addScheduledItems();
    }

    private boolean passToNextPipe(LPTravelingItem item, TileEntity tile) {
        IPipeInformationProvider information = SimpleServiceLocator.pipeInformationManager
                .getInformationProviderFor(tile);
        if (information != null) {
            return information.acceptItem(item, container);
        }
        return false;
    }

    /**
     * Accept items from BC
     */
    @cpw.mods.fml.common.Optional.Method(modid = "BuildCraft|Transport")
    public void injectItem(TravelingItem item, ForgeDirection inputOrientation) {
        if (MainProxy.isServer(getWorld())) {
            if (item instanceof LPRoutedBCTravelingItem) {
                ItemRoutingInformation info = ((LPRoutedBCTravelingItem) item).getRoutingInformation();
                info.setItem(ItemIdentifierStack.getFromStack(item.getItemStack()));
                LPTravelingItemServer lpItem = new LPTravelingItemServer(info);
                lpItem.setSpeed(item.getSpeed());
                this.injectItem(lpItem, inputOrientation);
            } else {
                ItemRoutingInformation info = LPRoutedBCTravelingItem.restoreFromExtraNBTData(item);
                if (info != null) {
                    info.setItem(ItemIdentifierStack.getFromStack(item.getItemStack()));
                    LPTravelingItemServer lpItem = new LPTravelingItemServer(info);
                    lpItem.setSpeed(item.getSpeed());
                    this.injectItem(lpItem, inputOrientation);
                } else {
                    LPTravelingItemServer lpItem = SimpleServiceLocator.routedItemHelper
                            .createNewTravelItem(item.getItemStack());
                    lpItem.setSpeed(item.getSpeed());
                    this.injectItem(lpItem, inputOrientation);
                }
            }
        }
    }

    private void dropItem(LPTravelingItemServer item) {
        if (container.getWorldObj().isRemote) {
            return;
        }
        item.setSpeed(0.05F);
        item.setContainer(container);
        EntityItem entity = item.toEntityItem();
        if (entity != null) {
            container.getWorldObj().spawnEntityInWorld(entity);
        }
    }

    protected boolean endReached(LPTravelingItem item) {
        return item.getPosition() >= ((item.output == ForgeDirection.UNKNOWN) ? 0.75F : 1.0F);
    }

    protected void neighborChange() {}

    public List<ItemStack> dropContents() {
        List<ItemStack> list = new ArrayList<>();
        if (MainProxy.isServer(getWorld())) {
            for (LPTravelingItem item : items) {
                list.add(item.getItemIdentifierStack().makeNormalStack());
            }
            // clumps on their way here drop as if they were inside this pipe
            if (pendingClumpTags != null) {
                restoreClumps();
            }
            for (ItemClump clump : incomingClumps) {
                clump.closed = true;
                ClumpTransit.unregister(clump);
                for (LPTravelingItemServer item : clump.items) {
                    list.add(item.getItemIdentifierStack().makeNormalStack());
                    item.itemWasLost();
                }
            }
            incomingClumps.clear();
        }
        return list;
    }

    public boolean delveIntoUnloadedChunks() {
        return true;
    }

    private void sendItemPacket(LPTravelingItemServer item) {
        if (MainProxy
                .isAnyoneWatching(container.xCoord, container.zCoord, MainProxy.getDimensionForWorld(getWorld()))) {
            if (!LPTravelingItem.clientSideKnownIDs.get(item.getId())) {
                MainProxy.sendPacketToAllWatchingChunk(
                        container.xCoord,
                        container.zCoord,
                        MainProxy.getDimensionForWorld(getWorld()),
                        (PacketHandler.getPacket(PipeContentPacket.class).setItem(item.getItemIdentifierStack())
                                .setTravelId(item.getId())));
                LPTravelingItem.clientSideKnownIDs.set(item.getId());
            }
            MainProxy.sendPacketToAllWatchingChunk(
                    container.xCoord,
                    container.zCoord,
                    MainProxy.getDimensionForWorld(getWorld()),
                    (PacketHandler.getPacket(PipePositionPacket.class).setSpeed(item.getSpeed())
                            .setPosition(item.getPosition()).setInput(item.input).setOutput(item.output)
                            .setTravelId(item.getId()).setTilePos(container)));
        }
    }

    public void handleItemPositionPacket(int travelId, ForgeDirection input, ForgeDirection output, float speed,
            float position) {
        WeakReference<LPTravelingItemClient> ref = LPTravelingItem.clientList.get(travelId);
        LPTravelingItemClient item = null;
        if (ref != null) {
            item = ref.get();
        }
        if (item == null) {
            sendItemContentRequest(travelId);
            item = new LPTravelingItemClient(travelId, position, input, output);
            item.setSpeed(speed);
            LPTravelingItem.clientList.put(travelId, new WeakReference<>(item));
        } else {
            if (item.getContainer() instanceof LogisticsTileGenericPipe) {
                ((LogisticsTileGenericPipe) item.getContainer()).pipe.transport.items.scheduleRemoval(item);
                ((LogisticsTileGenericPipe) item.getContainer()).pipe.transport.items.removeScheduledItems();
            }
            item.updateInformation(input, output, speed, position);
        }
        // update lastTicked so we don't double-move items
        item.lastTicked = MainProxy.getGlobalTick();
        if (items.get(travelId) == null) {
            items.add(item);
        }
        // getPipe().spawnParticle(Particles.OrangeParticle, 1);
    }

    private void sendItemContentRequest(int travelId) {
        MainProxy.sendPacketToServer(PacketHandler.getPacket(PipeContentRequest.class).setInteger(travelId));
    }

    public void sendItem(ItemStack stackToSend) {
        this.injectItem(
                (LPTravelingItem) SimpleServiceLocator.routedItemHelper.createNewTravelItem(stackToSend),
                ForgeDirection.UP);
    }

    public World getWorld() {
        return container.getWorldObj();
    }

    public void onNeighborBlockChange(int blockId) {}

    public void onBlockPlaced() {}

    public void setTile(LogisticsTileGenericPipe tile) {
        container = tile;
    }
}
