# Rework changes vs GTNH upstream

What the `crafting_rework` branch changes compared with the GTNH upstream, `GTNH-origin/master`.

- **Compared:** `crafting_rework` at `6d86ac6b` (merge of `Thumb/crafting_rework_2`) against `GTNH-origin/master` at
  `df8e3fb0` ("Fix and cleanup Crafting sign crash (#137)").
- **Upstream is fully contained in this branch**, so `git diff GTNH-origin/master HEAD` shows exactly what the rework
  adds or changes. Nothing was merged or pushed to produce these docs.
- **Size:** 390 files, about 63,300 lines added and 1,000 removed.
  - 250 files are new and 140 existing upstream files were modified.
  - No upstream file was deleted outright. The legacy pattern GUIs removed during the rework were never in upstream.

How to read these docs:

- Each feature doc says what changed for players, how it works, which upstream classes were touched, save-compat notes,
  known gaps, and a table of its files.
- [class-index.md](class-index.md) lists **every** changed file by package. Each row links to the doc section that
  explains it.
- The docs were written by reading the code and diffs, not by playing. Anything marked as a bug or gap was seen in the
  code and has **not** been confirmed in game.

## Feature docs

| # | Feature | Files | Added / modified | Lines | Summary |
|---|---|---|---|---|---|
| 01 | [Pattern crafting system](01-pattern-crafting.md) | 138 | 103 / 35 | +24,149 / -144 | Pattern items, Pattern Crafting Pipe, staged crafting through the request tree, item/fluid satellites, byproducts, fluid crafting, cancellation, persistence, HUD and Crafting Monitor, crafting debug tooling, NEI import, Pattern Crafting Table |
| 02 | [Request table rework](02-request-table.md) | 31 | 27 / 4 | +4,454 / -20 | New "Request Table Mk2" next to the old one: combined item+fluid network list, internal item and fluid storage, storage upgrades, request popup, ghost crafting grid; fluid orderer improvements |
| 03 | [Router rework](03-router-rework.md) | 50 | 41 / 9 | +8,971 / -70 | Link-state `ServerRouter` replaced on the server by `JunctionRouter`: an immutable junction/corridor graph, a single writer thread, cached A* queries and an in-transit tracker. Includes tests and 50k-pipe benchmarks |
| 04 | [Item transport rewrite](04-item-transport.md) | 24 | 7 / 17 | +1,760 / -181 | Items move between junctions as "clumps" that teleport after the travel time, with client-side animation. Pipe bodies are baked into chunk meshes. Adds `/lp pipespeed` |
| 05 | [GUI migration to ModularUI2](05-modularui-gui.md) | 97 | 62 / 35 | +21,877 / -300 | MUI layer for pipes and modules. Chassis, provider/supplier/analyser, fluid and satellite pipes and 10 modules now use MUI. Adds an upgrade sidebar, the Legacy Wrench, packet guards and a debug GUI. Also includes the MUI reference docs in `.claude/` (~16k lines) |
| 06 | [Pipes, compat, mod init & build](06-modules-pipes-compat-build.md) | 50 | 10 / 40 | +2,094 / -289 | Provider/fluid/basic pipe behaviour, GregTech battery support for the Electric Manager, `IInventoryUtil.isEmpty()`, proxies and registration, config, recipes, lang, dependencies and CI, project docs |
| | [Changed files index](class-index.md) | 390 | 250 / 140 | | Every file, by package, linked to its feature section |

Shared classes such as `CoreRoutedPipe`, `LogisticsTileGenericPipe`, `PipeTransportLogistics`, `LogisticsPipes` and
`ClientProxy` carry changes for several features. Each is listed once in the index under its main feature, and that
doc names the other features with links.

## Cross-cutting findings

The most important findings from all six docs. Each doc's "Known gaps / discrepancies" section has the full list and
the code references.

### Save compatibility (old worlds)

