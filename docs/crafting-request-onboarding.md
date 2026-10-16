# Crafting Request Onboarding

This document summarizes how LogisticsPipes turns a new item request into provider and crafting work. The main entry point for player, GUI, macro, and ComputerCraft requests is `src/main/java/logisticspipes/request/RequestHandler.java`.

## High-Level Flow

1. A requester asks for one or more items through `RequestHandler`.
2. `RequestHandler` spends request energy, creates a `RequestLog`, and delegates planning to `RequestTree`.
3. `RequestTree` builds a recursive tree of `RequestTreeNode`s. Each node represents one needed resource at one destination.
4. Each node first tries to satisfy itself from providers, then from already-known extras/byproducts, then from crafters.
5. Crafters expose `ICraftingTemplate`s. A template describes the crafted result, required components, byproducts, priority, and the crafter that will perform the work.
6. If the tree can be satisfied, `fullFill()` walks the tree and converts promises into real logistics orders.
7. Provider modules send existing items. Crafter modules queue crafting orders, wait for crafted output, extract it, and route it to the next destination.
8. If planning cannot satisfy the full request, the failed tree is expanded enough to report missing components back to the requester.

## Request Entry Points

`RequestHandler` is deliberately thin. It translates UI/API requests into calls on `RequestTree` and translates the result into packets or return values.

Important methods:

- `request(EntityPlayer, ItemIdentifierStack, CoreRoutedPipe)` handles a single player request. It spends 5 energy, calls `RequestTree.request(...)`, sends `MissingItems` packets, and lets `IRequestWatcher` pipes display the resulting order tree.
- `requestList(...)` and `requestMacrolist(...)` handle batches. They create one root tree with child requests for each requested stack.
- `simulate(...)` asks `RequestTree` to plan without fulfilling anything. It reports both used and missing resources through `ComponentList`.
- `computerRequest(...)` is the ComputerCraft path. It can plan with both providing and crafting, or with crafting only.
- `refresh(...)` asks the logistics managers for currently available and craftable items so the request UI can display them.
- `requestFluid(...)` follows the same pattern for fluids, using `FluidResource`.

The key point: `RequestHandler` does not decide how crafting works. It only starts planning, handles energy and user feedback, and receives success/missing callbacks through `RequestLog`.

## The Planner: RequestTree and RequestTreeNode

`RequestTree` extends `RequestTreeNode` and represents the root of a planning attempt. The root also tracks global promise totals so the same provider or crafter is not over-promised while the tree is still speculative.

`RequestTree.ActiveRequestType` controls what the planner may do:

- `Provide`: use existing stock from providers.
- `Craft`: create crafting subrequests.
- `AcceptPartial`: allow partial success.
- `SimulateOnly`: build the plan without fulfilling it.
- `LogMissing`: report missing resources.
- `LogUsed`: report resources used by a simulation.

The default request flags are `Provide` and `Craft`.

Each `RequestTreeNode` owns:

- `requestType`: the `IResource` being requested, including amount and target router.
- `subRequests`: child resource requests, usually crafting ingredients.
- `promises`: provider or crafter commitments that satisfy this node.
- `extrapromises`: overflow promises that can later satisfy other nodes.
- `byproducts`: byproducts from selected crafting templates.
- `usedCrafters`: a local ancestry guard that prevents recursive use of the same crafter template.
- `lastCrafterTried`: used to expand failed crafting attempts into useful missing-item reports.

When a node is constructed, it immediately tries to satisfy itself in this order:

1. `checkProvider()`
2. `checkExtras()`
3. `checkCrafting()`

### Providers First

`checkProvider()` finds routers interested in the requested resource through `ServerRouter.getRoutersInterestedIn(...)`, keeps reachable routes with `canRequestFrom`, sorts them by priority, load, and distance, then calls `IProvide.canProvide(...)`.

Providers add `IPromise`s to the node. A promise is not an item movement yet. It is only a commitment that the provider can later fulfill if the whole request plan succeeds.

### Extras and Byproducts

`checkExtras()` asks the root for extras that match the requested resource. Extras come from two places:

