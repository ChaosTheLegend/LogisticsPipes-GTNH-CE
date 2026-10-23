package logisticspipes.crafting.v3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

import net.minecraft.nbt.NBTTagCompound;

import logisticspipes.crafting.v3.exec.CraftDelivery;
import logisticspipes.crafting.v3.exec.CrafterPort;
import logisticspipes.crafting.v3.exec.CraftingDriver;
import logisticspipes.crafting.v3.exec.DriverPersistence;
import logisticspipes.crafting.v3.exec.DriverWorld;
import logisticspipes.crafting.v3.exec.KeyCodec;
import logisticspipes.crafting.v3.exec.Ticket;
import logisticspipes.crafting.v3.model.BlockingMode;
import logisticspipes.crafting.v3.model.CrafterInfo;
import logisticspipes.crafting.v3.model.InputSpec;
import logisticspipes.crafting.v3.model.Pattern;
import logisticspipes.crafting.v3.model.ResourceAmount;
import logisticspipes.crafting.v3.model.ResourceKey;
import logisticspipes.crafting.v3.model.ResourceMatcher;
import logisticspipes.crafting.v3.pipe.CrafterRuntime;
import logisticspipes.crafting.v3.plan.CraftingPlanner;
import logisticspipes.crafting.v3.plan.Plan;
import logisticspipes.crafting.v3.plan.PlanRequest;
import logisticspipes.crafting.v3.plan.RouteOracle;
import logisticspipes.crafting.v3.plan.StockView;

/**
 * Discrete tick simulator (plan section 11.2): the real driver and real crafter runtimes, with fake providers, fake
 * machines and a transit queue that delays (and optionally drops) routed items.
 */
final class Sim implements DriverWorld {

    static final KeyCodec CODEC = new KeyCodec() {

        @Override
        public void write(ResourceKey key, NBTTagCompound tag) {
            tag.setString("k", key.displayName());
        }

        @Override
        public ResourceKey read(NBTTagCompound tag) {
            return Fakes.key(tag.getString("k"));
        }
    };

    long tick;
    final Fakes.Registry registry = new Fakes.Registry();
    final Fakes.Routes routes = new Fakes.Routes();
    final Map<UUID, Map<ResourceKey, Long>> providers = new LinkedHashMap<>();
    final Map<UUID, SimCrafter> crafters = new LinkedHashMap<>();
    final Map<ResourceKey, Long> storage = new LinkedHashMap<>();
    final Map<ResourceKey, Long> requesterGot = new LinkedHashMap<>();
    final List<Transit> transit = new ArrayList<>();
    final List<String> messages = new ArrayList<>();
    final UUID requester = Fakes.loc("requester");
    CraftingDriver driver = new CraftingDriver(this);
    int transitDelay = 3;
    double lossChance;
    final Random random = new Random(42);
    final List<String> invariantFailures = new ArrayList<>();

    static final class Transit {

        long arrive;
        final UUID dest;
        final ResourceKey key;
        final long qty;
        final CraftDelivery info;

        Transit(long arrive, UUID dest, ResourceKey key, long qty, CraftDelivery info) {
            this.arrive = arrive;
            this.dest = dest;
            this.key = key;
            this.qty = qty;
            this.info = info;
        }
    }

    // ---------------------------------------------------------------- setup

    Sim provider(String name, String item, long qty) {
        providers.computeIfAbsent(Fakes.loc(name), k -> new LinkedHashMap<>()).merge(Fakes.key(item), qty, Long::sum);
        return this;
    }

    SimCrafter crafter(String name, BlockingMode mode, boolean legacy) {
        SimCrafter c = new SimCrafter(name, mode, legacy);
        crafters.put(c.id, c);
        return c;
    }

    void pattern(SimCrafter c, Pattern p, int duration) {
        int slot = c.patterns.size();
        c.patterns.put(slot, p);
        c.machine.durations.put(slot, duration);
        CrafterInfo info = new CrafterInfo(c.id, c.name, c.mode, 0, c.legacy, 1);
        registry.patterns.put(p.id(), p);
        registry.crafters.computeIfAbsent(p.id(), k -> new ArrayList<>())
                .add(new logisticspipes.crafting.v3.plan.PatternRegistry.CrafterSlot(info, slot));
    }

    Plan plan(String item, long qty) {
        StockView snapshot = matcher -> availableStock(matcher, requester);
        return new CraftingPlanner(registry, snapshot, routes)
                .plan(new PlanRequest(ResourceMatcher.exact(Fakes.key(item)), qty, requester));
    }

    // ---------------------------------------------------------------- running

