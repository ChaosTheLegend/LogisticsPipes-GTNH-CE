package logisticspipes.interfaces;

import net.minecraftforge.common.util.ForgeDirection;

public interface ISlotUpgradeManager {

    boolean hasPatternUpgrade();

    /**
     * @return true if a priority upgrade is in these slots: the module's priority works
     */
    boolean hasPriorityUpgrade();

    boolean isAdvancedSatelliteCrafter();

    boolean hasInstantSatelliteUpgrade();

    boolean hasByproductExtractor();

    int getFluidCrafter();

    boolean isFuzzyUpgrade();

    int getCrafterCleanup();

    /**
     * @return true if a sneaky upgrade with at least one face works here
     */
    boolean hasSneakyUpgrade();

    /**
     * @return the first face of the sneaky upgrade, UNKNOWN without one
     */
    ForgeDirection getSneakyOrientation();

    /**
     * @return the faces of the sneaky upgrade in order (try the first, then the next), empty without one
     */
    ForgeDirection[] getSneakyOrientations();

    /**
     * @return true if a sneaky upgrade is in these slots (its faces show in the settings window)
     */
    boolean hasSneakyItem();

    /**
     * @return the faces set for these slots, also while no sneaky upgrade is in them
     */
    ForgeDirection[] getSneakyFaces();

    void setSneakyFaces(ForgeDirection[] faces);
}
