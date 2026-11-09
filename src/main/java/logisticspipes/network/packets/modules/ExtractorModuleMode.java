package logisticspipes.network.packets.modules;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;

import cpw.mods.fml.client.FMLClientHandler;
import logisticspipes.gui.modules.GuiExtractor;
import logisticspipes.modules.ModuleExtractor;
import logisticspipes.modules.abstractmodules.LogisticsSneakyDirectionModule;
import logisticspipes.network.abstractpackets.DirectionModuleCoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import lombok.experimental.Accessors;

/**
 * Client: the first face of a module's sneaky upgrade, for the HUD and the old side window. It only changes what they
 * show: the client's module keeps its own faces.
 */
@Accessors(chain = true)
public class ExtractorModuleMode extends DirectionModuleCoordinatesPacket {

    public ExtractorModuleMode(int id) {
        super(id);
    }

    @Override
    public ModernPacket template() {
        return new ExtractorModuleMode(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        LogisticsSneakyDirectionModule recieiver = this
                .getLogisticsModule(player, LogisticsSneakyDirectionModule.class);
        if (recieiver == null) {
            return;
        }
        if (recieiver instanceof ModuleExtractor extractor) {
            extractor.setShownDirection(getDirection());
        }
        GuiScreen screen = FMLClientHandler.instance().getClient().currentScreen;
        if (screen instanceof GuiExtractor gui && gui.isFor(recieiver)) {
            gui.setMode(getDirection());
        }
    }
}
