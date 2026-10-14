package logisticspipes.network.packets.crafting.requesttable;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.crafting.requesttable.upgrade.RequestTableUpgradeBranch;
import logisticspipes.crafting.requesttable.upgrade.RequestTableUpgradeContainer;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.PacketGuards;
import logisticspipes.network.abstractpackets.ModernPacket;

/** Carries the selected branch/tier and explicit confirmation; the server owns costs and unlocks. */
public class RequestTableItemUpgradePacket extends ModernPacket {

    private int windowId;
    private RequestTableUpgradeBranch branch;
    private int tier;
    private boolean start;
    private boolean creative;

    public RequestTableItemUpgradePacket(int id) {
        super(id);
    }

    public RequestTableItemUpgradePacket setUpgrade(int windowId, RequestTableUpgradeBranch branch, int tier,
            boolean start, boolean creative) {
        this.windowId = windowId;
        this.branch = branch;
        this.tier = tier;
        this.start = start;
        this.creative = creative;
        return this;
    }

    @Override
    public ModernPacket template() {
        return new RequestTableItemUpgradePacket(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        if (PacketGuards.isOnClient(player) || branch == null
                || !branch.isValidSelection(tier)
                || !(player.openContainer instanceof RequestTableUpgradeContainer upgrades)
                || upgrades.windowId != windowId
                || !upgrades.canInteractWith(player))
            return;
        if (start) {
            upgrades.startUpgrade(branch, tier, creative);
            // Reconcile rejected, stale and duplicate confirmations too.
            upgrades.select(upgrades.getSelectedBranch(), upgrades.getSelectedTier());
        } else {
            upgrades.select(branch, tier);
        }
    }

    @Override
    public void writeData(LPDataOutputStream output) throws IOException {
        output.writeInt(windowId);
        output.writeEnum(branch);
        output.writeInt(tier);
        output.writeBoolean(start);
        output.writeBoolean(creative);
    }

    @Override
    public void readData(LPDataInputStream input) throws IOException {
        windowId = input.readInt();
        branch = input.readEnum(RequestTableUpgradeBranch.class);
        tier = input.readInt();
        start = input.readBoolean();
        creative = input.readBoolean();
    }
}
