package logisticspipes.request;

import java.util.ArrayList;
import java.util.List;

import logisticspipes.interfaces.IStack;
import logisticspipes.interfaces.routing.ICraftFluids;
import logisticspipes.interfaces.routing.IProvideItems;
import logisticspipes.request.resources.FluidResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.routing.FluidExtraPromise;
import logisticspipes.routing.FluidLogisticsPromise;
import logisticspipes.routing.LogisticsExtraPromise;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.utils.FluidIdentifierStack;
import logisticspipes.utils.item.ItemIdentifierStack;

public class FluidCraftingTemplate extends BaseCraftingTemplate {

    private final FluidResource result;
    private final ICraftFluids crafter;
    private final List<ItemIdentifierStack> byproductItems = new ArrayList<>();
    private final List<FluidIdentifierStack> byproductFluids = new ArrayList<>();

    public FluidCraftingTemplate(FluidResource result, ICraftFluids crafter, int priority) {
        super(3, priority);
        this.result = result;
        this.crafter = crafter;
    }

    @Override
    public void addByproduct(ItemIdentifierStack byproductItem) {
        if (byproductItem == null || byproductItem.getStackSize() <= 0) {
            return;
        }
        for (ItemIdentifierStack existing : byproductItems) {
            if (existing.getItem().equals(byproductItem.getItem())) {
                existing.setStackSize(existing.getStackSize() + byproductItem.getStackSize());
                return;
            }
        }
        byproductItems.add(byproductItem.clone());
    }

    /**
     * Registers an additional fluid output for a fluid craft.
     * <p>
     * The requested fluid result is not stored here; this list only describes extra fluids that must be drained after
     * the craft so the adjacent handler is ready for later crafts.
     */
    public void addFluidByproduct(FluidIdentifierStack byproductFluid) {
        if (byproductFluid == null || byproductFluid.getStackSize() <= 0) {
            return;
        }
        for (int i = 0; i < byproductFluids.size(); i++) {
            FluidIdentifierStack existing = byproductFluids.get(i);
            if (existing.getFluidIdentifier().equals(byproductFluid.getFluidIdentifier())) {
                byproductFluids.set(
                        i,
                        new FluidIdentifierStack(
                                existing.getFluidIdentifier(),
                                existing.getStackSize() + byproductFluid.getStackSize()));
                return;
            }
        }
        byproductFluids
                .add(new FluidIdentifierStack(byproductFluid.getFluidIdentifier(), byproductFluid.getStackSize()));
    }

    @Override
    public List<IExtraPromise> getByproducts(int workSets) {
        List<IExtraPromise> list = new ArrayList<>();
        if (crafter instanceof IProvideItems) {
            for (ItemIdentifierStack stack : byproductItems) {
                list.add(
                        new LogisticsExtraPromise(
                                stack.getItem(),
                                stack.getStackSize() * workSets,
                                (IProvideItems) crafter,
                                false));
            }
        }
        for (FluidIdentifierStack stack : byproductFluids) {
            list.add(
                    new FluidExtraPromise(stack.getFluidIdentifier(), stack.getStackSize() * workSets, crafter, false));
        }
        return list;
    }

    @Override
    public boolean canCraft(IResource type) {
        if (type instanceof FluidResource) {
            return ((FluidResource) type).isFluidIdentifierSame(result.getFluid());
        }

        return false;
    }

    @Override
    public FluidLogisticsPromise generatePromise(int nResultSets) {
        return new FluidLogisticsPromise(
                result.getFluid(),
                result.getRequestedAmount() * nResultSets,
                crafter,
                IOrderInfoProvider.ResourceType.CRAFTING);
    }

    @Override
    public IResource getResultResource() {
        return result;
    }

    @Override
    public IStack getResultStack() {
        return new FluidIdentifierStack(result.getFluid(), result.getRequestedAmount());
    }

    @Override
    public ICraftFluids getCrafter() {
        return crafter;
    }
}
