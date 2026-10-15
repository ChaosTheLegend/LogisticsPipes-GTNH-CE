package logisticspipes.crafting.requesttable;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import org.jetbrains.annotations.NotNull;

import logisticspipes.LogisticsPipes;
import logisticspipes.crafting.requesttable.gui.RequestTableLayout;
import logisticspipes.crafting.requesttable.storage.RequestTableFluidSlot;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.crafting.requesttable.RequestTableInventoryPacket;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.gui.DummyContainer;
import logisticspipes.utils.gui.DummySlot;
import logisticspipes.utils.gui.HandelableSlot;
import logisticspipes.utils.item.ItemIdentifierStack;
import lombok.Getter;

/**
 * Container for the new request table.
 * <p>
 * Storage slots follow the server's inventory sizes and are moved by the client layout. Slots that belong to an
 * inactive scrollable view are moved far outside the GUI so Minecraft cannot render or click them.
 */
public class RequestTableContainer extends DummyContainer {

    private static final int HIDDEN = -5000;
    private static final int SHIFT_CRAFT_LIMIT = 64;

    @Getter
    private final RequestTablePipe table;
    private final List<Slot> itemStorageSlots = new ArrayList<>();
    private final List<Slot> fluidStorageSlots = new ArrayList<>();
    private final List<Slot> craftingSlots = new ArrayList<>();
    private final List<Slot> playerSlots = new ArrayList<>();
    private final List<FluidStack> syncedFluids = new ArrayList<>();
    private final Slot resultSlot;
    private final EntityPlayer player;
    private int playerSlotStart;
    private int playerSlotEnd;
    @Getter
    private int craftableAmount;
    private boolean craftingCountReady;
    private int syncedItemStackLimit;
    private int syncedFluidSlotCapacity;
    private int syncedItemSlotTier = -1;
    private int syncedItemSizeTier = -1;
    private int syncedFluidSlotTier = -1;
    private int syncedFluidSizeTier = -1;
    private boolean syncedFluidController;
    private boolean syncedMonitoring;
    private ItemStack syncedCursor;
    private boolean syncRequired = true;
    @Getter
    private boolean inventoryReady;
    private RequestTableView view = RequestTableView.NETWORK;

    /**
     * Creates the complete slot set for the request table.
     *
     * @param player player opening the GUI
     * @param table  backing request table pipe
     */
    public RequestTableContainer(EntityPlayer player, RequestTablePipe table) {
        super(player, table.matrix, table);
        this.table = table;
        this.player = player;
        this.table.updateStorageUpgrades();

        for (int i = 0; i < table.inv.getSizeInventory(); i++) {
            int slotIndex = inventorySlots.size();
            addNormalSlot(i, table.inv, HIDDEN, HIDDEN);
            itemStorageSlots.add(inventorySlots.get(slotIndex));
        }
        for (int i = 0; i < table.getFluidStorage().getSizeInventory(); i++) {
            fluidStorageSlots.add(addSlotToContainer(new RequestTableFluidSlot(table, i, HIDDEN, HIDDEN)));
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
            playerSlots.add(inventorySlots.get(i));
        }
    }

    /** Waits for a fresh layout after an interaction that can resize the storage slots. */
    public void awaitInventory() {
        inventoryReady = false;
    }

    @Override
    public void addCraftingToCrafters(ICrafting crafter) {
        if (crafters.contains(crafter)) {
            throw new IllegalArgumentException("Listener already listening");
        }
        crafters.add(crafter);
        syncRequired = true;
        detectAndSendChanges();
    }

    @NotNull
    static ItemStack getItemStack(FluidStack held, int accepted) {
        int remaining = held.amount - accepted;
        if (remaining <= 0) {
            return new ItemStack(LogisticsPipes.LogisticsFluidContainer, 1);
        }
        FluidStack leftover = held.copy();
        leftover.amount = remaining;
        return SimpleServiceLocator.logisticsFluidManager.getFluidContainer(leftover).makeNormalStack();
    }

    /** Vanilla packets use byte counts and may arrive before the server's slot layout. */
    @Override
    public void putStackInSlot(int slot, ItemStack stack) {}

