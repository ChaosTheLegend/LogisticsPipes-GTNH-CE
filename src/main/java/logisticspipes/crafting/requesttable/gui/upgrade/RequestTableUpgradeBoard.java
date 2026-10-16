package logisticspipes.crafting.requesttable.gui.upgrade;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.crafting.requesttable.gui.RequestTableGuiStyle;
import logisticspipes.crafting.requesttable.gui.RequestTableRender;
import logisticspipes.crafting.requesttable.gui.upgrade.RequestTableBoardLayout.Node;
import logisticspipes.crafting.requesttable.upgrade.RequestTableStorageUpgradeConfig;
import logisticspipes.crafting.requesttable.upgrade.RequestTableUpgradeBranch;
import logisticspipes.crafting.requesttable.upgrade.RequestTableUpgradeContainer;
import logisticspipes.crafting.requesttable.upgrade.RequestTableUpgradeMaterials;
import logisticspipes.crafting.requesttable.upgrade.RequestTableUpgradeMaterials.Requirement;

/** One motherboard with four storage buses fed by a CPU and a separate fluid controller. */
@SideOnly(Side.CLIENT)
final class RequestTableUpgradeBoard {

    private static final ResourceLocation BACKGROUND = RequestTableRender.texture("requesttable_board");
    private static final int DRAG_THRESHOLD = 4;
    private static final float[] ZOOM_LEVELS = {0.15F, 0.25F, 0.5F, 0.75F, 1.0F, 1.5F};
    private final List<Node> nodes = RequestTableBoardLayout.createNodes();
    private final RequestTableUpgradeAssembly assembly = new RequestTableUpgradeAssembly();
    private final int[] observedTiers = new int[RequestTableUpgradeBranch.values().length];
    private boolean progressInitialized;
    private int left;
    private int top;
    private int width;
    private int height;
    private float panX;
    private float panY;
    private float zoom = 1;
    private boolean positioned;
    private int dragButton = -1;
    private int dragStartX;
    private int dragStartY;
    private float dragPanX;
    private float dragPanY;
    private boolean moved;
    private Node pressedNode;

    private static void trace(int x1, int y1, int x2, int y2, int color) {
        Gui.drawRect(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, color);
    }

    private static void drawDrop(int x, int y, int color) {
        Gui.drawRect(x + 7, y, x + 9, y + 4, color);
        Gui.drawRect(x + 5, y + 4, x + 11, y + 6, color);
        Gui.drawRect(x + 3, y + 6, x + 13, y + 13, color);
        Gui.drawRect(x + 5, y + 13, x + 11, y + 15, color);
    }

    private static void drawMonitor(int x, int y) {
        Gui.drawRect(x, y, x + 20, y + 12, 0xff50634b);
        Gui.drawRect(x + 2, y + 2, x + 18, y + 10, 0xff18241d);
        trace(x + 4, y + 7, x + 7, y + 7, 0xff98aa6b);
        trace(x + 7, y + 4, x + 7, y + 7, 0xff98aa6b);
        trace(x + 7, y + 4, x + 12, y + 4, 0xff98aa6b);
        trace(x + 12, y + 4, x + 12, y + 7, 0xff98aa6b);
        trace(x + 12, y + 7, x + 15, y + 7, 0xff98aa6b);
    }

    private static void addCosts(List<String> tooltip, Node node, RequestTableStorageUpgradeConfig config) {
        tooltip.add("Required materials:");
        for (Requirement requirement : RequestTableUpgradeMaterials.getRecipe(node.branch, node.tier, config)) {
            tooltip.add(requirement.getCount() + "x " + requirement.getLabel());
        }
    }

    void setArea(int left, int top, int width, int height) {
        if (positioned) {
            panX += (width - this.width) / 2.0F;
            panY += (height - this.height) / 2.0F;
        }
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
    }

    void initializeView(RequestTableUpgradeBranch branch, int tier) {
        if (!positioned) centerOnUpgrade(branch, tier);
    }

    void updateProgress(RequestTableUpgradeContainer upgrades) {
        // Initial state and reopening a table must not assemble already installed chips again.
        if (!upgrades.isReady()) return;
        for (RequestTableUpgradeBranch branch : RequestTableUpgradeBranch.values()) {
            int previous = observedTiers[branch.ordinal()];
            int installed = upgrades.getTable().getUpgradeTier(branch);
            if (progressInitialized && installed > previous) {
                for (int tier = previous + 1; tier <= installed; tier++) {
                    assembly.start(findUpgrade(branch, branch.isSpecial() ? 0 : tier));
                }
            }
            observedTiers[branch.ordinal()] = installed;
        }
        progressInitialized = true;
    }

    void centerOnUpgrade(RequestTableUpgradeBranch branch, int tier) {
        cancelDrag();
        positioned = true;
        Node node = findUpgrade(branch, tier);
        panX = width / 2.0F - node.x * zoom;
        panY = height / 2.0F - node.y * zoom;
    }

