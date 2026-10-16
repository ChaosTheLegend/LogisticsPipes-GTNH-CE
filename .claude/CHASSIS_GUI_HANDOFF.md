# Chassis GUI rewrite — session handoff

Working session on branch `dev`. All changes below are **uncommitted** — see "Before switching machines" at the bottom.

## Original task

Implement a new ModularUI2 GUI for chassis pipes (`PipeLogisticsChassi` / `PipeLogisticsChassiMk1..Mk5`) to
replace the old, broken one. Requirements as given:

1. Top of the GUI: a row of page buttons, each with a module item-slot, to insert modules into.
2. Main GUI area calls into "module UI" and builds it dynamically based on the module selected.
3. When editing a module in the GUI, the new module NBT should be loaded/saved to both the module and the
   chassis — this should also happen on chassis GUI close.
4. Module UI should update dynamically on page change and on module insert.
5. Each module's sync handlers must have a unique name inside the chassis GUI so they don't collide with
   another module's sync handlers (e.g. two Provider modules in two different chassis slots).

## Root causes found (before writing any code)

`ChassisGui.java` had been deleted from the working tree because it didn't work. Two root bugs, found via
research agents that traced ModularUI2 semantics (`modularUI-docs`) and the actual click-dispatch
chain:

1. It built each module's editor from a **throwaway** `LogisticsModule` instance
   (`itemModule.getModuleForItem(stack, null, null, null)` — passing `null` for `currentModule` always
   constructs a new one) instead of the actually-installed live module, so edits never affected real pipe
   behavior.
2. It was **never reachable** — `PipeLogisticsChassi` didn't implement `IMUICompatiblePipeV2`, so
   wrench-right-click fell through to the legacy `ChassiGuiProvider`/`GuiChassiPipe` Swing GUI instead.