    @Override
    public void putStacksInSlots(ItemStack[] stacks) {}

    /** Applies a complete server snapshot after establishing the server's slot indexes. */
    public void applyInventory(int itemSlots, int itemStackLimit, int fluidSlotCapacity, int craftableAmount,
            List<ItemStack> contents, List<FluidStack> fluids) {
        this.craftableAmount = Math.max(0, craftableAmount);
        table.inv.setSizeInventory(itemSlots);
        table.inv.setInventoryStackLimit(itemStackLimit);
        table.getFluidStorage().applySnapshot(fluidSlotCapacity, fluids);
        if (itemStorageSlots.size() != itemSlots || fluidStorageSlots.size() != fluids.size()) {
            rebuildStorageSlots();
        }
        for (int i = 0; i < inventorySlots.size(); i++) {
            Slot slot = inventorySlots.get(i);
            if (slot.inventory != table.getFluidStorage()) {
                slot.putStack(contents.get(i));
            }
        }
        inventoryReady = true;
    }

    private void rebuildStorageSlots() {
        inventorySlots.clear();
        inventoryItemStacks.clear();
        inventoryFuzzySlotsContent.clear();
        itemStorageSlots.clear();
        fluidStorageSlots.clear();
        for (int i = 0; i < table.inv.getSizeInventory(); i++) {
            itemStorageSlots.add(addSlotToContainer(new Slot(table.inv, i, HIDDEN, HIDDEN)));
        }
        for (int i = 0; i < table.getFluidStorage().getSizeInventory(); i++) {
            fluidStorageSlots.add(addSlotToContainer(new RequestTableFluidSlot(table, i, HIDDEN, HIDDEN)));
        }
        for (Slot slot : craftingSlots) {
            addSlotToContainer(slot);
        }
        addSlotToContainer(resultSlot);
        playerSlotStart = inventorySlots.size();
        for (Slot slot : playerSlots) {
            addSlotToContainer(slot);
        }
        playerSlotEnd = inventorySlots.size();
    }

    static FluidStack getFluidStack(ItemStack stack) {
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

    private void layoutStorageSlots(List<Slot> slots, RequestTableLayout layout, int columns, int scrollRow) {
        int left = layout.panelLeft + 1;
        int top = layout.panelTop + 1 - scrollRow * RequestTableLayout.SLOT;
        int storageTop = layout.panelTop + 1;
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
            int y = layout.getCraftingGridTop() + (i / 3) * RequestTableLayout.SLOT;
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
        try {
            ItemStack result = handleSlotClick(slotId, mouseButton, mode, player);
            // Vanilla click acknowledgements also encode the returned stack count as a signed byte.
            if (result != null && result.stackSize > 127) {
                result = result.copy();
                result.stackSize = 127;
            }
            return result;
        } finally {
            // Reconcile even rejected clicks and vanilla transaction recovery snapshots.
            syncRequired = true;
        }
    }

    private ItemStack handleSlotClick(int slotId, int mouseButton, int mode, EntityPlayer player) {
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
            Slot slot = inventorySlots.get(slotId);
            if (itemStorageSlots.contains(slot) && mode == 0 && (mouseButton == 0 || mouseButton == 1)) {
                return handleItemStorageClick(slot, mouseButton, player);
            }
            if (itemStorageSlots.contains(slot) && slot.getHasStack()
                    && slot.getStack().stackSize > slot.getStack().getMaxStackSize()) {
                if (mode == 2 && mouseButton >= 0 && mouseButton < 9) {
                    // A hotbar swap cannot move a compressed stack into a normal player slot.
                    if (player.inventory.getStackInSlot(mouseButton) == null) {
                        ItemStack taken = slot.decrStackSize(slot.getStack().getMaxStackSize());
                        player.inventory.setInventorySlotContents(mouseButton, taken);
                        slot.onPickupFromSlot(player, taken);
                        slot.onSlotChanged();
                    }
                    return null;
                }
                if (mode == 4 && mouseButton == 1 && player.inventory.getItemStack() == null) {
                    ItemStack taken = slot.decrStackSize(slot.getStack().getMaxStackSize());
                    player.dropPlayerItemWithRandomChoice(taken, true);
                    slot.onPickupFromSlot(player, taken);
                    slot.onSlotChanged();
                    return null;
                }
            }
            if (fluidStorageSlots.contains(slot) && mode == 0 && (mouseButton == 0 || mouseButton == 1)) {
                ItemStack held = ItemStack.copyItemStack(player.inventory.getItemStack());
                if (MainProxy.isServer(player.worldObj)) {
                    handleFluidStorageClick(slot.getSlotIndex(), mouseButton, player);
                }
                return held;
            }
        }
        return super.slotClick(slotId, mouseButton, mode, player);
    }

