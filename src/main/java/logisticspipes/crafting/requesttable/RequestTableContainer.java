package logisticspipes.crafting.requesttable;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import logisticspipes.LogisticsPipes;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.gui.DummyContainer;
import logisticspipes.utils.gui.DummySlot;
import logisticspipes.utils.gui.HandelableSlot;
import logisticspipes.utils.gui.UnmodifiableSlot;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * Container for the new request table.
 * <p>
 * All slots are registered once and then moved by the client layout. Slots that belong to an inactive scrollable view
 * are moved far outside the GUI so Minecraft cannot render or click them.
 */
public class RequestTableContainer extends DummyContainer {

    private static final int HIDDEN = -5000;
    private static final int SHIFT_CRAFT_LIMIT = 64;

    private final RequestTablePipe table;
    private final List<Slot> itemStorageSlots = new ArrayList<>();
    private final List<Slot> fluidStorageSlots = new ArrayList<>();
    private final List<Slot> craftingSlots = new ArrayList<>();
    private final List<Slot> playerSlots = new ArrayList<>();
    private final Slot resultSlot;
    private final int playerSlotStart;
    private final int playerSlotEnd;

    /**
     * Creates the complete slot set for the request table.
     *
     * @param player player opening the GUI
     * @param table  backing request table pipe
     */
    public RequestTableContainer(EntityPlayer player, RequestTablePipe table) {
        super(player, table.matrix, table);
        this.table = table;
        this.table.updateStorageUpgrades();

        for (int i = 0; i < table.inv.getSizeInventory(); i++) {
            int slotIndex = inventorySlots.size();
            addNormalSlot(i, table.inv, HIDDEN, HIDDEN);
            itemStorageSlots.add((Slot) inventorySlots.get(slotIndex));
        }
        for (int i = 0; i < table.getFluidStorage().getSizeInventory(); i++) {
            fluidStorageSlots.add(addSlotToContainer(new UnmodifiableSlot(table.getFluidStorage(), i, HIDDEN, HIDDEN)));
        }
        for (int i = 0; i < table.matrix.getSizeInventory(); i++) {
            craftingSlots.add(addSlotToContainer(new DummySlot(table.matrix, i, HIDDEN, HIDDEN)));
        }
        resultSlot = addSlotToContainer(
                new HandelableSlot(table.resultInv, 0, HIDDEN, HIDDEN, () -> table.getResultForClick(player)));

        playerSlotStart = inventorySlots.size();
        addNormalSlotsForPlayerInventory(0, 0);
        playerSlotEnd = inventorySlots.size();
        for (int i = playerSlotStart; i < playerSlotEnd; i++) {
            playerSlots.add((Slot) inventorySlots.get(i));
        }
    }

    public RequestTablePipe getTable() {
        return table;
    }

    /**
     * Moves the client-side slots into their current adaptive positions.
     */
    public void layout(RequestTableLayout layout, RequestTableView view, int storageScrollRow) {
        hide(itemStorageSlots);
        hide(fluidStorageSlots);
        if (view == RequestTableView.ITEM_STORAGE) {
            layoutStorageSlots(itemStorageSlots, layout, 9, storageScrollRow);
        } else if (view == RequestTableView.FLUID_STORAGE) {
            layoutStorageSlots(fluidStorageSlots, layout, 9, storageScrollRow);
        }
        layoutCrafting(layout);
        layoutPlayer(layout);
    }

