package logisticspipes.gui.modularUI.requesttable;

import java.util.ArrayList;
import java.util.function.Supplier;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.integration.nei.INEIRecipeTransfer;
import com.cleanroommc.modularui.screen.ModularContainer;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import logisticspipes.crafting.requesttable.RequestTablePipe;

/**
 * Container of the request table GUI. Implementing {@link INEIRecipeTransfer} lets ModularUI route the recipe transfer
 * of NEI (the "+" button of a crafting recipe) into the crafting grid of the table.
 * <p>
 * Only created on the client, it uses classes of NEI.
 */
public class RequestTableMuiContainer extends RequestTableContainerBase implements INEIRecipeTransfer<GuiContainer> {

    private static final String[] IDENTS = { "crafting" };

    public RequestTableMuiContainer(RequestTableSyncHandler handler, RequestTablePipe table) {
        super(handler, table);
    }

    /**
     * Container factory for {@code UISettings#customContainer}, so the GUI code never refers to this class directly.
     */
    public static Supplier<ModularContainer> supplier(RequestTableSyncHandler handler, RequestTablePipe table) {
        return () -> new RequestTableMuiContainer(handler, table);
    }

    @Override
    public String[] getIdents() {
        return IDENTS;
    }

    @Override
    public int transferRecipe(GuiContainer gui, IRecipeHandler recipe, int recipeIndex, int multiplier) {
        ItemStack[][] candidates = new ItemStack[9][];
        for (PositionedStack ingredient : recipe.getIngredientStacks(recipeIndex)) {
            // where NEI draws the 3x3 grid of a crafting recipe
            int column = (ingredient.relx - 25) / 18;
            int row = (ingredient.rely - 6) / 18;
            if (column < 0 || column > 2 || row < 0 || row > 2) {
                return 0;
            }
            candidates[column + row * 3] = ingredient.items;
        }
        // an ingredient that can be several items is settled by the player
        handler.offerRecipe(candidates);
        return 1;
    }

    @Override
    public ArrayList<PositionedStack> positionStacks(GuiContainer gui, ArrayList<PositionedStack> stacks) {
        // the crafting grid is made of phantom widgets, not vanilla slots, so NEI's ghost overlay has nothing to draw
        // on
        return new ArrayList<>();
    }
}
