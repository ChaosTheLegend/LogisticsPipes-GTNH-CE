package logisticspipes.modules;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Deprecated: an item sink with the terminus priority replaces it. Kept for the name and icon of the old item until the
 * deletion version; a chassis turns it into an item sink when it loads it, see {@code ModuleMigration}.
 */
@Deprecated
public class ModuleTerminus extends ModuleItemSink {

    public ModuleTerminus() {
        super(true);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconTexture(IIconRegister register) {
        return register.registerIcon("logisticspipes:itemModule/ModuleTerminus");
    }
}
