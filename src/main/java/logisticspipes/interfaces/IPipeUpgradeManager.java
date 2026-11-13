package logisticspipes.interfaces;

import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;

public interface IPipeUpgradeManager {

    int getSpeedUpgradeCount();

    boolean hasCraftingMonitoringUpgrade();

    boolean hasCombinedSneakyUpgrade();

    IItemHandlerModifiable getUpgradeInventory();
}
