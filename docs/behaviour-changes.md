# Behaviour changes

Planned rework changes that can break or change how **existing** systems behave after the update, even with the
migrations from "Compatibility with old bases" in [rework-design-decisions.md](rework-design-decisions.md). Use this list
for the changelog and the update notes for players. Changes that a migration hides completely (the old setup behaves
the same) don't belong here.

Each entry: what changes, who is affected, and what the player has to do. Status is **planned** unless marked
**shipped** (in `dev`) or "(in `module-host`)": done on the `module-host` branch, not in `dev` yet.

## Pipes & transport

- **Only LP pipes route and carry LP items.** BuildCraft, Thermal Dynamics and other mods' pipes no longer take part in
  LP routing. GregTech pipes only act as buffers: they can take items from the LP network and send items into it.
  - Affected: networks bridged or extended with non-LP pipes; they split into separate networks.
  - Player action: replace those segments with LP pipes (or fiber pipes for power only).
- **No traveling item entities.** Items teleport from pipe to pipe; what players see is a client-side simulation.
  - Affected: anything that reacted to the item entities in pipes (other mods' detectors, visual tricks).
  - Player action: none for normal setups.
- **Speed upgrades no longer change travel speed.** They raise extraction speed / stack size of extractor and provider
  modules and speed up the pattern crafting table. Travel speed comes from tiered transport controller blocks. Until
  those exist, speed upgrades in a pipe's own slots still set the travel speed as before.
  - Affected: pipes with speed upgrades: items travel at the default speed until a transport controller is placed,
    and extractors/providers with speed upgrades extract faster than before.
  - Player action: place a transport controller where speed mattered; check extraction rates.
- **Tesseracts / special pipe connections** will most likely be treated as chests (a buffer endpoint), not as part of
  a route.
  - Affected: networks that route through tesseract links.
  - Player action: link the two sides with inventory system connectors instead.

## Sneaky / sides

- **Sides are connected and disconnected with shift + right click of a wrench** (in `module-host`), on a 3x3 grid of
  the clicked face as on GregTech pipes: the middle is that face, an edge the side next to it, a corner the opposite
  side. The grid is drawn while sneaking with a wrench. Connection upgrades are deprecated: installed ones become
  disconnected sides when the world loads and are deleted.
  - The grid colours its cells by the side they stand for: green connected, red disconnected, yellow the working side.
  - Shift + wrench no longer turns a chassis or picks the target of a pattern crafting pipe. Their working side is
    picked with a screwdriver (any item of the ore name craftingToolScrewdriver) on the same grid, or in the settings
    window; the pattern crafting window also turns the target on a click of its icon.
  - **Settings window** (gear button in the chassis, pipe, pattern crafting and module-in-hand windows): the six sides
    as a cross (left click connects / disconnects, right click makes it the working side), the sneaky faces while a
    Sneaky Upgrade is installed and the priority while a Priority Upgrade is installed. It is rebuilt at once when
    those upgrades are put in or taken out.
  - The wrench and the screwdriver respect the security station.
  - Player action: none; turn chassis and pick crafting targets with the screwdriver or the settings window.
- **Single-module pipes use only connected sides** (in `module-host`): a side disconnected with shift + wrench is
  ignored by the provider, supplier, extractor and fluid pipes.
  - Affected: pipes whose old connection upgrades became disconnected sides; the inventory on such a side is no
    longer used.
  - Player action: reconnect the side with shift + wrench if the inventory there should be used.
- **One Sneaky Upgrade, faces in the settings window** (in `module-host`): the six sided sneaky upgrades and the
  combined one are one Sneaky Upgrade (the item damage the up one had). With it installed, the settings window (gear)
  shows a cross of the faces of the inventory next to the pipe: a click adds or removes a face, the number on it is
  its order. Several faces: items go in through the first that takes them, then the next (the combined upgrade did
  that); extraction pulls through the first that gives items; providers and fluid modules use the first face. No
  face: the face toward the pipe, as without the upgrade. It works in the pipe's slots and in a module's (item sinks,
  extractor, provider, active supplier, electric manager, fluid container supplier, crafter, the fluid modules), in a
  chassis, in a pipe with a built-in module and in hand.
  - Installed old ones turn into it with their face when they load; the first sneaky upgrade in slot order decides.
    A combined one keeps the faces of the sneaky upgrades in its slots, **in their order**; those items stay in the
    hidden slots and drop when the upgrade window closes or the pipe breaks. Old ones in an inventory craft alone
    into it; LP no longer has recipes for them.
  - The extractor and provider modules lost their own side setting, the side is the sneaky upgrade's face now. A
    module saved with a side gets a Sneaky Upgrade with that face in a free upgrade slot once it runs. If all 4 slots
    are taken (an old Extractor Mk3 or Provider Mk2 migrates with 4 speed upgrades), one speed upgrade is replaced by
    the Sneaky Upgrade: the speed drops one level, the side is kept.
  - Crafting pipes Mk1-Mk3 have their old window without the gear: their faces come from the old upgrades and can't be
    changed there yet.
  - Player action: none; an old Extractor Mk3 / Provider Mk2 with a side runs one speed level lower.

## Upgrades

- **Module upgrades belong to the module and move with it** (in `module-host`, not in `dev` yet). Taking a module out
  of a chassis takes its upgrades along in the module item; putting it into another chassis brings them. Before, they
  stayed in the chassis slot and the next module in that slot got them.
  - Affected: players who swap modules in a slot and expect the slot's upgrades to stay.
  - Migration: upgrades saved in a chassis slot go to the module of that slot on load; upgrades in a slot without a
    module stay there and the next module put into the slot takes them.
  - Player action: none.
- **Every chassis module has its own upgrade slots; the "upgrade module upgrade" is no longer needed** (in
  `module-host`). New ones can't be installed; installed ones do nothing and are deleted from the pipe on load.
  - Player action: none.
- **Removed upgrades installed in pipes are deleted, not given back.** Upgrades without a replacement
  (energy transfer, power transportation, upgrade module upgrade; opaque, see below) disappear from pipes when the
  world loads. Connection upgrades are first migrated to the pipe's side settings, then deleted the same way. Sneaky
  upgrades are not deleted, they turn into the Sneaky Upgrade (see "Sneaky / sides").
  - Affected: everyone with these upgrades in pipes; the items used for them are lost.
  - Player action: none; loose upgrade items keep existing (no recipe, a warning) until the deletion version.
- **Speed upgrades are module upgrades.** They go into the slots of extractor / provider modules, so every module on a
  chassis has its own speed. One Speed Upgrade item, one per slot, up to 4 per module (tiered speed upgrades were
  tried and dropped 2026-11-07).
  - Migration: speed upgrades already on a chassis pipe stay in its legacy slots and keep applying to every module
    until taken out.
  - Player action: none; new speed upgrades go into the modules.
- **Module upgrade slots are only in the chassis window and in the window of a single module held in hand** (in
  `module-host`). The legacy chassis GUI has none, and a held stack of several modules shows no upgrade slots.
- **A module upgrade slot holds one upgrade** (in `module-host`): a module takes as many of one upgrade as it has slots
  (4 speed upgrades fill all of them). Stacks already in a slot stay until taken out; a module counts at most 4 speed
  upgrades. Shift-click puts upgrades into the module of the open chassis tab or the module in hand.
  - Affected: old crafter modules with several fluid crafting or crafting cleanup upgrades; they need a slot each.
- **A module goes into an empty chassis slot only** (in `module-host`): to change a module, take the old one out
  first. Swapping in place lost the upgrades of the new module.
- **Breaking a pipe drops the upgrades of its built-in module** and the upgrades left in empty chassis slots (in
  `module-host`).
- **Upgrade storage is capped at 4 per pipe/module.**
  - Affected: pipes holding more than 4 upgrades (the cap applies after removed upgrades are migrated away).
  - **Open:** what happens to the extra ones (dropped, kept until removed, ...).
- **Energy transfer upgrades stop working right away** (the RF/EU power supplier upgrades that feed machines from LP
  power). They don't get the usual version of grace.
  - Affected: machines powered through LP pipes lose that power with the update.
  - Player action: power those machines another way.
- **The power transportation upgrade and the RF / IC2 power provider blocks are removed.** They only fed the energy
  transfer upgrades; LP's own power (power junctions) doesn't need them.
  - Affected: the same setups as energy transfer upgrades.
  - Player action: none beyond the above; placed provider blocks stay until the deletion version.
- **The CC remote control upgrade is removed; computers don't need it** (in `module-host`). A computer at any LP pipe
  (ComputerCraft or OpenComputers) gets the other pipes of its network with `getPipeForUUID`; pipes of other networks
  stay out of reach. Installed ones are deleted from pipes on load.
  - Affected: setups that relied on the upgrade to keep computers away from the rest of the network.
  - Player action: none.
- **The advanced satellite upgrade is removed; its behaviour is on by default.**
  - Affected: crafting pipes that had per-ingredient satellites configured but no upgrade installed; those satellites
    start being used.
  - Player action: clear the per-ingredient satellites that shouldn't apply.
- **The opaque upgrade is removed.** Installed ones are deleted from pipes on load like the ones above. A client config
  option to turn off item rendering is planned instead; it doesn't exist yet (the `OpaquePipes` option only renders
  the pipe bodies opaque).
  - Affected: pipes that hid their items; they render items again.
