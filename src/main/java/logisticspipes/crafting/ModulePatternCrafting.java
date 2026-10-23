package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import logisticspipes.crafting.legacy.template.ICraftingTemplate;
import logisticspipes.crafting.pattern.AbstractPattern;
import logisticspipes.crafting.pattern.ItemPattern;
import logisticspipes.crafting.pattern.PatternHandler;
import logisticspipes.crafting.pattern.PatternRecipeSnapshot;
import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.crafting.patternStack.PatternFluidStack;
import logisticspipes.crafting.patternStack.PatternItemStack;
import logisticspipes.crafting.patternStack.PatternStackHelper;
import logisticspipes.crafting.v3.exec.CraftDelivery;
import logisticspipes.crafting.v3.exec.CrafterPort;
import logisticspipes.crafting.v3.exec.CraftingDriver;
import logisticspipes.crafting.v3.exec.Ticket;
import logisticspipes.crafting.v3.mc.CraftingService;
import logisticspipes.crafting.v3.mc.FluidKey;
import logisticspipes.crafting.v3.mc.ICraftingStockSource;
import logisticspipes.crafting.v3.mc.IV3Crafter;
import logisticspipes.crafting.v3.mc.ItemKey;
import logisticspipes.crafting.v3.mc.McKeyCodec;
import logisticspipes.crafting.v3.mc.PatternIds;
import logisticspipes.crafting.v3.model.BlockingMode;
import logisticspipes.crafting.v3.model.CrafterInfo;
import logisticspipes.crafting.v3.model.InputSpec;
import logisticspipes.crafting.v3.model.MatchRule;
import logisticspipes.crafting.v3.model.Pattern;
import logisticspipes.crafting.v3.model.ResourceAmount;
import logisticspipes.crafting.v3.model.ResourceKey;
import logisticspipes.crafting.v3.model.ResourceMatcher;
import logisticspipes.crafting.v3.pipe.CrafterRuntime;
import logisticspipes.interfaces.ISlotUpgradeManager;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.ICraftItems;
import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.interfaces.routing.IRequest;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.interfaces.routing.IRequireReliableTransport;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.pipes.basic.CoreRoutedPipe.ItemSendMode;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.IPromise;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTreeNode;
import logisticspipes.request.resources.IResource;
import logisticspipes.request.resources.ItemResource;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.ItemRoutingInformation;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.order.LogisticsOrder;
import logisticspipes.utils.AdjacentTile;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.FluidSendUtil;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.SinkReply.FixedPriority;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.item.SimpleStackInventory;
import logisticspipes.utils.tuples.Pair;
import logisticspipes.utils.tuples.Triplet;

/**
 * The pattern crafting pipe's logic: up to 9 patterns, and the crafter side of the v3 ticket protocol through a
 * {@link CrafterRuntime} (plan section 6). The pipe never crafts on its own: the crafting driver opens tickets, routes
 * their inputs here and commits them; this module buffers, inserts sets into the selected adjacent inventory (and
 * pattern satellites) under the blocking mode, and captures outputs.
 * <p>
 * It is no longer part of the request tree: requests that need crafting are planned by the v3 planner.
 */
