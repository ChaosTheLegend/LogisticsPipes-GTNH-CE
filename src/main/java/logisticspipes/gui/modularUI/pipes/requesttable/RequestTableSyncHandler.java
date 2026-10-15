package logisticspipes.gui.modularUI.pipes.requesttable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.value.sync.SyncHandler;

import logisticspipes.crafting.requesttable.RequestTableInteractions;
import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.crafting.requesttable.RequestTableView;
import logisticspipes.crafting.requesttable.network.RequestTableNetworkEntry;
import logisticspipes.crafting.requesttable.settings.RequestTableDisplaySettings;
import logisticspipes.crafting.requesttable.upgrade.RequestTableUpgradeBranch;
import logisticspipes.crafting.requesttable.upgrade.RequestTableUpgradeSession;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.packets.crafting.requesttable.RequestTableRefreshPacket;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.RequestHandler;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import lombok.Getter;

/**
 * Carries the request table terminal between client and server.
 * <p>
 * The server pushes the combined network list, a snapshot of the table storage (compressed stacks need integer counts,
 * so they cannot use slot packets) and the viewer's upgrade selection. The client sends the terminal's actions, which
 * the server applies with the same table methods as the legacy GUI.
 */
public class RequestTableSyncHandler extends SyncHandler<RequestTableSyncHandler> {

    /** Vanilla click modes forwarded for virtual storage slots. */
    public static final int MODE_PICKUP = 0;
    public static final int MODE_QUICK_MOVE = 1;
    public static final int MODE_SWAP = 2;
    public static final int MODE_CLONE = 3;
    public static final int MODE_THROW = 4;
    public static final int MODE_PICKUP_ALL = 6;

    private static final int SHIFT_CRAFT_LIMIT = 64;
    private static final int MAX_DRAG_SLOTS = 1024;
    private static final int MAX_CANDIDATE_SLOTS = 9;

    private static final int C_VIEW = 0;
    private static final int C_REFRESH = 1;
    private static final int C_SETTINGS = 2;
    private static final int C_NETWORK_CLICK = 3;
    private static final int C_SUBMIT = 4;
    private static final int C_INGREDIENTS = 5;
    private static final int C_CLEAR_GRID = 6;
    private static final int C_SEND_STORAGE = 7;
    private static final int C_STORAGE_CLICK = 8;
    private static final int C_STORAGE_DRAG = 9;
    private static final int C_FLUID_CLICK = 10;
    private static final int C_RESULT_CLICK = 11;
    private static final int C_RECIPE = 12;
    private static final int C_MOST_LIKELY = 13;
    private static final int C_UPGRADE_SELECT = 14;
    private static final int C_UPGRADE_START = 15;

    private static final int S_CONTENT = 100;
    private static final int S_STORAGE = 101;
    private static final int S_UPGRADES = 102;
    private static final int S_MOST_LIKELY = 103;

    @Getter
    private final RequestTablePipe table;
    @Getter
    private final RequestTableUpgradeSession upgrades;
    @Getter
    private RequestTableView view = RequestTableView.NETWORK;

    // region client state

    @Getter
    private List<RequestTableNetworkEntry> entries = Collections.emptyList();
    private final Map<ItemIdentifier, Integer> networkStock = new HashMap<>();
    @Getter
    private RequestTableDisplaySettings displaySettings = RequestTableDisplaySettings.DEFAULT;
    @Getter
    private boolean settingsReceived;
    /** Increases with every received network list. */
    @Getter
    private int contentRevision;
    @Getter
    private boolean inventoryReady;
    @Getter
    private int craftableAmount;
    private Consumer<List<Integer>> mostLikelyListener;

    // endregion

    // region server state

    private int sentContentRevision = Integer.MIN_VALUE;
    private final List<ItemStack> syncedItems = new ArrayList<>();
    private final List<FluidStack> syncedFluids = new ArrayList<>();
    private final ItemStack[] syncedCrafting = new ItemStack[9 + 1 + 36];
    private int syncedStackLimit = -1;
    private int syncedFluidCapacity = -1;
    private int syncedTiers = -1;
    private int syncedCraftable = -1;
    private boolean craftableReady;

