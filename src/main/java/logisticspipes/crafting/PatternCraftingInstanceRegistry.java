package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.routing.order.LogisticsOrder;

/**
 * Reference index for live crafting instances.
 *
 * <p>
 * All lookups are constant-time and updates happen only when an order is created, restored, completed, or cancelled.
 * The bounded cancellation tombstones are used to reject deliveries that arrive after their instance was cancelled.
 * </p>
 */
public final class PatternCraftingInstanceRegistry {

    private static final int MAX_CANCELLED_INSTANCES = 4096;
    private static final Map<PatternCraftingReference, PatternCraftingOrder> ORDERS = new HashMap<>();
    private static final Map<UUID, Set<PatternCraftingReference>> INSTANCE_ORDERS = new HashMap<>();
    private static final LinkedHashMap<UUID, Boolean> CANCELLED_INSTANCES = new LinkedHashMap<>();

    private PatternCraftingInstanceRegistry() {}

    static synchronized void register(IOrderInfoProvider outputOrder, PatternCraftingOrder order) {
        if (outputOrder == null || order == null || order.reference() == null) {
            return;
        }
        if (outputOrder instanceof LogisticsOrder logisticsOrder) {
            logisticsOrder.setCraftingReference(order.reference());
        }
        ORDERS.put(order.reference(), order);
        INSTANCE_ORDERS.computeIfAbsent(order.reference().instanceId(), ignored -> new HashSet<>())
                .add(order.reference());
    }

    static synchronized PatternCraftingOrder find(IOrderInfoProvider outputOrder) {
        if (outputOrder instanceof LogisticsOrder logisticsOrder) {
            PatternCraftingOrder referenced = find(logisticsOrder.getCraftingReference());
            if (referenced != null) {
                return referenced;
            }
        }
        // Legacy live orders could have their registered reference overwritten by the delivery reference.
        // Preserve their tracking until they are saved and restored with a consistent reference.
        for (PatternCraftingOrder order : ORDERS.values()) {
            if (order.outputOrder == outputOrder) {
                return order;
            }
        }
        return null;
    }

    static synchronized boolean isTrackedOutputOrder(IOrderInfoProvider outputOrder) {
        return find(outputOrder) != null;
    }

    static synchronized PatternCraftingOrder find(PatternCraftingReference reference) {
        return reference == null ? null : ORDERS.get(reference);
    }

    static synchronized List<PatternCraftingOrder> ordersForInstance(UUID instanceId) {
        Set<PatternCraftingReference> references = INSTANCE_ORDERS.get(instanceId);
        if (references == null || references.isEmpty()) {
            return Collections.emptyList();
        }
        List<PatternCraftingOrder> result = new ArrayList<>(references.size());
        for (PatternCraftingReference reference : references) {
            PatternCraftingOrder order = ORDERS.get(reference);
            if (order != null) {
                result.add(order);
            }
        }
        return result;
    }

    static boolean cancelInstance(UUID instanceId) {
        List<PatternCraftingOrder> orders;
        synchronized (PatternCraftingInstanceRegistry.class) {
            markCancelled(instanceId);
            orders = ordersForInstance(instanceId);
        }
        boolean changed = false;
        for (PatternCraftingOrder order : orders) {
            changed |= order.module().cancelTrackedOrder(order);
        }
        return changed;
    }

    static synchronized boolean isCancelled(PatternCraftingReference reference) {
        return reference != null && CANCELLED_INSTANCES.containsKey(reference.instanceId());
    }

    static synchronized void recordCancellation(UUID instanceId) {
        if (instanceId != null) {
            markCancelled(instanceId);
        }
    }

    static synchronized void unregister(PatternCraftingOrder order) {
        if (order == null) {
            return;
        }
        PatternCraftingReference reference = order.reference();
        ORDERS.remove(reference);
        Set<PatternCraftingReference> references = INSTANCE_ORDERS.get(reference.instanceId());
        if (references != null) {
            references.remove(reference);
            if (references.isEmpty()) {
                INSTANCE_ORDERS.remove(reference.instanceId());
            }
        }
    }

    private static void markCancelled(UUID instanceId) {
        CANCELLED_INSTANCES.put(instanceId, Boolean.TRUE);
        while (CANCELLED_INSTANCES.size() > MAX_CANCELLED_INSTANCES) {
            UUID oldest = CANCELLED_INSTANCES.keySet().iterator().next();
            CANCELLED_INSTANCES.remove(oldest);
        }
    }

    static synchronized List<PatternCraftingOrder> liveOrders() {
        return new ArrayList<>(ORDERS.values());
    }

    public static synchronized void clear() {
        ORDERS.clear();
        INSTANCE_ORDERS.clear();
        CANCELLED_INSTANCES.clear();
    }
}
