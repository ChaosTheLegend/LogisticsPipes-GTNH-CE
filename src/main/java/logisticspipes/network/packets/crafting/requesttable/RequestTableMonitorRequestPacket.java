package logisticspipes.network.packets.crafting.requesttable;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.crafting.monitor.CraftingMonitorService;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.PacketGuards;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;

/** Subscribes, selects an observed job, or unsubscribes. Never mutates crafting state. */
public class RequestTableMonitorRequestPacket extends CoordinatesPacket {

    private long session;
    private long job = -1;
    private int window;
    private int action;

    public RequestTableMonitorRequestPacket(int id) {
        super(id);
    }

    public RequestTableMonitorRequestPacket setRequest(long session, int window, int action, long job) {
        this.session = session;
        this.window = window;
        this.action = action;
        this.job = job;
        return this;
    }

    @Override
    public ModernPacket template() {
        return new RequestTableMonitorRequestPacket(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        if (PacketGuards.isOnClient(player)) return;
        var table = PacketGuards.getOpenRequestTable(player);
        if (table == null || table.getX() != getPosX() || table.getY() != getPosY() || table.getZ() != getPosZ())
            return;
        CraftingMonitorService.INSTANCE.request(player, table, window, session, action, job);
    }

    @Override
    public void writeData(LPDataOutputStream output) throws IOException {
        super.writeData(output);
        output.writeLong(session);
        output.writeLong(job);
        output.writeInt(window);
        output.writeInt(action);
    }

    @Override
    public void readData(LPDataInputStream input) throws IOException {
        super.readData(input);
        session = input.readLong();
        job = input.readLong();
        window = input.readInt();
        action = input.readInt();
    }
}
