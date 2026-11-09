package logisticspipes.gui.modularUI;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.layout.Column;

import logisticspipes.compat.ModularUIHelper;

public class GenericSimplePipeLogisticsGui extends LogisticsModularUI {

    private final LogisticsPipeMUI pipeMUI;

    public GenericSimplePipeLogisticsGui(LogisticsPipeMUI pipeMui, String prefix) {
        super(prefix);
        this.pipeMUI = pipeMui;
    }

    public GenericSimplePipeLogisticsGui(LogisticsPipeMUI pipeMui) {
        this(pipeMui, "");
    }

    @Override
    public String getId() {
        return pipeMUI.getId();
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        pipeMUI.addWidgets(widget, addPlayerInventory);
        return widget;
    }

    @Override
    public ModularPanel getPanel(GuiData guiData, PanelSyncManager guiSyncManager) {

        var panel = ModularPanel.defaultPanel(getId(), getWidth(), getHeight()).background(IDrawable.EMPTY);

        panel.child(
                addWidgets(new Column().width(pipeMUI.getWidth()).height(pipeMUI.getHeight()), true)
                        .background(ModularUIHelper.BACKGROUND_TEXTURE));

        return panel;
    }

    @Override
    public int getWidth() {
        return pipeMUI.getWidth();
    }

    @Override
    public int getHeight() {
        return pipeMUI.getHeight();
    }
}
