package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import logisticspipes.interfaces.IInventoryUtil;
import logisticspipes.interfaces.ISpecialTankAccessHandler;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.pipefxhandlers.Particles;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.basic.fluid.FluidRoutedPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.routing.IRouter;
import logisticspipes.utils.AdjacentTile;
import logisticspipes.utils.CacheHolder.CacheTypes;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.SidedInventoryMinecraftAdapter;
import logisticspipes.utils.WorldUtil;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * Extracts exact, already-ordered byproducts at a pattern satellite and routes them to their requester or storage.
 *
 * <p>
 * The adjacent handlers are cached because an active recipe can leave an extra order pending for many ticks. The cache
 * is deliberately short-lived so block changes are picked up without turning every extraction retry into a complete
 * adjacency scan.
 * </p>
 */
final class PatternSatelliteByproductExtractor {

    private static final int ADJACENT_HANDLER_CACHE_TICKS = 40;

    private final CoreRoutedPipe satellite;
    private List<AdjacentTile> itemTargets = Collections.emptyList();
    private List<AdjacentTile> fluidTargets = Collections.emptyList();
    private long itemTargetsValidUntil = Long.MIN_VALUE;
    private long fluidTargetsValidUntil = Long.MIN_VALUE;

    PatternSatelliteByproductExtractor(CoreRoutedPipe satellite) {
        this.satellite = satellite;
    }

    private static int fluidEnergy(int amount) {
        return 1 + (amount - 1) / 1000;
    }

