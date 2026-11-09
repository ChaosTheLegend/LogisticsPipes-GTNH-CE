package logisticspipes.pipes.basic;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.oredict.OreDictionary;

import logisticspipes.proxy.SimpleServiceLocator;

/**
 * The 3x3 grid on the face of a pipe that shift + wrench uses to connect and disconnect sides, as on GregTech pipes:
 * the middle stands for the face itself, an edge for the side next to it in that direction, a corner for the opposite
 * side. A screwdriver uses the same grid to pick the working side of a pipe that has one (a chassis, a pattern crafting
 * pipe). While a player is in either mode, a logistics pipe is a full block for clicks, so the whole face is the grid.
 */
public final class PipeSideGrid {

    // the edge band of the grid, from each border of the face
    public static final double EDGE = 0.25;

    private static final String SCREWDRIVER = "craftingToolScrewdriver";

    // the ore id of SCREWDRIVER, resolved on first use (-1 until then)
    private static int screwdriverOreId = -1;

    private PipeSideGrid() {}

    /**
     * @return true if the player is in the side mode: a wrench in hand and sneaking
     */
    public static boolean isSideMode(EntityPlayer player) {
        return player != null && player.isSneaking() && SimpleServiceLocator.toolWrenchHandler.isWrenchEquipped(player);
    }

    /**
     * @return true if the player holds a screwdriver (any item of the ore name craftingToolScrewdriver, as GregTech's)
     */
    public static boolean isScrewdriver(EntityPlayer player) {
        ItemStack held = player == null ? null : player.getCurrentEquippedItem();
        if (held == null || held.getItem() == null) {
            return false;
        }
        if (screwdriverOreId < 0) {
            screwdriverOreId = OreDictionary.getOreID(SCREWDRIVER);
        }
        for (int id : OreDictionary.getOreIDs(held)) {
            if (id == screwdriverOreId) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true if the grid is active for the player at that pipe: shift + wrench (sides), or a screwdriver at a
     *         pipe with a working side
     */
    public static boolean isGridMode(EntityPlayer player, CoreRoutedPipe pipe) {
        return isSideMode(player) || (pipe.hasWorkingSide() && isScrewdriver(player));
    }

    /**
     * How a side of a pipe stands, for the grid colours and the settings window.
     */
    public enum SideState {
        // nothing there to connect to
        NONE,
        CONNECTED,
        // the player disconnected it
        DISCONNECTED
    }

    public static SideState stateOf(CoreRoutedPipe pipe, ForgeDirection side) {
        if (pipe.isSideDisconnected(side)) {
            return SideState.DISCONNECTED;
        }
        if (pipe.container == null || pipe.getWorld() == null) {
            return SideState.NONE;
        }
        // the connection the pipe made there (the client has it from the render state), not just any tile entity
        return pipe.container.isPipeConnected(side) ? SideState.CONNECTED : SideState.NONE;
    }

    /**
     * @param face the face that was clicked (0-5, {@link ForgeDirection} order)
     * @param hitX where on the block, 0-1
     * @param hitY where on the block, 0-1
     * @param hitZ where on the block, 0-1
     * @return the side of the pipe the click stands for
     */
    public static ForgeDirection sideFor(int face, double hitX, double hitY, double hitZ) {
        ForgeDirection clicked = ForgeDirection.getOrientation(face);
        // the two coordinates in the plane of the face, and the sides they point to
        double u;
        double v;
        ForgeDirection uLow;
        ForgeDirection uHigh;
        ForgeDirection vLow;
        ForgeDirection vHigh;
        switch (clicked) {
            case DOWN, UP -> {
                u = hitX;
                v = hitZ;
                uLow = ForgeDirection.WEST;
                uHigh = ForgeDirection.EAST;
                vLow = ForgeDirection.NORTH;
                vHigh = ForgeDirection.SOUTH;
            }
            case NORTH, SOUTH -> {
                u = hitX;
                v = hitY;
                uLow = ForgeDirection.WEST;
                uHigh = ForgeDirection.EAST;
                vLow = ForgeDirection.DOWN;
                vHigh = ForgeDirection.UP;
            }
            case WEST, EAST -> {
                u = hitZ;
                v = hitY;
                uLow = ForgeDirection.NORTH;
                uHigh = ForgeDirection.SOUTH;
                vLow = ForgeDirection.DOWN;
                vHigh = ForgeDirection.UP;
            }
            default -> {
                return ForgeDirection.UNKNOWN;
            }
        }
        ForgeDirection alongU = u < EDGE ? uLow : u > 1 - EDGE ? uHigh : null;
        ForgeDirection alongV = v < EDGE ? vLow : v > 1 - EDGE ? vHigh : null;
        if (alongU != null && alongV != null) {
            return clicked.getOpposite();
        }
        if (alongU != null) {
            return alongU;
        }
        if (alongV != null) {
            return alongV;
        }
        return clicked;
    }
}
