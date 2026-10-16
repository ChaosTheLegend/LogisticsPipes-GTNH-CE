# Pipe hibernation (idea, 2026-09-30)

Status: idea only, not implemented. Nothing below has been checked against the code or profiled.

## Idea

Most pipes in a large network have nothing to do on most ticks. A pipe with nothing to do
**hibernates**: it stops running its main update loop every tick. Instead it gets an occasional
**lazy update** that only checks whether it should wake up.

Lazy-update timing doesn't matter, so the server can spread them over many ticks and run them in
batches with a fixed budget per tick. A 50k-pipe network then costs a bounded amount per tick,
not an amount proportional to the pipe count.

## When a pipe can hibernate

All of these must hold:

- no interests: it isn't a destination for anything. A full sink counts too, since full sinks now
  drop their interests.
- no open requests and no pending crafts
- no items or fluids held in the pipe or its buffers
- nothing else queued: particles, sign updates, stat updates, delayed tasks
- no timer that has to fire on time (for example extractor or supplier intervals)

## Wake-up

- **Lazy update**: re-check the conditions above. For example, a full sink sees free room again,
  or an extractor's source inventory has items again.
- **Events wake a pipe at once, without waiting for its lazy slot**:
  - an item or fluid arrives
  - a request or craft targets it
  - its configuration changes (GUI, module or upgrade change)
  - a neighbour or connection changes
  - a player interacts with it
  - the chunk reloads

## Scheduler

- One server-side queue of hibernating pipes. Each tick, process a fixed number of them, or a time
  budget, round-robin.
- The worst-case wake-up delay is (hibernating pipes ÷ batch size) ticks. That is fine for idle
  pipes. Anything that needs to be prompt should be an event.
- Waking removes the pipe from the queue. Hibernating adds it back.


## Special cases

- all pipes hibernate if they don't have an inventory/tank attached to them, exceptions - firewalls, request pipes

- Provider pipes advertise themselves and their content during lazy update, they wake up only when pulling items out
- Extractors enter hibernation when there's no items to pull and wake up when they find something
- Suppliers enter hibernation when all the conditions for the supplier are met, if they have a buffer upgrade, the buffer must be filled first before pipe enters the sleeping state
- **Hot upgrade** (decided in [rework-design-decisions.md](rework-design-decisions.md)): the pipe never hibernates, stays
  loaded and updates every tick. Works with the buffer upgrade for 0-tick hot swaps (e.g. nuclear reactors). It's an
  upgrade because the pipe then checks every tick. "Always loaded" may mean the pipe keeps its chunk loaded; not
  designed yet.
- Basic pipes/itemsinks sleep if there's no space to put items in
- Crafting pipes enter sleeping state either when there's no requests/interests OR if the pipe has been waiting for ingredients for a very long time (yet to decide), it wakes up once at least one item arrives

## Save and load

- Save hibernation state on world reload to prevent massive reloading lag for large networks

## Open questions

- **Tile ticking.** In 1.7.10 every loaded tile entity in `World.loadedTileEntityList` is still
  ticked, and that iteration costs something on its own. Either return early from `updateEntity`,
  which is cheap but not free, or take the pipe out of the ticking list while it hibernates, which
  saves more but is riskier.
- **Router work.** Which of the router's per-tick work (interest refresh, connection checks, route
  cache maintenance) can safely stop while hibernating? Which must stay event-driven so the network
  graph stays correct?
- **Client side.** Is there any client state that would go stale, such as status textures that
  depend on activity?
