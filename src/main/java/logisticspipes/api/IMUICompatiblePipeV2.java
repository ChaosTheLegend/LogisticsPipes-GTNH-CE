package logisticspipes.api;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.compat.ModularUIHelper;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.pipes.basic.CoreUnroutedPipe;

public interface IMUICompatiblePipeV2 {

    default void openGui(EntityPlayer player, CoreUnroutedPipe pipe) {
        ModularUIHelper.openPipeUI(player, pipe);
    }

    LogisticsModularUI getPipeGui();
}
