package logisticspipes.gui.modularUI;

import java.util.function.Supplier;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import logisticspipes.modules.abstractmodules.LogisticsModule;

/**
 * The priority control of a module: "label − N +". A click on − or + changes the priority by 1, with shift by 5.
 * Between modules of the same kind the higher priority wins, before distance (see
 * {@link LogisticsModule#getPriority()}).
 */
public final class PriorityWidgets {

    private PriorityWidgets() {}

    /**
     * The synced priority, registered with the window (both sides build it); {@link #row} shows it, also in a panel
     * opened on top of the window.
     *
     * @param syncManager the window's sync manager, or null
     * @param id          unique for the window, e.g. its full id
     * @param module      the module now, null when there is none (a chassis page can be rebuilt for another module, see
     *                    {@link CurrentModuleRef})
     */
    public static IntSyncValue value(PanelSyncManager syncManager, String id,
            Supplier<? extends LogisticsModule> module) {
        IntSyncValue priority = GenericModuleMUI
                .register(syncManager, id + "_priority", 0, IntSyncValue.class, () -> newValue(module));
        if (syncManager != null && !syncManager.isClient()) {
            // a value no module has: the server sends the real one with the next update (a chassis page built later
            // gets no first sync of its own for values no widget carries)
            priority.setIntValue(Integer.MIN_VALUE, false, false);
        }
        return priority;
    }

    /**
     * @param label the text in front, e.g. "Priority:"
     */
    public static Flow row(IntSyncValue priority, IKey label) {
        return Flow.row().coverChildren().childPadding(2).crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(new TextWidget<>(label)).child(step(priority, -1, "-"))
                .child(
                        new TextWidget<>(IKey.dynamic(() -> Integer.toString(shown(priority)))).width(16)
                                .textAlign(Alignment.Center))
                .child(step(priority, 1, "+"))
                .tooltipBuilder(tooltip -> tooltip.addLine(IKey.lang("gui.module.priority.tip")));
    }

    private static IntSyncValue newValue(Supplier<? extends LogisticsModule> module) {
        return new IntSyncValue(() -> {
            LogisticsModule current = module.get();
            return current == null ? 0 : current.getSetPriority();
        }, value -> {
            LogisticsModule current = module.get();
            if (current != null) {
                current.setPriority(value);
            }
        }).allowC2S();
    }

    private static int shown(IntSyncValue priority) {
        int value = priority.getIntValue();
        return value == Integer.MIN_VALUE ? 0 : value;
    }

    private static ButtonWidget<?> step(IntSyncValue priority, int direction, String text) {
        return new ButtonWidget<>().size(12, 12).overlay(IKey.str(text)).onMousePressed(mouse -> {
            if (mouse != 0) return false;
            int by = Interactable.hasShiftDown() ? 5 : 1;
            int next = Math.max(
                    LogisticsModule.MIN_PRIORITY,
                    Math.min(LogisticsModule.MAX_PRIORITY, shown(priority) + direction * by));
            priority.setIntValue(next, true, true);
            return true;
        });
    }
}
