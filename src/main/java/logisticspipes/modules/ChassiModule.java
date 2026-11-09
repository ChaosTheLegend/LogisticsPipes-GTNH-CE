package logisticspipes.modules;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;

import logisticspipes.interfaces.IInventoryUtil;
import logisticspipes.interfaces.IInventorylessSink;
import logisticspipes.interfaces.IModuleInventory;
import logisticspipes.interfaces.IModuleInventoryOverride;
import logisticspipes.interfaces.IPipeServiceProvider;
import logisticspipes.interfaces.IWorldProvider;
import logisticspipes.modules.abstractmodules.LogisticsGuiModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.network.NewGuiHandler;
import logisticspipes.network.abstractguis.ModuleCoordinatesGuiProvider;
import logisticspipes.network.abstractguis.ModuleInHandGuiProvider;
import logisticspipes.network.guis.pipe.ChassiGuiProvider;
import logisticspipes.pipes.PipeLogisticsChassi;
import logisticspipes.pipes.modulehost.ModuleHost;
import logisticspipes.pipes.modulehost.ModuleSlotTargetInformation;
import logisticspipes.proxy.computers.objects.CCSinkResponder;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

public class ChassiModule extends LogisticsGuiModule {

    private final PipeLogisticsChassi _parentPipe;
    private final ModuleHost host;

    public ChassiModule(PipeLogisticsChassi parentPipe, ModuleHost host) {
        _parentPipe = parentPipe;
        this.host = host;
        _service = parentPipe;
        registerPosition(ModulePositionType.IN_PIPE, 0);
    }

    public void removeModule(int slot) {
        host.removeModule(slot);
    }

    public LogisticsModule getModule(int slot) {
        return host.getModule(slot);
    }

    public boolean hasModule(int slot) {
        return host.hasModule(slot);
    }

    @Override
    public SinkReply sinksItem(ItemIdentifier item, int bestPriority, int bestCustomPriority, boolean allowDefault,
            boolean includeInTransit) {
        SinkReply bestresult = null;
        for (int i = 0; i < host.getSlotCount(); i++) {
            LogisticsModule module = host.getModule(i);
            if (module != null) {
                SinkReply result = module
                        .sinksItem(item, bestPriority, bestCustomPriority, allowDefault, includeInTransit);
                if (result != null && result.maxNumberOfItems >= 0) {
                    bestresult = result;
                    bestPriority = result.fixedPriority.ordinal();
                    bestCustomPriority = result.customPriority;
                }
            }
        }

        if (bestresult == null) {
            return null;
        }
        // Always deny items when we can't put the item anywhere
        int moduleSlot = ((ModuleSlotTargetInformation) bestresult.addInfo).getModuleSlot();
        if (host.getModule(moduleSlot) instanceof IInventorylessSink) {
            // the module takes the item itself and answered for its room already
            return bestresult;
        }
        IInventoryUtil invUtil = getModuleInventory(moduleSlot);
        if (invUtil == null) {
            invUtil = _parentPipe.getSneakyInventory(false, ModulePositionType.SLOT, moduleSlot);
        }
        if (invUtil == null) {
            return null;
        }
        int roomForItem = invUtil.roomForItem(item);
        if (roomForItem < 1) {
            return null;
        }
        if (includeInTransit) {
            int onRoute = _parentPipe.countOnRoute(item);
            roomForItem = invUtil.roomForItem(item, onRoute + item.getMaxStackSize());
            roomForItem -= onRoute;
            if (roomForItem < 1) {
                return null;
            }
        }

        if (bestresult.maxNumberOfItems == 0) {
            return new SinkReply(bestresult, roomForItem);
        }
        return new SinkReply(bestresult, Math.min(bestresult.maxNumberOfItems, roomForItem));
    }

    /**
     * @return the module's own inventory view (e.g. GT battery slots for the electric manager), or null if the module
     *         in that slot uses the pipe's normal inventory
     */
    public IModuleInventory getModuleInventory(int moduleSlot) {
        LogisticsModule module = getSubModule(moduleSlot);
        return module instanceof IModuleInventoryOverride override ? override.getModuleInventory() : null;
    }

    @Override
    public void insertionFailed(ItemIdentifier item) {
        for (int i = 0; i < host.getSlotCount(); i++) {
            LogisticsModule module = host.getModule(i);
            if (module != null) {
                module.insertionFailed(item);
            }
        }
    }

    @Override
    public LogisticsModule getSubModule(int slot) {
        return host.getModule(slot);
    }

    // the module host saves and loads the modules of the slots (same "slot<i>" keys as before)
    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {}

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {}

    @Override
    public void tick() {
        host.tickModules();
    }

    @Override
    public void registerHandler(IWorldProvider world, IPipeServiceProvider service) {
        // Not used in Chassie Module
    }

    @Override
    public boolean hasGenericInterests() {
        return false;
    }

    @Override
    public List<ItemIdentifier> getSpecificInterests() {
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
        for (int i = 0; i < host.getSlotCount(); i++) {
            LogisticsModule module = host.getModule(i);
            if (module != null && module.recievePassive()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<CCSinkResponder> queueCCSinkEvent(ItemIdentifierStack item) {
        List<CCSinkResponder> list = new ArrayList<>();
        for (int i = 0; i < host.getSlotCount(); i++) {
            LogisticsModule module = host.getModule(i);
            if (module != null) {
                list.addAll(module.queueCCSinkEvent(item));
            }
        }
        return list;
    }

    @Override
    public IIcon getIconTexture(IIconRegister register) {
        // Not Needed
        return null;
    }

    @Override
    protected ModuleCoordinatesGuiProvider getPipeGuiProvider() {
        return NewGuiHandler.getGui(ChassiGuiProvider.class);
    }

    @Override
    protected ModuleInHandGuiProvider getInHandGuiProvider() {
        return null;
    }
}
