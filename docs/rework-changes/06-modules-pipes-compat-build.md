# 06: Pipes, GT/IC2 compat, mod init, build and docs

This doc covers everything in the `crafting_rework` branch that isn't one of the big features: behaviour changes to
existing pipes, the new GregTech electric-item proxy, inventory handler changes, proxy and mod-init wiring, the solid
block, config, recipes, lang, the build and the project docs folder.

All diffs are against `GTNH-origin/master` (`git diff GTNH-origin/master HEAD -- <path>`).

Several files here also register things for other features. Where that happens, this doc names the registration
briefly and links the doc that describes the feature:

- [01-pattern-crafting.md](01-pattern-crafting.md): pattern crafting pipe, pattern satellites, pattern item, memory
  chip, pattern crafting table, crafting monitor, instant satellite upgrade
- [02-request-table.md](02-request-table.md): new request table (`RequestTablePipe`, `RequestTableGui`)
- [03-router-rework.md](03-router-rework.md): `JunctionRouterManager`, `LPJunctionNetwork`, `JunctionRoutingThread`
- [04-item-transport.md](04-item-transport.md): clump transport (`ClumpTransit`, clump config)
- [05-modularui-gui.md](05-modularui-gui.md): `IMUICompatiblePipeV2`, `PipeGuiFactory`, the pipe MUIs

---

# Features

## Provider pipe: settings moved into a `ModuleProvider`

**For the player:** the provider pipe (Mk1 and Mk2) opens the provider module's ModularUI with the wrench instead of
the old `GuiProviderPipe`. Filter, include/exclude and extraction mode behave as before. Provider stock that a staged
crafting tree has reserved is no longer offered to other requests.

**How it works** ([PipeItemsProviderLogistics](../../src/main/java/logisticspipes/pipes/PipeItemsProviderLogistics.java)):

- Both constructors now create a `ModuleProvider` (`myModule`) and register the pipe as its handler. The pipe's own
  fields `providingInventory`, `_filterIsExclude` and `_extractionMode` are gone. `getprovidingInventory()`,
  `hasFilter()`, `itemIsFiltered()`, `isExcludeFilter()` and `getExtractionMode()` read from the module.
- `setFilterExcluded()`, `setExtractionMode()` and `nextExtractionMode()` are kept but `@Deprecated` and are now
  **no-ops** ("replaced by modular ui handlers"). The MUI changes the module directly.
- `onWrenchClicked()` (which opened `GUI_ProviderPipe_ID` and sent `ProviderPipeMode`/`ProviderPipeInclude`) is
  removed. The pipe implements `IMUICompatiblePipeV2`; `getPipeGui()` returns `PipeGuiFactory.fromModule(this, myModule)`
  (see [05-modularui-gui.md](05-modularui-gui.md)).
- Implements `IStagedProviderReservation`: `reserveStagedCrafting()` / `releaseStagedCrafting()` keep a per-item
  `stagedCraftingReservations` map. Reserved amounts are subtracted in `itemCount()` and in the provided-items list;
  `fullFill()` releases the reservation before the real order is added. The staged crafting side is in
  [01-pattern-crafting.md](01-pattern-crafting.md).

**Save compatibility:** the pipe's NBT is now `myModule.readFromNBT/writeToNBT`. `ModuleProvider` uses the same keys
the pipe used (`items` with an empty prefix, `filterisexclude`, `extractionMode`) and the same 9-slot, stack-size-1
filter inventory, so old provider pipes load with their settings. The module adds `isActive` and
`sneakydirection`, which default to `false` / unknown on old saves.

## Fluid basic pipe: fluid filter is a phantom tank

**For the player:** the fluid basic pipe gets an MUI (`PipeFluidBasicMui`) with a fluid filter slot instead of the old
item-slot GUI.

**How it works** ([PipeFluidBasic](../../src/main/java/logisticspipes/pipes/PipeFluidBasic.java)):
`filterInv` (`ItemIdentifierInventory` holding a fluid-container item) is replaced by `filterTank`
(`FluidTank(1)`). `sinkAmount()` returns 0 unless `filterTank`'s fluid `isFluidEqual` to the offered stack. New
`getFluidName()` for the GUI. Implements `IMUICompatiblePipeV2`; `onWrenchClicked()` still opens the old
`GUI_Fluid_Basic_ID`, which is only reached with the Legacy Wrench.

