package logisticspipes.routing.astar;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.api.ILogisticsPowerProvider;
import logisticspipes.asm.te.ILPTEInformation;
import logisticspipes.asm.te.ITileEntityChangeListener;
import logisticspipes.asm.te.LPTileEntityObject;
import logisticspipes.interfaces.ISubSystemPowerProvider;
import logisticspipes.interfaces.routing.IDirectRoutingConnection;
import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.proxy.specialconnection.SpecialPipeConnection.ConnectionInformation;
import logisticspipes.routing.ExitRoute;
import logisticspipes.routing.PipeRoutingConnectionType;
import logisticspipes.routing.pathfinder.IPipeInformationProvider;
import logisticspipes.routing.pathfinder.IRouteProvider;
import logisticspipes.routing.pathfinder.IRouteProvider.RouteInfo;
import logisticspipes.utils.OneList;
import logisticspipes.utils.tuples.LPPosition;
import logisticspipes.utils.tuples.Pair;

/**
 * Corridor discovery for one junction: walks the plain pipes leaving a routed pipe until it hits other routed pipes,
 * compressing each run into one corridor (distance, flags, filters, sides).
 * <p>
 * This is the corridor-compression half of {@code PathFinder}, kept behaviour-compatible (special connections,
 * {@link IRouteProvider}s, direct connections, one-way/power-only/network-dividing pipes, firewall filters) and
 * extended to record the chunks each corridor passes through, which feeds the {@link ChunkEdgeIndex}.
 */
public final class CorridorScanner {

    private final int maxVisited;
    private final int maxLength;
    private final HashSet<LPPosition> setVisited = new HashSet<>();
    private final HashMap<LPPosition, Double> distances = new HashMap<>();
    /** Chunk keys of the pipes on the current DFS path, parallel to the path itself. */
    private final ArrayList<Long> pathChunks = new ArrayList<>();
    private final IdentityHashMap<ExitRoute, long[]> chunksByRoute = new IdentityHashMap<>();
    private final IdentityHashMap<ExitRoute, Double> metricByRoute = new IdentityHashMap<>();
    /** Directions taken at each pipe of the current DFS path, parallel to the path itself. */
    private final ArrayList<Byte> pathDirections = new ArrayList<>();
    /** How many hops of the current DFS path are not plain LP pipe to LP pipe (special connections, foreign pipes). */
    private int opaqueHops;
    private final IdentityHashMap<ExitRoute, byte[]> travelPathByRoute = new IdentityHashMap<>();
    private final Set<TileEntity> specialTileHops = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private int pipesVisited;

    public List<Pair<ILogisticsPowerProvider, List<IFilter>>> powerNodes;
    public List<Pair<ISubSystemPowerProvider, List<IFilter>>> subPowerProvider;
    public HashMap<CoreRoutedPipe, ExitRoute> result;

    private final ITileEntityChangeListener changeListener;
    public Set<List<ITileEntityChangeListener>> listenedPipes = new HashSet<>();
    public Set<LPTileEntityObject> touchedPipes = new HashSet<>();

    public CorridorScanner(IPipeInformationProvider startPipe, int maxVisited, int maxLength,
            ITileEntityChangeListener changeListener) {
        this.maxVisited = maxVisited;
        this.maxLength = maxLength;
        this.changeListener = changeListener;
        if (startPipe == null) {
            result = new HashMap<>();
            return;
        }
        result = getConnectedRoutingPipes(
                startPipe,
                EnumSet.allOf(PipeRoutingConnectionType.class),
                ForgeDirection.UNKNOWN);
    }

    /** Chunks the corridor behind {@code route} (one of {@link #result}'s values) passes through. */
    /**
     * Real length of the corridor behind {@code route}. {@link ExitRoute#distanceToDestination} is only set for
     * corridors items can be routed along; this is set for every corridor.
     */
    public double metricOf(ExitRoute route) {
        Double metric = metricByRoute.get(route);
        return metric == null ? route.distanceToDestination : metric;
    }

    private void addMetric(ExitRoute route, int resistance) {
        Double metric = metricByRoute.get(route);
        if (metric != null) {
            metricByRoute.put(route, metric + resistance);
        }
    }

    /**
     * Direction taken at each pipe of the corridor behind {@code route}, or {@code null} if items can't skip it as a
     * scheduled hop (see {@link EdgeSpec#travelPath}).
     */
    public byte[] travelPathOf(ExitRoute route) {
        return travelPathByRoute.get(route);
    }

    private byte[] currentTravelPath() {
        if (opaqueHops > 0 || pathDirections.isEmpty()) {
            return null;
        }
        byte[] path = new byte[pathDirections.size()];
        for (int i = 0; i < path.length; i++) {
            path[i] = pathDirections.get(i);
        }
        return path;
    }

