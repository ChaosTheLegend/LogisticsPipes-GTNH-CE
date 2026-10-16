# `core` Package

The `core` package is ModularUI2's load-time patching layer. It contains the FML coremod
entry point, an ASM class transformer, and a set of SpongePowered Mixin classes that patch
vanilla Minecraft, Forge, NEI, and Thaumcraft classes so the rest of the library can hook into
otherwise-inaccessible behavior (private fields, package-private methods, rendering/input
call sites, networking internals, etc.). Everything here executes during game/coremod
loading, not at "normal" mod runtime.

**Mod authors consuming ModularUI2 do not interact with anything in this package directly.**
No public API calls into `Mixin`/`Accessor`/`Invoker` interfaces or the mixin-plugin classes;
they exist purely so the library's own code (elsewhere in `com.cleanroommc.modularui`) can
call otherwise-inaccessible vanilla members through the generated accessor/invoker methods.
The only members here that a consumer might conceivably need to know about are the coremod
entry point (`ModularUICore`) and the ASM transformer (`ModularUITransformer`), covered below,
because they explain *why* ModularUI2 must be loaded as a coremod (via a `MANIFEST.MF`
`FMLCorePlugin` entry) rather than as a plain mod jar.

---

## 1. Framework glue: coremod entry points

### `com.cleanroommc.modularui.core.ModularUICore`

