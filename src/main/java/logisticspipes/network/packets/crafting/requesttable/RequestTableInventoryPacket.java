package logisticspipes.network.packets.crafting.requesttable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import logisticspipes.crafting.requesttable.RequestTableContainer;
import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.PacketGuards;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;

/** Synchronizes the open table's layout, items, actual fluids, and cursor using integer amounts. */
public class RequestTableInventoryPacket extends CoordinatesPacket {

    private int windowId;
    private int itemSlots;
    private int itemStackLimit;
    private int fluidSlots;
    private int fluidSlotCapacity;
    private int craftableAmount;
    private int itemSlotTier;
    private int itemSizeTier;
    private int fluidSlotTier;
    private int fluidSizeTier;
    private boolean fluidController;
    private boolean monitoring;
    private List<ItemStack> contents = new ArrayList<>();
    private List<FluidStack> fluids = new ArrayList<>();
    private ItemStack cursor;

    public RequestTableInventoryPacket(int id) {
        super(id);
    }

    public RequestTableInventoryPacket setInventory(RequestTableContainer container, EntityPlayer player) {
        RequestTablePipe table = container.getTable();
        windowId = container.windowId;
        itemSlots = table.inv.getSizeInventory();
        itemStackLimit = table.inv.getInventoryStackLimit();
        fluidSlots = table.getFluidStorage().getSizeInventory();
        fluidSlotCapacity = table.getFluidStorage().getSlotCapacity();
        craftableAmount = container.getCraftableAmount();
        itemSlotTier = table.getItemSlotTier();
        itemSizeTier = table.getItemSizeTier();
        fluidSlotTier = table.getFluidSlotTier();
        fluidSizeTier = table.getFluidSizeTier();
        fluidController = table.isFluidEnabled();
        monitoring = table.hasMonitoringUpgrade();
        contents = new ArrayList<>();
        for (Object entry : container.inventorySlots) {
            Slot slot = (Slot) entry;
            ItemStack stack = slot.inventory == table.getFluidStorage() ? null : slot.getStack();
            contents.add(stack == null ? null : stack.copy());
        }
        fluids = new ArrayList<>();
        for (int slot = 0; slot < fluidSlots; slot++) {
            fluids.add(table.getFluidStorage().getFluid(slot));
        }
        ItemStack held = player.inventory.getItemStack();
        cursor = held == null ? null : held.copy();
        setTilePos(table.container);
        return this;
    }

    @Override
    public ModernPacket template() {
        return new RequestTableInventoryPacket(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        if (!PacketGuards.isOnClient(player) || !(player.openContainer instanceof RequestTableContainer container)
                || container.windowId != windowId) {
            return;
        }
        RequestTablePipe table = container.getTable();
        if (table.getX() != getPosX() || table.getY() != getPosY() || table.getZ() != getPosZ()) {
            return;
        }
        if (fluids.size() != fluidSlots) {
            return;
        }
        table.applyItemUpgradeTiers(itemSlotTier, itemSizeTier);
        table.applyFluidUpgradeTiers(fluidSlotTier, fluidSizeTier);
        table.applySpecialUpgrades(fluidController, monitoring);
        container.applyInventory(itemSlots, itemStackLimit, fluidSlotCapacity, craftableAmount, contents, fluids);
        player.inventory.setItemStack(cursor);
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        super.writeData(data);
        data.writeInt(windowId);
        data.writeInt(itemSlots);
        data.writeInt(itemStackLimit);
        data.writeInt(fluidSlots);
        data.writeInt(fluidSlotCapacity);
        data.writeInt(craftableAmount);
        data.writeInt(itemSlotTier);
        data.writeInt(itemSizeTier);
        data.writeInt(fluidSlotTier);
        data.writeInt(fluidSizeTier);
        data.writeBoolean(fluidController);
        data.writeBoolean(monitoring);
        data.writeList(contents, LPDataOutputStream::writeItemStack);
        data.writeList(fluids, LPDataOutputStream::writeFluidStack);
        data.writeItemStack(cursor);
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        super.readData(data);
        windowId = data.readInt();
        itemSlots = data.readInt();
        itemStackLimit = data.readInt();
        fluidSlots = data.readInt();
        fluidSlotCapacity = data.readInt();
        craftableAmount = data.readInt();
        itemSlotTier = data.readInt();
        itemSizeTier = data.readInt();
        fluidSlotTier = data.readInt();
        fluidSizeTier = data.readInt();
        fluidController = data.readBoolean();
        monitoring = data.readBoolean();
        contents = data.readList(LPDataInputStream::readItemStack);
        fluids = data.readList(LPDataInputStream::readFluidStack);
        cursor = data.readItemStack();
    }
}
