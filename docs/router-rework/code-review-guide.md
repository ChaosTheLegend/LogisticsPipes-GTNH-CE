# Junction-Graph Router — Code Guide & Reviewer Notes

Companion to `agent-implementation-brief.md` (the spec), `developer-guide.md` (the design rationale) and
`benchmark-results.md` (numbers). This file explains what every class does and what deserves a careful look.

The rework design choices ([docs/rework-design-decisions.md](../rework-design-decisions.md)) change parts of this
router; see §5. The item transport built on top of it is in
[docs/item-transport-rewrite.md](../item-transport-rewrite.md).

- Production code: `src/main/java/logisticspipes/routing/astar/`
- Tests: `src/test/java/logisticspipes/routing/astar/`
- Old router (`routing/ServerRouter`, `routing/pathfinder/*`) is **unchanged** and still compiles, but nothing
  creates a `ServerRouter` anymore.

Run the tests: `./gradlew test --tests 'logisticspipes.routing.astar.*'`
(add `-i` to see the `[bench]` / `[junction-perf]` output; the two benchmark classes take ~1–2 min).

---

## 1. How the pieces fit

```
 world (main thread)                          writer (1 thread)                 queries (any thread)
 ───────────────────                          ─────────────────                 ────────────────────
 JunctionRouter ── CorridorScanner ──EdgeSpec──► JunctionGraphWriter ──publish──► NetworkGraph (immutable)
   (IRouter impl)   (scan plain pipe)            (graph surgery, union-find,           ▲
                                                  stamps, improvement log)            │ one snapshot per query
                                                                                       │
 JunctionRouter.getExitFor / getDistanceTo ──────────────────────────► JunctionRoutingEngine
                                                                        (pair cache, single-flight,
                                                                         one-to-many, sweeps, stale refresh)
                                                                               │
                                                                               ▼
                                                                         JunctionSearch
                                                                         (A*/Dijkstra, 1/N/all targets)
```

- **Junction** = a routed pipe (router). Plain pipe is never a node; it is compressed into **corridors** (directed
  edges) by `CorridorScanner`.
- Edits never mutate a published graph: the writer builds a new `NetworkGraph` and swaps it in atomically.
- Nothing is computed eagerly. A query validates its cached result against the current snapshot and re-searches only
  when the result may be wrong.

---

## 2. Production classes (`src/main/java/.../routing/astar`)

### 2.1 Core (pure Java, no Minecraft classes — unit-tested directly)

| Class | Role |
|---|---|
| `JunctionId` | Junction identity. Value = router simple id, doubles as array index (cached instances < 4096). |
| `RoutingFlags` | `int` bit-mask form of `PipeRoutingConnectionType` (route / request / power / sub-system power). |
| `EdgeSpec` | A corridor as reported by a scan, before it has an id/version. `sameContent` = "unchanged", `improvesOn` = "could make some route shorter". |
| `CorridorEdge` | Immutable directed corridor: id (owner index in high 32 bits), weight, flags, filters, sides, chunk keys, **version**. A changed corridor is replaced by a copy with the same id and `version + 1`. |
| `JunctionNode` | Immutable junction: position, outgoing corridors, `active` (pipe loaded), opaque `payload` (the router) and `data` (power providers). |
| `NetworkGraph` | Immutable snapshot: nodes by index, component of each node, per-component info, heuristic scale, chunk index, junctions carrying data. Also defines the scaled Manhattan heuristic (`manhattanHeuristic`, `lowerBound`). |
| `ComponentInfo` | Per connected component: **identity** token, change stamp, improvement log head, heuristic scale. |
| `ImprovementEvent` | Newest-first linked log entry: corridor added/cheaper/new flag (`EDGE`), junction added/re-activated (`NODE`), power data (`DATA`), or "everything" (`ALL`). Capped at 256, truncated to 128 + an `ALL` marker. |
| `UnionFind` | Union-by-size DSU over **slots**. Every junction gets a fresh slot that is never reused. `relabel` rewrites a component after a split. |
| `ChunkEdgeIndex` | Chunk → corridor ids. Lookup accelerator only (chunk unload → which routers re-scan); never used for invalidation. |
| `JunctionGraphWriter` | The single writer. Queues edits from any thread, applies them in order under a lock (own thread in async mode, caller thread in sync mode), publishes one snapshot per batch. Does corridor diffing (id/version), union-find, split detection (detour search, then full relabel), improvement logging, split/merge surgery primitives. |
| `JunctionSearch` | **The** search routine: `search(graph, source, targets, heuristic)`. `heuristic == null` → Dijkstra, otherwise A*; `targets` of size 1, N, or `null` (= everything). Port of the old link-state multi-label rules (per-flag settling, firewall filter dominance). |
| `RouteLabel` | One accepted route: target, distance, full flags, newly delivered flags, filters, block distance, first corridor (exit side), parent chain for the path. |
| `SearchResult` | Output of a search: routes per target, settle order, unreachable targets, settled/polled counters. |
| `PairKey` | `(source, dest)` cache key. |
| `ValidatedRoutes` | Base of cached results: recorded corridors + versions, component identity, known stamps. `isValid(graph)` is the whole invalidation logic (see §4.2). `isTraversable` decides whether a stale result may be served. |
| `RouteCacheEntry` | Cached routes for one pair + `settledAt` per flag. Implements the "ellipse" survival test for improvements. |
| `SweepEntry` | Cached whole-network result for one source (legacy views). Does not survive any improvement. |
| `JunctionRoutingEngine` | Query side: pair cache + single-flight, one-to-many (`findRoutesToTargets`, stores pair entries), sweeps, background refresh of stale-but-traversable results, stats (`/lp rt`). |

