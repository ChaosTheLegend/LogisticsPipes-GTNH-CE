# Roadmap

The work plan for the decisions in [rework-design-decisions.md](rework-design-decisions.md), merged from the old `TODO/rework.md` (the project board) and `TODO/roadmap.md`
(2026-10-01). The decisions are the source of truth: where an item here disagrees with them, they win. Bugs are
in [bug-list.md](bug-list.md), not here. Things to test are in [testing-checklist.md](testing-checklist.md).

**"Remove" means deprecate first.** Old bases must keep working after an update (see "Compatibility with old bases" in
the decisions): removed blocks, items, modules and upgrades are no longer craftable for new builds, but placed ones
keep working, show a removal warning, and are migrated to the new behaviour where there is one. They and their legacy
GUIs are deleted one major pack version after the rework ships (rework in 2.10 → deleted in 2.11).

**Difficulty** (effort, not priority): 🟢 easy (localized change / content) · 🟡 medium (new GUI, block or module,
contained logic) · 🟠 hard (touches routing, request or crafting core) · 🔴 very hard (new subsystem or cross-cutting
rewrite)

```
Phase 1: Crafting system ──┬──► Phase 2: Upgrades & modules ──┐
                           │                                  ├──► Phase 4: QoL, balance, polish
                           └──► Phase 3: Block UI & requests ─┘
Transport & routing: parallel track, pick up any time
GUI to ModularUI: cross-cutting, done per feature as it is touched
Bugs: bug-list.md, pick up any time
```

Phases 2 and 3 both depend on Phase 1 (request handler API) but not on each other, so they can overlap.

## Phase 1: Crafting system

**Goal:** a request handler that can carry everything else: large amounts, cancellation, reservations and fluids.

**1a. Request core** (start here, everything else builds on it)
- [ ] 🔴 Better request handler
- [ ] 🟡 Increase the 127 limit for crafting items
- [ ] 🟡 Crafting request cancellation (backend: unwind reservations, promises and in-flight items; the UI button is in 3c)
- [ ] 🟡 Deprecate the old crafting modules (`ModuleCrafter`, legacy crafting pipe): not craftable, removal warning.
  Placed ones keep crafting in a simple, non-blocking way, so the request handler keeps a simple path for them next
  to staged crafting
- [ ] 🟡 Crafting pipe toggle for ingredients that aren't consumed or lose durability (tools). Ties in with D7 and C8 in
  [pattern-crafting.md](pattern-crafting.md) §8

**1b. Suppliers**
- [ ] 🟡 Item Supplier
- [ ] 🟡 Fluid Supplier
- [ ] 🟠 Supplier reservation for requested crafts, so nothing can steal the ingredients

**1c. Fluid crafting**
- [ ] 🟠 Fluid crafting for pattern crafting pipes without needing satellites
- [ ] 🟠 Fluid byproducts
- [ ] 🔴 Fluid crafting, the general case (the module-based part waits for the fluid modules in 2c)

**Done when:** large multi-step item and fluid requests resolve, can be cancelled cleanly, and reserved ingredients stay
reserved.

## Phase 2: Upgrades & modules

**Goal:** a small, clear set of upgrades, chassis that are upgradable by default, and tools in place of the connection
upgrades.

**2a. Cleanup**
- [ ] Deprecate unused/useless upgrades (removal warning; installed ones are migrated or keep working):
  - [ ] 🟡 Sneaky upgrade, replaced by the sneaky-faces setting in the pipe GUI below; the faces are stored in the
    pipe per connection. Migrate installed ones to that setting
  - [ ] 🟡 Extractor sneaky directions: drop the direction setting from the extractor modules
    (`LogisticsSneakyDirectionModule`) and migrate modules that have one to the plain extractor, moving the direction
    into the sneaky-faces setting (per module on a chassis). See "Compatibility with old bases" in
    rework-design-decisions.md
  - [ ] 🟡 Combined sneaky upgrade: becomes the set of faces it held, tried in the fixed order (custom order is lost,
    listed in [behaviour-changes.md](behaviour-changes.md))
  - [ ] 🟡 Side-block (disconnection) upgrades, replaced by the shift+wrench overlay below. Migrate installed ones to
    the per-side setting
  - [ ] 🟢 Advanced satellite upgrade; its behaviour becomes the default
  - [ ] 🟢 Opaque upgrade; a client config option turns off item rendering instead
