# Junction-Graph Router — Benchmark Results

Source: `src/test/java/logisticspipes/routing/astar/JunctionBenchmarkTest.java`
Run: `./gradlew test --tests 'logisticspipes.routing.astar.JunctionBenchmarkTest' -i`

## Setup

| | |
|---|---|
| Network | synthetic grid, 720×720 blocks, pipe line every 20 blocks |
| Pipes | 50,544 |
| Junctions (routers) after compression | 1,087 (70% of crossings + a few along lines) |
| Corridors | 3,070 |
| Scan limits | LP defaults: 100 pipes visited, run length 50 |
| Machine | 20 cores, JDK 17 (Gradle test JVM, `-Xmx512m`) |
| Engine mode | synchronous: graph edits are applied on the calling thread |
| Warm-up | 20,000 untimed queries before measuring (steady-state JIT) |

All times in microseconds (µs); 1,000 µs = 1 ms. "Cold" = cache cleared before every query.

## Results (current: with the edit fixes below)

| # | Benchmark | n | mean | p50 | p90 | p99 | max |
|---|---|---:|---:|---:|---:|---:|---:|
| 1 | Pair query, cold (first time) | 3000 | 243 | 125 | 619 | 1,533 | 3,142 |
| 2 | Pair query, hot (cached) | 3000 | 0.6 | 0.5 | 0.8 | 1.3 | 11 |
| 3 | One-to-many ×3, cold, random targets anywhere | 2000 | 872 | 786 | 1,479 | 2,076 | 25,435 |
| 3 | One-to-many ×3, cold, targets within 96 blocks (crafting-like) | 2000 | 56 | 38 | 114 | 311 | 487 |
| 4 | One-to-many ×3, hot, random targets | 2000 | 1.7 | 1.4 | 2.2 | 4.2 | 202 |
| 4 | One-to-many ×3, hot, targets within 96 blocks | 2000 | 1.7 | 1.5 | 2.1 | 2.8 | 94 |
| 5a | Break pipe: graph edit + publish | 489 | 305 | 195 | 380 | 3,040 | 5,471 |
| 5b | Break pipe: next query over the cut corridor | 489 | 120 | 49 | 160 | 2,439 | 6,286 |
| 5c | Break pipe: next query of another cached pair | 489 | 22 | 8 | 23 | 560 | 1,185 |
| 5d | Place pipe: graph edit + publish | 489 | 168 | 140 | 288 | 432 | 1,013 |
| 5e | Place pipe: next query over the restored corridor | 489 | 32 | 29 | 47 | 99 | 155 |
| 5f | Place pipe: next query of another cached pair | 489 | 87 | 7 | 341 | 1,122 | 2,565 |
| 6 | 20 parallel one-to-many (1–4 targets), per query | 3000 | 985 | 983 | 1,378 | 3,485 | 5,900 |
| 6 | 20 parallel one-to-many (1–4 targets), whole batch of 20 | 150 | 2,609 | 2,440 | 3,135 | 6,599 | 6,701 |
| 6 | Same, with concurrent edits (~4,000 edits/s), per query | 3000 | 1,077 | 1,017 | 1,530 | 4,662 | 8,610 |
| 6 | Same, with concurrent edits, whole batch of 20 | 150 | 4,641 | 4,410 | 6,546 | 8,509 | 10,649 |

Stress totals: 3,000 cold queries in 0.41 s without edits, 0.72 s with 2,836 edits published meanwhile.

Cached pairs elsewhere in the network that needed a new search after an edit:

| Edit | before fixes | now |
|---|---:|---:|
| Break pipe | 10 of 489 | 10 of 489 (only those whose route used the cut corridor) |
| Place pipe | 475 of 489 | 61 of 489 (only those the new corridor could shorten) |

Run-to-run noise on this machine is roughly ±2× for the edit publishes (5a/5d) and the stress numbers (6):
5a p50 was 87–195 µs and 5d p50 was 64–140 µs across two runs.

## Before / after the edit fixes (p50 / p99)

