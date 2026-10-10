package logisticspipes.gui.modularUI.pipes;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;

import logisticspipes.gui.modularUI.LogisticsPipeMUI;
import logisticspipes.pipes.PipeFluidBasic;
import logisticspipes.pipes.basic.CoreRoutedPipe;

/**
 * Same controls as the fluid sink module of the fluid chassis.
 */
public class PipeFluidBasicMui extends LogisticsPipeMUI {

    public PipeFluidBasicMui(CoreRoutedPipe pipe) {
        super(pipe);
    }

    @Override
    public String getId() {
        return "pipe_fluid_basic";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {

        PipeFluidBasic basicPipe = (PipeFluidBasic) pipe;

        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        Flow filterSlots = Flow.row().coverChildren();
        for (int i = 0; i < PipeFluidBasic.FILTER_SLOTS; i++) {
            filterSlots.child(
                    new FluidSlot().syncHandler(
                            SyncHandlers.fluidSlot(basicPipe.getFilterTank(i)).phantom(true).controlsAmount(false)));
        }

        widget.child(
                new Flow(GuiAxis.Y).coverChildren().left(9).top(4).childPadding(4)
                        .crossAxisAlignment(Alignment.CrossAxis.START)
                        .child(IKey.lang("gui.module.ModuleFluidSink").asWidget()).child(filterSlots).child(
                                new Flow(GuiAxis.X).coverChildrenHeight().widthRel(1f).child(
                                        new ButtonWidget<>()
                                                .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouse -> {
                                                    if (mouse.mouseButton != 0) return;
                                                    basicPipe.importFromTanks();
                                                })).overlay(IKey.lang("gui.module.ModuleFluidSink.import")).width(40)
                                                .height(16))
                                        .child(new Flow(GuiAxis.X).expanded()).child(
                                                new Flow(GuiAxis.X).coverChildren().childPadding(4).child(
                                                        IKey.lang("gui.module.ModuleFluidSink.defaultRoute").asWidget())
                                                        .child(
                                                                new CycleButtonWidget().width(26).height(16).value(
                                                                        new BooleanSyncValue(
                                                                                basicPipe::isDefaultRoute,
                                                                                basicPipe::setDefaultRoute).allowC2S())
                                                                        .overlay(
                                                                                IKey.lang(
                                                                                        () -> basicPipe.isDefaultRoute()
                                                                                                ? "gui.module.ModuleFluidSink.on"
                                                                                                : "gui.module.ModuleFluidSink.off"))))));

        return widget;
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
