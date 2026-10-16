# `widgets` Subpackages: layout, menu, slot, textfield

This document covers four subpackages of `com.cleanroommc.modularui.widgets`:

- **`widgets.layout`** — the flexbox-like layout system (`Flow`, `Grid`, and the deprecated `Row`/`Column` presets).
- **`widgets.menu`** — context-menu / dropdown UI (`Menu`, `MenuPanel`, `AbstractMenuButton`, `ContextMenuButton`, `DropdownWidget`).
- **`widgets.slot`** — inventory slot widgets (`ItemSlot`, `PhantomItemSlot`, `FluidSlot`, `ModularSlot`, `ModularCraftingSlot`, `SlotGroup`/`PlayerSlotGroup`, `InventoryCraftingWrapper`).
- **`widgets.textfield`** — text input widgets (`TextFieldWidget`, `TextEditorWidget`, and their internals).

Examples are adapted from `src/main/java/com/cleanroommc/modularui/test/` (`TestGui.java`, `TestGuis.java`, `TestTile.java`, `TestItem.java`, `ItemEditorGui.java`, `CraftingModularContainer.java`) unless marked "constructed, not from repo".

---

## layout

Package: `com.cleanroommc.modularui.widgets.layout`.

### Overview / the layout algorithm

`Flow` is ModularUI2's flexbox equivalent: a `ParentWidget` that arranges its children along one **main axis** (`GuiAxis.X` for a row, `GuiAxis.Y` for a column), with alignment control on both the main axis (`Alignment.MainAxis`) and the perpendicular **cross axis** (`Alignment.CrossAxis`).

Layout happens in two widget-tree passes, matching `ILayoutWidget`'s contract:

