package logisticspipes.crafting.requesttable.upgrades;

import logisticspipes.crafting.requesttable.mk3.RequestTableMk3Pipe;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.upgrades.IPipeUpgrade;

/**
 * Base implementation for upgrades that only apply to the request table Mk3. The Mk2 table gets its storage from its
 * own upgrade board and accepts no upgrade items.
 */
abstract class RequestTableStorageUpgrade implements IPipeUpgrade {

    @Override
    public boolean needsUpdate() {
        return true;
    }

    @Override
    public boolean isAllowedForPipe(CoreRoutedPipe pipe) {
        return pipe instanceof RequestTableMk3Pipe;
    }

    @Override
    public boolean isAllowedForModule(LogisticsModule module) {
        return false;
    }

    @Override
    public String[] getAllowedPipes() {
        return new String[] { "requestTableMk3" };
    }

    @Override
    public String[] getAllowedModules() {
        return new String[] {};
    }
}
