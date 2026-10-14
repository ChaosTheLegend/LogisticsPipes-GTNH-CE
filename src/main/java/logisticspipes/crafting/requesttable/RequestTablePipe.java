package logisticspipes.crafting.requesttable;

import static logisticspipes.crafting.requesttable.RequestTableContainer.getFluidStack;
import static logisticspipes.crafting.requesttable.RequestTableContainer.getItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
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
import logisticspipes.crafting.requesttable.network.RequestTableNetworkEntry;
import logisticspipes.crafting.requesttable.settings.RequestTableDisplaySettings;
import logisticspipes.crafting.requesttable.settings.RequestTableDisplaySettingsStore;
import logisticspipes.crafting.requesttable.settings.RequestTableOrderSettings;
import logisticspipes.crafting.requesttable.storage.RequestTableFluidContainers;
import logisticspipes.crafting.requesttable.storage.RequestTableFluidStorage;
import logisticspipes.crafting.requesttable.upgrade.RequestTableStorageUpgradeConfig;
import logisticspipes.crafting.requesttable.upgrade.RequestTableUpgradeBranch;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.items.ItemUpgrade;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.logisticspipes.TransportLayer;
import logisticspipes.network.GuiIDs;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.crafting.requesttable.RequestTableContentPacket;
import logisticspipes.network.packets.crafting.requesttable.RequestTableRefreshPacket;
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
public class RequestTablePipe extends PipeBlockRequestTable implements IRequestFluid {

    private static final int FLUID_SEND_CHUNK = 5000;
    private static final String NBT_FLUID_STORAGE = "newRequestTableFluidStorage";
    private static final String NBT_DISPLAY_SETTINGS = "newRequestTableDisplaySettings";
    private static final String NBT_FLUID_UPGRADE = "newRequestTableFluidUpgrade";
    private static final String NBT_ITEM_UPGRADES = "requestTableItemUpgrades";
    private static final String NBT_FLUID_TIERS = "requestTableFluidUpgrades";
    private static final String NBT_SPECIAL_UPGRADES = "requestTableSpecialUpgrades";
    private final RequestTableFluidStorage fluidStorage = RequestTableFluidStorage.createDefault();
    private final RequestTableDisplaySettingsStore displaySettingsStore = new RequestTableDisplaySettingsStore();
    private final PlayerCollectionList requestTableGuiWatchers = new PlayerCollectionList();
    private boolean networkContentDirty;
    private int itemSlotTier;
    private int itemSizeTier;
    private int fluidSlotTier;
    private int fluidSizeTier;
    private boolean fluidControllerUnlocked;
    private boolean monitoringUnlocked;

    /**
     * Creates the new request table pipe item.
     *
     * @param item backing pipe item
     */
    public RequestTablePipe(Item item) {
        super(item);
        inv.addListener(this);
        fluidStorage.addListener(this);
        updateStorageUpgrades();
    }

    public static void addStorageUpgradeTooltip(ItemStack stack, List<String> tooltip) {
        if (!stack.hasTagCompound()) return;
        if (!stack.getTagCompound().hasKey(NBT_ITEM_UPGRADES) && !stack.getTagCompound().hasKey(NBT_FLUID_TIERS)
                && !stack.getTagCompound().hasKey(NBT_SPECIAL_UPGRADES))
            return;
        NBTTagCompound upgrades = stack.getTagCompound().getCompoundTag(NBT_ITEM_UPGRADES);
        tooltip.add("Item slots: " + RequestTableStorageUpgradeConfig.getTierName(upgrades.getInteger("slotTier")));
        tooltip.add("Slot size: " + RequestTableStorageUpgradeConfig.getTierName(upgrades.getInteger("sizeTier")));
        NBTTagCompound fluids = stack.getTagCompound().getCompoundTag(NBT_FLUID_TIERS);
        tooltip.add("Fluid tanks: " + RequestTableStorageUpgradeConfig.getTierName(fluids.getInteger("slotTier")));
        tooltip.add("Tank capacity: " + RequestTableStorageUpgradeConfig.getTierName(fluids.getInteger("sizeTier")));
        NBTTagCompound special = stack.getTagCompound().getCompoundTag(NBT_SPECIAL_UPGRADES);
        if (special.getBoolean("fluidController")) tooltip.add("Fluid controller: unlocked");
        if (special.getBoolean("monitoring")) tooltip.add("Crafting monitor: unlocked");
    }