### 2.2 Minecraft adapter

| Class | Role |
|---|---|
| `JunctionRouter` | `IRouter` implementation replacing `ServerRouter`. Keeps its behaviour for change listeners, corridor re-scan triggers, security/firewall side disconnection, interests, queued tasks, routed/sub-power exits. Publishes its corridors and power data to the writer; answers route questions from the engine and converts `RouteLabel`s to `ExitRoute`s once per cache entry. Power tables come from pair routes to power junctions (`PowerView`), the by-cost list and route table from a sweep (`SweepView`). |
| `CorridorScanner` | Port of `PathFinder#getConnectedRoutingPipes` (special connections, `IRouteProvider`, direct connections, one-way/power-only/network-dividing pipes, firewall filters) plus recording of chunk keys and real corridor length per found router. Since the transport rewrite it also records the direction taken at each pipe (`travelPathOf`, `null` when the corridor uses anything but LP pipes), which becomes `EdgeSpec`/`CorridorEdge.travelPath`. |
| `JunctionRouterManager` | `extends RouterManager`; creates `JunctionRouter`s on the server, own server router list/UUID map. Client routers, direct connections and security stations are inherited. |
| `InterestRegistry` | The static "who is interested in which item" tables (moved from `ServerRouter`). |
| `RouterIds` | Simple-id allocator + `getBiggestSimpleID()` (moved from `ServerRouter`). |
| `LPJunctionNetwork` | Server-wide singleton: writer + engine, thread start, cleanup on server stop, chunk-unload hook, `/lp rt` text, stats reset. |
| `JunctionRoutingThread` | Marker thread class for the writer and refresh pool; `MainProxy` treats it as a server thread. |

### 2.3 Driving classes changed outside `astar`

| File | Change |
|---|---|
| `LogisticsPipes` | `new JunctionRouterManager()`; `LPJunctionNetwork.start(...)` instead of `RoutingTableUpdateThread`s; `LPJunctionNetwork.cleanup()` on server stop. |
| `proxy/MainProxy` | `JunctionRoutingThread` counts as server side. |
| `LogisticsEventListener` | `ChunkEvent.Unload` → `LPJunctionNetwork.onChunkUnload`. |
| `LogisticsManager`, `ModuleCCBasedQuickSort`, `CoreRoutedPipe`, `ItemAmountPipeSign`, `LogisticsPowerProviderTileEntity` | `ServerRouter.getRoutersInterestedIn` / `getBiggestSimpleID` / interest getters → `InterestRegistry` / `RouterIds`. |
| `request/RequestTreeNode` | Providers and crafters resolved with one one-to-many search (`JunctionRouter#getDistancesTo`); `checkExtras` uses two pair lookups instead of `getRouteTable().get(id)` + a scan of `getIRoutersByCost()`. |
| `pipes/basic/CoreRoutedPipe#doDebugStuff` | Works for both router types. |
| `network/.../RoutingUpdateTargetResponse` | Refuses the step-by-step routing-table debugger for non-`ServerRouter` routers (it drives the old Dijkstra). |
| `commands/.../RoutingThreadCommand`, `RoutingThreadClearCommand`, `MainCommandHandler` | `/lp rt` shows engine stats, `/lp rt-clear` resets them. |

---

## 3. Test classes (`src/test/java/.../routing/astar`)

All tests build graphs directly through `JunctionGraphWriter` in synchronous mode; no Minecraft classes are loaded.

