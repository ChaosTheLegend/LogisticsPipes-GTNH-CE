package logisticspipes.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import logisticspipes.LogisticsPipes;
import logisticspipes.blocks.LogisticsSolidBlock;
import logisticspipes.items.ItemModule;
import logisticspipes.items.ItemUpgrade;
import logisticspipes.modules.ModuleMigration;
import logisticspipes.pipes.PipeItemsFluidSupplier;
import logisticspipes.pipes.PipeItemsProviderLogisticsMk2;
import logisticspipes.pipes.basic.CoreUnroutedPipe;
import logisticspipes.pipes.basic.LogisticsBlockGenericPipe;
import logisticspipes.utils.string.StringUtils;

/**
 * What the rework deprecates: its recipes are gone, shows a removal warning, and is deleted one major pack version
 * after the rework ships. Placed pipes and blocks keep working; deprecated upgrades do nothing and are deleted from
 * pipes when they load, except that old sided and combined sneaky upgrades first become the Sneaky Upgrade with their
 * faces and connection upgrades become disconnected sides of the pipe. See "Compatibility with old bases" in
 * docs/rework-design-decisions.md.
 */
public final class DeprecatedContent {

    private static final String WARNING_KEY = "lp.deprecated.tooltip";
    // the longest tooltip line, in characters: longer reasons go onto more lines
    private static final int LINE_LENGTH = 45;
    private static final String POWER_SUPPLY = "lp.deprecated.power_supply";

    // pipe class -> tooltip line that says what to use instead
    private static final Map<Class<? extends CoreUnroutedPipe>, String> PIPES = new HashMap<>();
    // upgrade item damage -> tooltip line
    private static final Map<Integer, String> UPGRADES = new HashMap<>();
    // solid block item damage -> tooltip line
    private static final Map<Integer, String> SOLID_BLOCKS = new HashMap<>();
    // module item damage -> tooltip line
    private static final Map<Integer, String> MODULES = new HashMap<>();

    static {
        PIPES.put(PipeItemsFluidSupplier.class, "lp.deprecated.fluid_supplier_mk1");
        PIPES.put(PipeItemsProviderLogisticsMk2.class, "lp.deprecated.provider_pipe_mk2");

        UPGRADES.put(ItemUpgrade.OPAQUE_UPGRADE, "lp.deprecated.opaque");
        UPGRADES.put(ItemUpgrade.UPGRADE_MODULE_UPGRADE, "lp.deprecated.upgrade_module_upgrade");
        UPGRADES.put(ItemUpgrade.CC_REMOTE_CONTROL, "lp.deprecated.cc_remote_control");
        for (int connection : new int[] { ItemUpgrade.CONNECTION_UP, ItemUpgrade.CONNECTION_DOWN,
                ItemUpgrade.CONNECTION_NORTH, ItemUpgrade.CONNECTION_SOUTH, ItemUpgrade.CONNECTION_EAST,
                ItemUpgrade.CONNECTION_WEST }) {
            UPGRADES.put(connection, "lp.deprecated.connection");
        }
        for (int sneaky : new int[] { ItemUpgrade.SNEAKY_DOWN, ItemUpgrade.SNEAKY_NORTH, ItemUpgrade.SNEAKY_SOUTH,
                ItemUpgrade.SNEAKY_EAST, ItemUpgrade.SNEAKY_WEST, ItemUpgrade.SNEAKY_COMBINATION }) {
            UPGRADES.put(sneaky, "lp.deprecated.sneaky");
        }
        UPGRADES.put(ItemUpgrade.POWER_TRANSPORTATION, POWER_SUPPLY);
        UPGRADES.put(ItemUpgrade.POWER_RF_SUPPLIER, POWER_SUPPLY);
        UPGRADES.put(ItemUpgrade.POWER_IC2_LV_SUPPLIER, POWER_SUPPLY);
        UPGRADES.put(ItemUpgrade.POWER_IC2_MV_SUPPLIER, POWER_SUPPLY);
        UPGRADES.put(ItemUpgrade.POWER_IC2_HV_SUPPLIER, POWER_SUPPLY);
        UPGRADES.put(ItemUpgrade.POWER_IC2_EV_SUPPLIER, POWER_SUPPLY);

        SOLID_BLOCKS.put(LogisticsSolidBlock.LOGISTICS_RF_POWERPROVIDER, POWER_SUPPLY);
        SOLID_BLOCKS.put(LogisticsSolidBlock.LOGISTICS_IC2_POWERPROVIDER, POWER_SUPPLY);

        for (int id : ModuleMigration.legacyIds()) {
            if (ModuleMigration.replacementFor(id) == ItemModule.EXTRACTOR) {
                MODULES.put(id, "lp.deprecated.extractor");
            } else if (ModuleMigration.replacementFor(id) == ItemModule.PROVIDER) {
                MODULES.put(id, "lp.deprecated.provider");
            } else if (ModuleMigration.replacementFor(id) == ItemModule.TAG_ITEMSINK) {
                MODULES.put(id, "lp.deprecated.tag_sink");
            } else if (ModuleMigration.replacementFor(id) == ItemModule.ENCHANTMENTSINK) {
                MODULES.put(id, "lp.deprecated.enchantment_sink");
            } else if (ModuleMigration.replacementFor(id) == ItemModule.ITEMSINK) {
                MODULES.put(id, "lp.deprecated.terminus");
            }
        }
    }

