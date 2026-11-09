package logisticspipes.gui.modularUI.dynamicModules;

import java.util.function.Supplier;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.EnumSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import logisticspipes.gui.modularUI.CurrentModuleRef;
import logisticspipes.gui.modularUI.GenericModuleMUI;
import logisticspipes.modules.ModuleApiaristSink;
import logisticspipes.modules.ModuleApiaristSink.FilterType;
import logisticspipes.modules.ModuleApiaristSink.SinkSetting;
import logisticspipes.proxy.SimpleServiceLocator;

/**
 * The bee sink window: the filters in use (the module has six, an unused one has the type off), "+ Add" turns on a free
 * one, the cross turns one off. Each filter has a type, one or two species and a group. The type and species buttons go
 * to the next value on a left click and the previous on a right click (a middle click sets a species back to any), as
 * in the old window; the server changes them, it knows the species the player has discovered. Filters of one group
 * (1-6) have to match together, group 0 filters match alone.
 */
public class ModuleApiaristSinkMuiDynamic extends GenericModuleMUI<ModuleApiaristSink> {

    private static final int GROUPS = 7;
    private static final int ROW_WIDTH = 170;
    private static final String LANG = "gui.module.ModuleApiaristSink.";

    public ModuleApiaristSinkMuiDynamic(ModuleApiaristSink module) {
        this(module, "");
    }

    public ModuleApiaristSinkMuiDynamic(ModuleApiaristSink module, String prefix) {
        super(module, prefix);
    }

