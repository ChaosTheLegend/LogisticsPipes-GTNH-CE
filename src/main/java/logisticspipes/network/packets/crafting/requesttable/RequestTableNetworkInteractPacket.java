package logisticspipes.network.packets.crafting.requesttable;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.PacketGuards;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.network.abstractpackets.RequestPacket;

/**
 * Applies a normal click on a network-grid entry to the request table's internal storage.
 */
public class RequestTableNetworkInteractPacket extends RequestPacket {

    private boolean fluid;
    private int mouseButton;
    private boolean shift;

    public RequestTableNetworkInteractPacket(int id) {
        super(id);
    }

    /**
     * Marks whether the clicked entry represents a fluid.
     */
    public RequestTableNetworkInteractPacket setFluid(boolean fluid) {
        this.fluid = fluid;
        return this;
    }

    /**
     * Sets the clicked mouse button.
     */
    public RequestTableNetworkInteractPacket setMouseButton(int mouseButton) {
        this.mouseButton = mouseButton;
        return this;
    }

    /**
     * Marks whether shift was held during the click.
     */
    public RequestTableNetworkInteractPacket setShift(boolean shift) {
        this.shift = shift;
        return this;
    }

    @Override
    public ModernPacket template() {
        return new RequestTableNetworkInteractPacket(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        RequestTablePipe table = PacketGuards.getOpenRequestTable(player);
        if (table == null) {
            return;
        }
        table.handleNetworkEntryInteraction(player, getStack(), fluid, mouseButton, shift);
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        super.writeData(data);
        data.writeBoolean(fluid);
        data.writeInt(mouseButton);
        data.writeBoolean(shift);
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        super.readData(data);
        fluid = data.readBoolean();
        mouseButton = data.readInt();
        shift = data.readBoolean();
    }
}
