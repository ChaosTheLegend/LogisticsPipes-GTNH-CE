# ModularUI2 — `widget` package reference

Covers `com.cleanroommc.modularui.widget` (top-level), `com.cleanroommc.modularui.widget.scroll`, and
`com.cleanroommc.modularui.widget.sizer`. Source root: `src/main/java/com/cleanroommc/modularui/widget/`.

Examples are adapted from `src/main/java/com/cleanroommc/modularui/test/TestGui.java` and `TestGuis.java` unless
marked "Example (constructed, not from repo)". Statements not directly stated in Javadoc but derived from reading
the resizing/layout algorithm are prefixed `Inferred:`.

---

## 0. Read this first — the sizing/positioning model

Every `IWidget` owns two objects:

- an **`Area`** (`getArea()`) — absolute `x,y,w,h`, a parent-relative `rx,ry`, a `z` layer, and `margin`/`padding`
  boxes (`com.cleanroommc.modularui.widget.sizer.Area`, `Box`).
- a **`StandardResizer`** (`resizer()`) — the builder/solver that *computes* the `Area`'s `x,y,w,h` once per resize
  pass (`com.cleanroommc.modularui.widget.sizer.StandardResizer`).

`Widget<W>` does not store position/size fields itself. Every fluent method like `.pos()`, `.width()`, `.widthRel()`
etc. (defined as `default` methods on `IPositioned<W>`, which `Widget<W>` implements) is a thin wrapper that
configures the `StandardResizer`. The resizer is only *evaluated* later, during the widget-tree resize pass
(`WidgetTree.resizeInternal` → `InternalWidgetTree.resize`), not at call time. Chained builder calls therefore just
accumulate configuration; order of calls generally does not matter except when the same axis slot is overwritten
(see "unit slots" below).

### `Unit` — one edge or size value

`com.cleanroommc.modularui.widget.sizer.Unit` (package-internal, `@ApiStatus.Internal`) stores a single number used
for a start/end/size coordinate:

- `value` (`float`) or a `DoubleSupplier` (for dynamic/animated values) — mutually exclusive, set via `setValue`.
- `measure`: `Unit.Measure.PIXEL` (literal pixels) or `Unit.Measure.RELATIVE` (fraction 0..1 of the reference size).
- `offset` (`int`): a flat pixel amount added *after* the relative/pixel value is resolved. This is what
  `xxxRelOffset(val, offset)` methods set — e.g. "50% of parent width, plus 4px".
- `anchor` (`float`) + `autoAnchor` (`boolean`): only meaningful for RELATIVE start/end units. The anchor is the
  fraction of *this widget's own size* that lines up with the relative point on the parent. `getAnchor()`:
  ```java
  public float getAnchor() {
      float val = getValue();
      return isAutoAnchor() && isRelative() && val < 1 ? val : this.anchor;
  }
  ```
  Inferred: this is why `leftRel(0.5f)` alone centers a widget horizontally — with `autoAnchor=true` (the default
  for the `xxxRel(float)` overloads) and a value `< 1`, the anchor automatically equals the value itself. So
  `leftRel(0.5f)` means "put the point that is 50% across *this widget* at 50% across the parent" = centered.
  `horizontalCenter()`/`center()` in `IPositioned` are literally `leftRel(0.5f)` / `leftRel(0.5f) + topRel(0.5f)`.
  Methods with an explicit `anchor` parameter (`leftRelAnchor`, `leftRel(val, offset, anchor)`) set `autoAnchor=false`
  and use the given anchor instead — e.g. `rightRelAnchor(0, 1f)` anchors the widget's *right* edge (anchor 1.0) to a
  point measured from the parent's right.

### `DimensionSizer` — one axis (X or Y) of a widget

`com.cleanroommc.modularui.widget.sizer.DimensionSizer` (`@ApiStatus.Internal`) holds up to **two** `Unit` slots
(`p1`, `p2`) that get assigned the roles `start` (left/top), `end` (right/bottom), or `size` (width/height) on
demand. Only two of the three roles can be active at once per axis:

