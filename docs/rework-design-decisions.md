This file is made as a guide for the rework

## Pipes

- The only pipes that can be used for LP routing and transport are the ones from log. pipes mod, buildcraft, thermal, gregtech and other pipes Won't be supported for routing, altho GregTech pipes can be used as buffers and can accept items from LP net and send them into the net
- Pipes no longer spawn traveling item entities, instead they simulate them by teleporting items from pipe to pipe
- Pipe speed no longer controlled by upgrades, speed upgrades are gonna be reworked to instead affect extraction speed/crafting table speed

## Upgrades
- Sneaky upgrades - one **Sneaky Upgrade** (decided 2026-11-09) instead of the six sided ones and the combined one. With
  it installed, the settings window (gear) shows the faces it uses (see Interactions). It goes into a pipe's slots or
  a module's slots (every module that works on a side). The six sided upgrades and the combined one are deprecated
- Extractor sneaky directions - removed. The extractor and provider modules lose their own side setting
  (`LogisticsSneakyDirectionModule`, the side picker in their GUI) and use the Sneaky Upgrade like every other
  module. Old worlds: see "Compatibility with old bases"
- Side block upgrades - remove. Sides are connected/disconnected with shift + wrench (see Interactions)
- Speed upgrades - no longer increase item travel speed, increase extraction speed/strack size for extractor/provider modules, speeds up the pattern crafting table.
  They are module upgrades: they go into the slots of the extractor/provider module, so on a chassis every module has
  its own speed (decided 2026-10-30). A pipe with a built-in module shows that module's upgrade slots in its window;
  Mk1/Mk2 pipes of one kind become one pipe plus speed upgrades. A deprecated Mk2 pipe keeps its speed built in rather
  than getting upgrade items, which would drop when it is broken (decided 2026-11-05, provider pipe Mk2)
  - One Speed Upgrade item, one per module slot, up to 4 per module. Tiered speed upgrades were tried and dropped
    (decided 2026-11-07). Item travel speed in pipes still comes from the old pipe speed upgrades until transport
    controllers exist
- Priority Upgrade (new, decided 2026-11-09) - a sink's or provider's priority works only with a Priority Upgrade in
  the module's slots (item sinks, provider, fluid sinks, fluid provider; not pipe slots). The settings window shows
  the priority only then; without it the priority is 0, the value set stays in the module. GTNH recipe: assembler,
  LV circuit + comparator + paper + circuit 18
- Buffer upgrade (new) - allows the pipe to hold 1 set of requested items inside itself, this skips travel time and allows the pipe to transfer items instantly when needed, applied to supplier and crafting pipes
- Nbt filter upgrade (new) - allows to filter items by nbt (enables option in crafting pipes)
- Ore dict. filter upgrade (new) - allows to filter items by ore dict. (enables option in crafting pipes)
- Adv. satellite upgrade - remove, works by default
- Crafting monitor upgrade - can now be applied to request pipes, request table and supplier pipes, allows players to watch pending crafting requests, the number of concurrent requests are limited per pipe: supplier - 1 crafting request, request pipe mk1 - 2 requests, request pipe mk2 - 4 requests, request table - 6
- Crafting upgrade (new) - allows suppliers to place crafting requests (currently this behaviour is enabled by default)
- opaque upgrade - remove, add client side config option (planned, not there yet) to disable item rendering, which will just disable client router for item animations, since server already teleport items
- Hot upgrade (new) - makes sure that the pipe is always loaded and updated every tick, synergizes with buffer upgrades and allows for 0 tick hot swap (usefull for nukes)
- Energy transfer upgrades - remove, this is too OP in the context of GTNH and doesn't really fit anywhere. Unlike other
  deprecated content they stop working right away, not one version later (decided 2026-10-30)
- Power transportation upgrade and the RF / IC2 power provider blocks - remove (decided 2026-10-30). They only carry
  RF/EU from the power provider blocks to pipes with energy transfer upgrades; LP power (the power junction pool)
  doesn't use them
- CC remote control upgrade - remove (decided 2026-11-05). It only gated `getPipeForUUID`; a computer (ComputerCraft or
  OpenComputers) at any LP pipe now reaches every pipe of its network without it, other networks stay out of reach

## Modules
- Many modules (extractor, itemsink) are duplicates of each other with upgraded functionality, remove duplicate modules, replace them with upgrades
- Modules that don't really fit the upgrades (aspect sink, beesink, polymorphic) stay separate modules with their own
  crafting recipes; no chisel conversion (decided 2026-10-29)
- Crafting modules - remove, they will be replaced by pattern crafting pipe and crafting pattern respectively
- Fluid modules (new) - modules that will work exactly like fluid pipes, they go into the regular chassis next to the item modules
- ~~Satellite and fluid satellite module - same as satellite pipes~~ - dropped (decided 2026-11-07): satellites stay
  pipes