Implements `cpw.mods.fml.relauncher.IFMLLoadingPlugin` (FML's coremod interface) and
`com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader` (GTNH's helper interface for registering an
"early" Mixin config, i.e. one applied before FML has fully bootstrapped mods). This is the
class referenced from the mod jar's `MANIFEST.MF` as `FMLCorePlugin`; FML instantiates it
very early during game launch.

| Method | Signature | Purpose / gotchas |
|---|---|---|
| `isDevEnv()` | `static boolean isDevEnv()` | Returns whether the game is running in a deobfuscated development environment. Backed by the static field set in `injectData`. **Gotcha:** the backing field is `null` until FML calls `injectData`, so calling this before that point (or reading it from a `static` initializer that runs earlier) would NPE on unboxing. `PacketByteBufferVisitor` relies on this being populated first (see below). |
| `getMixinConfig()` | `String getMixinConfig()` | Returns `"mixins.modularui2.early.json"` — the Mixin config file (on the classpath) listing the early mixin set for this loading plugin. |
| `getMixins(Set<String> loadedCoreMods)` | `List<String> getMixins(Set<String> loadedCoreMods)` | Delegates to `IMixins.getEarlyMixins(Mixins.class, loadedCoreMods)` (GTNH mixins helper), which walks the `Mixins` enum and returns the mixin class names applicable given which other coremods are already loaded. |
| `getASMTransformerClass()` | `String[] getASMTransformerClass()` | Returns `{"com.cleanroommc.modularui.core.ModularUITransformer"}`, registering `ModularUITransformer` as an `IClassTransformer` with LaunchWrapper. |
| `getModContainerClass()` / `getSetupClass()` / `getAccessTransformerClass()` | — | All return `null`; ModularUI2 does not use a separate mod-container class, an FML setup class, or an access-transformer (`.cfg`) file. |
| `injectData(Map<String, Object> data)` | `void injectData(Map<String, Object> data)` | FML callback invoked with loader environment data. Sets the static `isDevEnv` field to `!(boolean) data.get("runtimeDeobfuscationEnabled")`. |

Also exposes `public static final Logger LOGGER` (Log4j logger named `"modularui2"`), used
throughout the `core` package (e.g. by `ModularUITransformer` and `PacketByteBufferVisitor`)
for coremod/ASM-stage logging, since normal mod logging isn't necessarily set up yet at this
stage of loading.

### `com.cleanroommc.modularui.core.ModularUITransformer`

Implements `net.minecraft.launchwrapper.IClassTransformer`, the LaunchWrapper SPI for
per-class bytecode transformation, registered via `ModularUICore.getASMTransformerClass()`.

| Method | Signature | Purpose |
|---|---|---|
| `transform` | `byte[] transform(String name, String transformedName, byte[] basicClass)` | Called by LaunchWrapper for (in principle) every class loaded. If `transformedName` equals `PacketByteBufferVisitor.PACKET_BUFFER_CLASS` (`"net.minecraft.network.PacketBuffer"`), it runs the class bytes through a `ClassReader` → `PacketByteBufferVisitor` → `ClassWriter` pipeline and returns the rewritten bytecode, logging `"Applied {} ASM from ModularUI"` at info level. For every other class it returns `basicClass` unchanged (no-op passthrough). |

This is plain ASM bytecode visiting (not a Mixin), used for the one case where ModularUI2
needs a small, targeted bytecode edit on `PacketBuffer` rather than a full Mixin — see
`PacketByteBufferVisitor` below for what it actually changes.

---

## 2. Mixin plugin / config classes (`core.mixinplugin`)

These three classes configure *how and when* the actual mixins (section 3) get applied; they
use the GTNH `gtnhmixins` library's builder API rather than implementing SpongePowered's
`IMixinConfigPlugin` directly.

### `Mixins` (enum, implements `com.gtnewhorizon.gtnhmixins.builders.IMixins`)

The central registry of mixin groups. Each enum constant builds a `MixinBuilder` describing:
which mixin classes belong to the group, whether they are client-only (`addClientMixins`) or
apply on both sides (`addCommonMixins`), which `Phase` (`EARLY` or `LATE`) they load in, and
optional mod gating (`addRequiredMod` / `addExcludedMod`) sourced from `TargetedMod`.

| Enum constant | Phase | Client-only mixins | Common mixins | Mod gating |
|---|---|---|---|---|
| `MINECRAFT` | `EARLY` | `forge.ForgeHooksClientMixin`, `minecraft.FontRendererAccessor`, `minecraft.GuiAccessor`, `minecraft.GuiButtonMixin`, `minecraft.GuiContainerAccessor`, `minecraft.GuiContainerMixin`, `minecraft.GuiScreenAccessor`, `minecraft.GuiScreenMixin`, `minecraft.MinecraftMixin`, `minecraft.SimpleResourceAccessor` | `minecraft.ContainerAccessor`, `minecraft.EntityAccessor`, `minecraft.EntityPlayerMPMixin`, `minecraft.InventoryCraftingAccessor`, `forge.SimpleNetworkWrapperMixin` | none — always applied |
| `THAUMCRAFT` | `LATE` | `thaumcraft.ClientTickEventsFMLMixin` | — | Requires `TargetedMod.THAUMCRAFT`; excludes `TargetedMod.SALISARCANA` (comment in source: "salis arcana implements the same mixin so ours fails") |
| `NEI` | `LATE` | — | `nei.RecipeInfoMixin` | Requires `TargetedMod.NEI` |

So the always-on early mixin set patches core Minecraft/Forge classes; the Thaumcraft and NEI
integration mixins only get applied at all if those mods (Thaumcraft / NotEnoughItems,
respectively) are present in the modpack, and the Thaumcraft one is additionally skipped if
the mod `salisarcana` is present, since that mod ships a conflicting mixin against the same
target.

### `ModularUILateMixinLoader` (implements `com.gtnewhorizon.gtnhmixins.ILateMixinLoader`, annotated `@LateMixin`)

Registers the "late" mixin config — mixins applied after early mod/coremod discovery, needed
because the Thaumcraft/NEI mixins target classes belonging to those mods, which may not be on
the classpath (or resolvable) as early as the vanilla/Forge mixins are.

- `getMixinConfig()` returns `"mixins.modularui2.late.json"`.
- `getMixins(Set<String> loadedMods)` delegates to `IMixins.getLateMixins(Mixins.class, loadedMods)`, which — analogous to the early path — filters the `Mixins` enum entries down to the `LATE`-phase ones whose required/excluded mods match what's actually loaded (`THAUMCRAFT`, `NEI`).

### `TargetedMod` (enum, implements `com.gtnewhorizon.gtnhmixins.builders.ITargetMod`)

Simple mod-id registry used by `Mixins` for the `addRequiredMod`/`addExcludedMod` gating above.

| Constant | Mod ID |
|---|---|
| `SALISARCANA` | `"salisarcana"` |
| `THAUMCRAFT` | `"Thaumcraft"` |
| `NEI` | `"NotEnoughItems"` |

**Inferred:** these mod IDs correspond to the mods' actual `modid` values as registered with
Forge; not independently verified against those mods' source.

---

## 3. Early mixins — `core.mixins.early.forge`

| Class | Target class | What it changes/exposes |
|---|---|---|
| `ForgeHooksClientMixin` | `net.minecraftforge.client.ForgeHooksClient` | `@Accessor` interface exposing a **static** setter `setStencilBits(int)` for the private static stencil-bits field on `ForgeHooksClient`, letting ModularUI2 code write to it directly. |
| `SimpleNetworkWrapperMixin` | `cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper` | Uses `@Overwrite` (full method replacement) on `addServerHandlerAfter` and `addClientHandlerAfter` so that **more than one handler of the same message type** can be registered on a channel pipeline (backported from the 1.12 behavior, per the doc comment). Also `@Shadow`s the private `getHandlerWrapper` method and reflectively calls netty's `DefaultChannelPipeline#generateName` (via reflection, since it's not normally accessible) to generate unique handler names when inserting into the pipeline. |

