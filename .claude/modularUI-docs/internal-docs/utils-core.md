# `utils` package reference (core, non-subpackage classes)

Package: `com.cleanroommc.modularui.utils`

This covers only the files directly under `utils/` (not `fakeworld`, `fluid`, `item`, `math`, `serialization`). These are general-purpose helpers used throughout the library: color math, alignment constants, JSON builders, math/array helpers, collection adapters, and a handful of Forge/LWJGL interop shims. Several classes here are legacy/deprecated (`Matrix4f`, `Vector3f`) kept for backport compatibility — prefer `org.joml.*` equivalents in new code.

---

## `com.cleanroommc.modularui.utils.Alignment`

Represents a fractional (x, y) anchor point (0..1 on each axis) used for widget/text/icon positioning. Ships 9 named constants plus `MainAxis`/`CrossAxis` enums used by `Flow` layout.

```java
public class Alignment {
    public final float x, y;
    public static final Alignment TopLeft, TopCenter, TopRight,
            CenterLeft, Center, CenterRight,
            BottomLeft, BottomCenter, BottomRight;
    public static final Alignment START, CENTER, END; // = TopLeft, Center, BottomRight
    public static final Alignment[] ALL;     // all 9, row-major
    public static final Alignment[] CORNERS; // the 4 corners

    public Alignment(float x, float y);

    public enum MainAxis { START, CENTER, END, SPACE_BETWEEN, SPACE_AROUND }
    public enum CrossAxis { START, CENTER, END }
    public static class Json implements JsonDeserializer<Alignment>, JsonSerializer<Alignment> { ... }
}
```

- `Alignment(float x, float y)` — public constructor for custom anchor points. **Gotcha:** instances made this way are *not* registered in the internal name lookup map, so `Alignment.Json` cannot serialize them back to a string name — they round-trip as `{"x":.., "y":..}` objects instead. Only the 9 built-in constants (and their name variants: `TopLeft`, `top_left`, `TL`, `tl`) are name-resolvable.
- `Json` — Gson `TypeAdapter` registered on `JsonHelper.GSON` for the `Alignment` type. Deserializes either a string name (case variants above) or `{"x":..,"y":..}`. Serializes back to a name if the instance is one of the 9 constants (identity check), else to an `{x,y}` object.
- `MainAxis` / `CrossAxis` — used by `Flow.mainAxisAlignment(...)` / `.crossAxisAlignment(...)`. `SPACE_BETWEEN`/`SPACE_AROUND` (main axis only) fall back to `CENTER` behavior for a single child.

**Example (adapted from `test/TestTile.java` and `test/GLTestGui.java`):**
```java
Flow.column()
        .mainAxisAlignment(Alignment.MainAxis.SPACE_AROUND)
        .child(Flow.row()
                .crossAxisAlignment(Alignment.CrossAxis.START));

tooltip.addLine(GuiTextures.MUI_LOGO.asIcon().size(50).alignment(Alignment.TopCenter));
```

---

## `com.cleanroommc.modularui.utils.AssetHelper`

Thin wrapper around Minecraft's `IResourceManager` for looking up resources by `ResourceLocation`.

```java
public class AssetHelper {
    public static @Nullable IResource findAsset(ResourceLocation resourceLocation);
    public static List<IResource> findAssets(String domain, String file);
    public static List<IResource> findAssets(String file); // scans all resource domains
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `findAsset(ResourceLocation)` | location | resource or `null` | Swallows `IOException`, returns `null` instead. |
| `findAssets(String domain, String file)` | mod domain, path | all matching resources across resource packs | Swallows `IOException`, returns empty list. |
| `findAssets(String file)` | path only | matches across *every* loaded domain | Iterates all `getResourceDomains()` — O(domains), only use for one-off lookups (e.g. tooling), not per-frame. |

---

## `com.cleanroommc.modularui.utils.BooleanConsumer`

```java
public interface BooleanConsumer {
    void accept(boolean value);
}
```

Primitive-boolean analog of `Consumer<Boolean>` (no autoboxing). **Appears in public API signatures** you will actually type: `BoolValue.Dynamic(BooleanSupplier, BooleanConsumer)`, `BooleanSyncValue`'s constructors, and `SyncHandlers.bool(BooleanSupplier, BooleanConsumer)`.

**Example (constructed, not from repo):**
```java
boolean[] flag = {false};
BooleanConsumer setter = v -> flag[0] = v;
setter.accept(true);
```

---

## `com.cleanroommc.modularui.utils.Color`

Static color-math utility. **All `int` color values are packed as AARRGGBB.** Covers construction (RGB/HSV/HSL/CMYK), component extraction/replacement, interpolation, hex/JSON parsing, and a full Material-Design-style palette of `ColorShade` constants.

```java
public class Color {
    public static final int TEXT_COLOR_DARK; // 0xFF404040
    public static final ColorShade WHITE, BLACK, RED, RED_ACCENT, PINK, PINK_ACCENT, PURPLE, PURPLE_ACCENT,
            DEEP_PURPLE, DEEP_PURPLE_ACCENT, INDIGO, INDIGO_ACCENT, BLUE, BLUE_ACCENT, LIGHT_BLUE, LIGHT_BLUE_ACCENT,
            CYAN, CYAN_ACCENT, TEAL, TEAL_ACCENT, GREEN, GREEN_ACCENT, LIGHT_GREEN, LIGHT_GREEN_ACCENT,
            LIME, LIME_ACCENT, YELLOW, YELLOW_ACCENT, AMBER, AMBER_ACCENT, ORANGE, ORANGE_ACCENT,
            DEEP_ORANGE, DEEP_ORANGE_ACCENT, BROWN, GREY, BLUE_GREY;
    // ... static methods below
}
```

### Construction

| Method | Params | Returns | Notes |
|---|---|---|---|
| `rgb(int r, int g, int b)` | 0-255 each | ARGB, alpha=255 | |
| `argb(int r, int g, int b, int a)` | 0-255 each | ARGB | Bit-packs directly. |
| `argb(float r, float g, float b, float a)` | 0-1 each | ARGB | Truncates (`(int)(v*255)`), no rounding. |
| `rgb(float r, float g, float b)` | 0-1 each | ARGB, alpha=255 | |
| `ofHSV(float h, float s, float v[, float a])` | h: 0-360 (wraps), s/v/a: 0-1 | ARGB | |
| `ofHSL(float h, float s, float l[, float a])` | same ranges | ARGB | |
| `ofCMYK(float c, float m, float y, float k[, float a])` | 0-1 each | ARGB | |
| `ofJson(JsonElement)` | primitive (string, parsed via `parseString`) or object | ARGB | Object form accepts `r/g/b`, or `h/s` + (`v` xor `l`), or `c/m/y/k`, plus `a`/`alpha`. Throws `JsonParseException` if groups are mixed (e.g. RGB + HSV keys together) or empty. |
| `parseString(String)` / `parseString(String, int fallback, boolean silent)` | see below | ARGB | See gotcha. |

**Gotcha — `parseString` format:** Accepts a decimal/hex(`0x`/`#`)/octal int literal, `"invisible"` (→ white with alpha 0), or a `ColorShade` name optionally suffixed `:<index>` (negative = darker, positive = brighter, clamped to available shades). Example: `"deep_purple:-3"` → `0xFF4527A0`. On parse failure, returns `fallback` and (unless `silent`) logs via `ModularUI.LOGGER`.

### Component access / mutation

| Method | Notes |
|---|---|
| `getRed/Green/Blue/Alpha(argb)` | → `int` 0-255 |
| `getRedF/GreenF/BlueF/AlphaF(argb)` | → `float` 0-1 |
| `getRedSq/GreenSq/BlueSq(argb)` | squared int component (used internally for perceptually-correct averaging) |
| `withRed/Green/Blue/Alpha(argb, int\|float)` | returns new ARGB with that channel replaced |
| `getHue/getHSVSaturation/getValue/getHSLSaturation/getLightness(argb)` | HSV/HSL derived values |
| `withHSVHue/withHSVSaturation/withValue/withHSLHue/withHSLSaturation/withLightness(argb, x)` | replace one HSV/HSL channel, others recomputed |
| `getCyan/Magenta/Yellow/Black(argb)`, `withCyan/Magenta/Yellow/Black(argb, x)` | CMYK equivalents |
| `getRGBValues/getARGBValues/getHSVValues/getHSLValues/getCMYKValues(argb)` | pack into `int[]`/`float[]` |
| `getLuminance(argb)` | perceived luminance (HSP model) |

### Combination / interpolation / format

