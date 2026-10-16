package logisticspipes.gui.modularUI.pipes.patterncrafting;

import static logisticspipes.gui.modularUI.pipes.patterncrafting.PatternEditor.LANG;
import static logisticspipes.gui.modularUI.pipes.patterncrafting.PatternEditor.TEXT_COLOR;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.factory.inventory.InventoryTypes;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ScrollingTextWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import logisticspipes.compat.ModularUIHelper;
import logisticspipes.crafting.pattern.PatternSource;

/**
 * ModularUI GUI of a pattern item held by the player: the same {@link PatternEditor} as the pattern crafting pipe,
 * working directly on the held item's NBT, above the player inventory. NEI recipes can be transferred into it, see
 * {@link PatternCraftingContainer}.
 */
public class HandheldPatternMui {

    private static final int WIDTH = 196;
    private static final int HEIGHT = 196;
    private static final int EDITOR_X = 7;
    private static final int EDITOR_Y = 17;
    private static final int STATUS_Y = 100;

    private final PlayerInventoryGuiData data;

    private PatternEditorState state;

    public HandheldPatternMui(PlayerInventoryGuiData data) {
        this.data = data;
    }

    public ModularPanel buildUI(PanelSyncManager syncManager, UISettings settings) {
        state = new PatternEditorState(PatternSource.heldBy(data.getPlayer(), data.getSlotIndex()), 0);
        PatternEditorSyncHandler actions = new PatternEditorSyncHandler(state);
        syncManager.syncValue("pattern_editor", actions);
        lockHeldSlot(syncManager);
        if (settings != null && syncManager.isClient()) {
            // only the client needs the NEI hooks; the server keeps the default container (slot sync is identical)
            settings.customContainer(PatternCraftingContainer.supplier(actions));
        }

        ModularPanel panel = ModularPanel.defaultPanel("handheld_pattern", WIDTH, HEIGHT)
                .background(ModularUIHelper.BACKGROUND_TEXTURE);
        panel.child(IKey.lang("gui.pattern.title").asWidget().pos(8, 6).color(TEXT_COLOR));
        new PatternEditor(state, actions, EDITOR_X, EDITOR_Y).addTo(panel);
        panel.child(
                new ScrollingTextWidget(IKey.dynamic(this::statusText)).pos(8, STATUS_Y).size(WIDTH - 16, 10)
                        .color(TEXT_COLOR));
        panel.child(SlotGroupWidget.playerInventory(true));
        return panel;
    }

    /**
     * The edited pattern is the item in the player's slot, so that slot must not be picked up or swapped while the GUI
     * is open. MUI only binds the player inventory itself when the GUI didn't.
     */
    private void lockHeldSlot(PanelSyncManager syncManager) {
        if (data.getInventoryType() != InventoryTypes.PLAYER) {
            return;
        }
        int heldSlot = data.getSlotIndex();
        syncManager.bindPlayerInventory(
                data.getPlayer(),
                (inventory, index) -> index == heldSlot ? new ModularSlot(inventory, index).accessibility(false, false)
                        : new ModularSlot(inventory, index));
    }

    private String statusText() {
        if (!state.hasPattern()) {
            return StatCollector.translateToLocal("gui.pattern.status.gone");
        }
        if (!state.getPattern().isConfigured()) {
            return StatCollector.translateToLocal(LANG + "status.unconfigured");
        }
        return StatCollector.translateToLocal("gui.pattern.status.ready");
    }
}
