package logisticspipes.interfaces;

import net.minecraftforge.common.util.ForgeDirection;

public interface ISlotUpgradeManager {

    boolean hasPatternUpgrade();

    boolean isAdvancedSatelliteCrafter();

    boolean hasInstantSatelliteUpgrade();

    boolean hasByproductExtractor();

    int getFluidCrafter();

    boolean isFuzzyUpgrade();

    int getCrafterCleanup();

    boolean hasSneakyUpgrade();

    ForgeDirection getSneakyOrientation();

    boolean hasOwnSneakyUpgrade();
}
