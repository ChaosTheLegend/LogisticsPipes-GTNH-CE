package logisticspipes.modules;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import logisticspipes.LogisticsPipes;
import logisticspipes.items.ItemModule;
import logisticspipes.items.ItemUpgrade;
import logisticspipes.logisticspipes.ItemModuleInformationManager;
import logisticspipes.modules.ModuleTagItemSink.TagMode;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.upgrades.ModuleUpgradeManager;
import logisticspipes.utils.item.SimpleStackInventory;

/**
 * Modules the rework merged into another one plus upgrades (docs/module-rework.md): which module replaces them and how
 * many speed upgrades it gets so it works as fast as before. A chassis converts them when it loads them or one is put
 * in; a loose item converts in the crafting grid.
 */
public final class ModuleMigration {

    // old module id -> { new module id, speed upgrades }
    private static final Map<Integer, int[]> LEGACY = new HashMap<>();
    // old module id -> what to add to its saved settings so the replacing module reads them as before
    private static final Map<Integer, Consumer<NBTTagCompound>> SETTINGS = new HashMap<>();

    static {
        LEGACY.put(ItemModule.EXTRACTOR_MK2, new int[] { ItemModule.EXTRACTOR, 2 });
        LEGACY.put(ItemModule.EXTRACTOR_MK3, new int[] { ItemModule.EXTRACTOR, 4 });
        LEGACY.put(ItemModule.ADVANCED_EXTRACTOR, new int[] { ItemModule.EXTRACTOR, 0 });
        LEGACY.put(ItemModule.ADVANCED_EXTRACTOR_MK2, new int[] { ItemModule.EXTRACTOR, 2 });
        LEGACY.put(ItemModule.ADVANCED_EXTRACTOR_MK3, new int[] { ItemModule.EXTRACTOR, 4 });
        LEGACY.put(ItemModule.PROVIDER_MK2, new int[] { ItemModule.PROVIDER, 4 });
        LEGACY.put(ItemModule.CREATIVETABBASEDITEMSINK, new int[] { ItemModule.TAG_ITEMSINK, 0 });
        LEGACY.put(ItemModule.OREDICTITEMSINK, new int[] { ItemModule.TAG_ITEMSINK, 0 });
        LEGACY.put(ItemModule.GT_TYPEFILTER_ITEMSINK, new int[] { ItemModule.TAG_ITEMSINK, 0 });
        // same settings (the filter), the enchantment sink reads them as they are
        LEGACY.put(ItemModule.ENCHANTMENTSINK_MK2, new int[] { ItemModule.ENCHANTMENTSINK, 0 });
        // their settings don't say which tag they took (the mod and tab sinks even saved the same keys)
        SETTINGS.put(ItemModule.CREATIVETABBASEDITEMSINK, tagMode(TagMode.CREATIVE_TAB));
        SETTINGS.put(ItemModule.OREDICTITEMSINK, tagMode(TagMode.ORE_DICT));
        SETTINGS.put(ItemModule.GT_TYPEFILTER_ITEMSINK, tagMode(TagMode.GT_PREFIX));
        // the terminus is an item sink with the terminus priority, its filter is saved the same way
        LEGACY.put(ItemModule.TERMINUS, new int[] { ItemModule.ITEMSINK, 0 });
        SETTINGS.put(ItemModule.TERMINUS, settings -> {
            if (!settings.hasKey(ModuleItemSink.TERMINUS_KEY)) {
                settings.setBoolean(ModuleItemSink.TERMINUS_KEY, true);
            }
        });
    }

    private ModuleMigration() {}

    public static Set<Integer> legacyIds() {
        return Collections.unmodifiableSet(LEGACY.keySet());
    }

    public static boolean isLegacy(int moduleId) {
        return LEGACY.containsKey(moduleId);
    }

    public static boolean isLegacy(ItemStack stack) {
        return stack != null && stack.getItem() == LogisticsPipes.ModuleItem && isLegacy(stack.getItemDamage());
    }

    /**
     * @return the id of the module that replaces the old one
     */
    public static int replacementFor(int legacyId) {
        return LEGACY.get(legacyId)[0];
    }

    /**
     * @return how many speed upgrades the replacing module gets
     */
    public static int speedUpgradesFor(int legacyId) {
        return LEGACY.get(legacyId)[1];
    }

    /**
     * @return the module item that replaces the old one, as many as it was, with its settings and upgrades and the
     *         speed upgrades it gets on top; null if the item isn't an old module
     */
    public static ItemStack migrate(ItemStack old) {
        if (!isLegacy(old)) {
            return null;
        }
        int[] to = LEGACY.get(old.getItemDamage());
        ItemStack result = new ItemStack(LogisticsPipes.ModuleItem, old.stackSize, to[0]);
        LogisticsModule module = LogisticsPipes.ModuleItem.getModuleForItem(result, null, null, null);
        ItemStack source = old.copy();
        NBTTagCompound tag = source.hasTagCompound() ? source.getTagCompound() : new NBTTagCompound();
        NBTTagCompound settings = tag.getCompoundTag("moduleInformation");
        migrateSettings(old.getItemDamage(), settings);
        tag.setTag("moduleInformation", settings);
        source.setTagCompound(tag);
        ItemModuleInformationManager.readInformation(source, module);
        addSpeedUpgrades(module.getModuleUpgrades(), to[1]);
        ItemModuleInformationManager.saveInfotmation(result, module);
        return result;
    }

    /**
     * Adds to the saved settings of an old module what the replacing one needs to read them as before: the mode of a
     * tag sink, the terminus priority of an item sink. Settings that already say it are left alone.
     */
    public static void migrateSettings(int legacyId, NBTTagCompound settings) {
        Consumer<NBTTagCompound> migration = SETTINGS.get(legacyId);
        if (migration != null) {
            migration.accept(settings);
        }
    }

    private static Consumer<NBTTagCompound> tagMode(TagMode mode) {
        return settings -> {
            if (!settings.hasKey(ModuleTagItemSink.MODE_KEY)) {
                settings.setString(ModuleTagItemSink.MODE_KEY, mode.name());
            }
        };
    }

    /**
     * Adds speed upgrades to a module's upgrades, one per free slot (a slot takes one), up to what a module takes. What
     * doesn't fit is lost, the module keeps what it has. A side the old module saved turns into a sneaky upgrade later,
     * in a slot of one of these if none is free (LogisticsSneakyDirectionModule.applyLegacySide).
     */
    public static void addSpeedUpgrades(ModuleUpgradeManager upgrades, int count) {
        SimpleStackInventory inv = upgrades.getInv();
        int has = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack != null && stack.getItem() == LogisticsPipes.UpgradeItem
                    && stack.getItemDamage() == ItemUpgrade.SPEED) {
                has += stack.stackSize;
            }
        }
        count = Math.min(count, ModuleUpgradeManager.MAX_SPEED_UPGRADES - has);
        boolean added = false;
        for (int i = 0; i < inv.getSizeInventory() && count > 0; i++) {
            if (inv.getStackInSlot(i) == null) {
                inv.setInventorySlotContents(i, new ItemStack(LogisticsPipes.UpgradeItem, 1, ItemUpgrade.SPEED));
                count--;
                added = true;
            }
        }
        if (added) {
            upgrades.InventoryChanged(inv);
        }
    }
}
