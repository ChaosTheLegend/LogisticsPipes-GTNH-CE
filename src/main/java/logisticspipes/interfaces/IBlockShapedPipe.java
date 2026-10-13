package logisticspipes.interfaces;

import net.minecraft.util.IIcon;

/**
 * A pipe that stands in the world as a whole block, like the request table: it is drawn, hit and collided with as a
 * full cube, and its item is drawn as one.
 */
public interface IBlockShapedPipe extends IRotationProvider {

    /**
     * @return the textures of the block without the new renderer
     */
    BlockTextures getBlockTextures();

    /**
     * @return the texture the block model of the new renderer is drawn with
     */
    IIcon getBlockModelTexture();

    /**
     * The textures of a block shaped pipe, as they are drawn without the new renderer.
     */
    final class BlockTextures {

        public final IIcon top;
        public final IIcon bottom;
        /** a side with a routed pipe at it */
        public final IIcon routedSide;
        /** a side with another pipe at it */
        public final IIcon unroutedSide;
        /** a side with nothing at it */
        public final IIcon emptySide;

        public BlockTextures(IIcon top, IIcon bottom, IIcon routedSide, IIcon unroutedSide, IIcon emptySide) {
            this.top = top;
            this.bottom = bottom;
            this.routedSide = routedSide;
            this.unroutedSide = unroutedSide;
            this.emptySide = emptySide;
        }

        /**
         * @param icons the five textures in the order of the fields
         */
        public static BlockTextures of(IIcon[] icons) {
            return new BlockTextures(icons[0], icons[1], icons[2], icons[3], icons[4]);
        }
    }
}
