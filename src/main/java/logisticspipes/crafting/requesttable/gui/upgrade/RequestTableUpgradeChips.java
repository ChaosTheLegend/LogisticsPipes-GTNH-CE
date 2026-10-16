package logisticspipes.crafting.requesttable.gui.upgrade;

import net.minecraft.util.ResourceLocation;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.crafting.requesttable.gui.RequestTableRender;
import logisticspipes.crafting.requesttable.upgrade.RequestTableUpgradeBranch;

/** Native GUI sprites: installed storage chips are composed of base, tier and branch layers. */
@SideOnly(Side.CLIENT)
final class RequestTableUpgradeChips {

    private static final String[] FAMILIES = {"small", "middle", "large"};
    private static final String[] TYPES = {"item_slots", "item_capacity", "fluid_tanks", "fluid_capacity"};
    private static final int[] LEVEL_COUNTS = {3, 4, 3};
    private static final ResourceLocation[] BASES = new ResourceLocation[3];
    private static final ResourceLocation[][] LEVELS = new ResourceLocation[3][];
    private static final ResourceLocation[][] BRANCHES = new ResourceLocation[3][4];

    static {
        for (int family = 0; family < FAMILIES.length; family++) {
            BASES[family] = texture(FAMILIES[family], "base");
            LEVELS[family] = new ResourceLocation[LEVEL_COUNTS[family]];
            for (int level = 0; level < LEVEL_COUNTS[family]; level++)
                LEVELS[family][level] = texture(FAMILIES[family], "level_" + (level + 1));
            for (int type = 0; type < TYPES.length; type++)
                BRANCHES[family][type] = texture(FAMILIES[family], "type_" + TYPES[type]);
        }
    }

    private RequestTableUpgradeChips() {
    }

    private static ResourceLocation texture(String family, String layer) {
        return RequestTableRender.texture("chips/" + family + "/" + layer);
    }

    static void draw(int tier, RequestTableUpgradeBranch branch, int x, int y) {
        int size = RequestTableBoardLayout.getStorageChipSize(tier);
        drawAssembly(tier, branch, x, y, size, size, size);
    }

    static void drawAssembly(int tier, RequestTableUpgradeBranch branch, int x, int y, int packageRows, int levelRows,
                             int symbolRows) {
        int family = RequestTableBoardLayout.getStorageChipFamily(tier);
        int level = tier - (family == 0 ? 1 : family == 1 ? 4 : 8);
        int type = switch (branch) {
            case SLOT_COUNT -> 0;
            case SLOT_SIZE -> 1;
            case FLUID_SLOT_COUNT -> 2;
            case FLUID_SLOT_SIZE -> 3;
            default -> throw new IllegalArgumentException("Controller sockets do not use storage chip textures");
        };
        int size = RequestTableBoardLayout.getStorageChipSize(tier);
        drawLayer(BASES[family], x, y, size, packageRows);
        // Preserve the native canvases of the hand-edited small layers instead of stretching them.
        int levelSize = family == 0 && level == 0 ? 30 : size;
        int typeSize = family == 0 ? 30 : size;
        int typeInset = family == 0 ? 1 : 0;
        drawLayer(LEVELS[family][level], x, y, levelSize, levelRows);
        drawLayer(BRANCHES[family][type], x + typeInset, y + typeInset, typeSize, symbolRows - typeInset);
    }

    private static void drawLayer(ResourceLocation texture, int x, int y, int size, int visibleRows) {
        RequestTableRender.textureRows(texture, x, y, size, size, visibleRows, 0, 1);
    }
}
