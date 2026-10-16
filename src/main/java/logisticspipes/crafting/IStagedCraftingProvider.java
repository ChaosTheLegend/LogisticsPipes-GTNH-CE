package logisticspipes.crafting;

import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.request.IPromise;
import logisticspipes.request.resources.IResource;
import logisticspipes.routing.order.IOrderInfoProvider;

public interface IStagedCraftingProvider {

    IOrderInfoProvider fullFillStagedCrafting(IPromise promise, IResource requestType,
            IAdditionalTargetInformation info, PatternCraftingBranch branch);
}
