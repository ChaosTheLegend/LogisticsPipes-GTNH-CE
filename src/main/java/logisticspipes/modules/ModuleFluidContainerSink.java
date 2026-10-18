package logisticspipes.modules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidContainerRegistry.FluidContainerData;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.dynamicModules.ModuleFluidContainerSinkMuiDynamic;
import logisticspipes.interfaces.IFluidModuleHost;
import logisticspipes.interfaces.IInventorylessSink;
import logisticspipes.modules.abstractmodules.LogisticsFluidModule;
import logisticspipes.pipes.PipeLogisticsChassi.ChassiTargetInformation;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.routing.astar.CategoryInterests;
import logisticspipes.routing.astar.ItemCategory;
import logisticspipes.utils.FluidContainerUtil;
import logisticspipes.utils.FluidFilter;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.FluidRoomCache;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.SinkReply.FixedPriority;
import logisticspipes.utils.item.ItemIdentifier;

/**
 * Takes filled containers from the network (cells, buckets, capsules) and empties them into the tank. The empty
 * containers go back into the network. Only whole containers are emptied: one the tank has no room for is not accepted.
 */
public class ModuleFluidContainerSink extends LogisticsFluidModule implements IInventorylessSink {

    // the items that hold fluid themselves, whatever they hold: they are told apart only by their data
    private static Set<ItemIdentifier> fluidHoldingItems;
    private static final int MAX_DAMAGE_PROBED = Short.MAX_VALUE - 1;
    // the filled containers of the registry by their fluid, and the size of the registry they were read from
    private static final Map<FluidIdentifier, List<ItemIdentifier>> registeredContainers = new HashMap<>();
    private static int registrySize = -1;
    private static final CategoryInterests FLUID_HOLDING = CategoryInterests.flag(ItemCategory.FLUID_HOLDING);

    private final FluidFilter filter = new FluidFilter();
    private final FluidRoomCache roomCache = new FluidRoomCache();

    public FluidFilter getFilter() {
        return filter;
    }

    private long getRoom(FluidIdentifier fluid) {
        return roomCache.getRoom(fluid, getWorld().getTotalWorldTime(), this::getTankRoom);
    }

