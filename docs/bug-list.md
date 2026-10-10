# Bug list

Known bugs, in one place. Add new ones at the end of the matching section, with the next free ID.

This branch only carries the router rework and the item transport rewrite, so only routing, transport, rendering and
performance bugs are listed here. Crafting and GUI bugs live with those parts of the rework.
Things to verify after a fix go in [testing-checklist.md](testing-checklist.md).

**Status:** `open` · `needs repro` (reported or found by reading code, not reproduced in game) · `possibly fixed`
(a fix exists, not confirmed in game) · `fixed` (move to the Fixed section with the commit or date)

Template:

```
### B<n>: <short title>
- **Status:** open
- **Found:** <date>, <in game / by reading code>
- **Where:** <feature, pipe, or file>
- **Repro:** <steps>
- **Notes:** <expected vs. actual, suspected cause>
```

---

## Routing and item transport

### B1: Bounced items lose their jam list when they have nowhere to go
- **Status:** needs repro
- **Found:** 2026-09-30, by reading code
- **Where:** `PipeTransportLogistics.resolveRoutedDestination` / `updateEntity` (item buffer)
- **Notes:** An item with no destination is buffered without its routing info (`Triplet` value 3 is `null`). On retry a
  fresh item is created, so the jam list with the sinks that already rejected it is lost. It can be sent back to a sink
  that just bounced it. The full-sink room cache limits this but doesn't remove it.

### B2: Basic pipe touching several inventories only checks the first one for room
- **Status:** open (known limitation)
- **Found:** 2026-09-30, by reading code
- **Where:** `ModuleItemSink.targetInventory()` → `PipeItemsBasicLogistics.getPointedInventory`
- **Notes:** Delivery picks a random adjacent inventory, but the room check only looks at the first one found. The pipe
  can claim room it doesn't have, or skip room it has. A failed insert marks the item as full and reroutes it, so it
  doesn't loop.
- **In game (2026-09-30):** no loops, but the pipe still pushes items into the other adjacent inventories (and
  succeeds). Wanted: make the pipe's connections configurable with shift+wrench, like the pattern crafting pipe, so it
  only touches the chosen inventory.

### B3: `PipeContentRequest` can never be answered
- **Status:** needs repro
- **Found:** 2026-09-29, by reading code ([lag-investigation.md](lag-investigation.md) §1.3)
- **Where:** `LPTravelingItem.serverList`, `PipeContentRequest:22`
- **Notes:** `serverList` is never filled, so a client asking for an unknown item's contents gets no reply.

### B4: Item packets go to players in other dimensions
- **Status:** needs repro
- **Found:** 2026-09-29, by reading code ([lag-investigation.md](lag-investigation.md) §1.3)
- **Where:** `MainProxy.isAnyoneWatching`
- **Notes:** The check ignores the dimension.

### B5: Client-known item IDs are tracked globally, not per player
- **Status:** needs repro
- **Found:** 2026-09-29, by reading code ([lag-investigation.md](lag-investigation.md) §1.3)
- **Where:** `LPTravelingItem.clientSideKnownIDs`
- **Notes:** One global sliding bitset of 2^20 IDs. A player who joins later may never get the full item contents
  packet for an ID another player already received.

### B22: Breaking a plain transport pipe under a clump doesn't drop its items
- **Status:** open
- **Found:** 2026-09-30, in game (dedicated server)
- **Where:** `ClumpTransit.onPipeRemoved` / `onBreak`
- **Repro:** send a clump down a corridor of basic transport pipes, break the transport pipe it is in right now.
- **Notes:** Works for routed pipes (the items drop there). For plain transport pipes the break is ignored: the clump
  stops rendering, keeps going, and arrives at the next routed pipe, where it renders again. No items are lost.
  Expected: drop the items at the broken pipe, as with routed pipes.

### B23: Items wait at a junction after a corridor break instead of rerouting
- **Status:** open
- **Found:** 2026-09-30, in game (dedicated server)
- **Where:** `ClumpTransit` (turn back, broken-corridor flag), the pipe's retry buffer
- **Repro:** 1) break a pipe ahead of a clump in flight: it turns back to its source junction, sits there for a while,
  then is routed around the gap. If there's no other path to the destination it stays in the junction pipe forever.
  2) While the corridor is flagged broken, new items sent that way wait in the retry buffer until the corridor is
  re-scanned (or 100 ticks).
- **Notes:** Expected: route along the new path right away, and go to a default route when the destination can't be
  reached. Items should never be stuck in a junction indefinitely.

