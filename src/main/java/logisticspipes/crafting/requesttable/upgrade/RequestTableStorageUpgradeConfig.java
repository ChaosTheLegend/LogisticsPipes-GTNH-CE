package logisticspipes.crafting.requesttable.upgrade;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.oredict.OreDictionary;

import lombok.Getter;

/** Editable cumulative tier bonuses and ore-dictionary circuit costs, also sent to the upgrade GUI. */
public final class RequestTableStorageUpgradeConfig {

    public static final String CATEGORY = "requesttable.itemupgrades";
    public static final String FLUID_CATEGORY = "requesttable.fluidupgrades";
    public static final String[] TIERS = { "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV" };
    public static final int TIER_COUNT = TIERS.length;
    private static final int[] DEFAULT_SLOTS = { 9, 18, 27, 36, 45, 54, 63, 72, 81, 90 };
    private static final int[] DEFAULT_SIZES = { 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768 };
    private static final String[] DEFAULT_CIRCUITS = { "circuitBasic", "circuitGood", "circuitAdvanced", "circuitData",
            "circuitElite", "circuitMaster", "circuitUltimate", "circuitSuperconductor", "circuitInfinite",
            "circuitBio" };
    @Getter
    private static RequestTableStorageUpgradeConfig configured = new RequestTableStorageUpgradeConfig(false);
    private static RequestTableStorageUpgradeConfig configuredFluids = new RequestTableStorageUpgradeConfig(true);
    private final int[] slots = DEFAULT_SLOTS.clone();
    private final int[] sizes = DEFAULT_SIZES.clone();
    private final String[] circuits = DEFAULT_CIRCUITS.clone();
    private final int[] costs = new int[TIER_COUNT];
    private int baseSlots = 27;
    private int baseSlotSize = 64;

    private RequestTableStorageUpgradeConfig(boolean fluid) {
        java.util.Arrays.fill(costs, 1);
        if (fluid) {
            baseSlotSize *= 1000;
            for (int i = 0; i < sizes.length; i++) sizes[i] *= 1000;
        }
    }

    public static RequestTableStorageUpgradeConfig getConfigured(boolean fluid) {
        return fluid ? configuredFluids : configured;
    }

    public static void load(Configuration config) {
        configured = load(config, false);
        configuredFluids = load(config, true);
    }

    private static RequestTableStorageUpgradeConfig load(Configuration config, boolean fluid) {
        RequestTableStorageUpgradeConfig values = new RequestTableStorageUpgradeConfig(fluid);
        String rootCategory = fluid ? FLUID_CATEGORY : CATEGORY;
        values.baseSlots = Math.max(1, config.get(rootCategory, "baseSlots", 27, "Base storage slot count.").getInt());
        values.baseSlotSize = Math.max(
                1,
                config.get(
                        rootCategory,
                        "baseSlotSize",
                        values.baseSlotSize,
                        fluid ? "Base millibuckets per tank." : "Base items per storage slot.").getInt());
        for (int i = 0; i < TIER_COUNT; i++) {
            String category = rootCategory + "." + TIERS[i].toLowerCase(java.util.Locale.ROOT);
            values.slots[i] = Math.max(
                    0,
                    config.get(
                            category,
                            "additionalSlots",
                            DEFAULT_SLOTS[i],
                            "Added once when this slot-count tier is consumed.").getInt());
            values.sizes[i] = Math.max(
                    0,
                    config.get(
                            category,
                            "additionalSlotSize",
                            values.sizes[i],
                            "Added once when this slot-size tier is consumed.").getInt());
            values.circuits[i] = config
                    .get(
                            category,
                            "circuitOreName",
                            DEFAULT_CIRCUITS[i],
                            "Accepted circuit ore-dictionary name; any matching circuit variant can be used.")
                    .getString();
            values.costs[i] = Math.max(
                    1,
                    Math.min(
                            256,
                            config.get(
                                    category,
                                    "circuitCount",
                                    1,
                                    "Circuits consumed by either branch at this tier (1-256).").getInt()));
        }
        return values;
    }

    public static boolean isValidTier(int tier) {
        return tier >= 1 && tier <= TIER_COUNT;
    }

    public static String getTierName(int tier) {
        return tier == 0 ? "Base" : TIERS[Math.max(1, Math.min(TIER_COUNT, tier)) - 1];
    }

    public static RequestTableStorageUpgradeConfig readData(DataInput input) throws IOException {
        RequestTableStorageUpgradeConfig values = new RequestTableStorageUpgradeConfig(false);
        values.baseSlots = Math.max(1, input.readInt());
        values.baseSlotSize = Math.max(1, input.readInt());
        for (int i = 0; i < TIER_COUNT; i++) {
            values.slots[i] = Math.max(0, input.readInt());
            values.sizes[i] = Math.max(0, input.readInt());
            values.circuits[i] = input.readUTF();
            values.costs[i] = Math.max(1, Math.min(256, input.readInt()));
        }
        return values;
    }

    public int getBonus(RequestTableUpgradeBranch branch, int tier) {
        return (branch.isSlotCount() ? slots : sizes)[tier - 1];
    }

    public int getTotal(RequestTableUpgradeBranch branch, int tier) {
        long total = branch.isSlotCount() ? baseSlots : baseSlotSize;
        for (int i = 1; i <= Math.min(TIER_COUNT, Math.max(0, tier)); i++) {
            total += getBonus(branch, i);
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public int getCost(int tier) {
        return costs[tier - 1];
    }

    public String getCircuitOreName(int tier) {
        return circuits[tier - 1];
    }

    public ItemStack getExampleCircuit(int tier) {
        java.util.List<ItemStack> options = OreDictionary.getOres(getCircuitOreName(tier));
        for (ItemStack option : options) {
            if (option != null && option.getItem() != null && option.getItemDamage() != OreDictionary.WILDCARD_VALUE) {
                ItemStack result = option.copy();
                result.stackSize = 1;
                return result;
            }
        }
        return null;
    }

    public void writeData(DataOutput output) throws IOException {
        output.writeInt(baseSlots);
        output.writeInt(baseSlotSize);
        for (int i = 0; i < TIER_COUNT; i++) {
            output.writeInt(slots[i]);
            output.writeInt(sizes[i]);
            output.writeUTF(circuits[i]);
            output.writeInt(costs[i]);
        }
    }
}
