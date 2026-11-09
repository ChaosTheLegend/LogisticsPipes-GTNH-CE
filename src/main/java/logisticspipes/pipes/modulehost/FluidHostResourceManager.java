package logisticspipes.pipes.modulehost;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import logisticspipes.interfaces.ISlotUpgradeManager;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.items.LogisticsFluidContainer;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.modules.IFluidReserver;
import logisticspipes.modules.ModuleFluidContainerSink;
import logisticspipes.modules.ModuleFluidProvider;
import logisticspipes.modules.ModuleFluidSink;
import logisticspipes.modules.ModuleFluidTankSupplier;
import logisticspipes.modules.abstractmodules.LogisticsFluidModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.modules.abstractmodules.LogisticsModule.ModulePositionType;
import logisticspipes.pipes.basic.fluid.PendingFluids;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTreeNode;
import logisticspipes.routing.FluidLogisticsPromise;
import logisticspipes.routing.astar.CategoryInterests;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.transport.LPTravelingItem.LPTravelingItemServer;
import logisticspipes.utils.CacheHolder.CacheTypes;
import logisticspipes.utils.FluidContainerUtil;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.FluidSendUtil;
import logisticspipes.utils.SinkReply.FixedPriority;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * The tank a module host pipe works on and the fluid modules that work on it. Fluid arrives and leaves as fluid
 * container items over the normal item transport, and the modules fill and drain the tank directly, so the pipe has no
 * tanks of its own. Without a fluid module this manager takes nothing and offers nothing.
 */
public class FluidHostResourceManager implements IHostResourceManager {

    private static final int TICKS_PER_PENDING_RETRY = 20;
    private static final String PENDING_KEY = "pendingFluids";

    private final IModuleHostPipe pipe;
    private final ModuleHost host;

    // fluid that arrived but didn't fit into the tank, waiting for the tank or another sink in the network
    private final PendingFluids pendingFluids = new PendingFluids();

    private boolean hasModules;

    public FluidHostResourceManager(IModuleHostPipe pipe, ModuleHost host) {
        this.pipe = pipe;
        this.host = host;
    }

    /**
     * @return true if a fluid module is installed
     */
    public boolean hasModules() {
        return hasModules;
    }

    @Override
    public boolean ownsModule(LogisticsModule module) {
        return module instanceof LogisticsFluidModule;
    }

    @Override
    public void onModulesChanged() {
        boolean had = hasModules;
        hasModules = !getModules(LogisticsFluidModule.class).isEmpty();
        // the chassis looks like a fluid pipe while it has a fluid module
        if (had != hasModules && pipe.getContainer() != null) {
            pipe.refreshRender(false);
        }
    }

    private <T> List<T> getModules(Class<T> type) {
        List<T> list = new ArrayList<>();
        for (int i = 0; i < host.getSlotCount(); i++) {
            LogisticsModule module = host.getModule(i);
            if (type.isInstance(module)) {
                list.add(type.cast(module));
            }
        }
        return list;
    }

    /*** target ***/

    private boolean isFluidTarget(TileEntity tile) {
        if (tile == null || !SimpleServiceLocator.pipeInformationManager.isNotAPipe(tile)) {
            return false;
        }
        return tile instanceof IFluidHandler || SimpleServiceLocator.specialTankHandler.hasHandlerFor(tile);
    }

