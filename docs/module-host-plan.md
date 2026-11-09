# Module host: implementation plan

The decision is the "Module host and upgrades" section in [rework-design-decisions.md](rework-design-decisions.md), the
tasks are Phase 2a in [roadmap.md](roadmap.md). State on 2026-11-09: the module host is done and committed (steps 0–7
and 9; the chassis, the fluid pipes and the item single-module pipes run on it). Pattern crafting (step 7.5) is
postponed; step 8 moved to new features in 2b, the satellite modules were dropped (2026-11-07).

## Goal

One component that holds modules, their upgrades and targets (inventory, tank), used by the chassis (N slots) and by
single pipes (1 locked slot). The logic lives in the modules, the pipes do not repeat it.

```
                 ┌──────────────────────────────────────────────────┐
                 │                    ModuleHost                    │
                 │  modules by slot, ModuleUpgradeManager for each  │
                 │  routing arrivals by slot, NBT                   │
                 │  ┌──────────────────────┐ ┌───────────────────┐  │
                 │  │ ItemHostResource-    │ │ FluidHostResource-│  │
                 │  │ Manager              │ │ Manager           │  │
                 │  └──────────────────────┘ └───────────────────┘  │
                 └──────────────────────────────────────────────────┘
                        ▲ N slots                     ▲ 1 slot
          ┌─────────────┴────────────┐   ┌────────────┴────────────┐
          │   PipeLogisticsChassi    │   │  PipeFluidExtractor and │
          │   target: 1 by direction │   │  other single pipes     │
          │                          │   │  targets: all adjacent  │
          └──────────────────────────┘   └─────────────────────────┘
```

## Starting point

| Class | Lines | Role |
|---|---|---|
| `PipeLogisticsChassi` | 840 | Module slots, upgrades per slot, direction, routing arrivals, forwarding to managers |
| `ChassiModule` | 241 | `LogisticsModule` facade over the chassis modules (`sinksItem` picks the best module) |
| `ChassisItemManager` → `ItemHostResourceManager` | 256 | Inventory and item modules: interests, receiving, provide, crafting |
| `ChassisFluidManager` → `FluidHostResourceManager` | 506 | Tank and fluid modules: side, reservations, in transit, fluid sink/provide |
| `IChassisResourceManager` → `IHostResourceManager` | 83 | Common interface of the managers |
| `ModuleUpgradeManager` | 175 | Slot upgrades, adds the pipe upgrades through `parent` |

What the managers take from the chassis (all through the `pipe` field): `getChassiSize` (14), `isEnabled` (6),
`getModules` (5), `getPointedOrientation` (3), `getRealInventory`, `getContainer`, `getCacheHolder` (2 each),
`spawnParticle`, `refreshRender`, `queueRoutedItem`, `isNthTick`, `getUpgradeManager`, `getSneakyInventory`,
`getRouter` (1 each). This is the future interface of "what the host needs from the pipe".

Saves: `ChassiTargetInformation` (module slot number of an item in transit) **is not saved** —
`ItemRoutingInformation` writes only `PatternTargetInformation`. Renaming the class does not break saves.

## Names

| Was | Now |
|---|---|
| `pipes.chassis.ChassisItemManager` | `pipes.modulehost.ItemHostResourceManager` |
| `pipes.chassis.ChassisFluidManager` | `pipes.modulehost.FluidHostResourceManager` |
| `pipes.chassis.IChassisResourceManager` | `pipes.modulehost.IHostResourceManager` |
| `PipeLogisticsChassi.ChassiTargetInformation` | `pipes.modulehost.ModuleSlotTargetInformation` |
| — | `pipes.modulehost.ModuleHost` (new) |
| — | `pipes.modulehost.IModuleHostPipe` (new, what the host needs from the pipe) |
| `ChassiModule` | unchanged: only the chassis needs the facade, a single pipe returns its module directly |

"Resource" — as in the old `IChassisResourceManager`: the managers are split by the LP resource kind (`ItemResource` /
`FluidResource`). "Target" was not taken: in the code this is the name for information about where an item is going
(`IAdditionalTargetInformation`).

## Steps

