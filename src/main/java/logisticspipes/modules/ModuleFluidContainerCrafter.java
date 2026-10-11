package logisticspipes.modules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import org.jetbrains.annotations.NotNull;

import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.dynamicModules.ModuleFluidContainerCrafterMuiDynamic;
import logisticspipes.interfaces.IFluidModuleHost;
import logisticspipes.interfaces.ILegacyActiveModule;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.ICraftItems;
import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.interfaces.routing.IProvideFluids;
import logisticspipes.interfaces.routing.IRequest;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.modules.abstractmodules.LogisticsFluidModule;
import logisticspipes.pipes.PipeLogisticsChassi.ChassiTargetInformation;
import logisticspipes.pipes.basic.CoreRoutedPipe.ItemSendMode;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.ICraftingTemplate;
import logisticspipes.request.IPromise;
import logisticspipes.request.ItemCraftingTemplate;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTreeNode;
import logisticspipes.request.resources.DictResource;
import logisticspipes.request.resources.FluidResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.request.resources.ItemResource;
import logisticspipes.routing.FluidLogisticsPromise;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.routing.order.LogisticsFluidOrder;
import logisticspipes.routing.order.LogisticsFluidOrderManager;
import logisticspipes.routing.order.LogisticsItemOrder;
import logisticspipes.routing.order.LogisticsItemOrderManager;
import logisticspipes.routing.order.LogisticsOrder;
import logisticspipes.utils.FluidContainerUtil;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierInventory;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * Makes filled containers on request: the network can craft "a cell of water" here. The recipe is one empty container
 * and the fluid it holds. The empty containers come from the network; the fluid is taken out of the tank the chassis
 * points at.
 * <p>
 * The fluid is part of the recipe, and this module answers for it itself from the tank. So a request never asks for
 * more containers than the tank has fluid for, and what was promised to one request isn't promised to the next.
 */