**Save compatibility: not migrated.** `readFromNBT` now calls `filterTank.readFromNBT(nbt)` (keys `FluidName` /
`Amount` / `Empty`), and the old `items` list is ignored. Old fluid basic pipes lose their filter and stop sinking until
reconfigured. `PipeFluidSupplierMk2` already does this migration with
`LegacyHelper.readItemIdentifierInventoryAndConvertToTank` when `items` is present; the basic pipe doesn't. See Known
gaps.

## Fluid supplier Mk2: GUI moved out of the pipe

[PipeFluidSupplierMk2](../../src/main/java/logisticspipes/pipes/PipeFluidSupplierMk2.java) drops its inline
`IMUICompatiblePipe.addUIWidgets` (about 110 lines of MUI layout) and its `onWrenchClicked`. It implements
`IMUICompatiblePipeV2` and returns `PipeFluidSupplierMk2Mui`. `phantomTank`, `amount`, `requestPartials` and
`refillThreshold` became `public` so the MUI class can bind them. NBT reading is unchanged, including the existing
legacy `items` / `_bucketMinimum` migration. Bug B15 in [bug-list.md](../bug-list.md) ("Some MUIs drop client edits")
reports that this MUI errors on edit.

## Fluid provider: orders carry their target information

[PipeFluidProvider](../../src/main/java/logisticspipes/pipes/PipeFluidProvider.java) now calls
`item.setAdditionalTargetInformation(order.getInformation())` on the fluid containers it sends, in both send paths.
Before, the information was dropped, so the receiver couldn't tell which order or satellite slot a delivery belonged
to. Pattern crafting relies on this for fluid inputs ([01-pattern-crafting.md](01-pattern-crafting.md)).

## Basic logistics pipe: no random exits, interest fixes

[PipeItemsBasicLogistics](../../src/main/java/logisticspipes/pipes/PipeItemsBasicLogistics.java):

