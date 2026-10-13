package logisticspipes.pipes.basic.fluid;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.IFluidHandler;

import logisticspipes.config.Configs;
import logisticspipes.interfaces.IFluidContainerReceiver;
import logisticspipes.interfaces.routing.IRequireReliableFluidTransport;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.routing.order.LogisticsFluidOrderManager;
import logisticspipes.routing.order.LogisticsOrderManager;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.transport.LPTravelingItem.LPTravelingItemServer;
import logisticspipes.transport.PipeFluidTransportLogistics;
import logisticspipes.utils.CacheHolder.CacheTypes;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.FluidSendUtil;
import logisticspipes.utils.FluidTankUtil;
import logisticspipes.utils.WorldUtil;
import logisticspipes.utils.tuples.Pair;

public abstract class FluidRoutedPipe extends CoreRoutedPipe implements IFluidContainerReceiver {

    private static final int TICKS_PER_PENDING_RETRY = 5;

    private WorldUtil worldUtil;
    private LogisticsFluidOrderManager _orderFluidManager;

    // fluid that arrived but didn't fit into the tank yet, the pipe has no tanks of its own for it
    private final PendingFluids pendingFluids = new PendingFluids();
    private boolean tanksMigrated = false;

    public FluidRoutedPipe(Item item) {
        super(new PipeFluidTransportLogistics(), item);
    }

    protected FluidRoutedPipe(PipeFluidTransportLogistics transport, Item item) {
        super(transport, item);
    }

    @Override
    public void setTile(TileEntity tile) {
        super.setTile(tile);
    }

    @Override
    public boolean logisitcsIsPipeConnected(TileEntity tile, ForgeDirection dir) {
        // an ME interface or the like
        if (SimpleServiceLocator.specialTankHandler.getAccessHandlerFor(tile) != null) {
            return true;
        }
        if (tile instanceof IFluidHandler) {
            IFluidHandler liq = (IFluidHandler) tile;

            if (liq.getTankInfo(dir.getOpposite()) != null && liq.getTankInfo(dir.getOpposite()).length > 0) {
                return true;
            }
        }

        return tile instanceof LogisticsTileGenericPipe;
    }

    @Override
    public ItemSendMode getItemSendMode() {
        return ItemSendMode.Normal;
    }

    @Override
    public TextureType getNonRoutedTexture(ForgeDirection connection) {
        if (isFluidSidedTexture(connection)) {
            return Textures.LOGISTICSPIPE_LIQUID_TEXTURE;
        }
        return super.getNonRoutedTexture(connection);
    }

    private boolean isFluidSidedTexture(ForgeDirection connection) {
        WorldUtil util = new WorldUtil(getWorld(), getX(), getY(), getZ());
        TileEntity tile = util.getAdjacentTileEntitie(connection);
        if (tile instanceof IFluidHandler) {
            IFluidHandler liq = (IFluidHandler) tile;

            return liq.getTankInfo(connection.getOpposite()) != null
                    && liq.getTankInfo(connection.getOpposite()).length > 0;
        }
        return false;
    }

    @Override
    public LogisticsModule getLogisticsModule() {
        return null;
    }

