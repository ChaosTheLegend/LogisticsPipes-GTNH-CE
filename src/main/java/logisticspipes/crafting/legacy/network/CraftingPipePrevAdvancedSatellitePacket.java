package logisticspipes.crafting.legacy.network;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.crafting.legacy.module.ModuleCrafter;
import logisticspipes.network.abstractpackets.IntegerModuleCoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;

public class CraftingPipePrevAdvancedSatellitePacket extends IntegerModuleCoordinatesPacket {

    public CraftingPipePrevAdvancedSatellitePacket(int id) {
        super(id);
    }

    @Override
    public ModernPacket template() {
        return new CraftingPipePrevAdvancedSatellitePacket(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        ModuleCrafter module = this.getLogisticsModule(player, ModuleCrafter.class);
        if (module == null) {
            return;
        }
        module.setPrevSatellite(player, getInteger());
    }
}