### `TestNetworks` (helper, not a test)
- `node`, `edge`, `link` (symmetric corridor), `apply`, `specsOf`, `withWeight`: small graph-building helpers.
- `buildRandom(writer, rnd, count, flagRestrictChance)`: junctions on a grid of 16-block cells with random offsets,
  corridors to near neighbours with weight ≥ Manhattan distance (like real pipe), ~15% missing (loops and detours),
  optionally random flag restrictions per direction.
- `referenceDistances(graph, source, flag)`: **independent** plain Dijkstra over corridors carrying one flag, through
  active junctions only, never through the source. It shares no code with `JunctionSearch` and is the oracle for all
  correctness tests (filter-free graphs only).

### `JunctionGraphWriterTest` — graph surgery, union-find, index, stamps
| Test | Checks |
|---|---|
| `splitPreservesTotalCorridorWeight` | `splitCorridor` keeps the corridor total in both directions; old ids disappear, halves are fresh edges. |
| `mergeSumsWeights` | `mergeThrough` of a degree-2 junction produces `w1 + w2` both ways and keeps connectivity. |
| `mergeRefusesNonPassThroughJunction` | Degree ≠ 2 is left alone. |
| `unchangedCorridorKeepsIdAndVersion` | A no-op re-scan publishes nothing; a weight change keeps the id and bumps the version by 1; untouched corridors keep theirs. |
| `severingTheOnlyConnectionSplitsComponents` | Two triangles joined by a bridge: removing a non-bridge keeps one component, cutting the bridge splits it, re-adding merges. |
| `cutWithADetourNeedsNoFullRelabel` | Cutting one side of a square is resolved by the detour search (no full relabel, component identity kept); cutting a real bridge afterwards does one full relabel. |
| `reusedIdDoesNotInheritTheOldComponent` | Removed junction ids re-used elsewhere are not in their old component (the slot bug that was fixed). |
| `removingAJunctionDropsCorridorsIntoIt` | Removing a junction also drops the corridors other junctions reported into it. |
| `chunkIndexFindsCorridorsThroughAChunk` | Index follows corridor re-routes and removals. |
| `stampsTrackImprovementsSeparatelyFromChanges` | A heavier corridor changes the change stamp only; a cheaper one also the improvement stamp. |
| `asyncWriterPublishes` | The writer thread applies queued edits and publishes. |

### `JunctionSearchTest` — the unified search routine
| Test | Checks |
|---|---|
| `cheaperCorridorOfALoopIsChosenAndFlipsOnWeightChange` | Two corridors between the same junctions: the cheaper wins, and the choice flips back and forth with weight changes (through the engine, so invalidation is exercised too). |
| `differentComponentsAreUnreachableWithoutSearch` | Union-find pre-check answers without running a search. |
| `filteredShortRouteKeepsUnfilteredAlternative` | A short route through a "firewall" filter and a longer unfiltered one are both returned (old link-state semantics). |
| `flagsAreIntersectedAlongTheRoute` | Flags are ANDed along a route; per-flag shortest routes can differ. |
| `randomGraphsMatchReferencePerFlag` | 20 random graphs × 10 pairs × 4 flags: A* distances equal the reference oracle. |
| `heuristicAndTargetSetNeverChangeTheAnswer` | **Parity:** Dijkstra vs A*, one target vs many vs all give identical routes (distances, flags, new flags, paths). Guards the single-routine design. |
| `oneToManyStopsEarlyForNearbyTargets` | Multi-target search settles < ¼ of the junctions a full run settles. |
| `aStarExploresLessThanDijkstra` | Same distance, fewer settled junctions. |
| `manhattanHeuristicIsAdmissible` | Including cheap "tesseract" shortcuts: `h(s,t) ≤ true distance` for all sampled pairs. Catches a future per-axis cost that breaks A*. |
| `inactiveJunctionsAreNotRoutedThrough` | Unloaded junctions are skipped as intermediates and as targets. |

### `RouteCacheTest` — cache validity, single-flight, stale serving
| Test | Checks |
|---|---|
| `repeatedQueryIsACacheHit` | Same entry object, one search. |
| `worseningOneEdgeInvalidatesOnlyRoutesUsingIt` | A heavier corridor invalidates the route over it only; the recomputed route takes the detour. |
| `improvementElsewhereInvalidatesRoutesOfTheComponent` | A new shortcut that the cached route does not use still invalidates it (loops!). |
| `farAwayImprovementKeepsTheRoute` | The ellipse test: a far corridor keeps the route (no search); a nearby cheaper one invalidates the affected route. |
| `editsInOtherComponentsDoNotTouchTheCache` | Stamps are per component. |
| `singleFlightRunsExactlyOneSearch` | 16 threads query the same invalidated pair at once: exactly one search, one shared entry. |
| `staleButTraversableRouteIsServedWhileRefreshing` | With an executor, the old route is returned immediately and replaced by the background refresh. |
| `brokenRouteIsNeverServedStale` | A route over a cut corridor is recomputed synchronously, never served. |
| `oneToManyFillsThePairCache` | One multi-target search, results reused as pair hits; a second call needs no search. |
| `cachedRoutesMatchFreshSearchUnderRandomEdits` | **Main safety net:** 6 × 40 steps of random edits (weight up/down, corridor add/remove, flag change, junction unload/reload, removal/re-add), cached answers compared with the reference after every batch. Any unsound invalidation shortcut fails here. |

