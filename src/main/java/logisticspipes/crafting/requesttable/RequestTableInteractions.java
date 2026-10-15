package logisticspipes.crafting.requesttable;

import static logisticspipes.crafting.requesttable.RequestTableContainer.getFluidStack;
import static logisticspipes.crafting.requesttable.RequestTableContainer.getItemStack;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import logisticspipes.LogisticsPipes;
import logisticspipes.crafting.requesttable.storage.RequestTableFluidContainers;
import logisticspipes.crafting.requesttable.storage.RequestTableFluidStorage;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.FluidIdentifier;

/**
 * Server-side storage interactions shared by the legacy request table container and its ModularUI GUI.
 * <p>
 * Item storage slots may hold compressed stacks above the item's maximum stack size, so clicks never move more than one
 * normal stack at a time.
 */
public final class RequestTableInteractions {

    private RequestTableInteractions() {}

    /**
     * Picks up, inserts or swaps the cursor stack with an item storage slot.
     *
     * @return the slot contents before the click
     */
    public static ItemStack clickItemStorage(Slot slot, int mouseButton, EntityPlayer player) {
        ItemStack stored = slot.getStack();
        ItemStack original = stored == null ? null : stored.copy();
        ItemStack cursor = player.inventory.getItemStack();
        if (cursor == null) {
            if (stored != null) {
                int amount = mouseButton == 0 ? stored.stackSize : (stored.stackSize + 1) / 2;
                ItemStack taken = slot.decrStackSize(Math.min(amount, stored.getMaxStackSize()));
                player.inventory.setItemStack(taken);
                slot.onPickupFromSlot(player, taken);
            }
        } else if (stored == null || (stored.isItemEqual(cursor) && ItemStack.areItemStackTagsEqual(stored, cursor))) {
            int room = slot.getSlotStackLimit() - (stored == null ? 0 : stored.stackSize);
            int amount = Math.min(room, mouseButton == 0 ? cursor.stackSize : 1);
            if (amount > 0) {
                ItemStack inserted = cursor.splitStack(amount);
                if (stored != null) {
                    inserted.stackSize += stored.stackSize;
                }
                slot.putStack(inserted);
                if (cursor.stackSize == 0) {
                    player.inventory.setItemStack(null);
                }
            }
        } else if (stored.stackSize <= stored.getMaxStackSize() && cursor.stackSize <= slot.getSlotStackLimit()) {
            slot.putStack(cursor);
            player.inventory.setItemStack(stored);
            slot.onPickupFromSlot(player, stored);
        }
        slot.onSlotChanged();
        return original;
    }

    /** Fills or empties the held fluid container with one fluid storage tank. */
    public static void clickFluidStorage(RequestTablePipe table, int fluidSlot, int mouseButton, EntityPlayer player) {
        if (!table.isFluidEnabled()) {
            return;
        }
        RequestTableFluidContainers.interact(
                player.inventory,
                mouseButton,
                RequestTableFluidContainers.stage(
                        table.getFluidStorage(),
                        (storage, cursor) -> getFluidStorageClickResult(table, storage, fluidSlot, cursor)));
    }

    /**
     * Moves a player inventory stack into the table, following the routing rules of the active view.
     *
     * @return the original stack if anything moved, otherwise {@code null}
     */
    public static ItemStack transferPlayerStack(RequestTablePipe table, EntityPlayer player, Slot slot,
            RequestTableView view) {
        boolean filledCell = table.isFluidEnabled() && view != RequestTableView.ITEM_STORAGE
                && table.isFilledFluidContainer(slot.getStack());
        if (view.transfersFluids(filledCell)) {
            if (!player.worldObj.isRemote) {
                table.emptyFluidContainers(player, slot.getSlotIndex());
            }
            // Do not let vanilla retry a converted GT cell, whose empty form can use the same Item.
            return null;
        }
        if (!view.transfersItems(filledCell)) {
            return null;
        }
        return transferPlayerStackToInternalStorage(table, player, slot);
    }

    /** Inserts held items with the same storage routing as a Main-view shift-click. */
    public static boolean insertCursorItem(RequestTablePipe table, EntityPlayer player, int mouseButton) {
        if (player.worldObj.isRemote || (mouseButton != 0 && mouseButton != 1)) {
            return false;
        }
        ItemStack cursor = player.inventory.getItemStack();
        if (cursor == null || cursor.stackSize <= 0) {
            return false;
        }
        ItemStack moving = cursor.copy();
        moving.stackSize = mouseButton == 0 ? cursor.stackSize : 1;
        int amount = moving.stackSize;
        moving.stackSize = table.inv.addCompressed(moving, true);
        int moved = amount - moving.stackSize;
        if (moved <= 0) {
            return false;
        }
        cursor.stackSize -= moved;
        player.inventory.setItemStack(cursor.stackSize == 0 ? null : cursor);
        return true;
    }

