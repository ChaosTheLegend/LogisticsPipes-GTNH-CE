package logisticspipes.pipes.upgrades;

import logisticspipes.modules.ModuleExtractor;
import logisticspipes.modules.ModuleFluidExtractor;
import logisticspipes.modules.ModuleFluidProvider;
import logisticspipes.modules.ModuleProvider;
import logisticspipes.modules.ModuleQuickSort;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.basic.CoreRoutedPipe;

public class SpeedUpgrade implements IPipeUpgrade {

    @Override
    public boolean needsUpdate() {
        return false;
    }

    @Override
    public boolean isAllowedForPipe(CoreRoutedPipe pipe) {
        return true;
    }

    @Override
    public boolean isAllowedForModule(LogisticsModule module) {
        return module instanceof ModuleExtractor || module instanceof ModuleFluidExtractor
                || module instanceof ModuleProvider
                || module instanceof ModuleFluidProvider
                || module instanceof ModuleQuickSort;
    }

    @Override
    public String[] getAllowedPipes() {
        return new String[] { "all" };
    }

    @Override
    public String[] getAllowedModules() {
        return new String[] { "extractor", "fluid extractor", "provider", "fluid provider", "quicksort" };
    }
}
