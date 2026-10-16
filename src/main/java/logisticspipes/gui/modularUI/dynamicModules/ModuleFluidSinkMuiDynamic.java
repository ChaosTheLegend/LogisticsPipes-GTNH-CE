package logisticspipes.gui.modularUI.dynamicModules;

import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;

import logisticspipes.gui.modularUI.FluidSinkWidgets;
import logisticspipes.gui.modularUI.GenericModuleMUI;
import logisticspipes.modules.ModuleFluidSink;

public class ModuleFluidSinkMuiDynamic extends GenericModuleMUI<ModuleFluidSink> {

    public ModuleFluidSinkMuiDynamic(ModuleFluidSink module) {
        this(module, "");
    }

    public ModuleFluidSinkMuiDynamic(ModuleFluidSink module, String prefix) {
        super(module, prefix);
    }

    @Override
    public String getId() {
        return "module_fluid_sink";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, PanelSyncManager syncManager, boolean addPlayerInventory) {

        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        widget.child(FluidSinkWidgets.sinkPage(module, syncManager, getFullId()));

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
