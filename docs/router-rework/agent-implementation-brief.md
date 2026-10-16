# Junction-Graph Routing Engine — Implementation Brief (Agent-Executable)

## Objective
Implement an incremental, junction-compressed routing layer for a Logistics-Pipes-style
item transport network in Java, replacing per-tick full-network pathfinding with lazy,
edge-versioned, per-pair route caching over a pruned junction graph.

## Design decisions already finalized — do not re-litigate these
- Graph nodes = active/junction pipes only. Plain transport pipes are compressed into
  edge weights, never represented as individual nodes.
- Redundant/looped paths ARE supported. The graph is a general graph, not a tree — do
  not implement tree-walk shortcuts that assume a single unique path between any two
  junctions.
- Compute is fully lazy / demand-driven: nothing is computed until a query needs it.
  The first successful query after a network edit is treated as proof the change
  matters — do not eagerly recompute on placement/removal alone.
- Cache key is per (source, destination) pair, not per-source batch. Do not implement
  "compute all distances from source" as the default query path.
- Crafting-tree resolution uses one-to-many search (Phase 4), not a loop of per-pair
  queries.
- Single-pair search and multi-target search are implemented as **one core routine**,
  not two separate algorithms. A* is Dijkstra with a heuristic; the difference between
  "quick one-to-one" and "complex one-to-many" is which arguments are passed to the
  same routine (heuristic on/off, target-set size 1 vs N), not which function is
  called. See Phase 3.
- Threading: structural edits and route computation happen off the main/tick thread.
  Queries read from an immutable graph snapshot via atomic reference swap.

## Data model
```java
record JunctionId(long value) {}

final class CorridorEdge {
    JunctionId a, b;
    double weight;   // aggregated length/cost of the compressed straight run
    long id;
    volatile long version; // bumped whenever this edge's existence or weight changes
}

final class JunctionNode {
    JunctionId id;
    BlockPos pos;
    List<CorridorEdge> edges;
    int componentId; // connected-component tag from union-find
    boolean isActiveEndpoint; // true if this junction is itself a provider/requester
}

final class NetworkGraph {
    // Immutable snapshot. Replaced wholesale via AtomicReference on structural change.
    Map<JunctionId, JunctionNode> nodes;
    Map<Long, CorridorEdge> edgesById;
    Map<ChunkPos, Set<Long>> edgesByChunk; // spatial index, see Phase 2
}

record PairKey(JunctionId source, JunctionId dest) {}

final class RouteCacheEntry {
    List<Long> edgeIds;                 // edges the cached route used, in order
    List<Long> edgeVersionsAtCacheTime; // parallel array to edgeIds
    List<JunctionId> path;
    double totalWeight;
}
```
- Cache: `ConcurrentHashMap<PairKey, RouteCacheEntry>`.
- In-flight dedupe (single-flight): `ConcurrentHashMap<PairKey, CompletableFuture<RouteCacheEntry>>`.
- Connectivity: `UnionFind<JunctionId>` maintained incrementally alongside the graph.

## Phase 1 — Junction graph construction & maintenance
1. Classify every placed pipe as **junction** (branching/functional, routing-relevant)
   or **pass-through** (straight/leaf transport pipe, no routing relevance on its own).
2. Placing a pass-through pipe: extend/merge into the adjacent corridor's aggregated
   weight. Never create a graph node for it.
3. Placing a junction pipe:
   - Mid-corridor (splits an existing edge): remove the old `CorridorEdge`, create two
     new edges to the new node, bump both new edges' `version`.
   - At a true dead end: add as a degree-1 `JunctionNode` only if `isActiveEndpoint` is
     true. Otherwise store it as an unindexed leaf with a cached scalar "distance to
     parent junction," not as a graph node.
4. Removing a junction pipe:
   - Degree 2, pass-through junction: merge its two edges into one
     (`weight = w1 + w2`), bump the merged edge's `version`.
   - Degree 1: remove the node; drop any associated last-mile cache entry.
   - Degree ≥ 3, and removal disconnects the graph: update union-find to split the
     component; bump `version` on every edge that was incident to the removed node.
     Do not eagerly recompute anything else — this is a lazy system.
5. Maintain `UnionFind<JunctionId>` incrementally on every edge add/remove. Use it as
   an O(α(n)) reachability pre-check before running any search — if source and dest
   are in different components, skip search entirely and return "unreachable."

## Phase 2 — Chunk/edge spatial index
Maintain `Map<ChunkPos, Set<Long edgeId>>` so a block edit inside one chunk looks up
exactly which corridor edges pass through that chunk, instead of scanning the whole
graph. Rebuild only the affected chunk's edge set when edges passing through it change.
Do not use chunk boundaries as the unit of cache invalidation — invalidation is always
edge-based (via `version`); the chunk index is purely a lookup accelerator to find
*which* edges to touch after a block edit.

## Phase 3 — Unified core search routine

Implement one routine, not two algorithms:

```java
// heuristic == null  → behaves as plain Dijkstra
// heuristic != null  → behaves as A*
// targets.size()==1  → single-pair mode (stop when that target is settled)
// targets.size()>1   → multi-target mode (stop when all targets are settled)
SearchResult search(JunctionId source, Set<JunctionId> targets,
                     ToDoubleBiFunction<JunctionId, JunctionId> heuristic)
```

A* is Dijkstra with a heuristic added; setting the heuristic to zero everywhere
recovers plain Dijkstra. Do not implement A* and Dijkstra as separate methods — the
only difference is whether a heuristic term is added when ordering the priority queue,
and whether termination checks one target or a shrinking set. Two call sites wrap this
one routine:

