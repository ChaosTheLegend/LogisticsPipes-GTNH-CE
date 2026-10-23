package logisticspipes.crafting.v3.mc;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;

import logisticspipes.crafting.monitor.CraftingMonitorService;
import logisticspipes.crafting.v3.exec.CraftingJob;
import logisticspipes.crafting.v3.model.ResourceAmount;
import logisticspipes.crafting.v3.model.ResourceKey;
import logisticspipes.crafting.v3.model.ResourceMatcher;
import logisticspipes.crafting.v3.plan.Plan;
import logisticspipes.crafting.v3.plan.PlanIssue;
import logisticspipes.crafting.v3.plan.SourceClaim;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.IRequest;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.request.RequestLog;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTree.ActiveRequestType;
import logisticspipes.request.resources.FluidResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.request.resources.ItemResource;
import logisticspipes.routing.order.LinkedLogisticsOrderList;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * Bridges the request API ({@link RequestTree}) to the v3 crafting system. Requests that stock alone satisfies keep the
 * old provider path; anything that needs crafting is planned and run as a crafting job.
 */
public final class CraftingRequests {

    /** The player behind the request being handled, for job messages; set by the request handler. */
    private static final ThreadLocal<UUID> CURRENT_PLAYER = new ThreadLocal<>();

    private CraftingRequests() {}

    public static <T> T asPlayer(EntityPlayer player, java.util.function.Supplier<T> request) {
        UUID previous = CURRENT_PLAYER.get();
        CURRENT_PLAYER.set(player == null ? null : player.getUniqueID());
        try {
            return request.get();
        } finally {
            CURRENT_PLAYER.set(previous);
        }
    }

    /** Marks {@code player} as the one requesting until {@link #endPlayer}; returns what to pass there. */
    public static UUID beginPlayer(EntityPlayer player) {
        UUID previous = CURRENT_PLAYER.get();
        CURRENT_PLAYER.set(player == null ? null : player.getUniqueID());
        return previous;
    }

    public static void endPlayer(UUID previous) {
        CURRENT_PLAYER.set(previous);
    }

    public static void asPlayer(EntityPlayer player, Runnable request) {
        asPlayer(player, () -> {
            request.run();
            return null;
        });
    }

    public static boolean handles(EnumSet<ActiveRequestType> flags) {
        return flags.contains(ActiveRequestType.Craft) && CraftingService.INSTANCE.driver() != null;
    }

    public static EnumSet<ActiveRequestType> withoutCrafting(EnumSet<ActiveRequestType> flags) {
        EnumSet<ActiveRequestType> copy = EnumSet.copyOf(flags);
        copy.remove(ActiveRequestType.Craft);
        if (copy.isEmpty()) copy.add(ActiveRequestType.Provide);
        return copy;
    }

    /**
     * An item request that may need crafting. Same contract as the old request worker: returns the amount that will
     * arrive (or, when simulating, could).
     */
    public static int requestItem(ItemIdentifierStack item, IRequestItems requester, RequestLog log,
            boolean acceptPartial, boolean simulateOnly, boolean logMissing, boolean logUsed,
            EnumSet<ActiveRequestType> flags, IAdditionalTargetInformation info) {
        ResourceKey key = ItemKey.of(item.getItem());
        return request(
                key,
                item.getStackSize(),
                requester,
                new ItemResource(item, requester),
                log,
                acceptPartial,
                simulateOnly,
                logMissing,
                logUsed,
                flags,
                info,
                () -> RequestTree.request(
                        item,
                        requester,
                        log,
                        acceptPartial,
                        simulateOnly,
                        logMissing,
                        logUsed,
                        withoutCrafting(flags),
                        info));
    }

    public static int requestFluid(FluidIdentifier fluid, int amount, IRequestFluid requester, RequestLog log,
            boolean acceptPartial, EnumSet<ActiveRequestType> flags, IAdditionalTargetInformation info,
            java.util.function.IntSupplier stockOnly) {
        return request(
                FluidKey.of(fluid),
                amount,
                requester,
                new FluidResource(fluid, amount, requester),
                log,
                acceptPartial,
                false,
                true,
                false,
                flags,
                info,
                stockOnly);
    }

