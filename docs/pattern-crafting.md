# Pattern Crafting — Reference & Issue Tracker

> **2026-10-23:** the staged execution layer described in §1, §4 and §5 (branches, orders, coordinator, scheduler,
> buffers, result extractor, request-tree templates) is replaced by the v3 crafting system. See
> [crafting/v3-implementation.md](crafting/v3-implementation.md). World IO (§6), the pattern format (§3), satellites
> and the GUI (§7) are still current. Issues in §8 that live in deleted classes are gone with them.

Working reference for the pattern crafting system (branch `pattern-crafting`). Written 2026-09-27 from a full
read-through of the code; line numbers drift — re-grep before trusting them.

Paths are relative to `src/main/java/logisticspipes/`. Abbreviations:
`M` = `crafting/ModulePatternCrafting.java`, `P` = `pipes/PipeItemsPatternCraftingLogistics.java`,
`AIH` = `crafting/AdjacentInventoryHandler.java`, `RE` = `crafting/PatternCraftingResultExtractor.java`,
`Br` = `crafting/PatternCraftingBranch.java`, `Ord` = `crafting/PatternCraftingOrder.java`,
`Coord` = `crafting/PatternStagedCraftingCoordinator.java`, `Sched` = `crafting/PatternStagedCraftingScheduler.java`,
`PP` = `crafting/PatternCraftingPersistence.java`, `RTN` = `request/RequestTreeNode.java`.

---

## 1. Big picture

A pattern crafting pipe holds up to 9 **pattern items** (crafting 3x3→3 outputs, or processing 4x4→2x2, items and
fluids). When the LP request tree plans a craft from it, the pipe does not order ingredients up front the
old way. Instead the whole sub-tree is snapshotted into a **branch** and handed to the pipe (a "staged" craft). The pipe
then requests ingredients set by set as the target machine has room, **buffers** arrivals per pattern slot, pushes
complete sets into the adjacent machine (or into **pattern satellites** next to other machines), and extracts
results back into the network.

```
Request tree ──checkCrafting──> PatternCraftingTemplate (one component per *input slot*)
     │
 RTN.fullFill ─(node has IStagedCraftingProvider promise)─> RTN.fullFillStaged
     │   toPatternCraftingBranch(): snapshot node+subtree (promises, extras, children)
     │   reserveProviderPromises()  (IStagedProviderReservation on providers)
     ▼
 M.fullFillStagedCrafting → Coord.fulfill → registerOrder → PatternCraftingOrder
     │                                              (stagedCrafts + outputOrders + MonitorRegistry)
 each tick: Sched.requestIngredients → Ord.requestIngredients(sets) → Br.request(...)
     │          ingredients routed to the pipe with PatternTargetInformation(patternSlot, inputSlot)
     ▼
 M.itemArrived → ingredientBuffer (per slot) → pushBufferedIngredients
     │          → PatternDispatchPlan: local insert (AIH) + satellite insertPatternInput
     ▼
 RE.tick (every 6 ticks) → extract results by order → route to destination / local buffer / storage
```

## 2. File map

| Area | Files |
|---|---|
| Pipe shell | `P` — FluidRoutedPipe; item + separate fluid order manager, target selector, linked satellite id/UUID sets, HUD watchers. Almost all LP interfaces delegate to the module. |
| Core module | `M` (~2950 lines) — buffering, sink logic, blocking modes, dispatch, lost-item retry, HUD state, NBT. |
| Planning | `PatternCraftingTemplateBuilder`, `PatternCraftingTemplate`, `PatternFluidCraftingTemplate`, `PatternCraftingPromise`, `PatternFluidCraftingPromise` |
| Staged execution | `Br`, `Ord`, `Coord`, `Sched`, `IStagedCraftingProvider`, `IStagedProviderReservation`, `PatternCraftingCancellationResolver` |
| Ingredient model | `PatternIngredientTarget`, `PatternIngredientAssignment`, `PatternTargetInformation` (`NO_INPUT_SLOT=-1` wildcard) |
| World IO | `AIH` (insert/capacity/extract/isEmpty), `RE`, `PatternCraftingTargetSelector` |
| Buffers | `PatternStackBufferHandler` (arrived), `PatternStackRequestHandler` (in flight; shares map with `M.requestedIngredients`) |
| Persistence | `PP` (orders/promises/branches), buffer/request handlers' own NBT |
| Stack model | `crafting/patternStack/*` — `IPatternStack`, `PatternItemStack`, `PatternFluidStack`, `PatternStackHelper` |
| Pattern item | `crafting/pattern/*` — `ItemPattern` (`IGuiHolder`, opens the handheld MUI), `AbstractPattern`, `DefaultPattern`, `ProcessingPattern`, `PatternHandler`, `PatternSource` (pipe slot or held stack), `EditedPatternInventory`, `PatternRecipeImport` |
| Satellites | `PipeItemsPatternSatelliteLogistics`, `PipeFluidPatternSatelliteLogistics` (both `IPatternSatellitePipe`), `PatternSatelliteInfo`, `PatternSatelliteSelectorGui` (handheld only), `ItemMemoryChip` |
| Monitoring / HUD | `PatternCraftingHudState`, `gui/hud/HUDPatternCrafting`, `PatternCraftingMonitorRegistry`/`Node`, `gui/popup/PatternRequestMonitorPopup` |
| GUI | MUI: `gui/modularUI/pipes/patterncrafting/*` — shared editor (`PatternEditor`, `PatternEditorState`, `PatternEditorSyncHandler`, `PatternCraftingContainer` for NEI) used by `PipePatternCraftingMui` (+ `PatternCraftingSyncHandler` for pipe state) and `HandheldPatternMui`; `gui/modularUI/blocks/PatternCraftingTableMui`, `gui/modularUI/pipes/PipeSatelliteMui` (name field for pattern satellites) |
| Packets | `orderer/PatternCrafting{HudContent,WatchPacket}` (pipe, table and satellite GUIs sync through MUI) |
| Crafting table | `PatternLogisticsCraftingTableTileEntity` (`IGuiHolder`, opened by `LogisticsSolidBlock` via the MUI tile factory) — solid block meta 6, crafts vanilla recipes for the pipe |
| New request table | `crafting/requesttable/**` — `RequestTablePipe` (extends `PipeBlockRequestTable`), network grid, fluid storage, storage upgrades (damage 45–48) |
| NEI | `nei/PatternCraftingRecipeTransfer` (pipe and handheld MUI, through `PatternCraftingContainer`) |

**Core LP classes touched:** `RTN` (`fullFillStaged`, same-item dict promises), `ModuleProvider` /
`PipeItemsProviderLogistics` (staged reservations), `LogisticsTileGenericPipe` (render target connection, refuse
`injectItem`, MUI V2), `PipeTransportLogistics:~529` (reverse items reaching adjacent IInventory),
`LogisticsFluidManager.getBestReply` (skip pattern pipes as passive sinks), `PipeBlockRequestTable` (monitor packets),
`FluidCraftingUpgrade`, `request/debug/CraftingRequestDebugManager` (new, always-on), `DelayedGeneric` (used for retries).

