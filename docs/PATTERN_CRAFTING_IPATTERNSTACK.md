# Pattern Crafting IPatternStack Notes

## Current shape

- `AbstractPattern` stores every input and output slot as an `IPatternStack`.
- `PatternItemStack` wraps item ingredients and results.
- `PatternFluidStack` wraps fluid ingredients and results.
- `PatternStackHelper` contains the shared matching, aggregation, copying, and display conversion helpers.

## Runtime flow

- `ModulePatternCrafting` is the lifecycle and API coordinator. It keeps persisted module state and delegates the
  complex runtime concerns to focused handlers:
  - `PatternCraftingArrivalHandler` accepts routed ingredients and resolves legacy arrivals without target metadata.
  - `PatternCraftingIngredientPlanner` owns flexible ingredient matching, satellite targets, and concrete buffer plans.
  - `PatternCraftingCapacity` calculates safe item/fluid reservations.
  - `PatternCraftingBufferDispatcher` moves complete sets into the local target and linked satellites.
- `PatternStagedCraftingCoordinator` owns staged output-order creation. It starts at
  `ModulePatternCrafting.fullFillStagedCrafting`, validates the requester target, resolves pattern metadata, registers the
  live `PatternCraftingOrder`, and links it to the monitor registry.
- `PatternStagedCraftingScheduler` owns the staged ingredient-request loop. It decides how many pattern sets can be
  requested from the captured branch state without overcommitting the local buffer or adjacent target.
- `PatternCraftingOrder` consumes branch promises for those selected sets and records local requested ingredients.
- `PatternCraftingResultExtractor` drains completed outputs in live order-manager order. If an intermediate item or
  fluid order targets the same pattern pipe, the extractor delivers it directly to `ModulePatternCrafting.itemArrived`
  instead of routing an item back to the pipe that just extracted it.
- Patterns that contain fluid inputs or fluid outputs require a Fluid Crafting Upgrade in the pattern crafting pipe.
  Without that upgrade they are not advertised as craftable, cannot sink fluid ingredients, and any running/restored
  craft for that pattern slot is cancelled.
- The pattern crafting pipe GUI exposes one cancel button per pattern slot. Cancelling removes staged and waiting output
  orders for that slot, clears requested local ingredients, and sends any buffered ingredients back into normal storage
  routing without pattern target information.
- `PatternStackBufferHandler` and `PatternStackRequestHandler` replace the old separate item/fluid handlers.
- `PatternHandler` caches immutable `PatternRecipeSnapshot` instances until the pattern inventory changes. Parsed inputs,
  outputs, fluid requirements, routing interests, and craft results therefore do not reread pattern NBT on hot paths.
- Adjacent inventory capacity and empty-state simulations, resolved ingredient target plans, upgrade flags, and
  satellite-batch refreshes are memoized for a world tick. Mutations and target changes invalidate the relevant caches.
- Fluid-upgrade validation scans all pattern slots only after a recipe change or a fluid-upgrade state transition, not
  on every module tick.
- `PatternStackRequestHandler` persists requested-but-not-yet-arrived ingredients so saved in-flight items still have
  reserved buffer space after a world restart.
- Lost ingredient retries are persisted by `ModulePatternCrafting`, including the pattern target slot when available.
- Routed items persist `PatternTargetInformation` in the transport NBT so saved send queues keep their target pattern
  slot across world stop/start. The module also falls back to the saved requested-ingredient state if an older saved
  item arrives without that target information.
- Request-tree boundaries still split by transport type:
  - `PatternItemStack` becomes `ItemResource` or item requests.
  - `PatternFluidStack` becomes `FluidResource` or fluid requests.
- Staged pattern crafting accepts `IPromise`, so fluid-output pattern crafts can create a `PatternCraftingOrder` and
  request their own ingredients when the pipe has a Fluid Crafting Upgrade.
- `PatternFluidCraftingTemplate`/`PatternFluidCraftingPromise` carry the pattern slot and result amount per set for fluid outputs.
- Pattern satellite routing supports both item and fluid inputs. Item inputs target pattern item satellites, while fluid
  inputs can target pattern fluid satellites. Inputs without a satellite assignment remain local to the pattern crafting
  pipe and are buffered before insertion into the adjacent crafting target.
- Pattern items can be either normal crafting patterns with 9 inputs and 3 outputs or processing patterns with 16 inputs
  and 4 outputs. The pattern type is stored on the pattern item and the GUI rebuilds its slot layout when that type or
  the selected pattern slot changes.

## Compatibility

- `PatternStackBufferHandler` writes the unified `patternIngredientBuffer` representation with the `IPatternStack` type marker.
- Buffer reads also accept the previous `bufferedIngredients` tag name used by earlier in-progress builds.
- `PatternStackRequestHandler` writes the same `IPatternStack` representation under `patternRequestedIngredients`.
- Lost retry entries use the same `IPatternStack` representation under `patternLostIngredients`.
- Active staged crafts are written under `patternStagedCrafting`. This includes the live output order, remaining pattern
  sets, branch promise state, extras/byproducts, and standalone extra output orders. Loading is deferred until the
  server has registered the involved routers again, then provider reservations are rebuilt from the restored branch
  state.
- Requested-but-not-yet-arrived ingredients from a restored craft are queued for a delayed retry. The retry is capped to
  the amount still listed in `patternRequestedIngredients` and skips while a restored pattern output order is already
  targeting that same pattern slot, avoiding an immediate duplicate subcraft after a world restart.
- Breaking a pattern pipe drops buffered ingredients by converting each `IPatternStack` back to concrete `ItemStack`
  instances. Item stacks are split by their max stack size; fluid stacks drop as Logistics Pipes fluid containers.
- `IPatternStack.readFromNBT` still accepts legacy item-stack-shaped entries when no explicit pattern stack type marker is present.
- `AbstractPattern` still exposes the old item/fluid convenience getters, but new crafting-system code should prefer `getInputs`, `getOutputs`, `getAggregatedInputs`, and `getAggregatedOutputs`.

## Verification notes

- `gradlew compileJava --rerun-tasks` succeeds after the crafting cleanup.
- `gradlew test` succeeds; the existing `LPBCPluggableStateTest` passes.