    boolean canExtractFor(IRouter requester) {
        if (requester == null || satellite.getWorld() == null
                || MainProxy.isClient(satellite.getWorld())
                || satellite.getContainer() == null
                || satellite.getContainer().isInvalid()) {
            return false;
        }
        try {
            IRouter target = satellite.getRouter();
            return target != null && (requester == target || !target.getDistanceTo(requester).isEmpty());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    PatternByproductExtractionResult extractItem(ItemIdentifier item, int amount, int destination,
            IAdditionalTargetInformation info) {
        if (item == null || amount <= 0 || satellite.getWorld() == null || MainProxy.isClient(satellite.getWorld())) {
            return PatternByproductExtractionResult.empty();
        }
        for (AdjacentTile target : getItemTargets()) {
            IInventory inventory = extractionInventory(target);
            if (inventory == null) {
                continue;
            }
            IInventoryUtil inventoryUtil = SimpleServiceLocator.inventoryUtilFactory
                    .getInventoryUtil(inventory, target.orientation.getOpposite());
            int available = inventoryUtil.itemCount(item);
            int toExtract = Math.min(amount, available);
            if (toExtract <= 0 || !satellite.useEnergy(toExtract)) {
                continue;
            }
            ItemStack extracted = inventoryUtil.getMultipleItems(item, toExtract);
            if (extracted == null || extracted.stackSize <= 0) {
                continue;
            }
            IRoutedItem routedItem = queueItem(extracted, target.orientation, destination, info);
            extractionSucceeded();
            return new PatternByproductExtractionResult(extracted.stackSize, routedItem);
        }
        return PatternByproductExtractionResult.empty();
    }

    PatternByproductExtractionResult extractFluid(FluidIdentifier fluid, int amount, int destination,
            IAdditionalTargetInformation info) {
        if (fluid == null || amount <= 0 || satellite.getWorld() == null || MainProxy.isClient(satellite.getWorld())) {
            return PatternByproductExtractionResult.empty();
        }
        for (AdjacentTile target : getFluidTargets()) {
            if (SimpleServiceLocator.specialTankHandler.hasHandlerFor(target.tile)
                    && SimpleServiceLocator.specialTankHandler
                            .getTankHandlerFor(target.tile) instanceof ISpecialTankAccessHandler special) {
                FluidStack simulated = special.drainFrom(target.tile, fluid, amount, false);
                if (simulated == null || simulated.amount <= 0
                        || !fluid.equals(FluidIdentifier.get(simulated))
                        || !satellite.useEnergy(fluidEnergy(Math.min(amount, simulated.amount)))) {
                    continue;
                }
                FluidStack drained = special.drainFrom(target.tile, fluid, Math.min(amount, simulated.amount), true);
                if (drained != null && drained.amount > 0 && fluid.equals(FluidIdentifier.get(drained))) {
                    IRoutedItem routedItem = queueFluid(drained, target.orientation, destination, info);
                    extractionSucceeded();
                    return new PatternByproductExtractionResult(drained.amount, routedItem);
                }
                continue;
            }
            if (!(target.tile instanceof IFluidHandler)) continue;
            IFluidHandler handler = (IFluidHandler) target.tile;
            ForgeDirection side = target.orientation.getOpposite();
            FluidStack requested = fluid.makeFluidStack(amount);
            FluidStack simulated = handler.drain(side, requested, false);
            boolean typedDrain = simulated != null && simulated.amount > 0
                    && fluid.equals(FluidIdentifier.get(simulated));
            if (!typedDrain) {
                // Some hatch handlers implement only the amount-based drain overload.
                // Simulate on the connected face and reject other fluids before changing the tank.
                simulated = handler.drain(side, amount, false);
            }
            if (simulated == null || simulated.amount <= 0
                    || !fluid.equals(FluidIdentifier.get(simulated))
                    || !satellite.useEnergy(fluidEnergy(Math.min(amount, simulated.amount)))) {
                continue;
            }
            int toDrain = Math.min(amount, simulated.amount);
            FluidStack drained = typedDrain ? handler.drain(side, fluid.makeFluidStack(toDrain), true)
                    : handler.drain(side, toDrain, true);
            if (drained == null || drained.amount <= 0 || !fluid.equals(FluidIdentifier.get(drained))) {
                continue;
            }
            IRoutedItem routedItem = queueFluid(drained, target.orientation, destination, info);
            extractionSucceeded();
            return new PatternByproductExtractionResult(drained.amount, routedItem);
        }
        return PatternByproductExtractionResult.empty();
    }

    private List<AdjacentTile> getItemTargets() {
        long now = satellite.getWorld().getTotalWorldTime();
        if (now >= itemTargetsValidUntil || containsInvalidTile(itemTargets)) {
            itemTargets = findTargets(true);
            itemTargetsValidUntil = now + ADJACENT_HANDLER_CACHE_TICKS;
        }
        return itemTargets;
    }

    private List<AdjacentTile> getFluidTargets() {
        long now = satellite.getWorld().getTotalWorldTime();
        if (now >= fluidTargetsValidUntil || containsInvalidTile(fluidTargets)) {
            fluidTargets = findTargets(false);
            fluidTargetsValidUntil = now + ADJACENT_HANDLER_CACHE_TICKS;
        }
        return fluidTargets;
    }

    private List<AdjacentTile> findTargets(boolean items) {
        List<AdjacentTile> targets = new ArrayList<>();
        if (!items && satellite instanceof FluidRoutedPipe fluidPipe) {
            for (var tank : fluidPipe.getAdjacentTanks(false)) {
                targets.add(new AdjacentTile(tank.getValue1(), tank.getValue2()));
            }
            return Collections.unmodifiableList(targets);
        }
        ForgeDirection pointed = satellite.getPointedOrientation();
        WorldUtil worldUtil = new WorldUtil(satellite.getWorld(), satellite.getX(), satellite.getY(), satellite.getZ());
        for (AdjacentTile target : worldUtil.getAdjacentTileEntities(true)) {
            if (target == null || target.tile == null
                    || SimpleServiceLocator.pipeInformationManager.isItemPipe(target.tile)
                    || SimpleServiceLocator.pipeInformationManager.isFluidPipe(target.tile)
                    || (items && !(target.tile instanceof IInventory))
                    || (!items && !(target.tile instanceof IFluidHandler))) {
                continue;
            }
            if (target.orientation == pointed) {
                targets.add(0, target);
            } else {
                targets.add(target);
            }
        }
        return Collections.unmodifiableList(targets);
    }

    private boolean containsInvalidTile(List<AdjacentTile> targets) {
        for (AdjacentTile target : targets) {
            if (target == null || target.tile == null || target.tile.isInvalid()) {
                return true;
            }
        }
        return false;
    }

    private IInventory extractionInventory(AdjacentTile target) {
        if (!(target.tile instanceof IInventory inventory)) {
            return null;
        }
        if (inventory instanceof ISidedInventory) {
            return new SidedInventoryMinecraftAdapter(
                    (ISidedInventory) inventory,
                    target.orientation.getOpposite(),
                    true);
        }
        return inventory;
    }

    private IRoutedItem queueItem(ItemStack stack, ForgeDirection from, int destination,
            IAdditionalTargetInformation info) {
        IRoutedItem routedItem = SimpleServiceLocator.routedItemHelper.createNewTravelItem(stack);
        routedItem.setDestination(destination);
        routedItem.setTransportMode(TransportMode.Active);
        routedItem.setAdditionalTargetInformation(info);
        satellite.queueRoutedItem(routedItem, safeDirection(from));
        return routedItem;
    }

    private IRoutedItem queueFluid(FluidStack fluid, ForgeDirection from, int destination,
            IAdditionalTargetInformation info) {
        ItemIdentifierStack container = SimpleServiceLocator.logisticsFluidManager.getFluidContainer(fluid);
        IRoutedItem routedItem = SimpleServiceLocator.routedItemHelper.createNewTravelItem(container);
        routedItem.setDestination(destination);
        routedItem.setTransportMode(TransportMode.Active);
        routedItem.setAdditionalTargetInformation(info);
        satellite.queueRoutedItem(routedItem, safeDirection(from));
        return routedItem;
    }

    private ForgeDirection safeDirection(ForgeDirection direction) {
        return direction == null ? ForgeDirection.UNKNOWN : direction;
    }

    private void extractionSucceeded() {
        satellite.getCacheHolder().trigger(CacheTypes.Inventory);
        satellite.spawnParticle(Particles.VioletParticle, 2);
    }
}
