package logisticspipes.crafting.requesttable.gui.upgrade;

import java.util.ArrayList;
import java.util.List;

import logisticspipes.crafting.requesttable.upgrade.RequestTableStorageUpgradeConfig;
import logisticspipes.crafting.requesttable.upgrade.RequestTableUpgradeBranch;

/** Fixed motherboard coordinates and native chip dimensions shared by drawing and hit detection. */
final class RequestTableBoardLayout {

    static final int WIDTH = 1536;
    static final int HEIGHT = 512;
    static final int CPU_X = 720;
    static final int CPU_Y = 256;
    static final int MONITOR_X = 640;
    static final int FLUID_X = 800;
    static final int ITEM_START_X = 632;
    static final int FLUID_START_X = 888;
    static final int TOP_BUS_Y = 160;
    static final int BOTTOM_BUS_Y = 352;
    static final int TIER_SPACING = 52;
    static final int CPU_CHIP_SIZE = 54;
    static final int MONITOR_CHIP_WIDTH = 50;
    static final int MONITOR_CHIP_HEIGHT = 38;
    static final int FLUID_CHIP_WIDTH = 38;
    static final int FLUID_CHIP_HEIGHT = 50;

    private RequestTableBoardLayout() {
    }

    static int getStorageChipSize(int tier) {
        return switch (getStorageChipFamily(tier)) {
            case 0 -> 31;
            case 1 -> 39;
            case 2 -> 44;
            default -> throw new IllegalStateException("Unknown storage chip family");
        };
    }

    static int getStorageChipFamily(int tier) {
        return switch (tier) {
            case 1, 2, 3 -> 0;
            case 4, 5, 6, 7 -> 1;
            case 8, 9, 10 -> 2;
            default -> throw new IllegalArgumentException("Invalid storage chip tier: " + tier);
        };
    }

    static List<Node> createNodes() {
        List<Node> nodes = new ArrayList<>();
        nodes.add(new Node(null, 0, CPU_X, CPU_Y, CPU_CHIP_SIZE, CPU_CHIP_SIZE));
        nodes.add(
            new Node(
                RequestTableUpgradeBranch.CRAFTING_MONITORING,
                0,
                MONITOR_X,
                CPU_Y,
                MONITOR_CHIP_WIDTH,
                MONITOR_CHIP_HEIGHT));
        nodes.add(
            new Node(
                RequestTableUpgradeBranch.FLUID_CONTROLLER,
                0,
                FLUID_X,
                CPU_Y,
                FLUID_CHIP_WIDTH,
                FLUID_CHIP_HEIGHT));
        for (RequestTableUpgradeBranch branch : RequestTableUpgradeBranch.values()) {
            if (branch.isSpecial()) continue;
            for (int tier = 1; tier <= RequestTableStorageUpgradeConfig.TIER_COUNT; tier++) {
                int x = (branch.isFluid() ? FLUID_START_X : ITEM_START_X)
                    + (branch.isFluid() ? 1 : -1) * (tier - 1) * TIER_SPACING;
                int size = getStorageChipSize(tier);
                nodes.add(new Node(branch, tier, x, branch.isSlotCount() ? TOP_BUS_Y : BOTTOM_BUS_Y, size, size));
            }
        }
        return nodes;
    }

    static final class Node {

        final RequestTableUpgradeBranch branch;
        final int tier;
        final int x;
        final int y;
        final int width;
        final int height;

        private Node(RequestTableUpgradeBranch branch, int tier, int x, int y, int width, int height) {
            this.branch = branch;
            this.tier = tier;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        int getLeft() {
            if (branch == null || branch.isSpecial()) return x - width / 2;
            // Item sockets are the horizontal reflection of the fluid sockets in the artwork.
            return x - (branch.isFluid() ? width / 2 : width - width / 2);
        }

        int getTop() {
            if (branch == null || branch.isSpecial()) return y - height / 2;
            int upperInset = height / 2 + (height == 31 ? 1 : 0);
            // Capacity sockets reflect the upper row; odd canvas sizes need the other rounding.
            return y - (branch.isSlotCount() ? upperInset : height - upperInset);
        }
    }
}
