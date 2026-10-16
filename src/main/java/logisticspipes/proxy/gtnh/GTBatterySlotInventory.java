package logisticspipes.proxy.gtnh;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicBatteryBuffer;
import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import gregtech.api.util.GTModHandler;
import ic2.api.item.IElectricItem;
import logisticspipes.interfaces.IModuleInventory;
import logisticspipes.utils.item.ItemIdentifier;

/**
 * The battery slots of a GT machine, for the electric manager module.
 * <p>
 * GT's sided inventory never lets items into or out of battery slots (battery buffers only let a few single-use
 * batteries out), so this view reads and writes those slots directly. The module decides which batteries are done
 * charging or discharging. Only the electric manager uses it: other modules keep GT's normal rules.
 * <ul>
 * <li>Battery buffers, chargers and Tesla coils: every slot. Insertion follows GT's own rule, one battery of the
 * buffer's exact tier per empty slot.</li>
 * <li>Electric single-block machines (benders, wiremills, chemical reactors, ...): the battery slot. Steam machines
 * have none.</li>
 * <li>Other GT tiles: the slots GT is charging or discharging right now (battery hatches, solar panels, ...).</li>
 * </ul>
 * Outside battery buffers, a slot only takes a battery the machine can charge or drain (tier up to the machine's).
 */
public class GTBatterySlotInventory implements IModuleInventory {

    private final IGregTechTileEntity tile;
    private final MetaTileEntity machine;
    private final int[] slots;
    private final int side;

    private GTBatterySlotInventory(IGregTechTileEntity tile, MetaTileEntity machine, int[] slots, ForgeDirection side) {
        this.tile = tile;
        this.machine = machine;
        this.slots = slots;
        this.side = side.ordinal();
    }

    /**
     * @return the battery slots of a GT tile, or null if it has none
     */
    public static GTBatterySlotInventory of(IGregTechTileEntity tile, ForgeDirection side) {
        IMetaTileEntity meta = tile.getMetaTileEntity();
        if (!(meta instanceof MetaTileEntity machine)) {
            return null;
        }
        int[] slots = batterySlots(machine);
        return slots.length == 0 ? null : new GTBatterySlotInventory(tile, machine, slots, side);
    }

    private static int[] batterySlots(MetaTileEntity machine) {
        int size = machine.getSizeInventory();
        if (machine instanceof MTEBasicBatteryBuffer) {
            return range(0, size, size);
        }
        if (machine instanceof MTEBasicMachine) {
            // GT only reports the slot while it is charging or discharging, but it is always the battery slot
            return machine.isSteampowered() ? new int[0] : range(machine.rechargerSlotStartIndex(), 1, size);
        }
        Set<Integer> found = new LinkedHashSet<>();
        addRange(found, machine.rechargerSlotStartIndex(), machine.rechargerSlotCount(), size);
        addRange(found, machine.dechargerSlotStartIndex(), machine.dechargerSlotCount(), size);
        return found.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int[] range(int start, int count, int size) {
        Set<Integer> found = new LinkedHashSet<>();
        addRange(found, start, count, size);
        return found.stream().mapToInt(Integer::intValue).toArray();
    }

    private static void addRange(Set<Integer> found, int start, int count, int size) {
        for (int i = Math.max(0, start); i < start + count && i < size; i++) {
            found.add(i);
        }
    }

    private boolean accepts(int slot, ItemStack stack) {
        if (machine instanceof MTEBasicBatteryBuffer) {
            return tile.canInsertItem(slot, stack, side);
        }
        if (!GTModHandler.isElectricItem(stack)) {
            return false;
        }
        int tier = ((IElectricItem) stack.getItem()).getTier(stack);
        return tier <= Math.max(machine.getInputTier(), machine.getOutputTier());
    }

    private int slotLimit(ItemStack stack) {
        return Math.min(stack.getMaxStackSize(), tile.getInventoryStackLimit());
    }

    @Override
    public ItemStack add(ItemStack stack, ForgeDirection orientation, boolean doAdd) {
        ItemStack added = stack.copy();
        added.stackSize = 0;
        for (int slot : slots) {
            int left = stack.stackSize - added.stackSize;
            if (left <= 0) {
                break;
            }
            if (tile.getStackInSlot(slot) != null || !accepts(slot, stack)) {
                continue;
            }
            int amount = Math.min(left, slotLimit(stack));
            if (doAdd) {
                ItemStack toSlot = stack.copy();
                toSlot.stackSize = amount;
                tile.setInventorySlotContents(slot, toSlot);
            }
            added.stackSize += amount;
        }
        return added;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < getSizeInventory(); i++) {
            if (getStackInSlot(i) != null) return false;
        }
        return true;
    }

    @Override
    public int itemCount(ItemIdentifier item) {
        int count = 0;
        for (int i = 0; i < getSizeInventory(); i++) {
            ItemStack stack = getStackInSlot(i);
            if (stack != null && ItemIdentifier.get(stack).equals(item)) {
                count += stack.stackSize;
            }
        }
        return count;
    }

    @Override
    public Map<ItemIdentifier, Integer> getItemsAndCount() {
        Map<ItemIdentifier, Integer> items = new HashMap<>();
        for (int i = 0; i < getSizeInventory(); i++) {
            ItemStack stack = getStackInSlot(i);
            if (stack != null) {
                items.merge(ItemIdentifier.get(stack), stack.stackSize, Integer::sum);
            }
        }
        return items;
    }

    @Override
    public ItemStack getSingleItem(ItemIdentifier item) {
        return getMultipleItems(item, 1);
    }

    @Override
    public ItemStack getMultipleItems(ItemIdentifier item, int count) {
        if (itemCount(item) < count) {
            return null;
        }
        ItemStack result = null;
        for (int i = 0; i < getSizeInventory() && count > 0; i++) {
            ItemStack stack = getStackInSlot(i);
            if (stack == null || !ItemIdentifier.get(stack).equals(item)) {
                continue;
            }
            ItemStack taken = decrStackSize(i, count);
            if (taken == null) {
                continue;
            }
            count -= taken.stackSize;
            if (result == null) {
                result = taken;
            } else {
                result.stackSize += taken.stackSize;
            }
        }
        return result;
    }

    @Override
    public boolean containsUndamagedItem(ItemIdentifier item) {
        for (int i = 0; i < getSizeInventory(); i++) {
            ItemStack stack = getStackInSlot(i);
            if (stack != null && ItemIdentifier.get(stack).getUndamaged().equals(item)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int roomForItem(ItemIdentifier item) {
        return roomForItem(item, Integer.MAX_VALUE);
    }

    @Override
    public int roomForItem(ItemIdentifier item, int count) {
        ItemStack probe = item.unsafeMakeNormalStack(1);
        int room = 0;
        for (int slot : slots) {
            if (room >= count) {
                break;
            }
            if (tile.getStackInSlot(slot) == null && accepts(slot, probe)) {
                room += slotLimit(probe);
            }
        }
        return room;
    }

    @Override
    public boolean isSpecialInventory() {
        return false;
    }

    @Override
    public Set<ItemIdentifier> getItems() {
        return new TreeSet<>(getItemsAndCount().keySet());
    }

    @Override
    public int getSizeInventory() {
        return slots.length;
    }

    @Override
    public ItemStack getStackInSlot(int i) {
        ItemStack stack = tile.getStackInSlot(slots[i]);
        return stack == null || stack.stackSize <= 0 ? null : stack;
    }

    @Override
    public ItemStack decrStackSize(int i, int j) {
        return tile.decrStackSize(slots[i], j);
    }
}