| Benchmark | before | after |
|---|---|---|
| 5a Break pipe: graph edit + publish | 2,182 / 11,466 | 87–195 / 2,693–3,040 |
| 5c Break pipe: other cached pair | 9 / 401 | 7–8 / 560–1,018 |
| 5d Place pipe: graph edit + publish | 63 / 180 | 64–140 / 181–432 |
| 5f Place pipe: other cached pair | 187 / 1,078 | 7 / 1,122–1,453 |
| Other pairs re-searched after a placement | 475 of 489 | 61 of 489 |
| 6 Stress with concurrent edits, edits published | 864 in 1.19 s | 2,836 in 0.72 s |

### Fix 1: breaking a pipe (split check)
Union-find cannot split, so every removed connection used to trigger a BFS over the whole component. Now each pair of
junctions that lost a direct connection is checked first for (a) a remaining corridor in the other direction and
(b) a detour found by a bidirectional BFS limited to 512 junctions. Only if both fail is the component re-labelled.
The remaining p99 of ~3 ms comes from cuts with no detour within the budget (real bridges, or dead ends of the
synthetic grid); those still re-label the component, as before.

### Fix 2: placing a pipe (cache invalidation)
Adding or cheapening a corridor used to invalidate every cached route of the component. Each component now keeps a
short newest-first log of improvements (corridor added/cheaper/new flag, junction added/re-activated). A cached route
walks the events newer than itself and survives an event when the admissible lower bound of any route through it,
`h(s,u) + w + h(v,t)`, is above the cost at which every flag the corridor carries was already settled at the
destination (an "ellipse" test using only lower bounds, so it never keeps a route that is no longer shortest).
The randomized edit fuzz test compares cached answers with fresh searches after every batch of edits.

### Also fixed while doing this
Union-find now uses per-junction slots that are never re-used. Previously a removed junction could remain the internal
root of its old network, and a new junction re-using its id would wrongly look connected to that network. Networks
also carry an identity token that survives merges (the larger side keeps it) and splits (the largest part keeps it),
so placing a router next to a big network does not invalidate that network's cached routes.

## Reference: old link-state router

The old router ran a full Dijkstra on every router after each edit. One full-network sweep on this network takes
~2 ms, so one edit cost roughly **2.1–2.4 s** of routing-table work in total (from `JunctionPerfTest`).

## Caveats

- The in-world corridor re-scan (`CorridorScanner`) that precedes an edit in game is not included in (5);
  it walks at most 100 pipes per affected router.
- In game, edits (5a, 5d) run on the writer thread, not on the server tick, and edits queued while a publish is running
  are applied together in one publish.
- Whole-network views (`getIRoutersByCost`, route table, power tables) are still invalidated by any improvement in the
  network; they are not part of these benchmarks.

## 7. Power polling while building (after the in-game `/lp rt` finding)

In game, every routed pipe polls its power providers every few ticks (`checkTexturePowered` -> `canUseEnergy`).
The first version served this from the whole-network view, which any improvement invalidates, so every placement
re-ran a full sweep for every router (`/lp rt` on a 100-junction test network: 3,343 network-view searches vs 118
pair searches). Power tables now come from cached pair routes to just the junctions that have power providers attached.

Benchmark: 50k-pipe network, 1,087 routers, 5 power junctions at random positions, 100 corridor placements in a row;
after each placement every router asks for its power table.

| Benchmark (all routers, per placement) | searches per placement | mean | p50 | p99 |
|---|---:|---:|---:|---:|
| 7a) old: whole-network view per router | 1,070 | 1,631 ms | 1,604 ms | 2,473 ms |
| 7b) new: pair routes to the 5 power junctions | 393 | 396 ms | 302 ms | 1,103 ms |

In game this work runs on the background refresh pool (stale power tables are served meanwhile), not on the tick.
The remaining re-searches are exact invalidations: with providers spread over the whole 720×720 map, a placement lies
inside the "ellipse" between many routers and one of their providers. Providers placed close to the machines they
feed (the usual layout) invalidate far fewer routers.

Possible follow-up: power tables only need reachability and filters, not the shortest route, so reachable providers
could be kept while their route is still traversable, re-searching only unreachable ones on improvements. This would
make power polling nearly free during building, at the cost of power routes not always being the shortest.
