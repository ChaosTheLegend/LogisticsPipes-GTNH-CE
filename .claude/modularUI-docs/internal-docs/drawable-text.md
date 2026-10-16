# `com.cleanroommc.modularui.drawable.text` — Text Rendering / Rich Text / Localization Keys

Source root: `src/main/java/com/cleanroommc/modularui/drawable/text/`

This package implements ModularUI's text subsystem:

- **Key types** (`BaseKey`, `StringKey`, `LangKey`, `CompoundKey`, `DynamicKey`) — implementations of `IKey`, a piece of text (literal, translated, composed or lazily-computed) with formatting.
- **Formatting** (`FormattingState`, `FontRenderHelper`) — Minecraft `§`-code formatting state tracking and low-level string utilities.
- **Rich text** (`RichText`, `RichTextCompiler`) — a builder for mixed text+icon paragraphs with word-wrap, and the compiler that turns that object list into drawable lines.
- **Compiled line types** (`TextLine`, `ComposedLine`, `Spacer`) — implementations of `ITextLine`, the output of `RichTextCompiler`.
- **Icon adapters** (`KeyIcon`, `TextIcon`) — implementations of `IIcon` that let an `IKey`/raw string be embedded as a fixed-size element (e.g. inside a `RichText` line).
- **Styling wrappers** (`StyledText`, `AnimatedText`) — decorators over an `IKey` adding per-instance alignment/color/scale/shadow, and a typewriter reveal animation.
- **Draw helper** (`TextRenderer`) — the actual GL/`FontRenderer` drawing code, used by `IKey.draw`, `StyledText.draw`, and `RichText.draw`.
- **`TextDrawParams`** — present in the package but effectively a dead stub (see below).

All classes are used from a single `IKey` entry point (`com.cleanroommc.modularui.api.drawable.IKey`), so it is summarized first for context.

## `com.cleanroommc.modularui.api.drawable.IKey` (context, not fully documented here)

`IKey extends IDrawable, IJsonSerializable` — a piece of GUI text. It is not in `drawable/text/` but every class below either implements it or wraps one, so its factory methods and default methods are the "public API" of this whole subsystem:

```java
static IKey lang(String key);
static IKey lang(String key, Object... args);
static IKey lang(String key, Supplier<Object[]> argsSupplier);
static IKey lang(Supplier<String> keySupplier);
static IKey lang(Supplier<String> keySupplier, Supplier<Object[]> argsSupplier);
static IKey str(String key);
static IKey str(String key, Object... args);          // String.format-style
static IKey comp(IKey... keys);
static IKey dynamic(Supplier<String> getter);
static IKey dynamicKey(Supplier<IKey> getter);

String get();                                          // unformatted text
String getFormatted(@Nullable FormattingState parentFormatting);
String getFormatted();
IKey style(@Nullable EnumChatFormatting formatting);   // null clears color
default IKey style(EnumChatFormatting... formatting);
IKey removeStyle();
default StyledText withStyle();
default AnimatedText withAnimation();
default KeyIcon asTextIcon();
default TextWidget<?> asWidget();
```

Relation to the classes documented below: `IKey.lang(...)` constructs a `LangKey`, `IKey.str(...)` a `StringKey`, `IKey.comp(...)` a `CompoundKey`, `IKey.dynamic`/`dynamicKey` a `DynamicKey`. `StringKey`, `LangKey`, `CompoundKey`, and `DynamicKey` all extend `BaseKey`, which supplies the shared `style`/`removeStyle`/`getFormatting`/`toString` implementation. Prefer the `IKey` static factories over calling these constructors directly — that is how every call site in `test/` does it (no test code constructs `StringKey`/`LangKey`/etc. directly).

---

## `com.cleanroommc.modularui.drawable.text.AnimatedText`

