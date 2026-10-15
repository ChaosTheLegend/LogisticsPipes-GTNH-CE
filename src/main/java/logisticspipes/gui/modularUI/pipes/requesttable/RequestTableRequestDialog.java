package logisticspipes.gui.modularUI.pipes.requesttable;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.entity.player.EntityPlayer;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.screen.ModularPanel;

import logisticspipes.crafting.requesttable.gui.RequestTableGuiStyle;
import logisticspipes.crafting.requesttable.gui.RequestTableIcons;
import logisticspipes.crafting.requesttable.gui.RequestTableRender;
import logisticspipes.crafting.requesttable.network.RequestTableNetworkEntry;
import logisticspipes.crafting.requesttable.settings.RequestTableOrderSettings;
import logisticspipes.crafting.requesttable.storage.RequestTableFluidDisplay;
import logisticspipes.utils.gui.GuiGraphics;

/**
 * Amount popup for one network entry: stock summary, four +/- step buttons, All and OK.
 * <p>
 * The amount starts at zero. All fills in the network stock; the gear opens the order step editor. Newer network lists
 * refresh the shown stock without touching the typed amount.
 */
final class RequestTableRequestDialog {

    private static final int WIDTH = 184;
    private static final int HEIGHT = 102;
    private static final int DELTA_LEFT = 16;
    private static final int DELTA_STEP = 38;
    private static final int DELTA_WIDTH = 34;
    private static final int DELTA_HEIGHT = 14;
    private static final int PLUS_TOP = 33;
    private static final int ITEM_TOP = 51;
    private static final int MINUS_TOP = 81;
    private static final int ITEM_LEFT = 16;
    private static final int ALL_LEFT = 109;
    private static final int CONFIRM_LEFT = 143;
    private static final int CONFIRM_TOP = ITEM_TOP + 4;
    private static final int CONFIRM_WIDTH = 28;
    private static final int CONFIRM_HEIGHT = 18;
    private static final int HEADER_BUTTON_TOP = 4;
    private static final int HEADER_BUTTON_SIZE = 12;
    private static final int CLOSE_LEFT = WIDTH - 16;
    private static final int SETTINGS_LEFT = CLOSE_LEFT - HEADER_BUTTON_SIZE - 2;
    private static final int SUMMARY_TOP = 21;

    private final RequestTableMui gui;
    private final IPanelHandler handler;
    private final IPanelHandler settingsHandler;
    private RequestTableNetworkEntry entry;
    private RequestTableNumberFieldWidget amount;

    RequestTableRequestDialog(RequestTableMui gui, ModularPanel parent) {
        this.gui = gui;
        handler = IPanelHandler.simple(parent, this::build, true);
        settingsHandler = IPanelHandler.simple(parent, (panel, player) -> buildSettings(), true);
    }

    boolean isOpen() {
        return handler.isPanelOpen();
    }

    RequestTableNetworkEntry getEntry() {
        return entry;
    }

    void open(RequestTableNetworkEntry entry) {
        this.entry = entry;
        // A fresh panel starts with a zero amount and the current order steps.
        handler.deleteCachedPanel();
        handler.openPanel();
    }

    void close() {
        settingsHandler.closePanel();
        handler.closePanel();
    }

    /** Replaces the shown entry with its refreshed counterpart, keeping the typed amount. */
    void updateEntry(List<RequestTableNetworkEntry> entries) {
        if (entry == null || !isOpen()) return;
        for (RequestTableNetworkEntry refreshed : entries) {
            if (refreshed.fluid() == entry.fluid() && refreshed.stack().getItem().equals(entry.stack().getItem())) {
                entry = refreshed;
                return;
            }
        }
        entry = new RequestTableNetworkEntry(entry.stack().getItem().makeStack(0), entry.fluid(), 0, 0, false);
    }

    private int[] deltas() {
        return gui.getActions().getDisplaySettings().getOrderSettings().getAmountSteps(entry.fluid());
    }

    private String format(int value) {
        return entry.fluid() ? RequestTableFluidDisplay.formatAmount(value) : RequestTableGuiStyle.formatCount(value);
    }

    private void submit() {
        if (entry == null || !amount.isPositive()) return;
        gui.getActions().submitRequest(entry, amount.getAmount());
        close();
    }

    private void changeAmount(int delta) {
        int value = (int) Math.max(0, Math.min(Integer.MAX_VALUE, (long) amount.getAmount() + delta));
        amount.setValue(Integer.toString(value));
        focusAmount();
    }

    private void focusAmount() {
        if (amount.isValid()) amount.getContext().focus(amount);
    }

