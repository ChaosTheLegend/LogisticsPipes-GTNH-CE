package logisticspipes.gui.modularUI.dynamicModules;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.PhantomItemSlotSH;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;

import logisticspipes.gui.modularUI.CurrentModuleRef;
import logisticspipes.gui.modularUI.GenericModuleMUI;
import logisticspipes.modules.ModuleEnchantmentSink;

/**
 * The enchantment sink window: the filter, empty for every enchanted item. The slots ask a {@link CurrentModuleRef} for
 * the module, so a rebuilt chassis page works on the module in the slot now.
 */
public class ModuleEnchantmentSinkMuiDynamic extends GenericModuleMUI<ModuleEnchantmentSink> {

    public ModuleEnchantmentSinkMuiDynamic(ModuleEnchantmentSink module) {
        this(module, "");
    }

    public ModuleEnchantmentSinkMuiDynamic(ModuleEnchantmentSink module, String prefix) {
        super(module, prefix);
    }

    @Override
    public String getId() {
        return "module_enchantment_sink";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, PanelSyncManager syncManager, boolean addPlayerInventory) {

        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        widget.child(
                new Flow(GuiAxis.Y).coverChildren().left(9).top(4).childPadding(4)
                        .crossAxisAlignment(Alignment.CrossAxis.START)
                        .child(new TextWidget<>("Enchanted items to take"))
                        .child(new TextWidget<>("Empty: any enchanted item").scale(0.8f))
                        .child(buildFilterSlots(syncManager)));

        return widget;
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        return addWidgets(widget, null, addPlayerInventory);
    }

    private Flow buildFilterSlots(PanelSyncManager syncManager) {
        String id = getFullId();
        CurrentModuleRef<ModuleEnchantmentSink> ref = CurrentModuleRef.of(syncManager, id, module);
        IItemHandlerModifiable filterInventory = ref.inventory(ModuleEnchantmentSink::getFilterInventory);
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
        return 150;
    }
}
