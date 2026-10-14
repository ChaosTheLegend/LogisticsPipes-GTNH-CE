# Request-table GUI code review

Reviewed and refactored on 10 October 2026.

The review covers the new request-table screen, request popup, network grid and presentation models,
buttons and icons, upgrade screen and board, chip rendering and assembly animation, GUI layouts,
display preferences, and the container layout and material rules used by these screens.

## Shared rendering

- `RequestTableRender` owns GUI render-state scopes, item rendering, texture quads and partial sprite rows,
  slot grids, hit detection and clipping. Its state scope restores attributes and the model-view matrix
  through try-with-resources. Board clipping respects an existing parent clip.
- `RequestTableGuiStyle` owns bevels, borders, scrollbars and count typography. Popup and ordinary buttons
  share the same bevel implementation while retaining their existing colors.
- `RequestTableButton` is the common base for text, icon and monitor buttons. `RequestTableIcons` owns their
  pixel artwork, including the shared hammer. Atlas indexes are explicit rather than inferred from enum order.
- Storage chips, special controllers and the board background use the same texture renderer. Partial assembly
  layers keep their native dimensions and UV coordinates, including the hand-edited small-chip offsets.
- `RequestTableUpgradeStatus` supplies the applied, waiting and prerequisite states to both upgrade descriptions
  and tooltips. Applied upgrades continue to hide costs, including while another server response is pending.

## Findings fixed

| Issue | Resolution |
| --- | --- |
| The request popup rewrote its amount text after arrow-key input, moving the caret to the end. | `RequestTableNumberField` filters text before insertion and preserves vanilla caret, selection, deletion and clipboard handling. Both amount inputs use it. |
| Several drawing paths restored OpenGL state only on normal return; icon buttons also inherited item lighting. | Shared render scopes restore state on exceptional exits and use neutral lighting for GUI controls. Native item rendering still manages its own lighting and depth. |
| Board scissor setup could widen an existing clip region. | The board now intersects its clip with the parent clip and display bounds. |
| Combined network and table stock could overflow an integer, making the displayed amount negative and hiding stocked entries. | Addition uses a long intermediate and saturates at the supported integer limit. |
| Cached search names called modded display-name providers without a fallback, despite known broken facade variants. | Name resolution falls back to LP's guarded friendly name and reuses the resolved name rather than calling the failing provider twice. |
| Upgrade prerequisites were repeated in the details panel and multiple tooltips. | A shared status calculation keeps the applied, prerequisite and waiting descriptions consistent. |

## Java 25 conventions

Button actions and tooltips, view selection, filter/sort choices, icon selection, controller rendering and chip
families use arrow switches or switch expressions. Enum decisions are exhaustive where appropriate. Small
guards and comparisons remain ordinary conditions. Typed iteration replaces raw casts, and local `var` is used
where the initializer makes the type clear.

The configured Java 25 toolchain still uses Jabel and emits Java 8 bytecode. The refactor therefore retains the
existing runtime target and uses compatible library APIs. It introduces no `List.of`, record runtime dependency,
preview language feature or project-wide build change.

## Validation

Scoped Spotless formatting and `compileJava` passed. No tests were added or run, as requested. The compiler's
existing warnings about primitive `@NonNull` in `LaserData` are unrelated to the GUI refactor.
`javap` confirmed class-file major version 52 for the main screen and shared renderer, preserving Java 8 bytecode.

Rendering was checked by inspecting the shared coordinate and UV calculations against the previous paths.
In-game verification remains needed for GL behavior and mod-provided renderers: chest lids, NEI fluid icons,
popup brightness, small buttons, board zoom/dragging, chip assembly and numeric-field editing.
