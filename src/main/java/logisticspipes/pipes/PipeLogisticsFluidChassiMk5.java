package logisticspipes.pipes;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;

public class PipeLogisticsFluidChassiMk5 extends PipeLogisticsFluidChassi {

    public PipeLogisticsFluidChassiMk5(Item item) {
        super(item);
    }

    @Override
    public TextureType getCenterTexture() {
        return Textures.LOGISTICSPIPE_FLUID_CHASSI5_TEXTURE;
    }

    @Override
    public int getChassiSize() {
        return 8;
    }

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            "logisticspipes",
            "textures/gui/chassipipe_size8.png");

    @Override
    public ResourceLocation getChassiGUITexture() {
        return PipeLogisticsFluidChassiMk5.TEXTURE;
    }
}
