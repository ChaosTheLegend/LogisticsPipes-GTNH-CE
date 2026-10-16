package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.crafting.monitor.CraftingMonitorCancelPacket;
import logisticspipes.network.packets.crafting.monitor.CraftingMonitorRefreshPacket;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.gui.GuiGraphics;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;
import logisticspipes.utils.gui.SmallGuiButton;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.string.StringUtils;

public class CraftingMonitorGui extends LogisticsBaseGuiScreen {

    private static final String PREFIX = "gui.craftingmonitor.";
    private static final int REQUEST_VISIBLE_ROWS = 6;
    private static final int DETAIL_VISIBLE_ROWS = 8;
    private static final int REQUEST_ROW_HEIGHT = 27;
    private static final int DETAIL_ROW_HEIGHT = 20;
    private static final int CANCEL_BUTTON_BASE = 100;

    private final CraftingMonitorTileEntity tile;
    private List<PatternCraftingMonitorEntry> entries;
    private int selectedEntry;
    private int requestScroll;
    private int detailScroll;
    private int refreshTicks;
    private Object[] tooltip;

    public CraftingMonitorGui(CraftingMonitorTileEntity tile, List<PatternCraftingMonitorEntry> entries) {
        super(370, 230, 0, 0);
        this.tile = tile;
        this.entries = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
    }

