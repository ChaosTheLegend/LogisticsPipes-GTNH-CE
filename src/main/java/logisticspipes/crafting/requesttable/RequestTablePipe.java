package logisticspipes.crafting.requesttable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
import logisticspipes.blocks.crafting.AutoCraftingInventory;
import logisticspipes.config.Configs;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.items.ItemUpgrade;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.logisticspipes.TransportLayer;
import logisticspipes.network.GuiIDs;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.crafting.requesttable.RequestTableContentPacket;
import logisticspipes.network.packets.crafting.requesttable.RequestTableRefreshPacket;
import logisticspipes.network.packets.crafting.requesttable.RequestTableSetCursorPacket;
import logisticspipes.pipes.PipeBlockRequestTable;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.RequestHandler;
import logisticspipes.utils.CraftingUtil;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.PlayerCollectionList;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierInventory;
import logisticspipes.utils.item.ItemIdentifierStack;
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
public class RequestTablePipe extends PipeBlockRequestTable implements IRequestFluid {

    private static final int BASE_ITEM_SLOTS = 27;
    private static final int ITEM_SLOT_UPGRADE_SIZE = 9;
    private static final int BASE_ITEM_STACK_LIMIT = 64;
    private static final int ITEM_STACK_UPGRADE_SIZE = 64;
    private static final int BASE_FLUID_SLOTS = 9;
    private static final int FLUID_SLOT_UPGRADE_SIZE = 9;
    private static final int FLUID_SEND_CHUNK = 5000;
    private static final String NBT_FLUID_STORAGE = "newRequestTableFluidStorage";
    private static final String NBT_DISPLAY_SETTINGS = "newRequestTableDisplaySettings";
    private final RequestTableFluidStorage fluidStorage = RequestTableFluidStorage.createDefault();
    private final RequestTableDisplaySettingsStore displaySettingsStore = new RequestTableDisplaySettingsStore();
    private final PlayerCollectionList requestTableGuiWatchers = new PlayerCollectionList();

    /**
     * Creates the new request table pipe item.
     *
     * @param item backing pipe item
     */
    public RequestTablePipe(Item item) {
        super(item);
        updateStorageUpgrades();
    }

    @Override
    public void openGui(EntityPlayer player) {
        updateStorageUpgrades();
        player.openGui(LogisticsPipes.instance, GuiIDs.GUI_New_Request_Table_ID, getWorld(), getX(), getY(), getZ());
    }

    @Override
    public void guiOpenedByPlayer(EntityPlayer player) {
        super.guiOpenedByPlayer(player);
        if (!requestTableGuiWatchers.contains(player)) {
            requestTableGuiWatchers.add(player);
        }
        sendNetworkContentToPlayer(player);
    }

    @Override
    public void guiClosedByPlayer(EntityPlayer player) {
        super.guiClosedByPlayer(player);
        requestTableGuiWatchers.remove(player);
    }

    /**
     * Sends a fresh combined network/internal list to all players currently watching this table.
     */
    public void sendNetworkContentToWatchers() {
        if (MainProxy.isClient(getWorld()) || requestTableGuiWatchers.isEmpty()) {
            return;
        }
        List<RequestTableNetworkEntry> entries = RequestTableRefreshPacket.buildEntries(this);
        for (EntityPlayer player : requestTableGuiWatchers.players()) {
            if (player != null) {
                sendNetworkContentToPlayer(player, entries);
            }
        }
    }

    private void sendNetworkContentToPlayer(EntityPlayer player) {
        if (MainProxy.isClient(getWorld())) {
            return;
        }
        sendNetworkContentToPlayer(player, RequestTableRefreshPacket.buildEntries(this));
    }

    private void sendNetworkContentToPlayer(EntityPlayer player, List<RequestTableNetworkEntry> entries) {
        MainProxy.sendPacketToPlayer(
                PacketHandler.getPacket(RequestTableContentPacket.class).setEntries(entries)
                        .setDisplaySettings(getDisplaySettings(player)).setTilePos(container),
                player);
    }

