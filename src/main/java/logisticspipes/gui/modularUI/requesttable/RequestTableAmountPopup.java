package logisticspipes.gui.modularUI.requesttable;

import java.util.function.IntConsumer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.input.Mouse;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.IntValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import logisticspipes.compat.ModularUIHelper;
import logisticspipes.config.Configs;

/**
 * The small window of the request table that asks for an amount: a number field, buttons that add to and take from it
 * and a confirm button. It is used for requests from the network and for the ingredients of the crafting grid.
 */
public class RequestTableAmountPopup {

    private static final int WIDTH = 152;
    private static final int HEIGHT = 80;
    private static final int TEXT_COLOR = 0x404040;
    private static final ResourceLocation ICONS = new ResourceLocation(
            "logisticspipes",
            "textures/gui/request_table_icons.png");
    private static final int MAX_AMOUNT = 999_999_999;

    private final IPanelHandler panelHandler;

    private String title = "";
    private ItemStack iconItem;
    private FluidStack iconFluid;
    private int amount = 1;
    // what the "All" button sets, 0 for a window without that button
    private int allAmount;
    private IntConsumer onConfirm = value -> {};

    // true while the window shows the values of its buttons for editing instead of the amount
    private boolean editingSteps;
    private final int[] editedSteps = new int[4];

    public RequestTableAmountPopup(ModularPanel parent) {
        panelHandler = IPanelHandler.simple(parent, this::build, true);
    }

    /**
     * Opens the window.
     *
     * @param title     the translated headline
     * @param iconItem  the item the amount is for, or null
     * @param iconFluid the fluid the amount is for, or null
     * @param amount    the amount to start with
     * @param onConfirm gets the chosen amount
     */
    public void open(String title, ItemStack iconItem, FluidStack iconFluid, int amount, IntConsumer onConfirm) {
        open(title, iconItem, iconFluid, amount, 0, onConfirm);
    }

    /**
     * @param allAmount all there is of what the amount is for: a button sets the amount to it. 0 for no such button.
     */
    public void open(String title, ItemStack iconItem, FluidStack iconFluid, int amount, int allAmount,
            IntConsumer onConfirm) {
        this.allAmount = Math.max(0, allAmount);
        this.editingSteps = false;
        this.title = title;
        this.iconItem = iconItem;
        this.iconFluid = iconFluid;
        this.amount = clamp(amount);
        this.onConfirm = onConfirm;
        // the window reads its content while it is drawn, so an open one just shows the new request
        if (!panelHandler.isPanelOpen()) {
            // with and without the "All" button the window is laid out differently
            panelHandler.deleteCachedPanel();
            panelHandler.openPanel();
        }
    }

    private static int clamp(long value) {
        return (int) Math.max(1, Math.min(MAX_AMOUNT, value));
    }

