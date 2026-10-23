package logisticspipes.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import logisticspipes.crafting.v3.exec.CraftingDriver;
import logisticspipes.crafting.v3.exec.CraftingJob;
import logisticspipes.crafting.v3.exec.JobState;
import logisticspipes.crafting.v3.exec.SetState;
import logisticspipes.crafting.v3.exec.StallCause;
import logisticspipes.crafting.v3.exec.Ticket;
import logisticspipes.crafting.v3.exec.TicketState;
import logisticspipes.crafting.v3.mc.CraftingService;
import logisticspipes.crafting.v3.mc.FluidKey;
import logisticspipes.crafting.v3.mc.ItemKey;
import logisticspipes.crafting.v3.model.ResourceKey;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.order.LinkedLogisticsOrderList;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * Builds the crafting monitor views (crafting monitor block, request table) from the v3 crafting jobs (plan section
 * 10.2): one entry per job, one child per plan node, each with its set state as an icon, a label and a color, and the
 * stall detail when it waits on something.
 */
public final class PatternCraftingMonitorRegistry {

    /** Marks monitor ids that stand for v3 jobs: {@code new UUID(JOB_MARKER, jobId)}. */
    private static final long JOB_MARKER = 0x4C50_4A4F_4256_3300L;

    private PatternCraftingMonitorRegistry() {}

    public static void clear() {}

    /** Request-table watches of plain provider orders: crafting no longer goes through order lists. */
    public static List<PatternCraftingMonitorNode> build(LinkedLogisticsOrderList orders) {
        return Collections.emptyList();
    }

    /** One entry per running crafting job whose requester is on the network of {@code networkRouter}. */
    public static List<PatternCraftingMonitorEntry> buildAll(IRouter networkRouter) {
        CraftingDriver driver = CraftingService.INSTANCE.driver();
        if (networkRouter == null || driver == null) {
            return Collections.emptyList();
        }
        List<PatternCraftingMonitorEntry> result = new ArrayList<>();
        for (CraftingJob job : driver.jobs()) {
            if (!onNetwork(job, networkRouter)) {
                continue;
            }
            result.add(
                    new PatternCraftingMonitorEntry(
                            new UUID(JOB_MARKER, job.id),
                            Collections.singletonList(jobNode(driver, job))));
        }
        return result;
    }

    /** Cancels a job shown in a monitor on the same network. */
    public static boolean cancelInstance(UUID instanceId, IRouter networkRouter) {
        CraftingDriver driver = CraftingService.INSTANCE.driver();
        if (instanceId == null || networkRouter == null
                || driver == null
                || instanceId.getMostSignificantBits() != JOB_MARKER) {
            return false;
        }
        CraftingJob job = driver.job(instanceId.getLeastSignificantBits());
        return job != null && onNetwork(job, networkRouter) && driver.cancelJob(job.id);
    }

    private static boolean onNetwork(CraftingJob job, IRouter networkRouter) {
        if (networkRouter.getId().equals(job.requester)) {
            return true;
        }
        IRouter requester = CraftingService.router(job.requester);
        return requester != null && !networkRouter.getDistanceTo(requester).isEmpty();
    }

    private static PatternCraftingMonitorNode jobNode(CraftingDriver driver, CraftingJob job) {
        Ticket delivery = job.delivery();
        long delivered = delivery == null ? 0 : delivery.delivered();
        boolean winding = job.state() == JobState.CANCELLING;
        PatternCraftingMonitorNode root = new PatternCraftingMonitorNode(
                display(job.target.key(), job.qty),
                (int) Math.min(Integer.MAX_VALUE, job.qty - delivered),
                (int) Math.min(Integer.MAX_VALUE, job.qty),
                !winding);
        Map<Integer, List<Ticket>> byNode = new LinkedHashMap<>();
        for (Ticket t : job.tickets()) {
            if (!t.isDelivery()) byNode.computeIfAbsent(t.node, k -> new ArrayList<>()).add(t);
        }
        SetState worst = SetState.DONE;
        for (CraftingJob.NodeInfo info : job.nodes()) {
            List<Ticket> tickets = byNode.getOrDefault(info.node(), Collections.emptyList());
            PatternCraftingMonitorNode node = nodeView(driver, info, tickets);
            root.addChild(node);
            worst = worse(worst, stateOf(node));
        }
        if (winding) {
            root.withStatus(
                    "[-] " + (job.isDelivered() ? "Finishing" : "Cancelling"),
                    0xFF9E9E9E,
                    "Waiting for running sets and items in transit");
        } else {
            String detail = stallSummary(driver, job);
            root.withStatus(worst.icon + " " + worst.label, worst.color, detail);
        }
        return root;
    }

