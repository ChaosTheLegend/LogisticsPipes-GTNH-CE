# Item transport rewrite: clumps that hop junction to junction

Fixes the TPS lag in [lag-investigation.md](lag-investigation.md) §1. Started 2026-09-29.
Paths are relative to `src/main/java/logisticspipes/`; `PTL` = `transport/PipeTransportLogistics.java`.

## Goal

The server no longer simulates routed items block by block. When an item leaves a routed pipe (junction) along a corridor
that holds only LP pipes, it becomes part of a **clump**: a group of items with the same destination, transport mode and
route. The clump is scheduled to arrive at the next junction after `blockDistance / speed` ticks. Nothing happens
between departure and arrival. The route is computed **once, when the clump departs**. Each intermediate junction only
checks that the next corridor on the stored path still exists. The client gets one packet per clump per hop, containing
the pipe-by-pipe directions of the corridor, and animates the items itself.

## Decisions (user, 2026-09-29)

- **A pipe breaks under a clump in flight** (superseded the first "drop at the target pipe" rule). A clump's position is
  known from time, as `pipe index = elapsed / ticks per pipe` along the corridor it is on. Compared with the broken pipe:
  - **behind** the clump: the clump finishes its route;
  - **the pipe it is in right now**: its items drop there;
  - **ahead** of it (including the target junction): the clump turns back to the junction it left and is routed again
    there. If that junction is gone or unloaded, the items drop where the clump is.
  - A clump already turning back that meets another break ahead drops its items where it is, instead of bouncing again.
  - Corridors through the broken pipe are flagged broken until they are re-scanned. Items that would depart along one
    wait in the pipe's retry buffer instead of teleporting through the gap.
- **Clumping:** there is a short gather window. An item can join a clump that left the same junction with the same key up
  to `itemClumpGatherTicks` ticks earlier (config, default 5). A late joiner arrives up to that many ticks early. Clumps
  that meet at a junction in the same tick merge the same way.
- **Visuals:** the client draws up to 3 stacks per clump.

## Constraints from the rework design ([rework-design-decisions.md](rework-design-decisions.md))

Those decisions take precedence over this doc. What they mean for transport:

- **Only LP pipes route and carry items.** BuildCraft, Thermal Dynamics and other mods' pipes aren't supported for
  routing. So the phase 1 fallback that simulates corridors through foreign pipes and special connections is temporary:
  - The final state has no per-tick item simulation at all. `CorridorScanner` stops at non-LP pipes, and
    `BCPipeInformationProvider`, `TDDuctInformationProvider`, the `LPRoutedBCTravelingItem` interop and
    `injectItem(TravelingItem)` go away.
  - Tesseracts and other special pipe connections keep an explicit link for compatibility. In GTNH they will most likely
    be treated as chests: a buffer endpoint like the GT pipes below, not part of a corridor.
- **GregTech pipes as buffers.** A GT item pipe next to an LP pipe can take items out of the LP network and push items into
  it, like an inventory. It is never part of a corridor. *(Not designed yet.)*
- **No traveling item entities.** Items only teleport pipe to pipe. The client animation is visual only.
- **Travel speed comes from transport controller blocks, not upgrades.** Speed upgrades move to extraction speed / stack
  size and the crafting table.
  - The controllers are like the logistics power junction and come in several tiers. The last tier makes transport
    **instant**.
  - `readjustSpeed` (per-pipe speed upgrades, acceleration power) goes away. Each hop reads the speed from the network's
    controller.
  - Travel time becomes `pipes × ticks per pipe of the controller tier`. For the instant tier, a clump skips straight to
    its destination in the same tick: one event per trip and no intermediate junctions. That's the biggest win for
    performance, and it needs the pass-through skipping of later phase 1. Whether the client still animates instant
    transport is an open question.
  - **Open:** how a controller's speed applies (the whole network or a region; what happens with several tiers in one
    network; whether it costs power), and whether the Default/Passive/Active modes keep different speeds.
  - With speed only changing at a controller, a run of pass-through junctions is always one scheduled hop.
