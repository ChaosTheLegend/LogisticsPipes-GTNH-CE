package logisticspipes.routing.astar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import logisticspipes.proxy.MainProxy;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.RouterManager;
import logisticspipes.utils.tuples.LPPosition;

/**
 * Router manager that creates {@link JunctionRouter}s on the server. Client routers, direct connections and security
 * stations are inherited unchanged from {@link RouterManager}.
 */
public class JunctionRouterManager extends RouterManager {

    private final ArrayList<IRouter> routersServer = new ArrayList<>();
    private final Map<UUID, Integer> uuidMap = new HashMap<>();
    // dimension -> packed position -> router, so loading n pipes is not n linear scans over every router
    private final Map<Integer, Map<Long, IRouter>> routersByPos = new HashMap<>();

    @Override
    public IRouter getRouter(int id) {
        if (id <= 0 || MainProxy.isClient()) {
            return null;
        }
        return getServerRouter(id);
    }

    private IRouter getServerRouter(int id) {
        synchronized (routersServer) {
            return id < routersServer.size() ? routersServer.get(id) : null;
        }
    }

    @Override
    public IRouter getRouterUnsafe(Integer id, boolean side) {
        if (side || id <= 0) {
            return null;
        }
        return getServerRouter(id);
    }

    @Override
    public int getIDforUUID(UUID id) {
        if (id == null) {
            return -1;
        }
        synchronized (routersServer) {
            Integer iId = uuidMap.get(id);
            return iId == null ? -1 : iId;
        }
    }

    @Override
    public void removeRouter(int id) {
        if (!MainProxy.isClient()) {
            synchronized (routersServer) {
                if (id >= 0 && id < routersServer.size()) {
                    IRouter old = routersServer.set(id, null);
                    if (old != null) {
                        unindex(old);
                    }
                }
            }
        }
    }

    @Override
    public IRouter getOrCreateRouter(UUID uuid, int dimension, int xCoord, int yCoord, int zCoord,
            boolean forceCreateDuplicate) {
        if (MainProxy.isClient()) {
            return super.getOrCreateRouter(uuid, dimension, xCoord, yCoord, zCoord, forceCreateDuplicate);
        }
        synchronized (routersServer) {
            Map<Long, IRouter> inDim = routersByPos.computeIfAbsent(dimension, d -> new HashMap<>());
            long pos = packPosition(xCoord, yCoord, zCoord);
            IRouter existing = inDim.get(pos);
            if (existing != null && !forceCreateDuplicate) {
                return existing;
            }
            IRouter r = new JunctionRouter(uuid, dimension, xCoord, yCoord, zCoord);
            if (existing == null) {
                inDim.put(pos, r);
            }
            int rId = r.getSimpleID();
            if (routersServer.size() <= rId) {
                routersServer.ensureCapacity(rId + 1);
                while (routersServer.size() <= rId) {
                    routersServer.add(null);
                }
            }
            routersServer.set(rId, r);
            uuidMap.put(r.getId(), r.getSimpleID());
            return r;
        }
    }

    /** Caller holds the routersServer lock. Leaves the entry alone if it points to another router at that spot. */
    private void unindex(IRouter router) {
        LPPosition pos = router.getLPPosition();
        Map<Long, IRouter> inDim = routersByPos.get(router.getDimension());
        if (inDim != null) {
            inDim.remove(packPosition(pos.getX(), pos.getY(), pos.getZ()), router);
        }
    }

    @Override
    public boolean isRouter(int id) {
        if (MainProxy.isClient()) {
            return true;
        }
        return getServerRouter(id) != null;
    }

    @Override
    public boolean isRouterUnsafe(int id, boolean side) {
        if (side) {
            return true;
        }
        return getServerRouter(id) != null;
    }

    @Override
    public List<IRouter> getRouters() {
        if (MainProxy.isClient()) {
            return super.getRouters();
        }
        return Collections.unmodifiableList(routersServer);
    }

    @Override
    public void serverStopClean() {
        super.serverStopClean();
        synchronized (routersServer) {
            routersServer.clear();
            uuidMap.clear();
            routersByPos.clear();
        }
    }

    @Override
    public void dimensionUnloaded(int dim) {
        synchronized (routersServer) {
            for (IRouter r : routersServer) {
                if (r != null && r.isInDim(dim)) {
                    r.clearPipeCache();
                    r.clearInterests();
                }
            }
        }
    }

    @Override
    public void printAllRouters() {
        synchronized (routersServer) {
            for (IRouter router : routersServer) {
                if (router != null) {
                    System.out.println(router);
                }
            }
        }
        for (String line : LPJunctionNetwork.describe()) {
            System.out.println(line);
        }
    }
}
