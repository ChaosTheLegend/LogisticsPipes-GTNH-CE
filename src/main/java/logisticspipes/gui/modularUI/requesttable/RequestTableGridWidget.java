package logisticspipes.gui.modularUI.requesttable;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerIngredientProvider;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;

import cpw.mods.fml.common.Loader;
import gregtech.api.util.GTUtility;
import logisticspipes.crafting.requesttable.RequestTableDisplaySettings.ClickMode;
import logisticspipes.crafting.requesttable.RequestTableDisplaySettings.FilterMode;
import logisticspipes.crafting.requesttable.RequestTableDisplaySettings.FluidUnit;
import logisticspipes.crafting.requesttable.RequestTableNetworkEntry;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * The network list of the request table: a grid of slots with the amount in each of them and a scrollbar next to it.
 * <p>
 * The entries are no real slots, the widget draws them and sends the clicks on them to the server.
 */
public class RequestTableGridWidget extends Widget<RequestTableGridWidget>
        implements Interactable, RecipeViewerIngredientProvider {

    public static final int CELL = 18;
    public static final int COLUMNS = 9;
    public static final int SCROLLBAR_GAP = 2;
    public static final int SCROLLBAR_WIDTH = 12;
    public static final int WIDTH = COLUMNS * CELL + SCROLLBAR_GAP + SCROLLBAR_WIDTH;

    private static final int SCROLLBAR_X = COLUMNS * CELL + SCROLLBAR_GAP;
    static final int HANDLE_HEIGHT = 15;

    private static final int AMOUNT_COLOR = 0xffffff;
    private static final int CRAFT_COLOR = 0xffff55;
    private static final int HOVER_COLOR = 0x80ffffff;

    // the amounts the request window starts with
    private static final int FLUID_AMOUNT = 1000;

    /**
     * Asks the player how much of an entry to request.
     */
    public interface RequestOpener {

        void open(RequestTableNetworkEntry entry, FluidStack fluid, int startAmount);
    }

    private final RequestTableSyncHandler handler;
    private final Supplier<String> search;
    private final RequestOpener requestOpener;

    private final Map<RequestTableNetworkEntry, FluidStack> fluids = new IdentityHashMap<>();
    private final RichTooltip entryTooltip = new RichTooltip().parent(this).pos(RichTooltip.Pos.NEXT_TO_MOUSE)
            .tooltipBuilder(this::buildEntryTooltip);

    private int scrollRow;
    private boolean draggingScrollbar;
    // where the dragged handle is drawn, and where on the handle the mouse holds it
    private int dragHandleTop = -1;
    private int grabOffset;
    private RequestTableNetworkEntry hovered;
    private RequestTableNetworkEntry tooltipEntry;
    private boolean tooltipShift;

    public RequestTableGridWidget(RequestTableSyncHandler handler, Supplier<String> search,
            RequestOpener requestOpener) {
        this.handler = handler;
        this.search = search;
        this.requestOpener = requestOpener;
        width(WIDTH);
    }

    private List<RequestTableNetworkEntry> entries() {
        if (handler.getList().setSearch(search.get())) {
            scrollRow = 0;
        }
        return handler.getList().getVisibleEntries();
    }

    private int visibleRows() {
        return Math.max(1, getArea().height / CELL);
    }

    private int maxScroll(List<RequestTableNetworkEntry> entries) {
        int rows = (entries.size() + COLUMNS - 1) / COLUMNS;
        return Math.max(0, rows - visibleRows());
    }

    private RequestTableNetworkEntry entryAt(List<RequestTableNetworkEntry> entries, int mouseX, int mouseY) {
        if (mouseX < 0 || mouseY < 0 || mouseX >= COLUMNS * CELL || mouseY >= visibleRows() * CELL) {
            return null;
        }
        int index = (scrollRow + mouseY / CELL) * COLUMNS + mouseX / CELL;
        return index < entries.size() ? entries.get(index) : null;
    }

    private FluidStack fluidOf(RequestTableNetworkEntry entry) {
        if (!entry.isFluid()) {
            return null;
        }
        if (fluids.size() > 4096) {
            fluids.clear();
        }
        return fluids.computeIfAbsent(
                entry,
                e -> SimpleServiceLocator.logisticsFluidManager.getFluidFromContainer(e.getStack()));
    }

    // region drawing

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        List<RequestTableNetworkEntry> entries = entries();
        int rows = visibleRows();
        int maxScroll = maxScroll(entries);
        scrollRow = Math.max(0, Math.min(maxScroll, scrollRow));
        WidgetTheme theme = widgetTheme.getTheme();

        if (draggingScrollbar) {
            if (Mouse.isButtonDown(0)) {
                // every frame, not only when the mouse reports a move, so the handle follows without steps
                scrollTo(entries, context.getMouseY());
            } else {
                draggingScrollbar = false;
            }
        }
        hovered = isHovering() && !draggingScrollbar ? entryAt(entries, context.getMouseX(), context.getMouseY())
                : null;

        int first = scrollRow * COLUMNS;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int x = column * CELL;
                int y = row * CELL;
                GuiTextures.SLOT_ITEM.draw(context, x, y, CELL, CELL, theme);
                int index = first + row * COLUMNS + column;
                if (index < entries.size()) {
                    drawEntry(context, entries.get(index), x, y);
                }
            }
        }
        drawScrollbar(rows * CELL, maxScroll);
    }

    private void drawEntry(ModularGuiContext context, RequestTableNetworkEntry entry, int x, int y) {
        FluidStack fluid = fluidOf(entry);
        if (fluid != null) {
            GuiDraw.drawFluidTexture(fluid, x + 1, y + 1, 16, 16, context.getCurrentDrawingZ());
        } else {
            GuiDraw.drawItem(
                    entry.getStack().getItem().unsafeMakeNormalStack(1),
                    x + 1,
                    y + 1,
                    16,
                    16,
                    context.getCurrentDrawingZ());
        }
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        // what the network has at the bottom, what the table holds at the top
        // a list of only what can be crafted is about crafting: it shows no amounts
        boolean craftOnly = handler.getSettings().getFilterMode() == FilterMode.CRAFTABLE;
        if (craftOnly) {
            drawCornerText("Craft", x, y, CRAFT_COLOR, false);
        } else if (entry.getNetworkAmount() > 0) {
            drawCornerText(formatAmount(entry, entry.getNetworkAmount()), x, y, AMOUNT_COLOR, false);
        } else if (entry.isCraftable()) {
            drawCornerText("Craft", x, y, CRAFT_COLOR, false);
        }
        if (!craftOnly && entry.getInternalAmount() > 0) {
            drawCornerText(formatAmount(entry, entry.getInternalAmount()), x, y, AMOUNT_COLOR, true);
        }
        if (entry == hovered) {
            GuiDraw.drawRect(x + 1, y + 1, 16, 16, HOVER_COLOR);
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1, 1, 1, 1);
    }

    /**
     * Draws half-sized text into the top or bottom right corner of a slot.
     */
    private static void drawCornerText(String text, int slotX, int slotY, int color, boolean top) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        float scale = 0.5f;
        float x = slotX + 17 - font.getStringWidth(text) * scale;
        float y = top ? slotY + 1 : slotY + 17 - font.FONT_HEIGHT * scale;
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        GL11.glScalef(scale, scale, 1);
        font.drawStringWithShadow(text, 0, 0, color);
        GL11.glPopMatrix();
        GL11.glColor4f(1, 1, 1, 1);
    }

    private boolean inBuckets(RequestTableNetworkEntry entry) {
        return entry.isFluid() && handler.getSettings().getFluidUnit() == FluidUnit.BUCKETS;
    }

    /**
     * Formats the amount of an entry for its slot, a fluid in the unit the player chose.
     */
    private String formatAmount(RequestTableNetworkEntry entry, int amount) {
        if (!inBuckets(entry)) {
            return formatAmount(amount);
        }
        if (amount < 10_000) {
            // up to ten buckets with a decimal: 0.5B, 2B, 9.9B
            String buckets = String.format(Locale.ROOT, "%.1f", amount / 1000.0);
            return (buckets.endsWith(".0") ? buckets.substring(0, buckets.length() - 2) : buckets) + "B";
        }
        return formatAmount(amount / 1000) + "B";
    }

    /**
     * The full amount for the tooltip.
     */
    private String describeAmount(RequestTableNetworkEntry entry, int amount) {
        if (!entry.isFluid()) {
            return Integer.toString(amount);
        }
        if (inBuckets(entry)) {
            String buckets = String.format(Locale.ROOT, "%.3f", amount / 1000.0);
            buckets = buckets.contains(".") ? buckets.replaceAll("0+$", "").replaceAll("\\.$", "") : buckets;
            return buckets + " B";
        }
        return amount + " mB";
    }

    /**
     * Formats an amount of items or mB so it fits into a slot: {@code 128}, {@code 16k}, {@code 2.4M}.
     */
    static String formatAmount(int amount) {
        if (amount < 10_000) {
            return Integer.toString(amount);
        }
        if (amount < 1_000_000) {
            return amount / 1000 + "k";
        }
        if (amount < 10_000_000) {
            return String.format(Locale.ROOT, "%.1fM", amount / 1_000_000.0);
        }
        if (amount < 1_000_000_000) {
            return amount / 1_000_000 + "M";
        }
        return String.format(Locale.ROOT, "%.1fG", amount / 1_000_000_000.0);
    }

    private int handleTop(int trackHeight, int maxScroll) {
        int travel = Math.max(0, trackHeight - 2 - HANDLE_HEIGHT);
        return 1 + (maxScroll == 0 ? 0 : travel * scrollRow / maxScroll);
    }

    private void drawScrollbar(int trackHeight, int maxScroll) {
        // while it is dragged the handle stays under the mouse, the list itself moves by whole rows
        int y = draggingScrollbar && dragHandleTop >= 0 ? dragHandleTop : handleTop(trackHeight, maxScroll);
        drawScrollbar(SCROLLBAR_X, trackHeight, y, maxScroll > 0);
    }

    /**
     * Draws the scrollbar of the list, the storage tabs use the same one.
     *
     * @param handleTop where the handle starts, one pixel below the top of the track at least
     * @param active    false if there is nothing to scroll
     */
    static void drawScrollbar(int trackX, int trackHeight, int handleTop, boolean active) {
        // a track that looks like a slot
        GuiDraw.drawRect(trackX, 0, SCROLLBAR_WIDTH, trackHeight, 0xff8b8b8b);
        GuiDraw.drawRect(trackX, 0, SCROLLBAR_WIDTH - 1, 1, 0xff373737);
        GuiDraw.drawRect(trackX, 0, 1, trackHeight - 1, 0xff373737);
        GuiDraw.drawRect(trackX + 1, trackHeight - 1, SCROLLBAR_WIDTH - 1, 1, 0xffffffff);
        GuiDraw.drawRect(trackX + SCROLLBAR_WIDTH - 1, 1, 1, trackHeight - 1, 0xffffffff);

        int x = trackX + 1;
        int y = handleTop;
        int w = SCROLLBAR_WIDTH - 2;
        GuiDraw.drawRect(x, y, w, HANDLE_HEIGHT, active ? 0xffc6c6c6 : 0xff9a9a9a);
        GuiDraw.drawRect(x, y, w - 1, 1, active ? 0xffffffff : 0xffb0b0b0);
        GuiDraw.drawRect(x, y, 1, HANDLE_HEIGHT - 1, active ? 0xffffffff : 0xffb0b0b0);
        GuiDraw.drawRect(x + 1, y + HANDLE_HEIGHT - 1, w - 1, 1, 0xff555555);
        GuiDraw.drawRect(x + w - 1, y + 1, 1, HANDLE_HEIGHT - 1, 0xff555555);
    }

    @Override
    public void drawForeground(ModularGuiContext context) {
        if (hovered == null || context.hasDraggable()) {
            return;
        }
        boolean shift = Interactable.hasShiftDown();
        if (hovered != tooltipEntry || shift != tooltipShift) {
            tooltipEntry = hovered;
            tooltipShift = shift;
            entryTooltip.markDirty();
        }
        ItemStack stack = hovered.isFluid() ? null : hovered.getStack().getItem().unsafeMakeNormalStack(1);
        entryTooltip.draw(context, stack);
    }

    /**
     * The keys of NEI for recipes, usages and bookmarks work on the entry under the mouse.
     */
    @Override
    public ItemStack getStackForRecipeViewer() {
        RequestTableNetworkEntry entry = hovered;
        if (entry == null) {
            return null;
        }
        FluidStack fluid = fluidOf(entry);
        if (fluid == null) {
            return entry.getStack().getItem().unsafeMakeNormalStack(1);
        }
        // NEI knows fluids by the display item of GregTech
        return Loader.isModLoaded("gregtech") ? GregTechFluidDisplay.of(fluid) : null;
    }

    /**
     * Only to be touched while GregTech is loaded.
     */
    private static final class GregTechFluidDisplay {

        static ItemStack of(FluidStack fluid) {
            return GTUtility.getFluidDisplayStack(fluid, false);
        }
    }

    private void buildEntryTooltip(RichTooltip tooltip) {
        RequestTableNetworkEntry entry = tooltipEntry;
        if (entry == null) {
            return;
        }
        FluidStack fluid = fluidOf(entry);
        if (fluid != null) {
            tooltip.addLine(fluid.getLocalizedName());
        } else {
            tooltip.addFromItem(entry.getStack().getItem().unsafeMakeNormalStack(1));
        }
        tooltip.addLine(EnumChatFormatting.GRAY + "Network: " + describeAmount(entry, entry.getNetworkAmount()));
        tooltip.addLine(EnumChatFormatting.GRAY + "Internal: " + describeAmount(entry, entry.getInternalAmount()));
        if (tooltipShift) {
            tooltip.addLine(EnumChatFormatting.GRAY + "Total: " + describeAmount(entry, entry.getTotalAmount()));
        }
        if (entry.isCraftable()) {
            tooltip.addLine(EnumChatFormatting.YELLOW + "Craftable");
        }
    }

    // endregion

    // region input

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        List<RequestTableNetworkEntry> entries = entries();
        int mouseX = getContext().getMouseX();
        int mouseY = getContext().getMouseY();
        if (mouseButton == 0 && mouseX >= SCROLLBAR_X) {
            draggingScrollbar = true;
            int top = handleTop(visibleRows() * CELL, maxScroll(entries));
            // taken by the handle it keeps the spot it was taken at, a click on the track brings its middle there
            grabOffset = mouseY >= top && mouseY < top + HANDLE_HEIGHT ? mouseY - top : HANDLE_HEIGHT / 2;
            scrollTo(entries, mouseY);
            return Result.SUCCESS;
        }
        RequestTableNetworkEntry entry = entryAt(entries, mouseX, mouseY);
        // an item on the cursor goes into the storage of the table wherever it is dropped on the list; a fluid
        // container dropped on a fluid entry is filled from or emptied into the storage instead
        ItemStack held = Minecraft.getMinecraft().thePlayer.inventory.getItemStack();
        if (held != null && (mouseButton == 0 || mouseButton == 1) && (entry == null || !entry.isFluid())) {
            handler.interact(ItemIdentifierStack.getFromStack(held), false, mouseButton, false);
            return Result.SUCCESS;
        }
        if (entry == null) {
            return Result.ACCEPT;
        }
        boolean leftOrRight = mouseButton == 0 || mouseButton == 1;
        boolean requestMode = handler.getSettings().getClickMode() == ClickMode.REQUEST;
        boolean request = mouseButton == 2 || (leftOrRight && (Interactable.hasControlDown() || requestMode));
        if (request) {
            int startAmount = entry.isFluid() ? FLUID_AMOUNT : 1;
            if (Interactable.hasShiftDown() && entry.getNetworkAmount() > 0) {
                // with shift the request starts with all the network has
                startAmount = entry.getNetworkAmount();
            }
            requestOpener.open(entry, fluidOf(entry), startAmount);
            return Result.SUCCESS;
        }
        if (mouseButton == 0 || mouseButton == 1) {
            handler.interact(entry, mouseButton, Interactable.hasShiftDown());
            return Result.SUCCESS;
        }
        return Result.ACCEPT;
    }

    @Override
    public void onMouseDrag(int mouseButton, long timeSinceClick) {
        if (draggingScrollbar) {
            scrollTo(entries(), getContext().getMouseY());
        }
    }

    @Override
    public boolean onMouseRelease(int mouseButton) {
        draggingScrollbar = false;
        return false;
    }

    private void scrollTo(List<RequestTableNetworkEntry> entries, int mouseY) {
        int maxScroll = maxScroll(entries);
        int travel = visibleRows() * CELL - 2 - HANDLE_HEIGHT;
        if (maxScroll == 0 || travel <= 0) {
            scrollRow = 0;
            dragHandleTop = -1;
            return;
        }
        int position = Math.max(0, Math.min(travel, mouseY - grabOffset - 1));
        dragHandleTop = 1 + position;
        scrollRow = Math.round((float) position * maxScroll / travel);
    }

    @Override
    public boolean onMouseScroll(UpOrDown scrollDirection, int amount) {
        scrollRow = Math.max(0, Math.min(maxScroll(entries()), scrollRow - scrollDirection.modifier));
        return true;
    }

    // endregion
}
