package logisticspipes.logistics;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import logisticspipes.LogisticsPipes;
import logisticspipes.crafting.PipeItemsPatternSatelliteLogistics;
import logisticspipes.interfaces.routing.IFluidSink;
import logisticspipes.interfaces.routing.IProvideFluids;
import logisticspipes.items.LogisticsFluidContainer;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.routing.ExitRoute;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.PipeRoutingConnectionType;
import logisticspipes.routing.astar.InterestRegistry;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.SinkReply.FixedPriority;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.tuples.Pair;

public class LogisticsFluidManager implements ILogisticsFluidManager {

    /**
     * Only the routers that announced an interest in the fluid are asked, like for items. The closest sink with the
     * highest priority wins.
     */
    @Override
    public Pair<Integer, Integer> getBestReply(FluidStack stack, IRouter sourceRouter, List<Integer> jamList) {
        BitSet interested = InterestRegistry.getRoutersInterestedIn(FluidIdentifier.get(stack).getItemIdentifier());
        List<ExitRoute> candidates = new ArrayList<>();
        for (int i = interested.nextSetBit(0); i >= 0; i = interested.nextSetBit(i + 1)) {
            if (i == sourceRouter.getSimpleID() || jamList.contains(i)) continue;
            IRouter router = SimpleServiceLocator.routerManager.getRouterUnsafe(i, false);
            if (router == null) continue;
            List<ExitRoute> exits = sourceRouter.getDistanceTo(router);
            if (exits == null) continue;
            for (ExitRoute exit : exits) {
                if (exit.containsFlag(PipeRoutingConnectionType.canRouteTo)) {
                    candidates.add(exit);
                }
            }
        }
        Collections.sort(candidates);

        int bestRouter = 0;
        int bestAmount = 0;
        FixedPriority bestPriority = null;
        for (ExitRoute candidateRouter : candidates) {
            CoreRoutedPipe pipe = candidateRouter.destination.getPipe();
            if (pipe == null || !pipe.isEnabled()) continue;
            if (!(pipe instanceof IFluidSink iFluidSink)) continue;
            if (pipe instanceof PipeItemsPatternCraftingLogistics || pipe instanceof PipeItemsPatternSatelliteLogistics)
                continue;

            FixedPriority priority = iFluidSink.getFluidSinkPriority(stack);
            // the candidates are sorted by distance, a later one has to be more important
            if (bestPriority != null && priority.ordinal() <= bestPriority.ordinal()) continue;

            int amount = iFluidSink.sinkAmount(stack);
            if (amount > 0) {
                bestRouter = candidateRouter.destination.getSimpleID();
                bestAmount = amount;
                bestPriority = priority;
            }
        }
        return new Pair<>(bestRouter, bestAmount);
    }

    @Override
    public ItemIdentifierStack getFluidContainer(FluidStack stack) {
        ItemStack item = new ItemStack(LogisticsPipes.LogisticsFluidContainer, 1);
        NBTTagCompound nbt = new NBTTagCompound();
        stack.writeToNBT(nbt);
        item.setTagCompound(nbt);
        return ItemIdentifierStack.getFromStack(item);
    }

    @Override
    public FluidStack getFluidFromContainer(ItemIdentifierStack stack) {
        if (stack.getItem().item instanceof LogisticsFluidContainer && stack.getItem().tag != null) {
            return FluidStack.loadFluidStackFromNBT(stack.getItem().tag);
        }

        // Support for GregTech fluid containers
        if (stack.getItem().tag != null && stack.getItem().tag.hasKey("GT.FluidContent", 10)) {
            return FluidStack.loadFluidStackFromNBT(stack.getItem().tag.getCompoundTag("GT.FluidContent"));
        }

        return null;
    }

    @Override
    public TreeSet<ItemIdentifierStack> getAvailableFluid(List<ExitRoute> validDestinations) {
        Map<FluidIdentifier, Integer> allAvailableItems = new HashMap<>();
        for (ExitRoute r : validDestinations) {
            if (r == null) {
                continue;
            }
            if (!r.containsFlag(PipeRoutingConnectionType.canRequestFrom)) {
                continue;
            }
            if (!(r.destination.getPipe() instanceof IProvideFluids)) {
                continue;
            }

            IProvideFluids provider = (IProvideFluids) r.destination.getPipe();
            Map<FluidIdentifier, Integer> allItems = provider.getAvailableFluids();

            for (Entry<FluidIdentifier, Integer> liquid : allItems.entrySet()) {
                Integer amount = allAvailableItems.get(liquid.getKey());
                if (amount == null) {
                    allAvailableItems.put(liquid.getKey(), liquid.getValue());
                } else {
                    long addition = ((long) amount) + liquid.getValue();
                    if (addition > Integer.MAX_VALUE) {
                        addition = Integer.MAX_VALUE;
                    }
                    allAvailableItems.put(liquid.getKey(), (int) addition);
                }
            }
        }
        TreeSet<ItemIdentifierStack> itemIdentifierStackList = new TreeSet<>();
        for (Entry<FluidIdentifier, Integer> item : allAvailableItems.entrySet()) {
            itemIdentifierStackList.add(new ItemIdentifierStack(item.getKey().getItemIdentifier(), item.getValue()));
        }
        return itemIdentifierStackList;
    }
}
