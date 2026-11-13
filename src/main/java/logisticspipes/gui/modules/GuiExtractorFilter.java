/*
 * Copyright (c) Krapht, 2011 "LogisticsPipes" is distributed under the terms of the Minecraft Mod Public License 1.0,
 * or MMPL. Please check the contents of the license located in http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package logisticspipes.gui.modules;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.inventory.IInventory;

import org.lwjgl.opengl.GL11;

import logisticspipes.modules.ModuleExtractor;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.module.ExtractorIncludePacket;
import logisticspipes.network.packets.module.ExtractorSneakyGuiPacket;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.gui.DummyContainer;
import logisticspipes.utils.gui.GuiStringHandlerButton;
import logisticspipes.utils.gui.LogisticsBaseGuiScreen;
import logisticspipes.utils.string.StringUtils;

public class GuiExtractorFilter extends ModuleBaseGui {

    private final ModuleExtractor _extractor;

    @Override
    public void initGui() {
        super.initGui();
        // Default item toggle:
        buttonList.clear();
        buttonList.add(
                new GuiStringHandlerButton(
                        0,
                        width / 2 + 20,
                        height / 2 - 34,
                        60,
                        20,
                        () -> _extractor.areItemsIncluded() ? "Included" : "Excluded"));

        buttonList.add(new GuiButton(1, width / 2 - 25, height / 2 - 34, 40, 20, "Sneaky"));
    }

    @Override
    protected void actionPerformed(GuiButton guibutton) {
        switch (guibutton.id) {
            case 0:
                _extractor.setItemsIncluded(!_extractor.areItemsIncluded());
                MainProxy.sendPacketToServer(
                        PacketHandler.getPacket(ExtractorIncludePacket.class).setModulePos(_extractor));
                break;
            case 1:
                MainProxy.sendPacketToServer(
                        PacketHandler.getPacket(ExtractorSneakyGuiPacket.class).setModulePos(_extractor));
                break;
        }
    }

    public GuiExtractorFilter(IInventory playerInventory, ModuleExtractor extractor) {
        super(null, extractor);
        _extractor = extractor;
        DummyContainer dummy = new DummyContainer(playerInventory, _extractor.getFilterInventory());
        dummy.addNormalSlotsForPlayerInventory(8, 60);

        // Pipe slots
        for (int pipeSlot = 0; pipeSlot < 9; pipeSlot++) {
            dummy.addDummySlot(pipeSlot, 8 + pipeSlot * 18, 18);
        }

        inventorySlots = dummy;
        xSize = 175;
        ySize = 142;
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int par1, int par2) {
        mc.fontRenderer.drawString(_extractor.getFilterInventory().getInventoryName(), 8, 6, 0x404040);
        mc.fontRenderer
                .drawString(StringUtils.translate("gui.logisticspipes.inventory.title"), 8, ySize - 92, 0x404040);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float f, int x, int y) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        mc.renderEngine.bindTexture(LogisticsBaseGuiScreen.ITEMSINK);
        int j = guiLeft;
        int k = guiTop;
        drawTexturedModalRect(j, k, 0, 0, xSize, ySize);
    }

    public void setInclude(boolean flag) {
        _extractor.setItemsIncluded(flag);
    }
}
