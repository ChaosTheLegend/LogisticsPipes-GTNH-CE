package logisticspipes.crafting.v3.plan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import logisticspipes.crafting.v3.model.CrafterInfo;
import logisticspipes.crafting.v3.model.Pattern;
import logisticspipes.crafting.v3.model.ResourceKey;

/** One pattern on one crafter, run {@link #crafts} times (plan section 2.3). */
public final class PlanNode {

    public final int id;
    public final Pattern pattern;
    public CrafterInfo crafter;
    public int patternSlot;
    public long crafts;
    public int height;
    public final List<InputResolution> inputs = new ArrayList<>();
    public final List<Allocation> allocations = new ArrayList<>();
    /** Output left over after all allocations, by key. */
    public final Map<ResourceKey, Long> surplus = new LinkedHashMap<>();

    public PlanNode(int id, Pattern pattern) {
        this.id = id;
        this.pattern = pattern;
    }

    public long totalAllocated(ResourceKey key) {
        long sum = 0;
        for (Allocation a : allocations) {
            if (a.key().equals(key)) sum += a.qty();
        }
        return sum;
    }

    @Override
    public String toString() {
        return "Node#" + id
                + "["
                + pattern.primaryOutput().displayName()
                + " x"
                + crafts
                + " on "
                + crafter
                + ", h="
                + height
                + "]";
    }
}
