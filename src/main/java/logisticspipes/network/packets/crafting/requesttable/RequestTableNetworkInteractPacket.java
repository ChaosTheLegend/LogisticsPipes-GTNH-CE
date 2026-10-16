package logisticspipes.network.packets.crafting.requesttable;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.PacketGuards;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * Applies a normal click on the network grid, with an optional entry and the server's actual cursor stack.
 */
public class RequestTableNetworkInteractPacket extends CoordinatesPacket {

    private ItemIdentifierStack stack;
    private boolean fluid;
    private int mouseButton;
    private boolean shift;
    private boolean cursorInteraction;

    public RequestTableNetworkInteractPacket(int id) {
        super(id);
    }

    /** Sets the clicked entry, or null when inserting into an empty grid cell. */
    public RequestTableNetworkInteractPacket setStack(ItemIdentifierStack stack) {
        this.stack = stack;
        return this;
    }

    /** Distinguishes held-stack clicks from withdrawals, including when the cursor changes before delivery. */
    public RequestTableNetworkInteractPacket setCursorInteraction(boolean cursorInteraction) {
        this.cursorInteraction = cursorInteraction;
        return this;
    }

    /**
     * Marks whether the clicked entry represents a fluid.
     */
    public RequestTableNetworkInteractPacket setFluid(boolean fluid) {
        this.fluid = fluid;
        return this;
    }

    /**
     * Sets the clicked mouse button.
     */
    public RequestTableNetworkInteractPacket setMouseButton(int mouseButton) {
        this.mouseButton = mouseButton;
        return this;
    }

    /**
     * Marks whether shift was held during the click.
     */
    public RequestTableNetworkInteractPacket setShift(boolean shift) {
        this.shift = shift;
        return this;
    }

    @Override
    public ModernPacket template() {
        return new RequestTableNetworkInteractPacket(getId());
    }

    @Override
    public void processPacket(EntityPlayer player) {
        RequestTablePipe table = PacketGuards.getOpenRequestTable(player);
        if (table == null) {
            return;
        }
        table.handleNetworkEntryInteraction(player, stack, fluid, mouseButton, shift, cursorInteraction);
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        super.writeData(data);
        data.writeBoolean(stack != null);
        if (stack != null) {
            data.writeItemIdentifierStack(stack);
        }
        data.writeBoolean(fluid);
        data.writeInt(mouseButton);
        data.writeBoolean(shift);
        data.writeBoolean(cursorInteraction);
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        super.readData(data);
        stack = data.readBoolean() ? data.readItemIdentifierStack() : null;
        fluid = data.readBoolean();
        mouseButton = data.readInt();
        shift = data.readBoolean();
        cursorInteraction = data.readBoolean();
    }
}
