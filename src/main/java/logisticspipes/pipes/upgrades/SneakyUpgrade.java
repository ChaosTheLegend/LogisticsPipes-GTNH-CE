package logisticspipes.pipes.upgrades;

import logisticspipes.modules.ModuleActiveSupplier;
import logisticspipes.modules.ModuleCrafter;
import logisticspipes.modules.ModuleElectricManager;
import logisticspipes.modules.ModuleExtractor;
import logisticspipes.modules.ModuleFluidContainerSupplier;
import logisticspipes.modules.ModuleItemSink;
import logisticspipes.modules.ModulePolymorphicItemSink;
import logisticspipes.modules.ModuleProvider;
import logisticspipes.modules.ModuleTagItemSink;
import logisticspipes.modules.abstractmodules.LogisticsFluidModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.basic.CoreRoutedPipe;

/**
 * Lets the pipe or module work on the inventory next to it through faces other than the one facing the pipe. The faces
 * are picked in the settings window (gear), in order: items go in through the first face that takes them and come out
 * of the first that gives them. It replaces the six sided sneaky upgrades and the combined one (decided 2026-11-09).
 */
public class SneakyUpgrade implements IPipeUpgrade {

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
        return module instanceof ModuleItemSink || module instanceof ModulePolymorphicItemSink
                || module instanceof ModuleTagItemSink
                || module instanceof ModuleExtractor
                || module instanceof ModuleProvider
                || module instanceof ModuleActiveSupplier
                || module instanceof ModuleElectricManager
                || module instanceof ModuleFluidContainerSupplier
                || module instanceof ModuleCrafter
                || module instanceof LogisticsFluidModule;
    }

    @Override
    public String[] getAllowedPipes() {
        return new String[] { "all" };
    }

    @Override
    public String[] getAllowedModules() {
        return new String[] { "itemsink", "extractor", "provider", "supplier", "electric manager", "crafter", "fluid" };
    }
}
