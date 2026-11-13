package logisticspipes.network.packets.module;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.modules.ModuleExtractor;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.modules.abstractmodules.LogisticsModule.ModulePositionType;
import logisticspipes.network.NewGuiHandler;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.network.abstractpackets.ModuleCoordinatesPacket;
import logisticspipes.network.guis.module.inhand.ExtractorModuleInHand;
import logisticspipes.network.guis.module.inpipe.ExtractorModuleSlot;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.utils.gui.DummyModuleContainer;

public class ExtractorSneakyGuiPacket extends ModuleCoordinatesPacket {

    public ExtractorSneakyGuiPacket(int id) {
        super(id);
    }

    @Override
    public ModernPacket template() {
        return new ExtractorSneakyGuiPacket(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        if (getType() == ModulePositionType.IN_HAND) {
            if (player.openContainer instanceof DummyModuleContainer) {
                DummyModuleContainer dummy = (DummyModuleContainer) player.openContainer;
                if (dummy.getModule() instanceof ModuleExtractor) {
                    player.closeScreen();
                    NewGuiHandler.getGui(ExtractorModuleInHand.class).setInvSlot(getPositionInt()).open(player);
                }
            }
            return;
        }
        final LogisticsTileGenericPipe pipe = this.getPipe(player.worldObj);
        if (pipe == null) {
            return;
        }
        if (!(pipe.pipe instanceof CoreRoutedPipe)) {
            return;
        }
        final CoreRoutedPipe piperouted = (CoreRoutedPipe) pipe.pipe;
        LogisticsModule pipeModule = piperouted.getLogisticsModule();
        if (pipeModule == null) {
            return;
        }
        // a chassis holds the module in a slot; the extractor pipe is the module itself
        LogisticsModule slotModule = pipeModule.getSubModule(getPositionInt());
        if (slotModule == null && getType() == ModulePositionType.IN_PIPE) {
            slotModule = pipeModule;
        }
        if (slotModule instanceof ModuleExtractor module) {
            NewGuiHandler.getGui(ExtractorModuleSlot.class).setSneakyOrientation(module.getSneakyDirection())
                    .setSlot(getType()).setPositionInt(getPositionInt()).setPosX(getPosX()).setPosY(getPosY())
                    .setPosZ(getPosZ()).open(player);
        }
    }
}
