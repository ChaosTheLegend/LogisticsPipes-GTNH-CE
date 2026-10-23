package logisticspipes.crafting.v3.pipe;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import logisticspipes.crafting.v3.exec.CraftDelivery;
import logisticspipes.crafting.v3.exec.KeyCodec;
import logisticspipes.crafting.v3.exec.Need;
import logisticspipes.crafting.v3.exec.Ticket;
import logisticspipes.crafting.v3.model.BlockingMode;
import logisticspipes.crafting.v3.model.ResourceAmount;
import logisticspipes.crafting.v3.model.ResourceKey;
import logisticspipes.crafting.v3.model.ResourceMatcher;

/**
 * The crafting pipe's side of the ticket protocol (plan section 6): ticket table, buffer, insertion queue and output
 * attribution. Holds physical items tagged by owner, a ticket's need or a job pool, and never crafts on its own: no
 * needs without an Open, no insertion without a Commit.
 * <p>
 * Minecraft-free: the machine and the driver are behind {@link Machine} and {@link Link}.
 */
public final class CrafterRuntime {

    /** The adjacent machine (and any satellites). */
    public interface Machine {

        enum InsertResult {
            INSERTED,
            NO_ROOM,
            CONFIG_ERROR
        }

        BlockingMode mode();

        /**
         * Applies the pattern's configuration, then inserts all {@code stacks} or nothing (simulating first). Should
         * the real insert still come up short, what didn't go in is added to {@code notInserted} and the result is
         * INSERTED; the crafter retries the rest.
         */
        InsertResult insert(int patternSlot, String patternId, long crafts, List<ResourceAmount> stacks,
                List<ResourceAmount> notInserted);

        /** Whether some pattern of this crafter uses {@code key} as an input (never pulled back out). */
        boolean isInput(ResourceKey key);

        /** No inputs or outputs left in the machine, ignoring items the pattern doesn't consume. */
        boolean isIdle(int patternSlot, String patternId);

        /** Takes up to {@code qty} of {@code key} out of the machine. */
        long extract(ResourceKey key, long qty);

        /** What could be taken out of the machine right now (its output side). */
        List<ResourceKey> extractable();
    }

    /** The driver and the network. */
    public interface Link {

        void arrived(long ticket, int need, ResourceKey key, long qty, CraftDelivery hint);

        void stray(long job, ResourceKey key, long qty, CraftDelivery hint);

        void inserted(long ticket);

        void output(long ticket, ResourceKey key, long qty);

        void wrongCraft(long ticket, String reason);

        /** Routes items out of the pipe. Returns the amount that left. */
        long send(ResourceKey key, long qty, UUID destination, CraftDelivery info);

        /** Sends items to storage (item sinks / default route). Returns the amount that left. */
        long sendToStorage(ResourceKey key, long qty);

        boolean isJobAlive(long job);
    }

    public static final int OUTPUT_INTERVAL = 5;
    /** An inserted set in an idle machine that produced nothing for this long is a wrong craft. */
    public static final long IDLE_WRONG_CRAFT_TICKS = 200;

    private final Machine machine;
    private final Link link;
    private final Map<Long, PipeTicket> tickets = new LinkedHashMap<>();
    private final Map<Long, Map<ResourceKey, Long>> pools = new LinkedHashMap<>();
    private final Map<ResourceKey, Long> orphans = new LinkedHashMap<>();
    private final Set<Long> insertQueue = new LinkedHashSet<>();
    private final Deque<Long> inserted = new ArrayDeque<>();
    private String lastInsertedPattern;
    private boolean dirty;

    public CrafterRuntime(Machine machine, Link link) {
        this.machine = machine;
        this.link = link;
    }

    // ================================================================== driver -> pipe

    public void open(Ticket t) {
        if (tickets.containsKey(t.id)) return;
        PipeTicket p = new PipeTicket(t.id, t.job, t.patternSlot, t.patternId, t.height, t.seq);
        p.crafts = t.crafts;
        for (Need n : t.needs) p.needs.add(new NeedState(n.index, n.matcher, n.qty, n.consumed));
        p.expectedRemaining.putAll(t.expectedOutputs);
        p.chanced.addAll(t.chancedOutputs);
        tickets.put(t.id, p);
        dirty = true;
    }

