package logisticspipes.gui.modularUI.dynamicModules;

import java.util.List;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.GenericListSyncHandler;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.PhantomItemSlotSH;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;

import logisticspipes.gui.modularUI.GenericModuleMUI;
import logisticspipes.modules.ModuleCreativeTabBasedItemSink;
import logisticspipes.utils.item.ItemIdentifier;

public class ModuleCreativeTabBasedItemSinkMuiDynamic extends GenericModuleMUI<ModuleCreativeTabBasedItemSink> {

    private final IItemHandlerModifiable analyseInventory;
    private GenericListSyncHandler<String> tabListSync;

    public ModuleCreativeTabBasedItemSinkMuiDynamic(ModuleCreativeTabBasedItemSink module) {
        this(module, "");
    }

    public ModuleCreativeTabBasedItemSinkMuiDynamic(ModuleCreativeTabBasedItemSink module, String prefix) {
        super(module, prefix);
        analyseInventory = new InvWrapper(module.getAnalyseInventory());
    }

    @Override
    public String getId() {
        return "module_creative_tab_based_item_sink";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, PanelSyncManager syncManager, boolean addPlayerInventory) {

        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        widget.child(
                new Flow(GuiAxis.Y).coverChildren().left(9).top(4).childPadding(3)
                        .crossAxisAlignment(Alignment.CrossAxis.START).child(new TextWidget<>("Creative tab sink"))
                        .child(buildAnalyseRow(syncManager)).child(buildEntryList(syncManager)));

        return widget;
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        return addWidgets(widget, null, addPlayerInventory);
    }

    private Flow buildAnalyseRow(PanelSyncManager syncManager) {
        String id = getFullId();

        PhantomItemSlot slotWidget = new PhantomItemSlot();
        InteractionSyncHandler addSyncHandler;
        if (syncManager != null) {
            PhantomItemSlotSH slotSH = syncManager.getOrCreateSyncHandler(
                    id + "_analyse",
                    0,
                    PhantomItemSlotSH.class,
                    () -> new PhantomItemSlotSH(new ModularSlot(analyseInventory, 0)));
            slotWidget.syncHandler(slotSH);
            addSyncHandler = syncManager
                    .getOrCreateSyncHandler(id + "_add", 0, InteractionSyncHandler.class, InteractionSyncHandler::new);
        } else {
            slotWidget.slot(analyseInventory, 0);
            addSyncHandler = new InteractionSyncHandler();
        }
        addSyncHandler.setOnMousePressed(i -> {
            if (i.mouseButton != 0) return;
            module.addTab(getPhantomTabName());
        });

        return Flow.row().fullWidth().coverChildrenHeight().childPadding(4).child(slotWidget)
                .child(new TextWidget<>(IKey.dynamic(this::getPhantomTabName)).expanded())
                .child(new ButtonWidget<>().syncHandler(addSyncHandler).overlay(IKey.lang("Add")).width(40).height(16));
    }

    private String getPhantomTabName() {
        var stack = analyseInventory.getStackInSlot(0);
        if (stack == null) return "";

        return ItemIdentifier.get(stack).getCreativeTabName();
    }

    private ListWidget<IWidget, ?> buildEntryList(PanelSyncManager syncManager) {
        String id = getFullId();

        if (syncManager != null) {
            tabListSync = syncManager.getOrCreateSyncHandler(
                    id + "_tablist",
                    0,
                    GenericListSyncHandler.class,
                    () -> GenericListSyncHandler.<String>builder().getter(() -> module.tabList).setter(v -> {
                        module.tabList.clear();
                        module.tabList.addAll(v);
                    }).serializer(NetworkUtils::writeStringSafe).deserializer(NetworkUtils::readStringSafe)
                            .immutableCopy().build());
            if (!syncManager.isClient() && tabListSync.isValid()) {
                tabListSync.notifyUpdate();
            }
        }

        ListWidget<IWidget, ?> list = new ListWidget<>().width(160).height(56);
        for (int i = 0; i < ModuleCreativeTabBasedItemSink.MAX_ENTRIES; i++) {
            int idx = i;
            InteractionSyncHandler removeSyncHandler = syncManager != null ? syncManager.getOrCreateSyncHandler(
                    id + "_remove",
                    idx,
                    InteractionSyncHandler.class,
                    InteractionSyncHandler::new) : new InteractionSyncHandler();
            removeSyncHandler.setOnMousePressed(btn -> {
                if (btn.mouseButton != 0) return;
                String tab = entryAt(idx);
                if (!tab.isEmpty()) module.removeTab(tab);
            });
            list.child(
                    Flow.row().coverChildren().mainAxisAlignment(Alignment.MainAxis.START).height(12).childPadding(4)
                            .child(new TextWidget<>(IKey.dynamic(() -> entryAt(idx))).width(140).height(10)).child(
                                    new ButtonWidget<>().syncHandler(removeSyncHandler)
                                            .overlay(GuiTextures.CROSS_TINY.asIcon().size(8)).size(10)));
        }
        return list;
    }

    private String entryAt(int idx) {
        List<String> source = tabListSync != null ? tabListSync.getValue() : module.tabList;
        return idx < source.size() ? source.get(idx) : "";
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
