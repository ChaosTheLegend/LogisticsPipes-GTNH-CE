# `drawable` package reference (core)

Packages: `com.cleanroommc.modularui.drawable`, `com.cleanroommc.modularui.drawable.graph`

Everything here is `IDrawable` (or `IIcon`, a fixed-size `IDrawable`) — the rendering primitives used as widget backgrounds/overlays, tooltip lines, and standalone icons. All classes are client-side only in practice; drawing methods run during a widget's `draw(...)` call inside a `GuiScreenEvent.DrawScreenEvent` render pass, and touch OpenGL state directly (via `Platform`/`GlStateManager`), so they must not be called outside rendering.

Two families dominate this package:
- **Texture family**: `UITexture` (base) → `AdaptableUITexture` (9-slice) / `TiledUITexture` (repeat instead of stretch), wrapped by `FallbackableUITexture` (resource-existence fallback) and composed by `TabTexture` (6-texture tab state matrix). `GuiTextures` is the central registry of `UITexture` constants shipped with the built-in `icons.png` atlas and widget textures.
- **Icon family**: `IIcon` (interface, in `api.drawable`) wraps any `IDrawable` with a fixed width/height/alignment/margin. `Icon` is the concrete implementation (`IDrawable.asIcon()` returns one). `DelegateIcon` forwards to a wrapped `IIcon`; `HoverableIcon`/`InteractableIcon` extend `DelegateIcon` to add tooltip/input capability, used by `RichText`.

`GuiDraw` is the low-level immediate-mode rendering helper (rects, gradients, circles, textures, entities, text) that nearly every drawable in this package calls into.

---

## Texture abstraction family

### `com.cleanroommc.modularui.drawable.UITexture`

A rectangular region (`u0,v0,u1,v1` in 0-1 UV space) of a PNG resource, drawable at any size by stretching. Base class of the texture family; implements `IDrawable` and `IJsonSerializable`.

```java
public class UITexture implements IDrawable, IJsonSerializable {
    public static final UITexture DEFAULT;
    public final ResourceLocation location;
    public final float u0, v0, u1, v1;
    @Nullable public final ColorType colorType;
    public final boolean nonOpaque;
}
```

#### Constructors
| Signature | Notes |
|---|---|
| `UITexture(ResourceLocation location, float u0, float v0, float u1, float v1, @Nullable ColorType colorType)` | `nonOpaque=false`, `colorOverride=0`. |
| `UITexture(..., boolean nonOpaque)` | Adds blend-mode flag. |
| `UITexture(..., boolean nonOpaque, int colorOverride)` | Full constructor. `colorOverride=0` means "no override". |

The constructor normalizes `location`: if the path doesn't start with `textures/` and/or doesn't end in `.png`, both are added automatically. So `location(ModularUI.ID, "gui/widgets/mc_button")` resolves to `textures/gui/widgets/mc_button.png`.

#### Static factories
| Method | Returns | Notes |
|---|---|---|
| `builder()` | new `Builder` | Preferred way to construct textures (see below). |
| `fullImage(ResourceLocation)` / `fullImage(String)` / `fullImage(String mod, String location)` | `UITexture` spanning the whole file, `colorType=null` | |
| `fullImage(ResourceLocation, ColorType)` / `fullImage(String, ColorType)` / `fullImage(String mod, String location, ColorType)` | same, with theme coloring | |
| `setDefaultImageSize(int w, int h)` (static) | - | Changes the fallback `imageSize` used by `Builder` and by `parseFromJson` when not specified (default 16x16). Global/static — affects all subsequent builders. |

#### Instance methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `register(String name)` | texture name | `this` | Registers with `DrawableSerialization` for JSON lookup by name. Overridden covariantly in `AdaptableUITexture`/`TiledUITexture`. |
| `getSubArea(float uStart, float vStart, float uEnd, float vEnd)` | relative UV *within this texture's existing UV range* | new `UITexture` of same runtime type | `uStart..uEnd` are 0-1 fractions of this texture's own sub-rectangle, not absolute UVs (see `lerpU`/`lerpV`). Used heavily in `GuiTextures` (e.g. `CHECK_BOX_EMPTY`, `TAB_TOP` construction). |
| `getSubArea(Area bounds)` | pixel `Area` | `UITexture` | Overload taking an `Area` (x, y, ex(), ey()). |
| `getLocation()` | - | `ResourceLocation` | Plain getter. |
| `draw(float x, float y, float width, float height)` | GUI-space coords | - | Low-level draw, no theme color applied (caller must call `applyColor` first if needed). Delegates to `GuiDraw.drawTexture`. |
| `draw(GuiContext, int x, int y, int width, int height, WidgetTheme)` | `IDrawable` override | - | Applies `colorType`'s theme color (or `ColorType.DEFAULT` if `colorType == null`) then calls `draw(x,y,w,h)`. |
| `drawSubArea(float x, float y, float width, float height, float uStart, float vStart, float uEnd, float vEnd, WidgetTheme)` | sub-rectangle draw with theme color applied | - | `drawSubArea` without theme arg is `@Deprecated` (uses `WidgetTheme.getDefault()`). |
| `canApplyTheme()` | - | `colorType != null` | |
| `applyColor(int themeColor)` | - | - | If `colorOverride != 0`, sets that color directly (ignoring `themeColor`/theme system entirely); otherwise defers to `IDrawable.applyColor`. |
| `withColorOverride(int color)` | ARGB color | copy of this texture with `colorOverride` set | Returns a **new instance** (via `copy()`), does not mutate `this`. Covariant override in subclasses. |
| `parseFromJson(JsonObject)` (static) | - | `UITexture` | Deserializes either a named-texture reference (`"name"`/`"id"`) or a full inline spec (location/imageSize/subarea/border/tiled/colorType/nonOpaque/colorOverride). |
| `equals`/`hashCode` | - | - | Value equality over all fields; subclasses extend via `isEqual`. |

**Gotcha:** `draw(float,float,float,float)` does *no* theme coloring — always call the `IDrawable.draw(GuiContext,...)` overload (or `applyColor` yourself) unless you intend a raw texture blit.

**Gotcha:** `getSubArea` composes with existing UV bounds via `lerpU`/`lerpV`, so calling it on an already-sub-area'd texture further narrows relative to that sub-area, not the original image.

### `UITexture.Builder`

Fluent builder; the only way most `GuiTextures` entries are constructed.

```java
public static class Builder {
    public Builder location(ResourceLocation loc);
    public Builder location(String mod, String path);
    public Builder location(String path);
    public Builder imageSize(int w, int h);
    public Builder tiled(int imageWidth, int imageHeight);
    public Builder tiled();
    public Builder fullImage();
    public Builder subAreaXYWH(int x, int y, int w, int h);
    public Builder subAreaLTRB(int left, int top, int right, int bottom);
    public Builder subAreaUV(float u0, float v0, float u1, float v1);
    public Builder adaptable(int bl, int bt, int br, int bb);
    public Builder adaptable(int borderX, int borderY);
    public Builder adaptable(int border);
    public Builder canApplyTheme();
    public Builder colorType(@Nullable ColorType colorType);
    public Builder defaultColorType();
    public Builder textColorType();
    public Builder iconColorType();
    public Builder name(String name);
    public Builder nonOpaque();
    public UITexture build();
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `location(...)` (3 overloads) | resource location, or mod+path, or bare path | `this` | Required. |
| `imageSize(int w, int h)` | pixel dimensions of the source PNG | `this` | Required for `tiled()`, `adaptable(...)`, and pixel-based sub-areas (defaults to 16x16, or `setDefaultImageSize`'s value, if omitted). |
| `tiled(int w, int h)` / `tiled()` | - | `this` | Draws repeated tiles instead of stretching; produces a `TiledUITexture`. |
| `fullImage()` | - | `this` | Default mode: whole file is the texture. |
| `subAreaXYWH(int x, int y, int w, int h)` / `subAreaLTRB(...)` | pixel rect | `this` | Pixel-space sub-region (converted to UV at `build()`). `xy(...)` is an `@ApiStatus.Obsolete` alias for `subAreaXYWH`. |
| `subAreaUV(float u0, float v0, float u1, float v1)` | 0-1 UV rect | `this` | `uv(...)` is an `@ApiStatus.Obsolete` alias. |
| `adaptable(int bl, int bt, int br, int bb)` / `adaptable(int borderX, int borderY)` / `adaptable(int border)` | 9-slice border widths in pixels, 0 = no border on that side | `this` | Produces an `AdaptableUITexture`. Combines with `tiled()` to tile (rather than stretch) the border/center segments. |
| `colorType(@Nullable ColorType)` | - | `this` | `null` = texture is never recolored by theme. |
| `canApplyTheme()` / `defaultColorType()` | - | `this` | Both equivalent to `colorType(ColorType.DEFAULT)`. Use for background-style textures. |
| `textColorType()` | - | `this` | `colorType(ColorType.TEXT)`. |
| `iconColorType()` | - | `this` | `colorType(ColorType.ICON)`. For flat white/grey icon shapes. |
| `name(String)` | - | `this` | Auto-registers the built texture with `DrawableSerialization` under this name for JSON lookup. |
| `nonOpaque()` | - | `this` | Marks texture as (partly) transparent; drawing keeps GL blend enabled instead of disabling it. |
| `build()` | - | `UITexture`, `AdaptableUITexture`, or `TiledUITexture` depending on which modifiers were set | Throws `NullPointerException`/`IllegalArgumentException` on missing location or non-positive image size, `IllegalArgumentException` if UV values fall outside 0-1. |

**Example — real usage, `src/main/java/com/cleanroommc/modularui/drawable/GuiTextures.java:150-155`:**
```java
UITexture BUTTON_CLEAN = UITexture.builder()
        .location(ModularUI.ID, "gui/widgets/base_button")
        .imageSize(18, 18)
        .adaptable(1)
        .name("vanilla_button").canApplyTheme()
        .build();
```

### `com.cleanroommc.modularui.drawable.AdaptableUITexture`

A [9-slice texture](https://en.wikipedia.org/wiki/9-slice_scaling): draws corners at native size, stretches (or tiles) edges along one axis, and stretches/tiles the center — so a bordered texture can be resized without distorting its border. Package-private constructor; only reachable via `UITexture.builder().adaptable(...)`.

```java
public class AdaptableUITexture extends UITexture {
    @Override public void draw(float x, float y, float width, float height);
    public void drawStretched(float x, float y, float width, float height);
    public void drawTiled(float x, float y, float width, float height);
}
```

| Method | Notes |
|---|---|
| `draw(float,float,float,float)` | If `width`/`height` exactly equal the source image size, falls back to a plain single-quad `UITexture.draw`. Otherwise dispatches to `drawStretched` or `drawTiled` depending on the `tiled` flag set via the builder. |
| `drawStretched(...)` | If all 4 borders are `<= 0`, behaves like a plain texture. Otherwise draws up to 9 quads (4 corners at native size + up to 4 stretched edges + stretched center) depending on which border sides are non-zero. |
| `drawTiled(...)` | Same layout as `drawStretched` but edges/center are tiled (via `GuiDraw.drawTiledTexture`) rather than stretched; corners are still drawn at native size (never tiled). |

**Gotcha:** corners are *never* resized in either mode — only edges and the center scale. A texture whose target draw size is smaller than `bl+br` (or `bt+bb`) will visually overlap/clip.

**Inferred:** `getSubArea` on an `AdaptableUITexture` preserves the border widths and tiled flag, so slicing a sub-region of an already-adaptable texture still 9-slices with the same border pixel widths — this is likely only correct if the sub-area covers the whole original image, since border widths are absolute pixel counts, not UV-relative.

**Example — real usage, `GuiTextures.MC_BACKGROUND`:**
```java
UITexture MC_BACKGROUND = UITexture.builder()
        .location(ModularUI.ID, "gui/background/vanilla_background")
        .imageSize(195, 136)
        .adaptable(4)
        .name("vanilla_background")
        .defaultColorType()
        .build();
