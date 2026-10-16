package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import logisticspipes.crafting.pattern.PatternRecipeSnapshot;
import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.crafting.patternStack.PatternFluidStack;
import logisticspipes.crafting.patternStack.PatternItemStack;
import logisticspipes.crafting.patternStack.PatternStackHelper;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * Builds and dispatches complete buffered ingredient sets for pattern crafting.
 * <p>
 * The main module owns the buffers and staged requests; this handler owns the target split between the local adjacent
 * inventory and configured pattern satellites.
 */
final class PatternSatelliteDispatchHandler {

    private final ModulePatternCrafting module;
    private final PipeItemsPatternCraftingLogistics pipe;
    private final AdjacentInventoryHandler adjacentInventory;

    PatternSatelliteDispatchHandler(ModulePatternCrafting module, PipeItemsPatternCraftingLogistics pipe,
            AdjacentInventoryHandler adjacentInventory) {
        this.module = module;
        this.pipe = pipe;
        this.adjacentInventory = adjacentInventory;
    }

    /**
     * Finds the largest buffered set count that can be sent to all configured targets right now.
     */
    DispatchPlan findInsertableBufferedPlan(PatternCraftingReference ownerReference, int patternSlot, ItemStack pattern,
            int maxSets) {
        for (int sets = maxSets; sets > 0; sets--) {
            List<PatternIngredientAssignment> assignments = module
                    .buildBufferedIngredientPlan(ownerReference, patternSlot, pattern, sets);
            if (assignments == null) {
                continue;
            }
            DispatchPlan dispatchPlan = buildDispatchPlan(ownerReference, patternSlot, pattern, assignments);
            if (dispatchPlan != null && dispatchPlan.canDispatch()) {
                return dispatchPlan;
            }
        }
        return null;
    }

    /**
     * Returns how many full pattern sets are represented by a concrete dispatch plan.
     */
    int insertedSetsFromPlan(ItemStack pattern, List<PatternIngredientAssignment> plan) {
        if (pattern == null || plan == null || plan.isEmpty()) {
            return 0;
        }
        PatternRecipeSnapshot configuredPattern = module.getPatternRecipe(pattern);
        if (configuredPattern == null) {
            return 0;
        }
        int sets = Integer.MAX_VALUE;
        for (PatternIngredientAssignment assignment : plan) {
            IPatternStack ingredient = configuredPattern.getInput(assignment.inputSlot());
            if (ingredient == null || ingredient.getAmount() <= 0) {
                continue;
            }
            sets = Math.min(sets, assignment.stack().getAmount() / ingredient.getAmount());
        }
        return sets == Integer.MAX_VALUE ? 0 : sets;
    }

