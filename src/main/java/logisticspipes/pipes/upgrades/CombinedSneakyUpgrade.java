package logisticspipes.pipes.upgrades;

import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.basic.CoreRoutedPipe;

/**
 * Deprecated: merged into {@link SneakyUpgrade}, which takes several faces in order.
 */
public class CombinedSneakyUpgrade implements IPipeUpgrade {

    @Override
    public boolean needsUpdate() {
        return false;
    }

    @Override
    public boolean isAllowedForPipe(CoreRoutedPipe pipe) {
        // merged into the sneaky upgrade (several faces in order); installed ones turn into it when they load
        return false;
    }

    @Override
    public boolean isAllowedForModule(LogisticsModule pipe) {
        return false;
    }

    @Override
    public String[] getAllowedPipes() {
        return new String[] {};
    }

    @Override
    public String[] getAllowedModules() {
        return new String[] {};
    }
}