1. **`layoutWidgets()`** (main axis pass): reads `resizer().dependsOnChildren(axis)` / `isSizeCalculated(axis)` to decide whether the flow's own main-axis size is already known. If not known and not `START`-aligned (and not `coverChildren`), it **returns `false`** and layout is retried later once sizes resolve — this is the standard ModularUI2 multi-pass resizer negotiation, not a bug.
   Children are split into one or more `SimpleFlow` "rows" via `buildWrappedFlows`: normally a single `SimpleFlow` holding every non-ignored child; if `wrap(true)` is set, a new `SimpleFlow` is started whenever the next child would overflow the available main-axis size (or immediately after any expanded child, per a `// TODO: is this desirable?` comment in the source). Children whose main-axis position is fixed (`resizer().hasPos(axis)`), or which are collapsed via `shouldIgnoreChildSize`, are excluded from flow rows entirely and positioned by their own resizer instead.
   Each `SimpleFlow.layout(...)` then: resolves `Alignment.MainAxis` (falling back to `CENTER` for `SPACE_BETWEEN`/`SPACE_AROUND` when there's only 0–1 children; forcing `START` when any child in the row is expanded), computes inter-child spacing (`childPadding`, or the computed gap for `SPACE_BETWEEN`/`SPACE_AROUND`), grows any expanded children to fill leftover space divided evenly among them, then walks the children left-to-right (or top-to-bottom) accumulating position.
2. **`postLayoutWidgets()`** (cross axis pass): delegates to the static helper `Flow.layoutCrossAxisListLike`, which computes each `SimpleFlow` row's cross-axis extent (`calculateCrossAxisSize`), and — when wrapped into multiple rows — stacks the rows along the cross axis according to `Alignment.CrossAxis`, inserting `crossAxisChildPadding` between them. Each row's individual children are then aligned within that row's cross-axis span (start/center/end) via `SimpleFlow.layoutCrossAxis`. Children with a fixed cross-axis position are skipped.

The engine only ever produces `true` once every affected widget's position (and, for expanded children, size) has actually been resolved; if required sizes aren't known yet it returns `false` so the surrounding layout system re-invokes it after a later pass.

`Grid` is a **separate, simpler algorithm**: not a `Flow` subclass. It stores children in an explicit `List<List<IWidget>>` matrix (rows of columns), computes each row's height and each column's width as the max over that row/column's children (respecting per-border margin rules — the first/last row or column relaxes margin merging against `minElementMargin`), then walks the matrix placing each child at the accumulated `(x, y)` for its column/row and aligning it within that cell via an `Alignment` (default `Alignment.Center`, optionally overridden per-row or per-column). `Grid` also extends `AbstractScrollWidget`, so it can be made scrollable.

### `com.cleanroommc.modularui.widgets.layout.Flow`

Generic single-axis flex container; also the base of the deprecated `Row`/`Column` classes. Static factories `row()`/`column()`/`col()` are the only supported way to obtain the two orientations going forward.

```java
public class Flow extends ParentWidget<Flow> implements ILayoutWidget
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `static Flow row()` | — | new `Flow` on `GuiAxis.X` | main axis = horizontal |
| `static Flow column()` | — | new `Flow` on `GuiAxis.Y` | main axis = vertical |
| `static Flow col()` | — | new `Flow` on `GuiAxis.Y` | alias of `column()` |
| `isRow()` / `isColumn()` | — | `boolean` | based on `getAxis().isHorizontal()`/`isVertical()` |
| `getAxis()` | — | `GuiAxis` | the fixed main axis (set in constructor, not mutable after) |
| `getMaa()` / `getCaa()` | — | `Alignment.MainAxis` / `Alignment.CrossAxis` | current alignment |
| `getChildPadding()` / `getCrossAxisChildPadding()` | — | `int` | current gaps |
| `isCollapseDisabledChild()` / `isReverseLayout()` / `isWrap()` | — | `boolean` | current flags |

Constructors: `Flow(GuiAxis axis)` is public but normally you use the `row()`/`column()`/`col()` factories; it sets `resizer(new ExpanderResizer(this, axis))` and defaults to `sizeRel(1f, 1f)` (fills its parent unless overridden).

Fluent configuration methods:

| Method | Params | Returns | Purpose / gotchas |
|---|---|---|---|
| `mainAxisAlignment(Alignment.MainAxis maa)` | main-axis alignment | `Flow` | `START` (default), `CENTER`, `END`, `SPACE_BETWEEN`, `SPACE_AROUND` |
| `crossAxisAlignment(Alignment.CrossAxis caa)` | cross-axis alignment | `Flow` | `START`, `CENTER` (default), `END` |
| `childPadding(int spaceBetween)` | pixels | `Flow` | fixed gap between consecutive children on the main axis; **ignored** if `maa` is `SPACE_BETWEEN`/`SPACE_AROUND` |
| `collapseDisabledChild()` / `collapseDisabledChild(boolean)` | — / flag | `Flow` | when true, disabled children are excluded from layout entirely (no gap left behind); default is false |
| `reverseLayout()` / `reverseLayout(boolean)` | — / flag | `Flow` | lays out the children list back-to-front |
| `wrap()` / `wrap(boolean)` | — / flag | `Flow` | **Experimental.** Wraps overflowing children into additional rows/columns. Incompatible with a main axis that covers its children (a warning is logged and wrap is force-disabled in that case) |
| `crossAxisChildPadding(int)` | pixels | `Flow` | **Experimental.** Gap between wrapped rows/columns; only applies when `wrap()` is active |
| `children(Iterable<IWidget>)` / `children(int amount, IntFunction<IWidget>)` / `children(Iterable<T>, Function<T, IWidget>)` | widgets / count+factory / items+mapper | `Flow` | bulk-add convenience; each simply calls `child(...)` in a loop |

Overrides worth knowing: `getDefaultWidth()`/`getDefaultHeight()` compute a natural size from children along/across the axis (`getDefaultMainAxisSize()`/`getDefaultCrossAxisSize()`), used when the flow is set to cover its children. `canCoverByDefaultSize(axis)` returns true only for the cross axis (a `Flow` can't cleanly cover-children on its own main axis while also being asked to report a default main-axis size — this matters for the `wrap()` restriction above). `getTypeName()` returns `"Row"` or `"Column"` for debug/logging based on orientation.

**Example** (`TestGui.java:58-67`, a row per sortable-list item, and `TestGuis.java:130-176`, a top-level column):
```java
// row: label + remove button
Flow.row().name("row_" + line)
        .child(new Widget<>()
                .addTooltipLine(line)
                .widgetTheme(IThemeApi.BUTTON)
                .overlay(IKey.str(line))
                .expanded().heightRel(1f))
        .child(new ButtonWidget<>()
                .onMousePressed(button -> item.removeSelfFromList())
                .overlay(GuiTextures.CROSS_TINY.asIcon().size(10))
                .width(10).heightRel(1f));

// column: title + expanding list
Flow.column()
        .child(IKey.str("Client Test UIs").asWidget().margin(1))
        .child(new ListWidget<>().widthRel(1f).expanded()
                .children(uiMethods.size(), i -> /* ... */ null));
```

Wrapping example (`TestGuis.java:657-684`, `buildWrappedFlowUI`):
```java
Flow.row()
        .wrap()
        .widthRel(1f)
        .coverChildrenHeight()
        .padding(2)
        .crossAxisAlignment(Alignment.CrossAxis.START)
        .mainAxisAlignment(Alignment.MainAxis.CENTER)
        .childPadding(2)
        .crossAxisChildPadding(2)
        .children(5, i -> /* random-sized rectangle widget */ null);
```

### `com.cleanroommc.modularui.widgets.layout.Grid`

Matrix layout container: children are placed in an explicit row/column grid, sized to the max width/height needed in that column/row, with per-cell alignment. Extends `AbstractScrollWidget`, so `scrollable()` makes it a scrollable panel-like widget.

```java
public class Grid extends AbstractScrollWidget<IWidget, Grid> implements ILayoutWidget, IParentWidget<IWidget, Grid>
```

Construction / population:

| Method | Params | Returns | Notes |
|---|---|---|---|
| `new Grid()` | — | — | no-arg; matrix starts empty |
| `grid(List<List<I>> matrix)` | row-major matrix of widgets (rows may be ragged — `null`/short rows get padded with `null` cells during `sanitizeMatrix()`) | `Grid` | replaces the whole matrix; wires children in if the widget is already valid |
| `row(List<IWidget> row)` / `row(IWidget... row)` | one row of widgets | `Grid` | appends a row |
| `child(IWidget widget)` | one widget, may be `null` | `Grid` | appends to the **last** row (creates the first row if the matrix is empty) — this is how `Grid` implements `IParentWidget.child(...)`-style single-widget building |
| `nextRow()` | — | `Grid` | starts a new (initially empty) row for subsequent `child(...)` calls |
| `addChild(IWidget child, int index)` | widget, insertion index (supports negative indices, Python-style from the end) | `boolean` | lower-level `IParentWidget` hook; adds directly to the flat children list, not the matrix — mixing this with `grid()`/`row()`/`child()` is possible but unusual |

Matrix-builder helpers (static factories producing `List<List<I>>`, consumed by `grid(...)`, or convenience instance wrappers that call `grid(...)` for you):

| Method | Params | Returns | Purpose |
|---|---|---|---|
| `static createGridOfWidthHeight(int width, int height, GridPosMapper<I>)` | row length, row count, `(x, y, index) -> widget` | matrix | fixed rectangular grid |
| `static createGridOfSizeHeight(int size, int height, GridPosMapper<I>)` | total count, row count | matrix | width computed as `ceil(size / height)` |
| `static createGridOfSizeWidth(int size, int width, GridPosMapper<I>)` | total count, row length | matrix | height computed as `ceil(size / width)` |
| `static createGridOfElements(Iterable<? extends Iterable<T>>, GridPosElementMapper<T, I>)` | ragged 2D source data, `(x, y, index, element) -> widget` | matrix | maps an arbitrary 2D structure |
| `static createGridOfWidthElements(int width, Iterable<T>, GridPosElementMapper<T, I>)` | row length, flat source list, mapper | matrix | flat list wrapped into rows of `width` — the function `TestGui.java:72-85` uses directly |
| `gridOfWidthHeight` / `gridOfSizeHeight` / `gridOfSizeWidth` / `gridOfElements` / `gridOf(int width, Iterable<I>)` / `gridOfWidthElements` | same params as the `static create...` counterparts | `Grid` | instance methods that build the matrix and immediately call `grid(...)` on `this` |
| `matrix(List<List<I>>)` | — | `Grid` | **Deprecated**, scheduled for removal in 3.4 — use `grid(...)` |
| `mapTo(...)` (3 overloads) | — | `Grid` | **Deprecated**, scheduled for removal in 3.4 — use `gridOfWidthElements`/`gridOfWidthHeight` etc. |

Sizing / appearance:

| Method | Params | Returns | Notes |
|---|---|---|---|
| `minColWidth(int)` / `minRowHeight(int)` | pixels | `Grid` | floor for column width / row height even if all cells in it are empty or smaller; default 5 |
| `alignment(Alignment)` | alignment | `Grid` | default per-cell alignment; default `Alignment.Center` |
| `rowAlignments(Alignment[])` | one alignment per row | `Grid` | overrides `alignment` per row; array length must equal row count or the override is dropped with a logged warning at layout time |
| `columnAlignments(Alignment[])` | one alignment per column | `Grid` | overrides `alignment` per column; same length-check caveat. Setting one of `rowAlignments`/`columnAlignments` clears the other's override flag (mutually exclusive) |
| `minElementMargin(int)` / `minElementMargin(int h, int v)` / `minElementMargin(int l, int r, int t, int b)` / `minElementMarginLeft/Right/Top/Bottom(int)` | pixel margin(s) | `Grid` | a minimum margin enforced only on non-edge (interior) sides of a cell — border cells (first/last row or column) don't get this minimum applied on their outer edge, see the `border(index, size)` helper in the source |
| `collapseDisabledChild()` | — | `Grid` | ignore disabled children's size when computing row/column extents (mirrors `Flow`'s option) |
| `scrollable()` | — | `Grid` | enables both-axis scrolling with default `VerticalScrollData`/`HorizontalScrollData` |
| `scrollable(ScrollData)` / `scrollable(VerticalScrollData, HorizontalScrollData)` | scroll config | `Grid` | custom scroll data |

Getters `getMinElementMargin()`, `getMinRowHeight()`, `getMinColWidth()`, `getAlignment()`, `isCollapseDisabledChild()` mirror the setters above.

Gotchas from source: `getChildren()` lazily flattens the matrix into the underlying children list (`makeFlatList()`) only when `dirty` — any direct matrix mutation must go through the documented methods (which set `dirty = true`) or the flat list won't update. `sanitizeMatrix()` pads short rows with `null` cells so every row has equal length before layout; `null` cells are simply skipped (`shouldIgnoreChildSize` treats `child == null` as ignorable).

**Example** (`TestGui.java:72-103`, real usage combining `createGridOfWidthElements` and `.grid(...).scrollable()`):
```java
List<List<AvailableElement>> availableMatrix = Grid.createGridOfWidthElements(2, this.lines, (x, y, index, value) -> {
    AvailableElement availableElement = new AvailableElement().overlay(IKey.str(value))
            .widthRel(0.5f).height(14)
            .addTooltipLine(value)
            .onMousePressed(mouseButton1 -> { /* ... */ return true; });
    this.availableElements.put(value, availableElement);
    return availableElement;
});
// ... later, inside a dialog panel:
panel1.child(new Grid()
        .grid(availableMatrix)
        .scrollable()
        .pos(7, 7).right(16).bottom(7).name("available list"));
```

Fixed-size grid example (`TestGuis.java:185-213`, `buildToggleGridListUI`):
```java
new Grid()
        .left(0)
        .coverChildren()
        .gridOfWidthHeight(4, 4, (x, y, j) -> new ToggleButton()
                .overlay(GuiTextures.BOOKMARK)
                .value(new BoolValue.Dynamic(() -> states[i][j], val -> states[i][j] = val))
                .size(10)
                .margin(1)
                .name("G:" + i + ",W:" + j));
```

### `com.cleanroommc.modularui.widgets.layout.Row` / `com.cleanroommc.modularui.widgets.layout.Column`

```java
@ApiStatus.ScheduledForRemoval(inVersion = "3.3.0")
@Deprecated
public class Row extends Flow { public Row() { super(GuiAxis.X); } }

@ApiStatus.ScheduledForRemoval(inVersion = "3.3.0")
@Deprecated
public class Column extends Flow { public Column() { super(GuiAxis.Y); } }
```

Both are **deprecated, scheduled for removal in 3.3.0** — thin `Flow` subclasses with a fixed axis and no additional behavior. Use `Flow.row()` / `Flow.column()` instead. Not exercised anywhere in `test/`; `TestGui.java`/`TestGuis.java` consistently use `Flow.row()`/`Flow.column()`/`Flow.col()`.

### `com.cleanroommc.modularui.widgets.layout.SimpleFlow`

Internal helper, not a widget — represents one laid-out row/column of a `Flow` (a single "line" after wrap splitting).

```java
public class SimpleFlow
```

Public fields: `widgets` (the `List<IWidget>` in this line), `size` (accumulated main-axis size), `expanderCount`, `crossSize`, `crossSizeCalculated`.

| Method | Params | Returns | Purpose |
|---|---|---|---|
| `layout(GuiAxis axis, int availableSize, Box padding, Alignment.MainAxis maa, int childPadding)` | — | `void` | positions this line's widgets along the main axis; see the layout-algorithm description above |
| `calculateCrossAxisSize(GuiAxis axis)` | — | `void` | computes `crossSize` as the max cross-axis extent among this line's widgets |
| `layoutCrossAxis(IWidget parent, GuiAxis axis, Alignment.CrossAxis caa, int availableSize, int p, Box padding)` | — | `boolean` | positions this line's widgets on the cross axis; returns `false` if a size dependency isn't resolved yet |

Not intended for direct consumer use — it's `Flow`'s and `Flow.layoutCrossAxisListLike`'s implementation detail, documented here only because it clarifies how `Flow`'s two-pass algorithm actually works.

### `com.cleanroommc.modularui.widgets.layout.IExpander`

```java
public interface IExpander {
    GuiAxis getExpandAxis();
}
```

Marker/query interface: something that knows which `GuiAxis` it expands along. **Inferred:** implemented by resizer types (e.g. `ExpanderResizer`, used internally by `Flow`) to let the `Flow`/`SimpleFlow` layout code find widgets marked `.expanded()` and grow them to fill leftover main-axis space; not something a typical UI author implements or calls directly.

---

## menu

Package: `com.cleanroommc.modularui.widgets.menu`.

Menus are built from a small button + panel/container triad:

- **`AbstractMenuButton`** — a widget that, on click or hover, opens a floating `Menu` (either inside the current panel via a `MenuPanel`, or as its own panel through an `IPanelHandler`).
- **`Menu`** — the floating container widget itself; a thin `ParentWidget` that defaults each child to `height(12)`/`widthRel(1f)` if unset.
- **`MenuPanel`** — the (experimental) full-screen invisible `ModularPanel` a menu button opens into when it isn't nested inside another `Menu`; supports "sub menus" opening within the same panel.
- **`ContextMenuButton`** — concrete button that shows a user-supplied `Menu` (or a quick `ListWidget` via `menuList(...)`).
- **`DropdownWidget<T>`** — concrete button that shows a list of selectable values of type `T` and displays/syncs the current selection.
- **`IMenuPart`** — shared hover-tracking contract implemented by both `Menu` and `AbstractMenuButton`.

### `com.cleanroommc.modularui.widgets.menu.AbstractMenuButton<W>`

Base class for any button that opens a floating menu widget.

```java
public abstract class AbstractMenuButton<W extends AbstractMenuButton<W>> extends Widget<W> implements IMenuPart, Interactable
```

Constructor: `AbstractMenuButton(String panelName)` — `panelName` is required (non-null) and is also used as the button's own widget `name(...)`; it becomes the name of the `MenuPanel` created if this button ever needs to open its own panel.

Subclasses must implement `protected abstract Menu<?> createMenu()` — called lazily the first time the menu widget is needed; if it returns `null`, a fallback `Menu` reading "No Menu supplied" in red is used instead.

| Method | Params | Returns | Purpose / gotchas |
|---|---|---|---|
| `isOpen()` | — | `boolean` | true if the menu is open, soft or hard |
| `isSoftOpen()` | — | `boolean` | true if it was opened by hover rather than click |
| `toggleMenu(boolean soft)` | `soft` = triggered by hover vs. click | `void` | click-toggles between open/closed; a soft (hover) open on top of an existing hard-open just no-ops, and a hard click on a soft-open menu "upgrades" it to hard-open rather than closing it |
| `openMenu(boolean soft)` | — | `void` | opens the menu: if nested inside a `Menu` whose panel is a `MenuPanel`, calls `menuPanel.openSubMenu(getMenu())`; otherwise opens via its own `IPanelHandler` (lazily created, backed by a new `MenuPanel`) |
| `closeMenu(boolean soft)` | — | `void` | inverse of `openMenu`; a soft-close request is ignored if the menu was hard-opened |
| `getMenu()` (`protected`) | — | `Menu<?>` | lazily creates via `createMenu()` (or the fallback menu), then (re-)applies `relative(this)` positioning and the configured `direction`, and points the menu back at this button via `setMenuSource` |
| `setMenu(Menu<?> menu)` (`protected`) | — | `void` | replaces the current menu and invalidates any cached panel (`panelHandler.deleteCachedPanel()`) |

Fields subclasses can read/set directly: `protected Direction direction = Direction.DOWN` (the opening direction shortcut, or `null`/`UNDEFINED` to position the menu manually) and `protected boolean openOnHover = true` (whether hovering alone opens the menu; the menu then auto-closes once neither the button nor any widget in its menu tree is hovered).

`enum Direction`: `UP`, `DOWN`, `LEFT_UP`, `LEFT_DOWN`, `RIGHT_UP`, `RIGHT_DOWN`, `UNDEFINED` — each wraps a `Consumer<StandardResizer>` shortcut (e.g. `DOWN` sets `topRel(1f)`) so the menu widget positions itself relative to the button without manual resizer calls; `UNDEFINED` applies no positioning (caller must position the menu manually).

Interaction overrides of note: `onMousePressed` closes sibling menu buttons in the same `Menu` before toggling this one (so only one sibling submenu is open at a time); `onMouseEnterArea` opens softly if hovering is enabled and no sibling is currently hard-open; `onMouseLeaveArea` triggers `checkClose(true, true)`, which walks up through parent `Menu`s closing softly-opened menus once nothing in the chain is hovered anymore.

Not directly instantiated in `test/` (its two concrete subclasses below are); it exists purely to be extended.

### `com.cleanroommc.modularui.widgets.menu.ContextMenuButton<W>`

Concrete `AbstractMenuButton` for classic "click/hover to reveal a menu" buttons (context menus, submenus).

```java
public class ContextMenuButton<W extends ContextMenuButton<W>> extends AbstractMenuButton<W>
```

Constructor: `ContextMenuButton(String panelName)` — sets `openOnHover = true` by default. `createMenu()` always returns `null` here; the menu must be supplied via `menu(...)` or `menuList(...)`.

| Method | Params | Returns | Purpose |
|---|---|---|---|
| `menu(Menu<?> menu)` | menu widget | `W` | sets the menu directly; typically paired with `widthRel(1f)` + `coverChildrenHeight()` on the menu |
| `menuList(Consumer<ListWidget<IWidget, ?>> builder)` | builder called once | `W` | shortcut: wraps a `ListWidget` (call `builder.accept(list)`; recommend calling `list.maxSize(int)` inside) in a `Menu` sized `widthRel(1f).coverChildrenHeight()` — not meant for further menu customization |
| `direction(Direction)` | direction | `W` | sets the open direction explicitly |
| `openUp()` / `openDown()` / `openLeftUp()` / `openLeftDown()` / `openRightUp()` / `openRightDown()` | — | `W` | shortcuts for `direction(Direction.X)` |
| `openCustom()` | — | `W` | sets `direction(Direction.UNDEFINED)` — position the menu manually |
| `requiresClick()` | — | `W` | shortcut for `openOnHover(false)` |
| `openOnHover(boolean)` | flag | `W` | whether hovering (not just clicking) opens the menu |

**Example** (`TestGuis.java:576-599`, `buildContextMenu` — nested context menu with a submenu):
```java
new ContextMenuButton<>("menu")
        .top(7).width(100).horizontalCenter().height(16)
        .overlay(IKey.str("Menu"))
        .menuList(l -> l
                .maxSize(80)
                .children(options1, s -> IKey.str(s).asWidget())
                .child(new ContextMenuButton<>("sub_menu")
                        .widthRel(1f).height(12)
                        .overlay(IKey.str("Sub Menu"))
                        .openRightDown()
                        .menuList(l1 -> l1
                                .maxSize(80)
                                .children(options2, s -> IKey.str(s).asWidget()))));
```

### `com.cleanroommc.modularui.widgets.menu.DropdownWidget<T, W>`

A button whose menu is an option list; clicking an option selects it, closes the menu, and syncs the value.

```java
public class DropdownWidget<T, W extends DropdownWidget<T, W>> extends AbstractMenuButton<W>
```

Constructor: `DropdownWidget(String panelName, Class<T> valueType)` — `valueType` is used purely to validate the synced `IValue<T>` at bind time (`isValidSyncOrValue`). Sets `openOnHover = false`.

| Method | Params | Returns | Purpose / gotchas |
|---|---|---|---|
| `value(IValue<T> value)` | value handler (sync or plain) | `W` | required before the widget is usable — `onInit()` calls `setValue(this.value.getValue(), false)` and will NPE if no value was set |
| `option(T option)` | one selectable value | `W` | appends to the internal option list |
| `options(Iterable<T>)` / `options(T... options)` | many values | `W` | bulk-add |
| `clearOptions()` | — | `W` | clears the option list |
| `optionToWidget(ToWidget<T> toWidget)` | `(value, forSelectedDisplay) -> IWidget` | `W` | controls how each option (and the current selection) is rendered; defaults to `IKey.str(String.valueOf(v)).asWidget()` if never set |
| `maxVerticalMenuSize(int)` | pixels, default 100 | `W` | caps the height of the generated option `ListWidget` |
| `directionUp()` / `directionDown()` | — | `W` | shortcuts setting `this.direction` directly (bypassing `AbstractMenuButton`'s protected field access pattern) |
| `deleteMenu()` | — | `void` | drops the cached menu (via `setMenu(null)`) so it's rebuilt next time it's opened — needed if the option list changes while the button exists but the menu isn't currently open |
| `getValue()` | — | `IValue<T>` | the bound value handler (may be `null` before `value(...)` is called) |
| `getValueType()` | — | `Class<T>` | the type token passed to the constructor |
| `getMaxListSize()` | — | `int` | current `maxVerticalMenuSize` |

`createMenu()` builds a `Menu` containing a `ListWidget` (capped to `maxListSize`) of `ButtonWidget`s, one per option (via `valueToWidget(v, false)`); clicking an option calls `setValue(v, true)` (which pushes to the bound `IValue` and disposes/rebuilds the "selected" display widget) then `closeMenu(false)`.

`getChildren()` is overridden to return a `MutableSingletonList` holding only the current "selected" display widget — the dropdown button itself only ever shows that one widget, not the option list (which lives in the separate menu panel).

`interface ToWidget<T> { IWidget apply(T value, boolean forSelectedDisplay); }` — the mapper function type for `optionToWidget`.

**Example** (`TestGuis.java:600-620`, item-stack dropdown):
```java
new DropdownWidget<>("test_dropdown", ItemStack.class)
        .top(45).width(100).horizontalCenter()
        .value(itemValue)
        .option(new ItemStack(Items.wooden_door))
        .option(new ItemStack(Items.gold_ingot))
        .option(new ItemStack(Items.apple))
        // ... more options
        .optionToWidget((item, forSelected) -> Flow.row()
                .coverChildrenHeight()
                .padding(4, 1)
                .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
                .child(new ItemDrawable(item).asWidget())
                .child(IKey.str(item.getDisplayName()).asWidget()
                        .widgetTheme(IThemeApi.BUTTON)
                        .invisible()));
```

### `com.cleanroommc.modularui.widgets.menu.IMenuPart`

```java
public interface IMenuPart extends IWidget {
    default boolean isSelfOrChildHovered() { /* ... */ }
}
```

Shared contract implemented by both `Menu` and `AbstractMenuButton`: `isSelfOrChildHovered()` recursively checks whether this widget or any descendant (following nested `IMenuPart`s specially) is currently hovered by the mouse. Used to decide whether an open menu chain should auto-close on hover-leave. **Inferred:** not meant to be implemented by ordinary widgets — only the two menu building blocks.

### `com.cleanroommc.modularui.widgets.menu.Menu<W>`

The floating widget that actually displays menu contents (option lists, submenu buttons, arbitrary widgets).

```java
public class Menu<W extends Menu<W>> extends ParentWidget<W> implements IMenuPart
```

| Method | Params | Returns | Purpose |
|---|---|---|---|
| `checkClose(boolean soft, boolean requireNoHover)` | — | `void` | if this menu has a source button (`menuSource`) and (per the flags) nothing relevant is hovered, tells the source button to close and propagates the check upward |
| `getMenuSource()` | — | `AbstractMenuButton<?>` | the button that opened this menu, if any (set internally via package-private `setMenuSource`) |

`onChildAdd(IWidget child)` is overridden to default any added child's height to `12` and width to `widthRel(1f)` if the child didn't already set a size — this is why option rows in the examples above don't need explicit sizing. `getWidgetThemeInternal` uses the theme's `IThemeApi.PANEL` entry. `onMouseLeaveArea()` triggers `checkClose(true, true)`.

Not constructed directly in `test/` — always built inside `createMenu()` overrides (see `ContextMenuButton`/`DropdownWidget` examples above), e.g. `new Menu<>().widthRel(1f).coverChildrenHeight().child(list)`.

### `com.cleanroommc.modularui.widgets.menu.MenuPanel`

`@ApiStatus.Experimental`. The full-screen, invisible `ModularPanel` that hosts a `Menu` when the menu isn't already nested inside another `Menu` in the widget tree (i.e. the "top level" menu open).

```java
@ApiStatus.Experimental
public class MenuPanel extends ModularPanel
```

Constructor: `MenuPanel(String name, IWidget menu)` — calls `fullScreenInvisible()`, adds `menu` as a child, and applies the `"modularui.context_menu"` theme override.

| Method | Params | Returns | Purpose |
|---|---|---|---|
| `openSubMenu(IWidget menuList)` | another menu widget | `void` | adds it as an additional child — this is how a menu button nested inside an already-open `MenuPanel` shows its submenu in the *same* panel instead of opening a second panel |
| `closeAllMenus(boolean soft, boolean requireNoHover)` | — | `void` | collects every `Menu` under this panel (via `WidgetTree.flatListByType`, collected up-front to avoid concurrent-modification while closing) and calls `checkClose` on each |
| `isDraggable()` | — | `boolean` | always `false` |
| `closeOnOutOfBoundsClick()` | — | `boolean` | always `true` — clicking outside the menu closes it |

`onClose()` calls `closeAllMenus(false, false)` so nested `AbstractMenuButton`s reset their open/soft-open state when the panel itself closes. `onChildAdd` schedules a resize on any added child.

Created internally by `AbstractMenuButton.getPanelHandler()` (`new MenuPanel(this.panelName, getMenu())`) — not constructed directly by consumers in `test/`; you interact with it only indirectly through `AbstractMenuButton`/`ContextMenuButton`/`DropdownWidget`.

---

## slot

Package: `com.cleanroommc.modularui.widgets.slot`.

### Overview

`ItemSlot` is the visible widget wrapping a vanilla `net.minecraft.inventory.Slot`; `ModularSlot` (extends Forge's `SlotItemHandler`) is that underlying vanilla `Slot`, adapted to read/write an `IItemHandler`. `PhantomItemSlot` is an `ItemSlot` variant for ghost/no-take slots (visual only — items are "set" rather than physically transferred, and clicking never removes from the cursor). `FluidSlot` is the parallel widget for fluid tanks (not item-backed) — README's headline "fluid slot" example is exactly `new FluidSlot().syncHandler(new FluidTank(16000))`. `ModularCraftingSlot` is a `ModularSlot` specialization mirroring vanilla's `SlotCrafting` (statistics/achievements, container-item handling, shift-click behavior) for a crafting output slot, paired with `InventoryCraftingWrapper` (an `InventoryCrafting` backed by an `IItemHandlerModifiable`, used so vanilla's `CraftingManager` recipe lookup works against a modular item handler). `SlotGroup`/`PlayerSlotGroup` are non-widget bookkeeping objects that group slots together for sorting and shift-click-into behavior (Inventory BogoSorter integration); `PlayerSlotType` is a small helper enum/classifier for identifying hotbar/main-inventory/armor slots.

### `com.cleanroommc.modularui.widgets.slot.ItemSlot`

Visible item-slot widget; wraps a `ModularSlot` via an `ItemSlotSH` sync handler and renders it like a vanilla inventory slot (including NEI underlay/overlay hooks and drag-splitting visuals).

```java
public class ItemSlot extends Widget<ItemSlot> implements IVanillaSlot, Interactable, RecipeViewerIngredientProvider
```

| Method | Params | Returns | Purpose / gotchas |
|---|---|---|---|
| `static create(boolean phantom)` | `phantom` | `ItemSlot` | returns a `new PhantomItemSlot()` if `true`, else `new ItemSlot()` |
| `slot(ModularSlot slot)` | the slot | `ItemSlot` | wraps it in a new `ItemSlotSH` and calls `syncHandler(...)` — the common entry point |
| `slot(IItemHandlerModifiable itemHandler, int index)` | handler + index | `ItemSlot` | shortcut for `slot(new ModularSlot(itemHandler, index))` |
| `syncHandler(ItemSlotSH syncHandler)` | pre-built sync handler | `ItemSlot` | lower-level entry point if you already have (or share) an `ItemSlotSH`, e.g. via `SyncHandlers.itemSlot(...)` or `syncManager1.getOrCreateSyncHandler(...)` |
| `getSlot()` | — | `ModularSlot` | the underlying vanilla-facing slot (`syncHandler.getSlot()`) |
| `getSyncHandler()` | — | `ItemSlotSH` | throws `IllegalStateException` if not yet synced/initialised |
| `tooltip()` / `itemTooltip()` | — | `RichTooltip` | `tooltip()`/`itemTooltip()` both return the auto-built item tooltip (from `buildTooltip`); override `buildTooltip(ItemStack, RichTooltip)` to customize |

`onInit()` throws `IllegalStateException` if placed in a screen overlay ("Overlays can't have slots!") and fixes the size to `18` (`ItemSlot.SIZE`). `isValidSyncOrValue` requires an `ItemSlotSH` specifically (no plain-value fallback). `getWidgetThemeInternal` selects a themed background based on `PlayerSlotType` (`ITEM_SLOT_PLAYER_HOTBAR`/`_MAIN_INV`/`_ARMOR`) when the sync handler reports one, otherwise the generic `ITEM_SLOT` theme. Mouse handling (`onMousePressed`/`onMouseRelease`/`onMouseDrag`) all delegate to `ClientScreenHandler`'s vanilla-slot-click emulation rather than implementing pickup logic itself.

**Example** (`ItemEditorGui.java:72`, simplest possible use):
```java
new ItemSlot().slot(new ModularSlot(this.stackHandler, 0))
```

**Example** (`TestTile.java:218-223`, a 3×3 storage grid tied into a shift-click group, via `SlotGroupWidget`):
```java
SlotGroupWidget.builder()
        .matrix("III", "III")
        .key('I', i -> new ItemSlot().slot(new ModularSlot(this.storage, i)))
        .slotGroup("item_inv")
        .build();
```

**Example** (`TestItem.java:59-69`, using a pre-built sync handler and per-slot `.filter(...)`/`.slotGroup(...)`):
```java
new ItemSlot().slot(SyncHandlers.itemSlot(itemHandler, index)
        .ignoreMaxStackSize(true)
        .slotGroup("mixer_items"))
```

### `com.cleanroommc.modularui.widgets.slot.PhantomItemSlot`

Ghost/no-take variant of `ItemSlot`: items shown here are visual-only configuration (e.g. recipe filters), never physically removable from the slot by the player and never affect the held-item cursor stack on click.

```java
public class PhantomItemSlot extends ItemSlot implements RecipeViewerGhostIngredientSlot<ItemStack>
```

Key differences from `ItemSlot`, all visible in source:
- `isValidSyncOrValue` requires a `PhantomItemSlotSH` (not the plain `ItemSlotSH`).
- `slot(ModularSlot)` / `syncHandler(ItemSlotSH)` are overridden to wrap in `PhantomItemSlotSH` instead of `ItemSlotSH`.
- `onMousePressed` / `onMouseScroll` sync a click/scroll action to the server (`PhantomItemSlotSH.SYNC_CLICK` / `SYNC_SCROLL`) instead of running vanilla pickup logic; `onMouseRelease` is a no-op returning `true`.
- `handleAsVanillaSlot()` returns `false` — vanilla slot-click machinery (shift-click transfer, drag-splitting, etc.) does not apply to it.
- `handleDragAndDrop(ItemStack, int)` (recipe-viewer ghost-ingredient drag support) validates the dragged stack against the sync handler and calls `updateFromClient(...)`, then zeroes the dragged stack's size (since nothing is actually being taken/placed).

**Example** (`TestTile.java:291-294`):
```java
SlotGroupWidget.builder()
        .matrix("III")
        .key('I', i -> new PhantomItemSlot().slot(new ModularSlot(this.phantomStorage, i).ignoreMaxStackSize(true)))
        .build();
```

### `com.cleanroommc.modularui.widgets.slot.FluidSlot`

The fluid-tank equivalent of `ItemSlot` — displays and edits a single `IFluidTank`/`FluidStack` (via a `FluidSlotSyncHandler`), not backed by a vanilla `Slot` at all. README's headline example.

```java
public class FluidSlot extends AbstractFluidDisplayWidget<FluidSlot> implements Interactable, RecipeViewerGhostIngredientSlot<FluidStack>
```

Construction / binding:

| Method | Params | Returns | Purpose |
|---|---|---|---|
| `syncHandler(IFluidTank fluidTank)` | a raw tank (e.g. Forge's `FluidTank`) | `FluidSlot` | wraps it in a `new FluidSlotSyncHandler(fluidTank)` — the simplest binding, used directly in README's example |
| `syncHandler(IMultiFluidTankHandler fluidTank, int index)` | multi-tank handler + index | `FluidSlot` | shortcut for `syncHandler(fluidTank.getFluidTank(index))` |
| `syncHandler(FluidSlotSyncHandler syncHandler)` | pre-built sync handler | `FluidSlot` | lower-level entry point, e.g. when you need `new FluidSlotSyncHandler(tank).phantom(true)` for a ghost fluid slot |
| `tank(IFluidTank)` / `tank(IMultiFluidTankHandler, int)` | same as above | `FluidSlot` | aliases of the corresponding `syncHandler(...)` overloads |
| `getSyncHandler()` | — | `FluidSlotSyncHandler` | throws `IllegalStateException` if not yet synced/initialised |
| `getFluidStack()` | — | `FluidStack` (nullable) | current fluid, or `null` if unsynced/empty |
| `getFluidTank()` | — | `IFluidTank` | the underlying tank, or a shared empty dummy tank if unsynced |
| `alwaysShowFull(boolean)` | flag, default `true` | `FluidSlot` | if true, the fluid always renders as visually "full" instead of proportional to its actual amount — read by `getCapacity()` (returns `0`, meaning "always full", unless disabled) |
| `isAlwaysShowFull()` | — | `boolean` | current value |
| `addAdditionalFluidInfo(RichTooltip, FluidStack)` | — | `void` | override point for extra tooltip lines beyond the built-in amount/capacity text |
| `formatFluidTooltipAmount(double)` | — | `String` | override point for the tooltip's number formatting (`DecimalFormat("#.##")` by default) |

Deprecated (scheduled for removal 3.2.0): `contentOffset(int x, int y)` → use `contentPaddingLeft(x).contentPaddingTop(y)`; `overlayTexture(IDrawable)` → use `overlay(IDrawable)`.

Built-in tooltip (`addToolTip`, wired via `tooltipBuilder(this::addToolTip)` in the constructor, auto-updating) shows fluid name/amount, and — for phantom fluid slots — whether the slot "controls amount" (scroll-adjustable) or only accepts/clears a fluid type; for real (non-phantom) slots it also shows fill/drain hints, gated behind holding Shift (`Interactable.hasShiftDown()`). Mouse handling: `onMousePressed` syncs a click to the server only if the slot can fill or drain, and (for phantom slots) always allows the click regardless of held cursor item; `onMouseScroll` (phantom only) increments/decrements via `FluidSlotSyncHandler.SYNC_SCROLL`. `handleDragAndDrop` (recipe-viewer ghost ingredient support, phantom slots only) converts a dropped filled-container `ItemStack` into a `FluidStack` via `FluidContainerRegistry.getFluidForFilledItem(...)` and syncs it, then zeroes the dropped stack.

**Example** (README's headline pattern, and `TestTile.java:295-304` for both a normal and a phantom fluid slot):
```java
// normal (real) fluid tank
SlotGroupWidget.builder()
        .matrix("FFF")
        .key('F', i -> new FluidSlot().syncHandler(new FluidSlotSyncHandler(this.fluidStorage, i)))
        .build();

// phantom (ghost) fluid tank
SlotGroupWidget.builder()
        .matrix("FFF")
        .key('F', i -> new FluidSlot().syncHandler(new FluidSlotSyncHandler(this.phantomFluidStorage, i).phantom(true)))
        .build();
```

The simplest single-tank form referenced in the README (**constructed, not from repo** — `test/` only exercises the `FluidSlotSyncHandler(handler, index)` form above):
```java
new FluidSlot().syncHandler(new net.minecraftforge.fluids.FluidTank(16000))
```

### `com.cleanroommc.modularui.widgets.slot.ModularSlot`

The vanilla-facing `Slot` implementation backing `ItemSlot`/`PhantomItemSlot`. Extends Forge's `SlotItemHandler`, adapting `IItemHandler` access into vanilla `Slot` semantics, plus ModularUI-specific accessibility/filter/change-listener/grouping hooks.

```java
public class ModularSlot extends SlotItemHandler
```

Constructor: `ModularSlot(IItemHandler itemHandler, int index)` — throws `IllegalArgumentException` if `index` is out of `[0, itemHandler.getSlots())`. Screen position is irrelevant here (passed as `Integer.MIN_VALUE, Integer.MIN_VALUE` to the vanilla super-constructor) since ModularUI positions the *widget*, not the vanilla slot's x/y.

Fluent configuration (all return `ModularSlot`/`this` for chaining):

| Method | Params | Notes |
|---|---|---|
| `filter(Predicate<ItemStack>)` | predicate | Called on every GUI insert attempt; `null` resets to "accept anything" |
| `changeListener(IOnSlotChanged)` | listener | Called whenever the slot's item changes (not guaranteed to actually differ from before); `null` resets to `IOnSlotChanged.DEFAULT` (no-op) |
| `accessibility(boolean canPut, boolean canTake)` | both flags | Controls GUI-only put/take; does **not** affect pipes/hoppers-style external transfers |
| `canPut(boolean)` / `canTake(boolean)` | one flag | individual setters for the above |
| `canDragInto(boolean)` | flag | whether items dragged across the screen land in this slot; useful to disable when the filter depends on sibling slots' contents (drag preview updates are not "real" until drag completes) |
| `ignoreMaxStackSize(boolean)` | flag | `@ApiStatus.Experimental`. If true, only the item handler's own slot limit applies, not the item's vanilla max stack size |
| `slotGroup(String name)` | group id | associates with a **registered** `SlotGroup` by name (resolved later); see `slot`-package overview |
| `slotGroup(SlotGroup group)` | group instance | associates directly; removes from any previously-set group first |
| `singletonSlotGroup(int shiftClickPriority)` / `singletonSlotGroup()` | — | creates+assigns a one-off `SlotGroup.singleton(...)` purely so shift-clicks can target this lone slot; default priority is `SlotGroup.STORAGE_SLOT_PRIO` |

Getters: `isCanTake()`, `isCanPut()`, `isCanDragInto()`, `getFilter()`, `isPhantom()`, `isIgnoreMaxStackSize()`, `getSlotGroupName()`, `getSlotGroup()`, `getSyncHandler()` (throws `IllegalStateException` if not yet initialised by an `ItemSlotSH`).

Static helpers: `isPlayerSlot(Slot)` / `isPlayerSlot(SlotItemHandler)` and `getPlayerSlotPlayer(Slot)` / `getPlayerSlotPlayer(SlotItemHandler)` identify/extract the owning player for slots backed by one of the various player-inventory wrapper classes (`PlayerInvWrapper`, `PlayerMainInvWrapper`, `PlayerArmorInvWrapper`, including a GTNH-compat variant).

Gotchas: `isItemValid`/`canTakeStack`/`getItemStackLimit` all layer the `canPut`/`canTake`/`ignoreMaxStackSize` flags on top of Forge's `SlotItemHandler` defaults — overriding just one of these in a subclass without calling `super` will silently drop the other checks. `onSlotChanged()` itself is a deliberate no-op (real notification flows through `onSlotChangedReal(...)`, called by the sync layer) — don't expect overriding `onSlotChanged()` to see anything.

**Example** (`TestTile.java:365`, associating an existing `SlotGroup` instance directly):
```java
new ItemSlot().slot(new ModularSlot(smallStorage, i).slotGroup(slotGroup))
```

**Example** (`TestItem.java:52-54`, disabling accessibility for the slot holding the item that opened this GUI):
```java
guiSyncManager.bindPlayerInventory(guiData.getPlayer(), (inv, index) -> index == guiData.getSlotIndex() ?
        new ModularSlot(inv, index).accessibility(false, false) :
        new ModularSlot(inv, index));
```

### `com.cleanroommc.modularui.widgets.slot.ModularCraftingSlot`

`ModularSlot` specialization for a 3×3-style crafting **output** slot — "basically a copy of `net.minecraft.inventory.SlotCrafting`" per the source's own doc comment, so vanilla achievement/statistics triggers and container-item ("returns a bucket", etc.) handling keep working.

```java
public class ModularCraftingSlot extends ModularSlot
```

Constructor: `ModularCraftingSlot(IItemHandler itemHandler, int index)` — same shape as `ModularSlot`'s constructor; the output slot's backing storage index in the handler.

| Method | Params | Returns | Purpose |
|---|---|---|---|
| `isItemValid(ItemStack)` | — | `boolean` | always `false` — nothing can be manually placed into a crafting output slot |
| `updateResult(ItemStack stack)` | the newly-computed craft result | `void` | called by the container (e.g. `CraftingModularContainer.onCraftMatrixChanged`) after a recipe match; calls `putStack(stack)` then forces a sync (`getSyncHandler().forceSyncItem()`) |
| `setCraftMatrix(InventoryCraftingWrapper craftMatrix)` | the 3×3 input grid wrapper | `void` | must be called (by the container) before `onPickupFromSlot` runs, since pickup consumes ingredients from this matrix |
| `onPickupFromSlot(EntityPlayer, ItemStack)` | — | `void` | fires the FML crafting event, applies container-item replacement logic (drops broken tools, damage-overflow handling, etc.), decrements the crafting matrix, and re-notifies the container |
| `onCraftShiftClick(EntityPlayer, ItemStack)` | — | `void` | drops the stack into the world if a shift-click transfer can't place it in the player inventory |
| `decrStackSize(int amount)` | — | `ItemStack` | tracks `amountCrafted` (for the `onCrafting`/achievement hooks) before delegating to the vanilla decrement |

Not constructed directly with `new` outside a `ModularContainer`-aware setup — always paired with `InventoryCraftingWrapper` and a container (`CraftingModularContainer`) that calls `setCraftMatrix(...)` and forwards `onCraftMatrixChanged`.

**Example** (`TestTile.java:194`, wiring a crafting output slot into a `SlotGroupWidget` matrix):
```java
.key('O', i -> new ItemSlot().slot(new ModularCraftingSlot(this.craftingInventory, 9)))
```

**Example** (`CraftingModularContainer.java`, the container half — recipe re-evaluation on grid change):
```java
public class CraftingModularContainer extends ModularContainer {
    private final InventoryCraftingWrapper inventoryCrafting;
    private ModularCraftingSlot craftingSlot;

    public CraftingModularContainer(int width, int height, IItemHandlerModifiable craftingInventory, int startIndex) {
        this.inventoryCrafting = new InventoryCraftingWrapper(this, width, height, craftingInventory, startIndex);
    }

    @Override
    public void registerSlot(String panelName, ModularSlot slot) {
        super.registerSlot(panelName, slot);
        if (slot instanceof ModularCraftingSlot craftingSlot1) {
            this.craftingSlot = craftingSlot1;
            craftingSlot1.setCraftMatrix(this.inventoryCrafting);
        }
    }

    @Override
    public void onCraftMatrixChanged(@NotNull IInventory inventoryIn) {
        if (!getGuiData().isClient()) {
            this.craftingSlot.updateResult(CraftingManager.getInstance().findMatchingRecipe(this.inventoryCrafting, getPlayer().worldObj));
        }
    }
}
```
Used from `TestTile.java:110`: `settings.customContainer(() -> new CraftingModularContainer(3, 3, this.craftingInventory));`.

### `com.cleanroommc.modularui.widgets.slot.InventoryCraftingWrapper`

An `InventoryCrafting` (vanilla's 3×3-grid `IInventory`) backed by an `IItemHandlerModifiable`, so vanilla's `CraftingManager.findMatchingRecipe(...)` can be run against a modular item-handler-backed grid. Also solves a real synchronization problem: interacting with a `ModularSlot` updates the item handler directly, which does **not** automatically notify the `Container` to re-check for a matching recipe — this class snapshots contents and detects the diff.

```java
public class InventoryCraftingWrapper extends InventoryCrafting
```

Constructor: `InventoryCraftingWrapper(Container eventHandlerIn, int width, int height, IItemHandlerModifiable delegate, int startIndex)` — throws `IllegalArgumentException` if `delegate` doesn't have enough slots (`startIndex + width*height + 1`, the `+1` reserved for the paired output slot) starting from `startIndex`. Takes an internal snapshot of the input slots' contents immediately.

| Method | Params | Returns | Purpose |
|---|---|---|---|
| `detectChanges()` | — | `void` | compares the live item handler against the last snapshot; if anything meaningfully changed (empty↔non-empty, or a differing, non-stackable item), updates the snapshot and calls `notifyContainer()` — call this every tick/`detectAndSendChanges()` from the owning container (see `CraftingModularContainer` above) |
| `notifyContainer()` | — | `void` | `getContainer().onCraftMatrixChanged(this)` |
| `getDelegate()` | — | `IItemHandler` | the wrapped handler |
| `getStartIndex()` | — | `int` | offset into the delegate where this grid begins |
| `isEmpty()` | — | `boolean` | true if every slot (including the reserved output slot) is empty |
| `setSlot(int index, ItemStack stack, boolean notifyContainer)` | — | `void` | writes directly into the delegate at `startIndex + index`, optionally suppressing the change notification |
| `decrStackSize(int index, int count, boolean notifyContainer)` / `removeStackFromSlot(int index, boolean notifyContainer)` | — | `ItemStack` | notify-suppressible variants of the vanilla `IInventory` methods |
| `clear()` | — | `void` | empties every slot without notifying |

Gotchas: `getSizeInventory()` returns `width*height + 1` (the `+1` output slot), but `getStackInSlot`/`decrStackSize`/`removeStackFromSlot` bounds-check against `this.size`, not against the delegate — index math is always relative to `startIndex`. Not meant to be constructed standalone; always paired with a container implementing `onCraftMatrixChanged` (see `CraftingModularContainer` above).

### `com.cleanroommc.modularui.widgets.slot.IOnSlotChanged`

```java
public interface IOnSlotChanged {
    IOnSlotChanged DEFAULT = (newItem, onlyAmountChanged, client, init) -> {};
    void onChange(ItemStack newItem, boolean onlyAmountChanged, boolean client, boolean init);
}
```

Functional listener passed to `ModularSlot.changeListener(...)`. Params: `newItem` (current stack after the change), `onlyAmountChanged` (true if item identity is unchanged and only the count differs), `client`/`init` (whether this call is happening client-side, and whether it's the very first sync after GUI open rather than a real change — per the doc comment, `init` does not necessarily mean the slot actually changed). `DEFAULT` is the no-op used when no listener is set.

**Example** (`TestTile.java:312-318`, forwarding a change to a dynamic sync handler only for real client-side changes):
```java
new ItemSlot().slot(new ModularSlot(this.storageInventory0, 0)
        .changeListener((newItem, onlyAmountChanged, client, init) -> {
            if (client && !onlyAmountChanged) {
                dynamicSyncHandler.notifyUpdate(packet -> NetworkUtils.writeItemStack(packet, newItem));
            }
        }));
```

### `com.cleanroommc.modularui.widgets.slot.SlotGroup`

Non-widget bookkeeping object: a named group of vanilla `Slot`s that can be shift-clicked into (in a defined priority order relative to other groups) and, with Inventory BogoSorter installed, sorted as a unit. Must exist identically on server and client (registered through `PanelSyncManager`/`ModularSyncManager`, not constructed ad hoc per side).

```java
public class SlotGroup
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `new SlotGroup(String name, int rowSize)` | name, slots-per-row | — | delegates to the 3-arg ctor with `allowShiftTransfer = true` |
| `new SlotGroup(String name, int rowSize, boolean allowShiftTransfer)` | — | — | delegates to the 4-arg ctor with `shiftClickPriority = STORAGE_SLOT_PRIO` |
| `new SlotGroup(String name, int rowSize, int shiftClickPriority, boolean allowShiftTransfer)` | full config | — | the general-purpose constructor; assumes a rectangular `rowSize`-wide layout |
| `static singleton(String name, int shiftClickPriority)` | — | `SlotGroup` | a group capped at exactly one slot — purely so a lone slot outside any "real" group can still be a shift-click target; throws `IllegalStateException` if a second slot is ever added |
| `getShiftClickPriority()` | — | `int` | lower value groups are (per convention) filled first on shift-click; constants `PLAYER_INVENTORY_PRIO = 0` and `STORAGE_SLOT_PRIO = 100` establish the usual ordering (player inventory is the lowest-priority target, i.e. filled last from a machine slot shift-click) |
| `getSlots()` | — | `List<Slot>` (unmodifiable) | all member slots |
| `getFirstSlotForSorting()` | — | `Slot` | first slot, used by BogoSorter's sort buttons; `PlayerSlotGroup` overrides this (see below) |
| `getRowSize()` / `getName()` | — | `int` / `String` | as configured |
| `allowShiftTransfer()` | — | `boolean` | whether shift-clicked items may land in this group at all |
| `isAllowSorting()` | — | `boolean` | true only if there's more than one slot **and** `setAllowSorting(true)` (the default) hasn't been overridden off |
| `setAllowSorting(boolean)` | flag | `SlotGroup` | disables/enables the sort-button affordance for this group |
| `isSingleton()` | — | `boolean` | true only for groups created via `singleton(...)` |

Package-private `addSlot`/`removeSlot` are called from `ModularSlot.slotGroup(...)`, not meant to be called directly.

**Example** (`TestTile.java:113, 338, 341`, registering a group by name via the sync manager vs. constructing+registering one manually for a sub-panel):
```java
// simple: registered by name/row-size, referenced later via ModularSlot.slotGroup("item_inv")
syncManager.registerSlotGroup("item_inv", 3);

// manual instance, used when you need the SlotGroup object itself (e.g. to pass into ModularSlot.slotGroup(SlotGroup))
SlotGroup slotGroup = new SlotGroup("small_inv", 2);
syncManager.registerSlotGroup(slotGroup);
// ...
new ItemSlot().slot(new ModularSlot(smallStorage, i).slotGroup(slotGroup))
```

### `com.cleanroommc.modularui.widgets.slot.PlayerSlotGroup`

`SlotGroup` specialization representing the player's hotbar + main inventory as a **single** shift-click/sort group (unlike some other mods, e.g. Bogosorter itself, which treat hotbar and main inventory as two groups).

```java
public class PlayerSlotGroup extends SlotGroup
```

Constructor: `PlayerSlotGroup(String name)` — fixed `rowSize = 9`, `shiftClickPriority = PLAYER_INVENTORY_PRIO`, `allowShiftTransfer = true`. `public static final String NAME = "player_inventory"` is the conventional group name.

`getFirstSlotForSorting()` is overridden to specifically return the first **main-inventory** slot (index in `[9, 36)`), not the first hotbar slot — cached after the first lookup — because BogoSorter's sort buttons are placed against the main inventory, not the hotbar, even though both share this one group.

**Inferred:** not constructed directly anywhere in `test/`; `ModularSyncManager.construct(...)` auto-registers one (`if (this.mainPSM.getSlotGroup(PlayerSlotGroup.NAME) == null) { this.mainPSM.bindPlayerInventory(getPlayer()); }`) whenever a panel binds the player inventory and no such group exists yet, so most consumers get a `PlayerSlotGroup` for free via `PanelSyncManager.bindPlayerInventory(...)` (see `TestItem.java:52`, `ItemEditorGui.java:66` `panel.bindPlayerInventory()`) rather than by name via `SlotGroupWidget.playerInventory(false)` (`TestTile.java:328`, `TestItem.java:70`) which builds the visible slot widgets for it.

### `com.cleanroommc.modularui.widgets.slot.PlayerSlotType`

```java
public enum PlayerSlotType {
    HOTBAR, MAIN_INVENTORY, ARMOR;
    static PlayerSlotType getPlayerSlotType(Slot slot);
}
```

Classifies a vanilla `Slot` as belonging to the player's hotbar (index `< 9`), main inventory (`9`–`35`), or armor (`36`–`39`), by checking which player-inventory item-handler wrapper class backs it (`PlayerMainInvWrapper`, `PlayerArmorInvWrapper`, `PlayerInvWrapper`, plus a GTNH-compat `SlotItemHandler`/`PlayerMainInvWrapper` pair, or a raw `InventoryPlayer`-backed slot). Returns `null` for anything else (index out of range, or not player-inventory-backed at all). Used internally by `ItemSlot.getWidgetThemeInternal(ITheme)` to theme hotbar/main-inv/armor slots distinctly — not something consumers typically call directly.

---

## textfield

Package: `com.cleanroommc.modularui.widgets.textfield`.

### Overview

`TextFieldWidget` is the single-line, syncable text/number input consumers actually build UIs with. `TextEditorWidget` is a multiline, client-only (non-synced) variant for editing large text blobs. Both extend `BaseTextFieldWidget`, which owns mouse/keyboard input handling and drawing; `BaseTextFieldWidget` in turn delegates text-buffer manipulation (insert/delete/cursor/selection) to `TextFieldHandler`, and measurement/drawing (including the 1.7.10-only integer-grouping formatting hack) to `TextFieldRenderer`. `INumberParser` is the pluggable math-expression parser interface consumed by `TextFieldWidget.numberParser(...)`.

### `com.cleanroommc.modularui.widgets.textfield.TextFieldWidget`

The main public text-input widget: single line, syncs an `IStringValue<?>`, and offers built-in numeric-input modes (double/long/int, with optional scroll-wheel increment/decrement and math-expression parsing).

```java
public class TextFieldWidget extends BaseTextFieldWidget<TextFieldWidget>
```

Value binding & validation:

| Method | Params | Returns | Purpose / gotchas |
|---|---|---|---|
| `value(IStringValue<?> stringValue)` | sync/plain string value | `TextFieldWidget` | required for the field to have a backing value; if never called, `onInit()` creates a throwaway `StringValue("")` so the widget still works standalone |
| `setValidator(Function<String, String> validator)` | text → sanitized text | `TextFieldWidget` | applied when focus is lost (and, if `autoUpdateOnChange(true)`, on every keystroke too); default is identity |
| `autoUpdateOnChange(boolean)` | flag | `TextFieldWidget` | if true, the bound value updates on every text change instead of only on unfocus — useful for live search fields (see `TestGuis.java:454-458`) |
| `acceptsExpressions(boolean)` | flag, default `true` | `TextFieldWidget` | whether `parse(String)` evaluates a math expression (via `MathUtils.PARSER_WITH_SI` or a custom `numberParser(...)`) vs. plain `NumberFormat.AMOUNT_TEXT` parsing |
| `numberParser(INumberParser parser)` | custom parser | `TextFieldWidget` | overrides the default expression parser used by `parse(String)` |
| `setMaxLength(int)` | char cap | `TextFieldWidget` | forwards to `TextFieldHandler.setMaxCharacters(int)` |
| `setPattern(Pattern)` | regex | `TextFieldWidget` | forwards to `TextFieldHandler.setPattern(Pattern)`; every inserted character/paste is validated against it |
| `defaultNumber(double)` | fallback | `TextFieldWidget` | value substituted when the field is empty or fails to parse |
| `setTooltipOverride(boolean)` | flag | `TextFieldWidget` | by default, the tooltip shows the field's *contents* only when the horizontal scrollbar is active (i.e. text overflows); setting this lets you use the normal `ITooltip` methods (`tooltip(...)`, `addTooltipLine(...)`, etc.) instead — every one of those methods is overridden here to auto-set this flag when called |

Numeric-mode builders (all set `this.numbers = true` and install a validator via the private `numbersDouble(NumberValidator)` core):

| Method | Params | Notes |
|---|---|---|
| `numbersDouble()` | — | accepts any double, identity validator |
| `numbersDouble(double min, double max)` | inclusive bounds | clamps |
| `numbersDouble(DoubleSupplier min, DoubleSupplier max)` | dynamic bounds | re-evaluated each validation, so bounds can change at runtime |
| `numbersDouble(DAM.UnaryDoubleOperator validator)` | custom transform | applied before clamping |
| `numbersDouble(NumberValidator validator)` | `(input, value) -> double` | lowest-level double overload — `input` is the raw pre-parse string, useful for validators that need to inspect it (e.g. percent suffix handling) |
| `numbersLong()` / `numbersLong(long min, long max)` / `numbersLong(LongSupplier, LongSupplier)` / `numbersLong(UnaryLongOperator)` | whole-number variants | also install `MathUtils.PARSER_WHOLE_NUMBER` as the number parser and default scroll-step values; the `(validator, min, max)` overload treats an explicit `max` bound specially — percentages/fractional input get multiplied by `max` (`MathUtils.percentOrSelf`) before validating |
| `numbersInt()` / `numbersInt(int min, int max)` / `numbersInt(LongSupplier, LongSupplier)` / `numbersInt(UnaryIntOperator)` | int-range variants | thin wrappers around the `numbersLong` family with `MathUtils.castToIntSaturated(...)` |
| `formatAsInteger(boolean)` | flag | forwards to `TextFieldRenderer.setFormatAsInteger(boolean)` — enables 1.7.10-only thousands-grouping display (e.g. `1,000,000`) without affecting the underlying stored value; automatically enabled by the `numbersLong(validator, min, max)` overload |
| `getText()` / `parse(String)` | — | `getText()` returns the single line of text (throws `IllegalStateException` if somehow multi-line); `parse(String)` runs the configured expression parser (or plain number parse if `acceptsExpressions(false)`), falling back to `defaultNumber` and recording `getMathFailMessage()` on failure |

Older, `@Deprecated` numeric setters (`setNumbers(...)`, `setNumbersLong(...)`, `setNumbersDouble(...)`, `setDefaultNumber(double)`, `setFormatAsInteger(boolean)`) remain for backward compatibility — prefer the `numbersInt`/`numbersLong`/`numbersDouble`/`defaultNumber`/`formatAsInteger` families above for new code.

Scroll-wheel increment (only active while `usingScrollStep()` is on, the field `isNumbers()`, and it's focused — otherwise scroll falls back to `BaseTextFieldWidget`'s normal scrollbar behavior):

| Method | Params | Notes |
|---|---|---|
| `scrollValues(double baseStep, double shiftStep, double ctrlStep, double altStep)` | per-modifier increments | Also sets `usingScrollStep = true`. Modifiers multiply together if held simultaneously. Defaults (if never called): `1, 100, 0.1, 10000`; the whole-number (`numbersLong`/`numbersInt`) family instead defaults to `1, 100, 10_000, 1_000_000` via the private `defaultWholeNumberScrollValues()` |
| `usingScrollStep()` / `usingScrollStep(boolean)` | — | toggles the flag without changing the step values |

`onUpdate()` re-syncs displayed text from the bound value whenever unfocused and the two differ (so external/server-driven changes show up live). `onRemoveFocus` re-applies the validator to the committed text and pushes it to the bound value; `onMouseScroll` implements the scroll-step increment logic described above; `onTextChanged` pushes to the bound value immediately when `autoUpdateOnChange` is set.

**Example** (`ItemEditorGui.java:78-98`, int-bounded numeric fields synced to NBT-backed values):
```java
new TextFieldWidget()
        .size(50, 16)
        .value(new IntSyncValue(() -> {
            ItemStack stack = getStack();
            return stack != null ? stack.getItemDamage() : 0;
        }, val -> { if (!syncManager.isClient()) getStack().setItemDamage(val); }))
        .numbersInt(0, Short.MAX_VALUE - 1);
```

**Example** (`TestGuis.java:740-762`, `buildTextFieldUI` — plain, decimal, and whole-number fields side by side):
```java
Flow.col()
        .coverChildrenHeight()
        .childPadding(2)
        .crossAxisAlignment(Alignment.CrossAxis.START)
        .fullWidth()
        .child(IKey.str("Any").asWidget())
        .child(new TextFieldWidget().fullWidth())
        .child(IKey.str("Decimal numbers").asWidget())
        .child(new TextFieldWidget()
                .fullWidth()
                .numbersDouble(-1000, 1000)
                .usingScrollStep())
        .child(IKey.str("Whole numbers").asWidget())
        .child(new TextFieldWidget()
                .fullWidth()
                .numbersLong(-100_000_000_000_000L, 100_000_000_000_000L)
                .usingScrollStep());
```

**Example** (`TestGuis.java:449-458`, live search field via `autoUpdateOnChange`):
```java
StringValue searchValue = new StringValue("");
new TextFieldWidget()
        .value(searchValue)
        .height(16).widthRel(1f)
        .autoUpdateOnChange(true);
```

### `com.cleanroommc.modularui.widgets.textfield.TextEditorWidget`

A non-synced, multiline text editor for client-only screens (e.g. editing large config-like blobs locally).

```java
// TODO steal from Mclib
public class TextEditorWidget extends BaseTextFieldWidget<TextEditorWidget>
```

Its entire body is the constructor: `this.handler.setMaxLines(10000)`. Everything else (insert/delete/cursor movement, rendering, focus handling, scrolling) is inherited unchanged from `BaseTextFieldWidget`; there is no synced value plumbing (`TextFieldWidget`'s `IStringValue` binding is specific to that subclass). Not exercised in `test/` — the source itself flags it as unfinished (`// TODO steal from Mclib`).

**Example (constructed, not from repo):**
```java
new TextEditorWidget()
        .sizeRel(1f)
        .setFocusOnGuiOpen(true);
```

### `com.cleanroommc.modularui.widgets.textfield.BaseTextFieldWidget<W>`

Shared base for `TextFieldWidget`/`TextEditorWidget`: owns mouse/keyboard event handling, focus/cursor-blink state, and drawing, delegating actual text-buffer mutation to a `TextFieldHandler` and measurement/drawing to a `TextFieldRenderer`.

```java
public class BaseTextFieldWidget<W extends BaseTextFieldWidget<W>> extends AbstractScrollWidget<VoidWidget, W> implements IFocusedWidget, ModernInteractable
```

Public static constants: `NATURAL_NUMS`, `WHOLE_NUMS`, `DECIMALS`, `LETTERS`, `ANY` — ready-made `Pattern`s for `setPattern(Pattern)`, covering "digits + operators", "signed digits + operators", "digits with locale-aware decimal separator + operators", "letters only", and "anything", respectively. `getDecimalSeparator()` / `getGroupSeparator()` expose the locale's `DecimalFormat` separators used to build `DECIMALS`.

Common configuration setters (all fluent, return `W`):

| Method | Params | Notes |
|---|---|---|
| `setTextAlignment(Alignment)` | — | default `Alignment.CenterLeft` |
| `setScale(float)` | — | text render scale, default `1f` |
| `setTextColor(int)` / `setMarkedColor(int)` | ARGB int | overrides theme-provided text/selection colors; `null` (unset) falls back to the active `TextFieldTheme` |
| `setFocusOnGuiOpen(boolean)` | flag | if true, `afterInit()` focuses this field and selects all text as soon as the containing screen opens |
| `hintText(String)` / `hintColor(int)` | — | placeholder text shown (in a dimmed/theme color unless `hintColor` overrides it) whenever the field is empty |

Getters mirror all of the above (`getTextAlignment()`, `getScale()`, `isFocusOnGuiOpen()`, `getTextColor()`, `getMarkedColor()`, `getHintText()`, `getHintTextColor()`, `getScrollOffset()`, `getMaxLines()`, `getScrollData()`, `getLastText()`, `canScrollHorizontally()`).

Behavior notes from source (not exhaustively re-documented per-key): arrow keys move/extend the cursor selection (Ctrl = by word, Shift = extend selection); Enter inserts a newline only if `getMaxLines() > 1`, otherwise it removes focus; Escape restores the text as of focus-gain if `ModularUIConfig.escRestoreLastText` is set, then always removes focus; Ctrl+A/C/V/X are wired to select-all/copy/paste/cut through `TextFieldHandler`; double-/triple-click (within `DOUBLE_CLICK_THRESHOLD` = 300 ms) mark the current line / entire text. Right-click clears the field entirely. `onTextChanged()` is a protected no-op hook subclasses override (`TextFieldWidget` uses it to push `autoUpdateOnChange`).

Not constructed directly (it's abstract-in-practice via the generic self-type, though not declared `abstract`) — always used through `TextFieldWidget` or `TextEditorWidget`.

### `com.cleanroommc.modularui.widgets.textfield.TextFieldHandler`

Internal text-buffer engine: owns the actual `List<String>` line buffer, both cursor points (`cursor`/`cursorEnd`, for selection), and every insert/delete/cursor-movement operation. Not a widget.

```java
public class TextFieldHandler
```

Selected public surface relevant if you're extending `BaseTextFieldWidget` yourself (most consumers never touch this directly — it's reached via `this.handler` inside subclasses):

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getText()` | — | `List<String>` | the live line buffer (mutable reference — `TextFieldWidget.getText()`/`setText(String)` manipulate it directly for the single-line case) |
| `getTextAsString()` | — | `String` | all lines newline-joined |
| `getSelectedText()` | — | `String` | current selection, or `""` if none |
| `hasTextMarked()` | — | `boolean` | whether there's an active (non-collapsed) selection |
| `insert(String text, boolean hasHorizontalScrolling)` / `insert(List<String>, boolean)` | text (split on `\n`), whether horizontal scroll makes width irrelevant | `void` | inserts at the cursor, replacing any selection first; silently rejects the insert (no-op) if it would exceed `maxLines`, fail the configured `test(String)` pattern/length check, or (when horizontal scrolling is disabled) overflow the renderer's available width |
| `delete(boolean inFront, boolean ctrl, boolean shift)` / `delete(boolean ctrl, boolean shift)` / `deleteMarked()` | — | `void` | deletes selection, or one char/word/line depending on flags, in the given direction |
| `newLine()` | — | `void` | splits the current line at the cursor into two (only meaningful when `maxLines > 1`) |
| `clear()` | — | `void` | selects everything then deletes it |
| `markAll()` / `markCurrentLine()` | — | `void` | selection helpers used by double/triple-click |
| `moveCursorLeft/Right/Up/Down/Start/End(boolean ctrl, boolean shift)` | modifiers | `void` | cursor navigation; `ctrl` = by word/to document start-end, `shift` = extend selection instead of moving the anchor |
| `setMaxLines(int)` / `getMaxLines()` | — | — / `int` | clamped to a minimum of 1; `TextFieldWidget` never changes this from the default `1`, `TextEditorWidget` sets it to `10000` |
| `test(String text)` | candidate text | `boolean` | validates against the optional `Pattern` (`setPattern`) and/or `maxCharacters` (`setMaxCharacters`); always `true` when `maxLines > 1` (multiline fields skip pattern/length gating entirely) |

Not meant to be constructed or held onto independently — every `BaseTextFieldWidget` owns exactly one (`protected TextFieldHandler handler = new TextFieldHandler(this)`), created in the field declaration.

### `com.cleanroommc.modularui.widgets.textfield.TextFieldRenderer`

Internal text measurement/drawing engine for text fields — extends the general-purpose `TextRenderer` with cursor/selection-highlight drawing and a 1.7.10-specific integer thousands-grouping display mode.

```java
public class TextFieldRenderer extends TextRenderer
```

Only method worth calling from outside the `textfield` package: `setFormatAsInteger(boolean)` — enables/disables the grouping-separator display hack (exposed to consumers via `TextFieldWidget.formatAsInteger(boolean)`, which should be preferred over touching the renderer directly). Everything else (`setCursor`, `toggleCursor`, `setMarkedColor`, `setCursorColor`, `getCursorPos`, `wrapLine`, `getPosOf`, `drawMeasuredLines`, `drawMarked`, `drawCursor`) is either driven internally by `BaseTextFieldWidget`/`TextFieldHandler` or is a low-level rendering primitive not intended for direct external use. **Inferred:** the grouping/formatting logic is explicitly commented as a stopgap ("1.7.10 only until proper number groupings support") rather than a long-term design.

### `com.cleanroommc.modularui.widgets.textfield.INumberParser`

```java
public interface INumberParser {
    ParseResult parse(String expr, double defaultValue);
}
```

Pluggable math-expression parser consumed by `TextFieldWidget.numberParser(INumberParser)`. `parse(expr, defaultValue)` should return a `ParseResult` (see `com.cleanroommc.modularui.utils.ParseResult`, out of scope for this document) indicating success with a numeric value, or failure with an error message. `TextFieldWidget` uses `MathUtils.PARSER_WITH_SI` by default (or `MathUtils.PARSER_WHOLE_NUMBER` when a `numbersLong`/`numbersInt` mode installs it) unless a custom parser is set via `numberParser(...)`.

**Example (constructed, not from repo)** — no `test/` file supplies a custom `INumberParser`; `TestGuis`/`ItemEditorGui` only use the built-in `numbersDouble`/`numbersLong`/`numbersInt` helpers, which install `MathUtils`'s parsers automatically:
```java
INumberParser hexParser = (expr, defaultValue) -> {
    try {
        return ParseResult.success(DAM.number(Long.parseLong(expr, 16)));
    } catch (NumberFormatException e) {
        return ParseResult.failure("Not a valid hex number", defaultValue);
    }
};
new TextFieldWidget().numberParser(hexParser);
```
