package logisticspipes.crafting;

import logisticspipes.interfaces.ISlotUpgradeManager;

/** Caches the crafting-relevant upgrade flags once per world tick. */
final class PatternCraftingUpgradeCache {

    private final ModulePatternCrafting module;
    private long cachedTick = Long.MIN_VALUE;
    private boolean fluidCrafting;
    private boolean advancedSatellite;
    private boolean instantSatellite;

    PatternCraftingUpgradeCache(ModulePatternCrafting module) {
        this.module = module;
    }

    boolean supportsFluidCrafting() {
        refresh();
        return fluidCrafting;
    }

    boolean hasAdvancedSatellite() {
        refresh();
        return advancedSatellite;
    }

    boolean hasInstantSatellite() {
        refresh();
        return instantSatellite;
    }

    private void refresh() {
        long tick = module.currentWorldTick();
        if (cachedTick == tick) {
            return;
        }
        cachedTick = tick;
        ISlotUpgradeManager upgradeManager = module.getUpgradeManager();
        fluidCrafting = upgradeManager != null && upgradeManager.getFluidCrafter() > 0;
        advancedSatellite = upgradeManager != null && upgradeManager.isAdvancedSatelliteCrafter();
        instantSatellite = upgradeManager != null && upgradeManager.hasInstantSatelliteUpgrade();
    }
}