### B24: Full AE interfaces keep being advertised as a destination
- **Status:** open
- **Found:** 2026-09-30, in game
- **Where:** `ModuleItemSink` room cache, special inventory handlers (AE interface)
- **Notes:** Drawers and barrels stop advertising when full. An AE interface behind a sink keeps being chosen after it
  fills up. Likely its inventory handler reports room it doesn't have.

### B27: Junctions made of plain transport pipes route items like routed pipes
- **Status:** open (minor, wanted for flavour)
- **Found:** 2026-09-30, in game
- **Where:** `CorridorScanner` / `ClumpTransit` (corridors run through plain transport pipe junctions)
- **Notes:** An item passing a junction built from plain (unrouted) transport pipes still takes the correct exit, as if
  the junction were a routed pipe. Not critical. Wanted: at a plain transport pipe junction the item takes a random
  nearby exit, so routing needs routed pipes at the junctions.

## Rendering

### B7: Pipe model doesn't update when a neighbour's shape changes
- **Status:** needs repro
- **Found:** 2026-09-30, by reading code
- **Where:** `LogisticsNewRenderPipe.fillObjectsToRenderList` (`sideNormal` scaling), `PipeRenderState.cachedRenderer`
- **Notes:** Connected sides are stretched to the neighbour block's bounds. The cached parts list is only cleared when
  the connection/texture state or the solid sides change. A neighbour that changes shape without changing either
  (e.g. one non-solid block replaced by another with different bounds) keeps the old stretch until something else
  invalidates the cache.

### B30: The LAN host's render config overrides other clients' config
- **Status:** needs repro
- **Found:** before 2026-09-29 (project board, moved from the old `TODO/rework.md`)
- **Where:** render config sync
- **Repro:** open a world to LAN, join from a second client with different LP render settings.
- **Notes:** The world owner's render settings apply to the clients that joined. Render settings should stay
  per client.

## Performance

### B8: Particles cause server and client lag when enabled
- **Status:** open (workaround: `enableParticleFX=false` in the server config)
- **Found:** 2026-09-30, in game
- **Where:** `CoreRoutedPipe.spawnParticleTick`, `PipeFXRenderHandler`
- **Notes:** Every pipe that spawned a particle sends its own `ParticleFX` packet every tick to every player watching
  the chunk. Fix idea: throttle per pipe, or batch per chunk and tick.

### B9: Full sink modules other than the item sink stay registered as destinations
- **Status:** open
- **Found:** 2026-09-30, by reading code
- **Where:** ore dict, mod-based, polymorphic, creative tab, enchantment and other sink modules
- **Notes:** Only `ModuleItemSink` drops its interests when its inventory is full. The others are still found by every
  destination search. On chassis, `ChassiModule`'s room check still rejects the item, but only after a distance query and
  a `countOnRoute` scan.

### B10: Loading a large pipe network lags the client and server
- **Status:** possibly fixed (2026-09-28)
- **Found:** in game ([pattern-crafting.md](pattern-crafting.md) §10)
- **Where:** `RouterManager`, `JunctionRouterManager`
- **Notes:** `getOrCreateRouter` scanned every known router for each pipe that loaded, O(n²). Both managers use a
  position index now. Confirm with spark.

### B32: Placing a pipe on a large network causes a short, severe lag spike
- **Status:** needs repro (may be fixed by the junction-graph router)
- **Found:** before 2026-09-29 (project board, moved from the old `TODO/rework.md`)
- **Where:** routing table rebuild on a network change
- **Repro:** on a large network, place any pipe other than a plain transport pipe.
- **Notes:** Reported before the junction-graph router ([router-rework/](router-rework/)), which updates routes
  incrementally. Check with spark whether it still happens. Worth fixing before the request handler work
  ([roadmap.md](roadmap.md) Phase 1), which puts more load on routing.

## Fixed

Move entries here when they're fixed, with the commit or date.

### B6: Items in flight drop when the network loses power
- **Status:** not reproduced (2026-09-30): in game the items in flight are still delivered, which is acceptable
- **Found:** 2026-09-30, by reading code
- **Where:** `PipeItemsBasicLogistics.getTransportLayer().stillWantItem`, `ChassiTransportLayer.stillWantItem`
- **Notes:** `sinksItem` checks `canUseEnergy`. If power runs out while items are in flight, they are rerouted, find
  nothing, wait about 10 s and drop. Basic pipes used to deliver them anyway.

### B18: Some pipes stay unpowered after joining a powered and an unpowered island
- **Status:** fixed (2026-09-30), tested in game
- **Found:** 2026-09-30, in game
- **Repro:** 1) build a small network with a power junction attached 2) build another network nearby, don't power it
  3) connect both with a transport/basic pipe. Some pipes in the unpowered part show as disconnected/unpowered.
