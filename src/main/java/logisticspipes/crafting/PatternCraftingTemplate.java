package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.List;

import logisticspipes.crafting.pattern.ItemPattern;
import logisticspipes.interfaces.routing.ICraftFluids;
import logisticspipes.interfaces.routing.ICraftItems;
import logisticspipes.request.BaseCraftingTemplate;
import logisticspipes.request.IExtraPromise;
import logisticspipes.request.IPromise;
import logisticspipes.request.resources.IResource;
import logisticspipes.request.resources.ItemResource;
import logisticspipes.routing.FluidExtraPromise;
import logisticspipes.utils.FluidIdentifierStack;
import logisticspipes.utils.item.ItemIdentifierStack;

public class PatternCraftingTemplate extends BaseCraftingTemplate {

    private final ItemIdentifierStack result;
    private final List<ItemByproduct> byproducts = new ArrayList<>();
    private final List<FluidByproduct> fluidByproducts = new ArrayList<>();
    private final ICraftItems crafter;
    private final int patternSlot;
    private PatternByproductTarget outputTarget;

    public void setOutputTarget(PatternByproductTarget target) {
        outputTarget = target;
    }

    public int getPatternSlot() {
        return patternSlot;
    }

    public PatternCraftingTemplate(ItemIdentifierStack result, ICraftItems crafter, int priority, int patternSlot) {
        this(result, crafter, priority, patternSlot, ItemPattern.INGREDIENT_SLOTS);
    }

    public PatternCraftingTemplate(ItemIdentifierStack result, ICraftItems crafter, int priority, int patternSlot,
            int ingredientSlots) {
        super(ingredientSlots, priority);
        this.result = result;
        this.crafter = crafter;
        this.patternSlot = patternSlot;
    }

    /**
     * Registers an item output from the same pattern that is not the requested result.
     * <p>
     * These outputs become extra promises when the request tree decides to craft this template, so the crafting pipe
     * will later extract them from the connected inventory and route them to storage or a consumer.
     */
    public void addByproduct(ItemIdentifierStack stack) {
        addByproduct(stack, null);
    }

    public void addByproduct(ItemIdentifierStack stack, PatternByproductTarget target) {
        if (stack != null && stack.getStackSize() > 0) {
            byproducts.add(new ItemByproduct(stack.clone(), target));
        }
    }

    /**
     * Registers a fluid output from the same pattern that is not the requested item result.
     * <p>
     * Pattern item crafts may still produce fluid byproducts. They are tracked separately from item byproducts because
     * the request tree must create {@link FluidExtraPromise}s and the pipe must drain those fluids from a fluid
     * handler.
     */
    public void addFluidByproduct(FluidIdentifierStack stack) {
        addFluidByproduct(stack, null);
    }

    public void addFluidByproduct(FluidIdentifierStack stack, PatternByproductTarget target) {
        if (stack == null || stack.getStackSize() <= 0) {
            return;
        }
        fluidByproducts.add(
                new FluidByproduct(new FluidIdentifierStack(stack.getFluidIdentifier(), stack.getStackSize()), target));
    }

    /**
     * Creates extra promises for every item and fluid byproduct produced by the requested number of pattern work sets.
     * <p>
     * The promises are registered during request fulfilment and become destinationless extra orders. Once the craft has
     * run, those orders force the pipe to remove the byproducts from the adjacent inventory or fluid handler.
     */
    @Override
    public List<IExtraPromise> getByproducts(int workSets) {
        List<IExtraPromise> result = new ArrayList<>();
        for (ItemByproduct byproduct : byproducts) {
            result.add(
                    new PatternItemByproductPromise(
                            byproduct.stack.getItem(),
                            byproduct.stack.getStackSize() * workSets,
                            crafter,
                            false,
                            byproduct.target));
        }
        if (crafter instanceof ICraftFluids) {
            for (FluidByproduct byproduct : fluidByproducts) {
                result.add(
                        new PatternFluidByproductPromise(
                                byproduct.stack.getFluidIdentifier(),
                                byproduct.stack.getStackSize() * workSets,
                                (ICraftFluids) crafter,
                                false,
                                byproduct.target));
            }
        }
        return result;
    }

    /**
     * Creates the staged promise for the requested item result and records the pattern slot that produced it.
     * <p>
     * The slot and per-set result amount let {@link ModulePatternCrafting} request ingredients gradually while the
     * output order remains visible in the normal item order manager.
     */
    @Override
    public IPromise generatePromise(int nCraftingSetsNeeded) {
        PatternCraftingPromise promise = new PatternCraftingPromise(
                result.getItem(),
                result.getStackSize() * nCraftingSetsNeeded,
                crafter,
                patternSlot,
                result.getStackSize());
        promise.setByproductTarget(outputTarget);
        return promise;
    }

    @Override
    public ICraftItems getCrafter() {
        return crafter;
    }

    @Override
    public boolean canCraft(IResource requestType) {
        return requestType.matches(result.getItem(), IResource.MatchSettings.NORMAL);
    }

    @Override
    public IResource getResultResource() {
        return new ItemResource(result, null);
    }

    @Override
    public ItemIdentifierStack getResultStack() {
        return result;
    }

    private static final class ItemByproduct {

        private final ItemIdentifierStack stack;
        private final PatternByproductTarget target;

        private ItemByproduct(ItemIdentifierStack stack, PatternByproductTarget target) {
            this.stack = stack;
            this.target = target;
        }
    }

    private static final class FluidByproduct {

        private final FluidIdentifierStack stack;
        private final PatternByproductTarget target;

        private FluidByproduct(FluidIdentifierStack stack, PatternByproductTarget target) {
            this.stack = stack;
            this.target = target;
        }
    }
}
