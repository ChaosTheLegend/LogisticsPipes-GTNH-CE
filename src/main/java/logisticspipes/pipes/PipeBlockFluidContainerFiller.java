package logisticspipes.pipes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import logisticspipes.api.IMUICompatiblePipeV2;
import logisticspipes.compat.ModularUIHelper;
import logisticspipes.crafting.legacy.template.ICraftingTemplate;
import logisticspipes.crafting.legacy.template.ItemCraftingTemplate;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.PipeGuiFactory;
import logisticspipes.gui.modularUI.pipes.PipeBlockFluidContainerFillerMui;
import logisticspipes.interfaces.IBlockShapedPipe;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.ICraftItems;
import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.items.LogisticsFluidContainer;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.logisticspipes.TransportLayer;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.IPromise;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTreeNode;
import logisticspipes.request.resources.FluidResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.request.resources.ItemResource;
import logisticspipes.routing.ExitRoute;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.routing.order.LogisticsItemOrder;
import logisticspipes.routing.order.LogisticsItemOrderManager;
import logisticspipes.routing.order.LogisticsOrder;
import logisticspipes.security.SecuritySettings;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.utils.FluidContainerUtil;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.FluidSendUtil;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierInventory;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * A block that fills containers on request: with empty cells set, the network can craft a cell of any fluid here. The
 * recipe is one empty container and the fluid it holds. Both come from the network like the ingredients of any other
 * craft, so the block stands next to no tank.
 * <p>
 * Only the kind of container is set. Which fluids there are is not: whatever filled form of the container is asked for
 * is answered for, and the request for the fluid decides whether it can be made.
 */
