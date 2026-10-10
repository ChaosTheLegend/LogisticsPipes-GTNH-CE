# Lag investigation (2026-09-29)

Found by reading the code. The main claims were checked against the source, but none of this was profiled; confirm the ranking with
spark in game. Paths are relative to `src/main/java/logisticspipes/`. Line numbers drift, so re-grep them.
`PTL` = `transport/PipeTransportLogistics.java`, `CRP` = `pipes/basic/CoreRoutedPipe.java`.

Scale target: ~50k pipes, and routing must stay well under one 50 ms tick.

The rework design ([rework-design-decisions.md](rework-design-decisions.md)) removes some of these costs outright:
- Only LP pipes route and carry items, so the reflection adapters for other mods' pipes (§1.8) go away.
- Travel speed comes from transport controller blocks instead of speed upgrades, so the per-hop `readjustSpeed` / power
  charge in §1 goes away. The instant top tier turns a trip into a single event.

---

## 1. TPS lag from items traveling in pipes

The engine does all of its routing work at every routed pipe, for every item, on every hop, and it sends packets each time.
One hop takes 3–5 ticks per item at the default speed (`PIPE_NORMAL_SPEED` 0.01 × boost 20/25/30, `PTL:437-468`), and down
to 1 tick with speed upgrades. So N items in flight make N/5 to N hops per tick. `JunctionRouterManager` is the active
router manager (`LogisticsPipes.java:316`). No mixin or ASM code touches transport.

Causes, ranked by likely impact:

1. **Routing is redone at every hop.**
   - Path: `injectItem` (`PTL:187-225`) → `resolveRoutedDestination` (`PTL:312-375`) → `RouteLayer.getOrientationForItem`
     (`logisticspipes/RouteLayer.java:30-98`).
   - Each hop makes 3 `hasRoute` calls (`PTL:331`, `RouteLayer:40`, `:77`) plus a `getExitFor` (`:85`).
   - `JunctionRouter.hasRoute` is simply `getExitFor != null` (`JunctionRouter.java:725-729`), and each call reaches
     `JunctionRoutingEngine.findRoute` (`:71-99`). That call allocates a `PairKey`, does a CHM lookup and a `sameComponent`
     check, runs `ValidatedRoutes.isValid`, and scans the filters.
   - `isValid` (`ValidatedRoutes.java:84-117`) walks the improvement log and every corridor edge on the route whenever the
     graph snapshot has changed.
   - Cost: O(hops/tick × 4 × route length after any edit). This is the biggest steady-state cost.
2. **Synchronous A\* on a cache miss, and the cache thrashes.**
   - The cache is keyed by (current pipe, destination). So a new destination runs A\* on the main thread once at every
     routed pipe on its path (`findRoute:89-98`, `computePair:101-108`), about L searches for a path of length L.
   - The number of pairs grows as pipes × destinations. Once it passes `MAX_CACHED_PAIRS = 1<<20` (`:34`), `onPublish` wipes
     the whole cache (`:296-298`) and everything is recomputed inline. 50k pipes × ~20 destinations already reaches that.
   - `onPublish` also runs a `removeIf` over the full cache whenever a junction is removed (`:291-294`).
3. **2 packets per item per hop.**
   - `sendItemPacket` (`PTL:807-827`) sends a `PipePositionPacket` on every inject or reverse, plus a `PipeContentPacket`
     carrying the full item with its NBT the first time an ID is seen.
   - These packets are not compressed, so `MainProxy.sendPacketToPlayer:171-178` encodes each one synchronously through
     `writeOutbound`, once per watching player.
   - `isAnyoneWatching` (`MainProxy:182-189`) ignores the dimension.
   - `clientSideKnownIDs` is one global sliding bitset of 2^20 IDs, not one per player (`LPTravelingItem.java:44`).
   - Side bug: `LPTravelingItem.serverList` is never filled, so `PipeContentRequest:22` can never answer.