    /***
     * @param flag Weather to list a Nearby Pipe or not
     */
    public final List<Pair<TileEntity, ForgeDirection>> getAdjacentTanks(boolean flag) {
        if (worldUtil == null) {
            worldUtil = new WorldUtil(getWorld(), getX(), getY(), getZ());
        }
        List<Pair<TileEntity, ForgeDirection>> tileList = new ArrayList<>();
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            TileEntity tile = worldUtil.getAdjacentTileEntitie(dir);
            if (!isConnectableTank(tile, dir, flag)) {
                continue;
            }
            tileList.add(new Pair<>(tile, dir));
        }
        return tileList;
    }

    /***
     * @param dir  the direction to check
     * @param flag Weather to list a Nearby Pipe or not
     */
    public final TileEntity getAdjacentTank(ForgeDirection dir, boolean flag) {
        if (worldUtil == null) {
            worldUtil = new WorldUtil(getWorld(), getX(), getY(), getZ());
        }
        if (dir == ForgeDirection.UNKNOWN || dir == null) {
            return null;
        }
        TileEntity tile = worldUtil.getAdjacentTileEntitie(dir);
        if (!isConnectableTank(tile, dir, flag)) {
            return null;
        }
        return tile;
    }

    /***
     * @param tile The connected TileEntity
     * @param dir  The direction the TileEntity is in relative to the currect pipe
     * @param flag Weather to list a Nearby Pipe or not
     */
    public final boolean isConnectableTank(TileEntity tile, ForgeDirection dir, boolean flag) {
        if (SimpleServiceLocator.specialTankHandler.hasHandlerFor(tile)) {
            return true;
        }
        if (!(tile instanceof IFluidHandler)) {
            return false;
        }
        if (!this.canPipeConnect(tile, dir)) {
            return false;
        }
        if (tile instanceof LogisticsTileGenericPipe) {
            if (((LogisticsTileGenericPipe) tile).pipe instanceof FluidRoutedPipe) {
                return false;
            }
            if (!flag) {
                return false;
            }
            return ((LogisticsTileGenericPipe) tile).pipe != null
                    && ((LogisticsTileGenericPipe) tile).pipe.transport instanceof IFluidHandler;
        }
        return true;
    }

    @Override
    public void enabledUpdateEntity() {
        super.enabledUpdateEntity();
        if (!canInsertFromSideToTanks() || MainProxy.isClient(getWorld())) {
            return;
        }
        if (!tanksMigrated) {
            migrateTransportTanks();
        }
        if (pendingFluids.isEmpty() || !isNthTick(TICKS_PER_PENDING_RETRY)) {
            return;
        }
        insertPendingFluids();
    }

    /**
     * Older versions kept fluid that didn't fit in tanks of the transport, take it over.
     */
    private void migrateTransportTanks() {
        tanksMigrated = true;
        PipeFluidTransportLogistics fluidTransport = (PipeFluidTransportLogistics) transport;
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            FluidTank tank = fluidTransport.sideTanks[dir.ordinal()];
            pendingFluids.add(tank.getFluid(), dir);
            tank.setFluid(null);
        }
        pendingFluids.add(fluidTransport.internalTank.getFluid(), ForgeDirection.UNKNOWN);
        fluidTransport.internalTank.setFluid(null);
    }

    /**
     * @return the tanks next to the pipe that waiting fluid may go into
     */
    private List<Pair<TileEntity, ForgeDirection>> getFillableTanks() {
        List<Pair<TileEntity, ForgeDirection>> list = getAdjacentTanks(true);
        list.removeIf(
                pair -> pair.getValue1() instanceof LogisticsTileGenericPipe pipeTile
                        && pipeTile.pipe instanceof CoreRoutedPipe);
        return list;
    }

    /**
     * Puts the fluid that is waiting for room into the tanks.
     */
    private void insertPendingFluids() {
        List<Pair<TileEntity, ForgeDirection>> tanks = getFillableTanks();
        boolean inserted = false;
        for (Iterator<PendingFluids.Entry> iterator = pendingFluids.iterator(); iterator.hasNext();) {
            PendingFluids.Entry entry = iterator.next();
            boolean sideFound = false;
            for (Pair<TileEntity, ForgeDirection> pair : tanks) {
                if (entry.side != ForgeDirection.UNKNOWN && entry.side != pair.getValue2()) {
                    continue;
                }
                sideFound = true;
                int filled = fillTank(pair.getValue1(), pair.getValue2(), entry.fluid, true);
                if (filled > 0) {
                    entry.fluid.amount -= filled;
                    inserted = true;
                }
                if (entry.fluid.amount <= 0) {
                    break;
                }
            }
            if (entry.fluid.amount <= 0) {
                iterator.remove();
            } else if (!sideFound) {
                // the tank on that side is gone
                entry.side = ForgeDirection.UNKNOWN;
            }
        }
        if (inserted) {
            onTankContentChanged();
        }
    }

    /**
     * Called after this pipe put fluid into an adjacent tank.
     */
    protected void onTankContentChanged() {
        getCacheHolder().trigger(CacheTypes.Inventory);
    }

    /**
     * @return how much fluid may wait for room in the tanks before it is sent to another sink, the size of the tanks
     *         the transport had for that
     */
    protected int getPendingCapacity() {
        return Configs.MAX_LOGISTICS_FLUID_TRANSPORT_INNER_CAPACITY * 3 / 2;
    }

    public PendingFluids getPendingFluids() {
        return pendingFluids;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        super.readFromNBT(nbttagcompound);
        pendingFluids.readFromNBT(nbttagcompound, "pendingFluids");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
        pendingFluids.writeToNBT(nbttagcompound, "pendingFluids");
    }

    /**
     * @return true if clumps that only pass through may skip the routing of this pipe
     */
    public boolean relaysClumps() {
        return true;
    }

    public abstract boolean canInsertFromSideToTanks();

    public abstract boolean canInsertToTanks();

    public abstract boolean canReceiveFluid();

    @Override
    public boolean endReached(LPTravelingItemServer arrivingItem, TileEntity tile) {
        if (canInsertToTanks()) {
            getCacheHolder().trigger(CacheTypes.Inventory);
            if (arrivingItem.getItemIdentifierStack() == null
                    || !(arrivingItem.getItemIdentifierStack().getItem().isFluidContainer())) {
                return false;
            }
            ForgeDirection waitingSide = ForgeDirection.UNKNOWN;
            FluidStack liquid = SimpleServiceLocator.logisticsFluidManager
                    .getFluidFromContainer(arrivingItem.getItemIdentifierStack());
            if (isConnectableTank(tile, arrivingItem.output, false)) {
                // Try to put liquid into where it should be
                TileEntity intendedTank = getAdjacentTank(arrivingItem.output, false);
                if (intendedTank != null) {
                    fillSide(liquid, arrivingItem.output, intendedTank);
                    if (liquid.amount == 0) {
                        onTankContentChanged();
                        return true;
                    }
                }
                if (intendedTank == null) {
                    // TL,DR: this piece of legacy code acts as a fallback to above intendedTank handling in case
                    // something goes wrong

                    // Before my 2023 change it dumps fluid into first tank ever visited, pretty bad obviously
                    // my 2023 change try to be smart and worked somewhat, but it was not really a well-thought-out
                    // change. It simply checks if the target might have requested fluid (that code is still live in
                    // PipeFluidSupplierMK2.java) and if not it goes to next tank.
                    //
                    // Now it suddenly appears to me LP knows which direction this arrivingItem should go out
                    // and in my limited debugging sessions it shows it's never UNKNOWN or an incorrect direction value
                    // I can only speculate why the original LP developers decided to go with this weird approach.
                    // Fearful of hitting some weird niche in routing code, I decided to not entirely ditch this for
                    // loop and instead put it as a fallback when intended tank is not found...
                    List<Pair<TileEntity, ForgeDirection>> adjTanks = getAdjacentTanks(false);
                    // Try to put liquid into all adjacent tanks.
                    for (Pair<TileEntity, ForgeDirection> pair : adjTanks) {
                        ForgeDirection dir = pair.getValue2();
                        fillSide(liquid, dir, pair.getValue1());
                        if (liquid.amount != 0) {
                            continue;
                        }
                        onTankContentChanged();
                        return true;
                    }
                }
                waitingSide = arrivingItem.output;
            }
            onTankContentChanged();
            if (liquid.amount <= 0) {
                return true;
            }
            // the rest waits for room in the tank
            int room = (int) Math.max(0, getPendingCapacity() - pendingFluids.getTotalAmount());
            if (room < liquid.amount) {
                // too much is waiting already, another sink gets what goes beyond that
                FluidStack surplus = liquid.copy();
                surplus.amount = liquid.amount - room;
                IRoutedItem routedItem = FluidSendUtil.createItemForSink(surplus, getRouter());
                if (routedItem != null) {
                    int sent = liquid.amount - room - surplus.amount;
                    liquid.amount -= sent;
                    // TODO: FIX THIS
                    if (this instanceof IRequireReliableFluidTransport) {
                        ((IRequireReliableFluidTransport) this).liquidNotInserted(FluidIdentifier.get(liquid), sent);
                    }
                    queueRoutedItem(routedItem, arrivingItem.output.getOpposite());
                }
            }
            // what nobody else takes is kept rather than sent nowhere
            pendingFluids.add(liquid, waitingSide);
            return true;
        }
        return false;
    }

    /**
     * Puts fluid into the tile on that side of the pipe.
     *
     * @return the amount that was taken
     */
    protected int fillTank(TileEntity tile, ForgeDirection tankLocation, FluidStack stack, boolean fill) {
        return FluidTankUtil.fill(tile, tankLocation.getOpposite(), stack, fill);
    }

    protected void fillSide(FluidStack toFill, ForgeDirection tankLocation, TileEntity tile) {
        toFill.amount -= fillTank(tile, tankLocation, toFill, true);
    }

    @Override
    public boolean isFluidPipe() {
        return true;
    }

    @Override
    public boolean sharesInterestWith(CoreRoutedPipe other) {
        if (!(other instanceof FluidRoutedPipe)) {
            return false;
        }
        List<TileEntity> theirs = ((FluidRoutedPipe) other).getAllTankTiles();
        for (TileEntity tile : getAllTankTiles()) {
            if (theirs.contains(tile)) {
                return true;
            }
        }
        return false;
    }

    public List<TileEntity> getAllTankTiles() {
        List<TileEntity> list = new ArrayList<>();
        for (Pair<TileEntity, ForgeDirection> pair : getAdjacentTanks(false)) {
            list.addAll(SimpleServiceLocator.specialTankHandler.getBaseTileFor(pair.getValue1()));
        }
        return list;
    }

    public LogisticsFluidOrderManager getFluidOrderManager() {
        _orderFluidManager = _orderFluidManager != null ? _orderFluidManager : new LogisticsFluidOrderManager(this);
        return _orderFluidManager;
    }

    @Override
    public LogisticsOrderManager<?, ?> getOrderManager() {
        return getFluidOrderManager();
    }
}
