# `integration` Package

The `integration` package bridges ModularUI2 to third-party recipe-viewer mods (NEI, and
JEI/EMI-shaped "recipe viewer" mods in general). It is **entirely optional/soft-dependency
code** — nothing in the rest of the library requires these mods to be present:

- `ModularUI.DEPENDENCIES` in `com.cleanroommc.modularui.ModularUI` declares
  `after:NotEnoughItems@[2.3.27-GTNH,);` — an `after:` (load-order only) dependency, not
  `required-after:`, meaning NEI is not mandatory for ModularUI2 to load.
- The one Mixin that wires NEI's `RecipeInfo` internals to ModularUI2
  (`com.cleanroommc.modularui.core.mixins.late.nei.RecipeInfoMixin`) is registered in
  `com.cleanroommc.modularui.core.mixinplugin.Mixins`:

  ```java
  NEI(new MixinBuilder()
          .addCommonMixins("nei.RecipeInfoMixin")
          .setPhase(Phase.LATE)
          .addRequiredMod(TargetedMod.NEI));
  ```

  `.addRequiredMod(TargetedMod.NEI)` means this mixin — and therefore the entire
  `integration.nei` wiring path — is only applied if NotEnoughItems is actually loaded.
- `integration.recipeviewer.RecipeViewerRecipeTransferHandler` (JEI-style recipe transfer) has
  its **entire body commented out** in source (see below) — it is currently dead/disabled code,
  presumably retained from a port of a JEI-based branch. Treat it as non-functional in the
  current (1.7.10 + NEI) build.

Mod authors who want their own recipe handler to interoperate with ModularUI2's NEI/recipe-viewer
support implement one or more of: `INEIRecipeTransfer` (NEI recipe transfer/overlay), and, on any
widget, `RecipeViewerIngredientProvider` (recipe-lookup-from-widget) and/or
`RecipeViewerGhostIngredientSlot` (drag-and-drop ghost slots). Everything else in this package is
internal glue that ModularUI2 registers with NEI itself; a mod author does not instantiate it
directly.

---

## 1. `integration.nei` — NotEnoughItems integration

### `com.cleanroommc.modularui.integration.nei.INEIRecipeTransfer<G extends net.minecraft.client.gui.inventory.GuiContainer>`

The extension point for NEI recipe transfer/overlay support. **Implement this on your
`ModularContainer`** (the interface is checked via `instanceof` against
`gui.inventorySlots`, i.e. the container, not the screen/GUI class). `G` is the concrete
`GuiContainer` subtype (which must also implement `IMuiScreen`) used by the callback methods.

```java
public interface INEIRecipeTransfer<G extends GuiContainer> {
    String[] getIdents();
    default void overlayRecipe(G gui, IRecipeHandler recipe, int recipeIndex, boolean maxTransfer);
    int transferRecipe(G gui, IRecipeHandler recipe, int recipeIndex, int multiplier);
    default boolean canFillCraftingGrid(G gui, IRecipeHandler recipe, int recipeIndex);
    default boolean craft(G gui, IRecipeHandler recipe, int recipeIndex, int multiplier);
    default boolean canCraft(G gui, IRecipeHandler recipe, int recipeIndex);
    default List<GuiOverlayButton.ItemOverlayState> presenceOverlay(G gui, IRecipeHandler recipe, int recipeIndex);
    default ArrayList<PositionedStack> positionStacks(G gui, ArrayList<PositionedStack> stacks);
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getIdents()` | — | `String[]` | NEI recipe-handler identifier strings (`IRecipeHandler#getRecipeName()`/ident) this transfer implementation supports. Used by `RecipeInfoMixin`/`ModularUIGuiContainerStackPositioner.of` to decide whether this container should handle a given recipe ident. |
| `overlayRecipe(gui, recipe, recipeIndex, maxTransfer)` | recipe handler, recipe index, whether shift is held | `void` (default) | Default impl calls `transferRecipe(gui, recipe, recipeIndex, maxTransfer ? Integer.MAX_VALUE : 1)`. Override only if overlay behavior must differ from transfer. |
| `transferRecipe(gui, recipe, recipeIndex, multiplier)` | recipe handler, index, craft multiplier | `int` | **Must implement.** Perform the actual item-shuffling to fill the crafting grid/inventory from the recipe's ingredients, `multiplier` times. Return value is handler-defined (NEI's `IOverlayHandler` contract). |
| `canFillCraftingGrid(gui, recipe, recipeIndex)` | — | `boolean`, default `true` | Whether the overlay ("?" fill) button should be shown at all. |
| `craft(gui, recipe, recipeIndex, multiplier)` | — | `boolean`, default `false` | Hook for one-click "craft" (not just transfer) support; default no-op/false. |
| `canCraft(gui, recipe, recipeIndex)` | — | `boolean`, default `false` | Whether the craft button should be shown; default false. |
| `presenceOverlay(gui, recipe, recipeIndex)` | — | `List<GuiOverlayButton.ItemOverlayState>` | Default implementation scans `gui.inventorySlots.inventorySlots` for player-accessible stacks (`isItemValid` + `canTakeStack`) and marks each recipe ingredient `PositionedStack` as present/absent, consuming counted copies as it goes (a working default "do I have the ingredients" overlay). Override if slot/inventory layout needs different logic. |
| `positionStacks(gui, stacks)` | `ArrayList<PositionedStack>` from NEI | `ArrayList<PositionedStack>`, default identity | Lets the implementation reposition NEI's ingredient-stack overlay to match custom slot positions; default is a no-op passthrough. |

