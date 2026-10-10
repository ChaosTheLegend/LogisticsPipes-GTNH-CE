package logisticspipes.routing.astar;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lazy, per-pair route cache over the junction graph.
 * <p>
 * Query path for {@link #findRoute}:
 * <ol>
 * <li>union-find check: different components are unreachable, no search;</li>
 * <li>cache lookup by {@link PairKey}, validated against the current snapshot (see {@link ValidatedRoutes});</li>
 * <li>on a stale entry that can still be travelled, and with a background executor configured: return it and refresh it
 * in the background;</li>
 * <li>otherwise single-flight: exactly one thread runs the search for a pair, concurrent callers wait for its
 * result.</li>
 * </ol>
 * The caller's thread runs the search when there is no usable entry. The {@code IRouter} API is synchronous and callers
 * (item routing in particular) cannot act on "pending", so returning nothing would drop or bounce items; a single A*
 * over the pruned junction graph is cheap enough to run inline. The expensive whole-network work of the old router
 * (every router recomputing a full table after every edit) no longer exists.
 */
public final class JunctionRoutingEngine {

    private static final int MAX_CACHED_PAIRS = 1 << 20;

    private final JunctionGraphWriter writer;
    private final ConcurrentHashMap<PairKey, RouteCacheEntry> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<PairKey, CompletableFuture<RouteCacheEntry>> inFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<JunctionId, SweepEntry> sweeps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<JunctionId, CompletableFuture<SweepEntry>> sweepsInFlight = new ConcurrentHashMap<>();
    private volatile ExecutorService executor;

    private final AtomicLong pairSearches = new AtomicLong();
    private final AtomicLong multiSearches = new AtomicLong();
    private final AtomicLong sweepSearches = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong staleServed = new AtomicLong();
    private final AtomicLong searchNanos = new AtomicLong();

    public JunctionRoutingEngine(JunctionGraphWriter writer) {
        this.writer = writer;
        writer.setPublishListener(this::onPublish);
    }

    public JunctionGraphWriter writer() {
        return writer;
    }

    public NetworkGraph graph() {
        return writer.graph();
    }

    /** Background refreshes of stale routes run here; {@code null} computes everything on the calling thread. */
    public void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    // ------------------------------------------------------------------ one-to-one

    /** Quick point-to-point route: the core search with the Manhattan heuristic and one target. */
    public RouteCacheEntry findRoute(JunctionId source, JunctionId dest) {
        NetworkGraph graph = writer.graph();
        PairKey key = new PairKey(source, dest);
        if (!graph.sameComponent(source, dest)) {
            return new RouteCacheEntry(graph, key, Collections.emptyList());
        }
        RouteCacheEntry entry = cache.get(key);
        if (entry != null) {
            if (entry.isValid(graph)) {
                cacheHits.incrementAndGet();
                return entry;
            }
            if (executor != null && entry.isTraversable(graph)) {
                staleServed.incrementAndGet();
                refreshInBackground(key);
                return entry;
            }
        }
        return singleFlight(inFlight, key, () -> {
            NetworkGraph g = writer.graph();
            RouteCacheEntry cached = cache.get(key);
            if (cached != null && cached.isValid(g)) {
                return cached;
            }
            RouteCacheEntry fresh = computePair(g, key);
            cache.put(key, fresh);
            return fresh;
        });
    }

    private RouteCacheEntry computePair(NetworkGraph g, PairKey key) {
        long start = System.nanoTime();
        // Manhattan heuristic: see NetworkGraph#manhattanHeuristic for why it is scaled and when that must change.
        SearchResult r = JunctionSearch.search(g, key.source, Collections.singleton(key.dest), g.manhattanHeuristic());
        pairSearches.incrementAndGet();
        searchNanos.addAndGet(System.nanoTime() - start);
        return new RouteCacheEntry(g, key, r.routesTo(key.dest));
    }

    private void refreshInBackground(PairKey key) {
        ExecutorService ex = executor;
        if (ex == null) {
            return;
        }
        CompletableFuture<RouteCacheEntry> mine = new CompletableFuture<>();
        if (inFlight.putIfAbsent(key, mine) != null) {
            return; // someone is already computing this pair
        }
        try {
            ex.execute(() -> {
                try {
                    NetworkGraph g = writer.graph();
                    RouteCacheEntry cached = cache.get(key);
                    RouteCacheEntry result = cached != null && cached.isValid(g) ? cached : computePair(g, key);
                    cache.put(key, result);
                    mine.complete(result);
                } catch (Throwable t) {
                    mine.completeExceptionally(t);
                } finally {
                    inFlight.remove(key, mine);
                }
            });
        } catch (RejectedExecutionException e) {
            inFlight.remove(key, mine);
            mine.cancel(false);
        }
    }

    // ------------------------------------------------------------------ one-to-many

    /**
     * Routes from one source to a set of targets (crafting-tree resolution): targets with a valid cached pair entry are
     * answered from the cache, all others by a single multi-target run of the core search whose results are stored as
     * ordinary pair entries.
     */
    public Map<JunctionId, RouteCacheEntry> findRoutesToTargets(JunctionId source, Set<JunctionId> targets) {
        NetworkGraph graph = writer.graph();
        Map<JunctionId, RouteCacheEntry> result = new LinkedHashMap<>();
        Set<JunctionId> missing = new HashSet<>();
        for (JunctionId t : targets) {
            PairKey key = new PairKey(source, t);
            if (!graph.sameComponent(source, t)) {
                result.put(t, new RouteCacheEntry(graph, key, Collections.emptyList()));
                continue;
            }
            RouteCacheEntry entry = cache.get(key);
            if (entry != null && entry.isValid(graph)) {
                cacheHits.incrementAndGet();
                result.put(t, entry);
            } else if (entry != null && executor != null && entry.isTraversable(graph)) {
                staleServed.incrementAndGet();
                refreshInBackground(key);
                result.put(t, entry);
            } else {
                missing.add(t);
                result.put(t, null); // keep caller order
            }
        }
        if (!missing.isEmpty()) {
            long start = System.nanoTime();
            SearchResult r = JunctionSearch.search(graph, source, missing, null);
            multiSearches.incrementAndGet();
            searchNanos.addAndGet(System.nanoTime() - start);
            for (JunctionId t : missing) {
                PairKey key = new PairKey(source, t);
                RouteCacheEntry entry = new RouteCacheEntry(graph, key, r.routesTo(t));
                cache.put(key, entry);
                result.put(t, entry);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ one-to-all (legacy whole-network views)

    public SweepEntry sweep(JunctionId source) {
        NetworkGraph graph = writer.graph();
        SweepEntry entry = sweeps.get(source);
        if (entry != null) {
            if (entry.isValid(graph)) {
                return entry;
            }
            if (executor != null && entry.isTraversable(graph)) {
                staleServed.incrementAndGet();
                refreshSweepInBackground(source);
                return entry;
            }
        }
        return singleFlight(sweepsInFlight, source, () -> {
            NetworkGraph g = writer.graph();
            SweepEntry cached = sweeps.get(source);
            if (cached != null && cached.isValid(g)) {
                return cached;
            }
            SweepEntry fresh = computeSweep(g, source);
            if (g.contains(source)) {
                sweeps.put(source, fresh);
            }
            return fresh;
        });
    }

    private SweepEntry computeSweep(NetworkGraph g, JunctionId source) {
        long start = System.nanoTime();
        SearchResult r = JunctionSearch.search(g, source, null, null);
        sweepSearches.incrementAndGet();
        searchNanos.addAndGet(System.nanoTime() - start);
        return new SweepEntry(g, source, r.settledOrder);
    }

    private void refreshSweepInBackground(JunctionId source) {
        ExecutorService ex = executor;
        if (ex == null) {
            return;
        }
        CompletableFuture<SweepEntry> mine = new CompletableFuture<>();
        if (sweepsInFlight.putIfAbsent(source, mine) != null) {
            return;
        }
        try {
            ex.execute(() -> {
                try {
                    NetworkGraph g = writer.graph();
                    SweepEntry cached = sweeps.get(source);
                    SweepEntry result = cached != null && cached.isValid(g) ? cached : computeSweep(g, source);
                    if (g.contains(source)) {
                        sweeps.put(source, result);
                    }
                    mine.complete(result);
                } catch (Throwable t) {
                    mine.completeExceptionally(t);
                } finally {
                    sweepsInFlight.remove(source, mine);
                }
            });
        } catch (RejectedExecutionException e) {
            sweepsInFlight.remove(source, mine);
            mine.cancel(false);
        }
    }

    // ------------------------------------------------------------------ plumbing

    private interface Computation<V> {

        V compute();
    }

    /**
     * Run {@code computation} unless another thread is already computing the same key, in which case wait for that
     * thread's result instead.
     */
    private static <K, V> V singleFlight(ConcurrentHashMap<K, CompletableFuture<V>> flights, K key,
            Computation<V> computation) {
        while (true) {
            CompletableFuture<V> mine = new CompletableFuture<>();
            CompletableFuture<V> existing = flights.putIfAbsent(key, mine);
            if (existing != null) {
                try {
                    return existing.join();
                } catch (RuntimeException e) {
                    // the other computation failed or was cancelled: try again ourselves
                    flights.remove(key, existing);
                    continue;
                }
            }
            try {
                V value = computation.compute();
                mine.complete(value);
                return value;
            } catch (RuntimeException | Error e) {
                mine.completeExceptionally(e);
                throw e;
            } finally {
                flights.remove(key, mine);
            }
        }
    }

    private void onPublish(JunctionGraphWriter.PublishEvent event) {
        if (!event.removedJunctions.isEmpty()) {
            Set<JunctionId> removed = new HashSet<>(event.removedJunctions);
            cache.keySet().removeIf(k -> removed.contains(k.source) || removed.contains(k.dest));
            sweeps.keySet().removeAll(removed);
        }
        if (cache.size() > MAX_CACHED_PAIRS) {
            cache.clear();
        }
    }

    public void clear() {
        cache.clear();
        sweeps.clear();
        inFlight.clear();
        sweepsInFlight.clear();
    }

    // ------------------------------------------------------------------ stats

    /** Zero every statistics counter (engine and writer). Cached routes and the graph are not touched. */
    public void resetStats() {
        pairSearches.set(0);
        multiSearches.set(0);
        sweepSearches.set(0);
        cacheHits.set(0);
        staleServed.set(0);
        searchNanos.set(0);
        writer.resetStats();
    }

    public long pairSearches() {
        return pairSearches.get();
    }

    public long multiSearches() {
        return multiSearches.get();
    }

    public long sweepSearches() {
        return sweepSearches.get();
    }

    public long cacheHits() {
        return cacheHits.get();
    }

    public long staleServed() {
        return staleServed.get();
    }

    public int cachedPairs() {
        return cache.size();
    }

    public int cachedSweeps() {
        return sweeps.size();
    }

    public long totalSearches() {
        return pairSearches.get() + multiSearches.get() + sweepSearches.get();
    }

    /** Mean wall time per search in nanoseconds. */
    public long averageSearchNanos() {
        long n = totalSearches();
        return n == 0 ? 0 : searchNanos.get() / n;
    }

    public List<String> describe() {
        NetworkGraph g = writer.graph();
        return java.util.Arrays.asList(
                "Junction graph: " + g.nodeCount()
                        + " junctions, "
                        + g.edgeCount()
                        + " corridors, generation "
                        + g.generation()
                        + ", pending edits "
                        + writer.pendingMutations(),
                "Route cache: " + cachedPairs()
                        + " pairs, "
                        + cachedSweeps()
                        + " network views, hits "
                        + cacheHits()
                        + ", stale served "
                        + staleServed(),
                "Searches: pair " + pairSearches()
                        + ", one-to-many "
                        + multiSearches()
                        + ", network views "
                        + sweepSearches()
                        + ", avg "
                        + averageSearchNanos() / 1000
                        + "us",
                describeEdits());
    }

    private String describeEdits() {
        long[] c = writer.improvementCounts();
        return "Edits: improvements logged: corridor " + c[ImprovementEvent.EDGE]
                + ", junction "
                + c[ImprovementEvent.NODE]
                + ", power data "
                + c[ImprovementEvent.DATA]
                + ", full refresh "
                + c[ImprovementEvent.ALL]
                + " | cuts: detour found "
                + writer.detourChecks()
                + ", full re-label "
                + writer.fullRelabels();
    }
}