    public void commit(long ticket) {
        PipeTicket p = tickets.get(ticket);
        if (p == null || p.state == PipeTicket.State.INSERTED) return;
        p.state = PipeTicket.State.COMMITTED;
        p.configError = false;
        insertQueue.add(ticket);
        dirty = true;
    }

    public void cancel(long ticket) {
        PipeTicket p = tickets.get(ticket);
        if (p == null) return;
        if (p.state == PipeTicket.State.INSERTED) {
            // keeps running; its outputs go to the pool once captured
            p.cancelled = true;
            return;
        }
        for (NeedState n : p.needs) {
            for (Map.Entry<ResourceKey, Long> e : n.buffered.entrySet()) addPool(p.job, e.getKey(), e.getValue());
        }
        tickets.remove(ticket);
        insertQueue.remove(ticket);
        dirty = true;
    }

    public long assign(long job, ResourceKey key, long qty, long ticket, int need) {
        PipeTicket p = tickets.get(ticket);
        if (p == null || p.job != job) return 0;
        NeedState n = p.need(need);
        long have = pooled(job, key);
        long move = n == null ? 0 : Math.min(Math.min(qty, have), n.remaining());
        if (move <= 0) return 0;
        takePool(job, key, move);
        n.buffered.merge(key, move, Long::sum);
        dirty = true;
        return move;
    }

    public long ship(long job, ResourceKey key, long qty, UUID destination, CraftDelivery info) {
        long move = Math.min(qty, pooled(job, key));
        if (move <= 0) return 0;
        long sent = Math.max(0, Math.min(move, link.send(key, move, destination, info)));
        if (sent > 0) {
            takePool(job, key, sent);
            dirty = true;
        }
        return sent;
    }

    public void flushJob(long job) {
        Map<ResourceKey, Long> pool = pools.remove(job);
        if (pool == null) return;
        pool.forEach((k, v) -> orphans.merge(k, v, Long::sum));
        flushOrphans();
        dirty = true;
    }

    public long pooled(long job, ResourceKey key) {
        Map<ResourceKey, Long> pool = pools.get(job);
        if (pool == null) return 0;
        return pool.getOrDefault(key, 0L);
    }

    // ================================================================== arrivals (6.3)

    /**
     * An item routed by the driver reached the pipe. Returns the amount taken; untracked items (no hint) are left alone
     * so the transport sends them on.
     */
    public long accept(ResourceKey key, long qty, CraftDelivery hint) {
        if (hint == null || qty <= 0) return 0;
        List<long[]> assignments = new ArrayList<>(); // ticket, need, qty
        long left = qty;
        for (NeedRef ref : candidates(key, hint)) {
            if (left <= 0) break;
            long take = Math.min(left, ref.need.remaining());
            if (take <= 0) continue;
            ref.need.buffered.merge(key, take, Long::sum);
            assignments.add(new long[] { ref.ticket.id, ref.need.index, take });
            left -= take;
        }
        long stray = left;
        if (stray > 0) {
            if (link.isJobAlive(hint.job())) {
                addPool(hint.job(), key, stray);
            } else {
                orphans.merge(key, stray, Long::sum);
            }
        }
        dirty = true;
        for (long[] a : assignments) link.arrived(a[0], (int) a[1], key, a[2], hint);
        if (stray > 0 && link.isJobAlive(hint.job())) link.stray(hint.job(), key, stray, hint);
        return qty;
    }

    /**
     * Needs that can take {@code key}: the hinted ticket first, then other open tickets of the same job, strict
     * matchers before fuzzy ones, lowest height, oldest.
     */
    private List<NeedRef> candidates(ResourceKey key, CraftDelivery hint) {
        List<NeedRef> result = new ArrayList<>();
        for (PipeTicket p : tickets.values()) {
            if (p.job != hint.job() || p.state != PipeTicket.State.OPEN) continue;
            for (NeedState n : p.needs) {
                if (n.remaining() > 0 && n.matcher.matches(key)) result.add(new NeedRef(p, n));
            }
        }
        result.sort(
                Comparator.<NeedRef>comparingInt(r -> r.ticket.id == hint.ticket() ? 0 : 1)
                        .thenComparingInt(r -> r.need.matcher.rule().strictness())
                        .thenComparingInt(r -> r.ticket.height).thenComparingLong(r -> r.ticket.seq));
        return result;
    }

