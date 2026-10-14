package logisticspipes.crafting.requesttable.gui.upgrade;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.crafting.requesttable.gui.upgrade.RequestTableBoardLayout.Node;

/** One-shot pixel assembly effects in board coordinates, using the installed chip's native artwork. */
@SideOnly(Side.CLIENT)
final class RequestTableUpgradeAssembly {

    private static final int CHARGE_END = 200;
    private static final int PACKAGE_END = 700;
    private static final int LEVEL_END = 950;
    private static final int SYMBOL_END = 1200;
    private static final int DURATION = 1600;
    private static final int SIGNAL = 0xffb1b79b;
    private static final int GREEN = 0xff98aa6b;
    private static final int DIM = 0xff415b40;
    private final Map<Node, Long> started = new HashMap<>();

    private static int rows(int time, int from, int to, int height) {
        if (time <= from) return 0;
        if (time >= to) return height;
        // Reveal whole pairs of pixels without stretching any texture layer.
        return Math.min(height, ((time - from) * height / (to - from) / 2) * 2);
    }

    private static void drawGuides(Node node, int time, int tick) {
        int distance = time < CHARGE_END ? (CHARGE_END - time) / 50 : 0;
        int inset = 2 + distance;
        for (int corner = 0; corner < 4; corner++) {
            boolean right = corner == 1 || corner == 2;
            boolean bottom = corner >= 2;
            int x = right ? node.getLeft() + node.width + inset - 1 : node.getLeft() - inset;
            int y = bottom ? node.getTop() + node.height + inset - 1 : node.getTop() - inset;
            int color = time >= SYMBOL_END ? (tick % 4 < 2 ? SIGNAL : GREEN) : corner == tick % 4 ? SIGNAL : DIM;
            Gui.drawRect(right ? x - 3 : x, y, right ? x + 1 : x + 4, y + 1, color);
            Gui.drawRect(x, bottom ? y - 3 : y, x + 1, bottom ? y + 1 : y + 4, color);
        }
    }

    void start(Node node) {
        started.put(node, Minecraft.getSystemTime());
    }

    /** Returns false once the ordinary installed-chip renderer should take over. */
    boolean draw(Node node) {
        Long start = started.get(node);
        if (start == null) return false;
        long elapsed = Math.max(0, Minecraft.getSystemTime() - start);
        if (elapsed >= DURATION) {
            started.remove(node);
            return false;
        }
        // Fifty-millisecond steps keep the effect crisp at any frame rate or GUI scale.
        int tick = (int) (elapsed / 50);
        int time = tick * 50;
        int scanRows;
        if (node.branch.isSpecial()) {
            scanRows = rows(time, CHARGE_END, SYMBOL_END, node.height);
            RequestTableSpecialChips.drawAssembly(node, scanRows);
        } else {
            int packageRows = rows(time, CHARGE_END, PACKAGE_END, node.height);
            int levelRows = rows(time, PACKAGE_END, LEVEL_END, node.height);
            int symbolRows = rows(time, LEVEL_END, SYMBOL_END, node.height);
            RequestTableUpgradeChips.drawAssembly(
                    node.tier,
                    node.branch,
                    node.getLeft(),
                    node.getTop(),
                    packageRows,
                    levelRows,
                    symbolRows);
            scanRows = time < PACKAGE_END ? packageRows : time < LEVEL_END ? levelRows : symbolRows;
        }
        drawGuides(node, time, tick);
        if (time >= CHARGE_END && time < SYMBOL_END && scanRows > 0) {
            int y = node.getTop() + Math.min(node.height - 1, scanRows - 1);
            Gui.drawRect(node.getLeft(), y, node.getLeft() + node.width, y + 1, GREEN);
            int x = node.getLeft() + 1 + tick * 3 % Math.max(1, node.width - 4);
            Gui.drawRect(x, y, x + 2, y + 1, SIGNAL);
        }
        return true;
    }
}
