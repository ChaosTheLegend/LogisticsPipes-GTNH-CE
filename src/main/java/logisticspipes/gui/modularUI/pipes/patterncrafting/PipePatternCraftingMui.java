package logisticspipes.gui.modularUI.pipes.patterncrafting;

import static logisticspipes.gui.modularUI.pipes.patterncrafting.PatternEditor.BUTTONS_END_Y;
import static logisticspipes.gui.modularUI.pipes.patterncrafting.PatternEditor.BUTTONS_X;
import static logisticspipes.gui.modularUI.pipes.patterncrafting.PatternEditor.BUTTON_WIDTH;
import static logisticspipes.gui.modularUI.pipes.patterncrafting.PatternEditor.LANG;
import static logisticspipes.gui.modularUI.pipes.patterncrafting.PatternEditor.SLOT;
import static logisticspipes.gui.modularUI.pipes.patterncrafting.PatternEditor.TARGET_X;
import static logisticspipes.gui.modularUI.pipes.patterncrafting.PatternEditor.TARGET_Y;
import static logisticspipes.gui.modularUI.pipes.patterncrafting.PatternEditor.TEXT_COLOR;

import java.util.Locale;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ScrollingTextWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import logisticspipes.compat.ModularUIHelper;
import logisticspipes.crafting.PatternCraftingHudState;
import logisticspipes.crafting.pattern.AbstractPattern;
import logisticspipes.crafting.pattern.PatternSource;
import logisticspipes.crafting.patternStack.PatternStackHelper;
import logisticspipes.gui.modularUI.LogisticsPipeMUI;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;

/**
 * ModularUI GUI of the pattern crafting pipe.
 * <p>
 * Layout, top to bottom: the nine pattern slots (click to select), the {@link PatternEditor} for the selected pattern
 * plus the crafting target and the cancel/return buttons, a status line and the player inventory. NEI recipes can be
 * transferred into the selected pattern, see {@link PatternCraftingContainer}.
 */
public class PipePatternCraftingMui extends LogisticsPipeMUI {

    private static final int WIDTH = 196;
    private static final int HEIGHT = 226;

    private static final int PATTERN_X = 17;
    private static final int PATTERN_Y = 17;
    private static final int EDITOR_X = 7;
    private static final int EDITOR_Y = 38;
    private static final int STATUS_Y = 121;

    private static final String PATTERN_SLOT_GROUP = "pattern_crafting_patterns";

    private final PipeItemsPatternCraftingLogistics patternPipe;

    private PatternEditorState state;
    private PatternCraftingSyncHandler actions;

    public PipePatternCraftingMui(PipeItemsPatternCraftingLogistics pipe) {
        super(pipe);
        this.patternPipe = pipe;
    }