- **Sneaky upgrades are removed.** Holding a screwdriver shows a grid overlay on the pipe, and the sneaky side is set
  per connection with it. **Side-block (disconnection) upgrades are removed**: shift+wrench shows the same overlay
  to connect or disconnect each side. The delivery step in
  `handleTileReachedServer` (sneaky orientation, `getCombinedSneakyOrientation`) and the scanner's connection checks
  read these per-side settings instead. Sneaky and disconnection upgrades already installed in old bases are migrated
  to the per-side settings, so their pipes behave the same after the update.
- **The inventory system connector (ISC) is re-enabled and upgraded.** It links two networks through *any* buffer (chests,
  ender chests, pipelines, minecarts, rockets). Its direct connection stays opaque to clumps; items enter the buffer at
  one end and are re-injected at the other.
- **Buffer upgrade (new):** supplier and crafting pipes can hold one requested set inside the pipe, so it is sent without
  travel time. It is also the prestock: without it, pipes re-request items every time.

## Design

**Scanner (`routing/astar/CorridorScanner`)**
- Along the DFS path it records the direction taken at each pipe.
- A corridor is *teleportable* only if every pipe on it is an LP pipe (`LogisticsTileGenericPipe`). It must not use
  special pipe or tile connections, an `IRouteProvider`, or a direct (ISC) connection.
- For a teleportable corridor it stores the direction list as `byte[] travelPath`; otherwise the value is `null`.
- `travelPath` goes into `EdgeSpec` / `CorridorEdge`, and is part of `sameContent`.
- A graph split drops it (`null`). A graph merge concatenates the two lists when both are set.

**Routes (`JunctionRouter.getRouteFor`)**
- Returns the `RouteLabel` that `getExitFor` would pick, so the whole edge list is available at departure.

**Clump (`transport/ItemClump`)**
- Fields: id (drawn from the traveling-item id space so client ids stay unique), the list of `LPTravelingItemServer`,
  the remaining path (`CorridorEdge[]` + index), the travel edge, depart/arrive ticks and speed.
- NBT keeps the items and the remaining ticks only. Edge ids aren't stable across restarts, so a clump loaded from disk
  arrives pathless and is routed again on arrival.

**Transport (`PTL`)**
- Server-side state per pipe:
  - `incoming`: a min-heap of clumps by arrival tick. It is stored in this pipe's NBT, so it saves and unloads with the
    chunk.
  - `departures`: the gather map from clump key to the clump that just left.
- **Departure** (`tryDepart`, called in `injectItem`/`reverseItem` after `resolveDestination`). A routed exit becomes a
  clump when all of these hold:
  - the router is a `JunctionRouter`;
  - the label's first edge leaves through that exit;
  - the edge is teleportable and has no BC pluggable on that side;
  - the target junction's pipe is loaded.

  Otherwise the item is simulated as before.
- **Arrival** (processed in `updateEntity` when the tick is due). The *fast path* applies when:
  - the pipe relays normally (`supportsFastRelay`: not entrance, ISC or fluid transport);
  - it isn't the destination;
  - the next path edge still exists, is teleportable, carries `CAN_ROUTE_TO` and leads to an active junction;
  - the destination junction is still active.

  On the fast path it updates the stats, the distance tracker, `resetDelay` and `readjustSpeed` (speed and power) per
  item, and departs to the next junction. Otherwise every item goes through `injectItem` as if it had just entered the
  pipe: the full `RouteLayer` runs and handles delivery, rerouting and drops.
- **Pipe removal** (`LogisticsBlockGenericPipe.removePipe`, before the tile goes) calls `ClumpTransit.onPipeRemoved`:
  - `ClumpTransit` indexes in-flight clumps by the chunks of their corridor. For each clump whose corridor goes through
    the removed pipe, it applies the break rule above, finding the pipe's index from the clump's own recorded path and
    source position.
  - It also flags the graph's corridors through that pipe (`isBroken`; cleared by a new edge version, or after 100 ticks).
  - `dropContents` still drops whatever is left in `incoming`, which is only clumps restored from NBT (they have no path).
- **Chunk unload:** incoming clumps are flagged detached, so the source's gather map won't add to a clump that is now on
  disk.
- The final pipe → inventory step, drops, and exits into foreign tiles (BC/TD pipes, special connections) keep the old
  per-tick simulation. So do corridors that aren't teleportable.