- **Suppliers need the new crafting upgrade to place crafting requests** (today they always can).
  - Affected: supplier pipes that rely on crafting to restock.
  - **Open:** whether placed suppliers get the upgrade through migration.
- **Concurrent crafting requests are limited per pipe:** supplier 1, request pipe Mk1 2, request pipe Mk2 4, request
  table 6.
  - Affected: anyone queuing more crafts at once from one pipe/table; extra requests have to wait or be split.

## Modules & crafting

- **The fluid extractor pipe drains through its built-in fluid extractor module** (in `module-host`). It only drains
  what a sink in the network takes (before, it filled its side buffers and then waited), pauses while nothing takes
  the fluid, and checks power before draining. Fluid other pipes push into it is routed as before.
  - Affected: setups that relied on the pipe buffering fluid while no sink had room.
  - Player action: none.
- **The provider pipe runs on its built-in provider module** (in `module-host`). Settings and saves carry over.
  - A double chest next to a provider pipe is offered as a whole (before: only the half it touches).
  - The provider Mk2 pipe sends its items in fast mode, as its module always did (before: normal).
  - A provider module in a chassis now honours firewall filters for ore dictionary requests too, as the pipe did.
  - Player action: none.
- **The fluid supplier pipe Mk1 is deprecated** (in `module-host`): it can't be crafted any more and its tooltip shows
  a removal warning. Placed ones keep working until it is deleted one major pack version later.
  - Affected: setups that stock tanks by requesting filled buckets or cells through the Mk1.
  - Player action: replace it with the fluid supplier pipe Mk2, which stocks the fluid directly.
