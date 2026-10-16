# api/drawable and api/value Reference

Covers `com.cleanroommc.modularui.api.drawable`, `com.cleanroommc.modularui.api.value`, and
`com.cleanroommc.modularui.api.value.sync`. Source read directly from
`src/main/java/com/cleanroommc/modularui/api/...`; examples cross-checked against
`src/main/java/com/cleanroommc/modularui/test/*.java` where possible.

---

## Package overview: how `value` and `value/sync` relate

This is a small type hierarchy and it is not obvious from file names alone, so read this before the
per-interface sections:

- **`ISyncOrValue`** (`api/value/ISyncOrValue.java`) is the root marker interface. It is
  `@ApiStatus.NonExtendable` — user code should never implement it directly, only inherit it
  transitively. Widgets that accept "either a plain value or a network-synced value" (e.g.
  `ToggleButton.value(IBoolValue<?>)`, `TextFieldWidget.value(IStringValue<?>)`) work because both
  branches of the hierarchy below eventually extend it.
- **`IValue<T>`** (`api/value/IValue.java`) is the client-side-only binding: `getValue()`/`setValue(T)`
  plus `getValueType()`. Concrete non-synced implementations live in `com.cleanroommc.modularui.value`
  (`BoolValue`, `IntValue`, `DoubleValue`, `StringValue`, `EnumValue`, `ObjectValue`,
  `BoolValue.Dynamic`, etc.) — used for widgets whose state doesn't need to cross the network (e.g. a
  purely local GUI toggle).
- **`IBoolValue<T>`, `IByteValue<T>`, `IDoubleValue<T>`, `IEnumValue<T>`, `IFloatValue<T>`,
  `IIntValue<T>`, `ILongValue<T>`, `IShortValue<T>`, `IStringValue<T>`** (all in `api/value`) are typed
  convenience facets of `IValue<T>`. Each adds a primitive-typed getter/setter pair (e.g.
  `getBoolValue()/setBoolValue(boolean)`) and some provide default bridging to another facet (e.g.
  `IBoolValue` bridges to `IIntValue`, `IByteValue` bridges to both `IIntValue` and `IStringValue`).
  Widgets depend on these typed interfaces, not on `IValue<T>` directly, so any object that can produce
  the right primitive works regardless of its generic type `T`.
- **`IValueSyncHandler<T>`** (`api/value/sync/IValueSyncHandler.java`) extends `IValue<T>` and adds the
  networking contract: 3-arg `setValue(value, setSource, sync)`, `updateCacheFromSource`,
  `notifyUpdate`, `write`/`read` (PacketBuffer). Its abstract base implementation is
  `com.cleanroommc.modularui.value.sync.ValueSyncHandler` (extends `SyncHandler`), which concrete
  classes like `IntSyncValue`, `BooleanSyncValue`, `StringSyncValue`, `EnumSyncValue`, `DoubleSyncValue`
  live under `com.cleanroommc.modularui.value.sync`.
- **`IBoolSyncValue<T>`, `IByteSyncValue<T>`, `IDoubleSyncValue<T>`, `IFloatSyncValue<T>`,
  `IIntSyncValue<T>`, `ILongSyncValue<T>`, `IShortSyncValue<T>`, `IStringSyncValue<T>`** (in
  `api/value/sync`) are the synced counterparts of the `api/value` typed facets: each extends
  `IValueSyncHandler<T>` plus its non-synced facet (e.g. `IIntSyncValue<T> extends
  IValueSyncHandler<T>, IIntValue<T>`), and replaces the single-arg setter with a `(val, setSource,
  sync)` triple, defaulting the 1-arg and 2-arg overloads to `(val, true, true)` / `(val, setSource,
  true)`.
- **`IServerKeyboardAction`** and **`IServerMouseAction`** (`api/value/sync`) are unrelated
  single-method listener interfaces (not part of the value hierarchy) used by
  `com.cleanroommc.modularui.value.sync.InteractionSyncHandler` to run server-side callbacks in
  response to client input packets.

In short: pick a typed facet from `api/value` (e.g. `IIntValue<T>`) for local/no-sync widget state, or
its `api/value/sync` counterpart (e.g. `IIntSyncValue<T>`, normally via the concrete `IntSyncValue`) for
state that must be synced client↔server through a `PanelSyncManager`.

---

## api/drawable

### com.cleanroommc.modularui.api.drawable.IDrawable

An object that can be drawn at any position/size. Backbone of widget backgrounds and overlays.

```java
public interface IDrawable {
    IDrawable EMPTY = (context, x, y, width, height, widgetTheme) -> {};
    IDrawable NONE = (context, x, y, width, height, widgetTheme) -> {};

    static IDrawable of(IDrawable... drawables);
    static boolean isVisible(@Nullable IDrawable drawable);

    void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme);
    // ... default draw*/apply* methods below

    class DrawableWidget extends Widget<DrawableWidget> { ... }
}
```

To draw at a *fixed* size instead of "any size", use `IIcon` (`asIcon()`).