| Method | Params | Returns | Notes |
|---|---|---|---|
| `invert(argb)` | - | inverted RGB, alpha unchanged | |
| `multiply(argb, float factor, boolean multiplyAlpha)` | - | scaled color | |
| `mix(argb1, argb2)` | - | component-wise product | |
| `average(int... colors)` | - | ARGB | Averages **squared** RGB components then sqrt's (perceptually correct), plain average for alpha. |
| `average(ToIntFunction<T>, T... holders)` | extractor + objects | ARGB | Same algorithm, generic source. |
| `average(argb1, argb2)` | - | ARGB | = `lerp(argb1, argb2, 0.5f)`. |
| `lerp(argb1, argb2, float v)` | - | ARGB | Linear interpolation via `Interpolation.LINEAR`. |
| `interpolate(IInterpolation curve, c1, c2, float v)` | curve + colors + progress | ARGB | `v` clamped to [0,1]; each channel interpolated on its **square** then sqrt'd. |
| `interpolate(c1, c2, v)` | *(deprecated)* | ARGB | Renamed to `lerp`; `@ApiStatus.ScheduledForRemoval(3.2.0)`. |
| `rgbaToArgb`/`argbToRgba` | - | reordered int | |
| `rgbToFullHexString`/`argbToFullHexString`/`toFullHexString(r,g,b[,a])`/`componentToFullHexString` | - | uppercase hex `String` | |
| `setGlColor(argb)` `@SideOnly(CLIENT)` | - | - | Applies color to GL state via `GlStateManager.color`. If alpha byte is 0 (and color isn't fully 0), forces alpha to 1.0 first — avoids "invisible tint" bugs from colors with `a=0` used as plain RGB. |
| `setGlColorOpaque(argb)` `@SideOnly(CLIENT)` | - | - | Same but alpha forced to 1.0 unconditionally. |
| `resetGlColor()` `@SideOnly(CLIENT)` | - | - | Sets GL color to opaque white. |

**Example (adapted from `test/GLTestGui.java` and `test/TestTile.java`):**
```java
private static final int COLOR = Color.withAlpha(Color.RED.brighter(0), 0.75f);
// ...
float r = Color.getRedF(COLOR);
float g = Color.getGreenF(COLOR);
float b = Color.getBlueF(COLOR);
float a = Color.getAlphaF(COLOR);

new Circle().setColor(Color.RED.darker(2), Color.RED.brighter(2));
int blended = Color.lerp(color1.getColor(), color2.getColor(), 0.5f);
```

---

## `com.cleanroommc.modularui.utils.ColorShade`

A named base color plus arrays of precomputed "brighter" and "darker" shade variants (Material Design palette style). Implements `IntIterable` (darkest→brightest via internal packed array `all`). All of `Color`'s named constants (`Color.RED`, `Color.BLUE`, ...) are `ColorShade` instances.

```java
public class ColorShade implements IntIterable {
    public final String name;
    public final int main;

    public static Builder builder(String name, int main);
    public static @Nullable ColorShade getFromName(String name);
    public static Collection<ColorShade> getAll();

    public int darker(int index);       // unchecked
    public int darkerSafe(int index);   // clamped to valid range
    public int darkerShadeCount();
    public int brighter(int index);
    public int brighterSafe(int index);
    public int brighterShadeCount();

    public static class Builder {
        public Builder addDarker(int... darker);
        public Builder addBrighter(int... brighter);
        public ColorShade build();
    }
}
```

- Construction is only via `builder(name, main).addBrighter(...).addDarker(...).build()`; `build()` registers the instance into a static name→shade map (`getFromName`), so shade names must be globally unique.
- `darker(int)`/`brighter(int)` throw `ArrayIndexOutOfBoundsException` if out of range; use the `*Safe` variants (used by `Color.parseString`) to clamp instead.
- Iterating a `ColorShade` (`for (int c : shade)`) walks brightest→darkest with `main` in the middle (used by `TestGuis.buildColorTheoryUI` to enumerate every shade of every color for a luminance-sorted gradient).

**Example (adapted from `test/TestGuis.java`):**
```java
List<Pair<Integer, Float>> colors = new ArrayList<>();
for (ColorShade shade : ColorShade.getAll()) {
    for (int c : shade) {
        colors.add(Pair.of(c, Color.getLuminance(c)));
    }
}
colors.sort((a, b) -> Float.compare(a.getRight(), b.getRight()));
```

---

## `com.cleanroommc.modularui.utils.DAM` (DoubleArrayMath)

Numpy-style operations on flat `double[]` arrays. Almost every method takes a `@Nullable double[] res` output array parameter — pass `null` to allocate a new array, or pass `src` itself to mutate in place (several `*Mut` shortcuts do this for you).

```java
public class DAM {
    public static final double[] EMPTY;
    public interface UnaryDoubleOperator { double apply(double v); }
    public interface BinaryDoubleOperator { double apply(double v, double op); }
    public interface TernaryDoubleOperator { double apply(double v, double op1, double op2); }
    public interface NDoubleOperator { double apply(double v, double[] op); }
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `zeros(n)` / `ones(n)` / `full(n, f)` | length [+ fill value] | new array | |
| `copyInto(src, res)` | - | `res` (or `src` if `res==src`) | |
| `subArray(src, start, length)` | - | new array | |
| `ofFloats/ofInts/ofLongs(src)` | - | `double[]` cast copy | |
| `linspace(start, stop[, n=50][, includeEndpoint=true])` | - | evenly spaced array | |
| `arange(start=0, stop, step)` | - | array from `start` to `stop` by `step` | length = `ceil((stop-start)/step)` |
| `argMax(arr)` / `argMin(arr)` | - | index, or `-1` if empty | |
| `max(arr)` / `min(arr)` | - | value, or `0` if empty | |
| `plus/mult/div(src, scalarOrArray, res)` + `*Mut` variants | - | elementwise result | `div` = `mult` by reciprocal |
| `reciprocal(a, b, res)` | - | `a/b[i]` | |
| `square/cube/pow(src, [op,] res)` | - | elementwise | |
| `diff(src)` | - | `src[i+1]-src[i]` array, length-1 | Empty if `src.length < 2`. |
| `applyEach(src, op[, operands[, operands2]], res)` | unary/binary/ternary/N-ary operator | elementwise result | Throws `IllegalArgumentException` if operand array lengths mismatch `src`. |
| `abs/sin/cos/tan(src, res)` | - | elementwise | |
| `clamp(src, min, max, res)` | - | elementwise clamp | |
| `polynomial(src, coeff, res)` | Horner-ish evaluation | `coeff[0] + coeff[1]*x + ...` per element | |
| `reduce(src, op)` | binary reducer | folded scalar | `0` if empty. |
| `reverse(src, res)` | - | reversed copy | |
| `sum/product/arithmeticMean/geometricMean(src)` | - | scalar | |
| `concat(a, b)` / `flatten(double[]... src)` | - | joined array | |

---

## `com.cleanroommc.modularui.utils.FAM` (FloatArrayMath)

Byte-for-byte `float[]` counterpart of `DAM` — identical method set and semantics (`zeros/ones/full`, `linspace/arange`, `argMax/argMin/max/min`, `plus/mult/div(+Mut)`, `square/cube/pow`, `diff`, `applyEach` with `UnaryFloatOperator`/`BinaryFloatOperator`/`TernaryFloatOperator`/`NFloatOperator`, `abs/sin/cos/tan` (trig delegates to `MathUtils.sin/cos/tan`, not `Math`), `clamp`, `polynomial`, `reduce`, `reverse`, `sum/product/arithmeticMean/geometricMean`, `concat/flatten`), plus `ofDoubles/ofInts/ofLongs`. See `DAM` above for the full table — everything applies with `float` in place of `double`.

---

## `com.cleanroommc.modularui.utils.FloatConsumer`

```java
@FunctionalInterface
public interface FloatConsumer extends DoubleConsumer {
    void accept(float value);
    // default accept(double) narrows to float and delegates
}
```

Primitive-`float` consumer that also satisfies `java.util.function.DoubleConsumer` (via a narrowing default method), so it can be passed anywhere a `DoubleConsumer` is expected. **Appears in public constructor signatures**: `FloatValue.Dynamic(FloatSupplier, FloatConsumer)`, `FloatSyncValue`'s constructors.

---

## `com.cleanroommc.modularui.utils.FloatSupplier`

```java
@FunctionalInterface
public interface FloatSupplier extends DoubleSupplier {
    float getAsFloat();
    // default getAsDouble() widens and delegates
}
```

Companion of `FloatConsumer`; also satisfies `DoubleSupplier`. Used the same places (`FloatValue.Dynamic`, `FloatSyncValue`).

**Example (constructed, not from repo):**
```java
float[] value = {0.5f};
FloatSupplier getter = () -> value[0];
FloatConsumer setter = v -> value[0] = v;
new FloatSyncValue(getter, setter);
```

---

## `com.cleanroommc.modularui.utils.FluidTankHandler`

Adapts a single `IFluidTank` to Forge's `IFluidHandler` interface (single-tank version of `MultiFluidTankHandler`).

```java
public class FluidTankHandler implements IFluidHandler {
    public static IFluidHandler getTankFluidHandler(IFluidTank tank);
    public FluidTankHandler(IFluidTank tank);
    // fill/drain/canFill/canDrain/getTankInfo delegate to the wrapped tank
}
```

- `getTankFluidHandler(tank)` — factory that avoids double-wrapping: if `tank` already implements `IFluidHandler` (a `instanceof` check), it's returned as-is instead of being wrapped.
- `canFill`/`canDrain` always return `true` regardless of direction/fluid — no filtering at this layer.
- `drain(ForgeDirection, FluidStack, boolean)` returns `null` if the tank is empty or the requested fluid doesn't match the tank's current fluid (`isFluidEqual`).

---

## `com.cleanroommc.modularui.utils.FpsCounter`

Minimal per-second FPS counter, ticked once per rendered frame.

```java
public class FpsCounter {
    public void reset();
    public void onDraw(); // call once per frame
    public int getFps();
}
```

Internally buckets `frameCount` over rolling 1000ms windows measured against `Minecraft.getSystemTime()`; `getFps()` returns the count from the last completed window (i.e. it updates once per second, not smoothed).

---

## `com.cleanroommc.modularui.utils.GameObjectHelper`

```java
public class GameObjectHelper {
    public static ItemStack getItemStack(String mod, String path);
    public static ItemStack getItemStack(String mod, String path, int meta);
}
```

Looks up a registered item via `GameRegistry.findItemStack(mod, path, 1)`, throwing `NoSuchElementException` if not found.

**Gotcha (bug, observable in source):** the 3-arg overload sets damage via `Items.feather.setDamage(item, meta)` — i.e. it always calls `setDamage` through the hardcoded `Items.feather` item instance rather than the looked-up item. `ItemStack.setItemDamage` is an instance method dispatched by vtable in vanilla 1.7.10, so this likely still mutates `item`'s damage correctly in practice, but the code reads as a copy-paste artifact and is worth treating with suspicion.

---

## `com.cleanroommc.modularui.utils.GlStateManager`

```java
/** Thin wrapper for making backport easier. Do not use outside MUI source code, it's pointless. */
public class GlStateManager {
    public enum DestFactor { ONE_MINUS_SRC_ALPHA, ZERO }
    public enum SourceFactor { ONE, SRC_ALPHA }
}
```

Explicitly documented as internal-only (backport shim over `GL11`/`OpenGlHelper`). Full method list — each is a 1:1 forward to the corresponding `GL11` call:

| Method | Notes |
|---|---|
| `enableAlpha/disableAlpha` | `GL_ALPHA_TEST` |
| `enableLighting/disableLighting` | `GL_LIGHTING` |
| `enableDepth/disableDepth` | `GL_DEPTH_TEST` |
| `depthMask(boolean)` | `glDepthMask` |
| `enableBlend/disableBlend` | `GL_BLEND` |
| `blendFunc(src, dst)` | raw `glBlendFunc` |
| `tryBlendFuncSeparate(SourceFactor, DestFactor, SourceFactor, DestFactor)` / int overload | delegates to `OpenGlHelper.glBlendFunc` (separate RGB/alpha factors) |
| `enableCull` | `GL_CULL_FACE` |
| `disableColorLogic` | `GL_COLOR_LOGIC_OP` |
| `enableTexture2D/disableTexture2D` | `GL_TEXTURE_2D` |
| `bindTexture(int)` | `glBindTexture` |
| `shadeModel(int)` | `glShadeModel` |
| `enableRescaleNormal/disableRescaleNormal` | `GL_RESCALE_NORMAL` |
| `viewport(x,y,w,h)` | `glViewport` |
| `colorMask(r,g,b,a)` | `glColorMask` |
| `clearDepth(double)` / `clear(int mask)` | `glClearDepth`/`glClear` |
| `matrixMode(int)` / `loadIdentity()` / `pushMatrix()` / `popMatrix()` | matrix stack |
| `rotate(angle,x,y,z)` / `scale(x,y,z)` (float+double overloads) / `translate(x,y,z)` (float+double overloads) | |
| `color(r,g,b,a)` | `glColor4f` |

---

## `com.cleanroommc.modularui.utils.GuiUtils`

Reads/writes the current OpenGL modelview matrix as a `org.joml.Matrix4f` (note: **not** this package's own legacy `Matrix4f` class).

```java
public class GuiUtils {
    public static FloatBuffer getTransformationBuffer();
    public static FloatBuffer getTransformationBuffer(FloatBuffer floatBuffer);
    public static Matrix4f getTransformationMatrix();                 // org.joml.Matrix4f
    public static Matrix4f getTransformationMatrix(Matrix4f matrix4f);
    public static void setTransformationMatrix(Matrix4f matrix);
    public static void applyTransformationMatrix(Matrix4f matrix);    // glMultMatrix (compose)
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getTransformationBuffer([FloatBuffer])` | optional buffer to reuse | 16-float buffer with `GL_MODELVIEW_MATRIX` | Position rewound to 0 after read. |
| `getTransformationMatrix([Matrix4f])` | optional matrix to reuse | current modelview as `org.joml.Matrix4f` | Uses an **internal static shared `FloatBuffer`** (not the one from `getTransformationBuffer()`). |
| `setTransformationMatrix(matrix)` | matrix | - | `glLoadMatrix` — replaces the current matrix. |
| `applyTransformationMatrix(matrix)` | matrix | - | `glMultMatrix` — composes onto the current matrix. |

**Gotcha:** `getTransformationMatrix`, `setTransformationMatrix`, and `applyTransformationMatrix` (no-arg-buffer forms) all share one private static `FloatBuffer floatBuffer` field. This is fine for typical single-threaded, non-reentrant GUI render code, but do not call these recursively/interleaved in a way that assumes buffer contents survive across nested calls.

---

## `com.cleanroommc.modularui.utils.HoveredWidgetList`

Stack-like wrapper around `ObjectList<LocatedWidget>` used to track which widgets are currently hovered, most-recently-added first.

```java
public class HoveredWidgetList {
    public HoveredWidgetList(ObjectList<LocatedWidget> delegate);
    public void add(IWidget widget, IViewportStack viewports, Object additionalHoverInfo);
    public @Nullable IWidget peek();
    public boolean isEmpty();
    public int size();
}
```

`add(...)` always inserts at the **front** (`addFirst`), so `peek()` (used to determine "the" hovered widget when multiple overlap) returns the most recently added entry — i.e. later `add` calls take priority over earlier ones for a given frame.

---

## `com.cleanroommc.modularui.utils.ICopy<T>`

Functional interface for producing a deep copy of a value, with two ready-made strategies.

```java
public interface ICopy<T> {
    static <T> ICopy<T> immutable();                                        // identity: t -> t
    static <T> ICopy<T> ofSerializer(IByteBufSerializer<T> ser, IByteBufDeserializer<T> deser);
    static <T> ICopy<T> ofSerializer(IByteBufAdapter<T> adapter);
    static <T> ICopy<T> wrapNullSafe(ICopy<T> copy);
    T createDeepCopy(T t);
}
```

- `immutable()` — use when `T` is immutable (or copying is meaningless), avoids allocation.
- `ofSerializer(...)` — deep-copies by round-tripping the value through a `PacketBuffer` (serialize then deserialize into a fresh instance). Wraps checked `IOException` in an unchecked `RuntimeException`.
- `wrapNullSafe(copy)` — returns a copy strategy that passes `null` through instead of invoking the delegate.

**Example (constructed, not from repo):**
```java
ICopy<int[]> copy = arr -> arr.clone();
int[] a = {1, 2, 3};
int[] b = copy.createDeepCopy(a);
```

---

## `com.cleanroommc.modularui.utils.ImageUtil`

Parses just the width/height out of PNG/JPEG/GIF file bytes without decoding the whole image (reads the signature + header only).

```java
public class ImageUtil {
    public static final long ERROR_NO_RESOURCE, ERROR_NO_IMAGE_TYPE, ERROR_IO_EXCEPTION, ERROR_PNG, ERROR_JPEG_1, ERROR_JPEG_2;

    public static IResource getResource(ResourceLocation resLoc);
    public static long readImageSize(ResourceLocation resLoc);
    public static long readImageSize(IResource resource);
    public static long readImageSize(InputStream inputStream) throws IOException;
    public static String getError(long size);
    public static long packSize(int width, int height);
    public static int getWidth(long packedSize);
    public static int getHeight(long packedSize);
    public static boolean testImageSize(ResourceLocation resLoc, int width, int height);
    public static String getImageType(InputStream inputStream);
    public static DataInput getDataInput(InputStream is);
    public static byte[] toBytes(int... ints);
    public static String getFileTypeOfPath(String path);
    public static int readLittleEndianShort(InputStream inputStream) throws IOException;
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `readImageSize(...)` (3 overloads) | resource location / `IResource` / raw stream | packed `long` size, or a negative `ERROR_*` code | Unpack with `getWidth`/`getHeight`. The `ResourceLocation`/`IResource` overloads swallow `IOException` into `ERROR_IO_EXCEPTION`/`ERROR_NO_RESOURCE`; the raw `InputStream` overload throws. |
| `getError(long)` | a negative size code | human-readable message, or `null` if `size >= 0` | |
| `packSize(w,h)` / `getWidth(long)` / `getHeight(long)` | - | bit-packs width into low 32 bits, height into high 32 bits | |
| `testImageSize(loc, w, h)` | - | `true` always (even on mismatch/error) | Logs an error via `ModularUI.LOGGER` on mismatch, info on match — a diagnostic/assert-and-log helper, not a real predicate (**Inferred:** the return value shouldn't be relied on for control flow). |
| `getImageType(stream)` | - | `"PNG"`/`"JPEG"`/`"GIF"` or `null` | |
| `getFileTypeOfPath(path)` | - | extension after last `.`, or `null` | |

Supports exactly PNG, JPEG (scans markers for the first `SOFn`), and GIF (little-endian 6-byte header). Unrecognized signatures yield `ERROR_NO_IMAGE_TYPE`.

---

## `com.cleanroommc.modularui.utils.IMultiFluidTankHandler`

```java
public interface IMultiFluidTankHandler extends IFluidHandler {
    int getTankCount();
    IFluidTank getFluidTank(int index);
}
```

Extension of Forge's `IFluidHandler` exposing per-tank access for handlers backing more than one tank. Sole implementation in scope: `MultiFluidTankHandler`.

---

## `com.cleanroommc.modularui.utils.Interpolation`

Enum of ~30 easing curves implementing `com.cleanroommc.modularui.api.drawable.IInterpolation`, used by `Animator`/`Color.interpolate` and anywhere an `IInterpolation` curve is accepted. See <https://easings.net> for visual reference.

```java
public enum Interpolation implements IInterpolation {
    LINEAR, QUAD_IN, QUAD_OUT, QUAD_INOUT, CUBIC_IN, CUBIC_OUT, CUBIC_INOUT,
    EXP_IN, EXP_OUT, EXP_INOUT, BACK_IN, BACK_OUT, BACK_INOUT,
    ELASTIC_IN, ELASTIC_OUT, ELASTIC_INOUT, BOUNCE_IN, BOUNCE_OUT, BOUNCE_INOUT,
    SINE_IN, SINE_OUT, SINE_INOUT, QUART_IN, QUART_OUT, QUART_INOUT,
    QUINT_IN, QUINT_OUT, QUINT_INOUT, CIRCLE_IN, CIRCLE_OUT, CIRCLE_INOUT;

    public final String name; // lowercase snake-case id, e.g. "quad_in"
    public float interpolate(float a, float b, float x);
    public @NotNull String getName();
    public static Interpolation getForName(String name); // returns null if not found
}
```

Each constant's `interpolate(a, b, x)` maps progress `x` (nominally 0-1) to a value between `a` and `b` along that curve's shape. `BOUNCE_IN`/`BOUNCE_INOUT` are defined in terms of `BOUNCE_OUT`.

**Example (adapted from `test/TestTile.java` and `test/TestGuis.java`):**
```java
new Animator().interpolation(Interpolation.BOUNCE_OUT);
Animator post = new Animator().curve(Interpolation.SINE_IN).duration(300).bounds(-35, 0);
float scaled = Interpolation.BACK_INOUT.interpolate(0.5f, 1f, t / 2000f);
```

---

## `com.cleanroommc.modularui.utils.Interpolations`

Static math helpers underlying `Interpolation` and general animation math — `float` and `double` overloads of the same functions.

```java
public class Interpolations {
    public static float lerp(float a, float b, float position);
    public static int lerp(int a, int b, float position);
    public static float lerpYaw(float a, float b, float position);
    public static double cubicHermite(double y0, double y1, double y2, double y3, double x);
    public static double cubicHermiteYaw(float y0, float y1, float y2, float y3, float position);
    public static float cubic(float y0, float y1, float y2, float y3, float x);
    public static float cubicYaw(float y0, float y1, float y2, float y3, float position);
    public static float bezierX(float x1, float x2, float t[, float epsilon]);
    public static float bezier(float x1, float x2, float x3, float x4, float t);
    public static float normalizeYaw(float a, float b);
    public static float envelope(float x, float duration, float fades);
    public static float envelope(float x, float lowIn, float lowOut, float highIn, float highOut);
    // + double overloads of lerp, lerpYaw, cubic, cubicYaw, bezierX, bezier, normalizeYaw, envelope
}
```

| Method | Notes |
|---|---|
| `lerp(a, b, position)` | Plain linear interpolation; `int` overload truncates. |
| `lerpYaw` / `normalizeYaw` | Yaw-aware interpolation that picks the shorter angular path across the ±180° wraparound (e.g. interpolating -170° → 170° goes through 180°, not through 0°). |
| `cubicHermite(y0,y1,y2,y3,x)` | Hermite spline through 4 control points, evaluated between `y1` and `y2` at `x∈[0,1]`. |
| `cubic(y0,y1,y2,y3,x)` | Catmull-Rom-style cubic (different coefficient derivation than Hermite). |
| `bezierX(x1,x2,t[,epsilon])` | Iteratively solves for the `x` parameter of a cubic bezier given target `t`, via a shrinking-step search (default `epsilon=0.0005`). |
| `bezier(x1,x2,x3,x4,t)` | Direct cubic bezier evaluation via repeated `lerp`. |
| `envelope(x, duration, fades)` / 5-arg overload | Attack/sustain/release ramp: 0 before `lowIn`, ramps to 1 by `lowOut`, holds 1 until `highIn`, ramps back to 0 by `highOut`. |

---

## `com.cleanroommc.modularui.utils.ISimpleBauble`

Baubles-mod integration interface (`baubles.api.expanded.IBaubleExpanded`) with sensible no-op defaults, so implementors only override what they need.

```java
public interface ISimpleBauble extends IBaubleExpanded {
    default BaubleType getBaubleType(ItemStack itemstack); // BaubleType.UNIVERSAL
    default void onWornTick(ItemStack itemstack, EntityLivingBase player);
    default void onEquipped(ItemStack itemstack, EntityLivingBase player);
    default void onUnequipped(ItemStack itemstack, EntityLivingBase player);
    default boolean canEquip(ItemStack itemstack, EntityLivingBase player);   // true
    default boolean canUnequip(ItemStack itemstack, EntityLivingBase player); // true
}
```

**Example (from `test/TestItem.java`):**
```java
public class TestItem extends Item implements IGuiHolder<PlayerInventoryGuiData>, ISimpleBauble {
    // inherits BaubleType.UNIVERSAL and permissive equip/unequip defaults
}
```

---

## `com.cleanroommc.modularui.utils.ItemStackItemHandler`

An `IItemHandlerModifiable` that stores its slots as an NBT tag list on a *container* `ItemStack` (e.g. the item this handler is attached to, sitting in a player's hand/bauble slot) rather than in a separate inventory object.

```java
public class ItemStackItemHandler implements IItemHandlerModifiable {
    public ItemStackItemHandler(PlayerInventoryGuiData data, int slots);
    public ItemStackItemHandler(Supplier<ItemStack> container, Consumer<ItemStack> containerUdater, int slots);

    public int getSlots();
    public @Nullable ItemStack getStackInSlot(int slot);
    public void setStackInSlot(int slot, @Nullable ItemStack stack);
    public @Nullable ItemStack insertItem(int slot, @Nullable ItemStack stack, boolean simulate);
    public @Nullable ItemStack extractItem(int slot, int amount, boolean simulate);
    public int getSlotLimit(int slot); // 64
    public NBTTagList getItemsNbt();
}
```

**Gotcha (flagged directly in source, `// nh todo`):** the class doc comment states *"this doesn't work due to `ItemStackItemHandler#container` being different object from actual item stored in inv"* — i.e. the `Supplier<ItemStack>`/`Consumer<ItemStack>` pair can drift out of sync with the real backing `ItemStack` instance in the inventory, so persisted changes may not actually stick. Treat this class as unreliable/WIP rather than production-ready.

**Example (from `test/TestItem.java`):**
```java
IItemHandlerModifiable itemHandler = new ItemStackItemHandler(guiData, 4);
```

---

## `com.cleanroommc.modularui.utils.JsonArrayBuilder`

Fluent wrapper around Gson `JsonArray` construction.

```java
public class JsonArrayBuilder {
    public JsonArrayBuilder();
    public JsonArrayBuilder(JsonArray json);
    public JsonArray getJson();
    public JsonArrayBuilder add(boolean|char|Number|String|JsonElement element);
    public JsonArrayBuilder add(JsonBuilder element);
    public JsonArrayBuilder add(JsonArrayBuilder element);
    public JsonArrayBuilder addObject(Consumer<JsonBuilder> builderConsumer);
    public JsonArrayBuilder addArray(Consumer<JsonArrayBuilder> builderConsumer);
    public JsonArrayBuilder addAllOf(JsonArray json);
    public JsonArrayBuilder addAllOf(JsonArrayBuilder json);
}
```

All `add*` methods return `this` for chaining. `addObject`/`addArray` take a builder-consuming lambda to inline nested structures without pre-declaring variables.

**Example (constructed, not from repo):**
```java
JsonArray arr = new JsonArrayBuilder()
        .add("a").add(1).add(true)
        .addObject(o -> o.add("nested", "value"))
        .getJson();
```

---

## `com.cleanroommc.modularui.utils.JsonBuilder`

Fluent wrapper around Gson `JsonObject` construction, mirroring `JsonArrayBuilder`.

```java
public class JsonBuilder {
    public JsonBuilder();
    public JsonBuilder(JsonObject json);
    public JsonObject getJson();
    public JsonBuilder add(String key, JsonElement|String|Number|boolean|char element);
    public JsonBuilder add(String key, JsonBuilder|JsonArrayBuilder element);
    public JsonBuilder mergeAdd(String key, JsonObject|JsonBuilder element);
    public JsonBuilder addObject(String key, Consumer<JsonBuilder> builderConsumer);
    public JsonBuilder addArray(String key, Consumer<JsonArrayBuilder> builderConsumer);
    public JsonBuilder addAllOf(JsonObject|JsonBuilder json);
}
```

`mergeAdd(key, obj)` differs from `add(key, obj)`: if `key` already holds a `JsonObject`, its entries are merged into the existing object (existing keys overwritten) instead of replacing it wholesale; falls back to `add` if the existing value isn't an object.

**Example (used internally by `Alignment.Json.serialize`, adapted):**
```java
JsonObject json = JsonHelper.makeJson(j -> {
    JsonBuilder b = new JsonBuilder(j);
    b.add("x", 0.5f).add("y", 1f);
});
```

---

## `com.cleanroommc.modularui.utils.JsonHelper`

Central Gson instance plus a large set of null-safe, multi-key-fallback accessors for reading values out of `JsonObject`s (used throughout theme/widget JSON deserialization).

```java
public class JsonHelper {
    public static final Gson GSON;             // pretty-printing; registers IDrawable + Alignment.Json adapters
    public static final JsonDeserializationContext DESERIALIZER;
    public static final JsonSerializationContext SERIALIZER;
    public static final JsonParser parser;

    public static JsonElement serialize(Object object);
    public static <T> T deserialize(JsonElement json, Class<T> clazz);
    public static <T> T deserialize(JsonObject json, Class<T> clazz, T defaultValue, String... keys);
    public static <T> T deserializeWithFallback(JsonObject json, JsonObject fallback, Class<T> clazz, T defaultValue, String... keys);
    public static float getFloat(JsonObject json, float defaultValue, String... keys);
    public static int getInt(JsonObject json, int defaultValue, String... keys);
    public static int getIntWithFallback(JsonObject json, JsonObject fallback, int defaultValue, String... keys);
    public static boolean getBoolean(JsonObject json, boolean defaultValue, String... keys);
    public static boolean getBoolWithFallback(JsonObject json, JsonObject fallback, boolean defaultValue, String... keys);
    public static String getString(JsonObject json, String defaultValue, String... keys);
    public static <T> T getObject(JsonObject json, T defaultValue, Function<JsonObject, T> factory, String... keys);
    public static <T> T getElement(JsonObject json, T defaultValue, Function<JsonElement, T> factory, String... keys);
    public static @Nullable Integer getBoxedInt(JsonObject json, Integer defaultValue, String... keys);
    public static @Nullable Boolean getBoxedBool(JsonObject json, Boolean defaultValue, String... keys);
    public static int getColor(JsonObject json, int defaultValue, String... keys);
    public static int getColorWithFallback(JsonObject json, JsonObject fallback, int defaultValue, String... keys);
    public static @Nullable JsonElement getJsonElement(JsonObject json, String... keys);
    public static JsonElement parse(InputStream inputStream);
    public static JsonObject merge(JsonObject base, JsonObject other);
    public static JsonObject makeJson(Consumer<JsonObject> writer);
}
```

**Key pattern (applies to every `get*(json, default, String... keys)` method):** `keys` is a list of *alternative* key names checked in order (e.g. `"color"` vs `"colour"`, or short `"h"` vs long `"hue"`); the first key present in the JSON object wins, its value is read, and `defaultValue` is returned if none of the keys are present **or** `json` itself is `null` (all these methods are null-safe on `json`).

| Method | Notes |
|---|---|
| `getColor(json, default, keys)` | Delegates value parsing to `Color.ofJson`. |
| `*WithFallback(json, fallback, default, keys)` variants | Try `json` first; if absent, retry the same key search against a second `fallback` JsonObject (used for theme inheritance — child theme falls back to parent theme's JSON). |
| `getObject`/`getElement` | Generic escape hatches: supply your own `Function` to convert the raw `JsonObject`/`JsonElement` into any `T`. |
| `merge(base, other)` | Mutates and returns `base`, overwriting any overlapping keys with `other`'s values (shallow). |
| `makeJson(writer)` | Convenience for building an ad-hoc `JsonObject` inline via a lambda. |

---

## `com.cleanroommc.modularui.utils.KeyboardData`

Immutable snapshot of a keyboard event plus modifier state, with client/server packet (de)serialization.

```java
public class KeyboardData {
    public final Side side;
    public final char character;
    public final int keycode;
    public final boolean shift, ctrl, alt;

    public KeyboardData(Side side, char character, int keycode, boolean shift, boolean ctrl, boolean alt);
    public boolean isClient();
    public void writeToPacket(PacketBuffer buffer);
    public static KeyboardData readPacket(PacketBuffer buffer);
    @SideOnly(Side.CLIENT) public static KeyboardData create(char character, int keycode);
}
```

`create(char, int)` is the client-side factory: reads current shift/ctrl/alt from `Interactable.hasShiftDown()`/`hasControlDown()`/`hasAltDown()` and tags the result `Side.CLIENT`. `readPacket` always produces `Side.SERVER` data. Modifiers are packed into a single byte (bit 0/1/2) over the wire.

---

## `com.cleanroommc.modularui.utils.MathUtils`

Large grab-bag of numeric helpers: clamping, wrapping, min/max varargs, trig fixes for Minecraft's lookup-table `MathHelper`, and an expression-parser front-end (backed by the `evalex` library) used by number text fields.

```java
public class MathUtils {
    public static final float PI, PI2, PI_HALF, PI_QUART;
    public static final ExpressionConfiguration MATH_CFG, MATH_CFG_CASE_SENSITIVE;
    public static final INumberParser PARSER_WITH_SI, PARSER_WHOLE_NUMBER;

    public static ParseResult parseExpression(String expression, double defaultValue);
    public static ParseResult parseExpression(String expression, double defaultValue, boolean useSiPrefixes, boolean biggerThanOne);
    public static ParseResult parseExpressionWholeNumber(String expression, double defaultValue);
    // ... static helpers below
    public interface UnaryLongOperator { long apply(long l); }
    public interface UnaryIntOperator { int apply(int l); }
}
```

### Expression parsing

| Member | Notes |
|---|---|
| `MATH_CFG` | `evalex` config: no arrays/structures, strips trailing zeros, allows overwriting constants, registers a `%` postfix operator (`PostfixPercentOperator`) and a custom data accessor. |
| `MATH_CFG_CASE_SENSITIVE` | Same, but variable/constant names are case-sensitive. |
| `parseExpression(expr, default[, useSiPrefixes=true, biggerThanOne=false])` | Evaluates `expr` as a math expression (case-sensitive config); if `useSiPrefixes`, registers all `SIPrefix` symbols (`k`, `M`, `µ`, etc.) as constants first via `SIPrefix.addAllToExpression`. Empty/null `expr` short-circuits to `defaultValue` wrapped in a successful `ParseResult`. |
| `parseExpressionWholeNumber(expr, default)` | Case-insensitive config; registers only `k`/`M`/`G` (alt `b`)/`T` prefixes plus `i`=144 (ingot) and `s`=64 (stack) as Minecraft-flavored constants. |
| `PARSER_WITH_SI` / `PARSER_WHOLE_NUMBER` | Method references to the two parse methods, typed as `INumberParser` for use by number text field widgets. |

### Grab-bag statics

| Method | Params | Returns | Notes |
|---|---|---|---|
| `clamp(v, min, max)` | `int`/`float`/`double`/`long` overloads | clamped value | |
| `cycler(x, min, max)` | `int`/`float`/`double` overloads | `max` if `x<min`, `min` if `x>max`, else `x` | Wraps a value around a range instead of clamping. |
| `gridIndex(x, y, size, width)` | pixel coords, cell size, grid width | flat cell index | |
| `gridRows(count, size, width)` | item count, cell size, grid width | row count (`ceil`) | Returns `1` if `count<=0`. |
| `min(int...)` / `max(int...)` | - | extremum | Throws `IllegalArgumentException` on empty/null input. |
| `ceil(float)` / `ceil(double)` | - | `int` | Manual ceiling (avoids `Math.ceil`'s double round-trip). |
| `wrapDegrees(float\|double\|int)` | angle | angle wrapped to `[-180, 180)` | |
| `sin(float)` / `cos(float)` / `tan(float)` | - | `float` | **Gotcha:** delegates to `net.minecraft.util.MathHelper.sin/cos`, which use a lookup table that (per the source comment) doesn't handle negative inputs correctly — these wrappers fix that via sign/symmetry tricks before delegating. Prefer these over `MathHelper` directly for negative angles. |
| `sqrt(double)` / `sqrt(float)` | - | matching type | Thin `Math.sqrt` wrapper. |
| `arithmeticGeometricMean(a, b[, iterations=5])` | - | `float` | Iterative AGM. |
| `rescaleLinear(v, fromMin, fromMax, toMin, toMax)` | `float`/`double` overloads | rescaled value | Reverse-lerp then forward-lerp; **not clamped** — extrapolates outside `[fromMin, fromMax]`. |
| `intPlaces(double\|BigDecimal x)` | - | number of digits left of the decimal point (min 1) | Used by `NumberFormat` to budget string length. |
| `areBothSmallerOrBiggerThanOne(a, b)` | - | boolean | |
| `percentOrSelf(value, maxValue)` | parsed value, upper bound | `long` | If `value>1` (or is already ~integral), rounds and returns as-is; otherwise treats it as a fraction of `maxValue` (e.g. `0.5` → `maxValue/2`). Used to resolve number fields where the user may type a raw amount or a fraction. |
| `percentOrSelf(expression, value, maxValue)` | + original expression string | `long` | If the source expression literally contains a `%` character, always treats `value` as a fraction of `maxValue` (preserves explicit percent intent even if `value` happens to be `>1` after the `%` operator already divided it). |
| `castToIntSaturated/ShortSaturated/ByteSaturated(long)` | - | saturating narrowing cast | Clamps to the target type's min/max instead of wrapping. |

---

## `com.cleanroommc.modularui.utils.Matrix4f`

**`@Deprecated`, `@ApiStatus.ScheduledForRemoval(inVersion = "3.2.0")`.** Legacy LWJGL2-style column-major 4x4 float matrix, kept only for backport compatibility. New code should use `org.joml.Matrix4f` directly (this class provides `toJoml()` for migration).

```java
@ApiStatus.ScheduledForRemoval(inVersion = "3.2.0")
@Deprecated
public class Matrix4f {
    public float m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23, m30, m31, m32, m33;
    public Matrix4f();               // identity
    public Matrix4f(Matrix4f src);   // copy
    public org.joml.Matrix4f toJoml();
}
```

| Method | Notes |
|---|---|
| `setIdentity()` / static `setIdentity(m)` | |
| `setZero()` / static `setZero(m)` | |
| `load(Matrix4f src)` / static `load(src, dest)` | Copy values between matrices. |
| `load(FloatBuffer)` | Column-major (OpenGL) order. |
| `loadTranspose(FloatBuffer)` | Row-major (math) order. |
| `store(FloatBuffer)` / `storeTranspose(FloatBuffer)` | Write out in column-major / row-major order. |
| `store3f(FloatBuffer)` | Writes only the 3x3 rotation/scale block (9 floats). |
| static `add(left, right, dest)` / `sub(left, right, dest)` / `mul(left, right, dest)` | `dest=null` allocates a new matrix. |
| static `transform(Matrix4f left, Vector4f right, Vector4f dest)` | Matrix × vector (uses LWJGL's `org.lwjgl.util.vector.Vector4f`, not this package's `Vector3f`). |
| `transpose()` / static `transpose(src, dest)` | |
| `translate(x,y[,dest])` / `translate(Vector3f[,dest])` + static forms | Accumulates into `dest.m3x` (note: **adds** to existing `dest` values rather than overwriting — see source, uses `+=`). |
| `scale(Vector3f)` + static `scale(vec, src, dest)` | Scales only rows 0-2 (translation row untouched). |
| `rotate(angle, axis[, dest])` + static form | `angle` in radians; `axis` must be pre-normalized. |
| `determinant()` | Full 4x4 determinant via cofactor expansion. |
| `invert()` / static `invert(src, dest)` | Returns `null` if `determinant()==0` (singular). |
| `negate()` / static `negate(src, dest)` | Negates every component. |

---

## `com.cleanroommc.modularui.utils.MouseData`

Immutable snapshot of a mouse event plus modifier state; structurally identical to `KeyboardData` but for mouse button + no character/keycode.

```java
public class MouseData {
    public final Side side;
    public final int mouseButton;
    public final boolean shift, ctrl, alt;

    public MouseData(Side side, int mouseButton, boolean shift, boolean ctrl, boolean alt);
    public boolean isClient();
    public void writeToPacket(PacketBuffer buffer);
    public static MouseData readPacket(PacketBuffer buffer);
    @SideOnly(Side.CLIENT) public static MouseData create(int mouse);
}
```

Same modifier-byte packet format and `Interactable`-sourced modifier state as `KeyboardData.create`.

---

## `com.cleanroommc.modularui.utils.MultiFluidTankHandler`

`IMultiFluidTankHandler` implementation backing several independent `IFluidTank`s with fill/drain logic that spreads across them.

```java
public class MultiFluidTankHandler implements IMultiFluidTankHandler {
    public MultiFluidTankHandler(IFluidTank... tanks);
    public MultiFluidTankHandler(int count, int capacity);                      // uses plain FluidTank
    public MultiFluidTankHandler(int count, IntFunction<IFluidTank> tankBuilder);

    public int getTankCount();
    public IFluidTank getFluidTank(int index);
    public static boolean isFluidEmpty(FluidStack f);
    // fill/drain/canFill/canDrain/getTankInfo per IFluidHandler
}
```

- `fill(...)` — two passes: first tops off tanks that already hold the *same* fluid, then distributes any remainder into empty tanks, in array order.
- `drain(ForgeDirection, FluidStack, boolean)` — drains matching fluid starting at tank 0, recursing forward through tanks until the requested amount is satisfied or tanks are exhausted.
- `drain(ForgeDirection, int maxDrain, boolean)` — drains from the first non-empty tank; if that alone doesn't satisfy `maxDrain`, recurses into the *rest* of the tanks for the same fluid to top up the amount.
- `canFill`/`canDrain` always `true` (no direction/fluid filtering, same as `FluidTankHandler`).
- Constructor validates: varargs form throws `NullPointerException` (via `Objects.requireNonNull`) on a `null` array or any `null` element; `IntFunction` form requires the builder to never return `null`.

**Example (from `test/TestTile.java`):**
```java
private final MultiFluidTankHandler fluidStorage = new MultiFluidTankHandler(3, 10000);
private final MultiFluidTankHandler phantomFluidStorage = new MultiFluidTankHandler(3, 500000);
```

---

## `com.cleanroommc.modularui.utils.MutableSingletonList<T>`

A full `java.util.List<T>` implementation that holds **at most one element** — a mutable "optional as list" for APIs that need `List<T>` shape but only ever have 0 or 1 items (e.g. a widget that has a single, swappable child but is stored in a child-list-typed field).

```java
public class MutableSingletonList<T> implements List<T> {
    public MutableSingletonList();       // starts empty
    public MutableSingletonList(T value); // starts with one value

    public T get();          // throws IndexOutOfBoundsException if empty
    public T getOrNull();
    public void set(T t);    // replaces the single slot (does not grow beyond 1)
    public void remove();    // clears the slot
    public boolean hasValue();
    // + full List<T> contract: size/isEmpty/contains/iterator/toArray/add/remove/
    //   containsAll/addAll/removeAll/retainAll/clear/get(int)/set(int,T)/add(int,T)/
    //   remove(int)/indexOf/lastIndexOf/listIterator/subList
}
```

**Gotchas:**
- `add(T)` (the no-index `List` method) **throws `IllegalStateException`** if a value is already present — this list never grows to size 2; use `set(T)` to replace.
- Any indexed access other than index `0` throws `IndexOutOfBoundsException`.
- `get(int)` on index 0 while empty throws `IndexOutOfBoundsException` — but the parameterless `getOrNull()` is the null-safe alternative.

Used internally by `DelegatingWidget` (single-child delegate storage) — not commonly instantiated directly by mod authors, but worth knowing if you see `List<T>` fields backed by it.

---

## `com.cleanroommc.modularui.utils.NumberFormat`

Compact number formatter that picks an `SIPrefix` (k, M, µ, etc.) to keep the formatted string within a target character budget — used for things like fluid/energy amount displays that must fit a fixed-width label.

```java
public class NumberFormat {
    public static final BigDecimal TEN_THOUSAND;
    public static final Params DEFAULT, DECIMALS_3, AMOUNT_TEXT;

    public static Params params(DecimalFormat format, int maxLength, boolean considerOnlyDecimalsForLength,
                                 boolean considerDecimalSeparatorForLength, boolean considerMinusForLength,
                                 boolean considerSuffixForLength, boolean spaceAfterNumber);
    public static ParamsBuilder paramsBuilder();
    public static String format(double number, Params params);
    public static String format(BigDecimal number, Params params);
    public static String formatFromUnit(double number, SIPrefix unit, Params params);
    public static SIPrefix findBestPrefix(double number);
    public static SIPrefix findBestPrefix(BigDecimal number);
    public static String formatNanos(long nanos); // convenience: nanoseconds -> "123n"-style string

    public static class Params { /* immutable config, see fields below */ public String format(double); public String format(BigDecimal); }
    public static class ParamsBuilder { /* fluent setters mirroring Params fields */ public Params build(); }
}
```

- `Params` fields: `format` (a `DecimalFormat`), `maxLength` (character budget), `considerOnlyDecimalsForLength`/`considerDecimalSeparatorForLength`/`considerMinusForLength`/`considerSuffixForLength` (which parts of the string count against `maxLength`), `spaceAfterNumber`. Constructor throws `IllegalArgumentException` if `maxLength<4` and `considerOnlyDecimalsForLength` is false.
- `DEFAULT` — `HALF_UP` rounding, budget 4 chars, suffix counts toward length, no space before suffix.
- `DECIMALS_3` — like `DEFAULT` but budgets only the decimal digits (3), ignores suffix for length.
- `AMOUNT_TEXT` — like `DEFAULT` but rounds `DOWN` (never over-reports an amount).
- `findBestPrefix(number)` — picks the largest `SIPrefix` such that the scaled value lands in `[1, 10_000)`; returns `SIPrefix.One` for `NaN`/zero/already-in-range, `SIPrefix.Infinite` for `Infinite`.
- `format(...)` handles the sign separately (formats the absolute value, prepends `-`), then delegates to prefix selection + `DecimalFormat`, dynamically adjusting `format.setMaximumFractionDigits(...)` per-call based on the remaining character budget after accounting for sign/suffix/separator/integer digits.

**Example (constructed, not from repo):**
```java
String s = NumberFormat.DEFAULT.format(123_456_789.0); // -> "123.5M" (four significant chars + suffix)
```

---

## `com.cleanroommc.modularui.utils.ObjectList<V>`

Interface extending fastutil's `ObjectList<V>` with a `Deque`-like API (`addFirst/addLast/getFirst/getLast/removeFirst/removeLast/peekFirst/pollFirst/peekLast/pollLast`) plus `trim()`/`elements()`/`ensureCapacity(int)`. The sole implementation, nested `ObjectArrayList<V>`, is a thin subclass of fastutil's `ObjectArrayList` adding those deque operations on top.

```java
public interface ObjectList<V> extends it.unimi.dsi.fastutil.objects.ObjectList<V> {
    static <V> ObjectArrayList<V> create();
    static <V> ObjectArrayList<V> create(int size);
    static <V> ObjectArrayList<V> of(Collection<? extends V> c);
    static <V> ObjectArrayList<V> of(V[] a[, int offset, int length]);
    static <V> ObjectArrayList<V> of(Iterator<? extends V> i);
    // + of(ObjectCollection), of(fastutil ObjectList), of(ObjectIterator)

    void addFirst(V v);
    void addLast(V v);
    @NotNull V getFirst();
    @NotNull V getLast();
    @NotNull V removeFirst();
    @NotNull V removeLast();
    @Nullable V peekFirst();
    @Nullable V pollFirst();
    @Nullable V peekLast();
    @Nullable V pollLast();
    void trim();
    @NotNull V[] elements();
    void ensureCapacity(int minCapacity);

    class ObjectArrayList<V> extends it.unimi.dsi.fastutil.objects.ObjectArrayList<V> implements ObjectList<V> { ... }
}
```

`peekFirst`/`peekLast`/`pollFirst`/`pollLast` are null-safe (return `null` on empty list); `getFirst`/`getLast`/`removeFirst`/`removeLast` are not (fastutil's underlying `get`/`remove` throw on empty/bad index). **Appears in a public constructor signature**: `HoveredWidgetList(ObjectList<LocatedWidget> delegate)`.

**Example (adapted from `TreeUtil.foreachChildBFS`, which uses it as a work queue):**
```java
ObjectList<T> parents = ObjectList.create();
parents.add(parent);
while (!parents.isEmpty()) {
    T next = parents.removeFirst();
    // ... process, then parents.addLast(child) for BFS traversal
}
```

---

## `com.cleanroommc.modularui.utils.PairList<T1, T2>`

Parallel-array alternative to `List<Pair<T1,T2>>` — stores the two components in separate backing `ArrayList`s to avoid a `Pair` allocation per entry on insert.

```java
public class PairList<T1, T2> implements Iterable<Pair<T1, T2>> {
    public void add(T1 t1, T2 t2);
    public int size();
    public boolean isEmpty();
    public T1 getLeft(int index);
    public T2 getRight(int index);
    public Pair<T1, T2> get(int index);       // allocates a Pair.of(...) on demand
    public Iterator<Pair<T1, T2>> iterator();
}
```

**Gotcha:** `iterator()` yields a **single reused `MutablePair`** instance across all steps (via Guava's `AbstractIterator`) — each call to `next()` mutates the same object's left/right rather than creating a new one. Do **not** retain a reference to the yielded `Pair` past the current loop iteration (e.g. don't collect them into a `List<Pair<...>>` while iterating); use `get(index)` instead if you need independently-alive `Pair` instances.

**Example (constructed, not from repo):**
```java
PairList<String, Integer> pairs = new PairList<>();
pairs.add("a", 1);
pairs.add("b", 2);
for (Pair<String, Integer> p : pairs) {
    System.out.println(p.getLeft() + "=" + p.getRight()); // fine: used and discarded within the loop body
}
```

---

## `com.cleanroommc.modularui.utils.ParseResult`

Result wrapper for `MathUtils.parseExpression(...)` — either a successful `evalex` `EvaluationValue` or a `BaseException` describing the parse/evaluation failure.

```java
public class ParseResult {
    public static ParseResult success(EvaluationValue result);
    public static ParseResult failure(@NotNull BaseException error);
    public static ParseResult failure(EvaluationValue value, @NotNull BaseException error);

    public boolean isSuccess();
    public boolean isFailure();
    public boolean hasValue();
    public EvaluationValue getResult();
    public BaseException getError();
    public String getErrorMessage(); // null if isSuccess()
}
```

`getErrorMessage()` formats as `"<message> for Token <token> at <start>:<end>"` using the underlying `BaseException`'s position info — handy for surfacing expression-parse errors in a text field tooltip.

---

## `com.cleanroommc.modularui.utils.Platform`

"Version specific code is supposed to go here" — a grab-bag of GL render-state setup/teardown pairs and 1.7.10-specific item/entity helpers, intended as the seam for future MC-version ports.

```java
public class Platform {
    public static final ItemStack EMPTY_STACK; // = null (1.7.10 convention: no ItemStack.EMPTY sentinel)

    @SideOnly(Side.CLIENT) public static @NotNull EntityPlayerSP getClientPlayer();
    @SideOnly(Side.CLIENT) public static String getKeyDisplay(KeyBinding keyBinding);
    public static boolean isStackEmpty(ItemStack stack);
    public static ItemStack copyStack(ItemStack stack);
    public static void unFocusRecipeViewer();
    public static void startDrawing(DrawMode drawMode, VertexFormat format, Consumer<BufferBuilder> bufferBuilder);
    // setup*/end* pairs below
    public enum DrawMode { QUADS, POINTS, LINES, LINE_STRIP, LINE_LOOP, TRIANGLES, TRIANGLE_STRIP, TRIANGLE_FAN }
    public enum VertexFormat { POS, POS_TEX, POS_COLOR, POS_TEX_COLOR, POS_NORMAL, POS_TEX_NORMAL, POS_TEX_COLOR_NORMAL, POS_TEX_LMAP_COLOR }
}
```

| Method pair | Notes |
|---|---|
| `isStackEmpty(stack)` / `copyStack(stack)` | `isStackEmpty` treats `null`, null-item, or `stackSize<=0` as empty. `copyStack` returns `EMPTY_STACK` (`null`) for empty input instead of copying. |
| `setupDrawColor()` / `endDrawColor()` | Disables texture/alpha test, enables blend, for drawing flat-colored rects; `endDrawColor` restores vanilla `Gui.drawRect`-compatible state (documented as required for NEI/vanilla-button interop). |
| `setupDrawTex([texture\|textureId][, withBlend])` / (no explicit "end", paired with `setupDrawColor`/`setupDrawGradient` as needed) | Enables texture+alpha, optionally blend; overloads bind a `ResourceLocation` or raw GL texture id first. |
| `setupDrawGradient([factors])` / `endDrawGradient()` | Sets up separate-alpha blend function + smooth shading for gradient quads; default factors are `SRC_ALPHA`/`ONE_MINUS_SRC_ALPHA` (color) and `ONE`/`ZERO` (alpha). |
| `setupDrawItem()` / `endDrawItem()` | Standard GUI item-rendering GL state (rescale normal, standard item lighting, depth test). |
| `setupDrawFont()` | Alias for `setupDrawTex()`. |
| `setupDrawEntity(entity, x, y, w, h, z)` / `endDrawEntity()` | Scales/positions/rotates the GL state to render a live `Entity` fit into a `w`×`h` GUI box at depth `z`; picks scale from whichever of width/height is the limiting dimension. |
| `startDrawing(DrawMode, VertexFormat, Consumer<BufferBuilder>)` | Wraps `Tessellator.instance.startDrawing/draw` around a callback that populates the (MUI) `BufferBuilder`. **Note:** `VertexFormat` values are currently unused by `startDrawing` itself — kept only "for parity with 1.12" per the source comment. |
| `unFocusRecipeViewer()` | If NEI is loaded, clears focus on NEI's search/quantity text fields (prevents MUI and NEI text fields fighting over keyboard focus). |

---

## `com.cleanroommc.modularui.utils.ReversedList<T>`

Zero-copy reversed **view** of a `List<T>` (an `AbstractList` that flips indices on every access).

```java
public class ReversedList<T> extends AbstractList<T> {
    public ReversedList(List<T> delegate);
    public int inverseIndex(int i); // = size() - 1 - i
    // get/set/remove/add(index,...)/addAll(index,...) all delegate through inverseIndex
}
```

Mutations write through to `delegate` (it's a live view, not a copy) — `size()` is read fresh from `delegate.size()` on every call, so it reflects concurrent changes to the underlying list.

**Example (constructed, not from repo):**
```java
List<Integer> nums = new ArrayList<>(List.of(1, 2, 3));
List<Integer> reversed = new ReversedList<>(nums);
reversed.get(0); // 3
```

---

## `com.cleanroommc.modularui.utils.ReverseIterable<T>`

Lightweight `Iterable<T>` that walks a `List<T>` back-to-front using the list's own `ListIterator` (cheaper than constructing a `ReversedList` when you only need to iterate once, not index/mutate).

```java
public class ReverseIterable<T> implements Iterable<T> {
    public ReverseIterable(List<T> list);
    public Iterator<T> iterator(); // wraps list.listIterator(list.size()) walking backwards
}
```

Used internally by the screen viewport manager to iterate panels front-to-back for hit-testing/rendering order (`PanelManager` keeps a `private final ReverseIterable<ModularPanel> reversePanels`).

**Example (constructed, not from repo):**
```java
List<String> list = List.of("a", "b", "c");
for (String s : new ReverseIterable<>(list)) {
    System.out.println(s); // c, b, a
}
```

---

## `com.cleanroommc.modularui.utils.SIPrefix`

Enum of SI unit prefixes from `Quetta` (10^30) down to `Quecto` (10^-30), plus `One` (10^0) and two non-numeric sentinels `Infinite`/`Infinitesimal`. Backs both `NumberFormat`'s prefix selection and `MathUtils`'s expression-parser SI-suffix support (`"5k"` → `5000`).

```java
public enum SIPrefix {
    Infinite, Quetta, Ronna, Yotta, Zetta, Exa, Peta, Tera, Giga, Mega, Kilo,
    One,
    Milli, Micro, Nano, Pico, Femto, Atto, Zepto, Yocto, Ronto, Quecto,
    Infinitesimal;

    public final char symbol;          // e.g. 'k', 'M', 'µ'; Character.MIN_VALUE for One
    public final String stringSymbol;
    public final double factor;        // e.g. 1000.0 for Kilo
    public final double oneOverFactor;
    public final BigDecimal bigFactor;
    public final BigDecimal bigOneOverFactor;
    public final boolean infiniteLike;

    public boolean isOne();
    public void addToExpression(Expression e);                       // e.with(symbol, factor)
    public void addToExpression(Expression e, String alternativeSymbol); // + a second alias, e.g. Giga also as "b"
    public static void addAllToExpression(Expression e, boolean biggerThanOne); // registers all (non-One, non-infinite) prefixes as evalex constants

    public static final SIPrefix[] VALUES, HIGH, LOW; // HIGH = descending from Quetta to Kilo, LOW = ascending from Milli to Quecto
}
```

- `Exa` uses symbol `'X'` instead of the real SI `'E'` — noted in source: clashes with Euler's number `e` in the expression grammar.
- `HIGH`/`LOW` split arrays exist to let `NumberFormat.findBestPrefix` binary-scan only the relevant half (values ≥10,000 vs <1) instead of the full enum.
- `addAllToExpression(e, biggerThanOne)` skips `One` and the infinite-like sentinels always; if `biggerThanOne`, also skips every prefix with `factor<1` (used by `MathUtils.parseExpression`'s `biggerThanOne` flag to avoid ambiguous small-unit suffixes in contexts where only whole/large values make sense).

---

## `com.cleanroommc.modularui.utils.TooltipLines`

`AbstractList<String>` that lazily re-wraps a mutable `List<Object>` of tooltip content (mixing `IKey`, plain `String`, `TextIcon`, and an `IKey.LINE_FEED` sentinel) into logical lines on demand, and keeps mutations (`add`/`remove`/`set`) synced back into that underlying element list.

```java
public class TooltipLines extends AbstractList<String> {
    public TooltipLines(List<Object> elements);
    public void clearCache();
    public String get(int index);
    public int size();
    public String remove(int index);
    public void add(int index, String s);
    public String set(int index, String element);
    public void clear();
}
```

- Lines are built lazily and cached (`buildUntil(index)` parses only as far as needed); `clearCache()` forces a full re-parse from scratch (e.g. after the underlying `elements` list was mutated externally).
- A run of elements is cut into a line either at an `IKey.LINE_FEED` marker or at the end of the element list; `IKey`/`String`/`TextIcon` elements are concatenated (via `.get()`/identity/`.getText()` respectively) to form the line's text; other object types contribute nothing to the text but still occupy a slot (relevant for icons embedded inline in a tooltip line, which aren't representable as `String`).
- `set(index, element)` only mutates in place if the target line was a single-element line (`line.length==1`); otherwise it falls back to `remove` + `add`, which discards the original (possibly multi-element/icon-bearing) line entirely and replaces it with a plain string.

**Example (constructed, not from repo — mirrors how `RichTooltip` composes lines internally):**
```java
List<Object> elements = new ArrayList<>();
elements.add(IKey.str("Line one"));
elements.add(IKey.LINE_FEED);
elements.add(IKey.str("Line two"));
TooltipLines lines = new TooltipLines(elements);
lines.get(0); // "Line one"
lines.get(1); // "Line two"
```

---

## `com.cleanroommc.modularui.utils.TreeUtil`

Static traversal/inspection utilities for any `ITreeNode<T>` tree (widgets implement `ITreeNode`, so this is the backbone of widget-tree search/debug tooling). Provides both depth-first (recursive) and breadth-first traversal variants with different performance tradeoffs (documented in the source Javadoc itself).

```java
public class TreeUtil {
    public static boolean allowUnicode; // toggles Unicode (✓/│/├/└) vs ASCII (T/F/|/+/-) tree-print glyphs

    public static <T extends ITreeNode<T>> boolean foreachChildBFS(T parent, Predicate<T> consumer[, boolean includeSelf]);
    public static <T extends ITreeNode<T>> boolean foreachChild(T parent, Predicate<T> consumer[, boolean includeSelf]);
    public static <T extends ITreeNode<T>, V> @Nullable V foreachChildWithResult(T parent, Function<T, V> consumer, boolean includeSelf);
    public static <T extends ITreeNode<T>> boolean foreachChildReverse(T parent, Predicate<T> consumer, boolean includeSelf);
    public static <T extends ITreeNode<T>> Stream<T> flatStreamBFS(T parent);
    public static <T extends ITreeNode<T>> Iterable<T> iterableBFS(T parent);
    public static <T extends ITreeNode<T>> Iterator<T> iteratorBFS(T parent);
    public static <T extends ITreeNode<T>> List<T> flatList(T parent, Predicate<T> test);
    public static <T extends ITreeNode<T>> List<T> flatListBFS(T parent, Predicate<T> test);
    public static <T,R> List<R> flatListByType(T parent, Class<R> type[, Predicate<R> test]);
    public static <T,R> @Nullable R findFirst(T parent, Class<R> type, @Nullable Predicate<R> test); // + Predicate-only overload
    public static <T> @Nullable T findParent(T parent, Predicate<T> filter);
    public static <T,R> @Nullable R findParent(T parent, Class<R> type[, Predicate<R> test]);
    public static <T extends ITreeNode<T>> void print(T parent[, Predicate<T> test][, NodeInfo<T> additionalInfo]);
    public static <T extends ITreeNode<T>> String toString(T parent[, Predicate<T> test][, NodeInfo<T> additionalInfo]);

    public interface NodeInfo<T extends ITreeNode<T>> {
        void addInfo(T root, T widget, StringBuilder builder);
        default NodeInfo<T> combine(NodeInfo<T> other[, String joiner]);
        static <T> NodeInfo<T> of([String joiner,] NodeInfo<T>... infos);
    }
}
```

| Method | Notes |
|---|---|
| `foreachChildBFS(parent, consumer[, includeSelf])` | Breadth-first via an `ObjectList` work queue; `consumer` returning `false` stops early and the method returns `false`. Per source Javadoc, "can outperform `foreachChild` in certain small widget trees." |
| `foreachChild(parent, consumer[, includeSelf])` | Recursive depth-first; per source Javadoc "has the best performance in most cases." |
| `foreachChildWithResult(parent, consumer, includeSelf)` | Like `foreachChild` but the callback returns a nullable value; the *first* non-null return short-circuits the whole traversal and is returned. |
| `foreachChildReverse` | Depth-first, but visits deepest/last children before their ancestors (post-order-ish, reverse of normal). |
| `flatStreamBFS` / `iterableBFS` / `iteratorBFS` | Same BFS order exposed as `Stream`/`Iterable`/`Iterator`; Javadoc explicitly ranks `iteratorBFS` as **~4x slower** than `foreachChildBFS` — prefer the `Predicate`-callback methods in hot paths. |
| `flatList`/`flatListBFS(parent, test)` | Collect all matching descendants (including `parent` itself) into a new `ArrayList`. |
| `flatListByType(parent, type[, test])` | Same, filtered by `Class` (`isAssignableFrom`) plus optional extra predicate. |
| `findFirst(parent, [type,] test)` | First matching descendant (including `parent`), by predicate and/or type. |
| `findParent(parent, [type,] filter)` | Walks **up** via `getParent()` instead of down, returning the first ancestor (including `parent` itself) matching the filter/type. |
| `print`/`toString(parent[, test][, NodeInfo])` | Renders an ASCII-art/Unicode tree graph of the sub-tree (cycle-safe via a `ReferenceOpenHashSet` of visited nodes — prints `"CYCLING TREE FOUND (...)"` instead of recursing infinitely). `print` logs via `ModularUI.LOGGER.info`; `toString` returns the string instead. `NodeInfo` is a pluggable per-node annotator (e.g. `TreeUtil.RESIZE_NODE_INFO_FULLY_RESIZED`/`RESIZE_NODE_INFO_RESIZED_DETAILED`/`RESIZE_NODE_INFO_RESIZED_COLLAPSED` constants for annotating `ResizeNode` layout state) — `NodeInfo.of(...)`/`.combine(...)` compose multiple annotators with a joiner string. |

**Example (constructed, not from repo — typical debug usage):**
```java
TreeUtil.print(rootWidget); // logs the whole widget subtree as a Unicode tree graph
List<Button> allButtons = TreeUtil.flatListByType(rootWidget, Button.class);
```

---

## `com.cleanroommc.modularui.utils.Vector3f`

**`@Deprecated`, `@ApiStatus.ScheduledForRemoval(inVersion = "3.2.0")`.** Legacy LWJGL2-style mutable 3-float vector, kept for backport compatibility; provides `toJoml()`/`set(org.joml.Vector3f)` bridges to migrate to `org.joml.Vector3f`. Prefer `VectorUtil`/`org.joml.Vector3f` in new code.

```java
@ApiStatus.ScheduledForRemoval(inVersion = "3.2.0")
@Deprecated
public class Vector3f {
    public float x, y, z;
    public Vector3f();
    public Vector3f(Vector3f src);
    public Vector3f(Vec3 src);          // net.minecraft.util.Vec3
    public Vector3f(float x, float y, float z);
    public org.joml.Vector3f toJoml();
}
```

| Method | Notes |
|---|---|
| `set(...)` overloads | `(float,float)`, `(float,float,float)`, `(double,double,double)` (narrows), `(Vector3f)`, `(Vec3)`, `(org.joml.Vector3f)` — all mutate in place and (where applicable) return `this`. |
| `lengthSquared()` / `length()` | |
| `translate(x,y,z)` (float or double) | Mutates `this` in place, adds the offset. |
| static `add(left, right[, c], dest)` | `dest=null` allocates a new `Vector3f`; 2-vector and 3-vector overloads. |
| `add(Vector3f)` / `add(Vector3f, dest)` | Instance-method sugar over the static form. |
| static `sub(left, right, dest)` / static `cross(left, right, dest)` / static `dot(left, right)` / static `angle(a, b)` | Standard vector ops; `angle` clamps the cosine to `[-1,1]` before `acos` to avoid `NaN` from float rounding. |
| `negate([dest])` / `normalise([dest])` | `normalise` short-circuits (returns a copy of `this`) if already unit length (`lengthSquared()==1`). |
| `load(FloatBuffer)` / `store(FloatBuffer)` | 3 floats, x/y/z order. |
| `scale(float)` | In-place uniform scale. |
| `distanceTo(v)` / `squareDistanceTo(v \| x,y,z)` | |
| `rotatePitch(pitch)` / `rotateYaw(yaw)` | In-place axis rotation (radians); **note** `rotateYaw`'s local `sin`/`cos` locals are swapped relative to `rotatePitch`'s naming (`sin = MathHelper.cos(yaw)`, `cos = MathHelper.sin(yaw)`) — this is in the source as-is; treat the two methods' exact rotation direction as something to verify empirically rather than assume from the variable names. |
| `copy()` | New `Vector3f` with same components. |
| `toVec3d()` | → `net.minecraft.util.Vec3`. |
| `equals`/`hashCode` | Component-wise. |

---

## `com.cleanroommc.modularui.utils.VectorUtil`

Static helpers for `org.joml` vector types — conversions between `Vector3f`/`Vector3d`/`Vector3i`, and "mutate-or-allocate" setters (pass an existing vector to reuse, or `null` to allocate).

```java
public class VectorUtil {
    public static final Vector3fc UNIT_X, UNIT_Y, UNIT_Z; // (1,0,0), (0,1,0), (0,0,1)

    public static Vec3 toVec3d(Vector3f vec); // net.minecraft.util.Vec3

    public static Vector3f set(@Nullable Vector3f target, float x, float y, float z);
    public static @NotNull Vector3f set(@Nullable Vector3f target, Vector3d vec);
    public static @NotNull Vector3f set(@Nullable Vector3f target, Vector3i vec);

    public static Vector3f vec3f(Vector3d vec3d); // = set(null, vec3d)
    public static Vector3f vec3f(Vector3i vec3i); // = set(null, vec3i)

    public static Vector3f vec3fAdd(Vector3f source, @Nullable Vector3f target, float x, float y, float z);
    public static @NotNull Vector3f vec3fAdd(Vector3f source, @Nullable Vector3f target, Vector3i vec);
    public static @NotNull Vector3f vec3fAdd(Vector3f source, @Nullable Vector3f target, Vector3d vec);
}
```

- Every `set`/`vec3fAdd` overload follows the same pattern: if `target` is `null`, a new `org.joml.Vector3f` is allocated; otherwise `target` is mutated and returned — lets calling code avoid per-frame allocation by reusing a cached vector field.
- `vec3fAdd(source, target, x, y, z)` — if `source` is `null`, behaves like `set(target, x, y, z)` (treats the add as a plain set); otherwise copies `source` into `target` (unless they're already the same object) and then adds `(x,y,z)` via JOML's in-place `Vector3f.add`.

**Example (constructed, not from repo):**
```java
Vector3f cached = null;
cached = VectorUtil.set(cached, 1f, 2f, 3f); // allocates the first time (target == null)
cached = VectorUtil.set(cached, 4f, 5f, 6f); // reuses `cached` in place, no new allocation
```