    // endregion

    public RequestTableSyncHandler(RequestTablePipe table, RequestTableUpgradeSession upgrades) {
        this.table = table;
        this.upgrades = upgrades;
        allowC2S();
    }

    // region client accessors

    /** Complete crafting sets the network can deliver for the ghost grid, from the last received list. */
    public int getNetworkCraftingSets() {
        return table.getNetworkCraftingSets(networkStock);
    }

    /** Applies the display settings locally and saves them on the server. */
    public void setDisplaySettings(RequestTableDisplaySettings settings) {
        displaySettings = settings;
        NBTWriter.send(this, C_SETTINGS, settings);
    }

    // endregion

    // region client actions

    public void setView(RequestTableView newView) {
        view = newView;
        syncToServer(C_VIEW, buf -> buf.writeVarIntToBuffer(newView.ordinal()));
    }

    public void refresh() {
        syncToServer(C_REFRESH);
    }

    public void clickNetworkEntry(RequestTableNetworkEntry entry, int mouseButton, boolean shift, boolean cursor) {
        syncToServer(C_NETWORK_CLICK, buf -> {
            LPDataOutputStream output = new LPDataOutputStream(buf);
            output.writeBoolean(entry != null);
            if (entry != null) output.writeItemIdentifierStack(entry.stack());
            output.writeBoolean(entry != null && entry.fluid());
            output.writeByte(mouseButton);
            output.writeBoolean(shift);
            output.writeBoolean(cursor);
        });
    }

    public void submitRequest(RequestTableNetworkEntry entry, int amount) {
        syncToServer(C_SUBMIT, buf -> {
            LPDataOutputStream output = new LPDataOutputStream(buf);
            output.writeItemIdentifierStack(entry.stack().getItem().makeStack(amount));
            output.writeBoolean(entry.fluid());
        });
    }

    public void requestIngredients(int sets) {
        syncToServer(C_INGREDIENTS, buf -> buf.writeVarIntToBuffer(sets));
    }

    public void clearCraftingGrid() {
        table.clearCraftingGrid();
        syncToServer(C_CLEAR_GRID);
    }

    public void sendStorageToNetwork(boolean fluids) {
        syncToServer(C_SEND_STORAGE, buf -> buf.writeBoolean(fluids));
    }

    public void clickItemStorage(int slot, int mouseButton, int mode) {
        syncToServer(C_STORAGE_CLICK, buf -> {
            buf.writeVarIntToBuffer(slot);
            buf.writeByte(mouseButton);
            buf.writeByte(mode);
        });
    }

    public void dragIntoItemStorage(int mouseButton, List<Integer> slots) {
        syncToServer(C_STORAGE_DRAG, buf -> {
            buf.writeByte(mouseButton);
            buf.writeVarIntToBuffer(slots.size());
            for (int slot : slots) buf.writeVarIntToBuffer(slot);
        });
    }

    public void clickFluidStorage(int tank, int mouseButton) {
        syncToServer(C_FLUID_CLICK, buf -> {
            buf.writeVarIntToBuffer(tank);
            buf.writeByte(mouseButton);
        });
    }

    public void clickCraftingResult(int mouseButton, boolean shift) {
        syncToServer(C_RESULT_CLICK, buf -> {
            buf.writeByte(mouseButton);
            buf.writeBoolean(shift);
        });
    }

    public void setCraftingRecipe(ItemStack[] content) {
        syncToServer(C_RECIPE, buf -> {
            LPDataOutputStream output = new LPDataOutputStream(buf);
            output.writeByte(content.length);
            for (ItemStack stack : content) output.writeItemStack(stack);
        });
    }

    /** Asks the server which candidate of every ingredient slot the network most likely provides. */
    public void findMostLikelyCandidates(List<List<ItemIdentifierStack>> candidates, Consumer<List<Integer>> listener) {
        mostLikelyListener = listener;
        syncToServer(C_MOST_LIKELY, buf -> {
            LPDataOutputStream output = new LPDataOutputStream(buf);
            output.writeList(
                    candidates,
                    (stream, list) -> stream.writeList(list, LPDataOutputStream::writeItemIdentifierStack));
        });
    }