    // ================================================================== tick

    public void tick(long now) {
        insertCommitted(now);
        if (now % OUTPUT_INTERVAL == 0) captureOutputs(now);
        if (now % 20 == 0) {
            for (Iterator<Map.Entry<Long, Map<ResourceKey, Long>>> it = pools.entrySet().iterator(); it.hasNext();) {
                Map.Entry<Long, Map<ResourceKey, Long>> e = it.next();
                if (!link.isJobAlive(e.getKey())) {
                    e.getValue().forEach((k, v) -> orphans.merge(k, v, Long::sum));
                    it.remove();
                    dirty = true;
                }
            }
            for (Iterator<PipeTicket> it = tickets.values().iterator(); it.hasNext();) {
                PipeTicket p = it.next();
                if (link.isJobAlive(p.job)) continue;
                // job gone: buffered items go to storage; a set left in the machine is forgotten so it can't
                // block the blocking modes forever
                for (NeedState n : p.needs) n.buffered.forEach((k, v) -> orphans.merge(k, v, Long::sum));
                for (ResourceAmount r : p.remainder) orphans.merge(r.key(), r.amount(), Long::sum);
                insertQueue.remove(p.id);
                inserted.remove(p.id);
                it.remove();
                dirty = true;
            }
            flushOrphans();
        }
    }

    private void insertCommitted(long now) {
        // sets that went in short: finish them first, the machine is waiting for the rest
        for (Long id : inserted) {
            PipeTicket p = tickets.get(id);
            if (p == null || p.remainder.isEmpty()) continue;
            List<ResourceAmount> notInserted = new ArrayList<>();
            if (machine.insert(p.patternSlot, p.patternId, p.crafts, p.remainder, notInserted)
                    == Machine.InsertResult.INSERTED) {
                p.remainder.clear();
                p.remainder.addAll(notInserted);
                p.lastOutputTick = now;
                dirty = true;
            }
        }
        if (insertQueue.isEmpty()) return;
        BlockingMode mode = machine.mode();
        if (inserted.isEmpty() && mode != BlockingMode.NONE) clearJunk();
        for (Long id : new ArrayList<>(insertQueue)) {
            PipeTicket p = tickets.get(id);
            if (p == null || p.state != PipeTicket.State.COMMITTED) {
                insertQueue.remove(id);
                continue;
            }
            if (p.configError || !p.isComplete() || !mayInsert(p, mode)) continue;
            List<ResourceAmount> stacks = new ArrayList<>();
            for (NeedState n : p.needs) n.buffered.forEach((k, v) -> stacks.add(new ResourceAmount(k, v)));
            List<ResourceAmount> notInserted = new ArrayList<>();
            Machine.InsertResult result = machine.insert(p.patternSlot, p.patternId, p.crafts, stacks, notInserted);
            if (result == Machine.InsertResult.INSERTED) {
                for (NeedState n : p.needs) n.buffered.clear();
                p.remainder.addAll(notInserted);
                p.state = PipeTicket.State.INSERTED;
                p.insertedTick = now;
                p.lastOutputTick = now;
                inserted.addLast(p.id);
                lastInsertedPattern = p.patternId;
                insertQueue.remove(id);
                dirty = true;
                link.inserted(p.id);
                if (mode != BlockingMode.NONE && !sameOutstandingPattern(p.patternId)) break;
            } else if (result == Machine.InsertResult.CONFIG_ERROR) {
                p.configError = true;
                dirty = true;
                link.wrongCraft(p.id, "The machine could not be configured for the pattern");
            }
        }
    }

    /** Plan section 6.4: may this committed set go into the machine now? */
    private boolean mayInsert(PipeTicket p, BlockingMode mode) {
        switch (mode) {
            case NONE:
                return true;
            case SMART:
                if (!inserted.isEmpty() && p.patternId.equals(lastInsertedPattern)
                        && sameOutstandingPattern(p.patternId)) {
                    return true;
                }
                return inserted.isEmpty() && machine.isIdle(p.patternSlot, p.patternId);
            default:
                return inserted.isEmpty() && machine.isIdle(p.patternSlot, p.patternId);
        }
    }