    /**
     * @param fluid what one container holds
     * @return how many such containers the tank can take, counting what is already on its way
     */
    private int containersWithRoom(ItemIdentifier container, FluidStack fluid) {
        IFluidModuleHost host = getHost();
        if (host == null || getTankTile() == null || fluid.amount <= 0) {
            return 0;
        }
        FluidIdentifier fluidId = FluidIdentifier.get(fluid);
        long room = getRoom(fluidId) - host.countOnRoute(fluidId);
        if (_service instanceof CoreRoutedPipe pipe) {
            room -= (long) pipe.countOnRoute(container) * fluid.amount;
        }
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, room / fluid.amount));
    }

    @Override
    public SinkReply sinksItem(ItemIdentifier item, int bestPriority, int bestCustomPriority, boolean allowDefault,
            boolean includeInTransit) {
        if (bestPriority > FixedPriority.ItemSink.ordinal()
                || (bestPriority == FixedPriority.ItemSink.ordinal() && bestCustomPriority >= 0)) {
            return null;
        }
        // asked for many items that are no containers: the ones without data can only be in the registry
        if (item.tag == null && !FluidContainerRegistry.isFilledContainer(item.unsafeMakeNormalStack(1))) {
            return null;
        }
        FluidStack fluid = FluidContainerUtil.getFluid(item.unsafeMakeNormalStack(1));
        if (fluid == null || !filter.contains(fluid)) {
            return null;
        }
        int containers = containersWithRoom(item, fluid);
        if (containers < 1) {
            return null;
        }
        return new SinkReply(
                FixedPriority.ItemSink,
                0,
                true,
                false,
                1,
                containers,
                new ChassiTargetInformation(getPositionInt()));
    }

    /**
     * Empties one arrived container into the tank.
     *
     * @param fluid    what the container holds
     * @param overflow is given what the tank didn't take after all, although it had promised the room
     * @return false if the tank doesn't take all of it; nothing is filled then
     */
    public boolean acceptContainer(FluidStack fluid, Consumer<FluidStack> overflow) {
        if (getTankTile() == null || !filter.contains(fluid)) {
            return false;
        }
        roomCache.clear();
        if (getTankRoom(FluidIdentifier.get(fluid)) < fluid.amount) {
            return false;
        }
        int filled = fillTank(fluid, fluid.amount);
        if (filled < fluid.amount) {
            // the rest must not vanish, the host sends it on
            FluidStack rest = fluid.copy();
            rest.amount = fluid.amount - filled;
            overflow.accept(rest);
        }
        return true;
    }

    /**
     * The filled containers of the filter fluids. Fluids the tank has no room for aren't announced, so a full sink
     * drops out of the destination searches. The items that keep their fluid in their data are announced as a category,
     * see {@link #getCategoryInterests()}.
     */
    @Override
    public Collection<ItemIdentifier> getSpecificInterests() {
        List<ItemIdentifier> interests = new ArrayList<>();
        if (getHost() == null || getTankTile() == null) {
            return interests;
        }
        for (FluidIdentifier wanted : filter.getFluids()) {
            if (getRoom(wanted) > 0) {
                interests.addAll(getRegisteredContainers(wanted));
            }
        }
        return interests;
    }

    /**
     * The items that hold fluid in their data, as long as one of the filter fluids has room. They are thousands in a
     * big pack, so they are one category value instead of thousands of specific interests: the router would rebuild and
     * compare them every second.
     */
    @Override
    public CategoryInterests getCategoryInterests() {
        if (getHost() == null || getTankTile() == null) {
            return CategoryInterests.EMPTY;
        }
        for (FluidIdentifier wanted : filter.getFluids()) {
            if (getRoom(wanted) > 0) {
                return FLUID_HOLDING;
            }
        }
        return CategoryInterests.EMPTY;
    }

    /**
     * @return the filled containers the registry knows for that fluid. The registry of a big pack has thousands of
     *         entries and the router asks for the interests every second, so the answer is kept until the registry
     *         grows.
     */
    private static List<ItemIdentifier> getRegisteredContainers(FluidIdentifier fluid) {
        FluidContainerData[] registered = FluidContainerRegistry.getRegisteredFluidContainerData();
        if (registered.length != registrySize) {
            registrySize = registered.length;
            registeredContainers.clear();
            for (FluidContainerData data : registered) {
                if (data.fluid != null && data.filledContainer != null && data.filledContainer.getItem() != null) {
                    registeredContainers.computeIfAbsent(FluidIdentifier.get(data.fluid), key -> new ArrayList<>())
                            .add(ItemIdentifier.get(data.filledContainer));
                }
            }
        }
        List<ItemIdentifier> containers = registeredContainers.get(fluid);
        return containers == null ? Collections.emptyList() : containers;
    }

    /**
     * Items like large cells keep their fluid in their data, so there is no list of them filled with a certain fluid.
     * The module asks to see the kinds of items that can hold fluid at all ({@link ItemCategory#FLUID_HOLDING}) and
     * looks at the fluid when one is offered.
     * <p>
     * Many items can be asked for a fluid without ever holding one: all the items of GregTech share one class that can.
     * So each damage value is asked for its capacity once, and only the ones with a capacity are of interest; anything
     * else would make this module a candidate for nearly every item that looks for a place.
     */
    private static Set<ItemIdentifier> getFluidHoldingItems() {
        loadFluidHoldingItems();
        return fluidHoldingItems;
    }

    /**
     * Whether the item is one of the kinds that hold fluid, in any of the forms the router lookup tries for an item
     * (without data, undamaged, ...): the same items {@link #getFluidHoldingItems()} reached as specific interests.
     */
    public static boolean isFluidHoldingItem(ItemIdentifier item) {
        Set<ItemIdentifier> items = getFluidHoldingItems();
        ItemIdentifier undamaged = item.getUndamaged();
        ItemIdentifier ignoringData = item.getIgnoringData();
        return items.contains(item) || items.contains(undamaged)
                || items.contains(item.getIgnoringNBT())
                || items.contains(undamaged.getIgnoringNBT())
                || items.contains(ignoringData)
                || items.contains(ignoringData.getIgnoringNBT());
    }

    /**
     * Looks through the items for the ones that hold fluid. That takes a moment in a big pack, so it is done while the
     * server starts and not in the tick the first module asks in.
     */
    public static void loadFluidHoldingItems() {
        if (fluidHoldingItems == null) {
            Set<ItemIdentifier> items = new HashSet<>();
            for (Object registered : Item.itemRegistry) {
                if (!(registered instanceof IFluidContainerItem container) || !(registered instanceof Item item)) {
                    continue;
                }
                int lastDamage = item.getHasSubtypes() ? MAX_DAMAGE_PROBED : 0;
                for (int damage = 0; damage <= lastDamage; damage++) {
                    ItemStack stack = new ItemStack(item, 1, damage);
                    try {
                        if (container.getCapacity(stack) > 0) {
                            // without data: the form the interest lookup falls back to
                            items.add(ItemIdentifier.get(stack));
                        }
                    } catch (RuntimeException ignored) {
                        // an item that can't answer for a damage value it never has
                    }
                }
            }
            fluidHoldingItems = items;
        }
    }

    /**
     * Containers are ordinary items that look for a place in the network, the module is offered them like an item sink.
     */
    @Override
    public boolean recievePassive() {
        return true;
    }

    /**
     * Sets the filter to the fluids that are in the tank.
     */
    public void importFromTank() {
        if (getTankTile() == null || MainProxy.isClient(getWorld())) {
            return;
        }
        filter.setFluids(getTankContents().keySet());
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        filter.readFromNBT(nbttagcompound);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        filter.writeToNBT(nbttagcompound);
    }

    @Override
    public LogisticsModularUI getHandGui() {
        return new ModuleFluidContainerSinkMuiDynamic(this);
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        return new ModuleFluidContainerSinkMuiDynamic(this);
    }

    @Override
    public LogisticsModularUI getPipeGui(String prefix) {
        return new ModuleFluidContainerSinkMuiDynamic(this, prefix);
    }
}
