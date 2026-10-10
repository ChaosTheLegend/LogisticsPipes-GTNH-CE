package logisticspipes.routing.astar;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.LPConstants;
import logisticspipes.api.ILogisticsPowerProvider;
import logisticspipes.asm.te.ILPTEInformation;
import logisticspipes.asm.te.ITileEntityChangeListener;
import logisticspipes.asm.te.LPTileEntityObject;
import logisticspipes.config.Configs;
import logisticspipes.interfaces.ISubSystemPowerProvider;
import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipefxhandlers.Particles;
import logisticspipes.pipes.PipeItemsFirewall;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.routing.ExitRoute;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.IRouterQueuedTask;
import logisticspipes.routing.PipeRoutingConnectionType;
import logisticspipes.routing.pathfinder.PathFinder;
import logisticspipes.utils.CacheHolder;
import logisticspipes.utils.OneList;
import logisticspipes.utils.StackTraceUtil;
import logisticspipes.utils.StackTraceUtil.Info;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.tuples.LPPosition;
import logisticspipes.utils.tuples.Pair;

/**
 * Server-side router backed by the junction graph.
 * <p>
 * Each router is a junction. It scans its own corridors (on the same triggers as before: pipe change listeners,
 * periodic refresh, neighbour removal) and hands them to the {@link JunctionGraphWriter}. It never builds a routing
 * table: route questions go to the {@link JunctionRoutingEngine}, which answers them per (source, destination) pair and
 * caches the answer until an edit actually affects it. Placing or removing a pipe therefore costs a local re-scan plus
 * a graph edit instead of a full Dijkstra on every router of the network.
 */
public class JunctionRouter implements IRouter, Comparable<JunctionRouter> {

    private static final ExitRoute[] NO_ROUTES = new ExitRoute[0];
    private static final int ROUTE_FLAGS = RoutingFlags.CAN_ROUTE_TO | RoutingFlags.CAN_REQUEST_FROM
            | RoutingFlags.CAN_POWER_SUB_SYSTEM_FROM;

    static final int REFRESH_TIME = 20;
    static int iterated = 0; // used pseudo-random to spread interest checks over the tick range

    // things this pipe is interested in (either providing or sinking)
    Set<ItemIdentifier> _hasInterestIn = new TreeSet<>();
    boolean _hasGenericInterest;
    int ticksUntillNextInventoryCheck = 0;

    public Map<CoreRoutedPipe, ExitRoute> _adjacent = new HashMap<>();
    public Map<IRouter, ExitRoute> _adjacentRouter = new HashMap<>();
    public Map<IRouter, ExitRoute> _adjacentRouter_Old = new HashMap<>();
    private Map<IRouter, long[]> _adjacentChunks = new HashMap<>();
    private Map<IRouter, Double> _adjacentMetric = new HashMap<>();
    private Map<IRouter, byte[]> _adjacentTravelPaths = new HashMap<>();
    public List<Pair<ILogisticsPowerProvider, List<IFilter>>> _powerAdjacent = new ArrayList<>();
    public List<Pair<ISubSystemPowerProvider, List<IFilter>>> _subSystemPowerAdjacent = new ArrayList<>();
    private boolean hadPowerData = false;

    public boolean[] sideDisconnected = new boolean[6];

    private EnumSet<ForgeDirection> _routedExits = EnumSet.noneOf(ForgeDirection.class);
    private EnumMap<ForgeDirection, Integer> _subPowerExits = new EnumMap<>(ForgeDirection.class);

    protected final int simpleID;
    private final JunctionId junctionId;
    public final UUID id;
    private final int _dimension;
    private final int _xCoord;
    private final int _yCoord;
    private final int _zCoord;
    private volatile boolean destroied = false;

    private WeakReference<CoreRoutedPipe> _myPipeCache = null;
    private boolean reportedActive = false;
    private final Queue<Pair<Integer, IRouterQueuedTask>> queue = new ConcurrentLinkedQueue<>();

    private final ExitRoute selfRoute;

    public JunctionRouter(UUID globalID, int dimension, int xCoord, int yCoord, int zCoord) {
        id = globalID != null ? globalID : UUID.randomUUID();
        _dimension = dimension;
        _xCoord = xCoord;
        _yCoord = yCoord;
        _zCoord = zCoord;
        simpleID = RouterIds.claim();
        junctionId = JunctionId.of(simpleID);
        selfRoute = new ExitRoute(
                this,
                this,
                ForgeDirection.UNKNOWN,
                ForgeDirection.UNKNOWN,
                0,
                EnumSet.allOf(PipeRoutingConnectionType.class),
                0);
        LPJunctionNetwork.writer().addJunction(junctionId, dimension, xCoord, yCoord, zCoord, this, false);
    }

    public static int getBiggestSimpleID() {
        return RouterIds.getBiggestSimpleID();
    }

    public JunctionId getJunctionId() {
        return junctionId;
    }

    @Override
    public int hashCode() {
        return simpleID; // guaranteed to be unique, and uniform distribution over a range.
    }