- `start` + `size` → position is `start`, size is explicit (most common: `.pos(x,y).size(w,h)` / `.left().width()`).
- `start` + `end` (no `size`) → size is *computed* as `end − start` (CSS-style "pin both edges").
- `end` + `size` (no `start`) → position computed by anchoring from the parent's far edge.
- neither `start` nor `end` → position defaults to `0` (or is left for a layout widget like `Flow`/`Grid` to place).
- no `size` → falls back to `IWidget.getDefaultWidth()/getDefaultHeight()` (for a bare `Widget<W>`, this is the
  current theme's default size, `18` px if not yet valid — see `Widget.getDefaultWidth()`).

Gotcha (`DimensionSizer.getNext`): calling a *third distinct* role (e.g. `left()` then `right()` then `width()`) on
the same axis overwrites the oldest still-unused-for-that-role slot; in dev-mode this logs
`"unit {} of widget {} was already used and will be overwritten with unit {}"`. In practice: don't call all three of
`left/right/width` (or `top/bottom/height`) on the same widget — pick two.

`sizeDependsOnParent()` (RELATIVE size), `posDependsOnParent()` (has an `end`, or a RELATIVE `start`), and
`dependsOnChildren()` (`coverChildrenMinSize >= 0`, see below) are what the multi-pass resize algorithm uses to
decide calculation order (see §0.3).

### `StandardResizer` — per-widget resizer, one `DimensionSizer` per axis

`com.cleanroommc.modularui.widget.sizer.StandardResizer extends WidgetResizeNode implements IPositioned<StandardResizer>`.
Every `AbstractWidget` owns exactly one (`resizer()`), created in `Widget()`'s constructor:
`resizer(new StandardResizer(this))`. All `IPositioned<W>` default methods on the widget itself
(`left`, `right`, `top`, `bottom`, `width`, `height`, ...) just forward to `resizer().left(...)` etc. with fixed
`Unit.Measure`/`autoAnchor` arguments (see the table in §1.2 for exact values).

Key resizer-level concepts:

- **`expanded()`** — marks this widget as flexible along the parent's main axis. Only meaningful when the parent
  implements `IExpander` (e.g. `Flow.row()`/`Flow.column()` — not in this doc's scope). In dev env,
  `detectConflictingConfiguration()` warns if `expanded()` is set but the parent isn't an `IExpander`. Mechanically:
  `InternalWidgetTree.resize` computes the parent's `expandAxis` and calls `child.checkExpanded(expandAxis)`, which
  flips `DimensionSizer.expanded=true` for that axis only; the actual extra space is then distributed by the parent
  layout widget (`ILayoutWidget.layoutWidgets()`), not by `StandardResizer` itself. Real example —
  `TestGuis.java:667-683` (`buildWrappedFlowUI`): inside a wrapping `Flow.row()`, alternating children get
  `widget.resizer().expanded()` to fill leftover row space:
  ```java
  // TestGuis.java:676-682
  .children(5, i -> {
      IWidget widget = rndRect(colors, rnd).asWidget()
              .width(rnd.nextInt(maxRectSize - minRectSize) + minRectSize)
              .height(rnd.nextInt(maxRectSize - minRectSize) + minRectSize)
              .name("rect_" + (i + 1));
      if (i % 2 == 1) widget.resizer().expanded();
      return widget;
  });
  ```
- **`coverChildrenWidth(minWidth)` / `coverChildrenHeight(minHeight)`** (via `IPositioned.coverChildren*`) — makes
  this widget's size on that axis equal to the bounding box of its non-decoration children (plus their margins,
  plus this widget's own padding), clamped to at least `minSize`. `minSize < 0` disables it
  (`disableCoverChildrenWidth()`/`Height()`). Resolved in `StandardResizer.postResize()` →
  `doCoverChildren(...)`/`coverChildrenForLayout(...)` (the latter used when the widget also implements
  `ILayoutWidget`, e.g. `Flow`/`Grid`, since those position children themselves). If *all* children also depend on
  the parent for that axis (nothing independent to measure), `GuiError.throwNew(..., SIZING, "Can't cover children
  width/height when all children depend on their parent and min size is 0!")` is thrown unless a positive min size
  was given. Real examples: `TestGuis.java:522` `new ModularPanel("colors").width(300).coverChildrenHeight()`;
  `TestGuis.java:687-689` `new ModularPanel("machine_like").coverChildren().padding(7)`.
- **`decoration(boolean)`** — excludes this widget's resizer from the parent's `coverChildren` bounding-box
  calculation and from margin/padding participation. Used for purely-visual overlay children that shouldn't affect
  the parent's auto-size. Example: `TestGuis.java:716-724` a `ParentWidget<>()` title-bar decoration:
  ```java
  // TestGuis.java:716-724
  .child(new ParentWidget<>()
          .coverChildren()
          .decoration()
          .padding(3)
          .background(GuiTextures.MC_BACKGROUND.getSubArea(0, 0, 1, 0.5f))
          .horizontalCenter()
          .anchorTop(1)
          .child(IKey.str("Machine Name").asWidget())
          .name("title"));
  ```
- **`relative(ResizeNode)` / `relative(IWidget)` / `relativeToParent()` / `relativeToScreen()`** — overrides which
  `ResizeNode` percentages/anchors are computed against, independent of the actual widget-tree parent. Default is
  the widget-tree parent's resizer. `relativeToScreen()` walks up to the root `ScreenResizeNode` (the screen's own
  area) and anchors to that instead — e.g. positioning a dialog relative to the whole screen instead of its logical
  parent panel. Real example — `TestGuis.java:524-528` (`buildColorTheoryUI`), a color-picker dialog is positioned
  relative to the panel that opened it even though it isn't a widget-tree child of it:
  ```java
  // TestGuis.java:524-528
  new ColorPickerDialog("color_picker1", color1::color, color1.getColor(), true)
          .setDraggable(true)
          .relative(panel)
          .top(0)
          .rightRel(1f)
  ```
- **`anchorLeft/Right/Top/Bottom(float)`** — set an explicit anchor (see `Unit.anchor` above) without touching
  `autoAnchor`'s value-based shortcut; `anchorRight`/`anchorBottom` internally store `1 - val` because the end-unit
  math measures from the far edge.
- **`padding`/`margin`** are **not** on the resizer — they live on the widget's own `Area.getPadding()`/`getMargin()`
  (`Box`), set via `IPositioned.padding(...)`/`margin(...)`. Padding shrinks the space children compute RELATIVE
  sizes against and is what `coverChildren*` adds back around the children's bounding box. Margin is space this
  widget requests *around itself* that a covering/laying-out parent must respect (`Area.requestedWidth()` =
  `width + margin.horizontal()`).

### §0.3 Multi-pass resize algorithm (why order mostly doesn't matter)

`InternalWidgetTree.resize(ResizeNode, init, onOpen, isParentLayout)` recursively resizes the whole `ResizeNode`
tree (rooted, ultimately, at a `ScreenResizeNode`). Because a widget can simultaneously (a) need its own size to be
finished before a `coverChildren` parent can finish, and (b) need the parent's finished size before a `RELATIVE`
child can resolve, a single top-down or bottom-up pass cannot always finish everything. The algorithm therefore:

1. Calls `resize(child)` for each child (a widget may partially resolve — e.g. size known but position not, or
   vice versa — see `DimensionSizer.apply`'s three branches).
2. Collects children that are not "fully calculated" into `anotherResize` and retries just those, repeatedly, until
   nothing changes.
3. After children are done, `postResize()` runs `coverChildren` (needs children's areas) then `preApplyPos`/
   `applyPos` convert every resizer's relative `rx,ry` into absolute `x,y` top-down (`WidgetTree.applyPos`).
4. If a full pass makes no progress and something is still unresolved, `ModularUI` logs a `GuiError` and (in a
   running game) prints a chat message `"ModularUI: Failed to resize sub tree of ..."` — this is the practical
   symptom of a bad/contradictory sizing configuration (e.g. two RELATIVE children each covering the other, or
   `coverChildren` with no independent child and no min size).

Practical rule of thumb: RELATIVE units need the reference (`relativeTo`, normally the parent) to already have a
known size on that axis; `coverChildren*` needs its independent (non-parent-dependent) children to already have a
known size. Circular configurations across a single axis (parent width depends on child, child width depends on
parent) will fail to resolve.

---

## 1. `com.cleanroommc.modularui.widget` — top-level

### 1.1 `AbstractWidget`

`com.cleanroommc.modularui.widget.AbstractWidget` — minimal concrete implementation of `IWidget`; base class of
`Widget<W>` and `DelegatingWidget`. Owns the `Area`, the `StandardResizer`, validity/parent/panel/context state,
hover-time tracking, and the init/dispose lifecycle.

```java
public abstract class AbstractWidget implements IWidget
```

#### Lifecycle

| Method | Purpose |
|---|---|
| `void initialise(@NotNull IWidget parent, boolean late)` (`final`) | Called by the framework when the panel opens or a widget is added later (`late=true`). Sets `parent`/`panel`/`context`, bumps `z`, hooks the resizer into the parent's (`resizer.initialize(...)`), sets `valid=true`, then calls `onInitInternal(late)` → `onInit()` → recursively initialises children → `afterInit()` → `onResized()`. Throws `IllegalStateException` if `resizer` is still `null` (i.e. a subclass failed to call `resizer(...)` before this runs). |
| `void onInit()` | `@ApiStatus.OverrideOnly`. Runs after this widget is initialised, before children. Override for setup that must happen before children exist. |
| `void afterInit()` | `@ApiStatus.OverrideOnly`. Runs after this widget **and its children** are initialised. |
| `void dispose()` | `@MustBeInvokedByOverriders`. Recursively disposes children, clears parent/panel/context (unless this is a `ModularPanel`), disposes the resizer, resets hover timers, `valid=false`. |

#### Gui-context accessors (throw `IllegalStateException` if `!isValid()`)

| Method | Returns |
|---|---|
| `ModularScreen getScreen()` | screen of the panel |
| `@NotNull IWidget getParent()` | widget-tree parent |
| `ModularGuiContext getContext()` | gui context |
| `@NotNull ModularPanel getPanel()` | owning panel |
| `Area getParentArea()` | parent's `Area`, skipping through `IDelegatingWidget`s |
| `boolean isValid()` | true once between `initialise` and `dispose` (no throw) |

#### Hover / tick state

| Method | Purpose |
|---|---|
| `void onUpdate()` | increments `timeHovered`/`timeBelowMouse` while under the mouse; called 20/s |
| `onMouseStartHover/EndHover/EnterArea/LeaveArea()` | `@MustBeInvokedByOverriders` hooks resetting the counters |
| `boolean isHoveringFor(int ticks)` / `isBelowMouseFor(int ticks)` | duration checks |
| `int getTicksHovered()` / `getTicksBelowMouse()` | raw counters (`-1` when not applicable) |

#### Area / resizer / name

| Method | Purpose |
|---|---|
| `Area getArea()` | this widget's mutable `Area` — treat as read-mostly outside the resizer |
| `@NotNull StandardResizer resizer()` | the resizer instance |
| `void resizer(StandardResizer resizer)` (`protected`) | swaps in a different resizer subclass (e.g. `ExpanderResizer`); must be called before `initialise` |
| `@Nullable String getName()` / `setName(String)` (`protected`) | debug name, see `Widget.name(String)` |
| `boolean isName(String)` / `nameContains(String)` | name comparison helpers |
| `String toString()` | `SimpleClassName` or `SimpleClassName#name` |

Example (constructed, not from repo):

```java
public class MyWidget extends AbstractWidget {
    // must call resizer(new StandardResizer(this)) before use — Widget<W> does this already.
}
```

---

### 1.2 `Widget<W extends Widget<W>>`

`com.cleanroommc.modularui.widget.Widget<W>` — **the base class for almost all UI elements**; used directly for
plain decorative/interactive rectangles (`new Widget<>()`), and as the superclass of every other widget in scope
here.

```java
public class Widget<W extends Widget<W>> extends AbstractWidget
        implements IPositioned<W>, ITooltip<W>, ISynced<W>
```

`new Widget<>()` sets `resizer(new StandardResizer(this))` in its constructor — this is the only constructor.

Most of `Widget`'s fluent surface is actually declared as `default` methods on the three interfaces it implements
(`IPositioned<W>`, `ITooltip<W>`, `ISynced<W>`); the tables below list everything, noting the declaring
type. All builder methods return `W` (via `getThis()`, an unchecked self-cast) for chaining.

#### 1.2.1 Position & size (`IPositioned<W>`, `api/widget/IPositioned.java`) — all forward into the `StandardResizer` described in §0

Coordinate space: pixel values are relative to the resolved reference node (normally the direct parent's content
box, see `relative(...)` in §0 to change this). `Rel` methods use `Unit.Measure.RELATIVE` (fraction 0..1, see §0).
`Offset` variants add a flat pixel nudge after the relative math. `Anchor` variants set an explicit pivot instead of
the auto-anchor-from-value shortcut.

| Method | Resizer call | Meaning |
|---|---|---|
| `left(int val)` | `resizer().left(val,0,0,PIXEL,true)` | absolute left edge, `val` px from reference origin |
| `leftRel(float val)` | `left(val,0,0,RELATIVE,true)` | left edge at `val` fraction of reference width; **auto-anchors** to `val` itself if `val<1` (see §0) |
| `leftRelOffset(float val,int offset)` | `left(val,offset,0,RELATIVE,true)` | as above, `+offset` px |
| `leftRelAnchor(float val,float anchor)` | `left(val,0,anchor,RELATIVE,false)` | relative left with explicit anchor, no auto-anchor |
| `leftRel(float val,int offset,float anchor)` | `left(val,offset,anchor,RELATIVE,false)` | full control: value, offset, anchor |
| `left(float val,int offset,float anchor,Unit.Measure measure)` | `left(...,false)` | fully explicit, any measure |
| `left(DoubleSupplier val, Unit.Measure measure)` | `left(val,0,0,measure,true)` | dynamic (e.g. animated) left value |
| `right(...)`, `rightRel(...)`, `rightRelOffset(...)`, `rightRelAnchor(...)` | mirrors of `left*` using `getRight()` unit | measured from the reference's **right** edge; `anchorRight` stores `1-val` internally |
| `top(...)`, `topRel(...)`, `topRel*` | mirrors of `left*` on the Y axis | — |
| `bottom(...)`, `bottomRel(...)`, `bottomRel*` | mirrors of `right*` on the Y axis | — |
| `width(int val)` | `resizer().width(val,0,PIXEL)` | fixed pixel width |
| `widthRel(float val)` | `width(val,0,RELATIVE)` | width = `val` fraction of reference width minus reference's padding |
| `widthRelOffset(float val,int offset)` | `width(val,offset,RELATIVE)` | as above `+offset` px |
| `width(float val, Unit.Measure measure)` / `width(DoubleSupplier val, Unit.Measure measure)` | — | explicit/dynamic width |
| `height(...)`, `heightRel(...)`, `heightRelOffset(...)` | mirrors of `width*` on Y | — |
| `pos(int x,int y)` | `left(x).top(y)` | shorthand |
| `posRel(float x,float y)` | `leftRel(x).topRel(y)` | shorthand |
| `posRel(Alignment alignment)` | `leftRel(alignment.x).topRel(alignment.y)` | position from an `Alignment` (0..1 fractions) |
| `size(int w,int h)` / `size(int val)` | `width(w).height(h)` | shorthand (square if one arg) |
| `sizeRel(float w,float h)` / `sizeRel(float val)` | `widthRel(w).heightRel(h)` | shorthand |
| `fullWidth()` / `fullHeight()` / `full()` | `widthRel(1f)` etc. | fill reference on that axis |
| `expanded()` | `resizer().expanded()` | flex-fill remaining space along a `Flow`-like parent's main axis; see §0 |
| `coverChildrenWidth()/(int)`, `coverChildrenHeight()/(int)`, `coverChildren()/(int)/(int,int)` | `resizer().coverChildren*` | size == children bounding box (+min); see §0 |
| `disableCoverChildrenWidth/Height/()` | `coverChildren*(-1)` | opt out |
| `decoration(boolean)` / `decoration()` | `resizer().decoration(...)` | exclude from parent's `coverChildren`/margin-padding accounting; see §0 |
| `relative(ResizeNode)` / `relative(IWidget)` / `relative(Area)` (deprecated) | `resizer().relative(...)` | change the reference node for RELATIVE units/anchors |
| `relativeToParent()` | `resizer().relativeToParent()` | reset reference to the widget-tree parent |
| `relativeToScreen()` | `resizer().relativeToScreen()` | reference = the screen's root area |
| `anchorLeft/Right/Top/Bottom(float val)` | `resizer().anchorX(val)` | explicit anchor pivot, `autoAnchor=false` |
| `horizontalCenter()` / `verticalCenter()` / `center()` | `leftRel(0.5f)` / `topRel(0.5f)` / both | centers via the auto-anchor trick (§0) |
| `resizer(Consumer<StandardResizer>)` | runs the consumer against `resizer()` | escape hatch for resizer APIs with no `IPositioned` shortcut |
| `padding(int...)` overloads (`all`/`horizontal,vertical`/`left,right,top,bottom`) | `getArea().getPadding().all(...)` + `scheduleResize()` | shrinks the box children measure RELATIVE sizes against |
| `paddingLeft/Right/Top/Bottom(int)` | single-edge padding | — |
| `margin(int...)` overloads, `marginLeft/Right/Top/Bottom(int)` | `getArea().getMargin()...` + `scheduleResize()` | space requested around this widget (affects a covering parent) |
| `align(Alignment)`, `alignX/Y(...)`, `anchor(Alignment)` | *deprecated, scheduled for removal 3.3.0* | use `posRel`/`anchorLeft` etc. instead |

Trivial passthroughs: `resizer()`, `getArea()`, `requiresResize()`, `scheduleResize()`, `getThis()` — declared as
abstract/plain accessors, implemented by `AbstractWidget`/`Widget`.

Real example — `TestGui.java:59-63`, a plain `Widget<>` used as a themed, tooltip-bearing label that fills the
remaining row space and matches the row height:

```java
// TestGui.java:59-63
.child(new Widget<>()
        .addTooltipLine(line)
        .widgetTheme(IThemeApi.BUTTON)
        .overlay(IKey.str(line))
        .expanded().heightRel(1f))
```

Real example — `TestGui.java:92-96`, absolute + mixed pixel/anchored positioning on a `SortableListWidget`
(an `AbstractParentWidget` subclass, so `Widget`'s `IPositioned` methods apply the same way):

```java
// TestGui.java:92-96
panel.child(sortableListWidget
        .onRemove(stringItem -> this.availableElements.get(stringItem.getWidgetValue()).available = true)
        .pos(10, 10)
        .bottom(23)
        .width(100));
```
Here `left=10, top=10` (via `pos`) are the `start` units and `bottom=23` is the `end` unit for the Y axis, so
height is computed as `parentHeight - 23 - 10` (§0's "start + end, no size" case); width is a fixed 100px.

#### 1.2.2 Background / overlay / shadow / theme

| Method | Purpose | Gotcha |
|---|---|---|
| `W shadow(IDrawable... shadow)` | drawn first, before background; unaffected by theme/hover | purely decorative layer |
| `W backgroundOverlay(IDrawable... background)` | sets `background` field, drawn after the theme background | does **not** disable the theme background |
| `W background(IDrawable... background)` | `backgroundOverlay(...).disableThemeBackground(true)` | disables theme background too — prefer this over `backgroundOverlay` for a full replacement |
| `W overlay(IDrawable... overlay)` | drawn after the widget's own `draw()` and after backgrounds | does not touch theme |
| `W hoverBackgroundOverlay(IDrawable... background)` | hover-only background override | `null`→falls back to theme hover bg; `IDrawable.EMPTY`→invisible; `IDrawable.NONE`→use normal (non-hover) background |
| `W hoverBackground(IDrawable... background)` | `hoverBackgroundOverlay(...).disableHoverThemeBackground(true)` | — |
| `W hoverOverlay(IDrawable... overlay)` | hover-only overlay | same `EMPTY`/`NONE` semantics as above |
| `W disableThemeBackground(boolean)` / `disableHoverThemeBackground(boolean)` | toggles theme bg use | — |
| `W disableHoverBackground()` | `hoverBackgroundOverlay(NONE).disableHoverThemeBackground(true)` | forces hover state to reuse the normal background |
| `W disableHoverOverlay()` | `hoverOverlay(NONE)` | forces hover state to reuse the normal overlay |
| `W widgetTheme(String id)` / `widgetTheme(WidgetThemeKey<?>)` | overrides which `WidgetTheme` entry this widget pulls colors/textures from (ids in `IThemeApi`, e.g. `IThemeApi.BUTTON`) | throws `IllegalArgumentException` if the id string doesn't resolve |
| `W invisible()` | `disableThemeBackground(true).disableHoverBackground()` | strips all background rendering, keeps overlay/content |
| `getBackground()/getOverlay()/getHoverBackground()/getHoverOverlay()/getShadow()` | `@Nullable IDrawable` getters | the *set* value, not necessarily what's currently drawn — use `getCurrentBackground(theme)`/`getCurrentOverlay(theme)` for that |
| `getThemeBackground(WidgetThemeEntry)` (2 overloads) | resolves the theme-provided background, honoring hover/disable flags | — |
| `isDisableThemeBackground()` / `isDisableHoverThemeBackground()` | trivial getters | — |
| `getWidgetTheme(ITheme)` (`final`) / `getWidgetTheme(ITheme, Class<T>)` (`final`) | resolves the effective `WidgetThemeEntry`, honoring `widgetTheme(...)` override | the typed overload throws `IllegalStateException` on a type mismatch |
| `getWidgetThemeOverride()` | the raw override key, if set | — |

`AvailableElement` in `TestGui.java:115-130` shows overriding `getBackground()` for dynamic per-instance
backgrounds (a `ButtonWidget` subclass), and even throws from an overridden `backgroundOverlay(...)` to force
callers toward `overlay()` instead — a useful pattern for "this widget's chrome must not be replaced" widgets.

Real example — `TestGuis.java:569-573`, plain `Widget<>` with a hover-swap background:

```java
// TestGuis.java:569-573
.child(new Widget<>()
        .center()
        .size(50, 50)
        .background(GuiTextures.MC_BUTTON)
        .hoverBackground(GuiTextures.MC_BUTTON_HOVERED));
```

#### 1.2.3 Tooltip (`ITooltip<W>`, `api/widget/ITooltip.java`)

`Widget` implements `getTooltip()`/`tooltip()`/`tooltip(RichTooltip)` directly (lazily creates a `RichTooltip` on
first `tooltip()` call); everything else is a default method on `ITooltip<W>` building on those three.

| Method | Purpose |
|---|---|
| `@Nullable RichTooltip getTooltip()` | current tooltip, `null` if never touched |
| `@NotNull RichTooltip tooltip()` | get-or-create |
| `W tooltip(RichTooltip)` | replace outright |
| `boolean hasTooltip()` | non-null and non-empty |
| `W tooltip(Consumer<RichTooltip>)` / `tooltipStatic(...)` | run a one-time builder against `tooltip()` |
| `W tooltipBuilder(Consumer<RichTooltip>)` / `tooltipDynamic(...)` | register a builder **re-run every time the tooltip is marked dirty** — use for tooltips whose content changes (values, translations) |
| `W tooltipPos(RichTooltip.Pos)` / `tooltipPos(int x,int y)` | tooltip placement strategy or fixed pos |
| `W tooltipAlignment(Alignment)` | content alignment inside the tooltip |
| `W tooltipTextShadow(boolean)` / `tooltipTextColor(int)` / `tooltipScale(float)` | text styling |
| `W tooltipShowUpTimer(int ticks)` | hover delay before the tooltip appears (consumed by `AbstractWidget`'s `isHoveringFor`) |
| `W tooltipAutoUpdate(boolean)` | force per-frame tooltip rebuild (normally only `ValueSyncHandler` changes trigger a rebuild) |
| `W addTooltipElement(String\|IDrawable)` | append inline (same line) |
| `W addTooltipLine(ITextLine\|IDrawable\|String)` | append as a new line (`String` → `IKey.str(line)`) |
| `W addTooltipDrawableLines(Iterable<IDrawable>)` / `addTooltipStringLines(Iterable<String>)` | bulk append |
| `W removeAllTooltips()` | `tooltip().reset()` |
| `markTooltipDirty()` (on `Widget`, not the interface) | invalidates so a `tooltipBuilder` re-runs; called automatically when a bound `ValueSyncHandler` changes |

Real example — `TestGui.java:59-63` (`addTooltipLine(line)`, a plain string line) and
`TestGuis.java:295-306` (`DraggableWidget<>().tooltipBuilder(tooltip -> { tooltip.addLine(...); tooltip.alignment(...);
tooltip.scale(0.5f); tooltip.pos(RichTooltip.Pos.NEXT_TO_MOUSE); })`) — the latter is a one-shot static builder in
practice (called via `tooltipBuilder` even though nothing dynamic changes), demonstrating the multi-line + styling
API.

#### 1.2.4 Sync / value (`ISynced<W>`, plus direct members on `Widget`)

| Method | Purpose | Gotcha |
|---|---|---|
| `boolean isSynced()` | has a resolved `SyncHandler` | only meaningful after init |
| `@NotNull SyncHandler<?> getSyncHandler()` | throws `IllegalStateException` if none | — |
| `@Nullable IValue<?> getValue()` | the bound value handler (numbers/strings/etc.) | — |
| `W syncHandler(String name, int id)` | registers this widget to look up a sync handler by key from the panel's `PanelSyncManager` at init time | preferred over constructing/holding a `SyncHandler` directly since it decouples client/server widget trees |
| `initialiseSyncHandler(ModularSyncManager, boolean late)` | resolves the handler (own field, or by `syncKey` lookup, including the "main" PSM fallback) and calls `setSyncOrValue(...)`; also wires `markTooltipDirty()` as the default change-listener for a `ValueSyncHandler` | throws `IllegalStateException` if both a value **and** a sync key were set — mutually exclusive |
| `setSyncOrValue(ISyncOrValue)` (`protected`, `@MustBeInvokedByOverriders`) | hook for subclasses to react to the resolved handler/value | must call `super` |

#### 1.2.5 Update listener / gui actions / misc

| Method | Purpose |
|---|---|
| `void onUpdate()` (`@MustBeInvokedByOverriders`) | calls `super.onUpdate()` then the registered update listener |
| `@Nullable Consumer<W> getOnUpdateListener()` | current listener |
| `W onUpdateListener(Consumer<W> listener)` / `onUpdateListener(listener, boolean merge)` | registers a per-tick callback; `merge=true` chains onto an existing listener instead of replacing it |
| `W setEnabledIf(Predicate<W> condition)` | shorthand: `onUpdateListener(w -> setEnabled(condition.test(w)), true)` — re-evaluates every tick; merges so it won't clobber another update listener set *before* it, but a later plain `onUpdateListener(...)` call (without `merge=true`) will overwrite it |
| `W listenGuiAction(IGuiAction action)` | registers a raw mouse/keyboard listener on the screen (auto (de)registered with this widget's lifecycle); fires even if this widget is disabled/not hovered |
| `W transform(BiConsumer<W, IViewportStack>)` | custom per-frame transform, applied in `transform(IViewportStack)` in addition to the default translate-by-`rx,ry` |
| `W disabled()` | `setEnabled(false)`, convenient inline during tree construction |
| `W name(String name)` | sets the debug name used by `WidgetTree.findFirstWithName(...)` and `toString()` |
| `W excludeAreaInRecipeViewer()` / `excludeAreaInRecipeViewer(boolean)` | marks this widget's area as excluded from recipe-viewer (JEI/NEI-style) overlap detection |
| `int getDefaultWidth()` / `getDefaultHeight()` | theme's default size once valid, else `18` |
| `Object getAdditionalHoverInfo(...)` | drag-resize corner detection hook for `IDragResizeable` widgets |
| `W getThis()` | unchecked self-cast used by every builder method |

Real example — `TestGui.java:76-82` (`onMousePressed` here belongs to `ButtonWidget`, **not** `Widget` — see the
callout below) combined with a plain `Widget<>`-level pattern from `TestGuis.java:442-445`
(`onUpdateListener` toggling `setEnabled` randomly):

```java
// TestGuis.java:437-446
.children(12, i -> new Widget<>()
        .widthRel(1f)
        .height(16)
        .widgetTheme(IThemeApi.BUTTON)
        .overlay(IKey.str(String.valueOf(i + 1)))
        .onUpdateListener(w -> {
            if (rnd.nextDouble() < 0.05) {
                w.setEnabled(!w.isEnabled());
            }
        }))
```

> **Important — `onMousePressed`/`onMouseTapped` are *not* `Widget` builder methods.** They are builder methods
> declared on `com.cleanroommc.modularui.widgets.ButtonWidget` (outside this doc's scope, package
> `com.cleanroommc.modularui.widgets`), which internally implements `Interactable`
> (`com.cleanroommc.modularui.api.widget.Interactable`) and exposes `onMousePressed(IGuiAction.MousePressed)` /
> `onMouseTapped(IGuiAction.MousePressed)` as fluent setters. Every `.onMousePressed(...)`/`.onMouseTapped(...)` call
> in `TestGui.java`/`TestGuis.java` (e.g. `TestGui.java:64-66`, `TestGuis.java:143-160`) is on a `ButtonWidget<>` (or
> a subclass like `AvailableElement`/`ContextMenuButton`), not on a bare `Widget<>`. A plain `Widget<W>` only
> implements `Interactable`'s *default* no-op behavior if it doesn't override it — to make a bare `Widget` react to
> clicks you'd need to subclass it and override `onMousePressed(int)`/`onMouseTapped(int)` yourself, or use
> `listenGuiAction(...)` with an `IGuiAction.MousePressed`/similar.

---

### 1.3 `AbstractParentWidget<I extends IWidget, W extends AbstractParentWidget<I, W>>`

`com.cleanroommc.modularui.widget.AbstractParentWidget` — a `Widget<W>` that can hold any number of typed children.

```java
public class AbstractParentWidget<I extends IWidget, W extends AbstractParentWidget<I, W>> extends Widget<W>
```

`I` constrains the *type* of children this parent accepts (use `com.cleanroommc.modularui.widgets.VoidWidget` for
"no children"). Child mutation methods are `protected` here — subclasses (e.g. `ParentWidget`) expose them publicly,
typically alongside `IParentWidget<I, W>` for the `.child(...)` builder sugar.

| Method | Purpose | Gotcha |
|---|---|---|
| `@NotNull List<IWidget> getChildren()` | `IWidget`-erased view of the children list | modifiable despite `@UnmodifiableView`; adding requires also initialising (`child.initialise(this, true)`), removing requires disposing |
| `@NotNull List<I> getTypeChildren()` | typed view of the same list | — |
| `boolean canHover()` (override) | `true` if this widget has any visible background/hover-background/overlay/tooltip, so children can't "hover-passthrough" a widget that visually reacts to hover | — |
| `boolean canClickThrough()` / `canHoverThrough()` (override) | both `= !canHover()` | a parent with any visible chrome blocks click/hover pass-through to widgets below it |
| `boolean addChild(I child, int index)` (`protected`) | inserts at `index` (negative = from end, Python-style: `index += size()+1`); returns `false` for `null`/self/duplicate; throws `IllegalArgumentException` for a `ModularPanel` child (use `ModularScreen#openPanel` instead) or if `isChildValid(child)` returns `false`; initialises the child immediately if this parent is already valid | — |
| `boolean remove(I child)` / `remove(int index)` (`protected`) | removes + disposes (if valid) | negative index wraps like `addChild` |
| `boolean removeAll()` (`protected`) | disposes and clears all children | — |
| `boolean isChildValid(I child)` (`protected`, override point) | gate for `addChild`; default `true` | — |
| `onChildAdd(I)` / `onChildRemove(I)` (`protected`, override points) | no-op hooks | — |

Example (constructed, not from repo) — a typed parent restricted to `ButtonWidget` children:

```java
public class ButtonBar<W extends ButtonBar<W>> extends AbstractParentWidget<ButtonWidget<?>, W> {
    public W addButton(ButtonWidget<?> button) {
        addChild(button, -1);
        return getThis();
    }
}
```

### 1.4 `ParentWidget<W extends ParentWidget<W>>`

`com.cleanroommc.modularui.widget.ParentWidget` — the generic "any `IWidget` child" parent; the everyday building
block for wrapper/decoration widgets in this codebase.

```java
public class ParentWidget<W extends ParentWidget<W>> extends AbstractParentWidget<IWidget, W>
        implements IParentWidget<IWidget, W>
```

Just makes `addChild`/`remove`/`remove(int)`/`removeAll` `public` and adds the `IParentWidget` `.child(...)` /
`.child(int, ...)` / `.childIf(...)` builder sugar (`com.cleanroommc.modularui.api.widget.IParentWidget`):

| Method (from `IParentWidget<I,W>`) | Purpose |
|---|---|
| `W child(I child)` | append; throws `IllegalStateException` if `addChild` returns `false` |
| `W child(int index, I child)` | insert at index |
| `W childIf(boolean condition, Supplier<I> child)` | only builds/adds the child if `condition` is true |

Real example — `TestGuis.java:716-724` and `:725-737` use bare `new ParentWidget<>()` as decoration wrappers
(title bar, side-option bar) around other widgets — see the `decoration()` example in §0.

### 1.5 `SingleChildWidget<W extends SingleChildWidget<W>>`

`com.cleanroommc.modularui.widget.SingleChildWidget` — a `Widget<W>` holding at most one child.

```java
public class SingleChildWidget<W extends SingleChildWidget<W>> extends Widget<W>
```

| Method | Purpose |
|---|---|
| `IWidget getChild()` | current child or `null` |
| `@NotNull List<IWidget> getChildren()` (override) | singleton or empty immutable list |
| `W child(IWidget child)` | replaces the current child: disposes the old one (if any), initialises the new one immediately if this widget is already valid, calls `scheduleResize()`, then `onChildAdd(child)`. No-op if `child == this` or already the current child. |
| `onChildAdd(IWidget)` (`protected`, override point) | no-op hook |

Inferred: unlike `AbstractParentWidget.addChild`, `child(null)` is legal here and simply clears the slot (disposes
the old child, list becomes empty) — there's no null/self/duplicate guard beyond the identity checks shown.

Example (constructed, not from repo):

```java
public class Frame extends SingleChildWidget<Frame> {
    public Frame() { background(GuiTextures.FRAME); }
}
// usage
new Frame().size(80, 60).child(new Widget<>().full().overlay(IKey.str("content")));
```

### 1.6 `DelegatingWidget`

`com.cleanroommc.modularui.widget.DelegatingWidget` — an `AbstractWidget` (not `Widget<W>`!) that forwards its area,
resizer, and rendering entirely to a single wrapped `IWidget` delegate, transparently splicing the delegate into the
resize-node tree at this widget's position while making itself invisible for hit-testing.

```java
public class DelegatingWidget extends AbstractWidget implements IDelegatingWidget
```

| Method | Purpose | Gotcha |
|---|---|---|
| `DelegatingWidget(IWidget delegate)` | only constructor; installs its own `StandardResizer` initially | — |
| `setDelegate(IWidget delegate)` (`protected`) | disposes current delegate, installs the new one, re-initialises if valid | passing `null` disposes and leaves no delegate |
| `onChangeDelegate(IWidget)` (`protected`, override point) | no-op hook | — |
| `@NotNull List<IWidget> getChildren()` (override) | the delegate as a one-element `MutableSingletonList` | — |
| `void afterInit()` (override) | detaches *this* widget's own resizer from the resize-node tree (`setDefaultParent(null)`) and splices the delegate's resizer in at this widget's place (`getDelegate().resizer().relative(getParent())`, `setDefaultParentIsDelegating(true)`) | Inferred: this is why `getParentArea()` on `AbstractWidget` walks *through* `IDelegatingWidget`s — the delegate, not this wrapper, is the "real" resize-node participant |
| `void postResize()` (override) | copies the delegate's resolved `Area` onto this widget's own `Area` (so code that only knows about `DelegatingWidget` still sees correct bounds) | — |
| `@NotNull StandardResizer resizer()` / `Area getArea()` (override) | delegate's, if present, else this widget's own (pre-delegate fallback) | — |
| `void transform(IViewportStack)` (override) | translates by *this widget's own* `rx,ry` (not the delegate's) | — |
| `boolean canBeSeen(IViewportStack)` (override) | always `false` — this wrapper itself is never drawn/hit-tested directly | — |
| `requiresResize()`, `getDefaultWidth()/Height()`, `getDelegate()` | forwarded to the delegate when present | — |

Inferred: used for widgets that want to *wrap* another widget's identity (e.g. present as one logical widget while
actually being backed by another instance) without adding an extra layout box — no direct usage found in
`test/`; treat as an advanced/internal composition tool.

### 1.7 `AbstractScrollWidget<I extends IWidget, W extends AbstractScrollWidget<I, W>>`

`com.cleanroommc.modularui.widget.AbstractScrollWidget` — an `AbstractParentWidget` whose `Area` is a
`ScrollArea` (from `widget.scroll`, §2) and which implements `IViewport` + `Interactable` to scroll/clip its
children.

```java
public abstract class AbstractScrollWidget<I extends IWidget, W extends AbstractScrollWidget<I, W>>
        extends AbstractParentWidget<I, W> implements IViewport, Interactable
```

| Method | Purpose |
|---|---|
| `AbstractScrollWidget(@Nullable HorizontalScrollData x, @Nullable VerticalScrollData y)` | only constructor; either axis may be `null` to disable scrolling on that axis. Registers a `MouseReleased` gui-action so drag-scrolling stops even if the mouse leaves the widget. |
| `Area getArea()` (override) | returns the internal `ScrollArea` (which *is* an `Area` subclass) instead of a plain `Area` |
| `ScrollArea getScrollArea()` | direct access to the scroll area (padding, scrollbar state, etc.) |
| `void transformChildren(IViewportStack)` (`IViewport`) | translates children by `-scrollX, -scrollY` — this is *the* mechanism that makes scrolling visually work |
| `void getWidgetsAt(...)` (`IViewport`) | only descends into children when the hit-test point isn't inside the scrollbar's own hit area |
| `void beforeResize(boolean onOpen)` (override) | applies the panel's scrollbar theme, recomputes scrollbar-active flags, resets/reapplies the extra cross-axis `ScrollPadding` needed when a scrollbar occupies space |
| `checkScrollbarActive(boolean resizeOnChange)` (`protected`) | recomputes `scrollXActive`/`scrollYActive`; if `resizeOnChange` and either flag flipped, schedules a resize (so content reflows around an appearing/disappearing scrollbar) |
| `boolean canHover()` (override) | also `true` when the mouse is over the scrollbar hit area |
| `Result onMousePressed(int)` / `boolean onMouseRelease(int)` / `boolean onMouseScroll(UpOrDown, int)` / `void onMouseDrag(int, long)` (`Interactable`) | forwarded to the internal `ScrollArea`'s click/scroll/drag handling |
| `void preDraw(context, transformed)` / `postDraw(context, transformed)` (`IViewport`) | applies/removes a `Stencil` clip around the scroll area; draws the scrollbar + optional edge shadows in `postDraw` |
| `int getScrollX()` / `getScrollY()` | current scroll offset in px on each axis (`0` if that axis has no `ScrollData`) |
| `boolean isShowScrollShadows()` / `W showScrollShadows(boolean)` | toggles the fade-out gradient drawn at scrolled edges |

Real example — `TestGui.java:100-103`, `com.cleanroommc.modularui.widgets.layout.Grid` (which `extends
AbstractScrollWidget<IWidget, Grid>`) made scrollable and positioned:

```java
// TestGui.java:100-103
.child(new Grid()
        .grid(availableMatrix)
        .scrollable()
        .pos(7, 7).right(16).bottom(7).name("available list"));
```

### 1.8 `ScrollWidget<W extends ScrollWidget<W>>`

`com.cleanroommc.modularui.widget.ScrollWidget` — the concrete, general-purpose `AbstractScrollWidget` for
"any children" scroll containers.

```java
public class ScrollWidget<W extends ScrollWidget<W>> extends AbstractScrollWidget<IWidget, W>
        implements IParentWidget<IWidget, W>
```

| Constructor | Scrolling |
|---|---|
| `ScrollWidget()` | none on either axis (`super(null, null)`) — you'd still need to enable scrolling via other means/subclass |
| `ScrollWidget(VerticalScrollData data)` | vertical only |
| `ScrollWidget(HorizontalScrollData data)` | horizontal only |

`addChild(IWidget, int)` is exposed `public` (delegates to `super`); gets `.child(...)` sugar from `IParentWidget`.

Example (constructed, not from repo):

```java
new ScrollWidget<>(new VerticalScrollData())
        .size(120, 100)
        .child(Flow.column()
                .widthRel(1f)
                .children(50, i -> IKey.str("Row " + i).asWidget().widthRel(1f).height(12)));
```

### 1.9 `DraggableWidget<W extends DraggableWidget<W>>`

`com.cleanroommc.modularui.widget.DraggableWidget` — a `Widget<W>` that can be picked up and moved by the cursor.

```java
public class DraggableWidget<W extends DraggableWidget<W>> extends Widget<W> implements IDraggable, IViewport
```

| Method | Purpose |
|---|---|
| `DraggableWidget()` | snapshots `getArea().createCopy()` into `movingArea` |
| `void drawMovingState(context, partialTicks)` | while being dragged, redraws this widget's whole subtree at the drag position via `WidgetTree.drawTree(this, context, true, true)` |
| `boolean onDragStart(int mouseButton)` | only accepts button `0`; captures the click offset relative to the widget's current absolute position |
| `void onDragEnd(boolean successful)` | on success, commits the drop position via `resizer().top(...).left(...)` (absolute pixel `top`/`left`, overwriting any previous relative config — see the §0 "3rd unit overwrites" gotcha, this call intentionally forces `start`-based positioning) and `scheduleResize()` |
| `void onDrag(int mouseButton, long timeSinceLastClick)` | updates the live `movingArea` position for rendering while dragging |
| `@Nullable Area getMovingArea()` | the area to draw the widget at while mid-drag |
| `isMoving()` / `setMoving(boolean)` | dragging state; `setMoving` also disables the widget while moving (`setEnabled(!moving)`) |
| `getSelfAt(...)` / `getWidgetsAt(...)` (`IViewport`) | hit-testing is suppressed entirely while `isMoving()` |
| `void transform(IViewportStack)` (override) | while moving, cancels the normal `rx,ry` translation and instead translates to `movingArea`'s live position |

Comment in source: "Might not work as expected when a parent is scaling or rotating itself."

Real example — `TestGuis.java:290-306` (`buildSpriteAndEntityUI`), a draggable icon with a long tooltip:

```java
// TestGuis.java:290-306
.child(new DraggableWidget<>()
        .size(20)
        .horizontalCenter()
        .top(20)
        .tooltipBuilder(tooltip -> {
            tooltip.addLine("Lorem ipsum ...");
            tooltip.addLine("Longer Line 2");
            tooltip.addLine("Line 3");
            tooltip.alignment(Alignment.Center);
            tooltip.scale(0.5f);
            tooltip.pos(RichTooltip.Pos.NEXT_TO_MOUSE);
        }))
```

### 1.10 `DragHandle`

`com.cleanroommc.modularui.widget.DragHandle` — a `Widget<DragHandle>` that forwards all `IDraggable` behavior to
the nearest draggable ancestor (another `IDraggable` widget, or the owning `ModularPanel` if it `isDraggable()`),
letting a small "handle" child drag its bigger container/panel.

```java
public class DragHandle extends Widget<DragHandle> implements IDraggable, IViewport
```

| Method | Purpose |
|---|---|
| `void onInit()` (override) | walks up parents until a `ModularPanel` is hit; if an `IDraggable` is found first, forwards to it; otherwise, if the panel itself `isDraggable()`, wraps it in a `DraggablePanelWrapper` |
| `drawMovingState/onDragStart/onDragEnd/onDrag/canDropHere/getMovingArea/isMoving/setMoving` | all forwarded to `parentDraggable`, no-op/`false`/`Area.SHARED` if none was found |
| `void transform(IViewportStack)` | just `super.transform(stack)` — no special-case behavior (unlike `DraggableWidget`) |

Inferred: no direct usage in `test/`; typical use is a dedicated "title bar" strip child inside a movable dialog
that should be the only part of the dialog you can grab to drag it, while the rest of the dialog ignores drag
input.

Example (constructed, not from repo):

```java
new ParentWidget<>().widthRel(1f).height(14).background(GuiTextures.TITLE_BAR)
        .child(new DragHandle().full()); // whole title bar area is grabbable
```

### 1.11 `EmptyWidget`

`com.cleanroommc.modularui.widget.EmptyWidget` — a bare-bones, non-`Widget<W>` `IWidget` implementation with no
rendering, no hover, and no children; exists purely as a placeholder resize-node/area holder.

```java
public class EmptyWidget implements IWidget
```

All draw methods are no-ops; `canBeSeen(...)` and `canHover()` are always `false`, `canHoverThrough()` is always
`true` (pure pass-through). Owns its own `Area`/`StandardResizer` pair like `AbstractWidget` does, but implements
`IWidget` directly rather than extending `AbstractWidget`.

Inferred: intended as a lightweight filler/spacer widget or a placeholder slot in generic code paths that need
*some* `IWidget` instance but nothing visual — not used directly in `test/`.

Example (constructed, not from repo):

```java
Flow.row().child(new EmptyWidget()).width(20); // fixed-width invisible spacer... (constructed illustration)
```

### 1.12 `InternalWidgetTree` (package-private, `@ApiStatus.Internal`)

`com.cleanroommc.modularui.widget.InternalWidgetTree` — implementation details behind `WidgetTree`'s public API:
drawing traversal (`drawTree`, `drawBackground`, `drawTreeForeground`) and the multi-pass `resize(...)` algorithm
described in §0.3. Not part of the public API surface; documented here only because `WidgetTree.resizeInternal`
and the resize algorithm's behavior (order-independence, failure mode) are directly relevant to understanding
sizing. See §0.3 for the externally-relevant behavior.

### 1.13 `WidgetTree`

`com.cleanroommc.modularui.widget.WidgetTree extends TreeUtil` — public static utility for traversing, drawing,
resizing, and locating widgets in a widget tree. All methods are `static`; the class itself cannot be instantiated
(`private` constructor).

```java
public class WidgetTree extends TreeUtil
```

| Method | Purpose |
|---|---|
| `logResizeTime` (public static field) | when `true`, logs the nanosecond duration of each resize pass — dev/debug only |
| `findFirstWithNameNullable(IWidget parent, String name)` / `findFirstWithName(...)` (throws `NoSuchElementException`) | depth-first name search (2 typed overloads with `Class<T> type` too, which additionally throw `ClassCastException` on a type mismatch) |
| `findChildAtNullable(IWidget parent, String... path)` / `findChildAt(...)` | path-based lookup — each path segment is a widget name, must match in order down the tree (typed overloads throw `ClassCastException` if the leaf's type doesn't match) |
| `hasSyncedValues(ModularPanel)` | true if every `ISynced` widget in the tree has a sync handler |
| `collectSyncValues(PanelSyncManager, ModularPanel[, boolean includePanel])` (`@ApiStatus.Internal`) | auto-registers unregistered `ISynced` widgets' handlers under an auto-generated key prefix |
| `countUnregisteredSyncHandlers(PanelSyncManager, IWidget)` | diagnostic count |
| `drawTree(IWidget, ModularGuiContext)` / `drawTree(..., boolean ignoreEnabled, boolean drawBackground)` | full recursive draw (background → draw → overlay, per widget, with viewport push/pop) |
| `drawTreeForeground(IWidget, ModularGuiContext)` | second pass for tooltips/foreground-only elements |
| `onUpdate(IWidget)` (`@ApiStatus.Internal`) | BFS tick dispatch |
| `resize(IWidget)` | **deprecated** — just calls `parent.scheduleResize()` |
| `resizeInternal(ResizeNode, boolean onOpen)` (`@ApiStatus.Internal`) | walks up to the nearest ancestor that `dependsOnChildren()`/`isLayout()` before resizing (so a leaf edit re-resizes the right subtree root), runs the algorithm from §0.3, then `preApplyPos`/`applyPos`/`postFullResize`; on failure, chat-warns the player and logs the ResizeNode tree via `print(...)` |
| `preApplyPos(ResizeNode)` / `applyPos(ResizeNode)` / `postFullResize(ResizeNode)` | recursive tree walks over the *resize-node* tree (not the widget tree) applying margin/padding, converting relative→absolute position, and firing `postResize()` |
| `verifyTree(ResizeNode, Set<ResizeNode>)` | cycle detector; throws `IllegalStateException` on a cycle |
| `interface WidgetInfo extends NodeInfo<IWidget>` | plug-in for `TreeUtil`'s debug tree-printing; `WIDGET_INFO_AREA`/`WIDGET_INFO_ENABLED`/`WIDGET_INFO_WIDGET_THEME` constants supply ready-made columns (area xywh/rx,ry, enabled state, resolved theme name) |

Example (constructed, not from repo) — locating a named widget after building a panel:

```java
IWidget title = WidgetTree.findFirstWithName(panel, "title");
```

---

## 2. `com.cleanroommc.modularui.widget.scroll`

Backs `AbstractScrollWidget`/`ScrollWidget`/`Grid`/`ListWidget`. Each axis of scrolling is described by a
`ScrollData` subclass instance (`HorizontalScrollData`/`VerticalScrollData`), and the widget's `Area` is replaced
with a `ScrollArea` that tracks both axes plus scrollbar geometry.

### 2.1 `ScrollData` (abstract)

`com.cleanroommc.modularui.widget.scroll.ScrollData` — shared scroll state/behavior for one axis: current scroll
offset, content size, drag/click handling, scrollbar geometry and animation.

```java
public abstract class ScrollData
```

| Static factory | Result |
|---|---|
| `ScrollData.of(GuiAxis axis)` | scrollbar 4px thick, at the axis's "end" cross-position (bottom for X, right for Y) |
| `ScrollData.of(GuiAxis axis, boolean axisStart)` | as above, but scrollbar at the "start" (top/left) if `axisStart` |
| `ScrollData.of(GuiAxis axis, boolean axisStart, int thickness)` | explicit scrollbar thickness in px (`DEFAULT_THICKNESS = -1` = use the widget theme's fallback thickness) |

| Method | Purpose |
|---|---|
| `getAxis()` / `isOnAxisStart()` / `isVertical()` / `isHorizontal()` | trivial axis/placement getters |
| `getThickness()` | configured thickness, or the theme-derived `fallbackThickness` if unset |
| `getScrollSpeed()` / `setScrollSpeed(int)` | pixels scrolled per wheel notch (default `30`) |
| `getScrollSize()` / `setScrollSize(int)` | total content extent on this axis — **callers must keep this in sync with actual content size** (e.g. `ListWidget` sets it from its computed content height) |
| `getScroll()` | current scroll offset in px |
| `isDragging()` | scrollbar thumb is being dragged |
| `getMinLength()` | `getThickness() + 1` — scrollbar thumb is always longer than it is thick |
| `isCancelScrollEdge()` / `setCancelScrollEdge(boolean)` | if `true` (default), wheel-scroll input is still "consumed" (blocking pass-through to a scroll view underneath) even after this view hit an edge and stopped moving |
| `getFullVisibleSize(ScrollArea)` / `(ScrollArea, boolean isOtherActive)` | visible extent on this axis, minus the other axis's scrollbar thickness if that one is/would-be active |
| `getVisibleSize(ScrollArea)` / overloads | `getFullVisibleSize(...)` minus this axis's own padding |
| `getProgress(area, mainAxisPos, crossAxisPos)` | thumb-drag progress fraction |
| `abstract getOtherScrollData(ScrollArea)` | the perpendicular axis's `ScrollData`, if any (used for corner/thickness interplay) |
| `clamp(ScrollArea)` | clamps `scroll` into `[0, scrollSize - visibleSize]`; returns `true` if it had to change |
| `scrollBy(ScrollArea, int x)` / `scrollTo(ScrollArea, int x)` | relative/absolute scroll, both auto-clamp |
| `animateTo(ScrollArea, int x)` | animates to a target offset over 500ms (quad-out), stopping early if an edge is hit mid-animation |
| `isScrollBarActive(ScrollArea)` / `(ScrollArea, boolean isOtherActive)` | `true` only if content overflows the visible area (accounting for the other axis's scrollbar footprint) |
| `isOtherScrollBarActive(ScrollArea, boolean isSelfActive)` | convenience for the perpendicular axis |
| `getScrollBarLength(ScrollArea)` | thumb pixel length, proportional to visible/content ratio, floored at `getMinLength()` |
| `abstract isInsideScrollbarArea(ScrollArea, int x, int y)` | hit-test for the scrollbar track |
| `isAnimating()` / `getAnimationDirection()` / `getAnimatingTo()` | animation state introspection |
| `getScrollBarStart(...)` (2 overloads) | thumb's pixel start position along the track |
| `texture(IDrawable)` | overrides the scrollbar drawable (falls back to the widget theme's background, then a generic default `Scrollbar.DEFAULT`) |
| `abstract drawScrollbar(...)` / `abstract drawScrollShadow(...)` | rendering hooks, axis-specific in the two subclasses |
| `onMouseClicked(area, mainAxisPos, crossAxisPos, button)` | begins a drag if the click landed on the scrollbar track; computes whether the click was inside the thumb itself (drag offset relative to thumb) or outside it (assumes center-of-thumb offset, i.e. "jump to here") |

### 2.2 `HorizontalScrollData` / `VerticalScrollData`

`com.cleanroommc.modularui.widget.scroll.HorizontalScrollData` / `VerticalScrollData` — the two concrete
`ScrollData` axes.

```java
public class HorizontalScrollData extends ScrollData   // axis = GuiAxis.X
public class VerticalScrollData extends ScrollData     // axis = GuiAxis.Y
```

| Constructor | Scrollbar placement / thickness |
|---|---|
| `HorizontalScrollData()` | bottom, 4px |
| `HorizontalScrollData(boolean topAlignment)` | top if `true`, else bottom; 4px |
| `HorizontalScrollData(boolean topAlignment, int thickness)` | explicit thickness |
| `VerticalScrollData()` | right, 4px |
| `VerticalScrollData(boolean leftAlignment)` | left if `true`, else right; 4px |
| `VerticalScrollData(boolean leftAlignment, int thickness)` | explicit thickness |

Both add `cancelScrollEdge(boolean)` (fluent wrapper over `setCancelScrollEdge`, returns `this` for chaining) and
implement `getFallbackThickness(WidgetTheme)` (horizontal → theme's default *height*; vertical → theme's default
*width* — i.e. the scrollbar's thickness defaults to the widget's other-axis default size), `getOtherScrollData`,
`isInsideScrollbarArea`, `drawScrollbar`, `drawScrollShadow` with axis-appropriate geometry.

Real usage: `ListWidget` defaults to `scrollDirection(new VerticalScrollData())`
(`src/main/java/com/cleanroommc/modularui/widgets/ListWidget.java:55`); `Grid.scrollable()`
(`src/main/java/com/cleanroommc/modularui/widgets/layout/Grid.java:430-431`) wires up both:
`scrollable(new VerticalScrollData(), new HorizontalScrollData())`.

### 2.3 `ScrollPadding`

`com.cleanroommc.modularui.widget.scroll.ScrollPadding extends Box` — a `Box` (see §3.2) that adds a *second*,
independent padding layer specifically reserved for scrollbar space, layered on top of the normal padding.

```java
public class ScrollPadding extends Box
```

`getLeft()/getRight()/getTop()/getBottom()` (overrides) return `super.<edge>() + scrollPadding<Edge>`, so any code
reading padding through the normal `Box` API (e.g. `DimensionSizer.calcSize`) automatically also accounts for
reserved scrollbar space once it's applied. `vertical()`/`horizontal()` similarly add the scroll padding component.

| Method | Purpose |
|---|---|
| `scrollPaddingAll(int all)` / `(int h,int v)` / `(int l,int r,int t,int b)` | set the extra scrollbar-reserved padding on all/paired/individual edges |
| `scrollPaddingLeft/Top/Right/Bottom(int)` | single-edge setters |
| `scrollPadding(GuiAxis axis, boolean start, int val)` | axis+side setter, mirrors `Box.set(...)` |
| `setScrollPadding(ScrollPadding box)` | copies both normal (`Box`) and scroll padding from another instance |
| `getScrollPaddingLeft/Top/Right/Bottom()` | raw scroll-padding-only getters (excludes normal padding) |
| `verticalScrollPadding()` / `horizontalScrollPadding()` | sum of the scroll-padding-only component per axis |
| `getTotalScrollPadding(GuiAxis)` | axis-dispatching version of the above two |

Inferred: `AbstractScrollWidget.beforeResize`/`applyAdditionalOffset` call `scrollPaddingAll(0)` then
`scrollPadding(axis.getOther(), start, thickness)` per active scrollbar every resize pass — this is what reserves
room so content doesn't render underneath an active scrollbar.

### 2.4 `ScrollArea`

`com.cleanroommc.modularui.widget.scroll.ScrollArea extends Area` — an `Area` (§3.1) augmented with up to one
`HorizontalScrollData` and one `VerticalScrollData`, their combined hit-testing/dragging logic, and scrollbar
rendering dispatch. This *is* what `AbstractScrollWidget.getArea()` returns.

```java
public class ScrollArea extends Area
```

| Method | Purpose |
|---|---|
| `ScrollArea()` / `ScrollArea(int x,int y,int w,int h)` | constructors, same shape as `Area`'s |
| `getPadding()` (override) | returns the `ScrollPadding` (§2.3) instead of a plain `Box` |
| `getScrollPadding()` | typed accessor for the same object |
| `setScrollData(ScrollData)` | installs into `scrollX` or `scrollY` based on the runtime type |
| `removeScrollData()` | clears both axes |
| `setScrollDataX(HorizontalScrollData)` / `setScrollDataY(VerticalScrollData)` | axis-specific setters |
| `getScrollX()` / `getScrollY()` / `getScrollData(GuiAxis)` | accessors, `null` if that axis has no scrolling |
| `mouseClicked(GuiContext)` / `mouseClicked(int x,int y)` | starts a scrollbar drag if the click hit either axis's track |
| `mouseScroll(GuiContext)` / `mouseScroll(int x,int y,int scroll,boolean shift)` | routes wheel input to whichever axis is active; if both axes are active, `shift` picks the vertical one, otherwise horizontal is preferred; animates toward the new target and returns whether the event should be considered "consumed" |
| `mouseReleased(GuiContext)` / `mouseReleased(int x,int y)` | clears drag state on both axes |
| `drag(GuiContext)` / `drag(int x,int y)` | updates scroll position for whichever axis is currently mid-drag, based on thumb-drag progress |
| `isInsideScrollbarArea(int x,int y)` | combined hit-test (both axes) |
| `isScrollBarXActive()` / `isScrollBarYActive()` | per-axis active flags |
| `getScrollBarBackgroundColor()` / `setScrollBarBackgroundColor(int)` | track background color (default: black @ 25% alpha) |
| `isDragging()` | either axis dragging |
| `applyWidgetTheme(WidgetTheme)` | pushes fallback-thickness resolution into both axes |
| `drawScrollbar(ModularGuiContext, WidgetTheme, IDrawable texture)` / `drawScrollShadow(ModularGuiContext)` | draws both active scrollbars / edge shadows, X axis first so Y can react to X's occupied corner space |

Example (constructed, not from repo) — manually building a scroll area (normally you'd just use
`AbstractScrollWidget`/`ScrollWidget`, which own one of these already):

```java
ScrollArea area = new ScrollArea();
area.setScrollDataY(new VerticalScrollData());
area.getScrollY().setScrollSize(500); // total content height in px
```

---

## 3. `com.cleanroommc.modularui.widget.sizer`

### 3.1 `Area`

`com.cleanroommc.modularui.widget.sizer.Area extends java.awt.Rectangle implements IAnimatable<Area>` — position +
size + parent-relative position + z-layer + margin/padding for one widget. See §0 for the conceptual role.

```java
public class Area extends Rectangle implements IAnimatable<Area>
```

| Field/constant | Meaning |
|---|---|
| `int rx, ry` (public) | position relative to the reference node (normally the parent), **before** `applyPos` converts it to absolute `x,y` |
| `Area.SHARED` / `Area.ZERO` (static) | scratch/sentinel instances — do not hold onto references across calls |

Trivial paired getter/setters (all public): `x()/x(int)`, `y()/y(int)`, `w()/w(int)`, `h()/h(int)`,
`ex()/ex(int)` (right edge, `ex(v)` keeps width fixed and moves `x`), `ey()/ey(int)` (bottom edge, same pattern),
`mx()`/`my()` (center point), `z()/z(int)` (layer).

| Method | Purpose |
|---|---|
| `int x(float anchor)` / `int y(float anchor)` | point at a given fraction across this area's width/height |
| `getPoint(GuiAxis)` / `getEndPoint(GuiAxis)` / `getSize(GuiAxis)` / `getRelativePoint(GuiAxis)` | axis-dispatching accessors (`x`/`y`, `x+w`/`y+h`, `w`/`h`, `rx`/`ry`) |
| `setPoint/setSize/setRelativePoint/addPoint/addSize/addRelativePoint(GuiAxis, int)` | axis-dispatching mutators |
| `void applyPos(int parentX, int parentY)` (package-private) | `x = parentX + rx; y = parentY + ry` — the relative→absolute conversion step used by `WidgetTree.applyPos` |
| `requestedWidth()/Height()` | `width/height + margin.horizontal()/vertical()` — what a covering parent should reserve for this widget |
| `paddedWidth()/Height()` | `width/height - padding.horizontal()/vertical()` — inner content box |
| `requestedSize(GuiAxis)` / `paddedSize(GuiAxis)` | axis-dispatching versions |
| `relativeEndX()/Y()` | `rx+width` / `ry+height` |
| `isInside(int x,int y)` | point-in-rect on **absolute** coords; prefer `IWidget.isInside(IViewportStack,...)` for hit-testing since that accounts for transforms |
| `intersects(Rectangle2D)` | AABB intersection test |
| `clamp(Area area)` | clamps `area`'s corners to lie within this area, mutating `area` |
| `expand(int)` / `expand(int x,int y)` / `expandX(int)` / `expandY(int)` | grows/shrinks symmetric about the center (`offset(-n)` + `grow(2n)`) |
| `offset(int)` / `offset(int,int)` / `offsetX(int)` / `offsetY(int)` | moves position only |
| `grow(int)` / `grow(int,int)` / `growW(int)` / `growH(int)` | changes size only, position fixed |
| `set(x,y,w,h)` / `setPos(x,y)` / `setSize(w,h)` / `setPos(Rectangle)` / `setSize(Rectangle)` / `set(Rectangle)` | bulk setters |
| `setPos(sx,sy,ex,ey)` | sets from two opposite corners (auto-normalizes if reversed) |
| `reset()` | zeroes `x,y,width,height` (not `rx,ry`/margin/padding) |
| `transformAndRectanglerize(IViewportStack)` | transforms all four corners through the stack (so a rotated area becomes axis-aligned again) and takes the bounding box — used for scissor/stencil regions under rotation |
| `getMargin()` / `getPadding()` | the two `Box` instances |
| `createCopy()` | deep-ish copy (`new Area(this)`, which also copies `rx,ry,z,margin,padding`) |
| `interpolate(Area start, Area end, float t)` / `shouldAnimate(Area)` / `copyOrImmutable()` (`IAnimatable<Area>`) | drives animated area transitions (e.g. panel open/close, drag-resize) |

Example (constructed, not from repo):

```java
Area a = widget.getArea();
int right = a.ex();          // absolute right edge
int contentWidth = a.paddedWidth(); // width available to children after padding
```

### 3.2 `Box`

`com.cleanroommc.modularui.widget.sizer.Box implements IAnimatable<Box>` — a four-edge `left/top/right/bottom` int
box, used for both `Area.margin` and `Area.padding` (and subclassed by `ScrollPadding`, §2.3).

```java
public class Box implements IAnimatable<Box>
```

`Box.SHARED` / `Box.ZERO` / `Box.ONE` (= all edges `1`) are static scratch/sentinel instances.

| Method | Purpose |
|---|---|
| `all(int all)` / `all(int h,int v)` / `all(int l,int r,int t,int b)` | bulk setters, fluent (`return this`) |
| `left/top/right/bottom(int)` | single-edge fluent setters |
| `set(GuiAxis axis, boolean start, int val)` | axis+side setter |
| `set(Box)` | copy from another `Box` |
| `getLeft/Right/Top/Bottom()` | edge getters (overridden by `ScrollPadding` to add its extra layer) |
| `vertical()` / `horizontal()` | `top+bottom` / `left+right` |
| `getTotal(GuiAxis)` | axis-dispatching version of the above two |
| `getStart(GuiAxis)` / `getEnd(GuiAxis)` | `left`/`top` or `right`/`bottom` by axis |
| `fromJson(JsonObject)` / `toJson(JsonObject)` | theme/config (de)serialization (`margin`, `marginHorizontal`, `marginVertical`, `marginTop/Bottom/Left/Right` keys) |
| `interpolate/copyOrImmutable` (`IAnimatable<Box>`) | animation support |
| `isEqual(Box)` / `equals`/`hashCode` | value equality |

### 3.3 `Unit` (`@ApiStatus.Internal`)

`com.cleanroommc.modularui.widget.sizer.Unit` — one start/end/size coordinate value. Fully described in §0
("Unit — one edge or size value"). Public surface not already covered there:

| Member | Purpose |
|---|---|
| `Unit.State` enum: `UNUSED`, `START`, `END`, `SIZE` | which role this unit currently plays; drives debug label text (`xText`/`yText`, e.g. `"LEFT"`/`"TOP"`) |
| `Unit.Measure` enum: `PIXEL`, `RELATIVE` | literal px vs fraction of reference size |
| `reset()` | clears back to `UNUSED`/defaults |
| `setFrom(Unit other)` | copies all fields from another `Unit` (used by `DimensionSizer.setUnit`, e.g. theme JSON application) |
| `setValue(float)` / `setValue(DoubleSupplier)` | mutually exclusive value source |
| `getValue()` | resolves the supplier if present, else the literal float |
| `getAnchor()` | see §0 (auto-anchor shortcut) |
| `isAutoAnchor()` / `setAutoAnchor(boolean)` | — |
| `getOffset()` / `getAbsOffset()` / `setOffset(int)` | flat pixel adjustment |
| `getMeasure()` / `setMeasure(Measure)` | — |
| `setAnchor(float)` | explicit anchor override |
| `isCloseToZero()` | used by `DimensionSizer.isFullSize()`'s start+end branch — `RELATIVE`: value `≤0.01` and offset `<5`; else: `|value+offset| < 5` |
| `isRelative()` / `isUnused()` | trivial predicates |

### 3.4 `ResizeNode` (abstract)

`com.cleanroommc.modularui.widget.sizer.ResizeNode implements IResizeable, ITreeNode<ResizeNode>` — the base of the
*resize-node tree*, a parallel tree to the widget tree (nodes: one per widget's `StandardResizer`, plus root nodes
like `ScreenResizeNode`/`AreaResizer` that aren't backed by a widget at all). Note this tree's shape can *diverge*
from the widget tree — e.g. `relative(...)`/`relativeToScreen()` (§0) reparent a node without moving the widget,
and `DelegatingWidget` removes itself and re-parents its delegate in its place.

```java
public abstract class ResizeNode implements IResizeable, ITreeNode<ResizeNode>
```

| Method | Purpose |
|---|---|
| `getChildren()` (`@ApiStatus.Internal`) | resize-tree children |
| `getParent()` | `parentOverride` if set (via `relative(...)`), else `defaultParent` (the widget-tree parent's node) |
| `replacementOf(ResizeNode node)` (`@ApiStatus.Internal`) | swaps this node into `node`'s exact tree position (parent + child index), taking over its children, then disposes `node` — used when a widget's resizer is replaced (`AbstractWidget.resizer(StandardResizer)`) |
| `dispose()` | detaches from parent, clears children |
| `initialize(ResizeNode defaultParent, ResizeNode root)` (`@ApiStatus.Internal`) | sets `defaultParent`; `StandardResizer` overrides to also apply a pending `relativeToScreen()` |
| `setDefaultParent(ResizeNode)` / `setParentOverride(ResizeNode)` (`protected`) | tree-splicing internals; both throw `IllegalArgumentException` on a self-parent attempt |
| `setDefaultParentIsDelegating(boolean)` | marks that `initResizing`/`onResized`/`postFullResize` should also propagate to `defaultParent` even while a `parentOverride` is active (the `DelegatingWidget` mechanism) |
| `hasParentOverride()` | true if `relative(...)`/`relativeToScreen()` is in effect |
| `initResizing(boolean onOpen)` / `reset()` / `markDirty()` / `onResized()` / `postFullResize()` / `requiresResize()` | resize-pass lifecycle hooks, see §0.3 |
| `widthDependsOnParent()/heightDependsOnParent()/xDependsOnParent()/yDependsOnParent()` (default `false`, overridden by `StandardResizer`) | drive the multi-pass ordering |
| `dependsOnParentX/Y/()`, `dependsOnParent(GuiAxis)` | OR-combinations of the above |
| `dependsOnChildrenX/Y/()`, `dependsOnChildren(GuiAxis)` (default `false`, overridden by `StandardResizer`) | drives `coverChildren` scheduling |
| `isSameResizer(ResizeNode)` | identity check |
| `isLayout()` / `layoutChildren()` / `postLayoutChildren()` | `ILayoutWidget` integration (e.g. `Flow`, `Grid` — out of scope here); default: not a layout, no-op success |
| `checkExpanded(@Nullable GuiAxis)` (`@ApiStatus.Internal`) | propagates `expanded()` flag per-axis before each resize; no-op by default |
| `isDecoration()` | default `false`, overridden by `StandardResizer.decoration(...)` |
| abstract `hasYPos/hasXPos/hasHeight/hasWidth/hasStartPos(axis)/hasEndPos(axis)` | subclass-specific unit-presence queries |
| `hasPos(GuiAxis)` / `hasSize(GuiAxis)` | axis-dispatching wrappers of the above |
| abstract `isFullSize()` / `hasFixedSize()` / `getDebugDisplayName()` / `toString()` | — |

### 3.5 `WidgetResizeNode` (abstract)

`com.cleanroommc.modularui.widget.sizer.WidgetResizeNode extends ResizeNode` — binds a `ResizeNode` to a specific
`IWidget`, forwarding the widget lifecycle hooks. Direct superclass of `StandardResizer`.

```java
public abstract class WidgetResizeNode extends ResizeNode
```

| Method | Purpose |
|---|---|
| `WidgetResizeNode(IWidget widget)` | requires non-null widget |
| `IWidget getWidget()` | the bound widget |
| `Area getArea()` (override) | `widget.getArea()` |
| `initResizing(boolean onOpen)` (override) | also calls `widget.beforeResize(onOpen)` |
| `onResized()` (override) | also calls `widget.onResized()` |
| `postFullResize()` (override) | also calls `widget.postResize()` |
| `isLayout()` | `widget instanceof ILayoutWidget` |
| `layoutChildren()` / `postLayoutChildren()` | forwards to `ILayoutWidget.layoutWidgets()`/`postLayoutWidgets()` if applicable, else `true` |
| `getDebugDisplayName()` / `toString()` | includes the widget and its screen |

### 3.6 `StandardResizer`

`com.cleanroommc.modularui.widget.sizer.StandardResizer extends WidgetResizeNode implements IPositioned<StandardResizer>`
— the concrete per-widget resizer; the actual X/Y solving engine. Fully described in §0 and §1.2.1 (every
`Widget.IPositioned` method is a thin wrapper here). This section covers members not already detailed above.

```java
public class StandardResizer extends WidgetResizeNode implements IPositioned<StandardResizer>
```

| Method | Purpose |
|---|---|
| `StandardResizer(IWidget widget)` | only public constructor; creates its own X/Y `DimensionSizer` via `createDimensionSizer(GuiAxis)` (`protected`, overridable — see `ExpanderResizer`) |
| `initialize(ResizeNode defaultParent, ResizeNode root)` (override) | also applies a pending `relativeToScreen()` by overriding the parent to `root` |
| `initResizing(boolean onOpen)` (override) | resets per-pass calculated flags; on `onOpen`, runs `detectConflictingConfiguration()` (dev-env only warnings, e.g. `expanded()` without an `IExpander` parent, or `expanded()` + `coverChildren()` together) |
| `reset()` / `resetPosition()` | delegate to both `DimensionSizer`s |
| `resize(boolean isParentLayout)` | the core per-axis solve step: `x.apply(...)`/`y.apply(...)` against `getParent()`, using `getWidget().getDefaultWidth()/getDefaultHeight()` as the size fallback |
| `postResize()` | runs `coverChildren` resolution (see §0) if either axis depends on children |
| `preApplyPos()` | applies margin/padding to the relative position (`applyMarginAndPaddingToPos`), then converts to absolute (`area.applyPos(...)`) |
| `applyPos()` | recomputes `rx,ry` relative to the **widget-tree** parent's area (which may differ from the resize-node parent used for percentages, e.g. after `relative(...)`); also special-cases `IDelegatingWidget` (skips, since the delegate already applied) and `IVanillaSlot` (mirrors position into the Minecraft `Slot.xDisplayPosition/yDisplayPosition`, offset by 1px for the 18×18-vs-16×16 vanilla slot convention) |
| `expanded()` (no-arg) | sets the flag and schedules a resize; see §0 |
| `isExpanded()` / `hasFixedSize()` / `isFullSize()` | AND-combinations of the two `DimensionSizer`s |
| `relative(ResizeNode)` / `relativeToParent()` / `relativeToScreen()` / `coverChildrenWidth(int)` / `coverChildrenHeight(int)` / `decoration(boolean)` | fluent config, all described in §0 |
| `left/right/top/bottom(float/DoubleSupplier, int offset, float anchor, Unit.Measure, boolean autoAnchor)` (`@ApiStatus.Internal`) | the low-level unit setters that every `IPositioned.left(...)`/`right(...)`/etc. overload on `Widget` ultimately calls |
| `width/height(float/DoubleSupplier, int offset, Unit.Measure)` (`@ApiStatus.Internal`) | ditto for size |
| `anchorLeft/Right/Top/Bottom(float)` | see §0; note `anchorRight`/`anchorBottom` store `1 - val` |
| `anchor(Alignment)` *(deprecated, 3.3.0)* | legacy combined anchor setter |
| `setUnit(Unit, GuiAxis, Unit.State)` | low-level unit injection (theme/JSON application) |

### 3.7 `ScreenResizeNode`

`com.cleanroommc.modularui.widget.sizer.ScreenResizeNode extends StaticResizer` — the resize-tree root representing
the whole screen; what `relativeToScreen()` (§0) ultimately anchors to.

```java
public class ScreenResizeNode extends StaticResizer
```

| Method | Purpose |
|---|---|
| `ScreenResizeNode(ModularScreen screen)` | binds to a screen |
| `getScreen()` | the bound screen |
| `Area getArea()` (override) | `screen.getScreenArea()` |
| `getDebugDisplayName()` / `toString()` | screen-qualified debug text |

### 3.8 `AreaResizer`

`com.cleanroommc.modularui.widget.sizer.AreaResizer extends StaticResizer` — a resize-node wrapping a bare `Area`
(not tied to any widget); used by the deprecated `IPositioned.relative(Area)` overload (§1.2.1) to let a widget
position itself relative to an arbitrary rectangle instead of another widget/screen.

```java
public class AreaResizer extends StaticResizer
```

`AreaResizer(Area area)`; `getArea()` returns that fixed `Area` (never recalculated by this node itself — it's a
read-only anchor for other nodes' percentage math).

### 3.9 `StaticResizer` (abstract)

`com.cleanroommc.modularui.widget.sizer.StaticResizer extends ResizeNode` — a `ResizeNode` whose own position/size
never needs solving (it's already fixed from outside, e.g. the screen's real pixel dimensions, or a fixed `Area`
snapshot). All the "calculated" queries hard-return `true`, `resize()`/`postResize()` hard-return `true`
(nothing to do), and `hasStartPos/hasFixedSize` are `true` while `hasEndPos`/`isFullSize` are `false`. Base of
`ScreenResizeNode` and `AreaResizer`.

```java
public abstract class StaticResizer extends ResizeNode
```

Only genuinely stateful member: `areChildrenCalculated()`/`setChildrenResized(boolean)`, tracked per resize pass
(reset in `initResizing`).

### 3.10 `ExpanderResizer`

`com.cleanroommc.modularui.widget.sizer.ExpanderResizer extends StandardResizer implements IExpander` — a
`StandardResizer` variant that is *itself* an `IExpander` (i.e. this widget's own resizer can be the "flex
container" that `expanded()` children measure against), rather than requiring the parent widget class to implement
`IExpander` directly.

```java
public class ExpanderResizer extends StandardResizer implements IExpander
```

`ExpanderResizer(IWidget widget, GuiAxis axis)` — fixes which axis this node expands children along;
`getExpandAxis()` returns it. Inferred: installed via `AbstractWidget.resizer(new ExpanderResizer(this, axis))` by
a custom layout widget's constructor when it wants expand-along-axis behavior without also satisfying whatever
other contract `IExpander` implementors normally carry (e.g. `Flow` presumably implements `IExpander` itself
instead — `Flow` is out of this doc's scope, so treat this as the composable alternative).

### 3.11 `DelegatingResizer`

`com.cleanroommc.modularui.widget.sizer.DelegatingResizer extends StandardResizer` — a `StandardResizer` subclass
with no additional members; exists solely as a distinct type marker.

```java
public class DelegatingResizer extends StandardResizer {
    public DelegatingResizer(IWidget widget) { super(widget); }
}
```

Inferred: given the name and `DelegatingWidget`'s manual resize-node splicing logic (§1.6) doing the equivalent
work inline via plain `StandardResizer`, this class is likely a (currently unused-in-`test/`) alternate/legacy hook
point for delegate-aware resizing — no references found outside its own declaration in the searched sources; treat
as reserved/advanced.

### 3.12 `DimensionSizer` (`@ApiStatus.Internal`, package-private use)

`com.cleanroommc.modularui.widget.sizer.DimensionSizer` — one axis's worth of `Unit` bookkeeping and the actual
arithmetic (`calcPoint`, `calcSize`, `apply`, `postApply`). Not part of the public widget-building API (you never
construct or call one directly — it's reached only via `StandardResizer`/`IPositioned`), but its logic **is** the
sizing system, so it is described in full in §0 ("`DimensionSizer` — one axis of a widget") and referenced
throughout. Notable members not already covered:

| Method | Purpose |
|---|---|
| `setCoverChildren(int minSize, IWidget widget)` | called by `StandardResizer.coverChildrenWidth/Height`; also allocates the `size` unit slot as a side effect (`getSize(widget)`) |
| `applyMarginAndPaddingToPos(IWidget parent, Area area, Area relativeTo)` | one-time-per-pass application of this widget's margin + the parent's padding to the relative position, with edge cases to avoid double-shifting when the opposite edge/size is itself relative; throws (`GuiError.throwNew(..., SIZING, ...)`) if margin+padding+size exceeds the parent's size when both edges are fixed |
| `calcSize(Unit s, Box padding, int parentSize, boolean parentSizeCalculated)` | resolves a size `Unit`: pixel value as-is; relative value × `(parentSize - padding.getTotal(axis))`; always `+offset`. Returns a placeholder (`18`) without marking calculated if this axis `dependsOnChildren()` or is `expanded` (real value comes from `postResize`/layout instead) |
| `calcPoint(Unit p, int width, int parentSize, boolean parentSizeCalculated)` | resolves a start/end `Unit`: relative value × `parentSize`, minus `width × anchor` if an anchor is set and `width>0`, then `+offset`; if `p` is the `end` unit, mirrors the result as `parentSize - value` |
| `pointRequiresParentSize(Unit p)` | `true` for the `end` unit or any `RELATIVE` unit — i.e. whether this coordinate needs the parent's size to already be known |

---

## Coverage notes / ambiguities

- **`onMousePressed`/`onMouseTapped` are not on `Widget`.** They belong to `ButtonWidget`
  (`com.cleanroommc.modularui.widgets.ButtonWidget`, outside the requested scope). Every such call in
  `TestGui.java`/`TestGuis.java` is on a `ButtonWidget<>` (or subclass) instance, never on a bare `new Widget<>()`.
  This is called out explicitly in §1.2.5 to avoid documenting a method that doesn't exist on the class in scope.
- **`DelegatingResizer`** has no distinguishing logic beyond its constructor and no confirmed call sites in the
  files read for this doc — documented as "reserved/advanced" per the `Inferred:` guidance rather than guessing at
  intended usage.
- **`DragHandle`** and **`EmptyWidget`** also have no direct usage in `test/TestGui.java`/`TestGuis.java`; their
  "Example" sections are marked "constructed, not from repo" and kept intentionally small/illustrative.
- The **`expanded()`/`IExpander`** mechanism is only half in-scope: the flag-setting and per-axis propagation live
  in `sizer/` (documented fully), but the actual "distribute remaining space" math lives in `ILayoutWidget`/`Flow`
  (`com.cleanroommc.modularui.widgets.layout`), which is out of scope. Treat the `expanded()` docs here as "what it
  configures", not "how the parent uses it".
- **Percentage semantics recap** (the part most likely to be misread): `xRel(float)` is **always** a fraction of
  the *reference node's* size on that axis (parent by default; see `relative(...)`), computed at solve time, never
  a fraction of the screen or of this widget's own size — except indirectly, in that the *anchor* fraction (for
  start/end units) is applied against *this widget's own* resolved size (`width`/`height`) to decide the pivot
  offset, per `Unit.getAnchor()`/`DimensionSizer.calcPoint`.
