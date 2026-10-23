package logisticspipes.crafting.v3.model;

import java.util.UUID;

import com.github.bsideup.jabel.Desugar;

/**
 * A crafter as the planner sees it.
 *
 * @param id         router UUID of the crafting pipe
 * @param legacy     an old crafting module/pipe: no buffering, no blocking mode, shown with a removal warning
 * @param batchLimit most crafts one ticket may carry (1 for workbench-style crafters)
 */
@Desugar
public record CrafterInfo(UUID id, String name, BlockingMode mode, int priority, boolean legacy, int batchLimit) {

    @Override
    public String toString() {
        return name + (legacy ? " (legacy)" : "");
    }
}
