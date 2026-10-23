package logisticspipes.crafting.v3.exec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import logisticspipes.crafting.v3.model.ResourceKey;

/**
 * Every ledger record of every job, with the indexes the driver needs. All changes go through {@link #create},
 * {@link #mutate}, {@link #split} and {@link #remove}, which keep the indexes and the {@link ReservationIndex} in step.
 */
public final class Ledger {

    private final Map<Long, LedgerRecord> byId = new LinkedHashMap<>();
    private final Map<Long, Set<LedgerRecord>> byTicket = new HashMap<>();
    private final Map<Long, Set<LedgerRecord>> byJob = new HashMap<>();
    private final Map<Long, Set<LedgerRecord>> byShipment = new HashMap<>();
    /** job -> node -> EXPECTED records. */
    private final Map<Long, Map<Integer, Set<LedgerRecord>>> expected = new HashMap<>();
    private final Map<Long, Set<LedgerRecord>> unboundByJob = new HashMap<>();
    private final Set<LedgerRecord> unsourced = new LinkedHashSet<>();
    private final ReservationIndex reservations = new ReservationIndex();
    private final IdSource ids;

    public Ledger(IdSource ids) {
        this.ids = ids;
    }

    public interface IdSource {

        long next();
    }

    public ReservationIndex reservations() {
        return reservations;
    }

    public LedgerRecord get(long id) {
        return byId.get(id);
    }

    public Collection<LedgerRecord> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public int size() {
        return byId.size();
    }

    public List<LedgerRecord> forTicket(long ticket) {
        return copy(byTicket.get(ticket));
    }

    public List<LedgerRecord> forJob(long job) {
        return copy(byJob.get(job));
    }

    public List<LedgerRecord> forShipment(long shipment) {
        return copy(byShipment.get(shipment));
    }

    public List<LedgerRecord> expectedFrom(long job, int node) {
        Map<Integer, Set<LedgerRecord>> byNode = expected.get(job);
        return copy(byNode == null ? null : byNode.get(node));
    }

    public List<LedgerRecord> unbound(long job) {
        return copy(unboundByJob.get(job));
    }

    public List<LedgerRecord> unsourced() {
        return new ArrayList<>(unsourced);
    }

    public boolean hasShipment(long shipment) {
        Set<LedgerRecord> set = byShipment.get(shipment);
        return set != null && !set.isEmpty();
    }

    private static List<LedgerRecord> copy(Set<LedgerRecord> set) {
        return set == null ? Collections.emptyList() : new ArrayList<>(set);
    }

    // ------------------------------------------------------------------ mutation

    public LedgerRecord create(long job, ResourceKey key, long qty, RecordState state, Consumer<LedgerRecord> init) {
        if (qty <= 0) throw new IllegalArgumentException("record quantity must be positive: " + qty);
        LedgerRecord r = new LedgerRecord(ids.next(), job, key, qty, state);
        if (init != null) init.accept(r);
        index(r);
        return r;
    }

    /** Restores a saved record with its original id. */
    LedgerRecord restore(LedgerRecord r) {
        index(r);
        return r;
    }

    public void mutate(LedgerRecord r, Consumer<LedgerRecord> change) {
        unindex(r);
        change.accept(r);
        if (r.qty <= 0) throw new IllegalStateException("record quantity went to " + r.qty + ": " + r);
        index(r);
    }

    /**
     * Splits {@code qty} off {@code r} into a new record with the same fields, or returns {@code r} itself when
     * {@code qty} covers all of it.
     */
    public LedgerRecord split(LedgerRecord r, long qty) {
        if (qty >= r.qty) return r;
        if (qty <= 0) throw new IllegalArgumentException("split quantity must be positive: " + qty);
        mutate(r, x -> x.qty -= qty);
        return create(r.job, r.key, qty, r.state, n -> {
            n.location = r.location;
            n.ticket = r.ticket;
            n.need = r.need;
            n.producedBy = r.producedBy;
            n.shipment = r.shipment;
            n.cause = r.cause;
            n.nextRetry = r.nextRetry;
            n.retryDelay = r.retryDelay;
        });
    }

    public void remove(LedgerRecord r) {
        if (byId.containsKey(r.id)) unindex(r);
    }

    public void removeJob(long job) {
        for (LedgerRecord r : forJob(job)) remove(r);
        expected.remove(job);
        unboundByJob.remove(job);
    }

    void clear() {
        byId.clear();
        byTicket.clear();
        byJob.clear();
        byShipment.clear();
        expected.clear();
        unboundByJob.clear();
        unsourced.clear();
        reservations.clear();
    }

    private void index(LedgerRecord r) {
        byId.put(r.id, r);
        byJob.computeIfAbsent(r.job, k -> new LinkedHashSet<>()).add(r);
        if (r.ticket != LedgerRecord.UNBOUND) {
            byTicket.computeIfAbsent(r.ticket, k -> new LinkedHashSet<>()).add(r);
        } else {
            unboundByJob.computeIfAbsent(r.job, k -> new LinkedHashSet<>()).add(r);
        }
        if (r.shipment >= 0) byShipment.computeIfAbsent(r.shipment, k -> new LinkedHashSet<>()).add(r);
        if (r.state == RecordState.EXPECTED) {
            expected.computeIfAbsent(r.job, k -> new HashMap<>())
                    .computeIfAbsent(r.producedBy, k -> new LinkedHashSet<>()).add(r);
        }
        if (r.state == RecordState.UNSOURCED) unsourced.add(r);
        if (r.state == RecordState.RESERVED) reservations.add(r.location, r.key, r.qty);
    }

    private void unindex(LedgerRecord r) {
        byId.remove(r.id);
        removeFrom(byJob, r.job, r);
        if (r.ticket != LedgerRecord.UNBOUND) {
            removeFrom(byTicket, r.ticket, r);
        } else {
            removeFrom(unboundByJob, r.job, r);
        }
        if (r.shipment >= 0) removeFrom(byShipment, r.shipment, r);
        if (r.state == RecordState.EXPECTED) {
            Map<Integer, Set<LedgerRecord>> byNode = expected.get(r.job);
            if (byNode != null) {
                removeFrom(byNode, r.producedBy, r);
                if (byNode.isEmpty()) expected.remove(r.job);
            }
        }
        if (r.state == RecordState.UNSOURCED) unsourced.remove(r);
        if (r.state == RecordState.RESERVED) reservations.add(r.location, r.key, -r.qty);
    }

    private static <K> void removeFrom(Map<K, Set<LedgerRecord>> map, K key, LedgerRecord r) {
        Set<LedgerRecord> set = map.get(key);
        if (set == null) return;
        set.remove(r);
        if (set.isEmpty()) map.remove(key);
    }

    // ------------------------------------------------------------------ queries

    public long sumBound(long ticket, int need) {
        long sum = 0;
        Set<LedgerRecord> set = byTicket.get(ticket);
        if (set == null) return 0;
        for (LedgerRecord r : set) {
            if (r.need == need) sum += r.qty;
        }
        return sum;
    }

    /** Reserved amount the RESERVED records add up to, recomputed from scratch (invariant checks). */
    Map<UUID, Map<ResourceKey, Long>> recomputeReservations() {
        Map<UUID, Map<ResourceKey, Long>> result = new HashMap<>();
        for (LedgerRecord r : byId.values()) {
            if (r.state == RecordState.RESERVED) {
                result.computeIfAbsent(r.location, k -> new HashMap<>()).merge(r.key, r.qty, Long::sum);
            }
        }
        return result;
    }
}