    private boolean sameOutstandingPattern(String patternId) {
        for (Long id : inserted) {
            PipeTicket t = tickets.get(id);
            if (t != null && !t.patternId.equals(patternId)) return false;
        }
        return true;
    }

    /** Plan section 6.5: outputs go to the oldest inserted ticket of the pattern that makes them. */
    private void captureOutputs(long now) {
        if (inserted.isEmpty()) return;
        Set<ResourceKey> chancedSeen = new LinkedHashSet<>();
        for (Iterator<Long> it = inserted.iterator(); it.hasNext();) {
            PipeTicket p = tickets.get(it.next());
            if (p == null) {
                it.remove();
                continue;
            }
            for (Map.Entry<ResourceKey, Long> e : p.expectedRemaining.entrySet()) {
                if (e.getValue() <= 0) continue;
                long got = machine.extract(e.getKey(), e.getValue());
                if (got <= 0) continue;
                e.setValue(e.getValue() - got);
                p.lastOutputTick = now;
                outputCaptured(p, e.getKey(), got);
            }
            for (ResourceKey chanced : p.chanced) {
                if (!chancedSeen.add(chanced)) continue;
                long got = machine.extract(chanced, chanced.maxStackSize());
                if (got > 0) outputCaptured(p, chanced, got);
            }
            // byproducts the pattern doesn't list (GT chanced outputs): leftovers, plan section 5.5
            for (ResourceKey k : unexpectedOutputs()) {
                long got = machine.extract(k, k.maxStackSize());
                if (got > 0) outputCaptured(p, k, got);
            }
            if (!p.remainder.isEmpty()) p.lastOutputTick = now;
            if (p.outputsDone()) {
                it.remove();
                tickets.remove(p.id);
                dirty = true;
            } else if (now - p.lastOutputTick > IDLE_WRONG_CRAFT_TICKS && !p.wrongCraftReported
                    && machine.isIdle(p.patternSlot, p.patternId)) {
                        // plan section 7.1: the machine is done and the expected output never came
                        p.wrongCraftReported = true;
                        link.wrongCraft(p.id, "The machine finished without producing the expected output");
                    }
        }
    }

    /**
     * Extractable items no inserted ticket expects and no pattern here uses as an input (so a slow machine's inputs,
     * molds and circuits are never pulled back out).
     */
    private List<ResourceKey> unexpectedOutputs() {
        List<ResourceKey> result = new ArrayList<>();
        for (ResourceKey k : machine.extractable()) {
            if (machine.isInput(k)) continue;
            boolean expected = false;
            for (Long id : inserted) {
                PipeTicket t = tickets.get(id);
                if (t != null && (t.expectedRemaining.containsKey(k) || t.chanced.contains(k))) {
                    expected = true;
                    break;
                }
            }
            if (!expected) result.add(k);
        }
        return result;
    }

    /**
     * Nothing is running but a blocking machine isn't idle: whatever it holds that no pattern here uses is junk from
     * outside the job (or an earlier wrong craft). It goes to storage so the machine can be used.
     */
    private void clearJunk() {
        for (ResourceKey k : unexpectedOutputs()) {
            long got = machine.extract(k, k.maxStackSize());
            if (got > 0) {
                orphans.merge(k, got, Long::sum);
                dirty = true;
            }
        }
    }

    private void outputCaptured(PipeTicket p, ResourceKey key, long qty) {
        if (link.isJobAlive(p.job)) {
            addPool(p.job, key, qty);
            dirty = true;
            if (!p.cancelled) link.output(p.id, key, qty);
            else link.stray(p.job, key, qty, null);
        } else {
            orphans.merge(key, qty, Long::sum);
            dirty = true;
        }
    }