    /** Coalesces storage changes into one network-list refresh per server tick. */
    public void requestNetworkContentUpdate() {
        networkContentDirty = true;
    }

    @Override
    public void ignoreDisableUpdateEntity() {
        super.ignoreDisableUpdateEntity();
        if (MainProxy.isServer(getWorld()) && networkContentDirty) {
            networkContentDirty = false;
            sendNetworkContentToWatchers();
        }
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
        return displaySettingsStore.get(player.getUniqueID())
                .withOrderSettings(RequestTableOrderSettings.forPlayer(player));
    }

    /**
     * Saves the view state for this player at this table.
     */
    public void setDisplaySettings(EntityPlayer player, RequestTableDisplaySettings settings) {
        boolean orderChanged = !RequestTableOrderSettings.forPlayer(player).equals(settings.getOrderSettings());
        if (orderChanged) settings.getOrderSettings().saveForPlayer(player);
        boolean displayChanged = displaySettingsStore
                .set(player.getUniqueID(), settings.withOrderSettings(RequestTableOrderSettings.DEFAULT));
        if ((displayChanged || orderChanged) && container != null) {
            if (displayChanged) container.markDirty();
            sendNetworkContentToPlayer(player);
        }
    }

    private static boolean isControllerCard(ItemStack stack, int damage) {
        return stack != null && stack.stackSize > 0
                && stack.getItem() == LogisticsPipes.UpgradeItem
                && stack.getItemDamage() == damage;
    }

    @Override
    public void InventoryChanged(IInventory inventory) {
        super.InventoryChanged(inventory);
        if ((inventory == inv || inventory == fluidStorage) && container != null
                && getWorld() != null
                && MainProxy.isServer(getWorld())) {
            container.markDirty();
            requestNetworkContentUpdate();
        }
    }

    /** Request-table upgrades are purchased permanently through the motherboard. */
    public boolean isUpgradeAllowed() {
        return false;
    }

    public boolean isFluidEnabled() {
        return fluidControllerUnlocked;
    }

    public int getUpgradeTier(RequestTableUpgradeBranch branch) {
        return switch (branch) {
            case SLOT_COUNT -> itemSlotTier;
            case SLOT_SIZE -> itemSizeTier;
            case FLUID_SLOT_COUNT -> fluidSlotTier;
            case FLUID_SLOT_SIZE -> fluidSizeTier;
            case FLUID_CONTROLLER -> isFluidEnabled() ? 1 : 0;
            case CRAFTING_MONITORING -> hasMonitoringUpgrade() ? 1 : 0;
        };
    }

    public boolean hasMonitoringUpgrade() {
        return monitoringUnlocked;
    }

    public void applySpecialUpgrades(boolean fluidController, boolean monitoring) {
        fluidControllerUnlocked = fluidController;
        monitoringUnlocked = monitoring;
    }

    /** The server validates a one-time controller purchase before its card is consumed. */
    public boolean unlockSpecialUpgrade(RequestTableUpgradeBranch branch) {
        if (branch == null || !branch.isSpecial()
                || getWorld() == null
                || MainProxy.isClient(getWorld())
                || getUpgradeTier(branch) > 0)
            return false;
        if (branch == RequestTableUpgradeBranch.FLUID_CONTROLLER) fluidControllerUnlocked = true;
        else monitoringUnlocked = true;
        container.markDirty();
        requestNetworkContentUpdate();
        return true;
    }

    public void applyItemUpgradeTiers(int slots, int size) {
        itemSlotTier = Math.max(0, Math.min(RequestTableStorageUpgradeConfig.TIER_COUNT, slots));
        itemSizeTier = Math.max(0, Math.min(RequestTableStorageUpgradeConfig.TIER_COUNT, size));
    }

    public void applyFluidUpgradeTiers(int slots, int size) {
        fluidSlotTier = Math.max(0, Math.min(RequestTableStorageUpgradeConfig.TIER_COUNT, slots));
        fluidSizeTier = Math.max(0, Math.min(RequestTableStorageUpgradeConfig.TIER_COUNT, size));
    }

