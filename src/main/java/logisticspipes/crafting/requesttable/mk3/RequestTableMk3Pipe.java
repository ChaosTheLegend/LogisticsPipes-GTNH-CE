package logisticspipes.crafting.requesttable.mk3;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import logisticspipes.LogisticsPipes;
import logisticspipes.api.IMUICompatiblePipeV2;
import logisticspipes.blocks.crafting.AutoCraftingInventory;
import logisticspipes.compat.ModularUIHelper;
import logisticspipes.config.Configs;
import logisticspipes.crafting.requesttable.RequestTableFluidStorage;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.requesttable.RequestTableMui;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.items.ItemUpgrade;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.logisticspipes.TransportLayer;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.crafting.requesttable.RequestTableSetCursorPacket;
import logisticspipes.pipes.PipeBlockRequestTable;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.RequestHandler;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierInventory;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.item.SimpleStackInventory;
import logisticspipes.utils.tuples.Pair;
import lombok.Getter;

/**
 * New request table implementation with separate item and fluid storage.
 * <p>
 * The class intentionally lives beside the old request table instead of replacing it. It inherits the existing request
 * table block behaviour and recipe cache fields, while overriding the GUI target, arrival handling and result-click
 * crafting semantics required by the redesigned table.
 */
@Getter
public class RequestTableMk3Pipe extends PipeBlockRequestTable implements IRequestFluid, IMUICompatiblePipeV2 {

    private static final int BASE_ITEM_SLOTS = 27;
    private static final int ITEM_SLOT_UPGRADE_SIZE = 9;
    private static final int BASE_ITEM_STACK_LIMIT = 64;
    private static final int ITEM_STACK_UPGRADE_SIZE = 64;
    private static final int BASE_FLUID_SLOTS = 9;
    private static final int FLUID_SLOT_UPGRADE_SIZE = 9;
    private static final int FLUID_SEND_CHUNK = 5000;
    private static final String NBT_FLUID_STORAGE = "requestTableMk3FluidStorage";
    private static final String NBT_DISPLAY_SETTINGS = "requestTableMk3DisplaySettings";
    private final RequestTableFluidStorage fluidStorage = RequestTableFluidStorage.createDefault();
    private final RequestTableMk3DisplaySettingsStore displaySettingsStore = new RequestTableMk3DisplaySettingsStore();
    private int contentRevision;
    private static final int NETWORK_REFRESH_TICKS = 300;
    private long networkRefreshUntil;
    /**
     * The items lying in the crafting grid, like in a workbench. {@link #matrix} is the recipe the grid remembers: it
     * stays when the items are used up, until another recipe is laid out or the grid is cleared.
     */
    public final SimpleStackInventory craftGrid = new SimpleStackInventory(9, "Crafting Grid", 64);
    private static final String NBT_CRAFT_GRID = "requestTableMk3CraftGrid";
    // the players that have the ModularUI GUI of this table open
    private final Map<UUID, EntityPlayer> guiViewers = new HashMap<>();
    // the size of the storage changed while a GUI was open
    private boolean storageResized;

    /**
     * Creates the new request table pipe item.
     *
     * @param item backing pipe item
     */
    public RequestTableMk3Pipe(Item item) {
        super(item);
        updateStorageUpgrades();
    }

    @Override
    public void openGui(EntityPlayer player) {
        updateStorageUpgrades();
        if (container != null && MainProxy.isServer(getWorld())) {
            // both sides build the slots of the storage, the client has to know its size before the window opens
            MainProxy.sendPacketToPlayer(container.getLPDescriptionPacket(), player);
        }
        ModularUIHelper.openPipeUI(player, this);
    }

