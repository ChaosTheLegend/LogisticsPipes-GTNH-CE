# GUI migration status (ModularUI2)

Determined from the actual GUI wiring, not from filenames. Something counts as migrated only if it opens an MUI in game:
- **Modules** implement `logisticspipes.api.IMUICompatibleModule`. `ChassisGui` (in pipe) and `ItemModule` (in hand)
  call `getPipeGui()`/`getHandGui()` (a `*MuiDynamic` class in `gui/modularUI/dynamicModules/`), otherwise they fall
  back to the legacy `getPipeGuiProvider()`/`getInHandGuiProvider()` → `gui/modules/Gui*.java`.
- **Pipes** implement `IMUICompatiblePipeV2`. `CoreRoutedPipe.blockActivated` opens the MUI on a wrench click (except
  with `ItemLegacyWrench`). Otherwise it opens the legacy GUI of the pipe's `LogisticsGuiModule`, or calls
  `onWrenchClicked` (legacy `GuiIDs` / `NewGuiHandler` GUIs).
- **Blocks** implement `IGuiHolder`. `LogisticsSolidBlock` opens them through the MUI tile factory, otherwise through
  the legacy `IGuiTileEntity` provider.

Branch `fork/Navatusein/request-table-mui` (not merged into `dev` yet) covers the items marked 🔀.

_Last verified: 2026-10-16, on `dev`._

## Modules

To migrate:
- [ ] `ModuleFluidSupplier` → `gui/modules/GuiFluidSupplier` (not covered by the branch)
- [ ] `ModuleTypeFilterItemSink` (GT) → `GuiStringBasedItemSink`. Quick win: same string-based GUI as the mod-based and
  creative-tab sinks, which already have MUIs
- [ ] `ModuleApiaristSink` → `GuiApiaristSink`
- [ ] `ModuleThaumicAspectSink` → `GuiThaumicAspectSink`
- [ ] `ModuleCCBasedQuickSort` → `GuiCCBasedQuickSort`

Blocked on "Rethink modules" (`docs/roadmap.md` 2c), they may become upgrades:
- [ ] `ModuleExtractor` (+ Mk2/Mk3) → `GuiExtractor`
- [ ] `ModuleAdvancedExtractor` (+ MK2/MK3) → `GuiAdvancedExtractor`
- [ ] `ModuleOreDictItemSink` → `GuiOreDictItemSink`

Don't port the extractors' sneaky direction picker: the setting is removed and replaced by the shared sneaky-faces
setting (`docs/rework-design-decisions.md`, Interactions). New pipe MUIs that touch an inventory need a sneaky-faces
section (enable/disable faces); in a chassis it goes in each module's GUI, since the setting is per module.

Deprecated, keep the legacy GUI (no MUI):
- `ModuleCrafter` (+ MK2/MK3) → `gui/GuiCraftingPipe`. `dynamicModules/ModuleCraftingMuiDynamic` has no callers and can
  be deleted

Migrated:

| Module | MUI |
|---|---|
| `ModuleItemSink` | `ModuleItemSinkMuiDynamic` |
| `ModuleProvider` (+ Mk2) | `ModuleProviderMuiDynamic` |
| `ModuleActiveSupplier` | `ModuleActiveSupplierMuiDynamic` |
| `ModulePassiveSupplier` | `ModulePassiveSupplierMuiDynamic` |
| `ModuleElectricManager` | `ModuleElectricManagerMuiDynamic` |
| `ModuleTerminus` | `ModuleTerminusMuiDynamic` |
| `ModuleEnchantmentSinkMK2` | `ModuleEnchantmentSinkMK2MuiDynamic` |
| `ModuleApiaristAnalyser` | `ModuleBeeAnalyzerMuiDynamic` |
| `ModuleModBasedItemSink` | `ModuleModBasedItemSinkMuiDynamic` |
| `ModuleCreativeTabBasedItemSink` | `ModuleCreativeTabBasedItemSinkMuiDynamic` |

No GUI: Polymorphic item sink, QuickSort, ElectricBuffer, ApiaristRefiller, ApiaristTerminus, CCBasedItemSink,
EnchantmentSink (Mk1), Satelite, PatternCrafting.

`LogisticsSimpleFilterModule` → `GuiSimpleFilter` is only a fallback; every current subclass overrides it with an MUI.

## Pipes

To migrate:
- [ ] `PipeItemsBasicLogistics`. Quick win: its `ModuleItemSink` has an MUI, but the pipe isn't `IMUICompatiblePipeV2`,
  so a wrench click opens the legacy `GuiItemSink`
