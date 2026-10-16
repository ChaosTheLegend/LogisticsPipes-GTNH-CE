package logisticspipes.routing;

import logisticspipes.interfaces.routing.ICraftFluids;
import logisticspipes.interfaces.routing.IProvideFluids;
import logisticspipes.request.IExtraPromise;
import logisticspipes.request.resources.IResource;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.utils.FluidIdentifier;

public class FluidExtraPromise extends FluidLogisticsPromise implements IExtraPromise {

    private boolean provided;

    public FluidExtraPromise(FluidIdentifier liquid, int amount, IProvideFluids sender, boolean provided) {
        super(liquid, amount, sender, IOrderInfoProvider.ResourceType.EXTRA);
        this.provided = provided;
    }

    @Override
    public void registerExtras(IResource requestType) {
        if (getSender() instanceof ICraftFluids) {
            ((ICraftFluids) getSender()).registerExtras(this);
        }
    }

    @Override
    public FluidExtraPromise copy() {
        return new FluidExtraPromise(getLiquid(), getAmount(), getSender(), provided);
    }

    @Override
    public FluidExtraPromise copyWithAmount(int amount) {
        return new FluidExtraPromise(getLiquid(), amount, getSender(), provided);
    }

    @Override
    public boolean isProvided() {
        return provided;
    }

    @Override
    public void lowerAmount(int usedcount) {
        setAmount(getAmount() - usedcount);
    }

    @Override
    public void setAmount(int amount) {
        super.setAmount(amount);
    }
}
