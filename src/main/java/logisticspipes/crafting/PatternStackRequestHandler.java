package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.crafting.patternStack.PatternStackHelper;
import logisticspipes.interfaces.routing.ISaveState;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;

/**
 * Tracks in-flight ingredients by their owning crafting order.
 *
 * <p>
 * The slot map is an incrementally maintained aggregate cache for capacity calculations and HUD rendering. Ownership is
 * never inferred from that cache, which makes cancellation deterministic and avoids rebuilding totals every tick.
 * </p>
 */
class PatternStackRequestHandler implements ISaveState {

    private static final String REQUESTED_TAG = "patternRequestedIngredients";
    private static final String PATTERN_SLOT_TAG = "patternSlot";
    private static final String REFERENCE_PREFIX = "owner";
    private static final int TAG_COMPOUND = 10;

    private final Map<Integer, List<IPatternStack>> requestedIngredients;
    private final Map<PatternCraftingReference, OwnedStacks> ownedRequests = new LinkedHashMap<>();
    private final Runnable changeListener;

    PatternStackRequestHandler(Map<Integer, List<IPatternStack>> requestedIngredients, Runnable changeListener) {
        this.requestedIngredients = requestedIngredients;
        this.changeListener = changeListener;
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

    int amount(int patternSlot, IPatternStack stack) {
        return stack == null ? 0 : amountMatching(patternSlot, stack::canMerge);
    }

    int amount(PatternCraftingReference owner, IPatternStack stack) {
        if (owner == null || stack == null) {
            return 0;
        }
        OwnedStacks owned = ownedRequests.get(owner);
        if (owned == null) {
            return 0;
        }
        int amount = 0;
        for (IPatternStack requested : owned.stacks) {
            if (requested.canMerge(stack)) {
                amount += requested.getAmount();
            }
        }
        return amount;
    }

    int amountMatching(PatternCraftingReference owner, Predicate<IPatternStack> matcher) {
        OwnedStacks owned = ownedRequests.get(owner);
        if (owned == null) {
            return 0;
        }
        int amount = 0;
        for (IPatternStack requested : owned.stacks) {
            if (matcher.test(requested)) {
                amount += requested.getAmount();
            }
        }
        return amount;
    }

    int amount(int patternSlot, ItemIdentifier item) {
        return amountMatching(patternSlot, stack -> PatternStackHelper.matches(stack, item));
    }

    int amountMatching(int patternSlot, Predicate<IPatternStack> matcher) {
        int amount = 0;
        for (IPatternStack requested : getExistingRequested(patternSlot)) {
            if (matcher.test(requested)) {
                amount += requested.getAmount();
            }
        }
        return amount;
    }

    int amount(int patternSlot, FluidIdentifier fluid) {
        return amountMatching(patternSlot, stack -> PatternStackHelper.matches(stack, fluid));
    }

    void add(PatternCraftingReference owner, int patternSlot, IPatternStack stack) {
        if (owner == null || stack == null || stack.getAmount() <= 0) {
            return;
        }
        OwnedStacks owned = ownedRequests.computeIfAbsent(owner, ignored -> new OwnedStacks(patternSlot));
        if (owned.patternSlot != patternSlot) {
            throw new IllegalArgumentException("A crafting object cannot own ingredients for multiple patterns");
        }
        PatternStackHelper.addAggregated(owned.stacks, stack.copy());
        PatternStackHelper.addAggregated(getOrCreateRequested(patternSlot), stack.copy());
        markChanged();
    }

    void remove(PatternCraftingReference owner, int patternSlot, IPatternStack stack) {
        if (owner == null || stack == null || stack.getAmount() <= 0) {
            return;
        }
        OwnedStacks owned = ownedRequests.get(owner);
        if (owned == null || owned.patternSlot != patternSlot) {
            return;
        }
        int removed = removeMatching(owned.stacks, stack.getAmount(), stack::canMerge);
        if (removed <= 0) {
            return;
        }
        removeMatching(getOrCreateRequested(patternSlot), removed, stack::canMerge);
        cleanup(owner, owned);
        cleanupAggregate(patternSlot);
        markChanged();
    }

    int removeMatching(PatternCraftingReference owner, int patternSlot, int amount, Predicate<IPatternStack> matcher) {
        if (owner == null || amount <= 0) {
            return 0;
        }
        OwnedStacks owned = ownedRequests.get(owner);
        if (owned == null || owned.patternSlot != patternSlot) {
            return 0;
        }
        List<IPatternStack> removedStacks = new ArrayList<>();
        int remaining = amount;
        for (int i = 0; i < owned.stacks.size() && remaining > 0; i++) {
            IPatternStack stack = owned.stacks.get(i);
            if (!matcher.test(stack)) {
                continue;
            }
            int removed = Math.min(remaining, stack.getAmount());
            removedStacks.add(PatternStackHelper.copyWithAmount(stack, removed));
            stack.addAmount(-removed);
            remaining -= removed;
            if (stack.getAmount() <= 0) {
                owned.stacks.remove(i--);
            }
        }
        int removed = amount - remaining;
        if (removed == 0) {
            return 0;
        }
        for (IPatternStack removedStack : removedStacks) {
            removeMatching(getOrCreateRequested(patternSlot), removedStack.getAmount(), removedStack::canMerge);
        }
        cleanup(owner, owned);
        cleanupAggregate(patternSlot);
        markChanged();
        return removed;
    }

    boolean removeAll(PatternCraftingReference owner) {
        OwnedStacks removed = ownedRequests.remove(owner);
        if (removed == null) {
            return false;
        }
        for (IPatternStack stack : removed.stacks) {
            removeMatching(getOrCreateRequested(removed.patternSlot), stack.getAmount(), stack::canMerge);
        }
        cleanupAggregate(removed.patternSlot);
        markChanged();
        return !removed.stacks.isEmpty();
    }

    boolean removeInstance(UUID instanceId) {
        boolean changed = false;
        for (PatternCraftingReference owner : new ArrayList<>(ownedRequests.keySet())) {
            if (instanceId.equals(owner.instanceId())) {
                changed |= removeAll(owner);
            }
        }
        return changed;
    }

    boolean removeAll(int patternSlot) {
        boolean changed = ownedRequests.entrySet().removeIf(entry -> entry.getValue().patternSlot == patternSlot);
        changed |= requestedIngredients.remove(patternSlot) != null;
        if (changed) {
            markChanged();
        }
        return changed;
    }

    List<OwnedEntry> entries() {
        List<OwnedEntry> entries = new ArrayList<>();
        for (Map.Entry<PatternCraftingReference, OwnedStacks> entry : ownedRequests.entrySet()) {
            for (IPatternStack stack : entry.getValue().stacks) {
                entries.add(new OwnedEntry(entry.getKey(), entry.getValue().patternSlot, stack.copy()));
            }
        }
        return entries;
    }

    private void cleanup(PatternCraftingReference owner, OwnedStacks owned) {
        owned.stacks.removeIf(stack -> stack.getAmount() <= 0);
        if (owned.stacks.isEmpty()) {
            ownedRequests.remove(owner);
        }
    }

    private void cleanupAggregate(int patternSlot) {
        List<IPatternStack> requested = requestedIngredients.get(patternSlot);
        if (requested == null) {
            return;
        }
        requested.removeIf(stack -> stack.getAmount() <= 0);
        if (requested.isEmpty()) {
            requestedIngredients.remove(patternSlot);
        }
    }

    private List<IPatternStack> getExistingRequested(int patternSlot) {
        List<IPatternStack> requested = requestedIngredients.get(patternSlot);
        return requested == null ? Collections.emptyList() : requested;
    }

    private List<IPatternStack> getOrCreateRequested(int patternSlot) {
        return requestedIngredients.computeIfAbsent(patternSlot, ignored -> new ArrayList<>());
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        requestedIngredients.clear();
        ownedRequests.clear();
        NBTTagList requested = tag.getTagList(REQUESTED_TAG, TAG_COMPOUND);
        for (int i = 0; i < requested.tagCount(); i++) {
            NBTTagCompound stackTag = requested.getCompoundTagAt(i);
            PatternCraftingReference owner = PatternCraftingReference.readFromNBT(stackTag, REFERENCE_PREFIX);
            IPatternStack stack = IPatternStack.readFromNBT(stackTag);
            if (owner != null && stack != null && stack.getAmount() > 0) {
                add(owner, stackTag.getInteger(PATTERN_SLOT_TAG), stack);
            }
        }
        markChanged();
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList requested = new NBTTagList();
        for (Map.Entry<PatternCraftingReference, OwnedStacks> entry : ownedRequests.entrySet()) {
            for (IPatternStack stack : entry.getValue().stacks) {
                if (stack == null || stack.getAmount() <= 0) {
                    continue;
                }
                NBTTagCompound stackTag = new NBTTagCompound();
                stack.writeToNBT(stackTag);
                stackTag.setInteger(PATTERN_SLOT_TAG, entry.getValue().patternSlot);
                entry.getKey().writeToNBT(stackTag, REFERENCE_PREFIX);
                requested.appendTag(stackTag);
            }
        }
        tag.setTag(REQUESTED_TAG, requested);
    }

    private void markChanged() {
        if (changeListener != null) {
            changeListener.run();
        }
    }

    static final class OwnedEntry {

        final PatternCraftingReference owner;
        final int patternSlot;
        final IPatternStack stack;

        private OwnedEntry(PatternCraftingReference owner, int patternSlot, IPatternStack stack) {
            this.owner = owner;
            this.patternSlot = patternSlot;
            this.stack = stack;
        }
    }

    private static final class OwnedStacks {

        private final int patternSlot;
        private final List<IPatternStack> stacks = new ArrayList<>();

        private OwnedStacks(int patternSlot) {
            this.patternSlot = patternSlot;
        }
    }
}
