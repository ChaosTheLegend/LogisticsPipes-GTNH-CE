# Module rework (roadmap 2c)

A table of all modules: what exists now, what is decided, what is proposed. Decisions from
[rework-design-decisions.md](rework-design-decisions.md) are marked **decided**, the rest is a **proposal** until it is
approved. The "Remove" rule everywhere means *deprecated, removed one major pack version later*.

General decisions the table relies on:

- Upgrades belong to the module, every module has 4 slots; a single pipe is a host with one built-in module
  (module host, done).
- Speed is a module upgrade (extractor, provider), not a pipe upgrade; item travel speed is set by transport
  controllers (decided 2026-10-30).
- Extractor/provider sides and the old sneaky upgrades become one Sneaky Upgrade in the module's slots; its faces are
  set in the settings window (decided 2026-11-09).
- A sink's or provider's priority works only with a Priority Upgrade in the module's slots (decided 2026-11-09).
- Polymorphic, Thaumic aspect and Bee sink stay separate modules with their own recipes, without chisel conversion
  (decided 2026-10-29).
- Crafter modules are deprecated, pattern crafting replaces them (decided).
- In GTNH module recipes are set by the pack scripts (LP in the GTNH branch only has assembler recipes for the fluid
  modules and the reset recipes). We do not edit the pack scripts as part of this rework (decided 2026-11-06): the fact
  that the scripts keep crafting the old modules is covered by the conversion (on a chassis by itself, a separate item
  by crafting it alone).

## Extractors

Now (numbers from the code, per operation):

| Module | id | Ticks between operations | Items per operation | Energy per item | Send mode | Notes |
|---|---|---|---|---|---|---|
| Extractor | 3 | 20 | 5 | 5 | Normal | sneaky direction in the GUI |
| Extractor Mk2 | 103 | 20 | 32 | 7 | Normal | — |
| Extractor Mk3 | 203 | 1 | 64 | 10 | Fast | — |
| Advanced Extractor | 7 | 20 | 5 | 6 | Normal | 9-slot filter, include/exclude; code is copied, does not extend Extractor |
| Advanced Extractor Mk2 | 107 | 20 | 32 | 8 | Normal | — |
| Advanced Extractor Mk3 | 207 | 1 | 64 | 11 | Fast | — |
| Fluid Extractor | 701 | 10 | 500 mB from each tank | 5 per tank | Passive | no filter; a new module of this rework |

No extractor accepts upgrades right now.

**Proposal:**

- One **Extractor** module. Six classes merge into one:
  - the 9-slot filter with include/exclude from Advanced; an empty filter means "everything";
  - speed is set by the number of speed upgrades (0–4), curve (decided 2026-11-05):

    | Speed | Ticks | Items | Energy per item |
    |---|---|---|---|
    | 0 | 20 | 5 | 5 |
    | 1 | 20 | 16 | 6 |
    | 2 | 10 | 32 | 7 |
    | 3 | 5 | 64 | 8 |
    | 4 | 1 | 64 | 10 |

  - the extra cost for the filter (+1 energy on Advanced) goes away: the filter is free.
- **Migration:** Mk2/Mk3/Advanced* load as Extractor with the matching settings. Advanced carries over the filter and
  include/exclude; a saved side becomes a Sneaky Upgrade with that face (if the 4 slots are full, it replaces one
  speed upgrade, decided 2026-11-09).
- **Fluid Extractor** stays separate (fluid path) and also accepts speed: each speed doubles the mB per
  operation (500 → 8000 mB) and adds 1 energy.
- The filter of the new Extractor defaults to exclude mode: an empty filter pulls everything, old plain extractors
  load the same way.

**Decided 2026-11-05:**

- A migrated module gets speed upgrades in its slots: Mk2 → 2, Mk3 → 4, so the base speed does not drop.
- Mk2/Mk3/Advanced items in chests become deprecated (a warning in the tooltip), a crafting table recipe
  turns them into an Extractor with the speed already inserted (as with migration in a pipe). Modules on a chassis
  migrate by themselves.
- The curve is as in the table above.

## Item sink

Priorities (`SinkReply.FixedPriority`, ascending): DefaultRoute < ModBasedItemSink < GT_TypeFilterSink <
OreDictItemSink < EnchantmentItemSink < Terminus < APIARIST_BeeSink < APIARIST_Analyser < ItemSink < PassiveSupplier <
ElectricBuffer < ElectricManager.

