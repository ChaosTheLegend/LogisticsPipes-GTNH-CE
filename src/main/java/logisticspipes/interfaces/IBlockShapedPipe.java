package logisticspipes.interfaces;

import net.minecraft.util.IIcon;

import logisticspipes.textures.Textures;

/**
 * A pipe that stands in the world as a whole block, like the request table: it is drawn, hit and collided with as a
 * full cube, and its item is drawn as one.
 */
public interface IBlockShapedPipe extends IRotationProvider {

    /**
     * @return the textures of the block without the new renderer: the top, the bottom, a side with a routed pipe at
     *         it, a side with another pipe at it, a side with nothing at it
     */
    default IIcon[] getBlockTextures() {
        return Textures.LOGISTICS_REQUEST_TABLE;
    }

    /**
     * @return the texture the block model of the new renderer is drawn with
     */
    default IIcon getBlockModelTexture() {
        return Textures.LOGISTICS_REQUEST_TABLE_NEW;
    }
}