Each step is built and checked in game on its own. Steps 0–3 do not change behaviour for the player.

### Step 0. Renaming 🟢 ✅ (2026-10-29, build and spotless pass, not checked in game)
- Rename and move the classes from the table above, update imports (`ChassiTargetInformation` is created by ~25
  modules, plus `PipeTransportLogistics`, `ChassiTransportLayer`).
- The managers still take `PipeLogisticsChassi` for now.
- Done: the classes are moved to `pipes.modulehost`, `ModuleSlotTargetInformation` is a separate file;
  `SupplierTargetInformation` (`ModuleActiveSupplier`) and `CraftingChassieInformation` (`ModuleCrafter`) extend it.
  The chassis reaches the slot through `getModuleSlot()` instead of the field.
- Check: build, a chassis with item and fluid modules works as before.

### Step 1. Untie the managers from the chassis 🟡 ✅ (2026-10-29, build and spotless pass, not checked in game)
- Add `IModuleHostPipe` with the methods from the list above (`getChassiSize` → host size, `getModules` → host).
- `PipeLogisticsChassi` implements it; the managers take `IModuleHostPipe` and `ModuleHost` instead of the chassis.
- Done: `pipes.modulehost.IModuleHostPipe` — `getModuleSlotCount`, `getHostedModule(slot)`, `isEnabled`,
  `getContainer`, `getPointedOrientation`, `getRealInventory`, `getSneakyInventory`, `getUpgradeManager(slot, pos)`,
  `getRouter`, `getCacheHolder`, `isNthTick`, `sendItem`, `queueRoutedItem`, `spawnParticle`, `refreshRender`.
  The managers take only `IModuleHostPipe` (there is no host yet, it appears in step 2). The chassis implements three
  new methods (`getModuleSlotCount` → `getChassiSize`, `getHostedModule` → `ChassiModule.getSubModule`, `sendItem` →
  `transport.sendItem`), it already had the rest. Instead of the `pipe.container` and `pipe.transport` fields the
  managers call methods. In step 2 `getModuleSlotCount` and `getHostedModule` move from the pipe to the host.
- Check: as in step 0.

### Step 2. Extract `ModuleHost` 🟠 ✅ (in code)
- Moved from the chassis to the host: the module inventory (`_moduleInventory`), `ModuleUpgradeManager[]`, the
  managers, `getManagerFor`, routing of `itemArrived` / `itemLost` / `addToBuffer` by `ModuleSlotTargetInformation`,
  NBT read/write, module install/removal (`InventoryChanged`).
- The host writes NBT **with the same keys** as the chassis does now (`"chassi"`, upgrades by slot number, manager
  keys), so old chassis load without migration. The direction (`"Orientation"`) stays with the pipe.
- The chassis becomes thin: direction, GUI, HUD, textures, forwarding to the host.
- Remove the silent `try/catch` + `printStackTrace` in the chassis `readFromNBT`, or at least log through the logger.
- Check: an old world with chassis of all tiers, item and fluid modules; modules, upgrades, direction are in place;
  receiving, provide, crafting, fluids work.
- Done (2026-10-29, build, spotless and tests pass, not checked in game): `pipes.modulehost.ModuleHost`.
  - In the host: the module array (before in `ChassiModule`), the module inventory, `ModuleUpgradeManager[]`, both
    managers (the host creates them itself, for now always both), module install from items
    (`onModuleInventoryChanged`), routing of arrivals, `getManagerFor` / `takeArrival`, interests, dropping modules
    when the pipe is broken (`dropModules`), NBT with the same keys (`"chassi"`, `"slot<i>"`,
    `"ModuleUpgradeInventory_<i>"`, manager keys).
  - The managers take modules from the host (`ModuleHost`), everything else from the pipe (`IModuleHostPipe`).
    `getModuleSlotCount` / `getHostedModule` left `IModuleHostPipe`, a callback
    `onHostModulesChanged(moduleRemoved)` was added: in it the chassis rebuilds the open old GUI and sends the slot
    contents to the HUD.
  - `ChassiModule` stays the chassis facade (`sinksItem`, CC, GUI provider); it takes the modules, module NBT and
    `tick` from the host. Its `getModule` / `hasModule` / `removeModule` stay for the old GUI and `DummyContainer`.
  - `ModuleUpgradeManager` takes any `CoreRoutedPipe`, not only a chassis.
  - The chassis keeps the direction, GUI, HUD, textures, wrench / module click, CC commands and forwarding to the
    host. The external API of the chassis (`getModuleInventory`, `getModules`, `getModuleUpgradeManager`,
    `InventoryChanged`, `getManagerFor`) did not change.
  - Differences from the old code: upgrades and managers are read from NBT before the direction, not after (neither
    depends on the direction); a chassis read error is written to the LP log with a message, not `printStackTrace`.

