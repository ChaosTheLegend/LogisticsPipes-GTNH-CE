package logisticspipes.request;

import logisticspipes.request.resources.IResource;

public interface IExtraPromise extends IPromise {

    /**
     * Registers this extra fluid with the crafting provider that produced it.
     * <p>
     * Registration creates a destinationless extra order. The crafting pipe must later drain that fluid even when no
     * request consumes it, otherwise the adjacent fluid handler can block subsequent crafts.
     */
    void registerExtras(IResource requestType);

    @Override
    IExtraPromise copy();

    boolean isProvided();

    void lowerAmount(int usedcount);

    void setAmount(int amount);
}
