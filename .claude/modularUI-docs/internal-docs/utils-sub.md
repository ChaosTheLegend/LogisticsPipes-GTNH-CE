# `utils` sub-package reference

Package roots: `com.cleanroommc.modularui.utils.{fakeworld,fluid,item,math,serialization}`

Covers four largely independent utility areas bundled under `utils`: a fake-`World` renderer used to preview 3D structures inside a GUI (`fakeworld`), a long-capacity fluid-tank abstraction (`fluid`), a Forge-`IItemHandler`-shaped item-handler abstraction (`item`), small `evalex` math-expression helpers (`math`), and a `PacketBuffer` (de)serialization helper toolkit (`serialization`).

---

## fakeworld

Package: `com.cleanroommc.modularui.utils.fakeworld`

`package-info.java` marks the whole package `@ApiStatus.Experimental` with the note *"Framebuffer in `BaseSchemaRenderer` not working!"* — `SchemaWidget` itself carries the same warning. Treat everything here as unstable.

**Purpose (inferred from source):** let a mod render a 3D preview of an arbitrary block structure ("schema") inside a 2D GUI — e.g. a multiblock structure preview, a recipe's required block layout, or a decorative diorama — without needing a real, loaded Minecraft `World`. The pipeline is:

1. An `ISchema` describes *which* `BlockInfo` (block + meta + tile entity) sits at which `BlockPos`, backed by a `DummyWorld` (a real `World` subclass that never touches the actual save/chunk system).
2. `RenderWorld` adapts an `ISchema` to `IBlockAccess` so vanilla rendering code (`RenderBlocks`, TESRs) can read it, honoring the schema's optional render filter (e.g. to hide layers).
3. `BaseSchemaRenderer` (an `IDrawable`) sets up an isolated GL camera/viewport, renders the schema's blocks and tile entities into a framebuffer (currently broken — see above) and draws the result as a texture; `SchemaRenderer` is the public subclass exposing camera/scale/ray-trace hooks.
4. `com.cleanroommc.modularui.widgets.SchemaWidget` (different package — not documented here) wraps a `BaseSchemaRenderer`/`ISchema` as an interactive `Widget`: mouse-drag rotates/pans the camera, scroll wheel zooms, and it exposes `getBlockUnderMouse()` from the renderer's ray trace. That's the intended end-user entry point into this subsystem — construct an `ISchema` (`ArraySchema`/`BoxSchema`/`MapSchema`/`PosListSchema`/`SchemaWorld`), then `new SchemaWidget(schema)` and add it as a child widget.

### `com.cleanroommc.modularui.utils.fakeworld.ISchema`

```java
public interface ISchema extends Iterable<Pair<BlockPos, BlockInfo>>
```

Core contract for anything that can be rendered by the fake-world pipeline: a `World` to read tile entities/render data from, a focus point for the camera to look at, an origin, an optional render filter, and iteration over every `(BlockPos, BlockInfo)` pair it contains.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getWorld()` | - | `World` | The backing (usually `DummyWorld`) world instance blocks were placed into. |
| `getFocus()` | - | `Vector3d` | World-space point the camera should look at (typically the structure's center). |
| `getOrigin()` | - | `BlockPos` | Reference corner of the schema (usage varies per implementation — min corner for box/array schemas). |
| `setRenderFilter(BiPredicate<BlockPos, BlockInfo> filter)` | nullable filter | - | Installs a predicate; blocks failing it are skipped by iteration/rendering (used e.g. by `SchemaWidget.LayerButton` to show only up to a Y layer). |
| `getRenderFilter()` | - | nullable `BiPredicate<BlockPos, BlockInfo>` | Current filter. |
| `iterator()` | - | `Iterator<Pair<BlockPos, BlockInfo>>` | Enumerates placed (non-air, filter-passing) blocks; used by `BaseSchemaRenderer.renderBlocksInLayer`. |

**Example (constructed, not from repo):**
```java
ISchema schema = BoxSchema.of(myWorld, new BlockPos(0, 64, 0), 3);
schema.setRenderFilter((pos, info) -> info.getBlock() != Blocks.air);
```

### `com.cleanroommc.modularui.utils.fakeworld.BlockInfo`

```java
public class BlockInfo
```

Immutable snapshot of a block's state at some position: `Block` + metadata + (optional) `TileEntity`, sufficient to fully represent a block (including machines) without needing a live `World` reference. `BlockInfo.Mut` is a mutable subclass reused internally as a scratch/shared instance to avoid allocations during iteration (see `BlockInfo.Mut.SHARED`, used by `PosListSchema`/`RenderWorld`).

```java
public static class Mut extends BlockInfo
```

| Member | Params | Returns | Notes |
|---|---|---|---|
| `EMPTY`, `INVALID` | `static final BlockInfo` | - | Both wrap `Blocks.air`; two separate constants for semantic distinction (`INVALID` unused as a sentinel currently, just an air block like `EMPTY`). |
| `of(IBlockAccess world, BlockPos pos)` (static) | world, pos | `BlockInfo` | Snapshots a real position: reads block/meta, and if the block has a tile entity, deep-copies it via NBT round-trip (`fixRealTileWorldCorrupting`) so the fake copy never mutates/attaches to the real tile. Returns `EMPTY` for air. |
| `BlockInfo(Block)`, `BlockInfo(Block, int meta)`, `BlockInfo(Block, TileEntity)`, `BlockInfo(Block, int meta, TileEntity)` | see names | - | Direct construction; `Preconditions` in the internal `set()` reject a non-null tile for a block that `hasTileEntity(meta)` returns false for. |
| `getBlock()`, `getBlockMeta()`, `getTileEntity()` | - | `Block` / `int` / `TileEntity` | Plain getters. |
| `apply(World world, BlockPos pos)` | world, pos | - | Places this block+meta into `world` at `pos`; sets the tile entity if present, otherwise reads back whatever tile the world created. Used by schema constructors to populate their backing `DummyWorld`. |
| `isMutable()` | - | `boolean` | `false` here, `true` on `Mut`. |
| `toMutable()` | - | `Mut` | Copies into a new mutable instance (or returns `this` if already `Mut`). |
| `toImmutable()` | - | `BlockInfo` | Identity on `BlockInfo`; on `Mut` makes a defensive immutable copy — needed because `Mut.SHARED` is reused across iterations. |
| `copy()` | - | `BlockInfo`/`Mut` | Defensive copy preserving mutability class. |

**Gotcha:** `Mut.SHARED` is a single static scratch instance reused by `PosListSchema`/`RenderWorld`/`SchemaWorld` iterators — never retain a reference to a `BlockInfo` obtained mid-iteration from those without calling `.toImmutable()`/`.copy()` first.

### `com.cleanroommc.modularui.utils.fakeworld.BlockPosUtil`

```java
public class BlockPosUtil
```

Static helpers over `com.gtnewhorizon.gtnhlib.blockpos.BlockPos`, used throughout the schema classes for bounding-box math.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `MAX`, `MIN` | `static final BlockPos` | - | `(Integer.MAX_VALUE, ...)` / `(Integer.MIN_VALUE, ...)` sentinels used to seed min/max accumulation. |
| `getManhattanDistance`, `getBlockCountInside`, `getXDist`/`getYDist`/`getZDist` | two `BlockPos` | `int` | Straightforward per-axis distance / volume math. |
| `getMin(p1, p2)`, `getMax(p1, p2)` | two `BlockPos` | new `BlockPos` | Component-wise min/max. |
| `setMin(p1, p2)`, `setMax(p1, p2)` | mutable `p1`, `p2` | - | Mutates `p1` in place to the component-wise min/max of itself and `p2`. |
| `getCenter(p1, p2)` | two `BlockPos` | `BlockPos` (integer) | **Bug (observable in source):** the Z component formula uses `getYDist(...)` twice instead of `getZDist`, so the returned Z is wrong unless the box is cubic on Y/Z. Prefer `getCenterD` for anything that needs a correct center. |
| `getCenterD(p1, p2)` / `getCenterD(origin, xs, ys, zs)` | positions or origin+extents | `Vector3d` | Correct floating-point center; used by all schema `getFocus()` implementations. |
| `getAllInside(p1, p2, includeBorder)` | corners, whether to include the border layer | `Iterable<BlockPos>` | Backs `BoxSchema`/`PosListSchema`. |
| `isOnBorder(boxMin, boxMax, p)` | box + point | `boolean` | True if `p` touches any face of the box; used by `SchemaWorld` to know when removing a block might shrink its cached bounds. |
| `add(pos, x, y, z)` | mutable `pos`, deltas | same `BlockPos` | In-place translate, returns `pos` for chaining. |

### `com.cleanroommc.modularui.utils.fakeworld.ArraySchema`

```java
public class ArraySchema implements ISchema
```

Schema backed by a dense `BlockInfo[][][]` 3D array (indices are local X/Y/Z, `null` entries = "any block allowed"/empty). Good for hand-authored fixed-size structures (a `Builder` lets you draw them as ASCII-art layers).

| Member | Params | Returns | Notes |
|---|---|---|---|
| `of(Entity entity, int radius)` (static) | entity, radius | `ArraySchema` | Snapshots a cube of the *real* world around an entity's position (`BlockInfo.of` per block) into a fresh `DummyWorld`. |
| `of(World world, int centerX, int centerY, int centerZ, int radius)` (static) | world, center, radius | `ArraySchema` | Same, explicit center. |
| `of(World world, int ax, int ay, int az, int bx, int by, int bz)` (static) | world, two opposite corners | `ArraySchema` | Snapshots an arbitrary axis-aligned box (order-independent corners) from a real world. |
| `ArraySchema(BlockInfo[][][] blocks)` | pre-built dense array | - | Applies every non-null entry into an internal `DummyWorld`; computes `getFocus()` as the array's center. |
| `getWorld()`, `getFocus()`, `getOrigin()` | - | `World` / `Vector3d` / `BlockPos` | `getOrigin()` is always `(0,0,0)`. |
| `setRenderFilter`/`getRenderFilter` | see `ISchema` | - | |
| `iterator()` | - | `Iterator<Pair<BlockPos, BlockInfo>>` | Skips `null` array slots and filter-rejected entries. |

```java
public static class Builder
```

Fluent ASCII-art builder: each `layer(String...)` call adds one X-layer of Y-rows of Z-characters; `where(char, ...)` maps a character to a `BlockInfo` (by `Block`, `Block`+meta, `Block`+`TileEntity`, or a `ResourceLocation`/registry name string). `' '` and `'#'` default to `BlockInfo.EMPTY`. `build()` validates that every layer/row has consistent dimensions and every character used is mapped (throws `IllegalArgumentException` after logging all problems to `ModularUI.LOGGER` otherwise).

**Example (constructed, not from repo):**
```java
ArraySchema schema = ArraySchema.builder()
        .where('C', Blocks.chest)
        .where('#', Blocks.iron_block)
        .layer(
                "###",
                "#C#",
                "###")
        .build();
