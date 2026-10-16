package logisticspipes.gui.modularUI.requesttable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.integration.nei.INEIRecipeTransfer;
import com.cleanroommc.modularui.screen.ModularContainer;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiOverlayButton;
import codechicken.nei.recipe.IRecipeHandler;
import logisticspipes.crafting.requesttable.mk3.RequestTableMk3Pipe;

/**
 * Container of the request table GUI. Implementing {@link INEIRecipeTransfer} lets ModularUI route the recipe transfer
 * of NEI (the "+" button of a crafting recipe) into the crafting grid of the table, and lets the auto crafting of NEI
 * craft recipes from what the table and the player hold.
 * <p>
 * Only created on the client, it uses classes of NEI.
 */
public class RequestTableMuiContainer extends RequestTableContainerBase implements INEIRecipeTransfer<GuiContainer> {

    private static final String[] IDENTS = { "crafting" };
    // how long the answer of the server to an auto craft is waited for
    private static final long AUTOCRAFT_TIMEOUT_MILLIS = 2000;

    public RequestTableMuiContainer(RequestTableSyncHandler handler, RequestTableMk3Pipe table) {
        super(handler, table);
    }

    /**
     * Container factory for {@code UISettings#customContainer}, so the GUI code never refers to this class directly.
     */
    public static Supplier<ModularContainer> supplier(RequestTableSyncHandler handler, RequestTableMk3Pipe table) {
        return () -> new RequestTableMuiContainer(handler, table);
    }

    @Override
    public String[] getIdents() {
        return IDENTS;
    }

    /**
     * @return for each of the nine slots of the grid the items it can hold, or null if the recipe isn't one for a 3x3
     *         crafting grid
     */
    private static ItemStack[][] gridCandidates(IRecipeHandler recipe, int recipeIndex) {
        ItemStack[][] candidates = new ItemStack[9][];
        for (PositionedStack ingredient : recipe.getIngredientStacks(recipeIndex)) {
            int slot = gridSlot(ingredient);
            if (slot < 0) {
                return null;
            }
            candidates[slot] = ingredient.items;
        }
        return candidates;
    }

    /**
     * @return the slot of the 3x3 grid NEI draws the ingredient in, -1 if it lies outside of one
     */
    private static int gridSlot(PositionedStack ingredient) {
        // where NEI draws the 3x3 grid of a crafting recipe
        int column = (ingredient.relx - 25) / 18;
        int row = (ingredient.rely - 6) / 18;
        if (column < 0 || column > 2 || row < 0 || row > 2) {
            return -1;
        }
        return column + row * 3;
    }

    @Override
    public int transferRecipe(GuiContainer gui, IRecipeHandler recipe, int recipeIndex, int multiplier) {
        ItemStack[][] candidates = gridCandidates(recipe, recipeIndex);
        if (candidates == null) {
            return 0;
        }
        // an ingredient that can be several items is settled by the player
        handler.offerRecipe(candidates);
        return 1;
    }

    /**
     * The auto crafting of NEI asks this for every recipe of a chain, from a thread of its own: it only reads.
     */
    @Override
    public boolean canCraft(GuiContainer gui, IRecipeHandler recipe, int recipeIndex) {
        if (gridCandidates(recipe, recipeIndex) == null) {
            return false;
        }
        for (GuiOverlayButton.ItemOverlayState state : presenceOverlay(gui, recipe, recipeIndex)) {
            if (!state.isPresent()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Crafts a recipe for the auto crafting of NEI from what the table and the player hold. The server does the
     * crafting; this waits for its answer, which is fine on the thread of NEI and would freeze the game on the client
     * thread.
     *
     * @param multiplier how often to craft the recipe
     * @return true if it was crafted at least once
     */
    @Override
    public boolean craft(GuiContainer gui, IRecipeHandler recipe, int recipeIndex, int multiplier) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.func_152345_ab() || multiplier < 1) {
            return false;
        }
        ItemStack[] grid = pickIngredients(gui, recipe, recipeIndex);
        if (grid == null) {
            return false;
        }
        int request = handler.newAutoCraft();
        minecraft.func_152344_a(() -> handler.sendAutoCraft(request, grid, multiplier));
        return handler.awaitAutoCraft(request, AUTOCRAFT_TIMEOUT_MILLIS) > 0;
    }

    /**
     * Settles every ingredient on an item that is there: in the slots of the player, of the storage or of the crafting
     * grid. An item that one slot of the recipe takes isn't counted for another.
     *
     * @return the recipe as nine stacks, or null if it is no 3x3 recipe or an ingredient is missing
     */
    private static ItemStack[] pickIngredients(GuiContainer gui, IRecipeHandler recipe, int recipeIndex) {
        List<ItemStack> present = new ArrayList<>();
        for (Slot slot : gui.inventorySlots.inventorySlots) {
            if (slot == null) {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (stack != null && stack.stackSize > 0
                    && slot.isItemValid(stack)
                    && slot.canTakeStack(gui.mc.thePlayer)) {
                present.add(stack.copy());
            }
        }
        ItemStack[] grid = new ItemStack[9];
        for (PositionedStack ingredient : recipe.getIngredientStacks(recipeIndex)) {
            int slot = gridSlot(ingredient);
            if (slot < 0) {
                return null;
            }
            ItemStack found = null;
            for (ItemStack stack : present) {
                if (stack.stackSize > 0 && ingredient.contains(stack)) {
                    found = stack;
                    break;
                }
            }
            if (found == null) {
                return null;
            }
            found.stackSize--;
            ItemStack single = found.copy();
            single.stackSize = 1;
            grid[slot] = single;
        }
        return grid;
    }

    @Override
    public ArrayList<PositionedStack> positionStacks(GuiContainer gui, ArrayList<PositionedStack> stacks) {
        // the crafting grid is made of phantom widgets, not vanilla slots, so NEI's ghost overlay has nothing to draw
        // on
        return new ArrayList<>();
    }
}
