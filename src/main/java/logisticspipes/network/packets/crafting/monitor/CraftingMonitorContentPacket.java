package logisticspipes.network.packets.crafting.monitor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;

import cpw.mods.fml.client.FMLClientHandler;
import logisticspipes.crafting.CraftingMonitorGui;
import logisticspipes.crafting.CraftingMonitorTileEntity;
import logisticspipes.crafting.PatternCraftingMonitorEntry;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
public class CraftingMonitorContentPacket extends CoordinatesPacket {

    @Getter
    @Setter
    private List<PatternCraftingMonitorEntry> entries = new ArrayList<>();

    public CraftingMonitorContentPacket(int id) {
        super(id);
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        super.writeData(data);
        data.writeInt(entries.size());
        for (PatternCraftingMonitorEntry entry : entries) {
            entry.writeData(data);
        }
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        super.readData(data);
        int count = data.readInt();
        entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(PatternCraftingMonitorEntry.readData(data));
        }
    }

    @Override
    public void processPacket(EntityPlayer player) {
        CraftingMonitorTileEntity tile = getTile(player.worldObj, CraftingMonitorTileEntity.class);
        if (FMLClientHandler.instance().getClient().currentScreen instanceof CraftingMonitorGui gui
                && gui.isFor(tile)) {
            gui.handleContent(entries);
        }
    }

    @Override
    public ModernPacket template() {
        return new CraftingMonitorContentPacket(getId());
    }

    @Override
    public boolean isCompressable() {
        return true;
    }
}
