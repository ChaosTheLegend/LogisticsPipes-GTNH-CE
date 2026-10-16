package logisticspipes.gui.modularUI;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.utils.item.IItemHandler;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

/**
 * A {@link ModularSlot} for inventories whose {@code setInventorySlotContents} stores a copy, like
 * {@link logisticspipes.utils.item.SimpleStackInventory}.
 * <p>
 * MUI's shift-click merge reads the target stack, then asks {@link #getItemStackLimit} for the limit and finally grows
 * the stack it read. The default limit check empties the slot and puts the stack back, which replaces it with a copy in
 * such inventories, so the merge grows an orphaned stack and the moved items vanish. The limit is computed here without
 * touching the slot.
 */
public class SimpleInventorySlot extends ModularSlot {

    public SimpleInventorySlot(IItemHandler itemHandler, int index) {
        super(itemHandler, index);
    }

    @Override
    public int getItemStackLimit(@NotNull ItemStack stack) {
        return isIgnoreMaxStackSize() ? getSlotStackLimit() : Math.min(stack.getMaxStackSize(), getSlotStackLimit());
    }
}
