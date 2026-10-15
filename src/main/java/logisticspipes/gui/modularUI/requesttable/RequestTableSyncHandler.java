package logisticspipes.gui.modularUI.requesttable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;

import logisticspipes.crafting.requesttable.RequestTableView;
import logisticspipes.crafting.requesttable.mk3.RequestTableMk3Content;
import logisticspipes.crafting.requesttable.mk3.RequestTableMk3DisplaySettings;
import logisticspipes.crafting.requesttable.mk3.RequestTableMk3DisplaySettings.DeliveryMode;
import logisticspipes.crafting.requesttable.mk3.RequestTableMk3DisplaySettings.DropMode;
import logisticspipes.crafting.requesttable.mk3.RequestTableMk3NetworkEntry;
import logisticspipes.crafting.requesttable.mk3.RequestTableMk3NetworkList;
import logisticspipes.crafting.requesttable.mk3.RequestTableMk3Pipe;
import logisticspipes.crafting.requesttable.mk3.RequestTableMk3PlayerTarget;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.request.RequestHandler;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTree.ActiveRequestType;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * Carries the network list of the request table to the client and the clicks on it back to the server.
 * <p>
 * The server sends the list when the GUI opens, when the client asks for it and whenever the table reports a change of
 * its content. Several changes within a tick are sent once.
 */
public class RequestTableSyncHandler extends SyncHandler<RequestTableSyncHandler> {

    private static final int C_REFRESH = 1;
    private static final int C_INTERACT = 2;
    private static final int C_SETTINGS = 3;
    private static final int C_REQUEST = 4;
    private static final int C_SEND_STORAGE = 5;
    private static final int C_CRAFT = 6;
    private static final int C_CLEAR_CRAFTING = 7;
    private static final int C_REQUEST_INGREDIENTS = 8;
    private static final int C_SET_RECIPE = 9;
    private static final int C_SAVE_SEARCH = 10;
    private static final int C_VIEW = 11;
    private static final int C_AUTOCRAFT = 12;

    private static final int SHIFT_CRAFT_LIMIT = 64;
    private static final int MAX_INGREDIENT_SETS = 999;

    private static final int MAX_REQUEST_AMOUNT = 999_999_999;
    private static final int MAX_UNSENT_REPORTED = 64;
    private static final int FOLLOW_INTERVAL = 20;

    private static final int S_CONTENT = 100;
    private static final int S_RECIPE = 101;
    private static final int S_CONTENT_CHANGES = 102;
    private static final int S_UNSENT = 103;
    private static final int S_AUTOCRAFT_DONE = 104;
    private static final int S_MISSING = 105;
    private static final int MAX_AUTOCRAFTS = 64;
    // how long the slots of the missing ingredients stay marked
    private static final long MISSING_SHOWN_MILLIS = 2000;

    // the least ticks between two updates of the list
    private static final int MIN_SEND_INTERVAL = 10;

    private final RequestTableMk3Pipe table;

    // client: the received list, sorted and filtered for display
    private final RequestTableMk3NetworkList list = new RequestTableMk3NetworkList();
    private RequestTableMk3DisplaySettings settings = RequestTableMk3DisplaySettings.DEFAULT;

    // client: the search text the table remembered for the player, and what to do once it is known
    private String savedSearch = "";
    private Runnable onOpened;
    private boolean opened;

    // both sides: what the upper part of the window shows for the player
    private RequestTableView shownView = RequestTableView.NETWORK;

    // client: takes a recipe from NEI and lets the player settle its ingredients
    private Consumer<ItemStack[][]> recipeOffer;
    // client: is told what the network had no place for
    private Consumer<List<ItemIdentifierStack>> unsentListener;
    // client: the slots of the crafting grid the last craft had no ingredient for, a bit for each, and until when
    // they are marked
    private int missingSlots;
    private long missingShownUntil;
    // client: the auto crafts the server hasn't answered yet, by their number. They are waited for by another thread.
    private final Map<Integer, CompletableFuture<Integer>> pendingAutoCrafts = new ConcurrentHashMap<>();
    private final AtomicInteger nextAutoCraft = new AtomicInteger();

