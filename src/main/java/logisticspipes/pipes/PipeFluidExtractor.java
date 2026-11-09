package logisticspipes.pipes;

import net.minecraft.item.Item;

import logisticspipes.api.IMUICompatibleModule;
import logisticspipes.api.IMUICompatiblePipeV2;
import logisticspipes.gui.modularUI.LogisticsModularUI;
import logisticspipes.gui.modularUI.PipeGuiFactory;
import logisticspipes.items.ItemModule;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;

/**
 * Drains the tanks on every side and sends the fluid to the sinks in the network. The draining is done by its built-in
 * fluid extractor module, the same one that goes into a chassis; fluid other pipes push into it is routed as by the
 * insertion pipe.
 * <p>
 * Pipes saved before the module did the draining have "liquidToExtract" (a counter for the side tanks); it is ignored.
 * Fluid still in the side tanks is sent on as before.
 */
public class PipeFluidExtractor extends PipeFluidInsertion implements IMUICompatiblePipeV2 {

    public PipeFluidExtractor(Item item) {
        super(item);
        hostModule(ItemModule.FLUID_EXTRACTOR);
    }

    @Override
    public TextureType getCenterTexture() {
        return Textures.LOGISTICSPIPE_LIQUID_EXTRACTOR;
    }

    /**
     * The window of the built-in module, with its upgrades and the settings (sides, priority).
     */
    @Override
    public LogisticsModularUI getPipeGui() {
        return PipeGuiFactory.fromModule(this, (IMUICompatibleModule) getLogisticsModule());
    }
}
