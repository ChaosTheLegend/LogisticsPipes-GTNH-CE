package logisticspipes.gui.modularUI;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.value.sync.GenericListSyncHandler;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.PhantomItemSlotSH;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.PhantomItemSlot;

/**
 * The widgets of a sink that takes a list of names read off an item (tags, aspects): a slot that shows the names of the
 * item put into it with an Add button, and the list with a remove button on each row. The server changes the list, the
 * client gets it synced. The callers pass suppliers of the module now, so a rebuilt chassis page works on the module in
 * the slot now.
 */
public final class AnalyseListWidgets {

    public static final int WIDTH = 162;
    private static final int LIST_HEIGHT = 50;

    private AnalyseListWidgets() {}

    /**
     * @param syncManager the window's sync manager, or null
     * @param id          unique for the window, e.g. its full id
     * @param inventory   the one-slot inventory the item to read goes into
     * @param namesOf     the names of an item (null: no item)
     * @param add         puts names on the list, runs on the server
     * @return the slot, the names of its item and the Add button in a row
     */
    public static Flow analyseRow(PanelSyncManager syncManager, String id, IItemHandlerModifiable inventory,
            Function<ItemStack, List<String>> namesOf, Consumer<List<String>> add) {
        PhantomItemSlot slotWidget = new PhantomItemSlot();
        if (syncManager != null) {
            slotWidget.syncHandler(
                    syncManager.getOrCreateSyncHandler(
                            id + "_analyse",
                            0,
                            PhantomItemSlotSH.class,
                            () -> new PhantomItemSlotSH(new ModularSlot(inventory, 0))));
        } else {
            slotWidget.slot(inventory, 0);
        }
        Supplier<List<String>> names = () -> namesOf.apply(inventory.getStackInSlot(0));

        InteractionSyncHandler addSync = GenericModuleMUI
                .register(syncManager, id + "_add", 0, InteractionSyncHandler.class, InteractionSyncHandler::new);
        addSync.setOnMousePressed(mouse -> {
            // runs on both sides: only the server changes the list, the client gets it synced
            if (syncManager != null && syncManager.isClient()) return;
            if (mouse.mouseButton == 0) add.accept(names.get());
        });

        return Flow.row().width(WIDTH).coverChildrenHeight().childPadding(4).child(slotWidget)
                .child(new TextWidget<>(IKey.dynamic(() -> String.join(", ", names.get()))).expanded()).child(
                        new ButtonWidget<>().syncHandler(addSync).overlay(IKey.lang("gui.module.add")).width(28)
                                .height(14));
    }

    /**
     * @param syncManager the window's sync manager, or null
     * @param id          unique for the window, e.g. its full id
     * @param maxEntries  the most names the list holds
     * @param entries     the list of the module now
     * @param setEntries  sets the list of the module now (the synced list)
     * @param remove      takes a name off the list, runs on the server
     * @return the list in a darker frame
     */
    @SuppressWarnings("unchecked")
    public static ParentWidget<?> entryList(PanelSyncManager syncManager, String id, int maxEntries,
            Supplier<List<String>> entries, Consumer<List<String>> setEntries, Consumer<String> remove) {
        GenericListSyncHandler<String> entriesSync = null;
        if (syncManager != null) {
            entriesSync = syncManager.getOrCreateSyncHandler(
                    id + "_entries",
                    0,
                    GenericListSyncHandler.class,
                    () -> GenericListSyncHandler.<String>builder().getter(entries).setter(setEntries)
                            .serializer(NetworkUtils::writeStringSafe).deserializer(NetworkUtils::readStringSafe)
                            .immutableCopy().build());
            if (!syncManager.isClient() && entriesSync.isValid()) {
                entriesSync.notifyUpdate();
            }
        }
        GenericListSyncHandler<String> finalSync = entriesSync;
        Supplier<List<String>> shown = finalSync != null ? finalSync::getValue : entries;

        // only the rows with a value take room, so the list scrolls only with more values than fit; no scroll shadows,
        // they darkened the edges of the box
        ListWidget<IWidget, ?> list = new ListWidget<>().width(WIDTH - 4).height(LIST_HEIGHT - 2).pos(2, 1)
                .collapseDisabledChild().showScrollShadows(false);
        for (int i = 0; i < maxEntries; i++) {
            int idx = i;
            InteractionSyncHandler removeSync = GenericModuleMUI.register(
                    syncManager,
                    id + "_remove",
                    idx,
                    InteractionSyncHandler.class,
                    InteractionSyncHandler::new);
            removeSync.setOnMousePressed(mouse -> {
                // runs on both sides: only the server changes the list, the client gets it synced
                if (syncManager != null && syncManager.isClient()) return;
                if (mouse.mouseButton != 0) return;
                String value = entryAt(shown, idx);
                if (!value.isEmpty()) remove.accept(value);
            });
            list.child(
                    Flow.row().coverChildren().mainAxisAlignment(Alignment.MainAxis.START).height(12).childPadding(4)
                            .setEnabledIf(w -> !entryAt(shown, idx).isEmpty())
                            .child(new TextWidget<>(IKey.dynamic(() -> entryAt(shown, idx))).width(140).height(10))
                            .child(
                                    new ButtonWidget<>().syncHandler(removeSync)
                                            .overlay(GuiTextures.CROSS_TINY.asIcon().size(8)).size(10)));
        }
        // the darker area sits on a frame around the list, so it stays put while the list scrolls
        return new ParentWidget<>().width(WIDTH).height(LIST_HEIGHT).background(new Rectangle().color(0x30000000))
                .child(list);
    }

    private static String entryAt(Supplier<List<String>> entries, int idx) {
        List<String> source = entries.get();
        return source != null && idx < source.size() ? source.get(idx) : "";
    }
}
