package logisticspipes.pipes.upgrades;

import logisticspipes.modules.abstractmodules.IPrioritizedModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.basic.CoreRoutedPipe;

/**
 * Lets a sink or provider module have a priority: set in the settings window (gear), it works while this upgrade is
 * installed; without it the module's priority is 0 and the value set stays in the module (decided 2026-11-09).
 */
public class PriorityUpgrade implements IPipeUpgrade {

    @Override
    public boolean needsUpdate() {
        return false;
    }

    /**
     * Never into a pipe's slots: every prioritized module is in a chassis or built into its pipe, with slots of its
     * own.
     */
    @Override
    public boolean isAllowedForPipe(CoreRoutedPipe pipe) {
        return false;
    }

    @Override
    public boolean isAllowedForModule(LogisticsModule module) {
        return module instanceof IPrioritizedModule;
    }

    @Override
    public String[] getAllowedPipes() {
        return new String[] {};
    }

    @Override
    public String[] getAllowedModules() {
        return new String[] { "itemsink", "provider", "fluid sink", "fluid provider" };
    }
}
