package logisticspipes.pipes.upgrades;

import logisticspipes.crafting.PipeFluidPatternSatelliteLogistics;
import logisticspipes.crafting.PipeItemsPatternSatelliteLogistics;
import logisticspipes.modules.ModuleCrafter;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.PipeItemsCraftingLogistics;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.pipes.basic.CoreRoutedPipe;

public class CraftingByproductUpgrade implements IPipeUpgrade {

    @Override
    public boolean needsUpdate() {
        return false;
    }

    @Override
    public boolean isAllowedForPipe(CoreRoutedPipe pipe) {
        return pipe instanceof PipeItemsCraftingLogistics || pipe instanceof PipeItemsPatternCraftingLogistics
                || pipe instanceof PipeItemsPatternSatelliteLogistics
                || pipe instanceof PipeFluidPatternSatelliteLogistics;
    }

    @Override
    public boolean isAllowedForModule(LogisticsModule pipe) {
        return pipe instanceof ModuleCrafter;
    }

    @Override
    public String[] getAllowedPipes() {
        return new String[] { "crafting", "pattern crafting", "pattern satellite", "pattern fluid satellite" };
    }

    @Override
    public String[] getAllowedModules() {
        return new String[] { "crafting" };
    }
}
