package logisticspipes.gui.modularUI.dynamicModules;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;

import logisticspipes.gui.modularUI.GenericModuleMUI;
import logisticspipes.modules.abstractmodules.LogisticsFluidModule;

/**
 * The page of a fluid module that has nothing to configure: a line that says what the module does.
 */
public class ModuleFluidInfoMuiDynamic extends GenericModuleMUI<LogisticsFluidModule> {

    private final String langKey;

    public ModuleFluidInfoMuiDynamic(LogisticsFluidModule module, String prefix, String langKey) {
        super(module, prefix);
        this.langKey = langKey;
    }

    @Override
    public String getId() {
        return "module_fluid_info";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, PanelSyncManager syncManager, boolean addPlayerInventory) {

        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        widget.child(
                new TextWidget<>(IKey.lang(langKey)).leftRel(Alignment.Center.x).anchorLeft(Alignment.Center.x)
                        .top(40));

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