### Step 3. Targets as a list 🟡 ✅ (fluids; items — in step 7)
- The pipe gives the host a list of targets (`IModuleHostPipe.getTargets()` or similar): the chassis — one by
  direction, a single pipe — all adjacent.
- `ItemHostResourceManager` and `FluidHostResourceManager` work with the list. `LogisticsFluidModule` (`getTank`,
  `getTankSide`, `fillTank`, `drainTank`, `getTankContents`) moves to the list: the contents are the sum over the
  tanks, draining and filling go one by one.
- Open question: item modules reach the inventory through `_service.getPointedInventory` /
  `getSneakyInventory`, not through the manager. Is this what gives "all adjacent" for single pipes now? Check
  before step 5.
- Check: the chassis is unchanged (a list of one).
- Done for fluids (2026-10-29, build, spotless, tests and selftest pass, not checked in game):
  - `IModuleHostPipe.getTargetDirections()`: the pipe sides whose blocks the modules work with. The chassis returns
    its one side (or empty), single pipes will return all sides.
  - `IFluidModuleHost`: instead of `getFluidTargetTile()` / `getFluidTargetSide(slot)` —
    `getFluidTargets(slot)`, a list of `FluidTarget` (tank + side to fill and drain it from; the module sneaky upgrade
    still replaces the side).
  - `FluidHostResourceManager.getTargets(slot)` / `hasTarget()` build the list from the pipe sides.
    Providers of the same set of tanks still do not offer it twice (before — by tank side, now — by the target
    list).
  - `LogisticsFluidModule`: filling and draining go through the tanks one by one, space, contents and stock are
    summed, `hasTank()` instead of `getTankTile() != null`. `ModuleFluidExtractor` drains each tank separately, like
    `PipeFluidExtractor`; the pause when the network takes nothing is set after the pass over all tanks.
- **Items are not moved — postponed to step 7.** What was found:
  - `CoreRoutedPipe.pointedDirection` is set only by the chassis. For single pipes it is always `UNKNOWN`, so
    `getRealInventory` / `getPointedInventory` / `getSneakyInventory` return `null` there.
  - Each single pipe finds inventories itself: basic pipe — the first adjacent one (its own `getPointedInventory`),
    provider pipe — all adjacent (its own loops over `WorldUtil`). `ModuleActiveSupplier` walks over all neighbours
    itself — **in the chassis too**: a supplier in a chassis tops up all adjacent inventories, not only the one the
    chassis points at.
  - Item modules reach the inventory through `IPipeServiceProvider` (`getPointedInventory`,
    `getSneakyInventory`, `getRealInventory`), not through the manager. It is better to move them to host targets one
    pipe at a time in step 7, deciding for each whether to keep its current behaviour.

### Step 4. Upgrades on the module 🟡 ✅ (in code; GUI not checked in game)
- `CoreRoutedPipe.getUpgradeManager(slot, pos)` for a pipe with a host returns the slot manager from the host;
  `IN_PIPE, 0` on a single pipe = host slot 0.
- `ModuleUpgradeManager` stops adding the pipe upgrades (`parent`), except for the **legacy slot**: upgrades that were
  on the chassis itself are moved there on load and keep working on all modules until the player takes them out.
  Everything beyond 4 slots goes there too.
- Pipe-wide upgrades (`IPipeUpgradeManager`: hot, CC control): the pipe has the effect if the upgrade is in at least
  one module.
