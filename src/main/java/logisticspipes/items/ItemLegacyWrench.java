package logisticspipes.items;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;

import buildcraft.api.tools.IToolWrench;

public class ItemLegacyWrench extends LogisticsItem implements IToolWrench {

    @Override
    public int getItemStackLimit() {
        return 1;
    }

    @Override
    public CreativeTabs getCreativeTab() {
        return CreativeTabs.tabTools;
    }

    @Override
    public boolean canWrench(EntityPlayer player, int x, int y, int z) {
        return true;
    }

    @Override
    public void wrenchUsed(EntityPlayer player, int x, int y, int z) {

    }
}
