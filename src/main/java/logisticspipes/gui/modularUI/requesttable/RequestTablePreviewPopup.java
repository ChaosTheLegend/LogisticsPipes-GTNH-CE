package logisticspipes.gui.modularUI.requesttable;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
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
import logisticspipes.crafting.v3.mc.PlanPreview;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * The crafting preview of the request table (plan section 10.1): the steps of the craft with their crafters, what it
 * takes from storage, and what is missing or can't be reached, with Start / Start anyway / Cancel.
 * <p>
 * The headline is green when the craft can start, orange when it can start but waits on unreachable items, red when
 * something is missing. Every color comes with a text saying the same.
 * <p>
 * Client only.
 */
public class RequestTablePreviewPopup {

    private static final int TEXT_COLOR = 0x404040;
    private static final int SECTION_COLOR = 0x202060;
    private static final int DETAIL_COLOR = 0x707070;
    private static final int MISSING_TEXT = 0xb23b3b;
    private static final int UNREACHABLE_TEXT = 0xc06000;
    private static final int OK_COLOR = 0xff3d8b3d;
    private static final int WARN_COLOR = 0xffd08000;
    private static final int BAD_COLOR = 0xffb23b3b;
    private static final int WIDTH = 240;
    private static final int HEADLINE_HEIGHT = 14;
    private static final int ROW_HEIGHT = 18;
    private static final int SECTION_HEIGHT = 11;
    private static final int MAX_ROWS = 14;

    private static final class Row {

        final ItemStack item;
        final FluidStack fluid;
        final String text;
        final String detail;
        final int color;
        final boolean section;

        Row(String section) {
            item = null;
            fluid = null;
            text = section;
            detail = null;
            color = SECTION_COLOR;
            this.section = true;
        }

        Row(ItemIdentifierStack stack, String text, String detail, int color) {
            FluidIdentifier fluidId = stack == null ? null : FluidIdentifier.get(stack.getItem());
            fluid = fluidId == null ? null : fluidId.makeFluidStack(Math.max(1, stack.getStackSize()));
            item = stack == null || fluid != null ? null : stack.getItem().unsafeMakeNormalStack(1);
            this.text = text;
            this.detail = detail;
            this.color = color;
            section = false;
        }

        int height() {
            return section ? SECTION_HEIGHT : ROW_HEIGHT;
        }
    }

    private final RequestTableSyncHandler handler;
    private final IPanelHandler panelHandler;
    private PlanPreview preview;
    private boolean pending;

    public RequestTablePreviewPopup(ModularPanel parent, RequestTableSyncHandler handler) {
        this.handler = handler;
        this.panelHandler = IPanelHandler.simple(parent, this::build, true);
    }

    /** Called when the server sent a preview; the window opens on the next GUI tick. */
    public void show(PlanPreview preview) {
        this.preview = preview;
        pending = true;
    }

    /** Called every tick while the GUI of the table is shown. */
    public void openPending() {
        if (!pending) {
            return;
        }
        if (panelHandler.isPanelOpen()) {
            panelHandler.closePanel();
            return;
        }
        pending = false;
        panelHandler.deleteCachedPanel();
        panelHandler.openPanel();
    }

