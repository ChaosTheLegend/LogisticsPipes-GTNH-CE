# `widgets` package reference (core widgets)

Package: `com.cleanroommc.modularui.widgets`

The concrete, ready-to-use widgets a mod author drops into a `ModularPanel`: buttons, dialogs, sliders, lists, text, progress bars, item/fluid/entity display, tab/page controls. This document covers only the classes directly in `widgets/` — not the `layout`, `menu`, `slot`, or `textfield` subpackages, which are documented elsewhere.

Examples are adapted from `com.cleanroommc.modularui.test` (`TestGui.java`, `TestGuis.java`, `TestTile.java`, `GLTestGui.java`) wherever real usage exists; file/line references are given. Where no test/ usage exists, examples are labeled "constructed, not from repo".

---

## `com.cleanroommc.modularui.widgets.ButtonWidget<W>`

```java
public class ButtonWidget<W extends ButtonWidget<W>> extends SingleChildWidget<W> implements Interactable
```

The basic clickable widget. Holds one optional child (background/overlay driven by the widget theme, same as any `SingleChildWidget`), fires user-supplied callbacks for mouse/keyboard events, and can be bound to an `InteractionSyncHandler` for server-synced buttons. Plays a click sound by default on any successful interaction.

### Static factory
- `static ButtonWidget<?> panelCloseButton()` — pre-configured button: `IThemeApi.CLOSE_BUTTON` theme, positioned `top(4).right(4)`, `GuiTextures.CROSS_TINY` overlay, closes the containing panel on left/right mouse press (`getPanel().closeIfOpen()`). Used directly in `TestGui.java:99` and `ColorPickerDialog` pattern (dialogs typically add this as their close "X").

### Constructors
- `ButtonWidget()` — no-arg; generic self-type `W` inferred at use site (typical pattern: `new ButtonWidget<>()`).

### Event-handler setters (fluent, return `W`)
| Method | Params | Notes |
|---|---|---|
| `onMousePressed(IGuiAction.MousePressed)` | press callback returning boolean | Called from `onMousePressed(int)`; returning `true` plays click sound and reports `Result.SUCCESS`. Falls back to `syncHandler.onMousePressed` if callback absent/returns false. |
| `onMouseReleased(IGuiAction.MouseReleased)` | release callback | — |
| `onMouseTapped(IGuiAction.MousePressed)` | tap callback (press+release without drag) | Result defaults to `IGNORE` if neither callback nor sync handler consumes it (unlike `onMousePressed` which defaults `ACCEPT`). |
| `onMouseScrolled(IGuiAction.MouseScroll)` | scroll callback | — |
| `onKeyPressed(IGuiAction.KeyPressed)` | key press callback | — |
| `onKeyReleased(IGuiAction.KeyReleased)` | key release callback | — |
| `onKeyTapped(IGuiAction.KeyPressed)` | key tap callback | — |
| `syncHandler(InteractionSyncHandler)` | server sync handler | Wraps via `ISyncOrValue.orEmpty(...)`; only `InteractionSyncHandler` (or empty) is accepted — `isValidSyncOrValue` enforces this. |
| `playClickSound(boolean)` | enable/disable click sound | Default `true`. |
| `clickSound(Runnable)` | custom sound callback | Overrides the default `Interactable.playButtonClickSound()` when set. |

### Other methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `playClickSound()` | - | - | Manually invoke the configured click sound logic (runs `clickSound` if set, else default button click). |
| `isPlayClickSound()` / `getClickSound()` | - | boolean / `Runnable` | Getters. |
| `getSyncHandler()` | - | `@NotNull InteractionSyncHandler` | **Gotcha:** returns the field directly without a null-check despite `@NotNull` — will NPE downstream if no sync handler was ever set and this is called. |

**Gotcha:** `AvailableElement` in `TestGui.java:115-129` shows the standard subclassing pattern — extend `ButtonWidget<Self>`, override `backgroundOverlay(IDrawable...)` to forbid it (`throw new UnsupportedOperationException("Use overlay()")`) and override `getBackground()` for state-dependent backgrounds instead of the state-array machinery `AbstractCycleButtonWidget` uses.

### Example (from `TestGui.java:64-67, 105-111, 99`)
```java
import com.cleanroommc.modularui.widgets.ButtonWidget;

// remove-from-list button inside a SortableListWidget.Item
new ButtonWidget<>()
        .onMousePressed(button -> item.removeSelfFromList())
        .overlay(GuiTextures.CROSS_TINY.asIcon().size(10))
        .width(10).heightRel(1f);

// button that opens a sub-panel
panel.child(new ButtonWidget<>()
        .bottom(7).size(12, 12).leftRel(0.5f)
        .overlay(GuiTextures.ADD)
        .onMouseTapped(mouseButton -> {
            otherPanel.openPanel();
            return true;
        }));

// standard dialog close button
panel1.child(ButtonWidget.panelCloseButton());
```

---

## `com.cleanroommc.modularui.widgets.Dialog<T>`

```java
public class Dialog<T> extends ModularPanel
```

A `ModularPanel` preconfigured as a modal-ish popup: not draggable, disables panels below it, and does not close on out-of-bounds click by default (all overridable). Carries an optional `Consumer<T>` result callback so a caller can get a typed result back when the dialog closes itself.

### Constructors
- `Dialog(String name)` — no result consumer (`this(name, null)`).
- `Dialog(String name, Consumer<T> resultConsumer)` — `name` is the standard `ModularPanel` name/id; `resultConsumer` receives the value passed to `closeWith`.

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `closeWith(T result)` | result value | - | Calls `resultConsumer.accept(result)` (if non-null) then `closeIfOpen()`. This is the intended way to close a dialog *with* a result, as opposed to a plain cancel. |
| `isDraggable()` | - | boolean | Overrides `ModularPanel`; returns internal `draggable` field (default `false`). |
| `disablePanelsBelow()` | - | boolean | Default `true` — panels beneath this one are disabled while the dialog is open. |
| `closeOnOutOfBoundsClick()` | - | boolean | Default `false`. |
| `setDraggable(boolean)` | - | `Dialog<T>` | Fluent setter. |
| `setDisablePanelsBelow(boolean)` | - | `Dialog<T>` | Fluent setter. |
| `setCloseOnOutOfBoundsClick(boolean)` | - | `Dialog<T>` | Fluent setter. |

**Gotcha:** `Dialog` itself does not open its panel — construction just builds the `ModularPanel`; a caller must actually open it, typically via `IPanelHandler.simple(mainPanel, (panel, player) -> new Dialog<>(...)...., true)` and then `handler.openPanel()`.

### Example (from `TestGui.java:97-104`)
```java
import com.cleanroommc.modularui.widgets.Dialog;

IPanelHandler otherPanel = IPanelHandler.simple(panel, (mainPanel, player) -> {
    ModularPanel panel1 = new Dialog<>("Option Selection")
            .setDisablePanelsBelow(false)
            .setDraggable(false)
            .size(150, 120);
    return panel1.child(ButtonWidget.panelCloseButton())
            .child(new Grid()
                    .grid(availableMatrix)
                    .scrollable()
                    .pos(7, 7).right(16).bottom(7).name("available list"));
}, true);
```

`ColorPickerDialog` (below) is the other in-repo subclass, showing the `Consumer<T>`/`closeWith` pattern in full: its Confirm button calls `closeWith(this.color)`, Cancel calls plain `closeIfOpen()`.

---

## `com.cleanroommc.modularui.widgets.SortableListWidget<T>`

```java
public class SortableListWidget<T> extends ListValueWidget<T, SortableListWidget.Item<T>, SortableListWidget<T>>
```

A vertical list of `Item<T>` rows that the player can drag to reorder, with add/remove support and animated re-layout on change. Wraps a `T` value per row via `Item::getWidgetValue`.

### Constructor
- `SortableListWidget()` — sets `heightRel(1f)` and disables `collapseDisabledChild` (animations handle visual removal instead of instant collapse).

### Lifecycle overrides
| Method | Notes |
|---|---|
| `onInit()` | Assigns sequential indexes to children (`assignIndexes()`). |
| `onUpdate()` | Increments an internal move-cooldown counter (`timeSinceLastMove`). |
| `beforeResize(boolean)` / `postResize()` | Snapshot each item's `Area` before resize, then animate (150ms) any item whose area actually changed after resize — this is what produces the smooth reorder/remove slide animation. |
| `getDefaultWidth()` | Returns `80` (fallback default). |

### Behavioral methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `moveTo(int from, int to)` | source/target index | - | Moves a child in the underlying children list. **Gotcha:** no-ops if `timeSinceLastMove < 3` (throttles moves to avoid thrashing during a drag) or if `from == to` / either index negative (logs an error in the latter case). Triggers `onChange` callback with the new value list. |
| `remove(int index)` (override) | index | boolean | Removes and disposes the `Item` at index, reassigns indexes, schedules the remove animation, then fires `onChange` and `onRemove` callbacks. Returns `false` if index had no widget. |
| `onChildAdd(Item<T>)` (override) | new child | - | Reassigns indexes, schedules animation, fires `onChange`, and schedules a resize. |
| `onChange(Consumer<List<T>>)` | callback | `SortableListWidget<T>` | Called whenever the ordered value list changes (add/remove/reorder). |
| `onRemove(Consumer<Item<T>>)` | callback | `SortableListWidget<T>` | Called with the removed `Item` specifically (in addition to `onChange`). |

