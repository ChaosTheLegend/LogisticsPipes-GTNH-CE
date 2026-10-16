package logisticspipes.crafting.pattern;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.crafting.patternStack.PatternFluidStack;
import logisticspipes.crafting.patternStack.PatternItemStack;
import logisticspipes.crafting.patternStack.PatternStackHelper;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.string.ChatColor;
import logisticspipes.utils.string.StringUtils;
import lombok.Getter;
import lombok.NonNull;

@Getter
public abstract class AbstractPattern {

    private static final String MAIN_OUTPUT_TAG = "patternMainOutputSlot";
    private static final String ITEMS_TAG = "patternItems";
    private static final String SATELLITE_TARGETS_TAG = "patternSatelliteTargets";
    private static final String SATELLITE_TARGET_UUIDS_TAG = "patternSatelliteTargetUuids";
    private static final String FLUID_SATELLITE_TARGETS_TAG = "patternFluidSatelliteTargets";
    private static final String FLUID_SATELLITE_TARGET_UUIDS_TAG = "patternFluidSatelliteTargetUuids";
    private static final String BYPRODUCT_SATELLITE_TARGETS_TAG = "patternByproductSatelliteTargets";
    private static final String BYPRODUCT_SATELLITE_TARGET_UUIDS_TAG = "patternByproductSatelliteTargetUuids";
    private static final String FLUID_BYPRODUCT_SATELLITE_TARGETS_TAG = "patternFluidByproductSatelliteTargets";
    private static final String FLUID_BYPRODUCT_SATELLITE_TARGET_UUIDS_TAG = "patternFluidByproductSatelliteTargetUuids";
    private static final String ORE_DICT_SUBSTITUTION_TAG = "patternOreDictSubstitution";
    private static final String IGNORE_NBT_TAG = "patternIgnoreNbt";

    private final ItemStack patternStack;

    protected AbstractPattern(ItemStack patternStack) {
        this.patternStack = patternStack;
    }

    public abstract int getIngredientSlotCount();

    public abstract int getResultSlotCount();

    public int getResultSlotStart() {
        return getIngredientSlotCount();
    }

    public int getItemSlotCount() {
        return getIngredientSlotCount() + getResultSlotCount();
    }

    public List<IPatternStack> getAggregatedInputs() {
        return PatternStackHelper.aggregate(getInputs());
    }

    public List<IPatternStack> getAggregatedOutputs() {
        return PatternStackHelper.aggregate(getOutputs());
    }

    /** Uses the first populated output for patterns that have no explicit selection. */
    public int getMainOutputSlot() {
        if (patternStack != null && patternStack.hasTagCompound()
                && patternStack.getTagCompound().hasKey(MAIN_OUTPUT_TAG)) {
            int slot = patternStack.getTagCompound().getInteger(MAIN_OUTPUT_TAG);
            if (slot >= 0 && slot < getResultSlotCount()
                    && getPatternStackInSlot(getResultSlotStart() + slot) != null) {
                return slot;
            }
        }
        for (int slot = 0; slot < getResultSlotCount(); slot++) {
            if (getPatternStackInSlot(getResultSlotStart() + slot) != null) {
                return slot;
            }
        }
        return -1;
    }

    public void setMainOutputSlot(int slot) {
        if (patternStack != null && slot >= 0
                && slot < getResultSlotCount()
                && getPatternStackInSlot(getResultSlotStart() + slot) != null) {
            getOrCreateTag(patternStack).setInteger(MAIN_OUTPUT_TAG, slot);
        }
    }

    public List<IPatternStack> getCraftableOutputs() {
        IPatternStack main = getPatternStackInSlot(getResultSlotStart() + getMainOutputSlot());
        List<IPatternStack> outputs = new ArrayList<>();
        if (getMainOutputSlot() >= 0 && main != null) {
            outputs.add(main);
        }
        return outputs;
    }

    public List<ItemIdentifierStack> getAggregatedIngredients() {
        return toItemIdentifierStacks(getSolidPatternStacks(getAggregatedInputs()));
    }

    public List<PatternFluidStack> getAggregatedFluidIngredients() {
        return getFluidPatternStacks(getAggregatedInputs());
    }

    /**
     * Clears all item and fluid representations stored by this pattern.
     */
    public void clear() {
        if (patternStack != null && patternStack.hasTagCompound()) {
            patternStack.getTagCompound().removeTag(MAIN_OUTPUT_TAG);
        }
        for (int i = 0; i < getItemSlotCount(); i++) {
            setStackInSlot(i, null);
        }
        for (int i = 0; i < getIngredientSlotCount(); i++) {
            setSatelliteIdForInputSlot(i, 0);
            setFluidSatelliteIdForInputSlot(i, 0);
        }
        for (int i = 0; i < getResultSlotCount(); i++) {
            setByproductSatelliteIdForOutputSlot(i, 0);
            setFluidByproductSatelliteIdForOutputSlot(i, 0);
        }
    }

