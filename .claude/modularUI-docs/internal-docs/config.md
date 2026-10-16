# `config` package reference

Package: `com.cleanroommc.modularui.config`

**Every class in this package (except `ModularUIGuiConfig`/`ModularUIGuiConfigFactory`) is annotated both `@ApiStatus.Experimental` and `@Deprecated`.** This is a work-in-progress in-game config-value/UI system (define a tree of named, JSON-serializable, optionally client/server-synced `Value`s, and generate a `ModularPanel` to edit them), separate from and not to be confused with `ModularUIGuiConfig`/`ModularUIGuiConfigFactory`, which are unrelated, non-deprecated glue for GTNHLib's `SimpleGuiConfig`/`SimpleGuiFactory` (the mod's actual Forge-mod-config-menu integration). No usage of the deprecated `Config`/`Value` classes was found anywhere in `src/main`, including `test/` — all examples below are constructed from reading the source directly.

---

## `com.cleanroommc.modularui.config.Config`

```java
@ApiStatus.Experimental
@Deprecated
public class Config
```

A named, hierarchical bag of `Value`s and child `Config` categories, backed by a JSON file under the Forge config directory, with optional client→server sync over the mod's packet system. Built exclusively through the nested `Config.Builder` (no public top-level constructor is meant to be called by hand, though the constructor itself is public — see gotcha).

### Static methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `getConfig(String name)` | registered config name | `@NotNull Config` | Throws `NoSuchElementException` if no config was registered under that name. Configs self-register into a static `Map<String, Config>` in their constructor. |
| `builder(String name)` | new config's name | new `Config.Builder` | Throws `IllegalStateException` if a config with that name is already registered. |

