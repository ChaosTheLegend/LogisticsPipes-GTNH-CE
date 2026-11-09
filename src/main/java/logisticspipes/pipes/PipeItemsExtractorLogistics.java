package logisticspipes.pipes;

import net.minecraft.item.Item;

import logisticspipes.api.IMUICompatibleModule;
import logisticspipes.api.IMUICompatiblePipeV2;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.PipeGuiFactory;
import logisticspipes.items.ItemModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;

/**
 * Pulls items out of every inventory next to it and sends them to whatever takes them, like the fluid extractor pipe
 * does with tanks: each inventory gets a full extraction. Its built-in extractor module, the same one that goes into a
 * chassis, does the work; filter and speed upgrades are in the pipe's window. A side disconnected with shift + wrench
 * is left alone.
 */
public class PipeItemsExtractorLogistics extends CoreRoutedPipe implements IMUICompatiblePipeV2 {

    public PipeItemsExtractorLogistics(Item item) {
        super(item);
        hostModule(ItemModule.EXTRACTOR);
    }

    @Override
    public LogisticsModule getLogisticsModule() {
        return hostedModule();
    }

    @Override
    public ItemSendMode getItemSendMode() {
        return ItemSendMode.Normal;
    }

    @Override
    public TextureType getCenterTexture() {
        return Textures.LOGISTICSPIPE_EXTRACTOR_TEXTURE;
    }

    @Override
    public LogisticsModularUI getPipeGui() {
        return PipeGuiFactory.fromModule(this, (IMUICompatibleModule) hostedModule());
    }
}
