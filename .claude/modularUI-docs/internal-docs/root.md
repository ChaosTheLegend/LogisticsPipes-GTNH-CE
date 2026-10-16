# Root package (`com.cleanroommc.modularui`) reference

The six classes directly in `com.cleanroommc.modularui` (not a subpackage). These are the mod's
FML entry points, its config, and its client-side GUI error tracker. Everything here is
Forge/FML-flavored glue rather than GUI-framework API — mod authors embedding ModularUI2 as a
library generally won't touch these directly, except to read `ModularUIConfig` values or check
`ModularUI.Mods`/`ModularUI.isDevEnv`.

## `com.cleanroommc.modularui.ModularUI`

The `@Mod` entry point.

```java
@Mod(modid = ModularUI.ID, name = ModularUI.NAME, version = Tags.VERSION,
     dependencies = ModularUI.DEPENDENCIES,
     guiFactory = "com.cleanroommc.modularui.config.ModularUIGuiConfigFactory")
public class ModularUI {
    public static final String ID = "modularui2";
    public static final String NAME = "Modular UI 2";
    public static final Logger LOGGER; // log4j logger, name = ID
    public static final String BOGO_SORT = "bogosorter";

    @SidedProxy(modId = ID, clientSide = "...ClientProxy", serverSide = "...CommonProxy")
    public static CommonProxy proxy;

    @Mod.Instance
    public static ModularUI INSTANCE;

    public static final boolean isTestEnv; // true when running outside FML (Launch.blackboard == null)
    public static final boolean isDevEnv;  // isTestEnv || fml.deobfuscatedEnvironment

    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event);
    @Mod.EventHandler public void postInit(FMLPostInitializationEvent event);
    @Mod.EventHandler public void onServerLoad(FMLServerStartingEvent event);

    public enum Mods { LWJGL3IFY, BAUBLES, BOGOSORTER, GT5U, HODGEPODGE, NEI, NEA; public boolean isLoaded(); }
    public static class ModIds { public static final String LWJGL3IFY, BOGOSORTER, GT5U, GT6, HODGEPODGE, NEI, NEA, BAUBLES; }
}
```

| Member | Notes |
|---|---|
| `ID` / `NAME` | mod id `"modularui2"` / display name `"Modular UI 2"` |
| `LOGGER` | shared log4j `Logger`, used throughout the codebase as `ModularUI.LOGGER.warn(...)`/`.info(...)` |
| `DEPENDENCIES` | package-private FML dependency string: requires `gtnhmixins`, `gtnhlib`; soft-depends `NotEnoughItems`, `hodgepodge`; loads `before:gregtech` |
| `proxy` | `@SidedProxy`-injected `ClientProxy` (client) or `CommonProxy` (server); all three lifecycle events (`preInit`/`postInit`/`onServerLoad`) just forward to `proxy` |
| `INSTANCE` | the singleton mod instance, injected by FML |
| `isTestEnv` | `true` when `Launch.blackboard == null` — i.e. running outside a real FML/launchwrapper environment (e.g. unit tests) |
| `isDevEnv` | `isTestEnv` **or** the `fml.deobfuscatedEnvironment` blackboard flag; used elsewhere as the default for `ModularUIConfig.enableTestGuis`/`guiDebugMode` |
| `Mods` (enum) | per-mod "is it loaded" cache: `isLoaded()` computes `Loader.isModLoaded(id)` once (memoized via `initialized`/`loaded` fields) and, for `GT5U`, additionally requires GT6 (`gregapi_post`) to **not** be loaded |
| `ModIds` (nested class) | raw string mod-id constants backing the `Mods` enum values |

**Gotcha:** `Mods.isLoaded()` memoizes on first call — if a mod's loaded state could somehow change after the first check (it can't in practice, mod loading is a one-time startup phase), the cached value would be stale. Not an issue in normal use since it's only ever queried post-`FMLPreInitializationEvent`.

**Example (from repo)** — checking a soft dependency before using its API, `src/main/java/com/cleanroommc/modularui/ClientProxy.java:181`:
```java
if (ModularUIConfig.enableTestGuis && testKey != null && testKey.isPressed() && ModularUI.Mods.BAUBLES.isLoaded()) {
    InventoryTypes.BAUBLES.visitAll(Platform.getClientPlayer(), (type, index, stack) -> { /* ... */ return false; });
}
```

---

