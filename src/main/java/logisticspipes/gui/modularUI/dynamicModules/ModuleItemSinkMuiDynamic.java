package logisticspipes.gui.modularUI.dynamicModules;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.PhantomItemSlotSH;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;

import logisticspipes.gui.modularUI.CurrentModuleRef;
import logisticspipes.gui.modularUI.GenericModuleMUI;
import logisticspipes.modules.ModuleItemSink;

/**
 * The item sink window: the filter, import, default route and the priority (normal or terminus). The handlers ask a
 * {@link CurrentModuleRef} for the module, so a rebuilt chassis page works on the module in the slot now.
 */
public class ModuleItemSinkMuiDynamic extends GenericModuleMUI<ModuleItemSink> {

    public ModuleItemSinkMuiDynamic(ModuleItemSink module) {
        this(module, "");
    }

    public ModuleItemSinkMuiDynamic(ModuleItemSink module, String prefix) {
        super(module, prefix);
    }

    @Override
    public String getId() {
        return "module_item_sink";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, PanelSyncManager syncManager, boolean addPlayerInventory) {

        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        CurrentModuleRef<ModuleItemSink> ref = CurrentModuleRef.of(syncManager, getFullId(), module);

        BooleanSyncValue defaultRouteSync = sync(
                syncManager,
                "_default_route",
                BooleanSyncValue.class,
                () -> new BooleanSyncValue(() -> ref.get().isDefaultRoute(), v -> ref.get().setDefaultRoute(v))
                        .allowC2S());
        BooleanSyncValue terminusSync = sync(
                syncManager,
                "_terminus",
                BooleanSyncValue.class,
                () -> new BooleanSyncValue(() -> ref.get().isTerminus(), v -> ref.get().setTerminus(v)).allowC2S());

        // the import reads the inventory next to the pipe, so it runs on the server
        InteractionSyncHandler importSync = sync(
                syncManager,
                "_import",
                InteractionSyncHandler.class,
                InteractionSyncHandler::new);
        importSync.setOnMousePressed(mouse -> {
            // runs on both sides: only the server imports, the client gets the filter synced
            if (syncManager != null && syncManager.isClient()) return;
            if (mouse.mouseButton == 0) ref.get().importFromInventory();
        });

        widget.child(
                new Flow(GuiAxis.Y).coverChildren().left(9).top(4).childPadding(4)
                        .crossAxisAlignment(Alignment.CrossAxis.START)
                        .child(new TextWidget<>(IKey.lang("gui.module.requestedItems")))
                        .child(buildFilterSlots(syncManager, ref))
                        .child(
                                new Flow(GuiAxis.X).coverChildrenHeight().width(162)
                                        .child(
                                                new ButtonWidget<>().syncHandler(importSync)
                                                        .overlay(IKey.lang("gui.module.import")).width(40).height(16))
                                        .child(new Flow(GuiAxis.X).expanded()).child(
                                                new Flow(GuiAxis.X).coverChildren().childPadding(4)
                                                        .child(new TextWidget<>(IKey.lang("gui.module.defaultRoute")))
                                                        .child(
                                                                new CycleButtonWidget().width(26).height(16)
                                                                        .value(defaultRouteSync).overlay(
                                                                                IKey.lang(
                                                                                        () -> ref.get().isDefaultRoute()
                                                                                                ? "gui.module.on"
                                                                                                : "gui.module.off")))))
                        .child(
                                // terminus: below the item sinks, above the tag and enchantment sinks
                                new Flow(GuiAxis.X).coverChildren().childPadding(4)
                                        .child(new TextWidget<>(IKey.lang("gui.module.priority"))).child(
                                                new CycleButtonWidget().width(60).height(16).value(terminusSync)
                                                        .overlay(
                                                                IKey.lang(
                                                                        () -> ref.get().isTerminus()
                                                                                ? "gui.module.ModuleItemSink.terminus"
                                                                                : "gui.module.ModuleItemSink.normal")))));

        return widget;
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        return addWidgets(widget, null, addPlayerInventory);
    }

    private Flow buildFilterSlots(PanelSyncManager syncManager, CurrentModuleRef<ModuleItemSink> ref) {
        String id = getFullId();
        IItemHandlerModifiable filterInventory = ref.inventory(ModuleItemSink::getFilterInventory);
        Flow row = Flow.row().coverChildren();
        for (int i = 0; i < 9; i++) {
            int slotIndex = i;
            PhantomItemSlot slotWidget = new PhantomItemSlot();
            if (syncManager != null) {
                PhantomItemSlotSH slotSH = syncManager.getOrCreateSyncHandler(
                        id + "_filter",
                        slotIndex,
                        PhantomItemSlotSH.class,
                        () -> new PhantomItemSlotSH(new ModularSlot(filterInventory, slotIndex)));
                slotWidget.syncHandler(slotSH);
            } else {
                slotWidget.slot(filterInventory, slotIndex);
            }
            row.child(slotWidget);
        }
        return row;
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
