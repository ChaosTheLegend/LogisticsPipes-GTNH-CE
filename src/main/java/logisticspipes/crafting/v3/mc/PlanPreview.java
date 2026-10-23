package logisticspipes.crafting.v3.mc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import logisticspipes.crafting.v3.model.ResourceKey;
import logisticspipes.crafting.v3.plan.Plan;
import logisticspipes.crafting.v3.plan.PlanIssue;
import logisticspipes.crafting.v3.plan.PlanNode;
import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * What the player sees before a crafting job starts (plan section 10.1): the steps with their crafters, the raw
 * materials taken from storage, and what is missing or unreachable. Built on the server, sent to the client.
 */
public final class PlanPreview {

    public static final class Step {

        public final ItemIdentifierStack output;
        public final long crafts;
        public final String crafter;
        public final boolean legacy;

        public Step(ItemIdentifierStack output, long crafts, String crafter, boolean legacy) {
            this.output = output;
            this.crafts = crafts;
            this.crafter = crafter;
            this.legacy = legacy;
        }
    }

    public final ItemIdentifierStack target;
    /** Why planning failed, or empty. */
    public final String error;
    public final boolean canStart;
    public final List<Step> steps;
    public final List<ItemIdentifierStack> raw;
    public final List<ItemIdentifierStack> missing;
    public final List<ItemIdentifierStack> unreachable;

    private PlanPreview(ItemIdentifierStack target, String error, boolean canStart, List<Step> steps,
            List<ItemIdentifierStack> raw, List<ItemIdentifierStack> missing, List<ItemIdentifierStack> unreachable) {
        this.target = target;
        this.error = error == null ? "" : error;
        this.canStart = canStart;
        this.steps = steps;
        this.raw = raw;
        this.missing = missing;
        this.unreachable = unreachable;
    }

    public boolean hasUnreachable() {
        return !unreachable.isEmpty();
    }

    public boolean hasLegacy() {
        for (Step s : steps) {
            if (s.legacy) return true;
        }
        return false;
    }

    public static PlanPreview of(Plan plan) {
        ItemIdentifierStack target = JobOrderView.display(plan.request.target().key(), plan.request.qty());
        if (plan.isFailed()) {
            return new PlanPreview(
                    target,
                    plan.error,
                    false,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList());
        }
        List<PlanNode> nodes = new ArrayList<>(plan.nodes.values());
        nodes.sort((a, b) -> Integer.compare(b.height, a.height));
        List<Step> steps = new ArrayList<>();
        for (PlanNode n : nodes) {
            ResourceKey out = n.pattern.primaryOutput();
            steps.add(
                    new Step(
                            JobOrderView.display(out, n.crafts * n.pattern.yield(out)),
                            n.crafts,
                            n.crafter.name(),
                            n.crafter.legacy()));
        }
        Map<ResourceKey, Long> missing = new LinkedHashMap<>();
        Map<ResourceKey, Long> unreachable = new LinkedHashMap<>();
        for (PlanIssue issue : plan.issues) {
            if (issue.blocksStart()) missing.merge(issue.what().key(), issue.qty(), Long::sum);
            if (issue.kind() == PlanIssue.Kind.UNREACHABLE) {
                unreachable.merge(issue.what().key(), issue.qty(), Long::sum);
            }
        }
        return new PlanPreview(
                target,
                null,
                plan.canStart(),
                steps,
                stacks(plan.rawTotals),
                stacks(missing),
                stacks(unreachable));
    }

    private static List<ItemIdentifierStack> stacks(Map<ResourceKey, Long> amounts) {
        List<ItemIdentifierStack> result = new ArrayList<>();
        amounts.forEach((k, v) -> {
            ItemIdentifierStack s = JobOrderView.display(k, v);
            if (s != null) result.add(s);
        });
        return result;
    }

    public void write(LPDataOutputStream out) throws IOException {
        out.writeItemIdentifierStack(target);
        out.writeUTF(error);
        out.writeBoolean(canStart);
        out.writeInt(steps.size());
        for (Step s : steps) {
            out.writeItemIdentifierStack(s.output);
            out.writeLong(s.crafts);
            out.writeUTF(s.crafter == null ? "" : s.crafter);
            out.writeBoolean(s.legacy);
        }
        writeStacks(out, raw);
        writeStacks(out, missing);
        writeStacks(out, unreachable);
    }

    public static PlanPreview read(LPDataInputStream in) throws IOException {
        ItemIdentifierStack target = in.readItemIdentifierStack();
        String error = in.readUTF();
        boolean canStart = in.readBoolean();
        int count = in.readInt();
        List<Step> steps = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            steps.add(new Step(in.readItemIdentifierStack(), in.readLong(), in.readUTF(), in.readBoolean()));
        }
        return new PlanPreview(target, error, canStart, steps, readStacks(in), readStacks(in), readStacks(in));
    }

    private static void writeStacks(LPDataOutputStream out, List<ItemIdentifierStack> stacks) throws IOException {
        out.writeInt(stacks.size());
        for (ItemIdentifierStack s : stacks) out.writeItemIdentifierStack(s);
    }

    private static List<ItemIdentifierStack> readStacks(LPDataInputStream in) throws IOException {
        int count = in.readInt();
        List<ItemIdentifierStack> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(in.readItemIdentifierStack());
        return result;
    }
}