    void center() {
        centerOnUpgrade(null, 0);
    }

    void showAll() {
        cancelDrag();
        positioned = true;
        zoom = Math
            .min((width - 8.0F) / RequestTableBoardLayout.WIDTH, (height - 8.0F) / RequestTableBoardLayout.HEIGHT);
        panX = (width - RequestTableBoardLayout.WIDTH * zoom) / 2.0F;
        panY = (height - RequestTableBoardLayout.HEIGHT * zoom) / 2.0F;
    }

    void changeZoom(boolean increase) {
        float target = zoom;
        if (increase) {
            for (float level : ZOOM_LEVELS) {
                if (level > zoom + 0.01F) {
                    target = level;
                    break;
                }
            }
        } else {
            for (int i = ZOOM_LEVELS.length - 1; i >= 0; i--) {
                if (ZOOM_LEVELS[i] < zoom - 0.01F) {
                    target = ZOOM_LEVELS[i];
                    break;
                }
            }
        }
        float x = (width / 2.0F - panX) / zoom;
        float y = (height / 2.0F - panY) / zoom;
        zoom = target;
        panX = width / 2.0F - x * zoom;
        panY = height / 2.0F - y * zoom;
        positioned = true;
        cancelDrag();
    }

    private Node findUpgrade(RequestTableUpgradeBranch branch, int tier) {
        for (Node node : nodes) if (node.branch == branch && node.tier == tier) return node;
        return nodes.get(0);
    }

    boolean contains(int mouseX, int mouseY) {
        return mouseX >= left + 1 && mouseX < left + width - 1 && mouseY >= top + 1 && mouseY < top + height - 1;
    }

    boolean startDrag(int mouseX, int mouseY, int button) {
        if ((button != 0 && button != 2) || !contains(mouseX, mouseY)) return false;
        if (isDragging()) return true;
        positioned = true;
        dragButton = button;
        dragStartX = mouseX;
        dragStartY = mouseY;
        dragPanX = panX;
        dragPanY = panY;
        moved = false;
        pressedNode = nodeAt(mouseX, mouseY);
        return true;
    }

    boolean isDragging() {
        return dragButton >= 0;
    }

    int getDragButton() {
        return dragButton;
    }

    void drag(int mouseX, int mouseY) {
        int dx = mouseX - dragStartX;
        int dy = mouseY - dragStartY;
        moved |= Math.max(Math.abs(dx), Math.abs(dy)) > DRAG_THRESHOLD;
        if (moved) {
            panX = dragPanX + dx;
            panY = dragPanY + dy;
        }
    }

    Node release(int mouseX, int mouseY, int button) {
        if (button != dragButton) return null;
        drag(mouseX, mouseY);
        Node selected = button == 0 && !moved
            && pressedNode != null
            && pressedNode.branch != null
            && nodeAt(mouseX, mouseY) == pressedNode ? pressedNode : null;
        cancelDrag();
        return selected;
    }

    void cancelDrag() {
        dragButton = -1;
        pressedNode = null;
    }

    void scroll(int wheel, boolean horizontal) {
        int distance = wheel > 0 ? 32 : -32;
        positioned = true;
        if (horizontal) {
            panX += distance;
            dragPanX += distance;
        } else {
            panY += distance;
            dragPanY += distance;
        }
    }

    void draw(Minecraft mc, RequestTableUpgradeContainer upgrades, int mouseX, int mouseY) {
        try (var ignored = RequestTableRender.guiState()) {
            RequestTableGuiStyle.inset(left, top, width, height);
            RequestTableRender.clip(mc, left + 1, top + 1, width - 2, height - 2);
            Gui.drawRect(left, top, left + width, top + height, 0xff18291f);
            GL11.glTranslatef(left + panX, top + panY, 0);
            GL11.glScalef(zoom, zoom, 1);
            RequestTableRender.texture(BACKGROUND, 0, 0, RequestTableBoardLayout.WIDTH, RequestTableBoardLayout.HEIGHT);
            Node hover = isDragging() ? null : nodeAt(mouseX, mouseY);
            for (Node node : nodes) {
                float x = left + panX + node.getLeft() * zoom;
                float y = top + panY + node.getTop() * zoom;
                if (x + node.width * zoom <= left || x >= left + width
                    || y + node.height * zoom <= top
                    || y >= top + height)
                    continue;
                drawNode(node, upgrades, node == hover);
            }
        }
    }

