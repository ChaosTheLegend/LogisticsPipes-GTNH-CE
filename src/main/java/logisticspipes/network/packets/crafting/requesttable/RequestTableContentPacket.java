package logisticspipes.network.packets.crafting.requesttable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;

import cpw.mods.fml.client.FMLClientHandler;
import logisticspipes.crafting.requesttable.RequestTableDisplaySettings;
import logisticspipes.crafting.requesttable.RequestTableGui;
import logisticspipes.crafting.requesttable.RequestTableNetworkEntry;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;

/**
 * Sends the combined item/fluid request list to the new request table GUI.
 */
public class RequestTableContentPacket extends CoordinatesPacket {

    private List<RequestTableNetworkEntry> entries = new ArrayList<>();
    private RequestTableDisplaySettings displaySettings = RequestTableDisplaySettings.DEFAULT;

    public RequestTableContentPacket(int id) {
        super(id);
    }

    /**
     * Sets the list payload.
     */
    public RequestTableContentPacket setEntries(List<RequestTableNetworkEntry> entries) {
        this.entries = entries;
        return this;
    }

    /**
     * Sets the receiving player's state for this particular request table.
     */
    public RequestTableContentPacket setDisplaySettings(RequestTableDisplaySettings displaySettings) {
        this.displaySettings = displaySettings;
        return this;
    }

    @Override
    public ModernPacket template() {
        return new RequestTableContentPacket(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        if (FMLClientHandler.instance().getClient().currentScreen instanceof RequestTableGui gui) {
            if (gui.isForTable(getPosX(), getPosY(), getPosZ())) {
                gui.handleNetworkContent(entries, displaySettings);
            }
        }
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        super.writeData(data);
        data.writeInt(entries.size());
        for (RequestTableNetworkEntry entry : entries) {
            data.writeBoolean(entry.isFluid());
            data.writeItemIdentifierStack(entry.getStack());
            data.writeInt(entry.getNetworkAmount());
            data.writeInt(entry.getInternalAmount());
            data.writeBoolean(entry.isCraftable());
        }
        data.writeEnum(displaySettings.getSortMode());
        data.writeEnum(displaySettings.getSortDirection());
        data.writeEnum(displaySettings.getFilterMode());
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        super.readData(data);
        int size = data.readInt();
        entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            boolean fluid = data.readBoolean();
            entries.add(
                    new RequestTableNetworkEntry(
                            data.readItemIdentifierStack(),
                            fluid,
                            data.readInt(),
                            data.readInt(),
                            data.readBoolean()));
        }
        displaySettings = RequestTableDisplaySettings.fromOrdinals(data.readInt(), data.readInt(), data.readInt());
    }
}
