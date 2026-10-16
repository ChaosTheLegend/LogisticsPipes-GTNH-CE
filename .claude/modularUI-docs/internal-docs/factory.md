# `com.cleanroommc.modularui.factory` — Opening GUIs

This package is the "how do I actually open a GUI" subsystem. A `UIFactory` knows how to
turn some game object (a `TileEntity`, an `Item`, an `Entity`, the player itself, or nothing at
all) into a synced `ModularPanel`/`ModularScreen` pair on both logical sides. `GuiManager` is the
low-level engine that all factories call into; `GuiFactories` is the discoverable list of
built-in factory singletons; the `*GuiData` classes carry the context needed to rebuild the same
UI on client and server.

If you just want to open a GUI for your block/item/entity, skip to
["Which factory do I use?"](#which-factory-do-i-use) below.

## Package map

| Class | Role |
|---|---|
| `GuiManager` | Central engine: registers factories, opens/syncs containers, dispatches packets. |
| `GuiFactories` | Static accessors for the built-in factory singletons + `SimpleGuiFactory` creation. |
| `AbstractUIFactory<T>` | Base class implementing `UIFactory<T>` boilerplate (name, panel/screen creation, casting). |
| `TileEntityGuiFactory` | GUI bound to a `TileEntity` position. |
| `SidedTileEntityGuiFactory` | Same, plus a `ForgeDirection` (e.g. GT covers). |
| `EntityGuiFactory` | GUI bound to a living `Entity`. |
| `PlayerInventoryGuiFactory` | GUI bound to an item sitting in a player-owned inventory (main inv, hotbar, baubles). |
| `ItemGuiFactory` | Deprecated predecessor of `PlayerInventoryGuiFactory`, main-hand only. |
| `ItemStackGuiFactory` | GUI bound to an arbitrary `ItemStack` value (not necessarily a real slot/item). |
| `SimpleGuiFactory` | GUI with no game-object context at all (commands, standalone tools/editors). |
| `GuiData` / `PosGuiData` / `SidedPosGuiData` / `ItemStackGuiData` / `EntityGuiData` / `PlayerInventoryGuiData` | Context objects passed to `buildUI`. |
| `ClientGUI` | Helper for opening plain (non-synced) client-only screens. |
| `factory.inventory.*` | `InventoryType` abstraction used by `PlayerInventoryGuiFactory` to address "some inventory owned by a player" (vanilla inventory, baubles, etc). |

---

## Which factory do I use?

| You're attaching a GUI to... | Use | Your holder implements |
|---|---|---|
| A `TileEntity` | `GuiFactories.tileEntity()` | `TileEntity` + `IGuiHolder<PosGuiData>` |
| A `TileEntity` where the clicked side matters (e.g. GT cover) | `GuiFactories.sidedTileEntity()` | `TileEntity` + `IGuiHolder<SidedPosGuiData>` |
| An `Entity` (e.g. right-click a mob) | `GuiFactories.entity()` | `Entity` + `IGuiHolder<EntityGuiData>` |
| An item sitting in the player's own inventory/hotbar/baubles | `GuiFactories.playerInventory()` | `Item` + `IGuiHolder<PlayerInventoryGuiData>` |
| An arbitrary `ItemStack` (not necessarily a real inventory slot) | `new ItemStackGuiFactory(name, holder)` | any `IGuiHolder<ItemStackGuiData>` |
| Nothing — commands, standalone editor windows, debug tools | `new SimpleGuiFactory(name, holder)` | any `IGuiHolder<GuiData>` |
| An item in main hand only (legacy) | `GuiFactories.item()` *(deprecated)* | `Item` + `IGuiHolder<GuiData>` |

All factories except `SimpleGuiFactory`/`ItemStackGuiFactory` are singletons pre-registered by
`GuiFactories.init()` (called internally by the library). `SimpleGuiFactory` and
`ItemStackGuiFactory` self-register in their constructor — just keep one `static final` instance
per GUI type, constructed once (on both sides, in the same order/class-loading path), and it is
usable directly.

---

## `GuiData` hierarchy

Base class carrying the minimum needed to identify "who is looking at this GUI": the
`EntityPlayer`. Subclasses add whatever else is needed to relocate the same target object on the
other side of the network.

```java
package com.cleanroommc.modularui.factory;

public class GuiData {
    public GuiData(@NotNull EntityPlayer player);
    @NotNull EntityPlayer getPlayer();
    World getWorld();               // player.getEntityWorld()
    boolean isClient();              // NetworkUtils.isClient(player)
    ItemStack getMainHandItem();     // player.getHeldItem()
}
```

| Subclass | Adds | Used by |
|---|---|---|
| `GuiData` | player only | `SimpleGuiFactory`, `ItemGuiFactory` |
| `PosGuiData` | block `x,y,z`, `getTileEntity()`, distance helpers | `TileEntityGuiFactory` |
| `SidedPosGuiData` | + `ForgeDirection side` | `SidedTileEntityGuiFactory` |
| `ItemStackGuiData` | the actual `ItemStack` + `getTagCompound()` | `ItemStackGuiFactory` |
| `EntityGuiData` | the target `Entity` | `EntityGuiFactory` |
| `PlayerInventoryGuiData` | `InventoryType` + `slotIndex`, `getUsedItemStack()`/`setUsedItemStack()` | `PlayerInventoryGuiFactory` |

### `GuiData`

Fully-qualified: `com.cleanroommc.modularui.factory.GuiData`

```java
public class GuiData {
    public GuiData(@NotNull EntityPlayer player);
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getPlayer()` | — | `EntityPlayer` | Never null. |
| `getWorld()` | — | `World` | `player.getEntityWorld()`. |
| `isClient()` | — | `boolean` | True if `player` is a client-side player. |
| `getMainHandItem()` | — | `ItemStack` | `player.getHeldItem()`. |

Use directly when the GUI has no target object — e.g. commands, config editors
(`ItemEditorGui`, see below).

### `PosGuiData`

Fully-qualified: `com.cleanroommc.modularui.factory.PosGuiData`

```java
public class PosGuiData extends GuiData {
    public PosGuiData(@NotNull EntityPlayer player, int x, int y, int z);
    public PosGuiData(@NotNull EntityPlayer player, @NotNull BlockPos pos);
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getX()`/`getY()`/`getZ()` | — | `int` | Block coordinates. |
| `getSquaredDistance(double,double,double)` / `getSquaredDistance(Entity)` | coords or entity | `double` | Distance to block center (`+0.5`). |
| `getDistance(...)` | same overloads | `double` | `sqrt` of squared distance. |
| `getTileEntity()` | — | `TileEntity` | `getWorld().getTileEntity(x, y, z)`, re-fetched live (not cached). |

Use for any GUI whose owner is a `TileEntity` — this is what `TileEntityGuiFactory` constructs.

### `SidedPosGuiData`

Fully-qualified: `com.cleanroommc.modularui.factory.SidedPosGuiData`

```java
public class SidedPosGuiData extends PosGuiData {
    public SidedPosGuiData(@NotNull EntityPlayer player, int x, int y, int z, @NotNull ForgeDirection side);
}
```

| Method | Returns | Notes |
|---|---|---|
| `getSide()` | `ForgeDirection` | The side of the block that was interacted with. Never null. |

Use when the same `TileEntity` shows a different UI per side (e.g. GregTech-style covers/facing
machines). Built by `SidedTileEntityGuiFactory`.

### `ItemStackGuiData`

Fully-qualified: `com.cleanroommc.modularui.factory.ItemStackGuiData`

```java
public class ItemStackGuiData extends GuiData {
    public ItemStackGuiData(EntityPlayer player, ItemStack itemStack);
}
```

| Method | Returns | Notes |
|---|---|---|
| `getItemStack()` | `ItemStack` | The stack captured at open time; not necessarily still in any slot. |
| `getTagCompound()` | `NBTTagCompound` | Shortcut for `itemStack.getTagCompound()`. |

Use when the GUI needs an `ItemStack` value (and its NBT) but the stack isn't tied to a live
inventory slot that can be looked up again later — the whole stack is serialized over the network
by `ItemStackGuiFactory`.

### `EntityGuiData`

Fully-qualified: `com.cleanroommc.modularui.factory.EntityGuiData`

```java
public class EntityGuiData extends GuiData {
    public EntityGuiData(EntityPlayer player, Entity guiHolder);
}
```

| Method | Returns | Notes |
|---|---|---|
| `getGuiHolder()` | `Entity` | The target entity; re-resolved by entity ID on the receiving side. |

### `PlayerInventoryGuiData`

Fully-qualified: `com.cleanroommc.modularui.factory.PlayerInventoryGuiData`

```java
public class PlayerInventoryGuiData extends GuiData {
    public PlayerInventoryGuiData(@NotNull EntityPlayer player, @NotNull InventoryType inventoryType, int slotIndex);
}
```

| Method | Returns | Notes |
|---|---|---|
| `getInventoryType()` | `InventoryType` | Which player-owned inventory (see `factory.inventory` below). |
| `getSlotIndex()` | `int` | Index into that inventory. |
| `getUsedItemStack()` | `ItemStack` | `inventoryType.getStackInSlot(player, slotIndex)` — live lookup. |
| `setUsedItemStack(ItemStack)` | `void` | `inventoryType.setStackInSlot(player, slotIndex, stack)`. |

Note the item is looked up live each time — if the item leaves the slot between server open and
some later interaction, `getUsedItemStack()` may return something else or null; factories that use
this data guard interaction validity via `canInteractWith`.

---

## `UIFactory<T>` / `IGuiHolder<T>` — the two interfaces every factory bridges

`com.cleanroommc.modularui.api.UIFactory<D extends GuiData>` — implemented by every `*GuiFactory`
class in this package (usually via `AbstractUIFactory`). Key contract:

```java
public interface UIFactory<D extends GuiData> {
    @NotNull String getFactoryName();
    ModularPanel createPanel(D guiData, PanelSyncManager syncManager, UISettings settings);
    @SideOnly(Side.CLIENT) ModularScreen createScreen(D guiData, ModularPanel mainPanel);
    @SideOnly(Side.CLIENT) default IMuiScreen createScreenWrapper(ModularContainer container, ModularScreen screen);
    default ModularContainer createContainer();
    default boolean canInteractWith(EntityPlayer player, D guiData); // checked every tick; false closes the GUI
    void writeGuiData(D guiData, PacketBuffer buffer);
    @NotNull D readGuiData(EntityPlayer player, PacketBuffer buffer);
}
```

`com.cleanroommc.modularui.api.IGuiHolder<T extends GuiData>` — implemented by the mod author's
`TileEntity`/`Item`/`Entity`/plain class that actually builds the panel:

```java
@FunctionalInterface
public interface IGuiHolder<T extends GuiData> {
    @SideOnly(Side.CLIENT) default ModularScreen createScreen(T data, ModularPanel mainPanel);
    ModularPanel buildUI(T data, PanelSyncManager syncManager, UISettings settings);
}
```

Every `*GuiFactory.getGuiHolder(data)` resolves *which* `IGuiHolder` to delegate to (the tile
itself, the item instance, the entity instance, or a fixed reference for `SimpleGuiFactory`/
`ItemStackGuiFactory`), then `AbstractUIFactory` calls `guiHolder.buildUI(...)` /
`guiHolder.createScreen(...)` on it.

Gotcha: `createScreen` should be overridden by the `IGuiHolder` to pass your own mod ID — the
default implementation logs a warning and uses `ModularUI.ID`.

---

## `GuiManager`

Fully-qualified: `com.cleanroommc.modularui.factory.GuiManager`

The actual open/sync engine. Mod authors rarely call this directly — factory `open(...)` methods
call it — but it's the class to understand for how opening works end-to-end.

```java
public class GuiManager {
    public static void registerFactory(UIFactory<?> factory);
    public static @NotNull UIFactory<?> getFactory(String name);
    public static boolean hasFactory(String name);
    public static <T extends GuiData> void open(@NotNull UIFactory<T> factory, @NotNull T guiData, EntityPlayerMP player);
    @ApiStatus.Internal @SideOnly(Side.CLIENT)
    public static <T extends GuiData> void openFromClient(int windowId, int networkId, @NotNull UIFactory<T> factory, @NotNull PacketBuffer data, @NotNull EntityPlayerSP player);
    @SideOnly(Side.CLIENT)
    public static <T extends GuiData> void openFromClient(@NotNull UIFactory<T> factory, @NotNull T guiData);
    @SideOnly(Side.CLIENT) static void openScreen(ModularScreen screen, UISettings settings); // package-private, used by ClientGUI
}
```

| Method | Params | Returns | Notes / gotchas |
|---|---|---|---|
| `registerFactory(factory)` | any `UIFactory` | `void` | Name must be ≤ 32 chars and unique; throws `IllegalArgumentException` otherwise. Call once, at mod init, on **both sides** in the same order (network relies on name-keyed lookup, not ordinal, so order doesn't need to match, but presence does). `SimpleGuiFactory`/`ItemStackGuiFactory` do this automatically in their constructor. |
| `getFactory(name)` | factory name | `UIFactory<?>` | Throws `NoSuchElementException` if unregistered. |
| `hasFactory(name)` | factory name | `boolean` | — |
| `open(factory, guiData, player)` | — | `void` | **Server-side entry point.** Builds the panel/sync manager, constructs the `ModularContainer`, allocates a window id, sends an `OpenGuiPacket` to the client, and installs the container as `player.openContainer`. No-op if `player` is a `FakePlayer` or already had a GUI opened this tick (re-entrancy guard cleared every server tick end via `onTick`). |
| `openFromClient(windowId, networkId, factory, data, player)` | — | `void` | `@ApiStatus.Internal`, client-side. Invoked when the client receives the server's `OpenGuiPacket`; reconstructs `GuiData` from the buffer, builds panel + screen + container + `IMuiScreen` wrapper, and calls `MCHelper.displayScreen(...)`. Requires the wrapper's `GuiScreen` to be a `GuiContainer`, and forbids custom containers that aren't the one just constructed (`IllegalStateException` otherwise). You should not call this yourself. |
| `openFromClient(factory, guiData)` | — | `void` | **Client-side entry point when the client itself initiates the open** (e.g. clicking something client-only that still needs a server-synced GUI). Sends an `OpenGuiPacket` to the server requesting it open the real GUI; the server then calls `open(...)` and pushes back the real packet. |
| `onTick(TickEvent.ServerTickEvent)` | — | `void` | `@SubscribeEvent` instance method; clears the per-tick "already opened" guard at `END` phase. Registered as a Forge event listener internally — not something you register yourself. |

Threading/side notes:
- `open(...)` must run on the **logical server** (`EntityPlayerMP`).
- `openFromClient(...)` (both overloads) are `@SideOnly(Side.CLIENT)`.
- Mod authors should call the `open(...)`/`openClient(...)` methods on the concrete `*GuiFactory`
  instance instead of touching `GuiManager` directly — those methods pick the right `GuiManager`
  entry point and do side verification (`AbstractUIFactory.verifyServerSide`/`verifyClientSide`).

---

## `AbstractUIFactory<T extends GuiData>`

Fully-qualified: `com.cleanroommc.modularui.factory.AbstractUIFactory<T>`

```java
public abstract class AbstractUIFactory<T extends GuiData> implements UIFactory<T> {
    protected AbstractUIFactory(String name);
    @NotNull public abstract IGuiHolder<T> getGuiHolder(T data);
    @Override public final @NotNull String getFactoryName();
    @Override public ModularPanel createPanel(T guiData, PanelSyncManager syncManager, UISettings settings);
    @Override public ModularScreen createScreen(T guiData, ModularPanel mainPanel);
    protected IGuiHolder<T> castGuiHolder(Object o);
    protected static EntityPlayerMP verifyServerSide(EntityPlayer player);
    protected static EntityPlayerSP verifyClientSide(EntityPlayer player);
}
```

Base class every concrete factory in this package extends. It wires `createPanel`/`createScreen`
to `getGuiHolder(data).buildUI(...)`/`createScreen(...)`, and provides:

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getGuiHolder(data)` | gui data | `IGuiHolder<T>` | Abstract — subclasses resolve the holder (tile entity, item, entity, or fixed reference). Must not return null (enforced via `Objects.requireNonNull`). |
| `castGuiHolder(o)` | any object | `IGuiHolder<T>` or `null` | Safe cast helper; returns `null` if `o` isn't an `IGuiHolder` at all (rather than throwing). |
| `verifyServerSide(player)` | `EntityPlayer` | `EntityPlayerMP` | Throws `NullPointerException`/`IllegalStateException` if not a real server player. |
| `verifyClientSide(player)` | `EntityPlayer` | `EntityPlayerSP` | Same for client. |

Inferred: you would only extend this directly if none of the six concrete factories fit your
target object type (e.g. attaching GUIs to a custom non-`TileEntity`/`Item`/`Entity` game object).

---

## Concrete factories

### `TileEntityGuiFactory`

Fully-qualified: `com.cleanroommc.modularui.factory.TileEntityGuiFactory`

```java
public class TileEntityGuiFactory extends AbstractUIFactory<PosGuiData> {
    public static final TileEntityGuiFactory INSTANCE;
}
```

Singleton, factory name `"mui:tile_entity"`. Resolves the `IGuiHolder` by looking up the
`TileEntity` at `data.getTileEntity()` and casting it to `IGuiHolder<PosGuiData>`.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `open(EntityPlayer player, T tile)` | `tile` implements `TileEntity & IGuiHolder<PosGuiData>` | `void` | Server-side. Validates via `verifyTile` (not invalid, same dimension as player), casts `player` to `EntityPlayerMP`. |
| `open(EntityPlayer player, int x, int y, int z)` | coords | `void` | Server-side; skips the tile-in-hand check, looks it up by position on the server through `GuiData`. |
| `openClient(T tile)` (`@SideOnly(CLIENT)`) | tile | `void` | Client-initiated open (routes through `GuiManager.openFromClient`, server round-trip). |
| `openClient(int x, int y, int z)` (`@SideOnly(CLIENT)`) | coords | `void` | Same, by position. |
| `canInteractWith(player, guiData)` | — | `boolean` | `true` iff same player, tile still exists, and within 64 (=8²) blocks squared distance. |
| `verifyTile(player, tile)` *(static)* | — | `void` | Throws if tile is invalid or in a different world than the player. Reused by `SidedTileEntityGuiFactory`. |

**Example** (adapted from `src/main/java/com/cleanroommc/modularui/test/TestBlock.java:29-35` and
`TestTile.java:75,108-109,330`):

```java
// TestBlock.java
public class TestBlock extends Block implements ITileEntityProvider {
    @Override
    public boolean onBlockActivated(World worldIn, int x, int y, int z, EntityPlayer playerIn,
                                     int side, float hitX, float hitY, float hitZ) {
        if (!worldIn.isRemote) {
            GuiFactories.tileEntity().open(playerIn, x, y, z);
        }
        return true;
    }
}

// TestTile.java
public class TestTile extends TileEntity implements IGuiHolder<PosGuiData> {
    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        ModularPanel panel = new ModularPanel("test_tile");
        // ... add widgets, sync values ...
        return panel;
    }
}
```

### `SidedTileEntityGuiFactory`

Fully-qualified: `com.cleanroommc.modularui.factory.SidedTileEntityGuiFactory`

```java
public class SidedTileEntityGuiFactory extends AbstractUIFactory<SidedPosGuiData> {
    public static final SidedTileEntityGuiFactory INSTANCE;
}
```

Singleton, factory name `"mui:sided_tile"`. Identical shape to `TileEntityGuiFactory` but every
`open`/`openClient` overload takes an extra `ForgeDirection facing`, and the holder must implement
`IGuiHolder<SidedPosGuiData>`. Use this instead of `TileEntityGuiFactory` when the GUI content
depends on which face was clicked (e.g. GregTech-style side-configurable machines/covers).

| Method | Params | Returns | Notes |
|---|---|---|---|
| `open(EntityPlayer player, T tile, ForgeDirection facing)` | tile implements `TileEntity & IGuiHolder<SidedPosGuiData>` | `void` | Server-side. |
| `open(EntityPlayer player, int x, int y, int z, ForgeDirection facing)` | coords + facing | `void` | Server-side. |
| `openClient(T tile, ForgeDirection facing)` (`@SideOnly(CLIENT)`) | — | `void` | Client-initiated. |
| `openClient(int x, int y, int z, ForgeDirection facing)` (`@SideOnly(CLIENT)`) | — | `void` | Client-initiated. |
| `canInteractWith` | — | `boolean` | Same 64-block-squared check as `TileEntityGuiFactory` (does not re-check the side). |

Example (constructed, not from repo — no test/ file exercises this factory):

```java
public class SidedMachineTile extends TileEntity implements IGuiHolder<SidedPosGuiData> {
    @Override
    public ModularPanel buildUI(SidedPosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        ForgeDirection clickedSide = data.getSide();
        return new ModularPanel("sided_machine").size(176, 166);
    }
}

// on block activated, server side:
GuiFactories.sidedTileEntity().open(player, x, y, z, ForgeDirection.getOrientation(side));
```

### `EntityGuiFactory`

Fully-qualified: `com.cleanroommc.modularui.factory.EntityGuiFactory`

```java
public class EntityGuiFactory extends AbstractUIFactory<EntityGuiData> {
    public static EntityGuiFactory INSTANCE;
}
```

Singleton, factory name `"mui:entity"`. Resolves the holder by casting the target `Entity` itself
to `IGuiHolder<EntityGuiData>`.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `open(EntityPlayer player, E entity)` | `entity` implements `Entity & IGuiHolder<EntityGuiData>` | `void` | Server-side only (casts `player` to `EntityPlayerMP`; call from server logic). Validates entity is alive and in the same world as the player. |
| `canInteractWith` | — | `boolean` | Player-check + within 64 blocks-squared + same world + entity still alive. |
| `writeGuiData`/`readGuiData` | — | — | Serializes/re-resolves the entity by `getEntityId()` via `world.getEntityByID(id)`. |

No client-initiated `openClient` overload exists on this factory (unlike Tile/PlayerInventory
variants) — Inferred: entity GUIs are expected to always be opened from a server-side interaction
handler (e.g. `Entity.interact`/`onRightClickedByPlayer`), not from client-only code paths.

Example (constructed, not from repo):

```java
public class TalkingNpcEntity extends EntityCreature implements IGuiHolder<EntityGuiData> {
    @Override
    public ModularPanel buildUI(EntityGuiData data, PanelSyncManager syncManager, UISettings settings) {
        return new ModularPanel("npc_dialogue").size(200, 100);
    }
}

// server-side interaction:
@Override
public boolean interactFirst(EntityPlayer player) {
    if (!worldObj.isRemote) {
        EntityGuiFactory.INSTANCE.open(player, this);
    }
    return true;
}
```

### `PlayerInventoryGuiFactory`

Fully-qualified: `com.cleanroommc.modularui.factory.PlayerInventoryGuiFactory`

```java
public class PlayerInventoryGuiFactory extends AbstractUIFactory<PlayerInventoryGuiData> {
    public static final PlayerInventoryGuiFactory INSTANCE;
}
```

Singleton, factory name `"mui:player_inv"`. Resolves the holder from the `Item` currently sitting
in the addressed `InventoryType`/slot (`data.getUsedItemStack().getItem()` cast to
`IGuiHolder<PlayerInventoryGuiData>`). Supersedes the deprecated `ItemGuiFactory` — supports any
slot in the player's own inventory (not just main hand) and Baubles slots.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `openFromPlayerInventory(player, index)` | slot index into `InventoryTypes.PLAYER` | `void` | Server-side (verified via `verifyServerSide`). |
| `openFromMainHand(player)` | — | `void` | Convenience: `openFromPlayerInventory(player, player.inventory.currentItem)`. |
| `openFromBaubles(player, index)` | slot index into `InventoryTypes.BAUBLES` | `void` | Throws `IllegalArgumentException` if Baubles isn't loaded. |
| `open(player, InventoryType type, int index)` | arbitrary registered type | `void` | Generic entry point for custom `InventoryType`s. |
| `openFromPlayerInventoryClient(index)` (`@SideOnly(CLIENT)`) | — | `void` | Client-initiated; uses `Platform.getClientPlayer()`. |
| `openFromMainHandClient()` (`@SideOnly(CLIENT)`) | — | `void` | Client-initiated main-hand convenience. |
| `openFromBaublesClient(index)` (`@SideOnly(CLIENT)`) | — | `void` | Client-initiated. |
| `openClient(InventoryType type, int index)` (`@SideOnly(CLIENT)`) | — | `void` | Client-initiated, generic. |

Example (from `src/main/java/com/cleanroommc/modularui/test/TestItem.java:34,44-73,76-81`):

```java
public class TestItem extends Item implements IGuiHolder<PlayerInventoryGuiData>, ISimpleBauble {

    @Override
    public ModularPanel buildUI(PlayerInventoryGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        IItemHandlerModifiable itemHandler = new ItemStackItemHandler(guiData, 4);
        guiSyncManager.registerSlotGroup("mixer_items", 2);
        if (guiData.getInventoryType() == InventoryTypes.PLAYER) {
            // disable interacting with the item's own slot while its GUI is open
            guiSyncManager.bindPlayerInventory(guiData.getPlayer(), (inv, index) -> index == guiData.getSlotIndex() ?
                    new ModularSlot(inv, index).accessibility(false, false) :
                    new ModularSlot(inv, index));
        }
        ModularPanel panel = ModularPanel.defaultPanel("knapping_gui").resizeableOnDrag(true);
        // ... build widgets ...
        return panel;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack itemStackIn, World worldIn, EntityPlayer player) {
        if (!worldIn.isRemote) {
            GuiFactories.playerInventory().openFromMainHand(player);
        }
        return super.onItemRightClick(itemStackIn, worldIn, player);
    }
}
```

Note the `ItemStackItemHandler(guiData, 4)` constructor takes the `PlayerInventoryGuiData` itself
— it's how the storage tied to *that specific item stack* (via its NBT) is created, keeping the
inventory pinned to the exact stack even as `guiData.getUsedItemStack()` is re-resolved.

### `ItemGuiFactory` (deprecated)

Fully-qualified: `com.cleanroommc.modularui.factory.ItemGuiFactory`

```java
/** @deprecated use {@link PlayerInventoryGuiFactory} */
@Deprecated
public class ItemGuiFactory extends AbstractUIFactory<GuiData> {
    public static final ItemGuiFactory INSTANCE;
}
```

Singleton, factory name `"mui:item"`. Resolves the holder from `player.getHeldItem().getItem()` —
i.e. main-hand only, no slot index tracked. Kept for backward compatibility only.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `open(EntityPlayer player)` | — | `void` | Delegates to `open(EntityPlayerMP)` if possible, else throws `IllegalStateException`. |
| `open(EntityPlayerMP player)` | — | `void` | Server-side. |

Prefer `GuiFactories.playerInventory().openFromMainHand(player)` in new code.

### `ItemStackGuiFactory`

Fully-qualified: `com.cleanroommc.modularui.factory.ItemStackGuiFactory`

```java
public class ItemStackGuiFactory extends AbstractUIFactory<ItemStackGuiData> {
    public ItemStackGuiFactory(String name, IGuiHolder<ItemStackGuiData> guiHolder);
}
```

Not a singleton — you construct your own instance (it self-registers with `GuiManager` in the
constructor). Unlike `PlayerInventoryGuiFactory`/`ItemGuiFactory`, the backing `Item` does **not**
need to implement `IGuiHolder` itself, and the stack doesn't need to be findable in any live
inventory slot — the whole `ItemStack` (via `NetworkUtils.writeItemStack`/`readItemStack`) is
serialized into the open packet. Use this for a GUI keyed off an arbitrary stack value (e.g. one
constructed on the fly, or read from a container that isn't a normal player/tile inventory).

| Method | Params | Returns | Notes |
|---|---|---|---|
| constructor | `name`, `guiHolder` | — | Registers itself; construct once as a `static final` field. |
| `getGuiHolder(data)` | — | `IGuiHolder<ItemStackGuiData>` | Always returns the fixed `guiHolder` passed at construction (ignores the actual item type). |

No `open(...)` convenience method is provided on this class itself — Inferred: callers build an
`ItemStackGuiData` themselves and call `GuiManager.open(factory, data, player)` /
`GuiManager.openFromClient(factory, data)` directly. No test/ file exercises this factory.

Example (constructed, not from repo):

```java
public class TradeReceiptGuiHolder implements IGuiHolder<ItemStackGuiData> {
    public static final ItemStackGuiFactory FACTORY =
            new ItemStackGuiFactory("mymod:trade_receipt", new TradeReceiptGuiHolder());

    @Override
    public ModularPanel buildUI(ItemStackGuiData data, PanelSyncManager syncManager, UISettings settings) {
        ItemStack receipt = data.getItemStack();
        return new ModularPanel("trade_receipt").size(150, 80);
    }
}

// server side, e.g. after a trade completes:
GuiManager.open(TradeReceiptGuiHolder.FACTORY, new ItemStackGuiData(player, receiptStack), (EntityPlayerMP) player);
```

### `SimpleGuiFactory`

Fully-qualified: `com.cleanroommc.modularui.factory.SimpleGuiFactory`

```java
public class SimpleGuiFactory extends AbstractUIFactory<GuiData> {
    public SimpleGuiFactory(String name, IGuiHolder<GuiData> guiHolder);
    public SimpleGuiFactory(String name, Supplier<IGuiHolder<GuiData>> guiHolderSupplier);
}
```

Use for GUIs with no attached game object at all — commands, standalone editors, debug tools.
Not a singleton — construct your own instance; it self-registers with `GuiManager`. "You are
supposed to create one simple factory per GUI to make sure they are same on client and server."

| Method | Params | Returns | Notes |
|---|---|---|---|
| constructor(name, guiHolder) | fixed holder | — | Wraps into `() -> guiHolder`. |
| constructor(name, guiHolderSupplier) | lazy holder | — | Supplier called once, then cached — handy if the holder's constructor needs something not ready at class-init time. |
| `init()` | — | `void` | No-op hook — Inferred: intended as a place to force static-initializer class loading (so the factory registers) without other side effects; call once from mod setup. |
| `open(EntityPlayerMP player)` | — | `void` | Server-side. |
| `openClient()` (`@SideOnly(CLIENT)`) | — | `void` | Client-initiated; uses `Platform.getClientPlayer()`. |

Example (from `src/main/java/com/cleanroommc/modularui/test/ItemEditorGui.java:30-32,134` and
`TestEventHandler.java`):

```java
public class ItemEditorGui implements IGuiHolder<GuiData> {

    private static final SimpleGuiFactory GUI = new SimpleGuiFactory("mui:item_editor", ItemEditorGui::new);

    @Override
    public ModularPanel buildUI(GuiData data, PanelSyncManager syncManager, UISettings settings) {
        ItemStack itemStack = data.getPlayer().getHeldItem();
        // ... build editor panel using the held item ...
        return panel;
    }
}

// elsewhere, e.g. a command handler (server-side):
GUI.open(entityPlayerMP);
```

Note the `Supplier` constructor overload is used here — `ItemEditorGui::new` is only invoked the
first time `getGuiHolder` is called, then cached on the factory instance.

---

## `GuiFactories`

Fully-qualified: `com.cleanroommc.modularui.factory.GuiFactories`

```java
public class GuiFactories {
    public static TileEntityGuiFactory tileEntity();
    public static SidedTileEntityGuiFactory sidedTileEntity();
    public static EntityGuiFactory entity();
    @Deprecated public static ItemGuiFactory item();
    public static PlayerInventoryGuiFactory playerInventory();
    public static SimpleGuiFactory createSimple(String name, IGuiHolder<GuiData> holder);
    public static SimpleGuiFactory createSimple(String name, Supplier<IGuiHolder<GuiData>> holder);
    @ApiStatus.Internal public static void init();
}
```

Pure static accessor facade — no state of its own beyond forwarding to the `INSTANCE` fields of
each built-in factory. `createSimple(...)` is sugar for `new SimpleGuiFactory(...)`.

`init()` is `@ApiStatus.Internal` — called by the library itself during startup to register the
five built-in singleton factories (`tileEntity`, `sidedTileEntity`, `entity`, `item`,
`playerInventory`) with `GuiManager`. Mod authors should not call it themselves; it exists purely
so those factories are registered before any GUI can be opened.

Gotcha: don't call `GuiFactories.item()` in new code — it's marked `@Deprecated` and forwards to
`ItemGuiFactory`; use `GuiFactories.playerInventory()` instead.

---

## `ClientGUI`

Fully-qualified: `com.cleanroommc.modularui.factory.ClientGUI`

```java
@SideOnly(Side.CLIENT)
public class ClientGUI {
    public static void open(@NotNull ModularScreen screen);
    public static void open(@NotNull ModularScreen screen, @NotNull RecipeViewerSettingsImpl recipeViewerSettings);
    public static void open(@NotNull ModularScreen screen, @Nullable Supplier<ModularContainer> container);
    public static void open(@NotNull ModularScreen screen, @NotNull RecipeViewerSettingsImpl recipeViewerSettings, @Nullable Supplier<ModularContainer> container);
    public static void open(@NotNull ModularScreen screen, @NotNull UISettings settings);
    public static void open(GuiScreen screen);
    public static void close();
}
```

Client-only helper for opening a `ModularScreen` (or plain vanilla `GuiScreen`) that is **not**
server-synced — no `UIFactory`/`GuiData`/container plumbing at all. Safe to call from inside an
already-open Modular GUI (e.g. a button handler) because the actual `Minecraft.displayGuiScreen`
call is deferred to next client tick via `GuiManager.openScreen`/`MCHelper.displayScreen`, avoiding
tearing down the current screen mid-render/mid-event.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `open(screen)` | screen | `void` | Default `UISettings` + `RecipeViewerSettings` (dummy, no JEI/REI hookup). |
| `open(screen, recipeViewerSettings)` | + recipe viewer settings | `void` | Wraps into `new UISettings(recipeViewerSettings)`. |
| `open(screen, container)` | + container supplier | `void` | For screens that still want a `ModularContainer` (e.g. slots) without server sync. |
| `open(screen, recipeViewerSettings, container)` | both | `void` | — |
| `open(screen, settings)` | full `UISettings` | `void` | Most flexible overload; others delegate here. |
| `open(GuiScreen)` | plain vanilla screen | `void` | Goes through `MCHelper.displayScreen`, not `GuiManager`. |
| `close()` | — | `void` | `MCHelper.displayScreen(null)`. |

Gotcha: this is for client-only screens (config menus, JEI-style popups) — anything with slots
that need to reflect real server inventory state should go through a `UIFactory`/`GuiManager.open`
instead.

---

## `factory.inventory` — addressing "an inventory owned by a player"

Used exclusively by `PlayerInventoryGuiFactory`/`PlayerInventoryGuiData` to describe *which*
player-owned inventory + slot an item lives in, in a way that can be serialized by ID and looked up
identically on both sides.

### `InventoryType`

Fully-qualified: `com.cleanroommc.modularui.factory.inventory.InventoryType`

```java
public abstract class InventoryType {
    public InventoryType(String id);
    public abstract ItemStack getStackInSlot(EntityPlayer player, int index);
    public abstract void setStackInSlot(EntityPlayer player, int index, ItemStack stack);
    public abstract int getSlotCount(EntityPlayer player);
}
```

Registers itself into a static `id -> InventoryType` map at construction time **if `isActive()`
is true at that time** — subclasses gating on a mod being loaded (see `InventoryTypes.BAUBLES`)
must ensure `isActive()` is stable/correct at class-init. Limited to 16 registered types total
(id is written as a short index over the network — see `write`/`read`; gotcha: construction order
must match between client and server or the wrong type will be read back).

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getId()` | — | `String` | — |
| `isActive()` | — | `boolean` | Default `true`; override to gate on optional mod presence. |
| `findFirstStackable(player, stack)` | player, stack to match | `int` | First slot index whose content can stack with `stack` (or is empty, if `stack` is also empty); `-1` if none. |
| `visitAllStackable(player, stack, visitor)` | + `InventoryVisitor` | `boolean` | Visits only stackable-with-`stack` slots; stops early if the visitor returns `true`. |
| `visitAll(player, visitor)` | + visitor | `boolean` | Visits every slot. |
| `write(buf)` / `read(buf)` *(static)* | `PacketBuffer` | `void` / `InventoryType` | Serializes by string id (`NetworkUtils.writeStringSafe`/`readStringSafe`). |
| `getFromId(id)` *(static)* | — | `InventoryType` | Map lookup. |
| `getAll()` *(static)* | — | `Collection<InventoryType>` | Unmodifiable view of all registered types. |

Two ready-made abstract subclasses adapt this to common inventory backing types:

- **`Inventory`** — for `net.minecraft.inventory.IInventory` (e.g. vanilla `InventoryPlayer`).
  Implement `getInventory(EntityPlayer player)`.
- **`ItemHandler`** — for `IItemHandlerModifiable` (capability-style inventories). Implement
  `getInventory(EntityPlayer player)`.

```java
// Inventory.java
public abstract class Inventory extends InventoryType {
    public Inventory(String id);
    public abstract IInventory getInventory(EntityPlayer player);
    // getStackInSlot/setStackInSlot/getSlotCount implemented via getInventory(player)
}

// ItemHandler.java
public abstract class ItemHandler extends InventoryType {
    public ItemHandler(String id);
    public abstract IItemHandlerModifiable getInventory(EntityPlayer player);
    // getStackInSlot/setStackInSlot/getSlotCount implemented via getInventory(player)
}
```

### `InventoryTypes`

Fully-qualified: `com.cleanroommc.modularui.factory.inventory.InventoryTypes`

```java
public class InventoryTypes {
    public static final InventoryType PLAYER;   // Inventory backed by player.inventory
    public static final InventoryType BAUBLES;  // Inventory backed by PlayerHandler.getPlayerBaubles(player), active only if Baubles is loaded
    public static Collection<InventoryType> getAll();
    public static @Nullable SlotFindResult findFirstStackable(EntityPlayer player, ItemStack stack);
    public static void visitAllStackable(EntityPlayer player, ItemStack stack, InventoryVisitor visitor);
    public static void visitAll(EntityPlayer player, InventoryVisitor visitor);
    public static class SlotFindResult {
        public final InventoryType type;
        public final int slot;
    }
}
```

The built-in registered types, plus multi-type convenience search helpers that scan across *all*
registered `InventoryType`s (not just one) — e.g. "find me the first stackable slot anywhere the
player could hold this item, whether in their main inventory or baubles."

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getAll()` | — | `Collection<InventoryType>` | Delegates to `InventoryType.getAll()`. |
| `findFirstStackable(player, stack)` | — | `SlotFindResult` or `null` | First match across all registered types, in registration order. |
| `visitAllStackable(player, stack, visitor)` | — | `void` | Stops at the first type whose `visitAllStackable` returns `true`. |
| `visitAll(player, visitor)` | — | `void` | Stops at the first type whose `visitAll` returns `true`. |

`InventoryTypes.init()` is `@ApiStatus.Internal` and a no-op body — Inferred: exists only to force
this class to load (and thus register `PLAYER`/`BAUBLES` in their static initializers) at a
controlled point during startup, mirroring the `GuiFactories.init()` pattern.

Real usage — `TestItem.buildUI` (`test/TestItem.java:51-55`) checks
`guiData.getInventoryType() == InventoryTypes.PLAYER` to decide whether to disable the item's own
slot in the rendered player inventory (a Baubles slot isn't shown in that grid, so no special
handling is needed there):

```java
if (guiData.getInventoryType() == InventoryTypes.PLAYER) {
    guiSyncManager.bindPlayerInventory(guiData.getPlayer(), (inv, index) -> index == guiData.getSlotIndex() ?
            new ModularSlot(inv, index).accessibility(false, false) :
            new ModularSlot(inv, index));
}
```

### `InventoryVisitor`

Fully-qualified: `com.cleanroommc.modularui.factory.inventory.InventoryVisitor`

```java
@FunctionalInterface
public interface InventoryVisitor {
    boolean visit(InventoryType type, int index, ItemStack stack);
}
```

Single method, called per-slot by `InventoryType.visitAll(StackAble)`/`InventoryTypes.visitAll(StackAble)`.
Return `true` to stop iteration early (a "found it" signal), `false` to keep visiting.

Example (constructed, not from repo):

```java
InventoryTypes.visitAll(player, (type, index, stack) -> {
    if (stack != null && stack.getItem() == Items.diamond) {
        System.out.println("Found diamond in " + type.getId() + " slot " + index);
        return true; // stop
    }
    return false;
});
```

---

## End-to-end flow (server-initiated open)

1. Mod code calls e.g. `GuiFactories.tileEntity().open(player, x, y, z)`.
2. The factory validates (`verifyTile`) and builds a `PosGuiData`, then calls
   `GuiManager.open(factory, guiData, (EntityPlayerMP) player)`.
3. `GuiManager.open` builds a `PanelSyncManager`, calls `factory.createPanel(...)` →
   `AbstractUIFactory.createPanel` → `getGuiHolder(guiData).buildUI(...)` on your `IGuiHolder`
   (e.g. `TestTile.buildUI`), collects sync handlers, constructs the `ModularContainer`
   (`settings.hasCustomContainer()` ? your supplier : `factory.createContainer()`), assigns a
   window id, and sends an `OpenGuiPacket` (with `factory.writeGuiData(...)`-serialized `GuiData`)
   to the client.
4. Client receives the packet → `GuiManager.openFromClient(windowId, networkId, factory, data, player)`
   → `factory.readGuiData(...)` reconstructs the `GuiData`, `createPanel`/`createScreen` build the
   same UI (from your `IGuiHolder.buildUI`/`createScreen`), a `ModularContainer` and `IMuiScreen`
   wrapper are built, and `MCHelper.displayScreen(...)` shows it.
5. Every server tick, `factory.canInteractWith(player, guiData)` (or your `UISettings`
   override) is checked; returning `false` closes the GUI.

## Ambiguities / things worth double-checking in the library itself

- `ItemStackGuiFactory` has no `open(...)` convenience methods (unlike every other concrete
  factory) — callers must call `GuiManager.open`/`openFromClient` directly with a manually built
  `ItemStackGuiData`. No test/ file in this repo exercises this class, so the intended calling
  convention is inferred from its sibling factories.
- `SidedTileEntityGuiFactory` is likewise not exercised anywhere under `test/`; its documented
  usage is inferred from `TileEntityGuiFactory`'s parallel structure.
- `GuiData.isClient()` delegates to `NetworkUtils.isClient(player)` — not read directly, but by
  name/convention this checks if `player` is a client-side (`EntityPlayerSP`) instance rather than
  querying `world.isRemote`; worth confirming if used for logic that must be strictly
  server-authoritative.
- `SimpleGuiFactory.init()` and `InventoryTypes.init()` both have empty bodies — their only
  effect is forcing static-initializer class loading when called. This is inferred from context
  (mirrors `GuiFactories.init()`), not stated in a comment.
