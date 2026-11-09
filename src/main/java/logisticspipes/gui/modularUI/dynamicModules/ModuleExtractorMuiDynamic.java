package logisticspipes.gui.modularUI.dynamicModules;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.PhantomItemSlotSH;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;

import logisticspipes.gui.modularUI.CurrentModuleRef;
import logisticspipes.gui.modularUI.GenericModuleMUI;
import logisticspipes.gui.modularUI.SpeedWidgets;
import logisticspipes.modules.ModuleExtractor;

/**
 * The extractor window: the 9-slot filter with include / exclude, the side it pulls from, and its speed.
 * <p>
 * The values and slots ask a {@link CurrentModuleRef} for the module, so a rebuilt chassis page works on the module in
 * the slot now.
 */
public class ModuleExtractorMuiDynamic extends GenericModuleMUI<ModuleExtractor> {

    public ModuleExtractorMuiDynamic(ModuleExtractor module) {
        this(module, "");
    }

    public ModuleExtractorMuiDynamic(ModuleExtractor module, String prefix) {
        super(module, prefix);
    }

    @Override
    public String getId() {
        return "Extractor_module";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        return addWidgets(widget, null, addPlayerInventory);
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, PanelSyncManager syncManager, boolean addPlayerInventory) {
        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        CurrentModuleRef<ModuleExtractor> ref = CurrentModuleRef.of(syncManager, getFullId(), module);

        BooleanSyncValue included = sync(
                syncManager,
                "_included",
                BooleanSyncValue.class,
                () -> new BooleanSyncValue(() -> ref.get().areItemsIncluded(), v -> ref.get().setItemsIncluded(v))
                        .allowC2S());
        IntSyncValue speed = SpeedWidgets.value(syncManager, getFullId(), ref::get);

        widget.child(
                new Flow(GuiAxis.Y).coverChildrenHeight().fullWidth().childPadding(4)
                        .child(new TextWidget<>(IKey.lang("gui.module.filterItems")).left(4).height(10).top(4))
                        .child(
                                new CycleButtonWidget().value(included)
                                        .overlay(
                                                IKey.lang(
                                                        () -> ref.get().areItemsIncluded() ? "gui.module.whitelist"
                                                                : "gui.module.blacklist"))
                                        .width(50).height(16).top(16).right(6))
                        .child(buildFilterSlots(syncManager, ref))
                        .child(SpeedWidgets.line(speed).asWidget().top(78).right(6)));
        return widget;
    }

    private Flow buildFilterSlots(PanelSyncManager syncManager, CurrentModuleRef<ModuleExtractor> ref) {
        String id = getFullId();
        IItemHandlerModifiable filterInventory = ref.inventory(ModuleExtractor::getFilterInventory);
        Flow col = Flow.col().coverChildren().leftRel(Alignment.TopCenter.x).anchorLeft(Alignment.TopCenter.x)
                .topRel(Alignment.TopCenter.y).anchorTop(Alignment.TopCenter.y).top(16);
        for (int row = 0; row < 3; row++) {
            Flow rowWidget = Flow.row().coverChildren();
            for (int column = 0; column < 3; column++) {
                int slotIndex = row * 3 + column;
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
                rowWidget.child(slotWidget);
            }
            col.child(rowWidget);
        }
        return col;
    }

    @Override
    public int getWidth() {
        return 180;
    }

    @Override
    public int getHeight() {
        return 180;
    }
}
