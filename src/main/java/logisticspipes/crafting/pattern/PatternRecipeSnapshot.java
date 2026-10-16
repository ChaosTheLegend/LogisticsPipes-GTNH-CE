package logisticspipes.crafting.pattern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.crafting.patternStack.PatternStackHelper;
import logisticspipes.utils.FluidIdentifier;

/**
 * Immutable, read-only view of one configured pattern item.
 * <p>
 * Pattern data is stored in item NBT. Reading individual slots through {@link AbstractPattern} repeatedly therefore
 * reparses the same NBT lists. A snapshot reads each slot once and is reused until the pattern inventory reports a
 * change. Callers must treat the returned pattern stacks as read-only.
 */
public final class PatternRecipeSnapshot {

    private final ItemStack patternStack;
    private final AbstractPattern pattern;
    private final List<IPatternStack> inputs;
    private final List<IPatternStack> outputs;
    private final List<IPatternStack> aggregatedInputs;
    private final IPatternStack[] inputSlots;
    private final IPatternStack[] outputSlots;
    private final int[] itemSatelliteIds;
    private final String[] itemSatelliteUuids;
    private final int[] fluidSatelliteIds;
    private final String[] fluidSatelliteUuids;
    private final int[] byproductSatelliteIds;
    private final String[] byproductSatelliteUuids;
    private final int[] fluidByproductSatelliteIds;
    private final String[] fluidByproductSatelliteUuids;
    private final int mainOutputSlot;
    private final boolean containsFluid;
    private final Map<FluidIdentifier, Integer> fluidIngredientAmounts;

    PatternRecipeSnapshot(ItemStack patternStack) {
        this.patternStack = patternStack;
        pattern = ItemPattern.fromStack(patternStack);
        inputSlots = new IPatternStack[pattern.getIngredientSlotCount()];
        itemSatelliteIds = new int[inputSlots.length];
        itemSatelliteUuids = new String[inputSlots.length];
        fluidSatelliteIds = new int[inputSlots.length];
        fluidSatelliteUuids = new String[inputSlots.length];

        List<IPatternStack> inputList = new ArrayList<>();
        for (int slot = 0; slot < inputSlots.length; slot++) {
            IPatternStack stack = pattern.getPatternStackInSlot(slot);
            inputSlots[slot] = stack;
            if (stack != null && stack.getAmount() > 0) {
                inputList.add(stack);
            }
            itemSatelliteIds[slot] = pattern.getSatelliteIdForInputSlot(slot);
            itemSatelliteUuids[slot] = pattern.getSatelliteUuidForInputSlot(slot);
            fluidSatelliteIds[slot] = pattern.getFluidSatelliteIdForInputSlot(slot);
            fluidSatelliteUuids[slot] = pattern.getFluidSatelliteUuidForInputSlot(slot);
        }
        inputs = Collections.unmodifiableList(inputList);

        outputSlots = new IPatternStack[pattern.getResultSlotCount()];
        byproductSatelliteIds = new int[outputSlots.length];
        byproductSatelliteUuids = new String[outputSlots.length];
        fluidByproductSatelliteIds = new int[outputSlots.length];
        fluidByproductSatelliteUuids = new String[outputSlots.length];
        List<IPatternStack> outputList = new ArrayList<>();
        for (int slot = 0; slot < outputSlots.length; slot++) {
            IPatternStack stack = pattern.getPatternStackInSlot(pattern.getResultSlotStart() + slot);
            outputSlots[slot] = stack;
            if (stack != null && stack.getAmount() > 0) {
                outputList.add(stack);
            }
            byproductSatelliteIds[slot] = pattern.getByproductSatelliteIdForOutputSlot(slot);
            byproductSatelliteUuids[slot] = pattern.getByproductSatelliteUuidForOutputSlot(slot);
            fluidByproductSatelliteIds[slot] = pattern.getFluidByproductSatelliteIdForOutputSlot(slot);
            fluidByproductSatelliteUuids[slot] = pattern.getFluidByproductSatelliteUuidForOutputSlot(slot);
        }
        outputs = Collections.unmodifiableList(outputList);
        mainOutputSlot = pattern.getMainOutputSlot();
        aggregatedInputs = Collections.unmodifiableList(PatternStackHelper.aggregate(inputs));
        containsFluid = PatternStackHelper.containsFluid(inputs) || PatternStackHelper.containsFluid(outputs);
        Map<FluidIdentifier, Integer> fluidAmounts = new HashMap<>();
        for (IPatternStack input : inputs) {
            FluidIdentifier fluid = PatternStackHelper.asFluid(input);
            if (fluid != null) {
                fluidAmounts.merge(fluid, input.getAmount(), Integer::sum);
            }
        }
        fluidIngredientAmounts = Collections.unmodifiableMap(fluidAmounts);
    }

