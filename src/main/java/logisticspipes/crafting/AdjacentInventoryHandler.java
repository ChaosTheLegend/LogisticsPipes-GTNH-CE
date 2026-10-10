package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import logisticspipes.crafting.pattern.PatternRecipeSnapshot;
import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.crafting.patternStack.PatternFluidStack;
import logisticspipes.crafting.patternStack.PatternItemStack;
import logisticspipes.crafting.patternStack.PatternStackHelper;
import logisticspipes.interfaces.IInventoryUtil;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.resources.IResource;
import logisticspipes.utils.AdjacentTile;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.InventoryHelper;
import logisticspipes.utils.SidedInventoryMinecraftAdapter;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.transactor.ITransactor;
import logisticspipes.utils.tuples.Pair;

class AdjacentInventoryHandler {

    private final ModulePatternCrafting module;
    private final PipeItemsPatternCraftingLogistics pipe;
    private final Map<ItemStack, Integer> patternSetCapacity = new IdentityHashMap<>();
    private long contentCacheTick = Long.MIN_VALUE;
    private net.minecraft.tileentity.TileEntity contentCacheTile;
    private ForgeDirection contentCacheOrientation = ForgeDirection.UNKNOWN;
    private boolean emptyCached;
    private boolean cachedEmpty;
    private boolean itemsEmptyCached;
    private boolean cachedItemsEmpty;

    AdjacentInventoryHandler(ModulePatternCrafting module, PipeItemsPatternCraftingLogistics pipe) {
        this.module = module;
        this.pipe = pipe;
    }

    void invalidate() {
        contentCacheTick = Long.MIN_VALUE;
        contentCacheTile = null;
        contentCacheOrientation = ForgeDirection.UNKNOWN;
        clearContentCacheValues();
    }

    AdjacentTile getConnected() {
        return pipe.getConnectedInventoryTile();
    }

    boolean isConnectedToPatternCraftingTable() {
        AdjacentTile connected = getConnected();
        return connected != null && connected.tile instanceof PatternLogisticsCraftingTableTileEntity;
    }

    public boolean hasConnectedTE() {
        AdjacentTile connected = getConnected();
        return connected != null && connected.tile != null;
    }

    List<AdjacentTile> locateFluidHandlers() {
        List<AdjacentTile> handlers = new ArrayList<>();
        AdjacentTile connected = getConnected();
        if (connected != null && connected.tile instanceof IFluidHandler) {
            handlers.add(connected);
        }
        return handlers;
    }

    int roomFor(AdjacentTile connected, ItemIdentifier item) {
        if (connected.tile instanceof PatternLogisticsCraftingTableTileEntity) {
            return ((PatternLogisticsCraftingTableTileEntity) connected.tile).roomForPatternPipeItem(item);
        }
        IInventoryUtil inv = SimpleServiceLocator.inventoryUtilFactory
                .getInventoryUtil(getInsertableInventory(connected), module.getInsertionOrientation(connected));
        return inv.roomForItem(item, 9999);
    }

