# Debug GUI Notes

The old external `network.rs485.debuggui` implementation is not available in this workspace. The project now provides a
local `network.rs485.debuggui.DebugGuiEntry` implementation so `IDebugGuiEntry.create()` resolves without the original
debug GUI dependency.

## Current Flow

- `DebugGuiController.startWatchingOf` allocates a numeric debug connection id.
- `DebugPanelOpen` is sent to the client with the target name and that connection id.
- The client creates a Swing window and sends snapshot requests through `DebugDataPacket`.
- The server renders a reflection snapshot of the watched object and sends the text back through the same connection id.
- Client-side debug payloads that arrive before the matching panel are queued and replayed once the panel exists.
- Client debug windows are closed on client world unload/connect, and server-side debug sessions are cleared when the
  player logs out.

## DebugGuiEntry Protocol

The payload inside `DebugDataPacket` is a small internal byte protocol:

- `1`: client requests a fresh snapshot.
- `2`: either side closes the debug connection.
- `3`: server sends a snapshot with timestamp, title, and UTF-8 text body.
- `4`: server sends an error message.

Snapshots are intentionally text based. They inspect fields directly, skip static and synthetic fields, limit recursion,
truncate very large output, and use `IObjectIdentification` for LogisticsPipes-specific values such as worlds and large
arrays.
