package logisticspipes.crafting.requesttable;

import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.item.ItemStack;

/** Counts output from available ingredients without touching inventories or running crafting callbacks. */
final class RequestTableCraftingCounter {

    private RequestTableCraftingCounter() {}

    static int count(ItemStack[] stored, ItemStack[] pattern, BiPredicate<ItemStack, ItemStack> matching,
            BiPredicate<ItemStack, ItemStack> exact, Predicate<ItemStack> reusable,
            Function<ItemStack[], ItemStack> recipe) {
        ItemStack[] remaining = new ItemStack[stored.length];
        for (int i = 0; i < stored.length; i++) {
            remaining[i] = ItemStack.copyItemStack(stored[i]);
        }
        long total = 0;
        while (true) {
            Batch batch = findBatch(remaining, pattern, matching, reusable, recipe);
            if (batch == null) {
                batch = findBatch(remaining, pattern, exact, reusable, recipe);
            }
            if (batch == null) {
                return (int) Math.min(Integer.MAX_VALUE, total);
            }
            int crafts = Integer.MAX_VALUE;
            boolean consumes = false;
            for (int i = 0; i < remaining.length; i++) {
                if (batch.consumed[i] > 0) {
                    consumes = true;
                    int reservedTools = batch.used[i] - batch.consumed[i];
                    crafts = Math.min(crafts, (remaining[i].stackSize - reservedTools) / batch.consumed[i]);
                }
            }
            if (!consumes) return (int) Math.min(Integer.MAX_VALUE, total + batch.output);
            // Each batch exhausts at least one ingredient stack, so work scales with slots rather than item amounts.
            for (int i = 0; i < remaining.length; i++) {
                if (batch.consumed[i] > 0) {
                    remaining[i].stackSize -= crafts * batch.consumed[i];
                }
            }
            total += (long) crafts * batch.output;
        }
    }

    private static Batch findBatch(ItemStack[] stored, ItemStack[] pattern, BiPredicate<ItemStack, ItemStack> matching,
            Predicate<ItemStack> reusable, Function<ItemStack[], ItemStack> recipe) {
        int[] used = new int[stored.length];
        int[] consumed = new int[stored.length];
        ItemStack[] inputs = new ItemStack[pattern.length];
        boolean hasIngredients = false;
        for (int i = 0; i < pattern.length; i++) {
            if (pattern[i] == null) {
                continue;
            }
            hasIngredients = true;
            for (int slot = 0; slot < stored.length; slot++) {
                ItemStack candidate = stored[slot];
                if (candidate != null && candidate.stackSize > used[slot] && matching.test(pattern[i], candidate)) {
                    used[slot]++;
                    if (!reusable.test(pattern[i])) consumed[slot]++;
                    inputs[i] = candidate.copy();
                    inputs[i].stackSize = 1;
                    break;
                }
            }
            if (inputs[i] == null) {
                return null;
            }
        }
        if (!hasIngredients) {
            return null;
        }
        ItemStack output = recipe.apply(inputs);
        return output == null || output.stackSize <= 0 ? null : new Batch(used, consumed, output.stackSize);
    }

    private static final class Batch {

        private final int[] used;
        private final int[] consumed;
        private final int output;

        private Batch(int[] used, int[] consumed, int output) {
            this.used = used;
            this.consumed = consumed;
            this.output = output;
        }
    }
}
