package logisticspipes.crafting.v3.mc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fluids.FluidStack;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import logisticspipes.LogisticsPipes;
import logisticspipes.crafting.legacy.LegacyCrafters;
import logisticspipes.crafting.v3.exec.CraftDelivery;
import logisticspipes.crafting.v3.exec.CrafterPort;
import logisticspipes.crafting.v3.exec.CraftingDriver;
import logisticspipes.crafting.v3.exec.CraftingJob;
import logisticspipes.crafting.v3.exec.DriverPersistence;
import logisticspipes.crafting.v3.exec.DriverWorld;
import logisticspipes.crafting.v3.model.ResourceKey;
import logisticspipes.crafting.v3.model.ResourceMatcher;
import logisticspipes.crafting.v3.plan.CraftingPlanner;
import logisticspipes.crafting.v3.plan.Plan;
import logisticspipes.crafting.v3.plan.PlanRequest;
import logisticspipes.crafting.v3.plan.RouteOracle;
import logisticspipes.crafting.v3.plan.StockView;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.PipeLogisticsChassi;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.ItemRoutingInformation;
import logisticspipes.routing.astar.LPJunctionNetwork;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import logisticspipes.utils.tuples.LPPosition;

/**
 * Server-side home of the crafting driver: creates it with the world, ticks it, saves it with the overworld, and is the
 * bridge between Minecraft (pipes, routed items, players) and the Minecraft-free driver.
 * <p>
 * There is one driver per server rather than one per network: the router has no stable network id, and with
 * reachability checked per item and location, a job simply stalls on the side of a split it can't reach and resumes
 * when the networks join again (plan section 9.5).
 */
public final class CraftingService {

    public static final CraftingService INSTANCE = new CraftingService();
    private static final String SAVE_NAME = "LogisticsPipes_Crafting";
    /** Planning runs on the server thread, so it gets a tight budget. */
    public static final long PLAN_BUDGET_MS = 250;

    private CraftingDriver driver;
    private McWorld world;
    private SaveData saveData;
    private long lastGeneration = Long.MIN_VALUE;
    private long lastGenerationRetry;
    private final Map<Long, IAdditionalTargetInformation> requesterInfo = new HashMap<>();
    private final Map<IV3Crafter, Boolean> knownCrafters = new WeakHashMap<>();

    private CraftingService() {}

