package logisticspipes.crafting.v3.exec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import logisticspipes.crafting.v3.model.ResourceKey;
import logisticspipes.crafting.v3.model.ResourceMatcher;

/**
 * Saves and loads the driver: jobs, tickets, ledger and shipments (plan section 9.1). The reservation index is rebuilt
 * from the RESERVED records. Records or tickets whose item no longer exists are dropped; the next rebalance turns the
 * gap into an UNSOURCED record so the job shows what is missing instead of silently losing it.
 */
public final class DriverPersistence {

    private static final int VERSION = 1;

    private DriverPersistence() {}

    public static NBTTagCompound write(CraftingDriver driver, KeyCodec codec) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("version", VERSION);
        tag.setLong("nextId", driver.peekNextId());
        NBTTagList jobs = new NBTTagList();
        for (CraftingJob job : driver.jobs()) jobs.appendTag(writeJob(job, codec));
        tag.setTag("jobs", jobs);
        NBTTagList records = new NBTTagList();
        for (LedgerRecord r : driver.ledger().all()) records.appendTag(writeRecord(r, codec));
        tag.setTag("records", records);
        NBTTagList shipments = new NBTTagList();
        for (Shipment s : driver.allShipments()) {
            NBTTagCompound st = codec.writeKey(s.key);
            st.setLong("id", s.id);
            st.setLong("job", s.job);
            writeUuid(st, "from", s.from);
            writeUuid(st, "to", s.to);
            st.setLong("qty", s.qty);
            st.setLong("sent", s.sentTick);
            st.setLong("producer", s.producerTicket);
            shipments.appendTag(st);
        }
        tag.setTag("shipments", shipments);
        return tag;
    }

    public static void read(CraftingDriver driver, NBTTagCompound tag, KeyCodec codec, long now) {
        List<CraftingJob> jobs = new ArrayList<>();
        NBTTagList jobList = tag.getTagList("jobs", 10);
        for (int i = 0; i < jobList.tagCount(); i++) {
            CraftingJob job = readJob(jobList.getCompoundTagAt(i), codec);
            if (job != null) jobs.add(job);
        }
        List<LedgerRecord> records = new ArrayList<>();
        NBTTagList recordList = tag.getTagList("records", 10);
        for (int i = 0; i < recordList.tagCount(); i++) {
            LedgerRecord r = readRecord(recordList.getCompoundTagAt(i), codec);
            if (r != null) records.add(r);
        }
        List<Shipment> shipments = new ArrayList<>();
        NBTTagList shipmentList = tag.getTagList("shipments", 10);
        for (int i = 0; i < shipmentList.tagCount(); i++) {
            NBTTagCompound st = shipmentList.getCompoundTagAt(i);
            ResourceKey key = codec.read(st);
            if (key == null) continue;
            // restart the transit timer: travelling items were frozen while the world was off
            shipments.add(
                    new Shipment(
                            st.getLong("id"),
                            st.getLong("job"),
                            readUuid(st, "from"),
                            readUuid(st, "to"),
                            key,
                            st.getLong("qty"),
                            now,
                            st.getLong("producer")));
        }
        driver.restore(tag.getLong("nextId"), jobs, records, shipments);
        for (CraftingJob job : driver.jobs()) {
            for (Ticket t : job.tickets()) driver.rebalance(t);
        }
    }

    private static NBTTagCompound writeJob(CraftingJob job, KeyCodec codec) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("id", job.id);
        tag.setTag("target", codec.writeMatcher(job.target));
        tag.setLong("qty", job.qty);
        writeUuid(tag, "requester", job.requester);
        writeUuid(tag, "player", job.player);
        tag.setLong("created", job.createdTick);
        tag.setByte("state", (byte) job.state.ordinal());
        tag.setBoolean("delivered", job.delivered);
        tag.setLong("windDownSince", job.windDownSince);
        tag.setLong("delivery", job.deliveryTicket);
        NBTTagList nodes = new NBTTagList();
        for (CraftingJob.NodeInfo n : job.nodes) {
            NBTTagCompound nt = codec.writeKey(n.output());
            nt.setInteger("node", n.node());
            nt.setLong("crafts", n.crafts());
            writeUuid(nt, "crafter", n.crafter());
            nt.setString("crafterName", n.crafterName() == null ? "" : n.crafterName());
            nt.setBoolean("legacy", n.legacy());
            nt.setInteger("height", n.height());
            nodes.appendTag(nt);
        }
        tag.setTag("nodes", nodes);
        NBTTagList tickets = new NBTTagList();
        for (Ticket t : job.tickets.values()) tickets.appendTag(writeTicket(t, codec));
        tag.setTag("tickets", tickets);
        return tag;
    }

    private static CraftingJob readJob(NBTTagCompound tag, KeyCodec codec) {
        ResourceMatcher target = codec.readMatcher(tag.getCompoundTag("target"));
        if (target == null) return null;
        CraftingJob job = new CraftingJob(
                tag.getLong("id"),
                target,
                tag.getLong("qty"),
                readUuid(tag, "requester"),
                readUuid(tag, "player"),
                tag.getLong("created"));
        job.state = JobState.values()[tag.getByte("state")];
        job.delivered = tag.getBoolean("delivered");
        job.windDownSince = tag.getLong("windDownSince");
        job.deliveryTicket = tag.getLong("delivery");
        NBTTagList nodes = tag.getTagList("nodes", 10);
        for (int i = 0; i < nodes.tagCount(); i++) {
            NBTTagCompound nt = nodes.getCompoundTagAt(i);
            ResourceKey out = codec.read(nt);
            if (out == null) continue;
            job.nodes.add(
                    new CraftingJob.NodeInfo(
                            nt.getInteger("node"),
                            out,
                            nt.getLong("crafts"),
                            readUuid(nt, "crafter"),
                            nt.getString("crafterName"),
                            nt.getBoolean("legacy"),
                            nt.getInteger("height")));
        }
        NBTTagList tickets = tag.getTagList("tickets", 10);
        for (int i = 0; i < tickets.tagCount(); i++) {
            Ticket t = readTicket(tickets.getCompoundTagAt(i), codec);
            if (t != null) job.addTicket(t);
            if (t != null && t.state.isFinished()) job.ticketFinished(t);
        }
        return job;
    }

    private static NBTTagCompound writeTicket(Ticket t, KeyCodec codec) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("id", t.id);
        tag.setLong("job", t.job);
        tag.setByte("kind", (byte) t.kind.ordinal());
        tag.setInteger("node", t.node);
        writeUuid(tag, "crafter", t.crafter);
        tag.setInteger("slot", t.patternSlot);
        if (t.patternId != null) tag.setString("pattern", t.patternId);
        tag.setLong("crafts", t.crafts);
        tag.setInteger("height", t.height);
        tag.setLong("seq", t.seq);
        tag.setBoolean("legacy", t.legacy);
        tag.setByte("state", (byte) t.state.ordinal());
        tag.setLong("since", t.stateSince);
        tag.setLong("delivered", t.delivered);
        if (t.problem != null) tag.setString("problem", t.problem);
        NBTTagList needs = new NBTTagList();
        for (Need n : t.needs) {
            NBTTagCompound nt = codec.writeMatcher(n.matcher);
            nt.setInteger("index", n.index);
            nt.setLong("qty", n.qty);
            nt.setBoolean("consumed", n.consumed);
            writeUuid(nt, "target", n.target);
            needs.appendTag(nt);
        }
        tag.setTag("needs", needs);
        tag.setTag("outputs", writeAmounts(t.expectedOutputs, codec));
        tag.setTag("remaining", writeAmounts(t.outputsRemaining, codec));
        NBTTagList chanced = new NBTTagList();
        for (ResourceKey k : t.chancedOutputs) chanced.appendTag(codec.writeKey(k));
        tag.setTag("chanced", chanced);
        return tag;
    }

    private static Ticket readTicket(NBTTagCompound tag, KeyCodec codec) {
        List<Need> needs = new ArrayList<>();
        NBTTagList needList = tag.getTagList("needs", 10);
        for (int i = 0; i < needList.tagCount(); i++) {
            NBTTagCompound nt = needList.getCompoundTagAt(i);
            ResourceMatcher m = codec.readMatcher(nt);
            if (m == null) return null;
            needs.add(
                    new Need(
                            nt.getInteger("index"),
                            m,
                            nt.getLong("qty"),
                            nt.getBoolean("consumed"),
                            readUuid(nt, "target")));
        }
        Map<ResourceKey, Long> outputs = readAmounts(tag.getTagList("outputs", 10), codec);
        List<ResourceKey> chanced = new ArrayList<>();
        NBTTagList chancedList = tag.getTagList("chanced", 10);
        for (int i = 0; i < chancedList.tagCount(); i++) {
            ResourceKey k = codec.read(chancedList.getCompoundTagAt(i));
            if (k != null) chanced.add(k);
        }
        Ticket t = new Ticket(
                tag.getLong("id"),
                tag.getLong("job"),
                Ticket.Kind.values()[tag.getByte("kind")],
                tag.getInteger("node"),
                readUuid(tag, "crafter"),
                tag.getInteger("slot"),
                tag.hasKey("pattern") ? tag.getString("pattern") : null,
                tag.getLong("crafts"),
                tag.getInteger("height"),
                tag.getLong("seq"),
                tag.getBoolean("legacy"),
                needs,
                outputs,
                chanced);
        t.state = TicketState.values()[tag.getByte("state")];
        t.stateSince = tag.getLong("since");
        t.delivered = tag.getLong("delivered");
        t.problem = tag.hasKey("problem") ? tag.getString("problem") : null;
        t.outputsRemaining.clear();
        t.outputsRemaining.putAll(readAmounts(tag.getTagList("remaining", 10), codec));
        return t;
    }

    private static NBTTagCompound writeRecord(LedgerRecord r, KeyCodec codec) {
        NBTTagCompound tag = codec.writeKey(r.key);
        tag.setLong("id", r.id);
        tag.setLong("job", r.job);
        tag.setLong("qty", r.qty);
        tag.setByte("state", (byte) r.state.ordinal());
        writeUuid(tag, "loc", r.location);
        tag.setLong("ticket", r.ticket);
        tag.setInteger("need", r.need);
        tag.setInteger("producedBy", r.producedBy);
        tag.setLong("shipment", r.shipment);
        tag.setByte("cause", (byte) r.cause.ordinal());
        return tag;
    }

    private static LedgerRecord readRecord(NBTTagCompound tag, KeyCodec codec) {
        ResourceKey key = codec.read(tag);
        if (key == null || tag.getLong("qty") <= 0) return null;
        LedgerRecord r = new LedgerRecord(
                tag.getLong("id"),
                tag.getLong("job"),
                key,
                tag.getLong("qty"),
                RecordState.values()[tag.getByte("state")]);
        r.location = readUuid(tag, "loc");
        r.ticket = tag.getLong("ticket");
        r.need = tag.getInteger("need");
        r.producedBy = tag.getInteger("producedBy");
        r.shipment = tag.getLong("shipment");
        r.cause = StallCause.values()[tag.getByte("cause")];
        return r;
    }

    private static NBTTagList writeAmounts(Map<ResourceKey, Long> amounts, KeyCodec codec) {
        NBTTagList list = new NBTTagList();
        amounts.forEach((k, v) -> {
            NBTTagCompound e = codec.writeKey(k);
            e.setLong("qty", v);
            list.appendTag(e);
        });
        return list;
    }

    private static Map<ResourceKey, Long> readAmounts(NBTTagList list, KeyCodec codec) {
        Map<ResourceKey, Long> result = new LinkedHashMap<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            ResourceKey k = codec.read(e);
            if (k != null) result.merge(k, e.getLong("qty"), Long::sum);
        }
        return result;
    }

    private static void writeUuid(NBTTagCompound tag, String name, UUID id) {
        if (id == null) return;
        tag.setLong(name + "M", id.getMostSignificantBits());
        tag.setLong(name + "L", id.getLeastSignificantBits());
    }

    private static UUID readUuid(NBTTagCompound tag, String name) {
        if (!tag.hasKey(name + "M")) return null;
        return new UUID(tag.getLong(name + "M"), tag.getLong(name + "L"));
    }
}
