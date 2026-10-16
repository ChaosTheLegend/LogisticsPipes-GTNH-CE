package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.crafting.patternStack.PatternStackHelper;
import logisticspipes.interfaces.routing.ISaveState;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierInventory;

/** Buffered ingredients with stable crafting ownership and an aggregate-by-slot cache. */
class PatternStackBufferHandler implements ISaveState {

    private static final String BUFFER_TAG = "patternIngredientBuffer";
    private static final String PATTERN_SLOT_TAG = "patternSlot";
    private static final String REFERENCE_PREFIX = "owner";
    private static final int TAG_COMPOUND = 10;

    private final Map<Integer, List<IPatternStack>> bufferedIngredients = new HashMap<>();
    private final Map<PatternCraftingReference, OwnedStacks> ownedBuffer = new LinkedHashMap<>();
    private final Runnable changeListener;
    private long changeVersion;

    PatternStackBufferHandler(Runnable changeListener) {
        this.changeListener = changeListener;
    }

    int amount(int patternSlot, IPatternStack stack) {
        if (stack == null) {
            return 0;
        }
        int amount = 0;
        for (IPatternStack buffered : getExistingBuffer(patternSlot)) {
            if (buffered.canMerge(stack)) {
                amount += buffered.getAmount();
            }
        }
        return amount;
    }

    private static int removeMatching(List<IPatternStack> stacks, int amount, Predicate<IPatternStack> matcher) {
        int removedTotal = 0;
        for (int i = 0; i < stacks.size() && amount > 0; i++) {
            IPatternStack stack = stacks.get(i);
            if (!matcher.test(stack)) {
                continue;
            }
            int removed = Math.min(amount, stack.getAmount());
            stack.addAmount(-removed);
            amount -= removed;
            removedTotal += removed;
            if (stack.getAmount() <= 0) {
                stacks.remove(i--);
            }
        }
        return removedTotal;
    }

    private static List<IPatternStack> copyStacks(List<IPatternStack> stacks) {
        List<IPatternStack> copy = new ArrayList<>(stacks.size());
        for (IPatternStack stack : stacks) {
            if (stack != null && stack.getAmount() > 0) {
                copy.add(stack.copy());
            }
        }
        return copy;
    }

    int amount(int patternSlot, ItemIdentifier item) {
        return amountMatching(patternSlot, stack -> PatternStackHelper.matches(stack, item));
    }

    int amount(int patternSlot, FluidIdentifier fluid) {
        return amountMatching(patternSlot, stack -> PatternStackHelper.matches(stack, fluid));
    }

    private int amountMatching(int patternSlot, Predicate<IPatternStack> matcher) {
        int amount = 0;
        for (IPatternStack stack : getExistingBuffer(patternSlot)) {
            if (matcher.test(stack)) {
                amount += stack.getAmount();
            }
        }
        return amount;
    }

    void add(PatternCraftingReference owner, int patternSlot, IPatternStack stack) {
        if (owner == null || stack == null || stack.getAmount() <= 0) {
            return;
        }
        OwnedStacks owned = ownedBuffer.computeIfAbsent(owner, ignored -> new OwnedStacks(patternSlot));
        if (owned.patternSlot != patternSlot) {
            throw new IllegalArgumentException("A crafting object cannot own ingredients for multiple patterns");
        }
        PatternStackHelper.addAggregated(owned.stacks, stack.copy());
        PatternStackHelper.addAggregated(getOrCreateBuffer(patternSlot), stack.copy());
        markChanged();
    }