    public void selectUpgrade(RequestTableUpgradeBranch branch, int tier) {
        upgrades.awaitState();
        syncToServer(C_UPGRADE_SELECT, buf -> {
            buf.writeVarIntToBuffer(branch.ordinal());
            buf.writeVarIntToBuffer(tier);
        });
    }

    public void startUpgrade(boolean creative) {
        RequestTableUpgradeBranch branch = upgrades.getSelectedBranch();
        int tier = upgrades.getSelectedTier();
        upgrades.awaitState();
        syncToServer(C_UPGRADE_START, buf -> {
            buf.writeVarIntToBuffer(branch.ordinal());
            buf.writeVarIntToBuffer(tier);
            buf.writeBoolean(creative);
        });
    }

    // endregion

    // region client receive

    @Override
    public void readOnClient(int id, PacketBuffer buf) throws IOException {
        LPDataInputStream input = new LPDataInputStream(buf);
        switch (id) {
            case S_CONTENT -> readContent(input);
            case S_STORAGE -> readStorage(input);
            case S_UPGRADES -> upgrades.readState(input);
            case S_MOST_LIKELY -> {
                List<Integer> response = input.readList(LPDataInputStream::readInt);
                if (mostLikelyListener != null) mostLikelyListener.accept(response);
            }
            default -> {}
        }
    }

