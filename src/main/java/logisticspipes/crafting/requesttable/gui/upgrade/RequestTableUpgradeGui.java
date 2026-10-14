package logisticspipes.crafting.requesttable.gui.upgrade;

import static logisticspipes.crafting.requesttable.gui.RequestTableRender.inside;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Mouse;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.crafting.requesttable.gui.RequestTableButton;
import logisticspipes.crafting.requesttable.gui.RequestTableGuiStyle;
import logisticspipes.crafting.requesttable.gui.RequestTableRender;
import logisticspipes.crafting.requesttable.upgrade.RequestTableStorageUpgradeConfig;
import logisticspipes.crafting.requesttable.upgrade.RequestTableUpgradeBranch;
import logisticspipes.crafting.requesttable.upgrade.RequestTableUpgradeContainer;
import logisticspipes.crafting.requesttable.upgrade.RequestTableUpgradeMaterials.Requirement;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.crafting.requesttable.RequestTableItemUpgradePacket;
import logisticspipes.network.packets.crafting.requesttable.RequestTableOpenUpgradesPacket;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.gui.GuiGraphics;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;

/** Fixed material controls around a freely draggable motherboard-style upgrade tree. */
@SideOnly(Side.CLIENT)
public class RequestTableUpgradeGui extends LogisticsBaseGuiScreen {

    private static final int WIDTH = 286;
    private static final int BACK = 0;
    private static final int APPLY = 1;
    private static final int CENTER = 2;
    private static final int ZOOM_OUT = 3;
    private static final int ZOOM_IN = 4;
    private static final int SHOW_ALL = 5;
    private static final int APPLY_CREATIVE = 6;
    private final RequestTableUpgradeContainer upgrades;
    private final RequestTableUpgradeBoard board = new RequestTableUpgradeBoard();
    private GuiButton applyButton;
    private GuiButton creativeApplyButton;
    private GuiButton centerButton;

    public RequestTableUpgradeGui(EntityPlayer player, RequestTablePipe table) {
        super(new RequestTableUpgradeContainer(player, table), WIDTH, 300, 0, 0);
        upgrades = (RequestTableUpgradeContainer) inventorySlots;
    }

    private static String compact(int value) {
        if (value < 1000) return Integer.toString(value);
        double scaled = value >= 1_000_000 ? value / 1_000_000.0 : value / 1000.0;
        return String.format(java.util.Locale.ROOT, "%.1f", scaled).replace(".0", "")
                + (value >= 1_000_000 ? "M" : "k");
    }

    @Override
    public void initGui() {
        ySize = Math.max(220, Math.min(420, height - 40));
        super.initGui();
        board.cancelDrag();
        buttonList.clear();
        buttonList.add(new RequestTableButton(BACK, guiLeft + xSize - 50, guiTop + 5, 42, 14, "Back"));
        centerButton = new RequestTableButton(CENTER, guiLeft + xSize - 96, guiTop + 5, 40, 14, "Center");
        buttonList.add(centerButton);
        buttonList.add(new RequestTableButton(ZOOM_OUT, guiLeft + 116, guiTop + 5, 14, 14, "-"));
        buttonList.add(new RequestTableButton(ZOOM_IN, guiLeft + 134, guiTop + 5, 14, 14, "+"));
        buttonList.add(new RequestTableButton(SHOW_ALL, guiLeft + 152, guiTop + 5, 32, 14, "All"));
        int applyLeft = guiLeft + RequestTableUpgradeContainer.INVENTORY_LEFT
                + RequestTableUpgradeContainer.MATERIAL_SLOTS * 18
                + 4;
        applyButton = new RequestTableButton(APPLY, applyLeft, detailsTop() + 34, 46, 18, "Apply");
        buttonList.add(applyButton);
        creativeApplyButton = new RequestTableButton(APPLY_CREATIVE, applyLeft, detailsTop() + 54, 46, 16, "Free");
        buttonList.add(creativeApplyButton);
        updateBoard();
        upgrades.layout(ySize);
    }

    private int detailsTop() {
        return guiTop + ySize - 150;
    }

