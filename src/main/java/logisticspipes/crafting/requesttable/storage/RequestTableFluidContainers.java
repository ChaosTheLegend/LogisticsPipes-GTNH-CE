package logisticspipes.crafting.requesttable.storage;

import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

/** Plans cell conversions before committing fluid and player inventory changes together. */
public final class RequestTableFluidContainers {

    private RequestTableFluidContainers() {}

    public static Transfer stage(RequestTableFluidStorage storage,
            BiFunction<RequestTableFluidStorage, ItemStack, ItemStack> operation) {
        RequestTableFluidStorage staged = storage.copy();
        return new Transfer(cell -> operation.apply(staged, cell), () -> storage.copyFrom(staged));
    }

    public static boolean interact(InventoryPlayer inventory, int mouseButton, Transfer transfer) {
        ItemStack cursor = inventory.getItemStack();
        if (cursor == null || cursor.stackSize <= 0 || (mouseButton != 0 && mouseButton != 1)) {
            return false;
        }
        int count = mouseButton == 0 ? cursor.stackSize : 1;
        ItemStack converted = convertStack(cursor, count, transfer);
        if (converted == null
                || converted.stackSize > Math.min(converted.getMaxStackSize(), inventory.getInventoryStackLimit())) {
            return false;
        }
        if (mouseButton == 0) {
            int remaining = cursor.stackSize - converted.stackSize;
            if (remaining > 0) {
                ItemStack remainder = cursor.copy();
                remainder.stackSize = remaining;
                ItemStack[] contents = copyContents(inventory);
                if (!insert(contents, remainder, inventory.getInventoryStackLimit())) {
                    return false;
                }
                applyContents(inventory, contents);
            }
            inventory.setItemStack(converted);
        } else {
            ItemStack[] contents = copyContents(inventory);
            if (!insert(contents, converted, inventory.getInventoryStackLimit())) {
                return false;
            }
            applyContents(inventory, contents);
            ItemStack remainder = cursor.copy();
            remainder.stackSize--;
            inventory.setItemStack(remainder.stackSize == 0 ? null : remainder);
        }
        transfer.commit.run();
        inventory.markDirty();
        return true;
    }

    private static ItemStack convertStack(ItemStack source, int count, Transfer transfer) {
        ItemStack converted = null;
        for (int i = 0; i < count; i++) {
            ItemStack cell = source.copy();
            cell.stackSize = 1;
            ItemStack result = transfer.operation.apply(cell);
            if (result == cell) {
                break;
            }
            if (result == null || result.stackSize != 1) {
                return null;
            }
            if (converted == null) {
                converted = result.copy();
            } else if (canMerge(converted, result)) {
                converted.stackSize++;
            } else {
                // A stack must remain one kind of container, including its fluid tags.
                return null;
            }
        }
        return converted;
    }

    public static boolean shiftClick(InventoryPlayer inventory, int sourceSlot, Supplier<Transfer> transfers) {
        if (sourceSlot < 0 || sourceSlot >= inventory.mainInventory.length) {
            return false;
        }
        ItemStack source = inventory.mainInventory[sourceSlot];
        if (source == null || source.stackSize <= 0) {
            return false;
        }
        int count = source.stackSize;
        Transfer wholeStack = transfers.get();
        ItemStack converted = convertStack(source, count, wholeStack);
        if (converted != null && converted.stackSize == count
                && converted.stackSize <= Math.min(converted.getMaxStackSize(), inventory.getInventoryStackLimit())) {
            // Reuse the source slot so a full player inventory can still empty an entire stack.
            inventory.mainInventory[sourceSlot] = converted;
            wholeStack.commit.run();
            inventory.markDirty();
            return true;
        }
        boolean changed = false;
        for (int i = 0; i < count; i++) {
            Transfer transfer = transfers.get();
            ItemStack cell = source.copy();
            cell.stackSize = 1;
            ItemStack result = transfer.operation.apply(cell);
            if (result == cell || result == null || result.stackSize != 1) {
                break;
            }
            ItemStack[] contents = copyContents(inventory);
            if (--contents[sourceSlot].stackSize == 0) {
                contents[sourceSlot] = null;
            }
            if (!insert(contents, result.copy(), inventory.getInventoryStackLimit())) {
                break;
            }
            applyContents(inventory, contents);
            transfer.commit.run();
            changed = true;
        }
        if (changed) {
            inventory.markDirty();
        }
        return changed;
    }

    private static ItemStack[] copyContents(InventoryPlayer inventory) {
        ItemStack[] contents = new ItemStack[inventory.mainInventory.length];
        for (int slot = 0; slot < contents.length; slot++) {
            contents[slot] = ItemStack.copyItemStack(inventory.mainInventory[slot]);
        }
        return contents;
    }

    private static void applyContents(InventoryPlayer inventory, ItemStack[] contents) {
        System.arraycopy(contents, 0, inventory.mainInventory, 0, contents.length);
    }

    private static boolean insert(ItemStack[] contents, ItemStack stack, int inventoryLimit) {
        int limit = Math.min(stack.getMaxStackSize(), inventoryLimit);
        for (ItemStack stored : contents) {
            if (stored != null && canMerge(stored, stack)) {
                int moved = Math.min(stack.stackSize, Math.max(0, limit - stored.stackSize));
                stored.stackSize += moved;
                stack.stackSize -= moved;
            }
        }
        for (int slot = 0; slot < contents.length && stack.stackSize > 0; slot++) {
            if (contents[slot] == null) {
                contents[slot] = stack.copy();
                contents[slot].stackSize = Math.min(stack.stackSize, limit);
                stack.stackSize -= contents[slot].stackSize;
            }
        }
        return stack.stackSize == 0;
    }

    private static boolean canMerge(ItemStack first, ItemStack second) {
        return first.isStackable() && first.isItemEqual(second) && ItemStack.areItemStackTagsEqual(first, second);
    }

    public static final class Transfer {

        private final UnaryOperator<ItemStack> operation;
        private final Runnable commit;

        Transfer(UnaryOperator<ItemStack> operation, Runnable commit) {
            this.operation = operation;
            this.commit = commit;
        }
    }
}
