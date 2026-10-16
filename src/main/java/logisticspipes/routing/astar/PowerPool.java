package logisticspipes.routing.astar;

/**
 * The junction data (power providers) every junction of one power pool shares.
 * <p>
 * A pool is an island (graph component) unless a corridor in it doesn't pass power: a power-blocking firewall, or a
 * corridor without {@link RoutingFlags#CAN_POWER_FROM} in either direction (inventory system connectors). Such an
 * island is split along those corridors. Pools that share a store are joined (see {@link Member}). The writer builds
 * the pools at publish, so a pipe's power lookup is one array read instead of routes to every power junction.
 */
public final class PowerPool {

    /**
     * Junction data that names what it stores power in. Pools whose junctions name the same thing are joined: a power
     * junction touching two networks is one store, so it links their pools.
     */
    public interface Member {

        Iterable<?> poolKeys();
    }

    /** Per-publish pool number; the island's dense number when the island is one pool. */
    public final int id;
    /** Data of the junctions in the pool that carry data, by ascending junction index. */
    public final Object[] data;
    /** Whatever the router derives from {@link #data}, built once per pool and snapshot. */
    private volatile Object adapterView;

    PowerPool(int id, Object[] data) {
        this.id = id;
        this.data = data;
    }

    public Object getAdapterView() {
        return adapterView;
    }

    public void setAdapterView(Object view) {
        adapterView = view;
    }
}