    /**
     * Returns the saved view state for this player at this table.
     */
    public RequestTableDisplaySettings getDisplaySettings(EntityPlayer player) {
        return displaySettingsStore.get(player.getUniqueID());
    }

    /**
     * Saves the view state for this player at this table.
     */
    public void setDisplaySettings(EntityPlayer player, RequestTableDisplaySettings settings) {
        if (displaySettingsStore.set(player.getUniqueID(), settings) && container != null) {
            container.markDirty();
        }
    }

    /**
     * Applies installed request-table storage upgrades to the item and fluid backing stores.
     */
    public void updateStorageUpgrades() {
        if (container == null) return;

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
            sendNetworkContentToWatchers();
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
            stack.stackSize = inv.addCompressed(stack, true);
            if (stack.stackSize > 0) {
                dropItemStack(stack);
            }
        }
        inv.markDirty();
        sendNetworkContentToWatchers();
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
        boolean changed = fluid ? handleNetworkFluidInteraction(player, stack.getItem())
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
        sendNetworkContentToWatchers();
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
        int remaining = inv.addCompressed(moving, true);
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
        remaining.stackSize = inv.addCompressed(remaining, true);
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

    private boolean handleNetworkFluidInteraction(EntityPlayer player, ItemIdentifier fluidContainer) {
        FluidStack target = SimpleServiceLocator.logisticsFluidManager
                .getFluidFromContainer(new ItemIdentifierStack(fluidContainer, 1));
        ItemStack cursor = player.inventory.getItemStack();
        if (target == null || cursor == null) {
            return false;
        }
        if (cursor.stackSize > 1) {
            return handleStackedFluidContainerInteraction(player, target, cursor);
        }
        ItemStack result = getNetworkFluidClickResult(target, cursor);
        if (result == cursor) {
            return false;
        }
        player.inventory.setItemStack(result);
        return true;
    }

