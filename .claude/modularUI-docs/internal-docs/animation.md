# `animation` package reference

Package: `com.cleanroommc.modularui.animation`

Frame-driven animation utilities. Animators are advanced once per client frame by `AnimatorManager`, which is registered on the Forge event bus during a `GuiScreenEvent.DrawScreenEvent.Pre` handler. All classes here are client-side only in practice (they depend on `Minecraft.getSystemTime()` / GUI draw events), even though few are annotated `@SideOnly` explicitly.

Time is measured in integer milliseconds. An "animator" moves from 0 to some duration (or back), calling an update callback each step, and optionally chains/repeats/reverses on finish.

---

## `com.cleanroommc.modularui.animation.IAnimator`

Core animation contract. Every animator (leaf or composite) implements this.

```java
public interface IAnimator {
    @Nullable IAnimator getParent();
    default void animate(boolean reverse);
    default void animate();
    boolean stop(boolean force);
    void pause();
    void resume(boolean reverse);
    void reset(boolean atEnd);
    default void reset();
    @ApiStatus.OverrideOnly int advance(int elapsedTime);
    boolean isPaused();
    boolean isAnimating();
    boolean isAnimatingReverse();
    boolean hasProgressed();
    default boolean isAnimatingForward();
    static int getTimeDiff(long startTime);
    static int getTimeDiff(long startTime, long currentTime);
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getParent()` | - | parent animator or `null` | Set internally when an animator is nested inside a `SequentialAnimator`/`ParallelAnimator`. |
| `animate(boolean reverse)` | direction to start in | - | Default impl: `reset(reverse); resume(reverse);`. This is the main "start" entry point. |
| `animate()` | - | - | Shortcut for `animate(false)` (forward). |
| `stop(boolean force)` | `force=true` skips reverse/repeat logic | `true` if animation actually stopped | If `force=false`, implementations may instead bounce back or repeat instead of stopping — see `BaseAnimator.stop`. |
| `pause()` | - | - | Freezes progress; `AnimatorManager` skips paused animators each frame. |
| `resume(boolean reverse)` | direction | - | Un-pauses and sets direction; on top-level animators (no parent) this re-registers the animator with `AnimatorManager`. |
| `reset(boolean atEnd)` | `atEnd=true` resets to the finished position instead of the start | - | Does not start animating. |
| `reset()` | - | - | Shortcut for `reset(false)`. |
| `advance(int elapsedTime)` | elapsed ms since last frame | remaining unconsumed ms | `@ApiStatus.OverrideOnly` — called by `AnimatorManager` (or a parent composite animator), not meant to be called directly by user code. |
| `isPaused()` / `isAnimating()` / `isAnimatingReverse()` | - | booleans | State queries. |
| `hasProgressed()` | - | boolean | Whether the animation has moved past its starting point yet. |
| `isAnimatingForward()` | - | boolean | Default: `isAnimating() && !isAnimatingReverse()`. |
| `getTimeDiff(long startTime)` (static) | start time | elapsed ms vs `Minecraft.getSystemTime()`, clamped to `Integer.MAX_VALUE` | |
| `getTimeDiff(long startTime, long currentTime)` (static) | two times | absolute difference clamped to int range | |

**Example (adapted from `test/TestGuis.java`, `buildPendulumAnimationUI`):**
```java
Animator animator = new Animator()
        .bounds(0, 1)
        .curve(Interpolation.SINE_INOUT)
        .reverseOnFinish(true)
        .repeatsOnFinish(-1)
        .duration(1200);

animator.reset(true);
animator.animate(true); // starts the (repeating, bouncing) animation
```

---

## `com.cleanroommc.modularui.animation.BaseAnimator<A extends BaseAnimator<A>>`

```java
public abstract class BaseAnimator<A extends BaseAnimator<A>> implements IAnimator
```

