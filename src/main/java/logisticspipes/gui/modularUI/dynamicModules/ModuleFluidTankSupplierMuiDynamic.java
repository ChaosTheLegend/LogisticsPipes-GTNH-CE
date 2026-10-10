package logisticspipes.gui.modularUI.dynamicModules;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import logisticspipes.gui.modularUI.GenericModuleMUI;
import logisticspipes.modules.ModuleFluidTankSupplier;

public class ModuleFluidTankSupplierMuiDynamic extends GenericModuleMUI<ModuleFluidTankSupplier> {

    public ModuleFluidTankSupplierMuiDynamic(ModuleFluidTankSupplier module) {
        this(module, "");
    }

    public ModuleFluidTankSupplierMuiDynamic(ModuleFluidTankSupplier module, String prefix) {
        super(module, prefix);
    }

    @Override
    public String getId() {
        return "module_fluid_tank_supplier";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, PanelSyncManager syncManager, boolean addPlayerInventory) {

        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        String id = getFullId();

        FluidSlotSyncHandler fluidSync = syncManager != null
                ? syncManager.getOrCreateSyncHandler(id + "_fluid", FluidSlotSyncHandler.class, this::newFluidSlot)
                : newFluidSlot();
        IntSyncValue amountSync = syncManager != null
                ? syncManager.getOrCreateSyncHandler(id + "_amount", IntSyncValue.class, this::newAmountValue)
                : newAmountValue();
        BooleanSyncValue partialSync = syncManager != null
                ? syncManager.getOrCreateSyncHandler(id + "_partial", BooleanSyncValue.class, this::newPartialValue)
                : newPartialValue();
        IntSyncValue thresholdSync = syncManager != null
                ? syncManager.getOrCreateSyncHandler(id + "_threshold", IntSyncValue.class, this::newThresholdValue)
                : newThresholdValue();

        // same layout as the fluid supplier pipe GUI
        widget.child(
                new Flow(GuiAxis.Y).fullWidth().coverChildrenHeight().top(4).childPadding(4)
                        .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                        .child(IKey.lang("gui.fluidsuppliermk2.TargetInv").asWidget())
                        .child(
                                row().child(
                                        IKey.comp(IKey.lang("gui.fluidsuppliermk2.Fluid"), IKey.str(":")).asWidget())
                                        .child(new FluidSlot().syncHandler(fluidSync))
                                        .child(
                                                new TextFieldWidget().width(80).setNumbers(0, Integer.MAX_VALUE)
                                                        .value(amountSync))
                                        .child(IKey.str("mB").asWidget()))
                        .child(
                                row().child(
                                        IKey.comp(IKey.lang("gui.fluidsuppliermk2.partial"), IKey.str(":")).asWidget())
                                        .child(
                                                new CycleButtonWidget().width(26).height(16).value(partialSync)
                                                        .overlay(
                                                                IKey.lang(
                                                                        () -> module.requestPartials
                                                                                ? "gui.fluidsuppliermk2.partial.yes"
                                                                                : "gui.fluidsuppliermk2.partial.no"))
                                                        .tooltipBuilder(tooltip -> {
                                                            tooltip.addLine(
                                                                    IKey.lang("gui.fluidsuppliermk2.partial.tip"));
                                                            tooltip.addLine(
                                                                    IKey.lang(
                                                                            module.requestPartials
                                                                                    ? "gui.fluidsuppliermk2.partial.yes.tip"
                                                                                    : "gui.fluidsuppliermk2.partial.no.tip"));
                                                        }).tooltipPos(RichTooltip.Pos.ABOVE)))
                        .child(
                                IKey.comp(IKey.lang("gui.fluidsuppliermk2.refill_if_depleted"), IKey.str(":"))
                                        .asWidget())
                        .child(
                                row().child(
                                        new TextFieldWidget().width(80).setNumbers(0, Integer.MAX_VALUE)
                                                .value(thresholdSync))
                                        .child(IKey.str("mB").asWidget())
                                        .child(IKey.str("§9[?]").asWidget().tooltipBuilder(tooltip -> {
                                            tooltip.setAutoUpdate(true);
                                            tooltip.addLine(
                                                    IKey.lang(
                                                            "gui.fluidsuppliermk2.refill_if_depleted.tip",
                                                            module.refillThreshold != 0 ? module.refillThreshold
                                                                    : "n"));
                                            tooltip.addLine(
                                                    IKey.lang("gui.fluidsuppliermk2.refill_if_depleted.tip.zero"));
                                        }).tooltipPos(RichTooltip.Pos.ABOVE))));

        return widget;
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        return addWidgets(widget, null, addPlayerInventory);
    }

    private Flow row() {
        return Flow.row().coverChildren().childPadding(4).crossAxisAlignment(Alignment.CrossAxis.CENTER);
    }

    private FluidSlotSyncHandler newFluidSlot() {
        return new FluidSlotSyncHandler(module.phantomTank).phantom(true).controlsAmount(false);
    }

    private IntSyncValue newAmountValue() {
        return new IntSyncValue(() -> module.amount, value -> module.amount = value).allowC2S();
    }

    private BooleanSyncValue newPartialValue() {
        return new BooleanSyncValue(() -> module.requestPartials, value -> module.requestPartials = value).allowC2S();
    }

    private IntSyncValue newThresholdValue() {
        return new IntSyncValue(() -> module.refillThreshold, value -> module.refillThreshold = value).allowC2S();
    }

    @Override
    public int getWidth() {
        return 180;
    }

    @Override
    public int getHeight() {
        return 170;
    }
}