    @Override
    public void initGui() {
        super.initGui();
        rebuildButtons();
        requestRefresh();
        Mouse.getDWheel();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            requestRefresh();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            requestRefresh();
        } else if (button.id == 1) {
            scrollRequests(-1);
        } else if (button.id == 2) {
            scrollRequests(1);
        } else if (button.id == 3) {
            scrollDetails(-1);
        } else if (button.id == 4) {
            scrollDetails(1);
        } else if (button.id >= CANCEL_BUTTON_BASE && button.id < CANCEL_BUTTON_BASE + REQUEST_VISIBLE_ROWS) {
            int entryIndex = requestScroll + button.id - CANCEL_BUTTON_BASE;
            if (entryIndex >= 0 && entryIndex < entries.size()) {
                button.enabled = false;
                MainProxy.sendPacketToServer(
                        PacketHandler.getPacket(CraftingMonitorCancelPacket.class)
                                .setInstanceId(entries.get(entryIndex).getInstanceId()).setTilePos(tile));
            }
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        tooltip = null;
        handleMouseWheel(mouseX, mouseY);
        GuiGraphics.drawGuiBackGround(mc, guiLeft, guiTop, right, bottom, zLevel, true);
        Gui.drawRect(guiLeft + 8, guiTop + 28, guiLeft + 164, guiTop + 194, 0x33000000);
        Gui.drawRect(guiLeft + 170, guiTop + 28, right - 8, guiTop + 194, 0x33000000);

        mc.fontRenderer.drawString(StringUtils.translate(PREFIX + "title"), guiLeft + 8, guiTop + 8, 0x404040);
        mc.fontRenderer.drawString(StringUtils.translate(PREFIX + "requests"), guiLeft + 10, guiTop + 18, 0x606060);
        mc.fontRenderer.drawString(StringUtils.translate(PREFIX + "recipes"), guiLeft + 172, guiTop + 18, 0x606060);
        drawRequests(mouseX, mouseY);
        drawSelectedRecipe(mouseX, mouseY);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (tooltip != null) {
            GuiGraphics.displayItemToolTip(tooltip, zLevel, 0, 0, true);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && mouseX >= guiLeft + 8
                && mouseX < guiLeft + 164
                && mouseY >= guiTop + 30
                && mouseY < guiTop + 30 + REQUEST_VISIBLE_ROWS * REQUEST_ROW_HEIGHT) {
            int clicked = requestScroll + (mouseY - guiTop - 30) / REQUEST_ROW_HEIGHT;
            if (clicked >= 0 && clicked < entries.size()) {
                selectedEntry = clicked;
                detailScroll = 0;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    public void handleContent(List<PatternCraftingMonitorEntry> updatedEntries) {
        UUID selectedId = selectedEntry >= 0 && selectedEntry < entries.size()
                ? entries.get(selectedEntry).getInstanceId()
                : null;
        entries = updatedEntries == null ? new ArrayList<>() : new ArrayList<>(updatedEntries);
        selectedEntry = findEntry(selectedId);
        if (selectedEntry < 0) {
            selectedEntry = entries.isEmpty() ? 0 : Math.min(selectedEntry, entries.size() - 1);
        }
        requestScroll = Math.max(0, Math.min(requestScroll, maxRequestScroll()));
        if (selectedEntry < requestScroll) {
            requestScroll = selectedEntry;
        } else if (selectedEntry >= requestScroll + REQUEST_VISIBLE_ROWS) {
            requestScroll = selectedEntry - REQUEST_VISIBLE_ROWS + 1;
        }
        detailScroll = Math.max(0, Math.min(detailScroll, maxDetailScroll()));
        rebuildButtons();
    }

    public boolean isFor(CraftingMonitorTileEntity otherTile) {
        return otherTile != null && tile.xCoord == otherTile.xCoord
                && tile.yCoord == otherTile.yCoord
                && tile.zCoord == otherTile.zCoord;
    }

    private void rebuildButtons() {
        buttonList.clear();
        buttonList.add(
                new SmallGuiButton(0, guiLeft + 8, guiTop + 205, 52, 12, StringUtils.translate(PREFIX + "refresh")));
        buttonList.add(new SmallGuiButton(1, guiLeft + 64, guiTop + 205, 14, 12, "^"));
        buttonList.add(new SmallGuiButton(2, guiLeft + 80, guiTop + 205, 14, 12, "v"));
        buttonList.add(new SmallGuiButton(3, right - 44, guiTop + 205, 14, 12, "^"));
        buttonList.add(new SmallGuiButton(4, right - 28, guiTop + 205, 14, 12, "v"));
        for (int row = 0; row < REQUEST_VISIBLE_ROWS; row++) {
            int entryIndex = requestScroll + row;
            if (entryIndex >= entries.size()) {
                break;
            }
            buttonList.add(
                    new SmallGuiButton(
                            CANCEL_BUTTON_BASE + row,
                            guiLeft + 119,
                            guiTop + 36 + row * REQUEST_ROW_HEIGHT,
                            40,
                            12,
                            StringUtils.translate(PREFIX + "cancel")));
        }
    }

    private void drawRequests(int mouseX, int mouseY) {
        if (entries.isEmpty()) {
            mc.fontRenderer.drawString(StringUtils.translate(PREFIX + "empty"), guiLeft + 18, guiTop + 100, 0x707070);
            return;
        }
        for (int row = 0; row < REQUEST_VISIBLE_ROWS; row++) {
            int entryIndex = requestScroll + row;
            if (entryIndex >= entries.size()) {
                break;
            }
            int rowTop = guiTop + 30 + row * REQUEST_ROW_HEIGHT;
            PatternCraftingMonitorEntry entry = entries.get(entryIndex);
            if (entryIndex == selectedEntry) {
                Gui.drawRect(guiLeft + 9, rowTop, guiLeft + 163, rowTop + 25, 0x5555aaff);
            }
            ItemIdentifierStack display = entry.getDisplayStack();
            if (display != null) {
                renderStack(display, guiLeft + 12, rowTop + 4, mouseX, mouseY, Collections.emptyList());
                String name = StringUtils.getCuttedString(display.getItem().getFriendlyName(), 80, mc.fontRenderer);
                mc.fontRenderer.drawString(name, guiLeft + 32, rowTop + 5, 0x404040);
                String status;
                int statusColor;
                if (entry.isRestoring()) {
                    status = StringUtils.translate(PREFIX + "restoring") + " "
                            + entry.getRestoreAttempts()
                            + "/"
                            + entry.getMaxRestoreAttempts();
                    statusColor = 0xa04020;
                } else if (entry.isInProgress()) {
                    status = StringUtils.translate(PREFIX + "active");
                    statusColor = 0x208020;
                } else {
                    status = StringUtils.translate(PREFIX + "queued");
                    statusColor = 0x806020;
                }
                mc.fontRenderer.drawString(
                        StringUtils.getCuttedString(status, 80, mc.fontRenderer),
                        guiLeft + 32,
                        rowTop + 15,
                        statusColor);
            }
        }
    }

    private void drawSelectedRecipe(int mouseX, int mouseY) {
        List<NodeRow> rows = selectedRows();
        if (rows.isEmpty()) {
            if (!entries.isEmpty()) {
                mc.fontRenderer
                        .drawString(StringUtils.translate(PREFIX + "nodetails"), guiLeft + 185, guiTop + 100, 0x707070);
            }
            return;
        }
        for (int row = 0; row < DETAIL_VISIBLE_ROWS; row++) {
            int detailIndex = detailScroll + row;
            if (detailIndex >= rows.size()) {
                break;
            }
            NodeRow nodeRow = rows.get(detailIndex);
            PatternCraftingMonitorNode node = nodeRow.node;
            int rowTop = guiTop + 31 + row * DETAIL_ROW_HEIGHT;
            int indent = Math.min(42, nodeRow.depth * 10);
            if (nodeRow.depth == 0) {
                Gui.drawRect(guiLeft + 171, rowTop - 1, right - 9, rowTop + 18, 0x2255aaff);
            }
            if (node.getStack() == null) {
                continue;
            }
            if (nodeRow.depth > 0) {
                mc.fontRenderer.drawString("-", guiLeft + 176 + indent, rowTop + 5, 0x808080);
            }
            List<String> details = new ArrayList<>();
            details.add(
                    EnumChatFormatting.BLUE + StringUtils.translate(PREFIX + "needed")
                            + ": "
                            + EnumChatFormatting.YELLOW
                            + node.getStack().getStackSize());
            details.add(
                    EnumChatFormatting.BLUE + StringUtils.translate(PREFIX + "unrequested")
                            + ": "
                            + EnumChatFormatting.YELLOW
                            + node.getUnrequestedAmount());
            details.add(
                    EnumChatFormatting.BLUE + StringUtils.translate(PREFIX + "ordered")
                            + ": "
                            + EnumChatFormatting.YELLOW
                            + node.getOrderedAmount());
            renderStack(node.getStack(), guiLeft + 184 + indent, rowTop, mouseX, mouseY, details);
            int textLeft = guiLeft + 204 + indent;
            int textWidth = Math.max(20, right - 14 - textLeft);
            String name = StringUtils
                    .getCuttedString(node.getStack().getItem().getFriendlyName(), textWidth, mc.fontRenderer);
            mc.fontRenderer.drawString(name, textLeft, rowTop + 2, 0x404040);
            String state = node.getUnrequestedAmount() > 0
                    ? node.getUnrequestedAmount() + " " + StringUtils.translate(PREFIX + "waiting")
                    : node.getOrderedAmount() + " " + StringUtils.translate(PREFIX + "orderedshort");
            mc.fontRenderer.drawString(state, textLeft, rowTop + 11, node.isInProgress() ? 0x208020 : 0x707070);
        }
    }

    private void renderStack(ItemIdentifierStack identifierStack, int x, int y, int mouseX, int mouseY,
            List<String> details) {
        ItemStack stack = identifierStack.makeNormalStack();
        int amount = Math.max(0, stack.stackSize);
        stack.stackSize = Math.max(1, stack.stackSize);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        String amountText = StringUtils.getFormatedStackSize(amount, false);
        mc.fontRenderer
                .drawStringWithShadow(amountText, x + 17 - mc.fontRenderer.getStringWidth(amountText), y + 9, 0xffffff);
        if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
            tooltip = new Object[] { mouseX - 10, mouseY, stack, true, details };
        }
    }

    private void handleMouseWheel(int mouseX, int mouseY) {
        int wheel = Mouse.getDWheel();
        if (wheel == 0) {
            return;
        }
        int direction = wheel > 0 ? -1 : 1;
        if (mouseX < guiLeft + 167) {
            scrollRequests(direction);
        } else {
            scrollDetails(direction);
        }
    }

    private void scrollRequests(int direction) {
        int oldScroll = requestScroll;
        requestScroll = Math.max(0, Math.min(maxRequestScroll(), requestScroll + direction));
        if (oldScroll != requestScroll) {
            rebuildButtons();
        }
    }

    private void scrollDetails(int direction) {
        detailScroll = Math.max(0, Math.min(maxDetailScroll(), detailScroll + direction));
    }

    private int maxRequestScroll() {
        return Math.max(0, entries.size() - REQUEST_VISIBLE_ROWS);
    }

    private int maxDetailScroll() {
        return Math.max(0, selectedRows().size() - DETAIL_VISIBLE_ROWS);
    }

    private List<NodeRow> selectedRows() {
        if (selectedEntry < 0 || selectedEntry >= entries.size()) {
            return Collections.emptyList();
        }
        List<NodeRow> rows = new ArrayList<>();
        for (PatternCraftingMonitorNode root : entries.get(selectedEntry).getRoots()) {
            flatten(root, 0, rows);
        }
        return rows;
    }

    private void flatten(PatternCraftingMonitorNode node, int depth, List<NodeRow> rows) {
        rows.add(new NodeRow(node, depth));
        for (PatternCraftingMonitorNode child : node.getChildren()) {
            flatten(child, depth + 1, rows);
        }
    }

    private int findEntry(UUID instanceId) {
        if (instanceId != null) {
            for (int i = 0; i < entries.size(); i++) {
                if (instanceId.equals(entries.get(i).getInstanceId())) {
                    return i;
                }
            }
        }
        return entries.isEmpty() ? -1 : Math.min(selectedEntry, entries.size() - 1);
    }

    private void requestRefresh() {
        MainProxy.sendPacketToServer(PacketHandler.getPacket(CraftingMonitorRefreshPacket.class).setTilePos(tile));
    }

    private static class NodeRow {

        private final PatternCraftingMonitorNode node;
        private final int depth;

        private NodeRow(PatternCraftingMonitorNode node, int depth) {
            this.node = node;
            this.depth = depth;
        }
    }
}
