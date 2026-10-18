package logisticspipes.recipes;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;
import logisticspipes.items.ItemModule;

/**
 * GregTech recipes for converting legacy items into their pattern crafting counterparts, for the fluid modules, for the
 * request table Mk2, the inventory system connector and the frequency card. Kept apart from {@link RecipeManager} so
 * GregTech classes only load when GregTech is installed.
 */
final class GregTechConversionRecipes {

    // the circuit the pack uses for its Logistics Pipes assembler recipes
    static final int PACK_CIRCUIT = 18;

    private GregTechConversionRecipes() {}

    /** Assembler: {@code input} + programmed circuit 18 → {@code output}. */
    static void addAssemblerConversion(ItemStack input, ItemStack output) {
        GTValues.RA.stdBuilder().itemInputs(input, GTUtility.getIntegratedCircuit(PACK_CIRCUIT)).itemOutputs(output)
                .duration(5 * 20).eut(TierEU.RECIPE_ULV).addTo(RecipeMaps.assemblerRecipes);
    }

    /** Assembler: request table + fluid request pipe + programmed circuit 18 → request table Mk2, at MV. */
    static void addRequestTableMk2(ItemStack table, ItemStack fluidRequestPipe, ItemStack output) {
        GTValues.RA.stdBuilder().itemInputs(table, fluidRequestPipe, GTUtility.getIntegratedCircuit(PACK_CIRCUIT))
                .itemOutputs(output).duration(30 * 20).eut(TierEU.RECIPE_MV).addTo(RecipeMaps.assemblerRecipes);
    }

    /** Assembler: 2 basic logistics pipes + HV emitter + HV sensor + programmed circuit 18 → 2 connectors, at HV. */
    static void addInvSysConnector(ItemStack basicPipes, ItemStack output) {
        GTValues.RA.stdBuilder()
                .itemInputs(
                        basicPipes,
                        ItemList.Emitter_HV.get(1L),
                        ItemList.Sensor_HV.get(1L),
                        GTUtility.getIntegratedCircuit(PACK_CIRCUIT))
                .itemOutputs(output).duration(30 * 20).eut(TierEU.RECIPE_HV).addTo(RecipeMaps.assemblerRecipes);
    }

    /**
     * Assembler: 2 blank modules + 4 ender eye plates + 2 HV circuits of any kind + programmed circuit 18 → 2 frequency
     * cards, at HV.
     */
    static void addFrequencyCard(ItemStack blankModules, ItemStack output) {
        GTValues.RA.stdBuilder()
                .itemInputs(
                        blankModules,
                        GTOreDictUnificator.get(OrePrefixes.plate, Materials.EnderEye, 4L),
                        GTOreDictUnificator.get(OrePrefixes.circuit, Materials.HV, 2L),
                        GTUtility.getIntegratedCircuit(PACK_CIRCUIT))
                .itemOutputs(output).duration(30 * 20).eut(TierEU.RECIPE_HV).addTo(RecipeMaps.assemblerRecipes);
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
     * Assembler: the fluid container supplier module, made of an active supplier module and a fluid supplier module, at
     * MV like the other modules that request.
     */
    static void addFluidContainerSupplier(ItemStack activeSupplier, ItemStack fluidSupplier, ItemStack output) {
        addFluidVariant(output, TierEU.RECIPE_MV, activeSupplier, fluidSupplier);
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
