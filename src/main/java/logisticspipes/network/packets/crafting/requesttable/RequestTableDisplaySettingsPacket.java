package logisticspipes.network.packets.crafting.requesttable;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.crafting.requesttable.RequestTableDisplaySettings;
import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;

/**
 * Saves a player's sort and filter state at one redesigned request table.
 */
public class RequestTableDisplaySettingsPacket extends CoordinatesPacket {

    private RequestTableDisplaySettings settings = RequestTableDisplaySettings.DEFAULT;

    public RequestTableDisplaySettingsPacket(int id) {
        super(id);
    }

    public RequestTableDisplaySettingsPacket setSettings(RequestTableDisplaySettings settings) {
        this.settings = settings;
        return this;
    }

    @Override
    public ModernPacket template() {
        return new RequestTableDisplaySettingsPacket(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        LogisticsTileGenericPipe tile = getPipe(player.worldObj);
        if (tile == null || !(tile.pipe instanceof RequestTablePipe)) {
            return;
        }
        ((RequestTablePipe) tile.pipe).setDisplaySettings(player, settings);
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        super.writeData(data);
        data.writeEnum(settings.getSortMode());
        data.writeEnum(settings.getSortDirection());
        data.writeEnum(settings.getFilterMode());
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        super.readData(data);
        settings = RequestTableDisplaySettings.fromOrdinals(data.readInt(), data.readInt(), data.readInt());
    }
}
