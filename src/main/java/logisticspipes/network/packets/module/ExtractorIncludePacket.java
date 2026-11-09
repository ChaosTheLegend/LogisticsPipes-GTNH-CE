package logisticspipes.network.packets.module;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.modules.ModuleExtractor;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.network.abstractpackets.ModuleCoordinatesPacket;
import logisticspipes.network.packets.modules.ExtractorInclude;
import logisticspipes.proxy.MainProxy;

public class ExtractorIncludePacket extends ModuleCoordinatesPacket {

    public ExtractorIncludePacket(int id) {
        super(id);
    }

    @Override
    public ModernPacket template() {
        return new ExtractorIncludePacket(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        final ModuleExtractor module = this.getLogisticsModule(player, ModuleExtractor.class);
        if (module == null) {
            return;
        }
        module.setItemsIncluded(!module.areItemsIncluded());
        MainProxy.sendPacketToPlayer(
                PacketHandler.getPacket(ExtractorInclude.class).setFlag(module.areItemsIncluded()).setPacketPos(this),
                player);
    }
}