    public long[] chunksOf(ExitRoute route) {
        long[] chunks = chunksByRoute.get(route);
        return chunks == null ? new long[0] : chunks;
    }

    private static long chunkOf(IPipeInformationProvider pipe) {
        World world = pipe.getWorld();
        int dim = world == null || world.provider == null ? 0 : world.provider.dimensionId;
        return ChunkEdgeIndex.chunkKeyForBlock(dim, pipe.getX(), pipe.getZ());
    }

    private long[] currentPathChunks(IPipeInformationProvider end) {
        LinkedHashSet<Long> set = new LinkedHashSet<>(pathChunks);
        set.add(chunkOf(end));
        long[] chunks = new long[set.size()];
        int i = 0;
        for (long c : set) {
            chunks[i++] = c;
        }
        return chunks;
    }

    private HashMap<CoreRoutedPipe, ExitRoute> getConnectedRoutingPipes(IPipeInformationProvider startPipe,
            EnumSet<PipeRoutingConnectionType> connectionFlags, ForgeDirection side) {
        HashMap<CoreRoutedPipe, ExitRoute> foundPipes = new HashMap<>();

        boolean root = setVisited.size() == 0;

        // Reset visited count at top level
        if (setVisited.size() == 1) {
            pipesVisited = 0;
        }

        // Break recursion if we have visited a set number of pipes, to prevent hangs on weird configurations
        if (++pipesVisited > maxVisited) {
            return foundPipes;
        }

        // Break recursion after certain amount of nodes visited
        if (setVisited.size() > maxLength) {
            return foundPipes;
        }

        if (!startPipe.isRouterInitialized()) {
            return foundPipes;
        }

        // Stop at a routing pipe, unless it's the one we started from: it is the other end of the corridor.
        if (startPipe.isRoutingPipe() && setVisited.size() != 0) {
            CoreRoutedPipe rp = startPipe.getRoutingPipe();
            if (rp.stillNeedReplace()) {
                return foundPipes;
            }
            double size = 0;
            for (Double dis : distances.values()) {
                size += dis;
            }

            if (!rp.getUpgradeManager().hasPowerPassUpgrade()) {
                connectionFlags.remove(PipeRoutingConnectionType.canPowerSubSystemFrom);
            }

            ExitRoute route = new ExitRoute(
                    null,
                    rp.getRouter(),
                    ForgeDirection.UNKNOWN,
                    side.getOpposite(),
                    Math.max(1, size),
                    connectionFlags,
                    distances.size());
            chunksByRoute.put(route, currentPathChunks(startPipe));
            metricByRoute.put(route, Math.max(1, size));
            byte[] travelPath = currentTravelPath();
            if (travelPath != null) {
                travelPathByRoute.put(route, travelPath);
            }
            foundPipes.put(rp, route);

            return foundPipes;
        }

        // Visited is checked after, so we can reach the same target twice to allow to keep the shortest path
        setVisited.add(new LPPosition(startPipe));
        distances.put(new LPPosition(startPipe), startPipe.getDistance());
        pathChunks.add(chunkOf(startPipe));

        // first check specialPipeConnections (tesseracts, teleports, other connectors)
        List<ConnectionInformation> pipez = SimpleServiceLocator.specialpipeconnection
                .getConnectedPipes(startPipe, connectionFlags, side);
        for (ConnectionInformation specialConnection : pipez) {
            if (setVisited.contains(new LPPosition(specialConnection.getConnectedPipe()))) {
                // Don't go where we have been before
                continue;
            }
            distances.put(new LPPosition(startPipe).center(), specialConnection.getDistance());
            opaqueHops++;
            HashMap<CoreRoutedPipe, ExitRoute> result = getConnectedRoutingPipes(
                    specialConnection.getConnectedPipe(),
                    specialConnection.getConnectionFlags(),
                    specialConnection.getInsertOrientation());
            opaqueHops--;
            distances.remove(new LPPosition(startPipe).center());
            for (Entry<CoreRoutedPipe, ExitRoute> pipe : result.entrySet()) {
                pipe.getValue().exitOrientation = specialConnection.getExitOrientation();
                ExitRoute foundPipe = foundPipes.get(pipe.getKey());
                if (foundPipe == null || (pipe.getValue().distanceToDestination < foundPipe.distanceToDestination)) {
                    // New path OR If new path is better, replace old path
                    foundPipes.put(pipe.getKey(), pipe.getValue());
                }
            }
        }

        ArrayDeque<Pair<TileEntity, ForgeDirection>> connections = new ArrayDeque<>();

        // Recurse in all directions
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            if (root && !ForgeDirection.UNKNOWN.equals(side) && !direction.equals(side)) {
                continue;
            }

            // tile may be up to 1 second old, but any neighbour pipe change will cause an immediate update here
            TileEntity tile = startPipe.getTile(direction);

            if (tile == null) {
                continue;
            }
            if (logisticspipes.utils.OrientationsUtil.isSide(direction)) {
                if (root && tile instanceof ILogisticsPowerProvider) {
                    if (powerNodes == null) {
                        powerNodes = new ArrayList<>();
                    }
                    // If we are a FireWall pipe add our filter to the pipes
                    if (startPipe.isFirewallPipe()) {
                        powerNodes.add(
                                new Pair<>(
                                        (ILogisticsPowerProvider) tile,
                                        new OneList<>(startPipe.getFirewallFilter())));
                    } else {
                        powerNodes.add(
                                new Pair<>(
                                        (ILogisticsPowerProvider) tile,
                                        Collections.unmodifiableList(new ArrayList<>(0))));
                    }
                }
                if (root && tile instanceof ISubSystemPowerProvider) {
                    if (subPowerProvider == null) {
                        subPowerProvider = new ArrayList<>();
                    }
                    // If we are a FireWall pipe add our filter to the pipes
                    if (startPipe.isFirewallPipe()) {
                        subPowerProvider.add(
                                new Pair<>(
                                        (ISubSystemPowerProvider) tile,
                                        new OneList<>(startPipe.getFirewallFilter())));
                    } else {
                        subPowerProvider.add(
                                new Pair<>(
                                        (ISubSystemPowerProvider) tile,
                                        Collections.unmodifiableList(new ArrayList<>(0))));
                    }
                }
            }
            connections.add(new Pair<>(tile, direction));
        }