    private void readContent(LPDataInputStream input) throws IOException {
        int size = input.readInt();
        List<RequestTableNetworkEntry> received = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            boolean fluid = input.readBoolean();
            received.add(
                    new RequestTableNetworkEntry(
                            input.readItemIdentifierStack(),
                            fluid,
                            input.readInt(),
                            input.readInt(),
                            input.readBoolean()));
        }
        RequestTableDisplaySettings settings = RequestTableDisplaySettings.readFromNBT(input.readNBTTagCompound());
        entries = received;
        networkStock.clear();
        for (RequestTableNetworkEntry entry : received) {
            if (!entry.fluid()) networkStock.put(entry.stack().getItem(), Math.max(0, entry.networkAmount()));
        }
        // Only this viewer edits its settings, so later lists must not undo edits still on their way to the server.
        if (!settingsReceived) {
            displaySettings = settings;
            settingsReceived = true;
        }
        contentRevision++;
    }

    private void readStorage(LPDataInputStream input) throws IOException {
        int itemSlots = input.readInt();
        int stackLimit = input.readInt();
        int fluidCapacity = input.readInt();
        craftableAmount = input.readInt();
        table.applyItemUpgradeTiers(input.readInt(), input.readInt());
        table.applyFluidUpgradeTiers(input.readInt(), input.readInt());
        table.applySpecialUpgrades(input.readBoolean(), input.readBoolean());
        List<ItemStack> items = input.readList(LPDataInputStream::readItemStack);
        List<FluidStack> fluids = input.readList(LPDataInputStream::readFluidStack);
        if (items.size() != itemSlots) return;
        table.inv.setSizeInventory(itemSlots);
        table.inv.setInventoryStackLimit(stackLimit);
        for (int slot = 0; slot < itemSlots; slot++) {
            table.inv.setInventorySlotContents(slot, items.get(slot));
        }
        table.getFluidStorage().applySnapshot(fluidCapacity, fluids);
        inventoryReady = true;
    }

    // endregion

    // region server

    @Override
    public void detectAndSendChanges(boolean init) {
        if (getSyncManager().isClient()) return;
        table.updateStorageUpgrades();
        if (init || table.getNetworkContentRevision() != sentContentRevision) {
            sendContent();
        }
        syncStorage(init);
        if (init) upgrades.markStateDirty();
        if (upgrades.consumeStateChange()) {
            syncToClient(S_UPGRADES, buf -> upgrades.writeState(new LPDataOutputStream(buf)));
        }
    }

    private EntityPlayer player() {
        return getSyncManager().getPlayer();
    }

    private void sendContent() {
        sentContentRevision = table.getNetworkContentRevision();
        List<RequestTableNetworkEntry> list = RequestTableRefreshPacket.buildEntries(table);
        RequestTableDisplaySettings settings = table.getDisplaySettings(player());
        syncToClient(S_CONTENT, buf -> {
            LPDataOutputStream output = new LPDataOutputStream(buf);
            output.writeInt(list.size());
            for (RequestTableNetworkEntry entry : list) {
                output.writeBoolean(entry.fluid());
                output.writeItemIdentifierStack(entry.stack());
                output.writeInt(entry.networkAmount());
                output.writeInt(entry.internalAmount());
                output.writeBoolean(entry.craftable());
            }
            output.writeNBTTagCompound(NBTWriter.write(settings));
        });
    }

    /** Sends a complete storage snapshot whenever storage, upgrades or the craftable count changed. */
    private void syncStorage(boolean init) {
        EntityPlayer player = player();
        boolean storageChanged = init || syncedStackLimit != table.inv.getInventoryStackLimit()
                || syncedFluidCapacity != table.getFluidStorage().getSlotCapacity()
                || syncedItems.size() != table.inv.getSizeInventory()
                || syncedFluids.size() != table.getFluidStorage().getSizeInventory()
                || syncedTiers != tierHash();
        if (syncedItems.size() != table.inv.getSizeInventory()) {
            syncedItems.clear();
            for (int i = 0; i < table.inv.getSizeInventory(); i++) syncedItems.add(null);
        }
        if (syncedFluids.size() != table.getFluidStorage().getSizeInventory()) {
            syncedFluids.clear();
            for (int i = 0; i < table.getFluidStorage().getSizeInventory(); i++) syncedFluids.add(null);
        }
        for (int i = 0; i < syncedItems.size(); i++) {
            ItemStack current = table.inv.getStackInSlot(i);
            if (!ItemStack.areItemStacksEqual(syncedItems.get(i), current)) {
                syncedItems.set(i, current == null ? null : current.copy());
                storageChanged = true;
            }
        }
        for (int i = 0; i < syncedFluids.size(); i++) {
            FluidStack current = table.getFluidStorage().getFluid(i);
            FluidStack previous = syncedFluids.get(i);
            if (current == null ? previous != null : !current.isFluidStackIdentical(previous)) {
                syncedFluids.set(i, current == null ? null : current.copy());
                storageChanged = true;
            }
        }
        // Only table contents change the network list; the craftable count alone does not.
        boolean contentsChanged = storageChanged;
        boolean craftingChanged = !craftableReady || storageChanged;
        for (int i = 0; i < syncedCrafting.length; i++) {
            ItemStack current = i < 9 ? table.matrix.getStackInSlot(i)
                    : i == 9 ? table.resultInv.getStackInSlot(0) : player.inventory.mainInventory[i - 10];
            if (!ItemStack.areItemStacksEqual(syncedCrafting[i], current)) {
                syncedCrafting[i] = current == null ? null : current.copy();
                craftingChanged = true;
            }
        }
        if (craftingChanged) {
            craftableReady = true;
            int craftable = table.getCraftableAmount(player);
            storageChanged |= craftable != syncedCraftable;
            syncedCraftable = craftable;
        }
        if (!storageChanged) return;
        if (contentsChanged && !init) table.requestNetworkContentUpdate();
        syncedStackLimit = table.inv.getInventoryStackLimit();
        syncedFluidCapacity = table.getFluidStorage().getSlotCapacity();
        syncedTiers = tierHash();
        syncToClient(S_STORAGE, buf -> {
            LPDataOutputStream output = new LPDataOutputStream(buf);
            output.writeInt(table.inv.getSizeInventory());
            output.writeInt(table.inv.getInventoryStackLimit());
            output.writeInt(table.getFluidStorage().getSlotCapacity());
            output.writeInt(syncedCraftable);
            output.writeInt(table.getItemSlotTier());
            output.writeInt(table.getItemSizeTier());
            output.writeInt(table.getFluidSlotTier());
            output.writeInt(table.getFluidSizeTier());
            output.writeBoolean(table.isFluidEnabled());
            output.writeBoolean(table.hasMonitoringUpgrade());
            output.writeList(syncedItems, LPDataOutputStream::writeItemStack);
            output.writeList(syncedFluids, LPDataOutputStream::writeFluidStack);
        });
    }

    private int tierHash() {
        int hash = table.getItemSlotTier();
        hash = hash * 31 + table.getItemSizeTier();
        hash = hash * 31 + table.getFluidSlotTier();
        hash = hash * 31 + table.getFluidSizeTier();
        hash = hash * 2 + (table.isFluidEnabled() ? 1 : 0);
        return hash * 2 + (table.hasMonitoringUpgrade() ? 1 : 0);
    }

    @Override
    public void readOnServer(int id, PacketBuffer buf) throws IOException {
        EntityPlayer player = player();
        LPDataInputStream input = new LPDataInputStream(buf);
        switch (id) {
            case C_VIEW -> setViewOnServer(buf.readVarIntFromBuffer());
            case C_REFRESH -> sendContent();
            case C_SETTINGS -> table
                    .updateDisplaySettings(player, RequestTableDisplaySettings.readFromNBT(input.readNBTTagCompound()));
            case C_NETWORK_CLICK -> {
                ItemIdentifierStack stack = input.readBoolean() ? input.readItemIdentifierStack() : null;
                boolean fluid = input.readBoolean();
                int mouseButton = input.readByte();
                boolean shift = input.readBoolean();
                boolean cursor = input.readBoolean();
                table.handleNetworkEntryInteraction(player, stack, fluid, mouseButton, shift, cursor);
                updateCursor(player);
            }
            case C_SUBMIT -> {
                ItemIdentifierStack stack = input.readItemIdentifierStack();
                boolean fluid = input.readBoolean();
                submitOnServer(player, stack, fluid);
            }
            case C_INGREDIENTS -> {
                table.requestCraftingIngredients(player, Math.max(1, buf.readVarIntFromBuffer()));
                sendContent();
            }
            case C_CLEAR_GRID -> table.clearCraftingGrid();
            case C_SEND_STORAGE -> {
                if (buf.readBoolean()) table.sendStoredFluidsToNetwork();
                else table.sendStoredItemsToNetwork();
            }
            case C_STORAGE_CLICK -> {
                int slot = buf.readVarIntFromBuffer();
                int mouseButton = buf.readByte();
                int mode = buf.readByte();
                clickItemStorageOnServer(player, slot, mouseButton, mode);
                updateCursor(player);
            }
            case C_STORAGE_DRAG -> {
                int mouseButton = buf.readByte();
                int count = buf.readVarIntFromBuffer();
                if (count < 0 || count > MAX_DRAG_SLOTS) return;
                List<Integer> slots = new ArrayList<>(count);
                for (int i = 0; i < count; i++) slots.add(buf.readVarIntFromBuffer());
                dragIntoItemStorageOnServer(player, mouseButton, slots);
                updateCursor(player);
            }
            case C_FLUID_CLICK -> {
                int tank = buf.readVarIntFromBuffer();
                int mouseButton = buf.readByte();
                if (tank >= 0 && tank < table.getFluidStorage().getSizeInventory()
                        && (mouseButton == 0 || mouseButton == 1)) {
                    table.updateStorageUpgrades();
                    RequestTableInteractions.clickFluidStorage(table, tank, mouseButton, player);
                    updateCursor(player);
                }
            }
            case C_RESULT_CLICK -> {
                int mouseButton = buf.readByte();
                boolean shift = buf.readBoolean();
                clickResultOnServer(player, mouseButton, shift);
                updateCursor(player);
            }
            case C_RECIPE -> {
                int length = input.readUnsignedByte();
                if (length != table.matrix.getSizeInventory()) return;
                ItemStack[] content = new ItemStack[length];
                for (int i = 0; i < length; i++) {
                    content[i] = input.readItemStack();
                    if (content[i] != null) content[i].stackSize = 1;
                }
                table.handleNEIRecipePacket(content);
            }
            case C_MOST_LIKELY -> {
                List<List<ItemIdentifierStack>> candidates = input
                        .readList(stream -> stream.readList(LPDataInputStream::readItemIdentifierStack));
                if (candidates.size() > MAX_CANDIDATE_SLOTS) return;
                List<Integer> response = findMostLikely(candidates);
                syncToClient(
                        S_MOST_LIKELY,
                        out -> new LPDataOutputStream(out).writeList(response, LPDataOutputStream::writeInt));
            }
            case C_UPGRADE_SELECT -> {
                RequestTableUpgradeBranch branch = readBranch(buf.readVarIntFromBuffer());
                int tier = buf.readVarIntFromBuffer();
                upgrades.select(branch, tier);
                upgrades.markStateDirty();
            }
            case C_UPGRADE_START -> {
                RequestTableUpgradeBranch branch = readBranch(buf.readVarIntFromBuffer());
                int tier = buf.readVarIntFromBuffer();
                boolean creative = buf.readBoolean();
                if (branch != null && branch.isValidSelection(tier)
                        && view == RequestTableView.UPGRADES
                        && upgrades.canInteract(player)) {
                    upgrades.startUpgrade(branch, tier, creative);
                }
                // Reconcile rejected, stale and duplicate confirmations too.
                upgrades.markStateDirty();
            }
            default -> {}
        }
    }

    private static RequestTableUpgradeBranch readBranch(int ordinal) {
        RequestTableUpgradeBranch[] values = RequestTableUpgradeBranch.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    private void setViewOnServer(int ordinal) {
        RequestTableView[] values = RequestTableView.values();
        if (ordinal < 0 || ordinal >= values.length) return;
        RequestTableView next = values[ordinal];
        if (next == RequestTableView.FLUID_STORAGE && !table.isFluidEnabled()) next = RequestTableView.NETWORK;
        if (next == RequestTableView.CRAFTING_MONITOR && !table.hasMonitoringUpgrade()) next = RequestTableView.NETWORK;
        if (view == RequestTableView.UPGRADES && next != RequestTableView.UPGRADES) {
            // Unused materials return when leaving the upgrade board, like closing the legacy upgrade screen.
            upgrades.returnMaterials(player());
        }
        view = next;
    }

    /** Called when the GUI closes on the server. */
    public void onClosed(EntityPlayer player) {
        upgrades.returnMaterials(player);
    }

    private void submitOnServer(EntityPlayer player, ItemIdentifierStack stack, boolean fluid) {
        if (stack == null || stack.getStackSize() <= 0 || (fluid && !table.isFluidEnabled())) return;
        boolean messages = table.getDisplaySettings(player).isRequestMessagesEnabled();
        if (fluid) {
            RequestHandler.requestFluid(player, stack, table, table, messages);
        } else {
            RequestHandler.request(player, stack, table, messages);
        }
        sendContent();
    }

    private void clickResultOnServer(EntityPlayer player, int mouseButton, boolean shift) {
        if (shift) {
            table.craftIntoPlayerInventory(player, SHIFT_CRAFT_LIMIT);
            return;
        }
        if (mouseButton != 0 && mouseButton != 1) return;
        ItemStack crafted = table.getResultForClick(player, player.inventory.getItemStack(), 1);
        if (crafted != null) {
            player.inventory.setItemStack(crafted);
        }
    }

    private void clickItemStorageOnServer(EntityPlayer player, int index, int mouseButton, int mode) {
        table.updateStorageUpgrades();
        if (index < 0 || index >= table.inv.getSizeInventory()) return;
        Slot slot = new Slot(table.inv, index, 0, 0);
        ItemStack stored = slot.getStack();
        ItemStack cursor = player.inventory.getItemStack();
        switch (mode) {
            case MODE_PICKUP -> {
                if (mouseButton == 0 || mouseButton == 1) {
                    RequestTableInteractions.clickItemStorage(slot, mouseButton, player);
                }
            }
            case MODE_QUICK_MOVE -> {
                if (stored != null) moveStorageToPlayer(player, slot, stored);
            }
            case MODE_SWAP -> {
                if (mouseButton >= 0 && mouseButton < 9) swapWithHotbar(player, slot, stored, mouseButton);
            }
            case MODE_CLONE -> {
                if (player.capabilities.isCreativeMode && cursor == null && stored != null) {
                    ItemStack clone = stored.copy();
                    clone.stackSize = clone.getMaxStackSize();
                    player.inventory.setItemStack(clone);
                }
            }
            case MODE_THROW -> {
                if (cursor == null && stored != null) {
                    int amount = mouseButton == 1 ? Math.min(stored.stackSize, stored.getMaxStackSize()) : 1;
                    ItemStack taken = slot.decrStackSize(amount);
                    player.dropPlayerItemWithRandomChoice(taken, true);
                    slot.onSlotChanged();
                }
            }
            case MODE_PICKUP_ALL -> collectIntoCursor(player);
            default -> {}
        }
    }

    /** Merges one normal stack into the player inventory, hotbar first, like the legacy shift-click. */
    private void moveStorageToPlayer(EntityPlayer player, Slot slot, ItemStack stored) {
        ItemStack moving = stored.copy();
        moving.stackSize = Math.min(stored.stackSize, stored.getMaxStackSize());
        int amount = moving.stackSize;
        int[] order = new int[36];
        for (int i = 0; i < 9; i++) order[i] = 8 - i;
        for (int i = 0; i < 27; i++) order[9 + i] = 35 - i;
        ItemStack[] inventory = player.inventory.mainInventory;
        int limit = Math.min(moving.getMaxStackSize(), player.inventory.getInventoryStackLimit());
        for (int index : order) {
            ItemStack target = inventory[index];
            if (moving.stackSize <= 0) break;
            if (target != null && target.isItemEqual(moving) && ItemStack.areItemStackTagsEqual(target, moving)) {
                int room = Math.max(0, limit - target.stackSize);
                int moved = Math.min(room, moving.stackSize);
                target.stackSize += moved;
                moving.stackSize -= moved;
            }
        }
        for (int index : order) {
            if (moving.stackSize <= 0) break;
            if (inventory[index] == null) {
                inventory[index] = moving.splitStack(Math.min(limit, moving.stackSize));
            }
        }
        int moved = amount - moving.stackSize;
        if (moved <= 0) return;
        slot.decrStackSize(moved);
        slot.onSlotChanged();
        player.inventory.markDirty();
    }

    private void swapWithHotbar(EntityPlayer player, Slot slot, ItemStack stored, int hotbar) {
        ItemStack hotbarStack = player.inventory.getStackInSlot(hotbar);
        if (stored != null && stored.stackSize > stored.getMaxStackSize()) {
            // A compressed stack cannot move into a normal slot; take one normal stack instead.
            if (hotbarStack == null) {
                player.inventory.setInventorySlotContents(hotbar, slot.decrStackSize(stored.getMaxStackSize()));
                slot.onSlotChanged();
            }
            return;
        }
        if (hotbarStack != null && hotbarStack.stackSize > slot.getSlotStackLimit()) return;
        slot.putStack(hotbarStack == null ? null : hotbarStack.copy());
        player.inventory.setInventorySlotContents(hotbar, stored == null ? null : stored.copy());
        slot.onSlotChanged();
    }

    /** Vanilla double-click: fills the cursor from the table storage first, then from the player inventory. */
    private void collectIntoCursor(EntityPlayer player) {
        ItemStack cursor = player.inventory.getItemStack();
        if (cursor == null) return;
        int limit = cursor.getMaxStackSize();
        for (int i = 0; i < table.inv.getSizeInventory() && cursor.stackSize < limit; i++) {
            ItemStack stack = table.inv.getStackInSlot(i);
            if (stack == null || !stack.isItemEqual(cursor) || !ItemStack.areItemStackTagsEqual(stack, cursor))
                continue;
            ItemStack taken = table.inv.decrStackSize(i, Math.min(stack.stackSize, limit - cursor.stackSize));
            if (taken != null) cursor.stackSize += taken.stackSize;
        }
        ItemStack[] inventory = player.inventory.mainInventory;
        for (int i = 0; i < inventory.length && cursor.stackSize < limit; i++) {
            ItemStack stack = inventory[i];
            if (stack == null || !stack.isItemEqual(cursor) || !ItemStack.areItemStackTagsEqual(stack, cursor))
                continue;
            int moved = Math.min(stack.stackSize, limit - cursor.stackSize);
            cursor.stackSize += moved;
            stack.stackSize -= moved;
            if (stack.stackSize <= 0) inventory[i] = null;
        }
        table.inv.markDirty();
        player.inventory.markDirty();
    }

    /** Vanilla drag distribution over several storage slots: even split on left, one item per slot on right. */
    private void dragIntoItemStorageOnServer(EntityPlayer player, int mouseButton, List<Integer> indexes) {
        ItemStack cursor = player.inventory.getItemStack();
        if (cursor == null || (mouseButton != 0 && mouseButton != 1)) return;
        table.updateStorageUpgrades();
        List<Integer> targets = new ArrayList<>();
        for (int index : indexes) {
            if (index < 0 || index >= table.inv.getSizeInventory() || targets.contains(index)) continue;
            ItemStack stored = table.inv.getStackInSlot(index);
            if (stored == null || (stored.isItemEqual(cursor) && ItemStack.areItemStackTagsEqual(stored, cursor)
                    && stored.stackSize < stored.getMaxStackSize())) {
                targets.add(index);
            }
        }
        if (targets.isEmpty()) return;
        int each = mouseButton == 0 ? Math.max(1, cursor.stackSize / targets.size()) : 1;
        for (int index : targets) {
            if (cursor.stackSize <= 0) break;
            ItemStack stored = table.inv.getStackInSlot(index);
            int existing = stored == null ? 0 : stored.stackSize;
            int room = Math.min(cursor.getMaxStackSize(), table.inv.getInventoryStackLimit()) - existing;
            int moved = Math.min(Math.min(each, room), cursor.stackSize);
            if (moved <= 0) continue;
            ItemStack inserted = cursor.splitStack(moved);
            inserted.stackSize += existing;
            table.inv.setInventorySlotContents(index, inserted);
        }
        player.inventory.setItemStack(cursor.stackSize <= 0 ? null : cursor);
        table.inv.markDirty();
    }

    private List<Integer> findMostLikely(List<List<ItemIdentifierStack>> candidates) {
        List<Integer> result = new ArrayList<>(candidates.size());
        LinkedList<ItemIdentifier> craftable = null;
        for (List<ItemIdentifierStack> order : candidates) {
            int maxItemPos = -1;
            int max = 0;
            for (int i = 0; i < order.size(); i++) {
                ItemIdentifier ident = order.get(i).getItem();
                int amount = SimpleServiceLocator.logisticsManager
                        .getAmountFor(ident, table.getRouter().getIRoutersByCost());
                if (amount > max) {
                    max = amount;
                    maxItemPos = i;
                }
            }
            if (max < 64) {
                if (craftable == null) {
                    craftable = SimpleServiceLocator.logisticsManager
                            .getCraftableItems(table.getRouter().getIRoutersByCost());
                }
                for (ItemIdentifier craft : craftable) {
                    for (int i = 0; i < order.size(); i++) {
                        if (craft == order.get(i).getItem()) {
                            maxItemPos = i;
                            break;
                        }
                    }
                }
            }
            result.add(maxItemPos);
        }
        return result;
    }

    private static void updateCursor(EntityPlayer player) {
        if (player instanceof EntityPlayerMP playerMP) {
            playerMP.updateHeldItem();
        }
    }

    // endregion

    /** Display settings travel as NBT, like the legacy packets. */
    private static final class NBTWriter {

        private static net.minecraft.nbt.NBTTagCompound write(RequestTableDisplaySettings settings) {
            net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
            settings.writeToNBT(tag);
            return tag;
        }

        private static void send(RequestTableSyncHandler handler, int id, RequestTableDisplaySettings settings) {
            handler.syncToServer(id, buf -> new LPDataOutputStream(buf).writeNBTTagCompound(write(settings)));
        }
    }
}