- [ ] 🟡 Shift+wrench grid overlay: right click connects/disconnects that side
- [ ] 🟡 Sneaky-faces section in the pipe GUI (per module in chassis module GUIs): add/remove the faces of the
  connected inventory the pipe interacts with. Default: only the touching face. Several faces are tried in
  `ForgeDirection` order (down, up, north, south, west, east)
- [ ] 🟢 Deprecate the "upgrade module upgrade" and make chassis upgradable by default. Settle per-module vs. shared chassis
  upgrades first
- [ ] 🟢 4 upgrade slots per module/pipe, insertable through the side upgrade GUI without a pipe controller

**2b. New upgrade set**
- [ ] 🟠 Buffer upgrade (also the prestock)
- [ ] 🟠 Hot upgrade. Pairs with [pipe-hibernation.md](pipe-hibernation.md)
- [ ] 🟡 Crafting upgrade
- [ ] 🟡 Crafting monitor upgrade (the monitor UI is in 3c)
- [ ] NBT filter upgrade, also unlocks the NBT option in crafting pipes
- [ ] OreDict filter upgrade, also unlocks the OreDict option in crafting pipes
- [ ] Damage upgrade: filter by damage values (includes 🟡 fuzzy damage split)
- [ ] Placement rules upgrade: choose which slot to extract from / insert into
- [ ] 🟡 Speed upgrade rework: extraction speed / stack size and pattern crafting table speed, not travel speed
- [ ] 🟡 Logical extractor upgrade
- [ ] CC control upgrade: ComputerCraft / OpenComputers integration
- [ ] 🟡 NBT / OreDict / damage support for the supplier pipe (reuse the filter upgrades above)

**2c. Modules & routing priority**
- [ ] 🟡 Rethink modules: deprecate duplicate modules, replace them with upgrades (do this first, it decides the scope
  of the rest). Placed duplicates keep working, or are converted to the base module plus the matching upgrades
- [ ] 🟢 Convert item sink modules between types in the crafting table / assembler / chisel; the ones that don't fit the
  upgrades (aspect sink, bee sink, polymorphic) in the chisel
- [ ] 🟡 ItemSink priority
- [ ] 🟡 Priority for providers and basic pipes
- [ ] 🟡 Satellite and fluid satellite modules
- [ ] 🟡 Extractor pipe
- [ ] 🔴 Fluid modules in the chassis (unblocks module-based fluid crafting from 1c)

**Done when:** the upgrade list is final, every upgrade/module has a single clear purpose, and no connection behaviour
needs an upgrade item.

## Phase 3: Block UI & request table

**Goal:** a modern request workflow: type the amount, search, see what happened, cancel.

**3a. Request inputs**
- [ ] 🟢 Request pipe & request table: input field for requested items, number fields instead of the request buttons
- [ ] 🟢 Logistics fluid request: input field for requested liquids
- [ ] 🟢 "T" search for request items
- [ ] 🟢 View fluids in different units (mB, buckets, multiples of 144, …)

**3b. Request table & pipes**
- [ ] 🟡 Request table
- [ ] 🟡 Logistics fluid request pipe mk2
- [ ] 🟡 Better GUI for successful / unsuccessful requests
- [ ] 🟡 NEI recipe support, items and fluids

**3c. Crafting monitors & block access**
- [ ] 🟡 Improved crafting monitor, with a cancel button hooked to the 1a backend
- [ ] 🟡 Supplier crafting monitor
- [ ] 🟢 Open crafting pipes without a wrench

**Done when:** a player can search, request an exact amount of an item or fluid, follow it in the monitor, and cancel it
without a wrench or button spam.

## Phase 4: QoL, balance, polish

**4a. QoL & GUI tweaks**
- [ ] 🟢 Tooltips for items and modules
- [ ] 🟢 HUD glasses in the baubles slot
- [ ] 🟢 Basic fluid pipe GUI: import button, default route toggle, more filter slots
- [ ] 🟡 Matter manipulator support
- [ ] 🟢 Fewer particle animations, with an option to turn them off (the lag side is B8 in the bug list)

**4b. Balance**
- [ ] 🟢 Chassis rebalance (after 2a, since chassis are upgradable by default then)
- [ ] 🟢 Recipe rebalance (after Phases 2–3 settle the final item list)

