package logisticspipes.modules;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Deprecated: the tag sink in its ORE_DICT mode replaces it. Kept for the name and icon of the old item until the
 * deletion version; a chassis turns it into a tag sink when it loads it, see {@code ModuleMigration}.
 */
@Deprecated
public class ModuleOreDictItemSink extends ModuleTagItemSink {

    public ModuleOreDictItemSink() {
        super(TagMode.ORE_DICT);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconTexture(IIconRegister register) {
        return register.registerIcon("logisticspipes:itemModule/ModuleOreDictItemSink");
    }
}
