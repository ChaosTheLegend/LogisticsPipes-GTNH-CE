package logisticspipes.pipes.upgrades.sneaky;

import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.upgrades.IPipeUpgrade;

/**
 * A sneaky upgrade of one face, from before the sneaky upgrade took its faces from the settings window. Deprecated:
 * installed ones turn into the sneaky upgrade with their face when they load, the items craft into it.
 */
public abstract class SidedSneakyUpgrade implements IPipeUpgrade {

    public abstract ForgeDirection getSneakyOrientation();

    @Override
    public boolean needsUpdate() {
        return false;
    }

    @Override
    public boolean isAllowedForPipe(CoreRoutedPipe pipe) {
        return false;
    }

    @Override
    public boolean isAllowedForModule(LogisticsModule module) {
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
