# Crafting v3: implementation notes

Implements [lp-crafting-rework-plan.md](lp-crafting-rework-plan.md). Written 2026-10-23. Code lives in
`src/main/java/logisticspipes/crafting/v3/`; tests in `src/test/java/logisticspipes/crafting/v3/`.

Status: compiles, 37 unit/simulation tests pass (planner 18, driver simulation 19, plus the existing suites). **Not
tested in game yet** — see the checklist at the end.

## 1. Package layout

| Package | Minecraft-free | Contents |
|---|---|---|
| `v3.model` | yes | `ResourceKey`, `ResourceMatcher`/`MatchRule`, `InputSpec`, `Pattern`, `CrafterInfo`, `BlockingMode` |
| `v3.plan` | yes | `CraftingPlanner` and its inputs (`PatternRegistry`, `StockView`, `RouteOracle`) and outputs (`Plan`, `PlanNode`, `InputResolution`, `SourceClaim`, `Allocation`, `PlanIssue`) |
| `v3.exec` | yes (uses NBT classes for persistence) | `CraftingDriver`, `Ledger`/`LedgerRecord`/`ReservationIndex`, `Ticket`/`Need`, `Shipment`, `CraftingJob`, `CraftDelivery`, `DriverPersistence`, interfaces `DriverWorld`, `CrafterPort`, `KeyCodec` |
| `v3.pipe` | yes | `CrafterRuntime`: the crafting pipe's ticket table, buffer, insertion queue, output capture |
| `v3.mc` | no | `CraftingService` (driver lifetime, save data, transport hooks, world adapter), `NetworkScan`, `McRouteOracle`, `ItemKey`/`FluidKey`/`McKeyCodec`, `CraftingRequests` (request API bridge), `JobOrderView`, `PlanPreview`, `ICraftingStockSource`, `IV3Crafter` |

Everything of the old crafting system (deprecated, deleted one major pack version after the rework) lives in
`crafting/legacy/`:

| Package | Contents |
|---|---|
| `legacy` | `LegacyCrafters`: legacy crafters as v3 crafters |
| `legacy.module` | `ModuleCrafter`, `ModuleCrafterMK2`, `ModuleCrafterMK3` |
| `legacy.pipe` | `PipeItemsCraftingLogistics` (+Mk2, Mk3), `CraftingPipeSign` |
| `legacy.satellite` | `PipeItemsSatelliteLogistics`, `PipeFluidSatellite`, `ISatellitePipe` (base classes of the pattern satellites) |
| `legacy.template` | request-tree crafting templates: `ICraftingTemplate`, `IReqCraftingTemplate`, `BaseCraftingTemplate`, `ItemCraftingTemplate`, `DictCraftingTemplate`, `FluidCraftingTemplate` |
| `legacy.gui` | `GuiCraftingPipe`, `GuiSatellitePipe`, `HUDCrafting`, `HUDSatellite`, `ModuleCraftingMuiDynamic`, GUI providers `CraftingModuleInHand`/`CraftingModuleSlot`, NEI `CraftingPipeOverlayHandler` |
| `legacy.network` | the crafter's packets (`CPipe*`, `SatPipe*`, `Crafting*`, `FluidCrafting*`, `NEISetAdvancedCraftingRecipe`, `RequestRunningCraftingTasks`) |

Moving them is save-safe: pipe items are registered by simple class name, modules and pipe signs by id, packets and
GUI providers get their ids from a list sorted by simple name (`PacketHandler` also scans
`crafting.legacy.network`; `NewGuiHandler` already scans `crafting`). Still outside: packets shared with the request
table (`NEISetCraftingRecipe`, `FindMostLikelyRecipeComponents`), the crafting upgrades, the BuildCraft gate trigger,
and the crafting branch of `RequestTreeNode` (the request tree no longer crafts, it is only reached without a world).

The planner, driver and pipe runtime are tested without Minecraft: `Fakes` (keys, registry, stock, routes) and `Sim`
(a tick simulator with fake providers, fake machines, a transit queue with delays and loss injection, legacy crafters
and save/load).

## 2. How the plan maps to code

