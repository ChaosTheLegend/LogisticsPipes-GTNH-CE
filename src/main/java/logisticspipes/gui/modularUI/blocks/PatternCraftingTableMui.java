package logisticspipes.gui.modularUI.blocks;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;

import logisticspipes.compat.ModularUIHelper;
import logisticspipes.crafting.PatternLogisticsCraftingTableTileEntity;
import logisticspipes.gui.modularUI.PipeGuiFactory;
import logisticspipes.gui.modularUI.SimpleInventorySlot;

/**
 * ModularUI GUI of the pattern crafting table: a 3x3 input grid, a progress arrow and three output slots above the
 * player inventory, with the upgrade column (speed upgrades only) on the right side.
 */
public class PatternCraftingTableMui {

    private static final int WIDTH = 176;
    private static final int HEIGHT = 184;
    private static final int SLOT = 18;
    private static final int UPGRADE_GAP = 4;

    private static final int INPUT_X = 25;
    private static final int INPUT_Y = 17;
    private static final int PROGRESS_X = 88;
    private static final int PROGRESS_Y = 35;
    private static final int OUTPUT_X = 115;
    private static final int OUTPUT_Y = 35;

    private static final int TEXT_COLOR = 0x404040;
    private static final String INPUT_GROUP = "pattern_table_input";
    private static final String OUTPUT_GROUP = "pattern_table_output";
    private static final String LANG = "gui.patterncraftingtable.";

    private final PatternLogisticsCraftingTableTileEntity tile;

    public PatternCraftingTableMui(PatternLogisticsCraftingTableTileEntity tile) {
        this.tile = tile;
    }

    public ModularPanel buildUI(PanelSyncManager syncManager) {
        // below the upgrade group on shift-click, so speed upgrades don't land in the crafting grid
        syncManager.registerSlotGroup(new SlotGroup(INPUT_GROUP, 3, SlotGroup.STORAGE_SLOT_PRIO - 10, true));
        syncManager.registerSlotGroup(new SlotGroup(OUTPUT_GROUP, 3, SlotGroup.STORAGE_SLOT_PRIO, false));

        ModularPanel panel = ModularPanel
                .defaultPanel("pattern_crafting_table", WIDTH + UPGRADE_GAP + PipeGuiFactory.UPGRADE_GUI_WIDTH, HEIGHT)
                .background(IDrawable.EMPTY);

        ParentWidget<?> main = new ParentWidget<>().pos(0, 0).size(WIDTH, HEIGHT)
                .background(ModularUIHelper.BACKGROUND_TEXTURE);
        main.child(IKey.lang(LANG + "title").asWidget().pos(8, 6).color(TEXT_COLOR));

        IItemHandlerModifiable input = new InvWrapper(tile.getInputInventory());
        for (int i = 0; i < 9; i++) {
            ModularSlot slot = watched(new SimpleInventorySlot(input, i)).slotGroup(INPUT_GROUP)
                    .filter(tile::canPlayerInsertInput);
            main.child(new ItemSlot().slot(slot).pos(INPUT_X + (i % 3) * SLOT, INPUT_Y + (i / 3) * SLOT));
        }

        main.child(
                new ProgressWidget().value(new DoubleSyncValue(tile::getProgress))
                        .texture(GuiTextures.PROGRESS_ARROW, 20).pos(PROGRESS_X, PROGRESS_Y).size(20, 20));

        IItemHandlerModifiable output = new InvWrapper(tile.getOutputInventory());
        for (int i = 0; i < 3; i++) {
            ModularSlot slot = watched(new SimpleInventorySlot(output, i)).slotGroup(OUTPUT_GROUP)
                    .accessibility(false, true);
            main.child(new ItemSlot().slot(slot).pos(OUTPUT_X + i * SLOT, OUTPUT_Y));
        }

        main.child(SlotGroupWidget.playerInventory(true));
        panel.child(main);

        IItemHandlerModifiable upgrades = new InvWrapper(tile.getUpgradeInventory());
        ParentWidget<?> upgradeColumn = PipeGuiFactory.getUpgradeGui(
                syncManager,
                i -> watched(new SimpleInventorySlot(upgrades, i))
                        .filter(PatternLogisticsCraftingTableTileEntity::isSpeedUpgrade));
        upgradeColumn.top(4).left(WIDTH + UPGRADE_GAP);
        panel.child(upgradeColumn);
        return panel;
    }

    /**
     * MUI changes slots without always calling markDirty (e.g. shift-click merges into an existing stack), so every
     * server-side slot change asks the table to re-check its recipe on its next tick.
     */
    private ModularSlot watched(ModularSlot slot) {
        return slot.changeListener((stack, onlyAmountChanged, client, init) -> {
            if (!client && !init) {
                tile.scheduleInventoryCheck();
            }
        });
    }
}
