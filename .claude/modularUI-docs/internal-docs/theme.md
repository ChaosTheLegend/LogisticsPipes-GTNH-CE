# `theme` package reference

Package: `com.cleanroommc.modularui.theme`

The JSON-driven visual theming system. A **theme** (`ITheme`/`Theme`/`DefaultTheme`) is a named bundle of **widget themes** (`WidgetTheme` and its subclasses `SlotTheme`, `TextFieldTheme`, `SelectableTheme`), each keyed by a `WidgetThemeKey`. Themes are defined as JSON (`assets/<domain>/themes/<id>.json`, discovered via `themes.json` index files) and/or in Java via `ThemeBuilder`/`WidgetThemeBuilder`, merged and re-parsed on every `/reloadThemes` or resource reload. Resourcepack JSON always wins over Java-registered defaults unless a theme sets `"override": true`.

**Core relationship (read this first):**

- `WidgetThemeKey<T extends WidgetTheme>` — a typed, named, globally-registered identifier (e.g. `IThemeApi.BUTTON`, declared in `com.cleanroommc.modularui.api.IThemeApi`). It knows its JSON key name, its Java type `T`, its default (non-hover) and default-hover `T` instances, and a `WidgetThemeParser<T>` that turns JSON into `T`. Keys are registered once, at classload, via `ThemeAPI.INSTANCE.registerWidgetTheme(...)` (usually through `WidgetThemeKeyBuilder`).
- `WidgetTheme` (and subclasses) — the actual immutable data object: background `IDrawable`, colors, text shadow flag, default width/height. One instance = one visual state (normal *or* hovered).
- `WidgetThemeEntry<T>` — pairs a `WidgetThemeKey<T>` with the two `T` instances (normal + hover) that a *specific* `ITheme` resolved for that key.
- `ITheme` (`Theme` / `DefaultTheme`, both extending `AbstractTheme`) — a theme instance holds a `WidgetThemeMap` mapping every registered `WidgetThemeKey` to its `WidgetThemeEntry`, and falls back to its parent theme's entry for keys it didn't define.
- A `Widget` (see `widget/Widget.java`, out of scope here) normally has a hardcoded default key for its widget type; calling `.widgetTheme(WidgetThemeKey<?>)` / `.widgetTheme(String)` on a widget instance *overrides* which key it looks itself up with in the active `ITheme` at draw time. This is exactly what `TestGui.java` does with `.widgetTheme(IThemeApi.BUTTON)` — it tells a plain `Widget` to render using the button widget theme instead of its own default.
- `WidgetThemeBuilder<T, B>` — a `JsonBuilder` subclass for constructing a widget theme's JSON in Java (passed to `ThemeBuilder.widgetTheme(key, builder)`); `ThemeManager` parses that JSON back into `T` through the key's `WidgetThemeParser` on theme load.

This doc also lightly touches `com.cleanroommc.modularui.api.ITheme` and `com.cleanroommc.modularui.api.IThemeApi` since `Theme`/`DefaultTheme` implement the former and `ThemeAPI` implements the latter.

---

## `com.cleanroommc.modularui.api.IThemeApi` (cross-reference only)

Not in `theme/`, but every widget theme key constant (`IThemeApi.BUTTON`, `IThemeApi.TEXT_FIELD`, etc.) and every JSON property name constant (`IThemeApi.COLOR`, `IThemeApi.BACKGROUND`, ...) lives here. `ThemeAPI` is the sole implementation (`ThemeAPI.INSTANCE`, obtained via `IThemeApi.get()`).

```java
@ApiStatus.NonExtendable
public interface IThemeApi {
    WidgetThemeKey<WidgetTheme> FALLBACK, PANEL, BUTTON, CLOSE_BUTTON, SCROLLBAR;
    WidgetThemeKey<SlotTheme> ITEM_SLOT, FLUID_SLOT, ITEM_SLOT_PLAYER, ITEM_SLOT_PLAYER_HOTBAR, ITEM_SLOT_PLAYER_MAIN_INV, ITEM_SLOT_PLAYER_ARMOR;
    WidgetThemeKey<TextFieldTheme> TEXT_FIELD;
    WidgetThemeKey<SelectableTheme> TOGGLE_BUTTON;
    String HOVER_SUFFIX = ":hover";
    String PARENT, DEFAULT_WIDTH, DEFAULT_HEIGHT, BACKGROUND, COLOR, TEXT_COLOR, TEXT_SHADOW, ICON_COLOR,
           SLOT_HOVER_COLOR, SLOT_CUSTOM_TEXTURES, SLOT_INVENTORY_BACKGROUND, SLOT_HOTBAR_BACKGROUND,
           MARKED_COLOR, HINT_COLOR, SELECTED_BACKGROUND, SELECTED_COLOR, SELECTED_TEXT_COLOR,
           SELECTED_TEXT_SHADOW, SELECTED_ICON_COLOR; // JSON property name constants

    static IThemeApi get(); // returns ThemeAPI.INSTANCE
    ITheme getDefaultTheme();
    @NotNull ITheme getTheme(String id);
    boolean hasTheme(String id);
    void registerTheme(String id, JsonBuilder json);
    default void registerTheme(ThemeBuilder<?> themeBuilder);
    List<JsonBuilder> getJavaDefaultThemes(String id);
    ITheme getThemeForScreen(String owner, String name, @Nullable String panel, @Nullable String defaultTheme, @Nullable String fallbackTheme);
    default ITheme getThemeForScreen(String owner, String name, @Nullable String defaultTheme, @Nullable String fallbackTheme);
    default ITheme getThemeForScreen(ModularPanel panel, @Nullable String defaultTheme);
    default ITheme getThemeForScreen(ModularScreen screen, @Nullable String defaultTheme);
    void registerThemeForScreen(String screen, String theme);
    default void registerThemeForScreen(String owner, String name, String theme);
    <T extends WidgetTheme> WidgetThemeKey<T> registerWidgetTheme(String id, T defaultTheme, T defaultHoverTheme, WidgetThemeParser<T> parser);
    default <T extends WidgetTheme> WidgetThemeKeyBuilder<T> widgetThemeKeyBuilder(String id, Class<T> type);
    @UnmodifiableView List<WidgetThemeKey<?>> getWidgetThemeKeys();
}
```