    private static int request(ResourceKey key, long qty, IRequest requester, IResource display, RequestLog log,
            boolean acceptPartial, boolean simulateOnly, boolean logMissing, boolean logUsed,
            EnumSet<ActiveRequestType> flags, IAdditionalTargetInformation info,
            java.util.function.IntSupplier stockOnly) {
        if (!flags.contains(ActiveRequestType.Provide) && !flags.contains(ActiveRequestType.Craft)
                || requester.getRouter() == null) {
            return stockOnly.getAsInt();
        }
        Plan plan = CraftingService.INSTANCE.plan(
                requester.getRouter().getId(),
                ResourceMatcher.exact(key),
                qty,
                !flags.contains(ActiveRequestType.Provide));
        if (plan.isFailed()) {
            tell("Crafting " + qty + "x " + key.displayName() + " could not be planned: " + plan.error);
            return acceptPartial ? stockOnly.getAsInt() : fail(plan, display, log, logMissing);
        }
        if (plan.nodes.isEmpty() && plan.canStart()) {
            // no crafting needed after all: the old provider path handles stock
            return stockOnly.getAsInt();
        }
        if (simulateOnly) {
            if (log != null && logUsed) {
                log.handleSucessfullRequestOfList(usedOf(plan, requester), new LinkedLogisticsOrderList());
            }
            if (log != null) log.handleMissingItems(missingOf(plan, requester));
            return plan.canStart() ? (int) qty : 0;
        }
        if (!plan.canStart()) {
            return acceptPartial ? stockOnly.getAsInt() : fail(plan, display, log, logMissing);
        }
        CraftingJob job = CraftingService.INSTANCE.start(plan, CURRENT_PLAYER.get(), info);
        warnAboutIssues(plan, job);
        LinkedLogisticsOrderList view = JobOrderView.of(job);
        CraftingMonitorService.INSTANCE.observe(display, view);
        if (log != null) {
            log.handleCraftedAmount((int) Math.min(Integer.MAX_VALUE, craftedAmount(plan)));
            log.handleSucessfullRequestOf(display.copyForDisplayWith((int) qty), view);
        }
        return (int) qty;
    }

    /**
     * A list request (crafting table "request ingredients"): all or nothing. Each item is planned and started in turn,
     * so later plans see what earlier jobs reserved; if one can't start, the started ones are cancelled.
     */
    public static boolean requestList(List<ItemIdentifierStack> items, IRequestItems requester, RequestLog log,
            EnumSet<ActiveRequestType> flags, IAdditionalTargetInformation info) {
        if (requester.getRouter() == null) return false;
        List<CraftingJob> started = new ArrayList<>();
        List<IResource> missing = new ArrayList<>();
        List<IResource> done = new ArrayList<>();
        for (ItemIdentifierStack stack : items) {
            Plan plan = CraftingService.INSTANCE.plan(
                    requester.getRouter().getId(),
                    ResourceMatcher.exact(ItemKey.of(stack.getItem())),
                    stack.getStackSize(),
                    !flags.contains(ActiveRequestType.Provide));
            if (plan.isFailed() || !plan.canStart()) {
                missing.addAll(
                        plan.isFailed() ? java.util.Collections.singletonList(new ItemResource(stack, requester))
                                : missingOf(plan, requester));
                continue;
            }
            CraftingJob job = CraftingService.INSTANCE.start(plan, CURRENT_PLAYER.get(), info);
            started.add(job);
            ItemResource resource = new ItemResource(stack, requester);
            CraftingMonitorService.INSTANCE.observe(resource, JobOrderView.of(job));
            done.add(resource);
        }
        if (!missing.isEmpty()) {
            for (CraftingJob job : started) CraftingService.INSTANCE.cancel(job.id);
            if (log != null) log.handleMissingItems(missing);
            return false;
        }
        if (log != null) log.handleSucessfullRequestOfList(done, new LinkedLogisticsOrderList());
        return true;
    }

