# `holoui` package reference

Package: `com.cleanroommc.modularui.holoui`

Renders a `ModularScreen` onto a floating 3D plane in the game world, attached to an invisible entity ("holographic" UI panels, e.g. floating screens in front of a block/player). **The whole package is marked `@ApiStatus.Experimental` and every class carries a "Highly experimental" javadoc.** As of the current code, the real-world usage in `test/TestEventHandler.java` is commented out with the note `// todo: fix ScreenEntityRender / TestGui().` — treat this package as not fully working yet.

`HoloUI` is the main entry point end users touch; the other four classes are supporting plumbing (entity, plane math, renderer, orientation enum).

---

## `com.cleanroommc.modularui.holoui.HoloUI`

```java
@ApiStatus.Experimental
public class HoloUI
```

Static entry point and fluent builder for spawning a `HoloScreenEntity` that displays a `ModularScreen` in the world.

### Static members

| Method | Params | Returns | Notes |
|---|---|---|---|
| `registerSyncedHoloUI(ResourceLocation loc, Supplier<ModularScreen> screen)` | registration key, screen factory | - | Stores the supplier in an internal `Map<ResourceLocation, Supplier<ModularScreen>>` (`syncedHolos`). **Inferred:** intended for server-synced holo screens looked up by id, but nothing in this package currently reads `syncedHolos` back out — no getter is exposed here, so the consuming code (if any) lives elsewhere or is not yet implemented. |
| `builder()` | - | new `HoloUI.Builder` | Entry point for constructing and opening a holo screen. |

### `HoloUI.Builder`

Fluent builder; all setters return `this` (`Builder`) for chaining, mutating internal state — not thread-safe, not reusable in parallel.

| Field defaults | Value |
|---|---|
| `x, y, z` | `0, 0, 0` |
| `plane3D` | `new Plane3D()` (480x270 virtual size, scale 1, anchor center, facing +Z) |
| `orientation` | `ScreenOrientation.FIXED` |

| Method | Params | Returns | Notes |
|---|---|---|---|
| `at(double x, double y, double z)` | world position | `this` | Sets the fixed world-space position where the holo entity will spawn. |
| `inFrontOf(EntityPlayer player, double distance, boolean fixed)` | player, distance in blocks, whether orientation should be `FIXED` vs. `TO_PLAYER` | `this` | Computes a position `distance` blocks along the player's look vector, offset by eye height, and calls `at(...)`. Also sets `orientation` based on `fixed`. **Gotcha:** this both positions the screen *and* sets orientation — calling `faceToPlayer()`/`faceTo(...)` after this will override the orientation choice made here. |
| `faceToPlayer()` | - | `this` | Sets `orientation = ScreenOrientation.TO_PLAYER` (screen normal continuously recalculated toward the viewing player each render — see `ScreenEntityRender`). |
| `faceTo(float x, float y, float z)` | normal vector | `this` | Sets `orientation = ScreenOrientation.FIXED` and sets the plane's fixed facing normal via `Plane3D.setNormal` (auto-normalized). |
| `screenAnchor(float x, float y)` | anchor fraction (0-1 typically) | `this` | Delegates to `Plane3D.setAnchor` — controls which point of the virtual screen sits at the entity's world position. |
| `virtualScreenSize(int width, int height)` | pixel dimensions of the virtual GUI | `this` | Delegates to `Plane3D.setSize`. Also drives the `GuiContainerWrapper`'s resolution (`HoloScreenEntity` re-syncs its wrapper's width/height from the plane every tick). |
| `screenScale(float scale)` | scale factor | `this` | Delegates to `Plane3D.setScale` — controls how large the (fixed-size) virtual screen appears in world space. |
| `plane(Plane3D plane)` | a fully custom `Plane3D` | `this` | Replaces the builder's default plane entirely (discards any prior `screenAnchor`/`virtualScreenSize`/`screenScale`/`faceTo` calls made through the builder). Call this *before* the other plane-related setters if you use it, or it will clobber them. |
| `open(ModularScreen screen)` | the screen to display | `void` | Terminal builder method. Builds a `UISettings`, calls `defaultState()` on its recipe-viewer settings, assigns it to `screen.getContext()`, then creates a `HoloScreenEntity` in `Minecraft.getMinecraft().theWorld`, positions it at `(x,y,z)`, calls `setScreen(screen)`, `spawnInWorld()`, and finally `setOrientation(orientation)`. **Client-only**: uses `Minecraft.getMinecraft().theWorld`, so this must run on the client (and, per `HoloScreenEntity.onEntityUpdate`, generally on a remote/client world — spawning server-side would produce an entity with no working GUI wrapper on that side). |

**Gotcha — call order:** `open(screen)` reads `this.x/y/z`, `this.plane3D`, `this.orientation` at the moment it's called; you must call the positioning/orientation/plane setters *before* `open(...)`. There is no validation that `at(...)` (or `inFrontOf`) was called — unset `x/y/z` default to `(0,0,0)`, i.e. world origin.