    // client: every entry the server sent, at the position the server knows it by. Entries that are gone stay here
    // with nothing in them.
    private final List<RequestTableMk3NetworkEntry> received = new ArrayList<>();

    // server: the content revision of the table that was sent last
    private int sentRevision;
    private long lastSendTime;
    // server: the position each entry was sent at, and its amounts and craftable flag as the client knows them
    private final Map<ItemIdentifier, Integer> sentIndex = new HashMap<>();
    private final List<int[]> sentValues = new ArrayList<>();
    // server: the player who has the GUI open
    private EntityPlayer viewer;
    // server: the recipe of the crafting grid that was sent last
    private final ItemIdentifier[] sentRecipe = new ItemIdentifier[9];

    public RequestTableSyncHandler(RequestTableMk3Pipe table) {
        this.table = table;
        allowC2S();
    }

    @Override
    public void init(String key, PanelSyncManager syncManager) {
        super.init(key, syncManager);
        if (!syncManager.isClient()) {
            viewer = syncManager.getPlayer();
            table.addGuiViewer(viewer);
        }
    }

    @Override
    public void dispose() {
        if (viewer != null) {
            table.removeGuiViewer(viewer);
            viewer = null;
        }
        super.dispose();
    }

    // region client

    public RequestTableMk3NetworkList getList() {
        return list;
    }

    public RequestTableMk3DisplaySettings getSettings() {
        return settings;
    }

    public String getSavedSearch() {
        return savedSearch;
    }

    /**
     * @param onOpened runs on the client once the settings of the player and the remembered search text arrived
     */
    public void setOnOpened(Runnable onOpened) {
        this.onOpened = onOpened;
    }

    /**
     * Lets the table remember the search text for the player.
     */
    public void saveSearch(String text) {
        savedSearch = text;
        syncToServer(C_SAVE_SEARCH, buf -> buf.writeStringToBuffer(RequestTableMk3DisplaySettings.limitSearch(text)));
    }

    public RequestTableView getShownView() {
        return shownView;
    }

    /**
     * Announces an auto craft, so its answer can be waited for.
     *
     * @return the number of the request, for {@link #sendAutoCraft} and {@link #awaitAutoCraft}
     */
    public int newAutoCraft() {
        int request = nextAutoCraft.incrementAndGet();
        pendingAutoCrafts.put(request, new CompletableFuture<>());
        return request;
    }

    /**
     * Asks the server to craft a recipe from what is at hand. To be called on the client thread.
     *
     * @param grid   the recipe, nine stacks row by row
     * @param crafts how often to craft it
     */
    public void sendAutoCraft(int request, ItemStack[] grid, int crafts) {
        syncToServer(C_AUTOCRAFT, buf -> {
            buf.writeVarIntToBuffer(request);
            for (int i = 0; i < 9; i++) {
                buf.writeItemStackToBuffer(grid[i]);
            }
            buf.writeVarIntToBuffer(crafts);
        });
    }

