package logisticspipes.crafting.requesttable;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.Loader;
import logisticspipes.utils.item.ItemIdentifier;

/** Shared ingredient identities and reusable-tool requirements for previews, sets and requests. */
final class RequestTableCraftingIngredients {

    private RequestTableCraftingIngredients() {}

    static ItemIdentifier key(ItemIdentifier item) {
        // Damageable items use damage as wear. Subtype metadata (ingots, circuits, GT tool kinds) stays distinct.
        return item.getIgnoringNBT().getUndamaged();
    }

    static Map<ItemIdentifier, Requirement> requirements(IInventory matrix) {
        Map<ItemIdentifier, Requirement> result = new LinkedHashMap<>();
        for (int slot = 0; slot < matrix.getSizeInventory(); slot++) {
            ItemStack stack = matrix.getStackInSlot(slot);
            if (stack == null || stack.stackSize <= 0) continue;
            ItemIdentifier item = ItemIdentifier.get(stack);
            Requirement requirement = result.computeIfAbsent(key(item), ignored -> new Requirement(item));
            if (isReusable(stack)) requirement.tools++;
            else requirement.consumed++;
        }
        return result;
    }

    static boolean isReusable(ItemStack stack) {
        if (stack == null) return false;
        // GT stores durability in NBT. NEI's ghost tool may have no material/durability NBT at all.
        if (Loader.isModLoaded("gregtech") && GregTechTools.isTool(stack)) return true;
        // A returned empty cell/bucket is a byproduct; the fluid-filled ingredient is still consumed per set.
        if (RequestTableContainer.getFluidStack(stack.copy()) != null) return false;
        ItemStack probe = stack.copy();
        probe.stackSize = 1;
        if (probe.isItemStackDamageable()) probe.setItemDamage(0);
        if (!probe.getItem().hasContainerItem(probe)) return false;
        ItemStack remainder = probe.getItem().getContainerItem(probe.copy());
        return remainder != null && remainder.stackSize > 0
                && key(ItemIdentifier.get(stack)).equals(key(ItemIdentifier.get(remainder)));
    }

    static void addStock(Map<ItemIdentifier, Long> stock, ItemIdentifier item, long amount) {
        if (amount > 0) stock.merge(key(item), amount, Long::sum);
    }

    static int maxSets(Map<ItemIdentifier, Requirement> requirements, Map<ItemIdentifier, Long> stock) {
        if (requirements.isEmpty()) return 0;
        int sets = Integer.MAX_VALUE;
        boolean consumes = false;
        for (var entry : requirements.entrySet()) {
            Requirement requirement = entry.getValue();
            long available = stock.getOrDefault(entry.getKey(), 0L) - requirement.tools;
            if (available < 0) return 0;
            if (requirement.consumed > 0) {
                consumes = true;
                sets = (int) Math
                        .min(sets, Math.min(available, Integer.MAX_VALUE - requirement.tools) / requirement.consumed);
            }
        }
        // An entirely reusable recipe has no finite stock-based "All" amount.
        return consumes ? sets : 0;
    }

    static final class Requirement {

        final ItemIdentifier example;
        int consumed;
        int tools;

        Requirement(ItemIdentifier example) {
            this.example = example;
        }

        int amount(int sets) {
            return (int) Math.min(Integer.MAX_VALUE, (long) consumed * Math.max(1, sets) + tools);
        }
    }

    private static final class GregTechTools {

        static boolean isTool(ItemStack stack) {
            return stack.getItem() instanceof gregtech.api.items.MetaGeneratedTool
                    && stack.getItem().hasContainerItem(stack.copy());
        }
    }
}
