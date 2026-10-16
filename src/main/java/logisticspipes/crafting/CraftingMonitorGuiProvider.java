package logisticspipes.crafting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.abstractguis.CoordinatesGuiProvider;
import logisticspipes.network.abstractguis.GuiProvider;
import logisticspipes.utils.gui.DummyContainer;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
public class CraftingMonitorGuiProvider extends CoordinatesGuiProvider {

    @Getter
    @Setter
    private List<PatternCraftingMonitorEntry> entries = new ArrayList<>();

    public CraftingMonitorGuiProvider(int id) {
        super(id);
    }

    @Override
    public Object getClientGui(EntityPlayer player) {
        CraftingMonitorTileEntity tile = getTile(player.getEntityWorld(), CraftingMonitorTileEntity.class);
        if (tile == null) {
            return null;
        }
        CraftingMonitorGui gui = new CraftingMonitorGui(tile, entries);
        gui.inventorySlots = new DummyContainer(player.inventory, null);
        return gui;
    }

    @Override
    public Container getContainer(EntityPlayer player) {
        CraftingMonitorTileEntity tile = getTile(player.getEntityWorld(), CraftingMonitorTileEntity.class);
        return tile == null ? null : new DummyContainer(player, null);
    }

    @Override
    public GuiProvider template() {
        return new CraftingMonitorGuiProvider(getId());
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
}
