package logisticspipes.modules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.dynamicModules.ModuleFluidSinkMuiDynamic;
import logisticspipes.interfaces.IFluidModuleHost;
import logisticspipes.modules.abstractmodules.LogisticsFluidModule;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.FluidRoomCache;
import logisticspipes.utils.item.ItemIdentifier;

/**
 * Takes fluids from the network into the tank, like the basic fluid pipe.
 */
public class ModuleFluidSink extends LogisticsFluidModule {

    public static final int FILTER_SLOTS = 9;

    private final FluidTank[] filter = new FluidTank[FILTER_SLOTS];
    private boolean defaultRoute = false;
    private final FluidRoomCache roomCache = new FluidRoomCache();

    public ModuleFluidSink() {
        for (int i = 0; i < FILTER_SLOTS; i++) {
            filter[i] = new FluidTank(1);
        }
    }

    public FluidTank getFilterTank(int slot) {
        return filter[slot];
    }

    public boolean isDefaultRoute() {
        return defaultRoute;
    }

    public void setDefaultRoute(boolean defaultRoute) {
        this.defaultRoute = defaultRoute;
    }

    public boolean isFiltered(FluidStack stack) {
        for (FluidTank tank : filter) {
            if (tank.getFluid() != null && tank.getFluid().isFluidEqual(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return how much of the stack the tank can take, counting what is already on its way
     */
    public int sinkAmount(FluidStack stack) {
        IFluidModuleHost host = getHost();
        if (host == null) {
            return 0;
        }
        if (!isFiltered(stack) && !defaultRoute) {
            return 0;
        }
        FluidIdentifier fluid = FluidIdentifier.get(stack);
        long freeSpace = getRoom(fluid) - host.countOnRoute(fluid);
        return (int) Math.max(0, Math.min(freeSpace, stack.amount));
    }

    /**
     * @return the room the tank has for that fluid, remembered for a moment
     */
    private long getRoom(FluidIdentifier fluid) {
        return roomCache.getRoom(fluid, getWorld().getTotalWorldTime(), this::getTankRoom);
    }

    @Override
    public int acceptFluid(FluidStack stack) {
        roomCache.clear();
        return super.acceptFluid(stack);
    }

    /**
     * Filter fluids the tank has no room for aren't announced, so a full sink drops out of the destination searches.
     */
    @Override
    public Collection<ItemIdentifier> getSpecificInterests() {
        List<ItemIdentifier> interests = new ArrayList<>();
        if (getHost() == null || getTankTile() == null) {
            return interests;
        }
        for (FluidTank tank : filter) {
            if (tank.getFluid() == null) {
                continue;
            }
            FluidIdentifier fluid = FluidIdentifier.get(tank.getFluid());
            if (getRoom(fluid) > 0) {
                interests.add(fluid.getItemIdentifier());
            }
        }
        return interests;
    }

    @Override
    public boolean hasGenericInterests() {
        return defaultRoute && getHost() != null && getTankTile() != null;
    }

    /**
     * Sets the filter to the fluids that are in the tank.
     */
    public void importFromTank() {
        if (getTankTile() == null || MainProxy.isClient(getWorld())) {
            return;
        }
        int slot = 0;
        for (FluidTank tank : filter) {
            tank.setFluid(null);
        }
        for (FluidIdentifier fluid : getTankContents().keySet()) {
            if (slot >= FILTER_SLOTS) {
                break;
            }
            filter[slot++].setFluid(fluid.makeFluidStack(1));
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        for (int i = 0; i < FILTER_SLOTS; i++) {
            filter[i].setFluid(null);
            if (nbttagcompound.hasKey("filter" + i)) {
                filter[i].readFromNBT(nbttagcompound.getCompoundTag("filter" + i));
            }
        }
        defaultRoute = nbttagcompound.getBoolean("defaultRoute");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        for (int i = 0; i < FILTER_SLOTS; i++) {
            if (filter[i].getFluid() != null) {
                nbttagcompound.setTag("filter" + i, filter[i].writeToNBT(new NBTTagCompound()));
            }
        }
        nbttagcompound.setBoolean("defaultRoute", defaultRoute);
    }

    @Override
    public LogisticsModularUI getHandGui() {
        return new ModuleFluidSinkMuiDynamic(this);
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        return new ModuleFluidSinkMuiDynamic(this);
    }

    @Override
    public LogisticsModularUI getPipeGui(String prefix) {
        return new ModuleFluidSinkMuiDynamic(this, prefix);
    }
}
