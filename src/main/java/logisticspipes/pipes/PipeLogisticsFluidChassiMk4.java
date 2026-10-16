package logisticspipes.pipes;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;

public class PipeLogisticsFluidChassiMk4 extends PipeLogisticsFluidChassi {

    public PipeLogisticsFluidChassiMk4(Item item) {
        super(item);
    }

    @Override
    public TextureType getCenterTexture() {
        return Textures.LOGISTICSPIPE_FLUID_CHASSI4_TEXTURE;
    }

    @Override
    public int getChassiSize() {
        return 4;
    }

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            "logisticspipes",
            "textures/gui/chassipipe_size4.png");

    @Override
    public ResourceLocation getChassiGUITexture() {
        return PipeLogisticsFluidChassiMk4.TEXTURE;
    }
}
