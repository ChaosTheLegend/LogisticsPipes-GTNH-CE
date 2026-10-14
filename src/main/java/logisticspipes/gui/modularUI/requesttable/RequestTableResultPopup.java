package logisticspipes.gui.modularUI.requesttable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;

import logisticspipes.compat.ModularUIHelper;
import logisticspipes.crafting.requesttable.RequestTableDisplaySettings;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.resources.IResource;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * Tells the player how a request went: what was requested, or what the network is missing for it. A green headline
 * stands for a request that went through, a red one for a failed one.
 * <p>
 * Client only.
 */
public class RequestTableResultPopup {

    private static final int TEXT_COLOR = 0x404040;
    private static final int HEADLINE_TEXT_COLOR = 0xffffff;
    private static final int SUCCESS_COLOR = 0xff3d8b3d;
    private static final int SUCCESS_SHADE = 0xff2a662a;
    private static final int MISSING_COLOR = 0xffb23b3b;
    private static final int MISSING_SHADE = 0xff842626;
    private static final int MIN_WIDTH = 120;
    private static final int MAX_WIDTH = 300;
    private static final int HEADLINE_HEIGHT = 14;
    private static final int ROW_HEIGHT = 18;
    // a row with a second line under its text: how the request reaches the table
    private static final int DETAIL_ROW_HEIGHT = 22;
    private static final int DETAIL_COLOR = 0x707070;
    private static final int MAX_ROWS = 8;
    private static final String LANG = "gui.requesttable.result.";

    // the popup of the request table window that is open, if one is
    private static RequestTableResultPopup active;

    /**
     * One requested or missing resource: what it looks like and its text, the amount first.
     */
    private static class Row {

        final ItemStack item;
        final FluidStack fluid;
        final String text;
        // the second line, null for none
        final String detail;

        /**
         * @param crafted how much of a requested resource is crafted, 0 for a missing one
         */
        Row(IResource resource, int crafted) {
            ItemIdentifierStack display = resource.getDisplayItem();
            FluidStack displayFluid = display == null ? null
                    : SimpleServiceLocator.logisticsFluidManager.getFluidFromContainer(display);
            fluid = displayFluid;
            item = display == null || displayFluid != null ? null : display.getItem().unsafeMakeNormalStack(1);
            text = resource.getDisplayText(IResource.ColorCode.NONE);
            detail = split(resource, crafted);
        }

        int height() {
            return detail == null ? ROW_HEIGHT : DETAIL_ROW_HEIGHT;
        }

        /**
         * @param stack what the network didn't take, a fluid container with the amount in millibuckets for a fluid
         */
        Row(ItemIdentifierStack stack) {
            fluid = SimpleServiceLocator.logisticsFluidManager.getFluidFromContainer(stack);
            if (fluid != null) {
                item = null;
                text = stack.getStackSize() + "mB " + fluid.getLocalizedName();
            } else {
                item = stack.getItem().unsafeMakeNormalStack(1);
                text = stack.getStackSize() + " " + item.getDisplayName();
            }
            detail = null;
        }
    }

    private final ModularPanel parent;
    private final RequestTableSyncHandler handler;
    private final IPanelHandler panelHandler;

    private final List<Row> rows = new ArrayList<>();
    private boolean missing;
    // which headline the window has: "success", "missing" or "unsent"
    private String titleKey = "success";
    // a result that waits for the window to be built for it
    private boolean pending;

    public RequestTableResultPopup(ModularPanel parent, RequestTableSyncHandler handler) {
        this.parent = parent;
        this.handler = handler;
        this.panelHandler = IPanelHandler.simple(parent, this::build, true);
        active = this;
    }

