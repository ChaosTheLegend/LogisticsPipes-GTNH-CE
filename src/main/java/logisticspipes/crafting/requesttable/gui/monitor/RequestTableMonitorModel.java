package logisticspipes.crafting.requesttable.gui.monitor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import logisticspipes.LogisticsPipes;
import logisticspipes.crafting.monitor.CraftingMonitorData;
import logisticspipes.crafting.monitor.CraftingMonitorData.Node;
import logisticspipes.crafting.monitor.CraftingMonitorData.State;
import logisticspipes.crafting.monitor.CraftingMonitorData.Summary;
import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.crafting.requesttable.RequestTableMonitorRequestPacket;
import logisticspipes.proxy.MainProxy;

/** Per-screen subscription and atomic snapshot assembly, independent of GUI rendering. */
@SideOnly(Side.CLIENT)
public final class RequestTableMonitorModel {

    final List<Summary> summaries = new ArrayList<>();
    final Map<Integer, State> states = new HashMap<>();
    private final RequestTablePipe table;
    private final Map<Long, Assembly> pending = new LinkedHashMap<>();
    List<Node> nodes = new ArrayList<>();
    long selected = -1;
    long receivedAt;
    long generation;
    private long session;
    private long overviewTransfer;
    private long detailTransfer;
    private long revision = -1;
    private int window;
    private boolean active;
    private boolean awaitingStructure;
    private boolean needsResync;
    private long requestedAt;

    public RequestTableMonitorModel(RequestTablePipe table) {
        this.table = table;
    }

    public void enter(int window) {
        this.window = window;
        session = System.nanoTime();
        active = true;
        overviewTransfer = detailTransfer = 0;
        summaries.clear();
        clearDetails();
        awaitingStructure = selected >= 0;
        requestedAt = System.currentTimeMillis();
        request(0);
    }

    public void leave() {
        if (active) request(2);
        active = false;
        pending.clear();
    }

    void select(long job) {
        if (selected == job && (revision >= 0 || awaitingStructure)) return;
        selected = job;
        clearDetails();
        detailTransfer = 0;
        requestStructure();
    }

    Summary summary() {
        for (Summary summary : summaries) if (summary.id == selected) return summary;
        return null;
    }

    private void clearDetails() {
        nodes = new ArrayList<>();
        states.clear();
        revision = -1;
        generation++;
        awaitingStructure = needsResync = false;
    }

    void maintain() {
        long now = System.currentTimeMillis();
        pending.values().removeIf(assembly -> now - assembly.started > 10_000);
        if (active && selected >= 0 && awaitingStructure && now - requestedAt > 10_000) {
            awaitingStructure = false;
            requestStructure();
        }
    }

    private void requestStructure() {
        if (!active || selected < 0) return;
        if (awaitingStructure) {
            needsResync = true;
            return;
        }
        awaitingStructure = true;
        needsResync = false;
        requestedAt = System.currentTimeMillis();
        request(1);
    }

    private void request(int action) {
        if (table.container == null) return;
        MainProxy.sendPacketToServer(
                PacketHandler.getPacket(RequestTableMonitorRequestPacket.class)
                        .setRequest(session, window, action, selected).setTilePos(table.container));
    }

    public void chunk(long session, long transfer, int part, int parts, byte[] bytes) {
        if (!active || this.session != session) return;
        long now = System.currentTimeMillis();
        pending.values().removeIf(assembly -> now - assembly.started > 10_000);
        if (!pending.containsKey(transfer) && pending.size() >= 4) pending.remove(pending.keySet().iterator().next());
        Assembly assembly = pending.computeIfAbsent(transfer, ignored -> new Assembly(parts));
        if (assembly.parts.length != parts || part < 0 || part >= parts) return;
        if (assembly.parts[part] == null) {
            assembly.parts[part] = bytes;
            assembly.received++;
        }
        if (assembly.received != parts) return;
        pending.remove(transfer);
        try {
            ByteArrayOutputStream combined = new ByteArrayOutputStream();
            for (byte[] chunk : assembly.parts) {
                if (combined.size() + chunk.length > CraftingMonitorData.MAX_BYTES)
                    throw new IOException("Oversized monitor snapshot");
                combined.write(chunk);
            }
            apply(transfer, combined.toByteArray());
        } catch (IOException | RuntimeException exception) {
            LogisticsPipes.log.warn("Unable to read crafting monitor snapshot", exception);
            requestStructure();
        }
    }

    private void apply(long transfer, byte[] payload) throws IOException {
        try (LPDataInputStream input = new LPDataInputStream(payload)) {
            int kind = input.readUnsignedByte();
            if (kind == CraftingMonitorData.OVERVIEW) {
                if (transfer <= overviewTransfer) return;
                List<Summary> updated = new ArrayList<>();
                int count = CraftingMonitorData.count(input);
                for (int i = 0; i < count; i++) updated.add(Summary.read(input));
                summaries.clear();
                summaries.addAll(updated);
                receivedAt = System.currentTimeMillis();
                overviewTransfer = transfer;
                if (summary() == null) {
                    long choice = -1;
                    for (Summary summary : summaries) if (!summary.finished) {
                        choice = summary.id;
                        break;
                    }
                    if (choice < 0 && !summaries.isEmpty()) choice = summaries.get(0).id;
                    selected = -1;
                    clearDetails();
                    if (choice >= 0) select(choice);
                }
                return;
            }
            if (kind != CraftingMonitorData.STRUCTURE && kind != CraftingMonitorData.CHANGES)
                throw new IOException("Unknown monitor message");
            long job = input.readLong();
            long base = input.readLong();
            long next = input.readLong();
            if (job != selected || transfer <= detailTransfer) return;
            if (kind == CraftingMonitorData.CHANGES && base != revision) {
                requestStructure();
                return;
            }
            List<Node> updatedNodes = kind == CraftingMonitorData.STRUCTURE ? CraftingMonitorData.readNodes(input)
                    : nodes;
            Map<Integer, State> updatedStates = new HashMap<>();
            if (kind == CraftingMonitorData.CHANGES) updatedStates.putAll(states);
            int count = CraftingMonitorData.count(input);
            for (int i = 0; i < count; i++) updatedStates.put(input.readInt(), State.read(input));
            nodes = updatedNodes;
            states.clear();
            states.putAll(updatedStates);
            revision = next;
            detailTransfer = transfer;
            generation++;
            if (kind == CraftingMonitorData.STRUCTURE) {
                awaitingStructure = false;
                if (needsResync) requestStructure();
            }
        }
    }

    private static final class Assembly {

        final byte[][] parts;
        final long started = System.currentTimeMillis();
        int received;

        Assembly(int count) {
            parts = new byte[count][];
        }
    }
}
