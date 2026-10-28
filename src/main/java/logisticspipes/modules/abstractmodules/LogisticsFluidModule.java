package logisticspipes.modules.abstractmodules;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.api.IMUICompatibleModule;
import logisticspipes.interfaces.IFluidModuleHost;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.network.abstractguis.ModuleCoordinatesGuiProvider;
import logisticspipes.network.abstractguis.ModuleInHandGuiProvider;
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

    protected TileEntity getTankTile() {
        IFluidModuleHost host = getHost();
        return host == null ? null : host.getFluidTargetTile();
    }

    protected IFluidHandler getTank() {
        return getTankTile() instanceof IFluidHandler handler ? handler : null;
    }

    public ForgeDirection getTankSide() {
        IFluidModuleHost host = getHost();
        return host == null ? ForgeDirection.UNKNOWN : host.getFluidTargetSide(getPositionInt());
    }

    /**
     * Takes fluid that was routed to the host.
     *
     * @return the amount put into the tank
     */
    public int acceptFluid(FluidStack stack) {
        return fillTank(stack, stack.amount);
    }

    protected int fillTank(FluidStack stack, int max) {
        if (max <= 0) {
            return 0;
        }
        FluidStack toFill = stack.copy();
        toFill.amount = Math.min(stack.amount, max);
        return FluidTankUtil.fill(getTankTile(), getTankSide(), toFill, true);
    }

    /**
     * @return how much of that fluid the tank still takes
     */
    protected long getTankRoom(FluidIdentifier fluid) {
        return FluidTankUtil.getRoom(getTankTile(), getTankSide(), fluid);
    }

    /**
     * @return the fluids that can be drained from the tank
     */
    protected Map<FluidIdentifier, Integer> getTankContents() {
        Map<FluidIdentifier, Integer> map = new HashMap<>();
        for (Entry<FluidIdentifier, Long> entry : FluidTankUtil.getContents(getTankTile(), getTankSide(), true)
                .entrySet()) {
            map.put(entry.getKey(), (int) Math.min(entry.getValue(), Integer.MAX_VALUE));
        }
        return map;
    }

    /**
     * @return the amount of that fluid in the tank, drainable or not
     */
    protected int getStoredAmount(FluidIdentifier fluid) {
        return (int) Math.min(FluidTankUtil.getStored(getTankTile(), getTankSide(), fluid), Integer.MAX_VALUE);
    }

    /**
     * Drains up to that amount of the fluid from the tank.
     *
     * @return what was drained, or null
     */
    protected FluidStack drainTank(FluidIdentifier fluid, int amount) {
        return FluidTankUtil.drain(getTankTile(), getTankSide(), fluid, amount);
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
