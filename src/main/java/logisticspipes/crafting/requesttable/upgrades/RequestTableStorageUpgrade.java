package logisticspipes.crafting.requesttable.upgrades;

import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.crafting.requesttable.mk3.RequestTableMk3Pipe;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.upgrades.IPipeUpgrade;

/**
 * Base implementation for upgrades that only apply to the request tables Mk2 and Mk3.
 */
abstract class RequestTableStorageUpgrade implements IPipeUpgrade {

    @Override
    public boolean needsUpdate() {
        return true;
    }

    @Override
    public boolean isAllowedForPipe(CoreRoutedPipe pipe) {
        return pipe instanceof RequestTablePipe || pipe instanceof RequestTableMk3Pipe;
    }

    @Override
    public boolean isAllowedForModule(LogisticsModule module) {
        return false;
    }

    @Override
    public String[] getAllowedPipes() {
        return new String[] { "newRequestTable", "requestTableMk3" };
    }

    @Override
    public String[] getAllowedModules() {
        return new String[] {};
    }
}
