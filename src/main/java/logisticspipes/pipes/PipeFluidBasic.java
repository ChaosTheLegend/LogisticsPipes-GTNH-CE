package logisticspipes.pipes;

import java.util.Set;
import java.util.TreeSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.IFluidHandler;

import logisticspipes.LogisticsPipes;
import logisticspipes.api.IMUICompatiblePipeV2;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.PipeGuiFactory;
import logisticspipes.gui.modularUI.pipes.PipeFluidBasicMui;
import logisticspipes.interfaces.routing.IFluidSink;
import logisticspipes.network.GuiIDs;
import logisticspipes.pipes.basic.fluid.FluidRoutedPipe;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.FluidRoomCache;
import logisticspipes.utils.PlayerCollectionList;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.tuples.Pair;

public class PipeFluidBasic extends FluidRoutedPipe implements IFluidSink, IMUICompatiblePipeV2 {

    // a bucket, some tanks only take whole ones
    private static final int ROOM_PROBE_AMOUNT = 1000;

    public final FluidTank filterTank = new FluidTank(1);
    private final FluidRoomCache roomCache = new FluidRoomCache();
    private final PlayerCollectionList guiOpenedBy = new PlayerCollectionList();

    public PipeFluidBasic(Item item) {
        super(item);
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
    public void onWrenchClicked(EntityPlayer entityplayer) {
        entityplayer.openGui(LogisticsPipes.instance, GuiIDs.GUI_Fluid_Basic_ID, getWorld(), getX(), getY(), getZ());
    }

    @Override
    public int sinkAmount(FluidStack stack) {
        if (!guiOpenedBy.isEmpty()) {
            return 0; // Don't sink when the gui is open
        }

        if (filterTank.getFluid() == null) return 0;
        if (!filterTank.getFluid().isFluidEqual(stack)) return 0;

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
     * The filter fluid isn't announced while the tanks have no room for it, so a full sink drops out of the destination
     * searches.
     */
    @Override
    public Set<ItemIdentifier> getSpecificInterests() {
        Set<ItemIdentifier> interests = new TreeSet<>();
        if (filterTank.getFluid() != null) {
            FluidIdentifier ident = FluidIdentifier.get(filterTank.getFluid());
            if (getRoom(ident) > 0) {
                interests.add(ident.getItemIdentifier());
            }
        }
        return interests;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
        filterTank.writeToNBT(nbttagcompound);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        super.readFromNBT(nbttagcompound);
        filterTank.readFromNBT(nbttagcompound);
    }

    @Override
    public boolean canInsertToTanks() {
        return true;
    }

    public void guiOpenedByPlayer(EntityPlayer player) {
        guiOpenedBy.add(player);
    }

    public void guiClosedByPlayer(EntityPlayer player) {
        guiOpenedBy.remove(player);
    }

    @Override
    public boolean canReceiveFluid() {
        return false;
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        return PipeGuiFactory.fromMui(new PipeFluidBasicMui(this));
    }

    public String getFluidName() {
        if (filterTank.getFluid() == null) return "None";
        return filterTank.getFluid().getLocalizedName();
    }
}