    @Override
    public String getId() {
        return "module_apiarist_sink";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        return addWidgets(widget, null, addPlayerInventory);
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, PanelSyncManager syncManager, boolean addPlayerInventory) {
        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        CurrentModuleRef<ModuleApiaristSink> ref = CurrentModuleRef.of(syncManager, getFullId(), module);

        // only the filters in use are listed; they scroll, a chassis page has room for about four
        ListWidget<IWidget, ?> list = new ListWidget<>().width(ROW_WIDTH + 2).height(66).collapseDisabledChild()
                .showScrollShadows(false);
        for (int i = 0; i < module.filter.length; i++) {
            int index = i;
            list.child(
                    buildRow(syncManager, ref, i)
                            .setEnabledIf(w -> ref.get().filter[index].filterType != FilterType.Null));
        }

        // turns on the first unused filter, on the server
        InteractionSyncHandler add = sync(
                syncManager,
                "_add",
                0,
                InteractionSyncHandler.class,
                InteractionSyncHandler::new);
        add.setOnMousePressed(mouse -> {
            // the server changes it, the client gets it synced
            if (syncManager != null && syncManager.isClient()) return;
            if (mouse.mouseButton != 0) return;
            for (SinkSetting setting : ref.get().filter) {
                if (setting.filterType == FilterType.Null) {
                    reset(setting);
                    setting.filterType = FilterType.BeeAllele;
                    return;
                }
            }
        });

        // "+ Add" on the title line: below the list it would sit under the player inventory of the window in hand
        Flow title = Flow.row().width(ROW_WIDTH + 2).height(14)
                .child(new TextWidget<>(IKey.lang(LANG + "filters")).expanded()).child(
                        new ButtonWidget<>().syncHandler(add).overlay(IKey.lang(LANG + "add")).width(36).height(12)
                                .setEnabledIf(w -> hasFreeFilter(ref.get())));

        widget.child(
                new Flow(GuiAxis.Y).coverChildren().left(4).top(4).childPadding(2)
                        .crossAxisAlignment(Alignment.CrossAxis.START).child(title).child(list));
        return widget;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Flow buildRow(PanelSyncManager syncManager, CurrentModuleRef<ModuleApiaristSink> ref, int index) {
        Supplier<SinkSetting> setting = () -> ref.get().filter[index];

        // type and species only go server to client, the buttons change them on the server; the group is changed by
        // its cycle button on the client and sent up (allowC2S)
        EnumSyncValue type = sync(
                syncManager,
                "_type",
                index,
                EnumSyncValue.class,
                () -> new EnumSyncValue<>(
                        FilterType.class,
                        () -> setting.get().filterType,
                        t -> setting.get().filterType = t));
        IntSyncValue group = sync(
                syncManager,
                "_group",
                index,
                IntSyncValue.class,
                () -> new IntSyncValue(() -> setting.get().filterGroup, g -> setting.get().filterGroup = g).allowC2S());
        StringSyncValue first = sync(
                syncManager,
                "_first",
                index,
                StringSyncValue.class,
                () -> new StringSyncValue(() -> setting.get().firstBee));
        StringSyncValue second = sync(
                syncManager,
                "_second",
                index,
                StringSyncValue.class,
                () -> new StringSyncValue(() -> setting.get().secondBee));

        if (syncManager != null && !syncManager.isClient()) {
            // values no setting has: the server sends the real ones with the next update (a chassis page built later
            // gets no first sync of its own for values no widget carries)
            type.setValue(null, false, false);
            first.setStringValue("\u0000", false, false);
            second.setStringValue("\u0000", false, false);
        }

        // next type on a left click, previous on a right click; off is skipped, the cross turns a filter off
        InteractionSyncHandler typeClick = sync(
                syncManager,
                "_type_click",
                index,
                InteractionSyncHandler.class,
                InteractionSyncHandler::new);
        typeClick.setOnMousePressed(mouse -> {
            // the server changes it, the client gets it synced
            if (syncManager != null && syncManager.isClient()) return;
            SinkSetting s = setting.get();
            if (s.filterType == FilterType.Null) return;
            if (mouse.mouseButton == 0) {
                do {
                    s.FilterTypeUp();
                } while (s.filterType == FilterType.Null);
            } else if (mouse.mouseButton == 1) {
                do {
                    s.FilterTypeDown();
                } while (s.filterType == FilterType.Null);
            }
        });

        InteractionSyncHandler remove = sync(
                syncManager,
                "_remove",
                index,
                InteractionSyncHandler.class,
                InteractionSyncHandler::new);
        remove.setOnMousePressed(mouse -> {
            // the server changes it, the client gets it synced
            if (syncManager != null && syncManager.isClient()) return;
            if (mouse.mouseButton == 0) reset(setting.get());
        });

        return Flow.row().width(ROW_WIDTH).height(16).childPadding(2).collapseDisabledChild()
                .mainAxisAlignment(Alignment.MainAxis.START)
                .child(
                        new ButtonWidget<>().syncHandler(typeClick).width(48).height(14)
                                .overlay(IKey.lang(() -> LANG + "type." + setting.get().filterType.name()).scale(0.8f)))
                .child(speciesButton(syncManager, setting, index, true, first))
                .child(
                        speciesButton(syncManager, setting, index, false, second)
                                .setEnabledIf(w -> setting.get().filterType.secondSlots == 2))
                .child(
                        new CycleButtonWidget().value(group).stateCount(GROUPS).width(14).height(14).overlay(
                                IKey.dynamic(
                                        () -> setting.get().filterGroup == 0 ? "-"
                                                : Integer.toString(setting.get().filterGroup))))
                .child(
                        new ButtonWidget<>().syncHandler(remove).overlay(GuiTextures.CROSS_TINY.asIcon().size(8))
                                .size(10));
    }

    // a filter back to unused: off, any species, no group
    private static void reset(SinkSetting setting) {
        setting.filterType = FilterType.Null;
        setting.firstBeeReset();
        setting.secondBeeReset();
        setting.filterGroupReset();
    }

    private static boolean hasFreeFilter(ModuleApiaristSink module) {
        for (SinkSetting setting : module.filter) {
            if (setting.filterType == FilterType.Null) {
                return true;
            }
        }
        return false;
    }

    private ButtonWidget<?> speciesButton(PanelSyncManager syncManager, Supplier<SinkSetting> setting, int index,
            boolean firstSpecies, StringSyncValue species) {
        InteractionSyncHandler click = sync(
                syncManager,
                firstSpecies ? "_first_click" : "_second_click",
                index,
                InteractionSyncHandler.class,
                InteractionSyncHandler::new);
        click.setOnMousePressed(mouse -> {
            // the server changes it, the client gets it synced
            if (syncManager != null && syncManager.isClient()) return;
            SinkSetting s = setting.get();
            switch (mouse.mouseButton) {
                case 0 -> {
                    if (firstSpecies) s.firstBeeUp();
                    else s.secondBeeUp();
                }
                case 1 -> {
                    if (firstSpecies) s.firstBeeDown();
                    else s.secondBeeDown();
                }
                case 2 -> {
                    if (firstSpecies) s.firstBeeReset();
                    else s.secondBeeReset();
                }
                default -> {}
            }
        });
        return new ButtonWidget<>().syncHandler(click).width(44).height(14)
                .overlay(IKey.dynamic(() -> speciesName(species.getStringValue())).scale(0.7f));
    }

    private static String speciesName(String uid) {
        if (uid == null || uid.isEmpty() || uid.equals("\u0000")) {
            return StatCollector.translateToLocal(LANG + "any");
        }
        String name = SimpleServiceLocator.forestryProxy.getAlleleName(uid);
        return name == null || name.isEmpty() ? uid : name;
    }

    @Override
    public int getWidth() {
        return 180;
    }

    @Override
    public int getHeight() {
        return 170;
    }
}
