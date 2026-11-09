package logisticspipes.items;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

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

    // a sneak click reaches the block, as with the BuildCraft and GregTech wrenches: shift + wrench on a pipe connects
    // and disconnects its sides
    @Override
    public boolean doesSneakBypassUse(World world, int x, int y, int z, EntityPlayer player) {
        return true;
    }
}
