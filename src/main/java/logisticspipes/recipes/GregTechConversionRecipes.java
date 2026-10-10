package logisticspipes.recipes;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.TierEU;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTUtility;

/**
 * GregTech recipes for converting legacy items into their pattern crafting counterparts and for the fluid chassis and
 * its modules. Kept apart from {@link RecipeManager} so GregTech classes only load when GregTech is installed.
 */
final class GregTechConversionRecipes {

    static final int CONVERSION_CIRCUIT = 3;
    // the circuit the pack uses for its Logistics Pipes assembler recipes
    static final int PACK_CIRCUIT = 18;

    private GregTechConversionRecipes() {}

    /** Assembler: {@code input} + programmed circuit 3 → {@code output}. */
    static void addAssemblerConversion(ItemStack input, ItemStack output) {
        GTValues.RA.stdBuilder().itemInputs(input, GTUtility.getIntegratedCircuit(CONVERSION_CIRCUIT))
                .itemOutputs(output).duration(5 * 20).eut(TierEU.RECIPE_ULV).addTo(RecipeMaps.assemblerRecipes);
    }

    /**
     * Assembler: the fluid variant of {@code base}, made like the fluid pipes of the pack: the item variant plus a
     * large steel fluid cell, programmed circuit 18.
     *
     * @param extra    a further ingredient, or null
     * @param withPump true for the parts that move the fluid themselves
     * @param mv       true for an MV recipe, false for LV
     */
    static void addFluidVariant(ItemStack base, ItemStack extra, boolean withPump, boolean mv, ItemStack output) {
        List<ItemStack> inputs = new ArrayList<>();
        inputs.add(base);
        if (extra != null) {
            inputs.add(extra);
        }
        if (withPump) {
            inputs.add(ItemList.Electric_Pump_LV.get(1L));
        }
        inputs.add(ItemList.Large_Fluid_Cell_Steel.get(1L));
        inputs.add(GTUtility.getIntegratedCircuit(PACK_CIRCUIT));
        GTValues.RA.stdBuilder().itemInputs(inputs.toArray(new ItemStack[0])).itemOutputs(output).duration(30 * 20)
                .eut(mv ? TierEU.RECIPE_MV : TierEU.RECIPE_LV).addTo(RecipeMaps.assemblerRecipes);
    }
}