    private boolean handleStackedFluidContainerInteraction(EntityPlayer player, FluidStack target, ItemStack cursor) {
        ItemStack remainder = cursor.copy();
        remainder.stackSize--;
        if (!canAddToPlayerInventory(player, remainder)) {
            return false;
        }
        ItemStack single = cursor.copy();
        single.stackSize = 1;
        ItemStack result = getNetworkFluidClickResult(target, single);
        if (result == single) {
            return false;
        }
        player.inventory.setItemStack(result);
        if (!player.inventory.addItemStackToInventory(remainder) && remainder.stackSize > 0) {
            player.dropPlayerItemWithRandomChoice(remainder, false);
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
     * Clears all fake crafting inputs.
     */
    public void clearCraftingGrid() {
        for (int i = 0; i < matrix.getSizeInventory(); i++) {
            matrix.setInventorySlotContents(i, (ItemStack) null);
        }
        cacheRecipe();
    }

    /**
     * Requests the missing fake crafting-grid ingredients for the given number of crafts.
     *
     * @param player     player requesting the ingredients
     * @param multiplier number of craft sets to request
     */
    public void requestCraftingIngredients(EntityPlayer player, int multiplier) {
        List<ItemIdentifierStack> request = getCraftingIngredientRequest(multiplier);
        if (!request.isEmpty()) {
            RequestHandler.requestList(player, request, this);
        }
    }

    private List<ItemIdentifierStack> getCraftingIngredientRequest(int multiplier) {
        Map<ItemIdentifier, Integer> requested = new HashMap<>();
        int clampedMultiplier = Math.max(1, multiplier);
        for (Entry<ItemIdentifier, Integer> entry : matrix.getItemsAndCount().entrySet()) {
            requested.put(entry.getKey(), entry.getValue() * clampedMultiplier);
        }
        for (Pair<ItemStack, Integer> entry : inv) {
            ItemStack stack = entry.getValue1();
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
        List<ItemIdentifierStack> result = new ArrayList<>(requested.size());
        for (Entry<ItemIdentifier, Integer> entry : requested.entrySet()) {
            if (entry.getValue() > 0) {
                result.add(entry.getKey().makeStack(entry.getValue()));
            }
        }
        return result;
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

    private IRecipe getCurrentRecipe() {
        cacheRecipe();
        if (resultInv.getStackInSlot(0) == null) {
            return null;
        }
        AutoCraftingInventory craftInv = new AutoCraftingInventory(null);
        for (int i = 0; i < 9; i++) {
            craftInv.setInventorySlotContents(i, matrix.getStackInSlot(i));
        }
        ItemIdentifier resultType = ItemIdentifier.get(resultInv.getStackInSlot(0));
        for (IRecipe recipe : CraftingUtil.getRecipeList()) {
            if (!recipe.matches(craftInv, getWorld())) {
                continue;
            }
            AutoCraftingInventory resultInvForRecipe = new AutoCraftingInventory(null);
            for (int i = 0; i < 9; i++) {
                resultInvForRecipe.setInventorySlotContents(i, matrix.getStackInSlot(i));
            }
            ItemStack result = recipe.getCraftingResult(resultInvForRecipe);
            if (result != null && resultType.equalsWithoutNBT(ItemIdentifier.get(result))) {
                return recipe;
            }
        }
        return null;
    }

    private IngredientUse[] findIngredients(EntityPlayer player, boolean oreDict) {
        IngredientUse[] uses = new IngredientUse[9];
        int[] usedInternal = new int[inv.getSizeInventory()];
        int[] usedPlayer = new int[player.inventory.mainInventory.length];
        for (int i = 0; i < 9; i++) {
            ItemStack ingredient = matrix.getStackInSlot(i);
            if (ingredient == null) {
                continue;
            }
            ItemIdentifier expected = ItemIdentifier.get(ingredient);
            IngredientUse use = findIngredientInInventory(expected, inv, usedInternal, oreDict);
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
        stack.stackSize = inv.addCompressed(stack, true);
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
            sendNetworkContentToWatchers();
        }
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
            sendNetworkContentToWatchers();
        }
    }

    @Override
    public TransportLayer getTransportLayer() {
        if (_transportLayer == null) {
            _transportLayer = new TransportLayer() {

                @Override
                public void handleItem(IRoutedItem item) {
                    RequestTablePipe.this.notifyOfItemArival(item.getInfo());
                    ItemIdentifierStack routedStack = item.getItemIdentifierStack();
                    if (routedStack == null) {
                        return;
                    }
                    FluidStack fluid = SimpleServiceLocator.logisticsFluidManager.getFluidFromContainer(routedStack);
                    if (fluid != null) {
                        updateStorageUpgrades();
                        int filled = fluidStorage.fill(fluid, true);
                        if (filled < fluid.amount) {
                            fluid.amount -= filled;
                            ItemStack leftover = SimpleServiceLocator.logisticsFluidManager.getFluidContainer(fluid)
                                    .makeNormalStack();
                            leftover.stackSize = inv.addCompressed(leftover, true);
                            if (leftover.stackSize > 0) {
                                ItemIdentifierInventory.dropItems(getWorld(), leftover, getX(), getY(), getZ());
                            }
                        }
                        routedStack.setStackSize(0);
                        sendNetworkContentToWatchers();
                    } else {
                        updateStorageUpgrades();
                        ItemStack stack = routedStack.makeNormalStack();
                        stack.stackSize = inv.addCompressed(stack, true);
                        if (stack.stackSize > 0) {
                            ItemIdentifierInventory.dropItems(getWorld(), stack, getX(), getY(), getZ());
                        }
                        routedStack.setStackSize(0);
                        sendNetworkContentToWatchers();
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
            fluidStorage.dropContents(getWorld(), getX(), getY(), getZ());
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        fluidStorage.readFromNBT(tag, NBT_FLUID_STORAGE);
        displaySettingsStore.readFromNBT(tag, NBT_DISPLAY_SETTINGS);
        updateStorageUpgrades();
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        updateStorageUpgrades();
        super.writeToNBT(tag);
        fluidStorage.writeToNBT(tag, NBT_FLUID_STORAGE);
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