    private static PatternCraftingMonitorNode nodeView(CraftingDriver driver, CraftingJob.NodeInfo info,
            List<Ticket> tickets) {
        long remaining = 0;
        boolean running = false;
        SetState worst = SetState.DONE;
        StringBuilder detail = new StringBuilder();
        for (Ticket t : tickets) {
            SetState s = driver.displayState(t);
            if (t.state() != TicketState.DONE && t.state() != TicketState.CANCELLED) remaining += t.crafts;
            if (s == SetState.DISPATCHED) running = true;
            worst = worse(worst, s);
            if (s.isStalled() && detail.length() == 0) detail.append(stallText(driver, t));
        }
        if (info.legacy()) {
            if (detail.length() > 0) detail.append(" | ");
            detail.append("[!] Deprecated crafter: ").append(info.crafterName()).append(" will be removed");
        }
        PatternCraftingMonitorNode node = new PatternCraftingMonitorNode(
                display(info.output(), info.crafts()),
                (int) Math.min(Integer.MAX_VALUE, remaining),
                (int) Math.min(Integer.MAX_VALUE, info.crafts()),
                running);
        String label = worst.icon + " " + worst.label + (info.legacy() ? " [!] legacy" : "");
        return node.withStatus(label, worst.color, detail.toString());
    }

    private static String stallText(CraftingDriver driver, Ticket t) {
        for (CraftingDriver.Stall stall : driver.stalls(t.job)) {
            if (stall.ticket() != t) continue;
            if (stall.key() != null) {
                String cause = stall.cause() == StallCause.MISSING ? "missing" : "unreachable";
                return stall.qty() + "x "
                        + stall.key().displayName()
                        + " "
                        + cause
                        + (stall.problem() != null ? " (" + stall.problem() + ")" : "");
            }
            if (stall.problem() != null) return stall.problem();
        }
        return t.problem() == null ? "" : t.problem();
    }

    /** Short text of the job's stalls plus what it holds reserved (plan sections 7.5 and 10.3). */
    private static String stallSummary(CraftingDriver driver, CraftingJob job) {
        List<CraftingDriver.Stall> stalls = driver.stalls(job.id);
        if (stalls.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (CraftingDriver.Stall stall : stalls) {
            if (shown++ >= 3) {
                sb.append(", ...");
                break;
            }
            if (sb.length() > 0) sb.append(", ");
            sb.append(
                    stall.key() == null ? stall.problem()
                            : stall.qty() + "x "
                                    + stall.key().displayName()
                                    + " "
                                    + (stall.cause() == StallCause.MISSING ? "missing" : "unreachable"));
        }
        Map<ResourceKey, Long> reserved = driver.reservedBy(job.id);
        if (!reserved.isEmpty()) {
            sb.append(" | reserved: ");
            int n = 0;
            for (Map.Entry<ResourceKey, Long> e : reserved.entrySet()) {
                if (n++ >= 3) {
                    sb.append("...");
                    break;
                }
                sb.append(e.getValue()).append("x ").append(e.getKey().displayName()).append(' ');
            }
        }
        return sb.toString().trim();
    }

    private static SetState stateOf(PatternCraftingMonitorNode node) {
        for (SetState s : SetState.values()) {
            if (node.getStatus().startsWith(s.icon + " " + s.label)) return s;
        }
        return SetState.WAITING;
    }

    /** Stalls first (missing, unreachable, wrong craft), then running work, then waiting, then done. */
    private static SetState worse(SetState a, SetState b) {
        return rank(b) > rank(a) ? b : a;
    }

    private static int rank(SetState s) {
        switch (s) {
            case MISSING:
                return 7;
            case WRONG_CRAFT:
                return 6;
            case UNREACHABLE:
                return 5;
            case DISPATCHED:
                return 4;
            case READY:
                return 3;
            case WAITING:
                return 2;
            default:
                return 1;
        }
    }

    private static ItemIdentifierStack display(ResourceKey key, long amount) {
        int n = (int) Math.min(Integer.MAX_VALUE, amount);
        if (key instanceof ItemKey) return ((ItemKey) key).id.makeStack(n);
        if (key instanceof FluidKey) return ((FluidKey) key).routingItem().makeStack(n);
        return null;
    }
}
