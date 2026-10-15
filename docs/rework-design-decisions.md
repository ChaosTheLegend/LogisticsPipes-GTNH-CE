This file is made as a guide for the rework

## Pipes

- The only pipes that can be used for LP routing and transport are the ones from log. pipes mod, buildcraft, thermal, gregtech and other pipes Won't be supported for routing, altho GregTech pipes can be used as buffers and can accept items from LP net and send them into the net
- Pipes no longer spawn traveling item entities, instead they simulate them by teleporting items from pipe to pipe
- Pipe speed no longer controlled by upgrades, speed upgrades are gonna be reworked to instead affect extraction speed/crafting table speed

## Upgrades
- Sneaky upgrades - remove. The sneaky behaviour can be changed with GT screwdriver
- Side block upgrades - remove. Sides are connected/disconnected with shift + wrench (see Interactions)
- Speed upgrades - no longer increase item travel speed, increase extraction speed/strack size for extractor/provider modules, speeds up the pattern crafting table
- Buffer upgrade (new) - allows the pipe to hold 1 set of requested items inside itself, this skips travel time and allows the pipe to transfer items instantly when needed, applied to supplier and crafting pipes
- Nbt filter upgrade (new) - allows to filter items by nbt (enables option in crafting pipes)
- Ore dict. filter upgrade (new) - allows to filter items by ore dict. (enables option in crafting pipes)
- Adv. satellite upgrade - remove, works by default
- Crafting monitor upgrade - can now be applied to request pipes, request table and supplier pipes, allows players to watch pending crafting requests, the number of concurrent requests are limited per pipe: supplier - 1 crafting request, request pipe mk1 - 2 requests, request pipe mk2 - 4 requests, request table - 6
- Crafting upgrade (new) - allows suppliers to place crafting requests (currently this behaviour is enabled by default)
- opaque upgrade - remove, add client side config option to disable item rendering, which will just disable client router for item animations, since server already teleport items
- Hot upgrade (new) - makes sure that the pipe is always loaded and updated every tick, synergizes with buffer upgrades and allows for 0 tick hot swap (usefull for nukes)
- Energy transfer upgrades - remove, this is too OP in the context of GTNH and doesn't really fit anywhere

## Modules
- Many modules (extractor, itemsink) are duplicates of each other with upgraded functionality, remove duplicate modules, replace them with upgrades
- Modules that don't really fit the upgrades (aspect sink, beesink, polymorphic) will be interchangeable in chisel
- Crafting modules - remove, they will be replaced by pattern crafting pipe and crafting pattern respectively
- Fluid modules (new) - modules that will work exactly like fluid pipes, they will work with fluid chassis
- Satellite and fluid satellite module - same as satellite pipes
- Electric manager - allow for extraction/incertion of batteries into energy slots of machines

## GUI

- All GUIs are to be rewritten with modularUI, for the sake of extendability, better interaction and better packet handling
- Old guis for the pipes often lack controls for features outlined in this doc, they should be added when needed
- Upgrades should be insertable without pipe controller using the side upgrade gui, upgrade storage capped at 4
- Chassis upgrades are still undecided
- Add "import" button to basic fluid pipe gui - load fluid in tank into filter
- Add "default route" toggle for basic fluid pipes
- Make basic fluid pipes have more than 1 filter slots (for tanks that support more than 1 fluid)
- Add a toggle to crafting pipes to mark non-consumed items or items that use durability (tools)
- Add pending crafts on the side of request gui for requisters, remote orderers

## New pipes

- Logistics fluid chassis - same as regular chassis, but support fluid modules
- Pattern crafting pipe - new driver for autocrafting, the old crafting system is going to be deprecated
- Inv. system connector pipe - to be re-enabled and upgraded. this is the major advantage of LP allowing to connect 2 networks using *ANY* intermediate buffer (chests, ender chests, long distance pipelines, *chest minecarts*, *CARGO ROCKETS* <- wild one, and more), can be used to automate cleanroom and connect LP networks from different outposts
- Extractor pipe - same as extractor module (why don't we have this yet?)
- Fiber pipe (new) - connects subnets for power only: logistics power flows through it, items and requests don't. The subnets stay fully separate networks (the fiber pipe divides the network like obsidian pipes do). Each subnet sees the fiber as a power provider, like a power junction, so its power search ends at the fiber; drawing power from it passes the request to the subnet(s) on the other side, whose own firewall/security filters still apply. Replaces the BuildCraft diamond pipe, which did this before BC support was dropped

## Power

- Network power is pooled per island (a connected network): all power junctions in it share their power, and any pipe in the island draws from the pool. Pipes no longer look for a route to a power junction. Power transport (power supplier upgrades) is a separate topic and unchanged for now
- One-way pipes don't restrict power any more (LP has no power-passing one-way pipes; BC/TD support is dropped)
- Firewall pipes are the only exception: a firewall that blocks power splits the pool
- Firewall pipes work against most routing optimizations (their filters make routes depend on the item, and they split power pools), so they cost more than other pipes. Mark them as "use sparingly" (tooltip, docs); subnets joined by fiber pipes are the cheap way to separate networks

## Interactions

- Shift + wrench in hand shows a grid overlay over the pipe (like the one on gt pipes) and allows to connect/disconect pipes from those sides on right click, works like old disconnect upgrades
- Similarly, holding a screwdriver shows the same grid overlay and allows to configure sneaky behaviour to each connection, like sneaky upgrade

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
- Removed upgrades (sneaky, side-block/disconnection, advanced satellite, opaque) in existing pipes are migrated to the new
  behaviour where there is one (sneaky side → screwdriver setting, disconnected sides → shift+wrench setting), or keep
  working until the player replaces them.
- Saved data needs migrations, not silent drops: new NBT formats read the old ones.

## Clarifications (2026-09-29)

Answers to open questions raised while aligning the other docs with this one.

- **Legacy Wrench** - for debugging only, nothing else. It isn't craftable. It does **not** bring back deleted legacy
  GUIs; it only opens the ones that still exist.
- **Crafting monitor** - without the upgrade you only see that crafts were queued (no breakdown). With the crafting
  monitor upgrade you can inspect the whole request tree. The per-pipe request limits above apply.
- **Prestock** - is the buffer upgrade. Without it, pipes re-request items every time, which is slow.
- **Chassis upgrades** - chassis will have upgrades. Still undecided: keep per-module upgrades, or share one set of
  upgrades across the whole chassis.
- **Travel speed** - adjustable, but not through speed upgrades. Special **transport controller blocks**, similar to the
  logistics power junction, come in several tiers. The final tier makes item transport instant, which should massively
  improve performance.
- **Tesseracts / special pipe connections** - keep the explicit link for compatibility. In GTNH they will *most likely*
  be treated as chests (a buffer endpoint, like GT pipes), not as part of a route.

The work plan for these decisions is in [roadmap.md](roadmap.md).
