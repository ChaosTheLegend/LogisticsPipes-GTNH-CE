package logisticspipes.pipes.upgrades;

import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.basic.CoreRoutedPipe;

public class UpgradeModuleUpgrade implements IPipeUpgrade {

    @Override
    public boolean needsUpdate() {
        return false;
    }

    /**
     * Deprecated: every module has its own upgrade slots now. Installed ones are deleted when the pipe loads, new ones
     * can't be installed.
     */
    @Override
    public boolean isAllowedForPipe(CoreRoutedPipe pipe) {
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