- Electric manager - allow for extraction/incertion of batteries into energy slots of machines
- The GTNH pack scripts (recipes, quests) are not edited by this project (decided 2026-11-06); the conversions cover
  the old items the scripts still craft

## Module host and upgrades (2026-10-29)

- **Upgrades live in the module, not in the pipe.** Every module has its own upgrade slots (4, see GUI). This settles
  the chassis upgrade question: per-module upgrades, no shared chassis set, and the "upgrade module upgrade" goes away.
  A module taken out of a chassis keeps its upgrades in its item NBT
- **Single-module pipes are hosts with one fixed module.** Sink, supplier, provider, pattern crafting,
  fluid pipes, the new extractor pipe: the pipe's upgrade slots are the slots of its built-in module, and its logic
  lives in the module (no logic duplicated between a pipe and its module, as in the provider pipe or the fluid pipes
  today)
- **Shared code is a "module host" component, not inheritance from the chassis.** The component holds the modules
  and their upgrade managers, dispatches `itemArrived` / `itemLost` / `addToBuffer` by module slot, and reads/writes
  their NBT. The chassis uses it with N slots, a single-module pipe with 1 locked slot. Any pipe can embed it,
  whatever its parent class (pattern crafting extends `FluidRoutedPipe`, not `CoreRoutedPipe` directly). Why not
  extend `PipeLogisticsChassi`: its NBT layout, its single pointed inventory, its `ChassiTargetInformation`
  assumption, all of its interfaces (`IProvideItems`, `ICraftItems`, …) and the `instanceof PipeLogisticsChassi`
  checks would all apply to every pipe at once
- **The host owns the target managers.** Today the chassis forwards its work to `ChassisItemManager` (the inventory
  and the item modules on it: interests, arrivals, providing, crafting, items that arrive without a module slot) and
  `ChassisFluidManager` (the tank and the fluid modules on it: tank side, reservations, fluid on route, fluid sink and
  provide). They move into the host and lose the chassis name (`ItemHostResourceManager`, `FluidHostResourceManager`,
  `IHostResourceManager`, package `logisticspipes.pipes.modulehost`). A host only creates the managers its pipe needs: the
  chassis both, a fluid pipe the fluid one, a sink pipe the item one
- **`IFluidModuleHost` is answered by the host.** Fluid modules ask the pipe they sit in for their tank, side,
  reservations and fluid on route; today only the chassis answers, so fluid modules only work in a chassis. A pipe
  that embeds the host implements `IFluidModuleHost` by forwarding to it, and the fluid modules work in fluid pipes
  unchanged
- **Which inventories a pipe works with stays a pipe setting.** The pipe gives the host its targets: the chassis its
  one pointed inventory or tank, single-module pipes every adjacent one, as today (provider pipe, fluid pipes). The
  managers and the fluid modules work on a list of targets, not a single one
- **Fluid pipes run on the fluid modules:** basic → `ModuleFluidSink`, extractor → `ModuleFluidExtractor`, supplier
  Mk2 → `ModuleFluidTankSupplier`, provider → `ModuleFluidProvider`. Insertion and request fluid pipes need no module
- **The fluid supplier pipe Mk1 is deprecated** (decided 2026-10-29) in favour of the Mk2. The Mk1 is an item pipe that
  requests filled containers and empties them into tanks; the Mk2 keeps tanks stocked with fluid directly. No module
  does what the Mk1 does, so it isn't moved to the host either.
- **Deprecated pipes are not moved to the host.** The legacy crafting pipes (`ModuleCrafter`) keep their current
  code until they are deleted
- **Pipe-wide upgrades** (hot, CC control) also go into module slots; the pipe has the effect if any of its modules
  holds one. The pipe itself has no upgrade slots. This is the end state: until the pipe-level upgrades have their
  replacements (shift+wrench sides, hot upgrade, ...) they stay in the pipe's own slots (decided
  2026-10-29)
- **Module upgrades move with the module** (decided 2026-10-29): taken out of a chassis, a module carries its
  upgrades in its item NBT, next to its settings; put into a chassis, it brings them
- **Pipes without a real module** that need upgrades (request pipe and request table for the crafting monitor) get
  an internal module, so the rule has no exceptions

## GUI

- All GUIs are to be rewritten with modularUI, for the sake of extendability, better interaction and better packet handling
- Old guis for the pipes often lack controls for features outlined in this doc, they should be added when needed
- Upgrades should be insertable without pipe controller using the side upgrade gui, upgrade storage capped at 4
- Chassis upgrades: per module, see "Module host and upgrades". The upgrade slots are in each module's own GUI: in a
  chassis, in a single-module pipe, and for a module held in hand (stored in the module item, active once the module
  is placed)