    private void drawNode(Node node, RequestTableUpgradeContainer upgrades, boolean hover) {
        boolean cpu = node.branch == null;
        int installed = cpu ? 0 : upgrades.getTable().getUpgradeTier(node.branch);
        boolean complete = cpu || (node.branch.isSpecial() ? installed > 0 : node.tier <= installed);
        boolean enabled = cpu || !node.branch.isFluid()
            || node.branch.isSpecial()
            || upgrades.getTable().isFluidEnabled();
        RequestTableUpgradeStatus status = cpu ? RequestTableUpgradeStatus.APPLIED
            : RequestTableUpgradeStatus.of(upgrades, node.branch, node.tier);
        boolean unlocked = status == RequestTableUpgradeStatus.APPLIED || status == RequestTableUpgradeStatus.AVAILABLE;
        boolean selected = node.branch == upgrades.getSelectedBranch() && node.tier == upgrades.getSelectedTier();
        int x = node.getLeft();
        int y = node.getTop();
        int rim = complete ? 0xff98aa6b : unlocked ? 0xff8b875a : 0xff415b40;
        if (!complete && node.branch.isSpecial()) {
            Gui.drawRect(x, y, x + node.width, y + node.height, 0xff18241d);
            Gui.drawRect(x + 1, y + 1, x + node.width - 1, y + node.height - 1, rim);
            Gui.drawRect(x + 3, y + 3, x + node.width - 3, y + node.height - 3, 0xff1a2e25);
        }
        if (!complete || !assembly.draw(node)) {
            if (cpu) {
                RequestTableSpecialChips.draw(node);
            } else {
                switch (node.branch) {
                    case FLUID_CONTROLLER, CRAFTING_MONITORING -> {
                        if (complete) RequestTableSpecialChips.draw(node);
                        else if (node.branch.isFluid()) drawDrop(node.x - 8, node.y - 8, 0xff5a8177);
                        else drawMonitor(node.x - 10, node.y - 6);
                    }
                    case SLOT_COUNT, SLOT_SIZE, FLUID_SLOT_COUNT, FLUID_SLOT_SIZE -> {
                        if (complete) {
                            RequestTableUpgradeChips.draw(node.tier, node.branch, x, y);
                            if (!enabled) Gui.drawRect(x, y, x + node.width, y + node.height, 0x771a2e25);
                        } else {
                            // The authored board supplies the empty socket.
                            Gui.drawRect(x + 4, y + node.height - 7, x + 7, y + node.height - 4, rim);
                        }
                    }
                }
            }
        }
        if (selected || hover) {
            RequestTableGuiStyle.border(x, y, node.width, node.height, selected ? 0xffb1b79b : 0xff98aa6b);
        }
    }

    private Node nodeAt(int mouseX, int mouseY) {
        if (!contains(mouseX, mouseY)) return null;
        float boardX = (mouseX - left - panX) / zoom;
        float boardY = (mouseY - top - panY) / zoom;
        for (Node node : nodes) {
            int x = node.getLeft();
            int y = node.getTop();
            if (boardX >= x && boardX < x + node.width && boardY >= y && boardY < y + node.height) return node;
        }
        return null;
    }

    List<String> tooltip(int mouseX, int mouseY, RequestTableUpgradeContainer upgrades) {
        if (isDragging()) return null;
        Node node = nodeAt(mouseX, mouseY);
        if (node == null) return null;
        if (node.branch == null) {
            return Arrays.asList("Base request table / CPU", "Feeds the item, fluid and monitoring buses.");
        }
        RequestTableUpgradeStatus status = RequestTableUpgradeStatus.of(upgrades, node.branch, node.tier);
        RequestTableStorageUpgradeConfig config = upgrades.getConfig(node.branch.isFluid());
        String heading = node.branch.getLabel()
            + (node.branch.isSpecial() ? "" : " " + RequestTableStorageUpgradeConfig.getTierName(node.tier));
        var tooltip = new ArrayList<>(Arrays.asList(heading, status.hint(node.tier)));
        if (node.branch.isSpecial()) {
            tooltip.add(
                node.branch.isFluid() ? "Enables both fluid buses, storage, requests and cell handling."
                    : "Enables crafting monitoring.");
            tooltip.add("Retained when the table is broken and placed again.");
        } else {
            int installed = upgrades.getTable().getUpgradeTier(node.branch);
            switch (status) {
                case APPLIED -> {
                    tooltip.add("Total: " + config.getTotal(node.branch, installed) + " " + node.branch.getUnit());
                    tooltip.add("Provides +" + config.getBonus(node.branch, node.tier) + " " + node.branch.getUnit());
                }
                case AVAILABLE -> tooltip.add(
                    config.getTotal(node.branch, installed) + " + "
                        + EnumChatFormatting.DARK_GREEN
                        + config.getBonus(node.branch, node.tier)
                        + EnumChatFormatting.RESET
                        + " -> "
                        + config.getTotal(node.branch, node.tier)
                        + " "
                        + node.branch.getUnit());
                case PREVIOUS_REQUIRED, FLUID_CONTROLLER_REQUIRED -> tooltip.add(
                    "Provides " + EnumChatFormatting.DARK_GREEN
                        + "+"
                        + config.getBonus(node.branch, node.tier)
                        + EnumChatFormatting.RESET
                        + " "
                        + node.branch.getUnit());
                case WAITING -> {
                }
            }
        }
        if (status != RequestTableUpgradeStatus.APPLIED) addCosts(tooltip, node, config);
        return tooltip;
    }
}