    /** Called only after the upgrade container has validated and reserved the required materials. */
    public boolean unlockStorageUpgrade(RequestTableUpgradeBranch branch, int tier) {
        if (branch == null || branch.isSpecial()
                || !RequestTableStorageUpgradeConfig.isValidTier(tier)
                || getWorld() == null
                || MainProxy.isClient(getWorld())
                || getUpgradeTier(branch) + 1 != tier
                || (branch.isFluid() && !isFluidEnabled()))
            return false;
        switch (branch) {
            case SLOT_COUNT -> itemSlotTier = tier;
            case SLOT_SIZE -> itemSizeTier = tier;
            case FLUID_SLOT_COUNT -> fluidSlotTier = tier;
            case FLUID_SLOT_SIZE -> fluidSizeTier = tier;
            default -> {
                return false;
            }
        }
        updateStorageUpgrades();
        container.markDirty();
        requestNetworkContentUpdate();
        return true;
    }

    public ItemStack makeTableDrop(int damage) {
        ItemStack stack = new ItemStack(item, 1, damage);
        if (itemSlotTier > 0 || itemSizeTier > 0
                || fluidSlotTier > 0
                || fluidSizeTier > 0
                || fluidControllerUnlocked
                || monitoringUnlocked) {
            NBTTagCompound data = new NBTTagCompound();
            writeStorageUpgradeData(data);
            stack.setTagCompound(data);
        }
        return stack;
    }

    public void restoreStorageUpgrades(ItemStack stack) {
        if (stack != null && stack.hasTagCompound()) readStorageUpgradeData(stack.getTagCompound());
        updateStorageUpgrades();
    }

    private void writeStorageUpgradeData(NBTTagCompound tag) {
        NBTTagCompound upgrades = new NBTTagCompound();
        upgrades.setInteger("slotTier", itemSlotTier);
        upgrades.setInteger("sizeTier", itemSizeTier);
        tag.setTag(NBT_ITEM_UPGRADES, upgrades);
        NBTTagCompound fluids = new NBTTagCompound();
        fluids.setInteger("slotTier", fluidSlotTier);
        fluids.setInteger("sizeTier", fluidSizeTier);
        tag.setTag(NBT_FLUID_TIERS, fluids);
        NBTTagCompound special = new NBTTagCompound();
        special.setBoolean("fluidController", fluidControllerUnlocked);
        special.setBoolean("monitoring", monitoringUnlocked);
        tag.setTag(NBT_SPECIAL_UPGRADES, special);
    }