| Method | Params | Returns | Notes |
|---|---|---|---|
| `static IDrawable of(IDrawable... drawables)` | varargs drawables | `null` if empty/null array, the single element if length 1, else a new `DrawableStack` | Convenience combinator. |
| `void draw(GuiContext, int x, int y, int w, int h, WidgetTheme)` | position, size, theme | — | The one abstract method. `@SideOnly(Side.CLIENT)`. Implementors are responsible for calling `applyColor(int)` before drawing to respect the theme. |
| `default void drawAtZero(GuiContext, int w, int h, WidgetTheme)` | size, theme | — | `draw(ctx, 0, 0, w, h, theme)`; use inside widgets where GL is already translated to the widget's position. |
| `default void draw(GuiContext, Area area, WidgetTheme)` | area, theme | — | `draw` at `area.x/y/width/height`. Area padding is **not** applied. |
| `default void drawPadded(GuiContext, Area area, WidgetTheme)` | area, theme | — | Draws inside the area with padding applied (`area.getPadding()`, `paddedWidth()/paddedHeight()`). |
| `default void drawAtZero(GuiContext, Area area, WidgetTheme)` | area, theme | — | Like `drawAtZero(int,int,...)` but sized from `area.width/height`. Padding not applied despite the javadoc mentioning it — reads directly `area.width/height`. |
| `default void drawAtZeroPadded(GuiContext, Area area, WidgetTheme)` | area, theme | — | Draws at the area's padding offset with padded size, but with the origin still treated as "zero" (i.e., not translated by `area.x/y`). |
| `default boolean canApplyTheme()` | — | `false` by default | Override to `true` if the drawable should be tinted by the widget theme's color. |
| `default void applyColor(int themeColor)` | theme color (usually `WidgetTheme#getColor()`) | — | If `canApplyTheme()` sets GL color to `themeColor` (`Color.setGlColor`), else resets to opaque white. Call before drawing. |
| `default int getDefaultWidth()` / `getDefaultHeight()` | — | `0` by default | Intrinsic size hint, used by e.g. `asIcon()`. |
| `default Widget<?> asWidget()` | — | new `DrawableWidget` wrapping `this` | Lets a raw drawable be used as a widget (background between... itself and nothing; see `DrawableWidget`). |
| `default Icon asIcon()` | — | `new Icon(this).size(getDefaultWidth(), getDefaultHeight())` | Converts to a fixed-size `IIcon`. |
| `static boolean isVisible(@Nullable IDrawable drawable)` | drawable (nullable) | `false` for `null`, `EMPTY`, `NONE`, or an empty `DrawableStack`; `true` otherwise | Used to skip rendering/measuring work for effectively-invisible drawables. |

Constants: `EMPTY` and `NONE` are both no-op lambdas but are distinct instances/identities — `NONE` is
specifically used as a sentinel meaning "no hover texture, don't fall back to anything" (per its
javadoc), while `EMPTY` is a generic no-op. `isVisible` treats both as invisible.

`DrawableWidget` (nested class): a `Widget<DrawableWidget>` that draws the wrapped `IDrawable` at zero
using `getArea()`/`getActiveWidgetTheme(...)`; this is what `asWidget()` returns.

**Example** (constructed from `TestGuis.java`, `src/main/java/com/cleanroommc/modularui/test/TestGuis.java:491-504`):
```java
IDrawable luminanceSortedColors = (context1, x, y, width, height, widgetTheme) -> {
    // custom GL drawing using x, y, width, height
};
IDrawable gradient = (context1, x, y, width, height, widgetTheme) ->
        GuiDraw.drawHorizontalGradientRect(x, y, width, height, color1.getColor(), color2.getColor());
// ...
.child(luminanceSortedColors.asWidget().widthRel(1f).height(10))
.child(gradient.asWidget().widthRel(1f).height(10))
```
A custom anonymous `IDrawable` also appears in `TestEventHandler.java:62-70`, wrapped via `.asIcon().height(3)`,
and `GLTestGui.java:134-137` casts an inline lambda to `IDrawable` then chains `.asIcon().size(100).asWidget()`.

---

### com.cleanroommc.modularui.api.drawable.IHoverable

Marks an `IIcon` as hoverable inside `RichText`/rich tooltips (mouse-over detection + optional tooltip).
`@ApiStatus.NonExtendable` — obtain instances via `IIcon#asHoverable()` rather than implementing this
directly.

```java
@ApiStatus.NonExtendable
public interface IHoverable extends IIcon {
    default void onHover() {}
    @Nullable default RichTooltip getTooltip() { return null; }
    void setRenderedAt(int x, int y);
    Area getRenderedArea();
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `default void onHover()` | — | — | No-op by default. Called every frame this element is hovered inside a `RichText`. |
| `default @Nullable RichTooltip getTooltip()` | — | `null` by default | Tooltip to show while hovered, if any. |
| `void setRenderedAt(int x, int y)` | render position | — | Internal: records where this hoverable was last drawn, used to hit-test the mouse. |
| `Area getRenderedArea()` | — | last drawn `Area` | Used together with `setRenderedAt` for hit-testing. |

**Example** (real implementer, `src/main/java/com/cleanroommc/modularui/drawable/HoverableIcon.java`):
```java
public class HoverableIcon extends DelegateIcon implements IHoverable, ITooltip<HoverableIcon> {
    private final Area area = new Area();
    private RichTooltip tooltip;

    @Override public RichTooltip getTooltip() { return tooltip; }
    @Override public void setRenderedAt(int x, int y) { this.area.set(x, y, getWidth(), getHeight()); }
    @Override public Area getRenderedArea() { this.area.setSize(getWidth(), getHeight()); return this.area; }
}
```
Obtained in practice via `IIcon#asHoverable()`; a plain (non-hoverable) icon with its own tooltip is used
in `TestEventHandler.java:108` (`GuiTextures.MUI_LOGO.asIcon().size(18)`), but hoverable-specific tooltip
attachment isn't exercised in `test/`.

---

### com.cleanroommc.modularui.api.drawable.IIcon

An `IDrawable` with a fixed size (as opposed to "any size").