**4c. Textures**
- [ ] New modernized icons for modules (once 2c is final)
- [ ] New modernized icons for upgrades (once 2b is final)

**4d. Documentation**
- [ ] 🟢 Update the questbook tab to explain the different pipes
- [ ] 🟡 Write the GTNH Logistics Pipes guide / documentation

**Done when:** the rework is balanced, documented and ready to release.

## Parallel track: Transport & routing

- [ ] 🔴 Item transport rewrite, clumps ([item-transport-rewrite.md](item-transport-rewrite.md)). Phase 1 is in code and
  passed its first in-game pass on 2026-09-30; open issues are in the bug list
- [ ] 🟠 Transport controller blocks (the instant tier needs pass-through hop skipping in the transport rewrite)
- [ ] 🟠 Only LP pipes route and carry items: drop BuildCraft / Thermal Dynamics / other pipe support
- [ ] 🟡 GregTech pipes as buffers
- [ ] 🟡 Tesseracts / special pipe connections as buffer endpoints
- [ ] 🟠 Re-add and upgrade the inv. system connector pipe
  - Linked buffers (in code 2026-10-16, not tested in game yet): when both connectors sit on a buffer that moves items
    by itself, the sending end hands the item straight to the receiving end and nothing goes into the inventory
    (`PipeItemsInvSysConnector.passThroughLinkedBuffer`, handlers in `proxy/bufferlink`). It costs the same 6 power at
    the receiving end as an extraction. Without power, a broken link or a buffer that doesn't fit, the item goes the
    normal way. Handled buffers:
    - GT machine hulls (all tiers, bronze/steel too): both connectors on the same hull. A hull has an item slot and a
      tank, so it carries items and fluid packets;
    - EnderStorage chests: same colors and owner, any dimension (the receiving connector must be loaded), items only;
    - GT long distance pipelines: sender on the input end, receiver on the output end, one way only. The item
      pipeline carries only items, the fluid pipeline only fluid packets. Reads GT's protected `mTarget` by
      reflection.
    - EnderStorage tanks: same colors and owner, any dimension, fluid packets only. A tank isn't an item inventory,
      so the connector connects to it only as a linked buffer: the handoff is the only way through, and an item that
      can't be handed over is sent back instead of dropped.
  - Each handler says what its buffer carries (`BufferContent`: items, fluids or both). Item-only and fluid-only
    links put a `BufferContentRouteFilter` on the connector's direct connection (both route scanners), so nothing
    else is routed, provided or crafted across them: no fluid packets through ender chests or item pipelines, no
    items through ender tanks or fluid pipelines. The handoff checks the same, as a safety net. Open: a connector
    touching two buffers of different kinds keeps only the first route the scanner finds, so that route's filter
    applies to the other side too.
  - A broken link stops the handoff right away: the link is checked again for every item (ender storage color
    change, pipeline broken in the middle, hull gone, receiving connector unloaded or out of power), and the item
    goes into the inventory the normal way (or back, for an ender tank).
  - More handlers to come: whenever another block or mod is found that moves items or fluids instantly between two
    places (shared storage, paired endpoints), add an `IBufferLinkHandler` for it in `proxy/bufferlink` and register
    it in `BufferLinkManager.load()`.
