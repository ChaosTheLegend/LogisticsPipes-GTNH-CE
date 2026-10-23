package logisticspipes.crafting.v3.exec;

import java.util.UUID;

import logisticspipes.crafting.v3.model.ResourceKey;

/**
 * Items the driver routed in one go. The records it carries are found through {@link Ledger#forShipment}; the shipment
 * itself only remembers what left and when, for timeouts.
 */
public final class Shipment {

    public final long id;
    public final long job;
    public final UUID from;
    public final UUID to;
    public final ResourceKey key;
    /** Amount still travelling. */
    long qty;
    final long sentTick;
    /** Legacy crafter outputs: the ticket that produced them, so its completion can be tracked on arrival. */
    final long producerTicket;

    Shipment(long id, long job, UUID from, UUID to, ResourceKey key, long qty, long sentTick, long producerTicket) {
        this.id = id;
        this.job = job;
        this.from = from;
        this.to = to;
        this.key = key;
        this.qty = qty;
        this.sentTick = sentTick;
        this.producerTicket = producerTicket;
    }

    public long qty() {
        return qty;
    }

    @Override
    public String toString() {
        return "S" + id + "[" + qty + "x " + key.displayName() + "]";
    }
}