    void step() {
        tick++;
        for (Iterator<Transit> it = transit.iterator(); it.hasNext();) {
            Transit t = it.next();
            if (t.arrive > tick) continue;
            it.remove();
            if (lossChance > 0 && t.info != null && random.nextDouble() < lossChance) {
                driver.onLost(t.info, t.key, t.qty);
                continue;
            }
            arrive(t);
        }
        for (SimCrafter c : crafters.values()) c.machine.tick();
        for (SimCrafter c : crafters.values()) {
            if (c.loaded && !c.legacy) c.runtime.tick(tick);
            if (c.loaded && c.legacy) c.legacyTick();
        }
        driver.tick();
        List<String> problems = driver.checkInvariants();
        if (!problems.isEmpty() && invariantFailures.isEmpty()) {
            invariantFailures.add("tick " + tick + ": " + problems);
        }
    }

    boolean runUntil(Predicate<Sim> done, int maxTicks) {
        for (int i = 0; i < maxTicks; i++) {
            if (done.test(this)) return true;
            step();
        }
        return done.test(this);
    }

    private void arrive(Transit t) {
        if (t.dest.equals(requester)) {
            requesterGot.merge(t.key, t.qty, Long::sum);
            driver.onDelivered(requester, t.key, t.qty, t.info);
            return;
        }
        SimCrafter c = crafters.get(t.dest);
        if (c == null || !c.loaded) {
            // destination gone: the item is re-routed to storage
            storage.merge(t.key, t.qty, Long::sum);
            if (t.info != null) driver.onLost(t.info, t.key, t.qty);
            return;
        }
        if (c.legacy) {
            (c.machineOverride != null ? c.machineOverride : c.machine).inputs.merge(t.key, t.qty, Long::sum);
            driver.onDelivered(c.id, t.key, t.qty, t.info);
            return;
        }
        long taken = c.runtime.accept(t.key, t.qty, t.info);
        if (taken < t.qty) storage.merge(t.key, t.qty - taken, Long::sum);
    }

    void send(UUID dest, ResourceKey key, long qty, CraftDelivery info) {
        transit.add(new Transit(tick + transitDelay, dest, key, qty, info));
    }

    // ---------------------------------------------------------------- save / load

    void saveAndReload() {
        NBTTagCompound driverTag = DriverPersistence.write(driver, CODEC);
        Map<UUID, NBTTagCompound> pipeTags = new LinkedHashMap<>();
        for (SimCrafter c : crafters.values()) {
            if (c.legacy) continue;
            NBTTagCompound tag = new NBTTagCompound();
            c.runtime.writeToNBT(tag, CODEC);
            pipeTags.put(c.id, tag);
        }
        driver = new CraftingDriver(this);
        DriverPersistence.read(driver, driverTag, CODEC, tick);
        for (SimCrafter c : crafters.values()) {
            if (c.legacy) continue;
            c.runtime = new CrafterRuntime(c.machine, c.link());
            c.runtime.readFromNBT(pipeTags.get(c.id), CODEC);
            driver.onCrafterLoaded(c.id, c.runtime.knownTickets(), c.runtime.poolJobs());
        }
    }

    // ---------------------------------------------------------------- DriverWorld

    @Override
    public long now() {
        return tick;
    }

    @Override
    public RouteOracle routes() {
        return new RouteOracle() {

            @Override
            public boolean canReach(UUID from, UUID to, ResourceKey key) {
                SimCrafter c = crafters.get(to);
                if (c != null && !c.loaded) return false;
                return routes.canReach(from, to, key);
            }

            @Override
            public boolean isLoaded(UUID location) {
                SimCrafter c = crafters.get(location);
                return c == null || c.loaded;
            }
        };
    }

    @Override
    public List<StockView.Entry> availableStock(ResourceMatcher matcher, UUID near) {
        List<StockView.Entry> result = new ArrayList<>();
        providers.forEach((loc, items) -> items.forEach((k, q) -> {
            if (!matcher.matches(k)) return;
            long free = q - driver.reservations().reserved(loc, k);
            if (free > 0) result.add(new StockView.Entry(loc, k, free));
        }));
        return result;
    }

    @Override
    public long availableAt(UUID provider, ResourceKey key) {
        Map<ResourceKey, Long> items = providers.get(provider);
        if (items == null) return 0;
        return items.getOrDefault(key, 0L) - driver.reservations().reserved(provider, key);
    }

    @Override
    public long physicalAt(UUID provider, ResourceKey key) {
        Map<ResourceKey, Long> items = providers.get(provider);
        return items == null ? -1 : items.getOrDefault(key, 0L);
    }