- **Notes:** Those pipes are skipped by routing or don't work at all, and render wrong.
- **Cause:** Placing the joining pipe makes both end routers publish their corridors separately, so for a moment the
  graph has the islands merged but only a one-way corridor. A pipe that polls power in that window (every 10 ticks)
  caches "no route to the power junction". Two things then kept that answer:
  - `ValidatedRoutes.isTraversable` counted an "unreachable" entry as traversable, because it has no corridors to
    check. The engine served it stale.
  - `JunctionRouter.powerView()` pinned the stale answer to the new graph snapshot. The fast path returned "unpowered"
    until the graph changed again, which a quiet network never does.
- **Fix:** `isTraversable` returns false for entries with no corridors, so "unreachable" is recomputed instead of
  served stale. `powerView()` only ties its view to a snapshot when all its routes are valid on it. Regression test:
  `RouteCacheTest.unreachableIsNeverServedStaleAfterAMerge`.

### B19: Items left over after an inventory fills stay in the pipe buffer instead of going to the next sink
- **Status:** fixed (2026-09-30), tested in game, same cause as B18. The leftover (bounced items go to the
  default route) was B20
- **Found:** 2026-09-30, in game
- **Repro:** 1) place 2 or more small inventories (tested with GT lead chests) and connect them with basic pipes 2) push
  some items into the network 3) the first inventory fills up, and the rest of the clump stays in that pipe's buffer
  instead of being rerouted to the next pipe.
- **Notes:**The reroute itself works: the bounced item puts the full pipe on its jam list and asks for a new
  destination. It finds none because the other sinks answer "no" from `canUseEnergy`. Building the network by placing
  pipes goes through the same half-merged state as B18, so those pipes could be pinned as unpowered. With no
  destination, the items wait in the buffer (about 10 s) and are then dropped.
- While the fix works, the bounced items get returned to the default route without checking if there are other basic pipes that can accept this item, this makes large storage networks to fill up chests very slowly
- **Check after the fix:** if items still stay in the buffer with every pipe powered, the cause is something else.
  Note the pipe layout: does the next pipe touch the full chest too, and is it a default route or filtered?

### B20: Bounced items go to the default route while filtered sinks still have room
- **Status:** fixed (2026-09-30), tested in game
- **Found:** 2026-09-30, in game (while retesting B19)
- **Repro:** extractor pipe, several small chests on basic pipes with a filter for the item (one chest per pipe), and a
  separate default route. Items that bounce off the first full chest go to the default route, while newly extracted
  items keep filling the other chests.
- **Cause:** Nothing reserved room for items in flight:
  - `ModuleItemSink` only checked "room for 1 more" and ignored items already on their way. The extractor kept aiming
    fresh items at the next small chest, and bounced items, which had further to travel, arrived after it filled.
  - Rerouted items were never counted as in transit to their new destination. `assignDestinationFor` set the
    destination without `notifyOfSend`, so even `ChassiModule`'s in-transit check missed them.
  - Each extra bounce put that chest on the item's jam list. After a few, every filtered sink was excluded and only
    the default route was left.
- **Fix:** `ModuleItemSink` subtracts `countOnRoute` from the room and limits its reply to what's left, as
  `ChassiModule` does. `assignDestinationFor` registers a rerouted item as in transit to its new destination.

### B21: Bounced items sometimes fail to reroute and drop into world
- **Status:** fixed (2026-09-30), tested in game (grid and default-route fallback)
- **Found:** 2026-09-30, in game (while testing B20)
- **Repro:** 1) Place a lot of basic pipes with chests attached (tested with 16x16 grid), 2) extract items into the network. Chest will fill up, some items will bounce and try to reroute, but eventually some won't be able to find a router and drop
- **Suggested fix (if nothing else works)** make a last effort check for basic pipes, if there's no destination to reroute after 5 tries, try to reroute to default route, if even that fails only then drop items
- **Likely cause:** leftover in-transit reservations. When a reroute assigns a destination but `RouteLayer` then finds
  no exit (routes changing while many items reroute), `resolveRoutedDestination` buffers the stack without its routing
  info. The destination keeps counting it as in transit (new since B20) until the 640-tick (32 s) timeout. In a busy
  grid these phantom reservations make sinks look full, and bounced items run out of their 5 retries (~10 s) and drop.
- **Fix (2026-09-30):**
  - A stack buffered without its routing info now releases its destination's reservation first.
  - The suggested fallback above, for all routed pipes: when an item is out of retries, it goes to the nearest default
    route that has physical room (ignoring room promised to items in transit). It only drops if there's none
    (`LogisticsManager.assignDefaultRouteFor`).