These conflict with the "never break existing worlds" rule in
[rework-design-decisions.md](../rework-design-decisions.md#compatibility-with-old-bases-2026-10-01):

- **Basic fluid pipe filters are lost.** `PipeFluidBasic` now stores its filter as a `FluidTank` and doesn't read the
  old `items` filter. The existing `LegacyHelper.readItemIdentifierInventoryAndConvertToTank` would migrate it.
  ([06](06-modules-pipes-compat-build.md#known-gaps--discrepancies))
- **Legacy crafting pipes stop requesting fluid ingredients.** `ModuleCrafter.getFluidMaterial` reads `_liquidTank`,
  which old saves never fill. ([01](01-pattern-crafting.md#known-gaps--discrepancies))
- **Request Table Mk2 stacks over 127 are not save-safe.** They are written with a vanilla byte `Count`. Overflow
  items are dropped in the world when storage upgrades are removed. ([02](02-request-table.md#known-gaps--discrepancies))
- **Clumps in flight are lost on downgrade.** Loading such a save with upstream LP drops those items.
  ([04](04-item-transport.md#known-gaps--discrepancies))
- **Hidden chassis upgrade slots.** Chassis upgrade slots 4–8 of old pipes keep working but are hidden by the 4-slot
  MUI sidebar. Module upgrade slots 3–4 can't be filled from any GUI. ([05](05-modularui-gui.md#known-gaps--discrepancies))
- **No migration paths yet.** No recipes or migration exist from old crafting pipes or request tables to the new ones.
  Most new content has no crafting recipe at all.

### Likely bugs seen in code (not tested in game)

- **Possible crash in `JunctionRouter.removeAllInterests()`.** `updateInterests` keeps the pipe's own interest set.
  `removeAllInterests()` then calls `.clear()` on the pattern crafting pipe's unmodifiable set, which should crash when
  such a pipe is broken or its dimension unloads. The copy fix went into the unused `ServerRouter`.
  ([03](03-router-rework.md#known-gaps--discrepancies))
- **Crafting Monitor cancel has no permission check.** `CraftingMonitorCancelPacket` has no permission, distance or
  open-container check. ([01](01-pattern-crafting.md#known-gaps--discrepancies))
- **Unfinished legacy crafter blocking mode.** It blocks forever and forwards arrivals twice.
  ([01](01-pattern-crafting.md#known-gaps--discrepancies))
- **Fluid Supplier Mk2 MUI.** Value syncs lost `.allowC2S()`, so client edits may not reach the server.
  ([05](05-modularui-gui.md#known-gaps--discrepancies))
- **Fluid/provider pipes with the Legacy Wrench.** The legacy GUIs for basic fluid and provider pipes are unreachable
  or inert. ([05](05-modularui-gui.md#known-gaps--discrepancies), [06](06-modules-pipes-compat-build.md#known-gaps--discrepancies))

### Design-doc mismatches

- **Advanced Satellite Upgrade is still required.** Pattern crafting pipes gate every satellite feature on it; the
  design says it should be removed and satellites should work by default.
- **Crafting monitor shape.** It exists as a block (meta 7) and a popup, not as the per-pipe upgrade with request limits
  that the design describes.
- **Missing upgrades.** The OreDict/NBT filter upgrades and the buffer upgrade are not implemented. Pattern flags and
  always-on buffering stand in for them.
- **Speed still comes from upgrades and power.** Transport controller blocks are not implemented yet.
- **Partial MUI migration.** Several pipes and modules are still legacy. Request Table Mk2 is a new legacy `GuiContainer`
  and the Crafting Monitor GUI is a new legacy screen, although new GUIs are supposed to use MUI.

### Stale documentation

- **ITEM_ROUTING.md** still describes the upstream link-state router.
- **pattern-crafting.md** refers to line numbers and classes from before the handler split.
- **MUI migration notes.** `.claude/modularUI-docs/migration-status.md` and the chassis handoff note are partly out of
  date.

## Regenerating

The file lists come from `git diff -M --name-status GTNH-origin/master HEAD` and `--numstat`. Each file was assigned to
one feature area by path. If the branch moves on, re-run the diff and update the affected doc's "Files" table and
[class-index.md](class-index.md).
