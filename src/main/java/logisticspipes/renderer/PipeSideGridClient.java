package logisticspipes.renderer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.pipes.basic.PipeSideGrid;
import logisticspipes.pipes.basic.PipeSideGrid.SideState;

/**
 * Client side of {@link PipeSideGrid}: the grid drawn on the face of the logistics pipe the player looks at while in
 * the grid mode. Each cell is coloured by the side it stands for: green connected, red disconnected, yellow the working
 * side, none when there is nothing on that side.
 */
@SideOnly(Side.CLIENT)
public class PipeSideGridClient {

    // the grid floats a little in front of the face, so it isn't hidden in it
    private static final double LIFT = 0.002;
    private static final double[] CELL_EDGES = { 0, PipeSideGrid.EDGE, 1 - PipeSideGrid.EDGE, 1 };

    @SubscribeEvent
    public void onDrawBlockHighlight(DrawBlockHighlightEvent event) {
        MovingObjectPosition target = event.target;
        if (target == null || target.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return;
        }
        EntityPlayer player = event.player;
        TileEntity tile = player.worldObj.getTileEntity(target.blockX, target.blockY, target.blockZ);
        if (!(tile instanceof LogisticsTileGenericPipe pipeTile) || !(pipeTile.pipe instanceof CoreRoutedPipe pipe)
                || !PipeSideGrid.isGridMode(player, pipe)) {
            return;
        }
        double viewX = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.partialTicks;
        double viewY = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.partialTicks;
        double viewZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.partialTicks;
        ForgeDirection face = ForgeDirection.getOrientation(target.sideHit);

        GL11.glPushMatrix();
        GL11.glTranslated(target.blockX - viewX, target.blockY - viewY, target.blockZ - viewZ);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);

        // the cells, coloured by the side they stand for
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double u1 = CELL_EDGES[i];
                double u2 = CELL_EDGES[i + 1];
                double v1 = CELL_EDGES[j];
                double v2 = CELL_EDGES[j + 1];
                ForgeDirection side = sideOfCell(face, (u1 + u2) / 2, (v1 + v2) / 2);
                if (!setColor(pipe, side)) {
                    continue;
                }
                vertex(face, u1, v1);
                vertex(face, u2, v1);
                vertex(face, u2, v2);
                vertex(face, u1, v2);
            }
        }
        GL11.glEnd();

        // the lines of the grid
        GL11.glColor4f(0F, 0F, 0F, 0.6F);
        GL11.glLineWidth(2F);
        GL11.glBegin(GL11.GL_LINES);
        double low = PipeSideGrid.EDGE;
        double high = 1 - PipeSideGrid.EDGE;
        line(face, low, 0, low, 1);
        line(face, high, 0, high, 1);
        line(face, 0, low, 1, low);
        line(face, 0, high, 1, high);
        GL11.glEnd();

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    /**
     * @return false if the cell isn't coloured (nothing on that side)
     */
    private static boolean setColor(CoreRoutedPipe pipe, ForgeDirection side) {
        if (side == pipe.getWorkingSide() && pipe.hasWorkingSide()) {
            GL11.glColor4f(0.95F, 0.8F, 0.1F, 0.45F);
            return true;
        }
        SideState state = PipeSideGrid.stateOf(pipe, side);
        switch (state) {
            case CONNECTED -> GL11.glColor4f(0.1F, 0.8F, 0.1F, 0.3F);
            case DISCONNECTED -> GL11.glColor4f(0.85F, 0.1F, 0.1F, 0.4F);
            default -> {
                return false;
            }
        }
        return true;
    }

    // the side a point of the face stands for, by the rules of PipeSideGrid.sideFor
    private static ForgeDirection sideOfCell(ForgeDirection face, double u, double v) {
        return switch (face) {
            case DOWN, UP -> PipeSideGrid.sideFor(face.ordinal(), u, 0.5, v);
            case NORTH, SOUTH -> PipeSideGrid.sideFor(face.ordinal(), u, v, 0.5);
            case WEST, EAST -> PipeSideGrid.sideFor(face.ordinal(), 0.5, v, u);
            default -> ForgeDirection.UNKNOWN;
        };
    }

    // a line on the face from (u1, v1) to (u2, v2), in the plane coordinates PipeSideGrid.sideFor uses
    private static void line(ForgeDirection face, double u1, double v1, double u2, double v2) {
        vertex(face, u1, v1);
        vertex(face, u2, v2);
    }

    private static void vertex(ForgeDirection face, double u, double v) {
        switch (face) {
            case DOWN -> GL11.glVertex3d(u, -LIFT, v);
            case UP -> GL11.glVertex3d(u, 1 + LIFT, v);
            case NORTH -> GL11.glVertex3d(u, v, -LIFT);
            case SOUTH -> GL11.glVertex3d(u, v, 1 + LIFT);
            case WEST -> GL11.glVertex3d(-LIFT, v, u);
            case EAST -> GL11.glVertex3d(1 + LIFT, v, u);
            default -> {}
        }
    }
}
