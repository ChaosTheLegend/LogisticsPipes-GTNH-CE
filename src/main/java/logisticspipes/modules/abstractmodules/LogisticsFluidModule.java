package logisticspipes.modules.abstractmodules;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.api.IMUICompatibleModule;
import logisticspipes.interfaces.IFluidModuleHost;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.network.abstractguis.ModuleCoordinatesGuiProvider;
import logisticspipes.network.abstractguis.ModuleInHandGuiProvider;
import logisticspipes.pipes.modulehost.FluidTarget;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.FluidSendUtil;
import logisticspipes.utils.FluidTankUtil;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.item.ItemIdentifier;

/**
 * Base of the modules that work on the tank a chassis points at. Fluid travels through the network as a fluid container
 * item; the module fills and drains the tank directly, the pipe itself holds no fluid.
 */
public abstract class LogisticsFluidModule extends LogisticsGuiModule implements IMUICompatibleModule {

    /**
     * @return the pipe this module sits in, or null while it is held in hand or sits in a pipe without fluid support
     */
    protected IFluidModuleHost getHost() {
        return _service instanceof IFluidModuleHost host ? host : null;
    }

    /**
     * @return the tanks this module works on, empty while it has none (held in hand, nothing to point at)
     */
    protected List<FluidTarget> getTargets() {
        IFluidModuleHost host = getHost();
        return host == null ? Collections.emptyList() : host.getFluidTargets(getPositionInt());
    }

    /**
     * @return true if the module has a tank to work on
     */
    protected boolean hasTank() {
        return !getTargets().isEmpty();
    }

    /**
     * Takes fluid that was routed to the host.
     *
     * @return the amount put into the tank
     */
    public int acceptFluid(FluidStack stack) {
        return fillTank(stack, stack.amount);
    }

    /**
     * Fills the tanks one after another.
     *
     * @return the amount filled
     */
    protected int fillTank(FluidStack stack, int max) {
        int left = Math.min(stack.amount, max);
        int filled = 0;
        for (FluidTarget target : getTargets()) {
            if (left <= 0) {
                break;
            }
            FluidStack toFill = stack.copy();
            toFill.amount = left;
            int done = FluidTankUtil.fill(target.tile, target.side, toFill, true);
            filled += done;
            left -= done;
        }
        return filled;
    }

    /**
     * @return how much of that fluid the tanks still takes
     */
    protected long getTankRoom(FluidIdentifier fluid) {
        long room = 0;
        for (FluidTarget target : getTargets()) {
            room += FluidTankUtil.getRoom(target.tile, target.side, fluid);
        }
        return room;
    }

    /**
     * @return the fluids that can be drained from the tanks, summed up
     */
    protected Map<FluidIdentifier, Integer> getTankContents() {
        Map<FluidIdentifier, Long> sum = new HashMap<>();
        for (FluidTarget target : getTargets()) {
            for (Entry<FluidIdentifier, Long> entry : FluidTankUtil.getContents(target.tile, target.side, true)
                    .entrySet()) {
                sum.merge(entry.getKey(), entry.getValue(), Long::sum);
            }
        }
        Map<FluidIdentifier, Integer> map = new HashMap<>();
        for (Entry<FluidIdentifier, Long> entry : sum.entrySet()) {
            map.put(entry.getKey(), (int) Math.min(entry.getValue(), Integer.MAX_VALUE));
        }
        return map;
    }

    /**
     * @return the amount of that fluid in the tanks, drainable or not
     */
    protected int getStoredAmount(FluidIdentifier fluid) {
        long stored = 0;
        for (FluidTarget target : getTargets()) {
            stored += FluidTankUtil.getStored(target.tile, target.side, fluid);
        }
        return (int) Math.min(stored, Integer.MAX_VALUE);
    }

    /**
     * Drains up to that amount of the fluid from the tanks, one after another.
     *
     * @return what was drained, or null
     */
    protected FluidStack drainTank(FluidIdentifier fluid, int amount) {
        FluidStack drained = null;
        for (FluidTarget target : getTargets()) {
            int left = amount - (drained == null ? 0 : drained.amount);
            if (left <= 0) {
                break;
            }
            FluidStack part = FluidTankUtil.drain(target.tile, target.side, fluid, left);
            if (part == null || part.amount <= 0) {
                continue;
            }
            if (drained == null) {
                drained = part;
            } else {
                drained.amount += part.amount;
            }
        }
        return drained;
    }

    /**
     * Sends fluid into the network as a fluid container.
     */
    protected IRoutedItem sendFluid(FluidStack stack, int destination, TransportMode mode,
            IAdditionalTargetInformation info) {
        IRoutedItem item = FluidSendUtil.createItem(stack, destination, mode, info);
        queue(item);
        return item;
    }

    private void queue(IRoutedItem item) {
        IFluidModuleHost host = getHost();
        ForgeDirection from = host.getPointedOrientation();
        host.queueRoutedItem(item, from == null || from == ForgeDirection.UNKNOWN ? ForgeDirection.UP : from);
    }

    /**
     * Called on the server before the module leaves its pipe: it is taken out or replaced, or the pipe is removed.
     */
    public void onRemoved() {}

    @Override
    public SinkReply sinksItem(ItemIdentifier stack, int bestPriority, int bestCustomPriority, boolean allowDefault,
            boolean includeInTransit) {
        return null;
    }

    @Override
    public LogisticsModule getSubModule(int slot) {
        return null;
    }

    @Override
    public void tick() {}

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {}

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {}

    @Override
    public boolean hasGenericInterests() {
        return false;
    }

    @Override
    public Collection<ItemIdentifier> getSpecificInterests() {
        return null;
    }

    @Override
    public boolean interestedInAttachedInventory() {
        return false;
    }

    @Override
    public boolean interestedInUndamagedID() {
        return false;
    }

    @Override
    public boolean recievePassive() {
        return false;
    }

    // fluid modules only have a ModularUI
    @Override
    protected ModuleCoordinatesGuiProvider getPipeGuiProvider() {
        return null;
    }

    @Override
    protected ModuleInHandGuiProvider getInHandGuiProvider() {
        return null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconTexture(IIconRegister register) {
        return register.registerIcon("logisticspipes:itemModule/" + getClass().getSimpleName());
    }
}