- A provider promised more than the current node needed, so `RequestTreeNode.addPromise(...)` split the surplus into an `IExtraPromise`.
- A crafter template declared byproducts, which are registered after fulfillment.

Extras are route-checked before use. This matters because an extra produced somewhere in the network still has to be able to travel to the current requester.

### Crafting

`checkCrafting()` searches the routing graph for `ICraft` pipes/modules that can create the requested resource. Each reachable crafter is asked for an `ICraftingTemplate` via `ICraft.addCrafting(IResource)`.

Crafting candidates are processed by priority. For crafters with the same priority, `CraftingSorterNode` balances work by current todo count, so a busy crafter is less likely to receive all new work when equivalent crafters exist.

For each candidate, the planner:

1. Checks that the template can craft the requested resource.
2. Applies route filters and crafting-blocking filters.
3. Calculates how many crafting sets are needed from the template result stack size.
4. Creates subrequests for every ingredient returned by `template.getComponents(nCraftingSets)`.
5. If all ingredients can be promised, adds a crafting promise for the result.
6. If not all ingredients are available, destroys the speculative children and retries with the maximum number of craftable sets.

This is why a crafting request becomes a tree: the requested item is the parent, each ingredient is a child, and any craftable ingredient can recursively create its own child ingredient requests.

## Crafting Templates

`ICraftingTemplate` is the planner-facing description of a crafting recipe. It provides:

- `getComponents(int nCraftingSets)`: ingredient resources scaled to the requested number of crafting sets.
- `getByproducts(int workSets)`: extra resources that should be registered after crafting.
- `generatePromise(int nCraftingSetsNeeded)`: creates the final crafting promise for the result.
- `getCrafter()`: points back to the `ICraft` implementation that will receive the order.
- `getPriority()`: lower/higher priority ordering is controlled by the template comparison and crafter sorting.
- `canCraft(IResource)`, `getResultResource()`, and `getResultStack()`: matching and display data.

`BaseCraftingTemplate` stores ingredients and handles scaling. `ItemCraftingTemplate` is the normal item implementation. `DictCraftingTemplate` handles fuzzy or dictionary-style matches. `FluidCraftingTemplate` is the fluid equivalent.

## Crafting Pipes and Modules

The standard crafting pipe is `PipeItemsCraftingLogistics`. It mostly delegates actual recipe and order behavior to `ModuleCrafter`.

Relevant `PipeItemsCraftingLogistics` responsibilities:

- Implements `ICraftItems`, so the planner can ask it for crafting templates.
- Owns the `LogisticsItemOrderManager` used for crafting and extra orders.
- Delegates `addCrafting(...)`, `canProvide(...)`, `fullFill(...)`, `registerExtras(...)`, and `canCraft(...)` to `ModuleCrafter`.
- Reports its current load through `getTodo()` and `getLoadFactor()`, which affects balancing.
- Updates HUD/watchers with current crafting order contents.

Relevant `ModuleCrafter` responsibilities:

- Stores the configured recipe in `_dummyInventory`.
  - Slots `0..8`: item ingredients.
  - Slot `9`: crafted result.
  - Slot `10`: byproduct result when the byproduct extractor upgrade is present.
- Stores fluid ingredients in `_liquidInventory` when fluid crafting upgrades are present.
- Builds `ItemCraftingTemplate` or `DictCraftingTemplate` in `addCrafting(...)`.
- Assigns ingredient destinations. Normal ingredients target the crafter itself; satellite and advanced satellite settings can redirect specific ingredient slots to item or fluid satellite pipes.
- Adds byproducts to the template when the byproduct extractor upgrade is installed.
- Converts accepted crafting promises into queued `LogisticsItemOrder`s in `fullFill(...)`.
- Registers extras in the order manager through `registerExtras(...)`.
- On ticks, extracts crafted output from adjacent crafting inventories and routes it to the order destination.

## Fulfillment: From Plan to Orders

The planner is speculative until `RequestTree.fullFillAll()` is called.

`RequestTreeNode.fullFill()` works bottom-up:

1. Fulfill all child nodes first.
2. For every promise on the node, call `promise.fullFill(requestType, info)`.
3. Register overflow extras and byproducts.
4. Return a `LinkedLogisticsOrderList` that mirrors the request tree for UI/HUD display.