    /**
     * Waits for the answer of the server. Blocks, so it must not be called on the client thread.
     *
     * @return how often the server crafted the recipe, 0 if it didn't answer in time
     */
    public int awaitAutoCraft(int request, long timeoutMillis) {
        CompletableFuture<Integer> answer = pendingAutoCrafts.get(request);
        if (answer == null) {
            return 0;
        }
        try {
            return answer.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (ExecutionException | TimeoutException e) {
            return 0;
        } finally {
            pendingAutoCrafts.remove(request);
        }
    }

    /**
     * Tells the server what the player looks at, a shift click depends on it.
     */
    public void setShownView(RequestTableView view) {
        shownView = view;
        syncToServer(C_VIEW, buf -> buf.writeByte(view.ordinal()));
    }

    /**
     * Asks the server for a fresh list.
     */
    public void refresh() {
        syncToServer(C_REFRESH);
    }

    /**
     * A click on an entry without a request: it moves items or fluid between the cursor and the storage of the table.
     */
    public void interact(RequestTableMk3NetworkEntry entry, int mouseButton, boolean shift) {
        interact(entry.getStack(), entry.isFluid(), mouseButton, shift);
    }

    /**
     * The same for an item that may not be in the list, like the one on the cursor.
     */
    public void interact(ItemIdentifierStack stack, boolean fluid, int mouseButton, boolean shift) {
        syncToServer(C_INTERACT, buf -> {
            new LPDataOutputStream(buf).writeItemIdentifierStack(stack);
            buf.writeBoolean(fluid);
            buf.writeByte(mouseButton);
            buf.writeBoolean(shift);
        });
    }

    /**
     * Requests that amount of the entry from the network, it is delivered into the storage of the table.
     *
     * @param craft true to have it crafted even if the network has it
     */
    public void request(RequestTableMk3NetworkEntry entry, int amount, boolean craft) {
        syncToServer(C_REQUEST, buf -> {
            new LPDataOutputStream(buf).writeItemIdentifierStack(entry.getStack());
            buf.writeBoolean(entry.isFluid());
            buf.writeInt(amount);
            buf.writeBoolean(craft);
        });
    }

    /**
     * Sends everything in the item or the fluid storage of the table back into the network.
     */
    public void sendStorage(boolean fluids) {
        syncToServer(C_SEND_STORAGE, buf -> buf.writeBoolean(fluids));
    }

    /**
     * Crafts what the crafting grid shows: one onto the cursor, or with shift a stack into the inventory.
     */
    public void craft(boolean shift) {
        missingSlots = 0;
        syncToServer(C_CRAFT, buf -> buf.writeBoolean(shift));
    }

    /**
     * @return whether the last craft failed for want of the ingredient of that slot of the crafting grid
     */
    public boolean isIngredientMissing(int slot) {
        return (missingSlots & 1 << slot) != 0 && System.currentTimeMillis() < missingShownUntil;
    }

    public void setUnsentListener(Consumer<List<ItemIdentifierStack>> unsentListener) {
        this.unsentListener = unsentListener;
    }

    /**
     * Server: tells the player what the network had no place for.
     *
     * @param stacks items with their amount, or fluid containers with the amount in millibuckets
     */
    public void reportUnsent(List<ItemIdentifierStack> stacks) {
        if (stacks.isEmpty()) {
            return;
        }
        // a full storage can hold many kinds, the window shows a few and counts the rest
        List<ItemIdentifierStack> sent = stacks.size() > MAX_UNSENT_REPORTED ? stacks.subList(0, MAX_UNSENT_REPORTED)
                : stacks;
        syncToClient(S_UNSENT, buf -> {
            LPDataOutputStream data = new LPDataOutputStream(buf);
            buf.writeVarIntToBuffer(sent.size());
            for (ItemIdentifierStack stack : sent) {
                data.writeItemIdentifierStack(stack);
            }
        });
    }

    public void reportUnsent(ItemStack stack) {
        if (stack != null && stack.stackSize > 0) {
            reportUnsent(Collections.singletonList(ItemIdentifierStack.getFromStack(stack)));
        }
    }

    public void setRecipeOffer(Consumer<ItemStack[][]> recipeOffer) {
        this.recipeOffer = recipeOffer;
    }

    /**
     * Takes a recipe from NEI: for each of the nine slots of the grid the items it can hold, or null.
     */
    public void offerRecipe(ItemStack[][] candidates) {
        if (recipeOffer != null) {
            recipeOffer.accept(candidates);
            return;
        }
        ItemStack[] grid = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            if (candidates[i] != null && candidates[i].length > 0) {
                grid[i] = candidates[i][0];
            }
        }
        setRecipe(grid);
    }