    @Override
    public long sendFromProvider(UUID provider, ResourceKey key, long qty, UUID destination, CraftDelivery info) {
        Map<ResourceKey, Long> items = providers.get(provider);
        if (items == null) return 0;
        long have = items.getOrDefault(key, 0L);
        long take = Math.min(have, qty);
        if (take <= 0) return 0;
        items.put(key, have - take);
        send(destination, key, take, info);
        return take;
    }

    @Override
    public CrafterPort crafter(UUID location) {
        SimCrafter c = crafters.get(location);
        return c == null || !c.loaded ? null : c.port;
    }

    @Override
    public UUID findCrafter(String patternId, UUID excluding) {
        for (SimCrafter c : crafters.values()) {
            if (c.id.equals(excluding) || !c.loaded) continue;
            for (Pattern p : c.patterns.values()) {
                if (p.id().equals(patternId)) return c.id;
            }
        }
        return null;
    }

    @Override
    public void message(UUID player, String text) {
        messages.add(text);
    }

    // ---------------------------------------------------------------- crafters and machines

    final class SimCrafter {

        final UUID id;
        final String name;
        final BlockingMode mode;
        final boolean legacy;
        final Map<Integer, Pattern> patterns = new LinkedHashMap<>();
        final SimMachine machine = new SimMachine(this);
        /** A legacy satellite: what arrives goes into another crafter's machine. */
        SimMachine machineOverride;
        CrafterRuntime runtime;
        final Port port = new Port();
        boolean loaded = true;
        final List<Object[]> legacyOrders = new ArrayList<>();

        SimCrafter(String name, BlockingMode mode, boolean legacy) {
            this.id = Fakes.loc(name);
            this.name = name;
            this.mode = mode;
            this.legacy = legacy;
            this.runtime = new CrafterRuntime(machine, link());
        }

        CrafterRuntime.Link link() {
            return new CrafterRuntime.Link() {

                @Override
                public void arrived(long ticket, int need, ResourceKey key, long qty, CraftDelivery hint) {
                    driver.onArrived(id, ticket, need, key, qty, hint);
                }

                @Override
                public void stray(long job, ResourceKey key, long qty, CraftDelivery hint) {
                    driver.onStray(id, job, key, qty, hint);
                }

                @Override
                public void inserted(long ticket) {
                    driver.onInserted(ticket);
                }

                @Override
                public void output(long ticket, ResourceKey key, long qty) {
                    driver.onOutput(ticket, key, qty);
                }

                @Override
                public void wrongCraft(long ticket, String reason) {
                    driver.onWrongCraft(ticket, reason);
                }

                @Override
                public long send(ResourceKey key, long qty, UUID destination, CraftDelivery info) {
                    Sim.this.send(destination, key, qty, info);
                    return qty;
                }

                @Override
                public long sendToStorage(ResourceKey key, long qty) {
                    storage.merge(key, qty, Long::sum);
                    return qty;
                }

                @Override
                public boolean isJobAlive(long job) {
                    return driver.isJobAlive(job);
                }
            };
        }

        void legacyTick() {
            for (Iterator<Object[]> it = legacyOrders.iterator(); it.hasNext();) {
                Object[] o = it.next();
                ResourceKey key = (ResourceKey) o[0];
                long want = (Long) o[1];
                long got = machine.extract(key, want);
                if (got <= 0) continue;
                send((UUID) o[2], key, got, (CraftDelivery) o[3]);
                if (got >= want) it.remove();
                else o[1] = want - got;
            }
        }

        final class Port implements CrafterPort {

            @Override
            public boolean isLegacy() {
                return legacy;
            }

            @Override
            public BlockingMode mode() {
                return legacy ? BlockingMode.NONE : mode;
            }

            @Override
            public void open(Ticket ticket) {
                runtime.open(ticket);
            }

            @Override
            public void commit(Ticket ticket) {
                runtime.commit(ticket.id);
            }

            @Override
            public void cancel(long ticket, long job) {
                runtime.cancel(ticket);
            }

            @Override
            public long pooled(long job, ResourceKey key) {
                return runtime.pooled(job, key);
            }

            @Override
            public long assign(long job, ResourceKey key, long qty, Ticket ticket, int need) {
                return runtime.assign(job, key, qty, ticket.id, need);
            }

            @Override
            public long ship(long job, ResourceKey key, long qty, UUID destination, CraftDelivery info) {
                return runtime.ship(job, key, qty, destination, info);
            }