```

### `com.cleanroommc.modularui.utils.fakeworld.BoxSchema`

```java
public class BoxSchema extends PosListSchema
```

A schema that lazily represents every block inside an axis-aligned box **of a real `World`** (not a copy into a `DummyWorld` — it queries `world` live through the `PosListSchema` iterator, via `BlockInfo.Mut.SHARED`). Cheaper than `ArraySchema` when you just want a live preview window into an existing world.

| Member | Params | Returns | Notes |
|---|---|---|---|
| `of(World world, BlockPos center, int r)` (static) | world, center, radius | `BoxSchema` | Box from `center - r` to `center + r`, no filter (always show). |
| `of(World world, BlockPos center, int r, BiPredicate<BlockPos, BlockInfo> renderFilter)` (static) | + filter | `BoxSchema` | Same with a filter. |
| `BoxSchema(World world, BlockPos min, BlockPos max, BiPredicate renderFilter)` | corners (order-independent) | - | Delegates to `PosListSchema` with `BlockPosUtil.getAllInside(min, max, false)`. |
| `getWorld()`, `getFocus()`, `getOrigin()` | - | `World` / `Vector3d` / `BlockPos` | `getOrigin()` returns the box's min corner. |
| `getMin()`, `getMax()` | - | `BlockPos` | Box bounds. |

**Example (constructed, not from repo):**
```java
BoxSchema schema = BoxSchema.of(player.worldObj, new BlockPos((int) player.posX, (int) player.posY, (int) player.posZ), 4);
```

### `com.cleanroommc.modularui.utils.fakeworld.MapSchema`

```java
public class MapSchema implements ISchema
```

Sparse schema backed by a `BlockPos -> BlockInfo` map — good for irregularly-shaped or scattered structures where a dense array would waste memory. Air entries are dropped at construction; every kept block is applied into an internal `DummyWorld`.

| Member | Params | Returns | Notes |
|---|---|---|---|
| `MapSchema(Map<BlockPos, BlockInfo> blocks)` / `MapSchema(Map, BiPredicate renderFilter)` | block map, optional filter | - | Computes bounds/focus from the surviving (non-air) entries; empty input yields a zero-size box at origin. |
| `getWorld()`, `getFocus()`, `getOrigin()` | - | `World` / `Vector3d` / `BlockPos` | `getOrigin()` is the map's min corner. |
| `setRenderFilter`/`getRenderFilter` | see `ISchema` | - | |
| `iterator()` | - | `Iterator<Pair<BlockPos, BlockInfo>>` | Iterates the backing `Object2ObjectOpenHashMap`, filter-aware. |

```java
public static class Builder
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `add(pos, block)` / `add(pos, block, meta)` / `add(pos, block, meta, tile)` | position + block description | `Builder` | No-ops (returns `this` unchanged) if `block == Blocks.air`. |
| `add(pos, BlockInfo)` | position, info | `Builder` | Stores `blockInfo.toImmutable()`. |
| `add(Iterable<BlockPos>, Function<BlockPos, BlockInfo>)` | positions + generator | `Builder` | Bulk-add via a per-position factory. |
| `add(Map<BlockPos, BlockInfo>)` | map | `Builder` | Bulk `putAll`. |
| `setRenderFilter(BiPredicate)` | filter | `Builder` | |
| `build()` | - | `MapSchema` | |

**Example (constructed, not from repo):**
```java
MapSchema schema = new MapSchema.Builder()
        .add(new BlockPos(0, 0, 0), Blocks.gold_block)
        .add(new BlockPos(1, 0, 0), Blocks.iron_block)
        .build();
```

### `com.cleanroommc.modularui.utils.fakeworld.PosListSchema`

```java
public abstract class PosListSchema implements ISchema
```

Base class representing a schema as a plain `Iterable<? extends BlockPos>` positions read live from a `World` (rather than copied into a `DummyWorld`), reusing `BlockInfo.Mut.SHARED` per iterated element for zero-allocation reads. `BoxSchema` is the only concrete subclass in this package, but this is the extension point for "arbitrary live-world position list" schemas.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `PosListSchema(World world, Iterable<? extends BlockPos> posList, BiPredicate renderFilter)` | backing world, positions, optional filter | - | |
| `getWorld()` | - | `World` | The real world passed in (not a dummy). |
| `setRenderFilter`/`getRenderFilter` | see `ISchema` | - | |
| `iterator()` | - | `Iterator<Pair<BlockPos, BlockInfo>>` | For each position, refreshes `BlockInfo.Mut.SHARED` from the live world; if the filter rejects it, yields `BlockInfo.EMPTY` for that position instead of skipping it (unlike `ArraySchema`/`MapSchema`, which skip). |

**Gotcha:** subclasses must still implement `getFocus()`/`getOrigin()` — this class only wires `getWorld()`/filter/iteration.

### `com.cleanroommc.modularui.utils.fakeworld.SchemaWorld`

```java
public class SchemaWorld extends DummyWorld implements ISchema
```

A `DummyWorld` that *is* its own `ISchema` — i.e., build the structure by calling normal `World.setBlock(...)` on it directly (through vanilla/Forge world-editing code), and it tracks which positions are occupied (`ObjectLinkedOpenHashSet<BlockPos>`) plus a running min/max bounding box, incrementally, as blocks are placed/removed.

| Member | Params | Returns | Notes |
|---|---|---|---|
| `SchemaWorld()` | - | - | No filter (always render). |
| `SchemaWorld(BiPredicate<BlockPos, BlockInfo> renderFilter)` | initial filter | - | |
| `setBlock(int x, int y, int z, Block, int meta, int flags)` | vanilla `World.setBlock` signature | `boolean` | Overridden: only actually places the block (delegates to `super.setBlock`) if the render filter accepts it; always maintains the tracked position set and bounding box (shrinking/recomputing bounds on removal via `BlockPosUtil.isOnBorder`). Returns whether both the filter passed and the underlying placement succeeded. |
| `getWorld()` | - | `World` | Returns `this`. |
| `getFocus()` | - | `Vector3d` | Center of the current tracked bounding box. |
| `getOrigin()` | - | `BlockPos` | Current min corner. |
| `iterator()` | - | `Iterator<Pair<BlockPos, BlockInfo>>` | Iterates tracked positions in insertion order, filter-aware, reusing a private `BlockInfo.Mut`. |

**Gotcha:** since it's both the `World` and the `ISchema`, you build it by mutating it directly (`schemaWorld.setBlock(...)`) rather than via a separate builder object, unlike `ArraySchema`/`MapSchema`.

**Example (constructed, not from repo):**
```java
SchemaWorld world = new SchemaWorld();
world.setBlock(0, 0, 0, Blocks.diamond_block, 0, 2);
world.setBlock(1, 0, 0, Blocks.gold_block, 0, 2);
SchemaWidget widget = new SchemaWidget(world);
```

### `com.cleanroommc.modularui.utils.fakeworld.DummyWorld`

```java
public class DummyWorld extends World
```

A real Minecraft `World` subclass configured to never touch disk I/O or fire neighbor-update/Forge events — the backing store for `ArraySchema`/`MapSchema`/`SchemaWorld`/`FakeEntity`. Constructed with a `DummySaveHandler`, `WorldProviderSurface`, a fixed `WorldSettings` (seed `1`, survival, `WorldType.DEFAULT`), and a `DummyChunkProvider`. Reflectively nulls out the vanilla `lightUpdateBlockList` field (`ObfuscationReflectionHelper`) since light updates are meaningless here and the list would otherwise grow unbounded.

| Member | Params | Returns | Notes |
|---|---|---|---|
| `INSTANCE` | `public static final DummyWorld` | - | Shared singleton instance (also used per-schema — schemas that need isolation construct their own `new DummyWorld()` rather than sharing `INSTANCE`). |
| `DummyWorld()` | - | - | Sets the internal dimension ID to `Integer.MAX_VALUE` around provider registration to avoid clashing with real dimension IDs. |
| `notifyBlocksOfNeighborChange(...)` (both overloads), `markAndNotifyBlock(...)`, `markBlockForUpdate(...)`, `markBlockRangeForRenderUpdate(...)` | vanilla signatures | - | All no-ops — prevents cascading vanilla/Forge block-update logic from firing against the fake world. |
| `createChunkProvider()` | - | `IChunkProvider` | Returns a `DummyChunkProvider`. |
| `updateLightByType(...)` | vanilla signature | `boolean` | Always `true` (real implementation was disabled by removing `lightUpdateBlockList`). |
| `func_152379_p()` | - | `int` | Returns `-1` (obfuscated method, likely "next entity/tile ID" or similar — not used meaningfully here). |
| `getEntityByID(int)` | id | `Entity` | Always `null` — no entity tracking in the dummy world. |

**Inferred:** `DummyWorld` is the load-bearing piece that makes the whole `fakeworld` subsystem possible — it lets `Block`/`TileEntity` code that expects a real `World` run safely off the actual game world.

### `com.cleanroommc.modularui.utils.fakeworld.DummyChunkProvider`

```java
public class DummyChunkProvider implements IChunkProvider
```

Minimal in-memory `IChunkProvider` for `DummyWorld`: lazily creates and caches `Chunk` objects per `(x,z)` (packed via `CoordinatePacker`) instead of loading from disk, and disables saving/population/structure generation entirely.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `DummyChunkProvider(World world)` | owning world | - | |
| `loadChunk(x, z)` | chunk coords | `Chunk` or `null` | Only returns already-cached chunks, never generates. |
| `provideChunk(x, z)` | chunk coords | `Chunk` | Creates-and-caches a fresh empty `Chunk` on first access. |
| `chunkExists`, `canSave` | - | `boolean` | `true` / `false` respectively (chunks always "exist" but nothing is ever saved). |
| `populate`, `saveChunks`, `unloadQueuedChunks`, `recreateStructures`, `saveExtraData` | vanilla signatures | no-op / `false` | All disabled. |
| `getPossibleCreatures(...)` | vanilla signature | `List<BiomeGenBase.SpawnListEntry>` | Always empty — no mob spawning. |

