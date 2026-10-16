# ModularUI2 — Public Modding API Quick Reference

This is a **curated, condensed index** of the classes/methods a third-party mod actually calls to
build a Modular UI: open a GUI, build the widget tree, position/style widgets, add item/fluid
slots, and sync values between client and server. It is derived entirely from the full reference
docs in this folder — nothing here is new information, just filtered and shortened.

**Out of scope on purpose** (framework internals a mod author never calls directly): `GuiManager`'s
packet plumbing, the `widget/sizer` resizer engine, `network.packets.*`, core mixins, animation
internals, `screen.viewport.*`. See the full docs (linked per section) for those.

Every entry below links to the full doc for details — signatures here are trimmed to the
overloads/params you're most likely to need; gotchas are only repeated when they're easy to get
bitten by.

---

## 1. Open a GUI

Full doc: [`internal-docs/factory.md`](internal-docs/factory.md)

Pick one factory based on what the GUI is attached to, implement `IGuiHolder<T>` on your
`TileEntity`/`Item`/`Entity`, and call `.open(...)` from server-side code (or `.openClient(...)`
from client code, which round-trips through the server).

| Your GUI is attached to... | Factory | Your class implements |
|---|---|---|
| A `TileEntity` | `GuiFactories.tileEntity()` | `TileEntity` + `IGuiHolder<PosGuiData>` |
| A `TileEntity`, side-dependent (e.g. covers) | `GuiFactories.sidedTileEntity()` | `TileEntity` + `IGuiHolder<SidedPosGuiData>` |
| An `Entity` | `GuiFactories.entity()` | `Entity` + `IGuiHolder<EntityGuiData>` |
| An item in the player's own inventory/hotbar/baubles | `GuiFactories.playerInventory()` | `Item` + `IGuiHolder<PlayerInventoryGuiData>` |
| An arbitrary `ItemStack` (not a real slot) | `new ItemStackGuiFactory(name, holder)` | any `IGuiHolder<ItemStackGuiData>` |
| Nothing — commands, standalone editors | `new SimpleGuiFactory(name, holder)` | any `IGuiHolder<GuiData>` |

```java
// the interface you implement:
public interface IGuiHolder<T extends GuiData> {
    ModularPanel buildUI(T data, PanelSyncManager syncManager, UISettings settings);
    @SideOnly(Side.CLIENT) default ModularScreen createScreen(T data, ModularPanel mainPanel);
}
```

Per-factory open methods (server-side unless noted `@SideOnly(CLIENT)`):

| Factory | Method |
|---|---|
| `TileEntityGuiFactory` | `open(EntityPlayer, T tile)` · `open(EntityPlayer, int x, int y, int z)` · `openClient(...)` |
| `SidedTileEntityGuiFactory` | same, plus a trailing `ForgeDirection facing` on every overload |
| `EntityGuiFactory` | `open(EntityPlayer, E entity)` — no client-initiated variant |
| `PlayerInventoryGuiFactory` | `openFromMainHand(player)` · `openFromPlayerInventory(player, index)` · `openFromBaubles(player, index)` · `open(player, InventoryType, index)` (+ `*Client()` twins) |
| `ItemStackGuiFactory` | none — call `GuiManager.open(factory, new ItemStackGuiData(player, stack), (EntityPlayerMP) player)` directly |
| `SimpleGuiFactory` | `open(EntityPlayerMP)` · `openClient()` |

**Example** (`test/TestBlock.java` + `test/TestTile.java`):
```java
// block activation, server-side:
GuiFactories.tileEntity().open(playerIn, x, y, z);

// the tile entity:
public class TestTile extends TileEntity implements IGuiHolder<PosGuiData> {
    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        ModularPanel panel = new ModularPanel("test_tile");
        // ... add widgets, sync values ...
        return panel;
    }
}
```

`GuiData`/`PosGuiData`/`EntityGuiData`/`ItemStackGuiData`/`PlayerInventoryGuiData` are the context
objects passed into `buildUI` — see `internal-docs/factory.md` for their getters (`getPlayer()`, `getTileEntity()`,
`getItemStack()`, etc.).

Client-only screens with **no server sync at all** (config menus, popups) skip factories entirely —
use `com.cleanroommc.modularui.factory.ClientGUI.open(ModularScreen)`.

---

## 2. Build the panel

Full doc: [`internal-docs/screen.md`](internal-docs/screen.md)

`buildUI` (from step 1) returns a `ModularPanel` — the root widget container.

```java
public static ModularPanel defaultPanel(@NotNull String name);              // 176x166
public static ModularPanel defaultPanel(@NotNull String name, int w, int h);
public ModularPanel(@NotNull String name);                                  // arbitrary size, call .size(w,h)
```