- **The basic, supplier Mk2 and provider fluid pipes run on their built-in fluid modules** (in `module-host`), the same
  modules as in a chassis. Settings and saves carry over.
  - Basic fluid pipe: with several tanks next to it, arriving fluid fills them in a fixed side order (down, up,
    north, south, west, east) instead of the side the delivery was routed to. Fluid that doesn't fit is sent to other
    sinks at once instead of waiting at the pipe.
  - Supplier Mk2 and provider: no visible change intended (each tank is still stocked on its own).
  - Player action: none.

- **Old crafting modules and the legacy crafting pipe are deprecated** (replaced by the pattern crafting pipe and
  crafting patterns). They keep crafting in a simple, non-blocking way and get no new features, and are deleted one
  major pack version after the rework ships.
  - Affected: every autocrafting setup built on crafting pipes/modules.
  - Player action: move to pattern crafting pipes (migration recipes turn old crafting modules into configured
    patterns).
- **Duplicate modules become a base module plus upgrades** (the list is in [module-rework.md](module-rework.md)).
  Placed ones keep working or are converted.
- **One extractor module with a filter and speed upgrades** (in `module-host`). Extractor Mk2/Mk3 and the advanced
  extractors Mk1–3 are deprecated: no recipe, a removal warning, and they turn into the extractor. Every extractor has
  the 9-slot include/exclude filter of the advanced one; it starts as exclude, so an empty filter pulls everything.
  Speed upgrades (0–4) set the rate: 20 ticks / 5 items, 20 / 16, 10 / 32, 5 / 64, every tick / 64 (fast mode).
  - Migration: in a chassis an old extractor becomes the extractor when the chassis loads or it is put in, with 2 speed
    upgrades for a Mk2 and 4 for a Mk3, settings kept. A loose one converts when crafted alone.
  - Speed upgrades in the legacy slots of a chassis count for its extractors, so such a chassis extracts faster than
    before (an extractor counts at most 4).
  - The filter costs no extra energy any more; a Mk2 with 2 speed upgrades pulls 32 items every 10 ticks instead of
    every 20.
  - The basic extractor pulls by item type like the advanced one did, not slot by slot.
  - Player action: none; upgrade new extractors with speed upgrades instead of crafting Mk2/Mk3.
- **One provider module with speed upgrades** (in `module-host`). Speed upgrades (0–4) set how much it sends at once:
  8 items / 1 stack, 16 / 2, 32 / 4, 64 / 6, 128 / 8 (fast mode, the old Mk2); energy per item 1, 1, 2, 2, 2. The
  provider Mk2 module is deprecated and turns into a provider with 4 speed upgrades like the old extractors (in a
  chassis by itself, a loose one crafted alone).
  - The provider pipe Mk2 is deprecated: no recipe, a removal warning. Placed ones keep working with the speed built
    in (no upgrade items, so breaking one gives no speed upgrades).
  - Speed upgrades in the pipe slots of a provider pipe or a chassis count for its providers, so they send more at once
    than before.
  - Player action: none; put speed upgrades into a provider pipe's window instead of crafting the Mk2.