For item promises, `LogisticsPromise.fullFill(...)` resolves the request destination from the `ItemResource` or `DictResource`, then calls the promising provider's `fullFill(...)`.

For crafting promises on `ModuleCrafter`, `fullFill(...)` adds a `ResourceType.CRAFTING` order to the crafter's `LogisticsItemOrderManager`. For extra promises, it removes the consumed extra reservation before adding or satisfying the order.

## Runtime Crafting Execution

Once a crafting order exists, `ModuleCrafter.enabledUpdateEntity()` drives the runtime side.

Every few ticks it:

1. Checks whether there are `CRAFTING` or `EXTRA` orders.
2. Locates adjacent crafters/inventories.
3. Peeks at the next crafting order with `peekAtTopRequest(ResourceType.CRAFTING, ResourceType.EXTRA)`.
4. Extracts matching output from an adjacent inventory or from `LogisticsCraftingTableTileEntity`.
5. Builds an `IRoutedItem`, sets its destination and additional target information, and queues it into the pipe network.
6. Calls `sendSuccessfull(...)` on the order manager to reduce or finish the order.
7. Defers an order if output is not currently available or the destination is temporarily buffered.

If no adjacent crafter exists while orders are pending, the order manager marks the current order failed. If reliable transport reports lost ingredients, `ModuleCrafter.itemLost(...)` records them and `tick()` later re-requests them with `RequestTree.requestPartial(...)`.

## Important Data Types

- `IResource`: a requested thing plus amount and destination router. `ItemResource`, `DictResource`, and `FluidResource` are the common implementations.
- `IPromise`: a provider or crafter commitment. Promises become real orders only during fulfillment.
- `IExtraPromise`: a promise for surplus or byproduct items that can satisfy later requests.
- `IProvide`: something that can provide existing resources.
- `ICraft`: something that can provide crafting templates and receive crafting orders.
- `IRequestItems` / `IRequestFluid`: destination interfaces used when an item/fluid cannot be sent or needs a target router.
- `LogisticsItemOrderManager`: the queue of provider, crafting, and extra orders owned by a pipe/module.
- `LinkedLogisticsOrderList`: a tree-shaped view of fulfilled orders used for request watchers and HUDs.

## Common Debugging Path

When investigating a crafting request, follow this path:

1. Start at `RequestHandler.request(...)` or the relevant batch/computer method and confirm which flags are passed to `RequestTree`.
2. Inspect the root `RequestTree` and whether the request resource has the expected target pipe/router.
3. Step through `RequestTreeNode.checkProvider()` to see whether existing stock satisfies the request before crafting is considered.
4. Step through `RequestTreeNode.checkCrafting()` and confirm that reachable `ICraft` pipes return templates.
5. In `ModuleCrafter.addCrafting(...)`, verify the configured output, ingredient slots, fuzzy flags, satellites, fluid ingredients, and byproduct upgrade.
6. If the plan succeeds but crafting does not happen, inspect `ModuleCrafter.fullFill(...)` and the pipe's `LogisticsItemOrderManager`.
7. If orders exist but nothing moves, inspect `ModuleCrafter.enabledUpdateEntity()`, adjacent crafter discovery, extraction matching, available energy, and destination sink replies.
8. If the request reports missing items, inspect `lastCrafterTried`, `recurseFailedRequestTree()`, and `buildMissingMap(...)` to understand which ingredient chain failed.

## Things to Keep in Mind

- The planner is recursive and speculative. Child nodes may be created and then destroyed while the planner probes how many crafting sets are actually possible.
- Providers and crafters are selected through routing tables, so route flags and filters are just as important as inventory contents.
- Crafting templates describe recipes; promises reserve planned work; order managers drive runtime execution. Keeping those three concepts separate makes the request system much easier to reason about.
- Crafting priority and current todo count affect how work is distributed across equivalent crafters.
- Extras and byproducts are first-class resources in the planning system, not an afterthought.
- Simulation uses the same planner but avoids fulfillment, which makes it a good way to understand what would be consumed without changing network state.
