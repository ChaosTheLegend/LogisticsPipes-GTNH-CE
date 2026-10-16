package logisticspipes.network.packets.orderer;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.network.abstractpackets.RequestPacket;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.request.RequestHandler;

public class RequestFluidComponentPacket extends RequestPacket {

    public RequestFluidComponentPacket(int id) {
        super(id);
    }

    @Override
    public ModernPacket template() {
        return new RequestFluidComponentPacket(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        final LogisticsTileGenericPipe pipe = MainProxy.proxy
                .getPipeInDimensionAt(getDimension(), getPosX(), getPosY(), getPosZ(), player);
        if (pipe == null || !(pipe.pipe instanceof CoreRoutedPipe) || !(pipe.pipe instanceof IRequestFluid)) {
            return;
        }
        RequestHandler.simulateFluid(player, getStack(), (CoreRoutedPipe) pipe.pipe, (IRequestFluid) pipe.pipe);
    }
}
