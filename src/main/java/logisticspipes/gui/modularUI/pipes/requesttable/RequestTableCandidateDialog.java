package logisticspipes.gui.modularUI.pipes.requesttable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.screen.ModularPanel;

import logisticspipes.crafting.requesttable.gui.RequestTableRender;
import logisticspipes.utils.gui.GuiGraphics;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.string.StringUtils;

/**
 * Lets the player pick one of several ore dictionary candidates per ingredient before an NEI recipe fills the ghost
 * grid. "Most likely" asks the server which candidates the network stocks or can craft.
 */
final class RequestTableCandidateDialog {

    private static final int WIDTH = 150;
    private static final int HEIGHT = 200;
    private static final int PICKER_COLUMNS = 8;

    private final RequestTableMui gui;
    private final Candidates[] grid = new Candidates[9];
    private final List<Candidates> groups = new ArrayList<>();

    private RequestTableCandidateDialog(RequestTableMui gui, ItemStack[][] stacks) {
        this.gui = gui;
        for (int i = 0; i < 9; i++) {
            if (stacks[i] == null) continue;
            Set<ItemIdentifierStack> set = new TreeSet<>();
            List<ItemIdentifierStack> order = new ArrayList<>();
            for (ItemStack stack : stacks[i]) {
                ItemIdentifierStack identifier = ItemIdentifierStack.getFromStack(stack);
                set.add(identifier);
                order.add(identifier);
            }
            Candidates candidates = null;
            for (Candidates group : groups) {
                if (group.set.equals(set)) {
                    candidates = group;
                    break;
                }
            }
            if (candidates == null) {
                candidates = new Candidates(set, order);
                if (order.size() > 1) groups.add(candidates);
            }
            grid[i] = candidates;
        }
    }

    static void open(RequestTableMui gui, ModularPanel parent, ItemStack[][] stacks) {
        RequestTableCandidateDialog dialog = new RequestTableCandidateDialog(gui, stacks);
        IPanelHandler.simple(parent, (panel, player) -> dialog.build(parent), true).openPanel();
    }

    private ModularPanel build(ModularPanel parent) {
        RequestTablePopupPanel panel = new RequestTablePopupPanel("request_table_candidates", WIDTH, HEIGHT);
        Runnable done = () -> {
            ItemStack[] content = new ItemStack[9];
            for (int i = 0; i < 9; i++) {
                if (grid[i] != null) content[i] = grid[i].current().makeNormalStack();
            }
            gui.getActions().setCraftingRecipe(content);
            panel.closeIfOpen();
        };
        panel.onEnter(done);
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        panel.child(new RequestTablePaintWidget((w, h) -> {
            try (var ignored = RequestTableRender.guiState()) {
                font.drawString(StringUtils.translate("misc.selectOreDict"), 10, 6, 0x404040);
                for (int i = 0; i < 9; i++) {
                    GuiGraphics.drawSlotBackground(Minecraft.getMinecraft(), 44 + (i % 3) * 18, 19 + (i / 3) * 18);
                }
                for (int i = 0; i < groups.size(); i++) {
                    GuiGraphics.drawSlotBackground(Minecraft.getMinecraft(), 19 + (i % 3) * 40, 89 + (i / 3) * 40);
                }
            }
        }).pos(0, 0).size(WIDTH, HEIGHT));
        for (int i = 0; i < 9; i++) {
            if (grid[i] == null) continue;
            Candidates candidates = grid[i];
            panel.child(
                    itemIcon(() -> candidates.current().makeNormalStack()).pos(45 + (i % 3) * 18, 20 + (i / 3) * 18));
        }
        for (int i = 0; i < groups.size(); i++) {
            Candidates group = groups.get(i);
            int x = i % 3;
            int y = i / 3;
            panel.child(
                    itemIcon(() -> group.current().makeNormalStack()).pos(20 + x * 40, 90 + y * 40)
                            .onLeftClick(() -> openPicker(parent, group)));
            panel.child(
                    RequestTableMui.textButton(() -> "/\\", () -> true).pos(38 + x * 40, 88 + y * 40).size(15, 10)
                            .onLeftClick(() -> group.step(1)));
            panel.child(
                    RequestTableMui.textButton(() -> "\\/", () -> true).pos(38 + x * 40, 98 + y * 40).size(15, 10)
                            .onLeftClick(() -> group.step(-1)));
        }
        panel.child(
                RequestTableMui.textButton(() -> "Most likely", () -> true).pos(10, 180).size(60, 10)
                        .onLeftClick(() -> {
                            List<List<ItemIdentifierStack>> request = new ArrayList<>();
                            for (Candidates group : groups) request.add(group.order);
                            gui.getActions().findMostLikelyCandidates(request, response -> {
                                if (response.size() != groups.size()) return;
                                for (int i = 0; i < groups.size(); i++) {
                                    if (response.get(i) >= 0) groups.get(i).position = response.get(i);
                                }
                            });
                        }));
        panel.child(RequestTableMui.textButton(() -> "Done", () -> true).pos(100, 180).size(40, 10).onLeftClick(done));
        return panel;
    }

    private void openPicker(ModularPanel parent, Candidates group) {
        int columns = Math.min(PICKER_COLUMNS, group.order.size());
        int rows = (group.order.size() + columns - 1) / columns;
        int width = 16 + columns * 18;
        int height = 30 + rows * 18;
        IPanelHandler.simple(parent, (panel, player) -> {
            RequestTablePopupPanel picker = new RequestTablePopupPanel("request_table_candidate_pick", width, height);
            picker.child(new RequestTablePaintWidget((w, h) -> {
                try (var ignored = RequestTableRender.guiState()) {
                    Minecraft.getMinecraft().fontRenderer
                            .drawString(StringUtils.translate("misc.selectOreDict"), 8, 6, 0x404040);
                    for (int i = 0; i < group.order.size(); i++) {
                        GuiGraphics.drawSlotBackground(
                                Minecraft.getMinecraft(),
                                7 + (i % columns) * 18,
                                19 + (i / columns) * 18);
                    }
                }
            }).pos(0, 0).size(width, height));
            for (int i = 0; i < group.order.size(); i++) {
                int index = i;
                picker.child(
                        itemIcon(() -> group.order.get(index).makeNormalStack())
                                .pos(8 + (i % columns) * 18, 20 + (i / columns) * 18).onLeftClick(() -> {
                                    group.position = index;
                                    picker.closeIfOpen();
                                }));
            }
            return picker;
        }, true).openPanel();
    }

    private static RequestTableButtonWidget itemIcon(java.util.function.Supplier<ItemStack> stack) {
        return new RequestTableButtonWidget().size(16, 16).painter((width, height, hover, active) -> {
            RequestTableRender.item(stack.get(), 0, 0, 100.0F, false);
            if (hover) RequestTableMuiDraw.slotHighlight(0, 0);
        }).itemTooltip(stack);
    }

    private static final class Candidates {

        private final Set<ItemIdentifierStack> set;
        private final List<ItemIdentifierStack> order;
        private int position;

        private Candidates(Set<ItemIdentifierStack> set, List<ItemIdentifierStack> order) {
            this.set = set;
            this.order = order;
        }

        private ItemIdentifierStack current() {
            return order.get(position);
        }

        private void step(int direction) {
            position = (position + direction + order.size()) % order.size();
        }
    }
}