    /**
     * Reports the result of a request the way the player of the open request table window wants it: in the window, in
     * the chat, in both or not at all.
     *
     * @param missing true if the items are what the network lacks, false if they are what was requested
     * @param crafted for each requested item how much of it is crafted, may be shorter than the items
     * @return false if no such window is open: the result has to be reported another way
     */
    public static boolean offer(Collection<IResource> items, boolean missing, List<Integer> crafted) {
        RequestTableResultPopup popup = active;
        if (popup == null || !popup.parent.isValid()) {
            active = null;
            return false;
        }
        RequestTableDisplaySettings settings = popup.handler.getSettings();
        if (settings.getPopupMessages().shows(missing)) {
            popup.show(items, missing, crafted);
        }
        if (settings.getChatMessages().shows(missing)) {
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            int index = 0;
            for (IResource item : items) {
                String text = item.getDisplayText(missing ? IResource.ColorCode.MISSING : IResource.ColorCode.SUCCESS);
                String detail = split(item, craftedAt(crafted, index++, missing));
                if (detail != null) {
                    text += EnumChatFormatting.GRAY + " (" + detail + ")";
                }
                player.addChatMessage(
                        new ChatComponentText(
                                (missing ? EnumChatFormatting.RED : EnumChatFormatting.GREEN)
                                        + StatCollector
                                                .translateToLocal(LANG + (missing ? "chat.missing" : "chat.requested"))
                                        + " "
                                        + text));
            }
        }
        return true;
    }

    private static int craftedAt(List<Integer> crafted, int index, boolean missing) {
        return missing || crafted == null || index >= crafted.size() ? 0 : crafted.get(index);
    }

    /**
     * @return how a requested resource reaches the table: what comes from the network and what is crafted, null if it
     *         all comes from the network
     */
    private static String split(IResource resource, int crafted) {
        int total = resource.getRequestedAmount();
        if (crafted <= 0) {
            return null;
        }
        if (crafted >= total) {
            return StatCollector.translateToLocal(LANG + "crafted.all");
        }
        return StatCollector.translateToLocalFormatted(LANG + "crafted.part", total - crafted, crafted);
    }

    private void show(Collection<IResource> items, boolean nowMissing, List<Integer> crafted) {
        List<Row> added = new ArrayList<>();
        int index = 0;
        for (IResource item : items) {
            added.add(new Row(item, craftedAt(crafted, index++, nowMissing)));
        }
        show(added, nowMissing, nowMissing ? "missing" : "success");
    }

    private void show(List<Row> added, boolean failed, String kind) {
        // several results of one kind in a row are one list, a request for many items reports each of them
        if (!kind.equals(titleKey) || !(pending || panelHandler.isPanelOpen())) {
            rows.clear();
        }
        missing = failed;
        titleKey = kind;
        rows.addAll(added);
        pending = true;
    }

    /**
     * Tells the player what the network had no place for, in the window and in the chat as far as the player has the
     * messages there switched on.
     */
    public void showUnsent(List<ItemIdentifierStack> stacks) {
        RequestTableDisplaySettings settings = handler.getSettings();
        if (stacks.isEmpty()) {
            return;
        }
        List<Row> added = new ArrayList<>();
        for (ItemIdentifierStack stack : stacks) {
            added.add(new Row(stack));
        }
        if (settings.getChatMessages().shows(true)) {
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            for (Row row : added) {
                player.addChatMessage(
                        new ChatComponentText(
                                EnumChatFormatting.RED + StatCollector.translateToLocal(LANG + "unsent")
                                        + " "
                                        + row.text));
            }
        }
        if (!settings.getPopupMessages().shows(true)) {
            return;
        }
        // the same thing again replaces its line: a click that fails twice is still one problem
        if ("unsent".equals(titleKey)) {
            for (Row row : added) {
                rows.removeIf(present -> present.text.equals(row.text));
            }
        }
        show(added, true, "unsent");
    }

    /**
     * Called every tick while the GUI of the table is shown.
     */
    public void openPending() {
        if (!pending) {
            return;
        }
        if (panelHandler.isPanelOpen()) {
            // built for other rows
            panelHandler.closePanel();
            return;
        }
        pending = false;
        panelHandler.deleteCachedPanel();
        panelHandler.openPanel();
    }

    /**
     * @return the text of a row with its amount in the colour of the result
     */
    private String colored(String text) {
        int space = text.indexOf(' ');
        if (space <= 0) {
            return text;
        }
        EnumChatFormatting color = missing ? EnumChatFormatting.DARK_RED : EnumChatFormatting.DARK_GREEN;
        return color + text.substring(0, space) + EnumChatFormatting.RESET + text.substring(space);
    }