Notes:
- `ITEM_SLOT_PLAYER`, `..._PLAYER_HOTBAR`, `..._PLAYER_MAIN_INV`, `..._PLAYER_ARMOR` are **sub widget themes** created with `ITEM_SLOT.createSubKey(...)` — see `WidgetThemeKey.createSubKey`.
- `CLOSE_BUTTON` is deliberately its own top-level key, not a sub key of `BUTTON` — its comment in source says "shouldn't inherit from button".
- `widgetThemeKeyBuilder(id, type)` ignores its `type` parameter in the current implementation (`new WidgetThemeKeyBuilder<>(id)`); the actual type is inferred later from `defaultTheme.getClass()`.

## `com.cleanroommc.modularui.api.ITheme` (cross-reference only)

Implemented by `Theme`/`DefaultTheme` via `AbstractTheme`.

```java
public interface ITheme {
    static ITheme getDefault(); // IThemeApi.get().getDefaultTheme()
    static ITheme get(String id);
    String getId();
    ITheme getParentTheme();
    @UnmodifiableView Collection<WidgetThemeEntry<?>> getWidgetThemes();
    WidgetThemeEntry<WidgetTheme> getFallback();
    WidgetThemeEntry<WidgetTheme> getPanelTheme();
    WidgetThemeEntry<WidgetTheme> getButtonTheme();
    WidgetThemeEntry<WidgetTheme> getScrollbarTheme();
    WidgetThemeEntry<SlotTheme> getItemSlotTheme();
    WidgetThemeEntry<SlotTheme> getFluidSlotTheme();
    WidgetThemeEntry<TextFieldTheme> getTextFieldTheme();
    WidgetThemeEntry<SelectableTheme> getToggleButtonTheme();
    <T extends WidgetTheme> WidgetThemeEntry<T> getWidgetTheme(WidgetThemeKey<T> key);
}
```

---

## `com.cleanroommc.modularui.theme.WidgetThemeKey<T extends WidgetTheme>`

The typed identifier described above. Instances self-register into a static registry on construction — there is no separate "registration" step once you have an instance.