- Add "import" button to basic fluid pipe gui - load fluid in tank into filter
- Add "default route" toggle for basic fluid pipes
- Make basic fluid pipes have more than 1 filter slots (for tanks that support more than 1 fluid)
- Add a toggle to crafting pipes to mark non-consumed items or items that use durability (tools)
- Add pending crafts on the side of request gui for requisters, remote orderers
- The request table Mk3 height and the empty space in the chassis window are left as they are (decided 2026-11-09)
## New pipes

- ~~Logistics fluid chassis~~ - merged into the regular chassis: it takes item and fluid modules and points at an inventory, a tank or a block that is both
- Pattern crafting pipe - new driver for autocrafting, the old crafting system is going to be deprecated
- Inv. system connector pipe - to be re-enabled and upgraded. this is the major advantage of LP allowing to connect 2 networks using *ANY* intermediate buffer (chests, ender chests, long distance pipelines, *chest minecarts*, *CARGO ROCKETS* <- wild one, and more), can be used to automate cleanroom and connect LP networks from different outposts
- Extractor pipe (done 2026-11-07) - a pipe with a built-in extractor module. It pulls from every adjacent inventory,
  each getting a full extraction; sides disconnected with the wrench are skipped
- Fiber pipe (new) - connects subnets for power only: logistics power flows through it, items and requests don't. The subnets stay fully separate networks (the fiber pipe divides the network like obsidian pipes do). Each subnet sees the fiber as a power provider, like a power junction, so its power search ends at the fiber; drawing power from it passes the request to the subnet(s) on the other side, whose own firewall/security filters still apply. Replaces the BuildCraft diamond pipe, which did this before BC support was dropped

## Power

- Network power is pooled per island (a connected network): all power junctions in it share their power, and any pipe in the island draws from the pool. Pipes no longer look for a route to a power junction. Power transport (power supplier upgrades) is a separate topic and unchanged for now
- Power junctions don't store power themselves: each junction adds its capacity to its island's pool, and the pool holds the energy. Pipes only need to know their pool. A junction touching two networks joins their pools
- One-way pipes don't restrict power any more (LP has no power-passing one-way pipes; BC/TD support is dropped)
- Firewall pipes are the only exception: a firewall that blocks power splits the pool
- Firewall pipes work against most routing optimizations (their filters make routes depend on the item, and they split power pools), so they cost more than other pipes. Mark them as "use sparingly" (tooltip, docs); subnets joined by fiber pipes are the cheap way to separate networks

## Interactions

- Shift + wrench in hand shows a 3x3 grid overlay over the pipe (like the one on gt pipes) and connects/disconnects
  the side under the cursor on right click, works like old disconnect upgrades. A screwdriver on the same grid picks
  the working side (chassis direction, pattern crafting target). Both respect the security station
- Single-module pipes use only connected sides: a side disconnected with the wrench is ignored by the provider,
  supplier, extractor and fluid pipes
- Settings window (gear button in the chassis, pipe, pattern crafting and module-in-hand windows): the pipe sides as a
  cross (left click connects/disconnects, right click makes it the working side), the sneaky faces cross while a
  Sneaky Upgrade is installed, the priority while a Priority Upgrade is installed. It is rebuilt live when those
  upgrades are put in or taken out
- Sneaky faces (final design 2026-11-09) - set in the settings window, only with a Sneaky Upgrade installed
  - What "sneaky" means: the pipe always works on the block it is connected to; the sneaky face is the *face of that
    block* the pipe pretends to touch. It only matters for sided inventories (furnace, GT machines with per-side I/O),
    e.g. a pipe on the side of a furnace with sneaky face DOWN reaches only the output slot
  - The window shows a cross of the faces of the inventory next to the pipe. A click adds or removes a face, the
    number on it is its order. No face selected: the face toward the pipe, as without the upgrade
  - Several faces: items go in through the first face that takes them; extraction pulls through the first face that
    gives items; providers and fluid modules use the first face only
  - The faces are stored in the upgrade's slot: a pipe's slots or a module's slots (item sinks, extractor, provider,
    active supplier, electric manager, fluid container supplier, crafter, fluid modules), in a chassis, in a pipe
    with a built-in module and for a module in hand

## QoL

- Make blank crafting patters stack to 64, configured don't stack
- Migration recipe to convert old crafting modules into **configured** crafting patterns
- Migration recipes to convert old pipes/modules into new counterparts
- Matter manipulator interaction - be able to place pipes, copy them, remove them with matter manipulator. Configs should persist between copies
- AE requester pipe - allows AE system to request items from the pipe network by connecting proxy pipe to the interface, note that in this case, the pipe will always first pull items from the ME interface and wait for what is provided in the pattern, and only pull missing items from the internal net

## Debug/Legacy

Legacy Wrench item - made for the purpose of accessing legacy gui and debugging, in case new implementation breaks something

## Compatibility with old bases (2026-10-01)

