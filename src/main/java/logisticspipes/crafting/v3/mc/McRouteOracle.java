package logisticspipes.crafting.v3.mc;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import logisticspipes.crafting.v3.model.ResourceKey;
import logisticspipes.crafting.v3.plan.RouteOracle;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.routing.IRouter;
import logisticspipes.utils.item.ItemIdentifier;

/**
 * Reachability through the junction router: {@link IRouter#hasRoute} with the item, so one-way pipes, filters and
 * firewalls count. Answers are cached until the network graph changes (or for one planning run).
 */
public final class McRouteOracle implements RouteOracle {

    private final Map<Object, Boolean> cache = new HashMap<>();
    private long generation = Long.MIN_VALUE;

    @Override
    public boolean canReach(UUID from, UUID to, ResourceKey key) {
        if (from.equals(to)) return true;
        long gen = CraftingService.graphGeneration();
        if (gen != generation) {
            cache.clear();
            generation = gen;
        }
        Object cacheKey = new Triple(from, to, key);
        Boolean cached = cache.get(cacheKey);
        if (cached != null) return cached;
        boolean result = compute(from, to, key);
        if (cache.size() > 50_000) cache.clear();
        cache.put(cacheKey, result);
        return result;
    }

    private static boolean compute(UUID from, UUID to, ResourceKey key) {
        IRouter source = CraftingService.router(from);
        int destination = SimpleServiceLocator.routerManager.getIDforUUID(to);
        if (source == null || destination < 0) return false;
        if (!isLoadedRouter(source) || !isLoadedRouter(CraftingService.router(to))) return false;
        ItemIdentifier item = routingItem(key);
        return item != null && source.hasRoute(destination, true, item);
    }

    @Override
    public boolean isLoaded(UUID location) {
        return isLoadedRouter(CraftingService.router(location));
    }

    private static boolean isLoadedRouter(IRouter router) {
        return router != null && CraftingService.pipeOf(router) != null;
    }

    static ItemIdentifier routingItem(ResourceKey key) {
        if (key instanceof ItemKey) return ((ItemKey) key).id;
        if (key instanceof FluidKey) return ((FluidKey) key).routingItem();
        return null;
    }

    private static final class Triple {

        final UUID from;
        final UUID to;
        final ResourceKey key;

        Triple(UUID from, UUID to, ResourceKey key) {
            this.from = from;
            this.to = to;
            this.key = key;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Triple)) return false;
            Triple t = (Triple) o;
            return from.equals(t.from) && to.equals(t.to) && key.equals(t.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(from, to, key);
        }
    }
}
