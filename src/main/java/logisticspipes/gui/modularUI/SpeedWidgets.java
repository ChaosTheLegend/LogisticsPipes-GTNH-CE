package logisticspipes.gui.modularUI;

import java.util.function.Supplier;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.upgrades.ModuleUpgradeManager;

/**
 * The speed line of a module window: "Speed: N/M", the speed upgrades that count of the most that do.
 */
public final class SpeedWidgets {

    private SpeedWidgets() {}

    /**
     * The synced speed, registered with the window. The server counts it: the client's module doesn't count upgrades.
     *
     * @param syncManager the window's sync manager, or null
     * @param id          unique for the window, e.g. its full id
     * @param module      the module now, null when there is none (see {@link CurrentModuleRef})
     */
    public static IntSyncValue value(PanelSyncManager syncManager, String id,
            Supplier<? extends LogisticsModule> module) {
        IntSyncValue speed = GenericModuleMUI.register(syncManager, id + "_speed", 0, IntSyncValue.class, () -> {
            return new IntSyncValue(() -> {
                LogisticsModule current = module.get();
                return current == null ? 0 : current.getSpeedLevel();
            });
        });
        if (syncManager != null && !syncManager.isClient()) {
            // a value no module has: the server sends the real one with the next update (a chassis page built later
            // gets no first sync of its own for values no widget carries)
            speed.setIntValue(-1, false, false);
        }
        return speed;
    }

    /**
     * @return the line that shows the speed
     */
    public static IKey line(IntSyncValue speed) {
        return IKey.lang(
                "gui.module.speed",
                () -> new Object[] { Math.max(0, speed.getIntValue()), ModuleUpgradeManager.MAX_SPEED_UPGRADES });
    }
}
