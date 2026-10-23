package logisticspipes.crafting.v3.exec;

import java.util.UUID;

import logisticspipes.crafting.v3.model.ResourceKey;

/**
 * Ownership record for some of a job's items (plan section 2.5). Fields are changed only through {@link Ledger} so its
 * indexes stay consistent.
 */
public final class LedgerRecord {

    public static final long UNBOUND = -1;

    final long id;
    final long job;
    ResourceKey key;
    long qty;
    RecordState state;
    /** Provider for RESERVED, crafter/requester for BUFFERED, INSERTED, LEFTOVER, destination for IN_TRANSIT. */
    UUID location;
    long ticket = UNBOUND;
    int need = -1;
    /** Producing node for EXPECTED records. */
    int producedBy = -1;
    /** Shipment carrying the items (IN_TRANSIT, and EXPECTED outputs a legacy crafter was told to send). */
    long shipment = -1;
    StallCause cause = StallCause.NONE;
    long nextRetry;
    int retryDelay;

    LedgerRecord(long id, long job, ResourceKey key, long qty, RecordState state) {
        this.id = id;
        this.job = job;
        this.key = key;
        this.qty = qty;
        this.state = state;
    }

    public long id() {
        return id;
    }

    public long job() {
        return job;
    }

    public ResourceKey key() {
        return key;
    }

    public long qty() {
        return qty;
    }

    public RecordState state() {
        return state;
    }

    public UUID location() {
        return location;
    }

    public long ticket() {
        return ticket;
    }

    public int need() {
        return need;
    }

    public int producedBy() {
        return producedBy;
    }

    public long shipment() {
        return shipment;
    }

    public StallCause cause() {
        return cause;
    }

    public boolean isBound() {
        return ticket != UNBOUND;
    }

    @Override
    public String toString() {
        return "R" + id
                + "["
                + state
                + " "
                + qty
                + "x "
                + key.displayName()
                + (isBound() ? " ->T" + ticket + "/" + need : "")
                + (producedBy >= 0 ? " from N" + producedBy : "")
                + (shipment >= 0 ? " S" + shipment : "")
                + (cause != StallCause.NONE ? " " + cause : "")
                + "]";
    }
}
