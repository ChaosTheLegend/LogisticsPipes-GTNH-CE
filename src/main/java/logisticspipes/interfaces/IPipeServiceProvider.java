package logisticspipes.interfaces;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.api.IRoutedPowerProvider;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.logisticspipes.ExtractionMode;
import logisticspipes.logisticspipes.IInventoryProvider;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.pipes.basic.CoreRoutedPipe.ItemSendMode;
import logisticspipes.pipes.basic.debug.DebugLogController;
import logisticspipes.pipes.modulehost.ItemTarget;
import logisticspipes.utils.CacheHolder;

// methods needed by modules that any CRP can offer
public interface IPipeServiceProvider extends IRoutedPowerProvider, IInventoryProvider, ISpawnParticles {

    boolean isNthTick(int n);

    DebugLogController getDebug();

    CacheHolder getCacheHolder();

    /**
     * @return the inventories the modules of this pipe work on, one per target side (a chassis: the one it points at, a
     *         single-module pipe: every side), seen through the extraction mode
     */
    List<ItemTarget> getItemTargets(ExtractionMode mode, boolean forExtraction);

    /**
     * @param face the face of each inventory to work on, {@link ForgeDirection#UNKNOWN} for the one facing the pipe
     */
    List<ItemTarget> getItemTargets(ExtractionMode mode, boolean forExtraction, ForgeDirection face);

    /**
     * Sends a stack to that destination, entering the pipe from that side.
     */
    IRoutedItem sendStack(ItemStack stack, int destination, ItemSendMode mode, IAdditionalTargetInformation info,
            ForgeDirection from);
}
