# 04 - Item transport rewrite (clumps)

How the item transport on `crafting_rework` differs from upstream `GTNH-origin/master`. Everything here was checked
against `git diff GTNH-origin/master HEAD`. Design background: [item-transport-rewrite.md](../item-transport-rewrite.md)
(working notes) and [rework-design-decisions.md](../rework-design-decisions.md) (source of truth).

## Overview

**Upstream:** every routed item is an `LPTravelingItemServer` living in the `items` list of the pipe it is in. Each
tick every pipe moves its items by their speed (`moveSolids`). When an item reaches the pipe centre the pipe routes it
again (`RouteLayer.getOrientationForItem`), and when it reaches the pipe edge it is handed to the next pipe. The server
sends a position packet per item per pipe, so the client can draw it. Server cost grows with
items x pipes travelled.

**Now:** when a routed item leaves a junction (a routed pipe with a `JunctionRouter`, see
[03-router-rework.md](03-router-rework.md)) along a corridor made only of LP pipes, it joins an **item clump**
([ItemClump](../../src/main/java/logisticspipes/transport/ItemClump.java)). The clump is put in the incoming queue of
the junction at the other end and arrives there after `ceil(pipes / speed)` ticks. Nothing simulates it in between.
The route is looked up once, at departure. Intermediate junctions only check that the next corridor still exists
(fast relay). The client gets one [ItemClumpPacket](../../src/main/java/logisticspipes/network/packets/pipe/ItemClumpPacket.java)
per clump per hop with the pipe-by-pipe directions, and animates the items itself.

Everything the clump path can't handle still uses the upstream per-tick simulation: the last step from a pipe into an
inventory, drops, exits into BC/TD pipes or special connections, non-teleportable corridors, entrances, inventory
system connectors and fluid pipes. The config switch `itemClumpTransport=false` turns clumps off completely.

The design goal "no traveling item entities" is therefore only partly reached: items in corridors are simulated as
clumps, but the per-tick engine is still there for the cases above. Pipe speed is **still** computed from speed
upgrades and power (`readjustSpeed`); the transport-controller speed model is a later phase.

## Features

### Clump departure

**What:** a routed item that is about to leave a junction along a teleportable corridor becomes part of a clump
instead of being moved block by block.

**How** ([PipeTransportLogistics](../../src/main/java/logisticspipes/transport/PipeTransportLogistics.java),
"clump transport" section):

- `injectItem` (after `resolveDestination` and `readjustSpeed`) and `reverseItem` call `tryDepart(item)`.
- `tryDepart` returns false (old simulation) unless all of these hold:
  - `Configs.ITEM_CLUMP_TRANSPORT` is on, the transport is routed, the item has a destination and a known exit;
  - the pipe's router is a `JunctionRouter` and there is no BC pluggable on the exit side;
  - `JunctionRouter.getRouteFor(destination, active, item)` returns a route whose first corridor leaves through that
    exit;
  - `NetworkGraph.hopStillValid(path[0])` accepts the first corridor (it must be teleportable, i.e. have a
    `travelPath`).
- If the first corridor is flagged broken (see "Pipe breaks under a clump"), the item goes into the pipe's retry
  buffer `_itemBuffer` instead.
- `sendAlong` builds an `ItemClump.Key` (destination, transport mode, remaining edge ids). If a clump with the same key
  left this pipe at most `itemClumpGatherTicks` ticks ago and isn't closed, the item joins it (it then arrives up to
  that many ticks early). Otherwise a new clump is created with an id from `LPTravelingItem.nextId()` (shared id space,
  so client ids never collide) and the speed of its first item.
- `startHop` sets the corridor, depart/arrival ticks, `ticksPerPipe` and the source position, puts the clump into the
  target junction's `incomingClumps` heap (`receiveClump`), registers it in `ClumpTransit` and sends the client packet
  (unless the source pipe is opaque).

Travel time is `ItemClump.travelTicks(travelPath.length, speed)` = `max(1, ceil(pipes / speed))`, the same 1/speed
ticks per pipe as upstream.

### Clump arrival and fast relay

**What:** a junction takes in clumps whose arrival tick has come. A clump that only passes through goes on to the next
junction without routing its items again.