public class ModuleFluidContainerCrafter extends LogisticsFluidModule
        implements ICraftItems, ILegacyActiveModule, IRequestFluid, IProvideFluids, IFluidReserver {

    private static final int TICKS_PER_RETRY = 20;
    private static final int TICKS_PER_RECONCILE = 100;
    private static final int TANK_CACHE_TICKS = 20;
    private static final int MAX_ORDER_CHECKS = 16;

    public final FluidTank phantomTank = new FluidTank(1);
    // the empty container to fill, one phantom item
    private final ItemIdentifierInventory containerInventory = new ItemIdentifierInventory(1, "", 1);
    public int priority = 0;

    // empty containers that arrived for the requests
    private ItemStack emptyBuffer;
    // the fluid in the tank that was promised to accepted requests
    private LogisticsFluidOrderManager reservations;

    // what the settings come down to, worked out again when they change
    private FluidStack cachedFluid;
    private ItemIdentifier cachedContainer;
    private ItemIdentifierStack result;
    private int capacity;

    private int tankAmount;
    private long tankAmountTime = Long.MIN_VALUE;
    private boolean waitingForFluid;

    public ItemIdentifierInventory getContainerInventory() {
        return containerInventory;
    }

    /**
     * @return true while a request waits for the tank to have enough fluid for a container
     */
    public boolean isWaitingForFluid() {
        return waitingForFluid;
    }

    public int getBufferedContainers() {
        return emptyBuffer == null ? 0 : emptyBuffer.stackSize;
    }

    /**
     * Sets the fluid of the recipe to the one in the tank: the one there is most of, if it holds several.
     */
    public void importFromTank() {
        if (getTankTile() == null || MainProxy.isClient(getWorld())) {
            return;
        }
        FluidIdentifier most = null;
        int mostAmount = 0;
        for (Map.Entry<FluidIdentifier, Integer> entry : getTankContents().entrySet()) {
            if (entry.getValue() > mostAmount) {
                most = entry.getKey();
                mostAmount = entry.getValue();
            }
        }
        if (most != null) {
            phantomTank.setFluid(most.makeFluidStack(1));
        }
    }

    private LogisticsFluidOrderManager getReservations() {
        if (reservations == null) {
            reservations = new LogisticsFluidOrderManager(getHost());
        }
        return reservations;
    }

    private ItemStack getContainer() {
        ItemIdentifierStack stack = containerInventory.getIDStackInSlot(0);
        return stack == null ? null : stack.getItem().makeNormalStack(1);
    }

    /**
     * Works out what the module makes from its settings.
     *
     * @return false if it makes nothing: a setting is missing or the container can't hold the fluid
     */
    private boolean updateRecipe() {
        FluidStack fluid = phantomTank.getFluid();
        ItemIdentifierStack containerStack = containerInventory.getIDStackInSlot(0);
        ItemIdentifier containerType = containerStack == null ? null : containerStack.getItem();
        boolean sameFluid = fluid == null ? cachedFluid == null
                : cachedFluid != null && fluid.isFluidEqual(cachedFluid);
        if (sameFluid && containerType == cachedContainer) {
            return result != null;
        }
        cachedFluid = fluid == null ? null : fluid.copy();
        cachedContainer = containerType;
        result = null;
        capacity = 0;
        // asked for often, so the stacks are only made when a setting changed
        ItemStack container = getContainer();
        if (fluid == null || container == null) {
            return false;
        }
        capacity = FluidContainerUtil.getCapacity(container, fluid);
        if (capacity <= 0) {
            return false;
        }
        FluidStack full = fluid.copy();
        full.amount = capacity;
        ItemStack filled = FluidContainerUtil.fill(container, full);
        if (filled == null) {
            capacity = 0;
            return false;
        }
        result = ItemIdentifier.get(filled).makeStack(1);
        return true;
    }

    private FluidIdentifier getFluidType() {
        return cachedFluid == null ? null : FluidIdentifier.get(cachedFluid);
    }

    /**
     * @return the amount of the recipe fluid in the tank, asked for at most once a second
     */
    private int getTankAmount() {
        long now = getWorld().getTotalWorldTime();
        if (now < tankAmountTime || now - tankAmountTime >= TANK_CACHE_TICKS) {
            tankAmountTime = now;
            FluidIdentifier fluid = getFluidType();
            tankAmount = fluid == null || getTankTile() == null ? 0 : getStoredAmount(fluid);
        }
        return tankAmount;
    }

    /**
     * @return the fluid of the recipe in the tank that no request has been promised yet
     */
    private int getFreeFluid() {
        FluidIdentifier fluid = getFluidType();
        if (fluid == null) {
            return 0;
        }
        IFluidModuleHost host = getHost();
        int reserved = host == null ? getReservedAmount(fluid) : host.getReservedFluid(fluid);
        return Math.max(0, getTankAmount() - reserved);
    }

    @Override
    public int getReservedAmount(FluidIdentifier fluid) {
        return reservations == null ? 0 : reservations.totalFluidsCountInOrders(fluid);
    }

    // region the recipe

    @Override
    public List<ItemIdentifierStack> getConfiguredCraftResults() {
        List<ItemIdentifierStack> list = new ArrayList<>(1);
        if (getHost() != null && updateRecipe()) {
            list.add(result);
        }
        return list;
    }

    @Override
    public boolean canCraft(IResource toCraft) {
        if (getHost() == null || !updateRecipe()) {
            return false;
        }
        if (toCraft instanceof ItemResource || toCraft instanceof DictResource) {
            return toCraft.matches(result.getItem(), IResource.MatchSettings.NORMAL);
        }
        return false;
    }

    @Override
    public ICraftingTemplate addCrafting(IResource toCraft) {
        if (!canCraft(toCraft)) {
            return null;
        }
        ItemCraftingTemplate template = new ItemCraftingTemplate(result, this, priority);
        // the slot tells the chassis which module the deliveries are for
        IAdditionalTargetInformation info = new ChassiTargetInformation(getPositionInt());
        template.addIngredient(new ItemResource(cachedContainer.makeStack(1), this), info);
        template.addIngredient(new FluidResource(getFluidType(), capacity, this), info);
        return template;
    }

    @Override
    public void registerExtras(IPromise promise) {
        // a craft makes exactly one container, there is never more than was asked for
    }

    @Override
    public int getTodo() {
        return _service == null ? 0 : _service.getItemOrderManager().totalAmountCountInAllOrders();
    }

    /**
     * The filled container, to be found as its crafter. The fluid is not announced: the request for it comes to this
     * module by itself, and an interest in it would make the chassis a candidate in every search for that fluid.
     */
    @Override
    public Collection<ItemIdentifier> getSpecificInterests() {
        List<ItemIdentifier> interests = new ArrayList<>(1);
        if (getHost() != null && updateRecipe()) {
            interests.add(result.getItem());
        }
        return interests;
    }

    // endregion

    // region the fluid of the recipe

    /**
     * Answers for the fluid of the own recipe out of the tank. Nothing else is offered: this is no fluid provider.
     */
    @Override
    public void canProvide(RequestTreeNode tree, RequestTree root, List<IFilter> filters) {
        if (getHost() == null || tree.isDone() || !(tree.getRequestType() instanceof FluidResource resource)) {
            return;
        }
        if (resource.getTarget() != this || !updateRecipe()) {
            return;
        }
        FluidIdentifier fluid = getFluidType();
        if (!resource.getFluid().equals(fluid)) {
            return;
        }
        int amount = Math.min(
                tree.getMissingAmount(),
                getFreeFluid() - root.getAllPromissesFor(this, fluid.getItemIdentifier()));
        if (amount > 0) {
            tree.addPromise(new FluidLogisticsPromise(fluid, amount, this, ResourceType.PROVIDER));
        }
    }

    @Override
    public Map<FluidIdentifier, Integer> getAvailableFluids() {
        // the tank isn't offered to the network
        return new HashMap<>();
    }

    /**
     * The promise is kept by leaving the fluid in the tank for the request: nothing is sent.
     */
    @Override
    public IOrderInfoProvider fullFill(FluidLogisticsPromise promise, IRequestFluid destination, ResourceType type,
            IAdditionalTargetInformation info) {
        return getReservations().addOrder(promise, destination, type, info);
    }

    private void releaseReserved(int amount) {
        if (reservations == null) {
            return;
        }
        int left = amount;
        while (left > 0 && reservations.hasOrders(ResourceType.PROVIDER)) {
            LogisticsFluidOrder order = reservations.peekAtTopRequest(ResourceType.PROVIDER);
            int taken = Math.min(left, order.getAmount());
            if (taken <= 0) {
                reservations.sendFailed();
                continue;
            }
            reservations.sendSuccessfull(taken, false, null);
            left -= taken;
        }
    }

    /**
     * Fluid is kept for the containers that are still to be made. A request that went away, cancelled or lost with its
     * pipe, must not hold fluid back for good.
     */
    private void dropStaleReservations() {
        FluidIdentifier fluid = getFluidType();
        if (fluid == null || result == null) {
            while (reservations.hasOrders(ResourceType.PROVIDER)) {
                reservations.sendFailed();
            }
            return;
        }
        long needed = (long) _service.getItemOrderManager().totalItemsCountInOrders(result.getItem()) * capacity;
        long reserved = reservations.totalFluidsCountInOrders(fluid);
        if (reserved > needed) {
            releaseReserved((int) Math.min(Integer.MAX_VALUE, reserved - needed));
        }
    }

    @Override
    public void sendFailed(FluidIdentifier fluid, Integer count) {}

    // endregion

    // region requests for the result

    @Override
    public LogisticsOrder fullFill(LogisticsPromise promise, IRequestItems destination,
            IAdditionalTargetInformation info) {
        if (!updateRecipe() || promise.item != result.getItem()) {
            return null;
        }
        return _service.getItemOrderManager().addOrder(
                new ItemIdentifierStack(promise.item, promise.numberOfItems),
                destination,
                ResourceType.CRAFTING,
                info);
    }

    @Override
    public void getAllItems(Map<ItemIdentifier, Integer> list, List<IFilter> filter) {}

    @Override
    public void onBlockRemoval() {}

    /**
     * Takes empty containers that were sent to this module.
     *
     * @return false if the stack is none of them
     */
    public boolean takeContainers(ItemStack stack) {
        if (stack == null || !updateRecipe() || ItemIdentifier.get(stack) != cachedContainer) {
            return false;
        }
        if (emptyBuffer == null) {
            emptyBuffer = stack.copy();
        } else {
            emptyBuffer.stackSize += stack.stackSize;
        }
        waitingForFluid = false;
        return true;
    }

    @Override
    public void tick() {
        if (_service == null || getHost() == null) {
            return;
        }
        if (reservations != null && _service.isNthTick(TICKS_PER_RECONCILE)) {
            dropStaleReservations();
        }
        if (emptyBuffer == null) {
            return;
        }
        if (waitingForFluid && !_service.isNthTick(TICKS_PER_RETRY)) {
            return;
        }
        LogisticsItemOrderManager orders = _service.getItemOrderManager();
        if (!orders.hasOrders(ResourceType.CRAFTING) || !updateRecipe()) {
            return;
        }
        if (ItemIdentifier.get(emptyBuffer) != cachedContainer) {
            // containers of an older setting, they go back into the network
            _service.sendStack(emptyBuffer, -1, ItemSendMode.Normal, null);
            emptyBuffer = null;
            return;
        }
        LogisticsItemOrder order = findOwnOrder(orders);
        if (order == null) {
            return;
        }
        int wanted = Math.min(order.getResource().stack.getStackSize(), emptyBuffer.stackSize);
        wanted = Math.min(wanted, Math.max(1, result.getItem().getMaxStackSize()));
        FluidIdentifier fluid = getFluidType();
        FluidStack drained = drainTank(fluid, wanted * capacity);
        tankAmountTime = Long.MIN_VALUE;
        int made = drained == null ? 0 : drained.amount / capacity;
        if (drained != null && drained.amount % capacity > 0) {
            // only whole containers are filled, the rest goes back
            fillTank(drained, drained.amount % capacity);
        }
        if (made <= 0) {
            waitingForFluid = true;
            return;
        }
        waitingForFluid = false;
        ItemStack filled = result.getItem().makeNormalStack(made);
        emptyBuffer.stackSize -= made;
        if (emptyBuffer.stackSize <= 0) {
            emptyBuffer = null;
        }
        releaseReserved(made * capacity);
        if (order.getDestination() != null && order.getDestination().getRouter() != null) {
            IFluidModuleHost host = getHost();
            IRoutedItem item = SimpleServiceLocator.routedItemHelper.createNewTravelItem(filled);
            item.setDestination(order.getDestination().getRouter().getSimpleID());
            item.setTransportMode(TransportMode.Active);
            item.setAdditionalTargetInformation(order.getInformation());
            host.queueRoutedItem(item, host.getPointedOrientation());
            orders.sendSuccessfull(made, false, item);
        } else {
            _service.sendStack(filled, -1, ItemSendMode.Normal, order.getInformation());
            orders.sendSuccessfull(made, false, null);
        }
    }

    /**
     * The chassis has one list of orders for all of its modules.
     *
     * @return the first order for the container this module makes, moved to the top; or null
     */
    private LogisticsItemOrder findOwnOrder(LogisticsItemOrderManager orders) {
        for (int i = 0; i < MAX_ORDER_CHECKS; i++) {
            LogisticsItemOrder order = orders.peekAtTopRequest(ResourceType.CRAFTING);
            if (order == null) {
                return null;
            }
            if (order.getResource().stack.getItem() == result.getItem()) {
                return order;
            }
            orders.deferSend();
        }
        return null;
    }

    @Override
    public void itemArrived(ItemIdentifierStack item, IAdditionalTargetInformation info) {}

    @Override
    public void itemLost(ItemIdentifierStack item, IAdditionalTargetInformation info) {}

    @Override
    public void itemCouldNotBeSend(ItemIdentifierStack item, IAdditionalTargetInformation info) {}

    // endregion

    @Override
    public IRouter getRouter() {
        IFluidModuleHost host = getHost();
        return host == null ? null : host.getRouter();
    }

    @Override
    public int getID() {
        return getRouter().getSimpleID();
    }

    @Override
    public int compareTo(@NotNull IRequest other) {
        return getID() - other.getID();
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        phantomTank.setFluid(null);
        phantomTank.readFromNBT(tag.getCompoundTag("tank"));
        containerInventory.readFromNBT(tag, "container");
        priority = tag.getInteger("priority");
        emptyBuffer = tag.hasKey("emptyBuffer") ? ItemStack.loadItemStackFromNBT(tag.getCompoundTag("emptyBuffer"))
                : null;
        if (emptyBuffer != null) {
            // more than a normal stack may wait here
            emptyBuffer.stackSize = tag.getInteger("emptyBufferSize");
        }
        cachedFluid = null;
        cachedContainer = null;
        result = null;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setTag("tank", phantomTank.writeToNBT(new NBTTagCompound()));
        containerInventory.writeToNBT(tag, "container");
        tag.setInteger("priority", priority);
        if (emptyBuffer != null) {
            tag.setTag("emptyBuffer", emptyBuffer.writeToNBT(new NBTTagCompound()));
            tag.setInteger("emptyBufferSize", emptyBuffer.stackSize);
        }
    }

    @Override
    public LogisticsModularUI getHandGui() {
        return new ModuleFluidContainerCrafterMuiDynamic(this);
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        return new ModuleFluidContainerCrafterMuiDynamic(this);
    }

    @Override
    public LogisticsModularUI getPipeGui(String prefix) {
        return new ModuleFluidContainerCrafterMuiDynamic(this, prefix);
    }
}
