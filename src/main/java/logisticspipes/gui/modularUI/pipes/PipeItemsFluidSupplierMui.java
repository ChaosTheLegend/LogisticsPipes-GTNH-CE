package logisticspipes.gui.modularUI.pipes;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;

import logisticspipes.gui.modularUI.LogisticsPipeMUI;
import logisticspipes.pipes.PipeItemsFluidSupplier;
import logisticspipes.pipes.basic.CoreRoutedPipe;

public class PipeItemsFluidSupplierMui extends LogisticsPipeMUI {

    private static final int ROWS = 3;
    private static final int COLUMNS = 3;

    private final IItemHandlerModifiable supplyInventory;

    public PipeItemsFluidSupplierMui(CoreRoutedPipe pipe) {
        super(pipe);
        supplyInventory = new InvWrapper(((PipeItemsFluidSupplier) pipe).getDummyInventory());
    }

    @Override
    public String getId() {
        return "pipe_items_fluid_supplier";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {

        PipeItemsFluidSupplier supplierPipe = (PipeItemsFluidSupplier) pipe;

        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        // the filled containers to keep stocked, the stack size is the number of them
        Flow slots = Flow.col().coverChildren();
        for (int row = 0; row < ROWS; row++) {
            Flow rowWidget = Flow.row().coverChildren();
            for (int column = 0; column < COLUMNS; column++) {
                rowWidget.child(new PhantomItemSlot().slot(supplyInventory, row * COLUMNS + column));
            }
            slots.child(rowWidget);
        }

        widget.child(
                new Flow(GuiAxis.Y).fullWidth().coverChildrenHeight().top(4).childPadding(4)
                        .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                        .child(IKey.lang("gui.fluidsupplier.TargetInv").asWidget()).child(slots).child(
                                Flow.row().coverChildren().childPadding(4)
                                        .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                                        .child(
                                                IKey.comp(IKey.lang("gui.fluidsupplier.Partialrequests"), IKey.str(":"))
                                                        .asWidget())
                                        .child(
                                                new CycleButtonWidget().width(30).height(16)
                                                        .value(
                                                                new BooleanSyncValue(
                                                                        supplierPipe::isRequestingPartials,
                                                                        supplierPipe::setRequestingPartials).allowC2S())
                                                        .overlay(
                                                                IKey.lang(
                                                                        () -> supplierPipe.isRequestingPartials()
                                                                                ? "gui.fluidsupplier.Yes"
                                                                                : "gui.fluidsupplier.No")))));

        return widget;
    }

    @Override
    public int getWidth() {
        return 180;
    }

    @Override
    public int getHeight() {
        return 184;
    }
}
