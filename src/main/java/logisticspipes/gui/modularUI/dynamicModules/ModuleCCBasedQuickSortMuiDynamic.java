package logisticspipes.gui.modularUI.dynamicModules;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import logisticspipes.gui.modularUI.CurrentModuleRef;
import logisticspipes.gui.modularUI.GenericModuleMUI;
import logisticspipes.gui.modularUI.SpeedWidgets;
import logisticspipes.modules.ModuleCCBasedQuickSort;

/**
 * The window of the computer quick sort: what it does, how long it waits for the computers to answer (the timeout, in
 * ticks), how many stacks wait for an answer now, and its speed. The server changes the timeout, the client sends the
 * value; the handlers ask a {@link CurrentModuleRef} for the module, so a rebuilt chassis page works on the module in
 * the slot now.
 */
public class ModuleCCBasedQuickSortMuiDynamic extends GenericModuleMUI<ModuleCCBasedQuickSort> {

    private static final String LANG = "gui.module.ModuleCCBasedQuickSort";
    private static final int TEXT_WIDTH = 168;
    // the limits of the old window
    private static final int MIN_TIMEOUT = 5;
    private static final int MAX_TIMEOUT = 1000;

    public ModuleCCBasedQuickSortMuiDynamic(ModuleCCBasedQuickSort module, String prefix) {
        super(module, prefix);
    }

    @Override
    public String getId() {
        return "module_cc_quick_sort";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        return addWidgets(widget, null, addPlayerInventory);
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, PanelSyncManager syncManager, boolean addPlayerInventory) {
        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        CurrentModuleRef<ModuleCCBasedQuickSort> ref = CurrentModuleRef.of(syncManager, getFullId(), module);

        IntSyncValue timeout = sync(
                syncManager,
                "_timeout",
                IntSyncValue.class,
                () -> new IntSyncValue(() -> ref.get().getTimeout(), v -> ref.get().setTimeout(clamp(v))).allowC2S());
        IntSyncValue waiting = sync(
                syncManager,
                "_sink_size",
                IntSyncValue.class,
                () -> new IntSyncValue(() -> ref.get().getSinkSize()));
        if (syncManager != null && !syncManager.isClient()) {
            // values no module has: the server sends the real ones with the next update (a chassis page built later
            // gets no first sync of its own for values no widget carries)
            timeout.setIntValue(-1, false, false);
            waiting.setIntValue(-1, false, false);
        }

        Flow timeoutRow = Flow.row().coverChildren().childPadding(2).crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(step(timeout, -10, -100, "--")).child(step(timeout, -1, -5, "-"))
                .child(
                        new TextWidget<>(IKey.lang(LANG + ".ticks", () -> new Object[] { shown(timeout) })).width(60)
                                .textAlign(Alignment.Center))
                .child(step(timeout, 1, 5, "+")).child(step(timeout, 10, 100, "++"))
                .tooltipBuilder(tooltip -> tooltip.addLine(IKey.lang(LANG + ".timeout.tip")));

        Flow column = new Flow(GuiAxis.Y).width(TEXT_WIDTH).coverChildrenHeight().left(6).top(8).childPadding(4)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(new TextWidget<>(IKey.lang(LANG + ".info")).width(TEXT_WIDTH).textAlign(Alignment.Center))
                .child(new TextWidget<>(IKey.lang(LANG)).width(TEXT_WIDTH).textAlign(Alignment.Center))
                .child(timeoutRow).child(
                        new TextWidget<>(
                                IKey.lang(LANG + ".waiting", () -> new Object[] { Math.max(0, waiting.getIntValue()) }))
                                        .width(TEXT_WIDTH).textAlign(Alignment.Center));
        if (ModuleInfoMuiDynamic.takesSpeed(module)) {
            IntSyncValue speed = SpeedWidgets.value(syncManager, getFullId(), ref::get);
            column.child(new TextWidget<>(SpeedWidgets.line(speed)).width(TEXT_WIDTH).textAlign(Alignment.Center));
        }
        widget.child(column);
        return widget;
    }

    private static int clamp(int timeout) {
        return Math.max(MIN_TIMEOUT, Math.min(MAX_TIMEOUT, timeout));
    }

    // the timeout shown: the module's default until the server sent the real one
    private static int shown(IntSyncValue timeout) {
        int value = timeout.getIntValue();
        return value < 0 ? 100 : value;
    }

    // a click changes the timeout by the step, with shift by the big step; the server gets the new value
    private static ButtonWidget<?> step(IntSyncValue timeout, int step, int shiftStep, String text) {
        return new ButtonWidget<>().size(16, 12).overlay(IKey.str(text)).onMousePressed(mouse -> {
            if (mouse != 0) return false;
            int by = Interactable.hasShiftDown() ? shiftStep : step;
            timeout.setIntValue(clamp(shown(timeout) + by), true, true);
            return true;
        });
    }

    @Override
    public int getWidth() {
        return 180;
    }

    /**
     * Room for the description, the timeout, the waiting stacks and the speed above the player inventory.
     */
    @Override
    public int getHeight() {
        return 176;
    }
}
