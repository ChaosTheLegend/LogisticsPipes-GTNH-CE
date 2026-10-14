package logisticspipes.crafting.requesttable.gui.monitor;

import static logisticspipes.crafting.requesttable.gui.RequestTableRender.inside;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.crafting.monitor.CraftingMonitorData.Node;
import logisticspipes.crafting.monitor.CraftingMonitorData.State;
import logisticspipes.crafting.monitor.CraftingMonitorData.Summary;
import logisticspipes.crafting.requesttable.gui.RequestTableGuiStyle;
import logisticspipes.crafting.requesttable.gui.RequestTableIcons;
import logisticspipes.crafting.requesttable.gui.RequestTableLayout;
import logisticspipes.crafting.requesttable.gui.RequestTableRender;
import logisticspipes.crafting.requesttable.storage.RequestTableFluidDisplay;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.gui.GuiGraphics;
import logisticspipes.utils.item.ItemIdentifier;
import lombok.Getter;

/** Minecraft-style resource cards and a clipped, draggable rendering of LP's order-list tree. */
@SideOnly(Side.CLIENT)
public final class RequestTableMonitorPanel {

    private static final int CARD_WIDTH = 67;
    private static final int CARD_HEIGHT = 30;
    private static final int TAB_HEIGHT = 26;
    private static final int TAB_STEP = TAB_HEIGHT + 2;
    private static final int TAB_ARROW_HEIGHT = 12;
    private static final int CENTER_WIDTH = 52;
    private static final float[] ZOOM = { 1, .75F, .5F, .25F };
    private final Minecraft mc = Minecraft.getMinecraft();
    private final RequestTableMonitorModel model;
    private final Map<Long, View> views = new HashMap<>();
    private final List<Card> cards = new ArrayList<>();
    private final Map<Integer, Group> groups = new LinkedHashMap<>();
    private final Map<Integer, Point> positions = new HashMap<>();
    private List<Node> previousNodes;
    private long previousGeneration = -1;
    @Getter
    private boolean tree;
    private int tabScroll;
    private int tabsX, tabsY, tabsWidth, tabsHeight, tabRows, tabTop;
    private long visibleTab = -1;
    private long previousSelection = -1;
    private int x, y, width, top, height;
    private boolean dragging;
    private boolean draggingScroll;
    private int dragX, dragY, dragOffset;
    private float startPanX, startPanY;
    private List<String> tooltip;

    public RequestTableMonitorPanel(RequestTableMonitorModel model) {
        this.model = model;
    }

    private static int childrenWidth(Group group) {
        // Only gaps between siblings count; a trailing gap shifts even a single child off its parent's bus.
        int width = Math.max(0, group.children.size() - 1) * 12;
        for (Group child : group.children) width += child.width;
        return width;
    }

    private static List<String> lines(String... text) {
        List<String> lines = new ArrayList<>();
        java.util.Collections.addAll(lines, text);
        return lines;
    }

    private static String compact(long amount) {
        if (amount >= 1_000_000_000_000L) return amount / 1_000_000_000_000L + "T";
        if (amount >= 1_000_000_000L) return amount / 1_000_000_000L + "G";
        return RequestTableGuiStyle.formatCount((int) amount);
    }

