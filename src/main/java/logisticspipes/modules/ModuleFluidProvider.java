package logisticspipes.modules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraftforge.fluids.FluidStack;

import logisticspipes.config.Configs;
import logisticspipes.crafting.v3.exec.CraftDelivery;
import logisticspipes.crafting.v3.mc.CraftingService;
import logisticspipes.crafting.v3.mc.FluidKey;
import logisticspipes.crafting.v3.mc.ICraftingStockSource;
import logisticspipes.crafting.v3.model.ResourceKey;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.dynamicModules.ModuleFluidInfoMuiDynamic;
import logisticspipes.interfaces.IFluidModuleHost;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.interfaces.routing.IProvideFluids;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.modules.abstractmodules.LogisticsFluidModule;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTreeNode;
import logisticspipes.request.resources.FluidResource;
import logisticspipes.routing.FluidLogisticsPromise;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.routing.order.LogisticsFluidOrder;
import logisticspipes.routing.order.LogisticsFluidOrderManager;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;

/**
 * Offers the fluids in the tank to requests from the network, like the fluid provider pipe.
 */
public class ModuleFluidProvider extends LogisticsFluidModule
        implements IProvideFluids, IFluidReserver, ICraftingStockSource {

    private static final int TICKS_PER_SEND = 6;

    private LogisticsFluidOrderManager orderManager;

    private LogisticsFluidOrderManager getOrderManager() {
        if (orderManager == null) {
            orderManager = new LogisticsFluidOrderManager(getHost());
        }
        return orderManager;
    }

    @Override
    public void tick() {
        IFluidModuleHost host = getHost();
        if (host == null || orderManager == null
                || !orderManager.hasOrders(ResourceType.PROVIDER)
                || !_service.isNthTick(TICKS_PER_SEND)) {
            return;
        }
        LogisticsFluidOrder order = orderManager.peekAtTopRequest(ResourceType.PROVIDER);
        IRouter destination = order.getRouter();
        if (destination == null) {
            orderManager.sendFailed();
            return;
        }
        int amount = Math.min(order.getAmount(), Configs.MAX_LOGISTICS_FLUID_TRANSPORT_INNER_CAPACITY / 2);
        FluidStack drained = drainTank(order.getFluid(), amount);
        if (drained == null || drained.amount <= 0) {
            orderManager.sendFailed();
            return;
        }
        IRoutedItem item = sendFluid(drained, destination.getSimpleID(), TransportMode.Active, order.getInformation());
        orderManager.sendSuccessfull(drained.amount, false, item);
    }

    @Override
    public void onRemoved() {
        if (orderManager == null) {
            return;
        }
        while (orderManager.hasOrders(ResourceType.PROVIDER)) {
            orderManager.sendFailed();
        }
    }

    @Override
    public void canProvide(RequestTreeNode tree, RequestTree root, List<IFilter> filter) {
        if (getHost() == null || tree.isDone() || !(tree.getRequestType() instanceof FluidResource resource)) {
            return;
        }
        FluidIdentifier fluid = resource.getFluid();
        Integer available = getAvailableFluids().get(fluid);
        if (available == null) {
            return;
        }
        int amount = Math
                .min(tree.getMissingAmount(), available - root.getAllPromissesFor(this, fluid.getItemIdentifier()));
        if (amount > 0) {
            tree.addPromise(new FluidLogisticsPromise(fluid, amount, this, ResourceType.PROVIDER));
        }
    }

    /**
     * @return the fluids in the tank that are not reserved by an order yet
     */
    @Override
    public Map<FluidIdentifier, Integer> getAvailableFluids() {
        Map<FluidIdentifier, Integer> result = new HashMap<>();
        if (getHost() == null) {
            return result;
        }
        for (Entry<FluidIdentifier, Integer> entry : getTankContents().entrySet()) {
            // what any module of the chassis promised is taken, not only what this one did, and what crafting jobs
            // reserved here
            int remaining = entry.getValue() - getHost().getReservedFluid(entry.getKey())
                    - (int) Math.min(Integer.MAX_VALUE, craftingReserved(entry.getKey()));
            if (remaining > 0) {
                result.put(entry.getKey(), remaining);
            }
        }
        return result;
    }

    private long craftingReserved(FluidIdentifier fluid) {
        IRouter router = getRouter();
        return router == null ? 0 : CraftingService.INSTANCE.reserved(router.getId(), FluidKey.of(fluid));
    }

    // ------------------------------------------------------------------ crafting stock source

    @Override
    public void listCraftingStock(Map<ResourceKey, Long> into) {
        if (getHost() == null) {
            return;
        }
        for (Entry<FluidIdentifier, Integer> entry : getTankContents().entrySet()) {
            long count = entry.getValue() - getHost().getReservedFluid(entry.getKey());
            if (count > 0) {
                // several fluid provider modules of one chassis see the same tank: count it once
                into.putIfAbsent(FluidKey.of(entry.getKey()), count);
            }
        }
    }

    @Override
    public long craftingStockCount(ResourceKey key) {
        if (!(key instanceof FluidKey) || getHost() == null) {
            return 0;
        }
        FluidIdentifier fluid = ((FluidKey) key).id;
        Integer inTank = getTankContents().get(fluid);
        return inTank == null ? 0 : Math.max(0, inTank - getHost().getReservedFluid(fluid));
    }

    /** Drains and routes fluid for the crafting driver right away. */
    @Override
    public long sendForCrafting(ResourceKey key, long qty, int destination, CraftDelivery delivery) {
        if (!(key instanceof FluidKey) || qty <= 0 || getHost() == null) {
            return 0;
        }
        FluidStack drained = drainTank(((FluidKey) key).id, (int) Math.min(qty, FluidKey.MAX_PER_CONTAINER));
        if (drained == null || drained.amount <= 0) {
            return 0;
        }
        sendFluid(drained, destination, TransportMode.Active, delivery);
        return drained.amount;
    }

    @Override
    public IOrderInfoProvider fullFill(FluidLogisticsPromise promise, IRequestFluid destination, ResourceType type,
            IAdditionalTargetInformation info) {
        return getOrderManager().addOrder(promise, destination, type, info);
    }

    @Override
    public int getReservedAmount(FluidIdentifier fluid) {
        return orderManager == null ? 0 : orderManager.totalFluidsCountInOrders(fluid);
    }

    @Override
    public IRouter getRouter() {
        IFluidModuleHost host = getHost();
        return host == null ? null : host.getRouter();
    }

    @Override
    public Collection<ItemIdentifier> getSpecificInterests() {
        List<ItemIdentifier> interests = new ArrayList<>();
        for (FluidIdentifier fluid : getTankContents().keySet()) {
            interests.add(fluid.getItemIdentifier());
        }
        return interests;
    }

    @Override
    public boolean hasGui() {
        return false;
    }

    @Override
    public LogisticsModularUI getHandGui() {
        return new ModuleFluidInfoMuiDynamic(this, "", "gui.module.ModuleFluidProvider");
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        return new ModuleFluidInfoMuiDynamic(this, "", "gui.module.ModuleFluidProvider");
    }

    @Override
    public LogisticsModularUI getPipeGui(String prefix) {
        return new ModuleFluidInfoMuiDynamic(this, prefix, "gui.module.ModuleFluidProvider");
    }
}