## `com.cleanroommc.modularui.CommonProxy`

Server-side (and base-class) half of the `@SidedProxy` pair. Registers everything that must exist
on both sides: network handler, GUI factories, inventory types, and the per-tick `ModularContainer`
update hook.

```java
public class CommonProxy {
    void preInit(FMLPreInitializationEvent event);
    void postInit(FMLPostInitializationEvent event); // no-op body
    void onServerLoad(FMLServerStartingEvent event);
    @SideOnly(Side.CLIENT) public Timer getTimer60Fps(); // throws UnsupportedOperationException here

    @SubscribeEvent public void onTick(TickEvent.PlayerTickEvent event);
    @SubscribeEvent public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event);
}
```

| Method | Notes |
|---|---|
| `preInit(event)` | registers `ModularUIConfig` with gtnhlib's `ConfigurationManager`; registers a `GuiManager` and `this` on both the FML and Forge event buses; conditionally registers `TestEventHandler` and calls `TestEventHandler.preInit()` when `ModularUIConfig.enableTestGuis`; registers the `HoloScreenEntity` entity type; calls `NetworkHandler.init()`, `GuiFactories.init()`, `InventoryTypes.init()` |
| `postInit(event)` | empty in `CommonProxy`; overridden by `ClientProxy` to register the theme-reload command and resource-reload listener |
| `onServerLoad(event)` | registers the `ItemEditorGui.Command` server command |
| `getTimer60Fps()` | package-visible-looking but `public`; **throws `UnsupportedOperationException`** on the server — only `ClientProxy` provides a real 60fps `Timer`. Calling this on a dedicated server is a bug in the caller, not something `CommonProxy` is expected to support |
| `onTick(PlayerTickEvent)` | if the player's open container is a `ModularContainer`, calls its `onUpdate()` — this is how synced widgets get their periodic server-side tick |
| `onPlayerLeave(PlayerLoggedOutEvent)` | on a non-dedicated-client side (i.e. server or integrated server), tells `ModularNetwork.SERVER` the player left so it can clean up their active GUIs |

**Gotcha:** `preInit`/`postInit`/`onServerLoad` are package-private (no visibility modifier) — only `ModularUI` (same package) and `ClientProxy` (subclass, via `@Override`) can call/override them; they are not part of any public API contract for other mods.

---

## `com.cleanroommc.modularui.ClientProxy extends CommonProxy`

`@SideOnly(Side.CLIENT)`. Adds everything client-only: key bindings, custom resize cursors, the
holo-screen entity renderer, animation ticking, and the theme-reload command/resource listener.

```java
@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {
    public static KeyBinding testKey;
    public static int majorLwjgl = -1;
    public static Cursor resizeCursorDiag, resizeCursorDiagInverse, resizeCursorH, resizeCursorV;

    @Override void preInit(FMLPreInitializationEvent event);
    @Override void postInit(FMLPostInitializationEvent event);
    @Override public Timer getTimer60Fps();

    public static IntBuffer readPixel(BufferedImage img, boolean inverse, boolean transpose);
    public static void setCursorResizeIcon(ResizeDragArea dragArea);
    public static void resetCursorIcon();

    @SubscribeEvent public void onKeyboard(InputEvent.KeyInputEvent event);
    @SubscribeEvent public void onUnloadWorld(WorldEvent.Unload event);
}
```

| Member | Notes |
|---|---|
| `preInit(event)` | registers a `ClientScreenHandler` on both event buses; calls `AnimatorManager.init()`; conditionally registers the `key.test` keybind (`ModularUIConfig.enableTestGuis`); calls `DrawableSerialization.init()`; registers `ScreenEntityRender` for `HoloScreenEntity`; force-enables an 8-bit stencil buffer if none is present; loads the 3 custom resize cursors from PNGs under `assets/modularui/textures/gui/icons/` — **skipped/disabled entirely under LWJGL3** (`majorLwjgl > 2` logs a warning and returns early) |
| `getTimer60Fps()` | returns the real client `Timer` (60 fps), unlike `CommonProxy`'s throwing stub |
| `readPixel(img, inverse, transpose)` | builds an `IntBuffer` of ARGB pixels from a `BufferedImage` in the row/column order LWJGL's `Cursor` constructor expects; `inverse` flips vertical scan direction, `transpose` swaps x/y (used to derive the horizontal-resize cursor from the vertical one's pixel data) |
| `setCursorResizeIcon(ResizeDragArea)` | swaps in the diagonal/horizontal/vertical native cursor matching the given `ResizeDragArea` (or resets if cursors failed to load / `dragArea == null`) — the actual per-frame cursor feedback during panel/widget drag-resize |
| `resetCursorIcon()` | restores whatever cursor was active before a resize cursor was set |
| `onKeyboard(KeyInputEvent)` | if the test key is pressed and Baubles is loaded, scans the player's Baubles slots for a `TestItem` and opens its player-inventory GUI from that slot — dev/test-only wiring |
| `onUnloadWorld(WorldEvent.Unload)` | on client world unload, tells `ModularNetwork.CLIENT` the player left; if running an integrated (single-player) server, **also** notifies `ModularNetwork.SERVER` directly, since `PlayerLoggedOutEvent` doesn't fire for the local player in that case |

