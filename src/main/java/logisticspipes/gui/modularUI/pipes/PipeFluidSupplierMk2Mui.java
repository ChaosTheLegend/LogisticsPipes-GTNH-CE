package logisticspipes.gui.modularUI.pipes;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Row;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import logisticspipes.gui.modularUI.LogisticsPipeMUI;
import logisticspipes.pipes.PipeFluidSupplierMk2;
import logisticspipes.pipes.basic.CoreRoutedPipe;

public class PipeFluidSupplierMk2Mui extends LogisticsPipeMUI {

    public PipeFluidSupplierMk2Mui(CoreRoutedPipe pipe) {
        super(pipe);
    }

    @Override
    public String getId() {
        return "pipe_fluid_supplier_mk2";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {

        PipeFluidSupplierMk2 supplierPipe = (PipeFluidSupplierMk2) pipe;

        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        widget.child(
                new Column().widthRel(1.0F).top(6).coverChildrenHeight().child(
                        new Row().mainAxisAlignment(Alignment.MainAxis.CENTER)
                                .crossAxisAlignment(Alignment.CrossAxis.CENTER).widthRel(1.0F).coverChildrenHeight()
                                .child(IKey.lang("gui.fluidsuppliermk2.TargetInv").asWidget()))
                        .child(
                                new Row().mainAxisAlignment(Alignment.MainAxis.CENTER)
                                        .crossAxisAlignment(Alignment.CrossAxis.CENTER).marginTop(5)
                                        .coverChildrenHeight()
                                        .child(
                                                IKey.comp(IKey.lang("gui.fluidsuppliermk2.Fluid"), IKey.str(":"))
                                                        .asWidget())
                                        .child(
                                                new FluidSlot()
                                                        .syncHandler(
                                                                new FluidSlotSyncHandler(supplierPipe.phantomTank)
                                                                        .phantom(true).controlsAmount(false))
                                                        .marginLeft(6).width(16).height(16))
                                        .child(
                                                new TextFieldWidget().marginLeft(6).width(80)
                                                        .setNumbers(0, Integer.MAX_VALUE).value(
                                                                SyncHandlers
                                                                        .intNumber(
                                                                                () -> supplierPipe.amount,
                                                                                value -> supplierPipe.amount = value)
                                                                        .allowC2S()))
                                        .child(IKey.str("mB").asWidget().marginLeft(3)))
                        .child(
                                new Row().mainAxisAlignment(Alignment.MainAxis.CENTER)
                                        .crossAxisAlignment(Alignment.CrossAxis.CENTER).marginTop(5)
                                        .coverChildrenHeight()
                                        .child(
                                                IKey.comp(IKey.lang("gui.fluidsuppliermk2.partial"), IKey.str(":"))
                                                        .asWidget())
                                        .child(
                                                new CycleButtonWidget().marginLeft(6).width(24).value(
                                                        SyncHandlers
                                                                .bool(
                                                                        () -> supplierPipe.requestPartials,
                                                                        value -> supplierPipe.requestPartials = value)
                                                                .allowC2S())
                                                        .overlay(
                                                                IKey.lang(
                                                                        () -> supplierPipe.requestPartials
                                                                                ? "gui.fluidsuppliermk2.partial.yes"
                                                                                : "gui.fluidsuppliermk2.partial.no"))
                                                        .tooltipBuilder((tooltip -> {
                                                            tooltip.addLine(
                                                                    IKey.lang("gui.fluidsuppliermk2.partial.tip"));

                                                            if (supplierPipe.requestPartials) {
                                                                tooltip.addLine(
                                                                        IKey.lang(
                                                                                "gui.fluidsuppliermk2.partial.yes.tip"));
                                                            } else {
                                                                tooltip.addLine(
                                                                        IKey.lang(
                                                                                "gui.fluidsuppliermk2.partial.no.tip"));
                                                            }
                                                        })).tooltipPos(RichTooltip.Pos.ABOVE)))
                        .child(
                                new Row().mainAxisAlignment(Alignment.MainAxis.CENTER)
                                        .crossAxisAlignment(Alignment.CrossAxis.CENTER).marginTop(5)
                                        .coverChildrenHeight().child(
                                                IKey.comp(
                                                        IKey.lang("gui.fluidsuppliermk2.refill_if_depleted"),
                                                        IKey.str(":")).asWidget()))
                        .child(
                                new Row().marginTop(5).mainAxisAlignment(Alignment.MainAxis.CENTER)
                                        .coverChildrenHeight()
                                        .child(
                                                new TextFieldWidget().width(80).setNumbers(0, Integer.MAX_VALUE).value(
                                                        SyncHandlers
                                                                .intNumber(
                                                                        () -> supplierPipe.refillThreshold,
                                                                        value -> supplierPipe.refillThreshold = value)
                                                                .allowC2S())

                                        ).child(IKey.str("mB").asWidget().marginLeft(3))
                                        .child(IKey.str("§9[?]").asWidget().marginLeft(6).tooltipBuilder(tooltip -> {
                                            tooltip.setAutoUpdate(true);
                                            tooltip.addLine(
                                                    IKey.lang(
                                                            "gui.fluidsuppliermk2.refill_if_depleted.tip",
                                                            supplierPipe.refillThreshold != 0
                                                                    ? supplierPipe.refillThreshold
                                                                    : "n"));
                                            tooltip.addLine(
                                                    IKey.lang("gui.fluidsuppliermk2.refill_if_depleted.tip.zero"));
                                        }).tooltipPos(RichTooltip.Pos.ABOVE))));
        return null;
    }

    @Override
    public int getWidth() {
        return 184;
    }

    @Override
    public int getHeight() {
        return 186;
    }
}