- [x] 🟠 Pooled network power (in code 2026-10-15, not tested in game yet): every power junction in an island adds to
  that island's power pool, and any pipe in the island draws from the pool. No routes to power junctions.
  - The graph publish builds the pools (`JunctionGraphWriter.publish`, `PowerPool`). A pool reuses its island's
    number. An island is split only when a corridor in it may not pass power: the writer keeps a per-junction
    "power-gated" flag (a corridor with filters or without `CAN_POWER_FROM`), and only islands with a gated junction
    run the extra union-find over the corridors that do pass power. Other islands cost nothing extra.
  - A pipe's lookup is `NetworkGraph.powerPool(junctionId)`, an array read. `JunctionRouter.getPowerProvider()` builds
    the pool view once per pool and snapshot and caches it on the pool. Joining or leaving an island (merge,
    split, provider added or removed) is a publish, so it updates the pools.
  - Power junctions hold no power of their own. Each adds its capacity (`MAX_STORAGE`) to its pool's
    `PowerPoolStorage`, and pipes draw from that storage directly (`IRouter.getPowerPool()`), with no provider
    choice. A graph pool binds to its storage the first time a pipe draws from it. When the member junctions change
    (merge, split, junction added or removed), each junction brings its share of its old pool's energy, in proportion
    to capacity, so energy is neither lost nor created.
  - A junction that leaves (broken, chunk unloaded) takes its share along and saves it as `powerLevel` in its NBT; on
    load it brings that share into its pool. Breaking a junction loses its share, as before.
  - A junction touching two networks is one store, so it joins their pools (`PowerPool.Member` keys at publish). Before,
    both networks drew from it separately.
  - The junction GUI, HUD, CC commands and RF/EU input show and use the whole pool (`PowerJunctionLevel` now sends the
    capacity too). The "needs more power" gate trigger is per pool: off at full, on below half.
  - Scope: powering the network itself only. Subsystem power (RF/EU supplier upgrades) keeps its route-based
    `powerView()`, which now runs only for pipes with supplier upgrades.
  - One-way pipes no longer restrict power: a corridor passing power in either direction joins the pool.
  - Corridors that don't pass power in either direction split the pool (inventory system connectors).
  - Firewall pipes: a power-blocking firewall (`PipeItemsFirewall.blockPower`, on by default) splits the pool. The
    firewall junction is its own pool, so a power junction attached directly to a firewall powers only the firewall.
    The flag is read live, so `FireWallFlag` calls `refreshPowerPools()` when it changes.
  - Power lasers (visual) need a source junction; pick the nearest only when they are shown. (open)
- [ ] 🟡 Fiber pipe: power-only link between subnets that stay separate networks. The pipe divides the network
  (`divideNetwork()`), so each subnet is its own island and power pool.
  - Power grid: a structure that only stores which pool ids are linked by fiber runs into one grid. At publish, a
    small union-find over the pools (one union per fiber run) gives each pool its grid; a pipe draws from every pool
    of its grid. No delegation through the fiber at draw time, so no visited sets and no A→B→C→A loops.
  - Per fiber run (flood fill, cached until a fiber pipe changes): the junctions attached on each side; a branched run
    links several subnets. The run reports its links to the writer like corridors, and the publish turns them into
    pool links.
  - Firewalls still apply: a link joins the pools on each side of the fiber, and those pools already end at
    power-blocking firewalls.
  - refusing items entering the pipe
  - open: whether it carries subsystem power (RF/EU supplier upgrades)
- Junction-graph router: [router-rework/](router-rework/) (spec, developer guide, code review guide, benchmarks)

## Cross-cutting: GUI & debug

- [ ] 🟠 Rewrite every GUI with ModularUI, adding controls for the decided features where old GUIs lack them
  ([migration-status.md](../.claude/modularUI-docs/migration-status.md)). Deprecated modules keep their legacy GUI
  instead (the exception to the MUI rule) until they are deleted. Checklist of what is left (pipes, modules, blocks,
  items) is in migration-status.md
  - [ ] 🟢 Quick wins: `PipeItemsBasicLogistics` (its item sink module already has an MUI) and
    `ModuleTypeFilterItemSink` (same GUI as the migrated string-based sinks)
  - [ ] 🟢 Delete the dead legacy GUI paths listed there (`GuiUpgradeManager`, `GuiProviderPipe`, `GuiCardManager`,
    `ModuleCraftingMuiDynamic`)
- [ ] 🟠 Compatibility with old bases: a world from the last released version loads and keeps working after the
  update. Removal warnings on deprecated blocks, items, modules and upgrades. NBT migrations for every changed save
  format
- [ ] 🟢 One major pack version after the rework ships: delete the deprecated items, blocks, modules and their legacy
  GUIs
- [ ] 🟢 Legacy wrench item

## After the rework: Integrations & large features

Out of scope for the four phases. Revisit once Phase 4 ships.

- [ ] 🟠 Re-add energy transportation to LP
- [ ] 🔴 AE2 connectivity
  - [ ] 🟠 AE requester pipe

## Done

- Pattern crafting pipe, blocking and smart blocking
- Item and process crafting modules, item byproducts
- Satellite rework
- NBT / OreDict support in crafting
- Crafting patterns mk1–mk3, crafting overflow policy
- Crafting monitor on by default (the crafting monitor upgrade narrows it again)
- Prestock upgrade, merged into the buffer upgrade
- Pipe bodies baked into chunk geometry (FPS lag near many pipes), passed in game 2026-09-30
