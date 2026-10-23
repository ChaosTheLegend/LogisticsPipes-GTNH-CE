package logisticspipes.crafting.v3.exec;

import com.github.bsideup.jabel.Desugar;

import logisticspipes.interfaces.routing.IAdditionalTargetInformation;

/**
 * Travels with every item the crafting driver routes: the job, the shipment (which knows the ledger records) and the
 * ticket the items were sent for. A crafter may still assign them to another ticket of the same job, never to another
 * job.
 * <p>
 * It is handed to the transport as target information. The routed item keeps it in its own
 * {@code ItemRoutingInformation.craftDelivery} field and carries {@link #inner} as the visible target information, so a
 * requester still gets whatever target information its request asked for (chassis slot, player delivery, ...). Never
 * written into item NBT.
 *
 * @param inner target information the destination expects, or null
 */
@Desugar
public record CraftDelivery(long job, long shipment, long ticket, IAdditionalTargetInformation inner)
        implements IAdditionalTargetInformation {

    public CraftDelivery(long job, long shipment, long ticket) {
        this(job, shipment, ticket, null);
    }

    public CraftDelivery withInner(IAdditionalTargetInformation info) {
        return new CraftDelivery(job, shipment, ticket, info);
    }
}
