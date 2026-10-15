package logisticspipes.network.packets.crafting.requesttable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import logisticspipes.crafting.requesttable.RequestTableNetworkEntry;
import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.network.PacketGuards;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.IntegerCoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.tuples.Pair;

/**
 * Requests a fresh combined network list for the new request table GUI.
 */
public class RequestTableRefreshPacket extends IntegerCoordinatesPacket {

    public RequestTableRefreshPacket(int id) {
        super(id);
    }

    @Override
    public ModernPacket template() {
        return new RequestTableRefreshPacket(getId());
    }

    /**
     * Builds the combined network/internal request-table entry list.
     */
    public static List<RequestTableNetworkEntry> buildEntries(RequestTablePipe table) {
        List<RequestTableNetworkEntry> entries = new ArrayList<>();
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
            if (!craftable.isFluidContainer()) {
                itemIds.add(craftable);
            }
        }
        for (ItemIdentifier item : itemIds) {
            if (item.isFluidContainer()) {
                continue;
            }
            int networkAmount = getAmount(availableItems, item);
            int internalAmount = getAmount(internalItems, item);
            entries.add(
                    new RequestTableNetworkEntry(
                            item.makeStack(networkAmount + internalAmount),
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
            if (craftable.isFluidContainer()) {
                fluidIds.add(craftable);
            }
        }
        for (ItemIdentifier fluid : fluidIds) {
            int networkAmount = getAmount(networkFluids, fluid);
            int internalAmount = getAmount(internalFluids, fluid);
            entries.add(
                    new RequestTableNetworkEntry(
                            fluid.makeStack(networkAmount + internalAmount),
                            true,
                            networkAmount,
                            internalAmount,
                            craftableItems.contains(fluid)));
        }
        return entries;
    }

    @Override
    public void processPacket(EntityPlayer player) {
        RequestTablePipe table = PacketGuards.getOpenRequestTable(player);
        if (table == null) {
            return;
        }
        List<RequestTableNetworkEntry> entries = buildEntries(table);
        MainProxy.sendPacketToPlayer(
                PacketHandler.getPacket(RequestTableContentPacket.class).setEntries(entries)
                        .setDisplaySettings(table.getDisplaySettings(player)).setTilePos(table.container),
                player);
    }

    private static Map<ItemIdentifier, Integer> getInternalItems(RequestTablePipe table) {
        Map<ItemIdentifier, Integer> items = new HashMap<>();
        for (Pair<ItemStack, Integer> entry : table.inv) {
            ItemStack stack = entry.getValue1();
            if (stack != null) {
                items.merge(ItemIdentifier.get(stack), stack.stackSize, Integer::sum);
            }
        }
        return items;
    }

    private static Map<ItemIdentifier, Integer> getInternalFluids(RequestTablePipe table) {
        Map<ItemIdentifier, Integer> fluids = new HashMap<>();
        for (int slot = 0; slot < table.getFluidStorage().getSizeInventory(); slot++) {
            FluidStack stack = table.getFluidStorage().getFluid(slot);
            if (stack != null) {
                fluids.merge(FluidIdentifier.get(stack).getItemIdentifier(), stack.amount, Integer::sum);
            }
        }
        return fluids;
    }

    private static int getAmount(Map<ItemIdentifier, Integer> amounts, ItemIdentifier item) {
        Integer amount = amounts.get(item);
        return amount == null ? 0 : amount;
    }
}
