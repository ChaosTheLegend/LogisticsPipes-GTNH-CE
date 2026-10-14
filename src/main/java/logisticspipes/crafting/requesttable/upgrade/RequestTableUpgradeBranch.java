package logisticspipes.crafting.requesttable.upgrade;

import lombok.Getter;

/** Permanent storage buses and the two permanent controller upgrades. */
@Getter
public enum RequestTableUpgradeBranch {

    SLOT_COUNT("Item slots", false, true, false),
    SLOT_SIZE("Item capacity", false, false, false),
    FLUID_SLOT_COUNT("Fluid tanks", true, true, false),
    FLUID_SLOT_SIZE("Fluid capacity", true, false, false),
    FLUID_CONTROLLER("Fluid controller", true, false, true),
    CRAFTING_MONITORING("Crafting monitor", false, false, true);

    private final String label;
    private final boolean fluid;
    private final boolean slotCount;
    private final boolean special;

    RequestTableUpgradeBranch(String label, boolean fluid, boolean slotCount, boolean special) {
        this.label = label;
        this.fluid = fluid;
        this.slotCount = slotCount;
        this.special = special;
    }

    public boolean isValidSelection(int tier) {
        return special ? tier == 0 : RequestTableStorageUpgradeConfig.isValidTier(tier);
    }

    public String getUnit() {
        return slotCount ? (fluid ? "tanks" : "slots") : (fluid ? "mB/tank" : "items/slot");
    }
}