    /**
     * Multiplies both item stack sizes and fluid amounts stored by this pattern.
     */
    public void multiply(int factor) {
        for (int i = 0; i < getItemSlotCount(); i++) {
            IPatternStack stack = getPatternStackInSlot(i);
            if (stack != null) {
                IPatternStack copy = stack.copy();
                copy.addAmount(stack.getAmount() * (factor - 1));
                setPatternStackInSlot(i, copy);
            }
        }
    }

    public ItemStack getStackInSlot(int slot) {
        IPatternStack stack = getPatternStackInSlot(slot);
        return stack == null ? null : stack.makePatternStack();
    }

    public IPatternStack getPatternStackInSlot(int slot) {
        if (patternStack == null || slot < 0 || slot >= getItemSlotCount() || !patternStack.hasTagCompound()) {
            return null;
        }
        NBTTagList list = patternStack.getTagCompound().getTagList(ITEMS_TAG, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            if (tag.getInteger("slot") == slot) {
                return IPatternStack.readFromNBT(tag);
            }
        }
        return null;
    }

    public void setStackInSlot(int slot, ItemStack stack) {
        setPatternStackInSlot(slot, IPatternStack.fromItemStack(stack));
    }

    public void setPatternStackInSlot(int slot, IPatternStack stack) {
        if (patternStack == null || slot < 0 || slot >= getItemSlotCount()) {
            return;
        }
        NBTTagCompound root = getOrCreateTag(patternStack);
        NBTTagList oldList = root.getTagList(ITEMS_TAG, 10);
        NBTTagList newList = new NBTTagList();
        for (int i = 0; i < oldList.tagCount(); i++) {
            NBTTagCompound tag = oldList.getCompoundTagAt(i);
            if (tag.getInteger("slot") != slot) {
                newList.appendTag(tag);
            }
        }
        if (stack != null && stack.getAmount() > 0) {
            NBTTagCompound tag = new NBTTagCompound();
            stack.writeToNBT(tag);
            tag.setInteger("slot", slot);
            newList.appendTag(tag);
        }
        root.setTag(ITEMS_TAG, newList);
        if ((stack == null || stack.getAmount() <= 0) && root.hasKey(MAIN_OUTPUT_TAG)
                && root.getInteger(MAIN_OUTPUT_TAG) == slot - getResultSlotStart()) {
            root.removeTag(MAIN_OUTPUT_TAG);
        }
    }

    /**
     * Returns whether item ingredients may be substituted by matching OreDict entries while the request tree is built.
     */
    public boolean isOreDictSubstitutionEnabled() {
        return getBooleanTag(ORE_DICT_SUBSTITUTION_TAG);
    }

    /**
     * Stores whether request-tree ingredient resources should allow OreDict substitutions for this pattern.
     */
    public void setOreDictSubstitutionEnabled(boolean enabled) {
        setBooleanTag(ORE_DICT_SUBSTITUTION_TAG, enabled);
    }

    /**
     * Toggles OreDict substitution for all item ingredients in this pattern.
     */
    public void toggleOreDictSubstitution() {
        setOreDictSubstitutionEnabled(!isOreDictSubstitutionEnabled());
    }

    /**
     * Returns whether item ingredient request resources should ignore NBT differences.
     */
    public boolean isIgnoreNbtEnabled() {
        return getBooleanTag(IGNORE_NBT_TAG);
    }

    /**
     * Stores whether request-tree ingredient resources should ignore NBT for this pattern.
     */
    public void setIgnoreNbtEnabled(boolean enabled) {
        setBooleanTag(IGNORE_NBT_TAG, enabled);
    }

    /**
     * Toggles NBT-insensitive ingredient matching for this pattern.
     */
    public void toggleIgnoreNbt() {
        setIgnoreNbtEnabled(!isIgnoreNbtEnabled());
    }

    public int getSatelliteIdForInputSlot(int slot) {
        return getSatelliteId(slot, SATELLITE_TARGETS_TAG, getIngredientSlotCount());
    }

    public String getSatelliteUuidForInputSlot(int slot) {
        return getSatelliteUuid(slot, SATELLITE_TARGET_UUIDS_TAG, getIngredientSlotCount());
    }

