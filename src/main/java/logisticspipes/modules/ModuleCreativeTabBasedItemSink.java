package logisticspipes.modules;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Deprecated: the tag sink in its CREATIVE_TAB mode replaces it. Kept for the name and icon of the old item until the
 * deletion version; a chassis turns it into a tag sink when it loads it, see {@code ModuleMigration}.
 */
@Deprecated
public class ModuleCreativeTabBasedItemSink extends ModuleTagItemSink {

    public ModuleCreativeTabBasedItemSink() {
        super(TagMode.CREATIVE_TAB);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconTexture(IIconRegister register) {
        return register.registerIcon("logisticspipes:itemModule/ModuleCreativeTabBasedItemSink");
    }
}
