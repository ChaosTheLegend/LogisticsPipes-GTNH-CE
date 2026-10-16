/*
 * Copyright (c) Krapht, 2011 "LogisticsPipes" is distributed under the terms of the Minecraft Mod Public License 1.0,
 * or MMPL. Please check the contents of the license located in http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package logisticspipes.utils.item;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

import logisticspipes.LogisticsPipes;
import logisticspipes.interfaces.routing.ISaveState;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.ISimpleInventoryEventHandler;
import logisticspipes.utils.tuples.Pair;

public class SimpleStackInventory implements IInventory, ISaveState, Iterable<Pair<ItemStack, Integer>> {

    private ItemStack[] _contents;
    private final String _name;
    private int _stackLimit;

    private final LinkedList<ISimpleInventoryEventHandler> _listener = new LinkedList<>();

    public SimpleStackInventory(int size, String name, int stackLimit) {
        _contents = new ItemStack[size];
        _name = name;
        _stackLimit = stackLimit;
    }

    /**
     * Resizes this inventory while preserving all stacks that still fit into the new slot range.
     *
     * @param size new inventory size
     */
    public void setSizeInventory(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Inventory size cannot be negative");
        }
        if (_contents.length == size) {
            return;
        }
        _contents = Arrays.copyOf(_contents, size);
        markDirty();
    }

    /**
     * Changes the maximum amount accepted by a single slot.
     *
     * @param stackLimit new per-slot stack limit
     */
    public void setInventoryStackLimit(int stackLimit) {
        int newStackLimit = Math.max(1, stackLimit);
        if (_stackLimit == newStackLimit) {
            return;
        }
        _stackLimit = newStackLimit;
        markDirty();
    }

    @Override
    public int getSizeInventory() {
        return _contents.length;
    }

    @Override
    public ItemStack getStackInSlot(int i) {
        return _contents[i];
    }

    @Override
    public ItemStack decrStackSize(int slot, int count) {
        if (_contents[slot] == null) {
            return null;
        }
        if (_contents[slot].stackSize > count) {
            ItemStack ret = _contents[slot].copy();
            ret.stackSize = count;
            _contents[slot].stackSize -= count;
            return ret;
        }
        ItemStack ret = _contents[slot];
        _contents[slot] = null;
        return ret;
    }

    @Override
    public void setInventorySlotContents(int i, ItemStack itemstack) {
        if (itemstack != null) {
            _contents[i] = itemstack.copy();
        } else {
            _contents[i] = null;
        }
    }

    @Override
    public String getInventoryName() {
        return _name;
    }

    @Override
    public int getInventoryStackLimit() {
        return _stackLimit;
    }

    @Override
    public void markDirty() {
        for (ISimpleInventoryEventHandler handler : _listener) {
            handler.InventoryChanged(this);
        }
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer entityplayer) {
        return false;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        readFromNBT(nbttagcompound, "");
    }

    public void readFromNBT(NBTTagCompound nbttagcompound, String prefix) {
        NBTTagList nbttaglist = nbttagcompound.getTagList(prefix + "items", nbttagcompound.getId());
        int storedSize = nbttagcompound.getInteger(prefix + "itemsCount");
        if (storedSize > _contents.length) {
            setSizeInventory(storedSize);
        }

        for (int j = 0; j < nbttaglist.tagCount(); ++j) {
            NBTTagCompound nbttagcompound2 = nbttaglist.getCompoundTagAt(j);
            int index = nbttagcompound2.getInteger("index");
            if (index < _contents.length) {
                _contents[index] = ItemStack.loadItemStackFromNBT(nbttagcompound2);
                if (_contents[index] != null && nbttagcompound2.hasKey("lpStackSize")) {
                    _contents[index].stackSize = nbttagcompound2.getInteger("lpStackSize");
                }
            } else {
                LogisticsPipes.log.fatal(
                        "SimpleInventory: java.lang.ArrayIndexOutOfBoundsException: " + index
                                + " of "
                                + _contents.length);
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        writeToNBT(nbttagcompound, "");
    }

    public void writeToNBT(NBTTagCompound nbttagcompound, String prefix) {
        NBTTagList nbttaglist = new NBTTagList();
        for (int j = 0; j < _contents.length; ++j) {
            if (_contents[j] != null && _contents[j].stackSize > 0) {
                NBTTagCompound nbttagcompound2 = new NBTTagCompound();
                nbttaglist.appendTag(nbttagcompound2);
                nbttagcompound2.setInteger("index", j);
                _contents[j].writeToNBT(nbttagcompound2);
                // Vanilla's Count byte cannot persist compressed request-table storage stacks.
                nbttagcompound2.setInteger("lpStackSize", _contents[j].stackSize);
            }
        }
        nbttagcompound.setTag(prefix + "items", nbttaglist);
        nbttagcompound.setInteger(prefix + "itemsCount", _contents.length);
    }

    public void dropContents(World worldObj, int posX, int posY, int posZ) {
        if (MainProxy.isServer(worldObj)) {
            for (int i = 0; i < _contents.length; i++) {
                while (_contents[i] != null) {
                    ItemStack todrop = decrStackSize(i, _contents[i].getMaxStackSize());
                    dropItems(worldObj, todrop, posX, posY, posZ);
                }
            }
        }
    }

    private void dropItems(World world, ItemStack stack, int i, int j, int k) {
        ItemIdentifierInventory.dropItems(world, stack, i, j, k);
    }

    public void addListener(ISimpleInventoryEventHandler listner) {
        if (!_listener.contains(listner)) {
            _listener.add(listner);
        }
    }

    public void removeListener(ISimpleInventoryEventHandler listner) {
        _listener.remove(listner);
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int i) {
        if (_contents[i] == null) {
            return null;
        }
        ItemStack stackToTake = _contents[i];
        _contents[i] = null;
        return stackToTake;
    }

    private int tryAddToSlot(int i, ItemStack stack, int realstacklimit) {
        ItemStack slot = _contents[i];
        if (slot == null) {
            _contents[i] = stack.copy();
            _contents[i].stackSize = Math.min(_contents[i].stackSize, realstacklimit);
            return _contents[i].stackSize;
        }
        ItemIdentifier stackIdent = ItemIdentifier.get(stack);
        ItemIdentifier slotIdent = ItemIdentifier.get(slot);
        if (slotIdent != null && slotIdent.equals(stackIdent)) {
            slot.stackSize += stack.stackSize;
            if (slot.stackSize > realstacklimit) {
                int ans = stack.stackSize - (slot.stackSize - realstacklimit);
                slot.stackSize = realstacklimit;
                return ans;
            } else {
                return stack.stackSize;
            }
        }
        return 0;
    }

    public int addCompressed(ItemStack stack, boolean ignoreMaxStackSize) {
        if (stack == null) {
            return 0;
        }
        stack = stack.copy();

        ItemIdentifier stackIdent = ItemIdentifier.get(stack);
        int stacklimit = _stackLimit;
        if (!ignoreMaxStackSize) {
            if (stackIdent != null) {
                stacklimit = Math.min(stacklimit, stackIdent.getMaxStackSize());
            }
        }

        for (int i = 0; i < _contents.length; i++) {
            if (stack.stackSize <= 0) {
                break;
            }
            if (_contents[i] == null) {
                continue; // Skip Empty Slots on first attempt.
            }
            int added = tryAddToSlot(i, stack, stacklimit);
            stack.stackSize -= added;
        }
        for (int i = 0; i < _contents.length; i++) {
            if (stack.stackSize <= 0) {
                break;
            }
            int added = tryAddToSlot(i, stack, stacklimit);
            stack.stackSize -= added;
        }
        markDirty();
        return stack.stackSize;
    }

    @Override
    public boolean isItemValidForSlot(int i, ItemStack itemstack) {
        return true;
    }

    public void clearInventorySlotContents(int i) {
        _contents[i] = null;
    }

    @Override
    public boolean hasCustomInventoryName() {
        return true;
    }

    @Override
    public @NotNull Iterator<Pair<ItemStack, Integer>> iterator() {
        final Iterator<ItemStack> iter = Arrays.asList(_contents).iterator();
        return new Iterator<>() {

            int pos = -1;

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public Pair<ItemStack, Integer> next() {
                pos++;
                return new Pair<>(iter.next(), pos);
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
