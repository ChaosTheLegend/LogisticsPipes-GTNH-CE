package logisticspipes.crafting.v3.plan;

import com.github.bsideup.jabel.Desugar;

import logisticspipes.crafting.v3.model.ResourceMatcher;

/**
 * Something the player has to look at in the preview.
 *
 * @param node node id that needs the item, or {@link Allocation#REQUESTER}
 */
@Desugar
public record PlanIssue(Kind kind, ResourceMatcher what, long qty, int node, String detail) {

    public enum Kind {
        /** Doesn't exist anywhere and can't be crafted. Blocks starting. */
        MISSING,
        /** Exists, but there is no route to where it's needed. */
        UNREACHABLE,
        /** A recipe loop made every pattern for the item unusable. */
        CYCLE,
        /** A node runs on an old crafting module/pipe that is going away. */
        LEGACY_CRAFTER
    }

    public boolean blocksStart() {
        return kind == Kind.MISSING || kind == Kind.CYCLE;
    }
}
