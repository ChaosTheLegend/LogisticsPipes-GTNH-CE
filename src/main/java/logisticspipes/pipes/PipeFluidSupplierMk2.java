package logisticspipes.pipes;

import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import gnu.trove.iterator.TObjectIntIterator;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import logisticspipes.api.IMUICompatiblePipeV2;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.PipeGuiFactory;
import logisticspipes.gui.modularUI.pipes.PipeFluidSupplierMk2Mui;
import logisticspipes.interfaces.ISpecialTankAccessHandler;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.interfaces.routing.IRequireReliableFluidTransport;
import logisticspipes.migration.LegacyHelper;
import logisticspipes.pipes.basic.fluid.FluidRoutedPipe;
import logisticspipes.pipes.basic.fluid.PendingFluids;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.RequestTree;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.utils.AdjacentTile;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.WorldUtil;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class PipeFluidSupplierMk2 extends FluidRoutedPipe
        implements IRequestFluid, IRequireReliableFluidTransport, IMUICompatiblePipeV2 {

    private boolean _lastRequestFailed = false;

    public final FluidTank phantomTank = new FluidTank(1);
    public int amount = 0;
    public boolean requestPartials = false;
    public int refillThreshold = 0;

    public PipeFluidSupplierMk2(Item item) {
        super(item);
        throttleTime = 100;
    }

    @Override
    public void sendFailed(FluidIdentifier value1, Integer value2) {
        liquidLost(value1, value2);
    }

    @Override
    public ItemSendMode getItemSendMode() {
        return ItemSendMode.Fast;
    }

    @Override
    public boolean canInsertFromSideToTanks() {
        return true;
    }

    @Override
    public boolean canInsertToTanks() {
        return true;
    }

    /* TRIGGER INTERFACE */
    public boolean isRequestFailed() {
        return _lastRequestFailed;
    }

    public void setRequestFailed(boolean value) {
        _lastRequestFailed = value;
    }

    @Override
    public TextureType getCenterTexture() {
        return Textures.LOGISTICSPIPE_LIQUIDSUPPLIER_MK2_TEXTURE;
    }

    @Override
    public boolean hasGenericInterests() {
        return true;
    }

    private final TObjectIntMap<FluidIdentifier> _requestedItems = new TObjectIntHashMap<>(8, 0.5f, -1);

    /**
     * @return how much of the fluid that tile holds. For a tile with a special tank handler, like an ME interface, that
     *         is what the handler reports, so for an ME network the content of the whole network.
     */
    private long getStoredAmount(TileEntity tile, FluidIdentifier fluid) {
        ISpecialTankAccessHandler special = SimpleServiceLocator.specialTankHandler.getAccessHandlerFor(tile);
        if (special != null) {
            Long stored = special.getAvailableLiquid(tile).get(fluid);
            return stored == null ? 0 : stored;
        }
        long stored = 0;
        if (tile instanceof IFluidHandler handler) {
            FluidTankInfo[] tanks = handler.getTankInfo(ForgeDirection.UNKNOWN);
            if (tanks != null) {
                for (FluidTankInfo info : tanks) {
                    if (info != null && info.fluid != null && fluid.equals(FluidIdentifier.get(info.fluid))) {
                        stored += info.fluid.amount;
                    }
                }
            }
        }
        return stored;
    }

    /**
     * @return true if the supplier can keep that tile stocked
     */
    private boolean isSupplyTarget(TileEntity tile) {
        if (SimpleServiceLocator.pipeInformationManager.isItemPipe(tile)) {
            return false;
        }
        if (SimpleServiceLocator.specialTankHandler.getAccessHandlerFor(tile) != null) {
            return true;
        }
        if (!(tile instanceof IFluidHandler handler)) {
            return false;
        }
        FluidTankInfo[] tanks = handler.getTankInfo(ForgeDirection.UNKNOWN);
        return tanks != null && tanks.length > 0;
    }

    @Override
    protected void fillSide(FluidStack toFill, ForgeDirection tankLocation, TileEntity tile) {
        if (phantomTank.getFluid() == null) {
            // shouldn't happen, but ok
            return;
        }
        // check if this tank need more fluid
        long have = getStoredAmount(tile, FluidIdentifier.get(phantomTank.getFluid()));
        int vacant = (int) Math.max(0, amount - have);
        if (vacant <= 0)
            // too much fluid..
            return;
        // we can't actually be too strict with checking here. maybe the tank is being drained by
        // another party faster than LP can sustain.
        // the check to send fluid request does not run every tick after all...
        // so we just check if minimum is reached
        if (refillThreshold != 0 && vacant < refillThreshold) {
            // not enough spare space - this packet must be for tank on different side
            return;
        }
        // attempt to fill now... don't fill too much though
        // if insertion on all sides failed, surplus will end up in one of the internal pipe tanks
        // it won't be wasted
        FluidStack filled = toFill.copy();
        filled.amount = Math.min(filled.amount, vacant);
        toFill.amount -= fillTank(tile, tankLocation, filled, true);
    }

    @Override
    public void throttledUpdateEntity() {
        if (!isEnabled()) {
            return;
        }
        if (MainProxy.isClient(container.getWorld())) {
            return;
        }
        super.throttledUpdateEntity();
        if (phantomTank.getFluid() == null) {
            return;
        }

        TObjectIntMap<FluidIdentifier> requestDiscount = new TObjectIntHashMap<>(_requestedItems);
        // what waits at the pipe for room in a tank is not requested again
        for (PendingFluids.Entry pending : getPendingFluids()) {
            requestDiscount
                    .adjustOrPutValue(FluidIdentifier.get(pending.fluid), pending.fluid.amount, pending.fluid.amount);
        }

        WorldUtil worldUtil = new WorldUtil(getWorld(), getX(), getY(), getZ());
        for (AdjacentTile tile : worldUtil.getAdjacentTileEntities(true)) {
            if (!isSupplyTarget(tile.tile)) {
                continue;
            }

            // How much should I request?
            TObjectIntMap<FluidIdentifier> wantFluids = new TObjectIntHashMap<>(8);
            FluidIdentifier fIdent = FluidIdentifier.get(phantomTank.getFluid());
            wantFluids.put(fIdent, (int) Math.max(0, amount - getStoredAmount(tile.tile, fIdent)));

            // Reduce what have been requested already
            for (TObjectIntIterator<FluidIdentifier> iter = requestDiscount.iterator(); iter.hasNext();) {
                iter.advance();
                wantFluids.adjustValue(iter.key(), -iter.value());
            }

            setRequestFailed(false);

            // Make request

            for (TObjectIntIterator<FluidIdentifier> iter = wantFluids.iterator(); iter.hasNext();) {
                iter.advance();
                FluidIdentifier need = iter.key();
                int countToRequest = iter.value();

                if (countToRequest <= 0) {
                    // skip entries that already have enough in it
                    continue;
                }

                if (refillThreshold != 0 && countToRequest < refillThreshold) {
                    continue;
                }

                if (!useEnergy(11)) {
                    break;
                }

                boolean success = false;

                if (requestPartials) {
                    countToRequest = RequestTree.requestFluidPartial(need, countToRequest, this, null);
                    if (countToRequest > 0) {
                        success = true;
                    }
                } else {
                    success = RequestTree.requestFluid(need, countToRequest, this, null);
                }

                if (success) {
                    _requestedItems.adjustOrPutValue(need, countToRequest, countToRequest);
                } else {
                    setRequestFailed(true);
                }
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);

        if (compound.hasKey("items")) {
            // legacy
            LegacyHelper.readItemIdentifierInventoryAndConvertToTank(phantomTank, compound, "");
        } else {
            phantomTank.readFromNBT(compound.getCompoundTag("tank"));
        }

        if (compound.hasKey("_bucketMinimum")) {
            // legacy
            byte bucketMinimum = compound.getByte("_bucketMinimum");
            if (bucketMinimum == 0) refillThreshold = 0;
            if (bucketMinimum == 1) refillThreshold = 1000;
            if (bucketMinimum == 2) refillThreshold = 2000;
            if (bucketMinimum == 3) refillThreshold = 5000;
        } else {
            refillThreshold = compound.getInteger("refillThreshold");
        }

        requestPartials = compound.getBoolean("requestpartials");
        amount = compound.getInteger("amount");
    }

    @Override
    public void writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setBoolean("requestpartials", requestPartials);
        compound.setInteger("amount", amount);
        compound.setInteger("refillThreshold", refillThreshold);

        compound.setTag("tank", phantomTank.writeToNBT(new NBTTagCompound()));
    }

    private void decreaseRequested(FluidIdentifier liquid, int remaining) {
        // see if we can get an exact match
        int count = _requestedItems.get(liquid);
        if (count <= 0) return;
        if (count <= remaining) _requestedItems.remove(liquid);
        else _requestedItems.put(liquid, count - remaining);
        if (remaining > count) {
            // we have no idea what this is, log it.
            debug.log("liquid supplier got unexpected item %s", liquid.toString());
        }
    }

    @Override
    public void liquidLost(FluidIdentifier item, int amount) {
        decreaseRequested(item, amount);
    }

    @Override
    public void liquidArrived(FluidIdentifier item, int amount) {
        decreaseRequested(item, amount);
        delayThrottle();
    }

    @Override
    public void liquidNotInserted(FluidIdentifier item, int amount) {}

    @Override
    public boolean canReceiveFluid() {
        return false;
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        return PipeGuiFactory.fromMui(new PipeFluidSupplierMk2Mui(this));
    }
}
