package logisticspipes.crafting.v3.exec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.github.bsideup.jabel.Desugar;

import logisticspipes.crafting.v3.model.BlockingMode;
import logisticspipes.crafting.v3.model.ResourceAmount;
import logisticspipes.crafting.v3.model.ResourceKey;
import logisticspipes.crafting.v3.model.ResourceMatcher;
import logisticspipes.crafting.v3.plan.InputResolution;
import logisticspipes.crafting.v3.plan.Plan;
import logisticspipes.crafting.v3.plan.PlanNode;
import logisticspipes.crafting.v3.plan.SourceClaim;
import logisticspipes.crafting.v3.plan.StockView;

/**
 * The single authority over what gets crafted, where and when (plan sections 4, 5, 7, 8). Runs on the server thread.
 * Crafting pipes never craft on their own: they buffer what the driver routes to them, insert when told to, and report
 * back.
 * <p>
 * Bookkeeping rule: for every unfinished ticket and every need, the records bound to it add up to the need. Anything
 * that breaks that (arrivals at the "wrong" ticket, strays, losses) is repaired by {@link #rebalance}: excess is
 * unbound, a shortfall becomes an UNSOURCED record, and {@link #source} finds it a new source: the job's own unbound
 * items first, then provider stock.
 */
public final class CraftingDriver {

    /** Tunables; times in ticks. */
    public static final class Config {

        /** Tickets open while their height is at most the lowest unfinished height plus this. */
        public int openWindow = 1;
        public int maxOpenPerCrafter = 32;
        public int maxShipmentsPerTick = 128;
        public int maxSourcingPerTick = 256;
        public int maxCommitsPerCrafterPerTick = 8;
        public long transitTimeout = 20 * 60 * 5;
        public long commitResend = 20 * 30;
        public long insertWarning = 20 * 60;
        public int retryMin = 40;
        public int retryMax = 600;
        public long windDownTimeout = 20 * 60 * 5;
    }

    public final Config config;
    private final DriverWorld world;
    private long nextId = 1;
    private final Ledger ledger = new Ledger(this::nextId);
    private final Map<Long, CraftingJob> jobs = new LinkedHashMap<>();
    private final Map<Long, Ticket> tickets = new HashMap<>();
    private final Map<Long, Shipment> shipments = new LinkedHashMap<>();
    private final Map<UUID, CrafterState> crafters = new HashMap<>();
    private int shipmentsThisTick;
    private boolean dirty;

    public CraftingDriver(DriverWorld world) {
        this(world, new Config());
    }

    public CraftingDriver(DriverWorld world, Config config) {
        this.world = world;
        this.config = config;
    }

    long nextId() {
        return nextId++;
    }

    // ================================================================== accessors

    public Ledger ledger() {
        return ledger;
    }

    public ReservationIndex reservations() {
        return ledger.reservations();
    }

    public Collection<CraftingJob> jobs() {
        return Collections.unmodifiableCollection(jobs.values());
    }

    public CraftingJob job(long id) {
        return jobs.get(id);
    }

    public Ticket ticket(long id) {
        return tickets.get(id);
    }