```java
public class WidgetThemeKey<T extends WidgetTheme> implements Comparable<WidgetThemeKey<?>>
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getFromFullName(String)` *(static)* | full name (`name` or `name:subName`) | `@Nullable WidgetThemeKey<?>` | Global lookup table populated by every constructor call. |
| `createSubKey(String subName)` | sub name | `WidgetThemeKey<T>` | Shortcut for `createSubKey(subName, null, null)`. |
| `createSubKey(String subName, T defaultValue, T defaultHoverValue)` | sub name, optional defaults (fall back to parent's if `null`) | `WidgetThemeKey<T>` | Creates/looks up a **sub widget theme** key sharing this key's `name` and JSON parser, but its own `subName` and defaults. If a key for `name:subName` already exists with the same type, returns it instead of creating a duplicate; if it exists with a *different* type, throws `IllegalStateException`. |
| `getParent()` | - | `@Nullable WidgetThemeKey<T>` | Non-null only for sub keys. |
| `getWidgetThemeType()` | - | `Class<T>` | |
| `getName()` / `getSubName()` | - | `String` / `@Nullable String` | |
| `getFullName()` | - | `String` | `name` or `name:subName`; this is the JSON object key used inside theme files. |
| `getDefaultValue()` / `getDefaultHoverValue()` | - | `T` | Used by `DefaultTheme` and as ultimate fallback during JSON parsing. |
| `getParser()` | - | `WidgetThemeParser<T>` | |
| `isSubWidgetTheme()` | - | boolean | `getParent() != null`. Sub widget themes inherit from their **parent widget theme key's entry within the same theme**, not from the enclosing theme's parent theme (see `ThemeManager`'s `parse` gotcha below). |
| `isCompatible(WidgetTheme theme)` | instance | boolean | `type.isInstance(theme)`. |
| `isExactType(WidgetTheme theme)` | instance | boolean | Exact class match (not subclass). |
| `isOfType(Class<? extends WidgetTheme>)` | candidate supertype | boolean | `type.isAssignableFrom(this.type)`. |
| `cast(WidgetTheme theme)` | instance | `T` | `type.cast(theme)`. |
| `equals` / `hashCode` | - | - | Based on `(name, subName)` only — **not** identity, so two `new WidgetThemeKey` for the same full name would collide (in practice keys are only ever constructed through `ThemeAPI.registerWidgetTheme`/`createSubKey`, which guards against duplicates). |
| `compareTo(WidgetThemeKey<?>)` | other key | int | Orders non-sub keys before sub keys, then ancestors before descendants, then alphabetically by `name`. |

**Gotcha — id validation:** constructing a *top-level* key is only possible via `ThemeAPI.registerWidgetTheme`, which requires the id to match `[a-zA-Z0-9$_-]+` and rejects duplicates; `createSubKey` has no such pattern check.

**Example (from `com.cleanroommc.modularui.api.IThemeApi`):**
```java
WidgetThemeKey<SlotTheme> ITEM_SLOT = get().widgetThemeKeyBuilder("itemSlot", SlotTheme.class)
        .defaultTheme(new SlotTheme(GuiTextures.SLOT_ITEM))
        .register();
WidgetThemeKey<SlotTheme> ITEM_SLOT_PLAYER = ITEM_SLOT.createSubKey("player");
```

---

## `com.cleanroommc.modularui.theme.WidgetThemeKeyBuilder<T extends WidgetTheme>`

Fluent helper to build and register a `WidgetThemeKey`; this is the normal way to declare a new widget theme type instead of calling `ThemeAPI.registerWidgetTheme` directly.

```java
public class WidgetThemeKeyBuilder<T extends WidgetTheme>
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `WidgetThemeKeyBuilder(String id)` | JSON/registry id | - | Constructor. |
| `defaultTheme(T)` | default (non-hover) instance | `this` | Required — `register()` throws `NullPointerException` (via `Objects.requireNonNull`) if omitted. |
| `defaultHoverTheme(T)` | default hover instance | `this` | Optional; see gotcha below. |
| `parser(WidgetThemeParser<T>)` | parser fn | `this` | Optional; see gotcha below. |
| `register()` | - | `WidgetThemeKey<T>` | Finalizes and calls `IThemeApi.get().registerWidgetTheme(...)`. |

**Gotchas:**
- If `parser` is not set, one is built via reflection, requiring `T` to declare a public constructor `T(T parent, JsonObject json, JsonObject fallback)`; otherwise `register()` throws `RuntimeException`.
- If `defaultHoverTheme` is not set, it's derived by calling `defaultTheme.withNoHoverBackground()`; if the concrete `WidgetTheme` subclass didn't override `withNoHoverBackground()` to return its own type, `register()` throws `IllegalArgumentException` ("method withNoHoverBackground() is not override to create its type").
- The `type` parameter passed to `IThemeApi.widgetThemeKeyBuilder(id, type)` is unused; the real type comes from `defaultTheme.getClass()`.

**Example (from `com.cleanroommc.modularui.api.IThemeApi`):**
```java
WidgetThemeKey<WidgetTheme> BUTTON = get().widgetThemeKeyBuilder("button", WidgetTheme.class)
        .defaultTheme(WidgetTheme.whiteTextShadow(18, 18, GuiTextures.MC_BUTTON))
        .defaultHoverTheme(WidgetTheme.whiteTextShadow(18, 18, GuiTextures.MC_BUTTON_HOVERED))
        .register();
```

---

## `com.cleanroommc.modularui.theme.WidgetThemeParser<T extends WidgetTheme>`

```java
@FunctionalInterface
public interface WidgetThemeParser<T extends WidgetTheme> {
    @NotNull T parse(T parent, JsonObject json, JsonObject fallback);
}
```

Converts JSON into a `T`. `parent` is the widget theme this one inherits unset properties from (either the parent *theme's* same key, or — for sub widget themes — the same theme's parent widget theme key). `fallback` is a secondary JSON object consulted before `parent` for a handful of properties (see `WidgetTheme`'s JSON constructor). In practice this is almost always implemented as a method reference to a `WidgetTheme` subclass's `(parent, json, fallback)` constructor, e.g. `SlotTheme::new`.

---

## `com.cleanroommc.modularui.theme.WidgetThemeEntry<T extends WidgetTheme>`

Immutable pair of (normal, hover) `T` instances for one `WidgetThemeKey`, as resolved by one `ITheme`.

```java
public class WidgetThemeEntry<T extends WidgetTheme>
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `WidgetThemeEntry(WidgetThemeKey<T> key, T theme)` | key, single theme | - | Uses `theme` for both normal and hover. |
| `WidgetThemeEntry(WidgetThemeKey<T> key, T theme, T hoverTheme)` | key, both themes | - | Main constructor. |
| `getKey()` | - | `WidgetThemeKey<T>` | |
| `getTheme()` | - | `T` | Normal (non-hover). |
| `getHoverTheme()` | - | `T` | |
| `getTheme(boolean hover)` | hover flag | `T` | `hover ? hoverTheme : theme`. |
| `expectType(Class<F> expectedType)` | expected `WidgetTheme` subtype | `WidgetThemeEntry<F>` (unchecked cast) | Throws `IllegalStateException` if `key.isOfType(expectedType)` is false. Used defensively where code expects e.g. a `SlotTheme` entry. |

---

## `com.cleanroommc.modularui.theme.WidgetThemeMap`

```java
public class WidgetThemeMap extends Object2ObjectOpenHashMap<WidgetThemeKey<?>, WidgetThemeEntry<?>>
```

Type-safety wrapper (fastutil map) used internally by `DefaultTheme`, `Theme`, and `ThemeManager` to store one theme's resolved entries.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `put(WidgetThemeKey<?>, WidgetThemeEntry<?>)` | key, entry | previous entry | **Overridden** to throw `IllegalArgumentException` if `widgetThemeKey != widgetTheme.getKey()` (reference-inequality check) — guards against inserting an entry under the wrong key. |
| `putTheme(WidgetThemeKey<T>, WidgetThemeEntry<T>)` | typed key, typed entry | previous entry, cast to `WidgetThemeEntry<T>` | Type-safe variant of `put`. |
| `getTheme(WidgetThemeKey<T>)` | typed key | `WidgetThemeEntry<T>` (cast) | Type-safe variant of `get`. |

Inferred: not part of any public API surface meant for mod authors beyond reading `ITheme.getWidgetThemes()`; exists purely to avoid unchecked casts scattered through `theme/` internals.

---

## `com.cleanroommc.modularui.theme.WidgetTheme`

The base "look" data object: size defaults, background drawable, primary/text/icon colors, text shadow flag. All fields are `final`; every mutation method returns a new instance.

```java
public class WidgetTheme
```

### Constructors / factories

| Signature | Notes |
|---|---|
| `WidgetTheme(int defaultWidth, int defaultHeight, @Nullable IDrawable background, int color, int textColor, boolean textShadow, int iconColor)` | Main Java constructor. If `textColor == 0`, it's replaced with `color`; same for `iconColor`. `0` (fully transparent black / ARGB 0) is treated as "unset, use `color`". |
| `WidgetTheme(WidgetTheme parent, JsonObject json, JsonObject fallback)` | JSON-parsing constructor (this is what most `WidgetThemeParser` implementations delegate to / mirror). Reads `w`/`width`, `h`/`height`, `background`/`bg`, `color`, `textColor`, `textShadow`, `iconColor`. See gotcha below for the `fallback`/`inherit` interaction. |
| `whiteTextShadow(int w, int h, @Nullable IDrawable background)` *(static)* | `color=WHITE, textColor=WHITE, textShadow=true, iconColor=WHITE`. |
| `darkTextNoShadow(int w, int h, @Nullable IDrawable background)` *(static)* | `color=WHITE, textColor=TEXT_COLOR_DARK, textShadow=false, iconColor=WHITE`. |
| `getDefault()` *(static)* | Returns `ThemeAPI.DEFAULT_THEME.getFallback()`, i.e. the `WidgetThemeEntry<WidgetTheme>` for `IThemeApi.FALLBACK` in the built-in default theme. |

### Getters / other methods

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getDefaultWidth()` / `getDefaultHeight()` | - | `int` | |
| `getBackground()` | - | `@Nullable IDrawable` | |
| `getColor()` / `getTextColor()` / `getIconColor()` | - | `int` (ARGB) | |
| `getTextShadow()` | - | `boolean` | |
| `withNoHoverBackground()` | - | `WidgetTheme` | Copy with `background = IDrawable.NONE`, everything else unchanged. **Must be overridden** by subclasses to return the subclass type — see `WidgetThemeKeyBuilder` gotcha. |

**Gotcha — `inherit` JSON array:** the JSON constructor reads an optional `"inherit"` property (string or array of strings) naming which of `color`/`textColor`/`textShadow`/`iconColor` should skip the `fallback` object and inherit straight from `parent`. Everything not listed there still consults `fallback` first, then `parent`.

**Example (constructed, not from repo — how `IThemeApi.BUTTON`'s defaults are declared):**
```java
WidgetTheme normal = WidgetTheme.whiteTextShadow(18, 18, GuiTextures.MC_BUTTON);
WidgetTheme hovered = WidgetTheme.whiteTextShadow(18, 18, GuiTextures.MC_BUTTON_HOVERED);
```

---

## `com.cleanroommc.modularui.theme.WidgetThemeBuilder<T extends WidgetTheme, B extends WidgetThemeBuilder<T, B>>`

`JsonBuilder` subclass for building one widget theme's JSON blob in Java. Meant to be subclassed per `WidgetTheme` subtype (see `SlotTheme.Builder`, `TextFieldTheme.Builder`, `SelectableTheme.Builder`).

```java
public class WidgetThemeBuilder<T extends WidgetTheme, B extends WidgetThemeBuilder<T, B>> extends JsonBuilder
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `defaultWidth(int)` / `defaultHeight(int)` | value | `B` | Writes `defaultWidth`/`defaultHeight`. |
| `color(int)` / `textColor(int)` / `iconColor(int)` | ARGB color | `B` | |
| `textShadow(int)` | *(sic — takes `int`, not `boolean`)* | `B` | Writes `textShadow` as whatever numeric value is passed; downstream JSON parsing (`JsonHelper.getBoolWithFallback`) expects a boolean-compatible value — pass `0`/`1` or note this looks like a latent API wart. |
| `background(JsonBuilder)` / `background(IDrawable)` / `background(String textureId)` | drawable spec | `B` | `background(String)` builds `{"type":"texture","id":textureId}`; `background(IDrawable)` uses `DrawableSerialization.serialize(...)`. |

Used via `ThemeBuilder.widgetTheme(key, builder)` / `widgetThemeHover(key, builder)`, not directly registered — the resulting JSON is merged into the enclosing theme's JSON under the key's full name (or `fullName + ":hover"`).

**Example (adapted from `test/TestEventHandler.java`):**
```java
new SelectableTheme.Builder<>()
        .color(Color.BLUE_ACCENT.brighter(0))
        .selectedColor(Color.WHITE.main)
        .selectedIconColor(Color.RED.brighter(0));
```

---

## `com.cleanroommc.modularui.theme.SlotTheme`

`WidgetTheme` subclass for item/fluid slot widgets; adds a hover-highlight color.

```java
public class SlotTheme extends WidgetTheme
```

| Constructor | Notes |
|---|---|
| `SlotTheme(IDrawable background)` | `slotHoverColor = Color.withAlpha(WHITE, 0x60)`. |
| `SlotTheme(IDrawable background, int slotHoverColor)` | `18x18`, `color=WHITE`, `textColor=0xFF404040`, `textShadow=false`, `iconColor=WHITE`. |
| `SlotTheme(int defaultWidth, int defaultHeight, @Nullable IDrawable background, int color, int textColor, boolean textShadow, int iconColor, int slotHoverColor)` | Full constructor. |
| `SlotTheme(SlotTheme parent, JsonObject json, JsonObject fallback)` | JSON constructor; this is the method-reference target used as the `WidgetThemeParser<SlotTheme>` for `IThemeApi.ITEM_SLOT`/`FLUID_SLOT`. Reads `slotHoverColor` via `JsonHelper.getColorWithFallback`, inheriting `IThemeApi.SLOT_HOVER_COLOR`. |

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getSlotHoverColor()` | - | `int` | |
| `withNoHoverBackground()` | - | `SlotTheme` | Overridden to preserve `SlotTheme` type (required by `WidgetThemeKeyBuilder`'s auto-hover-derivation). |

`SlotTheme.Builder<T, B>` adds: `hoverColor(int)` → writes `slotHoverColor`.

**Example (from `com.cleanroommc.modularui.api.IThemeApi`):**
```java
WidgetThemeKey<SlotTheme> ITEM_SLOT = get().widgetThemeKeyBuilder("itemSlot", SlotTheme.class)
        .defaultTheme(new SlotTheme(GuiTextures.SLOT_ITEM))
        .register();
```

---

## `com.cleanroommc.modularui.theme.TextFieldTheme`

`WidgetTheme` subclass for text fields; adds marked (selection) color and hint-text color.

```java
public class TextFieldTheme extends WidgetTheme
```

| Constructor | Notes |
|---|---|
| `TextFieldTheme(int markedColor, int hintColor)` | `56x18`, background `GuiTextures.DISPLAY_SMALL`, `color/textColor/iconColor = WHITE`, `textShadow=false`. |
| `TextFieldTheme(int defaultWidth, int defaultHeight, @Nullable IDrawable background, int color, int textColor, boolean textShadow, int iconColor, int markedColor, int hintColor)` | Full constructor. |
| `TextFieldTheme(TextFieldTheme parent, JsonObject json, JsonObject fallback)` | JSON constructor / `WidgetThemeParser<TextFieldTheme>` target for `IThemeApi.TEXT_FIELD`. Reads `markedColor` (`IThemeApi.MARKED_COLOR`) and `hintColor` (`IThemeApi.HINT_COLOR`). |

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getMarkedColor()` / `getHintColor()` | - | `int` | |
| `withNoHoverBackground()` | - | `TextFieldTheme` | Type-preserving override. |

`TextFieldTheme.Builder<T, B>` adds: `markedColor(int)`, `hintColor(int)`.

**Example (adapted from `test/TestEventHandler.java`, via `ThemeBuilder.textColor`):**
```java
new ThemeBuilder<>("mui:test_theme")
        .textColor(IThemeApi.TEXT_FIELD, Color.DEEP_PURPLE.main);
```

---

## `com.cleanroommc.modularui.theme.SelectableTheme`

`WidgetTheme` subclass for toggle-able widgets (e.g. toggle buttons): wraps a second, nested `WidgetTheme` describing the "selected" visual state, on top of the normal/hover pair every widget theme already has.

```java
public class SelectableTheme extends WidgetTheme
```

| Constructor / factory | Notes |
|---|---|
| `darkTextNoShadow(int w, int h, @Nullable IDrawable background, @Nullable IDrawable selectedBackground)` *(static)* | Dark-text variant for both normal and selected states. |
| `whiteTextShadow(int w, int h, @Nullable IDrawable background, @Nullable IDrawable selectedBackground)` *(static)* | White-shadowed-text variant for both states. Used for `IThemeApi.TOGGLE_BUTTON`'s defaults. |
| `SelectableTheme(int defaultWidth, int defaultHeight, @Nullable IDrawable background, int color, int textColor, boolean textShadow, int iconColor, @Nullable IDrawable selectedBackground, int selectedColor, int selectedTextColor, boolean selectedTextShadow, int selectedIconColor)` | Full constructor; builds an internal `WidgetTheme` for the selected state. |
| `SelectableTheme(SelectableTheme parent, JsonObject json, JsonObject fallback)` | JSON constructor / `WidgetThemeParser<SelectableTheme>` target for `IThemeApi.TOGGLE_BUTTON`. Reads `selectedBackground`, `selectedColor`, `selectedTextColor`, `selectedTextShadow`, `selectedIconColor` (note: `selectedIconColor` odd-looking fallback source — it reads `parent.getSelected().getTextColor()` rather than `getIconColor()`, appears to be a copy-paste artifact in source). |

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getSelected()` | - | `WidgetTheme` | The nested selected-state theme. |
| `withNoHoverBackground()` | - | `SelectableTheme` | Type-preserving override; strips background from both the normal and the selected nested theme. |

`SelectableTheme.Builder<T, B>` adds: `selectedColor(int)`, `selectedTextColor(int)`, `selectedTextShadow(int)`, `selectedIconColor(int)`, `selectedBackground(JsonBuilder|IDrawable|String textureId)`.

**Example (from `test/TestEventHandler.java`):**
```java
private static final ThemeBuilder<?> testTheme = new ThemeBuilder<>(TEST_THEME)
        .defaultColor(Color.BLUE_ACCENT.brighter(0))
        .widgetTheme(IThemeApi.TOGGLE_BUTTON, new SelectableTheme.Builder<>()
                .color(Color.BLUE_ACCENT.brighter(0))
                .selectedColor(Color.WHITE.main)
                .selectedIconColor(Color.RED.brighter(0)))
        .widgetThemeHover(IThemeApi.TOGGLE_BUTTON, new SelectableTheme.Builder<>()
                .selectedIconColor(Color.DEEP_PURPLE.brighter(0)))
        .textColor(IThemeApi.TEXT_FIELD, Color.DEEP_PURPLE.main);
```

---

## `com.cleanroommc.modularui.theme.AbstractTheme`

Base implementation of `ITheme` shared by `DefaultTheme` and `Theme`. Implements the fixed convenience getters (`getButtonTheme()`, etc.) as lazily-cached calls to the abstract `getWidgetTheme(WidgetThemeKey)`.

```java
public abstract class AbstractTheme implements ITheme
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `AbstractTheme(String id, ITheme parentTheme)` *(protected constructor)* | theme id, parent (nullable for the true root) | - | |
| `getId()` | - | `String` | |
| `getParentTheme()` | - | `ITheme` | |
| `getFallback()` / `getPanelTheme()` / `getButtonTheme()` / `getScrollbarTheme()` | - | `WidgetThemeEntry<WidgetTheme>` | Each lazily fetches and caches via `getWidgetTheme(IThemeApi.FALLBACK/PANEL/BUTTON/SCROLLBAR)`. |
| `getItemSlotTheme()` / `getFluidSlotTheme()` | - | `WidgetThemeEntry<SlotTheme>` | Via `IThemeApi.ITEM_SLOT` / `FLUID_SLOT`. |
| `getTextFieldTheme()` | - | `WidgetThemeEntry<TextFieldTheme>` | Via `IThemeApi.TEXT_FIELD`. |
| `getToggleButtonTheme()` | - | `WidgetThemeEntry<SelectableTheme>` | Via `IThemeApi.TOGGLE_BUTTON`. |

`getWidgetThemes()` and `getWidgetTheme(WidgetThemeKey<T>)` remain abstract — implemented differently by `DefaultTheme` (lazy self-initializing map of pure defaults) and `Theme` (eagerly merged with parent at construction).

---

## `com.cleanroommc.modularui.theme.DefaultTheme`

The absolute root theme (id `"DEFAULT"`), a singleton containing nothing but every widget theme key's registered defaults — no JSON, no parent.

```java
public class DefaultTheme extends AbstractTheme
```

| Member | Notes |
|---|---|
| `DefaultTheme.INSTANCE` *(static field)* | The singleton; also exposed as `ThemeAPI.DEFAULT_THEME`. |
| `getWidgetThemes()` | Lazily builds (on first call) one `WidgetThemeEntry` per key from `ThemeAPI.INSTANCE.getWidgetThemeKeys()`, using each key's `getDefaultValue()`/`getDefaultHoverValue()`. Returns an unmodifiable view. |
| `getWidgetTheme(WidgetThemeKey<T>)` | Triggers the same lazy init, then looks up the entry; if missing and the key `isSubWidgetTheme()`, walks up via `key.getParent()` until found. |

**Gotcha — initialization timing:** the lazy `initialize()` iterates `ThemeAPI.INSTANCE.getWidgetThemeKeys()` at *first use*, not at classload of `DefaultTheme`. Since widget theme keys are typically declared as `static final` fields on `IThemeApi` (an interface), simply referencing `IThemeApi.BUTTON` anywhere is enough to trigger that key's registration before `DefaultTheme` is first queried — but custom keys registered later (e.g. in a mod's own static initializer) must run before the first `DefaultTheme` lookup or they won't appear in the default's map until... actually the loop only runs once (`initialized` flag), so keys registered *after* the first `DefaultTheme.getWidgetThemes()`/`getWidgetTheme()` call are invisible to it. Register all custom widget theme keys eagerly (e.g. as interface constants, mirroring `IThemeApi`) before any GUI is opened.

---

## `com.cleanroommc.modularui.theme.Theme`

Concrete, JSON-backed `ITheme` for everything except the root. Package-private constructor — only `ThemeManager` (via `ThemeJson.deserialize()`) creates instances.

```java
public class Theme extends AbstractTheme
```

| Member | Notes |
|---|---|
| `Theme(String id, ITheme parent, WidgetThemeMap widgetThemes)` *(package-private constructor)* | Copies `widgetThemes` in, then back-fills any keys missing from it: if `parent` is another `Theme`, copies its entries for missing keys; if `parent == DefaultTheme.INSTANCE` (i.e. this is a top-level custom theme), first ensures a `FALLBACK` entry exists (using `ThemeManager.defaultFallbackWidgetTheme` if still missing) and then copies all of `DefaultTheme`'s entries for anything else missing. |
| `getWidgetThemes()` | Returns unmodifiable view of the internal `WidgetThemeMap` values (already fully merged — no further parent walk needed here). |
| `getWidgetTheme(WidgetThemeKey<T>)` | Direct map lookup; if missing and the key is a sub widget theme, walks up via `key.getParent()`. |

Inferred: because the constructor eagerly copies every ancestor's entries in at construction time, a `Theme`'s `getWidgetTheme` lookup never actually needs to consult `getParentTheme()` at runtime — parent-walking only happens for the sub-widget-theme case, and only within the same theme's own map.

---

## `com.cleanroommc.modularui.theme.ThemeAPI`

Singleton implementing `IThemeApi`; the concrete registry backing all theme/widget-theme registration and lookup. This is the class you interact with (almost always through the `IThemeApi` interface / `IThemeApi.get()`) to add custom themes and widget theme keys.

```java
public class ThemeAPI implements IThemeApi
```

| Field | Notes |
|---|---|
| `ThemeAPI.INSTANCE` *(static)* | The singleton. Same object as `IThemeApi.get()`. |
| `ThemeAPI.DEFAULT_ID` *(static, `= "DEFAULT"`)* | Reserved id for the root theme. |
| `ThemeAPI.DEFAULT_THEME` *(static)* | `= DefaultTheme.INSTANCE`. |
| `widgetThemeNamePattern` *(static)* | `[a-zA-Z0-9$_-]+` — validates widget theme ids in `registerWidgetTheme`. |

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getDefaultTheme()` | - | `ITheme` | `DEFAULT_THEME`. |
| `getTheme(String id)` | theme id | `@NotNull ITheme` | Falls back to `getDefaultTheme()` if `id` isn't registered. |
| `hasTheme(String id)` | theme id | boolean | |
| `registerTheme(String id, JsonBuilder json)` | id, JSON | - | Appends `json` to the list of Java-registered JSON fragments for `id` (deduped by reference-equality-ish `List.contains`, i.e. `JsonBuilder.equals`); does **not** immediately create a usable `ITheme` — actual `Theme` objects are only built during `ThemeManager.loadThemes(...)` (i.e. on `/reloadThemes` or resource reload). **Must be called before/during that reload**, e.g. from a `ReloadThemeEvent.Pre` handler (see `TestEventHandler.onThemeReload`), not at mod init time, since `onReload()` clears the theme map every reload. |
| `getJavaDefaultThemes(String id)` | theme id | `List<JsonBuilder>` | Mutable-view accessor (`computeIfAbsent`) into the Java-registered fragments for `id`. |
| `getThemeForScreen(String owner, String name, @Nullable String panel, @Nullable String defaultTheme, @Nullable String fallbackTheme)` | screen/panel identifiers, fallbacks | `ITheme` | Resolution order: JSON-registered screen theme (panel-specific, then screen-wide, then owner-wide) → Java-registered screen theme (same 3 levels) → `defaultTheme` → `fallbackTheme` → `"vanilla_dark"`/`"vanilla"` per `ModularUIConfig.useDarkThemeByDefault`. |
| `registerThemeForScreen(String screen, String theme)` | full screen id (`owner:name` or `owner:name:panel`), theme id | - | Java-side registration (JSON `"screens"` blocks always take priority per `getThemeIdForScreen`'s lookup order). |
| `registerWidgetTheme(String id, T defaultTheme, T defaultHoverTheme, WidgetThemeParser<T> parser)` | see `WidgetThemeKey` | `WidgetThemeKey<T>` | Validates non-null args, validates `id` against `widgetThemeNamePattern`, and throws `IllegalStateException` if a key for `id` is already registered (via `WidgetThemeKey.getFromFullName`). Constructing the returned `WidgetThemeKey` self-registers it (`registerWidgetThemeKey`). |
| `getWidgetThemeKeys()` | - | `@UnmodifiableView List<WidgetThemeKey<?>>` | All top-level *and* sub widget theme keys ever constructed, in construction order. |
| `registerTheme(ITheme theme)` *(package-private)* | theme instance | - | Called only by `ThemeManager` after JSON parsing; throws `IllegalArgumentException` on duplicate id. |
| `registerWidgetThemeKey(WidgetThemeKey<?> key)` *(package-private)* | key | - | Called from every `WidgetThemeKey` constructor. |
| `onReload()` *(package-private)* | - | - | Clears `themes` and `jsonScreenThemes`, then re-registers `DEFAULT_THEME`. Called at the start of `ThemeManager.reload()`. |

**Example (registering a custom theme and reacting to reload — from `test/TestEventHandler.java`):**
```java
public static final String TEST_THEME = "mui:test_theme";
private static final ThemeBuilder<?> testTheme = new ThemeBuilder<>(TEST_THEME)
        .defaultColor(Color.BLUE_ACCENT.brighter(0))
        .widgetTheme(IThemeApi.TOGGLE_BUTTON, new SelectableTheme.Builder<>()
                .color(Color.BLUE_ACCENT.brighter(0)));

@SideOnly(Side.CLIENT)
@SubscribeEvent
public void onThemeReload(ReloadThemeEvent.Pre event) {
    IThemeApi.get().registerTheme(testTheme); // ThemeBuilder is-a JsonBuilder
}
```
And later, applying it to a screen (`test/TestGuis.java`):
```java
// we need to do this to attach the theme since we have no screen yet
getScreen().useTheme(TestEventHandler.TEST_THEME);
```

---

## `com.cleanroommc.modularui.theme.ThemeBuilder<B extends ThemeBuilder<B>>`

`JsonBuilder` subclass with fluent helpers for building a whole theme's JSON in Java. Meant to be subclassed for project-specific shorthand (per its class javadoc).

```java
public class ThemeBuilder<B extends ThemeBuilder<B>> extends JsonBuilder
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `ThemeBuilder(String id)` | theme id | - | `id` is used as the registration key when passed to `IThemeApi.registerTheme(ThemeBuilder)`. |
| `getId()` | - | `String` | |
| `getParent()` | - | `String` | Only populated after calling `parent(String)` — not read back out of the underlying JSON. |
| `parent(String v)` | parent theme id | `B` | Writes `"parent": v`. If omitted, `ThemeManager` treats the theme as parented to `"DEFAULT"`. |
| `defaultBackground(IDrawable)` / `defaultBackground(String textureId)` | - | `B` | Writes the theme-wide fallback `background` (i.e. `IThemeApi.FALLBACK`'s background), inherited by every widget theme that doesn't override it. |
| `defaultHoverBackground(IDrawable\|String)` | - | `B` | Writes under the fallback's `:hover` sub-object (`mergeAdd`). |
| `defaultColor(int)` / `defaultTextColor(int)` / `defaultTextShadow(boolean)` / `defaultIconColor(int)` | value | `B` | Theme-wide fallback values. |
| `defaultTextHoverColor(int)` / `defaultTextHoverShadow(boolean)` / `defaultIconHoverColor(int)` / `defaultHoverColor(int)` | value | `B` | Hover variants of the above. |
| `defaultWidth(WidgetThemeKey<?>, int)` / `defaultHeight(WidgetThemeKey<?>, int)` | key, value | `B` | Per-widget-theme size override. |
| `background(WidgetThemeKey<?>, IDrawable\|String\|JsonBuilder)` | key, drawable spec | `B` | Per-widget-theme background. |
| `hoverBackground(WidgetThemeKey<?>, IDrawable\|String\|JsonBuilder)` | key, drawable spec | `B` | Per-widget-theme hover background. |
| `color(WidgetThemeKey<?>, int)` / `textColor(...)` / `textShadow(WidgetThemeKey<?>, boolean)` / `iconColor(...)` | key, value | `B` | Per-widget-theme property. |
| `hoverColor(...)` / `textHoverColor(...)` / `textHoverShadow(...)` / `iconHoverColor(...)` | key, value | `B` | Hover variants. |
| `itemSlotHoverColor(int)` / `fluidSlotHoverColor(int)` | value | `B` | Shorthand for `SlotTheme`'s `slotHoverColor` on `ITEM_SLOT`/`FLUID_SLOT`. |
| `textFieldMarkedColor(int)` / `textFieldHintColor(int)` | value | `B` | Shorthand for `TextFieldTheme` properties on `TEXT_FIELD`. |
| `widgetTheme(WidgetThemeKey<T>, WidgetThemeBuilder<T, ?>)` | key, builder | `B` | Recommended, organized way to set an arbitrary widget theme's full JSON at once (see per-subclass `Builder`s). Overwrites (`add`, not `mergeAdd`) any prior JSON for that key's full name. |
| `widgetThemeHover(WidgetThemeKey<T>, WidgetThemeBuilder<T, ?>)` | key, builder | `B` | Same, for the `:hover` variant. |

**Property-name gotcha:** every `WidgetThemeKey`-scoped setter uses `mergeAdd` (shallow-merges into any existing object at that key), so calling e.g. `color(BUTTON, ...)` then `textColor(BUTTON, ...)` accumulates into the same `button` JSON object rather than overwriting it — but `widgetTheme(key, builder)` uses plain `add` and **replaces** the whole object.

**Example: see `ThemeAPI` section above (`TestEventHandler.testTheme`).**

---

## `com.cleanroommc.modularui.theme.ThemeManager`

`@ApiStatus.Internal`, client-only (`@SideOnly(Side.CLIENT)`). Loads `themes.json` index files and per-theme JSON from resources/resourcepacks, merges them with Java-registered defaults, resolves parent chains, and builds the actual `Theme` instances registered into `ThemeAPI`. Implements `IResourceManagerReloadListener` so it re-runs automatically on any Minecraft resource reload (F3+T, resourcepack change), in addition to `/reloadThemes`.

```java
@ApiStatus.Internal
@SideOnly(Side.CLIENT)
public class ThemeManager implements IResourceManagerReloadListener
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `reload()` *(static)* | - | - | The full pipeline: posts `ReloadThemeEvent.Pre` → `ThemeAPI.INSTANCE.onReload()` (clears registry, re-adds `DEFAULT_THEME`) → discovers and loads all `themes.json` + per-theme JSON files → validates screen-theme JSON references → `FallbackableUITexture.reload()` → posts `ReloadThemeEvent.Post`. This is what `/reloadThemes` and resource-manager reloads both call. |
| `loadThemes(Map<String, List<String>> themesPaths)` *(static)* | map of theme id → list of resource-path fragments (for multi-file merge) | - | Lower-level entry point (also invoked internally by `reload()`'s discovery step); resolves each theme's JSON files, folds in any Java-registered defaults not already present in resources, validates the parent ancestor tree (rejects missing/circular parents, logging and discarding affected themes), sorts themes so parents are processed before children, then deserializes and registers each as a `Theme`. |
| `onResourceManagerReload(IResourceManager)` | Forge/MC callback | - | Just calls `reload()`. |

**Gotchas surfaced by the parsing logic (`ThemeJson.deserialize`/`parse`):**
- A theme's `"parent"` is read from the **last** JSON fragment (in file-list order) that declares one; if none declare it, parent defaults to `"DEFAULT"`.
- `"override": true` on any fragment for a theme id discards *all* Java-registered default JSON for that id (and any earlier-loaded resource fragments) — later resourcepacks patching the same theme id still get appended after an override, though.
- Sub widget themes (`isSubWidgetTheme() == true`) **only** inherit from the *same theme's* parent widget-theme-key entry (e.g. `ITEM_SLOT_PLAYER` inherits from this theme's own `ITEM_SLOT`, not from the parent theme's `ITEM_SLOT_PLAYER`) — this is called out explicitly in a source comment: "sub widget themes strictly only inherit from their parent widget theme and not the parent theme".
- If a widget theme key is completely undefined in both the normal and `:hover` JSON, and it's not a sub key, its value is fully copied from the parent theme's entry for that key (fast path, still parses an empty JSON object against it so future fallback-only properties resolve).
- Registering a custom `WidgetThemeKey` **after** a reload has already happened means it's simply absent from already-built `Theme`s until the next reload — themes aren't lazily re-derived per key.

---

## `com.cleanroommc.modularui.theme.ReloadThemeEvent`

Forge event bus event bracketing a theme reload; the extension point for adding Java-registered themes (`registerTheme`) at the right time.

```java
public class ReloadThemeEvent extends Event {
    public static class Pre extends ReloadThemeEvent {}
    public static class Post extends ReloadThemeEvent {}
}
```

No fields/methods beyond what `cpw.mods.fml.common.eventhandler.Event` provides. `Pre` fires before any JSON is loaded (register Java default themes/screen-theme associations here); `Post` fires after everything is loaded and registered.

**Example (from `test/TestEventHandler.java`):**
```java
@SideOnly(Side.CLIENT)
@SubscribeEvent
public void onThemeReload(ReloadThemeEvent.Pre event) {
    IThemeApi.get().registerTheme(testTheme);
}
```

---

## `com.cleanroommc.modularui.theme.ThemeReloadCommand`

Minecraft `/reloadThemes` client command; thin wrapper around `ThemeManager.reload()`.

```java
@SideOnly(Side.CLIENT)
public class ThemeReloadCommand extends CommandBase
```

| Method | Returns | Notes |
|---|---|---|
| `getCommandName()` | `"reloadThemes"` | |
| `getCommandUsage(ICommandSender)` | `"/reloadThemes"` | |
| `processCommand(ICommandSender, String[])` | - | Calls `ThemeManager.reload()`; reports success/failure as chat messages (green/red `EnumChatFormatting`). Any exception is caught and its message printed rather than propagated. |
| `getRequiredPermissionLevel()` | `0` | No permission required — usable by any player, including in single-player without cheats/op. |

Inferred: registered elsewhere (client command registration, not shown in `theme/`) — likely in a `ClientCommandHandler.registerCommand(new ThemeReloadCommand())` call during client init.