- `findRoute(source, dest)` → `search(source, Set.of(dest), MANHATTAN_HEURISTIC)`
- `findRoutesToTargets(source, targets)` → `search(source, targets, null)` (or, as a
  later optimization, a heuristic of `min over remaining targets of
  manhattanDistance(node, target)` — stays admissible since it's a lower bound to the
  closest remaining target; only add this if profiling shows it's needed, since it
  requires recomputing the heuristic as targets are removed from the remaining set)

### Heuristic: Manhattan distance, not Euclidean
Movement in the pipe network is axis-aligned (no diagonals), so Manhattan distance
(`|dx| + |dy| + |dz|` between junction `BlockPos`s) is the correct heuristic:
- **Admissible** — never overestimates true remaining cost, since any real path must
  cover at least that much axis-aligned displacement.
- **Consistent/monotonic** — the heuristic never increases by more than the real edge
  cost along any step, since each pipe segment changes exactly one axis.
- Consistency means a settled node's distance is final — never re-open a closed node.
- Pure integer `abs()`/addition — no `sqrt`, no floating point. Use this over Euclidean
  distance unconditionally; there is no accuracy trade-off here, only a correctness
  and performance win.
- **Caveat**: if any pipe axis (e.g., vertical/elevator pipes) is ever given a
  different per-block cost than others, the heuristic must be scaled by the minimum
  possible per-unit cost on each axis (`dx*minHorizCost + dy*minVertCost +
  dz*minHorizCost`) to remain admissible. Leave a comment at the heuristic call site
  noting this so weighted pipe types don't silently break optimality later.

### Query path using the unified routine
1. On a routing request `(source, dest)`:
   - Union-find check first. If not the same component, return "unreachable"
     immediately.
   - Cache lookup by `PairKey(source, dest)`. If present, verify every
     `edgeVersionsAtCacheTime[i] == edgesById.get(edgeIds[i]).version`. All match →
     return cached route.
   - On miss: `computeIfAbsent` into the single-flight map. If another thread is
     already computing this exact pair, await its future instead of starting a new
     search.
   - Call `search(source, Set.of(dest), MANHATTAN_HEURISTIC)` on a background
     thread/executor.
   - On completion: build and store a new `RouteCacheEntry` with edge ids and their
     versions *as observed at completion time*, complete the future, remove the
     in-flight entry.
2. Never mutate `NetworkGraph` in place. Structural edits build a new snapshot and
   publish it via `AtomicReference<NetworkGraph>.set(...)`. A query takes one
   consistent snapshot reference at the start of its search and uses only that.

## Phase 4 — One-to-many search (crafting-tree resolution)
Use this whenever a single source needs distances/routes to a known set of target
junctions at once (e.g., all ingredient sources for one crafting station), via the same
`search(...)` routine from Phase 3 called with the full target set:
- `search(source, targets, null)` — plain Dijkstra mode, multi-target termination.
- Internally: maintain `remainingTargets` as a mutable copy of the target set. On
  settling a node, if it is in `remainingTargets`, record its distance/path and remove
  it from the set. Terminate as soon as `remainingTargets` is empty — do not drain the
  whole priority queue. Fall back to normal queue-empty termination to catch
  unreachable targets (or pre-filter unreachable targets via union-find before
  starting).
- Do not implement this as a loop calling the per-pair query once per target — that
  defeats the shared-exploration benefit and duplicates cache/version-check overhead
  per target.

## Phase 5 — Concurrency
- Structural graph edits: single-writer per connected component (or a dedicated
  network-mutation thread); publish via atomic reference swap.
- Route computation: off the tick thread. Use a bounded executor, or structured
  concurrency (JDK 26, JEP 525, still preview — gate behind `--enable-preview` if used)
  for fanning out independent one-to-many requests across components.
- Never block the main game tick thread on route computation. Return a best-effort
  result (stale cached route, or "pending") if a fresh one isn't ready.

## Explicit non-goals for this implementation pass
- No all-pairs precomputation.
- No "cache all destinations from a source" batching — per-pair caching only.
- No dependency on Vector API or Valhalla value classes — both are preview/incubator
  in the current JDK track (JEP 529 remains an incubator feature in JDK 26 pending
  Valhalla; value classes are not yet available for general use). Use plain records
  and primitive-collection libraries (e.g., fastutil) for hot-path structures instead.
- No tree-only shortcuts (e.g., "recompute only the downstream subtree") — the graph
  allows cycles, so all recompute must go through real shortest-path search.

## Acceptance criteria / tests to implement
- Unit: split/merge correctness — merged edge weight equals sum of the two edges it
  replaced; split preserves total corridor weight across the two new edges.
- Unit: disconnect handling — union-find correctly reports "different component" after
  an edit that severs the only connection between two subgraphs.
- Unit: cache invalidation — mutate one edge's weight; confirm only cache entries whose
  `edgeIds` include that edge are treated as stale on next lookup, others remain valid.
- Concurrency: fire N simultaneous queries for the same `(source, dest)` immediately
  after invalidation; assert exactly one A* run occurs (single-flight dedupe holds).
- Perf: synthetic network generator producing 10k–50k pipes; confirm post-pruning
  junction count and benchmark per-pair query latency and edit-to-next-query latency.
- One-to-many: assert early termination — the search visits strictly fewer nodes than
  a full unbounded Dijkstra when targets are a small, nearby subset of the graph.
- Unified routine: assert `search(source, targets, null)` and
  `search(source, targets, MANHATTAN_HEURISTIC)` return identical distances/paths for
  the same inputs (heuristic must only affect exploration order/speed, never
  correctness) — this is the regression test that catches the two modes drifting apart
  since they share one code path.
- Heuristic admissibility: for a random sample of (node, target) pairs, assert
  `manhattanDistance(node, target) <= actualShortestDistance(node, target)` never
  fails, to catch any future weighted-axis change that breaks admissibility.