Abstract base implementing the shared state machine (direction, pause flag, reverse-on-finish, repeat count) for all animators. Uses a self-typed generic (`A`) so fluent setters on subclasses return the subclass type. Not meant to be instantiated directly for general use (it's `abstract`); `Wait` extends it with a raw type.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `getThis()` | - | `A` (this, cast) | Used internally by fluent setters. |
| `getParent()` | - | `@Nullable IAnimator` | `final`. |
| `reset(boolean atEnd)` | - | - | Resets `startedReverse` flag and internal repeat counter. Subclasses call `super.reset(atEnd)`. |
| `stop(boolean force)` | see below | boolean | See gotcha below. |
| `pause()` / `resume(boolean reverse)` / `isPaused()` | - | - / - / boolean | `resume` re-registers with `AnimatorManager.startAnimation` only if `getParent() == null` (i.e. this is a top-level animator, not a child of a Sequential/Parallel animator). |
| `isAnimating()` / `isAnimatingReverse()` / `isAnimatingForward()` | - | boolean | Based on internal `direction` byte (`0`=stopped, `>0`=forward, `<0`=reverse). |
| `getDirection()` | - | `byte` | `final`. `0`, `1`, or `-1`. |
| `reverseOnFinish(boolean)` | flag | `A` (this) | Fluent. If set, when the animation finishes it immediately animates back in the opposite direction once (a "bounce"), then stops. |
| `repeatsOnFinish(int repeats)` | repeat count; negative = infinite | `A` (this) | Fluent. Repeats the full cycle (including any `reverseOnFinish` bounce) this many additional times after the first. |
| `followedBy(IAnimator animator)` | next animator | new `SequentialAnimator` | Wraps `this` and `animator` into a new sequential chain. |
| `inParallelWith(IAnimator animator)` | other animator | new `ParallelAnimator` | Wraps `this` and `animator` into a new parallel group. |

**Gotcha — `stop(boolean force)` semantics:** Calling `stop(false)` while animating does *not* necessarily stop the animator: if `reverseOnFinish` is set and the animator just finished its initial direction, it flips direction and returns `false` (still running); if repeats remain, it restarts the cycle and returns `false`. Only when neither condition applies (or `force=true`) does it actually zero out `direction` and return `true`. This is why `Animator.advance` checks `isAnimating()` after calling `stop(false)` to decide whether the animation truly finished.

---

## `com.cleanroommc.modularui.animation.Animator`

```java
public class Animator extends BaseAnimator<Animator> implements IAnimator
```

The main leaf animator: interpolates a `float` between a min and max bound over a duration, using an `IInterpolation` curve, invoking callbacks on update/finish. This is the class most user code will use directly.

| Field defaults | Value |
|---|---|
| `min` / `max` | `0.0f` / `1.0f` |
| `duration` | `250` ms |
| `curve` | `Interpolation.LINEAR` |

### Constructors
- `Animator()` — no-arg; configure via fluent setters.

### Methods

| Method | Params | Returns | Notes |
|---|---|---|---|
| `copy(boolean reversed)` | whether to swap min/max on the copy | new `Animator` with same curve/reverseOnFinish/repeats/onUpdate/duration/onFinish | Does **not** copy `onUpdate`'s reference identity issues, but does copy the callback references directly (shared, not cloned). If `reversed`, bounds are swapped (`max`→min, `min`→max start). |
| `advance(int elapsedTime)` | elapsed ms | remaining unconsumed ms | Overridden from `IAnimator`; drives `progress` towards `duration` (or 0 if reverse), invoking `onUpdate()` each micro-step and stopping when a bound is hit. Returns early (no-op, returns `elapsedTime` unchanged) if not currently animating. |
| `isAtEnd()` / `isAtStart()` | - | boolean | Progress vs. duration/0. |
| `getMin()` / `getMax()` / `getDuration()` / `getCurve()` | - | current values | Plain getters. |
| `getValue()` | - | `float` — current interpolated value | Does **not** advance the animation itself (a commented-out `advance()` call was removed); relies on `AnimatorManager` having already advanced it this frame. Safe to call every render frame to read the current value. |
| `hasProgressed()` | - | boolean | True once progress has moved off the starting edge for the current direction. |
| `min(float)` / `max(float)` / `bounds(float min, float max)` | bound value(s) | `this` | Fluent. |
| `duration(int duration)` | ms | `this` | Fluent. Javadoc notes actual timing can be off by ~2ms or more since it's tied to frame advances, not a precise timer. |
| `curve(IInterpolation curve)` | interpolation curve, e.g. from `Interpolation` | `this` | Fluent. |
| `onUpdate(DoublePredicate onUpdate)` | predicate receiving current interpolated value; returning `true` stops the animation early | `this` | Fluent. Called every frame the animator advances. |
| `onUpdate(DoubleConsumer onUpdate)` | consumer of current value | `this` | Fluent convenience overload; wraps consumer as a predicate that always returns `false` (never early-stops). |
| `onFinish(Runnable onFinish)` | callback | `this` | Fluent. Runs whenever a cycle or the whole repeat sequence finishes (see `onAnimationFinished`). |

**Gotcha:** `min`/`max`/`bounds`/`duration`/`curve`/`onUpdate`/`onFinish` all mutate the same instance and return `this` — they are not copy-on-write. Reuse of a shared `Animator` instance across multiple UI elements will cause them to fight over state; use `copy(boolean)` if you need independent instances derived from a template.

**Example (adapted from `test/TestGuis.java`, `buildPostTheLogAnimationUI`):**
```java
Animator post = new Animator().curve(Interpolation.SINE_IN).duration(300).bounds(-35, 0);
// ... read post.getValue() during a widget transform to animate a translation
```

---

## `com.cleanroommc.modularui.animation.MutableObjectAnimator<T extends IAnimatable<T>>`

```java
public class MutableObjectAnimator<T extends IAnimatable<T>> extends Animator
```

Drives `Animator`'s `[0,1]` progress but, instead of exposing a raw float, interpolates a mutable/immutable object of type `T` (via `IAnimatable.interpolate`) and applies the interpolated result back onto the `animatable` target each step. Typically created via `IAnimatable.animator(T target)` rather than directly.

### Constructor
- `MutableObjectAnimator(T animatable, T from, T to)` — `animatable` is the live object that gets mutated with intermediate values (if it's mutable — see `IAnimatable.copyOrImmutable()`); `from`/`to` are the interpolation endpoints. Bounds are fixed to `[0f, 1f]` in the constructor (further `bounds()` calls would be meaningless/discouraged here).

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `resume(boolean reverse)` | direction | - | Overridden: after resuming, immediately applies `animatable.interpolate(from, to, getRawValue())` once, so the object snaps to a valid interpolated state right when the animation (re)starts. |
| `onUpdate()` (protected) | - | boolean (stop early?) | Overridden: computes the interpolated intermediate object every step, passes it to `intermediateConsumer` if set, then defers to `Animator.onUpdate()` for the early-stop predicate check. |
| `intermediateConsumer(Consumer<T> consumer)` | consumer invoked with each intermediate interpolated value | `this` | Fluent. Use this to, e.g., write the interpolated object into a widget's state each frame. |

**Inferred:** Because `resume` calls `interpolate` directly rather than going through `onUpdate`, the very first frame's value is applied even before `AnimatorManager` advances the animator — this avoids a one-frame flash of the un-interpolated start value.

No direct usage found in `test/`. See `IAnimatable.animator(T)` below for the intended construction path.

---

## `com.cleanroommc.modularui.animation.IAnimatable<T extends IAnimatable<T>>`

```java
public interface IAnimatable<T extends IAnimatable<T>>
```

Implemented by mutable value types that want to be smoothly animated between two states (e.g. colors, transforms). Self-typed generic pattern.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `interpolate(T start, T end, float t)` | endpoints and progress `t` in `[0,1]` | `T` — the interpolated value | Must be implemented by the value type. May mutate `this` and return it, or return a new instance — contract is up to the implementer. |
| `copyOrImmutable()` | - | `T` | Should return an independent snapshot of the current state (a copy, or `this` if the type is immutable) so that the animation's `from` endpoint doesn't change if the live object is mutated later. |
| `shouldAnimate(T target)` (default) | candidate target state | boolean, default `!equals(target)` | Guard used by all `animate(...)` overloads to skip starting an animation when already at the target. |
| `animator(T target)` (default) | target state | new `MutableObjectAnimator<T>` from `copyOrImmutable()` to `target` | Unchecked cast `(T) this` internally — implementing classes must be their own type parameter. |
| `animate(T target)` (default) | target | - | Forward, default curve/duration, only if `shouldAnimate`. |
| `animate(T target, boolean reverse)` (default) | target, direction | - | |
| `animate(T target, boolean reverse, boolean reverseOnFinish, int repeatsOnFinish)` (default) | target, direction, bounce flag, repeat count | - | |
| `animate(T target, int durationMs, boolean reverse)` (default) | target, duration override, direction | - | |
| `animate(T target, IInterpolation curve, boolean reverse)` (default) | target, curve override, direction | - | |
| `animate(T target, IInterpolation curve, int durationMs, boolean reverse)` (default) | target, curve, duration, direction | - | Delegates to the 6-arg overload with `reverseOnFinish=false, repeatsOnFinish=0`. |
| `animate(T target, IInterpolation curve, int durationMs, boolean reverse, boolean reverseOnFinish, int repeatsOnFinish)` (default) | full control | - | All overloads funnel here or to a subset; all no-op if `!shouldAnimate(target)`. |

No implementers or call sites found under `src/main`; treat as a library extension point. Example below is constructed.

**Example (constructed, not from repo):**
```java
class AnimatedColor implements IAnimatable<AnimatedColor> {
    float r, g, b;

    @Override
    public AnimatedColor interpolate(AnimatedColor start, AnimatedColor end, float t) {
        this.r = start.r + (end.r - start.r) * t;
        this.g = start.g + (end.g - start.g) * t;
        this.b = start.b + (end.b - start.b) * t;
        return this;
    }

    @Override
    public AnimatedColor copyOrImmutable() {
        AnimatedColor c = new AnimatedColor();
        c.r = r; c.g = g; c.b = b;
        return c;
    }
}

AnimatedColor current = new AnimatedColor();
current.animate(targetColor, 500, false); // animate to targetColor over 500ms
```

---

## `com.cleanroommc.modularui.animation.SequentialAnimator`

```java
public class SequentialAnimator extends BaseAnimator<SequentialAnimator> implements IAnimator
```

Runs a list of `IAnimator`s one after another. Acts as a single `IAnimator` itself (composable/nestable).

### Constructors
- `SequentialAnimator(List<IAnimator> animators)` — copies the list; sets `this` as parent on any `BaseAnimator` children (so their `resume()` won't self-register with `AnimatorManager`).
- `SequentialAnimator(IAnimator... animators)` — varargs convenience, same parent-wiring.

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `animate(boolean reverse)` | direction | - | Overridden. No-op if the list is empty. Starts only the first (or last, if reverse — see gotcha) child. |
| `reset(boolean atEnd)` | - | - | Resets `currentIndex` to `0` (or `size-1` if `atEnd`) and resets every child. |
| `resume(boolean reverse)` | direction | - | Resumes the *current* child animator too. |
| `advance(int elapsedTime)` | elapsed ms | remaining ms | Advances the current child; when a child finishes, moves `currentIndex` by `getDirection()` and starts the next child in the sequence, or stops the whole sequence if past the ends. |
| `hasProgressed()` | - | boolean | Delegates to the **first** animator in the list only — `false` for an empty list. |
| `followedBy(IAnimator animator)` | animator to append | `this` | Overridden: throws `IllegalStateException` if called while animating. Otherwise resets the sequence and appends. |

**Gotcha:** `animate(boolean reverse)` always starts `this.animators.get(this.currentIndex)` — after a `reset(true)`, `currentIndex` points at the *last* element, so `animate(true)` (reverse) correctly starts from the end; but calling `animate(true)` without a preceding `reset(true)` will still start from whatever `currentIndex` last was, not necessarily the last element. Always pair `reset(atEnd)`/`animate(reverse)` consistently, or just use `animate(reverse)` which does this pairing via `IAnimator.animate` default... note `SequentialAnimator` does **not** override the default `animate(boolean)` from `IAnimator` — wait, it does override it here, and does *not* call `reset` internally, unlike the default `IAnimator.animate`. So calling `sequential.animate(reverse)` skips the implicit reset that leaf `Animator`s get. Call `reset(reverse)` explicitly first if you need a clean start.

**Example (adapted from `test/TestGuis.java`, `buildPostTheLogAnimationUI`):**
```java
IAnimator animator = new Wait(300)
        .followedBy(post)
        .followedBy(the)
        .followedBy(extraordinary)
        .followedBy(log)
        .followedBy(logGrow);
animator.animate();
```
(Here `new Wait(300).followedBy(...)` chains via `BaseAnimator.followedBy`, which internally builds a `SequentialAnimator`.)

---

## `com.cleanroommc.modularui.animation.ParallelAnimator`

```java
public class ParallelAnimator extends BaseAnimator<ParallelAnimator> implements IAnimator
```

Runs a list of `IAnimator`s at the same time (optionally staggered).

### Constructors
- `ParallelAnimator(List<IAnimator> animators)` — copies list; wires parent on `BaseAnimator` children.
- `ParallelAnimator(IAnimator... animators)` — varargs convenience.

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `animate(boolean reverse)` | direction | - | If `waitTimeBetweenAnimators <= 0`, starts **all** children immediately. Otherwise starts only the first and staggers the rest during `advance`. |
| `stop(boolean force)` | - | boolean | Overridden: if the base stop succeeds (see `BaseAnimator.stop` semantics), propagates `stop(force)` to all children too. |
| `reset(boolean atEnd)` | - | - | Resets stagger counters and all children. |
| `advance(int elapsedTime)` | elapsed ms | remaining ms | Advances each started child; tracks how many have finished via `isFinished()`; if staggering, starts additional children as `waitTimeBetweenAnimators` elapses. |
| `isFinished()` | - | boolean | True when every child has finished. |
| `hasProgressed()` | - | boolean | True if animating and at least one child has been started. |
| `waitTimeBetweenAnimators(int waitTime)` | ms delay before starting each subsequent animator | `this` | Fluent. `0` (default) = all start simultaneously. |
| `inParallelWith(IAnimator animator)` | animator to add | `this` | Overridden: throws `IllegalStateException` if called while animating; otherwise resets the group and appends. |

No direct call sites found in `test/`. Example below is constructed from the sibling `SequentialAnimator` usage pattern.

**Example (constructed, not from repo):**
```java
Animator fadeIn = new Animator().bounds(0, 1).duration(300);
Animator slideIn = new Animator().bounds(-20, 0).duration(300);
IAnimator group = new ParallelAnimator(fadeIn, slideIn);
group.animate();
```

---

## `com.cleanroommc.modularui.animation.Wait`

```java
public class Wait extends BaseAnimator
```

A no-op animator that simply consumes a fixed duration before finishing — useful as a delay step inside a `SequentialAnimator` chain. Note: extends `BaseAnimator` with a raw type (no generic argument), so its fluent setters inherited from `BaseAnimator` (`reverseOnFinish`, `repeatsOnFinish`) return the raw `BaseAnimator` type, not `Wait`.

### Constructors
- `Wait()` — 250ms default duration.
- `Wait(int duration)` — duration in ms.

### Methods
| Method | Params | Returns | Notes |
|---|---|---|---|
| `reset(boolean atEnd)` | - | - | Overridden: always resets `progress` to `0` regardless of `atEnd` (does not support "wait already at end" state). |
| `advance(int elapsedTime)` | elapsed ms | remaining ms | Consumes up to `duration - progress` ms; calls `stop(false)` once `progress >= duration`. |
| `hasProgressed()` | - | boolean | `progress > 0 && isAnimating()`. |
| `duration(int duration)` | ms | `this` (typed as `Wait`) | Fluent — this one explicitly returns `Wait`, unlike the inherited raw-typed setters. |

**Example (adapted from `test/TestGuis.java`):**
```java
IAnimator animator = new Wait(300).followedBy(post); // 300ms delay before `post` starts
```

---

## `com.cleanroommc.modularui.animation.AnimatorManager`

```java
public class AnimatorManager {
    public static void init();
    static void startAnimation(IAnimator animator); // package-private
}
```

The framework-internal driver that actually advances every root-level `IAnimator` once per frame. Not meant to be constructed or called directly by mod code — animators register themselves automatically.

| Member | Notes |
|---|---|
| `init()` | registers a `new AnimatorManager()` instance onto `MinecraftForge.EVENT_BUS`; called once from `ClientProxy.preInit`. Calling it again would register an additional listener instance (no guard against double-init). |
| `startAnimation(animator)` | package-private; called by `BaseAnimator.animate()`/similar **only when `this.parent == null`** (i.e. only top-level animators register themselves — child animators inside a `SequentialAnimator`/`ParallelAnimator` are driven by their parent, not by `AnimatorManager` directly). Adds to a `queuedAnimators` list rather than the live list directly, guarded by a `contains` check against both `animators` and `queuedAnimators` so the same animator instance isn't queued twice. |
| `onDraw(GuiScreenEvent.DrawScreenEvent.Pre)` | `@SubscribeEvent(priority = HIGHEST)`; once per frame, computes elapsed time since `lastTime` via `IAnimator.getTimeDiff`, then for every registered animator: skips it if `isPaused()`, otherwise calls `advance(elapsedTime)` and removes it from the list if `isAnimating()` becomes `false`. After processing, flushes `queuedAnimators` into the live `animators` list. **Gotcha:** on the very first frame (`lastTime == 0`), no animators are advanced at all — that frame is only used to establish a `lastTime` baseline. |
| `onDraw(GuiOpenEvent)` | `@SubscribeEvent(priority = LOWEST)`; when a GUI is closed (`event.gui == null`), force-stops (`stop(false)`) and clears every currently-tracked animator — animations don't persist or resume across GUI screens. |

**Gotcha:** `queuedAnimators` exists specifically to avoid mutating the `animators` list while iterating it inside `removeIf` during the same frame an animation is started — a new animator added mid-frame won't be advanced until the *next* frame's `onDraw`, since the queue is only flushed after the removal pass.

**Example (constructed, not from repo)** — mod code never calls this class directly; it only observes it working transparently once an animator starts:
```java
Animator fadeIn = new Animator().bounds(0, 1).duration(200);
fadeIn.animate(); // internally: BaseAnimator.animate() -> AnimatorManager.startAnimation(this)
// no further action needed — AnimatorManager.onDraw advances it every frame from here on
```
