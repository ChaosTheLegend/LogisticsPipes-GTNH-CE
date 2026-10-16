package logisticspipes.gui.modularUI.pipes.patterncrafting;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IGuiAction;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import logisticspipes.compat.ModularUIHelper;
import logisticspipes.crafting.PatternSatelliteInfo;
import logisticspipes.crafting.pattern.AbstractPattern;
import logisticspipes.crafting.pattern.DefaultPattern;
import logisticspipes.crafting.pattern.ProcessingPattern;
import logisticspipes.crafting.patternStack.PatternStackHelper;
import logisticspipes.gui.modularUI.pipes.patterncrafting.PatternEditorSyncHandler.PatternAction;

/**
 * Builds the editor for the pattern selected in a {@link PatternEditorState}: the input and output entries (crafting or
 * processing layout), satellite badges and selector, and the type, flag, multiply and clear buttons.
 * <p>
 * Shared by the pattern crafting pipe GUI and the handheld pattern GUI. Positions are relative to the editor origin;
 * {@link #x(int)} and {@link #y(int)} turn them into panel positions for widgets the host GUI adds around it.
 */
public class PatternEditor {

    public static final int WIDTH = 182;
    public static final int HEIGHT = 80;
    static final int SLOT = 18;

    private static final int PROCESSING_INPUT_X = 4;
    private static final int PROCESSING_INPUT_Y = 4;
    private static final int CRAFTING_INPUT_X = 13;
    private static final int CRAFTING_INPUT_Y = 13;
    private static final int PROCESSING_OUTPUT_X = 104;
    private static final int PROCESSING_OUTPUT_Y = 22;
    private static final int CRAFTING_OUTPUT_X = 113;
    private static final int CRAFTING_OUTPUT_Y = 13;
    private static final int ARROW_X = 80;
    private static final int ARROW_Y = 12;
    static final int TARGET_X = 82;
    static final int TARGET_Y = 40;
    static final int BUTTONS_X = 144;
    static final int BUTTON_WIDTH = 36;
    static final int HALF_BUTTON_WIDTH = 17;
    /**
     * First free y below the editor buttons, for buttons the host GUI adds.
     */
    static final int BUTTONS_END_Y = 54;

    static final int TEXT_COLOR = 0x404040;
    private static final int FLAG_ON_COLOR = 0x55ff55;
    private static final int FLAG_OFF_COLOR = 0xa0a0a0;
    static final String LANG = "gui.patterncrafting.";

    private final PatternEditorState state;
    private final PatternEditorSyncHandler actions;
    private final int originX;
    private final int originY;
    private IntFunction<String> inputProgress = inputSlot -> null;
    private IntFunction<String> outputProgress = outputIndex -> null;

    private IPanelHandler satelliteSelector;
    private int selectorInputSlot;
    private boolean selectorFluid;

    public PatternEditor(PatternEditorState state, PatternEditorSyncHandler actions, int originX, int originY) {
        this.state = state;
        this.actions = actions;
        this.originX = originX;
        this.originY = originY;
    }

    /**
     * Text drawn in blue in the top right corner of an input entry, e.g. how much of it is buffered.
     */
    public PatternEditor inputProgress(IntFunction<String> inputProgress) {
        this.inputProgress = inputProgress;
        return this;
    }

    /**
     * Text drawn in blue in the top right corner of an output entry, e.g. how much of it is requested.
     */
    public PatternEditor outputProgress(IntFunction<String> outputProgress) {
        this.outputProgress = outputProgress;
        return this;
    }

    public int x(int relativeX) {
        return originX + relativeX;
    }

    public int y(int relativeY) {
        return originY + relativeY;
    }

