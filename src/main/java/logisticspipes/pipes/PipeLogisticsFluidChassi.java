package logisticspipes.pipes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import logisticspipes.interfaces.IFluidModuleHost;
import logisticspipes.interfaces.ISlotUpgradeManager;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.interfaces.routing.IFluidSink;
import logisticspipes.interfaces.routing.IProvideFluids;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.items.ItemModule;
import logisticspipes.items.LogisticsFluidContainer;
import logisticspipes.logisticspipes.FluidChassiTransportLayer;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.logisticspipes.TransportLayer;
import logisticspipes.modules.IFluidReserver;
import logisticspipes.modules.ModuleFluidContainerCrafter;
import logisticspipes.modules.ModuleFluidContainerSink;
import logisticspipes.modules.ModuleFluidProvider;
import logisticspipes.modules.ModuleFluidSink;
import logisticspipes.modules.ModuleFluidTankSupplier;
import logisticspipes.modules.abstractmodules.LogisticsFluidModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.modules.abstractmodules.LogisticsModule.ModulePositionType;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTreeNode;
import logisticspipes.request.resources.FluidResource;
import logisticspipes.routing.FluidLogisticsPromise;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.transport.LPTravelingItem.LPTravelingItemServer;
import logisticspipes.utils.CacheHolder.CacheTypes;
import logisticspipes.utils.FluidContainerUtil;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.SinkReply.FixedPriority;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.tuples.Pair;

/**
 * A chassis for fluid modules. It points at a tank instead of an inventory. Fluid arrives and leaves as fluid container
 * items over the normal item transport, and the modules fill and drain the tank directly, so the pipe has no tanks of
 * its own.
 */
