# Pattern Crafting Debug View

`CraftingRequestDebugClient` is now a single live Swing window opened with `Ctrl+Shift+T`. While open, it requests a
fresh server snapshot about once per second and updates its tabs instead of opening a new static dump each time.

## Tabs

- `Overview`: generation time, snapshot/event counts, active pattern pipe count, latest event.
- `Timeline`: chronological request and flow history with event id, server tick, timestamp, category, and pipe position.
- `Requests`: captured request tree snapshots and order lists.
- `Pattern Pipes`: current live state for every registered pattern crafting pipe.
- `Raw`: the complete server payload.

## Recorded Events

The server records bounded history for the parts that matter most when diagnosing pattern crafting:

- root item/fluid request snapshots from `RequestTree`;
- staged craft creation from `PatternStagedCraftingCoordinator`;
- staged craft save/load restore attempts and restored order counts;
- staged ingredient set selection from `PatternStagedCraftingScheduler`;
- branch item/fluid requests;
- reserved local ingredients and buffer pushes into adjacent inventories;
- item/fluid arrivals at pattern crafting pipes;
- extracted craft results sent to a destination router or sent without a routed destination;
- lost ingredient retry activity.

I did not add Minecraft tick stepping. The current request/debug path has no safe pause-and-step control around the
server tick thread, and adding one here would risk changing runtime behavior. The history timeline is meant to provide
the same inspection value after or during a craft without stopping the game thread.