    public Shipment shipment(long id) {
        return shipments.get(id);
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    public boolean isJobAlive(long job) {
        CraftingJob j = jobs.get(job);
        return j != null && !j.state.isOver();
    }

    // ================================================================== commit (plan section 4)

    /**
     * Turns a plan into a running job: tickets for every node, ledger records for every claim, reservations for
     * provider stock. Provider claims are re-checked against live stock; shortfalls start UNSOURCED and are re-sourced
     * on the next tick.
     */
    public CraftingJob startJob(Plan plan, UUID player) {
        if (!plan.canStart()) throw new IllegalStateException("plan has missing items: " + plan.issues);
        long now = world.now();
        CraftingJob job = new CraftingJob(
                nextId(),
                plan.request.target(),
                plan.request.qty(),
                plan.request.requester(),
                player,
                now);
        long seq = 0;
        Map<Integer, List<Ticket>> byNode = new HashMap<>();
        List<PlanNode> nodes = new ArrayList<>(plan.nodes.values());
        nodes.sort(Comparator.comparingInt(n -> n.height));
        for (PlanNode node : nodes) {
            List<Ticket> list = new ArrayList<>();
            long batch = Math.max(1, node.crafter.batchLimit());
            long left = node.crafts;
            List<ResourceKey> chanced = new ArrayList<>();
            for (ResourceAmount c : node.pattern.chanced()) chanced.add(c.key());
            for (int i = 0; left > 0; i++) {
                long crafts = Math.min(batch, left);
                left -= crafts;
                List<Need> needs = new ArrayList<>();
                for (int idx = 0; idx < node.inputs.size(); idx++) {
                    InputResolution res = node.inputs.get(idx);
                    long q = res.spec.consumed() ? res.spec.qty() * crafts : (i == 0 ? res.spec.qty() : 0);
                    if (q > 0) {
                        needs.add(new Need(idx, res.spec.matcher(), q, res.spec.consumed(), res.spec.target()));
                    }
                }
                Map<ResourceKey, Long> outputs = new LinkedHashMap<>();
                for (ResourceAmount o : node.pattern.outputs()) outputs.put(o.key(), o.amount() * crafts);
                Ticket t = new Ticket(
                        nextId(),
                        job.id,
                        Ticket.Kind.CRAFT,
                        node.id,
                        node.crafter.id(),
                        node.patternSlot,
                        node.pattern.id(),
                        crafts,
                        node.height,
                        seq++,
                        node.crafter.legacy(),
                        needs,
                        outputs,
                        chanced);
                t.stateSince = now;
                list.add(t);
            }
            byNode.put(node.id, list);
            job.nodes.add(
                    new CraftingJob.NodeInfo(
                            node.id,
                            node.pattern.primaryOutput(),
                            node.crafts,
                            node.crafter.id(),
                            node.crafter.name(),
                            node.crafter.legacy(),
                            node.height));
        }
        Ticket delivery = new Ticket(
                nextId(),
                job.id,
                Ticket.Kind.DELIVERY,
                -1,
                plan.request.requester(),
                -1,
                null,
                0,
                plan.maxHeight() + 1,
                seq,
                false,
                Collections.singletonList(new Need(0, plan.request.target(), plan.request.qty(), true)),
                Collections.emptyMap(),
                null);
        delivery.stateSince = now;
        delivery.state = TicketState.OPEN;
        job.deliveryTicket = delivery.id;

        jobs.put(job.id, job);
        for (List<Ticket> list : byNode.values()) {
            for (Ticket t : list) register(job, t);
        }
        register(job, delivery);
        job.active.add(delivery);

        for (PlanNode node : nodes) {
            List<Ticket> list = byNode.get(node.id);
            for (int idx = 0; idx < node.inputs.size(); idx++) {
                distribute(job, node.inputs.get(idx).sources, slotsFor(list, idx));
            }
            for (Map.Entry<ResourceKey, Long> s : node.surplus.entrySet()) {
                if (s.getValue() <= 0) continue;
                ledger.create(job.id, s.getKey(), s.getValue(), RecordState.EXPECTED, r -> r.producedBy = node.id);
            }
        }
        distribute(job, plan.root.sources, slotsFor(Collections.singletonList(delivery), 0));
        for (Ticket t : job.tickets.values()) rebalance(t);
        dirty = true;
        return job;
    }

    private void register(CraftingJob job, Ticket t) {
        job.addTicket(t);
        tickets.put(t.id, t);
        if (t.kind == Ticket.Kind.CRAFT) job.planned.add(t);
    }

    @Desugar
    private record Slot(Ticket ticket, int need, long qty) {}

    private static List<Slot> slotsFor(List<Ticket> list, int needIndex) {
        List<Slot> slots = new ArrayList<>();
        for (Ticket t : list) {
            Need n = t.need(needIndex);
            if (n != null) slots.add(new Slot(t, needIndex, n.qty));
        }
        return slots;
    }

    /** Spreads a resolution's claims over the tickets' needs, in ticket order. */
    private void distribute(CraftingJob job, List<SourceClaim> sources, List<Slot> slots) {
        int slotIdx = 0;
        long slotLeft = slots.isEmpty() ? 0 : slots.get(0).qty;
        for (SourceClaim claim : sources) {
            long claimLeft = claim.qty();
            while (claimLeft > 0 && slotIdx < slots.size()) {
                if (slotLeft == 0) {
                    slotIdx++;
                    if (slotIdx >= slots.size()) break;
                    slotLeft = slots.get(slotIdx).qty;
                    continue;
                }
                long take = Math.min(claimLeft, slotLeft);
                Slot slot = slots.get(slotIdx);
                createFromClaim(job, claim, take, slot.ticket, slot.need);
                claimLeft -= take;
                slotLeft -= take;
            }
        }
    }

    private void createFromClaim(CraftingJob job, SourceClaim claim, long qty, Ticket t, int need) {
        if (claim instanceof SourceClaim.Provider) {
            SourceClaim.Provider p = (SourceClaim.Provider) claim;
            long available = Math.max(0, world.availableAt(p.provider(), p.key()));
            long reserve = Math.min(qty, available);
            if (reserve > 0) {
                ledger.create(job.id, p.key(), reserve, RecordState.RESERVED, r -> bind(r, t, need, p.provider()));
            }
            if (qty > reserve) {
                ledger.create(job.id, p.key(), qty - reserve, RecordState.UNSOURCED, r -> bind(r, t, need, null));
            }
        } else if (claim instanceof SourceClaim.Surplus) {
            int producer = ((SourceClaim.Surplus) claim).producer();
            ledger.create(job.id, claim.key(), qty, RecordState.EXPECTED, r -> {
                bind(r, t, need, null);
                r.producedBy = producer;
            });
        } else if (claim instanceof SourceClaim.Craft) {
            int child = ((SourceClaim.Craft) claim).child();
            ledger.create(job.id, claim.key(), qty, RecordState.EXPECTED, r -> {
                bind(r, t, need, null);
                r.producedBy = child;
            });
        } else {
            boolean unreachable = ((SourceClaim.Unresolved) claim).unreachable();
            ledger.create(job.id, claim.key(), qty, RecordState.UNSOURCED, r -> {
                bind(r, t, need, null);
                r.cause = unreachable ? StallCause.UNREACHABLE : StallCause.MISSING;
            });
        }
    }

    private static void bind(LedgerRecord r, Ticket t, int need, UUID location) {
        r.ticket = t.id;
        r.need = need;
        r.location = location;
    }

    // ================================================================== tick (plan section 5)

    public void tick() {
        long now = world.now();
        shipmentsThisTick = 0;
        List<CraftingJob> snapshot = new ArrayList<>(jobs.values());
        for (CraftingJob job : snapshot) {
            if (job.state == JobState.RUNNING) openTickets(job, now);
        }
        sourceUnsourced(now);
        for (CraftingJob job : snapshot) {
            if (job.state != JobState.RUNNING) continue;
            for (Ticket t : new ArrayList<>(job.active)) {
                if (t.state == TicketState.OPEN) moveInputs(job, t, now);
            }
        }
        commits(now);
        timeouts(now);
        for (CraftingJob job : snapshot) {
            if (job.state == JobState.CANCELLING) windDown(job, now);
            else if (job.state == JobState.RUNNING) notifyStalls(job);
        }
    }

    // ---------------------------------------------------------------- opening (5.1)

    private void openTickets(CraftingJob job, long now) {
        int min = job.minUnfinishedHeight();
        long limit = min == Integer.MAX_VALUE ? Long.MAX_VALUE : (long) min + config.openWindow;
        for (Iterator<Ticket> it = job.planned.iterator(); it.hasNext();) {
            Ticket t = it.next();
            if (t.height > limit) break;
            CrafterState cs = crafterState(t.crafter);
            if (cs.open.size() >= config.maxOpenPerCrafter || cs.blockedJobs.contains(job.id)) continue;
            CrafterPort port = world.crafter(t.crafter);
            if (port == null) {
                setProblem(job, t, "Crafter is not loaded");
                continue;
            }
            it.remove();
            setState(job, t, TicketState.OPEN, now);
            t.problem = null;
            cs.open.add(t.id);
            job.active.add(t);
            if (!t.legacy) {
                port.open(t);
                for (LedgerRecord r : ledger.forTicket(t.id)) {
                    if (r.state == RecordState.BUFFERED && t.crafter.equals(r.location)) assignHeld(port, t, r);
                }
            }
            checkComplete(job, t, now);
        }
    }

    /** Moves held items (BUFFERED in the job pool) into the open ticket at the same crafter. */
    private void assignHeld(CrafterPort port, Ticket t, LedgerRecord r) {
        long moved = port.assign(t.job, r.key, r.qty, t, r.need);
        if (moved < r.qty) {
            // the crafter doesn't have them any more
            LedgerRecord lost = ledger.split(r, r.qty - moved);
            ledger.remove(lost);
            rebalance(t);
        }
    }

    // ---------------------------------------------------------------- sourcing / retry scheduler (7.2)

    private void sourceUnsourced(long now) {
        int budget = config.maxSourcingPerTick;
        for (LedgerRecord r : ledger.unsourced()) {
            if (budget <= 0) break;
            if (r.nextRetry > now || ledger.get(r.id) != r) continue;
            Ticket t = tickets.get(r.ticket);
            CraftingJob job = t == null ? null : jobs.get(t.job);
            if (t == null || job == null || job.state != JobState.RUNNING || t.state.isFinished()) {
                ledger.remove(r);
                continue;
            }
            budget--;
            source(job, t, r, now);
        }
    }

    /** Finds a source for an UNSOURCED record: the job's unbound items first, then provider stock. */
    private void source(CraftingJob job, Ticket t, LedgerRecord r, long now) {
        Need need = t.need(r.need);
        if (need == null) {
            ledger.remove(r);
            return;
        }
        ResourceMatcher m = need.matcher;
        UUID dest = destination(t, r.need);
        long left = r.qty;
        for (LedgerRecord u : ledger.unbound(job.id)) {
            if (left <= 0) break;
            if (!m.matches(u.key) || !canAdopt(job, t, u, dest)) continue;
            LedgerRecord part = ledger.split(u, Math.min(left, u.qty));
            boolean held = part.state == RecordState.LEFTOVER || part.state == RecordState.BUFFERED;
            ledger.mutate(part, x -> {
                x.ticket = t.id;
                x.need = r.need;
                if (held) x.state = RecordState.BUFFERED;
            });
            left -= part.qty;
            if (held && dest.equals(part.location) && t.state == TicketState.OPEN && !t.legacy) {
                CrafterPort port = world.crafter(dest);
                if (port != null) assignHeld(port, t, part);
            }
        }
        boolean anyStock = false;
        if (left > 0) {
            List<StockView.Entry> entries = new ArrayList<>(world.availableStock(m, dest));
            entries.sort(Comparator.comparingLong(StockView.Entry::available).reversed());
            for (StockView.Entry e : entries) {
                if (left <= 0) break;
                if (e.available() <= 0) continue;
                anyStock = true;
                if (!reachable(e.location(), dest, e.key())) continue;
                long take = Math.min(left, e.available());
                ledger.create(job.id, e.key(), take, RecordState.RESERVED, x -> bind(x, t, r.need, e.location()));
                left -= take;
            }
        }
        if (ledger.get(r.id) == null) return;
        long consumed = r.qty - left;
        if (left <= 0) {
            ledger.remove(r);
        } else {
            StallCause cause = anyStock ? StallCause.UNREACHABLE : StallCause.MISSING;
            int delay = r.retryDelay <= 0 ? config.retryMin : Math.min(config.retryMax, r.retryDelay * 2);
            ledger.mutate(r, x -> {
                x.qty -= consumed;
                x.cause = cause;
                x.retryDelay = delay;
                x.nextRetry = now + delay;
            });
        }
        if (consumed > 0) touch(job);
        checkComplete(job, t, now);
    }

    private boolean canAdopt(CraftingJob job, Ticket t, LedgerRecord u, UUID dest) {
        switch (u.state) {
            case LEFTOVER:
            case BUFFERED:
                return dest.equals(u.location) || reachable(u.location, dest, u.key);
            case IN_TRANSIT:
                return dest.equals(u.location);
            case EXPECTED: {
                // the producer must sit below the consumer, or the job could wait on itself
                if (u.shipment >= 0) return false;
                if (t.isDelivery()) return true;
                CraftingJob.NodeInfo producer = nodeInfo(job, u.producedBy);
                return producer != null && producer.height() < t.height && hasUnfinishedTicket(job, u.producedBy);
            }
            default:
                return false;
        }
    }

    /** Re-tries every stalled record now (inventory, chunk or routing change). */
    public void retryStalled() {
        long now = world.now();
        for (LedgerRecord r : ledger.unsourced()) {
            ledger.mutate(r, x -> {
                x.nextRetry = now;
                x.retryDelay = 0;
            });
        }
    }

    // ---------------------------------------------------------------- routing (5.2)

    private void moveInputs(CraftingJob job, Ticket t, long now) {
        for (LedgerRecord r : ledger.forTicket(t.id)) {
            if (shipmentsThisTick >= config.maxShipmentsPerTick) return;
            if (ledger.get(r.id) != r) continue;
            switch (r.state) {
                case RESERVED:
                    shipFromProvider(job, t, r, now);
                    break;
                case BUFFERED:
                    if (!destination(t, r.need).equals(r.location)) shipFromCrafter(job, t, r, now);
                    break;
                default:
                    break;
            }
        }
    }

    private void shipFromProvider(CraftingJob job, Ticket t, LedgerRecord r, long now) {
        UUID provider = r.location;
        UUID dest = destination(t, r.need);
        if (!reachable(provider, dest, r.key)) {
            unsourceNow(r, StallCause.UNREACHABLE);
            return;
        }
        long sent = ship(job, r, dest, now, (q, info) -> world.sendFromProvider(provider, r.key, q, dest, info));
        if (sent > 0) return;
        long physical = world.physicalAt(provider, r.key);
        long reserved = ledger.reservations().reserved(provider, r.key);
        if (physical >= 0 && physical < reserved) {
            // items left the provider behind our back: re-source the part that's gone
            long gone = Math.min(r.qty, reserved - physical);
            unsourceNow(ledger.split(r, gone), StallCause.NONE);
        }
    }

    private void shipFromCrafter(CraftingJob job, Ticket t, LedgerRecord r, long now) {
        UUID from = r.location;
        UUID dest = destination(t, r.need);
        CrafterPort port = world.crafter(from);
        if (port == null) return;
        if (!reachable(from, dest, r.key)) {
            setProblem(job, t, "No route from a crafter holding its inputs");
            return;
        }
        long sent = ship(job, r, dest, now, (q, info) -> port.ship(t.job, r.key, q, dest, info));
        if (sent > 0) return;
        long pooled = port.pooled(t.job, r.key);
        long recorded = heldAt(job.id, from, r.key);
        if (pooled < recorded) {
            long gone = Math.min(r.qty, recorded - pooled);
            ledger.remove(ledger.split(r, gone));
            rebalance(t);
        }
    }

    /** Amount the ledger says the job pool at {@code location} holds (held outputs and leftovers). */
    private long heldAt(long job, UUID location, ResourceKey key) {
        long sum = 0;
        for (LedgerRecord r : ledger.forJob(job)) {
            if (!key.equals(r.key) || !location.equals(r.location)) continue;
            if (r.state == RecordState.LEFTOVER) sum += r.qty;
            if (r.state == RecordState.BUFFERED) {
                Ticket bound = tickets.get(r.ticket);
                if (bound == null || !location.equals(bound.crafter) || !bound.state.isOpenAtCrafter()) sum += r.qty;
            }
        }
        return sum;
    }

    private interface Sender {

        long send(long qty, CraftDelivery info);
    }

    /**
     * Marks up to a stack of {@code r} as in transit under a new shipment, then sends. Records are switched first so an
     * instant arrival finds them; whatever didn't leave is switched back.
     */
    private long ship(CraftingJob job, LedgerRecord r, UUID dest, long now, Sender sender) {
        long q = Math.min(r.qty, Math.max(1, r.key.maxStackSize()));
        RecordState prevState = r.state;
        UUID prevLocation = r.location;
        Shipment s = new Shipment(nextId(), job.id, prevLocation, dest, r.key, q, now, -1);
        shipments.put(s.id, s);
        LedgerRecord part = ledger.split(r, q);
        ledger.mutate(part, x -> {
            x.state = RecordState.IN_TRANSIT;
            x.location = dest;
            x.shipment = s.id;
        });
        long sent = Math.max(0, Math.min(q, sender.send(q, new CraftDelivery(job.id, s.id, r.ticket))));
        if (sent < q && ledger.get(part.id) == part && part.state == RecordState.IN_TRANSIT) {
            LedgerRecord back = ledger.split(part, Math.min(part.qty, q - sent));
            ledger.mutate(back, x -> {
                x.state = prevState;
                x.location = prevLocation;
                x.shipment = -1;
            });
        }
        if (sent <= 0) {
            shipments.remove(s.id);
        } else {
            shipmentsThisTick++;
            s.qty = Math.min(s.qty, sent);
            touch(job);
        }
        return sent;
    }

    // ---------------------------------------------------------------- commits (5.4)

    private void commits(long now) {
        for (Map.Entry<UUID, CrafterState> e : crafters.entrySet()) {
            CrafterState cs = e.getValue();
            if (cs.complete.isEmpty()) continue;
            CrafterPort port = world.crafter(e.getKey());
            if (port == null) continue;
            BlockingMode mode = port.mode();
            int budget = config.maxCommitsPerCrafterPerTick;
            for (Iterator<Ticket> it = cs.complete.iterator(); it.hasNext() && budget > 0;) {
                Ticket t = it.next();
                CraftingJob job = jobs.get(t.job);
                if (job == null || job.state != JobState.RUNNING || cs.blockedJobs.contains(t.job)) continue;
                boolean allowed;
                if (cs.outstanding.isEmpty() || mode == BlockingMode.NONE) {
                    allowed = true;
                } else if (mode == BlockingMode.SMART) {
                    allowed = t.patternId.equals(cs.outstandingPattern());
                } else {
                    allowed = false;
                }
                if (!allowed) {
                    if (mode == BlockingMode.BLOCKING) break;
                    continue;
                }
                it.remove();
                setState(job, t, TicketState.COMMITTED, now);
                cs.outstanding.put(t.id, now);
                cs.outstandingPatterns.merge(t.patternId, 1, Integer::sum);
                port.commit(t);
                budget--;
            }
        }
    }

    // ---------------------------------------------------------------- timeouts and resends (6.1, 7.1)

    private void timeouts(long now) {
        for (Shipment s : new ArrayList<>(shipments.values())) {
            if (now - s.sentTick > config.transitTimeout) lostInternal(s, s.qty);
        }
        for (Map.Entry<UUID, CrafterState> e : crafters.entrySet()) {
            CrafterState cs = e.getValue();
            if (cs.outstanding.isEmpty()) continue;
            CrafterPort port = world.crafter(e.getKey());
            for (Map.Entry<Long, Long> o : cs.outstanding.entrySet()) {
                Ticket t = tickets.get(o.getKey());
                if (t == null) continue;
                long waited = now - t.stateSince;
                if (waited > config.insertWarning && t.problem == null) {
                    setProblem(jobs.get(t.job), t, "Waiting for the machine to accept the set");
                }
                if (port != null && now - o.getValue() > config.commitResend) {
                    o.setValue(now);
                    port.commit(t);
                }
            }
        }
    }

    // ================================================================== crafter -> driver (6.1)

    /**
     * Items arrived at a crafter and the crafter assigned them to {@code ticket}'s need. {@code hint} is what the items
     * carried (may be null or name another ticket).
     */
    public void onArrived(UUID crafter, long ticketId, int needIndex, ResourceKey key, long qty, CraftDelivery hint) {
        Ticket t = tickets.get(ticketId);
        if (t == null || qty <= 0) return;
        CraftingJob job = jobs.get(t.job);
        if (job == null) return;
        long now = world.now();
        Set<Ticket> touched = new LinkedHashSet<>();
        touched.add(t);
        long left = qty;
        Shipment s = hint == null ? null : shipments.get(hint.shipment());
        if (s != null) {
            List<LedgerRecord> carried = ledger.forShipment(s.id);
            // records already meant for this ticket's need first
            carried.sort(
                    Comparator.comparingInt((LedgerRecord r) -> r.ticket == ticketId && r.need == needIndex ? 0 : 1));
            for (LedgerRecord r : carried) {
                if (left <= 0) break;
                if (!key.equals(r.key)) continue;
                LedgerRecord part = ledger.split(r, Math.min(left, r.qty));
                Ticket previous = tickets.get(part.ticket);
                if (previous != null && previous != t) touched.add(previous);
                ledger.mutate(part, x -> {
                    x.state = RecordState.BUFFERED;
                    x.location = crafter;
                    x.ticket = ticketId;
                    x.need = needIndex;
                    x.shipment = -1;
                    x.producedBy = -1;
                });
                left -= part.qty;
                arrivedFromShipment(s, part.qty, now);
            }
        }
        if (left > 0) {
            long extra = left;
            ledger.create(t.job, key, extra, RecordState.BUFFERED, x -> bind(x, t, needIndex, crafter));
        }
        for (Ticket x : touched) rebalance(x);
        touch(job);
        checkComplete(job, t, now);
    }

    /** Items arrived that match no open need; the crafter keeps them in the job pool. */
    public void onStray(UUID crafter, long jobId, ResourceKey key, long qty, CraftDelivery hint) {
        CraftingJob job = jobs.get(jobId);
        if (job == null || qty <= 0) return;
        long now = world.now();
        long left = qty;
        Shipment s = hint == null ? null : shipments.get(hint.shipment());
        Set<Ticket> touched = new LinkedHashSet<>();
        if (s != null) {
            for (LedgerRecord r : ledger.forShipment(s.id)) {
                if (left <= 0) break;
                if (!key.equals(r.key)) continue;
                LedgerRecord part = ledger.split(r, Math.min(left, r.qty));
                Ticket previous = tickets.get(part.ticket);
                if (previous != null) touched.add(previous);
                ledger.mutate(part, x -> {
                    x.state = RecordState.LEFTOVER;
                    x.location = crafter;
                    x.ticket = LedgerRecord.UNBOUND;
                    x.need = -1;
                    x.shipment = -1;
                    x.producedBy = -1;
                });
                left -= part.qty;
                arrivedFromShipment(s, part.qty, now);
            }
        }
        if (left > 0) {
            long extra = left;
            ledger.create(jobId, key, extra, RecordState.LEFTOVER, x -> x.location = crafter);
        }
        for (Ticket x : touched) rebalance(x);
        touch(job);
    }

    /**
     * Items carrying {@code hint} reached a destination that isn't a v3 crafting pipe: the requester, or a legacy
     * crafter (where they went straight into the machine).
     */
    public void onDelivered(UUID at, ResourceKey key, long qty, CraftDelivery hint) {
        if (hint == null || qty <= 0) return;
        CraftingJob job = jobs.get(hint.job());
        Shipment s = shipments.get(hint.shipment());
        if (job == null || s == null) return;
        long now = world.now();
        long left = qty;
        Set<Ticket> touched = new LinkedHashSet<>();
        for (LedgerRecord r : ledger.forShipment(s.id)) {
            if (left <= 0) break;
            if (!key.equals(r.key)) continue;
            LedgerRecord part = ledger.split(r, Math.min(left, r.qty));
            left -= part.qty;
            arrivedFromShipment(s, part.qty, now);
            Ticket t = tickets.get(part.ticket);
            if (t != null && t.isDelivery() && t.crafter.equals(at)) {
                ledger.remove(part);
                t.delivered += part.qty;
            } else if (t != null && t.legacy && destination(t, part.need).equals(at) && !t.state.isFinished()) {
                ledger.mutate(part, x -> {
                    x.state = RecordState.INSERTED;
                    x.location = at;
                    x.shipment = -1;
                    x.producedBy = -1;
                });
                legacyCheckInserted(job, t, now);
            } else {
                // ended up somewhere we didn't send it: out of our hands
                ledger.remove(part);
                if (t != null) touched.add(t);
            }
        }
        for (Ticket t : touched) rebalance(t);
        touch(job);
        Ticket delivery = job.delivery();
        if (delivery != null && delivery.delivered >= job.qty && !job.delivered) {
            job.delivered = true;
            setState(job, delivery, TicketState.DONE, now);
            beginWindDown(job, now);
        }
    }

    /** Routed items carrying {@code hint} were destroyed, dropped or re-routed elsewhere. */
    public void onLost(CraftDelivery hint, ResourceKey key, long qty) {
        if (hint == null) return;
        Shipment s = shipments.get(hint.shipment());
        if (s == null) return;
        lostInternal(s, qty);
    }

    private void lostInternal(Shipment s, long qty) {
        long now = world.now();
        long left = qty;
        Set<Ticket> touched = new LinkedHashSet<>();
        for (LedgerRecord r : ledger.forShipment(s.id)) {
            if (left <= 0) break;
            LedgerRecord part = ledger.split(r, Math.min(left, r.qty));
            left -= part.qty;
            Ticket t = tickets.get(part.ticket);
            if (t != null) touched.add(t);
            ledger.remove(part);
            arrivedFromShipment(s, part.qty, now);
        }
        if (left > 0) arrivedFromShipment(s, left, now);
        for (Ticket t : touched) rebalance(t);
        CraftingJob job = jobs.get(s.job);
        if (job != null) touch(job);
    }

    private void arrivedFromShipment(Shipment s, long qty, long now) {
        s.qty -= qty;
        if (s.producerTicket >= 0) {
            Ticket producer = tickets.get(s.producerTicket);
            if (producer != null) {
                producer.outputsRemaining.computeIfPresent(s.key, (k, v) -> Math.max(0, v - qty));
                if (allOutputsCaptured(producer) && !producer.state.isFinished()) {
                    finishTicket(jobs.get(producer.job), producer, now);
                }
            }
        }
        if (s.qty <= 0 && !ledger.hasShipment(s.id)) shipments.remove(s.id);
    }

    /** The crafter reports the ticket's set is in the machine. */
    public void onInserted(long ticketId) {
        Ticket t = tickets.get(ticketId);
        if (t == null || t.state != TicketState.COMMITTED) return;
        CraftingJob job = jobs.get(t.job);
        long now = world.now();
        setState(job, t, TicketState.INSERTED, now);
        t.problem = null;
        CrafterState cs = crafterState(t.crafter);
        releaseCommit(cs, t);
        cs.open.remove(t.id);
        job.active.remove(t);
        for (LedgerRecord r : ledger.forTicket(t.id)) {
            if (r.state == RecordState.BUFFERED) ledger.mutate(r, x -> x.state = RecordState.INSERTED);
        }
    }

    /**
     * The crafter captured {@code qty} of {@code key} from its machine and attributed it to {@code ticketId}. The items
     * are now in the job pool.
     */
    public void onOutput(long ticketId, ResourceKey key, long qty) {
        Ticket t = tickets.get(ticketId);
        if (t == null || qty <= 0) return;
        CraftingJob job = jobs.get(t.job);
        if (job == null) return;
        long now = world.now();
        long expected = t.outputsRemaining.getOrDefault(key, 0L);
        long take = Math.min(qty, expected);
        if (take > 0) {
            t.outputsRemaining.put(key, expected - take);
            long unconverted = convertExpected(job, t, key, take);
            if (unconverted > 0) leftover(job, t.crafter, key, unconverted);
        }
        long extra = qty - take;
        if (extra > 0) {
            // chanced or unlisted byproducts: leftovers. A wrong craft is when the expected output never comes,
            // which the crafter reports through onWrongCraft.
            LedgerRecord left = leftover(job, t.crafter, key, extra);
            adoptLeftover(job, left);
        }
        if (allOutputsCaptured(t) && !t.state.isFinished()) finishTicket(job, t, now);
        touch(job);
    }

    /** The machine can't run the ticket (configuration failed, wrong recipe, ...). */
    public void onWrongCraft(long ticketId, String reason) {
        Ticket t = tickets.get(ticketId);
        if (t == null || t.state.isFinished() || t.state == TicketState.WRONG_CRAFT) return;
        wrongCraft(jobs.get(t.job), t, reason, world.now());
    }

    private void wrongCraft(CraftingJob job, Ticket t, String reason, long now) {
        CrafterState cs = crafterState(t.crafter);
        releaseCommit(cs, t);
        cs.complete.remove(t);
        cs.blockedJobs.add(t.job);
        t.problem = reason;
        if (t.state == TicketState.COMMITTED || t.state == TicketState.COMPLETE) {
            // still buffered: may run again once the player fixes the machine
            t.state = TicketState.WRONG_CRAFT;
            t.stateSince = now;
        } else if (t.state == TicketState.INSERTED) {
            t.state = TicketState.WRONG_CRAFT;
            t.stateSince = now;
        }
        if (job != null) {
            touch(job);
            message(job, "Crafting " + job.target + " stalled: " + reason);
        }
    }

    /** Player says the machine is fixed: let the job use the crafter again. */
    public void retryWrongCrafts(long jobId) {
        CraftingJob job = jobs.get(jobId);
        if (job == null) return;
        long now = world.now();
        for (Ticket t : job.tickets.values()) {
            if (t.state != TicketState.WRONG_CRAFT) continue;
            crafterState(t.crafter).blockedJobs.remove(jobId);
            t.problem = null;
            boolean buffered = true;
            for (LedgerRecord r : ledger.forTicket(t.id)) {
                if (r.state != RecordState.BUFFERED) buffered = false;
            }
            if (buffered && !ledger.forTicket(t.id).isEmpty()) {
                setState(job, t, TicketState.COMPLETE, now);
                crafterState(t.crafter).complete.add(t);
            } else {
                setState(job, t, TicketState.INSERTED, now);
            }
        }
        retryStalled();
        touch(job);
    }

    /**
     * Turns EXPECTED records of the ticket's node into BUFFERED items at its crafter, consumers that can use them
     * first. Returns what no record was waiting for.
     */
    private long convertExpected(CraftingJob job, Ticket producer, ResourceKey key, long qty) {
        List<LedgerRecord> waiting = new ArrayList<>();
        for (LedgerRecord r : ledger.expectedFrom(job.id, producer.node)) {
            if (key.equals(r.key) && r.shipment < 0) waiting.add(r);
        }
        waiting.sort(Comparator.comparingInt(this::consumerPriority).thenComparingLong(r -> {
            Ticket c = tickets.get(r.ticket);
            return c == null ? Long.MAX_VALUE : c.seq;
        }));
        long left = qty;
        UUID at = producer.crafter;
        for (LedgerRecord r : waiting) {
            if (left <= 0) break;
            LedgerRecord part = ledger.split(r, Math.min(left, r.qty));
            left -= part.qty;
            if (!part.isBound()) {
                ledger.mutate(part, x -> {
                    x.state = RecordState.LEFTOVER;
                    x.location = at;
                    x.producedBy = -1;
                });
                continue;
            }
            ledger.mutate(part, x -> {
                x.state = RecordState.BUFFERED;
                x.location = at;
                x.producedBy = -1;
            });
            Ticket consumer = tickets.get(part.ticket);
            if (consumer != null && at.equals(consumer.crafter)
                    && consumer.state == TicketState.OPEN
                    && !consumer.legacy) {
                CrafterPort port = world.crafter(at);
                if (port != null) assignHeld(port, consumer, part);
                checkComplete(job, consumer, world.now());
            }
        }
        return left;
    }

    /** 0 for consumers that are open now, then by height. Unbound records last. */
    private int consumerPriority(LedgerRecord r) {
        Ticket c = tickets.get(r.ticket);
        if (c == null) return Integer.MAX_VALUE;
        int open = c.state == TicketState.OPEN || c.isDelivery() ? 0 : 1;
        return open * 100_000 + Math.min(c.height, 99_999);
    }

    private LedgerRecord leftover(CraftingJob job, UUID at, ResourceKey key, long qty) {
        return ledger.create(job.id, key, qty, RecordState.LEFTOVER, x -> x.location = at);
    }

    /**
     * Plan section 5.5: an unplanned output replaces provider stock that a ticket reserved but hasn't moved yet, and
     * the reservation is released.
     */
    private void adoptLeftover(CraftingJob job, LedgerRecord left) {
        for (LedgerRecord r : ledger.forJob(job.id)) {
            if (ledger.get(left.id) != left) return;
            if (r.state != RecordState.RESERVED || !r.key.equals(left.key)) continue;
            Ticket t = tickets.get(r.ticket);
            if (t == null || !reachable(left.location, destination(t, r.need), left.key)) continue;
            long q = Math.min(r.qty, left.qty);
            LedgerRecord reserved = ledger.split(r, q);
            LedgerRecord adopted = ledger.split(left, q);
            int need = reserved.need;
            ledger.remove(reserved);
            ledger.mutate(adopted, x -> {
                x.state = RecordState.BUFFERED;
                x.ticket = t.id;
                x.need = need;
            });
            if (t.crafter.equals(adopted.location) && t.state == TicketState.OPEN && !t.legacy) {
                CrafterPort port = world.crafter(t.crafter);
                if (port != null) assignHeld(port, t, adopted);
            }
        }
    }

    private static boolean allOutputsCaptured(Ticket t) {
        for (long v : t.outputsRemaining.values()) {
            if (v > 0) return false;
        }
        return true;
    }

    private void finishTicket(CraftingJob job, Ticket t, long now) {
        if (t.state.isFinished()) return;
        CrafterState cs = crafterState(t.crafter);
        if (t.state == TicketState.WRONG_CRAFT) {
            cs.blockedJobs.remove(t.job);
            t.problem = null;
        }
        releaseCommit(cs, t);
        cs.open.remove(t.id);
        cs.complete.remove(t);
        setState(job, t, TicketState.DONE, now);
        if (job != null) {
            job.ticketFinished(t);
            job.active.remove(t);
            job.planned.remove(t);
        }
        for (LedgerRecord r : ledger.forTicket(t.id)) {
            if (r.state == RecordState.INSERTED) {
                ledger.remove(r);
            } else {
                unbind(r);
            }
        }
    }

    // ---------------------------------------------------------------- legacy crafters

    private void legacyCheckInserted(CraftingJob job, Ticket t, long now) {
        if (t.state != TicketState.OPEN) return;
        for (Need n : t.needs) {
            long inserted = 0;
            for (LedgerRecord r : ledger.forTicket(t.id)) {
                if (r.need == n.index && r.state == RecordState.INSERTED) inserted += r.qty;
            }
            if (inserted < n.qty) return;
        }
        setState(job, t, TicketState.INSERTED, now);
        CrafterState cs = crafterState(t.crafter);
        cs.open.remove(t.id);
        job.active.remove(t);
        CrafterPort port = world.crafter(t.crafter);
        for (Map.Entry<ResourceKey, Long> out : t.expectedOutputs.entrySet()) {
            ResourceKey key = out.getKey();
            long left = out.getValue();
            List<LedgerRecord> waiting = new ArrayList<>();
            for (LedgerRecord r : ledger.expectedFrom(job.id, t.node)) {
                if (key.equals(r.key) && r.shipment < 0) waiting.add(r);
            }
            waiting.sort(Comparator.comparingInt(this::consumerPriority));
            for (LedgerRecord r : waiting) {
                if (left <= 0) break;
                LedgerRecord part = ledger.split(r, Math.min(left, r.qty));
                left -= part.qty;
                Ticket consumer = tickets.get(part.ticket);
                if (consumer == null || port == null) {
                    // nobody to send it to: the legacy crafter leaves it in the machine
                    ledger.remove(part);
                    t.outputsRemaining.computeIfPresent(key, (k, v) -> Math.max(0, v - part.qty));
                    continue;
                }
                Shipment s = new Shipment(
                        nextId(),
                        job.id,
                        t.crafter,
                        destination(consumer, part.need),
                        key,
                        part.qty,
                        now,
                        t.id);
                shipments.put(s.id, s);
                ledger.mutate(part, x -> x.shipment = s.id);
                port.legacyOrder(
                        key,
                        part.qty,
                        destination(consumer, part.need),
                        new CraftDelivery(job.id, s.id, consumer.id));
            }
            if (left > 0) {
                long rest = left;
                t.outputsRemaining.computeIfPresent(key, (k, v) -> Math.max(0, v - rest));
            }
        }
        if (allOutputsCaptured(t)) finishTicket(job, t, now);
    }

    // ================================================================== bookkeeping

    /**
     * Makes the records bound to {@code t} add up to its needs: excess pending records are unbound, a shortfall becomes
     * an UNSOURCED record.
     */
    void rebalance(Ticket t) {
        CraftingJob job = jobs.get(t.job);
        if (job == null) return;
        if (t.state.isFinished() || job.state != JobState.RUNNING) {
            for (LedgerRecord r : ledger.forTicket(t.id)) {
                if (r.state != RecordState.INSERTED) unbind(r);
            }
            return;
        }
        if (t.state == TicketState.INSERTED || t.state == TicketState.WRONG_CRAFT && !hasBuffered(t)) return;
        for (Need n : t.needs) {
            long bound = ledger.sumBound(t.id, n.index);
            long wanted = t.isDelivery() ? Math.max(0, n.qty - t.delivered) : n.qty;
            if (bound > wanted) {
                long excess = bound - wanted;
                List<LedgerRecord> pending = new ArrayList<>();
                for (LedgerRecord r : ledger.forTicket(t.id)) {
                    if (r.need == n.index && r.state.isPending()) pending.add(r);
                }
                pending.sort(Comparator.comparingInt(CraftingDriver::unbindOrder));
                for (LedgerRecord r : pending) {
                    if (excess <= 0) break;
                    LedgerRecord part = ledger.split(r, Math.min(excess, r.qty));
                    excess -= part.qty;
                    unbind(part);
                }
            } else if (bound < wanted) {
                long missing = wanted - bound;
                ledger.create(t.job, n.matcher.key(), missing, RecordState.UNSOURCED, x -> bind(x, t, n.index, null));
            }
        }
        if (t.state == TicketState.COMPLETE && !isFullyBuffered(t)) {
            // lost something it had: back to collecting
            t.state = TicketState.OPEN;
            crafterState(t.crafter).complete.remove(t);
        }
    }

    private static int unbindOrder(LedgerRecord r) {
        switch (r.state) {
            case UNSOURCED:
                return 0;
            case RESERVED:
                return 1;
            case EXPECTED:
                return r.shipment < 0 ? 2 : 4;
            case IN_TRANSIT:
                return 3;
            default:
                return 5;
        }
    }

    /** Detaches a record from its ticket: provider holds are released, things in the world become the job's. */
    private void unbind(LedgerRecord r) {
        switch (r.state) {
            case UNSOURCED:
            case RESERVED:
                ledger.remove(r);
                break;
            case BUFFERED:
                ledger.mutate(r, x -> {
                    x.state = RecordState.LEFTOVER;
                    x.ticket = LedgerRecord.UNBOUND;
                    x.need = -1;
                });
                break;
            default:
                ledger.mutate(r, x -> {
                    x.ticket = LedgerRecord.UNBOUND;
                    x.need = -1;
                });
        }
    }

    private void unsourceNow(LedgerRecord r, StallCause cause) {
        long now = world.now();
        ledger.mutate(r, x -> {
            x.state = RecordState.UNSOURCED;
            x.location = null;
            x.cause = cause;
            x.nextRetry = now;
        });
    }

    private boolean hasBuffered(Ticket t) {
        for (LedgerRecord r : ledger.forTicket(t.id)) {
            if (r.state == RecordState.BUFFERED) return true;
        }
        return false;
    }

    private boolean isFullyBuffered(Ticket t) {
        for (Need n : t.needs) {
            long buffered = 0;
            for (LedgerRecord r : ledger.forTicket(t.id)) {
                if (r.need == n.index && r.state == RecordState.BUFFERED && t.crafter.equals(r.location)) {
                    buffered += r.qty;
                }
            }
            if (buffered < n.qty) return false;
        }
        return true;
    }

    private void checkComplete(CraftingJob job, Ticket t, long now) {
        if (t.state != TicketState.OPEN || t.isDelivery() || t.legacy) return;
        if (!isFullyBuffered(t)) return;
        setState(job, t, TicketState.COMPLETE, now);
        crafterState(t.crafter).complete.add(t);
    }

    private void releaseCommit(CrafterState cs, Ticket t) {
        if (cs.outstanding.remove(t.id) != null) {
            cs.outstandingPatterns.computeIfPresent(t.patternId, (k, v) -> v <= 1 ? null : v - 1);
        }
    }

    private void setState(CraftingJob job, Ticket t, TicketState state, long now) {
        if (t.state == state) return;
        if (t.state == TicketState.COMPLETE) crafterState(t.crafter).complete.remove(t);
        t.state = state;
        t.stateSince = now;
        if (job != null) touch(job);
    }

    private void setProblem(CraftingJob job, Ticket t, String problem) {
        if (problem.equals(t.problem)) return;
        t.problem = problem;
        if (job != null) touch(job);
    }

    private void touch(CraftingJob job) {
        job.revision++;
        dirty = true;
    }

    /** Where a ticket's need is delivered: its crafter, or a legacy satellite. */
    private static UUID destination(Ticket t, int need) {
        Need n = t.need(need);
        return n != null && n.target != null ? n.target : t.crafter;
    }

    private boolean reachable(UUID from, UUID to, ResourceKey key) {
        return from.equals(to) || world.routes().canReach(from, to, key);
    }

    private CrafterState crafterState(UUID id) {
        return crafters.computeIfAbsent(id, k -> new CrafterState());
    }

    private static CraftingJob.NodeInfo nodeInfo(CraftingJob job, int node) {
        for (CraftingJob.NodeInfo n : job.nodes) {
            if (n.node() == node) return n;
        }
        return null;
    }

    private boolean hasUnfinishedTicket(CraftingJob job, int node) {
        for (Ticket t : job.tickets.values()) {
            if (t.node == node && t.kind == Ticket.Kind.CRAFT && !t.state.isFinished()) return true;
        }
        return false;
    }

    private void message(CraftingJob job, String text) {
        if (job.player != null) world.message(job.player, text);
    }

    // ================================================================== stall reporting (7, 10.3)

    public SetState displayState(Ticket t) {
        switch (t.state) {
            case DONE:
                return SetState.DONE;
            case CANCELLED:
                return SetState.DONE;
            case WRONG_CRAFT:
                return SetState.WRONG_CRAFT;
            case COMMITTED:
            case INSERTED:
                return SetState.DISPATCHED;
            case COMPLETE:
                return SetState.READY;
            default:
        }
        boolean waiting = false;
        StallCause worst = StallCause.NONE;
        for (LedgerRecord r : ledger.forTicket(t.id)) {
            if (r.state == RecordState.UNSOURCED && r.cause != StallCause.NONE) {
                if (r.cause == StallCause.MISSING) worst = StallCause.MISSING;
                else if (worst == StallCause.NONE) worst = StallCause.UNREACHABLE;
            }
            if (r.state == RecordState.EXPECTED) waiting = true;
        }
        if (worst == StallCause.MISSING) return SetState.MISSING;
        if (worst == StallCause.UNREACHABLE) return SetState.UNREACHABLE;
        if (t.problem != null && t.state == TicketState.PLANNED) return SetState.UNREACHABLE;
        return waiting || t.state == TicketState.PLANNED ? SetState.WAITING : SetState.READY;
    }

    /** One stalled input, for the stall detail (plan section 10.3). */
    @Desugar
    public record Stall(Ticket ticket, ResourceKey key, long qty, StallCause cause, String problem) {}

    public List<Stall> stalls(long jobId) {
        CraftingJob job = jobs.get(jobId);
        List<Stall> result = new ArrayList<>();
        if (job == null) return result;
        for (Ticket t : job.tickets.values()) {
            if (t.state.isFinished()) continue;
            boolean any = false;
            for (LedgerRecord r : ledger.forTicket(t.id)) {
                if (r.state == RecordState.UNSOURCED && r.cause != StallCause.NONE) {
                    result.add(new Stall(t, r.key, r.qty, r.cause, t.problem));
                    any = true;
                }
            }
            if (!any && (t.state == TicketState.WRONG_CRAFT || t.problem != null)) {
                result.add(new Stall(t, null, 0, StallCause.NONE, t.problem));
            }
        }
        return result;
    }

    /** Reserved amounts of the job, by key (stall detail; plan section 7.5). */
    public Map<ResourceKey, Long> reservedBy(long jobId) {
        Map<ResourceKey, Long> result = new LinkedHashMap<>();
        for (LedgerRecord r : ledger.forJob(jobId)) {
            if (r.state == RecordState.RESERVED) result.merge(r.key, r.qty, Long::sum);
        }
        return result;
    }

    private void notifyStalls(CraftingJob job) {
        boolean stalled = false;
        String what = null;
        for (Ticket t : job.active) {
            SetState s = displayState(t);
            if (s.isStalled()) {
                stalled = true;
                for (LedgerRecord r : ledger.forTicket(t.id)) {
                    if (r.state == RecordState.UNSOURCED && r.cause != StallCause.NONE) {
                        what = r.qty + "x " + r.key.displayName() + " (" + s.label.toLowerCase() + ")";
                        break;
                    }
                }
                if (what != null) break;
            }
        }
        if (stalled && !job.stallNotified && what != null) {
            job.stallNotified = true;
            message(job, "Crafting " + job.qty + "x " + job.target + " is waiting for " + what);
        } else if (!stalled && job.stallNotified) {
            job.stallNotified = false;
        }
    }

    // ================================================================== cancellation and completion (8)

    public boolean cancelJob(long jobId) {
        CraftingJob job = jobs.get(jobId);
        if (job == null || job.state != JobState.RUNNING) return false;
        beginWindDown(job, world.now());
        return true;
    }

    private void beginWindDown(CraftingJob job, long now) {
        job.state = JobState.CANCELLING;
        job.windDownSince = now;
        for (Ticket t : new ArrayList<>(job.tickets.values())) {
            CrafterState cs = crafterState(t.crafter);
            switch (t.state) {
                case PLANNED:
                case WRONG_CRAFT:
                    t.state = TicketState.CANCELLED;
                    job.ticketFinished(t);
                    break;
                case OPEN:
                case COMPLETE:
                case COMMITTED: {
                    if (t.isDelivery()) {
                        t.state = job.delivered ? TicketState.DONE : TicketState.CANCELLED;
                        break;
                    }
                    CrafterPort port = world.crafter(t.crafter);
                    if (port != null && !t.legacy) port.cancel(t.id, t.job);
                    releaseCommit(cs, t);
                    cs.complete.remove(t);
                    cs.open.remove(t.id);
                    t.state = TicketState.CANCELLED;
                    job.ticketFinished(t);
                    break;
                }
                case INSERTED:
                    if (t.legacy) {
                        t.state = TicketState.CANCELLED;
                        job.ticketFinished(t);
                    }
                    break;
                default:
            }
            t.stateSince = now;
        }
        job.planned.clear();
        job.active.clear();
        for (LedgerRecord r : ledger.forJob(job.id)) {
            Ticket t = tickets.get(r.ticket);
            if (r.state == RecordState.INSERTED && t != null && t.state == TicketState.INSERTED) continue;
            if (r.state == RecordState.EXPECTED && r.shipment >= 0) {
                // a legacy crafter will still send these; they arrive as leftovers
                unbind(r);
                continue;
            }
            unbind(r);
        }
        touch(job);
    }

    private void windDown(CraftingJob job, long now) {
        boolean busy = false;
        for (Ticket t : job.tickets.values()) {
            if (t.state == TicketState.INSERTED) busy = true;
        }
        for (LedgerRecord r : ledger.forJob(job.id)) {
            if (r.state == RecordState.IN_TRANSIT) busy = true;
        }
        if (busy && now - job.windDownSince < config.windDownTimeout) return;
        Set<UUID> places = new HashSet<>();
        for (Ticket t : job.tickets.values()) {
            if (!t.isDelivery() && !t.legacy) places.add(t.crafter);
        }
        for (LedgerRecord r : ledger.forJob(job.id)) {
            if (r.location != null && (r.state == RecordState.LEFTOVER || r.state == RecordState.BUFFERED)) {
                places.add(r.location);
            }
        }
        for (UUID place : places) {
            CrafterPort port = world.crafter(place);
            if (port != null && !port.isLegacy()) port.flushJob(job.id);
        }
        finishJob(job, job.delivered ? JobState.COMPLETED : JobState.CANCELLED);
    }

    private void finishJob(CraftingJob job, JobState end) {
        job.state = end;
        ledger.removeJob(job.id);
        for (Ticket t : job.tickets.values()) {
            tickets.remove(t.id);
            CrafterState cs = crafters.get(t.crafter);
            if (cs != null) {
                cs.open.remove(t.id);
                cs.complete.remove(t);
                releaseCommit(cs, t);
                cs.blockedJobs.remove(job.id);
            }
        }
        shipments.values().removeIf(s -> s.job == job.id);
        jobs.remove(job.id);
        message(
                job,
                end == JobState.COMPLETED ? "Crafting " + job.qty + "x " + job.target + " finished"
                        : "Crafting " + job.qty + "x " + job.target + " cancelled");
        dirty = true;
    }

    // ================================================================== world events (7.1, 9)

    /**
     * A crafter (re)loaded. {@code knownTickets} are the tickets it holds, with whether each one is already in the
     * machine; {@code pools} the jobs it holds pooled items for.
     */
    public void onCrafterLoaded(UUID crafter, Map<Long, Boolean> knownTickets, Set<Long> pools) {
        CrafterPort port = world.crafter(crafter);
        if (port == null) return;
        long now = world.now();
        for (Map.Entry<Long, Boolean> e : knownTickets.entrySet()) {
            Ticket t = tickets.get(e.getKey());
            CraftingJob job = t == null ? null : jobs.get(t.job);
            if (t == null || job == null || t.state.isFinished() || !crafter.equals(t.crafter)) {
                port.cancel(e.getKey(), t == null ? -1 : t.job);
            } else if (e.getValue() && t.state == TicketState.COMMITTED) {
                onInserted(t.id);
            }
        }
        for (long pool : pools) {
            CraftingJob job = jobs.get(pool);
            if (job == null || job.state.isOver()) port.flushJob(pool);
        }
        for (Ticket t : new ArrayList<>(tickets.values())) {
            if (!crafter.equals(t.crafter) || t.legacy || t.isDelivery()) continue;
            CraftingJob job = jobs.get(t.job);
            if (job == null || job.state != JobState.RUNNING) continue;
            boolean known = knownTickets.containsKey(t.id);
            if (!known && (t.state.isOpenAtCrafter() || t.state == TicketState.INSERTED)) {
                // the crafter lost the ticket (crash between saves): what it held is gone
                restartTicket(job, t, now);
            }
        }
        retryStalled();
    }

    /** A crafter was broken: whatever it held is gone (dropped in the world). */
    public void onCrafterRemoved(UUID crafter) {
        long now = world.now();
        Set<Ticket> touched = new LinkedHashSet<>();
        for (LedgerRecord r : new ArrayList<>(ledger.all())) {
            if (!crafter.equals(r.location)) continue;
            if (r.state == RecordState.BUFFERED || r.state == RecordState.LEFTOVER || r.state == RecordState.INSERTED) {
                ledger.remove(r);
                Ticket t = tickets.get(r.ticket);
                if (t != null) touched.add(t);
            }
        }
        for (Ticket t : touched) rebalance(t);
        for (Ticket t : new ArrayList<>(tickets.values())) {
            if (!crafter.equals(t.crafter) || t.isDelivery() || t.state.isFinished()) continue;
            CraftingJob job = jobs.get(t.job);
            if (job == null || job.state != JobState.RUNNING) continue;
            restartTicket(job, t, now);
            UUID replacement = world.findCrafter(t.patternId, crafter);
            if (replacement != null) {
                t.crafter = replacement;
                t.problem = null;
            } else {
                t.problem = "Crafter was removed";
            }
            rebalance(t);
        }
        crafters.remove(crafter);
    }

    /**
     * The crafter lost the ticket's set (pipe broken, or it forgot the ticket between saves): start the ticket over.
     * Its inputs are re-sourced and the set is crafted again; consumers keep waiting on the same EXPECTED records.
     */
    private void restartTicket(CraftingJob job, Ticket t, long now) {
        CrafterState cs = crafterState(t.crafter);
        releaseCommit(cs, t);
        cs.complete.remove(t);
        cs.open.remove(t.id);
        job.active.remove(t);
        for (LedgerRecord r : ledger.forTicket(t.id)) {
            if (r.state == RecordState.INSERTED || r.state == RecordState.BUFFERED && t.crafter.equals(r.location)) {
                ledger.remove(r);
            }
        }
        t.outputsRemaining.clear();
        t.outputsRemaining.putAll(t.expectedOutputs);
        t.state = TicketState.PLANNED;
        t.stateSince = now;
        if (job.state == JobState.RUNNING) job.planned.add(t);
        rebalance(t);
        touch(job);
    }

    // ================================================================== invariants (5.6)

    /** Empty when the bookkeeping is consistent. Used by tests and by dev-build assertions. */
    public List<String> checkInvariants() {
        List<String> problems = new ArrayList<>();
        for (CraftingJob job : jobs.values()) {
            if (job.state != JobState.RUNNING) continue;
            for (Ticket t : job.tickets.values()) {
                if (t.state.isFinished() || t.isDelivery() && job.delivered) continue;
                if (t.state == TicketState.WRONG_CRAFT) continue;
                for (Need n : t.needs) {
                    long bound = ledger.sumBound(t.id, n.index);
                    long expected = t.isDelivery() ? n.qty - t.delivered : n.qty;
                    if (bound != expected) {
                        problems.add(
                                t + " need "
                                        + n.index
                                        + " has "
                                        + bound
                                        + " bound, expected "
                                        + expected
                                        + ": "
                                        + ledger.forTicket(t.id));
                    }
                }
            }
        }
        for (LedgerRecord r : ledger.all()) {
            if (r.isBound() && !tickets.containsKey(r.ticket)) problems.add("record bound to unknown ticket: " + r);
            if (!jobs.containsKey(r.job)) problems.add("record of unknown job: " + r);
            if (r.state == RecordState.IN_TRANSIT && !shipments.containsKey(r.shipment)) {
                problems.add("in transit without shipment: " + r);
            }
        }
        if (!ledger.recomputeReservations().equals(ledger.reservations().snapshot())) {
            problems.add(
                    "reservation index out of step: " + ledger.reservations().snapshot()
                            + " vs "
                            + ledger.recomputeReservations());
        }
        return problems;
    }

    // ================================================================== persistence hooks

    /** Used by {@link DriverPersistence} to rebuild state. */
    void restore(long nextId, List<CraftingJob> savedJobs, List<LedgerRecord> records, List<Shipment> savedShipments) {
        this.nextId = Math.max(this.nextId, nextId);
        jobs.clear();
        tickets.clear();
        shipments.clear();
        crafters.clear();
        ledger.clear();
        for (CraftingJob job : savedJobs) {
            jobs.put(job.id, job);
            for (Ticket t : job.tickets.values()) {
                tickets.put(t.id, t);
                CrafterState cs = crafterState(t.crafter);
                switch (t.state) {
                    case PLANNED:
                        if (job.state == JobState.RUNNING) job.planned.add(t);
                        break;
                    case OPEN:
                        job.active.add(t);
                        cs.open.add(t.id);
                        break;
                    case COMPLETE:
                        cs.open.add(t.id);
                        cs.complete.add(t);
                        break;
                    case COMMITTED:
                        cs.open.add(t.id);
                        cs.outstanding.put(t.id, t.stateSince);
                        cs.outstandingPatterns.merge(t.patternId, 1, Integer::sum);
                        break;
                    case WRONG_CRAFT:
                        cs.blockedJobs.add(job.id);
                        break;
                    default:
                }
                if (t.isDelivery() && !t.state.isFinished()) job.active.add(t);
            }
        }
        for (LedgerRecord r : records) ledger.restore(r);
        for (Shipment s : savedShipments) shipments.put(s.id, s);
    }

    long peekNextId() {
        return nextId;
    }

    Collection<Shipment> allShipments() {
        return shipments.values();
    }

    private static final class CrafterState {

        /** Tickets with a needs entry at the crafter (OPEN, COMPLETE, COMMITTED). */
        final Set<Long> open = new HashSet<>();
        final TreeSet<Ticket> complete = new TreeSet<>(CraftingJob.OPEN_ORDER);
        /** Committed, not yet inserted: ticket -> tick the commit was (re)sent. */
        final Map<Long, Long> outstanding = new LinkedHashMap<>();
        final Map<String, Integer> outstandingPatterns = new HashMap<>();
        /** Jobs that hit a wrong craft here and may not commit until the player retries. */
        final Set<Long> blockedJobs = new HashSet<>();

        /** The pattern every outstanding commit uses, or null when they differ. */
        String outstandingPattern() {
            return outstandingPatterns.size() == 1 ? outstandingPatterns.keySet().iterator().next() : null;
        }
    }
}