While the whole system is being redone, existing bases must keep working after a mod update. "Remove" anywhere in this
doc means *deprecate first, delete one major pack version later*.

- **Deprecation timeline:** deprecated items, blocks and modules, and their GUIs, stay in the codebase for one extra
  major version of the pack. If the rework ships in GTNH 2.10, they still work in 2.10 with a removal warning, and are
  deleted in 2.11.
- **Deprecated module GUIs are the exception to the MUI rule:** they keep their legacy GUI until they are deleted, and
  don't get an MUI.
- Old bases shouldn't break once the mod is updated. Worlds load, and placed pipes, modules and upgrades keep working.
- Old blocks and items that are going away keep working, but show a warning that they will be removed (tooltip /
  chat / GUI, details open).
- Old crafting modules (`ModuleCrafter`, the legacy crafting pipe) keep crafting, at least in a simple, non-blocking
  way. They don't need the new features.
- Removed upgrades (side-block/disconnection, advanced satellite, opaque) in existing pipes are migrated to the new
  behaviour where there is one (disconnected sides → shift+wrench setting), and keep working until that migration
  exists.
- Removed upgrades without a new behaviour (opaque, energy transfer, power transportation, upgrade module upgrade, and
  later the migrated ones) are deleted from pipes when the world loads, not given back to the player (decided
  2026-10-30). The upgrade items themselves stay registered until the deletion version, as no recipe, a warning and no
  effect: deleting an item ID right away makes Forge remove it from every chest and ME system, and breaks the pack's
  recipe scripts and quests that name it.
- Installed sided and combined sneaky upgrades convert on load into the Sneaky Upgrade (decided 2026-11-09). The first
  sneaky upgrade in slot order decides; a combined one keeps the faces of the sneaky upgrades in its slots, in order.
  Those items stay in the hidden slots and drop when the upgrade window closes or the pipe breaks. Loose old ones
  craft alone into the Sneaky Upgrade; LP has no recipes for the old ones.
- Extractor and provider modules saved with their own side (`sneakydirection`, and the older `sneakyorientation`) get
  a Sneaky Upgrade with that face in a free upgrade slot, so they keep working on the same face. If all 4 slots are
  taken (an old Extractor Mk3 or Provider Mk2 migrates with 4 speed upgrades), one speed upgrade is replaced by the
  Sneaky Upgrade, so the speed drops one level but the side is kept (decided 2026-11-09).
- Saved data needs migrations, not silent drops: new NBT formats read the old ones.
- Moving to the module host: a single-module pipe's upgrades move into its module one to one. Upgrades installed on a
  chassis pipe itself (they apply to every module today) stay in the pipe's slots, which act as the legacy slot: they
  keep applying to all modules until the player takes them out; copying them into each module would duplicate items.
  Of the pipe-level upgrades a chassis can hold, only speed and CC remote control stayed (2026-10-30): speed moves into
  the modules, everything else is removed (see the upgrade list above; CC remote control too since 2026-11-05).
  Speed upgrades already on a chassis pipe stay in the pipe's legacy slots and keep applying to all its modules until
  the player takes them out; they are not split between the modules (decided 2026-10-30). Pipe-level upgrades are put in
  and taken out in the Logistics Pipe Controller GUI (its upgrade tab), the chassis GUI gets no pipe upgrade panel.
  Module upgrades an old chassis saved per slot go to the module of that slot; in a slot without a module they wait
  for the next module put there. Upgrades beyond the 4-slot cap
  are kept the same way, not dropped. Items already in transit when the world updates need nothing special: the
  module slot of an item (`ChassiTargetInformation`) is never saved, so after any world load items arrive without
  one and the item manager picks the module (`wantsArrival`). This is also why renaming that class breaks no save.

## Clarifications (2026-09-29)

Answers to open questions raised while aligning the other docs with this one.

- **Legacy Wrench** - for debugging only, nothing else. It isn't craftable. It does **not** bring back deleted legacy
  GUIs; it only opens the ones that still exist.
- **Crafting monitor** - without the upgrade you only see that crafts were queued (no breakdown). With the crafting
  monitor upgrade you can inspect the whole request tree. The per-pipe request limits above apply.
- **Prestock** - is the buffer upgrade. Without it, pipes re-request items every time, which is slow.
- **Chassis upgrades** - decided 2026-10-29: per module, no shared set (see "Module host and upgrades").
- **Travel speed** - adjustable, but not through speed upgrades. Special **transport controller blocks**, similar to the
  logistics power junction, come in several tiers. The final tier makes item transport instant, which should massively
  improve performance.
- **Tesseracts / special pipe connections** - keep the explicit link for compatibility. In GTNH they will *most likely*
  be treated as chests (a buffer endpoint, like GT pipes), not as part of a route.

The work plan for these decisions is in [roadmap.md](roadmap.md).
