package logisticspipes.pipes.upgrades;

import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.basic.CoreRoutedPipe;

public abstract class ConnectionUpgrade implements IPipeUpgrade {

    public abstract ForgeDirection getSide();

    @Override
    public boolean needsUpdate() {
        return true;
    }

    @Override
    public boolean isAllowedForPipe(CoreRoutedPipe pipe) {
        // deprecated (2026-11-06): sides are disconnected with shift + wrench, installed upgrades become that setting
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