    /**
     * The size of the storage goes to the client with the state of the pipe: the client doesn't know the upgrades.
     */
    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        super.writeData(data);
        updateStorageUpgrades();
        data.writeInt(inv.getSizeInventory());
        data.writeInt(inv.getInventoryStackLimit());
        data.writeInt(fluidStorage.getSizeInventory());
        data.writeInt(fluidStorage.getSlotCapacity());
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        super.readData(data);
        int itemSlotCount = data.readInt();
        int itemStackLimit = data.readInt();
        int fluidSlotCount = data.readInt();
        int fluidSlotCapacity = data.readInt();
        // only the sizes: what the storage holds comes with the slots of the open window
        inv.setSizeInventory(itemSlotCount);
        inv.setInventoryStackLimit(itemStackLimit);
        fluidStorage.resize(fluidSlotCount, fluidSlotCapacity, null, 0, 0, 0);
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        return new RequestTableMui(this);
    }

    /**
     * Something left for the network. It shows up in the network list only once it arrived there, so the list is sent
     * again for a while.
     */
    private void expectNetworkChange() {
        if (getWorld() != null) {
            networkRefreshUntil = getWorld().getTotalWorldTime() + NETWORK_REFRESH_TICKS;
        }
    }

    /**
     * @return the world time until which the network list should be sent again from time to time
     */
    public long getNetworkRefreshUntil() {
        return networkRefreshUntil;
    }

    public void addGuiViewer(EntityPlayer player) {
        guiViewers.put(player.getUniqueID(), player);
    }

    public void removeGuiViewer(EntityPlayer player) {
        guiViewers.remove(player.getUniqueID());
    }

    /**
     * Sends as much of the stack into the network as takes it, by default routing and sinks.
     *
     * @return the amount that was sent
     */
    public int sendStackToNetwork(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0 || MainProxy.isClient(getWorld())) {
            return 0;
        }
        int sent = 0;
        while (sent < stack.stackSize) {
            ItemStack toSend = stack.copy();
            toSend.stackSize = Math.min(stack.stackSize - sent, stack.getMaxStackSize());
            IRoutedItem routed = SimpleServiceLocator.routedItemHelper.createNewTravelItem(toSend);
            SimpleServiceLocator.logisticsManager.assignDestinationFor(routed, getRouter().getSimpleID(), false);
            if (routed.getDestinationUUID() == null) {
                break;
            }
            ForgeDirection dir = getRouteLayer().getOrientationForItem(routed, null);
            queueRoutedItem(routed, dir.getOpposite());
            sent += toSend.stackSize;
        }
        if (sent > 0) {
            expectNetworkChange();
        }
        return sent;
    }

    /**
     * Sends the item on the cursor of the player into the network: all of it, or one with the right mouse button.
     *
     * @return true if something was sent
     */
    public boolean sendCursorItemToNetwork(EntityPlayer player, int mouseButton) {
        ItemStack cursor = player.inventory.getItemStack();
        if (cursor == null) {
            return false;
        }
        ItemStack toSend = cursor.copy();
        if (mouseButton == 1) {
            toSend.stackSize = 1;
        }
        int sent = sendStackToNetwork(toSend);
        if (sent <= 0) {
            return false;
        }
        cursor.stackSize -= sent;
        player.inventory.setItemStack(cursor.stackSize <= 0 ? null : cursor);
        player.inventory.markDirty();
        syncCursor(player);
        markContentChanged();
        return true;
    }

    /**
     * Gives an arrived stack to the player who requested it, if that player still has the GUI of the table open. The
     * stack keeps what the player had no room for.
     */
    private void deliverToRequester(IRoutedItem item, ItemStack stack) {
        if (item.getInfo() == null || !(item.getInfo().targetInfo instanceof RequestTableMk3PlayerTarget target)) {
            return;
        }
        EntityPlayer player = guiViewers.get(target.getPlayer());
        if (player == null || !player.isEntityAlive()
                || player.openContainer == null
                || player.openContainer == player.inventoryContainer) {
            return;
        }
        player.inventory.addItemStackToInventory(stack);
        player.inventory.markDirty();
        player.openContainer.detectAndSendChanges();
    }

    /**
     * @return a number that changes whenever the content shown in the network list may have changed
     */
    public int getContentRevision() {
        return contentRevision;
    }

    /**
     * Reports that what the table holds has changed: the open GUIs send the network list again and the table is saved.
     */
    public void markContentChanged() {
        contentRevision++;
        if (container != null) {
            container.markDirty();
        }
    }

    /**
     * Returns the saved view state for this player at this table.
     */
    public RequestTableMk3DisplaySettings getDisplaySettings(EntityPlayer player) {
        return displaySettingsStore.get(player.getUniqueID());
    }

    /**
     * Saves the view state for this player at this table.
     */
    public void setDisplaySettings(EntityPlayer player, RequestTableMk3DisplaySettings settings) {
        if (displaySettingsStore.set(player.getUniqueID(), settings) && container != null) {
            container.markDirty();
        }
    }

    /**
     * Applies installed request-table storage upgrades to the item and fluid backing stores.
     */
    public void updateStorageUpgrades() {
        if (container == null) return;
        // the client takes the size the server works out, see readData
        if (getWorld() != null && getWorld().isRemote) return;

        int itemSlotCount = BASE_ITEM_SLOTS
                + countUpgrade(ItemUpgrade.REQUEST_TABLE_ITEM_INVENTORY) * ITEM_SLOT_UPGRADE_SIZE;
        int itemStackLimit = BASE_ITEM_STACK_LIMIT
                + countUpgrade(ItemUpgrade.REQUEST_TABLE_ITEM_STACK) * ITEM_STACK_UPGRADE_SIZE;
        adjustItemStorageForUpgrades(itemSlotCount, itemStackLimit);

        int fluidSlotCount = BASE_FLUID_SLOTS
                + countUpgrade(ItemUpgrade.REQUEST_TABLE_FLUID_INVENTORY) * FLUID_SLOT_UPGRADE_SIZE;
        int fluidSlotCapacity = Configs.MAX_LOGISTICS_FLUID_TRANSPORT_INNER_CAPACITY
                * (1 + countUpgrade(ItemUpgrade.REQUEST_TABLE_FLUID_CAPACITY));
        boolean fluidStorageChanged = fluidStorage.getSizeInventory() != fluidSlotCount
                || fluidStorage.getSlotCapacity() != fluidSlotCapacity;
        if (getWorld() != null)
            fluidStorage.resize(fluidSlotCount, fluidSlotCapacity, getWorld(), getX(), getY(), getZ());
        if (fluidStorageChanged) {
            markContentChanged();
            container.sendUpdateToClient();
            storageResized |= !guiViewers.isEmpty();
        }
    }

    /**
     * A window that is open was built for the old size of the storage, its slots don't fit the new one. It is closed
     * and opened again by the player. Not at once: the size may change while that window is being worked on.
     */
    @Override
    public void ignoreDisableUpdateEntity() {
        super.ignoreDisableUpdateEntity();
        if (!storageResized || MainProxy.isClient(getWorld())) {
            return;
        }
        storageResized = false;
        for (EntityPlayer player : new ArrayList<>(guiViewers.values())) {
            if (player instanceof EntityPlayerMP) {
                ((EntityPlayerMP) player).closeScreen();
            }
        }
    }

    private int countUpgrade(int itemDamage) {
        int count = 0;
        IInventory upgrades = getOriginalUpgradeManager().getInv();
        for (int slot = 0; slot < upgrades.getSizeInventory(); slot++) {
            ItemStack stack = upgrades.getStackInSlot(slot);
            if (stack != null && stack.getItem() == LogisticsPipes.UpgradeItem && stack.getItemDamage() == itemDamage) {
                count += stack.stackSize;
            }
        }
        return count;
    }

    private void adjustItemStorageForUpgrades(int slotCount, int stackLimit) {
        if (!needsItemStorageAdjustment(slotCount, stackLimit)) {
            return;
        }
        List<ItemStack> contents = collectItemStorageContents();
        inv.setSizeInventory(slotCount);
        inv.setInventoryStackLimit(stackLimit);
        for (int slot = 0; slot < inv.getSizeInventory(); slot++) {
            inv.clearInventorySlotContents(slot);
        }
        for (ItemStack stack : contents) {
            stack.stackSize = addToStorage(stack);
            if (stack.stackSize > 0) {
                dropItemStack(stack);
            }
        }
        inv.markDirty();
        markContentChanged();
        if (container != null && MainProxy.isServer(getWorld())) {
            container.sendUpdateToClient();
            storageResized |= !guiViewers.isEmpty();
        }
    }

    private boolean needsItemStorageAdjustment(int slotCount, int stackLimit) {
        if (inv.getSizeInventory() != slotCount || inv.getInventoryStackLimit() != stackLimit) {
            return true;
        }
        for (Pair<ItemStack, Integer> entry : inv) {
            ItemStack stack = entry.getValue1();
            if (stack != null && stack.stackSize > stackLimit) {
                return true;
            }
        }
        return false;
    }

    private List<ItemStack> collectItemStorageContents() {
        List<ItemStack> contents = new ArrayList<>();
        for (Pair<ItemStack, Integer> entry : inv) {
            ItemStack stack = entry.getValue1();
            if (stack != null && stack.stackSize > 0) {
                contents.add(stack.copy());
            }
        }
        return contents;
    }

    private void dropItemStack(ItemStack stack) {
        if (getWorld() != null) {
            ItemIdentifierInventory.dropItems(getWorld(), stack, getX(), getY(), getZ());
        }
    }

    /**
     * @return item storage fill level from {@code 0.0F} to {@code 1.0F}
     */
    public float getItemStorageFillLevel() {
        updateStorageUpgrades();
        int stored = 0;
        for (Pair<ItemStack, Integer> entry : inv) {
            if (entry.getValue1() != null) {
                stored += entry.getValue1().stackSize;
            }
        }
        return Math.min(1.0F, stored / (float) (inv.getSizeInventory() * inv.getInventoryStackLimit()));
    }

    /**
     * @return fluid storage fill level from {@code 0.0F} to {@code 1.0F}
     */
    public float getFluidStorageFillLevel() {
        updateStorageUpgrades();
        if (fluidStorage.getTotalCapacity() <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, fluidStorage.getStoredAmount() / (float) fluidStorage.getTotalCapacity());
    }

    /**
     * Counts matching stacks in the internal item storage.
     *
     * @param item item identifier to count
     * @return amount currently held internally
     */
    public int getStoredItemAmount(ItemIdentifier item) {
        updateStorageUpgrades();
        int amount = 0;
        for (Pair<ItemStack, Integer> entry : inv) {
            ItemStack stack = entry.getValue1();
            if (stack != null && ItemIdentifier.get(stack).equals(item)) {
                amount += stack.stackSize;
            }
        }
        return amount;
    }

    /**
     * Counts matching fluid in the internal fluid storage.
     *
     * @param fluidContainer logistics fluid-container identifier
     * @return amount currently held internally in millibuckets
     */
    public int getStoredFluidAmount(ItemIdentifier fluidContainer) {
        updateStorageUpgrades();
        FluidStack requested = SimpleServiceLocator.logisticsFluidManager
                .getFluidFromContainer(new ItemIdentifierStack(fluidContainer, 1));
        if (requested == null) {
            return 0;
        }
        FluidIdentifier requestedFluid = FluidIdentifier.get(requested);
        int amount = 0;
        for (int slot = 0; slot < fluidStorage.getSizeInventory(); slot++) {
            FluidStack stored = fluidStorage.getFluid(slot);
            if (stored != null && requestedFluid.equals(FluidIdentifier.get(stored))) {
                amount += stored.amount;
            }
        }
        return amount;
    }

    /**
     * Handles a click on a network-grid entry when the click should manipulate internal storage instead of opening a
     * request dialog.
     *
     * @param player      player using the GUI
     * @param stack       clicked entry stack
     * @param fluid       whether the entry represents a fluid
     * @param mouseButton mouse button sent by the GUI
     * @param shift       whether shift was held
     */
    public void handleNetworkEntryInteraction(EntityPlayer player, ItemIdentifierStack stack, boolean fluid,
            int mouseButton, boolean shift) {
        if (stack == null || mouseButton < 0 || mouseButton > 1) {
            return;
        }
        updateStorageUpgrades();
        boolean changed = fluid ? handleNetworkFluidInteraction(player, stack.getItem(), mouseButton)
                : handleNetworkItemInteraction(player, stack.getItem(), mouseButton, shift);
        if (!changed) {
            return;
        }
        inv.markDirty();
        fluidStorage.markDirty();
        player.inventory.markDirty();
        if (player.openContainer != null) {
            player.openContainer.detectAndSendChanges();
        }
        syncCursor(player);
        markContentChanged();
    }

    private boolean handleNetworkItemInteraction(EntityPlayer player, ItemIdentifier item, int mouseButton,
            boolean shift) {
        ItemStack cursor = player.inventory.getItemStack();
        if (cursor != null) {
            return insertCursorItemIntoStorage(player, item, mouseButton);
        }
        int maxStack = item.getMaxStackSize();
        if (shift && mouseButton == 0) {
            return moveInternalItemToPlayerInventory(player, item, Math.min(maxStack, getStoredItemAmount(item)));
        }
        int targetAmount;
        if (shift && mouseButton == 1) {
            targetAmount = 1;
        } else if (mouseButton == 1) {
            targetAmount = Math.min((getStoredItemAmount(item) + 1) / 2, Math.max(1, maxStack / 2));
        } else {
            targetAmount = Math.min(getStoredItemAmount(item), maxStack);
        }
        return moveInternalItemToCursor(player, item, targetAmount);
    }

    private boolean insertCursorItemIntoStorage(EntityPlayer player, ItemIdentifier item, int mouseButton) {
        ItemStack cursor = player.inventory.getItemStack();
        if (cursor == null || !ItemIdentifier.get(cursor).equals(item)) {
            return false;
        }
        int toMove = mouseButton == 1 ? 1 : cursor.stackSize;
        ItemStack moving = cursor.copy();
        moving.stackSize = toMove;
        int remaining = addToStorage(moving);
        int moved = toMove - remaining;
        if (moved <= 0) {
            return false;
        }
        cursor.stackSize -= moved;
        player.inventory.setItemStack(cursor.stackSize <= 0 ? null : cursor);
        return true;
    }

    private boolean moveInternalItemToCursor(EntityPlayer player, ItemIdentifier item, int amount) {
        if (amount <= 0) {
            return false;
        }
        ItemStack cursor = player.inventory.getItemStack();
        int room;
        if (cursor == null) {
            room = item.getMaxStackSize();
        } else if (ItemIdentifier.get(cursor).equals(item)) {
            room = cursor.getMaxStackSize() - cursor.stackSize;
        } else {
            return false;
        }
        int toTake = Math.min(amount, room);
        if (toTake <= 0) {
            return false;
        }
        ItemStack taken = removeInternalItem(item, toTake);
        if (taken == null || taken.stackSize <= 0) {
            return false;
        }
        if (cursor == null) {
            player.inventory.setItemStack(taken);
        } else {
            cursor.stackSize += taken.stackSize;
            player.inventory.setItemStack(cursor);
        }
        return true;
    }

    private boolean moveInternalItemToPlayerInventory(EntityPlayer player, ItemIdentifier item, int amount) {
        if (amount <= 0) {
            return false;
        }
        ItemStack sample = item.makeStack(1).makeNormalStack();
        int toTake = Math.min(amount, getPlayerInventoryRoom(player, sample));
        if (toTake <= 0) {
            return false;
        }
        ItemStack taken = removeInternalItem(item, toTake);
        if (taken == null || taken.stackSize <= 0) {
            return false;
        }
        ItemStack remaining = taken.copy();
        if (player.inventory.addItemStackToInventory(remaining) || remaining.stackSize <= 0) {
            return true;
        }
        remaining.stackSize = addToStorage(remaining);
        return remaining.stackSize < taken.stackSize;
    }

    private ItemStack removeInternalItem(ItemIdentifier item, int amount) {
        int remaining = amount;
        ItemStack removed = null;
        for (int slot = 0; slot < inv.getSizeInventory() && remaining > 0; slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (stack == null || !ItemIdentifier.get(stack).equals(item)) {
                continue;
            }
            int toRemove = Math.min(remaining, stack.stackSize);
            ItemStack part = inv.decrStackSize(slot, toRemove);
            if (part == null) {
                continue;
            }
            if (removed == null) {
                removed = part.copy();
            } else {
                removed.stackSize += part.stackSize;
            }
            remaining -= part.stackSize;
        }
        return removed;
    }

    private boolean handleNetworkFluidInteraction(EntityPlayer player, ItemIdentifier fluidContainer, int mouseButton) {
        FluidStack target = SimpleServiceLocator.logisticsFluidManager
                .getFluidFromContainer(new ItemIdentifierStack(fluidContainer, 1));
        ItemStack cursor = player.inventory.getItemStack();
        if (target == null || cursor == null) {
            return false;
        }
        if (cursor.stackSize > 1) {
            // a left click works on the whole stack, a right click on one container of it
            return convertHeldFluidContainers(player, target, cursor, mouseButton == 0 ? cursor.stackSize : 1);
        }
        ItemStack result = getNetworkFluidClickResult(target, cursor);
        if (result == cursor) {
            return false;
        }
        player.inventory.setItemStack(result);
        return true;
    }

    /**
     * Fills or empties up to the given number of containers of the stack on the cursor. If all of the stack was worked
     * on, the result stays on the cursor. Otherwise the untouched containers stay there and the others go into the
     * inventory of the player. Nothing happens if they have no room there.
     */
    private boolean convertHeldFluidContainers(EntityPlayer player, FluidStack target, ItemStack cursor, int count) {
        NBTTagCompound before = new NBTTagCompound();
        fluidStorage.writeToNBT(before, "");
        List<ItemStack> converted = new ArrayList<>();
        int done = 0;
        while (done < count) {
            ItemStack single = cursor.copy();
            single.stackSize = 1;
            ItemStack result = getNetworkFluidClickResult(target, single);
            if (result == single) {
                break;
            }
            mergeInto(converted, result);
            done++;
        }
        if (done == 0) {
            return false;
        }
        ItemStack newCursor;
        if (done < cursor.stackSize) {
            newCursor = cursor.copy();
            newCursor.stackSize -= done;
        } else {
            newCursor = converted.isEmpty() ? null : converted.remove(0);
        }
        ItemStack[] contents = copyStacks(player.inventory.mainInventory);
        if (!insertAll(contents, converted, player.inventory.getInventoryStackLimit())) {
            fluidStorage.readFromNBT(before, "");
            return false;
        }
        System.arraycopy(contents, 0, player.inventory.mainInventory, 0, contents.length);
        player.inventory.setItemStack(newCursor);
        return true;
    }

    /**
     * @return the fluid the stack carries through the network, or null for an item. A GregTech cell is an item here,
     *         although it keeps its fluid in its data the same way: the cell that was requested has to arrive as one.
     */
    public static FluidStack getNetworkFluid(ItemIdentifierStack stack) {
        if (stack == null || stack.getItem().item != LogisticsPipes.LogisticsFluidContainer) {
            return null;
        }
        return SimpleServiceLocator.logisticsFluidManager.getFluidFromContainer(stack);
    }

    /**
     * @return whether the stack is something fluid can be put into or taken out of, filled or empty
     */
    public static boolean isFluidContainer(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        if (stack.getItem() == LogisticsPipes.LogisticsFluidContainer || FluidContainerRegistry.isContainer(stack)) {
            return true;
        }
        // all the items of GregTech share a class that can hold fluid, only some of them do
        return stack.getItem() instanceof IFluidContainerItem
                && ((IFluidContainerItem) stack.getItem()).getCapacity(stack) > 0;
    }

    /**
     * @return whether the stack is a container with fluid in it
     */
    public boolean isFilledFluidContainer(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        ItemStack single = stack.copy();
        single.stackSize = 1;
        FluidStack fluid = getContainedFluid(single);
        return fluid != null && fluid.amount > 0;
    }

    /**
     * Empties the fluid containers in a slot of the inventory of the player into the fluid storage, as many of them as
     * the storage takes. The empty containers stay with the player.
     *
     * @return whether anything was emptied
     */
    public boolean emptyFluidContainers(EntityPlayer player, int inventorySlot) {
        ItemStack[] inventory = player.inventory.mainInventory;
        if (inventorySlot < 0 || inventorySlot >= inventory.length || inventory[inventorySlot] == null) {
            return false;
        }
        updateStorageUpgrades();
        ItemStack source = inventory[inventorySlot];
        NBTTagCompound before = new NBTTagCompound();
        fluidStorage.writeToNBT(before, "");
        List<ItemStack> emptied = new ArrayList<>();
        int done = 0;
        while (done < source.stackSize) {
            ItemStack single = source.copy();
            single.stackSize = 1;
            FluidStack held = getContainedFluid(single);
            if (held == null || held.amount <= 0) {
                break;
            }
            ItemStack result = emptyContainerIntoInternal(single, held);
            if (result == single) {
                break;
            }
            mergeInto(emptied, result);
            done++;
        }
        if (done == 0) {
            return false;
        }
        ItemStack[] contents = copyStacks(inventory);
        if (done < source.stackSize) {
            contents[inventorySlot].stackSize -= done;
        } else {
            // the slot the containers came from is free for the empty ones
            contents[inventorySlot] = null;
        }
        if (!insertAll(contents, emptied, player.inventory.getInventoryStackLimit())) {
            fluidStorage.readFromNBT(before, "");
            return false;
        }
        System.arraycopy(contents, 0, inventory, 0, contents.length);
        fluidStorage.markDirty();
        player.inventory.markDirty();
        if (player.openContainer != null) {
            player.openContainer.detectAndSendChanges();
        }
        markContentChanged();
        return true;
    }

    private static void mergeInto(List<ItemStack> stacks, ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) {
            return;
        }
        for (ItemStack present : stacks) {
            if (present.stackSize < present.getMaxStackSize() && present.isItemEqual(stack)
                    && ItemStack.areItemStackTagsEqual(present, stack)) {
                present.stackSize += stack.stackSize;
                return;
            }
        }
        stacks.add(stack.copy());
    }

    private static ItemStack[] copyStacks(ItemStack[] stacks) {
        ItemStack[] copy = new ItemStack[stacks.length];
        for (int i = 0; i < stacks.length; i++) {
            copy[i] = ItemStack.copyItemStack(stacks[i]);
        }
        return copy;
    }

    /**
     * Puts the stacks into the given slots, onto equal stacks first.
     *
     * @return false if not all of them have room; the slots are a copy the caller throws away then
     */
    private static boolean insertAll(ItemStack[] slots, List<ItemStack> stacks, int inventoryLimit) {
        for (ItemStack stack : stacks) {
            int left = stack.stackSize;
            int limit = Math.min(stack.getMaxStackSize(), inventoryLimit);
            for (ItemStack present : slots) {
                if (left > 0 && present != null
                        && present.isItemEqual(stack)
                        && ItemStack.areItemStackTagsEqual(present, stack)) {
                    int moved = Math.min(left, Math.max(0, limit - present.stackSize));
                    present.stackSize += moved;
                    left -= moved;
                }
            }
            for (int i = 0; i < slots.length && left > 0; i++) {
                if (slots[i] == null) {
                    slots[i] = stack.copy();
                    slots[i].stackSize = Math.min(left, limit);
                    left -= slots[i].stackSize;
                }
            }
            if (left > 0) {
                return false;
            }
        }
        return true;
    }

    private ItemStack getNetworkFluidClickResult(FluidStack target, ItemStack cursor) {
        FluidStack held = getContainedFluid(cursor);
        FluidIdentifier targetFluid = FluidIdentifier.get(target);
        if (held == null) {
            return fillContainerFromInternal(targetFluid, cursor);
        }
        if (!targetFluid.equals(FluidIdentifier.get(held))) {
            return cursor;
        }
        if (isContainerFull(cursor, held)) {
            return emptyContainerIntoInternal(cursor, held);
        }
        return getStoredFluidAmount(FluidIdentifier.get(held).getItemIdentifier()) > 0
                ? fillContainerFromInternal(targetFluid, cursor)
                : emptyContainerIntoInternal(cursor, held);
    }

    private ItemStack fillContainerFromInternal(FluidIdentifier target, ItemStack cursor) {
        if (cursor.getItem() instanceof IFluidContainerItem container) {
            ItemStack filled = cursor.copy();
            boolean changed = false;
            for (int slot = 0; slot < fluidStorage.getSizeInventory(); slot++) {
                FluidStack stored = fluidStorage.getFluid(slot);
                if (stored == null || !target.equals(FluidIdentifier.get(stored))) {
                    continue;
                }
                int fillable = container.fill(filled, stored.copy(), false);
                if (fillable <= 0) {
                    continue;
                }
                FluidStack drained = fluidStorage.drain(slot, fillable, true);
                if (drained == null || drained.amount <= 0) {
                    continue;
                }
                container.fill(filled, drained, true);
                changed = true;
            }
            return changed ? filled : cursor;
        }
        if (cursor.getItem() == LogisticsPipes.LogisticsFluidContainer) {
            FluidStack held = getContainedFluid(cursor);
            int room = fluidStorage.getSlotCapacity() - (held == null ? 0 : held.amount);
            if (room <= 0) {
                return cursor;
            }
            FluidStack collected = null;
            for (int slot = 0; slot < fluidStorage.getSizeInventory() && room > 0; slot++) {
                FluidStack stored = fluidStorage.getFluid(slot);
                if (stored == null || !target.equals(FluidIdentifier.get(stored))) {
                    continue;
                }
                FluidStack drained = fluidStorage.drain(slot, room, true);
                if (drained == null || drained.amount <= 0) {
                    continue;
                }
                if (collected == null) {
                    collected = drained.copy();
                } else {
                    collected.amount += drained.amount;
                }
                room -= drained.amount;
            }
            if (collected == null) {
                return cursor;
            }
            if (held != null) {
                collected.amount += held.amount;
            }
            return SimpleServiceLocator.logisticsFluidManager.getFluidContainer(collected).makeNormalStack();
        }
        for (int slot = 0; slot < fluidStorage.getSizeInventory(); slot++) {
            FluidStack stored = fluidStorage.getFluid(slot);
            if (stored == null || !target.equals(FluidIdentifier.get(stored))) {
                continue;
            }
            ItemStack filled = FluidContainerRegistry.fillFluidContainer(stored, cursor);
            FluidStack filledFluid = getContainedFluid(filled);
            if (filled == null || filledFluid == null
                    || !target.equals(FluidIdentifier.get(filledFluid))
                    || filledFluid.amount > stored.amount) {
                continue;
            }
            fluidStorage.drain(slot, filledFluid.amount, true);
            return filled;
        }
        return cursor;
    }

    private ItemStack emptyContainerIntoInternal(ItemStack cursor, FluidStack held) {
        if (cursor.getItem() instanceof IFluidContainerItem container) {
            ItemStack drainedContainer = cursor.copy();
            int accepted = fluidStorage.fill(held, false);
            if (accepted <= 0) {
                return cursor;
            }
            FluidStack drained = container.drain(drainedContainer, accepted, true);
            if (drained == null || drained.amount <= 0) {
                return cursor;
            }
            fluidStorage.fill(drained, true);
            return drainedContainer;
        }
        if (cursor.getItem() == LogisticsPipes.LogisticsFluidContainer) {
            int accepted = fluidStorage.fill(held, false);
            if (accepted <= 0) {
                return cursor;
            }
            FluidStack inserted = held.copy();
            inserted.amount = accepted;
            fluidStorage.fill(inserted, true);
            int remaining = held.amount - accepted;
            if (remaining <= 0) {
                return new ItemStack(LogisticsPipes.LogisticsFluidContainer, 1);
            }
            FluidStack leftover = held.copy();
            leftover.amount = remaining;
            return SimpleServiceLocator.logisticsFluidManager.getFluidContainer(leftover).makeNormalStack();
        }
        int accepted = fluidStorage.fill(held, false);
        if (accepted < held.amount) {
            return cursor;
        }
        ItemStack empty = FluidContainerRegistry.drainFluidContainer(cursor);
        if (empty == null) {
            return cursor;
        }
        fluidStorage.fill(held, true);
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
            return held.amount >= fluidStorage.getSlotCapacity();
        }
        return true;
    }

    private void syncCursor(EntityPlayer player) {
        if (player instanceof EntityPlayerMP) {
            MainProxy.sendPacketToPlayer(
                    PacketHandler.getPacket(RequestTableSetCursorPacket.class).setStack(player.inventory.getItemStack())
                            .setTilePos(container),
                    player);
        }
    }

    @Override
    public ItemStack getResultForClick(EntityPlayer player) {
        return getResultForClick(player, null, 1);
    }

    /**
     * Crafts the current fake recipe and returns the stack that should remain on the player's cursor.
     *
     * @param player          player using the table
     * @param heldStack       current cursor stack, or {@code null}
     * @param requestedAmount desired number of output items to craft
     * @return new cursor stack, or {@code null} if no craft could be made
     */
    public ItemStack getResultForClick(EntityPlayer player, ItemStack heldStack, int requestedAmount) {
        CraftingPreview firstPreview = getCraftingPreview(player);
        if (firstPreview == null) {
            return null;
        }
        ItemStack cursorStack = heldStack == null ? null : heldStack.copy();
        if (cursorStack != null && !canMerge(cursorStack, firstPreview.result)) {
            return null;
        }

        int stackLimit = cursorStack == null ? firstPreview.result.getMaxStackSize() : cursorStack.getMaxStackSize();
        int room = cursorStack == null ? stackLimit : stackLimit - cursorStack.stackSize;
        if (room < firstPreview.result.stackSize) {
            return null;
        }

        int targetAmount = Math.min(room, Math.max(Math.max(1, requestedAmount), firstPreview.result.stackSize));
        int craftedAmount = 0;
        while (craftedAmount < targetAmount) {
            CraftingPreview preview = getCraftingPreview(player);
            if (preview == null) {
                break;
            }
            if (cursorStack != null && !canMerge(cursorStack, preview.result)) {
                break;
            }
            int currentSize = cursorStack == null ? 0 : cursorStack.stackSize;
            if (currentSize + preview.result.stackSize > stackLimit) {
                break;
            }
            ItemStack crafted = consumeCraftingPreview(player, preview);
            if (crafted == null) {
                break;
            }
            if (cursorStack == null) {
                cursorStack = crafted.copy();
            } else {
                cursorStack.stackSize += crafted.stackSize;
            }
            craftedAmount += crafted.stackSize;
        }
        return craftedAmount > 0 ? cursorStack : null;
    }

    /**
     * Shift-crafts the current fake recipe directly into the player's inventory.
     *
     * @param player          player using the table
     * @param requestedAmount maximum number of output items to craft
     * @return number of crafted output items
     */
    public int craftIntoPlayerInventory(EntityPlayer player, int requestedAmount) {
        int targetAmount = Math.max(1, requestedAmount);
        int craftedAmount = 0;
        while (craftedAmount < targetAmount) {
            CraftingPreview preview = getCraftingPreview(player);
            if (preview == null || craftedAmount + preview.result.stackSize > targetAmount) {
                break;
            }
            if (getPlayerInventoryRoom(player, preview.result) < preview.result.stackSize) {
                break;
            }
            ItemStack crafted = consumeCraftingPreview(player, preview);
            if (crafted == null) {
                break;
            }
            ItemStack toInsert = crafted.copy();
            if (!player.inventory.addItemStackToInventory(toInsert) && toInsert.stackSize > 0) {
                storeCraftingRemainder(toInsert, player);
                break;
            }
            craftedAmount += crafted.stackSize;
        }
        if (craftedAmount > 0) {
            player.inventory.markDirty();
        }
        return craftedAmount;
    }

    /**
     * Forgets the recipe of the crafting grid and puts the items lying in it into the storage of the table.
     */
    public void clearCraftingGrid() {
        for (int i = 0; i < matrix.getSizeInventory(); i++) {
            matrix.setInventorySlotContents(i, (ItemStack) null);
        }
        cacheRecipe();
        if (!MainProxy.isClient(getWorld())) {
            storeCraftGridItems(false);
        }
    }

    /**
     * Moves items out of the crafting grid into the storage of the table. What doesn't fit stays in the grid.
     *
     * @param onlyMismatched true to keep the items that lie on the ingredient the recipe wants there
     */
    public void storeCraftGridItems(boolean onlyMismatched) {
        boolean changed = false;
        for (int i = 0; i < craftGrid.getSizeInventory(); i++) {
            ItemStack stack = craftGrid.getStackInSlot(i);
            if (stack == null) {
                continue;
            }
            if (onlyMismatched && matchesRecipeSlot(i, stack)) {
                continue;
            }
            ItemStack moving = stack.copy();
            int remaining = addToStorage(moving);
            if (remaining <= 0) {
                craftGrid.setInventorySlotContents(i, null);
            } else {
                stack.stackSize = remaining;
            }
            changed = true;
        }
        if (changed) {
            craftGrid.markDirty();
            inv.markDirty();
            markContentChanged();
        }
    }

    private boolean matchesRecipeSlot(int slot, ItemStack stack) {
        ItemStack wanted = matrix.getStackInSlot(slot);
        return wanted != null && ItemIdentifier.get(wanted).equals(ItemIdentifier.get(stack));
    }

    /**
     * Called after a slot of the crafting grid changed. An item that isn't what the remembered recipe wants there
     * starts a new recipe: the one that lies in the grid now.
     *
     * @param onlyAmountChanged true if the slot holds the item it held before. That starts no recipe: the table itself
     *                          takes items out of the grid, and what it leaves there must not replace the recipe that
     *                          was just set.
     */
    public void onCraftGridChanged(int slot, boolean onlyAmountChanged) {
        if (container != null) {
            container.markDirty();
        }
        ItemStack stack = craftGrid.getStackInSlot(slot);
        if (onlyAmountChanged || stack == null || matchesRecipeSlot(slot, stack)) {
            return;
        }
        for (int i = 0; i < matrix.getSizeInventory(); i++) {
            ItemStack lying = craftGrid.getStackInSlot(i);
            if (lying == null) {
                matrix.setInventorySlotContents(i, (ItemStack) null);
            } else {
                ItemStack single = lying.copy();
                single.stackSize = 1;
                matrix.setInventorySlotContents(i, single);
            }
        }
        cacheRecipe();
    }

    /**
     * Puts an arrived stack into the slots of the crafting grid whose recipe ingredient it is, spread evenly so the
     * grid lasts for as many crafts as possible. The stack keeps what didn't go in.
     */
    private void addToCraftGrid(ItemStack stack) {
        ItemIdentifier item = ItemIdentifier.get(stack);
        int limit = Math.min(stack.getMaxStackSize(), craftGrid.getInventoryStackLimit());
        boolean placed = true;
        boolean changed = false;
        while (stack.stackSize > 0 && placed) {
            placed = false;
            for (int i = 0; i < craftGrid.getSizeInventory() && stack.stackSize > 0; i++) {
                ItemStack wanted = matrix.getStackInSlot(i);
                if (wanted == null || !ItemIdentifier.get(wanted).equals(item)) {
                    continue;
                }
                ItemStack lying = craftGrid.getStackInSlot(i);
                if (lying == null) {
                    ItemStack single = stack.copy();
                    single.stackSize = 1;
                    craftGrid.setInventorySlotContents(i, single);
                } else if (lying.stackSize < limit && ItemIdentifier.get(lying).equals(item)) {
                    lying.stackSize++;
                } else {
                    continue;
                }
                stack.stackSize--;
                placed = true;
                changed = true;
            }
        }
        if (changed) {
            craftGrid.markDirty();
        }
    }

    /**
     * Requests the missing fake crafting-grid ingredients for the given number of crafts.
     *
     * @param player     player requesting the ingredients
     * @param multiplier number of craft sets to request
     */
    public void requestCraftingIngredients(EntityPlayer player, int multiplier) {
        List<ItemIdentifierStack> request = getCraftingIngredientRequest(player, multiplier);
        if (!request.isEmpty()) {
            RequestHandler.requestList(player, request, this, getDisplaySettings(player).reportsRequests());
        }
    }

    /**
     * Puts a stack into the item storage. A slot holds more than a normal stack there, but items that don't stack at
     * all keep a slot each.
     *
     * @return how many items didn't fit
     */
    private int addToStorage(ItemStack stack) {
        return inv.addCompressed(stack, stack != null && stack.getMaxStackSize() > 1);
    }

    /**
     * @return whether crafting gives the item back: a tool like a hammer or a wrench, which is used and not used up
     */
    private static boolean isTool(ItemStack stack) {
        Item item = stack.getItem();
        if (item == null || stack.getMaxStackSize() != 1 || !item.hasContainerItem(stack)) {
            return false;
        }
        ItemStack left = item.getContainerItem(stack.copy());
        return left != null && left.getItem() == item;
    }

    /**
     * Looks for a tool in the network that can stand in for an ingredient the network has none of and cannot craft: a
     * file for the single use file a recipe shows, or a wrench of another material.
     *
     * @param networkItems cache of two maps, what the network has and what it can craft; filled on first use
     * @return the tool the network has most of, or null if the ingredient itself can be had or nothing can replace it
     */
    private ItemIdentifier findToolInNetwork(ItemIdentifier ingredient, Map<ItemIdentifier, Integer>[] networkItems) {
        if (getRouter() == null) {
            return null;
        }
        if (networkItems[0] == null) {
            networkItems[0] = SimpleServiceLocator.logisticsManager.getAvailableItems(getRouter().getIRoutersByCost());
            networkItems[1] = new HashMap<>();
            for (ItemIdentifier craftable : SimpleServiceLocator.logisticsManager
                    .getCraftableItems(getRouter().getIRoutersByCost())) {
                networkItems[1].put(craftable, 1);
            }
        }
        Integer amount = networkItems[0].get(ingredient);
        if ((amount != null && amount > 0) || networkItems[1].containsKey(ingredient)) {
            return null;
        }
        ItemIdentifier best = null;
        int bestAmount = 0;
        for (Entry<ItemIdentifier, Integer> entry : networkItems[0].entrySet()) {
            ItemIdentifier candidate = entry.getKey();
            if (entry.getValue() <= bestAmount || candidate.getMaxStackSize() != 1) {
                continue;
            }
            if (matchesIngredient(ingredient, candidate, true) && isTool(candidate.unsafeMakeNormalStack(1))) {
                best = candidate;
                bestAmount = entry.getValue();
            }
        }
        return best;
    }

    @SuppressWarnings("unchecked")
    private List<ItemIdentifierStack> getCraftingIngredientRequest(EntityPlayer player, int multiplier) {
        List<ItemStack> present = new ArrayList<>();
        for (Pair<ItemStack, Integer> entry : inv) {
            present.add(entry.getValue1());
        }
        // what lies in the crafting grid doesn't have to be requested either
        for (Pair<ItemStack, Integer> entry : craftGrid) {
            present.add(entry.getValue1());
        }
        // and neither does what the player carries: crafting takes its ingredients from there as well
        if (player != null) {
            present.addAll(Arrays.asList(player.inventory.mainInventory));
        }
        // the tools at hand: crafting takes them from the grid, the storage or the player and gives them back
        List<ItemIdentifier> toolsAtHand = new ArrayList<>();
        for (ItemStack stack : present) {
            if (stack != null && isTool(stack)) {
                toolsAtHand.add(ItemIdentifier.get(stack));
            }
        }

        // what the network has and can craft, looked up only when an ingredient needs a replacement
        Map<ItemIdentifier, Integer>[] networkItems = new Map[2];

        Map<ItemIdentifier, Integer> requested = new HashMap<>();
        // tools: one of each is enough however often the recipe is crafted
        List<ItemIdentifier> tools = new ArrayList<>();
        int clampedMultiplier = Math.max(1, multiplier);
        for (int i = 0; i < matrix.getSizeInventory(); i++) {
            ItemStack ingredient = matrix.getStackInSlot(i);
            if (ingredient == null) {
                continue;
            }
            ItemIdentifier item = ItemIdentifier.get(ingredient);
            // a tool at hand does the job of the ingredient: any tool of the kind, whatever it is made of and
            // however worn it is, also when the recipe shows one that is used up, like a single use wrench
            boolean covered = false;
            for (ItemIdentifier tool : toolsAtHand) {
                if (matchesIngredient(item, tool, true)) {
                    covered = true;
                    break;
                }
            }
            if (covered) {
                continue;
            }
            // the network may not have what the recipe shows but a tool that does the same job
            ItemIdentifier networkTool = findToolInNetwork(item, networkItems);
            if (networkTool != null) {
                boolean requestedAlready = false;
                for (ItemIdentifier tool : tools) {
                    if (matchesIngredient(item, tool, true)) {
                        requestedAlready = true;
                        break;
                    }
                }
                if (!requestedAlready) {
                    tools.add(networkTool);
                }
                continue;
            }
            if (isTool(ingredient)) {
                if (!tools.contains(item)) {
                    tools.add(item);
                }
            } else {
                requested.merge(item, clampedMultiplier, Integer::sum);
            }
        }
        for (ItemStack stack : present) {
            if (stack == null) {
                continue;
            }
            ItemIdentifier item = ItemIdentifier.get(stack);
            Integer remaining = requested.get(item);
            if (remaining == null) {
                continue;
            }
            int missing = remaining - stack.stackSize;
            if (missing > 0) {
                requested.put(item, missing);
            } else {
                requested.remove(item);
            }
        }
        List<ItemIdentifierStack> result = new ArrayList<>(requested.size() + tools.size());
        for (Entry<ItemIdentifier, Integer> entry : requested.entrySet()) {
            if (entry.getValue() > 0) {
                result.add(entry.getKey().makeStack(entry.getValue()));
            }
        }
        for (ItemIdentifier tool : tools) {
            result.add(tool.makeStack(1));
        }
        return result;
    }

    /**
     * Crafts a recipe a number of times from what is at hand: the crafting grid, the storage of the table and the
     * inventory of the player. The results go to the player, into the storage once the player has no room. The recipe
     * the table remembers stays as it is.
     *
     * @param grid   the recipe, nine stacks row by row
     * @param crafts how often to craft it
     * @return how often it was crafted
     */
    public int autoCraft(EntityPlayer player, ItemStack[] grid, int crafts) {
        ItemStack[] remembered = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            remembered[i] = matrix.getStackInSlot(i);
        }
        handleNEIRecipePacket(grid);
        int made = 0;
        try {
            while (made < crafts && resultInv.getStackInSlot(0) != null) {
                CraftingPreview preview = getCraftingPreview(player);
                if (preview == null) {
                    break;
                }
                ItemStack result = consumeCraftingPreview(player, preview);
                if (result == null) {
                    break;
                }
                made++;
                if (player.inventory.addItemStackToInventory(result) || result.stackSize <= 0) {
                    continue;
                }
                result.stackSize = addToStorage(result);
                if (result.stackSize > 0) {
                    // no room anywhere: what is made is not lost, but nothing more is made
                    ItemIdentifierInventory.dropItems(getWorld(), result, getX(), getY(), getZ());
                    break;
                }
            }
        } finally {
            handleNEIRecipePacket(remembered);
        }
        if (made > 0) {
            inv.markDirty();
            craftGrid.markDirty();
            player.inventory.markDirty();
        }
        return made;
    }

    private CraftingPreview getCraftingPreview(EntityPlayer player) {
        CraftingPreview preview = getCraftingPreview(player, true);
        if (preview == null) {
            preview = getCraftingPreview(player, false);
        }
        return preview;
    }

    private CraftingPreview getCraftingPreview(EntityPlayer player, boolean oreDict) {
        IRecipe recipe = getCurrentRecipe();
        if (recipe == null || resultInv.getStackInSlot(0) == null) {
            return null;
        }
        IngredientUse[] uses = findIngredients(player, oreDict);
        if (uses == null) {
            return null;
        }
        AutoCraftingInventory preview = buildPreviewCraftingInventory(uses);
        if (!recipe.matches(preview, getWorld())) {
            return null;
        }
        ItemStack previewResult = recipe.getCraftingResult(preview);
        if (previewResult == null || !ItemIdentifier.get(previewResult)
                .equalsWithoutNBT(ItemIdentifier.get(resultInv.getStackInSlot(0)))) {
            return null;
        }
        return new CraftingPreview(recipe, uses, previewResult.copy());
    }

    private ItemStack consumeCraftingPreview(EntityPlayer player, CraftingPreview preview) {
        AutoCraftingInventory consumed = consumeIngredients(preview.uses);
        ItemStack result = preview.recipe.getCraftingResult(consumed);
        if (result == null) {
            return null;
        }
        result = result.copy();

        SlotCrafting craftingSlot = new SlotCrafting(player, consumed, resultInv, 0, 0, 0);
        craftingSlot.onPickupFromSlot(player, result);
        collectCraftingRemainders(consumed, player);
        inv.markDirty();
        player.inventory.markDirty();
        return result;
    }

    private boolean canMerge(ItemStack first, ItemStack second) {
        return first != null && second != null
                && first.isItemEqual(second)
                && ItemStack.areItemStackTagsEqual(first, second);
    }

    private int getPlayerInventoryRoom(EntityPlayer player, ItemStack stack) {
        int room = 0;
        int stackLimit = Math.min(stack.getMaxStackSize(), player.inventory.getInventoryStackLimit());
        for (ItemStack inventoryStack : player.inventory.mainInventory) {
            if (inventoryStack == null) {
                room += stackLimit;
            } else if (canMerge(inventoryStack, stack)) {
                room += Math.max(0, stackLimit - inventoryStack.stackSize);
            }
        }
        return room;
    }

    /**
     * @return the recipe of the crafting matrix. The list of all recipes is only searched when the matrix changed.
     */
    private IRecipe getCurrentRecipe() {
        if (getCachedRecipe() == null) {
            cacheRecipe();
        }
        return resultInv.getStackInSlot(0) == null ? null : getCachedRecipe();
    }

    private IngredientUse[] findIngredients(EntityPlayer player, boolean oreDict) {
        IngredientUse[] uses = new IngredientUse[9];
        int[] usedGrid = new int[craftGrid.getSizeInventory()];
        int[] usedInternal = new int[inv.getSizeInventory()];
        int[] usedPlayer = new int[player.inventory.mainInventory.length];
        for (int i = 0; i < 9; i++) {
            ItemStack ingredient = matrix.getStackInSlot(i);
            if (ingredient == null) {
                continue;
            }
            ItemIdentifier expected = ItemIdentifier.get(ingredient);
            // what lies in the grid goes first, then the storage of the table, then the inventory of the player
            IngredientUse use = findIngredientInInventory(expected, craftGrid, usedGrid, oreDict);
            if (use == null) {
                use = findIngredientInInventory(expected, inv, usedInternal, oreDict);
            }
            if (use == null) {
                use = findIngredientInInventory(expected, player.inventory, usedPlayer, oreDict);
            }
            if (use == null) {
                return null;
            }
            uses[i] = use;
        }
        return uses;
    }

    /**
     * @return the slots of the crafting grid whose ingredient is nowhere at hand, a bit for each of them
     */
    public int getMissingIngredientSlots(EntityPlayer player) {
        if (resultInv.getStackInSlot(0) == null) {
            return 0;
        }
        int[] usedGrid = new int[craftGrid.getSizeInventory()];
        int[] usedInternal = new int[inv.getSizeInventory()];
        int[] usedPlayer = new int[player.inventory.mainInventory.length];
        int missing = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack ingredient = matrix.getStackInSlot(i);
            if (ingredient == null) {
                continue;
            }
            ItemIdentifier expected = ItemIdentifier.get(ingredient);
            if (findIngredientInInventory(expected, craftGrid, usedGrid, true) == null
                    && findIngredientInInventory(expected, inv, usedInternal, true) == null
                    && findIngredientInInventory(expected, player.inventory, usedPlayer, true) == null) {
                missing |= 1 << i;
            }
        }
        return missing;
    }

    private IngredientUse findIngredientInInventory(ItemIdentifier expected, IInventory inventory, int[] used,
            boolean oreDict) {
        int limit = Math.min(used.length, inventory.getSizeInventory());
        for (int slot = 0; slot < limit; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack == null || stack.stackSize <= used[slot]) {
                continue;
            }
            if (matchesIngredient(expected, ItemIdentifier.get(stack), oreDict)) {
                used[slot]++;
                return new IngredientUse(inventory, slot);
            }
        }
        return null;
    }

    private boolean matchesIngredient(ItemIdentifier expected, ItemIdentifier candidate, boolean oreDict) {
        if (expected.equalsForCrafting(candidate)) {
            return true;
        }
        return oreDict && expected.getDictIdentifiers() != null
                && candidate.getDictIdentifiers() != null
                && expected.getDictIdentifiers().canMatch(candidate.getDictIdentifiers(), true, false);
    }

    private AutoCraftingInventory buildPreviewCraftingInventory(IngredientUse[] uses) {
        AutoCraftingInventory craftInv = new AutoCraftingInventory(null);
        for (int i = 0; i < uses.length; i++) {
            if (uses[i] == null) {
                continue;
            }
            ItemStack stack = uses[i].inventory.getStackInSlot(uses[i].slot);
            if (stack != null) {
                ItemStack copy = stack.copy();
                copy.stackSize = 1;
                craftInv.setInventorySlotContents(i, copy);
            }
        }
        return craftInv;
    }

    private AutoCraftingInventory consumeIngredients(IngredientUse[] uses) {
        AutoCraftingInventory craftInv = new AutoCraftingInventory(null);
        for (int i = 0; i < uses.length; i++) {
            if (uses[i] != null) {
                craftInv.setInventorySlotContents(i, uses[i].inventory.decrStackSize(uses[i].slot, 1));
            }
        }
        return craftInv;
    }

    private void collectCraftingRemainders(AutoCraftingInventory craftInv, EntityPlayer player) {
        for (int i = 0; i < craftInv.getSizeInventory(); i++) {
            ItemStack remainder = craftInv.getStackInSlot(i);
            craftInv.setInventorySlotContents(i, null);
            storeCraftingRemainder(remainder, player);
        }
    }

    private void storeCraftingRemainder(ItemStack stack, EntityPlayer player) {
        if (stack == null || stack.stackSize <= 0) {
            return;
        }
        stack.stackSize = addToStorage(stack);
        if (stack.stackSize <= 0) {
            return;
        }
        if (player.inventory.addItemStackToInventory(stack) || stack.stackSize <= 0) {
            return;
        }
        ItemIdentifierInventory.dropItems(getWorld(), stack, getX(), getY(), getZ());
    }

    /**
     * Sends all internal item stacks that can find a storage destination back into the logistics network.
     */
    public void sendStoredItemsToNetwork() {
        updateStorageUpgrades();
        boolean changed = false;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            while (stack != null) {
                ItemStack toSendStack = stack.copy();
                toSendStack.stackSize = Math.min(stack.stackSize, stack.getMaxStackSize());
                IRoutedItem itemToSend = SimpleServiceLocator.routedItemHelper.createNewTravelItem(toSendStack);
                SimpleServiceLocator.logisticsManager
                        .assignDestinationFor(itemToSend, getRouter().getSimpleID(), false);
                if (itemToSend.getDestinationUUID() == null) {
                    break;
                }
                ForgeDirection dir = getRouteLayer().getOrientationForItem(itemToSend, null);
                queueRoutedItem(itemToSend, dir.getOpposite());
                inv.decrStackSize(i, toSendStack.stackSize);
                changed = true;
                stack = inv.getStackInSlot(i);
            }
        }
        if (changed) {
            inv.markDirty();
            markContentChanged();
            expectNetworkChange();
        }
    }

    /**
     * @return what the item storage holds, one stack with the whole amount for each kind of item
     */
    public List<ItemIdentifierStack> getStoredItems() {
        Map<ItemIdentifier, Integer> amounts = new LinkedHashMap<>();
        for (Pair<ItemStack, Integer> entry : inv) {
            ItemStack stack = entry.getValue1();
            if (stack != null) {
                amounts.merge(ItemIdentifier.get(stack), stack.stackSize, Integer::sum);
            }
        }
        List<ItemIdentifierStack> result = new ArrayList<>(amounts.size());
        for (Entry<ItemIdentifier, Integer> entry : amounts.entrySet()) {
            result.add(entry.getKey().makeStack(entry.getValue()));
        }
        return result;
    }

    /**
     * @return what the fluid storage holds, one fluid container with the whole amount in millibuckets for each fluid
     */
    public List<ItemIdentifierStack> getStoredFluids() {
        Map<ItemIdentifier, Integer> amounts = new LinkedHashMap<>();
        for (int slot = 0; slot < fluidStorage.getSizeInventory(); slot++) {
            FluidStack stored = fluidStorage.getFluid(slot);
            if (stored != null && stored.amount > 0) {
                amounts.merge(FluidIdentifier.get(stored).getItemIdentifier(), stored.amount, Integer::sum);
            }
        }
        List<ItemIdentifierStack> result = new ArrayList<>(amounts.size());
        for (Entry<ItemIdentifier, Integer> entry : amounts.entrySet()) {
            result.add(entry.getKey().makeStack(entry.getValue()));
        }
        return result;
    }

    /**
     * Sends all stored fluids that can find a fluid sink back into the logistics network.
     */
    public void sendStoredFluidsToNetwork() {
        updateStorageUpgrades();
        boolean changed = false;
        for (int slot = 0; slot < fluidStorage.getSizeInventory(); slot++) {
            FluidStack stored = fluidStorage.getFluid(slot);
            while (stored != null && stored.amount > 0) {
                FluidStack toSend = stored.copy();
                toSend.amount = Math.min(toSend.amount, FLUID_SEND_CHUNK);
                Pair<Integer, Integer> destination = SimpleServiceLocator.logisticsFluidManager
                        .getBestReply(toSend, getRouter(), new ArrayList<>());
                if (destination == null || destination.getValue1() == null
                        || destination.getValue1() == 0
                        || destination.getValue2() == null
                        || destination.getValue2() <= 0) {
                    break;
                }
                int amount = Math.min(toSend.amount, destination.getValue2());
                FluidStack drained = fluidStorage.drain(slot, amount, true);
                if (drained == null || drained.amount <= 0) {
                    break;
                }
                IRoutedItem item = SimpleServiceLocator.routedItemHelper
                        .createNewTravelItem(SimpleServiceLocator.logisticsFluidManager.getFluidContainer(drained));
                item.setDestination(destination.getValue1());
                item.setTransportMode(TransportMode.Passive);
                ForgeDirection dir = getRouteLayer().getOrientationForItem(item, null);
                queueRoutedItem(item, dir.getOpposite());
                changed = true;
                stored = fluidStorage.getFluid(slot);
            }
        }
        if (changed) {
            markContentChanged();
            expectNetworkChange();
        }
    }

    @Override
    public TransportLayer getTransportLayer() {
        if (_transportLayer == null) {
            _transportLayer = new TransportLayer() {

                @Override
                public void handleItem(IRoutedItem item) {
                    RequestTableMk3Pipe.this.notifyOfItemArival(item.getInfo());
                    ItemIdentifierStack routedStack = item.getItemIdentifierStack();
                    if (routedStack == null) {
                        return;
                    }
                    FluidStack fluid = getNetworkFluid(routedStack);
                    if (fluid != null) {
                        updateStorageUpgrades();
                        int filled = fluidStorage.fill(fluid, true);
                        if (filled < fluid.amount) {
                            fluid.amount -= filled;
                            ItemStack leftover = SimpleServiceLocator.logisticsFluidManager.getFluidContainer(fluid)
                                    .makeNormalStack();
                            leftover.stackSize = addToStorage(leftover);
                            if (leftover.stackSize > 0) {
                                ItemIdentifierInventory.dropItems(getWorld(), leftover, getX(), getY(), getZ());
                            }
                        }
                        routedStack.setStackSize(0);
                        markContentChanged();
                    } else {
                        updateStorageUpgrades();
                        ItemStack stack = routedStack.makeNormalStack();
                        deliverToRequester(item, stack);
                        if (stack.stackSize > 0) {
                            addToCraftGrid(stack);
                        }
                        if (stack.stackSize > 0) {
                            stack.stackSize = addToStorage(stack);
                        }
                        if (stack.stackSize > 0) {
                            ItemIdentifierInventory.dropItems(getWorld(), stack, getX(), getY(), getZ());
                        }
                        routedStack.setStackSize(0);
                        markContentChanged();
                    }
                }

                @Override
                public ForgeDirection itemArrived(IRoutedItem item, ForgeDirection denied) {
                    return null;
                }

                @Override
                public boolean stillWantItem(IRoutedItem item) {
                    return false;
                }
            };
        }
        return _transportLayer;
    }

    @Override
    public void sendFailed(FluidIdentifier fluid, Integer amount) {
        // The GUI receives the request failure through the normal request popup/chat path.
    }

    @Override
    public void onAllowedRemoval() {
        super.onAllowedRemoval();
        if (MainProxy.isServer(getWorld())) {
            craftGrid.dropContents(getWorld(), getX(), getY(), getZ());
            fluidStorage.dropContents(getWorld(), getX(), getY(), getZ());
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        fluidStorage.readFromNBT(tag, NBT_FLUID_STORAGE);
        craftGrid.readFromNBT(tag, NBT_CRAFT_GRID);
        displaySettingsStore.readFromNBT(tag, NBT_DISPLAY_SETTINGS);
        updateStorageUpgrades();
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        updateStorageUpgrades();
        super.writeToNBT(tag);
        fluidStorage.writeToNBT(tag, NBT_FLUID_STORAGE);
        craftGrid.writeToNBT(tag, NBT_CRAFT_GRID);
        displaySettingsStore.writeToNBT(tag, NBT_DISPLAY_SETTINGS);
    }

    private static class IngredientUse {

        private final IInventory inventory;
        private final int slot;

        private IngredientUse(IInventory inventory, int slot) {
            this.inventory = inventory;
            this.slot = slot;
        }
    }

    private static class CraftingPreview {

        private final IRecipe recipe;
        private final IngredientUse[] uses;
        private final ItemStack result;

        private CraftingPreview(IRecipe recipe, IngredientUse[] uses, ItemStack result) {
            this.recipe = recipe;
            this.uses = uses;
            this.result = result;
        }
    }
}
