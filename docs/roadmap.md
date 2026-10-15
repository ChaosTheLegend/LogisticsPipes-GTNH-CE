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
- [ ] 🔴 Fluid crafting, the general case (the module-based part waits for the fluid chassis in 2c)

**Done when:** large multi-step item and fluid requests resolve, can be cancelled cleanly, and reserved ingredients stay
reserved.

## Phase 2: Upgrades & modules

**Goal:** a small, clear set of upgrades, chassis that are upgradable by default, and tools in place of the connection
upgrades.

**2a. Cleanup**
- [ ] Deprecate unused/useless upgrades (removal warning; installed ones are migrated or keep working):
  - [ ] 🟡 Sneaky upgrade, replaced by the screwdriver overlay below. Migrate installed ones to the per-side setting
  - [ ] 🟡 Side-block (disconnection) upgrades, replaced by the shift+wrench overlay below. Migrate installed ones to
    the per-side setting
  - [ ] 🟢 Advanced satellite upgrade; its behaviour becomes the default
  - [ ] 🟢 Opaque upgrade; a client config option turns off item rendering instead
- [ ] 🟡 Shift+wrench grid overlay: right click connects/disconnects that side
- [ ] 🟡 Screwdriver grid overlay: sets the sneaky side per connection
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
- [ ] 🔴 Logistics fluid chassis and fluid modules (unblocks module-based fluid crafting from 1c)

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
- [ ] 🟠 Pooled network power: every power junction in an island (graph component) adds to that island's power pool,
  and any pipe in the island draws from the pool. No routes to power junctions: the snapshot already knows each
  junction's component (`componentOf`), so the publish step keeps a list of power providers per component, and
  `canUseEnergy` / `useEnergy` walk that short list (a draw takes from the fullest provider, or round-robin). Only
  joining or leaving an island (merge, split, provider added or removed) changes a pipe's pool, and that is already
  part of the publish. Replaces the per-pipe `powerView()` sweep (`findRoutesToTargets` to every power junction after
  every graph change).
  - Scope: powering the network itself only. Power transport (RF/EU supplier upgrades, subsystem power) is separate
    and keeps its current path for now.
  - One-way pipes no longer restrict power (only BC/TD had power-passing one-way pipes, and they are being dropped).
  - Firewall pipes are the only exception: a power-blocking firewall (`PipeItemsFirewall.blockPower`, on by default)
    splits the pool, so a pool is a component minus the corridors through power-blocking firewalls. Computed at publish
    like the components (union-find over the corridors that pass power).
  - Power lasers (visual) need a source junction; pick the nearest only when they are shown.
- [ ] 🟡 Fiber pipe: power-only link between subnets that stay separate networks. The pipe divides the network
  (`divideNetwork()`) and implements `ILogisticsPowerProvider`, so each subnet registers it like a power junction and
  its power search ends there. Drawing power delegates to the far side: a junction attached there returns its cached
  power providers (with that subnet's filters). Needs:
  - a visited set of fiber runs, so a draw can't loop back through the fiber it came from or around A→B→C→A
  - a scanner change: a fiber reached along a corridor (behind transport pipes) registers as a provider of the scanning
    junction with that corridor's filters; today only providers touching the routed pipe register
  - per fiber run (flood fill, cached until a fiber pipe changes): the junctions attached on each side; a branched run
    links several subnets
  - refusing items entering the pipe
  - open: whether it carries subsystem power (RF/EU supplier upgrades)
- Junction-graph router: [router-rework/](router-rework/) (spec, developer guide, code review guide, benchmarks)

## Cross-cutting: GUI & debug

- [ ] 🟠 Rewrite every GUI with ModularUI, adding controls for the decided features where old GUIs lack them
  ([migration-status.md](../.claude/modularUI-docs/migration-status.md)). Deprecated modules keep their legacy GUI
  instead (the exception to the MUI rule) until they are deleted
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
