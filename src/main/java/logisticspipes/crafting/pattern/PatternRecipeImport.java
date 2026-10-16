package logisticspipes.crafting.pattern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.crafting.patternStack.PatternItemStack;

/**
 * A recipe transferred from a recipe viewer into a pattern.
 * <p>
 * Inputs carry the pattern input slot they should be placed in, so crafting-table recipes keep their grid shape.
 * Processing imports use sequential slots.
 */
public final class PatternRecipeImport {

    /**
     * Item amounts are stored through {@link ItemStack#writeToNBT}, which keeps the count in a byte.
     */
    public static final int MAX_ITEM_AMOUNT = 127;

    private static final String PROCESSING_TAG = "processing";
    private static final String INPUTS_TAG = "inputs";
    private static final String OUTPUTS_TAG = "outputs";
    private static final String SLOT_TAG = "importSlot";

    private final boolean processing;
    private final List<IPatternStack> inputs;
    private final List<Integer> inputSlots;
    private final List<IPatternStack> outputs;

    public PatternRecipeImport(boolean processing, List<IPatternStack> inputs, List<Integer> inputSlots,
            List<IPatternStack> outputs) {
        if (inputs.size() != inputSlots.size()) {
            throw new IllegalArgumentException("Every input needs a target slot");
        }
        this.processing = processing;
        this.inputs = inputs;
        this.inputSlots = inputSlots;
        this.outputs = outputs;
    }

    public boolean isProcessing() {
        return processing;
    }

    public List<IPatternStack> getInputs() {
        return Collections.unmodifiableList(inputs);
    }

    public List<Integer> getInputSlots() {
        return Collections.unmodifiableList(inputSlots);
    }

    public List<IPatternStack> getOutputs() {
        return Collections.unmodifiableList(outputs);
    }

    public boolean isEmpty() {
        return inputs.isEmpty() || outputs.isEmpty();
    }

    /**
     * Replaces the contents of a pattern item with this recipe, switching the pattern type when needed.
     * <p>
     * Inputs that do not fit the pattern type are dropped, item amounts are clamped to what the pattern NBT can store.
     */
    public void applyTo(ItemStack patternStack) {
        ItemPattern.setProcessingPattern(patternStack, processing);
        AbstractPattern pattern = ItemPattern.fromStack(patternStack);
        List<IPatternStack> validInputs = new ArrayList<>();
        List<Integer> validSlots = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            int slot = inputSlots.get(i);
            IPatternStack input = clampAmount(inputs.get(i));
            if (input != null && slot >= 0 && slot < pattern.getIngredientSlotCount()) {
                validInputs.add(input);
                validSlots.add(slot);
            }
        }
        List<IPatternStack> validOutputs = new ArrayList<>();
        for (IPatternStack output : outputs) {
            IPatternStack clamped = clampAmount(output);
            if (clamped != null) {
                validOutputs.add(clamped);
            }
        }
        pattern.setInputsAndOutputs(validInputs, validSlots, validOutputs);
    }

    private static IPatternStack clampAmount(IPatternStack stack) {
        if (stack == null || stack.getAmount() <= 0) {
            return null;
        }
        if (stack instanceof PatternItemStack && stack.getAmount() > MAX_ITEM_AMOUNT) {
            IPatternStack copy = stack.copy();
            copy.addAmount(MAX_ITEM_AMOUNT - copy.getAmount());
            return copy;
        }
        return stack;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean(PROCESSING_TAG, processing);
        NBTTagList inputList = new NBTTagList();
        for (int i = 0; i < inputs.size(); i++) {
            NBTTagCompound stackTag = new NBTTagCompound();
            inputs.get(i).writeToNBT(stackTag);
            stackTag.setInteger(SLOT_TAG, inputSlots.get(i));
            inputList.appendTag(stackTag);
        }
        tag.setTag(INPUTS_TAG, inputList);
        NBTTagList outputList = new NBTTagList();
        for (IPatternStack output : outputs) {
            NBTTagCompound stackTag = new NBTTagCompound();
            output.writeToNBT(stackTag);
            outputList.appendTag(stackTag);
        }
        tag.setTag(OUTPUTS_TAG, outputList);
        return tag;
    }

    public static PatternRecipeImport readFromNBT(NBTTagCompound tag) {
        List<IPatternStack> inputs = new ArrayList<>();
        List<Integer> inputSlots = new ArrayList<>();
        NBTTagList inputList = tag.getTagList(INPUTS_TAG, 10);
        for (int i = 0; i < inputList.tagCount(); i++) {
            NBTTagCompound stackTag = inputList.getCompoundTagAt(i);
            IPatternStack stack = IPatternStack.readFromNBT(stackTag);
            if (stack != null) {
                inputs.add(stack);
                inputSlots.add(stackTag.getInteger(SLOT_TAG));
            }
        }
        List<IPatternStack> outputs = new ArrayList<>();
        NBTTagList outputList = tag.getTagList(OUTPUTS_TAG, 10);
        for (int i = 0; i < outputList.tagCount(); i++) {
            IPatternStack stack = IPatternStack.readFromNBT(outputList.getCompoundTagAt(i));
            if (stack != null) {
                outputs.add(stack);
            }
        }
        return new PatternRecipeImport(tag.getBoolean(PROCESSING_TAG), inputs, inputSlots, outputs);
    }
}
