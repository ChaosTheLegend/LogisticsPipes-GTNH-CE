package logisticspipes.recipes;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.TierEU;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTUtility;
import logisticspipes.items.ItemModule;

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
     * Assembler: a fluid chassis, made like the fluid pipes of the pack: the item variant and a basic fluid pipe plus a
     * large steel fluid cell, programmed circuit 18.
     */
    static void addFluidChassis(ItemStack chassis, ItemStack fluidPipe, ItemStack output) {
        addFluidVariant(output, TierEU.RECIPE_MV, chassis, fluidPipe);
    }

    /**
     * Assembler: a fluid module, made of the item module it stands for. The modules that move the fluid themselves take
     * a pump, and the ones that answer or make requests are MV.
     */
    static void addFluidModule(ItemStack module, ItemStack output) {
        int fluidModule = output.getItemDamage();
        boolean movesFluid = fluidModule != ItemModule.FLUID_SINK;
        boolean requests = fluidModule == ItemModule.FLUID_PROVIDER || fluidModule == ItemModule.FLUID_SUPPLIER;
        addFluidVariant(
                output,
                requests ? TierEU.RECIPE_MV : TierEU.RECIPE_LV,
                module,
                movesFluid ? ItemList.Electric_Pump_LV.get(1L) : null);
    }

    /**
     * @param parts what the recipe takes besides the cell and the circuit, a part that is null is left out
     */
    private static void addFluidVariant(ItemStack output, long eut, ItemStack... parts) {
        List<ItemStack> inputs = new ArrayList<>();
        for (ItemStack part : parts) {
            if (part != null) {
                inputs.add(part);
            }
        }
        inputs.add(ItemList.Large_Fluid_Cell_Steel.get(1L));
        inputs.add(GTUtility.getIntegratedCircuit(PACK_CIRCUIT));
        GTValues.RA.stdBuilder().itemInputs(inputs.toArray(new ItemStack[0])).itemOutputs(output).duration(30 * 20)
                .eut(eut).addTo(RecipeMaps.assemblerRecipes);
    }
}