    private RequestTableUpgradeStatus selectedStatus() {
        return RequestTableUpgradeStatus.of(upgrades, upgrades.getSelectedBranch(), upgrades.getSelectedTier());
    }

    private boolean isSelectedUpgradeApplied() {
        return selectedStatus() == RequestTableUpgradeStatus.APPLIED;
    }

    private void updateBoard() {
        int boardTop = guiTop + 24;
        board.setArea(guiLeft + 8, boardTop, xSize - 16, detailsTop() - boardTop - 8);
        if (upgrades.isReady()) board.initializeView(upgrades.getSelectedBranch(), upgrades.getSelectedTier());
        board.updateProgress(upgrades);
        applyButton.displayString = isSelectedUpgradeApplied() ? "Applied"
                : upgrades.getSelectedBranch().isSpecial() ? "Install" : "Apply";
        applyButton.enabled = upgrades.isReady() && upgrades.canUpgrade();
        creativeApplyButton.visible = mc.thePlayer.capabilities.isCreativeMode;
        creativeApplyButton.enabled = upgrades.isReady() && upgrades.canUpgradeCreative();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        try (var ignored = RequestTableRender.guiState()) {
            updateBoard();
            upgrades.layout(ySize);
            GuiGraphics.drawGuiBackGround(mc, guiLeft, guiTop, right, bottom, zLevel, true);
            mc.fontRenderer.drawString("Table upgrades", guiLeft + 12, guiTop + 8, RequestTableGuiStyle.TEXT);
            board.draw(mc, upgrades, mouseX, mouseY);
            drawRequirements();
            int playerTop = guiTop + ySize - 84;
            mc.fontRenderer.drawString(
                    "Inventory",
                    guiLeft + RequestTableUpgradeContainer.INVENTORY_LEFT,
                    playerTop - 11,
                    RequestTableGuiStyle.TEXT);
            GuiGraphics.drawPlayerInventoryBackground(
                    mc,
                    guiLeft + RequestTableUpgradeContainer.INVENTORY_LEFT,
                    playerTop);
        }
    }

    private void drawRequirements() {
        RequestTableUpgradeBranch branch = upgrades.getSelectedBranch();
        int tier = upgrades.getSelectedTier();
        RequestTableStorageUpgradeConfig config = upgrades.getConfig();
        boolean applied = isSelectedUpgradeApplied();
        String heading = branch.getLabel()
                + (branch.isSpecial() ? "" : " " + RequestTableStorageUpgradeConfig.getTierName(tier));
        String change = switch (selectedStatus()) {
            case APPLIED -> branch.isSpecial() ? "Enabled"
                    : "Total: " + compact(config.getTotal(branch, upgrades.getTable().getUpgradeTier(branch)))
                            + " "
                            + branch.getUnit();
            case WAITING -> "Waiting for the server...";
            case PREVIOUS_REQUIRED -> "Install " + RequestTableStorageUpgradeConfig.getTierName(tier - 1)
                    + " first ("
                    + EnumChatFormatting.DARK_GREEN
                    + "+"
                    + compact(config.getBonus(branch, tier))
                    + EnumChatFormatting.RESET
                    + ")";
            case FLUID_CONTROLLER_REQUIRED -> "Install fluid controller first";
            case AVAILABLE -> {
                if (branch.isSpecial()) yield "Locked";
                int previous = config.getTotal(branch, upgrades.getTable().getUpgradeTier(branch));
                yield compact(previous) + " + "
                        + EnumChatFormatting.DARK_GREEN
                        + compact(config.getBonus(branch, tier))
                        + EnumChatFormatting.RESET
                        + " -> "
                        + compact(config.getTotal(branch, tier))
                        + " "
                        + branch.getUnit();
            }
        };
        int changeWidth = mc.fontRenderer.getStringWidth(change);
        // Long fluid formulas still leave a gap to the left-aligned upgrade name.
        int headingWidth = xSize - 24 - changeWidth - 6;
        if (mc.fontRenderer.getStringWidth(heading) > headingWidth) {
            heading = mc.fontRenderer
                    .trimStringToWidth(heading, Math.max(0, headingWidth - mc.fontRenderer.getStringWidth("...")))
                    + "...";
        }
        mc.fontRenderer.drawString(heading, guiLeft + 12, detailsTop(), RequestTableGuiStyle.TEXT);
        mc.fontRenderer
                .drawString(change, guiLeft + xSize - 12 - changeWidth, detailsTop(), RequestTableGuiStyle.MUTED);
        int materialsLeft = guiLeft + RequestTableUpgradeContainer.INVENTORY_LEFT;
        // Keep unused inputs accessible, including when an applied upgrade is selected.
        RequestTableRender
                .slotGrid(mc, materialsLeft - 1, detailsTop() + 34, RequestTableUpgradeContainer.MATERIAL_SLOTS, 1);
        if (applied) {
            String contribution = switch (branch) {
                case FLUID_CONTROLLER -> "Enables fluid storage, requests and cells.";
                case CRAFTING_MONITORING -> "Enables crafting monitoring.";
                default -> "Provides " + EnumChatFormatting.DARK_GREEN
                        + "+"
                        + compact(config.getBonus(branch, tier))
                        + EnumChatFormatting.RESET
                        + " "
                        + branch.getUnit();
            };
            mc.fontRenderer.drawString(contribution, guiLeft + 12, detailsTop() + 16, RequestTableGuiStyle.TEXT);
            return;
        }
        List<Requirement> requirements = upgrades.getRequirements();
        for (int i = 0; i < requirements.size(); i++) {
            Requirement requirement = requirements.get(i);
            int x = requirementLeft(i);
            ItemStack example = requirement.getExample();
            if (example != null) {
                RequestTableRender.item(example, x, detailsTop() + 12, 100.0F, false);
            } else {
                mc.fontRenderer.drawString("?", x + 5, detailsTop() + 16, RequestTableGuiStyle.MUTED);
            }
            int inserted = upgrades.getMaterialCount(requirement);
            mc.fontRenderer.drawString(
                    inserted + "/" + requirement.getCount(),
                    x + 19,
                    detailsTop() + 16,
                    inserted >= requirement.getCount() ? 0x37622e : RequestTableGuiStyle.MUTED);
        }
    }