    public void addTo(ModularPanel panel) {
        satelliteSelector = IPanelHandler.simple(panel, this::buildSatelliteSelector, true);

        panel.child(
                new Widget<>().pos(originX, originY).size(WIDTH, HEIGHT).background(new Rectangle().color(0x28000000)));
        panel.child(
                IKey.lang(LANG + "editor.nopattern").alignment(Alignment.Center).asWidget().pos(originX, originY)
                        .size(WIDTH, HEIGHT).color(0x606060).setEnabledIf(w -> !state.hasPattern()));

        IItemHandlerModifiable edited = new InvWrapper(state.getEditedInventory());
        BooleanSupplier crafting = () -> state.hasPattern() && !state.isProcessing();
        BooleanSupplier processing = () -> state.hasPattern() && state.isProcessing();
        for (int i = 0; i < DefaultPattern.INGREDIENT_SLOTS; i++) {
            addInputSlot(
                    panel,
                    edited,
                    i,
                    x(CRAFTING_INPUT_X + i % 3 * SLOT),
                    y(CRAFTING_INPUT_Y + i / 3 * SLOT),
                    crafting);
        }
        for (int i = 0; i < ProcessingPattern.INGREDIENT_SLOTS; i++) {
            addInputSlot(
                    panel,
                    edited,
                    i,
                    x(PROCESSING_INPUT_X + i % 4 * SLOT),
                    y(PROCESSING_INPUT_Y + i / 4 * SLOT),
                    processing);
        }
        for (int i = 0; i < DefaultPattern.RESULT_SLOTS; i++) {
            addOutputSlot(
                    panel,
                    edited,
                    DefaultPattern.INGREDIENT_SLOTS + i,
                    i,
                    x(CRAFTING_OUTPUT_X),
                    y(CRAFTING_OUTPUT_Y + i * SLOT),
                    crafting);
        }
        for (int i = 0; i < ProcessingPattern.RESULT_SLOTS; i++) {
            addOutputSlot(
                    panel,
                    edited,
                    ProcessingPattern.INGREDIENT_SLOTS + i,
                    i,
                    x(PROCESSING_OUTPUT_X + i % 2 * SLOT),
                    y(PROCESSING_OUTPUT_Y + i / 2 * SLOT),
                    processing);
        }

        panel.child(
                new Widget<>().pos(x(ARROW_X), y(ARROW_Y)).size(20, 20)
                        .background(GuiTextures.PROGRESS_ARROW.getSubArea(0, 0, 1, 0.5f))
                        .setEnabledIf(w -> state.hasPattern()));
        addEditorButtons(panel);
    }

    private void addInputSlot(ModularPanel panel, IItemHandlerModifiable edited, int inputSlot, int x, int y,
            BooleanSupplier visible) {
        ModularSlot slot = new ModularSlot(edited, inputSlot).ignoreMaxStackSize(true);
        panel.child(
                new PatternIngredientSlot().progressText(() -> inputProgress.apply(inputSlot))
                        .satelliteClick(() -> openSatelliteSelector(inputSlot))
                        .badgeDraw(() -> drawSatelliteBadge(inputSlot))
                        .extraTooltip(tooltip -> addInputTooltip(tooltip, inputSlot))
                        .syncHandler(new PatternIngredientSlotSH(slot)).pos(x, y)
                        .setEnabledIf(w -> visible.getAsBoolean()));
    }

    private void addOutputSlot(ModularPanel panel, IItemHandlerModifiable edited, int patternSlot, int outputIndex,
            int x, int y, BooleanSupplier visible) {
        ModularSlot slot = new ModularSlot(edited, patternSlot).ignoreMaxStackSize(true);
        panel.child(
                new PatternIngredientSlot().progressText(() -> outputProgress.apply(outputIndex))
                        .satelliteClick(() -> openSatelliteSelector(patternSlot))
                        .badgeDraw(() -> drawSatelliteBadge(patternSlot))
                        .mainOutput(
                                () -> actions.selectMainOutput(outputIndex),
                                () -> state.getPattern().getMainOutputSlot() == outputIndex)
                        .extraTooltip(tooltip -> {
                            tooltip.addLine(
                                    IKey.lang(
                                            LANG + (state.getPattern().getMainOutputSlot() == outputIndex
                                                    ? "output.main"
                                                    : "output.byproduct")));
                            tooltip.addLine(IKey.lang(LANG + "output.select").style(EnumChatFormatting.DARK_GRAY));
                            addSatelliteTooltip(tooltip, patternSlot);
                            addEntryHint(tooltip, patternSlot);
                        }).syncHandler(new PatternIngredientSlotSH(slot)).pos(x, y)
                        .setEnabledIf(w -> visible.getAsBoolean()));
    }

