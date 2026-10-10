package logisticspipes.gui.modularUI.dynamicModules;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;

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

        String id = getFullId();

        BooleanSyncValue defaultRouteSync = syncManager != null
                ? syncManager.getOrCreateSyncHandler(
                        id + "_default_route",
                        BooleanSyncValue.class,
                        () -> new BooleanSyncValue(module::isDefaultRoute, module::setDefaultRoute).allowC2S())
                : new BooleanSyncValue(module::isDefaultRoute, module::setDefaultRoute).allowC2S();

        InteractionSyncHandler importSync = syncManager != null ? syncManager
                .getOrCreateSyncHandler(id + "_import", 0, InteractionSyncHandler.class, InteractionSyncHandler::new)
                : new InteractionSyncHandler();
        importSync.setOnMousePressed(mouse -> {
            if (mouse.mouseButton != 0) return;
            module.importFromTank();
        });

        widget.child(
                new Flow(GuiAxis.Y).coverChildren().left(9).top(4).childPadding(4)
                        .crossAxisAlignment(Alignment.CrossAxis.START)
                        .child(IKey.lang("gui.module.ModuleFluidSink").asWidget()).child(buildFilterSlots(syncManager))
                        .child(
                                new Flow(GuiAxis.X).coverChildrenHeight().widthRel(1f)
                                        .child(
                                                new ButtonWidget<>().syncHandler(importSync)
                                                        .overlay(IKey.lang("gui.module.ModuleFluidSink.import"))
                                                        .width(40).height(16))
                                        .child(new Flow(GuiAxis.X).expanded()).child(
                                                new Flow(GuiAxis.X).coverChildren().childPadding(4).child(
                                                        IKey.lang("gui.module.ModuleFluidSink.defaultRoute").asWidget())
                                                        .child(
                                                                new CycleButtonWidget().width(26).height(16)
                                                                        .value(defaultRouteSync).overlay(
                                                                                IKey.lang(
                                                                                        () -> module.isDefaultRoute()
                                                                                                ? "gui.module.ModuleFluidSink.on"
                                                                                                : "gui.module.ModuleFluidSink.off"))))));

        return widget;
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        return addWidgets(widget, null, addPlayerInventory);
    }

    private Flow buildFilterSlots(PanelSyncManager syncManager) {
        String id = getFullId();
        Flow row = Flow.row().coverChildren();
        for (int i = 0; i < ModuleFluidSink.FILTER_SLOTS; i++) {
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
