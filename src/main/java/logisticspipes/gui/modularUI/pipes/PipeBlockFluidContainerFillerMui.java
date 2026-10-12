package logisticspipes.gui.modularUI.pipes;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;

import logisticspipes.gui.modularUI.LogisticsPipeMUI;
import logisticspipes.pipes.PipeBlockFluidContainerFiller;
import logisticspipes.pipes.basic.CoreRoutedPipe;

public class PipeBlockFluidContainerFillerMui extends LogisticsPipeMUI {

    private final IItemHandlerModifiable containerInventory;

    public PipeBlockFluidContainerFillerMui(CoreRoutedPipe pipe) {
        super(pipe);
        containerInventory = new InvWrapper(((PipeBlockFluidContainerFiller) pipe).getContainerInventory());
    }

    @Override
    public String getId() {
        return "pipe_block_fluid_container_filler";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {

        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        widget.child(
                new Flow(GuiAxis.Y).fullWidth().coverChildrenHeight().top(4).childPadding(4)
                        .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                        .child(IKey.lang("gui.fluidcontainerfiller.title").asWidget())
                        .child(
                                Flow.row().coverChildren().childPadding(4)
                                        .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                                        .child(
                                                IKey.comp(
                                                        IKey.lang("gui.fluidcontainerfiller.container"),
                                                        IKey.str(":")).asWidget())
                                        .child(new PhantomItemSlot().slot(containerInventory, 0)))
                        .child(IKey.lang("gui.fluidcontainerfiller.hint").asWidget().width(162)));

        return widget;
    }

    @Override
    public int getWidth() {
        return 180;
    }

    @Override
    public int getHeight() {
        return 160;
    }
}
