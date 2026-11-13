package logisticspipes.pipes.modulehost;

import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.routing.astar.CategoryInterests;
import logisticspipes.transport.LPTravelingItem.LPTravelingItemServer;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * One kind of target the modules of a module host pipe work on, like the inventory next to the pipe for item modules.
 * The host asks its managers what concerns all of them, the things only one kind of module needs stay on the manager
 * itself.
 */
public interface IHostResourceManager {

    /**
     * @return true if the module belongs to this manager
     */
    boolean ownsModule(LogisticsModule module);

    /**
     * @return true if the pipe may work on that tile for this manager
     */
    boolean isValidTarget(TileEntity tile, ForgeDirection dir);

    /**
     * @return true if the pipe has a target this manager works on, the modules of the manager only count then
     */
    boolean isActive();

    /**
     * Adds the specific interests of the modules of this manager. Only called while the manager is active.
     */
    void addSpecificInterests(Set<ItemIdentifier> interests);

    /**
     * Only called while the manager is active.
     */
    boolean hasGenericInterests();

    /**
     * Only called while the manager is active.
     */
    CategoryInterests getCategoryInterests();

    /**
     * Asked for an item that was sent to the pipe without naming a module, like after a world reload.
     *
     * @return true if this manager wants it
     */
    boolean wantsArrival(ItemIdentifierStack stack);

    /**
     * Takes an item that reached the end of the pipe and is for this manager.
     *
     * @return true if the item is handled, false lets it go into the inventory next to the pipe
     */
    boolean takeArrival(LPTravelingItemServer arrivingItem);

    /**
     * Called on the server every tick the pipe is enabled.
     */
    default void tick() {}

    /**
     * Called after a module was put in, taken out or replaced.
     */
    default void onModulesChanged() {}

    /**
     * Called on the server before the pipe is removed.
     */
    default void onRemoved() {}

    default void readFromNBT(NBTTagCompound nbttagcompound) {}

    default void writeToNBT(NBTTagCompound nbttagcompound) {}
}