    private ModularPanel build(ModularPanel parent, EntityPlayer player) {
        RequestTablePopupPanel panel = new RequestTablePopupPanel("request_table_request", WIDTH, HEIGHT)
                .onEnter(this::submit);
        panel.child(
                new RequestTablePaintWidget((width, height) -> paintStatic()).pos(0, 0).size(WIDTH, HEIGHT)
                        .lines(lines -> {
                            if (entry == null) return;
                            String unit = entry.fluid() ? " mB" : "";
                            lines.add("In table: " + entry.internalAmount() + unit);
                            lines.add("Network: " + entry.networkAmount() + unit);
                        }));
        panel.child(
                RequestTableMui.popupButton(() -> "x", () -> true).pos(CLOSE_LEFT, HEADER_BUTTON_TOP)
                        .size(HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE).onLeftClick(this::close));
        panel.child(
                new RequestTableButtonWidget().pos(SETTINGS_LEFT, HEADER_BUTTON_TOP)
                        .size(HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE).painter((width, height, hover, active) -> {
                            try (var ignored = RequestTableRender.guiState()) {
                                RequestTableGuiStyle
                                        .requestButton(0, 0, width, height, true, hover ? 1 : -1, hover ? 1 : -1);
                                RequestTableIcons.gear(2, 2);
                            }
                        }).onLeftClick(this::openSettings).lines(lines -> lines.add("Configure order steps")));
        for (int i = 0; i < 4; i++) {
            int index = i;
            int x = DELTA_LEFT + i * DELTA_STEP;
            panel.child(
                    RequestTableMui.popupButton(() -> "+" + deltas()[index], () -> true).pos(x, PLUS_TOP)
                            .size(DELTA_WIDTH, DELTA_HEIGHT).onLeftClick(() -> changeAmount(deltas()[index]))
                            .lines(lines -> lines.add("+" + deltas()[index])));
            panel.child(
                    RequestTableMui.popupButton(() -> "-" + deltas()[index], () -> true).pos(x, MINUS_TOP)
                            .size(DELTA_WIDTH, DELTA_HEIGHT).onLeftClick(() -> changeAmount(-deltas()[index]))
                            .lines(lines -> lines.add("-" + deltas()[index])));
        }
        amount = new RequestTableNumberFieldWidget("0", 0).onEnter(this::submit).onEscape(this::close);
        amount.pos(48, ITEM_TOP + 6).size(55, 14).setFocusOnGuiOpen(true);
        panel.child(amount);
        panel.child(
                RequestTableMui.popupButton(() -> "All", () -> entry != null && entry.networkAmount() > 0)
                        .pos(ALL_LEFT, CONFIRM_TOP).size(CONFIRM_WIDTH, CONFIRM_HEIGHT).onLeftClick(() -> {
                            amount.setValue(Integer.toString(entry.networkAmount()));
                            focusAmount();
                        }));
        panel.child(
                RequestTableMui.popupButton(() -> "OK", () -> amount.isPositive()).pos(CONFIRM_LEFT, CONFIRM_TOP)
                        .size(CONFIRM_WIDTH, CONFIRM_HEIGHT).onLeftClick(this::submit));
        return panel;
    }