**Gotcha:** custom cursor loading is wrapped in a broad `catch (Throwable e)` that just logs `"Custom Cursors failed to load."` — any I/O, LWJGL, or resource-lookup failure silently disables custom cursors rather than crashing startup; `setCursorResizeIcon`/`resetCursorIcon` both no-op (return early) whenever `resizeCursorV == null`, which is the load-failure signal.

**Example (constructed, not from repo)** — the pattern used by drag-resize code to reflect hover state in the cursor:
```java
ResizeDragArea corner = IDragResizeable.getDragResizeCorner(widget, area, stack, mouseX, mouseY);
ClientProxy.setCursorResizeIcon(corner); // null clears back to the default cursor
```

---

## `com.cleanroommc.modularui.ModularUIConfig`

gtnhlib-managed `@Config` class — every `public static` field annotated `@Config.*` becomes a
config entry, editable through the in-game mod config screen (`ModularUIGuiConfigFactory`).

```java
@Config(modid = ModularUI.ID)
public class ModularUIConfig {
    @Config.RangeInt(min = 1, max = 100)
    public static int defaultScrollSpeed = 30;
    public static boolean smoothProgressBar = false;
    public static RichTooltip.Pos tooltipPos = RichTooltip.Pos.NEXT_TO_MOUSE;
    public static boolean escRestoreLastText = false;
    public static boolean showSlotOverlay = true;
    public static boolean guiDebugMode = ModularUI.isDevEnv;
    public static boolean useDarkThemeByDefault = false;
    public static String debugTextColor = "#FFAAAAAA";
    public static String debugOutlineColor = "#DCB42873";
    @Config.RequiresMcRestart
    public static boolean enableTestGuis = ModularUI.isDevEnv;
    @Config.RequiresMcRestart
    public static boolean enableTestOverlays = false;
}
```

| Field | Default | Meaning |
|---|---|---|
| `defaultScrollSpeed` | `30` | pixels scrolled per scroll-wheel tick; range-limited 1–100 |
| `smoothProgressBar` | `false` | if `true`, progress bars step in screen pixels (smoother, more steps) instead of texture pixels |
| `tooltipPos` | `RichTooltip.Pos.NEXT_TO_MOUSE` | default tooltip placement around a widget/panel when not overridden per-tooltip |
| `escRestoreLastText` | `false` | if `true`, pressing Esc in a focused text field restores the previous text instead of confirming the current one |
| `showSlotOverlay` | `true` | whether occupied slots draw their overlay |
| `guiDebugMode` | `ModularUI.isDevEnv` | draws widget outlines/info when `true` |
| `useDarkThemeByDefault` | `false` | prefer the `vanilla_dark` theme when a screen doesn't specify one |
| `debugTextColor` / `debugOutlineColor` | `"#FFAAAAAA"` / `"#DCB42873"` | colors for debug-mode overlays; `#`-prefixed hex or a named color |
| `enableTestGuis` | `ModularUI.isDevEnv`, **`@Config.RequiresMcRestart`** | enables the test block/item/GUI and the diamond-right-click test opener |
| `enableTestOverlays` | `false`, **`@Config.RequiresMcRestart`** | enables the title-screen test overlay and per-`GuiContainer` watermark |

**Gotcha:** `enableTestGuis`/`enableTestOverlays` require a Minecraft restart to take effect (`@Config.RequiresMcRestart`) — toggling them from the in-game config screen won't apply until relaunch, unlike the rest of the fields which presumably apply live (no such annotation on them).

