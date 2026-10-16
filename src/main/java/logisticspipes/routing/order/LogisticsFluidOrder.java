package logisticspipes.routing.order;

import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.routing.IRouter;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import lombok.Getter;

public class LogisticsFluidOrder extends LogisticsOrder {

    /**
     * Creates a fluid order for either a real requester or a destinationless extra output.
     * <p>
     * Extra orders intentionally allow {@code destination == null}; they are drained from the crafting handler and sent
     * back into the network for storage routing, or dropped if no storage can accept them.
     */
    public LogisticsFluidOrder(FluidIdentifier fuild, Integer amount, IRequestFluid destination, ResourceType type,
            IAdditionalTargetInformation info) {
        super(type, info);
        fluid = fuild;
        this.amount = amount;
        this.destination = destination;
    }

    @Getter
    private final FluidIdentifier fluid;

    @Getter
    private int amount;

    @Getter
    private final IRequestFluid destination;

    @Override
    public ItemIdentifierStack getAsDisplayItem() {
        return fluid.getItemIdentifier().makeStack(amount);
    }

    @Override
    public IRouter getRouter() {
        if (destination == null) {
            return null;
        }
        return destination.getRouter();
    }

    @Override
    public void sendFailed() {
        if (destination == null) {
            return;
        }
        destination.sendFailed(fluid, amount);
    }

    @Override
    public void reduceAmountBy(int reduce) {
        amount -= reduce;
    }
}
