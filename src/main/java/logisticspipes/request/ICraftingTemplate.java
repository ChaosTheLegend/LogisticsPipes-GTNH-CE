package logisticspipes.request;

import java.util.List;

import logisticspipes.interfaces.IStack;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.ICraft;
import logisticspipes.request.resources.IResource;
import logisticspipes.routing.FluidExtraPromise;
import logisticspipes.utils.tuples.Pair;

public interface ICraftingTemplate extends Comparable<ICraftingTemplate> {

    List<Pair<IResource, IAdditionalTargetInformation>> getComponents(int nCraftingSets);

    /**
     * Builds the extra promises produced alongside the requested fluid result.
     * <p>
     * Item byproducts are routed through the item order manager, while fluid byproducts become
     * {@link FluidExtraPromise}s and are handled by the pattern fluid order manager.
     */
    List<IExtraPromise> getByproducts(int workSets);

    /**
     * Creates the fluid crafting promise for the requested number of result sets.
     * <p>
     * Subclasses such as {@code PatternFluidCraftingTemplate} add pattern-slot metadata for staged crafting.
     */
    IPromise generatePromise(int nCraftingSetsNeeded);

    ICraft getCrafter();

    int getPriority();

    /**
     * @return true when this template can produce the requested fluid identity.
     */
    boolean canCraft(IResource requestType);

    IResource getResultResource();

    IStack getResultStack();
}
