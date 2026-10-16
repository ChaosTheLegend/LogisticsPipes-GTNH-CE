package logisticspipes.network.packets.crafting.requesttable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.crafting.requesttable.RequestTableContainer;
import logisticspipes.crafting.requesttable.RequestTableView;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.PacketGuards;
import logisticspipes.network.abstractpackets.ModernPacket;

/** Carries a bounded batch of player inventory slots and the active view, independent of storage slot indexes. */
public class RequestTableShiftClickPacket extends ModernPacket {

    private static final int MAX_SLOTS = 36;
    private int windowId;
    private List<Integer> inventorySlots = new ArrayList<>();
    private RequestTableView view = RequestTableView.NETWORK;

    public RequestTableShiftClickPacket(int id) {
        super(id);
    }

    public RequestTableShiftClickPacket setClicks(int windowId, List<Integer> inventorySlots, RequestTableView view) {
        if (inventorySlots.isEmpty() || inventorySlots.size() > MAX_SLOTS) {
            throw new IllegalArgumentException("Invalid player shift-click batch size.");
        }
        this.windowId = windowId;
        this.inventorySlots = new ArrayList<>(inventorySlots);
        this.view = view;
        return this;
    }

    @Override
    public ModernPacket template() {
        return new RequestTableShiftClickPacket(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        if (PacketGuards.isOnClient(player) || PacketGuards.getOpenRequestTable(player) == null
                || !(player.openContainer instanceof RequestTableContainer container)
                || container.windowId != windowId) {
            return;
        }
        container.shiftPlayerStacks(player, inventorySlots, view);
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        data.writeInt(windowId);
        data.writeByte(inventorySlots.size());
        for (int slot : inventorySlots) data.writeByte(slot);
        data.writeEnum(view);
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        windowId = data.readInt();
        int count = data.readUnsignedByte();
        if (count == 0 || count > MAX_SLOTS) throw new IOException("Invalid player shift-click batch size.");
        inventorySlots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) inventorySlots.add(data.readUnsignedByte());
        view = data.readEnum(RequestTableView.class);
    }
}
