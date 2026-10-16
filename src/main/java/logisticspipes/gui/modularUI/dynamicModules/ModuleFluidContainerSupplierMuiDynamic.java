package logisticspipes.gui.modularUI.dynamicModules;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.PhantomItemSlotSH;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import logisticspipes.gui.modularUI.GenericModuleMUI;
import logisticspipes.modules.ModuleFluidContainerSupplier;

public class ModuleFluidContainerSupplierMuiDynamic extends GenericModuleMUI<ModuleFluidContainerSupplier> {

    private static final String LANG = "gui.module.ModuleFluidContainerSupplier";

    private final IItemHandlerModifiable containerInventory;

    public ModuleFluidContainerSupplierMuiDynamic(ModuleFluidContainerSupplier module) {
        this(module, "");
    }

    public ModuleFluidContainerSupplierMuiDynamic(ModuleFluidContainerSupplier module, String prefix) {
        super(module, prefix);
        containerInventory = new InvWrapper(module.getContainerInventory());
    }

    @Override
    public String getId() {
        return "module_fluid_container_supplier";
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

        PhantomItemSlot containerSlot = new PhantomItemSlot();
        if (syncManager != null) {
            containerSlot.syncHandler(
                    syncManager.getOrCreateSyncHandler(
                            id + "_container",
                            PhantomItemSlotSH.class,
                            () -> new PhantomItemSlotSH(new ModularSlot(containerInventory, 0))));
        } else {
            containerSlot.slot(containerInventory, 0);
        }

        widget.child(
                new Flow(GuiAxis.Y).fullWidth().coverChildrenHeight().top(4).childPadding(4)
                        .crossAxisAlignment(Alignment.CrossAxis.CENTER).child(IKey.lang(LANG).asWidget())
                        .child(
                                row().child(IKey.comp(IKey.lang(LANG + ".fluid"), IKey.str(":")).asWidget())
                                        .child(new FluidSlot().syncHandler(fluidSync))
                                        .child(IKey.comp(IKey.lang(LANG + ".container"), IKey.str(":")).asWidget())
                                        .child(containerSlot))
                        .child(
                                row().child(IKey.comp(IKey.lang(LANG + ".amount"), IKey.str(":")).asWidget())
                                        .child(new TextFieldWidget().width(60).setNumbers(0, 9999).value(amountSync)))
                        .child(IKey.lang(LANG + ".hint").asWidget().width(162)));

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
        return new IntSyncValue(() -> module.amount, value -> module.amount = Math.max(0, value)).allowC2S();
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