public abstract class PipeLogisticsFluidChassi extends PipeLogisticsChassi
        implements IFluidModuleHost, IFluidSink, IProvideFluids {

    private static final int TICKS_PER_PENDING_RETRY = 20;

    // fluid that arrived but didn't fit into the tank, waiting for the tank or another sink in the network
    private final List<FluidStack> pendingFluids = new ArrayList<>();

    public PipeLogisticsFluidChassi(Item item) {
        super(item);
    }

    @Override
    public boolean isModuleAllowed(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemModule && ItemModule.isFluidModule(stack);
    }

    /*** connection ***/

    private boolean isFluidTarget(TileEntity tile) {
        if (tile == null || !SimpleServiceLocator.pipeInformationManager.isNotAPipe(tile)) {
            return false;
        }
        return tile instanceof IFluidHandler || SimpleServiceLocator.specialTankHandler.hasHandlerFor(tile);
    }

    @Override
    public boolean logisitcsIsPipeConnected(TileEntity tile, ForgeDirection dir) {
        if (!isFluidTarget(tile)) {
            return false;
        }
        if (tile instanceof IFluidHandler handler) {
            FluidTankInfo[] tanks = handler.getTankInfo(dir.getOpposite());
            return tanks != null && tanks.length > 0;
        }
        return true;
    }

    @Override
    public boolean hasFluidPipeLook() {
        return true;
    }

    @Override
    protected boolean isValidOrientation(ForgeDirection connection) {
        if (!super.isValidOrientation(connection)) {
            return false;
        }
        return isFluidTarget(getContainer().getTile(connection));
    }

    @Override
    public TileEntity getFluidTargetTile() {
        ForgeDirection pointed = getPointedOrientation();
        if (pointed == null || pointed == ForgeDirection.UNKNOWN || getContainer() == null) {
            return null;
        }
        TileEntity tile = getContainer().getTile(pointed);
        return isFluidTarget(tile) ? tile : null;
    }

    @Override
    public ForgeDirection getFluidTargetSide(int moduleSlot) {
        ISlotUpgradeManager manager = getUpgradeManager(ModulePositionType.SLOT, moduleSlot);
        if (manager.hasSneakyUpgrade() && manager.getSneakyOrientation() != null) {
            return manager.getSneakyOrientation();
        }
        ForgeDirection pointed = getPointedOrientation();
        return pointed == null ? ForgeDirection.UNKNOWN : pointed.getOpposite();
    }

    @Override
    public TransportLayer getTransportLayer() {
        if (_transportLayer == null) {
            _transportLayer = new FluidChassiTransportLayer(this);
        }
        return _transportLayer;
    }

    /*** modules ***/

    private <T> List<T> getModules(Class<T> type) {
        List<T> list = new ArrayList<>();
        for (int i = 0; i < getChassiSize(); i++) {
            LogisticsModule module = getModules().getSubModule(i);
            if (type.isInstance(module)) {
                list.add(type.cast(module));
            }
        }
        return list;
    }

    /**
     * @return one provider module per side of the tank, so two modules on the same tank don't offer its content twice
     */
    private List<ModuleFluidProvider> getProviders() {
        List<ModuleFluidProvider> list = new ArrayList<>();
        Set<ForgeDirection> sides = new TreeSet<>();
        for (ModuleFluidProvider provider : getModules(ModuleFluidProvider.class)) {
            if (sides.add(provider.getTankSide())) {
                list.add(provider);
            }
        }
        return list;
    }

    /**
     * @return the module that fluid arriving here is for
     */
    private LogisticsFluidModule getModuleFor(IAdditionalTargetInformation info, FluidStack liquid) {
        if (info instanceof ChassiTargetInformation target
                && getModules().getSubModule(target.getModuleSlot()) instanceof LogisticsFluidModule module) {
            return module;
        }
        List<ModuleFluidSink> sinks = getModules(ModuleFluidSink.class);
        for (ModuleFluidSink sink : sinks) {
            if (sink.isFiltered(liquid)) {
                return sink;
            }
        }
        // a requested delivery that lost its target information, e.g. after a world reload
        for (ModuleFluidTankSupplier supplier : getModules(ModuleFluidTankSupplier.class)) {
            if (supplier.wants(liquid)) {
                return supplier;
            }
        }
        for (ModuleFluidSink sink : sinks) {
            if (sink.isDefaultRoute()) {
                return sink;
            }
        }
        return null;
    }

    @Override
    public void onAllowedRemoval() {
        if (MainProxy.isServer(getWorld())) {
            for (LogisticsFluidModule module : getModules(LogisticsFluidModule.class)) {
                module.onHostRemoved();
            }
        }
        super.onAllowedRemoval();
    }

    /*** arriving fluid ***/

    /**
     * Puts an arriving fluid container into the tank. What doesn't fit is sent to another sink in the network.
     *
     * @return true if the item was a fluid container and is handled
     */
    public boolean endReached(LPTravelingItemServer arrivingItem, TileEntity tile) {
        ItemIdentifierStack stack = arrivingItem.getItemIdentifierStack();
        if (stack == null) {
            return false;
        }
        if (emptyArrivingContainers(arrivingItem, stack)) {
            return true;
        }
        if (!stack.getItem().isFluidContainer()) {
            return false;
        }
        FluidStack liquid = SimpleServiceLocator.logisticsFluidManager.getFluidFromContainer(stack);
        if (liquid == null) {
            return false;
        }
        // a real item that holds fluid, like a large cell: every one of the stack holds that much, and the empty
        // ones are given back to the network
        boolean realContainer = !(stack.getItem().item instanceof LogisticsFluidContainer);
        if (realContainer) {
            liquid.amount = (int) Math.min(Integer.MAX_VALUE, (long) liquid.amount * stack.getStackSize());
            ItemStack empty = FluidContainerUtil.getEmptied(stack.getItem().makeNormalStack(1));
            if (empty != null) {
                sendStacks(empty, stack.getStackSize());
            }
        }
        getCacheHolder().trigger(CacheTypes.Inventory);
        if (getRouter().getSimpleID() == arrivingItem.getDestination()) {
            LogisticsFluidModule module = getModuleFor(arrivingItem.getInfo().targetInfo, liquid);
            if (module != null) {
                liquid.amount -= module.acceptFluid(liquid);
            }
        }
        if (liquid.amount > 0) {
            addPendingFluid(liquid);
            sendPendingFluids();
        }
        return true;
    }

    /**
     * Hands filled containers that were sent to a container sink module to that module. The emptied containers go back
     * into the network, and so do filled ones the tank had no room for.
     *
     * @return true if the item was for such a module and is handled
     */
    private boolean emptyArrivingContainers(LPTravelingItemServer arrivingItem, ItemIdentifierStack stack) {
        if (stack.getItem().item instanceof LogisticsFluidContainer
                || getRouter().getSimpleID() != arrivingItem.getDestination()) {
            return false;
        }
        // empty containers a crafter module asked for
        if (arrivingItem.getInfo().targetInfo instanceof ChassiTargetInformation target
                && getModules().getSubModule(target.getModuleSlot()) instanceof ModuleFluidContainerCrafter crafter
                && crafter.takeContainers(stack.makeNormalStack())) {
            return true;
        }
        ItemStack single = stack.getItem().makeNormalStack(1);
        FluidStack fluid = FluidContainerUtil.getFluid(single);
        if (fluid == null) {
            return false;
        }
        ModuleFluidContainerSink module = getContainerSinkFor(arrivingItem.getInfo().targetInfo, fluid);
        if (module == null) {
            return false;
        }
        getCacheHolder().trigger(CacheTypes.Inventory);
        int count = stack.getStackSize();
        int emptied = 0;
        while (emptied < count && module.acceptContainer(fluid)) {
            emptied++;
            FluidStack overflow = module.takeOverflow();
            if (overflow != null) {
                addPendingFluid(overflow);
            }
        }
        if (emptied > 0) {
            ItemStack empty = FluidContainerUtil.getEmptied(single);
            if (empty != null) {
                sendStacks(empty, emptied);
            }
        }
        if (emptied < count) {
            sendStacks(single, count - emptied);
        }
        if (!pendingFluids.isEmpty()) {
            sendPendingFluids();
        }
        return true;
    }

    private ModuleFluidContainerSink getContainerSinkFor(IAdditionalTargetInformation info, FluidStack fluid) {
        if (info instanceof ChassiTargetInformation target
                && getModules().getSubModule(target.getModuleSlot()) instanceof ModuleFluidContainerSink module) {
            return module;
        }
        for (ModuleFluidContainerSink sink : getModules(ModuleFluidContainerSink.class)) {
            if (sink.isFiltered(fluid)) {
                return sink;
            }
        }
        return null;
    }

    /**
     * Sends items into the network, in stacks of their normal size.
     */
    private void sendStacks(ItemStack prototype, int amount) {
        while (amount > 0) {
            ItemStack stack = prototype.copy();
            stack.stackSize = Math.min(amount, Math.max(1, stack.getMaxStackSize()));
            amount -= stack.stackSize;
            transport.sendItem(stack);
        }
    }

    private void addPendingFluid(FluidStack liquid) {
        for (FluidStack pending : pendingFluids) {
            if (pending.isFluidEqual(liquid) && pending.amount <= Integer.MAX_VALUE - liquid.amount) {
                pending.amount += liquid.amount;
                return;
            }
        }
        pendingFluids.add(liquid.copy());
    }

    private void sendPendingFluids() {
        for (Iterator<FluidStack> iterator = pendingFluids.iterator(); iterator.hasNext();) {
            FluidStack pending = iterator.next();
            if (pending.amount <= 0) {
                iterator.remove();
                continue;
            }
            Pair<Integer, Integer> reply = SimpleServiceLocator.logisticsFluidManager
                    .getBestReply(pending, getRouter(), new ArrayList<>());
            if (reply.getValue1() != 0 && reply.getValue2() > 0) {
                FluidStack toSend = pending.copy();
                toSend.amount = Math.min(pending.amount, reply.getValue2());
                IRoutedItem routedItem = SimpleServiceLocator.routedItemHelper
                        .createNewTravelItem(SimpleServiceLocator.logisticsFluidManager.getFluidContainer(toSend));
                routedItem.setDestination(reply.getValue1());
                routedItem.setTransportMode(TransportMode.Passive);
                ForgeDirection from = getPointedOrientation();
                queueRoutedItem(routedItem, from == null || from == ForgeDirection.UNKNOWN ? ForgeDirection.UP : from);
                pending.amount -= toSend.amount;
            }
            if (pending.amount <= 0) {
                iterator.remove();
            }
        }
    }

    @Override
    public void enabledUpdateEntity() {
        super.enabledUpdateEntity();
        if (pendingFluids.isEmpty() || !isNthTick(TICKS_PER_PENDING_RETRY)) {
            return;
        }
        // the tank may have room again
        for (FluidStack pending : pendingFluids) {
            LogisticsFluidModule module = getModuleFor(null, pending);
            if (module != null) {
                pending.amount -= module.acceptFluid(pending);
            }
        }
        sendPendingFluids();
    }

    /*** IFluidSink ***/

    private int getSinkAmount(FluidStack stack, boolean filtered) {
        int amount = 0;
        for (ModuleFluidSink sink : getModules(ModuleFluidSink.class)) {
            if (sink.isFiltered(stack) == filtered) {
                amount = Math.max(amount, sink.sinkAmount(stack));
            }
        }
        return amount;
    }

    @Override
    public int sinkAmount(FluidStack stack) {
        if (!isEnabled()) {
            return 0;
        }
        int amount = getSinkAmount(stack, true);
        return amount > 0 ? amount : getSinkAmount(stack, false);
    }

    @Override
    public FixedPriority getFluidSinkPriority(FluidStack stack) {
        for (ModuleFluidSink sink : getModules(ModuleFluidSink.class)) {
            if (sink.isFiltered(stack)) {
                return FixedPriority.ItemSink;
            }
        }
        return FixedPriority.DefaultRoute;
    }

    /*** IProvideFluids ***/

    @Override
    public void canProvide(RequestTreeNode tree, RequestTree root, List<IFilter> filters) {
        if (!isEnabled() || !(tree.getRequestType() instanceof FluidResource resource)) {
            return;
        }
        // a module of this chassis that asks for the fluid of its own recipe answers for it itself out of the tank
        if (resource.getTarget() instanceof LogisticsFluidModule && isOwnModule(resource.getTarget())) {
            return;
        }
        for (IFilter filter : filters) {
            if (filter.blockProvider()) {
                return;
            }
        }
        for (ModuleFluidProvider provider : getProviders()) {
            provider.canProvide(tree, root, filters);
        }
    }

    private boolean isOwnModule(Object module) {
        for (int i = 0; i < getChassiSize(); i++) {
            if (getModules().getSubModule(i) == module) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getReservedFluid(FluidIdentifier fluid) {
        // asked once for each fluid of the tank, so no list of the modules is made
        long reserved = 0;
        for (int i = 0; i < getChassiSize(); i++) {
            if (getModules().getSubModule(i) instanceof IFluidReserver module) {
                reserved += module.getReservedAmount(fluid);
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, reserved);
    }

    @Override
    public Map<FluidIdentifier, Integer> getAvailableFluids() {
        Map<FluidIdentifier, Integer> result = new HashMap<>();
        if (!isEnabled()) {
            return result;
        }
        for (ModuleFluidProvider provider : getProviders()) {
            for (Entry<FluidIdentifier, Integer> entry : provider.getAvailableFluids().entrySet()) {
                long total = (long) entry.getValue() + result.getOrDefault(entry.getKey(), 0);
                result.put(entry.getKey(), (int) Math.min(total, Integer.MAX_VALUE));
            }
        }
        return result;
    }

    @Override
    public IOrderInfoProvider fullFill(FluidLogisticsPromise promise, IRequestFluid destination, ResourceType type,
            IAdditionalTargetInformation info) {
        // promises are made by the provider modules and come back to them, this is only the fallback
        List<ModuleFluidProvider> providers = getProviders();
        return providers.isEmpty() ? null : providers.get(0).fullFill(promise, destination, type, info);
    }

    /*** interests ***/

    @Override
    public Set<ItemIdentifier> getSpecificInterests() {
        Set<ItemIdentifier> interests = new TreeSet<>();
        if (getFluidTargetTile() == null) {
            return interests;
        }
        for (LogisticsFluidModule module : getModules(LogisticsFluidModule.class)) {
            Collection<ItemIdentifier> current = module.getSpecificInterests();
            if (current != null) {
                interests.addAll(current);
            }
        }
        return interests;
    }

    @Override
    public boolean hasGenericInterests() {
        if (getFluidTargetTile() == null) {
            return false;
        }
        for (LogisticsFluidModule module : getModules(LogisticsFluidModule.class)) {
            if (module.hasGenericInterests()) {
                return true;
            }
        }
        return false;
    }

    /*** save ***/

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        super.readFromNBT(nbttagcompound);
        pendingFluids.clear();
        NBTTagList list = nbttagcompound.getTagList("pendingFluids", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(list.getCompoundTagAt(i));
            if (fluid != null) {
                pendingFluids.add(fluid);
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
        NBTTagList list = new NBTTagList();
        for (FluidStack fluid : pendingFluids) {
            list.appendTag(fluid.writeToNBT(new NBTTagCompound()));
        }
        nbttagcompound.setTag("pendingFluids", list);
    }
}
