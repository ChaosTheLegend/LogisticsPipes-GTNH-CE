package logisticspipes.crafting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.IFluidHandler;

import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.AdjacentTile;
import logisticspipes.utils.WorldUtil;

/**
 * Maintains the adjacent inventory or fluid handler selected as the pattern crafting target.
 * <p>
 * The pipe still owns connection refreshes and rendering, while this helper owns target discovery, caching, cycling,
 * and persistence of the selected side.
 */
public class PatternCraftingTargetSelector {

    private static final String CONNECTED_INVENTORY_DIRECTION_TAG = "patternConnectedInventoryDirection";

    private final PipeItemsPatternCraftingLogistics pipe;
    private ForgeDirection connectedInventoryDirection = ForgeDirection.UNKNOWN;
    private AdjacentTile cachedConnectedInventory;

    /**
     * Creates a selector bound to the owning pattern pipe.
     */
    public PatternCraftingTargetSelector(PipeItemsPatternCraftingLogistics pipe) {
        this.pipe = pipe;
    }

    /**
     * Returns the selected adjacent crafting target, resolving and caching it when needed.
     */
    public AdjacentTile getConnectedInventoryTile() {
        if (isCachedConnectedInventoryValid()) {
            return cachedConnectedInventory;
        }
        cachedConnectedInventory = resolveConnectedInventoryTile();
        return cachedConnectedInventory;
    }

    /**
     * Invalidates the cached target after world or pipe connection changes.
     */
    public void clearCache() {
        cachedConnectedInventory = null;
    }

    /**
     * Checks whether a neighboring tile is the selected crafting target side.
     */
    public boolean isSelectedInventory(TileEntity tile, ForgeDirection direction) {
        AdjacentTile selected = getConnectedInventoryTile();
        return selected != null && selected.tile == tile
                && (selected.orientation == direction || selected.orientation == getDirectionTo(tile));
    }

    /**
     * Advances the selected target to the next adjacent inventory or fluid handler and reports the choice to the
     * player.
     */
    public void cycleConnectedInventory(EntityPlayer player) {
        List<AdjacentTile> inventories = getSelectableAdjacentInventories();
        if (inventories.isEmpty()) {
            connectedInventoryDirection = ForgeDirection.UNKNOWN;
            cachedConnectedInventory = null;
            pipe.refreshSelectedInventoryConnection();
            player.addChatComponentMessage(new ChatComponentText("Pattern crafting target: none"));
            return;
        }
        int current = -1;
        for (int i = 0; i < inventories.size(); i++) {
            if (inventories.get(i).orientation == connectedInventoryDirection) {
                current = i;
                break;
            }
        }
        AdjacentTile selected = inventories.get((current + 1) % inventories.size());
        connectedInventoryDirection = selected.orientation;
        cachedConnectedInventory = selected;
        pipe.refreshSelectedInventoryConnection();
        player.addChatComponentMessage(
                new ChatComponentText(
                        "Pattern crafting target: " + connectedInventoryDirection.name().toLowerCase(Locale.ENGLISH)));
    }