- New `getTransportLayer()` override: a `PipeTransportLayer` whose `stillWantItem()` asks the item sink module whether
  it still sinks the item. Items arriving when the inventory is missing or full are routed elsewhere instead of
  leaving through a random exit; with nowhere to go they wait in the pipe buffer and are dropped after a few retries
  (per the method's Javadoc; transport and rerouting are covered in [04-item-transport.md](04-item-transport.md)).
- `getSpecificInterests()` returns the module's interests (or null) without the old "default route ⇒ null" shortcut,
  and `hasGenericInterests()` delegates to `itemSinkModule.hasGenericInterests()` instead of `isDefaultRoute()`.

Related bugs: B2 and the bounced-items entries in [bug-list.md](../bug-list.md).

## Supplier and apiarist analyser pipes: module MUI

[PipeItemsSupplierLogistics](../../src/main/java/logisticspipes/pipes/PipeItemsSupplierLogistics.java) and
[PipeItemsApiaristAnalyser](../../src/main/java/logisticspipes/pipes/PipeItemsApiaristAnalyser.java) implement
`IMUICompatiblePipeV2` and return `PipeGuiFactory.fromModule(this, <their module>)`. No behaviour or NBT change.

## GregTech electric items and battery slots (electric manager)

**For the player:** with GregTech loaded, the electric manager module recognises GT electric items (GT `MetaBaseItem`
batteries and tools), and can put batteries into and take them out of GT battery slots that GT's sided inventory
normally hides: battery buffers, chargers, Tesla coils, the battery slot of electric single-block machines, and battery
hatches and other tiles that report charger/decharger slots. Other modules (extractor, provider, quicksort) still see
only GT's normal sided inventory.

**How it works:**

- [IIC2Proxy](../../src/main/java/logisticspipes/proxy/interfaces/IIC2Proxy.java) gets
  `IModuleInventory getElectricItemInventory(TileEntity, ForgeDirection)` (null = use the normal inventory).
  [IC2Proxy](../../src/main/java/logisticspipes/proxy/ic2/IC2Proxy.java) and both dummy proxies return null.
  `ModuleElectricManager` calls it through `SimpleServiceLocator.IC2Proxy`.
- [ProxyManager](../../src/main/java/logisticspipes/proxy/ProxyManager.java): if `LogisticsPipes.isGregTech`, the
  electric item proxy is a wrapped [GTNHProxy](../../src/main/java/logisticspipes/proxy/gtnh/GTNHProxy.java) (mod id
  `gregtech`) with a no-op dummy; otherwise the old `IC2Proxy` wiring is used.
- `GTNHProxy` wraps an `IC2Proxy` and delegates everything except the item checks. For `MetaBaseItem`:
  electric = `getMaxCharge > 0`; fully charged = `charge == maxCharge`; fully discharged = `charge < tier`;
  partially charged = `charge > 0`; similar = IC2's check or same `Item`. `hasIC2()` always returns true.
- [GTBatterySlotInventory](../../src/main/java/logisticspipes/proxy/gtnh/GTBatterySlotInventory.java) is an
  `IModuleInventory` over a GT tile's battery slots only. Battery buffers: every slot, insertion through GT's own
  `canInsertItem` (one battery of the buffer's tier per empty slot). `MTEBasicMachine`: the single recharger slot,
  none for steam machines. Other `MetaTileEntity`s: the recharger and decharger slot ranges. Outside buffers a slot
  only accepts an IC2 electric item whose tier is at most the machine's input/output tier. Items are only put into
  empty slots; extraction scans every slot, so a battery behind an empty slot is still found.

Testing list: [testing-checklist.md](../testing-checklist.md) §9.

## Inventory handlers: `IInventoryUtil.isEmpty()`

[IInventoryUtil](../../src/main/java/logisticspipes/interfaces/IInventoryUtil.java) gains `boolean isEmpty()`, used for
early returns (for example `ModuleExtractor` returns when its target is empty).

- [InventoryUtil](../../src/main/java/logisticspipes/utils/InventoryUtil.java): scans every raw slot.
- [SpecialInventoryHandler](../../src/main/java/logisticspipes/proxy/specialinventoryhandler/SpecialInventoryHandler.java):
  default `getItemsAndCount().isEmpty()`.
- [AEInterfaceInventoryHandler](../../src/main/java/logisticspipes/proxy/specialinventoryhandler/AEInterfaceInventoryHandler.java):
  builds the slot cache (`initCache()`) and checks it, so the slot scan that usually follows reuses the cache.
- [StorageDrawersInventoryHandler](../../src/main/java/logisticspipes/proxy/specialinventoryhandler/StorageDrawersInventoryHandler.java):
  any enabled drawer with a stored count above 0.
- `DSULikeInventoryHandler` already had a package-private abstract `isEmpty()`; it and its subclasses
  ([DSU](../../src/main/java/logisticspipes/proxy/specialinventoryhandler/DSUInventoryHandler.java),
  [JABBA](../../src/main/java/logisticspipes/proxy/specialinventoryhandler/JABBAInventoryHandler.java),
  [QuantumChest](../../src/main/java/logisticspipes/proxy/specialinventoryhandler/QuantumChestInventoryHandler.java))
  are just made `public` to satisfy the interface. Their meaning (no stored type, ghosts count as non-empty for DSU
  and JABBA) is unchanged.

## BuildCraft render proxy: skip empty TESR passes

[IBCRenderTESR](../../src/main/java/logisticspipes/proxy/buildcraft/subproxies/IBCRenderTESR.java) gets
`hasDynamicContent(pipe)`. [BCRenderTESR](../../src/main/java/logisticspipes/proxy/buildcraft/BCRenderTESR.java) returns
true when the BC tile has any wire or any pluggable with a dynamic renderer; the dummy proxy in `ProxyManager` returns
false. `LogisticsRenderPipe` uses it to skip the dynamic render for pipes with nothing to draw (render-side
performance).

## Mod init, proxies and events

[LogisticsPipes](../../src/main/java/logisticspipes/LogisticsPipes.java):

- Router: `RouterManager` replaced by `JunctionRouterManager`; the `RoutingTableUpdateThread` pool replaced by
  `LPJunctionNetwork.start(MULTI_THREAD_NUMBER, MULTI_THREAD_PRIORITY)`; `LPJunctionNetwork.cleanup()` on server stop
  ([03-router-rework.md](03-router-rework.md)).
- Server stop also clears `ClumpTransit` ([04-item-transport.md](04-item-transport.md)), the pattern satellites and
  `PatternCraftingMonitorRegistry` ([01-pattern-crafting.md](01-pattern-crafting.md)).
- New flags set in `preInit`: `isGregTech` (`gregtech` loaded; used by `ProxyManager`), and `enableVBO` (`gtnhlib`
  loaded and OptiFine absent; not read anywhere yet).
- New items: `LegacyWrenchItem` (`legacyWrench`, [ItemLegacyWrench](../../src/main/java/logisticspipes/items/ItemLegacyWrench.java),
  a BC `IToolWrench` that `CoreRoutedPipe` uses to open the legacy GUI instead of the MUI; not craftable by design),
  `LogisticsPattern` (with `PatternItemRenderer` on the client) and `LogisticsMemoryChip`
  ([01](01-pattern-crafting.md)).
- New pipes: Pattern Crafting / Pattern Satellite / Pattern Fluid Satellite ([01](01-pattern-crafting.md)) and
  "New Request Table" `RequestTablePipe` ([02](02-request-table.md)).

[LogisticsSolidBlock](../../src/main/java/logisticspipes/blocks/LogisticsSolidBlock.java) and
[LogisticsSolidBlockItem](../../src/main/java/logisticspipes/items/LogisticsSolidBlockItem.java): two new metas,
`LOGISTICS_PATTERN_CRAFTING_TABLE = 6` (`PatternLogisticsCraftingTableTileEntity`, autocrafting-table textures) and
`LOGISTICS_CRAFTING_MONITOR = 7` (`CraftingMonitorTileEntity`, statistics-table textures), with names, creative-tab
entries, drop metas and `onBlockBreak` for the pattern table. Right-click opens a ModularUI through
`GuiFactories.tileEntity()` for any tile that is an `IGuiHolder`, before the old `IGuiTileEntity` path. The rest of the
diff only moves `getNewIcon` / `damageDropped` within the file. Existing metas are unchanged, so placed blocks are
unaffected. Both new tile entities are registered in
[ClientProxy](../../src/main/java/logisticspipes/proxy/side/ClientProxy.java) and
[ServerProxy](../../src/main/java/logisticspipes/proxy/side/ServerProxy.java).

`ClientProxy` also routes request answers to the new `RequestTableGui` when it is open (popup if `DISPLAY_POPUP`,
otherwise chat as before), and accepts it as a target for the request result handler ([02](02-request-table.md)).

[MainProxy](../../src/main/java/logisticspipes/proxy/MainProxy.java): `JunctionRoutingThread` counts as server side in
`getEffectiveSide`.

[LogisticsEventListener](../../src/main/java/logisticspipes/LogisticsEventListener.java): new
`ChunkEvent.Unload` handler calling `LPJunctionNetwork.onChunkUnload(dim, cx, cz)` on the server
([03](03-router-rework.md)); `DebugGuiController` client/server debuggers are cleared on world unload, player logout and
client disconnect.

[MainCommandHandler](../../src/main/java/logisticspipes/commands/MainCommandHandler.java) registers two new `/lp`
subcommands: `routingthread-clear` (`rt-clear`, resets `LPJunctionNetwork` routing stats, any player) and
`pipespeed` (`ps`, ops only, shows or sets `LPConstants.PIPE_NORMAL_SPEED` at runtime; not saved).
[LPConstants](../../src/main/java/logisticspipes/LPConstants.java): `PIPE_NORMAL_SPEED` is no longer `final` (for
`/lp pipespeed`), and `DEBUG` gets a Javadoc warning not to hard-code it on.

## Config

[Configs](../../src/main/java/logisticspipes/config/Configs.java), category `general`:

| Key | Default | Meaning |
|---|---|---|
| `itemClumpTransport` | `true` | Routed items hop across LP pipe corridors in clumps instead of moving block by block; `false` restores the old transport. |
| `itemClumpGatherTicks` | `5` (0–40) | Ticks after a clump left a pipe during which an item going the same way may still join it. |

Both belong to [04-item-transport.md](04-item-transport.md). New keys only, so existing config files just gain them.

## Recipes

[RecipeManager](../../src/main/java/logisticspipes/recipes/RecipeManager.java):

- **Crafting Monitor** (`Advanced_Information`): `iDi / rSr / iBi` with iron, redstone, a Statistics Table (S), a
  Pattern Crafting Pipe (B), and either a tier-3 gear or a tier-3 chip as D (two recipes).
- **Instant Satellite upgrade** (`ItemUpgrade.INSTANT_SATELLITE`, `Upgrades`): `PeP / eCe / PeP` with paper, ender
  pearls and a tier-3 chip; a second copy in the other recipe set uses the `expand` part.
- `addOrdererRecipe` passes varargs directly instead of an explicit `Object[]` (same behaviour).

There are no recipes in this file for the pattern crafting pipe, pattern satellites, pattern table, pattern item,
memory chip or the new request table (see Known gaps).

## Lang, tooltips and textures

[en_US.lang](../../src/main/resources/assets/logisticspipes/lang/en_US.lang): names and tooltips for the pattern
crafting pipe and satellites, `RequestTablePipe` ("Logistics Request Table Mk2"), the pattern item, Legacy Wrench, the
instant satellite and request-table upgrades, the pattern crafting table and crafting monitor, and the
`gui.craftingmonitor.*`, `gui.pattern.*`, `gui.patterncraftingtable.*` and `gui.patterncrafting.*` GUI strings. The
three render settings keys `gui.settings.pipevborenderer`, `pipefallbackrenderer` and `piperenderdistance` are removed
(their `PlayerConfig` options are gone).

[tootlips.txt](../../src/main/resources/assets/logisticspipes/lang/tootlips.txt) (sic): a notes file, "Reserved for
future use", with one-line descriptions of the basic pipe, item sink, provider, supplier, passive supplier, crafting
pipe, satellite, polymorphic/tag item sink, extractor, advanced extractor and terminus. Nothing loads it.

`textures/items/legacyWrench.png`: 32x32 icon for the Legacy Wrench.

`StringUtils.addShiftAction(list, action)` runs `action` while Shift is held and otherwise adds the "hold shift" line;
used by pattern tooltips. `ItemStackRenderer` enables `GL_RESCALE_NORMAL` after GUI item lighting is set up, which
fixes lighting on scaled item renders.

## Project docs

- [docs/rework-design-decisions.md](../rework-design-decisions.md): the source of truth for the rework: pipes,
  upgrades, modules, GUI, new pipes, interactions, QoL, debug/legacy, "Compatibility with old bases" and
  clarifications.
- [docs/roadmap.md](../roadmap.md): the work plan for those decisions, by phase (crafting, upgrades and modules, block
  UI and request table, QoL, transport and routing track, GUI/debug, integrations), with difficulty markers and a Done
  list.
- [docs/bug-list.md](../bug-list.md): known bugs with stable IDs (B1, B2, ...), grouped by routing/transport, crafting,
  rendering, performance and pattern crafting found in game, with status and repro steps.
- [docs/testing-checklist.md](../testing-checklist.md): everything written but not yet tested in game, grouped by area
  (clumps, lag fixes, pattern crafting, MUI migration, rendering, particles, routing to full inventories, modules and
  inventory handlers), with the date of the last pass.
- [TODO.md](../../TODO.md) (repo root): two lines, "Fix deprecated MUI references" and "Fix Chassis".

---

# Build & dependencies

**[dependencies.gradle](../../dependencies.gradle)** (all versions are new entries; no existing version changed):

| Change | Dependency |
|---|---|
| added `compileOnly` | `com.github.GTNewHorizons:NotEnoughEnergistics:1.7.32` (`transitive = false`) |
| added `runtimeOnlyNonPublishable` | `com.github.GTNewHorizons:GT5-Unofficial:5.09.54.86:dev` (already `compileOnly`; now also on the dev runtime classpath) |
| added `runtimeOnlyNonPublishable` | `com.github.GTNewHorizons:Applied-Energistics-2-Unofficial:rv3-beta-999-GTNH:api` |
| commented out | `runtimeOnlyNonPublishable('com.github.GTNewHorizons:NewHorizonsCoreMod:2.8.290:dev')` |
| whitespace | `junit-platform-launcher` line re-indented (tab to spaces) |

**Gradle and buildscript:**

| File | Upstream | Branch |
|---|---|---|
| [gradle/wrapper/gradle-wrapper.properties](../../gradle/wrapper/gradle-wrapper.properties) | Gradle 9.4.0 | Gradle 9.7.1 |
| [settings.gradle](../../settings.gradle) | `gtnhsettingsconvention` 2.0.29 | 2.0.32 |
| [gradle/gradle-daemon-jvm.properties](../../gradle/gradle-daemon-jvm.properties) | foojay toolchain URLs | all 10 toolchain download URLs regenerated (toolchain version line unchanged) |
| [gradle.properties](../../gradle.properties) | n/a | adds `curseForgeEnvironments = client,server` with its template comment |

**CI** ([.github/workflows/build-artifact.yml](../../.github/workflows/build-artifact.yml), new): "Build artifact" runs
on every push to any branch, on pull requests and on manual dispatch. On `ubuntu-slim` with Corretto 25 (Gradle cache
on), it runs `./gradlew spotlessApply`, then `./gradlew assemble`, and uploads `build/libs/*.jar` as
`logisticspipes-jars` (fails if no jars). It doesn't run tests or `spotlessCheck`.

---

# Changes to upstream classes

- **Pipes:** provider pipe NBT and settings now live in a `ModuleProvider` (compatible keys); fluid basic filter is a
  `FluidTank` (not migrated); fluid supplier Mk2, supplier, apiarist analyser, provider and fluid basic switched to
  `IMUICompatiblePipeV2`; fluid provider forwards order info; basic pipe gets a `stillWantItem` transport layer and
  interest fixes.
- **Interfaces:** `IInventoryUtil.isEmpty()`, `IIC2Proxy.getElectricItemInventory()`,
  `IBCRenderTESR.hasDynamicContent()` are new abstract methods; every implementation in the repo was updated, but any
  outside implementer (addons) would break.
- **Proxies:** `ProxyManager` picks `GTNHProxy` over `IC2Proxy` when GregTech is loaded; `MainProxy` knows the new
  routing thread; client/server proxies register two new tile entities.
- **Mod init / events / commands:** new router manager and junction network lifecycle, chunk-unload hook, debug GUI
  cleanup, two `/lp` subcommands, mutable pipe speed.
- **Blocks / items / config / recipes / lang:** two solid-block metas, ModularUI tile GUI path, two config keys, crafting
  monitor and instant satellite recipes, new lang strings, three render-setting strings removed.

---

# Known gaps / discrepancies

- **Fluid basic pipe filter is lost on old worlds.** `PipeFluidBasic.readFromNBT` doesn't read the old `items` filter;
  `LegacyHelper.readItemIdentifierInventoryAndConvertToTank` (already used by the fluid supplier Mk2) would cover it.
  This breaks the "saved data needs migrations, not silent drops" rule.
- **Fluid basic legacy GUI is dead.** `onWrenchClicked` still opens `GUI_Fluid_Basic_ID`, but `GuiHandler` no longer has
  a case for it, so the Legacy Wrench opens nothing. `guiOpenedBy` is never filled any more, so the "don't sink while
  the GUI is open" check in `sinkAmount` never triggers.
- **Provider pipe legacy GUI unreachable and inert.** `onWrenchClicked` was removed and `getLogisticsModule()` returns
  null, so the Legacy Wrench path does nothing for provider pipes; `GuiProviderPipe` and `GuiHandler`'s
  `GUI_ProviderPipe_ID` case still exist, but the setters they call are no-ops.
- **`enableVBO` is set but never read.** The `PlayerConfig` VBO / fallback / render-distance options and their lang
  keys were removed.
- **`item.logisticsMemoryChip` has no lang entry** in `en_US.lang` (the pattern item and legacy wrench have theirs).
- **No crafting recipes for most new content:** pattern crafting pipe, pattern satellites, pattern crafting table,
  pattern item, memory chip and new request table have no recipe in `RecipeManager`, but the crafting monitor recipe
  needs a pattern crafting pipe. (The Legacy Wrench is uncraftable on purpose.)
- **`NotEnoughEnergistics` is added as `compileOnly` but nothing in `src/main/java` imports it.**
- **The AE2 runtime dependency is the `:api` artifact**, not a full dev jar, so it probably doesn't run AE2 in the dev
  client.
- **CI runs `spotlessApply`, not `spotlessCheck`,** so unformatted code passes. The new GregTech block in
  `ProxyManager` (`if(LogisticsPipes.isGregTech){ ... } else{`) isn't spotless-formatted in the repo.
- **`GTNHProxy` assumes IC2 is present:** its constructor creates an `IC2Proxy` and `hasIC2()` always returns true.
  This holds in GTNH but not for GregTech without IC2. `isFullyCharged` doesn't null-check the stack (`isElectricItem`
  does). "Fully discharged" for GT items is the heuristic `charge < tier`.
- `tootlips.txt` is misspelled and unused.

---

# Files

### Root, build and CI

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [.github/workflows/build-artifact.yml](../../.github/workflows/build-artifact.yml) | A | +35/-0 | CI: spotlessApply + assemble on Corretto 25, uploads jars |
| [TODO.md](../../TODO.md) | A | +2/-0 | Two open items: deprecated MUI references, chassis |
| [dependencies.gradle](../../dependencies.gradle) | M | +6/-1 | Adds NEE compileOnly, GT5 and AE2-api dev runtime deps |
| [gradle.properties](../../gradle.properties) | M | +5/-0 | Adds `curseForgeEnvironments = client,server` |
| [gradle/gradle-daemon-jvm.properties](../../gradle/gradle-daemon-jvm.properties) | M | +10/-10 | Regenerated foojay toolchain URLs |
| [gradle/wrapper/gradle-wrapper.properties](../../gradle/wrapper/gradle-wrapper.properties) | M | +1/-1 | Gradle 9.4.0 to 9.7.1 |
| [settings.gradle](../../settings.gradle) | M | +1/-1 | gtnhsettingsconvention 2.0.29 to 2.0.32 |

### docs/

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [docs/bug-list.md](../bug-list.md) | A | +372/-0 | Known bugs with IDs, status and repro steps |
| [docs/rework-design-decisions.md](../rework-design-decisions.md) | A | +104/-0 | Rework design decisions, incl. old-base compatibility rules |
| [docs/roadmap.md](../roadmap.md) | A | +187/-0 | Phased work plan for the decisions |
| [docs/testing-checklist.md](../testing-checklist.md) | A | +194/-0 | Untested changes to verify in game, by area |

### logisticspipes

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [LPConstants](../../src/main/java/logisticspipes/LPConstants.java) | M | +6/-1 | `PIPE_NORMAL_SPEED` mutable; `DEBUG` Javadoc warning |
| [LogisticsEventListener](../../src/main/java/logisticspipes/LogisticsEventListener.java) | M | +18/-0 | Chunk-unload hook for junction network; debug GUI cleanup |
| [LogisticsPipes](../../src/main/java/logisticspipes/LogisticsPipes.java) | M | +69/-5 | Junction router init, new items/pipes, `isGregTech`/`enableVBO`, cleanup |

### logisticspipes.blocks / items / interfaces / commands / config / recipes

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [blocks/LogisticsSolidBlock](../../src/main/java/logisticspipes/blocks/LogisticsSolidBlock.java) | M | +86/-51 | Metas 6 (pattern table) and 7 (crafting monitor); MUI tile GUI path |
| [items/LogisticsSolidBlockItem](../../src/main/java/logisticspipes/items/LogisticsSolidBlockItem.java) | M | +6/-0 | Names and creative entries for the two new metas |
| [interfaces/IInventoryUtil](../../src/main/java/logisticspipes/interfaces/IInventoryUtil.java) | M | +2/-0 | New `isEmpty()` |
| [commands/MainCommandHandler](../../src/main/java/logisticspipes/commands/MainCommandHandler.java) | M | +4/-0 | Registers `/lp rt-clear` and `/lp pipespeed` |
| [config/Configs](../../src/main/java/logisticspipes/config/Configs.java) | M | +18/-0 | `itemClumpTransport` and `itemClumpGatherTicks` options |
| [recipes/RecipeManager](../../src/main/java/logisticspipes/recipes/RecipeManager.java) | M | +67/-1 | Crafting monitor and instant satellite upgrade recipes |

### logisticspipes.pipes

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [PipeFluidBasic](../../src/main/java/logisticspipes/pipes/PipeFluidBasic.java) | M | +21/-11 | Filter is a `FluidTank`, MUI GUI; old filter NBT not migrated |
| [PipeFluidProvider](../../src/main/java/logisticspipes/pipes/PipeFluidProvider.java) | M | +2/-0 | Forwards order target information on sent fluids |
| [PipeFluidSupplierMk2](../../src/main/java/logisticspipes/pipes/PipeFluidSupplierMk2.java) | M | +13/-133 | Inline MUI moved to `PipeFluidSupplierMk2Mui`; fields public |
| [PipeItemsApiaristAnalyser](../../src/main/java/logisticspipes/pipes/PipeItemsApiaristAnalyser.java) | M | +9/-1 | Module MUI via `IMUICompatiblePipeV2` |
| [PipeItemsBasicLogistics](../../src/main/java/logisticspipes/pipes/PipeItemsBasicLogistics.java) | M | +26/-9 | Reroutes unwanted items; interests delegate to item sink |
| [PipeItemsProviderLogistics](../../src/main/java/logisticspipes/pipes/PipeItemsProviderLogistics.java) | M | +80/-40 | Settings in `ModuleProvider` (NBT-compatible), MUI, staged reservations |
| [PipeItemsSupplierLogistics](../../src/main/java/logisticspipes/pipes/PipeItemsSupplierLogistics.java) | M | +10/-1 | Module MUI via `IMUICompatiblePipeV2` |

### logisticspipes.proxy

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [MainProxy](../../src/main/java/logisticspipes/proxy/MainProxy.java) | M | +2/-0 | `JunctionRoutingThread` treated as server side |
| [ProxyManager](../../src/main/java/logisticspipes/proxy/ProxyManager.java) | M | +83/-0 | `GTNHProxy` as electric proxy when GregTech loaded; new dummy methods |
| [buildcraft/BCRenderTESR](../../src/main/java/logisticspipes/proxy/buildcraft/BCRenderTESR.java) | M | +21/-0 | `hasDynamicContent`: wires or dynamic pluggables present |
| [buildcraft/subproxies/IBCRenderTESR](../../src/main/java/logisticspipes/proxy/buildcraft/subproxies/IBCRenderTESR.java) | M | +3/-0 | New `hasDynamicContent()` |
| [gtnh/GTBatterySlotInventory](../../src/main/java/logisticspipes/proxy/gtnh/GTBatterySlotInventory.java) | A | +246/-0 | View of GT battery slots for the electric manager |
| [gtnh/GTNHProxy](../../src/main/java/logisticspipes/proxy/gtnh/GTNHProxy.java) | A | +131/-0 | GT electric-item checks over IC2; battery slot inventory |
| [ic2/IC2Proxy](../../src/main/java/logisticspipes/proxy/ic2/IC2Proxy.java) | M | +6/-0 | `getElectricItemInventory` returns null |
| [interfaces/IIC2Proxy](../../src/main/java/logisticspipes/proxy/interfaces/IIC2Proxy.java) | M | +10/-0 | New `getElectricItemInventory(tile, side)` |
| [side/ClientProxy](../../src/main/java/logisticspipes/proxy/side/ClientProxy.java) | M | +35/-16 | Registers new tiles; request answers to new request table |
| [side/ServerProxy](../../src/main/java/logisticspipes/proxy/side/ServerProxy.java) | M | +8/-0 | Registers pattern table and crafting monitor tiles |

### logisticspipes.proxy.specialinventoryhandler

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [AEInterfaceInventoryHandler](../../src/main/java/logisticspipes/proxy/specialinventoryhandler/AEInterfaceInventoryHandler.java) | M | +16/-0 | `isEmpty()` via slot cache |
| [DSUInventoryHandler](../../src/main/java/logisticspipes/proxy/specialinventoryhandler/DSUInventoryHandler.java) | M | +1/-1 | `isEmpty()` made public |
| [DSULikeInventoryHandler](../../src/main/java/logisticspipes/proxy/specialinventoryhandler/DSULikeInventoryHandler.java) | M | +1/-1 | Abstract `isEmpty()` made public |
| [JABBAInventoryHandler](../../src/main/java/logisticspipes/proxy/specialinventoryhandler/JABBAInventoryHandler.java) | M | +1/-1 | `isEmpty()` made public |
| [QuantumChestInventoryHandler](../../src/main/java/logisticspipes/proxy/specialinventoryhandler/QuantumChestInventoryHandler.java) | M | +1/-1 | `isEmpty()` made public |
| [SpecialInventoryHandler](../../src/main/java/logisticspipes/proxy/specialinventoryhandler/SpecialInventoryHandler.java) | M | +5/-0 | Default `isEmpty()` from `getItemsAndCount()` |
| [StorageDrawersInventoryHandler](../../src/main/java/logisticspipes/proxy/specialinventoryhandler/StorageDrawersInventoryHandler.java) | M | +15/-0 | `isEmpty()` over enabled drawers |

### logisticspipes.utils

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [InventoryUtil](../../src/main/java/logisticspipes/utils/InventoryUtil.java) | M | +9/-0 | `isEmpty()` slot scan |
| [item/ItemStackRenderer](../../src/main/java/logisticspipes/utils/item/ItemStackRenderer.java) | M | +2/-0 | Enables `GL_RESCALE_NORMAL` for scaled item lighting |
| [string/StringUtils](../../src/main/java/logisticspipes/utils/string/StringUtils.java) | M | +8/-0 | `addShiftAction` tooltip helper |

### Resources

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [lang/en_US.lang](../../src/main/resources/assets/logisticspipes/lang/en_US.lang) | M | +108/-3 | Pattern crafting, monitor, request table, wrench strings; render settings removed |
| [lang/tootlips.txt](../../src/main/resources/assets/logisticspipes/lang/tootlips.txt) | A | +28/-0 | Unused draft of pipe/module descriptions |
| [textures/items/legacyWrench.png](../../src/main/resources/assets/logisticspipes/textures/items/legacyWrench.png) | A | binary | Legacy Wrench item icon (32x32) |