Inherited from `ListValueWidget`/`ListWidget`: `children(Iterable<V>, Function<V,I>)`, `child(IWidget)`, `getValues()` (returns `List<T>` via the constructor's value-extractor), plain `remove`, `collapseDisabledChild`, scroll config, etc.

### Nested class: `SortableListWidget.Item<T>`

```java
public static class Item<T> extends DraggableWidget<Item<T>> implements IValueWidget<T>
```

A single draggable row. Holds a fixed `T value`, an optional single child (set via `child(IWidget)` or `child(Function<Item<T>, IWidget>)`), and drag-to-reorder logic.

| Member | Notes |
|---|---|
| `Item(T value)` | Constructor; sets `resizer().widthRel(1f).height(18)` and `background(GuiTextures.BUTTON_CLEAN)` as sane list-row defaults. |
| `getWidgetValue()` | Returns the wrapped `T` (implements `IValueWidget<T>`). |
| `getIndex()` | Current position in the list (assigned by the parent `SortableListWidget`; `-1` before first layout). |
| `removeSelfFromList()` | Convenience: calls `this.listWidget.remove(this.index)`; returns `true` (fits `IGuiAction.MousePressed` signature directly). |
| `child(IWidget widget)` | Sets the single child (replacing any previous), initialising it immediately if the item is already valid. |
| `child(Function<Item<T>, IWidget> widgetCreator)` | Same, but the creator receives `this` — lets a row's child reference the row itself (e.g. to wire a remove button). |
| `dropPredicate(Predicate<IWidget> dropPredicate)` | Restricts what other widgets can be dropped onto this item (`canDropHere` override); `null` (default) accepts anything. |
| `onDrag(int mouseButton, long timeSinceLastClick)` (override) | Finds any other `Item` of the *same* `SortableListWidget` currently hovered and calls `listWidget.moveTo(this.index, item.index)` — this is the actual reorder trigger during a drag. |
| `onDragEnd(boolean successful)` (override) | No-op. |

**Gotcha:** a commented-out `removeable()` helper (adding a built-in remove `ButtonWidget`) exists in source but is dead code — you must build your own remove button and call `item.removeSelfFromList()` from it, as `TestGui.java` does.

### Example (from `TestGui.java:54-96`)
```java
import com.cleanroommc.modularui.widgets.SortableListWidget;

Map<String, SortableListWidget.Item<String>> items = new Object2ObjectOpenHashMap<>();
for (String line : lines) {
    items.put(line, new SortableListWidget.Item<>(line)
            .name("item_" + line)
            .child(item -> Flow.row().name("row_" + line)
                    .child(new Widget<>()
                            .addTooltipLine(line)
                            .widgetTheme(IThemeApi.BUTTON)
                            .overlay(IKey.str(line))
                            .expanded().heightRel(1f))
                    .child(new ButtonWidget<>()
                            .onMousePressed(button -> item.removeSelfFromList())
                            .overlay(GuiTextures.CROSS_TINY.asIcon().size(10))
                            .width(10).heightRel(1f))));
}

SortableListWidget<String> sortableListWidget = new SortableListWidget<String>()
        .children(configuredOptions, items::get)
        .name("sortable list");

panel.child(sortableListWidget
        .onRemove(stringItem -> availableElements.get(stringItem.getWidgetValue()).available = true)
        .pos(10, 10).bottom(23).width(100));
```

---

## `com.cleanroommc.modularui.widgets.AbstractCycleButtonWidget<W>`

```java
public class AbstractCycleButtonWidget<W extends AbstractCycleButtonWidget<W>> extends SingleChildWidget<W> implements Interactable
```

Base class for buttons that cycle through N states on click, each with its own background/hover-background/overlay/hover-overlay/tooltip/child. Not instantiated directly — `ToggleButton` (2 states) and `CycleButtonWidget` (N states, public API) subclass it. State is backed by an `IIntValue<?>`.

### Key mechanics
- **State count must generally be set before configuring per-state visuals.** `expectCount()` logs an error (does not throw) if a tooltip/visual method is called before a count is known. State count is auto-derived to the enum's constant count or `2` when the bound value is an `IEnumValue`/`IBoolValue` respectively (`setSyncOrValue`); otherwise it defaults to `1` and grows implicitly as you address higher-indexed states (`updateStateCount`), unless `stateCount(int)` was called explicitly (`explicitStateCount = true` locks it).
- `getState()` re-syncs from the bound `IIntValue` every draw/interaction and calls `setState` if it changed externally.
- Left-click (`mouseButton == 0`) calls `next()`; right-click (`1`) calls `prev()`; both wrap around (`% stateCount`). Any other mouse button is ignored (`Result.IGNORE`).

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `next()` / `prev()` | - | - | Advance/retreat state (wrapping), writes back to the bound value (`setSource=true`). |
| `setState(int state, boolean setSource)` | target state, whether to push to bound value | - | Throws `IndexOutOfBoundsException` if `state` outside `[0, stateCount)`. Updates child, marks tooltip dirty. |
| `playClickSound()` | - | - | Same pattern as `ButtonWidget`. |
| `stateChild(int state, IWidget child)` | state index, widget | `W` | Sets the child widget shown for a given state; grows `stateChildren` array as needed via `updateStateCount(state, false)`. |
| `stateBackground/stateOverlay/stateHoverBackground/stateHoverOverlay(UITexture texture)` (protected) | one vertically-stacked texture sliced into `stateCount` equal horizontal strips | `W` | `expectCount()` first — **the state count must already be final** since the texture is sliced by the *current* `stateCount`. Exposed publicly by subclasses with per-index overloads (see `CycleButtonWidget`). |
| `addTooltip(int state, IDrawable/String)` (protected) | - | `W` | Adds a line to one state's tooltip only. |
| `addTooltipElement/addTooltipLine/addTooltipDrawableLines/addTooltipStringLines/tooltipStatic/tooltipDynamic/tooltipAlignment/tooltipPos/tooltipScale/tooltipTextColor/tooltipTextShadow/tooltipShowUpTimer/tooltipAutoUpdate` (overrides) | - | `W` | All apply to **every** state's tooltip (loop over `this.tooltip[]`), unlike `addTooltip(int, ...)` which targets one state. |
| `disableHoverBackground()` / `disableHoverOverlay()` / `invisible()` (overrides) | - | `W` | Fill the per-state array with `IDrawable.NONE`/`EMPTY` in addition to the single-value fallback from `Widget`. |
| `value(IIntValue<?> value)` (protected) | bound value | `W` | Sets the sync/value source; subclasses expose this publicly with a covariant return type. |
| `stateCount(int)` (protected) | count | `W` | Explicitly (and permanently) fixes the state count. |
| `getStateCount()` / `getIntValue()` | - | `int` / `IIntValue<?>` | Getters. |

**Gotcha:** `updateChild(int state)` falls back to `fallbackChild` (the widget set via the normal `child(IWidget)` call) whenever no `stateChild` exists for the current state — so a plain `.child(x)` call still works as a default/shared child across all states.

*(No direct instantiation in test/ — abstract; see `CycleButtonWidget`/`ToggleButton` below for concrete usage.)*

---

## `com.cleanroommc.modularui.widgets.CycleButtonWidget`

```java
/** @see ToggleButton */
public class CycleButtonWidget extends AbstractCycleButtonWidget<CycleButtonWidget>
```

Public N-state cycle button. Exposes per-index visual/tooltip setters (both `int` and generic `T extends Enum<T>` / `boolean` overloads that just delegate to the `int` form via `.ordinal()` / `0`-or-`1`).

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `value(IIntValue<?>)` (override) | bound int/enum/bool value | `CycleButtonWidget` | Public covariant override of the protected base method. |
| `stateBackground/stateHoverBackground/stateOverlay/stateHoverOverlay(int state, IDrawable drawable)` | index, drawable | `CycleButtonWidget` | Sets a *single* state's visual (as opposed to the base class's whole-texture-slice setters). Background variants also call `disableThemeBackground(true)`/`disableHoverThemeBackground(true)` so the theme's default background doesn't show through. |
| same 4, `(boolean state, ...)` overload | - | `CycleButtonWidget` | `state ? 1 : 0`. |
| same 4, `(T extends Enum<T> state, ...)` overload | - | `CycleButtonWidget` | `state.ordinal()`. |
| `addTooltip(int state, String/IDrawable)` (override) | - | `CycleButtonWidget` | Public covariant override. |
| `length(int)` / `stateCount(int)` (override) | count | `CycleButtonWidget` | `length` is an alias for `stateCount`. |
| `tooltip(int, Consumer<RichTooltip>)` / `tooltipBuilder(int, Consumer<RichTooltip>)` (override) | index, builder | `CycleButtonWidget` | Public covariant overrides of protected base methods; `tooltip` applies the builder once immediately, `tooltipBuilder` registers it to re-run every tooltip refresh. |

### Example (from `TestGuis.java:733-736` and `GLTestGui.java:85-89`)
```java
import com.cleanroommc.modularui.widgets.CycleButtonWidget;

new CycleButtonWidget()
        .value(new IntValue(0))
        .stateCount(3)
        .stateOverlay(GuiTextures.CYCLE_BUTTON_DEMO); // one texture sliced into 3 states

// enum-backed variant
new CycleButtonWidget()
        .value(new EnumValue.Dynamic<>(Type.class, () -> ro.type, val -> ro.type = val))
        .widthRel(1f).height(14)
        .overlay(IKey.dynamic(() -> "Type: " + ro.type.name().toLowerCase(Locale.ROOT)));
```

---

## `com.cleanroommc.modularui.widgets.ToggleButton`

```java
/** @see CycleButtonWidget */
public class ToggleButton extends AbstractCycleButtonWidget<ToggleButton>
```

Boolean on/off button; fixes `stateCount(2)` in its constructor. Uses the theme's `SelectableTheme` (`getToggleButtonTheme()`), rendering the "selected" sub-theme when `isValueSelected()` (state `== 1`), optionally inverted.

### Constructor
- `ToggleButton()` — calls `stateCount(2)`.

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `value(IBoolValue<?>)` | bound bool value | `ToggleButton` | Public covariant override. |
| `valueWrapped(IIntValue<?> intValue, int trueValue)` | an int-backed value, and which int represents "true" | `ToggleButton` | Wraps an `IIntValue` (e.g. a shared "selected option" int) as a bool: `true` iff `intValue.getIntValue() == trueValue`; setting `true` writes `trueValue` back. Used to build a radio-button-style row of `ToggleButton`s sharing one backing int (see example). |
| `isValueSelected()` | - | boolean | `getState() == 1`. |
| `selectedBackground/selectedHoverBackground(IDrawable...)` | drawables for the "true" state | `ToggleButton` | Sugar for `background(true, ...)` / `hoverBackground(true, ...)`. |
| `backgroundOverlay(IDrawable...)` (override) | drawables | `ToggleButton` | **Sets the same background for BOTH states** (indices 0 and 1) — this is how a plain `.background(...)` call behaves on a `ToggleButton`, distinct from `background(boolean, ...)` which targets one state. |
| `hoverBackgroundOverlay(IDrawable...)` (override) | drawables | `ToggleButton` | Delegates to `hoverBackground(false, ...)` — i.e. sets the **unselected** hover background only, despite the name suggesting "both". |
| `background/overlay/hoverBackground/hoverOverlay(boolean selected, IDrawable...)` | target state, drawables | `ToggleButton` | Per-state visual setters keyed by boolean instead of int. |
| `addTooltip(boolean, String/IDrawable)` / `tooltip(boolean, Consumer)` / `tooltipBuilder(boolean, Consumer)` | - | `ToggleButton` | Per-state tooltip setters keyed by boolean. |
| `invertSelected(boolean)` / `invertSelected()` | - | `ToggleButton` / boolean | Flips which physical state (0 or 1) is rendered as "selected" in the theme lookup, without changing the underlying value semantics. |
| `child(boolean selected, IWidget widget)` | state, widget | `ToggleButton` | Sugar for `stateChild(selected ? 1 : 0, widget)`. |