**Client**
- `ItemClumpPacket` carries the source pipe coordinates, clump id, speed, the direction list and up to 3 display stacks.
- It is sent only to players in the same dimension within 64 blocks of either end of the corridor.
- The client creates an `LPTravelingItemClient` with `plannedExits`. The client-side `injectItem` takes the next planned
  exit instead of `UNKNOWN`, so the item follows plain pipes without any server packet.
- The renderer draws the extra display stacks behind the first one.

**In-transit bookkeeping (`CoreRoutedPipe._inTransitToMe`)**
- Changes from a `PriorityBlockingQueue` (O(n) `contains`/`remove`) to an insertion-ordered set, indexed by item for
  `countOnRoute`.
- The timeout scan runs every 20 ticks.

**Config:** `itemClumpTransport` (default true; false falls back to the old engine) and `itemClumpGatherTicks` (default 5).

## Status (2026-09-30)

Phase 1 is implemented as described above. It compiles and the unit tests pass (`TravelPathTest` covers graph merges, versions
and `hopStillValid`; `ClumpBreakTest` covers the break rule). First in-game pass on a dedicated server (2026-09-30):
delivery, corridor following, fallbacks (firewall, one-way, ISC, entrances, BC/TD), the gather window, the client
animation and most of the break rules work. Speed upgrades have no noticeable effect, which is fine since that support
is being dropped. Found:

- B22: breaking the plain transport pipe a clump is in doesn't drop its items (routed pipes do). The clump stops
  rendering and still arrives at the next routed pipe.
- B23: after a break, items wait at the junction or in the retry buffer instead of rerouting, and wait forever when
  there's no path. They should take the new path or a default route.
- B27 (minor): a junction made of plain transport pipes still routes items to the correct exit, because the corridor
  runs straight through it. Wanted: a random nearby exit there, so routing needs routed pipes at junctions.

What's left to test (save/unload, config switch, spark) is in [testing-checklist.md](testing-checklist.md) §1.

## Later phases

1. Skip pass-through junctions: one event per run of junctions that don't touch passing items (no pluggable, not a
   destination), with the position derived from time if the run is invalidated mid-flight. Once speed comes only from
   transport controllers (see the constraints above), speed changes no longer split runs.
2. Destination assignment with one-to-many distance queries (`getDistancesTo`) instead of one query per sink.
3. Move the final delivery step onto the event queue too.
4. **Transport controller speed:** remove the speed-upgrade effect and `readjustSpeed` from transport (the upgrade itself
   is reworked in Phase 2 of [roadmap.md](roadmap.md)). Read the ticks per pipe from the network's transport controller
   tier. The instant tier delivers in the same tick along the whole path (needs phase 1).
5. **LP-only routing:** the scanner stops at foreign pipes. Remove the BC/TD transport interop and the per-tick
   simulation, then drop the `itemClumpTransport` switch.
6. **GT pipe buffers:** GT pipes attached to LP pipes act as insert/extract endpoints. Special connections (tesseracts)
   most likely get the same treatment in GTNH, with the explicit link kept for compatibility.

## Known behaviour changes

- Items no longer random-walk at branches of plain transport pipes. They follow the corridor the router picked. - tested in game, acceptable
- ~~Breaking a plain pipe in the middle of a corridor doesn't stop clumps already on it; they arrive at the far junction.~~ Reworked (see Decisions): behind → finish, the current pipe → drop, ahead → return to the source junction.
- A new, shorter route that appears while a clump is travelling isn't taken until the clump is routed again. - acceptable
- A player who walks into range while a clump is in flight sees it again once they're close enough. - tested, acceptable
- An item that joins a clump late (gather window) arrives up to `itemClumpGatherTicks` ticks early. - acceptable

- Clump items still spawn gold "energy" sparkles at each routed pipe they pass, like before (the particle fix is separate). - to be adressed

## Review notes (session 2026-09-29)

Nothing is committed. The uncommitted `RouterManager` / `JunctionRouterManager` position-index fix (load lag) from the
earlier session is in the same working tree.

**Files**