    // ================================================================== lifecycle

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        CraftingDriver d = driver();
        if (d == null) return;
        long generation = graphGeneration();
        long now = world.now();
        if (generation != lastGeneration && now - lastGenerationRetry >= 40) {
            // routing changed: things that were unreachable may not be any more (plan section 7.2)
            lastGeneration = generation;
            lastGenerationRetry = now;
            d.retryStalled();
        }
        d.tick();
        if (d.isDirty()) {
            d.clearDirty();
            if (saveData != null) saveData.markDirty();
        }
    }

    /** The driver, created and loaded with the overworld on first use. Null on the client or before a world loads. */
    public CraftingDriver driver() {
        if (driver != null) return driver;
        World overworld = DimensionManager.getWorld(0);
        if (overworld == null || overworld.isRemote) return null;
        world = new McWorld();
        driver = new CraftingDriver(world);
        SaveData data = (SaveData) overworld.mapStorage.loadData(SaveData.class, SAVE_NAME);
        if (data == null) {
            data = new SaveData(SAVE_NAME);
            overworld.mapStorage.setData(SAVE_NAME, data);
        } else if (data.loaded != null) {
            try {
                DriverPersistence.read(driver, data.loaded, McKeyCodec.INSTANCE, world.now());
            } catch (RuntimeException e) {
                LogisticsPipes.log.error("Could not load crafting jobs, they are dropped", e);
            }
            data.loaded = null;
        }
        saveData = data;
        return driver;
    }

    /**
     * Server stopping: forget everything. FML fires this before the final world save, so the save data keeps a snapshot
     * to write then.
     */
    public void clear() {
        if (driver != null && saveData != null) {
            saveData.loaded = DriverPersistence.write(driver, McKeyCodec.INSTANCE);
            saveData.markDirty();
        }
        driver = null;
        world = null;
        saveData = null;
        requesterInfo.clear();
        knownCrafters.clear();
        lastGeneration = Long.MIN_VALUE;
    }

    public static final class SaveData extends WorldSavedData {

        NBTTagCompound loaded;

        public SaveData(String name) {
            super(name);
        }

        @Override
        public void readFromNBT(NBTTagCompound tag) {
            loaded = tag.getCompoundTag("driver");
        }

        @Override
        public void writeToNBT(NBTTagCompound tag) {
            CraftingDriver d = INSTANCE.driver;
            if (d != null) {
                tag.setTag("driver", DriverPersistence.write(d, McKeyCodec.INSTANCE));
            } else if (loaded != null) {
                tag.setTag("driver", loaded);
            }
        }
    }

    // ================================================================== requests

    /** Plans {@code qty} of {@code target} for {@code requester}, against the requester's network. */
    public Plan plan(UUID root, ResourceMatcher target, long qty, boolean craftOnly) {
        NetworkScan scan = NetworkScan.from(root);
        return new CraftingPlanner(
                scan.registry(),
                scan.stockSnapshot(),
                new McRouteOracle(),
                CraftingPlanner.DEFAULT_NODE_CAP,
                PLAN_BUDGET_MS).plan(new PlanRequest(target, qty, root, craftOnly));
    }

    /**
     * Starts a planned job.
     *
     * @param info target information the requester expects on deliveries (chassis slot, player target, ...)
     */
    public CraftingJob start(Plan plan, UUID player, IAdditionalTargetInformation info) {
        CraftingDriver d = driver();
        if (d == null) throw new IllegalStateException("no server world");
        CraftingJob job = d.startJob(plan, player);
        if (info != null) requesterInfo.put(job.id, info);
        return job;
    }

    public boolean cancel(long job) {
        CraftingDriver d = driver();
        return d != null && d.cancelJob(job);
    }

    // ================================================================== reservations (plan section 4)

    /** Amount of {@code key} at the provider with router {@code location} that crafting jobs hold. */
    public long reserved(UUID location, ResourceKey key) {
        CraftingDriver d = driver;
        return d == null || location == null ? 0 : d.reservations().reserved(location, key);
    }

    /** Convenience for item providers: reservation on the pipe's router, clamped to int. */
    public int reserved(CoreRoutedPipe pipe, ItemIdentifier item) {
        if (pipe == null || item == null || pipe.getRouter() == null) return 0;
        return (int) Math.min(Integer.MAX_VALUE, reserved(pipe.getRouter().getId(), ItemKey.of(item)));
    }

    public int reserved(CoreRoutedPipe pipe, FluidIdentifier fluid) {
        if (pipe == null || fluid == null || pipe.getRouter() == null) return 0;
        return (int) Math.min(Integer.MAX_VALUE, reserved(pipe.getRouter().getId(), FluidKey.of(fluid)));
    }

    // ================================================================== transport hooks

    /**
     * A routed item that carries a crafting delivery reached its destination. Pattern crafting pipes take their
     * deliveries themselves; everything else (requesters, legacy crafters) is reported here.
     */
    public void onDelivered(CoreRoutedPipe at, ItemRoutingInformation info) {
        CraftDelivery delivery = info.craftDelivery;
        CraftingDriver d = driver();
        if (delivery == null || d == null || at.getRouter() == null) return;
        info.craftDelivery = null;
        ResourceAmountView amount = amountOf(info.getItem());
        if (amount != null) d.onDelivered(at.getRouter().getId(), amount.key, amount.qty, delivery);
    }

    /** A routed item that carried a crafting delivery was lost or re-routed elsewhere. */
    public void onLost(ItemRoutingInformation info, ItemIdentifierStack lostPart) {
        CraftDelivery delivery = info.craftDelivery;
        CraftingDriver d = driver();
        if (delivery == null || d == null) return;
        ResourceAmountView amount = amountOf(lostPart != null ? lostPart : info.getItem());
        if (lostPart == null) info.craftDelivery = null;
        if (amount != null) d.onLost(delivery, amount.key, amount.qty);
    }

    /** An order placed for a crafting job (legacy crafter output) could not be sent. */
    public void onSendFailed(CraftDelivery delivery, ItemIdentifierStack stack) {
        CraftingDriver d = driver();
        ResourceAmountView amount = amountOf(stack);
        if (d != null && amount != null) d.onLost(delivery, amount.key, amount.qty);
    }

    /** The key and amount an item stack stands for: a fluid container counts as its fluid. */
    public static ResourceAmountView amountOf(ItemIdentifierStack stack) {
        if (stack == null || stack.getStackSize() <= 0) return null;
        if (stack.getItem().isFluidContainer()) {
            FluidStack fluid = SimpleServiceLocator.logisticsFluidManager.getFluidFromContainer(stack);
            if (fluid != null && fluid.amount > 0) {
                return new ResourceAmountView(FluidKey.of(FluidIdentifier.get(fluid)), fluid.amount);
            }
        }
        return new ResourceAmountView(ItemKey.of(stack.getItem()), stack.getStackSize());
    }

    public static final class ResourceAmountView {

        public final ResourceKey key;
        public final long qty;

        ResourceAmountView(ResourceKey key, long qty) {
            this.key = key;
            this.qty = qty;
        }
    }

    // ================================================================== crafters

    /** Pattern crafting pipes announce themselves so a replacement crafter can be found (plan section 7.1). */
    public void registerCrafter(IV3Crafter crafter) {
        knownCrafters.put(crafter, Boolean.TRUE);
    }

    public void unregisterCrafter(IV3Crafter crafter) {
        knownCrafters.remove(crafter);
    }

    static List<IV3Crafter> craftersOf(CoreRoutedPipe pipe) {
        if (pipe instanceof IV3CrafterHolder) {
            IV3Crafter crafter = ((IV3CrafterHolder) pipe).v3Crafter();
            return crafter == null ? Collections.emptyList() : Collections.singletonList(crafter);
        }
        // rebuilt every time: the modules of a chassis can change at any moment
        return LegacyCrafters.of(pipe);
    }

    static List<ICraftingStockSource> stockSourcesOf(CoreRoutedPipe pipe) {
        List<ICraftingStockSource> result = new ArrayList<>();
        if (pipe instanceof ICraftingStockSource) result.add((ICraftingStockSource) pipe);
        if (pipe instanceof IV3CrafterHolder && ((IV3CrafterHolder) pipe).v3Crafter() instanceof ICraftingStockSource) {
            // a crafting pipe's leftovers nobody could store (plan section 8.3)
            result.add((ICraftingStockSource) ((IV3CrafterHolder) pipe).v3Crafter());
        }
        if (pipe instanceof PipeLogisticsChassi) {
            PipeLogisticsChassi chassis = (PipeLogisticsChassi) pipe;
            for (int i = 0; i < chassis.getChassiSize(); i++) {
                LogisticsModule module = chassis.getLogisticsModule().getSubModule(i);
                if (module instanceof ICraftingStockSource) result.add((ICraftingStockSource) module);
            }
        }
        return result;
    }

    // ================================================================== router helpers

    public static IRouter router(UUID id) {
        int simple = SimpleServiceLocator.routerManager.getIDforUUID(id);
        return simple < 0 ? null : SimpleServiceLocator.routerManager.getRouter(simple);
    }

    /** The router's pipe if its chunk is loaded; never loads chunks. */
    public static CoreRoutedPipe pipeOf(IRouter router) {
        if (router == null) return null;
        CoreRoutedPipe pipe = router.getCachedPipe();
        if (pipe == null) {
            World w = DimensionManager.getWorld(router.getDimension());
            LPPosition pos = router.getLPPosition();
            if (w == null || pos == null || !w.blockExists(pos.getX(), pos.getY(), pos.getZ())) return null;
            pipe = router.getPipe();
        }
        return pipe == null || pipe.container == null || pipe.container.isInvalid() ? null : pipe;
    }

    public static CoreRoutedPipe pipeAt(UUID id) {
        return pipeOf(router(id));
    }

    public static long graphGeneration() {
        try {
            return LPJunctionNetwork.writer().graph().generation();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static EntityPlayerMP player(UUID id) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || id == null) return null;
        for (Object o : server.getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP && id.equals(((EntityPlayerMP) o).getUniqueID())) {
                return (EntityPlayerMP) o;
            }
        }
        return null;
    }

    public static void tell(UUID player, String text) {
        EntityPlayerMP p = player(player);
        if (p != null) p.addChatComponentMessage(
                new ChatComponentText(EnumChatFormatting.AQUA + "[LP] " + EnumChatFormatting.RESET + text));
    }

    // ================================================================== world adapter

    private final class McWorld implements DriverWorld {

        private final McRouteOracle routes = new McRouteOracle();
        private final Map<UUID, NetworkScan> scans = new HashMap<>();
        private long scanTick = Long.MIN_VALUE;
        private long scanGeneration;

        @Override
        public long now() {
            World overworld = DimensionManager.getWorld(0);
            return overworld == null ? 0 : overworld.getTotalWorldTime();
        }

        @Override
        public RouteOracle routes() {
            return routes;
        }

        private NetworkScan scan(UUID near) {
            long tick = now();
            long generation = graphGeneration();
            if (tick != scanTick || generation != scanGeneration) {
                scans.clear();
                scanTick = tick;
                scanGeneration = generation;
            }
            return scans.computeIfAbsent(near, NetworkScan::from);
        }

        @Override
        public List<StockView.Entry> availableStock(ResourceMatcher matcher, UUID near) {
            return scan(near).liveStock(matcher);
        }

        @Override
        public long availableAt(UUID provider, ResourceKey key) {
            long physical = physicalAt(provider, key);
            return physical < 0 ? 0 : physical - reserved(provider, key);
        }

        @Override
        public long physicalAt(UUID provider, ResourceKey key) {
            CoreRoutedPipe pipe = pipeAt(provider);
            if (pipe == null) return -1;
            long count = 0;
            for (ICraftingStockSource source : stockSourcesOf(pipe)) {
                count = Math.max(count, source.craftingStockCount(key));
            }
            return count;
        }

        @Override
        public long sendFromProvider(UUID provider, ResourceKey key, long qty, UUID destination, CraftDelivery info) {
            CoreRoutedPipe pipe = pipeAt(provider);
            int dest = SimpleServiceLocator.routerManager.getIDforUUID(destination);
            if (pipe == null || dest < 0) return 0;
            CraftDelivery delivery = withRequesterInfo(info, destination);
            long sent = 0;
            for (ICraftingStockSource source : stockSourcesOf(pipe)) {
                if (sent >= qty) break;
                sent += source.sendForCrafting(key, qty - sent, dest, delivery);
            }
            return sent;
        }

        @Override
        public CrafterPort crafter(UUID location) {
            CoreRoutedPipe pipe = pipeAt(location);
            if (pipe == null) return null;
            List<IV3Crafter> crafters = craftersOf(pipe);
            return crafters.isEmpty() ? null : crafters.get(0).port();
        }

        @Override
        public UUID findCrafter(String patternId, UUID excluding) {
            for (IV3Crafter crafter : new ArrayList<>(knownCrafters.keySet())) {
                UUID id = crafter.crafterInfo().id();
                if (id.equals(excluding) || pipeAt(id) == null) continue;
                for (var p : crafter.v3Patterns().values()) {
                    if (p.id().equals(patternId)) return id;
                }
            }
            return null;
        }

        @Override
        public void message(UUID player, String text) {
            tell(player, text);
        }
    }

    /** Deliveries to a job's requester carry the target information its request asked for. */
    CraftDelivery withRequesterInfo(CraftDelivery info, UUID destination) {
        if (info == null || driver == null) return info;
        CraftingJob job = driver.job(info.job());
        if (job != null && destination.equals(job.requester)) {
            return info.withInner(requesterInfo.get(job.id));
        }
        return info;
    }

    /** Used by crafting pipes when they ship items out of their pools. */
    public CraftDelivery deliveryFor(CraftDelivery info, UUID destination) {
        return withRequesterInfo(info, destination);
    }
}
