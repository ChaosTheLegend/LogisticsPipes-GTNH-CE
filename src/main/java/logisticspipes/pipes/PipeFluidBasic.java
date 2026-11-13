package logisticspipes.pipes;

import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import logisticspipes.api.IMUICompatiblePipeV2;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.PipeGuiFactory;
import logisticspipes.interfaces.IFluidSinkSettings;
import logisticspipes.interfaces.routing.IFluidSink;
import logisticspipes.items.ItemModule;
import logisticspipes.modules.ModuleFluidSink;
import logisticspipes.pipes.basic.fluid.FluidRoutedPipe;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.utils.FluidFilter;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.SinkReply.FixedPriority;

/**
 * Takes fluid from the network into the tanks on every side. Its built-in fluid sink module, the same one that goes
 * into a chassis, keeps the filter and the default route; the module host takes the arriving fluid.
 */
public class PipeFluidBasic extends FluidRoutedPipe implements IFluidSink, IFluidSinkSettings, IMUICompatiblePipeV2 {

    public PipeFluidBasic(Item item) {
        super(item);
        hostModule(ItemModule.FLUID_SINK);
    }

    private ModuleFluidSink sink() {
        return (ModuleFluidSink) getLogisticsModule();
    }

    /*** IFluidSinkSettings, kept by the module ***/

    @Override
    public FluidFilter getFilter() {
        return sink().getFilter();
    }

    @Override
    public boolean isDefaultRoute() {
        return sink().isDefaultRoute();
    }

    @Override
    public void setDefaultRoute(boolean defaultRoute) {
        sink().setDefaultRoute(defaultRoute);
    }

    @Override
    public void importFromTank() {
        sink().importFromTank();
    }

    /*** IFluidSink ***/

    @Override
    public int sinkAmount(FluidStack stack) {
        // fluid an older version left waiting at the pipe has its place in the tanks already
        long waiting = getPendingFluids().getAmount(FluidIdentifier.get(stack));
        return (int) Math.max(0, moduleHost.getFluidManager().sinkAmount(stack) - waiting);
    }

    /**
     * A fluid that is taken by filter is preferred over one that is only taken as default route.
     */
    @Override
    public FixedPriority getFluidSinkPriority(FluidStack stack) {
        return moduleHost.getFluidManager().getFluidSinkPriority(stack);
    }

    @Override
    public int getFluidSinkCustomPriority(FluidStack stack) {
        return moduleHost.getFluidManager().getFluidSinkCustomPriority(stack);
    }

    /**
     * The module reads its filter and default route from the pipe's tag, where the pipe kept them before. Older
     * versions kept the first, then only, filter slot in the tag of the pipe itself.
     */
    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        super.readFromNBT(nbttagcompound);
        if (!nbttagcompound.hasKey("filter0")) {
            sink().getFilter().getTank(0).setFluid(FluidStack.loadFluidStackFromNBT(nbttagcompound));
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
    public boolean canInsertToTanks() {
        return true;
    }

    @Override
    public boolean canReceiveFluid() {
        return false;
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        // the window of its fluid sink module, with the module's upgrades and the settings (sides, priority)
        return PipeGuiFactory.fromModule(this, sink());
    }
}
