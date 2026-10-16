package logisticspipes.crafting;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import logisticspipes.config.Configs;
import logisticspipes.crafting.patternStack.PatternFluidStack;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.pipefxhandlers.Particles;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.routing.order.LogisticsFluidOrder;
import logisticspipes.routing.order.LogisticsItemOrder;
import logisticspipes.routing.order.LogisticsOrder;
import logisticspipes.utils.AdjacentTile;
import logisticspipes.utils.CacheHolder.CacheTypes;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * Drains completed pattern crafting outputs from the selected adjacent inventory or fluid handler.
 * <p>
 * Normal crafting orders are routed to their requester. Extra orders have no requester, so they are sent back through
 * normal storage routing and may drop if no storage accepts them. Keeping this logic outside
 * {@link ModulePatternCrafting} keeps the module focused on request planning and buffer state.
 */
class PatternCraftingResultExtractor {

    private static final int MAX_EXTRACTED_ITEMS_PER_TICK = 64;
    private static final int MAX_EXTRACTED_STACKS_PER_TICK = 16;

    private final ModulePatternCrafting module;
    private final PipeItemsPatternCraftingLogistics pipe;
    private final AdjacentInventoryHandler adjacentInventory;
    private final PatternByproductExtractionTargetCache remoteByproducts;

    /**
     * Creates an extractor for one pattern crafting module and its selected adjacent handlers.
     */
    PatternCraftingResultExtractor(ModulePatternCrafting module, PipeItemsPatternCraftingLogistics pipe,
            AdjacentInventoryHandler adjacentInventory) {
        this.module = module;
        this.pipe = pipe;
        this.adjacentInventory = adjacentInventory;
        remoteByproducts = new PatternByproductExtractionTargetCache(pipe);
    }

    /**
     * Attempts to extract both item and fluid outputs for this tick.
     */
    void tick() {
        extractItemsFromAdjacentInventory();
        extractFluidsFromAdjacentHandlers();
    }

    /**
     * Drains completed craft results, including extra and byproduct orders that were produced by the same staged craft.
     */
    private void extractItemsFromAdjacentInventory() {
        if (!pipe.isNthTick(6)) {
            return;
        }

        var orderManager = pipe.getItemOrderManager();
        if (!orderManager.hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA)) {
            return;
        }

        int itemsLeft = MAX_EXTRACTED_ITEMS_PER_TICK;
        int stacksLeft = MAX_EXTRACTED_STACKS_PER_TICK;
        int ordersLeftToTry = orderManager.getAllOrders().size();
        boolean extractedAny = false;

