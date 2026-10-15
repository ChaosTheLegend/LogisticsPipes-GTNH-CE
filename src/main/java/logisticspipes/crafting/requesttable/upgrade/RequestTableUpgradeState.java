package logisticspipes.crafting.requesttable.upgrade;

import java.util.List;

import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.crafting.requesttable.upgrade.RequestTableUpgradeMaterials.Requirement;

/** Read access to one viewer's upgrade selection, shared by the legacy upgrade screen and the ModularUI board. */
public interface RequestTableUpgradeState {

    RequestTablePipe getTable();

    /** @return {@code false} while the client waits for the server to confirm its last selection */
    boolean isReady();

    RequestTableUpgradeBranch getSelectedBranch();

    int getSelectedTier();

    List<Requirement> getRequirements();

    RequestTableStorageUpgradeConfig getConfig(boolean fluid);

    default RequestTableStorageUpgradeConfig getConfig() {
        return getConfig(getSelectedBranch().isFluid());
    }

    int getMaterialCount(Requirement requirement);

    boolean canUpgrade();

    boolean canUpgradeCreative();
}