    private DeprecatedContent() {}

    public static boolean isDeprecatedPipe(Class<? extends CoreUnroutedPipe> pipeClass) {
        return pipeClass != null && PIPES.containsKey(pipeClass);
    }

    /**
     * A deprecated upgrade does nothing: it can't be installed, and installed ones are deleted when the pipe loads (old
     * sneaky upgrades are converted to the Sneaky Upgrade and connection upgrades to disconnected sides before that).
     */
    public static boolean isDeprecatedUpgrade(ItemStack stack) {
        return stack != null && stack.getItem() == LogisticsPipes.UpgradeItem
                && UPGRADES.containsKey(stack.getItemDamage());
    }

    public static boolean isDeprecated(ItemStack stack) {
        return reasonKey(stack) != null;
    }

    private static String reasonKey(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        if (stack.getItem() == LogisticsPipes.UpgradeItem) {
            return UPGRADES.get(stack.getItemDamage());
        }
        if (stack.getItem() == LogisticsPipes.ModuleItem) {
            return MODULES.get(stack.getItemDamage());
        }
        if (LogisticsPipes.LogisticsSolidBlock != null
                && stack.getItem() == Item.getItemFromBlock(LogisticsPipes.LogisticsSolidBlock)) {
            return SOLID_BLOCKS.get(stack.getItemDamage());
        }
        Class<? extends CoreUnroutedPipe> pipeClass = LogisticsBlockGenericPipe.getPipeClassByItem(stack.getItem());
        return pipeClass != null ? PIPES.get(pipeClass) : null;
    }

    /**
     * Adds the removal warning to the tooltip of a deprecated item, nothing for others.
     */
    public static void addWarning(ItemStack stack, List<String> tooltip) {
        String reason = reasonKey(stack);
        if (reason == null) {
            return;
        }
        tooltip.add(StringUtils.translate(WARNING_KEY));
        tooltip.addAll(wrap(StringUtils.translate(reason), LINE_LENGTH));
    }

    /**
     * Breaks a text into lines of at most that many characters at the spaces; the formatting codes it starts with (its
     * colour) begin every line.
     */
    static List<String> wrap(String text, int length) {
        int codes = 0;
        while (codes + 1 < text.length() && text.charAt(codes) == '§') {
            codes += 2;
        }
        String format = text.substring(0, codes);
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.substring(codes).trim().split(" +")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > length) {
                lines.add(format + line);
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(format + line);
        }
        return lines;
    }
}
