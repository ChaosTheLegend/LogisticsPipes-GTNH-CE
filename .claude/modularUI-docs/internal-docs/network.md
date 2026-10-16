# `network` Package

The `network` package is ModularUI2's client&lt;-&gt;server transport layer: one Forge
`SimpleNetworkWrapper` channel (`NetworkHandler.CHANNEL`), a packet contract (`IPacket`), and the
concrete packets (`network.packets`) that open/close/reopen GUIs and carry synced-widget state
(`PacketSyncHandler`) between the two sides. `ModularNetwork`/`ModularNetworkSide` track, per
logical side, which `ModularSyncManager` instances (i.e. open synced GUIs) are currently active by
an integer "network ID", and route packets to/from them.

This layer is internal transport plumbing — **no file under `com.cleanroommc.modularui.test/`
references any class in `network` or `network.packets` directly** (grepped; zero matches). Mod
authors interact with synced widgets/GUI opening through higher-level APIs
(`GuiFactories`/`UIFactory`, `SyncHandler` subclasses, `ModularSyncManager`) and never construct
these packets by hand; `test/TestBlock.java` (`GuiFactories.tileEntity().open(...)`) and
`test/TestItem.java` (`GuiFactories.playerInventory().openFromMainHand(...)`) exercise the *result*
of this layer (an `OpenGuiPacket` eventually gets sent) but not the classes themselves. Examples
below are marked accordingly.

---

## 1. `com.cleanroommc.modularui.network.IPacket`

The packet contract every ModularUI2 network message implements. Extends Forge's
`cpw.mods.fml.common.network.simpleimpl.IMessage` so instances can be registered with a
`SimpleNetworkWrapper`, but replaces `IMessage`'s raw-`ByteBuf` read/write with a `PacketBuffer`
based contract plus side-specific execute callbacks.

```java
public interface IPacket extends IMessage {
    void write(PacketBuffer buf) throws IOException;
    void read(PacketBuffer buf) throws IOException;

    @SideOnly(Side.CLIENT)
    @Nullable
    default IPacket executeClient(NetHandlerPlayClient handler) { return null; }

    @Nullable
    default IPacket executeServer(NetHandlerPlayServer handler) { return null; }

    @Deprecated @Override default void fromBytes(ByteBuf buf);
    @Deprecated @Override default void toBytes(ByteBuf buf);
}
```

