package logisticspipes.network.packets.orderer;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.crafting.PatternCraftingHudState;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;

public class PatternCraftingHudContent extends CoordinatesPacket {

    private PatternCraftingHudState state = PatternCraftingHudState.empty();

    public PatternCraftingHudContent(int id) {
        super(id);
    }

    public PatternCraftingHudContent setState(PatternCraftingHudState state) {
        this.state = state == null ? PatternCraftingHudState.empty() : state;
        return this;
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        super.writeData(data);
        state.writeData(data);
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        super.readData(data);
        state = PatternCraftingHudState.readData(data);
    }

    @Override
    public ModernPacket template() {
        return new PatternCraftingHudContent(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        final LogisticsTileGenericPipe tile = this.getPipe(player.worldObj);
        if (tile == null || !(tile.pipe instanceof PipeItemsPatternCraftingLogistics)) {
            return;
        }
        ((PipeItemsPatternCraftingLogistics) tile.pipe).setHudState(state);
    }
}
