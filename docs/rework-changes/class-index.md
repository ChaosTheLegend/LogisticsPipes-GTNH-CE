# Changed files index

Every file that differs between `GTNH-origin/master` and `crafting_rework`, grouped by package or folder. Each row links to the feature doc section that explains the change. Generated from `git diff -M --numstat GTNH-origin/master HEAD`; see the [README](README.md) for the feature overview.

Totals: **390 files**: 250 added, 140 modified.

## Packages

- [logisticspipes](#logisticspipes) (3)
- [logisticspipes.api](#logisticspipesapi) (2)
- [logisticspipes.blocks](#logisticspipesblocks) (1)
- [logisticspipes.blocks.powertile](#logisticspipesblockspowertile) (1)
- [logisticspipes.commands](#logisticspipescommands) (1)
- [logisticspipes.commands.commands](#logisticspipescommandscommands) (3)
- [logisticspipes.commands.commands.debug](#logisticspipescommandscommandsdebug) (1)
- [logisticspipes.compat](#logisticspipescompat) (1)
- [logisticspipes.config](#logisticspipesconfig) (2)
- [logisticspipes.crafting](#logisticspipescrafting) (54)
- [logisticspipes.crafting.pattern](#logisticspipescraftingpattern) (9)
- [logisticspipes.crafting.patternStack](#logisticspipescraftingpatternstack) (4)
- [logisticspipes.crafting.requesttable](#logisticspipescraftingrequesttable) (12)
- [logisticspipes.crafting.requesttable.upgrades](#logisticspipescraftingrequesttableupgrades) (5)
- [logisticspipes.gui](#logisticspipesgui) (2)
- [logisticspipes.gui.hud](#logisticspipesguihud) (1)
- [logisticspipes.gui.modularUI](#logisticspipesguimodularui) (10)
- [logisticspipes.gui.modularUI.blocks](#logisticspipesguimodularuiblocks) (1)
- [logisticspipes.gui.modularUI.dynamicModules](#logisticspipesguimodularuidynamicmodules) (11)
- [logisticspipes.gui.modularUI.pipes](#logisticspipesguimodularuipipes) (3)
- [logisticspipes.gui.modularUI.pipes.patterncrafting](#logisticspipesguimodularuipipespatterncrafting) (11)
- [logisticspipes.gui.modularUI.test](#logisticspipesguimodularuitest) (1)
- [logisticspipes.gui.orderer](#logisticspipesguiorderer) (2)
- [logisticspipes.gui.popup](#logisticspipesguipopup) (1)
- [logisticspipes.interfaces](#logisticspipesinterfaces) (5)
- [logisticspipes.items](#logisticspipesitems) (5)
- [logisticspipes.logistics](#logisticspipeslogistics) (3)
- [logisticspipes.modules](#logisticspipesmodules) (15)
- [logisticspipes.modules.abstractmodules](#logisticspipesmodulesabstractmodules) (1)
- [logisticspipes.nei](#logisticspipesnei) (4)
- [logisticspipes.network](#logisticspipesnetwork) (5)
- [logisticspipes.network.packets.crafting.monitor](#logisticspipesnetworkpacketscraftingmonitor) (3)
- [logisticspipes.network.packets.crafting.requesttable](#logisticspipesnetworkpacketscraftingrequesttable) (9)
- [logisticspipes.network.packets.debug](#logisticspipesnetworkpacketsdebug) (2)
- [logisticspipes.network.packets.debuggui](#logisticspipesnetworkpacketsdebuggui) (2)
- [logisticspipes.network.packets.orderer](#logisticspipesnetworkpacketsorderer) (4)
- [logisticspipes.network.packets.pipe](#logisticspipesnetworkpacketspipe) (2)
- [logisticspipes.network.packets.routingdebug](#logisticspipesnetworkpacketsroutingdebug) (1)
- [logisticspipes.network.packets.satpipe](#logisticspipesnetworkpacketssatpipe) (3)
- [logisticspipes.pipes](#logisticspipespipes) (15)
- [logisticspipes.pipes.basic](#logisticspipespipesbasic) (4)
- [logisticspipes.pipes.basic.fluid](#logisticspipespipesbasicfluid) (1)
- [logisticspipes.pipes.signs](#logisticspipespipessigns) (1)
- [logisticspipes.pipes.upgrades](#logisticspipespipesupgrades) (6)
- [logisticspipes.proxy](#logisticspipesproxy) (2)
- [logisticspipes.proxy.buildcraft](#logisticspipesproxybuildcraft) (1)
- [logisticspipes.proxy.buildcraft.subproxies](#logisticspipesproxybuildcraftsubproxies) (1)
- [logisticspipes.proxy.gtnh](#logisticspipesproxygtnh) (2)
- [logisticspipes.proxy.ic2](#logisticspipesproxyic2) (1)
- [logisticspipes.proxy.interfaces](#logisticspipesproxyinterfaces) (1)
- [logisticspipes.proxy.side](#logisticspipesproxyside) (2)
- [logisticspipes.proxy.specialinventoryhandler](#logisticspipesproxyspecialinventoryhandler) (7)
- [logisticspipes.recipes](#logisticspipesrecipes) (1)
- [logisticspipes.renderer](#logisticspipesrenderer) (5)
- [logisticspipes.renderer.newpipe](#logisticspipesrenderernewpipe) (2)
- [logisticspipes.renderer.state](#logisticspipesrendererstate) (1)
- [logisticspipes.request](#logisticspipesrequest) (7)
- [logisticspipes.request.debug](#logisticspipesrequestdebug) (2)
- [logisticspipes.request.resources](#logisticspipesrequestresources) (3)
- [logisticspipes.routing](#logisticspipesrouting) (7)
- [logisticspipes.routing.astar](#logisticspipesroutingastar) (26)
- [logisticspipes.routing.order](#logisticspipesroutingorder) (6)
- [logisticspipes.ticks](#logisticspipesticks) (1)
- [logisticspipes.transport](#logisticspipestransport) (8)
- [logisticspipes.utils](#logisticspipesutils) (1)
- [logisticspipes.utils.gui](#logisticspipesutilsgui) (1)
- [logisticspipes.utils.item](#logisticspipesutilsitem) (2)
- [logisticspipes.utils.string](#logisticspipesutilsstring) (1)
- [network.rs485.debuggui](#networkrs485debuggui) (1)
- [test: logisticspipes.routing.astar](#test-logisticspipesroutingastar) (7)
- [test: logisticspipes.transport](#test-logisticspipestransport) (1)
- [resources: assets/logisticspipes/lang](#resources-assetslogisticspipeslang) (2)
- [resources: assets/logisticspipes/textures/gui](#resources-assetslogisticspipestexturesgui) (5)
- [resources: assets/logisticspipes/textures/items](#resources-assetslogisticspipestexturesitems) (1)
- [docs](#docs) (18)
- [.claude (internal AI/MUI reference docs)](#claude-internal-aimui-reference-docs) (24)
- [build & repository root](#build--repository-root) (7)

## logisticspipes

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [LogisticsEventListener.java](../../src/main/java/logisticspipes/LogisticsEventListener.java) | Modified | +18 / -0 | [06 · Mod init, proxies and events](06-modules-pipes-compat-build.md#mod-init-proxies-and-events) | Chunk-unload hook for junction network; debug GUI cleanup on unload/logout |
| [LogisticsPipes.java](../../src/main/java/logisticspipes/LogisticsPipes.java) | Modified | +69 / -5 | [06 · Mod init, proxies and events](06-modules-pipes-compat-build.md#mod-init-proxies-and-events) | Junction router init, new items/pipes, isGregTech/enableVBO flags, stop cleanup |
| [LPConstants.java](../../src/main/java/logisticspipes/LPConstants.java) | Modified | +6 / -1 | [06 · Mod init, proxies and events](06-modules-pipes-compat-build.md#mod-init-proxies-and-events) | PIPE_NORMAL_SPEED made mutable for /lp pipespeed; DEBUG Javadoc warning |

## logisticspipes.api

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [IMUICompatibleModule.java](../../src/main/java/logisticspipes/api/IMUICompatibleModule.java) | Added | +24 / -0 | [05 · MUI integration layer](05-modularui-gui.md#mui-integration-layer) | Module MUI contract: hand GUI, pipe GUI with prefix, open from main hand |
| [IMUICompatiblePipeV2.java](../../src/main/java/logisticspipes/api/IMUICompatiblePipeV2.java) | Added | +16 / -0 | [05 · MUI integration layer](05-modularui-gui.md#mui-integration-layer) | Pipe MUI contract: getPipeGui() returning a LogisticsModularUI |

## logisticspipes.blocks

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [LogisticsSolidBlock.java](../../src/main/java/logisticspipes/blocks/LogisticsSolidBlock.java) | Modified | +86 / -51 | [06 · Mod init, proxies and events](06-modules-pipes-compat-build.md#mod-init-proxies-and-events) | New metas 6 pattern table, 7 crafting monitor; MUI tile GUI path |

## logisticspipes.blocks.powertile

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [LogisticsPowerProviderTileEntity.java](../../src/main/java/logisticspipes/blocks/powertile/LogisticsPowerProviderTileEntity.java) | Modified | +2 / -2 | [03 · Power provider routing](03-router-rework.md#power-provider-routing) | reOrdered BitSet now sized from RouterIds instead of ServerRouter |

## logisticspipes.commands

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [MainCommandHandler.java](../../src/main/java/logisticspipes/commands/MainCommandHandler.java) | Modified | +4 / -0 | [06 · Mod init, proxies and events](06-modules-pipes-compat-build.md#mod-init-proxies-and-events) | Registers /lp rt-clear and /lp pipespeed subcommands |

## logisticspipes.commands.commands

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [PipeSpeedCommand.java](../../src/main/java/logisticspipes/commands/commands/PipeSpeedCommand.java) | Added | +54 / -0 | [04 · Pipe speed command](04-item-transport.md#pipe-speed-command) | OP-only `/lp pipespeed [speed]` shows or sets PIPE_NORMAL_SPEED at runtime, not saved |
| [RoutingThreadClearCommand.java](../../src/main/java/logisticspipes/commands/commands/RoutingThreadClearCommand.java) | Added | +31 / -0 | [03 · Diagnostics: commands and routing debug](03-router-rework.md#diagnostics-commands-and-routing-debug) | New /lp rt-clear command; resets junction-router statistics, keeps caches |
| [RoutingThreadCommand.java](../../src/main/java/logisticspipes/commands/commands/RoutingThreadCommand.java) | Modified | +4 / -6 | [03 · Diagnostics: commands and routing debug](03-router-rework.md#diagnostics-commands-and-routing-debug) | /lp rt prints LPJunctionNetwork.describe() instead of RoutingTableUpdateThread stats |

## logisticspipes.commands.commands.debug

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [DebugGuiController.java](../../src/main/java/logisticspipes/commands/commands/debug/DebugGuiController.java) | Modified | +155 / -26 | [05 · Debug GUI](05-modularui-gui.md#debug-gui) | Per-watch ids, queued client data, per-player/world cleanup, bounds checks |

## logisticspipes.compat

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [ModularUIHelper.java](../../src/main/java/logisticspipes/compat/ModularUIHelper.java) | Modified | +17 / -0 | [05 · MUI integration layer](05-modularui-gui.md#mui-integration-layer) | Adds openModuleUI (main hand) and unused right-tab texture |

## logisticspipes.config

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [Configs.java](../../src/main/java/logisticspipes/config/Configs.java) | Modified | +18 / -0 | [06 · Config](06-modules-pipes-compat-build.md#config) | Adds itemClumpTransport and itemClumpGatherTicks options |
| [PlayerConfig.java](../../src/main/java/logisticspipes/config/PlayerConfig.java) | Modified | +0 / -39 | [05 · Settings GUI](05-modularui-gui.md#settings-gui) | Removes VBO/fallback renderer and render distance settings |

## logisticspipes.crafting

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [AdjacentInventoryHandler.java](../../src/main/java/logisticspipes/crafting/AdjacentInventoryHandler.java) | Added | +742 / -0 | [01 · Pattern crafting pipe](01-pattern-crafting.md#pattern-crafting-pipe) | Capacity simulation, insert, extract and isEmpty against the selected target |
| [CraftingMonitorGui.java](../../src/main/java/logisticspipes/crafting/CraftingMonitorGui.java) | Added | +375 / -0 | [01 · HUD & crafting monitor](01-pattern-crafting.md#hud--crafting-monitor) | Legacy-style GUI of the Crafting Monitor block: instance tree, refresh, cancel |
| [CraftingMonitorGuiProvider.java](../../src/main/java/logisticspipes/crafting/CraftingMonitorGuiProvider.java) | Added | +70 / -0 | [01 · HUD & crafting monitor](01-pattern-crafting.md#hud--crafting-monitor) | GuiProvider opening the Crafting Monitor GUI with initial entries |
| [CraftingMonitorTileEntity.java](../../src/main/java/logisticspipes/crafting/CraftingMonitorTileEntity.java) | Added | +60 / -0 | [01 · HUD & crafting monitor](01-pattern-crafting.md#hud--crafting-monitor) | Crafting Monitor block (meta 7): lists/cancels live instances on adjacent network |
| [IPatternSatellitePipe.java](../../src/main/java/logisticspipes/crafting/IPatternSatellitePipe.java) | Added | +19 / -0 | [01 · Satellites (item/fluid, routed vs instant)](01-pattern-crafting.md#satellites-itemfluid-routed-vs-instant) | Satellite interface adding a player-defined, uniqueness-suffixed name |
| [IStagedCraftingProvider.java](../../src/main/java/logisticspipes/crafting/IStagedCraftingProvider.java) | Added | +12 / -0 | [01 · Staged crafting & request tree integration](01-pattern-crafting.md#staged-crafting--request-tree-integration) | Hook letting a crafter take a whole request-tree branch (fullFillStagedCrafting) |
| [IStagedProviderReservation.java](../../src/main/java/logisticspipes/crafting/IStagedProviderReservation.java) | Added | +16 / -0 | [01 · Staged crafting & request tree integration](01-pattern-crafting.md#staged-crafting--request-tree-integration) | Provider stock reserve/release API used while a staged craft waits |
| [ItemMemoryChip.java](../../src/main/java/logisticspipes/crafting/ItemMemoryChip.java) | Added | +231 / -0 | [01 · Satellites (item/fluid, routed vs instant)](01-pattern-crafting.md#satellites-itemfluid-routed-vs-instant) | New Memory Chip item storing satellite refs; FAVORITES / APPLY_LAST_TO_RECIPE modes |
| [ModulePatternCrafting.java](../../src/main/java/logisticspipes/crafting/ModulePatternCrafting.java) | Added | +1615 / -0 | [01 · Pattern crafting pipe](01-pattern-crafting.md#pattern-crafting-pipe) | Core module: sink, tick loop, LP craft/provide hooks, NBT, delegates to handlers |
| [PatternByproductExtractionResult.java](../../src/main/java/logisticspipes/crafting/PatternByproductExtractionResult.java) | Added | +31 / -0 | [01 · Byproducts & extras](01-pattern-crafting.md#byproducts--extras) | Result of one remote (satellite) byproduct extraction |
| [PatternByproductExtractionTarget.java](../../src/main/java/logisticspipes/crafting/PatternByproductExtractionTarget.java) | Added | +29 / -0 | [01 · Byproducts & extras](01-pattern-crafting.md#byproducts--extras) | Interface for satellites that can extract byproducts from their machine |
| [PatternByproductExtractionTargetCache.java](../../src/main/java/logisticspipes/crafting/PatternByproductExtractionTargetCache.java) | Added | +93 / -0 | [01 · Byproducts & extras](01-pattern-crafting.md#byproducts--extras) | Resolves and caches (40 ticks) satellites assigned to pattern output slots |
| [PatternByproductPromise.java](../../src/main/java/logisticspipes/crafting/PatternByproductPromise.java) | Added | +9 / -0 | [01 · Byproducts & extras](01-pattern-crafting.md#byproducts--extras) | Marker interface exposing a promise's PatternByproductTarget |
| [PatternByproductTarget.java](../../src/main/java/logisticspipes/crafting/PatternByproductTarget.java) | Added | +117 / -0 | [01 · Byproducts & extras](01-pattern-crafting.md#byproducts--extras) | Identifies a byproduct's pattern/output slot, source order and extraction satellite |
| [PatternCraftingArrivalHandler.java](../../src/main/java/logisticspipes/crafting/PatternCraftingArrivalHandler.java) | Added | +167 / -0 | [01 · Pattern crafting pipe](01-pattern-crafting.md#pattern-crafting-pipe) | Buffers routed ingredients only when they carry tracked order/delivery references |
| [PatternCraftingBlockingHandler.java](../../src/main/java/logisticspipes/crafting/PatternCraftingBlockingHandler.java) | Added | +327 / -0 | [01 · Blocking modes](01-pattern-crafting.md#blocking-modes) | Running-craft lock and satellite batch state for BLOCKING/SMART modes |
| [PatternCraftingBranch.java](../../src/main/java/logisticspipes/crafting/PatternCraftingBranch.java) | Added | +1424 / -0 | [01 · Staged crafting & request tree integration](01-pattern-crafting.md#staged-crafting--request-tree-integration) | Consumable snapshot of a request subtree: promises, extras, children, slicing |
| [PatternCraftingBufferDispatcher.java](../../src/main/java/logisticspipes/crafting/PatternCraftingBufferDispatcher.java) | Added | +239 / -0 | [01 · Pattern crafting pipe](01-pattern-crafting.md#pattern-crafting-pipe) | Pushes complete buffered sets to target/satellites; tracks partial (pending) sets |
| [PatternCraftingCancelHandler.java](../../src/main/java/logisticspipes/crafting/PatternCraftingCancelHandler.java) | Added | +204 / -0 | [01 · Cancellation](01-pattern-crafting.md#cancellation) | Cancels whole crafting instances and flushes their owned inputs to storage |
| [PatternCraftingCapacity.java](../../src/main/java/logisticspipes/crafting/PatternCraftingCapacity.java) | Added | +268 / -0 | [01 · Pattern crafting pipe](01-pattern-crafting.md#pattern-crafting-pipe) | Computes safe item/fluid room and reservations for sinks and scheduler |
| [PatternCraftingHudHandler.java](../../src/main/java/logisticspipes/crafting/PatternCraftingHudHandler.java) | Added | +216 / -0 | [01 · HUD & crafting monitor](01-pattern-crafting.md#hud--crafting-monitor) | Builds and caches the pattern HUD snapshot (dirty flag + recheck interval) |
| [PatternCraftingHudState.java](../../src/main/java/logisticspipes/crafting/PatternCraftingHudState.java) | Added | +195 / -0 | [01 · HUD & crafting monitor](01-pattern-crafting.md#hud--crafting-monitor) | Serializable HUD snapshot: mode, per-pattern buffered inputs and requested outputs |
| [PatternCraftingIngredientPlanner.java](../../src/main/java/logisticspipes/crafting/PatternCraftingIngredientPlanner.java) | Added | +447 / -0 | [01 · Pattern crafting pipe](01-pattern-crafting.md#pattern-crafting-pipe) | OreDict/NBT ingredient matching, satellite targets, buffered set plans |
| [PatternCraftingInstanceRegistry.java](../../src/main/java/logisticspipes/crafting/PatternCraftingInstanceRegistry.java) | Added | +140 / -0 | [01 · Cancellation](01-pattern-crafting.md#cancellation) | Static index of live staged orders by instance plus cancellation tombstones |
| [PatternCraftingMonitorEntry.java](../../src/main/java/logisticspipes/crafting/PatternCraftingMonitorEntry.java) | Added | +102 / -0 | [01 · HUD & crafting monitor](01-pattern-crafting.md#hud--crafting-monitor) | Client-safe snapshot of one cancellable crafting instance |
| [PatternCraftingMonitorNode.java](../../src/main/java/logisticspipes/crafting/PatternCraftingMonitorNode.java) | Added | +93 / -0 | [01 · HUD & crafting monitor](01-pattern-crafting.md#hud--crafting-monitor) | Serializable progress-tree node for monitor views |
| [PatternCraftingMonitorRegistry.java](../../src/main/java/logisticspipes/crafting/PatternCraftingMonitorRegistry.java) | Added | +212 / -0 | [01 · HUD & crafting monitor](01-pattern-crafting.md#hud--crafting-monitor) | Builds monitor trees per request or per network; cancel by instance |
| [PatternCraftingOrder.java](../../src/main/java/logisticspipes/crafting/PatternCraftingOrder.java) | Added | +523 / -0 | [01 · Staged crafting & request tree integration](01-pattern-crafting.md#staged-crafting--request-tree-integration) | One staged output order: remaining sets, ingredient requests, dispatched byproducts |
| [PatternCraftingPersistence.java](../../src/main/java/logisticspipes/crafting/PatternCraftingPersistence.java) | Added | +696 / -0 | [01 · Persistence & NBT](01-pattern-crafting.md#persistence--nbt) | NBT codec for orders, promises, resources and branches (RestoreNotReadyException) |
| [PatternCraftingPromise.java](../../src/main/java/logisticspipes/crafting/PatternCraftingPromise.java) | Added | +33 / -0 | [01 · Staged crafting & request tree integration](01-pattern-crafting.md#staged-crafting--request-tree-integration) | Item crafting promise carrying pattern slot and result amount per set |
| [PatternCraftingReference.java](../../src/main/java/logisticspipes/crafting/PatternCraftingReference.java) | Added | +92 / -0 | [01 · Cancellation](01-pattern-crafting.md#cancellation) | Stable (instanceId, objectId) UUID identity for orders, deliveries, batches |
| [PatternCraftingResultExtractor.java](../../src/main/java/logisticspipes/crafting/PatternCraftingResultExtractor.java) | Added | +506 / -0 | [01 · Pattern crafting pipe](01-pattern-crafting.md#pattern-crafting-pipe) | Extracts outputs/extras every 6 ticks, routes them or feeds same-pipe buffers |
| [PatternCraftingTargetSelector.java](../../src/main/java/logisticspipes/crafting/PatternCraftingTargetSelector.java) | Added | +241 / -0 | [01 · Pattern crafting pipe](01-pattern-crafting.md#pattern-crafting-pipe) | Selected adjacent target side: auto-pick, sneak-wrench cycling, NBT, sync |
| [PatternCraftingTemplate.java](../../src/main/java/logisticspipes/crafting/PatternCraftingTemplate.java) | Added | +166 / -0 | [01 · Staged crafting & request tree integration](01-pattern-crafting.md#staged-crafting--request-tree-integration) | Item crafting template with one component per input slot and targeted byproducts |
| [PatternCraftingTemplateBuilder.java](../../src/main/java/logisticspipes/crafting/PatternCraftingTemplateBuilder.java) | Added | +246 / -0 | [01 · Staged crafting & request tree integration](01-pattern-crafting.md#staged-crafting--request-tree-integration) | Turns pattern main output into item/fluid templates; other outputs become byproducts |
| [PatternCraftingUpgradeCache.java](../../src/main/java/logisticspipes/crafting/PatternCraftingUpgradeCache.java) | Added | +44 / -0 | [01 · Upgrades](01-pattern-crafting.md#upgrades) | Per-tick cache of fluid crafting, advanced and instant satellite upgrade flags |
| [PatternFluidByproductPromise.java](../../src/main/java/logisticspipes/crafting/PatternFluidByproductPromise.java) | Added | +39 / -0 | [01 · Byproducts & extras](01-pattern-crafting.md#byproducts--extras) | Fluid extra promise that keeps its extraction target |
| [PatternFluidCraftingPromise.java](../../src/main/java/logisticspipes/crafting/PatternFluidCraftingPromise.java) | Added | +40 / -0 | [01 · Fluid crafting](01-pattern-crafting.md#fluid-crafting) | Fluid crafting promise with pattern slot and result amount per set |
| [PatternFluidCraftingTemplate.java](../../src/main/java/logisticspipes/crafting/PatternFluidCraftingTemplate.java) | Added | +122 / -0 | [01 · Fluid crafting](01-pattern-crafting.md#fluid-crafting) | Fluid-output crafting template with targeted item/fluid byproducts |
| [PatternIngredientAssignment.java](../../src/main/java/logisticspipes/crafting/PatternIngredientAssignment.java) | Added | +13 / -0 | [01 · Pattern crafting pipe](01-pattern-crafting.md#pattern-crafting-pipe) | Record: concrete buffered stack chosen for one input slot |
| [PatternIngredientTarget.java](../../src/main/java/logisticspipes/crafting/PatternIngredientTarget.java) | Added | +27 / -0 | [01 · Pattern crafting pipe](01-pattern-crafting.md#pattern-crafting-pipe) | Record: input ingredient plus local or satellite destination |
| [PatternItemByproductPromise.java](../../src/main/java/logisticspipes/crafting/PatternItemByproductPromise.java) | Added | +34 / -0 | [01 · Byproducts & extras](01-pattern-crafting.md#byproducts--extras) | Item extra promise that keeps its extraction target |
| [PatternLogisticsCraftingTableTileEntity.java](../../src/main/java/logisticspipes/crafting/PatternLogisticsCraftingTableTileEntity.java) | Added | +667 / -0 | [01 · Pattern crafting table](01-pattern-crafting.md#pattern-crafting-table) | Pattern Crafting Table (meta 6): timed vanilla crafting fed by pattern pipes |
| [PatternLostIngredientHandler.java](../../src/main/java/logisticspipes/crafting/PatternLostIngredientHandler.java) | Added | +171 / -0 | [01 · Pattern crafting pipe](01-pattern-crafting.md#pattern-crafting-pipe) | Lost-ingredient retry queue keyed by delivery references, persisted |
| [PatternSatelliteByproductExtractor.java](../../src/main/java/logisticspipes/crafting/PatternSatelliteByproductExtractor.java) | Added | +213 / -0 | [01 · Byproducts & extras](01-pattern-crafting.md#byproducts--extras) | Satellite-side extraction of ordered byproducts (needs byproduct upgrade) |
| [PatternSatelliteDispatchHandler.java](../../src/main/java/logisticspipes/crafting/PatternSatelliteDispatchHandler.java) | Added | +600 / -0 | [01 · Satellites (item/fluid, routed vs instant)](01-pattern-crafting.md#satellites-itemfluid-routed-vs-instant) | Builds dispatch plans: local insert, routed/instant item sats, direct fluid sats |
| [PatternSatelliteInfo.java](../../src/main/java/logisticspipes/crafting/PatternSatelliteInfo.java) | Added | +204 / -0 | [01 · Satellites (item/fluid, routed vs instant)](01-pattern-crafting.md#satellites-itemfluid-routed-vs-instant) | Network DTO describing a satellite for the editor's selector list |
| [PatternStackBufferHandler.java](../../src/main/java/logisticspipes/crafting/PatternStackBufferHandler.java) | Added | +319 / -0 | [01 · Persistence & NBT](01-pattern-crafting.md#persistence--nbt) | Arrived-ingredient buffer with per-instance ownership and slot aggregates |
| [PatternStackRequestHandler.java](../../src/main/java/logisticspipes/crafting/PatternStackRequestHandler.java) | Added | +313 / -0 | [01 · Persistence & NBT](01-pattern-crafting.md#persistence--nbt) | In-flight requested ingredients with ownership; persisted |
| [PatternStagedCraftingCoordinator.java](../../src/main/java/logisticspipes/crafting/PatternStagedCraftingCoordinator.java) | Added | +639 / -0 | [01 · Staged crafting & request tree integration](01-pattern-crafting.md#staged-crafting--request-tree-integration) | Creates/registers staged output orders; staged save/restore and pending entries |
| [PatternStagedCraftingScheduler.java](../../src/main/java/logisticspipes/crafting/PatternStagedCraftingScheduler.java) | Added | +263 / -0 | [01 · Staged crafting & request tree integration](01-pattern-crafting.md#staged-crafting--request-tree-integration) | Per-tick choice of how many sets each staged order may request |
| [PatternTargetInformation.java](../../src/main/java/logisticspipes/crafting/PatternTargetInformation.java) | Added | +33 / -0 | [01 · Staged crafting & request tree integration](01-pattern-crafting.md#staged-crafting--request-tree-integration) | Record target info: pattern slot, input slot, order and delivery references |
| [PipeFluidPatternSatelliteLogistics.java](../../src/main/java/logisticspipes/crafting/PipeFluidPatternSatelliteLogistics.java) | Added | +483 / -0 | [01 · Satellites (item/fluid, routed vs instant)](01-pattern-crafting.md#satellites-itemfluid-routed-vs-instant) | Pattern fluid satellite pipe: UUID/name, reservations, direct fluid insert |
| [PipeItemsPatternSatelliteLogistics.java](../../src/main/java/logisticspipes/crafting/PipeItemsPatternSatelliteLogistics.java) | Added | +776 / -0 | [01 · Satellites (item/fluid, routed vs instant)](01-pattern-crafting.md#satellites-itemfluid-routed-vs-instant) | Pattern item satellite pipe: UUID/name, reservations, routed inputs, chip linking |

## logisticspipes.crafting.pattern

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [AbstractPattern.java](../../src/main/java/logisticspipes/crafting/pattern/AbstractPattern.java) | Added | +581 / -0 | [01 · Pattern items & pattern types](01-pattern-crafting.md#pattern-items--pattern-types) | Pattern NBT accessor: entries, satellite targets, main output, OD/NBT flags |
| [DefaultPattern.java](../../src/main/java/logisticspipes/crafting/pattern/DefaultPattern.java) | Added | +24 / -0 | [01 · Pattern items & pattern types](01-pattern-crafting.md#pattern-items--pattern-types) | Crafting pattern layout: 9 inputs, 3 outputs |
| [EditedPatternInventory.java](../../src/main/java/logisticspipes/crafting/pattern/EditedPatternInventory.java) | Added | +98 / -0 | [01 · Pattern items & pattern types](01-pattern-crafting.md#pattern-items--pattern-types) | IInventory view of one pattern's entries for editor phantom slots |
| [ItemPattern.java](../../src/main/java/logisticspipes/crafting/pattern/ItemPattern.java) | Added | +125 / -0 | [01 · Pattern items & pattern types](01-pattern-crafting.md#pattern-items--pattern-types) | Logistic Crafting Pattern item; opens handheld MUI; type toggle; tooltip |
| [PatternHandler.java](../../src/main/java/logisticspipes/crafting/pattern/PatternHandler.java) | Added | +234 / -0 | [01 · Pattern items & pattern types](01-pattern-crafting.md#pattern-items--pattern-types) | Pipe pattern inventory wrapper with cached PatternRecipeSnapshots |
| [PatternRecipeImport.java](../../src/main/java/logisticspipes/crafting/pattern/PatternRecipeImport.java) | Added | +151 / -0 | [01 · NEI integration](01-pattern-crafting.md#nei-integration) | Recipe transferred from a viewer into a pattern (slots, inputs, outputs) |
| [PatternRecipeSnapshot.java](../../src/main/java/logisticspipes/crafting/pattern/PatternRecipeSnapshot.java) | Added | +193 / -0 | [01 · Pattern items & pattern types](01-pattern-crafting.md#pattern-items--pattern-types) | Immutable parsed view of a pattern, reused until inventory changes |
| [PatternSource.java](../../src/main/java/logisticspipes/crafting/pattern/PatternSource.java) | Added | +101 / -0 | [01 · Pattern items & pattern types](01-pattern-crafting.md#pattern-items--pattern-types) | Where an edited pattern lives: pipe slot or held item, read live |
| [ProcessingPattern.java](../../src/main/java/logisticspipes/crafting/pattern/ProcessingPattern.java) | Added | +24 / -0 | [01 · Pattern items & pattern types](01-pattern-crafting.md#pattern-items--pattern-types) | Processing pattern layout: 16 inputs, 4 outputs |

## logisticspipes.crafting.patternStack

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [IPatternStack.java](../../src/main/java/logisticspipes/crafting/patternStack/IPatternStack.java) | Added | +50 / -0 | [01 · Pattern items & pattern types](01-pattern-crafting.md#pattern-items--pattern-types) | Item-or-fluid stack abstraction with typed NBT and legacy ItemStack read |
| [PatternFluidStack.java](../../src/main/java/logisticspipes/crafting/patternStack/PatternFluidStack.java) | Added | +129 / -0 | [01 · Fluid crafting](01-pattern-crafting.md#fluid-crafting) | Fluid pattern entry (mB); uses NEI StackInfo for container conversion |
| [PatternItemStack.java](../../src/main/java/logisticspipes/crafting/patternStack/PatternItemStack.java) | Added | +107 / -0 | [01 · Pattern items & pattern types](01-pattern-crafting.md#pattern-items--pattern-types) | Item pattern entry; writes int lpCount to survive >127 stacks |
| [PatternStackHelper.java](../../src/main/java/logisticspipes/crafting/patternStack/PatternStackHelper.java) | Added | +126 / -0 | [01 · Pattern items & pattern types](01-pattern-crafting.md#pattern-items--pattern-types) | Matching, aggregation, copy and display helpers for pattern stacks |

## logisticspipes.crafting.requesttable

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [RequestTableContainer.java](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableContainer.java) | Added | +411 / -0 | [02 · Internal item & fluid storage](02-request-table.md#internal-item--fluid-storage) | Container with all slots made once and moved by layout; fluid-slot clicks, result crafting, shift-click transfers |
| [RequestTableDisplaySettings.java](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableDisplaySettings.java) | Added | +90 / -0 | [02 · Display settings (sort / filter, per player)](02-request-table.md#display-settings-sort--filter-per-player) | Immutable sort mode/direction/filter value with NBT and ordinal helpers |
| [RequestTableDisplaySettingsStore.java](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableDisplaySettingsStore.java) | Added | +69 / -0 | [02 · Display settings (sort / filter, per player)](02-request-table.md#display-settings-sort--filter-per-player) | Per-player-UUID display settings saved in table NBT |
| [RequestTableFluidStorage.java](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableFluidStorage.java) | Added | +372 / -0 | [02 · Internal item & fluid storage](02-request-table.md#internal-item--fluid-storage) | Resizable slotted FluidStack storage shown as IInventory of LP fluid containers |
| [RequestTableGui.java](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableGui.java) | Added | +718 / -0 | [02 · GUI](02-request-table.md#gui) | Client GUI: network grid, storage views, display buttons, crafting area, request popup |
| [RequestTableLayout.java](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableLayout.java) | Added | +146 / -0 | [02 · Internal item & fluid storage](02-request-table.md#internal-item--fluid-storage) | Pixel layout of the adaptive-height request table GUI |
| [RequestTableNetworkEntry.java](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableNetworkEntry.java) | Added | +116 / -0 | [02 · Combined item + fluid network list](02-request-table.md#combined-item--fluid-network-list) | List entry: stack, fluid flag, network/internal amounts, craftable flag |
| [RequestTableNetworkGrid.java](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableNetworkGrid.java) | Added | +173 / -0 | [02 · Combined item + fluid network list](02-request-table.md#combined-item--fluid-network-list) | Draws the scrollable item/fluid grid, tooltips, internal amounts, hit-testing |
| [RequestTableNetworkList.java](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableNetworkList.java) | Added | +145 / -0 | [02 · Combined item + fluid network list](02-request-table.md#combined-item--fluid-network-list) | Client-side cached sort/filter/search over received entries |
| [RequestTablePipe.java](../../src/main/java/logisticspipes/crafting/requesttable/RequestTablePipe.java) | Added | +1168 / -0 | [02 · New request table pipe (Mk2)](02-request-table.md#new-request-table-pipe-mk2) | Mk2 table: storage upgrades, item/fluid arrivals, storage clicks, crafting, Send all, NBT |
| [RequestTableRequestOverlay.java](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableRequestOverlay.java) | Added | +240 / -0 | [02 · Request popup (request overlay)](02-request-table.md#request-popup-request-overlay) | Modal request popup with amount field, ±1/10/100/1000 buttons, OK/close |
| [RequestTableView.java](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableView.java) | Added | +22 / -0 | [02 · Internal item & fluid storage](02-request-table.md#internal-item--fluid-storage) | Enum of upper panel modes: NETWORK, ITEM_STORAGE, FLUID_STORAGE |

## logisticspipes.crafting.requesttable.upgrades

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [RequestTableFluidCapacityUpgrade.java](../../src/main/java/logisticspipes/crafting/requesttable/upgrades/RequestTableFluidCapacityUpgrade.java) | Added | +7 / -0 | [02 · Storage upgrades](02-request-table.md#storage-upgrades) | Marker upgrade (damage 48): +1× base capacity per fluid slot |
| [RequestTableFluidInventoryUpgrade.java](../../src/main/java/logisticspipes/crafting/requesttable/upgrades/RequestTableFluidInventoryUpgrade.java) | Added | +7 / -0 | [02 · Storage upgrades](02-request-table.md#storage-upgrades) | Marker upgrade (damage 47): +9 fluid slots |
| [RequestTableItemInventoryUpgrade.java](../../src/main/java/logisticspipes/crafting/requesttable/upgrades/RequestTableItemInventoryUpgrade.java) | Added | +7 / -0 | [02 · Storage upgrades](02-request-table.md#storage-upgrades) | Marker upgrade (damage 45): +9 item slots |
| [RequestTableItemStackUpgrade.java](../../src/main/java/logisticspipes/crafting/requesttable/upgrades/RequestTableItemStackUpgrade.java) | Added | +7 / -0 | [02 · Storage upgrades](02-request-table.md#storage-upgrades) | Marker upgrade (damage 46): +64 per-slot item limit |
| [RequestTableStorageUpgrade.java](../../src/main/java/logisticspipes/crafting/requesttable/upgrades/RequestTableStorageUpgrade.java) | Added | +37 / -0 | [02 · Storage upgrades](02-request-table.md#storage-upgrades) | Base IPipeUpgrade: allowed only on RequestTablePipe, never on modules |

## logisticspipes.gui

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [GuiCraftingPipe.java](../../src/main/java/logisticspipes/gui/GuiCraftingPipe.java) | Modified | +12 / -1 | [01 · Changes to upstream classes](01-pattern-crafting.md#changes-to-upstream-classes) | Legacy crafting pipe GUI gets a "Blocking" button (sets blocking on only) |
| [GuiLogisticsSettings.java](../../src/main/java/logisticspipes/gui/GuiLogisticsSettings.java) | Modified | +4 / -31 | [05 · Settings GUI](05-modularui-gui.md#settings-gui) | Removes VBO/fallback renderer and render distance controls |

## logisticspipes.gui.hud

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [HUDPatternCrafting.java](../../src/main/java/logisticspipes/gui/hud/HUDPatternCrafting.java) | Added | +333 / -0 | [01 · HUD & crafting monitor](01-pattern-crafting.md#hud--crafting-monitor) | HUD glasses view of patterns, buffered inputs, outputs and status |

## logisticspipes.gui.modularUI

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [ChassisGui.java](../../src/main/java/logisticspipes/gui/modularUI/ChassisGui.java) | Added | +370 / -0 | [05 · Chassis GUI](05-modularui-gui.md#chassis-gui) | Chassis MUI: module tabs, live per-slot module pages, upgrade column, save on close |
| [DraggableFlow.java](../../src/main/java/logisticspipes/gui/modularUI/DraggableFlow.java) | Added | +110 / -0 | [05 · MUI integration layer](05-modularui-gui.md#mui-integration-layer) | Draggable Flow container used by the upgrade sidebar |
| [GenericModuleMUI.java](../../src/main/java/logisticspipes/gui/modularUI/GenericModuleMUI.java) | Added | +17 / -0 | [05 · MUI integration layer](05-modularui-gui.md#mui-integration-layer) | Base class for module MUIs holding the typed module |
| [GenericPipeLogisticsGui.java](../../src/main/java/logisticspipes/gui/modularUI/GenericPipeLogisticsGui.java) | Added | +93 / -0 | [05 · MUI integration layer](05-modularui-gui.md#mui-integration-layer) | Wraps module MUI as pipe GUI; upgrade column disabled |
| [GenericSimplePipeLogisticsGui.java](../../src/main/java/logisticspipes/gui/modularUI/GenericSimplePipeLogisticsGui.java) | Added | +91 / -0 | [05 · MUI integration layer](05-modularui-gui.md#mui-integration-layer) | Wraps LogisticsPipeMUI as pipe GUI; upgrade column disabled |
| [LogisticsModularUI.java](../../src/main/java/logisticspipes/gui/modularUI/LogisticsModularUI.java) | Added | +55 / -0 | [05 · MUI integration layer](05-modularui-gui.md#mui-integration-layer) | Base of all LP MUIs: id, size, sync prefix, addWidgets, getPanel |
| [LogisticsModuleData.java](../../src/main/java/logisticspipes/gui/modularUI/LogisticsModuleData.java) | Added | +21 / -0 | [05 · MUI integration layer](05-modularui-gui.md#mui-integration-layer) | GuiData carrying a module; unused |
| [LogisticsPipeMUI.java](../../src/main/java/logisticspipes/gui/modularUI/LogisticsPipeMUI.java) | Added | +21 / -0 | [05 · MUI integration layer](05-modularui-gui.md#mui-integration-layer) | Base class for pipe MUIs holding the pipe |
| [PipeGuiFactory.java](../../src/main/java/logisticspipes/gui/modularUI/PipeGuiFactory.java) | Added | +107 / -0 | [05 · Upgrade sidebar and upgrade inventories](05-modularui-gui.md#upgrade-sidebar-and-upgrade-inventories) | fromModule/fromMui wrappers and draggable 4-slot upgrade sidebar |
| [SimpleInventorySlot.java](../../src/main/java/logisticspipes/gui/modularUI/SimpleInventorySlot.java) | Added | +29 / -0 | [05 · MUI integration layer](05-modularui-gui.md#mui-integration-layer) | Slot fix so shift-click into copy-storing inventories doesn't lose items |

## logisticspipes.gui.modularUI.blocks

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [PatternCraftingTableMui.java](../../src/main/java/logisticspipes/gui/modularUI/blocks/PatternCraftingTableMui.java) | Added | +107 / -0 | [05 · Pipe GUIs](05-modularui-gui.md#pipe-guis) | Pattern crafting table MUI: 3x3 input, 3 outputs, speed upgrades |

## logisticspipes.gui.modularUI.dynamicModules

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [ModuleActiveSupplierMuiDynamic.java](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleActiveSupplierMuiDynamic.java) | Added | +118 / -0 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | Active supplier MUI: 3x3 stock slots, request mode |
| [ModuleBeeAnalyzerMuiDynamic.java](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleBeeAnalyzerMuiDynamic.java) | Added | +61 / -0 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | Apiarist analyser MUI: extract on/off toggle |
| [ModuleCraftingMuiDynamic.java](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleCraftingMuiDynamic.java) | Added | +185 / -0 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | Crafting module MUI; no callers, module deprecated |
| [ModuleCreativeTabBasedItemSinkMuiDynamic.java](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleCreativeTabBasedItemSinkMuiDynamic.java) | Added | +156 / -0 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | Creative tab sink MUI: analyse slot, add, 9-entry removable list |
| [ModuleElectricManagerMuiDynamic.java](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleElectricManagerMuiDynamic.java) | Added | +100 / -0 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | Electric manager MUI: filter slots, charge/discharge toggle |
| [ModuleEnchantmentSinkMK2MuiDynamic.java](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleEnchantmentSinkMK2MuiDynamic.java) | Added | +85 / -0 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | Enchantment sink MK2 MUI: filter slots |
| [ModuleItemSinkMuiDynamic.java](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleItemSinkMuiDynamic.java) | Added | +110 / -0 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | Item sink MUI: 9 filter slots, import button, default route |
| [ModuleModBasedItemSinkMuiDynamic.java](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleModBasedItemSinkMuiDynamic.java) | Added | +156 / -0 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | Mod sink MUI: analyse slot, add, 9-entry removable list |
| [ModulePassiveSupplierMuiDynamic.java](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModulePassiveSupplierMuiDynamic.java) | Added | +85 / -0 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | Passive supplier MUI: filter slots |
| [ModuleProviderMuiDynamic.java](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleProviderMuiDynamic.java) | Added | +157 / -0 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | Provider MUI: 3x3 filter, extraction mode, whitelist/blacklist |
| [ModuleTerminusMuiDynamic.java](../../src/main/java/logisticspipes/gui/modularUI/dynamicModules/ModuleTerminusMuiDynamic.java) | Added | +85 / -0 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | Terminus MUI: filter slots |

## logisticspipes.gui.modularUI.pipes

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [PipeFluidBasicMui.java](../../src/main/java/logisticspipes/gui/modularUI/pipes/PipeFluidBasicMui.java) | Added | +52 / -0 | [05 · Pipe GUIs](05-modularui-gui.md#pipe-guis) | Basic fluid pipe MUI: phantom fluid filter; id copy-pasted "pipe_satellite" |
| [PipeFluidSupplierMk2Mui.java](../../src/main/java/logisticspipes/gui/modularUI/pipes/PipeFluidSupplierMk2Mui.java) | Added | +135 / -0 | [05 · Pipe GUIs](05-modularui-gui.md#pipe-guis) | Fluid supplier Mk2 MUI moved out of pipe; lost allowC2S on values |
| [PipeSatelliteMui.java](../../src/main/java/logisticspipes/gui/modularUI/pipes/PipeSatelliteMui.java) | Added | +80 / -0 | [05 · Pipe GUIs](05-modularui-gui.md#pipe-guis) | Satellite MUI: id field, next free, pattern satellite name |

## logisticspipes.gui.modularUI.pipes.patterncrafting

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [HandheldPatternMui.java](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/HandheldPatternMui.java) | Added | +87 / -0 | [01 · Pattern items & pattern types](01-pattern-crafting.md#pattern-items--pattern-types) | MUI for a held pattern item using the shared PatternEditor |
| [PatternCraftingContainer.java](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/PatternCraftingContainer.java) | Added | +61 / -0 | [01 · NEI integration](01-pattern-crafting.md#nei-integration) | MUI container implementing INEIRecipeTransfer for both pattern editors |
| [PatternCraftingSyncHandler.java](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/PatternCraftingSyncHandler.java) | Added | +234 / -0 | [01 · Pattern crafting pipe](01-pattern-crafting.md#pattern-crafting-pipe) | Pipe GUI sync: HUD/state to client; cancel, return inputs, blocking mode |
| [PatternEditor.java](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/PatternEditor.java) | Added | +490 / -0 | [01 · Pattern items & pattern types](01-pattern-crafting.md#pattern-items--pattern-types) | Shared editor widgets: entries, satellite badges/selector, type/flag buttons |
| [PatternEditorState.java](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/PatternEditorState.java) | Added | +104 / -0 | [01 · Pattern items & pattern types](01-pattern-crafting.md#pattern-items--pattern-types) | Per-side selection state of a pattern editor |
| [PatternEditorSyncHandler.java](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/PatternEditorSyncHandler.java) | Added | +309 / -0 | [01 · Pattern items & pattern types](01-pattern-crafting.md#pattern-items--pattern-types) | Editor C2S actions (select, clear, x2, type, OD/NBT, satellite, import) |
| [PatternGuiDraw.java](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/PatternGuiDraw.java) | Added | +63 / -0 | [01 · Pattern items & pattern types](01-pattern-crafting.md#pattern-items--pattern-types) | Client text helpers for slot overlays |
| [PatternIngredientSlot.java](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/PatternIngredientSlot.java) | Added | +59 / -0 | [01 · Pattern items & pattern types](01-pattern-crafting.md#pattern-items--pattern-types) | Phantom slot for a pattern entry with fluid amount and progress overlay |
| [PatternIngredientSlotSH.java](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/PatternIngredientSlotSH.java) | Added | +82 / -0 | [01 · Pattern items & pattern types](01-pattern-crafting.md#pattern-items--pattern-types) | Phantom slot handler adjusting fluid entries in mB steps |
| [PatternSelectSlot.java](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/PatternSelectSlot.java) | Added | +104 / -0 | [01 · Pattern crafting pipe](01-pattern-crafting.md#pattern-crafting-pipe) | Pattern slot: click selects for editing; renders primary result |
| [PipePatternCraftingMui.java](../../src/main/java/logisticspipes/gui/modularUI/pipes/patterncrafting/PipePatternCraftingMui.java) | Added | +299 / -0 | [01 · Pattern crafting pipe](01-pattern-crafting.md#pattern-crafting-pipe) | Pattern crafting pipe MUI: 9 pattern slots, editor, target, cancel/return, mode |

## logisticspipes.gui.modularUI.test

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [TestTile.java](../../src/main/java/logisticspipes/gui/modularUI/test/TestTile.java) | Added | +554 / -0 | [05 · Other helpers](05-modularui-gui.md#other-helpers) | Copy of MUI demo tile entity; unreferenced |

## logisticspipes.gui.orderer

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [FluidGuiOrderer.java](../../src/main/java/logisticspipes/gui/orderer/FluidGuiOrderer.java) | Modified | +49 / -3 | [02 · Fluid orderer changes (fluid request pipe GUI)](02-request-table.md#fluid-orderer-changes-fluid-request-pipe-gui) | Small buttons, Content (simulate) button, Both/Craft/Supply filter on refresh |
| [GuiRequestTable.java](../../src/main/java/logisticspipes/gui/orderer/GuiRequestTable.java) | Modified | +6 / -1 | [02 · Changes to upstream classes](02-request-table.md#changes-to-upstream-classes) | Old table GUI opens the pattern-crafting monitor popup for pattern orders |

## logisticspipes.gui.popup

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [PatternRequestMonitorPopup.java](../../src/main/java/logisticspipes/gui/popup/PatternRequestMonitorPopup.java) | Added | +178 / -0 | [01 · HUD & crafting monitor](01-pattern-crafting.md#hud--crafting-monitor) | Legacy request table popup showing a request's pattern craft progress tree |

## logisticspipes.interfaces

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [IInventoryUtil.java](../../src/main/java/logisticspipes/interfaces/IInventoryUtil.java) | Modified | +2 / -0 | [06 · Inventory handlers: IInventoryUtil.isEmpty()](06-modules-pipes-compat-build.md#inventory-handlers-iinventoryutilisempty) | New isEmpty() method for early returns |
| [IModuleInventory.java](../../src/main/java/logisticspipes/interfaces/IModuleInventory.java) | Added | +10 / -0 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | Module-owned inventory view (inventory util + transactor) |
| [IModuleInventoryOverride.java](../../src/main/java/logisticspipes/interfaces/IModuleInventoryOverride.java) | Added | +13 / -0 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | Module supplies own inventory view, e.g. GT battery slots |
| [IPipeUpgradeManager.java](../../src/main/java/logisticspipes/interfaces/IPipeUpgradeManager.java) | Modified | +4 / -0 | [05 · Upgrade sidebar and upgrade inventories](05-modularui-gui.md#upgrade-sidebar-and-upgrade-inventories) | Adds getUpgradeInventory() for MUI slots |
| [ISlotUpgradeManager.java](../../src/main/java/logisticspipes/interfaces/ISlotUpgradeManager.java) | Modified | +2 / -0 | [05 · Upgrade sidebar and upgrade inventories](05-modularui-gui.md#upgrade-sidebar-and-upgrade-inventories) | Adds hasInstantSatelliteUpgrade() |

## logisticspipes.items

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [ItemLegacyWrench.java](../../src/main/java/logisticspipes/items/ItemLegacyWrench.java) | Added | +29 / -0 | [05 · Legacy Wrench](05-modularui-gui.md#legacy-wrench) | Uncraftable debug wrench that forces the legacy GUI path |
| [ItemModule.java](../../src/main/java/logisticspipes/items/ItemModule.java) | Modified | +58 / -7 | [05 · MUI integration layer](05-modularui-gui.md#mui-integration-layer) | In-hand module MUI via IGuiHolder.buildUI; saves NBT on close |
| [ItemUpgrade.java](../../src/main/java/logisticspipes/items/ItemUpgrade.java) | Modified | +15 / -0 | [05 · Upgrade sidebar and upgrade inventories](05-modularui-gui.md#upgrade-sidebar-and-upgrade-inventories) | Registers instant satellite (27) and request table upgrades (45-48) |
| [LogisticsFluidContainer.java](../../src/main/java/logisticspipes/items/LogisticsFluidContainer.java) | Modified | +2 / -2 | [01 · Fluid crafting](01-pattern-crafting.md#fluid-crafting) | Fluid containers may now exist in inventories and in the world |
| [LogisticsSolidBlockItem.java](../../src/main/java/logisticspipes/items/LogisticsSolidBlockItem.java) | Modified | +6 / -0 | [06 · Mod init, proxies and events](06-modules-pipes-compat-build.md#mod-init-proxies-and-events) | Names and creative entries for pattern table and crafting monitor |

## logisticspipes.logistics

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [ILogisticsManager.java](../../src/main/java/logisticspipes/logistics/ILogisticsManager.java) | Modified | +6 / -0 | [03 · In-transit tracking](03-router-rework.md#in-transit-tracking) | Adds assignDefaultRouteFor: last-resort default-route sink before an item is dropped |
| [LogisticsFluidManager.java](../../src/main/java/logisticspipes/logistics/LogisticsFluidManager.java) | Modified | +9 / -15 | [03 · Fluid destination search](03-router-rework.md#fluid-destination-search) | Skips pattern crafting/satellite pipes as passive fluid sinks; reformatted conditions |
| [LogisticsManager.java](../../src/main/java/logisticspipes/logistics/LogisticsManager.java) | Modified | +68 / -10 | [03 · In-transit tracking](03-router-rework.md#in-transit-tracking) | InterestRegistry/RouterIds; marks rerouted items in transit; implements default-route fallback |

## logisticspipes.modules

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [ChassiModule.java](../../src/main/java/logisticspipes/modules/ChassiModule.java) | Modified | +25 / -4 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | Room check via sub-module inventory override; forwards insertionFailed |
| [ModuleActiveSupplier.java](../../src/main/java/logisticspipes/modules/ModuleActiveSupplier.java) | Modified | +24 / -4 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | MUI support; dummy inventory limit raised to Integer.MAX_VALUE |
| [ModuleApiaristAnalyser.java](../../src/main/java/logisticspipes/modules/ModuleApiaristAnalyser.java) | Modified | +28 / -1 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | MUI support; boolean extract-mode accessors |
| [ModuleCCBasedQuickSort.java](../../src/main/java/logisticspipes/modules/ModuleCCBasedQuickSort.java) | Modified | +3 / -2 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | Uses InterestRegistry instead of ServerRouter (router rework) |
| [ModuleCrafter.java](../../src/main/java/logisticspipes/modules/ModuleCrafter.java) | Modified | +67 / -40 | [01 · Changes to upstream classes](01-pattern-crafting.md#changes-to-upstream-classes) | Legacy crafter: fluid tanks for fluid slots, blocking packet, debug println |
| [ModuleCreativeTabBasedItemSink.java](../../src/main/java/logisticspipes/modules/ModuleCreativeTabBasedItemSink.java) | Modified | +39 / -49 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | MUI support; tab list API; drops string-based interface and HUD |
| [ModuleElectricManager.java](../../src/main/java/logisticspipes/modules/ModuleElectricManager.java) | Modified | +50 / -5 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | MUI support; GT battery slot inventory override |
| [ModuleEnchantmentSinkMK2.java](../../src/main/java/logisticspipes/modules/ModuleEnchantmentSinkMK2.java) | Modified | +21 / -2 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | MUI support |
| [ModuleExtractor.java](../../src/main/java/logisticspipes/modules/ModuleExtractor.java) | Modified | +7 / -6 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | Skips extraction scan on empty inventories |
| [ModuleItemSink.java](../../src/main/java/logisticspipes/modules/ModuleItemSink.java) | Modified | +150 / -17 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | MUI support; room-aware sinking and interests |
| [ModuleModBasedItemSink.java](../../src/main/java/logisticspipes/modules/ModuleModBasedItemSink.java) | Modified | +35 / -43 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | MUI support; mod list API; drops string-based interface and HUD |
| [ModulePassiveSupplier.java](../../src/main/java/logisticspipes/modules/ModulePassiveSupplier.java) | Modified | +21 / -2 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | MUI support |
| [ModuleProvider.java](../../src/main/java/logisticspipes/modules/ModuleProvider.java) | Modified | +67 / -4 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | MUI support; staged crafting reservations |
| [ModuleQuickSort.java](../../src/main/java/logisticspipes/modules/ModuleQuickSort.java) | Modified | +5 / -0 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | Stalls on empty special inventories |
| [ModuleTerminus.java](../../src/main/java/logisticspipes/modules/ModuleTerminus.java) | Modified | +21 / -2 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | MUI support |

## logisticspipes.modules.abstractmodules

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [LogisticsModule.java](../../src/main/java/logisticspipes/modules/abstractmodules/LogisticsModule.java) | Modified | +15 / -0 | [05 · Module GUIs](05-modularui-gui.md#module-guis) | Adds getWorld, getService and insertionFailed hook |

## logisticspipes.nei

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [LogisticsCraftingOverlayHandler.java](../../src/main/java/logisticspipes/nei/LogisticsCraftingOverlayHandler.java) | Modified | +9 / -1 | [01 · NEI integration](01-pattern-crafting.md#nei-integration) | Singleton instance; also handles the new RequestTableGui |
| [NEILogisticsPipesConfig.java](../../src/main/java/logisticspipes/nei/NEILogisticsPipesConfig.java) | Modified | +9 / -3 | [01 · NEI integration](01-pattern-crafting.md#nei-integration) | Registers overlay for RequestTableGui; shared overlay handler instance |
| [PatternCraftingRecipeTransfer.java](../../src/main/java/logisticspipes/nei/PatternCraftingRecipeTransfer.java) | Added | +43 / -0 | [01 · NEI integration](01-pattern-crafting.md#nei-integration) | Client-side NEI transfer into the selected pattern of either editor |
| [PatternRecipeImporter.java](../../src/main/java/logisticspipes/nei/PatternRecipeImporter.java) | Added | +189 / -0 | [01 · NEI integration](01-pattern-crafting.md#nei-integration) | Converts NEI recipes to crafting (3x3) or processing pattern imports |

## logisticspipes.network

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [GuiHandler.java](../../src/main/java/logisticspipes/network/GuiHandler.java) | Modified | +15 / -29 | [05 · GUI handlers and packet guards](05-modularui-gui.md#gui-handlers-and-packet-guards) | New request table GUI id; legacy basic fluid GUI removed |
| [GuiIDs.java](../../src/main/java/logisticspipes/network/GuiIDs.java) | Modified | +1 / -0 | [05 · GUI handlers and packet guards](05-modularui-gui.md#gui-handlers-and-packet-guards) | Adds GUI_New_Request_Table_ID = 39 |
| [NewGuiHandler.java](../../src/main/java/logisticspipes/network/NewGuiHandler.java) | Modified | +18 / -1 | [05 · GUI handlers and packet guards](05-modularui-gui.md#gui-handlers-and-packet-guards) | Scans crafting package; skips non-providers and client-only classes on server |
| [PacketGuards.java](../../src/main/java/logisticspipes/network/PacketGuards.java) | Added | +94 / -0 | [05 · GUI handlers and packet guards](05-modularui-gui.md#gui-handlers-and-packet-guards) | Server-side packet validation helpers (client-only, reach/security, op, open table) |
| [PacketHandler.java](../../src/main/java/logisticspipes/network/PacketHandler.java) | Modified | +6 / -0 | [05 · GUI handlers and packet guards](05-modularui-gui.md#gui-handlers-and-packet-guards) | Skips non-packet classes; clear error for unregistered packets |

## logisticspipes.network.packets.crafting.monitor

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [CraftingMonitorCancelPacket.java](../../src/main/java/logisticspipes/network/packets/crafting/monitor/CraftingMonitorCancelPacket.java) | Added | +55 / -0 | [01 · HUD & crafting monitor](01-pattern-crafting.md#hud--crafting-monitor) | C2S cancel of one instance from the Crafting Monitor block |
| [CraftingMonitorContentPacket.java](../../src/main/java/logisticspipes/network/packets/crafting/monitor/CraftingMonitorContentPacket.java) | Added | +69 / -0 | [01 · HUD & crafting monitor](01-pattern-crafting.md#hud--crafting-monitor) | S2C Crafting Monitor entry list |
| [CraftingMonitorRefreshPacket.java](../../src/main/java/logisticspipes/network/packets/crafting/monitor/CraftingMonitorRefreshPacket.java) | Added | +30 / -0 | [01 · HUD & crafting monitor](01-pattern-crafting.md#hud--crafting-monitor) | C2S request to resend Crafting Monitor entries |

## logisticspipes.network.packets.crafting.requesttable

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [RequestTableClearCraftingPacket.java](../../src/main/java/logisticspipes/network/packets/crafting/requesttable/RequestTableClearCraftingPacket.java) | Added | +32 / -0 | [02 · Crafting grid (ghost recipe, craft from storage, request ingredients)](02-request-table.md#crafting-grid-ghost-recipe-craft-from-storage-request-ingredients) | C→S: clear the ghost crafting grid of the open table |
| [RequestTableContentPacket.java](../../src/main/java/logisticspipes/network/packets/crafting/requesttable/RequestTableContentPacket.java) | Added | +93 / -0 | [02 · Combined item + fluid network list](02-request-table.md#combined-item--fluid-network-list) | S→C: network entries plus the player's display settings |
| [RequestTableDisplaySettingsPacket.java](../../src/main/java/logisticspipes/network/packets/crafting/requesttable/RequestTableDisplaySettingsPacket.java) | Added | +58 / -0 | [02 · Display settings (sort / filter, per player)](02-request-table.md#display-settings-sort--filter-per-player) | C→S: save sort/filter settings; finds table by packet coords, not open container |
| [RequestTableNetworkInteractPacket.java](../../src/main/java/logisticspipes/network/packets/crafting/requesttable/RequestTableNetworkInteractPacket.java) | Added | +80 / -0 | [02 · Internal item & fluid storage](02-request-table.md#internal-item--fluid-storage) | C→S: network-entry click moves items/fluids between cursor and internal storage |
| [RequestTableRefreshPacket.java](../../src/main/java/logisticspipes/network/packets/crafting/requesttable/RequestTableRefreshPacket.java) | Added | +145 / -0 | [02 · Combined item + fluid network list](02-request-table.md#combined-item--fluid-network-list) | C→S refresh; buildEntries merges network, internal and craftable items and fluids |
| [RequestTableRequestIngredientsPacket.java](../../src/main/java/logisticspipes/network/packets/crafting/requesttable/RequestTableRequestIngredientsPacket.java) | Added | +32 / -0 | [02 · Crafting grid (ghost recipe, craft from storage, request ingredients)](02-request-table.md#crafting-grid-ghost-recipe-craft-from-storage-request-ingredients) | C→S: request missing grid ingredients × N crafts |
| [RequestTableSendStoragePacket.java](../../src/main/java/logisticspipes/network/packets/crafting/requesttable/RequestTableSendStoragePacket.java) | Added | +36 / -0 | [02 · Internal item & fluid storage](02-request-table.md#internal-item--fluid-storage) | C→S: Send all internal items (0) or fluids (1) to network |
| [RequestTableSetCursorPacket.java](../../src/main/java/logisticspipes/network/packets/crafting/requesttable/RequestTableSetCursorPacket.java) | Added | +31 / -0 | [02 · Internal item & fluid storage](02-request-table.md#internal-item--fluid-storage) | S→C cursor stack sync; ignored on server |
| [RequestTableSubmitPacket.java](../../src/main/java/logisticspipes/network/packets/crafting/requesttable/RequestTableSubmitPacket.java) | Added | +63 / -0 | [02 · Request popup (request overlay)](02-request-table.md#request-popup-request-overlay) | C→S: request an item or fluid amount into the open table |

## logisticspipes.network.packets.debug

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [CraftingRequestDebugRequest.java](../../src/main/java/logisticspipes/network/packets/debug/CraftingRequestDebugRequest.java) | Added | +62 / -0 | [01 · Crafting debug tooling](01-pattern-crafting.md#crafting-debug-tooling) | C2S debug snapshot/clear request, operator-only (PacketGuards.isPrivileged) |
| [CraftingRequestDebugResponse.java](../../src/main/java/logisticspipes/network/packets/debug/CraftingRequestDebugResponse.java) | Added | +82 / -0 | [01 · Crafting debug tooling](01-pattern-crafting.md#crafting-debug-tooling) | S2C debug snapshot text that opens/updates the client window |

## logisticspipes.network.packets.debuggui

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [DebugDataPacket.java](../../src/main/java/logisticspipes/network/packets/debuggui/DebugDataPacket.java) | Modified | +5 / -0 | [05 · Debug GUI](05-modularui-gui.md#debug-gui) | Debug data packet now compressed |
| [DebugPanelOpen.java](../../src/main/java/logisticspipes/network/packets/debuggui/DebugPanelOpen.java) | Modified | +0 / -4 | [05 · Debug GUI](05-modularui-gui.md#debug-gui) | Panel-open packet no longer compressed |

## logisticspipes.network.packets.orderer

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [PatternCraftingHudContent.java](../../src/main/java/logisticspipes/network/packets/orderer/PatternCraftingHudContent.java) | Added | +53 / -0 | [01 · HUD & crafting monitor](01-pattern-crafting.md#hud--crafting-monitor) | S2C pattern HUD state for watching players |
| [PatternCraftingWatchPacket.java](../../src/main/java/logisticspipes/network/packets/orderer/PatternCraftingWatchPacket.java) | Added | +67 / -0 | [01 · HUD & crafting monitor](01-pattern-crafting.md#hud--crafting-monitor) | S2C monitor roots for a watched request table request |
| [RequestFluidComponentPacket.java](../../src/main/java/logisticspipes/network/packets/orderer/RequestFluidComponentPacket.java) | Added | +33 / -0 | [02 · Fluid orderer changes (fluid request pipe GUI)](02-request-table.md#fluid-orderer-changes-fluid-request-pipe-gui) | C→S: simulate a fluid request, reply with used/missing component list |
| [RequestFluidOrdererRefreshPacket.java](../../src/main/java/logisticspipes/network/packets/orderer/RequestFluidOrdererRefreshPacket.java) | Modified | +16 / -3 | [02 · Fluid orderer changes (fluid request pipe GUI)](02-request-table.md#fluid-orderer-changes-fluid-request-pipe-gui) | Now Integer2CoordinatesPacket; integer2 picks Both/Supply/Craft fluid list |

## logisticspipes.network.packets.pipe

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [CraftingBlockingModePacket.java](../../src/main/java/logisticspipes/network/packets/pipe/CraftingBlockingModePacket.java) | Added | +53 / -0 | [01 · Changes to upstream classes](01-pattern-crafting.md#changes-to-upstream-classes) | C2S blocking flag for the legacy crafting pipe (no security checks) |
| [ItemClumpPacket.java](../../src/main/java/logisticspipes/network/packets/pipe/ItemClumpPacket.java) | Added | +90 / -0 | [04 · Client sync and rendering of clumps](04-item-transport.md#client-sync-and-rendering-of-clumps) | Server-to-client clump hop: id, speed, input, per-pipe directions, up to 3 stacks |

## logisticspipes.network.packets.routingdebug

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [RoutingUpdateTargetResponse.java](../../src/main/java/logisticspipes/network/packets/routingdebug/RoutingUpdateTargetResponse.java) | Modified | +30 / -22 | [03 · Diagnostics: commands and routing debug](03-router-rework.md#diagnostics-commands-and-routing-debug) | Step debugger shows a message for non-ServerRouter routers instead of crashing with a cast error |

## logisticspipes.network.packets.satpipe

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [SatPipeNext.java](../../src/main/java/logisticspipes/network/packets/satpipe/SatPipeNext.java) | Modified | +7 / -0 | [01 · Satellites (item/fluid, routed vs instant)](01-pattern-crafting.md#satellites-itemfluid-routed-vs-instant) | Ignores pattern satellites (ids set through MUI) |
| [SatPipePrev.java](../../src/main/java/logisticspipes/network/packets/satpipe/SatPipePrev.java) | Modified | +7 / -0 | [01 · Satellites (item/fluid, routed vs instant)](01-pattern-crafting.md#satellites-itemfluid-routed-vs-instant) | Ignores pattern satellites (ids set through MUI) |
| [SatPipeSetID.java](../../src/main/java/logisticspipes/network/packets/satpipe/SatPipeSetID.java) | Modified | +8 / -0 | [01 · Satellites (item/fluid, routed vs instant)](01-pattern-crafting.md#satellites-itemfluid-routed-vs-instant) | Server ignores set-id packets for pattern satellites |

## logisticspipes.pipes

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [ISatellitePipe.java](../../src/main/java/logisticspipes/pipes/ISatellitePipe.java) | Added | +10 / -0 | [01 · Satellites (item/fluid, routed vs instant)](01-pattern-crafting.md#satellites-itemfluid-routed-vs-instant) | Common satellite id interface used by PipeSatelliteMui |
| [PipeBlockRequestTable.java](../../src/main/java/logisticspipes/pipes/PipeBlockRequestTable.java) | Modified | +45 / -13 | [02 · Changes to upstream classes](02-request-table.md#changes-to-upstream-classes) | Watch helpers also send pattern-crafting monitor trees; client pattern-watch map |
| [PipeFluidBasic.java](../../src/main/java/logisticspipes/pipes/PipeFluidBasic.java) | Modified | +21 / -11 | [06 · Fluid basic pipe: fluid filter is a phantom tank](06-modules-pipes-compat-build.md#fluid-basic-pipe-fluid-filter-is-a-phantom-tank) | Filter now FluidTank with MUI; old filter NBT not migrated |
| [PipeFluidProvider.java](../../src/main/java/logisticspipes/pipes/PipeFluidProvider.java) | Modified | +2 / -0 | [06 · Fluid provider: orders carry their target information](06-modules-pipes-compat-build.md#fluid-provider-orders-carry-their-target-information) | Forwards order target information on sent fluid containers |
| [PipeFluidSatellite.java](../../src/main/java/logisticspipes/pipes/PipeFluidSatellite.java) | Modified | +19 / -3 | [01 · Satellites (item/fluid, routed vs instant)](01-pattern-crafting.md#satellites-itemfluid-routed-vs-instant) | Implements ISatellitePipe and MUI (PipeSatelliteMui); setNextFreeId |
| [PipeFluidSupplierMk2.java](../../src/main/java/logisticspipes/pipes/PipeFluidSupplierMk2.java) | Modified | +13 / -133 | [06 · Fluid supplier Mk2: GUI moved out of the pipe](06-modules-pipes-compat-build.md#fluid-supplier-mk2-gui-moved-out-of-the-pipe) | Inline MUI moved to PipeFluidSupplierMk2Mui; fields made public |
| [PipeItemsApiaristAnalyser.java](../../src/main/java/logisticspipes/pipes/PipeItemsApiaristAnalyser.java) | Modified | +9 / -1 | [06 · Supplier and apiarist analyser pipes: module MUI](06-modules-pipes-compat-build.md#supplier-and-apiarist-analyser-pipes-module-mui) | Module MUI via IMUICompatiblePipeV2 |
| [PipeItemsBasicLogistics.java](../../src/main/java/logisticspipes/pipes/PipeItemsBasicLogistics.java) | Modified | +26 / -9 | [06 · Basic logistics pipe: no random exits, interest fixes](06-modules-pipes-compat-build.md#basic-logistics-pipe-no-random-exits-interest-fixes) | stillWantItem transport layer reroutes unwanted items; interests delegate to sink |
| [PipeItemsCraftingLogistics.java](../../src/main/java/logisticspipes/pipes/PipeItemsCraftingLogistics.java) | Modified | +31 / -1 | [01 · Changes to upstream classes](01-pattern-crafting.md#changes-to-upstream-classes) | Unfinished blocking-mode buffer stub; itemArrived forwards twice |
| [PipeItemsPatternCraftingLogistics.java](../../src/main/java/logisticspipes/pipes/PipeItemsPatternCraftingLogistics.java) | Added | +812 / -0 | [01 · Pattern crafting pipe](01-pattern-crafting.md#pattern-crafting-pipe) | Pattern crafting pipe shell: orders, fluid orders, target, sat links, HUD, MUI |
| [PipeItemsProviderLogistics.java](../../src/main/java/logisticspipes/pipes/PipeItemsProviderLogistics.java) | Modified | +80 / -40 | [06 · Provider pipe: settings moved into a ModuleProvider](06-modules-pipes-compat-build.md#provider-pipe-settings-moved-into-a-moduleprovider) | Settings in ModuleProvider (NBT-compatible), MUI, staged crafting reservations |
| [PipeItemsSatelliteLogistics.java](../../src/main/java/logisticspipes/pipes/PipeItemsSatelliteLogistics.java) | Modified | +21 / -4 | [01 · Satellites (item/fluid, routed vs instant)](01-pattern-crafting.md#satellites-itemfluid-routed-vs-instant) | Implements ISatellitePipe and MUI (PipeSatelliteMui); setNextFreeId |
| [PipeItemsSupplierLogistics.java](../../src/main/java/logisticspipes/pipes/PipeItemsSupplierLogistics.java) | Modified | +10 / -1 | [06 · Supplier and apiarist analyser pipes: module MUI](06-modules-pipes-compat-build.md#supplier-and-apiarist-analyser-pipes-module-mui) | Module MUI via IMUICompatiblePipeV2 |
| [PipeLogisticsChassi.java](../../src/main/java/logisticspipes/pipes/PipeLogisticsChassi.java) | Modified | +10 / -1 | [05 · Chassis GUI](05-modularui-gui.md#chassis-gui) | Implements IMUICompatiblePipeV2 returning ChassisGui; unused blockingMode field |
| [PipeLogisticsChassiMk5.java](../../src/main/java/logisticspipes/pipes/PipeLogisticsChassiMk5.java) | Modified | +1 / -0 | [05 · Chassis GUI](05-modularui-gui.md#chassis-gui) | Blank line only |

## logisticspipes.pipes.basic

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [CoreRoutedPipe.java](../../src/main/java/logisticspipes/pipes/basic/CoreRoutedPipe.java) | Modified | +124 / -42 | [04 · In-transit bookkeeping](04-item-transport.md#in-transit-bookkeeping) | InTransitTracker, 20-tick timeout scan, hasPipeSigns; plus queued-send tracking (01), junction debug (03), MUI wrench (05) |
| [CoreUnroutedPipe.java](../../src/main/java/logisticspipes/pipes/basic/CoreUnroutedPipe.java) | Modified | +9 / -4 | [04 · Pipe body baked into chunks (rendering performance)](04-item-transport.md#pipe-body-baked-into-chunks-rendering-performance) | Drops forceRenderOldPipe check; empty upgrade inventory for MUI (05) |
| [LogisticsBlockGenericPipe.java](../../src/main/java/logisticspipes/pipes/basic/LogisticsBlockGenericPipe.java) | Modified | +7 / -0 | [04 · Pipe breaks under a clump in flight](04-item-transport.md#pipe-breaks-under-a-clump-in-flight) | removePipe calls ClumpTransit.onPipeRemoved before the tile is removed |
| [LogisticsTileGenericPipe.java](../../src/main/java/logisticspipes/pipes/basic/LogisticsTileGenericPipe.java) | Modified | +35 / -7 | [04 · Save, chunk unload and old worlds](04-item-transport.md#save-chunk-unload-and-old-worlds) | Calls transport.onChunkUnload; pattern-crafter render/inject changes (01); IMUICompatiblePipeV2 UI (05) |

## logisticspipes.pipes.basic.fluid

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [FluidRoutedPipe.java](../../src/main/java/logisticspipes/pipes/basic/fluid/FluidRoutedPipe.java) | Modified | +4 / -0 | [04 · Changes to upstream classes](04-item-transport.md#changes-to-upstream-classes) | Protected constructor with custom fluid transport, used by pattern crafting pipe (01) |

## logisticspipes.pipes.signs

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [ItemAmountPipeSign.java](../../src/main/java/logisticspipes/pipes/signs/ItemAmountPipeSign.java) | Modified | +2 / -2 | [01 · Changes to upstream classes](01-pattern-crafting.md#changes-to-upstream-classes) | Compile fix: ServerRouter.getBiggestSimpleID moved to RouterIds |

## logisticspipes.pipes.upgrades

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [AdvancedSatelliteUpgrade.java](../../src/main/java/logisticspipes/pipes/upgrades/AdvancedSatelliteUpgrade.java) | Modified | +3 / -2 | [01 · Upgrades](01-pattern-crafting.md#upgrades) | Also allowed in pattern crafting pipes (gates satellite use there) |
| [CraftingByproductUpgrade.java](../../src/main/java/logisticspipes/pipes/upgrades/CraftingByproductUpgrade.java) | Modified | +5 / -2 | [01 · Upgrades](01-pattern-crafting.md#upgrades) | Also allowed in pattern item/fluid satellites for remote byproduct extraction |
| [FluidCraftingUpgrade.java](../../src/main/java/logisticspipes/pipes/upgrades/FluidCraftingUpgrade.java) | Modified | +3 / -2 | [01 · Upgrades](01-pattern-crafting.md#upgrades) | Also allowed in pattern crafting pipes; required for fluid patterns |
| [InstantSatelliteUpgrade.java](../../src/main/java/logisticspipes/pipes/upgrades/InstantSatelliteUpgrade.java) | Added | +34 / -0 | [01 · Upgrades](01-pattern-crafting.md#upgrades) | New upgrade (meta 27): item satellite inputs inserted directly, no routing |
| [ModuleUpgradeManager.java](../../src/main/java/logisticspipes/pipes/upgrades/ModuleUpgradeManager.java) | Modified | +14 / -2 | [05 · Upgrade sidebar and upgrade inventories](05-modularui-gui.md#upgrade-sidebar-and-upgrade-inventories) | Module upgrade slots 2 to 4; unused MUI wrapper; instant satellite |
| [UpgradeManager.java](../../src/main/java/logisticspipes/pipes/upgrades/UpgradeManager.java) | Modified | +17 / -0 | [05 · Upgrade sidebar and upgrade inventories](05-modularui-gui.md#upgrade-sidebar-and-upgrade-inventories) | MUI inventory wrapper; instant satellite upgrade flag |

## logisticspipes.proxy

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [MainProxy.java](../../src/main/java/logisticspipes/proxy/MainProxy.java) | Modified | +2 / -0 | [06 · Mod init, proxies and events](06-modules-pipes-compat-build.md#mod-init-proxies-and-events) | JunctionRoutingThread treated as server side |
| [ProxyManager.java](../../src/main/java/logisticspipes/proxy/ProxyManager.java) | Modified | +83 / -0 | [06 · GregTech electric items and battery slots (electric manager)](06-modules-pipes-compat-build.md#gregtech-electric-items-and-battery-slots-electric-manager) | Uses GTNHProxy as electric proxy when GregTech loaded; new dummy methods |

## logisticspipes.proxy.buildcraft

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [BCRenderTESR.java](../../src/main/java/logisticspipes/proxy/buildcraft/BCRenderTESR.java) | Modified | +21 / -0 | [06 · BuildCraft render proxy: skip empty TESR passes](06-modules-pipes-compat-build.md#buildcraft-render-proxy-skip-empty-tesr-passes) | hasDynamicContent: true if wires or dynamic pluggables present |

## logisticspipes.proxy.buildcraft.subproxies

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [IBCRenderTESR.java](../../src/main/java/logisticspipes/proxy/buildcraft/subproxies/IBCRenderTESR.java) | Modified | +3 / -0 | [06 · BuildCraft render proxy: skip empty TESR passes](06-modules-pipes-compat-build.md#buildcraft-render-proxy-skip-empty-tesr-passes) | New hasDynamicContent() method |

## logisticspipes.proxy.gtnh

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [GTBatterySlotInventory.java](../../src/main/java/logisticspipes/proxy/gtnh/GTBatterySlotInventory.java) | Added | +246 / -0 | [06 · GregTech electric items and battery slots (electric manager)](06-modules-pipes-compat-build.md#gregtech-electric-items-and-battery-slots-electric-manager) | IModuleInventory view of GT battery slots for electric manager |
| [GTNHProxy.java](../../src/main/java/logisticspipes/proxy/gtnh/GTNHProxy.java) | Added | +131 / -0 | [06 · GregTech electric items and battery slots (electric manager)](06-modules-pipes-compat-build.md#gregtech-electric-items-and-battery-slots-electric-manager) | GT MetaBaseItem charge checks over IC2Proxy; battery slot inventory |

## logisticspipes.proxy.ic2

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [IC2Proxy.java](../../src/main/java/logisticspipes/proxy/ic2/IC2Proxy.java) | Modified | +6 / -0 | [06 · GregTech electric items and battery slots (electric manager)](06-modules-pipes-compat-build.md#gregtech-electric-items-and-battery-slots-electric-manager) | getElectricItemInventory returns null |

## logisticspipes.proxy.interfaces

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [IIC2Proxy.java](../../src/main/java/logisticspipes/proxy/interfaces/IIC2Proxy.java) | Modified | +10 / -0 | [06 · GregTech electric items and battery slots (electric manager)](06-modules-pipes-compat-build.md#gregtech-electric-items-and-battery-slots-electric-manager) | New getElectricItemInventory(tile, side) for hidden battery slots |

## logisticspipes.proxy.side

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [ClientProxy.java](../../src/main/java/logisticspipes/proxy/side/ClientProxy.java) | Modified | +35 / -16 | [06 · Mod init, proxies and events](06-modules-pipes-compat-build.md#mod-init-proxies-and-events) | Registers new tiles; routes request answers to new request table GUI |
| [ServerProxy.java](../../src/main/java/logisticspipes/proxy/side/ServerProxy.java) | Modified | +8 / -0 | [06 · Mod init, proxies and events](06-modules-pipes-compat-build.md#mod-init-proxies-and-events) | Registers pattern table and crafting monitor tile entities |

## logisticspipes.proxy.specialinventoryhandler

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [AEInterfaceInventoryHandler.java](../../src/main/java/logisticspipes/proxy/specialinventoryhandler/AEInterfaceInventoryHandler.java) | Modified | +16 / -0 | [06 · Inventory handlers: IInventoryUtil.isEmpty()](06-modules-pipes-compat-build.md#inventory-handlers-iinventoryutilisempty) | isEmpty() built on the slot cache |
| [DSUInventoryHandler.java](../../src/main/java/logisticspipes/proxy/specialinventoryhandler/DSUInventoryHandler.java) | Modified | +1 / -1 | [06 · Inventory handlers: IInventoryUtil.isEmpty()](06-modules-pipes-compat-build.md#inventory-handlers-iinventoryutilisempty) | isEmpty() made public |
| [DSULikeInventoryHandler.java](../../src/main/java/logisticspipes/proxy/specialinventoryhandler/DSULikeInventoryHandler.java) | Modified | +1 / -1 | [06 · Inventory handlers: IInventoryUtil.isEmpty()](06-modules-pipes-compat-build.md#inventory-handlers-iinventoryutilisempty) | Abstract isEmpty() made public |
| [JABBAInventoryHandler.java](../../src/main/java/logisticspipes/proxy/specialinventoryhandler/JABBAInventoryHandler.java) | Modified | +1 / -1 | [06 · Inventory handlers: IInventoryUtil.isEmpty()](06-modules-pipes-compat-build.md#inventory-handlers-iinventoryutilisempty) | isEmpty() made public |
| [QuantumChestInventoryHandler.java](../../src/main/java/logisticspipes/proxy/specialinventoryhandler/QuantumChestInventoryHandler.java) | Modified | +1 / -1 | [06 · Inventory handlers: IInventoryUtil.isEmpty()](06-modules-pipes-compat-build.md#inventory-handlers-iinventoryutilisempty) | isEmpty() made public |
| [SpecialInventoryHandler.java](../../src/main/java/logisticspipes/proxy/specialinventoryhandler/SpecialInventoryHandler.java) | Modified | +5 / -0 | [06 · Inventory handlers: IInventoryUtil.isEmpty()](06-modules-pipes-compat-build.md#inventory-handlers-iinventoryutilisempty) | Default isEmpty() from getItemsAndCount() |
| [StorageDrawersInventoryHandler.java](../../src/main/java/logisticspipes/proxy/specialinventoryhandler/StorageDrawersInventoryHandler.java) | Modified | +15 / -0 | [06 · Inventory handlers: IInventoryUtil.isEmpty()](06-modules-pipes-compat-build.md#inventory-handlers-iinventoryutilisempty) | isEmpty() over enabled drawers with stored items |

## logisticspipes.recipes

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [RecipeManager.java](../../src/main/java/logisticspipes/recipes/RecipeManager.java) | Modified | +67 / -1 | [06 · Recipes](06-modules-pipes-compat-build.md#recipes) | Crafting monitor (two variants) and instant satellite upgrade recipes |

## logisticspipes.renderer

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [FluidContainerRenderer.java](../../src/main/java/logisticspipes/renderer/FluidContainerRenderer.java) | Modified | +37 / -0 | [01 · Fluid crafting](01-pattern-crafting.md#fluid-crafting) | Draws fluid amount (k/m/b suffix) on fluid containers in inventories |
| [LogisticsPipeWorldRenderer.java](../../src/main/java/logisticspipes/renderer/LogisticsPipeWorldRenderer.java) | Modified | +1 / -1 | [04 · Pipe body baked into chunks (rendering performance)](04-item-transport.md#pipe-body-baked-into-chunks-rendering-performance) | New renderer no longer falls back via forceRenderOldPipe |
| [LogisticsRenderPipe.java](../../src/main/java/logisticspipes/renderer/LogisticsRenderPipe.java) | Modified | +97 / -34 | [04 · Client sync and rendering of clumps](04-item-transport.md#client-sync-and-rendering-of-clumps) | TESR skipped without dynamic content; placeItem helper; trailing clump stacks drawn |
| [PatternItemRenderer.java](../../src/main/java/logisticspipes/renderer/PatternItemRenderer.java) | Added | +69 / -0 | [01 · Pattern items & pattern types](01-pattern-crafting.md#pattern-items--pattern-types) | Renders a pattern as its primary result while shift is held |
| [TravelingItemRenderer.java](../../src/main/java/logisticspipes/renderer/TravelingItemRenderer.java) | Modified | +1 / -1 | [04 · Changes to upstream classes](04-item-transport.md#changes-to-upstream-classes) | New request table item drawn at half scale too (02) |

## logisticspipes.renderer.newpipe

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [LogisticsNewPipeWorldRenderer.java](../../src/main/java/logisticspipes/renderer/newpipe/LogisticsNewPipeWorldRenderer.java) | Modified | +7 / -0 | [04 · Pipe body baked into chunks (rendering performance)](04-item-transport.md#pipe-body-baked-into-chunks-rendering-performance) | Bakes new-model pipe body into chunk mesh in render pass 0 |
| [LogisticsNewRenderPipe.java](../../src/main/java/logisticspipes/renderer/newpipe/LogisticsNewRenderPipe.java) | Modified | +22 / -78 | [04 · Pipe body baked into chunks (rendering performance)](04-item-transport.md#pipe-body-baked-into-chunks-rendering-performance) | Per-frame VBO TESR replaced by chunk-mesh renderWorldBlock |

## logisticspipes.renderer.state

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [PipeRenderState.java](../../src/main/java/logisticspipes/renderer/state/PipeRenderState.java) | Modified | +0 / -4 | [04 · Pipe body baked into chunks (rendering performance)](04-item-transport.md#pipe-body-baked-into-chunks-rendering-performance) | Removes forceRenderOldPipe, buffer and renderList fields |

## logisticspipes.request

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [BaseCraftingTemplate.java](../../src/main/java/logisticspipes/request/BaseCraftingTemplate.java) | Modified | +4 / -0 | [01 · Staged crafting & request tree integration](01-pattern-crafting.md#staged-crafting--request-tree-integration) | Exposes the raw ingredient list (getIngredients) |
| [FluidCraftingTemplate.java](../../src/main/java/logisticspipes/request/FluidCraftingTemplate.java) | Modified | +56 / -4 | [01 · Fluid crafting](01-pattern-crafting.md#fluid-crafting) | Implements item and fluid byproducts for fluid crafting templates |
| [ICraftingTemplate.java](../../src/main/java/logisticspipes/request/ICraftingTemplate.java) | Modified | +15 / -0 | [01 · Changes to upstream classes](01-pattern-crafting.md#changes-to-upstream-classes) | Javadoc only (fluid-oriented comments on generic methods) |
| [IExtraPromise.java](../../src/main/java/logisticspipes/request/IExtraPromise.java) | Modified | +6 / -0 | [01 · Changes to upstream classes](01-pattern-crafting.md#changes-to-upstream-classes) | Javadoc only on registerExtras |
| [RequestHandler.java](../../src/main/java/logisticspipes/request/RequestHandler.java) | Modified | +85 / -25 | [01 · Fluid crafting](01-pattern-crafting.md#fluid-crafting) | Fluid refresh shows craftable fluids by DisplayOptions; adds simulateFluid |
| [RequestTree.java](../../src/main/java/logisticspipes/request/RequestTree.java) | Modified | +37 / -5 | [01 · Crafting debug tooling](01-pattern-crafting.md#crafting-debug-tooling) | Records request snapshots to debug manager; fluid partial request with info |
| [RequestTreeNode.java](../../src/main/java/logisticspipes/request/RequestTreeNode.java) | Modified | +320 / -35 | [01 · Staged crafting & request tree integration](01-pattern-crafting.md#staged-crafting--request-tree-integration) | fullFillStaged branch handoff, same-item dict promises, byproduct claims, toString |

## logisticspipes.request.debug

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [CraftingRequestDebugClient.java](../../src/main/java/logisticspipes/request/debug/CraftingRequestDebugClient.java) | Added | +323 / -0 | [01 · Crafting debug tooling](01-pattern-crafting.md#crafting-debug-tooling) | Client Swing debug window opened with Ctrl+Shift+T, refreshes every second |
| [CraftingRequestDebugManager.java](../../src/main/java/logisticspipes/request/debug/CraftingRequestDebugManager.java) | Added | +738 / -0 | [01 · Crafting debug tooling](01-pattern-crafting.md#crafting-debug-tooling) | Always-on server event log (60k) and request snapshots (24) for debug dumps |

## logisticspipes.request.resources

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [DictResource.java](../../src/main/java/logisticspipes/request/resources/DictResource.java) | Modified | +9 / -3 | [01 · Staged crafting & request tree integration](01-pattern-crafting.md#staged-crafting--request-tree-integration) | New match_same_item flag (bit 4); display copies keep requester |
| [FluidResource.java](../../src/main/java/logisticspipes/request/resources/FluidResource.java) | Modified | +8 / -1 | [01 · Fluid crafting](01-pattern-crafting.md#fluid-crafting) | Display copies keep target; adds toString |
| [ItemResource.java](../../src/main/java/logisticspipes/request/resources/ItemResource.java) | Modified | +5 / -0 | [01 · Crafting debug tooling](01-pattern-crafting.md#crafting-debug-tooling) | Adds toString for debug output |

## logisticspipes.routing

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [FluidExtraPromise.java](../../src/main/java/logisticspipes/routing/FluidExtraPromise.java) | Added | +50 / -0 | [01 · Byproducts & extras](01-pattern-crafting.md#byproducts--extras) | New fluid extra/byproduct promise registering fluid EXTRA orders |
| [FluidLogisticsPromise.java](../../src/main/java/logisticspipes/routing/FluidLogisticsPromise.java) | Modified | +12 / -3 | [01 · Fluid crafting](01-pattern-crafting.md#fluid-crafting) | Fluid promises can be split into FluidExtraPromise; copyWithAmount |
| [InTransitTracker.java](../../src/main/java/logisticspipes/routing/InTransitTracker.java) | Added | +103 / -0 | [03 · In-transit tracking](03-router-rework.md#in-transit-tracking) | Indexed in-transit set replaces the PriorityBlockingQueue; O(1) add/remove, per-item counts |
| [ItemRoutingInformation.java](../../src/main/java/logisticspipes/routing/ItemRoutingInformation.java) | Modified | +44 / -0 | [01 · Persistence & NBT](01-pattern-crafting.md#persistence--nbt) | Persists PatternTargetInformation of routed items under targetInfo |
| [LogisticsDictPromise.java](../../src/main/java/logisticspipes/routing/LogisticsDictPromise.java) | Modified | +5 / -0 | [01 · Staged crafting & request tree integration](01-pattern-crafting.md#staged-crafting--request-tree-integration) | copy() now keeps the dict resource and type |
| [RouterManager.java](../../src/main/java/logisticspipes/routing/RouterManager.java) | Modified | +13 / -4 | [03 · Router lookup indexes](03-router-rework.md#router-lookup-indexes) | Client router position index and packPosition helper; O(1) lookup on load |
| [ServerRouter.java](../../src/main/java/logisticspipes/routing/ServerRouter.java) | Modified | +11 / -11 | [03 · Interest registry](03-router-rework.md#interest-registry) | Copies the interest set instead of aliasing it; cleanups; class no longer used on the server |

## logisticspipes.routing.astar

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [ChunkEdgeIndex.java](../../src/main/java/logisticspipes/routing/astar/ChunkEdgeIndex.java) | Added | +64 / -0 | [03 · Chunk unload handling](03-router-rework.md#chunk-unload-handling) | Maps chunk to the corridor edge ids through it, for re-scans on chunk unload |
| [ComponentInfo.java](../../src/main/java/logisticspipes/routing/astar/ComponentInfo.java) | Added | +43 / -0 | [03 · Graph writer and immutable snapshots](03-router-rework.md#graph-writer-and-immutable-snapshots) | Per-component identity, change stamp, improvement log, heuristic scale |
| [CorridorEdge.java](../../src/main/java/logisticspipes/routing/astar/CorridorEdge.java) | Added | +72 / -0 | [03 · Graph writer and immutable snapshots](03-router-rework.md#graph-writer-and-immutable-snapshots) | Immutable directed corridor: id, version, weight, flags, filters, chunks, travel path |
| [CorridorScanner.java](../../src/main/java/logisticspipes/routing/astar/CorridorScanner.java) | Added | +461 / -0 | [03 · Corridor scanning](03-router-rework.md#corridor-scanning) | PathFinder port finding corridors; adds chunks, real metric, travel paths |
| [EdgeSpec.java](../../src/main/java/logisticspipes/routing/astar/EdgeSpec.java) | Added | +74 / -0 | [03 · Graph writer and immutable snapshots](03-router-rework.md#graph-writer-and-immutable-snapshots) | Scanned corridor description with sameContent/improvesOn diff helpers |
| [ImprovementEvent.java](../../src/main/java/logisticspipes/routing/astar/ImprovementEvent.java) | Added | +69 / -0 | [03 · Graph writer and immutable snapshots](03-router-rework.md#graph-writer-and-immutable-snapshots) | Newest-first per-component log of edits that could shorten routes |
| [InterestRegistry.java](../../src/main/java/logisticspipes/routing/astar/InterestRegistry.java) | Added | +110 / -0 | [03 · Interest registry](03-router-rework.md#interest-registry) | Global item-interest tables moved out of ServerRouter, same behaviour |
| [JunctionGraphWriter.java](../../src/main/java/logisticspipes/routing/astar/JunctionGraphWriter.java) | Added | +1023 / -0 | [03 · Graph writer and immutable snapshots](03-router-rework.md#graph-writer-and-immutable-snapshots) | Single writer: batched edits, union-find, detour split check, improvement log, snapshot publish |
| [JunctionId.java](../../src/main/java/logisticspipes/routing/astar/JunctionId.java) | Added | +64 / -0 | [03 · Graph writer and immutable snapshots](03-router-rework.md#graph-writer-and-immutable-snapshots) | Junction identity equal to the router simple id; dense array index |
| [JunctionNode.java](../../src/main/java/logisticspipes/routing/astar/JunctionNode.java) | Added | +127 / -0 | [03 · Graph writer and immutable snapshots](03-router-rework.md#graph-writer-and-immutable-snapshots) | Immutable junction: position, outgoing corridors, active flag, payload, power data |
| [JunctionRouter.java](../../src/main/java/logisticspipes/routing/astar/JunctionRouter.java) | Added | +1214 / -0 | [03 · Junction router (IRouter adapter)](03-router-rework.md#junction-router-irouter-adapter) | Server IRouter on the junction graph: scans, publishes corridors, cached queries, power view |
| [JunctionRouterManager.java](../../src/main/java/logisticspipes/routing/astar/JunctionRouterManager.java) | Added | +171 / -0 | [03 · Junction router (IRouter adapter)](03-router-rework.md#junction-router-irouter-adapter) | RouterManager subclass creating JunctionRouters, with UUID and position indexes |
| [JunctionRoutingEngine.java](../../src/main/java/logisticspipes/routing/astar/JunctionRoutingEngine.java) | Added | +401 / -0 | [03 · Route cache, invalidation and single-flight](03-router-rework.md#route-cache-invalidation-and-single-flight) | Pair/one-to-many/sweep cache, validation, single-flight, background refresh, stats |
| [JunctionRoutingThread.java](../../src/main/java/logisticspipes/routing/astar/JunctionRoutingThread.java) | Added | +22 / -0 | [03 · Threading and configuration](03-router-rework.md#threading-and-configuration) | Daemon thread type and factory for the writer thread and refresh pool |
| [JunctionSearch.java](../../src/main/java/logisticspipes/routing/astar/JunctionSearch.java) | Added | +479 / -0 | [03 · Unified A* / Dijkstra search](03-router-rework.md#unified-a--dijkstra-search) | One per-flag A*/Dijkstra routine for one, many or all targets; low-allocation heap |
| [LPJunctionNetwork.java](../../src/main/java/logisticspipes/routing/astar/LPJunctionNetwork.java) | Added | +90 / -0 | [03 · Threading and configuration](03-router-rework.md#threading-and-configuration) | Static owner of writer and engine: thread start, cleanup, chunk-unload hook, stats |
| [NetworkGraph.java](../../src/main/java/logisticspipes/routing/astar/NetworkGraph.java) | Added | +249 / -0 | [03 · Graph writer and immutable snapshots](03-router-rework.md#graph-writer-and-immutable-snapshots) | Immutable graph snapshot: components, scaled Manhattan heuristic, hop validation |
| [PairKey.java](../../src/main/java/logisticspipes/routing/astar/PairKey.java) | Added | +39 / -0 | [03 · Route cache, invalidation and single-flight](03-router-rework.md#route-cache-invalidation-and-single-flight) | (source, destination) key for the route cache |
| [RouteCacheEntry.java](../../src/main/java/logisticspipes/routing/astar/RouteCacheEntry.java) | Added | +98 / -0 | [03 · Route cache, invalidation and single-flight](03-router-rework.md#route-cache-invalidation-and-single-flight) | Cached pair routes with per-flag settle cost and the ellipse survival test |
| [RouteLabel.java](../../src/main/java/logisticspipes/routing/astar/RouteLabel.java) | Added | +102 / -0 | [03 · Unified A* / Dijkstra search](03-router-rework.md#unified-a--dijkstra-search) | One accepted route: distance, flags, filters, parent chain, first edge |
| [RouterIds.java](../../src/main/java/logisticspipes/routing/astar/RouterIds.java) | Added | +36 / -0 | [03 · Junction router (IRouter adapter)](03-router-rework.md#junction-router-irouter-adapter) | Dense router simple-id allocator, replacing the ServerRouter statics |
| [RoutingFlags.java](../../src/main/java/logisticspipes/routing/astar/RoutingFlags.java) | Added | +44 / -0 | [03 · Unified A* / Dijkstra search](03-router-rework.md#unified-a--dijkstra-search) | Int bitmask form of PipeRoutingConnectionType for the hot path |
| [SearchResult.java](../../src/main/java/logisticspipes/routing/astar/SearchResult.java) | Added | +50 / -0 | [03 · Unified A* / Dijkstra search](03-router-rework.md#unified-a--dijkstra-search) | Search output: routes per target, settle order, unreachable targets, counters |
| [SweepEntry.java](../../src/main/java/logisticspipes/routing/astar/SweepEntry.java) | Added | +30 / -0 | [03 · Route cache, invalidation and single-flight](03-router-rework.md#route-cache-invalidation-and-single-flight) | Cached one-to-all view for getIRoutersByCost/getRouteTable; any improvement invalidates it |
| [UnionFind.java](../../src/main/java/logisticspipes/routing/astar/UnionFind.java) | Added | +81 / -0 | [03 · Graph writer and immutable snapshots](03-router-rework.md#graph-writer-and-immutable-snapshots) | Union by size, slots never reused, relabel after a split |
| [ValidatedRoutes.java](../../src/main/java/logisticspipes/routing/astar/ValidatedRoutes.java) | Added | +170 / -0 | [03 · Route cache, invalidation and single-flight](03-router-rework.md#route-cache-invalidation-and-single-flight) | Base checks: stamps, improvement log, edge versions, traversability for stale serving |

## logisticspipes.routing.order

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [LogisticsFluidOrder.java](../../src/main/java/logisticspipes/routing/order/LogisticsFluidOrder.java) | Modified | +13 / -3 | [01 · Byproducts & extras](01-pattern-crafting.md#byproducts--extras) | Allows destinationless (extra) fluid orders |
| [LogisticsFluidOrderManager.java](../../src/main/java/logisticspipes/routing/order/LogisticsFluidOrderManager.java) | Modified | +77 / -1 | [01 · Byproducts & extras](01-pattern-crafting.md#byproducts--extras) | Fluid EXTRA orders: addExtra and targeted removeExtras |
| [LogisticsItemOrderManager.java](../../src/main/java/logisticspipes/routing/order/LogisticsItemOrderManager.java) | Modified | +23 / -1 | [01 · Byproducts & extras](01-pattern-crafting.md#byproducts--extras) | getAllOrders and byproduct-target-aware removeExtras |
| [LogisticsOrder.java](../../src/main/java/logisticspipes/routing/order/LogisticsOrder.java) | Modified | +17 / -0 | [01 · Byproducts & extras](01-pattern-crafting.md#byproducts--extras) | Adds byproduct flag, byproductTarget and craftingReference fields |
| [LogisticsOrderLinkedList.java](../../src/main/java/logisticspipes/routing/order/LogisticsOrderLinkedList.java) | Modified | +8 / -0 | [01 · Cancellation](01-pattern-crafting.md#cancellation) | Adds remove(order) keeping extra index consistent |
| [LogisticsOrderManager.java](../../src/main/java/logisticspipes/routing/order/LogisticsOrderManager.java) | Modified | +9 / -0 | [01 · Cancellation](01-pattern-crafting.md#cancellation) | Adds removeOrder(order) used by cancellation |

## logisticspipes.ticks

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [LPTickHandler.java](../../src/main/java/logisticspipes/ticks/LPTickHandler.java) | Modified | +2 / -0 | [03 · Changes to upstream classes](03-router-rework.md#changes-to-upstream-classes) | Client tick calls CraftingRequestDebugClient (crafting debug, not routing) |

## logisticspipes.transport

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [ClumpTransit.java](../../src/main/java/logisticspipes/transport/ClumpTransit.java) | Added | +194 / -0 | [04 · Pipe breaks under a clump in flight](04-item-transport.md#pipe-breaks-under-a-clump-in-flight) | Chunk index of clumps in flight, break rule, broken-corridor flags |
| [EntrencsTransport.java](../../src/main/java/logisticspipes/transport/EntrencsTransport.java) | Modified | +5 / -0 | [04 · Clump arrival and fast relay](04-item-transport.md#clump-arrival-and-fast-relay) | supportsFastRelay() = false so entrances route every item |
| [ItemClump.java](../../src/main/java/logisticspipes/transport/ItemClump.java) | Added | +159 / -0 | [04 · Clump departure](04-item-transport.md#clump-departure) | Clump data, gather key, travel time, position from time, NBT |
| [LPItemList.java](../../src/main/java/logisticspipes/transport/LPItemList.java) | Modified | +4 / -0 | [04 · Pipe body baked into chunks (rendering performance)](04-item-transport.md#pipe-body-baked-into-chunks-rendering-performance) | Adds isEmpty(), used to skip the TESR |
| [LPTravelingItem.java](../../src/main/java/logisticspipes/transport/LPTravelingItem.java) | Modified | +10 / -0 | [04 · Client sync and rendering of clumps](04-item-transport.md#client-sync-and-rendering-of-clumps) | Static nextId(); client extraStacks and plannedExits |
| [PipeFluidTransportLogistics.java](../../src/main/java/logisticspipes/transport/PipeFluidTransportLogistics.java) | Modified | +5 / -0 | [04 · Clump arrival and fast relay](04-item-transport.md#clump-arrival-and-fast-relay) | supportsFastRelay() = false for fluid pipes |
| [PipeTransportLogistics.java](../../src/main/java/logisticspipes/transport/PipeTransportLogistics.java) | Modified | +528 / -10 | [04 · Clump departure](04-item-transport.md#clump-departure) | Clump depart/arrive/relay/break/NBT/packets; retry/default-route fallback (03); module insert hooks (01, 06) |
| [TransportInvConnection.java](../../src/main/java/logisticspipes/transport/TransportInvConnection.java) | Modified | +5 / -0 | [04 · Clump arrival and fast relay](04-item-transport.md#clump-arrival-and-fast-relay) | supportsFastRelay() = false for inventory system connectors |

## logisticspipes.utils

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [InventoryUtil.java](../../src/main/java/logisticspipes/utils/InventoryUtil.java) | Modified | +9 / -0 | [06 · Inventory handlers: IInventoryUtil.isEmpty()](06-modules-pipes-compat-build.md#inventory-handlers-iinventoryutilisempty) | isEmpty() raw slot scan |

## logisticspipes.utils.gui

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [DummyContainer.java](../../src/main/java/logisticspipes/utils/gui/DummyContainer.java) | Modified | +4 / -0 | [05 · Other helpers](05-modularui-gui.md#other-helpers) | Unused addFluidSlot overload on dummy inventory |

## logisticspipes.utils.item

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [ItemStackRenderer.java](../../src/main/java/logisticspipes/utils/item/ItemStackRenderer.java) | Modified | +2 / -0 | [06 · Lang, tooltips and textures](06-modules-pipes-compat-build.md#lang-tooltips-and-textures) | Enables GL_RESCALE_NORMAL to fix lighting on scaled item renders |
| [SimpleStackInventory.java](../../src/main/java/logisticspipes/utils/item/SimpleStackInventory.java) | Modified | +46 / -19 | [05 · Other helpers](05-modularui-gui.md#other-helpers) | Resizable, grows on NBT load, null-safe add helpers |

## logisticspipes.utils.string

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [StringUtils.java](../../src/main/java/logisticspipes/utils/string/StringUtils.java) | Modified | +8 / -0 | [06 · Lang, tooltips and textures](06-modules-pipes-compat-build.md#lang-tooltips-and-textures) | addShiftAction tooltip helper used by pattern tooltips |

## network.rs485.debuggui

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [DebugGuiEntry.java](../../src/main/java/network/rs485/debuggui/DebugGuiEntry.java) | Added | +762 / -0 | [05 · Debug GUI](05-modularui-gui.md#debug-gui) | Missing debug GUI implementation: reflective snapshots, Swing window |

## test: logisticspipes.routing.astar

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [JunctionBenchmarkTest.java](../../src/test/java/logisticspipes/routing/astar/JunctionBenchmarkTest.java) | Added | +415 / -0 | [03 · Tests and benchmarks](03-router-rework.md#tests-and-benchmarks) | 50k-network latency benchmarks behind benchmark-results.md; run in the normal test task |
| [JunctionGraphWriterTest.java](../../src/test/java/logisticspipes/routing/astar/JunctionGraphWriterTest.java) | Added | +277 / -0 | [03 · Tests and benchmarks](03-router-rework.md#tests-and-benchmarks) | Surgery, versions, split/detour, id reuse, chunk index, stamps, async writer |
| [JunctionPerfTest.java](../../src/test/java/logisticspipes/routing/astar/JunctionPerfTest.java) | Added | +256 / -0 | [03 · Tests and benchmarks](03-router-rework.md#tests-and-benchmarks) | Synthetic 10k/50k pipe grids; latencies and the old-router reference |
| [JunctionSearchTest.java](../../src/test/java/logisticspipes/routing/astar/JunctionSearchTest.java) | Added | +262 / -0 | [03 · Tests and benchmarks](03-router-rework.md#tests-and-benchmarks) | Search correctness, heuristic parity and admissibility, one-to-many, filters, flags |
| [RouteCacheTest.java](../../src/test/java/logisticspipes/routing/astar/RouteCacheTest.java) | Added | +429 / -0 | [03 · Tests and benchmarks](03-router-rework.md#tests-and-benchmarks) | Cache validity, ellipse test, single-flight, stale serving, random-edit fuzz test |
| [TestNetworks.java](../../src/test/java/logisticspipes/routing/astar/TestNetworks.java) | Added | +165 / -0 | [03 · Tests and benchmarks](03-router-rework.md#tests-and-benchmarks) | Helpers to build graphs through the writer, plus a reference shortest path |
| [TravelPathTest.java](../../src/test/java/logisticspipes/routing/astar/TravelPathTest.java) | Added | +88 / -0 | [03 · Tests and benchmarks](03-router-rework.md#tests-and-benchmarks) | Corridor travel paths: merge, opacity, version bump, hopStillValid |

## test: logisticspipes.transport

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [ClumpBreakTest.java](../../src/test/java/logisticspipes/transport/ClumpBreakTest.java) | Added | +96 / -0 | [04 · Pipe breaks under a clump in flight](04-item-transport.md#pipe-breaks-under-a-clump-in-flight) | Tests position from time, break rule forwards/returning, path index helpers |

## resources: assets/logisticspipes/lang

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [en_US.lang](../../src/main/resources/assets/logisticspipes/lang/en_US.lang) | Modified | +108 / -3 | [06 · Lang, tooltips and textures](06-modules-pipes-compat-build.md#lang-tooltips-and-textures) | Pattern crafting/monitor/request table/wrench strings; render-setting keys removed |
| [tootlips.txt](../../src/main/resources/assets/logisticspipes/lang/tootlips.txt) | Added | +28 / -0 | [06 · Lang, tooltips and textures](06-modules-pipes-compat-build.md#lang-tooltips-and-textures) | Unused draft of pipe/module descriptions (misspelled filename) |

## resources: assets/logisticspipes/textures/gui

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [gui_tab_right.png](../../src/main/resources/assets/logisticspipes/textures/gui/gui_tab_right.png) | Added | binary | [05 · Internal MUI reference docs (.claude/modularUI-docs)](05-modularui-gui.md#internal-mui-reference-docs-claudemodularui-docs) | Right tab texture (41x45), referenced only by unused constant |
| [module_slot_1.png](../../src/main/resources/assets/logisticspipes/textures/gui/module_slot_1.png) | Added | binary | [05 · Chassis GUI](05-modularui-gui.md#chassis-gui) | Module slot background used by chassis tabs |
| [module_slot_2.png](../../src/main/resources/assets/logisticspipes/textures/gui/module_slot_2.png) | Added | binary | [05 · Chassis GUI](05-modularui-gui.md#chassis-gui) | Module slot background variant; unused |
| [module_slot_3.png](../../src/main/resources/assets/logisticspipes/textures/gui/module_slot_3.png) | Added | binary | [05 · Chassis GUI](05-modularui-gui.md#chassis-gui) | Module slot background variant; unused |
| [upgrade_slot.png](../../src/main/resources/assets/logisticspipes/textures/gui/upgrade_slot.png) | Added | binary | [05 · Upgrade sidebar and upgrade inventories](05-modularui-gui.md#upgrade-sidebar-and-upgrade-inventories) | Upgrade slot background for the sidebar |

## resources: assets/logisticspipes/textures/items

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [legacyWrench.png](../../src/main/resources/assets/logisticspipes/textures/items/legacyWrench.png) | Added | binary | [06 · Lang, tooltips and textures](06-modules-pipes-compat-build.md#lang-tooltips-and-textures) | 32x32 Legacy Wrench item icon |

## docs

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [agent-implementation-brief.md](../../docs/router-rework/agent-implementation-brief.md) | Added | +214 / -0 | [03 · Documentation added](03-router-rework.md#documentation-added) | Original spec for the junction-graph routing engine and its acceptance tests |
| [benchmark-results.md](../../docs/router-rework/benchmark-results.md) | Added | +122 / -0 | [03 · Documentation added](03-router-rework.md#documentation-added) | 50k-pipe benchmark latencies, before/after of the edit fixes, power polling numbers |
| [bug-list.md](../../docs/bug-list.md) | Added | +372 / -0 | [06 · Project docs](06-modules-pipes-compat-build.md#project-docs) | Known bugs with stable IDs, status and repro steps |
| [code-review-guide.md](../../docs/router-rework/code-review-guide.md) | Added | +266 / -0 | [03 · Documentation added](03-router-rework.md#documentation-added) | Class map, correctness arguments, deviations, known limitations, effect of design decisions |
| [crafting-request-onboarding.md](../../docs/crafting-request-onboarding.md) | Added | +195 / -0 | [01 · Staged crafting & request tree integration](01-pattern-crafting.md#staged-crafting--request-tree-integration) | Walkthrough of RequestHandler/RequestTree/crafting template flow for new contributors |
| [DEBUG_GUI_NOTES.md](../../docs/DEBUG_GUI_NOTES.md) | Added | +28 / -0 | [01 · Crafting debug tooling](01-pattern-crafting.md#crafting-debug-tooling) | Notes on the local DebugGuiEntry replacement and debug panel byte protocol |
| [developer-guide.md](../../docs/router-rework/developer-guide.md) | Added | +276 / -0 | [03 · Documentation added](03-router-rework.md#documentation-added) | Concepts: junction compression, unified A*/Dijkstra, Manhattan heuristic, one-to-many, test strategy |
| [item-transport-rewrite.md](../../docs/item-transport-rewrite.md) | Added | +228 / -0 | [04 · Overview](04-item-transport.md#overview) | Working design notes, decisions, status and review notes of the clump transport |
| [ITEM_ROUTING.md](../../docs/ITEM_ROUTING.md) | Added | +323 / -0 | [03 · Documentation added](03-router-rework.md#documentation-added) | Write-up of item destination/next-hop/failure handling; still describes upstream ServerRouter link-state router |
| [lag-investigation.md](../../docs/lag-investigation.md) | Added | +216 / -0 | [03 · Documentation added](03-router-rework.md#documentation-added) | Lag analysis from reading code: per-hop routing cost, particles, client FPS, per-pipe idle cost (DEBUG, power view) |
| [pattern-crafting.md](../../docs/pattern-crafting.md) | Added | +469 / -0 | [01 · Pattern crafting pipe](01-pattern-crafting.md#pattern-crafting-pipe) | Reference and issue tracker (S/D/C/F/L/G ids) for pattern crafting |
| [PATTERN_CRAFTING_DEBUG.md](../../docs/PATTERN_CRAFTING_DEBUG.md) | Added | +30 / -0 | [01 · Crafting debug tooling](01-pattern-crafting.md#crafting-debug-tooling) | Describes the Ctrl+Shift+T crafting debug window and recorded event types |
| [PATTERN_CRAFTING_IPATTERNSTACK.md](../../docs/PATTERN_CRAFTING_IPATTERNSTACK.md) | Added | +80 / -0 | [01 · Pattern items & pattern types](01-pattern-crafting.md#pattern-items--pattern-types) | IPatternStack model, runtime handler split and NBT compatibility notes |
| [pipe-hibernation.md](../../docs/pipe-hibernation.md) | Added | +75 / -0 | [04 · Known gaps / discrepancies](04-item-transport.md#known-gaps--discrepancies) | Idea for idle pipes skipping ticks; explicitly not implemented |
| [rework-design-decisions.md](../../docs/rework-design-decisions.md) | Added | +104 / -0 | [06 · Project docs](06-modules-pipes-compat-build.md#project-docs) | Source-of-truth rework decisions, including old-base compatibility rules |
| [roadmap.md](../../docs/roadmap.md) | Added | +187 / -0 | [06 · Project docs](06-modules-pipes-compat-build.md#project-docs) | Phased work plan for the design decisions |
| [testing-checklist.md](../../docs/testing-checklist.md) | Added | +194 / -0 | [06 · Project docs](06-modules-pipes-compat-build.md#project-docs) | Untested changes to verify in game, grouped by area |
| [timeline1.txt](../../docs/timelines/timeline1.txt) | Added | +311 / -0 | [01 · Crafting debug tooling](01-pattern-crafting.md#crafting-debug-tooling) | Sample debug timeline dump of a staged LuV motor craft |

## .claude (internal AI/MUI reference docs)

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [animation.md](../../.claude/modularUI-docs/internal-docs/animation.md) | Added | +339 / -0 | [05 · Internal MUI reference docs (.claude/modularUI-docs)](05-modularui-gui.md#internal-mui-reference-docs-claudemodularui-docs) | ModularUI2 package reference (animation) |
| [api-core.md](../../.claude/modularUI-docs/internal-docs/api-core.md) | Added | +1904 / -0 | [05 · Internal MUI reference docs (.claude/modularUI-docs)](05-modularui-gui.md#internal-mui-reference-docs-claudemodularui-docs) | ModularUI2 package reference (api core) |
| [api-drawable-value.md](../../.claude/modularUI-docs/internal-docs/api-drawable-value.md) | Added | +800 / -0 | [05 · Internal MUI reference docs (.claude/modularUI-docs)](05-modularui-gui.md#internal-mui-reference-docs-claudemodularui-docs) | ModularUI2 package reference (api drawable/value) |
| [CHASSIS_GUI_HANDOFF.md](../../.claude/CHASSIS_GUI_HANDOFF.md) | Added | +180 / -0 | [05 · Chassis GUI](05-modularui-gui.md#chassis-gui) | Session handoff for chassis MUI rewrite: root causes, prefix fix, crash fix, open items |
| [config.md](../../.claude/modularUI-docs/internal-docs/config.md) | Added | +220 / -0 | [05 · Internal MUI reference docs (.claude/modularUI-docs)](05-modularui-gui.md#internal-mui-reference-docs-claudemodularui-docs) | ModularUI2 package reference (config) |
| [core.md](../../.claude/modularUI-docs/internal-docs/core.md) | Added | +190 / -0 | [05 · Internal MUI reference docs (.claude/modularUI-docs)](05-modularui-gui.md#internal-mui-reference-docs-claudemodularui-docs) | ModularUI2 package reference (core) |
| [drawable-core.md](../../.claude/modularUI-docs/internal-docs/drawable-core.md) | Added | +1203 / -0 | [05 · Internal MUI reference docs (.claude/modularUI-docs)](05-modularui-gui.md#internal-mui-reference-docs-claudemodularui-docs) | ModularUI2 package reference (drawable core) |
| [drawable-text.md](../../.claude/modularUI-docs/internal-docs/drawable-text.md) | Added | +755 / -0 | [05 · Internal MUI reference docs (.claude/modularUI-docs)](05-modularui-gui.md#internal-mui-reference-docs-claudemodularui-docs) | ModularUI2 package reference (drawable text) |
| [factory.md](../../.claude/modularUI-docs/internal-docs/factory.md) | Added | +845 / -0 | [05 · Internal MUI reference docs (.claude/modularUI-docs)](05-modularui-gui.md#internal-mui-reference-docs-claudemodularui-docs) | ModularUI2 package reference (GUI factories) |
| [holoui.md](../../.claude/modularUI-docs/internal-docs/holoui.md) | Added | +124 / -0 | [05 · Internal MUI reference docs (.claude/modularUI-docs)](05-modularui-gui.md#internal-mui-reference-docs-claudemodularui-docs) | ModularUI2 package reference (holoui) |
| [integration.md](../../.claude/modularUI-docs/internal-docs/integration.md) | Added | +425 / -0 | [05 · Internal MUI reference docs (.claude/modularUI-docs)](05-modularui-gui.md#internal-mui-reference-docs-claudemodularui-docs) | ModularUI2 package reference (integration) |
| [migration-status.md](../../.claude/modularUI-docs/migration-status.md) | Added | +54 / -0 | [05 · Internal MUI reference docs (.claude/modularUI-docs)](05-modularui-gui.md#internal-mui-reference-docs-claudemodularui-docs) | Module GUI migration status table; partly outdated (creative tab sink is migrated) |
| [network.md](../../.claude/modularUI-docs/internal-docs/network.md) | Added | +449 / -0 | [05 · Internal MUI reference docs (.claude/modularUI-docs)](05-modularui-gui.md#internal-mui-reference-docs-claudemodularui-docs) | ModularUI2 package reference (network) |
| [overlay.md](../../.claude/modularUI-docs/internal-docs/overlay.md) | Added | +158 / -0 | [05 · Internal MUI reference docs (.claude/modularUI-docs)](05-modularui-gui.md#internal-mui-reference-docs-claudemodularui-docs) | ModularUI2 package reference (overlay) |
| [public-api.md](../../.claude/modularUI-docs/public-api.md) | Added | +346 / -0 | [05 · Internal MUI reference docs (.claude/modularUI-docs)](05-modularui-gui.md#internal-mui-reference-docs-claudemodularui-docs) | Condensed index of the ModularUI2 API mods call |
| [root.md](../../.claude/modularUI-docs/internal-docs/root.md) | Added | +249 / -0 | [05 · Internal MUI reference docs (.claude/modularUI-docs)](05-modularui-gui.md#internal-mui-reference-docs-claudemodularui-docs) | ModularUI2 package reference (root package) |
| [screen.md](../../.claude/modularUI-docs/internal-docs/screen.md) | Added | +1533 / -0 | [05 · Internal MUI reference docs (.claude/modularUI-docs)](05-modularui-gui.md#internal-mui-reference-docs-claudemodularui-docs) | ModularUI2 package reference (screen/viewport) |
| [theme.md](../../.claude/modularUI-docs/internal-docs/theme.md) | Added | +578 / -0 | [05 · Internal MUI reference docs (.claude/modularUI-docs)](05-modularui-gui.md#internal-mui-reference-docs-claudemodularui-docs) | ModularUI2 package reference (theme) |
| [utils-core.md](../../.claude/modularUI-docs/internal-docs/utils-core.md) | Added | +1320 / -0 | [05 · Internal MUI reference docs (.claude/modularUI-docs)](05-modularui-gui.md#internal-mui-reference-docs-claudemodularui-docs) | ModularUI2 package reference (utils core) |
| [utils-sub.md](../../.claude/modularUI-docs/internal-docs/utils-sub.md) | Added | +1045 / -0 | [05 · Internal MUI reference docs (.claude/modularUI-docs)](05-modularui-gui.md#internal-mui-reference-docs-claudemodularui-docs) | ModularUI2 package reference (utils subpackages) |
| [value.md](../../.claude/modularUI-docs/internal-docs/value.md) | Added | +785 / -0 | [05 · Internal MUI reference docs (.claude/modularUI-docs)](05-modularui-gui.md#internal-mui-reference-docs-claudemodularui-docs) | ModularUI2 package reference (value/sync) |
| [widget.md](../../.claude/modularUI-docs/internal-docs/widget.md) | Added | +1206 / -0 | [05 · Internal MUI reference docs (.claude/modularUI-docs)](05-modularui-gui.md#internal-mui-reference-docs-claudemodularui-docs) | ModularUI2 package reference (widget) |
| [widgets-core.md](../../.claude/modularUI-docs/internal-docs/widgets-core.md) | Added | +1249 / -0 | [05 · Internal MUI reference docs (.claude/modularUI-docs)](05-modularui-gui.md#internal-mui-reference-docs-claudemodularui-docs) | ModularUI2 package reference (core widgets) |
| [widgets-sub.md](../../.claude/modularUI-docs/internal-docs/widgets-sub.md) | Added | +929 / -0 | [05 · Internal MUI reference docs (.claude/modularUI-docs)](05-modularui-gui.md#internal-mui-reference-docs-claudemodularui-docs) | ModularUI2 package reference (widget subpackages) |

## build & repository root

| File | Status | Lines | Feature | Summary |
|---|---|---|---|---|
| [build-artifact.yml](../../.github/workflows/build-artifact.yml) | Added | +35 / -0 | [06 · Build & dependencies](06-modules-pipes-compat-build.md#build--dependencies) | New CI: spotlessApply + assemble on Corretto 25, uploads jars |
| [dependencies.gradle](../../dependencies.gradle) | Modified | +6 / -1 | [06 · Build & dependencies](06-modules-pipes-compat-build.md#build--dependencies) | Adds NEE compileOnly, GT5 and AE2-api dev runtime deps |
| [gradle-daemon-jvm.properties](../../gradle/gradle-daemon-jvm.properties) | Modified | +10 / -10 | [06 · Build & dependencies](06-modules-pipes-compat-build.md#build--dependencies) | Regenerated foojay toolchain download URLs |
| [gradle-wrapper.properties](../../gradle/wrapper/gradle-wrapper.properties) | Modified | +1 / -1 | [06 · Build & dependencies](06-modules-pipes-compat-build.md#build--dependencies) | Gradle 9.4.0 to 9.7.1 |
| [gradle.properties](../../gradle.properties) | Modified | +5 / -0 | [06 · Build & dependencies](06-modules-pipes-compat-build.md#build--dependencies) | Adds curseForgeEnvironments = client,server |
| [settings.gradle](../../settings.gradle) | Modified | +1 / -1 | [06 · Build & dependencies](06-modules-pipes-compat-build.md#build--dependencies) | gtnhsettingsconvention 2.0.29 to 2.0.32 |
| [TODO.md](../../TODO.md) | Added | +2 / -0 | [06 · Project docs](06-modules-pipes-compat-build.md#project-docs) | Root TODO with two items: deprecated MUI references, fix chassis |