| Method | Params | Returns | Purpose / gotchas |
|---|---|---|---|
| `write(buf)` | `PacketBuffer` to serialize into | `void`, throws `IOException` | Serialize this packet's fields. **Order matters**: `read` must consume fields in exactly the order `write` produced them (all concrete packets below follow this by construction). |
| `read(buf)` | `PacketBuffer` to deserialize from | `void`, throws `IOException` | Javadoc: *"Do not do anything else other than reading this packet!"* — i.e. no side effects/logic here, only field population; side effects belong in `executeClient`/`executeServer`. |
| `executeClient(handler)` | client play net handler | `IPacket` or `null` | `@SideOnly(Side.CLIENT)` — only ever invoked (and only safe to invoke) on the physical/logical client. Return a non-null `IPacket` to send an immediate reply; default `null` (no reply). Called by `NetworkHandler.S2CHandler`. |
| `executeServer(handler)` | server play net handler | `IPacket` or `null` | Runs on the server; same reply semantics as `executeClient`. Called by `NetworkHandler.C2SHandler`. |
| `fromBytes(buf)` / `toBytes(buf)` | raw `ByteBuf` | `void` | `@Deprecated` `IMessage` bridge methods — wrap the given `ByteBuf` in a `PacketBuffer` (if it isn't already one) and delegate to `read`/`write`, converting any `IOException` into an unchecked `RuntimeException`. Present only so Forge's `SimpleNetworkWrapper`/codec machinery (which knows about `IMessage`, not `IPacket`) can still (de)serialize instances; **implementers should never call or override these directly** — override `read`/`write` instead. |

**Gotcha:** a packet class must have a no-arg constructor (Forge's codec instantiates it via
reflection before calling `fromBytes`/`read`) in addition to whatever data constructor it exposes
for the sending side — every concrete packet in `network.packets` follows this pattern.

**Example (constructed, not from repo)** — a minimal custom packet:

```java
package com.example.mymod.network;

import com.cleanroommc.modularui.network.IPacket;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.PacketBuffer;
import java.io.IOException;

public class MyActionPacket implements IPacket {
    private int value;

    public MyActionPacket() {} // required no-arg ctor for Forge's codec

    public MyActionPacket(int value) { this.value = value; }

    @Override public void write(PacketBuffer buf) throws IOException { buf.writeVarIntToBuffer(value); }
    @Override public void read(PacketBuffer buf) throws IOException { value = buf.readVarIntFromBuffer(); }

    @Override
    public IPacket executeServer(NetHandlerPlayServer handler) {
        // handle on the server, e.g. mutate a tile entity belonging to handler.playerEntity
        return null;
    }
}
```

---

## 2. `com.cleanroommc.modularui.network.NetworkHandler`

The single registration point and send entry point for ModularUI2's Forge network channel. All
packet classes in `network.packets` are registered here; nothing else in the codebase creates a
`SimpleNetworkWrapper` for ModularUI2.

```java
public class NetworkHandler {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(ModularUI.ID);

    public static void init();
    public static void sendToServer(IPacket packet);
    public static void sendToWorld(IPacket packet, World world);
    public static void sendToPlayer(IPacket packet, EntityPlayerMP player);
}
```

| Member | Signature | Purpose / gotchas |
|---|---|---|
| `CHANNEL` | `public static final SimpleNetworkWrapper` | The Forge channel, named after `ModularUI.ID` (`"modularui2"`). Created eagerly at class-load time (static field initializer), not inside `init()`. |
| `init()` | `static void init()` | Registers every built-in packet with an auto-incrementing discriminator byte (`packetId`, starting at 0): `SClipboard` (S2C only), `SyncConfig` (C2S only), then `OpenGuiPacket`, `ReopenGuiPacket`, `CloseGuiPacket`, `CloseAllGuiPacket`, `PacketSyncHandler` (all registered both directions via `registerBoth`). **Gotcha:** registration order fixes each packet's discriminator ID for this channel instance — this must run exactly once, and (implicitly) before any packet of these types is sent; **the file itself does not show where `init()` is called from** (not present in `network`/`network.packets`, presumably invoked once from the mod's proxy/setup code elsewhere in the codebase, outside this doc's scope). |
| `registerC2S(clazz)` / `registerS2C(clazz)` / `registerBoth(clazz)` | `private static void` | Thin wrappers around `CHANNEL.registerMessage(handler, clazz, packetId++, Side.SERVER\|CLIENT)`, using the shared `C2SHandler`/`S2CHandler` message handlers for every packet type (i.e. dispatch is generic — it always just calls `executeServer`/`executeClient` on whatever `IPacket` came in, never packet-type-specific logic). `registerBoth` calls both S2C and C2S registration (consuming two discriminator IDs). |
| `sendToServer(packet)` | `IPacket packet` | `void` | Client -> server send. Delegates to `CHANNEL.sendToServer(packet)`. Only meaningful/safe to call from the client thread. |
| `sendToWorld(packet, world)` | `IPacket packet, World world` | `void` | Server -> all clients tracking a dimension. Delegates to `CHANNEL.sendToDimension(packet, world.provider.dimensionId)`. |
| `sendToPlayer(packet, player)` | `IPacket packet, EntityPlayerMP player` | `void` | Server -> one client. Delegates to `CHANNEL.sendTo(packet, player)`. |
| `S2CHandler` / `C2SHandler` | `final static IMessageHandler<IPacket, IPacket>` | package-private lambdas: `S2CHandler` calls `message.executeClient(ctx.getClientHandler())`; `C2SHandler` calls `message.executeServer(ctx.getServerHandler())`. This is the only dispatch logic in the entire channel — every packet's behavior lives in its own `executeClient`/`executeServer`, not in `NetworkHandler`. |

**Gotchas:**
- `sendToServer` must only be called client-side; `sendToPlayer`/`sendToWorld` only server-side —
  `NetworkHandler` itself performs no side validation, callers (e.g. `ModularNetwork.Client`/
  `Server`) are responsible for calling the right method on the right side.
- Because dispatch is fully generic (`executeClient`/`executeServer` on the message itself), adding
  a new packet type requires no changes to `S2CHandler`/`C2SHandler` — only a new
  `registerC2S`/`registerS2C`/`registerBoth` call in `init()`.

**Example (constructed, not from repo)** — sending the custom packet from section 1:

```java
// client-side
NetworkHandler.sendToServer(new MyActionPacket(42));

// server-side, to one player
NetworkHandler.sendToPlayer(new MyActionPacket(42), (EntityPlayerMP) somePlayer);
```

(No file in `network`/`network.packets` calls `NetworkHandler.init()`, `registerC2S`, or
`registerS2C` with a non-built-in packet — a mod author adding a custom packet would need to
register it themselves, on their own channel or by extending this one; **Inferred**, not shown in
source.)

---

## 3. `com.cleanroommc.modularui.network.ModularNetwork`

`@ApiStatus.Internal` abstract holder for the two logical-side singletons that track active synced
GUIs ("network IDs" -> `ModularSyncManager`) and route sync/close/reopen traffic to
`NetworkHandler`. Source comment: *"These need to be separate instances, otherwise they would
access the same maps in singleplayer... there is no validation [of picking the wrong logical
side]."*

```java
public abstract class ModularNetwork {
    public static final Client CLIENT = new Client();
    public static final ServerManager SERVER = new ServerManager();

    public static ModularNetworkSide get(boolean client);
    public static ModularNetworkSide get(Side side);
    public static ModularNetworkSide get(EntityPlayer player);

    public static final class Client extends ModularNetworkSide { ... }
    public static final class ServerManager extends Server { ... }
    @ApiStatus.NonExtendable
    public static class Server extends ModularNetworkSide { ... }
}
```

| Method | Params | Returns | Notes |
|---|---|---|---|
| `get(boolean client)` | `true` for client | `ModularNetworkSide` | Returns `CLIENT` or `SERVER`. |
| `get(Side side)` | Forge `Side` | `ModularNetworkSide` | `side.isClient() ? CLIENT : SERVER`. |
| `get(EntityPlayer player)` | any player | `ModularNetworkSide` | `get(NetworkUtils.isClient(player))` — picks the side based on the player instance (works for both `EntityPlayerSP` and `EntityPlayerMP`/remote players in singleplayer). |

### `ModularNetwork.Client` (singleton `CLIENT`)

One shared `activeScreens` map for the *local* client (a single-player client only ever has one
player, so a flat map is correct here, unlike the server).

| Method | Params | Returns | Gotchas |
|---|---|---|---|
| `activate(nid, msm)` | network ID, `ModularSyncManager` | `void` | Registers a newly-opened synced GUI's manager under `nid`. Throws `IllegalStateException` (via `activateInternal`) if `nid` is already active. |
| `sendPacket(packet, player)` (package-private, `@SideOnly(Side.CLIENT)`) | — | `void` | `NetworkHandler.sendToServer(packet)` — ignores the `player` param entirely (there's only one client player). |
| `closeContainer(player)` (package-private, `@SideOnly(Side.CLIENT)`) | — | `void` | Mimics `EntityPlayerSP.closeScreenAndDropStack()` **but without actually closing the screen**: clears `player.inventory.setItemStack(null)` and resets `player.openContainer = player.inventoryContainer`. |
| `closeContainer(networkId, dispose, player)` | int, boolean, `EntityPlayerSP` | `void`, `@SideOnly(Side.CLIENT)` | Convenience overload; calls the inherited 4-arg `closeContainer(networkId, dispose, player, true)` (i.e. `sync = true`, meaning it will additionally *send* a `CloseGuiPacket` back — see `ModularNetworkSide`). |
| `closeAll()` | — | `void`, `@SideOnly(Side.CLIENT)` | `closeAll(Minecraft.getMinecraft().thePlayer)`. |
| `reopenSyncerOf(guiScreen)` | any `GuiScreen` | `void`, `@SideOnly(Side.CLIENT)` | If `guiScreen instanceof IMuiScreen ms` and `!ms.getScreen().isClientOnly()`, calls `reopen(player, ms.getScreen().getSyncManager(), true)` — re-triggers server sync for an already-open synced screen (e.g. after some client-side reconnect/refresh scenario). No-ops for client-only screens. |

### `ModularNetwork.ServerManager` (singleton `SERVER`)

Wraps a *per-player* map of `Server` instances (`Map<UUID, Server> playerHandlers`), because a
dedicated server has many simultaneous players, each needing their own `activeScreens` bookkeeping.
Every inherited/overridden method here just resolves the calling player's own `Server` via
`get(player)` (creating one lazily with `computeIfAbsent`) and delegates.

| Method | Params | Returns | Notes |
|---|---|---|---|
| `get(player)` | `EntityPlayer` | `Server` | `playerHandlers.computeIfAbsent(player.getUniqueID(), k -> new Server())` — lazily creates per-player state. |
| `activate(player, msm)` | player, sync manager | `int` | `get(player).activate(msm)` — allocates and returns the next network ID for that player (see `Server.activate` below). |
| `onPlayerLeave(player)` | — | `void` | Delegates to the player's `Server.onPlayerLeave`, then **removes** that player's `Server` entirely from `playerHandlers` — cleans up all state for a disconnected player. |
| `closeAll(player)` / `closeAll(player, sync)` | — | `void` | Delegates to the player's own `Server`. |
| `receivePacket(player, packet)` | `PacketSyncHandler` | `void` | Delegates to the player's own `Server`. |
| `sendSyncHandlerPacket(panel, syncHandler, buffer, player)` | — | `void` | Delegates to the player's own `Server`. |
| `sendActionPacket(msm, panel, key, buffer, player)` | — | `void` | Delegates to the player's own `Server`. |
| `closeContainer(networkId, dispose, player, sync)` | — | `void` | Delegates to the player's own `Server`. |
| `reopen(player, networkId, sync)` / `reopen(player, msm, sync)` | — | `void` | Delegates to the player's own `Server`. |

### `ModularNetwork.Server` (`@ApiStatus.NonExtendable`, base of both a per-player `Server` and used directly as the base class `ServerManager` extends)

| Method | Params | Returns | Notes |
|---|---|---|---|
| `activate(msm)` (protected) | `ModularSyncManager` | `int` | Allocates the next network ID: increments `nextId` (starting at `-1`, so the first ID is `0`), wraps back to `0` once it exceeds `100_000`, then calls `activateInternal(nextId, msm)` and returns `nextId`. **Gotcha:** wraparound means IDs are reused after 100,000 activations for a given player/session — fine in practice since old entries are removed on close/disconnect, but a bug that never deactivates a manager could eventually collide. |
| `sendPacket(packet, player)` (package-private) | — | `void` | `NetworkHandler.sendToPlayer(packet, (EntityPlayerMP) player)` — **unchecked cast**, will throw `ClassCastException` if called with a non-`EntityPlayerMP` (i.e. must only run server-side with a real remote/local server-side player object). |
| `closeContainer(player)` (package-private) | — | `void` | `((EntityPlayerMP) player).closeContainer()` — vanilla container close; same unchecked-cast gotcha. |
| `closeContainer(networkId, dispose, player)` | int, boolean, `EntityPlayerMP` | `void` | Convenience overload for `closeContainer(networkId, dispose, player, true)` (syncs back to the client via packet). |

---

## 4. `com.cleanroommc.modularui.network.ModularNetworkSide`

Abstract base shared by `ModularNetwork.Client` and `ModularNetwork.Server`/`ServerManager`,
holding the actual `networkId <-> ModularSyncManager` bidirectional map and all the
open/close/reopen/sync logic. Every `@ApiStatus.Internal`-marked method here is called only from
`ModularNetwork`'s singletons or from the packet classes' `executeClient`/`executeServer`.

```java
public abstract class ModularNetworkSide {
    public abstract boolean isClient();
    abstract void sendPacket(IPacket packet, EntityPlayer player);
    abstract void closeContainer(EntityPlayer player);

    void activateInternal(int networkId, ModularSyncManager manager);
    public void onPlayerLeave(EntityPlayer player);
    public void closeAll(EntityPlayer player);
    @ApiStatus.Internal public void closeAll(EntityPlayer player, boolean sync);
    @ApiStatus.Internal public void receivePacket(EntityPlayer player, PacketSyncHandler packet);
    @ApiStatus.Internal public void sendSyncHandlerPacket(String panel, SyncHandler<?> syncHandler, PacketBuffer buffer, EntityPlayer player);
    @ApiStatus.Internal public void sendActionPacket(ModularSyncManager msm, String panel, String key, PacketBuffer buffer, EntityPlayer player);
    @ApiStatus.Internal public void closeContainer(int networkId, boolean dispose, EntityPlayer player, boolean sync);
    void deactivate(int networkId, boolean dispose);
    @ApiStatus.Internal public void reopen(EntityPlayer player, ModularSyncManager msm, boolean sync);
    @ApiStatus.Internal public void reopen(EntityPlayer player, int networkId, boolean sync);
}
```

| Method | Params | Returns | Purpose / gotchas |
|---|---|---|---|
| `isClient()` | — | `boolean` | Identifies which logical side this instance represents. |
| `sendPacket(packet, player)` (abstract, package-private) | — | `void` | Side-specific send strategy — implemented by `Client` (always `sendToServer`, ignoring `player`) and `Server` (`sendToPlayer`, requires an `EntityPlayerMP`). |
| `activateInternal(networkId, manager)` | int, `ModularSyncManager` | `void` | Inserts into both `activeScreens` (`Int2ReferenceOpenHashMap`) and `inverseActiveScreens` (`Reference2IntOpenHashMap`). **Throws `IllegalStateException`** if `networkId` is already present — network IDs must be unique per side/player at all times. |
| `onPlayerLeave(player)` | — | `void` | Clears **both** maps entirely (not per-player on this base class — per-player scoping is handled one level up by `ServerManager`, which additionally removes the player's whole `Server`). |
| `closeAll(player)` | — | `void` | `closeAll(player, true)`. |
| `closeAll(player, sync)` | boolean: send a `CloseAllGuiPacket` afterward | `void` | If `activeScreens` is empty, no-ops. Otherwise: if the player's *currently open* container is a non-client-only `ModularContainer`, remembers its network ID (`currentContainer`) so it can be closed client-side too; iterates every active `(networkId, msm)` entry via a fastutil `fastIterator`, closing the container if `nid == currentContainer`, calling `msm.onClose()` if not already closed, and `msm.dispose()` unconditionally; clears both maps; if `sync`, sends a `CloseAllGuiPacket`. |
| `receivePacket(player, packet)` | `PacketSyncHandler` | `void`, `@ApiStatus.Internal` | Looks up `activeScreens.get(packet.networkId)`; **silently discards** (returns) if no manager is registered for that ID (comment: *"silently discard packets for inactive screens"* — i.e. a race where a GUI closed just as a sync packet was in flight is not an error). Otherwise reads an extra `id` varint from `packet.packet` **only if `!packet.action`** (`packet.action ? 0 : packet.packet.readVarIntFromBuffer()`), then calls `msm.receiveWidgetUpdate(packet.panel, packet.key, packet.action, id, packet.packet)`. Catches `IndexOutOfBoundsException` (logs an error, malformed/short buffer) and `IOException` (logs via `LOGGER.throwing`) around this — a bad sync packet does not crash the connection. |
| `sendSyncHandlerPacket(panel, syncHandler, buffer, player)` | — | `void`, `@ApiStatus.Internal` | Resolves `syncHandler.getSyncManager().getModularSyncManager()`; if that manager isn't in `inverseActiveScreens` (GUI not currently active on this side), silently no-ops; otherwise looks up its network ID and calls `sendPacket(new PacketSyncHandler(id, panel, syncHandler.getKey(), false, buffer), player)` — `action = false` marks this as a **value-sync** packet (not an action). |
| `sendActionPacket(msm, panel, key, buffer, player)` | — | `void`, `@ApiStatus.Internal` | Same active-screen guard as above, then `sendPacket(new PacketSyncHandler(id, panel, key, true, buffer), player)` — `action = true` marks this as an **action** packet (see `PacketSyncHandler` section 6 for what that flag changes on the read side). |
| `closeContainer(networkId, dispose, player, sync)` | — | `void`, `@ApiStatus.Internal` | Calls the side-specific `closeContainer(player)`, then `deactivate(networkId, dispose)`, then if `sync`, sends a `CloseGuiPacket(networkId, dispose)` to the other side. |
| `deactivate(networkId, dispose)` | int, boolean | `void` | If no manager registered under `networkId`, no-ops. Otherwise calls `msm.onClose()` if not already closed; if `dispose` is true, additionally removes the manager from both maps and calls `msm.dispose()`. **So `dispose=false` keeps the manager tracked but marks it closed** — used when the close is expected to be followed by a reopen (see `reopen` below), avoiding a full teardown/rebuild. |
| `reopen(player, msm, sync)` | — | `void`, `@ApiStatus.Internal` | If `player.openContainer != msm.getContainer()`: closes the player's current container (`closeContainer(player)`), sets `player.openContainer = msm.getContainer()` directly, and calls `msm.onOpen()`. Comment: *"1.12.2 fire event here which doesn't exist in 1.7.10"* — a Forge container-open event that 1.12.2 ModularUI fires is intentionally **not** fired on this 1.7.10 port. If `sync`, sends a `ReopenGuiPacket(inverseActiveScreens.getInt(msm))` to the other side. |
| `reopen(player, networkId, sync)` | — | `void`, `@ApiStatus.Internal` | `reopen(player, activeScreens.get(networkId), sync)`. |

**Gotcha (concurrency):** `activeScreens`/`inverseActiveScreens` are plain (non-concurrent)
fastutil maps. All access happens on the network/game thread for the relevant logical side
(Netty decodes hand off to the main thread via Forge's packet dispatch), so this is safe under
vanilla Forge threading but would not be safe if called from an arbitrary thread.

---

## 5. `com.cleanroommc.modularui.network.NetworkUtils`

Static (de)serialization helpers used throughout `network.packets` (and elsewhere) for
`PacketBuffer` I/O beyond vanilla's built-ins, plus a couple of client/server-side predicates.

```java
public class NetworkUtils {
    public static final Consumer<PacketBuffer> EMPTY_PACKET = buffer -> {};
    public static final boolean DEDICATED_CLIENT = FMLCommonHandler.instance().getSide().isClient();

    public static boolean isClient();
    public static boolean isDedicatedClient();
    public static boolean isClient(EntityPlayer player);
    public static void writeByteBuf(PacketBuffer writeTo, ByteBuf writeFrom);
    public static ByteBuf readByteBuf(PacketBuffer buf);
    public static PacketBuffer readPacketBuffer(PacketBuffer buf);
    public static void writeItemStack(PacketBuffer buffer, ItemStack itemStack);
    public static ItemStack readItemStack(PacketBuffer buffer);
    public static void writeFluidStack(PacketBuffer buffer, @Nullable FluidStack fluidStack);
    @Nullable public static FluidStack readFluidStack(PacketBuffer buffer);
    public static void writeStringSafe(PacketBuffer buffer, String string);
    public static void writeStringSafe(PacketBuffer buffer, @Nullable String string, boolean crash);
    public static void writeStringSafe(PacketBuffer buffer, @Nullable String string, int maxBytes);
    public static void writeStringSafe(PacketBuffer buffer, @Nullable String string, int maxBytes, boolean crash);
    public static String readStringSafe(PacketBuffer buffer);
    public static void writeEnumValue(PacketBuffer buffer, Enum<?> value);
    public static <T extends Enum<T>> T readEnumValue(PacketBuffer buffer, Class<T> enumClass);
}
```

| Field/Method | Params | Returns | Notes |
|---|---|---|---|
| `EMPTY_PACKET` | — | `Consumer<PacketBuffer>` | A no-op consumer constant; used as a default/placeholder wherever a `PacketBuffer` writer callback is optional (not used within this package itself — utility for callers). |
| `DEDICATED_CLIENT` | — | `boolean` (static final, computed once at class load) | `FMLCommonHandler.instance().getSide().isClient()` — the **physical** side (is this JVM instance a client build at all), not the logical/effective side. |
| `isClient()` | — | `boolean` | `FMLCommonHandler.instance().getEffectiveSide().isClient()` — the **logical** side of the current thread/call (differs from `DEDICATED_CLIENT` on an integrated server's client thread). |
| `isDedicatedClient()` | — | `boolean` | Returns the precomputed `DEDICATED_CLIENT`. |
| `isClient(player)` | `EntityPlayer` (nullable) | `boolean` | If `player == null`, falls back to `isClient()`. Otherwise: `player.worldObj == null ? player instanceof EntityPlayerSP : player.worldObj.isRemote` — handles players whose world isn't set yet by falling back to an `instanceof` check. |

### Byte-buffer-in-buffer helpers

| Method | Params | Returns | Notes |
|---|---|---|---|
| `writeByteBuf(writeTo, writeFrom)` | dest `PacketBuffer`, source `ByteBuf` | `void` | Writes `writeFrom.readableBytes()` as a varint length prefix, then the raw bytes. Used to nest an already-serialized sub-packet/payload (e.g. `PacketSyncHandler`'s inner sync payload, `OpenGuiPacket`'s `GuiData`) inside an outer packet. |
| `readByteBuf(buf)` | source `PacketBuffer` | `ByteBuf` | Reads the varint length, slices that many bytes (`buf.readBytes(len)`), copies them into a fresh `Unpooled.copiedBuffer(...)`, releases the slice, and returns the copy. **Gotcha:** the returned buffer is a copy independent of `buf`'s lifecycle — safe to retain after `buf` itself is released/reused. |
| `readPacketBuffer(buf)` | source `PacketBuffer` | `PacketBuffer` | `new PacketBuffer(readByteBuf(buf))` — convenience wrapper. |

### Item/fluid stack helpers

| Method | Params | Returns | Notes |
|---|---|---|---|
| `writeItemStack(buffer, itemStack)` | — | `void` | Delegates to vanilla `buffer.writeItemStackToBuffer(itemStack)`; catches `IOException` and only logs it (`ModularUI.LOGGER.catching`) — **does not rethrow**, so a write failure here silently produces a malformed/short buffer rather than aborting the send. |
| `readItemStack(buffer)` | — | `ItemStack` (nullable) | Delegates to `buffer.readItemStackFromBuffer()`; on `IOException`, logs and returns `null` rather than throwing. |
| `writeFluidStack(buffer, fluidStack)` | nullable `FluidStack` | `void` | Writes a leading boolean (`true` = null marker); if non-null, serializes via `fluidStack.writeToNBT(...)` then `buffer.writeNBTTagCompoundToBuffer(...)` (IOException caught/logged, not rethrown). |
| `readFluidStack(buffer)` | — | `FluidStack` (nullable) | Reads the leading boolean; if `true`, returns `null` immediately. Otherwise `FluidStack.loadFluidStackFromNBT(buffer.readNBTTagCompoundFromBuffer())` (IOException caught/logged, returns `null` on failure). |

### String helpers

| Method | Params | Returns | Notes |
|---|---|---|---|
| `writeStringSafe(buffer, string)` | — | `void` | `writeStringSafe(buffer, string, Short.MAX_VALUE, false)`. |
| `writeStringSafe(buffer, string, boolean crash)` | — | `void` | `writeStringSafe(buffer, string, Short.MAX_VALUE, crash)`. |
| `writeStringSafe(buffer, string, int maxBytes)` | — | `void` | `writeStringSafe(buffer, string, maxBytes, false)`. |
| `writeStringSafe(buffer, string, int maxBytes, boolean crash)` | | `void` | Core implementation. Clamps `maxBytes` to at most `Short.MAX_VALUE`. `null` is encoded as the sentinel varint `Short.MAX_VALUE + 1`; empty string as varint `0`; otherwise UTF-8-encodes the string, and if it's longer than `maxBytes`, either **throws `IllegalArgumentException`** (if `crash == true`) or **silently truncates** to `maxBytes` bytes and logs a warning (`"Warning! Synced string exceeds max length!"`). Length-prefixes (varint) then writes the (possibly truncated) bytes. |
| `readStringSafe(buffer)` | — | `String` (nullable) | Reads the varint length; if `> Short.MAX_VALUE` (i.e. the null sentinel or corrupt data), returns `null`; if `0`, returns `StringUtils.EMPTY`; otherwise decodes `length` bytes as UTF-8 and advances `readerIndex` manually. **Gotcha:** pairs exactly with `writeStringSafe`'s sentinel/length scheme — do not use vanilla's own string read/write against a buffer written with this method or vice versa. |

### Enum helpers

| Method | Params | Returns | Notes |
|---|---|---|---|
| `writeEnumValue(buffer, value)` | any `Enum<?>` | `void` | Writes `value.ordinal()` as a varint. **Gotcha:** ordinal-based — reordering/inserting enum constants between versions breaks wire compatibility (no name-based fallback). |
| `readEnumValue(buffer, enumClass)` | `Class<T>` | `T` | Reads a varint index into `enumClass.getEnumConstants()` (unchecked cast). Throws `ArrayIndexOutOfBoundsException` if the buffer's ordinal doesn't exist in `enumClass` (e.g. version mismatch). |

---

## 6. `network.packets` — concrete packets

All classes below `implement IPacket` directly, are registered in `NetworkHandler.init()`, and
follow the same shape: a no-arg constructor for the codec, a data constructor for the sending
side, `write`/`read` in matching field order, and `executeClient`/`executeServer` performing the
actual effect via `ModularNetwork.CLIENT`/`ModularNetwork.SERVER` (or, for `SClipboard`, directly
via vanilla `GuiScreen.setClipboardString`).

| Packet | Purpose | Direction | Payload | Registration |
|---|---|---|---|---|
| `SClipboard` | Push a string to the client's system clipboard (e.g. server-triggered "copy to clipboard" for a synced widget). | S -> C | 1 safe string | `registerS2C` |
| `SyncConfig` | Push a `Config`'s serialized state from client to server (config sync on GUI open/change). | C -> S | safe string (config name) + nested `PacketBuffer` (config's own `writeToBuffer` output) | `registerC2S` |
| `OpenGuiPacket<T extends GuiData>` | Open a ModularUI GUI on the receiving side, in response to a factory-driven open request. | C <-> S (`registerBoth`) | 2 varints (`windowId`, `networkId`) + safe string (`factory.getFactoryName()`) + nested `PacketBuffer` (`GuiData`) | `registerBoth` |
| `ReopenGuiPacket` | Re-activate an already-registered network ID's GUI on the other side (see `ModularNetworkSide.reopen`). | C <-> S | 1 raw `int` (`networkId`, via `writeInt`/`readInt` — **not** varint, unlike most other packets here) | `registerBoth` |
| `CloseGuiPacket` | Close one active GUI/network ID, optionally disposing its `ModularSyncManager`. | C <-> S | 1 varint (`networkId`) + 1 boolean (`dispose`) | `registerBoth` |
| `CloseAllGuiPacket` | Close every active GUI on the receiving side for that player. | C <-> S | none (empty `write`/`read`) | `registerBoth` |
| `PacketSyncHandler` | Carries one synced-widget value update or action invocation between a `SyncHandler` and its counterpart. | C <-> S | varint `networkId` + 2 safe strings (`panel`, `key`, max 256 bytes, `crash=true`) + boolean `action` + nested `PacketBuffer` (`packet`, the handler-specific payload) | `registerBoth` |

`CloseAllGuiPacket`, `CloseGuiPacket`, `ReopenGuiPacket`, `OpenGuiPacket` and `SClipboard`'s
`executeClient` are all `@SideOnly(Side.CLIENT)` (as required by `IPacket`'s contract for that
method); every packet's `executeServer` runs unannotated on the server.

### `com.cleanroommc.modularui.network.packets.OpenGuiPacket<T extends GuiData>`

Worth calling out individually (not just the table) since it's the packet that actually opens a
GUI and touches the `UIFactory`/`GuiManager` API surface:

```java
public class OpenGuiPacket<T extends GuiData> implements IPacket {
    public OpenGuiPacket();
    public OpenGuiPacket(int windowId, int networkId, UIFactory<T> factory, PacketBuffer data);
    public void write(PacketBuffer buf) throws IOException;
    public void read(PacketBuffer buf);
    @SideOnly(Side.CLIENT) public @Nullable IPacket executeClient(NetHandlerPlayClient handler);
    public @Nullable IPacket executeServer(NetHandlerPlayServer handler);
}
```

| Method | Notes |
|---|---|
| `write` | Writes `windowId`, `networkId` (both varint), the factory's name (`NetworkUtils.writeStringSafe`), then the pre-serialized `data` buffer nested via `NetworkUtils.writeByteBuf`. |
| `read` | Reads in the same order; resolves the factory back from its name via `GuiManager.getFactory(...)` (unchecked cast to `UIFactory<T>`) and reconstitutes `data` via `NetworkUtils.readPacketBuffer`. **Gotcha:** `read` does not declare `throws IOException` in its signature even though `write` does — an asymmetry in the checked-exception signature (no functional issue since neither actually throws here beyond what the delegated helpers already catch). |
| `executeClient` | `GuiManager.openFromClient(windowId, networkId, factory, data, Platform.getClientPlayer())` — actually builds/opens the GUI client-side. |
| `executeServer` | `T guiData = factory.readGuiData(handler.playerEntity, data); GuiManager.open(factory, guiData, handler.playerEntity);` — server-side open path (e.g. a server-initiated GUI open, distinct from the more common client-initiated flow). |

**Gotcha:** the class is generic (`<T extends GuiData>`) but `read` performs an unchecked cast
(`(UIFactory<T>) GuiManager.getFactory(...)`) — type safety depends entirely on the sender and
receiver agreeing on which factory name maps to which `T`, which is guaranteed by
`GuiManager`'s registry, not by the packet itself.

### `com.cleanroommc.modularui.network.packets.PacketSyncHandler`

Treated fully here since it's the central sync transport for every synced widget in the library —
essentially every `SyncHandler` value change or action flows through this one packet type.

```java
@ApiStatus.Internal
public class PacketSyncHandler implements IPacket {
    public int networkId;
    public String panel;
    public String key;
    public boolean action;
    public PacketBuffer packet;

    public PacketSyncHandler();
    public PacketSyncHandler(int networkId, String panel, String key, boolean action, PacketBuffer packet);
    public void write(PacketBuffer buf);
    public void read(PacketBuffer buf);
    @SideOnly(Side.CLIENT) public @Nullable IPacket executeClient(NetHandlerPlayClient handler);
    public @Nullable IPacket executeServer(NetHandlerPlayServer handler);
}
```

| Field | Meaning |
|---|---|
| `networkId` | The active-GUI network ID this update belongs to (looked up in `ModularNetworkSide.activeScreens`). |
| `panel` | Name of the panel the target `SyncHandler` belongs to (see `ModularSyncManager.receiveWidgetUpdate(panelName, ...)`). |
| `key` | The `SyncHandler`'s own key within that panel. |
| `action` | `true` = this is an **action invocation** (e.g. a button press / triggered behavior); `false` = this is a **value sync** (state update). Constructed with `action=false` by `ModularNetworkSide.sendSyncHandlerPacket` and `action=true` by `sendActionPacket`. |
| `packet` | The nested, handler-specific payload buffer — opaque to `PacketSyncHandler` itself, interpreted only by the target `SyncHandler`. |

| Method | Notes |
|---|---|
| Constructors | No-arg for the codec; 5-arg for sending — all fields are `public` (no getters), unusual among the packets here but consistent with `@ApiStatus.Internal` (not meant for external construction anyway). |
| `write` | `networkId` (varint) -> `panel` and `key` as **safe strings capped at 256 bytes with `crash=true`** (i.e. a panel/key name over 256 UTF-8 bytes throws `IllegalArgumentException` rather than silently truncating — the only packet in this package using the crashing string variant) -> `action` (boolean) -> `packet` nested via `writeByteBuf`. |
| `read` | Same order, using `readStringSafe`/`readPacketBuffer`. |
| `executeClient` | `ModularNetwork.CLIENT.receivePacket(Minecraft.getMinecraft().thePlayer, this)`. |
| `executeServer` | `ModularNetwork.SERVER.receivePacket(handler.playerEntity, this)`. |

Both `executeClient`/`executeServer` funnel into `ModularNetworkSide.receivePacket` (documented in
section 4), which is where the `action` flag actually changes read behavior: for action packets
(`action == true`), no extra leading ID is read from `packet`; for value-sync packets
(`action == false`), an extra varint `id` is read first from `packet` before dispatching to
`msm.receiveWidgetUpdate(panel, key, action, id, packet)`. A malformed/short `packet` buffer is
caught there (`IndexOutOfBoundsException`/`IOException`, logged, connection not dropped) rather
than in this class.

**Gotcha (thread/side safety):** `receivePacket` silently discards updates for a `networkId` with
no registered `ModularSyncManager` — i.e. sync packets that arrive for a GUI that has just been
closed on the receiving side are expected and not an error condition; do not assume every sent
`PacketSyncHandler` is guaranteed to be applied.

**Example (constructed, not from repo)** — `PacketSyncHandler` is never constructed directly by
mod/library code outside `ModularNetworkSide`; it is emitted implicitly whenever a `SyncHandler`
calls its own sync/action-send method. Illustrative (not an actual call site):

```java
// inside a SyncHandler<?> subclass, conceptually:
PacketBuffer payload = new PacketBuffer(Unpooled.buffer());
payload.writeVarIntToBuffer(someValue);
getSyncManager().getModularSyncManager(); // ... eventually reaches:
ModularNetwork.get(false).sendSyncHandlerPacket(panelName, this, payload, player); // server -> client value sync
```
