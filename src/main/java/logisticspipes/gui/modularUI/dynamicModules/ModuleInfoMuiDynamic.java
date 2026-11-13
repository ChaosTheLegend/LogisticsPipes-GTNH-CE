package logisticspipes.gui.modularUI.dynamicModules;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import logisticspipes.gui.modularUI.CurrentModuleRef;
import logisticspipes.gui.modularUI.GenericModuleMUI;
import logisticspipes.gui.modularUI.SpeedWidgets;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.upgrades.SpeedUpgrade;

/**
 * The window of a module that has nothing to configure: a line that says what the module does, and its speed if it
 * takes speed upgrades. It gives such a module a chassis page and, in hand, its upgrade slots.
 */
public class ModuleInfoMuiDynamic extends GenericModuleMUI<LogisticsModule> {

    private static final SpeedUpgrade SPEED = new SpeedUpgrade();
    private static final int TEXT_WIDTH = 168;

    private final String langKey;

    public ModuleInfoMuiDynamic(LogisticsModule module, String prefix, String langKey) {
        super(module, prefix);
        this.langKey = langKey;
    }

    /**
     * The window with the line under "gui.module.&lt;module class&gt;.info".
     */
    public static ModuleInfoMuiDynamic of(LogisticsModule module, String prefix) {
        return new ModuleInfoMuiDynamic(module, prefix, "gui.module." + module.getClass().getSimpleName() + ".info");
    }

    @Override
    public String getId() {
        return "module_info";
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, PanelSyncManager syncManager, boolean addPlayerInventory) {

        if (addPlayerInventory) widget.child(SlotGroupWidget.playerInventory(true));

        // one column, so the speed line follows the text however many lines that takes
        Flow column = new Flow(GuiAxis.Y).width(TEXT_WIDTH).coverChildrenHeight().left(6).top(12).childPadding(6)
                .child(new TextWidget<>(IKey.lang(langKey)).width(TEXT_WIDTH).textAlign(Alignment.Center));

        if (takesSpeed(module)) {
            CurrentModuleRef<LogisticsModule> ref = CurrentModuleRef.of(syncManager, getFullId(), module);
            IntSyncValue speed = SpeedWidgets.value(syncManager, getFullId(), ref::get);
            column.child(new TextWidget<>(SpeedWidgets.line(speed)).width(TEXT_WIDTH).textAlign(Alignment.Center));
        }
        widget.child(column);

        return widget;
    }

    /**
     * @return true if the module takes speed upgrades, its window then shows the speed line
     */
    public static boolean takesSpeed(LogisticsModule module) {
        return SPEED.isAllowedForModule(module);
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        return addWidgets(widget, null, addPlayerInventory);
    }

    @Override
    public int getWidth() {
        return 180;
    }

    /**
     * Room for a line of up to four lines and the speed line above the player inventory.
     */
    @Override
    public int getHeight() {
        return 152;
    }
}