    private void layoutStorageSlots(List<Slot> slots, RequestTableLayout layout, int columns, int scrollRow) {
        int left = layout.panelLeft + 4;
        int top = layout.panelTop + 3 - scrollRow * RequestTableLayout.SLOT;
        int storageTop = layout.panelTop + 3;
        int storageBottom = storageTop + layout.getVisibleStorageRows() * RequestTableLayout.SLOT;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            int x = left + (i % columns) * RequestTableLayout.SLOT;
            int y = top + (i / columns) * RequestTableLayout.SLOT;
            if (y < storageTop || y + RequestTableLayout.SLOT > storageBottom) {
                move(slot, HIDDEN, HIDDEN);
            } else {
                move(slot, layout, x, y);
            }
        }
    }

    private void layoutCrafting(RequestTableLayout layout) {
        for (int i = 0; i < craftingSlots.size(); i++) {
            int x = layout.craftingLeft + (i % 3) * RequestTableLayout.SLOT;
            int y = layout.craftingTop + 7 + (i / 3) * RequestTableLayout.SLOT;
            move(craftingSlots.get(i), layout, x, y);
        }
        move(resultSlot, layout, layout.craftingResultX, layout.craftingResultY);
    }

    private void layoutPlayer(RequestTableLayout layout) {
        for (int i = 0; i < playerSlots.size(); i++) {
            Slot slot = playerSlots.get(i);
            if (i < 27) {
                int x = layout.playerLeft + (i % 9) * RequestTableLayout.SLOT;
                int y = layout.playerTop + (i / 9) * RequestTableLayout.SLOT;
                move(slot, layout, x, y);
            } else {
                int hotbar = i - 27;
                move(slot, layout, layout.playerLeft + hotbar * RequestTableLayout.SLOT, layout.playerTop + 58);
            }
        }
    }

    @Override
    public ItemStack slotClick(int slotId, int mouseButton, int mode, EntityPlayer player) {
        if (slotId >= 0 && slotId < inventorySlots.size() && inventorySlots.get(slotId) == resultSlot) {
            if (mode == 1) {
                table.craftIntoPlayerInventory(player, SHIFT_CRAFT_LIMIT);
                return player.inventory.getItemStack();
            }
            if (mode == 0 && (mouseButton == 0 || mouseButton == 1)) {
                ItemStack cursor = player.inventory.getItemStack();
                ItemStack crafted = table.getResultForClick(player, cursor, 1);
                if (crafted != null) {
                    player.inventory.setItemStack(crafted);
                    return crafted;
                }
                return cursor;
            }
        }
        if (slotId >= 0 && slotId < inventorySlots.size()) {
            Slot slot = (Slot) inventorySlots.get(slotId);
            if (fluidStorageSlots.contains(slot) && mode == 0 && (mouseButton == 0 || mouseButton == 1)) {
                handleFluidStorageClick(slot.getSlotIndex(), player);
                return player.inventory.getItemStack();
            }
        }
        return super.slotClick(slotId, mouseButton, mode, player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= inventorySlots.size()) {
            return null;
        }
        Slot slot = (Slot) inventorySlots.get(slotIndex);
        if (slot == null || !slot.getHasStack()) {
            return null;
        }
        if (itemStorageSlots.contains(slot)) {
            return transferStackToRange(player, slot, playerSlotStart, playerSlotEnd, true);
        }
        if (slotIndex >= playerSlotStart && slotIndex < playerSlotEnd) {
            return transferPlayerStackToInternalStorage(player, slot);
        }
        return null;
    }

    private void handleFluidStorageClick(int fluidSlot, EntityPlayer player) {
        ItemStack cursor = player.inventory.getItemStack();
        if (cursor == null) {
            return;
        }
        if (cursor.stackSize > 1) {
            handleStackedFluidContainerClick(fluidSlot, player, cursor);
            return;
        }
        ItemStack result = getFluidStorageClickResult(fluidSlot, cursor);
        player.inventory.setItemStack(result);
        player.inventory.markDirty();
    }

    private void handleStackedFluidContainerClick(int fluidSlot, EntityPlayer player, ItemStack cursor) {
        ItemStack remainder = cursor.copy();
        remainder.stackSize--;
        if (!canAddToPlayerInventory(player, remainder)) {
            return;
        }
        ItemStack single = cursor.copy();
        single.stackSize = 1;
        ItemStack result = getFluidStorageClickResult(fluidSlot, single);
        if (result == single) {
            return;
        }
        player.inventory.setItemStack(result);
        if (!player.inventory.addItemStackToInventory(remainder) && remainder.stackSize > 0) {
            player.dropPlayerItemWithRandomChoice(remainder, false);
        }
        player.inventory.markDirty();
    }

    private ItemStack getFluidStorageClickResult(int fluidSlot, ItemStack cursor) {
        FluidStack stored = table.getFluidStorage().getFluid(fluidSlot);
        FluidStack held = getContainedFluid(cursor);
        ItemStack result = cursor;
        if (stored != null) {
            if (held == null) {
                result = fillContainerFromSlot(fluidSlot, cursor, stored);
            } else if (sameFluid(stored, held)) {
                result = isContainerFull(cursor, held) ? emptyContainerIntoSlot(fluidSlot, cursor, held)
                        : fillContainerFromSlot(fluidSlot, cursor, stored);
            }
        } else if (held != null) {
            result = emptyContainerIntoSlot(fluidSlot, cursor, held);
        }
        return result;
    }

    private ItemStack fillContainerFromSlot(int fluidSlot, ItemStack cursor, FluidStack stored) {
        if (cursor.getItem() instanceof IFluidContainerItem) {
            IFluidContainerItem container = (IFluidContainerItem) cursor.getItem();
            ItemStack filled = cursor.copy();
            int fillable = container.fill(filled, stored.copy(), false);
            if (fillable <= 0) {
                return cursor;
            }
            FluidStack drained = table.getFluidStorage().drain(fluidSlot, fillable, true);
            if (drained == null || drained.amount <= 0) {
                return cursor;
            }
            container.fill(filled, drained, true);
            return filled;
        }
        if (cursor.getItem() == LogisticsPipes.LogisticsFluidContainer) {
            FluidStack held = getContainedFluid(cursor);
            int room = table.getFluidStorage().getSlotCapacity() - (held == null ? 0 : held.amount);
            if (room <= 0) {
                return cursor;
            }
            FluidStack drained = table.getFluidStorage().drain(fluidSlot, room, true);
            if (drained == null || drained.amount <= 0) {
                return cursor;
            }
            if (held != null) {
                drained.amount += held.amount;
            }
            return SimpleServiceLocator.logisticsFluidManager.getFluidContainer(drained).makeNormalStack();
        }
        ItemStack filled = FluidContainerRegistry.fillFluidContainer(stored, cursor);
        FluidStack filledFluid = getContainedFluid(filled);
        if (filled == null || filledFluid == null
                || !sameFluid(stored, filledFluid)
                || filledFluid.amount > stored.amount) {
            return cursor;
        }
        table.getFluidStorage().drain(fluidSlot, filledFluid.amount, true);
        return filled;
    }

    private ItemStack emptyContainerIntoSlot(int fluidSlot, ItemStack cursor, FluidStack held) {
        if (cursor.getItem() instanceof IFluidContainerItem) {
            IFluidContainerItem container = (IFluidContainerItem) cursor.getItem();
            ItemStack drainedContainer = cursor.copy();
            int accepted = table.getFluidStorage().fillSlot(fluidSlot, held, false);
            if (accepted <= 0) {
                return cursor;
            }
            FluidStack drained = container.drain(drainedContainer, accepted, true);
            if (drained == null || drained.amount <= 0) {
                return cursor;
            }
            table.getFluidStorage().fillSlot(fluidSlot, drained, true);
            return drainedContainer;
        }
        if (cursor.getItem() == LogisticsPipes.LogisticsFluidContainer) {
            int accepted = table.getFluidStorage().fillSlot(fluidSlot, held, false);
            if (accepted <= 0) {
                return cursor;
            }
            FluidStack inserted = held.copy();
            inserted.amount = accepted;
            table.getFluidStorage().fillSlot(fluidSlot, inserted, true);
            int remaining = held.amount - accepted;
            if (remaining <= 0) {
                return new ItemStack(LogisticsPipes.LogisticsFluidContainer, 1);
            }
            FluidStack leftover = held.copy();
            leftover.amount = remaining;
            return SimpleServiceLocator.logisticsFluidManager.getFluidContainer(leftover).makeNormalStack();
        }
        int accepted = table.getFluidStorage().fillSlot(fluidSlot, held, false);
        if (accepted < held.amount) {
            return cursor;
        }
        ItemStack empty = FluidContainerRegistry.drainFluidContainer(cursor);
        if (empty == null) {
            return cursor;
        }
        table.getFluidStorage().fillSlot(fluidSlot, held, true);
        return empty;
    }

    private FluidStack getContainedFluid(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        if (stack.getItem() instanceof IFluidContainerItem) {
            return ((IFluidContainerItem) stack.getItem()).drain(stack.copy(), Integer.MAX_VALUE, false);
        }
        FluidStack fluid = FluidContainerRegistry.getFluidForFilledItem(stack);
        if (fluid != null) {
            return fluid;
        }
        return SimpleServiceLocator.logisticsFluidManager
                .getFluidFromContainer(ItemIdentifierStack.getFromStack(stack));
    }

    private boolean isContainerFull(ItemStack stack, FluidStack held) {
        if (stack.getItem() instanceof IFluidContainerItem) {
            return held.amount >= ((IFluidContainerItem) stack.getItem()).getCapacity(stack);
        }
        if (stack.getItem() == LogisticsPipes.LogisticsFluidContainer) {
            return held.amount >= table.getFluidStorage().getSlotCapacity();
        }
        return true;
    }

    private boolean sameFluid(FluidStack first, FluidStack second) {
        return first != null && second != null && FluidIdentifier.get(first).equals(FluidIdentifier.get(second));
    }

    private boolean canAddToPlayerInventory(EntityPlayer player, ItemStack stack) {
        int remaining = stack.stackSize;
        int stackLimit = Math.min(stack.getMaxStackSize(), player.inventory.getInventoryStackLimit());
        for (ItemStack inventoryStack : player.inventory.mainInventory) {
            if (inventoryStack == null) {
                remaining -= stackLimit;
            } else if (inventoryStack.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(inventoryStack, stack)) {
                remaining -= Math.max(0, stackLimit - inventoryStack.stackSize);
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private ItemStack transferPlayerStackToInternalStorage(EntityPlayer player, Slot sourceSlot) {
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

    private ItemStack transferStackToRange(EntityPlayer player, Slot sourceSlot, int start, int end, boolean reverse) {
        ItemStack source = sourceSlot.getStack();
        ItemStack original = source.copy();
        ItemStack moving = source.copy();
        if (!mergeItemStack(moving, start, end, reverse)) {
            return null;
        }
        int moved = source.stackSize - moving.stackSize;
        if (moved <= 0) {
            return null;
        }
        ItemStack removed = sourceSlot.decrStackSize(moved);
        sourceSlot.onPickupFromSlot(player, removed);
        sourceSlot.onSlotChanged();
        return original;
    }

    private void hide(List<Slot> slots) {
        for (Slot slot : slots) {
            move(slot, HIDDEN, HIDDEN);
        }
    }

    private void move(Slot slot, int x, int y) {
        slot.xDisplayPosition = x;
        slot.yDisplayPosition = y;
    }

    private void move(Slot slot, RequestTableLayout layout, int screenX, int screenY) {
        slot.xDisplayPosition = screenX - layout.guiLeft;
        slot.yDisplayPosition = screenY - layout.guiTop;
    }
}
