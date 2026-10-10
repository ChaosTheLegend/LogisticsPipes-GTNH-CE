package logisticspipes.routing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import logisticspipes.utils.item.ItemIdentifier;

/**
 * Items routed to a pipe that have not arrived yet. Adding, removing and counting by item are O(1) / O(items of that
 * kind) instead of scans over everything in transit, which a busy destination with thousands of items on the way hit on
 * every send, arrival and sink check.
 * <p>
 * Entries are identity based, like the queue this replaces ({@link ItemRoutingInformation} keeps Object equality).
 */
public final class InTransitTracker implements Iterable<ItemRoutingInformation> {

    private final Set<ItemRoutingInformation> all = new LinkedHashSet<>();
    /** The item each entry was indexed under, which stays its key even if the entry's stack is replaced. */
    private final Map<ItemRoutingInformation, ItemIdentifier> keys = new IdentityHashMap<>();
    private final Map<ItemIdentifier, Set<ItemRoutingInformation>> byItem = new HashMap<>();

    public synchronized boolean add(ItemRoutingInformation info) {
        if (!all.add(info)) {
            return false;
        }
        ItemIdentifier key = info.getItem().getItem();
        keys.put(info, key);
        byItem.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(info);
        return true;
    }

    public synchronized boolean contains(ItemRoutingInformation info) {
        return all.contains(info);
    }

    public synchronized boolean remove(ItemRoutingInformation info) {
        if (!all.remove(info)) {
            return false;
        }
        ItemIdentifier key = keys.remove(info);
        Set<ItemRoutingInformation> set = byItem.get(key);
        if (set != null) {
            set.remove(info);
            if (set.isEmpty()) {
                byItem.remove(key);
            }
        }
        return true;
    }

    /** Total stack size in transit of {@code item}. */
    public synchronized int count(ItemIdentifier item) {
        Set<ItemRoutingInformation> set = byItem.get(item);
        if (set == null) {
            return 0;
        }
        int count = 0;
        for (ItemRoutingInformation info : set) {
            count += info.getItem().getStackSize();
        }
        return count;
    }

    /** Drop every entry whose delay ran out, handing each to {@code onTimeout}. */
    public void removeTimedOut(Consumer<ItemRoutingInformation> onTimeout) {
        List<ItemRoutingInformation> timedOut = null;
        synchronized (this) {
            for (ItemRoutingInformation info : all) {
                if (info.getTickToTimeOut() <= 0) {
                    if (timedOut == null) {
                        timedOut = new ArrayList<>();
                    }
                    timedOut.add(info);
                }
            }
            if (timedOut != null) {
                for (ItemRoutingInformation info : timedOut) {
                    remove(info);
                }
            }
        }
        if (timedOut != null) {
            timedOut.forEach(onTimeout);
        }
    }

    public synchronized int size() {
        return all.size();
    }

    /** Iterates a copy, so callers may send, receive or reroute items while iterating. */
    @Override
    public synchronized Iterator<ItemRoutingInformation> iterator() {
        return new ArrayList<>(all).iterator();
    }
}
