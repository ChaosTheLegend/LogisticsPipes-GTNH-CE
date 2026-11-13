package logisticspipes.gui.modularUI.dynamicModules;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.EnumSyncValue;
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
import logisticspipes.logisticspipes.ExtractionMode;
import logisticspipes.modules.ModuleProvider;

/**
 * The provider window: the 9-slot filter with whitelist / blacklist, the extraction mode, and its speed. The handlers
 * ask a {@link CurrentModuleRef} for the module, so a rebuilt chassis page works on the module in the slot now.
 */
public class ModuleProviderMuiDynamic extends GenericModuleMUI<ModuleProvider> {

    public ModuleProviderMuiDynamic(ModuleProvider module) {
        this(module, "");
    }

    public ModuleProviderMuiDynamic(ModuleProvider module, String prefix) {
        super(module, prefix);
    }

    @Override
    public String getId() {
        return "Provider_module";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        return addWidgets(widget, null, addPlayerInventory);
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, PanelSyncManager syncManager, boolean addPlayerInventory) {
        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        CurrentModuleRef<ModuleProvider> ref = CurrentModuleRef.of(syncManager, getFullId(), module);

        @SuppressWarnings({ "unchecked", "rawtypes" })
        EnumSyncValue extractionMode = sync(
                syncManager,
                "_extraction_mode",
                EnumSyncValue.class,
                () -> new EnumSyncValue<>(
                        ExtractionMode.class,
                        () -> ref.get().getExtractionMode(),
                        mode -> ref.get().setExtractionMode(mode.ordinal())).allowC2S());
        BooleanSyncValue excludeFilter = sync(
                syncManager,
                "_exclude_filter",
                BooleanSyncValue.class,
                () -> new BooleanSyncValue(() -> ref.get().isExcludeFilter(), v -> ref.get().setFilterExcluded(v))
                        .allowC2S());
        IntSyncValue speed = SpeedWidgets.value(syncManager, getFullId(), ref::get);

        widget.child(
                new Flow(GuiAxis.Y).coverChildrenHeight().fullWidth().childPadding(4)
                        .child(new TextWidget<>(IKey.lang("gui.module.filterItems")).left(4).height(10).top(4))
                        .child(
                                new CycleButtonWidget().value(extractionMode)
                                        .overlay(IKey.lang(() -> switch (ref.get().getExtractionMode()) {
                                        case Normal -> "gui.module.ModuleProvider.mode.normal";
                                        case LeaveFirst -> "gui.module.ModuleProvider.mode.leaveFirst";
                                        case LeaveLast -> "gui.module.ModuleProvider.mode.leaveLast";
                                        case LeaveFirstAndLast -> "gui.module.ModuleProvider.mode.leaveFirstAndLast";
                                        case Leave1PerStack -> "gui.module.ModuleProvider.mode.leave1PerStack";
                                        case Leave1PerType -> "gui.module.ModuleProvider.mode.leave1PerType";
                                        })).width(80).height(22).top(72).left(6))
                        .child(
                                new CycleButtonWidget().value(excludeFilter)
                                        .overlay(
                                                IKey.lang(
                                                        () -> !ref.get().isExcludeFilter() ? "gui.module.whitelist"
                                                                : "gui.module.blacklist"))
                                        .width(50).height(16).top(16).right(6))
                        .child(buildFilterSlots(syncManager, ref))
                        .child(SpeedWidgets.line(speed).asWidget().top(78).right(6)));
        return widget;
    }

    private Flow buildFilterSlots(PanelSyncManager syncManager, CurrentModuleRef<ModuleProvider> ref) {
        String id = getFullId();
        IItemHandlerModifiable filterInventory = ref.inventory(ModuleProvider::getFilterInventory);
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