**Gotcha:** `backgroundOverlay` and `hoverBackgroundOverlay` are asymmetric in behavior (see table) — read the source before assuming plain `.background()`/`.hoverBackground()` calls do what you expect on a `ToggleButton`.

### Example (from `GLTestGui.java:107-110` and `TestTile.java:249-266`)
```java
import com.cleanroommc.modularui.widgets.ToggleButton;

new ToggleButton()
        .size(14)
        .stateBackground(GuiTextures.CHECK_BOX) // whole-texture slice, both states
        .value(new BoolValue.Dynamic(() -> ro.depth, val -> ro.depth = val));

// three ToggleButtons acting as a radio group over one shared IntValue `cycleStateValue`
new ToggleButton()
        .valueWrapped(cycleStateValue, 0)
        .tooltipBuilder(false, t -> t.addLine("Not selected"))
        .tooltipBuilder(true, t -> t.addLine("Selected!"))
        .tooltipAutoUpdate(true)
        .overlay(GuiTextures.CYCLE_BUTTON_DEMO.getSubArea(0, 0, 1, 1 / 3f));
```

---

## `com.cleanroommc.modularui.widgets.AbstractFluidDisplayWidget<W>`

```java
public abstract class AbstractFluidDisplayWidget<W extends AbstractFluidDisplayWidget<W>> extends Widget<W> implements RecipeViewerIngredientProvider
```

Base rendering/formatting logic for a fluid-slot-like display: draws a `FluidStack` (optionally partial-height based on a configurable capacity), and optionally an amount string using GTNH's `NumberFormat`/`SIPrefix` utilities. `FluidDisplayWidget` (below) is the only concrete subclass in this package.

### Constructor
- `AbstractFluidDisplayWidget()` (protected) — sets `size(18)` (standard slot size).

### Abstract hooks (implemented by subclasses)
- `protected abstract boolean displayAmountText()`
- `protected abstract @Nullable FluidStack getFluidStack()`

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `getCapacity()` (protected, overridable) | - | int | Return a positive milli-bucket capacity to draw the fluid partially filled by `amount/capacity`; `0` (default) always draws full. |
| `getBaseUnitAmount(double amount)` | raw amount | double | `amount * getBaseUnitSiPrefix().factor`. |
| `getUnit()` | - | `String` (final) | SI-prefix symbol + base unit, e.g. `"mB"`. |
| `getBaseUnit()` | - | `String` | Explicit unit if set via `fluidUnit`, else `"B"` (bucket) or `"L"` (liter) depending on `NumberFormatConfig.useForgeFluidMillibuckets`. |
| `getBaseUnitSiPrefix()` | - | `SIPrefix` | Explicit prefix if set, else `Milli` or `One` matching the same GTNH config flag. |
| `contentPadding(int left,right,top,bottom)` / `(int h,int v)` / `(int all)` | padding | `W` | Inner content padding (default `1` on all sides) — affects both the fluid rect and the amount-text position. |
| `contentPaddingLeft/Right/Top/Bottom(int)` | single side | `W` | Individual setters. |
| `fluidUnit(String symbol, SIPrefix prefix)` | override display unit | `W` | Overrides both `getBaseUnit()` and `getBaseUnitSiPrefix()`. |
| `flipLighterThanAir(boolean)` | - | `W` | When drawing partially and the fluid `isGaseous()` (used as the 1.7.10 stand-in for `isLighterThanAir`), draws fill growing from the top instead of the bottom, if `true` (default). No effect when drawn full. |
| `getStackForRecipeViewer()` | - | `@Nullable ItemStack` | Only returns a value if `ModularUI.Mods.GT5U.isLoaded()` (delegates to `GTUtility.getFluidDisplayStack`) — **hard dependency on GregTech5U being present** for recipe-viewer (JEI/NEI-style) integration; returns `null` otherwise. |
| `getContentPadding()` / `getBaseUnitPrefix()` | - | `Box` / `SIPrefix` | Getters. |

**Gotcha:** the file imports `com.gtnewhorizon.gtnhlib...` and `gregtech.api.util.GTUtility` directly — this widget (and its subclass) are tied to the GTNH/GT5U mod ecosystem for unit formatting and recipe-viewer stack conversion, not generic vanilla-Forge-1.7.10 code.

*(Abstract — not directly constructed; see `FluidDisplayWidget`.)*

---

## `com.cleanroommc.modularui.widgets.FluidDisplayWidget`

```java
public class FluidDisplayWidget extends AbstractFluidDisplayWidget<FluidDisplayWidget>
```

Concrete fluid slot: display-only (no interaction), value-or-sync-driven `FluidStack`.

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `value(IValue<FluidStack>)` | value/sync source | `FluidDisplayWidget` | Accepts any `IValue<FluidStack>` (plain, dynamic, or a `GenericSyncValue`). |
| `value(FluidStack)` | static stack | `FluidDisplayWidget` | Sugar: wraps in `new ObjectValue<>(FluidStack.class, value)`. |
| `capacity(int)` | milli-bucket capacity | `FluidDisplayWidget` | See `getCapacity()` above — enables partial-fill drawing. |
| `displayAmount(boolean)` | show amount text | `FluidDisplayWidget` | Default `true`. |
| `fluidTooltip(BiConsumer<RichTooltip, FluidStack>)` | tooltip builder | `FluidDisplayWidget` | Sugar for `tooltipAutoUpdate(true).tooltipBuilder(t -> tooltip.accept(t, getFluidStack()))` — rebuilds every frame with the current fluid. |
| `getValue()` / `isDisplayAmount()` | - | `@Nullable IValue<FluidStack>` / boolean | Getters. |

### Example (constructed, not from repo)
```java
import com.cleanroommc.modularui.widgets.FluidDisplayWidget;

new FluidDisplayWidget()
        .value(new FluidStack(FluidRegistry.WATER, 8000))
        .capacity(10000)
        .displayAmount(true)
        .pos(0, 0);
```
No `FluidDisplayWidget` usage exists in `test/`; `FluidSlot` (in `widgets.slot`, out of scope here) is what `TestTile.java` uses for interactive fluid slots instead.

---

## `com.cleanroommc.modularui.widgets.ItemDisplayWidget`

```java
public class ItemDisplayWidget extends Widget<ItemDisplayWidget> implements RecipeViewerIngredientProvider
```

Display-only item slot (no player interaction) — value-or-sync-driven `ItemStack`, size defaults to `18` (standard slot).

### Constructor
- `ItemDisplayWidget()` — `size(18)`.

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `item(IValue<ItemStack>)` | value/sync source | `ItemDisplayWidget` | — |
| `item(ItemStack)` | static stack | `ItemDisplayWidget` | Sugar via `ObjectValue`. |
| `displayAmount(boolean)` | show stack-size text | `ItemDisplayWidget` | Default `false` (unlike fluid/slider widgets which mostly default to showing). |
| `getStackForRecipeViewer()` | - | `@Nullable ItemStack` | Just returns `value.getValue()` — no mod-gating (unlike `AbstractFluidDisplayWidget`). |
| `getValue()` / `isDisplayAmount()` | - | getters | — |

**Gotcha:** `draw()` calls `value.getValue()` unconditionally — if `item(...)` was never called, `value` is `null` and this NPEs. Always call `.item(...)` before use, even with a `null`/empty `ItemStack`.

### Example (from `TestGuis.java:696-712` and `TestTile.java:195`)
```java
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;

SlotGroupWidget.builder()
        .matrix("II", "II")
        .key('I', i -> new ItemDisplayWidget().item(TestEventHandler.getRandomItem()))
        .build()
        .coverChildren();

// synced variant (server-driven display slot)
new ItemDisplayWidget().syncHandler("display_item").displayAmount(true);
```

---

## `com.cleanroommc.modularui.widgets.EntityDisplayWidget`

```java
public class EntityDisplayWidget implements IDrawable
```

**Not a `Widget` subclass** — an `IDrawable` that renders a live entity (via `GuiDraw.drawEntity`/`drawEntityLookingAtMouse`); must be attached with `.asWidget()` (or used as a background/overlay) like any other `IDrawable`.

### Constructor
- `EntityDisplayWidget(Supplier<EntityLivingBase> e)` — entity supplied lazily each draw (so it can change/be re-fetched).

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `doesLookAtMouse(boolean)` | enable mouse-tracking rotation | `EntityDisplayWidget` | Switches `draw()` to `GuiDraw.drawEntityLookingAtMouse` (uses `context.getMouseX/Y()`) instead of the static `drawEntity`. |
| `preDraw(Consumer<EntityLivingBase>)` / `postDraw(Consumer<EntityLivingBase>)` | hooks around the entity render call | `EntityDisplayWidget` | Passed through to `GuiDraw.drawEntity(...)`; use to set up custom GL rotation/scale before Minecraft's own entity-render matrix ops (see `TestGuis.buildSpriteAndEntityUI` for the equivalent pattern done manually with a raw `IDrawable`). |
| `draw(GuiContext, int x, int y, int width, int height, WidgetTheme)` | — | - | No-ops if the supplier or its result is `null`. |

**Gotcha:** class-level Javadoc points at `GuiDraw#drawEntity` for the real behavior — this class is a thin wrapper, all rendering nuance (lighting, rotation timing) lives there.

