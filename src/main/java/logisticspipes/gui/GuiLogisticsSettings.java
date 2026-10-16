package logisticspipes.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import logisticspipes.LogisticsPipes;
import logisticspipes.config.PlayerConfig;
import logisticspipes.utils.gui.DummyContainer;
import logisticspipes.utils.gui.GuiCheckBox;
import logisticspipes.utils.gui.LogisticsBaseTabGuiScreen;
import logisticspipes.utils.gui.SearchBar;
import logisticspipes.utils.string.StringUtils;

public class GuiLogisticsSettings extends LogisticsBaseTabGuiScreen {

    private final String PREFIX = "gui.settings.";

    public GuiLogisticsSettings(final EntityPlayer player) {
        super(220, 220);
        DummyContainer dummy = new DummyContainer(player, null);
        dummy.addNormalSlotsForPlayerInventory(10, 135);

        addTab(new PipeRenderSettings());

        inventorySlots = dummy;
    }

    private class PipeRenderSettings extends TabSubGui {

        private SearchBar contentRenderDistance;
        private GuiCheckBox useNewRendererButton;

        private PipeRenderSettings() {}

        @Override
        public void initTab() {
            PlayerConfig config = LogisticsPipes.getClientPlayerConfig();
            if (contentRenderDistance == null) {
                contentRenderDistance = new SearchBar(
                        fontRendererObj,
                        getBaseScreen(),
                        15,
                        105,
                        30,
                        15,
                        false,
                        true,
                        true);
                contentRenderDistance.searchinput1 = config.getRenderPipeContentDistance() + "";
            }
            contentRenderDistance.reposition(15, 54, 30, 15);
            useNewRendererButton = (GuiCheckBox) addButton(
                    new GuiCheckBox(0, guiLeft + 15, guiTop + 30, 16, 16, config.isUseNewRenderer()));
        }

        @Override
        public void renderIcon(int x, int y) {
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            RenderHelper.enableGUIStandardItemLighting();
            ItemStack stack = new ItemStack(LogisticsPipes.LogisticsBasicPipe, 1);
            GuiScreen.itemRender.renderItemAndEffectIntoGUI(fontRendererObj, getMC().renderEngine, stack, x, y);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GuiScreen.itemRender.zLevel = 0.0F;
        }

        @Override
        public void renderBackgroundContent() {}

        @Override
        public void buttonClicked(GuiButton button) {
            if (button == useNewRendererButton) {
                useNewRendererButton.change();
            }
        }

        @Override
        public void renderForgroundContent() {
            contentRenderDistance.renderSearchBar();
            fontRendererObj.drawString(StringUtils.translate(PREFIX + "pipenewrenderer"), 38, 34, 0x404040);
            fontRendererObj.drawString(StringUtils.translate(PREFIX + "pipecontentrenderdistance"), 53, 58, 0x404040);
        }

        @Override
        public boolean handleClick(int x, int y, int type) {
            return contentRenderDistance.handleClick(x - guiLeft, y - guiTop, type);
        }

        @Override
        public boolean handleKey(int code, char c) {
            return contentRenderDistance.handleKey(c, code);
        }

        @Override
        public void guiClose() {
            PlayerConfig config = LogisticsPipes.getClientPlayerConfig();
            try {
                config.setRenderPipeContentDistance(Integer.parseInt(contentRenderDistance.getContent()));
            } catch (Exception e) {
                e.printStackTrace();
            }
            config.setUseNewRenderer(useNewRendererButton.getState());
            config.sendUpdate();
        }
    }
}
