package logisticspipes.network.packets.crafting.requesttable;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;

import cpw.mods.fml.client.FMLClientHandler;
import logisticspipes.crafting.monitor.CraftingMonitorData;
import logisticspipes.crafting.requesttable.gui.RequestTableGui;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.PacketGuards;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;

/** Bounded chunks; the client applies a complete transfer atomically. */
public class RequestTableMonitorUpdatePacket extends CoordinatesPacket {

    private long session;
    private long transfer;
    private int part;
    private int parts;
    private byte[] payload = new byte[0];

    public RequestTableMonitorUpdatePacket(int id) {
        super(id);
    }

    public RequestTableMonitorUpdatePacket setChunk(long session, long transfer, int part, int parts, byte[] payload) {
        this.session = session;
        this.transfer = transfer;
        this.part = part;
        this.parts = parts;
        this.payload = payload;
        return this;
    }

    @Override
    public ModernPacket template() {
        return new RequestTableMonitorUpdatePacket(getId());
    }

    @Override
    public boolean isCompressable() {
        return true;
    }

    @Override
    public void processPacket(EntityPlayer player) {
        if (!PacketGuards.isOnClient(player)) return;
        if (FMLClientHandler.instance().getClient().currentScreen instanceof RequestTableGui gui
            && gui.isForTable(getPosX(), getPosY(), getPosZ())) {
            gui.handleMonitorChunk(session, transfer, part, parts, payload);
        }
    }

    @Override
    public void writeData(LPDataOutputStream output) throws IOException {
        super.writeData(output);
        output.writeLong(session);
        output.writeLong(transfer);
        output.writeInt(part);
        output.writeInt(parts);
        output.writeInt(payload.length);
        output.write(payload);
    }

    @Override
    public void readData(LPDataInputStream input) throws IOException {
        super.readData(input);
        session = input.readLong();
        transfer = input.readLong();
        part = input.readInt();
        parts = input.readInt();
        int length = input.readInt();
        if (parts < 1 || parts > CraftingMonitorData.MAX_BYTES / CraftingMonitorData.CHUNK_BYTES
            || part < 0
            || part >= parts
            || length < 0
            || length > CraftingMonitorData.CHUNK_BYTES) {
            throw new IOException("Invalid crafting monitor chunk");
        }
        payload = new byte[length];
        input.readFully(payload);
    }
}