### Constructor
- `Config(String name, Map<String, Config> categories, Map<String, Value> values, String basePath, boolean synced)` — public, but intended to be called only via `Builder.build()`/`buildInternal()`. Computes the on-disk `filePath` as `<forgeConfigDir>/<basePath>/<name>.json` (basePath segment omitted if empty), registers itself in the static `configs` map (**gotcha:** this means simply constructing two `Config`s with the same `name` will silently overwrite the registry entry — there's no duplicate check here, only in `builder(String)`), and computes final `synced` as `synced && determineSynced()` (see below).

### Instance methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `createScreen()` | - | `ModularScreen` | Builds a `new ModularScreen(ModularUI.ID, new ConfigPanel(this))` — the generated editor UI for this config. |
| `serialize()` | - | `JsonObject` | Recursively serializes all values (`Value.writeJson()`) and child categories into one JSON tree, in insertion order (backed by `Object2ObjectLinkedOpenHashMap`). |
| `deserialize(JsonObject json)` | JSON to read | - | For each entry: if it's a JSON object and a category of that name exists, recurses into it; otherwise if a value of that key exists, calls `value.readJson(...)`. Unknown keys are silently ignored. |
| `syncToServer()` | - | - | No-op if `!isSynced()`. Otherwise sends a `SyncConfig` packet to the server via `NetworkHandler.sendToServer`. |
| `writeToBuffer(PacketBuffer buffer)` / `readFromBuffer(PacketBuffer buffer)` | packet buffer | - | Writes/reads only the subset of categories/values where `isSynced()` is true, in the order: category count, then each `(name, category-subtree)`, then value count, then each `(name, value)`. **Gotcha:** `readFromBuffer` looks up categories/values by name read from the buffer and calls methods on them without a null-check (`category.readFromBuffer(buffer)` / `value.readFromPacket(buffer)`) — if the receiving `Config`'s tree doesn't structurally match the sender's (e.g. mismatched mod versions), this throws an NPE rather than failing gracefully. |
| `getName()` / `getFilePath()` / `isSynced()` | - | `String` / `File` / `boolean` | Plain getters. |

**`determineSynced()` (private) semantics:** a `Config` is only actually synced if `synced=true` **and** at least one descendant category or value is itself synced — i.e. the flag is advisory unless something underneath opts in. `Value.isSynced()` is currently hardcoded `false` in the base `Value` class (see below), so as the code stands, `determineSynced()` returns `false` unless a `Value` subclass overrides `isSynced()` to return `true` (none of the four bundled `Value` subclasses do).

### `Config.Builder` (nested static class)

Fluent, hierarchical builder. Root builders are created via `Config.builder(String)`; category builders via `createCategory(String)`.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `basePath(String basePath)` | subdirectory under the Forge config dir | `this` | Default `"modularui"`. |
| `synced(boolean synced)` | - | `this` | Default `true` — but see `determineSynced()` above; this alone doesn't guarantee syncing. |
| `value(Value value)` | a constructed `Value` (e.g. `new ValueBoolean(...)`) | the same `Value` instance (not the builder) | **Gotcha:** breaks the fluent chain — returns the `Value`, not `this`, so you can't chain further `Builder` calls directly off this call. Registers the value under `value.getKey()` in this builder's map. |
| `createCategory(String name)` | category name | new child `Builder` (parented to `this`) | Switches "context" to building a nested category; call `buildCategory()` to return to the parent. |
| `buildCategory()` | - | parent `Builder` | Throws `IllegalStateException` if called on a root builder (`parent == null`, i.e. "Call 'build' on root config"). Finalizes this category into a `Config` and registers it under `name` in the parent's `categories` map. |
| `build()` | - | `Config` | Throws `IllegalStateException` if called on a category builder (`parent != null`, i.e. "Call 'buildCategory' on categories!"). Finalizes the root config (which also registers it globally via the `Config` constructor). |

**Example (constructed, not from repo):**
```java
Config config = Config.builder("mymod")
        .basePath("mymod")
        .value(new ValueBoolean("enableFeature", true))
        .createCategory("display")
            .value(new ValueInt("scale", 100, 50, 200))
            .buildCategory()
        .build();

ModularScreen screen = config.createScreen();
```

---

## `com.cleanroommc.modularui.config.Value`

```java
@ApiStatus.Experimental
@Deprecated
public abstract class Value
```

Abstract base for a single named config value: JSON (de)serialization, network (de)serialization, default-reset, and an optional GUI widget factory.

### Constructor
- `Value(String key)` — `key` is the JSON property name / network lookup key; stored `final`.

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `buildGuiConfig(ModularGuiContext context)` | context | `@Nullable IWidget`, default `null` | Override to supply the widget used to edit this value in a generated config UI; base implementation contributes nothing. |
| `writeJson()` (abstract) | - | `JsonElement` | Serialize current value. |
| `readJson(JsonElement json)` (abstract) | - | - | Deserialize into current value. |
| `writeToPacket(PacketBuffer buffer)` (abstract) | - | - | Network serialize. |
| `readFromPacket(PacketBuffer buffer)` (abstract) | - | - | Network deserialize. |
| `resetToDefault()` (abstract) | - | - | Reset to the subclass's default value. |
| `getKey()` | - | `String` | |
| `isSynced()` | - | `boolean`, backed by a `private final boolean synced = false` field | **Gotcha:** this field is hardcoded `false` and has no setter — subclasses must **override** `isSynced()` (not set a field) to opt into syncing, e.g. as `ValueDrawable` does (also returning `false`, i.e. explicitly non-synced). None of the four built-in subclasses currently return `true`. |
| `isHidden()` | - | `boolean`, backed by a `private final boolean hidden = false` field | Same shape as `isSynced()` — always `false` in the base class, with no way to set it short of a subclass override. |

---

## `com.cleanroommc.modularui.config.ValueBoolean`

```java
@ApiStatus.Experimental
@Deprecated
public class ValueBoolean extends Value
```

Boolean-valued `Value`. JSON via `JsonPrimitive`, network via `buffer.writeBoolean`/`readBoolean`.

| Constructor/Method | Params | Returns | Notes |
|---|---|---|---|
| `ValueBoolean(String key, boolean defaultValue)` | key, default | - | Initializes current value to `defaultValue`. |
| `writeJson()` / `readJson(JsonElement)` | - / json | `JsonElement` / - | Straightforward `JsonPrimitive` round-trip. |
| `writeToPacket(PacketBuffer)` / `readFromPacket(PacketBuffer)` | buffer | - | `writeBoolean`/`readBoolean`. |
| `resetToDefault()` | - | - | Sets value back to the constructor default. |
| `getValue()` | - | `boolean` | Current value. |

**Example (constructed, not from repo):**
```java
Value enableFeature = builder.value(new ValueBoolean("enableFeature", true));
```

---

## `com.cleanroommc.modularui.config.ValueInt`

```java
@ApiStatus.Experimental
@Deprecated
public class ValueInt extends Value
```

Integer-valued `Value` with optional min/max bounds. JSON via `JsonPrimitive`, network via var-int encoding.

| Constructor/Method | Params | Returns | Notes |
|---|---|---|---|
| `ValueInt(String key, int defaultValue)` | key, default | - | Delegates to the 4-arg constructor with `min=Integer.MIN_VALUE`, `max=Integer.MAX_VALUE` (effectively unbounded). |
| `ValueInt(String key, int defaultValue, int min, int max)` | key, default, bounds | - | **Gotcha:** `min`/`max` are stored but never actually enforced anywhere in this class — `readJson`/`readFromPacket` accept any int without clamping. The bounds exist for a UI widget (via `buildGuiConfig`, not overridden here) to use, not as a hard invariant. |
| `writeJson()` / `readJson(JsonElement)` | - / json | `JsonElement` / - | `JsonPrimitive` round-trip via `getAsInt()`. |
| `writeToPacket(PacketBuffer)` / `readFromPacket(PacketBuffer)` | buffer | - | `writeVarIntToBuffer`/`readVarIntFromBuffer`. |
| `resetToDefault()` | - | - | Resets to constructor default. |
| `getValue()` / `getDefaultValue()` | - | `int` | Current / default value. |

---

## `com.cleanroommc.modularui.config.ValueDrawable`

```java
@ApiStatus.Experimental
@Deprecated
public class ValueDrawable extends Value
```

Holder for an `IDrawable` config value. **Currently a stub**: `writeJson()` returns `null`, `readJson`/`writeToPacket`/`readFromPacket`/`resetToDefault` are all empty no-ops, and there is no getter/setter exposed for the underlying `drawable` field at all — it's set nowhere in this class.

| Constructor/Method | Params | Returns | Notes |
|---|---|---|---|
| `ValueDrawable(String key)` | key | - | No default value parameter. |
| `writeJson()` | - | `null` always | Not implemented — do not rely on this for persistence. |
| `readJson(JsonElement)` / `writeToPacket(PacketBuffer)` / `readFromPacket(PacketBuffer)` / `resetToDefault()` | - | - | All empty bodies. |
| `isSynced()` | - | `boolean`, hardcoded `false` | Explicit override (redundant with base `Value.isSynced()`, which is already `false`, but documents intent). |

**Inferred:** this class appears to be a placeholder/incomplete implementation — there is no way to actually set or read the wrapped drawable from outside the class as written. Treat as non-functional until further developed.

---

## `com.cleanroommc.modularui.config.ConfigPanel`

```java
@ApiStatus.Experimental
@Deprecated
public class ConfigPanel extends ModularPanel
```

The `ModularPanel` returned by `Config.createScreen()`. Currently a near-stub: its `initGui()` method (called from the constructor) is empty, so **no config-editing widgets are actually built** as the code stands — the panel exists but has no content yet.

### Constructor
- `ConfigPanel(Config config)` — calls `super("config")`, stores the config, then calls the (currently empty) `initGui()`.

### Method
| Method | Params | Returns | Notes |
|---|---|---|---|
| `onClose()` | - | - | Overridden: calls `super.onClose()`, then `config.serialize()` (result discarded — **gotcha:** the serialized `JsonObject` is never written to `config.getFilePath()`, i.e. this currently doesn't persist to disk despite the name) and `config.syncToServer()`. |

**Inferred:** given both `initGui()` and the "serialize but don't write to file" behavior in `onClose()`, this class is mid-development; don't rely on it to actually build an editable config UI or save config state to disk yet.

---

## `com.cleanroommc.modularui.config.ModularUIGuiConfig`

```java
public class ModularUIGuiConfig extends SimpleGuiConfig
```

Unrelated to the deprecated `Config`/`Value` system above — this is ModularUI's own mod-options screen, wired through GTNHLib's `SimpleGuiConfig` (annotation-driven config GUI generator over `ModularUIConfig.class`). Not deprecated.

### Constructor
- `ModularUIGuiConfig(GuiScreen parent) throws ConfigException` — calls `super(parent, ModularUI.ID, ModularUI.NAME, ModularUIConfig.class)`, i.e. builds a GTNHLib-generated options screen reflecting the fields/annotations on `ModularUIConfig`. `ConfigException` propagates from GTNHLib if that class is misconfigured.

No other public members. Instantiated by `ModularUIGuiConfigFactory` (typically via Forge's mod-options button), not normally constructed directly by other mods.

---

## `com.cleanroommc.modularui.config.ModularUIGuiConfigFactory`

```java
@SuppressWarnings("unused")
public class ModularUIGuiConfigFactory implements SimpleGuiFactory
```

GTNHLib `SimpleGuiFactory` implementation that tells Forge/GTNHLib which class implements this mod's config GUI.

### Method
| Method | Params | Returns | Notes |
|---|---|---|---|
| `mainConfigGuiClass()` | - | `Class<? extends GuiScreen>`, always `ModularUIGuiConfig.class` | The `@SuppressWarnings("unused")` on the class reflects that it's referenced only via reflection/`mcmod.info`/annotation processing by Forge/GTNHLib, not called directly from Java code. |

**Example (constructed, not from repo — this is how Forge/GTNHLib would use it, not how a mod author calls it):**
```java
// Forge/GTNHLib instantiates this via reflection based on mod metadata;
// mod authors do not call it directly.
SimpleGuiFactory factory = new ModularUIGuiConfigFactory();
Class<? extends GuiScreen> guiClass = factory.mainConfigGuiClass(); // ModularUIGuiConfig.class
```