Fields are read directly as static state throughout the codebase, e.g. `ClientProxy.preInit`
gates the test keybind on `ModularUIConfig.enableTestGuis`, and `CommonProxy.preInit` gates
`TestEventHandler` registration the same way.

---

## `com.cleanroommc.modularui.GuiError`

Immutable record-like value describing one client-side GUI framework error (bad sizing, a broken
widget tree, a failed sync, etc.), pushed into `GuiErrorHandler`.

```java
public class GuiError {
    public static void throwNew(IWidget guiElement, Type type, String msg);

    protected GuiError(String msg, IWidget reference, Type type);
    public Level getLevel();       // always Level.ERROR
    public IWidget getReference();
    public Type getType();
    public String getMsg();
    @Override public String toString(); // "MUI [type][reference]: msg"
    @Override public int hashCode();    // hash of (level, reference, type) — msg NOT included

    public enum Type { DRAW, SIZING, WIDGET_TREE, INTERACTION, SYNC }
}
```

| Member | Notes |
|---|---|
| `throwNew(guiElement, type, msg)` | the intended entry point for the rest of the codebase; only actually pushes the error `if (NetworkUtils.isClient())` — errors are client-only (no server-side GUI rendering to report on) |
| `getLevel()` | always `Level.ERROR`; there is no way to construct a `GuiError` at another severity |
| constructor | `protected` — only `GuiErrorHandler` (which calls `new GuiError(...)` from the same... actually different package) can construct one. **Gotcha:** the constructor is `protected` but `GuiErrorHandler` is in the *same* package (`com.cleanroommc.modularui`), so package-private access is what actually makes this work, not the `protected` modifier's inheritance semantics |

**Gotcha:** `hashCode()` is overridden (hashing `level`, `reference`, `type` — **not** `msg`) but `equals()` is **not** overridden, so it still falls back to `Object.equals` (identity). `GuiErrorHandler`'s dedup set (`ObjectOpenHashSet<GuiError>`) relies on `add()` using both `hashCode` and `equals`; since two distinct `GuiError` instances are never `equals()`, **every** pushed error is added to the set (and thus logged) even if it's a byte-for-byte duplicate of one already present — the override present here doesn't actually achieve deduplication on its own. Inferred from reading `GuiErrorHandler.pushError`/`GuiError`'s source; not verified against a runtime test.

---

## `com.cleanroommc.modularui.GuiErrorHandler`

`@SideOnly(Side.CLIENT)` singleton collecting `GuiError`s for later inspection (e.g. by a debug
overlay).

```java
@SideOnly(Side.CLIENT)
public class GuiErrorHandler {
    public static final GuiErrorHandler INSTANCE;

    public void clear();
    void pushError(IWidget reference, GuiError.Type type, String msg);
    public List<GuiError> getErrors();
    public void drawErrors(int x, int y); // empty body
}
```

| Member | Notes |
|---|---|
| `INSTANCE` | eagerly-constructed singleton (private constructor) |
| `clear()` | clears the ordered `errors` list — **note:** does **not** clear the internal `errorSet` used for dedup, so an error cleared via `clear()` and then pushed again with the exact same reference/type (same identity — see `GuiError`'s gotcha above) would still be suppressed as a "duplicate" by `errorSet`, even though `getErrors()` no longer shows it |
| `pushError(reference, type, msg)` | package-private; constructs a `GuiError`, and if `errorSet.add(error)` reports it as new, logs it via `ModularUI.LOGGER.log(level, error)` and appends to `errors` |
| `getErrors()` | the live list backing this handler — direct reference, not a copy |
| `drawErrors(int, int)` | present but has an **empty body** — the "draw errors to screen" hook is not implemented in this version despite the method existing |

**Gotcha:** given `GuiError` never overrides `equals()`, `errorSet.add(error)` dedups only by reference identity, so `pushError` will treat almost every call as "new" (unless the exact same `GuiError` object instance were reused, which never happens since `pushError` always constructs a fresh one) — in practice this means the intended de-duplication of repeated identical errors does not occur. Inferred from source; not confirmed against actual runtime log output.

**Example (constructed, not from repo)** — reading collected errors for a debug overlay:
```java
for (GuiError error : GuiErrorHandler.INSTANCE.getErrors()) {
    System.out.println(error); // "MUI [SIZING][...]: msg"
}
```