    private void addEditorButtons(ModularPanel panel) {
        int y = PROCESSING_INPUT_Y;
        panel.child(
                editorButton(BUTTONS_X, y, BUTTON_WIDTH, 14)
                        .overlay(IKey.lang(() -> LANG + (state.isProcessing() ? "type.processing" : "type.crafting")))
                        .onMousePressed(patternAction(PatternAction.TOGGLE_TYPE)).tooltipAutoUpdate(true)
                        .tooltipBuilder(tooltip -> {
                            tooltip.addLine(
                                    IKey.lang(
                                            LANG + (state.isProcessing() ? "type.processing" : "type.crafting")
                                                    + ".long"));
                            tooltip.addLine(IKey.lang(LANG + "type.tip").style(EnumChatFormatting.GRAY));
                        }));
        y += 16;
        panel.child(
                editorButton(BUTTONS_X, y, HALF_BUTTON_WIDTH, 14)
                        .overlay(flagLabel("oredict.short", () -> state.getPattern().isOreDictSubstitutionEnabled()))
                        .onMousePressed(patternAction(PatternAction.TOGGLE_ORE_DICT)).tooltipAutoUpdate(true)
                        .tooltipBuilder(
                                tooltip -> addFlagTooltip(
                                        tooltip,
                                        "oredict",
                                        state.getPattern().isOreDictSubstitutionEnabled())));
        panel.child(
                editorButton(BUTTONS_X + HALF_BUTTON_WIDTH + 2, y, HALF_BUTTON_WIDTH, 14)
                        .overlay(flagLabel("nbt.short", () -> state.getPattern().isIgnoreNbtEnabled()))
                        .onMousePressed(patternAction(PatternAction.TOGGLE_IGNORE_NBT)).tooltipAutoUpdate(true)
                        .tooltipBuilder(
                                tooltip -> addFlagTooltip(tooltip, "nbt", state.getPattern().isIgnoreNbtEnabled())));
        y += 16;
        panel.child(
                editorButton(BUTTONS_X, y, HALF_BUTTON_WIDTH, 14).overlay(IKey.str("x2"))
                        .onMousePressed(patternAction(PatternAction.MULTIPLY)).tooltip(
                                tooltip -> tooltip.addLine(IKey.lang(LANG + "multiply"))
                                        .addLine(IKey.lang(LANG + "multiply.tip").style(EnumChatFormatting.GRAY))));
        panel.child(
                editorButton(BUTTONS_X + HALF_BUTTON_WIDTH + 2, y, HALF_BUTTON_WIDTH, 14)
                        .overlay(GuiTextures.CROSS_TINY).onMousePressed(patternAction(PatternAction.CLEAR)).tooltip(
                                tooltip -> tooltip.addLine(IKey.lang(LANG + "clear"))
                                        .addLine(IKey.lang(LANG + "clear.tip").style(EnumChatFormatting.GRAY))));
    }

    /**
     * A button at an editor-relative position that is only shown while a pattern is selected.
     */
    ButtonWidget<?> editorButton(int relativeX, int relativeY, int width, int height) {
        return new ButtonWidget<>().pos(x(relativeX), y(relativeY)).size(width, height)
                .setEnabledIf(w -> state.hasPattern());
    }

    private IGuiAction.MousePressed patternAction(PatternAction action) {
        return mouseButton -> {
            if (mouseButton != 0) return false;
            actions.patternAction(action);
            return true;
        };
    }

    private IDrawable flagLabel(String key, BooleanSupplier enabled) {
        return IKey.lang(LANG + key).scale(0.75f).color(() -> enabled.getAsBoolean() ? FLAG_ON_COLOR : FLAG_OFF_COLOR);
    }

    private static void addFlagTooltip(RichTooltip tooltip, String key, boolean enabled) {
        tooltip.addLine(IKey.lang(LANG + key));
        tooltip.addLine(IKey.lang(LANG + key + ".tip").style(EnumChatFormatting.GRAY));
        tooltip.addLine(
                IKey.lang(LANG + (enabled ? "enabled" : "disabled"))
                        .style(enabled ? EnumChatFormatting.GREEN : EnumChatFormatting.RED));
    }

    // region slot helpers

    private boolean hasEntry(int patternSlot) {
        return state.getPattern().getPatternStackInSlot(patternSlot) != null;
    }

    boolean isFluidEntry(int patternSlot) {
        return PatternStackHelper.isFluid(state.getPattern().getPatternStackInSlot(patternSlot));
    }

    private void addInputTooltip(RichTooltip tooltip, int inputSlot) {
        String buffered = inputProgress.apply(inputSlot);
        if (buffered != null) {
            tooltip.addLine(IKey.lang(LANG + "buffered", buffered).style(EnumChatFormatting.AQUA));
        }
        addSatelliteTooltip(tooltip, inputSlot);
        addEntryHint(tooltip, inputSlot);
    }

    private void addEntryHint(RichTooltip tooltip, int patternSlot) {
        tooltip.addLine(
                IKey.lang(LANG + (isFluidEntry(patternSlot) ? "entry.fluid.tip" : "entry.item.tip"))
                        .style(EnumChatFormatting.DARK_GRAY));
    }

    // endregion

    // region satellites