    private void flushOrphans() {
        for (Iterator<Map.Entry<ResourceKey, Long>> it = orphans.entrySet().iterator(); it.hasNext();) {
            Map.Entry<ResourceKey, Long> e = it.next();
            long sent = link.sendToStorage(e.getKey(), e.getValue());
            if (sent >= e.getValue()) {
                it.remove();
            } else if (sent > 0) {
                e.setValue(e.getValue() - sent);
            }
            if (sent > 0) dirty = true;
        }
    }

    // ================================================================== queries

    /** Tickets this pipe holds, with whether each one is in the machine (for the driver's sync). */
    public Map<Long, Boolean> knownTickets() {
        Map<Long, Boolean> result = new LinkedHashMap<>();
        for (PipeTicket p : tickets.values()) result.put(p.id, p.state == PipeTicket.State.INSERTED);
        return result;
    }

    public Set<Long> poolJobs() {
        return new LinkedHashSet<>(pools.keySet());
    }

    /** Items nobody owns any more (no storage had room). Offered to the network as a provider. */
    public Map<ResourceKey, Long> orphans() {
        return orphans;
    }

    public long takeOrphan(ResourceKey key, long qty) {
        long have = orphans.getOrDefault(key, 0L);
        long take = Math.min(have, qty);
        if (take <= 0) return 0;
        if (take == have) orphans.remove(key);
        else orphans.put(key, have - take);
        dirty = true;
        return take;
    }

    /** Everything the pipe holds, for dropping on break. */
    public Map<ResourceKey, Long> contents() {
        Map<ResourceKey, Long> all = new LinkedHashMap<>(orphans);
        for (Map<ResourceKey, Long> pool : pools.values()) pool.forEach((k, v) -> all.merge(k, v, Long::sum));
        for (PipeTicket p : tickets.values()) {
            for (NeedState n : p.needs) n.buffered.forEach((k, v) -> all.merge(k, v, Long::sum));
            for (ResourceAmount r : p.remainder) all.merge(r.key(), r.amount(), Long::sum);
        }
        return all;
    }

    public void clear() {
        tickets.clear();
        pools.clear();
        orphans.clear();
        insertQueue.clear();
        inserted.clear();
        dirty = true;
    }

    public boolean isEmpty() {
        return tickets.isEmpty() && pools.isEmpty() && orphans.isEmpty();
    }

    /** Per-pattern-slot view for the GUI / HUD. */
    public List<TicketView> view() {
        List<TicketView> result = new ArrayList<>();
        for (PipeTicket p : tickets.values()) {
            long need = 0;
            long have = 0;
            for (NeedState n : p.needs) {
                need += n.qty;
                have += n.qty - n.remaining();
            }
            result.add(new TicketView(p.id, p.job, p.patternSlot, p.state.name(), have, need, p.configError));
        }
        return result;
    }

    public static final class TicketView {

        public final long ticket;
        public final long job;
        public final int patternSlot;
        public final String state;
        public final long buffered;
        public final long needed;
        public final boolean configError;

