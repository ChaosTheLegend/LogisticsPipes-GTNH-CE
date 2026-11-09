package logisticspipes.pipes.modulehost;

import java.util.List;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.interfaces.IInventoryUtil;
import logisticspipes.interfaces.ISlotUpgradeManager;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.modules.abstractmodules.LogisticsModule.ModulePositionType;
import logisticspipes.pipefxhandlers.Particles;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.routing.IRouter;
import logisticspipes.utils.CacheHolder;

/**
 * What a {@link ModuleHost} and its resource managers need from the pipe the host sits in: the chassis and the
 * single-module pipes.
 */
public interface IModuleHostPipe {

    /**
     * Called by the host after its modules changed to match the module items (also while reading NBT).
     *
     * @param moduleRemoved true if a module was removed, so an open GUI has to be rebuilt
     */
    void onHostModulesChanged(boolean moduleRemoved);

    boolean isEnabled();

    /**
     * @return the tile entity that holds the pipe, null before it is placed
     */
    LogisticsTileGenericPipe getContainer();

    ForgeDirection getPointedOrientation();

    /**
     * @return the sides of the pipe whose blocks the modules work on, in the order they are tried: the chassis gives
     *         the side it points at, a single-module pipe every side. Empty if there is none
     */
    List<ForgeDirection> getTargetDirections();

    IInventory getRealInventory();

    IInventoryUtil getSneakyInventory(boolean forExtraction, ModulePositionType slot, int positionInt);

    ISlotUpgradeManager getUpgradeManager(ModulePositionType slot, int positionInt);

    IRouter getRouter();

    CacheHolder getCacheHolder();

    boolean isNthTick(int n);

    /**
     * Sends a stack into the network from this pipe, to whatever takes it.
     */
    void sendItem(ItemStack stack);

    void queueRoutedItem(IRoutedItem routedItem, ForgeDirection from);

    void spawnParticle(Particles particle, int amount);

    void refreshRender(boolean spawnPart);
}
