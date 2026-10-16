package logisticspipes.crafting;

import java.util.UUID;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.TimeUnit;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.crafting.patternStack.PatternFluidStack;
import logisticspipes.crafting.patternStack.PatternItemStack;
import logisticspipes.crafting.patternStack.PatternStackHelper;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.RequestTree;
import logisticspipes.utils.DelayedGeneric;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.tuples.Pair;

/** Tracks lost routed ingredients by their stable delivery and owning-order references. */
final class PatternLostIngredientHandler {

    private static final String LOST_INGREDIENTS_TAG = "patternLostIngredients";
    private static final String LOST_DELAY_TAG = "delay";
    private static final int TAG_COMPOUND = 10;
    private static final int LOST_RETRY_DELAY = 5000;

    private final DelayQueue<DelayedGeneric<Pair<IPatternStack, IAdditionalTargetInformation>>> lostIngredients = new DelayQueue<>();
    private final ModulePatternCrafting module;
    private final PipeItemsPatternCraftingLogistics pipe;
    private final PatternStackRequestHandler requestedIngredient;

    PatternLostIngredientHandler(ModulePatternCrafting module, PipeItemsPatternCraftingLogistics pipe,
            PatternStackRequestHandler requestedIngredient) {
        this.module = module;
        this.pipe = pipe;
        this.requestedIngredient = requestedIngredient;
    }

    int size() {
        return lostIngredients.size();
    }

    void queue(IPatternStack stack, IAdditionalTargetInformation info, long delay) {
        if (stack == null || stack.getAmount() <= 0
                || !(info instanceof PatternTargetInformation target)
                || !target.isTracked()) {
            return;
        }
        lostIngredients.add(new DelayedGeneric<>(new Pair<>(stack, target), delay));
    }

    boolean clear() {
        if (lostIngredients.isEmpty()) {
            return false;
        }
        lostIngredients.clear();
        return true;
    }

    boolean removeInstance(UUID instanceId) {
        return lostIngredients.removeIf(queued -> {
            IAdditionalTargetInformation info = queued.get().getValue2();
            return info instanceof PatternTargetInformation target && target.orderReference() != null
                    && instanceId.equals(target.orderReference().instanceId());
        });
    }

    void fluidSendFailed(FluidIdentifier fluid, Integer amount) {
        module.debugEvent(
                "FLOW",
                "fluid send failed fluid=%s amount=%s; no delivery reference is available for a safe retry",
                fluid,
                amount);
    }

    void itemLost(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        if (item == null || !(info instanceof PatternTargetInformation target) || !target.isTracked()) {
            return;
        }
        module.debugEvent("FLOW", "ingredient lost item=%s delivery=%s", item, target.deliveryReference());
        FluidStack fluid = SimpleServiceLocator.logisticsFluidManager.getFluidFromContainer(item);
        IPatternStack lostStack;
        if (fluid != null) {
            lostStack = new PatternFluidStack(FluidIdentifier.get(fluid), fluid.amount);
        } else {
            lostStack = new PatternItemStack(item.clone());
        }
        queue(lostStack, target, LOST_RETRY_DELAY);
    }

    void retryLostItems() {
        DelayedGeneric<Pair<IPatternStack, IAdditionalTargetInformation>> lost = lostIngredients.poll();
        int rerequested = 0;
        while (lost != null && rerequested < 100) {
            Pair<IPatternStack, IAdditionalTargetInformation> pair = lost.get();
            PatternTargetInformation target = (PatternTargetInformation) pair.getValue2();
            if (!PatternCraftingInstanceRegistry.isCancelled(target.orderReference())) {
                IPatternStack stack = pair.getValue1();
                int received = requestLostIngredient(stack, target);
                if (received < stack.getAmount()) {
                    IPatternStack remaining = PatternStackHelper.copyWithAmount(stack, stack.getAmount() - received);
                    queue(remaining, target, 4500 + (int) (Math.random() * 1000));
                }
            }
            rerequested++;
            lost = lostIngredients.poll();
        }
    }

    void readFromNBT(NBTTagCompound tag) {
        lostIngredients.clear();
        NBTTagList lost = tag.getTagList(LOST_INGREDIENTS_TAG, TAG_COMPOUND);
        for (int i = 0; i < lost.tagCount(); i++) {
            NBTTagCompound stackTag = lost.getCompoundTagAt(i);
            IPatternStack stack = IPatternStack.readFromNBT(stackTag);
            IAdditionalTargetInformation info = PatternCraftingPersistence.readTargetInfoFromParent(stackTag);
            if (stack != null && stack.getAmount() > 0
                    && info instanceof PatternTargetInformation target
                    && target.isTracked()) {
                queue(stack, target, Math.max(1, stackTag.getLong(LOST_DELAY_TAG)));
            }
        }
    }

    void writeToNBT(NBTTagCompound tag) {
        NBTTagList lost = new NBTTagList();
        for (DelayedGeneric<Pair<IPatternStack, IAdditionalTargetInformation>> queued : lostIngredients) {
            Pair<IPatternStack, IAdditionalTargetInformation> pair = queued.get();
            IPatternStack stack = pair.getValue1();
            if (stack == null || stack.getAmount() <= 0) {
                continue;
            }
            NBTTagCompound stackTag = new NBTTagCompound();
            stack.writeToNBT(stackTag);
            stackTag.setLong(LOST_DELAY_TAG, Math.max(1, queued.getDelay(TimeUnit.NANOSECONDS) / 1000));
            PatternCraftingPersistence.writeTargetInfo(stackTag, pair.getValue2());
            lost.appendTag(stackTag);
        }
        tag.setTag(LOST_INGREDIENTS_TAG, lost);
    }

    private int requestLostIngredient(IPatternStack stack, PatternTargetInformation target) {
        int originalAmount = stack == null ? 0 : stack.getAmount();
        ItemIdentifierStack item = PatternStackHelper.asSolidStack(stack);
        int outstandingAmount = item == null ? requestedIngredient.amount(target.orderReference(), stack)
                : module.requestedItemAmount(target.orderReference(), target.patternSlot(), item.getItem());
        IPatternStack outstanding = PatternStackHelper
                .copyWithAmount(stack, Math.min(originalAmount, outstandingAmount));
        if (outstanding == null || outstanding.getAmount() <= 0) {
            return originalAmount;
        }
        PatternTargetInformation retryTarget = PatternTargetInformation
                .delivery(target.patternSlot(), target.inputSlot(), target.orderReference());
        item = PatternStackHelper.asSolidStack(outstanding);
        if (item != null) {
            return originalAmount - outstanding.getAmount()
                    + RequestTree.requestPartial(item.clone(), pipe, retryTarget);
        }
        FluidIdentifier fluid = PatternStackHelper.asFluid(outstanding);
        if (fluid != null) {
            return originalAmount - outstanding.getAmount()
                    + RequestTree.requestFluidPartial(fluid, outstanding.getAmount(), module, null, retryTarget);
        }
        return 0;
    }
}