    private ItemStack handleItemStorageClick(Slot slot, int mouseButton, EntityPlayer player) {
        return RequestTableInteractions.clickItemStorage(slot, mouseButton, player);
    }

    @Override
    public boolean canDragIntoSlot(Slot slot) {
        return super.canDragIntoSlot(slot) && (!itemStorageSlots.contains(slot) || !slot.getHasStack()
                || slot.getStack().stackSize < slot.getStack().getMaxStackSize());
    }

    @Override
    public void detectAndSendChanges() {
        if (MainProxy.isClient(player.worldObj)) {
            return;
        }
        table.updateStorageUpgrades();
        if (itemStorageSlots.size() != table.inv.getSizeInventory()
                || fluidStorageSlots.size() != table.getFluidStorage().getSizeInventory()) {
            rebuildStorageSlots();
            syncRequired = true;
        }
        boolean storageChanged = syncedItemStackLimit != table.inv.getInventoryStackLimit()
                || syncedFluidSlotCapacity != table.getFluidStorage().getSlotCapacity()
                || syncedFluids.size() != fluidStorageSlots.size();
        if (syncedFluids.size() != fluidStorageSlots.size()) {
            syncedFluids.clear();
            for (int i = 0; i < fluidStorageSlots.size(); i++) {
                syncedFluids.add(null);
            }
        }
        for (int i = 0; i < syncedFluids.size(); i++) {
            FluidStack current = table.getFluidStorage().getFluid(i);
            FluidStack previous = syncedFluids.get(i);
            if (current == null ? previous != null : !current.isFluidStackIdentical(previous)) {
                syncedFluids.set(i, current);
                storageChanged = true;
            }
        }
        boolean craftingChanged = !craftingCountReady;
        for (int i = 0; i < inventorySlots.size(); i++) {
            Slot slot = inventorySlots.get(i);
            if (slot.inventory == table.getFluidStorage()) {
                continue;
            }
            ItemStack current = slot.getStack();
            if (!ItemStack.areItemStacksEqual(inventoryItemStacks.get(i), current)) {
                inventoryItemStacks.set(i, current == null ? null : current.copy());
                syncRequired = true;
                craftingChanged |= slot.inventory == table.inv || slot.inventory == table.matrix
                        || slot.inventory == table.resultInv
                        || slot.inventory == player.inventory;
                storageChanged |= i < itemStorageSlots.size() + fluidStorageSlots.size();
            }
        }
        if (craftingChanged) {
            craftableAmount = table.getCraftableAmount(player);
            craftingCountReady = true;
            syncRequired = true;
        }
        ItemStack cursor = player.inventory.getItemStack();
        syncRequired |= storageChanged || !ItemStack.areItemStacksEqual(syncedCursor, cursor)
                || syncedItemSlotTier != table.getItemSlotTier()
                || syncedItemSizeTier != table.getItemSizeTier()
                || syncedFluidSlotTier != table.getFluidSlotTier()
                || syncedFluidSizeTier != table.getFluidSizeTier()
                || syncedFluidController != table.isFluidEnabled()
                || syncedMonitoring != table.hasMonitoringUpgrade();
        if (storageChanged) {
            table.container.markDirty();
            table.requestNetworkContentUpdate();
        }
        if (!syncRequired || crafters.isEmpty()) {
            return;
        }
        syncedItemStackLimit = table.inv.getInventoryStackLimit();
        syncedFluidSlotCapacity = table.getFluidStorage().getSlotCapacity();
        syncedItemSlotTier = table.getItemSlotTier();
        syncedItemSizeTier = table.getItemSizeTier();
        syncedFluidSlotTier = table.getFluidSlotTier();
        syncedFluidSizeTier = table.getFluidSizeTier();
        syncedFluidController = table.isFluidEnabled();
        syncedMonitoring = table.hasMonitoringUpgrade();
        syncedCursor = cursor == null ? null : cursor.copy();
        for (ICrafting crafter : crafters) {
            if (crafter instanceof EntityPlayer viewer) {
                MainProxy.sendPacketToPlayer(
                        PacketHandler.getPacket(RequestTableInventoryPacket.class).setInventory(this, viewer),
                        viewer);
            }
        }
        syncRequired = false;
    }