    private ModularPanel build(ModularPanel parent, EntityPlayer player) {
        ModularPanel panel = new ModularPanel("request_table_amount").size(WIDTH, HEIGHT)
                .background(ModularUIHelper.BACKGROUND_TEXTURE);
        panel.child(
                IKey.dynamic(
                        () -> editingSteps ? StatCollector.translateToLocal("gui.requesttable.popup.steps") : title)
                        .asWidget().pos(8, 7).width(WIDTH - 28).color(TEXT_COLOR));
        panel.child(ButtonWidget.panelCloseButton());

        // the window has two faces: the amount, and the values of its buttons
        ParentWidget<?> amountPage = new ParentWidget<>().pos(0, 0).size(WIDTH, HEIGHT)
                .setEnabledIf(w -> !editingSteps);
        ParentWidget<?> stepsPage = new ParentWidget<>().pos(0, 0).size(WIDTH, HEIGHT).setEnabledIf(w -> editingSteps);
        panel.child(amountPage);
        panel.child(stepsPage);

        IDrawable icon = (context, x, y, width, height, widgetTheme) -> {
            GuiTextures.SLOT_ITEM.draw(context, x, y, width, height, widgetTheme);
            if (iconFluid != null) {
                GuiDraw.drawFluidTexture(iconFluid, x + 1, y + 1, 16, 16, context.getCurrentDrawingZ());
            } else if (iconItem != null) {
                GuiDraw.drawItem(iconItem, x + 1, y + 1, 16, 16, context.getCurrentDrawingZ());
            }
        };
        amountPage.child(new Widget<>().pos(8, 20).size(18, 18).overlay(icon));

        amountPage.child(
                new AmountField().pos(30, 22).size(allAmount > 0 ? 44 : 72, 14).setNumbers(1, MAX_AMOUNT)
                        .value(new IntValue.Dynamic(() -> amount, value -> amount = clamp(value))));
        if (allAmount > 0) {
            amountPage.child(
                    new ButtonWidget<>().pos(77, 21).size(26, 16)
                            .overlay(IKey.lang("gui.requesttable.popup.all").scale(0.8f))
                            .onMousePressed(mouseButton -> {
                                if (mouseButton != 0) return false;
                                commitField(panel);
                                amount = clamp(allAmount);
                                Interactable.playButtonClickSound();
                                return true;
                            }).tooltipAutoUpdate(true).tooltipBuilder(
                                    tooltip -> tooltip.addLine(
                                            IKey.lang("gui.requesttable.popup.all.tip", String.valueOf(allAmount)))));
        }
        amountPage.child(
                new ButtonWidget<>().pos(106, 21).size(38, 16).overlay(IKey.lang("gui.requesttable.popup.ok"))
                        .onMousePressed(mouseButton -> {
                            if (mouseButton != 0) return false;
                            Interactable.playButtonClickSound();
                            commitField(panel);
                            int chosen = amount;
                            panelHandler.closePanel();
                            onConfirm.accept(chosen);
                            return true;
                        }));

        for (int i = 0; i < 4; i++) {
            int x = 8 + i * 34;
            amountPage.child(stepButton(panel, i, 1).pos(x, 42));
            amountPage.child(stepButton(panel, i, -1).pos(x, 58));
        }

        // the values of the buttons: what they are for, a field under each pair of buttons, and a button to keep them
        stepsPage.child(
                IKey.lang("gui.requesttable.popup.steps.description").asWidget().pos(8, 19).size(WIDTH - 16, 20)
                        .alignment(Alignment.TopLeft).color(TEXT_COLOR));
        for (int i = 0; i < editedSteps.length; i++) {
            int index = i;
            stepsPage.child(
                    new TextFieldWidget().pos(8 + i * 34, 42).size(32, 14)
                            .setNumbers(1, Configs.MAX_REQUEST_TABLE_AMOUNT_STEP).value(
                                    new IntValue.Dynamic(
                                            () -> editedSteps[index],
                                            value -> editedSteps[index] = Math
                                                    .max(1, Math.min(Configs.MAX_REQUEST_TABLE_AMOUNT_STEP, value)))));
        }
        stepsPage.child(
                new ButtonWidget<>().pos(WIDTH - 8 - 44, 59).size(44, 14)
                        .overlay(IKey.lang("gui.requesttable.recipe.done")).onMousePressed(mouseButton -> {
                            if (mouseButton != 0) return false;
                            Interactable.playButtonClickSound();
                            commitField(panel);
                            Configs.setRequestTableAmountSteps(editedSteps);
                            Configs.saveRequestTableAmountSteps();
                            editingSteps = false;
                            return true;
                        }));

        // left of the window: switches between the two faces, going back this way keeps the old values
        IDrawable gear = (context, x, y, width, height, widgetTheme) -> {
            GuiDraw.drawTexture(ICONS, x + (width - 16) / 2f, y + (height - 16) / 2f, 16, 16, 48, 128, 64, 208);
            if (editingSteps) {
                GuiDraw.drawBorderInsideXYWH(x, y, width, height, 1, 0xffffffff);
            }
        };
        panel.child(new ButtonWidget<>().pos(-20, 4).size(18, 18).overlay(gear).onMousePressed(mouseButton -> {
            if (mouseButton != 0) return false;
            Interactable.playButtonClickSound();
            commitField(panel);
            if (!editingSteps) {
                System.arraycopy(Configs.REQUEST_TABLE_AMOUNT_STEPS, 0, editedSteps, 0, editedSteps.length);
            }
            editingSteps = !editingSteps;
            return true;
        }).tooltipBuilder(tooltip -> {
            tooltip.addLine(IKey.lang("gui.requesttable.popup.steps"));
            tooltip.addLine(IKey.lang("gui.requesttable.popup.steps.tip").style(EnumChatFormatting.GRAY));
        }));
        return panel;
    }

    /**
     * What is typed into a field only becomes its value when the field loses the keyboard. A button that works with the
     * amount takes the keyboard away first, otherwise the field would overwrite what the button did.
     */
    private static void commitField(ModularPanel panel) {
        if (panel.isValid()) {
            panel.getContext().removeFocus();
        }
    }

    /**
     * Takes the keyboard as soon as the window is open, with its number selected: typing replaces it right away.
     */
    private static class AmountField extends TextFieldWidget {

        // how long after the window opened the field still makes sure that it has the keyboard and its number
        // selected: the click that opened the window can take the focus away again
        private static final int SELECT_TICKS = 10;

        private int ticks;
        private String initialText;

        @Override
        public void onUpdate() {
            // puts the number into the field while it has no focus
            super.onUpdate();
            if (ticks >= SELECT_TICKS || !isValid()) {
                return;
            }
            if (initialText == null) {
                initialText = getText();
            }
            if (!initialText.equals(getText())) {
                // the player is typing already
                ticks = SELECT_TICKS;
                return;
            }
            if (Mouse.isButtonDown(0) || Mouse.isButtonDown(1) || Mouse.isButtonDown(2)) {
                // still the click that opened the window, or the player places the cursor
                if (ticks > 0) {
                    ticks = SELECT_TICKS;
                }
                return;
            }
            ticks++;
            if (!isFocused()) {
                getContext().focus(this);
            }
            if (!handler.hasTextMarked()) {
                handler.markAll();
            }
        }
    }

    /**
     * @param index which of the four button values the button works with
     * @param sign  1 to add it, -1 to take it away
     */
    private ButtonWidget<?> stepButton(ModularPanel panel, int index, int sign) {
        return new ButtonWidget<>().size(32, 14).overlay(
                IKey.dynamic(() -> (sign > 0 ? "+" : "-") + Configs.REQUEST_TABLE_AMOUNT_STEPS[index]).scale(0.8f))
                .onMousePressed(mouseButton -> {
                    if (mouseButton != 0) return false;
                    commitField(panel);
                    int step = sign * Configs.REQUEST_TABLE_AMOUNT_STEPS[index];
                    // from the starting 1 a bigger step goes to the round number: 1000 instead of 1001
                    amount = amount == 1 && step > 1 ? step : clamp((long) amount + step);
                    Interactable.playButtonClickSound();
                    return true;
                });
    }
}
