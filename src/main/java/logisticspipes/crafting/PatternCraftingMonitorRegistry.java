package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.routing.ExitRoute;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.routing.order.LinkedLogisticsOrderList;

/** Builds the request-table monitor view from the stable crafting reference index. */
public final class PatternCraftingMonitorRegistry {

    private PatternCraftingMonitorRegistry() {}

    static PatternCraftingOrder find(IOrderInfoProvider outputOrder) {
        return PatternCraftingInstanceRegistry.find(outputOrder);
    }

    public static void clear() {
        PatternCraftingInstanceRegistry.clear();
    }

    public static List<PatternCraftingMonitorNode> build(LinkedLogisticsOrderList orders) {
        if (orders == null) {
            return Collections.emptyList();
        }
        cleanupFinishedOrders();
        List<PatternCraftingMonitorNode> result = new ArrayList<>();
        appendMonitorNodes(orders, result);
        return result;
    }

    /**
     * Builds one cancellable entry per live crafting instance reachable from the supplied network router.
     */
    public static List<PatternCraftingMonitorEntry> buildAll(IRouter networkRouter) {
        if (networkRouter == null) {
            return Collections.emptyList();
        }
        cleanupFinishedOrders();
        Map<UUID, List<PatternCraftingOrder>> ordersByInstance = new LinkedHashMap<>();
        for (PatternCraftingOrder order : PatternCraftingInstanceRegistry.liveOrders()) {
            if (order.outputOrder == null || !belongsToNetwork(order, networkRouter)) {
                continue;
            }
            ordersByInstance.computeIfAbsent(order.reference().instanceId(), ignored -> new ArrayList<>()).add(order);
        }

        Map<UUID, PatternCraftingMonitorEntry> entriesByInstance = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<PatternCraftingOrder>> instance : ordersByInstance.entrySet()) {
            Set<PatternCraftingOrder> nestedOrders = new HashSet<>();
            for (PatternCraftingOrder order : instance.getValue()) {
                order.collectNestedCraftingOrders(nestedOrders);
            }
            List<PatternCraftingMonitorNode> roots = new ArrayList<>();
            for (PatternCraftingOrder order : instance.getValue()) {
                if (!nestedOrders.contains(order)) {
                    PatternCraftingMonitorNode node = order.toMonitorNode(new HashSet<>());
                    if (node.hasVisibleWork()) {
                        roots.add(node);
                    }
                }
            }
            if (roots.isEmpty() && !instance.getValue().isEmpty()) {
                PatternCraftingMonitorNode fallback = instance.getValue().get(0).toMonitorNode(new HashSet<>());
                if (fallback.hasVisibleWork()) {
                    roots.add(fallback);
                }
            }
            if (!roots.isEmpty()) {
                entriesByInstance.put(instance.getKey(), new PatternCraftingMonitorEntry(instance.getKey(), roots));
            }
        }
        List<ModulePatternCrafting> modules = networkPatternModules(networkRouter);
        for (ModulePatternCrafting module : modules) {
            for (PatternCraftingMonitorEntry standalone : module.getStandaloneOrderEntries()) {
                mergeEntry(entriesByInstance, standalone);
            }
        }
        for (ModulePatternCrafting module : modules) {
            for (PatternCraftingMonitorEntry pending : module.getPendingRestoreEntries()) {
                mergeEntry(entriesByInstance, pending);
            }
        }
        List<PatternCraftingMonitorEntry> result = new ArrayList<>(entriesByInstance.values());
        result.sort(
                Comparator.comparing(PatternCraftingMonitorRegistry::displayName)
                        .thenComparing(entry -> entry.getInstanceId().toString()));
        return result;
    }

    /** Cancels a crafting instance only when it belongs to the supplied logistics network. */
    public static boolean cancelInstance(UUID instanceId, IRouter networkRouter) {
        if (instanceId == null || networkRouter == null) {
            return false;
        }
        boolean foundInNetwork = false;
        for (PatternCraftingOrder order : PatternCraftingInstanceRegistry.ordersForInstance(instanceId)) {
            if (belongsToNetwork(order, networkRouter)) {
                foundInNetwork = true;
                break;
            }
        }
        List<ModulePatternCrafting> modules = networkPatternModules(networkRouter);
        boolean pendingInNetwork = false;
        boolean standaloneInNetwork = false;
        for (ModulePatternCrafting module : modules) {
            pendingInNetwork |= module.hasPendingRestoreInstance(instanceId);
            standaloneInNetwork |= module.hasStandaloneOrderInstance(instanceId);
        }
        if (!foundInNetwork && !pendingInNetwork && !standaloneInNetwork) {
            return false;
        }

        PatternCraftingInstanceRegistry.recordCancellation(instanceId);
        boolean changed = foundInNetwork && PatternCraftingInstanceRegistry.cancelInstance(instanceId);
        for (ModulePatternCrafting module : modules) {
            changed |= module.cancelPendingRestore(instanceId);
            changed |= module.cancelStandaloneOrderInstance(instanceId);
        }
        return changed;
    }

    private static void mergeEntry(Map<UUID, PatternCraftingMonitorEntry> entriesByInstance,
            PatternCraftingMonitorEntry addition) {
        PatternCraftingMonitorEntry existing = entriesByInstance.get(addition.getInstanceId());
        if (existing == null) {
            entriesByInstance.put(addition.getInstanceId(), addition);
            return;
        }
        List<PatternCraftingMonitorNode> roots = new ArrayList<>(existing.getRoots());
        roots.addAll(addition.getRoots());
        if (existing.isRestoring() || addition.isRestoring()) {
            entriesByInstance.put(
                    addition.getInstanceId(),
                    PatternCraftingMonitorEntry.restoring(
                            addition.getInstanceId(),
                            roots,
                            Math.max(existing.getRestoreAttempts(), addition.getRestoreAttempts()),
                            Math.max(existing.getMaxRestoreAttempts(), addition.getMaxRestoreAttempts())));
            return;
        }
        entriesByInstance
                .put(addition.getInstanceId(), new PatternCraftingMonitorEntry(addition.getInstanceId(), roots));
    }

    private static void appendMonitorNodes(LinkedLogisticsOrderList orders, List<PatternCraftingMonitorNode> result) {
        for (IOrderInfoProvider order : orders) {
            PatternCraftingOrder stagedOrder = find(order);
            if (stagedOrder == null) {
                continue;
            }
            PatternCraftingMonitorNode node = stagedOrder.toMonitorNode(new HashSet<>());
            if (node.hasVisibleWork()) {
                result.add(node);
            }
        }
        for (LinkedLogisticsOrderList subOrder : orders.getSubOrders()) {
            appendMonitorNodes(subOrder, result);
        }
    }

    private static void cleanupFinishedOrders() {
        for (PatternCraftingOrder order : PatternCraftingInstanceRegistry.liveOrders()) {
            IOrderInfoProvider output = order.outputOrder;
            if (output == null || output.isFinished() && output.getProgresses().isEmpty() && order.isFullyRequested()) {
                PatternCraftingInstanceRegistry.unregister(order);
            }
        }
    }

    private static boolean belongsToNetwork(PatternCraftingOrder order, IRouter networkRouter) {
        IRouter craftingRouter = order.module().getRouter();
        return craftingRouter != null
                && (craftingRouter == networkRouter || craftingRouter.getSimpleID() == networkRouter.getSimpleID()
                        || !networkRouter.getDistanceTo(craftingRouter).isEmpty());
    }

    private static List<ModulePatternCrafting> networkPatternModules(IRouter networkRouter) {
        List<ModulePatternCrafting> result = new ArrayList<>();
        Set<LogisticsModule> seen = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        appendPatternModule(networkRouter, seen, result);
        for (ExitRoute route : networkRouter.getIRoutersByCost()) {
            appendPatternModule(route.destination, seen, result);
        }
        return result;
    }

    private static void appendPatternModule(IRouter router, Set<LogisticsModule> seen,
            List<ModulePatternCrafting> result) {
        if (router == null) {
            return;
        }
        LogisticsModule module = router.getLogisticsModule();
        if (module instanceof ModulePatternCrafting patternModule && seen.add(module)) {
            result.add(patternModule);
        }
    }

    private static String displayName(PatternCraftingMonitorEntry entry) {
        return entry.getDisplayStack() == null ? "" : entry.getDisplayStack().getItem().getFriendlyName();
    }
}