### `com.cleanroommc.modularui.utils.fakeworld.DummySaveHandler`

```java
@SuppressWarnings("all")
public class DummySaveHandler implements ISaveHandler, IPlayerFileData, IChunkLoader
```

All-no-op implementation of the three save/load interfaces `World` requires, so `DummyWorld` never reads or writes to disk. Every method either returns `null`/`false`/empty or does nothing; `getChunkLoader`/`getSaveHandler` return `this` (self-referential no-op chain).

**Inferred:** never meant to be used directly — only exists to satisfy `DummyWorld`'s constructor requirement for an `ISaveHandler`.

### `com.cleanroommc.modularui.utils.fakeworld.RenderWorld`

```java
public class RenderWorld implements IBlockAccess
```

Adapts an `ISchema` to `IBlockAccess` for consumption by vanilla block-rendering code (`RenderBlocks`, `isSideSolid`, etc.), applying the schema's render filter transparently on every block/tile-entity/metadata query (returning air/`null`/`0` for filtered-out positions instead of the real content).

| Method | Params | Returns | Notes |
|---|---|---|---|
| `RenderWorld(ISchema schema)` | schema to wrap | - | Caches `schema.getWorld()` for delegation. |
| `getTileEntity(x, y, z)` | coords | `TileEntity` or `null` | Filter-checked; `null` if `schema == null` falls back to the world directly (defensive, though `schema` is `final` and set in the constructor so this branch is effectively dead unless subclassed oddly). |
| `getBlock(x, y, z)` | coords | `Block` | Filter-checked, returns `Blocks.air` if filtered/absent. |
| `getBlockMetadata(x, y, z)` | coords | `int` | Filter-checked, `0` if filtered. |
| `isAirBlock(x, y, z)` | coords | `boolean` | Delegates to `getBlock(...).isAir(...)`. |
| `getLightBrightnessForSkyBlocks`, `getBiomeGenForCoords`, `isBlockProvidingPowerTo`, `getHeight` | vanilla signatures | passthrough | Delegate straight to the backing world (not filtered — lighting/biome/power aren't schema-specific). |
| `isSideSolid(x, y, z, side, default)` | vanilla signature | `boolean` | Bounds-checks against the `±30000000` world border before querying the chunk, matching vanilla's own guard. |
| `extendedLevelsInChunkCache()` | - | `boolean` | Always `false`. |

### `com.cleanroommc.modularui.utils.fakeworld.Camera`

```java
public class Camera
```

Mutable orbit/free camera state (position, look-at point, yaw, pitch, distance) with several redundant ways to set it depending on what you want to keep fixed while changing something else. Uses `org.joml.Vector3f`. Consumed by `BaseSchemaRenderer`/`SchemaWidget` to drive `GLU.gluLookAt`.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `setPosAndLookAt(...)` (4 overloads) | explicit pos + look-at (as floats/`Vector3f` mixes) | `Camera` | Recomputes yaw/pitch/distance from the two points. |
| `setLookAtKeepPos(x, y, z)` | new look-at | `Camera` | Keeps `pos`, recomputes angle from it. |
| `setLookAtKeepAngle(x, y, z)` | new look-at | `Camera` | Keeps yaw/pitch/distance, translates `pos` by the look-at delta (orbit-style pan). |
| `setPosKeepLookAt(x, y, z)` | new pos | `Camera` | Keeps look-at, recomputes angle. |
| `setPosKeepAngle(x, y, z)` | new pos | `Camera` | Keeps yaw/pitch/distance, translates look-at by the same delta. |
| `setAngleKeepLookAt(radius, yaw, pitch)` | spherical params | `Camera` | Recomputes `pos` on the orbit sphere around the current look-at. |
| `setLookAtAndAngle(Vector3f/Vector3i lookAt, radius, yaw, pitch)` / `(x, y, z, dist, yaw, pitch)` | explicit look-at + spherical | `Camera` | Used by `SchemaWidget.draw` every frame to point the camera at the schema's focus. |
| `setPosAndAngle(posX, posY, posZ, dist, yaw, pitch)` | pos + spherical | `Camera` | Derives look-at from pos + angle (inverse of the above). |
| `setDistanceKeepLookAt(dist)` | absolute distance | - | Moves `pos` along the existing pos→lookAt axis to the new distance. |
| `scaleDistanceKeepLookAt(factor)` | multiplier | - | Same but relative; no-op if `factor == 1`. |
| `getPos()`, `getLookAt()`, `getLookVec()`, `getLookVec(Vector3f dest)`, `getYaw()`, `getPitch()`, `getDist()` | - | `Vector3f`/`float` | `getPos()`/`getLookAt()` return defensive copies; `getLookVec` is `lookAt - pos` (optionally into a supplied `dest` to avoid allocation). |

**Example (adapted from `widgets/SchemaWidget.java`):**
```java
Vector3d f = schema.getFocus();
camera.setLookAtAndAngle((float) f.x, (float) f.y, (float) f.z, /*dist*/ 10f, /*yaw*/ 0f, /*pitch*/ (float) Math.PI / 4f);
```

### `com.cleanroommc.modularui.utils.fakeworld.Projection`

```java
public class Projection
```

Singleton (`INSTANCE`) thin wrapper around `GLU.gluProject`/`gluUnProject` using the *current* GL matrix/viewport state, with static scratch `FloatBuffer`/`IntBuffer`s to avoid per-call allocation. Uses legacy `org.lwjgl.util.vector.Vector3f` (note: distinct from the `org.joml.Vector3f` used elsewhere in this package).

| Method | Params | Returns | Notes |
|---|---|---|---|
| `project(BlockPos pos)` | block position | `Vector3f` | World→screen: projects the block's center (`pos + 0.5` each axis) using the live modelview/projection/viewport matrices read via `glGet`. |
| `unProject(int screenX, int screenY)` | screen coords | `Vector3f` | Screen→world: reads the depth buffer at that pixel (`GL11.glReadPixels`) then unprojects. |

**Gotcha:** both methods read GL state (`glGetFloat`/`glGetInteger`) at call time — must be called while the intended camera matrices are still bound (i.e., during/immediately after the relevant render call), not arbitrarily later.

### `com.cleanroommc.modularui.utils.fakeworld.BlockHighlight`

```java
public class BlockHighlight
```

Renders a colored selection-box outline (solid or wireframe "frame") around one block, similar to vanilla's block-breaking overlay — used to highlight a ray-traced block (see `SchemaRenderer.highlightRenderer`). Vertex data for each face is precomputed once statically, scaled by ±0.005 to avoid z-fighting with the block's own faces.

| Constructor | Params | Notes |
|---|---|---|
| `BlockHighlight(int color)` | ARGB color | All sides, solid fill (`frameThickness = 0`). |
| `BlockHighlight(int color, float frameThickness)` | + thickness | All sides, wireframe frame of given thickness. |
| `BlockHighlight(int color, boolean allSides)` | + whether to draw all 6 faces vs. just the hit face | Solid fill. |
| `BlockHighlight(int color, boolean allSides, float frameThickness)` | full control | |

| Method | Params | Returns | Notes |
|---|---|---|---|
| `renderHighlight(MovingObjectPosition result, Vector3f camera)` | ray-trace result, camera pos | - | No-ops unless `result.typeOfHit == BLOCK`; delegates to the coordinate overload. |
| `renderHighlight(int x, int y, int z, ForgeDirection side, Vector3f camera)` | block pos, hit side, camera pos | - | Sets up GL color/translate state, computes squared distance to camera (for frame-thickness scaling), then calls `doRender`. |
| `getFrameThickness()`/`setFrameThickness(float)`, `getColor()`/`setColor(int)`, `isAllSides()`/`setAllSides(boolean)` | - | plain getters/setters | Negative `frameThickness` selects solid-fill rendering (`doRender` checks `>= 0`). |

**Inferred:** used together with `SchemaRenderer.rayTracing(true)`/`highlightRenderer(...)` to show which schema block is under the mouse cursor.

### `com.cleanroommc.modularui.utils.fakeworld.FakeEntity`

```java
public class FakeEntity
```

Utility for instantiating vanilla/modded `Entity` subclasses attached to a shared `DummyWorld`, purely so they can be rendered in a GUI (e.g. an item preview showing an entity model) without a real world reference.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `create(Class<T> entityClass)` (static) | entity class | `T` | Looks up the class's `EntityRegistry.EntityRegistration` via `EntityRegistry.instance().lookupModSpawn(...)`, then delegates. |
| `create(EntityRegistry.EntityRegistration entry)` (static) | registration entry | `Entity` | Reflectively invokes the `(World)` constructor with the shared dummy world; wraps checked reflection exceptions in `RuntimeException`. |

**Example (constructed, not from repo):**
```java
EntityItem fakeItem = FakeEntity.create(EntityItem.class);
```

### `com.cleanroommc.modularui.utils.fakeworld.BaseSchemaRenderer`

```java
public class BaseSchemaRenderer implements IDrawable
```

The actual GL rendering engine for an `ISchema`: sets up an isolated perspective camera/viewport, iterates the schema's blocks through a `RenderWorld`, renders solid+translucent passes plus TESRs, optionally ray-traces under the mouse, and (intended to, currently broken per the package warning) composites the result into an offscreen `Framebuffer` drawn as a textured quad. Most behavior is customized via the `@ApiStatus.OverrideOnly` protected hooks; `SchemaRenderer` is the public, composable subclass most code should use instead of subclassing this directly.

| Member | Params | Returns | Notes |
|---|---|---|---|
| `BaseSchemaRenderer(ISchema schema, Framebuffer framebuffer)` | schema, explicit target FBO | - | |
| `BaseSchemaRenderer(ISchema schema)` | schema | - | Uses a shared static `1080x1080` FBO (`FBO`) — **all instances without an explicit framebuffer share this one buffer**, so concurrent/overlapping renders would clobber each other. |
| `getSchema()` | - | `ISchema` | |
| `getCamera()` | - | `Camera` | Mutable camera driving the perspective/lookAt setup; callers (e.g. `SchemaWidget`) mutate this every frame before `draw`. |
| `getLastRayTrace()` | - | nullable `MovingObjectPosition` | Result of the most recent ray trace (only populated if `doRayTrace()` returns `true`). |
| `asWidget()` | - | `SchemaWidget` | `IDrawable` override — wraps `this` in a new `SchemaWidget`. |
| `asIcon()` | - | `Icon` | Sized to 50px by default. |
| `draw(GuiContext, x, y, width, height, WidgetTheme)` | `IDrawable` draw call | - | Delegates to `render(...)`. |
| `doRayTrace()` | - | `boolean` | `false` by default; override (or use `SchemaRenderer.rayTracing(true)`) to enable mouse ray-tracing each frame. |
| `getClearColor()` | - | `int` (ARGB) | Defaults to a bright red — deliberately loud so the broken-framebuffer bug is obvious rather than silently showing black/transparent. |
| `isIsometric()`, `isTesrEnabled()` | - | `boolean` | Hooks for subclasses (`SchemaRenderer` exposes these as configurable). |
| `onSetupCamera()`, `onRendered()`, `onSuccessfulRayTrace(MovingObjectPosition)`, `onRayTraceFailed()` | — (protected, `@ApiStatus.OverrideOnly`) | - | Extension points called at the corresponding points in `render(...)`. |

**Gotcha:** the class-level TODO states the framebuffer compositing does not currently work in 1.7.10 (world/ray-trace rendering does); don't rely on `asIcon()`/`draw()` producing a visible result until that's fixed.

### `com.cleanroommc.modularui.utils.fakeworld.SchemaRenderer`

```java
public class SchemaRenderer extends BaseSchemaRenderer
```

Public, fluent-configurable subclass of `BaseSchemaRenderer` — the one application code is expected to instantiate directly (`SchemaWidget`'s `ISchema` constructor wraps a plain `BaseSchemaRenderer`; use this subclass explicitly when you need any of the below hooks).

| Method | Params | Returns | Notes |
|---|---|---|---|
| `SchemaRenderer(ISchema schema)` / `SchemaRenderer(ISchema schema, Framebuffer fb)` | see `BaseSchemaRenderer` | - | |
| `cameraFunc(BiConsumer<Camera, ISchema> fn)` | per-frame camera hook | `SchemaRenderer` | Called from `onSetupCamera()` after the scale adjustment, so it can further reposition the camera each frame. |
| `scale(double)` / `scale(DoubleSupplier)` | zoom multiplier or dynamic supplier | `SchemaRenderer` | Applied via `camera.scaleDistanceKeepLookAt(...)` in `onSetupCamera()`. |
| `isometric(boolean)` | flag | `SchemaRenderer` | Feeds `isIsometric()`, which scales the modelview matrix by `0.1` instead of using perspective depth cues. |
| `disableTESR(boolean)` / `disableTESR(BooleanSupplier)` | flag/supplier | `SchemaRenderer` | Feeds `isTesrEnabled()` (inverted). |
| `rayTracing(boolean)` | flag | `SchemaRenderer` | Feeds `doRayTrace()`. |
| `highlightRenderer(BlockHighlight highlight)` | highlight renderer | `SchemaRenderer` | Also force-enables ray tracing (calls `rayTracing(true)`); the highlight is drawn in `onSuccessfulRayTrace`. |
| `afterRender(Consumer<SchemaRenderer>)` | callback | `SchemaRenderer` | Invoked from `onRendered()`, after the world/TESR render pass but before framebuffer teardown. |

**Example (constructed, not from repo):**
```java
SchemaRenderer renderer = new SchemaRenderer(schema)
        .rayTracing(true)
        .highlightRenderer(new BlockHighlight(Color.WHITE.main, 1f))
        .scale(1.0);
SchemaWidget widget = new SchemaWidget(renderer);
```

---

## fluid

Package: `com.cleanroommc.modularui.utils.fluid`

A **long-capacity** parallel fluid-tank abstraction, distinct from vanilla Forge's `int`-capacity `IFluidTank`/`FluidTank`. `IFluidTankLong`/`FluidTankLong` mirror `IFluidTank`/`FluidTank` but store amount/capacity as `long` (useful once tank sizes exceed `Integer.MAX_VALUE` mB), and default-implement the `int`-based `IFluidTank` methods by saturating-casting to `long`. `IFluidTanksHandler`/`FluidTanksHandler` are the multi-tank container built on top of these.

**Note on the README's headline example:** the project README shows `.child(new FluidSlot().syncHandler(new FluidTank(16000)))` — that `FluidTank` is vanilla Forge's `net.minecraftforge.fluids.FluidTank` (int capacity), not anything in this package. The in-repo demo (`test/TestTile.java`) similarly uses `com.cleanroommc.modularui.utils.MultiFluidTankHandler` (a *different*, `int`-based multi-tank class in the parent `utils` package, wrapping vanilla `IFluidTank[]`) together with `value.sync.FluidSlotSyncHandler`, not the classes in this `fluid` subpackage. Nothing under `test/` exercises `FluidTankLong`/`FluidTanksHandler`/`FluidStackTank`/`ListFluidHandler` directly — all examples below are constructed. **Inferred:** reach for this package's `Long`-suffixed classes specifically when a tank's capacity can exceed ~2.1 billion mB; otherwise `MultiFluidTankHandler`/vanilla `FluidTank` (elsewhere in `utils`) is the simpler/most-used path.

### `com.cleanroommc.modularui.utils.fluid.IFluidTankLong`

```java
public interface IFluidTankLong extends IFluidTank
```

`long`-capacity analogue of Forge's `IFluidTank`, default-bridging every `int`-based `IFluidTank` method onto the `long` ones via `Ints.saturatedCast`.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `fill(Fluid fluid, long amount, boolean doFill)` | fluid type, amount, commit flag | `long` filled | Primary fill method. |
| `drain(long amount, boolean doFill)` | amount, commit flag | `FluidStack` | Primary drain method (note: param name `doFill` — actually means "commit", same convention issue as vanilla's own `IFluidTank`). |
| `getCapacityLong()`, `getRealCapacityLong()`, `getFluidAmountLong()` | - | `long` | Real (unbounded-overflow) capacity/amount accessors. |
| `getFluidStack()` | - | `FluidStack` | Same as `getFluid()` conceptually — implementations typically alias this. |
| `getStoredFluid()` | - | `Fluid` | Just the `Fluid` type, no amount. |
| `setFluid(Fluid fluid, long amount)` | fluid, amount | - | Direct setter (bypasses fill logic/capacity checks — implementation-dependent). |
| `copy()` | - | nullable `IFluidTankLong` | Defensive copy. |
| `isFluidEqual(IFluidTankLong cached)` | other tank (nullable) | `boolean` | Fluid-type equality check against a cached/previous tank state. |
| `saveToNBT(NBTTagCompound)` / `loadFromNBT(NBTTagCompound)` | tag | - | Persistence. |
| `getFluidAmount()`, `getInfo()`, `fill(FluidStack, boolean)`, `getFluid()`, `getCapacity()`, `drain(int, boolean)` | vanilla `IFluidTank` signatures | default-implemented | Bridge methods: cast/delegate to the `long` versions above. |
| `writeToBuffer(PacketBuffer, IFluidTankLong)` (static) | buffer, tank (nullable) | - | Writes a null-flag boolean then (if non-null) the tank's NBT via `PacketBuffer.writeNBTTagCompoundToBuffer`. |
| `readFromBuffer(PacketBuffer, IFluidTankLong)` (static) | buffer, target tank | - | Reads the null flag then calls `currentTank.loadFromNBT(...)` with either `null` or the read NBT. |

### `com.cleanroommc.modularui.utils.fluid.FluidTankLong`

```java
public class FluidTankLong implements IFluidTankLong
```

Straightforward single-tank `IFluidTankLong` implementation: one `Fluid` + a `long` stored amount + a `long` capacity, with `Ints.saturatedCast` used whenever bridging to `int`-based Forge APIs (e.g. `FluidStack.amount`).

| Constructor | Params | Notes |
|---|---|---|
| `FluidTankLong(Fluid fluid, long capacity, long amount)` | full | Builds an internal `FluidStack` mirror if `fluid != null`. |
| `FluidTankLong(Fluid fluid, long capacity)` | no initial amount | amount `0`. |
| `FluidTankLong(long capacity)` | empty tank | `fluid = null`. |
| `FluidTankLong(FluidStack fluid, long capacity, long amount)` | from a `FluidStack`'s fluid type | Ignores the stack's own amount — uses the explicit `amount` param. |

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getFluidStack()` | - | `FluidStack` | **Doc comment says prefer `drain`/`fill` for actual fluid handling** — this getter also has a side effect: if `storedAmount <= 0`, it clears `fluid`/`internal`/`storedAmount` as a side effect of being called. |
| `getStoredFluid()`, `getCapacityLong()`, `getRealCapacityLong()`, `getFluidAmountLong()` | - | `Fluid`/`long` | Plain accessors (`getRealCapacityLong()` == `getCapacityLong()` here — no overflow concept, unlike `FluidStackTank`/`IOverflowableTank`). |
| `fill(Fluid fluid, long amount, boolean doFill)` | fluid, amount, commit | `long` | Returns `0` if the tank already holds a *different* fluid or `fluid == null`; otherwise fills up to remaining capacity and (if `fluid` was previously `null`) adopts the new fluid type. |
| `drain(long amount, boolean doDrain)` | amount, commit | `FluidStack` | Returns `null` if empty; on commit, clears `fluid`/`internal` once `storedAmount <= 0` **unless `locked`** (there is no public setter for `locked` in this class — it stays `false` always as written, so this branch is effectively dead code as it stands). |
| `setFluid(Fluid fluid, long amount)` / `setFluid(Fluid fluid)` | fluid, optional amount | - | Direct overwrite, no capacity clamp. |
| `saveToNBT(NBTTagCompound)` / `loadFromNBT(NBTTagCompound)` | tag | - | Keys: `FluidName`, `StoredAmount`, `Capacity`, optional `Tag` (a private `tag` field with no public setter as written — effectively unused). |
| `isFluidEqual(IFluidTankLong cached)` | other tank | `boolean` | `cached != null && getFluid() == cached.getFluid()` — reference-equality on the `Fluid` singleton, not `FluidStack.isFluidEqual`. |
| `copy()` | - | `FluidTankLong` | New tank with same fluid/capacity/amount. |

**Example (constructed, not from repo):**
```java
FluidTankLong tank = new FluidTankLong(FluidRegistry.WATER, 64_000_000_000L);
long filled = tank.fill(FluidRegistry.WATER, 8_000L, true);
FluidStack drained = tank.drain(1_000L, true);
```

### `com.cleanroommc.modularui.utils.fluid.IFluidTanksHandler`

```java
public interface IFluidTanksHandler
```

Multi-tank container contract (the `long`-capacity analogue of Forge's multi-tank `IFluidHandler` pattern), addressed by tank index.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getTanks()` | - | `int` | Tank count. |
| `getFluidStackInTank(int tank)` | index | nullable `FluidStack` | |
| `getFluidInTank(int tank)` | index | `Fluid` | Type only, no amount. |
| `getFluidTank(int tank)` | index | `IFluidTankLong` | Direct access to the underlying tank object. |
| `fill(int tank, Fluid fluid, long amount, boolean simulate)` | target tank, fluid, amount, simulate flag | nullable `FluidStack` | Note: `simulate`, not `doFill` — opposite polarity convention from `IFluidTankLong.fill`. |
| `drain(int tank, long amount, boolean simulate)` | tank, amount, simulate | nullable `FluidStack` | |
| `getTankCapacity(int tank)` / `getRealTankCapacity(int tank)` | index | `long` | |
| `getTankStoredAmount(int tank)` | index | `long` | |
| `isItemValid(int slot, FluidStack stack)`* — actually `isFluidValid(int slot, FluidStack stack)` | slot, candidate stack | `boolean` | Default `true`. |
| `getFluids()` | - | `List<FluidStack>` (default) | Collects `getFluidStackInTank(i)` for every tank. |
| `setFluidInTank(int tank, Fluid fluid, long amount)` | tank, fluid, amount | - | |
| `setFluidInTank(int tank, Fluid fluid)` (default) | tank, fluid | - | amount `0`. |
| `setFluidInTank(int tank, IFluidTankLong fluid)` (default) | tank, source tank (nullable) | - | Copies type+amount from another tank (or clears if `null`). |

### `com.cleanroommc.modularui.utils.fluid.FluidTanksHandler`

```java
public class FluidTanksHandler implements IFluidTanksHandler
```

Straightforward `List<IFluidTankLong>`-backed implementation of `IFluidTanksHandler`.

| Constructor | Params | Notes |
|---|---|---|
| `FluidTanksHandler(IFluidTankLong tank)` | single existing tank | Wraps it in a `Collections.singletonList`. |
| `FluidTanksHandler(int tankAmount, long capacity)` | tank count, per-tank capacity | Creates `tankAmount` fresh `FluidTankLong`s all sharing the same capacity. |

All `IFluidTanksHandler` methods are one-liners delegating to `fluids.get(tank)`. **Gotcha:** no bounds checking — an out-of-range `tank` index throws `IndexOutOfBoundsException` from the underlying `List`, not a friendlier error.

**Example (constructed, not from repo):**
```java
FluidTanksHandler tanks = new FluidTanksHandler(3, 16_000L);
tanks.fill(0, FluidRegistry.LAVA, 4_000L, false);
```

### `com.cleanroommc.modularui.utils.fluid.IOverflowableTank`

```java
public interface IOverflowableTank
```

Tiny marker/capability interface: `int getRealCapacity()` — the "true" capacity, distinct from whatever `getCapacity()` reports when overflow is allowed to exceed it. Implemented by `FluidStackTank`; consumed by `FluidInteractions.getRealCapacity(IFluidTank)` to look past an artificially-inflated `getCapacity()`.

### `com.cleanroommc.modularui.utils.fluid.FluidStackTank`

```java
public class FluidStackTank implements IFluidTank, IOverflowableTank
```

An `IFluidTank` view over an arbitrary external `FluidStack` getter/setter pair (e.g. a field on a tile entity) rather than owning its own storage — lets any `Supplier<FluidStack>`/`Consumer<FluidStack>` be exposed as a tank, with optional "overflow" (report `Integer.MAX_VALUE` capacity) and "prevent draining to empty / keep the `null`-vs-empty distinction" behaviors.

| Constructor | Params | Notes |
|---|---|---|
| `FluidStackTank(Supplier<FluidStack> getter, Consumer<FluidStack> setter, int capacity)` | accessor pair, fixed capacity | |
| `FluidStackTank(Supplier<FluidStack> getter, Consumer<FluidStack> setter, IntSupplier capacityGetter)` | accessor pair, dynamic capacity | e.g. capacity that depends on machine tier/upgrades. |

| Method | Params | Returns | Notes |
|---|---|---|---|
| `setAllowOverflow(boolean)` | flag | - | When `true`, `getCapacity()` reports `Integer.MAX_VALUE` and `fill`'s no-op-simulate branch (`getCanFillAmount()`) also reports unlimited — but `validateFluid()`/actual fill logic still clamps to `getRealCapacity()`. |
| `setPreventDraining(boolean)` | flag | - | When `true`, draining to `0` leaves the external stack's `amount` at `0` instead of setting the reference to `null` via `setter.accept(null)`. |
| `getFluid()`, `getFluidAmount()`, `getCapacity()`, `getInfo()` | - | `FluidStack`/`int`/`FluidTankInfo` | Standard `IFluidTank` reads via the getter. |
| `fill(FluidStack resource, boolean doFill)` | resource, commit | `int` | Mutates the *existing* `FluidStack` object in place when possible (`fluid.amount += ...`) rather than replacing it, calling `setter.accept(...)` only when creating a brand-new stack. |
| `drain(int maxDrain, boolean doDrain)` | amount, commit | `FluidStack` | Returns a **new** `FluidStack` copy of the drained amount; mutates the source's `amount` down, clearing the source via `setter.accept(null)` on empty unless `preventDraining`. |
| `getRealCapacity()` | - | `int` | From `capacityGetter`, ignoring `allowOverflow`. |
| `getCanFillAmount()` | - | `int` | Remaining room, `Integer.MAX_VALUE` if `allowOverflow` and non-empty. |
| `validateFluid()` | - | - | Clamps the external stack's `amount` down to `getRealCapacity()` if it has drifted over (e.g. after upgrades were removed, shrinking capacity) — must be called manually after such changes; not invoked automatically except from `fill()`. |

**Gotcha:** because `fill()` mutates the caller-owned `FluidStack` object returned by `getter`, don't hand it a `FluidStack` you expect to remain unmodified elsewhere.

**Example (constructed, not from repo):**
```java
FluidStack[] box = new FluidStack[1];
FluidStackTank tank = new FluidStackTank(() -> box[0], f -> box[0] = f, 4000);
tank.fill(new FluidStack(FluidRegistry.WATER, 1000), true);
```

### `com.cleanroommc.modularui.utils.fluid.ListFluidHandler`

```java
public class ListFluidHandler implements IFluidTanksHandler
```

Composes several `IFluidTanksHandler`s into one, presenting a single contiguous tank-index space — the fluid-side analogue of `item.CombinedInvWrapper`.

| Constructor | Params | Notes |
|---|---|---|
| `ListFluidHandler(Iterable<? extends IFluidTanksHandler> fluidHandlers)` | child handlers, in order | Tank indices are resolved by linear scan (`findFluidHandler`) each call — O(handlers), not cached. |

Every `IFluidTanksHandler` method delegates to `findFluidHandler(tank)` (which throws `RuntimeException` for an out-of-range index) then calls the same method on the resolved child with a re-based local index. **Gotcha:** `getFluidTank(int tank)` has an apparent bug — it resolves the owning handler/local-index pair via `findFluidHandler(tank)` but then calls `result.getLeft().getFluidTank(tank)` with the **original** (non-rebased) index instead of `result.getRight()`; this can throw/return the wrong tank for any handler after the first in the list.

**Example (constructed, not from repo):**
```java
ListFluidHandler combined = new ListFluidHandler(List.of(tanksHandlerA, tanksHandlerB));
```

### `com.cleanroommc.modularui.utils.fluid.FluidInteractions`

```java
public class FluidInteractions
```

Static helpers for extracting/inserting fluids to/from `ItemStack`s (fluid containers), bridging `IFluidContainerItem`, vanilla `FluidContainerRegistry`, and (if loaded) NEI's `StackInfo` fluid lookup.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getFluidForItem(ItemStack)` (static) | item stack (nullable) | nullable `FluidStack` | Tries, in order: `IFluidContainerItem.getFluid`, `FluidContainerRegistry.getFluidForFilledItem`, then (only if `ModularUI.Mods.NEI.isLoaded()`) `codechicken.nei.recipe.StackInfo.getFluid`. |
| `getFullFluidContainer(ItemStack itemStack, FluidStack fluidToFill)` (static) | empty/partial container, fluid to fill | nullable `ItemStack` | For `IFluidContainerItem`s, only returns a result if the container reaches *exactly* full capacity after filling a copy (`null` otherwise, i.e. no partial-fill result); for other items delegates to `FluidContainerRegistry.fillFluidContainer`. |
| `getEmptyFluidContainer(ItemStack)` (static) | filled container | nullable `ItemStack` | For `IFluidContainerItem`s, only returns a result if draining a copy removes *all* of the fluid; otherwise delegates to `FluidContainerRegistry.drainFluidContainer`. |
| `getRealCapacity(IFluidTank fluidTank)` (static) | any vanilla tank | `int` | Returns `((IOverflowableTank) fluidTank).getRealCapacity()` if the tank implements that marker interface, else falls back to `fluidTank.getCapacity()`. |

**Example (constructed, not from repo):**
```java
FluidStack contents = FluidInteractions.getFluidForItem(bucketStack);
```

---

## item

Package: `com.cleanroommc.modularui.utils.item`

A backport/reimplementation of Forge's (1.8+) `IItemHandler` capability API for 1.7.10 — `IItemHandler`/`IItemHandlerModifiable` mirror the capability interfaces field-for-field (both are additionally `@Optional.Interface`-bridged onto `com.gtnewhorizons.modularui.api.forge.IItemHandler`/`IItemHandlerModifiable` for cross-compat with GTNH's ModularUI fork when that mod is present). The rest of the package is concrete handlers/wrappers/helpers built on top of it, directly modeled on Forge's `ItemHandlerHelper`/`InvWrapper`/`ItemStackHandler`/etc.

**Which wrapper to pick:**
- **`InvWrapper`** — adapt an existing vanilla `IInventory` (e.g. a `TileEntity` implementing it, or `InventoryBasic`) to `IItemHandlerModifiable`.
- **`ItemStackHandler`** — brand-new standalone inventory backed by a `List<ItemStack>`, with NBT (de)serialization built in (`INBTSerializable`). The default choice for a tile entity's own storage (see `TestTile.storage` below).
- **`LimitingItemStackHandler`** — `ItemStackHandler` variant with a configurable non-64 per-slot stack-count limit.
- **`RangedWrapper`** — expose only a contiguous sub-range of another `IItemHandlerModifiable`'s slots, re-basing indices automatically. Building block for the `Player*` wrappers below.
- **`CombinedInvWrapper`** — concatenate several `IItemHandlerModifiable`s into one contiguous slot space (also `Iterable<IItemHandlerModifiable>` over its children).
- **`PlayerMainInvWrapper`** — the player's hotbar+main inventory only (36 slots, `RangedWrapper` over `InvWrapper(InventoryPlayer)`), with pickup-style stack-changed animation triggering.
- **`PlayerArmorInvWrapper`** — the player's 4 armor slots only, insertion additionally validated against `Item.isValidArmor`.
- **`PlayerInvWrapper`** — main + armor combined (`CombinedInvWrapper` of the previous two) — the "whole player inventory" view.
- **`EmptyHandler`** — zero-slot singleton null-object, used internally by `CombinedInvWrapper`/`RangedWrapper` when an index resolves to no handler.
- **`SlotItemHandler`** — a vanilla `Slot` bound to an `IItemHandler` instead of an `IInventory`, for use in a `Container`.

### `com.cleanroommc.modularui.utils.item.IItemHandler`

```java
@Optional.Interface(iface = "com.gtnewhorizons.modularui.api.forge.IItemHandler", modid = "modularui")
public interface IItemHandler extends com.gtnewhorizons.modularui.api.forge.IItemHandler
```

The core read/insert/extract contract every item-storage abstraction in this package implements — a straight reimplementation of Forge's capability `IItemHandler`.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getSlots()` | - | `int` | Slot count. |
| `getStackInSlot(int slot)` | slot | nullable `ItemStack` | **Must not be modified** by callers — implementations may throw if they detect mutation. Result's `stackSize` may legally exceed `getMaxStackSize()`. |
| `insertItem(int slot, ItemStack stack, boolean simulate)` | slot, stack (not modified by this call), simulate flag | nullable `ItemStack` remainder | Empty/`null` return means fully accepted. May return the same input reference if nothing changed, or a new stack otherwise. |
| `extractItem(int slot, int amount, boolean simulate)` | slot, requested amount, simulate | nullable `ItemStack` | Result size is `<= amount` and `<= getMaxStackSize()`; safe for the caller to mutate afterward. |
| `getSlotLimit(int slot)` | slot | `int` | Max stack size the slot itself allows (independent of item's own max stack size). |
| `isItemValid(int slot, ItemStack stack)` (default) | slot, candidate stack | `boolean` | `true` by default — a cheap pre-check ("never valid" vs "maybe valid, must simulate"), not a substitute for a real simulated `insertItem`. |
| `getStacks()` (default) | - | `List<ItemStack>` | Collects every slot via `getStackInSlot` — marked as not existing in the 1.12 API this was ported from. |
| `isSlotFromInventory(int index, IInventory inventory, int invIndex)` (default) | this handler's index, an `IInventory`, its index | `boolean` | `false` by default; overridden by `InvWrapper`/`RangedWrapper`/`PlayerMainInvWrapper` etc. to answer "does slot `index` here correspond to `invIndex` in `inventory`" — used by `PlayerSlotType` to classify slots without caring which wrapper layer produced them. |

### `com.cleanroommc.modularui.utils.item.IItemHandlerModifiable`

```java
@Optional.Interface(iface = "com.gtnewhorizons.modularui.api.forge.IItemHandlerModifiable", modid = "modularui")
public interface IItemHandlerModifiable extends IItemHandler, com.gtnewhorizons.modularui.api.forge.IItemHandlerModifiable
```

Adds direct slot overwrite on top of `IItemHandler` — needed by generic helper code (Forge's own item-handler helpers, `SlotItemHandler`) that must set a slot's contents without going through `insertItem`'s stacking/limit logic.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `setStackInSlot(int slot, ItemStack stack)` | slot, stack (nullable) | - | Not intended for general/mod use — implementations may throw if called unexpectedly. |

### `com.cleanroommc.modularui.utils.item.INBTSerializable`

```java
public interface INBTSerializable<T extends NBTBase>
```

Generic NBT persistence contract (`T serializeNBT()` / `void deserializeNBT(T nbt)`); `ItemStackHandler` implements this with `T = NBTTagCompound`.

### `com.cleanroommc.modularui.utils.item.ItemHandlerHelper`

```java
public class ItemHandlerHelper
```

Static helper functions for item-handler-level operations that don't belong to any single handler — mostly ported near-verbatim from Forge's own `ItemHandlerHelper`.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `insertItem(IItemHandler dest, ItemStack stack, boolean simulate)` (static) | handler, stack, simulate | nullable `ItemStack` remainder | Tries every slot `0..getSlots()-1` in order, stopping early once fully consumed. |
| `canItemStacksStack(ItemStack a, ItemStack b)` (static) | two stacks | `boolean` | Strict: `isItemEqual` (item+damage+NBT-presence) plus exact NBT tag equality. |
| `canItemStacksStackRelaxed(ItemStack a, ItemStack b)` (static) | two stacks | `boolean` | Looser: same item, respects `isStackable()`/subtype-damage/NBT presence+equality, but doesn't require `isItemEqual` exactly. |
| `copyStackWithSize(ItemStack, int size)` (static) | stack, new size | nullable `ItemStack` | `null` if `itemStack == null || size == 0`. |
| `insertItemStacked(IItemHandler, ItemStack, boolean simulate)` (static) | handler, stack, simulate | nullable `ItemStack` | Two passes: first tries to merge into slots with a "relaxed"-stackable existing item, then fills any empty slots — mimics vanilla pickup-merging behavior more closely than plain `insertItem`. |
| `giveItemToPlayer(EntityPlayer, ItemStack)` / `giveItemToPlayer(EntityPlayer, ItemStack, int preferredSlot)` (static) | player, stack, optional preferred slot | - | Tries the preferred slot first (if given), then `insertItemStacked` into a fresh `PlayerMainInvWrapper`; plays the vanilla item-pickup sound if anything was consumed, and spawns an `EntityItem` drop for any leftover (server-side only). |
| `calcRedstoneFromInventory(IItemHandler)` (static) | handler (nullable) | `int` | Vanilla-style comparator/redstone-signal-strength calculation (0 for `null` handler) based on per-slot fill proportion. |

### `com.cleanroommc.modularui.utils.item.EmptyHandler`

```java
public class EmptyHandler implements IItemHandlerModifiable
```

Null-object singleton: `getSlots() == 0`, every read returns `null`/`0`/`false`, `insertItem` always rejects (returns the input stack unchanged), `setStackInSlot` is a no-op. Access via `EmptyHandler.INSTANCE` (typed as `IItemHandlerModifiable`); used by `CombinedInvWrapper.getHandlerFromIndex` as the fallback for an out-of-range index instead of returning `null`/throwing.

### `com.cleanroommc.modularui.utils.item.InvWrapper`

```java
public class InvWrapper implements IItemHandlerModifiable
```

Adapts any vanilla `IInventory` to `IItemHandlerModifiable`, reimplementing stacking/limit logic on top of `IInventory`'s cruder `getStackInSlot`/`setInventorySlotContents`/`decrStackSize` API.

| Member | Params | Returns | Notes |
|---|---|---|---|
| `InvWrapper(IInventory inv)` | inventory to wrap | - | `Objects.requireNonNull`s it. |
| `getInv()` | - | `IInventory` | `@Deprecated` (still the only accessor for the wrapped inventory). |
| `equals(Object)`/`hashCode()` | - | - | Delegate to the wrapped `IInventory`'s identity — two `InvWrapper`s over the same `IInventory` are equal. |
| `getSlots()`, `getStackInSlot(int)` | - | `int`/`ItemStack` | Delegate directly. |
| `insertItem(slot, stack, simulate)` | slot, stack, simulate | nullable remainder | Respects `IInventory.isItemValidForSlot`, per-slot limit (`min(stackSize max, getSlotLimit)`), merges into an existing compatible stack or starts a new one, calls `markDirty()` on real writes. |
| `extractItem(slot, amount, simulate)` | slot, amount, simulate | nullable `ItemStack` | Simulated path returns a copy; real path uses `IInventory.decrStackSize` + `markDirty()`. |
| `setStackInSlot(slot, stack)` | slot, stack | - | `IInventory.setInventorySlotContents`. |
| `getSlotLimit(slot)` | slot | `int` | `IInventory.getInventoryStackLimit()` (same for every slot — `IInventory` has no per-slot limit concept). |
| `isItemValid(slot, stack)` | slot, stack | `boolean` | `IInventory.isItemValidForSlot`. |
| `isSlotFromInventory(index, inventory, invIndex)` | see `IItemHandler` | `boolean` | `true` iff `inventory == this.inv && index == invIndex` and in range. |

### `com.cleanroommc.modularui.utils.item.ItemStackHandler`

```java
public class ItemStackHandler implements IItemHandlerModifiable, INBTSerializable<NBTTagCompound>
```

Standalone `List<ItemStack>`-backed handler — the default choice for "a tile entity/item needs its own inventory storage." Slot limit is a fixed `64` unless subclassed (see `LimitingItemStackHandler`, and `TestTile`'s anonymous-subclass override below).

| Constructor | Params | Notes |
|---|---|---|
| `ItemStackHandler()` | - | 1 slot. |
| `ItemStackHandler(int size)` | slot count | All slots `null`-initialized. |
| `ItemStackHandler(List<ItemStack> stacks)` / `ItemStackHandler(ItemStack[] stacks)` | pre-built contents | Wraps/adopts the given list (array variant via `Arrays.asList`, so it's a fixed-size view over the array). |

| Method | Params | Returns | Notes |
|---|---|---|---|
| `setSize(int size)` | new size | - | **Discards all current contents** and reinitializes to `size` empty slots. |
| `setStackInSlot`, `getStackInSlot`, `getSlots` | see `IItemHandlerModifiable` | | `validateSlotIndex` throws `RuntimeException` (not `IndexOutOfBoundsException`) for bad indices. |
| `insertItem(slot, stack, simulate)` | slot, stack, simulate | nullable remainder | Limit = `min(getSlotLimit(slot), stack.getMaxStackSize())`; calls `onContentsChanged(slot)` on real writes. |
| `extractItem(slot, amount, simulate)` | slot, amount, simulate | nullable `ItemStack` | Extracts up to `min(amount, existing.getMaxStackSize())`. |
| `getSlotLimit(slot)` | slot | `int` | `64`, override to change (see `getStackLimit` which further clamps by the item's own max stack size). |
| `isItemValid(slot, stack)` | slot, stack | `boolean` | `true` unconditionally — override for slot-restricted inventories. |
| `serializeNBT()` / `deserializeNBT(NBTTagCompound)` | - / tag | `NBTTagCompound` / - | Stores `Items` (list of `{Slot, Count, <item NBT>}`) + `Size`; `deserializeNBT` calls `setSize(...)` first (so it **resizes to match the saved data**, discarding whatever size the handler had before loading) then calls `onLoad()` at the end. |
| `onLoad()`, `onContentsChanged(int slot)` | - (protected, empty by default) | - | Override points for reacting to NBT load / slot mutation (e.g. `TileEntity.markDirty()`). |

**Example (from `test/TestTile.java`):**
```java
private final ItemStackHandler storage = new ItemStackHandler(9);
private final ItemStackHandler oversizedStorage = new ItemStackHandler(3) {
    @Override
    public int getSlotLimit(int slot) {
        return 10000;
    }
};

@Override
public void writeToNBT(@NotNull NBTTagCompound compound) {
    super.writeToNBT(compound);
    compound.setTag("item_inv", this.storage.serializeNBT());
}

@Override
public void readFromNBT(@NotNull NBTTagCompound compound) {
    super.readFromNBT(compound);
    this.storage.deserializeNBT(compound.getCompoundTag("item_inv"));
}
```

### `com.cleanroommc.modularui.utils.item.LimitingItemStackHandler`

```java
public class LimitingItemStackHandler extends ItemStackHandler
```

`ItemStackHandler` with a single configurable per-slot stack limit applied uniformly to every slot (overrides `getSlotLimit` to return a fixed `int limit` regardless of `slot`) instead of the fixed `64`. Constructors mirror `ItemStackHandler`'s, each with an added trailing `int limit` parameter: `LimitingItemStackHandler(int limit)`, `(int slots, int limit)`, `(List<ItemStack> stacks, int limit)`, `(ItemStack[] stacks, int limit)`.

**Example (constructed, not from repo):**
```java
ItemStackHandler handler = new LimitingItemStackHandler(4, 16); // 4 slots, max 16 items each
```

### `com.cleanroommc.modularui.utils.item.RangedWrapper`

```java
public class RangedWrapper implements IItemHandlerModifiable
```

Exposes only slots `[minSlot, maxSlotExclusive)` of another `IItemHandlerModifiable`, transparently shifting indices. Building block for `PlayerMainInvWrapper`/`PlayerArmorInvWrapper`.

| Member | Params | Returns | Notes |
|---|---|---|---|
| `RangedWrapper(IItemHandlerModifiable compose, int minSlot, int maxSlotExclusive)` | wrapped handler, range | - | `Preconditions.checkArgument(maxSlotExclusive > minSlot, ...)`. |
| `getSlots()` | - | `int` | `maxSlot - minSlot`. |
| `getStackInSlot`, `insertItem`, `extractItem`, `setStackInSlot`, `getSlotLimit`, `isItemValid` | local slot index + usual params | usual returns | All re-base `slot -> slot + minSlot` after a `checkSlot` bounds check, then delegate to `compose`. |
| `getCompose()` | - | `IItemHandlerModifiable` | The wrapped handler. |
| `isSlotFromInventory(index, inventory, invIndex)` | see `IItemHandler` | `boolean` | Delegates to `compose.isSlotFromInventory(index + minSlot, inventory, invIndex)`. |

**Gotcha:** `checkSlot` only checks the upper bound (`localSlot + minSlot < maxSlot`); a negative `localSlot` is never rejected here and would be passed straight through to `compose` with a possibly-out-of-range or wrapped-negative rebased index.

### `com.cleanroommc.modularui.utils.item.CombinedInvWrapper`

```java
public class CombinedInvWrapper implements IItemHandlerModifiable, Iterable<IItemHandlerModifiable>
```

Concatenates multiple `IItemHandlerModifiable`s into one contiguous slot space (precomputes a `baseIndex[]` cumulative-offset table at construction).

| Member | Params | Returns | Notes |
|---|---|---|---|
| `CombinedInvWrapper(IItemHandlerModifiable... itemHandler)` | child handlers, in order | - | |
| `getSlots()` | - | `int` | Total across all children. |
| `getStackInSlot`, `insertItem`, `extractItem`, `setStackInSlot`, `getSlotLimit`, `isItemValid` | global slot index + usual params | usual returns | Resolve which child handler owns the index (`getIndexForSlot`) and the local slot within it (`getSlotFromIndex`), then delegate; an index past all handlers resolves to `EmptyHandler.INSTANCE` rather than throwing. |
| `iterator()` | - | `Iterator<IItemHandlerModifiable>` | Iterates the child handlers themselves (not their contents) via `Iterators.forArray`. |

**Example (adapted from `utils/item/PlayerInvWrapper.java`):**
```java
public class PlayerInvWrapper extends CombinedInvWrapper {
    public PlayerInvWrapper(InventoryPlayer inv) {
        super(new PlayerMainInvWrapper(inv), new PlayerArmorInvWrapper(inv));
    }
}
```

### `com.cleanroommc.modularui.utils.item.PlayerMainInvWrapper`

```java
public class PlayerMainInvWrapper extends RangedWrapper
```

The player's hotbar + main inventory (slots `0` through `inv.mainInventory.length - 1`, i.e. 36 slots, **excluding armor**), wrapping `new InvWrapper(inv)`.

| Member | Params | Returns | Notes |
|---|---|---|---|
| `PlayerMainInvWrapper(InventoryPlayer inv)` | player's inventory | - | |
| `insertItem(slot, stack, simulate)` | slot, stack, simulate | nullable remainder | After a normal insert, if the slot's contents actually changed: client-side triggers a short pickup "pop" animation (`inSlot.animationsToGo = 5`); server-side (`EntityPlayerMP`) forces `openContainer.detectAndSendChanges()` so the client sees the update immediately rather than waiting for the next tick's diff. |
| `getInventoryPlayer()` | - | `InventoryPlayer` | |

Used directly by `ItemHandlerHelper.giveItemToPlayer` and by `PanelSyncManager`'s player-inventory-binding machinery (`SlotFunction` in `value/sync/PanelSyncManager.java` takes a `PlayerMainInvWrapper` per-slot).

### `com.cleanroommc.modularui.utils.item.PlayerArmorInvWrapper`

```java
public class PlayerArmorInvWrapper extends RangedWrapper
```

The player's 4 armor slots (offset range `[mainInventory.length, mainInventory.length + armorInventory.length)`), wrapping the same underlying `InvWrapper(inv)`.

| Member | Params | Returns | Notes |
|---|---|---|---|
| `PlayerArmorInvWrapper(InventoryPlayer inv)` | player's inventory | - | |
| `insertItem(slot, stack, simulate)` | local slot `0..3`, stack, simulate | `ItemStack` | Only delegates to `super.insertItem` if `stack.getItem().isValidArmor(stack, slot, player)`; otherwise rejects (returns the input stack unchanged) even if the underlying `RangedWrapper`/`IInventory` would have accepted it. |
| `getInventoryPlayer()` | - | `InventoryPlayer` | |

### `com.cleanroommc.modularui.utils.item.PlayerInvWrapper`

```java
public class PlayerInvWrapper extends CombinedInvWrapper
```

`CombinedInvWrapper` of `PlayerMainInvWrapper` + `PlayerArmorInvWrapper` — "the whole player inventory" (main+hotbar then armor, contiguous). No members beyond the constructor:

```java
public PlayerInvWrapper(InventoryPlayer inv) {
    super(new PlayerMainInvWrapper(inv), new PlayerArmorInvWrapper(inv));
}
```

**Inferred:** prefer this over manually combining the two wrappers yourself when you need "everything the player carries except off-hand/baubles" as a single `IItemHandlerModifiable`.

### `com.cleanroommc.modularui.utils.item.SlotItemHandler`

```java
public class SlotItemHandler extends Slot
```

Bridges an `IItemHandler` into vanilla's `Slot`/`Container` system — construct one per GUI slot backed by an item handler instead of an `IInventory`. Uses a shared empty `InventoryBasic` (`emptyInventory`) as the required-but-unused base `Slot` constructor argument.

| Member | Params | Returns | Notes |
|---|---|---|---|
| `SlotItemHandler(IItemHandler itemHandler, int index, int xPosition, int yPosition)` | handler, slot index, GUI pixel position | - | |
| `isItemValid(ItemStack)` | candidate stack | `boolean` | First checks `itemHandler.isItemValid(index, stack)` (cheap pre-check), then does a *real* simulated insert (temporarily clearing the slot on `IItemHandlerModifiable` handlers to avoid stacking-limit false negatives) to see if anything would actually be accepted. |
| `getStack()` / `putStack(ItemStack)` | - / stack | `ItemStack` / - | `putStack` requires the handler to be `IItemHandlerModifiable` (unchecked cast — throws `ClassCastException` on a plain `IItemHandler`) and calls `onSlotChanged()`. |
| `getSlotStackLimit()` | - | `int` | `itemHandler.getSlotLimit(index)`. |
| `getItemStackLimit(ItemStack stack)` | candidate stack | `int` | How many of `stack` could fit in this slot right now, computed via a simulated max-size insert (same clear-simulate-restore trick for modifiable handlers). |
| `canTakeStack(EntityPlayer)` | player | `boolean` | Best-effort: empty slot, or a simulated 1-item extraction succeeds. |
| `decrStackSize(int amount)` | amount | nullable `ItemStack` | Real (non-simulated) `extractItem`. |
| `getItemHandler()` | - | `IItemHandler` | |
| `isSameInventory(Slot other)` | other slot | `boolean` | `true` iff `other` is also a `SlotItemHandler` over the same handler instance. |
| `isSlotInInventory(IInventory, int invIndex)` | inventory, index | `boolean` | Delegates to `itemHandler.isSlotFromInventory(index, inventory, invIndex)`. |
| `onSlotChange(ItemStack, ItemStack)` | - | - | No-op override (vanilla hook not needed here). |

**Example (constructed, not from repo — vanilla `Container`, contrast with this project's own `widgets.slot.ModularSlot`/`ItemSlot` which is the actual UI-facing slot type used in `test/TestTile.java`):**
```java
this.addSlotToContainer(new SlotItemHandler(itemHandler, 0, 8, 18));
```

---

## math

Package: `com.cleanroommc.modularui.utils.math`

Two small helper classes plugging into the `com.ezylang.evalex` expression-evaluation library (used elsewhere in ModularUI for things like dynamic size/position expressions). **Inferred:** these back a small custom-expression feature (e.g. layout expressions), not directly demonstrated in `test/`.

### `com.cleanroommc.modularui.utils.math.CustomDataAccessor`

```java
public class CustomDataAccessor implements DataAccessorIfc
```

A `evalex` `DataAccessorIfc` (variable-name → `EvaluationValue` lookup table) backed by an `Object2ObjectOpenHashMap`, with optional case-insensitive variable names.

| Constructor/Method | Params | Returns | Notes |
|---|---|---|---|
| `CustomDataAccessor(boolean caseSensitive)` | whether variable lookups are case-sensitive | - | |
| `isCaseSensitive()` | - | `boolean` | |
| `getData(String variable)` | variable name | `EvaluationValue` | Case-sensitive: exact-key lookup. Case-insensitive: lower-cases the query before lookup. |
| `setData(String variable, EvaluationValue value)` | name, value | - | Case-sensitive: single put. Case-insensitive: stores under the original key **and** both its lowercase and uppercase forms (three map entries for a mixed-case name), so any-case lookups later succeed via `getData`'s lowercasing. |

**Example (constructed, not from repo):**
```java
CustomDataAccessor accessor = new CustomDataAccessor(false);
accessor.setData("width", EvaluationValue.numberOfString("10", MathContext.DECIMAL32));
Expression expr = new Expression("width * 2").withDataAccessor(accessor);
```

### `com.cleanroommc.modularui.utils.math.PostfixPercentOperator`

```java
@PostfixOperator(precedence = OperatorIfc.OPERATOR_PRECEDENCE_MULTIPLICATIVE - 1)
public class PostfixPercentOperator extends AbstractOperator
```

Custom `evalex` postfix operator implementing a trailing `%` — e.g. lets expressions write `50%` instead of `0.5`. Registered with the `evalex` engine as a custom operator (registration site not in this file — `@PostfixOperator` is metadata evalex's operator-scanning reads).

| Member | Params | Returns | Notes |
|---|---|---|---|
| `HUNDRED` | `static final BigDecimal` | `100` | |
| `evaluate(Expression, Token operatorToken, EvaluationValue... operands)` | the single postfix operand | `EvaluationValue` | Divides the operand's numeric value by 100 using the expression's configured `MathContext`; throws `EvaluationException.ofUnsupportedDataTypeInOperation` if the operand isn't numeric. |

**Example (constructed, not from repo):**
```java
Expression expr = new Expression("50%").withOperator(new PostfixPercentOperator()); // evaluates to 0.5
```

---

## serialization

Package: `com.cleanroommc.modularui.utils.serialization`

Tiny functional-interface toolkit for reading/writing objects to a Netty `PacketBuffer`/`ByteBuf` (Minecraft's network packet buffer), used throughout the sync-value system to describe how to (de)serialize arbitrary payload types over the network.

### `com.cleanroommc.modularui.utils.serialization.IByteBufSerializer`

```java
public interface IByteBufSerializer<T>
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `serialize(PacketBuffer buffer, T value)` | buffer, value | - (`throws IOException`) | Core write method. |
| `serializeSafe(PacketBuffer buffer, T value)` (default) | buffer, value | - | Catches `IOException` and logs via `ModularUI.LOGGER.catching(e)` instead of propagating. |
| `wrapNullSafe(IByteBufSerializer<T> serializer)` (static) | serializer | `IByteBufSerializer<T>` | Wraps it to first write a null-flag boolean, only calling the delegate serializer if `value != null`. |

### `com.cleanroommc.modularui.utils.serialization.IByteBufDeserializer`

```java
public interface IByteBufDeserializer<T>
```

Mirror of `IByteBufSerializer` for reading.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `deserialize(PacketBuffer buffer)` | buffer | `T` (`throws IOException`) | Core read method. |
| `deserializeSafe(PacketBuffer buffer)` (default) | buffer | nullable `T` | Catches `IOException`, logs, returns `null`. |
| `wrapNullSafe(IByteBufDeserializer<T> deserializer)` (static) | deserializer | `IByteBufDeserializer<T>` | Reads the null-flag boolean first; returns `null` without invoking the delegate if set — pairs with `IByteBufSerializer.wrapNullSafe`. |

### `com.cleanroommc.modularui.utils.serialization.IEquals`

```java
public interface IEquals<T>
```

Custom pluggable equality test (used where `Object.equals` isn't appropriate/available, e.g. comparing `FluidStack`s or `ItemStack`s by game-semantic equality rather than reference/field equality).

| Method | Params | Returns | Notes |
|---|---|---|---|
| `areEqual(T a, T b)` | two non-null objects | `boolean` | Core test — contract explicitly states params are not null. |
| `wrapNullSafe(IEquals<T> equals)` (static) | tester | `IEquals<T>` | Wraps to handle nulls: if either is `null`, returns `a == b` (both-null is equal, one-null is not); otherwise delegates. |
| `defaultTester()` (static) | - | `IEquals<T>` | Returns `Objects::equals`. |

### `com.cleanroommc.modularui.utils.serialization.IByteBufAdapter`

```java
public interface IByteBufAdapter<T> extends IByteBufSerializer<T>, IByteBufDeserializer<T>, IEquals<T>
```

Combines all three of the above into a single reusable "how to fully handle type `T` over the network" object — this is the type actually used to declare a sync value's wire format elsewhere in ModularUI (e.g. `GenericSyncValue`).

### `com.cleanroommc.modularui.utils.serialization.ByteBufAdapters`

```java
public class ByteBufAdapters
```

Static registry of ready-made `IByteBufAdapter` constants for common types, plus the `makeAdapter` factory used to build them.

| Member | Type | Notes |
|---|---|---|
| `ITEM_STACK` | `IByteBufAdapter<ItemStack>` | `PacketBuffer::readItemStackFromBuffer`/`writeItemStackToBuffer`, equality via `ItemStack::areItemStacksEqual`. |
| `FLUID_STACK` | `IByteBufAdapter<FluidStack>` | Via `NetworkUtils::readFluidStack`/`writeFluidStack`, equality via `FluidStack::isFluidStackIdentical`. |
| `NBT` | `IByteBufAdapter<NBTTagCompound>` | Via `PacketBuffer` NBT read/write; no custom equality tester (falls back to `Objects.equals`). |
| `STRING` | `IByteBufAdapter<String>` | Via `NetworkUtils::readStringSafe`/`writeStringSafe`. |
| `BYTE_BUF` | `IByteBufAdapter<ByteBuf>` | Via `NetworkUtils::readByteBuf`/`writeByteBuf`. |
| `PACKET_BUFFER` | `IByteBufAdapter<PacketBuffer>` | Reads via `NetworkUtils::readPacketBuffer`, writes via `NetworkUtils::writeByteBuf` (asymmetric helper names, same underlying byte data). |
| `INT`, `LONG`, `FLOAT`, `DOUBLE`, `BOOL`, `BYTE`, `SHORT`, `CHAR` | `IByteBufAdapter<Integer/Long/Float/Double/Boolean/Byte/Short/Character>` | Thin wrappers over the matching `PacketBuffer` primitive read/write methods, no custom equality (`Objects.equals` via boxed-primitive equality). |
| `BYTE_ARR` | `IByteBufAdapter<byte[]>` | Length-prefixed (`writeVarIntToBuffer`/`readVarIntFromBuffer`) raw bytes; custom `areEqual` does an element-wise compare (arrays don't have value-based `equals`). |
| `LONG_ARR` | `IByteBufAdapter<long[]>` | Same pattern for `long[]`. |
| `BIG_INT` | `IByteBufAdapter<BigInteger>` | Serializes via `BYTE_ARR` of `toByteArray()`. |
| `BIG_DECIMAL` | `IByteBufAdapter<BigDecimal>` | Serializes as `BIG_INT` (unscaled value) + a `VarInt` scale. |
| `makeAdapter(IByteBufDeserializer<T>, IByteBufSerializer<T>, IEquals<T> tester)` (static) | deserializer, serializer, optional equality tester (nullable) | `IByteBufAdapter<T>` | Factory combining the three functional pieces into one adapter; `tester == null` falls back to `Objects::equals` inside `areEqual`. |

**Example (constructed, not from repo — pattern mirrors the constants above):**
```java
public static final IByteBufAdapter<UUID> UUID_ADAPTER = ByteBufAdapters.makeAdapter(
        buffer -> new UUID(buffer.readLong(), buffer.readLong()),
        (buffer, uuid) -> {
            buffer.writeLong(uuid.getMostSignificantBits());
            buffer.writeLong(uuid.getLeastSignificantBits());
        },
        UUID::equals);
```
