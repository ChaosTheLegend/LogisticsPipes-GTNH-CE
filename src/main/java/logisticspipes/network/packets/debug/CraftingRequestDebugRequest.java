package logisticspipes.network.packets.debug;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.PacketGuards;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.proxy.MainProxy;
import logisticspipes.request.debug.CraftingRequestDebugManager;

public class CraftingRequestDebugRequest extends ModernPacket {

    public boolean clearInfo;

    public CraftingRequestDebugRequest(int id) {
        super(id);
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        clearInfo = data.readBoolean();
    }

    @Override
    public void processPacket(EntityPlayer player) {
        if (player == null || !MainProxy.isServer(player.worldObj)) {
            return;
        }
        // The log holds every player's requests; only operators may read or clear it.
        if (!PacketGuards.isPrivileged(player)) {
            MainProxy.sendPacketToPlayer(
                    PacketHandler.getPacket(CraftingRequestDebugResponse.class).setTitle("Crafting Request Debug"),
                    player);
            return;
        }
        if (clearInfo) CraftingRequestDebugManager.clear();

        MainProxy.sendPacketToPlayer(
                PacketHandler.getPacket(CraftingRequestDebugResponse.class).setTitle("Crafting Request Debug")
                        .setPayload(CraftingRequestDebugManager.buildSnapshot()),
                player);
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        data.writeBoolean(clearInfo);
    }

    @Override
    public ModernPacket template() {
        return new CraftingRequestDebugRequest(getId());
    }

    @Override
    public boolean isCompressable() {
        return true;
    }
}