            @Override
            public void flushJob(long job) {
                runtime.flushJob(job);
            }

            @Override
            public boolean legacyOrder(ResourceKey key, long qty, UUID destination, CraftDelivery info) {
                legacyOrders.add(new Object[] { key, qty, destination, info });
                return true;
            }
        }
    }

    /** One machine: consumes a full set of a pattern, works for a while, then outputs. */
    static final class SimMachine implements CrafterRuntime.Machine {

        final SimCrafter owner;
        final Map<Integer, Integer> durations = new LinkedHashMap<>();
        final Map<ResourceKey, Long> inputs = new LinkedHashMap<>();
        final Map<ResourceKey, Long> outputs = new LinkedHashMap<>();
        int busyUntil = -1;
        Pattern running;
        long tick;
        /** Wrong-recipe injection: produce this instead of the real output. */
        ResourceKey wrongOutput;
        /** A byproduct the pattern doesn't list (GT chanced output), made with every craft. */
        ResourceKey extraOutput;
        boolean configFails;
        int maxInputStacks = Integer.MAX_VALUE;

        SimMachine(SimCrafter owner) {
            this.owner = owner;
        }

        void tick() {
            tick++;
            if (running != null && tick >= busyUntil) {
                for (ResourceAmount o : running.outputs()) {
                    ResourceKey k = wrongOutput != null ? wrongOutput : o.key();
                    outputs.merge(k, o.amount(), Long::sum);
                }
                if (extraOutput != null) outputs.merge(extraOutput, 1L, Long::sum);
                running = null;
            }
            if (running != null) return;
            for (Map.Entry<Integer, Pattern> e : owner.patterns.entrySet()) {
                Pattern p = e.getValue();
                if (!hasSet(p)) continue;
                for (InputSpec in : p.inputs()) {
                    if (!in.consumed()) continue;
                    take(inputs, in.matcher(), in.qty());
                }
                running = p;
                busyUntil = (int) tick + durations.getOrDefault(e.getKey(), 5);
                return;
            }
        }

        private boolean hasSet(Pattern p) {
            for (InputSpec in : p.inputs()) {
                long have = 0;
                for (Map.Entry<ResourceKey, Long> e : inputs.entrySet()) {
                    if (in.matcher().matches(e.getKey())) have += e.getValue();
                }
                if (have < in.qty()) return false;
            }
            return true;
        }

        private static void take(Map<ResourceKey, Long> from, ResourceMatcher m, long qty) {
            for (Iterator<Map.Entry<ResourceKey, Long>> it = from.entrySet().iterator(); it.hasNext() && qty > 0;) {
                Map.Entry<ResourceKey, Long> e = it.next();
                if (!m.matches(e.getKey())) continue;
                long t = Math.min(qty, e.getValue());
                qty -= t;
                if (t == e.getValue()) it.remove();
                else e.setValue(e.getValue() - t);
            }
        }

        @Override
        public BlockingMode mode() {
            return owner.mode;
        }

        @Override
        public boolean isInput(ResourceKey key) {
            for (Pattern p : owner.patterns.values()) {
                for (InputSpec in : p.inputs()) {
                    if (in.matcher().matches(key)) return true;
                }
            }
            return false;
        }

        @Override
        public InsertResult insert(int patternSlot, String patternId, long crafts, List<ResourceAmount> stacks,
                List<ResourceAmount> notInserted) {
            if (configFails) return InsertResult.CONFIG_ERROR;
            if (inputs.size() + stacks.size() > maxInputStacks) return InsertResult.NO_ROOM;
            for (ResourceAmount s : stacks) inputs.merge(s.key(), s.amount(), Long::sum);
            return InsertResult.INSERTED;
        }

        @Override
        public boolean isIdle(int patternSlot, String patternId) {
            if (running != null || !outputs.isEmpty()) return false;
            Pattern p = owner.patterns.get(patternSlot);
            for (ResourceKey k : inputs.keySet()) {
                boolean ignored = false;
                if (p != null) {
                    for (InputSpec in : p.inputs()) {
                        if (!in.consumed() && in.matcher().matches(k)) ignored = true;
                    }
                }
                if (!ignored) return false;
            }
            return true;
        }

        @Override
        public List<ResourceKey> extractable() {
            return new ArrayList<>(outputs.keySet());
        }

        @Override
        public long extract(ResourceKey key, long qty) {
            long have = outputs.getOrDefault(key, 0L);
            long take = Math.min(have, qty);
            if (take <= 0) return 0;
            if (take == have) outputs.remove(key);
            else outputs.put(key, have - take);
            return take;
        }
    }
}
