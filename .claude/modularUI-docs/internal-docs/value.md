# `value` / `value.sync` package reference

Packages: `com.cleanroommc.modularui.value`, `com.cleanroommc.modularui.value.sync`

This is the client-server data-binding system for the whole library. Two layers exist:

- **`value/`** — plain, non-networked value wrappers. They implement `com.cleanroommc.modularui.api.value.IValue<T>` (`getValue()`/`setValue()`/`getValueType()`) and its typed siblings (`IBoolValue`, `IIntValue`, `IStringValue`, ...). They just wrap a field, a getter/setter pair, or an `Atomic*`. Used with widgets that don't need networking (single-player-only state, purely client-side widgets), e.g. `TextFieldWidget.value(StringValue)`, `ToggleButton.value(new BoolValue.Dynamic(...))`.
- **`value/sync/`** — the actual client↔server sync layer. `SyncHandler<S>` is the abstract root; `ValueSyncHandler<T,S>` adds a typed cached value with automatic diffing (`detectAndSendChanges`); concrete `*SyncValue` classes (`IntSyncValue`, `StringSyncValue`, `EnumSyncValue`, ...) mirror the `value/` primitive family but network the value instead of just holding it. Sync handlers are registered on a `PanelSyncManager`, which is itself managed per-panel by a `ModularSyncManager` (one per open GUI). README's headline example, `.child(new FluidSlot().syncHandler(new FluidTank(16000)))`, is shorthand for constructing a `FluidSlotSyncHandler` and registering it through `ISyncRegistrar`.

Both layers are largely orthogonal: a widget takes either an `IValue` (local) or a `SyncHandler`/`IValueSyncHandler` (networked), rarely both.

---

## Package `com.cleanroommc.modularui.value`

### The `IValue<T>` contract (context, not in scope)

`com.cleanroommc.modularui.api.value.IValue<T>` declares `T getValue()`, `void setValue(T)`, `Class<T> getValueType()`. Typed sibling interfaces (`IBoolValue<T>`, `IByteValue<T>`, `IShortValue<T>`, `IIntValue<T>`, `ILongValue<T>`, `IFloatValue<T>`, `IDoubleValue<T>`, `IStringValue<T>`, `IEnumValue<T>`) add a primitive-typed getter/setter pair (e.g. `getBoolValue()`/`setBoolValue(boolean)`) with default bridging to `getIntValue()`/`getValue()`. Every class below implements one or more of these.

### Primitive value family

All of these follow the same shape: a mutable field wrapper class, plus a nested `static class Dynamic` (or `Dynamic<T>`) that instead holds a getter/setter functional pair (so the "source of truth" lives elsewhere, e.g. a TileEntity field). Every class has a static `wrap(...)` that adapts an existing `I*Value` into a `Dynamic`, and (where an `Atomic*` JDK/Guava type exists) a static `wrapAtomic(...)`.

| Type | `value/` class | Implements | Wraps via `wrap(I*Value)` | `wrapAtomic` |
|---|---|---|---|---|
| `boolean`/`Boolean` | `BoolValue` | `IBoolValue<Boolean>`, `IStringValue<Boolean>` | yes | `AtomicBoolean` |
| `byte`/`Byte` | `ByteValue` | `IByteValue<Byte>` | yes | — |
| `short`/`Short` | `ShortValue` | `IShortValue<Short>`, `IIntValue<Short>` | yes | — |
| `int`/`Integer` | `IntValue` | `IIntValue<Integer>`, `IDoubleValue<Integer>`, `IStringValue<Integer>` | yes | `AtomicInteger` |
| `long`/`Long` | `LongValue` | `ILongValue<Long>`, `IIntValue<Long>`, `IStringValue<Long>` | yes | `AtomicLong` |
| `float`/`Float` | `FloatValue` | `IFloatValue<Float>`, `IDoubleValue<Float>`, `IStringValue<Float>` | yes | `AtomicDouble` (Guava, via `floatValue()`) |
| `double`/`Double` | `DoubleValue` | `IDoubleValue<Double>`, `IFloatValue<Double>`, `IStringValue<Double>` | yes | `AtomicDouble` (Guava) |
| `String` | `StringValue` (extends `ObjectValue<String>`) | `IStringValue<String>` | yes | — |
| `T extends Enum<T>` | `EnumValue<T>` | `IEnumValue<T>`, `IIntValue<T>` | yes (`Dynamic<T>`) | — |

Gotchas / notes common to the whole family:
- `ByteValue` and `ShortValue` do **not** get a `Dynamic` variant generated from lambdas directly usable as `java.util.function.*` — they define their own tiny `Supplier`/`Consumer` functional interfaces (`ByteValue.Supplier#getByte()`, `ByteValue.Consumer#setByte(byte)`, and the `Short` equivalents) because the JDK has no `ByteSupplier`/`ShortSupplier`. These same interfaces are reused by `ByteSyncValue`/`ShortSyncValue` in `value.sync`.
- `FloatValue` implements both `IFloatValue` and `IDoubleValue`; `setValue(Float)` internally calls `setDoubleValue(value)` (routes through the double path, then narrows).
- `IntValue`/`LongValue`/`ShortValue`/`ByteValue` all bridge to `IIntValue` (`getIntValue()`/`setIntValue(int)`), which is what widgets like `CycleButtonWidget` bind against generically.
- `Dynamic` variants throw nothing special on null setters except where documented (`ObjectValue.Dynamic` allows a `null` setter — see below).

**Example (constructed, not from repo) — local (non-synced) primitive value:**
```java
import com.cleanroommc.modularui.value.IntValue;

IntValue integer = new IntValue(0);
// ... later, e.g. inside a click handler:
int i = integer.getIntValue() + 1;
integer.setIntValue(i);
```
(adapted from `src/main/java/com/cleanroommc/modularui/test/TestGuis.java:323,373-374`, used to drive a `RichTextWidget`'s dynamic key counter — this value is never synced, it's pure client-side render state)

**Example (real usage) — `Dynamic` variant bound to array-backed state, from `test/TestGuis.java:207`:**
```java
boolean[][] states = ...;
new ToggleButton()
        .overlay(GuiTextures.BOOKMARK)
        .value(new BoolValue.Dynamic(() -> states[i][j], val -> states[i][j] = val))
        .size(10)
```

**Example (real usage) — `StringValue` bound to a `TextFieldWidget`, from `test/TestGuis.java:450,455`:**
```java
StringValue searchValue = new StringValue("");
new TextFieldWidget()
        .value(searchValue)
        .height(16)
        .widthRel(1f)
        .autoUpdateOnChange(true);
// elsewhere: searchValue.getStringValue() reads back the live text
```

---

### `com.cleanroommc.modularui.value.BoolValue`

```java
public class BoolValue implements IBoolValue<Boolean>, IStringValue<Boolean>
```
Wraps a plain `boolean` field.

| Member | Signature | Notes |
|---|---|---|
| ctor | `BoolValue(boolean value)` | |
| `wrap(IBoolValue<?>)` | static → `Dynamic` | Adapts any `IBoolValue` into a getter/setter `Dynamic`. |
| `wrapAtomic(AtomicBoolean)` | static → `Dynamic` | |
| `getValue`/`setValue` | `Boolean` | delegates to bool accessors |
| `getBoolValue`/`setBoolValue` | `boolean` | backing field access |
| `getStringValue`/`setStringValue` | `String` | `String.valueOf` / `Boolean.parseBoolean` |
| `getValueType()` | `Class<Boolean>` | |

`Dynamic` (nested `static class`) additionally implements `IIntValue<Boolean>` (`getIntValue()` returns `1`/`0`) and takes `(BooleanSupplier getter, BooleanConsumer setter)` (`BooleanConsumer` is `com.cleanroommc.modularui.utils.BooleanConsumer`, a JDK-missing functional type).

### `com.cleanroommc.modularui.value.ByteValue` / `com.cleanroommc.modularui.value.ShortValue`

```java
public class ByteValue implements IByteValue<Byte>
public class ShortValue implements IShortValue<Short>, IIntValue<Short>
```
Both define their own `Supplier`/`Consumer` nested functional interfaces (no JDK `ByteSupplier`/`ShortSupplier` exist):
```java
public interface Supplier { byte getByte(); }      // ShortValue: short getShort();
public interface Consumer { void setByte(byte b); } // ShortValue: void setShort(short b);
```
`Dynamic` extends the outer class directly (not a separate hierarchy) and overrides `getByteValue`/`setByteValue` (resp. `getShortValue`/`setShortValue`) to delegate to the supplier/consumer. `ShortValue` additionally bridges to `IIntValue<Short>` (`getIntValue()` widens, `setIntValue(int)` narrows with `(short)`).

### `com.cleanroommc.modularui.value.IntValue` / `LongValue` / `FloatValue` / `DoubleValue`