```java
public interface IIcon extends IDrawable {
    IIcon EMPTY_2PX = EMPTY.asIcon().height(2);

    @Nullable IDrawable getWrappedDrawable();
    int getWidth();
    int getHeight();
    Box getMargin();

    default int getSize(GuiAxis axis) { ... }
    default IDrawable getRootDrawable() { ... }
    default HoverableIcon asHoverable() { ... }
    default InteractableIcon asInteractable() { ... }
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `@Nullable IDrawable getWrappedDrawable()` | — | wrapped drawable or `null` | `null` if this icon wraps nothing. |
| `int getWidth()` | — | width, or `0` if dynamic | |
| `int getHeight()` | — | height, or `0` if dynamic | |
| `default int getSize(GuiAxis axis)` | axis | `getWidth()` if `axis.isHorizontal()` else `getHeight()` | |
| `@Override default int getDefaultWidth()/getDefaultHeight()` | — | delegates to `getWrappedDrawable().getDefault*()` if non-null, else `0` | Overrides `IDrawable`'s defaults. |
| `Box getMargin()` | — | margin box | Only used when width or height is `0` (dynamic). |
| `default IDrawable getRootDrawable()` | — | innermost non-`IIcon` `IDrawable` | Walks `getWrappedDrawable()` chain until a non-`IIcon` (or `null`) is hit. |
| `default HoverableIcon asHoverable()` | — | new `HoverableIcon(this)` | Only meaningful inside `RichText`; gives the icon its own tooltip. |
| `default InteractableIcon asInteractable()` | — | new `InteractableIcon(this)` | Only meaningful inside `RichText`; lets the icon receive clicks/input. |

`IIcon.EMPTY_2PX` is a shared constant: an empty icon forced to height 2.

**Example** (`src/main/java/com/cleanroommc/modularui/test/TestEventHandler.java:169`,
`TestGuis.java:216`):
```java
GuiTextures.MUI_LOGO.asIcon().asWidget()          // TestEventHandler.java:169
GuiTextures.MUI_LOGO.asWidget().size(20).pos(65, 65)  // TestGuis.java:216
```
and `TestTile.java:166`: `new ItemDrawable(Blocks.chest).asIcon()` used as a widget overlay.

---

### com.cleanroommc.modularui.api.drawable.IInterpolation

A single-method functional interface for interpolating between two floats along a curve.

```java
public interface IInterpolation {
    float interpolate(float a, float b, float x);
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `float interpolate(float a, float b, float x)` | `a` start, `b` end, `x` progress in `[0.0, 1.0]` | interpolated value | Pure function; curve shape is implementation-defined (e.g. linear, ease-in/out). |

**Example (constructed, not from repo)** — no usage found in `test/`; typical implementation:
```java
IInterpolation linear = (a, b, x) -> a + (b - a) * x;
float mid = linear.interpolate(0f, 10f, 0.5f); // 5.0
```
Inferred: concrete curve implementations likely live under `com.cleanroommc.modularui.utils` (e.g. an
`Interpolations` or `Easing` utility class), not confirmed by reading that package here.

---

### com.cleanroommc.modularui.api.drawable.IKey

Represents a piece of text in a GUI. The primary text-key/label API of the library; extends both
`IDrawable` (so it can be a widget background/overlay) and `IJsonSerializable`.

```java
public interface IKey extends IDrawable, IJsonSerializable {
    int TEXT_COLOR = 0xFF404040;
    TextRenderer renderer = new TextRenderer();

    IKey EMPTY = str("");
    IKey LINE_FEED = str("\n");
    IKey SPACE = str(" ");

    // EnumChatFormatting re-exports: BLACK, DARK_BLUE, ..., BOLD, ITALIC, UNDERLINE, OBFUSCATED,
    // STRIKETHROUGH, RESET, etc. (for `import static IKey.*` convenience)

    static IKey lang(@NotNull String key);
    static IKey lang(@NotNull String key, @Nullable Object... args);
    static IKey lang(@NotNull String key, @NotNull Supplier<Object[]> argsSupplier);
    static IKey lang(@NotNull Supplier<String> keySupplier);
    static IKey lang(@NotNull Supplier<String> keySupplier, @NotNull Supplier<Object[]> argsSupplier);
    static IKey str(@NotNull String key);
    static IKey str(@NotNull String key, @Nullable Object... args);
    @Deprecated static IKey format(@NotNull String key, @Nullable Object... args);
    static IKey comp(@NotNull IKey... keys);
    static IKey dynamic(@NotNull Supplier<@NotNull String> getter);
    static IKey dynamicKey(@NotNull Supplier<@NotNull IKey> getter);

    String get();
    IKey style(@Nullable EnumChatFormatting formatting);
    IKey removeStyle();
    // + many defaults below
}
```

#### Static factories

| Method | Returns | Notes |
|---|---|---|
| `lang(String key)` | `LangKey` | Translated text via Minecraft's lang file. |
| `lang(String key, Object... args)` | `LangKey` | Translated text with static format args. |
| `lang(String key, Supplier<Object[]> argsSupplier)` | `LangKey` | Translated text, args re-evaluated dynamically. |
| `lang(Supplier<String> keySupplier)` | `LangKey` | Translation key itself is dynamic. |
| `lang(Supplier<String> keySupplier, Supplier<Object[]> argsSupplier)` | `LangKey` | Both key and args dynamic. |
| `str(String key)` | `StringKey` | Literal string, not translated. |
| `str(String key, Object... args)` | `StringKey` | Literal string formatted via `String.format`; args may be dynamic objects. |
| `format(String, Object...)` | `StringKey` | **Deprecated**, renamed to `str(...)`. |
| `comp(IKey... keys)` | `CompoundKey` | Concatenates multiple keys, preserving each one's own formatting. |
| `dynamic(Supplier<String> getter)` | `DynamicKey` | Re-evaluates the raw string every access; wraps into `str(getter.get())`. |
| `dynamicKey(Supplier<IKey> getter)` | `DynamicKey` | Re-evaluates a whole `IKey` (not just a string) every access. |

#### Instance methods

| Method | Params | Returns | Notes |
|---|---|---|---|
| `String get()` | — | current unformatted string | Abstract. |
| `default String getFormatted(@Nullable FormattingState parentFormatting)` | parent formatting for composite keys | formatted string | Default just returns `get()`; concrete keys apply color/style codes. |
| `default String getFormatted()` | — | `getFormatted(null)` | |
| `default void draw(...)` (`@Override`, `@SideOnly(CLIENT)`) | context, x, y, w, h, theme | — | Delegates to `drawAligned(..., Alignment.CENTER)`. |
| `default void drawAligned(GuiContext, int x, int y, int w, int h, WidgetTheme, Alignment)` (`@SideOnly(CLIENT)`) | + alignment | — | Configures the shared `renderer` (color/shadow/alignment/scale/pos from theme) and draws `getFormatted()`. |
| `default boolean canApplyTheme()` (`@Override`) | — | `true` | Text always honors the theme color (unlike the `IDrawable` default of `false`). |
| `default int getDefaultWidth()/getDefaultHeight()` (`@Override`) | — | measured text width/height in pixels | Renders in "simulate" mode (no actual draw) via the shared `renderer` to measure. |
| `default float getScale()` | — | `1f` | Override to change render scale. |
| `default TextWidget<?> asWidget()` | — | `new TextWidget<>(this)` | Overload of `IDrawable#asWidget()` specific to text. |
| `default StyledText withStyle()` | — | `new StyledText(this)` | Entry point to the style-builder API (`alignment`, `color`, `scale`, `shadow` all delegate here). |
| `default AnimatedText withAnimation()` | — | `new AnimatedText(this)` | Wraps this key for animated text effects. |
| `default @Nullable FormattingState getFormatting()` | — | `null` by default | Formatting state of this key. |
| `IKey style(@Nullable EnumChatFormatting formatting)` | one formatting code, or `null` | `this` | Abstract. `null` clears color formatting (uses default color regardless of parent). `IKey.RESET` is applied first, then subsequent formatting. |
| `default IKey style(EnumChatFormatting... formatting)` | varargs | `this` | Calls `style(EnumChatFormatting)` for each in order. |
| `default IKey removeFormatColor()` | — | `this` | Shorthand for `style((EnumChatFormatting) null)`. |
| `IKey removeStyle()` | — | `this`(-ish) | Abstract; clears all style. |
| `default StyledText alignment(Alignment)` / `color(int)` / `color(@Nullable IntSupplier)` / `scale(float)` / `shadow(@Nullable Boolean)` | — | `StyledText` | All shorthand for `withStyle().xxx(...)`. |
| `default Icon asIcon()` (`@Override`) | — | `new Icon(this)` | Overrides `IDrawable#asIcon()` — unlike the generic version, does not force a `size(...)` call. |
| `default KeyIcon asTextIcon()` | — | `new KeyIcon(this)` | Wraps as an icon specialized for text keys. |
| `default void loadFromJson(JsonObject)` (`@Override`) | json | — | Reads `color`/`shadow`/`align`/`alignment`/`scale` fields, promoting `this` to a `StyledText` if it isn't already, and applies them. |

Constants of note: `EMPTY` = `str("")`, `LINE_FEED` = `str("\n")`, `SPACE` = `str(" ")`; also re-exports
every `net.minecraft.util.EnumChatFormatting` value (`BLACK` … `RESET`) as `IKey` constants so callers
can `import static IKey.*` for style codes.

**Example** (`src/main/java/com/cleanroommc/modularui/test/TestGuis.java:344-378`, extensive real usage):
```java
.add(IKey.GREEN + "This is a long ")
.add(IKey.str("string").style(IKey.DARK_PURPLE))
.add(" of characters" + IKey.RESET)
.add(IKey.comp(
        IKey.comp(
                IKey.str("Green Text, "),
                IKey.str("this is red").style(IKey.RED),
                IKey.str(" and this should be green again"))
            .style(IKey.GREEN),
        IKey.str(". Still underlined, "))
    .style(IKey.UNDERLINE))
.addLine(IKey.comp(IKey.str("Dynamic ").style(IKey.GOLD),
        IKey.dynamicKey(() -> IKey.str("key [%s]", IKey.str("arg").style(IKey.UNDERLINE, IKey.BLACK))
                .style(IKey.BOLD))))
```
Simple label usage everywhere, e.g. `TestGui.java:62`: `.overlay(IKey.str(line))`; dynamic text in
`GLTestGui.java:89`: `.overlay(IKey.dynamic(() -> "Type: " + ro.type.name().toLowerCase(Locale.ROOT)))`;
`asWidget()` in `TestTile.java:187`: `IKey.str("Expandable & Crafting Demo").asWidget().scale(0.7f).pos(20, 7)`.

---

### com.cleanroommc.modularui.api.drawable.IRichTextBuilder\<T extends IRichTextBuilder\<T>>

Fluent builder DSL mixed into rich-text containers (tooltips, rich text widgets). Nearly every method
is a default that delegates to an internal `getRichText()` object and returns `getThis()` for chaining.

```java
public interface IRichTextBuilder<T extends IRichTextBuilder<T>> {
    T getThis();
    IRichTextBuilder<?> getRichText();
    // all other methods are default, delegating to getRichText()
}
```

| Method(s) | Params | Notes |
|---|---|---|
| `T getThis()` | — | Abstract; CRTP self-return for chaining. |
| `IRichTextBuilder<?> getRichText()` | — | Abstract; the underlying rich-text model this builder mutates. |
| `default T reset()` | — | Removes all text and style. |
| `default T add(String s)` / `add(IDrawable drawable)` | string / drawable | Appends to current line. Non-`IIcon` drawables are converted via `IDrawable#asIcon()`; if the resulting icon has no default height, it's set to the default text height (9px); if no width, the widest tooltip line's width is used. |
| `default T addLine(String s)` / `addLine(IDrawable line)` / `addLine(ITextLine line)` | — | Adds then starts a new line (`add(...).newLine()`), or adds a pre-built `ITextLine`. |
| `default T newLine()` | — | Preferred over `"\n"` / `IKey.str("\n")` — cheaper, adds `IKey.LINE_FEED`. |
| `default T space()` | — | Adds `IKey.SPACE`. Rarely useful. |
| `default T spaceLine(int pixelSpace)` | thickness in px | Adds a blank line of given pixel height (`Spacer.of(pixelSpace)`). |
| `default T spaceLine()` | — | 2px blank line (`Spacer.SPACER_2PX`); good for titles. |
| `default T emptyLine()` | — | Blank line the height of normal text (`Spacer.LINE_SPACER`). |
| `default T addElements(Iterable<IDrawable>)` | drawables | Adds each to the current line (no line breaks). |
| `default T addDrawableLines(Iterable<IDrawable>)` / `addStringLines(Iterable<String>)` | — | Adds each element followed by a new line. |
| `default T moveCursorAfterElement(String\|Pattern regex)` | regex | Finds next element matching regex, places cursor after it; cursor goes to end if not found. |
| `default T replace(String\|Pattern regex, UnaryOperator<IKey> function)` | regex, mapper | Replaces the next matching element with `function`'s result; `null` result removes the element; cursor moves after the (possibly removed) spot; no-op if nothing matches. |
| `default T moveCursorToStart()` / `moveCursorToEnd()` | — | Cursor to very start / very end (end is default). |
| `default T moveCursorForward(int)` / `moveCursorForward()` / `moveCursorBackward(int)` / `moveCursorBackward()` | count (default 1) | Clamped at start/end. |
| `default T moveCursorToNextLine()` | — | Moves past the next element that itself ends with a line break; an element with a line break mid-way is ignored. |
| `default T lockCursor()` / `unlockCursor()` | — | While locked, cursor stops auto-advancing on add (but manual moves still work). |
| `default T clearText()` | — | Removes all text (keeps style, unlike `reset()`). |
| `default T alignment(Alignment)` / `textColor(int)` / `scale(float)` / `textShadow(boolean)` | — | Global style setters delegated to `getRichText()`. |

**Example** (`src/main/java/com/cleanroommc/modularui/test/TestEventHandler.java:104-114`, real
`RichTooltip` — `RichTooltip implements IRichTextBuilder<RichTooltip>`):
```java
event.getTooltip()
        .add(IKey.str("Powered By: ").style(IKey.GOLD, IKey.ITALIC))
        .add(GuiTextures.MUI_LOGO.asIcon().size(18)).newLine()
        .moveCursorToStart()
        .moveCursorToNextLine()
        .addLine(tooltipLine)
        .replace("Minecraft", key -> IKey.str("Chicken Jockey").style(IKey.BLUE, IKey.ITALIC))
        .moveCursorToEnd();
```
Also used for actual tooltip content building, e.g. `TestTile.java:229-237`:
```java
tooltip.addLine(IKey.str("Test Line g"));
tooltip.addLine(IKey.str("An image inside of a tooltip:"));
tooltip.addLine(GuiTextures.MUI_LOGO.asIcon().size(50).alignment(Alignment.TopCenter));
```

---

### com.cleanroommc.modularui.api.drawable.ITextLine

Low-level representation of a single measured/drawable line of rich text (used internally by
`IRichTextBuilder`'s line model, e.g. `addLine(ITextLine)`).

```java
public interface ITextLine {
    int getWidth();
    int getHeight(FontRenderer fr);
    void draw(GuiContext context, FontRenderer fr, float x, float y, int color, boolean shadow,
              int availableWidth, int availableHeight);
    Object getHoveringElement(FontRenderer fr, int x, int y);
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `int getWidth()` | — | line width in pixels | |
| `int getHeight(FontRenderer fr)` | font renderer | line height in pixels | Height can depend on the font renderer (line-height metrics). |
| `void draw(GuiContext, FontRenderer, float x, float y, int color, boolean shadow, int availableWidth, int availableHeight)` | position, color, shadow flag, available space | — | Draws the line's content. |
| `Object getHoveringElement(FontRenderer fr, int x, int y)` | font renderer, mouse-local coords | the element under the point, or presumably `null` | Used for hover/tooltip hit-testing within a line (e.g. to find which `IHoverable` icon or key segment the mouse is over). Nullability not annotated in source. |

Known implementers (from source, not in `test/`): `com.cleanroommc.modularui.drawable.text.TextLine`,
`ComposedLine`, and `Spacer` (used by `IRichTextBuilder#spaceLine()`/`emptyLine()`).

**Example (constructed, not from repo)** — no direct `test/` usage; typical consumption is indirect via
`IRichTextBuilder#addLine(ITextLine)`:
```java
richTextBuilder.addLine(Spacer.of(2)); // Spacer implements ITextLine
```

---

## api/value

All interfaces below share generic parameter `<T>` for the boxed value type, and (except `IEnumValue`)
extend `IValue<T>` (itself extending `ISyncOrValue`) — see the overview at the top of this document.

### com.cleanroommc.modularui.api.value.IValue\<T>

The base client-side value binding.

```java
public interface IValue<T> extends ISyncOrValue {
    T getValue();
    void setValue(T value);
    Class<T> getValueType();

    default boolean isValueOfType(Class<?> type);
    @Override default <V> IValue<V> castValueNullable(Class<V> valueType);
    @Override default boolean isValueHandler();
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `T getValue()` | — | current value | Abstract. |
| `void setValue(T value)` | new value | — | Abstract. |
| `Class<T> getValueType()` | — | runtime class of `T` | Abstract; needed since generics are erased. |
| `default boolean isValueOfType(Class<?> type)` (`@Override` of `ISyncOrValue`) | type | `type.isAssignableFrom(getValueType())` | |
| `default <V> IValue<V> castValueNullable(Class<V> valueType)` (`@Override`) | type | `this` cast to `IValue<V>` if `isValueOfType(valueType)`, else `null` | Unchecked cast internally, guarded by the type check. |
| `default boolean isValueHandler()` (`@Override`) | — | `true` | Distinguishes value handlers from sync handlers when both implement `ISyncOrValue`. |

**Example** (concrete implementer `com.cleanroommc.modularui.value.BoolValue`, used via
`BoolValue.Dynamic` in `src/main/java/com/cleanroommc/modularui/test/GLTestGui.java:110`):
```java
.value(new BoolValue.Dynamic(() -> ro.depth, val -> ro.depth = val))
```

---

### com.cleanroommc.modularui.api.value.ISyncOrValue

Common, non-extendable root for `IValue` and `SyncHandler`, enabling safe casting/validation without
committing to which one a widget actually holds.

```java
@ApiStatus.NonExtendable
public interface ISyncOrValue {
    ISyncOrValue EMPTY = new ISyncOrValue() { ... }; // castNullable -> null, isTypeOrEmpty -> true

    static ISyncOrValue orEmpty(@Nullable ISyncOrValue syncOrValue);
    default boolean isTypeOrEmpty(Class<?> type);
    @Nullable default <T> T castNullable(Class<T> type);
    @Nullable default <V> IValue<V> castValueNullable(Class<V> valueType);
    default <T> T castOrThrow(Class<T> type);
    default boolean isValueOfType(Class<?> type);
    default boolean isSyncHandler();
    default boolean isValueHandler();
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `static ISyncOrValue orEmpty(@Nullable ISyncOrValue)` | nullable input | input if non-null, else `EMPTY` | Null-safety helper. |
| `default boolean isTypeOrEmpty(Class<?> type)` | type | `type.isAssignableFrom(getClass())` by default | `EMPTY`'s override always returns `true` (an empty value is considered compatible with any type check). |
| `default <T> T castNullable(Class<T> type)` | type | `this` cast to `T` if assignable, else `null` | Unchecked cast; `EMPTY` always returns `null`. |
| `default <V> IValue<V> castValueNullable(Class<V> valueType)` | expected contained value type | `IValue<V>` if this is a value handler of matching type, else `null` | Base default always `null`; overridden in `IValue`. |
| `default <T> T castOrThrow(Class<T> type)` | type | `this` cast to `T` | Throws `IllegalStateException` if not castable — message distinguishes "Empty sync handler or value can't be used for anything" (neither sync nor value handler) vs. a type-mismatch message naming both classes. |
| `default boolean isValueOfType(Class<?> type)` | expected value type | `false` by default | Overridden by `IValue` to check the real contained type. |
| `default boolean isSyncHandler()` | — | `false` by default | Overridden by `SyncHandler`. |
| `default boolean isValueHandler()` | — | `false` by default | Overridden by `IValue` to `true`. |

`EMPTY`: a singleton "null object" representing "no value and no sync handler" — `castNullable` always
`null`, `isTypeOrEmpty` always `true`; used so widget code can call `ISyncOrValue.orEmpty(x)` and treat
the result uniformly instead of null-checking.

**Example (constructed, not from repo)** — internal validation pattern:
```java
ISyncOrValue binding = ISyncOrValue.orEmpty(maybeNullValueOrSyncHandler);
if (!binding.isTypeOrEmpty(Integer.class)) {
    throw new IllegalStateException("expected an int-typed value");
}
```

---

### Typed value facets

All extend `IValue<T>` (transitively `ISyncOrValue`); each pairs a getter/setter for one primitive (or,
for `IEnumValue`, exposes the enum class). Several also cross-bridge to another facet via default
methods so a single concrete class can satisfy multiple typed contracts cheaply.

#### com.cleanroommc.modularui.api.value.IBoolValue\<T>

```java
public interface IBoolValue<T> extends IValue<T>, IIntValue<T> {
    boolean getBoolValue();
    void setBoolValue(boolean val);
    @Override default int getIntValue() { return getBoolValue() ? 1 : 0; }
    @Override default void setIntValue(int val) { setBoolValue(val == 1); }
}
```
Bridges to `IIntValue<T>`: `true`/`false` map to `1`/`0`.

#### com.cleanroommc.modularui.api.value.IByteValue\<T>

```java
public interface IByteValue<T> extends IIntValue<T>, IStringValue<T> {
    void setByteValue(byte b);
    byte getByteValue();
    @Override default void setIntValue(int val) { setByteValue((byte) val); }
    @Override default int getIntValue() { return getByteValue(); }
    @Override default void setStringValue(String val) { setByteValue(Byte.parseByte(val)); }
    @Override default String getStringValue() { return String.valueOf(getByteValue()); }
}
```
Bridges to both `IIntValue<T>` (narrowing cast) and `IStringValue<T>` (via `Byte.parseByte` /
`String.valueOf`) — the string setter throws `NumberFormatException` on non-numeric input.

#### com.cleanroommc.modularui.api.value.IShortValue\<T>, IIntValue\<T>, ILongValue\<T>, IFloatValue\<T>, IDoubleValue\<T>

All four follow the same minimal shape — no bridging defaults, just a getter/setter pair:

```java
public interface IShortValue<T>  extends IValue<T> { short  getShortValue();  void setShortValue(short val); }
public interface IIntValue<T>    extends IValue<T> { int    getIntValue();    void setIntValue(int val); }
public interface ILongValue<T>   extends IValue<T> { long   getLongValue();   void setLongValue(long val); }
public interface IFloatValue<T>  extends IValue<T> { float  getFloatValue();  void setFloatValue(float val); }
public interface IDoubleValue<T> extends IValue<T> { double getDoubleValue(); void setDoubleValue(double val); }
```

#### com.cleanroommc.modularui.api.value.IStringValue\<T>

```java
public interface IStringValue<T> extends IValue<T> {
    String getStringValue();
    void setStringValue(String val);
}
```

#### com.cleanroommc.modularui.api.value.IEnumValue\<T extends Enum\<T>>

```java
public interface IEnumValue<T extends Enum<T>> extends IValue<T> {
    Class<T> getEnumClass();
}
```
Notably does **not** add its own getter/setter pair beyond `IValue<T>`'s `getValue()/setValue(T)` — it
only adds `getEnumClass()` (needed because `getValueType()` from `IValue` is erased/generic and enum
code often needs the specific `Class<T>` for e.g. `Enum.valueOf` or building a selector widget).

**Examples**: `IIntValue`/`IStringValue` via concrete `IntSyncValue` (which also implements
`IDoubleSyncValue`/`IStringSyncValue`), used in `src/main/java/com/cleanroommc/modularui/test/ItemEditorGui.java:80-97`:
```java
.value(new IntSyncValue(() -> stack.getItemDamage(), val -> getStack().setItemDamage(val)))
        .numbersInt(0, Short.MAX_VALUE - 1)
```
`IBoolValue` via `BoolValue.Dynamic`, `src/main/java/com/cleanroommc/modularui/test/GLTestGui.java:110`
(shown above under `IValue`). `IDoubleValue` via `DoubleValue.Dynamic`,
`src/main/java/com/cleanroommc/modularui/test/GLTestGui.java:100`:
```java
.value(new DoubleValue.Dynamic(() -> ro.zLevel, val -> ro.zLevel = (float) val))
```
`IEnumValue`/`IShortValue`/`IByteValue`/`ILongValue`/`IFloatValue`: no direct `test/` usage found;
concrete non-synced implementations exist as `com.cleanroommc.modularui.value.{EnumValue, ShortValue,
ByteValue, LongValue, FloatValue}` (confirmed present in the `value` package, not read in full here).

---

## api/value/sync

Synced counterparts of the `api/value` facets. Each extends `IValueSyncHandler<T>` (which extends
`IValue<T>`) plus the matching non-synced facet, and replaces the 1-arg setter with a 3-arg
`(value, setSource, sync)` form — see the package overview above for the full rationale.

### com.cleanroommc.modularui.api.value.sync.IValueSyncHandler\<T>

The sync-capable base contract; abstract base implementation is
`com.cleanroommc.modularui.value.sync.ValueSyncHandler<T, S>` (extends `SyncHandler<S>`).

```java
public interface IValueSyncHandler<T> extends IValue<T> {
    @Override default void setValue(T value) { setValue(value, true, true); }
    default void setValue(T value, boolean setSource) { setValue(value, setSource, true); }
    void setValue(T value, boolean setSource, boolean sync);

    boolean updateCacheFromSource(boolean isFirstSync);
    void notifyUpdate();
    void write(PacketBuffer buffer) throws IOException;
    void read(PacketBuffer buffer) throws IOException;
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `default void setValue(T value)` (`@Override`) | value | — | `setValue(value, true, true)` — update source and sync. |
| `default void setValue(T value, boolean setSource)` | value, setSource | — | `setValue(value, setSource, true)` — always syncs. |
| `void setValue(T value, boolean setSource, boolean sync)` | value, whether to push into the backing "source" getter/setter, whether to sync to the other side | — | Abstract; the actual mutation point every other setter overload funnels into. |
| `boolean updateCacheFromSource(boolean isFirstSync)` | `true` if this is the first tick in the UI | `true` if the cached value differed from source (and was updated) | Called every tick to detect server/source-side changes. |
| `void notifyUpdate()` | — | — | Forces a resync from source, e.g. typically implemented as `setValue(getter.get(), false, true)`. |
| `void write(PacketBuffer buffer) throws IOException` | buffer | — | Serializes current value for network sync. |
| `void read(PacketBuffer buffer) throws IOException` | buffer | — | Deserializes and applies a value received over network. |

**Example** (concrete implementer, `src/main/java/com/cleanroommc/modularui/value/sync/IntSyncValue.java`):
```java
public class IntSyncValue extends ValueSyncHandler<Integer, IntSyncValue>
        implements IIntSyncValue<Integer>, IDoubleSyncValue<Integer>, IStringSyncValue<Integer> {
    @Override
    public void setIntValue(int value, boolean setSource, boolean sync) {
        this.cache = value;
        if (setSource && this.setter != null) this.setter.accept(value);
        onValueChanged();
        if (sync) sync();
    }
    @Override
    public boolean updateCacheFromSource(boolean isFirstSync) {
        if (isFirstSync || this.getter.getAsInt() != this.cache) {
            setIntValue(this.getter.getAsInt(), false, false);
            return true;
        }
        return false;
    }
}
```
Used directly in `src/main/java/com/cleanroommc/modularui/test/TestTile.java:114-125,339-346`:
```java
IntSyncValue cycleStateValue = new IntSyncValue(() -> this.cycleState, val -> this.cycleState = val);
syncManager.getHyperVisor().syncValue("cycle_state", cycleStateValue);
syncManager.syncValue("progress", new DoubleSyncValue(() -> (double) this.progress / this.duration));
```

---

### Typed sync facets

Each of the following pairs `IValueSyncHandler<T>` with the corresponding `api/value` facet, exposing a
2-arg convenience setter (`(val, setSource)`, defaulting `sync=true`) on top of the mandatory 3-arg one.

#### com.cleanroommc.modularui.api.value.sync.IBoolSyncValue\<T>

```java
public interface IBoolSyncValue<T> extends IValueSyncHandler<T>, IBoolValue<T>, IIntSyncValue<T> {
    @Override default void setBoolValue(boolean val) { setBoolValue(val, true, true); }
    default void setBoolValue(boolean val, boolean setSource) { setBoolValue(val, setSource, true); }
    void setBoolValue(boolean value, boolean setSource, boolean sync);

    @Override default void setIntValue(int value, boolean setSource, boolean sync) {
        setBoolValue(value == 1, setSource, sync);
    }
    @Override default int getIntValue() { return IBoolValue.super.getIntValue(); }
    @Override default void setIntValue(int val) { IBoolValue.super.setIntValue(val); }
}
```
Also implements `IIntSyncValue<T>` directly (not just via `IBoolValue`'s int bridge), so ambiguous
default methods (`getIntValue`, `setIntValue(int)`) are explicitly resolved by delegating to
`IBoolValue.super`.

#### com.cleanroommc.modularui.api.value.sync.IByteSyncValue\<T>, IDoubleSyncValue\<T>, IFloatSyncValue\<T>, IIntSyncValue\<T>, ILongSyncValue\<T>, IShortSyncValue\<T>, IStringSyncValue\<T>

All six follow one shape (shown for `IIntSyncValue`, the pattern is identical for the others just
swapping the primitive/String type and the value-facet parent):

```java
public interface IIntSyncValue<T> extends IValueSyncHandler<T>, IIntValue<T> {
    @Override default void setIntValue(int val) { setIntValue(val, true, true); }
    default void setIntValue(int val, boolean setSource) { setIntValue(val, setSource, true); }
    void setIntValue(int value, boolean setSource, boolean sync);
}
```

| Interface | Extends (facet) | Primitive setter added |
|---|---|---|
| `IByteSyncValue<T>` | `IByteValue<T>`, `IValueSyncHandler<T>` | `setByteValue(byte, boolean, boolean)` |
| `IDoubleSyncValue<T>` | `IValueSyncHandler<T>`, `IDoubleValue<T>` | `setDoubleValue(double, boolean, boolean)` |
| `IFloatSyncValue<T>` | `IValueSyncHandler<T>`, `IFloatValue<T>` | `setFloatValue(float, boolean, boolean)` |
| `IIntSyncValue<T>` | `IValueSyncHandler<T>`, `IIntValue<T>` | `setIntValue(int, boolean, boolean)` |
| `ILongSyncValue<T>` | `IValueSyncHandler<T>`, `ILongValue<T>` | `setLongValue(long, boolean, boolean)` |
| `IShortSyncValue<T>` | `IValueSyncHandler<T>`, `IShortValue<T>` | `setShortValue(short, boolean, boolean)` |
| `IStringSyncValue<T>` | `IValueSyncHandler<T>`, `IStringValue<T>` | `setStringValue(String, boolean, boolean)` |

**Example** (`IIntSyncValue`/`IStringSyncValue`/`IDoubleSyncValue` all satisfied by `IntSyncValue`, real
usage `src/main/java/com/cleanroommc/modularui/test/ItemEditorGui.java:89-98`):
```java
.child(new TextFieldWidget()
        .size(30, 16)
        .value(new IntSyncValue(() -> getStack() != null ? getStack().stackSize : 0,
                value -> { if (!syncManager.isClient()) getStack().stackSize = value; }))
        .numbersInt(1, 127))
```
`IStringSyncValue` via `StringSyncValue`, `src/main/java/com/cleanroommc/modularui/test/ItemEditorGui.java:99-104`:
```java
.child(new TextFieldWidget()
        .height(20).widthRel(1f)
        .value(new StringSyncValue(() -> { /* nbt string */ }, ...)))
```
No direct `test/` usage found for `IByteSyncValue`, `IFloatSyncValue`, `ILongSyncValue`,
`IShortSyncValue` specifically; concrete classes `ByteSyncValue`, `FloatSyncValue`, `LongSyncValue`,
`ShortSyncValue` exist under `com.cleanroommc.modularui.value.sync` (confirmed present, contents not
read here).

---

### com.cleanroommc.modularui.api.value.sync.IServerKeyboardAction

Single-method callback interface, unrelated to the `IValue`/`ISyncOrValue` hierarchy. Fired
server-side when a keyboard packet arrives.

```java
public interface IServerKeyboardAction {
    void onServerKeyboardAction(KeyboardData data);
}
```

| Method | Params | Notes |
|---|---|---|
| `void onServerKeyboardAction(KeyboardData data)` | `KeyboardData` (`side`, `character`, `keycode`, `shift`, `ctrl`, `alt`; from `com.cleanroommc.modularui.utils.KeyboardData`) | Runs on whichever side received the forwarded input — check `data.side`/`data.isClient()` if the handler must behave differently per side. |

Used by `com.cleanroommc.modularui.value.sync.InteractionSyncHandler` (`setOnKeyPressed`,
`setOnKeyReleased`, `setOnKeyTapped`), which is not exercised in `test/`.

**Example (constructed, not from repo)**:
```java
new InteractionSyncHandler()
        .setOnKeyPressed(data -> {
            if (!data.isClient()) {
                // server-side reaction to a keypress forwarded from the client
            }
        });
```

---

### com.cleanroommc.modularui.api.value.sync.IServerMouseAction

Mouse-input counterpart to `IServerKeyboardAction`.

```java
public interface IServerMouseAction {
    void onServerMouseAction(MouseData mouseData);
}
```

| Method | Params | Notes |
|---|---|---|
| `void onServerMouseAction(MouseData mouseData)` | `MouseData` (`side`, `mouseButton`, `shift`, `ctrl`, `alt`; from `com.cleanroommc.modularui.utils.MouseData`) | Runs on whichever side received the forwarded input. |

Used by `InteractionSyncHandler` (`setOnMousePressed`, `setOnMouseReleased`, `setOnMouseTapped`,
`setOnMouseScroll`), not exercised in `test/`.

**Example (constructed, not from repo)**:
```java
new InteractionSyncHandler()
        .setOnMousePressed(data -> {
            if (!data.isClient() && data.mouseButton == 0) {
                // server-side reaction to a left-click forwarded from the client
            }
        });
```

---

## Summary of shorted / inferred items

- **Full treatment**: `IDrawable`, `IKey` (as requested), plus `IHoverable`, `IIcon`,
  `IInterpolation`, `IRichTextBuilder`, `ITextLine`, `ISyncOrValue`, `IValue`, `IValueSyncHandler`, and
  every value/sync typed facet — all methods documented individually or in grouped tables per file
  source.
- **Grouped rather than individually exhaustive**: the five near-identical primitive facets
  (`IShortValue`/`IIntValue`/`ILongValue`/`IFloatValue`/`IDoubleValue`) and the six near-identical sync
  facets (`IByteSyncValue`/`IDoubleSyncValue`/`IFloatSyncValue`/`IIntSyncValue`/`ILongSyncValue`/
  `IShortSyncValue`) are each shown as one representative signature plus a table of the differences,
  since they're structurally identical.
- **Constructed (non-source-confirmed) examples**: `IInterpolation`, `ITextLine`, `ISyncOrValue`,
  `IServerKeyboardAction`, `IServerMouseAction` — no exercising code found in `test/`.
- **Inferred statements**: curve-implementation location for `IInterpolation` is a guess (flagged
  inline); nullability of `ITextLine#getHoveringElement` return is not annotated in source, described
  as "presumably" nullable rather than confirmed.
- **Not read in full**: concrete classes outside the requested `api/` scope (`EnumValue`, `ShortValue`,
  `ByteValue`, `LongValue`, `FloatValue`, `ByteSyncValue`, `FloatSyncValue`, `LongSyncValue`,
  `ShortSyncValue`, `DelegateIcon`, `InteractableIcon`, `Icon`, `DrawableStack`, `TextRenderer`,
  `StyledText`, `AnimatedText`) — their existence and package location were confirmed via directory
  listing/grep, but their internals were not opened; any behavior attributed to them here is limited to
  what call sites in `test/` or the documented interfaces themselves reveal.