4. **Reassigning a destination is O(network).**
   - `LogisticsManager.assignDestinationFor` (`:237-300`) calls `getDistanceTo` once per interested router, which is A\*
     for each pair on a miss. The one-to-many `JunctionRouter.getDistancesTo:749` exists but isn't used.
   - It then sorts the candidates and calls `sinksItem` on each one.
   - It is triggered when an item has no destination, has lost its route, or its destination no longer wants it
     (`RouteLayer:34-67`).
   - Buffered items retry every 40 ticks: up to 30 times for a lost route, 5 times for an unroutable item (`PTL:129-163`).
   - It gets worst when storage is full, because that is when items bounce.
5. **Bookkeeping for items in transit is O(n) per item.** `CRP._inTransitToMe` is a `PriorityBlockingQueue` (`CRP:167`).
   - `notifyOfSend` calls `contains`, which scans the whole queue (`:344`).
   - `removeFromInTransit` calls `remove` in a while loop, so it scans the queue at least twice (`:358-362`). It runs on
     every arrival and every reroute.
   - `countOnRoute` scans everything (`:1452-1459`) and is called from sink replies (`ChassiModule:86`, `ModuleCrafter:233,259`,
     `ModulePatternCrafting:1382`).
   - Together that is O(n²) at a busy destination.
6. **Per-tick work for each pipe holding items.** `moveSolids` runs `items.flush()`, which does a `purgeBadItems` pass and a
   `removeAll` (`LPItemList.java:79-103`, `PTL:705-735`).
7. **Plain pipes** (`resolveUnroutedDestination`, `PTL:288-310`): each hop allocates an ArrayList, calls `getTileEntity` 5
   times and creates a `new Random()`. Items do a random walk at branches.
8. **Smaller costs:**
   - `relayedItem` → `updateStats` (`CRP:1107-1137`).
   - `isItemExitable` calls `makeNormalStack` 3 times, and each call copies the NBT (`PTL:366-370`, `:663-670`).
   - `getBCPipePluggable` is called twice per arrival.
   - Reflection adapters in `PipeInformationManager` for tiles from other mods.
   - `markChunkModified` forces the chunk to re-save, and every save serializes every item.

**What already exists that a junction-to-junction rewrite can reuse:**
- `RouteCacheEntry.path` (a `List<JunctionId>`) and `RouteLabel` (`edges()`/`path()`/`firstEdge`/`blockDistance`).
- `CorridorEdge` has from, to, weight, `blockDistance` (usable as travel time), exit/insert side, flags, filters, `chunks[]`
  and a version.
- `ChunkEdgeIndex` (chunk → edges), which can check that the chunks on a path are loaded.
- `ValidatedRoutes.isTraversable` checks a path mid-flight, and the edge versions allow checking one segment at a time.
- One-to-many `findRoutesToTargets`/`getDistancesTo` and the per-source `SweepEntry`.
- `ExitRoute.blockDistance`, `IDistanceTracker`, and the background refresh executor.

Caveat: most LP networks are made entirely of routed pipes, so junctions ≈ pipes unless the corridors are merged further.

## 2. FPS lag from particles

**How particles are sent:**
- `CRP.spawnParticle` (`:760-766`) adds to `queuedParticles`.
- `spawnParticleTick` (`:768-797`) then sends one `ParticleFX` packet per pipe per tick through
  `MainProxy.sendPacketToAllWatchingChunk`, which reaches every player with the chunk loaded, out to the full view distance.
- The client throws the packet away unless it is on Fancy graphics (`ParticleFX:63`), within **16 blocks**, and its
  particle setting isn't Minimal (`PipeFXRenderHandler:18-30`). So most of the traffic is wasted.

**Settings:**
- Each burst spawns `ceil(sqrt(amount))` particles, and each lives about 20 ticks.
- The only switch is the server config `Configs.ENABLE_PARTICLE_FX` (default true). Clients can't opt out.

**Rendering:** `EntitySparkleFX.renderParticle` (`:47-96`) does its own `tess.draw()`, GL state changes and 2 `bindTexture`
calls for every particle. `onUpdate` creates 3 `new Random()` per particle per tick.

