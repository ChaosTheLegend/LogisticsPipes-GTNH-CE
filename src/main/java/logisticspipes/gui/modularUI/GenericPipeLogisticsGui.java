package logisticspipes.gui.modularUI;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.layout.Column;

import logisticspipes.api.IMUICompatibleModule;
import logisticspipes.compat.ModularUIHelper;
import logisticspipes.pipes.basic.CoreRoutedPipe;

public class GenericPipeLogisticsGui extends LogisticsModularUI {

    private final CoreRoutedPipe pipe;
    private final IMUICompatibleModule module;

    public GenericPipeLogisticsGui(IMUICompatibleModule module, CoreRoutedPipe pipe) {
        this(module, pipe, "");
    }

    public GenericPipeLogisticsGui(IMUICompatibleModule module, CoreRoutedPipe pipe, String prefix) {
        super(prefix);
        this.module = module;
        this.pipe = pipe;
    }

    @Override
    public String getId() {
        return module.getPipeGui().getId();
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        module.getPipeGui().addWidgets(widget, addPlayerInventory);
        return widget;
    }

    @Override
    public ModularPanel getPanel(GuiData guiData, PanelSyncManager guiSyncManager) {

        var panel = ModularPanel.defaultPanel(getId(), getWidth(), getHeight()).background(IDrawable.EMPTY);

        // with the sync manager, so values that no widget carries (e.g. a module's speed) reach the client too
        panel.child(
                module.getPipeGui()
                        .addWidgets(
                                new Column().width(module.getPipeGui().getWidth())
                                        .height(module.getPipeGui().getHeight()),
                                guiSyncManager,
                                true)
                        .background(ModularUIHelper.BACKGROUND_TEXTURE));

        // a pipe that is one module shows the module's own upgrades, as a module in hand does; the settings of the pipe
        // (its sides and the priority of its module) under them
        int width = module.getPipeGui().getWidth();
        var settings = PipeSettingsPanel.button(panel, guiSyncManager, "pipe_settings", pipe, pipe::getLogisticsModule);
        if (hasColumn()) {
            ModuleUpgradeSlots.addSide(
                    panel,
                    width + ModuleUpgradeSlots.GAP,
                    4,
                    ModuleUpgradeSlots.column(guiSyncManager, "module_upgrades", pipe::getLogisticsModule, () -> {
                        // the server saves the pipe when an upgrade went in or out
                        if (pipe.container != null) {
                            pipe.container.markDirty();
                        }
                    }),
                    settings);
        } else {
            panel.child(settings.left(width - 20).top(4));
        }

        return panel;
    }

    // a pipe that is one module has the upgrade column of the module
    private boolean hasColumn() {
        return pipe.hasModuleHost() && pipe.getLogisticsModule() != null;
    }

    @Override
    public int getWidth() {
        int width = module.getPipeGui().getWidth();
        return hasColumn() ? width + ModuleUpgradeSlots.SIDE_WIDTH : width;
    }

    @Override
    public int getHeight() {
        return module.getPipeGui().getHeight();
    }
}
