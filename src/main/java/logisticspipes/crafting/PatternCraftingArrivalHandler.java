package logisticspipes.crafting;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import logisticspipes.crafting.pattern.PatternHandler;
import logisticspipes.crafting.patternStack.PatternFluidStack;
import logisticspipes.crafting.patternStack.PatternItemStack;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.CacheHolder.CacheTypes;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

/** Accepts routed item/fluid ingredients only when their crafting order and delivery references are present. */
final class PatternCraftingArrivalHandler {

    private final ModulePatternCrafting module;
    private final PipeItemsPatternCraftingLogistics pipe;
    private final PatternHandler patternHandler;
    private final PatternStackBufferHandler ingredientBuffer;
    private final PatternStackRequestHandler requestedIngredient;
    private final PatternCraftingIngredientPlanner ingredientPlanner;
    private final PatternCraftingCancelHandler cancelHandler;

    PatternCraftingArrivalHandler(ModulePatternCrafting module, PipeItemsPatternCraftingLogistics pipe,
            PatternHandler patternHandler, PatternStackBufferHandler ingredientBuffer,
            PatternStackRequestHandler requestedIngredient, PatternCraftingIngredientPlanner ingredientPlanner,
            PatternCraftingCancelHandler cancelHandler) {
        this.module = module;
        this.pipe = pipe;
        this.patternHandler = patternHandler;
        this.ingredientBuffer = ingredientBuffer;
        this.requestedIngredient = requestedIngredient;
        this.ingredientPlanner = ingredientPlanner;
        this.cancelHandler = cancelHandler;
    }

    void itemArrived(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        if (!(info instanceof PatternTargetInformation target) || !target.isTracked()
                || item == null
                || item.getStackSize() <= 0) {
            module.debugEvent("FLOW", "ignored untracked pattern arrival item=%s info=%s", item, info);
            return;
        }
        int patternSlot = target.patternSlot();
        FluidStack fluid = SimpleServiceLocator.logisticsFluidManager.getFluidFromContainer(item);
        if (cancelHandler.shouldRouteLateArrivalToStorage(target.orderReference())) {
            sendToStorage(target, item, fluid);
            return;
        }
        ItemStack pattern = module.getPatternStack(patternSlot);
        if (fluid != null) {
            fluidArrived(target, pattern, item, fluid);
        } else {
            solidItemArrived(target, pattern, item);
        }
    }

    private void solidItemArrived(PatternTargetInformation target, ItemStack pattern, ItemIdentifierStack item) {
        int patternSlot = target.patternSlot();
        if (pattern == null || ingredientPlanner.ingredientAmount(pattern, item.getItem()) <= 0) {
            module.debugEvent("FLOW", "item arrival rejected slot=%d item=%s pattern=%s", patternSlot, item, pattern);
            return;
        }
        int original = item.getStackSize();
        int requested = ingredientPlanner.requestedItemAmount(target.orderReference(), pattern, item.getItem());
        int accepted = Math.min(original, requested);
        module.debugEvent(
                "FLOW",
                "item arrived slot=%d item=%s original=%d requested=%d space=%d accepted=%d",
                patternSlot,
                item.getItem(),
                original,
                requested,
                requested,
                accepted);
        ingredientPlanner.removeRequestedItem(target.orderReference(), patternSlot, pattern, item.getItem(), accepted);
        int requestedAfter = ingredientPlanner.requestedItemAmount(target.orderReference(), pattern, item.getItem());
        if (accepted > 0) {
            ingredientBuffer.add(
                    target.orderReference(),
                    patternSlot,
                    new PatternItemStack(new ItemIdentifierStack(item.getItem(), accepted)));
            module.debugEvent(
                    "BUFFER",
                    "item buffered slot=%d item=%s accepted=%d requested=%d->%d buffered=%d completeSets=%d",
                    patternSlot,
                    item.getItem(),
                    accepted,
                    requested,
                    requestedAfter,
                    ingredientPlanner.bufferedItemAmount(patternSlot, pattern, item.getItem()),
                    module.completeBufferedSets(patternSlot));
            module.activateRunningCraftFromBuffer(patternSlot, target.orderReference());
            module.pushBufferedIngredientsFor(patternSlot);
        }
        item.setStackSize(original - accepted);
        if (accepted > 0) {
            pipe.getCacheHolder().trigger(CacheTypes.Inventory);
        }
    }

    private void fluidArrived(PatternTargetInformation target, ItemStack pattern, ItemIdentifierStack routedStack,
            FluidStack fluidStack) {
        int patternSlot = target.patternSlot();
        FluidIdentifier fluid = FluidIdentifier.get(fluidStack);
        if (pattern == null || patternHandler.fluidIngredientAmount(pattern, fluid) <= 0) {
            module.debugEvent(
                    "FLOW",
                    "fluid arrival rejected slot=%d fluid=%s amount=%d pattern=%s",
                    patternSlot,
                    fluid,
                    fluidStack == null ? 0 : fluidStack.amount,
                    pattern);
            return;
        }
        int original = fluidStack.amount;
        PatternFluidStack arriving = new PatternFluidStack(fluid, original);
        int requested = requestedIngredient.amount(target.orderReference(), arriving);
        int accepted = requested >= original ? original : 0;
        module.debugEvent(
                "FLOW",
                "fluid arrived slot=%d fluid=%s original=%d requested=%d space=%d accepted=%d",
                patternSlot,
                fluid,
                original,
                requested,
                requested,
                accepted);
        requestedIngredient.remove(target.orderReference(), patternSlot, new PatternFluidStack(fluid, accepted));
        int requestedAfter = requestedIngredient.amount(target.orderReference(), arriving);
        if (accepted > 0) {
            ingredientBuffer.add(target.orderReference(), patternSlot, new PatternFluidStack(fluid, accepted));
            module.debugEvent(
                    "BUFFER",
                    "fluid buffered slot=%d fluid=%s accepted=%d requested=%d->%d buffered=%d completeSets=%d",
                    patternSlot,
                    fluid,
                    accepted,
                    requested,
                    requestedAfter,
                    ingredientBuffer.amount(patternSlot, fluid),
                    module.completeBufferedSets(patternSlot));
            module.activateRunningCraftFromBuffer(patternSlot, target.orderReference());
            module.pushBufferedIngredientsFor(patternSlot);
            routedStack.setStackSize(0);
            pipe.getCacheHolder().trigger(CacheTypes.Inventory);
        }
    }

    private void sendToStorage(PatternTargetInformation target, ItemIdentifierStack item, FluidStack fluid) {
        module.debugEvent(
                "FLOW",
                "cancelled reference=%s slot=%d sends late ingredient to storage item=%s fluid=%s amount=%d",
                target.orderReference(),
                target.patternSlot(),
                item.getItem(),
                fluid == null ? "<none>" : FluidIdentifier.get(fluid),
                fluid == null ? item.getStackSize() : fluid.amount);
        pipe.sendStack(item.makeNormalStack(), -1, CoreRoutedPipe.ItemSendMode.Normal, null);
        item.setStackSize(0);
        pipe.getCacheHolder().trigger(CacheTypes.Inventory);
    }
}