    private ModularPanel build(ModularPanel parentPanel, EntityPlayer player) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String title = StatCollector.translateToLocal(LANG + titleKey);
        List<Row> shown = new ArrayList<>(rows);
        String more = null;
        if (shown.size() > MAX_ROWS) {
            more = StatCollector.translateToLocalFormatted(LANG + "more", shown.size() - (MAX_ROWS - 1));
            shown = new ArrayList<>(shown.subList(0, MAX_ROWS - 1));
        }
        int textWidth = font.getStringWidth(title);
        for (Row row : shown) {
            textWidth = Math.max(textWidth, ROW_HEIGHT + 2 + font.getStringWidth(row.text));
            if (row.detail != null) {
                textWidth = Math.max(textWidth, ROW_HEIGHT + 2 + font.getStringWidth(row.detail));
            }
        }
        int width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, textWidth + 20));
        int rowsY = 5 + HEADLINE_HEIGHT + 4;
        int rowsHeight = more == null ? 0 : ROW_HEIGHT;
        for (Row row : shown) {
            rowsHeight += row.height();
        }
        int buttonY = rowsY + rowsHeight + 4;

        ModularPanel panel = new ModularPanel("request_table_result").size(width, buttonY + 14 + 7)
                .background(ModularUIHelper.BACKGROUND_TEXTURE);

        // the headline on a bar in the colour of the result
        boolean failed = missing;
        IDrawable bar = (context, x, y, w, h, widgetTheme) -> {
            GuiDraw.drawRect(x, y, w, h, failed ? MISSING_SHADE : SUCCESS_SHADE);
            GuiDraw.drawRect(x, y, w - 1, h - 1, failed ? MISSING_COLOR : SUCCESS_COLOR);
        };
        panel.child(new Widget<>().pos(5, 5).size(width - 10, HEADLINE_HEIGHT).background(bar));
        panel.child(
                IKey.str(title).asWidget().pos(5, 5).size(width - 10, HEADLINE_HEIGHT).alignment(Alignment.Center)
                        .color(HEADLINE_TEXT_COLOR).shadow(true));

        int y = rowsY;
        for (Row row : shown) {
            IDrawable icon = (context, x, iconY, w, h, widgetTheme) -> {
                if (row.fluid != null) {
                    GuiDraw.drawFluidTexture(row.fluid, x, iconY, 16, 16, context.getCurrentDrawingZ());
                } else if (row.item != null) {
                    GuiDraw.drawItem(row.item, x, iconY, 16, 16, context.getCurrentDrawingZ());
                }
            };
            panel.child(new Widget<>().pos(8, y + (row.height() - 16) / 2).size(16, 16).overlay(icon));
            int textX = 8 + ROW_HEIGHT + 2;
            int textWidthLeft = width - 16 - ROW_HEIGHT - 2;
            String text = colored(font.trimStringToWidth(row.text, textWidthLeft));
            if (row.detail == null) {
                panel.child(
                        IKey.str(text).asWidget().pos(textX, y).size(textWidthLeft, ROW_HEIGHT)
                                .alignment(Alignment.CenterLeft).color(TEXT_COLOR));
            } else {
                panel.child(
                        IKey.str(text).asWidget().pos(textX, y + 1).size(textWidthLeft, 10)
                                .alignment(Alignment.CenterLeft).color(TEXT_COLOR));
                panel.child(
                        IKey.str(font.trimStringToWidth(row.detail, textWidthLeft)).asWidget().pos(textX, y + 11)
                                .size(textWidthLeft, 10).alignment(Alignment.CenterLeft).color(DETAIL_COLOR));
            }
            y += row.height();
        }
        if (more != null) {
            panel.child(
                    IKey.str(more).asWidget().pos(8, y).size(width - 16, ROW_HEIGHT).alignment(Alignment.Center)
                            .color(TEXT_COLOR));
        }
        panel.child(
                new ButtonWidget<>().pos((width - 44) / 2, buttonY).size(44, 14)
                        .overlay(IKey.lang("gui.requesttable.popup.ok")).onMousePressed(mouseButton -> {
                            if (mouseButton != 0) return false;
                            Interactable.playButtonClickSound();
                            panelHandler.closePanel();
                            return true;
                        }));
        return panel;
    }
}
