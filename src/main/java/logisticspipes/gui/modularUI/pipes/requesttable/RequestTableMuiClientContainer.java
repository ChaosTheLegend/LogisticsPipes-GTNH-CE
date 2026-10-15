package logisticspipes.gui.modularUI.pipes.requesttable;

import java.util.function.Supplier;

import net.minecraft.client.gui.inventory.GuiContainer;

import com.cleanroommc.modularui.integration.nei.INEIRecipeTransfer;
import com.cleanroommc.modularui.screen.ModularContainer;

import codechicken.nei.recipe.IRecipeHandler;
import logisticspipes.nei.RequestTableRecipeTransfer;

/**
 * Client container of the ModularUI request table. Implementing {@link INEIRecipeTransfer} lets NEI's recipe transfer
 * fill the ghost crafting grid.
 * <p>
 * The NEI logic lives in {@link RequestTableRecipeTransfer}, which is only loaded on the client.
 */
public class RequestTableMuiClientContainer extends RequestTableMuiContainer
        implements INEIRecipeTransfer<GuiContainer> {

    private final RequestTableMui gui;

    public RequestTableMuiClientContainer(RequestTableMui gui) {
        super(gui.getTable(), gui::getView);
        this.gui = gui;
    }

    /**
     * Container factory for {@code UISettings#customContainer}. Kept here so GUI code never references this class
     * directly: {@link INEIRecipeTransfer} uses client-only classes and must not be loaded on a dedicated server.
     */
    public static Supplier<ModularContainer> supplier(RequestTableMui gui) {
        return () -> new RequestTableMuiClientContainer(gui);
    }

    @Override
    public String[] getIdents() {
        return RequestTableRecipeTransfer.getIdents();
    }

    @Override
    public int transferRecipe(GuiContainer guiContainer, IRecipeHandler recipe, int recipeIndex, int multiplier) {
        return RequestTableRecipeTransfer.transfer(gui, recipe, recipeIndex);
    }
}
