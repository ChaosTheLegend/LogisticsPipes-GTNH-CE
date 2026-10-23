package logisticspipes.pipes.upgrades;

import logisticspipes.crafting.legacy.module.ModuleCrafter;
import logisticspipes.crafting.legacy.pipe.PipeItemsCraftingLogistics;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.pipes.basic.CoreRoutedPipe;

public class AdvancedSatelliteUpgrade implements IPipeUpgrade {

    @Override
    public boolean needsUpdate() {
        return false;
    }

    @Override
    public boolean isAllowedForPipe(CoreRoutedPipe pipe) {
        return pipe instanceof PipeItemsCraftingLogistics || pipe instanceof PipeItemsPatternCraftingLogistics;
    }

    @Override
    public boolean isAllowedForModule(LogisticsModule module) {
        return module instanceof ModuleCrafter;
    }

    @Override
    public String[] getAllowedPipes() {
        return new String[] { "crafting", "pattern crafting" };
    }

    @Override
    public String[] getAllowedModules() {
        return new String[] { "crafting" };
    }
}