- **§2 data model** — `model/*`, `exec/Ticket`, `exec/LedgerRecord`. Items and fluids share `ResourceKey`.
- **§3 planner** — `plan/CraftingPlanner`. Phase A picks one pattern per matcher with path-based cycle checks and a
  closure check when reusing an already chosen pattern. Phase B walks the matcher/pattern DAG root first: a matcher
  resolves (surplus → providers most-constrained-first → craft) once all its consumers are known; a node fixes crafts
  and crafter once all its demand is known. Heights include surplus edges; a cycle there throws (planner bug).
- **§4 commit** — `CraftingDriver.startJob`. Provider claims re-checked against live stock; shortfalls become UNSOURCED
  and are re-sourced next tick.
- **§5 execution** — `CraftingDriver.tick`: open within the height window (and a per-crafter cap), source UNSOURCED
  records, route inputs, commit COMPLETE tickets per crafter (BLOCKING: one outstanding; SMART: same pattern stacks;
  NONE: free), timeouts and commit re-sends.
- **§6 crafter** — `pipe/CrafterRuntime` + `crafting/ModulePatternCrafting` (machine adapter over the existing
  `AdjacentInventoryHandler`, satellites, the pattern crafting table).
- **§7 failures** — loss, unreachable, missing, provider theft, removed crafter, wrong craft, retry with backoff
  (2 s doubling to 30 s) plus a retry on every routing-graph change.
- **§8 completion/cancellation** — wind-down in `beginWindDown`/`windDown`; leftovers flushed to storage; leftovers
  nobody can store stay in the crafting pipe and are offered as stock (`ModulePatternCrafting` is an
  `ICraftingStockSource`).
- **§9 persistence** — driver in overworld `WorldSavedData` (`LogisticsPipes_Crafting`), pipes in tile NBT,
  `onCrafterLoaded` reconciles (pipe authoritative for contents, driver for intent). Routed items persist their
  `CraftDelivery` in `ItemRoutingInformation`.
- **§10 UI** — preview popup in the mk3 request table (`RequestTablePreviewPopup`); job view in the crafting monitor
  block and request table monitor (set state label + icon + color, stall detail, reserved amounts, cancel).

## 3. Design decisions and deviations from the plan

1. **One driver per server, not per network.** The router has no stable public network id (the component identity is
   package-private and split/merge has no hook). Jobs are scoped by per-item reachability instead, which gives §9.5
   for free: across a split the unreachable side stalls (orange) and resumes on merge.
2. **Self-healing ledger.** For every unfinished ticket and need, bound records add up to the need. `rebalance(ticket)`
   restores that after any event (excess → unbound, shortfall → UNSOURCED), and `source()` finds a new source: the
   job's own unbound items first (leftovers, expected surplus, items already travelling there), then provider stock.
   Swaps on arrival, strays, losses and retries all go through this one path.
3. **EXPECTED records belong to the producing node, not a ticket.** Outputs of any ticket of a node feed its consumers
   in priority order (open consumers first, then lowest height).
