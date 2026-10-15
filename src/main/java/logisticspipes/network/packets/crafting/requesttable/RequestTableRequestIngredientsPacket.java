package logisticspipes.network.packets.crafting.requesttable;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.network.PacketGuards;
import logisticspipes.network.abstractpackets.IntegerCoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;

/**
 * Requests the current fake crafting-grid ingredients for a variable number of crafts.
 */
public class RequestTableRequestIngredientsPacket extends IntegerCoordinatesPacket {

    public RequestTableRequestIngredientsPacket(int id) {
        super(id);
    }

    @Override
    public ModernPacket template() {
        return new RequestTableRequestIngredientsPacket(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        RequestTablePipe table = PacketGuards.getOpenRequestTable(player);
        if (table == null) {
            return;
        }
        table.requestCraftingIngredients(player, getInteger());
    }
}