```

### `com.cleanroommc.modularui.drawable.TiledUITexture`

Draws the whole texture tiled (repeated at native resolution) rather than stretched, with no border slicing. Package-private constructor; reachable via `UITexture.builder().tiled()` (without `adaptable(...)`).

```java
public class TiledUITexture extends UITexture {
    @Override public void draw(float x, float y, float width, float height);
}
```

If `width`/`height` match the native image size, falls back to a single stretched quad (equivalent, since no scaling occurs); otherwise calls `GuiDraw.drawTiledTexture`, which tiles full-size copies and clips a partial tile at the right/bottom edge.

### `com.cleanroommc.modularui.drawable.FallbackableUITexture`

Wraps a "candidate" `UITexture` and a fallback (`UITexture` or another `FallbackableUITexture`, chainable). Resolves to the candidate if its resource actually exists on disk, else the fallback. Not itself an `IDrawable` — callers must call `.get()` to obtain the `UITexture` to draw.

```java
public class FallbackableUITexture {
    public FallbackableUITexture(UITexture candidate, UITexture fallback);
    public FallbackableUITexture(UITexture candidate, FallbackableUITexture fallback);
    public FallbackableUITexture(UITexture fallback);
    public UITexture get();
    public static void reload();
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `FallbackableUITexture(UITexture candidate, UITexture fallback)` | candidate looked up first | - | If resource is missing at first use, fallback is used from then on (result is cached, see `get()`). |
| `FallbackableUITexture(UITexture candidate, FallbackableUITexture fallback)` | chain to another fallback wrapper | - | Allows multi-level fallback chains. |
| `FallbackableUITexture(UITexture fallback)` | candidate is `null` | - | Always resolves to the fallback (useful as a placeholder that can be swapped later, presumably via subclassing or reflection — no setter exists). |
| `get()` | - | resolved `UITexture` | Lazily resolves and **caches** the choice (`useFallback` field) on first call; only re-checked after `reload()`. |
| `reload()` (static) | - | - | Clears the cached resolution on **every** `FallbackableUITexture` instance ever constructed (tracked in a static list) so `get()` re-checks resource existence. Presumably called on resource-pack reload. |

**Gotcha:** resource existence is only checked `NetworkUtils.isDedicatedClient()`-aware — on a dedicated server (or non-client context) it **always** uses the fallback, never attempting `Minecraft.getMinecraft().getResourceManager()`.

**Gotcha (from source comment):** the class carries a `// TODO: Does this really need to exist?` note — every instance is kept forever in a static `ALL_INSTANCES` list for `reload()` to iterate, so avoid constructing large numbers dynamically (this is meant for static, small-in-number, mod-defined fallback textures).

No usage in `test/`. **Example (constructed, not from repo):**
```java
FallbackableUITexture icon = new FallbackableUITexture(
        UITexture.fullImage("mymod", "gui/icons/custom_gear"),
        GuiTextures.GEAR); // falls back to built-in gear icon if mymod's texture is missing
UITexture resolved = icon.get();
```

### `com.cleanroommc.modularui.drawable.TabTexture`

Bundles 6 `UITexture`s (start/middle/end × active/inactive) sliced out of one source sheet, used to render a strip of tabs where the selected tab's edge pieces differ from its neighbors. Constructed via the static factory, which auto-slices a single texture into the 6 sub-areas based on axis/orientation.

```java
public class TabTexture {
    public static TabTexture of(UITexture texture, GuiAxis axis, boolean positive, int width, int height, int textureInset);
    public TabTexture(UITexture startActive, UITexture active, UITexture endActive,
                       UITexture startInactive, UITexture inactive, UITexture endInactive,
                       int width, int height, int textureInset, GuiAxis axis, boolean positive);
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `of(UITexture texture, GuiAxis axis, boolean positive, int width, int height, int textureInset)` | `texture` must contain a 2(active/inactive) × 3(start/middle/end) grid; `axis` = tab strip orientation; `positive` = whether tabs grow toward positive axis direction | new `TabTexture` | Slices `texture` into 6 `getSubArea(...)` regions; the active/inactive halves and the start/middle/end thirds are arranged differently depending on `axis`/`positive` (4 branches in source), throws `IllegalArgumentException` if `axis` is neither horizontal nor vertical (impossible given `GuiAxis` has only X/Y). |
| `getStart(boolean active)` / `getMiddle(boolean active)` / `getEnd(boolean active)` | active state | corresponding `UITexture` | Plain accessors into the 6 stored textures. |
| `get(int location, boolean active)` | `location < 0` → start, `== 0` → middle, `> 0` → end | `UITexture` | Convenience dispatcher, e.g. for "this is the Nth tab out of M". |
| `getWidth()` / `getHeight()` / `getTextureInset()` / `getAxis()` / `isPositive()` | - | stored values | Plain getters describing intended draw size and axis. |

Used in `test/` only via the pre-built `GuiTextures.TAB_TOP`/`TAB_BOTTOM`/`TAB_LEFT`/`TAB_RIGHT` constants (see `TestTile.java:165,168` — `.tab(GuiTextures.TAB_TOP, -1)` / `.tab(GuiTextures.TAB_TOP, 0)`), not by constructing a `TabTexture` directly.

**Definition — `GuiTextures.java:226`:**
```java
TabTexture TAB_TOP = TabTexture.of(UITexture.fullImage(ModularUI.ID, "gui/tab/tabs_top", ColorType.DEFAULT), GuiAxis.Y, false, 28, 32, 4);
```

---

## `com.cleanroommc.modularui.drawable.ColorType`

A named function `WidgetTheme -> int` describing *which* theme color a texture/drawable should recolor itself with. Acts as a small registry keyed by name (for JSON round-tripping).

```java
public class ColorType {
    public static final ColorType DEFAULT; // WidgetTheme::getColor
    public static final ColorType TEXT;    // WidgetTheme::getTextColor
    public static final ColorType ICON;    // WidgetTheme::getIconColor
    public static ColorType get(String name);
    public ColorType(String name, ToIntFunction<WidgetTheme> colorGetter);
    public String getName();
    public int getColor(WidgetTheme theme);
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `get(String name)` (static) | registered name | matching `ColorType`, or `DEFAULT` if unknown | Backed by a static `Map`; every `new ColorType(name, ...)` self-registers in its constructor. |
| `ColorType(String name, ToIntFunction<WidgetTheme> colorGetter)` | unique name + color-lookup function | - | Constructing one always registers/overwrites the name in the static map — creating a `ColorType` with a name that collides with `DEFAULT`/`TEXT`/`ICON` silently replaces the lookup for that name. |
| `getName()` | - | `String` | |
| `getColor(WidgetTheme theme)` | current theme | `int` ARGB (or RGB, per theme's convention) | Applies `colorGetter`. |

**Gotcha:** equality/hashCode are name-only, so two `ColorType`s with the same name but different `colorGetter`s are `.equals()`.

No direct construction in `test/`; used exclusively through `UITexture.Builder.colorType(...)`/`defaultColorType()`/`textColorType()`/`iconColorType()` and the three static constants.

---

## Icon family

### `com.cleanroommc.modularui.api.drawable.IIcon` (interface, for context)

```java
public interface IIcon extends IDrawable {
    @Nullable IDrawable getWrappedDrawable();
    int getWidth();
    int getHeight();
    default int getSize(GuiAxis axis);
    Box getMargin();
    default IDrawable getRootDrawable();
    default HoverableIcon asHoverable();
    default InteractableIcon asInteractable();
    IIcon EMPTY_2PX = EMPTY.asIcon().height(2);
}
```
A "fixed-size" `IDrawable` — `getWidth()`/`getHeight()` return `0` to mean "dynamic" (size decided by the layout context) rather than a real 0px size. `asHoverable()`/`asInteractable()` wrap in `HoverableIcon`/`InteractableIcon` — used by `RichText`/`RichTextWidget` to give inline icons tooltips or click handlers (see `TestGuis.java:330-342` below).

### `com.cleanroommc.modularui.drawable.Icon`

The concrete `IIcon`: wraps any `IDrawable`, giving it a fixed width/height (or 0 for "auto"), optional aspect ratio, `Alignment` (used when the wrapped drawable's natural size differs from the frame), and a `Box` margin. This is what `IDrawable.asIcon()` returns (`new Icon(this).size(getDefaultWidth(), getDefaultHeight())`), and is the class behind the `GuiTextures.CROSS_TINY.asIcon().size(10)` pattern used throughout the widget code.

```java
public class Icon implements IIcon, IJsonSerializable {
    public Icon(IDrawable drawable);
}
```

#### Sizing / layout methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `width(int width)` / `height(int height)` | pixels, clamped to `>= 0` | `this` | `0` = auto/expand (dynamic size resolved by the parent layout). |
| `size(int width, int height)` | - | `this` | `width(width).height(height)`. |
| `size(int size)` | - | `this` | Square: `width(size).height(size)`. |
| `expandWidth()` / `expandHeight()` | - | `this` | Shortcuts for `width(0)` / `height(0)`. |
| `aspectRatio(float aspectRatio)` | width/height ratio | `this` | See draw-time gotcha below. |
| `alignment(Alignment alignment)` | - | `this` | Used to position the drawable within its frame when the resolved size differs from the frame size (e.g. aspect-ratio-constrained icons, or icons smaller than their margin-reduced frame). |
| `center()` | - | `this` | `alignment(Alignment.Center)`. |
| `margin(int left, int right, int top, int bottom)` / `margin(int horizontal, int vertical)` / `margin(int all)` | - | `this` | Delegates to `Box.all(...)` overloads. |
| `marginLeft(int)` / `marginRight(int)` / `marginTop(int)` / `marginBottom(int)` | - | `this` | Per-side margin setters. |
| `getWidth()` / `getHeight()` / `getAlignment()` / `getMargin()` | - | current values | Plain getters (`getWidth`/`getHeight` return the raw stored value, `0` meaning auto). |

#### Draw behavior
`draw(GuiContext, int x, int y, int width, int height, WidgetTheme)`:
1. Shrinks the frame by `margin` first.
2. If `width`/`height` were explicitly set (`> 0`), they override the frame's dimension.
3. If `aspectRatio > 0`: fills in whichever of width/height is still unset (`0`) based on the other and the ratio; if **both** width and height are explicitly set, the aspect ratio is silently ignored and (in dev env) logged as an error, then cleared (`this.aspectRatio = 0`) to avoid repeat log spam.
4. `alignment` repositions the drawable inside the (possibly larger) frame if the resolved size doesn't fill it.
5. Delegates to the wrapped `IDrawable.draw(...)`.

**Gotcha:** setting both `width(...)` and `height(...)` *and* `aspectRatio(...)` is a no-op combination that gets silently disabled on first draw (with a dev-only error log) — set at most one of {both dimensions} vs {one dimension + aspect ratio}.

**Gotcha:** all fluent setters mutate `this` and return it — `Icon` is not copy-on-write; sharing one `Icon` instance across multiple widgets means they fight over size/margin/alignment state.

JSON: `ofJson(JsonObject)` (static factory used by `DrawableSerialization`) deserializes a nested `"drawable"`/`"icon"` field then calls `.asIcon()` on it; `loadFromJson`/`saveToJson` round-trip width/height (`"autoWidth"`/`"autoSize"` flags force `0`), `aspectRatio`, `alignment`, and margin.

**Example — real usage, `src/main/java/com/cleanroommc/modularui/test/TestGui.java:66`:**
```java
new ButtonWidget<>()
        .onMousePressed(button -> item.removeSelfFromList())
        .overlay(GuiTextures.CROSS_TINY.asIcon().size(10))
        .width(10).heightRel(1f)
```

**Example — aspect ratio, `src/main/java/com/cleanroommc/modularui/test/TestGuis.java:646-649`:**
```java
new Rectangle().color(Color.RED_ACCENT.main)
        .asIcon().aspectRatio(4f / 3).width(70)
        .asWidget().size(80)
        .overlay(IKey.str("4:3 | width = 70"))
```

### `com.cleanroommc.modularui.drawable.DelegateIcon`

Forwarding `IIcon` — every method delegates to a wrapped `icon` field. Base class for `HoverableIcon`/`InteractableIcon`; useful on its own as a mutable "swap the underlying icon later" wrapper.

```java
public class DelegateIcon implements IIcon {
    public DelegateIcon(IIcon icon);
    public IIcon getDelegate();
    public IIcon findRootDelegate();
    protected void setDelegate(IIcon icon);
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getWrappedDrawable()` / `getWidth()` / `getHeight()` / `getMargin()` / `draw(...)` | - | forwarded to `icon` | All `IIcon` contract methods pass straight through. |
| `getDelegate()` | - | the wrapped `IIcon` | |
| `findRootDelegate()` | - | innermost non-`DelegateIcon` `IIcon` | Walks the `DelegateIcon` chain (`while (icon instanceof DelegateIcon di))`. |
| `setDelegate(IIcon icon)` | new wrapped icon | - | `protected` — subclasses decide whether/how to expose mutability. |
| `toString()` | - | `ClassSimpleName(wrappedToString)` | |

No direct instantiation in `test/` (only via subclasses). **Example (constructed, not from repo):**
```java
DelegateIcon swappable = new DelegateIcon(GuiTextures.GEAR.asIcon());
// later, via a subclass exposing setDelegate(...):
// swappable.setDelegate(GuiTextures.WRENCH.asIcon());
```

### `com.cleanroommc.modularui.drawable.HoverableIcon`

`DelegateIcon` + `IHoverable` + `ITooltip<HoverableIcon>` — an icon that can own a `RichTooltip`, tracking its own rendered `Area` for tooltip positioning. Returned by `IIcon.asHoverable()`.

```java
public class HoverableIcon extends DelegateIcon implements IHoverable, ITooltip<HoverableIcon> {
    public HoverableIcon(IIcon icon);
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getTooltip()` | - | `@Nullable RichTooltip` | |
| `setRenderedAt(int x, int y)` | - | - | Updates internal `Area` to `(x, y, getWidth(), getHeight())`; called once in the constructor with `(0,0)`. **Inferred:** the rich-text renderer is responsible for calling this again during layout to keep the area accurate for hover/tooltip positioning. |
| `getRenderedArea()` | - | `Area` | Re-syncs size (`getWidth()`/`getHeight()`) before returning — position is only as fresh as the last `setRenderedAt` call. |
| `tooltip()` | - | non-null `RichTooltip` | Lazily creates one on first access, with `parent(area -> area.set(getRenderedArea()))` so the tooltip anchors to this icon's rendered area. |
| `tooltip(RichTooltip tooltip)` | - | `this` | Fluent setter, replaces any existing tooltip. |

**Example — real usage, `src/main/java/com/cleanroommc/modularui/test/TestGuis.java:330-334`:**
```java
.add(new ItemDrawable(new ItemStack(Blocks.grass))
        .asIcon()
        .asHoverable()
        .tooltip(richTooltip -> richTooltip.addFromItem(new ItemStack(Blocks.grass))
                .add(IKey.GRAY + "Lorem ipsum ...")))
```

### `com.cleanroommc.modularui.drawable.InteractableIcon`

`DelegateIcon` + `Interactable` — an icon that reacts to mouse/keyboard input via settable callback fields (`IGuiAction.*` functional interfaces). Returned by `IIcon.asInteractable()`.

```java
public class InteractableIcon extends DelegateIcon implements Interactable {
    public InteractableIcon(IIcon icon);
    public void playClickSound();
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `onMousePressed(IGuiAction.MousePressed)` / `onMouseReleased(...)` / `onMouseTapped(...)` / `onMouseScrolled(...)` / `onKeyPressed(...)` / `onKeyReleased(...)` / `onKeyTapped(...)` | matching callback | `this` | Fluent setters storing the callback; only one callback per event type (setting again replaces, no chaining/list). |
| `onMousePressed(int mouseButton)` / `onMouseTapped(int)` (interface impl) | - | `Interactable.Result` | Calls the stored callback if present; on success plays the click sound and returns `SUCCESS`. Returns `ACCEPT` (pressed) or `IGNORE` (tapped) if no callback is set or it returns `false`. |
| `playClickSound()` | - | - | Delegates to `Interactable.playButtonClickSound()`. Currently unconditional (a commented-out `if (this.playClickSound)` guard suggests a planned toggle that isn't wired up). |

**Example — real usage, `src/main/java/com/cleanroommc/modularui/test/TestGuis.java:336-341`:**
```java
.add(new ItemDrawable(new ItemStack(Items.porkchop))
        .asIcon()
        .asInteractable()
        .onMousePressed(button -> {
            ModularUI.LOGGER.info("Pressed Pork");
            return true;
        }))
```

---

## `com.cleanroommc.modularui.drawable.GuiTextures`

`interface` acting purely as a namespace of `public static final` `UITexture`/`TabTexture` constants — the built-in icon/widget-texture registry. Most icons come from a single 256x256 `icons.png` atlas (via the package-private `UITexture.icon(name, x, y[, w, h])` helper, 16x16 unless overridden), addressed by pixel offset; widget/background textures are separate PNGs built with `UITexture.builder()`.

```java
public interface GuiTextures {
    UITexture GEAR, MORE, SAVED, SAVE, ADD, DUPE, REMOVE, POSE, FILTER,
              MOVE_UP, MOVE_DOWN, LOCKED, UNLOCKED, COPY, PASTE, CUT, REFRESH,
              DOWNLOAD, UPLOAD, SERVER, FOLDER, IMAGE, EDIT, MATERIAL, CLOSE,
              LIMB, CODE, MOVE_LEFT, MOVE_RIGHT, HELP, LEFT_HANDLE, MAIN_HANDLE,
              RIGHT_HANDLE, REVERSE, BLOCK, FAVORITE, VISIBLE, INVISIBLE, PLAY,
              PAUSE, MAXIMIZE, MINIMIZE, STOP, FULLSCREEN, ALL_DIRECTIONS,
              SPHERE, SHIFT_TO, SHIFT_FORWARD, SHIFT_BACKWARD, MOVE_TO, GRAPH,
              WRENCH, EXCLAMATION, LEFTLOAD, RIGHTLOAD, BUBBLE, FILE, PROCESSOR,
              MAZE, BOOKMARK, SOUND, SEARCH, CHECKMARK, FAVORITE_OUTLINE,
              COLOR_WHEEL, SUN, MOON, CHECKBOARD, DISABLED, CURSOR; // 16x16 atlas icons

    UITexture MUI_LOGO, MC_BACKGROUND, MENU_BACKGROUND,
              MC_BUTTON, MC_BUTTON_PRESSED, MC_BUTTON_HOVERED, MC_BUTTON_HOVERED_PRESSED, MC_BUTTON_DISABLED,
              BUTTON_CLEAN, DISPLAY, DISPLAY_SMALL,
              SLOT_ITEM, SLOT_ITEM_PLAYER, SLOT_ITEM_HOTBAR, SLOT_FLUID,
              PROGRESS_ARROW, PROGRESS_CYCLE, CYCLE_BUTTON_DEMO,
              CHECK_BOX, CROSS, CROSS_TINY, ARROW_UP, ARROW_DOWN,
              CHECK_BOX_EMPTY, CHECK_BOX_FULL; // standalone widget textures

    TabTexture TAB_TOP, TAB_BOTTOM, TAB_LEFT, TAB_RIGHT;
}
```

Field-by-field javadoc doesn't exist (they're self-descriptive constants); the notable groups are:
- **Atlas icons** (`GEAR` .. `CURSOR`): all 16x16 (or explicit `w,h` for a few like `MOVE_UP`/`MOVE_DOWN` at 16x8), built with `iconColorType()` (theme-recolored as flat icons), sourced from `textures/gui/icons.png` (256x256).
- **Backgrounds/buttons** (`MC_BACKGROUND`, `MENU_BACKGROUND`, `MC_BUTTON*`, `BUTTON_CLEAN`, `DISPLAY*`, `SLOT_*`): 9-slice (`adaptable(...)`), most with `defaultColorType()`/`canApplyTheme()` so panel-background theming recolors them; `MC_BUTTON*` variants are also `tiled()` (so the 9-slice edges/center repeat rather than stretch — matches vanilla button texture behavior).
- **Standalone widget icons** (`CHECK_BOX`, `CROSS`, `CROSS_TINY`, `ARROW_UP`, `ARROW_DOWN`): plain `fullImage(...)`, no color type (drawn as-is, not recolored). `CHECK_BOX_EMPTY`/`CHECK_BOX_FULL` are `getSubArea` halves of `CHECK_BOX` (top/bottom half of a 2-state sprite sheet).
- **Tabs** (`TAB_TOP/BOTTOM/LEFT/RIGHT`): `TabTexture.of(...)` over a `gui/tab/tabs_*` sheet.

Consumers reference these as plain `IDrawable`/`UITexture` values — used directly as `.overlay(...)`/`.background(...)` arguments, or turned into a fixed-size `Icon` via `.asIcon()` first when a specific pixel size is wanted (`GuiTextures.CROSS_TINY.asIcon().size(10)`). Because every entry is `public static final` on an `interface`, referencing e.g. `GuiTextures.ADD` never allocates — the same `UITexture` instance is shared everywhere it's used (its only mutable field, `colorOverride`, is only ever changed via `withColorOverride(...)`, which returns a **copy**, so the shared constant is never mutated by callers).

**Example — real usage (multiple constants), `src/main/java/com/cleanroommc/modularui/test/TestGui.java:105-119`:**
```java
panel.child(new ButtonWidget<>()
        .bottom(7).size(12, 12).leftRel(0.5f)
        .overlay(GuiTextures.ADD)
        .onMouseTapped(mouseButton -> { otherPanel.openPanel(); return true; }));
// ...
private final IDrawable activeBackground = GuiTextures.BUTTON_CLEAN;
private final IDrawable background = GuiTextures.SLOT_FLUID;
```

---

## `com.cleanroommc.modularui.drawable.GuiDraw`

Low-level, stateless, `static`-only immediate-mode rendering helper. Nearly every other class in this package (and many widgets) calls into it rather than touching `Platform`/`GlStateManager`/Tessellator directly. All coordinates are **GUI-space** (already scaled/translated by the current `GuiContext`/GL matrix, i.e. widget-local pixels), not raw screen pixels — callers are expected to be inside an active draw pass. Constants `PI2`/`PI_2` are `2π`/`π/2`.

```java
public class GuiDraw {
    public static final double PI2, PI_2;
}
```

### Rectangles / gradients
| Method | Notes |
|---|---|
| `drawRect(float x0, float y0, float w, float h, int color)` | Flat-color quad. |
| `drawHorizontalGradientRect(..., int colorLeft, int colorRight)` / `drawVerticalGradientRect(..., int colorTop, int colorBottom)` | 2-color gradients; delegate to the 4-corner overload. |
| `drawRect(float x0, float y0, float w, float h, int colorTL, int colorTR, int colorBL, int colorBR)` | Per-corner colored quad (arbitrary bilinear gradient). |
| `drawRectRaw(BufferBuilder buffer, float x0, float y0, float x1, float y1, int color)` / `(..., int r, int g, int b, int a)` | Appends a quad's 4 vertices to an already-active `buffer` — for use *inside* a `Platform.startDrawing(...)` lambda, not standalone. |
| `drawBorderInsideLTRB` / `drawBorderOutsideLTRB` / `drawBorderInsideXYWH` / `drawBorderOutsideXYWH` | Draws a rectangular outline of given thickness, either inset (default border=1) or offset outward from the given bounds. Replaces the deprecated `drawOutline*`/`drawBorder` methods. |
| `drawDropShadow(int x, int y, int w, int h, int oX, int oY, int opaque, int shadow)` | Solid rect (`opaque` color) plus 4 gradient-shaded edges fading to `shadow` color over `oX`/`oY` pixels — a rectangular drop shadow. |
| `drawDropCircleShadow(int x, int y, int radius, int segments, int opaque, int shadow)` / overload with `offset` | Circular drop shadow; `offset` variant draws a solid disk of `offset` radius then a gradient ring out to `radius`. |

### Circles / rounded rects
| Method | Notes |
|---|---|
| `drawCircle(float x0, float y0, float diameter, int color, int segments)` / `(..., int centerColor, int outerColor, int segments)` | Wraps `drawEllipse` with equal width/height. |
| `drawEllipse(float x0, float y0, float w, float h, int color, int segments)` / `(..., int centerColor, int outerColor, int segments)` | Triangle-fan; `centerColor` at the middle vertex, `outerColor` at the rim — lets you fake a radial gradient. Used by `Circle`. |
| `drawRoundedRect(..., int cornerRadius, int segments)` (flat, vertical-gradient, horizontal-gradient, and full 4-corner-color overloads) | Rounded-corner rect via a single triangle fan; `segments` controls corner smoothness (quarter-circle per corner). Used by `Rectangle` when `cornerRadius > 0`. |

### Textures
| Method | Notes |
|---|---|
| `drawTexture(ResourceLocation, float x, float y, float w, float h, int u, int v, int textureWidth, int textureHeight)` and pixel-UV overloads | Binds `location` then draws a quad using pixel `u,v` converted to UV internally (`1/textureW`, `1/textureH`). |
| `drawTexture(ResourceLocation, x0,y0,x1,y1, u0,v0,u1,v1[, withBlend])` and UV-only overloads | The main "draw a texture region at explicit UV corners" family; used by `UITexture.draw`. `withBlend` toggles `Platform.setupDrawTex(location, withBlend)`. |
| `drawTexture(BufferBuilder buffer, ...)` overloads | Buffer-appending variants for use inside an active draw batch (e.g. from `AdaptableUITexture`'s multi-quad borders). |
| `drawTiledTexture(...)` (several overloads: `ResourceLocation` + pixel UV, `ResourceLocation` + float UV, buffer-appending) | Repeats a `tileWidth × tileHeight` texture region to fill `w × h`, clipping a partial tile at the far edge (computes `countX`/`countY` and a fractional "filler" tile). Used by `TiledUITexture` and `AdaptableUITexture.drawTiled`. |

### Items / fluids / entities
| Method | Notes |
|---|---|
| `drawItem(ItemStack item, int x, int y, float width, float height, int z)` | No-op if `item == null`. Scales `RenderItem` to `width/16, height/16` (vanilla item renders are natively 16x16) and calls `renderItemAndEffectIntoGUI`; `z` is added to `100` for the item's `zLevel` (vanilla convention so enchant-glint layers don't z-fight). Restores `zLevel` to 0 after. Used by `ItemDrawable`/`IngredientDrawable`. |
| `drawFluidTexture(FluidStack content, float x0, float y0, float width, float height, float z)` | No-op if `content == null`. Looks up the fluid's still icon from the block atlas (falls back to `"missingno"` if absent), tints by `fluid.getColor(content)` (alpha forced to `1f` if the fluid reports `0`), tiles it via `drawTiledTexture` at native sprite resolution. Used by `FluidDrawable`. Has a Hodgepodge-mod integration hook (`markNeedsAnimationUpdate`) if that mod is loaded. |
| `drawEntityRaw(Entity entity)` | Minimal entity render (`RenderManager.renderEntityWithPosYaw`), no GL setup/teardown — caller's responsibility. |
| `drawEntity(T entity, float x, float y, float w, float h, float z, @Nullable Consumer<T> preDraw, @Nullable Consumer<T> postDraw)` | Full GUI-safe entity draw: pushes matrix, calls `Platform.setupDrawEntity`, runs `preDraw` (for custom rotation/scale), draws, runs `postDraw`, tears down. **Gotcha (javadoc):** "When transforming during pre draw, you may need to manually correct the scale and offset." |
| `drawEntityLookingAtMouse(EntityLivingBase entity, float x, float y, float w, float h, float z, int mouseX, int mouseY, Consumer<EntityLivingBase> preDraw, Consumer<EntityLivingBase> postDraw)` | Ported from vanilla's player-inventory entity preview: temporarily overwrites the entity's yaw/pitch/head-rotation fields to face the mouse, restores them afterward. |

### Text / amount labels
| Method | Notes |
|---|---|
| `drawText(String text, float x, float y, float scale, int color, boolean shadow)` | Thin wrapper over vanilla `FontRenderer.drawString`, manually scaling via a GL matrix push/pop (rather than the internal `TextRenderer`). |
| `drawStandardSlotAmountText(int amount, String format, Area area)` / `(long amount, ...)` | Draws an item-count-style label bottom-right of `area`, only if `amount > 1` or a custom `format` prefix is given. The `long` overload insets by 1px on each side. |
| `drawScaledAmountText` / `drawAmountText` / `drawScaledAlignedTextInBox` | Increasingly general helpers for auto-shrinking a formatted number/string to fit a box; `NumberFormat.AMOUNT_TEXT` supplies K/M/B-style abbreviation. `drawScaledAlignedTextInBox` simulates a draw first (`textRenderer.setSimulate(true)`) to measure width before picking the final scale when the text is longer than 2 chars and the box is wider than 16px. |
| `drawTooltipBackground(ItemStack stack, List<String> lines, int x, int y, int textWidth, int height, @Nullable RichTooltip tooltip)` | Draws the layered background+border quads behind a tooltip; if `tooltip != null`, fires a `RichTooltipEvent.Color` on the Forge event bus first so mods can override the 3 colors. |

### Misc
| Method | Notes |
|---|---|
| `afterRenderItemAndEffectIntoGUI(ItemStack stack)` | Restores GL blend func/color after drawing an enchanted item — mirrors a private vanilla `RenderItem` step that must be redone manually per a `// asked by Forge :shrug:` comment. |
| `drawOutline*` / `drawBorder(float,float,float,float,int,float)` | `@Deprecated`, `@ApiStatus.ScheduledForRemoval(inVersion = "3.2.0")` — use the `drawBorderInside*`/`drawBorderOutside*` family instead. |

**Gotcha:** Every drawing method assumes an active/compatible GL draw pass and correct blend/texture state; the surrounding `Platform.setupDraw*`/`Platform.startDrawing(...)` calls are what actually set up shaders/vertex format — calling the raw buffer-appending overloads (`drawRectRaw`, `drawTexture(BufferBuilder, ...)`) outside such a lambda will not render correctly.

**Example — real usage, `src/main/java/com/cleanroommc/modularui/test/TestEventHandler.java:64-68`:**
```java
private static final IIcon tooltipLine = new IDrawable() {
    @Override
    public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme) {
        int high = Color.PURPLE.main;
        int low = Color.withAlpha(high, 0.05f);
        GuiDraw.drawHorizontalGradientRect(x, y + 1, width / 2f, 1, low, high);
        GuiDraw.drawHorizontalGradientRect(x + width / 2f, y + 1, width / 2f, 1, high, low);
    }
}.asIcon().height(3);
```

---

## `com.cleanroommc.modularui.drawable.BufferBuilder`

"Simple wrapper to keep code the same as 1.12 as much as possible" (source javadoc) — a fluent shim around Minecraft 1.7.10's static `Tessellator.instance`, giving it the chainable `pos(...).tex(...).color(...).endVertex()` API that 1.12+ `BufferBuilder` code uses. Not a real GPU buffer — every call proxies straight to the (global, singleton) `Tessellator`.

```java
public class BufferBuilder {
    public static final BufferBuilder buffer, bufferbuilder; // aliases, same singleton
    public BufferBuilder pos(double x, double y, double z);
    public BufferBuilder tex(double u, double v);
    public BufferBuilder color(int r, int g, int b, int a);
    public BufferBuilder color(float r, float g, float b, float a);
    public void endVertex();
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `pos(double x, double y, double z)` | vertex position | `this` | Stages position; applied on `endVertex()`. |
| `tex(double u, double v)` | UV | `this` | Stages texture coords. |
| `color(int r,g,b,a)` / `color(float r,g,b,a)` | 0-255 ints or 0-1 floats | `this` | Float overload converts via `* 255.0F` (truncating cast — not rounded). |
| `endVertex()` | - | - | Commits staged pos/tex/color to `Tessellator.instance` **in tex → then pos order internally is color→tex→pos** (source calls `setColorRGBA` then `setTextureUV` then `addVertex`); clears the "is set" flags after, so unset attributes from a previous vertex don't leak forward accidentally — but attributes **do** carry over if you don't call `tex`/`color` again before the next `pos`+`endVertex` (each flag is independently reset only after being consumed). |

**Gotcha:** the constructor is `private` — always use the `BufferBuilder.buffer` (or `.bufferbuilder`) static singleton; there is exactly one instance in the whole mod, matching `Tessellator.instance`'s own singleton nature. All draw code passed into `Platform.startDrawing(...)` lambdas receives this same shared instance as its `buffer` argument.

Constructed and driven internally by `Platform.startDrawing(...)`; user code interacts with it only inside the lambda passed to that method (see `GuiDraw`/`Rectangle`/`Circle`/`Plot` for examples), never constructs it directly.

---

## Other drawables

### `com.cleanroommc.modularui.drawable.Rectangle`

`IDrawable` + `IJsonSerializable` + `IAnimatable<Rectangle>` — a themeable flat/gradient/rounded/hollow rectangle. The most common ad-hoc "colored box" drawable in the codebase.

```java
public class Rectangle implements IDrawable, IJsonSerializable, IAnimatable<Rectangle> {
    public Rectangle(); // white (0xFFFFFFFF), no rounding, 6 corner segments
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `color(int color)` | single ARGB color | `this` | All 4 corners same color. |
| `color(int colorTL, int colorTR, int colorBL, int colorBR)` | per-corner colors | `this` | Arbitrary bilinear-gradient rect. |
| `verticalGradient(int top, int bottom)` / `horizontalGradient(int left, int right)` | 2-color gradient | `this` | Convenience wrappers over the 4-color `color(...)`. |
| `cornerRadius(int)` | pixels, clamped `>= 0` | `this` | Non-zero + `hollow(...)` logs an error (`"Hollow rectangles currently can't have a corner radius"`) — the combination is unsupported but not prevented/thrown, just logged. |
| `cornerSegments(int)` | tessellation smoothness for rounded corners | `this` | |
| `solid()` | - | `this` | `borderThickness = 0` (filled rect; default). |
| `hollow(float borderThickness)` / `hollow()` | border width (default `1`) | `this` | Draws only an outline (triangle-strip border) instead of a filled quad; incompatible with `cornerRadius > 0` (see above). |
| `canApplyTheme(boolean)` | - | `this` | Opts this instance into theme recoloring (default `false` — colors set via `color(...)` are used as-is). |
| `getColor()` | - | `colorTL` (top-left corner color) | Approximation of "the" color when a single flat color was set via `color(int)`. |
| `setColor(...)` / `setCornerRadius(...)` / `setCornerSegments(...)` / `setVerticalGradient(...)` / `setHorizontalGradient(...)` / `setCanApplyTheme(...)` | same params as their lower-case counterparts | `this` | `@Deprecated`, `@ApiStatus.ScheduledForRemoval(inVersion = "3.2.0")` aliases — prefer the fluent (non-`set`) names. |
| `interpolate(Rectangle start, Rectangle end, float t)` / `copyOrImmutable()` | `IAnimatable` contract | interpolated/copied `Rectangle` | Lets a `Rectangle` be animated (corner radius, segments, all 4 corner colors) via the `animation` package. |

**Gotcha:** default `canApplyTheme` is `false` — a plain `new Rectangle().color(x)` is drawn exactly as specified regardless of the active `WidgetTheme`; you must opt in with `canApplyTheme(true)` for theme-driven recoloring (rarely used since colors are usually set explicitly anyway).

**Example — real usage, `src/main/java/com/cleanroommc/modularui/test/TestGuis.java:642-645`:**
```java
new Rectangle().color(Color.BLUE_ACCENT.main)
        .asIcon().aspectRatio(4f / 3)
        .asWidget().size(80)
        .overlay(IKey.str("4:3 Free"))
```

**Example — hollow rectangle, `src/main/java/com/cleanroommc/modularui/test/TestGuis.java:285-288`:**
```java
.overlay(new Rectangle()
        .color(Color.GREEN.main)
        .hollow(2)
        .asIcon().margin(5))
```

### `com.cleanroommc.modularui.drawable.Circle`

`IDrawable` + `IJsonSerializable` + `IAnimatable<Circle>` — a filled ellipse/circle with independent center/outer colors (radial-ish gradient) and adjustable tessellation.

```java
public class Circle implements IDrawable, IJsonSerializable, IAnimatable<Circle> {
    public Circle(); // colorInner=0, colorOuter=0, segments=40
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `colorInner(int)` / `setColorInner(int)` | center-vertex color | `this` | `setColorInner` just calls `colorInner`. |
| `colorOuter(int)` / `setColorOuter(int)` | rim color | `this` | |
| `color(int inner, int outer)` / `setColor(int, int)` | both | `this` | |
| `color(int color)` | single flat color | `this` | `color(color, color)`. |
| `segments(int)` / `setSegments(int)` | tessellation (triangle-fan slice count) | `this` | Default `40`; drawn via `GuiDraw.drawEllipse`, which fits a `w × h` ellipse into the draw area, so a non-square draw area yields an actual ellipse, not a circle. |
| `interpolate(...)` / `copyOrImmutable()` | `IAnimatable` contract | - | Interpolates both colors (`Color.lerp`) and segment count. |

Draws at whatever `width`/`height` the caller passes (default `IDrawable.getDefaultWidth/Height() == 0`, so wrap in `.asIcon().size(n)` to get a fixed on-screen size — a circle needs equal width/height to look round).

**Example — real usage, `src/main/java/com/cleanroommc/modularui/test/TestTile.java:233-236`:**
```java
tooltip.addLine(new Circle()
                .setColor(Color.RED.darker(2), Color.RED.brighter(2))
                .asIcon()
                .size(20))
```

### `com.cleanroommc.modularui.drawable.HueBar`

`IDrawable` — draws a 6-stop rainbow gradient bar (red→yellow→green→cyan→blue→magenta→red) along a `GuiAxis`, used as the hue slider background in color pickers.

```java
public class HueBar implements IDrawable {
    public HueBar(GuiAxis axis);
}
```

Draws 6 equal gradient segments (`GuiDraw.drawHorizontalGradientRect`/`drawVerticalGradientRect` depending on `axis.isHorizontal()`) spanning full saturation/value HSV hues at 0/60/120/180/240/300/360°. Calls `applyColor(widgetTheme.getColor())` first even though the bar's own colors are fixed HSV stops — **Inferred:** this only affects GL alpha/blend state reset, since `HueBar` doesn't override `canApplyTheme()` (defaults to `false`), so `applyColor` actually resets to opaque white rather than tinting the bar.

No usage in `test/`. **Example (constructed, not from repo):**
```java
IDrawable hue = new HueBar(GuiAxis.X);
// typically used as the background of a draggable "hue picker" slider widget
```

### `com.cleanroommc.modularui.drawable.Scrollbar`

`IDrawable` + `IJsonSerializable` — the visual thumb/track drawn for scrollable widgets (see `AbstractScrollWidget`), with two presets.

```java
public class Scrollbar implements IDrawable, IJsonSerializable {
    public static final Scrollbar DEFAULT; // striped = false
    public static final Scrollbar VANILLA; // striped = true
    public Scrollbar(boolean striped);
}
```

| Method | Notes |
|---|---|
| `draw(...)` | Draws 3 nested flat rects (outer light border, dark border, lighter fill — all `Color.mix`ed with the theme color) then, if `isStriped()`, overlays 2px-spaced 1px stripes along whichever axis is longer (skips entirely if both `width` and `height` are `<= 5`). |
| `isStriped()` | - | `boolean`, the constructor flag. |
| `canApplyTheme()` | - | always `true` | So its base colors are always mixed with the current theme's color via `Color.mix`. |
| `ofJson(JsonObject)` (static) | `"striped"`/`"vanilla"` boolean field | `DEFAULT` or `VANILLA` | Only ever returns one of the two singletons — `Scrollbar` is not otherwise constructible from JSON with custom striping logic beyond the boolean. |

No usage in `test/`. **Example (constructed, not from repo):**
```java
IDrawable scrollbarLook = Scrollbar.VANILLA; // striped scrollbar track, e.g. for a scrollable Grid
```

### `com.cleanroommc.modularui.drawable.FluidDrawable`

`IDrawable` — draws a `FluidStack`'s still texture (tiled, tinted by the fluid's color), analogous to `ItemDrawable` for items.

```java
public class FluidDrawable implements IDrawable {
    public FluidDrawable();
    public FluidDrawable(@Nullable FluidStack fluid);
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `setFluid(FluidStack fluid)` | can be `null` | `this` | `null` fluid draws nothing (`GuiDraw.drawFluidTexture` no-ops on `null`). |
| `getDefaultWidth()` / `getDefaultHeight()` | - | `16` | Matches vanilla slot size. |
| `asWidget()` | - | `Widget<?>` sized `16` | Overridden to pre-size the wrapping widget, unlike the generic `IDrawable.asWidget()` default. |

No direct usage in `test/` (fluid slots in the test mod go through `SlotGroupWidget`/fluid-slot widgets built on `GuiTextures.SLOT_FLUID`, not this drawable directly). **Example (constructed, not from repo):**
```java
IDrawable fluidIcon = new FluidDrawable(new FluidStack(FluidRegistry.WATER, 1000));
```

### `com.cleanroommc.modularui.drawable.ItemDrawable`

`IDrawable` + `IJsonSerializable` — draws an `ItemStack` (including enchant glint) at the given area; the standard way to show an item as a background/overlay/icon.

```java
public class ItemDrawable implements IDrawable, IJsonSerializable {
    public ItemDrawable();
    public ItemDrawable(@Nullable ItemStack item);
    public ItemDrawable(@NotNull Item item);
    public ItemDrawable(@NotNull Item item, int meta);
    public ItemDrawable(@NotNull Item item, int meta, int amount);
    public ItemDrawable(@NotNull Item item, int meta, int amount, @Nullable NBTTagCompound nbt);
    public ItemDrawable(@NotNull Block item);
    public ItemDrawable(@NotNull Block item, int meta);
    public ItemDrawable(@NotNull Block item, int meta, int amount);
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getItem()` | - | current `ItemStack` (may be `null`) | |
| `setItem(...)` (9 overloads mirroring the constructors) | item/stack/meta/amount/nbt | `this` | All funnel down to `setItem(ItemStack)`. |
| `getDefaultWidth()` / `getDefaultHeight()` | - | `16` | Vanilla item render size. |
| `asWidget()` | - | `Widget<?>` sized `16` | Same pre-sizing override pattern as `FluidDrawable`. |
| `ofJson(JsonObject)` (static) | `"item"` (mod:name[:meta]), `"meta"`, `"nbt"` fields | new `ItemDrawable` | Throws `JsonParseException` for malformed item ids; empty `"item"` string yields an empty (no-item) `ItemDrawable`. |

**Gotcha:** `draw(...)` calls `GuiDraw.drawItem(this.item, ...)` unconditionally — `GuiDraw.drawItem` itself no-ops on `null`, so an `ItemDrawable` with no item set is safe to draw (renders nothing), but forgetting to set an item is a silent no-op rather than an error.

**Example — real usage, `src/main/java/com/cleanroommc/modularui/test/TestTile.java:166`:**
```java
.overlay(new ItemDrawable(Blocks.chest).asIcon())
```

### `com.cleanroommc.modularui.drawable.IngredientDrawable`

`IDrawable` + (not `IJsonSerializable`, despite similar shape to `ItemDrawable`) — cycles through an array of `ItemStack`s over time, showing one at a time (e.g. for "any of these items" recipe display).

```java
public class IngredientDrawable implements IDrawable {
    public IngredientDrawable(ItemStack... items);
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getItems()` / `setItems(ItemStack... items)` | - | array / `void` | Plain accessor pair (note: `setItems` returns `void`, not `this` — not fluent, unlike most setters in this package). |
| `getCycleTime()` | - | `int` ms | Default `1000`. |
| `cycleTime(int cycleTime)` | ms per item | `this` | The only fluent setter on this class. |

**Draw logic:** picks `items[(Minecraft.getSystemTime() % (cycleTime * items.length)) / cycleTime]` — a global wall-clock-driven index, so all `IngredientDrawable`s with the same `cycleTime` and item count stay in sync with each other (not per-instance phase-shifted). No-op if `items.length == 0`.

No usage in `test/`. **Example (constructed, not from repo):**
```java
IDrawable anyPlank = new IngredientDrawable(
        new ItemStack(Blocks.planks, 1, 0),
        new ItemStack(Blocks.planks, 1, 1)
).cycleTime(750);
```

### `com.cleanroommc.modularui.drawable.NamedDrawableRow`

`IDrawable` — draws a text label left-aligned and an `IIcon` right-aligned in the same row, "as if `Flow.row().mainAxisAlignment(SPACE_BETWEEN)`" per its javadoc, but implemented directly rather than via the `Flow` widget.

```java
public class NamedDrawableRow implements IDrawable {
    public NamedDrawableRow();
    public NamedDrawableRow(@Nullable IKey name, @Nullable IIcon drawable);
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `name(@Nullable IKey key)` | text | `this` | |
| `drawable(@Nullable IIcon icon)` | trailing icon | `this` | |
| `getNameKey()` / `getDrawable()` | - | current values | |
| `getDefaultWidth()` / `getDefaultHeight()` | - | sum / max of the two children's sizes | Used by `asIcon()`'s default sizing. |

**Gotcha:** the icon's width used for positioning (`getWidth() + getMargin().horizontal()`) means the icon must have an explicit fixed width (not `0`/auto) for correct right-alignment — an auto-width icon would compute `wd = 0 + margin`, placing it almost at the row's right edge regardless of its actual rendered size.

No usage in `test/`. **Example (constructed, not from repo):**
```java
IDrawable row = new NamedDrawableRow(IKey.str("Efficiency"), GuiTextures.GEAR.asIcon().size(12));
```

### `com.cleanroommc.modularui.drawable.FlowDrawable`

`IDrawable` — a non-widget, draw-time-only re-implementation of `widgets.layout.Flow`'s box layout (main/cross axis alignment, expansion, margins) for a list of `IIcon`s. Per its javadoc: "This version calculates the children positions on each frame" (i.e., no persistent layout tree/widget lifecycle — recomputed every `draw` call).

```java
public class FlowDrawable implements IDrawable {
    public static FlowDrawable row();    // GuiAxis.X
    public static FlowDrawable column(); // GuiAxis.Y
    public FlowDrawable(GuiAxis axis);
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `mainAxisAlignment(Alignment.MainAxis)` / `crossAxisAlignment(Alignment.CrossAxis)` | - | `this` | Defaults: `START` / `CENTER`. |
| `icon(IIcon icon)` | append one | `this` | |
| `icons(int amount, IntFunction<IIcon> func)` | generate `amount` icons via index | `this` | |
| `icons(Iterable<T> it, Function<T, IIcon> func)` | map arbitrary elements to icons | `this` | |
| `icons(Collection<IIcon> icons)` | bulk-add | `this` | |
| `removeIcon(IIcon icon)` / `removeAll()` | - | `this` | |
| `getAxis()` / `getIcons()` | - | `GuiAxis` / live `List<IIcon>` | `getIcons()` exposes the actual backing list (mutable in place, not a copy). |
| `getMainAxisDefaultSize()` / `getCrossAxisDefaultSize()` | - | `int` | Sum (main axis) / max (cross axis) of each icon's size-on-that-axis (falling back to `getDefaultWidth/Height()`, then a hardcoded `10`, if unset) plus margins; back `getDefaultWidth()`/`getDefaultHeight()`. |

**Gotcha:** icons with size `<= 0` on the main axis are treated as "expanders" sharing the remaining space equally (`expanderSize = (size - childrenSize) / expandedAmount`); if `expandedAmount == 0` this division is skipped, but if all icons are expanders and the total available `size` is smaller than needed, sizes can go negative — no clamping is performed.

**Gotcha:** `SPACE_BETWEEN`/`SPACE_AROUND` main-axis alignment silently degrades to `START` whenever any expander icon is present (source comment implies expanders make manual spacing meaningless).

No usage in `test/` (the widget-tree equivalent, `Flow.row()`/`Flow.column()`, is used everywhere instead — `FlowDrawable` is for contexts needing an `IDrawable`, e.g. a single tooltip line or icon composite, rather than a full widget). **Example (constructed, not from repo):**
```java
IDrawable iconRow = FlowDrawable.row()
        .mainAxisAlignment(Alignment.MainAxis.CENTER)
        .icon(GuiTextures.LOCKED.asIcon().size(12))
        .icon(GuiTextures.UNLOCKED.asIcon().size(12));
```

### `com.cleanroommc.modularui.drawable.DrawableStack`

`IDrawable` + `IJsonSerializable` — draws an array of `IDrawable`s on top of each other, same position/size for each. The runtime type produced when JSON deserializes an array of drawables (see `DrawableSerialization`), and the implementation behind `IDrawable.of(IDrawable...)` when given 2+ drawables.

```java
public class DrawableStack implements IDrawable, IJsonSerializable {
    public static final IDrawable[] EMPTY_BACKGROUND;
    public DrawableStack(IDrawable... drawables);
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `draw(...)` | - | - | Iterates and draws every non-`null` element in order (later = on top). |
| `canApplyTheme()` | - | `true` if **any** contained drawable can | |
| `getDrawables()` | - | backing `IDrawable[]` | Returned directly (not a copy). |
| `saveToJson(JsonObject)` | - | throws `IllegalStateException` | Serialized as a JSON array special-case by `DrawableSerialization` instead — this method is never actually meant to be invoked. |

`null`/empty varargs collapse to the shared `EMPTY_BACKGROUND` (empty array) constant. Not directly constructed in `test/`; reached via `IDrawable.of(a, b, c)` or JSON arrays. **Example (constructed, not from repo):**
```java
IDrawable combined = IDrawable.of(GuiTextures.SLOT_ITEM, new ItemDrawable(stack));
```

### `com.cleanroommc.modularui.drawable.DynamicDrawable`

`IDrawable` — wraps a `Supplier<IDrawable>`, re-resolving which drawable to render every frame. Its javadoc: "Return value of the supplier should be deterministic per render frame, in order to apply `ITheme` to correct object" — i.e. calling the supplier twice in the same frame must yield an equivalent drawable, since `canApplyTheme()` and `draw(...)` each call it independently.

```java
public class DynamicDrawable implements IDrawable {
    public DynamicDrawable(Supplier<IDrawable> supplier);
    public Supplier<IDrawable> getSupplier();
}
```

`draw(...)` and `canApplyTheme()` each call `supplier.get()` (twice total per frame if both are invoked) and no-op / return `false` if it yields `null`.

**Gotcha:** because the supplier is invoked separately in `canApplyTheme()` and `draw(...)`, a non-deterministic supplier (e.g. one with side effects, or based on a value that can change between those two calls) can theme one call's result against a *different* drawable than the one actually drawn.

No usage in `test/`. **Example (constructed, not from repo):**
```java
IDrawable conditional = new DynamicDrawable(() ->
        someState.isActive() ? GuiTextures.CHECK_BOX_FULL : GuiTextures.CHECK_BOX_EMPTY);
```

### `com.cleanroommc.modularui.drawable.DelegateDrawable`

`IDrawable` — forwarding wrapper around a single `IDrawable`, with a `protected setDrawable(...)` so subclasses can decide whether/how to expose mutability (mirrors the `DelegateIcon`/`Icon` relationship, but for the plain `IDrawable` level).

```java
public class DelegateDrawable implements IDrawable {
    public DelegateDrawable(@Nullable IDrawable drawable);
    protected void setDrawable(@Nullable IDrawable drawable);
    public IDrawable getWrappedDrawable();
}
```

| Method | Notes |
|---|---|
| `setDrawable(@Nullable IDrawable)` | `null` is normalized to `IDrawable.EMPTY` (never stores `null`), so `getWrappedDrawable()` is guaranteed non-null (`@NotNull`). |
| `getWrappedDrawable()` | Returns the live wrapped drawable — used by e.g. `Icon`'s draw dispatch chain and `findRootDelegate`-style walks elsewhere. |
| `draw` / `canApplyTheme` / `applyColor` / `getDefaultWidth` / `getDefaultHeight` / `asWidget` / `asIcon` | All forwarded to the wrapped drawable. |

No direct instantiation in `test/`. **Example (constructed, not from repo):**
```java
class ReplaceableBackground extends DelegateDrawable {
    ReplaceableBackground(IDrawable initial) { super(initial); }
    void replace(IDrawable next) { setDrawable(next); } // exposes mutability
}
```

---

## `com.cleanroommc.modularui.drawable.IconRenderer`

**`@Deprecated`.** A stateful, mutable "cursor" for laying out and drawing multiple lines of mixed `IDrawable`/`IKey`/`IIcon` content (predates `RichText`/`TextRenderer`'s line-based API). `SHARED` is a reusable static instance.

```java
@Deprecated
public class IconRenderer {
    public static final IconRenderer SHARED;
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `setAlignment(Alignment, float maxWidth[, float maxHeight])` | layout box | - | |
| `setShadow(boolean)` / `setScale(float)` / `setPos(int x, int y)` / `setColor(int)` / `setLinePadding(int)` / `setSimulate(boolean)` / `setUseWholeWidth(boolean)` | - | - | Mutates shared internal state; not thread-safe / not reentrant-safe given `SHARED` is a singleton. |
| `draw(GuiContext, IDrawable text)` / `draw(GuiContext, List<IDrawable> lines)` | content | - | Single-line convenience / multi-line entry point; delegates to `measureLines` then `drawMeasuredLines`. |
| `drawMeasuredLines(GuiContext, List<IIcon> lines)` | pre-measured lines | - | Computes total height/max width, then draws each line via `icon.draw(...)`, advancing `y` by `(height + linePadding) * scale`. |
| `measureLines(List<IDrawable> lines)` | mixed content | `List<IIcon>` | `IIcon` elements pass through; `IKey` elements are split on literal `\n`, word-wrapped via the font renderer, and converted to `TextIcon`s; anything else is wrapped via `.asIcon().height(FONT_HEIGHT)`. |
| `wrapLine(String line, float scale)` | - | `List<String>` | Uses `FontRenderer.listFormattedStringToWidth` if `maxWidth > 0`, else returns the line unchanged. |
| `getStartY(int totalHeight)` / `getStartX(float lineWidth)` (protected) | - | `int` | Alignment math against `maxWidth`/`maxHeight`. |
| `getFontHeight()` | - | `float` | `FONT_HEIGHT * scale`. |
| `getLastHeight()` / `getLastWidth()` | - | `float` | Measurements from the most recent draw/simulate pass. |
| `getFontRenderer()` (static) | - | `FontRenderer` | `Minecraft.getMinecraft().fontRenderer`. |

**Gotcha:** being `@Deprecated` with a shared mutable singleton, concurrent/nested use (e.g. drawing an `IconRenderer`-based line from inside another such line's draw callback) would corrupt shared state — new code should use `TextRenderer`/`RichText` instead.

No usage in `test/`.

---

## `com.cleanroommc.modularui.drawable.DrawableSerialization`

Gson `JsonSerializer<IDrawable>`/`JsonDeserializer<IDrawable>` plus two independent static registries: drawable **type** names (`"texture"`, `"color"`, `"rectangle"`, `"ellipse"`, `"item"`, `"icon"`, `"scrollbar"`, plus the special-cased `"text"`) and named `UITexture` **instances** (for `{"id": "..."}` JSON references, populated by `UITexture.Builder.name(...)`/`register(...)`).

```java
public class DrawableSerialization implements JsonSerializer<IDrawable>, JsonDeserializer<IDrawable> {
    @ApiStatus.Internal public static void init();
    public static void registerTexture(String name, UITexture texture);
    public static void registerTextureAutoName(UITexture texture);
    @Nullable public static UITexture getTexture(String s);
    @Nullable public static String getTextureId(UITexture texture);
    public static <T extends IDrawable & IJsonSerializable> void registerDrawableType(String id, Class<T> type, Function<@NotNull JsonObject, @NotNull T> creator);
    public static IDrawable deserialize(JsonElement json);
    public static JsonElement serialize(IDrawable drawable);
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `init()` (`@ApiStatus.Internal`) | - | - | Registers the 7 built-in drawable types. Called once by the library itself — not meant for mod code to call. |
| `registerTexture(String name, UITexture texture)` | - | - | If `texture` is already registered under a different name, **renames** it (removes the old mapping) rather than adding a second alias. If `name == null`, delegates to `registerTextureAutoName`. |
| `registerTextureAutoName(UITexture texture)` | - | - | Derives a name from the last path segment of the texture's `ResourceLocation` (minus extension), disambiguating with a `_1`, `_2`, ... suffix on collision (comparing by `.equals()` first — re-registering an identical texture under the same derived name is a no-op). Logs a dev-warning past 20 attempts, throws `IllegalStateException` past 10000 (pathological-collision guard). |
| `getTexture(String s)` / `getTextureId(UITexture texture)` | - | `@Nullable` | Reverse lookups both ways. |
| `registerDrawableType(String id, Class<T> type, Function<JsonObject,T> creator)` | unique id, optional type (for serialization's reverse lookup), a no-arg-ish factory taking the raw json | - | Throws `IllegalArgumentException` if `id` is already registered — **not** idempotent, so calling twice for the same id (e.g. from a hot-reloadable mod) will crash. |
| `deserialize(JsonElement)` / `serialize(IDrawable)` (static) | - | - | Convenience entry points equivalent to going through `JsonHelper.DESERIALIZER`/`SERIALIZER` with `IDrawable.class`. |

**Gotcha (serialization dispatch):** `serialize(IDrawable, ...)` special-cases `IDrawable.EMPTY`→`JsonNull`, `IDrawable.NONE`→`"none"`, `DrawableStack`→a raw JSON array (recursing per element), `IKey`→`{"type":"text","text":...}` (marked `// TODO serialize text properly`), and otherwise requires the drawable implement `IJsonSerializable` (throwing `IllegalArgumentException` if not) and have a registered type key found by walking up the class hierarchy (`type = type.getSuperclass()`) until a match or `Object.class` — so a custom `IDrawable` subclass of a registered type (e.g. extending `Rectangle`) serializes under its **superclass's** registered key, not its own.

**Gotcha (deserialization):** a JSON array of drawables collapses to a single `IDrawable` if it has exactly one non-null element, or to `IDrawable.EMPTY` if empty — only 2+ elements actually produce a `DrawableStack`.

Not directly used in `test/` (a build-time/config-loading concern); this is the mechanism behind `UITexture.parseFromJson`/`Icon.ofJson`/`ItemDrawable.ofJson`/`Scrollbar.ofJson` etc.

---

## `com.cleanroommc.modularui.drawable.Stencil`

Static utility managing a stencil-buffer-based clip-region stack (used instead of `glScissor` so clipping still works under 3D/holographic transforms, per its javadoc). Not an `IDrawable` — it's a cross-cutting rendering utility called by scrollable/clipped widgets (and `GraphDrawable`, to clip plots to the graph's inner area).

```java
public class Stencil {
    public static void reset();
    public static void apply(Rectangle area, @Nullable GuiContext context); // java.awt.Rectangle
    public static void applyAtZero(Rectangle area, @Nullable GuiContext context);
    public static void applyTransformed(Rectangle area);
    public static void applyTransformed(int x, int y, int w, int h);
    public static void apply(int x, int y, int w, int h, @Nullable GuiContext context);
    public static void apply(Runnable stencilShape, boolean hideStencilShape);
    public static void apply(Runnable stencilShape, int x, int y, int w, int h, @Nullable GuiContext context);
    public static void apply(Runnable stencilShape, int x, int y, int w, int h, @Nullable GuiContext context, boolean hideStencilShape);
    public static void remove();
    public static boolean isInsideScissorArea(Area area, IViewportStack stack);
}
```

Note: the `Rectangle` parameter type here is `java.awt.Rectangle`, **not** `com.cleanroommc.modularui.drawable.Rectangle`.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `reset()` | - | - | Clears the whole stencil stack and GL stencil buffer/state — presumably called once per frame or on GUI open. |
| `apply(int x, int y, int w, int h, @Nullable GuiContext context)` | clip rect, optional context for coordinate transform | - | The main entry point; increments the global stencil test value so nested `apply` calls clip to the **intersection** of all active regions (`stencils.top().clamp(scissor)`). If `context != null`, the stored (for intersection-tracking) area is transformed by the context's viewport stack, but the actual GL stencil shape drawn is **not** — see gotcha. |
| `applyAtZero(Rectangle area, ...)` | - | - | Same as `apply` but ignores `area.x`/`area.y` (clips at `(0,0)`-relative instead) — for widgets drawing relative to their own local origin. |
| `applyTransformed(...)` (2 overloads) | - | - | `apply(..., null)` — i.e. skip context-based area transform, only OpenGL's active matrix affects the region. |
| `apply(Runnable stencilShape, ...)` (3 overloads) | custom shape-drawing callback instead of an implicit rectangle | - | For clipping to a non-rectangular shape; `hideStencilShape` controls whether the shape-drawing pass is visually hidden (color/depth mask off) while writing the stencil buffer — almost always `true` except the raw 2-arg overload's caller decides explicitly. |
| `remove()` | - | - | Pops the top stencil region; if the stack becomes empty, fully `reset()`s and disables `GL_STENCIL_TEST`. **Must be paired 1:1 with a prior `apply(...)` call** — an unmatched `remove()` on an empty stack would underflow `ObjectArrayList.pop()`. |
| `isInsideScissorArea(Area area, IViewportStack stack)` | local-space area + viewport transform stack | `boolean` | Returns `true` (nothing to cull) if the stencil stack is empty; otherwise transforms `area` into screen space and checks intersection with the current top clip region — used as a cheap visibility/culling pre-check before actually drawing something expensive. |

**Gotcha:** always call `apply(...)`/`remove()` in strictly nested, balanced pairs (`apply` → draw clipped content → `remove`) — this is a stack, and `remove()` always pops the *top* regardless of which `apply()` call the caller "means" to close.

**Example — real usage, `src/main/java/com/cleanroommc/modularui/drawable/graph/GraphDrawable.java:53-57`:**
```java
Stencil.applyTransformed((int) this.view.sx0, (int) this.view.sy0, (int) (this.view.getScreenWidth() + 1), (int) (this.view.getScreenHeight() + 1));
for (Plot plot : this.plots) {
    plot.draw(this.view);
}
Stencil.remove();
```

---

## `drawable.graph` package — charting

Self-contained line-chart feature: `GraphDrawable` (the `IDrawable` entry point) owns two `GraphAxis` (X/Y) and a list of `Plot`s, delegating graph-space↔screen-space math to `GraphView` and tick-position computation to pluggable `MajorTickFinder`/`MinorTickFinder` strategies. Every class in this subpackage is `@ApiStatus.Experimental` except `Plot` (which lacks the annotation, likely an oversight rather than a stability signal, given it's exercised through the same experimental `GraphDrawable` API).

**Usage in `test/`:** exercised once, in `src/main/java/com/cleanroommc/modularui/test/TestGuis.java:623-633` (`buildGraphUI`):
```java
public static @NotNull ModularPanel buildGraphUI() {
    double[] x = DAM.linspace(-25, 25, 200);
    // sin(x) / x
    double[] y1 = DAM.div(DAM.sin(x, null), x, null);
    return new ModularPanel("graph")
            .size(200, 160)
            .padding(5)
            .overlay(new GraphDrawable()
                    .graphAspectRatio(16 / 9f)
                    .plot(x, y1));
}
```

### `com.cleanroommc.modularui.drawable.graph.GraphDrawable`

The chart widget-background. Owns axes, plots, grid/tick styling, and a dirty flag that avoids recomputing axis limits/ticks every frame unless something changed.

```java
@ApiStatus.Experimental
public class GraphDrawable implements IDrawable {
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `redraw()` | - | - | Marks the graph (and every plot) dirty, forcing axis/tick/vertex recomputation on next draw. Called automatically by most fluent setters below. |
| `getX()` / `getY()` | - | `GraphAxis` | Direct mutable access to the axis objects. |
| `autoXLim()` / `autoYLim()` | - | `this` | Re-enables automatic min/max computation from plotted data (padded 5% on the Y axis only — see `GraphAxis.compute`). |
| `xLim(float min, float max)` / `yLim(float min, float max)` | explicit bounds | `this` | Disables auto-limits for that axis. |
| `majorTickStyle(float thickness, float length)` / `minorTickStyle(float thickness, float length)` | pixel thickness/length of tick marks | `this` | Does **not** call `redraw()` (unlike most other setters) — tick *style* doesn't affect tick *position* computation. |
| `xTickFinder(MajorTickFinder, MinorTickFinder)` / `yTickFinder(...)` | strategy objects | `this` | Calls `redraw()`. |
| `xTickFinder(float majorMultiples, int minorTicksBetweenMajors)` / `yTickFinder(...)` | convenience overload | `this` | Builds `new AutoMajorTickFinder(majorMultiples)` + `new AutoMinorTickFinder(minorTicksBetweenMajors)`. |
| `backgroundColor(int color)` | ARGB; alpha `0` is auto-promoted to `0xFF` (opaque) unless `color == 0` entirely (which disables the background) | `this` | `0` = no background drawn at all. |
| `plot(double[] x, double[] y[, int color][, float thickness])` (4 overloads) | raw data (+ optional style) | `this` | Convenience wrappers constructing a `Plot`. |
| `plot(Plot plot)` | pre-built `Plot` | `this` | The actual add method; calls `plot.redraw()`. |
| `majorGridStyle(float thickness, int color)` / `minorGridStyle(float thickness, int color)` | - | `this` | Combined thickness+color setters. |
| `disableMajorGrid()` / `disableMinorGrid()` / `enableMajorGrid()` / `enableMinorGrid()` | - | `this` | Shortcuts toggling thickness to `0` / `0.5f` (major) / `0.25f` (**minor — despite the name `enableMinorGrid`, it calls `majorGridLineThickness`, not `minorGridLineThickness`**). |
| `majorGridLineThickness(float)` / `minorGridLineThickness(float)` / `majorGridLineColor(int)` / `minorGridLineColor(int)` | - | `this` | Individual field setters. |
| `graphAspectRatio(float aspectRatio)` | width/height ratio for the plotted area (not the whole widget) | `this` | Forwarded to `GraphView.setAspectRatio`. |

**Gotcha (confirmed bug in source):** `enableMinorGrid()` calls `majorGridLineThickness(0.25f)` instead of `minorGridLineThickness(0.25f)` — calling it does not enable the minor grid; it overwrites the *major* grid's thickness to `0.25f`. Use `minorGridStyle(thickness, color)` directly to actually enable minor gridlines.

**Draw-time behavior:** on each `draw(...)`, resizes the internal `GraphView` to the widget's screen area, recomputes axis padding/limits/ticks only if dirty or the screen size changed, draws the background rect, grid lines, then clips (`Stencil.applyTransformed`) to the plot area before drawing each `Plot` (so lines don't overdraw axis labels), then draws tick marks, a border outline, and axis tick labels.

### `com.cleanroommc.modularui.drawable.graph.GraphView`

Package-private-field (all fields default/package-visibility) coordinate-transform helper: converts between "graph space" (`double`, arbitrary range/precision — the data's own units) and "screen space" (`float` GUI pixels). Owned exclusively by `GraphDrawable`; not constructed by user code.

```java
@ApiStatus.Experimental
public class GraphView {
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `g2sX(double v)` / `g2sY(double v)` | graph-space coordinate | screen-space `float` | **Y is inverted**: `g2sY` maps using `(gy1, gy0)` swapped, because graph Y grows upward but screen Y grows downward. |
| `s2gX(float v)` / `s2gY(float v)` | screen-space coordinate | graph-space `double` | Inverse of the above. |
| `g2sScaleX()` / `g2sScaleY()` | - | `double` | Ratio of screen-units per graph-unit on each axis (Y still uses the inverted pair). |
| `getZeroX()` / `getZeroY()` | - | `float` | Screen position of graph coordinate `0` on each axis — cached, recomputed in `setGraph`/`postResize`. |
| `setAspectRatio(float)` / `getAspectRatio()` | - | `this`-less setter / `float` | `0` = no aspect constraint (fill available space). |
| `getGraphX0()`/`X1()`/`Y0()`/`Y1()`, `getScreenX0()`/`X1()`/`Y0()`/`Y1()` | - | `double` / `float` | Raw bounds accessors. |
| `getScreenWidth()` / `getScreenHeight()` | - | `float` | `sx1-sx0` / `sy1-sy0`. |
| `getGraphWidth()` / `getGraphHeight()` | - | `double` | `gx1-gx0` / `gy1-gy0`. |

Package-private `setScreen`/`setGraph`/`postResize` are called by `GraphDrawable` each frame/on resize; `postResize` re-centers the screen rect if an `aspectRatio` is set (shrinking whichever screen dimension is oversized, keeping it centered) and refreshes the cached zero-position.

### `com.cleanroommc.modularui.drawable.graph.GraphAxis`

One axis (X or Y) of a `GraphDrawable`: owns its data-driven or manual min/max range, delegates tick position computation to `MajorTickFinder`/`MinorTickFinder`, and draws grid lines/tick marks/labels for itself against the *other* axis's position.

```java
@ApiStatus.Experimental
public class GraphAxis {
    public final GuiAxis axis;
    public double[] majorTicks, minorTicks;
    public TextRenderer.Line[] tickLabels;
    public MajorTickFinder majorTickFinder; // default: new AutoMajorTickFinder(true)
    public MinorTickFinder minorTickFinder; // default: new AutoMinorTickFinder(2)
    public String label;
    public double min, max;
    public boolean autoLimits; // default true
    public GraphAxis(GuiAxis axis);
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getAxis()` / `getMin()` / `getMax()` / `getMajorTickFinder()` / `getMinorTickFinder()` / `getLabel()` | - | current values | Plain getters; the public fields above are also directly accessible/mutable (no encapsulation for `min`/`max`/`majorTickFinder`/etc. — `GraphDrawable`'s `xLim`/`xTickFinder` methods just set these fields directly). |
| `compute(List<Plot> plots)` (package-private) | all plots on the graph | - | If `autoLimits`, scans all plots' data on this axis for min/max (Y axis gets ±5% padding); then re-derives major/minor tick positions and pre-formats tick label strings (`DecimalFormat` with a precision derived from the smallest tick spacing). |
| `applyPadding(GraphView)` (package-private) | - | - | Shrinks the `GraphView`'s screen rect to reserve space for this axis's tick labels (and axis label, if set) — horizontal axis reserves space at the bottom (`sy1 -=`), vertical axis reserves space on the left (`sx0 +=`). |
| `drawGridLines(...)` / `drawTicks(...)` / `drawLabels(GraphView, GraphAxis other)` (package-private) | - | - | Rendering internals invoked by `GraphDrawable`; not meant for external use. |

**Gotcha:** `min`/`max`/`majorTickFinder`/`minorTickFinder`/`autoLimits` are public mutable fields with no validation — directly assigning `axis.min = 5` without also setting `autoLimits = false` will have your manual value overwritten on the next `compute()` call (only `GraphDrawable.xLim`/`yLim` correctly pair the two).

### `com.cleanroommc.modularui.drawable.graph.Plot`

One data series: parallel `x[]`/`y[]` double arrays drawn as a colored, mitered-thickness polyline (or a single dot if only one point).

```java
public class Plot {
    public static final int[] DEFAULT_PLOT_COLORS; // 8-color default palette cycle
    public Plot();
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `data(double[] x, double[] y)` | parallel arrays, must be equal length | `this` | Throws `IllegalArgumentException` on length mismatch. Calls `redraw()`. |
| `thickness(float thickness)` | line width in screen pixels | `this` | Default `1f`. Calls `redraw()`. |
| `color(int color)` | ARGB | `this` | Passing `0` marks `defaultColor = true`, so `GraphDrawable.compute()` will auto-assign the next color from `DEFAULT_PLOT_COLORS` (cycling) — **does not** call `redraw()` (color doesn't affect vertex geometry). |
| `redraw()` | - | - | Marks the cached screen-space vertex buffer stale; recomputed lazily on next `draw(GraphView)`. |
| `draw(GraphView view)` | - | - | No-op if no data; draws a single filled square if exactly 1 point; otherwise rebuilds (if dirty) a mitered triangle-strip outline for the polyline and draws it. |
| `getThickness()` / `getColor()` / `getX()` / `getY()` | - | current values | `getX()`/`getY()` return the live backing arrays (not copies). |
| `getData(GuiAxis axis)` | - | `xs` or `ys` | Convenience for `GraphAxis.compute` to fetch whichever array matches its axis. |

**Gotcha (confirmed by source):** two consecutive back-to-back-identical points (`len == 0` when computing the segment direction for the first or last segment) throw `IllegalArgumentException` ("Graph can't handle the same point back to back!") — but a **duplicate point in the middle** of the series is silently skipped (`if (len == 0) continue;`) rather than erroring. This inconsistency means duplicate points are only safe away from the start/end of the series.

**Inferred:** the miter-join math (averaging + rescaling perpendicular offsets by `1/cosAngle`) can produce very long miter spikes at sharp near-180°-reversal angles, similar to standard miter-join line rendering artifacts in other graphics APIs — no miter-limit clamp is present in the source.

### `com.cleanroommc.modularui.drawable.graph.MajorTickFinder` / `MinorTickFinder`

Strategy interfaces for choosing tick positions given the current axis range.

```java
@ApiStatus.Experimental
public interface MajorTickFinder {
    double[] find(double min, double max, double[] ticks);
}

@ApiStatus.Experimental
public interface MinorTickFinder {
    double[] find(double min, double max, double[] majorTicks, double[] ticks);
}
```

| Interface | Method | Params | Returns | Notes |
|---|---|---|---|---|
| `MajorTickFinder` | `find(min, max, ticks)` | current range, a reusable scratch array | `double[]` (may be a new, larger array than `ticks` if it didn't fit) | Implementations should terminate the used portion with `Float.NaN`(reused as a sentinel in a `double[]`) if it doesn't fill the whole returned array, per `AutoMajorTickFinder`'s convention — consumers (`GraphAxis`) scan until `Double.isNaN(...)`. |
| `MinorTickFinder` | `find(min, max, majorTicks, ticks)` | range + already-computed major ticks + scratch array | `double[]` | Same NaN-terminated convention; typically places ticks *between* consecutive major ticks. |

Both interfaces exist purely so `GraphDrawable.xTickFinder(...)`/`yTickFinder(...)` can accept custom tick-placement strategies; the shipped implementations are `AutoMajorTickFinder`/`AutoMinorTickFinder` below.

### `com.cleanroommc.modularui.drawable.graph.AutoMajorTickFinder`

`MajorTickFinder` — places major ticks at every multiple of a configurable step, optionally auto-computing that step from the axis range (a simple "nice numbers" heuristic).

```java
@ApiStatus.Experimental
public class AutoMajorTickFinder implements MajorTickFinder {
    public AutoMajorTickFinder(boolean autoAdjust);
    public AutoMajorTickFinder(float multiple);
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `AutoMajorTickFinder(boolean autoAdjust)` | `true` = recompute step from data range each `compute()` | - | `multiple` starts at `10` until first auto-adjusted. This is `GraphAxis`'s default (`new AutoMajorTickFinder(true)`). |
| `AutoMajorTickFinder(float multiple)` | fixed step | - | `autoAdjust = false` — step never changes automatically. |
| `find(double min, double max, double[] ticks)` | - | ticks at every `multiple` starting at `floor(min/multiple)*multiple` | Grows the array if needed (`s = ceil((max-min)/multiple) + 2`); NaN-terminates. |
| `calculateAutoTickMultiple(double min, double max)` (package-private) | range | - | Targets ~5 major ticks across the range (`step = (max-min)/5`), then rounds `step` to a "nice" value depending on magnitude (sub-1, exactly-1, or >1 branches with different rounding logic) — a simplified nice-numbers algorithm. Only invoked by `GraphAxis.compute()` when `isAutoAdjust()` is true. |
| `isAutoAdjust()` | - | `boolean` | |
| `setMultiple(double)` | - | - | Not fluent (`void` return) — direct field mutation, no `redraw()` triggered (caller must call `GraphDrawable.redraw()`/re-set the tick finder to force recomputation). |

### `com.cleanroommc.modularui.drawable.graph.AutoMinorTickFinder`

`MinorTickFinder` — places a fixed number of evenly-spaced minor ticks strictly between each pair of consecutive major ticks.

```java
@ApiStatus.Experimental
public class AutoMinorTickFinder implements MinorTickFinder {
    public AutoMinorTickFinder(int amountBetweenMajors);
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `AutoMinorTickFinder(int amountBetweenMajors)` | minor ticks per major-tick gap | - | `GraphAxis`'s default is `new AutoMinorTickFinder(2)`. |
| `find(double min, double max, double[] majorTicks, double[] ticks)` | - | evenly-spaced minor ticks within each major-tick interval, clipped to `[min, max]` | Stops early (breaks out of the outer loop) as soon as it hits a `NaN` in `majorTicks` (i.e., the unused tail of that array). |

**Example (constructed, not from repo) — custom tick spacing:**
```java
GraphDrawable graph = new GraphDrawable()
        .plot(xs, ys)
        .xTickFinder(new AutoMajorTickFinder(5f), new AutoMinorTickFinder(4)) // major tick every 5 units, 4 minor ticks between
        .yTickFinder(new AutoMajorTickFinder(true), new AutoMinorTickFinder(1));
```