| Module | id | What it accepts | Priority | Upgrades now | GUI | Proposal |
|---|---|---|---|---|---|---|
| ItemSink | 1 | 9-slot filter (ignore data / ignore NBT), default route | ItemSink, with default route — DefaultRoute | fuzzy, sneaky | legacy + MUI | **Stays** as the base sink |
| ModBased sink | 12 | list of mods | ModBasedItemSink | sneaky | legacy + MUI | Merge into **Tag sink** ("mod" mode) |
| CreativeTab sink | 16 | list of tabs | ModBasedItemSink (shared with ModBased, same NBT keys) | — | legacy + MUI | Merge into **Tag sink** ("tab" mode) |
| OreDict sink | 13 | list of ore dict names | OreDictItemSink | sneaky | legacy only | Merge into **Tag sink** ("ore dict" mode) |
| GT TypeFilter sink | 17 | GT prefixes (GTNH only) | GT_TypeFilterSink | — | legacy only | Merge into **Tag sink** ("GT prefix" mode) |
| Enchantment sink | 31 | any enchanted item | EnchantmentItemSink, custom 0 | — | none | Merge with Mk2: **Enchantment sink**, empty filter = any enchanted item |
| Enchantment sink Mk2 | 131 | filter AND enchanted | EnchantmentItemSink, custom 1 | — | MUI | → Enchantment sink with a filter |
| Terminus | 6 | filter | Terminus | — | MUI | → **ItemSink priority mode** (normal / terminus), decided |
| Polymorphic sink | 4 | what is already in the inventory | ItemSink | sneaky | none | **Stays** (decided) |
| Thaumic aspect sink | 30 | Thaumcraft aspects | ItemSink | — | legacy only | **Stays** (decided), needs MUI |
| Bee sink (Apiarist) | 9 | 6 bee filters | APIARIST_BeeSink | — | legacy only | **Stays** (decided), needs MUI |
| Apiarist Terminus | 11 | bees | Terminus | — | none | Stays |
| CC sink | 15 | decided by the CC script | — | — | none | Stays while the CC integration exists |

**Proposal, Tag sink.** ModBased, CreativeTab, OreDict and GT TypeFilter are the same "list of strings → does the
item match", they differ only in what they compare. One module with a list of strings and a mode switch; the priority
comes from the mode (keeps the current order between them). One MUI instead of four legacy GUIs.

**Decided 2026-11-05:**

- Tag sink: one mode per module (mod / tab / ore dict / GT prefix), 1:1 migration.
- Terminus becomes an ItemSink priority mode; Terminus migrates into ItemSink with this mode.
- Fuzzy is accepted by ItemSink and Enchantment sink with a filter; sneaky is the one Sneaky Upgrade (2026-11-09).
- Items of merged sinks in chests are deprecated with a conversion recipe, like the extractors.