**Gotchas:**
- All methods are client-side only in practice (NEI recipe GUIs are client-only), though nothing
  in the interface itself enforces that with `@SideOnly`.
- `presenceOverlay`'s default implementation mutates copies (`stack.getStack().copy()`) of
  inventory stacks, not the live stacks, so it is safe to call repeatedly for render-time
  overlays.

**Example (constructed, not from repo)** — no class in `test/` implements this interface, since
`test/` is 1.7.10 demo code and doesn't ship NEI recipe handlers. A minimal container hookup:

```java
package com.example.mymod;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import com.cleanroommc.modularui.integration.nei.INEIRecipeTransfer;
import com.cleanroommc.modularui.screen.ModularContainer;
import net.minecraft.client.gui.inventory.GuiContainer;

public class MyMachineContainer extends ModularContainer implements INEIRecipeTransfer<GuiContainer> {

    @Override
    public String[] getIdents() {
        return new String[] { "myMachineRecipe" };
    }

    @Override
    public int transferRecipe(GuiContainer gui, IRecipeHandler recipe, int recipeIndex, int multiplier) {
        // move recipe.getIngredientStacks(recipeIndex) into this.getSyncManager()'s slots
        return 0;
    }
}
```

---

### `com.cleanroommc.modularui.integration.nei.ModularUIContainerInputHandler implements codechicken.nei.guihook.IContainerInputHandler`

Forwards NEI's per-container keyboard/mouse input hook callbacks into ModularUI2's own input
pipeline. Registered once, globally, by `NEIModularUIConfig.loadConfig()` via
`GuiContainerManager.addInputHandler(...)`.