Full plan (with the ModularUI2 API detail — `DynamicSyncHandler`, `PanelSyncManager`, `PagedWidget` etc. — that
was verified against the library's actual decompiled sources) is preserved at:
`C:\Users\Chaos\.claude\plans\compressed-pondering-feather.md` (local to this machine — copy it over if you
want the full design rationale; the summary below covers what's needed to continue).

## What was implemented (all uncommitted, `git status` below)

### 1. `../src/main/java/logisticspipes/api/IMUICompatibleModule.java`
Added a prefix-aware default method:
```java
default LogisticsModularUI getPipeGui(String prefix) {
    return getPipeGui();
}
```

### 2. Eight module classes + their `*MuiDynamic` wrappers (mechanical, same pattern each)
`LogisticsModularUI` has a `prefix` field and `getFullId() = prefix + "_" + getId()`, used to key every sync
handler. `getPipeGui()` never threaded a prefix through, so two chassis slots holding the same module type
would collide on identical sync-handler keys. Fixed by adding a `(module, prefix)` constructor to each
`*MuiDynamic` class and a matching `getPipeGui(String prefix)` override on each module class:
- `ModuleProvider` / `ModuleProviderMuiDynamic`
- `ModuleActiveSupplier` / `ModuleActiveSupplierMuiDynamic`
- `ModuleApiaristAnalyser` / `ModuleBeeAnalyzerMuiDynamic`
- `ModuleElectricManager` / `ModuleElectricManagerMuiDynamic`
- `ModuleEnchantmentSinkMK2` / `ModuleEnchantmentSinkMK2MuiDynamic`
- `ModuleItemSink` / `ModuleItemSinkMuiDynamic`
- `ModulePassiveSupplier` / `ModulePassiveSupplierMuiDynamic`
- `ModuleTerminus` / `ModuleTerminusMuiDynamic`

(These 8 are the complete set implementing `IMUICompatibleModule`. `ModuleCrafter` does not implement it —
`ChassisGui` special-cases it with a "use Pattern Crafting Pipes instead" message.)

### 3. `../src/main/java/logisticspipes/pipes/PipeLogisticsChassi.java`
Now `implements IMUICompatiblePipeV2` with:
```java
@Override
public LogisticsModularUI getPipeGui() {
    return new ChassisGui(this);
}
```
`CoreRoutedPipe.blockActivated` (final) already checks `instanceof IMUICompatiblePipeV2` before falling back
to the legacy path — no other wiring was needed. The old `ChassiGuiProvider`/`GuiChassiPipe` path is now dead
code, left in place.

### 4. `../src/main/java/logisticspipes/gui/modularUI/ChassisGui.java` (new file, recreates the deleted one)
- Builds each slot's editor from the **live installed module** (`pipe.getModules().getSubModule(slot)`), not
  a throwaway instance.
- Each slot's module UI gets a unique `"chassis_slot_" + slot` prefix via `getPipeGui(prefix)`.
- Close-listener flushes each installed module's state into its item stack via
  `ItemModuleInformationManager.saveInfotmation(stack, module)` **with the required
  `setInventorySlotContents` write-back** — `ItemIdentifierInventory.getStackInSlot` returns a *clone*
  (it's `@Deprecated` and documented as such), so without the write-back the flush would silently no-op.
- Fixed a tab-texture bug where only the 8-slot Mk5 chassis got correct end-cap tabs (was hardcoded to
  `i == 7`, now `pipe.getChassiSize() - 1`).

## Bug found during manual testing, and the fix (already applied)

Manual test in `runClient` surfaced a crash. Crash reports were found in
`../run/client/crash-reports/crash-2026-08-05_17.24.57-server.txt` (and a separate, unrelated one at
`crash-2026-08-05_17.23.09-client.txt`, see "Known remaining issue" below) which pinned the exact cause:

```
java.lang.NullPointerException: Cannot invoke "SyncHandler.init(String, PanelSyncManager)" because "syncHandler" is null
    at PanelSyncManager.lambda$initialize$0(PanelSyncManager.java:64)
    at ... Object2ReferenceLinkedOpenHashMap$MapEntrySet.fastForEach(...)
    at PanelSyncManager.initialize(PanelSyncManager.java:64)
    ... at CoreRoutedPipe.blockActivated  (i.e. this happened on GUI open)
```

**Root cause:** the first version of `ChassisGui` eagerly called `handler.notifyUpdate(packet -> {})` right
after constructing each per-slot `DynamicSyncHandler`, to seed its initial content. Since the handler isn't
valid yet at that point, ModularUI2 caches the call and replays it from inside `DynamicSyncHandler.init()`
— which itself runs from inside `PanelSyncManager.initialize()`'s own iteration over its sync-handler map.
The replayed call creates new nested sync handlers (for the module's fields), mutating that same map
mid-iteration; fastutil's "fast" iterator doesn't tolerate that and hands back a corrupted null entry. This
only triggered once a slot actually held a module (empty slots need no nested handlers), and it also
explained why NBT/state appeared not to load on open — those late-created handlers missed the framework's
first value-push pass to the client.

**Fix (already applied):** removed the eager seed. Each slot's module widget is now built *synchronously*
during normal (non-dynamic) panel construction, via `DynamicSyncedWidget.initialChild(...)`, registering its
sync handlers the same safe way every other panel in this codebase does. `DynamicSyncHandler`/`notifyUpdate`
is now used *only* for the genuinely dynamic case — rebuilding a page when its module is swapped later while
the GUI is open — which fires via client-side packet processing, safely outside any map-iteration context.

This fix has been applied to `ChassisGui.java` and the project compiles + passes `spotlessCheck`. **It has
not yet been re-tested in `runClient`** — that's the next step on a fresh machine.

## Known remaining issue (not fixed, needs a decision)

A *separate*, pre-existing bug surfaced in the same test session (`crash-2026-08-05_17.23.09-client.txt`):

```
NullPointerException: Cannot invoke ForgeDirection.getOpposite() because CoreRoutedPipe.getPointedOrientation() is null
    at CoreRoutedPipe.getPointedInventory(CoreRoutedPipe.java:1639)
    at ModuleItemSink.importFromInventory(ModuleItemSink.java:397)
    at ModuleItemSinkMuiDynamic.lambda$addWidgets$1(ModuleItemSinkMuiDynamic.java:59)
```

`ModuleItemSink`'s "import from inventory" button assumes the module is hosted by a pipe with its own single
facing direction — that concept doesn't map cleanly onto a chassis slot. This is **not** something the
ChassisGui rewrite introduced; it's a latent incompatibility between `ModuleItemSink` and being hosted in a
chassis at all. Not fixed — was offered to the user as a follow-up (make it no-op gracefully instead of
crashing) but no decision was made before this session ended.

## Task list state (from TaskCreate/TaskUpdate this session)

1. [completed] Add `getPipeGui(String prefix)` default to `IMUICompatibleModule`
2. [completed] Thread prefix through 8 module classes + MuiDynamic wrappers
3. [completed] Wire `PipeLogisticsChassi` into `IMUICompatiblePipeV2`
4. [completed] Rewrite `ChassisGui.java`
5. [completed] Compile and format check (`./gradlew classes spotlessCheck` passes)

(Plus the crash fix above, done after task 5 as a follow-up once manual testing surfaced it — not tracked as
its own numbered task.)

## Next steps on the new machine

1. Pull/copy this branch's changes (see "Before switching machines" below).
2. Re-run `./gradlew runClient`, place a chassis pipe, install a module (e.g. Provider), open its GUI, and
   verify:
   - The module's editor shows up immediately, with correct current values (not defaults).
   - Editing a value (e.g. whitelist/blacklist toggle) works without crashing.
   - Closing and reopening the GUI preserves the edit.
   - Two modules of the same type in different slots don't bleed into each other.
   - Swapping the module item in a slot while the GUI is open rebuilds that page.
3. Decide on the `ModuleItemSink` orientation crash (leave as a known limitation, or harden
   `CoreRoutedPipe.getPointedInventory`/`ModuleItemSink.importFromInventory` to no-op when there's no valid
   pointed orientation instead of crashing).
4. Once verified, commit. Nothing has been committed yet this session.

## Before switching machines — uncommitted changes

`git status --short` at end of session:
```
 M src/main/java/logisticspipes/api/IMUICompatibleModule.java
D  src/main/java/logisticspipes/gui/modularUI/ChassisGui.java   (staged deletion of the OLD file; a NEW file now sits at the same path, see below)
 M src/main/java/logisticspipes/gui/modularUI/DraggableFlow.java   (cosmetic spotless reflow only, not a functional change)
 M src/main/java/logisticspipes/gui/modularUI/PipeGuiFactory.java
 M src/main/java/logisticspipes/gui/modularUI/dynamicModules/Module*MuiDynamic.java   (8 files, prefix ctor)
 M src/main/java/logisticspipes/modules/Module*.java   (8 files, getPipeGui(prefix) override)
 M src/main/java/logisticspipes/pipes/PipeLogisticsChassi.java
?? src/main/java/logisticspipes/gui/modularUI/ChassisGui.java   (new file, untracked)
```
None of this is committed. To continue on another machine, either:
- Commit this work here first, push, and pull on the other machine, or
- Copy the working directory (including the untracked `ChassisGui.java` and this handoff file) directly.

This file (`CHASSIS_GUI_HANDOFF.md`) is untracked too — it will show up in `git status` until committed or
removed.
