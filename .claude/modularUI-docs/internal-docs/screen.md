# Package `com.cleanroommc.modularui.screen` and `com.cleanroommc.modularui.screen.viewport`

This is the package a mod author interacts with most. `CustomModularScreen` and `ModularContainer` are the two
classes you subclass to build a UI; `ModularPanel` is the root widget container your `buildUI` returns;
`PanelManager` stacks additional panels on top of it; `ModularGuiContext` is the per-screen context object
threaded through the whole widget tree.

All classes are client-only (`@SideOnly(Side.CLIENT)`) unless noted otherwise (`ModularContainer` and its
sync-related pieces run on both sides).

---

## Contents

- [CustomModularScreen](#customroommcmodularuiscreencustommodularscreen)
- [ModularScreen](#comcleanroommcmodularuiscreenmodularscreen)
- [ModularContainer](#comcleanroommcmodularuiscreenmodularcontainer)
- [ModularPanel](#comcleanroommcmodularuiscreenmodularpanel)
- [PanelManager](#comcleanroommcmodularuiscreenpanelmanager)
- [SecondaryPanel](#comcleanroommcmodularuiscreensecondarypanel)
- [UISettings](#comcleanroommcmodularuiscreenuisettings)
- [RichTooltip](#comcleanroommcmodularuiscreenrichtooltip)
- [RichTooltipEvent](#comcleanroommcmodularuiscreenrichtooltipevent)
- [GuiScreenWrapper](#comcleanroommcmodularuiscreenguiscreenwrapper)
- [GuiContainerWrapper](#comcleanroommcmodularuiscreenguicontainerwrapper)
- [IClickableGuiContainer](#comcleanroommcmodularuiscreeniclickableguicontainer)
- [DraggablePanelWrapper](#comcleanroommcmodularuiscreendraggablepanelwrapper)
- [ClientScreenHandler](#comcleanroommcmodularuiscreenclientscreenhandler)
- [NEAAnimationHandler](#comcleanroommcmodularuiscreenneaanimationhandler)
- [OpenScreenEvent](#comcleanroommcmodularuiscreenopenscreenevent)
- [RecipeViewerSettingsImpl](#comcleanroommcmodularuiscreenrecipeviewersettingsimpl)
- [viewport.GuiContext](#comcleanroommcmodularuiscreenviewportguicontext)
- [viewport.ModularGuiContext](#comcleanroommcmodularuiscreenviewportmodularguicontext)
- [viewport.GuiViewportStack](#comcleanroommcmodularuiscreenviewportguiviewportstack)
- [viewport.TransformationMatrix](#comcleanroommcmodularuiscreenviewporttransformationmatrix)
- [viewport.LocatedElement](#comcleanroommcmodularuiscreenviewportlocatedelementt)
- [viewport.LocatedWidget](#comcleanroommcmodularuiscreenviewportlocatedwidget)

---

## `com.cleanroommc.modularui.screen.CustomModularScreen`

Abstract convenience base for a `ModularScreen` whose main panel is built by an overridable method instead of a
constructor argument. **This is the class a mod author extends to define a UI.**

```java
@SideOnly(Side.CLIENT)
public abstract class CustomModularScreen extends ModularScreen
```

### Constructors

| Constructor | Purpose | Gotchas |
|---|---|---|
| `CustomModularScreen()` | Deprecated single-arg ctor, owner defaults to `ModularUI.ID`. | `@Deprecated`, `@ApiStatus.ScheduledForRemoval(inVersion = "3.2.0")`. Logs an error in dev env. Do not use in real mods. |
| `CustomModularScreen(@NotNull String owner)` | Creates the screen with the given owner (usually your mod id). | Calls `super(owner)`, which internally defers building the panel until `buildUI` can be invoked (see below). |

### Methods

```java
@NotNull
@ApiStatus.OverrideOnly
public abstract ModularPanel buildUI(ModularGuiContext context);
```
Purpose: create and return the main panel of this screen. **Must return a new, non-null `ModularPanel` instance.**
Called once, from inside the super constructor (`ModularScreen`'s private constructor calls
`buildUI(this.context)` when no `mainPanelCreator` function was supplied) — i.e. it runs *during* your own
subclass's constructor, before your subclass's field initializers that come after the `super(...)` call have
executed for further use inside `buildUI` unless assigned earlier. In `TestGui`, lazy state (`this.lines`,
`this.configuredOptions`) is guarded with a null check inside `buildUI` for this reason.

- Param `context`: the `ModularGuiContext` of this screen; used to read screen size, register the panel's theme, etc.
- Return: the panel that becomes `getMainPanel()`.

### Example (from repo)

`src/main/java/com/cleanroommc/modularui/test/TestGui.java` lines 31-113:
```java
package com.cleanroommc.modularui.test;

public class TestGui extends CustomModularScreen {

    public TestGui() {
        super(ModularUI.ID);
    }

    @Override
    public void onClose() {
        ModularUI.LOGGER.info("New values: {}", this.configuredOptions);
    }

    @Override
    public @NotNull ModularPanel buildUI(ModularGuiContext context) {
        if (this.lines == null) {
            this.lines = IntStream.range(0, 20).mapToObj(i -> "Option " + (i + 1)).collect(Collectors.toList());
            this.configuredOptions = this.lines;
            this.availableElements = new Object2ObjectOpenHashMap<>();
        }
        // ... build widgets ...
        ModularPanel panel = ModularPanel.defaultPanel("test");
        panel.child(sortableListWidget /* ... */);
        IPanelHandler otherPanel = IPanelHandler.simple(panel, (mainPanel, player) -> {
            ModularPanel panel1 = new Dialog<>("Option Selection").setDisablePanelsBelow(false).setDraggable(false).size(150, 120);
            return panel1.child(ButtonWidget.panelCloseButton())
                    .child(new Grid().grid(availableMatrix).scrollable().pos(7, 7).right(16).bottom(7).name("available list"));
        }, true);
        panel.child(new ButtonWidget<>()
                .bottom(7).size(12, 12).leftRel(0.5f)
                .overlay(GuiTextures.ADD)
                .onMouseTapped(mouseButton -> {
                    otherPanel.openPanel();
                    return true;
                }));
        return panel;
    }
}
```
This also demonstrates `.addTooltipLine(line)` on a `Widget` (line 61) and opening a second panel with
`IPanelHandler.simple(...)` (see [PanelManager](#comcleanroommcmodularuiscreenpanelmanager) /
[SecondaryPanel](#comcleanroommcmodularuiscreensecondarypanel) below).

---

## `com.cleanroommc.modularui.screen.ModularScreen`

Base class for all modular UIs; exists only on the client. Owns the `PanelManager`, the `ModularGuiContext`,
draws the widget tree, and dispatches input. `CustomModularScreen` extends this and is normally what you extend
directly, but nothing prevents extending `ModularScreen` when the main panel is already built (see
`TestGuis.buildToggleGridListUI` opening `new ModularScreen(ModularUI.ID, panel)`).

```java
@SideOnly(Side.CLIENT)
public class ModularScreen
```

### Static methods

| Method | Params | Returns | Notes |
|---|---|---|---|
| `isScreen(GuiScreen guiScreen, String owner, String name)` | screen, owner, name | `boolean` | true if `guiScreen` is an `IMuiScreen` wrapping a `ModularScreen` matching owner+name. |
| `isActive(String owner, String name)` | owner, name | `boolean` | Checks against `Minecraft.getMinecraft().currentScreen`. |
| `getCurrent()` | — | `@Nullable ModularScreen` | Currently open modular screen, or null. |

### Constructors

| Constructor | Purpose | Gotchas |
|---|---|---|
| `ModularScreen(@NotNull ModularPanel mainPanel)` | Deprecated, owner = `ModularUI.ID`. | `@Deprecated`. |
| `ModularScreen(@NotNull String owner, @NotNull ModularPanel mainPanel)` | Owner + pre-built panel. | Delegates to the function-based ctor with `context -> mainPanel`. |
| `ModularScreen(@NotNull String owner, @NotNull Function<ModularGuiContext, ModularPanel> mainPanelCreator)` | Owner + a panel factory. | `mainPanelCreator` is invoked immediately (synchronously) with `this.context`; must not return null. |
| `ModularScreen(@NotNull String owner)` *(package-private)* | Used by `CustomModularScreen`; defers to overridden `buildUI`. | Not accessible outside the package — this is why you extend `CustomModularScreen` instead. |

Inferred: the private full constructor calls `buildUI(this.context)` only when `mainPanelCreator == null`,
which is the path `CustomModularScreen` takes; then wraps the resulting panel's name as `this.name` and
constructs a new `PanelManager`.

### Lifecycle hooks (override points)

| Method | When called | Notes |
|---|---|---|
| `onOpen()` | After the screen opens, before the first resize of the widget tree. `@ApiStatus.OverrideOnly`. | Default no-op. |
| `onClose()` | After the last panel (always the main panel) closes, which closes the screen. `@ApiStatus.OverrideOnly`. | Default no-op. `TestGui` logs `configuredOptions` here. |
| `onUpdate()` | Once per client tick (20/s), for every open panel's widget tree. `@MustBeInvokedByOverriders`. | If overridden, must call `super.onUpdate()`. |
| `onFrameUpdate()` | ~60 times/s, decoupled from tick rate. `@Deprecated`, `@MustBeInvokedByOverriders`. | Runs `PanelManager.checkDirty()`, frame-update listeners, and `context.onFrameUpdate()`. |
| `onResize(int width, int height)` | Every game-window resize. `@MustBeInvokedByOverriders`. | "Do not call this method except in an override!" Resizes the whole widget tree and updates the `IMuiScreen` wrapper's gui area. |
| `construct(IMuiScreen wrapper)` | Called from the constructor of the wrapping `GuiScreen`/`GuiContainer`. `@MustBeInvokedByOverriders`. | Throws `IllegalStateException` if already constructed. |
| `constructOverlay(GuiScreen screen)` | Internal, for overlay screens (NEI-style). `@ApiStatus.Internal`. | — |

### Instance methods

```java
public void close();                 // gentle close (animated if NEA present), calls close(false)
public void close(boolean force);    // force=true: MCHelper.closeScreen() immediately, skips lifecycle
```
Gotcha: `force=true` "should be avoided in most situations" — it skips panel close lifecycle.

Input dispatch methods — all follow the same pattern: fire registered `IGuiAction` listeners first, then walk
`panelManager.getOpenPanels()` top-to-bottom, stopping at the first panel that consumes the event or that
returns true from `disablePanelsBelow()`:

| Method | Signature | Returns |
|---|---|---|
| `onMousePressed` | `(int mouseButton)` | true if consumed |
| `onMouseRelease` | `(int mouseButton)` | true if consumed |
| `onKeyPressed` | `(char typedChar, int keyCode)` | true if consumed |
| `onKeyRelease` | `(char typedChar, int keyCode)` | true if consumed |
| `onMouseScroll` | `(UpOrDown scrollDirection, int amount)` | true if consumed |
| `onMouseDrag` | `(int mouseButton, long timeSinceClick)` | true if consumed |
| `onKeyEvent` / `onTextEvent` | lwjgl3ify-only, `@Optional.Method` | — |

`onMouseInputPre(int button, boolean pressed)` — `@ApiStatus.Internal final`; intercepts input while a
draggable element (panel or widget) is being dragged.

### Fluent configuration setters (builder-style, return `this`)

| Method | Params | Notes |
|---|---|---|
| `useTheme(String theme)` | theme id | No-op if a resource pack already overrides the theme for this screen. |
| `pausesGame(boolean)` | — | Never pauses if connected to a dedicated server. |
| `drawDarkBackground(boolean)` | — | Controls the dark gradient behind the UI. |
| `openParentOnClose(boolean)` | — | If true, re-opens the previous `GuiScreen` when this screen closes (used throughout `TestGuis` for the "test harness" screens). |

### Getters / simple queries

| Method | Returns | Notes |
|---|---|---|
| `isPanelOpen(String name)` / `isPanelOpen(ModularPanel panel)` | `boolean` | Delegates to `PanelManager`. |
| `isActive()` | `boolean` | true if `getCurrent() == this`. |
| `getOwner()` / `getName()` | `String` | Owner is usually a mod id; name = main panel's name. Used to resolve theme overrides. |
| `getResourceLocation()` | `ResourceLocation` | `new ResourceLocation(owner, name)`. |
| `isOverlay()` | `boolean` | true if this screen overlays another (e.g. embedded UI). |
| `getContext()` | `ModularGuiContext` | The one context instance for this screen's lifetime. |
| `getPanelManager()` | `PanelManager` | — |
| `getSyncManager()` | `ModularSyncManager` | `getContainer().getSyncManager()`. |
| `getMainPanel()` | `ModularPanel` | — |
| `getScreenWrapper()` | `IMuiScreen` | The `GuiScreen`/`GuiContainer` wrapping this screen. |
| `getScreenArea()` | `Area` | Full game-window area. |
| `isClientOnly()` | `boolean` | true for overlays, non-`GuiContainer` wrappers, or client-only containers. |
| `getContainer()` | `ModularContainer` | Throws `IllegalStateException` for overlays or non-`GuiContainer` wrappers. |
| `doesPauseGame()` / `shouldDrawDarkBackground()` / `isOpenParentOnClose()` | `boolean` | Reflect the fluent setters above. |
| `getThemeOverride()` / `getCurrentTheme()` | — | Theme resolution, lazily computed. |

### Frame-update / gui-action listener registration

```java
public void registerGuiActionListener(IGuiAction action);
public void removeGuiActionListener(IGuiAction action);
public void registerFrameUpdateListener(IWidget widget, Runnable runnable);           // merge=true
public void registerFrameUpdateListener(IWidget widget, Runnable runnable, boolean merge);
public void removeFrameUpdateListener(IWidget widget);
```
Gotcha: "Do NOT register listeners which are bound to a widget here! Use `Widget#listenGuiAction(IGuiAction)`
for that." Frame-update listeners auto-remove once the bound widget becomes invalid.

### Example (constructed, not from repo)

```java
ModularPanel panel = ModularPanel.defaultPanel("simple_ui");
ModularScreen screen = new ModularScreen(MyMod.ID, panel).openParentOnClose(true);
ClientGUI.open(screen);
```

---

## `com.cleanroommc.modularui.screen.ModularContainer`

Base class for the (server+client shared) `net.minecraft.inventory.Container` of a modular UI. **This is the
class a mod author extends for the container side of a UI**; `TestGuiContainer` in the test package extends
`GuiContainerWrapper` (the client-side `GuiContainer`) rather than `ModularContainer` itself, but
`CraftingModularContainer` in the same package directly extends `ModularContainer` and shows the pattern.

```java
public class ModularContainer extends Container
```

### Static

```java
public static ModularContainer getCurrent(EntityPlayer player);
```
Returns `player.openContainer` cast to `ModularContainer`, or null if it isn't one.

```java
public static int stackLimit(Slot slot, ItemStack stack);
```
Stack-limit helper that accounts for both `SlotItemHandler` variants; used internally by slot-click handling.

### Constructor

```java
public ModularContainer() {}
```
Trivial — real setup happens via `construct(...)`/`constructClientOnly()` below, which are called by the
factory/network layer, not directly by mod code in the common case (`UISettings.customContainer(Supplier<ModularContainer>)`
registers *how* to create one).

### Lifecycle / internal wiring

| Method | Purpose | Gotchas |
|---|---|---|
| `construct(EntityPlayer player, ModularSyncManager msm, UISettings settings, String mainPanelName, GuiData guiData)` | `@ApiStatus.Internal`. Full server/common-side init. | Also sorts shift-click slots. |
| `initializeClient(ModularScreen screen)` *(package-private)* | Client-only, stores the screen reference. | — |
| `constructClientOnly()` | `@ApiStatus.Internal @SideOnly(CLIENT)`. For client-only GUIs with no sync manager. | Sets `player` to the client player, `syncManager = null`. |
| `isInitialized()` | `player != null`. | — |
| `getScreen()` | `@SideOnly(CLIENT)`. | Throws `NullPointerException` if the client screen hasn't been initialized yet. |

### Override points

```java
public void onModularContainerOpened() {}
public void onModularContainerClosed() {}
public void onModularContainerDisposed() {}
public void onSlotChanged(ModularSlot slot, ItemStack stack, boolean onlyAmountChanged) {}
```
Gotcha (from the doc comment on `onModularContainerClosed`): this differs from vanilla
`Container#onContainerClosed(EntityPlayer)` — that one also fires from `GuiContainer#onGuiClosed()`, which can
happen while the container still exists (e.g. JEI/NEI takes over the screen temporarily). `onModularContainerClosed`
only fires when the container **actually** closes.

```java
@MustBeInvokedByOverriders
@Override
public void detectAndSendChanges();     // must call super; also runs syncManager.detectAndSendChanges(init)

@MustBeInvokedByOverriders
public void onUpdate();                 // once per tick, distinct from detectAndSendChanges which can run multiple times/tick
```
`CraftingModularContainer` overrides `detectAndSendChanges()` to additionally detect crafting-grid changes
(see example below).

```java
@Override
public boolean canInteractWith(@NotNull EntityPlayer playerIn);   // delegates to UISettings.canPlayerInteractWithUI
@Override
public boolean canDragIntoSlot(@NotNull Slot slotIn);
@Override
public ItemStack slotClick(int slotId, int mouseButton, int mode, EntityPlayer player);
@Override
public @Nullable ItemStack transferStackInSlot(@NotNull EntityPlayer playerIn, int index);   // shift-click
```
`slotClick`/`transferStackInSlot`/`transferItem` reimplement vanilla shift-click and slot-click logic to
support phantom slots and slot groups; not usually overridden by mod authors.

### Slot / sync-group registration

```java
@ApiStatus.Internal
public void registerSlot(String panelName, ModularSlot slot);
```
Called automatically when a `ModularSlot` widget is added to the widget tree; throws `IllegalArgumentException`
if the same slot instance is registered twice, or if its named slot group isn't registered.

```java
@Contract("_, null, null -> fail")
@NotNull
@ApiStatus.Internal
public SlotGroup validateSlotGroup(String panelName, @Nullable String slotGroupName, @Nullable SlotGroup slotGroup);
```

### Getters

| Method | Returns | Notes |
|---|---|---|
| `getSyncManager()` | `ModularSyncManager` | Throws `IllegalStateException` for client-only GUIs. |
| `isClient()` | `boolean` | true if no sync manager, or `NetworkUtils.isClient(player)`. |
| `isClientOnly()` | `boolean` | `syncManager == null`. |
| `getPlayer()` | `EntityPlayer` | — |
| `getGuiData()` | `GuiData` | — |
| `getModularSlot(int index)` | `ModularSlot` | Throws `IllegalStateException` if the slot at that index isn't a `ModularSlot`. |
| `getShiftClickSlots()` | `@UnmodifiableView List<ModularSlot>` | — |
| `acc()` | `ContainerAccessor` | Casts `this` to the Mixin accessor interface; internal use. |

### Example (from repo)

`src/main/java/com/cleanroommc/modularui/test/CraftingModularContainer.java`:
```java
public class CraftingModularContainer extends ModularContainer {

    private final InventoryCraftingWrapper inventoryCrafting;
    private ModularCraftingSlot craftingSlot;

    public CraftingModularContainer(int width, int height, IItemHandlerModifiable craftingInventory) {
        this(width, height, craftingInventory, 0);
    }

    public CraftingModularContainer(int width, int height, IItemHandlerModifiable craftingInventory, int startIndex) {
        this.inventoryCrafting = new InventoryCraftingWrapper(this, width, height, craftingInventory, startIndex);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        this.inventoryCrafting.detectChanges();
    }

    @Override
    public void registerSlot(String panelName, ModularSlot slot) {
        super.registerSlot(panelName, slot);
        if (slot instanceof ModularCraftingSlot craftingSlot1) {
            if (this.craftingSlot != null && this.craftingSlot != craftingSlot1) {
                throw new IllegalStateException("Only one crafting output slot is supported with CraftingModularContainer!");
            }
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

---

## `com.cleanroommc.modularui.screen.ModularPanel`

```java
public class ModularPanel extends ParentWidget<ModularPanel> implements IViewport, IDragResizeable
```
The root widget/container returned from `buildUI`, and also the type used for every secondary/sub panel opened
on top of it. Acts like a window: holds any number of child widgets, can be draggable, resizable, animated open
and closed (via NeverEnoughAnimations if present).

> "To open another panel on top of the main panel you must use `IPanelHandler#simple(ModularPanel, SecondaryPanel.IPanelBuilder, boolean)` or `PanelSyncManager#panel(String, PanelSyncHandler.IPanelBuilder, boolean)` if the panel should be synced."
> — class javadoc

### Static factories

```java
public static ModularPanel defaultPanel(@NotNull String name);                    // size 176x166 (vanilla inventory-like default)
public static ModularPanel defaultPanel(@NotNull String name, int width, int height);
```
Used pervasively in the test UIs, e.g. `TestGui.buildUI`: `ModularPanel panel = ModularPanel.defaultPanel("test");`.

### Constructor

```java
public ModularPanel(@NotNull String name)
```
`name` must be unique per screen (enforced only implicitly — `PanelManager.openPanel` throws
`IllegalStateException` if a panel with the same name is already open). Calls `center()` (from `ParentWidget`)
by default. Throws `NullPointerException` if `name` is null.

Gotcha (`@Deprecated @Override public ModularPanel name(String name)`): panel names are **final** — calling the
inherited `Widget#name(String)` setter throws `IllegalStateException`.

### Lifecycle

| Method | When | Notes |
|---|---|---|
| `onInit()` | Once, right after the widget tree is initialised (`@Override` of `IWidget`). | Registers a frame-update listener for hover-detection (`findHoveredWidgets`). |
| `onOpen(ModularScreen screen)` | When the panel is opened by `PanelManager`. `@MustBeInvokedByOverriders`. | Sets z-order to 1, initializes the resizer against the screen's resize node, initialises the widget tree, runs the first `onUpdate`, and — if not the main panel and animation is enabled — starts the open animation. Sets state to `OPEN`. |
| `onClose()` | When the panel closes. `@MustBeInvokedByOverriders`. | Runs `onCloseAction` if set, sets state to `CLOSED`, notifies the panel's `IPanelHandler` (if any). |
| `dispose()` | When the panel is permanently discarded (evicted from `PanelManager`'s disposal ring buffer, or screen closes for good). `@MustBeInvokedByOverriders @Override`. | Throws `IllegalStateException` if called while still open and not closing. After disposal the panel must be rebuilt to reopen. |

### Open/close state machine

```java
public enum State { OPEN, CLOSED, DISPOSED, WAIT_CLOSING, WAIT_DISPOSING, WAIT_CLOSING_AND_DISPOSING }
```
Each value carries `open`/`closing`/`disposing` booleans. The `WAIT_*` states exist because
`doSafe(Supplier<T>)` (see below) can defer close/dispose requests that arrive mid-callback.

```java
public boolean isOpen();
public void closeIfOpen();
public State getState();
```
`closeIfOpen()`: no-op if not open. If the panel currently "can't close now" (mid `doSafe` call), transitions
to a `WAIT_*` state instead of closing immediately. Closing the **main panel** closes the whole screen
(`MCHelper.popScreen(...)`). Otherwise, if NEA animation is enabled, plays the close animation and defers the
actual `PanelManager.closePanel(this)` call to the animation's finish callback; if this is itself the main
panel initiating a group close, it first triggers `closeIfOpen()` on every other open non-main panel.

```java
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "3.2.0")
public void animateClose();      // == closeIfOpen()
```

```java
public final <T> T doSafe(Supplier<T> runnable);
public final boolean doSafeBool(BooleanSupplier runnable);
public final int doSafeInt(IntSupplier runnable);
```
Purpose: run `runnable` while temporarily preventing this panel (and, via `PanelManager.doSafe`, the whole
screen) from being disposed out from under it — needed because input-handling callbacks (`onMousePressed` etc.)
can themselves trigger a close/dispose. Returns `null` immediately if the panel is already `DISPOSED`.

### Input dispatch (called by `ModularScreen`, not usually by mod code)

```java
public boolean onMousePressed(int mouseButton);
public boolean onMouseRelease(int mouseButton);
public boolean onKeyPressed(char typedChar, int keyCode);
public boolean onKeyRelease(char typedChar, int keyCode);
public boolean onMouseScroll(UpOrDown scrollDirection, int amount);
public boolean onMouseDrag(int mouseButton, long timeSinceClick);
public boolean onKeyEvent(InputEvents.KeyEvent event);     // lwjgl3ify only
public boolean onTextEvent(InputEvents.TextEvent event);   // lwjgl3ify only
```
All wrapped in `doSafeBool(...)`. Walks `this.hovering` (widgets currently under the mouse, most-specific
first) and tries `Interactable` handlers; handles resize-drag-area grabbing, recipe-viewer ghost-ingredient
drag targets (NEI), and focus assignment for `IFocusedWidget`s.

### Behavior override points

| Method | Default | Purpose |
|---|---|---|
| `isDraggable()` | `getScreen().getMainPanel() != this` | Main panel is never draggable. |
| `disablePanelsBelow()` | `false` | If true, panels below this one in the stack stop receiving input/consider-hover. |
| `closeOnOutOfBoundsClick()` | `false` | If true, a click outside this panel's hovering set closes it. |
| `isCurrentlyResizable()` (`IDragResizeable`) | `this.resizable` | See `resizeableOnDrag(boolean)`. |
| `keepPosOnDragResize()` (`IDragResizeable`) | `!isDraggable()` | — |
| `shouldAnimate()` | see javadoc | `@ApiStatus.Internal`. "It is strongly discouraged to override this method." Only false if invisible, NEA absent, or animation duration `<= 0`; for the main panel, only animates when there's no parent screen (i.e. it's the outermost screen) or `!isOpenParentOnClose()`. |

### Fluent configuration setters (return `this`/`ModularPanel`)

| Method | Params | Notes |
|---|---|---|
| `bindPlayerInventory()` | — | `child(SlotGroupWidget.playerInventory(true))`. |
| `bindPlayerInventory(int bottom)` | bottom margin | `child(SlotGroupWidget.playerInventory(bottom, true))`. |
| `invisible()` | — | `@Override`; also sets an internal `invisible` flag (affects `canHover()` and recipe-viewer exclusion). |
| `fullScreenInvisible()` | — | `invisible().full()`. |
| `resizeableOnDrag(boolean resizeable)` | — | Enables/disables drag-resize handles. |
| `onCloseAction(Runnable onCloseAction)` | — | Runs once, at the start of `onClose()`. |
| `themeOverride(String id)` | theme id | Clears the cached resolved theme so it's recomputed. |

### Getters / queries

| Method | Returns | Notes |
|---|---|---|
| `getName()` | `String` | Immutable, set in constructor. |
| `getScreen()` | `ModularScreen` | Throws `IllegalStateException` if `!isValid()` (not attached / disposed). |
| `getHovering()` | `ObjectList<LocatedWidget>` | All widgets currently under the mouse in this panel, most-specific first. |
| `getTopHovering()` / `getTopHoveringLocated(boolean debug)` | `IWidget` / `LocatedWidget` | Top of the hover list, skipping widgets that fail `canHover()` unless `debug`. |
| `isBelowMouse(IWidget)` | `boolean` | — |
| `isAnyHovered()` | `boolean` | Special-cased so a panel whose only hover is itself defers to `canHover()`. |
| `isOpening()` / `isClosing()` | `boolean` | Based on the NEA `Animator`'s direction. |
| `getScale()` / `getAlpha()` | `float` | Open/close animation values (1f if NEA absent or disabled). |
| `isMainPanel()` | `boolean` | `getScreen().getMainPanel() == this`. |
| `getTheme()` | `ITheme` | Lazily resolved via `IThemeApi`. |

### Coordinate space / transform

```java
@Override
public void transform(IViewportStack stack);
```
Called by the widget-tree transform pass; after the inherited `ParentWidget` transform, applies the
open/close-animation scale around the panel's own center (`getArea().w()/2, h()/2`). Inferred: panel-local
widget coordinates are relative to the panel's own area, and the panel's area itself is relative to the screen
(`getParentArea()` returns `getScreen().getScreenArea()`).

### Example (from repo)

See the `CustomModularScreen` example above — `ModularPanel.defaultPanel("test")` plus `.child(...)` calls.
A resizable/no-fixed-size example, `TestGuis.buildMachineLikeUI` (`src/main/java/com/cleanroommc/modularui/test/TestGuis.java:686`):
```java
return new ModularPanel("machine_like")
        .coverChildren()
        .padding(7)
        .child(Flow.col() /* ... */);
```

---

## `com.cleanroommc.modularui.screen.PanelManager`

Tracks every open/closed/disposed `ModularPanel` for one `ModularScreen`, and their stacking order. Not
normally constructed by mod code — one is created per `ModularScreen` in its constructor.

```java
public class PanelManager
```

### Constructor

```java
public PanelManager(ModularScreen screen, ModularPanel panel)
```
`panel` becomes the permanent main panel (`Objects.requireNonNull`). Package-visibility of everything except
query/stacking methods reflects that this is mostly framework-internal; mod authors mainly *read* from it
(`getOpenPanels()`, `isPanelOpen(...)`) and use `IPanelHandler` to open/close sub-panels rather than calling
`PanelManager` methods directly.

### How panels stack

- `panels` is an `ObjectList<ModularPanel>`, **top of stack first** (`panels.addFirst(panel)` on open).
- `getOpenPanels()` returns an unmodifiable view top-to-bottom; `getReverseOpenPanels()` returns bottom-to-top
  (used for drawing, so panels lower in the stack draw first / underneath).
- Input dispatch in `ModularScreen` iterates `getOpenPanels()` (top-first) and stops at the first panel that
  consumes the event or whose `disablePanelsBelow()` returns true.
- Max 127 concurrently open panels (`IllegalStateException` beyond that — `panels.size() == 127` check).
- Closed panels are not immediately discarded: `finalizePanel` moves them into a bounded disposal ring buffer
  (`DISPOSAL_CAPACITY = 16`) so they can be cheaply reopened; the oldest is disposed once the buffer is full.
- `tryInit()` (package-private, called from `ModularScreen.onResize`) handles first-open (`INIT`/`DISPOSED` →
  opens the main panel) vs. reopen-after-close (`CLOSED` → re-marks all previously-open panels as open again
  without rebuilding them, state `REOPENED`).

### Stack manipulation (z-order among open panels)

```java
public void pushUp(@NotNull ModularPanel panel);
public void pushDown(@NotNull ModularPanel panel);
public void pushToTop(@NotNull ModularPanel panel);
public void pushToBottom(@NotNull ModularPanel panel);
public void movePanelAbove(ModularPanel panelToMove, ModularPanel target);
public void movePanelBelow(ModularPanel panelToMove, ModularPanel target);
```
All throw `IllegalArgumentException` (via `getPanelIndexOrFail`) if `panelToMove`/`panel` isn't currently open.
`movePanelAbove`/`movePanelBelow` account for the target's own sub-panel chain (`SecondaryPanel.getParent()`)
so a whole sub-panel group moves together.

### Opening / closing

```java
@ApiStatus.Internal
public void openPanel(@NotNull ModularPanel panel, @NotNull IPanelHandler panelHandler);
```
Internal entry point used by `IPanelHandler` implementations (`SecondaryPanel`); mod code should call
`IPanelHandler.openPanel()` instead. Throws if the panel's name is already open (see `ModularPanel` ctor note).

```java
public boolean closeAll();               // closes all panels, screen stays "open"-ish but onClose() fires; NOT screen close
public boolean closePanelsAndScreen();    // proper full close: closes every sub panel then the main panel
```
Gotcha: "`closeAll()` won't close the screen and can put the screen and main panel into an invalid state if
used incorrectly. Use `closePanelsAndScreen()` to actually close all panels and the screen properly."

### Queries

| Method | Returns | Notes |
|---|---|---|
| `getMainPanel()` | `ModularPanel` | Throws `IllegalStateException` if disposed. |
| `getTopMostPanel()` | `ModularPanel` | Last opened / top of stack; `IndexOutOfBoundsException` if disposed. |
| `getOpenPanels()` / `getReverseOpenPanels()` | `List<ModularPanel>` / `Iterable<ModularPanel>` | Unmodifiable views. |
| `isPanelOpen(String name)` / `hasOpenPanel(ModularPanel)` / `hasPanelOpen(String)` / `getOpenPanel(String)` | — | Name/instance lookups. |
| `getOpenPanelCount()` | `int` | — |
| `isSubPanelOf(ModularPanel panel, ModularPanel target)` | `boolean` | Walks the `SecondaryPanel` parent chain. |
| `isClosed()` / `isDisposed()` / `isOpen()` / `isReopened()` | `boolean` | Reflects `PanelManager.State`. |

### Example (constructed, not from repo — reading the manager)

```java
PanelManager pm = screen.getPanelManager();
if (!pm.isPanelOpen("settings")) {
    // open via an IPanelHandler instead of calling pm.openPanel directly
}
for (ModularPanel p : pm.getOpenPanels()) {
    // top-to-bottom
}
```

---

## `com.cleanroommc.modularui.screen.SecondaryPanel`

Default, non-synced `IPanelHandler` implementation. Returned by the `IPanelHandler.simple(...)` factory method
used throughout `TestGui`/`TestGuis` to open a second panel.

```java
public class SecondaryPanel implements IPanelHandler
```

### Constructor

```java
public SecondaryPanel(ModularPanel parent, IPanelBuilder provider, boolean subPanel)
```
Mod code should not call this directly — use `IPanelHandler.simple(parent, provider, subPanel)` (defined as a
static default method on `IPanelHandler`, delegating to `new SecondaryPanel(...)`). Registers itself with the
parent panel (`parent.registerSubPanel(this)`) so the parent can close all its sub-panels together.

```java
public interface IPanelBuilder {
    ModularPanel build(ModularPanel parentPanel, EntityPlayer player);
}
```

### Behavior

- `openPanel()`: builds the panel lazily on first call (`buildPanel()`, client-side only) and caches it;
  subsequent calls reuse the cached panel instance. Throws `IllegalArgumentException` if the built panel *is*
  the main panel, or if the built panel contains any synced widgets (`WidgetTree.hasSyncedValues`) — use
  `PanelSyncManager#panel(...)` instead for synced sub-panels.
- `closePanel()` / `isPanelOpen()` / `isSubPanel()`: straightforward state accessors.
- `deleteCachedPanel()`: forces the panel to be rebuilt next `openPanel()` call; if currently open, the
  deletion is queued until it closes.

### Example (from repo)

`TestGui.buildUI` (`src/main/java/com/cleanroommc/modularui/test/TestGui.java:97-111`):
```java
IPanelHandler otherPanel = IPanelHandler.simple(panel, (mainPanel, player) -> {
    ModularPanel panel1 = new Dialog<>("Option Selection").setDisablePanelsBelow(false).setDraggable(false).size(150, 120);
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
`true` as the third argument makes this a sub-panel: it closes automatically when `panel` (the main panel)
closes.

---

## `com.cleanroommc.modularui.screen.UISettings`

Per-UI-factory configuration object: custom container/gui suppliers, interaction-range checks, theme,
recipe-viewer settings. Built up inside a `UIFactory` (outside this package) before the screen/container exist.

```java
public class UISettings
```

### Constructors

```java
public UISettings();                                          // uses a new RecipeViewerSettingsImpl()
public UISettings(RecipeViewerSettings recipeViewerSettings);   // custom recipe-viewer settings impl
```

### Configuration methods

| Method | Params | Notes |
|---|---|---|
| `customContainer(Supplier<ModularContainer> containerSupplier)` | supplier, must return a new instance each call | Overrides `UIFactory#createContainer()`. |
| `customGui(Supplier<GuiCreator> guiSupplier)` | supplier of a `GuiCreator` | Only evaluated `if (NetworkUtils.isDedicatedClient())`. `IMuiScreen#getGuiScreen()` must be a `GuiContainer` or an exception is thrown when the UI opens. |
| `canInteractWith(Predicate<EntityPlayer> canInteractWith)` | predicate | Checked every tick; once false, the UI closes immediately. |
| `canInteractWithinRange(double x, double y, double z, double range)` | block-center coords, range | Squared-distance check. |
| `canInteractWithinRange(int x, int y, int z, double range)` | block coords (adds 0.5 offset), range | — |
| `canInteractWithinRange(PosGuiData guiData, double range)` | — | — |
| `canInteractWithinDefaultRange(...)` (3 overloads) | same as above, no `range` arg | Uses `DEFAULT_INTERACT_RANGE = 8.0`. |
| `useTheme(String theme)` | theme id | — |

```java
public interface GuiCreator {
    IMuiScreen create(ModularContainer container, ModularScreen screen);
}
```

### Getters

| Method | Returns | Notes |
|---|---|---|
| `getRecipeViewerSettings()` | `RecipeViewerSettings` | — |
| `hasCustomContainer()` / `hasCustomGui()` | `boolean` | `hasCustomGui()` is `@SideOnly(CLIENT)`. |
| `canPlayerInteractWithUI(EntityPlayer player)` | `boolean` | `true` if no predicate was set. |
| `getTheme()` | `@Nullable String` | — |

Inferred: `createContainer()`/`createGui(...)` are `@ApiStatus.Internal`, invoked by the factory machinery, not
by mod code directly.

### Example (constructed, not from repo)

```java
UISettings settings = new UISettings();
settings.canInteractWithinDefaultRange(guiData);
settings.useTheme("my_mod:dark");
```

---

## `com.cleanroommc.modularui.screen.RichTooltip`

Builder + renderer for rich (multi-line, formatted, item/fluid-aware) tooltips, positioned relative to a
parent area or the mouse. Implements `IRichTextBuilder<RichTooltip>` (fluent text-building methods like `add`,
`newLine`, `spaceLine` live on that interface, not repeated here).

```java
public class RichTooltip implements IRichTextBuilder<RichTooltip>
```

### Constructor

```java
public RichTooltip()
```
Defaults `parent` to `Area.ZERO` (falls back to next-to-mouse positioning).

### Positioning

```java
public enum Pos { ABOVE, BELOW, LEFT, RIGHT, VERTICAL, HORIZONTAL, NEXT_TO_MOUSE, FIXED }
```
- `VERTICAL`/`HORIZONTAL` auto-pick `ABOVE`/`BELOW` or `LEFT`/`RIGHT` based on available screen space.
- `NEXT_TO_MOUSE`: vanilla-style tooltip that floats next to the cursor (used when no parent area is set).
- `FIXED`: uses the `x`/`y` set via `pos(int, int)`.

```java
public RichTooltip pos(Pos pos);
public RichTooltip pos(int x, int y);     // implies Pos.FIXED
```

```java
public RichTooltip parent(Consumer<Area> parent);
public RichTooltip parent(Supplier<Area> parent);
public RichTooltip parent(Area parent);
public RichTooltip parent(IWidget parent);   // area relative to the widget itself: (0,0,width,height)
```
Gotcha: if `pos` resolves to a non-`FIXED`/non-`NEXT_TO_MOUSE` value but no parent area was set (or it's all
zero), positioning silently falls back to `NEXT_TO_MOUSE`. Throws `IllegalStateException` at draw time if a
directional `Pos` is set with `parent == null` (can't happen via the fluent API since the constructor sets a
default parent, but is possible if `parent(null)` slips through some other caller).

### Content builders (item/fluid convenience)

```java
public RichTooltip addFromItem(ItemStack item);
public RichTooltip addFromFluid(FluidStack fluid);
public RichTooltip addAdditionalInfoFromFluid(FluidStack fluid);
```

### Other fluent setters

| Method | Params | Notes |
|---|---|---|
| `showUpTimer(int)` | ticks | Delay before showing (consumer of this value is elsewhere; this class only stores it). |
| `setAutoUpdate(boolean)` | — | If true, `markDirty()` is called every `draw(...)` call, forcing the tooltip content to rebuild every frame. |
| `tooltipBuilder(Consumer<RichTooltip> builder)` | — | Deferred content builder invoked lazily in `buildTooltip()`; chaining calls **compose** rather than overwrite (both old and new builder run). Marks dirty. |
| `titleMargin()` / `titleMargin(int margin)` | — | Inserts blank space after the title line once text is (re)built. |

### Draw / query

```java
public void draw(GuiContext context);
public void draw(GuiContext context, @Nullable ItemStack stack);
```
Rebuilds content if dirty (`buildTooltip()`), fires `RichTooltipEvent.Pre` (cancelable — return early if
cancelled), computes final position and size via `determineTooltipArea(...)`, then draws background
(`RichTooltipEvent.PostBackground`) and text (`RichTooltipEvent.PostText`), posting the corresponding events
around each stage. Gotcha: `stack` is only used for the `RichTooltipEvent`s and background texture selection —
tooltip *text* content must already have been added via `addFromItem`/`add(...)`.

```java
public boolean isEmpty();     // rebuilds first if dirty
public void markDirty();
```

```java
@ApiStatus.Internal
public static void injectRichTooltip(ItemStack stack, List<String> lines, int x, int y);
```
Used to render a `RichTooltip` for vanilla/NEI-driven item tooltips outside of a `ModularGuiContext` (uses
`GuiContext.getDefault()`).

### Example (from repo)

`RichTextWidget` item-icon tooltip, `TestGuis.buildRichTextUI` (`src/main/java/com/cleanroommc/modularui/test/TestGuis.java:322`):
```java
.add(new ItemDrawable(new ItemStack(Blocks.grass))
        .asIcon()
        .asHoverable()
        .tooltip(richTooltip -> richTooltip.addFromItem(new ItemStack(Blocks.grass))
                .add(IKey.GRAY + "Lorem ipsum dolor sit amet...")))
```
Standalone tooltip on a draggable widget, same file:
```java
.tooltipBuilder(tooltip -> {
    tooltip.addLine("Lorem ipsum dolor sit amet, ...");
    tooltip.addLine("Longer Line 2");
    tooltip.addLine("Line 3");
    tooltip.alignment(Alignment.Center);
    tooltip.scale(0.5f);
    tooltip.pos(RichTooltip.Pos.NEXT_TO_MOUSE);
})
```

---

## `com.cleanroommc.modularui.screen.RichTooltipEvent`

Forge event family posted around `RichTooltip` rendering, letting other mods inspect/modify tooltip
positioning, colors, or react after each render stage.

```java
public class RichTooltipEvent extends Event
```

| Nested type | Cancelable | Fired | Purpose |
|---|---|---|---|
| `Pre` | yes (`@Cancelable`) | Before any layout/size computation. | Mutable `x`/`y`/`screenWidth`/`screenHeight`/`maxWidth`/`fontRenderer`; canceling skips the whole tooltip draw. |
| `Color` | no | *(constructed elsewhere — likely `GuiDraw.drawTooltipBackground`, not in this file)* | Mutable `background`/`borderStart`/`borderEnd`; also exposes the un-mutated `original*` values. |
| `PostBackground` | no | After the tooltip background is drawn. | Read-only `width`/`height` of the drawn tooltip. |
| `PostText` | no | After the tooltip text is drawn. | Read-only `width`/`height`. |

Common accessors on the base class: `getTooltip()` (`IRichTextBuilder<?>`), `getItemStack()`, `getX()`, `getY()`.

### Example (constructed, not from repo)

```java
@SubscribeEvent
public void onTooltipPre(RichTooltipEvent.Pre event) {
    if (event.getItemStack() != null) {
        event.setMaxWidth(Math.min(event.getMaxWidth(), 200));
    }
}
```

---

## `com.cleanroommc.modularui.screen.GuiScreenWrapper`

Default `IMuiScreen` implementation wrapping a non-container `ModularScreen` in a vanilla `GuiScreen`. Use this
(by extending it, or as-is) for UIs that are not backed by a `Container`.

```java
@SideOnly(Side.CLIENT)
public class GuiScreenWrapper extends GuiScreen implements IMuiScreen
```

```java
public GuiScreenWrapper(ModularScreen screen)
```
Calls `screen.construct(this)` immediately — per `IMuiScreen`'s contract, any custom wrapper "MUST call
`ModularScreen#construct(IMuiScreen)` in its constructor."

| Method | Notes |
|---|---|
| `getScreen()` | Returns the wrapped `ModularScreen`. |
| `doesPauseGame()` | `screen == null \|\| screen.doesPauseGame()`. |
| `drawWorldBackground(int tint)` | Delegates to `IMuiScreen.handleDrawBackground(tint, super::drawWorldBackground)`. |

### Example (constructed, not from repo)

```java
ModularScreen screen = new ModularScreen(MyMod.ID, myPanel);
Minecraft.getMinecraft().displayGuiScreen(new GuiScreenWrapper(screen));
```

---

## `com.cleanroommc.modularui.screen.GuiContainerWrapper`

Default `IMuiScreen` implementation wrapping a `ModularScreen` in a vanilla `GuiContainer`. `TestGuiContainer`
extends this directly.

```java
@SideOnly(Side.CLIENT)
public class GuiContainerWrapper extends GuiContainer implements IMuiScreen
```

```java
public GuiContainerWrapper(ModularContainer container, ModularScreen screen)
```
Calls `super(container)` then `screen.construct(this)`.

| Method | Notes |
|---|---|
| `getScreen()` | — |
| `doesGuiPauseGame()` | `screen != null && screen.doesPauseGame()`. |
| `drawGuiContainerBackgroundLayer(...)` | Overridden to a no-op — ModularUI draws its own background via `ModularScreen`/`ClientScreenHandler`. |
| `drawWorldBackground(int tint)` | Same pattern as `GuiScreenWrapper`. |

### Example (from repo)

`src/main/java/com/cleanroommc/modularui/test/TestGuiContainer.java` (full file):
```java
package com.cleanroommc.modularui.test;

public class TestGuiContainer extends GuiContainerWrapper {

    public TestGuiContainer(ModularContainer container, ModularScreen screen) {
        super(container, screen);
        ModularUI.LOGGER.info("Created custom gui container");
    }
}
```
Typically registered via `UISettings.customGui(() -> TestGuiContainer::new)` (matching the `GuiCreator`
functional interface).

---

## `com.cleanroommc.modularui.screen.IClickableGuiContainer`

Marker/mixin-support interface letting `ClientScreenHandler` simulate a vanilla slot click programmatically
(used e.g. by NEI/recipe-viewer "transfer recipe" integrations).

```java
public interface IClickableGuiContainer {
    void modularUI$setClickedSlot(Slot slot);
    Slot modularUI$getClickedSlot();
}
```
Inferred: implemented via Mixin on the actual `GuiContainer` classes, not by mod-authored Java source directly;
paired with `ClientScreenHandler.clickSlot(ModularScreen, Slot)`.

---

## `com.cleanroommc.modularui.screen.DraggablePanelWrapper`

Adapts a `ModularPanel` to the `IDraggable` interface so the generic drag-and-drop machinery in
`ModularGuiContext` can drag whole panels around the screen (mouse-down on a panel's own body, not a widget).

```java
public class DraggablePanelWrapper implements IDraggable
```

```java
public DraggablePanelWrapper(ModularPanel panel)
```
Copies the panel's current area into an internal `movingArea` snapshot used while dragging.

| Method | Notes |
|---|---|
| `onDragStart(int button)` | Only left button (`button == 0`) starts a drag; records the click offset relative to the panel's top-left. |
| `onDrag(int mouseButton, long timeSinceLastClick)` | Updates `movingArea` to follow the mouse. |
| `onDragEnd(boolean successful)` | On success, computes final relative position as a *fraction* of remaining screen space and re-anchors the panel with `resizer().relativeToScreen()` + `topRelAnchor(...).leftRelAnchor(...)`, then `scheduleResize()`. |
| `drawMovingState(context, partialTicks)` | Draws the whole panel tree translated to the current drag position, via `WidgetTree.drawTree(panel, context, true, true)`. |
| `getMovingArea()` / `isMoving()` / `setMoving(boolean)` | `setMoving(true)` also disables the panel itself (`panel.setEnabled(!moving)`) so it doesn't receive normal input while being dragged. |
| `transform(IViewportStack stack)` | While moving, cancels the panel's normal area offset and substitutes the drag position. |

Gotcha: this is constructed internally by `ModularGuiContext.onHoveredClick(...)` when a click lands on a
draggable `ModularPanel` (`panel.isDraggable()` must be true, and the panel must have a fixed size —
`resizer().hasFixedSize()` — or an `IllegalStateException` is thrown). Not normally instantiated by mod code.

---

## `com.cleanroommc.modularui.screen.ClientScreenHandler`

`@ApiStatus.Internal` — the Forge event-subscriber glue that drives every open `ModularScreen`: gui-open/resize
tracking, keyboard/mouse input routing (including NEI-key passthrough and vanilla E/ESC handling), per-frame
drawing (including debug overlay), and the "mui screen stack" bookkeeping used to know which `GuiScreen` a
close should return to.

```java
@ApiStatus.Internal
@SideOnly(Side.CLIENT)
public class ClientScreenHandler
```

Mod authors generally do not call into this class beyond the handful of static query/utility methods below;
everything else is wired via `@SubscribeEvent` to the Forge event bus.

### Static utility methods usable by mod code

| Method | Returns | Notes |
|---|---|---|
| `hasScreen()` | `boolean` | Is any modular screen currently the active `GuiScreen`. |
| `getMCScreen()` | `@Nullable GuiScreen` | Current vanilla screen (`MCHelper.getCurrentScreen()`). |
| `getMuiScreen()` | `@Nullable ModularScreen` | Current modular screen, if any. |
| `getMuiStack()` | `@UnmodifiableView List<IMuiScreen>` | Stack of nested modular screens (e.g. screen A opens screen B). |
| `getDefaultContext()` | `GuiContext` | The shared context used when no modular screen is open (e.g. for tooltips over vanilla GUIs). |
| `getBestContext()` | `GuiContext` | The active screen's context if valid, else the default context. |
| `getTicks()` | `long` | Client tick counter, incremented in `onTick`. |
| `shouldDrawWorldBackground()` | `boolean` | `Minecraft.theWorld == null`. |
| `handleKeyboardInput(ModularScreen, GuiScreen)` | `boolean` | Public overload of the internal input replicator, for `EARLY` phase only. |
| `clickSlot(ModularScreen ms, Slot slot)` | `void` | Programmatically simulates a vanilla slot click via `IClickableGuiContainer`. |
| `releaseSlot()` | `void` | Simulates a mouse-release on the current screen. |
| `dragSlot(long timeSinceLastClick)` | `void` | Simulates a slot drag-move. |

Everything else (`onGuiChange`, `onGuiInit`, `onGuiInputHigh/Low`, `onGuiDraw`, `onTick`, `preDraw`,
`drawScreen`, `drawContainer`, `drawDebugScreen`, etc.) is `@SubscribeEvent`-annotated Forge event handling or
internal rendering machinery — not meant to be called directly.

Inferred: this is the class registered once on the Forge event bus by ModularUI's own mod init; a mod author
never instantiates or registers it themselves.

---

## `com.cleanroommc.modularui.screen.NEAAnimationHandler`

Static helper bridging `ModularContainer` slot operations to NeverEnoughAnimations (item-move/hover/pickup
animations). Entirely conditional on `ModularUI.Mods.NEA.isLoaded()`.

```java
public class NEAAnimationHandler
```

All methods are static; none are meant to be called by ordinary mod-author UI code — they're invoked from
inside `ModularContainer.slotClick`/`transferStackInSlot`/`GuiContainerWrapper` drawing paths
(`shouldHandleNEA`, `injectQuickMove`, `pickupAllPre/Mid/Post`, `injectVirtualStack`, `injectHoverScale`,
`endHoverScale`, `drawItemAnimation`, `injectVirtualCursorStack`). Documented here only for completeness since
it's a public class in this package; a mod author extending `ModularContainer`/`GuiContainerWrapper` will not
normally touch it directly unless writing a custom container that needs the same NEA hooks as
`ModularContainer` provides automatically.

---

## `com.cleanroommc.modularui.screen.OpenScreenEvent`

Forge event fired when a `GuiScreen` opens (modular or not), letting other mods attach overlay
`ModularScreen`s to it.

```java
public class OpenScreenEvent extends Event
```

```java
public OpenScreenEvent(GuiScreen screen)
```

| Method | Returns | Notes |
|---|---|---|
| `getScreen()` | `GuiScreen` | The vanilla screen being opened. |
| `isModularScreen()` | `boolean` | true if `screen instanceof IMuiScreen`. |
| `getModularScreen()` | `@Nullable ModularScreen` | Unwraps if modular, else null. |
| `getOverlays()` | `List<ModularScreen>` | Mutable — accumulates registered overlays. |
| `addOverlay(ModularScreen screen)` | `void` | Registers an overlay screen to render/tick alongside `getScreen()`. |

### Example (constructed, not from repo)

```java
@SubscribeEvent
public void onOpenScreen(OpenScreenEvent event) {
    if (event.getScreen() instanceof GuiContainer) {
        event.addOverlay(myHudOverlayScreen);
    }
}
```

---

## `com.cleanroommc.modularui.screen.RecipeViewerSettingsImpl`

Per-screen recipe-viewer (JEI/NEI/EMI-style) integration state: force-enable/disable, and exclusion
zones/widgets that recipe viewers should avoid overlapping.

```java
@SideOnly(Side.CLIENT)
public class RecipeViewerSettingsImpl implements RecipeViewerSettings
```
Obtained via `UISettings.getRecipeViewerSettings()` / `ModularGuiContext.getRecipeViewerSettings()` — not
constructed directly by mod code except when passed into `new UISettings(RecipeViewerSettings)`.

| Method | Notes |
|---|---|
| `enable()` / `disable()` / `defaultState()` | Force-enabled, force-disabled, or "only enabled in synced GUIs" (the default). |
| `isEnabled(ModularScreen screen)` | Delegates to `RecipeViewerState.test(screen)`. |
| `addExclusionArea(Rectangle area)` / `removeExclusionArea(Rectangle)` | Static exclusion rectangle. |
| `addExclusionArea(IWidget area)` / `removeExclusionArea(IWidget)` | Dynamic exclusion tied to a widget's current area — javadoc: "If a widget wishes to have an exclusion zone it should use this overload" rather than the `Rectangle` one. |
| `getRecipeViewerExclusionAreas()` / `getRecipeViewerExclusionWidgets()` | `@UnmodifiableView List<...>` | — |

### Example (constructed, not from repo)

```java
context.getRecipeViewerSettings().addExclusionArea(mySlotWidget);
```

---

## `com.cleanroommc.modularui.screen.viewport.GuiContext`

Base matrix/pose-stack + input/render-state holder. `ModularGuiContext` (below) extends this with modular-UI
specific state; `GuiContext` alone is what non-modular drawables (`IDrawable`) receive via `getDefault()`.

```java
public class GuiContext extends GuiViewportStack
```

```java
public static GuiContext getDefault();
```
Returns `ClientScreenHandler.getBestContext()` — the active modular screen's context if one is open, otherwise
a shared fallback context that's kept updated even with no UI open (usable from `IDrawable`s rendered outside
any modular screen, e.g. vanilla-GUI item tooltips).

### State accessors

| Method | Returns | Notes |
|---|---|---|
| `getScreenArea()` | `Area` | Full game-window rectangle. |
| `getMouseX()` / `getMouseY()` | `int` | Mouse position **with current viewport transform applied** (i.e. local/scrolled coordinates) — `unTransformX/Y(absMouseX, absMouseY)`. |
| `getAbsMouseX()` / `getAbsMouseY()` | `int` | Mouse position **without** viewport transform — raw screen pixels. |
| `getMouse(GuiAxis axis)` / `getAbsMouse(GuiAxis axis)` | `int` | Axis-generic versions of the above. |
| `getMouseButton()` / `getMouseWheel()` | `int` | Last event's button / wheel delta. |
| `getKeyCode()` / `getTypedChar()` | `int` / `char` | Last keyboard event. |
| `getPartialTicks()` | `float` | Render partial-tick fraction. |
| `getTick()` | `long` | Ticks since context creation. |
| `getCurrentDrawingZ()` | `int` | Current z-layer being drawn (0 = normal widgets, 100 = foreground pass — see `ModularScreen.drawForeground`). |
| `isMuiContext()` / `getMuiContext()` | `boolean` / `ModularGuiContext` | Base class always returns `false` / throws `UnsupportedOperationException`; overridden by `ModularGuiContext`. Use `isMuiContext()` to safely check before calling `getMuiContext()` on a possibly-plain `GuiContext`. |

### Hit-testing

```java
public boolean isAbove(IWidget widget);        // == isMouseAbove(widget)
public boolean isMouseAbove(IWidget widget);
public boolean isMouseAbove(Area area);
```
Gotcha: these test the **absolute** mouse position against the widget's area — coordinate-space correctness
depends on the area already being in the same (typically absolute/screen) space; for widgets under a scrolled
viewport, prefer widget-provided hover queries (`IWidget.isHovering()`) over calling this directly with a raw
`Area`.

### `@ApiStatus.Internal` update hooks

`updateState(mouseX, mouseY, partialTicks)`, `updateEventState()`, `updateScreenArea(w, h)`, `updateZ(int)`,
`tick()` — called by `ClientScreenHandler` each frame/tick; not for mod code to call.

### Example (constructed, not from repo — a custom `IDrawable`)

```java
IDrawable custom = (context, x, y, width, height, widgetTheme) -> {
    if (context.isMouseAbove(new Area(x, y, width, height))) {
        GuiDraw.drawRect(x, y, width, height, 0x80FFFFFF);
    }
};
```
(`TestGuis.buildSpriteAndEntityUI` uses a similar anonymous `IDrawable` pattern with `GuiContext` at
`src/main/java/com/cleanroommc/modularui/test/TestGuis.java:307-319`.)

---

## `com.cleanroommc.modularui.screen.viewport.ModularGuiContext`

The context object passed into `CustomModularScreen.buildUI(ModularGuiContext context)` and returned by
`ModularScreen.getContext()`. Extends `GuiContext` with everything specific to a live modular UI: which screen
it belongs to, currently focused/hovered/dragged widgets, and UI settings (recipe viewer, theme, interact
range). **One instance is created per `ModularScreen` and lives for that screen's whole lifetime** (created
eagerly as a `final` field in `ModularScreen`'s own field initializer, before the constructor body runs).

```java
public class ModularGuiContext extends GuiContext
```

### Constructor

```java
public ModularGuiContext(ModularScreen screen)
```
Not normally called by mod code — `ModularScreen` creates its own. Passed by reference into `buildUI(...)` so
widgets built there can already register hover/focus behavior against the right context.

### Screen / parent-screen linkage

| Method | Returns | Notes |
|---|---|---|
| `getScreen()` | `ModularScreen` | Owning screen. |
| `getParentScreen()` | `@Nullable GuiScreen` | The vanilla screen that was open before this one, if any (used to return to it on close via `openParentOnClose`). |
| `setParentScreen(GuiScreen)` | `void` | `@ApiStatus.Internal`, set by `ClientScreenHandler` on screen-open. |

### Hover queries

| Method | Returns | Notes |
|---|---|---|
| `isHovered()` | `boolean` | Any widget hovered anywhere in the screen. |
| `getTopHovered()` | `@Nullable IWidget` | Topmost hovered widget across all open panels. |
| `getAllHovered()` | `@UnmodifiableView Iterable<IWidget>` | All hovered widgets; iterating across multiple ticks after the underlying list changed throws `ConcurrentModificationException` by design. |
| `getAllBelowMouse()` | `Iterable<IWidget>` | Widgets whose area (not necessarily interactable) contains the mouse. |
| `isHovered(IWidget)` / `isHoveredFor(IWidget, int ticks)` / `getHovered()` | — | `@Deprecated`, scheduled for removal in 3.2.0 — use `IWidget.isHovering()`/`isHoveringFor(int)`/`getTopHovered()` instead. |

### Focus

```java
public boolean isFocused();
public boolean isFocused(IFocusedWidget widget);
public LocatedWidget getFocusedWidget();
public void focus(IFocusedWidget widget);
public void focus(@NotNull LocatedWidget widget);
public void removeFocus();
public boolean focusNext(IWidget parent);
public boolean focusPrevious(IWidget parent);
public boolean focus(IWidget parent, int index, int factor);
public boolean focus(IWidget widget, int index, int factor, boolean stop);
```
`focus(LocatedWidget)` throws `IllegalArgumentException` if the widget is non-null and not an
`IFocusedWidget`. Focus changes call `onRemoveFocus`/`onFocus` on the affected widgets and toggle
`ModularScreen.setFocused(boolean)` (relevant for text-field/recipe-viewer input priority).
`focusNext`/`focusPrevious`/`focus(parent, index, factor[, stop])` implement tab-order traversal by walking
sibling/parent widget trees.

### Draggable-element state

```java
public boolean hasDraggable();
public boolean isMouseItemEmpty();
```
`@ApiStatus.Internal`: `onMousePressed(int)`, `onMouseReleased(int)`, `dropDraggable(boolean shouldCancel)`,
`onHoveredClick(int button, LocatedWidget hovered)`, `drawDraggable()`, `onFrameUpdate()` — drive drag-and-drop
for `IDraggable` widgets and draggable `ModularPanel`s (via `DraggablePanelWrapper`); called by
`ModularScreen`/`ClientScreenHandler`, not mod code.

### Settings

| Method | Returns | Notes |
|---|---|---|
| `getUISettings()` | `UISettings` | Throws `IllegalStateException` if screen not yet initialised. |
| `getRecipeViewerSettings()` | `RecipeViewerSettingsImpl` | Throws `IllegalStateException` for overlay screens. |
| `hasSettings()` | `boolean` | — |
| `getTheme()` | `ITheme` | `screen.getCurrentTheme()`. |
| `setSettings(UISettings)` | `void` | `@ApiStatus.Internal`, one-time set (throws if called twice). |

### Public field

```java
public List<Consumer<ModularGuiContext>> postRenderCallbacks = new ArrayList<>();
```
Consumers run once at the end of `ModularScreen.drawScreen()`, after all panels are drawn but still within the
same viewport push — a hook point for one-off overlay drawing tied to a specific frame.

### Example (from repo)

`buildUI` receiving the context and using it purely to pass through, `TestGui.buildUI`
(`src/main/java/com/cleanroommc/modularui/test/TestGui.java:48`):
```java
@Override
public @NotNull ModularPanel buildUI(ModularGuiContext context) {
    // context is available here to read screen size / register theme, etc.
    ...
}
```
A more direct usage — setting the theme from a panel built without a screen yet, `TestGuis.buildToggleGridListUI`
(`src/main/java/com/cleanroommc/modularui/test/TestGuis.java:189-194`):
```java
return new ModularPanel("grid_list") {
    @Override
    public void onInit() {
        super.onInit();
        getScreen().useTheme(TestEventHandler.TEST_THEME);
    }
}.height(100)/* ... */;
```

---

## `com.cleanroommc.modularui.screen.viewport.GuiViewportStack`

Matrix/pose stack implementation (`IViewportStack`) tracking nested widget transforms, especially scroll-area
displacement. `GuiContext` extends this directly, so every context is itself a viewport stack.

```java
public class GuiViewportStack implements IViewportStack
```

Pools `TransformationMatrix` instances (cap 256) to avoid per-frame allocation churn.

### Stack operations

| Method | Notes |
|---|---|
| `pushViewport(IViewport viewport, Area area)` | Pushes a new *viewport* matrix, clamping `area` against the current top viewport's area if one exists. Throws if the current top viewport isn't actually a viewport matrix or has a null area — internal-consistency checks. |
| `popViewport(IViewport viewport)` | Must match the most-recently-pushed viewport, else throws `IllegalStateException` ("Viewports must be popped in reverse order they were pushed."). |
| `pushMatrix()` / `popMatrix()` | Plain (non-viewport) matrix push/pop; `popMatrix()` throws if the top is actually a viewport matrix. |
| `popUntilIndex(int index)` / `popUntilViewport(IViewport viewport)` | Bulk-pop helpers. |
| `getStackSize()` | `int` current depth. |

### Transform application

```java
public void translate(float x, float y);
public void translate(float x, float y, float z);
public void rotate(float angle, float x, float y, float z);
public void rotateZ(float angle);
public void scale(float x, float y);
public void multiply(Matrix4f matrix);
public void resetCurrent();
```
All except `resetCurrent()` throw `IllegalStateException` ("Tried to transform viewport, but there is no
viewport!") if the stack is empty — i.e. these must be called between a push and its matching pop.

### Coordinate conversion

```java
public int transformX(float x, float y);
public int transformY(float x, float y);
public int unTransformX(float x, float y);
public int unTransformY(float x, float y);
public Vector3f transform(Vector3f vec, Vector3f dest);
public Vector3f unTransform(Vector3f vec, Vector3f dest);
```
"Transform" = local → screen space (applies the accumulated matrix); "unTransform" = screen → local space
(applies the inverted matrix, lazily computed and cached per `TransformationMatrix`). Falls back to identity
(returns `x`/`y` unchanged) if the stack is empty.

```java
public void applyToOpenGl();     // pushes the current top matrix into GL modelview, via GuiUtils
public TransformationMatrix peek();
```

### Example (from repo)

`DraggablePanelWrapper.drawMovingState` (`src/main/java/com/cleanroommc/modularui/screen/DraggablePanelWrapper.java:24-29`):
```java
@Override
public void drawMovingState(ModularGuiContext context, float partialTicks) {
    context.pushMatrix();
    transform(context);
    WidgetTree.drawTree(this.panel, context, true, true);
    context.popMatrix();
}
```

---

## `com.cleanroommc.modularui.screen.viewport.TransformationMatrix`

`@ApiStatus.Internal` — a single node in the `GuiViewportStack` chain: a JOML `Matrix4f` (plus its lazily
inverted counterpart), optionally tagged as a *viewport* matrix (carrying an `IViewport` + clamped `Area`).

```java
@ApiStatus.Internal
public class TransformationMatrix
```

```java
public static final TransformationMatrix EMPTY = new TransformationMatrix(null);
```
Identity matrix, non-viewport; used as a placeholder (e.g. `LocatedWidget.EMPTY`).

### Reuse pattern

Instances are pooled and reconstructed in place (`construct(...)` overloads) rather than freshly allocated per
push, hence `checkInUse()`/`dispose()` guarding double-use. Not something mod code constructs directly outside
of framework/widget-transform internals (`DraggablePanelWrapper`, `LocatedElement`, custom `IViewport`
implementations).

### Read accessors

| Method | Returns | Notes |
|---|---|---|
| `getWrapped()` | `TransformationMatrix` | Parent matrix this one was built from (if constructed as a copy), else null. |
| `getViewport()` | `IViewport` | Non-null only if `isViewportMatrix()`. |
| `getArea()` | `Area` | The clamped viewport area, only meaningful for viewport matrices. |
| `getMatrix()` / `getInvertedMatrix()` | `Matrix4f` | Inverted matrix is computed lazily and cached until `markDirty()`. |
| `isViewportMatrix()` | `boolean` | — |
| `isDirty()` / `markDirty()` | `boolean` / `void` | Controls inverted-matrix cache invalidation. |
| `isInUse()` | `boolean` | Pool bookkeeping. |

### Coordinate conversion (same semantics as `GuiViewportStack`, but for this single matrix)

```java
public int transformX(float x, float y);
public int transformY(float x, float y);
public int unTransformX(float x, float y);
public int unTransformY(float x, float y);
public Vector3f transform(Vector3f vec, Vector3f dest);
public Vector3f unTransform(Vector3f vec, Vector3f dest);
public static Vector3f transform(Matrix4f m, Vector3f vec, Vector3f dest);   // static utility variant
```

---

## `com.cleanroommc.modularui.screen.viewport.LocatedElement<T>`

Pairs an arbitrary element `T` with a **snapshot** of the transformation matrix that was active when the
element was located (as opposed to `GuiViewportStack`'s live, mutable top-of-stack). Base class of
`LocatedWidget`.

```java
public class LocatedElement<T>
```

```java
public LocatedElement(T element, TransformationMatrix transformationMatrix)
```
Copies (`new TransformationMatrix(transformationMatrix, null)`) rather than holding a live reference — so the
snapshot survives the source matrix being popped/reused/pooled.

| Method | Returns | Notes |
|---|---|---|
| `getElement()` | `T` | — |
| `getTransformationMatrix()` | `TransformationMatrix` | The snapshot. |
| `applyMatrix(GuiContext context)` | `void` | `context.push(this.transformationMatrix)` — temporarily re-enters this element's coordinate space (used before calling `Interactable` methods on a hovered widget, so its input handlers see local coordinates). |
| `unapplyMatrix(GuiContext context)` | `void` | Matching `context.pop(...)` — **must be paired** with `applyMatrix`, same discipline as `GuiViewportStack` push/pop. |
| `createHashStrategy()` | `LocatedElementHashStrategy<T>` | Identity-by-element hashing/equality helper for use in fastutil hash collections. |

### Example (constructed, not from repo)

```java
LocatedWidget lw = LocatedWidget.of(myWidget);
lw.applyMatrix(context);
// myWidget's local coordinate space is now active on `context`
lw.unapplyMatrix(context);
```

---

## `com.cleanroommc.modularui.screen.viewport.LocatedWidget`

`LocatedElement<IWidget>` specialization — the type actually stored in `ModularPanel.getHovering()`,
`ModularGuiContext.getFocusedWidget()`, etc. Adds an `additionalHoverInfo` payload (e.g. a `ResizeDragArea`
when the located widget is being hovered specifically over its resize handle).

```java
public class LocatedWidget extends LocatedElement<IWidget>
```

```java
public static final LocatedWidget EMPTY = new LocatedWidget(null, TransformationMatrix.EMPTY, null);
```
Sentinel for "nothing located" (used instead of `null` throughout `ModularPanel`/`ModularGuiContext` to avoid
null checks — e.g. `getFocusedWidget()` never returns `null`, only possibly `EMPTY`).

```java
public static LocatedWidget of(IWidget widget);
```
Builds a fresh `LocatedWidget` by walking from the widget up to its owning `ModularPanel`, then replaying each
ancestor's `transform(...)` (and, for `IViewport`s, `pushViewport`/`transformChildren`) top-down on a shared
static `GuiViewportStack`. Returns `EMPTY` if `widget == null`. Gotcha: this recomputes the full transform
chain from the panel down — relatively expensive; prefer widgets that are already `LocatedWidget`s from hover
lists over calling `of(...)` repeatedly per frame.

```java
public LocatedWidget(IWidget element, TransformationMatrix transformationMatrix, Object additionalHoverInfo)
```

| Method | Returns | Notes |
|---|---|---|
| `getAdditionalHoverInfo()` | `Object` | e.g. cast to `ResizeDragArea` when relevant — see `ModularPanel.onMousePressed` checking `widget.getAdditionalHoverInfo() instanceof ResizeDragArea`. |

```java
public static final Hash.Strategy<LocatedWidget> HASH_STRATEGY = new HashStrategy();
```
Identity-by-`getElement()` strategy, mirroring `LocatedElement.LocatedElementHashStrategy`.

### Example (from repo)

`ModularPanel.onMousePressed` resize-handle detection
(`src/main/java/com/cleanroommc/modularui/screen/ModularPanel.java:355-365`):
```java
if (w instanceof IDragResizeable resizeable && widget.getAdditionalHoverInfo() instanceof ResizeDragArea dragArea) {
    this.currentResizing = resizeable;
    this.currentResizingWidget = widget;
    this.dragX = getContext().getMouseX();
    this.dragY = getContext().getMouseY();
    this.startArea.set(w.getArea());
    this.startArea.rx = w.getArea().rx;
    this.startArea.ry = w.getArea().ry;
    this.draggingDragArea = dragArea;
    widget.unapplyMatrix(getContext());
    break;
}
```

---

## Ambiguities / things flagged during research

- `RichTooltipEvent.Color` is never constructed inside `RichTooltip.java` itself — it must be fired from
  `GuiDraw.drawTooltipBackground(...)`, which lives outside this package and was not read for this doc.
  Documented here from its own field/constructor signature only.
- `ModularScreen`'s package-private constructors (`ModularScreen(String owner)` and the private
  `mainPanelCreator`-based one) mean the exact "when does `buildUI` run relative to subclass field init" timing
  is easiest to get wrong; the doc above states it as observed from source (`buildUI` called from inside the
  `super(...)` chain), matching the null-guard pattern seen in `TestGui.buildUI`.
- `ClientScreenHandler` and `NEAAnimationHandler` are `@ApiStatus.Internal`/effectively-internal but are public
  classes in the in-scope package list, so they're documented at lower depth (no per-line lifecycle table)
  since a mod author is not expected to subclass or deeply integrate with them.
- `RecipeViewerState` (enum backing `RecipeViewerSettingsImpl`) lives in `com.cleanroommc.modularui.integration.recipeviewer`,
  outside scope, so its `test(ModularScreen)` semantics are described only via the surrounding doc comments.
