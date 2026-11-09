package logisticspipes.network.packets.modules;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.modules.ModuleExtractor;
import logisticspipes.network.abstractpackets.BooleanModuleCoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;

public class ExtractorInclude extends BooleanModuleCoordinatesPacket {

    public ExtractorInclude(int id) {
        super(id);
    }

    @Override
    public ModernPacket template() {
        return new ExtractorInclude(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        ModuleExtractor recieiver = this.getLogisticsModule(player, ModuleExtractor.class);
        if (recieiver == null) {
            return;
        }
        recieiver.setItemsIncluded(isFlag());
    }
}
