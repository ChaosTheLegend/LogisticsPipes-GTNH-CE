# 05 - GUI migration to ModularUI2 (MUI)

How the GUIs on `crafting_rework` differ from upstream `GTNH-origin/master`, plus the module, upgrade and chassis
changes that go with them. Everything here was checked against `git diff GTNH-origin/master HEAD`. The rule set is in
[rework-design-decisions.md](../rework-design-decisions.md) (section "GUI" and "Compatibility with old bases"), the work
plan in [roadmap.md](../roadmap.md) ("Cross-cutting: GUI & debug").

Related docs: pattern crafting pipe GUIs → [01-pattern-crafting.md](01-pattern-crafting.md), new request table GUI →
[02-request-table.md](02-request-table.md), router / interest registry → [03-router-rework.md](03-router-rework.md),
item transport → [04-item-transport.md](04-item-transport.md), non-GUI pipe/module behaviour (e.g. GT battery support
in the electric manager) → [06-modules-pipes-compat-build.md](06-modules-pipes-compat-build.md).

## Overview

**Upstream:** ModularUI2 (`com.github.GTNewHorizons:ModularUI2`) was already a dependency, but only one GUI used it:
the Fluid Supplier Mk2 pipe, through `IMUICompatiblePipe` (the pipe builds its own widgets into a panel that
`LogisticsTileGenericPipe.buildUI` creates). Everything else used the old LP GUI stack: `GuiHandler`/`GuiIDs`,
`NewGuiHandler` GUI providers, `DummyContainer` and the `gui/modules/Gui*.java` screens.

**Now:** there is a small LP layer on top of MUI ([LogisticsModularUI](../../src/main/java/logisticspipes/gui/modularUI/LogisticsModularUI.java)
and friends) with two entry points:

- **Pipes** implement [IMUICompatiblePipeV2](../../src/main/java/logisticspipes/api/IMUICompatiblePipeV2.java). A
  wrench click on such a pipe opens its MUI (unless the player holds the Legacy Wrench).
- **Modules** implement [IMUICompatibleModule](../../src/main/java/logisticspipes/api/IMUICompatibleModule.java). The
  same `*MuiDynamic` class is used when the module item is right-clicked in hand, when it sits in a chassis, and when a
  dedicated pipe (provider, supplier, apiarist analyser) wraps its module.

GUIs on MUI now:

| GUI | MUI class |
|---|---|
| Chassis pipes (Mk1-Mk5) | [ChassisGui](../../src/main/java/logisticspipes/gui/modularUI/ChassisGui.java) |
| Provider pipe, Supplier pipe, Apiarist Analyser pipe | their module's `*MuiDynamic`, wrapped by [GenericPipeLogisticsGui](../../src/main/java/logisticspipes/gui/modularUI/GenericPipeLogisticsGui.java) |
| Basic fluid pipe | [PipeFluidBasicMui](../../src/main/java/logisticspipes/gui/modularUI/pipes/PipeFluidBasicMui.java) |
| Fluid Supplier Mk2 pipe | [PipeFluidSupplierMk2Mui](../../src/main/java/logisticspipes/gui/modularUI/pipes/PipeFluidSupplierMk2Mui.java) (moved out of the pipe class) |
| Item and fluid satellite pipes (+ pattern satellites) | [PipeSatelliteMui](../../src/main/java/logisticspipes/gui/modularUI/pipes/PipeSatelliteMui.java) |
| 10 modules, in hand and in a chassis | `gui/modularUI/dynamicModules/*MuiDynamic` (table below) |
| Pattern crafting table | [PatternCraftingTableMui](../../src/main/java/logisticspipes/gui/modularUI/blocks/PatternCraftingTableMui.java) |
| Pattern crafting pipe, handheld pattern | `gui/modularUI/pipes/patterncrafting/*`, see [01-pattern-crafting.md](01-pattern-crafting.md) |

