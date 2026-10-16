package logisticspipes.gui.popup;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import logisticspipes.crafting.PatternCraftingMonitorNode;
import logisticspipes.pipes.PipeBlockRequestTable;
import logisticspipes.utils.Color;
import logisticspipes.utils.gui.GuiGraphics;
import logisticspipes.utils.gui.SimpleGraphics;
import logisticspipes.utils.gui.SubGuiScreen;
import logisticspipes.utils.string.ChatColor;
import logisticspipes.utils.string.StringUtils;

public class PatternRequestMonitorPopup extends SubGuiScreen {

    private static final int NODE_SPACING_X = 46;
    private static final int NODE_SPACING_Y = 58;

    private final PipeBlockRequestTable table;
    private final int orderId;
    private final RenderItem renderItem = new RenderItem();
    private int scrollY = 0;
    private Object[] tooltip = null;

    public PatternRequestMonitorPopup(PipeBlockRequestTable table, int orderId) {
        super(280, 210, 0, 0);
        this.table = table;
        this.orderId = orderId;
        Mouse.getDWheel();
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        buttonList.add(new GuiButton(0, width / 2 - 40, height / 2 + 82, 80, 20, "Close"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            exitGui();
        }
    }

    @Override
    protected void renderToolTips(int mouseX, int mouseY, float par3) {
        if (tooltip != null) {
            GuiGraphics.displayItemToolTip(tooltip, zLevel, guiLeft, guiTop, true);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {}

    @Override
    protected void renderGuiBackground(int mouseX, int mouseY) {
        if (!table.watchedRequests.containsKey(orderId)) {
            exitGui();
            return;
        }
        tooltip = null;
        List<PatternCraftingMonitorNode> roots = table.watchedPatternCraftingRequests.get(orderId);
        GuiGraphics.drawGuiBackGround(mc, guiLeft, guiTop, right, bottom, zLevel, true);
        mc.fontRenderer.drawString("Pattern Request Tree #" + orderId, guiLeft + 10, guiTop + 8, 0x404040);
        if (roots == null || roots.isEmpty()) {
            mc.fontRenderer.drawString("No staged pattern data for this request.", guiLeft + 24, guiTop + 96, 0x404040);
            return;
        }

        int wheel = Mouse.getDWheel();
        if (wheel != 0) {
            scrollY -= wheel / 8;
        }

        int contentHeight = calculateContentHeight(roots);
        int viewportHeight = ySize - 48;
        int maxScroll = Math.max(0, contentHeight - viewportHeight);
        scrollY = Math.max(0, Math.min(maxScroll, scrollY));

        int y = guiTop + 34 - scrollY;
        for (PatternCraftingMonitorNode root : roots) {
            renderNode(root, guiLeft + xSize / 2, y, mouseX, mouseY);
            y += root.getDepth() * NODE_SPACING_Y + 24;
        }
        drawScrollHint(maxScroll);
    }

    private int calculateContentHeight(List<PatternCraftingMonitorNode> roots) {
        int height = 0;
        for (PatternCraftingMonitorNode root : roots) {
            height += root.getDepth() * NODE_SPACING_Y + 24;
        }
        return height;
    }

    private void renderNode(PatternCraftingMonitorNode node, int centerX, int y, int mouseX, int mouseY) {
        int childTotalWidth = 0;
        for (PatternCraftingMonitorNode child : node.getChildren()) {
            childTotalWidth += child.getTreeRootSize() * NODE_SPACING_X;
        }
        int childStart = centerX - childTotalWidth / 2;
        for (PatternCraftingMonitorNode child : node.getChildren()) {
            int childWidth = child.getTreeRootSize() * NODE_SPACING_X;
            int childCenter = childStart + childWidth / 2;
            drawConnection(centerX, y, childCenter, y + NODE_SPACING_Y);
            renderNode(child, childCenter, y + NODE_SPACING_Y, mouseX, mouseY);
            childStart += childWidth;
        }
        renderNodeIcon(node, centerX - 9, y - 9, mouseX, mouseY);
    }

    private void drawConnection(int parentX, int parentY, int childX, int childY) {
        int midY = parentY + (childY - parentY) / 2;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        SimpleGraphics.drawVerticalLine(parentX, parentY + 12, midY, Color.GREEN, 1);
        SimpleGraphics.drawHorizontalLine(Math.min(parentX, childX), Math.max(parentX, childX), midY, Color.GREEN, 1);
        SimpleGraphics.drawVerticalLine(childX, midY, childY - 12, Color.GREEN, 1);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private void renderNodeIcon(PatternCraftingMonitorNode node, int x, int y, int mouseX, int mouseY) {
        if (y < guiTop + 18 || y > bottom - 34 || node.getStack() == null) {
            return;
        }
        GuiGraphics.drawSlotBackground(mc, x - 1, y - 1);
        if (node.isInProgress()) {
            Gui.drawRect(x - 2, y - 2, x + 20, y, 0xff55ff55);
            Gui.drawRect(x - 2, y + 18, x + 20, y + 20, 0xff55ff55);
            Gui.drawRect(x - 2, y, x, y + 18, 0xff55ff55);
            Gui.drawRect(x + 18, y, x + 20, y + 18, 0xff55ff55);
        }
        ItemStack stack = node.getStack().makeNormalStack();
        int visibleAmount = Math.max(0, stack.stackSize);
        stack.stackSize = Math.max(1, stack.stackSize);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderHelper.enableGUIStandardItemLighting();
        renderItem.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y);
        renderItem.renderItemOverlayIntoGUI(mc.fontRenderer, mc.renderEngine, stack, x, y, "");
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        renderItem.zLevel = 0.0F;
        String amount = StringUtils.getFormatedStackSize(visibleAmount, false);
        mc.fontRenderer.drawStringWithShadow(amount, x + 17 - mc.fontRenderer.getStringWidth(amount), y + 9, 16777215);
        if (mouseX >= x - 2 && mouseX < x + 20 && mouseY >= y - 2 && mouseY < y + 20) {
            List<String> tooltipText = new ArrayList<>();
            tooltipText.add(ChatColor.BLUE + "Still needed: " + ChatColor.YELLOW + visibleAmount);
            tooltipText.add(ChatColor.BLUE + "Not requested yet: " + ChatColor.YELLOW + node.getUnrequestedAmount());
            tooltipText.add(ChatColor.BLUE + "Live orders: " + ChatColor.YELLOW + node.getOrderedAmount());
            tooltip = new Object[] { mouseX - 10, mouseY, stack, true, tooltipText };
        }
    }

    private void drawScrollHint(int maxScroll) {
        if (maxScroll <= 0) {
            return;
        }
        int barTop = guiTop + 25;
        int barBottom = bottom - 30;
        int barHeight = Math.max(12, (barBottom - barTop) * (barBottom - barTop) / (barBottom - barTop + maxScroll));
        int barY = barTop + (barBottom - barTop - barHeight) * scrollY / maxScroll;
        Gui.drawRect(right - 10, barTop, right - 7, barBottom, 0x66000000);
        Gui.drawRect(right - 10, barY, right - 7, barY + barHeight, 0xff808080);
    }
}