    private static void line(int x0, int y0, int x1, int y1) {
        Gui.drawRect(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1) + 2, Math.max(y0, y1) + 2, 0xff555555);
        Gui.drawRect(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1) + 1, Math.max(y0, y1) + 1, 0xffc6c6c6);
    }

    public void render(RequestTableLayout layout, int mouseX, int mouseY) {
        model.maintain();
        x = layout.centralLeft + 13;
        y = layout.centralTop + 8;
        width = layout.centralWidth - 26;
        top = y + 37;
        height = Math.max(20, layout.guiTop + layout.ySize - 10 - top);
        tabsX = layout.centralLeft + layout.centralWidth - 1;
        tabsY = layout.sortModeButtonY;
        tabsWidth = layout.monitorButtonWidth + 7;
        tabsHeight = layout.guiTop + layout.ySize - 10 - tabsY;
        tooltip = null;
        rebuild();
        Summary summary = model.summary();
        mc.fontRenderer.drawString(
                mc.fontRenderer.trimStringToWidth(summary == null ? "Crafting monitor" : title(summary), width),
                x,
                y + 1,
                RequestTableGuiStyle.TEXT);
        if (summary != null && inside(mouseX, mouseY, x, y, width, 10)) {
            tooltip = lines(title(summary));
        }
        if (tree) button(x + width - CENTER_WIDTH, y + 15, CENTER_WIDTH, 16, "Center", mouseX, mouseY);
        if (summary != null) {
            long seconds = summary.elapsed / 20
                    + (summary.finished ? 0 : (System.currentTimeMillis() - model.receivedAt) / 1000);
            String status = summary.unfulfilled ? "Unfulfilled remainder" : summary.finished ? "Finished" : "Running";
            String time = seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
            int statusWidth = width - (tree ? CENTER_WIDTH + 7 : 0);
            mc.fontRenderer.drawString(
                    mc.fontRenderer.trimStringToWidth(status + "  " + time, statusWidth),
                    x,
                    y + 19,
                    summary.unfulfilled ? 0x9c3f32 : RequestTableGuiStyle.MUTED);
        }
        RequestTableGuiStyle.inset(x, top, width, height);
        if (model.nodes.isEmpty()) {
            String empty = model.summaries.isEmpty() ? "No crafting requests" : "Loading request...";
            mc.fontRenderer.drawString(empty, x + 5, top + 8, RequestTableGuiStyle.TEXT);
        } else if (tree) {
            drawTree(mouseX, mouseY);
        } else {
            drawGrid(mouseX, mouseY);
        }
        drawTabs(mouseX, mouseY);
        if (tree && inside(mouseX, mouseY, x + width - CENTER_WIDTH, y + 15, CENTER_WIDTH, 16))
            tooltip = lines("Center tree", "Reset position and zoom to 100%.");
        if (inside(mouseX, mouseY, x, y + 19, width - (tree ? CENTER_WIDTH + 7 : 0), 10)) tooltip = lines(
                "LP order status",
                "Active means the pipe is processing the order.",
                "Sent does not confirm insertion into the destination.",
                "Transport progress is shown only when LP already tracks it.");
    }

    public void setTree(boolean tree) {
        release();
        this.tree = tree;
    }

    public void drawTooltip(int mouseX, int mouseY) {
        if (tooltip != null) GuiGraphics.drawToolTip(mouseX, mouseY, tooltip, EnumChatFormatting.WHITE);
    }

    private void rebuild() {
        if (previousNodes != model.nodes) {
            previousNodes = model.nodes;
            groups.clear();
            positions.clear();
            for (Node node : model.nodes) if (node.group) groups.put(node.id, new Group(node));
            for (Node node : model.nodes) {
                Group parent = groups.get(node.parent);
                if (parent == null) continue;
                if (node.group) parent.children.add(groups.get(node.id));
                else parent.orders.add(node);
            }
            List<Group> reverse = new ArrayList<>(groups.values());
            for (int i = reverse.size() - 1; i >= 0; i--) {
                Group group = reverse.get(i);
                int children = childrenWidth(group);
                group.width = Math.max(32, Math.max(group.orders.size() * 32, children));
            }
            for (Group group : groups.values()) {
                float start = group.center - (group.orders.size() - 1) * 16;
                for (int i = 0; i < group.orders.size(); i++)
                    positions.put(group.orders.get(i).id, new Point(start + i * 32 - 12, group.depth * 64 + 12));
                int total = childrenWidth(group);
                float cursor = group.center - total / 2F;
                for (Group child : group.children) {
                    child.center = cursor + child.width / 2F;
                    child.depth = group.depth + 1;
                    cursor += child.width + 12;
                }
            }
        }
        if (previousGeneration == model.generation) return;
        previousGeneration = model.generation;
        cards.clear();
        Map<ItemIdentifier, Card> merged = new LinkedHashMap<>();
        Card locked = null;
        for (Node node : model.nodes) {
            if (node.group) continue;
            if (node.locked) {
                if (locked == null) {
                    locked = new Card(node);
                    cards.add(locked);
                }
                continue;
            }
            if (node.resource == null) continue;
            Card card = merged.get(node.resource.getItem());
            if (card == null) {
                card = new Card(node);
                merged.put(node.resource.getItem(), card);
                cards.add(card);
            }
            State state = state(node);
            if (state.finished) card.failed += state.remaining;
            else if (state.active) card.active += state.remaining;
            else card.waiting += state.remaining;
            card.sent += Math.max(0, (long) node.initial - state.remaining);
            card.initial += node.initial;
            card.machine = Math.max(card.machine, state.machine);
            if (!node.destination.isEmpty() && !card.targets.contains(node.destination))
                card.targets.add(node.destination);
        }
    }

    private void drawGrid(int mouseX, int mouseY) {
        View view = view();
        view.scroll = Math.max(0, Math.min(maxScroll(), view.scroll));
        int columns = columns();
        int first = view.scroll * columns;
        try (var ignored = RequestTableRender.guiState()) {
            RequestTableRender.clip(mc, x + 1, top + 1, width - 10, height - 2);
            int end = Math.min(cards.size(), first + columns * rows());
            for (int i = first; i < end; i++) {
                Card card = cards.get(i);
                int cx = x + 2 + (i - first) % columns * (CARD_WIDTH + 3);
                int cy = top + 2 + (i - first) / columns * (CARD_HEIGHT + 2);
                boolean hover = inside(mouseX, mouseY, cx, cy, CARD_WIDTH, CARD_HEIGHT)
                        && inside(mouseX, mouseY, x, top, width - 10, height);
                RequestTableGuiStyle
                        .bevel(cx, cy, CARD_WIDTH, CARD_HEIGHT, hover ? 0xffd4d4d4 : 0xffc6c6c6, 0xff555555);
                int color = card.failed > 0 ? 0xffb46656 : card.active > 0 ? 0xff83ad65 : 0xffbbbbbb;
                Gui.drawRect(cx + 1, cy + 1, cx + 2, cy + CARD_HEIGHT - 1, color);
                RequestTableGuiStyle.inset(cx + 4, cy + 6, 18, 18);
                icon(card.node, cx + 5, cy + 7);
                if (card.node.locked) {
                    small("Fluid", cx + 23, cy + 7, RequestTableGuiStyle.TEXT);
                    small("Locked", cx + 23, cy + 15, RequestTableGuiStyle.MUTED);
                } else {
                    String unit = card.node.fluid ? " mB" : "";
                    small("Open: " + compact(card.waiting) + unit, cx + 23, cy + 4, RequestTableGuiStyle.TEXT);
                    small("Active: " + compact(card.active) + unit, cx + 23, cy + 12, 0x405b30);
                    small("Sent: " + compact(card.sent) + unit, cx + 23, cy + 20, 0x655436);
                    if (card.machine > 0 && card.active > 0) Gui.drawRect(
                            cx + 2,
                            cy + CARD_HEIGHT - 3,
                            cx + 2 + (CARD_WIDTH - 4) * card.machine / 100,
                            cy + CARD_HEIGHT - 2,
                            0xff83ad65);
                }
                if (hover) tooltip = cardTooltip(card);
            }
        }
        RequestTableGuiStyle.inset(x + width - 8, top, 7, height);
        RequestTableGuiStyle.raised(x + width - 8, thumbTop(), 7, thumbHeight(), false);
    }

    private void drawTree(int mouseX, int mouseY) {
        View view = view();
        float zoom = ZOOM[view.zoom];
        float localX = (mouseX - x - width / 2F - view.panX) / zoom;
        float localY = (mouseY - top - 10 - view.panY) / zoom;
        try (var ignored = RequestTableRender.guiState()) {
            RequestTableRender.clip(mc, x + 1, top + 1, width - 2, height - 2);
            GL11.glTranslatef(x + width / 2F + view.panX, top + 10 + view.panY, 0);
            GL11.glScalef(zoom, zoom, 1);
            for (Group group : groups.values()) {
                int gx = Math.round(group.center);
                int gy = group.depth * 64;
                int left = gx, right = gx;
                for (Node node : group.orders) {
                    Point point = positions.get(node.id);
                    int nx = Math.round(point.x) + 12;
                    left = Math.min(left, nx);
                    right = Math.max(right, nx);
                    line(nx, gy - 6, nx, gy + 12);
                    if (!group.children.isEmpty()) line(nx, gy + 36, nx, gy + 40);
                    if (!node.locked) for (float progress : state(node).transport) {
                        int py = gy - 6 + Math.round(18 * Math.max(0, Math.min(1, progress)));
                        Gui.drawRect(nx - 1, py - 1, nx + 2, py + 2, 0xffc4ef9b);
                    }
                }
                line(left, gy - 6, right, gy - 6);
                if (!group.children.isEmpty()) {
                    if (group.orders.isEmpty()) line(gx, gy - 6, gx, gy + 40);
                    line(left, gy + 40, right, gy + 40);
                    line(gx, gy + 40, gx, gy + 58);
                    int childLeft = Math.min(gx, Math.round(group.children.get(0).center));
                    int childRight = Math.max(gx, Math.round(group.children.get(group.children.size() - 1).center));
                    line(childLeft, gy + 58, childRight, gy + 58);
                }
            }
            for (Node node : model.nodes) {
                if (node.group) continue;
                Point point = positions.get(node.id);
                int nx = Math.round(point.x), ny = Math.round(point.y);
                float sx = x + width / 2F + view.panX + nx * zoom;
                float sy = top + 10 + view.panY + ny * zoom;
                if (sx + 26 * zoom < x || sx > x + width || sy + 30 * zoom < top || sy > top + height) continue;
                State order = state(node);
                RequestTableGuiStyle
                        .raised(nx, ny, 25, 25, localX >= nx && localX < nx + 25 && localY >= ny && localY < ny + 25);
                RequestTableGuiStyle.inset(nx + 3, ny + 3, 18, 18);
                icon(node, nx + 4, ny + 4);
                int color = node.locked ? 0xffa49c8b
                        : order.finished && order.remaining > 0 ? 0xffb46656
                                : order.active ? 0xff83ad65 : order.finished ? 0xffaaaaaa : 0xffd1b978;
                Gui.drawRect(nx + 1, ny + 23, nx + 24, ny + 25, color);
                if (!node.locked) RequestTableGuiStyle
                        .drawCount(mc.fontRenderer, compact(order.remaining), nx + 5, ny + 5, 0xffffff, true);
                if (!node.locked && order.active && order.machine > 0) {
                    Gui.drawRect(nx, ny + 27, nx + 25, ny + 30, 0xff333333);
                    Gui.drawRect(nx + 1, ny + 28, nx + 1 + 23 * order.machine / 100, ny + 29, 0xff83ad65);
                }
                if (inside(mouseX, mouseY, x, top, width, height) && localX >= nx
                        && localX < nx + 25
                        && localY >= ny
                        && localY < ny + 25)
                    tooltip = nodeTooltip(node);
            }
        }
    }

    private void drawTabs(int mouseX, int mouseY) {
        int availableRows = Math.max(1, (tabsHeight + 2) / TAB_STEP);
        boolean overflow = model.summaries.size() > availableRows;
        tabRows = overflow ? Math.max(1, (tabsHeight - 2 * (TAB_ARROW_HEIGHT + 3) + 2) / TAB_STEP) : availableRows;
        tabTop = tabsY + (overflow ? TAB_ARROW_HEIGHT + 3 : 0);
        // Keep the first visible request anchored when new jobs arrive, unless selection changes.
        if (previousSelection != model.selected) {
            int selected = summaryIndex(model.selected);
            if (selected >= 0 && (selected < tabScroll || selected >= tabScroll + tabRows)) tabScroll = selected;
            previousSelection = model.selected;
        } else {
            int anchored = summaryIndex(visibleTab);
            if (anchored >= 0) tabScroll = anchored;
        }
        setTabScroll(tabScroll);
        if (overflow) {
            tabArrow(tabsY, true, tabScroll > 0, mouseX, mouseY);
            tabArrow(
                    tabsY + tabsHeight - TAB_ARROW_HEIGHT,
                    false,
                    tabScroll + tabRows < model.summaries.size(),
                    mouseX,
                    mouseY);
        }
        int rows = Math.min(tabRows, model.summaries.size() - tabScroll);
        for (int row = 0; row < rows; row++) {
            Summary summary = model.summaries.get(tabScroll + row);
            int cy = tabTop + row * TAB_STEP;
            boolean selected = summary.id == model.selected;
            int cx = tabsX + (selected ? 0 : 3);
            int tabWidth = tabsWidth - (selected ? 0 : 3);
            boolean hover = inside(mouseX, mouseY, cx, cy, tabWidth, TAB_HEIGHT);
            RequestTableGuiStyle.bevel(
                    cx,
                    cy,
                    tabWidth,
                    TAB_HEIGHT,
                    selected ? 0xffc6c6c6 : hover ? 0xffb8bec6 : 0xffaeaeae,
                    0xff373737);
            if (selected) {
                // Join the selected tab to the panel, like Minecraft's inventory tabs.
                Gui.drawRect(cx - 2, cy + 2, cx + 2, cy + TAB_HEIGHT - 2, 0xffc6c6c6);
                Gui.drawRect(cx + 2, cy + 1, cx + tabWidth - 2, cy + 2, 0xffe8c66a);
            }
            int slotX = cx + (tabWidth - 18) / 2;
            RequestTableGuiStyle.inset(slotX, cy + 4, 18, 18);
            icon(new Node(0, -1, false, summary.fluid, summary.locked, summary.result, 0, "", ""), slotX + 1, cy + 5);
            Gui.drawRect(cx + tabWidth - 5, cy + TAB_HEIGHT - 6, cx + tabWidth - 2, cy + TAB_HEIGHT - 3, 0xff373737);
            Gui.drawRect(
                    cx + tabWidth - 4,
                    cy + TAB_HEIGHT - 5,
                    cx + tabWidth - 2,
                    cy + TAB_HEIGHT - 3,
                    summary.unfulfilled ? 0xffb46656 : summary.finished ? 0xff999999 : 0xff83ad65);
            if (hover) tooltip = lines(
                    title(summary),
                    summary.unfulfilled ? "Unfulfilled remainder" : summary.finished ? "Finished" : "Running",
                    "Click to select. Scroll to browse requests.");
        }
    }

    private void tabArrow(int cy, boolean up, boolean enabled, int mouseX, int mouseY) {
        RequestTableGuiStyle.raised(
                tabsX + 3,
                cy,
                tabsWidth - 3,
                TAB_ARROW_HEIGHT,
                enabled && inside(mouseX, mouseY, tabsX + 3, cy, tabsWidth - 3, TAB_ARROW_HEIGHT));
        int center = tabsX + 3 + (tabsWidth - 3) / 2;
        for (int row = 0; row < 3; row++) {
            int py = cy + 4 + (up ? row : 2 - row);
            Gui.drawRect(center - row, py, center + row + 1, py + 1, enabled ? 0xff404040 : 0xff909090);
        }
        if (inside(mouseX, mouseY, tabsX + 3, cy, tabsWidth - 3, TAB_ARROW_HEIGHT))
            tooltip = lines(up ? "Previous crafting requests" : "Next crafting requests");
    }

    private int summaryIndex(long id) {
        for (int index = 0; index < model.summaries.size(); index++)
            if (model.summaries.get(index).id == id) return index;
        return -1;
    }

    private void setTabScroll(int scroll) {
        tabScroll = Math.max(0, Math.min(Math.max(0, model.summaries.size() - tabRows), scroll));
        visibleTab = model.summaries.isEmpty() ? -1 : model.summaries.get(tabScroll).id;
    }

    private void clickSound() {
        mc.getSoundHandler()
                .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1));
    }

    public boolean click(int mouseX, int mouseY, int button) {
        if (inside(mouseX, mouseY, tabsX, tabsY, tabsWidth, tabsHeight)) {
            if (button != 0) return true;
            boolean up = inside(mouseX, mouseY, tabsX + 3, tabsY, tabsWidth - 3, TAB_ARROW_HEIGHT);
            boolean down = inside(
                    mouseX,
                    mouseY,
                    tabsX + 3,
                    tabsY + tabsHeight - TAB_ARROW_HEIGHT,
                    tabsWidth - 3,
                    TAB_ARROW_HEIGHT);
            if (tabTop != tabsY && (up || down)) {
                int next = tabScroll + (up ? -tabRows : tabRows);
                if (up ? tabScroll > 0 : tabScroll + tabRows < model.summaries.size()) {
                    setTabScroll(next);
                    clickSound();
                }
            } else {
                int row = (mouseY - tabTop) / TAB_STEP;
                int index = tabScroll + row;
                if (mouseY >= tabTop && row < tabRows && index < model.summaries.size()) {
                    Summary summary = model.summaries.get(index);
                    int offset = summary.id == model.selected ? 0 : 3;
                    if (inside(
                            mouseX,
                            mouseY,
                            tabsX + offset,
                            tabTop + row * TAB_STEP,
                            tabsWidth - offset,
                            TAB_HEIGHT)) {
                        release();
                        model.select(summary.id);
                        clickSound();
                    }
                }
            }
            return true;
        }
        if (tree && inside(mouseX, mouseY, x + width - CENTER_WIDTH, y + 15, CENTER_WIDTH, 16)) {
            if (button == 0) {
                release();
                view().panX = view().panY = 0;
                view().zoom = 0;
                clickSound();
            }
            return true;
        }
        if (!inside(mouseX, mouseY, x, top, width, height)) return false;
        if (button == 0 && !tree && inside(mouseX, mouseY, x + width - 8, top, 7, height) && maxScroll() > 0) {
            draggingScroll = true;
            dragOffset = mouseY >= thumbTop() && mouseY < thumbTop() + thumbHeight() ? mouseY - thumbTop()
                    : thumbHeight() / 2;
            move(mouseX, mouseY);
        } else if (button == 0 && tree) {
            dragging = true;
            dragX = mouseX;
            dragY = mouseY;
            startPanX = view().panX;
            startPanY = view().panY;
        }
        return true;
    }

    public void move(int mouseX, int mouseY) {
        if (dragging) {
            view().panX = startPanX + mouseX - dragX;
            view().panY = startPanY + mouseY - dragY;
        }
        if (draggingScroll) view().scroll = Math.max(
                0,
                Math.min(
                        maxScroll(),
                        Math.round(
                                (mouseY - top - dragOffset) * (float) maxScroll()
                                        / Math.max(1, height - thumbHeight()))));
    }

    public void release() {
        dragging = draggingScroll = false;
    }

    public void scroll(int wheel, int mouseX, int mouseY) {
        if (wheel == 0) return;
        int step = wheel > 0 ? -1 : 1;
        if (inside(mouseX, mouseY, tabsX, tabsY, tabsWidth, tabsHeight)) {
            setTabScroll(tabScroll + step);
            return;
        }
        if (!inside(mouseX, mouseY, x, top, width, height)) return;
        View view = view();
        if (tree) {
            int next = Math.max(0, Math.min(ZOOM.length - 1, view.zoom + step));
            float ratio = ZOOM[next] / ZOOM[view.zoom];
            view.panX = mouseX - x - width / 2F - (mouseX - x - width / 2F - view.panX) * ratio;
            view.panY = mouseY - top - 10 - (mouseY - top - 10 - view.panY) * ratio;
            view.zoom = next;
        } else view.scroll = Math.max(0, Math.min(maxScroll(), view.scroll + step));
    }

    private View view() {
        return views.computeIfAbsent(model.selected, ignored -> new View());
    }

    private State state(Node node) {
        return model.states.getOrDefault(node.id, State.EMPTY);
    }

    private int columns() {
        return Math.max(1, (width - 10 + 3) / (CARD_WIDTH + 3));
    }

    private int rows() {
        return Math.max(1, (height - 4) / (CARD_HEIGHT + 2));
    }

    private int maxScroll() {
        return Math.max(0, (cards.size() + columns() - 1) / columns() - rows());
    }

    private int thumbHeight() {
        return Math.max(12, height * rows() / Math.max(rows(), rows() + maxScroll()));
    }

    private int thumbTop() {
        return top + (maxScroll() == 0 ? 0 : (height - thumbHeight()) * view().scroll / maxScroll());
    }

    private void button(int x, int y, int width, int height, String text, int mouseX, int mouseY) {
        RequestTableGuiStyle.raised(x, y, width, height, inside(mouseX, mouseY, x, y, width, height));
        String label = mc.fontRenderer.trimStringToWidth(text, width - 8);
        mc.fontRenderer.drawString(
                label,
                x + (width - mc.fontRenderer.getStringWidth(label)) / 2,
                y + (height - 8) / 2,
                RequestTableGuiStyle.TEXT);
    }

    private void small(String text, int x, int y, int color) {
        try (var ignored = RequestTableRender.guiState()) {
            GL11.glTranslatef(x, y, 0);
            GL11.glScalef(.5F, .5F, 1);
            mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth(text, 82), 0, 0, color);
        }
    }

    private void icon(Node node, int x, int y) {
        if (node.locked) {
            RequestTableIcons.monitor(x, y, false, false);
            return;
        }
        if (node.resource == null) return;
        ItemStack stack = node.resource.getItem().makeStack(1).makeNormalStack();
        if (node.fluid) {
            var fluid = SimpleServiceLocator.logisticsFluidManager.getFluidFromContainer(node.resource);
            if (fluid != null) stack = RequestTableFluidDisplay.icon(fluid);
        }
        // Native tile-entity items (notably chest lids) need both depth testing and depth writes.
        RequestTableRender.item(stack, x, y, 100, false);
    }

    private String name(Node node) {
        if (node.locked) return "Fluid details locked";
        if (node.resource == null) return "Unknown resource";
        try {
            if (node.fluid) {
                var fluid = SimpleServiceLocator.logisticsFluidManager.getFluidFromContainer(node.resource);
                if (fluid != null) return fluid.getLocalizedName();
            }
            return node.resource.makeNormalStack().getDisplayName();
        } catch (RuntimeException exception) {
            return "Unknown resource";
        }
    }

    private String title(Summary summary) {
        String resource = summary.locked ? "Fluid details locked"
                : summary.result == null ? "Crafting request"
                        : name(new Node(0, -1, false, summary.fluid, false, summary.result, 0, "", ""));
        String amount = summary.result == null || summary.locked || summary.batch ? ""
                : compact(summary.result.getStackSize()) + (summary.fluid ? " mB " : "x ");
        return "#" + summary.id + " " + (summary.batch ? "Batch: " : amount) + resource;
    }

    private List<String> nodeTooltip(Node node) {
        if (node.locked) return lines("Fluid details locked", "Requires the permanent fluid controller.");
        State state = state(node);
        String unit = node.fluid ? " mB" : "";
        List<String> lines = lines(
                name(node),
                "Requested: " + node.initial + unit,
                "Remaining: " + state.remaining + unit,
                "Sent: " + Math.max(0, (long) node.initial - state.remaining) + unit,
                state.finished ? state.remaining > 0 ? "Remainder not executed" : "Finished"
                        : state.active ? "Active LP order" : "Waiting",
                "Type: " + node.type);
        if (state.machine > 0 && state.active) lines.add("Machine progress: " + state.machine + "%");
        if (!node.destination.isEmpty()) lines.add("Destination: " + node.destination);
        return lines;
    }

    private List<String> cardTooltip(Card card) {
        if (card.node.locked) return lines("Fluid details locked", "Requires the permanent fluid controller.");
        String unit = card.node.fluid ? " mB" : "";
        List<String> lines = lines(
                name(card.node),
                "Requested: " + card.initial + unit,
                "Open: " + card.waiting + unit,
                "Active: " + card.active + unit,
                "Sent: " + card.sent + unit);
        if (card.failed > 0) lines.add(EnumChatFormatting.RED + "Remainder not executed: " + card.failed + unit);
        if (card.machine > 0 && card.active > 0) lines.add("Machine progress (maximum): " + card.machine + "%");
        for (int i = 0; i < Math.min(4, card.targets.size()); i++) lines.add("Destination: " + card.targets.get(i));
        if (card.targets.size() > 4) lines.add("And " + (card.targets.size() - 4) + " more destinations");
        return lines;
    }

    private static final class View {

        int scroll, zoom;
        float panX, panY;
    }

    private static final class Point {

        final float x, y;

        Point(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class Group {

        final Node node;
        final List<Node> orders = new ArrayList<>();
        final List<Group> children = new ArrayList<>();
        int width, depth;
        float center;

        Group(Node node) {
            this.node = node;
        }
    }

    private static final class Card {

        final Node node;
        final List<String> targets = new ArrayList<>();
        long initial, waiting, active, sent, failed;
        int machine;

        Card(Node node) {
            this.node = node;
        }
    }
}
