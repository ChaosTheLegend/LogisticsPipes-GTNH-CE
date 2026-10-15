package logisticspipes.nei;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import logisticspipes.gui.modularUI.pipes.requesttable.RequestTableMui;

/**
 * NEI recipe transfer into the ghost crafting grid of the ModularUI request table, with the same ore dictionary
 * candidate handling as {@link LogisticsCraftingOverlayHandler}.
 */
public final class RequestTableRecipeTransfer {

    private static final String[] IDENTS = { "crafting" };

    private RequestTableRecipeTransfer() {}

    public static String[] getIdents() {
        return IDENTS;
    }

    public static int transfer(RequestTableMui gui, IRecipeHandler recipe, int recipeIndex) {
        ItemStack[] stack = new ItemStack[9];
        ItemStack[][] stacks = new ItemStack[9][];
        boolean hasCandidates = false;
        for (PositionedStack ps : recipe.getIngredientStacks(recipeIndex)) {
            int x = (ps.relx - 25) / 18;
            int y = (ps.rely - 6) / 18;
            int slot = x + y * 3;
            if (x < 0 || x > 2 || y < 0 || y > 2 || slot < 0 || slot > 8) {
                Minecraft.getMinecraft().thePlayer.sendChatMessage("Internal Error. This button is broken.");
                return 0;
            }
            stack[slot] = ps.items[0];
            List<ItemStack> list = new ArrayList<>(Arrays.asList(ps.items));
            Iterator<ItemStack> iterator = list.iterator();
            while (iterator.hasNext()) {
                ItemStack wildCardCheckStack = iterator.next();
                if (wildCardCheckStack.getItemDamage() == OreDictionary.WILDCARD_VALUE) {
                    iterator.remove();
                    Objects.requireNonNull(wildCardCheckStack.getItem()).getSubItems(
                            wildCardCheckStack.getItem(),
                            wildCardCheckStack.getItem().getCreativeTab(),
                            list);
                    iterator = list.iterator();
                }
            }
            stacks[slot] = list.toArray(new ItemStack[0]);
            if (stacks[slot].length > 1) {
                hasCandidates = true;
            } else if (stacks[slot].length == 1) {
                stack[slot] = stacks[slot][0];
            }
        }
        if (hasCandidates) {
            gui.openCandidates(stacks);
        } else {
            gui.getActions().setCraftingRecipe(stack);
        }
        return 1;
    }
}