    private void paintStatic() {
        if (entry == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;
        try (var ignored = RequestTableRender.guiState()) {
            font.drawString("Request", 8, 7, RequestTableGuiStyle.TEXT);
            String internal = "In table: " + format(entry.internalAmount());
            String network = "Network: " + format(entry.networkAmount());
            int internalWidth = font.getStringWidth(internal);
            int networkWidth = font.getStringWidth(network);
            int width = WIDTH - 16;
            float scale = Math.min(1.0F, (width - 8) / (float) (internalWidth + networkWidth));
            try (var ignored2 = RequestTableRender.guiState()) {
                GL11.glTranslatef(8, SUMMARY_TOP, 0);
                GL11.glScalef(scale, scale, 1);
                font.drawString(internal, 0, 0, RequestTableGuiStyle.TEXT);
                font.drawString(network, Math.round(width / scale) - networkWidth, 0, RequestTableGuiStyle.TEXT);
            }
            GuiGraphics.drawBigSlotBackground(mc, ITEM_LEFT, ITEM_TOP);
        }
        RequestTableRender.item(entry.getDisplayStack(), ITEM_LEFT + 5, ITEM_TOP + 5, 250.0F, false);
        if (entry.craftable()) {
            try (var ignored = RequestTableRender.guiState()) {
                RequestTableIcons.craftable(ITEM_LEFT + 5, ITEM_TOP + 5);
            }
        }
    }

    // region order step editor

    private static final int MARGIN = 12;
    private static final int FIELD_GAP = 6;
    private static final int ROW_STEP = 22;
    private static final int FIELD_HEIGHT = 16;
    private static final int ITEM_LABEL_TOP = 40;
    private static final int ITEM_FIELDS_TOP = 52;
    private static final int BUTTON_HEIGHT = 18;

    private void openSettings() {
        // The editor always starts from the saved steps.
        settingsHandler.deleteCachedPanel();
        settingsHandler.openPanel();
    }

    private ModularPanel buildSettings() {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int screenWidth = gui.getScreenWidth();
        int fieldWidth = Math.max(68, font.getStringWidth(Integer.toString(Integer.MAX_VALUE)) + 10);
        int columns = screenWidth >= MARGIN * 2 + fieldWidth * 4 + FIELD_GAP * 3 + 16 ? 4 : 2;
        int rows = 4 / columns;
        int width = MARGIN * 2 + fieldWidth * columns + FIELD_GAP * (columns - 1);
        int fluidLabelTop = ITEM_LABEL_TOP + rows * ROW_STEP + 14;
        int fluidTop = fluidLabelTop + 12;
        int buttonTop = fluidTop + rows * ROW_STEP + 18;
        int height = buttonTop + BUTTON_HEIGHT + 8;
        int buttonWidth = (width - MARGIN * 2 - FIELD_GAP * 2) / 3;
        RequestTableNumberFieldWidget[] fields = new RequestTableNumberFieldWidget[8];

        RequestTablePopupPanel panel = new RequestTablePopupPanel("request_table_order_steps", width, height);
        Runnable save = () -> {
            if (!allPositive(fields)) return;
            RequestTableOrderSettings settings = new RequestTableOrderSettings(steps(fields, 0), steps(fields, 4));
            gui.applyDisplaySettings(gui.getActions().getDisplaySettings().withOrderSettings(settings));
            panel.closeIfOpen();
            focusAmount();
        };
        Runnable back = () -> {
            panel.closeIfOpen();
            focusAmount();
        };
        panel.onEnter(save);
        panel.child(new RequestTablePaintWidget((w, h) -> {
            try (var ignored = RequestTableRender.guiState()) {
                font.drawString("Order steps", MARGIN, 8, RequestTableGuiStyle.TEXT);
                font.drawString("Left to right, for + and -", MARGIN, 24, RequestTableGuiStyle.MUTED);
                font.drawString("Items", MARGIN, ITEM_LABEL_TOP, RequestTableGuiStyle.TEXT);
                font.drawString("Fluids (mB)", MARGIN, fluidLabelTop, RequestTableGuiStyle.TEXT);
                if (!allPositive(fields)) {
                    font.drawString("Positive numbers only.", MARGIN, buttonTop - 14, 0xa02020);
                }
            }
        }).pos(0, 0).size(width, height));
        panel.child(
                RequestTableMui.popupButton(() -> "x", () -> true).pos(width - 16, 4).size(12, 12).onLeftClick(back));
        RequestTableOrderSettings current = gui.getActions().getDisplaySettings().getOrderSettings();
        for (int i = 0; i < fields.length; i++) {
            int index = i % 4;
            int[] amounts = current.getAmountSteps(i >= 4);
            int focusIndex = i;
            fields[i] = new RequestTableNumberFieldWidget(Integer.toString(amounts[index]), 1).onEnter(save)
                    .onEscape(back).onTab(() -> {
                        boolean reverse = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                                || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                        RequestTableNumberFieldWidget next = fields[(focusIndex + (reverse ? fields.length - 1 : 1))
                                % fields.length];
                        next.getContext().focus(next);
                    });
            fields[i]
                    .pos(
                            MARGIN + index % columns * (fieldWidth + FIELD_GAP),
                            (i < 4 ? ITEM_FIELDS_TOP : fluidTop) + index / columns * ROW_STEP)
                    .size(fieldWidth, FIELD_HEIGHT);
            panel.child(fields[i]);
        }
        fields[0].setFocusOnGuiOpen(true);
        panel.child(
                RequestTableMui.popupButton(() -> "Reset", () -> true).pos(MARGIN, buttonTop)
                        .size(buttonWidth, BUTTON_HEIGHT).onLeftClick(() -> {
                            for (int i = 0; i < fields.length; i++) {
                                int[] amounts = RequestTableOrderSettings.DEFAULT.getAmountSteps(i >= 4);
                                fields[i].setValue(Integer.toString(amounts[i % 4]));
                            }
                            fields[0].getContext().focus(fields[0]);
                        }));
        panel.child(
                RequestTableMui.popupButton(() -> "Back", () -> true).pos(MARGIN + buttonWidth + FIELD_GAP, buttonTop)
                        .size(buttonWidth, BUTTON_HEIGHT).onLeftClick(back));
        panel.child(
                RequestTableMui.popupButton(() -> "Save", () -> allPositive(fields))
                        .pos(MARGIN + 2 * (buttonWidth + FIELD_GAP), buttonTop).size(buttonWidth, BUTTON_HEIGHT)
                        .onLeftClick(save));
        return panel;
    }

    private static boolean allPositive(RequestTableNumberFieldWidget[] fields) {
        for (RequestTableNumberFieldWidget field : fields) {
            if (field == null || !field.isPositive()) return false;
        }
        return true;
    }

    private static RequestTableOrderSettings.AmountSteps steps(RequestTableNumberFieldWidget[] fields, int offset) {
        return new RequestTableOrderSettings.AmountSteps(
                fields[offset].getAmount(),
                fields[offset + 1].getAmount(),
                fields[offset + 2].getAmount(),
                fields[offset + 3].getAmount());
    }

    // endregion
}
