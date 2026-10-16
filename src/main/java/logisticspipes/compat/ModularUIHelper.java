package logisticspipes.compat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.factory.GuiFactories;

import logisticspipes.LogisticsPipes;
import logisticspipes.modules.abstractmodules.LogisticsGuiModule;
import logisticspipes.pipes.basic.CoreUnroutedPipe;

public class ModularUIHelper {

    public static final UITexture BACKGROUND_TEXTURE = UITexture.builder()
            .location(LogisticsPipes.rl("textures/gui/GuiBackground.png")).imageSize(45, 45).adaptable(15).build();

    public static final UITexture TAB_RIGHT_TEXTURE = UITexture.builder()
            .location(LogisticsPipes.rl("textures/gui/gui_tab_right.png")).imageSize(41, 45).adaptable(15).build();

    public static void openPipeUI(EntityPlayer player, CoreUnroutedPipe pipe) {
        World world = pipe.getWorld();
        if (world == null || world.isRemote) return;

        GuiFactories.tileEntity().open(player, pipe.getX(), pipe.getY(), pipe.getZ());
    }

    public static void openModuleUI(EntityPlayer player, LogisticsGuiModule module, World world) {
        if (world == null || world.isRemote) return;

        GuiFactories.playerInventory().openFromMainHand(player);
    }

    private static EntityPlayerMP verifyServerSide(EntityPlayer player) {
        if (player == null) throw new NullPointerException("Can't open UI for null player!");
        if (player instanceof EntityPlayerMP entityPlayerMP) return entityPlayerMP;
        throw new IllegalStateException("Expected server player to open UI on server!");
    }
}
