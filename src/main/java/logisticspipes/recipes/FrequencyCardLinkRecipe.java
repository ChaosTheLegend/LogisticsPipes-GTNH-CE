package logisticspipes.recipes;

import java.util.UUID;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import logisticspipes.LogisticsPipes;
import logisticspipes.items.LogisticsItemCard;

/**
 * Links and unlinks frequency cards in the crafting grid: two unlinked cards make a pair of cards linked to a new id,
 * and a lone linked card loses its id.
 */
public class FrequencyCardLinkRecipe implements IRecipe {

    private static final String UUID_KEY = "UUID";

    @Override
    public boolean matches(InventoryCrafting grid, World world) {
        return getCraftingResult(grid) != null;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting grid) {
        ItemStack first = null;
        int unlinked = 0;
        int linked = 0;
        for (int i = 0; i < grid.getSizeInventory(); i++) {
            ItemStack stack = grid.getStackInSlot(i);
            if (stack == null) {
                continue;
            }
            if (!isFrequencyCard(stack)) {
                return null;
            }
            if (isLinked(stack)) {
                linked++;
            } else {
                unlinked++;
            }
            if (first == null) {
                first = stack;
            }
        }
        if (unlinked == 2 && linked == 0) {
            ItemStack result = new ItemStack(LogisticsPipes.LogisticsItemCard, 2, LogisticsItemCard.FREQ_CARD);
            NBTTagCompound tag = first.hasTagCompound() ? (NBTTagCompound) first.getTagCompound().copy()
                    : new NBTTagCompound();
            tag.setString(UUID_KEY, UUID.randomUUID().toString());
            result.setTagCompound(tag);
            return result;
        }
        if (linked == 1 && unlinked == 0) {
            ItemStack result = new ItemStack(LogisticsPipes.LogisticsItemCard, 1, LogisticsItemCard.FREQ_CARD);
            NBTTagCompound tag = (NBTTagCompound) first.getTagCompound().copy();
            tag.removeTag(UUID_KEY);
            if (!tag.hasNoTags()) {
                result.setTagCompound(tag);
            }
            return result;
        }
        return null;
    }

    private static boolean isFrequencyCard(ItemStack stack) {
        return stack.getItem() == LogisticsPipes.LogisticsItemCard
                && stack.getItemDamage() == LogisticsItemCard.FREQ_CARD;
    }

    private static boolean isLinked(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().hasKey(UUID_KEY);
    }

    @Override
    public int getRecipeSize() {
        return 2;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return new ItemStack(LogisticsPipes.LogisticsItemCard, 2, LogisticsItemCard.FREQ_CARD);
    }
}
