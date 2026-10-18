package logisticspipes.pipes.chassis;

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
 * One kind of target a chassis works on, like the inventory it points at for item modules. The chassis asks its sides
 * what concerns all of them, the things only one kind of module needs stay on the side itself.
 */
public interface ChassisSide {

    /**
     * @return true if the module belongs to this side
     */
    boolean ownsModule(LogisticsModule module);

    /**
     * @return true if the chassis may point at that tile for this side
     */
    boolean isValidTarget(TileEntity tile, ForgeDirection dir);

    /**
     * @return true if the chassis points at something this side works on, the modules of the side only count then
     */
    boolean isActive();

    /**
     * Adds the specific interests of the modules of this side. Only called while the side is active.
     */
    void addSpecificInterests(Set<ItemIdentifier> interests);

    /**
     * Only called while the side is active.
     */
    boolean hasGenericInterests();

    /**
     * Only called while the side is active.
     */
    CategoryInterests getCategoryInterests();

    /**
     * Asked for an item that was sent to the chassis without naming a module, like after a world reload.
     *
     * @return true if this side wants it
     */
    boolean wantsArrival(ItemIdentifierStack stack);

    /**
     * Takes an item that reached the end of the chassis and is for this side.
     *
     * @return true if the item is handled, false lets it go into the inventory the chassis points at
     */
    boolean takeArrival(LPTravelingItemServer arrivingItem);

    /**
     * Called on the server every tick the chassis is enabled.
     */
    default void tick() {}

    /**
     * Called after a module was put in, taken out or replaced.
     */
    default void onModulesChanged() {}

    /**
     * Called on the server before the chassis is removed.
     */
    default void onRemoved() {}

    default void readFromNBT(NBTTagCompound nbttagcompound) {}

    default void writeToNBT(NBTTagCompound nbttagcompound) {}
}