        while (itemsLeft > 0 && stacksLeft > 0
                && ordersLeftToTry > 0
                && orderManager.hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA)) {
            LogisticsItemOrder order = orderManager.peekAtTopRequest(ResourceType.CRAFTING, ResourceType.EXTRA);
            if (order == null) {
                break;
            }
            boolean samePipe = module.isOrderDestinationThisModule(order)
                    && order.getInformation() instanceof PatternTargetInformation;
            int samePipeRequested = module.requestedSamePipeItemAmount(order);
            module.debugEventThrottled(
                    "FLOW",
                    60,
                    "extract item top order item=%s amount=%d destination=%s info=%s samePipe=%s localRequested=%d itemsLeft=%d stacksLeft=%d",
                    order.getResource().getItem(),
                    order.getAmount(),
                    order.getDestination(),
                    order.getInformation(),
                    samePipe,
                    samePipeRequested,
                    itemsLeft,
                    stacksLeft);
            int maxToSend = maxExtractableItemAmount(order, itemsLeft);
            if (maxToSend <= 0) {
                module.debugEventThrottled(
                        "FLOW",
                        60,
                        "extract item deferred order=%s amount=%d destination=%s info=%s localRequested=%d",
                        order.getResource().getItem(),
                        order.getAmount(),
                        order.getDestination(),
                        order.getInformation(),
                        samePipeRequested);
                orderManager.deferSend();
                ordersLeftToTry--;
                continue;
            }

            PatternByproductTarget remoteTarget = remoteByproductTarget(order, false);
            if (remoteTarget != null) {
                PatternByproductExtractionResult remoteExtraction = remoteByproducts.extractItem(
                        remoteTarget,
                        order.getResource().getItem(),
                        maxToSend,
                        remoteDestination(order),
                        order.getInformation());
                if (remoteExtraction.amount() > 0) {
                    module.debugEvent(
                            "FLOW",
                            "extract item byproduct success order=%s extracted=%d source=pattern-satellite outputSlot=%d",
                            order.getResource().getItem(),
                            remoteExtraction.amount(),
                            remoteTarget.getOutputSlot());
                    extractedAny = true;
                    itemsLeft -= remoteExtraction.amount();
                    stacksLeft--;
                    orderManager.sendSuccessfull(remoteExtraction.amount(), false, remoteExtraction.routedItem());
                    ordersLeftToTry = orderManager.getAllOrders().size();
                    continue;
                }
                module.debugEventThrottled(
                        "FLOW",
                        60,
                        "extract item byproduct deferred order=%s amount=%d satellite=%s",
                        order.getResource().getItem(),
                        maxToSend,
                        remoteTarget.getSatelliteUuid());
                orderManager.deferSend();
                ordersLeftToTry--;
                continue;
            }

            ItemStack extracted = adjacentInventory.hasConnectedTE()
                    ? adjacentInventory.extract(order.getResource(), maxToSend)
                    : null;
            if (extracted == null || extracted.stackSize <= 0) {
                module.debugEventThrottled(
                        "FLOW",
                        60,
                        "extract item deferred order=%s amount=%d maxToSend=%d",
                        order.getResource().getItem(),
                        order.getAmount(),
                        maxToSend);
                orderManager.deferSend();
                ordersLeftToTry--;
                continue;
            }

            module.debugEvent(
                    "FLOW",
                    "extract item success order=%s extracted=%d maxToSend=%d source=%s",
                    order.getResource().getItem(),
                    extracted.stackSize,
                    maxToSend,
                    adjacentInventory.getConnected());

            extractedAny = true;
            itemsLeft -= extracted.stackSize;
            stacksLeft--;

            pipe.spawnParticle(Particles.VioletParticle, 2);
            pipe.getCacheHolder().trigger(CacheTypes.Inventory);
            sendExtracted(order, extracted, adjacentInventory.getConnected().orientation);
            ordersLeftToTry = orderManager.getAllOrders().size();
        }

        if (extractedAny) module.requestIngredientsForStagedCrafts();
    }

    private int maxExtractableItemAmount(LogisticsItemOrder order, int itemsLeft) {
        int maxToSend = Math.min(itemsLeft, order.getAmount());
        maxToSend = Math.min(maxToSend, order.getResource().getItem().getMaxStackSize());
        PatternCraftingOrder source = PatternCraftingInstanceRegistry.find(order);
        if (source != null) {
            maxToSend = Math.min(maxToSend, source.extractableOutputAmount());
        }
        if (module.isOrderDestinationThisModule(order) && order.getInformation() instanceof PatternTargetInformation) {
            PatternCraftingOrder stagedOrder = PatternCraftingMonitorRegistry.find(order);
            if (stagedOrder != null && !stagedOrder.isFullyRequested()) {
                module.debugEventThrottled(
                        "FLOW",
                        60,
                        "extract item deferred order=%s amount=%d: staged ingredients pending slot=%d remainingSets=%d",
                        order.getResource().getItem(),
                        order.getAmount(),
                        stagedOrder.patternSlot,
                        stagedOrder.remainingSets);
                return 0;
            }
            int requested = module.requestedSamePipeItemAmount(order);
            if (requested > 0) {
                maxToSend = Math.min(maxToSend, requested);
            }
        }
        return maxToSend;
    }

    /**
     * Routes an extracted item result either to its requester or, for extra outputs, back through normal storage
     * routing.
     */
    private void sendExtracted(LogisticsItemOrder order, ItemStack extracted, ForgeDirection orientation) {
        if (module.isOrderDestinationThisModule(order) && order.getInformation() instanceof PatternTargetInformation) {
            sendExtractedToLocalBuffer(order, extracted);
            return;
        }
        if (order.getDestination() != null) {
            IRoutedItem item = SimpleServiceLocator.routedItemHelper.createNewTravelItem(extracted);
            item.setDestination(order.getDestination().getRouter().getSimpleID());
            item.setTransportMode(TransportMode.Active);
            item.setAdditionalTargetInformation(order.getInformation());
            pipe.queueRoutedItem(item, orientation);
            pipe.getItemOrderManager().sendSuccessfull(extracted.stackSize, false, item);
            module.debugEvent(
                    "FLOW",
                    "sent extracted item=%s amount=%d destination=%d",
                    ItemIdentifier.get(extracted),
                    extracted.stackSize,
                    order.getDestination().getRouter().getSimpleID());
        } else {
            pipe.sendStack(extracted, -1, CoreRoutedPipe.ItemSendMode.Normal, order.getInformation());
            pipe.getItemOrderManager().sendSuccessfull(extracted.stackSize, false, null);
            module.debugEvent(
                    "FLOW",
                    "sent extracted item=%s amount=%d without routed destination",
                    ItemIdentifier.get(extracted),
                    extracted.stackSize);
        }
    }

    /**
     * Hands a same-pipe intermediate result directly to the parent pattern buffer.
     * <p>
     * Routing an item to the pipe that just extracted it can make the order manager and transport retry logic disagree
     * about whether the intermediate result is already delivered. Direct arrival keeps the local requested buffer and
     * the live output order in lockstep.
     */
    private void sendExtractedToLocalBuffer(LogisticsItemOrder order, ItemStack extracted) {
        ItemIdentifierStack arrived = ItemIdentifierStack.getFromStack(extracted);
        int original = arrived.getStackSize();
        int orderBefore = order.getAmount();
        int requestedBefore = module.requestedSamePipeItemAmount(order);
        module.itemArrived(arrived, order.getInformation());
        int accepted = original - arrived.getStackSize();
        if (accepted > 0) {
            pipe.getItemOrderManager().sendSuccessfull(accepted, false, null);
        }
        module.debugEvent(
                "FLOW",
                "accepted extracted same-pipe item=%s extracted=%d accepted=%d unaccepted=%d orderBefore=%d orderAfter=%d requestedBefore=%d info=%s",
                ItemIdentifier.get(extracted),
                original,
                accepted,
                arrived.getStackSize(),
                orderBefore,
                order.getAmount(),
                requestedBefore,
                order.getInformation());
        if (arrived.getStackSize() > 0) {
            int unaccepted = arrived.getStackSize();
            pipe.sendStack(arrived.makeNormalStack(), -1, CoreRoutedPipe.ItemSendMode.Normal, null);
            pipe.getItemOrderManager().sendSuccessfull(unaccepted, false, null);
            module.debugEvent(
                    "FLOW",
                    "sent unaccepted same-pipe remainder item=%s amount=%d",
                    arrived.getItem(),
                    unaccepted);
        }
    }

    /**
     * Drains completed fluid craft results, including extra and byproduct orders from the connected fluid handler.
     */
    private void extractFluidsFromAdjacentHandlers() {
        if (!pipe.isNthTick(6)
                || !pipe.getPatternFluidOrderManager().hasOrders(ResourceType.CRAFTING, ResourceType.EXTRA)) {
            return;
        }
        LogisticsFluidOrder order = pipe.getPatternFluidOrderManager()
                .peekAtTopRequest(ResourceType.CRAFTING, ResourceType.EXTRA);
        if (order == null) {
            module.debugEventThrottled("FLOW", "extract fluids skipped: no top fluid order");
            return;
        }
        PatternByproductTarget remoteTarget = remoteByproductTarget(order, true);
        List<AdjacentTile> handlers = remoteTarget == null ? adjacentInventory.locateFluidHandlers()
                : java.util.Collections.emptyList();
        if (handlers.isEmpty() && remoteTarget == null) {
            module.debugEventThrottled("FLOW", "extract fluids failed: no adjacent fluid handlers");
            pipe.getPatternFluidOrderManager().sendFailed();
            return;
        }

        boolean samePipe = module.isOrderDestinationThisModule(order)
                && order.getInformation() instanceof PatternTargetInformation;
        int samePipeRequested = module.requestedSamePipeFluidAmount(order);
        module.debugEventThrottled(
                "FLOW",
                60,
                "extract fluid top order fluid=%s amount=%d destination=%s info=%s samePipe=%s localRequested=%d handlers=%d",
                order.getFluid(),
                order.getAmount(),
                order.getDestination(),
                order.getInformation(),
                samePipe,
                samePipeRequested,
                handlers.size());
        int amountToDrain = maxExtractableFluidAmount(order);
        if (amountToDrain <= 0) {
            module.debugEventThrottled(
                    "FLOW",
                    60,
                    "extract fluid deferred fluid=%s amount=%d destination=%s info=%s localRequested=%d",
                    order.getFluid(),
                    order.getAmount(),
                    order.getDestination(),
                    order.getInformation(),
                    samePipeRequested);
            pipe.getPatternFluidOrderManager().deferSend();
            return;
        }
        if (remoteTarget != null) {
            PatternByproductExtractionResult remoteExtraction = remoteByproducts.extractFluid(
                    remoteTarget,
                    order.getFluid(),
                    amountToDrain,
                    remoteDestination(order),
                    order.getInformation());
            if (remoteExtraction.amount() > 0) {
                module.debugEvent(
                        "FLOW",
                        "extract fluid byproduct success fluid=%s amount=%d source=pattern-satellite outputSlot=%d",
                        order.getFluid(),
                        remoteExtraction.amount(),
                        remoteTarget.getOutputSlot());
                pipe.getPatternFluidOrderManager()
                        .sendSuccessfull(remoteExtraction.amount(), false, remoteExtraction.routedItem());
                module.requestIngredientsForStagedCrafts();
                return;
            }
            module.debugEventThrottled(
                    "FLOW",
                    60,
                    "extract fluid byproduct deferred fluid=%s amount=%d satellite=%s",
                    order.getFluid(),
                    amountToDrain,
                    remoteTarget.getSatelliteUuid());
            pipe.getPatternFluidOrderManager().deferSend();
            return;
        }
        PatternFluidStack wanted = new PatternFluidStack(order.getFluid(), amountToDrain);
        for (AdjacentTile tile : handlers) {
            FluidStack drained = adjacentInventory.extractFluid(tile, wanted, amountToDrain);
            if (drained == null || drained.amount <= 0) {
                continue;
            }
            module.debugEvent(
                    "FLOW",
                    "extract fluid success fluid=%s amount=%d amountToDrain=%d source=%s",
                    order.getFluid(),
                    drained.amount,
                    amountToDrain,
                    tile.tile);
            sendExtractedFluid(order, drained, tile.orientation);
            pipe.getCacheHolder().trigger(CacheTypes.Inventory);
            module.requestIngredientsForStagedCrafts();
            return;
        }
        module.debugEventThrottled(
                "FLOW",
                60,
                "extract fluid deferred fluid=%s amount=%d",
                order.getFluid(),
                amountToDrain);
        pipe.getPatternFluidOrderManager().deferSend();
    }

    private int maxExtractableFluidAmount(LogisticsFluidOrder order) {
        int amountToDrain = Math.min(order.getAmount(), Configs.MAX_LOGISTICS_FLUID_TRANSPORT_INNER_CAPACITY / 2);
        PatternCraftingOrder source = PatternCraftingInstanceRegistry.find(order);
        if (source != null) {
            amountToDrain = Math.min(amountToDrain, source.extractableOutputAmount());
        }
        if (module.isOrderDestinationThisModule(order) && order.getInformation() instanceof PatternTargetInformation) {
            PatternCraftingOrder stagedOrder = PatternCraftingMonitorRegistry.find(order);
            if (stagedOrder != null && !stagedOrder.isFullyRequested()) {
                module.debugEventThrottled(
                        "FLOW",
                        60,
                        "extract fluid deferred fluid=%s amount=%d: staged ingredients pending slot=%d remainingSets=%d",
                        order.getFluid(),
                        order.getAmount(),
                        stagedOrder.patternSlot,
                        stagedOrder.remainingSets);
                return 0;
            }
            int requested = module.requestedSamePipeFluidAmount(order);
            if (requested > 0) {
                amountToDrain = Math.min(amountToDrain, requested);
            }
        }
        return amountToDrain;
    }

    /**
     * Routes an extracted fluid result either to its requester or, for extra outputs, back through normal storage
     * routing.
     */
    private void sendExtractedFluid(LogisticsFluidOrder order, FluidStack drained, ForgeDirection orientation) {
        if (module.isOrderDestinationThisModule(order) && order.getInformation() instanceof PatternTargetInformation) {
            sendExtractedFluidToLocalBuffer(order, drained);
            return;
        }
        if (order.getDestination() != null) {
            IRoutedItem item = SimpleServiceLocator.routedItemHelper
                    .createNewTravelItem(SimpleServiceLocator.logisticsFluidManager.getFluidContainer(drained));
            item.setDestination(order.getRouter().getSimpleID());
            item.setTransportMode(TransportMode.Active);
            item.setAdditionalTargetInformation(order.getInformation());
            pipe.queueRoutedItem(item, orientation);
            pipe.getPatternFluidOrderManager().sendSuccessfull(drained.amount, false, item);
            module.debugEvent(
                    "FLOW",
                    "sent extracted fluid=%s amount=%d destination=%d",
                    order.getFluid(),
                    drained.amount,
                    order.getDestination().getRouter().getSimpleID());
        } else {
            pipe.sendStack(
                    SimpleServiceLocator.logisticsFluidManager.getFluidContainer(drained).makeNormalStack(),
                    -1,
                    CoreRoutedPipe.ItemSendMode.Normal,
                    order.getInformation());
            pipe.getPatternFluidOrderManager().sendSuccessfull(drained.amount, false, null);
            module.debugEvent(
                    "FLOW",
                    "sent extracted fluid=%s amount=%d without routed destination",
                    order.getFluid(),
                    drained.amount);
        }
    }

    private void sendExtractedFluidToLocalBuffer(LogisticsFluidOrder order, FluidStack drained) {
        ItemIdentifierStack arrived = ItemIdentifierStack
                .getFromStack(SimpleServiceLocator.logisticsFluidManager.getFluidContainer(drained).makeNormalStack());
        int orderBefore = order.getAmount();
        int requestedBefore = module.requestedSamePipeFluidAmount(order);
        module.itemArrived(arrived, order.getInformation());
        if (arrived.getStackSize() <= 0) {
            pipe.getPatternFluidOrderManager().sendSuccessfull(drained.amount, false, null);
            module.debugEvent(
                    "FLOW",
                    "accepted extracted same-pipe fluid=%s amount=%d orderBefore=%d orderAfter=%d requestedBefore=%d info=%s",
                    order.getFluid(),
                    drained.amount,
                    orderBefore,
                    order.getAmount(),
                    requestedBefore,
                    order.getInformation());
            return;
        }
        pipe.sendStack(arrived.makeNormalStack(), -1, CoreRoutedPipe.ItemSendMode.Normal, null);
        pipe.getPatternFluidOrderManager().sendSuccessfull(drained.amount, false, null);
        module.debugEvent(
                "FLOW",
                "sent unaccepted same-pipe fluid container fluid=%s amount=%d remainingContainers=%d orderBefore=%d orderAfter=%d requestedBefore=%d info=%s",
                order.getFluid(),
                drained.amount,
                arrived.getStackSize(),
                orderBefore,
                order.getAmount(),
                requestedBefore,
                order.getInformation());
    }

    private PatternByproductTarget remoteByproductTarget(LogisticsOrder order, boolean fluid) {
        PatternByproductTarget target = order.getByproductTarget();
        return target != null && target.isConfigured() && target.isFluid() == fluid ? target : null;
    }

    private int remoteDestination(LogisticsOrder order) {
        return order.getRouter() == null ? -1 : order.getRouter().getSimpleID();
    }
}