    /**
     * Starts a plan the player confirmed in a preview. Provider claims are re-checked at commit, so stock that moved
     * since the preview is re-sourced instead of failing the job.
     */
    public static CraftingJob startPlanned(EntityPlayer player, Plan plan, IResource display,
            IAdditionalTargetInformation info) {
        UUID previous = beginPlayer(player);
        try {
            if (!plan.canStart()) return null;
            CraftingJob job = CraftingService.INSTANCE.start(plan, CURRENT_PLAYER.get(), info);
            warnAboutIssues(plan, job);
            CraftingMonitorService.INSTANCE.observe(display, JobOrderView.of(job));
            return job;
        } finally {
            endPlayer(previous);
        }
    }

    private static int fail(Plan plan, IResource display, RequestLog log, boolean logMissing) {
        if (log != null && logMissing) {
            List<IResource> missing = plan.isFailed() ? java.util.Collections.emptyList() : missingOf(plan, null);
            log.handleMissingItems(missing.isEmpty() ? java.util.Collections.singletonList(display) : missing);
        }
        return 0;
    }

    private static long craftedAmount(Plan plan) {
        long crafted = 0;
        for (SourceClaim c : plan.root.sources) {
            if (c instanceof SourceClaim.Craft || c instanceof SourceClaim.Surplus) crafted += c.qty();
        }
        return crafted;
    }

    static List<IResource> missingOf(Plan plan, IRequest requester) {
        Map<ResourceKey, Long> totals = new LinkedHashMap<>();
        for (PlanIssue issue : plan.issues) {
            if (issue.blocksStart()) totals.merge(issue.what().key(), issue.qty(), Long::sum);
        }
        return toResources(totals, requester);
    }

    static List<IResource> usedOf(Plan plan, IRequest requester) {
        return toResources(plan.rawTotals, requester);
    }

    private static List<IResource> toResources(Map<ResourceKey, Long> totals, IRequest requester) {
        List<IResource> result = new ArrayList<>();
        totals.forEach((k, v) -> {
            int n = (int) Math.min(Integer.MAX_VALUE, v);
            if (k instanceof ItemKey) {
                result.add(
                        new ItemResource(
                                new ItemIdentifierStack(((ItemKey) k).id, n),
                                requester instanceof IRequestItems ? (IRequestItems) requester : null));
            } else if (k instanceof FluidKey) {
                result.add(
                        new FluidResource(
                                ((FluidKey) k).id,
                                n,
                                requester instanceof IRequestFluid ? (IRequestFluid) requester : null));
            }
        });
        return result;
    }

    /** Unreachable inputs and legacy crafters don't stop a job, but the player should know (plan section 13). */
    private static void warnAboutIssues(Plan plan, CraftingJob job) {
        UUID player = CURRENT_PLAYER.get();
        if (player == null) return;
        Set<String> legacy = new LinkedHashSet<>();
        long unreachable = 0;
        for (PlanIssue issue : plan.issues) {
            if (issue.kind() == PlanIssue.Kind.LEGACY_CRAFTER) legacy.add(issue.detail());
            if (issue.kind() == PlanIssue.Kind.UNREACHABLE) unreachable += issue.qty();
        }
        if (!legacy.isEmpty()) {
            CraftingService.tell(
                    player,
                    "Warning: this craft uses old crafting modules/pipes, which are deprecated"
                            + " and will be removed: "
                            + String.join("; ", legacy)
                            + ". Replace them with pattern crafting pipes.");
        }
        if (unreachable > 0) {
            CraftingService.tell(
                    player,
                    unreachable + " item(s) for this craft can't be routed yet; the job waits" + " until they can.");
        }
        int steps = plan.nodes.size();
        if (steps > 0) {
            CraftingService.tell(
                    player,
                    "Crafting " + job.qty
                            + "x "
                            + job.target
                            + " started ("
                            + steps
                            + " step"
                            + (steps == 1 ? "" : "s")
                            + ")");
        }
    }

    private static void tell(String text) {
        UUID player = CURRENT_PLAYER.get();
        if (player != null) CraftingService.tell(player, text);
    }

    /** Raw materials a plan takes from storage, for display. */
    public static List<ResourceAmount> rawMaterials(Plan plan) {
        List<ResourceAmount> result = new ArrayList<>();
        plan.rawTotals.forEach((k, v) -> result.add(new ResourceAmount(k, v)));
        return result;
    }
}
