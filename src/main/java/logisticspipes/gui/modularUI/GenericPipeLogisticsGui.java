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

import logisticspipes.api.IMUICompatibleModule;
import logisticspipes.compat.ModularUIHelper;
import logisticspipes.pipes.basic.CoreRoutedPipe;

public class GenericPipeLogisticsGui extends LogisticsModularUI {

    private final CoreRoutedPipe pipe;
    private final IItemHandlerModifiable upgradeHandler;
    private final IMUICompatibleModule module;

    private static final ResourceLocation UpgradeSlotTexture = new ResourceLocation(
            "logisticspipes",
            "textures/gui/upgrade_slot.png");

    public GenericPipeLogisticsGui(IMUICompatibleModule module, CoreRoutedPipe pipe) {
        this(module, pipe, "");
    }

    public GenericPipeLogisticsGui(IMUICompatibleModule module, CoreRoutedPipe pipe, String prefix) {
        super(prefix);
        this.module = module;
        upgradeHandler = pipe.getUpgradeManager().getUpgradeInventory();
        this.pipe = pipe;
    }

    @Override
    public String getId() {
        return module.getPipeGui().getId();
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        ((IMUICompatibleModule) module).getPipeGui().addWidgets(widget, addPlayerInventory);
        return widget;
    }

    @Override
    public ModularPanel getPanel(GuiData guiData, PanelSyncManager guiSyncManager) {

        var panel = ModularPanel.defaultPanel(getId(), getWidth(), getHeight()).background(IDrawable.EMPTY);

        panel.child(
                addWidgets(
                        new Column().width(module.getPipeGui().getWidth()).height(module.getPipeGui().getHeight()),
                        true).background(ModularUIHelper.BACKGROUND_TEXTURE));

        // Disabled for now, until all pipes have a Mui gui

        // addUpgradeGui(panel);

        return panel;
    }

    @Override
    public int getWidth() {
        return ((IMUICompatibleModule) module).getPipeGui().getWidth(); // + 28 for upgrade gui;
    }

    @Override
    public int getHeight() {
        return ((IMUICompatibleModule) module).getPipeGui().getHeight();
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
