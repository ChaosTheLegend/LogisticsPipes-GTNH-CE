package logisticspipes.crafting.v3.mc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import logisticspipes.crafting.v3.model.Pattern;
import logisticspipes.crafting.v3.model.ResourceKey;
import logisticspipes.crafting.v3.model.ResourceMatcher;
import logisticspipes.crafting.v3.plan.PatternRegistry;
import logisticspipes.crafting.v3.plan.StockView;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.routing.ExitRoute;
import logisticspipes.routing.IRouter;

/**
 * Everything on one network that matters to crafting: stock sources and crafters, collected from a router's reachable
 * routers. Built on the server thread; the planner then works on the snapshot it produces.
 */
public final class NetworkScan {

    final Map<UUID, List<ICraftingStockSource>> sources = new LinkedHashMap<>();
    final List<IV3Crafter> crafters = new ArrayList<>();

    private NetworkScan() {}

    public static NetworkScan from(UUID root) {
        NetworkScan scan = new NetworkScan();
        IRouter router = CraftingService.router(root);
        if (router == null) return scan;
        scan.add(router);
        for (ExitRoute route : router.getIRoutersByCost()) {
            if (route.destination != null && route.destination != router) scan.add(route.destination);
        }
        return scan;
    }

    private void add(IRouter router) {
        CoreRoutedPipe pipe = CraftingService.pipeOf(router);
        if (pipe == null) return;
        List<ICraftingStockSource> found = CraftingService.stockSourcesOf(pipe);
        if (!found.isEmpty()) sources.putIfAbsent(router.getId(), found);
        for (IV3Crafter crafter : CraftingService.craftersOf(pipe)) {
            if (!crafters.contains(crafter)) crafters.add(crafter);
        }
    }

    /** Stock minus crafting reservations, frozen at the time of the call. */
    public StockView stockSnapshot() {
        Map<UUID, Map<ResourceKey, Long>> frozen = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<ICraftingStockSource>> e : sources.entrySet()) {
            Map<ResourceKey, Long> items = new LinkedHashMap<>();
            for (ICraftingStockSource source : e.getValue()) source.listCraftingStock(items);
            Map<ResourceKey, Long> free = new LinkedHashMap<>();
            for (Map.Entry<ResourceKey, Long> item : items.entrySet()) {
                long available = item.getValue() - CraftingService.INSTANCE.reserved(e.getKey(), item.getKey());
                if (available > 0) free.put(item.getKey(), available);
            }
            if (!free.isEmpty()) frozen.put(e.getKey(), free);
        }
        return matcher -> {
            List<StockView.Entry> result = new ArrayList<>();
            for (Map.Entry<UUID, Map<ResourceKey, Long>> e : frozen.entrySet()) {
                if (matcher.isExact()) {
                    Long q = e.getValue().get(matcher.key());
                    if (q != null) result.add(new StockView.Entry(e.getKey(), matcher.key(), q));
                    continue;
                }
                for (Map.Entry<ResourceKey, Long> item : e.getValue().entrySet()) {
                    if (matcher.matches(item.getKey())) {
                        result.add(new StockView.Entry(e.getKey(), item.getKey(), item.getValue()));
                    }
                }
            }
            return result;
        };
    }

    /** Patterns of every crafter found, pattern pipes before legacy crafters. */
    public PatternRegistry registry() {
        Map<String, Pattern> patterns = new LinkedHashMap<>();
        Map<String, List<PatternRegistry.CrafterSlot>> crafterSlots = new HashMap<>();
        Map<ResourceKey, List<String>> byOutput = new HashMap<>();
        for (IV3Crafter crafter : crafters) {
            for (Map.Entry<Integer, Pattern> e : crafter.v3Patterns().entrySet()) {
                Pattern p = e.getValue();
                if (patterns.putIfAbsent(p.id(), p) == null) {
                    for (var out : p.outputs()) byOutput.computeIfAbsent(out.key(), k -> new ArrayList<>()).add(p.id());
                }
                crafterSlots.computeIfAbsent(p.id(), k -> new ArrayList<>())
                        .add(new PatternRegistry.CrafterSlot(crafter.crafterInfo(), e.getKey()));
            }
        }
        Comparator<PatternRegistry.CrafterSlot> byPriority = Comparator
                .comparingInt((PatternRegistry.CrafterSlot s) -> -s.crafter().priority());
        for (List<PatternRegistry.CrafterSlot> slots : crafterSlots.values()) slots.sort(byPriority);
        return matcher -> {
            List<String> ids = new ArrayList<>();
            if (matcher.isExact()) {
                ids.addAll(byOutput.getOrDefault(matcher.key(), Collections.emptyList()));
            } else {
                for (Map.Entry<ResourceKey, List<String>> e : byOutput.entrySet()) {
                    if (matcher.matches(e.getKey())) {
                        for (String id : e.getValue()) if (!ids.contains(id)) ids.add(id);
                    }
                }
            }
            List<PatternRegistry.Candidate> result = new ArrayList<>();
            for (String id : ids) result.add(new PatternRegistry.Candidate(patterns.get(id), crafterSlots.get(id)));
            result.sort(
                    Comparator.comparingInt(
                            (PatternRegistry.Candidate c) -> c.crafters().isEmpty() ? 0
                                    : -c.crafters().get(0).crafter().priority()));
            return result;
        };
    }

    /** Live stock matching {@code matcher}, minus reservations (driver sourcing). */
    List<StockView.Entry> liveStock(ResourceMatcher matcher) {
        List<StockView.Entry> result = new ArrayList<>();
        for (Map.Entry<UUID, List<ICraftingStockSource>> e : sources.entrySet()) {
            Map<ResourceKey, Long> items = new LinkedHashMap<>();
            if (matcher.isExact()) {
                long count = 0;
                for (ICraftingStockSource s : e.getValue())
                    count = Math.max(count, s.craftingStockCount(matcher.key()));
                if (count > 0) items.put(matcher.key(), count);
            } else {
                for (ICraftingStockSource s : e.getValue()) s.listCraftingStock(items);
            }
            for (Map.Entry<ResourceKey, Long> item : items.entrySet()) {
                if (!matcher.matches(item.getKey())) continue;
                long free = item.getValue() - CraftingService.INSTANCE.reserved(e.getKey(), item.getKey());
                if (free > 0) result.add(new StockView.Entry(e.getKey(), item.getKey(), free));
            }
        }
        return result;
    }

    public List<IV3Crafter> crafters() {
        return crafters;
    }
}
