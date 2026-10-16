# Behaviour changes

Planned rework changes that can break or change how **existing** systems behave after the update, even with the
migrations from "Compatibility with old bases" in [rework-design-decisions.md](rework-design-decisions.md). Use this list
for the changelog and the update notes for players. Changes that a migration hides completely (the old setup behaves
the same) don't belong here.

Each entry: what changes, who is affected, and what the player has to do. Status is **planned** unless marked
**shipped** (in `dev`).

## Pipes & transport

- **Only LP pipes route and carry LP items.** BuildCraft, Thermal Dynamics and other mods' pipes no longer take part in
  LP routing. GregTech pipes only act as buffers: they can take items from the LP network and send items into it.
  - Affected: networks bridged or extended with non-LP pipes; they split into separate networks.
  - Player action: replace those segments with LP pipes (or fiber pipes for power only).
- **No traveling item entities.** Items teleport from pipe to pipe; what players see is a client-side simulation.
  - Affected: anything that reacted to the item entities in pipes (other mods' detectors, visual tricks).
  - Player action: none for normal setups.
- **Speed upgrades no longer change travel speed.** They raise extraction speed / stack size of extractor and provider
  modules and speed up the pattern crafting table. Travel speed comes from tiered transport controller blocks.
  - Affected: pipes with speed upgrades: items travel at the default speed until a transport controller is placed,
    and extractors/providers with speed upgrades extract faster than before.
  - Player action: place a transport controller where speed mattered; check extraction rates.
- **Tesseracts / special pipe connections** will most likely be treated as chests (a buffer endpoint), not as part of
  a route.
  - Affected: networks that route through tesseract links.
  - Player action: link the two sides with inventory system connectors instead.

## Sneaky / sides

- **Combined sneaky upgrade order is lost.** The upgrade tried its sneaky faces in the order of its slots. It becomes a
  set of selected faces on the pipe, tried in `ForgeDirection` order (down, up, north, south, west, east), which isn't
  configurable.
  - Affected: setups where the order mattered, e.g. insertion that relied on one face filling before another. Nobody
    is known to rely on a custom order.
  - Player action: none, unless the order mattered; then use separate pipes/modules per face.

## Upgrades

- **Upgrade storage is capped at 4 per pipe/module.**
  - Affected: pipes holding more than 4 upgrades (the cap applies after removed upgrades are migrated away).
  - **Open:** what happens to the extra ones (dropped, kept until removed, ...).
- **Energy transfer upgrades are removed** (the RF/EU power supplier upgrades that feed machines from LP power).
  - Affected: machines powered through LP pipes lose that power once the upgrades are deleted (one major pack version
    after the rework ships; until then they keep working with a removal warning).
  - Player action: power those machines another way.
- **The advanced satellite upgrade is removed; its behaviour is on by default.**
  - Affected: crafting pipes that had per-ingredient satellites configured but no upgrade installed; those satellites
    start being used.
  - Player action: clear the per-ingredient satellites that shouldn't apply.
- **The opaque upgrade is removed.** A client config option turns off item rendering instead.
  - Affected: pipes that hid their items; they render items again for players who don't set the option.
- **Suppliers need the new crafting upgrade to place crafting requests** (today they always can).
  - Affected: supplier pipes that rely on crafting to restock.
  - **Open:** whether placed suppliers get the upgrade through migration.
- **Concurrent crafting requests are limited per pipe:** supplier 1, request pipe Mk1 2, request pipe Mk2 4, request
  table 6.
  - Affected: anyone queuing more crafts at once from one pipe/table; extra requests have to wait or be split.

## Modules & crafting

- **Old crafting modules and the legacy crafting pipe are deprecated** (replaced by the pattern crafting pipe and
  crafting patterns). They keep crafting in a simple, non-blocking way and get no new features, and are deleted one
  major pack version after the rework ships.
  - Affected: every autocrafting setup built on crafting pipes/modules.
  - Player action: move to pattern crafting pipes (migration recipes turn old crafting modules into configured
    patterns).
- **Duplicate modules become a base module plus upgrades** (extractors, item sinks; the final list comes from "Rethink
  modules" in [roadmap.md](roadmap.md) 2c). Placed ones keep working or are converted.
  - Affected: to be filled in once the list is final.

## Power

- **Shipped (`dev`): network power is pooled per island.** All power junctions in a connected network share one pool,
  and every pipe draws from it; pipes no longer search for a route to a junction. Power junctions don't store power
  themselves, they add capacity to the pool. A junction touching two networks joins their pools.
  - Affected: setups that relied on routing to keep parts of a network on separate junctions.
  - Player action: split the networks with fiber pipes or power-blocking firewalls.
- **One-way pipes no longer restrict power.** Only power-blocking firewall pipes split a pool.
  - Affected: networks that used one-way pipes to separate power.
  - Player action: use a firewall that blocks power, or a fiber pipe.
- **The BuildCraft diamond pipe's power-only link is replaced by the fiber pipe** (BuildCraft support is dropped).
  - Affected: subnets joined for power through BC diamond pipes.
  - Player action: replace them with fiber pipes.
