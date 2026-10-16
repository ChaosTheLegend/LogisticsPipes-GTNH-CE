package logisticspipes.pipes.upgrades;

import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.pipes.basic.CoreRoutedPipe;

/** Allows pattern crafting item ingredients to be inserted into satellites without traveling through the network. */
public class InstantSatelliteUpgrade implements IPipeUpgrade {

    @Override
    public boolean needsUpdate() {
        return false;
    }

    @Override
    public boolean isAllowedForPipe(CoreRoutedPipe pipe) {
        return pipe instanceof PipeItemsPatternCraftingLogistics;
    }

    @Override
    public boolean isAllowedForModule(LogisticsModule module) {
        return false;
    }

    @Override
    public String[] getAllowedPipes() {
        return new String[] { "pattern crafting" };
    }

    @Override
    public String[] getAllowedModules() {
        return new String[] {};
    }
}
