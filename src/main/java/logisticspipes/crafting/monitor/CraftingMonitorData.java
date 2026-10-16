package logisticspipes.crafting.monitor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.utils.item.ItemIdentifierStack;

/** Display-only snapshots. No snapshot exposes or modifies a live LP order. */
public final class CraftingMonitorData {

    public static final int OVERVIEW = 0;
    public static final int STRUCTURE = 1;
    public static final int CHANGES = 2;
    public static final int CHUNK_BYTES = 16 * 1024;
    public static final int MAX_BYTES = 32 * 1024 * 1024;

    private CraftingMonitorData() {
    }

    public static int count(LPDataInputStream input) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > 100_000) throw new IOException("Invalid monitor entry count");
        return count;
    }

    public static List<Node> readNodes(LPDataInputStream input) throws IOException {
        List<Node> nodes = new ArrayList<>();
        int count = count(input);
        for (int i = 0; i < count; i++) nodes.add(Node.read(input));
        return nodes;
    }

    public static final class Summary {

        public final long id;
        public final ItemIdentifierStack result;
        public final boolean fluid;
        public final boolean locked;
        public final boolean batch;
        public final long elapsed;
        public final boolean finished;
        public final boolean unfulfilled;

        public Summary(long id, ItemIdentifierStack result, boolean fluid, boolean locked, boolean batch, long elapsed,
                       boolean finished, boolean unfulfilled) {
            this.id = id;
            this.result = result;
            this.fluid = fluid;
            this.locked = locked;
            this.batch = batch;
            this.elapsed = elapsed;
            this.finished = finished;
            this.unfulfilled = unfulfilled;
        }

        public static Summary read(LPDataInputStream input) throws IOException {
            long id = input.readLong();
            ItemIdentifierStack result = input.readBoolean() ? input.readItemIdentifierStack() : null;
            return new Summary(
                id,
                result,
                input.readBoolean(),
                input.readBoolean(),
                input.readBoolean(),
                input.readLong(),
                input.readBoolean(),
                input.readBoolean());
        }

        public void write(LPDataOutputStream output) throws IOException {
            output.writeLong(id);
            output.writeBoolean(result != null);
            if (result != null) output.writeItemIdentifierStack(result);
            output.writeBoolean(fluid);
            output.writeBoolean(locked);
            output.writeBoolean(batch);
            output.writeLong(elapsed);
            output.writeBoolean(finished);
            output.writeBoolean(unfulfilled);
        }
    }

    public static final class Node {

        public final int id;
        public final int parent;
        public final boolean group;
        public final boolean fluid;
        public final boolean locked;
        public final ItemIdentifierStack resource;
        public final int initial;
        public final String type;
        public final String destination;

        public Node(int id, int parent, boolean group, boolean fluid, boolean locked, ItemIdentifierStack resource,
                    int initial, String type, String destination) {
            this.id = id;
            this.parent = parent;
            this.group = group;
            this.fluid = fluid;
            this.locked = locked;
            this.resource = resource;
            this.initial = initial;
            this.type = type;
            this.destination = destination;
        }

        public static Node read(LPDataInputStream input) throws IOException {
            int id = input.readInt();
            int parent = input.readInt();
            boolean group = input.readBoolean();
            boolean fluid = input.readBoolean();
            boolean locked = input.readBoolean();
            ItemIdentifierStack stack = input.readBoolean() ? input.readItemIdentifierStack() : null;
            return new Node(id, parent, group, fluid, locked, stack, input.readInt(), input.readUTF(), input.readUTF());
        }

        public Node visible(boolean fluids) {
            return !fluid || fluids ? this : new Node(id, parent, group, true, true, null, 0, "", "");
        }

        public void write(LPDataOutputStream output) throws IOException {
            output.writeInt(id);
            output.writeInt(parent);
            output.writeBoolean(group);
            output.writeBoolean(fluid);
            output.writeBoolean(locked);
            output.writeBoolean(resource != null);
            if (resource != null) output.writeItemIdentifierStack(resource);
            output.writeInt(initial);
            output.writeUTF(type);
            output.writeUTF(destination);
        }
    }

    public static final class State {

        public static final State EMPTY = new State(0, false, true, 0, new float[0]);
        public final int remaining;
        public final boolean active;
        public final boolean finished;
        public final int machine;
        public final float[] transport;

        public State(int remaining, boolean active, boolean finished, int machine, float[] transport) {
            this.remaining = remaining;
            this.active = active;
            this.finished = finished;
            this.machine = machine;
            this.transport = transport;
        }

        public static State read(LPDataInputStream input) throws IOException {
            int remaining = input.readInt();
            boolean active = input.readBoolean();
            boolean finished = input.readBoolean();
            int machine = input.readInt();
            float[] transport = new float[count(input)];
            for (int i = 0; i < transport.length; i++) transport[i] = input.readFloat();
            return new State(remaining, active, finished, machine, transport);
        }

        public void write(LPDataOutputStream output) throws IOException {
            output.writeInt(remaining);
            output.writeBoolean(active);
            output.writeBoolean(finished);
            output.writeInt(machine);
            output.writeInt(transport.length);
            for (float progress : transport) output.writeFloat(progress);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof State state && remaining == state.remaining
                && active == state.active
                && finished == state.finished
                && machine == state.machine
                && Arrays.equals(transport, state.transport);
        }

        @Override
        public int hashCode() {
            return Objects.hash(remaining, active, finished, machine, Arrays.hashCode(transport));
        }
    }
}
