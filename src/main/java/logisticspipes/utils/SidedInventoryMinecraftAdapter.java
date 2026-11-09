/*
 * Copyright (c) Krapht, 2011 "LogisticsPipes" is distributed under the terms of the Minecraft Mod Public License 1.0,
 * or MMPL. Please check the contents of the license located in http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package logisticspipes.utils;

import java.util.ArrayList;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.common.primitives.Ints;

/**
 * This class is responsible for abstracting an ISidedInventory as a normal IInventory
 *
 * @author Krapht
 */
public final class SidedInventoryMinecraftAdapter implements IInventory {

    public final ISidedInventory _sidedInventory;
    // the faces the inventory is seen through, in order; UNKNOWN stands for all six
    private final int[] _sides;
    private final boolean _forExtraction;
    private int[] _slotMapCache;

    public SidedInventoryMinecraftAdapter(ISidedInventory sidedInventory, ForgeDirection side, boolean forExtraction) {
        this(sidedInventory, new ForgeDirection[] { side }, forExtraction);
    }

    /**
     * The inventory seen through several faces: the slots of the first face, then the ones the next face adds. A slot
     * takes an item if one of the faces it is reached through lets it in.
     */
    public SidedInventoryMinecraftAdapter(ISidedInventory sidedInventory, ForgeDirection[] faces,
            boolean forExtraction) {
        _sidedInventory = sidedInventory;
        if (faces.length == 1 && faces[0] == ForgeDirection.UNKNOWN) {
            faces = ForgeDirection.VALID_DIRECTIONS;
        }
        _sides = new int[faces.length];
        for (int i = 0; i < faces.length; i++) {
            _sides[i] = faces[i].ordinal();
        }
        _forExtraction = forExtraction;
    }

    private int[] getSlotMap() {
        if (_slotMapCache == null) {
            ArrayList<Integer> list = new ArrayList<>();
            for (int side : _sides) {
                for (int number : _sidedInventory.getAccessibleSlotsFromSide(side)) {
                    ItemStack item = _sidedInventory.getStackInSlot(number);
                    if (!list.contains(number) && (!_forExtraction
                            || (item != null && _sidedInventory.canExtractItem(number, item, side)))) {
                        list.add(number);
                    }
                }
            }
            _slotMapCache = Ints.toArray(list);
        }
        return _slotMapCache;
    }

    @Override
    public int getSizeInventory() {
        return getSlotMap().length;
    }

    @Override
    public ItemStack getStackInSlot(int i) {
        return _sidedInventory.getStackInSlot(getSlotMap()[i]);
    }

    @Override
    public ItemStack decrStackSize(int i, int j) {
        return _sidedInventory.decrStackSize(getSlotMap()[i], j);
    }

    @Override
    public void setInventorySlotContents(int i, ItemStack itemstack) {
        _sidedInventory.setInventorySlotContents(getSlotMap()[i], itemstack);
    }

    @Override
    public String getInventoryName() {
        return _sidedInventory.getInventoryName();
    }

    @Override
    public int getInventoryStackLimit() {
        return _sidedInventory.getInventoryStackLimit();
    }

    @Override
    public void markDirty() {
        _sidedInventory.markDirty();
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer entityplayer) {
        return _sidedInventory.isUseableByPlayer(entityplayer);
    }

    @Override
    public void openInventory() {
        _sidedInventory.openInventory();
    }

    @Override
    public void closeInventory() {
        _sidedInventory.closeInventory();
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return _sidedInventory.getStackInSlotOnClosing(getSlotMap()[slot]);
    }

    @Override
    public boolean hasCustomInventoryName() {
        return _sidedInventory.hasCustomInventoryName();
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack itemstack) {
        int number = getSlotMap()[slot];
        if (!_sidedInventory.isItemValidForSlot(number, itemstack)) {
            return false;
        }
        for (int side : _sides) {
            if (Ints.contains(_sidedInventory.getAccessibleSlotsFromSide(side), number)
                    && _sidedInventory.canInsertItem(number, itemstack, side)) {
                return true;
            }
        }
        return false;
    }
}
