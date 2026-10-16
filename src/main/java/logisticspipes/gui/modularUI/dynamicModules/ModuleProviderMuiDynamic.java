package logisticspipes.gui.modularUI.dynamicModules;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.*;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;

import logisticspipes.gui.modularUI.GenericModuleMUI;
import logisticspipes.logisticspipes.ExtractionMode;
import logisticspipes.modules.ModuleProvider;

public class ModuleProviderMuiDynamic extends GenericModuleMUI<ModuleProvider> {

    private final IItemHandlerModifiable filterInventory;

    public ModuleProviderMuiDynamic(ModuleProvider module) {
        this(module, "");
    }

    public ModuleProviderMuiDynamic(ModuleProvider module, String prefix) {
        super(module, prefix);

        filterInventory = new InvWrapper(module.getFilterInventory());
    }

    @Override
    public String getId() {
        return "Provider_module";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, PanelSyncManager syncManager, boolean addPlayerInventory) {

        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        EnumSyncValue extractionModeSync = syncManager.getOrCreateSyncHandler(
                getFullId() + "_extraction_mode",
                EnumSyncValue.class,
                () -> new EnumSyncValue<>(
                        ExtractionMode.class,
                        module::getExtractionMode,
                        i -> module.setExtractionMode(i.ordinal())).allowC2S());
        BooleanSyncValue excludeFilterSync = syncManager.getOrCreateSyncHandler(
                getFullId() + "_exclude_filter",
                BooleanSyncValue.class,
                () -> new BooleanSyncValue(module::isExcludeFilter, module::setFilterExcluded)).allowC2S();

        widget.child(
                new Flow(GuiAxis.Y).coverChildrenHeight().fullWidth().childPadding(4)
                        .child(new TextWidget<>("Filter items").left(4).height(10).top(4))
                        .child(
                                new CycleButtonWidget().value(extractionModeSync)
                                        .overlay(IKey.lang(() -> switch (module.getExtractionMode()) {
                                        case Normal -> "Mode: Normal";
                                        case LeaveFirst -> "Mode: Leave first";
                                        case LeaveLast -> "Mode: Leave last";
                                        case LeaveFirstAndLast -> "Mode: Leave first and last";
                                        case Leave1PerStack -> "Mode: Leave 1 per stack";
                                        case Leave1PerType -> "Mode: Leave 1 per type";
                                        })).width(80).height(22).top(72).left(6))
                        .child(
                                new CycleButtonWidget().value(excludeFilterSync)
                                        .overlay(IKey.lang(() -> !module.isExcludeFilter() ? "Whitelist" : "Blacklist"))
                                        .width(50).height(16).top(16).right(6))
                        .child(buildFilterSlots(syncManager)));

        return widget;
    }

    private Flow buildFilterSlots(PanelSyncManager syncManager) {
        String id = getFullId();
        Flow col = Flow.col().coverChildren().leftRel(Alignment.TopCenter.x).anchorLeft(Alignment.TopCenter.x)
                .topRel(Alignment.TopCenter.y).anchorTop(Alignment.TopCenter.y).top(16);
        for (int row = 0; row < 3; row++) {
            Flow rowWidget = Flow.row().coverChildren();
            for (int col2 = 0; col2 < 3; col2++) {
                int slotIndex = row * 3 + col2;
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
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        ModuleProvider moduleProvider = (ModuleProvider) module;

        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        widget.child(
                new Flow(GuiAxis.Y).coverChildrenHeight().fullWidth().childPadding(4)
                        .child(
                                new TextWidget<>("Filter items").left(4).height(10).top(4))
                        .child(
                                new CycleButtonWidget()
                                        .value(
                                                SyncHandlers
                                                        .enumValue(
                                                                ExtractionMode.class,
                                                                moduleProvider::getExtractionMode,
                                                                i -> moduleProvider.setExtractionMode(i.ordinal()))
                                                        .allowC2S())
                                        .overlay(IKey.lang(() -> switch (moduleProvider.getExtractionMode()) {
                                        case Normal -> "Mode: Normal";
                                        case LeaveFirst -> "Mode: Leave first";
                                        case LeaveLast -> "Mode: Leave last";
                                        case LeaveFirstAndLast -> "Mode: Leave first and last";
                                        case Leave1PerStack -> "Mode: Leave 1 per stack";
                                        case Leave1PerType -> "Mode: Leave 1 per type";
                                        })).width(80).height(22).top(72).left(6))
                        .child(
                                new CycleButtonWidget()
                                        .value(
                                                SyncHandlers.bool(
                                                        moduleProvider::isExcludeFilter,
                                                        moduleProvider::setFilterExcluded).allowC2S())
                                        .overlay(
                                                IKey.lang(
                                                        () -> !moduleProvider.isExcludeFilter() ? "Whitelist"
                                                                : "Blacklist"))
                                        .width(50).height(16).top(16).right(6))
                        .child(buildFilterSlots(null)));

        return widget;
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
