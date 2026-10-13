package logisticspipes.pipes;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import logisticspipes.api.IMUICompatiblePipeV2;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.PipeGuiFactory;
import logisticspipes.gui.modularUI.pipes.PipeFluidBasicMui;
import logisticspipes.interfaces.IFluidSinkSettings;
import logisticspipes.interfaces.routing.IFluidSink;
import logisticspipes.pipes.basic.fluid.FluidRoutedPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.utils.FluidFilter;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.FluidRoomCache;
import logisticspipes.utils.FluidTankUtil;
import logisticspipes.utils.SinkReply.FixedPriority;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.tuples.Pair;

public class PipeFluidBasic extends FluidRoutedPipe implements IFluidSink, IFluidSinkSettings, IMUICompatiblePipeV2 {

    private final FluidFilter filter = new FluidFilter();
    private boolean defaultRoute = false;
    private final FluidRoomCache roomCache = new FluidRoomCache();

    public PipeFluidBasic(Item item) {
        super(item);
    }

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
     * Sets the filter to the fluids that are in the adjacent tanks.
     */
    @Override
    public void importFromTank() {
        if (MainProxy.isClient(getWorld())) {
            return;
        }
        Set<FluidIdentifier> fluids = new LinkedHashSet<>();
        for (Pair<TileEntity, ForgeDirection> pair : getAdjacentTanks(false)) {
            fluids.addAll(FluidTankUtil.getContents(pair.getValue1(), pair.getValue2().getOpposite(), false).keySet());
        }
        filter.setFluids(fluids);
    }

    @Override
    public TextureType getCenterTexture() {
        return Textures.LOGISTICSPIPE_LIQUID_BASIC;
    }

    @Override
    public boolean canInsertFromSideToTanks() {
        return true;
    }

    @Override
    public int sinkAmount(FluidStack stack) {
        if (!defaultRoute && !filter.contains(stack)) return 0;

        FluidIdentifier ident = FluidIdentifier.get(stack);
        // what is on its way or waiting at the pipe already has its place in the tanks
        long freeSpace = getRoom(ident) - countOnRoute(ident) - getPendingFluids().getAmount(ident);
        return (int) Math.max(0, Math.min(freeSpace, stack.amount));
    }

    /**
     * @return the room the adjacent tanks have for that fluid, remembered for a moment
     */
    private long getRoom(FluidIdentifier ident) {
        return roomCache.getRoom(ident, getWorld().getTotalWorldTime(), this::computeRoom);
    }

    private long computeRoom(FluidIdentifier ident) {
        long freeSpace = 0;
        for (Pair<TileEntity, ForgeDirection> pair : getAdjacentTanks(true)) {
            freeSpace += FluidTankUtil.getRoom(pair.getValue1(), pair.getValue2().getOpposite(), ident);
        }
        return freeSpace;
    }

    @Override
    protected void onTankContentChanged() {
        super.onTankContentChanged();
        roomCache.clear();
    }

    /**
     * A fluid that is taken by filter is preferred over one that is only taken as default route.
     */
    @Override
    public FixedPriority getFluidSinkPriority(FluidStack stack) {
        return filter.contains(stack) ? FixedPriority.ItemSink : FixedPriority.DefaultRoute;
    }

    /**
     * A filter fluid isn't announced while the tanks have no room for it, so a full sink drops out of the destination
     * searches.
     */
    @Override
    public Set<ItemIdentifier> getSpecificInterests() {
        Set<ItemIdentifier> interests = new TreeSet<>();
        for (FluidIdentifier ident : filter.getFluids()) {
            if (getRoom(ident) > 0) {
                interests.add(ident.getItemIdentifier());
            }
        }
        return interests;
    }

    @Override
    public boolean hasGenericInterests() {
        return defaultRoute && !getAdjacentTanks(true).isEmpty();
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
        filter.writeToNBT(nbttagcompound);
        nbttagcompound.setBoolean("defaultRoute", defaultRoute);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        super.readFromNBT(nbttagcompound);
        filter.readFromNBT(nbttagcompound);
        if (!nbttagcompound.hasKey("filter0")) {
            // older versions kept the first, then only, slot in the tag of the pipe itself
            filter.getTank(0).setFluid(FluidStack.loadFluidStackFromNBT(nbttagcompound));
        }
        defaultRoute = nbttagcompound.getBoolean("defaultRoute");
    }

    @Override
    public boolean canInsertToTanks() {
        return true;
    }

    @Override
    public boolean canReceiveFluid() {
        return false;
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        return PipeGuiFactory.fromMui(new PipeFluidBasicMui(this));
    }
}
