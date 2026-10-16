package logisticspipes.blocks.powertile;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import logisticspipes.config.Configs;

/**
 * The energy of one power pool (see {@link logisticspipes.routing.astar.PowerPool}). Power junctions don't store power
 * themselves: each one adds its capacity ({@link LogisticsPowerJunctionTileEntity#MAX_STORAGE}) to the pool it is
 * connected to, and pipes draw from the pool directly.
 * <p>
 * The graph decides which junctions share a pool; {@link #bind} follows it. When the members change (islands merge or
 * split, a junction is added or removed), every junction brings its share of its old pool's energy, in proportion to
 * capacity, so energy is neither lost nor created. A junction that leaves (broken, chunk unloaded) takes its share with
 * it and saves it in its NBT.
 * <p>
 * Every method synchronizes on one lock: pools are rebound by whichever thread first sees a new graph snapshot.
 */
public final class PowerPoolStorage {

    /** Something that stores into a pool: a power junction. */
    public interface Member {

        /** The member's current pool, creating a pool of its own if it has none. */
        PowerPoolStorage pool();

        void setPool(PowerPoolStorage pool);

        /** What the member adds to its pool's capacity. */
        long poolCapacity();
    }

    private static final Object LOCK = new Object();

    private final Set<Member> members = new LinkedHashSet<>();
    private long stored;
    private long capacity;
    /** Turns off at full, on again below half: drives the "needs more power" gate trigger. */
    private boolean needMorePower = true;

    /** A pool of just this junction, holding the energy it was saved with. */
    public static PowerPoolStorage single(Member junction, long energy) {
        PowerPoolStorage pool = new PowerPoolStorage();
        pool.members.add(junction);
        pool.capacity = junction.poolCapacity();
        pool.stored = Math.max(0, Math.min(energy, pool.capacity));
        pool.updateNeed();
        return pool;
    }

    /**
     * The pool shared by exactly {@code junctions}, built from their current pools if the members changed. Returns
     * {@code null} for no junctions.
     */
    public static PowerPoolStorage bind(Collection<? extends Member> junctions) {
        if (junctions.isEmpty()) {
            return null;
        }
        synchronized (LOCK) {
            PowerPoolStorage current = junctions.iterator().next().pool();
            if (current.members.size() == junctions.size() && current.members.containsAll(junctions)) {
                return current;
            }
            PowerPoolStorage pool = new PowerPoolStorage();
            for (Member junction : junctions) {
                pool.stored += junction.pool().detach(junction);
                pool.capacity += junction.poolCapacity();
                pool.members.add(junction);
                junction.setPool(pool);
            }
            pool.updateNeed();
            return pool;
        }
    }

    /** Remove the junction and its capacity; returns the energy it takes along. */
    public long detach(Member junction) {
        synchronized (LOCK) {
            if (!members.remove(junction)) {
                return 0;
            }
            long share = members.isEmpty() ? stored : shareOf(junction.poolCapacity());
            stored -= share;
            capacity -= junction.poolCapacity();
            updateNeed();
            return share;
        }
    }

    /** The energy a junction would take along if it left now (saved in its NBT). */
    public long shareOf(Member junction) {
        synchronized (LOCK) {
            if (!members.contains(junction)) {
                return 0;
            }
            return members.size() == 1 ? stored : shareOf(junction.poolCapacity());
        }
    }

    private long shareOf(long memberCapacity) {
        return capacity <= 0 ? 0 : stored * memberCapacity / capacity;
    }

    private static long cost(int amount) {
        return (long) ((amount * Configs.POWER_USAGE_MULTIPLIER) + 0.5D);
    }

    public boolean canUseEnergy(int amount) {
        synchronized (LOCK) {
            return stored >= cost(amount);
        }
    }

    public boolean useEnergy(int amount) {
        synchronized (LOCK) {
            long cost = cost(amount);
            if (stored < cost) {
                return false;
            }
            stored -= cost;
            updateNeed();
            return true;
        }
    }

    /** Add up to {@code amount}; returns how much fit. */
    public long addEnergy(long amount) {
        synchronized (LOCK) {
            long added = Math.max(0, Math.min(amount, capacity - stored));
            stored += added;
            updateNeed();
            return added;
        }
    }

    public long getStored() {
        synchronized (LOCK) {
            return stored;
        }
    }

    public long getCapacity() {
        synchronized (LOCK) {
            return capacity;
        }
    }

    public long freeSpace() {
        synchronized (LOCK) {
            return capacity - stored;
        }
    }

    public boolean needsMorePower() {
        synchronized (LOCK) {
            return needMorePower;
        }
    }

    public Set<Member> members() {
        synchronized (LOCK) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(members));
        }
    }

    private void updateNeed() {
        if (stored >= capacity) {
            needMorePower = false;
        } else if (stored < capacity / 2) {
            needMorePower = true;
        }
    }
}