- GUI (decided 2026-10-29): upgrade slots are **in the window of each module**, in the chassis, in a single pipe, and
  **when the module is in hand** (`IN_HAND`). For a module in hand the upgrades are written to the item NBT together
  with the settings (`ItemModuleInformationManager.saveInfotmation` on window close) and start working when the
  module is put into a chassis.
- Dupe with a module in hand: the module slot is locked (`DummyModuleContainer` sets `UnmodifiableSlot`), but the
  settings are saved into the item that is in that slot **on close**. Check that the module cannot be taken out of the
  slot while the window is open: hotbar swap with a number key over another slot, drop (Q), death, the MUI variant of
  the window. Otherwise one can take the upgrades out in the window and carry away the module with the old NBT where
  they still are. It is safer to write the upgrades into the item right on each slot change, not only on close.
- "Upgrade module upgrade" — deprecated, stops doing anything.
- Check: an old chassis with upgrades on the pipe and in modules — everything works as before; nothing got duplicated.
- **Decided 2026-10-29, refines the points above:**
  - Pipe-level upgrades (speed, side disconnection, opaque, combined sneaky, power, CC, logic controller, crafting
    monitor, security card) **stay in the pipe slots** until there is a replacement for them (2a, hot upgrade).
    "The pipe has no slots" is the end goal, not step 4.
  - The legacy slot is the **existing pipe slots**: module-level upgrades in them (on a chassis this is only sneaky)
    keep working on all modules (`parent`), no separate slot is added.
  - Module upgrades **leave with the module** (in the item NBT) and come back on install.
  - The step is split into **4a** (logic) and **4b** (GUI).
- **Decided 2026-10-30, pipe-level upgrades on the chassis:** only speed (moves to the extractor / provider modules) and
  CC remote (stays on the pipe) remain. Opaque, connection, sneaky, combined sneaky, power supplier, power
  transportation, upgrade module upgrade — deprecated; on world load they are removed from pipes without returning
  them to the player (connection and sneaky — after moving into the side settings). Power supplier is turned off right
  away. The RF / IC2 power provider blocks — deprecated. The pipe upgrade panel in the chassis window is not needed: the
  slots of the pipe's own upgrades are in the Logistics Pipe Controller window (first tab, 9 slots), they can be taken
  out there.
  Speed that is already on a chassis pipe stays in its legacy slots and works on all modules until the player takes
  it out; it is not split between modules.
- **Done 2026-10-30:** the deprecated upgrades (opaque, upgrade module upgrade, power transportation, RF/IC2 power
  supplier) and the RF/IC2 power provider blocks are marked in `DeprecatedContent` (a warning in the tooltip), their
  recipes are removed from the code (CoFH, IC2, `RecipeManager`), they cannot be put into a pipe, on load they are
  removed from pipe and module slots (`updateModule` in both managers). Selftest `deprecated_upgrades`.
  Left to do: remove the machine power mechanism itself (`PowerSupplierHandler`, the power sub-system in
  `JunctionRouter` / `ServerRouter` / `PathFinder` / `CorridorScanner`, the power provider block logic, rendering and
  the link to receivers in `PipeTransportLogistics`). Without the upgrades it no longer works; do not touch the item
  and tile registration until the removal version. Speed into modules — done with the extractor rework (2c,
  2026-11-05/06): extractor, fluid extractor, provider, fluid provider and QuickSort take speed upgrades in their
  slots; pipe speed upgrades still set the item travel speed until the transport controllers exist.
