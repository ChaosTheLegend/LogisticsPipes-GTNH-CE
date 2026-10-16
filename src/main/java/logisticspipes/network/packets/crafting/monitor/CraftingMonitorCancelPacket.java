package logisticspipes.network.packets.crafting.monitor;

import java.io.IOException;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.crafting.CraftingMonitorTileEntity;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.proxy.MainProxy;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
public class CraftingMonitorCancelPacket extends CoordinatesPacket {

    @Setter
    private UUID instanceId;

    public CraftingMonitorCancelPacket(int id) {
        super(id);
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        super.writeData(data);
        data.writeLong(instanceId.getMostSignificantBits());
        data.writeLong(instanceId.getLeastSignificantBits());
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        super.readData(data);
        instanceId = new UUID(data.readLong(), data.readLong());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        CraftingMonitorTileEntity tile = getTile(player.worldObj, CraftingMonitorTileEntity.class);
        tile.cancel(instanceId);
        CraftingMonitorContentPacket content = PacketHandler.getPacket(CraftingMonitorContentPacket.class)
                .setEntries(tile.getMonitorEntries());
        content.setTilePos(tile);
        MainProxy.sendPacketToPlayer(content, player);
    }

    @Override
    public ModernPacket template() {
        return new CraftingMonitorCancelPacket(getId());
    }
}
