package logisticspipes.transport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.routing.astar.CorridorEdge;
import logisticspipes.transport.LPTravelingItem.LPTravelingItemServer;

/**
 * Routed items travelling together along a corridor as one scheduled hop: nothing simulates them until
 * {@link #arrivalTick}, when the pipe they are heading to (which holds the clump) takes them in.
 * <p>
 * All items share the destination, the transport mode and the route computed when the first of them left, so the route
 * is looked up once per clump and hop instead of once per item and pipe.
 */
public final class ItemClump {

    static final Comparator<ItemClump> BY_ARRIVAL = Comparator.comparingLong(c -> c.arrivalTick);

    /** Client-side identity, drawn from the traveling item id space so it never collides with a single item. */
    final int id;
    final List<LPTravelingItemServer> items = new ArrayList<>();
    final int destination;
    final TransportMode mode;
    float speed;
    /** Corridors from the junction the route was computed at; {@code null} when restored from NBT. */
    CorridorEdge[] path;
    /** Index in {@link #path} of the corridor being travelled now. */
    int hop;
    /** Direction the items move in when they enter the pipe they arrive at. */
    ForgeDirection travelDirection;
    long departTick;
    long arrivalTick;
    /** Set once the holder unloaded or took the items in: the clump must not take new items any more. */
    boolean closed;

    /** Corridor the clump is on now (forwards or back); {@code null} for clumps restored from NBT. */
    CorridorEdge edge;
    /** Position of the junction {@link #edge} starts at, pipe 0 of its {@link CorridorEdge#travelPath}. */
    int srcX, srcY, srcZ;
    /** Going back to the junction at pipe 0 because the corridor broke ahead of it. */
    boolean returning;
    /** Position along {@link #edge} (in pipes from its start) at {@link #departTick}. */
    float startPos;
    float ticksPerPipe;
    /** The pipe transport holding this clump in its incoming queue. */
    PipeTransportLogistics holder;
    /** Chunk keys this clump is indexed under in {@link ClumpTransit}. */
    long[] indexedChunks;

    /**
     * Position along {@link #edge} at {@code now}, in pipes from its start: pipe {@code i} covers {@code [i, i + 1)}.
     */
    float positionAt(long now) {
        float moved = (now - departTick) / ticksPerPipe;
        return returning ? Math.max(0, startPos - moved) : Math.min(edge.travelPath.length, startPos + moved);
    }

    ItemClump(int id, int destination, TransportMode mode, float speed) {
        this.id = id;
        this.destination = destination;
        this.mode = mode;
        this.speed = speed;
    }

    CorridorEdge currentEdge() {
        return path == null ? null : path[hop];
    }

    /** The corridor after the one being travelled, or {@code null} at the end of the path. */
    CorridorEdge nextEdge() {
        return path == null || hop + 1 >= path.length ? null : path[hop + 1];
    }

    int itemCount() {
        int count = 0;
        for (LPTravelingItemServer item : items) {
            count += item.getItemIdentifierStack().getStackSize();
        }
        return count;
    }

    /** Ticks to cross a corridor of {@code steps} pipes at {@code speed} blocks per tick. */
    static int travelTicks(int steps, float speed) {
        return Math.max(1, (int) Math.ceil(steps / Math.max(speed, 0.001F)));
    }

    /** Identifies clumps that may travel together from one junction: same destination, mode and remaining route. */
    static final class Key {

        private final int destination;
        private final TransportMode mode;
        private final long[] remainingEdges;
        private final int hash;

        Key(int destination, TransportMode mode, CorridorEdge[] path, int from) {
            this.destination = destination;
            this.mode = mode;
            remainingEdges = new long[path.length - from];
            for (int i = from; i < path.length; i++) {
                remainingEdges[i - from] = path[i].id;
            }
            hash = 31 * (31 * destination + mode.ordinal()) + Arrays.hashCode(remainingEdges);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Key)) {
                return false;
            }
            Key k = (Key) o;
            return hash == k.hash && destination == k.destination
                    && mode == k.mode
                    && Arrays.equals(remainingEdges, k.remainingEdges);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    void writeToNBT(NBTTagCompound tag, long now) {
        tag.setInteger("remaining", (int) Math.max(0, arrivalTick - now));
        tag.setInteger("travelDirection", travelDirection.ordinal());
        tag.setFloat("speed", speed);
        NBTTagList list = new NBTTagList();
        for (LPTravelingItemServer item : items) {
            NBTTagCompound itemTag = new NBTTagCompound();
            item.writeToNBT(itemTag);
            list.appendTag(itemTag);
        }
        tag.setTag("items", list);
    }

    /** Restores a pathless clump: on arrival its items are routed again as if they had just entered the pipe. */
    static ItemClump readFromNBT(NBTTagCompound tag, long now) {
        ItemClump clump = new ItemClump(LPTravelingItem.nextId(), -1, TransportMode.Unknown, tag.getFloat("speed"));
        clump.travelDirection = ForgeDirection.getOrientation(tag.getInteger("travelDirection"));
        clump.departTick = now;
        clump.arrivalTick = now + tag.getInteger("remaining");
        clump.closed = true;
        NBTTagList list = tag.getTagList("items", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            LPTravelingItemServer item = new LPTravelingItemServer(list.getCompoundTagAt(i));
            if (!item.isCorrupted()) {
                clump.items.add(item);
            }
        }
        return clump;
    }
}