    @Override
    public String getId() {
        return "pipe_pattern_crafting";
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public ParentWidget addWidgets(ParentWidget widget, boolean addPlayerInventory) {
        // the panel is built in getPanel, it needs the sync manager and UI settings
        return widget;
    }

    @Override
    public ModularPanel getPanel(GuiData guiData, PanelSyncManager syncManager) {
        return getPanel(guiData, syncManager, null);
    }

    @Override
    public ModularPanel getPanel(GuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        PatternSource source = PatternSource.of(patternPipe);
        // the client learns the selection from the server once the GUI opened
        int initialSlot = syncManager.isClient() ? 0 : PatternEditorState.findInitialSlot(source, 0);
        state = new PatternEditorState(source, initialSlot);
        actions = new PatternCraftingSyncHandler(state, patternPipe);
        syncManager.syncValue("pattern_crafting", actions);
        syncManager.registerSlotGroup(PATTERN_SLOT_GROUP, PatternEditorState.PATTERN_SLOTS);
        if (settings != null && syncManager.isClient()) {
            // only the client needs the NEI hooks; the server keeps the default container (slot sync is identical)
            settings.customContainer(PatternCraftingContainer.supplier(actions));
        }

        ModularPanel panel = ModularPanel.defaultPanel(getId(), WIDTH, HEIGHT)
                .background(ModularUIHelper.BACKGROUND_TEXTURE);

        panel.child(IKey.lang(LANG + "title").asWidget().pos(8, 6).color(TEXT_COLOR));
        panel.child(buildBlockingModeButton());
        addPatternSlots(panel);
        PatternEditor editor = new PatternEditor(state, actions, EDITOR_X, EDITOR_Y).inputProgress(this::bufferedText)
                .outputProgress(this::requestedText);
        editor.addTo(panel);
        panel.child(buildTargetDisplay(editor));
        addPipeButtons(panel, editor);
        panel.child(buildStatusLine());
        panel.child(SlotGroupWidget.playerInventory(true));
        return panel;
    }

    // region header and pattern slots

    private IWidget buildBlockingModeButton() {
        return new ButtonWidget<>().pos(WIDTH - 7 - 62, 4).size(62, 12)
                .overlay(IKey.lang(() -> LANG + "mode." + modeKey()).scale(0.8f)).onMousePressed(mouseButton -> {
                    if (mouseButton != 0) return false;
                    actions.cycleBlockingMode();
                    return true;
                }).tooltipAutoUpdate(true).tooltipBuilder(tooltip -> {
                    tooltip.addLine(IKey.lang(LANG + "mode.title"));
                    tooltip.addLine(IKey.lang(LANG + "mode." + modeKey() + ".tip").style(EnumChatFormatting.GRAY));
                    if (actions.isBlockingModeFixed()) {
                        tooltip.addLine(IKey.lang(LANG + "mode.fixed").style(EnumChatFormatting.YELLOW));
                    }
                });
    }

    private String modeKey() {
        return actions.getBlockingMode().name().toLowerCase(Locale.ROOT);
    }

    private void addPatternSlots(ModularPanel panel) {
        IItemHandlerModifiable patterns = new InvWrapper(patternPipe.getPatternModule().getPatternInventory());
        for (int i = 0; i < PatternEditorState.PATTERN_SLOTS; i++) {
            ModularSlot slot = new ModularSlot(patterns, i).slotGroup(PATTERN_SLOT_GROUP)
                    .filter(PatternEditorState::isPattern)
                    // ModularSlot does not forward onSlotChanged, so notify the module (HUD, interests, saving) here
                    .changeListener((stack, onlyAmountChanged, client, init) -> {
                        if (!client && !init) {
                            patternPipe.getPatternModule().markPatternInventoryDirty();
                        }
                    });
            panel.child(
                    new PatternSelectSlot(i, state, actions).slot(slot).pos(PATTERN_X + i * SLOT, PATTERN_Y)
                            .tooltipBuilder(
                                    tooltip -> tooltip.addLine(IKey.lang(LANG + "pattern.empty")).addLine(
                                            IKey.lang(LANG + "pattern.empty.tip").style(EnumChatFormatting.GRAY))));
        }
    }

    // endregion

    // region pipe widgets around the editor

    private IWidget buildTargetDisplay(PatternEditor editor) {
        IDrawable targetIcon = (context, x, y, width, height, widgetTheme) -> {
            ItemStack target = actions.getTargetStack();
            if (target != null) {
                GuiDraw.drawItem(target, x, y, width, height, context.getCurrentDrawingZ());
            } else {
                GuiDraw.drawRect(x + 3, y + 3, width - 6, height - 6, 0x40000000);
            }
        };
        return new Widget<>().pos(editor.x(TARGET_X), editor.y(TARGET_Y)).size(16, 16).overlay(targetIcon)
                .setEnabledIf(w -> state.hasPattern()).tooltipAutoUpdate(true).tooltipBuilder(tooltip -> {
                    ItemStack target = actions.getTargetStack();
                    if (target == null) {
                        tooltip.addLine(IKey.lang(LANG + "target.none"));
                        tooltip.addLine(IKey.lang(LANG + "target.none.tip").style(EnumChatFormatting.GRAY));
                        return;
                    }
                    tooltip.addLine(IKey.lang(LANG + "target", target.getDisplayName()));
                    ForgeDirection side = actions.getTargetSide();
                    if (side != ForgeDirection.UNKNOWN) {
                        tooltip.addLine(
                                IKey.lang(LANG + "target.side", side.name().toLowerCase(Locale.ROOT))
                                        .style(EnumChatFormatting.GRAY));
                    }
                    tooltip.addLine(IKey.lang(LANG + "target.tip").style(EnumChatFormatting.GRAY));
                });
    }

    private void addPipeButtons(ModularPanel panel, PatternEditor editor) {
        int y = BUTTONS_END_Y;
        panel.child(
                editor.editorButton(BUTTONS_X, y, BUTTON_WIDTH, 12).overlay(IKey.lang(LANG + "cancel").scale(0.8f))
                        .onMousePressed(mouseButton -> {
                            if (mouseButton != 0) return false;
                            actions.cancelCraft();
                            return true;
                        }).tooltip(
                                tooltip -> tooltip.addLine(IKey.lang(LANG + "cancel"))
                                        .addLine(IKey.lang(LANG + "cancel.tip").style(EnumChatFormatting.GRAY))));
        y += 14;
        panel.child(
                new ButtonWidget<>().pos(editor.x(BUTTONS_X), editor.y(y)).size(BUTTON_WIDTH, 12)
                        .overlay(IKey.lang(LANG + "return").scale(0.8f)).onMousePressed(mouseButton -> {
                            if (mouseButton != 0) return false;
                            actions.returnInputs();
                            return true;
                        }).tooltip(
                                tooltip -> tooltip.addLine(IKey.lang(LANG + "return"))
                                        .addLine(IKey.lang(LANG + "return.tip").style(EnumChatFormatting.GRAY))));
    }

    // endregion

    // region status

    private IWidget buildStatusLine() {
        return new ScrollingTextWidget(IKey.dynamic(this::statusText)).pos(8, STATUS_Y).size(WIDTH - 16, 10)
                .color(TEXT_COLOR).tooltipAutoUpdate(true).tooltipBuilder(tooltip -> {
                    tooltip.addLine(IKey.str(statusText()));
                    tooltip.addLine(
                            IKey.lang(LANG + "status.mode", StatCollector.translateToLocal(LANG + "mode." + modeKey()))
                                    .style(EnumChatFormatting.GRAY));
                });
    }

    private String statusText() {
        if (!state.hasPattern()) {
            return StatCollector.translateToLocal(LANG + "status.nopattern");
        }
        if (!state.getPattern().isConfigured()) {
            return StatCollector.translateToLocal(LANG + "status.unconfigured");
        }
        if (actions.isPatternUnsupported(state.getSelectedSlot())) {
            return EnumChatFormatting.RED + StatCollector.translateToLocal(LANG + "status.fluidupgrade");
        }
        PatternCraftingHudState.PatternInfo info = actions.getSelectedPatternInfo();
        if (info == null || info.getStatus().isEmpty()) {
            return StatCollector.translateToLocal(LANG + "status.idle");
        }
        return (info.isActive() ? EnumChatFormatting.DARK_GREEN + "● " + EnumChatFormatting.RESET : "")
                + info.getStatus();
    }

    // endregion

    // region progress texts

    private String bufferedText(int inputSlot) {
        PatternCraftingHudState.PatternInfo info = actions.getSelectedPatternInfo();
        if (info == null) {
            return null;
        }
        int buffered = 0;
        for (PatternCraftingHudState.IngredientInfo ingredient : info.getIngredients()) {
            if (ingredient.slot() == inputSlot) {
                buffered += ingredient.bufferedAmount();
            }
        }
        AbstractPattern pattern = state.getPattern();
        boolean fluid = PatternStackHelper.isFluid(pattern.getPatternStackInSlot(inputSlot));
        return buffered > 0 ? formatAmount(buffered, fluid) : null;
    }

    private String requestedText(int outputIndex) {
        PatternCraftingHudState.PatternInfo info = actions.getSelectedPatternInfo();
        if (info == null) {
            return null;
        }
        int requested = 0;
        for (PatternCraftingHudState.OutputInfo output : info.getOutputs()) {
            if (output.slot() == outputIndex) {
                requested += output.requestedAmount();
            }
        }
        AbstractPattern pattern = state.getPattern();
        boolean fluid = PatternStackHelper
                .isFluid(pattern.getPatternStackInSlot(pattern.getResultSlotStart() + outputIndex));
        return requested > 0 ? formatAmount(requested, fluid) : null;
    }

    private static String formatAmount(int amount, boolean fluid) {
        return fluid ? PatternGuiDraw.formatFluidAmount(amount) : PatternGuiDraw.formatCount(amount);
    }

    // endregion
}
