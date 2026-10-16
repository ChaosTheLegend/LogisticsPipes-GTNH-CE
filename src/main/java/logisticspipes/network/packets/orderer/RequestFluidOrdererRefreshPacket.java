package logisticspipes.network.packets.orderer;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.network.abstractpackets.Integer2CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.request.RequestHandler;

public class RequestFluidOrdererRefreshPacket extends Integer2CoordinatesPacket {

    public RequestFluidOrdererRefreshPacket(int id) {
        super(id);
    }

    @Override
    public ModernPacket template() {
        return new RequestFluidOrdererRefreshPacket(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        int dimension = getInteger();
        final LogisticsTileGenericPipe pipe = MainProxy.proxy
                .getPipeInDimensionAt(dimension, getPosX(), getPosY(), getPosZ(), player);
        if (pipe == null || !(pipe.pipe instanceof CoreRoutedPipe)) {
            return;
        }
        RequestHandler.DisplayOptions option;
        switch (getInteger2()) {
            case 1:
                option = RequestHandler.DisplayOptions.SupplyOnly;
                break;
            case 2:
                option = RequestHandler.DisplayOptions.CraftOnly;
                break;
            case 0:
            default:
                option = RequestHandler.DisplayOptions.Both;
                break;
        }
        RequestHandler.refreshFluid(player, (CoreRoutedPipe) pipe.pipe, option);
    }
}
