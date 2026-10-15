package logisticspipes.network.packets.block;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.blocks.powertile.LogisticsPowerJunctionTileEntity;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.abstractpackets.IntegerCoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/** The stored energy (the integer) and capacity of a power junction's pool, for its GUI and HUD. */
@Accessors(chain = true)
public class PowerJunctionLevel extends IntegerCoordinatesPacket {

    @Getter
    @Setter
    private int capacity;

    public PowerJunctionLevel(int id) {
        super(id);
    }

    @Override
    public ModernPacket template() {
        return new PowerJunctionLevel(getId());
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        super.readData(data);
        capacity = data.readInt();
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        super.writeData(data);
        data.writeInt(capacity);
    }

    @Override
    public void processPacket(EntityPlayer player) {
        LogisticsPowerJunctionTileEntity tile = this.getTile(player.worldObj, LogisticsPowerJunctionTileEntity.class);
        if (tile != null) {
            tile.handlePowerPacket(getInteger(), capacity);
        }
    }
}
