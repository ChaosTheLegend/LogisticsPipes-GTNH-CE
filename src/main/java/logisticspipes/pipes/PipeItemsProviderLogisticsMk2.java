package logisticspipes.pipes;

import net.minecraft.item.Item;

import logisticspipes.items.ItemModule;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;

/**
 * The faster provider pipe: its built-in module is the provider Mk2 (more items per send, sent fast).
 */
public class PipeItemsProviderLogisticsMk2 extends PipeItemsProviderLogistics {

    public PipeItemsProviderLogisticsMk2(Item item) {
        super(item);
    }

    @Override
    protected int providerModuleId() {
        return ItemModule.PROVIDER_MK2;
    }

    @Override
    public TextureType getCenterTexture() {
        return Textures.LOGISTICSPIPE_PROVIDERMK2_TEXTURE;
    }

    @Override
    public ItemSendMode getItemSendMode() {
        return ItemSendMode.Fast;
    }
}