    private static String name(ItemIdentifierStack stack) {
        if (stack == null) return "?";
        FluidIdentifier fluid = FluidIdentifier.get(stack.getItem());
        if (fluid != null) return stack.getStackSize() + "mB " + fluid.makeFluidStack(1).getLocalizedName();
        return stack.getStackSize() + " " + stack.getItem().getFriendlyName();
    }

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        if (!preview.error.isEmpty()) {
            rows.add(new Row("Could not plan: " + preview.error));
            return rows;
        }
        if (!preview.missing.isEmpty()) {
            rows.add(new Row("[x] Missing (nothing can start until it is there)"));
            for (ItemIdentifierStack s : preview.missing) rows.add(new Row(s, name(s), null, MISSING_TEXT));
        }
        if (!preview.unreachable.isEmpty()) {
            rows.add(new Row("[!] Unreachable (the craft waits for a route)"));
            for (ItemIdentifierStack s : preview.unreachable) rows.add(new Row(s, name(s), null, UNREACHABLE_TEXT));
        }
        if (!preview.steps.isEmpty()) {
            rows.add(new Row("Steps"));
            for (PlanPreview.Step step : preview.steps) {
                String detail = step.crafts + "x on " + step.crafter;
                if (step.legacy) detail = "[!] deprecated crafter - " + detail;
                rows.add(new Row(step.output, name(step.output), detail, step.legacy ? UNREACHABLE_TEXT : TEXT_COLOR));
            }
        }
        if (!preview.raw.isEmpty()) {
            rows.add(new Row("From storage"));
            for (ItemIdentifierStack s : preview.raw) rows.add(new Row(s, name(s), null, TEXT_COLOR));
        }
        return rows;
    }

    private ModularPanel build(ModularPanel parentPanel, EntityPlayer player) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        List<Row> shown = rows();
        String more = null;
        if (shown.size() > MAX_ROWS) {
            more = "... and " + (shown.size() - (MAX_ROWS - 1)) + " more";
            shown = new ArrayList<>(shown.subList(0, MAX_ROWS - 1));
        }
        int rowsY = 5 + HEADLINE_HEIGHT + 4;
        int rowsHeight = more == null ? 0 : SECTION_HEIGHT;
        for (Row row : shown) rowsHeight += row.height();
        int buttonY = rowsY + rowsHeight + 4;

        ModularPanel panel = new ModularPanel("request_table_preview").size(WIDTH, buttonY + 14 + 7)
                .background(ModularUIHelper.BACKGROUND_TEXTURE);

        boolean blocked = !preview.canStart;
        boolean warn = !blocked && (preview.hasUnreachable() || preview.hasLegacy());
        int barColor = blocked ? BAD_COLOR : warn ? WARN_COLOR : OK_COLOR;
        String title = (blocked ? "[x] Cannot craft " : warn ? "[!] Craft with warnings: " : "[ok] Craft ")
                + name(preview.target);
        IDrawable bar = (context, x, y, w, h, widgetTheme) -> GuiDraw.drawRect(x, y, w, h, barColor);
        panel.child(new Widget<>().pos(5, 5).size(WIDTH - 10, HEADLINE_HEIGHT).background(bar));
        panel.child(
                IKey.str(font.trimStringToWidth(title, WIDTH - 14)).asWidget().pos(5, 5)
                        .size(WIDTH - 10, HEADLINE_HEIGHT).alignment(Alignment.Center).color(0xffffff).shadow(true));

        int y = rowsY;
        for (Row row : shown) {
            if (row.section) {
                panel.child(
                        IKey.str(EnumChatFormatting.BOLD + font.trimStringToWidth(row.text, WIDTH - 20)).asWidget()
                                .pos(8, y).size(WIDTH - 16, SECTION_HEIGHT).alignment(Alignment.CenterLeft)
                                .color(row.color));
                y += row.height();
                continue;
            }
            IDrawable icon = (context, x, iconY, w, h, widgetTheme) -> {
                if (row.fluid != null) {
                    GuiDraw.drawFluidTexture(row.fluid, x, iconY, 16, 16, context.getCurrentDrawingZ());
                } else if (row.item != null) {
                    GuiDraw.drawItem(row.item, x, iconY, 16, 16, context.getCurrentDrawingZ());
                }
            };
            panel.child(new Widget<>().pos(10, y + 1).size(16, 16).overlay(icon));
            int textX = 10 + ROW_HEIGHT + 2;
            int textWidth = WIDTH - textX - 8;
            if (row.detail == null) {
                panel.child(
                        IKey.str(font.trimStringToWidth(row.text, textWidth)).asWidget().pos(textX, y)
                                .size(textWidth, ROW_HEIGHT).alignment(Alignment.CenterLeft).color(row.color));
            } else {
                panel.child(
                        IKey.str(font.trimStringToWidth(row.text, textWidth)).asWidget().pos(textX, y)
                                .size(textWidth, 9).alignment(Alignment.CenterLeft).color(row.color));
                panel.child(
                        IKey.str(font.trimStringToWidth(row.detail, textWidth)).asWidget().pos(textX, y + 9)
                                .size(textWidth, 9).alignment(Alignment.CenterLeft).color(DETAIL_COLOR));
            }
            y += row.height();
        }
        if (more != null) {
            panel.child(
                    IKey.str(more).asWidget().pos(8, y).size(WIDTH - 16, SECTION_HEIGHT).alignment(Alignment.Center)
                            .color(DETAIL_COLOR));
        }

        if (preview.canStart) {
            String start = preview.hasUnreachable() ? "Start anyway" : "Start";
            panel.child(button(start, (WIDTH / 2) - 74, buttonY, 70, () -> handler.startPreview()));
            panel.child(button("Cancel", (WIDTH / 2) + 4, buttonY, 70, () -> handler.cancelPreview()));
        } else {
            panel.child(button("Close", (WIDTH - 70) / 2, buttonY, 70, () -> handler.cancelPreview()));
        }
        return panel;
    }

    private ButtonWidget<?> button(String text, int x, int y, int width, Runnable action) {
        return new ButtonWidget<>().pos(x, y).size(width, 14).overlay(IKey.str(text)).onMousePressed(mouseButton -> {
            if (mouseButton != 0) return false;
            Interactable.playButtonClickSound();
            panelHandler.closePanel();
            action.run();
            return true;
        });
    }
}
