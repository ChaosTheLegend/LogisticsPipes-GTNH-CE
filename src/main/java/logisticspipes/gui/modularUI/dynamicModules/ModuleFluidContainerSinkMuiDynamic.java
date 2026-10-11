package logisticspipes.gui.modularUI.dynamicModules;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;

import logisticspipes.gui.modularUI.GenericModuleMUI;
import logisticspipes.modules.ModuleFluidContainerSink;

public class ModuleFluidContainerSinkMuiDynamic extends GenericModuleMUI<ModuleFluidContainerSink> {

    public ModuleFluidContainerSinkMuiDynamic(ModuleFluidContainerSink module) {
        this(module, "");
    }

    public ModuleFluidContainerSinkMuiDynamic(ModuleFluidContainerSink module, String prefix) {
        super(module, prefix);
    }

    @Override
    public String getId() {
        return "module_fluid_container_sink";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, PanelSyncManager syncManager, boolean addPlayerInventory) {

        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        widget.child(
                new Flow(GuiAxis.Y).coverChildren().left(9).top(4).childPadding(4)
                        .crossAxisAlignment(Alignment.CrossAxis.START)
                        .child(IKey.lang("gui.module.ModuleFluidContainerSink").asWidget())
                        .child(buildFilterSlots(syncManager))
                        .child(IKey.lang("gui.module.ModuleFluidContainerSink.hint").asWidget().width(162)));

        return widget;
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        return addWidgets(widget, null, addPlayerInventory);
    }

    private Flow buildFilterSlots(PanelSyncManager syncManager) {
        String id = getFullId();
        Flow row = Flow.row().coverChildren();
        for (int i = 0; i < ModuleFluidContainerSink.FILTER_SLOTS; i++) {
            int slotIndex = i;
            FluidSlotSyncHandler slotSH = syncManager != null ? syncManager.getOrCreateSyncHandler(
                    id + "_filter",
                    slotIndex,
                    FluidSlotSyncHandler.class,
                    () -> newFilterSlot(slotIndex)) : newFilterSlot(slotIndex);
            row.child(new FluidSlot().syncHandler(slotSH));
        }
        return row;
    }

    private FluidSlotSyncHandler newFilterSlot(int slot) {
        return new FluidSlotSyncHandler(module.getFilterTank(slot)).phantom(true).controlsAmount(false);
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
