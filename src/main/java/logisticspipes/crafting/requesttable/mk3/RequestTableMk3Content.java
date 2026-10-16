package logisticspipes.crafting.requesttable.mk3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import logisticspipes.items.LogisticsFluidContainer;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.tuples.Pair;

/**
 * Builds the list the request table shows: what the network and the table hold and what the network can craft.
 */
public final class RequestTableMk3Content {

    private RequestTableMk3Content() {}

    /**
     * Builds the combined network/internal request-table entry list.
     */
    public static List<RequestTableMk3NetworkEntry> buildEntries(RequestTableMk3Pipe table) {
        List<RequestTableMk3NetworkEntry> entries = new ArrayList<>();
        table.updateStorageUpgrades();
        Map<ItemIdentifier, Integer> availableItems = SimpleServiceLocator.logisticsManager
                .getAvailableItems(table.getRouter().getIRoutersByCost());
        Map<ItemIdentifier, Integer> internalItems = getInternalItems(table);
        Set<ItemIdentifier> craftableItems = new HashSet<>(
                SimpleServiceLocator.logisticsManager.getCraftableItems(table.getRouter().getIRoutersByCost()));

        Set<ItemIdentifier> itemIds = new HashSet<>();
        itemIds.addAll(availableItems.keySet());
        itemIds.addAll(internalItems.keySet());
        for (ItemIdentifier craftable : craftableItems) {
            if (!isFluid(craftable)) {
                itemIds.add(craftable);
            }
        }
        for (ItemIdentifier item : itemIds) {
            if (isFluid(item)) {
                continue;
            }
            int networkAmount = getAmount(availableItems, item);
            int internalAmount = getAmount(internalItems, item);
            entries.add(
                    new RequestTableMk3NetworkEntry(
                            item.makeStack(total(networkAmount, internalAmount)),
                            false,
                            networkAmount,
                            internalAmount,
                            craftableItems.contains(item)));
        }

        TreeSet<ItemIdentifierStack> availableFluids = SimpleServiceLocator.logisticsFluidManager
                .getAvailableFluid(table.getRouter().getIRoutersByCost());
        Map<ItemIdentifier, Integer> networkFluids = new HashMap<>();
        for (ItemIdentifierStack fluid : availableFluids) {
            networkFluids.put(fluid.getItem(), fluid.getStackSize());
        }
        Map<ItemIdentifier, Integer> internalFluids = getInternalFluids(table);
        Set<ItemIdentifier> fluidIds = new HashSet<>();
        fluidIds.addAll(networkFluids.keySet());
        fluidIds.addAll(internalFluids.keySet());
        for (ItemIdentifier craftable : craftableItems) {
            if (isFluid(craftable)) {
                fluidIds.add(craftable);
            }
        }
        for (ItemIdentifier fluid : fluidIds) {
            int networkAmount = getAmount(networkFluids, fluid);
            int internalAmount = getAmount(internalFluids, fluid);
            entries.add(
                    new RequestTableMk3NetworkEntry(
                            fluid.makeStack(total(networkAmount, internalAmount)),
                            true,
                            networkAmount,
                            internalAmount,
                            craftableItems.contains(fluid)));
        }
        return entries;
    }

    private static Map<ItemIdentifier, Integer> getInternalItems(RequestTableMk3Pipe table) {
        Map<ItemIdentifier, Integer> items = new HashMap<>();
        for (Pair<ItemStack, Integer> entry : table.inv) {
            ItemStack stack = entry.getValue1();
            if (stack != null) {
                items.merge(ItemIdentifier.get(stack), stack.stackSize, Integer::sum);
            }
        }
        return items;
    }

    private static Map<ItemIdentifier, Integer> getInternalFluids(RequestTableMk3Pipe table) {
        Map<ItemIdentifier, Integer> fluids = new HashMap<>();
        for (int slot = 0; slot < table.getFluidStorage().getSizeInventory(); slot++) {
            FluidStack stack = table.getFluidStorage().getFluid(slot);
            if (stack != null) {
                fluids.merge(FluidIdentifier.get(stack).getItemIdentifier(), stack.amount, Integer::sum);
            }
        }
        return fluids;
    }

    /**
     * Only the fluid of the network is listed as fluid. A GregTech cell keeps its fluid in its data and counts as a
     * fluid container elsewhere, but here it is an item: a filled cell that a filler can make is crafted as a cell, not
     * as the fluid in it.
     */
    private static boolean isFluid(ItemIdentifier item) {
        return item.item instanceof LogisticsFluidContainer;
    }

    /**
     * @return the sum, at most what an int holds: a network can report that much on its own
     */
    private static int total(int networkAmount, int internalAmount) {
        return (int) Math.min(Integer.MAX_VALUE, (long) networkAmount + internalAmount);
    }

    private static int getAmount(Map<ItemIdentifier, Integer> amounts, ItemIdentifier item) {
        Integer amount = amounts.get(item);
        return amount == null ? 0 : amount;
    }
}
