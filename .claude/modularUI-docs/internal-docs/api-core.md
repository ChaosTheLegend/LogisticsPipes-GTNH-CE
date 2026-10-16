# ModularUI2 — Core API Reference (`api/` package)

Covers `com.cleanroommc.modularui.api` (top level), `api.event`, `api.inventory`, `api.layout`, and `api.widget`. This is the contract layer that every widget, panel, and screen implementation in ModularUI2 is built on.

Source root: `src/main/java/com/cleanroommc/modularui/`. Examples adapted from `src/main/java/com/cleanroommc/modularui/test/` are cited by file name.

---

# `api/` (top-level)

## `com.cleanroommc.modularui.api.GuiAxis`

Enum representing the two layout axes (horizontal/vertical).

```java
public enum GuiAxis { X, Y }
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `isHorizontal()` | — | `boolean` | `true` iff `this == X` |
| `isVertical()` | — | `boolean` | `true` iff `this == Y` |
| `getOther()` | — | `GuiAxis` | flips X↔Y |

Used pervasively by the layout/resizer system (`IResizeParent.isSizeCalculated(GuiAxis)`, `IResizeable.setAxisResized`, etc.) to avoid duplicating per-axis logic.

**Example (constructed, not from repo)**
```java
GuiAxis axis = GuiAxis.X;
if (axis.isHorizontal()) { /* ... */ }
GuiAxis vertical = axis.getOther(); // Y
```

---

## `com.cleanroommc.modularui.api.IGuiHolder<T extends GuiData>`

Implement on a `TileEntity` or `Item` to make it openable as a synced ModularUI GUI.

```java
@FunctionalInterface
public interface IGuiHolder<T extends GuiData> {
    ModularScreen createScreen(T data, ModularPanel mainPanel); // default
    ModularPanel buildUI(T data, PanelSyncManager syncManager, UISettings settings); // abstract
}
```

- `createScreen(T data, ModularPanel mainPanel)` — client-only (`@SideOnly(Side.CLIENT)`). Default impl logs a warning and wraps `mainPanel` in a plain `ModularScreen(ModularUI.ID, mainPanel)`. Override to pass your own mod id. Inferred: not overriding this in future versions may crash per the in-code warning.
- `buildUI(T data, PanelSyncManager syncManager, UISettings settings)` — called on **both** server and client. Build only the main panel here; register sync handlers on `syncManager` for widgets that live in other panels.

Because only `buildUI` is abstract, the interface is a valid `@FunctionalInterface`, but in practice implementers are full classes since they also implement game interfaces (`TileEntity`, `Item`).

**Example (from repo)** — `src/main/java/com/cleanroommc/modularui/test/TestTile.java`:
```java
public class TestTile extends TileEntity implements IGuiHolder<PosGuiData> {
    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        settings.customContainer(() -> new CraftingModularContainer(3, 3, this.craftingInventory));
        settings.customGui(() -> TestGuiContainer::new);
        syncManager.registerSlotGroup("item_inv", 3);
        syncManager.bindPlayerInventory(guiData.getPlayer());
        ModularPanel panel = new ModularPanel("test_tile");
        // ... build widget tree ...
        return panel;
    }
}
```
`src/main/java/com/cleanroommc/modularui/test/TestItem.java` also overrides `createScreen`:
```java
public class TestItem extends Item implements IGuiHolder<PlayerInventoryGuiData>, ISimpleBauble {
    @Override
    public ModularScreen createScreen(PlayerInventoryGuiData data, ModularPanel mainPanel) {
        return new ModularScreen(ModularUI.ID, mainPanel);
    }
    @Override
    public ModularPanel buildUI(PlayerInventoryGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) { /* ... */ }
}
```

---

## `com.cleanroommc.modularui.api.IJsonSerializable`

Small marker interface for drawables/objects that support extra JSON (de)serialization on top of their normal construction.

```java
public interface IJsonSerializable {
    default void loadFromJson(JsonObject json) {}
    default boolean saveToJson(JsonObject json) { return false; }
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `loadFromJson(JsonObject json)` | json to read | `void` | default no-op; called after the drawable is already constructed |
| `saveToJson(JsonObject json)` | json to write | `boolean` | default returns `false` ("not serialized"); return `true` if data was actually written |

**Example (constructed, not from repo)**
```java
class MyDrawable implements IDrawable, IJsonSerializable {
    private int color;
    @Override public void loadFromJson(JsonObject json) { this.color = json.get("color").getAsInt(); }
    @Override public boolean saveToJson(JsonObject json) { json.addProperty("color", color); return true; }
}
```

---

## `com.cleanroommc.modularui.api.IMathValue`

Abstraction over "things that behave like a mutable, computable number/string/bool" — used by the math-expression evaluator system.

```java
public interface IMathValue {
    IMathValue get();
    boolean isNumber();
    void set(double value);
    void set(String value);
    double doubleValue();
    boolean booleanValue();
    String stringValue();

    class EvaluateException extends RuntimeException {
        public EvaluateException(String message) { super(message); }
    }
}
```

| Method | Purpose |
|---|---|
| `get()` | returns the computed/stored value (may be `this` or a resolved constant) |
| `isNumber()` | whether this value is numeric (vs. string/bool) |
| `set(double)` / `set(String)` | mutates the underlying value |
| `doubleValue()` / `booleanValue()` / `stringValue()` | coerces to the respective type |
| `EvaluateException` | thrown by implementations when an expression can't be evaluated |

No usage in `test/`. Inferred: implemented by internal math-expression node classes, not typically implemented by library consumers directly.

**Example (constructed, not from repo)**
```java
IMathValue constant = new IMathValue() {
    private double v = 42;
    public IMathValue get() { return this; }
    public boolean isNumber() { return true; }
    public void set(double value) { this.v = value; }
    public void set(String value) { this.v = Double.parseDouble(value); }
    public double doubleValue() { return v; }
    public boolean booleanValue() { return v != 0; }
    public String stringValue() { return String.valueOf(v); }
};
```

---

## `com.cleanroommc.modularui.api.IMuiScreen`

Implement on a `GuiScreen` subclass to make it a wrapper around a `ModularScreen`. The `GuiScreen` must hold a `final ModularScreen` field and call `ModularScreen.construct(IMuiScreen)` from its constructor.

```java
@Optional.InterfaceList({ ... }) // NEA / lwjgl3ify compat
@SideOnly(Side.CLIENT)
public interface IMuiScreen extends IAnimatedScreen, InputEvents.KeyboardListener {
    @NotNull ModularScreen getScreen();
    default void setFocused(boolean focused) {}
    @ApiStatus.NonExtendable default void handleDrawBackground(int tint, IntConsumer drawFunction) { ... }
    default void updateGuiArea(Rectangle area) { ... }
    @ApiStatus.NonExtendable default boolean isGuiContainer() { ... }
    @ApiStatus.NonExtendable default void setHoveredSlot(Slot slot) { ... }
    default GuiScreen getGuiScreen() { return (GuiScreen) this; }
    // nea$getX/Y/Width/Height, onKeyEvent, onTextEvent — internal overrides
}
```

| Method | Notes |
|---|---|
| `getScreen()` | abstract; must return the wrapped `ModularScreen` (usually a `final` field) |
| `setFocused(boolean)` | no-op by default — MC 1.7.10 `GuiScreen` has no such API |
| `handleDrawBackground(int tint, IntConsumer drawFunction)` | `@ApiStatus.NonExtendable`. Intended to be called from an override of `GuiScreen.drawWorldBackground(int)`, passing the super method reference as `drawFunction`. Also draws the dark overlay and re-enables texturing for recipe-viewer compat |
| `updateGuiArea(Rectangle area)` | called every time the wrapped `ModularScreen` resizes; only meaningful for `GuiContainer`s |
| `isGuiContainer()` | `@ApiStatus.NonExtendable`; `true` if `getGuiScreen()` is a `GuiContainer` |
| `setHoveredSlot(Slot slot)` | `@ApiStatus.NonExtendable`; forwards to `GuiContainerAccessor` mixin, only for `GuiContainer`s |
| `getGuiScreen()` | default casts `this` to `GuiScreen` — only valid because implementers are expected to *be* a `GuiScreen` |
| `nea$getX/Y/Width/Height()` | NeverEnoughAnimations compat, delegate to the main panel's `Area` |
| `onKeyEvent` / `onTextEvent` | `@ApiStatus.Internal`; lwjgl3ify keyboard listener callbacks forwarded to `ModularScreen` |

Default implementations to build on: `com.cleanroommc.modularui.screen.GuiScreenWrapper` and `com.cleanroommc.modularui.screen.GuiContainerWrapper` (per the interface javadoc).

**Example (from repo)** — `src/main/java/com/cleanroommc/modularui/test/TestGuiContainer.java` subclasses the container-based default implementation rather than implementing `IMuiScreen` from scratch:
```java
public class TestGuiContainer extends GuiContainerWrapper {
    public TestGuiContainer(ModularContainer container, ModularScreen screen) {
        super(container, screen);
        ModularUI.LOGGER.info("Created custom gui container");
    }
}
```
Wired up via `UISettings.customGui(() -> TestGuiContainer::new)` in `TestTile.java`.

---

## `com.cleanroommc.modularui.api.IPacketWriter`

Functional wrapper for writing arbitrary data to a `PacketBuffer`.

```java
@FunctionalInterface
public interface IPacketWriter {
    void write(PacketBuffer buffer) throws IOException;
    default PacketBuffer toPacket() { ... }
}
```

- `write(PacketBuffer buffer)` — writes data; may throw `IOException`.
- `toPacket()` — convenience default that allocates a new `PacketBuffer(Unpooled.buffer())`, calls `write`, and wraps any `IOException` in a `RuntimeException`.

**Example (constructed, not from repo)**
```java
IPacketWriter writer = buffer -> buffer.writeVarIntToBuffer(42);
PacketBuffer packet = writer.toPacket();
```

---

## `com.cleanroommc.modularui.api.IPanelHandler`

**Central interface.** Manages opening/closing/caching of a `ModularPanel` (a secondary or synced sub-panel), guaranteeing the same panel instance is reused rather than rebuilt every time it opens.

```java
@ApiStatus.NonExtendable
public interface IPanelHandler {
    static IPanelHandler simple(ModularPanel parent, SecondaryPanel.IPanelBuilder provider, boolean subPanel);

    boolean isPanelOpen();
    void openPanel();
    void closePanel();
    void closeSubPanels();
    @ApiStatus.OverrideOnly void closePanelInternal();
    default boolean togglePanel();
    void deleteCachedPanel();
    boolean isSubPanel();
}
```

- `static simple(ModularPanel parent, SecondaryPanel.IPanelBuilder provider, boolean subPanel)` — creates a **non-synced** panel handler (client-side only; using synced values in the built panel crashes). `provider` builds the panel lazily on first open; it must not return `null` or the main panel itself.
  - Throws `NullPointerException` if the built panel is null.
  - Throws `IllegalArgumentException` if the built panel is the main panel, or the panel contains synced values.
  - Backed by `com.cleanroommc.modularui.screen.SecondaryPanel`.
  - For panels with synced widgets, use `PanelSyncManager.panel(String, PanelSyncHandler.IPanelBuilder, boolean)` (exposed as `syncManager.syncedPanel(...)`) instead — it also returns an `IPanelHandler` but works on both sides.
- `isPanelOpen()` — whether the panel is currently open.
- `openPanel()` — opens (building it if not cached yet). Works on both sides if the handler is synced.
- `closePanel()` — starts the closing animation if open. Works on both sides if synced.
- `closeSubPanels()` — closes all sub-panels of this panel; mostly internal.
- `closePanelInternal()` — `@ApiStatus.OverrideOnly`; called internally after the panel finishes closing. Not meant to be called by consumers.
- `togglePanel()` — default; opens if closed / closes if open; returns `true` if it was opened, `false` if closed.
- `deleteCachedPanel()` — discards the cached panel instance; use sparingly. Throws `UnsupportedOperationException` if the panel has `ItemSlotSH` sync handlers.
- `isSubPanel()` — whether this panel closes automatically when its parent closes.

**Example (from repo)** — `src/main/java/com/cleanroommc/modularui/test/TestGui.java`:
```java
IPanelHandler otherPanel = IPanelHandler.simple(panel, (mainPanel, player) -> {
    ModularPanel panel1 = new Dialog<>("Option Selection")
            .setDisablePanelsBelow(false).setDraggable(false).size(150, 120);
    return panel1.child(ButtonWidget.panelCloseButton())
            .child(new Grid()
                    .grid(availableMatrix)
                    .scrollable()
                    .pos(7, 7).right(16).bottom(7).name("available list"));
}, true);

panel.child(new ButtonWidget<>()
        .bottom(7).size(12, 12).leftRel(0.5f)
        .overlay(GuiTextures.ADD)
        .onMouseTapped(mouseButton -> {
            otherPanel.openPanel();
            return true;
        }));
```
Synced variant, `src/main/java/com/cleanroommc/modularui/test/TestTile.java`:
```java
IPanelHandler panelSyncHandler = syncManager.syncedPanel("other_panel", true, this::openSecondWindow);
// ... later ...
.onMousePressed(mouseButton -> { panelSyncHandler.openPanel(); return true; })
```

---

## `com.cleanroommc.modularui.api.ISyncedAction`

Functional callback invoked with the raw packet payload of a synced action (client→server or server→client one-shot signal).

```java
@FunctionalInterface
public interface ISyncedAction {
    @ApiStatus.OverrideOnly
    void invoke(@NotNull PacketBuffer packet);
}
```

`invoke(PacketBuffer packet)` is marked `@ApiStatus.OverrideOnly` — implement it (usually as a lambda registered with a sync manager), but it is called by the framework, not by consumers.

**Example (constructed, not from repo)**
```java
ISyncedAction action = packet -> {
    int value = packet.readVarIntFromBuffer();
    // react to the synced action
};
```

---

## `com.cleanroommc.modularui.api.ITheme`

A parsed theme: color/background style information for widgets, with parent-theme fallback.

```java
public interface ITheme {
    static ITheme getDefault();
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

| Method | Returns | Notes |
|---|---|---|
| `static getDefault()` | `ITheme` | shortcut for `IThemeApi.get().getDefaultTheme()` |
| `static get(String id)` | `ITheme` | shortcut for `IThemeApi.get().getTheme(id)` |
| `getId()` | `String` | theme id |
| `getParentTheme()` | `ITheme` | theme this one inherits unset values from |
| `getWidgetThemes()` | `Collection<WidgetThemeEntry<?>>` | unmodifiable view of all registered per-widget-key theme entries |
| `getFallback/getPanelTheme/getButtonTheme/getScrollbarTheme()` | `WidgetThemeEntry<WidgetTheme>` | shortcuts equivalent to `getWidgetTheme(IThemeApi.FALLBACK/PANEL/BUTTON/SCROLLBAR)` |
| `getItemSlotTheme/getFluidSlotTheme()` | `WidgetThemeEntry<SlotTheme>` | slot-specific theme entries |
| `getTextFieldTheme()` | `WidgetThemeEntry<TextFieldTheme>` | — |
| `getToggleButtonTheme()` | `WidgetThemeEntry<SelectableTheme>` | — |
| `getWidgetTheme(WidgetThemeKey<T>)` | `WidgetThemeEntry<T>` | generic lookup by key; the shortcuts above are implemented in terms of this |

**Example (constructed, not from repo)**
```java
ITheme theme = ITheme.get("mui:test_theme");
WidgetThemeEntry<WidgetTheme> buttonTheme = theme.getButtonTheme();
```

---

## `com.cleanroommc.modularui.api.IThemeApi`

**Central interface.** The single entry point for theme registration and lookup; also hosts the well-known `WidgetThemeKey` constants every built-in widget theme is keyed by, plus the JSON property-name constants used when parsing theme JSON.

```java
@ApiStatus.NonExtendable
public interface IThemeApi {
    WidgetThemeKey<WidgetTheme> FALLBACK, PANEL, BUTTON, CLOSE_BUTTON, SCROLLBAR;
    WidgetThemeKey<SlotTheme> ITEM_SLOT, FLUID_SLOT;
    WidgetThemeKey<TextFieldTheme> TEXT_FIELD;
    WidgetThemeKey<SelectableTheme> TOGGLE_BUTTON;
    WidgetThemeKey<SlotTheme> ITEM_SLOT_PLAYER, ITEM_SLOT_PLAYER_HOTBAR, ITEM_SLOT_PLAYER_MAIN_INV, ITEM_SLOT_PLAYER_ARMOR;
    String HOVER_SUFFIX = ":hover";
    String PARENT, DEFAULT_WIDTH, DEFAULT_HEIGHT, BACKGROUND, COLOR, TEXT_COLOR, TEXT_SHADOW, ICON_COLOR,
           SLOT_HOVER_COLOR, SLOT_CUSTOM_TEXTURES, SLOT_INVENTORY_BACKGROUND, SLOT_HOTBAR_BACKGROUND,
           MARKED_COLOR, HINT_COLOR, SELECTED_BACKGROUND, SELECTED_COLOR, SELECTED_TEXT_COLOR,
           SELECTED_TEXT_SHADOW, SELECTED_ICON_COLOR; // JSON property name constants

