package logisticspipes.crafting.v3.exec;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import logisticspipes.crafting.v3.model.ResourceKey;

/**
 * {@code (provider, key) -> reserved amount} across all jobs, so provider availability is {@code physical - reserved}
 * in O(1). Kept in step with RESERVED records by {@link Ledger}; read from any request path that hands out provider
 * stock.
 */
public final class ReservationIndex {

    private final Map<UUID, Map<ResourceKey, Long>> reserved = new HashMap<>();

    public long reserved(UUID location, ResourceKey key) {
        Map<ResourceKey, Long> byKey = reserved.get(location);
        if (byKey == null) return 0;
        Long v = byKey.get(key);
        return v == null ? 0 : v;
    }

    public Map<ResourceKey, Long> reservedAt(UUID location) {
        Map<ResourceKey, Long> byKey = reserved.get(location);
        return byKey == null ? Collections.emptyMap() : Collections.unmodifiableMap(byKey);
    }

    void add(UUID location, ResourceKey key, long qty) {
        if (qty == 0) return;
        Map<ResourceKey, Long> byKey = reserved.computeIfAbsent(location, k -> new HashMap<>());
        long next = byKey.getOrDefault(key, 0L) + qty;
        if (next < 0) throw new IllegalStateException("negative reservation for " + key + " at " + location);
        if (next == 0) {
            byKey.remove(key);
            if (byKey.isEmpty()) reserved.remove(location);
        } else {
            byKey.put(key, next);
        }
    }

    void clear() {
        reserved.clear();
    }

    Map<UUID, Map<ResourceKey, Long>> snapshot() {
        Map<UUID, Map<ResourceKey, Long>> copy = new HashMap<>();
        reserved.forEach((l, m) -> copy.put(l, new HashMap<>(m)));
        return copy;
    }
}
