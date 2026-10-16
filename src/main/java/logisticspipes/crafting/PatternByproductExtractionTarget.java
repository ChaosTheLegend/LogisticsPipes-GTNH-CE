package logisticspipes.crafting;

import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.routing.IRouter;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;

/**
 * A pattern satellite that may extract an expected crafting byproduct from its adjacent machine.
 */
interface PatternByproductExtractionTarget {

    /**
     * Checks the upgrade, loaded state and routing connection immediately before an extraction attempt.
     */
    boolean canExtractByproductsFor(IRouter requester);

    /**
     * Extracts at most one item stack and queues it for the supplied requester or normal storage routing.
     */
    PatternByproductExtractionResult extractItemByproduct(ItemIdentifier item, int amount, int destination,
            IAdditionalTargetInformation info);

    /**
     * Extracts one fluid parcel and queues it for the supplied requester or normal storage routing.
     */
    PatternByproductExtractionResult extractFluidByproduct(FluidIdentifier fluid, int amount, int destination,
            IAdditionalTargetInformation info);
}
