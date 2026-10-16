package logisticspipes.crafting.requesttable.gui.upgrade;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.crafting.requesttable.upgrade.RequestTableStorageUpgradeConfig;
import logisticspipes.crafting.requesttable.upgrade.RequestTableUpgradeBranch;
import logisticspipes.crafting.requesttable.upgrade.RequestTableUpgradeContainer;

/** One presentation decision shared by upgrade descriptions, sockets and button tooltips. */
@SideOnly(Side.CLIENT)
enum RequestTableUpgradeStatus {

    WAITING,
    APPLIED,
    PREVIOUS_REQUIRED,
    FLUID_CONTROLLER_REQUIRED,
    AVAILABLE;

    static RequestTableUpgradeStatus of(RequestTableUpgradeContainer upgrades, RequestTableUpgradeBranch branch,
                                        int tier) {
        int installed = upgrades.getTable().getUpgradeTier(branch);
        if (branch.isSpecial() ? installed > 0 : installed >= tier) return APPLIED;
        if (!upgrades.isReady()) return WAITING;
        if (branch.isSpecial()) return AVAILABLE;
        if (installed < tier - 1) return PREVIOUS_REQUIRED;
        if (branch.isFluid() && !upgrades.getTable().isFluidEnabled()) return FLUID_CONTROLLER_REQUIRED;
        return AVAILABLE;
    }

    String hint(int tier) {
        return switch (this) {
            case WAITING -> "Waiting for the server...";
            case APPLIED -> "Upgrade applied permanently.";
            case PREVIOUS_REQUIRED -> "Install " + RequestTableStorageUpgradeConfig.getTierName(tier - 1)
                + " on this branch first.";
            case FLUID_CONTROLLER_REQUIRED -> "Install the fluid controller first.";
            case AVAILABLE -> "Consume the required materials to unlock this upgrade.";
        };
    }
}
