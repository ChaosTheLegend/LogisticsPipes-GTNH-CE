package logisticspipes.modules;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Deprecated: the enchantment sink with a filter replaces it. Kept for the name and icon of the old item until the
 * deletion version; a chassis turns it into an enchantment sink when it loads it, see {@code ModuleMigration}.
 */
@Deprecated
public class ModuleEnchantmentSinkMK2 extends ModuleEnchantmentSink {

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconTexture(IIconRegister register) {
        return register.registerIcon("logisticspipes:itemModule/ModuleEnchantmentSinkMK2");
    }
}