    public ItemStack getPatternStack() {
        return patternStack;
    }

    public AbstractPattern getPattern() {
        return pattern;
    }

    public boolean isConfigured() {
        return !inputs.isEmpty() && !outputs.isEmpty();
    }

    public List<IPatternStack> getInputs() {
        return inputs;
    }

    public List<IPatternStack> getOutputs() {
        return outputs;
    }

    public List<IPatternStack> getAggregatedInputs() {
        return aggregatedInputs;
    }

    public IPatternStack getInput(int slot) {
        return slot < 0 || slot >= inputSlots.length ? null : inputSlots[slot];
    }

    public int getIngredientSlotCount() {
        return inputSlots.length;
    }

    public IPatternStack getOutput(int slot) {
        return slot < 0 || slot >= outputSlots.length ? null : outputSlots[slot];
    }

    public int getMainOutputSlot() {
        return mainOutputSlot;
    }

    public List<IPatternStack> getCraftableOutputs() {
        IPatternStack main = getOutput(mainOutputSlot);
        return main == null ? Collections.emptyList() : Collections.singletonList(main);
    }

    public int getResultSlotCount() {
        return outputSlots.length;
    }

    public int getItemSatelliteId(int slot) {
        return slot < 0 || slot >= itemSatelliteIds.length ? 0 : itemSatelliteIds[slot];
    }

    public String getItemSatelliteUuid(int slot) {
        return slot < 0 || slot >= itemSatelliteUuids.length ? "" : itemSatelliteUuids[slot];
    }

    public int getFluidSatelliteId(int slot) {
        return slot < 0 || slot >= fluidSatelliteIds.length ? 0 : fluidSatelliteIds[slot];
    }

    public String getFluidSatelliteUuid(int slot) {
        return slot < 0 || slot >= fluidSatelliteUuids.length ? "" : fluidSatelliteUuids[slot];
    }

    public int getByproductSatelliteId(int slot) {
        return slot < 0 || slot >= byproductSatelliteIds.length ? 0 : byproductSatelliteIds[slot];
    }

    public String getByproductSatelliteUuid(int slot) {
        return slot < 0 || slot >= byproductSatelliteUuids.length ? "" : byproductSatelliteUuids[slot];
    }

    public int getFluidByproductSatelliteId(int slot) {
        return slot < 0 || slot >= fluidByproductSatelliteIds.length ? 0 : fluidByproductSatelliteIds[slot];
    }

    public String getFluidByproductSatelliteUuid(int slot) {
        return slot < 0 || slot >= fluidByproductSatelliteUuids.length ? "" : fluidByproductSatelliteUuids[slot];
    }

    public boolean containsFluid() {
        return containsFluid;
    }

    public int getFluidIngredientAmount(FluidIdentifier fluid) {
        return fluid == null ? 0 : fluidIngredientAmounts.getOrDefault(fluid, 0);
    }

    public boolean isOreDictSubstitutionEnabled() {
        return pattern.isOreDictSubstitutionEnabled();
    }

    public boolean isIgnoreNbtEnabled() {
        return pattern.isIgnoreNbtEnabled();
    }
}
