package logisticspipes.recipes;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

import logisticspipes.LogisticsPipes;
import logisticspipes.items.ItemModule;
import logisticspipes.modules.ModuleMigration;

/**
 * A lone module the rework merged into another, crafted alone, turns into the replacing module with its settings and
 * the speed upgrades it gets (see {@link ModuleMigration}).
 */
public class ModuleMigrationRecipe implements IRecipe {

    @Override
    public boolean matches(InventoryCrafting grid, World world) {
        return findLegacy(grid) != null;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting grid) {
        ItemStack legacy = findLegacy(grid);
        if (legacy == null) {
            return null;
        }
        ItemStack one = legacy.copy();
        one.stackSize = 1;
        return ModuleMigration.migrate(one);
    }

    // the only item in the grid, if it is an old module
    private static ItemStack findLegacy(InventoryCrafting grid) {
        ItemStack found = null;
        for (int i = 0; i < grid.getSizeInventory(); i++) {
            ItemStack stack = grid.getStackInSlot(i);
            if (stack == null) {
                continue;
            }
            if (found != null || !ModuleMigration.isLegacy(stack)) {
                return null;
            }
            found = stack;
        }
        return found;
    }

    @Override
    public int getRecipeSize() {
        return 1;
    }

    /**
     * A placeholder: the result depends on the module in the grid, see {@link #getCraftingResult}. Not null, the
     * logistics crafting table's fuzzy recipe lookup reads it with ItemIdentifier.get.
     */
    @Override
    public ItemStack getRecipeOutput() {
        return new ItemStack(LogisticsPipes.ModuleItem, 1, ItemModule.EXTRACTOR);
    }
}