        while (!connections.isEmpty()) {
            Pair<TileEntity, ForgeDirection> pair = connections.pollFirst();
            TileEntity tile = pair.getValue1();
            ForgeDirection direction = pair.getValue2();
            EnumSet<PipeRoutingConnectionType> nextConnectionFlags = EnumSet.copyOf(connectionFlags);
            boolean isDirectConnection = false;
            int resistance = 0;

            if (root) {
                Collection<TileEntity> list = SimpleServiceLocator.specialtileconnection.getConnectedPipes(tile);
                if (!list.isEmpty()) {
                    for (TileEntity pipe : list) {
                        connections.add(new Pair<>(pipe, direction));
                        specialTileHops.add(pipe);
                    }
                    listTileEntity(tile);
                    continue;
                }
                if (!startPipe.getRoutingPipe().getUpgradeManager().hasPowerPassUpgrade()) {
                    nextConnectionFlags.remove(PipeRoutingConnectionType.canPowerSubSystemFrom);
                }
            }

            if (tile instanceof IInventory && startPipe.isRoutingPipe()
                    && startPipe.getRoutingPipe() instanceof IDirectRoutingConnection
                    && startPipe.canConnect(tile, direction, false)) {
                if (SimpleServiceLocator.connectionManager
                        .hasDirectConnection(startPipe.getRoutingPipe().getRouter())) {
                    CoreRoutedPipe CRP = SimpleServiceLocator.connectionManager
                            .getConnectedPipe(startPipe.getRoutingPipe().getRouter());
                    if (CRP != null) {
                        tile = CRP.container;
                        isDirectConnection = true;
                        resistance = ((IDirectRoutingConnection) startPipe.getRoutingPipe()).getConnectionResistance();
                    }
                }
            }

            if (tile == null) {
                continue;
            }

            IPipeInformationProvider currentPipe = SimpleServiceLocator.pipeInformationManager
                    .getInformationProviderFor(tile);

            if (currentPipe != null && currentPipe.isRouterInitialized()
                    && (isDirectConnection || SimpleServiceLocator.pipeInformationManager
                            .canConnect(startPipe, currentPipe, direction, true))) {

                listTileEntity(tile);

                if (setVisited.contains(new LPPosition(tile))) {
                    // Don't go where we have been before
                    continue;
                }
                if (side != pair.getValue2() && !root) { // Only straight connections for subsystem power
                    nextConnectionFlags.remove(PipeRoutingConnectionType.canPowerSubSystemFrom);
                }
                if (isDirectConnection) { // ISC doesn't pass power
                    nextConnectionFlags.remove(PipeRoutingConnectionType.canPowerFrom);
                    nextConnectionFlags.remove(PipeRoutingConnectionType.canPowerSubSystemFrom);
                }
                // Iron, obsidian and liquid pipes will separate networks
                if (currentPipe.divideNetwork()) {
                    continue;
                }
                if (currentPipe.powerOnly()) {
                    nextConnectionFlags.remove(PipeRoutingConnectionType.canRouteTo);
                    nextConnectionFlags.remove(PipeRoutingConnectionType.canRequestFrom);
                }
                if (startPipe.isOnewayPipe()) {
                    if (!startPipe.isOutputOpen(direction)) {
                        nextConnectionFlags.remove(PipeRoutingConnectionType.canRouteTo);
                    }
                }
                if (currentPipe.isOnewayPipe()) {
                    nextConnectionFlags.remove(PipeRoutingConnectionType.canPowerSubSystemFrom);
                    if (!currentPipe.isOutputOpen(direction.getOpposite())) {
                        nextConnectionFlags.remove(PipeRoutingConnectionType.canRequestFrom);
                        nextConnectionFlags.remove(PipeRoutingConnectionType.canPowerFrom);
                    }
                }

                if (nextConnectionFlags.isEmpty()) { // don't bother going somewhere we can't do anything with
                    continue;
                }

                HashMap<CoreRoutedPipe, ExitRoute> result = null;
                if (currentPipe instanceof IRouteProvider) {
                    List<RouteInfo> list = ((IRouteProvider) currentPipe).getConnectedPipes(direction.getOpposite());
                    if (list != null) {
                        result = new HashMap<>();
                        LPPosition pos = new LPPosition(currentPipe);
                        for (RouteInfo info : list) {
                            if (info.getPipe() == startPipe) continue;
                            if (setVisited.contains(new LPPosition(info.getPipe()))) {
                                // Don't go where we have been before
                                continue;
                            }
                            distances.put(pos, currentPipe.getDistance() + info.getLength());
                            pathChunks.add(chunkOf(currentPipe));
                            opaqueHops++;
                            result.putAll(getConnectedRoutingPipes(info.getPipe(), nextConnectionFlags, direction));
                            opaqueHops--;
                            pathChunks.remove(pathChunks.size() - 1);
                            distances.remove(pos);
                        }
                    }
                }
                if (result == null) {
                    boolean opaque = isDirectConnection || !(tile instanceof LogisticsTileGenericPipe)
                            || specialTileHops.contains(tile);
                    if (opaque) {
                        opaqueHops++;
                    }
                    pathDirections.add((byte) direction.ordinal());
                    result = getConnectedRoutingPipes(currentPipe, nextConnectionFlags, direction);
                    pathDirections.remove(pathDirections.size() - 1);
                    if (opaque) {
                        opaqueHops--;
                    }
                }
                for (Entry<CoreRoutedPipe, ExitRoute> pipeEntry : result.entrySet()) {
                    // Update Result with the direction we took
                    pipeEntry.getValue().exitOrientation = direction;
                    ExitRoute foundPipe = foundPipes.get(pipeEntry.getKey());
                    if (foundPipe == null) {
                        // New path
                        foundPipes.put(pipeEntry.getKey(), pipeEntry.getValue());
                        // Add resistance
                        pipeEntry.getValue().distanceToDestination += resistance;
                        addMetric(pipeEntry.getValue(), resistance);
                    } else
                        if (pipeEntry.getValue().distanceToDestination + resistance < foundPipe.distanceToDestination) {
                            // If new path is better, replace old path, otherwise do nothing
                            foundPipes.put(pipeEntry.getKey(), pipeEntry.getValue());
                            // Add resistance
                            pipeEntry.getValue().distanceToDestination += resistance;
                            addMetric(pipeEntry.getValue(), resistance);
                        }
                }
            }
        }
        setVisited.remove(new LPPosition(startPipe));
        distances.remove(new LPPosition(startPipe));
        pathChunks.remove(pathChunks.size() - 1);
        if (startPipe.isRoutingPipe()) { // ie, has the recursion returned to the pipe it started from?
            for (ExitRoute e : foundPipes.values()) {
                e.root = (startPipe.getRoutingPipe()).getRouter();
            }
        }
        // If we are a FireWall pipe add our filter to the pipes
        if (startPipe.isFirewallPipe() && root) {
            for (ExitRoute e : foundPipes.values()) {
                e.filters = new OneList<>(startPipe.getFirewallFilter());
            }
        }
        return foundPipes;
    }

    private void listTileEntity(TileEntity tile) {
        if (changeListener != null && tile instanceof ILPTEInformation
                && ((ILPTEInformation) tile).getObject() != null) {
            if (!((ILPTEInformation) tile).getObject().changeListeners.contains(changeListener)) {
                ((ILPTEInformation) tile).getObject().changeListeners.add(changeListener);
            }
            listenedPipes.add(((ILPTEInformation) tile).getObject().changeListeners);
            touchedPipes.add(((ILPTEInformation) tile).getObject());
        }
    }
}