| File | Change |
|---|---|
| `transport/ItemClump.java` (new) | Clump data, gather key, travel time, NBT (items + remaining ticks), current corridor + source position + direction for `positionAt` |
| `transport/PipeTransportLogistics.java` | `tryDepart` in `injectItem`/`reverseItem`, the "clump transport" section (departure, arrival, fast relay, packets, NBT, chunk unload), clumps in `dropContents`, planned exits on the client |
| `transport/ClumpTransit.java` (new) | Chunk index of clumps in flight, `onPipeRemoved`, the break rule (`onBreak`), broken-corridor flags |
| `pipes/basic/LogisticsBlockGenericPipe.java`, `LogisticsPipes.java` | `removePipe` calls `ClumpTransit.onPipeRemoved`; `ClumpTransit.clear()` on server stop |
| `transport/LPTravelingItem.java` | static `nextId()`, client `extraStacks` + `plannedExits` |
| `transport/EntrencsTransport`, `TransportInvConnection`, `PipeFluidTransportLogistics` | `supportsFastRelay() = false` |
| `network/packets/pipe/ItemClumpPacket.java` (new) | S→C hop packet |
| `renderer/LogisticsRenderPipe.java` | `placeItem` helper split out of `renderSolids`, trailing clump stacks |
| `routing/astar/CorridorScanner.java` | direction stack + opaque-hop counter → `travelPathOf(route)` |
| `routing/astar/EdgeSpec`, `CorridorEdge` | `travelPath` (in `sameContent`), `isTeleportable()` |
| `routing/astar/JunctionGraphWriter.java` | merge concatenates travel paths; split leaves them `null` |
| `routing/astar/JunctionRouter.java` | travel paths published with corridors (and a changed path counts as an adjacency change), `PairView` keeps labels next to exit routes, `getRouteFor` |
| `routing/astar/NetworkGraph.java` | public `isActive(JunctionId)`, `hopStillValid` |
| `routing/astar/RouteLabel.java` | memoised `edgeArray()` |
| `routing/InTransitTracker.java` (new), `pipes/basic/CoreRoutedPipe.java` | replaces the `_inTransitToMe` queue; timeout scan every 20 ticks |
| `pipes/basic/LogisticsTileGenericPipe.java` | calls `transport.onChunkUnload()` |
| `config/Configs.java` | `itemClumpTransport`, `itemClumpGatherTicks` |
| `src/test/.../TravelPathTest.java` (new) | merge / opaque merge / version bump / `hopStillValid` |
| `src/test/.../transport/ClumpBreakTest.java` (new) | position from time, the break rule forwards and going back, pipe positions on a path |

**Worth a close look in review**
- `PipeTransportLogistics.clumpArrived`: the slow path calls `injectItem` for each item, which can depart the items again
  (re-clumping at this pipe). The clump is closed first, so nothing can add to it while that loop runs.
- The timing model: `travelTicks = ceil(travelPath.length / speed)`, where the length counts the pipes from the source
  junction (inclusive) to the target (exclusive). That matches the old 1/speed ticks per pipe.
- The client relies on the next hop packet arriving about when its copy reaches the junction. If it arrives early, the
  item snaps to the new pipe (the old per-pipe packets did the same).
- Restored clumps (from NBT) have no path and wait at least 20 ticks, so the routers around them are up before routing.
- `dropContents` calls `itemWasLost` for clump items. Legacy in-pipe items don't, and wait for the 640-tick timeout.
- Break handling (`corridorBroken` / `turnBack` in `PTL`, `ClumpTransit`):
  - The position comes from the clump's own recorded corridor and source position, not the current graph, so a
    corridor that was re-scanned since the clump left is still measured the way the clump travels it.
  - A clump going back is pathless and closed (nothing joins it). The client gets a reverse path from the pipe it
    turned in.
  - While a corridor is flagged broken, `tryDepart` puts items into the pipe's existing retry buffer (`_itemBuffer`,
    40 ticks) rather than simulating them into the gap. `relayEdge` falls back to routing the items, which then
    reaches the same buffer.

**Open questions for next session**
- Phase order: phase 1 of the later phases (skip pass-through junctions) versus routing at the source only once per item
  (today `RouteLayer` + `tryDepart` still do about 5 lookups per item at the pipe that sends it).
- Whether clumps should also be sent to players who arrive in range mid-flight (today they only see the next hop).