    public void setSatelliteIdForInputSlot(int slot, int satelliteId) {
        setSatelliteTargetForInputSlot(slot, satelliteId, "");
    }

    public void setSatelliteTargetForInputSlot(int slot, int satelliteId, String satelliteUuid) {
        setSatelliteTarget(
                slot,
                satelliteId,
                satelliteUuid,
                SATELLITE_TARGETS_TAG,
                SATELLITE_TARGET_UUIDS_TAG,
                getIngredientSlotCount());
    }

    public int getFluidSatelliteIdForInputSlot(int slot) {
        return getSatelliteId(slot, FLUID_SATELLITE_TARGETS_TAG, getIngredientSlotCount());
    }

    public String getFluidSatelliteUuidForInputSlot(int slot) {
        return getSatelliteUuid(slot, FLUID_SATELLITE_TARGET_UUIDS_TAG, getIngredientSlotCount());
    }

    public void setFluidSatelliteIdForInputSlot(int slot, int satelliteId) {
        setFluidSatelliteTargetForInputSlot(slot, satelliteId, "");
    }

    public void setFluidSatelliteTargetForInputSlot(int slot, int satelliteId, String satelliteUuid) {
        setSatelliteTarget(
                slot,
                satelliteId,
                satelliteUuid,
                FLUID_SATELLITE_TARGETS_TAG,
                FLUID_SATELLITE_TARGET_UUIDS_TAG,
                getIngredientSlotCount());
    }

    public int getByproductSatelliteIdForOutputSlot(int slot) {
        return getSatelliteId(slot, BYPRODUCT_SATELLITE_TARGETS_TAG, getResultSlotCount());
    }

    public String getByproductSatelliteUuidForOutputSlot(int slot) {
        return getSatelliteUuid(slot, BYPRODUCT_SATELLITE_TARGET_UUIDS_TAG, getResultSlotCount());
    }

    public void setByproductSatelliteIdForOutputSlot(int slot, int satelliteId) {
        setByproductSatelliteTargetForOutputSlot(slot, satelliteId, "");
    }

    public void setByproductSatelliteTargetForOutputSlot(int slot, int satelliteId, String satelliteUuid) {
        setSatelliteTarget(
                slot,
                satelliteId,
                satelliteUuid,
                BYPRODUCT_SATELLITE_TARGETS_TAG,
                BYPRODUCT_SATELLITE_TARGET_UUIDS_TAG,
                getResultSlotCount());
    }

    public int getFluidByproductSatelliteIdForOutputSlot(int slot) {
        return getSatelliteId(slot, FLUID_BYPRODUCT_SATELLITE_TARGETS_TAG, getResultSlotCount());
    }

    public String getFluidByproductSatelliteUuidForOutputSlot(int slot) {
        return getSatelliteUuid(slot, FLUID_BYPRODUCT_SATELLITE_TARGET_UUIDS_TAG, getResultSlotCount());
    }

    public void setFluidByproductSatelliteIdForOutputSlot(int slot, int satelliteId) {
        setFluidByproductSatelliteTargetForOutputSlot(slot, satelliteId, "");
    }

    public void setFluidByproductSatelliteTargetForOutputSlot(int slot, int satelliteId, String satelliteUuid) {
        setSatelliteTarget(
                slot,
                satelliteId,
                satelliteUuid,
                FLUID_BYPRODUCT_SATELLITE_TARGETS_TAG,
                FLUID_BYPRODUCT_SATELLITE_TARGET_UUIDS_TAG,
                getResultSlotCount());
    }

    private int getSatelliteId(int slot, String targetTag, int slotCount) {
        if (patternStack == null || slot < 0 || slot >= slotCount || !patternStack.hasTagCompound()) {
            return 0;
        }
        int[] targets = patternStack.getTagCompound().getIntArray(targetTag);
        return slot < targets.length ? Math.max(0, targets[slot]) : 0;
    }

    private String getSatelliteUuid(int slot, String uuidTag, int slotCount) {
        if (patternStack == null || slot < 0 || slot >= slotCount || !patternStack.hasTagCompound()) {
            return "";
        }
        NBTTagCompound targets = patternStack.getTagCompound().getCompoundTag(uuidTag);
        return targets.getString(Integer.toString(slot));
    }