    /**
     * Calculates how many complete sets can be inserted into the currently configured local and satellite targets.
     */
    int maxDispatchableSets(PatternCraftingReference ownerReference, ItemStack pattern, int maxSets) {
        if (pattern == null || maxSets <= 0) {
            return 0;
        }
        int low = 0;
        int high = maxSets;
        while (low < high) {
            int mid = low + (high - low + 1) / 2;
            List<PatternIngredientAssignment> assignments = buildPatternAssignments(pattern, mid);
            DispatchPlan plan = assignments == null ? null
                    : buildDispatchPlan(ownerReference, PatternTargetInformation.NO_PATTERN_SLOT, pattern, assignments);
            if (plan != null && plan.canDispatch()) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    private List<PatternIngredientAssignment> buildPatternAssignments(ItemStack pattern, int sets) {
        if (sets <= 0) {
            return null;
        }
        PatternRecipeSnapshot configuredPattern = module.getPatternRecipe(pattern);
        if (configuredPattern == null) {
            return null;
        }
        List<PatternIngredientAssignment> assignments = new ArrayList<>();
        for (int slot = 0; slot < configuredPattern.getIngredientSlotCount(); slot++) {
            IPatternStack stack = configuredPattern.getInput(slot);
            if (stack == null || stack.getAmount() <= 0) {
                continue;
            }
            long amount = (long) stack.getAmount() * sets;
            if (amount > Integer.MAX_VALUE) {
                return null;
            }
            assignments
                    .add(new PatternIngredientAssignment(slot, PatternStackHelper.copyWithAmount(stack, (int) amount)));
        }
        return assignments.isEmpty() ? null : assignments;
    }

    private DispatchPlan buildDispatchPlan(PatternCraftingReference ownerReference, int patternSlot, ItemStack pattern,
            List<PatternIngredientAssignment> assignments) {
        if (pattern == null || assignments == null || assignments.isEmpty()) {
            return null;
        }
        DispatchPlan plan = new DispatchPlan(ownerReference, patternSlot, pattern, assignments);
        PatternRecipeSnapshot configuredPattern = module.getPatternRecipe(pattern);
        if (configuredPattern == null) {
            return null;
        }
        for (PatternIngredientAssignment assignment : assignments) {
            IPatternStack configuredStack = configuredPattern.getInput(assignment.inputSlot());
            ItemIdentifierStack item = PatternStackHelper.asSolidStack(assignment.stack());
            if (item != null) {
                IRequestItems target = PatternStackHelper.isSolid(configuredStack)
                        ? module.getSatelliteTargetForInputSlot(configuredPattern.getPattern(), assignment.inputSlot())
                        : null;
                if (target instanceof PipeItemsPatternSatelliteLogistics satellite) {
                    plan.addItemSatellite(satellite, item.clone(), assignment.inputSlot());
                } else {
                    plan.addLocal(assignment);
                }
                continue;
            }
            FluidIdentifier fluid = PatternStackHelper.asFluid(assignment.stack());
            if (fluid != null) {
                IRequestFluid target = PatternStackHelper.isFluid(configuredStack) ? module
                        .getFluidSatelliteTargetForInputSlot(configuredPattern.getPattern(), assignment.inputSlot())
                        : null;
                if (target instanceof PipeFluidPatternSatelliteLogistics satellite) {
                    plan.addFluidSatellite(satellite, fluid, assignment.stack().getAmount());
                } else {
                    plan.addLocal(assignment);
                }
            }
        }
        return plan;
    }

    private List<PipeItemsPatternSatelliteLogistics> uniqueItemSatellites(List<ItemSatelliteAssignment> assignments) {
        List<PipeItemsPatternSatelliteLogistics> result = new ArrayList<>();
        for (ItemSatelliteAssignment assignment : assignments) {
            if (!result.contains(assignment.satellite)) {
                result.add(assignment.satellite);
            }
        }
        return result;
    }

    private List<PipeFluidPatternSatelliteLogistics> uniqueFluidSatellites(List<FluidSatelliteAssignment> assignments) {
        List<PipeFluidPatternSatelliteLogistics> result = new ArrayList<>();
        for (FluidSatelliteAssignment assignment : assignments) {
            if (!result.contains(assignment.satellite)) {
                result.add(assignment.satellite);
            }
        }
        return result;
    }

    private static final class ItemSatelliteAssignment {

        private final PipeItemsPatternSatelliteLogistics satellite;
        private final ItemIdentifierStack stack;
        private final int inputSlot;
        private boolean routed;
        private PatternCraftingReference deliveryReference;
        /** What still has to go to the satellite; the rest already left the buffer. */
        private int remaining;

        private ItemSatelliteAssignment(PipeItemsPatternSatelliteLogistics satellite, ItemIdentifierStack stack,
                int inputSlot) {
            this.satellite = satellite;
            this.stack = stack;
            this.inputSlot = inputSlot;
            this.remaining = stack.getStackSize();
        }
    }

    private static final class FluidSatelliteAssignment {

        private final PipeFluidPatternSatelliteLogistics satellite;
        private final FluidIdentifier fluid;
        private int amount;
        /** What still has to go to the satellite; the rest already left the buffer. */
        private int remaining;

        private FluidSatelliteAssignment(PipeFluidPatternSatelliteLogistics satellite, FluidIdentifier fluid,
                int amount) {
            this.satellite = satellite;
            this.fluid = fluid;
            this.amount = amount;
            this.remaining = amount;
        }
    }

    enum DispatchResult {
        /** Nothing went in; the buffer is unchanged. */
        NONE,
        /** Part of the set went in and left the buffer; the rest must follow before anything else. */
        PARTIAL,
        /** The whole set went in. */
        COMPLETE
    }

    /**
     * One set (or several) of buffered ingredients on its way into the target and its satellites.
     * <p>
     * Every amount that goes in leaves the buffer right away, per assignment, so a short insert can never leave items
     * both in the target and in the buffer (D1). What didn't fit stays buffered and is retried by the next
     * {@link #dispatch(PatternStackBufferHandler)}.
     */
    final class DispatchPlan {

        private final int patternSlot;
        private final PatternCraftingReference ownerReference;
        private final PatternCraftingReference batchReference;
        private final ItemStack pattern;
        private final List<PatternIngredientAssignment> assignments;
        private final List<PatternIngredientAssignment> localAssignments = new ArrayList<>();
        private final List<ItemSatelliteAssignment> itemSatelliteAssignments = new ArrayList<>();
        private final List<FluidSatelliteAssignment> fluidSatelliteAssignments = new ArrayList<>();
        private boolean started;

        private DispatchPlan(PatternCraftingReference ownerReference, int patternSlot, ItemStack pattern,
                List<PatternIngredientAssignment> assignments) {
            this.ownerReference = ownerReference;
            this.batchReference = ownerReference == null ? null : ownerReference.createChild();
            this.patternSlot = patternSlot;
            this.pattern = pattern;
            this.assignments = new ArrayList<>(assignments);
        }

        List<PatternIngredientAssignment> assignments() {
            return assignments;
        }

        int patternSlot() {
            return patternSlot;
        }

        PatternCraftingReference ownerReference() {
            return ownerReference;
        }

        private void addLocal(PatternIngredientAssignment assignment) {
            localAssignments.add(assignment);
        }

        private void addItemSatellite(PipeItemsPatternSatelliteLogistics satellite, ItemIdentifierStack stack,
                int inputSlot) {
            itemSatelliteAssignments.add(new ItemSatelliteAssignment(satellite, stack, inputSlot));
        }

        /**
         * Amounts of the same fluid for the same satellite are merged, so the room check covers all of them together.
         */
        private void addFluidSatellite(PipeFluidPatternSatelliteLogistics satellite, FluidIdentifier fluid,
                int amount) {
            for (FluidSatelliteAssignment existing : fluidSatelliteAssignments) {
                if (existing.satellite == satellite && existing.fluid.equals(fluid)) {
                    existing.amount += amount;
                    existing.remaining += amount;
                    return;
                }
            }
            fluidSatelliteAssignments.add(new FluidSatelliteAssignment(satellite, fluid, amount));
        }

        boolean canDispatch() {
            if (hasSatellites() && !pipe.hasAdvancedSatelliteUpgrade()) {
                return false;
            }
            if (!localAssignments.isEmpty()
                    && !adjacentInventory.canInsertPatternIngredients(pattern, localAssignments)) {
                return false;
            }
            boolean instantItems = module.hasInstantSatelliteUpgrade();
            boolean reserveSatellites = usesSatelliteReservations();
            for (ItemSatelliteAssignment assignment : itemSatelliteAssignments) {
                if ((reserveSatellites && !assignment.satellite.canReserveFor(pipe, batchReference))
                        || (reserveSatellites && !assignment.satellite.isPatternTargetEmpty())
                        || !assignment.satellite.canAcceptPatternInput(assignment.stack)
                        || (!instantItems && !canRouteToItemSatellite(assignment))) {
                    return false;
                }
            }
            for (FluidSatelliteAssignment assignment : fluidSatelliteAssignments) {
                if ((reserveSatellites && !assignment.satellite.canReserveFor(pipe, batchReference))
                        || (reserveSatellites && !assignment.satellite.isPatternTargetEmpty())
                        || !assignment.satellite.canAcceptPatternInput(assignment.fluid, assignment.amount)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Inserts whatever is still missing and takes exactly that out of {@code buffer}. The full room check and the
         * satellite reservations only happen before the first attempt; once part of the set is in, the rest is inserted
         * as room appears.
         */
        DispatchResult dispatch(PatternStackBufferHandler buffer) {
            if (!started && !canDispatch()) {
                return DispatchResult.NONE;
            }
            boolean reserveSatellites = usesSatelliteReservations();
            if (!started && reserveSatellites) {
                List<PipeItemsPatternSatelliteLogistics> reservedItemSatellites = new ArrayList<>();
                List<PipeFluidPatternSatelliteLogistics> reservedFluidSatellites = new ArrayList<>();
                if (!reserveSatellites(reservedItemSatellites, reservedFluidSatellites)) {
                    releaseSatellites(reservedItemSatellites, reservedFluidSatellites);
                    return DispatchResult.NONE;
                }
            }
            insertLocal(buffer);
            for (FluidSatelliteAssignment assignment : fluidSatelliteAssignments) {
                if (assignment.remaining <= 0) {
                    continue;
                }
                int inserted = assignment.satellite
                        .insertPatternInput(assignment.fluid, assignment.remaining, reserveSatellites);
                if (inserted > 0) {
                    assignment.remaining -= inserted;
                    buffer.remove(ownerReference, new PatternFluidStack(assignment.fluid, inserted), inserted);
                    started = true;
                }
            }
            boolean instantItems = module.hasInstantSatelliteUpgrade();
            for (ItemSatelliteAssignment assignment : itemSatelliteAssignments) {
                if (assignment.remaining <= 0) {
                    continue;
                }
                int inserted;
                if (instantItems) {
                    inserted = assignment.satellite.insertPatternInput(
                            new ItemIdentifierStack(assignment.stack.getItem(), assignment.remaining),
                            reserveSatellites);
                } else {
                    // routed items always leave in full
                    routeItemSatelliteAssignment(assignment, reserveSatellites);
                    assignment.routed = true;
                    inserted = assignment.remaining;
                }
                if (inserted > 0) {
                    assignment.remaining -= inserted;
                    buffer.remove(
                            ownerReference,
                            new PatternItemStack(new ItemIdentifierStack(assignment.stack.getItem(), inserted)),
                            inserted);
                    started = true;
                }
            }
            if (isComplete()) {
                return DispatchResult.COMPLETE;
            }
            if (!started) {
                releaseSatellites(
                        uniqueItemSatellites(itemSatelliteAssignments),
                        uniqueFluidSatellites(fluidSatelliteAssignments));
                return DispatchResult.NONE;
            }
            return DispatchResult.PARTIAL;
        }

        private void insertLocal(PatternStackBufferHandler buffer) {
            if (localAssignments.isEmpty()) {
                return;
            }
            int[] inserted = adjacentInventory.insertPatternIngredients(pattern, localAssignments);
            List<PatternIngredientAssignment> missing = new ArrayList<>();
            for (int i = 0; i < localAssignments.size(); i++) {
                PatternIngredientAssignment assignment = localAssignments.get(i);
                int amount = Math.max(0, Math.min(inserted[i], assignment.stack().getAmount()));
                if (amount > 0) {
                    buffer.remove(ownerReference, assignment.stack(), amount);
                    started = true;
                }
                if (amount < assignment.stack().getAmount()) {
                    IPatternStack rest = assignment.stack().copy();
                    rest.addAmount(-amount);
                    missing.add(new PatternIngredientAssignment(assignment.inputSlot(), rest));
                }
            }
            localAssignments.clear();
            localAssignments.addAll(missing);
        }

        private boolean isComplete() {
            if (!localAssignments.isEmpty()) {
                return false;
            }
            for (ItemSatelliteAssignment assignment : itemSatelliteAssignments) {
                if (assignment.remaining > 0) {
                    return false;
                }
            }
            for (FluidSatelliteAssignment assignment : fluidSatelliteAssignments) {
                if (assignment.remaining > 0) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Gives up on the rest of the set: pulls what already reached the satellites back to storage and releases them.
         * The parts still missing are in the buffer, which the caller flushes; items already in the local target stay
         * there, like any dispatched set.
         */
        void abandon() {
            for (ItemSatelliteAssignment assignment : itemSatelliteAssignments) {
                int delivered = assignment.stack.getStackSize() - assignment.remaining;
                if (delivered > 0) {
                    assignment.satellite.retrieveOrCancelToStorage(
                            new ItemIdentifierStack(assignment.stack.getItem(), delivered),
                            assignment.routed,
                            assignment.deliveryReference);
                }
            }
            for (FluidSatelliteAssignment assignment : fluidSatelliteAssignments) {
                int delivered = assignment.amount - assignment.remaining;
                if (delivered > 0) {
                    assignment.satellite.retrieveFluidToStorage(assignment.fluid, delivered);
                }
            }
            releaseSatellites(
                    uniqueItemSatellites(itemSatelliteAssignments),
                    uniqueFluidSatellites(fluidSatelliteAssignments));
        }

        PatternCraftingBlockingHandler.SatelliteBatch satelliteBatch() {
            if (!hasSatellites()) {
                return null;
            }
            return new SatelliteDispatchBatch(
                    batchReference,
                    patternSlot,
                    new ArrayList<>(itemSatelliteAssignments),
                    new ArrayList<>(fluidSatelliteAssignments));
        }

        private boolean hasSatellites() {
            return !itemSatelliteAssignments.isEmpty() || !fluidSatelliteAssignments.isEmpty();
        }

        private boolean usesSatelliteReservations() {
            return module.getEffectiveBlockingMode() != PipeItemsPatternCraftingLogistics.BlockingMode.OFF;
        }

        private boolean canRouteToItemSatellite(ItemSatelliteAssignment assignment) {
            return batchReference != null && assignment.satellite.getRouter() != null
                    && pipe.getRouter() != null
                    && pipe.getRouter()
                            .hasRoute(assignment.satellite.getRouter().getSimpleID(), true, assignment.stack.getItem());
        }

        private void routeItemSatelliteAssignment(ItemSatelliteAssignment assignment, boolean reserveSatellites) {
            if (reserveSatellites) {
                assignment.satellite.expectPatternInput(assignment.stack);
            }
            PatternTargetInformation target = PatternTargetInformation
                    .delivery(patternSlot, assignment.inputSlot, batchReference);
            assignment.deliveryReference = target.deliveryReference();
            int remaining = assignment.stack.getStackSize();
            int maxStackSize = Math.max(1, assignment.stack.getItem().getMaxStackSize());
            while (remaining > 0) {
                int sent = Math.min(remaining, maxStackSize);
                pipe.sendStack(
                        new ItemIdentifierStack(assignment.stack.getItem(), sent).makeNormalStack(),
                        assignment.satellite.getRouter().getSimpleID(),
                        CoreRoutedPipe.ItemSendMode.Normal,
                        target);
                remaining -= sent;
            }
        }

        private boolean reserveSatellites(List<PipeItemsPatternSatelliteLogistics> itemSatellites,
                List<PipeFluidPatternSatelliteLogistics> fluidSatellites) {
            for (PipeItemsPatternSatelliteLogistics satellite : uniqueItemSatellites(itemSatelliteAssignments)) {
                if (!satellite.reserveFor(pipe, batchReference)) {
                    return false;
                }
                itemSatellites.add(satellite);
            }
            for (PipeFluidPatternSatelliteLogistics satellite : uniqueFluidSatellites(fluidSatelliteAssignments)) {
                if (!satellite.reserveFor(pipe, batchReference)) {
                    return false;
                }
                fluidSatellites.add(satellite);
            }
            return true;
        }

        private void releaseSatellites(List<PipeItemsPatternSatelliteLogistics> itemSatellites,
                List<PipeFluidPatternSatelliteLogistics> fluidSatellites) {
            for (PipeItemsPatternSatelliteLogistics satellite : itemSatellites) {
                satellite.releaseReservation(pipe, batchReference);
            }
            for (PipeFluidPatternSatelliteLogistics satellite : fluidSatellites) {
                satellite.releaseReservation(pipe, batchReference);
            }
        }
    }

    private final class SatelliteDispatchBatch implements PatternCraftingBlockingHandler.SatelliteBatch {

        private final int patternSlot;
        private final PatternCraftingReference reference;
        private final List<ItemSatelliteAssignment> itemAssignments;
        private final List<FluidSatelliteAssignment> fluidAssignments;

        private SatelliteDispatchBatch(PatternCraftingReference batchReference, int patternSlot,
                List<ItemSatelliteAssignment> itemAssignments, List<FluidSatelliteAssignment> fluidAssignments) {
            this.reference = batchReference;
            this.patternSlot = patternSlot;
            this.itemAssignments = itemAssignments;
            this.fluidAssignments = fluidAssignments;
        }

        @Override
        public PatternCraftingReference ownerReference() {
            return reference;
        }

        @Override
        public int patternSlot() {
            return patternSlot;
        }

        @Override
        public boolean isConsumed() {
            for (PipeItemsPatternSatelliteLogistics satellite : uniqueItemSatellites(itemAssignments)) {
                if (!satellite.isReservationConsumed(pipe, reference)) {
                    return false;
                }
            }
            for (PipeFluidPatternSatelliteLogistics satellite : uniqueFluidSatellites(fluidAssignments)) {
                if (!satellite.isReservationConsumed(pipe, reference)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int size() {
            return uniqueItemSatellites(itemAssignments).size() + uniqueFluidSatellites(fluidAssignments).size();
        }

        @Override
        public void retrieveAndRelease() {
            for (ItemSatelliteAssignment assignment : itemAssignments) {
                assignment.satellite.retrieveOrCancelToStorage(
                        assignment.stack.clone(),
                        assignment.routed,
                        assignment.deliveryReference);
            }
            for (FluidSatelliteAssignment assignment : fluidAssignments) {
                assignment.satellite.retrieveFluidToStorage(assignment.fluid, assignment.amount);
            }
            release();
        }

        @Override
        public void release() {
            for (PipeItemsPatternSatelliteLogistics satellite : uniqueItemSatellites(itemAssignments)) {
                satellite.releaseReservation(pipe, reference);
            }
            for (PipeFluidPatternSatelliteLogistics satellite : uniqueFluidSatellites(fluidAssignments)) {
                satellite.releaseReservation(pipe, reference);
            }
        }
    }
}
