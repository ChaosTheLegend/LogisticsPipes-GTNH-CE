package logisticspipes.gui.modularUI.dynamicModules;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.PhantomItemSlotSH;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;

import logisticspipes.gui.modularUI.GenericModuleMUI;
import logisticspipes.modules.ModuleElectricManager;

public class ModuleElectricManagerMuiDynamic extends GenericModuleMUI<ModuleElectricManager> {

    private final IItemHandlerModifiable filterInventory;

    public ModuleElectricManagerMuiDynamic(ModuleElectricManager module) {
        this(module, "");
    }

    public ModuleElectricManagerMuiDynamic(ModuleElectricManager module, String prefix) {
        super(module, prefix);
        filterInventory = new InvWrapper(module.getFilterInventory());
    }

    @Override
    public String getId() {
        return "module_electric_manager";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, PanelSyncManager syncManager, boolean addPlayerInventory) {

        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        BooleanSyncValue dischargeModeSync = syncManager != null
                ? syncManager.getOrCreateSyncHandler(
                        getFullId() + "_discharge_mode",
                        BooleanSyncValue.class,
                        () -> new BooleanSyncValue(module::isDischargeMode, module::setDischargeMode).allowC2S())
                : new BooleanSyncValue(module::isDischargeMode, module::setDischargeMode).allowC2S();

        widget.child(
                new Flow(GuiAxis.Y).coverChildren().left(9).top(4).childPadding(4)
                        .crossAxisAlignment(Alignment.CrossAxis.START).child(new TextWidget<>("Electric items"))
                        .child(buildFilterSlots(syncManager)).child(
                                new CycleButtonWidget().width(86).height(20).leftRel(Alignment.END.x)
                                        .anchorLeft(Alignment.END.x).value(dischargeModeSync).overlay(
                                                IKey.lang(
                                                        () -> module.isDischargeMode() ? "Discharge items"
                                                                : "Charge items"))));

        return widget;
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        return addWidgets(widget, null, addPlayerInventory);
    }

    private Flow buildFilterSlots(PanelSyncManager syncManager) {
        String id = getFullId();
        Flow row = Flow.row().coverChildren();
        for (int i = 0; i < 9; i++) {
            int slotIndex = i;
            PhantomItemSlot slotWidget = new PhantomItemSlot();
            if (syncManager != null) {
                PhantomItemSlotSH slotSH = syncManager.getOrCreateSyncHandler(
                        id + "_filter",
                        slotIndex,
                        PhantomItemSlotSH.class,
                        () -> new PhantomItemSlotSH(new ModularSlot(filterInventory, slotIndex)));
                slotWidget.syncHandler(slotSH);
            } else {
                slotWidget.slot(filterInventory, slotIndex);
            }
            row.child(slotWidget);
        }
        return row;
    }

    @Override
    public int getWidth() {
        return 180;
    }

    @Override
    public int getHeight() {
        return 146;
    }
}