        TicketView(long ticket, long job, int patternSlot, String state, long buffered, long needed,
                boolean configError) {
            this.ticket = ticket;
            this.job = job;
            this.patternSlot = patternSlot;
            this.state = state;
            this.buffered = buffered;
            this.needed = needed;
            this.configError = configError;
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    private void addPool(long job, ResourceKey key, long qty) {
        if (qty <= 0) return;
        pools.computeIfAbsent(job, k -> new LinkedHashMap<>()).merge(key, qty, Long::sum);
    }

    private void takePool(long job, ResourceKey key, long qty) {
        Map<ResourceKey, Long> pool = pools.get(job);
        if (pool == null) return;
        long next = pool.getOrDefault(key, 0L) - qty;
        if (next <= 0) pool.remove(key);
        else pool.put(key, next);
        if (pool.isEmpty()) pools.remove(job);
    }

    // ================================================================== persistence

    public void writeToNBT(NBTTagCompound tag, KeyCodec codec) {
        NBTTagList list = new NBTTagList();
        for (PipeTicket p : tickets.values()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setLong("id", p.id);
            t.setLong("job", p.job);
            t.setInteger("slot", p.patternSlot);
            t.setString("pattern", p.patternId);
            t.setInteger("height", p.height);
            t.setLong("seq", p.seq);
            t.setByte("state", (byte) p.state.ordinal());
            t.setLong("insertedTick", p.insertedTick);
            t.setBoolean("cancelled", p.cancelled);
            t.setLong("crafts", p.crafts);
            Map<ResourceKey, Long> remainder = new LinkedHashMap<>();
            for (ResourceAmount r : p.remainder) remainder.merge(r.key(), r.amount(), Long::sum);
            t.setTag("remainder", writeAmounts(remainder, codec));
            NBTTagList needs = new NBTTagList();
            for (NeedState n : p.needs) {
                NBTTagCompound nt = codec.writeMatcher(n.matcher);
                nt.setInteger("index", n.index);
                nt.setLong("qty", n.qty);
                nt.setBoolean("consumed", n.consumed);
                nt.setTag("buffered", writeAmounts(n.buffered, codec));
                needs.appendTag(nt);
            }
            t.setTag("needs", needs);
            t.setTag("expected", writeAmounts(p.expectedRemaining, codec));
            NBTTagList chanced = new NBTTagList();
            for (ResourceKey k : p.chanced) chanced.appendTag(codec.writeKey(k));
            t.setTag("chanced", chanced);
            list.appendTag(t);
        }
        tag.setTag("tickets", list);
        NBTTagList poolList = new NBTTagList();
        for (Map.Entry<Long, Map<ResourceKey, Long>> e : pools.entrySet()) {
            NBTTagCompound pt = new NBTTagCompound();
            pt.setLong("job", e.getKey());
            pt.setTag("items", writeAmounts(e.getValue(), codec));
            poolList.appendTag(pt);
        }
        tag.setTag("pools", poolList);
        tag.setTag("orphans", writeAmounts(orphans, codec));
        long[] queue = new long[insertQueue.size()];
        int i = 0;
        for (Long id : insertQueue) queue[i++] = id;
        tag.setString("queue", join(queue));
        long[] ins = new long[inserted.size()];
        i = 0;
        for (Long id : inserted) ins[i++] = id;
        tag.setString("inserted", join(ins));
        if (lastInsertedPattern != null) tag.setString("lastPattern", lastInsertedPattern);
    }

    public void readFromNBT(NBTTagCompound tag, KeyCodec codec) {
        clear();
        NBTTagList list = tag.getTagList("tickets", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            PipeTicket p = new PipeTicket(
                    t.getLong("id"),
                    t.getLong("job"),
                    t.getInteger("slot"),
                    t.getString("pattern"),
                    t.getInteger("height"),
                    t.getLong("seq"));
            p.state = PipeTicket.State.values()[t.getByte("state")];
            p.insertedTick = t.getLong("insertedTick");
            p.lastOutputTick = p.insertedTick;
            p.cancelled = t.getBoolean("cancelled");
            p.crafts = Math.max(1, t.getLong("crafts"));
            Map<ResourceKey, Long> remainder = new LinkedHashMap<>();
            readAmounts(t.getTagList("remainder", 10), codec, remainder);
            remainder.forEach((k, v) -> p.remainder.add(new ResourceAmount(k, v)));
            NBTTagList needs = t.getTagList("needs", 10);
            for (int j = 0; j < needs.tagCount(); j++) {
                NBTTagCompound nt = needs.getCompoundTagAt(j);
                ResourceMatcher m = codec.readMatcher(nt);
                NeedState n = new NeedState(nt.getInteger("index"), m, nt.getLong("qty"), nt.getBoolean("consumed"));
                readAmounts(nt.getTagList("buffered", 10), codec, n.buffered);
                if (m == null) {
                    // item no longer exists: whatever is buffered becomes an orphan
                    n.buffered.forEach((k, v) -> orphans.merge(k, v, Long::sum));
                    continue;
                }
                p.needs.add(n);
            }
            readAmounts(t.getTagList("expected", 10), codec, p.expectedRemaining);
            NBTTagList chanced = t.getTagList("chanced", 10);
            for (int j = 0; j < chanced.tagCount(); j++) {
                ResourceKey k = codec.read(chanced.getCompoundTagAt(j));
                if (k != null) p.chanced.add(k);
            }
            tickets.put(p.id, p);
        }
        NBTTagList poolList = tag.getTagList("pools", 10);
        for (int i = 0; i < poolList.tagCount(); i++) {
            NBTTagCompound pt = poolList.getCompoundTagAt(i);
            Map<ResourceKey, Long> pool = new LinkedHashMap<>();
            readAmounts(pt.getTagList("items", 10), codec, pool);
            if (!pool.isEmpty()) pools.put(pt.getLong("job"), pool);
        }
        readAmounts(tag.getTagList("orphans", 10), codec, orphans);
        for (long id : split(tag.getString("queue"))) {
            if (tickets.containsKey(id)) insertQueue.add(id);
        }
        for (long id : split(tag.getString("inserted"))) {
            if (tickets.containsKey(id)) inserted.addLast(id);
        }
        lastInsertedPattern = tag.hasKey("lastPattern") ? tag.getString("lastPattern") : null;
        dirty = false;
    }

    private static NBTTagList writeAmounts(Map<ResourceKey, Long> amounts, KeyCodec codec) {
        NBTTagList list = new NBTTagList();
        amounts.forEach((k, v) -> {
            if (v <= 0) return;
            NBTTagCompound e = codec.writeKey(k);
            e.setLong("qty", v);
            list.appendTag(e);
        });
        return list;
    }

    private static void readAmounts(NBTTagList list, KeyCodec codec, Map<ResourceKey, Long> into) {
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            ResourceKey k = codec.read(e);
            if (k != null && e.getLong("qty") > 0) into.merge(k, e.getLong("qty"), Long::sum);
        }
    }

