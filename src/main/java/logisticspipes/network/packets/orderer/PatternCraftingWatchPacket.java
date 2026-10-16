package logisticspipes.network.packets.orderer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.crafting.PatternCraftingMonitorNode;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.abstractpackets.IntegerCoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.pipes.PipeBlockRequestTable;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
public class PatternCraftingWatchPacket extends IntegerCoordinatesPacket {

    @Getter
    @Setter
    private List<PatternCraftingMonitorNode> roots = new ArrayList<>();

    public PatternCraftingWatchPacket(int id) {
        super(id);
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        super.writeData(data);
        data.writeInt(roots.size());
        for (PatternCraftingMonitorNode root : roots) {
            root.writeData(data);
        }
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        super.readData(data);
        roots = new ArrayList<>();
        int count = data.readInt();
        for (int i = 0; i < count; i++) {
            roots.add(PatternCraftingMonitorNode.readData(data));
        }
    }

    @Override
    public void processPacket(EntityPlayer player) {
        LogisticsTileGenericPipe tile = getPipe(player.worldObj);
        if (tile != null && tile.pipe instanceof PipeBlockRequestTable) {
            ((PipeBlockRequestTable) tile.pipe).handleClientSidePatternCraftingInfo(getInteger(), roots);
        }
    }

    @Override
    public ModernPacket template() {
        return new PatternCraftingWatchPacket(getId());
    }

    @Override
    public boolean isCompressable() {
        return true;
    }
}