Sources, ranked:
1. `LogisticsManager:191`: Blue ×10 on the destination pipe on every successful `hasDestination`, which means every
   routing decision and every extractor or QuickSort probe.
2. `CRP:1300`: Gold, up to ×10, on every `useEnergy(int)`. Sparkles default to true (`:1218`), so this fires on every
   crafter or provider transfer and every `AdjacentInventoryHandler` move.
3. `CRP:337`: Orange ×2 for each stack sent.
4. **Router particles:**
   - `ServerRouter:490` and `JunctionRouter:291`: LightRed ×5 on every `recheckAdjacent`.
   - `ServerRouter:1085`: LightGreen ×5 on every route-table rebuild.
   - Placing or breaking one pipe therefore sends a packet for every router in the network.
   - They are also called from the routing thread, which writes `queuedParticles` without synchronisation (a data race).
5. Crafting:
   - Violet ×2 every 6 ticks while waiting (`PatternCraftingResultExtractor:82`, `ModuleCrafter:1396`).
   - White ×2 for each promise fulfilled (`ModuleCrafter:421/425`, `ModulePatternCrafting:667/709`).
6. Extractors short of power (`ModuleExtractor:177`, `ModuleAdvancedExtractor:202`): the call is inside a loop.
7. Other modules: QuickSort, Provider, ActiveSupplier, ApiaristRefiller.
8. `CRP:700`: power on/off Red ×3. It keeps firing when power is marginal.
9. Power lasers: these send packets only when a laser is added or removed, but the 4-tick timeout makes them flap.

**Fix:**
- Delete the router particles.
- Make `useEnergy(int)` not spawn sparkles.
- Drop the Blue `hasDestination` particle.
- Send only to players within 16 blocks in the same dimension.
- Add a client-side toggle, and honour `particleSetting`.
- Replace the status sparkles (waiting, unpowered, requesting) with texture or HUD state.
- Batch the rendering in `EntitySparkleFX`.

## 3. FPS lag when near a lot of pipes

This comes from upstream design; this branch didn't introduce it. What the branch changed on the client side is trivial.

1. **Pipe bodies are not in chunk display lists.**
   - Every pipe within `renderPipeDistance` (48, `PlayerConfig:46`) is drawn by the TESR every frame
     (`LogisticsNewRenderPipe.renderTileEntityAt:714-790`: bind, pushAttrib, blend, one VBO draw, popAttrib).
   - The block renderer only draws the pluggables.
   - So the cost is thousands of draw calls per frame, linear in the pipes in view.
2. **The TESR runs out to 256 blocks.**
   - `LogisticsTileGenericPipe.getMaxRenderDistanceSquared` returns 256².
   - Before the distance check, every pipe calls `renderPipeSigns` (2× `getPipeSigns`, each allocating a list), `Math.pow` ×3
     and `bindTexture`.
3. **Chunk rebuilds on the 48-block line.**
   - The TESR flips `forceRenderOldPipe`, and `CoreUnroutedPipe.updateEntity:124-130` then calls `markBlockForUpdate`, which
     rebuilds chunk sections along a shell that moves with the player.
   - `oldRendererState` starts false, so every pipe forces one rebuild when it loads.
4. **HUD glasses.**
   - `LogisticsHUDRenderer.refreshList:95-117` scans every client router ever loaded, with a `getTileEntity` for each.
   - It does this every frame when no HUD pipe is nearby, every 10 frames while moving, and on every half block moved.
   - Client routers are never removed (`RouterManager.removeRouter` does nothing on the client).
5. **Traveling items within 24 blocks** go through vanilla `RenderItem` one by one, up to 10 + 27 per pipe. There is a
   push/pop even for empty pipes.
6. VBOs are freed after 60 s unused, so pipes re-tessellate when the view turns back to them (hitches).
7. Every loaded pipe ticks on the client (`moveSolids` and so on), not only the nearby ones.

**Fix:**
- Bake the pipe model into the chunk Tessellator in the block renderer, and keep the TESR only for items, fluids, signs and
  lasers.
