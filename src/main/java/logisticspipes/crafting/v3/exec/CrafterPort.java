package logisticspipes.crafting.v3.exec;

import java.util.UUID;

import logisticspipes.crafting.v3.model.BlockingMode;
import logisticspipes.crafting.v3.model.ResourceKey;

/**
 * Driver-to-crafter messages (plan section 6.1). Every call is idempotent: repeating one for a ticket the crafter
 * already handled does nothing.
 * <p>
 * The crafter holds physical items tagged by owner: a ticket's needs, or the job pool (outputs waiting for their
 * consumer, leftovers, strays). The driver moves items between owners with {@link #assign} and out with {@link #ship}.
 */
public interface CrafterPort {

    /** Old crafting module/pipe: no buffer, items go straight into the machine, outputs leave through orders. */
    boolean isLegacy();

    BlockingMode mode();

    /** Create the needs entry for the ticket. */
    void open(Ticket ticket);

    /** Insert the ticket's set as soon as the blocking mode allows. */
    void commit(Ticket ticket);

    /** Drop the ticket; its buffered items go to the job pool. Inserted sets keep running. */
    void cancel(long ticket, long job);

    /** Amount of {@code key} the job pool holds. */
    long pooled(long job, ResourceKey key);

    /** Move items from the job pool into an open ticket's need. Returns the amount moved. */
    long assign(long job, ResourceKey key, long qty, Ticket ticket, int need);

    /** Route items from the job pool to {@code destination}. Returns the amount that left. */
    long ship(long job, ResourceKey key, long qty, UUID destination, CraftDelivery info);

    /** Send everything in the job pool to storage. */
    void flushJob(long job);

    /** Legacy crafters: send {@code qty} of the crafted {@code key} to {@code destination} once made. */
    boolean legacyOrder(ResourceKey key, long qty, UUID destination, CraftDelivery info);
}
