package logisticspipes.recipes;

import net.minecraft.item.ItemStack;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.TierEU;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTUtility;

/**
 * GregTech recipes for converting legacy items into their pattern crafting counterparts. Kept apart from
 * {@link RecipeManager} so GregTech classes only load when GregTech is installed.
 */
final class GregTechConversionRecipes {

    static final int CONVERSION_CIRCUIT = 3;

    private GregTechConversionRecipes() {}

    /** Assembler: {@code input} + programmed circuit 3 → {@code output}. */
    static void addAssemblerConversion(ItemStack input, ItemStack output) {
        GTValues.RA.stdBuilder().itemInputs(input, GTUtility.getIntegratedCircuit(CONVERSION_CIRCUIT))
                .itemOutputs(output).duration(5 * 20).eut(TierEU.RECIPE_ULV).addTo(RecipeMaps.assemblerRecipes);
    }
}