    private static String join(long[] ids) {
        StringBuilder sb = new StringBuilder();
        for (long id : ids) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        return sb.toString();
    }

    private static long[] split(String s) {
        if (s == null || s.isEmpty()) return new long[0];
        String[] parts = s.split(",");
        long[] ids = new long[parts.length];
        for (int i = 0; i < parts.length; i++) ids[i] = Long.parseLong(parts[i]);
        return ids;
    }

    // ================================================================== state

    private static final class PipeTicket {

        enum State {
            OPEN,
            COMMITTED,
            INSERTED
        }

        final long id;
        final long job;
        final int patternSlot;
        final String patternId;
        final int height;
        final long seq;
        final List<NeedState> needs = new ArrayList<>();
        final Map<ResourceKey, Long> expectedRemaining = new LinkedHashMap<>();
        final List<ResourceKey> chanced = new ArrayList<>();
        /** Part of an inserted set that didn't fit; inserted before anything else. */
        final List<ResourceAmount> remainder = new ArrayList<>();
        long crafts = 1;
        State state = State.OPEN;
        long insertedTick;
        long lastOutputTick;
        boolean configError;
        boolean cancelled;
        boolean wrongCraftReported;

        PipeTicket(long id, long job, int patternSlot, String patternId, int height, long seq) {
            this.id = id;
            this.job = job;
            this.patternSlot = patternSlot;
            this.patternId = patternId;
            this.height = height;
            this.seq = seq;
        }

        NeedState need(int index) {
            for (NeedState n : needs) {
                if (n.index == index) return n;
            }
            return null;
        }

        boolean isComplete() {
            for (NeedState n : needs) {
                if (n.remaining() > 0) return false;
            }
            return true;
        }

        boolean outputsDone() {
            for (long v : expectedRemaining.values()) {
                if (v > 0) return false;
            }
            return true;
        }
    }

    private static final class NeedState {

        final int index;
        final ResourceMatcher matcher;
        final long qty;
        final boolean consumed;
        final Map<ResourceKey, Long> buffered = new HashMap<>();

        NeedState(int index, ResourceMatcher matcher, long qty, boolean consumed) {
            this.index = index;
            this.matcher = matcher;
            this.qty = qty;
            this.consumed = consumed;
        }

        long remaining() {
            long have = 0;
            for (long v : buffered.values()) have += v;
            return qty - have;
        }
    }

    private static final class NeedRef {

        final PipeTicket ticket;
        final NeedState need;

        NeedRef(PipeTicket ticket, NeedState need) {
            this.ticket = ticket;
            this.need = need;
        }
    }
}