### `JunctionPerfTest` — synthetic 10k / 50k pipe networks
Generates a grid of pipe lines with routers at 70% of crossings, compresses it with a BFS that obeys LP's scan limits
(100 pipes, length 50), then prints compression ratio, cold/cached pair latency, edit publish latency and the old
router's estimated per-edit cost. Asserts correctness (sampled against the reference) and pruning ratio only; timings
are printed, never asserted.

### `JunctionBenchmarkTest` — the numbers in `benchmark-results.md`
50k-pipe network, 20,000 warm-up queries, then: cold/hot pair queries, cold/hot one-to-many (random and nearby
targets), break/place pipe edits (publish + next queries + how many unrelated routes were re-searched), power polling
during building (whole-network view vs routes to power junctions), and 20 parallel one-to-many queries with and
without concurrent edits. Only prints; asserts nothing about time.

---

## 4. Reviewer notes

### 4.1 Where to start
1. `JunctionSearch` (the algorithm), then `ValidatedRoutes#isValid` + `RouteCacheEntry#survives` (correctness of
   caching), then `JunctionGraphWriter#doSetEdges` / `resolveSplits` / `publish`.
2. `JunctionRouter` against `ServerRouter` side by side: most of it is a port; the differences are the query methods,
   `publishCorridors`, `PowerView`/`SweepView` and activity reporting.
3. `CorridorScanner` against `PathFinder`: should be a line-by-line port plus `pathChunks` / `metricByRoute`.

### 4.2 Correctness arguments worth checking
- **Invalidation (`ValidatedRoutes#isValid`).** A cached result is valid on a newer snapshot if the component identity
  is the same and either nothing changed (change stamp), or (a) every logged improvement since then *survives* and
  (b) every recorded corridor has the same version and leads to an active junction. Worsening changes are not logged:
  they cannot beat a route that does not use the worsened corridor, and routes that do use it fail (b).
- **Ellipse test (`RouteCacheEntry#survives`).** Any route using improved corridor `u→v` costs at least
  `h(s,u) + w + h(v,t)` (h admissible on the *current* graph). If that is above `settledAt[f]` for every flag the
  corridor can carry, the search would reject such a route at the target. Ties count as "may beat" (`EPSILON`).
  `settledAt` uses only filter-free routes; flags never settled are +∞, so they always invalidate. Flags the source
  cannot emit are masked out unless the event is at the source itself.
- **Heuristic scaling.** `scale = min(weight / manhattan)` over the component's corridors, 0 if any corridor crosses
  a dimension. Consistent, so settled routes are final. TD ducts and tesseracts shrink the scale automatically.
- **Union-find.** Slots are never reused (fixes a real bug: a removed junction could stay the root of its old
  component, and a new router re-using the id looked connected). Merges keep the larger side's identity; splits give
  every part a fresh slot and the largest part inherits identity/stamps/log.
- **Split detection.** A removed connection is ignored if the reverse corridor still exists or a bidirectional BFS
  finds a detour within 512 junctions; otherwise the component is re-labelled by a full BFS.

### 4.3 Deliberate deviations from the spec
- **Directed corridors** instead of undirected edges (one-way pipes, firewalls, sub-system power are asymmetric).
- **Cache misses are computed on the calling thread**, not answered with "pending": `IRouter` callers (item routing)
  cannot handle "pending". Stale results are served (with background refresh) only when every corridor still exists
  with the same sides, flags and filters.
- **Improvement log** on top of edge versions (edge versions alone are unsound on a looped graph).
- **Whole-network views** (`SweepEntry`) exist because `getIRoutersByCost` / `getRouteTable` are part of the
  `IRouter` contract. They are lazy, validated like pairs, never used for pair answers, and invalidated by any
  improvement.
- **Phase 1 graph surgery**: `splitCorridor` / `mergeThrough` exist and are tested, but in game every structural
  change arrives as "re-scan the neighbours' corridors" (`setEdges` diff). Removing a routing pipe cuts the corridor,
  it does not merge it.