    /**
     * Puts a recipe into the crafting grid, nine stacks row by row.
     */
    public void setRecipe(ItemStack[] grid) {
        syncToServer(C_SET_RECIPE, buf -> {
            for (int i = 0; i < 9; i++) {
                buf.writeItemStackToBuffer(grid[i]);
            }
        });
    }

    public void clearCraftingGrid() {
        table.clearCraftingGrid();
        syncToServer(C_CLEAR_CRAFTING);
    }

    /**
     * Requests the ingredients the crafting grid is missing for that many crafts.
     */
    public void requestIngredients(int sets) {
        syncToServer(C_REQUEST_INGREDIENTS, buf -> buf.writeVarIntToBuffer(sets));
    }

    public void setSettings(RequestTableMk3DisplaySettings newSettings) {
        settings = newSettings;
        list.setDisplaySettings(newSettings);
        syncToServer(C_SETTINGS, buf -> writeSettings(buf, newSettings));
    }

    @Override
    public void readOnClient(int id, PacketBuffer buf) throws IOException {
        if (id == S_AUTOCRAFT_DONE) {
            CompletableFuture<Integer> answer = pendingAutoCrafts.get(buf.readVarIntFromBuffer());
            int made = buf.readVarIntFromBuffer();
            if (answer != null) {
                answer.complete(made);
            }
            return;
        }
        if (id == S_MISSING) {
            missingSlots = buf.readVarIntFromBuffer();
            missingShownUntil = System.currentTimeMillis() + MISSING_SHOWN_MILLIS;
            return;
        }
        if (id == S_UNSENT) {
            LPDataInputStream data = new LPDataInputStream(buf);
            int count = buf.readVarIntFromBuffer();
            List<ItemIdentifierStack> stacks = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                stacks.add(data.readItemIdentifierStack());
            }
            if (unsentListener != null) {
                unsentListener.accept(stacks);
            }
            return;
        }
        if (id == S_RECIPE) {
            for (int i = 0; i < 9; i++) {
                table.matrix.setInventorySlotContents(i, buf.readItemStackFromBuffer());
            }
            table.cacheRecipe();
            return;
        }
        if (id != S_CONTENT && id != S_CONTENT_CHANGES) {
            return;
        }
        LPDataInputStream data = new LPDataInputStream(buf);
        if (id == S_CONTENT) {
            received.clear();
        } else {
            // the amounts of entries the client already has: no item has to be read for them
            int changed = buf.readVarIntFromBuffer();
            for (int i = 0; i < changed; i++) {
                int index = buf.readVarIntFromBuffer();
                int networkAmount = buf.readInt();
                int internalAmount = buf.readInt();
                boolean craftable = buf.readBoolean();
                if (index < received.size()) {
                    RequestTableMk3NetworkEntry old = received.get(index);
                    received.set(
                            index,
                            new RequestTableMk3NetworkEntry(
                                    old.getStack(),
                                    old.isFluid(),
                                    networkAmount,
                                    internalAmount,
                                    craftable));
                }
            }
        }
        int added = buf.readVarIntFromBuffer();
        for (int i = 0; i < added; i++) {
            ItemIdentifierStack stack = data.readItemIdentifierStack();
            boolean fluid = buf.readBoolean();
            int networkAmount = buf.readInt();
            int internalAmount = buf.readInt();
            boolean craftable = buf.readBoolean();
            received.add(new RequestTableMk3NetworkEntry(stack, fluid, networkAmount, internalAmount, craftable));
        }
        if (id == S_CONTENT) {
            // only with the list the window opens with: a later copy could undo what the player just set
            settings = readSettings(buf);
            savedSearch = buf.readStringFromBuffer(RequestTableMk3DisplaySettings.MAX_SEARCH_LENGTH);
        }
        List<RequestTableMk3NetworkEntry> present = new ArrayList<>(received.size());
        for (RequestTableMk3NetworkEntry entry : received) {
            if (entry.isStored() || entry.isCraftable()) {
                present.add(entry);
            }
        }
        list.setDisplaySettings(settings);
        list.setEntries(present);
        if (id == S_CONTENT && !opened) {
            opened = true;
            if (onOpened != null) {
                onOpened.run();
            }
        }
    }

    // endregion

    // region server

    @Override
    public void detectAndSendChanges(boolean init) {
        long time = table.getWorld().getTotalWorldTime();
        // what was sent into the network is on its way for a while, the list follows it once a second
        boolean following = time <= table.getNetworkRefreshUntil() && time % FOLLOW_INTERVAL == 0;
        if (init) {
            sendContent(true);
        } else
            if ((following || sentRevision != table.getContentRevision()) && time - lastSendTime >= MIN_SEND_INTERVAL) {
                // many items may arrive within a moment, the list follows them a few times a second
                sendContent(false);
            }
        boolean recipeChanged = init;
        for (int i = 0; i < 9; i++) {
            ItemStack wanted = table.matrix.getStackInSlot(i);
            ItemIdentifier item = wanted == null ? null : ItemIdentifier.get(wanted);
            if (item != sentRecipe[i]) {
                sentRecipe[i] = item;
                recipeChanged = true;
            }
        }
        if (recipeChanged) {
            syncToClient(S_RECIPE, buf -> {
                for (int i = 0; i < 9; i++) {
                    buf.writeItemStackToBuffer(table.matrix.getStackInSlot(i));
                }
            });
        }
    }

    /**
     * Sends the network list.
     *
     * @param full true to send every entry, false to send only what changed since the last time
     */
    private void sendContent(boolean full) {
        sentRevision = table.getContentRevision();
        lastSendTime = table.getWorld().getTotalWorldTime();
        List<RequestTableMk3NetworkEntry> entries = RequestTableMk3Content.buildEntries(table);
        RequestTableMk3DisplaySettings playerSettings = table.getDisplaySettings(getSyncManager().getPlayer());
        if (full) {
            sentIndex.clear();
            sentValues.clear();
        }
        // entries the client doesn't know yet, and the positions of the known ones whose numbers changed
        List<RequestTableMk3NetworkEntry> added = new ArrayList<>();
        List<Integer> changed = new ArrayList<>();
        boolean[] seen = new boolean[sentValues.size()];
        for (RequestTableMk3NetworkEntry entry : entries) {
            int[] values = { entry.getNetworkAmount(), entry.getInternalAmount(), entry.isCraftable() ? 1 : 0 };
            Integer index = sentIndex.get(entry.getStack().getItem());
            if (index == null) {
                sentIndex.put(entry.getStack().getItem(), sentValues.size());
                sentValues.add(values);
                added.add(entry);
            } else {
                seen[index] = true;
                if (!Arrays.equals(sentValues.get(index), values)) {
                    sentValues.set(index, values);
                    changed.add(index);
                }
            }
        }
        // what is no longer in the list is emptied on the client
        for (int index = 0; index < seen.length; index++) {
            int[] values = sentValues.get(index);
            if (!seen[index] && (values[0] != 0 || values[1] != 0 || values[2] != 0)) {
                sentValues.set(index, new int[3]);
                changed.add(index);
            }
        }
        syncToClient(full ? S_CONTENT : S_CONTENT_CHANGES, buf -> {
            LPDataOutputStream data = new LPDataOutputStream(buf);
            if (!full) {
                buf.writeVarIntToBuffer(changed.size());
                for (int index : changed) {
                    int[] values = sentValues.get(index);
                    buf.writeVarIntToBuffer(index);
                    buf.writeInt(values[0]);
                    buf.writeInt(values[1]);
                    buf.writeBoolean(values[2] != 0);
                }
            }
            buf.writeVarIntToBuffer(added.size());
            for (RequestTableMk3NetworkEntry entry : added) {
                data.writeItemIdentifierStack(entry.getStack());
                buf.writeBoolean(entry.isFluid());
                buf.writeInt(entry.getNetworkAmount());
                buf.writeInt(entry.getInternalAmount());
                buf.writeBoolean(entry.isCraftable());
            }
            if (full) {
                writeSettings(buf, playerSettings);
                buf.writeStringToBuffer(playerSettings.getSavedSearch());
            }
        });
    }

    @Override
    public void readOnServer(int id, PacketBuffer buf) throws IOException {
        if (table.container == null || table.container.isInvalid()) {
            return;
        }
        switch (id) {
            case C_REFRESH -> sendContent(false);
            case C_INTERACT -> {
                ItemIdentifierStack stack = new LPDataInputStream(buf).readItemIdentifierStack();
                boolean fluid = buf.readBoolean();
                int mouseButton = buf.readByte();
                boolean shift = buf.readBoolean();
                EntityPlayer player = getSyncManager().getPlayer();
                boolean toNetwork = !fluid && player.inventory.getItemStack() != null
                        && table.getDisplaySettings(player).getDropMode() == DropMode.NETWORK;
                ItemStack wanted = null;
                if (toNetwork) {
                    wanted = player.inventory.getItemStack().copy();
                    if (mouseButton == 1) {
                        wanted.stackSize = 1;
                    }
                }
                int heldBefore = toNetwork ? player.inventory.getItemStack().stackSize : 0;
                boolean sent = toNetwork && table.sendCursorItemToNetwork(player, mouseButton);
                if (toNetwork) {
                    ItemStack held = player.inventory.getItemStack();
                    wanted.stackSize -= heldBefore - (held == null ? 0 : held.stackSize);
                    reportUnsent(wanted);
                }
                // what the network doesn't take goes into the table like without that mode
                if (!sent) {
                    // reports the change through the content revision
                    table.handleNetworkEntryInteraction(player, stack, fluid, mouseButton, shift);
                }
            }
            case C_SETTINGS -> {
                EntityPlayer player = getSyncManager().getPlayer();
                // the search text has its own packet
                table.setDisplaySettings(
                        player,
                        readSettings(buf).withSavedSearch(table.getDisplaySettings(player).getSavedSearch()));
            }
            case C_SAVE_SEARCH -> {
                EntityPlayer player = getSyncManager().getPlayer();
                String text = buf.readStringFromBuffer(RequestTableMk3DisplaySettings.MAX_SEARCH_LENGTH);
                table.setDisplaySettings(player, table.getDisplaySettings(player).withSavedSearch(text));
            }
            case C_AUTOCRAFT -> {
                int request = buf.readVarIntFromBuffer();
                ItemStack[] grid = new ItemStack[9];
                for (int i = 0; i < 9; i++) {
                    ItemStack stack = buf.readItemStackFromBuffer();
                    if (stack != null && stack.getItem() != null) {
                        stack.stackSize = 1;
                        grid[i] = stack;
                    }
                }
                int crafts = Math.max(1, Math.min(MAX_AUTOCRAFTS, buf.readVarIntFromBuffer()));
                EntityPlayer player = getSyncManager().getPlayer();
                int made = table.autoCraft(player, grid, crafts);
                // the slots go out before the answer, so the client counts what is left when it gets it
                if (player.openContainer != null) {
                    player.openContainer.detectAndSendChanges();
                }
                syncToClient(S_AUTOCRAFT_DONE, answer -> {
                    answer.writeVarIntToBuffer(request);
                    answer.writeVarIntToBuffer(made);
                });
                if (made > 0) {
                    table.markContentChanged();
                }
            }
            case C_VIEW -> {
                int ordinal = buf.readByte();
                RequestTableView[] views = RequestTableView.values();
                shownView = ordinal >= 0 && ordinal < views.length ? views[ordinal] : RequestTableView.NETWORK;
            }
            case C_REQUEST -> {
                ItemIdentifierStack stack = new LPDataInputStream(buf).readItemIdentifierStack();
                boolean fluid = buf.readBoolean();
                int amount = Math.max(1, Math.min(MAX_REQUEST_AMOUNT, buf.readInt()));
                EnumSet<ActiveRequestType> flags = buf.readBoolean() ? RequestTree.craftOnlyRequestFlags
                        : RequestTree.defaultRequestFlags;
                if (stack == null) {
                    return;
                }
                ItemIdentifierStack toRequest = stack.getItem().makeStack(amount);
                EntityPlayer player = getSyncManager().getPlayer();
                RequestTableMk3DisplaySettings playerSettings = table.getDisplaySettings(player);
                boolean messages = playerSettings.reportsRequests();
                if (fluid) {
                    RequestHandler.requestFluid(player, toRequest, table, table, messages, flags);
                } else {
                    boolean toPlayer = playerSettings.getDeliveryMode() == DeliveryMode.PLAYER;
                    RequestHandler.request(
                            player,
                            toRequest,
                            table,
                            toPlayer ? new RequestTableMk3PlayerTarget(player.getUniqueID()) : null,
                            messages,
                            flags);
                }
                sendContent(false);
            }
            case C_SEND_STORAGE -> {
                if (buf.readBoolean()) {
                    table.sendStoredFluidsToNetwork();
                    reportUnsent(table.getStoredFluids());
                } else {
                    table.sendStoredItemsToNetwork();
                    reportUnsent(table.getStoredItems());
                }
            }
            case C_CRAFT -> craftOnServer(buf.readBoolean());
            case C_CLEAR_CRAFTING -> table.clearCraftingGrid();
            case C_SET_RECIPE -> {
                ItemStack[] grid = new ItemStack[9];
                for (int i = 0; i < 9; i++) {
                    ItemStack stack = buf.readItemStackFromBuffer();
                    if (stack != null && stack.getItem() != null) {
                        stack.stackSize = 1;
                        grid[i] = stack;
                    }
                }
                table.handleNEIRecipePacket(grid);
                // what lies in the grid and isn't part of the new recipe goes into the storage
                table.storeCraftGridItems(true);
            }
            case C_REQUEST_INGREDIENTS -> {
                int sets = Math.max(1, Math.min(MAX_INGREDIENT_SETS, buf.readVarIntFromBuffer()));
                table.requestCraftingIngredients(getSyncManager().getPlayer(), sets);
            }
            default -> {}
        }
    }

    private void craftOnServer(boolean shift) {
        EntityPlayer player = getSyncManager().getPlayer();
        boolean made;
        if (shift) {
            made = table.craftIntoPlayerInventory(player, SHIFT_CRAFT_LIMIT) > 0;
        } else {
            ItemStack crafted = table.getResultForClick(player, player.inventory.getItemStack(), 1);
            made = crafted != null;
            if (made) {
                player.inventory.setItemStack(crafted);
            }
        }
        if (!made) {
            int missing = table.getMissingIngredientSlots(player);
            if (missing != 0) {
                syncToClient(S_MISSING, buf -> buf.writeVarIntToBuffer(missing));
            }
        }
        table.inv.markDirty();
        player.inventory.markDirty();
        if (player instanceof EntityPlayerMP playerMP) {
            playerMP.updateHeldItem();
        }
        // the amounts in the storage of the table changed
        table.markContentChanged();
    }

    // endregion

    private static void writeSettings(PacketBuffer buf, RequestTableMk3DisplaySettings settings) {
        for (int ordinal : settings.toOrdinals()) {
            buf.writeByte(ordinal);
        }
    }

    private static RequestTableMk3DisplaySettings readSettings(PacketBuffer buf) {
        int[] ordinals = new int[RequestTableMk3DisplaySettings.ORDINAL_COUNT];
        for (int i = 0; i < ordinals.length; i++) {
            ordinals[i] = buf.readByte();
        }
        return RequestTableMk3DisplaySettings.fromOrdinals(ordinals);
    }
}