- **4a done** (2026-10-29, build, spotless, tests and selftest pass, not checked in game):
  - Upgrades belong to the module: `LogisticsModule.getModuleUpgrades()` / `hasModuleUpgrades()`.
    `ModuleUpgradeManager` can be not bound to a pipe (module in hand): then only its own upgrades count;
    `bind(pipe, parent)` on install into a host adds the pipe upgrades, as before.
  - They are saved together with the module settings, prefix `ItemModuleInformationManager.MODULE_UPGRADES_PREFIX`
    (`"module"`): in the module item — inside `moduleInformation`, in the chassis — inside `slot<i>`
    (`ModuleUpgradeInventory_moduleitems`). So they leave with the module wherever its settings are saved
    (MUI and the old chassis GUI, breaking the chassis, the window of the module in hand), and do not get duplicated.
  - Host: `getUpgradeManager(slot)` returns the upgrades of the slot's module; for an empty slot — the upgrades left
    in it. The old format (`ModuleUpgradeInventory_<i>` in the root of the chassis NBT) is read as left in the slot and
    is given to the slot's module right away, the rest waits for the next module (`ModuleUpgradeManager.moveInto`).
  - "Upgrade module upgrade": cannot be installed (`isAllowedForPipe` → false), installed ones do nothing. The module
    upgrade slots in the old chassis GUI are always visible (2 of 4, as before; all 4 — in 4b).
  - Selftest: `module_upgrades_travel` (an upgrade leaves with the module to another chassis, the old slot is empty),
    `legacy_slot_upgrades` (old format: the slot upgrade goes to the module, for an empty slot it is kept and goes to
    the next module), in `chassis_nbt` — the upgrade is in `slot0`.
  - Single pipes did not change: they do not have a host yet, their module takes the pipe upgrades (steps 5, 7).
