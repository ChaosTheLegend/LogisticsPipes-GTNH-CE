package logisticspipes.routing.astar;

import java.util.BitSet;
import java.util.Comparator;
import java.util.EnumMap;
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
    // things interested in the items with a certain value of a category -- sinks by mod, fluid holding items, ...
    private static final EnumMap<ItemCategory, Map<Object, Set<IRouter>>> globalCategoryInterests = new EnumMap<>(
            ItemCategory.class);

    private InterestRegistry() {}

    public static void cleanup() {
        globalSpecificInterests.clear();
        genericInterests.clear();
        globalCategoryInterests.clear();
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

    static void addCategory(ItemCategory category, Object value, IRouter router) {
        globalCategoryInterests.computeIfAbsent(category, k -> new HashMap<>())
                .computeIfAbsent(value, k -> new TreeSet<>(BY_ID)).add(router);
    }

    static void removeCategory(ItemCategory category, Object value, IRouter router) {
        Map<Object, Set<IRouter>> byValue = globalCategoryInterests.get(category);
        if (byValue == null) {
            return;
        }
        Set<IRouter> interests = byValue.get(value);
        if (interests == null) {
            return;
        }
        interests.remove(router);
        if (interests.isEmpty()) {
            byValue.remove(value);
            if (byValue.isEmpty()) {
                globalCategoryInterests.remove(category);
            }
        }
    }

    public static Map<ItemIdentifier, Set<IRouter>> getInterestedInSpecifics() {
        return globalSpecificInterests;
    }

    public static Set<IRouter> getInterestedInGeneral() {
        return genericInterests;
    }

    public static Map<ItemCategory, Map<Object, Set<IRouter>>> getInterestedInCategories() {
        return globalCategoryInterests;
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
        // only the categories somebody is interested in are looked at, an item is asked for its values once
        for (Map.Entry<ItemCategory, Map<Object, Set<IRouter>>> entry : globalCategoryInterests.entrySet()) {
            Map<Object, Set<IRouter>> byValue = entry.getValue();
            for (Object value : item.getCategoryKeys(entry.getKey())) {
                addAll(s, byValue.get(value));
            }
        }
        return s;
    }

    public static BitSet getRoutersInterestedIn(IResource item) {
        if (item instanceof ItemResource) {
            return getRoutersInterestedIn(((ItemResource) item).getItem());
        } else if (item instanceof FluidResource) {
            return getRoutersInterestedIn(((FluidResource) item).getFluid().getItemIdentifier());
        } else if (item instanceof DictResource) {
            // no categories: they are declared by sinks, and this is asked for the providers of a request
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