### Example (constructed, not from repo — `test/` builds the equivalent manually rather than using this class; see `TestGuis.java:307-319` for the hand-rolled version with `GuiDraw.drawEntity` directly)
```java
import com.cleanroommc.modularui.widgets.EntityDisplayWidget;

new EntityDisplayWidget(() -> myDragonEntity)
        .doesLookAtMouse(true)
        .asWidget().size(100, 75);
```

---

## `com.cleanroommc.modularui.widgets.CategoryList`

```java
public class CategoryList extends AbstractParentWidget<IWidget, CategoryList> implements Interactable, ILayoutWidget
```

A collapsible/expandable tree-list node ("category" you click to expand/collapse, revealing child rows below it, which may themselves be nested `CategoryList`s). Height of the whole tree is recalculated up the chain on any expand/collapse via `calculateHeightAndLayout`.

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `expanded(boolean)` | new state | - | No-ops if already in that state. Toggles `setEnabled` on every direct child, then calls `calculateHeightAndLayout(true)`. |
| `onMousePressed(int mouseButton)` (override) | 0 or 1 | `Result.SUCCESS` | Left or right click both toggle `expanded`; any other button `Result.ACCEPT` (passthrough). |
| `calculateHeightAndLayout(boolean calculateParents)` | whether to propagate upward | boolean | Lays out direct children vertically starting at `y = getArea().height` (i.e. below this node's own header row) when expanded, accounting for nested-`CategoryList` sub-heights; returns `false` if any child's height isn't calculated yet (deferred layout). If `calculateParents`, bubbles the recalculation to the parent `CategoryList` or the root `CategoryList.Root`. |
| `layoutWidgets()` (override, `ILayoutWidget`) | - | boolean | `calculateHeightAndLayout(false)`. |
| `setCollapsedOverlay(IDrawable)` / `setExpandedOverlay(IDrawable)` | overlay icon per state | `CategoryList` | If unset, inherited from the parent `CategoryList`/`Root` at `onInit()` (falls back to `IDrawable.EMPTY` at the top). |
| `getSubCategories()` / `isExpanded()` / `getTotalHeight()` / `getExpandedOverlay()` / `getCollapsedOverlay()` | - | getters | `getTotalHeight()` includes all expanded descendants' heights. |

### Nested class: `CategoryList.Root`

```java
public static class Root extends ListWidget<IWidget, Root>
```

The top-level container that must host a `CategoryList` tree. Default overlays: `GuiTextures.MOVE_DOWN` (expanded) / `GuiTextures.MOVE_RIGHT` (collapsed), both an 8px arrow icon aligned to the right of each row.

| Method | Notes |
|---|---|
| `layoutWidgets()` (override) | Lays out direct children top-to-bottom (respecting nested `CategoryList` expanded heights) and sets the vertical `ScrollData` scroll size to the total. |
| `setCollapsedOverlay(IDrawable)` / `setExpandedOverlay(IDrawable)` | Root-level default overlay, inherited by any child `CategoryList` that doesn't set its own. |

**Gotcha:** `CategoryList` is only useful nested under a `CategoryList.Root` (or another `CategoryList`) — its `onInit()` inheritance logic and its parent's `calculateHeightAndLayout`/`updateHeight` bubbling assume that ancestry; using it as a bare standalone widget under an arbitrary parent will silently get `IDrawable.EMPTY` overlays and never resize correctly on toggle.

### Example (constructed, not from repo — no usage in `test/`)
```java
import com.cleanroommc.modularui.widgets.CategoryList;

CategoryList.Root root = new CategoryList.Root().widthRel(1f).heightRel(1f);
CategoryList category = new CategoryList()
        .child(IKey.str("Category A").asWidget())
        .child(IKey.str("Row 1").asWidget())
        .child(IKey.str("Row 2").asWidget());
root.child(category);
```

---

## `com.cleanroommc.modularui.widgets.ColorPickerDialog`

```java
public class ColorPickerDialog extends Dialog<Integer>
```

A complete ARGB color-picker popup: hex text field, RGB/HSV tabbed slider pages (via `PagedWidget`/`PageButton`), optional alpha slider, live color preview swatch, and Cancel/Confirm buttons. Closes with the picked color (`Integer`) via `Dialog.closeWith`.

### Constructors
- `ColorPickerDialog(Consumer<Integer> resultConsumer, int startColor, boolean controlAlpha)` — uses default name `"color_picker"`.
- `ColorPickerDialog(String name, Consumer<Integer> resultConsumer, int startColor, boolean controlAlpha)` — full form. `controlAlpha` toggles whether an alpha slider/hex-with-alpha is shown; size is `140 x (106 or 94)` depending on it.

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `updateAll(int color)` | new ARGB color | - | Re-derives every internal field (rgb/hue/sat/value/alpha) from `color` and refreshes all slider gradients + the preview swatch. If `controlAlpha` is false, forces `alpha` back to the previously-held alpha (color input never controls alpha in that mode). |
| `updateColor(int color)` | display color (alpha stripped) | - | Rebuilds each slider's gradient background (e.g. red slider goes from `withRed(color,0)` to `withRed(color,255)`) and sets the preview rectangle. Called after any single-channel edit. |
| `getColor()` / `getRed()` / `getGreen()` / `getBlue()` / `getHue()` / `getSaturation()` / `getHSVValue()` / `getColorAlpha()` / `isControlAlpha()` | - | getters | Live values as currently edited (not just the initial `startColor`). |

**Gotcha:** the hex text field's validator (`validateRawColor`) just prefixes a bare/`0x`-prefixed string with `#`; actual parsing happens in the value's setter via `Long.decode`, which silently logs (`ModularUI.LOGGER.error`) and ignores unparsable input rather than rejecting keystrokes.

### Example (from `TestGuis.java:524-533`)
```java
import com.cleanroommc.modularui.widgets.ColorPickerDialog;

IPanelHandler colorPicker1 = IPanelHandler.simple(panel, (mainPanel, player) ->
        new ColorPickerDialog("color_picker1", color1::color, color1.getColor(), true)
                .setDraggable(true)
                .relative(panel)
                .top(0)
                .rightRel(1f), true);
// later: colorPicker1.openPanel();
```

---

## `com.cleanroommc.modularui.widgets.DropDownMenu`

```java
/** @deprecated in favor of com.cleanroommc.modularui.widgets.menu.DropdownWidget */
@Deprecated
public class DropDownMenu extends SingleChildWidget<DropDownMenu> implements Interactable
```

**Deprecated** — legacy dropdown/combobox implementation; replaced by `widgets.menu.DropdownWidget` (used throughout `TestGuis.buildContextMenu`). Kept for reference only; do not use in new code.

### Constructor
- `DropDownMenu()` — builds an internal disabled `DropDownWrapper` (a `ScrollWidget` holding the choice list) as its single child, with default up/down arrow textures.

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `getSelectedIndex()` / `setSelectedIndex(int)` | - / index | int / `DropDownMenu` | Current selection. |
| `addChoice(Function<Integer, DropDownItem> itemGetter)` | factory receiving the new item's index | `DropDownMenu` | Low-level add. |
| `addChoice(ItemSelected onSelect, IDrawable... drawable)` | selection callback, item visual | `DropDownMenu` | Convenience: wraps a `DropDownItem`, closes the menu and updates selection on click. |
| `addChoice(ItemSelected onSelect, String text)` | callback, plain text | `DropDownMenu` | Sugar using `IKey.str(text)`. |
| `setArrows(IDrawable closed, IDrawable opened)` | icons | `DropDownMenu` | — |
| `setMaxItemsToDisplay(int)` | visible row cap before scrolling | `DropDownMenu` | — |
| `setDropDownDirection(DropDownDirection)` | `UP`/`DOWN` | `DropDownMenu` | Controls whether the popup list appears above or below the closed button. |
| `background(IDrawable...)` (override) | - | `DropDownMenu` | Also forwards the background to the internal wrapper. |

### Nested types
- `enum DropDownDirection { UP, DOWN }` — carries `xOffset`/`yOffset` (unused beyond `0`/`±1`, largely informational).
- `class DropDownItem extends ButtonWidget<DropDownItem>` — a single option row; `canClickThrough()` returns `false`; uses `theme.getFallback()`.
- `interface ItemSelected { void selected(DropDownMenu menu); }` — functional callback for `addChoice`.

**Gotcha:** no usage anywhere in `test/` — `TestGuis.buildContextMenu` uses `widgets.menu.DropdownWidget` instead, confirming this class is legacy-only.

### Example (constructed, not from repo)
```java
import com.cleanroommc.modularui.widgets.DropDownMenu;

new DropDownMenu()
        .addChoice(menu -> ModularUI.LOGGER.info("picked A"), "Option A")
        .addChoice(menu -> ModularUI.LOGGER.info("picked B"), "Option B");
```

---

## `com.cleanroommc.modularui.widgets.DynamicSyncedWidget<W>`

```java
public class DynamicSyncedWidget<W extends DynamicSyncedWidget<W>> extends Widget<W>
```

A widget whose *entire child subtree* can be swapped out at runtime, driven by a `DynamicSyncHandler`/`DynamicLinkedSyncHandler` (server round-trip aware — the new child may itself contain further `SyncHandler`s, which must be registered via `PanelSyncManager.getOrCreateSyncHandler`). Without a sync handler attached, this widget has no effect (empty children list).

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `syncHandler(DynamicSyncHandler)` | handler | `W` | Attaches `updateChild` as the handler's dynamic-widget listener. |
| `syncHandler(DynamicLinkedSyncHandler<?>)` | handler | `W` | Same, different handler type. |
| `initialChild(IWidget child)` | initial widget before first init | `W` | **Throws `IllegalStateException`** if called after the widget is already valid/initialised — must be set before the widget tree is built. |
| `getDynamicSyncHandler()` | - | `@NotNull IDynamicSyncNotifiable` | **Throws `IllegalStateException`** ("Widget is not initialised or not synced!") if called before a sync handler is attached. |

**Gotcha:** the old child is `dispose()`d automatically inside `updateChild` whenever a new one arrives (or `null` is passed with no existing child, which is simply ignored) — you never manage the previous child's lifecycle yourself.

### Example (from `TestTile.java:319-326`)
```java
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;

new DynamicSyncedWidget<>()
        .widthRel(1f)
        .syncHandler(dynamicSyncHandler);

new DynamicSyncedWidget<>()
        .widthRel(1f)
        .coverChildrenHeight()
        .syncHandler(dynamicLinkedSyncHandler);
```

---

## `com.cleanroommc.modularui.widgets.Expandable`

```java
public class Expandable extends Widget<Expandable> implements Interactable, IViewport
```

A two-state (collapsed/expanded) container that click-toggles between a small "normal" view and a larger "expanded" view, with a stencil-clipped slide/scale animation between the two areas. Used for e.g. a small icon that expands into a full crafting-grid panel.

### Constructor
- `Expandable()` — calls `coverChildren()`.

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `collapsedView(IWidget)` | the small/default-state widget | `Expandable` | Replaces the default `EmptyWidget`; initialises immediately if already valid. |
| `expandedView(IWidget)` | the large/expanded-state widget | `Expandable` | Same pattern. |
| `toggle()` | - | - | Flips `expanded`. |
| `expanded(boolean)` | target state | `Expandable` | No-ops if already in that state. If turning on: enables `expandedView`, disables `normalView` (the reverse — re-enabling `normalView`/disabling `expandedView` — only happens once the collapse *animation finishes*, or immediately if `animationDuration <= 0`). Snapshots the current `Area`, then schedules a resize. |
| `stencilTransform(BiConsumer<Rectangle, Boolean>)` | `(rect, expandedFlag) -> mutate rect` | `Expandable` | Adjusts the stencil clip rectangle per frame — e.g. shrink it slightly so child content doesn't render flush to widget edges. Called from `preDraw` with a rect starting at the widget's own area (translated to origin). |
| `animationDuration(int)` | ms | `Expandable` | Default `300`; `0` or negative disables animation (state snaps instantly). |
| `interpolation(IInterpolation)` | easing curve | `Expandable` | Default `Interpolation.SINE_OUT`. |
| `isExpanded()` / `getAnimator()` / `getStencilTransform()` / `getAnimationDuration()` / `getInterpolation()` | - | getters | `getAnimator()` is the currently-running `Animator` for the area transition, or `null` when idle. |

**Gotcha:** `beforeResize` enforces the widget tree only ever has 1 or 2 resizer children (the collapsed and/or expanded view) and throws `IllegalStateException("Invalid Expandable children size")` otherwise — do not `child(...)` anything else onto an `Expandable` directly; use `collapsedView`/`expandedView` exclusively. Also implements `IViewport` purely to apply/remove a `Stencil` clip around drawing — don't confuse this with a scrollable viewport.

### Example (from `TestTile.java:170-197`)
```java
import com.cleanroommc.modularui.widgets.Expandable;

new Expandable()
        .name("expandable")
        .top(0).leftRelOffset(1f, 1)
        .background(GuiTextures.MC_BACKGROUND)
        .stencilTransform((r, expanded) -> {
            r.width = Math.max(20, r.width - 5);
            r.height = Math.max(20, r.height - 5);
        })
        .animationDuration(500)
        .interpolation(Interpolation.BOUNCE_OUT)
        .collapsedView(new ItemDrawable(Blocks.crafting_table).asIcon().asWidget().size(20).pos(0, 0))
        .expandedView(new ParentWidget<>()
                .name("crafting tab")
                .coverChildren()
                .child(new ItemDrawable(Blocks.crafting_table).asIcon().asWidget().size(20).pos(0, 0))
                .child(IKey.str("Expandable & Crafting Demo").asWidget().scale(0.7f).pos(20, 7))
                .child(SlotGroupWidget.builder()
                        .row("III  D")
                        .row("III  O")
                        .row("III   ")
                        .key('I', i -> new ItemSlot().slot(new ModularSlot(craftingInventory, i)))
                        .key('O', new ItemSlot().slot(new ModularCraftingSlot(craftingInventory, 9)))
                        .key('D', new ItemDisplayWidget().syncHandler("display_item").displayAmount(true))
                        .build()
                        .margin(5, 5, 20, 5).name("crafting")));
```

---

## `com.cleanroommc.modularui.widgets.ListValueWidget<T, I, W>`

```java
public class ListValueWidget<T, I extends IWidget, W extends ListValueWidget<T, I, W>> extends ListWidget<I, W>
```

Thin extension of `ListWidget` that additionally tracks a `T` value per child widget `I`, via a `Function<I, T>` extractor supplied at construction. Base class for `SortableListWidget` — no other subclass in this package.

### Constructor
- `ListValueWidget(Function<I, T> widgetToValue)` — the extractor used by `getValues()`.

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `getValues()` | - | `List<T>` | Maps every current typed child through `widgetToValue`, in list order. |
| `children(Iterable<V> values, Function<V, I> widgetCreator)` | source values, per-value widget factory | `W` | Adds one child per value (build helper distinct from `ListWidget.children(Iterable<I>)`, which takes already-built widgets). |

**Gotcha:** no getter for the `widgetToValue` extractor itself — it's write-once at construction and only used internally by `getValues()`.

*(No direct instantiation in test/ — see `SortableListWidget` for the concrete subclass and real usage.)*

---

## `com.cleanroommc.modularui.widgets.ListWidget<I, W>`

```java
public class ListWidget<I extends IWidget, W extends ListWidget<I, W>> extends AbstractScrollWidget<I, W> implements ILayoutWidget, IParentWidget<I, W>
```

A general-purpose scrollable list container: lays out children sequentially along one axis (default vertical, via `VerticalScrollData`), with optional separators between items, disabled-child collapsing, cross-axis alignment, reversed order, and a "shrink to content up to a max size" mode (`wrapTight`/`maxSize*`).

### Constructor
- `ListWidget()` — `super(null, null)`; scroll data is lazily set to `VerticalScrollData` in `onInit()` if never explicitly configured.

### Layout/behavior methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `scrollDirection(GuiAxis)` / `scrollDirection(ScrollData)` | axis or full scroll config | `W` | Switches the list's main axis and scrolling behavior (removes/re-sets the scroll area's data). |
| `childSeparator(IIcon)` | separator graphic drawn between items | `W` | Adds `separator.width`/`height` of spacing between consecutive children along the main axis; drawn in `draw()` at each computed separator position. |
| `children(Iterable<I>)` / `children(int amount, IntFunction<I>)` / `children(Iterable<T>, Function<T,I>)` | bulk-add | `W` | Three overloads: existing widgets, index-generated widgets, value-mapped widgets. |
| `collapseDisabledChild()` / `collapseDisabledChild(boolean)` | - / flag | `W` | Default **enabled** — disabled children are skipped during layout so the list has no gaps; set `false` to reserve their space anyway (used by `SortableListWidget`, which handles removal via animation instead). |
| `crossAxisAlignment(Alignment.CrossAxis)` | alignment across the non-scroll axis | `W` | Default `CENTER`. |
| `reverseLayout(boolean)` | - | `W` | Lays out (and iterates via `getOrderedChildren()`) in reverse child order when `true`. |
| `wrapTight()` | - | `W` | Marks the list to shrink itself to its content size (up to `maxSize*`) rather than always filling its allotted size. |
| `maxSize(int)` / `maxSizeRel(float)` / `maxSizeRelOffset(float,int)` / `maxSize(DoubleSupplier)` / `maxSizeRel(DoubleSupplier)` / `maxSizeRelOffset(DoubleSupplier,int)` | cap on main-axis size | `W` | All imply `wrapTight()`. Pixel, relative, relative-with-pixel-offset, and dynamic (`DoubleSupplier`) variants. |
| `getValues`-adjacent getters | `getSeparatorSize()`, `getScrollData()`, `getAxis()`, `getOrderedChildren()`, `getChildSeparator()`, `isCollapseDisabledChild()`, `isWrapTight()`, `getCaa()`, `isReverseLayout()` | - | Plain getters. |

**Gotcha:** `layoutWidgets()` explicitly skips widgets that already `hasPos(axis)` (an explicit position on the main axis) — mixing manually-positioned children into a `ListWidget` is supported but those children don't participate in the sequential main-axis flow (they still get cross-axis alignment via `postLayoutWidgets`, but not main-axis placement/order).

### Example (from `TestGuis.java:134` and `buildSearchTest`, `TestGuis.java:459-479`)
```java
import com.cleanroommc.modularui.widgets.ListWidget;

new ListWidget<>().widthRel(1f).expanded()
        .children(uiMethods.size(), i -> button(nameFor(uiMethods.get(i))));

// filterable item list
new ListWidget<>()
        .collapseDisabledChild()
        .expanded().widthRel(1f)
        .children((Iterable<Item>) GameData.getItemRegistry(), item -> {
            ItemStack stack = new ItemStack(item);
            String text = stack.getDisplayName();
            return Flow.row().height(20).widthRel(1f).padding(2)
                    .setEnabledIf(w -> text.toLowerCase().contains(searchValue.getStringValue()))
                    .child(new ItemDrawable(stack).asWidget())
                    .child(new ScrollingTextWidget(IKey.str(text)).expanded().height(16));
        });
```

---

## `com.cleanroommc.modularui.widgets.PageButton`

```java
public class PageButton extends Widget<PageButton> implements Interactable
```

A tab/page-selector button bound to a `PagedWidget.Controller`; renders itself using the theme's `SelectableTheme` (selected/unselected sub-themes), and switches the controller's active page on click if not already active.

### Constructor
- `PageButton(int index, PagedWidget.Controller controller)` — `index` is the page this button activates; calls `disableHoverBackground()` by default.

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `isActive()` | - | boolean | `controller.getActivePageIndex() == this.index`. |
| `background(boolean active, IDrawable...)` | which state, drawables | `PageButton` | Sets the background used only while **inactive** when `active=false` (0/1/many drawables → `null`/single/`DrawableStack`); calling with `active=true` just delegates to the normal `background(IDrawable...)`. |
| `tab(TabTexture texture, int location)` | pre-sliced tab texture, tab slot index | `PageButton` | Convenience for the common "tab strip" look: sets active/inactive backgrounds from `texture.get(location, selected)` (both orientations depending on `invertSelected()`), disables hover background, and sizes itself to the texture's dimensions. |
| `invertSelected(boolean)` / `invertSelected()` | - | `PageButton` / boolean | Swaps which physical selection state is rendered as the theme's "selected" variant — same pattern as `ToggleButton`/`PageButton` internals. |
| `getIndex()` / `getController()` / `isInvert()` | - | getters | — |

**Gotcha:** `onMousePressed` only calls `controller.setPage(index)` (and plays the click sound) **if not already active** — clicking the currently-active tab is a no-op that still reports `Result.ACCEPT` (not `SUCCESS`), so it doesn't retrigger `onPageChange`.

### Example (from `ColorPickerDialog.java:58-63` and `TestTile.java:164-168`)
```java
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;

PagedWidget.Controller controller = new PagedWidget.Controller();
Flow.row()
        .child(new PageButton(0, controller).sizeRel(0.5f, 1f).overlay(IKey.str("RGB")))
        .child(new PageButton(1, controller).sizeRel(0.5f, 1f).overlay(IKey.str("HSV")));

// tab-strip style
new PageButton(0, tabController)
        .tab(GuiTextures.TAB_TOP, -1)
        .overlay(new ItemDrawable(Blocks.chest).asIcon());
```

---

## `com.cleanroommc.modularui.widgets.PagedWidget<W>`

```java
public class PagedWidget<W extends PagedWidget<W>> extends Widget<W>
```

A container that holds N child "pages" (each a full widget subtree) and shows exactly one at a time (`setEnabled` toggling), driven either directly or through a `Controller` handle so page-switch buttons (`PageButton`) don't need a reference to the widget itself.

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `onPageChange(@Nullable IntConsumer)` | callback | `W` | Fires right after a page switch is applied (new page already enabled); also fires once with `0` right after `afterInit()` sets the initial page. |
| `setPage(int page)` | target index | - | **Throws `IndexOutOfBoundsException`** if out of `[0, pages.size())`. Disables current page, enables the new one, fires `onPageChange`. |
| `nextPage()` / `previousPage()` | - | - | Wrap around at the ends. |
| `initialPage(int page)` | starting page | `W` | Only takes effect if called **before** the widget is valid (silently ignored otherwise, since `afterInit()` has already applied the previous value). |
| `addPage(IWidget widget)` | page content | `W` | Adds and disables it (`setEnabled(false)`) immediately — `afterInit()` re-enables whichever is `currentPageIndex`. |
| `controller(Controller controller)` | - | `W` | Binds a `Controller` to this widget (`controller.setPagedWidget(this)`). |
| `getPages()` / `getCurrentPage()` / `getCurrentPageIndex()` | - | getters | `getPages()` is also `getChildren()` — unmodifiable per `@Unmodifiable` annotation on the return, though the underlying `ArrayList` is only *conventionally* not mutated elsewhere. |

### Nested class: `PagedWidget.Controller`

Indirection so `PageButton`/other UI can reference a controller before the `PagedWidget` itself exists (created first, wired to the widget via `.controller(...)` during widget construction).

| Method | Notes |
|---|---|
| `isInitialised()` | `pagedWidget != null && pagedWidget.isValid()`. |
| `setPage(int)` / `nextPage()` / `previousPage()` / `getActivePage()` / `getActivePageIndex()` | All call `validate()` first, which **throws `IllegalStateException`** if the controller isn't yet bound to a valid `PagedWidget` — a `Controller` used before its `PagedWidget` is constructed/initialised will throw. |

### Example (from `ColorPickerDialog.java:53, 83-88`)
```java
import com.cleanroommc.modularui.widgets.PagedWidget;

PagedWidget.Controller controller = new PagedWidget.Controller();
// ... build PageButtons bound to `controller` ...
new PagedWidget<>()
        .left(5).right(5).expanded()
        .controller(controller)
        .addPage(createRGBPage(alphaSlider))
        .addPage(createHSVPage(alphaSlider));
```

---

## `com.cleanroommc.modularui.widgets.ProgressWidget`

```java
public class ProgressWidget extends Widget<ProgressWidget>
```

The classic "arrow fills up as progress goes 0→1" machine-GUI widget: an empty-bar texture (always drawn) plus a full-bar texture drawn partially based on a `double` progress value, in one of 4 linear directions or as a 4-quadrant circular fill.

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `value(IDoubleValue<?>)` | bound/synced progress source | `ProgressWidget` | — |
| `progress(double)` | static progress `[0,1]`-ish (not clamped here) | `ProgressWidget` | Wraps in a plain `DoubleValue`. |
| `progress(DoubleSupplier)` (deprecated) | dynamic supplier | `ProgressWidget` | `@ApiStatus.ScheduledForRemoval(inVersion = "3.3.0")` — use `value(new DoubleValue.Dynamic(...))` instead. |
| `texture(UITexture emptyTexture, UITexture fullTexture, int imageSize)` | separate empty/full textures, pixel size along the fill axis | `ProgressWidget` | `imageSize` matters only for non-smooth (stepped) rendering — see `getProgressUV`. |
| `texture(UITexture texture, int imageSize)` | one texture, empty on top half / full on bottom half (UV-split) | `ProgressWidget` | Sugar: `texture.getSubArea(0,0,1,0.5f)` / `getSubArea(0,0.5f,1,1)`. |
| `direction(Direction)` | fill direction | `ProgressWidget` | `LEFT`, `RIGHT` (default), `UP`, `DOWN`, or `CIRCULAR_CW`. |
| `getCurrentProgress()` | - | float | Current bound value as float. |
| `getProgressUV(float uv)` | raw progress | float | If `ModularUIConfig.smoothProgressBar` is off, snaps to the nearest `1/imageSize` step (pixel-quantized fill, matching classic Minecraft machine GUIs); otherwise passes through unchanged. |

**Gotcha:** for `CIRCULAR_CW`, `onInit()` re-slices `fullTexture[0]` into 4 quadrant textures (`fullTexture[0..3]`) the first time — so the same single `UITexture` passed to `texture(...)` is reinterpreted as one full square image sliced into corners, not 4 separate assets; this only happens once and only for the circular direction.

### Example (from `TestGuis.java:701-704` and `TestTile.java:271-277`)
```java
import com.cleanroommc.modularui.widgets.ProgressWidget;

new ProgressWidget()
        .size(20)
        .texture(GuiTextures.PROGRESS_ARROW, 20)
        .value(new DoubleValue.Dynamic(() -> Minecraft.getSystemTime() % 5000 / 5000.0, null));

// synced + circular
new ProgressWidget()
        .syncHandler("progress")
        .texture(GuiTextures.PROGRESS_CYCLE, 20)
        .direction(ProgressWidget.Direction.CIRCULAR_CW);
```

---

## `com.cleanroommc.modularui.widgets.RichTextWidget`

```java
public class RichTextWidget extends Widget<RichTextWidget> implements IRichTextBuilder<RichTextWidget>, Interactable
```

Renders a `RichText` (multi-run styled/interactive text — colors, item icons, hover tooltips, click targets — see `drawable.text.RichText`) and forwards all mouse/keyboard interaction to whichever inline element is currently hovered (icons, hoverable text runs, etc. act like mini sub-widgets inside the text flow).

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `markDirty()` | - | - | Forces the text to be rebuilt from `builder` on next draw. |
| `autoUpdate(boolean)` | rebuild every frame instead of only when dirty | `RichTextWidget` | When `true`, `builder` runs on **every** `draw()` call — needed for content with per-frame dynamic pieces (e.g. `IKey.dynamicKey(...)`, seen in `buildRichTextUI`). |
| `textBuilder(Consumer<RichText>)` | build function; called with a cleared `RichText` | `RichTextWidget` | Also calls `markDirty()` immediately. |
| `getHoveredElement()` / `getHoveredElement(ModularGuiContext)` | - | `@Nullable Object` | The specific inline rich-text element under the mouse (only non-null while `isHovering()`); interaction methods (`onMousePressed` etc.) check `instanceof Interactable`/`IHoverable` on this. |
| `getRichText()` | - | `IRichTextBuilder<?>` | Returns the backing `RichText` (implements the fluent `IRichTextBuilder` add/newLine/etc. API used to actually compose content inside `textBuilder`). |
| `isAutoUpdate()` | - | boolean | Getter. |

**Gotcha:** all `Interactable` overrides (`onMousePressed`, `onKeyPressed`, `onMouseScroll`, etc.) are pure passthroughs to the currently-hovered inline element (if it implements `Interactable`) — the widget itself is never directly interactable beyond that; if nothing hoverable is under the mouse the calls just report the "no interaction happened" default (`ACCEPT`/`IGNORE`/`false`, matching each method's convention).

### Example (from `TestGuis.java:322-380`, abbreviated)
```java
import com.cleanroommc.modularui.widgets.RichTextWidget;

new RichTextWidget()
        .sizeRel(1f).margin(7)
        .autoUpdate(true)
        .textBuilder(text -> text
                .add("Hello ")
                .add(new ItemDrawable(new ItemStack(Blocks.grass)).asIcon().asHoverable()
                        .tooltip(t -> t.addFromItem(new ItemStack(Blocks.grass))))
                .add(IKey.GREEN + "This is a long ")
                .add(IKey.str("string").style(IKey.DARK_PURPLE).asTextIcon().asHoverable()
                        .addTooltipLine("Text Tooltip"))
                .newLine()
                .textShadow(false));
```

---

## `com.cleanroommc.modularui.widgets.SchemaWidget`

```java
@ApiStatus.Experimental
public class SchemaWidget extends Widget<SchemaWidget> implements Interactable
```

Renders an interactive 3D preview of a fake-world block layout (`ISchema`/`BaseSchemaRenderer`) — drag to rotate/pan, scroll to zoom. Class Javadoc: **"Schema renderer not working due to framebuffer issues"** — marked `@ApiStatus.Experimental`; treat as unstable/broken.

### Constructors
- `SchemaWidget(ISchema schema)` — wraps it in a new `BaseSchemaRenderer(schema)`.
- `SchemaWidget(BaseSchemaRenderer schema)` — direct.

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `scale(float)` / `incrementScale(float)` | absolute / delta | `SchemaWidget` / - | Camera distance/zoom. |
| `pitch(float)` | radians | `SchemaWidget` | Clamped to `(-PI/2, PI/2)` minus a small epsilon (avoids gimbal-lock at the poles). |
| `yaw(float)` | radians | `SchemaWidget` | Wrapped into `[0, 2π)`. |
| `offset(float x, float y, float z)` | pan offset added to the schema's focus point | `SchemaWidget` | — |
| `enableDragRotation/enableDragTranslation/enableScrollScaling(boolean)` | toggle each interaction independently | `SchemaWidget` | — |
| `enableInteraction(boolean rotation, boolean translation, boolean scaling)` / `enableAllInteraction(boolean)` | bulk toggles | `SchemaWidget` | — |
| `getBlockUnderMouse()` | - | `MovingObjectPosition` | Last raycast hit from the schema renderer (`schema.getLastRayTrace()`). |
| `getScale/getPitch/getYaw/getOffset/getLastMouseX/getLastMouseY/isEnableScaling/isEnableTranslation/isEnableRotation/getSchema` | - | getters | — |

Interaction: left-drag rotates (yaw/pitch from mouse delta), middle-drag pans (screen-relative right/up vectors computed from the camera look vector), scroll zooms (`incrementScale`).

### Nested class: `SchemaWidget.LayerButton`

```java
public static class LayerButton extends ButtonWidget<LayerButton>
```

A button cycling through a schema's Y-layers to filter what's rendered (for "peel back layers" style inspection), showing the current layer number or `"ALL"`.

| Method | Notes |
|---|---|
| `LayerButton(ISchema schema, int minLayer, int maxLayer)` | Left-click increments the current layer (starting from `minLayer`), right-click decrements (starting from `maxLayer`); going past either bound resets to "ALL" (`Integer.MIN_VALUE` sentinel). Installs `schema.setRenderFilter(...)` to actually hide blocks above the current layer. |
| `startLayer(int start)` | Sets the initial displayed layer. Returns `this`. |

### Example (from `TestGuis.java:408-427`)
```java
import com.cleanroommc.modularui.widgets.SchemaWidget;
import com.cleanroommc.modularui.utils.fakeworld.ArraySchema;

ISchema schema = ArraySchema.builder()
        .layer("D   D", "     ", "     ", "     ")
        .layer(" DDD ", " E E ", "     ", "     ")
        .where('D', "minecraft:gold_block")
        .where('E', "minecraft:emerald_block")
        .build();

panel.child(new SchemaWidget(schema).full())
     .child(new SchemaWidget.LayerButton(schema, 0, 3).bottom(1).left(1).size(16));
```

---

## `com.cleanroommc.modularui.widgets.ScrollingTextWidget`

```java
public class ScrollingTextWidget extends TextWidget<ScrollingTextWidget>
```

A single-line text widget that, when hovered and too wide for its area, animates the text scrolling left-then-back (with pauses) instead of clipping, and shows the full text as a tooltip as a fallback/accessibility aid.

### Constructor
- `ScrollingTextWidget(IKey key)` — auto-registers a `tooltipBuilder` that adds the full text as a tooltip line **only if** `line.getWidth() > getArea().width` (i.e. only when actually truncated), with a `showUpTimer(10)`.

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `scrollSpeed(int)` | duration **per pixel**, in ms (default `15`) | `ScrollingTextWidget` | Total forward-scroll duration = `lineWidth * speed`; backward is `3/4` of that. |
| `animator(Animator animator)` | curve/config template | `ScrollingTextWidget` | Builds the full forward→wait(500)→backward→wait(1000) `SequentialAnimator`, repeated 20 times, from the given `Animator` as a template (copied for the backward leg). Called lazily on first draw if never set explicitly, defaulting to `Interpolation.SINE_INOUT`. |
| `getProgress()` / `getAnimator()` / `getForward()` / `getBackward()` / `getSpeed()` | - | getters | `getProgress()` is the current scroll offset `[0, upperWidth]`. |

**Gotcha:** the scroll animation only runs while hovered — `onMouseStartHover` resumes it, `onMouseEndHover` stops+resets it (and zeroes `progress`), so unhovering mid-scroll always snaps back to the start rather than pausing in place.

### Example (from `TestGuis.java:473-478`)
```java
import com.cleanroommc.modularui.widgets.ScrollingTextWidget;

new ScrollingTextWidget(IKey.str(text))
        .widgetTheme(IThemeApi.BUTTON)
        .textAlign(Alignment.CENTER)
        .expanded()
        .height(16)
        .invisible();
```

---

## `com.cleanroommc.modularui.widgets.SliderWidget`

```java
public class SliderWidget extends Widget<SliderWidget> implements Interactable
```

A draggable-handle slider bound to an `IDoubleValue`, along either axis, with optional "stopper" snap points (either an explicit list or an evenly-spaced step).

### Constructor
- `SliderWidget()` — `sliderHeight(1f).sliderWidth(6)` (handle fills the cross-axis, 6px along the main axis), registers a mouse-release listener to clear `dragging`, and calls `bounds(0, 100)`.

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `value(IDoubleValue<?>)` | bound/synced value | `SliderWidget` | — |
| `bounds(double min, double max)` | value range | `SliderWidget` | Auto-swaps if `min > max`. If already valid, immediately re-clamps/pushes the current value into the new bounds. |
| `stopper(DoubleList)` / `stopper(double...)` | explicit snap points | `SliderWidget` | Adds to (doesn't replace) any existing stopper list, then sorts. |
| `stopper(double each)` | evenly spaced step | `SliderWidget` | Snap points generated lazily in `onInit()` from `min` to `max` in increments of `each` (plus `max` itself). |
| `setAxis(GuiAxis)` | `X` (default) or `Y` | `SliderWidget` | — |
| `sliderWidth(int)` / `sliderWidth(float)` | pixel / relative-to-widget-width | `SliderWidget` | Handle width. |
| `sliderHeight(int)` / `sliderHeight(float)` | pixel / relative | `SliderWidget` | Handle height. |
| `sliderSize(int,int)` / `sliderSize(float,float)` | both dims at once | `SliderWidget` | — |
| `sliderTexture(IDrawable)` | handle graphic | `SliderWidget` | Default `GuiTextures.BUTTON_CLEAN`. |
| `stopperTexture(IDrawable)` | snap-point tick graphic | `SliderWidget` | Default a semi-transparent white `Rectangle`. |
| `stopperSize(int w, int h)` | tick mark dimensions | `SliderWidget` | Default `2x4`. |
| `setValue(double value, boolean setSource)` | new value, whether to push to bound value | - | Snaps to nearest stopper if any are configured (uses a "distance stopped increasing" scan, not a true nearest search — see gotcha), clamps to `[min,max]`. |
| `posToValue(int p)` / `valueToPos(double value)` | pixel ↔ value conversion along the slider's travel range | double / int | Public — useful for custom drag/tick math. |
| `getSliderValue()` | - | double | Current bound value, or `0.0` if unbound. |
| `isDragging()` | - | boolean | True between mouse-press and mouse-release on the handle. |

**Gotcha:** `setValue`'s stopper-snapping algorithm assumes the stopper list is sorted and scans for the first point where distance-to-value starts *increasing* again, then uses the *previous* point — this works for a sorted list but is a linear "local minimum" search, not a full nearest-neighbor scan; with unusual/unsorted stopper sets (bypassed via direct field mutation, not possible via the public API) behavior would be undefined. Via the public API (`stopper(...)` always sorts) this is safe.

### Example (from `GLTestGui.java:95-100` and `ColorPickerDialog.java:162-169`)
```java
import com.cleanroommc.modularui.widgets.SliderWidget;

new SliderWidget()
        .widthRel(1f).height(14)
        .overlay(IKey.str("Z-Layer"))
        .bounds(140, 180)
        .value(new DoubleValue.Dynamic(() -> ro.zLevel, val -> ro.zLevel = (float) val));

// vertical, custom handle, used inside ColorPickerDialog
new SliderWidget()
        .expanded().heightRel(1f)
        .background(background.asIcon().size(0, 4))
        .sliderTexture(handleBackground)
        .sliderSize(2, 8)
        .bounds(0, 255)
        .value(new DoubleValue.Dynamic(() -> this.red, this::updateRed));
```

---

## `com.cleanroommc.modularui.widgets.SlotGroupWidget`

```java
public class SlotGroupWidget extends ParentWidget<SlotGroupWidget>
```

Groups a set of item slots (typically `ItemSlot`s) as one logical unit — ties them to a shared `SlotGroup` (for e.g. shift-click routing/sorting) and provides both a ready-made player-inventory layout and a text-matrix `Builder` for arbitrary custom slot grids.

### Static factories (player inventory)
| Method | Params | Returns | Notes |
|---|---|---|---|
| `playerInventory(boolean positioned)` | whether to auto-position (`bottom(7)`, horizontally centered) | `SlotGroupWidget` | `positioned=false` leaves slots at raw grid coordinates only. |
| `playerInventory(int bottom, boolean horizontalCentered)` | explicit offset + centering flag | `SlotGroupWidget` | — |
| `playerInventory(int bottom, boolean horizontalCentered, SlotConsumer slotConsumer)` | + per-slot customization hook | `SlotGroupWidget` | `slotConsumer.apply(index, slot)` lets you wrap/modify each generated `ItemSlot` (e.g. add a background) before it's added. |
| `playerInventory(SlotConsumer slotConsumer)` | customization hook only, no auto-position | `SlotGroupWidget` | Builds the classic 9 hotbar + 27 main-inventory `ItemSlot`s synced to key `"player"`, named `"slot_0"`.. `"slot_35"`. |

### Instance methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `slotGroup(String slotGroupName)` / `slotGroup(SlotGroup slotGroup)` | group identifier by name or object | `SlotGroupWidget` | Any `ItemSlot` child added afterward (that is synced and has a slot) is auto-assigned to this group in `onChildAdd`. |
| `setSlotsSynced(String name)` | sync-handler key base | - | Retroactively calls `syncHandler(name, i)` on every direct `ISynced` child, in child order. |
| `builder()` (static) | - | `Builder` | Entry point for the matrix-based construction API (below). |
| `getSlotGroupName()` / `getSlotGroup()` | - | getters | — |

**Gotcha:** a large chunk of "sort buttons" integration (`SortButtons`, `placeSortButtonsTopRight*`) is present in source but entirely commented out (`// TODO: bogo compat`) — none of that API is actually usable currently despite appearing referenced in comments.

### Nested class: `SlotGroupWidget.Builder`

Declarative grid builder using an ASCII matrix: each row is a string, each character maps to either a static `IWidget`, an `IntFunction<IWidget>` factory (called once per occurrence of that character, receiving a 0-based occurrence count), or blank space (` `, skipped — reserves an 18px cell).

| Method | Params | Returns | Notes |
|---|---|---|---|
| `matrix(String... rows)` | replace the whole matrix | `Builder` | — |
| `row(String row)` | append one row | `Builder` | — |
| `key(char c, IWidget widget)` | static widget for a char | `Builder` | **Throws `IllegalArgumentException`** at `build()` time if that char appears more than once in the matrix (a static widget instance can't be reused across the tree). |
| `key(char c, IntFunction<IWidget> widget)` | factory for a char, called once per occurrence | `Builder` | Use this (not the static form) for any character repeated in the matrix. |
| `synced(String name)` | sync-handler key base | `Builder` | If set, every `ISynced` widget placed gets `.syncHandler(name, syncId++)` in placement order (row-major). |
| `slotGroup(String)` / `slotGroup(SlotGroup)` | group assignment, forwarded to the built widget | `Builder` | — |
| `build()` | - | `SlotGroupWidget` | Places each widget at `(x, y)` = `(column * 18, row * 18)`, sizes the resulting widget to the matrix's pixel bounding box. |

### Example (from `TestGuis.java:696-712`, `TestTile.java:188-196, 218-222, 328`)
```java
import com.cleanroommc.modularui.widgets.SlotGroupWidget;

SlotGroupWidget.builder()
        .matrix("II", "II")
        .key('I', i -> new ItemDisplayWidget().item(TestEventHandler.getRandomItem()))
        .build()
        .coverChildren();

// mixed static + repeated-key matrix
SlotGroupWidget.builder()
        .row("III  D")
        .row("III  O")
        .row("III   ")
        .key('I', i -> new ItemSlot().slot(new ModularSlot(craftingInventory, i)))
        .key('O', new ItemSlot().slot(new ModularCraftingSlot(craftingInventory, 9))) // static, only used once
        .key('D', new ItemDisplayWidget().syncHandler("display_item").displayAmount(true))
        .build();

// player inventory
panel.child(SlotGroupWidget.playerInventory(false));
```

---

## `com.cleanroommc.modularui.widgets.TextWidget<W>`

```java
public class TextWidget<W extends TextWidget<W>> extends Widget<W>
```

The base "draw a line/block of formatted text" widget (backing `IKey.asWidget()` for plain keys); handles alignment, color, shadow, scale, and computing default width/height by simulating the text layout via `TextRenderer`.

### Constructors
- `TextWidget(IKey key)` — bind to an `IKey` (may itself be dynamic/composite).
- `TextWidget(String key)` — sugar for `this(IKey.str(key))`.

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `textAlign(Alignment)` | text alignment within the widget area | `W` | Default `Alignment.CenterLeft`. `alignment(Alignment)` is a **deprecated** alias. |
| `color(int)` / `color(@Nullable IntSupplier)` | fixed / dynamic text color | `W` | `null` supplier falls back to the active `WidgetTheme`'s text color. |
| `scale(float)` | text scale multiplier | `W` | Default `1f`. |
| `shadow(@Nullable Boolean)` | force shadow on/off, or `null` to use theme default | `W` | — |
| `style(EnumChatFormatting)` | applies a vanilla chat-formatting style to the underlying `IKey` | `W` | Mutates the key in place. |
| `maxWidth(int)` | wrap/measurement cap in pixels | `W` | `-1` (default) means "unbounded" / derive from parent width. |
| `getDefaultWidth()` / `getDefaultHeight()` (overrides) | - | int | Simulate-render the text (via a non-drawing `TextRenderer` pass) to derive natural size; source of automatic sizing for un-sized text widgets. |
| `checkString()` (protected) | - | `String` | Re-fetches `key.getFormatted()`; if changed since last call, invokes `onTextChanged(newText)` (triggers an immediate, non-scheduled resize via `WidgetTree.resizeInternal`). |
| `onTextChanged(String)` (protected, overridable) | new formatted text | - | Hook point for subclasses (`ScrollingTextWidget` overrides to rebuild its scroll line). |
| `canHoverThrough()` (override) | - | `true` | Text widgets never "consume" hover for click-through purposes by default. |
| `getKey/getAlignment/getScale/getColor/isShadow/getMaxWidth` | - | getters | — |

**Gotcha:** width/height simulation depends on `getParent().resizer().isWidthCalculated()`/the screen area as a fallback when no explicit/`maxWidth` bound exists — a `TextWidget` measured before its parent's width is resolved (or outside a properly-sized tree) can compute an oversized bound based on the whole screen width.

### Example (from `TestGuis.java:289` and pervasive `IKey.str(...).asWidget()` usage throughout `test/`)
```java
import com.cleanroommc.modularui.widgets.TextWidget;

new TextWidget<>(IKey.str("Test String")).scale(0.6f).horizontalCenter().top(7);
```

---

## `com.cleanroommc.modularui.widgets.TransformWidget`

```java
public class TransformWidget extends DelegatingWidget
```

Wraps an existing widget to apply extra per-frame viewport transforms (translate/rotate/scale) on top of whatever the wrapped widget already does — useful for animating a widget's visual position/rotation without altering its actual layout `Area`.

### Constructor
- `TransformWidget(IWidget child)` — delegates all normal widget behavior to `child`.

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `transform(Consumer<IViewportStack> transform)` | per-frame dynamic transform callback | `TransformWidget` | Called every `transform(IViewportStack)` invocation (i.e. every frame during layout/draw traversal) **after** any constant transform below. |
| `translate(float x, float y)` | - | `TransformWidget` | Accumulates into a constant `Matrix4f` (`constTransform`), applied once via `stack.multiply(...)` before the dynamic callback. Marks `hasConstTransform = true`. |
| `rotate(float angle, float x, float y, float z)` | radians, axis | `TransformWidget` | Same constant-matrix accumulation. |
| `scale(float x, float y)` | - | `TransformWidget` | Same; z-scale fixed at `1`. |
| `hasConstTransform()` | - | boolean | Whether any `translate`/`rotate`/`scale` has been called. |

**Gotcha:** `translate`/`rotate`/`scale` all compose into the **same** `constTransform` matrix in call order (each just does `constTransform.translate(...)`/`.rotate(...)`/`.scale(...)` on the existing matrix) — chaining `.translate(...).scale(...)` is order-sensitive exactly like raw matrix math, not three independent named transforms.

### Example (from `TestGuis.java:216-233`)
```java
import com.cleanroommc.modularui.widgets.TransformWidget;

IWidget widget = GuiTextures.MUI_LOGO.asWidget().size(20).pos(65, 65);
panel.child(new TransformWidget(widget)
        .transform(stack -> {
            double angle = Math.PI;
            float x = (float) (55 * Math.cos(animator.getValue() * angle));
            float y = (float) (55 * Math.sin(animator.getValue() * angle));
            stack.translate(x, y);
        }));
```

---

## `com.cleanroommc.modularui.widgets.ValueWidget<W, T>`

```java
public class ValueWidget<W extends ValueWidget<W, T>, T> extends Widget<W> implements IValueWidget<T>
```

Minimal base widget carrying an immutable `T` payload accessible via `getWidgetValue()` — for building simple "this widget IS a value" widgets (e.g. list-item markers) without pulling in a full sync/value framework. Distinct from `IValue`-driven widgets (`ItemDisplayWidget`, `SliderWidget`, etc.), which wrap a mutable/observable value; this is a fixed, construction-time value tag.

### Constructor
- `ValueWidget(T widgetValue)` — the wrapped value, fixed for the widget's lifetime (no setter).

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `getWidgetValue()` | - | `T` | The constructor-supplied value; implements `IValueWidget<T>`. |

**Gotcha:** genuinely minimal — adds nothing over `Widget` except the value field/getter; almost always subclassed rather than used directly (compare `SortableListWidget.Item<T>`, which follows the same `IValueWidget<T>` pattern but extends `DraggableWidget` instead for drag support).

### Example (constructed, not from repo — no direct instantiation in test/)
```java
import com.cleanroommc.modularui.widgets.ValueWidget;

class TagWidget extends ValueWidget<TagWidget, String> {
    TagWidget(String tag) { super(tag); }
}
new TagWidget("category-a").overlay(IKey.str("category-a"));
```

---

## `com.cleanroommc.modularui.widgets.VoidWidget`

```java
public class VoidWidget extends EmptyWidget
```

**Effectively unusable/dead class.** Its sole constructor is `private VoidWidget() { throw new UnsupportedOperationException(); }` — it can never be instantiated by any caller, including via reflection-based tooling that respects the `private` modifier. There is no static factory either.

**Gotcha:** if you need a genuinely empty placeholder widget, use `com.cleanroommc.modularui.widget.EmptyWidget` (this class's superclass) directly instead — `VoidWidget` appears to be either vestigial or a placeholder for future API that was never finished.

*(No example possible — the class cannot be constructed.)*

---

## Cross-cutting gotchas

- **State-array widgets (`AbstractCycleButtonWidget`, `CycleButtonWidget`, `ToggleButton`)**: always fix the state count (explicitly via `stateCount`/`length`, or implicitly by binding an enum/bool value) *before* calling any per-state visual/tooltip setter that depends on slicing a whole texture (`stateBackground(UITexture)` etc.) — those slice by the *current* count, and no error is thrown if it's wrong, only a logged one if the count was never set at all.
- **`Dialog`/`ColorPickerDialog`** are `ModularPanel`s, not `Widget`s — they must be opened through the panel-handler machinery (`IPanelHandler.simple(...).openPanel()`), not added as a `.child(...)` of another widget.
- **Display-only widgets (`ItemDisplayWidget`, `FluidDisplayWidget`)** will NPE if drawn before their value is set — always chain `.item(...)`/`.value(...)` immediately at construction.
- Several classes here are explicitly marked as legacy/experimental/dead: `DropDownMenu` (`@Deprecated`, replaced by `widgets.menu.DropdownWidget`), `SchemaWidget` (`@ApiStatus.Experimental`, "not working due to framebuffer issues"), `VoidWidget` (unconstructable). Prefer their replacements or avoid them in new code.
