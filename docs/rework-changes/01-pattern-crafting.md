# 01 — Pattern crafting system

Upstream `GTNH-origin/master` crafts through the legacy crafting pipe (`PipeItemsCraftingLogistics` +
`ModuleCrafter`): one recipe per pipe, configured in the pipe's own GUI. When a request is fulfilled, every
ingredient order is placed at once and the items are pushed into the adjacent inventory as they arrive. Item
satellites and "advanced satellite" slots redirect single ingredients. Fluid byproducts and fluid promise splitting
were stubs (`// TODO FluidCrafting: FIX`). The rework adds a separate, parallel system built around **pattern items**
and the **Pattern Crafting Pipe**. A pipe holds up to 9 patterns (crafting 3x3 → 3 or processing 16 → 4, items and
fluids). When the request tree picks it, the pipe takes over a snapshot of the whole sub-tree (a *staged* craft). It
then requests ingredients set by set as the target has room, buffers them per pattern, dispatches complete sets to the
adjacent machine or to **pattern satellites**, and extracts results and byproducts. Every running craft has a stable
identity, so it can be cancelled, saved and watched (HUD, Crafting Monitor block, request-table popup, operator debug
window). The legacy crafter stays, with a few small and partly unfinished changes (see
[Changes to upstream classes](#changes-to-upstream-classes)).

Paths below are relative to this file. Abbreviations: **M** = `ModulePatternCrafting`, **P** =
`PipeItemsPatternCraftingLogistics`, **RTN** = `RequestTreeNode`. Issue ids such as C2 and S5 refer to the tracker in
[pattern-crafting.md](../pattern-crafting.md) §8. Bug ids such as B13 refer to [bug-list.md](../bug-list.md).

Related areas documented elsewhere: the new request table and its monitor UI ([02-request-table.md](02-request-table.md)),
junction router / `InterestRegistry` / `RouterIds` used by the request tree ([03-router-rework.md](03-router-rework.md)),
item transport and clumps that carry `PatternTargetInformation` ([04-item-transport.md](04-item-transport.md)),
ModularUI infrastructure, `PipeSatelliteMui` and `PatternCraftingTableMui` ([05-modularui-gui.md](05-modularui-gui.md)),
and provider-module reservations / registrations / recipes ([06-modules-pipes-compat-build.md](06-modules-pipes-compat-build.md)).

---

## Features

### Pattern items & pattern types

**Player view.** The *Logistic Crafting Pattern* (`LogisticsPipes.LogisticsPattern`, [ItemPattern](../../src/main/java/logisticspipes/crafting/pattern/ItemPattern.java))
is a stand-alone item. Right-clicking it opens a handheld ModularUI editor
([HandheldPatternMui](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/HandheldPatternMui.java)).
The pipe GUI uses the same editor, so a pattern edited by hand works in a pipe exactly like one edited there. There
are two layouts, toggled in the editor:

- **Crafting** ([DefaultPattern](../../src/main/java/logisticspipes/crafting/pattern/DefaultPattern.java)): 9 inputs, 3 outputs.
- **Processing** ([ProcessingPattern](../../src/main/java/logisticspipes/crafting/pattern/ProcessingPattern.java)): 16 inputs, 4 outputs, `patternType="processing"`.

The editor ([PatternEditor](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/PatternEditor.java),
[PatternEditorSyncHandler](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/PatternEditorSyncHandler.java))
offers phantom entry slots (fluid entries change in mB steps,
[PatternIngredientSlotSH](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/PatternIngredientSlotSH.java)),
plus CLEAR, MULTIPLY (x2, refused with a chat message if too large), TOGGLE_TYPE, TOGGLE_ORE_DICT, TOGGLE_IGNORE_NBT,
per-input satellite assignment, and NEI import. Holding shift renders a pattern as its primary result
([PatternItemRenderer](../../src/main/java/logisticspipes/renderer/PatternItemRenderer.java)). The pipe GUI forces
that rendering in its slots.

**How it works.** All pattern data lives in the item NBT, read through
[AbstractPattern](../../src/main/java/logisticspipes/crafting/pattern/AbstractPattern.java):

| NBT key | Meaning |
|---|---|
| `patternItems` | list of `{slot, patternStackType:"solid"\|"fluid", ...}`; inputs first, outputs from `getResultSlotStart()` |
| `patternMainOutputSlot` | which output the pattern advertises; default = first populated output |
| `patternSatelliteTargets` / `…Uuids` | per-input item satellite (id + uuid) |
| `patternFluidSatelliteTargets` / `…Uuids` | per-input fluid satellite |
| `patternByproductSatelliteTargets` / `…Uuids`, `patternFluid…` | per-output satellite that extracts that byproduct |
| `patternOreDictSubstitution`, `patternIgnoreNbt` | matching flags (anyone can toggle them, no upgrade needed) |

Entries are [IPatternStack](../../src/main/java/logisticspipes/crafting/patternStack/IPatternStack.java)s
([PatternItemStack](../../src/main/java/logisticspipes/crafting/patternStack/PatternItemStack.java),
[PatternFluidStack](../../src/main/java/logisticspipes/crafting/patternStack/PatternFluidStack.java)). Item stacks
also write an int `lpCount`, because vanilla 1.7.10 stores `Count` as a byte (D4). An untyped entry is read as a
legacy `ItemStack`, and anything that holds fluid becomes a fluid entry. In the pipe,
[PatternHandler](../../src/main/java/logisticspipes/crafting/pattern/PatternHandler.java) caches an immutable
[PatternRecipeSnapshot](../../src/main/java/logisticspipes/crafting/pattern/PatternRecipeSnapshot.java) per slot until
the pattern inventory changes, so hot paths don't re-parse NBT. Editors work through a
[PatternSource](../../src/main/java/logisticspipes/crafting/pattern/PatternSource.java) (pipe slot or held item, read
live) and an [EditedPatternInventory](../../src/main/java/logisticspipes/crafting/pattern/EditedPatternInventory.java),
which rejects pattern items in the slots (S7).

**Upstream changes it relies on:** none apart from item registration in `LogisticsPipes` (see 06).

### Pattern crafting pipe

**Player view.** *Pattern Crafting Pipe* ([P](../../src/main/java/logisticspipes/pipes/PipeItemsPatternCraftingLogistics.java)),
GUI [PipePatternCraftingMui](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/PipePatternCraftingMui.java).
It has 9 pattern slots: click a slot to select it for editing, click again to take the pattern out
([PatternSelectSlot](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/PatternSelectSlot.java)).
Below the slots are the editor, the crafting target ("Crafts in: …"), a blocking-mode selector, *Cancel* (selected
slot) and *Return inputs*. The pipe works on **one** selected adjacent block. Sneak + wrench cycles the target
([PatternCraftingTargetSelector](../../src/main/java/logisticspipes/crafting/PatternCraftingTargetSelector.java),
persisted as `patternConnectedInventoryDirection`). With UNKNOWN, the first usable neighbour is picked. The target is
transport-disconnected (only the connection is rendered), so nothing can push untracked items into it. Legacy wrenches
open the MUI too (`onWrenchClicked`). Breaking the pipe drops the patterns and the buffered ingredients (fluids as LP
fluid containers).

**How it works.** P extends `FluidRoutedPipe`, but raw tank insertion is off (`canInsertToTanks`,
`canReceiveFluid` = false). Fluid ingredients only arrive as routed `LogisticsFluidContainer` items. P owns an item
order manager and a separate fluid order manager (`getPatternFluidOrderManager`). Almost everything else is delegated
to [M](../../src/main/java/logisticspipes/crafting/ModulePatternCrafting.java), which was split into focused helpers:

| Helper | Role |
|---|---|
| [PatternCraftingArrivalHandler](../../src/main/java/logisticspipes/crafting/PatternCraftingArrivalHandler.java) | `itemArrived`: buffers only arrivals whose `PatternTargetInformation` carries an order *and* delivery reference (`isTracked()`); accepts `min(stack, still requested for that order)`; untracked arrivals are ignored (left on the stack); late arrivals of cancelled instances go to storage |
| [PatternStackBufferHandler](../../src/main/java/logisticspipes/crafting/PatternStackBufferHandler.java) / [PatternStackRequestHandler](../../src/main/java/logisticspipes/crafting/PatternStackRequestHandler.java) | arrived / in-flight ingredients, both keyed by owning `PatternCraftingReference` with per-slot aggregate caches |
| [PatternCraftingIngredientPlanner](../../src/main/java/logisticspipes/crafting/PatternCraftingIngredientPlanner.java) | OreDict/NBT matching, per-input local/satellite targets, concrete buffered set plans |
| [PatternCraftingCapacity](../../src/main/java/logisticspipes/crafting/PatternCraftingCapacity.java) + [AdjacentInventoryHandler](../../src/main/java/logisticspipes/crafting/AdjacentInventoryHandler.java) | room simulation (snapshot + binary search for items, `fill(simulate)` for fluids, cumulative fluid check D3), insert, extract, `isEmpty`; memoised per tick |
| [PatternCraftingBufferDispatcher](../../src/main/java/logisticspipes/crafting/PatternCraftingBufferDispatcher.java) | pushes complete sets; a partly inserted set becomes a *pending dispatch* that is finished before anything else (D1) |
| [PatternCraftingResultExtractor](../../src/main/java/logisticspipes/crafting/PatternCraftingResultExtractor.java) | every 6 ticks, ≤ 64 items / 16 stacks per pass: extracts CRAFTING/EXTRA orders, routes to the requester, feeds same-pipe intermediates straight into the buffer, sends destinationless extras to storage (`-1`); fluids drained in parcels of `MAX_LOGISTICS_FLUID_TRANSPORT_INNER_CAPACITY/2` |
| [PatternLostIngredientHandler](../../src/main/java/logisticspipes/crafting/PatternLostIngredientHandler.java) | `itemLost` → delayed `RequestTree.requestPartial` / `requestFluidPartial` re-request, persisted as `patternLostIngredients` |

`M.tick()` runs every tick on the server: restore staged state if pending → cancel fluid patterns if the fluid upgrade
was removed → schedule post-load retries → retry lost items → push buffered sets → `stagedCrafting.requestIngredients()`
→ refresh the running-craft lock → result extractor. The pipe's router interests are the pattern **main outputs**
(`getSpecificInterests`), so passing items aren't pulled in. `sinksItem` answers at ItemSink priority, with the room
computed from requested amounts. Insertion uses the sneaky side if present, otherwise the face touching the pipe
(`getInsertionOrientation`, D2). `sharesInterestWith` keeps the upstream "two crafters on one inventory" guard and
counts the selected target.

**Upstream changes it relies on:** `LogisticsTileGenericPipe` (render the target connection, refuse `injectItem`),
`PipeTransportLogistics`, `LogisticsFluidManager` (pattern pipes are not passive fluid sinks). These are in other
areas' file lists. `ItemRoutingInformation` (this area) persists the routed item's target info.

### Blocking modes

**Player view.** The GUI selector ([lang](../../src/main/resources/assets/logisticspipes/lang/en_US.lang) `gui.patterncrafting.mode.*`)
has three modes:

- **OFF** – insert every complete set as soon as the target has room, for all patterns.
- **BLOCKING** – one set at a time, and only into an empty target.
- **SMART** – the running pattern may push several sets; other patterns wait until the target is empty.

The mode is forced to SMART (and shown as fixed) while the target is a Pattern Crafting Table.

**How it works.** [PatternCraftingBlockingHandler](../../src/main/java/logisticspipes/crafting/PatternCraftingBlockingHandler.java)
holds `runningCraft` / `runningCraftInAdjacent` / the running craft's reference, plus active **satellite batches**.
In non-OFF modes the satellites of a batch are reserved, and every slot is blocked until each reserved satellite
reports its inputs consumed (`isReservationConsumed`). Switching to OFF releases all batches. Persisted as
`patternBlockingMode`, `runningCraft`, `runningCraftInAdjacent` and a `runningCraft` reference. "Empty" is
`AdjacentInventoryHandler.isEmpty` over every raw slot and tank, so non-consumed items such as molds keep the lock
(B34 / C8).

### Satellites (item/fluid, routed vs instant)

**Player view.** There are two new pipes:
*Pattern Satellite Logistics Pipe* ([PipeItemsPatternSatelliteLogistics](../../src/main/java/logisticspipes/crafting/PipeItemsPatternSatelliteLogistics.java))
and *Pattern Fluid Satellite Logistics Pipe* ([PipeFluidPatternSatelliteLogistics](../../src/main/java/logisticspipes/crafting/PipeFluidPatternSatelliteLogistics.java)).
Each has a numeric id, a persistent UUID (`patternSatelliteUuid` / `patternFluidSatelliteUuid`) and a player-defined,
uniqueness-suffixed name ([IPatternSatellitePipe](../../src/main/java/logisticspipes/crafting/IPatternSatellitePipe.java)).
The id and name are edited in `PipeSatelliteMui` (see 05). The legacy `SatPipeNext` / `SatPipePrev` / `SatPipeSetID`
packets now ignore pattern satellites. In the pattern editor, each input slot can be assigned a satellite from a
server-sent list ([PatternSatelliteInfo](../../src/main/java/logisticspipes/crafting/PatternSatelliteInfo.java):
favourites first, then by distance). Assigning a satellite links it to the pipe.

The new **Memory Chip** ([ItemMemoryChip](../../src/main/java/logisticspipes/crafting/ItemMemoryChip.java)) works like
this:

- Clicking a pattern satellite with the chip stores it. A renamed chip also renames the satellite.
- Sneak-clicking a pattern crafting pipe with the chip either links all stored satellites (FAVORITES mode) or assigns
  the last stored satellite to every ingredient of the selected recipe (APPLY_LAST_TO_RECIPE mode).
- Right-click in the air cycles the mode.
- Chip NBT: `patternSatelliteRefs`, `patternSatelliteMode`, `lastPatternSatellite{Id,Uuid,Name}`.

**How it works.** [PatternSatelliteDispatchHandler](../../src/main/java/logisticspipes/crafting/PatternSatelliteDispatchHandler.java)
splits each set into local assignments, item-satellite assignments and fluid-satellite assignments (merged per
satellite and fluid). It finds the largest set count that `canDispatch()`.

- **Item satellites, routed (default).** Items are sent with `pipe.sendStack` to the satellite's router, tagged with a
  `PatternTargetInformation` delivery reference. Dispatch requires `hasRoute`. In non-OFF modes the satellite
  `expectPatternInput`s them and locks its exit to the target side (`isLockedExit`). This addresses the item half of
  B13.
- **Item satellites, instant.** With the new *Instant Satellite Upgrade*, `satellite.insertPatternInput(...)` puts the
  items straight into the satellite's adjacent inventory.
- **Fluid satellites** always insert directly (`insertPatternInput(fluid, amount, reserve)`). There is no routed fluid
  path.
- **Reservations** (`reservedOwnerRouter`, `reservationBaseline`) apply in BLOCKING/SMART only. They are not persisted.

Satellites register in static `WeakHashMap`-backed sets, are resolved by UUID first and then by id, and are cleared on
server stop (`LogisticsPipes` calls `cleanup()`). Cancelled routed deliveries that still arrive at a satellite are
intercepted and sent back to storage (`PendingCancelledArrival`, 640-tick timeout).

**Upstream changes:** `PipeItemsSatelliteLogistics` and `PipeFluidSatellite` implement the new
[ISatellitePipe](../../src/main/java/logisticspipes/crafting/legacy/satellite/ISatellitePipe.java) and `IMUICompatiblePipeV2`
(`getPipeGui()` → `PipeSatelliteMui`). They gain `setNextFreeId()`, and `satelliteId` gets a Lombok getter/setter
(this replaces the old `setSatelliteId(int)`).

**Gating:** without an Advanced Satellite Upgrade in the crafting pipe, `canDispatch()` returns false for any plan with
satellites, the template drops output-satellite targets, `resolvePatternSatelliteTarget` refuses to resolve, and
memory-chip linking answers "Advanced Satellite Upgrade required". See
[Known gaps](#known-gaps--discrepancies).

### Staged crafting & request tree integration

**How it works.**

1. **Templates.** [PatternCraftingTemplateBuilder](../../src/main/java/logisticspipes/crafting/PatternCraftingTemplateBuilder.java)
   matches the request only against each pattern's **main output** (first matching slot wins). It builds
   [PatternCraftingTemplate](../../src/main/java/logisticspipes/crafting/PatternCraftingTemplate.java) (items) or
   [PatternFluidCraftingTemplate](../../src/main/java/logisticspipes/crafting/PatternFluidCraftingTemplate.java)
   (fluids), with **one component per input slot**, tagged `PatternTargetInformation(slot, inputSlot)`. All other
   outputs become byproducts. If OreDict or ignore-NBT is on, the ingredient becomes a `DictResource` with the new
   `match_same_item` flag. RTN then collects provider promises per concrete item and accepts only one item variant
   for the whole amount (`beginSameItemPromiseCollection` / `finishSameItemPromiseCollection`).
2. **Hand-off.** `RTN.fullFill()` → `fullFillStaged()` when a node has a CRAFTING promise from an
   [IStagedCraftingProvider](../../src/main/java/logisticspipes/crafting/IStagedCraftingProvider.java). The node and
   its subtree are snapshotted into a [PatternCraftingBranch](../../src/main/java/logisticspipes/crafting/PatternCraftingBranch.java)
   (`toPatternCraftingBranch`). Each staged promise gets `branch.copyForAmount(n)`, and provider promises inside it are
   reserved (`reserveProviderPromises`, via [IStagedProviderReservation](../../src/main/java/logisticspipes/crafting/IStagedProviderReservation.java)
   on providers, see 06). Then `fullFillStagedCrafting` is called. Non-staged promises on the same node are fulfilled
   normally. Byproducts of staged providers are **not** registered at this point.
3. **Coordinator.** [PatternStagedCraftingCoordinator](../../src/main/java/logisticspipes/crafting/PatternStagedCraftingCoordinator.java)
   validates the destination, creates the output order and a [PatternCraftingOrder](../../src/main/java/logisticspipes/crafting/PatternCraftingOrder.java)
   (pattern slot, result per set, ingredient branches, remaining sets). It registers the order in
   [PatternCraftingInstanceRegistry](../../src/main/java/logisticspipes/crafting/PatternCraftingInstanceRegistry.java)
   and asks the scheduler for an immediate request.
4. **Scheduler.** [PatternStagedCraftingScheduler](../../src/main/java/logisticspipes/crafting/PatternStagedCraftingScheduler.java),
   each tick and per slot: `sets = min(remaining, orderable from room minus in-flight, available from branches)`.
   BLOCKING is capped at 1. Other slots are skipped while one slot holds the lock.
5. **Order.** `PatternCraftingOrder.requestIngredients(sets)` calls `branch.request(...)` for each input target, which
   fulfils provider/extra promises or hands child crafts off as further staged crafts. The requests are recorded in
   the request handler. `ingredientsDispatched(sets)` registers byproducts only for the sets actually inserted.
   `extractableOutputAmount()` caps extraction at `inherited + dispatchedSets × perSet`, so pre-existing items in the
   machine aren't taken.

Every order, delivery, buffered entry and satellite batch carries a
[PatternCraftingReference](../../src/main/java/logisticspipes/crafting/PatternCraftingReference.java)
`(instanceId, objectId)`. The instance id groups the whole recursive request. Routed items carry it in
[PatternTargetInformation](../../src/main/java/logisticspipes/crafting/PatternTargetInformation.java)
`(patternSlot, inputSlot, orderReference, deliveryReference)`.

**Upstream changes:** `RequestTreeNode` (`fullFillStaged`, `toPatternCraftingBranch`, same-item dict promises,
`getPromisedByproductAmount`, a recursive `toString` tree dump). It also moved to `InterestRegistry` and the one-to-many
`JunctionRouter.getDistancesTo`, and changed the extra-promise reachability check to `getDistanceTo` in both
directions (see 03). `DictResource` (`match_same_item`, bit 4 of the serialized BitSet; `copyForDisplayWith` now keeps
the requester). `FluidResource.copyForDisplayWith` keeps the target. `LogisticsDictPromise.copy()` keeps the dict
resource. `BaseCraftingTemplate.getIngredients()`.

### Byproducts & extras

**Player view.** Every non-main output of a pattern is a byproduct. It becomes an EXTRA order after its sets are
dispatched, and is then extracted and routed to storage, or handed to a later request that claims it. An output slot
can name a pattern satellite that should extract that byproduct from **its** machine. That satellite needs the
*Crafting Byproduct Upgrade*, which is now allowed in pattern (fluid) satellites.

**How it works.** [PatternByproductTarget](../../src/main/java/logisticspipes/crafting/PatternByproductTarget.java)
records the pattern slot, output slot, source reference, satellite id/uuid and the fluid flag.
`M.canProvide` offers existing EXTRA orders per (item, target) minus the claims already in the tree
(`root.getPromisedByproductAmount`, fixes C6's double-promise). It does this as
[PatternItemByproductPromise](../../src/main/java/logisticspipes/crafting/PatternItemByproductPromise.java) /
[PatternFluidByproductPromise](../../src/main/java/logisticspipes/crafting/PatternFluidByproductPromise.java). On
fulfil, the matching EXTRA order is removed (`removeExtras(resource, target)`) and a CRAFTING order with
`byproduct=true` and the target is added. The result extractor uses
[PatternByproductExtractionTargetCache](../../src/main/java/logisticspipes/crafting/PatternByproductExtractionTargetCache.java)
→ [PatternSatelliteByproductExtractor](../../src/main/java/logisticspipes/crafting/PatternSatelliteByproductExtractor.java)
(checks the byproduct upgrade and a route to the requester) for orders whose target names a satellite.

**Upstream changes:**

- `LogisticsOrder` gains `byproduct`, `byproductTarget` and `craftingReference`.
- `LogisticsItemOrderManager` gains `getAllOrders()` and target-aware `removeExtras`.
- `LogisticsFluidOrderManager` gains fluid EXTRA orders (`addExtra`, `removeExtras`).
- `LogisticsFluidOrder` accepts `destination == null`.
- `FluidExtraPromise` is new.
- `FluidCraftingTemplate.addByproduct` / `addFluidByproduct` / `getByproducts` are implemented (upstream TODO stubs).
- `FluidLogisticsPromise.split` now returns a `FluidExtraPromise` instead of throwing.

### Fluid crafting

**Player view.** Patterns may contain fluid inputs and outputs. They need a *Fluid Crafting Upgrade* in the pattern
crafting pipe (the upgrade is now allowed there). Without it, fluid patterns are not advertised and don't sink fluids,
and running crafts for them are cancelled when the upgrade is removed (`cancelUnsupportedFluidPatternCrafts`, now only
re-checked on a pattern change or upgrade change). Fluid containers show their amount (k/m/b) in inventories
([FluidContainerRenderer](../../src/main/java/logisticspipes/renderer/FluidContainerRenderer.java)).
`LogisticsFluidContainer` may now exist in normal inventories and in the world, so buffered fluids can be dropped and
shown. Fluid request GUIs can list craftable fluids (`RequestHandler.refreshFluid(..., DisplayOptions)`) and simulate a
fluid request (`simulateFluid`).

**How it works.** Fluids travel as routed `LogisticsFluidContainer` items and are buffered as `PatternFluidStack`s
(all-or-nothing per container). They are inserted with `IFluidHandler.fill`. Outputs go through P's fluid order manager
([PatternFluidCraftingPromise](../../src/main/java/logisticspipes/crafting/PatternFluidCraftingPromise.java)). Lost
fluids are re-requested with the new `RequestTree.requestFluidPartial(…, info)` overload.

### Cancellation

**Player view.** You can cancel a craft from three places: *Cancel* in the pipe GUI (whole instances touching the
selected slot), *Return inputs* (cancel everything and flush this pipe's buffer), or the Crafting Monitor block
(per instance).

**How it works.** `PatternCraftingInstanceRegistry.cancelInstance(id)` marks a tombstone (bounded to 4096 instances)
and calls `cancelTrackedOrder` on every order of the instance, in whichever pipe owns it.
[PatternCraftingCancelHandler](../../src/main/java/logisticspipes/crafting/PatternCraftingCancelHandler.java) then:

- releases reservations and removes the output order (`LogisticsOrderManager.removeOrder`, new),
- removes standalone extra orders of that instance and the requested-ingredient entries,
- abandons a pending partial dispatch,
- flushes owned buffer entries to storage,
- recalls satellite batches, clears the running lock and drops lost-queue entries.

Late arrivals of a tombstoned instance are routed to storage, both at the pipe and at satellites. This replaces the
former slot-number-based `PatternCraftingCancellationResolver`, which no longer exists.

### Persistence & NBT

The module NBT holds:

- `PatternCrafting` (pattern inventory) and `patternBlockingMode`, plus the running-craft tags.
- `patternIngredientBuffer`, `patternRequestedIngredients` and `patternLostIngredients`, each entry carrying a
  `patternSlot` and an owner reference.
- `patternStagedCrafting`, written by the coordinator (`stagedOrders`, `standaloneItemOrders`,
  `standaloneFluidOrders`; branches/promises/resources through
  [PatternCraftingPersistence](../../src/main/java/logisticspipes/crafting/PatternCraftingPersistence.java) with a
  `kind` discriminator, routers stored as UUID strings).

P adds the linked satellite id/uuid lists (`linkedPatternSatellite{Ids,Uuids}`, `linkedPatternFluidSatellite{Ids,Uuids}`).
Routed items persist `PatternTargetInformation` under `targetInfo` (`ItemRoutingInformation`).

Restore works like this. If any router in the saved staged state is not loaded yet, `RestoreNotReadyException` keeps
the state in `pendingStagedCrafting`, which is retried every tick. While pending, it is written back unchanged and
listed in the monitor. After **1200 attempts** (about a minute) it is discarded: its instances are tombstoned and their
inputs flushed (`expirePendingStagedCrafting`). Saved requested-but-not-arrived ingredients are queued as lost retries
with an 8000 delay once the restore is done. There is no format version tag. `PatternCraftingReference`s are assigned
to legacy standalone orders on load (`assignMissingStandaloneReferences`).

**Save-compat notes:**

- The doc-promised fallback read of the old `bufferedIngredients` tag no longer exists in code. Only in-progress
  branch builds wrote it.
- The removed `Ord.SatelliteIngredientDelivery` NBT is dropped (§8.7 of pattern-crafting.md).

### HUD & crafting monitor

- **HUD glasses** ([HUDPatternCrafting](../../src/main/java/logisticspipes/gui/hud/HUDPatternCrafting.java)) show each
  pattern with its buffered inputs, requested outputs and a status line. Watch mode 1 sends `OrdererManagerContent` plus
  [PatternCraftingHudContent](../../src/main/java/logisticspipes/network/packets/orderer/PatternCraftingHudContent.java).
  The snapshot ([PatternCraftingHudState](../../src/main/java/logisticspipes/crafting/PatternCraftingHudState.java)) is
  cached by [PatternCraftingHudHandler](../../src/main/java/logisticspipes/crafting/PatternCraftingHudHandler.java)
  (dirty flag + periodic recheck). The pipe MUI gets the same state through `PatternCraftingSyncHandler` (S_HUD /
  S_STATE).
- **Crafting Monitor block** (`LogisticsSolidBlock` meta 7, [CraftingMonitorTileEntity](../../src/main/java/logisticspipes/crafting/CraftingMonitorTileEntity.java)).
  Placed next to a routed pipe, it lists every live instance reachable from that router
  ([PatternCraftingMonitorRegistry.buildAll](../../src/main/java/logisticspipes/crafting/PatternCraftingMonitorRegistry.java)).
  The list includes pending restores and standalone extra orders as [PatternCraftingMonitorEntry](../../src/main/java/logisticspipes/crafting/PatternCraftingMonitorEntry.java)
  trees of [PatternCraftingMonitorNode](../../src/main/java/logisticspipes/crafting/PatternCraftingMonitorNode.java).
  Each instance has a cancel button. The GUI is the legacy-style [CraftingMonitorGui](../../src/main/java/logisticspipes/crafting/CraftingMonitorGui.java)
  (refresh every 20 ticks through `CraftingMonitorRefreshPacket` → `CraftingMonitorContentPacket`; cancel through
  `CraftingMonitorCancelPacket`). The recipe (in `RecipeManager`) uses a statistics table plus a pattern crafting pipe.
- **Request table popup** ([PatternRequestMonitorPopup](../../src/main/java/logisticspipes/gui/popup/PatternRequestMonitorPopup.java))
  in the legacy `GuiRequestTable` shows the progress tree of a watched request, fed by
  [PatternCraftingWatchPacket](../../src/main/java/logisticspipes/network/packets/orderer/PatternCraftingWatchPacket.java).
  See 02 for the table side.

### Crafting debug tooling

[CraftingRequestDebugManager](../../src/main/java/logisticspipes/request/debug/CraftingRequestDebugManager.java)
records the following, **always on**:

- the last 24 request-tree snapshots (`RequestTree` calls `record` for every item, list and fluid request, including
  non-pattern ones),
- up to 60 000 categorised events (`REQUEST`, `STAGED`, `SCHED`, `BRANCH`, `BUFFER`, `FLOW`, `EXTRA`, `CANCEL`,
  `PERSIST`, …) from `M.debugEvent` / `debugEventThrottled`.

`Ctrl+Shift+T` on the client ([CraftingRequestDebugClient](../../src/main/java/logisticspipes/request/debug/CraftingRequestDebugClient.java),
driven from `LPTickHandler`) opens a Swing window that re-requests a snapshot about once per second. The window has
Overview, Timeline, Requests, Pattern Pipes and Raw tabs, and includes `M.appendDebugState` per pipe.
`CraftingRequestDebugRequest` answers (and clears) only for privileged players (`PacketGuards.isPrivileged`, S6).
`RequestTreeNode.toString`, `ItemResource.toString` and `FluidResource.toString` were added for these dumps.
[timeline1.txt](../timelines/timeline1.txt) is a sample dump.

### NEI integration

NEI's recipe transfer (shift + "+") into either pattern editor goes through
[PatternCraftingContainer](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/PatternCraftingContainer.java)
(`INEIRecipeTransfer`) → [PatternCraftingRecipeTransfer](../../src/main/java/logisticspipes/nei/PatternCraftingRecipeTransfer.java)
→ [PatternRecipeImporter](../../src/main/java/logisticspipes/nei/PatternRecipeImporter.java).

- Crafting-table recipes keep the 3x3 shape and become crafting patterns. Everything else becomes a processing
  pattern with aggregated inputs and outputs.
- NEI fluid display stacks become fluid entries. Real containers stay items.
- The result is sent as a [PatternRecipeImport](../../src/main/java/logisticspipes/crafting/pattern/PatternRecipeImport.java)
  (C_IMPORT). With no pattern selected, the first blank pattern is used.
- `NEILogisticsPipesConfig` no longer registers a separate handler for patterns. It now shares one
  `LogisticsCraftingOverlayHandler.INSTANCE` and also registers it for the new `RequestTableGui` (request table, see
  02).

### Pattern crafting table

[PatternLogisticsCraftingTableTileEntity](../../src/main/java/logisticspipes/crafting/PatternLogisticsCraftingTableTileEntity.java)
is `LogisticsSolidBlock` meta 6. Its GUI is `PatternCraftingTableMui` (see 05). It has 9 inputs, 3 outputs, 3 pending
outputs and 4 upgrade slots (speed upgrades only). It crafts vanilla recipes with a fake player after a cooldown of
`max(1, 100 / (1 + speed upgrades))` ticks. NBT keys: `patternInput`, `patternOutput`, `patternPendingOutput`,
`patternUpgrades`, `craftStartedAt`, `craftReadyAt`, `craftCooldown`. Pattern pipes insert per slot through
`insertPatternPlanFromPatternPipe` and extract with `extractOutput`. A pipe facing the table is forced to SMART mode,
and satellites are not used for it (`useSatellites = advanced && !patternTable`).

### Upgrades

| Upgrade | Change |
|---|---|
| `InstantSatelliteUpgrade` (new, `ItemUpgrade.INSTANT_SATELLITE` = 27, has a recipe) | pattern crafting pipe only; item satellite inputs are inserted directly instead of routed |
| `AdvancedSatelliteUpgrade` | also allowed in the pattern crafting pipe; **required** there for any satellite use (inputs and byproduct outputs) |
| `FluidCraftingUpgrade` | also allowed in the pattern crafting pipe; required for fluid patterns |
| `CraftingByproductUpgrade` | also allowed in pattern item/fluid satellites (remote byproduct extraction); **not** allowed in the pattern crafting pipe itself, which always extracts byproducts |
| Speed upgrade | speeds up the Pattern Crafting Table |

Flags are read once per tick through [PatternCraftingUpgradeCache](../../src/main/java/logisticspipes/crafting/PatternCraftingUpgradeCache.java).
`ISlotUpgradeManager.hasInstantSatelliteUpgrade()` and the `UpgradeManager` registration are in the other areas' files.

---

## Changes to upstream classes

| Class | What changed and why |
|---|---|
| `request/RequestTreeNode` | Staged hand-off (`fullFillStaged`, `toPatternCraftingBranch`), same-item dict promise selection, byproduct claim counting, debug `toString`. Provider/crafter lookup moved to `InterestRegistry` + batched junction routing (03). |
| `request/RequestTree` | `CraftingRequestDebugManager.record` on every request outcome; `requestFluidPartial(…, IAdditionalTargetInformation)` overload. |
| `request/RequestHandler` | `refreshFluid` with `DisplayOptions` (supply / craft / both: craftable fluids listed with amount 0); `simulateFluid`; null-fluid guard in `requestFluid`. |
| `request/FluidCraftingTemplate` | Real item and fluid byproducts (was `TODO FluidCrafting: FIX`). |
| `request/BaseCraftingTemplate` | `getIngredients()` accessor. |
| `request/ICraftingTemplate`, `request/IExtraPromise` | Javadoc only (worded for fluids although the interfaces are generic). |
| `request/resources/DictResource` | `match_same_item` flag (serialized as bit 4, copied in clones); `copyForDisplayWith` keeps the requester (upstream passed `null`). |
| `request/resources/FluidResource`, `ItemResource` | `copyForDisplayWith` keeps the target (fluid); `toString` for debug. |
| `routing/FluidLogisticsPromise` | Mutable amount, `copyWithAmount`, `split` returns a `FluidExtraPromise` instead of throwing. |
| `routing/LogisticsDictPromise` | `copy()` override that preserves the dict resource. |
| `routing/ItemRoutingInformation` | Writes/reads `PatternTargetInformation` (`targetInfo` compound) so in-flight pattern items keep their target across saves. |
| `routing/order/LogisticsOrder` | `byproduct`, `byproductTarget`, `craftingReference`. |
| `routing/order/LogisticsOrderManager`, `LogisticsOrderLinkedList` | `removeOrder(order)` / `remove(order)` for cancellation. |
| `routing/order/LogisticsItemOrderManager` | `getAllOrders()`, target-aware `removeExtras`. |
| `routing/order/LogisticsFluidOrderManager`, `LogisticsFluidOrder` | Fluid EXTRA orders with null destination. |
| `pipes/PipeItemsSatelliteLogistics`, `pipes/PipeFluidSatellite` | `ISatellitePipe`, MUI GUI, `setNextFreeId`, Lombok id accessors. |
| `network/packets/satpipe/SatPipe{Next,Prev,SetID}` | Ignore pattern satellites. |
| `pipes/upgrades/{AdvancedSatellite,FluidCrafting,CraftingByproduct}Upgrade` | Allowed on the new pipes (see Upgrades). |
| `items/LogisticsFluidContainer` | `canExistInNormalInventory` / `canExistInWorld` → `true`. |
| `renderer/FluidContainerRenderer` | Fluid amount overlay. |
| `nei/NEILogisticsPipesConfig`, `nei/LogisticsCraftingOverlayHandler` | Shared overlay instance, `RequestTableGui` support. |
| `pipes/signs/ItemAmountPipeSign` | Compile fix for the router rework (`RouterIds.getBiggestSimpleID`). |
| `modules/ModuleCrafter`, `pipes/PipeItemsCraftingLogistics`, `gui/GuiCraftingPipe` + new `CraftingBlockingModePacket` | **Legacy crafter edits.** (1) The fluid ingredient config moved to a `List<FluidTank>` (`FluidTank_<i>` NBT), and `getFluidMaterial` now reads the tank instead of `_liquidInventory`. (2) A "Blocking" button / packet / `blockingModeEnabled` NBT, but the implementation is a placeholder (see gaps). (3) `System.out.println` in `itemArrived` / `itemLost`. (4) Tick early-return refactor and removal of `getWorld()`. |

---

## Known gaps / discrepancies

**Design-doc mismatches** ([rework-design-decisions.md](../rework-design-decisions.md)):

- **Advanced Satellite Upgrade is still required.** The decision says "Adv. satellite upgrade – remove, works by
  default". The code gates every satellite feature of the pattern crafting pipe on it: dispatch, template
  byproduct-satellite targets, remote byproduct extraction, `resolvePattern(Fluid)SatelliteTarget` and memory-chip
  linking. It was even newly allowed on the pattern pipe.
- **OreDict / NBT matching** are per-pattern flags anyone can toggle. The planned *OreDict filter* / *NBT filter*
  upgrades don't exist.
- **Crafting monitor** is a solid block (meta 7) plus a request-table popup. The design describes a *crafting monitor
  upgrade* for supplier pipes, request pipes and the request table, with concurrent-request limits (1/2/4/6). Neither
  the upgrade nor the limits exist.
- **Buffer upgrade** isn't implemented. Ingredient buffering is always on.
- **The sneaky upgrade** still decides the insertion side (`getInsertionOrientation`). The design moves this to a
  per-connection sneaky-faces setting in the pipe GUI.
- **The legacy crafter** was supposed to keep working simply and not get new features. Instead it got an unfinished
  blocking mode (below).
- **New legacy GUI.** `CraftingMonitorGui` is a new `LogisticsBaseGuiScreen` GUI, while the rule is MUI for new GUIs.

**Bugs / unfinished work seen in code:**

- **Legacy crafter fluid regression (save-compat).** `ModuleCrafter.getFluidMaterial` reads `_liquidTank`, which is
  only filled from NBT `FluidTank_<i>` or by the recipe-import path (`setFluid` at the import site). Fluids set through
  the GUI slots (`_liquidInventory`, still saved as `FluidInv`) are ignored, and existing worlds have no `FluidTank_*`
  tags. So old crafting pipes with fluid ingredients stop requesting those fluids. This breaks the "never break
  existing worlds" rule.
- **Legacy blocking mode is a stub.** `PipeItemsCraftingLogistics.tryInsertBufferedItems` has a placeholder loop that
  clears the list and sets `isBlocked = true` forever. `itemArrived` calls `craftingModule.itemArrived` twice when
  blocking is off. `@Override` was dropped from `itemLost`. The GUI button can only set blocking on. The packet has no
  distance or security check. There are `System.out.println`s in `ModuleCrafter`.
- **Fluids to satellites still teleport.** There is no routed fluid satellite path. B13 is only fixed for items, and
  only without the Instant Satellite Upgrade. Satellite reservations are not persisted (C9).
- **No GUI for main-output or byproduct satellites.** `patternMainOutputSlot` and
  `pattern(Fluid)ByproductSatelliteTargets` exist in NBT and are used by the template/extractor. The editor's satellite
  action (`applySatellite`) only sets **input** targets, and `TOGGLE_TYPE` does not carry byproduct targets over. Main
  output therefore always defaults to the first populated output.
- **No recipes** for the pattern crafting pipe, pattern item, pattern satellites, Pattern Crafting Table or Memory Chip
  (`RecipeManager` only adds the Crafting Monitor, which needs a pattern crafting pipe, and the Instant Satellite
  Upgrade).
- **Open issues from the tracker that still hold in code:**
  - C2 / F1: `PatternLostIngredientHandler` passes `5000` to `DelayedGeneric`, which multiplies by 1000 ns, so the
    delay is 5 ms and retries happen every tick.
  - F2: the debug manager is always on and formats every event.
  - C5: `ModuleProvider.fullFill` releases staged reservations on every order.
  - S5: `getKnownSatellitesFor` lists every satellite in every dimension, with no network filter.
  - G9 / B17: `PatternFluidStack` imports NEI `StackInfo` in common code.
  - C3 / B33: branch merge by `(patternSlot, inputSlot)` only.
  - B34 / C8: non-consumed items keep the BLOCKING/SMART lock.
  - B14: fluid extras may drop when storage is full.
  - B28: two fluids into a one-tank machine.
- **`CraftingMonitorCancelPacket` has no permission check.** Any client that knows a monitor's coordinates can cancel
  any instance reachable from it (no distance or security check, no open-container check).
  `PatternCraftingHudContent` also has no client-side guard.
- **Static registries are global across worlds.** `PatternCraftingInstanceRegistry` and the satellite sets are cleared
  only on server stop. Satellites are still not deregistered on chunk unload (L3).
- **pattern-crafting.md is partly stale.** Its line numbers refer to the ~2950-line pre-split module, which is now 1615
  lines plus handlers. It mentions `PatternCraftingCancellationResolver`, slot guessing for untagged arrivals
  (`findItemArrivalPattern`) and the `bufferedIngredients` fallback, none of which exist any more.
  `Br.copyAndReserve`, listed as removed in §8.7, still exists.

---

## Files

**docs**

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [DEBUG_GUI_NOTES.md](../../docs/DEBUG_GUI_NOTES.md) | Added | +28/-0 | Notes on the local DebugGuiEntry replacement and debug panel byte protocol |
| [PATTERN_CRAFTING_DEBUG.md](../../docs/PATTERN_CRAFTING_DEBUG.md) | Added | +30/-0 | Describes the Ctrl+Shift+T crafting debug window and recorded event types |
| [PATTERN_CRAFTING_IPATTERNSTACK.md](../../docs/PATTERN_CRAFTING_IPATTERNSTACK.md) | Added | +80/-0 | IPatternStack model, runtime handler split and NBT compatibility notes |
| [crafting-request-onboarding.md](../../docs/crafting-request-onboarding.md) | Added | +195/-0 | Walkthrough of RequestHandler/RequestTree/crafting template flow for new contributors |
| [pattern-crafting.md](../../docs/pattern-crafting.md) | Added | +469/-0 | Reference and issue tracker (S/D/C/F/L/G ids) for pattern crafting |

**docs.timelines**

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [timeline1.txt](../../docs/timelines/timeline1.txt) | Added | +311/-0 | Sample debug timeline dump of a staged LuV motor craft |

**logisticspipes.crafting**

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [AdjacentInventoryHandler](../../src/main/java/logisticspipes/crafting/AdjacentInventoryHandler.java) | Added | +742/-0 | Capacity simulation, insert, extract and isEmpty against the selected target |
| [CraftingMonitorGui](../../src/main/java/logisticspipes/crafting/CraftingMonitorGui.java) | Added | +375/-0 | Legacy-style GUI of the Crafting Monitor block: instance tree, refresh, cancel |
| [CraftingMonitorGuiProvider](../../src/main/java/logisticspipes/crafting/CraftingMonitorGuiProvider.java) | Added | +70/-0 | GuiProvider opening the Crafting Monitor GUI with initial entries |
| [CraftingMonitorTileEntity](../../src/main/java/logisticspipes/crafting/CraftingMonitorTileEntity.java) | Added | +60/-0 | Crafting Monitor block (meta 7): lists/cancels live instances on adjacent network |
| [IPatternSatellitePipe](../../src/main/java/logisticspipes/crafting/IPatternSatellitePipe.java) | Added | +19/-0 | Satellite interface adding a player-defined, uniqueness-suffixed name |
| [IStagedCraftingProvider](../../src/main/java/logisticspipes/crafting/IStagedCraftingProvider.java) | Added | +12/-0 | Hook letting a crafter take a whole request-tree branch (fullFillStagedCrafting) |
| [IStagedProviderReservation](../../src/main/java/logisticspipes/crafting/IStagedProviderReservation.java) | Added | +16/-0 | Provider stock reserve/release API used while a staged craft waits |
| [ItemMemoryChip](../../src/main/java/logisticspipes/crafting/ItemMemoryChip.java) | Added | +231/-0 | New Memory Chip item storing satellite refs; FAVORITES / APPLY_LAST_TO_RECIPE modes |
| [ModulePatternCrafting](../../src/main/java/logisticspipes/crafting/ModulePatternCrafting.java) | Added | +1615/-0 | Core module: sink, tick loop, LP craft/provide hooks, NBT, delegates to handlers |
| [PatternByproductExtractionResult](../../src/main/java/logisticspipes/crafting/PatternByproductExtractionResult.java) | Added | +31/-0 | Result of one remote (satellite) byproduct extraction |
| [PatternByproductExtractionTarget](../../src/main/java/logisticspipes/crafting/PatternByproductExtractionTarget.java) | Added | +29/-0 | Interface for satellites that can extract byproducts from their machine |
| [PatternByproductExtractionTargetCache](../../src/main/java/logisticspipes/crafting/PatternByproductExtractionTargetCache.java) | Added | +93/-0 | Resolves and caches (40 ticks) satellites assigned to pattern output slots |
| [PatternByproductPromise](../../src/main/java/logisticspipes/crafting/PatternByproductPromise.java) | Added | +9/-0 | Marker interface exposing a promise's PatternByproductTarget |
| [PatternByproductTarget](../../src/main/java/logisticspipes/crafting/PatternByproductTarget.java) | Added | +117/-0 | Identifies a byproduct's pattern/output slot, source order and extraction satellite |
| [PatternCraftingArrivalHandler](../../src/main/java/logisticspipes/crafting/PatternCraftingArrivalHandler.java) | Added | +167/-0 | Buffers routed ingredients only when they carry tracked order/delivery references |
| [PatternCraftingBlockingHandler](../../src/main/java/logisticspipes/crafting/PatternCraftingBlockingHandler.java) | Added | +327/-0 | Running-craft lock and satellite batch state for BLOCKING/SMART modes |
| [PatternCraftingBranch](../../src/main/java/logisticspipes/crafting/PatternCraftingBranch.java) | Added | +1424/-0 | Consumable snapshot of a request subtree: promises, extras, children, slicing |
| [PatternCraftingBufferDispatcher](../../src/main/java/logisticspipes/crafting/PatternCraftingBufferDispatcher.java) | Added | +239/-0 | Pushes complete buffered sets to target/satellites; tracks partial (pending) sets |
| [PatternCraftingCancelHandler](../../src/main/java/logisticspipes/crafting/PatternCraftingCancelHandler.java) | Added | +204/-0 | Cancels whole crafting instances and flushes their owned inputs to storage |
| [PatternCraftingCapacity](../../src/main/java/logisticspipes/crafting/PatternCraftingCapacity.java) | Added | +268/-0 | Computes safe item/fluid room and reservations for sinks and scheduler |
| [PatternCraftingHudHandler](../../src/main/java/logisticspipes/crafting/PatternCraftingHudHandler.java) | Added | +216/-0 | Builds and caches the pattern HUD snapshot (dirty flag + recheck interval) |
| [PatternCraftingHudState](../../src/main/java/logisticspipes/crafting/PatternCraftingHudState.java) | Added | +195/-0 | Serializable HUD snapshot: mode, per-pattern buffered inputs and requested outputs |
| [PatternCraftingIngredientPlanner](../../src/main/java/logisticspipes/crafting/PatternCraftingIngredientPlanner.java) | Added | +447/-0 | OreDict/NBT ingredient matching, satellite targets, buffered set plans |
| [PatternCraftingInstanceRegistry](../../src/main/java/logisticspipes/crafting/PatternCraftingInstanceRegistry.java) | Added | +140/-0 | Static index of live staged orders by instance plus cancellation tombstones |
| [PatternCraftingMonitorEntry](../../src/main/java/logisticspipes/crafting/PatternCraftingMonitorEntry.java) | Added | +102/-0 | Client-safe snapshot of one cancellable crafting instance |
| [PatternCraftingMonitorNode](../../src/main/java/logisticspipes/crafting/PatternCraftingMonitorNode.java) | Added | +93/-0 | Serializable progress-tree node for monitor views |
| [PatternCraftingMonitorRegistry](../../src/main/java/logisticspipes/crafting/PatternCraftingMonitorRegistry.java) | Added | +212/-0 | Builds monitor trees per request or per network; cancel by instance |
| [PatternCraftingOrder](../../src/main/java/logisticspipes/crafting/PatternCraftingOrder.java) | Added | +523/-0 | One staged output order: remaining sets, ingredient requests, dispatched byproducts |
| [PatternCraftingPersistence](../../src/main/java/logisticspipes/crafting/PatternCraftingPersistence.java) | Added | +696/-0 | NBT codec for orders, promises, resources and branches (RestoreNotReadyException) |
| [PatternCraftingPromise](../../src/main/java/logisticspipes/crafting/PatternCraftingPromise.java) | Added | +33/-0 | Item crafting promise carrying pattern slot and result amount per set |
| [PatternCraftingReference](../../src/main/java/logisticspipes/crafting/PatternCraftingReference.java) | Added | +92/-0 | Stable (instanceId, objectId) UUID identity for orders, deliveries, batches |
| [PatternCraftingResultExtractor](../../src/main/java/logisticspipes/crafting/PatternCraftingResultExtractor.java) | Added | +506/-0 | Extracts outputs/extras every 6 ticks, routes them or feeds same-pipe buffers |
| [PatternCraftingTargetSelector](../../src/main/java/logisticspipes/crafting/PatternCraftingTargetSelector.java) | Added | +241/-0 | Selected adjacent target side: auto-pick, sneak-wrench cycling, NBT, sync |
| [PatternCraftingTemplate](../../src/main/java/logisticspipes/crafting/PatternCraftingTemplate.java) | Added | +166/-0 | Item crafting template with one component per input slot and targeted byproducts |
| [PatternCraftingTemplateBuilder](../../src/main/java/logisticspipes/crafting/PatternCraftingTemplateBuilder.java) | Added | +246/-0 | Turns pattern main output into item/fluid templates; other outputs become byproducts |
| [PatternCraftingUpgradeCache](../../src/main/java/logisticspipes/crafting/PatternCraftingUpgradeCache.java) | Added | +44/-0 | Per-tick cache of fluid crafting, advanced and instant satellite upgrade flags |
| [PatternFluidByproductPromise](../../src/main/java/logisticspipes/crafting/PatternFluidByproductPromise.java) | Added | +39/-0 | Fluid extra promise that keeps its extraction target |
| [PatternFluidCraftingPromise](../../src/main/java/logisticspipes/crafting/PatternFluidCraftingPromise.java) | Added | +40/-0 | Fluid crafting promise with pattern slot and result amount per set |
| [PatternFluidCraftingTemplate](../../src/main/java/logisticspipes/crafting/PatternFluidCraftingTemplate.java) | Added | +122/-0 | Fluid-output crafting template with targeted item/fluid byproducts |
| [PatternIngredientAssignment](../../src/main/java/logisticspipes/crafting/PatternIngredientAssignment.java) | Added | +13/-0 | Record: concrete buffered stack chosen for one input slot |
| [PatternIngredientTarget](../../src/main/java/logisticspipes/crafting/PatternIngredientTarget.java) | Added | +27/-0 | Record: input ingredient plus local or satellite destination |
| [PatternItemByproductPromise](../../src/main/java/logisticspipes/crafting/PatternItemByproductPromise.java) | Added | +34/-0 | Item extra promise that keeps its extraction target |
| [PatternLogisticsCraftingTableTileEntity](../../src/main/java/logisticspipes/crafting/PatternLogisticsCraftingTableTileEntity.java) | Added | +667/-0 | Pattern Crafting Table (meta 6): timed vanilla crafting fed by pattern pipes |
| [PatternLostIngredientHandler](../../src/main/java/logisticspipes/crafting/PatternLostIngredientHandler.java) | Added | +171/-0 | Lost-ingredient retry queue keyed by delivery references, persisted |
| [PatternSatelliteByproductExtractor](../../src/main/java/logisticspipes/crafting/PatternSatelliteByproductExtractor.java) | Added | +213/-0 | Satellite-side extraction of ordered byproducts (needs byproduct upgrade) |
| [PatternSatelliteDispatchHandler](../../src/main/java/logisticspipes/crafting/PatternSatelliteDispatchHandler.java) | Added | +600/-0 | Builds dispatch plans: local insert, routed/instant item sats, direct fluid sats |
| [PatternSatelliteInfo](../../src/main/java/logisticspipes/crafting/PatternSatelliteInfo.java) | Added | +204/-0 | Network DTO describing a satellite for the editor's selector list |
| [PatternStackBufferHandler](../../src/main/java/logisticspipes/crafting/PatternStackBufferHandler.java) | Added | +319/-0 | Arrived-ingredient buffer with per-instance ownership and slot aggregates |
| [PatternStackRequestHandler](../../src/main/java/logisticspipes/crafting/PatternStackRequestHandler.java) | Added | +313/-0 | In-flight requested ingredients with ownership; persisted |
| [PatternStagedCraftingCoordinator](../../src/main/java/logisticspipes/crafting/PatternStagedCraftingCoordinator.java) | Added | +639/-0 | Creates/registers staged output orders; staged save/restore and pending entries |
| [PatternStagedCraftingScheduler](../../src/main/java/logisticspipes/crafting/PatternStagedCraftingScheduler.java) | Added | +263/-0 | Per-tick choice of how many sets each staged order may request |
| [PatternTargetInformation](../../src/main/java/logisticspipes/crafting/PatternTargetInformation.java) | Added | +33/-0 | Record target info: pattern slot, input slot, order and delivery references |
| [PipeFluidPatternSatelliteLogistics](../../src/main/java/logisticspipes/crafting/PipeFluidPatternSatelliteLogistics.java) | Added | +483/-0 | Pattern fluid satellite pipe: UUID/name, reservations, direct fluid insert |
| [PipeItemsPatternSatelliteLogistics](../../src/main/java/logisticspipes/crafting/PipeItemsPatternSatelliteLogistics.java) | Added | +776/-0 | Pattern item satellite pipe: UUID/name, reservations, routed inputs, chip linking |

**logisticspipes.crafting.pattern**

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [AbstractPattern](../../src/main/java/logisticspipes/crafting/pattern/AbstractPattern.java) | Added | +581/-0 | Pattern NBT accessor: entries, satellite targets, main output, OD/NBT flags |
| [DefaultPattern](../../src/main/java/logisticspipes/crafting/pattern/DefaultPattern.java) | Added | +24/-0 | Crafting pattern layout: 9 inputs, 3 outputs |
| [EditedPatternInventory](../../src/main/java/logisticspipes/crafting/pattern/EditedPatternInventory.java) | Added | +98/-0 | IInventory view of one pattern's entries for editor phantom slots |
| [ItemPattern](../../src/main/java/logisticspipes/crafting/pattern/ItemPattern.java) | Added | +125/-0 | Logistic Crafting Pattern item; opens handheld MUI; type toggle; tooltip |
| [PatternHandler](../../src/main/java/logisticspipes/crafting/pattern/PatternHandler.java) | Added | +234/-0 | Pipe pattern inventory wrapper with cached PatternRecipeSnapshots |
| [PatternRecipeImport](../../src/main/java/logisticspipes/crafting/pattern/PatternRecipeImport.java) | Added | +151/-0 | Recipe transferred from a viewer into a pattern (slots, inputs, outputs) |
| [PatternRecipeSnapshot](../../src/main/java/logisticspipes/crafting/pattern/PatternRecipeSnapshot.java) | Added | +193/-0 | Immutable parsed view of a pattern, reused until inventory changes |
| [PatternSource](../../src/main/java/logisticspipes/crafting/pattern/PatternSource.java) | Added | +101/-0 | Where an edited pattern lives: pipe slot or held item, read live |
| [ProcessingPattern](../../src/main/java/logisticspipes/crafting/pattern/ProcessingPattern.java) | Added | +24/-0 | Processing pattern layout: 16 inputs, 4 outputs |

**logisticspipes.crafting.patternStack**

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [IPatternStack](../../src/main/java/logisticspipes/crafting/patternStack/IPatternStack.java) | Added | +50/-0 | Item-or-fluid stack abstraction with typed NBT and legacy ItemStack read |
| [PatternFluidStack](../../src/main/java/logisticspipes/crafting/patternStack/PatternFluidStack.java) | Added | +129/-0 | Fluid pattern entry (mB); uses NEI StackInfo for container conversion |
| [PatternItemStack](../../src/main/java/logisticspipes/crafting/patternStack/PatternItemStack.java) | Added | +107/-0 | Item pattern entry; writes int lpCount to survive >127 stacks |
| [PatternStackHelper](../../src/main/java/logisticspipes/crafting/patternStack/PatternStackHelper.java) | Added | +126/-0 | Matching, aggregation, copy and display helpers for pattern stacks |

**logisticspipes.gui**

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [GuiCraftingPipe](../../src/main/java/logisticspipes/crafting/legacy/gui/GuiCraftingPipe.java) | Modified | +12/-1 | Legacy crafting pipe GUI gets a "Blocking" button (sets blocking on only) |

**logisticspipes.gui.hud**

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [HUDPatternCrafting](../../src/main/java/logisticspipes/gui/hud/HUDPatternCrafting.java) | Added | +333/-0 | HUD glasses view of patterns, buffered inputs, outputs and status |

**logisticspipes.gui.modularUI.pipes.patterncrafting**

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [HandheldPatternMui](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/HandheldPatternMui.java) | Added | +87/-0 | MUI for a held pattern item using the shared PatternEditor |
| [PatternCraftingContainer](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/PatternCraftingContainer.java) | Added | +61/-0 | MUI container implementing INEIRecipeTransfer for both pattern editors |
| [PatternCraftingSyncHandler](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/PatternCraftingSyncHandler.java) | Added | +234/-0 | Pipe GUI sync: HUD/state to client; cancel, return inputs, blocking mode |
| [PatternEditor](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/PatternEditor.java) | Added | +490/-0 | Shared editor widgets: entries, satellite badges/selector, type/flag buttons |
| [PatternEditorState](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/PatternEditorState.java) | Added | +104/-0 | Per-side selection state of a pattern editor |
| [PatternEditorSyncHandler](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/PatternEditorSyncHandler.java) | Added | +309/-0 | Editor C2S actions (select, clear, x2, type, OD/NBT, satellite, import) |
| [PatternGuiDraw](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/PatternGuiDraw.java) | Added | +63/-0 | Client text helpers for slot overlays |
| [PatternIngredientSlot](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/PatternIngredientSlot.java) | Added | +59/-0 | Phantom slot for a pattern entry with fluid amount and progress overlay |
| [PatternIngredientSlotSH](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/PatternIngredientSlotSH.java) | Added | +82/-0 | Phantom slot handler adjusting fluid entries in mB steps |
| [PatternSelectSlot](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/PatternSelectSlot.java) | Added | +104/-0 | Pattern slot: click selects for editing; renders primary result |
| [PipePatternCraftingMui](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/PipePatternCraftingMui.java) | Added | +299/-0 | Pattern crafting pipe MUI: 9 pattern slots, editor, target, cancel/return, mode |

**logisticspipes.gui.popup**

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [PatternRequestMonitorPopup](../../src/main/java/logisticspipes/gui/popup/PatternRequestMonitorPopup.java) | Added | +178/-0 | Legacy request table popup showing a request's pattern craft progress tree |

**logisticspipes.items**

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [LogisticsFluidContainer](../../src/main/java/logisticspipes/items/LogisticsFluidContainer.java) | Modified | +2/-2 | Fluid containers may now exist in inventories and in the world |

**logisticspipes.modules**

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [ModuleCrafter](../../src/main/java/logisticspipes/crafting/legacy/module/ModuleCrafter.java) | Modified | +67/-40 | Legacy crafter: fluid tanks for fluid slots, blocking packet, debug println |

**logisticspipes.nei**

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [LogisticsCraftingOverlayHandler](../../src/main/java/logisticspipes/nei/LogisticsCraftingOverlayHandler.java) | Modified | +9/-1 | Singleton instance; also handles the new RequestTableGui |
| [NEILogisticsPipesConfig](../../src/main/java/logisticspipes/nei/NEILogisticsPipesConfig.java) | Modified | +9/-3 | Registers overlay for RequestTableGui; shared overlay handler instance |
| [PatternCraftingRecipeTransfer](../../src/main/java/logisticspipes/nei/PatternCraftingRecipeTransfer.java) | Added | +43/-0 | Client-side NEI transfer into the selected pattern of either editor |
| [PatternRecipeImporter](../../src/main/java/logisticspipes/nei/PatternRecipeImporter.java) | Added | +189/-0 | Converts NEI recipes to crafting (3x3) or processing pattern imports |

**logisticspipes.network.packets.crafting.monitor**

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [CraftingMonitorCancelPacket](../../src/main/java/logisticspipes/network/packets/crafting/monitor/CraftingMonitorCancelPacket.java) | Added | +55/-0 | C2S cancel of one instance from the Crafting Monitor block |
| [CraftingMonitorContentPacket](../../src/main/java/logisticspipes/network/packets/crafting/monitor/CraftingMonitorContentPacket.java) | Added | +69/-0 | S2C Crafting Monitor entry list |
| [CraftingMonitorRefreshPacket](../../src/main/java/logisticspipes/network/packets/crafting/monitor/CraftingMonitorRefreshPacket.java) | Added | +30/-0 | C2S request to resend Crafting Monitor entries |

**logisticspipes.network.packets.debug**

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [CraftingRequestDebugRequest](../../src/main/java/logisticspipes/network/packets/debug/CraftingRequestDebugRequest.java) | Added | +62/-0 | C2S debug snapshot/clear request, operator-only (PacketGuards.isPrivileged) |
| [CraftingRequestDebugResponse](../../src/main/java/logisticspipes/network/packets/debug/CraftingRequestDebugResponse.java) | Added | +82/-0 | S2C debug snapshot text that opens/updates the client window |

**logisticspipes.network.packets.orderer**

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [PatternCraftingHudContent](../../src/main/java/logisticspipes/network/packets/orderer/PatternCraftingHudContent.java) | Added | +53/-0 | S2C pattern HUD state for watching players |
| [PatternCraftingWatchPacket](../../src/main/java/logisticspipes/network/packets/orderer/PatternCraftingWatchPacket.java) | Added | +67/-0 | S2C monitor roots for a watched request table request |

**logisticspipes.network.packets.pipe**

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [CraftingBlockingModePacket](../../src/main/java/logisticspipes/crafting/legacy/network/CraftingBlockingModePacket.java) | Added | +53/-0 | C2S blocking flag for the legacy crafting pipe (no security checks) |

**logisticspipes.network.packets.satpipe**

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [SatPipeNext](../../src/main/java/logisticspipes/crafting/legacy/network/SatPipeNext.java) | Modified | +7/-0 | Ignores pattern satellites (ids set through MUI) |
| [SatPipePrev](../../src/main/java/logisticspipes/crafting/legacy/network/SatPipePrev.java) | Modified | +7/-0 | Ignores pattern satellites (ids set through MUI) |
| [SatPipeSetID](../../src/main/java/logisticspipes/crafting/legacy/network/SatPipeSetID.java) | Modified | +8/-0 | Server ignores set-id packets for pattern satellites |

**logisticspipes.pipes**

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [ISatellitePipe](../../src/main/java/logisticspipes/crafting/legacy/satellite/ISatellitePipe.java) | Added | +10/-0 | Common satellite id interface used by PipeSatelliteMui |
| [PipeFluidSatellite](../../src/main/java/logisticspipes/crafting/legacy/satellite/PipeFluidSatellite.java) | Modified | +19/-3 | Implements ISatellitePipe and MUI (PipeSatelliteMui); setNextFreeId |
| [PipeItemsCraftingLogistics](../../src/main/java/logisticspipes/crafting/legacy/pipe/PipeItemsCraftingLogistics.java) | Modified | +31/-1 | Unfinished blocking-mode buffer stub; itemArrived forwards twice |
| [PipeItemsPatternCraftingLogistics](../../src/main/java/logisticspipes/pipes/PipeItemsPatternCraftingLogistics.java) | Added | +812/-0 | Pattern crafting pipe shell: orders, fluid orders, target, sat links, HUD, MUI |
| [PipeItemsSatelliteLogistics](../../src/main/java/logisticspipes/crafting/legacy/satellite/PipeItemsSatelliteLogistics.java) | Modified | +21/-4 | Implements ISatellitePipe and MUI (PipeSatelliteMui); setNextFreeId |

**logisticspipes.pipes.signs**

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [ItemAmountPipeSign](../../src/main/java/logisticspipes/pipes/signs/ItemAmountPipeSign.java) | Modified | +2/-2 | Compile fix: ServerRouter.getBiggestSimpleID moved to RouterIds |

**logisticspipes.pipes.upgrades**

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [AdvancedSatelliteUpgrade](../../src/main/java/logisticspipes/pipes/upgrades/AdvancedSatelliteUpgrade.java) | Modified | +3/-2 | Also allowed in pattern crafting pipes (gates satellite use there) |
| [CraftingByproductUpgrade](../../src/main/java/logisticspipes/pipes/upgrades/CraftingByproductUpgrade.java) | Modified | +5/-2 | Also allowed in pattern item/fluid satellites for remote byproduct extraction |
| [FluidCraftingUpgrade](../../src/main/java/logisticspipes/pipes/upgrades/FluidCraftingUpgrade.java) | Modified | +3/-2 | Also allowed in pattern crafting pipes; required for fluid patterns |
| [InstantSatelliteUpgrade](../../src/main/java/logisticspipes/pipes/upgrades/InstantSatelliteUpgrade.java) | Added | +34/-0 | New upgrade (meta 27): item satellite inputs inserted directly, no routing |

**logisticspipes.renderer**

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [FluidContainerRenderer](../../src/main/java/logisticspipes/renderer/FluidContainerRenderer.java) | Modified | +37/-0 | Draws fluid amount (k/m/b suffix) on fluid containers in inventories |
| [PatternItemRenderer](../../src/main/java/logisticspipes/renderer/PatternItemRenderer.java) | Added | +69/-0 | Renders a pattern as its primary result while shift is held |

**logisticspipes.request**

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [BaseCraftingTemplate](../../src/main/java/logisticspipes/crafting/legacy/template/BaseCraftingTemplate.java) | Modified | +4/-0 | Exposes the raw ingredient list (getIngredients) |
| [FluidCraftingTemplate](../../src/main/java/logisticspipes/crafting/legacy/template/FluidCraftingTemplate.java) | Modified | +56/-4 | Implements item and fluid byproducts for fluid crafting templates |
| [ICraftingTemplate](../../src/main/java/logisticspipes/crafting/legacy/template/ICraftingTemplate.java) | Modified | +15/-0 | Javadoc only (fluid-oriented comments on generic methods) |
| [IExtraPromise](../../src/main/java/logisticspipes/request/IExtraPromise.java) | Modified | +6/-0 | Javadoc only on registerExtras |
| [RequestHandler](../../src/main/java/logisticspipes/request/RequestHandler.java) | Modified | +85/-25 | Fluid refresh shows craftable fluids by DisplayOptions; adds simulateFluid |
| [RequestTree](../../src/main/java/logisticspipes/request/RequestTree.java) | Modified | +37/-5 | Records request snapshots to debug manager; fluid partial request with info |
| [RequestTreeNode](../../src/main/java/logisticspipes/request/RequestTreeNode.java) | Modified | +320/-35 | fullFillStaged branch handoff, same-item dict promises, byproduct claims, toString |

**logisticspipes.request.debug**

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [CraftingRequestDebugClient](../../src/main/java/logisticspipes/request/debug/CraftingRequestDebugClient.java) | Added | +323/-0 | Client Swing debug window opened with Ctrl+Shift+T, refreshes every second |
| [CraftingRequestDebugManager](../../src/main/java/logisticspipes/request/debug/CraftingRequestDebugManager.java) | Added | +738/-0 | Always-on server event log (60k) and request snapshots (24) for debug dumps |

**logisticspipes.request.resources**

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [DictResource](../../src/main/java/logisticspipes/request/resources/DictResource.java) | Modified | +9/-3 | New match_same_item flag (bit 4); display copies keep requester |
| [FluidResource](../../src/main/java/logisticspipes/request/resources/FluidResource.java) | Modified | +8/-1 | Display copies keep target; adds toString |
| [ItemResource](../../src/main/java/logisticspipes/request/resources/ItemResource.java) | Modified | +5/-0 | Adds toString for debug output |

**logisticspipes.routing**

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [FluidExtraPromise](../../src/main/java/logisticspipes/routing/FluidExtraPromise.java) | Added | +50/-0 | New fluid extra/byproduct promise registering fluid EXTRA orders |
| [FluidLogisticsPromise](../../src/main/java/logisticspipes/routing/FluidLogisticsPromise.java) | Modified | +12/-3 | Fluid promises can be split into FluidExtraPromise; copyWithAmount |
| [ItemRoutingInformation](../../src/main/java/logisticspipes/routing/ItemRoutingInformation.java) | Modified | +44/-0 | Persists PatternTargetInformation of routed items under targetInfo |
| [LogisticsDictPromise](../../src/main/java/logisticspipes/routing/LogisticsDictPromise.java) | Modified | +5/-0 | copy() now keeps the dict resource and type |

**logisticspipes.routing.order**

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [LogisticsFluidOrder](../../src/main/java/logisticspipes/routing/order/LogisticsFluidOrder.java) | Modified | +13/-3 | Allows destinationless (extra) fluid orders |
| [LogisticsFluidOrderManager](../../src/main/java/logisticspipes/routing/order/LogisticsFluidOrderManager.java) | Modified | +77/-1 | Fluid EXTRA orders: addExtra and targeted removeExtras |
| [LogisticsItemOrderManager](../../src/main/java/logisticspipes/routing/order/LogisticsItemOrderManager.java) | Modified | +23/-1 | getAllOrders and byproduct-target-aware removeExtras |
| [LogisticsOrder](../../src/main/java/logisticspipes/routing/order/LogisticsOrder.java) | Modified | +17/-0 | Adds byproduct flag, byproductTarget and craftingReference fields |
| [LogisticsOrderLinkedList](../../src/main/java/logisticspipes/routing/order/LogisticsOrderLinkedList.java) | Modified | +8/-0 | Adds remove(order) keeping extra index consistent |
| [LogisticsOrderManager](../../src/main/java/logisticspipes/routing/order/LogisticsOrderManager.java) | Modified | +9/-0 | Adds removeOrder(order) used by cancellation |