- **4b done** (2026-10-29, build, spotless, tests and selftest pass, GUI not checked in game):
  - `gui/modularUI/ModuleUpgradeSlots`: a column of 4 module upgrade slots. Filter — an upgrade allowed for this module
    (`isAllowedForModule`); after a change it recalculates the upgrade effects (the inventory wrapper does not tell
    the manager by itself).
  - Chassis window (MUI): on the right, in place of the former pipe upgrade panel — the upgrades of the open tab's
    module. One column per chassis slot, only the open tab's column is visible: the server does not know which tab is
    open, so all columns — real container slots — exist on both client and server in the same order (otherwise the
    slot numbers diverge and the window shows items in the wrong cells). The columns are not built in the rebuilt part
    of the module page, each takes the module of its slot on each access. Shift-click into these slots is disabled
    (the server does not know which module the player is looking at).
  - **The slots of the pipe's own upgrades are no longer in the MUI chassis window** (decided 2026-10-29). Upgrades
    there keep working. How the player takes them out was answered on 2026-10-30 (see "pipe-level upgrades on the
    chassis" above): in the Logistics Pipe Controller window.
  - Module in hand (`ItemModule.buildUI`): a column to the right of the module window. Dupe protection: the module slot
    in the player inventory is locked (as in `HandheldPatternMui`, MUI checks it on number key swap too); the settings
    are written into the same item object, not into "what is in hand on close"; the item is updated right on each
    upgrade change.
  - `ItemModuleInformationManager.saveInfotmation`: if there is nothing to save, the old data in the item is erased,
    not kept (otherwise a module without settings whose last upgrade was taken out kept it in the item).
    Selftest: `module_item_upgrade_removed`.
  - At the time upgrades were accepted only by: item sink modules (fuzzy, sneaky: ItemSink, Polymorphic, ModBased,
    OreDict), active supplier (pattern), the old crafter. Since then: speed (extractor, fluid extractor, provider,
    fluid provider, QuickSort, 2026-11-05/06), the one Sneaky Upgrade (every module that works on a side) and the
    Priority Upgrade (item sinks, provider, fluid sinks, fluid provider), both 2026-11-09.

### Step 5. First single pipe: `PipeFluidExtractor` 🟡 ✅ (in code, not checked in game)
- The pipe embeds a 1-slot host with `ModuleFluidExtractor`, implements `IFluidModuleHost` by forwarding to the host,
  its own drain logic is removed.
- NBT migration: an old pipe without host data → create the module; `liquidToExtract` is not migrated (it is a
  counter).
- Write to [behaviour-changes.md](behaviour-changes.md): the extractor pauses if the network takes nothing, checks
  energy before draining.
- Check: an old world with a fluid extractor at several tanks.
- Done (2026-10-29, build, spotless, tests and selftest pass, not checked in game):
  - Two kinds of host: `ModuleHost.forChassis(pipe, size)` and `ModuleHost.forSingleModule(pipe, moduleItem)`. In a
    single pipe the module is fixed (set in the constructor, does not drop when broken), the pipe itself saves the
    module settings in the NBT root, as single pipes always did; the host writes the rest (module upgrades, managers)
    under the `moduleHost` key, so as not to clash with the pipe keys (`pendingFluids` exists both in
    `FluidRoutedPipe` and in the fluid manager). Old pipes without `moduleHost` load with the module.
  - Found: the old `PipeFluidExtractor` did not send fluid itself — it drained into the buffers of its sides, and the
    inherited `PipeFluidInsertion` logic did the sending; it also sends fluid pumped into the pipe by other pipes. Now
    the module drains (`getTargetDirections` — all 6 sides), and receiving fluid pumped in from outside stays with
    `PipeFluidInsertion`. The `liquidToExtract` counter is no longer needed; fluid left in the buffers goes out as
    before.
  - `CoreRoutedPipe`: a wrench click opens the module's old GUI only if the module has one
    (`LogisticsGuiModule.hasLegacyPipeGui()`), otherwise it crashes on fluid modules (they only have MUI).
  - The fluid extractor accepted no module-level upgrades then, there was nothing to move from the pipe; the pipe
    upgrades stayed on the pipe. It takes speed and sneaky upgrades since (2026-11-05, 2026-11-09).
  - Selftest: `fluid_extractor_pipe_transfer` (two tanks on different sides → sink), `fluid_extractor_pipe_nbt`
    (old and new save).


### Step 6. Other fluid pipes 🟡 ✅ (Mk1 deprecated; in code, not checked in game)
One by one, in this order, each with NBT migration and the differences written to `behaviour-changes.md`:
1. `PipeFluidBasic` → `ModuleFluidSink` (filter, default route).
2. `PipeFluidSupplierMk2` → `ModuleFluidTankSupplier`.
3. `PipeFluidProvider` → `ModuleFluidProvider` — the most differences, compare line by line.
4. `PipeItemsFluidSupplier` (Mk1): first find out whether it matches the old `ModuleFluidSupplier`.

`PipeFluidInsertion`, `PipeFluidRequestLogistics` — without a module. Fluid receiving
(`FluidRoutedPipe.endReached`, `PendingFluids`) and receiving in `FluidHostResourceManager` — merge into one place once
all pipes are moved.

Done (2026-10-29, build, spotless, tests and selftest pass, not checked in game):
- The "pipe + host" link is moved into `FluidRoutedPipe`: a subclass calls `hostModule(moduleId)` in the constructor,
  and the pipe becomes the host of one module (`IModuleHostPipe`, `IFluidModuleHost`, the module, its upgrades, NBT
  under `moduleHost`, interests, manager tick, receiving arrivals through `ModuleHost.takeArrival`). Pipes without an
  embedded module work as before. `PipeFluidExtractor` is moved to the same link.
- `PipeFluidBasic` → `ModuleFluidSink`: the filter and default route are on the module (the same NBT keys in the pipe
  root; the oldest format — one fluid right in the pipe tag — is read by the pipe). The sink (`sinkAmount`, priority)
  and fluid receiving go through `FluidHostResourceManager`, like on the chassis. The pipe GUI is the same, it works
  through the module's `IFluidSinkSettings`.
- `PipeFluidSupplierMk2` → `ModuleFluidTankSupplier`. The module is moved to a stock **in each tank** (as the pipe
  always did; the chassis has one tank, nothing changes for it), the refill threshold is also per tank. The module
  makes the requests (the pipe is no longer `IRequestFluid`), deliveries are addressed to its slot. The old formats
  (`items`, `_bucketMinimum`) are read by the pipe. The pipe GUI is the same, it edits the module fields. The pipe is
  still an "active destination" (`hasGenericInterests` = true).
- `PipeFluidProvider` → `ModuleFluidProvider`: promises, orders and draining are done by the module, taking the host
  reservations into account; ~300 lines of the pipe's own logic are removed. The ME interface and other special tanks
  work through `FluidTankUtil`.
- **`PipeItemsFluidSupplier` (Mk1) is not moved:** it is an item pipe — it orders filled containers and empties them
  into tanks, — there is no ready module with such logic. `ModuleFluidSupplier` does not match it and is not even
  registered (the number 703 is given to `ModuleFluidTankSupplier`) — dead code with an old GUI. **Decided 2026-10-29:
  Mk1 is deprecated in favour of Mk2** — both recipes are removed from `RecipeManager`, the item tooltip always shows a
  removal warning (`utils/DeprecatedContent` — the common list of deprecated content, Mk1 is first in it), placed
  pipes work as before. Selftest `deprecated_fluid_supplier_mk1`.
- Fluid receiving is not fully merged into one place yet: for the moved pipes it goes through the host, for the other
  `FluidRoutedPipe` pipes (pattern crafting, satellite, request, insertion) — the old way.
- Selftest: `fluid_basic_pipe_transfer` (by filter into two tanks), `fluid_basic_pipe_nbt` (three formats),
  `fluid_supplier_pipe` (3000 mB in each of two tanks), `fluid_supplier_pipe_nbt` (settings and old formats),
  `fluid_provider_pipe` (5000 mB from two tanks). 13 scenarios in total, all pass.

### Step 7. Item single pipes 🟡 (1–4 ✅ in code; pattern crafting postponed)
1. `PipeItemsBasicLogistics` → `ModuleItemSink` (move `stillWantItem` and `getPointedInventory` into the module
   or the host).
2. `PipeItemsApiaristSink`.
3. `PipeItemsSupplierLogistics` → `ModuleActiveSupplier` (thin, almost ready).
4. `PipeItemsProviderLogistics` → `ModuleProvider`: remove its own implementation of `canProvide` / `fullFill` /
   `getAllItems`, keep the work with all adjacent inventories.
5. `PipeItemsPatternCraftingLogistics` → `ModulePatternCrafting` (parent `FluidRoutedPipe` — a check that the
   component can be embedded into any pipe).

The deprecated `PipeItemsCraftingLogistics*` (`ModuleCrafter`) are not touched.
- Done (2026-10-29, build, spotless, tests and selftest pass, not checked in game):
  - The "pipe + single-module host" link is lifted from `FluidRoutedPipe` into `CoreRoutedPipe`: `hostModule(id)`,
    `hostedModule()`, `hasModuleHost()`, `IModuleHostPipe` (all sides), module upgrades through
    `getUpgradeManager(IN_PIPE/SLOT, 0)`, manager tick, host NBT (`moduleHost`), pipe breaking. The chassis has its own
    host (`ModuleHost.forChassis`), pipes without a module work as before.
  - The single pipe host puts the module in the `IN_PIPE` position (not `SLOT`): from it `ModuleItemSink` knows it is
    not in a chassis and checks the space itself, counting items in transit. The fluid pipes also moved to `IN_PIPE`.
  - `PipeItemsBasicLogistics`, `PipeItemsApiaristSink`, `PipeItemsSupplierLogistics` — hosts of their modules
    (`ITEMSINK`, `BEESINK`, `ACTIVE_SUPPLIER`). The logic was already in the modules; inventories are chosen as before
    (basic — the first adjacent one, supplier — all adjacent, the module does this itself). Upgrades on the pipe (fuzzy
    on basic, pattern on supplier, sneaky) keep working on the module through `parent`. The module settings are in the
    pipe NBT root, as before.
  - The window of a single pipe with a module (`GenericPipeLogisticsGui`, on supplier) shows 4 module upgrade slots on
    the right, like a module in hand. The basic pipe (and the apiarist sink, fluid basic and fluid supplier Mk2 pipes)
    opens its module's window with the upgrade slots and the settings window (2026-11-09).
  - Selftest: `item_basic_pipe_transfer`, `item_basic_pipe_nbt` (filter, old save, fuzzy on the pipe works on the
    module, module in `IN_PIPE`), `item_supplier_pipe` (re-order of 32 from a provider in a chassis). The apiarist sink
    cannot be checked without Forestry in the dev server.
- Provider (2026-10-29, build, spotless, tests and selftest pass, not checked in game):
  - The module and the pipe did the same thing, but the module with one inventory, the pipe with all adjacent ones.
    Now the modules have a list of inventories: `IPipeServiceProvider.getItemTargets(mode, forExtraction)` →
    `ItemTarget` (an inventory seen from the right side and through the extraction mode, + the direction from the
    pipe). The chassis gives one (where it points), a single pipe — all adjacent ones (not pipes). `ModuleProvider`
    counts the stock, gives out items and builds the list over all inventories; the logic is taken from the pipe
    (taking request filters into account, including for ore dict; sending from the side of the inventory the item was
    taken from — `sendStack(..., from)`).
  - `PipeItemsProviderLogistics` (and Mk2 — module `PROVIDER_MK2`) — host of the module; its own logic (~400 lines) is
    removed. The pipe kept the HUD, its own order manager (the module works with it through `getItemOrderManager()`,
    the HUD sees the orders) and the "everything lying nearby" interests (in a chassis the module declares interests by
    filter, as before). The external API of the pipe (GUI, packets, HUD) did not change. The send rate is the same:
    once per 6 ticks for the pipe, every tick in a chassis.
  - Selftest: `item_provider_pipe` (32 from two chests), `item_provider_pipe_nbt` (filter, exclusion, extraction
    mode; old save).
  - Along the way in selftest: the scenario areas are laid out in a grid within 128 blocks of spawn — further away a
    server without players unloads chunks in the middle of a scenario (this is why the last scenarios of the list
    "failed").
- **Pattern crafting postponed:** `ModulePatternCrafting` is already a module, the host would mostly give it module
  upgrades, and the pipe is big and has its own mechanics — the risk is higher than the benefit. Come back after steps
  8–9.


### Step 8. Pipes without a module 🟡
- Request pipe Mk1/Mk2, remote orderer, request table, fluid request: an internal stub module only for upgrades
  (crafting monitor).
- ~~`PipeItemsSatelliteLogistics`, `PipeFluidSatellite`: a real satellite module~~ — dropped (decided 2026-11-07):
  satellites stay pipes.
- Firewall, system entrance/destination, inv. system connector — unchanged.

### Step 9. `instanceof PipeLogisticsChassi` checks 🟡
22 places in 11 files. For each, decide: is it about the chassis (GUI, orientation packets, HUD) or about "the pipe has
a host" (then `instanceof IModuleHostPipe`). Can be done in parallel with steps 5–8, as pipes are moved.

### Outcome of steps 8–9 (2026-10-29)
- **Step 9 is closed without code changes.** The `instanceof PipeLogisticsChassi` checks outside the GUI: the "Chassis"
  label in `LogisticsManager`, chassis quick sort modules at a chest (`LogisticsEventListener`), chassis orientation
  packets, module lookup by chassis slot in GUI packets (`ModuleCoordinatesGuiProvider`/`Packet` — single pipe modules
  sit in `IN_PIPE` and are found through `getLogisticsModule()`), debug output in `PipeTransportLogistics`. All of this
  is about the chassis and should stay so; the chassis GUI and packets too. The upgrade path on item insertion
  (`PipeTransportLogistics`: `getUpgradeManager(SLOT, slot)` by `ModuleSlotTargetInformation`) returns the upgrades of
  their module for single pipes — `CoreRoutedPipe.getUpgradeManager` takes both `SLOT` and `IN_PIPE` for slot 0.
- **Step 8 is not a migration but new features, moved to 2b/2c:**
  - the stub module on request pipe / request table is needed only as a place for the crafting monitor upgrade, and it
    is currently a pipe upgrade and can only be put into the request table; making it a module upgrade is task 2b
    ("Crafting monitor upgrade");
  - `ModuleSatelite` is not a module (no item and no number), but a helper of the satellite pipe for answering "how much
    will I accept"; all satellite logic is in the pipe. Satellite modules were dropped (decided 2026-11-07):
    satellites stay pipes.
- **The module host is ready**: the chassis, fluid pipes (except Mk1 — deprecated), basic, apiarist sink, supplier,
  provider run on it. Pattern crafting is postponed (see step 7).

## Closed questions
- The names from the table are in use (step 0).
- A module can't be taken out of a single pipe: the slot is locked, and when the pipe is dismantled the module does
  not drop (decided, step 5).
