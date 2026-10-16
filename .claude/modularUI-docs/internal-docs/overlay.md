# `overlay` package reference

Package: `com.cleanroommc.modularui.overlay`

Machinery for drawing `ModularScreen`s "on top of" vanilla/other-mod `GuiScreen`s — e.g. HUD-like overlays, or the built-in debug widget-inspector overlay. Most classes here are internal plumbing driven by `ClientScreenHandler`/Forge's `GuiOpenEvent`; the only class end users are expected to call directly is `OverlayStack.getHoveredElement()`/`isHoveringOverlay()`, and the modern overlay registration entry point is `OpenScreenEvent` (in `com.cleanroommc.modularui.screen`, not this package) rather than `OverlayHandler`/`OverlayManager`, which are deprecated.

---

## `com.cleanroommc.modularui.overlay.DebugOptions`

```java
public class DebugOptions
```

Plain data holder (singleton) for toggles that control what the built-in `DebugOverlay` widget-hover-info popup shows. All fields are public, mutable `*Value` wrapper objects (`BoolValue`, `IntValue`, `FloatValue` from `com.cleanroommc.modularui.value`) rather than raw primitives, so they can be bound directly to UI toggle widgets and read live.

| Field | Type | Default | Purpose |
|---|---|---|---|
| `INSTANCE` | `static final DebugOptions` | - | Singleton accessor; no other construction path. |
| `showHovered`, `showPos`, `showSize`, `showWidgetTheme`, `showExtra`, `showOutline` | `BoolValue` | `true` | What to show for the currently-hovered widget. |
| `showParent`, `showParentPos`, `showParentSize`, `showParentOutline` | `BoolValue` | `true` | Same, for the hovered widget's parent. |
| `showParentWidgetTheme` | `BoolValue` | `false` | |
| `textColor` | `IntValue` | `Color.argb(180, 40, 115, 220)` | ARGB color for debug overlay text. |
| `outlineColor` | `IntValue` | same as `textColor`'s initial value | ARGB color for hover outline boxes. |
| `cursorColor` | `IntValue` | `Color.withAlpha(Color.GREEN.main, 0.8f)` | |
| `scale` | `FloatValue` | `0.8f` | Text scale for the debug overlay. |

No methods beyond field access — this is a pure settings bag. Not meant to be subclassed or re-instantiated; use `DebugOptions.INSTANCE.showHovered` etc.

**Example (adapted from `overlay/DebugOverlay.java`):**
```java
boolean shown = DebugOptions.INSTANCE.showHovered.getBoolValue();
```

---

## `com.cleanroommc.modularui.overlay.DebugOverlay`

```java
public class DebugOverlay extends CustomModularScreen
```

The built-in "Debug Options" popup menu (bottom-center button that expands a context menu with toggles) shown when `ModularUIConfig.guiDebugMode` is enabled (wired up automatically by `OverlayStack.onGuiOpen`). Not typically constructed by user code directly.

### Constructor
- `DebugOverlay(IMuiScreen screen)` — `screen` is the wrapped screen this debug overlay is attached to (used to reach its panel manager / resize tree for the "print" buttons).

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `buildUI(ModularGuiContext context)` | context | `ModularPanel` | Builds the fixed debug-options UI: a bottom-centered button opening a context menu with "Print widget trees", "Print resizer tree", and two submenus of hover-info toggle checkboxes (self/parent) bound to `DebugOptions.INSTANCE` fields. |
| `toggleOption(int i, String name, IBoolValue<?> boolValue)` (static) | unique index `i` (used to build a widget name `"hover_info_toggle" + i`), display label, bound value | `IWidget` (a configured `ToggleButton`) | Helper used internally to build each checkbox row; public but only useful if building a similarly-styled custom debug row. |

**Gotcha:** `logWidgetTrees` (private) iterates `parent.getScreen().getPanelManager().getOpenPanels()` and calls `WidgetTree.print(panel)` — this only logs to console/logger, it doesn't render anything. `i` values in the two toggle groups are hardcoded (`0-5` and `10-14`) to keep widget names unique across both menus — don't reuse those indices if extending this class.

No constructed example needed beyond what's shown above — this class is effectively assembled automatically by `OverlayStack.onGuiOpen` when debug mode is on.

---

## `com.cleanroommc.modularui.overlay.OverlayHandler`

```java
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "3.2.0")
public class OverlayHandler implements Comparable<OverlayHandler>
```

**Deprecated, scheduled for removal in 3.2.0.** Prefer `OpenScreenEvent` (Forge event) for registering overlays instead. Wraps a predicate ("does this vanilla `GuiScreen` want an overlay?") plus a factory function that builds the `ModularScreen` overlay, with an ordering priority.

### Constructors
- `OverlayHandler(Predicate<GuiScreen> test, Function<GuiScreen, ModularScreen> overlayFunction)` — priority defaults to `1000`.
- `OverlayHandler(Predicate<GuiScreen> test, Function<GuiScreen, ModularScreen> overlayFunction, int priority)` — explicit priority; lower runs first (see `compareTo`).

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `isValidFor(GuiScreen screen)` | screen to test | boolean | Delegates to the constructor's `test` predicate. |
| `createOverlay(GuiScreen screen)` | screen | `ModularScreen` | Delegates to the constructor's `overlayFunction`. Caller (`OverlayStack.onGuiOpen`) `Objects.requireNonNull`s the result — factories must not return `null`. |
| `compareTo(OverlayHandler o)` | other handler | `int` | Compares by `priority` ascending. |