Fluent setters you'll actually use: `.size(w, h)` / `.coverChildren()`, `.child(widget)`,
`.bindPlayerInventory()` / `.bindPlayerInventory(int bottom)`, `.resizeableOnDrag(boolean)`,
`.padding(int)`.

If you need a `ModularScreen` subclass (e.g. to override `onClose()` or add a keybind), extend
`CustomModularScreen` instead of relying only on `IGuiHolder.buildUI`:

```java
public abstract class CustomModularScreen extends ModularScreen {
    protected CustomModularScreen(@NotNull String owner);
    @ApiStatus.OverrideOnly
    public abstract ModularPanel buildUI(ModularGuiContext context);
}
```

**Example** (`test/TestGui.java`):
```java
public class TestGui extends CustomModularScreen {
    public TestGui() { super(ModularUI.ID); }

    @Override
    public void onClose() { /* ... */ }

    @Override
    public @NotNull ModularPanel buildUI(ModularGuiContext context) {
        ModularPanel panel = ModularPanel.defaultPanel("test");
        panel.child(new ButtonWidget<>().bottom(7).size(12, 12).leftRel(0.5f)
                .overlay(GuiTextures.ADD)
                .onMouseTapped(mouseButton -> { otherPanel.openPanel(); return true; }));
        return panel;
    }
}
```

To open a second floating panel on top of the main one, use
`IPanelHandler.simple(mainPanel, builder, autoOpen)` (unsynced) or
`syncManager.syncedPanel("key", isSubPanel, builder)` (synced — see §6).

---

## 3. Position & style widgets

Full doc: [`internal-docs/widget.md`](internal-docs/widget.md) §1.2, and the `api/widget` interfaces in [`internal-docs/api-core.md`](internal-docs/api-core.md)

Every concrete widget extends `com.cleanroommc.modularui.widget.Widget<W>`, which mixes in
`IPositioned<W>` (position/size), `ITooltip<W>` (tooltips), and `ISynced<W>` (sync binding). All
builder methods return `W` for chaining.

**Position & size** (pixel values relative to the parent's content box unless `relative*`/`Rel`
variants are used):

| Method | Meaning |
|---|---|
| `left(int)` / `right(int)` / `top(int)` / `bottom(int)` | fixed pixel offset from that edge |
| `leftRel(float)` / `topRel(float)` / etc. | fraction (0–1) of the parent's size |
| `width(int)` / `height(int)` / `size(int w,int h)` / `size(int)` (square) | fixed pixel size |
| `widthRel(float)` / `heightRel(float)` / `sizeRel(...)` | fraction of parent size |
| `pos(int x,int y)` / `posRel(float x,float y)` | shorthand for `left+top` |
| `fullWidth()` / `fullHeight()` / `full()` | fill the parent on that axis |
| `expanded()` | flex-fill remaining space along a `Flow` parent's main axis |
| `coverChildren()` / `coverChildrenWidth(int)` / `coverChildrenHeight(int)` | size = children's bounding box |
| `center()` / `horizontalCenter()` / `verticalCenter()` | centers via `leftRel(0.5f)`/`topRel(0.5f)` |
| `padding(int)` / `paddingLeft/Right/Top/Bottom(int)` | shrinks the box children measure against |
| `margin(int)` / `marginLeft/Right/Top/Bottom(int)` | space reserved around this widget |

**Background / overlay**:

| Method | Purpose |
|---|---|
| `background(IDrawable...)` | replaces the theme background entirely |
| `backgroundOverlay(IDrawable...)` | drawn after the theme background (doesn't disable it) |
| `overlay(IDrawable...)` | drawn on top of everything (icons, text via `IKey.asWidget()`-like content) |
| `hoverBackground(...)` / `hoverOverlay(...)` | hover-state variants |
| `widgetTheme(String id)` | pick a specific theme entry, e.g. `IThemeApi.BUTTON` |

**Tooltips** (`ITooltip<W>`):

```java
W tooltip(Consumer<RichTooltip> tooltipConsumer);       // one-time builder
W tooltipBuilder(Consumer<RichTooltip> tooltipBuilder); // re-run every time the tooltip is dirty (dynamic content)
W addTooltipLine(String line);                          // quick single line
```

**Example** (`test/TestGui.java`):
```java
new Widget<>()
        .addTooltipLine(line)
        .widgetTheme(IThemeApi.BUTTON)
        .overlay(IKey.str(line))
        .expanded().heightRel(1f)
```

---

## 4. Common widgets

Full docs: [`internal-docs/widgets-core.md`](internal-docs/widgets-core.md), [`internal-docs/widgets-sub.md`](internal-docs/widgets-sub.md)

| Widget | Purpose | Key builder methods |
|---|---|---|
| `ButtonWidget<W>` | clickable widget | `.onMousePressed(...)` / `.onMouseTapped(...)`; static `ButtonWidget.panelCloseButton()` |
| `TextWidget<W>` (`new TextWidget<>(IKey)`, or `IKey.asWidget()`) | draw a line of text | `.textAlign(Alignment)`, `.color(int)`, `.scale(float)` |
| `ToggleButton` | boolean on/off button | `.value(IBoolValue<?>)`, `.valueWrapped(IIntValue<?>, int trueValue)` |
| `CycleButtonWidget` | cycles through N states | see `internal-docs/widgets-core.md` |
| `ProgressWidget` | machine-style progress bar | `.value(IDoubleValue<?>)`, `.texture(UITexture, int imageSize)`, `.direction(Direction)` |
| `TextFieldWidget` | single-line text/number input | `.value(IStringValue<?>)`, `.numbersInt(min,max)` / `.numbersDouble(min,max)` / `.numbersLong(min,max)` |
| `ItemDisplayWidget` | shows an `ItemStack`, no interaction | `.item(ItemStack)` / `.syncHandler(...)` |
| `FluidDisplayWidget` | shows a `FluidStack`, no interaction | see `internal-docs/widgets-core.md` |
| `Dialog<T>` | modal-ish floating panel helper | see `internal-docs/widgets-core.md` |
| `SortableListWidget<T>` | drag-to-reorder list | see `internal-docs/widgets-core.md` |

**Layout containers** (package `widgets.layout`):

```java
Flow.row() / Flow.column() / Flow.col()   // flexbox-like single-axis container
    .mainAxisAlignment(Alignment.MainAxis maa)
    .crossAxisAlignment(Alignment.CrossAxis caa)
    .childPadding(int)
    .child(widget) / .children(...)

new Grid()                                // row/column matrix container
    .grid(List<List<IWidget>> matrix) / .row(...) / .child(widget)
    .scrollable()
```

**Example** (`test/TestGui.java`):
```java
Flow.row().name("row")
        .child(new Widget<>().addTooltipLine(line).overlay(IKey.str(line)).expanded().heightRel(1f))
        .child(new ButtonWidget<>().onMousePressed(b -> item.removeSelfFromList())
                .overlay(GuiTextures.CROSS_TINY.asIcon().size(10)).width(10).heightRel(1f));
```

---

## 5. Item & fluid slots

Full doc: [`internal-docs/widgets-sub.md`](internal-docs/widgets-sub.md) §slot

| Class | Purpose |
|---|---|
| `ModularSlot(IItemHandler, int index)` | vanilla-facing `Slot` adapting an `IItemHandler`; `.filter(Predicate<ItemStack>)`, `.accessibility(canPut, canTake)`, `.slotGroup(String)` |
| `ItemSlot` | visible widget wrapping a `ModularSlot` — `.slot(ModularSlot)` / `.slot(itemHandler, index)` |
| `PhantomItemSlot` | ghost/no-take variant of `ItemSlot` (recipe filters etc.) |
| `FluidSlot` | fluid-tank widget — `.syncHandler(IFluidTank)` / `.tank(handler, index)` |
| `SlotGroupWidget` | groups slots for shift-click routing; `.builder().matrix("III","III").key('I', i -> new ItemSlot()...).build()`; static `playerInventory(boolean positioned)` |

**Example** (`test/ItemEditorGui.java`, `test/TestTile.java`):
```java
new ItemSlot().slot(new ModularSlot(this.stackHandler, 0));

SlotGroupWidget.builder()
        .matrix("III", "III")
        .key('I', i -> new ItemSlot().slot(new ModularSlot(this.storage, i)))
        .slotGroup("item_inv")
        .build();

new FluidSlot().syncHandler(new FluidSlotSyncHandler(this.fluidStorage, i));

panel.bindPlayerInventory(); // or: panel.child(SlotGroupWidget.playerInventory(false));
```

---

## 6. Sync values between client and server

Full doc: [`internal-docs/value.md`](internal-docs/value.md)

Every `buildUI(data, syncManager, settings)` gets a `PanelSyncManager` (implements
`ISyncRegistrar<PanelSyncManager>`) — call these **identically on both client and server** during
panel construction:

```java
syncManager.syncValue(String name, int id, SyncHandler<?> handler);   // or syncValue(name, handler) / syncValue(id, handler)
syncManager.itemSlot(String key, int id, ModularSlot slot);
syncManager.registerSlotGroup(String name, int rowSize);
syncManager.bindPlayerInventory(EntityPlayer player);
syncManager.syncedPanel(String key, boolean isSubPanel, PanelSyncHandler.IPanelBuilder builder); // -> IPanelHandler
syncManager.getOrCreateSyncHandler(String name, int id, Class<T> type, Supplier<T> factory);
```

Widgets bind to a handler either directly (`widget.syncHandler(handlerInstance)` on slot widgets)
or by key (`widget.syncHandler("name", id)`, resolved from the manager at init).

**Typed sync-value classes** (construct with a getter, and optionally a setter — `null` setter =
server-authoritative, read-only on client):

| Type | Class |
|---|---|
| `boolean` | `BooleanSyncValue(BooleanSupplier, @Nullable BooleanConsumer)` |
| `int` | `IntSyncValue(IntSupplier, @Nullable IntConsumer)` |
| `long` | `LongSyncValue(LongSupplier, @Nullable LongConsumer)` |
| `float` / `double` | `FloatSyncValue` / `DoubleSyncValue` |
| `String` | `StringSyncValue(Supplier<String>, @Nullable Consumer<String>)` |
| `T extends Enum<T>` | `EnumSyncValue<T,S>` |
| arbitrary reference type | `GenericSyncValue.builder(Class<T>).getter(...).setter(...).serializer(...).deserializer(...).build()`, or shortcuts `GenericSyncValue.forItem(...)` / `.forFluid(...)` |
| `List<T>` / `Set<T>` / `Map<K,V>` | `GenericListSyncHandler` / `GenericSetSyncHandler` / `GenericMapSyncHandler` (builder-style, same shape) |

**Example** (`test/TestTile.java`):
```java
IntSyncValue cycleStateValue = new IntSyncValue(() -> this.cycleState, val -> this.cycleState = val);
syncManager.getHyperVisor().syncValue("cycle_state", cycleStateValue);

syncManager.syncValue("progress", new DoubleSyncValue(() -> (double) this.progress / this.duration));

panel.child(new TextFieldWidget()
        .size(60, 14).pos(10, 80)
        .value(new StringSyncValue(() -> s, v -> s = v)));
```

Widgets that don't need networking (single-player-only state, purely client-side widgets) can bind
a plain `com.cleanroommc.modularui.value.*` class instead (`IntValue`, `StringValue`,
`BoolValue.Dynamic`, ...) via `.value(...)` — see `internal-docs/value.md` §"Package `com.cleanroommc.modularui.value`".

---

## 7. Text & drawables

Full docs: [`internal-docs/api-drawable-value.md`](internal-docs/api-drawable-value.md), [`internal-docs/drawable-text.md`](internal-docs/drawable-text.md), [`internal-docs/drawable-core.md`](internal-docs/drawable-core.md)

Almost everything that takes an `IDrawable`/text argument (`overlay(...)`, tooltip lines, `TextWidget`)
is built from `com.cleanroommc.modularui.api.drawable.IKey`'s static factories:

```java
IKey.str(String text);          // literal text
IKey.lang(String key, Object... args);  // localized text
IKey.comp(IKey... parts);       // composed/concatenated text
IKey.dynamic(Supplier<String> supplier); // recomputed text
key.asWidget();                 // -> TextWidget<?>
key.withStyle();                // -> StyledText (color/scale/shadow/alignment)
key.withAnimation();            // -> AnimatedText (typewriter reveal)
```

`GuiTextures` holds the library's built-in `UITexture`/`IDrawable` constants (buttons, icons,
slots, progress bars) usable directly as `overlay(...)`/`background(...)` arguments, e.g.
`GuiTextures.ADD`, `GuiTextures.CROSS_TINY`, `GuiTextures.PROGRESS_ARROW`.

---

## Full documentation index

| Topic | File |
|---|---|
| Opening GUIs | `internal-docs/factory.md` |
| Screens, panels, tooltips | `internal-docs/screen.md` |
| Widget positioning/sizing model | `internal-docs/widget.md` |
| Built-in widgets | `internal-docs/widgets-core.md`, `internal-docs/widgets-sub.md` |
| Value binding & client/server sync | `internal-docs/value.md` |
| Text, rich text, icons | `internal-docs/drawable-text.md`, `internal-docs/drawable-core.md`, `internal-docs/api-drawable-value.md` |
| Theming | `internal-docs/theme.md` |
| Animations | `internal-docs/animation.md` |
| Networking internals | `internal-docs/network.md` |
| NEI/recipe-viewer integration | `internal-docs/integration.md` |
| Fake-world rendering, item/fluid handler shims, misc utilities | `internal-docs/utils-core.md`, `internal-docs/utils-sub.md` |
| Coremod/mixin internals | `internal-docs/core.md` |
| Root-level proxy/config/error classes | `internal-docs/root.md` |