---

## 4. Early mixins — `core.mixins.early.minecraft`

| Class | Target class | What it changes/exposes |
|---|---|---|
| `ContainerAccessor` | `net.minecraft.inventory.Container` | `@Accessor` exposing the private `dragEvent` field (`field_94536_g`) as `getDragEvent()`. |
| `EntityAccessor` | `net.minecraft.entity.Entity` | `@Accessor` exposing a setter `setFirstUpdate(boolean)` for the private `firstUpdate` field. |
| `EntityPlayerMPMixin` | `net.minecraft.entity.player.EntityPlayerMP` | `@Inject`s into `closeContainer` right after the vanilla call to `Container.onContainerClosed`. If the player's open container is a `ModularContainer`, calls `onModularContainerClosed()` on it. Doc comment: "replicates the container closed event listener from 1.12" (a callback that doesn't exist in vanilla 1.7.10). |
| `FontRendererAccessor` | `net.minecraft.client.gui.FontRenderer` | `@Invoker` exposing the private `sizeStringToWidth` method as `invokeSizeStringToWidth(String, int)`. |
| `GuiAccessor` | `net.minecraft.client.gui.Gui` | `@Accessor` get/set pair for the protected/private `zLevel` field. |
| `GuiButtonMixin` | `net.minecraft.client.gui.GuiButton` | `@Redirect`s the internal call to `getHoverState(boolean)` inside `drawButton`. Forces the hovered flag to `false` when a ModularUI overlay is currently being hovered (`OverlayStack.isHoveringOverlay()`), fixing vanilla buttons rendering as "hovered" while an overlay widget sits on top of them. |
| `GuiContainerAccessor` | `net.minecraft.client.gui.inventory.GuiContainer` | Large `@Accessor`/`@Invoker` interface exposing most of `GuiContainer`'s private drag/slot/tooltip state: `xSize`/`ySize`, `guiLeft`/`guiTop`, hovered slot (`theSlot`), clicked slot, dragged stack, right-click flag, drag-splitting fields and slot set, returning-stack fields and timing, plus invokers for `updateDragSplitting`, `drawGuiContainerForegroundLayer`, and `drawGuiContainerBackgroundLayer`. |
| `GuiContainerMixin` | `net.minecraft.client.gui.inventory.GuiContainer` | Implements `IClickableGuiContainer` to track a `modularUI$clickedSlot` field. `@Inject`s at the head of `getSlotAtPosition` (cancellable) to return the tracked clicked slot if set, or the current hovered slot if the screen is an `IMuiScreen`. `@ModifyVariable`s a local boolean in `mouseClicked` (forces it `false`) to fix a vanilla bug where clicking a slot outside the main panel area tosses the held item. `@Redirect`s a field read of `Slot.inventory` in `mouseMovedOrUp` so that `ModularSlot`s belonging to a `SlotGroup` compare against the slot group's "dummy" inventory instead of their real one. |
| `GuiScreenAccessor` | `net.minecraft.client.gui.GuiScreen` | `@Accessor`/`@Invoker` interface exposing touch value, event button, last-mouse-event timestamp, the static `itemRender` (`RenderItem`), the screen's `FontRenderer`, button list (get/set), label list, plus invokers for `keyTyped`, `mouseClicked`, `mouseMovedOrUp` (as `invokeMouseReleased`), and `mouseClickMove`. |
| `GuiScreenMixin` | `net.minecraft.client.gui.GuiScreen` | `@Redirect`s the calls to `handleMouseInput()` and `handleKeyboardInput()` inside `handleInput`, wrapping each in a Forge `EVENT_BUS.post(...Pre/...Post)` pair (`MouseInputEvent.Pre/Post`, `KeyboardInputEvent.Pre/Post`) so ModularUI2's own input-event system gets pre/post hooks around vanilla's input handling, including cancellation support (returning from the redirect early if `Pre` is cancelled). Contains a large commented-out block (currently disabled, marked `TODO ... doesnt work when NEI is installed`) intended to hook `renderToolTip`/`drawHoveringText` for a rich-tooltip replacement feature. |
| `InventoryCraftingAccessor` | `net.minecraft.inventory.InventoryCrafting` | `@Accessor` exposing the private crafting-matrix `stackList` array and the `eventHandler` (`Container`). |
| `MinecraftMixin` | `net.minecraft.client.Minecraft` | `@Inject`s into `runGameLoop`, right after the 3rd (`ordinal = 2`) `Profiler.startSection` call. Drives `ModularUI.proxy`'s 60fps timer (`getTimer60Fps().updateTimer()`) and calls `ClientScreenHandler.onFrameUpdate()` up to 20 times per game tick, i.e. this is what gives ModularUI2 screens a frame-rate-independent update tick decoupled from the 20 TPS game loop. |
| `SimpleResourceAccessor` | `net.minecraft.client.resources.SimpleResource` | `@Accessor` exposing the private `srResourceLocation` field as `getResourceLocation()`. |