`StyledText` subclass that "types out" (or "types away") its text one or more characters at a time instead of drawing it all at once — a typewriter/teletype effect. Returned by `IKey.withAnimation()` (and `StyledText.withAnimation()`, which additionally carries over alignment/color/scale/shadow from the `StyledText` it's called on).

```java
public class AnimatedText extends StyledText {
    public AnimatedText(IKey key);

    @Override public String get(); // returns the currently-revealed substring, not the full text
    public void reset();           // forces re-detection of text changes on next draw

    @SideOnly(Side.CLIENT)
    @Override public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme);

    public AnimatedText startAnimation();
    public AnimatedText stopAnimation();
    public AnimatedText forward(boolean forward);
    public AnimatedText speed(int speed); // ms per character, default 40

    // covariant overrides of StyledText's fluent setters:
    @Override public AnimatedText style(EnumChatFormatting formatting);
    @Override public AnimatedText alignment(Alignment alignment);
    @Override public AnimatedText color(int color);
    @Override public AnimatedText color(@Nullable IntSupplier color);
    @Override public AnimatedText scale(float scale);
    @Override public AnimatedText shadow(@Nullable Boolean shadow);
}
```

| Member | Notes |
|---|---|
| `get()` | overridden to return `currentString` — the substring revealed so far — rather than the wrapped key's full text (which `super.get()` still exposes internally as `fullString`) |
| `reset()` | sets the cached `fullString` to `null`, forcing `draw` to re-detect the source text changed on its next call (see gotcha below) |
| `draw(...)` | on each call: if the underlying key's text (`super.get()`) differs from the cached `fullString` (or `fullString` is `null`), treats it as a "new" string — if `isAnimating()`, restarts the reveal/hide from the beginning (`currentString`/`currentIndex` reset per `forward`) and stamps `timeLastDraw`; if not animating, just snaps `currentString` to `""` (forward) or `fullString` (backward). Then calls `advance()` and skips drawing entirely (`return`) if `currentString` is empty. Otherwise delegates to `StyledText.draw(...)` with the (partial) revealed text. |
| `advance()` (private) | computes how many characters to reveal/hide since `timeLastDraw` based on `speed` (ms/char); when revealing (`forward = true`), skips space characters without consuming a "step" (extends `max` by one for each space encountered, so spaces don't visibly pause the typing); the reverse (`forward = false`) case does the same skip in the hide direction |
| `startAnimation()` / `stopAnimation()` | toggle the `isAnimating` flag; **note:** neither resets `fullString`/`currentString`, so toggling `stopAnimation()` then `startAnimation()` again on the *same* unchanged text does not restart the reveal from scratch — only a genuine text change (or explicit `reset()`) does that |
| `forward(boolean)` | `true` (default) = characters appear left-to-right (typewriter-in); `false` = characters disappear left-to-right (typewriter-out, starting fully revealed) |
| `speed(int)` | ms per character; smaller = faster |

**Gotcha (inferred, not exercised in `test/`):** when `forward = false` and the widget is drawn for the very first time with `isAnimating() == false`, `draw` executes `this.currentString = this.forward ? "" : this.fullString;` while `fullString` is still `null` (it's only ever assigned inside the `isAnimating` branch) — this would set `currentString` to `null` and NPE on the following `this.currentString.isEmpty()` check. This path only triggers if `forward(false)` is set **before** the first `startAnimation()` call ever ran a `draw` pass; no code under `test/` exercises `AnimatedText` at all, so this is source-level analysis, not a confirmed runtime bug.

**Example (constructed, not from repo — no `AnimatedText`/`withAnimation()` usage found under `test/`)**
```java
AnimatedText typewriter = IKey.str("Loading assets...").withAnimation()
        .speed(30)
        .startAnimation();
// use like any other IDrawable/ITextLine, e.g. as a TextWidget's key or a tooltip line —
// draw() will reveal one more character roughly every 30ms until the full string is shown
```

---

## `com.cleanroommc.modularui.drawable.text.BaseKey`

Abstract base for all `IKey` implementations in this package; centralizes formatting-state storage and the `style()`/`removeStyle()` contract.

```java
public abstract class BaseKey implements IKey
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getFormatted(FormattingState parentFormatting)` | parent formatting or `null` | formatted string | delegates to `FontRenderHelper.format(this.formatting, parentFormatting, get())` |
| `style(EnumChatFormatting formatting)` | formatting or `null` | `this` (`BaseKey`) | lazily allocates a `FormattingState`; `null` calls `forceDefaultColor()`, otherwise `formatting.add(f, false)` |
| `removeStyle()` | — | `this` | resets the `FormattingState` if one was allocated |
| `getFormatting()` | — | `@Nullable FormattingState` | the raw state, may be `null` if `style()` was never called |
| `toString()` | — | `String` | returns `getFormatted()` |
| `hashCode()` | — | `int` | **throws `NotImplementedException`** — subclasses must override it themselves; `BaseKey` refuses to provide a default |

Gotcha: calling `hashCode()` on a `BaseKey` subclass that doesn't override it (none of `StringKey`/`LangKey`/`CompoundKey`/`DynamicKey` do) throws at runtime. Don't put keys in hash-based collections.

### Example (constructed, not from repo)
```java
// BaseKey is abstract; used only via its subclasses, e.g.:
IKey key = IKey.str("Hello").style(IKey.BOLD); // BaseKey#style under the hood
```

---

## `com.cleanroommc.modularui.drawable.text.ComposedLine`

`ITextLine` implementation for a single wrapped line that mixes strings and icons (produced internally by `RichTextCompiler` when a line contains more than one element, or a non-string element).

```java
public class ComposedLine implements ITextLine
```

```java
public ComposedLine(List<Object> elements, int width, int height)
```
Params: `elements` — mix of `String` and `IIcon` in draw order; `width`/`height` — precomputed line box (pixels, unscaled). Purpose: holds one compiled visual line for `TextRenderer.drawCompiled`.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getWidth()` | — | `int` | the `width` passed to the constructor |
| `getHeight(FontRenderer fr)` | font renderer | `int` | `height` if it equals `fr.FONT_HEIGHT`, else `height + 1` |
| `draw(GuiContext, FontRenderer, float x, float y, int color, boolean shadow, int availableWidth, int availableHeight)` | draw context | — | draws each element left-to-right, vertically centered in the line; icons with `getWidth() <= 0` are stretched to `availableWidth`; updates `IHoverable.setRenderedAt` for hoverable icons |
| `getHoveringElement(FontRenderer, int x, int y)` | mouse coords | `Object` | returns the hovered `String`/`IIcon`, `Boolean.FALSE` if outside horizontally (signals "stop searching"), or `null` if outside vertically |
| `toString()` | — | `String` | e.g. `["foo", IconX]` |

This class is compiler-internal — no direct construction appears in `test/`; it is produced by `RichTextCompiler.newLine()` when `currentLine.size() != 1` or the sole element isn't a `String`.

### Example (constructed, not from repo)
```java
List<Object> elements = List.of("Item: ", someIcon);
ComposedLine line = new ComposedLine(elements, 40, 9);
```

---

## `com.cleanroommc.modularui.drawable.text.CompoundKey`

`IKey` implementation that concatenates multiple `IKey`s, propagating parent formatting into each child. Backs `IKey.comp(IKey...)`.

```java
public class CompoundKey extends BaseKey
```

```java
public CompoundKey(IKey... keys)
```
Params: `keys` — child keys in order; `null` or empty array is normalized to a shared empty array. Purpose: build composite text from sub-keys with independent styles.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `get()` | — | `String` | concatenation of `key.get()` for all children (unformatted) |
| `getFormatted(FormattingState parentFormatting)` | parent formatting | `String` | concatenates `key.getFormatted(FormattingState.merge(parentFormatting, getFormatting()))` — **this key's own style (via `BaseKey.style`) is merged into the parent formatting passed to every child**, so `IKey.comp(...).style(...)` colors/styles all children unless a child overrides it itself |
| `getKeys()` | — | `IKey[]` | the backing array (live reference, not a copy) |

### Example (from repo)
`src/main/java/com/cleanroommc/modularui/test/TestGuis.java:353-361`
```java
.add(IKey.comp(IKey.comp(
                IKey.str("Underline all: "),
                IKey.comp(
                                IKey.str("Green Text, "),
                                IKey.str("this is red").style(IKey.RED),
                                IKey.str(" and this should be green again"))
                        .style(IKey.GREEN),
                IKey.str(". Still underlined, "))
        .style(IKey.UNDERLINE), IKey.str("but not anymore.")))
```
Nested `IKey.comp` applies `UNDERLINE` to everything inside the outer group (including the nested green group) while the trailing `"but not anymore."` key is a sibling of the underlined group, not underlined itself.

---

## `com.cleanroommc.modularui.drawable.text.DynamicKey`

`IKey` implementation that re-evaluates a `Supplier<IKey>` every time it is read. Backs `IKey.dynamicKey(Supplier<IKey>)` (and transitively `IKey.dynamic(Supplier<String>)`, which wraps the supplied string in `IKey.str(...)`).

```java
public class DynamicKey extends BaseKey
```

```java
public DynamicKey(Supplier<IKey> supplier)
```
Params: `supplier` — invoked on every `get()`/`getFormatted()` call; if it returns `null`, `IKey.EMPTY` is substituted. Purpose: text that changes across frames/redraws (counters, live state).

| Method | Params | Returns | Notes |
|---|---|---|---|
| `get()` | — | `String` | calls the supplier once, returns `key.get()` |
| `getFormatted(FormattingState parentFormatting)` | parent formatting | `String` | calls the supplier once, returns `key.getFormatted(FormattingState.merge(parentFormatting, getFormatting()))` — own style is merged in the same way as `CompoundKey` |

Gotcha: the supplier is called separately by `get()` and `getFormatted()` — if it has side effects (as in the repo example, which increments a counter), calling both in the same frame double-counts.

### Example (from repo)
`src/main/java/com/cleanroommc/modularui/test/TestGuis.java:372-378`
```java
.addLine(IKey.comp(IKey.str("Dynamic ").style(IKey.GOLD), IKey.dynamicKey(() -> {
    int i = integer.getIntValue() + 1;
    integer.setIntValue(i);
    return IKey.str("key [%s]", IKey.str("arg")
                    .style(IKey.UNDERLINE, IKey.BLACK))
            .style(i % 30 > 5 ? IKey.RED : IKey.DARK_BLUE);
}).style(IKey.BOLD), IKey.str(" Test")))
```
Also `src/main/java/com/cleanroommc/modularui/test/GLTestGui.java:89`: `IKey.dynamic(() -> "Type: " + ro.type.name().toLowerCase(Locale.ROOT))`.

---

## `com.cleanroommc.modularui.drawable.text.FontRenderHelper`

Static utility class: fast `§`-formatting-code lookup and low-level string formatting/measuring helpers shared by the key classes and `RichTextCompiler`.

```java
public class FontRenderHelper
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getForCharacter(char c)` | formatting char (e.g. `'l'`) | `@Nullable EnumChatFormatting` | O(1) array lookup over range `'0'..'r'`; both cases accepted for letters |
| `addAfter(EnumChatFormatting[] state, EnumChatFormatting formatting, boolean removeAllOnReset)` | mutable 7-slot state array, formatting, reset-behavior flag | — | index `0` = color/reset slot, indices derived from `formatting.ordinal() - 15` for fancy styles (bold/italic/underline/strikethrough/obfuscated). Inferred: unused elsewhere in the scanned files — likely a leftover/alternate encoding to `FormattingState` |
| `format(FormattingState state, FormattingState parentState, String text)` | own state, parent state (either nullable), text | `String` | prepends a `§r` + resolved formatting codes to `text`; if both states are `null`, returns `text` unchanged |
| `formatArgs(Object[] args, FormattingState parentState, String text, boolean translate)` | format args (may contain nested `IKey`), parent state, format/translation-key string, whether to route through `I18n.format` vs `String.format` | `String` | any `IKey` argument is expanded to `parentFormat + keyFormattedText + §r + parentFormat` before formatting so nested keys don't bleed color into surrounding text |
| `getDefaultTextHeight()` | — | `int` | `FontRenderer.FONT_HEIGHT`, or `9` if no font renderer is available (e.g. server side) |
| `getFormatLength(String s, int start)` | string, start index | `int` | counts how many chars at `start` are `§`-formatting pairs (used to skip leading format codes when trimming whitespace) |

### Example (constructed, not from repo)
```java
String colored = FontRenderHelper.format(myState, parentState, "raw text");
int height = FontRenderHelper.getDefaultTextHeight(); // 9 on client
```

---

## `com.cleanroommc.modularui.drawable.text.FormattingState`

Mutable holder of one Minecraft chat-formatting "slot set" (reset/color/underline/italic/bold/strikethrough/obfuscated) plus a `forceDefaultColor` flag. Used by every `BaseKey` subclass and by `RichTextCompiler` to track formatting across a compiled string.

```java
public class FormattingState
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `reset()` | — | `void` | clears all fields including `forceDefaultColor` |
| `add(EnumChatFormatting formatting, boolean removeAllOnReset)` | formatting to add, whether `RESET` clears everything first | `void` | `RESET` is stored separately (`this.reset`); fancy styles (`isFancyStyling()`) go to their own dedicated field; everything else is treated as a color and overwrites `this.color` |
| `forceDefaultColor()` | — | `void` | sets `forceDefaultColor = true` and clears `color` — used by `BaseKey.style(null)` to explicitly drop color inheritance |
| `getFormatting()` | — | `String` | concatenation of the raw `EnumChatFormatting` codes currently set, in fixed order (reset, color, underline, italic, bold, strikethrough, obfuscated) |
| `prependText(StringBuilder builder)` / `prependText(StringBuilder builder, FormattingState fallback)` | target builder, optional fallback state | `StringBuilder` | appends resolved codes (falling back to `fallback`'s value per-field when this state's field is `null`, unless `forceDefaultColor` is set) — this is how parent-formatting inheritance is implemented |
| `setFrom(FormattingState state)` | source state | `void` | copies all fields (shallow, in-place) |
| `parseFrom(String text)` | text possibly containing `§` codes | `void` | scans for `§x` sequences and calls `add(x, true)` for each — used to re-absorb formatting embedded directly in literal strings |
| `copy()` | — | `FormattingState` | new instance via `setFrom` |
| `merge(FormattingState state)` | other state | `FormattingState` (`this`) | if `state` has a reset, this instance becomes a full copy of `state`; otherwise each non-null field of `state` overwrites the corresponding field of `this` |
| `hasReset()` | — | `boolean` | whether `reset` field is set |
| `equals` / `hashCode` / `toString` | — | — | standard value-based implementations (`toString` via `ToStringBuilder`) |

Static helpers:

| Method | Params | Returns | Notes |
|---|---|---|---|
| `merge(FormattingState state1, FormattingState state2)` | both nullable | `FormattingState` | see below |
| `merge(FormattingState state1, FormattingState state2, FormattingState result)` | both/`result` nullable | `FormattingState` | if both `null`, returns a fresh reset state (or resets `result` if given); if only one is `null`, returns the other **by reference** (no copy); otherwise merges `state2` onto a copy of `state1` (or into `result`) |
| `appendFormat(StringBuilder builder, FormattingState state)` / `(..., FormattingState fallback)` | builder, state, optional fallback | `StringBuilder` | no-op if `state == null`, else `state.prependText(builder, fallback)` |

Gotcha: `merge(state1, state2)` can return one of the input instances unmodified (aliasing) when the other argument is `null` — do not mutate the result assuming it is always a fresh object.

### Example (constructed, not from repo)
```java
FormattingState state = new FormattingState();
state.add(EnumChatFormatting.RED, false);
state.add(EnumChatFormatting.BOLD, false);
StringBuilder sb = state.prependText(new StringBuilder());
```

---

## `com.cleanroommc.modularui.drawable.text.KeyIcon`

`IIcon` adapter that wraps an `IKey` so it can be embedded as a fixed-size, single-line element (e.g. inside a `RichText`/`ComposedLine`). Returned by `IKey.asTextIcon()`.

```java
public class KeyIcon implements IIcon
```
Note (from source javadoc): "assumes the string will be a single line".

```java
public KeyIcon(IKey key)
```
Params: `key` — wrapped key. Purpose: measure/draw a key as an icon-sized box.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getFontRenderer()` | — | `FontRenderer` | override via `fontRenderer(fr)`, else `MCHelper.getFontRenderer()` |
| `getWrappedDrawable()` | — | `IKey` | the wrapped key |
| `getWidth()` | — | `int` | `0` if `expandWidth` was set, else `actualWidth()` |
| `getHeight()` | — | `int` | `0` if `expandHeight` was set, else `actualHeight()` |
| `getDefaultWidth()` / `getDefaultHeight()` | — | `int` | delegate to `key.getDefaultWidth()/getDefaultHeight()` |
| `actualWidth()` | — | `int` | `fontRenderer.getStringWidth(key.get()) + margin.horizontal()` — **uses unformatted `get()`, not `getFormatted()`**, so width excludes any effect of formatting codes on rendering (which is correct since `§` codes don't render as glyphs) |
| `actualHeight()` | — | `int` | `FONT_HEIGHT + margin.vertical()` |
| `getMargin()` | — | `Box` | **always returns `null`** (not `this.margin`) — see gotcha below |
| `getKey()` | — | `IKey` | the wrapped key |
| `draw(GuiContext, int x, int y, int width, int height, WidgetTheme)` | draw box | `void` | centers the actual-size box within `(width, height)`, then calls `key.draw(...)` at the offset position (still passing full `width`/`height` through) |

Gotcha: `getMargin()` is hard-coded to return `null` even though a private `margin` `Box` is maintained and used internally by `actualWidth()`/`actualHeight()`. Any caller reading `getMargin()` to get the configured margin will get `null`, not the box set via `margin(...)`.

Fluent setters:

| Method | Params | Returns | Notes |
|---|---|---|---|
| `expandWidth()` | — | `KeyIcon` | makes `getWidth()` report `0` (dynamic width) |
| `expandHeight()` | — | `KeyIcon` | makes `getHeight()` report `0` (dynamic height) |
| `margin(int left, int right, int top, int bottom)` | edges | `KeyIcon` | sets all four edges |
| `margin(int horizontal, int vertical)` | — | `KeyIcon` | |
| `margin(int all)` | — | `KeyIcon` | |
| `marginLeft/Right/Top/Bottom(int val)` | — | `KeyIcon` | single-edge setters |
| `fontRenderer(FontRenderer fr)` | — | `KeyIcon` | overrides the font renderer used for measurement |

### Example (from repo)
`src/main/java/com/cleanroommc/modularui/test/TestGuis.java:345-348`
```java
.add(IKey.str("string").style(IKey.DARK_PURPLE)
        .asTextIcon()
        .asHoverable()
        .addTooltipLine("Text Tooltip"))
```
`asTextIcon()` here is `IKey.asTextIcon()` → `new KeyIcon(this)`.

---

## `com.cleanroommc.modularui.drawable.text.LangKey`

`IKey` implementation for a translated (`I18n`) string, with optional (possibly dynamic) format arguments. Backs `IKey.lang(...)`.

```java
public class LangKey extends BaseKey
```

```java
public LangKey(String key)
public LangKey(String key, @Nullable Object[] args)
public LangKey(String key, Supplier<Object[]> argsSupplier)
public LangKey(Supplier<String> keySupplier)
public LangKey(Supplier<String> keySupplier, Supplier<Object[]> argsSupplier)
```
Params: `key`/`keySupplier` — translation key (literal or lazily supplied); `args`/`argsSupplier` — optional `I18n.format`/`String.format` arguments, may change every call. Purpose: localization-aware text with per-tick caching.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getKeySupplier()` | — | `Supplier<String>` | raw accessor |
| `getArgsSupplier()` | — | `Supplier<Object[]>` | raw accessor |
| `get()` | — | `String` | **cached per game tick**: if `ClientScreenHandler.getTicks()` hasn't advanced since the last call, returns the cached string; otherwise re-evaluates `I18n.format(keySupplier.get(), argsSupplier.get())` and replaces literal `\n` escapes with real newlines |
| `getFormatted(FormattingState parentFormatting)` | parent formatting | `String` | if there are no args, delegates to `BaseKey.getFormatted` (which calls `get()`, so it benefits from the tick cache); if there are args, it re-fetches the key/args and calls `FontRenderHelper.formatArgs(..., translate=true)` **every call — bypassing the tick cache** |

Gotcha: the per-tick cache in `get()` only helps the no-args / already-cached path; any `LangKey` constructed with args (or an args supplier) re-runs `I18n.format` on every `getFormatted()` call regardless of tick.

### Example (constructed, not from repo — no direct `IKey.lang(...)` calls found under `test/`)
```java
IKey title = IKey.lang("gui.mymod.title");
IKey greeting = IKey.lang("mymod.greeting", playerName);
```

---

## `com.cleanroommc.modularui.drawable.text.RichText`

Mutable builder for a paragraph of mixed text/icon/line elements with a cursor-based editing API, alignment/scale/color/shadow overrides, and hover-hit-testing. Implements `IRichTextBuilder<RichText>` (the fluent API surface) and `IDrawable`. Used internally by `RichTextWidget` and tooltip builders.

```java
public class RichText implements IDrawable, IRichTextBuilder<RichText>
```

Most of the fluent building API (`add`, `addLine`, `newLine`, `space`, `spaceLine`, `emptyLine`, `addElements`, `addDrawableLines`, `addStringLines`, `moveCursor*`, `lockCursor`/`unlockCursor`, `replace`, `alignment`, `textColor`, `scale`, `textShadow`, `clearText`, `reset`) is defined as **default methods on `IRichTextBuilder`** (see `src/main/java/com/cleanroommc/modularui/api/drawable/IRichTextBuilder.java`) and simply delegate to the methods below. Only `RichText`'s own methods are detailed here.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `isEmpty()` | — | `boolean` | true if no elements added |
| `getAsStrings()` | — | `List<String>` | lazily builds/caches a `TooltipLines` view over the raw elements (for tooltip-style plain-string consumption) |
| `getMinWidth()` | — | `int` | `max(12, widest IIcon element's getWidth())` |
| `getAlignment()` / `getShadow()` / `getColor()` / `getScale()` | — | respective type | current per-instance overrides (any of color/shadow may be `null`, meaning "use theme default") |
| `add(String s)` | string | `RichText` | inserts at cursor, advances cursor unless locked |
| `add(IDrawable drawable)` | drawable | `RichText` | if not already an `IKey`/`IIcon`, converts via `drawable.asIcon()` first |
| `addLine(ITextLine line)` | precompiled line | `RichText` | inserts the line object directly (bypasses the compiler's wrapping logic for this element) |
| `clearText()` | — | `RichText` | clears elements and resets cursor to 0 (keeps style overrides, unlike `reset()`) |
| `addAll(RichText other)` | another `RichText` | `RichText` | calls `newLine()` first, then splices in `other`'s raw element list at the cursor |
| `alignment(Alignment)` / `textColor(int)` / `scale(float)` / `textShadow(boolean)` | — | `RichText` | set the corresponding override field |
| `moveCursorAfterElement(Pattern regex)` | regex | `RichText` | finds next element whose plain text matches, cursor moves just after it; if none found, cursor moves to the end |
| `replace(Pattern regex, UnaryOperator<IKey> function)` | regex, transform | `RichText` | finds the next matching element (wrapping around once), converts it to `IKey` if it was a raw `String`, applies `function`; a `null` result **removes** the element |
| `moveCursorToStart()` / `moveCursorToEnd()` | — | `RichText` | jump to index `0` / `size - 1` |
| `moveCursorForward(int by)` / `moveCursorBackward(int by)` | delta | `RichText` | clamped to `[0, size-1]` |
| `lockCursor()` / `unlockCursor()` | — | `RichText` | toggles whether `add*` calls auto-advance the cursor |
| `moveCursorToNextLine()` | — | `RichText` | advances past the next line-ending element (`IKey.LINE_FEED`, a key/string ending in `\n`, or any `ITextLine`) |
| `insertTitleMargin(int margin)` | pixel margin | `RichText` | finds the first `IKey.LINE_FEED`, ensures the following element is a `Spacer.of(margin)` (replacing an existing `Spacer` of a different size, or inserting one) — no-op if already correct |
| `draw(GuiContext, int x, int y, int width, int height, WidgetTheme widgetTheme)` | theme-driven draw | `void` | `IDrawable` override; pulls color/shadow from the theme, delegates to the 8-arg overload |
| `draw(GuiContext, int x, int y, int width, int height, int color, boolean shadow)` | explicit color/shadow | `void` | uses the shared static `renderer` |
| `draw(TextRenderer renderer, GuiContext, int x, int y, int width, int height, int color, boolean shadow)` | explicit renderer | `void` | sets simulate=false, configures the renderer (`setupRenderer`), compiles+draws via `renderer.compileAndDraw`, caches the resulting `List<ITextLine>` for hit-testing |
| `getLastHeight()` / `getLastWidth()` | — | `int` | trimmed size from the last draw (via the static shared `renderer`) |
| `setupRenderer(TextRenderer renderer, int x, int y, float width, float height, int color, boolean shadow)` | — | `void` | applies pos/scale/color/shadow/alignment, honoring per-instance overrides (`this.color`/`this.shadow` win over the passed-in defaults) |
| `compileAndDraw(TextRenderer renderer, GuiContext, boolean simulate)` | — | `List<ITextLine>` | like the 8-arg `draw`, but lets the caller pass `simulate=true` to measure without rendering; always resets `simulate` to `false` afterward |
| `getHoveringElement(GuiContext context)` | context (must be transformed to this drawable's origin) | `Object` | delegates to the `FontRenderer`+coords overload using `context.getMouseX()/getMouseY()` |
| `getHoveringElement(FontRenderer fr, int x, int y)` | mouse coords | `Object` | walks `cachedText` (from the last draw/compile) and returns the first hovered element, or `null` if nothing was ever drawn |
| `copy()` | — | `RichText` | shallow copy: new element list contents, same cursor/alignment/scale/color/shadow |

Gotcha: `getHoveringElement` and `getLastHeight`/`getLastWidth` depend on a prior `draw`/`compileAndDraw` call populating `cachedText` — calling them beforehand returns `null`/stale/zero values.

### Example (from repo)
`src/main/java/com/cleanroommc/modularui/test/TestGuis.java:322-380` (`TestGuis.buildRichTextUI()`), via `RichTextWidget` which implements `IRichTextBuilder` by delegating to an internal `RichText`:
```java
new RichTextWidget()
        .sizeRel(1f).margin(7)
        .autoUpdate(true)
        .textBuilder(text -> text
                .add("Hello ")
                .add(new ItemDrawable(new ItemStack(Blocks.grass)).asIcon().asHoverable()
                        .tooltip(richTooltip -> richTooltip
                                .addFromItem(new ItemStack(Blocks.grass))
                                .add(IKey.GRAY + "Lorem ipsum ...")))
                .add(", nice to ")
                .add(IKey.GREEN + "This is a long ")
                .add(IKey.str("string").style(IKey.DARK_PURPLE).asTextIcon().asHoverable()
                        .addTooltipLine("Text Tooltip"))
                .add(" of characters" + IKey.RESET)
                .newLine()
                .newLine()
                .add(IKey.comp(/* ... nested styled keys ... */))
                .textShadow(false));
```
Also `src/main/java/com/cleanroommc/modularui/test/TestEventHandler.java:104-115` (`onRichTooltip`), operating on an event-supplied `RichText`/`IRichTextBuilder` (JEI tooltip):
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

**Confirmed markup/features** (from source, not invented): plain `String` and `IKey` elements (including inline `§`-formatted strings, e.g. `IKey.GREEN + "text"` since `IKey`'s formatting constants are `EnumChatFormatting` and its `toString()` yields the `§`-code), any `IDrawable`/`IIcon` embedded inline, explicit line breaks via `newLine()`/`IKey.LINE_FEED` (preferred over literal `"\n"` per the `IRichTextBuilder` javadoc), extra vertical spacing via `spaceLine(int)`/`spaceLine()`/`emptyLine()` (backed by `Spacer`), regex-based cursor navigation and in-place element replacement, and word-wrapping performed downstream by `RichTextCompiler`. There is no separate bracket/tag markup language (e.g. no `[color=red]...[/color]` parser) — styling is done by wrapping/calling Java methods on `IKey`/`RichText` before adding, not by parsing markup out of plain strings.

---

## `com.cleanroommc.modularui.drawable.text.RichTextCompiler`

Singleton (`INSTANCE`) stateful compiler that turns the raw element list from a `RichText`/`TextRenderer.compile` call into a `List<ITextLine>` (`TextLine`/`ComposedLine`/`Spacer`), performing word-wrap against a max width and tracking `§`-formatting across split lines.

```java
public class RichTextCompiler
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `compileLines(FontRenderer fr, List<Object> raw, int maxWidth, float scale)` | font renderer, raw elements, pixel max width, render scale | `List<ITextLine>` | entry point: calls `reset(fr, (int)(maxWidth/scale))` then `compile(raw)`. **Not thread-safe / not reentrant** — the compiler holds mutable instance state (`lines`, `currentLine`, `x`, `h`, `formatting`) reset at the start of each call; recommended usage is via `TextRenderer.compileAndDraw(GuiContext, List)` rather than calling this directly |
| `reset(FontRenderer fr, int maxWidth)` | font renderer (nullable, falls back to `Minecraft.getMinecraft().fontRenderer`), max width | `void` | if `maxWidth <= MIN_TEXT_WIDTH` (10), width is treated as unbounded (`Integer.MAX_VALUE`, i.e. no wrap) |
| `trimRight(String s)` (static) | string | `String` | strips trailing whitespace |
| `trimAt(String s, int start)` (static) | string, index | `String` | strips leading whitespace beginning at `start`, preserving anything before `start` |

Internal behavior (private methods `compile`, `compileString`, `newLine`, `addLineElement`, `checkNewLine`), summarized because it explains observable wrapping behavior:
- `IKey.EMPTY` elements are skipped entirely; `IKey.SPACE` is appended as a literal space without going through string-splitting logic; `IKey.LINE_FEED` forces a line break and resets the tracked `FormattingState`.
- Any other `IKey` is expanded via `key.getFormatted()` before wrapping (so styling is "baked in" as `§` codes at compile time).
- Non-`IKey`, non-`IDrawable` objects are stringified with `String.valueOf(o)`.
- `IDrawable`s that aren't already `IIcon` are converted via `asIcon()`; if the resulting icon (or the root of a `DelegateIcon` chain, if it resolves to a plain `Icon`) has no height set, it defaults to `fr.FONT_HEIGHT`. An icon wider than the max width triggers a `ModularUI.LOGGER.warn("Icon is wider than max width")` (rendering still proceeds).
- Text wrapping uses `FontRendererAccessor.invokeSizeStringToWidth` (a mixin accessor exposing MC's private word-wrap sizing) to find how many characters fit; if nothing fits at the current cursor `x`, it retries against the full width on a new line, forcing at least one character (and including a trailing `§` format code as part of that forced character if present) to avoid infinite loops.
- A finished line collapses to a `TextLine` if it ends up containing exactly one `String` element, otherwise it becomes a `ComposedLine`; trailing whitespace-only strings are dropped/trimmed when closing a line.

### Example (constructed, not from repo — `TextRenderer.compile`/`compileAndDraw` is the recommended entry point instead of calling `RichTextCompiler` directly)
```java
List<ITextLine> lines = RichTextCompiler.INSTANCE.compileLines(
        TextRenderer.getFontRenderer(), List.of("Hello ", "world"), 100, 1f);
```

---

## `com.cleanroommc.modularui.drawable.text.Spacer`

`ITextLine` implementation representing a blank vertical gap of a fixed pixel height (no text/icons). Used by `RichText.spaceLine`/`spaceLine()`/`emptyLine()` and by `RichText.insertTitleMargin`.

```java
public class Spacer implements ITextLine
```

Constants/factory:

| Member | Notes |
|---|---|
| `SPACER_2PX` | shared instance, height `2` |
| `LINE_SPACER` | shared instance, height `FontRenderHelper.getDefaultTextHeight()` (9 on client) |
| `of(int space)` (static) | returns `SPACER_2PX`/`LINE_SPACER` if `space` matches one of them, else allocates `new Spacer(space)` |

```java
protected Spacer(int space)
```
Constructor is `protected` — external code must use `Spacer.of(int)`.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getSpace()` | — | `int` | configured height |
| `getWidth()` | — | `int` | always `1` |
| `getHeight(FontRenderer fr)` | — | `int` | returns `space` (ignores `fr`) |
| `draw(...)` | — | `void` | no-op — a spacer draws nothing |
| `getHoveringElement(...)` | — | `Object` | always `null` |

### Example (from repo, via `IRichTextBuilder` defaults)
`src/main/java/com/cleanroommc/modularui/api/drawable/IRichTextBuilder.java`:
```java
default T spaceLine(int pixelSpace) {
    return addLine(Spacer.of(pixelSpace));
}
default T spaceLine() {
    return addLine(Spacer.SPACER_2PX);
}
default T emptyLine() {
    return addLine(Spacer.LINE_SPACER);
}
```
No direct call to `spaceLine`/`emptyLine` was found under `test/`; the double `.newLine().newLine()` in `TestGuis.buildRichTextUI()` (line 351-352) achieves a blank-line effect differently (two consecutive line breaks rather than a `Spacer`).

---

## `com.cleanroommc.modularui.drawable.text.StringKey`

`IKey` implementation for a literal (non-translated) string, with optional `String.format`-style arguments. Backs `IKey.str(...)`.

```java
public class StringKey extends BaseKey
```

```java
public StringKey(String string)
public StringKey(String string, @Nullable Object[] args)
```
Params: `string` — the literal/format template (non-null, `Objects.requireNonNull`); `args` — optional format arguments (normalized to `null` if empty). Purpose: the most common `IKey`, wraps a plain Java string.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `get()` | — | `String` | `string` verbatim if no args, else `String.format(string, args)` |
| `getFormatted(FormattingState parentFormatting)` | parent formatting | `String` | if no args, delegates to `BaseKey.getFormatted`; if args are present, expands any `IKey` arguments via `FontRenderHelper.formatArgs(..., translate=false)` (uses `String.format`, not `I18n.format`) before applying its own formatting |

### Example (from repo)
`src/main/java/com/cleanroommc/modularui/test/TestGuis.java:363-366`
```java
.add(IKey.str("Green, %s, %s and green again",
        IKey.str("red").style(IKey.RED),
        IKey.str("underline").style(null, IKey.UNDERLINE)
).style(IKey.GREEN))
```
Nested `IKey` arguments to `%s` keep their own style while the surrounding text is green.

---

## `com.cleanroommc.modularui.drawable.text.StyledText`

`IKey` decorator (`BaseKey` subclass) that adds a per-instance `Alignment`, color (`IntSupplier`), shadow (`Boolean`, nullable = "use theme default"), and scale on top of a wrapped `IKey`, overriding `draw` to apply them directly instead of going through the theme/default `IKey.draw`. Returned by `IKey.withStyle()`.

```java
public class StyledText extends BaseKey
```

```java
public StyledText(IKey key)
```
Params: `key` — the wrapped key. Defaults: `alignment = Alignment.Center`, `scale = 1f`, `color = null`, `shadow = null`.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `get()` | — | `String` | delegates to `key.get()` |
| `getFormatted()` | — | `String` | delegates to `key.getFormatted()` — **note: does not go through `StyledText`'s own `BaseKey.getFormatted(parent)` override path**, so any `style(...)` applied directly to the `StyledText` itself (as opposed to the wrapped `key`) has no effect on the returned text (see `style()` below) |
| `draw(GuiContext, int x, int y, int width, int height, WidgetTheme widgetTheme)` | draw box | `void` | `@SideOnly(CLIENT)`; uses the shared `BaseKey`/`IKey` `renderer` field directly; color/shadow fall back to `widgetTheme` only when `this.color`/`this.shadow` are `null` |
| `getAlignment()` / `getColor()` / `getScale()` / `isShadow()` | — | resp. type | plain getters |
| `style(EnumChatFormatting formatting)` | — | `BaseKey` (actually `StyledText`, declared covariant) | **forwards to `this.key.style(formatting)`**, i.e. mutates the *wrapped* key, not this wrapper — consistent with `getFormatted()` reading from `key` directly |
| `alignment(Alignment)` | — | `StyledText` | sets local field |
| `color(int color)` | — | `StyledText` | wraps into a constant `IntSupplier` |
| `color(IntSupplier color)` | — | `StyledText` | sets local field (nullable to clear) |
| `scale(float scale)` | — | `StyledText` | sets local field |
| `shadow(Boolean shadow)` | — | `StyledText` | sets local field (nullable = theme default) |
| `asWidget()` | — | `TextWidget<?>` | builds a `TextWidget` around the wrapped `key`, carrying over alignment/color/scale/shadow |
| `withAnimation()` | — | `AnimatedText` | builds an `AnimatedText` around the wrapped `key`, carrying over alignment/color/scale/shadow |

Gotcha: because `style(...)` on a `StyledText` mutates the wrapped `key` (not the `StyledText` instance), and `getFormatted()` bypasses `BaseKey`'s own formatting field, `StyledText`'s inherited `formatting` field (from `BaseKey`) is effectively dead for the purposes of text output — only alignment/color/scale/shadow are "this instance's own" state.

### Example (from repo, via `IKey.color(...)`/`.scale(...)` defaults which construct a `StyledText`)
`IKey` defaults (`api/drawable/IKey.java`):
```java
default StyledText color(int color) { return withStyle().color(() -> color); }
default StyledText scale(float scale) { return withStyle().scale(scale); }
```
No direct `new StyledText(...)`/`.withStyle()` call was found under `test/`; call sites there use `IKey`'s convenience defaults (e.g. `TextWidget`'s own `.scale(0.7f)` builder methods) rather than `StyledText` directly.

---

## `com.cleanroommc.modularui.drawable.text.TextDrawParams`

```java
public class TextDrawParams {
    private int line;
}
```

Note: this class has **no constructors, getters/setters, or any other members beyond the single private `int line` field**, and it is not referenced anywhere else in the codebase (only self-referential in its own file). It appears to be an unfinished/unused stub — there is nothing to document beyond its declared shape. Inferred: likely a placeholder for future per-line draw parameters that was never wired up.

---

## `com.cleanroommc.modularui.drawable.text.TextIcon`

`IIcon` implementation for a raw (already-formatted) `String` drawn at a fixed size/alignment/scale via the shared `TextRenderer.SHARED`. Unlike `KeyIcon`, it does not wrap an `IKey` — just a plain string — and unlike `KeyIcon` its width/height are fixed constructor values rather than measured.

```java
public class TextIcon implements IIcon
```

```java
public TextIcon(String text, int width, int height, float scale, Alignment alignment)
```
Params: `text` — string to draw (may already contain `§` codes); `width`/`height` — fixed box size; `scale` — render scale; `alignment` — alignment within the draw box. Purpose: cheap fixed-box text icon without `IKey` overhead.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `draw(GuiContext, int x, int y, int width, int height, WidgetTheme widgetTheme)` | draw box | `void` | `@SideOnly(CLIENT)`; configures the static shared `TextRenderer.SHARED` (pos/alignment/scale) then `drawSimple(text)` — **ignores the `widgetTheme` entirely** (no color/shadow from theme applied) and ignores the passed-in `width`/`height` draw box size for the renderer's max-width (uses only `this.width`/`this.height` fields for `getWidth()`/`getHeight()`, but note `setAlignment(this.alignment, width)` inside `draw` actually uses the *method parameter* `width`, not `this.width`) |
| `getWrappedDrawable()` | — | `null` | always `null` — does not wrap anything |
| `getWidth()` / `getHeight()` | — | `int` | fixed constructor values |
| `getMargin()` | — | `Box` | shared static empty `Box` (zero margin, all instances share it) |
| `getText()` | — | `String` | the raw text |

Gotcha: uses `TextRenderer.SHARED` (a static singleton) rather than its own `TextRenderer` instance — concurrent/re-entrant draws of two different `TextIcon`s in the same frame would stomp each other's renderer state (not an issue in MC's single-threaded render loop, but worth noting if code is refactored).

### Example (constructed, not from repo — no `TextIcon` usage found under `test/`; `test/` uses `KeyIcon` via `IKey.asTextIcon()` instead)
```java
TextIcon icon = new TextIcon("§aOK", 20, 9, 1f, Alignment.Center);
```

---

## `com.cleanroommc.modularui.drawable.text.TextLine`

`ITextLine` implementation for a single already-wrapped line consisting of exactly one plain `String` (no embedded icons). Produced internally by `RichTextCompiler.newLine()` when a compiled line reduces to a single string element.

```java
public class TextLine implements ITextLine
```

```java
public TextLine(String text, int width)
```
Params: `text` — the line's full text (may contain `§` codes); `width` — precomputed pixel width. Purpose: cheapest representation of a compiled text line.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getWidth()` | — | `int` | constructor value |
| `getHeight(FontRenderer fr)` | font renderer | `int` | `fr.FONT_HEIGHT + 1` |
| `draw(GuiContext, FontRenderer fr, float x, float y, int color, boolean shadow, int availableWidth, int availableHeight)` | draw args | `void` | calls `Platform.setupDrawFont()` then `fr.drawString(text, (int)x, (int)y, color, shadow)`; records `lastX`/`lastY` for hover testing |
| `getHoveringElement(FontRenderer fr, int x, int y)` | mouse coords | `Object` | `null` if outside vertically; `Boolean.FALSE` if outside horizontally; else returns `this.text` |
| `toString()` | — | `String` | returns `text` |

### Example (constructed, not from repo — internal compiler output; see `RichTextCompiler.newLine()`)
```java
TextLine line = new TextLine("Hello world", fr.getStringWidth("Hello world"));
```

---

## `com.cleanroommc.modularui.drawable.text.TextRenderer`

The core GL/`FontRenderer` text-drawing helper: a stateful, reusable object configured with position/alignment/scale/color/shadow/max-size, then told to draw either a raw string, a list of pre-split lines, or a compiled rich-text element list (via `RichTextCompiler`). Used directly by `IKey`'s default `draw`/`drawAligned`/`getDefaultWidth`/`getDefaultHeight`, by `StyledText.draw`, and by `RichText.draw`.

```java
public class TextRenderer
```

Static: `SHARED` — a shared instance used by `TextIcon` (and available for other ad-hoc single-shot draws).

Configuration setters (fluent-style but `void`-returning, so must be called then acted on, not chained):

| Method | Params | Notes |
|---|---|---|
| `setAlignment(Alignment alignment, float maxWidth)` | 2-arg overload | `maxHeight` defaults to `-1` |
| `setAlignment(Alignment alignment, float maxWidth, float maxHeight)` | full overload | negative width/height = unbounded |
| `setShadow(boolean shadow)` | — | |
| `setScale(float scale)` | — | |
| `setPos(int x, int y)` | — | |
| `setColor(int color)` | — | ARGB int |
| `setHardWrapOnBorder(boolean hardWrapOnBorder)` | default `true` | if `false`, `draw(String)` skips wrapping unless the text contains a literal `"\n'"` (note: source checks for the 3-char substring `\n'`, not just `\n` — likely a typo, see gotcha) |
| `setSimulate(boolean simulate)` | default `false` | when `true`, layout is computed (sizes updated) but nothing is actually rendered — used for measurement (`IKey.getDefaultWidth/Height`) |

Gotcha: `draw(String text)`'s wrap-skip condition is `!text.contains("\n'")` (backslash-n followed by a literal apostrophe) rather than `!text.contains("\n")`. As written, a plain newline without a following `'` character will still take the `drawSimple` path even when `hardWrapOnBorder` is true and `maxWidth <= 0`— this only matters when `maxWidth <= 0` anyway (unbounded width), where wrapping wouldn't trigger regardless, so the apparent typo is likely benign in practice. Inferred: dead/harmless condition rather than an active bug, but worth flagging as unusual.

Drawing/measuring methods:

| Method | Params | Returns | Notes |
|---|---|---|---|
| `draw(String text)` | text | `void` | picks `drawSimple` (single line, no wrap) vs. wrapping path based on `maxWidth`/`hardWrapOnBorder` (see gotcha above) |
| `draw(List<String> lines)` | pre-split lines | `void` | `measureLines` then `drawMeasuredLines` |
| `drawMeasuredLines(List<Line> measuredLines)` | protected | `void` | draws each line, stacking vertically by `getFontHeight()`; updates `lastActualWidth/Height` and `lastTrimmedWidth/Height` (`trimmed = max(0, actual - scale)`) |
| `drawSimple(String text)` | text | `void` | single-line fast path, no wrapping |
| `measureLines(List<String> lines)` | raw lines | `List<Line>` | applies `wrapLine` per input line if `hardWrapOnBorder` |
| `compile(List<Object> rawText)` | mixed elements | `List<ITextLine>` | delegates to `RichTextCompiler.INSTANCE.compileLines(getFontRenderer(), rawText, (int) maxWidth, scale)` |
| `compileAndDraw(GuiContext context, List<Object> raw)` | mixed elements | `List<ITextLine>` | compiles then `drawCompiled`; **this is the "recommended usage" entry point** called out in `RichTextCompiler`'s class javadoc |
| `drawCompiled(GuiContext context, List<ITextLine> lines)` | compiled lines | `void` | computes total height/max width across lines; if not simulating, pushes a GL matrix, translates to `(x,y)`, applies `scale`, draws each line via `ITextLine.draw`, pops the matrix; updates `lastActualWidth` (clamped to `maxWidth` if set) / `lastActualHeight` / trimmed variants |
| `drawCut(String text)` | single-line text | `void` | throws `IllegalArgumentException` if `text` contains `"\n"` ("Scrolling text can't wrap!"); else delegates to `drawCut(Line)` |
| `drawCut(Line line)` | pre-measured line | `void` | if the line is wider than `maxWidth`, trims it with `FontRenderer.trimStringToWidth(text, maxWidth - 6)` and appends `"..."` |
| `drawScrolling(Line line, float progress, Area area, GuiContext context)` | line, `0..1` scroll progress, clip area, context | `void` | if the line fits, just draws it; otherwise applies a `Stencil` clip over `area`/`maxWidth` and translates by `(width - maxWidth) * progress` to simulate marquee scrolling |
| `wrapLine(String line)` | line | `List<String>` | if `maxWidth > 0`, uses `FontRenderer.listFormattedStringToWidth` with `wrapWidth = max(10, maxWidth/scale)` (min 10 to avoid render-time stack overflow per source comment); else returns the line unwrapped as a singleton list |
| `wouldFit(List<String> text, boolean shouldCheckWidth)` | candidate lines, whether to also check width | `boolean` | checks `maxHeight` against line count × font height, and optionally each line's width against `maxWidth` |
| `getMaxWidth(List<String> lines)` | lines | `int` | widest measured line width, ceiling-rounded; `0` for empty input |
| `line(String text)` | text | `Line` | measures via `getFontRenderer().getStringWidth(text) * scale` |
| `getColor()` / `getScale()` / `getAlignment()` / `getX()` / `getY()` / `getFontHeight()` | — | resp. type | plain getters; `getFontHeight()` = `FONT_HEIGHT * scale` |
| `getLastActualWidth()` / `getLastActualHeight()` / `getLastTrimmedWidth()` / `getLastTrimmedHeight()` | — | `float` | results of the most recent draw/measure call |
| `getFontRenderer()` (static) | — | `FontRenderer` | `@SideOnly(CLIENT)`; `Minecraft.getMinecraft().fontRenderer` |

Inner class `TextRenderer.Line` — an immutable measured line:
```java
public static class Line {
    public Line(String text, float width)
    public String getText();
    public float getWidth();
    public int upperWidth();  // (int)(width + 1)
    public int lowerWidth();  // (int) width
}
```

### Example (from repo, indirectly via `IKey`'s default `draw`)
`api/drawable/IKey.java`:
```java
TextRenderer renderer = new TextRenderer();
// ...
default void drawAligned(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme, Alignment alignment) {
    renderer.setColor(widgetTheme.getTextColor());
    renderer.setShadow(widgetTheme.getTextShadow());
    renderer.setAlignment(alignment, width, height);
    renderer.setScale(getScale());
    renderer.setPos(x, y);
    renderer.draw(getFormatted());
}
```
Every `IKey.draw(...)` call in `test/` (e.g. `IKey.str("Menu").asWidget()` rendering inside `TextWidget`) ultimately runs through this `TextRenderer` instance.
