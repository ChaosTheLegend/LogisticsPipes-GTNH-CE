package logisticspipes.pipes;

import net.minecraft.item.Item;

import logisticspipes.api.IMUICompatibleModule;
import logisticspipes.api.IMUICompatiblePipeV2;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.PipeGuiFactory;
import logisticspipes.items.ItemModule;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.routing.astar.CategoryInterests;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;

/**
 * Takes bees by their genome. Its built-in apiarist sink module, the same one that goes into a chassis, decides.
 */
public class PipeItemsApiaristSink extends CoreRoutedPipe implements IMUICompatiblePipeV2 {

    public PipeItemsApiaristSink(Item item) {
        super(item);
        hostModule(ItemModule.BEESINK);
    }

    @Override
    public TextureType getCenterTexture() {
        return Textures.LOGISTICSPIPE_APIARIST_SINK_TEXTURE;
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
    public boolean hasGenericInterests() {
        return false;
    }

    @Override
    public CategoryInterests getCategoryInterests() {
        LogisticsModule module = hostedModule();
        return module == null ? CategoryInterests.EMPTY : module.getCategoryInterests();
    }

    /**
     * The window of its built-in module, with the module's upgrades and the settings window.
     */
    @Override
    public LogisticsModularUI getPipeGui() {
        return PipeGuiFactory.fromModule(this, (IMUICompatibleModule) hostedModule());
    }
}