    @Contract(pure = true) static IThemeApi get(); // returns ThemeAPI.INSTANCE

    ITheme getDefaultTheme();
    @NotNull ITheme getTheme(String id);
    boolean hasTheme(String id);
    void registerTheme(String id, JsonBuilder json);
    default void registerTheme(ThemeBuilder<?> themeBuilder);
    List<JsonBuilder> getJavaDefaultThemes(String id);
    default ITheme getThemeForScreen(String owner, String name, @Nullable String defaultTheme, @Nullable String fallbackTheme);
    ITheme getThemeForScreen(String owner, String name, @Nullable String panel, @Nullable String defaultTheme, @Nullable String fallbackTheme);
    default ITheme getThemeForScreen(ModularPanel panel, @Nullable String defaultTheme);
    default ITheme getThemeForScreen(ModularScreen screen, @Nullable String defaultTheme);
    default void registerThemeForScreen(String owner, String name, String theme);
    void registerThemeForScreen(String screen, String theme);
    <T extends WidgetTheme> WidgetThemeKey<T> registerWidgetTheme(String id, T defaultTheme, T defaultHoverTheme, WidgetThemeParser<T> parser);
    default <T extends WidgetTheme> WidgetThemeKeyBuilder<T> widgetThemeKeyBuilder(String id, Class<T> type);
    @UnmodifiableView List<WidgetThemeKey<?>> getWidgetThemeKeys();
}
```

### Built-in `WidgetThemeKey` constants

| Constant | Type | Default theme | Notes |
|---|---|---|---|
| `FALLBACK` | `WidgetTheme` | `darkTextNoShadow(18, 18, null)` | root fallback for any widget without a specific theme |
| `PANEL` | `WidgetTheme` | `darkTextNoShadow(176, 166, GuiTextures.MC_BACKGROUND)` | — |
| `BUTTON` | `WidgetTheme` | `whiteTextShadow(18, 18, MC_BUTTON)`, hover `MC_BUTTON_HOVERED` | used by `ButtonWidget` and any `Widget.widgetTheme(...)` that wants button styling |
| `CLOSE_BUTTON` | `WidgetTheme` | `whiteTextShadow(10, 10, MC_BUTTON)` | deliberately does **not** inherit `BUTTON` |
| `SCROLLBAR` | `WidgetTheme` | `darkTextNoShadow(4, 4, Scrollbar.VANILLA)` | — |
| `ITEM_SLOT` / `FLUID_SLOT` | `SlotTheme` | `new SlotTheme(GuiTextures.SLOT_ITEM/SLOT_FLUID)` | — |
| `TEXT_FIELD` | `TextFieldTheme` | `new TextFieldTheme(0xFF2F72A8, 0xFF5F5F5F)` | — |
| `TOGGLE_BUTTON` | `SelectableTheme` | `whiteTextShadow(...)`, hover with `IDrawable.NONE` disabled state | — |
| `ITEM_SLOT_PLAYER`, `..._PLAYER_HOTBAR`, `..._PLAYER_MAIN_INV`, `..._PLAYER_ARMOR` | `SlotTheme` | sub-keys of `ITEM_SLOT` via `createSubKey(...)` | inherit unset properties from `ITEM_SLOT` |

### Methods

| Method | Params | Returns | Notes |
|---|---|---|---|
| `static get()` | — | `IThemeApi` | `@Contract(pure = true)`; returns `ThemeAPI.INSTANCE` |
| `getDefaultTheme()` | — | `ITheme` | absolute fallback theme |
| `getTheme(String id)` | theme id | `ITheme` (`@NotNull`) | falls back to `getDefaultTheme()` if not found |
| `hasTheme(String id)` | theme id | `boolean` | — |
| `registerTheme(String id, JsonBuilder json)` | id, json builder | `void` | resource-pack themes always take priority over java-registered ones |
| `registerTheme(ThemeBuilder<?>)` | builder | `void` | default; delegates to the above using `themeBuilder.getId()` |
| `getJavaDefaultThemes(String id)` | theme id | `List<JsonBuilder>` | all java-side registered json fragments for a theme id |
| `getThemeForScreen(owner, name, panel, defaultTheme, fallbackTheme)` | screen owner/name/panel + fallbacks | `ITheme` | full resolver; the 4-arg overload delegates here with `panel = null` |
| `getThemeForScreen(ModularPanel panel, defaultTheme)` | panel, default | `ITheme` | default; resolves owner/name/panel/themeOverride from the panel's `ModularScreen` |
| `getThemeForScreen(ModularScreen screen, defaultTheme)` | screen, default | `ITheme` | default; convenience over the 4-arg overload |
| `registerThemeForScreen(owner, name, theme)` / `registerThemeForScreen(screen, theme)` | ids + theme id | `void` | registers a theme override for a specific screen; resource packs still win |
| `registerWidgetTheme(id, defaultTheme, defaultHoverTheme, parser)` | new widget theme registration | `WidgetThemeKey<T>` | store the returned key statically and expose it publicly |
| `widgetThemeKeyBuilder(id, type)` | id, theme class | `WidgetThemeKeyBuilder<T>` | default; used internally to build the constants above |
| `getWidgetThemeKeys()` | — | `List<WidgetThemeKey<?>>` (unmodifiable) | all registered widget theme keys |

**Example (from repo)** — `IThemeApi.BUTTON` used directly as a widget theme, `src/main/java/com/cleanroommc/modularui/test/TestGui.java`:
```java
new Widget<>()
    .addTooltipLine(line)
    .widgetTheme(IThemeApi.BUTTON)
    .overlay(IKey.str(line))
    .expanded().heightRel(1f)
```
Registering a custom theme, `src/main/java/com/cleanroommc/modularui/test/TestEventHandler.java`:
```java
private static final ThemeBuilder<?> testTheme = new ThemeBuilder<>(TEST_THEME)
        .defaultColor(Color.BLUE_ACCENT.brighter(0))
        .widgetTheme(IThemeApi.TOGGLE_BUTTON, new SelectableTheme.Builder<>()
                .color(Color.BLUE_ACCENT.brighter(0))
                .selectedColor(Color.WHITE.main))
        .textColor(IThemeApi.TEXT_FIELD, Color.DEEP_PURPLE.main);

@SubscribeEvent
public void onThemeReload(ReloadThemeEvent.Pre event) {
    IThemeApi.get().registerTheme(testTheme);
}
```

---

## `com.cleanroommc.modularui.api.ITreeNode<T extends ITreeNode<T>>`

Minimal generic tree-node contract. `IWidget extends ITreeNode<IWidget>`.

```java
public interface ITreeNode<T extends ITreeNode<T>> {
    T getParent();
    default boolean hasParent() { return getParent() != null; }
    List<T> getChildren();
    default boolean hasChildren() { return !getChildren().isEmpty(); }
}
```

| Method | Returns | Notes |
|---|---|---|
| `getParent()` | `T` | abstract |
| `hasParent()` | `boolean` | default: `getParent() != null` — note `IWidget` **overrides** this to mean `isValid()` instead, since the root widget technically has a non-null parent reference |
| `getChildren()` | `List<T>` | abstract |
| `hasChildren()` | `boolean` | default: `!getChildren().isEmpty()` |

**Example (constructed, not from repo)**
```java
class Node implements ITreeNode<Node> {
    Node parent;
    List<Node> children = new ArrayList<>();
    public Node getParent() { return parent; }
    public List<Node> getChildren() { return children; }
}
```

---

## `com.cleanroommc.modularui.api.MCHelper`

Static utility class (not an interface) bundling client-only Minecraft accessors and tooltip helpers so calling code doesn't need to null-check `Minecraft.getMinecraft()` everywhere.

```java
public class MCHelper { /* all static */ }
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `hasMc()` | — | `boolean` | `true` if running as a dedicated client and `getMc() != null` |
| `getMc()` | — | `@Nullable Minecraft` | `@SideOnly(CLIENT)` |
| `getPlayer()` | — | `@Nullable EntityPlayer` | client player, or `null` if no MC instance |
| `closeScreen()` | — | `boolean` | closes the current screen (`displayGuiScreen(null)`); always returns `false` |
| `popScreen(boolean openParentOnClose, GuiScreen parent)` | flag, fallback screen | `void` | if a player exists and `openParentOnClose`, redisplays `parent` and reopens its syncer via `ModularNetwork.CLIENT`; otherwise closes to `null`. If no player (not in world), always redisplays `parent` |
| `displayScreen(GuiScreen screen)` | screen | `boolean` | `true` if MC instance existed and screen was displayed |
| `getCurrentScreen()` | — | `GuiScreen` | `null` if no MC instance |
| `getFontRenderer()` | — | `FontRenderer` | `null` if no MC instance |
| `getItemToolTip(ItemStack item)` | stack | `List<String>` | uses NEI's multiline tooltip if NEI is loaded and current screen is a `GuiContainer`, otherwise vanilla `item.getTooltip(...)` with rarity/gray coloring applied |
| `getFluidTooltip(FluidStack fluid)` | fluid | `List<String>` | localized name + GT5U tooltip hook + registry name (advanced tooltips) + unique registry name if shift held |
| `getAdditionalFluidTooltip(FluidStack fluid)` | fluid | `List<String>` | only populated while shift is held: temperature, gas/liquid state, NEI amount detail |

**Example (constructed, not from repo)**
```java
if (MCHelper.hasMc()) {
    EntityPlayer player = MCHelper.getPlayer();
    List<String> tip = MCHelper.getItemToolTip(new ItemStack(Items.diamond));
}
```

---

## `com.cleanroommc.modularui.api.NEISettings`

Per-screen control surface for NEI (Not Enough Items) integration. Safe to call even when NEI is not installed (see `DUMMY`).

```java
@ApiStatus.NonExtendable
public interface NEISettings {
    void enableNEI();
    void disableNEI();
    void defaultNEI();
    boolean isNEIEnabled(ModularScreen screen);
    void addNEIExclusionArea(Rectangle area);
    void removeNEIExclusionArea(Rectangle area);
    void addNEIExclusionArea(IWidget area);
    void removeNEIExclusionArea(IWidget area);

    NEISettings DUMMY = /* no-op implementation */;
}
```

| Method | Notes |
|---|---|
| `enableNEI()` / `disableNEI()` | force NEI on/off for the owning screen |
| `defaultNEI()` | only enable NEI for synced GUIs (the library default) |
| `isNEIEnabled(ModularScreen screen)` | current effective state for a screen |
| `addNEIExclusionArea(Rectangle)` / `removeNEIExclusionArea(Rectangle)` | raw rectangle exclusion zone (must remove the same instance) |
| `addNEIExclusionArea(IWidget)` / `removeNEIExclusionArea(IWidget)` | widget-based exclusion zone; javadoc explicitly recommends widgets use **this** overload instead of the `Rectangle` one, especially for widgets outside their panel |
| `DUMMY` | fully no-op implementation used when NEI integration isn't applicable |

No direct usage in `test/`; NEI/recipe-viewer settings are normally reached off `ModularScreen`, not constructed directly.

**Example (constructed, not from repo)**
```java
NEISettings nei = screen.getNEISettings(); // hypothetical accessor
nei.addNEIExclusionArea(myWidget);
```

---

## `com.cleanroommc.modularui.api.RecipeViewerSettings`

Generalized successor to `NEISettings` covering any recipe-viewer mod (JEI, NEI, EMI). Same shape, with legacy `*RecipeViewer*`-named methods kept as `@Deprecated` aliases (`@ApiStatus.ScheduledForRemoval(inVersion = "3.2.0")`).

```java
@ApiStatus.NonExtendable
public interface RecipeViewerSettings {
    void enable();
    void disable();
    void defaultState();
    boolean isEnabled(ModularScreen screen);
    void addExclusionArea(Rectangle area);
    void removeExclusionArea(Rectangle area);
    void addExclusionArea(IWidget area);
    void removeExclusionArea(IWidget area);

    RecipeViewerSettings DUMMY = /* no-op implementation */;
}
```

| Current method | Deprecated alias (removed in 3.2.0) |
|---|---|
| `enable()` | `enableRecipeViewer()` |
| `disable()` | `disableRecipeViewer()` |
| `defaultState()` | `defaultRecipeViewerState()` |
| `isEnabled(ModularScreen)` | `isRecipeViewerEnabled(ModularScreen)` |
| `addExclusionArea(Rectangle)` | `addRecipeViewerExclusionArea(Rectangle)` |
| `removeExclusionArea(Rectangle)` | `removeRecipeViewerExclusionArea(Rectangle)` |
| `addExclusionArea(IWidget)` | `addRecipeViewerExclusionArea(IWidget)` |
| `removeExclusionArea(IWidget)` | `removeRecipeViewerExclusionArea(IWidget)` |

Same "prefer the `IWidget` overload" guidance as `NEISettings`. `DUMMY` is a fully no-op implementation.

Inferred: `Widget.excludeAreaInRecipeViewer()` (seen used on `Expandable` in `TestTile.java`) sets an internal flag that the widget tree consumes to call `addExclusionArea(IWidget)`/`removeExclusionArea(IWidget)` on this interface — the flag itself lives on `Widget`, not on this interface.

**Example (constructed, not from repo)**
```java
RecipeViewerSettings viewer = screen.getRecipeViewerSettings(); // hypothetical accessor
viewer.addExclusionArea(myWidget);
```

---

## `com.cleanroommc.modularui.api.SlotAccessor`

Accessor interface (AT/mixin-style) exposing `protected` methods of vanilla `net.minecraft.inventory.Slot`.

```java
public interface SlotAccessor {
    void invokeOnCrafting(ItemStack stack, int amount);
    void invokeOnSwapCraft(int p_190900_1_);
    void invokeOnCrafting(ItemStack stack);
}
```

| Method | Purpose |
|---|---|
| `invokeOnCrafting(ItemStack stack, int amount)` | exposes `Slot.onCrafting(ItemStack, int)` |
| `invokeOnSwapCraft(int p_190900_1_)` | exposes `Slot.onSwapCraft(int)` (obfuscated param name from MCP) |
| `invokeOnCrafting(ItemStack stack)` | exposes `Slot.onCrafting(ItemStack)` |

Implemented via a mixin/accessor mechanism on the real `Slot` class; not meant to be implemented manually by consumers.

**Example (constructed, not from repo)**
```java
((SlotAccessor) slot).invokeOnCrafting(resultStack, craftedAmount);
```

---

## `com.cleanroommc.modularui.api.UIFactory<D extends GuiData>`

Factory abstraction responsible for opening synced GUIs: builds panels/screens on both sides and (de)serializes the `GuiData` used to recreate context (position, player, etc.) over the network.

```java
@ApiStatus.AvailableSince("2.4.0")
public interface UIFactory<D extends GuiData> {
    @NotNull String getFactoryName();
    @ApiStatus.OverrideOnly ModularPanel createPanel(D guiData, PanelSyncManager syncManager, UISettings settings);
    @SideOnly(Side.CLIENT) @ApiStatus.OverrideOnly ModularScreen createScreen(D guiData, ModularPanel mainPanel);
    @SideOnly(Side.CLIENT) @ApiStatus.OverrideOnly default IMuiScreen createScreenWrapper(ModularContainer container, ModularScreen screen);
    default ModularContainer createContainer();
    default boolean canInteractWith(EntityPlayer player, D guiData);
    @ApiStatus.OverrideOnly void writeGuiData(D guiData, PacketBuffer buffer);
    @NotNull @ApiStatus.OverrideOnly D readGuiData(EntityPlayer player, PacketBuffer buffer);
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getFactoryName()` | — | `String` (`@NotNull`) | must be constant; used as a network/registry key |
| `createPanel(D, PanelSyncManager, UISettings)` | gui data, sync manager, settings | `ModularPanel` | `@ApiStatus.OverrideOnly`; called on both client and server |
| `createScreen(D, ModularPanel)` | gui data, panel from `createPanel` | `ModularScreen` | client-only, `@ApiStatus.OverrideOnly` |
| `createScreenWrapper(ModularContainer, ModularScreen)` | container, screen from `createScreen` | `IMuiScreen` | default returns `new GuiContainerWrapper(container, screen)`. Throws `IllegalStateException` (thrown by the *caller*, not this method) if the resulting wrapper isn't a `GuiContainer` or wraps the wrong container |
| `createContainer()` | — | `ModularContainer` | default `new ModularContainer()`; used when `UISettings` has no custom container supplier |
| `canInteractWith(EntityPlayer, D)` | player, gui data | `boolean` | default `player == guiData.getPlayer()`; polled every tick while the UI is open (unless overridden via `UISettings`) — returning `false` force-closes the UI |
| `writeGuiData(D, PacketBuffer)` | gui data, buffer | `void` | `@ApiStatus.OverrideOnly`; serializes gui data for opening over the network |
| `readGuiData(EntityPlayer, PacketBuffer)` | player, buffer | `D` (`@NotNull`) | `@ApiStatus.OverrideOnly`; deserializes counterpart of `writeGuiData` |

**Example (from repo)** — factories are typically obtained from `GuiFactories`, not implemented from scratch. `src/main/java/com/cleanroommc/modularui/test/TestBlock.java` and `TestItem.java`:
```java
// TestBlock.java — opens the tile-entity UIFactory
GuiFactories.tileEntity().open(playerIn, x, y, z);

// TestItem.java — opens the player-inventory UIFactory
GuiFactories.playerInventory().openFromMainHand(player);
```

---

## `com.cleanroommc.modularui.api.UpOrDown`

Enum for scroll direction (also doubles as a generic "increment/decrement" signal).

```java
public enum UpOrDown {
    UP(1), DOWN(-1);
    public final int modifier;
}
```

| Method | Returns | Notes |
|---|---|---|
| `isUp()` | `boolean` | `this == UP` |
| `isDown()` | `boolean` | `this == DOWN` |
| `modifier` (field) | `int` | `+1` for `UP`, `-1` for `DOWN` — usable directly as a multiplier |

Used by `Interactable.onMouseScroll(UpOrDown, int)` and `IGuiAction.MouseScroll`.

**Example (constructed, not from repo)**
```java
int newValue = current + direction.modifier * amount;
```

---

# `api/event/`

## `com.cleanroommc.modularui.api.event.KeyboardInputEvent`

Forge event fired around `GuiScreen.handleKeyboardInput()`.

```java
public class KeyboardInputEvent extends GuiScreenEvent {
    public KeyboardInputEvent(GuiScreen gui) { super(gui); }

    @Cancelable public static class Pre extends KeyboardInputEvent { ... }
    @Cancelable public static class Post extends KeyboardInputEvent { ... }
}
```

| Class | Fired | Cancel effect |
|---|---|---|
| `Pre` | before keyboard input is handled | cancels to **bypass** `GuiScreen.handleKeyboardInput()` entirely |
| `Post` | after handling, only if the active screen didn't change as a result | cancel to prevent other handlers from reacting to the same input |

**Example (constructed, not from repo)**
```java
@SubscribeEvent
public void onKeyPre(KeyboardInputEvent.Pre event) {
    if (shouldBlockInput()) event.setCanceled(true);
}
```

---

## `com.cleanroommc.modularui.api.event.MouseInputEvent`

Forge event fired around `GuiScreen.handleMouseInput()`. Structurally identical to `KeyboardInputEvent`.

```java
public class MouseInputEvent extends GuiScreenEvent {
    @Cancelable public static class Pre extends MouseInputEvent { ... }
    @Cancelable public static class Post extends MouseInputEvent { ... }
}
```

| Class | Fired | Cancel effect |
|---|---|---|
| `Pre` | before mouse input is handled | cancels to bypass `GuiScreen.handleMouseInput()` |
| `Post` | after handling, if screen didn't change | cancel to stop other handlers from reacting |

**Example (constructed, not from repo)**
```java
@SubscribeEvent
public void onMousePost(MouseInputEvent.Post event) {
    // react after vanilla mouse handling, then suppress further handlers
    event.setCanceled(true);
}
```

---

# `api/inventory/`

## `com.cleanroommc.modularui.api.inventory.ClickType`

Enum mirroring vanilla inventory click types (pickup/shift-click/swap/etc.), with ordinal (de)serialization helpers for network use.

```java
public enum ClickType {
    PICKUP, QUICK_MOVE, SWAP, CLONE, THROW, QUICK_CRAFT, PICKUP_ALL;
    public static final ClickType[] VALUES = values();
    public static ClickType fromNumber(int number);
    public int toNumber();
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `static fromNumber(int number)` | ordinal | `ClickType` | `VALUES[number]`; no bounds checking |
| `toNumber()` | — | `int` | `ordinal()` — pairs with `fromNumber` for compact network encoding |
| `VALUES` (field) | — | `ClickType[]` | cached `values()` to avoid re-allocating the array |

**Example (constructed, not from repo)**
```java
buffer.writeByte(ClickType.QUICK_MOVE.toNumber());
// ...
ClickType type = ClickType.fromNumber(buffer.readByte());
```

---

# `api/layout/`

## `com.cleanroommc.modularui.api.layout.ILayoutWidget`

Contract for widgets that arrange their children (flex-box-like containers: `Flow`, `Grid`, `ListWidget`, etc.). Extends `INotifyEnabled` so layout containers are told when a child's enabled state changes.

```java
public interface ILayoutWidget extends INotifyEnabled {
    boolean layoutWidgets();
    default boolean postLayoutWidgets() { return true; }
    default boolean canCoverByDefaultSize(GuiAxis axis) { return false; }
    default boolean shouldIgnoreChildSize(IWidget child) { return false; }
    @Override default void onChildChangeEnabled(IWidget child, boolean enabled) { layoutWidgets(); postLayoutWidgets(); }
}
```

| Method | Returns | Notes |
|---|---|---|
| `layoutWidgets()` | `boolean` | abstract — positions/sizes children, calling `IResizeable.setSizeResized`/`setPosResized`/etc. (or `updateResized()`) on each. **Must** call one of the `setResized`-family methods on every child even if `shouldIgnoreChildSize` is true, or the resize pass can fail to converge. Return `true` if layout is fully done and no further iteration is needed |
| `postLayoutWidgets()` | `boolean` | default `true`; called after post-calculation, guaranteed to run when this widget is fully calculated |
| `canCoverByDefaultSize(GuiAxis axis)` | `boolean` | default `false` |
| `shouldIgnoreChildSize(IWidget child)` | `boolean` | default `false`; return `true` to exclude a child's size/margin from wrapping-size calculations (typically for disabled children being collapsed) |
| `onChildChangeEnabled(IWidget, boolean)` | `void` | overrides `INotifyEnabled`; default re-runs `layoutWidgets()` + `postLayoutWidgets()` |

Inferred: implemented by `com.cleanroommc.modularui.widgets.layout.Flow`, `Grid`, and `com.cleanroommc.modularui.widgets.ListWidget` (all heavily used throughout `test/`, e.g. `Flow.column()`, `Flow.row()`, `new Grid()`, `new ListWidget<>()`), though this interface's methods are internal — consumers use the builder methods on those concrete classes rather than calling `layoutWidgets()` directly.

**Example (from repo, usage of an `ILayoutWidget` implementation)** — `src/main/java/com/cleanroommc/modularui/test/TestGui.java`:
```java
panel.child(Flow.row().name("row_" + line)
        .child(new Widget<>().expanded().heightRel(1f))
        .child(new ButtonWidget<>().width(10).heightRel(1f)));
```

---

## `com.cleanroommc.modularui.api.layout.IResizeParent`

Read-only view of an element's resize-calculation state (position/size "calculated" flags). Base of `IResizeable`.

```java
public interface IResizeParent {
    Area getArea();
    boolean isXCalculated();
    boolean isYCalculated();
    boolean isWidthCalculated();
    boolean isHeightCalculated();
    boolean areChildrenCalculated();
    boolean isLayoutDone();
    boolean canRelayout(boolean isParentLayout);
    default boolean isSizeCalculated(GuiAxis axis);
    default boolean isPosCalculated(GuiAxis axis);
    default boolean isSelfFullyCalculated(boolean isParentLayout);
    default boolean isSelfFullyCalculated();
    default boolean isFullyCalculated();
    default boolean isFullyCalculated(boolean isParentLayout);
    boolean isXMarginPaddingApplied();
    boolean isYMarginPaddingApplied();
}
```

| Method | Returns | Notes |
|---|---|---|
| `getArea()` | `Area` | element's area |
| `isXCalculated/isYCalculated/isWidthCalculated/isHeightCalculated()` | `boolean` | per-axis/per-dimension calculated flags |
| `areChildrenCalculated()` | `boolean` | whether all children finished resizing |
| `isLayoutDone()` | `boolean` | whether `ILayoutWidget.layoutWidgets()` finished |
| `canRelayout(boolean isParentLayout)` | `boolean` | whether another relayout pass is still needed |
| `isSizeCalculated(GuiAxis)` / `isPosCalculated(GuiAxis)` | `boolean` | default; axis-generic wrappers around the 4 raw flags |
| `isSelfFullyCalculated()` | `boolean` | default; all 4 raw flags true |
| `isSelfFullyCalculated(boolean isParentLayout)` | `boolean` | default; `isSelfFullyCalculated() && !canRelayout(isParentLayout)` |
| `isFullyCalculated()` | `boolean` | default; self + children + layout all done |
| `isFullyCalculated(boolean isParentLayout)` | `boolean` | default; adds the relayout check |
| `isXMarginPaddingApplied/isYMarginPaddingApplied()` | `boolean` | whether margin/padding have been applied per axis |

Inferred: purely internal to the resizer engine (`com.cleanroommc.modularui.widget.sizer`); not implemented or called directly by GUI-building code.

**Example (constructed, not from repo)**
```java
if (resizeParent.isFullyCalculated()) {
    // safe to read final Area
}
```

---

## `com.cleanroommc.modularui.api.layout.IResizeable`

Mutable counterpart of `IResizeParent` — the actual engine interface that drives one resize pass. "Usually not implemented or interacted with by library users" (per javadoc).

```java
public interface IResizeable extends IResizeParent {
    void initResizing(boolean onOpen);
    boolean resize(boolean isParentLayout);
    boolean postResize();
    default void preApplyPos() {}
    default void applyPos() {}
    void setChildrenResized(boolean resized);
    void setLayoutDone(boolean done);
    default void setAxisResized(GuiAxis axis, boolean pos, boolean size);
    void setXAxisResized(boolean pos, boolean size);
    void setYAxisResized(boolean pos, boolean size);
    default void setResized(boolean x, boolean y, boolean w, boolean h);
    default void setPosResized(boolean x, boolean y);
    default void setSizeResized(boolean w, boolean h);
    default void setXResized(boolean v);
    default void setYResized(boolean v);
    default void setPosResized(GuiAxis axis, boolean v);
    default void setWidthResized(boolean v);
    default void setHeightResized(boolean v);
    default void setSizeResized(GuiAxis axis, boolean v);
    default void setResized(boolean b);
    default void updateResized();
    void setXMarginPaddingApplied(boolean b);
    void setYMarginPaddingApplied(boolean b);
    default void setMarginPaddingApplied(boolean b);
    default void setMarginPaddingApplied(GuiAxis axis, boolean b);
}
```

| Method | Notes |
|---|---|
| `initResizing(boolean onOpen)` | called once before a resize pass begins |
| `resize(boolean isParentLayout)` | performs (a step of) resizing; returns `true` if fully resized |
| `postResize()` | called if `resize` returned `false`, after children resized; returns whether now fully resized |
| `preApplyPos()` / `applyPos()` | defaults no-op / converts relative-to-resizer-parent position into relative-to-widget-parent position, called after the whole tree is resized |
| `setChildrenResized(boolean)` / `setLayoutDone(boolean)` | raw flag setters backing `IResizeParent.areChildrenCalculated()`/`isLayoutDone()` |
| `setXAxisResized(pos, size)` / `setYAxisResized(pos, size)` | raw per-axis setters; all the `set*Resized` conveniences below funnel into these two |
| `setAxisResized/setResized/setPosResized/setSizeResized/setXResized/setYResized/setWidthResized/setHeightResized/updateResized` | default convenience combinators over the two raw setters, in both axis-generic (`GuiAxis` parameter) and explicit-axis (`X`/`Y` named) forms |
| `setXMarginPaddingApplied(boolean)` / `setYMarginPaddingApplied(boolean)` | raw setters; `setMarginPaddingApplied` (2 overloads) are convenience combinators |

`ILayoutWidget.layoutWidgets()` javadoc requires calling one of the `setResized`-family methods (or `updateResized()`) on every laid-out child.

**Example (constructed, not from repo)** — illustrates the contract an `ILayoutWidget.layoutWidgets()` implementation follows, not a direct API call site:
```java
for (IWidget child : getChildren()) {
    child.getArea().setSize(GuiAxis.X, computedWidth);
    child.resizer().setSizeResized(true, true);
}
```

---

## `com.cleanroommc.modularui.api.layout.IViewport`

A gui element that can transform its children's coordinate space (e.g. a scroll view). Extends `IWidget`.

```java
public interface IViewport extends IWidget {
    default void transformChildren(IViewportStack stack) {}
    default void getWidgetsAt(IViewportStack stack, HoveredWidgetList widgets, int x, int y);
    default void getSelfAt(IViewportStack stack, HoveredWidgetList widgets, int x, int y);
    default void preDraw(ModularGuiContext context, boolean transformed) {}
    default void postDraw(ModularGuiContext context, boolean transformed) {}

