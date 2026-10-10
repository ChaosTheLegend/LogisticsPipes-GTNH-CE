package logisticspipes.pipes;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import logisticspipes.api.IMUICompatiblePipeV2;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.PipeGuiFactory;
import logisticspipes.gui.modularUI.pipes.PipeFluidBasicMui;
import logisticspipes.interfaces.ISpecialTankAccessHandler;
import logisticspipes.interfaces.routing.IFluidSink;
import logisticspipes.pipes.basic.fluid.FluidRoutedPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.FluidRoomCache;
import logisticspipes.utils.SinkReply.FixedPriority;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.tuples.Pair;

public class PipeFluidBasic extends FluidRoutedPipe implements IFluidSink, IMUICompatiblePipeV2 {

    // a bucket, some tanks only take whole ones
    private static final int ROOM_PROBE_AMOUNT = 1000;

    public static final int FILTER_SLOTS = 9;

    private final FluidTank[] filter = new FluidTank[FILTER_SLOTS];
    private boolean defaultRoute = false;
    private final FluidRoomCache roomCache = new FluidRoomCache();

    public PipeFluidBasic(Item item) {
        super(item);
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
     * Sets the filter to the fluids that are in the adjacent tanks.
     */
    public void importFromTanks() {
        if (MainProxy.isClient(getWorld())) {
            return;
        }
        Set<FluidIdentifier> fluids = new LinkedHashSet<>();
        for (Pair<TileEntity, ForgeDirection> pair : getAdjacentTanks(false)) {
            TileEntity tile = pair.getValue1();
            ISpecialTankAccessHandler special = SimpleServiceLocator.specialTankHandler.getAccessHandlerFor(tile);
            if (special != null) {
                fluids.addAll(special.getAvailableLiquid(tile).keySet());
            } else if (tile instanceof IFluidHandler handler) {
                FluidTankInfo[] tanks = handler.getTankInfo(pair.getValue2().getOpposite());
                if (tanks == null) {
                    continue;
                }
                for (FluidTankInfo tank : tanks) {
                    if (tank != null && tank.fluid != null && tank.fluid.getFluidID() != 0) {
                        fluids.add(FluidIdentifier.get(tank.fluid));
                    }
                }
            }
        }
        for (FluidTank tank : filter) {
            tank.setFluid(null);
        }
        int slot = 0;
        for (FluidIdentifier fluid : fluids) {
            if (slot >= FILTER_SLOTS) {
                break;
            }
            filter[slot++].setFluid(fluid.makeFluidStack(1));
        }
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
        if (!defaultRoute && !isFiltered(stack)) return 0;

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
        // using long for our internal calculations avoids an overflow when tanks report
        // a capacity of Integer.MAX_VALUE (notably gt5 super tank when set to void fluids)
        long freeSpace = 0;
        for (Pair<TileEntity, ForgeDirection> pair : getAdjacentTanks(true)) {
            if (!(pair.getValue1() instanceof IFluidHandler handler)) {
                // an ME interface or the like
                freeSpace += fillTank(
                        pair.getValue1(),
                        pair.getValue2(),
                        ident.makeFluidStack(Integer.MAX_VALUE),
                        false);
                continue;
            }
            ForgeDirection dir = pair.getValue2().getOpposite();
            // ensure we are actually able to fill this handler, and it's not some output tank or such
            if (handler.fill(dir, ident.makeFluidStack(ROOM_PROBE_AMOUNT), false) <= 0) {
                continue;
            }
            freeSpace += ident.getFreeSpaceInsideTank(handler, dir);
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
        return isFiltered(stack) ? FixedPriority.ItemSink : FixedPriority.DefaultRoute;
    }

    /**
     * A filter fluid isn't announced while the tanks have no room for it, so a full sink drops out of the destination
     * searches.
     */
    @Override
    public Set<ItemIdentifier> getSpecificInterests() {
        Set<ItemIdentifier> interests = new TreeSet<>();
        for (FluidTank tank : filter) {
            if (tank.getFluid() == null) {
                continue;
            }
            FluidIdentifier ident = FluidIdentifier.get(tank.getFluid());
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
        // the first slot stays where the single filter of older versions was
        filter[0].writeToNBT(nbttagcompound);
        for (int i = 1; i < FILTER_SLOTS; i++) {
            if (filter[i].getFluid() != null) {
                nbttagcompound.setTag("filter" + i, filter[i].writeToNBT(new NBTTagCompound()));
            }
        }
        nbttagcompound.setBoolean("defaultRoute", defaultRoute);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        super.readFromNBT(nbttagcompound);
        filter[0].readFromNBT(nbttagcompound);
        for (int i = 1; i < FILTER_SLOTS; i++) {
            filter[i].setFluid(null);
            if (nbttagcompound.hasKey("filter" + i)) {
                filter[i].readFromNBT(nbttagcompound.getCompoundTag("filter" + i));
            }
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
