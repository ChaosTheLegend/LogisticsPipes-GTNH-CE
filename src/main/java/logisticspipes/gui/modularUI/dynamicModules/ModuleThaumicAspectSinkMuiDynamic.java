package logisticspipes.gui.modularUI.dynamicModules;

import java.util.function.Supplier;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import logisticspipes.gui.modularUI.AnalyseListWidgets;
import logisticspipes.gui.modularUI.CurrentModuleRef;
import logisticspipes.gui.modularUI.GenericModuleMUI;
import logisticspipes.modules.ModuleThaumicAspectSink;

/**
 * The aspect sink window: a slot that shows the aspects of an item the player has scanned (Add puts them on the list),
 * and the list. The handlers ask a {@link CurrentModuleRef} for the module, so a rebuilt chassis page works on the
 * module in the slot now.
 */
public class ModuleThaumicAspectSinkMuiDynamic extends GenericModuleMUI<ModuleThaumicAspectSink> {

    public ModuleThaumicAspectSinkMuiDynamic(ModuleThaumicAspectSink module) {
        this(module, "");
    }

    public ModuleThaumicAspectSinkMuiDynamic(ModuleThaumicAspectSink module, String prefix) {
        super(module, prefix);
    }

    @Override
    public String getId() {
        return "module_thaumic_aspect_sink";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        return addWidgets(widget, null, addPlayerInventory);
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, PanelSyncManager syncManager, boolean addPlayerInventory) {
        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        CurrentModuleRef<ModuleThaumicAspectSink> ref = CurrentModuleRef.of(syncManager, getFullId(), module);
        // Thaumcraft shows the aspects of an item only to a player who scanned it. Asked when needed: while the window
        // is built the sync manager has no player yet
        Supplier<String> playerName = () -> syncManager == null ? null : syncManager.getPlayer().getCommandSenderName();

        String id = getFullId();
        widget.child(
                new Flow(GuiAxis.Y).coverChildren().left(9).top(4).childPadding(2)
                        .crossAxisAlignment(Alignment.CrossAxis.START)
                        .child(new TextWidget<>(IKey.lang("gui.module.ModuleThaumicAspectSink.title")))
                        .child(
                                AnalyseListWidgets.analyseRow(
                                        syncManager,
                                        id,
                                        ref.inventory(ModuleThaumicAspectSink::getAnalyseInventory),
                                        stack -> ModuleThaumicAspectSink
                                                .aspectsOf(stack, stack == null ? null : playerName.get()),
                                        aspects -> ref.get().addAspects(aspects)))
                        .child(
                                AnalyseListWidgets.entryList(
                                        syncManager,
                                        id,
                                        ModuleThaumicAspectSink.MAX_ASPECTS,
                                        () -> ref.get().aspectList,
                                        v -> ref.get().setAspects(v),
                                        aspect -> ref.get().removeAspect(aspect))));
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
