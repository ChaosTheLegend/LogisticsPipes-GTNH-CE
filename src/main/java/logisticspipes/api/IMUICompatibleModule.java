package logisticspipes.api;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import logisticspipes.compat.ModularUIHelper;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.modules.abstractmodules.LogisticsGuiModule;

public interface IMUICompatibleModule {

    default void openGui(EntityPlayer player, LogisticsGuiModule module, World world) {
        ModularUIHelper.openModuleUI(player, module, world);
    }

    public LogisticsModularUI getHandGui();

    public LogisticsModularUI getPipeGui();

    default LogisticsModularUI getPipeGui(String prefix) {
        return getPipeGui();
    }

}
