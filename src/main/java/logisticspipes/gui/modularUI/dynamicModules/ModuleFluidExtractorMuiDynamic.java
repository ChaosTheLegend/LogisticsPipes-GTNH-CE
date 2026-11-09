package logisticspipes.gui.modularUI.dynamicModules;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import logisticspipes.gui.modularUI.CurrentModuleRef;
import logisticspipes.gui.modularUI.FluidSinkWidgets;
import logisticspipes.gui.modularUI.GenericModuleMUI;
import logisticspipes.gui.modularUI.SpeedWidgets;
import logisticspipes.modules.ModuleFluidExtractor;

/**
 * The fluid extractor window: the fluid filter (empty drains everything) with whitelist / blacklist, and its speed.
 */
public class ModuleFluidExtractorMuiDynamic extends GenericModuleMUI<ModuleFluidExtractor> {

    public ModuleFluidExtractorMuiDynamic(ModuleFluidExtractor module, String prefix) {
        super(module, prefix);
    }

    @Override
    public String getId() {
        return "module_fluid_extractor";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, PanelSyncManager syncManager, boolean addPlayerInventory) {

        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        String id = getFullId();
        CurrentModuleRef<ModuleFluidExtractor> ref = CurrentModuleRef.of(syncManager, id, module);

        BooleanSyncValue blacklist = sync(
                syncManager,
                "_blacklist",
                BooleanSyncValue.class,
                () -> new BooleanSyncValue(() -> ref.get().isBlacklist(), v -> ref.get().setBlacklist(v)).allowC2S());
        IntSyncValue speed = SpeedWidgets.value(syncManager, id, ref::get);

        Flow controls = new Flow(GuiAxis.X).coverChildrenHeight().width(162).child(
                new CycleButtonWidget().value(blacklist).width(56).height(16).overlay(
                        IKey.lang(() -> ref.get().isBlacklist() ? "gui.module.blacklist" : "gui.module.whitelist")))
                .child(new Flow(GuiAxis.X).expanded()).child(SpeedWidgets.line(speed).asWidget());

        widget.child(
                new Flow(GuiAxis.Y).coverChildren().left(9).top(4).childPadding(4)
                        .crossAxisAlignment(Alignment.CrossAxis.START)
                        .child(IKey.lang("gui.module.ModuleFluidExtractor.filter").asWidget())
                        .child(FluidSinkWidgets.filterRow(() -> ref.get().getFilter(), syncManager, id))
                        .child(controls));

        return widget;
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        return addWidgets(widget, null, addPlayerInventory);
    }

    @Override
    public int getWidth() {
        return 180;
    }

    @Override
    public int getHeight() {
        return 140;
    }
}