    static void getChildrenAt(IWidget parent, IViewportStack stack, HoveredWidgetList widgetList, int x, int y);
    static boolean foreachChild(IViewportStack stack, IWidget parent, Predicate<IWidget> predicate, int context);
}
```

| Method | Notes |
|---|---|
| `transformChildren(IViewportStack stack)` | default no-op; apply this viewport's shift/scroll transform to the stack before children are gathered/drawn |
| `getWidgetsAt(stack, widgets, x, y)` | default delegates to static `getChildrenAt` if `hasChildren()`; called with this viewport's transform already applied |
| `getSelfAt(stack, widgets, x, y)` | default adds `this` to `widgets` if `isInside(stack, x, y)`; called **before** `getWidgetsAt`, without this viewport's own transform applied |
| `preDraw`/`postDraw(context, transformed)` | defaults no-op; each called **twice** per frame — once with the viewport's transform active, once without |
| `static getChildrenAt(...)` | recursive helper walking a widget tree, pushing/popping viewport transforms as needed, used to build hover lists |
| `static foreachChild(...)` | recursive helper walking a tree while a `Predicate<IWidget>` returns `true`; short-circuits on first `false` |

**Example (from repo)** — `TestGuis.TestPanel` (in `src/main/java/com/cleanroommc/modularui/test/TestGuis.java`) is not an `IViewport` itself, but shows the sibling method `IWidget.transform(IViewportStack)` that viewports also use to push transforms:
```java
private static class TestPanel extends ModularPanel {
    @Override
    public void transform(IViewportStack stack) {
        super.transform(stack);
        stack.translate(50, 50);
        stack.rotateZ(angle);
        stack.scale(scale, scale);
        stack.translate(-50, -50);
    }
}
```
Inferred: real `IViewport` implementations (scrollable containers) live in `com.cleanroommc.modularui.widget.AbstractScrollWidget`.

---

## `com.cleanroommc.modularui.api.layout.IViewportStack`

**Central low-level interface.** Tracks the matrix stack (translate/rotate/scale) used for both rendering transforms and hit-testing, plus the active viewport (clip-rect) stack.

```java
@ApiStatus.NonExtendable
public interface IViewportStack {
    void reset();
    Area getViewport();
    void pushViewport(IViewport viewport, Area area);
    void pushMatrix();
    void popViewport(IViewport viewport);
    void popMatrix();
    int getStackSize();
    void popUntilIndex(int index);
    void popUntilViewport(IViewport viewport);
    void translate(float x, float y);
    void translate(float x, float y, float z);
    void rotate(float angle, float x, float y, float z);
    void rotateZ(float angle);
    void scale(float x, float y);
    void multiply(Matrix4f matrix);
    void resetCurrent();
    int transformX(float x, float y);
    int transformY(float x, float y);
    int unTransformX(float x, float y);
    int unTransformY(float x, float y);
    default Vector3f transform(Vector3f vec);
    Vector3f transform(Vector3f vec, Vector3f dest);
    default Vector3f unTransform(Vector3f vec);
    Vector3f unTransform(Vector3f vec, Vector3f dest);
    void applyToOpenGl();
    @Nullable TransformationMatrix peek();
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `reset()` | — | `void` | clears all viewports and matrices |
| `getViewport()` | — | `Area` | current (topmost) clip area |
| `pushViewport(IViewport, Area)` | viewport, its area | `void` | pushes a viewport **and** a new matrix |
| `pushMatrix()` | — | `void` | pushes a matrix only, no viewport |
| `popViewport(IViewport)` | viewport to remove | `void` | throws `IllegalStateException` if it doesn't match the current top |
| `popMatrix()` | — | `void` | throws `IllegalStateException` if the top entry is a viewport (use `popViewport` for those) |
| `getStackSize()` | — | `int` | — |
| `popUntilIndex(int index)` | index | `void` | pops everything above `index` |
| `popUntilViewport(IViewport)` | viewport | `void` | pops everything above the given viewport |
| `translate(x, y[, z])` | offsets | `void` | applies to the current top matrix |
| `rotate(angle, x, y, z)` | radians, axis flags (1/0) | `void` | generic axis rotation |
| `rotateZ(angle)` | radians | `void` | convenience for pure 2D rotation |
| `scale(x, y)` | factors | `void` | — |
| `multiply(Matrix4f)` | JOML matrix | `void` | raw matrix multiply into the top |
| `resetCurrent()` | — | `void` | resets top matrix back to the one below it |
| `transformX/Y(x, y)` | position | `int` | forward-transforms a coordinate with current matrix stack |
| `unTransformX/Y(x, y)` | position | `int` | inverse-transforms (screen→local) |
| `transform(Vector3f)` / `transform(Vector3f vec, Vector3f dest)` | vector [+ dest] | `Vector3f` | 1-arg default mutates `vec` in place via the 2-arg abstract method |
| `unTransform(Vector3f)` / `unTransform(Vector3f, Vector3f)` | vector [+ dest] | `Vector3f` | inverse of the above |
| `applyToOpenGl()` | — | `void` | pushes the accumulated transform onto the real GL matrix stack |
| `peek()` | — | `@Nullable TransformationMatrix` | top matrix, or `null` if stack empty |

Deprecated overloads exist for the legacy `com.cleanroommc.modularui.utils.Matrix4f`/`Vector3f` types (`multiply`, `transform`, `unTransform`), scheduled for removal in 3.2.0 — prefer the JOML (`org.joml`) overloads.

`IWidget.isInside(IViewportStack, int, int, boolean)` and `IWidget.transform(IViewportStack)` are the two most common call sites consumers interact with indirectly.

**Example (from repo)** — `src/main/java/com/cleanroommc/modularui/test/TestGuis.java`, `TestPanel.transform`:
```java
@Override
public void transform(IViewportStack stack) {
    super.transform(stack);
    stack.translate(50, 50);
    stack.rotateZ(angle);
    stack.scale(scale, scale);
    stack.translate(-50, -50);
}
```

---

# `api/widget/`

## `com.cleanroommc.modularui.api.widget.IWidget`

**Central interface.** The contract every widget in the tree implements: extends `IGuiElement`'s conceptual role (superseding the deprecated `IGuiElement`) plus `ITreeNode<IWidget>`. Combines hierarchy, geometry, hover/interaction plumbing, draw hooks, resize hooks, and enabled/name/type utilities.

```java
public interface IWidget extends IGuiElement, ITreeNode<IWidget> {
    ModularScreen getScreen();
    @NotNull @Override IWidget getParent();
    @Override default boolean hasParent() { return isValid(); }
    ModularGuiContext getContext();
    @NotNull ModularPanel getPanel();
    @Override Area getArea();
    default Area getParentArea();
    default boolean isInside(IViewportStack stack, int mx, int my);
    default boolean isInside(IViewportStack stack, int mx, int my, boolean absolute);
    default void onMouseStartHover() {}
    default void onMouseEndHover() {}
    default void onMouseEnterArea() {}
    default void onMouseLeaveArea() {}
    default boolean isHovering();
    default boolean isHoveringFor(int ticks) { return false; }
    default boolean isBelowMouse();
    default boolean isBelowMouseFor(int ticks) { return false; }
    default boolean canBeSeen(IViewportStack stack);
    default boolean canHover() { return true; }
    default boolean canClickThrough() { return true; }
    default boolean canHoverThrough() { return false; }
    default int getDefaultWidth() { return 18; }
    default int getDefaultHeight() { return 18; }
    void initialise(@NotNull IWidget parent, boolean late);
    void dispose();
    boolean isValid();
    default void onUpdate() {}
    default void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {}
    default void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {}
    default void drawOverlay(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {}
    default void drawForeground(ModularGuiContext context) {}
    default void transform(IViewportStack stack);
    default Object getAdditionalHoverInfo(IViewportStack viewportStack, int mouseX, int mouseY) { return null; }
    default WidgetThemeEntry<?> getWidgetTheme(ITheme theme) { return theme.getFallback(); }
    @NotNull @Override default List<IWidget> getChildren() { return Collections.emptyList(); }
    @Override default boolean hasChildren();
    void scheduleResize();
    boolean requiresResize();
    @NotNull @Override StandardResizer resizer();
    default IWidget resizerBuilder(Consumer<StandardResizer> builder);
    default void beforeResize(boolean onOpen) {}
    default void onResized() {}
    default void postResize() {}
    @Override boolean isEnabled();
    void setEnabled(boolean enabled);
    default boolean areAncestorsEnabled();
    @Nullable String getName();
    default boolean isName(String name);
    default boolean isType(Class<? extends IWidget> type);
    default boolean isNameAndType(String name, Class<? extends IWidget> type);
}
```

### Hierarchy & geometry

| Method | Returns | Notes |
|---|---|---|
| `getScreen()` | `ModularScreen` | screen this widget belongs to |
| `getParent()` | `IWidget` (`@NotNull`) | overrides `ITreeNode`/`IGuiElement` covariantly |
| `hasParent()` | `boolean` | **overridden** from `ITreeNode` default: means `isValid()`, not "parent != null" — the widget tree always has a non-null parent reference even for the root, so plain null-check would be wrong |
| `getContext()` | `ModularGuiContext` | the screen's gui context |
| `getPanel()` | `ModularPanel` (`@NotNull`) | owning panel |
| `getArea()` | `Area` | this widget's occupied rectangle (position/size/margin/padding) |
| `getParentArea()` | `Area` | default shortcut for `getParent().getArea()` |

### Hit-testing & hover

| Method | Notes |
|---|---|
| `isInside(stack, mx, my)` | default, `absolute = true` |
| `isInside(stack, mx, my, absolute)` | correct way to hit-test (accounts for viewport transforms) — prefer over `Area.isInside(int,int)`. When `absolute`, un-transforms `mx,my` via the stack first |
| `onMouseStartHover()` / `onMouseEndHover()` | fired when this specific widget becomes/stops being the *topmost* hovered widget (accounting for hover-passthrough) |
| `onMouseEnterArea()` / `onMouseLeaveArea()` | fired when the mouse enters/leaves this widget's area at any depth, or switches panel |
| `isHovering()` / `isHoveringFor(int ticks)` | whether directly below the mouse (optionally for at least N ticks); base default always `false` for the ticks variant — actual tracking lives in the concrete widget base class |
| `isBelowMouse()` / `isBelowMouseFor(int ticks)` | like above but "anywhere below the mouse", not necessarily topmost |
| `canBeSeen(IViewportStack stack)` | default: visual-only culling check via `Stencil.isInsideScissorArea` |
| `canHover()` | default `true`; return `false` to suppress hover state (and therefore tooltips) while still allowing children to be clicked through |
| `canClickThrough()` | default `true`; whether widgets below this one can still receive click callbacks when this widget didn't consume the click |
| `canHoverThrough()` | default `false` |
| `getDefaultWidth()`/`getDefaultHeight()` | default `18` each — fallback size when the resizer can't compute one |

### Lifecycle

| Method | Notes |
|---|---|
| `initialise(@NotNull IWidget parent, boolean late)` | validates/initializes this widget; `late = true` if called after the parent's tree already initialized (i.e. dynamically added later) |
| `dispose()` | invalidates the widget |
| `isValid()` | whether this widget currently exists in an active gui |
| `onUpdate()` | default no-op; called 20 times/second |

### Drawing (all default no-op, override to render)

| Method | Called |
|---|---|
| `drawBackground(context, widgetTheme)` | first |
| `draw(context, widgetTheme)` | after background, before overlay |
| `drawOverlay(context, widgetTheme)` | after `draw` |
| `drawForeground(context)` | separately, no transforms applied — for tooltips etc. |
| `transform(IViewportStack stack)` | default: `stack.translate(getArea().rx, getArea().ry, 0)` — override (calling `super.transform`) to add extra transforms, as `TestGuis.TestPanel` does (see `api.layout.IViewportStack` example above) |
| `getAdditionalHoverInfo(stack, mouseX, mouseY)` | default `null`; extra data attached to hover results |
| `getWidgetTheme(ITheme theme)` | default `theme.getFallback()`; override to pick a specific `WidgetThemeKey` |

### Children & resize

| Method | Notes |
|---|---|
| `getChildren()` | default empty list; overridden by container widgets |
| `hasChildren()` | default `!getChildren().isEmpty()` |
| `scheduleResize()` / `requiresResize()` | abstract; mark/check dirty-resize state |
| `resizer()` | `@NotNull`; returns this widget's `StandardResizer` (creates one if absent) — backs all `IPositioned` builder methods |
| `getFlex()` / `flex()` / `flexBuilder(...)` | **deprecated**, `@ApiStatus.ScheduledForRemoval(inVersion = "3.2.0")`; use `resizer()`/`resizerBuilder(...)` instead |
| `resizerBuilder(Consumer<StandardResizer> builder)` | default; applies `builder` to `resizer()` and returns `this` |
| `beforeResize(boolean onOpen)` / `onResized()` / `postResize()` | default no-op lifecycle hooks around a resize pass |

### Enabled state & identity

| Method | Notes |
|---|---|
| `isEnabled()` / `setEnabled(boolean)` | disabled widgets aren't drawn/interactable; children are treated as disabled too without their own flag flipping |
| `areAncestorsEnabled()` | default; walks up via `getParent()`/`hasParent()` checking `isEnabled()` at every level |
| `getName()` | `@Nullable` widget name (set via `Widget.name(...)`, seen throughout `test/` e.g. `.name("sortable list")`) |
| `isName(String)` / `isType(Class)` / `isNameAndType(String, Class)` | default convenience predicates for name/type matching |

**Example (from repo)** — nearly every widget-building call in `test/` exercises `IWidget`; a representative one from `src/main/java/com/cleanroommc/modularui/test/TestGui.java`:
```java
new Widget<>()
        .addTooltipLine(line)
        .widgetTheme(IThemeApi.BUTTON)
        .overlay(IKey.str(line))
        .expanded().heightRel(1f)
```
Enabled-state toggling, `src/main/java/com/cleanroommc/modularui/test/TestGuis.java` (`buildCollapseDisabledChildrenUI`):
```java
new Widget<>()
        .widthRel(1f).height(16)
        .widgetTheme(IThemeApi.BUTTON)
        .overlay(IKey.str(String.valueOf(i + 1)))
        .onUpdateListener(w -> {
            if (rnd.nextDouble() < 0.05) {
                w.setEnabled(!w.isEnabled());
            }
        })
```

---

## `com.cleanroommc.modularui.api.widget.IGuiElement`

**Deprecated predecessor of `IWidget`.** `@Deprecated`, `@ApiStatus.ScheduledForRemoval(inVersion = "3.2.0")`. Kept only for backward source-compatibility; do not implement in new code — implement `IWidget` instead.

```java
@ApiStatus.ScheduledForRemoval(inVersion = "3.2.0")
@Deprecated
public interface IGuiElement {
    ModularScreen getScreen();
    IGuiElement getParent();
    boolean hasParent();
    ResizeNode resizer();
    Area getArea();
    default Area getParentArea();
    default void applyTheme(ITheme theme) {}
    default void onMouseStartHover() {}
    default void onMouseEndHover() {}
    default void onMouseEnterArea() {}
    default void onMouseLeaveArea() {}
    default boolean isHovering();
    default boolean isHoveringFor(int ticks) { return false; }
    default boolean isBelowMouse();
    default boolean isBelowMouseFor(int ticks) { return false; }
    boolean isEnabled();
    default int getDefaultWidth() { return 18; }
    default int getDefaultHeight() { return 18; }
    void scheduleResize();
    boolean requiresResize();
}
```

Structurally a subset of `IWidget` (no lifecycle, no children, coarser `resizer()` return type `ResizeNode` instead of `StandardResizer`). `applyTheme(ITheme)` has an inline `// TODO: what is this doing here, not in 1.12` comment — it is otherwise unused by the theme system's current design.

**Example**: none — superseded by `IWidget`; no reason to implement this directly in new code.

---

## `com.cleanroommc.modularui.api.widget.IPositioned<W extends IPositioned<W>>`

**Central interface.** CRTP-style ("self-typed") fluent builder mixin providing all position/size/anchor/margin/padding builder methods for widgets. `IWidget` does not extend this directly in the source shown, but concrete widget base classes (e.g. `Widget<W>`) implement it alongside `IWidget` to get the `.left(...)`, `.width(...)`, `.pos(...)` etc. chain used everywhere in `test/`.

```java
@SuppressWarnings({"unused", "UnusedReturnValue"})
public interface IPositioned<W extends IPositioned<W>> {
    StandardResizer resizer();
    Area getArea();
    boolean requiresResize();
    void scheduleResize();
    default W getThis(); // unchecked cast to W
    // ... see tables below
}
```

Every builder method ultimately calls `resizer()` (or `getArea().getPadding()/getMargin()` + `scheduleResize()`) and returns `getThis()` for chaining.

### Cover-children / decoration / expansion

| Method | Notes |
|---|---|
| `coverChildrenWidth()` / `coverChildrenHeight()` | default `minSize = 8` |
| `coverChildrenWidth(int minWidth)` / `coverChildrenHeight(int minHeight)` | size = max of children's extents and `minWidth`/`minHeight` |
| `coverChildren()` / `coverChildren(int minSize)` / `coverChildren(int minWidth, int minHeight)` | both axes at once |
| `disableCoverChildrenWidth/Height/()` / `disableCoverChildren()` | passes `-1` as min to turn the behavior off |
| `decoration(boolean)` / `decoration()` | marks the resizer as decoration — ignored by `coverChildren`/margin-padding calculations of the parent |
| `expanded()` | tells the parent layout to expand this widget to fill remaining space (flex-grow equivalent) |

### Relative positioning

| Method | Notes |
|---|---|
| `relative(IGuiElement)` | **deprecated**, `@ApiStatus.ScheduledForRemoval(3.2.0)`; use `relative(Area)`/`relative(IWidget)` |
| `relative(Area)` | **deprecated** (no removal version given); wraps in an `AreaResizer` |
| `relative(ResizeNode)` | make this widget's position/size relative to an arbitrary `ResizeNode` |
| `relative(IWidget widget)` | relative to another widget's resizer |
| `relativeToScreen()` / `relativeToParent()` | relative to the screen root or the immediate parent |

### Edge positioning (`left`/`right`/`top`/`bottom` — identical shapes, only shown once)

| Method family | Params | Notes |
|---|---|---|
| `left(int val)` | pixel value | absolute pixel offset from left edge |
| `leftRel(float val)` | 0–1 fraction | relative to parent width |
| `leftRelOffset(float val, int offset)` | fraction + pixel offset | relative + fixed pixel nudge |
| `leftRelAnchor(float val, float anchor)` | fraction + anchor fraction | positions relative to an anchor point on this widget itself |
| `leftRel(float val, int offset, float anchor)` | fraction + offset + anchor | full control variant |
| `left(float val, int offset, float anchor, Unit.Measure measure)` | raw variant | lowest-level entry point; `measure` picks `PIXEL` vs `RELATIVE` |
| `left(DoubleSupplier val, Unit.Measure measure)` / `leftRelOffset/leftRelAnchor/leftRel(DoubleSupplier ...)` | dynamic value | same shapes but value computed lazily every layout pass instead of being fixed at call time |

The same 8-ish overload shapes exist verbatim for `right(...)`, `top(...)`, and `bottom(...)`.

### Size

| Method | Notes |
|---|---|
| `width(int val)` / `height(int val)` | fixed pixel size |
| `widthRel(float val)` / `heightRel(float val)` | fraction of parent |
| `widthRelOffset(float val, int offset)` / `heightRelOffset(...)` | fraction + pixel offset |
| `width(float val, Unit.Measure measure)` / `height(...)` | explicit measure |
| `width(DoubleSupplier val, Unit.Measure measure)` / `height(...)` / `*RelOffset(DoubleSupplier, int)` | dynamic (lazily computed) variants |
| `pos(int x, int y)` / `posRel(float x, float y)` / `posRel(Alignment alignment)` | combined `left`+`top` shortcuts |
| `size(int w, int h)` / `sizeRel(float w, float h)` / `size(int val)` / `sizeRel(float val)` | combined width+height shortcuts (square variants take one value for both) |
| `fullWidth()` / `fullHeight()` / `full()` | shortcuts for `widthRel(1f)` / `heightRel(1f)` / both |

### Anchoring & alignment

| Method | Notes |
|---|---|
| `anchorLeft/anchorRight/anchorTop/anchorBottom(float val)` | sets anchor fraction per edge directly on the resizer |
| `anchor(Alignment)` | **deprecated**, `@ApiStatus.ScheduledForRemoval(3.3.0)` — "removed due to this method being misused often" |
| `alignX(float)`/`alignX(Alignment)`/`alignY(float)`/`alignY(Alignment)`/`align(Alignment)` | all **deprecated**, scheduled for removal in 3.3.0; combine a `*Rel` position call with the matching `anchor*` call |
| `horizontalCenter()` / `verticalCenter()` / `center()` | shortcuts for `leftRel(0.5f)` / `topRel(0.5f)` / both |

### Misc / margin / padding

| Method | Notes |
|---|---|
| `resizer(Consumer<StandardResizer> flexConsumer)` | apply an arbitrary configuration function to the resizer |
| `padding(left,right,top,bottom)` / `padding(h,v)` / `padding(all)` | sets padding on `getArea().getPadding()`, then `scheduleResize()` |
| `paddingLeft/Right/Top/Bottom(int)` | single-edge padding setters |
| `margin(left,right,top,bottom)` / `margin(h,v)` / `margin(all)` / `marginLeft/Right/Top/Bottom(int)` | margin equivalents of the padding methods |

**Example (from repo)** — dense real usage, `src/main/java/com/cleanroommc/modularui/test/TestGui.java`:
```java
panel.child(sortableListWidget
        .onRemove(stringItem -> this.availableElements.get(stringItem.getWidgetValue()).available = true)
        .pos(10, 10)
        .bottom(23)
        .width(100));

panel.child(new ButtonWidget<>()
        .bottom(7).size(12, 12).leftRel(0.5f)
        .overlay(GuiTextures.ADD)
        .onMouseTapped(mouseButton -> { otherPanel.openPanel(); return true; }));
```
Dynamic/`DoubleSupplier`-based sizing style also appears widely, e.g. `src/main/java/com/cleanroommc/modularui/test/TestTile.java`:
```java
.child(Flow.col().name("slots_col").widthRel(0.5f).heightRelOffset(1f, -6) /* space for player sort buttons */)
```

---

## `com.cleanroommc.modularui.api.widget.Interactable`

**Central interface.** Handles user interaction callbacks (mouse/keyboard) on `IWidget`s. All instance methods are defaults with conservative no-op-ish behavior; static helpers expose modifier-key state.

```java
public interface Interactable {
    @NotNull default Result onMousePressed(int mouseButton) { return Result.ACCEPT; }
    default boolean onMouseRelease(int mouseButton) { return false; }
    @NotNull default Result onMouseTapped(int mouseButton) { return Result.IGNORE; }
    @NotNull default Result onKeyPressed(char typedChar, int keyCode) { return Result.IGNORE; }
    default boolean onKeyRelease(char typedChar, int keyCode) { return false; }
    @NotNull default Result onKeyTapped(char typedChar, int keyCode) { return Result.IGNORE; }
    default boolean onMouseScroll(UpOrDown scrollDirection, int amount) { return false; }
    default void onMouseDrag(int mouseButton, long timeSinceClick) {}

    @SideOnly(Side.CLIENT) static boolean hasControlDown();
    @SideOnly(Side.CLIENT) static boolean hasShiftDown();
    @SideOnly(Side.CLIENT) static boolean hasAltDown();
    static boolean isKeyComboCtrlX/CtrlV/CtrlC/CtrlA(int keyID);
    @SideOnly(Side.CLIENT) static boolean isKeyPressed(int key);
    ResourceLocation PRESS_SOUND;
    @SideOnly(Side.CLIENT) static void playButtonClickSound();

    enum Result { IGNORE(false,false), ACCEPT(true,false), STOP(false,true), SUCCESS(true,true); public final boolean accepts, stops; }
}
```

### Instance callbacks

| Method | Default return | Notes |
|---|---|---|
| `onMousePressed(int mouseButton)` | `Result.ACCEPT` | fired on press; `onMouseTapped` only fires afterward if this returned `ACCEPT` or `SUCCESS` |
| `onMouseRelease(int mouseButton)` | `false` | if `false`, `onMouseTapped` will **not** be called |
| `onMouseTapped(int mouseButton)` | `Result.IGNORE` | fired on a full press-then-release within the tap window |
| `onKeyPressed(char typedChar, int keyCode)` | `Result.IGNORE` | `onKeyTapped` only fires if this returns `ACCEPT`/`SUCCESS` |
| `onKeyRelease(char typedChar, int keyCode)` | `false` | if `false`, `onKeyTapped` will not be called |
| `onKeyTapped(char typedChar, int keyCode)` | `Result.IGNORE` | full press-then-release for keys |
| `onMouseScroll(UpOrDown direction, int amount)` | `false` | should return whether this widget **can** scroll at all (not whether it scrolled *this* time); if it scrolled to its end and returns `false`, scroll passes through to a widget below |
| `onMouseDrag(int mouseButton, long timeSinceClick)` | no-op | fired repeatedly while dragging after this widget was clicked |

### `Result` enum semantics

| Value | `accepts` | `stops` | Meaning |
|---|---|---|---|
| `IGNORE` | `false` | `false` | nothing happens; other widgets still get checked, tap not chained |
| `ACCEPT` | `true` | `false` | interaction accepted, but other widgets still get checked too |
| `STOP` | `false` | `true` | interaction rejected, no other widgets checked |
| `SUCCESS` | `true` | `true` | interaction accepted, no other widgets checked |

### Static helpers

| Method | Returns | Notes |
|---|---|---|
| `hasControlDown()` / `hasShiftDown()` / `hasAltDown()` | `boolean` | client-only modifier key state (`hasAltDown` checks LWJGL `Keyboard` directly since vanilla has no helper) |
| `isKeyComboCtrlX/CtrlV/CtrlC/CtrlA(int keyID)` | `boolean` | true only if the matching key is pressed **and** ctrl is down **and** neither shift nor alt is down |
| `isKeyPressed(int key)` | `boolean` | raw `Keyboard.isKeyDown` |
| `PRESS_SOUND` | `ResourceLocation` | `"gui.button.press"` |
| `playButtonClickSound()` | `void` | plays the vanilla button click sound |

**Example (from repo)** — `onMousePressed`/`onMouseTapped` used throughout `test/`, e.g. `src/main/java/com/cleanroommc/modularui/test/TestGui.java`:
```java
.onMousePressed(mouseButton1 -> {
    if (this.availableElements.get(value).available) {
        sortableListWidget.child(items.get(value));
        this.availableElements.get(value).available = false;
    }
    return true;
})
// ...
.onMouseTapped(mouseButton -> {
    otherPanel.openPanel();
    return true;
})
```
Note: `.onMousePressed(...)` on concrete widgets (e.g. `ButtonWidget`) is a builder-style overload accepting a lambda returning `boolean`/`true`, which the widget internally maps to `Result.SUCCESS`/`ACCEPT` — the raw `Interactable` contract shown above returns the `Result` enum directly.

---

## `com.cleanroommc.modularui.api.widget.ModernInteractable`

Optional-mod extension of `Interactable` adding lwjgl3ify's raw key/text event hooks (needed because 1.7.10's LWJGL2 input model doesn't expose per-character text events the way LWJGL3 does).

```java
public interface ModernInteractable {
    @Optional.Method(modid = ModularUI.ModIds.LWJGL3IFY)
    default Interactable.Result onKeyEvent(InputEvents.KeyEvent event) { return Interactable.Result.IGNORE; }

    @Optional.Method(modid = ModularUI.ModIds.LWJGL3IFY)
    default boolean onTextInput(InputEvents.TextEvent event) { return false; }
}
```

| Method | Default | Notes |
|---|---|---|
| `onKeyEvent(InputEvents.KeyEvent event)` | `Result.IGNORE` | only invoked when the `lwjgl3ify` mod is present (`@Optional.Method`) |
| `onTextInput(InputEvents.TextEvent event)` | `false` | same conditional-compilation guard |

**Example (constructed, not from repo)**
```java
class MyTextField extends TextFieldWidget implements ModernInteractable {
    @Override
    public boolean onTextInput(InputEvents.TextEvent event) {
        // handle raw text input under lwjgl3ify
        return true;
    }
}
```

---

## `com.cleanroommc.modularui.api.widget.ITooltip<W extends ITooltip<W>>`

**Central interface.** CRTP fluent builder mixin for configuring a widget's `RichTooltip`.

```java
public interface ITooltip<W extends ITooltip<W>> {
    @Nullable RichTooltip getTooltip();
    @NotNull RichTooltip tooltip();
    W tooltip(RichTooltip tooltip);
    default boolean hasTooltip();
    default W getThis(); // unchecked cast
    default W tooltip(Consumer<RichTooltip> tooltipConsumer);
    default W tooltipStatic(Consumer<RichTooltip> tooltipConsumer);
    default W tooltipBuilder(Consumer<RichTooltip> tooltipBuilder);
    default W tooltipDynamic(Consumer<RichTooltip> tooltipBuilder);
    default W tooltipPos(RichTooltip.Pos pos);
    default W tooltipPos(int x, int y);
    default W tooltipAlignment(Alignment alignment);
    default W tooltipTextShadow(boolean textShadow);
    default W tooltipTextColor(int textColor);
    default W tooltipScale(float scale);
    default W tooltipShowUpTimer(int showUpTimer);
    default W tooltipAutoUpdate(boolean update);
    default W tooltipHasTitleMargin(boolean hasTitleMargin); // currently a no-op body
    default W tooltipLinePadding(int linePadding);            // currently a no-op body
    default W addTooltipElement(String s);
    default W addTooltipElement(IDrawable drawable);
    default W addTooltipLine(ITextLine line);
    default W addTooltipLine(IDrawable drawable);
    default W addTooltipLine(String line);
    default W addTooltipDrawableLines(Iterable<IDrawable> lines);
    default W addTooltipStringLines(Iterable<String> lines);
    default W removeAllTooltips();
}
```

| Method | Notes |
|---|---|
| `getTooltip()` | `@Nullable`; current tooltip or `null` if none was ever created |
| `tooltip()` | `@NotNull`; lazily creates a `RichTooltip` if none exists |
| `tooltip(RichTooltip)` | overwrites the tooltip entirely |
| `hasTooltip()` | default: `getTooltip() != null && !getTooltip().isEmpty()` |
| `tooltip(Consumer<RichTooltip>)` / `tooltipStatic(...)` | default; runs the consumer against `tooltip()` **once**, meant for widget-tree-initialization-time setup |
| `tooltipBuilder(Consumer<RichTooltip>)` / `tooltipDynamic(...)` | default; registers a builder re-invoked every time the tooltip is marked dirty — use for **dynamic** tooltip content |
| `tooltipPos(RichTooltip.Pos)` / `tooltipPos(int x, int y)` | general vs. fixed-position tooltip placement |
| `tooltipAlignment(Alignment)` | how tooltip content is aligned inside the box |
| `tooltipTextShadow(boolean)` | default text shadow, overridable per `StyledText` line |
| `tooltipTextColor(int)` | default text color, overridable via formatting |
| `tooltipScale(float)` | overall tooltip render scale |
| `tooltipShowUpTimer(int)` | hover ticks required before the tooltip appears |
| `tooltipAutoUpdate(boolean)` | per-tick tooltip rebuild; usually unnecessary since `ValueSyncHandler` triggers rebuilds on value change automatically |
| `tooltipHasTitleMargin(boolean)` / `tooltipLinePadding(int)` | bodies are currently commented out / no-ops in this version — calling them has no effect yet |
| `addTooltipElement(String\|IDrawable)` | appends inline content to the current line without starting a new one |
| `addTooltipLine(ITextLine\|IDrawable\|String)` | appends content **and** starts a new line afterward; the `String` overload splits on `\n` into multiple lines via `IKey.str` |
| `addTooltipDrawableLines(Iterable<IDrawable>)` / `addTooltipStringLines(Iterable<String>)` | bulk variants |
| `removeAllTooltips()` | resets the tooltip to empty |

**Example (from repo)** — `src/main/java/com/cleanroommc/modularui/test/TestTile.java`:
```java
new ButtonWidget<>()
        .height(16).width(3 * 18)
        .tooltip(tooltip -> {
            tooltip.showUpTimer(10);
            tooltip.addLine(IKey.str("Test Line g"));
            tooltip.addLine(IKey.str("An image inside of a tooltip:"));
            tooltip.addLine(new Circle().setColor(Color.RED.darker(2), Color.RED.brighter(2)).asIcon().size(20))
                    .addLine(new ItemDrawable(new ItemStack(Items.diamond)).asIcon())
                    .pos(RichTooltip.Pos.LEFT);
        })
        .onMousePressed(mouseButton -> { panelSyncHandler.openPanel(); return true; })
        .overlay(IKey.str("Open Sub Panel").scale(0.75f));
```
Simple line addition, `src/main/java/com/cleanroommc/modularui/test/TestGui.java`:
```java
new Widget<>().addTooltipLine(line).widgetTheme(IThemeApi.BUTTON).overlay(IKey.str(line))
```
Dynamic tooltip, `src/main/java/com/cleanroommc/modularui/test/TestTile.java` (`ToggleButton`):
```java
new ToggleButton()
        .valueWrapped(cycleStateValue, 0)
        .tooltipBuilder(false, t -> { t.addLine("Wow! This button sure isnt selected! Not one bit!"); })
        .tooltipBuilder(true, t -> t.addLine("Wow! This button IS selected! How cool!"))
        .tooltipAutoUpdate(true)
```

---

## `com.cleanroommc.modularui.api.widget.IDelegatingWidget`

Marks a widget that forwards vanilla-slot behavior to an inner "delegate" widget.

```java
public interface IDelegatingWidget extends IWidget, IVanillaSlot {
    IWidget getDelegate();
    @Override default Slot getVanillaSlot();
    @Override default boolean handleAsVanillaSlot();
}
```

| Method | Notes |
|---|---|
| `getDelegate()` | abstract; the wrapped inner widget |
| `getVanillaSlot()` | default: if the delegate is itself an `IVanillaSlot`, return its slot; else `null` |
| `handleAsVanillaSlot()` | default: `true` only if the delegate is an `IVanillaSlot` **and** it says `true` |

Inferred: implemented by `com.cleanroommc.modularui.widget.DelegatingWidget`; not directly exercised in `test/`.

**Example (constructed, not from repo)**
```java
class MyDelegatingWidget implements IDelegatingWidget {
    private final IWidget delegate;
    MyDelegatingWidget(IWidget delegate) { this.delegate = delegate; }
    public IWidget getDelegate() { return delegate; }
}
```

---

## `com.cleanroommc.modularui.api.widget.IDragResizeable`

Implement on an `IWidget` to allow it to be resized by dragging its edges/corners, Windows-style.

```java
public interface IDragResizeable {
    default boolean isCurrentlyResizable() { return true; }
    default boolean keepPosOnDragResize() { return true; }
    default void onDragResize() { ((IWidget) this).scheduleResize(); }
    default int getDragAreaSize() { return 3; }
    default int getMinDragWidth() { return 18; }
    default int getMinDragHeight() { return 18; }

    static ResizeDragArea getDragResizeCorner(IDragResizeable widget, Area area, IViewportStack stack, int x, int y);
    static void applyDrag(IDragResizeable resizeable, IWidget widget, ResizeDragArea dragArea, Area startArea, int dx, int dy);
}
```

| Method | Notes |
|---|---|
| `isCurrentlyResizable()` | default `true`; gate for whether drag-resize is currently allowed |
| `keepPosOnDragResize()` | default `true`; if true, the opposite edge is also resized so the widget's center stays put |
| `onDragResize()` | default: `scheduleResize()`; called every pixel of mouse movement during a drag-resize |
| `getDragAreaSize()` | default `3` px; width of the border band that starts a drag-resize |
| `getMinDragWidth()` / `getMinDragHeight()` | default `18` each; resize floor |
| `static getDragResizeCorner(widget, area, stack, x, y)` | internal hit-test returning which `ResizeDragArea` (if any) the mouse is over |
| `static applyDrag(resizeable, widget, dragArea, startArea, dx, dy)` | internal; performs the actual resize+reposition math for one drag step, respecting `keepPosOnDragResize()`/shift-key override and the min-size floors |

`IWidget`-implementing classes that also implement this interface get free "drag to resize" behavior wherever the framework detects `instanceof IDragResizeable`.

**Example (constructed, not from repo)**
```java
class ResizableDialog extends Dialog<ResizableDialog> implements IDragResizeable {
    @Override public int getMinDragWidth() { return 60; }
    @Override public int getMinDragHeight() { return 40; }
}
```
Related real usage — `ModularPanel.resizeableOnDrag(true)` toggles this behavior on panels, `src/main/java/com/cleanroommc/modularui/test/TestItem.java`:
```java
ModularPanel panel = ModularPanel.defaultPanel("knapping_gui").resizeableOnDrag(true);
```

---

## `com.cleanroommc.modularui.api.widget.IDraggable`

Marks a widget as draggable (e.g. movable panels/windows); dragging mechanics are driven by the framework, not the implementer.

```java
public interface IDraggable {
    void drawMovingState(ModularGuiContext context, float partialTicks);
    boolean onDragStart(int button);
    void onDragEnd(boolean successful);
    void onDrag(int mouseButton, long timeSinceLastClick);
    default boolean canDropHere(int x, int y, @Nullable IWidget widget) { return true; }
    @Nullable Area getMovingArea();
    boolean isMoving();
    void setMoving(boolean moving);
    void transform(IViewportStack viewportStack);
}
```

| Method | Notes |
|---|---|
| `drawMovingState(context, partialTicks)` | called every frame after everything else is rendered, only while `isMoving()` is `true`; should translate to the mouse position and draw via `WidgetTree.drawTree(...)` |
| `onDragStart(int button)` | return `false` to cancel the drag before it starts |
| `onDragEnd(boolean successful)` | `successful = false` if the widget snapped back to its old position |
| `onDrag(int mouseButton, long timeSinceLastClick)` | fired continuously while dragging |
| `canDropHere(x, y, @Nullable IWidget widget)` | default `true`; called on mouse release with the current top-most widget under the cursor, to validate the drop location |
| `getMovingArea()` | `@Nullable Area`; size/position used while mid-move |
| `isMoving()` / `setMoving(boolean)` | drag-state flag |
| `transform(IViewportStack)` | positions the widget during the drag (separate from `IWidget.transform`) |

See also `com.cleanroommc.modularui.widget.DraggableWidget`, the concrete default implementation.

**Example (from repo)** — `src/main/java/com/cleanroommc/modularui/test/TestGuis.java` (`buildSpriteAndEntityUI`) uses the concrete `DraggableWidget`:
```java
.child(new DraggableWidget<>()
        .size(20)
        .horizontalCenter()
        .top(20)
        .tooltipBuilder(tooltip -> {
            tooltip.addLine("Lorem ipsum dolor sit amet ...");
            tooltip.alignment(Alignment.Center);
        }))
```

---

## `com.cleanroommc.modularui.api.widget.IFocusedWidget`

Makes an `IWidget` focusable — receives keyboard/mouse input first regardless of hover state. Primarily used by text fields.

```java
public interface IFocusedWidget {
    boolean isFocused();
    void onFocus(ModularGuiContext context);
    void onRemoveFocus(ModularGuiContext context);
}
```

| Method | Notes |
|---|---|
| `isFocused()` | current focus state |
| `onFocus(ModularGuiContext context)` | called when this widget gains focus |
| `onRemoveFocus(ModularGuiContext context)` | called when focus is removed |

Inferred: implemented by `com.cleanroommc.modularui.widgets.textfield.BaseTextFieldWidget` (base of `TextFieldWidget`, used extensively in `test/`, e.g. `new TextFieldWidget().value(searchValue)` in `TestGuis.buildSearchTest`), though focus is managed by the framework, not called directly by GUI-building code.

**Example (constructed, not from repo)**
```java
class MyFocusable extends Widget<MyFocusable> implements IFocusedWidget {
    private boolean focused;
    public boolean isFocused() { return focused; }
    public void onFocus(ModularGuiContext context) { focused = true; }
    public void onRemoveFocus(ModularGuiContext context) { focused = false; }
}
```

---

## `com.cleanroommc.modularui.api.widget.IGuiAction`

Marker + nested functional-interface family for screen-level (not widget-level) input listeners, registered via `ModularScreen.registerGuiActionListener(IGuiAction)`.

```java
public interface IGuiAction {
    @FunctionalInterface interface MousePressed extends IGuiAction { boolean press(int mouseButton); }
    @FunctionalInterface interface MouseReleased extends IGuiAction { boolean release(int mouseButton); }
    @FunctionalInterface interface KeyPressed extends IGuiAction { boolean press(char typedChar, int keyCode); }
    @FunctionalInterface interface KeyReleased extends IGuiAction { boolean release(char typedChar, int keyCode); }
    @FunctionalInterface interface MouseScroll extends IGuiAction { boolean scroll(UpOrDown direction, int amount); }
    @FunctionalInterface interface MouseDrag extends IGuiAction { boolean drag(int mouseButton, long timeSinceClick); }
}
```

| Nested interface | Method | Notes |
|---|---|---|
| `MousePressed` | `press(int mouseButton)` | screen-wide mouse press listener |
| `MouseReleased` | `release(int mouseButton)` | screen-wide mouse release listener |
| `KeyPressed` | `press(char typedChar, int keyCode)` | screen-wide key press listener |
| `KeyReleased` | `release(char typedChar, int keyCode)` | screen-wide key release listener |
| `MouseScroll` | `scroll(UpOrDown direction, int amount)` | screen-wide scroll listener |
| `MouseDrag` | `drag(int mouseButton, long timeSinceClick)` | screen-wide drag listener |

Not used in `test/`; conceptually parallel to `Interactable` but for screen-global listeners rather than per-widget ones.

**Example (constructed, not from repo)**
```java
screen.registerGuiActionListener((IGuiAction.MousePressed) mouseButton -> {
    // global mouse-press hook
    return false;
});
```

---

## `com.cleanroommc.modularui.api.widget.INotifyEnabled`

Single-method callback interface notifying a parent when a child's enabled state changes. `ILayoutWidget extends INotifyEnabled`.

```java
public interface INotifyEnabled {
    void onChildChangeEnabled(IWidget child, boolean enabled);
}
```

`onChildChangeEnabled(IWidget child, boolean enabled)` — called by the framework whenever `child.setEnabled(...)` changes the child's state; `ILayoutWidget`'s default implementation re-runs its layout in response.

**Example (constructed, not from repo)**
```java
class Container implements INotifyEnabled {
    public void onChildChangeEnabled(IWidget child, boolean enabled) {
        // re-layout, recompute visible children, etc.
    }
}
```

---

## `com.cleanroommc.modularui.api.widget.IParentWidget<I extends IWidget, W extends IParentWidget<I, W>>`

CRTP mixin providing the `.child(...)` builder family used to attach children to a container widget.

```java
public interface IParentWidget<I extends IWidget, W extends IParentWidget<I, W>> {
    W getThis();
    boolean addChild(I child, int index);
    default W child(int index, I child);
    default W child(I child);
    @Deprecated default W childIf(boolean condition, I child); // @ApiStatus.ScheduledForRemoval(3.2.0)
    default W childIf(boolean condition, Supplier<I> child);
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getThis()` | — | `W` | CRTP self-cast, abstract here (unlike `IPositioned`/`ITooltip` this one isn't a default) |
| `addChild(I child, int index)` | child, insert index (`-1` = append) | `boolean` | abstract; the actual insertion logic |
| `child(int index, I child)` | index, child | `W` | default; throws `IllegalStateException` if `addChild` returns `false` |
| `child(I child)` | child | `W` | default; append (`index = -1`), same failure behavior |
| `childIf(boolean condition, I child)` | **deprecated**, `@ApiStatus.ScheduledForRemoval(3.2.0)` — eagerly evaluates `child` even if `condition` is false | use the `Supplier` overload instead |
| `childIf(boolean condition, Supplier<I> child)` | condition, lazy child supplier | `W` | default; only constructs/adds the child if `condition` is `true` |

**Example (from repo)** — `.child(...)` chains are the primary tree-building idiom throughout `test/`, e.g. `src/main/java/com/cleanroommc/modularui/test/TestTile.java`:
```java
panel.size(176, 210)
        .child(Flow.row().name("Tab row")
                .child(new PageButton(0, tabController).tab(GuiTextures.TAB_TOP, -1))
                .child(new PageButton(1, tabController).tab(GuiTextures.TAB_TOP, 0)))
        .child(new Expandable().name("expandable") /* ... */);
```

---

## `com.cleanroommc.modularui.api.widget.IPositioned` — *(see full section above under "Central" interfaces)*

*(Documented in full detail earlier in this file; listed again here only because it lives in `api/widget/` alongside the other widget interfaces — see the dedicated section above for the complete method tables and example.)*

---

## `com.cleanroommc.modularui.api.widget.ISynced<W extends IWidget>`

CRTP mixin marking a widget as syncable to a `SyncHandler` obtained from a `ModularSyncManager`/`PanelSyncManager` by key.

```java
public interface ISynced<W extends IWidget> {
    default W getThis();
    void initialiseSyncHandler(ModularSyncManager syncManager, boolean late);
    @Deprecated default boolean isValidSyncHandler(SyncHandler<?> syncHandler); // @ApiStatus.ScheduledForRemoval(3.2.0)
    default boolean isValidSyncOrValue(@NotNull ISyncOrValue syncOrValue);
    @ApiStatus.NonExtendable default void checkValidSyncOrValue(ISyncOrValue syncHandler);
    boolean isSynced();
    @NotNull SyncHandler<?> getSyncHandler();
    W syncHandler(String name, int id);
    default W syncHandler(String key);
    default W syncHandler(int id);
}
```

| Method | Notes |
|---|---|
| `initialiseSyncHandler(ModularSyncManager syncManager, boolean late)` | called when the widget is initialized or added; `late = true` if added after the panel already opened |
| `isValidSyncHandler(SyncHandler<?>)` | **deprecated**, `@ApiStatus.ScheduledForRemoval(3.2.0)`; use `isValidSyncOrValue` |
| `isValidSyncOrValue(@NotNull ISyncOrValue)` | default: delegates to `isValidSyncHandler` only if the value is a `SyncHandler`; usually implemented via `ISyncOrValue.isTypeOrEmpty(Class)` or `isValueOfType(Class)` in overrides |
| `checkValidSyncOrValue(ISyncOrValue)` | `@ApiStatus.NonExtendable`; throws `IllegalStateException` if `isValidSyncOrValue` returns `false` |
| `castIfTypeElseNull(...)` / `castIfTypeGenericElseNull(...)` | **deprecated**, scheduled for removal in 3.2.0; legacy casting helpers |
| `isSynced()` | whether this widget currently has a valid sync handler |
| `getSyncHandler()` | `@NotNull`; throws `IllegalStateException` if not synced |
| `syncHandler(String name, int id)` | sets the sync-handler lookup key; the actual handler is resolved later in `initialiseSyncHandler` |
| `syncHandler(String key)` | default: `syncHandler(key, 0)` |
| `syncHandler(int id)` | default: `syncHandler("_", id)` |

**Example (from repo)** — `src/main/java/com/cleanroommc/modularui/test/TestGuis.java` (`buildSearchTest`):
```java
StringValue searchValue = new StringValue("");
new TextFieldWidget()
        .value(searchValue)
        .height(16)
        .widthRel(1f)
        .autoUpdateOnChange(true)
```
Sync-handler-key style, `src/main/java/com/cleanroommc/modularui/test/TestTile.java`:
```java
new ProgressWidget().syncHandler("progress").texture(GuiTextures.PROGRESS_ARROW, 20)
// ...
new ItemSlot().syncHandler(syncManager1.getOrCreateSyncHandler(name, i, ItemSlotSH.class, () -> new ItemSlotSH(new ModularSlot(handler, finalI))))
```

---

## `com.cleanroommc.modularui.api.widget.IValueWidget<T>`

Marks a widget as holding a value of type `T`.

```java
public interface IValueWidget<T> extends IWidget {
    T getWidgetValue();
}
```

`getWidgetValue()` — returns the stored value. Inferred: implemented by `com.cleanroommc.modularui.widgets.ValueWidget` and its subclasses (`ToggleButton`, `CycleButtonWidget`, `SortableListWidget.Item<T>`, etc.).

**Example (from repo)** — `getWidgetValue()` is called directly in `src/main/java/com/cleanroommc/modularui/test/TestGui.java`:
```java
.onRemove(stringItem -> this.availableElements.get(stringItem.getWidgetValue()).available = true)
```

---

## `com.cleanroommc.modularui.api.widget.IVanillaSlot`

Marks an `IWidget` as backing a vanilla `net.minecraft.inventory.Slot`.

```java
public interface IVanillaSlot {
    Slot getVanillaSlot();
    boolean handleAsVanillaSlot();
}
```

| Method | Notes |
|---|---|
| `getVanillaSlot()` | the backing item slot |
| `handleAsVanillaSlot()` | whether vanilla slot-click handling should apply to this widget |

Inferred: implemented by `com.cleanroommc.modularui.widgets.slot.ItemSlot` (used pervasively in `test/` via `new ItemSlot().slot(new ModularSlot(...))`) and consumed by `IDelegatingWidget`.

**Example (constructed, not from repo)**
```java
Slot slot = ((IVanillaSlot) itemSlotWidget).getVanillaSlot();
```

---

## `com.cleanroommc.modularui.api.widget.ResizeDragArea`

Enum of the 8 drag-resize handle regions (4 edges + 4 corners), used by `IDragResizeable`.

```java
public enum ResizeDragArea {
    TOP_LEFT(true, true, false, false),
    TOP_RIGHT(true, false, false, true),
    BOTTOM_LEFT(false, true, true, false),
    BOTTOM_RIGHT(false, false, true, true),
    TOP(true, false, false, false),
    LEFT(false, true, false, false),
    BOTTOM(false, false, true, false),
    RIGHT(false, false, false, true);

    public final boolean top, left, bottom, right;
}
```

Constructor throws `IllegalArgumentException` if both `top && bottom` or `left && right` are `true` (mutually exclusive edges). Fields are public and read directly (e.g. by `IDragResizeable.applyDrag`) rather than via accessor methods.

**Example (constructed, not from repo)**
```java
ResizeDragArea corner = IDragResizeable.getDragResizeCorner(widget, area, stack, mouseX, mouseY);
if (corner != null && corner.left) { /* dragging from the left edge/corner */ }
```
