package logisticspipes.network.guis.pipe;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;

import logisticspipes.gui.GuiChassiPipe;
import logisticspipes.network.abstractguis.GuiProvider;
import logisticspipes.network.abstractguis.ModuleCoordinatesGuiProvider;
import logisticspipes.pipes.PipeLogisticsChassi;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.utils.gui.DummyContainer;

public class ChassiGuiProvider extends ModuleCoordinatesGuiProvider {

    public ChassiGuiProvider(int id) {
        super(id);
    }

    @Override
    public Object getClientGui(EntityPlayer player) {
        LogisticsTileGenericPipe pipe = getPipe(player.getEntityWorld());
        if (pipe == null || !(pipe.pipe instanceof PipeLogisticsChassi)) {
            return null;
        }
        return new GuiChassiPipe(player, (PipeLogisticsChassi) pipe.pipe);
    }

    @Override
    public DummyContainer getContainer(EntityPlayer player) {
        LogisticsTileGenericPipe pipe = getPipe(player.getEntityWorld());
        if (pipe == null || !(pipe.pipe instanceof PipeLogisticsChassi)) {
            return null;
        }
        final PipeLogisticsChassi _chassiPipe = (PipeLogisticsChassi) pipe.pipe;
        IInventory _moduleInventory = _chassiPipe.getModuleInventory();
        DummyContainer dummy = new DummyContainer(player.inventory, _moduleInventory);
        if (_chassiPipe.getChassiSize() < 5) {
            dummy.addNormalSlotsForPlayerInventory(18, 97);
        } else {
            dummy.addNormalSlotsForPlayerInventory(18, 174);
        }
        if (_chassiPipe.getChassiSize() > 0) {
            dummy.addModuleSlot(0, _moduleInventory, 19, 9, _chassiPipe);
        }
        if (_chassiPipe.getChassiSize() > 1) {
            dummy.addModuleSlot(1, _moduleInventory, 19, 29, _chassiPipe);
        }
        if (_chassiPipe.getChassiSize() > 2) {
            dummy.addModuleSlot(2, _moduleInventory, 19, 49, _chassiPipe);
        }
        if (_chassiPipe.getChassiSize() > 3) {
            dummy.addModuleSlot(3, _moduleInventory, 19, 69, _chassiPipe);
        }
        if (_chassiPipe.getChassiSize() > 4) {
            dummy.addModuleSlot(4, _moduleInventory, 19, 89, _chassiPipe);
            dummy.addModuleSlot(5, _moduleInventory, 19, 109, _chassiPipe);
            dummy.addModuleSlot(6, _moduleInventory, 19, 129, _chassiPipe);
            dummy.addModuleSlot(7, _moduleInventory, 19, 149, _chassiPipe);
        }
        // module upgrades are in the chassis MUI only
        return dummy;
    }

    @Override
    public GuiProvider template() {
        return new ChassiGuiProvider(getId());
    }
}
