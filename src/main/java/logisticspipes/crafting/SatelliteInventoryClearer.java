package logisticspipes.crafting;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import logisticspipes.config.Configs;
import logisticspipes.interfaces.ISpecialTankAccessHandler;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.CacheHolder.CacheTypes;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.WorldUtil;

/** Manual inventory clearing, independent of crafting orders, upgrades, power and sink availability. */
public final class SatelliteInventoryClearer {

    private SatelliteInventoryClearer() {}

    public static void clearInventory(CoreRoutedPipe pipe) {
        if (pipe.getWorld() == null || MainProxy.isClient(pipe.getWorld())) return;
        WorldUtil world = new WorldUtil(pipe.getWorld(), pipe.getX(), pipe.getY(), pipe.getZ());
        for (var target : world.getAdjacentTileEntities(true)) {
            if (SimpleServiceLocator.pipeInformationManager.isItemPipe(target.tile)
                    || SimpleServiceLocator.pipeInformationManager.isFluidPipe(target.tile))
                continue;
            if (!pipe.isFluidPipe() && target.tile instanceof IInventory inventory) {
                for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
                    ItemStack stack = inventory.getStackInSlot(slot);
                    if (stack == null || stack.stackSize <= 0) continue;
                    int remaining = stack.stackSize;
                    while (remaining > 0) {
                        ItemStack removed = inventory.decrStackSize(slot, Math.min(remaining, stack.getMaxStackSize()));
                        if (removed == null || removed.stackSize <= 0) break;
                        remaining -= removed.stackSize;
                        queue(
                                pipe,
                                SimpleServiceLocator.routedItemHelper.createNewTravelItem(removed),
                                target.orientation);
                    }
                }
                inventory.markDirty();
            }
            if (!pipe.isFluidPipe()) continue;
            if (SimpleServiceLocator.specialTankHandler.hasHandlerFor(target.tile)
                    && SimpleServiceLocator.specialTankHandler
                            .getTankHandlerFor(target.tile) instanceof ISpecialTankAccessHandler special) {
                for (var entry : new HashMap<>(special.getAvailableLiquid(target.tile)).entrySet()) {
                    long remaining = entry.getValue();
                    while (remaining > 0) {
                        FluidStack drained = special
                                .drainFrom(target.tile, entry.getKey(), fluidBatch(remaining), true);
                        if (drained == null || drained.amount <= 0) break;
                        remaining -= drained.amount;
                        queueFluid(pipe, drained, target.orientation);
                    }
                }
            } else if (target.tile instanceof IFluidHandler handler) {
                var tanks = handler.getTankInfo(ForgeDirection.UNKNOWN);
                if (tanks == null) tanks = handler.getTankInfo(target.orientation.getOpposite());
                if (tanks == null) continue;
                Map<FluidIdentifier, Long> contents = new HashMap<>();
                for (var tank : tanks) {
                    if (tank != null && tank.fluid != null && tank.fluid.amount > 0)
                        contents.merge(FluidIdentifier.get(tank.fluid), (long) tank.fluid.amount, Long::sum);
                }
                for (var entry : contents.entrySet()) {
                    long remaining = entry.getValue();
                    while (remaining > 0) {
                        ForgeDirection side = ForgeDirection.UNKNOWN;
                        FluidStack requested = entry.getKey().makeFluidStack(fluidBatch(remaining));
                        FluidStack simulated = handler.drain(side, requested, false);
                        boolean typed = matches(simulated, entry.getKey());
                        if (!typed) simulated = handler.drain(side, requested.amount, false);
                        if (!matches(simulated, entry.getKey())) {
                            side = target.orientation.getOpposite();
                            simulated = handler.drain(side, requested, false);
                            typed = matches(simulated, entry.getKey());
                            if (!typed) simulated = handler.drain(side, requested.amount, false);
                        }
                        if (!matches(simulated, entry.getKey())) break;
                        int amount = Math.min(requested.amount, simulated.amount);
                        FluidStack drained = typed ? handler.drain(side, entry.getKey().makeFluidStack(amount), true)
                                : handler.drain(side, amount, true);
                        if (drained == null || drained.amount <= 0) break;
                        remaining -= drained.amount;
                        queueFluid(pipe, drained, target.orientation);
                    }
                }
                target.tile.markDirty();
            }
        }
        pipe.getCacheHolder().trigger(CacheTypes.Inventory);
    }

    private static boolean matches(FluidStack stack, FluidIdentifier fluid) {
        return stack != null && stack.amount > 0 && fluid.equals(FluidIdentifier.get(stack));
    }

    private static int fluidBatch(long remaining) {
        return (int) Math.min(remaining, Configs.MAX_LOGISTICS_FLUID_TRANSPORT_INNER_CAPACITY / 2);
    }

    private static void queueFluid(CoreRoutedPipe pipe, FluidStack fluid, ForgeDirection from) {
        queue(
                pipe,
                SimpleServiceLocator.routedItemHelper
                        .createNewTravelItem(SimpleServiceLocator.logisticsFluidManager.getFluidContainer(fluid)),
                from);
    }

    private static void queue(CoreRoutedPipe pipe, IRoutedItem item, ForgeDirection from) {
        item.setDestination(-1);
        item.setTransportMode(TransportMode.Active);
        pipe.sendRoutedItemImmediately(item, from);
    }
}
