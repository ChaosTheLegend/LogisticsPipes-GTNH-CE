package logisticspipes.network.packets.crafting.requesttable;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.PacketGuards;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.network.abstractpackets.RequestPacket;
import logisticspipes.request.RequestHandler;

/**
 * Submits an item or fluid request from the redesigned request table overlay.
 */
public class RequestTableSubmitPacket extends RequestPacket {

    private boolean fluid;

    public RequestTableSubmitPacket(int id) {
        super(id);
    }

    /**
     * Marks whether the request should use the fluid path.
     */
    public RequestTableSubmitPacket setFluid(boolean fluid) {
        this.fluid = fluid;
        return this;
    }

    @Override
    public ModernPacket template() {
        return new RequestTableSubmitPacket(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        RequestTablePipe table = PacketGuards.getOpenRequestTable(player);
        if (table == null) {
            return;
        }
        if (fluid) {
            RequestHandler.requestFluid(player, getStack(), table, table);
        } else {
            RequestHandler.request(player, getStack(), table);
        }
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        super.writeData(data);
        data.writeBoolean(fluid);
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        super.readData(data);
        fluid = data.readBoolean();
    }
}
