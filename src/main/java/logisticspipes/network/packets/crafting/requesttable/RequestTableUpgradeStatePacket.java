package logisticspipes.network.packets.crafting.requesttable;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.crafting.requesttable.upgrade.RequestTableStorageUpgradeConfig;
import logisticspipes.crafting.requesttable.upgrade.RequestTableUpgradeBranch;
import logisticspipes.crafting.requesttable.upgrade.RequestTableUpgradeContainer;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.PacketGuards;
import logisticspipes.network.abstractpackets.ModernPacket;

/** Sends authoritative tree progress and configured bonuses/costs to the current upgrade window. */
public class RequestTableUpgradeStatePacket extends ModernPacket {

    private int windowId;
    private int slotTier;
    private int sizeTier;
    private int fluidSlotTier;
    private int fluidSizeTier;
    private boolean fluidController;
    private boolean monitor;
    private RequestTableUpgradeBranch branch;
    private int tier;
    private RequestTableStorageUpgradeConfig config;
    private RequestTableStorageUpgradeConfig fluidConfig;

    public RequestTableUpgradeStatePacket(int id) {
        super(id);
    }

    public RequestTableUpgradeStatePacket setState(RequestTableUpgradeContainer upgrades) {
        windowId = upgrades.windowId;
        slotTier = upgrades.getTable().getItemSlotTier();
        sizeTier = upgrades.getTable().getItemSizeTier();
        fluidSlotTier = upgrades.getTable().getFluidSlotTier();
        fluidSizeTier = upgrades.getTable().getFluidSizeTier();
        fluidController = upgrades.getTable().isFluidEnabled();
        monitor = upgrades.getTable().hasMonitoringUpgrade();
        branch = upgrades.getSelectedBranch();
        tier = upgrades.getSelectedTier();
        config = upgrades.getConfig(false);
        fluidConfig = upgrades.getConfig(true);
        return this;
    }

    @Override
    public ModernPacket template() {
        return new RequestTableUpgradeStatePacket(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        if (!PacketGuards.isOnClient(player) || branch == null
                || !branch.isValidSelection(tier)
                || !(player.openContainer instanceof RequestTableUpgradeContainer upgrades)
                || upgrades.windowId != windowId)
            return;
        upgrades.applyState(
                config,
                fluidConfig,
                slotTier,
                sizeTier,
                fluidSlotTier,
                fluidSizeTier,
                fluidController,
                monitor,
                branch,
                tier);
    }

    @Override
    public void writeData(LPDataOutputStream output) throws IOException {
        output.writeInt(windowId);
        output.writeInt(slotTier);
        output.writeInt(sizeTier);
        output.writeInt(fluidSlotTier);
        output.writeInt(fluidSizeTier);
        output.writeBoolean(fluidController);
        output.writeBoolean(monitor);
        output.writeEnum(branch);
        output.writeInt(tier);
        config.writeData(output);
        fluidConfig.writeData(output);
    }

    @Override
    public void readData(LPDataInputStream input) throws IOException {
        windowId = input.readInt();
        slotTier = input.readInt();
        sizeTier = input.readInt();
        fluidSlotTier = input.readInt();
        fluidSizeTier = input.readInt();
        fluidController = input.readBoolean();
        monitor = input.readBoolean();
        branch = input.readEnum(RequestTableUpgradeBranch.class);
        tier = input.readInt();
        config = RequestTableStorageUpgradeConfig.readData(input);
        fluidConfig = RequestTableStorageUpgradeConfig.readData(input);
    }

}
