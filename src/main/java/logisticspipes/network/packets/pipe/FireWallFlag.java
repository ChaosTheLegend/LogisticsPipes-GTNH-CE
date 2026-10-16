package logisticspipes.network.packets.pipe;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.network.abstractpackets.BitSetCoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.pipes.PipeItemsFirewall;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.routing.astar.LPJunctionNetwork;
import lombok.experimental.Accessors;

@Accessors(chain = true)
public class FireWallFlag extends BitSetCoordinatesPacket {

    public FireWallFlag(int id) {
        super(id);
    }

    @Override
    public ModernPacket template() {
        return new FireWallFlag(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        final LogisticsTileGenericPipe pipe = this.getPipe(player.worldObj);
        if (pipe == null) {
            return;
        }
        if (pipe.pipe instanceof PipeItemsFirewall) {
            PipeItemsFirewall firewall = (PipeItemsFirewall) pipe.pipe;
            boolean blockedPower = firewall.isBlockPower();
            firewall.setFlags(getFlags());
            if (blockedPower != firewall.isBlockPower() && MainProxy.isServer(player.worldObj)) {
                // the firewall's filter is read live, so its corridors don't change: rebuild the power pools
                LPJunctionNetwork.writer().refreshPowerPools();
            }
        }
    }
}
