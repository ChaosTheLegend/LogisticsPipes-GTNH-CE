package logisticspipes.gui.orderer;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.orderer.RequestFluidComponentPacket;
import logisticspipes.network.packets.orderer.RequestFluidOrdererRefreshPacket;
import logisticspipes.network.packets.orderer.SubmitFluidRequestPacket;
import logisticspipes.pipes.PipeFluidRequestLogistics;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.gui.ItemDisplay;
import logisticspipes.utils.gui.SmallGuiButton;
import logisticspipes.utils.item.ItemIdentifier;

public class FluidGuiOrderer extends GuiOrderer {

    private enum DisplayOptions {
        Both,
        SupplyOnly,
        CraftOnly,
    }

    private DisplayOptions displayOptions = DisplayOptions.Both;

    public FluidGuiOrderer(PipeFluidRequestLogistics pipe, EntityPlayer entityPlayer) {
        super(pipe.getX(), pipe.getY(), pipe.getZ(), MainProxy.getDimensionForWorld(pipe.getWorld()), entityPlayer);
        _title = "Request Fluid";
        refreshItems();
    }

    @Override
    public void initGui() {
        boolean setItemDisplay = itemDisplay == null;
        super.initGui();
        buttonList.add(new SmallGuiButton(3, guiLeft + 10, bottom - 15, 46, 10, "Refresh")); // Refresh
        buttonList.add(new SmallGuiButton(13, guiLeft + 10, bottom - 28, 46, 10, "Content")); // Component
        buttonList.add(new SmallGuiButton(9, guiLeft + 10, bottom - 41, 46, 10, "Both"));
        if (setItemDisplay) {
            itemDisplay = new ItemDisplay(
                    this,
                    mc.fontRenderer,
                    this,
                    this,
                    guiLeft + 10,
                    guiTop + 18,
                    xSize - 20,
                    ySize - 100,
                    new int[] { 1, 1000, 16000, 100 },
                    false);
        }
        itemDisplay.reposition(guiLeft + 10, guiTop + 18, xSize - 20, ySize - 100);
    }

    @Override
    protected void actionPerformed(GuiButton guibutton) {
        if (guibutton.id == 0 && itemDisplay.getSelectedItem() != null) {
            MainProxy.sendPacketToServer(
                    PacketHandler.getPacket(SubmitFluidRequestPacket.class).setDimension(dimension)
                            .setStack(itemDisplay.getSelectedItem().getItem().makeStack(itemDisplay.getRequestCount()))
                            .setPosX(xCoord).setPosY(yCoord).setPosZ(zCoord));
            refreshItems();
        } else if (guibutton.id == 13 && itemDisplay.getSelectedItem() != null) {
            MainProxy.sendPacketToServer(
                    PacketHandler.getPacket(RequestFluidComponentPacket.class).setDimension(dimension)
                            .setStack(itemDisplay.getSelectedItem().getItem().makeStack(itemDisplay.getRequestCount()))
                            .setPosX(xCoord).setPosY(yCoord).setPosZ(zCoord));
        } else {
            super.actionPerformed(guibutton);
            if (guibutton.id == 9) {
                switch (displayOptions) {
                    case Both:
                        displayOptions = DisplayOptions.CraftOnly;
                        guibutton.displayString = "Craft";
                        break;
                    case CraftOnly:
                        displayOptions = DisplayOptions.SupplyOnly;
                        guibutton.displayString = "Supply";
                        break;
                    case SupplyOnly:
                        displayOptions = DisplayOptions.Both;
                        guibutton.displayString = "Both";
                        break;
                }
                refreshItems();
            }
        }
    }

    @Override
    protected int getStackAmount() {
        return 1000;
    }

    @Override
    public void refreshItems() {
        MainProxy.sendPacketToServer(
                PacketHandler.getPacket(RequestFluidOrdererRefreshPacket.class).setInteger2(getDisplayOptionId())
                        .setInteger(dimension).setPosX(xCoord).setPosY(yCoord).setPosZ(zCoord));
    }

    private int getDisplayOptionId() {
        switch (displayOptions) {
            case SupplyOnly:
                return 1;
            case CraftOnly:
                return 2;
            case Both:
            default:
                return 0;
        }
    }

    @Override
    public void specialItemRendering(ItemIdentifier item, int x, int y) {}
}