    /**
     * Saves the selected target side to world data.
     */
    public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger(CONNECTED_INVENTORY_DIRECTION_TAG, connectedInventoryDirection.ordinal());
    }

    /**
     * Restores the selected target side from world data.
     */
    public void readFromNBT(NBTTagCompound tag) {
        connectedInventoryDirection = tag.hasKey(CONNECTED_INVENTORY_DIRECTION_TAG)
                ? directionFromOrdinal(tag.getInteger(CONNECTED_INVENTORY_DIRECTION_TAG))
                : ForgeDirection.UNKNOWN;
        cachedConnectedInventory = null;
    }

    /**
     * Writes the selected target side to the client synchronization stream.
     */
    public void writeData(LPDataOutputStream data) throws IOException {
        data.writeInt(connectedInventoryDirection.ordinal());
    }

    /**
     * Reads the selected target side from the client synchronization stream.
     */
    public void readData(LPDataInputStream data) throws IOException {
        connectedInventoryDirection = directionFromOrdinal(data.readInt());
        cachedConnectedInventory = null;
    }

    /**
     * Checks that the cached target still exists on the selected side and can still be used by pattern crafting.
     */
    private boolean isCachedConnectedInventoryValid() {
        return cachedConnectedInventory != null && cachedConnectedInventory.orientation == connectedInventoryDirection
                && cachedConnectedInventory.tile != null
                && !cachedConnectedInventory.tile.isInvalid()
                && getAdjacentTile(cachedConnectedInventory.orientation) == cachedConnectedInventory.tile
                && isSelectableInventory(cachedConnectedInventory.tile, cachedConnectedInventory.orientation);
    }

    /**
     * Resolves the selected target side, auto-selecting the first usable adjacent target for old pipe data.
     */
    private AdjacentTile resolveConnectedInventoryTile() {
        AdjacentTile selected = getSelectableAdjacentInventory(connectedInventoryDirection);
        if (selected != null) {
            return selected;
        }
        if (connectedInventoryDirection != ForgeDirection.UNKNOWN) {
            return null;
        }
        List<AdjacentTile> inventories = getSelectableAdjacentInventories();
        if (inventories.isEmpty()) {
            return null;
        }
        selected = inventories.get(0);
        connectedInventoryDirection = selected.orientation;
        return selected;
    }

    /**
     * Lists every adjacent side that can be used as a direct pattern crafting target.
     */
    private List<AdjacentTile> getSelectableAdjacentInventories() {
        List<AdjacentTile> inventories = new ArrayList<>();
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            AdjacentTile tile = getSelectableAdjacentInventory(direction);
            if (tile != null) {
                inventories.add(tile);
            }
        }
        return inventories;
    }

    /**
     * Returns the target tile on one side when it is usable for item or fluid crafting output.
     */
    private AdjacentTile getSelectableAdjacentInventory(ForgeDirection direction) {
        if (direction == null || direction == ForgeDirection.UNKNOWN) {
            return null;
        }
        TileEntity tile = getAdjacentTile(direction);
        if (!isSelectableInventory(tile, direction)) {
            return null;
        }
        return new AdjacentTile(tile, direction);
    }

    /**
     * Looks up the neighboring tile on one side of the owning pipe.
     */
    private TileEntity getAdjacentTile(ForgeDirection direction) {
        if (direction == null || direction == ForgeDirection.UNKNOWN) {
            return null;
        }
        return new WorldUtil(pipe.getWorld(), pipe.getX(), pipe.getY(), pipe.getZ()).getAdjacentTileEntitie(direction);
    }

    /**
     * Finds the side currently occupied by a neighboring tile.
     */
    private ForgeDirection getDirectionTo(TileEntity tile) {
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            if (getAdjacentTile(direction) == tile) {
                return direction;
            }
        }
        return ForgeDirection.UNKNOWN;
    }

    /**
     * Checks whether a neighboring tile is a non-pipe inventory or tank that the pattern pipe may use as crafting
     * target.
     * <p>
     * This deliberately does not require a pipe transport connection. Pattern crafting inserts and extracts through
     * direct inventory/fluid handlers, while the transport connection to inventories and tanks stays closed so other
     * blocks cannot push untracked items into the pipe.
     */
    private boolean isSelectableInventory(TileEntity tile, ForgeDirection direction) {
        boolean hasInventory = tile instanceof IInventory && ((IInventory) tile).getSizeInventory() > 0;
        boolean hasTank = tile instanceof IFluidHandler
                && ((IFluidHandler) tile).getTankInfo(direction.getOpposite()) != null
                && ((IFluidHandler) tile).getTankInfo(direction.getOpposite()).length > 0;
        return (hasInventory || hasTank) && !SimpleServiceLocator.pipeInformationManager.isPipe(tile, false)
                && !pipe.isSideBlocked(direction, false)
                && pipe.transport.canPipeConnect(tile, direction);
    }

    /**
     * Converts persisted side ordinals back to Forge directions, defaulting to unknown for corrupt data.
     */
    private ForgeDirection directionFromOrdinal(int ordinal) {
        ForgeDirection[] values = ForgeDirection.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return ForgeDirection.UNKNOWN;
        }
        return values[ordinal];
    }
}
