package logisticspipes.pipes;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;

public class PipeLogisticsFluidChassiMk3 extends PipeLogisticsFluidChassi {

    public PipeLogisticsFluidChassiMk3(Item item) {
        super(item);
    }

    @Override
    public TextureType getCenterTexture() {
        return Textures.LOGISTICSPIPE_FLUID_CHASSI3_TEXTURE;
    }

    @Override
    public int getChassiSize() {
        return 3;
    }

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            "logisticspipes",
            "textures/gui/chassipipe_size3.png");

    @Override
    public ResourceLocation getChassiGUITexture() {
        return PipeLogisticsFluidChassiMk3.TEXTURE;
    }
}
