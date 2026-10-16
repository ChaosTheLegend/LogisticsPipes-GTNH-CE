package logisticspipes.network.packets.crafting.monitor;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.crafting.CraftingMonitorTileEntity;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.proxy.MainProxy;

public class CraftingMonitorRefreshPacket extends CoordinatesPacket {

    public CraftingMonitorRefreshPacket(int id) {
        super(id);
    }

    @Override
    public void processPacket(EntityPlayer player) {
        CraftingMonitorTileEntity tile = getTile(player.worldObj, CraftingMonitorTileEntity.class);
        CraftingMonitorContentPacket content = PacketHandler.getPacket(CraftingMonitorContentPacket.class)
                .setEntries(tile.getMonitorEntries());
        content.setTilePos(tile);
        MainProxy.sendPacketToPlayer(content, player);
    }

    @Override
    public ModernPacket template() {
        return new CraftingMonitorRefreshPacket(getId());
    }
}