```java
public class IntValue implements IIntValue<Integer>, IDoubleValue<Integer>, IStringValue<Integer>
public class LongValue implements ILongValue<Long>, IIntValue<Long>, IStringValue<Long>
public class FloatValue implements IFloatValue<Float>, IDoubleValue<Float>, IStringValue<Float>
public class DoubleValue implements IDoubleValue<Double>, IFloatValue<Double>, IStringValue<Double>
```
Standard field wrapper + `Dynamic` (separate top-level-ish nested class implementing the same interfaces except the numeric widening one is same as outer, backed by `IntSupplier`/`IntConsumer` etc., or `LongSupplier`/`LongConsumer`, or the library's own `FloatSupplier`/`FloatConsumer`, or `DoubleSupplier`/`DoubleConsumer`). `LongValue.getIntValue()` narrows with `(int)`; `setIntValue` widens. `FloatValue.wrapAtomic(AtomicDouble)` uses Guava's `AtomicDouble` (`float`/`double` interop, since there's no `AtomicFloat` in the JDK).

### `com.cleanroommc.modularui.value.EnumValue<T extends Enum<T>>`

```java
public class EnumValue<T extends Enum<T>> implements IEnumValue<T>, IIntValue<T>
```

| Member | Signature | Notes |
|---|---|---|
| ctor | `EnumValue(Class<T> enumClass, T value)` | |
| `wrap(IEnumValue<T>)` | static → `Dynamic<T>` | |
| `getValue`/`setValue` | `T` | |
| `getIntValue()` | `int` | `value.ordinal()` |
| `setIntValue(int)` | | `enumClass.getEnumConstants()[val]` — **gotcha:** out-of-range `val` throws `ArrayIndexOutOfBoundsException`. |
| `getEnumClass()` | `Class<T>` | |

`Dynamic<T>` holds `(Class<T> enumClass, Supplier<T> getter, Consumer<T> setter)`.

### `com.cleanroommc.modularui.value.BinaryEnumValue<T extends Enum<T>>`

```java
public class BinaryEnumValue<T extends Enum<T>> extends EnumValue<T> implements IBoolValue<T>
```
For 2-constant enums used as a boolean toggle (e.g. an on/off enum instead of `Boolean`). Constructor `BinaryEnumValue(Class<T> enumClass, T value)` **throws `IllegalArgumentException`** if `enumClass.getEnumConstants().length != 2`. `getBoolValue()` is `ordinal() == 1`; `setBoolValue(boolean)` picks `enumClass.getEnumConstants()[val ? 1 : 0]`. Static `wrap(V val)` (where `V extends EnumValue<T> & IBoolValue<T>`) produces a `BinaryEnumValue.Dynamic<T>`, which extends `EnumValue.Dynamic<T>` and adds the same bool bridge.

### `com.cleanroommc.modularui.value.ObjectValue<T>`

```java
public class ObjectValue<T> implements IValue<T>
```

| Member | Signature | Notes |
|---|---|---|
| ctor | `ObjectValue(Class<T> type, T value)` | preferred — explicit type avoids null-value edge cases |
| ctor (deprecated) | `ObjectValue(T value)` | infers type from `value.getClass()`; **scheduled for removal in 3.2.0**; breaks if `value` is `null` |
| `wrap(IValue<T>)` | static → `Dynamic<T>` (deprecated overload infers type from current getter value) | |
| `wrapAtomic(AtomicReference<T>)` | static → `Dynamic<T>` | |
| `getValueType()` | `Class<T>` | falls back to `value.getClass()` if `type` field is null (deprecated ctor path) |

`StringValue` extends `ObjectValue<String>` and is the base for `StringValue.Dynamic` too (see below).

### `com.cleanroommc.modularui.value.StringValue`

```java
public class StringValue extends ObjectValue<String> implements IStringValue<String>
```
`StringValue(String value)` calls `super(String.class, value)`. `Dynamic` extends `ObjectValue.Dynamic<String>` and accepts a **nullable setter** (`@Nullable Consumer<String> setter`) — a read-only synced string display is possible by passing `null`.

### Deprecated / legacy value classes

| Class | Status | Replacement |
|---|---|---|
| `com.cleanroommc.modularui.value.ConstValue<T>` | `@Deprecated`, `@ApiStatus.ScheduledForRemoval(inVersion = "3.2.0")` | `ObjectValue<T>` |
| `com.cleanroommc.modularui.value.DynamicValue<T>` | `@Deprecated`, `@ApiStatus.ScheduledForRemoval(inVersion = "3.2.0")` | `ObjectValue.Dynamic<T>` |

Both simply wrap a getter (+nullable setter for `DynamicValue`) and infer `getValueType()` from the current value's runtime class — do not use in new code.

---

## Package `com.cleanroommc.modularui.value.sync`

### Architecture overview

```
ModularSyncManager (1 per open GUI/container, client AND server instance)
 └─ PanelSyncManager (1 per open panel; "main" one bound at construct())
     ├─ SyncHandler<S> ............. registered under a "name:id" key (ISyncRegistrar.makeSyncKey)
     │   └─ ValueSyncHandler<T,S> .. adds a diffable cached value + detectAndSendChanges
     │       ├─ *SyncValue classes (Int/Long/Bool/Enum/String/Generic/...)
     │       ├─ ItemSlotSH / PhantomItemSlotSH
     │       └─ FluidSlotSyncHandler
     ├─ SlotGroup registrations
     ├─ SyncedAction registrations (fire-and-forget RPCs, not value-backed)
     └─ sub PanelSyncHandler entries (nested/secondary panels)
```

Registration must happen identically and deterministically on **both** client and server while the panel is being built (`PanelSyncManager` is locked afterwards — see `putSyncValue`). The only sanctioned way to register handlers after that point is from inside a `DynamicSyncHandler`/`DynamicLinkedSyncHandler` widget-provider callback (which temporarily unlocks registration) or through `getOrCreateSyncHandler`.

### `com.cleanroommc.modularui.value.sync.SyncHandler<S extends SyncHandler<S>>`

```java
public abstract class SyncHandler<S extends SyncHandler<S>> implements ISyncOrValue
```
Abstract root of every sync handler. Self-typed generic `S` so fluent setters (`allowC2S()`) return the concrete subtype.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `init(String key, PanelSyncManager syncManager)` | assigned key + owning manager | - | `@ApiStatus.OverrideOnly`, `@MustBeInvokedByOverriders`. Called once when the handler is registered/panel opens; stores `key`/`syncManager`. Overrides (e.g. `ItemSlotSH`, `PanelSyncHandler`) must call `super.init(...)` first, then do their own setup (e.g. registering the wrapped `Slot`). |
| `dispose()` | - | - | `@MustBeInvokedByOverriders`. Clears `key`/`syncManager`, making `isValid()` false again. Called when the panel closes. |
| `syncToClient(int id, IPacketWriter)` | packet id + writer | - | `final`. Server→client only; builds a `PacketBuffer`, writes `id` as varint, then delegates to the writer, then sends via `sendToClient`. |
| `syncToServer(int id, IPacketWriter)` | packet id + writer | - | `final`, `@SideOnly(CLIENT)`. **Gotcha:** if `isAllowC2S()` is false, logs a `SecurityException` warning and silently no-ops (also chats the player an error message) instead of sending — this is the guard against malicious/buggy C2S packets. |
| `sync(int id, IPacketWriter)` | packet id + writer | - | `final`. Sends to "the other side" (server if called on client, client if called on server) via `ModularNetwork.get(isClient())`. Same C2S guard as `syncToServer` applies when called from the client. |
| `syncToClient(int id)` / `syncToServer(int id)` / `sync(int id)` | id only | - | Convenience overloads sending an empty-body packet. |
| `readOnClient(int id, PacketBuffer buf)` | - | - | `abstract`, `@ApiStatus.OverrideOnly`, `@SideOnly(CLIENT)`. Dispatch point for packets arriving on the client for this handler. |
| `readOnServer(int id, PacketBuffer buf)` | - | - | `abstract`, `@ApiStatus.OverrideOnly`. Same, server side. Only invoked if `isAllowC2S()` (enforced by `PanelSyncManager.receiveWidgetUpdate`). |
| `detectAndSendChanges(boolean init)` | `init`=first tick of the panel | - | **Server-side only.** No-op by default (`{}`); `ValueSyncHandler` overrides it to diff+sync. Called every server tick per `PanelSyncManager.detectAndSendChanges`, which is itself called every tick by `ModularSyncManager.detectAndSendChanges`. |
| `getKey()` | - | `String` | The `"name:id"` registration key, or `null` before `init`. |
| `isValid()` | - | `boolean` | `key != null && syncManager != null`. |
| `getSyncManager()` | - | `PanelSyncManager` | Throws `IllegalStateException` if not yet initialised. |
| `isRegistered(PanelSyncManager)` | | `boolean` | Checks both this handler's own manager and the passed-in one. |
| `isAllowC2S()` / `allowC2S(boolean)` / `allowC2S()` | | `boolean` / `S` / `S` | Defaults to `false`. **Must be identical on client and server.** Handlers that accept player-driven input (buttons, phantom slots, cursor slot, interaction handlers) call `allowC2S()` in their constructor. |

**Gotcha:** `detectAndSendChanges` only ever runs server-side (`PanelSyncManager.detectAndSendChanges` early-returns `if (!isClient())`... actually guards the loop to run only `if (!isClient())`) — client state is purely a receiver, updated via `readOnClient`.

### `com.cleanroommc.modularui.value.sync.ValueSyncHandler<T, S extends ValueSyncHandler<T,S>>`

```java
public abstract class ValueSyncHandler<T, S extends ValueSyncHandler<T, S>> extends SyncHandler<S> implements IValueSyncHandler<T>
```
Adds the "typed cached value with automatic diff-and-sync" behavior all `*SyncValue` classes build on. `IValueSyncHandler<T>` (in `api.value.sync`) requires: `setValue(T, boolean setSource, boolean sync)`, `updateCacheFromSource(boolean isFirstSync)`, `notifyUpdate()`, `write(PacketBuffer)`, `read(PacketBuffer)`.

| Member | Notes |
|---|---|
| `SYNC_VALUE = 0` | packet-id constant reused by all subclasses for the "full value" packet. |
| `readOnClient`/`readOnServer` | Both already implemented here: `if (id == SYNC_VALUE) read(buf);` — subclasses that need more packet ids (e.g. `FluidSlotSyncHandler`, `ItemSlotSH` is not a `ValueSyncHandler` though) override and call further ids. |
| `sync()` (protected) | `sync(SYNC_VALUE, this::write)` — pushes the current cached value to the other side immediately. |
| `detectAndSendChanges(boolean init)` | `if (updateCacheFromSource(init)) sync();` — **this is the tick loop**: every server tick, compare cache vs. the getter's live value; if different (or `init`), sync. |
| `onValueChanged()` (protected) | Subclasses call this from inside their `setValue(...)` implementation whenever the cache actually changes; it invokes the registered `changeListener` (used by `DynamicLinkedSyncHandler` to know when its linked value updated). |
| `setChangeListener(Runnable)` / `changeListener(Runnable)` (fluent, returns `S`) / `getChangeListener()` | Wiring for `onValueChanged()`. |

**Lifecycle summary (server):** `init()` → cache = getter's initial value → every tick, `detectAndSendChanges(init)` → `updateCacheFromSource` compares getter's current value to cache → if changed, `setValue(newVal, setSource=false, sync=false)` updates cache and calls `onValueChanged()` → back in `detectAndSendChanges`, `sync()` writes the packet. **On client**, `readOnClient` calls `read(buf)` which deserializes and calls `setValue(newVal, setSource=true, sync=false)` — `setSource=true` means the client-side getter/setter pair (if any) is also updated so client-only code observing the same field sees the change.

### Primitive `*SyncValue` family (mirrors `value/`)

All extend `ValueSyncHandler<T,S>` directly (not `AbstractGenericSyncValue`), keep the cached value as a raw primitive field (`cache`), and share this constructor shape:

```java
XxxSyncValue(@NotNull XxxSupplier getter, @Nullable XxxConsumer setter)   // single-getter, used on both sides
XxxSyncValue(@NotNull XxxSupplier getter)                                 // setter = null (server pushes, client can't mutate source)
XxxSyncValue(@Nullable XxxSupplier clientGetter, @Nullable XxxSupplier serverGetter) // side-specific getter, no setters
XxxSyncValue(@Nullable XxxSupplier clientGetter, @Nullable XxxConsumer clientSetter,
             @Nullable XxxSupplier serverGetter, @Nullable XxxConsumer serverSetter) // fully side-specific
```
The 2-getter and 4-arg constructors pick client vs. server getter/setter via `NetworkUtils.isClient()` at construction time and **throw `NullPointerException`** if both getters are null (`@Contract("null, null -> fail")` / `@Contract("null, _, null, _ -> fail")` document this).

| Type | `value.sync` class | Extra interfaces | Wire format |
|---|---|---|---|
| `Boolean` | `BooleanSyncValue` | `IBoolSyncValue`, `IStringSyncValue` | `buffer.writeBoolean` |
| `Byte` | `ByteSyncValue` | `IByteSyncValue` | `buffer.writeByte` — getter/setter type is `com.cleanroommc.modularui.value.ByteValue.Supplier`/`.Consumer` (reused from `value/`) |
| `Short` | `ShortSyncValue` | `IShortSyncValue`, `IIntSyncValue`, `IStringSyncValue` | `buffer.writeShort` — getter/setter type is `ShortValue.Supplier`/`.Consumer` |
| `Integer` | `IntSyncValue` | `IIntSyncValue`, `IDoubleSyncValue`, `IStringSyncValue` | `buffer.writeVarIntToBuffer` (varint, not fixed 4 bytes) |
| `Long` | `LongSyncValue` | `ILongSyncValue`, `IIntSyncValue`, `IStringSyncValue` | `buffer.writeLong` |
| `Float` | `FloatSyncValue` | `IFloatSyncValue`, `IDoubleSyncValue`, `IStringSyncValue` | `buffer.writeFloat` |
| `Double` | `DoubleSyncValue` | `IDoubleSyncValue`, `IFloatSyncValue`, `IStringSyncValue` | `buffer.writeDouble` |
| `T extends Enum<T>` | `EnumSyncValue<T,S>` | `IEnumValue<T>`, `IIntSyncValue<T>` | `NetworkUtils.writeEnumValue`/`readEnumValue` (ordinal-based); `setIntValue` indexes `enumClass.getEnumConstants()[value]` — out-of-range throws |
| `T extends Enum<T>` (2 constants) | `BinaryEnumSyncValue<T,S>` | extends `EnumSyncValue`, + `IBoolSyncValue<T>` | same as `EnumSyncValue`; ctor throws `IllegalArgumentException` if enum doesn't have exactly 2 constants |
| `String` | `StringSyncValue` | extends `AbstractGenericSyncValue` (see below), `IStringSyncValue` | length-prefixed via `NetworkUtils.writeStringSafe`/`readStringSafe`, capped at `Short.MAX_VALUE - 74` bytes |

All `setXxxValue(value, setSource, sync)` implementations follow the identical pattern:
```java
this.cache = value;
if (setSource && this.setter != null) this.setter.accept(value); // or the primitive-specific accept method
onValueChanged();
if (sync) sync();
```

**Example (real usage, `test/TestTile.java:114-115`) — simple int sync bound to a TileEntity field:**
```java
IntSyncValue cycleStateValue = new IntSyncValue(() -> this.cycleState, val -> this.cycleState = val);
syncManager.getHyperVisor().syncValue("cycle_state", cycleStateValue);
```

**Example (real usage, `test/TestTile.java:116`) — read-only (no setter) double sync for a progress bar:**
```java
syncManager.syncValue("progress", new DoubleSyncValue(() -> (double) this.progress / this.duration));
```

**Example (real usage, `test/TestTile.java:349`) — string sync bound to a local field via lambdas:**
```java
panel.child(new TextFieldWidget()
        .size(60, 14).pos(10, 80)
        .value(new StringSyncValue(() -> s, v -> s = v)));
```

### `com.cleanroommc.modularui.value.sync.AbstractGenericSyncValue<T, S>`

```java
public abstract class AbstractGenericSyncValue<T, S extends AbstractGenericSyncValue<T, S>> extends ValueSyncHandler<T, S>
```
For reference-typed values that need explicit copy/serialize/deserialize/equals hooks (as opposed to the raw-primitive family above, and as opposed to `GenericSyncValue` which is a concrete usable subclass with pluggable functions). Subclasses implement:
```java
protected abstract T createDeepCopyOf(T value);
protected abstract boolean areEqual(T a, T b);
protected abstract void serialize(PacketBuffer buffer, T value) throws IOException;
protected abstract T deserialize(PacketBuffer buffer) throws IOException;
```

| Method | Notes |
|---|---|
| ctor `(Class<T> type, Supplier<T> getter, Consumer<T> setter)` | Infers `type` from the getter's current value if `type == null`; **throws `IllegalArgumentException`** if both are null. |
| ctor `(Class<T> type, clientGetter, clientSetter, serverGetter, serverSetter)` | Same client/server split pattern as the primitive family; `@Contract("_, null, _, null, _ -> fail")`. |
| `setValue(T value, boolean setSource, boolean sync)` | `cache = createDeepCopyOf(value)`, then `onSetCache(setSource, sync)`. |
| `modifyValue(Consumer<T> consumer)` | Mutates the cached value **in place** then forces a sync (`setSource=true, sync=true`). Use this instead of `getValue()` + external mutation, since plain mutation of the returned reference would desync `equals`/cache tracking. |
| `modifyValue(boolean setSource, boolean sync, Consumer<T> consumer)` | Same, with explicit control over whether source/sync fire. |
| `cast()` | Unchecked cast to `AbstractGenericSyncValue<V, ?>` — escape hatch for generic type erasure situations. |
| `getType()` / `isOfType(Class<?>)` | `@Deprecated`, scheduled removal 3.2.0 — use `getValueType()` / `isValueOfType(Class<?>)`. |

`StringSyncValue` (documented above) is the only direct concrete subclass in this codebase; `String` is immutable so `createDeepCopyOf` just returns the same reference.

### `com.cleanroommc.modularui.value.sync.GenericSyncValue<T, S>`

```java
public class GenericSyncValue<T, S extends GenericSyncValue<T, S>> extends AbstractGenericSyncValue<T, S>
```
The general-purpose "sync any type" handler — highly configurable via a `Builder<T>`. Most non-primitive, non-collection sync needs (custom NBT-like objects, `ItemStack`, `FluidStack`, ...) go through this. **All non-builder constructors are `@Deprecated`/`@ApiStatus.Obsolete`** in favor of the builder.

| Static factory | Signature | Notes |
|---|---|---|
| `builder(Class<T> type)` | → `Builder<T>` | **Recommended** entry point. |
| `rawTypeBuilder(Class<?> type)` | → `Builder<T>` (unchecked) | For generic-parameterized types the compiler can't verify, e.g. `GenericSyncValue.<List<ItemStack>>rawTypeBuilder(List.class)`. |
| `notNullBuilder()` | → `Builder<T>` | For values that are never null — type inferred from the getter's first returned value. |
| `forItem(Supplier<ItemStack>, Consumer<ItemStack>)` | → `GenericSyncValue<ItemStack,?>` | Shortcut using `ByteBufAdapters.ITEM_STACK`. |
| `forFluid(Supplier<FluidStack>, Consumer<FluidStack>)` | → `GenericSyncValue<FluidStack,?>` | Shortcut using `ByteBufAdapters.FLUID_STACK`. |

`Builder<T>` methods: `getter(Supplier<T>)` *(required)*, `setter(Consumer<T>)` *(optional)*, `deserializer(IByteBufDeserializer<T>)` *(required)*, `serializer(IByteBufSerializer<T>)` *(required)*, `equals(IEquals<T>)` *(optional, defaults to `Objects::equals` — must be set if `T` doesn't implement `equals()` meaningfully, otherwise the value re-syncs every tick)*, `equalsDefault()`, `copy(ICopy<T>)` *(optional, defaults to round-tripping through serializer/deserializer)*, `copyImmutable()` *(skip copying — only for genuinely immutable `T`)*, `adapter(IByteBufAdapter<T>)` *(sets deserializer+serializer+equals at once)*, `nullable()` *(wraps all functions null-safe)*, `build()` → `GenericSyncValue<T,?>`.

**Example (real usage, `test/TestTile.java:117`):**
```java
syncManager.syncValue("display_item", GenericSyncValue.forItem(() -> this.displayItem, null));
```

### `BigDecimalSyncValue` / `BigIntSyncValue` / `ByteArraySyncValue` / `LongArraySyncValue`

All four are thin `GenericSyncValue` subclasses pre-wired with a `ByteBufAdapters` constant and a copy strategy:

```java
public class BigDecimalSyncValue extends GenericSyncValue<BigDecimal, BigDecimalSyncValue> implements IStringValue<BigDecimal>
public class BigIntSyncValue extends GenericSyncValue<BigInteger, BigIntSyncValue> implements IStringValue<BigInteger>
public class ByteArraySyncValue extends GenericSyncValue<byte[], ByteArraySyncValue>
public class LongArraySyncValue extends GenericSyncValue<long[], LongArraySyncValue>
```

| Class | Ctor(s) | Adapter | Copy | Extra |
|---|---|---|---|---|
| `BigDecimalSyncValue` | `(Supplier<BigDecimal>, @Nullable Consumer<BigDecimal>)`, `+boolean nullable` | `ByteBufAdapters.BIG_DECIMAL` | `ICopy.immutable()` | `getStringValue()`/`setStringValue(String)` via `toString()`/`new BigDecimal(val)` |
| `BigIntSyncValue` | same shape | `ByteBufAdapters.BIG_INT` | `ICopy.immutable()` | same string bridge via `new BigInteger(val)` |
| `ByteArraySyncValue` | `(Supplier<byte[]>, @Nullable Consumer<byte[]>)`, `+boolean nullable` | `ByteBufAdapters.BYTE_ARR` | `byte[]::clone` | — |
| `LongArraySyncValue` | `(Supplier<long[]>, @Nullable Consumer<long[]>)`, `+boolean nullable` | `ByteBufAdapters.LONG_ARR` | `long[]::clone` | — |

Note arrays use `clone()` as their copy strategy (shallow but sufficient for primitive arrays) rather than the default serializer round-trip.

### Collection sync handlers: `GenericCollectionSyncHandler` / `GenericListSyncHandler` / `GenericSetSyncHandler` / `GenericMapSyncHandler`

```java
public abstract class GenericCollectionSyncHandler<T, C extends Collection<T>, S extends GenericCollectionSyncHandler<T, C, S>> extends ValueSyncHandler<C, S>
public class GenericListSyncHandler<T> extends GenericCollectionSyncHandler<T, List<T>, GenericListSyncHandler<T>>
public class GenericSetSyncHandler<T> extends GenericCollectionSyncHandler<T, Set<T>, GenericSetSyncHandler<T>>
public class GenericMapSyncHandler<K, V> extends ValueSyncHandler<Map<K, V>, GenericMapSyncHandler<K, V>>
```

Common shape: an internal mutable cache collection (`ObjectList<T>` for list, `ObjectOpenHashSet<T>` for set, `Object2ObjectOpenHashMap<K,V>` for map — all fastutil), full-collection wire format (`writeVarIntToBuffer(size)` then each element serialized), and change detection by full comparison (`didValuesChange`). `getValue()` returns an **unmodifiable view** of the cache (`Collections.unmodifiableList/Set/Map`) — mutate via `modifyValue(Consumer<C>)` (forces sync) or `modifyValue(boolean setSource, boolean sync, Consumer<C>)`, same pattern as `AbstractGenericSyncValue`.

`GenericCollectionSyncHandler` (abstract base for list/set) constructor takes `(Supplier<C> getter, @Nullable Consumer<C> setter, IByteBufDeserializer<T> deserializer, IByteBufSerializer<T> serializer, @Nullable IEquals<T> equals, @Nullable ICopy<T> copy)` and exposes a `Builder<T,C,B>` with `getter`, `setter`, `deserializer`, `serializer`, `adapter(IByteBufAdapter<T>)`, `copy`, `immutableCopy()`; `equals(...)` is `protected` on the base builder (sets use identity/hashCode via the `Set` contract instead of a custom equals — `GenericSetSyncHandler.didValuesChange` uses `cache.containsAll(newValues)`).

`GenericListSyncHandler.Builder<T>` (via `GenericListSyncHandler.builder()`) adds `getterArray(Supplier<T[]>)` and `setterArray(Consumer<T[]>, IntFunction<T[]> arrayFactory)` convenience wrappers for array-backed fields, and exposes `equals(IEquals<T>)` publicly (lists compare index-by-index). `GenericSetSyncHandler.builder()` gives the plain base builder without publicizing `equals`.

`GenericMapSyncHandler` is not a `GenericCollectionSyncHandler` (map isn't a `Collection`) but mirrors the same idea with `GenericMapSyncHandler.Builder<K,V>`: `getter`, `setter`, `keyDeserializer`/`valueDeserializer`, `keySerializer`/`valueSerializer`, `keyAdapter`/`valueAdapter`, `equals` (value equality only — keys are compared via map key equality), `keyCopy`/`valueCopy`, `immutableKey()`/`immutableValue()`, `build()`.

**Example (real usage, `test/TestTile.java:118-125`):**
```java
GenericListSyncHandler<Integer> numberListSyncHandler = GenericListSyncHandler.<Integer>builder()
        .getter(() -> this.serverInts)
        .setter(v -> this.serverInts = v)
        .serializer(PacketBuffer::writeInt)
        .deserializer(PacketBuffer::readInt)
        .immutableCopy()
        .build();
syncManager.syncValue("number_list", numberListSyncHandler);
```

---

### `com.cleanroommc.modularui.value.sync.ISyncRegistrar<S extends ISyncRegistrar<S>>`

```java
public interface ISyncRegistrar<S extends ISyncRegistrar<S>>
```
Shared registration API implemented by both `PanelSyncManager` and `ModularSyncManager` (which mostly delegates to its "main" `PanelSyncManager`). This is the interface actually called from `buildUI(...)` methods.

| Method | Notes |
|---|---|
| `syncValue(String name, int id, SyncHandler<?>)` / `syncValue(String name, SyncHandler<?>)` (id=0) / `syncValue(int id, SyncHandler<?>)` (name="_") | Register a handler under key `"name:id"`. **Must be called identically on client and server, during panel building.** |
| `itemSlot(String key, int id, ModularSlot)` / 2-arg / `itemSlot(int id, ModularSlot)` | Shortcut: `syncValue(key, id, new ItemSlotSH(slot))`. |
| `dynamicSyncHandler(String key, int id, DynamicSyncHandler.IWidgetProvider)` | Creates+registers a `DynamicSyncHandler`. |
| `syncedPanel(String key, boolean subPanel, PanelSyncHandler.IPanelBuilder)` | → `IPanelHandler`. Idempotent — a second call with the same key on the same manager returns the existing handler. |
| `findPanelHandlerNullable(String key)` / `findPanelHandler(String key)` | Latter throws `NoSuchElementException` if missing. |
| `registerSlotGroup(SlotGroup)` / `registerSlotGroup(name, rowSize, shiftClickPriority)` / `registerSlotGroup(name, rowSize, allowShiftTransfer)` / `registerSlotGroup(name, rowSize)` | Registers a `SlotGroup` (shift-click priority defaults to `100`, `allowShiftTransfer` defaults `true`). |
| `bindPlayerInventory(EntityPlayer)` / `bindPlayerInventory(EntityPlayer, PanelSyncManager.SlotFunction)` | Registers all 36 player inventory slots under key `"player"` plus a `PlayerSlotGroup`. **Throws `IllegalStateException`** if a player slot group is already registered. |
| `registerSyncedAction(String mapKey, ISyncedAction)` / `(..., Side)` / `registerClientSyncedAction` / `registerServerSyncedAction` / `registerSyncedAction(mapKey, executeClient, executeServer, action)` | Registers a fire-and-forget RPC-style action, not a persisted value. |
| `getOrCreateSyncHandler(String, int, Class<T>, Supplier<T>)` / `getOrCreateSH(...)` (alias) | Finds an existing handler by key or creates+registers one via the supplier. **This is the only sanctioned way to register handlers from inside a `DynamicSyncHandler`/`DynamicLinkedSyncHandler` widget-provider callback** (the manager temporarily unlocks registration there). |
| `getOrCreateSlot(String, int, Supplier<ModularSlot>)` | Same, specialized for `ItemSlotSH`. |
| `findSyncHandlerNullable(name, id)` / `findSyncHandler(name, id)` (throws) / typed overloads with `Class<T>` (throws `ClassCastException` on type mismatch) | Lookup by key. |
| `getSlotGroup(String name)` | |
| `makeSyncKey(String name, int id)` (static) | `name + ":" + id`. |

**Example (real usage, `test/TestTile.java:346`) — looking up a previously-registered handler by key/type from another panel:**
```java
IntSyncValue num = syncManager.getHyperVisor().findSyncHandler("cycle_state", IntSyncValue.class);
```

### `com.cleanroommc.modularui.value.sync.PanelSyncManager`

```java
public class PanelSyncManager implements ISyncRegistrar<PanelSyncManager>
```
Owns all `SyncHandler`s, `SlotGroup`s, and `SyncedAction`s for **one panel**. Created once per panel (main panel's is created and marked via `new PanelSyncManager(msm, true)`; secondary/sub panels get `new PanelSyncManager(msm, false)` from inside `PanelSyncHandler.openPanel()`).

| Method | Notes |
|---|---|
| ctor `PanelSyncManager(ModularSyncManager msm, boolean main)` | `@ApiStatus.Internal`. If `main`, registers itself as `msm`'s main PSM. |
| `initialize(String panelName)` | `@ApiStatus.Internal`. Locks the manager (`locked = true`), calls `init(mapKey, this)` on every already-registered handler, and pushes any pre-registered sub-panel handlers up into the main PSM. |
| `isInitialised()` | `panelName != null`. |
| `isLocked()` | Once locked, `syncValue`/`getOrCreateSyncHandler`/`syncedPanel` throw `IllegalStateException` unless bypassed via `allowTemporarySyncHandlerRegistration` (package-private, used by `DynamicSyncHandler`/`DynamicLinkedSyncHandler` and `getOrCreateSyncHandler`'s own temporary-unlock logic). |
| `detectAndSendChanges(boolean init)` (package-private) | Server-side only: iterates all handlers calling `syncHandler.detectAndSendChanges(init \|\| this.init)`; the internal `this.init` flag is `true` only for the very first call. |
| `onOpen()` / `onClose()` (both `@ApiStatus.Internal`) | Fire `openListener`/`closeListener` callbacks; `onClose()` also calls `dispose()` on every handler. |
| `onUpdate()` (package-private) | Runs tick listeners (see `onClientTick`/`onServerTick`/`onCommonTick`). |
| `addOpenListener(Consumer<EntityPlayer>)` / `addCloseListener(...)` | Run on both client and server when the panel's `Container` opens/closes. |
| `onClientTick(Runnable)` / `onServerTick(Runnable)` / `onCommonTick(Runnable)` | Registers a per-tick callback filtered by side (`onClientTick`/`onServerTick` silently no-op if called on the wrong side; `onCommonTick` always runs). |
| `receiveWidgetUpdate(String mapKey, boolean action, int id, PacketBuffer buf)` | `@ApiStatus.Internal`. Routes an incoming network packet either to a `SyncedAction` (`action=true`) or to the matching `SyncHandler`'s `readOnClient`/`readOnServer` — **enforces the C2S guard**: server-side dispatch only proceeds if `syncHandler.isAllowC2S()`, otherwise logs a `SecurityException` warning and drops the packet. |
| `getCursorItem()` / `setCursorItem(ItemStack)` | Delegates to `ModularSyncManager`, keeps the cursor slot in sync. |
| `hasSyncHandler(SyncHandler<?>)` | Checks this manager, falling back to the "hypervisor" (main PSM) if this is a sub panel. |
| `syncedPanel(String key, boolean subPanel, PanelSyncHandler.IPanelBuilder)` | See `ISyncRegistrar`. **Throws `IllegalStateException`** if called after locking without registration permission. If the panel is already open when called, immediately registers the new `PanelSyncHandler` up into the main PSM. |
| `panel(String, IPanelBuilder, boolean)` | `@Deprecated`, scheduled removal 3.3.0 — use `syncedPanel`. |
| `getOrCreateSyncHandler(name, id, clazz, supplier)` | See `ISyncRegistrar`. On a locked manager without registration permission, throws `IllegalStateException`; otherwise temporarily bypasses the lock to register. |
| `callSyncedAction(String mapKey, ...)` (3 overloads: `PacketBuffer`, `Consumer<PacketBuffer>`, no-arg) | Invokes a registered `SyncedAction` locally, and if it should also run on the other side, sends the packet via `ModularNetwork`. |
| `getHyperVisor()` | Returns the main PSM (`ISyncRegistrar<?>`) — used to reach handlers registered on the top-level panel from a sub panel, e.g. shared cross-panel state. |
| `getSlotGroups()` | All registered `SlotGroup`s. |
| `getSyncHandlerFromMapKey(String)` / `getSyncHandler(String)` (deprecated alias) | Raw lookup by the full `"name:id"` key. |
| `getPlayer()` / `getModularSyncManager()` / `getContainer()` / `getPanelName()` / `isClient()` | Plain accessors. |
| `SlotFunction` (nested functional interface) | `ModularSlot apply(PlayerMainInvWrapper playerInv, int index)` — used by `bindPlayerInventory`'s overload to customize player slot construction. |

**Gotcha:** registration order matters for consistency across sides — since keys are plain strings/ids chosen by the calling code (not auto-derived from call order), as long as both sides call the same `syncValue`/`itemSlot`/... calls with the same keys, ordering within `buildUI` doesn't matter, but keys **must** match exactly or the client and server will desync entirely (client reads packets meant for a different handler, or `findSyncHandlerNullable` returns null).

### `com.cleanroommc.modularui.value.sync.ModularSyncManager`

```java
public class ModularSyncManager implements ISyncRegistrar<ModularSyncManager>
```
One instance per open GUI/container (client instance + server instance, kept in lockstep by the network layer). Owns the map of all open `PanelSyncManager`s by panel name, plus the single `CursorSlotSyncHandler` shared across the whole GUI. Most `ISyncRegistrar` methods here simply delegate to `getMainPSM()`.

| Method | Notes |
|---|---|
| ctor `ModularSyncManager(boolean client)` | |
| `construct(ModularContainer container, String mainPanelName)` | `@ApiStatus.Internal`. Binds the player inventory to the main PSM if not already bound, registers the shared cursor-slot handler under a fixed key (`"cursor_slot:255255"`), then `open(mainPanelName, mainPSM)`. |
| `getMainPSM()` | The `PanelSyncManager` for the root panel. |
| `isClient()` | |
| `detectAndSendChanges(boolean init)` | `@ApiStatus.Internal`. Fans out to every open `PanelSyncManager.detectAndSendChanges`. |
| `dispose()` | `@ApiStatus.Internal`. Requires `isClosed()` first (else `IllegalStateException`); closes every panel, clears the map, notifies the container, sets state `DISPOSED`. |
| `onOpen()` / `onClose()` | `@ApiStatus.Internal`, state-machine transitions (`INIT → OPEN → CLOSED → DISPOSED`); `onOpen()` throws if already disposed or not yet `construct()`-ed. |
| `onUpdate()` | Fans out `PanelSyncManager.onUpdate()` (tick listeners) to every open panel. |
| `getPanelSyncManager(String panelName)` | Throws `NullPointerException` (not `NoSuchElementException`) if not open — inconsistent with the rest of the API's `NoSuchElementException` convention, worth noting as a gotcha. |
| `getSyncHandler(panelName, syncKey)` / `getSlotGroup(panelName, slotGroupName)` | Cross-panel lookups by explicit panel name. |
| `getCursorItem()` / `setCursorItem(ItemStack)` | The latter also re-syncs the shared cursor slot handler. |
| `open(String name, PanelSyncManager)` / `close(String name)` | `@ApiStatus.Internal`. `open` registers + records panel history (for late-packet detection); `close` removes and calls `onClose()`. |
| `isOpen(String panelName)` | Whether that specific panel is currently open. |
| `receiveWidgetUpdate(panelName, mapKey, action, id, buf)` | `@ApiStatus.Internal`. Routes to the right `PanelSyncManager`; if the panel isn't open **and never was** (`panelHistory`), logs (throws via `ModularUI.LOGGER.throwing`) an `IllegalStateException` about a packet to an unopened panel. If it was open before but closed, the packet is silently discarded (race between close and in-flight packets is expected). |
| `isOpen()` / `isClosed()` / `isDisposed()` | Based on the internal `State` enum (`INIT, OPEN, CLOSED, DISPOSED`). |
| `makeSyncKey(name, id)` (static) | `@Deprecated`, use `ISyncRegistrar.makeSyncKey`. |

### `com.cleanroommc.modularui.value.sync.PanelSyncHandler`

```java
public final class PanelSyncHandler extends SyncHandler<PanelSyncHandler> implements IPanelHandler
```
Syncs the open/close/dispose lifecycle of a **secondary panel** (created via `ISyncRegistrar.syncedPanel(...)`) across client and server, so calling `openPanel()`/`closePanel()` from either side works correctly. `final` — not meant to be subclassed; package-private constructor (`PanelSyncHandler(IPanelBuilder, boolean subPanel)`), only ever created through `PanelSyncManager.syncedPanel`/`panel`.

| Packet id constant | Meaning |
|---|---|
| `SYNC_NOTIFY_OPEN = 0` | client → server: "please open this panel" |
| `SYNC_OPEN = 1` | server → client: "panel is now open, build/show it" |
| `SYNC_CLOSE = 2` | either direction: close |
| `SYNC_DISPOSE = 3` | either direction: drop the cached panel entirely |

| Method | Notes |
|---|---|
| `createUI(PanelSyncManager)` | Delegates to the `IPanelBuilder` supplied at construction. |
| `openPanel()` (from `IPanelHandler`) | On client: syncs `SYNC_NOTIFY_OPEN` to server (server then builds the panel and replies `SYNC_OPEN`, which the client also applies locally). On server: builds the panel's own `PanelSyncManager` immediately (`new PanelSyncManager(getSyncManager().getModularSyncManager(), false)`), collects its sync values via `WidgetTree.collectSyncValues`, and opens it in the `ModularSyncManager`. **Throws `IllegalStateException`** if you try to reopen an already-built panel handler in a different screen/`ModularSyncManager` instance. |
| `closePanel()` | Client: closes the locally-open `ModularPanel` (animates out). Server: sends `SYNC_CLOSE` to the client. |
| `closePanelInternal()` (`@ApiStatus.Internal`) | Actually removes the panel from the `ModularSyncManager`; on client, also notifies server via `SYNC_CLOSE`. Guards against being called after the sync infrastructure was already torn down (`!isValid()` early return) — this can happen if the screen is disposed before a deferred `PanelManager.dispose()` runs. |
| `deleteCachedPanel()` | Frees the built panel/`PanelSyncManager` so the next `openPanel()` rebuilds from scratch. **Throws `UnsupportedOperationException`** if the panel is currently open, or if any synced widget in it uses an `ItemSlotSH` (item slots can't be safely re-created, since a new panel's sync handlers aren't guaranteed identical). |
| `isSubPanel()` / `isPanelOpen()` | Plain state accessors. |
| `readOnClient`/`readOnServer` | Dispatch on the 4 packet ids above. |
| `IPanelBuilder` (nested functional interface) | `@NotNull ModularPanel buildUI(@NotNull PanelSyncManager syncManager, @NotNull IPanelHandler syncHandler)` — **must not** return `null` or the main panel. |

**Example (real usage, `test/TestTile.java:156-157,240-244`) — declaring and opening a synced sub panel:**
```java
IPanelHandler panelSyncHandler = syncManager.syncedPanel("other_panel", true, this::openSecondWindow);
// ... later, inside a button click handler (runs identically on the side that has the button):
.onMousePressed(mouseButton -> {
    panelSyncHandler.openPanel();
    return true;
})
```
where `openSecondWindow(PanelSyncManager syncManager, IPanelHandler syncHandler)` (same file, line 332) matches `PanelSyncHandler.IPanelBuilder#buildUI` and itself registers further sync values (`IntSyncValue`, a nested `syncedPanel("other_panel_2", ...)`, etc.) scoped to that sub panel.

### `com.cleanroommc.modularui.value.sync.SyncHandlers`

```java
public class SyncHandlers
```
Small static-factory convenience class (private constructor, not instantiable). Thin sugar over the constructors above — using the constructors directly (as `TestTile.java` does) is equally valid; this class mainly saves an import/`new`.

| Method | Signature | Delegates to |
|---|---|---|
| `intNumber` | `(IntSupplier, IntConsumer) → IntSyncValue` | `new IntSyncValue(getter, setter)` |
| `longNumber` | `(LongSupplier, LongConsumer) → LongSyncValue` | `new LongSyncValue(getter, setter)` |
| `bool` | `(BooleanSupplier, BooleanConsumer) → BooleanSyncValue` | `new BooleanSyncValue(getter, setter)` |
| `doubleNumber` | `(DoubleSupplier, DoubleConsumer) → DoubleSyncValue` | `new DoubleSyncValue(getter, setter)` |
| `string` | `(Supplier<String>, Consumer<String>) → StringSyncValue` | `new StringSyncValue(getter, setter)` |
| `itemSlot` | `(IItemHandlerModifiable, int index) → ModularSlot` | `new ModularSlot(inventory, index)` — **not** an `ItemSlotSH`, just the plain slot (wrap with `ItemSlotSH`/`itemSlot(...)` on a registrar to sync it) |
| `fluidSlot` | `(IFluidTank) → FluidSlotSyncHandler` | `new FluidSlotSyncHandler(fluidTank)` |
| `enumValue` | `(Class<T>, Supplier<T>, Consumer<T>) → EnumSyncValue<T,?>` | `new EnumSyncValue<>(clazz, getter, setter)` |
| `generic` | `(Class<T>) → GenericSyncValue.Builder<T>` | `GenericSyncValue.builder(type)` |

**Example (constructed, not from repo) — the README-style fluid slot, via this factory:**
```java
new FluidSlot().syncHandler(SyncHandlers.fluidSlot(new FluidTank(16000)));
// equivalent to: new FluidSlot().syncHandler(new FluidSlotSyncHandler(new FluidTank(16000)));
```

---

### Slot sync handlers

### `com.cleanroommc.modularui.value.sync.ItemSlotSH`

```java
public class ItemSlotSH extends SyncHandler<ItemSlotSH>
```
Wraps a `ModularSlot`, handling its item-change detection and networking. *Inferred:* the doc comment says "Use `ModularSlot` directly", meaning application code should build a `ModularSlot` and let the `ItemSlot`/`PhantomItemSlot` widget wrap it into an `ItemSlotSH`/`PhantomItemSlotSH` rather than instantiating this class by hand — confirmed by `test/TestTile.java` which always goes through `ISyncRegistrar.itemSlot(...)` or `new ItemSlot().slot(new ModularSlot(...))`, never `new ItemSlotSH(...)` directly except inside a `DynamicSyncHandler` widget provider (`TestTile.java:139`, where `getOrCreateSyncHandler` is required anyway).

| Packet id | Meaning |
|---|---|
| `SYNC_ITEM = 0` | full stack push (server→client) |
| `SYNC_ENABLED = 1` | slot enabled/disabled flag (either direction) |

| Method | Notes |
|---|---|
| ctor `ItemSlotSH(ModularSlot slot)` | Derives `playerSlotType` via `PlayerSlotType.getPlayerSlotType(slot)`; calls `allowC2S()` (item slots always accept client-initiated clicks). |
| `init(String, PanelSyncManager)` | Registers the slot into the container exactly once (`this.slot.initialize(this, isPhantom())`, `getSyncManager().getContainer().registerSlot(panelName, slot)`), guarded by an internal `registered` flag so re-`init` (e.g. panel reopen) doesn't double-register. Also snapshots the current stack into `lastStoredItem`. |
| `dispose()` | Also disposes the wrapped `slot`. |
| `detectAndSendChanges(boolean init)` | Delegates to `checkUpdate(init)`. |
| `checkUpdate()` | Public no-arg convenience (`init=false`); can be called manually to force a change check outside the normal tick (used by `ModularSlot`'s change listener in some cases). |
| `forceSyncItem()` | Re-sends the current stack unconditionally (bypasses the diff check), marking the packet with a `forceSync` flag the client uses to hard-`putStack` even if its own prediction disagrees. |
| `getSlot()` | The wrapped `ModularSlot`. |
| `isItemValid(ItemStack)` | `getSlot().isItemValid(...)`. |
| `isPhantom()` | `false` here; overridden `true` in `PhantomItemSlotSH`. |
| `getPlayerSlotType()` / `isPlayerSlot()` | Whether this slot belongs to the player's own inventory (affects shift-click routing). |
| `getSlotGroup()` | Nullable slot-group name. |

**Gotcha:** `checkUpdate` only fires the network write when the stack meaningfully changed (`!canItemStacksStack` or amount differs) — pure NBT/no-op writes to the slot don't spam packets.

### `com.cleanroommc.modularui.value.sync.PhantomItemSlotSH`

```java
public class PhantomItemSlotSH extends ItemSlotSH
```
Handles UI-driven (not real inventory) phantom slots — the player "sets" a template stack rather than actually depositing items. `@ApiStatus.Internal` constructor `PhantomItemSlotSH(ModularSlot slot)` sets `slot.slotNumber = -1` (phantom slots aren't real container slots) — again, application code should go through `PhantomItemSlot` widget, not construct this directly.

| Packet id | Meaning |
|---|---|
| `SYNC_CLICK = 100` | client→server mouse click on the slot |
| `SYNC_SCROLL = 101` | client→server scroll wheel on the slot |
| `SYNC_ITEM_SIMPLE = 102` | client→server direct "set this exact stack" (e.g. from JEI ghost-slot dragging) |

| Method | Notes |
|---|---|
| `updateFromClient(ItemStack stack, int button)` | Client-only call site: syncs `SYNC_ITEM_SIMPLE` with the stack + a button code. |
| `phantomClick(MouseData)` / `phantomClick(MouseData, ItemStack cursorStack)` | Left-click sets/increments/clears using the cursor stack; right-click sets qty 1 or increments by 1; remembers `lastStoredPhantomItem` so scrolling back up after emptying restores the last template. |
| `phantomScroll(MouseData)` | Adjusts stack size by `mouseButton` scaled ×4 (shift) / ×16 (ctrl) / ×64 (alt). |
| `incrementStackCount(int amount)` | Clamps to `[0, slot stack/stack-size limit]`; setting to 0 clears the slot (`putStack(null)`). |
| `isPhantom()` | `true` (override). |

### `com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler`

```java
public class FluidSlotSyncHandler extends ValueSyncHandler<FluidStack, FluidSlotSyncHandler>
```
The handler behind `FluidSlot` widgets (README's headline example). Wraps a single `IFluidTank`.

| Static helper | Notes |
|---|---|
| `isFluidEmpty(@Nullable FluidStack)` | `stack == null`. |
| `copyFluid(@Nullable FluidStack)` | `null`-safe `.copy()`. |

| Packet id | Meaning |
|---|---|
| `SYNC_VALUE = 0` (inherited) | full `FluidStack` push |
| `SYNC_CLICK = 1` | client→server slot click |
| `SYNC_SCROLL = 2` | client→server scroll |
| `SYNC_CONTROLS_AMOUNT = 3` | either direction: toggles whether clicking fills/drains a fixed 1000mB vs. matching the held container |
| `4` (no named const) | client→server: click-with-dragged-stack variant carrying an explicit `ItemStack` (drag-fill across multiple slots) |

| Method | Notes |
|---|---|
| ctor `FluidSlotSyncHandler(IFluidTank fluidTank)` | Calls `allowC2S()` (players interact with fluid slots directly). |
| ctor `FluidSlotSyncHandler(IMultiFluidTankHandler fluidHandler, int index)` | Delegates to the tank ctor via `fluidHandler.getFluidTank(index)`. |
| `getValue()` / `setValue(FluidStack, setSource, sync)` | Standard `ValueSyncHandler` shape; `setSource=true` drains then (re)fills the real `fluidTank` to match. |
| `needsSync()` | Compares cache vs. live tank fluid by reference, then null-ness, then `amount`+`isFluidEqual`. |
| `updateCacheFromSource(boolean isFirstSync)` | `isFirstSync \|\| needsSync()`. |
| `phantom(boolean)` | Fluent. Phantom fluid slots behave like phantom item slots — clicking sets a template amount rather than transferring real fluid; `SYNC_VALUE`/`SYNC_CLICK`/`SYNC_SCROLL` are only honored server-side `if (this.phantom)`. |
| `controlsAmount(boolean)` | Fluent; if already `isValid()` (registered), immediately syncs the new flag (`SYNC_CONTROLS_AMOUNT`) instead of waiting for the next tick. |
| `canDrainSlot(boolean)` / `canFillSlot(boolean)` | Fluent toggles restricting player-driven fill/drain (both default `true`). |
| `filter(Predicate<FluidStack>)` | Fluent; rejects held fluids that don't pass the predicate (default accepts everything). |
| `getFluidTank()` / `canDrainSlot()` / `canFillSlot()` / `controlsAmount()` / `isPhantom()` / `getFilter()` | Plain getters. |

Internally implements full fluid-container interaction logic (`tryClickContainer`, `tryClickPhantom`, `fillFluid`/`drainFluid` with batch container support, `tryScrollPhantom`) — these are `protected`, not meant to be called from outside; only relevant if subclassing.

**Example (real usage, `test/TestTile.java:298,303`):**
```java
.key('F', i -> new FluidSlot().syncHandler(new FluidSlotSyncHandler(this.fluidStorage, i)))
// ...
.key('F', i -> new FluidSlot().syncHandler(new FluidSlotSyncHandler(this.phantomFluidStorage, i).phantom(true)))
```
(`this.fluidStorage`/`this.phantomFluidStorage` are `MultiFluidTankHandler` instances — the `IMultiFluidTankHandler` ctor overload.)

### `com.cleanroommc.modularui.value.sync.CursorSlotSyncHandler`

```java
public class CursorSlotSyncHandler extends SyncHandler<CursorSlotSyncHandler>
```
Syncs the item currently held on the player's cursor (outside any slot). Exactly one instance exists per `ModularSyncManager` (created and registered internally in `ModularSyncManager.construct(...)` under a fixed key), never constructed by user code. `sync()` writes the player's `inventory.getItemStack()`; both `readOnClient`/`readOnServer` just call `inventory.setItemStack(...)`. Not registered by user `buildUI` code — reach it indirectly via `PanelSyncManager.getCursorItem()`/`setCursorItem(ItemStack)` or `ModularSyncManager` equivalents.

---

### `com.cleanroommc.modularui.value.sync.InteractionSyncHandler`

```java
public class InteractionSyncHandler extends SyncHandler<InteractionSyncHandler>
```
Generic C2S mouse/keyboard event forwarder for widgets that need raw input callbacks executed server-side (rather than a bound value). Constructor calls `allowC2S()`.

| Packet id | Direction | Data |
|---|---|---|
| `1` mouse pressed, `2` mouse released, `3` mouse tapped, `4` mouse scroll | client→server | `MouseData` |
| `11` key pressed, `12` key released, `13` key tapped | client→server | `KeyboardData` |

| Method | Notes |
|---|---|
| `onMousePressed(int button)` / `onMouseReleased(int button)` / `onMouseTapped(int button)` / `onMouseScroll(int scroll)` | Called from client-side widget input handling: invokes the local callback (if any, so client-side prediction/feedback is immediate) **and** syncs to server via `syncToServer`. Returns `false` (no-op) if no corresponding callback (`setOnMouse*`) was registered — lets the widget fall through to default handling. |
| `onKeyPressed(char, int keycode)` / `onKeyReleased(...)` / `onKeyTapped(...)` | Same pattern for keyboard. |
| `setOnMousePressed(IServerMouseAction)` / `setOnMouseReleased` / `setOnMouseTapped` / `setOnMouseScroll` | Fluent setters (return `this`). Callback runs **server-side** when the packet arrives (`readOnServer`) — note it *also* runs client-side immediately inside `onMouse*` above, so the same `IServerMouseAction` executes on both sides (naming is a bit misleading; it's not literally "server-only"). |
| `setOnKeyPressed` / `setOnKeyReleased` / `setOnKeyTapped` | Same for keyboard. |

*Inferred:* no direct usage appears in `test/`, so no real-repo example is available; this handler appears intended for custom widgets that need arbitrary input plumbed to the server without defining a dedicated `SyncHandler` subclass.

**Example (constructed, not from repo):**
```java
InteractionSyncHandler interaction = new InteractionSyncHandler()
        .setOnMousePressed(mouseData -> myTile.onCustomClick(mouseData.mouseButton));
syncManager.syncValue("custom_click", interaction);
```

---

### Dynamic widget sync: `DynamicSyncHandler` / `DynamicLinkedSyncHandler` / `IDynamicSyncNotifiable`

### `com.cleanroommc.modularui.value.sync.IDynamicSyncNotifiable`

```java
public interface IDynamicSyncNotifiable {
    @ApiStatus.Internal
    void attachDynamicWidgetListener(Consumer<IWidget> onWidgetUpdate);
}
```
Internal linkage contract implemented by both dynamic handlers below so a `com.cleanroommc.modularui.widgets.DynamicSyncedWidget` can attach itself as the receiver of freshly-built widgets. Not meant to be called from application code.

### `com.cleanroommc.modularui.value.sync.DynamicSyncHandler`

```java
@ApiStatus.Obsolete
public class DynamicSyncHandler extends SyncHandler<DynamicSyncHandler> implements IDynamicSyncNotifiable
```
Rebuilds an arbitrary widget subtree on demand, driven by an explicit packet payload, on **both** client and server, and hands the result to a linked `DynamicSyncedWidget`. Marked `@ApiStatus.Obsolete` — superseded by `DynamicLinkedSyncHandler` for the common case of "rebuild whenever a `ValueSyncHandler` changes," but still used directly when the trigger isn't a plain value change (e.g. an arbitrary event with custom packet payload).

| Method | Notes |
|---|---|
| `widgetProvider(IWidgetProvider)` | Fluent. Sets the `(PanelSyncManager, PacketBuffer) -> IWidget` factory run identically on both sides. |
| `notifyUpdate(IPacketWriter packetWriter)` | Triggers a rebuild: builds the widget locally (with sync-handler registration temporarily allowed — see below), applies it client-side, and syncs the packet to the other side. If called before this handler is `init`-ialized, the packet writer is cached (`lastRejectedPacket`) and replayed once `init()` runs — **only the last call before init is effective**, earlier ones are discarded. |
| `init(String, PanelSyncManager)` | Overridden to flush any `lastRejectedPacket` immediately after calling `super.init(...)`. |
| `readOnClient`/`readOnServer` (id `0`) | Both parse the widget via the provider; only the client actually swaps it into the live UI (server parses solely to keep any sync handlers created inside the provider consistent). |
| `attachDynamicWidgetListener(Consumer<IWidget>)` (`@ApiStatus.Internal`) | Called by `DynamicSyncedWidget` to register itself; if a widget update was rejected earlier (arrived before the `DynamicSyncedWidget` attached), it's replayed once. |
| `IWidgetProvider` (nested functional interface) | `@Nullable IWidget createWidget(PanelSyncManager syncManager, PacketBuffer buf)`. **Inside this callback, sync handlers may only be registered via `ISyncRegistrar.getOrCreateSyncHandler(...)` variants** — the manager enforces this: `WidgetTree.countUnregisteredSyncHandlers` is checked after building and **throws `IllegalStateException`** if any synced widget in the returned tree wasn't registered that way. |

**Example (real usage, `test/TestTile.java:128-142, 319-321`):**
```java
DynamicSyncHandler dynamicSyncHandler = new DynamicSyncHandler()
        .widgetProvider((syncManager1, packet) -> {
            ItemStack itemStack = NetworkUtils.readItemStack(packet);
            if (itemStack == null) return new EmptyWidget();
            Item item = itemStack.getItem();
            ItemStackHandler handler = stackHandlerMap.computeIfAbsent(item, k -> new ItemStackHandler(handlerSizeMap.getInt(k)));
            Flow flow = Flow.row();
            for (int i = 0; i < handler.getSlots(); i++) {
                int finalI = i;
                flow.child(new ItemSlot()
                        .syncHandler(syncManager1.getOrCreateSyncHandler(name, i, ItemSlotSH.class,
                                () -> new ItemSlotSH(new ModularSlot(handler, finalI)))));
            }
            return flow;
        });
// registered as a normal sync value, then linked to a widget:
syncManager.syncValue(...); // (implicitly via registration when building the panel)
...
.child(new DynamicSyncedWidget<>().widthRel(1f).syncHandler(dynamicSyncHandler))
// triggered elsewhere when the backing slot's item changes:
dynamicSyncHandler.notifyUpdate(packet -> NetworkUtils.writeItemStack(packet, newItem));
```

### `com.cleanroommc.modularui.value.sync.DynamicLinkedSyncHandler<S extends ValueSyncHandler<?, ?>>`

```java
public class DynamicLinkedSyncHandler<S extends ValueSyncHandler<?, ?>> extends SyncHandler<DynamicLinkedSyncHandler<S>> implements IDynamicSyncNotifiable
```
A variation of `DynamicSyncHandler` that is automatically linked to an already-registered `ValueSyncHandler` (`linkedValue`): whenever that value changes (`ValueSyncHandler.onValueChanged()` → the change listener installed here), the widget is rebuilt automatically — no manual packet payload needed, since the provider receives the linked sync handler itself and can read its current value.

| Method | Notes |
|---|---|
| ctor `DynamicLinkedSyncHandler(S linkedValue)` | Installs itself as `linkedValue`'s change listener (`linkedValue.setChangeListener(() -> notifyUpdate(false))`). |
| `widgetProvider(IWidgetProvider<S>)` | Fluent. `(PanelSyncManager, S value) -> IWidget`. |
| `init(String, PanelSyncManager)` | After `super.init(...)`, immediately calls `notifyUpdate(false)` to build the initial widget. |
| `attachDynamicWidgetListener(Consumer<IWidget>)` (`@ApiStatus.Internal`) | Same rejected/replay semantics as `DynamicSyncHandler`. |
| `IWidgetProvider<S>` (nested functional interface) | `@Nullable IWidget createWidget(PanelSyncManager syncManager, S value)` — same registration restriction (`getOrCreateSyncHandler` only) enforced the same way via `WidgetTree.countUnregisteredSyncHandlers`. |

**Example (real usage, `test/TestTile.java:144-153, 323-326`):**
```java
DynamicLinkedSyncHandler<GenericListSyncHandler<Integer>> dynamicLinkedSyncHandler =
        new DynamicLinkedSyncHandler<>(numberListSyncHandler)
                .widgetProvider((syncManager1, value1) -> {
                    List<Integer> vals = value1.getValue();
                    return Flow.row()
                            .widthRel(1f)
                            .coverChildrenHeight()
                            .mainAxisAlignment(Alignment.MainAxis.SPACE_AROUND)
                            .children(vals.size(), i -> IKey.str(String.valueOf(vals.get(i))).asWidget().padding(2))
                            .name("synced number col");
                });
// ...
.child(new DynamicSyncedWidget<>().widthRel(1f).coverChildrenHeight().syncHandler(dynamicLinkedSyncHandler))
```
Here `numberListSyncHandler` is the `GenericListSyncHandler<Integer>` from the collection-sync example above — whenever the server's `serverInts` list changes and re-syncs, the linked handler notices via the change listener and rebuilds the displayed row of numbers automatically.

---

### `com.cleanroommc.modularui.value.sync.SyncedAction`

```java
public class SyncedAction
```
Not a `SyncHandler` — a lightweight wrapper around a registered `ISyncedAction` (a `@FunctionalInterface` with a single `void invoke(@NotNull PacketBuffer packet)`), used for fire-and-forget RPC-style calls that don't carry persistent state (see `ISyncRegistrar.registerSyncedAction`/`PanelSyncManager.callSyncedAction`).

| Method | Notes |
|---|---|
| ctor `SyncedAction(ISyncedAction action, boolean executeClient, boolean executeServer)` | |
| `invoke(boolean client, PacketBuffer packet)` | Runs `action.invoke(packet)` only `if (isExecute(client))`; returns whether it ran. |
| `isExecuteClient()` / `isExecuteServer()` | |
| `isExecute(boolean client)` | `(client && executeClient) \|\| (!client && executeServer)`. |

*Inferred:* no direct `test/` usage of raw `SyncedAction`/`registerSyncedAction` was found; it's driven indirectly through `ISyncRegistrar.registerSyncedAction(...)` and `PanelSyncManager.callSyncedAction(...)`, both documented above.

---

## Cross-references out of scope but relevant

- `com.cleanroommc.modularui.api.value.sync.IValueSyncHandler<T>` — the interface `ValueSyncHandler` implements (`setValue` 3-arg, `updateCacheFromSource`, `notifyUpdate`, `write`, `read`).
- `com.cleanroommc.modularui.api.value.sync.I*SyncValue` (`IBoolSyncValue`, `IIntSyncValue`, ...) — typed sync interfaces mirroring `api.value.I*Value` but with the `(value, setSource, sync)` 3-arg setter shape.
- `com.cleanroommc.modularui.api.IPanelHandler` — the interface `PanelSyncHandler` (and the unsynced `SecondaryPanel`) implement.
- `com.cleanroommc.modularui.utils.serialization.*` (`IByteBufAdapter`, `IByteBufSerializer`, `IByteBufDeserializer`, `IEquals`, `ByteBufAdapters`) and `com.cleanroommc.modularui.utils.ICopy` — the pluggable (de)serialize/equals/copy strategy types used throughout `GenericSyncValue`/`GenericCollectionSyncHandler`/`GenericMapSyncHandler`.
- `com.cleanroommc.modularui.widgets.slot.ModularSlot`, `PlayerSlotType`, `SlotGroup`, `PlayerSlotGroup` — the slot/inventory plumbing `ItemSlotSH`/`PhantomItemSlotSH`/`bindPlayerInventory` build on.
- `com.cleanroommc.modularui.widgets.DynamicSyncedWidget` — the widget counterpart that attaches to `IDynamicSyncNotifiable` handlers.
