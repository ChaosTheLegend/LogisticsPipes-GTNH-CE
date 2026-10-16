package logisticspipes.network.packets.satpipe;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.crafting.PipeFluidPatternSatelliteLogistics;
import logisticspipes.crafting.PipeItemsPatternSatelliteLogistics;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.pipes.PipeFluidSatellite;
import logisticspipes.pipes.PipeItemsSatelliteLogistics;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;

public class SatPipePrev extends CoordinatesPacket {

    public SatPipePrev(int id) {
        super(id);
    }

    @Override
    public ModernPacket template() {
        return new SatPipePrev(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        final LogisticsTileGenericPipe pipe = getPipe(player.worldObj);
        if (pipe == null) {
            return;
        }

        if (pipe.pipe instanceof PipeItemsPatternSatelliteLogistics
                || pipe.pipe instanceof PipeFluidPatternSatelliteLogistics) {
            return;
        }

        if (pipe.pipe instanceof PipeItemsSatelliteLogistics) {
            ((PipeItemsSatelliteLogistics) pipe.pipe).setPrevId(player);
        }
        if (pipe.pipe instanceof PipeFluidSatellite) {
            ((PipeFluidSatellite) pipe.pipe).setPrevId(player);
        }
    }
}