    private void drawSatelliteBadge(int inputSlot) {
        if (!hasEntry(inputSlot)) return;
        int x = 1, y = 1, width = 7, height = 7;
        boolean fluid = isFluidEntry(inputSlot);
        boolean assigned = isSatelliteAssigned(inputSlot, fluid);
        int fill = !assigned ? 0xff707070 : fluid ? 0xff00a8cc : 0xff2b6ee8;
        GuiDraw.drawRect(x, y, width, height, 0xff1a1a1a);
        GuiDraw.drawRect(x + 1, y + 1, width - 2, height - 2, fill);
        if (assigned) {
            GuiDraw.drawRect(x + 2, y + 2, width - 4, height - 4, 0xffffffff);
            GuiDraw.drawRect(x + 3, y + 3, width - 6, height - 6, fill);
        } else {
            GuiDraw.drawRect(x + 3, y + 2, 1, height - 4, 0xffffffff);
            GuiDraw.drawRect(x + 2, y + 3, width - 4, 1, 0xffffffff);
        }
    }

    private boolean isSatelliteAssigned(int inputSlot, boolean fluid) {
        return getSatelliteId(inputSlot, fluid) > 0 || !getSatelliteUuid(inputSlot, fluid).isEmpty();
    }

    private int getSatelliteId(int inputSlot, boolean fluid) {
        AbstractPattern pattern = state.getPattern();
        if (inputSlot >= pattern.getResultSlotStart()) {
            int outputSlot = inputSlot - pattern.getResultSlotStart();
            return fluid ? pattern.getFluidByproductSatelliteIdForOutputSlot(outputSlot)
                    : pattern.getByproductSatelliteIdForOutputSlot(outputSlot);
        }
        return fluid ? pattern.getFluidSatelliteIdForInputSlot(inputSlot)
                : pattern.getSatelliteIdForInputSlot(inputSlot);
    }

    private String getSatelliteUuid(int inputSlot, boolean fluid) {
        AbstractPattern pattern = state.getPattern();
        if (inputSlot >= pattern.getResultSlotStart()) {
            int outputSlot = inputSlot - pattern.getResultSlotStart();
            return fluid ? pattern.getFluidByproductSatelliteUuidForOutputSlot(outputSlot)
                    : pattern.getByproductSatelliteUuidForOutputSlot(outputSlot);
        }
        return fluid ? pattern.getFluidSatelliteUuidForInputSlot(inputSlot)
                : pattern.getSatelliteUuidForInputSlot(inputSlot);
    }

    private void addSatelliteTooltip(RichTooltip tooltip, int inputSlot) {
        boolean fluid = isFluidEntry(inputSlot);
        boolean output = inputSlot >= state.getPattern().getResultSlotStart();
        if (!isSatelliteAssigned(inputSlot, fluid)) {
            tooltip.addLine(IKey.lang(LANG + (output ? "byproduct.local" : "satellite.local")));
        } else {
            tooltip.addLine(
                    IKey.lang(
                            LANG + (output ? "byproduct.assigned" : "satellite.assigned"),
                            satelliteName(inputSlot, fluid)));
            PatternSatelliteInfo satellite = actions
                    .findSatellite(getSatelliteId(inputSlot, fluid), getSatelliteUuid(inputSlot, fluid), fluid);
            if (satellite != null) {
                tooltip.addLine(IKey.str(describeLocation(satellite)).style(EnumChatFormatting.GRAY));
            }
        }
        if (output) tooltip.addLine(IKey.lang(LANG + "byproduct.upgrade").style(EnumChatFormatting.GRAY));
        tooltip.addLine(IKey.lang(LANG + "satellite.click").style(EnumChatFormatting.DARK_GRAY));
    }

    private String satelliteName(int inputSlot, boolean fluid) {
        int id = getSatelliteId(inputSlot, fluid);
        PatternSatelliteInfo satellite = actions.findSatellite(id, getSatelliteUuid(inputSlot, fluid), fluid);
        return satellite != null ? satellite.displayName() : "#" + id;
    }

    private static String describeLocation(PatternSatelliteInfo satellite) {
        String distance = satellite.distance() >= 0 ? satellite.distance() + "m"
                : StatCollector.translateToLocal(LANG + "satellite.otherdim");
        return distance + ", D"
                + satellite.dimension()
                + " ("
                + satellite.x()
                + ", "
                + satellite.y()
                + ", "
                + satellite.z()
                + ")";
    }