    int availablePatternSets(ItemStack pattern) {
        AdjacentTile connected = getConnected();
        if (connected == null || pattern == null) {
            module.debug("adjacent capacity result=0 connected=%s pattern=%s", connected, pattern);
            return 0;
        }
        refreshContentCache(connected);
        Integer cached = patternSetCapacity.get(pattern);
        if (cached != null) {
            return cached;
        }
        int sets = Integer.MAX_VALUE;
        boolean hasIngredient = false;
        List<IPatternStack> localIngredients = module.getLocalAggregatedIngredients(pattern);
        List<ItemIdentifierStack> solidIngredients = getSolidIngredients(localIngredients);
        List<PatternFluidStack> fluidIngredients = getFluidIngredients(localIngredients);
        if (!solidIngredients.isEmpty()) {
            hasIngredient = true;
            if (connected.tile instanceof PatternLogisticsCraftingTableTileEntity) {
                sets = Math.min(
                        sets,
                        availablePatternSetsForPatternTable(
                                pattern,
                                (PatternLogisticsCraftingTableTileEntity) connected.tile));
            } else if (connected.tile instanceof IInventory) {
                sets = Math.min(sets, availablePatternSetsDisregardingSlots(solidIngredients, connected));
            } else {
                module.debug(
                        "adjacent capacity result=0: solid ingredients but connected tile is not inventory tile=%s",
                        connected.tile);
                return 0;
            }
        }
        if (!fluidIngredients.isEmpty()) {
            hasIngredient = true;
            if (!(connected.tile instanceof IFluidHandler)) {
                module.debug(
                        "adjacent capacity result=0: fluid ingredients but connected tile is not fluid handler tile=%s",
                        connected.tile);
                return 0;
            }
            sets = Math.min(sets, availablePatternSetsForFluids(fluidIngredients, connected));
        }
        int result = hasIngredient && sets != Integer.MAX_VALUE ? Math.max(0, sets) : 0;
        module.debug(
                "adjacent capacity result=%d solidIngredients=%d fluidIngredients=%d tile=%s",
                result,
                solidIngredients.size(),
                fluidIngredients.size(),
                connected.tile);
        patternSetCapacity.put(pattern, result);
        return result;
    }

