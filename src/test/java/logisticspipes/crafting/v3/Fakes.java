package logisticspipes.crafting.v3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import logisticspipes.crafting.v3.model.BlockingMode;
import logisticspipes.crafting.v3.model.CrafterInfo;
import logisticspipes.crafting.v3.model.InputSpec;
import logisticspipes.crafting.v3.model.Pattern;
import logisticspipes.crafting.v3.model.ResourceAmount;
import logisticspipes.crafting.v3.model.ResourceKey;
import logisticspipes.crafting.v3.model.ResourceMatcher;
import logisticspipes.crafting.v3.plan.PatternRegistry;
import logisticspipes.crafting.v3.plan.RouteOracle;
import logisticspipes.crafting.v3.plan.StockView;

/** Minecraft-free stand-ins for the planner and driver inputs. */
final class Fakes {

    private Fakes() {}

    static final class Key implements ResourceKey {

        final String name;

        Key(String name) {
            this.name = name;
        }

        @Override
        public boolean isFluid() {
            return false;
        }

        @Override
        public int maxStackSize() {
            return 64;
        }

        @Override
        public String displayName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key && ((Key) o).name.equals(name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static Key key(String name) {
        return new Key(name);
    }

    static UUID loc(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes());
    }

    static CrafterInfo crafter(String name) {
        return crafter(name, 0);
    }

    static CrafterInfo crafter(String name, int priority) {
        return new CrafterInfo(loc(name), name, BlockingMode.BLOCKING, priority, false, 1);
    }

    static InputSpec in(String name, long qty) {
        return new InputSpec(ResourceMatcher.exact(key(name)), qty, true);
    }

    static InputSpec tool(String name) {
        return new InputSpec(ResourceMatcher.exact(key(name)), 1, false);
    }

    static ResourceAmount out(String name, long qty) {
        return new ResourceAmount(key(name), qty);
    }

    static Pattern pattern(String id, List<ResourceAmount> outputs, InputSpec... inputs) {
        return new Pattern(id, Arrays.asList(inputs), outputs, null);
    }

    static Pattern pattern(String id, ResourceAmount output, InputSpec... inputs) {
        return pattern(id, Arrays.asList(output), inputs);
    }

    static final class Registry implements PatternRegistry {

        final Map<String, Pattern> patterns = new LinkedHashMap<>();
        final Map<String, List<CrafterSlot>> crafters = new HashMap<>();

        Registry add(Pattern p, CrafterInfo... on) {
            patterns.put(p.id(), p);
            List<CrafterSlot> list = crafters.computeIfAbsent(p.id(), k -> new ArrayList<>());
            for (CrafterInfo c : on) list.add(new CrafterSlot(c, 0));
            list.sort(Comparator.comparingInt((CrafterSlot s) -> -s.crafter().priority()));
            return this;
        }

        @Override
        public List<Candidate> candidatesFor(ResourceMatcher matcher) {
            List<Candidate> result = new ArrayList<>();
            for (Pattern p : patterns.values()) {
                if (p.outputMatching(matcher) != null) result.add(new Candidate(p, crafters.get(p.id())));
            }
            return result;
        }
    }

    static final class Stock implements StockView {

        final Map<UUID, Map<ResourceKey, Long>> contents = new LinkedHashMap<>();

        Stock put(String location, String item, long qty) {
            contents.computeIfAbsent(loc(location), k -> new LinkedHashMap<>()).merge(key(item), qty, Long::sum);
            return this;
        }

        @Override
        public List<Entry> find(ResourceMatcher matcher) {
            List<Entry> result = new ArrayList<>();
            contents.forEach(
                    (l, m) -> m
                            .forEach((k, q) -> { if (q > 0 && matcher.matches(k)) result.add(new Entry(l, k, q)); }));
            return result;
        }
    }

    /** Everything reaches everything unless blocked; blocks are one-way. */
    static final class Routes implements RouteOracle {

        final Set<String> blocked = new HashSet<>();

        Routes block(String from, String to) {
            blocked.add(loc(from) + ">" + loc(to));
            return this;
        }

        @Override
        public boolean canReach(UUID from, UUID to, ResourceKey key) {
            return !blocked.contains(from + ">" + to);
        }

        @Override
        public boolean isLoaded(UUID location) {
            return true;
        }
    }
}