    private void openSatelliteSelector(int inputSlot) {
        selectorInputSlot = inputSlot;
        selectorFluid = isFluidEntry(inputSlot);
        actions.refreshSatellites();
        satelliteSelector.deleteCachedPanel();
        satelliteSelector.openPanel();
    }

    private ModularPanel buildSatelliteSelector(ModularPanel parent, EntityPlayer player) {
        int inputSlot = selectorInputSlot;
        boolean fluid = selectorFluid;
        boolean output = inputSlot >= state.getPattern().getResultSlotStart();
        int displaySlot = output ? inputSlot - state.getPattern().getResultSlotStart() : inputSlot;
        ModularPanel panel = new ModularPanel("pattern_satellite_selector").size(184, 178)
                .background(ModularUIHelper.BACKGROUND_TEXTURE);
        panel.child(
                IKey.lang(
                        LANG + (output ? "byproduct.title." : "satellite.title.") + (fluid ? "fluid" : "item"),
                        displaySlot + 1).asWidget().pos(8, 7).color(TEXT_COLOR));
        panel.child(ButtonWidget.panelCloseButton());
        StringValue search = new StringValue("");
        panel.child(
                new TextFieldWidget().value(search).pos(8, 20).size(168, 14)
                        .hintText(StatCollector.translateToLocal(LANG + "satellite.search")));
        ListWidget<IWidget, ?> list = new ListWidget<>();
        list.pos(8, 38).size(168, 132);
        int[] builtRevision = { actions.getSatelliteRevision() };
        fillSatelliteRows(panel, list, search, inputSlot, fluid);
        list.onUpdateListener(w -> {
            if (builtRevision[0] != actions.getSatelliteRevision()) {
                builtRevision[0] = actions.getSatelliteRevision();
                w.removeAll();
                fillSatelliteRows(panel, w, search, inputSlot, fluid);
            }
        });
        panel.child(list);
        return panel;
    }

    private void fillSatelliteRows(ModularPanel panel, ListWidget<IWidget, ?> list, StringValue search, int inputSlot,
            boolean fluid) {
        int currentId = getSatelliteId(inputSlot, fluid);
        String currentUuid = getSatelliteUuid(inputSlot, fluid);
        list.addChild(
                satelliteRow(
                        panel,
                        search,
                        StatCollector.translateToLocal(
                                LANG + (inputSlot >= state.getPattern().getResultSlotStart() ? "byproduct.local.row"
                                        : "satellite.local.row")),
                        "local none",
                        currentId <= 0 && currentUuid.isEmpty(),
                        inputSlot,
                        0,
                        "",
                        fluid),
                -1);
        PatternSatelliteInfo.SatelliteType type = fluid ? PatternSatelliteInfo.SatelliteType.FLUID
                : PatternSatelliteInfo.SatelliteType.ITEM;
        for (PatternSatelliteInfo satellite : actions.getSatellites()) {
            if (satellite.type() != type) {
                continue;
            }
            boolean current = !currentUuid.isEmpty() ? currentUuid.equals(satellite.uuid())
                    : satellite.id() == currentId;
            String label = (satellite.favorite() ? "★ " : "") + satellite.displayName()
                    + EnumChatFormatting.GRAY
                    + "  "
                    + describeLocation(satellite);
            list.addChild(
                    satelliteRow(
                            panel,
                            search,
                            label,
                            satellite.getSearchText(),
                            current,
                            inputSlot,
                            satellite.id(),
                            satellite.uuid(),
                            fluid),
                    -1);
        }
    }

    private IWidget satelliteRow(ModularPanel panel, StringValue search, String label, String searchText,
            boolean current, int inputSlot, int satelliteId, String satelliteUuid, boolean fluid) {
        String text = (current ? EnumChatFormatting.YELLOW + "» " + EnumChatFormatting.RESET : "") + label;
        return new ButtonWidget<>().width(162).height(14)
                .overlay(IKey.str(text).alignment(Alignment.CenterLeft).scale(0.8f))
                .setEnabledIf(w -> matchesSearch(searchText, search.getStringValue())).onMousePressed(mouseButton -> {
                    if (mouseButton != 0) return false;
                    actions.assignSatellite(inputSlot, satelliteId, satelliteUuid, fluid);
                    panel.closeIfOpen();
                    return true;
                });
    }

    private static boolean matchesSearch(String searchText, String query) {
        if (query == null || query.trim().isEmpty()) {
            return true;
        }
        String haystack = searchText.toLowerCase(Locale.ROOT);
        for (String token : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (!haystack.contains(token)) {
                return false;
            }
        }
        return true;
    }

    // endregion
}