    boolean canInsertPatternIngredients(ItemStack pattern, List<PatternIngredientAssignment> assignments) {
        AdjacentTile connected = getConnected();
        if (connected == null || assignments == null || assignments.isEmpty()) {
            return false;
        }
        if (connected.tile instanceof PatternLogisticsCraftingTableTileEntity table) {
            for (PatternIngredientAssignment assignment : assignments) {
                ItemIdentifierStack item = PatternStackHelper.asSolidStack(assignment.stack());
                if (item == null) {
                    return false;
                }
                ItemStack stack = item.makeNormalStack();
                if (table.roomForPatternPipeSlot(assignment.inputSlot(), stack) < stack.stackSize) {
                    return false;
                }
            }
            return true;
        }
        List<ItemIdentifierStack> solidIngredients = new ArrayList<>();
        List<PatternFluidStack> fluidIngredients = new ArrayList<>();
        for (PatternIngredientAssignment assignment : assignments) {
            ItemIdentifierStack item = PatternStackHelper.asSolidStack(assignment.stack());
            if (item != null) {
                solidIngredients.add(item.clone());
                continue;
            }
            if (assignment.stack() instanceof PatternFluidStack fluid) {
                fluidIngredients.add(fluid.copy());
            }
        }
        if (!solidIngredients.isEmpty()) {
            if (!(connected.tile instanceof IInventory)) {
                return false;
            }
            if (!canFitPatternSetsDisregardingSlots(getInsertableInventory(connected), solidIngredients, 1)) {
                return false;
            }
        }
        if (!fluidIngredients.isEmpty()) {
            if (!(connected.tile instanceof IFluidHandler handler)) {
                return false;
            }
            if (!canFitFluids(handler, getFluidInsertionOrientation(connected), fluidIngredients, 1)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Inserts the assignments into the connected target and returns how much of each actually went in, index by index.
     * Callers must take exactly these amounts out of their buffer: a short insert can leave part of the set in the
     * target, and counting it as not inserted would duplicate it (D1).
     */
    int[] insertPatternIngredients(ItemStack pattern, List<PatternIngredientAssignment> assignments) {
        int[] inserted = new int[assignments.size()];
        AdjacentTile connected = getConnected();
        if (connected == null) {
            return inserted;
        }
        if (connected.tile instanceof PatternLogisticsCraftingTableTileEntity table) {
            int[] tableInserted = table.insertPatternPlanFromPatternPipe(assignments);
            for (int amount : tableInserted) {
                if (amount > 0) {
                    invalidateContentCache();
                    break;
                }
            }
            return tableInserted;
        }
        for (int i = 0; i < assignments.size(); i++) {
            PatternIngredientAssignment assignment = assignments.get(i);
            ItemIdentifierStack item = PatternStackHelper.asSolidStack(assignment.stack());
            if (item != null) {
                inserted[i] = insert(item.clone());
            } else if (assignment.stack() instanceof PatternFluidStack fluid) {
                inserted[i] = insertFluid(fluid.copy());
            }
        }
        return inserted;
    }

    private int availablePatternSetsForFluids(List<PatternFluidStack> ingredients, AdjacentTile connected) {
        IFluidHandler handler = (IFluidHandler) connected.tile;
        ForgeDirection side = getFluidInsertionOrientation(connected);
        ingredients = mergeFluids(ingredients, 1);
        int sets = Integer.MAX_VALUE;
        for (PatternFluidStack ingredient : ingredients) {
            int upperBound = ingredient.getFluid().getFreeSpaceInsideTank(handler, side) / ingredient.getAmount();
            int low = 0;
            int high = upperBound;
            while (low < high) {
                int mid = low + (high - low + 1) / 2;
                FluidStack stack = ingredient.getFluid().makeFluidStack(ingredient.getAmount() * mid);
                if (handler.fill(side, stack, false) == stack.amount) {
                    low = mid;
                } else {
                    high = mid - 1;
                }
            }
            module.debug("adjacent fluid capacity ingredient=%s upperBound=%d sets=%d", ingredient, upperBound, low);
            sets = Math.min(sets, low);
        }
        if (sets == Integer.MAX_VALUE) {
            return 0;
        }
        if (ingredients.size() > 1) {
            // each fluid fits on its own; find how many sets fit when they all go in together
            int low = 0;
            int high = sets;
            while (low < high) {
                int mid = low + (high - low + 1) / 2;
                if (canFitFluids(handler, side, ingredients, mid)) {
                    low = mid;
                } else {
                    high = mid - 1;
                }
            }
            sets = low;
        }
        return Math.max(0, sets);
    }

    /**
     * Whether {@code sets} times the given fluids fit into the handler at the same time (D3).
     * <p>
     * {@code fill(simulate)} only answers for one fluid against the current contents, so two fluids aimed at a single
     * tank would both pass. Each fluid (amounts of the same fluid merged) must pass the simulation, and when there are
     * several distinct fluids they must also fit the reported tanks together: a fluid goes into tanks that already hold
     * it, then into empty tanks, and a tank never takes two fluids. A handler without tank info can't prove that, so it
     * only accepts one fluid at a time.
     */
    private boolean canFitFluids(IFluidHandler handler, ForgeDirection side, List<PatternFluidStack> fluids, int sets) {
        List<PatternFluidStack> merged = mergeFluids(fluids, sets);
        for (PatternFluidStack fluid : merged) {
            FluidStack stack = fluid.makeFluidStack();
            if (handler.fill(side, stack, false) < stack.amount) {
                return false;
            }
        }
        if (merged.size() <= 1) {
            return true;
        }
        FluidTankInfo[] tanks = handler.getTankInfo(side);
        if (tanks == null || tanks.length == 0) {
            return false;
        }
        FluidIdentifier[] contents = new FluidIdentifier[tanks.length];
        int[] room = new int[tanks.length];
        for (int i = 0; i < tanks.length; i++) {
            if (tanks[i] == null) {
                continue;
            }
            FluidStack held = tanks[i].fluid;
            boolean empty = held == null || held.amount <= 0;
            contents[i] = empty ? null : FluidIdentifier.get(held);
            room[i] = Math.max(0, tanks[i].capacity - (empty ? 0 : held.amount));
        }
        for (PatternFluidStack fluid : merged) {
            long remaining = fluid.getAmount();
            for (int i = 0; i < tanks.length && remaining > 0; i++) {
                if (contents[i] != null && contents[i].equals(fluid.getFluid())) {
                    int used = (int) Math.min(remaining, room[i]);
                    room[i] -= used;
                    remaining -= used;
                }
            }
            for (int i = 0; i < tanks.length && remaining > 0; i++) {
                if (tanks[i] != null && contents[i] == null && room[i] > 0) {
                    contents[i] = fluid.getFluid();
                    int used = (int) Math.min(remaining, room[i]);
                    room[i] -= used;
                    remaining -= used;
                }
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Copies the fluids multiplied by {@code sets}, with amounts of the same fluid added together.
     */
    private static List<PatternFluidStack> mergeFluids(List<PatternFluidStack> fluids, int sets) {
        List<PatternFluidStack> merged = new ArrayList<>();
        for (PatternFluidStack fluid : fluids) {
            long amount = (long) fluid.getAmount() * sets;
            PatternFluidStack existing = null;
            for (PatternFluidStack candidate : merged) {
                if (candidate.getFluid().equals(fluid.getFluid())) {
                    existing = candidate;
                    break;
                }
            }
            if (existing == null) {
                merged.add(new PatternFluidStack(fluid.getFluid(), (int) Math.min(Integer.MAX_VALUE, amount)));
            } else {
                existing.addAmount((int) Math.min(Integer.MAX_VALUE - existing.getAmount(), amount));
            }
        }
        return merged;
    }

    private List<ItemIdentifierStack> getSolidIngredients(List<IPatternStack> ingredients) {
        List<ItemIdentifierStack> result = new ArrayList<>();
        for (IPatternStack ingredient : ingredients) {
            ItemIdentifierStack stack = PatternStackHelper.asSolidStack(ingredient);
            if (stack != null) {
                result.add(stack.clone());
            }
        }
        return result;
    }

    private List<PatternFluidStack> getFluidIngredients(List<IPatternStack> ingredients) {
        List<PatternFluidStack> result = new ArrayList<>();
        for (IPatternStack ingredient : ingredients) {
            if (ingredient instanceof PatternFluidStack) {
                result.add(((PatternFluidStack) ingredient).copy());
            }
        }
        return result;
    }

    private int availablePatternSetsDisregardingSlots(List<ItemIdentifierStack> ingredients, AdjacentTile connected) {
        if (ingredients.isEmpty()) {
            return 0;
        }
        int upperBound = Integer.MAX_VALUE;
        for (ItemIdentifierStack ingredient : ingredients) {
            upperBound = Math.min(upperBound, roomFor(connected, ingredient.getItem()) / ingredient.getStackSize());
            module.debug("adjacent item upper bound ingredient=%s currentUpperBound=%d", ingredient, upperBound);
        }
        if (upperBound <= 0 || upperBound == Integer.MAX_VALUE) {
            module.debug("adjacent item capacity result=0 upperBound=%d", upperBound);
            return 0;
        }
        IInventory inventory = getInsertableInventory(connected);
        int low = 0;
        int high = upperBound;
        while (low < high) {
            int mid = low + (high - low + 1) / 2;
            if (canFitPatternSetsDisregardingSlots(inventory, ingredients, mid)) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        module.debug("adjacent item capacity sets=%d upperBound=%d", low, upperBound);
        return low;
    }

    /**
     * The target inventory as seen from the insertion side, so capacity checks see the same slots the insert uses.
     */
    private IInventory getInsertableInventory(AdjacentTile connected) {
        IInventory inventory = (IInventory) connected.tile;
        if (inventory instanceof net.minecraft.inventory.ISidedInventory) {
            return new SidedInventoryMinecraftAdapter(
                    (net.minecraft.inventory.ISidedInventory) inventory,
                    module.getInsertionOrientation(connected),
                    false);
        }
        return inventory;
    }

    private boolean canFitPatternSetsDisregardingSlots(IInventory inventory, List<ItemIdentifierStack> ingredients,
            int sets) {
        ItemStack[] snapshot = new ItemStack[inventory.getSizeInventory()];
        for (int i = 0; i < snapshot.length; i++) {
            ItemStack existing = inventory.getStackInSlot(i);
            snapshot[i] = existing == null ? null : existing.copy();
        }
        for (ItemIdentifierStack ingredient : ingredients) {
            ItemStack stack = ingredient.makeNormalStack();
            stack.stackSize = ingredient.getStackSize() * sets;
            if (!insertIntoSnapshot(inventory, snapshot, stack)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Simulates inserting one stack into an inventory snapshot without changing the real adjacent inventory.
     * <p>
     * Existing compatible stacks are filled first, then empty slots are populated. The caller uses the result to decide
     * how many complete pattern sets can be routed before any real items are requested.
     */
    private boolean insertIntoSnapshot(IInventory inventory, ItemStack[] snapshot, ItemStack stack) {
        ItemIdentifier stackIdentifier = ItemIdentifier.get(stack);
        if (stackIdentifier == null) {
            return false;
        }
        int remaining = stack.stackSize;
        for (int i = 0; i < snapshot.length && remaining > 0; i++) {
            ItemStack existing = snapshot[i];
            if (existing == null) {
                continue;
            }
            ItemIdentifier existingIdentifier = ItemIdentifier.get(existing);
            if (existingIdentifier == null || !existingIdentifier.equalsForCrafting(stackIdentifier)) {
                continue;
            }
            int room = Math.min(inventory.getInventoryStackLimit(), existing.getMaxStackSize()) - existing.stackSize;
            if (room <= 0) {
                continue;
            }
            int moved = Math.min(room, remaining);
            existing.stackSize += moved;
            remaining -= moved;
        }
        for (int i = 0; i < snapshot.length && remaining > 0; i++) {
            if (snapshot[i] != null || !inventory.isItemValidForSlot(i, stack)) {
                continue;
            }
            int moved = Math.min(remaining, Math.min(inventory.getInventoryStackLimit(), stack.getMaxStackSize()));
            ItemStack inserted = stack.copy();
            inserted.stackSize = moved;
            snapshot[i] = inserted;
            remaining -= moved;
        }
        return remaining <= 0;
    }

    private int availablePatternSetsForPatternTable(ItemStack pattern, PatternLogisticsCraftingTableTileEntity table) {
        int sets = Integer.MAX_VALUE;
        boolean hasIngredient = false;
        PatternRecipeSnapshot configuredPattern = module.getPatternRecipe(pattern);
        if (configuredPattern == null) {
            return 0;
        }
        for (int slot = 0; slot < configuredPattern.getIngredientSlotCount(); slot++) {
            IPatternStack patternStack = configuredPattern.getInput(slot);
            if (!(patternStack instanceof PatternItemStack)) {
                continue;
            }
            if (module.hasLinkedSatelliteAssignment(pattern, slot)) {
                continue;
            }
            ItemStack ingredient = patternStack.makePatternStack();
            hasIngredient = true;
            int room = table.roomForPatternPipeSlot(slot, ingredient);
            module.debug(
                    "adjacent pattern-table slot capacity inputSlot=%d ingredient=%s room=%d amount=%d",
                    slot,
                    patternStack,
                    room,
                    ingredient.stackSize);
            sets = Math.min(sets, room / ingredient.stackSize);
        }
        int result = hasIngredient ? Math.max(0, sets) : 0;
        module.debug("adjacent pattern-table capacity sets=%d", result);
        return result;
    }

    private int insert(ItemIdentifierStack item) {
        AdjacentTile connected = getConnected();
        if (connected == null || item.getStackSize() <= 0) {
            module.debug("adjacent item insert skipped connected=%s item=%s", connected, item);
            return 0;
        }
        int amount = item.getStackSize();
        if (amount <= 0) {
            module.debug("adjacent item insert skipped after clamp item=%s", item);
            return 0;
        }
        ItemStack toInsert = item.makeNormalStack();
        toInsert.stackSize = amount;
        if (connected.tile instanceof PatternLogisticsCraftingTableTileEntity) {
            int inserted = ((PatternLogisticsCraftingTableTileEntity) connected.tile).insertFromPatternPipe(toInsert);
            if (inserted > 0) {
                invalidateContentCache();
            }
            module.debug(
                    "adjacent item inserted into pattern table item=%s amount=%d inserted=%d",
                    item.getItem(),
                    amount,
                    inserted);
            return inserted;
        }
        ForgeDirection side = module.getInsertionOrientation(connected);
        ITransactor transactor = InventoryHelper.getTransactorFor(connected.tile, side);
        if (transactor == null) {
            module.debug("adjacent item insert failed: no transactor tile=%s item=%s", connected.tile, item);
            return 0;
        }
        ItemStack added = transactor.add(toInsert, side, true);
        int inserted = added != null ? added.stackSize : 0;
        if (inserted > 0) {
            invalidateContentCache();
        }
        module.debug(
                "adjacent item inserted tile=%s item=%s amount=%d inserted=%d",
                connected.tile,
                item.getItem(),
                amount,
                inserted);
        return inserted;
    }

    private int insertFluid(PatternFluidStack fluid) {
        AdjacentTile connected = getConnected();
        if (connected == null || !(connected.tile instanceof IFluidHandler handler) || fluid.getAmount() <= 0) {
            module.debug("adjacent fluid insert skipped connected=%s fluid=%s", connected, fluid);
            return 0;
        }
        int inserted = handler.fill(getFluidInsertionOrientation(connected), fluid.makeFluidStack(), true);
        if (inserted > 0) {
            invalidateContentCache();
        }
        module.debug("adjacent fluid inserted tile=%s fluid=%s inserted=%d", connected.tile, fluid, inserted);
        return inserted;
    }

    private ForgeDirection getFluidInsertionOrientation(AdjacentTile connected) {
        return module.getInsertionOrientation(connected);
    }

    boolean isEmpty(AdjacentTile connected) {
        return isEmpty(connected, true);
    }

    /**
     * Whether the target holds nothing. With {@code includeFluids} false the fluid tanks are ignored, for patterns that
     * put no fluid into the target.
     */
    boolean isEmpty(AdjacentTile connected, boolean includeFluids) {
        if (connected == null
                || (!(connected.tile instanceof IInventory) && !(connected.tile instanceof IFluidHandler))) {
            return true;
        }
        refreshContentCache(connected);
        if (includeFluids) {
            if (!emptyCached) {
                cachedEmpty = calculateEmpty(connected, true);
                emptyCached = true;
            }
            return cachedEmpty;
        }
        if (!itemsEmptyCached) {
            cachedItemsEmpty = calculateEmpty(connected, false);
            itemsEmptyCached = true;
        }
        return cachedItemsEmpty;
    }

    private boolean calculateEmpty(AdjacentTile connected, boolean includeFluids) {
        if (connected.tile instanceof PatternLogisticsCraftingTableTileEntity) {
            return ((PatternLogisticsCraftingTableTileEntity) connected.tile).isIdle();
        }
        if (connected.tile instanceof IInventory inventory) {
            for (int i = 0; i < inventory.getSizeInventory(); i++) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (stack != null && stack.stackSize > 0) {
                    return false;
                }
            }
        }
        if (includeFluids && connected.tile instanceof IFluidHandler) {
            FluidTankInfo[] tanks = ((IFluidHandler) connected.tile)
                    .getTankInfo(getFluidInsertionOrientation(connected));
            if (tanks != null) {
                for (FluidTankInfo tank : tanks) {
                    if (tank != null && tank.fluid != null && tank.fluid.amount > 0) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    ItemStack extract(IResource wanted, int count) {
        var tile = getConnected();
        if (tile == null) return null;

        if (tile.tile instanceof PatternLogisticsCraftingTableTileEntity) {
            if (!pipe.useEnergy(Math.min(count, wanted.getRequestedAmount()))) {
                module.debug(
                        "adjacent extract item failed: no energy for pattern table wanted=%s count=%d",
                        wanted,
                        count);
                return null;
            }
            ItemStack extracted = ((PatternLogisticsCraftingTableTileEntity) tile.tile).extractOutput(wanted, count);
            if (extracted != null && extracted.stackSize > 0) {
                invalidateContentCache();
            }
            module.debug(
                    "adjacent extracted from pattern table wanted=%s count=%d extracted=%s",
                    wanted,
                    count,
                    extracted);
            return extracted;
        }
        IInventory inventory = (IInventory) tile.tile;
        if (inventory instanceof net.minecraft.inventory.ISidedInventory) {
            inventory = new SidedInventoryMinecraftAdapter(
                    (net.minecraft.inventory.ISidedInventory) inventory,
                    tile.orientation.getOpposite(),
                    true);
        }
        IInventoryUtil util = SimpleServiceLocator.inventoryUtilFactory.getInventoryUtil(inventory, tile.orientation);
        ItemIdentifier item = wanted.getAsItem();
        int available = util.itemCount(item);
        if (available <= 0 || !pipe.useEnergy(Math.min(count, available))) {
            module.debug("adjacent extract item failed item=%s available=%d count=%d", item, available, count);
            return null;
        }
        ItemStack extracted = util.getMultipleItems(item, Math.min(count, available));
        if (extracted != null && extracted.stackSize > 0) {
            invalidateContentCache();
        }
        module.debug(
                "adjacent extracted item=%s available=%d count=%d extracted=%s",
                item,
                available,
                count,
                extracted);
        return extracted;
    }

    FluidStack extractFluid(AdjacentTile tile, PatternFluidStack wanted, int amount) {
        if (!(tile.tile instanceof IFluidHandler handler) || wanted == null || amount <= 0) {
            module.debug("adjacent extract fluid skipped tile=%s wanted=%s amount=%d", tile.tile, wanted, amount);
            return null;
        }
        ForgeDirection side = tile.orientation.getOpposite();
        FluidStack simulated = handler.drain(side, amount, false);
        if (simulated == null || simulated.amount <= 0
                || !wanted.getFluid().equals(logisticspipes.utils.FluidIdentifier.get(simulated))) {
            module.debug(
                    "adjacent extract fluid simulation failed wanted=%s amount=%d simulated=%s",
                    wanted,
                    amount,
                    simulated);
            return null;
        }
        if (!pipe.useEnergy(Math.min(amount, simulated.amount))) {
            module.debug(
                    "adjacent extract fluid failed: no energy wanted=%s amount=%d simulated=%d",
                    wanted,
                    amount,
                    simulated.amount);
            return null;
        }
        FluidStack drained = handler.drain(side, Math.min(amount, simulated.amount), true);
        if (drained != null && drained.amount > 0) {
            invalidateContentCache();
        }
        module.debug("adjacent extracted fluid wanted=%s amount=%d drained=%s", wanted, amount, drained);
        return drained;
    }

    /**
     * returns null if there is no te connected. returns null if there is no handler for the connected te
     *
     * @return the list of items extractable from this inventory
     */
    public List<ItemStack> getExtractableItems() {
        var connected = getConnected();
        if (connected == null) return null;

        var tile = connected.tile;

        if (tile instanceof PatternLogisticsCraftingTableTileEntity table) {
            var out = new ArrayList<ItemStack>();
            for (Pair<ItemStack, Integer> entry : table.getOutputInventory()) {
                if (entry == null || entry.getValue1() == null) continue;
                out.add(entry.getValue1());
            }
            return out;
        }

        if (tile instanceof IInventory inventory) {
            var invUtil = SimpleServiceLocator.inventoryUtilFactory.getInventoryUtil(inventory, connected.orientation);

            var out = new ArrayList<ItemStack>();

            for (var entry : invUtil.getItemsAndCount().entrySet()) {
                if (entry.getValue() == null) continue;
                out.add(entry.getKey().makeNormalStack(entry.getValue()));
            }
            return out;
        }

        return null;
    }

    private void refreshContentCache(AdjacentTile connected) {
        long tick = module.currentWorldTick();
        net.minecraft.tileentity.TileEntity tile = connected == null ? null : connected.tile;
        ForgeDirection orientation = connected == null ? ForgeDirection.UNKNOWN : connected.orientation;
        if (contentCacheTick == tick && contentCacheTile == tile && contentCacheOrientation == orientation) {
            return;
        }
        contentCacheTick = tick;
        contentCacheTile = tile;
        contentCacheOrientation = orientation;
        clearContentCacheValues();
    }

    private void invalidateContentCache() {
        clearContentCacheValues();
    }

    private void clearContentCacheValues() {
        patternSetCapacity.clear();
        emptyCached = false;
        itemsEmptyCached = false;
    }

}