**Example (adapted from the commented-out code in `test/TestEventHandler.java` — currently disabled there with a `// todo: fix ScreenEntityRender / TestGui().` note, so treat as illustrative rather than confirmed-working):**
```java
HoloUI.builder()
        .inFrontOf(Platform.getClientPlayer(), 5, false)
        .screenScale(0.5f)
        .open(new TestGui());
```

---

## `com.cleanroommc.modularui.holoui.Plane3D`

```java
@ApiStatus.Experimental
public class Plane3D
```

Holds the geometry (virtual pixel size, world scale, anchor point, facing normal) for a holo screen and applies the corresponding OpenGL transform. Plumbing class — mutated by `HoloUI.Builder` and read by `HoloScreenEntity`/`ScreenEntityRender`; not usually constructed or called into directly by end-user code beyond the builder methods above.

Defaults: width `480`, height `270`, scale `1f`, anchor `(0.5, 0.5)` (center), normal `(0,0,1)`.

Key non-trivial methods (touch these only if extending the holo rendering itself):
- `transformRectangle()` — applies the full GL transform stack (anchor translate → scale/rotate pivot → `0.0625 * scale` scale (1/16, converting pixels to in-world blocks) → 180° rotation → optional rotation matrix built from the facing normal via a Rodrigues-style basis construction → un-translate). Called from `ScreenEntityRender.doRender` inside a push/pop matrix pair.
- `setWidthWithProp(float w)` / `setHeightWithProp(float h)` — resize one dimension, scaling the other proportionally to preserve aspect ratio.
- `setNormal(float x, float y, float z)` — auto-normalizes to unit length if not already.

Trivial getters/setters (`setSize`, `setAnchor`, `setScale`, `getWidth`, `getHeight`, `getScale`) are plain field accessors.

---

## `com.cleanroommc.modularui.holoui.ScreenOrientation`

```java
@ApiStatus.Experimental
public enum ScreenOrientation {
    FIXED, TO_PLAYER
}
```

Two-value enum controlling whether a holo screen's facing normal is fixed at spawn time (`FIXED`) or recomputed every render frame to face the viewing player (`TO_PLAYER`, handled in `ScreenEntityRender.doRender`).

---

## `com.cleanroommc.modularui.holoui.HoloScreenEntity`

```java
@ApiStatus.Experimental
public class HoloScreenEntity extends Entity
```

The invisible Minecraft `Entity` that carries a `ModularScreen` + `Plane3D` through the world so it can be rendered and (via `dataWatcher`) sync its `ScreenOrientation` to observing clients. Plumbing class — spawned exclusively through `HoloUI.Builder.open(...)`; not meant to be constructed directly in normal usage.

Notable behavior for anyone extending/debugging this class:
- `setScreen(ModularScreen)` builds a `GuiContainerWrapper` (a fake `ModularContainer` + the given screen) sized to the current `Plane3D` dimensions — this is what actually gets drawn each frame.
- `onEntityUpdate()` is client-only (`worldObj.isRemote` guarded) for most of its logic: it extinguishes fire visuals, kills the entity below y=-64, and re-syncs the `GuiContainerWrapper`'s resolution if the `Plane3D` size changed since last tick.
- `getOrientation()`/`setOrientation(...)` read/write a `dataWatcher` byte slot (index 16) so orientation replicates to observing clients like normal entity metadata.
- Several `Entity` lifecycle overrides are hardcoded for a non-physical HUD-like entity: always renders regardless of distance (`isInRangeToRender3d/Dist` return `true`), never triggers pressure plates, is not a valid mob-spawn creature type, NBT read/write are no-ops (no persistent state), and `getBrightnessForRender` is hardcoded to full brightness (`15728880`, `@SideOnly(Side.CLIENT)`).
- `spawnInWorld()` — convenience for `worldObj.spawnEntityInWorld(this)`.

---

## `com.cleanroommc.modularui.holoui.ScreenEntityRender`

```java
@ApiStatus.Experimental
public class ScreenEntityRender extends Render
```

The Minecraft `Render<HoloScreenEntity>`-style renderer (raw `Render` type) that actually draws the wrapped GUI onto the 3D plane each frame. Plumbing/registration class — register an instance of this against `HoloScreenEntity` in Minecraft's render-entity registry; not something application code calls into directly.

- `getEntityTexture(Entity)` — always returns `null` (no billboard texture; the screen itself is the visual).
- `doRender(Entity e, double x, double y, double z, float entityYaw, float partialTicks)` — casts to `HoloScreenEntity`, bails if its `GuiContainerWrapper` is `null` (screen not yet set), recomputes the plane's facing normal toward the client player if orientation is `TO_PLAYER`, then push-matrix / translate to the entity position / `plane3D.transformRectangle()` / draw the wrapped screen / pop-matrix.

**Inferred:** the `// todo: fix ScreenEntityRender / TestGui().` comment in `test/TestEventHandler.java` suggests this renderer currently has a known bug preventing the demo from working; treat holo rendering as unverified/likely-broken until that's resolved upstream.