- **One tag sink instead of the mod based, creative tab, ore dictionary and GT type filter sinks** (in `module-host`).
  It takes items by one kind of tag, picked in its window: mod, creative tab, ore dictionary name or GT prefix (GT only
  with GregTech), up to 9 values, with the priority the old sink of that kind had. The mod based sink is the tag sink
  in the mod mode (same item). The other three are deprecated: no recipe, a removal warning, and they turn into a tag
  sink in their mode (in a chassis by itself, a loose one crafted alone), their lists kept.
  - The creative tab and ore dictionary sinks now hold 9 values like the others.
  - The old ore dictionary and GT type filter windows are gone; the legacy chassis GUI shows the plain string list.
  - Player action: none.
- **One enchantment sink with a filter** (in `module-host`). It takes enchanted items: any with an empty filter, else
  those whose item is in the filter; a filled filter wins over an empty one, as the Mk2 won over the Mk1. The Mk1 is
  that module with an empty filter (same item); the Mk2 is deprecated and turns into it with its filter.
  - Player action: none.
- **The terminus is an item sink setting** (in `module-host`): the item sink window has "Priority: Normal / Terminus".
  The terminus module is deprecated and turns into an item sink with the terminus priority and its filter.
  - It now also checks the target inventory's room, takes fuzzy upgrades and can be a default route, like any item
    sink.
  - Player action: none.
- **QuickSort and the fluid provider take speed upgrades** (in `module-host`): 0-4 cut the ticks between two sends
  from 6 to 4, 3, 2, 1. Speed upgrades in the legacy slots of their pipe or chassis count too.
- **New extractor pipe** (in `module-host`): a pipe with a built-in extractor module, its filter and speed upgrades in
  its window. Like the fluid extractor pipe it pulls from every inventory next to it, each getting a full extraction
  (so the throughput grows with the inventories); a side disconnected with shift + wrench is left alone. GTNH:
  assembler, basic logistics pipe + extractor module + circuit 18.
- **The basic logistics pipe, the apiarist sink pipe, the fluid basic pipe and the fluid supplier Mk2 open the window
  of their module** (in `module-host`), with its upgrade slots and the settings window (gear).
  - Player action: none.
- **The fluid extractor and fluid provider pipes have a window** (in `module-host`): the module's settings (filter,
  speed), its upgrades and the settings window (sides, priority). Before they had none.
- **New windows for old module GUIs** (in `module-host`): the thaumic aspect sink, the bee sink and the modules
  without settings (one info window, with a speed line where speed applies) have MUI windows instead of the legacy
  GUIs. The bee sink window lists only the filters in use. Every module with an MUI window also opens it when held in
  hand, with its upgrade slots.
  - Player action: none.
- **The CC based QuickSort has its own window** (in `module-host`) with its settings (timeout, sink size).
  - Player action: none.
- **The fluid extractor has a fluid filter** (in `module-host`): 9 fluid slots with whitelist / blacklist; an empty
  filter drains everything, so placed extractors work as before. From a tank with several fluids it drains the first
  one the filter allows.
- **The fluid extractor takes speed upgrades** (in `module-host`): every one doubles the fluid it drains per tank and
  operation (500 mB without, up to 8000 mB with 4) and adds 1 energy. Speed upgrades in the pipe slots of a fluid
  extractor pipe count too, so such a pipe drains faster than before.

## Priority

- **Sinks and providers have a priority, unlocked by the Priority Upgrade** (in `module-host`), -10 to 10, set in the
  settings window (gear button; "− N +", shift for steps of 5). It works only with a Priority Upgrade in the module's
  slots (item sinks, provider, fluid sinks, fluid provider; in a chassis or built into a pipe, not in a pipe's own
  slots), and the settings window shows it only then. Without the upgrade the priority is 0; the value set stays in
  the module. GTNH recipe: assembler, LV circuit + comparator + paper + circuit 18.
  - Item sinks (item, tag, enchantment, polymorphic, aspect, bee sink) and fluid sinks of the same kind take from the
    highest priority first, before distance; providers (item and fluid) are asked in priority order. A chassis
    provides with the highest priority of its provider modules.
  - Providers and chassis are now asked before the old crafting pipes when the priorities are equal (crafting pipes
    used to be asked first always).
  - Player action: none; everything starts at 0. Install a Priority Upgrade where a priority is wanted.

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