    private void setSatelliteTarget(int slot, int satelliteId, String satelliteUuid, String targetTag, String uuidTag,
            int slotCount) {
        if (patternStack == null || slot < 0 || slot >= slotCount) {
            return;
        }
        NBTTagCompound root = getOrCreateTag(patternStack);
        int[] existing = root.getIntArray(targetTag);
        int[] targets = new int[slotCount];
        System.arraycopy(existing, 0, targets, 0, Math.min(existing.length, targets.length));
        targets[slot] = Math.max(0, satelliteId);
        root.setIntArray(targetTag, targets);
        NBTTagCompound uuidTargets = root.getCompoundTag(uuidTag);
        if (satelliteUuid == null || satelliteUuid.isEmpty()) {
            uuidTargets.removeTag(Integer.toString(slot));
        } else {
            uuidTargets.setString(Integer.toString(slot), satelliteUuid);
        }
        root.setTag(uuidTag, uuidTargets);
    }

    public List<IPatternStack> getInputs() {
        return readPatternRange(0, getIngredientSlotCount());
    }

    public List<IPatternStack> getOutputs() {
        return readPatternRange(getResultSlotStart(), getItemSlotCount());
    }

    public List<ItemIdentifierStack> getIngredients() {
        return toItemIdentifierStacks(readSolidRange(0, getIngredientSlotCount()));
    }

    public List<ItemIdentifierStack> getResults() {
        return toItemIdentifierStacks(readSolidRange(getResultSlotStart(), getItemSlotCount()));
    }

    public List<PatternFluidStack> getFluidIngredients() {
        return readFluidRange(0, getIngredientSlotCount());
    }

    public List<PatternFluidStack> getFluidResults() {
        return readFluidRange(getResultSlotStart(), getItemSlotCount());
    }

    public void setFluidIngredients(List<PatternFluidStack> fluids) {
        setFluidStacksInRange(0, getIngredientSlotCount(), fluids);
    }

    public void setFluidResults(List<PatternFluidStack> fluids) {
        setFluidStacksInRange(getResultSlotStart(), getItemSlotCount(), fluids);
    }

    public ItemStack getPrimaryResultStack() {
        List<IPatternStack> outputs = getCraftableOutputs();
        if (!outputs.isEmpty()) {
            return outputs.get(0).makeDisplayItemStack();
        }
        return null;
    }

    public boolean isConfigured() {
        boolean hasInputs = !getInputs().isEmpty();
        boolean hasResults = !getOutputs().isEmpty();
        return hasInputs && hasResults;
    }

    public void addTooltipInformation(List<String> tooltip) {
        List<IPatternStack> outputs = getOutputs();
        if (outputs.isEmpty()) {
            return;
        }
        tooltip.add(ChatColor.AQUA + "Main output:");
        addPatternStacksToTooltip(tooltip, getCraftableOutputs(), ChatColor.DARK_BLUE);
        List<IPatternStack> byproducts = new ArrayList<>();
        for (int slot = 0; slot < getResultSlotCount(); slot++) {
            IPatternStack output = getPatternStackInSlot(getResultSlotStart() + slot);
            if (slot != getMainOutputSlot() && output != null) {
                byproducts.add(output);
            }
        }
        if (!byproducts.isEmpty()) {
            tooltip.add(ChatColor.AQUA + "Byproducts:");
            addPatternStacksToTooltip(tooltip, byproducts, ChatColor.DARK_BLUE);
        }
        if (!getInputs().isEmpty()) {
            StringUtils.addShiftAction(tooltip, () -> {
                tooltip.add(ChatColor.DARK_GREEN + "Ingredients:");
                addPatternStacksToTooltip(tooltip, getAggregatedInputs(), ChatColor.GREEN);
            });
        }
        if (isOreDictSubstitutionEnabled()) {
            tooltip.add(ChatColor.GRAY + "OreDict substitution enabled");
        }
        if (isIgnoreNbtEnabled()) {
            tooltip.add(ChatColor.GRAY + "Ignoring ingredient NBT");
        }
    }

    private void addPatternStacksToTooltip(List<String> tooltip, List<IPatternStack> stacks, ChatColor color) {
        for (IPatternStack stack : stacks) {
            if (stack instanceof PatternFluidStack) {
                tooltip.add(
                        "  " + ChatColor.WHITE
                                + stack.getAmount()
                                + "mB "
                                + color
                                + ((PatternFluidStack) stack).makeFluidStack().getLocalizedName());
            } else {
                ItemStack normalStack = stack.makeDisplayItemStack();
                tooltip.add(
                        "  " + ChatColor.WHITE + normalStack.stackSize + " " + color + normalStack.getDisplayName());
            }
        }
    }

    private List<ItemIdentifierStack> toItemIdentifierStacks(List<PatternItemStack> stacks) {
        List<ItemIdentifierStack> result = new ArrayList<>();
        for (PatternItemStack stack : stacks) {
            result.add(stack.getItemIdentifierStack().clone());
        }
        return result;
    }

