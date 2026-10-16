package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.routing.IRouter;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.tuples.Pair;

public class PipeFluidPatternSatelliteLogistics extends logisticspipes.pipes.PipeFluidSatellite
        implements IPatternSatellitePipe, PatternByproductExtractionTarget {

    private static final Set<PipeFluidPatternSatelliteLogistics> ALL_PATTERN_FLUID_SATELLITES = Collections
            .newSetFromMap(new WeakHashMap<>());
    private static final String UUID_TAG = "patternFluidSatelliteUuid";
    private static final String NAME_TAG = "patternFluidSatelliteName";

    private String satelliteUuid = UUID.randomUUID().toString();
    private String satelliteName = "";
    private final Map<FluidIdentifier, Integer> reservationBaseline = new HashMap<>();
    private final PatternSatelliteByproductExtractor byproductExtractor = new PatternSatelliteByproductExtractor(this);
    private int reservedOwnerRouter = -1;
    private PatternCraftingReference reservedReference;

    public PipeFluidPatternSatelliteLogistics(Item item) {
        super(item);
    }

    public static void cleanup() {
        ALL_PATTERN_FLUID_SATELLITES.clear();
    }

    public static PipeFluidPatternSatelliteLogistics findById(int satelliteId) {
        if (satelliteId <= 0) {
            return null;
        }
        for (PipeFluidPatternSatelliteLogistics satellite : ALL_PATTERN_FLUID_SATELLITES) {
            if (satellite != null && satellite.satelliteId == satelliteId) {
                return satellite;
            }
        }
        return null;
    }

    public static PipeFluidPatternSatelliteLogistics findByUuid(String satelliteUuid) {
        if (satelliteUuid == null || satelliteUuid.isEmpty()) {
            return null;
        }
        for (PipeFluidPatternSatelliteLogistics satellite : ALL_PATTERN_FLUID_SATELLITES) {
            if (satellite != null && satelliteUuid.equals(satellite.satelliteUuid)) {
                return satellite;
            }
        }
        return null;
    }

    static List<PatternByproductExtractionTarget> getRegisteredByproductExtractionTargets() {
        return new ArrayList<>(ALL_PATTERN_FLUID_SATELLITES);
    }

    public static List<PatternSatelliteInfo> getKnownSatellitesFor(EntityPlayer player) {
        List<PatternSatelliteInfo> satellites = new ArrayList<>();
        int playerDimension = player != null && player.worldObj != null
                ? MainProxy.getDimensionForWorld(player.worldObj)
                : Integer.MIN_VALUE;
        for (PipeFluidPatternSatelliteLogistics satellite : ALL_PATTERN_FLUID_SATELLITES) {
            if (!isSelectableSatellite(satellite)) {
                continue;
            }
            int dimension = MainProxy.getDimensionForWorld(satellite.getWorld());
            satellites.add(
                    new PatternSatelliteInfo(
                            satellite.satelliteId,
                            satellite.getX(),
                            satellite.getY(),
                            satellite.getZ(),
                            dimension,
                            getDistance(player, playerDimension, satellite, dimension),
                            false,
                            satellite.satelliteUuid,
                            satellite.getDisplayName(),
                            PatternSatelliteInfo.SatelliteType.FLUID));
        }
        satellites.sort((left, right) -> {
            boolean leftSameDimension = left.distance() >= 0;
            boolean rightSameDimension = right.distance() >= 0;
            if (leftSameDimension != rightSameDimension) {
                return leftSameDimension ? -1 : 1;
            }
            if (leftSameDimension && left.distance() != right.distance()) {
                return Integer.compare(left.distance(), right.distance());
            }
            return Integer.compare(left.id(), right.id());
        });
        return satellites;
    }

    private static boolean isSelectableSatellite(PipeFluidPatternSatelliteLogistics satellite) {
        return satellite != null && satellite.satelliteId > 0
                && satellite.container != null
                && !satellite.container.isInvalid()
                && satellite.getWorld() != null;
    }

    private static int getDistance(EntityPlayer player, int playerDimension,
            PipeFluidPatternSatelliteLogistics satellite, int satelliteDimension) {
        if (player == null || playerDimension != satelliteDimension) {
            return -1;
        }
        double dx = satellite.getX() + 0.5D - player.posX;
        double dy = satellite.getY() + 0.5D - player.posY;
        double dz = satellite.getZ() + 0.5D - player.posZ;
        return (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    public String getSatelliteUuid() {
        return satelliteUuid;
    }

    public String getDisplayName() {
        return satelliteName == null || satelliteName.trim().isEmpty() ? Integer.toString(satelliteId)
                : satelliteName.trim();
    }

    @Override
    public String getSatelliteName() {
        return satelliteName == null ? "" : satelliteName.trim();
    }

    @Override
    public boolean canExtractByproductsFor(IRouter requester) {
        return byproductExtractor.canExtractFor(requester);
    }

    @Override
    public PatternByproductExtractionResult extractItemByproduct(ItemIdentifier item, int amount, int destination,
            IAdditionalTargetInformation info) {
        return byproductExtractor.extractItem(item, amount, destination, info);
    }

    @Override
    public PatternByproductExtractionResult extractFluidByproduct(FluidIdentifier fluid, int amount, int destination,
            IAdditionalTargetInformation info) {
        return byproductExtractor.extractFluid(fluid, amount, destination, info);
    }

    public void setSatelliteName(String satelliteName) {
        this.satelliteName = satelliteName == null ? "" : satelliteName.trim();
        ensureAllSatelliteStatus();
        if (container != null) {
            container.markDirty();
            container.sendUpdateToClient();
        }
    }

    /**
     * Returns true when this fluid satellite is unlocked or only pre-reserved by the same crafting pipe.
     */
    public boolean canReserveFor(PipeItemsPatternCraftingLogistics owner, PatternCraftingReference reference) {
        int ownerRouter = ownerRouterId(owner);
        return ownerRouter >= 0 && reference != null
                && (reservedOwnerRouter < 0
                        || (reservedOwnerRouter == ownerRouter && reference.equals(reservedReference)
                                && reservationBaseline.isEmpty()));
    }

    /**
     * Locks this fluid satellite for a pattern crafting pipe before a complete buffered set is dispatched.
     */
    public boolean reserveFor(PipeItemsPatternCraftingLogistics owner, PatternCraftingReference reference) {
        if (!canReserveFor(owner, reference)) {
            return false;
        }
        reservedOwnerRouter = ownerRouterId(owner);
        reservedReference = reference;
        return true;
    }

    /**
     * Releases the reservation held by the owner pipe.
     */
    public void releaseReservation(PipeItemsPatternCraftingLogistics owner, PatternCraftingReference reference) {
        int ownerRouter = ownerRouterId(owner);
        if (ownerRouter >= 0
                && (reservedOwnerRouter != ownerRouter || !java.util.Objects.equals(reservedReference, reference))) {
            return;
        }
        reservedOwnerRouter = -1;
        reservedReference = null;
        reservationBaseline.clear();
    }

    /**
     * Returns true once all fluid inserted during the current reservation has been consumed.
     */
    public boolean isReservationConsumed(PipeItemsPatternCraftingLogistics owner, PatternCraftingReference reference) {
        int ownerRouter = ownerRouterId(owner);
        if (ownerRouter < 0 || reservedOwnerRouter != ownerRouter
                || !java.util.Objects.equals(reservedReference, reference)) {
            return true;
        }
        for (Map.Entry<FluidIdentifier, Integer> entry : reservationBaseline.entrySet()) {
            if (countAdjacentFluid(entry.getKey()) > entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks whether adjacent satellite tanks can accept the complete fluid amount.
     */
    public boolean canAcceptPatternInput(FluidIdentifier fluid, int amount) {
        return fluid != null && amount > 0 && fillPatternInput(fluid, amount, false) == amount;
    }

    /**
     * Returns whether the adjacent tank target is ready for a blocking-mode satellite batch.
     */
    public boolean isPatternTargetEmpty() {
        boolean hasTank = false;
        for (Pair<TileEntity, ForgeDirection> pair : getAdjacentTanks(false)) {
            if (!(pair.getValue1() instanceof IFluidHandler handler)) {
                continue;
            }
            hasTank = true;
            FluidTankInfo[] tanks = handler.getTankInfo(pair.getValue2().getOpposite());
            if (tanks == null) {
                continue;
            }
            for (FluidTankInfo tank : tanks) {
                if (tank != null && tank.fluid != null && tank.fluid.amount > 0) {
                    return false;
                }
            }
        }
        return hasTank;
    }

    /**
     * Inserts a complete fluid pattern input into adjacent satellite tanks.
     */
    public int insertPatternInput(FluidIdentifier fluid, int amount) {
        return insertPatternInput(fluid, amount, true);
    }

    /**
     * Inserts a complete fluid pattern input and optionally tracks it as a blocking-mode reservation.
     */
    public int insertPatternInput(FluidIdentifier fluid, int amount, boolean trackReservation) {
        if (fluid == null || amount <= 0) {
            return 0;
        }
        int before = countAdjacentFluid(fluid);
        int inserted = fillPatternInput(fluid, amount, true);
        if (inserted > 0 && trackReservation) {
            reservationBaseline.putIfAbsent(fluid, before);
        }
        return inserted;
    }

    /**
     * Retrieves cancelled craft fluid from adjacent tanks and routes it back into normal storage.
     *
     * @return amount that was drained and queued for storage routing
     */
    public int retrieveFluidToStorage(FluidIdentifier fluid, int amount) {
        if (fluid == null || amount <= 0 || MainProxy.isClient(getWorld())) {
            return 0;
        }
        int remaining = amount;
        int retrieved = 0;
        for (Pair<TileEntity, ForgeDirection> pair : getAdjacentTanks(false)) {
            if (remaining <= 0) {
                break;
            }
            if (!(pair.getValue1() instanceof IFluidHandler tank)) {
                continue;
            }
            ForgeDirection side = pair.getValue2().getOpposite();
            FluidTankInfo[] tanks = tank.getTankInfo(side);
            if (tanks == null) {
                continue;
            }
            for (FluidTankInfo tankInfo : tanks) {
                if (remaining <= 0) {
                    break;
                }
                if (tankInfo == null || tankInfo.fluid == null || !fluid.equals(FluidIdentifier.get(tankInfo.fluid))) {
                    continue;
                }
                int toDrain = Math.min(remaining, tankInfo.fluid.amount);
                FluidStack simulated = tank.drain(side, toDrain, false);
                if (simulated == null || simulated.amount <= 0 || !fluid.equals(FluidIdentifier.get(simulated))) {
                    continue;
                }
                FluidStack drained = tank.drain(side, simulated.amount, true);
                if (drained == null || drained.amount <= 0 || !fluid.equals(FluidIdentifier.get(drained))) {
                    continue;
                }
                queueFluidToStorage(drained, pair.getValue2());
                remaining -= drained.amount;
                retrieved += drained.amount;
            }
        }
        return retrieved;
    }

    private void queueFluidToStorage(FluidStack fluid, ForgeDirection from) {
        ItemIdentifierStack container = SimpleServiceLocator.logisticsFluidManager.getFluidContainer(fluid);
        IRoutedItem routedItem = SimpleServiceLocator.routedItemHelper.createNewTravelItem(container);
        routedItem.setDestination(-1);
        routedItem.setTransportMode(TransportMode.Active);
        queueRoutedItem(routedItem, from == null ? ForgeDirection.UNKNOWN : from);
    }

    private int fillPatternInput(FluidIdentifier fluid, int amount, boolean doFill) {
        int remaining = amount;
        int inserted = 0;
        for (Pair<TileEntity, ForgeDirection> pair : getAdjacentTanks(false)) {
            if (remaining <= 0) {
                break;
            }
            if (!(pair.getValue1() instanceof IFluidHandler handler)) {
                continue;
            }
            ForgeDirection side = pair.getValue2().getOpposite();
            FluidStack stack = fluid.makeFluidStack(remaining);
            int filled = handler.fill(side, stack, doFill);
            remaining -= filled;
            inserted += filled;
        }
        return inserted;
    }

    private int countAdjacentFluid(FluidIdentifier fluid) {
        if (fluid == null) {
            return 0;
        }
        int amount = 0;
        for (Pair<TileEntity, ForgeDirection> pair : getAdjacentTanks(false)) {
            if (!(pair.getValue1() instanceof IFluidHandler handler)) {
                continue;
            }
            FluidTankInfo[] tanks = handler.getTankInfo(pair.getValue2().getOpposite());
            if (tanks == null) {
                continue;
            }
            for (FluidTankInfo tank : tanks) {
                if (tank != null && tank.fluid != null && fluid.equals(FluidIdentifier.get(tank.fluid))) {
                    amount += tank.fluid.amount;
                }
            }
        }
        return amount;
    }

    private int ownerRouterId(PipeItemsPatternCraftingLogistics owner) {
        return owner == null || owner.getRouter() == null ? -1 : owner.getRouter().getSimpleID();
    }

    @Override
    public void enabledUpdateEntity() {
        super.enabledUpdateEntity();
        if (!MainProxy.isClient(getWorld()) && isNthTick(40)) {
            ensureAllSatelliteStatus();
        }
    }

    @Override
    protected void ensureAllSatelliteStatus() {
        if (MainProxy.isClient()) {
            return;
        }
        if (satelliteId == 0) {
            satelliteId = findId(1);
        }
        super.ensureAllSatelliteStatus();
        if (satelliteId == 0) {
            ALL_PATTERN_FLUID_SATELLITES.remove(this);
        } else {
            ALL_PATTERN_FLUID_SATELLITES.add(this);
            ensureUniqueDisplayNameInNetwork();
        }
    }

    @Override
    public void setSatelliteId(int satelliteId) {
        this.satelliteId = satelliteId;
        ensureAllSatelliteStatus();
    }

    @Override
    public void onWrenchClicked(EntityPlayer entityplayer) {
        // legacy wrenches get the MUI as well; the name field lives there
        openGui(entityplayer, this);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        super.readFromNBT(nbttagcompound);
        satelliteUuid = nbttagcompound.hasKey(UUID_TAG) ? nbttagcompound.getString(UUID_TAG)
                : UUID.randomUUID().toString();
        satelliteName = nbttagcompound.getString(NAME_TAG);
        ensureAllSatelliteStatus();
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
        nbttagcompound.setString(UUID_TAG, satelliteUuid);
        nbttagcompound.setString(NAME_TAG, satelliteName == null ? "" : satelliteName);
    }

    @Override
    public void onAllowedRemoval() {
        super.onAllowedRemoval();
        if (!MainProxy.isClient(getWorld())) {
            ALL_PATTERN_FLUID_SATELLITES.remove(this);
        }
    }

    private void ensureUniqueDisplayNameInNetwork() {
        String displayName = getDisplayName();
        if (displayName.isEmpty()) {
            return;
        }
        int suffix = 2;
        String baseName = displayName;
        while (hasDisplayNameConflict(displayName)) {
            displayName = baseName + "-" + suffix++;
        }
        if (!displayName.equals(getDisplayName())) {
            satelliteName = displayName;
        }
    }

    private boolean hasDisplayNameConflict(String displayName) {
        for (PipeFluidPatternSatelliteLogistics satellite : ALL_PATTERN_FLUID_SATELLITES) {
            if (satellite == null || satellite == this || !isSelectableSatellite(satellite)) {
                continue;
            }
            if (displayName.equalsIgnoreCase(satellite.getDisplayName()) && isInSameNetwork(satellite)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInSameNetwork(PipeFluidPatternSatelliteLogistics other) {
        try {
            IRouter router = getRouter();
            IRouter otherRouter = other.getRouter();
            return router == otherRouter
                    || (router != null && otherRouter != null && !router.getDistanceTo(otherRouter).isEmpty());
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
