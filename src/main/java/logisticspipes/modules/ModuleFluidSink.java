package logisticspipes.modules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.dynamicModules.ModuleFluidSinkMuiDynamic;
import logisticspipes.interfaces.IFluidModuleHost;
import logisticspipes.interfaces.IFluidSinkSettings;
import logisticspipes.modules.abstractmodules.LogisticsFluidModule;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.FluidFilter;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.FluidRoomCache;
import logisticspipes.utils.item.ItemIdentifier;

/**
 * Takes fluids from the network into the tank, like the basic fluid pipe.
 */
public class ModuleFluidSink extends LogisticsFluidModule implements IFluidSinkSettings {

    private final FluidFilter filter = new FluidFilter();
    private boolean defaultRoute = false;
    private final FluidRoomCache roomCache = new FluidRoomCache();

    @Override
    public FluidFilter getFilter() {
        return filter;
    }

    @Override
    public boolean isDefaultRoute() {
        return defaultRoute;
    }

    @Override
    public void setDefaultRoute(boolean defaultRoute) {
        this.defaultRoute = defaultRoute;
    }

    /**
     * @return how much of the stack the tank can take, counting what is already on its way
     */
    public int sinkAmount(FluidStack stack) {
        IFluidModuleHost host = getHost();
        if (host == null) {
            return 0;
        }
        if (!filter.contains(stack) && !defaultRoute) {
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
        for (FluidIdentifier fluid : filter.getFluids()) {
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

    @Override
    public void importFromTank() {
        if (getTankTile() == null || MainProxy.isClient(getWorld())) {
            return;
        }
        filter.setFluids(getTankContents().keySet());
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        filter.readFromNBT(nbttagcompound);
        defaultRoute = nbttagcompound.getBoolean("defaultRoute");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        filter.writeToNBT(nbttagcompound);
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
