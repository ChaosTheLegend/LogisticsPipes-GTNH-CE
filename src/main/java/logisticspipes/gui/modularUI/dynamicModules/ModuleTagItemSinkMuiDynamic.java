package logisticspipes.gui.modularUI.dynamicModules;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.EnumSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import logisticspipes.gui.modularUI.AnalyseListWidgets;
import logisticspipes.gui.modularUI.CurrentModuleRef;
import logisticspipes.gui.modularUI.GenericModuleMUI;
import logisticspipes.modules.ModuleTagItemSink;
import logisticspipes.modules.ModuleTagItemSink.TagMode;
import logisticspipes.utils.item.ItemIdentifier;

/**
 * The tag sink window: the mode, a slot that shows the tags of an item for the mode (Add puts them on the list), and
 * the list. The handlers ask a {@link CurrentModuleRef} for the module, so a rebuilt chassis page works on the module
 * in the slot now.
 */
public class ModuleTagItemSinkMuiDynamic extends GenericModuleMUI<ModuleTagItemSink> {

    public ModuleTagItemSinkMuiDynamic(ModuleTagItemSink module) {
        this(module, "");
    }

    public ModuleTagItemSinkMuiDynamic(ModuleTagItemSink module, String prefix) {
        super(module, prefix);
    }

    @Override
    public String getId() {
        return "module_tag_item_sink";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        return addWidgets(widget, null, addPlayerInventory);
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ParentWidget addWidgets(ParentWidget widget, PanelSyncManager syncManager, boolean addPlayerInventory) {
        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        CurrentModuleRef<ModuleTagItemSink> ref = CurrentModuleRef.of(syncManager, getFullId(), module);

        EnumSyncValue mode = sync(
                syncManager,
                "_mode",
                EnumSyncValue.class,
                () -> new EnumSyncValue<>(TagMode.class, () -> ref.get().getMode(), m -> ref.get().setMode(m))
                        .allowC2S());

        String id = getFullId();
        widget.child(
                new Flow(GuiAxis.Y).coverChildren().left(9).top(4).childPadding(2)
                        .crossAxisAlignment(Alignment.CrossAxis.START)
                        .child(
                                Flow.row().width(AnalyseListWidgets.WIDTH).height(12)
                                        .child(new TextWidget<>(IKey.lang("gui.module.ModuleTagItemSink")).expanded())
                                        .child(
                                                new CycleButtonWidget().value(mode)
                                                        .overlay(IKey.dynamic(() -> ref.get().getMode().label))
                                                        .width(80).height(12)))
                        .child(
                                AnalyseListWidgets.analyseRow(
                                        syncManager,
                                        id,
                                        ref.inventory(ModuleTagItemSink::getAnalyseInventory),
                                        stack -> ref.get().getTagsOf(stack == null ? null : ItemIdentifier.get(stack)),
                                        tags -> ref.get().addEntries(tags)))
                        .child(
                                AnalyseListWidgets.entryList(
                                        syncManager,
                                        id,
                                        ModuleTagItemSink.MAX_ENTRIES,
                                        () -> ref.get().entries,
                                        v -> ref.get().setEntries(v),
                                        value -> ref.get().removeEntry(value))));
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
