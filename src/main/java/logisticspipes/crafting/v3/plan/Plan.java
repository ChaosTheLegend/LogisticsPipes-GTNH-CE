package logisticspipes.crafting.v3.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import logisticspipes.crafting.v3.model.ResourceKey;

/** Planner output (plan section 3.5). Immutable once returned. */
public final class Plan {

    public final PlanRequest request;
    /** How the request itself is satisfied: stock and/or crafts delivered to the requester. */
    public final InputResolution root;
    /** Nodes with at least one craft, by id, in processing order (parents before children). */
    public final Map<Integer, PlanNode> nodes;
    public final List<PlanIssue> issues;
    /** Items taken from providers, by key. */
    public final Map<ResourceKey, Long> rawTotals;
    /** Set when planning was aborted (node cap or time budget); everything else is then empty. */
    public final String error;

    Plan(PlanRequest request, InputResolution root, Map<Integer, PlanNode> nodes, List<PlanIssue> issues,
            Map<ResourceKey, Long> rawTotals, String error) {
        this.request = request;
        this.root = root;
        this.nodes = Collections.unmodifiableMap(nodes);
        this.issues = Collections.unmodifiableList(issues);
        this.rawTotals = Collections.unmodifiableMap(rawTotals);
        this.error = error;
    }

    static Plan failed(PlanRequest request, String error) {
        return new Plan(request, null, new LinkedHashMap<>(), new ArrayList<>(), new LinkedHashMap<>(), error);
    }

    public boolean isFailed() {
        return error != null;
    }

    /** False when something is missing; such a job may not start (plan section 13, "Start anyway"). */
    public boolean canStart() {
        if (isFailed()) return false;
        for (PlanIssue issue : issues) {
            if (issue.blocksStart()) return false;
        }
        return true;
    }

    public boolean hasIssues(PlanIssue.Kind kind) {
        for (PlanIssue issue : issues) {
            if (issue.kind() == kind) return true;
        }
        return false;
    }

    public PlanNode node(int id) {
        return nodes.get(id);
    }

    public int maxHeight() {
        int max = 0;
        for (PlanNode n : nodes.values()) max = Math.max(max, n.height);
        return max;
    }
}