- [ ] `PipeItemsApiaristSink` → legacy GUI of `ModuleApiaristSink`
- [ ] `PipeItemsFluidSupplier` → `GuiFluidSupplierPipe` 🔀
- [ ] `PipeItemsFirewall` → `GuiFirewall`
- [ ] `PipeItemsInvSysConnector` → `GuiInvSysConnector` (roadmap: re-add and upgrade)
- [ ] `PipeItemsSystemEntranceLogistics` / `PipeItemsSystemDestinationLogistics` → `GuiFreqCardContent`
- [ ] `PipeItemsRequestLogistics` / `PipeItemsRequestLogisticsMk2` → `NormalGuiOrderer` / `NormalMk2GuiOrderer`
  (the remote orderer item opens the same GUI)
- [ ] `PipeFluidRequestLogistics` → `FluidGuiOrderer`
- [ ] `PipeBlockRequestTable` → `GuiRequestTable`; `RequestTablePipe` ("New Request Table") → `RequestTableContainer`
  (`GUI_New_Request_Table_ID`). 🔀 adds a new Mk3 table with an MUI, but both old tables are still legacy there

Deprecated, keep the legacy GUI: `PipeItemsCraftingLogistics` Mk1–3.

Migrated: chassis Mk1–5, `PipeItemsProviderLogistics` (+ Mk2), `PipeItemsSupplierLogistics`,
`PipeItemsSatelliteLogistics`, `PipeFluidSatellite`, `PipeItemsPatternCraftingLogistics`, `PipeItemsApiaristAnalyser`,
`PipeFluidBasic`, `PipeFluidSupplierMk2`.

No GUI: `PipeFluidProvider`, `PipeFluidInsertion`, `PipeFluidExtractor`, `PipeItemsRemoteOrdererLogistics`.

## Shared pipe GUIs

- [ ] Network monitor (pipe controller on a pipe) → `GuiPipeController`
- [ ] Item amount sign → `ItemAmountSignCreationGui`
- [ ] Orderer / request table popups (`gui/popup/*`: request, macro, disk, recipe import, monitors), moved together with
  their parent GUIs

## Blocks

To migrate:
- [ ] Logistics crafting table → `GuiLogisticsCraftingTable`
- [ ] Security station → `GuiSecurityStation` (+ `GuiSecurityStationPopup`, `GuiEditCCAccessTable`)
- [ ] Soldering station → `GuiSolderingStation`
- [ ] Power provider (RF/EU) → `GuiPowerProvider`
- [ ] Statistics → `GuiStatistics` (+ `GuiAddTracking`)

Migrated: power junction (`PowerJunctionMui`), pattern crafting table (`PatternCraftingTableMui`).

## Items

- [ ] HUD glasses → `GuiHUDSettings`
- [ ] Pipe controller used in the air → `GuiLogisticsSettings`
- Module items in hand follow the module list above. The pattern item is migrated (`HandheldPatternMui`).

## Dead and broken legacy paths

- [ ] No caller, can be deleted:
  - `UpgradeManager.openGui` → `UpgradeManagerGui` / `GuiUpgradeManager` (MUIs use `PipeGuiFactory.getUpgradeGui`)
  - `GUI_ProviderPipe_ID` → `GuiProviderPipe`
  - `ItemMangerGui` → `GuiCardManager`
  - `ModuleCraftingMuiDynamic` (see modules)
- [ ] `PipeFluidBasic` with the legacy wrench opens `GUI_Fluid_Basic_ID`, which `GuiHandler` doesn't handle, so nothing
  opens. 🔀 deletes `GuiFluidBasic`
- Only reachable with the legacy wrench, kept until legacy GUIs are deleted: `GuiChassiPipe`, `GuiSatellitePipe`,
  `GuiSupplierPipe` (ActiveSupplier fallback)

## Effect of the rework design (2026-09-29)

Per [docs/rework-design-decisions.md](../../docs/rework-design-decisions.md), every GUI moves to ModularUI, but some
modules are deprecated rather than migrated:
- Deprecated modules keep their legacy GUI and get no MUI. They and their GUIs are deleted one major pack version after
  the rework ships (rework in 2.10 → deleted in 2.11), see "Compatibility with old bases" in
  `docs/rework-design-decisions.md`. Don't delete those legacy GUIs before then.
- `ModuleCrafter` is deprecated, because crafting modules are replaced by the pattern crafting pipe.
- Modules that only duplicate another one with more features become upgrades. The design doc names the extractors and
  item sinks as examples.
  - Likely candidates: `ModuleExtractor` Mk2/Mk3 and `ModuleAdvancedExtractor` MK2/MK3.
  - Possibly also the item sink variants such as `ModuleOreDictItemSink`, since the OreDict filter becomes an upgrade.
  - The final list is settled under "Rethink modules" in `docs/roadmap.md` 2c. Wait for it before migrating any of these GUIs.
- Fluid modules (new) for the fluid chassis will need MUI GUIs from the start.
