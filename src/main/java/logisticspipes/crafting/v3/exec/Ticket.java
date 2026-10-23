package logisticspipes.crafting.v3.exec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import logisticspipes.crafting.v3.model.ResourceKey;

/**
 * One dispatchable input set for one crafter (plan section 2.4), or the job's delivery to its requester
 * ({@link Kind#DELIVERY}: its needs are the requested items, its "crafter" is the requester).
 */
public final class Ticket {

    public enum Kind {
        CRAFT,
        DELIVERY
    }

    public final long id;
    public final long job;
    public final Kind kind;
    public final int node;
    /** Changes only when the crafter is removed and another one with the pattern takes over. */
    UUID crafter;
    public final int patternSlot;
    public final String patternId;
    public final long crafts;
    public final int height;
    public final long seq;
    public final boolean legacy;
    public final List<Need> needs;
    public final Map<ResourceKey, Long> expectedOutputs;
    /** Chanced outputs of the pattern: captured as leftovers, never a wrong craft. */
    public final List<ResourceKey> chancedOutputs;

    TicketState state = TicketState.PLANNED;
    final Map<ResourceKey, Long> outputsRemaining;
    long stateSince;
    /** Delivery tickets: amount that reached the requester. */
    long delivered;
    String problem;

    public Ticket(long id, long job, Kind kind, int node, UUID crafter, int patternSlot, String patternId, long crafts,
            int height, long seq, boolean legacy, List<Need> needs, Map<ResourceKey, Long> expectedOutputs,
            List<ResourceKey> chancedOutputs) {
        this.id = id;
        this.job = job;
        this.kind = kind;
        this.node = node;
        this.crafter = crafter;
        this.patternSlot = patternSlot;
        this.patternId = patternId;
        this.crafts = crafts;
        this.height = height;
        this.seq = seq;
        this.legacy = legacy;
        this.needs = Collections.unmodifiableList(needs);
        this.expectedOutputs = Collections.unmodifiableMap(new LinkedHashMap<>(expectedOutputs));
        this.chancedOutputs = chancedOutputs == null ? Collections.emptyList()
                : Collections.unmodifiableList(chancedOutputs);
        this.outputsRemaining = new LinkedHashMap<>(expectedOutputs);
    }

    public UUID crafter() {
        return crafter;
    }

    public TicketState state() {
        return state;
    }

    public long stateSince() {
        return stateSince;
    }

    public String problem() {
        return problem;
    }

    public long delivered() {
        return delivered;
    }

    public Map<ResourceKey, Long> outputsRemaining() {
        return Collections.unmodifiableMap(outputsRemaining);
    }

    public Need need(int index) {
        for (Need n : needs) {
            if (n.index == index) return n;
        }
        return null;
    }

    public boolean isDelivery() {
        return kind == Kind.DELIVERY;
    }

    public long totalNeeded() {
        long sum = 0;
        for (Need n : needs) sum += n.qty;
        return sum;
    }

    @Override
    public String toString() {
        return "T" + id + "[" + kind + " " + state + " h" + height + " x" + crafts + " " + needs + "]";
    }
}
