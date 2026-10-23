package logisticspipes.crafting.v3.exec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import com.github.bsideup.jabel.Desugar;

import logisticspipes.crafting.v3.model.ResourceKey;
import logisticspipes.crafting.v3.model.ResourceMatcher;

/** A committed crafting request (plan section 2.7). */
public final class CraftingJob {

    static final Comparator<Ticket> OPEN_ORDER = Comparator.<Ticket>comparingInt(t -> t.height)
            .thenComparingLong(t -> t.seq).thenComparingLong(t -> t.id);

    public final long id;
    public final ResourceMatcher target;
    public final long qty;
    public final UUID requester;
    /** Player who placed the request, for messages; may be null (automation). */
    public final UUID player;
    public final long createdTick;

    JobState state = JobState.RUNNING;
    /** Set when the delivery finished and the job is only winding down leftovers. */
    boolean delivered;
    long windDownSince;
    final Map<Long, Ticket> tickets = new LinkedHashMap<>();
    final TreeSet<Ticket> planned = new TreeSet<>(OPEN_ORDER);
    /** height -> unfinished craft tickets, to find the lowest unfinished height quickly. */
    final TreeMap<Integer, Integer> unfinishedByHeight = new TreeMap<>();
    /** Tickets whose inputs may still need moving: OPEN craft tickets and the delivery. */
    final Set<Ticket> active = new LinkedHashSet<>();
    long deliveryTicket = -1;
    /** Bumped on every change the job view shows. */
    long revision;
    final List<NodeInfo> nodes = new ArrayList<>();
    /** Stall notice already sent to the player for the current stall. */
    boolean stallNotified;

    CraftingJob(long id, ResourceMatcher target, long qty, UUID requester, UUID player, long createdTick) {
        this.id = id;
        this.target = target;
        this.qty = qty;
        this.requester = requester;
        this.player = player;
        this.createdTick = createdTick;
    }

    public JobState state() {
        return state;
    }

    public Collection<Ticket> tickets() {
        return Collections.unmodifiableCollection(tickets.values());
    }

    public List<NodeInfo> nodes() {
        return Collections.unmodifiableList(nodes);
    }

    public Ticket delivery() {
        return tickets.get(deliveryTicket);
    }

    public boolean isDelivered() {
        return delivered;
    }

    public long revision() {
        return revision;
    }

    void addTicket(Ticket t) {
        tickets.put(t.id, t);
        if (t.kind == Ticket.Kind.CRAFT) {
            unfinishedByHeight.merge(t.height, 1, Integer::sum);
        }
    }

    void ticketFinished(Ticket t) {
        if (t.kind != Ticket.Kind.CRAFT) return;
        unfinishedByHeight.computeIfPresent(t.height, (h, c) -> c <= 1 ? null : c - 1);
    }

    int minUnfinishedHeight() {
        return unfinishedByHeight.isEmpty() ? Integer.MAX_VALUE : unfinishedByHeight.firstKey();
    }

    /**
     * What one plan node turned into, for the job view.
     */
    @Desugar
    public record NodeInfo(int node, ResourceKey output, long crafts, UUID crafter, String crafterName, boolean legacy,
            int height) {}
}