**How:** `PipeTransportLogistics.updateEntity` calls `tickClumps()` on the server. It restores clumps read from NBT,
expires gather entries in `clumpDepartures`, and polls every clump with `arrivalTick <= now` from the heap
(`ItemClump.BY_ARRIVAL`). `clumpArrived`:

- `relayEdge` decides if the fast path applies: there is a next corridor on the stored path, the transport
  `supportsFastRelay()` (false for [EntrencsTransport](../../src/main/java/logisticspipes/transport/EntrencsTransport.java),
  [TransportInvConnection](../../src/main/java/logisticspipes/transport/TransportInvConnection.java) and
  [PipeFluidTransportLogistics](../../src/main/java/logisticspipes/transport/PipeFluidTransportLogistics.java)), the
  pipe is initialised, its router is the `JunctionRouter` the next corridor starts at, there's no BC pluggable on that
  exit, the destination junction is active and its router exists, and the next corridor is still valid and not
  flagged broken.
- Fast path: per item `readjustSpeed` (speed upgrades and power, as upstream), `resetDelay`, the distance tracker is
  set to the remaining block distance; `relayedItem(count)` updates the pipe stats; the clump takes the highest item
  speed. It then merges into a same-key clump that just left (gather window) or starts the next hop.
- Slow path (destination reached, path invalid, target unloaded, restored clump without a path): every item is put
  back to position 0 and goes through `injectItem` as if it had just entered the pipe, so the full `RouteLayer`
  handles delivery, rerouting and drops. Items can depart again from there as new clumps.

### Pipe breaks under a clump in flight

**What:** removing a pipe from a corridor that a clump is crossing applies a rule based on where the clump is, which is
known from time.

**How** ([ClumpTransit](../../src/main/java/logisticspipes/transport/ClumpTransit.java)):

- `ClumpTransit` keeps a static index of clumps in flight by the chunk keys of their corridor (`CorridorEdge.chunks`),
  filled by `register`/`unregister`.
- [LogisticsBlockGenericPipe](../../src/main/java/logisticspipes/pipes/basic/LogisticsBlockGenericPipe.java)`.removePipe`
  calls `ClumpTransit.onPipeRemoved(world, x, y, z)` before the tile is removed. It:
  - flags every graph corridor through that block as broken (`BrokenMark`: edge version + 100 ticks). `isBroken`
    clears the mark when the edge is re-scanned (new version) or the 100 ticks pass;
  - for every clump indexed in that chunk whose own recorded corridor (`clump.edge.travelPath` from `srcX/Y/Z`) passes
    the block, calls `holder.corridorBroken(clump, index, now)`.
- `ItemClump.positionAt(now)` gives the position in pipes; `ClumpTransit.onBreak` decides:
  - break **behind** the clump: `FINISH` (nothing happens);
  - break in the pipe it is **in**: `DROP` (items drop at that pipe, `itemWasLost` is called);
  - break **ahead** (target junction included): `TURN_BACK`; a clump that is already going back drops instead of
    bouncing again.
- `PipeTransportLogistics.turnBack` moves the clump to the source junction's incoming queue, pathless and closed,
  with `returning = true`, and sends the client a reversed path from the pipe it turned in. If the source junction is
  gone or unrouted, the items drop where the clump is.
- `ClumpTransit.clear()` is called on server stop (`LogisticsPipes.cleanup`).

**Tests:** [ClumpBreakTest](../../src/test/java/logisticspipes/transport/ClumpBreakTest.java) builds a straight
corridor with `JunctionGraphWriter` and checks position from time (with clamping), the forward rule (behind / current /
ahead / target), a clump still in its source pipe, the returning-clump rule, and `indexOnPath`/`pipeAt` on a path that
turns upwards. `TravelPathTest` (graph merges of travel paths, `hopStillValid`) belongs to the router area, see
[03-router-rework.md](03-router-rework.md).

### Save, chunk unload and old worlds

- **NBT:** a junction pipe writes its `incomingClumps` under the new tag `incomingClumps` in its transport NBT
  (`writeClumps`). Per clump only `remaining` ticks, `travelDirection`, `speed` and the items are stored; each item
  keeps its full routing information through `LPTravelingItemServer.writeToNBT`. Edge ids aren't stable across
  restarts, so the path is dropped.
