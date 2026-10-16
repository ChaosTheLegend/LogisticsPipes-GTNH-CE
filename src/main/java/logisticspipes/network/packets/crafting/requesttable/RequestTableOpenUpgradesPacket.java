package logisticspipes.network.packets.crafting.requesttable;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.LogisticsPipes;
import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.crafting.requesttable.upgrade.RequestTableUpgradeContainer;
import logisticspipes.network.GuiIDs;
import logisticspipes.network.PacketGuards;
import logisticspipes.network.abstractpackets.IntegerCoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;

/** Opens the item upgrade tree, or returns from it to the normal request-table screen. */
public class RequestTableOpenUpgradesPacket extends IntegerCoordinatesPacket {

    public RequestTableOpenUpgradesPacket(int id) {
        super(id);
    }

    @Override
    public ModernPacket template() {
        return new RequestTableOpenUpgradesPacket(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        if (PacketGuards.isOnClient(player) || getInteger() < 0 || getInteger() > 1) return;
        RequestTablePipe table = getInteger() == 0 ? PacketGuards.getOpenRequestTable(player)
                : player.openContainer instanceof RequestTableUpgradeContainer upgrades ? upgrades.getTable() : null;
        if (table == null || !PacketGuards.canConfigurePipe(player, table)) return;
        int gui = getInteger() == 0 ? GuiIDs.GUI_Request_Table_Upgrades_ID : GuiIDs.GUI_New_Request_Table_ID;
        player.openGui(LogisticsPipes.instance, gui, player.worldObj, table.getX(), table.getY(), table.getZ());
    }
}
