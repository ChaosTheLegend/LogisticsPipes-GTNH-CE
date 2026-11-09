package logisticspipes.modules;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Deprecated: a provider with speed upgrades replaces it. As an item it turns into a provider with 4 speed upgrades
 * (see {@link ModuleMigration}); it stays as the built-in module of the deprecated provider pipe Mk2, where its speed
 * is built in: speed upgrade items there would drop when the pipe is broken and could be had again and again.
 */
@Deprecated
public class ModuleProviderMk2 extends ModuleProvider {

    @Override
    public int getSpeed() {
        return MAX_SPEED;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconTexture(IIconRegister register) {
        return register.registerIcon("logisticspipes:itemModule/ModuleProviderMk2");
    }
}