## 3. Pattern item NBT (`AbstractPattern`)

- `patternItems`: list of `{slot:int, patternStackType:"solid"|"fluid", ...}`. Solid = vanilla ItemStack fields
  (**byte `Count`**), fluid = sub-compound `fluid`. Untyped entry = legacy ItemStack → `IPatternStack.fromItemStack`
  (anything that holds fluid becomes a fluid entry).
- Crafting: inputs 0–8, outputs 9–11, no `patternType`. Processing: inputs 0–15, outputs 16–19, `patternType="processing"`.
- Per-input satellite: `patternSatelliteTargets` (int[]), `patternSatelliteTargetUuids` (`"<slot>"→uuid`), fluid
  equivalents `patternFluidSatelliteTargets(Uuids)`. id 0 + no uuid = local target.
- Flags: `patternOreDictSubstitution`, `patternIgnoreNbt` (removed when false).
- `ItemPattern.fromStack` builds a fresh wrapper every call; accessors re-parse NBT every call (no cache).

## 4. Module runtime (`M`)

**State:** `patternInventory`(9), `ingredientBuffer`, `requestedIngredients` (both `Map<slot, List<IPatternStack>>`),
`cancelledPatternSlots`, `lostIngredients` (DelayQueue), `blockingMode`, `runningCraft`/`runningCraftInAdjacent`
(blocking lock), `activeSatelliteBatch` (not persisted), `pendingStagedCrafting` (NBT awaiting restore), HUD cache,
`throttledDebugEvents`.

**`tick()` (M:~281), server, every tick, unthrottled:**
1. `restoreStagedCraftingIfNeeded` — retry staged restore until it succeeds.
2. `cancelUnsupportedFluidPatternCrafts` — no fluid upgrade → cancel fluid patterns with state (TODO: not every tick).
3. `scheduleRequestedIngredientRestoreRetriesIfReady` — once after load, queue all requested entries as lost retries.
4. `retryLostItems` — ≤100 polls, `RequestTree.requestPartial` / `requestFluidPartial`.
5. `refreshSatelliteDispatchBatch` — release batch when satellites consumed inputs.
6. `pushBufferedIngredients` — OFF: all slots with complete sets; else only `runningCraft`.
7. `stagedCrafting.requestIngredients()` — scheduler.
8. `clearRunningCraftIfFinished` → `refreshRunningCraftState`.
9. `resultExtractor.tick()`.

