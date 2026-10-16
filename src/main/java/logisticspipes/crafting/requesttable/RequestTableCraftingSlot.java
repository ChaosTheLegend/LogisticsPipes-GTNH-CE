package logisticspipes.crafting.requesttable;

import java.util.function.BiConsumer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent;

import cpw.mods.fml.common.FMLCommonHandler;
import logisticspipes.utils.item.ItemIdentifier;

/** Keeps vanilla crafting callbacks and container handling, but returns reusable tools to their source slots. */
final class RequestTableCraftingSlot extends SlotCrafting {

    private final IInventory ingredients;
    private final BiConsumer<Integer, ItemStack> returnTool;

    RequestTableCraftingSlot(EntityPlayer player, IInventory ingredients, IInventory result,
            BiConsumer<Integer, ItemStack> returnTool) {
        super(player, ingredients, result, 0, 0, 0);
        this.ingredients = ingredients;
        this.returnTool = returnTool;
    }

    @Override
    public void onPickupFromSlot(EntityPlayer player, ItemStack result) {
        FMLCommonHandler.instance().firePlayerCraftingEvent(player, result, ingredients);
        onCrafting(result);
        ItemStack[] byproducts = new ItemStack[ingredients.getSizeInventory()];
        boolean[] leaveGrid = new boolean[byproducts.length];
        for (int slot = 0; slot < ingredients.getSizeInventory(); slot++) {
            ItemStack ingredient = ingredients.getStackInSlot(slot);
            if (ingredient == null) continue;
            boolean reusable = RequestTableCraftingIngredients.isReusable(ingredient);
            ItemIdentifier original = RequestTableCraftingIngredients.key(ItemIdentifier.get(ingredient));
            ingredients.decrStackSize(slot, 1);
            if (!ingredient.getItem().hasContainerItem(ingredient)) continue;
            ItemStack remainder = ingredient.getItem().getContainerItem(ingredient);
            if (remainder == null || remainder.stackSize <= 0) continue;
            if (remainder.isItemStackDamageable() && remainder.getItemDamage() > remainder.getMaxDamage()) {
                MinecraftForge.EVENT_BUS.post(new PlayerDestroyItemEvent(player, remainder));
                continue;
            }
            if (reusable && original.equals(RequestTableCraftingIngredients.key(ItemIdentifier.get(remainder)))) {
                returnTool.accept(slot, remainder);
            } else {
                byproducts[slot] = remainder;
                leaveGrid[slot] = ingredient.getItem().doesContainerItemLeaveCraftingGrid(ingredient);
            }
        }
        // Restore every tool before a bucket or other byproduct can occupy a vacated source slot.
        for (int slot = 0; slot < byproducts.length; slot++) {
            ItemStack remainder = byproducts[slot];
            if (remainder == null) continue;
            if (!leaveGrid[slot] || !player.inventory.addItemStackToInventory(remainder)) {
                if (ingredients.getStackInSlot(slot) == null) {
                    ingredients.setInventorySlotContents(slot, remainder);
                } else {
                    player.dropPlayerItemWithRandomChoice(remainder, false);
                }
            }
        }
    }
}
