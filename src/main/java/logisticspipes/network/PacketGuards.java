package logisticspipes.network;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import logisticspipes.blocks.LogisticsSecurityTileEntity;
import logisticspipes.crafting.requesttable.RequestTableContainer;
import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.security.SecuritySettings;

/**
 * Server-side validation helpers for packet handlers.
 * <p>
 * LP packets are not bound to a direction, so a modified client can send any packet to the server, including ones that
 * are only meant to travel server-to-client. Handlers that mutate state must check who is allowed to trigger them.
 */
public final class PacketGuards {

    /** Same reach vanilla containers use (8 blocks). */
    private static final double MAX_INTERACT_DISTANCE_SQ = 64.0D;

    private PacketGuards() {}

    /**
     * Returns true when a server-to-client packet is being processed on the client, i.e. it was not forged by a client
     * and sent to the server.
     */
    public static boolean isOnClient(EntityPlayer player) {
        return player != null && MainProxy.isClient(player.worldObj);
    }

    /**
     * Returns true when the player is next to the pipe, in the same world, and the pipe's security station (if any)
     * allows them to open its GUI.
     * <p>
     * Use for packets sent from GUIs whose container is not bound to the pipe.
     */
    public static boolean canConfigurePipe(EntityPlayer player, CoreRoutedPipe pipe) {
        if (player == null || pipe == null
                || pipe.container == null
                || pipe.container.isInvalid()
                || pipe.getWorld() != player.worldObj) {
            return false;
        }
        if (player.getDistanceSq(pipe.getX() + 0.5D, pipe.getY() + 0.5D, pipe.getZ() + 0.5D)
                > MAX_INTERACT_DISTANCE_SQ) {
            return false;
        }
        LogisticsSecurityTileEntity station = SimpleServiceLocator.securityStationManager
                .getStation(pipe.getOriginalUpgradeManager().getSecurityID());
        if (station == null) {
            return true;
        }
        SecuritySettings settings = station.getSecuritySettingsForPlayer(player, true);
        return settings == null || settings.openGui;
    }

    /**
     * Returns the request table whose GUI the player currently has open, or null.
     * <p>
     * Request table packets resolve their table through this instead of trusting client-sent coordinates or dimensions.
     */
    public static RequestTablePipe getOpenRequestTable(EntityPlayer player) {
        if (player == null || !(player.openContainer instanceof RequestTableContainer container)) {
            return null;
        }
        RequestTablePipe table = container.getTable();
        if (table == null || table.container == null || table.container.isInvalid()) {
            return null;
        }
        return table;
    }

    /**
     * Returns true for server operators and for the owner of an integrated (single player / LAN host) server.
     */
    public static boolean isPrivileged(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) {
            return false;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return false;
        }
        if (server.isSinglePlayer() && player.getCommandSenderName().equals(server.getServerOwner())) {
            return true;
        }
        return server.getConfigurationManager().func_152596_g(player.getGameProfile());
    }
}