public class ModulePatternCrafting extends LogisticsModule
        implements ICraftItems, IRequireReliableTransport, IV3Crafter, CrafterPort, ICraftingStockSource {

    /** Old buffer format (staged crafting); read once and handed back to storage. */
    private static final String LEGACY_BUFFER_TAG = "patternIngredientBuffer";
    private static final String RUNTIME_TAG = "patternCraftingV3";

    private final PipeItemsPatternCraftingLogistics pipe;
    private final SimpleStackInventory patternInventory = new SimpleStackInventory(9, "Patterns", 1);
    private final PatternHandler patternHandler;
    private final AdjacentInventoryHandler adjacentInventory;
    private final PatternCraftingUpgradeCache upgradeCache;
    private final CrafterRuntime runtime;
    private SinkReply sinkReply;
    private PipeItemsPatternCraftingLogistics.BlockingMode blockingMode = PipeItemsPatternCraftingLogistics.BlockingMode.OFF;
    private Map<Integer, Pattern> v3Patterns;
    private long v3PatternsVersion = -1;
    private boolean v3PatternsFluid;
    private boolean syncedWithDriver;
    private boolean hudDirty = true;
    private PatternCraftingHudState hudState = PatternCraftingHudState.empty();
    private long hudStateTick = Long.MIN_VALUE;

    public ModulePatternCrafting(PipeItemsPatternCraftingLogistics pipe) {
        this.pipe = pipe;
        patternHandler = new PatternHandler(patternInventory);
        adjacentInventory = new AdjacentInventoryHandler(this, pipe);
        upgradeCache = new PatternCraftingUpgradeCache(this);
        runtime = new CrafterRuntime(new Machine(), new Link());
        patternInventory.addListener(inventory -> {
            patternHandler.invalidate();
            adjacentInventory.invalidate();
            v3Patterns = null;
            if (pipe.container != null) {
                pipe.container.markDirty();
            }
            markHudStateDirty();
            pipe.listenedChanged();
        });
        _service = pipe;
        _world = pipe;
        registerPosition(ModulePositionType.IN_PIPE, 0);
    }

    // ================================================================== patterns

    public IInventory getPatternInventory() {
        return patternInventory;
    }

    public ItemStack getPatternStack(int slot) {
        return patternHandler.getConfiguredPatternStack(slot);
    }

    PatternRecipeSnapshot getPatternRecipe(ItemStack pattern) {
        return patternHandler.getRecipe(pattern);
    }

    public ItemStack getPatternItemStack(int slot) {
        if (slot < 0 || slot >= patternInventory.getSizeInventory()) {
            return null;
        }
        return patternInventory.getStackInSlot(slot);
    }

    public void markPatternInventoryDirty() {
        patternInventory.markDirty();
        markHudStateDirty();
    }

    public void onCraftingTargetChanged() {
        adjacentInventory.invalidate();
        markHudStateDirty();
    }

    public int assignSatelliteToAllPatternIngredients(int satelliteId, String satelliteUuid) {
        if (!hasAdvancedSatelliteUpgrade()) {
            return 0;
        }
        int changed = 0;
        for (int patternSlot = 0; patternSlot < patternInventory.getSizeInventory(); patternSlot++) {
            ItemStack patternStack = patternInventory.getStackInSlot(patternSlot);
            if (patternStack == null) {
                continue;
            }
            AbstractPattern pattern = ItemPattern.fromStack(patternStack);
            for (int inputSlot = 0; inputSlot < pattern.getIngredientSlotCount(); inputSlot++) {
                IPatternStack ingredient = pattern.getPatternStackInSlot(inputSlot);
                if (!PatternStackHelper.isSolid(ingredient)) {
                    continue;
                }
                pattern.setSatelliteTargetForInputSlot(inputSlot, satelliteId, satelliteUuid);
                changed++;
            }
        }
        if (changed > 0) {
            markPatternInventoryDirty();
        }
        return changed;
    }

    boolean supportsFluidCrafting() {
        return upgradeCache.supportsFluidCrafting();
    }

    boolean hasAdvancedSatelliteUpgrade() {
        return upgradeCache.hasAdvancedSatellite();
    }

    /**
     * Returns false for patterns with fluid entries while the pipe has no fluid crafting upgrade. Such patterns are not
     * offered to the network.
     */
    public boolean isPatternCraftingSupported(ItemStack pattern) {
        PatternRecipeSnapshot recipe = patternHandler.getRecipe(pattern);
        return recipe == null || !recipe.containsFluid() || supportsFluidCrafting();
    }

    public Set<ItemIdentifier> getCraftedItems() {
        return patternHandler.getCraftedItems(supportsFluidCrafting());
    }

    public Set<ItemIdentifier> getOutputItems() {
        return patternHandler.getOutputItems(supportsFluidCrafting());
    }

    /**
     * The configured patterns as the v3 planner sees them, by pattern slot. Pattern ids are content hashes, so the same
     * recipe in two pipes is one pattern with two crafters.
     */
    @Override
    public Map<Integer, Pattern> v3Patterns() {
        boolean fluid = supportsFluidCrafting();
        if (v3Patterns != null && v3PatternsVersion == patternHandler.getChangeVersion() && v3PatternsFluid == fluid) {
            return v3Patterns;
        }
        Map<Integer, Pattern> result = new LinkedHashMap<>();
        for (int slot = 0; slot < patternHandler.size(); slot++) {
            ItemStack stack = patternHandler.getConfiguredPatternStack(slot);
            if (stack == null || !isPatternCraftingSupported(stack)) continue;
            Pattern p = toV3Pattern(stack, patternHandler.getRecipe(slot));
            if (p != null) result.put(slot, p);
        }
        v3Patterns = Collections.unmodifiableMap(result);
        v3PatternsVersion = patternHandler.getChangeVersion();
        v3PatternsFluid = fluid;
        return v3Patterns;
    }

    private static Pattern toV3Pattern(ItemStack stack, PatternRecipeSnapshot recipe) {
        if (recipe == null || !recipe.isConfigured()) return null;
        AbstractPattern pattern = recipe.getPattern();
        MatchRule itemRule = recipe.isOreDictSubstitutionEnabled() ? MatchRule.ORE_DICT
                : recipe.isIgnoreNbtEnabled() ? MatchRule.IGNORE_NBT : MatchRule.EXACT;
        StringBuilder sig = new StringBuilder("pattern:").append(itemRule);
        List<InputSpec> inputs = new ArrayList<>();
        for (int slot = 0; slot < recipe.getIngredientSlotCount(); slot++) {
            IPatternStack in = recipe.getInput(slot);
            ResourceKey key = keyOf(in);
            if (key == null) continue;
            boolean consumed = pattern.isInputConsumed(slot);
            ResourceMatcher matcher = key.isFluid() ? ResourceMatcher.exact(key)
                    : ResourceMatcher.of(key, itemRule, null);
            inputs.add(new InputSpec(matcher, in.getAmount(), consumed));
            sig.append('|').append(PatternIds.signature(key)).append('x').append(in.getAmount())
                    .append(consumed ? "" : "!");
        }
        List<ResourceAmount> outputs = new ArrayList<>();
        int main = recipe.getMainOutputSlot();
        IPatternStack mainOut = recipe.getOutput(main);
        if (keyOf(mainOut) != null) outputs.add(new ResourceAmount(keyOf(mainOut), mainOut.getAmount()));
        for (int slot = 0; slot < recipe.getResultSlotCount(); slot++) {
            if (slot == main) continue;
            IPatternStack out = recipe.getOutput(slot);
            if (keyOf(out) != null) outputs.add(new ResourceAmount(keyOf(out), out.getAmount()));
        }
        if (inputs.isEmpty() || outputs.isEmpty()) return null;
        for (ResourceAmount o : outputs)
            sig.append("=>").append(PatternIds.signature(o.key())).append('x').append(o.amount());
        return new Pattern(PatternIds.hash(sig.toString()), inputs, outputs, null);
    }

    static ResourceKey keyOf(IPatternStack stack) {
        if (stack == null || stack.getAmount() <= 0) return null;
        if (stack instanceof PatternFluidStack) return FluidKey.of(((PatternFluidStack) stack).getFluid());
        ItemIdentifierStack item = PatternStackHelper.asSolidStack(stack);
        return item == null ? null : ItemKey.of(item.getItem());
    }

    /** Pattern slot holding {@code patternId} now (the player may have moved it), or -1. */
    private int slotOf(int preferred, String patternId) {
        Map<Integer, Pattern> patterns = v3Patterns();
        Pattern p = patterns.get(preferred);
        if (p != null && p.id().equals(patternId)) return preferred;
        for (Map.Entry<Integer, Pattern> e : patterns.entrySet()) {
            if (e.getValue().id().equals(patternId)) return e.getKey();
        }
        return -1;
    }

    // ================================================================== blocking mode

    public PipeItemsPatternCraftingLogistics.BlockingMode getBlockingMode() {
        return getEffectiveBlockingMode();
    }

    public void setBlockingMode(PipeItemsPatternCraftingLogistics.BlockingMode blockingMode) {
        PipeItemsPatternCraftingLogistics.BlockingMode next = blockingMode == null
                ? PipeItemsPatternCraftingLogistics.BlockingMode.OFF
                : blockingMode;
        if (adjacentInventory.isConnectedToPatternCraftingTable()) {
            next = PipeItemsPatternCraftingLogistics.BlockingMode.SMART;
        }
        if (this.blockingMode != next) {
            this.blockingMode = next;
            markHudStateDirty();
            if (pipe.container != null) pipe.container.markDirty();
        }
    }

    public boolean isBlockingModeFixed() {
        return adjacentInventory.isConnectedToPatternCraftingTable();
    }

    PipeItemsPatternCraftingLogistics.BlockingMode getEffectiveBlockingMode() {
        if (adjacentInventory.isConnectedToPatternCraftingTable()) {
            return PipeItemsPatternCraftingLogistics.BlockingMode.SMART;
        }
        return blockingMode;
    }

    private BlockingMode v3Mode() {
        switch (getEffectiveBlockingMode()) {
            case BLOCKING:
                return BlockingMode.BLOCKING;
            case SMART:
                return BlockingMode.SMART;
            default:
                return BlockingMode.NONE;
        }
    }

    // ================================================================== tick and arrivals

    @Override
    public void tick() {
        World world = pipe.getWorld();
        if (world == null || world.isRemote) return;
        CraftingDriver driver = CraftingService.INSTANCE.driver();
        if (driver == null) return;
        if (!syncedWithDriver) {
            // plan section 9.2: tell the driver what this crafter holds after a load
            syncedWithDriver = true;
            CraftingService.INSTANCE.registerCrafter(this);
            driver.onCrafterLoaded(pipe.getRouter().getId(), runtime.knownTickets(), runtime.poolJobs());
        }
        runtime.tick(world.getTotalWorldTime());
        if (runtime.isDirty()) {
            runtime.clearDirty();
            markHudStateDirty();
            if (pipe.container != null) pipe.container.markDirty();
        }
    }

    /**
     * A routed item carrying a crafting delivery reached this pipe: it goes into the buffer, assigned to an open ticket
     * of its job. The routed stack is emptied.
     */
    public void acceptDelivery(ItemRoutingInformation info) {
        CraftDelivery delivery = info.craftDelivery;
        info.craftDelivery = null;
        CraftingService.ResourceAmountView amount = CraftingService.amountOf(info.getItem());
        if (delivery == null || amount == null) return;
        long taken = runtime.accept(amount.key, amount.qty, delivery);
        if (taken > 0) info.getItem().setStackSize(0);
    }

    @Override
    public void itemArrived(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        // untracked items are not taken; the transport sends them on
    }

    @Override
    public void itemLost(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        // routed items of crafting jobs are tracked by the driver
    }

    @Override
    public SinkReply sinksItem(ItemIdentifier item, int bestPriority, int bestCustomPriority, boolean allowDefault,
            boolean includeInTransit) {
        // only the crafting driver sends items here, as active deliveries
        return null;
    }

    public int sinkAmount(FluidStack stack) {
        return 0;
    }

    // ================================================================== IV3Crafter / CrafterPort

    @Override
    public CrafterInfo crafterInfo() {
        return new CrafterInfo(
                pipe.getRouter().getId(),
                "Pattern crafting pipe at " + pipe.getX() + ", " + pipe.getY() + ", " + pipe.getZ(),
                v3Mode(),
                0,
                false,
                1);
    }

    @Override
    public CrafterPort port() {
        return this;
    }

    @Override
    public boolean isLegacy() {
        return false;
    }

    @Override
    public BlockingMode mode() {
        return v3Mode();
    }

    @Override
    public void open(Ticket ticket) {
        runtime.open(ticket);
        markHudStateDirty();
    }

    @Override
    public void commit(Ticket ticket) {
        runtime.commit(ticket.id);
        markHudStateDirty();
    }

    @Override
    public void cancel(long ticket, long job) {
        runtime.cancel(ticket);
        markHudStateDirty();
    }

    @Override
    public long pooled(long job, ResourceKey key) {
        return runtime.pooled(job, key);
    }

    @Override
    public long assign(long job, ResourceKey key, long qty, Ticket ticket, int need) {
        return runtime.assign(job, key, qty, ticket.id, need);
    }

    @Override
    public long ship(long job, ResourceKey key, long qty, UUID destination, CraftDelivery info) {
        return runtime.ship(job, key, qty, destination, info);
    }

    @Override
    public void flushJob(long job) {
        runtime.flushJob(job);
    }

    @Override
    public boolean legacyOrder(ResourceKey key, long qty, UUID destination, CraftDelivery info) {
        return false;
    }

    // ================================================================== leftovers as provider (plan section 8.3)

    @Override
    public void listCraftingStock(Map<ResourceKey, Long> into) {
        runtime.orphans().forEach((k, v) -> into.merge(k, v, Long::sum));
    }

    @Override
    public long craftingStockCount(ResourceKey key) {
        return runtime.orphans().getOrDefault(key, 0L);
    }

    @Override
    public long sendForCrafting(ResourceKey key, long qty, int destination, CraftDelivery delivery) {
        long take = runtime.takeOrphan(key, qty);
        if (take <= 0) return 0;
        long sent = sendKey(key, take, destination, delivery);
        if (sent < take) runtime.orphans().merge(key, take - sent, Long::sum);
        return sent;
    }

    // ================================================================== sending

    /** Routes {@code qty} of {@code key} out of the pipe to a router. Returns what left. */
    long sendKey(ResourceKey key, long qty, int destination, IAdditionalTargetInformation info) {
        if (key instanceof ItemKey) {
            ItemIdentifier item = ((ItemKey) key).id;
            long sent = 0;
            while (sent < qty) {
                int n = (int) Math.min(qty - sent, item.getMaxStackSize());
                pipe.sendStack(item.makeNormalStack(n), destination, ItemSendMode.Normal, info);
                sent += n;
            }
            return sent;
        }
        if (key instanceof FluidKey) {
            FluidIdentifier fluid = ((FluidKey) key).id;
            long sent = 0;
            while (sent < qty) {
                int n = (int) Math.min(qty - sent, FluidKey.MAX_PER_CONTAINER);
                IRoutedItem routed = FluidSendUtil
                        .createItem(fluid.makeFluidStack(n), destination, IRoutedItem.TransportMode.Active, info);
                pipe.queueRoutedItem(routed, sendDirection());
                sent += n;
            }
            return sent;
        }
        return 0;
    }

    /** Routed items leave from the target's side, like extracted outputs always did. */
    private ForgeDirection sendDirection() {
        AdjacentTile target = adjacentInventory.getConnected();
        if (target != null && target.orientation != null && target.orientation != ForgeDirection.UNKNOWN) {
            return target.orientation;
        }
        ForgeDirection pointed = pipe.getPointedOrientation();
        return pointed == null || pointed == ForgeDirection.UNKNOWN ? ForgeDirection.UP : pointed;
    }

    /** Sends to whatever storage takes it; returns 0 when nothing does (the items stay as leftovers). */
    long sendToStorage(ResourceKey key, long qty) {
        if (key instanceof ItemKey) {
            ItemIdentifier item = ((ItemKey) key).id;
            Triplet<Integer, SinkReply, List<IFilter>> reply = SimpleServiceLocator.logisticsManager
                    .hasDestination(item, true, pipe.getRouter().getSimpleID(), new ArrayList<>());
            if (reply == null) return 0;
            int n = (int) Math.min(qty, item.getMaxStackSize());
            if (reply.getValue2().maxNumberOfItems > 0) n = Math.min(n, reply.getValue2().maxNumberOfItems);
            pipe.sendStack(
                    item.makeNormalStack(n),
                    new Pair<>(reply.getValue1(), reply.getValue2()),
                    ItemSendMode.Normal);
            return n;
        }
        if (key instanceof FluidKey) {
            FluidStack stack = ((FluidKey) key).id.makeFluidStack((int) Math.min(qty, FluidKey.MAX_PER_CONTAINER));
            int before = stack.amount;
            IRoutedItem routed = FluidSendUtil.createItemForSink(stack, pipe.getRouter());
            if (routed == null) return 0;
            pipe.queueRoutedItem(routed, sendDirection());
            return before - stack.amount;
        }
        return 0;
    }

    /** The driver and the network, for the runtime. */
    private final class Link implements CrafterRuntime.Link {

        private CraftingDriver driver() {
            return CraftingService.INSTANCE.driver();
        }

        private UUID self() {
            return pipe.getRouter().getId();
        }

        @Override
        public void arrived(long ticket, int need, ResourceKey key, long qty, CraftDelivery hint) {
            CraftingDriver d = driver();
            if (d != null) d.onArrived(self(), ticket, need, key, qty, hint);
        }

        @Override
        public void stray(long job, ResourceKey key, long qty, CraftDelivery hint) {
            CraftingDriver d = driver();
            if (d != null) d.onStray(self(), job, key, qty, hint);
        }

        @Override
        public void inserted(long ticket) {
            CraftingDriver d = driver();
            if (d != null) d.onInserted(ticket);
        }

        @Override
        public void output(long ticket, ResourceKey key, long qty) {
            CraftingDriver d = driver();
            if (d != null) d.onOutput(ticket, key, qty);
        }

        @Override
        public void wrongCraft(long ticket, String reason) {
            CraftingDriver d = driver();
            if (d != null) d.onWrongCraft(ticket, reason);
        }

        @Override
        public long send(ResourceKey key, long qty, UUID destination, CraftDelivery info) {
            int dest = SimpleServiceLocator.routerManager.getIDforUUID(destination);
            if (dest < 0) return 0;
            return sendKey(key, qty, dest, CraftingService.INSTANCE.deliveryFor(info, destination));
        }

        @Override
        public long sendToStorage(ResourceKey key, long qty) {
            return ModulePatternCrafting.this.sendToStorage(key, qty);
        }

        @Override
        public boolean isJobAlive(long job) {
            CraftingDriver d = driver();
            return d != null && d.isJobAlive(job);
        }
    }

    // ================================================================== the machine (plan sections 6.4, 6.5)

    private final class Machine implements CrafterRuntime.Machine {

        @Override
        public BlockingMode mode() {
            return v3Mode();
        }

        @Override
        public InsertResult insert(int ticketSlot, String patternId, long crafts, List<ResourceAmount> stacks,
                List<ResourceAmount> notInserted) {
            int patternSlot = slotOf(ticketSlot, patternId);
            if (patternSlot < 0) return InsertResult.CONFIG_ERROR; // the pattern was taken out
            ItemStack patternStack = getPatternStack(patternSlot);
            PatternRecipeSnapshot recipe = patternStack == null ? null : patternHandler.getRecipe(patternStack);
            AdjacentTile connected = adjacentInventory.getConnected();
            if (recipe == null || connected == null || connected.tile == null) return InsertResult.CONFIG_ERROR;
            Map<ResourceKey, Long> available = new LinkedHashMap<>();
            for (ResourceAmount s : stacks) available.merge(s.key(), s.amount(), Long::sum);
            Pattern pattern = v3Patterns().get(patternSlot);
            MatchRule rule = recipe.isOreDictSubstitutionEnabled() ? MatchRule.ORE_DICT
                    : recipe.isIgnoreNbtEnabled() ? MatchRule.IGNORE_NBT : MatchRule.EXACT;
            List<PatternIngredientAssignment> local = new ArrayList<>();
            List<SatelliteInsert> satellites = new ArrayList<>();
            for (int slot = 0; slot < recipe.getIngredientSlotCount(); slot++) {
                IPatternStack in = recipe.getInput(slot);
                ResourceKey slotKey = keyOf(in);
                if (slotKey == null) continue;
                boolean consumed = recipe.getPattern().isInputConsumed(slot);
                long want = consumed ? in.getAmount() * crafts : in.getAmount();
                ResourceMatcher matcher = slotKey.isFluid() ? ResourceMatcher.exact(slotKey)
                        : ResourceMatcher.of(slotKey, rule, null);
                for (Map.Entry<ResourceKey, Long> e : available.entrySet()) {
                    if (want <= 0) break;
                    if (e.getValue() <= 0 || !matcher.matches(e.getKey())) continue;
                    long take = Math.min(want, e.getValue());
                    e.setValue(e.getValue() - take);
                    want -= take;
                    IPatternStack stack = patternStackOf(e.getKey(), take);
                    SatelliteInsert sat = satelliteFor(recipe, slot, e.getKey(), take);
                    if (sat != null) satellites.add(sat);
                    else local.add(new PatternIngredientAssignment(slot, stack));
                }
            }
            // anything not placed in a slot (shouldn't happen) still goes in with the set
            for (Map.Entry<ResourceKey, Long> e : available.entrySet()) {
                if (e.getValue() > 0)
                    local.add(new PatternIngredientAssignment(-1, patternStackOf(e.getKey(), e.getValue())));
            }
            if (!local.isEmpty() && !adjacentInventory.canInsertPatternIngredients(patternStack, local)) {
                return InsertResult.NO_ROOM;
            }
            for (SatelliteInsert sat : satellites) {
                if (!sat.canAccept()) return InsertResult.NO_ROOM;
            }
            if (!local.isEmpty()) {
                int[] inserted = adjacentInventory.insertPatternIngredients(patternStack, local);
                for (int i = 0; i < local.size(); i++) {
                    IPatternStack s = local.get(i).stack();
                    int missing = s.getAmount() - (i < inserted.length ? inserted[i] : 0);
                    if (missing > 0) notInserted.add(new ResourceAmount(keyOf(s), missing));
                }
            }
            for (SatelliteInsert sat : satellites) {
                long missing = sat.qty - sat.insert();
                if (missing > 0) notInserted.add(new ResourceAmount(sat.key, missing));
            }
            if (pattern != null) markHudStateDirty();
            return InsertResult.INSERTED;
        }

        @Override
        public boolean isIdle(int ticketSlot, String patternId) {
            AdjacentTile connected = adjacentInventory.getConnected();
            if (connected == null || connected.tile == null) return true;
            if (connected.tile instanceof PatternLogisticsCraftingTableTileEntity) {
                return ((PatternLogisticsCraftingTableTileEntity) connected.tile).isIdle();
            }
            Pattern pattern = v3Patterns().get(slotOf(ticketSlot, patternId));
            if (connected.tile instanceof IInventory) {
                IInventory inv = (IInventory) connected.tile;
                for (int i = 0; i < inv.getSizeInventory(); i++) {
                    ItemStack stack = inv.getStackInSlot(i);
                    if (stack == null || stack.stackSize <= 0) continue;
                    if (!isNonConsumed(pattern, ItemKey.of(ItemIdentifier.get(stack)))) return false;
                }
            }
            if (pattern != null && hasFluidInput(pattern)
                    && connected.tile instanceof net.minecraftforge.fluids.IFluidHandler) {
                net.minecraftforge.fluids.FluidTankInfo[] tanks = ((net.minecraftforge.fluids.IFluidHandler) connected.tile)
                        .getTankInfo(connected.orientation.getOpposite());
                if (tanks != null) {
                    for (net.minecraftforge.fluids.FluidTankInfo tank : tanks) {
                        if (tank == null || tank.fluid == null || tank.fluid.amount <= 0) continue;
                        if (!isNonConsumed(pattern, FluidKey.of(FluidIdentifier.get(tank.fluid)))) return false;
                    }
                }
            }
            return true;
        }

        @Override
        public long extract(ResourceKey key, long qty) {
            AdjacentTile connected = adjacentInventory.getConnected();
            if (connected == null || qty <= 0) return 0;
            if (key instanceof ItemKey) {
                ItemIdentifier item = ((ItemKey) key).id;
                int n = (int) Math.min(qty, item.getMaxStackSize());
                ItemStack got = adjacentInventory.extract(new ItemResource(new ItemIdentifierStack(item, n), null), n);
                return got == null ? 0 : got.stackSize;
            }
            if (key instanceof FluidKey) {
                int n = (int) Math.min(qty, FluidKey.MAX_PER_CONTAINER);
                FluidStack got = adjacentInventory
                        .extractFluid(connected, new PatternFluidStack(((FluidKey) key).id, n), n);
                return got == null ? 0 : got.amount;
            }
            return 0;
        }

        @Override
        public List<ResourceKey> extractable() {
            List<ResourceKey> result = new ArrayList<>();
            List<ItemStack> items = adjacentInventory.getExtractableItems();
            if (items != null) {
                for (ItemStack stack : items) {
                    if (stack != null && stack.stackSize > 0) result.add(ItemKey.of(ItemIdentifier.get(stack)));
                }
            }
            AdjacentTile connected = adjacentInventory.getConnected();
            if (connected != null && connected.tile instanceof net.minecraftforge.fluids.IFluidHandler) {
                net.minecraftforge.fluids.FluidTankInfo[] tanks = ((net.minecraftforge.fluids.IFluidHandler) connected.tile)
                        .getTankInfo(connected.orientation.getOpposite());
                if (tanks != null) {
                    for (net.minecraftforge.fluids.FluidTankInfo tank : tanks) {
                        if (tank != null && tank.fluid != null && tank.fluid.amount > 0) {
                            result.add(FluidKey.of(FluidIdentifier.get(tank.fluid)));
                        }
                    }
                }
            }
            return result;
        }

        @Override
        public boolean isInput(ResourceKey key) {
            for (Pattern p : v3Patterns().values()) {
                for (InputSpec in : p.inputs()) {
                    if (in.matcher().matches(key)) return true;
                }
            }
            return false;
        }

        private boolean isNonConsumed(Pattern pattern, ResourceKey key) {
            if (pattern == null) return false;
            for (InputSpec in : pattern.inputs()) {
                if (!in.consumed() && in.matcher().matches(key)) return true;
            }
            return false;
        }

        private boolean hasFluidInput(Pattern pattern) {
            for (InputSpec in : pattern.inputs()) {
                if (in.key().isFluid()) return true;
            }
            return false;
        }
    }

    private static IPatternStack patternStackOf(ResourceKey key, long qty) {
        int n = (int) Math.min(Integer.MAX_VALUE, qty);
        if (key instanceof FluidKey) return new PatternFluidStack(((FluidKey) key).id, n);
        return new PatternItemStack(new ItemIdentifierStack(((ItemKey) key).id, n));
    }

    /** A pattern input that goes into a pattern satellite's machine instead of the pipe's own target. */
    private SatelliteInsert satelliteFor(PatternRecipeSnapshot recipe, int slot, ResourceKey key, long qty) {
        if (key instanceof FluidKey) {
            String uuid = recipe.getFluidSatelliteUuid(slot);
            int id = recipe.getFluidSatelliteId(slot);
            if ((uuid == null || uuid.isEmpty()) && id <= 0) return null;
            PipeFluidPatternSatelliteLogistics sat = pipe.resolvePatternFluidSatelliteTarget(uuid, id);
            return sat == null ? null : new SatelliteInsert(key, qty, null, sat);
        }
        String uuid = recipe.getItemSatelliteUuid(slot);
        int id = recipe.getItemSatelliteId(slot);
        if ((uuid == null || uuid.isEmpty()) && id <= 0) return null;
        PipeItemsPatternSatelliteLogistics sat = pipe.resolvePatternSatelliteTarget(uuid, id);
        return sat == null ? null : new SatelliteInsert(key, qty, sat, null);
    }

    private static final class SatelliteInsert {

        final ResourceKey key;
        final long qty;
        final PipeItemsPatternSatelliteLogistics items;
        final PipeFluidPatternSatelliteLogistics fluids;

        SatelliteInsert(ResourceKey key, long qty, PipeItemsPatternSatelliteLogistics items,
                PipeFluidPatternSatelliteLogistics fluids) {
            this.key = key;
            this.qty = qty;
            this.items = items;
            this.fluids = fluids;
        }

        boolean canAccept() {
            if (items != null) {
                return items.canAcceptPatternInput(new ItemIdentifierStack(((ItemKey) key).id, (int) qty));
            }
            return fluids.canAcceptPatternInput(((FluidKey) key).id, (int) qty);
        }

        long insert() {
            if (items != null) {
                return items.insertPatternInput(new ItemIdentifierStack(((ItemKey) key).id, (int) qty), false);
            }
            return fluids.insertPatternInput(((FluidKey) key).id, (int) qty, false);
        }
    }

    // ================================================================== GUI / HUD

    /** Cancels the crafting jobs that use the selected pattern slot. */
    public boolean cancelPatternCraft(int patternSlot) {
        CraftingDriver driver = CraftingService.INSTANCE.driver();
        if (driver == null) return false;
        boolean changed = false;
        for (CrafterRuntime.TicketView view : runtime.view()) {
            if (view.patternSlot == patternSlot) changed |= driver.cancelJob(view.job);
        }
        markHudStateDirty();
        return changed;
    }

    /** Cancels every job using this pipe; their buffered items go back to storage when they wind down. */
    public boolean returnStoredInputsToStorage() {
        CraftingDriver driver = CraftingService.INSTANCE.driver();
        boolean changed = false;
        if (driver != null) {
            for (CrafterRuntime.TicketView view : runtime.view()) changed |= driver.cancelJob(view.job);
        }
        for (Long job : runtime.poolJobs()) {
            if (driver == null || !driver.isJobAlive(job)) {
                runtime.flushJob(job);
                changed = true;
            }
        }
        markHudStateDirty();
        return changed;
    }

    public PatternCraftingHudState getHudState() {
        long tick = currentWorldTick();
        if (!hudDirty && tick - hudStateTick < 20) {
            return hudState;
        }
        hudDirty = false;
        hudStateTick = tick;
        List<PatternCraftingHudState.PatternInfo> infos = new ArrayList<>();
        List<CrafterRuntime.TicketView> views = runtime.view();
        for (int slot = 0; slot < patternHandler.size(); slot++) {
            ItemStack pattern = getPatternStack(slot);
            if (pattern == null) continue;
            PatternCraftingHudState.PatternInfo info = new PatternCraftingHudState.PatternInfo(slot);
            PatternRecipeSnapshot recipe = patternHandler.getRecipe(slot);
            int open = 0;
            int queued = 0;
            int running = 0;
            long buffered = 0;
            long needed = 0;
            for (CrafterRuntime.TicketView view : views) {
                if (view.patternSlot != slot) continue;
                switch (view.state) {
                    case "OPEN":
                        open++;
                        break;
                    case "COMMITTED":
                        queued++;
                        break;
                    default:
                        running++;
                }
                buffered += view.buffered;
                needed += view.needed;
            }
            if (recipe != null) {
                for (int i = 0; i < recipe.getResultSlotCount(); i++) {
                    IPatternStack out = recipe.getOutput(i);
                    ItemIdentifierStack display = out == null ? null : PatternStackHelper.asSolidStack(out);
                    if (display != null) {
                        info.getOutputs().add(new PatternCraftingHudState.OutputInfo(display, running, i));
                    }
                }
            }
            if (!isPatternCraftingSupported(pattern)) {
                info.setStatus("Needs a fluid crafting upgrade");
            } else if (running > 0) {
                info.setStatus(running + " set(s) in the machine");
                info.setActive(true);
            } else if (queued > 0) {
                info.setStatus(queued + " set(s) waiting for the machine");
                info.setActive(true);
            } else if (open > 0) {
                info.setStatus("Collecting inputs " + buffered + "/" + needed);
                info.setActive(true);
            } else {
                info.setStatus("Idle");
            }
            infos.add(info);
        }
        hudState = new PatternCraftingHudState(getEffectiveBlockingMode(), infos);
        return hudState;
    }

    public boolean shouldRefreshHudState() {
        return hudDirty || currentWorldTick() - hudStateTick >= 20;
    }

    public void markHudStateDirty() {
        hudDirty = true;
    }

    public void appendDebugState(StringBuilder out) {
        out.append("Pattern crafting pipe at ").append(pipe.getX()).append(", ").append(pipe.getY()).append(", ")
                .append(pipe.getZ()).append(" router=").append(pipe.getRouter().getSimpleID()).append("\n");
        out.append("  mode=").append(getEffectiveBlockingMode()).append("\n");
        for (CrafterRuntime.TicketView view : runtime.view()) {
            out.append("  ticket ").append(view.ticket).append(" job ").append(view.job).append(" slot ")
                    .append(view.patternSlot).append(' ').append(view.state).append(' ').append(view.buffered)
                    .append('/').append(view.needed).append(view.configError ? " CONFIG ERROR" : "").append("\n");
        }
        out.append("  leftovers: ").append(runtime.orphans()).append("\n");
    }

    // ================================================================== persistence

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        patternInventory.readFromNBT(tag, "PatternCrafting");
        patternHandler.invalidate();
        adjacentInventory.invalidate();
        v3Patterns = null;
        int mode = tag.getInteger("patternBlockingMode");
        PipeItemsPatternCraftingLogistics.BlockingMode[] modes = PipeItemsPatternCraftingLogistics.BlockingMode
                .values();
        blockingMode = modes[Math.max(0, Math.min(modes.length - 1, mode))];
        if (tag.hasKey(RUNTIME_TAG)) {
            runtime.readFromNBT(tag.getCompoundTag(RUNTIME_TAG), McKeyCodec.INSTANCE);
        }
        migrateLegacyBuffer(tag);
        syncedWithDriver = false;
        markHudStateDirty();
    }

    /**
     * Saves from before the crafting rework buffered ingredients per pattern slot. Their crafts are gone, so the items
     * become leftovers and go back to storage; nothing is voided.
     */
    private void migrateLegacyBuffer(NBTTagCompound tag) {
        if (!tag.hasKey(LEGACY_BUFFER_TAG)) return;
        NBTTagList buffer = tag.getTagList(LEGACY_BUFFER_TAG, 10);
        for (int i = 0; i < buffer.tagCount(); i++) {
            IPatternStack stack = IPatternStack.readFromNBT(buffer.getCompoundTagAt(i));
            ResourceKey key = keyOf(stack);
            if (key != null) runtime.orphans().merge(key, (long) stack.getAmount(), Long::sum);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        patternInventory.writeToNBT(tag, "PatternCrafting");
        tag.setInteger("patternBlockingMode", blockingMode.ordinal());
        NBTTagCompound runtimeTag = new NBTTagCompound();
        runtime.writeToNBT(runtimeTag, McKeyCodec.INSTANCE);
        tag.setTag(RUNTIME_TAG, runtimeTag);
    }

    /** The pipe is broken: patterns and everything buffered drop, the driver re-plans around the lost items. */
    public void onAllowedRemoval() {
        World world = pipe.getWorld();
        CraftingService.INSTANCE.unregisterCrafter(this);
        if (world != null && !world.isRemote) {
            CraftingDriver driver = CraftingService.INSTANCE.driver();
            for (Map.Entry<ResourceKey, Long> e : runtime.contents().entrySet()) {
                if (e.getKey() instanceof ItemKey) {
                    ItemIdentifier item = ((ItemKey) e.getKey()).id;
                    long left = e.getValue();
                    while (left > 0) {
                        int n = (int) Math.min(left, item.getMaxStackSize());
                        net.minecraft.entity.item.EntityItem drop = item
                                .makeEntityItem(n, world, pipe.getX() + 0.5, pipe.getY() + 0.5, pipe.getZ() + 0.5);
                        world.spawnEntityInWorld(drop);
                        left -= n;
                    }
                }
            }
            runtime.clear();
            if (driver != null) driver.onCrafterRemoved(pipe.getRouter().getId());
        }
        patternInventory.dropContents(world, pipe.getX(), pipe.getY(), pipe.getZ());
    }

    // ================================================================== module plumbing

    @Override
    public void registerPosition(ModulePositionType slot, int positionInt) {
        super.registerPosition(slot, positionInt);
        sinkReply = new SinkReply(FixedPriority.ItemSink, 0, true, false, 1, 0, null);
    }

    void debug(String message, Object... args) {
        if (_service != null) {
            _service.getDebug().log("PatternCrafting: " + message, args);
        }
    }

    long currentWorldTick() {
        World world = pipe.getWorld();
        return world == null ? 0 : world.getTotalWorldTime();
    }

    protected ISlotUpgradeManager getUpgradeManager() {
        if (_service == null) {
            return null;
        }
        return _service.getUpgradeManager(slot, positionInt);
    }

    /**
     * The side of the target that items and fluids are inserted through: the sneaky side, otherwise the face touching
     * the pipe. {@code tile.orientation} points from the pipe to the target, so its opposite is the touching face.
     */
    ForgeDirection getInsertionOrientation(AdjacentTile tile) {
        ISlotUpgradeManager upgradeManager = getUpgradeManager();
        if (upgradeManager != null && upgradeManager.hasSneakyUpgrade()) {
            return upgradeManager.getSneakyOrientation();
        }
        return tile.orientation.getOpposite();
    }

    /** Ingredients that go into the pipe's own target (not to a satellite), for the capacity estimate. */
    List<IPatternStack> getLocalAggregatedIngredients(ItemStack pattern) {
        PatternRecipeSnapshot recipe = patternHandler.getRecipe(pattern);
        return recipe == null ? Collections.emptyList() : recipe.getAggregatedInputs();
    }

    boolean hasLinkedSatelliteAssignment(ItemStack pattern, int inputSlot) {
        PatternRecipeSnapshot recipe = patternHandler.getRecipe(pattern);
        if (recipe == null) return false;
        String uuid = recipe.getItemSatelliteUuid(inputSlot);
        return recipe.getItemSatelliteId(inputSlot) > 0 || uuid != null && !uuid.isEmpty();
    }

    AdjacentTile getConnectedInventoryTile() {
        return adjacentInventory.getConnected();
    }

    @Override
    public boolean hasGenericInterests() {
        return false;
    }

    @Override
    public Collection<ItemIdentifier> getSpecificInterests() {
        return getOutputItems();
    }

    @Override
    public boolean interestedInAttachedInventory() {
        return false;
    }

    @Override
    public boolean interestedInUndamagedID() {
        return false;
    }

    @Override
    public boolean recievePassive() {
        return false;
    }

    @Override
    public LogisticsModule getSubModule(int slot) {
        return null;
    }

    @Override
    public int getX() {
        return pipe.getX();
    }

    @Override
    public int getY() {
        return pipe.getY();
    }

    @Override
    public int getZ() {
        return pipe.getZ();
    }

    @Override
    public net.minecraft.util.IIcon getIconTexture(IIconRegister register) {
        return register.registerIcon("logisticspipes:itemModule/ModuleCrafter");
    }

    // ---------------------------------------------------------------- request tree: not a crafter any more

    @Override
    public void canProvide(RequestTreeNode tree, RequestTree root, List<IFilter> filters) {}

    @Override
    public LogisticsOrder fullFill(LogisticsPromise promise, IRequestItems destination,
            IAdditionalTargetInformation info) {
        return null;
    }

    @Override
    public void getAllItems(Map<ItemIdentifier, Integer> list, List<IFilter> filter) {}

    @Override
    public void registerExtras(IPromise promise) {}

    /** Crafting is planned by the v3 planner, not the request tree. */
    @Override
    public ICraftingTemplate addCrafting(IResource toCraft) {
        return null;
    }

    @Override
    public boolean canCraft(IResource toCraft) {
        for (ItemIdentifier item : getCraftedItems()) {
            if (toCraft.matches(item, IResource.MatchSettings.NORMAL)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getTodo() {
        return runtime.view().size();
    }

    @Override
    public List<ItemIdentifierStack> getConfiguredCraftResults() {
        return patternHandler.getCraftResults(supportsFluidCrafting());
    }

    @Override
    public IRouter getRouter() {
        return pipe.getRouter();
    }

    @Override
    public void itemCouldNotBeSend(ItemIdentifierStack item, IAdditionalTargetInformation info) {
        pipe.itemCouldNotBeSend(item, info);
    }

    @Override
    public int getID() {
        return pipe.getID();
    }

    @Override
    public int compareTo(IRequest request) {
        return getID() - request.getID();
    }
}
