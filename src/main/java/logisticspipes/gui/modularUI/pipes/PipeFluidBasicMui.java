package logisticspipes.gui.modularUI.pipes;

import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;

import logisticspipes.gui.modularUI.FluidSinkWidgets;
import logisticspipes.gui.modularUI.LogisticsPipeMUI;
import logisticspipes.pipes.PipeFluidBasic;
import logisticspipes.pipes.basic.CoreRoutedPipe;

/**
 * Same page as the fluid sink module.
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

        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        widget.child(FluidSinkWidgets.sinkPage((PipeFluidBasic) pipe, null, getId()));

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
