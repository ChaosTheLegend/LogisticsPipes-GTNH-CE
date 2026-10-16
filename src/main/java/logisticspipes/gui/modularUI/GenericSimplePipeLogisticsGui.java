package logisticspipes.gui.modularUI;

import net.minecraft.util.ResourceLocation;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import logisticspipes.compat.ModularUIHelper;
import logisticspipes.pipes.basic.CoreRoutedPipe;

public class GenericSimplePipeLogisticsGui extends LogisticsModularUI {

    private final CoreRoutedPipe pipe;
    private final LogisticsPipeMUI pipeMUI;
    private final IItemHandlerModifiable upgradeHandler;

    private static final ResourceLocation UpgradeSlotTexture = new ResourceLocation(
            "logisticspipes",
            "textures/gui/upgrade_slot.png");

    public GenericSimplePipeLogisticsGui(LogisticsPipeMUI pipeMui, String prefix) {
        super(prefix);
        this.pipeMUI = pipeMui;
        this.pipe = pipeMui.getPipe();
        upgradeHandler = pipe.getUpgradeManager().getUpgradeInventory();
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

        // Disabled until all pipes have a Mui gui

        // addUpgradeGui(panel);

        return panel;
    }

    @Override
    public int getWidth() {
        return pipeMUI.getWidth(); // + 28; for upgrade slots
    }

    @Override
    public int getHeight() {
        return pipeMUI.getHeight();
    }

    private void addUpgradeGui(ModularPanel panel) {
        panel.child(
                new Column().background(ModularUIHelper.BACKGROUND_TEXTURE).width(26).right(0).child(
                        SlotGroupWidget.builder().row("I").row("I").row("I").row("I").key(
                                'I',
                                i -> new ItemSlot()
                                        .slot(
                                                new ModularSlot(upgradeHandler, i).filter(PipeGuiFactory::isUpgradeItem)
                                                        .accessibility(true, true))
                                        .background(UITexture.fullImage(UpgradeSlotTexture)))
                                .build())
                        .padding(4).coverChildrenHeight());
    }
}