    List<PatternCraftingReference> owners(int patternSlot) {
        List<PatternCraftingReference> result = new ArrayList<>();
        for (Map.Entry<PatternCraftingReference, OwnedStacks> entry : ownedBuffer.entrySet()) {
            if (entry.getValue().patternSlot == patternSlot && !entry.getValue().stacks.isEmpty()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    List<IPatternStack> copyOwnedStacks(PatternCraftingReference owner) {
        OwnedStacks owned = ownedBuffer.get(owner);
        return owned == null ? new ArrayList<>() : copyStacks(owned.stacks);
    }

    void remove(PatternCraftingReference owner, IPatternStack stack, int amount) {
        if (owner == null || stack == null || amount <= 0) {
            return;
        }
        OwnedStacks owned = ownedBuffer.get(owner);
        if (owned == null) {
            return;
        }
        int removed = removeMatching(owned.stacks, amount, stack::canMerge);
        if (removed <= 0) {
            return;
        }
        removeMatching(getOrCreateBuffer(owned.patternSlot), removed, stack::canMerge);
        if (owned.stacks.isEmpty()) {
            ownedBuffer.remove(owner);
        }
        cleanupAggregate(owned.patternSlot);
        markChanged();
    }

    List<IPatternStack> removeAll(PatternCraftingReference owner) {
        OwnedStacks removed = ownedBuffer.remove(owner);
        if (removed == null) {
            return new ArrayList<>();
        }
        List<IPatternStack> result = copyStacks(removed.stacks);
        for (IPatternStack stack : removed.stacks) {
            removeMatching(getOrCreateBuffer(removed.patternSlot), stack.getAmount(), stack::canMerge);
        }
        cleanupAggregate(removed.patternSlot);
        markChanged();
        return result;
    }

    List<OwnedEntry> entries(UUID instanceId) {
        List<OwnedEntry> entries = new ArrayList<>();
        for (Map.Entry<PatternCraftingReference, OwnedStacks> entry : ownedBuffer.entrySet()) {
            if (instanceId.equals(entry.getKey().instanceId())) {
                entries.add(new OwnedEntry(entry.getKey(), entry.getValue().patternSlot));
            }
        }
        return entries;
    }

    private List<IPatternStack> getExistingBuffer(int patternSlot) {
        List<IPatternStack> buffer = bufferedIngredients.get(patternSlot);
        return buffer == null ? Collections.emptyList() : buffer;
    }

    public List<IPatternStack> removeAll(int patternSlot) {
        List<IPatternStack> removed = bufferedIngredients.remove(patternSlot);
        ownedBuffer.entrySet().removeIf(entry -> entry.getValue().patternSlot == patternSlot);
        if (removed == null) {
            return new ArrayList<>();
        }
        markChanged();
        return copyStacks(removed);
    }

    private List<IPatternStack> getOrCreateBuffer(int patternSlot) {
        return bufferedIngredients.computeIfAbsent(patternSlot, ignored -> new ArrayList<>());
    }

    public void dropContents(World world, int x, int y, int z) {
        if (MainProxy.isServer(world)) {
            for (List<IPatternStack> patternStacks : new ArrayList<>(bufferedIngredients.values())) {
                for (IPatternStack patternStack : patternStacks) {
                    for (ItemStack stack : makeItemStacks(patternStack)) {
                        ItemIdentifierInventory.dropItems(world, stack, x, y, z);
                    }
                }
            }
            clear();
        }
    }

    private void cleanupAggregate(int patternSlot) {
        List<IPatternStack> buffer = bufferedIngredients.get(patternSlot);
        if (buffer == null) {
            return;
        }
        buffer.removeIf(stack -> stack.getAmount() <= 0);
        if (buffer.isEmpty()) {
            bufferedIngredients.remove(patternSlot);
        }
    }

    public int size() {
        return bufferedIngredients.size();
    }

    public void clear() {
        if (bufferedIngredients.isEmpty() && ownedBuffer.isEmpty()) {
            return;
        }
        bufferedIngredients.clear();
        ownedBuffer.clear();
        markChanged();
    }

    long changeVersion() {
        return changeVersion;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        bufferedIngredients.clear();
        ownedBuffer.clear();
        NBTTagList buffer = tag.getTagList(BUFFER_TAG, TAG_COMPOUND);
        for (int i = 0; i < buffer.tagCount(); i++) {
            NBTTagCompound stackTag = buffer.getCompoundTagAt(i);
            PatternCraftingReference owner = PatternCraftingReference.readFromNBT(stackTag, REFERENCE_PREFIX);
            IPatternStack stack = IPatternStack.readFromNBT(stackTag);
            if (owner != null && stack != null && stack.getAmount() > 0) {
                add(owner, stackTag.getInteger(PATTERN_SLOT_TAG), stack);
            }
        }
        markChanged();
    }

    public Map<Integer, List<IPatternStack>> asMap() {
        return bufferedIngredients;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList buffer = new NBTTagList();
        for (Map.Entry<PatternCraftingReference, OwnedStacks> entry : ownedBuffer.entrySet()) {
            for (IPatternStack stack : entry.getValue().stacks) {
                NBTTagCompound stackTag = new NBTTagCompound();
                stack.writeToNBT(stackTag);
                stackTag.setInteger(PATTERN_SLOT_TAG, entry.getValue().patternSlot);
                entry.getKey().writeToNBT(stackTag, REFERENCE_PREFIX);
                buffer.appendTag(stackTag);
            }
        }
        tag.setTag(BUFFER_TAG, buffer);
    }

    public List<Integer> keySet() {
        return new ArrayList<>(bufferedIngredients.keySet());
    }

    private void markChanged() {
        changeVersion++;
        if (changeListener != null) {
            changeListener.run();
        }
    }

    private static final class OwnedStacks {

        private final int patternSlot;
        private final List<IPatternStack> stacks = new ArrayList<>();

        private OwnedStacks(int patternSlot) {
            this.patternSlot = patternSlot;
        }
    }

    static final class OwnedEntry {

        final PatternCraftingReference owner;
        final int patternSlot;

        private OwnedEntry(PatternCraftingReference owner, int patternSlot) {
            this.owner = owner;
            this.patternSlot = patternSlot;
        }
    }

    static List<ItemStack> makeItemStacks(IPatternStack patternStack) {
        List<ItemStack> stacks = new ArrayList<>();
        if (patternStack == null || patternStack.getAmount() <= 0) {
            return stacks;
        }
        ItemStack stack = patternStack.makePatternStack();
        if (stack == null || stack.stackSize <= 0) {
            return stacks;
        }
        int amount = stack.stackSize;
        int maxStackSize = Math.max(1, stack.getMaxStackSize());
        while (amount > 0) {
            ItemStack split = stack.copy();
            split.stackSize = Math.min(amount, maxStackSize);
            stacks.add(split);
            amount -= split.stackSize;
        }
        return stacks;
    }
}
