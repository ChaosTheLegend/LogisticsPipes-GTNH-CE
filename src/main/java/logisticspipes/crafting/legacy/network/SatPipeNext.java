package logisticspipes.crafting.legacy.network;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.crafting.PipeFluidPatternSatelliteLogistics;
import logisticspipes.crafting.PipeItemsPatternSatelliteLogistics;
import logisticspipes.crafting.legacy.satellite.PipeFluidSatellite;
import logisticspipes.crafting.legacy.satellite.PipeItemsSatelliteLogistics;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;

public class SatPipeNext extends CoordinatesPacket {

    public SatPipeNext(int id) {
        super(id);
    }

    @Override
    public ModernPacket template() {
        return new SatPipeNext(getId());
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
            ((PipeItemsSatelliteLogistics) pipe.pipe).setNextId(player);
        }
        if (pipe.pipe instanceof PipeFluidSatellite) {
            ((PipeFluidSatellite) pipe.pipe).setNextId(player);
        }
    }
}