    @Override
    public boolean isValidTarget(TileEntity tile, ForgeDirection dir) {
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
    public boolean isActive() {
        return hasModules && hasTarget();
    }

    /**
     * @return true if one of the pipe's target sides has a tank
     */
    public boolean hasTarget() {
        if (pipe.getContainer() == null) {
            return false;
        }
        for (ForgeDirection dir : pipe.getTargetDirections()) {
            if (isValidTarget(pipe.getContainer().getTile(dir), dir)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the tanks the module in that slot works on: the tank on each target side of the pipe, filled and drained
     *         from the side facing the pipe, or from the sneaky side of the module's upgrade
     */
    public List<FluidTarget> getTargets(int moduleSlot) {
        List<FluidTarget> targets = new ArrayList<>();
        if (pipe.getContainer() == null) {
            return targets;
        }
        ISlotUpgradeManager manager = pipe.getUpgradeManager(ModulePositionType.SLOT, moduleSlot);
        // fluid modules use the first sneaky face only; item modules use all of them
        ForgeDirection sneaky = manager.hasSneakyUpgrade() ? manager.getSneakyOrientation() : null;
        for (ForgeDirection dir : pipe.getTargetDirections()) {
            TileEntity tile = pipe.getContainer().getTile(dir);
            if (isValidTarget(tile, dir)) {
                targets.add(new FluidTarget(tile, sneaky != null ? sneaky : dir.getOpposite()));
            }
        }
        return targets;
    }

    @Override
    public void onRemoved() {
        for (LogisticsFluidModule module : getModules(LogisticsFluidModule.class)) {
            module.onRemoved();
        }
    }

    /*** modules ***/

    /**
     * @return one provider module per set of tanks and sides, so two modules on the same tank don't offer its content
     *         twice
     */
    private List<ModuleFluidProvider> getProviders() {
        List<ModuleFluidProvider> list = new ArrayList<>();
        Set<List<FluidTarget>> seen = new HashSet<>();
        for (ModuleFluidProvider provider : getModules(ModuleFluidProvider.class)) {
            if (seen.add(getTargets(provider.getPositionInt()))) {
                list.add(provider);
            }
        }
        return list;
    }

    /**
     * @return the module that fluid arriving here is for
     */
    private LogisticsFluidModule getModuleFor(IAdditionalTargetInformation info, FluidStack liquid) {
        if (info instanceof ModuleSlotTargetInformation target
                && host.getModule(target.getModuleSlot()) instanceof LogisticsFluidModule module) {
            return module;
        }
        List<ModuleFluidSink> sinks = getModules(ModuleFluidSink.class);
        for (ModuleFluidSink sink : sinks) {
            if (sink.getFilter().contains(liquid)) {
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

    /*** arriving fluid ***/

    /**
     * @return true if the item is fluid on its way through the network
     */
    public static boolean isFluidInTransit(ItemIdentifierStack stack) {
        return stack.getItem().item instanceof LogisticsFluidContainer;
    }

    @Override
    public boolean wantsArrival(ItemIdentifierStack stack) {
        if (isFluidInTransit(stack)) {
            return true;
        }
        FluidStack fluid = FluidContainerUtil.getFluid(stack.getItem().makeNormalStack(1));
        if (fluid == null) {
            return false;
        }
        // with an inventory to put it in, a filled container is only emptied when a container sink asks for it
        if (pipe.getRealInventory() == null) {
            return true;
        }
        for (ModuleFluidContainerSink sink : getModules(ModuleFluidContainerSink.class)) {
            if (sink.getFilter().contains(fluid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Puts an arriving fluid container into the tank. What doesn't fit is sent to another sink in the network.
     */
    @Override
    public boolean takeArrival(LPTravelingItemServer arrivingItem) {
        ItemIdentifierStack stack = arrivingItem.getItemIdentifierStack();
        if (emptyArrivingContainers(arrivingItem, stack)) {
            return true;
        }
        if (!stack.getItem().isFluidContainer()) {
            return false;
        }
        // fluid on its way through the network, or a real item that holds fluid, like a large cell
        boolean realContainer = !isFluidInTransit(stack);
        ItemStack single = stack.getItem().makeNormalStack(1);
        FluidStack liquid = realContainer ? FluidContainerUtil.getFluid(single)
                : SimpleServiceLocator.logisticsFluidManager.getFluidFromContainer(stack);
        if (liquid == null) {
            return false;
        }
        if (realContainer) {
            // every one of the stack holds that much, and the empty ones are given back to the network
            liquid.amount = (int) Math.min(Integer.MAX_VALUE, (long) liquid.amount * stack.getStackSize());
            ItemStack empty = FluidContainerUtil.getEmptied(single);
            if (empty != null) {
                sendStacks(empty, stack.getStackSize());
            }
        }
        pipe.getCacheHolder().trigger(CacheTypes.Inventory);
        LogisticsFluidModule module = getModuleFor(getTargetInfo(arrivingItem), liquid);
        if (module != null) {
            liquid.amount -= module.acceptFluid(liquid);
        }
        if (liquid.amount > 0) {
            addPendingFluid(liquid);
            sendPendingFluids();
        }
        return true;
    }

    private static IAdditionalTargetInformation getTargetInfo(LPTravelingItemServer arrivingItem) {
        return arrivingItem.getInfo() == null ? null : arrivingItem.getInfo().targetInfo;
    }

    /**
     * Hands filled containers that were sent to a container sink module to that module. The emptied containers go back
     * into the network, and so do filled ones the tank had no room for.
     *
     * @return true if the item was for such a module and is handled
     */
    private boolean emptyArrivingContainers(LPTravelingItemServer arrivingItem, ItemIdentifierStack stack) {
        if (isFluidInTransit(stack)) {
            return false;
        }
        ItemStack single = stack.getItem().makeNormalStack(1);
        FluidStack fluid = FluidContainerUtil.getFluid(single);
        if (fluid == null) {
            return false;
        }
        ModuleFluidContainerSink module = getContainerSinkFor(getTargetInfo(arrivingItem), fluid);
        if (module == null) {
            return false;
        }
        pipe.getCacheHolder().trigger(CacheTypes.Inventory);
        int count = stack.getStackSize();
        int emptied = 0;
        while (emptied < count && module.acceptContainer(fluid, this::addPendingFluid)) {
            emptied++;
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
        if (info instanceof ModuleSlotTargetInformation target
                && host.getModule(target.getModuleSlot()) instanceof ModuleFluidContainerSink module) {
            return module;
        }
        for (ModuleFluidContainerSink sink : getModules(ModuleFluidContainerSink.class)) {
            if (sink.getFilter().contains(fluid)) {
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
            pipe.sendItem(stack);
        }
    }

    private void addPendingFluid(FluidStack liquid) {
        pendingFluids.add(liquid, ForgeDirection.UNKNOWN);
    }

    private void sendPendingFluids() {
        ForgeDirection pointed = pipe.getPointedOrientation();
        ForgeDirection from = pointed == null || pointed == ForgeDirection.UNKNOWN ? ForgeDirection.UP : pointed;
        for (Iterator<PendingFluids.Entry> iterator = pendingFluids.iterator(); iterator.hasNext();) {
            FluidStack pending = iterator.next().fluid;
            IRoutedItem routedItem = FluidSendUtil.createItemForSink(pending, pipe.getRouter());
            if (routedItem != null) {
                pipe.queueRoutedItem(routedItem, from);
            }
            if (pending.amount <= 0) {
                iterator.remove();
            }
        }
    }

    @Override
    public void tick() {
        if (pendingFluids.isEmpty() || !pipe.isNthTick(TICKS_PER_PENDING_RETRY)) {
            return;
        }
        // the tank may have room again
        for (PendingFluids.Entry pending : pendingFluids) {
            LogisticsFluidModule module = getModuleFor(null, pending.fluid);
            if (module != null) {
                pending.fluid.amount -= module.acceptFluid(pending.fluid);
            }
        }
        sendPendingFluids();
    }

    /*** IFluidSink ***/

    private int getSinkAmount(FluidStack stack, boolean filtered) {
        int amount = 0;
        for (ModuleFluidSink sink : getModules(ModuleFluidSink.class)) {
            if (sink.getFilter().contains(stack) == filtered) {
                amount = Math.max(amount, sink.sinkAmount(stack));
            }
        }
        return amount;
    }

    public int sinkAmount(FluidStack stack) {
        if (!hasModules || !pipe.isEnabled()) {
            return 0;
        }
        int amount = getSinkAmount(stack, true);
        if (amount <= 0) {
            amount = getSinkAmount(stack, false);
        }
        // what waits at the pipe for room has its place in the tank already
        return (int) Math.max(0, amount - pendingFluids.getAmount(FluidIdentifier.get(stack)));
    }

    public FixedPriority getFluidSinkPriority(FluidStack stack) {
        if (!hasModules) {
            return FixedPriority.DefaultRoute;
        }
        for (ModuleFluidSink sink : getModules(ModuleFluidSink.class)) {
            if (sink.getFilter().contains(stack)) {
                return FixedPriority.ItemSink;
            }
        }
        return FixedPriority.DefaultRoute;
    }

    /**
     * @return the highest priority of the fluid sinks that take the fluid the way {@link #getFluidSinkPriority} says:
     *         by their filter, else as a default route
     */
    public int getFluidSinkCustomPriority(FluidStack stack) {
        if (!hasModules) {
            return 0;
        }
        boolean filtered = getFluidSinkPriority(stack) != FixedPriority.DefaultRoute;
        int best = Integer.MIN_VALUE;
        for (ModuleFluidSink sink : getModules(ModuleFluidSink.class)) {
            if (filtered ? sink.getFilter().contains(stack) : sink.isDefaultRoute()) {
                best = Math.max(best, sink.getPriority());
            }
        }
        return best == Integer.MIN_VALUE ? 0 : best;
    }

    /*** IProvideFluids ***/

    public void canProvide(RequestTreeNode tree, RequestTree root, List<IFilter> filters) {
        if (!hasModules || !pipe.isEnabled()) {
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

    public int getReservedFluid(FluidIdentifier fluid) {
        // asked once for each fluid of the tank, so no list of the modules is made
        long reserved = 0;
        for (int i = 0; i < host.getSlotCount(); i++) {
            if (host.getModule(i) instanceof IFluidReserver module) {
                reserved += module.getReservedAmount(fluid);
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, reserved);
    }

    public Map<FluidIdentifier, Integer> getAvailableFluids() {
        Map<FluidIdentifier, Integer> result = new HashMap<>();
        if (!hasModules || !pipe.isEnabled()) {
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

    public IOrderInfoProvider fullFill(FluidLogisticsPromise promise, IRequestFluid destination, ResourceType type,
            IAdditionalTargetInformation info) {
        // promises are made by the provider modules and come back to them, this is only the fallback
        List<ModuleFluidProvider> providers = getProviders();
        return providers.isEmpty() ? null : providers.get(0).fullFill(promise, destination, type, info);
    }

    /*** interests ***/

    @Override
    public void addSpecificInterests(Set<ItemIdentifier> interests) {
        for (LogisticsFluidModule module : getModules(LogisticsFluidModule.class)) {
            Collection<ItemIdentifier> current = module.getSpecificInterests();
            if (current != null) {
                interests.addAll(current);
            }
        }
    }

    @Override
    public boolean hasGenericInterests() {
        for (LogisticsFluidModule module : getModules(LogisticsFluidModule.class)) {
            if (module.hasGenericInterests()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public CategoryInterests getCategoryInterests() {
        CategoryInterests interests = CategoryInterests.EMPTY;
        for (LogisticsFluidModule module : getModules(LogisticsFluidModule.class)) {
            interests = interests.merge(module.getCategoryInterests());
        }
        return interests;
    }

    /*** save ***/

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        pendingFluids.readFromNBT(nbttagcompound, PENDING_KEY);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        if (!pendingFluids.isEmpty()) {
            pendingFluids.writeToNBT(nbttagcompound, PENDING_KEY);
        }
    }
}