    private int requirementLeft(int index) {
        return guiLeft + 12 + index * 66;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case BACK -> MainProxy
                    .sendPacketToServer(PacketHandler.getPacket(RequestTableOpenUpgradesPacket.class).setInteger(1));
            case APPLY -> {
                if (upgrades.isReady() && upgrades.canUpgrade()) {
                    sendSelection(upgrades.getSelectedBranch(), upgrades.getSelectedTier(), true);
                }
            }
            case APPLY_CREATIVE -> {
                if (upgrades.isReady() && upgrades.canUpgradeCreative()) {
                    sendSelection(upgrades.getSelectedBranch(), upgrades.getSelectedTier(), true, true);
                }
            }
            case CENTER -> board.center();
            case ZOOM_OUT, ZOOM_IN -> board.changeZoom(button.id == ZOOM_IN);
            case SHOW_ALL -> board.showAll();
        }
    }

    private void sendSelection(RequestTableUpgradeBranch branch, int tier, boolean start) {
        sendSelection(branch, tier, start, false);
    }

    private void sendSelection(RequestTableUpgradeBranch branch, int tier, boolean start, boolean creative) {
        upgrades.awaitState();
        MainProxy.sendPacketToServer(
                PacketHandler.getPacket(RequestTableItemUpgradePacket.class)
                        .setUpgrade(upgrades.windowId, branch, tier, start, creative));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Queued releases must still reach the board after the physical mouse button goes up.
        if (hasSubGui()) board.cancelDrag();
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (hasSubGui()) return;
        List<String> nodeTooltip = board.tooltip(mouseX, mouseY, upgrades);
        if (nodeTooltip != null) {
            GuiGraphics.drawToolTip(mouseX, mouseY, nodeTooltip, EnumChatFormatting.WHITE);
            return;
        }
        for (GuiButton button : buttonList) {
            if (!RequestTableRender.hovered(button, mouseX, mouseY)) continue;
            List<String> tooltip = switch (button.id) {
                case CENTER -> Arrays.asList(
                        "Center on the base chip.",
                        "Drag the board to move in any direction.",
                        "Mouse wheel: move vertically.",
                        "Shift + mouse wheel: move horizontally.");
                case ZOOM_OUT -> Arrays.asList("Zoom out");
                case ZOOM_IN -> Arrays.asList("Zoom in");
                case SHOW_ALL -> Arrays.asList("Show the whole motherboard");
                case APPLY -> applyTooltip();
                case APPLY_CREATIVE -> Arrays.asList(
                        "Apply without materials (creative mode)",
                        selectedStatus() == RequestTableUpgradeStatus.AVAILABLE ? "Prerequisite upgrades still apply."
                                : selectedStatus().hint(upgrades.getSelectedTier()));
                default -> null;
            };
            if (tooltip != null) {
                GuiGraphics.drawToolTip(mouseX, mouseY, tooltip, EnumChatFormatting.WHITE);
                return;
            }
        }
        if (isSelectedUpgradeApplied()) return;
        List<Requirement> requirements = upgrades.getRequirements();
        for (int i = 0; i < requirements.size(); i++) {
            if (!inside(mouseX, mouseY, requirementLeft(i), detailsTop() + 12, 64, 16)) continue;
            Requirement requirement = requirements.get(i);
            var tooltip = new ArrayList<String>();
            tooltip.add(requirement.getCount() + "x " + requirement.getLabel());
            if (requirement.getOreName() != null) tooltip.add("Accepted: " + requirement.getOreName());
            tooltip.add("Inserted: " + upgrades.getMaterialCount(requirement) + "/" + requirement.getCount());
            tooltip.add("Insert materials into the slots below.");
            tooltip.add("Unused materials return when you leave this screen.");
            GuiGraphics.drawToolTip(mouseX, mouseY, tooltip, EnumChatFormatting.WHITE);
            return;
        }
    }

    private List<String> applyTooltip() {
        RequestTableUpgradeStatus status = selectedStatus();
        if (status == RequestTableUpgradeStatus.APPLIED) {
            return Arrays.asList("Upgrade applied.", "Permanent, including after breaking and placing the table.");
        }
        var tooltip = new ArrayList<>(
                Arrays.asList(
                        status.hint(upgrades.getSelectedTier()),
                        "Permanent, including after breaking and placing the table."));
        if (!upgrades.getSelectedBranch().isSpecial() && (status == RequestTableUpgradeStatus.PREVIOUS_REQUIRED
                || status == RequestTableUpgradeStatus.FLUID_CONTROLLER_REQUIRED)) {
            tooltip.add(
                    "Provides " + EnumChatFormatting.DARK_GREEN
                            + "+"
                            + upgrades.getConfig().getBonus(upgrades.getSelectedBranch(), upgrades.getSelectedTier())
                            + EnumChatFormatting.RESET
                            + " "
                            + upgrades.getSelectedBranch().getUnit());
        }
        for (Requirement requirement : upgrades.getRequirements()) {
            tooltip.add(requirement.getCount() + "x " + requirement.getLabel());
        }
        return tooltip;
    }

    @Override
    public void handleMouseInputSub() {
        int wheel = Mouse.getEventDWheel();
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (wheel != 0 && !hasSubGui() && board.contains(mouseX, mouseY)) {
            board.scroll(wheel, isShiftKeyDown());
        }
        super.handleMouseInputSub();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (!hasSubGui() && board.startDrag(mouseX, mouseY, button)) return;
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long elapsed) {
        if (board.isDragging()) {
            if (button == board.getDragButton()) board.drag(mouseX, mouseY);
            return;
        }
        super.mouseClickMove(mouseX, mouseY, button, elapsed);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int button) {
        if (board.isDragging()) {
            RequestTableBoardLayout.Node node = board.release(mouseX, mouseY, button);
            if (node != null) {
                centerButton.func_146113_a(mc.getSoundHandler());
                sendSelection(node.branch, node.tier, false);
            }
            return;
        }
        super.mouseMovedOrUp(mouseX, mouseY, button);
    }

    @Override
    public void onGuiClosed() {
        board.cancelDrag();
        super.onGuiClosed();
    }

}
