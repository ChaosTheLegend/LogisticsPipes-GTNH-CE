package logisticspipes.modules;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Deprecated: an extractor with speed upgrades replaces it. Kept for the name and icon of the old item until the
 * deletion version; a chassis turns it into an extractor when it loads it, see {@code ModuleMigration}.
 */
@Deprecated
public class ModuleAdvancedExtractorMK2 extends ModuleExtractor {

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconTexture(IIconRegister register) {
        return register.registerIcon("logisticspipes:itemModule/ModuleAdvancedExtractorMK2");
    }
}