- Clamp the TESR distance, and move the bind below the distance check.
- Add hysteresis to the 48-block switch.
- Remove client routers when their tile is invalidated.
- Keep a registry of the HUD pipes nearby.

## 4. all pipes consume 20-50 us per tick even when idle

even when idle, pipes often cost 20-50 us per tick just to exist/update, even transport pipes, this needs to be investigated

**Cause (found 2026-09-29): debug mode was hard-coded on.** Fixed in code, not yet measured in game.
- `LPConstants.DEBUG` had been set to `true` in commit `1059c93e` ("crafting rework wip"). Before that it read the
  `logisticspipes.enableDebug` system property.
- With debug on, `LogisticsTileGenericPipe.updateEntity` builds two trace entries per pipe per tick:
  - `StackTraceUtil.addSuperTraceInformation` and `addTraceInformation`;
  - each one calls `Thread.currentThread().getStackTrace()`, which walks the whole server call stack (tens of µs from
    inside a tile tick);
  - plus string building and a synchronized map.
- That explains why every pipe, including plain transport pipes, costs 20–50 µs even when idle, and why the cost scales
  with the pipe count.
- Debug mode also:
  - lets any player use `PowerJunctionCheatPacket` (free power) and `/lp debug`;
  - turns on `TOOLTIP_INFO`;
  - adds stack walks and logging in many other places (`ClientRouter`, `JunctionRouter.update`, GUI providers, packet
    handling).
- **Fix:** `DEBUG = Boolean.getBoolean("logisticspipes.enableDebug")` again. For a debug session, add
  `-Dlogisticspipes.enableDebug=true` to the run configuration's JVM arguments. At scale, keep in mind debug mode costs
  this much per pipe.

**Also fixed: the power check polled by every routed pipe.**
- `checkTexturePowered` runs `canUseEnergy(1)` every 10 ticks on every routed pipe. That goes through
  `JunctionRouter.powerView()`, which did one-to-many route lookups (key allocation, cache lookup and validation for
  each power junction) and array comparisons on every call.
- At 50k pipes that is about 5,000 rebuilds per tick.
- The view is now reused while the graph snapshot is the same object and the pipe's own power lists haven't changed. Routes
  to power junctions and their data can only change with a new snapshot.

**What's left per idle pipe, with debug off** (from reading the code; each item is well under 1 µs unless stated):
- *Tile* (`LogisticsTileGenericPipe.updateEntity`):
  - `tilePart.updateEntity_LP()` runs the embedded BuildCraft pipe: gates, pluggables and 6 side lookups. It's cheap
    without pluggables.
  - `bcPlugableState.isDirty()` serializes the BuildCraft pluggable state into a buffer and compares it every tick
    (synchronized). It goes away with the BC support (design doc: only LP pipes).
  - `renderController.onUpdate()` makes two iterator allocations on empty laser maps.
- *Transport* (`PipeTransportLogistics.updateEntity`): `moveSolids` runs `LPItemList.flush()` (three passes over empty
  sets plus iterator allocations), then `tickClumps` and `_itemBuffer.sendUpdateToWaters()`.
- *Routed pipes* also run:
  - `debug.tick()`, `spawnParticleTick()` and `JunctionRouter.update()` (queue poll, interest countdown);
  - `securityTick()`, `throttledUpdateEntity()`, `enabledUpdateEntity()` and the module tick.
- *Amortized, not per tick:*
  - a full corridor re-scan every `LOGISTICS_DETECTION_FREQUENCY` (600) ticks per pipe, about 83 scans per tick at
    50k pipes;
  - `updateInterests` every 20 ticks, which calls `getSpecificInterests()`. Some pipes rebuild sets in it; the pattern
    crafting pipe parses pattern NBT (`pattern-crafting.md` F5).

Possible follow-ups if spark still shows a per-pipe floor:
- Skip the BC tile part and `isDirty` when the pipe has no pluggables.
- Stop ticking plain transport pipes that hold no items (they'd need a wake-up when items enter).
- Spread or shorten the periodic corridor re-scan.