4. **Crafter-side ownership.** The pipe holds physical items tagged by owner (a ticket's need, or the job pool). The
   driver moves items between owners (`assign`) or out (`ship`); the pipe decides arrival matching, insertion timing
   and output attribution. Items are never assigned across jobs.
5. **Messages are direct calls** on the server thread, made idempotent by state checks (no sequence numbers).
6. **`CraftDelivery` is not the target info.** The routed item carries it in `ItemRoutingInformation.craftDelivery`;
   the visible target info is the requester's own (chassis slot, mk3 player delivery). Any send path that passes a
   `CraftDelivery` as target info is tracked automatically (`LPTravelingItemServer.setAdditionalTargetInformation`).
   Losses (drop, reroute, split) report to the driver and never reach the destination's own re-request logic.
7. **Planning runs on the server thread** with a 250 ms budget and a node cap of 4096: router reachability queries
   (`IRouter.hasRoute`) are not known to be thread-safe.
8. **Crafter backtracking** is a look-ahead (prefer a crafter whose stock-only inputs are reachable) rather than a retry.
9. **Batch limit 1 per ticket.** SMART mode stacks same-pattern tickets, which gives the same throughput without the
   risk of a batch that never fits the machine.
10. **Wrong craft** = the expected output never comes and the machine is idle (200 ticks). Unexpected extra outputs
    (GT chanced byproducts) are leftovers, not a wrong craft. When nothing runs and a blocking machine holds things no
    pattern of the pipe uses, they are pulled out to storage so the machine can't lock (C8/B34).
11. **Not-consumed inputs** (molds, circuits, lenses, tools): new per-slot flag in the pattern NBT
    (`patternNonConsumed`), toggled with Alt+click on an input slot in the pattern editor (orange border). Requested once
    per node, ignored by the blocking-mode idle check, never extracted.
12. **Start anyway** is allowed for unreachable items only; missing items block the start (resolved question §13).
13. **Legacy crafters** (`ModuleCrafter` in a crafting pipe or chassis) are read through their own `addCrafting`
    templates (fuzzy flags and satellites keep working; satellite inputs are delivered to the satellite). They rank
    below every pattern crafting pipe, run without buffer or blocking mode, and every node on them shows a warning in
    the preview, the monitors and chat. Their items and modules get a deprecation tooltip.
14. **Pattern satellites** keep the old behaviour: inputs travel to the crafting pipe, which inserts them into the
    satellite's machine at insertion time (B13 unchanged).

## 4. What changed outside `v3`

- `crafting/ModulePatternCrafting` rewritten on top of `CrafterRuntime`; the staged execution layer is deleted
  (coordinator, scheduler, branch, order, persistence, buffers, capacity, blocking/arrival/cancel/lost handlers,
  result extractor, template builder/templates/promises, `IStagedCraftingProvider`, `IStagedProviderReservation`).
  Old saves: buffered ingredients become leftovers and go back to storage; in-flight staged orders are dropped.
- `request/RequestTree`: requests with the Craft flag that stock alone can't cover go to v3
  (`CraftingRequests`); stock-only requests keep the old path. `RequestTreeNode` lost its staged path.
- `request/RequestHandler`: requests from GUIs remember the player for job messages.
- Providers (`ModuleProvider`, `PipeItemsProviderLogistics`, `PipeFluidProvider`, `ModuleFluidProvider`) are
  `ICraftingStockSource`s and subtract crafting reservations from everything they offer.
- Transport hooks: `ItemRoutingInformation.craftDelivery` (+NBT), `LPTravelingItem` (intercept, loss, split),
  `CoreRoutedPipe.notifyOfItemArival` / `itemCouldNotBeSend`, `PipeItemsPatternCraftingLogistics.notifyOfItemArival`.
- `PatternCraftingMonitorRegistry` is a facade over v3 jobs; `PatternCraftingMonitorNode` carries a status label,
  color and detail line (wire format changed).

## 5. Known limitations / follow-ups

- Only the mk3 request table shows a preview. Request pipes, the remote orderer, the old tables and the API start
  crafts directly (missing items are reported as before; the "simulate"/component list shows used and missing from a
  v3 plan).
- Planning scans the requester's network (providers' inventories) on every crafting request; suppliers that request
  crafts often pay that each time.
- The whole driver state is saved on every autosave while jobs run; very large jobs (10k+ sets) make a large NBT.
- Insertion into pattern satellites is not part of the atomic simulate for the pipe's own target in every case
  (satellite room is checked separately).
- The request table's watch packet (`PatternCraftingWatchPacket`) no longer carries crafting trees; the monitor panel
  and the crafting monitor block show v3 jobs instead.

## 6. In-game test checklist (plan §11.3)

- [ ] Electric piston on one shared GT assembler (motor and piston patterns on the same pipe), BLOCKING and SMART.
- [ ] Programmed circuit / mold marked "not consumed": requested once, machine not seen as busy.
- [ ] Multiblock with several pattern pipes feeding one input bus.
- [ ] One-way pipes and filters between provider and crafter: preview shows unreachable, job stalls orange, resumes
      when fixed.
- [ ] Chunk boundary crossing the network: unload the crafter's chunk mid-job, reload, job resumes.
- [ ] Save and quit mid-job (items in transit, sets in machines), reload: job finishes, nothing duplicated or voided.
- [ ] Break a crafting pipe mid-job: its items drop, the job re-plans onto another pipe with the pattern or stalls.
- [ ] Cancel at different stages from the crafting monitor: everything ends in storage, no reservations left
      (provider shows the items as available again).
- [ ] Legacy crafting pipe and chassis crafting module in a tree, with and without satellites: warnings shown, crafts
      complete.
- [ ] Fluid crafting: fluid provider pipe and fluid provider module as sources, fluid output delivered to a fluid
      request pipe.
- [ ] Old world from before the rework with a pattern pipe holding buffered ingredients: they go back to storage.
