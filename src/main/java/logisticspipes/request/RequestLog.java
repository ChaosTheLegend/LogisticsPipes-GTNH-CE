package logisticspipes.request;

import java.util.List;

import logisticspipes.request.resources.IResource;
import logisticspipes.routing.order.LinkedLogisticsOrderList;

public interface RequestLog {

    void handleMissingItems(List<IResource> resources);

    void handleSucessfullRequestOf(IResource item, LinkedLogisticsOrderList paticipating);

    /**
     * Called right before {@link #handleSucessfullRequestOf} with how much of the request gets crafted, the rest comes
     * from what the network has.
     */
    default void handleCraftedAmount(int crafted) {}

    void handleSucessfullRequestOfList(List<IResource> resources, LinkedLogisticsOrderList paticipating);
}