**LP hooks:**
- `canProvide` (M:~603) — only offers existing EXTRA orders (fluid then item).
- `fullFill` item/fluid → CRAFTING order; staged → `Coord.fulfill`.
- `addCrafting` → template builder; `canCraft` linear over crafted items.
- `registerExtras` → destinationless EXTRA orders.
- `getAllItems` empty (buffer never provided).
- `P.getSpecificInterests` = crafted items (shadows module's ingredient interests).
- `sinksItem` (M:~226) at ItemSink priority; room from `spaceFor`/`spaceForFluid`.
- `itemArrived` (M:~1050) — infer slot if no `PatternTargetInformation`; cancelled slot → storage; fluid container →
  `fluidArrived` (all-or-nothing); items: `accepted = min(size, max(requested, arrivalSpace))`, remainder stays on stack.
- `itemLost` → subtract requested, queue retry.

**Blocking modes** (effective mode forced to SMART when target is the pattern crafting table):
- **OFF** — no lock; room = `(1+availableSets)*amount - buffered`; pushes as many sets as fit, every slot.
- **BLOCKING** — target must be `AIH.isEmpty` before push, one set at a time; `insert` clamps to `missingFor`.
- **SMART** — same lock, running slot may push many sets without waiting; other slots wait for empty target.
- An active satellite batch blocks **all** slots in every mode until every reserved satellite reports consumed.

**Dispatch:** `pushBufferedIngredientsFor` → `completeBufferedSets` → `findInsertableBufferedPlan` (most sets down)
→ `buildDispatchPlan` (local / item-sat / fluid-sat) → `PatternDispatchPlan.dispatch` (reserve sats, insert local,
insert sats) → `removeBufferedPlan` **only on full success** → set lock/batch.

**Cancel / return:** `cancelPatternCraft` → coordinator returns affected slots → recall satellite batch, clear
requested, flush buffer to storage (`sendStack(-1)`), clear lock, mark slots cancelled (late arrivals → storage).
`returnStoredInputsToStorage` = same for all slots + clears lost queue. `onAllowedRemoval` drops patterns + buffer.

**Fluids:** only arrive as routed LogisticsFluidContainer items (pipe tanks disabled, `P:425-449`); need fluid crafting
upgrade; local dispatch via `IFluidHandler.fill`; outputs via separate fluid order manager.

## 5. Staged execution

**Template:** `PatternCraftingTemplateBuilder.addCrafting` — first slot whose output matches. **One component per
input slot** (`PatternTargetInformation(slot, inputSlot)`); OreDict/ignoreNBT flags set here
(`DictResource.match_same_item` → RTN accepts only one item variant for the whole amount). Other outputs = byproducts.

**RTN.fullFillStaged (RTN:~276):** for each staged promise: `branch.copyForAmount(n)` → `reserveProviderPromises` →
`provider.fullFillStagedCrafting` → success: `branch.reserve(n)`; null: `releaseProviderPromises`. Non-staged
promises: `promise.fullFill` + `branch.reserve` (no sub-node fulfil — see C4).

**Branch (`Br`):** consumable slice of the tree: `requestType`, `info`, amounts (original/remaining, crafting sets),
`PromiseState`s (promise, remaining, providerReserved), extras/byproducts, `subRequests` (merged by
`mergeCompatibleBranches` when `info`+`requestType` equal), runtime `liveOrders`.
`request(amount)` walks promises: staged CRAFTING → `copyForAmount` + handoff + `reserveSubRequestsFor`; plain CRAFTING
→ `fullFill` + `requestSubRequestsFor`; PROVIDER/EXTRA → `fullFill`. Children allocated proportionally with cumulative
ceil (`allocateChildrenForCraftingAmount`). Overflow extras registered only on final slice; byproducts per set range.

**Order (`Ord`):** one per staged output: `patternSlot`, `resultAmountPerSet`, `ingredientBranches`,
`remainingSets` (= ceil(req/perSet) capped by `availableSetsFromBranches`), `outputOrder`, `preRequestedIngredients`.
`requestIngredients(sets)` per input target: request `perSet*sets - preRequested` from matching branches
(`branchTargetsInputSlot` compares **inputSlot only**), record in request handler, commit whole sets.

**Lifecycle:** registered (stagedCrafts + outputOrders + registry) → requesting each tick → fully requested
(released, leaves stagedCrafts, stays in outputOrders) → output finished → removed. `outputOrders` pruned lazily only
from `remainingOutputAmount` (HUD). Same-pipe-output orders stay staged until fully requested.

**Scheduler:** per-slot re-entrancy guard; blocking lock skips other slots; sets = min(remaining,
`orderableSetsForPattern`, `availableSetsFromBranches`). Orderable = min over ingredients of (room − in flight)/perSet;
BLOCKING capped at 1 (+1 if target empty).

**Provider reservations:** runtime `Map<ItemIdentifier,Integer>` subtracted in provider `canProvide`/`getAllItems`.
Items only (fluids not reserved). Re-reserved on restore for still-requesting orders.

**Cancellation resolver:** seed = active orders on slot, expand to fixpoint up (ancestors) then down (descendants).
`isParentOf` = liveOrders→registry link, **fallback: child output target patternSlot == parent.patternSlot**.

## 6. World IO

- **Target selection** (`PatternCraftingTargetSelector`): persisted side (`patternConnectedInventoryDirection`),
  cached `AdjacentTile` re-validated on **every** `getConnected()` call (fresh getTileEntity + getTankInfo).
  UNKNOWN → auto-pick first usable neighbour. Sneak-wrench cycles. Transport stays disconnected from target
  (`P.disconnectPipe`), connection only rendered.
- **Capacity:** IInventory → `roomFor` upper bound + binary search over snapshot copies (`canFitPatternSetsDisregardingSlots`);
  fluids → binary search `fill(simulate)` **per fluid independently**. Pattern table → per-slot API.
- **Insert:** items via `InventoryHelper.getTransactorFor(tile, opposite)` then `transactor.add(stack, M.getInsertionOrientation(tile))`;
  fluids via `fill(getFluidInsertionOrientation)`. Assignments inserted one at a time, abort on first short insert.
- **Extract** (`RE`, every 6 ticks while CRAFTING/EXTRA orders): items ≤64/16 stacks per pass by top order, exact
  ItemIdentifier, sided adapter respects `canExtractItem`; routed as Active item / to local buffer (same-pipe
  intermediates) / to storage (destinationless extra). Fluids: one order per pass, untyped drain, wrapped in container.
- **Persistence:** buffer `patternIngredientBuffer` (legacy `bufferedIngredients`), requested
  `patternRequestedIngredients`, PP `kind` discriminator (item/dict/fluid/patternItem/patternFluid/*Extra), routers
  as UUID string. No format version. Any unresolved piece → `RestoreNotReadyException` → whole restore retried every tick.

## 7. Satellites, HUD, GUI, packets

- **Registry:** static `WeakHashMap`-backed sets per satellite class; `findById`/`findByUuid` linear; removal only in
  `onAllowedRemoval` (no chunk-unload handling); `cleanup()` on server stop. Fluid pattern satellites also register in
  legacy `PipeFluidSatellite.AllSatellites` and share its id space; item ones don't.
- **Resolution:** UUID first, then numeric id fallback. `resolvePatternSatelliteTarget` auto-links whatever the pattern
  names — the link list is not access control.
- **Reservation:** `reservedOwnerRouter` (router *simple id*) + `reservationBaseline` counts; not persisted.
  Satellite `insertPatternInput` inserts **directly** into its adjacent inventory (no routing).
- **GUI:** `PipeSatelliteMui` (id, next free id and, for `IPatternSatellitePipe`, the name through a synced string; the
  server may add a uniqueness suffix, which syncs back). The pattern satellites' `onWrenchClicked` opens it too, so
  legacy wrenches get MUI. Plain LP satellites keep `GuiSatellitePipe` on legacy wrenches.
- **Memory chip:** stores `patternSatelliteRefs` + last satellite; modes FAVORITES (link all on sneak-click pipe) /
  APPLY_LAST_TO_RECIPE (`assignSatelliteToAllPatternIngredients`). Clicking a satellite with a renamed chip renames it.
- **HUD:** `startWatching` (mode 1) → server sends content + `PatternCraftingHudContent`. `checkHudUpdate` sends when
  `shouldRefreshHudState()` (dirty or ≥20 ticks) and state `!equals` old.
- **MUI:** the pattern is always the pattern item's NBT, read live through a `PatternSource` (`of(pipe)`: 9 slots,
  links satellites on assign; `heldBy(player, slot)`: 1 slot, the pipe links on resolve). `PatternEditorState` selects
  one, `EditedPatternInventory` exposes its entries to the phantom editor slots (limit 127, patterns rejected).
  `PatternEditorSyncHandler` C→S: SELECT, PATTERN_ACTION, SATELLITE, IMPORT, REFRESH_SATELLITES; S→C: SATELLITES,
  SELECT. The pipe's `PatternCraftingSyncHandler` ("pattern_crafting") adds C→S CANCEL, RETURN_INPUTS, BLOCKING_MODE
  and S→C HUD, STATE (per tick diffed by string key). The handheld GUI ("pattern_editor") locks the held slot.
- **Monitor:** `PatternCraftingMonitorRegistry` static synchronized IdentityHashMap order→PatternCraftingOrder;
  request table sends `PatternCraftingWatchPacket` every 2 ticks per watched request.

---

## 8. Issues

Status column: **V** = I re-verified in code myself; **C** = reviewer traced it in code (confirmed); **P** = plausible,
needs in-game check. IDs are stable — reference them in commits.

Legacy tags (see the decision in §10, legacy GUIs get deleted):
- **LEGACY**: the code lives only in legacy GUI code. Not important; don't fix it, delete it with the GUI.
- **PARTLY LEGACY**: the legacy part becomes obsolete; the rest still matters (the note says which part).
- **MUI-REQ**: becomes a requirement for the MUI replacement, so don't reintroduce it there.

### 8.1 Security / exploits (fix first)

| ID | Sev | Status | Issue | Where | Fix idea |
|---|---|---|---|---|---|
| S1 | HIGH | **FIXED — packet deleted** (tested in game 2026-10-01); the table GUI syncs slots and progress through MUI | S→C packet `PatternCraftingTableUpdate` processed on server → client-authored NBT replaces table inventories = **item creation**. LP has no packet direction guard. | `network/packets/block/PatternCraftingTableUpdate:40`, table `readUpdatePayload:176` | `if (!MainProxy.isClient(player.worldObj)) return;` — consider generic S2C-only flag on ModernPacket |
| S2 | HIGH | **FIXED — `PacketGuards.isOnClient`** · _PARTLY LEGACY: the packet goes away when the request table moves to MUI_ | `RequestTableSetCursorPacket.processPacket` → `player.inventory.setItemStack(getStack())` on server = **item creation**. | `crafting/requesttable/...RequestTableSetCursorPacket:23` | same client-only guard |
| S3 | HIGH | **FIXED** — the legacy packets are deleted along with the legacy GUI; the MUI open path checks `settings.openGui`; `PatternSatelliteSetName` requires `canConfigurePipe` (≤8 blocks + security) | Pattern pipe packets have no distance/security/open-container check. `PatternPipeSelectPacket` opens the legacy container remotely → steal/swap patterns. Cancel/ReturnInputs/Mode/SlotAction → remote grief. MUI open path (CoreRoutedPipe:~1029) skips `settings.openGui`. | `network/packets/gui/Pattern*` | require `player.openContainer` bound to this pipe (or distance ≤64 + security); don't reopen GUI from select |
| S4 | HIGH | **FIXED — all request table C→S packets resolve the table from `player.openContainer` (`PacketGuards.getOpenRequestTable`), client coords/dimension ignored** · _PARTLY LEGACY + MUI-REQ: the packets go away; MUI sync handlers must keep the bound-to-open-container rule_ | Request table interact/submit/refresh packets: no open-container check, client-supplied dimension → pull items from any loaded table anywhere. | `RequestTableNetworkInteractPacket:58`, `RequestTableSubmitPacket:41`, `RequestTableRefreshPacket:44` | require open `RequestTableContainer` for that table; use `player.worldObj` |
| S5 | MED-HIGH | C | Satellites: no network/ownership check — ingredients teleported into any satellite anywhere; selector sends **every** satellite (coords/dim/name) on server to any player. | `M` dispatch ~2801/2808, `SyncHandler.applySatellite:384`, `getKnownSatellitesFor` | require router reachability at assign + dispatch; filter list to player's network |
| S6 | MED | **FIXED — `PacketGuards.isPrivileged` (op or integrated-server owner)** | `CraftingRequestDebugRequest` packet: anyone can dump/clear all players' requests. | `request/debug/...` | op-only |
| S7 | LOW | C · _slots part FIXED and tested in game (2026-10-01): `EditedPatternInventory` rejects pattern items; import caps still to check_ | Pattern NBT unbounded: client import / phantom slots / nested patterns (B18) can bloat NBT until kick. | `PatternRecipeImport`, `PatternInventory.isItemValidForSlot:85` | reject `ItemPattern` in slots, cap sizes |

### 8.2 Item duplication / loss

| ID | Sev | Status | Issue | Where | Fix idea |
|---|---|---|---|---|---|
| D1 | HIGH |  **FIXED** — every inserted amount leaves the buffer per assignment; a partly inserted set becomes `pendingDispatch` and is finished before any other push; satellite amounts merged per satellite+item for the room check  | **Partial dispatch duplicates.** Assignments inserted one-by-one; first short insert returns false but already-inserted items stay in machine and in buffer (`removeBufferedPlan` only on success) → reinserted later. Same for local-then-satellite in `dispatch()`; satellite capacity checked per assignment not cumulatively. | `AIH:231-244`, `M:~2786-2815`, `M:~1938-1953` | subtract actually-inserted amounts from buffer per assignment; check/reserve satellites before local insert; cumulative sat simulation |
| D2 | HIGH |  **FIXED, tested in game 2026-10-01** — `getInsertionOrientation` = sneaky side, else `orientation.getOpposite()`; snapshot, room, transactor and fluid fill all use it  | **Wrong face for sided inventories.** `getInsertionOrientation` returns `tile.orientation` (pipe→tile, far face); TransactorSimple uses it for `getAccessibleSlotsFromSide`/`canInsertItem`. Capacity check uses the correct `opposite`. Core LP uses `getPointedOrientation().getOpposite()` (CoreRoutedPipe:1690). → stuck or (multi-ingredient) D1. Sneaky upgrade side also ignored by snapshot. | `M:1329-1335`, `AIH:449` | return `tile.orientation.getOpposite()` when no sneaky; same side everywhere |
| D3 | HIGH |  **FIXED** — `canFitFluids`: same fluids merged, each simulated, several distinct fluids must also fit the reported tanks together (no tank info → one fluid at a time). _In game (2026-10-01): no dupe or void, but nothing is inserted; at least one fluid should go in in non-blocking mode (B28)_  | Fluid capacity simulated per fluid against empty handler → 2 fluids into 1 tank both pass, second fails for real → D1. `availablePatternSetsForFluids` over-reports. | `AIH:208-219, 247-268` | cumulative check / insert fluids first and abort cleanly |
| D4 | HIGH |  **FIXED for pattern data** — `PatternItemStack.writeItem/readItem` add an int `lpCount` (buffers, requested, lost queue, pattern entries, orders/promises via `PP`); request table stacks still open (left for the request table migration)  | **Byte `Count`**: ItemStack NBT in 1.7.10 stores Count as byte. Buffers/requested/orders/promises with >127 items truncate or go ≤0 on save → voided or restore throws forever (L3). Also `MULTIPLY_TWO` 64→128 → -128 → ingredient vanishes; request table stack upgrades (up to 2048) lose items on save & vanilla slot sync. | `PatternItemStack.writeToNBT`, `PP:374`, `Ord:646`, `SimpleStackInventory.writeToNBT:171`, `PatternPipeSlotActionPacket:83`, `RequestTablePipe:131` | write int amount separately; clamp multiply (`canMultiply`); cap/sync table stacks. *Check no GTNH mixin widens Count.* |
| D5 | MED | C | Snapshot merge uses NBT-blind `equalsForCrafting`; real inventory uses exact equality → over-estimates room → D1. Merging also skips `isItemValidForSlot`. `amountOf`/`BH.amount(ItemIdentifier)` NBT-blind while `remove` exact. | `AIH:365, 500` | exact equality + validity check |
| D6 | MED |  C · _mitigated by D1: a clamped short insert now becomes a pending set, no dupe_  | BLOCKING insert clamp (`missingFor`) not considered by `canInsertPatternIngredients` → short insert → D1. | `AIH:424-428` | apply clamp in check too |
| D7 | MED | P | Non-sided targets: extraction pulls any exact-match item from any slot → re-extracts unconsumed ingredients (catalysts, container-return), or player items from shared chests. | `AIH:561-575` | track produced amounts / output slots |
| D8 | MED | P | Removing/swapping a pattern with buffered ingredients: `ingredientBuffer.removeAll(slot)` voids them; new pattern in same slot inherits old buffer. | `M:~1911`, `M:~2207` | `flushBufferedIngredientsToStorage(slot)` on pattern change |
| D9 | MED | P | Same-pipe intermediate not fully accepted locally → remainder to storage but still counted `sendSuccessfull` → parent waits. | `RE:246-249, 414` | clamp to local room / defer |
| D10 | MED | C | Pattern crafting table drops container items (buckets, GT tools) as EntityItems. | table `moveRemainingInputs:605` | put into output/pending |
| D11 | LOW | C · _PARTLY LEGACY + MUI-REQ: vanilla-Slot pickup is in the legacy container (MUI slots must cap at max stack); remainder voiding is server logic and stays_ | Request table: `addCompressed` ignores max stack → player can pick up 64 swords as one stack; `moveInternalItemToPlayerInventory` voids remainder. | `RequestTablePipe:171,337,962,...; :394` | capped slot |
| D12 | LOW | C | Missing fluid on load silently dropped; legacy untyped fluid containers reinterpreted as mB. | `PatternFluidStack.readFromNBT`, `IPatternStack.readFromNBT` | log + keep raw NBT |

### 8.3 Stuck crafts / wrong accounting

| ID | Sev | Status | Issue | Where | Fix idea |
|---|---|---|---|---|---|
| C1 | HIGH | V | **Lost ingredient never re-requested.** `itemLost` removes from requested *then* queues retry; retry capped by `outstandingRequestedRetryStack` = still-requested (now 0) → retry dropped as "received". Slot stuck "buffered ingredients incomplete". | `M:~1007-1040`, `M:~2526-2575` | don't decrement in `itemLost` (let retry consume) or re-add on successful retry |
| C2 | HIGH | V | **Retry delays are µs not ms.** `DelayedGeneric` does `delay*1000` ns → "5000" = 5 ms → retries every tick; each can `requestPartial` + scan all routers (`hasLivePatternOutputOrderForRequested`). Unobtainable ingredient re-requested forever, persisted in NBT. | `utils/DelayedGeneric:17`; `M:546, 715, 1024, 1036, 2514` | pass real delay (×1000 here; legacy callers depend on current semantics), backoff + cap |
| C3 | HIGH | C | **Multi-crafter node split mixes children.** Node's `subRequests` contains children of *all* templates on it; `allocateChildrenForCraftingAmount` scales all by the parent ratio; merge by equal `info` combines pipe A/B children; `branchTargetsInputSlot` ignores patternSlot → items to wrong pipe, both stall. | `RTN:276-301`, `Br:859-897, 963-988`, `Ord:473` | per-template child lists; compare patternSlot+target |
| C4 | HIGH | V | Regular LP crafter sharing a node with a staged promise: `fullFillStaged` fulfils its promise but never places its sub-requests → waits forever. | `RTN:~296-301` | request its allocated children / `subNode.fullFill()` |
| C5 | HIGH | V | **Providers release staged reservations on every order.** `ModuleProvider.fullFill` always `releaseStagedCrafting` → unrelated request frees reserved items → staged craft later can't get them. | `modules/ModuleProvider:282`, `PipeItemsProviderLogistics:359` | release only for promises the branch reserved (branch releases before fullFill when `providerReserved`) |
| C6 | HIGH | C | `canProvide` promises the same extra multiple times in one tree (no `root.getAllPromissesFor` subtraction like upstream `ModuleCrafter`), ignores filters. | `M:~621-639` | sum extras − existing promises; apply filters |
| C7 | HIGH | C | **Staged restore can freeze forever & wipe new orders.** Any unresolvable router (broken while offline / unloaded) → retry every tick forever (exception + NBT parse each tick). While pending: `writeToNBT` saves old snapshot, new staged crafts still accepted; on eventual success `clear()` orphans them (output orders stuck, reservations leak). | `M:411-417, 511-530`, `Coord:135-194`, `PP` | per-order restore, drop/refund unresolvable after N tries, gate fulfil while pending or merge |
| C8 | MED-HIGH | C/P | BLOCKING/SMART never unlocks if target has permanent contents (circuits, molds, fuel, unregistered chance byproducts): `isEmpty` scans all raw slots/tanks. | `AIH:511-539`, `M:~2241-2256` | only insertable side slots / only pattern items / track inserted batch |
| C9 | MED-HIGH | C/P · _the D1 `pendingDispatch` isn't persisted either: after a reload the rest of a partly inserted set stays in the buffer_ | Active satellite batch holds direct satellite objects; satellite broken/unloaded → `isConsumed` never true → whole pipe blocked. Batch & reservation not persisted (lost guarantees on reload; stale simple-id lock). | `M:~2872-2941, 1406, 1873`, sat `reserveFor:236` | store UUID, re-resolve, timeout; key reservation by router UUID, persist |
| C10 | MED | C | Fluid orders dropped: no fluid handler found → `sendFailed()` every 6 ticks (removes order, no re-request). Transient (wrench cycle, target replaced). | `RE:266-270` | just return like item path |
| C11 | MED | C | Rejected staged handoff releases reservations inherited from parent (copy has `providerReserved=true`) → double release later. | `Br:351-373` | release only what the copy itself reserved |
| C12 | MED | C | Cancel with child orders in another pipe: removes output order on wrong manager (no-op), releases child reservations without unstaging, marks same-index local slot cancelled. | `Coord:289-311`, `Ord:384-394` | route cancel to owning module |
| C13 | MED | C | Cancel resolver fallback matches patternSlot numbers across pipes → cascades into unrelated orders; never notifies remote parents. | `PatternCraftingCancellationResolver:94-103` | require destination is this module |
| C14 | MED | P | Extra promised as plain item not removed when registered as DictResource with flags (`removeExtras` compares flags). | `M:~652-655, 757-763` | keep original identifier on promise |
| C15 | MED | P | Passive items sunk: `sinksItem` offers room with nothing requested (OFF mode); interests = crafted items → e.g. ingot↔block patterns pull passing items and craft unrequested stuff, clogging machine. | `M:~226, 436`, `P:568` | only offer room for requested amounts |
| C16 | MED | P | Reload re-requests ingredients still in transit (only pattern-pipe outputs checked) → orphan surplus buffers. | `M:532-554` | longer delay / timeout-based |
| C17 | MED | P | Overflow extras deferred to final slice double-booked by sibling EXTRA promises fulfilled earlier. | `Br:519-549` | register before sibling fulfil / subtract claimed |
| C18 | MED | P | Same-pipe order finished early stays staged forever if branches can't supply rest; `releaseAll` iterates `outputOrders` only → leaks. | `Sched:87-99` | iterate union; timeout / parent-alive check |
| C19 | MED | C | Fluid provider promises not reserved during staged wait. | `Br:605-608` | fluid reservation |
| C20 | MED | C | Fluid drain untyped: first fluid ≠ wanted → order deferred forever; extraction side ignores sneaky upgrade while insertion uses it. | `AIH:590-609` | typed drain, consistent side |
| C21 | MED | P | Pattern table: `findRecipe` picks first match; unexpected output never extracted, `canAcceptInput` refuses input while any output → deadlock. | table `:384, 506, 592` | match recipe to pattern output, eject unexpected |
| C22 | LOW-MED | C | Same item in two output slots under-counted (`getOutputs` not aggregated). | `PatternCraftingTemplateBuilder:127,150` | `getAggregatedOutputs()` |
| C23 | LOW | P · _PARTLY LEGACY: `PatternContainer` goes away, but `fromItemStack` and MUI `PatternIngredientSlot` still convert_ | Crafting patterns with filled containers (milk bucket, cells) auto-convert to fluid → need fluid upgrade, table can't craft. | `IPatternStack.fromItemStack:14`, `PatternContainer:112` | convert only for processing / explicit toggle |
| C24 | LOW | P | Editing pattern mid-craft → branches don't match → order stalls holding reservations. | `Sched:~123` | cancel on pattern change |
| C25 | LOW | C | Earlier failed promise → later slices copy wrong promise state; failed non-final slice → overflow extras never registered. | `Br:813-831`, `RTN:283` | track by promise identity |
| C26 | LOW | C | `writePromiseStates` skips unwritable promise but keeps remaining; `writeStagedOrders` skips failed order → output order restored without staged state. | `Br:1014`, `Coord:405` | fail consistently |
| C27 | LOW | P | Cancel doesn't cancel already-placed provider/crafter orders; new order on slot clears cancelled flag → old arrivals become surplus. | `Coord:335` | cancel liveOrders / tag with order id |
| C28 | LOW | C | Energy charged before extraction (pattern table: every attempt even when nothing extracted). | `AIH:546, 571-575` | charge extracted amount |
| C29 | LOW | C | Dict orders only extract exact item (`wanted.getAsItem()`). | `AIH:569` | dict match |
| C30 | LOW | C | Auto-selected target side not marked dirty / synced. | `PatternCraftingTargetSelector:151-160` | markDirty + sync |
| C31 | LOW | **REMOVED with legacy GUI** | Legacy `PatternCraftingPipeMode` / `setBlockingMode` don't mark dirty. | `M:~194` | markDirty |

### 8.4 Performance

| ID | Sev | Status | Issue | Where | Fix idea |
|---|---|---|---|---|---|
| F1 | HIGH | C | See C2 — retries every tick with network-wide router scans. | | |
| F2 | HIGH | C | **Debug always on.** `debugEvent` always `String.format`s (+ toString of orders/resources) into global synchronized deque (60k); args computed eagerly (full plan builds just for log text); `debugEventThrottled` keys on formatted text (counters defeat it) and map grows forever; `CraftingRequestDebugManager.record` from every `RequestTree` request incl. non-pattern (full recursive `toString`). | `M:445-480`, `request/debug/CraftingRequestDebugManager:27-86`, `RequestTree:175-281` | global flag checked before formatting; key throttle by template; prune |
| F3 | HIGH | C | Request table crafting grid: ~256 full recipe-list scans per shift-click (`cacheRecipe` + second scan, ×2 passes, ×64 crafts). | `RequestTablePipe:704, 787, 851` | use cached recipe, recompute on matrix change |
| F4 | HIGH | C | Request table: full network scan + packet on every arriving item / click. | `RequestTablePipe:305, 1067, 1076` | dirty flag, send ≤ every 10–20 ticks |
| F5 | MED | C | **Pattern NBT re-parsed in hot paths** (`fromStack`, `getPatternStackInSlot` linear scan + ItemStack alloc, `isConfigured`, `getIngredientTargets` with linear sat lookup). Nested in `requestedItemAmount`/`matchingBufferedItemAmount` (per stored stack), `maxAcceptedItemAmount` (loops room→1 building plans), `completeBufferedSets`, scheduler, `cancelUnsupportedFluidPatternCrafts` every tick. | `M:1451-1465, 1594, 1692-1741, 1844, 2067, 578`, `AbstractPattern:97` | parsed-pattern cache per slot invalidated by inventory listener; binary search in maxAccepted |
| F6 | MED | C | Target re-validated on every `getConnected()` (getTileEntity + 4× getTankInfo); dozens of calls per tick; UNKNOWN scans 6 sides each call. | `PatternCraftingTargetSelector:47,135-141,223` | per-tick cache |
| F7 | MED | C | Sink capacity queries copy entire target inventory per binary-search step per ingredient, per routing query. | `AIH:79-125, 291-345` | per-tick per-pattern cache, invalidate on insert/extract |
| F8 | MED | C | Scheduler O(k²) per tick for k orders on one slot. | `Sched:42-85` | iterate distinct slots |
| F9 | MED | C | One request-tree node per input slot → 9 identical cobble slots = 9 nodes, ^depth; `addCrafting` re-parses all patterns per query. | `PatternCraftingTemplateBuilder:162-181` | aggregate identical non-OD ingredients with `NO_INPUT_SLOT`; cache templates |
| F10 | MED | C | Queries mutate state: `refreshRunningCraftState` from `sinksItem`/HUD, recomputed many times per tick with inventory scans. | `M:~2233, 848` | refresh once per tick in `tick()` |
| F11 | MED | C | `areAllOrdersBuffered` in every `sinksItem` → nested `canSink` per order destination. | `M:2306-2351` | per-tick cache |
| F12 | MED | C | Monitor watch packets rebuilt + global registry cleanup scan every 2 ticks per watched request, no diff, even non-pattern requests. | `PipeBlockRequestTable:117-121, 623` | send on change only |
| F13 | MED | C · _update packet part FIXED (deleted with the legacy GUI)_ | Pattern crafting table: full recipe scan on every insert/decr/extract; ~~full-NBT update packet to every chunk watcher per change~~. | table `:417, 652` | cache recipe; throttle, GUI viewers only |
| F14 | LOW-MED | C | `copyForAmount` rebuilds subtree + O(n²) merge per slice; finished subtrees still persisted; `liveOrders` never pruned. | `Br` | prune consumed branches |
| F15 | LOW-MED | C | Satellite lookups O(n) per ingredient; `ensureAllSatelliteStatus` name-conflict O(n) every 40 ticks per sat → O(n²). | sat `:72-94, 209, 538` | id/uuid index maps; name check only on rename/load |
| F16 | LOW | C | MUI S_STATE recomputed per tick per viewer (parses 9 patterns, `getPickBlock`); satellite list unbounded. | `PatternCraftingSyncHandler:214-230` | cache |
| F17 | LOW | C · _PARTLY LEGACY: the per-frame calls come from the legacy `RequestTableGui`; the resize in `writeToNBT` stays_ | Request table `updateStorageUpgrades` from getters called per frame on client and from `writeToNBT` (can resize/drop during save; client ghost drops). | `RequestTablePipe:126`, `RequestTableGui:147,153` | recompute on upgrade change, server only |

### 8.5 Leaks / lifecycle

| ID | Sev | Status | Issue | Where | Fix idea |
|---|---|---|---|---|---|
| L1 | MED | C | `outputOrders` pruned only from HUD path → unwatched pipe accumulates every finished order + branch tree. | `Coord:206-224` | prune in tick |
| L2 | MED | C | `PatternCraftingMonitorRegistry` static strong map: unregister only on cancel; cleanup only when a monitor GUI builds; never cleared on server stop → holds orders/pipes/World across SP world switches. | `PatternCraftingMonitorRegistry:22-79`, `LogisticsPipes.java:598-615` | unregister with outputOrders removal / releaseAll; clear on stop |
| L3 | MED | C | Satellites not deregistered on chunk unload; reload adds a second object with same UUID/id; lookups don't filter invalid → stale object wins randomly; `findId` only avoids *loaded* ids → duplicate ids; id fallback routes to wrong satellite when UUID target unloaded. | sat `:72-94, 500-520, 636`; `P:319-326` | index maps, remove on unload/invalidate, never fall back to id when uuid present, persisted id counter |
| L4 | LOW | C | `throttledDebugEvents` grows forever (see F2). | `M:100` | |
| L5 | LOW | C · _LEGACY: only matters while GuiProviders exist_ | `NewGuiHandler` catch of `RuntimeException|LinkageError` on dedicated server could silently skip a broken provider → GUI id shift between sides. | `NewGuiHandler` | log / rethrow for non-client providers |

### 8.6 GUI / HUD / UX

| ID | Sev | Status | Issue | Where | Fix idea |
|---|---|---|---|---|---|
| G1 | MED | C | HUD glasses stop updating while any MUI GUI is open: sync handler's `getHudState()` clears dirty first, `checkHudUpdate` returns early. `playerStartWatching` sets `oldHudState` without broadcasting to others. | `PatternCraftingSyncHandler:209`, `P:613-619, 750` | always compare cached state / revision counter |
| G2 | MED | P · _LEGACY + MUI-REQ: the legacy container goes away; the MUI table must size slots from server-synced upgrade counts_ | Request table container slot count differs client/server with storage upgrades (upgrade inv not synced) → client IOOBE. | `RequestTableContainer:53-60` | send sizes in open packet |
| G3 | MED | **REMOVED with legacy GUI** (`PatternSource.heldBy` reads the live stack) | Handheld `PatternInventory` captures stack once → GUI works on stale NBT (fluid slot treated as item → wrong satellite type). | `PatternInventory:21`, `PatternGui:320,338,356` | read live stack |
| G4 | LOW-MED | P | MUI client starts at slot 0, server at `findInitialSlot` → phantom slots may write wrong pattern's data client-side until next change. | `PipePatternCraftingMui:134` | pass initial slot in open data |
| G5 | LOW | C | HUD double-counts ingredient buffered once but used in several input slots. | `M:864, 941` | |
| G6 | LOW | C | Satellite renamed to chip's custom name on every click; unique-name suffix change not dirtied/synced. | sat `:583, 548` | |
| G7 | LOW | C | Request table grid prefers ore-dict substitutes over exact items. | `RequestTablePipe:788, 899` | exact pass first |
| G8 | LOW | **FIXED** — stub deleted; the handheld MUI uses `PatternCraftingContainer` like the pipe | Handheld pattern NEI handler is an empty stub but registered. | `nei/LogisticPatternHandler:23`, `NEILogisticsPipesConfig:63` | implement or unregister |
| G9 | LOW | C | `PatternFluidStack` calls NEI `StackInfo` from common code → NoClassDefFoundError without NEI; globally synchronized. | `patternStack/PatternFluidStack:10,55` | guard with `Loader.isModLoaded` |
| G10 | LOW | C | Request table ingredient multiplier unbounded (overflow / huge trees). | `RequestTablePipe:757` | cap |
| G11 | MED | C | Other MUIs use value sync handlers without `allowC2S()`, so client edits are dropped (same bug as the satellite field). | `PipeFluidSupplierMk2Mui:59,73,107`, `ModuleProviderMuiDynamic:135` | add `.allowC2S()` |
| G12 | MED | C | MUI shift-click merges can't work with `ItemIdentifierInventory` (every read returns a new stack), e.g. pipe upgrade slots via `UpgradeManager.getUpgradeInventory`. `SimpleInventorySlot` doesn't help there. | `PipeGuiFactory.getUpgradeGui`, `ItemIdentifierInventory` | slot/handler that writes the merged stack back |

### 8.7 Dead code — REMOVED (2026-09-27)

Removed: `AIH.insertPatternSets`; `Br.copyAndReserve`; `Br.liveOrderCount`/`liveOrdersFrom` (replaced by
`getLiveOrders()`); `Ord.BranchRequest` (now returns an int); `Ord.SatelliteIngredientDelivery` + `satelliteDeliveries`
NBT (legacy-only, **old saves lose those entries**); `PatternContainer.reloadFromPattern` + the unused
`NEISetPatternCraftingRecipe` packet; `PatternHandler.getIngredientItems`/`isIngredient`/`isFluidIngredient`.
`M.getSpecificInterests` is abstract, so it can't be deleted. It now returns `getCraftedItems()`, the same as the pipe.

**Legacy pattern pipe GUI removed:** `PatternCraftingPipeGui`, `PatternCraftingPipeGuiProvider` and the packets
`PatternPipeSelect`, `PatternPipeSlotAction`, `PatternCraftingPipe{Cancel,ReturnInputs,Mode}`,
`PatternPipeSatelliteAssignment`. `ModulePatternCrafting` now extends `LogisticsModule` (not `LogisticsGuiModule`).
The pipe's `onWrenchClicked` opens the MUI, so legacy wrenches also get MUI after the security check.
**Legacy crafting table and satellite GUIs removed (2026-09-28):** `PatternCraftingTableGui`, `PatternCraftingTableGuiProvider`,
the `PatternCraftingTableUpdate` packet (the table no longer broadcasts its NBT to the chunk), the pattern branch of
`GuiSatellitePipe` (restored to upstream) and the `PatternSatelliteSetName` packet (lang key `gui.satellite.Save` dropped).
The table's MUI slots flag the tile via `scheduleInventoryCheck()` because MUI shift-click merges don't call `markDirty`.
**Legacy handheld pattern GUI removed (2026-09-28):** `PatternGui`, `PatternGuiProvider`, `PatternContainer`,
`PatternInventory`, `PatternSlotLayout`, `PatternSatelliteSelectorGui`, the packets `PatternSlotActionPacket` and
`PatternSatelliteAssignmentPacket`, and the NEI stubs `LogisticPatternHandler`, `FluidPatternRecipeTransferHandler`,
`LogisticsPattern_NEIGuiHandler` (its commented draft was superseded by `PatternRecipeImporter`). `PipePatternInventory`
became `EditedPatternInventory` over a `PatternSource`. The pipe and the handheld GUI now share one editor.
Still legacy (no MUI yet): `RequestTableGui`.

### 8.8 Checked, no issue

- Dedicated server: no client-class crash path found in pipe/module/satellites/MUI (client classes only behind
  `isClient()` / suppliers / `@SideOnly`; HUD constructed server-side like upstream `HUDCrafting`). _Missed a
  client-only **method**: the pipe MUI's target icon called `Block.getPickBlock` on the server (B25, fixed
  and tested in game 2026-10-01). Calls into stripped `@SideOnly(CLIENT)` methods need a bytecode scan, not a class-import check._
- Threading: module state server-thread only; DelayQueue/debug deque thread-safe.
- Malformed pattern NBT: null items/unknown fluids return null and are null-checked; satellite arrays bounds-checked.
- Request table shift-click / result slot: no dupe found.

---

### 8.9 Legacy review summary

- **Not important (LEGACY):** G2, L5. Delete them with their GUIs. (C31, G3, G8 and the dead code are already done.)
- **Partly legacy, remainder still matters:** S1, S2, S3, S4, S7, D4, D11, C23, F17.
- **Requirements for the MUI migration:** S4 (sync handlers bound to the container), D11 (cap pickup at max stack), G2 (dynamic
  slot count from synced upgrades). G8 (NEI transfer for the handheld pattern) is done.
- **Not affected by the GUI change:** everything else. The core logic (module, branches, IO, persistence, satellites,
  request table server logic) stays.
- **Legacy GUIs left to remove or migrate:** `RequestTableGui`+`RequestTableContainer` plus `RequestTable*Packet`.
  Done: pattern pipe, crafting table, satellites, handheld pattern.

## 9. Suggested fix order

1. ~~**Exploits:** S1, S2, S3, S4, S6~~ done. S5 waits on the satellite routing design (see the §10 note on teleporting).
2. **Legacy removal:** ~~pattern pipe GUI~~, ~~crafting table~~, ~~satellites~~, ~~handheld pattern~~ done. Only the request table is left (MUI-REQ items apply).
3. **Dupes/loss:** ~~D2, D1, D3, D4~~ done (D4 except request table). Left: D5, D7–D12 (D6 mitigated).
4. **Stalls:** C1, C2, C5, C4, C10, C7.
5. **Perf:** F2 (debug gate) → F5/F6/F7 (per-tick + parsed-pattern caches) → F3/F4 (request table).
6. **Leaks:** L1, L2, L3.
7. Request-tree split correctness (C3) — biggest design change, needs its own plan.

## 10. User notes / decisions

### Known issues:

- Massive FPS lag when loading large pipe networks — _likely cause fixed (2026-09-28): `getOrCreateRouter` scanned every
  known router for each pipe that loaded, which is O(n²), on the client (`RouterManager`, render thread) and on the server
  (`JunctionRouterManager`, inside the lock that routing threads take). Both now use a position index. The client router
  itself is a no-op per tick. Needs an in-game check with a profiler (spark) to confirm nothing else is left._
- Items get stuck when trying to request crafts for the machines in blocking mode, even when machine is empty
- Items don't get dropped from the crafting pipe on break
- Items get teleported to satellite pipes instead of traveling to them when requested
  _B13 in [bug-list.md](bug-list.md): the ingredients travel to the crafting pipe, which then puts them into the
  satellite's machine. They should travel to the satellite pipe._
- Pattern crafting pipe voids excess fluids if there's no storage for them in the network, in this case it should hault, and either continue crafting in non-blocking mode, or error and wait untill there's space available, nothing should be voided
- ~~satellite name field doesn't update and errors when trying to give it a name (likely permission check fail)~~
- ~~shift-click transfering items that already present in pattern crafting table doesn't correctly display them in gui, this leads to several bugs, such as wrong crafts and missing items~~
- tps lag due to massive amounts of items traveling in the pipes - the driving engine must be rewritten to make pipes teleport from junction to junction instead of being simulated in world, as well as be able to clump up into chunks when in pipes, the traveling item visual will be client only and will be callebrated based on the travel delay
  _Causes: see [lag-investigation.md](lag-investigation.md) §1. Rewrite in progress: [item-transport-rewrite.md](item-transport-rewrite.md) (phase 1 tested in game 2026-09-30; open issues B22, B23 in [bug-list.md](bug-list.md))._
- fps lag due to massive amount of particles - disable them, use changing textures/hud glasses status instead
  _Causes: see [lag-investigation.md](lag-investigation.md) §2._
- large fps lag when near a lot of pipes, cause unknown
  _Causes: see [lag-investigation.md](lag-investigation.md) §3._

### Suggestions

- Items get buffered in the pipe before craft execution by default -> make this into a buffer upgrade
- Eventually implement buffer upgrade for supplier pipe as well
  _Decided in [rework-design-decisions.md](rework-design-decisions.md): **Buffer upgrade** (new), holds 1 requested set in
  the pipe and sends it without travel time, for supplier and crafting pipes._
- When copying recipe from nei, multiple stacks get compressed into one and then if the amount >127 doesn't add the item. make the nei import add stacks as is without compressing them, and then allow player to combine them manually (either by hand, or with "combine" button)
- request relay - AE2 integration. Add a special block that connect to both me and LP network, allow it to request items from LP network through AE net and supply items from AE network to LP.
- Crafting request improvement. It will be staged:
1) request item select - same as the current system
2) calculation screen - like in AE2 - calculate steps, required items, estimates crafting time. if all ingredients presents, dispatch routing path calculations
3) request monitor - actually monitor the completeness of the request, if it breaks, item gets lost, pipe disconnects or something else, monitor displays the error
4) deliver output - send the output to the requester and show it
- Add crafting monitor upgrade to supplier pipes
- Add crafting requests for suppliers as a toggleable option (possible upgrade?)
  _Decided: the **crafting monitor upgrade** goes on supplier pipes, request pipes and the request table, with a limit on
  concurrent monitored requests (supplier 1, request pipe mk1 2, mk2 4, request table 6). With the upgrade you can inspect
  the whole request tree; without it you only see that crafts were queued. The new **crafting upgrade** lets suppliers
  place crafting requests; today suppliers do that by default._

### Minors

- Minor gui change for upgrade menu, make gaps smaller, check styling

### Decisions

- **The rework design is in [rework-design-decisions.md](rework-design-decisions.md)** and takes precedence over this doc.
  What it means for pattern crafting:
  - The old crafting modules (`ModuleCrafter`, the legacy crafting pipe) are deprecated; the pattern crafting pipe and patterns
    replace them for new builds. Placed old crafters keep working in old bases (simple, non-blocking), so issues where an
    old crafter shares a request node with a staged promise (C4, part of C3) still matter, at a lower priority.
  - OreDict and NBT options in crafting pipes are unlocked by the new **OreDict filter** and **NBT filter** upgrades. Today
    they are per-pattern flags (`patternOreDictSubstitution`, `patternIgnoreNbt`) that anyone can set.
  - The advanced satellite upgrade is removed; its behaviour is on by default.
  - Speed upgrades no longer change travel speed; transport controller blocks do that. Speed upgrades speed up the
    pattern crafting table (its 4 upgrade slots already take speed upgrades) and extraction.
  - The buffer upgrade replaces the always-on ingredient buffering, and is also the prestock. Without it, pipes
    re-request items every time.
  - Items only teleport pipe to pipe. The known issue "items get teleported to satellite pipes" (B13) is about the
    crafting pipe inserting satellite ingredients straight into the satellite's machine (`insertPatternInput`) instead
    of the items travelling to the satellite pipe, not about the transport rewrite.
  - Satellite and fluid satellite **modules** (new) work like the satellite pipes, so a chassis can be a satellite.
  - Crafting pipes get a toggle to mark ingredients that aren't consumed or that lose durability (tools). That is the
    player-side answer to D7 (re-extracting catalysts and containers) and part of C8 (permanent contents keep
    BLOCKING locked).
  - The sneaky upgrade is removed. The insertion side is set per connection in the pipe GUI (sneaky faces), so
    `getInsertionOrientation` (D2) and the extraction side (C20) will read it from there.
  - Upgrades go in without a pipe controller through the side upgrade GUI, at most 4.
  - The **Legacy Wrench** is for debugging only and isn't craftable. It does not bring back the legacy GUIs deleted in
    §8.7; it only opens legacy GUIs that still exist. Deleting legacy GUIs once MUI covers them stays the rule. Exception: GUIs of deprecated modules
    (e.g. the old crafting modules) stay as legacy GUIs until those modules are deleted, one major pack version after
    the rework ships (see "Compatibility with old bases" in the decisions).
- **Legacy GUIs are deprecated** (prototyping only) and will be deleted. MUI migration is the priority; don't spend
  effort keeping legacy GUI paths compatible. Bugs that exist only in legacy paths → delete the path once MUI covers it.
- In new MUIs add an upgrade side gui with 4 slots for upgrades. this includes pattern crafting table
  (**done for the table:** 4 speed-upgrade slots in `PipeGuiFactory.getUpgradeGui(syncManager, slotFactory)`; old
  3-slot saves load into the first 3 slots)

<!-- Add design intent, known-by-design behaviours, and decisions here. -->