    /** Applies a player-inventory shift-click batch, then sends one authoritative inventory snapshot. */
    public void shiftPlayerStacks(EntityPlayer player, List<Integer> inventorySlots, RequestTableView view) {
        if (MainProxy.isClient(player.worldObj) || this.player != player
                || view == null
                || inventorySlots.isEmpty()
                || inventorySlots.size() > playerSlots.size()) {
            return;
        }
        this.view = view;
        table.updateStorageUpgrades();
        boolean[] processed = new boolean[player.inventory.mainInventory.length];
        for (int inventorySlot : inventorySlots) {
            if (inventorySlot < 0 || inventorySlot >= processed.length || processed[inventorySlot]) continue;
            processed[inventorySlot] = true;
            for (Slot slot : playerSlots) {
                if (slot.getSlotIndex() == inventorySlot) {
                    if (slot.getHasStack() && slot.canTakeStack(player)) transferPlayerStack(player, slot, view);
                    break;
                }
            }
        }
        syncRequired = true;
        detectAndSendChanges();
    }

    /**
     * Moves the client-side slots into their current adaptive positions.
     */
    public void layout(RequestTableLayout layout, RequestTableView view, int storageScrollRow) {
        this.view = view;
        hide(itemStorageSlots);
        hide(fluidStorageSlots);
        switch (view) {
            case ITEM_STORAGE -> layoutStorageSlots(
                    itemStorageSlots,
                    layout,
                    RequestTableLayout.INVENTORY_COLUMNS,
                    storageScrollRow);
            case FLUID_STORAGE -> {
                if (table.isFluidEnabled()) {
                    layoutStorageSlots(
                            fluidStorageSlots,
                            layout,
                            RequestTableLayout.INVENTORY_COLUMNS,
                            storageScrollRow);
                }
            }
            case NETWORK, CRAFTING_MONITOR -> {}
        }
        if (view == RequestTableView.CRAFTING_MONITOR) {
            hide(craftingSlots);
            hide(playerSlots);
            move(resultSlot, HIDDEN, HIDDEN);
        } else {
            layoutCrafting(layout);
            layoutPlayer(layout);
        }
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= inventorySlots.size()) {
            return null;
        }
        Slot slot = inventorySlots.get(slotIndex);
        if (slot == null || !slot.getHasStack() || !slot.canTakeStack(player)) {
            return null;
        }
        if (itemStorageSlots.contains(slot)) {
            return transferStackToRange(player, slot, playerSlotStart, playerSlotEnd);
        }
        if (slotIndex >= playerSlotStart && slotIndex < playerSlotEnd) {
            return transferPlayerStack(player, slot, view);
        }
        return null;
    }

    private void handleFluidStorageClick(int fluidSlot, int mouseButton, EntityPlayer player) {
        RequestTableInteractions.clickFluidStorage(table, fluidSlot, mouseButton, player);
    }

    private ItemStack transferPlayerStack(EntityPlayer player, Slot slot, RequestTableView view) {
        return RequestTableInteractions.transferPlayerStack(table, player, slot, view);
    }

    private ItemStack transferStackToRange(EntityPlayer player, Slot sourceSlot, int start, int end) {
        ItemStack source = sourceSlot.getStack();
        ItemStack original = source.copy();
        ItemStack moving = source.copy();
        moving.stackSize = Math.min(source.stackSize, source.getMaxStackSize());
        int amount = moving.stackSize;
        if (!mergeItemStack(moving, start, end, true)) {
            return null;
        }
        int moved = amount - moving.stackSize;
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
