package logisticspipes.gui.modularUI;

import java.util.function.IntFunction;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.DragHandle;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import logisticspipes.api.IMUICompatibleModule;
import logisticspipes.compat.ModularUIHelper;
import logisticspipes.items.ItemUpgrade;
import logisticspipes.pipes.basic.CoreRoutedPipe;

public class PipeGuiFactory {

    public static final ResourceLocation UpgradeSlotTexture = new ResourceLocation(
            "logisticspipes",
            "textures/gui/upgrade_slot.png");
    public static final int UPGRADE_GUI_WIDTH = 24;

    /*
     * Factory method to create a LogisticsMUIGui instance for a given pipe and module. Automatically adds upgrade
     * sidebar to the module UI
     * @param pipe The CoreRoutedPipe instance for which the GUI is being created.
     * @param module The IMUICompatibleModule instance associated with the pipe.
     * @return A LogisticsMUIGui instance for the specified pipe and module.
     */
    public static LogisticsModularUI fromModule(CoreRoutedPipe pipe, IMUICompatibleModule module) {
        return new GenericPipeLogisticsGui(module, pipe);
    }

    public static boolean isUpgradeItem(ItemStack stack) {
        if (stack == null) return false;

        return (stack.getItem() instanceof ItemUpgrade);
    }

    public static void addUpgradeGui(ModularPanel panel, IItemHandlerModifiable upgradeHandler) {
        panel.child(getUpgradeGui(upgradeHandler));
    }

    private static DragHandle dragHandle() {
        return new DragHandle().fullWidth().height(6).background(new Rectangle().color(0x80FFFFFF).solid())
                .tooltipBuilder(tooltip -> tooltip.addLine("Drag to move"));
    }

    public static ParentWidget getUpgradeGui(IItemHandlerModifiable upgradeHandler, PanelSyncManager syncManager) {
        return getUpgradeGui(
                syncManager,
                i -> new ModularSlot(upgradeHandler, i).filter(PipeGuiFactory::isUpgradeItem));
    }

    /*
     * Same as getUpgradeGui(IItemHandlerModifiable, PanelSyncManager), for callers that need their own slots (a
     * narrower filter, a change listener, a different slot class). The factory is called for the 4 slot indices; the
     * slot group and accessibility are set here.
     */
    public static ParentWidget getUpgradeGui(PanelSyncManager syncManager, IntFunction<ModularSlot> slotFactory) {

        syncManager.registerSlotGroup("upgrade_inventory", 4);

        var column = new DraggableFlow(GuiAxis.Y).background(ModularUIHelper.BACKGROUND_TEXTURE).width(24)
                .child(dragHandle())
                .child(
                        SlotGroupWidget.builder().row("I").row("I").row("I").row("I").key(
                                'I',
                                i -> new ItemSlot().slot(
                                        slotFactory.apply(i).slotGroup("upgrade_inventory").accessibility(true, true))
                                        .background(UITexture.fullImage(UpgradeSlotTexture)))
                                .build())
                .padding(4).coverChildrenHeight();

        return (ParentWidget) column;
    }

    public static ParentWidget getUpgradeGui(IItemHandlerModifiable upgradeHandler) {
        var column = new DraggableFlow(GuiAxis.Y).background(ModularUIHelper.BACKGROUND_TEXTURE).width(24)
                .child(dragHandle())
                .child(
                        SlotGroupWidget.builder().row("I").row("I").row("I").row("I").key(
                                'I',
                                i -> new ItemSlot()
                                        .slot(
                                                new ModularSlot(upgradeHandler, i).filter(PipeGuiFactory::isUpgradeItem)
                                                        .accessibility(true, true))
                                        .background(UITexture.fullImage(UpgradeSlotTexture)))
                                .build())
                .padding(4).coverChildrenHeight();

        return (ParentWidget) column;
    }

    public static LogisticsModularUI fromMui(LogisticsPipeMUI pipeMui) {
        return new GenericSimplePipeLogisticsGui(pipeMui);
    }
}