    private static ItemStack transferPlayerStackToInternalStorage(RequestTablePipe table, EntityPlayer player,
            Slot sourceSlot) {
        ItemStack source = sourceSlot.getStack();
        ItemStack original = source.copy();
        int remaining = table.inv.addCompressed(source, true);
        int moved = source.stackSize - remaining;
        if (moved <= 0) {
            return null;
        }
        ItemStack movedStack = source.copy();
        movedStack.stackSize = moved;
        if (remaining <= 0) {
            sourceSlot.putStack(null);
        } else {
            source.stackSize = remaining;
            sourceSlot.putStack(source);
        }
        sourceSlot.onPickupFromSlot(player, movedStack);
        sourceSlot.onSlotChanged();
        return original;
    }

    private static ItemStack getFluidStorageClickResult(RequestTablePipe table, RequestTableFluidStorage storage,
            int fluidSlot, ItemStack cursor) {
        FluidStack stored = storage.getFluid(fluidSlot);
        FluidStack held = getFluidStack(cursor);
        ItemStack result = cursor;
        if (stored != null) {
            if (held == null) {
                result = fillContainerFromSlot(storage, fluidSlot, cursor, stored);
            } else if (sameFluid(stored, held)) {
                result = isContainerFull(table, cursor, held) ? emptyContainerIntoSlot(storage, fluidSlot, cursor, held)
                        : fillContainerFromSlot(storage, fluidSlot, cursor, stored);
            }
        } else if (held != null) {
            result = emptyContainerIntoSlot(storage, fluidSlot, cursor, held);
        }
        return result;
    }

    private static ItemStack fillContainerFromSlot(RequestTableFluidStorage storage, int fluidSlot, ItemStack cursor,
            FluidStack stored) {
        if (cursor.getItem() instanceof IFluidContainerItem container) {
            ItemStack filled = cursor.copy();
            int fillable = container.fill(filled, stored.copy(), false);
            if (fillable <= 0) {
                return cursor;
            }
            FluidStack drained = storage.drain(fluidSlot, fillable, true);
            if (drained == null || drained.amount <= 0) {
                return cursor;
            }
            container.fill(filled, drained, true);
            return filled;
        }
        if (cursor.getItem() == LogisticsPipes.LogisticsFluidContainer) {
            FluidStack held = getFluidStack(cursor);
            int room = storage.getSlotCapacity() - (held == null ? 0 : held.amount);
            if (room <= 0) {
                return cursor;
            }
            FluidStack drained = storage.drain(fluidSlot, room, true);
            if (drained == null || drained.amount <= 0) {
                return cursor;
            }
            if (held != null) {
                drained.amount += held.amount;
            }
            return SimpleServiceLocator.logisticsFluidManager.getFluidContainer(drained).makeNormalStack();
        }
        ItemStack filled = FluidContainerRegistry.fillFluidContainer(stored, cursor);
        FluidStack filledFluid = getFluidStack(filled);
        if (filled == null || filledFluid == null
                || !sameFluid(stored, filledFluid)
                || filledFluid.amount > stored.amount) {
            return cursor;
        }
        storage.drain(fluidSlot, filledFluid.amount, true);
        return filled;
    }

    private static ItemStack emptyContainerIntoSlot(RequestTableFluidStorage storage, int fluidSlot, ItemStack cursor,
            FluidStack held) {
        if (cursor.getItem() instanceof IFluidContainerItem container) {
            ItemStack drainedContainer = cursor.copy();
            int accepted = storage.fillSlot(fluidSlot, held, false);
            if (accepted <= 0) {
                return cursor;
            }
            FluidStack drained = container.drain(drainedContainer, accepted, true);
            if (drained == null || drained.amount <= 0) {
                return cursor;
            }
            storage.fillSlot(fluidSlot, drained, true);
            return drainedContainer;
        }
        if (cursor.getItem() == LogisticsPipes.LogisticsFluidContainer) {
            int accepted = storage.fillSlot(fluidSlot, held, false);
            if (accepted <= 0) {
                return cursor;
            }
            FluidStack inserted = held.copy();
            inserted.amount = accepted;
            storage.fillSlot(fluidSlot, inserted, true);
            return getItemStack(held, accepted);
        }
        int accepted = storage.fillSlot(fluidSlot, held, false);
        if (accepted < held.amount) {
            return cursor;
        }
        ItemStack empty = FluidContainerRegistry.drainFluidContainer(cursor);
        if (empty == null) {
            return cursor;
        }
        storage.fillSlot(fluidSlot, held, true);
        return empty;
    }

    private static boolean isContainerFull(RequestTablePipe table, ItemStack stack, FluidStack held) {
        if (stack.getItem() instanceof IFluidContainerItem) {
            return held.amount >= ((IFluidContainerItem) stack.getItem()).getCapacity(stack);
        }
        if (stack.getItem() == LogisticsPipes.LogisticsFluidContainer) {
            return held.amount >= table.getFluidStorage().getSlotCapacity();
        }
        return true;
    }

    private static boolean sameFluid(FluidStack first, FluidStack second) {
        return first != null && second != null && FluidIdentifier.get(first).equals(FluidIdentifier.get(second));
    }
}