Still legacy: every other pipe (basic logistics/item sink pipe, extractor-style pipes, request pipes, firewall,
crafting pipe, ...), the non-migrated modules (OreDict / Type-filter / Apiarist / Thaumic aspect sinks, extractors,
advanced extractors, quicksort, CC quicksort, fluid supplier, crafting module), the pipe controller / upgrade manager
GUI, the HUD/settings GUIs, and the per-module upgrade slots of a chassis. The deprecated crafting module keeps its
legacy GUI on purpose (design rule: deprecated module GUIs don't get an MUI).

## Features

### MUI integration layer

What it does: gives every LP GUI the same shape: an object that knows its id, size and how to add its widgets to a
parent, so the same module UI can be put into a hand-held panel, a pipe panel or one page of the chassis GUI.

How it works:

- [LogisticsModularUI](../../src/main/java/logisticspipes/gui/modularUI/LogisticsModularUI.java) - abstract base.
  `getId()`, `getWidth()`, `getHeight()`, `addWidgets(parent, syncManager, addPlayerInventory)` and `getPanel(...)`
  (default: `ModularPanel.defaultPanel` + `addWidgets`). It carries a **prefix**; `getFullId()` = `prefix + "_" + id` is
  used as key for every sync handler, so two modules of the same type in one chassis don't share sync handlers. The
  old `addWidgets(parent, addPlayerInventory)` overload (no sync manager) is `@Deprecated` but still the path the
  generic pipe wrappers call.
- [GenericModuleMUI](../../src/main/java/logisticspipes/gui/modularUI/GenericModuleMUI.java) - base for module UIs,
  holds the typed module. [LogisticsPipeMUI](../../src/main/java/logisticspipes/gui/modularUI/LogisticsPipeMUI.java) -
  base for pipe UIs, holds the `CoreRoutedPipe`.
- [PipeGuiFactory](../../src/main/java/logisticspipes/gui/modularUI/PipeGuiFactory.java) - `fromModule(pipe, module)`
  → [GenericPipeLogisticsGui](../../src/main/java/logisticspipes/gui/modularUI/GenericPipeLogisticsGui.java),
  `fromMui(pipeMui)` → [GenericSimplePipeLogisticsGui](../../src/main/java/logisticspipes/gui/modularUI/GenericSimplePipeLogisticsGui.java).
  Both wrappers put the inner UI on the LP background texture. Both also contain an upgrade column, but it is commented
  out ("Disabled until all pipes have a Mui gui"). The factory also builds the **upgrade sidebar** (see below).
- [IMUICompatiblePipeV2](../../src/main/java/logisticspipes/api/IMUICompatiblePipeV2.java) - `getPipeGui()` returns a
  `LogisticsModularUI`; `openGui` calls `ModularUIHelper.openPipeUI` (MUI tile-entity factory on the pipe position).
  `LogisticsTileGenericPipe.buildUI` (file covered by 04) now accepts V2 pipes and returns
  `getPipeGui().getPanel(...)`; the old `IMUICompatiblePipe` path is kept, but no pipe implements it any more.
- [IMUICompatibleModule](../../src/main/java/logisticspipes/api/IMUICompatibleModule.java) - `getHandGui()`,
  `getPipeGui()`, `getPipeGui(prefix)` (default ignores the prefix; all 10 modules override it), and `openGui`, which
  calls `ModularUIHelper.openModuleUI` → MUI `playerInventory().openFromMainHand`.
- [ModularUIHelper](../../src/main/java/logisticspipes/compat/ModularUIHelper.java) - gained `openModuleUI` and a
  `TAB_RIGHT_TEXTURE` (`gui_tab_right.png`, currently not referenced by any GUI) and an unused `verifyServerSide`.
- [SimpleInventorySlot](../../src/main/java/logisticspipes/gui/modularUI/SimpleInventorySlot.java) - `ModularSlot`
  whose `getItemStackLimit` doesn't touch the slot. MUI's default empties and refills the slot, which with
  `SimpleStackInventory` (stores copies) made shift-clicked items vanish. Used by the pattern crafting table.
- [DraggableFlow](../../src/main/java/logisticspipes/gui/modularUI/DraggableFlow.java) - a `Flow` (can hold children)
  that can be dragged around by a `DragHandle`, like MUI's `DraggableWidget`. Used for the upgrade sidebar.
- [LogisticsModuleData](../../src/main/java/logisticspipes/gui/modularUI/LogisticsModuleData.java) - `GuiData` carrying
  a module; currently unused.

Opening flow (pipe): `CoreRoutedPipe.blockActivated` (wrench branch, file covered by 04) checks
`this instanceof IMUICompatiblePipeV2` and that the held item is not an `ItemLegacyWrench`, applies the security
station `openGui` check, and calls `openGui`. Otherwise the upstream path runs (module `getPipeGuiProviderForModule()`
or `onWrenchClicked`).

Opening flow (module in hand): [ItemModule](../../src/main/java/logisticspipes/items/ItemModule.java) now implements
`IGuiHolder<PlayerInventoryGuiData>`. `openConfigGui` sends MUI modules to `openGui`; others keep the legacy
`getInHandGuiProviderForModule()` path. `buildUI` creates the module from the held stack (with a
`DummyWorldProvider` so `MainProxy.isServer/isClient` checks work), reads its item NBT, builds `getHandGui()`, and
saves the module back onto the held item on close.

### Module GUIs

What it does for the player: the migrated modules have one MUI that looks the same in hand, in a chassis page and in
their dedicated pipe. Filter slots are phantom slots; toggles are cycle buttons.

How it works: each `*MuiDynamic` extends `GenericModuleMUI`, implements `addWidgets(widget, syncManager, ...)` and
registers its sync handlers through `syncManager.getOrCreateSyncHandler(getFullId() + "_<name>", ...)` (when no sync
manager is passed, it creates unregistered sync values instead). Each module class got `getHandGui()` /
`getPipeGui()` / `getPipeGui(prefix)` returning it, plus small accessors the UI needs.

| Module | MUI class | Controls | Module-side changes for the GUI |
|---|---|---|---|
| [ModuleItemSink](../../src/main/java/logisticspipes/modules/ModuleItemSink.java) | [ModuleItemSinkMuiDynamic](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleItemSinkMuiDynamic.java) | 9 filter slots, Import button, Default route on/off | none (also got room-aware sinking, see below) |
| [ModuleProvider](../../src/main/java/logisticspipes/modules/ModuleProvider.java) | [ModuleProviderMuiDynamic](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleProviderMuiDynamic.java) | 3x3 filter, extraction mode cycle (6 modes), whitelist/blacklist | `getExtractionSpeed()` (also staged-crafting reservations, see 01) |
| [ModuleActiveSupplier](../../src/main/java/logisticspipes/modules/ModuleActiveSupplier.java) | [ModuleActiveSupplierMuiDynamic](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleActiveSupplierMuiDynamic.java) | 3x3 "items to keep stocked", request mode cycle | dummy inventory stack limit 127 → `Integer.MAX_VALUE` |
| [ModulePassiveSupplier](../../src/main/java/logisticspipes/modules/ModulePassiveSupplier.java) | [ModulePassiveSupplierMuiDynamic](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModulePassiveSupplierMuiDynamic.java) | filter slots ("Requested items") | none |
| [ModuleTerminus](../../src/main/java/logisticspipes/modules/ModuleTerminus.java) | [ModuleTerminusMuiDynamic](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleTerminusMuiDynamic.java) | filter slots ("Terminated items") | none |
| [ModuleEnchantmentSinkMK2](../../src/main/java/logisticspipes/modules/ModuleEnchantmentSinkMK2.java) | [ModuleEnchantmentSinkMK2MuiDynamic](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleEnchantmentSinkMK2MuiDynamic.java) | filter slots | none |
| [ModuleElectricManager](../../src/main/java/logisticspipes/modules/ModuleElectricManager.java) | [ModuleElectricManagerMuiDynamic](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleElectricManagerMuiDynamic.java) | filter slots, Charge / Discharge cycle | `IModuleInventoryOverride` (GT battery slots, see below) |
| [ModuleApiaristAnalyser](../../src/main/java/logisticspipes/modules/ModuleApiaristAnalyser.java) | [ModuleBeeAnalyzerMuiDynamic](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleBeeAnalyzerMuiDynamic.java) | "Extract items: On/Off" | `setExtractMode(boolean)`, `getExtractModeBool()` |
| [ModuleModBasedItemSink](../../src/main/java/logisticspipes/modules/ModuleModBasedItemSink.java) | [ModuleModBasedItemSinkMuiDynamic](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleModBasedItemSinkMuiDynamic.java) | analyse slot + "Add", list of up to 9 mods with remove buttons | see below |
| [ModuleCreativeTabBasedItemSink](../../src/main/java/logisticspipes/modules/ModuleCreativeTabBasedItemSink.java) | [ModuleCreativeTabBasedItemSinkMuiDynamic](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleCreativeTabBasedItemSinkMuiDynamic.java) | same, for creative tabs | see below |
| `ModuleCrafter` (deprecated) | [ModuleCraftingMuiDynamic](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleCraftingMuiDynamic.java) exists but has no callers | - | module does not implement `IMUICompatibleModule`; keeps the legacy GUI |

Mod / creative-tab based sinks: both dropped `IStringBasedModule` and `IHUDModuleHandler` (no HUD, no `listChanged()`
packet sync any more) and gained a never-saved 1-slot `analyseInventory`, `MAX_ENTRIES = 9`, and
`addMod`/`removeMod` (`addTab`/`removeTab`). The list is synced with a `GenericListSyncHandler<String>`; Add/remove go
through `InteractionSyncHandler`s, so they run on the server. The creative-tab sink's client info header changed from
"Mods:" to "Tabs:". NBT keys (`Mod<i>`) are unchanged, so saved lists load as before.

Item sink Import button: the button's `onMousePressed` calls `module.importFromInventory()` directly in the widget,
with no sync handler, i.e. on the client side.

Non-GUI changes in the same module classes (summary only):

- `ModuleItemSink` - only advertises/accepts items the target inventory has room for (20-tick room cache, in-transit
  check outside chassis, `insertionFailed` hook). Belongs with routing/transport, see 03/04.
- `ModuleElectricManager` - implements [IModuleInventoryOverride](../../src/main/java/logisticspipes/interfaces/IModuleInventoryOverride.java),
  returning an [IModuleInventory](../../src/main/java/logisticspipes/interfaces/IModuleInventory.java) from the IC2 proxy
  for GT battery buffer / machine battery slots that the sided inventory hides; extraction uses it and a failing slot
  now `continue`s instead of aborting the scan. Details in 06.
- [ChassiModule](../../src/main/java/logisticspipes/modules/ChassiModule.java) - room check uses the sub-module's
  `IModuleInventory` when it has one; forwards `insertionFailed` to all sub-modules.
- [LogisticsModule](../../src/main/java/logisticspipes/modules/abstractmodules/LogisticsModule.java) - `getWorld()`,
  `getService()`, and the no-op `insertionFailed(ItemIdentifier)` hook.
- `ModuleExtractor` / `ModuleQuickSort` - early return when the inventory is empty. `ModuleCCBasedQuickSort` - uses
  `InterestRegistry.getRoutersInterestedIn` instead of `ServerRouter` (03).
- `ModuleProvider` - `IStagedProviderReservation` for staged crafting (01).

### Chassis GUI

What it does for the player: wrenching a chassis opens one window with a row of tabs on top, one per chassis slot.
Each tab has the module slot itself (insert/remove modules there) and selecting it shows that module's MUI below. A
draggable 4-slot upgrade column sits on the right, the player inventory at the bottom. Modules without an MUI show
"Module not compatible with MUI yet", crafting modules show "Crafting Modules are being replaced with Pattern Crafting
Pipes, please use them".

How it works ([ChassisGui](../../src/main/java/logisticspipes/gui/modularUI/ChassisGui.java), 254x210):

- [PipeLogisticsChassi](../../src/main/java/logisticspipes/pipes/PipeLogisticsChassi.java) implements
  `IMUICompatiblePipeV2` and returns `new ChassisGui(this)`. (It also gained an unused `blockingMode` field.)
- Each page edits the **live installed module** (`pipe.getModules().getSubModule(slot)`) with the prefix
  `chassis_slot_<n>`, so edits affect the running pipe and two same-type modules don't collide.
- Pages are `DynamicSyncedWidget`s with an `initialChild` built during normal panel construction. A
  `DynamicSyncHandler` per slot (`chassis_module_dynamic_<n>`) rebuilds a page when the module in that slot is
  swapped while the GUI is open; on that rebuild the server forces every new value/slot sync handler to push its value
  once.
- Before building, `pipe.InventoryChanged(...)` runs so the client's module array matches the item stacks. For 60
  ticks after opening, a common tick re-runs it and compares each module's NBT fingerprint; a change rebuilds that page
  (works around module fields settling after a chunk load).
- Saving: on close every installed module is written back to its item stack (`ItemModuleInformationManager.saveInfotmation`
  + `setInventorySlotContents`, because `getStackInSlot` returns a copy). When a module is taken out of its slot, its
  last state is captured before teardown and written onto the departing stack in `onPickupFromSlot`.
- Tab end caps use `pipe.getChassiSize() - 1` (was hard-coded for the 8-slot Mk5). Module slots use
  `module_slot_1.png`.
- The upgrade column is `PipeGuiFactory.getUpgradeGui(pipe.getUpgradeManager().getUpgradeInventory(), syncManager)` -
  the **pipe-level** upgrade inventory, not the per-module `ModuleUpgradeManager`s.

Design history: `.claude/CHASSIS_GUI_HANDOFF.md` (session notes, root causes, crash fix).

The legacy chassis GUI (`ChassiGuiProvider` / `GuiChassiPipe`) is still in the code and opens with the Legacy Wrench.

### Upgrade sidebar and upgrade inventories

What it does: a 4-slot column of upgrade slots (with `upgrade_slot.png` backgrounds) that can be dragged by a white
handle ("Drag to move"). Slots accept any `ItemUpgrade`.

How it works:

- `PipeGuiFactory.getUpgradeGui(handler, syncManager)` registers the slot group `upgrade_inventory` (4 slots) and
  builds a `DraggableFlow` with a `DragHandle` and a 4-row `SlotGroupWidget`. An overload takes a slot factory so a
  caller can use its own slots (the pattern crafting table limits it to speed upgrades with `SimpleInventorySlot`).
  `UPGRADE_GUI_WIDTH = 24`.
- [IPipeUpgradeManager](../../src/main/java/logisticspipes/interfaces/IPipeUpgradeManager.java) gained
  `getUpgradeInventory()` (MUI `IItemHandlerModifiable`). [UpgradeManager](../../src/main/java/logisticspipes/pipes/upgrades/UpgradeManager.java)
  returns an `InvWrapper` over its (still 9-slot) upgrade inventory; `CoreUnroutedPipe`'s dummy manager returns an
  empty `ItemStackHandler(0)` (file covered by 04). The sidebar shows slots 0-3 only.
- Used today by the chassis GUI and the pattern crafting table. The pipe wrappers have it disabled.
- [ModuleUpgradeManager](../../src/main/java/logisticspipes/pipes/upgrades/ModuleUpgradeManager.java) (per-module
  upgrades in a chassis): inventory and `upgrades[]` grew from **2 to 4 slots** (roadmap: "4 upgrade slots per
  module/pipe"), plus a `getUpgradeInventory()` wrapper that nothing calls yet.
- Instant satellite upgrade plumbing for pattern crafting: [ISlotUpgradeManager](../../src/main/java/logisticspipes/interfaces/ISlotUpgradeManager.java)
  `hasInstantSatelliteUpgrade()`, implemented by both managers (module manager defers to the pipe). See 01.
- [ItemUpgrade](../../src/main/java/logisticspipes/items/ItemUpgrade.java) registers new upgrade ids:
  `INSTANT_SATELLITE = 27` (01) and the request table upgrades 45-48 (02).

Compat: `SimpleStackInventory.readFromNBT` now grows the inventory if the saved `itemsCount` is bigger, so a
resized inventory never drops saved stacks; old 2-slot module upgrade NBT loads into the 4-slot inventory unchanged.

### Pipe GUIs

- **Basic fluid pipe** - [PipeFluidBasicMui](../../src/main/java/logisticspipes/gui/modularUI/pipes/PipeFluidBasicMui.java):
  one phantom `FluidSlot` ("Fluid filter") on `PipeFluidBasic.filterTank`. The legacy `GUI_Fluid_Basic_ID` cases were
  removed from [GuiHandler](../../src/main/java/logisticspipes/network/GuiHandler.java) (`GuiFluidBasic` itself is
  still in the tree). The pipe's change from `filterInv` to a `FluidTank` is in `PipeFluidBasic` (06).
- **Fluid Supplier Mk2** - [PipeFluidSupplierMk2Mui](../../src/main/java/logisticspipes/gui/modularUI/pipes/PipeFluidSupplierMk2Mui.java):
  the upstream widget code moved out of `PipeFluidSupplierMk2` into a `LogisticsPipeMUI` (fluid phantom slot, amount,
  partial requests toggle with tooltips, refill threshold).
- **Satellites** - [PipeSatelliteMui](../../src/main/java/logisticspipes/gui/modularUI/pipes/PipeSatelliteMui.java):
  satellite id number field + "Next free" button; for pattern satellites also a name field (max 64 chars, server may
  add a suffix to keep it unique). Size depends on the satellite type. Used by item and fluid satellite pipes.
- **Provider / Supplier / Apiarist Analyser pipes** - `PipeGuiFactory.fromModule(this, module)`, i.e. the module MUI in
  a `GenericPipeLogisticsGui`.
- **Pattern crafting table** - [PatternCraftingTableMui](../../src/main/java/logisticspipes/gui/modularUI/blocks/PatternCraftingTableMui.java):
  3x3 input, progress arrow, 3 outputs, player inventory, speed-upgrade column. Shift-click priority keeps upgrades out
  of the grid. Built from `PatternLogisticsCraftingTableTileEntity.buildUI`. Details in 01.

The pipe-side `implements IMUICompatiblePipeV2` / `getPipeGui()` changes live in the pipe classes owned by 01 and 06.

### Legacy Wrench

What it does: [ItemLegacyWrench](../../src/main/java/logisticspipes/items/ItemLegacyWrench.java) is a BuildCraft
`IToolWrench` (stack size 1, `canWrench` always true). Wrenching an MUI pipe with it skips the MUI and opens whatever
the upstream path opens (legacy module GUI / `onWrenchClicked`). Registered in `LogisticsPipes` as `legacyWrench`
("Legacy Wrench", icon `items/legacyWrench.png`); there is no recipe. Per the design doc it is for debugging only and
does not bring back legacy GUIs that were deleted.

### Debug GUI

What it does: the object debug window (opened by the debug commands `hand` / `me` and the debug target picker, all via `DebugGuiController.startWatchingOf`) now actually exists.
Upstream only had the API (`network.rs485.debuggui.api`) and loaded `network.rs485.debuggui.DebugGuiEntry` by
reflection; that class was missing.

How it works:

- [DebugGuiEntry](../../src/main/java/network/rs485/debuggui/DebugGuiEntry.java) (new): the server side builds a text
  snapshot of the watched object by reflection (depth 4, 25 entries per collection, 500 chars per string, 250k chars
  total) and sends it; the client opens a Swing `JFrame` "LP Debug - <name>" with Refresh, "Auto refresh" (1 s) and
  Close (also Escape). No window when the client is headless.
- [DebugGuiController](../../src/main/java/logisticspipes/commands/commands/debug/DebugGuiController.java): the server
  now assigns a free connection id per watch and sends it in `DebugPanelOpen` (upstream never set the id); it tracks
  ids per player and closes them in `clearServerDebuggers(player)` (called on logout). On the client, data for a
  session that isn't ready yet is queued and drained each tick instead of throwing `DelayPacketException`;
  `clearClientDebuggers()` runs on client world unload and when connecting to a server. Bounds checks on all ids.
- [DebugDataPacket](../../src/main/java/logisticspipes/network/packets/debuggui/DebugDataPacket.java) is now
  compressed, [DebugPanelOpen](../../src/main/java/logisticspipes/network/packets/debuggui/DebugPanelOpen.java) no longer
  is.

### GUI handlers and packet guards

- [GuiIDs](../../src/main/java/logisticspipes/network/GuiIDs.java) / [GuiHandler](../../src/main/java/logisticspipes/network/GuiHandler.java):
  new `GUI_New_Request_Table_ID = 39` (container + screen of the new request table, see 02); basic fluid pipe cases
  removed (now MUI).
- [NewGuiHandler](../../src/main/java/logisticspipes/network/NewGuiHandler.java): also scans `logisticspipes.crafting`
  for GUI providers, skips classes that aren't `GuiProvider`s, and on a dedicated server skips classes that fail to
  load (client-only screens in the scanned packages).
- [PacketHandler](../../src/main/java/logisticspipes/network/PacketHandler.java): skips non-`ModernPacket` classes
  during the scan; `getPacket` throws a clear `IllegalStateException` for unregistered packet classes.
- [PacketGuards](../../src/main/java/logisticspipes/network/PacketGuards.java) (new): server-side checks for packet
  handlers, because LP packets have no direction. `isOnClient` (S2C packet really on the client),
  `canConfigurePipe` (same world, within 8 blocks, security station `openGui`), `getOpenRequestTable` (table from
  the player's open container instead of client coordinates), `isPrivileged` (op or integrated-server owner). Used by
  the request table packets (02) and `CraftingRequestDebugRequest`; `canConfigurePipe` has no callers yet.

### Settings GUI

[GuiLogisticsSettings](../../src/main/java/logisticspipes/gui/GuiLogisticsSettings.java) /
[PlayerConfig](../../src/main/java/logisticspipes/config/PlayerConfig.java): the "VBO renderer", "fallback renderer"
and "pipe render distance" options are gone; only "new renderer" and "content render distance" remain. They were
removed from the network format (`writeData`/`readData`) and the saved player NBT as well; old NBT keys are simply
ignored. This goes with the renderer cleanup (`forceRenderOldPipe` removed in `CoreUnroutedPipe`).

### Other helpers

- [SimpleStackInventory](../../src/main/java/logisticspipes/utils/item/SimpleStackInventory.java): resizable
  (`setSizeInventory`, `setInventoryStackLimit`, used by the request table), grows on NBT load (see above), null-safe
  `ItemIdentifier` checks in the add helpers, item dropping delegated to `ItemIdentifierInventory.dropItems`.
- [DummyContainer](../../src/main/java/logisticspipes/utils/gui/DummyContainer.java): `addFluidSlot(slotId, x, y)`
  overload on the dummy inventory (no callers).
- [TestTile](../../src/main/java/logisticspipes/gui/modularUI/test/TestTile.java): a copy of MUI's demo tile entity
  (many widgets, multiple windows). Not registered or referenced anywhere.

### Internal MUI reference docs (`.claude/modularUI-docs`)

Reference written for working on MUI GUIs: `public-api.md` (condensed index of the MUI API a mod calls), `internal-docs/*.md`
(per-package reference of ModularUI2: api, drawable, factory, network, screen, value/sync, widget(s), utils, theme,
...), and `migration-status.md` (which module GUIs are migrated, last verified 2026-08-06, plus the effect of the
2026-09-29 design decisions). `.claude/CHASSIS_GUI_HANDOFF.md` is the session handoff for the chassis GUI rewrite.

## Changes to upstream classes

| Class | Change | Why |
|---|---|---|
| `ItemModule` | implements `IGuiHolder`, `buildUI` for in-hand MUI, `openConfigGui` branches on `IMUICompatibleModule` | in-hand module MUI |
| `ItemUpgrade` | new upgrade ids 27, 45-48 | instant satellite (01), request table upgrades (02) |
| `PipeLogisticsChassi` | `IMUICompatiblePipeV2`, `getPipeGui()` → `ChassisGui`; unused `blockingMode` field | chassis MUI |
| `PipeLogisticsChassiMk5` | blank line only | - |
| 10 module classes | implement `IMUICompatibleModule` + small accessors | module MUIs |
| `ModuleModBasedItemSink`, `ModuleCreativeTabBasedItemSink` | drop `IStringBasedModule`, HUD and `listChanged`; add list editing API | replaced string-based legacy GUI |
| `ChassiModule`, `ModuleElectricManager` | `IModuleInventoryOverride` support | GT battery slots (06) |
| `LogisticsModule` | `getWorld`, `getService`, `insertionFailed` | used by MUIs and room-aware sinks |
| `UpgradeManager`, `ModuleUpgradeManager`, `IPipeUpgradeManager`, `ISlotUpgradeManager` | `getUpgradeInventory()`, module slots 2→4, instant satellite flag | upgrade sidebar, roadmap 2a, 01 |
| `ModularUIHelper` | `openModuleUI`, `TAB_RIGHT_TEXTURE` | module MUI opening |
| `GuiHandler`, `GuiIDs`, `NewGuiHandler`, `PacketHandler` | request table GUI id, fluid basic GUI removed, safer class scans | 02, MUI basic fluid pipe, dedicated server |
| `DebugGuiController`, `DebugDataPacket`, `DebugPanelOpen` | id handling, queued client data, cleanup, compression flag | working debug GUI |
| `PlayerConfig`, `GuiLogisticsSettings` | renderer options removed | renderer cleanup |
| `SimpleStackInventory`, `DummyContainer` | resize/NBT growth, null checks; fluid slot overload | request table, MUI slot fix |
| `ModuleExtractor`, `ModuleQuickSort`, `ModuleCCBasedQuickSort` | empty-inventory early return; `InterestRegistry` | perf, router rework (03) |

## Known gaps / discrepancies

- **Non-MUI modules in a chassis can't be configured with a normal wrench.** The chassis MUI only shows "Module not
  compatible with MUI yet" for them (extractors, OreDict/type-filter/apiarist/thaumic sinks, quicksort, fluid
  supplier, ...). The options are configuring the module item in hand (legacy GUI) or the Legacy Wrench.
- **Per-module upgrades are not in the chassis MUI.** Its upgrade column is the pipe's `UpgradeManager`, not the
  `ModuleUpgradeManager`s. Those grew to 4 slots but the legacy chassis GUI still only shows 2 of them, so slots 3-4
  can't be filled from any GUI. `ModuleUpgradeManager.getUpgradeInventory()` is unused.
- **Upgrade sidebar shows 4 of the pipe's 9 upgrade slots.** Upgrades already in slots 4-8 of an old chassis keep
  working but aren't visible there. The slot filter accepts any `ItemUpgrade`; it doesn't check `isAllowedForPipe`.
- **Upgrade column disabled in all other pipe MUIs** (`GenericPipeLogisticsGui`, `GenericSimplePipeLogisticsGui`), so
  the design goal "upgrades insertable through the side upgrade GUI without a pipe controller" is only met for the
  chassis and the pattern crafting table.
- **Basic logistics pipe (item sink) still opens the legacy GUI** with a wrench: the pipe doesn't implement
  `IMUICompatiblePipeV2`. The item sink MUI is only used in hand and in a chassis.
- **Legacy string-based GUIs broken for mod/creative-tab sinks.** `getPipeGuiProvider`/`getInHandGuiProvider` still
  return the `StringBasedItemSinkModule*` providers, which look the module up as `IStringBasedModule`; the modules no
  longer implement it. The in-hand path doesn't reach them (MUI wins), but the legacy chassis GUI (Legacy Wrench)
  does. The two modules also lost their chassis HUD.
- **Legacy Wrench on a basic fluid pipe opens nothing**: `PipeFluidBasic.onWrenchClicked` still opens
  `GUI_Fluid_Basic_ID`, which `GuiHandler` no longer handles.
- **Fluid Supplier Mk2 edits may not reach the server.** Upstream's amount, partial and refill sync values used
  `.allowC2S()`; the moved code in `PipeFluidSupplierMk2Mui` dropped it (the code's own comment in `PipeSatelliteMui`
  says value sync handlers reject client edits without it). Not tested in game.
- **Item sink Import button** runs `importFromInventory()` in the client widget, with no sync handler. It is not
  shown that the imported filter reaches the server. The NPE from the handoff (`getPointedInventory` with a null
  pointed orientation) is still possible: `CoreRoutedPipe.getPointedInventory(boolean)` has no null check.
- **`PipeFluidBasicMui.getId()` returns `"pipe_satellite"`** (copy-paste; same id as `PipeSatelliteMui`).
- `PipeFluidSupplierMk2Mui.addWidgets` returns `null` (harmless; the wrapper ignores the return value).
- `GenericPipeLogisticsGui` creates a new module MUI object on every `getId`/`getWidth`/`getHeight`/`addWidgets` call.
- Unused code: `ModuleCraftingMuiDynamic`, `LogisticsModuleData`, `TestTile`, `ModularUIHelper.TAB_RIGHT_TEXTURE` /
  `verifyServerSide`, `PacketGuards.canConfigurePipe`, `DummyContainer.addFluidSlot(int,int,int)`, the old
  `IMUICompatiblePipe` path, and textures `module_slot_2.png`, `module_slot_3.png`, `gui_tab_right.png`.
- `migration-status.md` is out of date: it lists `ModuleCreativeTabBasedItemSink` as not migrated (it is), and the
  handoff talks of 8 MUI modules (there are 10).
- `.claude/CHASSIS_GUI_HANDOFF.md` says the chassis fix "has not yet been re-tested in runClient" and calls the legacy
  chassis GUI dead code; it is still reachable with the Legacy Wrench.
- The roadmap item "Legacy wrench item" is still unchecked although the item exists.

## Files

### Docs (`.claude/`)

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| `.claude/CHASSIS_GUI_HANDOFF.md` | A | +180/-0 | Session handoff for the chassis MUI rewrite: root causes, prefix change, crash fix, open items |
| `.claude/modularUI-docs/public-api.md` | A | +346/-0 | Condensed index of the ModularUI2 API used by mods |
| `.claude/modularUI-docs/migration-status.md` | A | +54/-0 | Which module GUIs are on MUI (verified 2026-08-06) and effect of the design decisions |
| `.claude/modularUI-docs/internal-docs/*.md` (21 files: animation, api-core, api-drawable-value, config, core, drawable-core, drawable-text, factory, holoui, integration, network, overlay, root, screen, theme, utils-core, utils-sub, value, widget, widgets-core, widgets-sub) | A | +16 306/-0 total | Per-package reference of the ModularUI2 library |

### `logisticspipes.api`

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [IMUICompatibleModule](../../src/main/java/logisticspipes/api/IMUICompatibleModule.java) | A | +24/-0 | Module MUI contract: hand GUI, pipe GUI (with prefix), open from main hand |
| [IMUICompatiblePipeV2](../../src/main/java/logisticspipes/api/IMUICompatiblePipeV2.java) | A | +16/-0 | Pipe MUI contract: `getPipeGui()` returning a `LogisticsModularUI` |

### `logisticspipes.gui.modularUI`

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [ChassisGui](../../src/main/java/logisticspipes/gui/modularUI/ChassisGui.java) | A | +370/-0 | Chassis MUI: module tabs with slots, live per-slot module pages, upgrade column, save on close |
| [DraggableFlow](../../src/main/java/logisticspipes/gui/modularUI/DraggableFlow.java) | A | +110/-0 | Draggable `Flow` container (used by the upgrade sidebar) |
| [GenericModuleMUI](../../src/main/java/logisticspipes/gui/modularUI/GenericModuleMUI.java) | A | +17/-0 | Base class for module MUIs, holds the typed module |
| [GenericPipeLogisticsGui](../../src/main/java/logisticspipes/gui/modularUI/GenericPipeLogisticsGui.java) | A | +93/-0 | Wraps a module MUI as a pipe GUI; upgrade column disabled |
| [GenericSimplePipeLogisticsGui](../../src/main/java/logisticspipes/gui/modularUI/GenericSimplePipeLogisticsGui.java) | A | +91/-0 | Wraps a `LogisticsPipeMUI` as a pipe GUI; upgrade column disabled |
| [LogisticsModularUI](../../src/main/java/logisticspipes/gui/modularUI/LogisticsModularUI.java) | A | +55/-0 | Base of all LP MUIs: id, size, prefix, `addWidgets`, `getPanel` |
| [LogisticsModuleData](../../src/main/java/logisticspipes/gui/modularUI/LogisticsModuleData.java) | A | +21/-0 | `GuiData` carrying a module; unused |
| [LogisticsPipeMUI](../../src/main/java/logisticspipes/gui/modularUI/LogisticsPipeMUI.java) | A | +21/-0 | Base class for pipe MUIs, holds the pipe |
| [PipeGuiFactory](../../src/main/java/logisticspipes/gui/modularUI/PipeGuiFactory.java) | A | +107/-0 | `fromModule`/`fromMui` wrappers and the draggable 4-slot upgrade sidebar |
| [SimpleInventorySlot](../../src/main/java/logisticspipes/gui/modularUI/SimpleInventorySlot.java) | A | +29/-0 | Slot fix for copy-storing inventories so shift-click doesn't lose items |
| [PatternCraftingTableMui](../../src/main/java/logisticspipes/gui/modularUI/blocks/PatternCraftingTableMui.java) | A | +107/-0 | Pattern crafting table MUI: 3x3 input, 3 outputs, speed upgrades (01) |
| [ModuleActiveSupplierMuiDynamic](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleActiveSupplierMuiDynamic.java) | A | +118/-0 | Active supplier MUI: 3x3 stock slots, request mode |
| [ModuleBeeAnalyzerMuiDynamic](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleBeeAnalyzerMuiDynamic.java) | A | +61/-0 | Apiarist analyser MUI: extract on/off |
| [ModuleCraftingMuiDynamic](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleCraftingMuiDynamic.java) | A | +185/-0 | Crafting module MUI; no callers (module deprecated) |
| [ModuleCreativeTabBasedItemSinkMuiDynamic](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleCreativeTabBasedItemSinkMuiDynamic.java) | A | +156/-0 | Creative tab sink MUI: analyse slot, add, 9-entry list with remove |
| [ModuleElectricManagerMuiDynamic](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleElectricManagerMuiDynamic.java) | A | +100/-0 | Electric manager MUI: filter slots, charge/discharge |
| [ModuleEnchantmentSinkMK2MuiDynamic](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleEnchantmentSinkMK2MuiDynamic.java) | A | +85/-0 | Enchantment sink MK2 MUI: filter slots |
| [ModuleItemSinkMuiDynamic](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleItemSinkMuiDynamic.java) | A | +110/-0 | Item sink MUI: 9 filter slots, import, default route |
| [ModuleModBasedItemSinkMuiDynamic](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleModBasedItemSinkMuiDynamic.java) | A | +156/-0 | Mod sink MUI: analyse slot, add, 9-entry list with remove |
| [ModulePassiveSupplierMuiDynamic](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModulePassiveSupplierMuiDynamic.java) | A | +85/-0 | Passive supplier MUI: filter slots |
| [ModuleProviderMuiDynamic](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleProviderMuiDynamic.java) | A | +157/-0 | Provider MUI: 3x3 filter, extraction mode, whitelist/blacklist |
| [ModuleTerminusMuiDynamic](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleTerminusMuiDynamic.java) | A | +85/-0 | Terminus MUI: filter slots |
| [PipeFluidBasicMui](../../src/main/java/logisticspipes/gui/modularUI/pipes/PipeFluidBasicMui.java) | A | +52/-0 | Basic fluid pipe MUI: phantom fluid filter slot |
| [PipeFluidSupplierMk2Mui](../../src/main/java/logisticspipes/gui/modularUI/pipes/PipeFluidSupplierMk2Mui.java) | A | +135/-0 | Fluid supplier Mk2 MUI moved out of the pipe class |
| [PipeSatelliteMui](../../src/main/java/logisticspipes/gui/modularUI/pipes/PipeSatelliteMui.java) | A | +80/-0 | Satellite MUI: id, next free, pattern satellite name |
| [TestTile](../../src/main/java/logisticspipes/gui/modularUI/test/TestTile.java) | A | +554/-0 | Copy of MUI's demo tile; unreferenced |

### `logisticspipes.gui`, `logisticspipes.config`, `logisticspipes.compat`, `logisticspipes.commands`

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [GuiLogisticsSettings](../../src/main/java/logisticspipes/gui/GuiLogisticsSettings.java) | M | +4/-31 | Removes VBO, fallback renderer and render distance options |
| [PlayerConfig](../../src/main/java/logisticspipes/config/PlayerConfig.java) | M | +0/-39 | Drops the same three settings from fields, network data and NBT |
| [ModularUIHelper](../../src/main/java/logisticspipes/compat/ModularUIHelper.java) | M | +17/-0 | `openModuleUI` (from main hand), right-tab texture |
| [DebugGuiController](../../src/main/java/logisticspipes/commands/commands/debug/DebugGuiController.java) | M | +155/-26 | Per-watch ids, queued client data, per-player/world cleanup, bounds checks |

### `logisticspipes.interfaces`

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [IModuleInventory](../../src/main/java/logisticspipes/interfaces/IModuleInventory.java) | A | +10/-0 | Module-owned inventory view (inventory util + transactor) |
| [IModuleInventoryOverride](../../src/main/java/logisticspipes/interfaces/IModuleInventoryOverride.java) | A | +13/-0 | Module provides its own inventory view (GT battery slots) |
| [IPipeUpgradeManager](../../src/main/java/logisticspipes/interfaces/IPipeUpgradeManager.java) | M | +4/-0 | `getUpgradeInventory()` for MUI slots |
| [ISlotUpgradeManager](../../src/main/java/logisticspipes/interfaces/ISlotUpgradeManager.java) | M | +2/-0 | `hasInstantSatelliteUpgrade()` |

### `logisticspipes.items`

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [ItemLegacyWrench](../../src/main/java/logisticspipes/items/ItemLegacyWrench.java) | A | +29/-0 | Debug wrench that opens the legacy GUI path |
| [ItemModule](../../src/main/java/logisticspipes/items/ItemModule.java) | M | +58/-7 | In-hand MUI (`IGuiHolder.buildUI`), MUI branch in `openConfigGui` |
| [ItemUpgrade](../../src/main/java/logisticspipes/items/ItemUpgrade.java) | M | +15/-0 | Registers instant satellite and request table upgrades |

### `logisticspipes.modules`

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [ChassiModule](../../src/main/java/logisticspipes/modules/ChassiModule.java) | M | +25/-4 | Uses sub-module inventory override for room check; forwards `insertionFailed` |
| [ModuleActiveSupplier](../../src/main/java/logisticspipes/modules/ModuleActiveSupplier.java) | M | +24/-4 | MUI; dummy inventory limit raised to `Integer.MAX_VALUE` |
| [ModuleApiaristAnalyser](../../src/main/java/logisticspipes/modules/ModuleApiaristAnalyser.java) | M | +28/-1 | MUI; boolean extract mode accessors |
| [ModuleCCBasedQuickSort](../../src/main/java/logisticspipes/modules/ModuleCCBasedQuickSort.java) | M | +3/-2 | Uses `InterestRegistry` (03) |
| [ModuleCreativeTabBasedItemSink](../../src/main/java/logisticspipes/modules/ModuleCreativeTabBasedItemSink.java) | M | +39/-49 | MUI; tab list API, drops string-based interface and HUD |
| [ModuleElectricManager](../../src/main/java/logisticspipes/modules/ModuleElectricManager.java) | M | +50/-5 | MUI; GT battery slot inventory override |
| [ModuleEnchantmentSinkMK2](../../src/main/java/logisticspipes/modules/ModuleEnchantmentSinkMK2.java) | M | +21/-2 | MUI |
| [ModuleExtractor](../../src/main/java/logisticspipes/modules/ModuleExtractor.java) | M | +7/-6 | Skips empty inventories |
| [ModuleItemSink](../../src/main/java/logisticspipes/modules/ModuleItemSink.java) | M | +150/-17 | MUI; room-aware sinking and interests |
| [ModuleModBasedItemSink](../../src/main/java/logisticspipes/modules/ModuleModBasedItemSink.java) | M | +35/-43 | MUI; mod list API, drops string-based interface and HUD |
| [ModulePassiveSupplier](../../src/main/java/logisticspipes/modules/ModulePassiveSupplier.java) | M | +21/-2 | MUI |
| [ModuleProvider](../../src/main/java/logisticspipes/modules/ModuleProvider.java) | M | +67/-4 | MUI; staged crafting reservations (01) |
| [ModuleQuickSort](../../src/main/java/logisticspipes/modules/ModuleQuickSort.java) | M | +5/-0 | Stalls on empty special inventories |
| [ModuleTerminus](../../src/main/java/logisticspipes/modules/ModuleTerminus.java) | M | +21/-2 | MUI |
| [LogisticsModule](../../src/main/java/logisticspipes/modules/abstractmodules/LogisticsModule.java) | M | +15/-0 | `getWorld`, `getService`, `insertionFailed` hook |

### `logisticspipes.network`

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [GuiHandler](../../src/main/java/logisticspipes/network/GuiHandler.java) | M | +15/-29 | New request table GUI; legacy basic fluid GUI removed |
| [GuiIDs](../../src/main/java/logisticspipes/network/GuiIDs.java) | M | +1/-0 | `GUI_New_Request_Table_ID = 39` |
| [NewGuiHandler](../../src/main/java/logisticspipes/network/NewGuiHandler.java) | M | +18/-1 | Scans `logisticspipes.crafting`; skips non-providers and client-only classes on servers |
| [PacketGuards](../../src/main/java/logisticspipes/network/PacketGuards.java) | A | +94/-0 | Server-side packet validation helpers |
| [PacketHandler](../../src/main/java/logisticspipes/network/PacketHandler.java) | M | +6/-0 | Skips non-packets in scan; clear error for unregistered packets |
| [DebugDataPacket](../../src/main/java/logisticspipes/network/packets/debuggui/DebugDataPacket.java) | M | +5/-0 | Now compressed |
| [DebugPanelOpen](../../src/main/java/logisticspipes/network/packets/debuggui/DebugPanelOpen.java) | M | +0/-4 | No longer compressed |

### `logisticspipes.pipes`, `logisticspipes.utils`

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [PipeLogisticsChassi](../../src/main/java/logisticspipes/pipes/PipeLogisticsChassi.java) | M | +10/-1 | `IMUICompatiblePipeV2` → `ChassisGui`; unused `blockingMode` field |
| [PipeLogisticsChassiMk5](../../src/main/java/logisticspipes/pipes/PipeLogisticsChassiMk5.java) | M | +1/-0 | Blank line |
| [ModuleUpgradeManager](../../src/main/java/logisticspipes/pipes/upgrades/ModuleUpgradeManager.java) | M | +14/-2 | 4 module upgrade slots (was 2); MUI inventory wrapper; instant satellite |
| [UpgradeManager](../../src/main/java/logisticspipes/pipes/upgrades/UpgradeManager.java) | M | +17/-0 | MUI inventory wrapper; instant satellite flag |
| [DummyContainer](../../src/main/java/logisticspipes/utils/gui/DummyContainer.java) | M | +4/-0 | `addFluidSlot` overload (unused) |
| [SimpleStackInventory](../../src/main/java/logisticspipes/utils/item/SimpleStackInventory.java) | M | +46/-19 | Resizable, grows on NBT load, null-safe add helpers |

### `network.rs485.debuggui`

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [DebugGuiEntry](../../src/main/java/network/rs485/debuggui/DebugGuiEntry.java) | A | +762/-0 | Debug GUI implementation: reflective snapshots, Swing window |

### Textures (`src/main/resources/assets/logisticspipes/textures/gui/`)

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| `gui_tab_right.png`, `module_slot_1.png`, `module_slot_2.png`, `module_slot_3.png`, `upgrade_slot.png` | A | binary | Right tab (41x45, unused), module slot backgrounds (18x18; only `_1` used), upgrade slot background (18x18) |
