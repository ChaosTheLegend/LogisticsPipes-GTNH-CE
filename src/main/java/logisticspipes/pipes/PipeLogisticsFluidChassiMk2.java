package logisticspipes.pipes;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;

public class PipeLogisticsFluidChassiMk2 extends PipeLogisticsFluidChassi {

    public PipeLogisticsFluidChassiMk2(Item item) {
        super(item);
    }

    @Override
    public TextureType getCenterTexture() {
        return Textures.LOGISTICSPIPE_FLUID_CHASSI2_TEXTURE;
    }

    @Override
    public int getChassiSize() {
        return 2;
    }

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            "logisticspipes",
            "textures/gui/chassipipe_size2.png");

    @Override
    public ResourceLocation getChassiGUITexture() {
        return PipeLogisticsFluidChassiMk2.TEXTURE;
    }
}
