package logisticspipes.network.packets.pipe;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.pipes.PipeItemsCraftingLogistics;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
public class CraftingBlockingModePacket extends CoordinatesPacket {

    public CraftingBlockingModePacket(int id) {
        super(id);
    }

    @Getter
    @Setter
    private boolean blockingMode;

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        super.writeData(data);
        data.writeBoolean(this.blockingMode);
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        super.readData(data);
        this.blockingMode = data.readBoolean();
    }

    @Override
    public void processPacket(EntityPlayer player) {
        var pipe = this.getPipe(player.worldObj);
        if (pipe == null) return;

        if (pipe.pipe instanceof PipeItemsCraftingLogistics p) {
            p.setBlockingMode(blockingMode);
        }
    }

    @Override
    public ModernPacket template() {
        return new CraftingBlockingModePacket(getId());
    }
}
