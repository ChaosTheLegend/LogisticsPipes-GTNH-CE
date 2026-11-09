package logisticspipes.pipes;

import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.api.IMUICompatibleModule;
import logisticspipes.api.IMUICompatiblePipeV2;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.PipeGuiFactory;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.interfaces.routing.IProvideFluids;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.items.ItemModule;
import logisticspipes.pipes.basic.fluid.FluidRoutedPipe;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTreeNode;
import logisticspipes.routing.FluidLogisticsPromise;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.IHavePriority;

/**
 * Offers the fluid in the tanks on every side to requests from the network. Its built-in fluid provider module, the
 * same one that goes into a chassis, makes the promises and sends the fluid; the pipe hands the requests to its module
 * host. A tank with a special handler, like an ME interface, offers what the handler reports.
 */
public class PipeFluidProvider extends FluidRoutedPipe implements IProvideFluids, IHavePriority, IMUICompatiblePipeV2 {

    public PipeFluidProvider(Item item) {
        super(item);
        hostModule(ItemModule.FLUID_PROVIDER);
    }

    /*** IProvideFluids, answered by the module ***/

    @Override
    public void canProvide(RequestTreeNode tree, RequestTree root, List<IFilter> filter) {
        // the host also refuses behind a firewall that blocks providers
        moduleHost.getFluidManager().canProvide(tree, root, filter);
    }

    @Override
    public Map<FluidIdentifier, Integer> getAvailableFluids() {
        return moduleHost.getFluidManager().getAvailableFluids();
    }

    @Override
    public IOrderInfoProvider fullFill(FluidLogisticsPromise promise, IRequestFluid destination, ResourceType type,
            IAdditionalTargetInformation info) {
        // promises are made by the module and come back to it, this is only the fallback
        return moduleHost.getFluidManager().fullFill(promise, destination, type, info);
    }

    @Override
    public boolean disconnectPipe(TileEntity tile, ForgeDirection dir) {
        return SimpleServiceLocator.pipeInformationManager.isFluidPipe(tile);
    }

    @Override
    public TextureType getCenterTexture() {
        return Textures.LOGISTICSPIPE_LIQUID_PROVIDER;
    }

    @Override
    public boolean canInsertToTanks() {
        return true;
    }

    @Override
    public boolean canInsertFromSideToTanks() {
        return true;
    }

    @Override
    public boolean canReceiveFluid() {
        return false;
    }

    /**
     * The priority of the built-in fluid provider: which provider a request asks first.
     */
    @Override
    public int getPriority() {
        return getLogisticsModule() == null ? 0 : getLogisticsModule().getPriority();
    }

    /**
     * The window of the built-in module, with its upgrades and the settings (sides, priority).
     */
    @Override
    public LogisticsModularUI getPipeGui() {
        return PipeGuiFactory.fromModule(this, (IMUICompatibleModule) getLogisticsModule());
    }
}
