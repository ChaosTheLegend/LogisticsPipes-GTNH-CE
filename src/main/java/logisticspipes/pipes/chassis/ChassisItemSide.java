package logisticspipes.pipes.chassis;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.interfaces.IInventoryUtil;
import logisticspipes.interfaces.ILegacyActiveModule;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.ICraftItems;
import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.modules.abstractmodules.LogisticsFluidModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipefxhandlers.Particles;
import logisticspipes.pipes.PipeLogisticsChassi;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.ICraftingTemplate;
import logisticspipes.request.IPromise;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTreeNode;
import logisticspipes.request.resources.DictResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.astar.CategoryInterests;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.routing.order.LogisticsItemOrder;
import logisticspipes.routing.order.LogisticsOrder;
import logisticspipes.transport.LPTravelingItem.LPTravelingItemServer;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * The inventory a chassis points at and the item modules that work on it: providing, crafting and the interests in
 * items.
 */
public class ChassisItemSide implements ChassisSide {

    private final PipeLogisticsChassi pipe;

    private final LinkedList<LogisticsOrder> extras = new LinkedList<>();

    public ChassisItemSide(PipeLogisticsChassi pipe) {
        this.pipe = pipe;
    }

    private LogisticsModule getModule(int slot) {
        return pipe.getModules().getSubModule(slot);
    }

    @Override
    public boolean ownsModule(LogisticsModule module) {
        return !(module instanceof LogisticsFluidModule);
    }

    @Override
    public boolean isValidTarget(TileEntity tile, ForgeDirection dir) {
        if (SimpleServiceLocator.pipeInformationManager.isItemPipe(tile)) {
            return false;
        }
        return MainProxy.checkPipesConnections(pipe.container, tile, dir);
    }

    @Override
    public boolean isActive() {
        return pipe.getRealInventory() != null;
    }

    @Override
    public void onRemoved() {
        for (int i = 0; i < pipe.getChassiSize(); i++) {
            if (getModule(i) instanceof ILegacyActiveModule module) {
                module.onBlockRemoval();
            }
        }
    }

    /*** interests ***/

    @Override
    public void addSpecificInterests(Set<ItemIdentifier> interests) {
        for (int i = 0; i < pipe.getChassiSize(); i++) {
            LogisticsModule module = getModule(i);
            if (module != null && ownsModule(module) && module.interestedInAttachedInventory()) {
                IInventoryUtil inv = pipe.getSneakyInventory(false, module.getSlot(), module.getPositionInt());
                if (inv == null) {
                    continue;
                }
                Set<ItemIdentifier> items = inv.getItems();
                interests.addAll(items);

                // also add tag-less variants ... we should probably add a module.interestedIgnoringNBT at some point
                for (ItemIdentifier id : items) {
                    interests.add(id.getIgnoringNBT());
                }

                if (module.interestedInUndamagedID()) {
                    for (ItemIdentifier id : items) {
                        interests.add(id.getUndamaged());
                    }
                }
                break; // no need to check other modules for interest in the inventory, when we know that 1 already is.
            }
        }
        for (int i = 0; i < pipe.getChassiSize(); i++) {
            LogisticsModule module = getModule(i);
            if (module != null && ownsModule(module)) {
                Collection<ItemIdentifier> current = module.getSpecificInterests();
                if (current != null) {
                    interests.addAll(current);
                }
            }
        }
    }

    @Override
    public boolean hasGenericInterests() {
        for (int i = 0; i < pipe.getChassiSize(); i++) {
            LogisticsModule module = getModule(i);
            if (module != null && ownsModule(module) && module.hasGenericInterests()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public CategoryInterests getCategoryInterests() {
        CategoryInterests interests = CategoryInterests.EMPTY;
        for (int i = 0; i < pipe.getChassiSize(); i++) {
            LogisticsModule module = getModule(i);
            if (module != null && ownsModule(module)) {
                interests = interests.merge(module.getCategoryInterests());
            }
        }
        return interests;
    }

    /*** arriving items ***/

    @Override
    public boolean wantsArrival(ItemIdentifierStack stack) {
        for (int i = 0; i < pipe.getChassiSize(); i++) {
            LogisticsModule module = getModule(i);
            if (module != null && ownsModule(module)) {
                SinkReply reply = module.sinksItem(stack.getItem(), -1, 0, true, false);
                if (reply != null && reply.maxNumberOfItems >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean takeArrival(LPTravelingItemServer arrivingItem) {
        // the transport puts it into the inventory
        return false;
    }

    /*** IProvideItems ***/

    public void canProvide(RequestTreeNode tree, RequestTree root, List<IFilter> filters) {
        if (!pipe.isEnabled()) {
            return;
        }
        for (IFilter filter : filters) {
            if (filter.isBlocked() == filter.isFilteredItem(tree.getRequestType()) || filter.blockProvider()) {
                return;
            }
        }
        for (int i = 0; i < pipe.getChassiSize(); i++) {
            if (getModule(i) instanceof ILegacyActiveModule module) {
                module.canProvide(tree, root, filters);
            }
        }
    }

    public LogisticsOrder fullFill(LogisticsPromise promise, IRequestItems destination,
            IAdditionalTargetInformation info) {
        if (!pipe.isEnabled()) {
            return null;
        }
        for (int i = 0; i < pipe.getChassiSize(); i++) {
            if (getModule(i) instanceof ILegacyActiveModule module) {
                LogisticsOrder result = module.fullFill(promise, destination, info);
                if (result != null) {
                    pipe.spawnParticle(Particles.WhiteParticle, 2);
                    return result;
                }
            }
        }
        return null;
    }

    public void getAllItems(Map<ItemIdentifier, Integer> list, List<IFilter> filter) {
        if (!pipe.isEnabled()) {
            return;
        }
        for (int i = 0; i < pipe.getChassiSize(); i++) {
            if (getModule(i) instanceof ILegacyActiveModule module) {
                module.getAllItems(list, filter);
            }
        }
    }

    /*** ICraftItems ***/

    public void registerExtras(IPromise promise) {
        if (!(promise instanceof LogisticsPromise itemPromise)) {
            throw new UnsupportedOperationException("Extra has to be an item for a chassis pipe");
        }
        ItemIdentifierStack stack = new ItemIdentifierStack(itemPromise.item, itemPromise.numberOfItems);
        extras.add(new LogisticsItemOrder(new DictResource(stack, null), null, ResourceType.EXTRA, null));
    }

    public ICraftingTemplate addCrafting(IResource toCraft) {
        for (int i = 0; i < pipe.getChassiSize(); i++) {
            if (getModule(i) instanceof ICraftItems crafter && crafter.canCraft(toCraft)) {
                return crafter.addCrafting(toCraft);
            }
        }
        return null;

        // trixy code goes here to ensure the right crafter answers the right request
    }

    public List<ItemIdentifierStack> getConfiguredCraftResults() {
        List<ItemIdentifierStack> craftables = null;
        for (int i = 0; i < pipe.getChassiSize(); i++) {
            if (getModule(i) instanceof ICraftItems crafter) {
                if (craftables == null) {
                    craftables = new LinkedList<>();
                }
                craftables.addAll(crafter.getConfiguredCraftResults());
            }
        }
        return craftables;
    }

    public boolean canCraft(IResource toCraft) {
        for (int i = 0; i < pipe.getChassiSize(); i++) {
            if (getModule(i) instanceof ICraftItems crafter && crafter.canCraft(toCraft)) {
                return true;
            }
        }
        return false;
    }
}