public class PipeBlockFluidContainerFiller extends CoreRoutedPipe
        implements ICraftItems, IRequestFluid, IBlockShapedPipe, IMUICompatiblePipeV2 {

    private static final int TICKS_PER_FILL = 10;
    private static final int TICKS_PER_CLEANUP = 100;
    private static final int TICKS_PER_RESULT_LIST = 100;
    private static final int MAX_ORDER_CHECKS = 16;
    private static final int MAX_CACHED_RECIPES = 4096;
    private static final int FLUID_SEND_CHUNK = 5000;

    // set while a filler lists what the network can craft: the other fillers answer with the list they have then,
    // or they would ask each other without end. Only the server thread lists and asks.
    private static boolean listingCraftables;
    // the fluids of the networks that were looked through lately. The fillers of a network share one: looking
    // through a network asks every tank and every crafter of it.
    private static final List<FluidSnapshot> SNAPSHOTS = new ArrayList<>();

    // the empty container to fill, one phantom item
    private final ItemIdentifierInventory containerInventory = new ItemIdentifierInventory(1, "Container", 1);

    // what arrived for the requests and wasn't used yet
    private final Map<ItemIdentifier, Integer> itemBuffer = new HashMap<>();
    private final Map<FluidIdentifier, Integer> fluidBuffer = new HashMap<>();

    // the container the caches below were made for
    private ItemIdentifier cachedContainer;
    // what was asked for so far and how it is made; null for what isn't made here
    private final Map<ItemIdentifier, Recipe> recipes = new HashMap<>();
    private List<ItemIdentifierStack> resultList = Collections.emptyList();
    // when the list was made, Long.MIN_VALUE while there is none
    private long resultListTime = Long.MIN_VALUE;

    private int rotation;

    public PipeBlockFluidContainerFiller(Item item) {
        super(item);
        _orderItemManager = new LogisticsItemOrderManager(this);
    }

    public ItemIdentifierInventory getContainerInventory() {
        return containerInventory;
    }

    /**
     * Forgets what the fillers found in the networks of a server that stops.
     */
    public static void cleanup() {
        SNAPSHOTS.clear();
        listingCraftables = false;
    }

    /**
     * Reads the kind of the empty container and drops what was remembered for another kind.
     *
     * @return the kind of the empty container, or null if none is set
     */
    private ItemIdentifier updateContainerType() {
        ItemIdentifierStack stack = containerInventory.getIDStackInSlot(0);
        ItemIdentifier type = stack == null ? null : stack.getItem();
        if (type != cachedContainer) {
            cachedContainer = type;
            recipes.clear();
            resultList = Collections.emptyList();
            resultListTime = Long.MIN_VALUE;
        }
        return type;
    }

    /**
     * @return how the item is made here, or null if it isn't: it has to be the container that is set, filled to the
     *         brim
     */
    private Recipe getRecipe(ItemIdentifier item) {
        ItemIdentifier containerType = updateContainerType();
        if (containerType == null || item == null) {
            return null;
        }
        if (recipes.containsKey(item)) {
            return recipes.get(item);
        }
        if (recipes.size() >= MAX_CACHED_RECIPES) {
            recipes.clear();
        }
        Recipe recipe = findRecipe(containerType, item);
        recipes.put(item, recipe);
        return recipe;
    }

    private static Recipe findRecipe(ItemIdentifier containerType, ItemIdentifier item) {
        ItemStack stack = item.makeNormalStack(1);
        FluidStack fluid = FluidContainerUtil.getFluid(stack);
        if (fluid == null) {
            return null;
        }
        ItemStack emptied = FluidContainerUtil.getEmptied(stack);
        if (emptied == null || ItemIdentifier.get(emptied) != containerType) {
            return null;
        }
        return makeRecipe(containerType, FluidIdentifier.get(fluid), item);
    }

    /**
     * @param wanted the filled container that has to come out, or null for whatever does
     * @return the recipe that fills the container with the fluid, or null if it doesn't hold that fluid
     */
    private static Recipe makeRecipe(ItemIdentifier containerType, FluidIdentifier fluid, ItemIdentifier wanted) {
        ItemStack container = containerType.makeNormalStack(1);
        FluidStack full = fluid.makeFluidStack(1);
        int capacity = FluidContainerUtil.getCapacity(container, full);
        if (capacity <= 0) {
            return null;
        }
        full.amount = capacity;
        ItemStack filled = FluidContainerUtil.fill(container, full);
        if (filled == null) {
            return null;
        }
        ItemIdentifier result = ItemIdentifier.get(filled);
        // a container that isn't full is another item, it is not made here
        if (wanted != null && result != wanted) {
            return null;
        }
        return new Recipe(result, containerType, fluid, capacity);
    }

    // region the recipes

    @Override
    public boolean canCraft(IResource toCraft) {
        return toCraft instanceof ItemResource resource && getRecipe(resource.getItem()) != null;
    }

    @Override
    public ICraftingTemplate addCrafting(IResource toCraft) {
        if (!isEnabled() || !(toCraft instanceof ItemResource resource)) {
            return null;
        }
        Recipe recipe = getRecipe(resource.getItem());
        if (recipe == null) {
            return null;
        }
        ItemCraftingTemplate template = new ItemCraftingTemplate(recipe.result.makeStack(1), this, 0);
        template.addIngredient(new ItemResource(recipe.container.makeStack(1), this), null);
        template.addIngredient(new FluidResource(recipe.fluid, recipe.capacity, this), null);
        return template;
    }

    /**
     * The container filled with every fluid the network has or can make. Only what is shown as craftable comes from
     * this list: a request is answered for whatever the list says.
     */
    @Override
    public List<ItemIdentifierStack> getConfiguredCraftResults() {
        ItemIdentifier containerType = updateContainerType();
        if (containerType == null || getWorld() == null || getRouter() == null) {
            return Collections.emptyList();
        }
        long now = getWorld().getTotalWorldTime();
        if (PipeBlockFluidContainerFiller.listingCraftables
                || (resultListTime != Long.MIN_VALUE && now >= resultListTime
                        && now - resultListTime < TICKS_PER_RESULT_LIST)) {
            return resultList;
        }
        resultListTime = now;
        Set<FluidIdentifier> fluids = getNetworkFluids(now);
        List<ItemIdentifierStack> list = new ArrayList<>(fluids.size());
        for (FluidIdentifier fluid : fluids) {
            Recipe recipe = fluid == null ? null : makeRecipe(containerType, fluid, null);
            if (recipe != null) {
                list.add(recipe.result.makeStack(1));
            }
        }
        resultList = list;
        return list;
    }

    /**
     * @return the fluids the network has or can make: from what another filler of the network found lately, or by
     *         looking through the network
     */
    private Set<FluidIdentifier> getNetworkFluids(long now) {
        List<ExitRoute> routes = getRouter().getIRoutersByCost();
        int ownId = getRouter().getSimpleID();
        Iterator<FluidSnapshot> snapshots = PipeBlockFluidContainerFiller.SNAPSHOTS.iterator();
        while (snapshots.hasNext()) {
            FluidSnapshot snapshot = snapshots.next();
            if (now < snapshot.time || now - snapshot.time >= TICKS_PER_RESULT_LIST) {
                snapshots.remove();
            } else if (snapshot.routerId == ownId || isReachable(routes, snapshot.routerId)) {
                return snapshot.fluids;
            }
        }
        Set<FluidIdentifier> fluids = new LinkedHashSet<>();
        for (ItemIdentifierStack fluid : SimpleServiceLocator.logisticsFluidManager.getAvailableFluid(routes)) {
            fluids.add(FluidIdentifier.get(fluid.getItem()));
        }
        PipeBlockFluidContainerFiller.listingCraftables = true;
        try {
            for (ItemIdentifier craftable : SimpleServiceLocator.logisticsManager.getCraftableItems(routes)) {
                if (craftable.isFluidContainer()) {
                    fluids.add(FluidIdentifier.get(craftable));
                }
            }
        } finally {
            PipeBlockFluidContainerFiller.listingCraftables = false;
        }
        PipeBlockFluidContainerFiller.SNAPSHOTS.add(new FluidSnapshot(ownId, now, fluids));
        return fluids;
    }

    private static boolean isReachable(List<ExitRoute> routes, int routerId) {
        for (ExitRoute route : routes) {
            if (route != null && route.destination.getSimpleID() == routerId) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void registerExtras(IPromise promise) {
        // a craft makes exactly one container, there is never more than was asked for
    }

    @Override
    public int getTodo() {
        return _orderItemManager.totalAmountCountInAllOrders();
    }

    /**
     * Any item may be a filled form of the container, so every request for one is looked at. Without a container
     * nothing is made, and the network doesn't have to ask.
     */
    @Override
    public boolean hasGenericInterests() {
        return containerInventory.getIDStackInSlot(0) != null;
    }

    @Override
    public void canProvide(RequestTreeNode tree, RequestTree root, List<IFilter> filters) {
        // nothing is kept in stock, a container is only made for a request
    }

    @Override
    public void getAllItems(Map<ItemIdentifier, Integer> list, List<IFilter> filter) {}

    @Override
    public LogisticsOrder fullFill(LogisticsPromise promise, IRequestItems destination,
            IAdditionalTargetInformation info) {
        if (getRecipe(promise.item) == null) {
            return null;
        }
        return _orderItemManager.addOrder(
                new ItemIdentifierStack(promise.item, promise.numberOfItems),
                destination,
                ResourceType.CRAFTING,
                info);
    }

    // endregion

    // region filling

    @Override
    public void enabledUpdateEntity() {
        super.enabledUpdateEntity();
        if (MainProxy.isClient(getWorld())) {
            return;
        }
        if (isNthTick(TICKS_PER_FILL) && !itemBuffer.isEmpty() && !fluidBuffer.isEmpty()) {
            fillForOrders();
        }
        if (isNthTick(TICKS_PER_CLEANUP)) {
            returnLeftovers();
        }
    }

    /**
     * Fills containers for the first order that has its container and its fluid here.
     */
    private void fillForOrders() {
        for (int i = 0; i < MAX_ORDER_CHECKS; i++) {
            LogisticsItemOrder order = _orderItemManager.peekAtTopRequest(ResourceType.CRAFTING);
            if (order == null) {
                return;
            }
            Recipe recipe = getRecipe(order.getResource().stack.getItem());
            if (recipe == null) {
                // the container was changed, the order can't be made any more
                _orderItemManager.sendFailed();
                continue;
            }
            int made = Math.min(order.getResource().stack.getStackSize(), getBuffered(itemBuffer, recipe.container));
            made = Math.min(made, getBuffered(fluidBuffer, recipe.fluid) / recipe.capacity);
            made = Math.min(made, Math.max(1, recipe.result.getMaxStackSize()));
            if (made <= 0) {
                _orderItemManager.deferSend();
                continue;
            }
            takeBuffered(itemBuffer, recipe.container, made);
            takeBuffered(fluidBuffer, recipe.fluid, made * recipe.capacity);
            IRoutedItem item = SimpleServiceLocator.routedItemHelper
                    .createNewTravelItem(recipe.result.makeNormalStack(made));
            if (order.getDestination() != null && order.getDestination().getRouter() != null) {
                item.setDestination(order.getDestination().getRouter().getSimpleID());
                item.setTransportMode(TransportMode.Active);
                item.setAdditionalTargetInformation(order.getInformation());
            } else {
                SimpleServiceLocator.logisticsManager.assignDestinationFor(item, getRouter().getSimpleID(), false);
            }
            ForgeDirection dir = getRouteLayer().getOrientationForItem(item, null);
            queueRoutedItem(item, dir.getOpposite());
            _orderItemManager.sendSuccessfull(made, false, item);
            return;
        }
    }

    /**
     * Sends back into the network what no order needs: a request was cancelled, or the container was changed.
     */
    private void returnLeftovers() {
        if (itemBuffer.isEmpty() && fluidBuffer.isEmpty()) {
            return;
        }
        ItemIdentifier containerType = updateContainerType();
        int neededContainers = _orderItemManager.totalAmountCountInAllOrders();
        Iterator<Entry<ItemIdentifier, Integer>> items = itemBuffer.entrySet().iterator();
        while (items.hasNext()) {
            Entry<ItemIdentifier, Integer> entry = items.next();
            int surplus = entry.getValue() - (entry.getKey() == containerType ? neededContainers : 0);
            if (surplus <= 0) {
                continue;
            }
            int sent = sendItemsToNetwork(entry.getKey(), surplus);
            if (sent >= entry.getValue()) {
                items.remove();
            } else {
                entry.setValue(entry.getValue() - sent);
            }
        }
        Iterator<Entry<FluidIdentifier, Integer>> fluids = fluidBuffer.entrySet().iterator();
        while (fluids.hasNext()) {
            Entry<FluidIdentifier, Integer> entry = fluids.next();
            Recipe recipe = containerType == null ? null : makeRecipe(containerType, entry.getKey(), null);
            long needed = recipe == null ? 0
                    : (long) _orderItemManager.totalItemsCountInOrders(recipe.result) * recipe.capacity;
            long surplus = entry.getValue() - needed;
            if (surplus <= 0) {
                continue;
            }
            int sent = sendFluidToNetwork(entry.getKey(), (int) surplus);
            if (sent >= entry.getValue()) {
                fluids.remove();
            } else {
                entry.setValue(entry.getValue() - sent);
            }
        }
    }

    /**
     * @return how many of the items the network took
     */
    private int sendItemsToNetwork(ItemIdentifier type, int amount) {
        int sent = 0;
        while (sent < amount) {
            ItemStack stack = type.makeNormalStack(Math.min(amount - sent, Math.max(1, type.getMaxStackSize())));
            IRoutedItem item = SimpleServiceLocator.routedItemHelper.createNewTravelItem(stack);
            SimpleServiceLocator.logisticsManager.assignDestinationFor(item, getRouter().getSimpleID(), false);
            if (item.getDestinationUUID() == null) {
                break;
            }
            ForgeDirection dir = getRouteLayer().getOrientationForItem(item, null);
            queueRoutedItem(item, dir.getOpposite());
            sent += stack.stackSize;
        }
        return sent;
    }

    /**
     * @return how much of the fluid the network took
     */
    private int sendFluidToNetwork(FluidIdentifier fluid, int amount) {
        int sent = 0;
        while (sent < amount) {
            FluidStack toSend = fluid.makeFluidStack(Math.min(amount - sent, FLUID_SEND_CHUNK));
            int offered = toSend.amount;
            IRoutedItem item = FluidSendUtil.createItemForSink(toSend, getRouter());
            if (item == null) {
                break;
            }
            ForgeDirection dir = getRouteLayer().getOrientationForItem(item, null);
            queueRoutedItem(item, dir.getOpposite());
            sent += offered - toSend.amount;
        }
        return sent;
    }

    private static <T> int getBuffered(Map<T, Integer> buffer, T type) {
        Integer amount = buffer.get(type);
        return amount == null ? 0 : amount;
    }

    private static <T> void takeBuffered(Map<T, Integer> buffer, T type, int amount) {
        int left = getBuffered(buffer, type) - amount;
        if (left > 0) {
            buffer.put(type, left);
        } else {
            buffer.remove(type);
        }
    }

    /**
     * What arrives here is kept for the requests: the empty containers and the fluid they are filled with.
     */
    @Override
    public TransportLayer getTransportLayer() {
        if (_transportLayer == null) {
            _transportLayer = new TransportLayer() {

                @Override
                public void handleItem(IRoutedItem item) {
                    PipeBlockFluidContainerFiller.this.notifyOfItemArival(item.getInfo());
                    ItemIdentifierStack stack = item.getItemIdentifierStack();
                    if (stack == null || stack.getStackSize() <= 0) {
                        return;
                    }
                    FluidStack fluid = stack.getItem().item instanceof LogisticsFluidContainer
                            ? SimpleServiceLocator.logisticsFluidManager.getFluidFromContainer(stack)
                            : null;
                    if (fluid != null) {
                        fluidBuffer.merge(FluidIdentifier.get(fluid), fluid.amount, Integer::sum);
                    } else {
                        itemBuffer.merge(stack.getItem(), stack.getStackSize(), Integer::sum);
                    }
                    stack.setStackSize(0);
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
    public void itemArrived(ItemIdentifierStack item, IAdditionalTargetInformation info) {}

    @Override
    public void itemLost(ItemIdentifierStack item, IAdditionalTargetInformation info) {}

    @Override
    public void itemCouldNotBeSend(ItemIdentifierStack item, IAdditionalTargetInformation info) {}

    @Override
    public void sendFailed(FluidIdentifier fluid, Integer amount) {}

    // endregion

    // region the block

    @Override
    public boolean handleClick(EntityPlayer player, SecuritySettings settings) {
        // the pipe controller opens the upgrades
        if (MainProxy.isPipeControllerEquipped(player) && !player.isSneaking()) {
            return false;
        }
        if (MainProxy.isServer(getWorld())) {
            if (settings == null || settings.openGui) {
                ModularUIHelper.openPipeUI(player, this);
            } else {
                player.addChatComponentMessage(new ChatComponentTranslation("lp.chat.permissiondenied"));
            }
        }
        return true;
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        return PipeGuiFactory.fromMui(new PipeBlockFluidContainerFillerMui(this));
    }

    @Override
    public void onAllowedRemoval() {
        if (MainProxy.isClient(getWorld())) {
            return;
        }
        while (_orderItemManager.hasOrders(ResourceType.CRAFTING)) {
            _orderItemManager.sendFailed();
        }
        // the fluid has no form to drop in, the containers do
        for (Entry<ItemIdentifier, Integer> entry : itemBuffer.entrySet()) {
            int left = entry.getValue();
            while (left > 0) {
                ItemStack stack = entry.getKey()
                        .makeNormalStack(Math.min(left, Math.max(1, entry.getKey().getMaxStackSize())));
                left -= stack.stackSize;
                ItemIdentifierInventory.dropItems(getWorld(), stack, getX(), getY(), getZ());
            }
        }
        itemBuffer.clear();
    }

    @Override
    public TextureType getCenterTexture() {
        return Textures.empty;
    }

    @Override
    public TextureType getRoutedTexture(ForgeDirection connection) {
        return Textures.empty_1;
    }

    @Override
    public TextureType getNonRoutedTexture(ForgeDirection connection) {
        return Textures.empty_2;
    }

    @Override
    public LogisticsModule getLogisticsModule() {
        return null;
    }

    @Override
    public ItemSendMode getItemSendMode() {
        return ItemSendMode.Normal;
    }

    @Override
    public boolean sharesInterestWith(CoreRoutedPipe other) {
        return false;
    }

    @Override
    public boolean canHoldBCParts() {
        return false;
    }

    @Override
    public BlockTextures getBlockTextures() {
        return BlockTextures.of(Textures.LOGISTICS_FLUID_FILLER);
    }

    @Override
    public IIcon getBlockModelTexture() {
        return Textures.LOGISTICS_FLUID_FILLER_NEW;
    }

    @Override
    public int getRotation() {
        return rotation;
    }

    @Override
    public void setRotation(int rotation) {
        this.rotation = rotation;
    }

    @Override
    public int getFrontTexture() {
        // unused for pipes
        return 0;
    }

    // endregion

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        containerInventory.readFromNBT(tag, "fillerContainer");
        rotation = tag.getInteger("blockRotation");
        itemBuffer.clear();
        NBTTagList items = tag.getTagList("fillerItems", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound entry = items.getCompoundTagAt(i);
            ItemStack stack = ItemStack.loadItemStackFromNBT(entry);
            if (stack != null && stack.getItem() != null) {
                // more than a normal stack may wait here
                itemBuffer.merge(ItemIdentifier.get(stack), entry.getInteger("amount"), Integer::sum);
            }
        }
        fluidBuffer.clear();
        NBTTagList fluids = tag.getTagList("fillerFluids", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < fluids.tagCount(); i++) {
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(fluids.getCompoundTagAt(i));
            if (fluid != null && fluid.amount > 0) {
                fluidBuffer.merge(FluidIdentifier.get(fluid), fluid.amount, Integer::sum);
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        containerInventory.writeToNBT(tag, "fillerContainer");
        tag.setInteger("blockRotation", rotation);
        NBTTagList items = new NBTTagList();
        for (Entry<ItemIdentifier, Integer> entry : itemBuffer.entrySet()) {
            NBTTagCompound item = entry.getKey().makeNormalStack(1).writeToNBT(new NBTTagCompound());
            item.setInteger("amount", entry.getValue());
            items.appendTag(item);
        }
        tag.setTag("fillerItems", items);
        NBTTagList fluids = new NBTTagList();
        for (Entry<FluidIdentifier, Integer> entry : fluidBuffer.entrySet()) {
            fluids.appendTag(entry.getKey().makeFluidStack(entry.getValue()).writeToNBT(new NBTTagCompound()));
        }
        tag.setTag("fillerFluids", fluids);
    }

    /**
     * The fluids a network has or can make, as the filler with that router found them.
     */
    private static class FluidSnapshot {

        private final int routerId;
        private final long time;
        private final Set<FluidIdentifier> fluids;

        private FluidSnapshot(int routerId, long time, Set<FluidIdentifier> fluids) {
            this.routerId = routerId;
            this.time = time;
            this.fluids = fluids;
        }
    }

    /**
     * How a filled container is made.
     */
    private static class Recipe {

        private final ItemIdentifier result;
        private final ItemIdentifier container;
        private final FluidIdentifier fluid;
        private final int capacity;

        private Recipe(ItemIdentifier result, ItemIdentifier container, FluidIdentifier fluid, int capacity) {
            this.result = result;
            this.container = container;
            this.fluid = fluid;
            this.capacity = capacity;
        }
    }
}