**Example (constructed, not from repo):**
```java
OverlayManager.register(new OverlayHandler(
        screen -> screen instanceof GuiContainer,
        screen -> new MyOverlayScreen()));
```

---

## `com.cleanroommc.modularui.overlay.OverlayManager`

```java
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "3.2.0")
public class OverlayManager
```

**Deprecated, scheduled for removal in 3.2.0.** Companion registry for `OverlayHandler`.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `overlays` | `public static final List<OverlayHandler>` | - | Exposed mutable list; direct mutation bypasses dedup/sort — prefer `register`. |
| `register(OverlayHandler handler)` (static) | handler | - | No-ops if already present (`equals`/`contains`, i.e. reference equality since `OverlayHandler` doesn't override `equals`); otherwise appends and re-sorts `overlays` by priority. |

---

## `com.cleanroommc.modularui.overlay.OverlayStack`

```java
@ApiStatus.Internal
public class OverlayStack
```

Static registry/render-loop for all currently-open overlay `ModularScreen`s (both new-style, added via `OpenScreenEvent`, and legacy `OverlayHandler`-based ones, plus the auto-added `DebugOverlay`). Marked `@ApiStatus.Internal` — treat as engine-internal; the only members useful to call from outside are the hover-query methods.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `foreach(Consumer<ModularScreen> function, boolean topToBottom)` (static) | callback, iteration order | - | Iterates the overlay stack top-first or bottom-first. |
| `interact(Predicate<ModularScreen> function, boolean topToBottom)` (static) | predicate, order | boolean — `true` if any screen's predicate returned `true` | Calls `screen.getContext().updateEventState()` before testing each screen; short-circuits on first match (topmost overlay "consumes" input first when `topToBottom=true`, mirroring normal GUI event bubbling). |
| `draw(int mouseX, int mouseY, float partialTicks)` (static) | mouse pos, partial ticks | - | Renders every overlay screen bottom-to-top, tracks which is hovered, then calls `ClientScreenHandler.drawDebugScreen(hovered, fallback)`. Client-render-thread only. |
| `open(ModularScreen screen)` (static) | screen | - | If already present but not topmost, removes and re-adds to bring it to front; calls `screen.onOpen()`; enables keyboard repeat events if not already enabled. |
| `close(ModularScreen screen)` (static) | screen | - | Removes it; if it was present, closes and disposes its `PanelManager`. |
| `onTick()` (static) | - | - | Calls `ModularScreen::onUpdate` on every overlay, top-to-bottom. |
| `getHoveredElement()` (static) | - | `@Nullable IWidget` | Walks overlays top-to-bottom, returns the first hovered widget found (via each screen's `ModularGuiContext.getTopHovered()`), or `null` if none hovered. Safe/cheap to call from anywhere on the client thread to check "is the mouse over an overlay widget." |
| `isHoveringOverlay()` (static) | - | boolean | `getHoveredElement() != null`. |
| `onGuiOpen(GuiScreen newScreen)` (static) | the vanilla screen Minecraft just opened (or `null` on close) | - | Closes all current overlays first. If a new screen was opened: runs legacy `OverlayHandler`s (deprecated path), posts an `OpenScreenEvent` and opens any overlays added via `event.addOverlay(...)`, and finally auto-adds a `DebugOverlay` if `ModularUIConfig.guiDebugMode` is on and `newScreen instanceof IMuiScreen`. |
| `closeAll()` (package-private) | - | - | Not callable from outside the package; closes/disposes every overlay. |

**Gotcha:** `onGuiOpen` is meant to be driven by Minecraft's own `GuiOpenEvent`/screen-open lifecycle (via `ClientScreenHandler`, not shown here) — calling it manually would tear down and rebuild the whole overlay stack.

---

## `com.cleanroommc.modularui.overlay.ScreenWrapper`

```java
@ApiStatus.Experimental
public class ScreenWrapper implements IMuiScreen
```

Minimal `IMuiScreen` adapter that pairs an existing vanilla `GuiScreen` with a `ModularScreen`, for use as an overlay (as opposed to `GuiScreenWrapper`/`GuiContainerWrapper`, which *are* the `GuiScreen`). Marked `@ApiStatus.Experimental`.

### Constructor
- `ScreenWrapper(GuiScreen guiScreen, ModularScreen screen)` — wraps both; neither is copied or validated.

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `getScreen()` | - | `@NotNull ModularScreen` | The wrapped modular screen. |
| `getGuiScreen()` | - | `GuiScreen` | The wrapped vanilla screen. |
| `updateGuiArea(Rectangle area)` | resize area | - | Deliberately **no-op** — comment: "overlay should not modify screen". This differs from `IMuiScreen`'s default implementation, which would resize a wrapped `GuiContainer`; overlays must not resize the screen they're layered on top of. |

**Example (constructed, not from repo):**
```java
ModularScreen overlay = new MyOverlayScreen();
overlay.constructOverlay(vanillaScreen);
IMuiScreen wrapper = new ScreenWrapper(vanillaScreen, overlay);
OverlayStack.open(overlay);
```