    @Override
    public int getSimpleID() {
        return simpleID;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isInDim(int dimension) {
        return _dimension == dimension;
    }

    @Override
    public boolean isAt(int dimension, int xCoord, int yCoord, int zCoord) {
        return _dimension == dimension && _xCoord == xCoord && _yCoord == yCoord && _zCoord == zCoord;
    }

    @Override
    public LPPosition getLPPosition() {
        return new LPPosition(_xCoord, _yCoord, _zCoord);
    }

    @Override
    public int getDimension() {
        return _dimension;
    }

    public boolean isDestroied() {
        return destroied;
    }

    // ------------------------------------------------------------------ pipe cache / activity

    @Override
    public CoreRoutedPipe getPipe() {
        CoreRoutedPipe crp = getCachedPipe();
        if (crp != null) {
            return crp;
        }
        World worldObj = DimensionManager.getWorld(_dimension);
        if (worldObj == null) {
            return null;
        }
        TileEntity tile = worldObj.getTileEntity(_xCoord, _yCoord, _zCoord);

        if (!(tile instanceof LogisticsTileGenericPipe)) {
            return null;
        }
        LogisticsTileGenericPipe pipe = (LogisticsTileGenericPipe) tile;
        if (!(pipe.pipe instanceof CoreRoutedPipe)) {
            return null;
        }
        setPipeCache((CoreRoutedPipe) pipe.pipe);
        return (CoreRoutedPipe) pipe.pipe;
    }

    @Override
    public CoreRoutedPipe getCachedPipe() {
        WeakReference<CoreRoutedPipe> ref = _myPipeCache;
        return ref != null ? ref.get() : null;
    }

    private void setPipeCache(CoreRoutedPipe pipe) {
        _myPipeCache = new WeakReference<>(pipe);
        reportActive(true);
    }

    @Override
    public void clearPipeCache() {
        _myPipeCache = null;
        reportActive(false);
    }

    /** Searches skip junctions whose pipe is not loaded, like the old router skipped routers without a pipe. */
    private synchronized void reportActive(boolean active) {
        if (destroied || reportedActive == active) {
            return;
        }
        reportedActive = active;
        LPJunctionNetwork.writer().setActive(junctionId, active);
    }

    @Override
    public boolean isValidCache() {
        return getPipe() != null;
    }

    // ------------------------------------------------------------------ change detection / corridor scan

    private int connectionNeedsChecking = 0;
    private final List<LPPosition> causedBy = new LinkedList<>();

    private final ITileEntityChangeListener localChangeListener = new ITileEntityChangeListener() {

        @Override
        public void pipeRemoved(LPPosition pos) {
            markConnectionChange(pos);
        }

        @Override
        public void pipeAdded(LPPosition pos, ForgeDirection side) {
            markConnectionChange(pos);
        }

        @Override
        public void pipeModified(LPPosition pos) {
            markConnectionChange(pos);
        }
    };

    private void markConnectionChange(LPPosition pos) {
        if (connectionNeedsChecking == 0) {
            connectionNeedsChecking = 1;
        }
        if (LPConstants.DEBUG && pos != null) {
            causedBy.add(pos);
        }
    }

    /** Ask for a corridor re-scan on the next ticks (used by the chunk index when a corridor's chunk unloads). */
    public void scheduleCorridorRecheck() {
        markConnectionChange(null);
    }

    private Set<List<ITileEntityChangeListener>> listenedPipes = new HashSet<>();
    private Set<LPTileEntityObject> oldTouchedPipes = new HashSet<>();

    private void ensureConnectionsFresh() {
        if (connectionNeedsChecking != 0) {
            checkAdjacentUpdate();
        }
    }

    /**
     * Rechecks the piped connection to all adjacent routers as well as discover new ones.
     */
    private boolean recheckAdjacent() {
        connectionNeedsChecking = 0;
        if (LPConstants.DEBUG) {
            causedBy.clear();
        }
        if (getPipe() != null) {
            if (getPipe().getDebug() != null && getPipe().getDebug().debugThisPipe) {
                Info info = StackTraceUtil.addTraceInformation(
                        "(" + getPipe().getX() + ", " + getPipe().getY() + ", " + getPipe().getZ() + ")");
                StackTraceUtil.printTrace();
                info.end();
            }
            getPipe().spawnParticle(Particles.LightRedParticle, 5);
        }

        boolean adjacentChanged = false;
        CoreRoutedPipe thisPipe = getPipe();
        if (thisPipe == null) {
            return false;
        }
        CorridorScanner finder = new CorridorScanner(
                thisPipe.container,
                Configs.LOGISTICS_DETECTION_COUNT,
                Configs.LOGISTICS_DETECTION_LENGTH,
                localChangeListener);
        List<Pair<ILogisticsPowerProvider, List<IFilter>>> power = finder.powerNodes;
        List<Pair<ISubSystemPowerProvider, List<IFilter>>> subSystemPower = finder.subPowerProvider;
        HashMap<CoreRoutedPipe, ExitRoute> adjacent = finder.result;

        Map<ForgeDirection, List<CoreRoutedPipe>> pipeDirections = new HashMap<>();
        for (Entry<CoreRoutedPipe, ExitRoute> entry : adjacent.entrySet()) {
            pipeDirections.computeIfAbsent(entry.getValue().exitOrientation, k -> new ArrayList<>())
                    .add(entry.getKey());
        }
        for (Entry<ForgeDirection, List<CoreRoutedPipe>> entry : pipeDirections.entrySet()) {
            if (entry.getValue().size() > Configs.MAX_UNROUTED_CONNECTIONS) {
                for (CoreRoutedPipe pipe : entry.getValue()) {
                    adjacent.remove(pipe);
                }
            }
        }

        for (List<ITileEntityChangeListener> list : listenedPipes) {
            if (!finder.listenedPipes.contains(list)) {
                list.remove(localChangeListener);
            }
        }
        listenedPipes = finder.listenedPipes;

        for (CoreRoutedPipe pipe : adjacent.keySet()) {
            if (pipe.stillNeedReplace()) {
                return false;
            }
        }

        boolean[] oldSideDisconnected = sideDisconnected;
        sideDisconnected = new boolean[6];
        checkSecurity(adjacent);

        boolean changed = false;
        for (int i = 0; i < 6; i++) {
            changed |= sideDisconnected[i] != oldSideDisconnected[i];
        }
        if (changed) {
            CoreRoutedPipe pipe = getPipe();
            if (pipe != null) {
                pipe.getWorld().notifyBlocksOfNeighborChange(
                        pipe.getX(),
                        pipe.getY(),
                        pipe.getZ(),
                        pipe.getWorld().getBlock(pipe.getX(), pipe.getY(), pipe.getZ()));
                pipe.refreshConnectionAndRender(false);
            }
            adjacentChanged = true;
        }

        if (_adjacent.size() != adjacent.size()) {
            adjacentChanged = true;
        }
        for (CoreRoutedPipe pipe : _adjacent.keySet()) {
            if (!adjacent.containsKey(pipe)) {
                adjacentChanged = true;
                break;
            }
        }
        boolean powerChanged = !samePowerLists(_powerAdjacent, power)
                || !samePowerLists(_subSystemPowerAdjacent, subSystemPower);
        adjacentChanged |= powerChanged;
        for (Entry<CoreRoutedPipe, ExitRoute> pipe : adjacent.entrySet()) {
            ExitRoute oldExit = _adjacent.get(pipe.getKey());
            if (oldExit == null || !pipe.getValue().equals(oldExit)) {
                adjacentChanged = true;
                break;
            }
        }
        if (!adjacentChanged) {
            // ExitRoute.equals ignores the pipes a corridor runs through, e.g. a plain pipe swapped for a foreign one
            for (Entry<CoreRoutedPipe, ExitRoute> pipe : adjacent.entrySet()) {
                if (!java.util.Arrays.equals(
                        finder.travelPathOf(pipe.getValue()),
                        _adjacentTravelPaths.get(pipe.getKey().getRouter()))) {
                    adjacentChanged = true;
                    break;
                }
            }
        }

        if (!oldTouchedPipes.equals(finder.touchedPipes)) {
            CacheHolder.clearCache(oldTouchedPipes);
            CacheHolder.clearCache(finder.touchedPipes);
            oldTouchedPipes = finder.touchedPipes;
            BitSet visited = new BitSet(getBiggestSimpleID());
            visited.set(getSimpleID());
            act(visited, new FloodClearCache());
        }

        if (adjacentChanged) {
            HashMap<IRouter, ExitRoute> adjacentRouter = new HashMap<>();
            HashMap<IRouter, long[]> adjacentChunks = new HashMap<>();
            HashMap<IRouter, Double> adjacentMetric = new HashMap<>();
            HashMap<IRouter, byte[]> adjacentTravelPaths = new HashMap<>();
            EnumSet<ForgeDirection> routedexits = EnumSet.noneOf(ForgeDirection.class);
            EnumMap<ForgeDirection, Integer> subpowerexits = new EnumMap<>(ForgeDirection.class);
            for (Entry<CoreRoutedPipe, ExitRoute> pipe : adjacent.entrySet()) {
                IRouter router = pipe.getKey().getRouter();
                adjacentRouter.put(router, pipe.getValue());
                adjacentChunks.put(router, finder.chunksOf(pipe.getValue()));
                adjacentMetric.put(router, finder.metricOf(pipe.getValue()));
                adjacentTravelPaths.put(router, finder.travelPathOf(pipe.getValue()));
                if ((pipe.getValue().connectionDetails.contains(PipeRoutingConnectionType.canRouteTo)
                        || pipe.getValue().connectionDetails.contains(PipeRoutingConnectionType.canRequestFrom)
                                && !routedexits.contains(pipe.getValue().exitOrientation))) {
                    routedexits.add(pipe.getValue().exitOrientation);
                }
                if (!subpowerexits.containsKey(pipe.getValue().exitOrientation) && pipe.getValue().connectionDetails
                        .contains(PipeRoutingConnectionType.canPowerSubSystemFrom)) {
                    subpowerexits.put(
                            pipe.getValue().exitOrientation,
                            PathFinder.messureDistanceToNextRoutedPipe(
                                    getLPPosition(),
                                    pipe.getValue().exitOrientation,
                                    pipe.getKey().getWorld()));
                }
            }
            _adjacent = Collections.unmodifiableMap(adjacent);
            _adjacentRouter_Old = _adjacentRouter;
            _adjacentRouter = Collections.unmodifiableMap(adjacentRouter);
            _adjacentChunks = adjacentChunks;
            _adjacentMetric = adjacentMetric;
            _adjacentTravelPaths = adjacentTravelPaths;
            _powerAdjacent = power != null ? Collections.unmodifiableList(power) : null;
            _subSystemPowerAdjacent = subSystemPower != null ? Collections.unmodifiableList(subSystemPower) : null;
            _routedExits = routedexits;
            _subPowerExits = subpowerexits;
            publishCorridors(powerChanged);
        }
        return adjacentChanged;
    }

    private static <T> boolean samePowerLists(List<T> a, List<T> b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.containsAll(b) && b.containsAll(a);
    }

    private void checkSecurity(HashMap<CoreRoutedPipe, ExitRoute> adjacent) {
        CoreRoutedPipe pipe = getPipe();
        if (pipe == null) {
            return;
        }
        UUID id = pipe.getSecurityID();
        List<CoreRoutedPipe> toRemove = new ArrayList<>();
        if (id != null) {
            for (Entry<CoreRoutedPipe, ExitRoute> entry : adjacent.entrySet()) {
                if (!entry.getValue().connectionDetails.contains(PipeRoutingConnectionType.canRouteTo)
                        && !entry.getValue().connectionDetails.contains(PipeRoutingConnectionType.canRequestFrom)) {
                    continue;
                }
                UUID thatId = entry.getKey().getSecurityID();
                if (!(pipe instanceof PipeItemsFirewall)) {
                    if (thatId == null) {
                        entry.getKey().insetSecurityID(id);
                    } else if (!id.equals(thatId)) {
                        sideDisconnected[entry.getValue().exitOrientation.ordinal()] = true;
                    }
                } else {
                    if (!(entry.getKey() instanceof PipeItemsFirewall)) {
                        if (thatId != null && !id.equals(thatId)) {
                            sideDisconnected[entry.getValue().exitOrientation.ordinal()] = true;
                        }
                    }
                }
            }
            for (Entry<CoreRoutedPipe, ExitRoute> entry : adjacent.entrySet()) {
                if (sideDisconnected[entry.getValue().exitOrientation.ordinal()]) {
                    toRemove.add(entry.getKey());
                }
            }
            for (CoreRoutedPipe remove : toRemove) {
                adjacent.remove(remove);
            }
        }
    }

    /** Hand this junction's corridors (the old "LSA") to the graph writer. */
    private void publishCorridors(boolean powerChanged) {
        List<EdgeSpec> specs = new ArrayList<>(_adjacentRouter.size());
        for (Entry<IRouter, ExitRoute> entry : _adjacentRouter.entrySet()) {
            ExitRoute exit = entry.getValue();
            int flags = RoutingFlags.toMask(exit.connectionDetails);
            // distanceToDestination is only meaningful when items can be routed along the corridor; use the scanned
            // length for request-only and power-only corridors so their routes are ordered by real distance too.
            double weight = (flags & RoutingFlags.CAN_ROUTE_TO) != 0 ? exit.distanceToDestination
                    : _adjacentMetric.getOrDefault(entry.getKey(), exit.distanceToDestination);
            specs.add(
                    new EdgeSpec(
                            JunctionId.of(entry.getKey().getSimpleID()),
                            weight,
                            flags,
                            exit.filters,
                            exit.blockDistance,
                            exit.exitOrientation.ordinal(),
                            exit.insertOrientation.ordinal(),
                            _adjacentChunks.get(entry.getKey()),
                            _adjacentTravelPaths.get(entry.getKey())));
        }
        JunctionGraphWriter writer = LPJunctionNetwork.writer();
        writer.setEdges(junctionId, specs);
        if (powerChanged) {
            // Only junctions with providers attached carry data: every pipe's power poll routes to all of them.
            boolean hasPower = (_powerAdjacent != null && !_powerAdjacent.isEmpty())
                    || (_subSystemPowerAdjacent != null && !_subSystemPowerAdjacent.isEmpty());
            PowerData data = hasPower ? new PowerData(_powerAdjacent, _subSystemPowerAdjacent) : null;
            if (data != null || hadPowerData) {
                writer.setData(junctionId, data, data != null);
            }
            hadPowerData = data != null;
        }
    }

    @Override
    public boolean checkAdjacentUpdate() {
        boolean blockNeedsUpdate = recheckAdjacent();
        if (!blockNeedsUpdate) {
            return false;
        }
        CoreRoutedPipe pipe = getPipe();
        if (pipe == null) {
            return true;
        }
        pipe.refreshRender(true);
        return true;
    }

    @Override
    public void act(BitSet hasBeenProcessed, IRAction actor) {
        if (hasBeenProcessed.get(simpleID)) {
            return;
        }
        hasBeenProcessed.set(simpleID);
        if (!actor.isInteresting(this)) {
            return;
        }
        actor.doTo(this);
        for (IRouter r : _adjacentRouter.keySet()) {
            r.act(hasBeenProcessed, actor);
        }
    }

    /** Flood-fill recheckAdjacent: neighbours re-scan while their corridors keep changing. */
    static class FloodCheckAdjacent implements IRAction {

        @Override
        public boolean isInteresting(IRouter that) {
            return that.checkAdjacentUpdate();
        }

        @Override
        public void doTo(IRouter that) {}
    }

    static class FloodClearCache implements IRAction {

        @Override
        public boolean isInteresting(IRouter that) {
            return true;
        }

        @Override
        public void doTo(IRouter that) {
            if (that instanceof JunctionRouter) {
                CacheHolder.clearCache(((JunctionRouter) that).oldTouchedPipes);
            }
        }
    }

    private void recheckNeighbours() {
        // Routing pipes do not fire change listeners on removal, so the routers that could see this one re-scan.
        BitSet visited = new BitSet(getBiggestSimpleID());
        IRAction flood = new FloodCheckAdjacent();
        visited.set(simpleID);
        for (IRouter r : _adjacentRouter_Old.keySet()) {
            r.act(visited, flood);
        }
        for (IRouter r : _adjacentRouter.keySet()) {
            r.act(visited, flood);
        }
        _adjacentRouter_Old = new HashMap<>();
    }

    @Override
    public void update(boolean doFullRefresh, CoreRoutedPipe pipe) {
        if (pipe != null && getCachedPipe() != pipe) {
            setPipeCache(pipe);
        }
        if (connectionNeedsChecking == 2) {
            Info info = null;
            if (LPConstants.DEBUG) {
                info = StackTraceUtil.addTraceInformation(causedBy.toString());
            }
            checkAdjacentUpdate();
            if (info != null) {
                info.end();
            }
        }
        if (connectionNeedsChecking == 1) {
            connectionNeedsChecking = 2;
        }
        handleQueuedTasks(pipe);
        updateInterests();
        if (doFullRefresh && pipe != null) {
            if (pipe.container instanceof ILPTEInformation && ((ILPTEInformation) pipe.container).getObject() != null) {
                if (!((ILPTEInformation) pipe.container).getObject().changeListeners.contains(localChangeListener)) {
                    ((ILPTEInformation) pipe.container).getObject().changeListeners.add(localChangeListener);
                }
            }
            checkAdjacentUpdate();
        }
    }

    private void handleQueuedTasks(CoreRoutedPipe pipe) {
        Pair<Integer, IRouterQueuedTask> element;
        while ((element = queue.poll()) != null) {
            if (element.getValue1() > MainProxy.getGlobalTick()) {
                element.getValue2().call(pipe, this);
            }
        }
    }

    @Override
    public void queueTask(int i, IRouterQueuedTask callable) {
        queue.add(new Pair<>(i + MainProxy.getGlobalTick(), callable));
    }

    /**
     * Nothing to flag: routes are recomputed lazily when a query finds its cached route stale.
     */
    @Override
    public void flagForRoutingUpdate() {}

    /** Manual network refresh (routing laser tool): drop every cached route of this network. */
    @Override
    public void forceLsaUpdate() {
        LPJunctionNetwork.writer().invalidateComponent(junctionId);
    }

    @Override
    public void destroy() {
        LPJunctionNetwork.writer().removeJunction(junctionId);
        removeAllInterests();
        _myPipeCache = null;
        destroied = true;
        SimpleServiceLocator.routerManager.removeRouter(simpleID);
        for (List<ITileEntityChangeListener> list : listenedPipes) {
            list.remove(localChangeListener);
        }
        recheckNeighbours();
        RouterIds.release(simpleID);
    }

    // ------------------------------------------------------------------ route queries

    /** Converted routes (old {@code ExitRoute} form) of a pair result and the labels they came from. */
    private static final class PairView {

        static final PairView EMPTY = new PairView(NO_ROUTES, new RouteLabel[0]);

        final ExitRoute[] routes;
        final RouteLabel[] labels;

        PairView(ExitRoute[] routes, RouteLabel[] labels) {
            this.routes = routes;
            this.labels = labels;
        }
    }

    private ExitRoute[] exitRoutes(RouteCacheEntry entry) {
        return pairView(entry).routes;
    }

    /** Built once per cache entry. */
    private PairView pairView(RouteCacheEntry entry) {
        Object view = entry.getAdapterView();
        if (view instanceof PairView) {
            return (PairView) view;
        }
        NetworkGraph graph = entry.computedOn();
        List<ExitRoute> list = new ArrayList<>(entry.routes.size());
        List<RouteLabel> labels = new ArrayList<>(entry.routes.size());
        for (RouteLabel label : entry.routes) {
            if ((label.newFlags & ROUTE_FLAGS) != 0) {
                ExitRoute e = toExitRoute(graph, label);
                if (e != null) {
                    list.add(e);
                    labels.add(label);
                }
            }
        }
        PairView result = list.isEmpty() ? PairView.EMPTY
                : new PairView(list.toArray(new ExitRoute[0]), labels.toArray(new RouteLabel[0]));
        entry.setAdapterView(result);
        return result;
    }

    private ExitRoute toExitRoute(NetworkGraph graph, RouteLabel label) {
        JunctionNode node = graph.node(label.node);
        if (node == null || !(node.payload instanceof IRouter)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<IFilter> filters = (List<IFilter>) label.filters;
        ExitRoute e = new ExitRoute(
                this,
                (IRouter) node.payload,
                label.distance,
                RoutingFlags.toEnumSet(label.newFlags),
                filters,
                Collections.emptyList(),
                label.blockDistance);
        e.exitOrientation = ForgeDirection.getOrientation(label.firstEdge.exitSide);
        return e;
    }

    private ExitRoute[] routesTo(int destination) {
        return pairTo(destination).routes;
    }

    private PairView pairTo(int destination) {
        if (destination <= 0 || destroied) {
            return PairView.EMPTY;
        }
        return pairView(LPJunctionNetwork.engine().findRoute(junctionId, JunctionId.of(destination)));
    }

    private static boolean passesFilters(ExitRoute exit, boolean active, ItemIdentifier type) {
        for (IFilter filter : exit.filters) {
            if (!active) {
                if (filter.blockRouting() || filter.isBlocked() == filter.isFilteredItem(type)) {
                    return false;
                }
            } else {
                if ((filter.blockProvider() && filter.blockCrafting())
                        || filter.isBlocked() == filter.isFilteredItem(type)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public ExitRoute getExitFor(int id, boolean active, ItemIdentifier type) {
        ensureConnectionsFresh();
        if (id == simpleID) {
            return selfRoute;
        }
        ExitRoute[] routes = routesTo(id);
        int i = pickRoute(routes, active, type);
        return i < 0 ? null : routes[i];
    }

    private static int pickRoute(ExitRoute[] routes, boolean active, ItemIdentifier type) {
        for (int i = 0; i < routes.length; i++) {
            if (routes[i].containsFlag(PipeRoutingConnectionType.canRouteTo)
                    && passesFilters(routes[i], active, type)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The route {@link #getExitFor} picks, as the whole corridor sequence from this junction. {@code null} when there
     * is none or {@code id} is this junction.
     */
    public RouteLabel getRouteFor(int id, boolean active, ItemIdentifier type) {
        ensureConnectionsFresh();
        if (id == simpleID) {
            return null;
        }
        PairView view = pairTo(id);
        int i = pickRoute(view.routes, active, type);
        return i < 0 ? null : view.labels[i];
    }

    @Override
    public boolean hasRoute(int id, boolean active, ItemIdentifier type) {
        if (!SimpleServiceLocator.routerManager.isRouterUnsafe(id, false)) {
            return false;
        }
        return getExitFor(id, active, type) != null;
    }

    @Override
    public List<ExitRoute> getDistanceTo(IRouter r) {
        ensureConnectionsFresh();
        if (r == null) {
            return new ArrayList<>(0);
        }
        if (r == this) {
            return new OneList<>(selfRoute);
        }
        ExitRoute[] routes = routesTo(r.getSimpleID());
        return routes.length == 0 ? new ArrayList<>(0) : Collections.unmodifiableList(java.util.Arrays.asList(routes));
    }

    /**
     * One-to-many variant of {@link #getDistanceTo}: routes to every router in {@code targets} from a single search
     * (targets already cached are answered from the cache). Used by crafting-tree resolution.
     */
    public Map<IRouter, List<ExitRoute>> getDistancesTo(Collection<IRouter> targets) {
        ensureConnectionsFresh();
        Map<IRouter, List<ExitRoute>> result = new LinkedHashMap<>();
        Set<JunctionId> ids = new HashSet<>();
        for (IRouter r : targets) {
            if (r != null && r != this && r.getSimpleID() > 0) {
                ids.add(JunctionId.of(r.getSimpleID()));
            }
        }
        Map<JunctionId, RouteCacheEntry> entries = destroied ? Collections.emptyMap()
                : LPJunctionNetwork.engine().findRoutesToTargets(junctionId, ids);
        for (IRouter r : targets) {
            if (r == null) {
                continue;
            }
            if (r == this) {
                result.put(r, new OneList<>(selfRoute));
                continue;
            }
            RouteCacheEntry entry = entries.get(JunctionId.of(Math.max(0, r.getSimpleID())));
            ExitRoute[] routes = entry == null ? NO_ROUTES : exitRoutes(entry);
            result.put(
                    r,
                    routes.length == 0 ? new ArrayList<>(0)
                            : Collections.unmodifiableList(java.util.Arrays.asList(routes)));
        }
        return result;
    }

    /** Whole-network views (by cost, route table), derived once per sweep result. */
    private static final class SweepView {

        final List<ExitRoute> byCost;
        volatile List<List<ExitRoute>> table;

        SweepView(List<ExitRoute> byCost) {
            this.byCost = byCost;
        }
    }

    /** Power providers known to a junction, stored as the junction's graph data. */
    static final class PowerData {

        final List<Pair<ILogisticsPowerProvider, List<IFilter>>> power;
        final List<Pair<ISubSystemPowerProvider, List<IFilter>>> subSystemPower;

        PowerData(List<Pair<ILogisticsPowerProvider, List<IFilter>>> power,
                List<Pair<ISubSystemPowerProvider, List<IFilter>>> subSystemPower) {
            this.power = power;
            this.subSystemPower = subSystemPower;
        }
    }

    private SweepView sweepView() {
        ensureConnectionsFresh();
        SweepEntry sweep = LPJunctionNetwork.engine().sweep(junctionId);
        Object view = sweep.getAdapterView();
        if (view instanceof SweepView) {
            return (SweepView) view;
        }
        NetworkGraph graph = sweep.computedOn();
        List<ExitRoute> byCost = new ArrayList<>(sweep.settledOrder.size() + 1);
        byCost.add(selfRoute);
        for (RouteLabel label : sweep.settledOrder) {
            if ((label.newFlags & ROUTE_FLAGS) != 0) {
                ExitRoute e = toExitRoute(graph, label);
                if (e != null) {
                    byCost.add(e);
                }
            }
        }
        SweepView result = new SweepView(Collections.unmodifiableList(byCost));
        sweep.setAdapterView(result);
        return result;
    }

    @Override
    public List<ExitRoute> getIRoutersByCost() {
        return sweepView().byCost;
    }

    @Override
    public List<List<ExitRoute>> getRouteTable() {
        SweepView view = sweepView();
        List<List<ExitRoute>> table = view.table;
        if (table != null) {
            return table;
        }
        Map<Integer, List<ExitRoute>> byDestination = new HashMap<>();
        int max = simpleID;
        for (ExitRoute e : view.byCost) {
            int d = e.destination.getSimpleID();
            byDestination.computeIfAbsent(d, k -> new ArrayList<>(1)).add(e);
            max = Math.max(max, d);
        }
        ArrayList<List<ExitRoute>> list = new ArrayList<>(Math.max(max, getBiggestSimpleID()) + 1);
        for (int i = 0; i <= Math.max(max, getBiggestSimpleID()); i++) {
            List<ExitRoute> routes = byDestination.get(i);
            list.add(
                    routes == null ? null
                            : routes.size() == 1 ? new OneList<>(routes.get(0)) : Collections.unmodifiableList(routes));
        }
        table = Collections.unmodifiableList(list);
        view.table = table;
        return table;
    }

    @Override
    public List<Pair<ILogisticsPowerProvider, List<IFilter>>> getPowerProvider() {
        return powerView().power;
    }

    @Override
    public List<Pair<ISubSystemPowerProvider, List<IFilter>>> getSubSystemPowerProvider() {
        return powerView().subSystemPower;
    }

    /**
     * Power tables. Every pipe polls these (energy checks run every few ticks), so they are not derived from the
     * whole-network view, which any improvement anywhere invalidates. Only junctions with power providers attached
     * matter, so the tables come from cached pair routes to exactly those junctions: one one-to-many search when
     * something relevant changed, cache hits otherwise.
     */
    private static final class PowerView {

        /** Graph snapshot the view was built or last confirmed on; routes to power junctions only change with it. */
        volatile NetworkGraph graph;
        final List<JunctionId> targets;
        final RouteCacheEntry[] entries;
        final Object[] data;
        final Object ownPower;
        final Object ownSubSystemPower;
        final List<Pair<ILogisticsPowerProvider, List<IFilter>>> power;
        final List<Pair<ISubSystemPowerProvider, List<IFilter>>> subSystemPower;

        PowerView(List<JunctionId> targets, RouteCacheEntry[] entries, Object[] data, Object ownPower,
                Object ownSubSystemPower, List<Pair<ILogisticsPowerProvider, List<IFilter>>> power,
                List<Pair<ISubSystemPowerProvider, List<IFilter>>> subSystemPower) {
            this.targets = targets;
            this.entries = entries;
            this.data = data;
            this.ownPower = ownPower;
            this.ownSubSystemPower = ownSubSystemPower;
            this.power = power;
            this.subSystemPower = subSystemPower;
        }
    }

    private volatile PowerView powerView;

    private PowerView powerView() {
        ensureConnectionsFresh();
        NetworkGraph graph = LPJunctionNetwork.writer().graph();
        PowerView last = powerView;
        if (last != null && last.graph == graph
                && last.ownPower == _powerAdjacent
                && last.ownSubSystemPower == _subSystemPowerAdjacent) {
            // every pipe polls this every 10 ticks: skip the route lookups while nothing in the graph changed
            return last;
        }
        List<JunctionId> targets = graph.dataJunctionsInComponentOf(junctionId);
        Map<JunctionId, RouteCacheEntry> routes = targets.isEmpty() || destroied ? Collections.emptyMap()
                : LPJunctionNetwork.engine().findRoutesToTargets(junctionId, new java.util.LinkedHashSet<>(targets));
        RouteCacheEntry[] entries = new RouteCacheEntry[targets.size()];
        Object[] data = new Object[targets.size()];
        for (int i = 0; i < entries.length; i++) {
            entries[i] = routes.get(targets.get(i));
            JunctionNode node = graph.node(targets.get(i));
            data[i] = node == null ? null : node.data;
        }
        // only tie the view to this snapshot when its routes are valid on it; a stale route (refreshed in the
        // background) would otherwise stay pinned until the graph changes again, e.g. "unpowered" after a merge
        boolean fresh = true;
        for (RouteCacheEntry e : entries) {
            if (e != null && !e.isValid(graph)) {
                fresh = false;
                break;
            }
        }
        PowerView cached = powerView;
        if (cached != null && cached.targets.equals(targets)
                && cached.ownPower == _powerAdjacent
                && cached.ownSubSystemPower == _subSystemPowerAdjacent
                && sameElements(cached.entries, entries)
                && sameElements(cached.data, data)) {
            cached.graph = fresh ? graph : null;
            return cached;
        }

        // closest providers first, like the settle order of the old table
        Integer[] order = new Integer[entries.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, java.util.Comparator.comparingDouble(i -> {
            RouteCacheEntry e = entries[i];
            return e == null || e.routes.isEmpty() ? Double.POSITIVE_INFINITY : e.routes.get(0).distance;
        }));
        ArrayList<Pair<ILogisticsPowerProvider, List<IFilter>>> powerTable = _powerAdjacent != null
                ? new ArrayList<>(_powerAdjacent)
                : new ArrayList<>(5);
        ArrayList<Pair<ISubSystemPowerProvider, List<IFilter>>> subPowerTable = _subSystemPowerAdjacent != null
                ? new ArrayList<>(_subSystemPowerAdjacent)
                : new ArrayList<>(5);
        for (int i : order) {
            if (entries[i] == null || !(data[i] instanceof PowerData)) {
                continue;
            }
            PowerData pd = (PowerData) data[i];
            for (RouteLabel label : entries[i].routes) {
                @SuppressWarnings("unchecked")
                List<IFilter> labelFilters = (List<IFilter>) label.filters;
                if (label.has(RoutingFlags.CAN_POWER_FROM) && pd.power != null) {
                    addPower(powerTable, pd.power, labelFilters);
                }
                if (label.has(RoutingFlags.CAN_POWER_SUB_SYSTEM_FROM) && pd.subSystemPower != null) {
                    addPower(subPowerTable, pd.subSystemPower, labelFilters);
                }
            }
        }
        PowerView view = new PowerView(
                targets,
                entries,
                data,
                _powerAdjacent,
                _subSystemPowerAdjacent,
                Collections.unmodifiableList(powerTable),
                Collections.unmodifiableList(subPowerTable));
        view.graph = fresh ? graph : null;
        powerView = view;
        return view;
    }

    private static <P> void addPower(List<Pair<P, List<IFilter>>> table, List<Pair<P, List<IFilter>>> providers,
            List<IFilter> routeFilters) {
        for (Pair<P, List<IFilter>> p : providers) {
            Pair<P, List<IFilter>> entry = p.copy();
            List<IFilter> list = new ArrayList<>(p.getValue2());
            list.addAll(routeFilters);
            entry.setValue2(Collections.unmodifiableList(list));
            if (!table.contains(entry)) {
                table.add(entry);
            }
        }
    }

    private static boolean sameElements(Object[] a, Object[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    /** Recompute this router's whole-network view right now (debug command). */
    public void recomputeNetworkView() {
        forceLsaUpdate();
        LPJunctionNetwork.writer().flush();
        sweepView();
    }

    // ------------------------------------------------------------------ exits and sides

    @Override
    public boolean isRoutedExit(ForgeDirection o) {
        return _routedExits.contains(o);
    }

    @Override
    public boolean isSubPoweredExit(ForgeDirection o) {
        return _subPowerExits.containsKey(o);
    }

    @Override
    public int getDistanceToNextPowerPipe(ForgeDirection dir) {
        return _subPowerExits.get(dir);
    }

    @Override
    public boolean isSideDisconneceted(ForgeDirection dir) {
        return ForgeDirection.UNKNOWN != dir && sideDisconnected[dir.ordinal()];
    }

    @Override
    public List<ExitRoute> getRoutersOnSide(ForgeDirection direction) {
        List<ExitRoute> routers = new ArrayList<>();
        for (ExitRoute exit : _adjacentRouter.values()) {
            if (exit.exitOrientation == direction) {
                routers.add(exit);
            }
        }
        return routers;
    }

    @Override
    public LogisticsModule getLogisticsModule() {
        CoreRoutedPipe pipe = getPipe();
        if (pipe == null) {
            return null;
        }
        return pipe.getLogisticsModule();
    }

    // ------------------------------------------------------------------ interests

    @Override
    public void updateInterests() {
        if (--ticksUntillNextInventoryCheck > 0) {
            return;
        }
        ticksUntillNextInventoryCheck = REFRESH_TIME;
        if (iterated++ % simpleID == 0) {
            ticksUntillNextInventoryCheck++; // randomly wait 1 extra tick so routers don't all check at once
        }
        if (iterated >= getBiggestSimpleID()) {
            iterated = 0;
        }
        CoreRoutedPipe pipe = getPipe();
        if (pipe == null) {
            return;
        }
        if (pipe.hasGenericInterests()) {
            _hasGenericInterest = true;
            InterestRegistry.addGeneric(this);
        } else {
            removeGenericInterest();
        }
        Set<ItemIdentifier> newInterests = pipe.getSpecificInterests();
        if (newInterests == null) {
            newInterests = new TreeSet<>();
        }
        if (!newInterests.equals(_hasInterestIn)) {
            for (ItemIdentifier i : _hasInterestIn) {
                if (!newInterests.contains(i)) {
                    InterestRegistry.remove(i, this);
                }
            }
            for (ItemIdentifier i : newInterests) {
                if (!_hasInterestIn.contains(i)) {
                    InterestRegistry.add(i, this);
                }
            }
            _hasInterestIn = newInterests;
        }
    }

    private void removeGenericInterest() {
        _hasGenericInterest = false;
        InterestRegistry.removeGeneric(this);
    }

    private void removeAllInterests() {
        removeGenericInterest();
        for (ItemIdentifier i : _hasInterestIn) {
            InterestRegistry.remove(i, this);
        }
        _hasInterestIn.clear();
    }

    @Override
    public void clearInterests() {
        removeAllInterests();
    }

    public boolean hasGenericInterest() {
        return _hasGenericInterest;
    }

    public boolean hasInterestIn(ItemIdentifier item) {
        return _hasInterestIn.contains(item);
    }

    // ------------------------------------------------------------------ misc

    @Override
    public int compareTo(JunctionRouter o) {
        return simpleID - o.simpleID;
    }

    @Override
    public String toString() {
        NetworkGraph graph = LPJunctionNetwork.writer().graph();
        return "JunctionRouter: {ID: " + simpleID
                + ", UUID: "
                + getId()
                + ", AT: ("
                + _dimension
                + ", "
                + _xCoord
                + ", "
                + _yCoord
                + ", "
                + _zCoord
                + "), Component: "
                + graph.componentOf(junctionId)
                + ", Destroied: "
                + isDestroied()
                + "}";
    }
}
