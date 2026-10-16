package logisticspipes.gui.modularUI.dynamicModules;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
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

import logisticspipes.gui.modularUI.GenericModuleMUI;
import logisticspipes.modules.ModuleItemSink;

public class ModuleItemSinkMuiDynamic extends GenericModuleMUI<ModuleItemSink> {

    private final IItemHandlerModifiable filterInventory;

    public ModuleItemSinkMuiDynamic(ModuleItemSink module) {
        this(module, "");
    }

    public ModuleItemSinkMuiDynamic(ModuleItemSink module, String prefix) {
        super(module, prefix);
        filterInventory = new InvWrapper(module.getFilterInventory());
    }

    @Override
    public String getId() {
        return "module_item_sink";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, PanelSyncManager syncManager, boolean addPlayerInventory) {

        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        BooleanSyncValue defaultRouteSync = syncManager != null
                ? syncManager.getOrCreateSyncHandler(
                        getFullId() + "_default_route",
                        BooleanSyncValue.class,
                        () -> new BooleanSyncValue(module::isDefaultRoute, module::setDefaultRoute).allowC2S())
                : new BooleanSyncValue(module::isDefaultRoute, module::setDefaultRoute).allowC2S();

        widget.child(
                new Flow(GuiAxis.Y).coverChildren().left(9).top(4).childPadding(4)
                        .crossAxisAlignment(Alignment.CrossAxis.START).child(new TextWidget<>("Requested items"))
                        .child(buildFilterSlots(syncManager)).child(
                                new Flow(GuiAxis.X).coverChildrenHeight().widthRel(1f)
                                        .child(new ButtonWidget<>().onMousePressed(i -> {
                                            if (i == 0) module.importFromInventory();
                                            return i == 0;
                                        }).overlay(IKey.lang("Import")).width(40).height(16))
                                        .child(new Flow(GuiAxis.X).expanded()).child(
                                                new Flow(GuiAxis.X).coverChildren().childPadding(4)
                                                        .child(new TextWidget<>("Default route:")).child(
                                                                new CycleButtonWidget().width(26).height(16)
                                                                        .value(defaultRouteSync).overlay(
                                                                                IKey.lang(
                                                                                        () -> module.isDefaultRoute()
                                                                                                ? "On"
                                                                                                : "Off"))))));

        return widget;
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        return addWidgets(widget, null, addPlayerInventory);
    }

    private Flow buildFilterSlots(PanelSyncManager syncManager) {
        String id = getFullId();
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
        return 140;
    }
}
