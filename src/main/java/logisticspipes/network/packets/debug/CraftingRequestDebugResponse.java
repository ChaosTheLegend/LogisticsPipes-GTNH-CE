package logisticspipes.network.packets.debug;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.proxy.MainProxy;
import logisticspipes.request.debug.CraftingRequestDebugClient;

public class CraftingRequestDebugResponse extends ModernPacket {

    private String title = "";
    private String payload = "";

    public CraftingRequestDebugResponse(int id) {
        super(id);
    }

    public CraftingRequestDebugResponse setTitle(String title) {
        this.title = title == null ? "" : title;
        return this;
    }

    public CraftingRequestDebugResponse setPayload(String payload) {
        this.payload = payload == null ? "" : payload;
        return this;
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        title = readString(data);
        payload = readString(data);
    }

    @Override
    public void processPacket(EntityPlayer player) {
        if (player == null || !MainProxy.isClient(player.worldObj)) {
            return;
        }
        if (payload.isEmpty()) {
            CraftingRequestDebugClient.notifyUnavailable(player);
            return;
        }
        CraftingRequestDebugClient.openWindow(title, payload);
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        writeString(data, title);
        writeString(data, payload);
    }

    @Override
    public ModernPacket template() {
        return new CraftingRequestDebugResponse(getId());
    }

    @Override
    public boolean isCompressable() {
        return true;
    }

    private static String readString(LPDataInputStream data) throws IOException {
        int length = data.readInt();
        if (length <= 0) {
            return "";
        }
        byte[] bytes = new byte[length];
        data.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeString(LPDataOutputStream data, String text) throws IOException {
        byte[] bytes = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
        data.writeInt(bytes.length);
        data.write(bytes);
    }
}