---

## 5. `core.mixins.KeyBindAccess`

`src/main/java/com/cleanroommc/modularui/core/mixins/KeyBindAccess.java` currently exists as
an **empty file (0 bytes)** — no package declaration, class, or content of any kind. It is not
referenced from `Mixins.java` and does not currently contribute to the mixin set. Likely a
placeholder/leftover from work in progress; nothing to document until it gains content.

---

## 6. Late mixins — `core.mixins.late.nei` and `core.mixins.late.thaumcraft`

These only apply when the corresponding third-party mod is present (see the `Mixins` enum
gating in section 2).

| Class | Target class | Requires mod | What it changes/exposes |
|---|---|---|---|
| `RecipeInfoMixin` | `codechicken.nei.recipe.RecipeInfo` | NotEnoughItems | Three `@Inject(at = @At("HEAD"), cancellable = true)` hooks — `hasOverlayHandler`, `getStackPositioner`, `getOverlayHandler` — that short-circuit NEI's recipe-transfer/overlay lookup when the current `GuiContainer` is an `IMuiScreen` backed by a `ModularContainer` implementing `INEIRecipeTransfer`. Lets ModularUI2 screens supply their own recipe-transfer "idents", stack positioner, and overlay handler to NEI instead of NEI's default lookup. |
| `ClientTickEventsFMLMixin` | `thaumcraft.client.lib.ClientTickEventsFML` | Thaumcraft (and not `salisarcana`) | `@Redirect`s the internal call to `isMouseOverSlot(Slot, int, int, int, int)` inside `renderAspectsInGui`, replacing it with a check against ModularUI2's own hovered-slot tracking (via `GuiContainerAccessor.getHoveredSlot()` on the current screen). Ensures Thaumcraft's aspect-hover rendering correctly detects hovered slots inside ModularUI2 GUIs. |

---

## 7. `core.visitor.PacketByteBufferVisitor`

```java
public class PacketByteBufferVisitor extends ClassVisitor implements Opcodes
```

Not a Mixin — a raw ASM `ClassVisitor`/`MethodVisitor` pair invoked by `ModularUITransformer`
(section 1) specifically against `net.minecraft.network.PacketBuffer`
(`PACKET_BUFFER_CLASS` constant). Its doc comment states the intent plainly: *"Write item
stack with var int stack size instead of byte stack size."*

- `visitMethod` matches the obfuscated/deobfuscated names of `writeItemStackToBuffer`
  (`"a"` obfuscated) and `readItemStackFromBuffer` (`"c"` obfuscated) — the exact name/desc
  used depends on `ModularUICore.isDevEnv()`, with a code comment noting that due to a Mixin
  tooling bug, obfuscated (not SRG) names are supplied at this stage even when a higher
  `SortingIndex` is set.
- For matching methods, it wraps the method visitor in the private inner class
  `ReadWriteItemStackVisitor`, which rewrites bytecode-level calls:
  - `writeByte` (which vanilla uses to write the stack's byte-sized item count) is rewritten
    to call `writeVarIntToBuffer`/`func_150787_b` (an `int`-returning-`void` method) instead,
    and the following `POP` bytecode instruction is dropped (`skipPop`) since
    `writeVarIntToBuffer` returns `void` where `writeByte` returned `ByteBuf`.
  - `readByte` is rewritten to call `readVarIntFromBuffer`/`func_150792_a` instead.
- Net effect: item stack `PacketBuffer` (de)serialization uses a variable-length int for stack
  size instead of vanilla's single byte, raising the effective max stack size representable
  over the network above 127/255 (**Inferred**: the write/read method rewiring is directly
  confirmed by reading the bytecode-visitor logic above; the specific numeric byte-vs-varint
  size-limit consequence is inferred from what "byte size" vs "var int size" implies, not from
  a stated limit in the code).

There is no consumer-facing public API surface here beyond the `PACKET_BUFFER_CLASS` constant
used by `ModularUITransformer`; mod authors never construct or call into this class directly.