```java
public class ModularUIContainerInputHandler implements IContainerInputHandler {
    public boolean keyTyped(GuiContainer gui, char keyChar, int keyCode);
    public void onKeyTyped(GuiContainer gui, char keyChar, int keyID);
    public boolean lastKeyTyped(GuiContainer gui, char keyChar, int keyID);
    public boolean mouseClicked(GuiContainer gui, int mousex, int mousey, int button);
    public void onMouseClicked(GuiContainer gui, int mousex, int mousey, int button);
    public void onMouseUp(GuiContainer gui, int mousex, int mousey, int button);
    public boolean mouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled);
    public void onMouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled);
    public void onMouseDragged(GuiContainer gui, int mousex, int mousey, int button, long heldTime);
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `keyTyped(gui, keyChar, keyCode)` | key event | `boolean` | The only non-trivial method: delegates to `ClientScreenHandler.handleKeyboardInput(ClientScreenHandler.getMuiScreen(), gui)`, wrapping the checked `IOException` into a `RuntimeException`. Source comment: *"This input handler must be after LayoutManager but not in `lastKeyTyped` because it cannot handle esc key"* — i.e. NEI hook-ordering constraint, not something a caller controls. |
| all others | — | `false` / `void` no-ops | Not used by ModularUI2; present only to satisfy the `IContainerInputHandler` interface. |

Internal; not instantiated by mod authors — only ever constructed by `NEIModularUIConfig`.

---

### `com.cleanroommc.modularui.integration.nei.ModularUIContainerObjectHandler implements codechicken.nei.guihook.IContainerObjectHandler`

Tells NEI which `ItemStack` is "under the mouse" for ModularUI screens, and whether NEI's own
tooltip should be suppressed, so NEI's item-lookup ("look up recipes/usages") features work for
ModularUI widgets. Registered once by `NEIModularUIConfig.loadConfig()`.

```java
public class ModularUIContainerObjectHandler implements IContainerObjectHandler {
    public void guiTick(GuiContainer gui);
    public void refresh(GuiContainer gui);
    public void load(GuiContainer gui);
    public ItemStack getStackUnderMouse(GuiContainer gui, int mousex, int mousey);
    public boolean objectUnderMouse(GuiContainer gui, int mousex, int mousey);
    public boolean shouldShowTooltip(GuiContainer gui);
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getStackUnderMouse(gui, mousex, mousey)` | mouse position | `ItemStack` or `null` | If `gui instanceof IMuiScreen`, looks up `muiScreen.getScreen().getContext().getHovered()`; if the hovered `IGuiElement` implements `RecipeViewerIngredientProvider`, returns `ingredientProvider.getStackForRecipeViewer()`. This is the mechanism by which **any widget implementing `RecipeViewerIngredientProvider` becomes NEI-lookup-aware** without further registration. |
| `shouldShowTooltip(gui)` | — | `boolean` | Returns `false` (suppress NEI's tooltip) only if the GUI is a `IMuiScreen` **and** `getContext().hasDraggable()` is true (i.e. the user is mid-drag) — otherwise `true`. Prevents NEI's tooltip from fighting with ModularUI2's own tooltip/drag rendering. |
| `guiTick` / `refresh` / `load` / `objectUnderMouse` | — | no-ops / `false` | Unused hooks. |

---

### `com.cleanroommc.modularui.integration.nei.ModularUIGuiContainerStackOverlay implements codechicken.nei.api.IOverlayHandler`

The NEI `IOverlayHandler` singleton (`NEIModularUIConfig.overlayHandler`) that adapts NEI's
per-recipe overlay callbacks onto whatever `INEIRecipeTransfer` the current container implements.
This is the class NEI actually calls into for "transfer recipe" clicks; it in turn delegates to
the container's `INEIRecipeTransfer` methods.

```java
public class ModularUIGuiContainerStackOverlay implements IOverlayHandler {
    public void overlayRecipe(GuiContainer gui, IRecipeHandler recipe, int recipeIndex, boolean maxTransfer);
    public int transferRecipe(GuiContainer gui, IRecipeHandler recipe, int recipeIndex, int multiplier);
    public boolean canFillCraftingGrid(GuiContainer gui, IRecipeHandler recipe, int recipeIndex);
    public boolean craft(GuiContainer gui, IRecipeHandler recipe, int recipeIndex, int multiplier);
    public boolean canCraft(GuiContainer gui, IRecipeHandler recipe, int recipeIndex);
    public List<GuiOverlayButton.ItemOverlayState> presenceOverlay(GuiContainer gui, IRecipeHandler recipe, int recipeIndex);
}
```

Every method follows the same pattern via a private generic helper
`doAction(GuiContainer gui, Action<T, G> action)`:

1. Checks `gui instanceof IMuiScreen && gui.inventorySlots instanceof ModularContainer mc && mc instanceof INEIRecipeTransfer<?> tr`.
2. If true, unchecked-casts and runs the given lambda against the container's
   `INEIRecipeTransfer` implementation, returning its result.
3. If false, returns `null` (for `void`/object-returning wrappers) or falls back to
   `IOverlayHandler.super.<method>(...)` (NEI's own default) for primitive-returning methods
   (`transferRecipe`, `canFillCraftingGrid`, `craft`, `canCraft`).

**Gotcha:** the cast `(G) tr` / `(T) action.doAction(...)` is an unchecked generic cast
(`@SuppressWarnings("unchecked")`); safety relies entirely on the `instanceof` check holding, i.e.
on the container's declared `INEIRecipeTransfer<G>` type parameter actually matching its own
concrete `GuiContainer` subtype.

Entirely internal — instantiated once as `NEIModularUIConfig.overlayHandler` and wired to NEI's
`RecipeInfo` via `RecipeInfoMixin.modularui$getOverlayHandler`; never constructed by mod authors.

---

### `com.cleanroommc.modularui.integration.nei.ModularUIGuiContainerStackPositioner<G extends GuiContainer & IMuiScreen> implements codechicken.nei.api.IStackPositioner`

Adapts NEI's ingredient-stack repositioning hook (`IStackPositioner`) onto
`INEIRecipeTransfer#positionStacks`.

```java
public class ModularUIGuiContainerStackPositioner<G extends GuiContainer & IMuiScreen> implements IStackPositioner {
    public static <G extends GuiContainer & IMuiScreen> ModularUIGuiContainerStackPositioner<G> of(GuiContainer gui, String ident);
    public ModularUIGuiContainerStackPositioner(G wrapper, INEIRecipeTransfer<G> recipeTransfer);
    public ArrayList<PositionedStack> positionStacks(ArrayList<PositionedStack> ai);
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `of(gui, ident)` | GUI instance, NEI recipe ident string | `ModularUIGuiContainerStackPositioner<G>` or `null` | Static factory. Returns a new positioner only if `gui instanceof IMuiScreen`, `gui.inventorySlots instanceof ModularContainer mc`, `mc instanceof INEIRecipeTransfer<?> tr`, **and** `ident` is one of `tr.getIdents()`; otherwise `null`. Called from `RecipeInfoMixin.modularui$getStackPositioner`. |
| constructor | `(G wrapper, INEIRecipeTransfer<G> recipeTransfer)` | — | `wrapper` and `recipeTransfer` are stored as `public final` fields; typically not called directly — use `of(...)`. |
| `positionStacks(ai)` | NEI's `ArrayList<PositionedStack>` | `ArrayList<PositionedStack>` | Delegates straight to `recipeTransfer.positionStacks(wrapper, ai)`. |

Internal glue produced by `of(...)` inside `RecipeInfoMixin`; not constructed directly by mod
authors.

---

### `com.cleanroommc.modularui.integration.nei.ModularUINEIGuiHandler extends codechicken.nei.api.INEIGuiAdapter`

Implements NEI's "exclusion zone" mechanism — telling NEI's item panel not to render item slots
in areas ModularUI2 widgets have claimed. Registered once by
`NEIModularUIConfig.loadConfig()` via `API.registerNEIGuiHandler(...)`.

```java
public class ModularUINEIGuiHandler extends INEIGuiAdapter {
    public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int w, int h);
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `hideItemPanelSlot(gui, x, y, w, h)` | candidate item-panel slot rect | `boolean` | Returns `false` immediately if `gui` isn't an `IMuiScreen`, or if `screen.getContext().getRecipeViewerSettings().isRecipeViewerEnabled(screen)` is false (recipe viewer disabled for this screen — see `RecipeViewerState`/`RecipeViewerSettings`). Otherwise sets the shared scratch `Area.SHARED` to `(x, y, w, h)` and returns `true` (hide this slot) if it intersects **any** `Rectangle` in `screen.getContext().getRecipeViewerSettings().getAllRecipeViewerExclusionAreas()`. |

**Gotcha:** reuses the static/shared `Area.SHARED` scratch object rather than allocating — not
thread-safe if called concurrently, but NEI GUI rendering is single-threaded on the client so this
is not an issue in practice.

---

### `com.cleanroommc.modularui.integration.nei.NEIModularUIConfig implements codechicken.nei.api.IConfigureNEI`

The NEI plugin entry point. NEI discovers classes implementing `IConfigureNEI` (typically via its
own mod-scanning) and calls `loadConfig()` once during NEI setup — this is where every other
`integration.nei` handler gets registered with NEI.

```java
public class NEIModularUIConfig implements IConfigureNEI {
    public static final ModularUIGuiContainerStackOverlay overlayHandler = new ModularUIGuiContainerStackOverlay();
    public void loadConfig();
    public String getName();
    public String getVersion();
}
```

| Method | Returns | Notes |
|---|---|---|
| `loadConfig()` | `void` | Calls `GuiContainerManager.addInputHandler(new ModularUIContainerInputHandler())`, `GuiContainerManager.addObjectHandler(new ModularUIContainerObjectHandler())`, and `API.registerNEIGuiHandler(new ModularUINEIGuiHandler())`. This is the single place all three handlers get wired into NEI. |
| `getName()` | `"ModularUI NEI integration"` | NEI plugin display name. |
| `getVersion()` | `""` | Empty string — no separate versioning for the NEI plugin shim. |

`overlayHandler` is the same singleton wired into NEI's `RecipeInfo` via `RecipeInfoMixin`
(`modularui$getOverlayHandler`), so all recipe-transfer traffic ultimately flows through this one
instance regardless of how many `INEIRecipeTransfer` containers exist.

**Inferred:** only reachable/constructed at all when NEI is loaded and scans for `IConfigureNEI`
implementors — consistent with the `RecipeInfoMixin` / `Mixins.NEI` mod-gating described above.

---

### `com.cleanroommc.modularui.integration.nei.NEIUtil`

Static helpers for reading/clearing NEI's own item-panel drag state, used to implement
ghost-ingredient drag-and-drop from NEI's item panel into ModularUI2 widgets.

```java
public class NEIUtil {
    public static ItemStack getNEIDragAndDropTarget(ModularGuiContext context);
    public static void stopNEIGhostDrag();
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getNEIDragAndDropTarget(context)` | current `ModularGuiContext` | `ItemStack` or `null` | Returns `null` if `context.getScreen().isOverlay()` or recipe viewer is disabled for that screen (`!isRecipeViewerEnabled`). Otherwise returns `ItemPanels.itemPanel.draggedStack` if non-null, else `ItemPanels.bookmarkPanel.draggedStack` if non-null, else `null`. This is how ModularUI2 code (e.g. a `RecipeViewerGhostIngredientSlot` widget's mouse handling) discovers "what item is NEI currently dragging". |
| `stopNEIGhostDrag()` | — | `void` | Comment: *"Replicate behavior of PanelWidget#handleDraggedClick"*. Clears `ItemPanels.itemPanel.draggedStack` / `ItemPanels.bookmarkPanel.draggedStack` back to `null`, but **only** if their current `stackSize == 0` — i.e. only cleans up an already-emptied drag stack, it does not forcibly cancel an in-progress drag. |

**Gotcha:** both methods read/write NEI's global static drag state directly (`ItemPanels.*`), so
they are inherently client-side-only and not safe to call off the render/input thread.

---

## 2. `integration.recipeviewer` — generic recipe-viewer (JEI/EMI-shaped) hooks

Unlike `integration.nei`, these are plain interfaces/enum with **no NEI or JEI import at the
top level except where noted** — they're the mod-author-facing contract that both the NEI layer
above and (if re-enabled) a JEI/EMI layer would target.

### `com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerIngredientProvider`

Lets a widget expose "the ingredient here" to a recipe viewer for item lookup (e.g. right-click
"show recipes/usages"). Implement on any `IWidget`.

```java
public interface RecipeViewerIngredientProvider {
    @Nullable ItemStack getStackForRecipeViewer();
}
```

| Method | Returns | Notes |
|---|---|---|
| `getStackForRecipeViewer()` | `ItemStack` or `null` | Called by `ModularUIContainerObjectHandler.getStackUnderMouse` when the currently-hovered `IGuiElement` implements this interface — no separate registration needed, implementing the interface is sufficient. |

**Example (constructed, not from repo)** — no widget in `test/` implements this; ModularUI2's own
built-in item-slot widgets (e.g. `com.cleanroommc.modularui.widgets.slot.ItemSlot`, outside the
scope of this doc) are the real implementors. Sketch:

```java
public class MyIngredientWidget extends Widget<MyIngredientWidget> implements RecipeViewerIngredientProvider {
    @Override
    public ItemStack getStackForRecipeViewer() {
        return this.displayedStack; // whatever ItemStack this widget currently shows
    }
}
```

---

### `com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerGhostIngredientSlot<I>`

Lets a widget accept a recipe-viewer drag-and-drop ("ghost ingredient") drop, e.g. dragging an
item from NEI's item panel onto a machine's phantom/filter slot. Implement on any `IWidget`. The
type parameter `I` is unused within the interface body itself; per the Javadoc it exists "for
parity with 1.12" (where the ingredient type is generic, e.g. items/fluids).

```java
public interface RecipeViewerGhostIngredientSlot<I> {
    boolean handleDragAndDrop(@NotNull ItemStack draggedStack, int button);
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `handleDragAndDrop(draggedStack, button)` | dragged stack, mouse button (`0`=left, `1`=right) | `boolean` (true = handled) | Javadoc: *"The held stack will be deleted if draggedStack.stackSize == 0"* — implementations that fully consume the dragged stack should set `stackSize` to `0` rather than expecting the caller to remove it. |

**Inferred:** paired at the NEI level with `NEIUtil.getNEIDragAndDropTarget` /
`NEIUtil.stopNEIGhostDrag` — a widget's mouse-drag handling code would call
`getNEIDragAndDropTarget(context)` to discover the dragged stack, invoke
`handleDragAndDrop(stack, button)`, and then `stopNEIGhostDrag()` to let NEI clear its drag state
once the stack is emptied. No class in this repo wires that sequence together in one place (not
found via search), so this call order is inferred from the two APIs' doc comments, not observed
directly.

**Example (constructed, not from repo):**

```java
public class MyPhantomSlotWidget extends Widget<MyPhantomSlotWidget> implements RecipeViewerGhostIngredientSlot<ItemStack> {
    @Override
    public boolean handleDragAndDrop(ItemStack draggedStack, int button) {
        this.setDisplayStack(draggedStack.copy());
        draggedStack.stackSize = 0; // let NEI's ghost-drag cleanup delete the source stack
        return true;
    }
}
```

---

### `com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerRecipeTransferHandler`

**Disabled/dead code.** The entire interface body (package line aside) is wrapped in a `/* ... */`
block comment in source, including the `import mezz.jei.api...` lines — this is a JEI
(`mezz.jei`) recipe-transfer contract, `@ApiStatus.Experimental`, currently compiled out entirely
(it isn't even a valid interface as committed; the whole declaration is commented text). Per its
(commented) Javadoc it was meant to be implemented on `ModularScreen` directly with "no further
registration needed", mirroring `INEIRecipeTransfer` but for a JEI-shaped recipe layout API:

```java
/*
public interface RecipeViewerRecipeTransferHandler {
    IRecipeTransferError transferRecipe(IRecipeLayout recipeLayout, boolean maxTransfer, boolean simulate);
}
*/
```

**Do not treat this as an active extension point** in the current codebase — there is no JEI
dependency, import, or call site anywhere else in `com.cleanroommc.modularui` referencing
`mezz.jei` (this file is the only occurrence, and it's commented out). It likely exists as a
placeholder carried over from a 1.12/JEI branch, kept for future re-enablement.

---

### `com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerState (enum) implements java.util.function.Predicate<ModularScreen>`

Controls whether recipe-viewer support (NEI exclusion zones, ingredient lookup, etc.) is active
for a given `ModularScreen`.

```java
public enum RecipeViewerState implements Predicate<ModularScreen> {
    ENABLED,
    DISABLED,
    DEFAULT;
    @Override public boolean test(ModularScreen screen);
}
```

| Constant | `test(screen)` behavior |
|---|---|
| `ENABLED` | Always `true` — force recipe viewer on regardless of screen type. |
| `DISABLED` | Always `false` — force recipe viewer off. |
| `DEFAULT` | `!screen.isClientOnly()` — recipe viewer is enabled by default only for screens backed by a real (synced) container, disabled for client-only screens (e.g. pure client-side config/overlay GUIs). |

Corresponds directly to `RecipeViewerSettings.enable()/disable()/defaultState()` (in
`com.cleanroommc.modularui.api`, outside this doc's scope) — `isEnabled(screen)` on that interface
is effectively backed by one of these three predicates.

**Example (constructed, not from repo):**

```java
// inside a ModularScreen subclass, e.g. overriding buildUI() setup
getContext().getRecipeViewerSettings().disable(); // force RecipeViewerState.DISABLED-like behavior
```