### 4.4 Behaviour changes a player could notice
- Request-only and power-only routes are ordered by real corridor length (the old router gave them cost
  `Integer.MAX_VALUE`). `ExitRoute.distanceToDestination` is still `MAX_VALUE` when the route lacks `canRouteTo`.
- A router never routes to itself through a loop (the old table could contain such entries).
- Corridors through an unloaded chunk make their owners re-scan (`onChunkUnload`); the old router kept them.
  Whether the re-scan actually sees the cut depends on the neighbour tile cache of the border pipe.
- `/lp` routing-table step debugger is not available for the new router (message instead of a crash).
- Power tables are ordered by the distance of each power junction's first route (approximation of the old settle
  order).

### 4.5 Threading
- The world is only read on the server thread: corridor scans happen in `update()` and, as before, inside
  `ensureConnectionsFresh()` at the start of a query when a change listener fired.
- `MULTI_THREAD_NUMBER > 0`: one writer thread + a refresh pool of that size (bounded queue 4096, `AbortPolicy`;
  a rejected refresh just leaves the stale entry for the next query). `0`: everything synchronous.
- `JunctionRouter.reportActive` is synchronized; `ExitRoute` views are built once per cache entry and shared.

### 4.6 Known limitations / follow-ups
- **No eviction of unused pair entries** except on junction removal or when the cache exceeds 2^20 entries (then
  everything is cleared). Fine at realistic sizes; add age-based eviction if pairs keep growing in long sessions.
- **Union-find slots grow** by one per junction ever added (8 bytes each), no compaction.
- **Whole-network views** (request GUIs, route table) re-run after any improvement in the network.
- **Power polling while building** still re-searches routers whose power route could have been shortened (see
  `benchmark-results.md` §7); a reachability-only mode would make it nearly free.
- `WeakReference` loss of a pipe without `clearPipeCache()` is not reported to the graph (junction stays "active").
- `RouterManager#getOrCreateRouter` quirk kept: the UUID lookup result is ignored, as in the original.
- Old `ServerRouter`, `PathFinder` (still used by the routing-laser packet) and `RoutingTableUpdateThread` remain in
  the code base; `ServerRouter.cleanup()` is still called on server stop (harmless).
- Pre-existing spotless violations in 5 unrelated files (`ModuleCreativeTabBasedItemSink*`, `ModuleModBasedItemSink*`,
  `ItemModule`) were left untouched.

### 4.7 Not covered by automated tests
Everything in the Minecraft adapter (`JunctionRouter`, `CorridorScanner`, `JunctionRouterManager`, power view,
chunk-unload hook) is only covered by in-game testing so far. Worth checking in game: firewall and security-station
separation, power provider lasers (`getRoutersOnSide` / sub-system power), tesseract / cross-dimension links, crafting
requests with extras (`checkExtras`), CC broadcast (`getIRoutersByCost`), chunk unload and reload of part of a network.

---

## 5. Effect of the rework design decisions

From [docs/rework-design-decisions.md](../rework-design-decisions.md), which takes precedence over this guide:

- **Only LP pipes route.** BuildCraft, Thermal Dynamics and other mods' pipes won't be supported for routing.
  - `CorridorScanner` should stop at any non-LP pipe. Its `IRouteProvider` branch (TD ducts) and the foreign-pipe
    handling in `PipeInformationManager` (`BCPipeInformationProvider`, `TDDuctInformationProvider`) can then go.
  - The heuristic note in §4.2 about TD ducts shrinking the scale no longer applies once they're gone.
  - Tesseracts and other special pipe connections (`SpecialPipeConnection`) keep an explicit link for compatibility. In
    GTNH they will most likely be treated as chests (buffer endpoints), so they would stop being corridors.
- **GregTech pipes** are not corridors. They are buffers next to LP pipes, handled like an adjacent inventory.
- **The Inv. System Connector is kept and upgraded**, so direct connections (`IDirectRoutingConnection`, the scanner's
  `isDirectConnection` path) stay part of routing.
- **Side-block (disconnection) upgrades are removed**, and shift+wrench disconnects sides instead. The router's side
  disconnection (`sideDisconnected`, `isSideDisconneceted`) must read the new per-side state. Security separation is
  unchanged.
- **Travel speed comes from tiered transport controller blocks** (like the power junction; the top tier is instant), not
  speed upgrades. Corridor weights don't depend on speed, so route choice doesn't change. Finding a network's controller
  could reuse the power-provider pattern (`PowerData` on junctions, routed lookups). The transport doc has the timing.