**Done 2026-11-09:** ItemSink priority (and the other sinks') is set in the settings window, unlocked by the Priority
Upgrade. Default route stays an ItemSink setting.

## Other modules

| Module | id | What it does | Now | Proposal |
|---|---|---|---|---|
| Provider | 500 | provides items for requests | 8 items, 1 stack, energy 1; 6 ticks (active) / 100 (passive) | **Provider + speed** |
| Provider Mk2 | 501 | the same, faster | 128 items, 8 stacks, energy 2 | → Provider with 4 speed (decided, done); Provider Mk2 pipe deprecated |
| Passive supplier | 2 | accepts filtered items to fill the inventory | PassiveSupplier priority | Stays |
| Active supplier | 502 | requests up to a set amount | pattern upgrade | Stays; later crafting upgrade (decided: without it, it does not request crafting) |
| QuickSort | 5 | unloads the whole inventory into sinks | 6 ticks, 24 when stalled | Stays, **accepts speed** (decided) |
| CC QuickSort | 14 | QuickSort, target chosen by a CC script | — | Stays while the CC integration exists |
| Electric manager | 300 | charges / discharges electric items | every 100 ticks | Stays; decided: insert / extract batteries into machine energy slots |
| Electric buffer | 301 | buffer of electric items | every 80 ticks | Stays separate (decided) |
| Apiarist analyser | 8 | extracts analysed bees | — | Stays (Forestry) |
| Apiarist refiller | 10 | returns drones and princesses to the apiary | — | Stays (Forestry) |
| Crafter / Mk2 / Mk3 | 600–602 | old autocrafting | — | **Deprecated** (decided), replaced by pattern crafting |
| Fluid sink | 700 | accepts fluids by filter / default route | — | Stays |
| Fluid provider | 702 | provides fluids from tanks | 6 ticks | Stays, **accepts speed** (decided) |
| Fluid supplier | 703 | requests fluid up to an amount / threshold | 100 ticks | Stays |
| Fluid container sink | 704 | accepts containers with a matching fluid | — | Stays |
| Fluid container supplier | 705 | requests containers and fluid, fills them | — | Stays |
| Satellite / fluid satellite | — | new, like the satellite pipes | none | **Not doing** (decided 2026-11-07): satellites stay pipes |
| Blank | 0 | crafting blank | — | Stays |

## Module upgrades after the rework

| Upgrade | Modules |
|---|---|
| Speed | Extractor, Fluid extractor, Provider, Fluid provider, QuickSort |
| Sneaky | Every module that works on a side: item sinks, Extractor, Provider, Active supplier, Electric manager, Fluid container supplier, Crafter, fluid modules |
| Priority | Item sinks, Provider, Fluid sink, Fluid provider |
| Fuzzy | ItemSink, Enchantment sink |
| Pattern | Active supplier |
| Crafting (new) | Active supplier |
| Other crafting upgrades (fluid crafting, byproduct, cleanup, advanced satellite) | deprecated crafters only |

One Speed Upgrade item, one per slot, up to 4 per module; tiered speed upgrades were tried and dropped (2026-11-07).
The six sided sneaky upgrades and the combined one are deprecated and convert into the one Sneaky Upgrade; the upgrade
module upgrade is deprecated.

## Work order (proposal)

1. Extractor: one module + speed + filter, migration of the six old ones; speed on Fluid extractor. **Done 2026-11-05**
   (`ModuleExtractor`, `ModuleMigration`, `ModuleMigrationRecipe`; Fluid extractor: 500 mB × 2^speed, energy
   5 + speed per tank).
2. Provider + speed, Mk2 migration. **Done 2026-11-05**: doubling curve (8/1/1, 16/2/1, 32/4/2, 64/6/2, 128/8/2 +
   Fast), Mk2 module → Provider with 4 speed; Provider Mk2 pipe deprecated, its built-in module has built-in speed 4
   (real speed items in the pipe would drop when it is broken and get duped).
3. Tag sink, Enchantment sink, Terminus → ItemSink mode, migration; speed on QuickSort and Fluid provider.
   **Tag sink done 2026-11-06**: `ModuleTagItemSink` took the ModBased id (12), CreativeTab / OreDict / GT TypeFilter are
   deprecated stubs, the migration writes the mode into the settings (ModBased and CreativeTab have the same NBT keys), the GT code
   is in `TagSinkGregTech`. Window: mode, item analysis slot with an Add button, a list of up to 9 values.
   **The rest done 2026-11-06**: Enchantment sink is one module with a filter (Mk1 = empty filter, Mk2 deprecated);
   fuzzy is not added — the filter already compares without damage and NBT. Terminus is an ItemSink priority switch
   (`terminus` in the settings), the Terminus module is deprecated. QuickSort and Fluid provider: speed 0–4 → 6/4/3/2/1 ticks
   between sends.
4. MUI for the remaining legacy GUIs (Thaumic, Bee sink, QuickSort and others).
   **Done 2026-11-06**: a shared window `ModuleInfoMuiDynamic` for modules without settings (QuickSort, Polymorphic, Electric
   buffer, Apiarist refiller / terminus, CC sink; with speed — a Speed line), windows for Thaumic aspect sink and Bee sink; any
   module with a MUI opens in the hand (upgrades column). Fluid extractor got a fluid filter (whitelist /
   blacklist, empty = everything). CC QuickSort got its own MUI window with its settings (timeout, sink size).
5. One Sneaky Upgrade and the Priority Upgrade. **Done 2026-11-09**: see the "Module upgrades after the rework" table
   and [behaviour-changes.md](behaviour-changes.md).
