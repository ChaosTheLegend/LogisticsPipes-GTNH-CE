package logisticspipes.network.packets.crafting.requesttable;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.network.PacketGuards;
import logisticspipes.network.abstractpackets.IntegerCoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;

/**
 * Sends the selected internal storage back into the network when destinations exist.
 */
public class RequestTableSendStoragePacket extends IntegerCoordinatesPacket {

    public RequestTableSendStoragePacket(int id) {
        super(id);
    }

    @Override
    public ModernPacket template() {
        return new RequestTableSendStoragePacket(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        RequestTablePipe table = PacketGuards.getOpenRequestTable(player);
        if (table == null) {
            return;
        }
        if (getInteger() == 1) {
            table.sendStoredFluidsToNetwork();
        } else {
            table.sendStoredItemsToNetwork();
        }
    }
}
