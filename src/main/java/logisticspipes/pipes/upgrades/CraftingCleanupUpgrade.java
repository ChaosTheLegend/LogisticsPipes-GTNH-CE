package logisticspipes.pipes.upgrades;

import logisticspipes.crafting.legacy.module.ModuleCrafter;
import logisticspipes.crafting.legacy.pipe.PipeItemsCraftingLogistics;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.basic.CoreRoutedPipe;

public class CraftingCleanupUpgrade implements IPipeUpgrade {

    @Override
    public boolean needsUpdate() {
        return false;
    }

    @Override
    public boolean isAllowedForPipe(CoreRoutedPipe pipe) {
        return pipe instanceof PipeItemsCraftingLogistics;
    }

    @Override
    public boolean isAllowedForModule(LogisticsModule pipe) {
        return pipe instanceof ModuleCrafter;
    }

    @Override
    public String[] getAllowedPipes() {
        return new String[] { "crafting" };
    }

    @Override
    public String[] getAllowedModules() {
        return new String[] { "crafting" };
    }
}
