package logisticspipes.routing.astar;

import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import logisticspipes.request.resources.DictResource;
import logisticspipes.request.resources.FluidResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.request.resources.ItemResource;
import logisticspipes.routing.IRouter;
import logisticspipes.utils.item.ItemIdentifier;

/**
 * Global registry of which routers are interested in which items (providers, crafters, sinks). Same behaviour as the
 * static interest tables of the link-state router, owned by the junction router instead.
 */
public final class InterestRegistry {

    private static final Comparator<IRouter> BY_ID = Comparator.comparingInt(IRouter::getSimpleID);

    // things with specific interests -- providers (including crafters)
    private static final HashMap<ItemIdentifier, Set<IRouter>> globalSpecificInterests = new HashMap<>();
    // things potentially interested in every item (chassis with generic sinks)
    private static final Set<IRouter> genericInterests = new TreeSet<>(BY_ID);

    private InterestRegistry() {}

    public static void cleanup() {
        globalSpecificInterests.clear();
        genericInterests.clear();
    }

    static void addGeneric(IRouter router) {
        genericInterests.add(router);
    }

    static void removeGeneric(IRouter router) {
        genericInterests.remove(router);
    }

    static void add(ItemIdentifier item, IRouter router) {
        globalSpecificInterests.computeIfAbsent(item, k -> new TreeSet<>(BY_ID)).add(router);
    }

    static void remove(ItemIdentifier item, IRouter router) {
        Set<IRouter> interests = globalSpecificInterests.get(item);
        if (interests == null) {
            return;
        }
        interests.remove(router);
        if (interests.isEmpty()) {
            globalSpecificInterests.remove(item);
        }
    }

    public static Map<ItemIdentifier, Set<IRouter>> getInterestedInSpecifics() {
        return globalSpecificInterests;
    }

    public static Set<IRouter> getInterestedInGeneral() {
        return genericInterests;
    }

    private static void addAll(BitSet s, Set<IRouter> routers) {
        if (routers != null) {
            for (IRouter r : routers) {
                s.set(r.getSimpleID());
            }
        }
    }

    public static BitSet getRoutersInterestedIn(ItemIdentifier item) {
        BitSet s = new BitSet(RouterIds.getBiggestSimpleID() + 1);
        addAll(s, genericInterests);
        if (item == null) {
            return s;
        }
        addAll(s, globalSpecificInterests.get(item));
        addAll(s, globalSpecificInterests.get(item.getUndamaged()));
        addAll(s, globalSpecificInterests.get(item.getIgnoringNBT()));
        addAll(s, globalSpecificInterests.get(item.getUndamaged().getIgnoringNBT()));
        addAll(s, globalSpecificInterests.get(item.getIgnoringData()));
        addAll(s, globalSpecificInterests.get(item.getIgnoringData().getIgnoringNBT()));
        return s;
    }

    public static BitSet getRoutersInterestedIn(IResource item) {
        if (item instanceof ItemResource) {
            return getRoutersInterestedIn(((ItemResource) item).getItem());
        } else if (item instanceof FluidResource) {
            return getRoutersInterestedIn(((FluidResource) item).getFluid().getItemIdentifier());
        } else if (item instanceof DictResource) {
            DictResource dict = (DictResource) item;
            BitSet s = new BitSet(RouterIds.getBiggestSimpleID() + 1);
            addAll(s, genericInterests);
            for (Entry<ItemIdentifier, Set<IRouter>> entry : globalSpecificInterests.entrySet()) {
                if (dict.matches(entry.getKey(), IResource.MatchSettings.NORMAL)) {
                    addAll(s, entry.getValue());
                }
            }
            return s;
        }
        return new BitSet(RouterIds.getBiggestSimpleID() + 1);
    }
}