    private List<PatternItemStack> getSolidPatternStacks(List<IPatternStack> stacks) {
        List<PatternItemStack> result = new ArrayList<>();
        for (IPatternStack stack : stacks) {
            if (stack instanceof PatternItemStack) {
                result.add(((PatternItemStack) stack).copy());
            }
        }
        return result;
    }

    private List<PatternFluidStack> getFluidPatternStacks(List<IPatternStack> stacks) {
        List<PatternFluidStack> result = new ArrayList<>();
        for (IPatternStack stack : stacks) {
            if (stack instanceof PatternFluidStack) {
                result.add(((PatternFluidStack) stack).copy());
            }
        }
        return result;
    }

    private List<PatternFluidStack> readFluidRange(int start, int end) {
        List<PatternFluidStack> fluids = new ArrayList<>();
        for (int slot = start; slot < end; slot++) {
            IPatternStack stack = getPatternStackInSlot(slot);
            if (stack instanceof PatternFluidStack && stack.getAmount() > 0) {
                fluids.add((PatternFluidStack) stack);
            }
        }
        return fluids;
    }

    private List<PatternItemStack> readSolidRange(int start, int end) {
        List<PatternItemStack> stacks = new ArrayList<>();
        for (int slot = start; slot < end; slot++) {
            IPatternStack stack = getPatternStackInSlot(slot);
            if (stack instanceof PatternItemStack && stack.getAmount() > 0) {
                stacks.add((PatternItemStack) stack);
            }
        }
        return stacks;
    }

    private List<IPatternStack> readPatternRange(int start, int end) {
        List<IPatternStack> stacks = new ArrayList<>();
        for (int slot = start; slot < end; slot++) {
            IPatternStack stack = getPatternStackInSlot(slot);
            if (stack != null && stack.getAmount() > 0) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    private void setFluidStacksInRange(int start, int end, List<PatternFluidStack> fluids) {
        int fluidIndex = 0;
        for (int slot = start; slot < end; slot++) {
            IPatternStack existing = getPatternStackInSlot(slot);
            if (existing != null && !(existing instanceof PatternFluidStack)) {
                continue;
            }
            PatternFluidStack fluid = fluidIndex < fluids.size() ? fluids.get(fluidIndex++) : null;
            setPatternStackInSlot(slot, fluid);
        }
    }

    private boolean getBooleanTag(String tagName) {
        return patternStack != null && patternStack.hasTagCompound()
                && patternStack.getTagCompound().getBoolean(tagName);
    }

    private void setBooleanTag(String tagName, boolean enabled) {
        if (patternStack == null) {
            return;
        }
        NBTTagCompound tag = getOrCreateTag(patternStack);
        if (enabled) {
            tag.setBoolean(tagName, true);
        } else {
            tag.removeTag(tagName);
        }
    }

    private NBTTagCompound getOrCreateTag(ItemStack stack) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        return stack.getTagCompound();
    }

    /** Checks the entire import before any existing contents are cleared. */
    public boolean canSetInputsAndOutputs(List<IPatternStack> inputs, List<Integer> indices,
            List<IPatternStack> outputs) {
        if (inputs == null || indices == null
                || outputs == null
                || inputs.size() != indices.size()
                || inputs.size() > getIngredientSlotCount()
                || outputs.size() > getResultSlotCount())
            return false;
        boolean[] occupied = new boolean[getIngredientSlotCount()];
        for (int i = 0; i < inputs.size(); i++) {
            Integer slot = indices.get(i);
            if (slot == null || slot < 0 || slot >= occupied.length || occupied[slot]) return false;
            IPatternStack input = inputs.get(i);
            if (input == null || input.getAmount() <= 0) return false;
            occupied[slot] = true;
        }
        for (IPatternStack output : outputs) {
            if (output == null || output.getAmount() <= 0) return false;
        }
        return true;
    }

    /** Replaces a complete recipe, preserving the supplied input slot positions. Invalid imports leave it unchanged. */
    public void setInputsAndOutputs(@NonNull List<IPatternStack> inputs, @NonNull List<Integer> indices,
            @NonNull List<IPatternStack> outputs) {
        if (!canSetInputsAndOutputs(inputs, indices, outputs)) return;
        clear();
        for (int i = 0; i < inputs.size(); i++) {
            setPatternStackInSlot(indices.get(i), inputs.get(i));
        }
        for (int i = 0; i < outputs.size(); i++) {
            setPatternStackInSlot(getResultSlotStart() + i, outputs.get(i));
        }
    }
}
