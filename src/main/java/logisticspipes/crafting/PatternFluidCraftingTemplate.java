package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.List;

import logisticspipes.interfaces.routing.ICraftFluids;
import logisticspipes.interfaces.routing.IProvideItems;
import logisticspipes.request.FluidCraftingTemplate;
import logisticspipes.request.IExtraPromise;
import logisticspipes.request.resources.FluidResource;
import logisticspipes.utils.FluidIdentifierStack;
import logisticspipes.utils.item.ItemIdentifierStack;

public class PatternFluidCraftingTemplate extends FluidCraftingTemplate {

    private final FluidResource result;
    private final ICraftFluids crafter;
    private final int patternSlot;
    private PatternByproductTarget outputTarget;

    public void setOutputTarget(PatternByproductTarget target) {
        outputTarget = target;
    }

    public int getPatternSlot() {
        return patternSlot;
    }

    private final List<ItemByproduct> itemByproducts = new ArrayList<>();
    private final List<FluidByproduct> fluidByproducts = new ArrayList<>();

    public PatternFluidCraftingTemplate(FluidResource result, ICraftFluids crafter, int priority, int patternSlot) {
        super(result, crafter, priority);
        this.result = result;
        this.crafter = crafter;
        this.patternSlot = patternSlot;
    }

    /**
     * Creates a fluid promise that keeps the source pattern slot and per-set fluid amount.
     * <p>
     * The staged crafting module uses those values to request the matching ingredient sets gradually and to drain the
     * correct amount of crafted fluid from the connected handler.
     */
    @Override
    public PatternFluidCraftingPromise generatePromise(int nResultSets) {
        PatternFluidCraftingPromise promise = new PatternFluidCraftingPromise(
                result.getFluid(),
                result.getRequestedAmount() * nResultSets,
                crafter,
                patternSlot,
                result.getRequestedAmount());
        promise.setByproductTarget(outputTarget);
        return promise;
    }

    @Override
    public void addByproduct(ItemIdentifierStack stack) {
        addByproduct(stack, null);
    }

    public void addByproduct(ItemIdentifierStack stack, PatternByproductTarget target) {
        if (stack != null && stack.getStackSize() > 0) {
            itemByproducts.add(new ItemByproduct(stack.clone(), target));
        }
    }

    @Override
    public void addFluidByproduct(FluidIdentifierStack stack) {
        addFluidByproduct(stack, null);
    }

    public void addFluidByproduct(FluidIdentifierStack stack, PatternByproductTarget target) {
        if (stack != null && stack.getStackSize() > 0) {
            fluidByproducts.add(
                    new FluidByproduct(
                            new FluidIdentifierStack(stack.getFluidIdentifier(), stack.getStackSize()),
                            target));
        }
    }

    @Override
    public List<IExtraPromise> getByproducts(int workSets) {
        List<IExtraPromise> result = new ArrayList<>();
        if (crafter instanceof IProvideItems itemProvider) {
            for (ItemByproduct byproduct : itemByproducts) {
                result.add(
                        new PatternItemByproductPromise(
                                byproduct.stack.getItem(),
                                byproduct.stack.getStackSize() * workSets,
                                itemProvider,
                                false,
                                byproduct.target));
            }
        }
        for (FluidByproduct byproduct : fluidByproducts) {
            result.add(
                    new PatternFluidByproductPromise(
                            byproduct.stack.getFluidIdentifier(),
                            byproduct.stack.getStackSize() * workSets,
                            crafter,
                            false,
                            byproduct.target));
        }
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