- **Load:** `readFromNBT` keeps the tag list in `pendingClumpTags` until the first tick (the world isn't known yet).
  `restoreClumps` then creates closed, pathless clumps that arrive at least 20 ticks later
  (`RESTORED_CLUMP_MIN_DELAY`), so the routers around them are up. On arrival they take the slow path.
- **Chunk unload:** [LogisticsTileGenericPipe](../../src/main/java/logisticspipes/pipes/basic/LogisticsTileGenericPipe.java)`.onChunkUnload`
  calls `transport.onChunkUnload()`, which closes and unregisters the incoming clumps, so the source's gather map
  can't add items to a clump that is now on disk.
- **Pipe broken with clumps queued:** `dropContents` also drops the items of `incomingClumps` (restored first if still
  pending) and calls `itemWasLost` for them, so requesters re-request.
- **Old worlds:** items saved in transit by upstream (`travelingEntities`) still load and are simulated the old way;
  when they reach a junction they may depart as clumps. Nothing is migrated. Going back to upstream LP after saving
  with clumps in flight loses those items, because upstream ignores the `incomingClumps` tag.

### Client sync and rendering of clumps

- [ItemClumpPacket](../../src/main/java/logisticspipes/network/packets/pipe/ItemClumpPacket.java) (new, server to
  client): source pipe coordinates, clump id, speed, input side, the exit direction at each pipe (`byte[] path`) and up
  to 3 display stacks (`MAX_DISPLAY_STACKS`). `sendClumpPacket` sends it only to players in the same world within 64
  blocks (`CLUMP_VIEW_DISTANCE`) of either end of the corridor.
- `handleItemClumpPacket` reuses or creates an `LPTravelingItemClient` with that id, removes it from the pipe it was in,
  and fills its new fields from [LPTravelingItem](../../src/main/java/logisticspipes/transport/LPTravelingItem.java):
  `extraStacks` (the 2nd and 3rd stack) and `plannedExits` (the directions after the first pipe). On the client
  `injectItem` takes the next planned exit instead of `UNKNOWN`, so the item follows plain pipes without more packets.
- [LogisticsRenderPipe](../../src/main/java/logisticspipes/renderer/LogisticsRenderPipe.java): the item placement code
  of `renderSolids` moved into `placeItem`; extra clump stacks are drawn behind the first one, `CLUMP_SPACING` = 0.2
  pipe lengths apart, within the current pipe only.

### Pipe body baked into chunks (rendering performance)

Not clump-specific, but part of the same "improve fps and tps" work:

- **New renderer:** [LogisticsNewRenderPipe](../../src/main/java/logisticspipes/renderer/newpipe/LogisticsNewRenderPipe.java)
  `renderTileEntityAt` (per-frame TESR drawing from a per-pipe `VBOList` display list, with a distance cut-off and a
  fallback to the old model) is replaced by `renderWorldBlock`, which emits the cached model into the chunk mesh with
  an `LPTranslation` to world coordinates. [LogisticsNewPipeWorldRenderer](../../src/main/java/logisticspipes/renderer/newpipe/LogisticsNewPipeWorldRenderer.java)
  calls it in render pass 0.
- **TESR only for dynamic content:** `LogisticsRenderPipe.renderTileEntityAt` now returns early unless
  `hasDynamicContent`: pipe signs (`CoreRoutedPipe.hasPipeSigns`, new), BC wires/gates with dynamic renderers
  (`BCRenderTESR.hasDynamicContent`, outside this area), or, for non-opaque pipes, items, buffered items
  (`LPItemList.isEmpty`, new) or fluid.
- Removed: `PipeRenderState.forceRenderOldPipe`, `buffer` and `renderList`; the matching checks in
  [LogisticsPipeWorldRenderer](../../src/main/java/logisticspipes/renderer/LogisticsPipeWorldRenderer.java) and
  [CoreUnroutedPipe](../../src/main/java/logisticspipes/pipes/basic/CoreUnroutedPipe.java). The player config options
  `useVBORenderer`, `useFallbackRenderer` and `renderPipeDistance` were removed in `PlayerConfig` (outside this area).

### Pipe speed command

[PipeSpeedCommand](../../src/main/java/logisticspipes/commands/commands/PipeSpeedCommand.java) (new, registered in
`MainCommandHandler`): `/lp pipespeed [speed]` (alias `ps`), OP only. Without an argument it prints
`LPConstants.PIPE_NORMAL_SPEED`; with a positive finite number it sets it. `PIPE_NORMAL_SPEED` was made non-final for
this. The value scales the target speed in `readjustSpeed`, so it changes clump travel time too. It is global, not
saved, and is reset to 0.01 on restart.

### In-transit bookkeeping

[CoreRoutedPipe](../../src/main/java/logisticspipes/pipes/basic/CoreRoutedPipe.java)`._inTransitToMe` changes from a
`PriorityBlockingQueue` (O(n) `contains`/`remove`/count) to `InTransitTracker` (outside this area): `countOnRoute`
uses `_inTransitToMe.count(item)`, and the timed-out scan (`removeTimedOut`) runs every 20 ticks instead of every
tick.

### Config

In `Configs` (outside this area), category `general`:

| Key | Default | Meaning |
|---|---|---|
| `itemClumpTransport` | `true` | `false` restores the upstream per-tick transport. |
| `itemClumpGatherTicks` | `5` (0-40) | How long after a clump left an item going the same way may still join it. |

## Changes to upstream classes

- **PipeTransportLogistics** (+528/-10): the clump section (departure, arrival, fast relay, break handling, packets,
  NBT, chunk unload), `tryDepart` hooks in `injectItem`/`reverseItem`, client planned exits, clump items in
  `dropContents`. Non-transport changes in the same file:
  - `resolveDestination`: a stack sent to the retry buffer first releases its destination's in-transit reservation
    (`releaseReservation` -> `notifyOfReroute`); when retries run out, `assignDefaultRouteFor` is tried before the
    item drops (bug-list B19/B20, routing: [03-router-rework.md](03-router-rework.md)).
  - `handleTileReachedServer`: pattern crafting pipes reverse items instead of inserting them
    ([01-pattern-crafting.md](01-pattern-crafting.md)); chassis modules with their own inventory view
    (`getTargetModuleInventory`, Electric Manager on GT battery slots) insert there; on a failed insert the module's
    `insertionFailed` is called so sinks stop advertising
    ([06-modules-pipes-compat-build.md](06-modules-pipes-compat-build.md)).
- **CoreRoutedPipe** (+124/-42): `InTransitTracker` and 20-tick timeout scan (transport); `hasPipeSigns` (rendering).
  Other areas: queued sends now count as in transit for the destination (`addToSendQueue`/`trackQueuedSend`,
  `refreshSendQueueTracking`, `clearSendQueue`, new `notifyOfRerouteTo`) and `getDestinationPipe` helper
  ([01-pattern-crafting.md](01-pattern-crafting.md)); the debug dump supports `JunctionRouter`, `InterestRegistry`,
  `RouterIds` ([03-router-rework.md](03-router-rework.md)); a non-legacy wrench opens the ModularUI GUI of
  `IMUICompatiblePipeV2` pipes ([05-modularui-gui.md](05-modularui-gui.md)).
- **CoreUnroutedPipe** (+9/-4): renderer-state check without `forceRenderOldPipe` (rendering); the dummy upgrade
  manager gets `getUpgradeInventory()` returning an empty `ItemStackHandler` ([05-modularui-gui.md](05-modularui-gui.md)).
- **LogisticsBlockGenericPipe** (+7/-0): `ClumpTransit.onPipeRemoved` in `removePipe`.
- **LogisticsTileGenericPipe** (+35/-7): `transport.onChunkUnload()` (transport). Other areas: pattern crafting pipes
  render a connection to their crafting target without a real transport connection (`shouldRenderPipeConnection`) and
  reject passive `injectItem` from external APIs ([01-pattern-crafting.md](01-pattern-crafting.md)); `buildUI`
  supports `IMUICompatiblePipeV2` and registers the save-on-close listener for both GUI kinds
  ([05-modularui-gui.md](05-modularui-gui.md)).
- **FluidRoutedPipe** (+4/-0): protected constructor taking a custom `PipeFluidTransportLogistics`, used by
  `PipeItemsPatternCraftingLogistics` ([01-pattern-crafting.md](01-pattern-crafting.md)).
- **EntrencsTransport, TransportInvConnection, PipeFluidTransportLogistics** (+5 each): `supportsFastRelay() = false`.
- **LPItemList** (+4): `isEmpty()`, used by the TESR skip.
- **LPTravelingItem** (+10): static `nextId()`; client `extraStacks` and `plannedExits`.
- **LogisticsRenderPipe** (+97/-34), **LogisticsNewRenderPipe** (+22/-78), **LogisticsNewPipeWorldRenderer** (+7),
  **LogisticsPipeWorldRenderer** (+1/-1), **PipeRenderState** (-4): clump trail rendering and chunk-baked pipe bodies
  (see above).
- **TravelingItemRenderer** (+1/-1): the new request table block item is drawn at half scale like the old one
  ([02-request-table.md](02-request-table.md)).

## Known gaps / discrepancies

- **Speed still comes from upgrades.** `readjustSpeed` (speed upgrades + power) still sets item and clump speed, at
  departure and at every fast relay. The design ("speed no longer controlled by upgrades", transport controllers) is not
  implemented yet; [item-transport-rewrite.md](../item-transport-rewrite.md) lists it as later phase 4. In-game
  testing found speed upgrades to have no noticeable effect.
- **Per-tick simulation is still there** for the last step into inventories, non-teleportable corridors (foreign
  pipes, special connections, ISC), entrances, inventory connectors and fluid pipes. BC/TD interop is not removed.
- **A clump's speed is its first item's speed**; items that join later in the gather window don't change it.
- **Open bugs** ([bug-list.md](../bug-list.md)): B22 (breaking the plain transport pipe a clump is in doesn't drop its
  items, routed pipes do), B23 (after a break items wait at the junction or in the retry buffer instead of rerouting,
  forever if there's no path), B27 (junctions made of plain transport pipes still route correctly; a random exit is
  wanted). Clump items still spawn the gold particle at every routed pipe they pass.
- **Clients:** a player who comes into range mid-flight sees the clump only from its next hop. The client relies on
  the next hop packet arriving about when its copy reaches the junction; an early packet makes the item snap.
- **`/lp pipespeed`** is a global, unsaved runtime override of `PIPE_NORMAL_SPEED`, a tuning tool rather than part
  of the planned speed model.
- **Downgrade:** saving with clumps in flight and loading with upstream LP loses those items (the `incomingClumps` tag
  is ignored).
- **Stale doc text:** the "Review notes" in [item-transport-rewrite.md](../item-transport-rewrite.md) say nothing is
  committed; the work is committed now.
- [pipe-hibernation.md](../pipe-hibernation.md) is an idea only; nothing of it is implemented.

## Files

Status: A = added, M = modified (against `GTNH-origin/master`).

### docs

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [item-transport-rewrite.md](../item-transport-rewrite.md) | A | +228/-0 | Design, decisions, status and review notes of the clump transport |
| [pipe-hibernation.md](../pipe-hibernation.md) | A | +75/-0 | Idea for idle pipes skipping ticks; not implemented |

### logisticspipes.commands.commands

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [PipeSpeedCommand](../../src/main/java/logisticspipes/commands/commands/PipeSpeedCommand.java) | A | +54/-0 | `/lp pipespeed [speed]`: show or set `PIPE_NORMAL_SPEED` at runtime (OP) |

### logisticspipes.network.packets.pipe

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [ItemClumpPacket](../../src/main/java/logisticspipes/network/packets/pipe/ItemClumpPacket.java) | A | +90/-0 | Server-to-client hop of a clump: id, speed, input, per-pipe directions, up to 3 stacks |

### logisticspipes.pipes.basic

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [CoreRoutedPipe](../../src/main/java/logisticspipes/pipes/basic/CoreRoutedPipe.java) | M | +124/-42 | `InTransitTracker`, 20-tick timeout scan, `hasPipeSigns`; queued-send tracking (01), junction debug (03), MUI wrench GUI (05) |
| [CoreUnroutedPipe](../../src/main/java/logisticspipes/pipes/basic/CoreUnroutedPipe.java) | M | +9/-4 | Drops `forceRenderOldPipe` check; empty upgrade inventory for MUI (05) |
| [LogisticsBlockGenericPipe](../../src/main/java/logisticspipes/pipes/basic/LogisticsBlockGenericPipe.java) | M | +7/-0 | `removePipe` calls `ClumpTransit.onPipeRemoved` |
| [LogisticsTileGenericPipe](../../src/main/java/logisticspipes/pipes/basic/LogisticsTileGenericPipe.java) | M | +35/-7 | Transport `onChunkUnload`; pattern crafter render connection and inject block (01); `IMUICompatiblePipeV2` UI (05) |
| [FluidRoutedPipe](../../src/main/java/logisticspipes/pipes/basic/fluid/FluidRoutedPipe.java) | M | +4/-0 | Constructor with custom fluid transport, for the pattern crafting pipe (01) |

### logisticspipes.renderer

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [LogisticsPipeWorldRenderer](../../src/main/java/logisticspipes/renderer/LogisticsPipeWorldRenderer.java) | M | +1/-1 | New renderer no longer falls back via `forceRenderOldPipe` |
| [LogisticsRenderPipe](../../src/main/java/logisticspipes/renderer/LogisticsRenderPipe.java) | M | +97/-34 | TESR only for dynamic content; `placeItem` helper; trailing clump stacks |
| [TravelingItemRenderer](../../src/main/java/logisticspipes/renderer/TravelingItemRenderer.java) | M | +1/-1 | Half scale for the new request table item too (02) |
| [LogisticsNewPipeWorldRenderer](../../src/main/java/logisticspipes/renderer/newpipe/LogisticsNewPipeWorldRenderer.java) | M | +7/-0 | Bakes the new-model pipe body in render pass 0 |
| [LogisticsNewRenderPipe](../../src/main/java/logisticspipes/renderer/newpipe/LogisticsNewRenderPipe.java) | M | +22/-78 | Per-frame VBO TESR replaced by chunk-mesh `renderWorldBlock` |
| [PipeRenderState](../../src/main/java/logisticspipes/renderer/state/PipeRenderState.java) | M | +0/-4 | Removes `forceRenderOldPipe`, `buffer`, `renderList` |

### logisticspipes.transport

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [ClumpTransit](../../src/main/java/logisticspipes/transport/ClumpTransit.java) | A | +194/-0 | Chunk index of clumps in flight, pipe-break rule, broken-corridor flags |
| [EntrencsTransport](../../src/main/java/logisticspipes/transport/EntrencsTransport.java) | M | +5/-0 | `supportsFastRelay() = false` |
| [ItemClump](../../src/main/java/logisticspipes/transport/ItemClump.java) | A | +159/-0 | Clump data, gather key, travel time, position from time, NBT |
| [LPItemList](../../src/main/java/logisticspipes/transport/LPItemList.java) | M | +4/-0 | `isEmpty()` |
| [LPTravelingItem](../../src/main/java/logisticspipes/transport/LPTravelingItem.java) | M | +10/-0 | Static `nextId()`; client `extraStacks`, `plannedExits` |
| [PipeFluidTransportLogistics](../../src/main/java/logisticspipes/transport/PipeFluidTransportLogistics.java) | M | +5/-0 | `supportsFastRelay() = false` |
| [PipeTransportLogistics](../../src/main/java/logisticspipes/transport/PipeTransportLogistics.java) | M | +528/-10 | Clump departure/arrival/relay/break/NBT/packets; retry-buffer and default-route fallback (03); module insert hooks (01, 06) |
| [TransportInvConnection](../../src/main/java/logisticspipes/transport/TransportInvConnection.java) | M | +5/-0 | `supportsFastRelay() = false` |

### Tests

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [ClumpBreakTest](../../src/test/java/logisticspipes/transport/ClumpBreakTest.java) | A | +96/-0 | Position from time, break rule forwards and returning, path index helpers |
