package logisticspipes.gui.modularUI.dynamicModules;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.PhantomItemSlotSH;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;

import logisticspipes.gui.modularUI.GenericModuleMUI;
import logisticspipes.modules.ModuleFluidContainerCrafter;

public class ModuleFluidContainerCrafterMuiDynamic extends GenericModuleMUI<ModuleFluidContainerCrafter> {

    private final IItemHandlerModifiable containerInventory;

    public ModuleFluidContainerCrafterMuiDynamic(ModuleFluidContainerCrafter module) {
        this(module, "");
    }

    public ModuleFluidContainerCrafterMuiDynamic(ModuleFluidContainerCrafter module, String prefix) {
        super(module, prefix);
        containerInventory = new InvWrapper(module.getContainerInventory());
    }

    @Override
    public String getId() {
        return "module_fluid_container_crafter";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, PanelSyncManager syncManager, boolean addPlayerInventory) {

        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        String id = getFullId();

        FluidSlotSyncHandler fluidSync = syncManager != null
                ? syncManager.getOrCreateSyncHandler(id + "_fluid", FluidSlotSyncHandler.class, this::newFluidSlot)
                : newFluidSlot();

        InteractionSyncHandler importSync = syncManager != null ? syncManager
                .getOrCreateSyncHandler(id + "_import", 0, InteractionSyncHandler.class, InteractionSyncHandler::new)
                : new InteractionSyncHandler();
        importSync.setOnMousePressed(mouse -> {
            if (mouse.mouseButton != 0) return;
            module.importFromTank();
        });

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
                        .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                        .child(IKey.lang("gui.module.ModuleFluidContainerCrafter").asWidget())
                        .child(
                                row().child(
                                        IKey.comp(
                                                IKey.lang("gui.module.ModuleFluidContainerCrafter.fluid"),
                                                IKey.str(":")).asWidget())
                                        .child(new FluidSlot().syncHandler(fluidSync)).child(
                                                new ButtonWidget<>().syncHandler(importSync)
                                                        .overlay(IKey.lang("gui.module.ModuleFluidSink.import"))
                                                        .width(40).height(16).tooltipBuilder(
                                                                tooltip -> tooltip.addLine(
                                                                        IKey.lang(
                                                                                "gui.module.ModuleFluidContainerCrafter.import.tip")))))
                        .child(
                                row().child(
                                        IKey.comp(
                                                IKey.lang("gui.module.ModuleFluidContainerCrafter.container"),
                                                IKey.str(":")).asWidget())
                                        .child(containerSlot))
                        .child(IKey.lang("gui.module.ModuleFluidContainerCrafter.hint").asWidget().width(162)));

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

    @Override
    public int getWidth() {
        return 180;
    }

    @Override
    public int getHeight() {
        return 150;
    }
}
