# 02 – Request table rework

## Overview

**Upstream (`GTNH-origin/master`).** There is one request table, `PipeBlockRequestTable` (item "Logistics Request
Table"). Its GUI is `GuiRequestTable`: an items-only network list, a 27-slot internal buffer that stacks to 64, a
"to sort" slot, a disk slot, and a 3x3 crafting grid that crafts from the buffer. Fluids are requested only through
the separate fluid request pipe and `FluidGuiOrderer`, and that list shows only fluids already stored in the network.

**This branch.** The old table is still there and works as before. A second table was added next to it:
[`RequestTablePipe`](../../src/main/java/logisticspipes/crafting/requesttable/RequestTablePipe.java), registered as
"New Request Table" with the display name "Logistics Request Table Mk2". It extends `PipeBlockRequestTable` and adds:

- one network list that shows items **and** fluids, with network, internal and craftable amounts for each entry,
  per-player sort/filter settings, and a search bar;
- internal item storage **and** internal fluid storage, both larger with four new storage upgrades;
- a request popup with typed amounts and +/- buttons in place of the old request buttons;
- a ghost (fake) 3x3 crafting grid that crafts from internal storage and then from the player's inventory, plus a
  "Req" button that orders the missing ingredients for N crafts;
- "Send all" buttons that push internal items or fluids back into the network;
- a GUI whose height follows the screen size.

Smaller related changes: the fluid orderer (`FluidGuiOrderer`) now shows craftable fluids, can filter Both/Craft/Supply,
and has a "Content" button that shows what a fluid request would use. The old table's monitor popup and its watch
packets were extended for pattern crafting (see [01-pattern-crafting.md](01-pattern-crafting.md)).

All new code is in `logisticspipes.crafting.requesttable` (pipe, container, GUI, models) and
`logisticspipes.network.packets.crafting.requesttable` (packets). The new GUI is still a legacy `GuiContainer`, not a
ModularUI screen (see [05-modularui-gui.md](05-modularui-gui.md) for the MUI work and the shared `PacketGuards`).

---

# Features

## New request table pipe (Mk2)

**For the player.** This is a new placeable item, "Logistics Request Table Mk2". Right-clicking it opens the new GUI
(`GuiIDs.GUI_New_Request_Table_ID = 39`). It routes, renders and accepts security settings like the old table,
because it inherits all of that from `PipeBlockRequestTable`.

**How it works.**
- [`RequestTablePipe`](../../src/main/java/logisticspipes/crafting/requesttable/RequestTablePipe.java) extends
  `PipeBlockRequestTable` and implements `IRequestFluid`, so the fluid request code can use it as a requester.
- `openGui` is overridden: it applies the storage upgrades and opens GUI 39 (server: `RequestTableContainer`, client:
  `RequestTableGui`, wired in `GuiHandler`). The parent's `openGui` would put a held disk into the disk slot. The
  override skips that, so the new table never takes a disk.
- `guiOpenedByPlayer`/`guiClosedByPlayer` keep their own `requestTableGuiWatchers` list. The parent's watcher list,
  used for crafting-monitor packets, is still updated through `super`.
- The custom `TransportLayer.handleItem` handles arriving items:
  - **Fluid container items** go into the fluid storage. Whatever doesn't fit becomes a new LP fluid container item.
    That item goes into item storage if there is room, and is dropped in the world otherwise.
  - **Normal items** go into item storage with `addCompressed(stack, true)`, which ignores the item's max stack size.
    Anything that doesn't fit is dropped in the world. The old table instead left the remainder on the routed item.
  - After each arrival the full network list is sent again to everyone who has the GUI open.
- `sendFailed(FluidIdentifier, Integer)` does nothing. Failures reach the player through the normal
  `MissingItems` popup/chat path.
- `onAllowedRemoval` first calls the parent, which drops `inv`, `toSortInv` and `diskInv`. It then drops every
  stored fluid as LP fluid container items.

**Registration (files owned by other areas).** `LogisticsPipes.java` creates the pipe
(`createPipe(RequestTablePipe.class, "New Request Table", side)`). `GuiIDs`/`GuiHandler` add GUI 39, `en_US.lang`
adds `item.RequestTablePipe(.name)=Logistics Request Table Mk2`, and `TravelingItemRenderer` renders the new item
like the old table.

**NBT / save compatibility.**
- The old table (`PipeBlockRequestTable`, "Request Table") is still registered, keeps its GUI (`GuiRequestTable`) and
  loads exactly as before. **Nothing migrates an old table into the new one.** They are separate items, so existing
  worlds keep their old tables.
- The new table writes the parent's keys (`inv`, `matrix`, `toSortInv`, `diskInv`, `blockRotation`, …) plus:
  - `newRequestTableFluidStoragefluids` (list of FluidStack + `index`), `newRequestTableFluidStoragesize` and
    `newRequestTableFluidStoragecapacity`. The key prefix and suffix are joined with no separator.
  - `newRequestTableDisplaySettings`: a list of `{player: UUID string, settings: {sortMode, sortDirection,
    filterMode}}`. Only non-default settings are stored, and bad UUIDs are skipped.
- `SimpleStackInventory` (changed in another area) now also writes `<prefix>itemsCount` and grows itself on load when
  the saved count is larger. This keeps slots added by upgrades through a reload: at load time `container` is still
  `null`, so `updateStorageUpgrades()` returns early. Old saves without `itemsCount` read as 0 and keep the default
  size.

## Combined item + fluid network list

**For the player.** The upper panel lists every requestable item and fluid. An entry is shown if it is stored in the
network, stored in this table, or craftable by the network. Fluids have a small blue corner marker. The hover tooltip
shows `Network: N`, `Internal: N`, and `Total: N` while shift is held, in mB for fluids. The internal amount is also
drawn small and cyan in the top-right corner of the icon. The mouse wheel scrolls the list. The search bar matches
space-separated words against the localized fluid name, the display name and the item's friendly name.

**How it works.**
- [`RequestTableRefreshPacket.buildEntries`](../../src/main/java/logisticspipes/network/packets/crafting/requesttable/RequestTableRefreshPacket.java)
  builds the list on the server from:
  - `logisticsManager.getAvailableItems` (network items);
  - the table's `inv` (internal items);
  - `logisticsManager.getCraftableItems` (craftable). Craftable fluid containers are sorted into the fluid half;
  - `logisticsFluidManager.getAvailableFluid` (network fluids), plus the internal fluid storage. Fluid identifiers
    are turned into fluid-container `ItemIdentifier`s.
- Each entry is a
  [`RequestTableNetworkEntry`](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableNetworkEntry.java)
  holding a stack, a fluid flag, the network amount, the internal amount and a craftable flag.
- [`RequestTableContentPacket`](../../src/main/java/logisticspipes/network/packets/crafting/requesttable/RequestTableContentPacket.java)
  (server → client) carries the list plus the player's display settings. The client applies it only if the open
  `RequestTableGui` is for the same coordinates (`isForTable`).
- On the client, [`RequestTableNetworkList`](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableNetworkList.java)
  caches names and the sorted and filtered lists. Sorting reruns only when entries or sort settings change, filtering
  only when entries, the filter or the search text change.
  [`RequestTableNetworkGrid`](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableNetworkGrid.java)
  draws the list and finds which entry is under the mouse.
- When the list is sent:
  - **Client asks:** when the GUI opens, after a sub-GUI closes, after a request is submitted, after an ingredient
    request, and when switching back to the network view.
  - **Server pushes to all viewers:** after each arrival, network-entry click, Send all, or storage resize.

## Request popup (request overlay)

**For the player.** Middle-click an entry, or Ctrl+left/right-click it, to open a small popup. It shows
"In table: N", the item icon, a number field, +1/+10/+100/+1000 and −1/−10/−100/−1000 buttons, OK and a close "x".
The starting amount is 64 for Ctrl+left-click and 1 otherwise, including middle-click. Enter or OK submits, Esc
closes. The popup takes all mouse and key input while it is open.

**How it works.**
- [`RequestTableRequestOverlay`](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableRequestOverlay.java)
  is drawn by `RequestTableGui.drawScreen`; it is not a `SubGuiScreen`. The amount is limited to 0…999 999 999.
  When a new list arrives, the selected entry is refreshed and the typed amount is kept.
- Submitting sends
  [`RequestTableSubmitPacket`](../../src/main/java/logisticspipes/network/packets/crafting/requesttable/RequestTableSubmitPacket.java)
  with a fluid flag. The server calls `RequestHandler.requestFluid(player, stack, table, table)` for fluids and
  `RequestHandler.request(player, stack, table)` for items. Requested goods are delivered into the table's internal
  storage.
- The answer (`MissingItems`) goes to `RequestTableGui.handleRequestAnswer` when `Configs.DISPLAY_POPUP` is on. That
  opens the standard `GuiRequestPopup` ("You are missing:" / "Request successful!"). When it is off, the answer goes
  to chat. The `ClientProxy` dispatch for this lives in another area.

## Internal item & fluid storage

**For the player.** The two buttons at the top right are a crate (items, brown fill bar) and a tank (fluids, blue fill
bar). They switch the upper panel between the network list and a scrollable 9-column slot grid, and their fill bar
shows how full each storage is. Clicking the active one shows an "X" and goes back to the network list. In the
storage views a **Send all** button appears.

What the player can do:
- **Item storage:** normal slots. Shift-click moves a stack from storage to the player inventory, and shift-click in
  the player inventory moves a stack into storage.
- **Fluid storage:** click a slot with a fluid container on the cursor:
  - an empty container is filled from the slot;
  - a full container of the same fluid is emptied into the slot;
  - a partly full container is filled.

  This works with `IFluidContainerItem`s, `FluidContainerRegistry` containers and LP fluid containers. With a stack
  of containers, only one is handled and the rest go back to the player inventory, but only if there is room.
- **From the network list (no Ctrl):**
  [`RequestTableNetworkInteractPacket`](../../src/main/java/logisticspipes/network/packets/crafting/requesttable/RequestTableNetworkInteractPacket.java)
  → `RequestTablePipe.handleNetworkEntryInteraction`. These clicks only touch the **internal** storage, never the
  network:
  - Holding the same item: left-click puts the whole cursor stack into storage, right-click puts in one.
  - Empty cursor: left-click takes up to a stack from storage, right-click takes about half (at most half a stack),
    shift+left moves up to a stack into the player inventory, shift+right takes 1.
  - Fluid entries with a container on the cursor: fill the container from storage, or empty it into storage.
  - Afterwards the server sends the new cursor stack with
    [`RequestTableSetCursorPacket`](../../src/main/java/logisticspipes/network/packets/crafting/requesttable/RequestTableSetCursorPacket.java),
    which is ignored if it reaches the server.
- **Send all:** [`RequestTableSendStoragePacket`](../../src/main/java/logisticspipes/network/packets/crafting/requesttable/RequestTableSendStoragePacket.java)
  (integer 0 = items, 1 = fluids).
  - `sendStoredItemsToNetwork` sends at most one max-size stack at a time, using `assignDestinationFor` (default
    routing/sinks). It stops on a slot once nothing accepts that item.
  - `sendStoredFluidsToNetwork` sends up to 5000 mB at a time to the target from `logisticsFluidManager.getBestReply`,
    as passive LP fluid containers.

**How it works.**
- **Items** use the inherited `inv` (`SimpleStackInventory`, 27 slots, limit 64), resized by the upgrades.
- **Fluids** use
  [`RequestTableFluidStorage`](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableFluidStorage.java).
  It holds a fixed `FluidStack[]` and shows it as an `IInventory`: each slot looks like an LP fluid container item,
  so vanilla container sync works. The `FluidStack`s are the real data. `fill` tops up slots that already hold the
  same fluid first, then uses empty slots. `fillSlot`/`drain` work on one slot. `resize` moves the contents into the
  new layout and drops overflow as fluid container items.
- **Container:** [`RequestTableContainer`](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableContainer.java)
  creates every slot once: item storage, fluid storage (`UnmodifiableSlot`), crafting ghost slots (`DummySlot`),
  result (`HandelableSlot`), and player inventory. The client moves them each frame through `layout(...)`. Slots
  that are hidden or scrolled out of view are parked at (-5000, -5000). Clicks on fluid slots and on the result slot
  are handled in `slotClick`, shift-click moves in `transferStackInSlot`.
- **Views and layout:** [`RequestTableView`](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableView.java)
  (NETWORK / ITEM_STORAGE / FLUID_STORAGE) and
  [`RequestTableLayout`](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableLayout.java). The GUI is
  210 px wide and between 214 and 330 px tall (`height - 16`). The network panel fills whatever space is left above
  the crafting grid and the player inventory.

## Storage upgrades

**For the player.** Four new upgrade items. They fit only in the new table, not in modules:

| Upgrade (damage) | Effect per upgrade item |
|---|---|
| Request Table Item Slot Upgrade (45) | +9 item slots (base 27) |
| Request Table Item Stack Upgrade (46) | +64 to the per-slot item limit (base 64) |
| Request Table Fluid Slot Upgrade (47) | +9 fluid slots (base 9) |
| Request Table Fluid Capacity Upgrade (48) | +1 × `MAX_LOGISTICS_FLUID_TRANSPORT_INNER_CAPACITY` (config, default 10 000 mB) per fluid slot (base 1×) |

**How it works.**
- [`RequestTableStorageUpgrade`](../../src/main/java/logisticspipes/crafting/requesttable/upgrades/RequestTableStorageUpgrade.java)
  is the base class: `isAllowedForPipe` returns true only for `RequestTablePipe`, `isAllowedForModule` returns false,
  and `getAllowedPipes()` returns `"newRequestTable"`. The four subclasses are empty marker classes:
  [`RequestTableItemInventoryUpgrade`](../../src/main/java/logisticspipes/crafting/requesttable/upgrades/RequestTableItemInventoryUpgrade.java),
  [`RequestTableItemStackUpgrade`](../../src/main/java/logisticspipes/crafting/requesttable/upgrades/RequestTableItemStackUpgrade.java),
  [`RequestTableFluidInventoryUpgrade`](../../src/main/java/logisticspipes/crafting/requesttable/upgrades/RequestTableFluidInventoryUpgrade.java),
  [`RequestTableFluidCapacityUpgrade`](../../src/main/java/logisticspipes/crafting/requesttable/upgrades/RequestTableFluidCapacityUpgrade.java).
  `ItemUpgrade` registers them as damage values 45–48 and reuses existing icons 31 and 15. That file belongs to
  another area.
- `RequestTablePipe.updateStorageUpgrades()` adds up the stack sizes of matching upgrade items in the upgrade
  inventory (9 slots × 16) and resizes `inv` and the fluid storage. Nothing listens for upgrade changes; this
  method is called from many places instead: open GUI, container constructor, every arrival, network click, Send all,
  NBT read/write, building the list, and the fill-level/amount getters. Those getters are also called while the
  client draws the GUI.
- When storage shrinks, item contents are compacted back in and the overflow is **dropped in the world**. Fluid
  overflow is dropped as fluid container items.
- Upgrade sizes use `SimpleStackInventory.setSizeInventory`/`setInventoryStackLimit`. Those methods were added in
  another area.

## Display settings (sort / filter, per player)

**For the player.** In the network view, three buttons to the left of the GUI cycle through:
- sort mode: **Name** / **Amount** (total amount, ties broken by name);
- direction: **Asc** / **Desc**;
- filter: **Both** / **Stored** (network + internal > 0) / **Craft** (craftable).

Each button has a tooltip. The choice is remembered **per player, per table**, and survives reloads.

**How it works.**
- [`RequestTableDisplaySettings`](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableDisplaySettings.java)
  is an immutable value (Lombok `@Value`) with three enums. It is sent as ordinals; ordinals out of range fall back
  to the default.
- [`RequestTableDisplaySettingsStore`](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableDisplaySettingsStore.java)
  is a UUID → settings map saved in the table's NBT.
- The client changes its settings right away and sends
  [`RequestTableDisplaySettingsPacket`](../../src/main/java/logisticspipes/network/packets/crafting/requesttable/RequestTableDisplaySettingsPacket.java).
  The server saves them and marks the tile dirty. Every `RequestTableContentPacket` carries that player's settings
  back.

## Crafting grid (ghost recipe, craft from storage, request ingredients)

**For the player.**
- The 3x3 grid holds ghost items only (like the old table's matrix), and the result shows the matching recipe output.
  The NEI "?" recipe transfer works on this GUI (the overlay handler is registered in another area).
- The small **x** next to the grid clears it.
- Clicking the result crafts once onto the cursor, if the cursor is empty or holds the same item with room left.
  Shift-clicking crafts up to 64 items straight into the player inventory.
- Ingredients come from the table's **internal storage first, then the player's main inventory**.
- Crafting remainders (empty buckets, damaged tools, …) go to internal storage, then the player inventory, and are
  dropped otherwise.
- The number field (1–9999) and the **Req** button (or Enter in the field) order N crafts' worth of grid ingredients
  from the network. Amounts already in internal storage are subtracted; the player inventory is not counted.

**How it works.**
- `RequestTablePipe.getResultForClick(player, held, amount)` / `craftIntoPlayerInventory(player, limit)` build a
  `CraftingPreview` for each craft:
  - `getCurrentRecipe()` runs `cacheRecipe()` and then scans `CraftingUtil.getRecipeList()` for a recipe that matches
    the grid and produces the cached result;
  - `findIngredients` picks the slots to use, first with ore-dictionary matching and, if that fails, with strict
    `equalsForCrafting` matching;
  - the recipe is checked again against the actual ingredients.
- The ingredients are then removed, `SlotCrafting.onPickupFromSlot` fires (achievements and crafting events), and
  the remainders are collected.
- `RequestTableContainer.slotClick` sends result-slot clicks here: mode 1 → `craftIntoPlayerInventory(64)`, mode 0 →
  one craft to the cursor.
- [`RequestTableClearCraftingPacket`](../../src/main/java/logisticspipes/network/packets/crafting/requesttable/RequestTableClearCraftingPacket.java)
  clears the matrix. The client also clears its own copy right away.
- [`RequestTableRequestIngredientsPacket`](../../src/main/java/logisticspipes/network/packets/crafting/requesttable/RequestTableRequestIngredientsPacket.java)
  (integer = number of crafts) → `requestCraftingIngredients` → `RequestHandler.requestList(player, list, table)`.

## GUI

[`RequestTableGui`](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableGui.java) (client only,
extends `LogisticsBaseGuiScreen`) puts the features above together:
- header: title, search bar (network view only), Send all (storage views only), item/fluid storage buttons;
- the main panel (network grid or storage slots);
- the display buttons on the left;
- the crafting area with the clear button, the amount field and Req;
- the player inventory.

Many things are drawn with plain `Gui.drawRect` calls: the storage icons, the crafting arrow, the clear button and
the overlay buttons. All strings in this GUI are hard-coded English.

## Fluid orderer changes (fluid request pipe GUI)

**For the player.** The fluid request pipe GUI (`FluidGuiOrderer`) now has:
- three small buttons: **Refresh**, **Content**, and a **Both / Craft / Supply** cycle button;
- craftable fluids in its list (amount 0) as well as stored ones;
- "Content", which shows what requesting the selected fluid amount would use and what is missing, with the same
  component popup the item orderer uses.

**How it works.**
- [`FluidGuiOrderer`](../../src/main/java/logisticspipes/gui/orderer/FluidGuiOrderer.java): the buttons are now
  `SmallGuiButton`s. Button id 13 ("Content") sends the new
  [`RequestFluidComponentPacket`](../../src/main/java/logisticspipes/network/packets/orderer/RequestFluidComponentPacket.java).
  Button id 9 cycles a local `DisplayOptions` (Both → Craft → Supply) and refreshes.
- [`RequestFluidOrdererRefreshPacket`](../../src/main/java/logisticspipes/network/packets/orderer/RequestFluidOrdererRefreshPacket.java)
  now extends `Integer2CoordinatesPacket`. `integer2` is the option (0 Both, 1 SupplyOnly, 2 CraftOnly), passed on to
  the new `RequestHandler.refreshFluid(player, pipe, DisplayOptions)`. For Craft/Both that method adds craftable
  fluid containers that aren't already stored.
- `RequestFluidComponentPacket` → the new `RequestHandler.simulateFluid` builds a `RequestTree` for a
  `FluidResource` without starting it and returns a `ComponentList` (used/missing). `RequestHandler` itself is
  documented with [01-pattern-crafting.md](01-pattern-crafting.md). `requestFluid` now also returns early if the
  stack is not a fluid.

---

# Changes to upstream classes

| Class | Change | Why |
|---|---|---|
| [`PipeBlockRequestTable`](../../src/main/java/logisticspipes/pipes/PipeBlockRequestTable.java) | Adds `watchedPatternCraftingRequests` (client side), `handleClientSidePatternCraftingInfo`, and the helpers `sendWatchedRequestToGuiWatchers`/`sendWatchedRequestToPlayer`. These send `OrdererWatchPacket` **and** a new `PatternCraftingWatchPacket` (roots from `PatternCraftingMonitorRegistry.build`). Expired/removed watches also clear the pattern map. | Crafting-monitor support for pattern crafting (see [01-pattern-crafting.md](01-pattern-crafting.md)). `RequestTablePipe` inherits it, but the new GUI does not show monitor data (see gaps). |
| [`GuiRequestTable`](../../src/main/java/logisticspipes/gui/orderer/GuiRequestTable.java) (old table GUI) | The monitor button (id 100) opens `PatternRequestMonitorPopup` if the watched order has pattern crafting data, otherwise the old `RequestMonitorPopup`. | Pattern crafting tree view in the old table. |
| [`FluidGuiOrderer`](../../src/main/java/logisticspipes/gui/orderer/FluidGuiOrderer.java) | Smaller buttons; new Content and Both/Craft/Supply buttons; refresh sends the display option. | Fluid orderer changes above. |
| [`RequestFluidOrdererRefreshPacket`](../../src/main/java/logisticspipes/network/packets/orderer/RequestFluidOrdererRefreshPacket.java) | Base class `IntegerCoordinatesPacket` → `Integer2CoordinatesPacket`; maps `integer2` to `RequestHandler.DisplayOptions`. | Carries the fluid display filter. **The wire format changed**, so client and server must run the same build (normal for LP packets). |

Upstream classes changed for this feature but documented in other areas: `SimpleStackInventory` (resizable,
`itemsCount`), `ItemUpgrade` (damages 45–48), `GuiIDs`/`GuiHandler` (GUI 39), `PacketGuards` (`getOpenRequestTable`),
`PacketHandler` (fails clearly on unregistered packets), `RequestHandler` (`refreshFluid` with options,
`simulateFluid`), `ClientProxy` (request-answer and recipe-import routing to `RequestTableGui`),
`LogisticsCraftingOverlayHandler`/`NEILogisticsPipesConfig` (NEI overlay on `RequestTableGui`), `LogisticsPipes`
(registration), `TravelingItemRenderer`, and `en_US.lang`.

---

# Known gaps / discrepancies

Seen in the code. Where `docs/pattern-crafting.md` §8 or `docs/testing-checklist.md` already lists an issue, its ID is
given.

1. **Stacks over 127 are not save-safe (D4).** The item stack upgrade raises the per-slot limit above 64. With two
   upgrades it is 192, and there is no cap other than the 9×16 upgrade inventory. `SimpleStackInventory.writeToNBT`
   still uses vanilla `ItemStack.writeToNBT` (byte `Count`), and vanilla slot sync also sends the count as a byte.
   Big stacks can be truncated or wrap around on save or sync.
2. **Unstackable items stack (D11).** Arrivals, shift-click into storage and network-entry inserts use
   `addCompressed(stack, true)`, which ignores the item's max stack size. 64 swords can sit in one slot.
   `moveInternalItemToPlayerInventory` puts back what the player inventory didn't take with `addCompressed`, and
   anything that doesn't fit back is lost. A room check runs first, so this is unlikely.
3. **Performance (F3, F4).** Each craft scans the full recipe list again, and the preview runs twice (ore-dict and
   strict pass). A 64-item shift-craft does this for every craft. Each arriving item or fluid, and each click,
   rebuilds the whole network list (`getAvailableItems` + `getCraftableItems` + `getAvailableFluid`) and sends it to
   every viewer, with no batching.
4. **`RequestTableDisplaySettingsPacket` reads client coordinates.** It uses `getPipe(player.worldObj)`, not
   `PacketGuards.getOpenRequestTable`. That doesn't match the S4 note ("all request table C→S packets resolve the
   table from `player.openContainer`"). The only effect is that a player can set their own display settings on any
   loaded table in their world.
5. **Client/server slot counts can differ (G2).** Each side builds its own `RequestTableContainer` slot list from its
   own `inv` and fluid storage sizes, which come from `updateStorageUpgrades()` on that side. The client also calls
   `updateStorageUpgrades()` every frame (through `getItemStorageFillLevel`/`getFluidStorageFillLevel`), and that
   method can resize and drop items with no server-side check.
6. **Storage upgrades apply late.** Upgrade changes take effect only the next time `updateStorageUpgrades()` runs,
   not when the upgrade is inserted or removed. Removing upgrades drops the overflow items and fluids **in the
   world**; nothing protects the contents.
7. **Old-table features missing in the new GUI.**
   - No disk slot; `openGui` no longer takes a held disk, and `diskInv` is still saved and dropped.
   - No "to sort" slot; the inherited `toSortInv` logic still runs every tick but nothing can fill it.
   - No crafting-monitor UI. The inherited watch logic keeps sending `OrdererWatchPacket`/`PatternCraftingWatchPacket`
     to viewers, but `RequestTableGui` never shows that data.
8. **No migration and no recipes.** Old request tables are never turned into Mk2. This repo has no crafting recipe
   for `logisticsNewRequestTable` or for upgrade damages 45–48: `RecipeManager` and `SolderingStationRecipes` don't
   mention them. The items are only in the creative tab and NEI.
9. **Missing lang key.** `RequestTableStorageUpgrade.getAllowedPipes()` returns `"newRequestTable"`, but
   `en_US.lang` has no `item.upgrade.info.newRequestTable`, so the shift tooltip shows the raw key.
10. **Fallback amount 1.** Middle-click opens the request popup with amount 1, and only Ctrl+left-click starts at 64.
    `RequestTableRequestOverlay.open(entry, font, w, h)`, which would start at the entry's stack size, is never
    called.
11. **Ore-dict first.** `getCraftingPreview` tries ore-dictionary matching before strict matching. When both kinds of
    candidate are present, an ore-dict-equivalent item can be used even though an exact match exists.
12. **Unused fields.** `RequestTableRefreshPacket` still sends the dimension in its integer, and
    `RequestTableSubmitPacket` and `RequestTableNetworkInteractPacket` still send dimension and coordinates. The
    server ignores them by design (S4), so they are just dead payload.
13. **Hard-coded English.** "Request Table", "Send all", "Req", "Name/Amount", "Asc/Desc", "Both/Stored/Craft",
    tooltips and "In table:" are not localized.

---

# Files

### `logisticspipes.crafting.requesttable`

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [RequestTableContainer](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableContainer.java) | A | +411/-0 | Server/client container: all slots created once and moved by the layout; fluid-slot clicks, result-slot crafting, shift-click transfers. |
| [RequestTableDisplaySettings](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableDisplaySettings.java) | A | +90/-0 | Immutable sort mode / direction / filter value with NBT and ordinal helpers. |
| [RequestTableDisplaySettingsStore](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableDisplaySettingsStore.java) | A | +69/-0 | Per-player-UUID display settings saved in the table NBT; defaults are not stored. |
| [RequestTableFluidStorage](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableFluidStorage.java) | A | +372/-0 | Resizable slotted FluidStack storage shown as an IInventory of LP fluid containers; fill/drain/resize/NBT/drop. |
| [RequestTableGui](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableGui.java) | A | +718/-0 | Client GUI: network grid, storage views, display buttons, crafting area, request popup, packet sends. |
| [RequestTableLayout](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableLayout.java) | A | +146/-0 | Pixel layout of the adaptive-height GUI (panel, buttons, crafting, player inventory). |
| [RequestTableNetworkEntry](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableNetworkEntry.java) | A | +116/-0 | One list entry: stack, fluid flag, network/internal amounts, craftable flag. |
| [RequestTableNetworkGrid](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableNetworkGrid.java) | A | +173/-0 | Draws the scrollable item/fluid icon grid, tooltips, internal-amount labels and hit-testing. |
| [RequestTableNetworkList](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableNetworkList.java) | A | +145/-0 | Client-side cached sort/filter/search over the received entries. |
| [RequestTablePipe](../../src/main/java/logisticspipes/crafting/requesttable/RequestTablePipe.java) | A | +1168/-0 | Mk2 table pipe: storage upgrades, item/fluid arrivals, storage interactions, crafting, Send all, NBT. |
| [RequestTableRequestOverlay](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableRequestOverlay.java) | A | +240/-0 | Modal request popup with an amount field, ±1/10/100/1000 buttons and OK/close. |
| [RequestTableView](../../src/main/java/logisticspipes/crafting/requesttable/RequestTableView.java) | A | +22/-0 | Enum of the upper panel modes: NETWORK, ITEM_STORAGE, FLUID_STORAGE. |

### `logisticspipes.crafting.requesttable.upgrades`

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [RequestTableFluidCapacityUpgrade](../../src/main/java/logisticspipes/crafting/requesttable/upgrades/RequestTableFluidCapacityUpgrade.java) | A | +7/-0 | Marker upgrade: +1× base capacity per fluid slot (damage 48). |
| [RequestTableFluidInventoryUpgrade](../../src/main/java/logisticspipes/crafting/requesttable/upgrades/RequestTableFluidInventoryUpgrade.java) | A | +7/-0 | Marker upgrade: +9 fluid slots (damage 47). |
| [RequestTableItemInventoryUpgrade](../../src/main/java/logisticspipes/crafting/requesttable/upgrades/RequestTableItemInventoryUpgrade.java) | A | +7/-0 | Marker upgrade: +9 item slots (damage 45). |
| [RequestTableItemStackUpgrade](../../src/main/java/logisticspipes/crafting/requesttable/upgrades/RequestTableItemStackUpgrade.java) | A | +7/-0 | Marker upgrade: +64 per-slot item limit (damage 46). |
| [RequestTableStorageUpgrade](../../src/main/java/logisticspipes/crafting/requesttable/upgrades/RequestTableStorageUpgrade.java) | A | +37/-0 | Base `IPipeUpgrade`: allowed only on `RequestTablePipe`, never on modules. |

### `logisticspipes.gui.orderer`

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [FluidGuiOrderer](../../src/main/java/logisticspipes/gui/orderer/FluidGuiOrderer.java) | M | +49/-3 | Small buttons, Content (simulate) button, Both/Craft/Supply filter sent with refresh. |
| [GuiRequestTable](../../src/main/java/logisticspipes/gui/orderer/GuiRequestTable.java) | M | +6/-1 | Old table GUI opens the pattern-crafting monitor popup for pattern orders. |

### `logisticspipes.network.packets.crafting.requesttable`

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [RequestTableClearCraftingPacket](../../src/main/java/logisticspipes/network/packets/crafting/requesttable/RequestTableClearCraftingPacket.java) | A | +32/-0 | C→S: clear the ghost crafting grid of the open table. |
| [RequestTableContentPacket](../../src/main/java/logisticspipes/network/packets/crafting/requesttable/RequestTableContentPacket.java) | A | +93/-0 | S→C: network entries plus the player's display settings for the open GUI. |
| [RequestTableDisplaySettingsPacket](../../src/main/java/logisticspipes/network/packets/crafting/requesttable/RequestTableDisplaySettingsPacket.java) | A | +58/-0 | C→S: save the player's sort/filter settings (finds the table by packet coordinates). |
| [RequestTableNetworkInteractPacket](../../src/main/java/logisticspipes/network/packets/crafting/requesttable/RequestTableNetworkInteractPacket.java) | A | +80/-0 | C→S: click on a network entry, moving items/fluids between cursor and internal storage. |
| [RequestTableRefreshPacket](../../src/main/java/logisticspipes/network/packets/crafting/requesttable/RequestTableRefreshPacket.java) | A | +145/-0 | C→S refresh; `buildEntries` merges network, internal and craftable items and fluids. |
| [RequestTableRequestIngredientsPacket](../../src/main/java/logisticspipes/network/packets/crafting/requesttable/RequestTableRequestIngredientsPacket.java) | A | +32/-0 | C→S: request the missing grid ingredients × N crafts. |
| [RequestTableSendStoragePacket](../../src/main/java/logisticspipes/network/packets/crafting/requesttable/RequestTableSendStoragePacket.java) | A | +36/-0 | C→S: Send all internal items (0) or fluids (1) to the network. |
| [RequestTableSetCursorPacket](../../src/main/java/logisticspipes/network/packets/crafting/requesttable/RequestTableSetCursorPacket.java) | A | +31/-0 | S→C cursor stack sync; ignored on the server. |
| [RequestTableSubmitPacket](../../src/main/java/logisticspipes/network/packets/crafting/requesttable/RequestTableSubmitPacket.java) | A | +63/-0 | C→S: request an item or fluid amount into the open table. |

### `logisticspipes.network.packets.orderer`

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [RequestFluidComponentPacket](../../src/main/java/logisticspipes/network/packets/orderer/RequestFluidComponentPacket.java) | A | +33/-0 | C→S: simulate a fluid request and reply with a used/missing component list. |
| [RequestFluidOrdererRefreshPacket](../../src/main/java/logisticspipes/network/packets/orderer/RequestFluidOrdererRefreshPacket.java) | M | +16/-3 | Now `Integer2CoordinatesPacket`; `integer2` picks the Both/Supply/Craft fluid list. |

### `logisticspipes.pipes`

| Class/file | Status | +/- | Summary |
|---|---|---|---|
| [PipeBlockRequestTable](../../src/main/java/logisticspipes/pipes/PipeBlockRequestTable.java) | M | +45/-13 | Watch helpers also send pattern-crafting monitor trees; client map of pattern watches. |
