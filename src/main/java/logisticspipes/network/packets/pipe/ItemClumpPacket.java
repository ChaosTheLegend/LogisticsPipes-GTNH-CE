package logisticspipes.network.packets.pipe;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.utils.item.ItemIdentifierStack;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * A clump of routed items leaves the pipe at these coordinates along a corridor. The client moves it through the pipes
 * of {@link #path} on its own; the server sends nothing else until the clump leaves the next junction.
 */
@Accessors(chain = true)
public class ItemClumpPacket extends CoordinatesPacket {

    @Getter
    @Setter
    private int travelId;

    @Getter
    @Setter
    private float speed;

    @Getter
    @Setter
    private ForgeDirection input;

    /** ForgeDirection ordinal of the exit taken at each pipe, starting at this one. */
    @Getter
    @Setter
    private byte[] path;

    /** Up to three stacks of the clump to draw. */
    @Getter
    @Setter
    private ItemIdentifierStack[] stacks;

    public ItemClumpPacket(int id) {
        super(id);
    }

    @Override
    public void processPacket(EntityPlayer player) {
        LogisticsTileGenericPipe tile = this.getPipe(player.getEntityWorld(), LTGPCompletionCheck.TRANSPORT);
        if (tile == null || tile.pipe == null || tile.pipe.transport == null) {
            return;
        }
        tile.pipe.transport.handleItemClumpPacket(travelId, input, path, speed, stacks);
    }

    @Override
    public void writeData(LPDataOutputStream data) throws IOException {
        super.writeData(data);
        data.writeInt(travelId);
        data.writeFloat(speed);
        data.writeForgeDirection(input);
        data.writeByteArray(path);
        data.writeByte(stacks.length);
        for (ItemIdentifierStack stack : stacks) {
            data.writeItemIdentifierStack(stack);
        }
    }

    @Override
    public void readData(LPDataInputStream data) throws IOException {
        super.readData(data);
        travelId = data.readInt();
        speed = data.readFloat();
        input = data.readForgeDirection();
        path = data.readByteArray();
        stacks = new ItemIdentifierStack[data.readByte()];
        for (int i = 0; i < stacks.length; i++) {
            stacks[i] = data.readItemIdentifierStack();
        }
    }

    @Override
    public ModernPacket template() {
        return new ItemClumpPacket(getId());
    }
}
