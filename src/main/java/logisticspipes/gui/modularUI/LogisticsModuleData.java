package logisticspipes.gui.modularUI;

import net.minecraft.entity.player.EntityPlayer;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.factory.inventory.InventoryType;

import logisticspipes.modules.abstractmodules.LogisticsModule;

public class LogisticsModuleData extends PlayerInventoryGuiData {

    public final LogisticsModule module;

    public LogisticsModuleData(@NotNull EntityPlayer player, @NotNull InventoryType inventoryType, int slotIndex,
            @NotNull LogisticsModule module) {
        super(player, inventoryType, slotIndex);
        this.module = module;
    }
}