    private void readStorageUpgradeData(NBTTagCompound tag) {
        NBTTagCompound upgrades = tag.getCompoundTag(NBT_ITEM_UPGRADES);
        applyItemUpgradeTiers(upgrades.getInteger("slotTier"), upgrades.getInteger("sizeTier"));
        NBTTagCompound fluids = tag.getCompoundTag(NBT_FLUID_TIERS);
        applyFluidUpgradeTiers(fluids.getInteger("slotTier"), fluids.getInteger("sizeTier"));
        NBTTagCompound special = tag.getCompoundTag(NBT_SPECIAL_UPGRADES);
        applySpecialUpgrades(special.getBoolean("fluidController"), special.getBoolean("monitoring"));
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
        requestNetworkContentUpdate();
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
            if (stack != null && Objects.requireNonNull(ItemIdentifier.get(stack)).equals(item)) {
                amount += stack.stackSize;
            }
        }
        return amount;
    }

    /** Applies installed request-table storage upgrades to the item and fluid backing stores. */
    public void updateStorageUpgrades() {
        if (container == null || getWorld() == null || MainProxy.isClient(getWorld())) return;

        RequestTableStorageUpgradeConfig itemUpgrades = RequestTableStorageUpgradeConfig.getConfigured();
        int itemSlotCount = itemUpgrades.getTotal(RequestTableUpgradeBranch.SLOT_COUNT, itemSlotTier);
        int itemStackLimit = itemUpgrades.getTotal(RequestTableUpgradeBranch.SLOT_SIZE, itemSizeTier);
        adjustItemStorageForUpgrades(itemSlotCount, itemStackLimit);

        RequestTableStorageUpgradeConfig fluidUpgrades = RequestTableStorageUpgradeConfig.getConfigured(true);
        int fluidSlotCount = fluidUpgrades.getTotal(RequestTableUpgradeBranch.FLUID_SLOT_COUNT, fluidSlotTier);
        int fluidSlotCapacity = fluidUpgrades.getTotal(RequestTableUpgradeBranch.FLUID_SLOT_SIZE, fluidSizeTier);
        boolean fluidStorageChanged = fluidStorage.getSizeInventory() != fluidSlotCount
                || fluidStorage.getSlotCapacity() != fluidSlotCapacity;
        if (getWorld() != null)
            fluidStorage.resize(fluidSlotCount, fluidSlotCapacity, getWorld(), getX(), getY(), getZ());
        if (fluidStorageChanged) {
            requestNetworkContentUpdate();
        }
    }

    /**
     * Handles a click on a network-grid entry when the click should manipulate internal storage instead of opening a
     * request dialog.
     *
     * @param player            player using the GUI
     * @param stack             clicked entry stack, or null for an empty grid cell
     * @param fluid             whether the entry represents a fluid
     * @param mouseButton       mouse button sent by the GUI
     * @param shift             whether shift was held
     * @param cursorInteraction whether the click is intended to transfer the held stack
     */
    public void handleNetworkEntryInteraction(EntityPlayer player, ItemIdentifierStack stack, boolean fluid,
            int mouseButton, boolean shift, boolean cursorInteraction) {
        if (mouseButton < 0 || mouseButton > 1 || (fluid && !isFluidEnabled())) {
            return;
        }
        updateStorageUpgrades();
        boolean changed;
        if (cursorInteraction) {
            ItemStack cursor = player.inventory.getItemStack();
            if (cursor == null || cursor.stackSize <= 0) {
                return;
            }
            if (isFluidEnabled() && isFilledFluidContainer(cursor)) {
                changed = RequestTableFluidContainers
                        .interact(player.inventory, mouseButton, stageEmptyFluidContainers());
            } else if (fluid && stack != null && isEmptyFluidContainer(cursor)) {
                changed = handleNetworkFluidInteraction(player, stack.getItem(), mouseButton);
            } else {
                changed = player.openContainer instanceof RequestTableContainer
                        && ((RequestTableContainer) player.openContainer).insertCursorItem(player, mouseButton);
            }
        } else {
            if (stack == null || player.inventory.getItemStack() != null) {
                return;
            }
            changed = !fluid && handleNetworkItemInteraction(player, stack.getItem(), mouseButton, shift);
        }
        if (!changed) {
            return;
        }
        inv.markDirty();
        fluidStorage.markDirty();
        player.inventory.markDirty();
        if (player.openContainer != null) {
            player.openContainer.detectAndSendChanges();
        }
        requestNetworkContentUpdate();
    }

    private boolean handleNetworkItemInteraction(EntityPlayer player, ItemIdentifier item, int mouseButton,
            boolean shift) {
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

    private boolean moveInternalItemToCursor(EntityPlayer player, ItemIdentifier item, int amount) {
        if (amount <= 0) {
            return false;
        }
        ItemStack cursor = player.inventory.getItemStack();
        int room;
        if (cursor == null) {
            room = item.getMaxStackSize();
        } else if (Objects.requireNonNull(ItemIdentifier.get(cursor)).equals(item)) {
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
            if (stack == null || !Objects.requireNonNull(ItemIdentifier.get(stack)).equals(item)) {
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
        if (!isFluidEnabled()) {
            return false;
        }
        FluidStack target = SimpleServiceLocator.logisticsFluidManager
                .getFluidFromContainer(new ItemIdentifierStack(fluidContainer, 1));
        ItemStack cursor = player.inventory.getItemStack();
        if (target == null || cursor == null) {
            return false;
        }
        return RequestTableFluidContainers.interact(
                player.inventory,
                mouseButton,
                RequestTableFluidContainers.stage(
                        fluidStorage,
                        (storage, single) -> fillContainerFromInternal(storage, FluidIdentifier.get(target), single)));
    }

    boolean isFilledFluidContainer(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        ItemStack single = stack.copy();
        single.stackSize = 1;
        FluidStack fluid = getContainedFluid(single);
        return fluid != null && fluid.amount > 0;
    }

    void emptyFluidContainers(EntityPlayer player, int inventorySlot) {
        if (!isFluidEnabled()) {
            return;
        }
        RequestTableFluidContainers.shiftClick(player.inventory, inventorySlot, this::stageEmptyFluidContainers);
    }

    private RequestTableFluidContainers.Transfer stageEmptyFluidContainers() {
        return RequestTableFluidContainers.stage(fluidStorage, (storage, single) -> {
            FluidStack held = getContainedFluid(single);
            return held == null ? single : emptyContainerIntoInternal(storage, single, held);
        });
    }

    private boolean isEmptyFluidContainer(ItemStack stack) {
        return stack.getItem() instanceof IFluidContainerItem
                || stack.getItem() == LogisticsPipes.LogisticsFluidContainer
                || FluidContainerRegistry.isEmptyContainer(stack);
    }

    private ItemStack fillContainerFromInternal(RequestTableFluidStorage storage, FluidIdentifier target,
            ItemStack cursor) {
        if (cursor.getItem() instanceof IFluidContainerItem container) {
            ItemStack filled = cursor.copy();
            boolean changed = false;
            for (int slot = 0; slot < storage.getSizeInventory(); slot++) {
                FluidStack stored = storage.getFluid(slot);
                if (stored == null || !target.equals(FluidIdentifier.get(stored))) {
                    continue;
                }
                int fillable = container.fill(filled, stored.copy(), false);
                if (fillable <= 0) {
                    continue;
                }
                FluidStack drained = storage.drain(slot, fillable, true);
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
            int room = storage.getSlotCapacity() - (held == null ? 0 : held.amount);
            if (room <= 0) {
                return cursor;
            }
            FluidStack collected = null;
            for (int slot = 0; slot < storage.getSizeInventory() && room > 0; slot++) {
                FluidStack stored = storage.getFluid(slot);
                if (stored == null || !target.equals(FluidIdentifier.get(stored))) {
                    continue;
                }
                FluidStack drained = storage.drain(slot, room, true);
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
        for (int slot = 0; slot < storage.getSizeInventory(); slot++) {
            FluidStack stored = storage.getFluid(slot);
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
            storage.drain(slot, filledFluid.amount, true);
            return filled;
        }
        return cursor;
    }

    private ItemStack emptyContainerIntoInternal(RequestTableFluidStorage storage, ItemStack cursor, FluidStack held) {
        if (cursor.getItem() instanceof IFluidContainerItem container) {
            ItemStack drainedContainer = cursor.copy();
            int accepted = storage.fill(held, false);
            if (accepted <= 0) {
                return cursor;
            }
            FluidStack drained = container.drain(drainedContainer, accepted, true);
            if (drained == null || drained.amount <= 0) {
                return cursor;
            }
            storage.fill(drained, true);
            return drainedContainer;
        }
        if (cursor.getItem() == LogisticsPipes.LogisticsFluidContainer) {
            int accepted = storage.fill(held, false);
            if (accepted <= 0) {
                return cursor;
            }
            FluidStack inserted = held.copy();
            inserted.amount = accepted;
            storage.fill(inserted, true);
            return getItemStack(held, accepted);
        }
        int accepted = storage.fill(held, false);
        if (accepted < held.amount) {
            return cursor;
        }
        ItemStack empty = FluidContainerRegistry.drainFluidContainer(cursor);
        if (empty == null) {
            return cursor;
        }
        storage.fill(held, true);
        return empty;
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
     */
    public void craftIntoPlayerInventory(EntityPlayer player, int requestedAmount) {
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
     * Requests complete ingredient sets without subtracting existing table or player stock.
     *
     * @param player     player requesting the ingredients
     * @param multiplier number of craft sets to request
     */
    public void requestCraftingIngredients(EntityPlayer player, int multiplier) {
        List<ItemIdentifierStack> request = getCraftingIngredientRequest(multiplier);
        if (!request.isEmpty()) {
            RequestHandler.requestList(player, request, this, getDisplaySettings(player).isRequestMessagesEnabled());
        }
    }

    private List<ItemIdentifierStack> getCraftingIngredientRequest(int multiplier) {
        var requirements = RequestTableCraftingIngredients.requirements(matrix);
        Map<ItemIdentifier, Integer> network = SimpleServiceLocator.logisticsManager
                .getAvailableItems(getRouter().getIRoutersByCost());
        List<ItemIdentifierStack> result = new ArrayList<>();
        for (var entry : requirements.entrySet()) {
            int remaining = entry.getValue().amount(multiplier);
            // Request the actual stored variant, retaining its NBT and damage for LP's exact routing identity.
            for (var available : network.entrySet()) {
                if (remaining == 0) break;
                if (!entry.getKey().equals(RequestTableCraftingIngredients.key(available.getKey()))) continue;
                int amount = Math.min(remaining, Math.max(0, available.getValue()));
                if (amount == 0) continue;
                result.add(available.getKey().makeStack(amount));
                remaining -= amount;
            }
            if (remaining > 0) result.add(entry.getValue().example.makeStack(remaining));
        }
        return result;
    }

    /** Counts complete requestable sets from network stock, with returned tools needed only once. */
    public int getNetworkCraftingSets(Map<ItemIdentifier, Integer> networkStock) {
        var requirements = RequestTableCraftingIngredients.requirements(matrix);
        Map<ItemIdentifier, Long> stock = new HashMap<>();
        networkStock.forEach((item, amount) -> RequestTableCraftingIngredients.addStock(stock, item, amount));
        return RequestTableCraftingIngredients.maxSets(requirements, stock);
    }

    private FluidStack getContainedFluid(ItemStack stack) {
        return getFluidStack(stack);
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
        if (previewResult == null || !Objects.requireNonNull(ItemIdentifier.get(previewResult))
                .equalsWithoutNBT(Objects.requireNonNull(ItemIdentifier.get(resultInv.getStackInSlot(0))))) {
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

        List<ItemStack> displacedTools = new ArrayList<>();
        RequestTableCraftingSlot craftingSlot = new RequestTableCraftingSlot(
                player,
                consumed,
                resultInv,
                (slot, tool) -> returnCraftingTool(preview.uses[slot], tool, displacedTools));
        craftingSlot.onPickupFromSlot(player, result);
        displacedTools.forEach(stack -> storeCraftingRemainder(stack, player));
        collectCraftingRemainders(consumed, player);
        inv.markDirty();
        player.inventory.markDirty();
        return result;
    }

    private void returnCraftingTool(IngredientUse source, ItemStack tool, List<ItemStack> displacedTools) {
        ItemStack stored = source.inventory.getStackInSlot(source.slot);
        // Compressed table slots can contain several identical tools. Keep the worn tool here and
        // relocate the unused stack when its unchanged durability prevents merging it with the return.
        if (stored != null && !canMerge(stored, tool)
                && RequestTableCraftingIngredients.isReusable(stored)
                && RequestTableCraftingIngredients.key(ItemIdentifier.get(stored))
                        .equals(RequestTableCraftingIngredients.key(ItemIdentifier.get(tool)))) {
            displacedTools.add(stored);
            stored = null;
            source.inventory.setInventorySlotContents(source.slot, null);
        } else if (stored != null && !canMerge(stored, tool)) {
            displacedTools.add(tool);
            return;
        }
        int limit = source.inventory == inv ? inv.getInventoryStackLimit()
                : Math.min(tool.getMaxStackSize(), source.inventory.getInventoryStackLimit());
        int existing = stored == null ? 0 : stored.stackSize;
        int accepted = Math.min(tool.stackSize, Math.max(0, limit - existing));
        if (accepted > 0) {
            ItemStack returned = tool.copy();
            returned.stackSize = existing + accepted;
            source.inventory.setInventorySlotContents(source.slot, returned);
            tool.stackSize -= accepted;
        }
        if (tool.stackSize > 0) displacedTools.add(tool);
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
            if (result != null) {
                assert resultType != null;
                if (resultType.equalsWithoutNBT(Objects.requireNonNull(ItemIdentifier.get(result)))) {
                    return recipe;
                }
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
        if (RequestTableCraftingIngredients.key(expected).equals(RequestTableCraftingIngredients.key(candidate))) {
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
            requestNetworkContentUpdate();
        }
    }

    /**
     * Sends all stored fluids that can find a fluid sink back into the logistics network.
     */
    public void sendStoredFluidsToNetwork() {
        if (!isFluidEnabled()) {
            return;
        }
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
            requestNetworkContentUpdate();
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
                    if (fluid != null && isFluidEnabled()) {
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
                        requestNetworkContentUpdate();
                    } else {
                        updateStorageUpgrades();
                        ItemStack stack = routedStack.makeNormalStack();
                        stack.stackSize = inv.addCompressed(stack, true);
                        if (stack.stackSize > 0) {
                            ItemIdentifierInventory.dropItems(getWorld(), stack, getX(), getY(), getZ());
                        }
                        routedStack.setStackSize(0);
                        requestNetworkContentUpdate();
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

    /** Counts output items from table storage followed by this player's main inventory, including the hotbar. */
    public int getCraftableAmount(EntityPlayer player) {
        IRecipe recipe = getCurrentRecipe();
        ItemStack expectedResult = resultInv.getStackInSlot(0);
        if (recipe == null || expectedResult == null) {
            return 0;
        }
        int internalSlots = inv.getSizeInventory();
        ItemStack[] stored = new ItemStack[internalSlots + player.inventory.mainInventory.length];
        ItemStack[] pattern = new ItemStack[matrix.getSizeInventory()];
        for (int i = 0; i < internalSlots; i++) {
            stored[i] = inv.getStackInSlot(i);
        }
        System.arraycopy(
                player.inventory.mainInventory,
                0,
                stored,
                internalSlots,
                player.inventory.mainInventory.length);
        for (int i = 0; i < pattern.length; i++) {
            pattern[i] = matrix.getStackInSlot(i);
        }
        return RequestTableCraftingCounter.count(
                stored,
                pattern,
                (expected, candidate) -> matchesIngredient(
                        Objects.requireNonNull(ItemIdentifier.get(expected)),
                        ItemIdentifier.get(candidate),
                        true),
                (expected, candidate) -> matchesIngredient(
                        Objects.requireNonNull(ItemIdentifier.get(expected)),
                        ItemIdentifier.get(candidate),
                        false),
                RequestTableCraftingIngredients::isReusable,
                inputs -> {
                    AutoCraftingInventory preview = new AutoCraftingInventory(null);
                    for (int i = 0; i < inputs.length; i++) {
                        preview.setInventorySlotContents(i, inputs[i]);
                    }
                    if (!recipe.matches(preview, getWorld())) {
                        return null;
                    }
                    ItemStack result = recipe.getCraftingResult(preview);
                    return result != null && Objects.requireNonNull(ItemIdentifier.get(expectedResult))
                            .equalsWithoutNBT(Objects.requireNonNull(ItemIdentifier.get(result))) ? result : null;
                });
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        readStorageUpgradeData(tag);
        fluidStorage.readFromNBT(tag, NBT_FLUID_STORAGE);
        migrateControllerCards(tag);
        displaySettingsStore.readFromNBT(tag, NBT_DISPLAY_SETTINGS);
        updateStorageUpgrades();
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        updateStorageUpgrades();
        super.writeToNBT(tag);
        writeStorageUpgradeData(tag);
        fluidStorage.writeToNBT(tag, NBT_FLUID_STORAGE);
        tag.removeTag(NBT_FLUID_UPGRADE + "items");
        tag.removeTag(NBT_FLUID_UPGRADE + "itemsCount");
        displaySettingsStore.writeToNBT(tag, NBT_DISPLAY_SETTINGS);
    }

    /** Convert cards from older table saves into unlocks so stored fluids remain accessible. */
    private void migrateControllerCards(NBTTagCompound tag) {
        SimpleStackInventory legacyFluid = new SimpleStackInventory(1, "Legacy fluid upgrade", 1);
        legacyFluid.readFromNBT(tag, NBT_FLUID_UPGRADE);
        ItemStack fluidCard = legacyFluid.getStackInSlot(0);
        if (isControllerCard(fluidCard, ItemUpgrade.LIQUID_CRAFTING)) fluidControllerUnlocked = true;
        SimpleStackInventory legacyUpgrades = getOriginalUpgradeManager().getInv();
        for (int slot = 0; !monitoringUnlocked && slot < legacyUpgrades.getSizeInventory(); slot++) {
            if (isControllerCard(legacyUpgrades.getStackInSlot(slot), ItemUpgrade.CRAFTING_MONITORING)) {
                monitoringUnlocked = true;
                // Older monitor slots could contain a stack: consume one and retain any extra cards for normal drops.
                legacyUpgrades.decrStackSize(slot, 1);
                legacyUpgrades.markDirty();
            }
        }
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
