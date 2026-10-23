package logisticspipes.crafting.v3.plan;

import java.util.UUID;

import com.github.bsideup.jabel.Desugar;

import logisticspipes.crafting.v3.model.ResourceMatcher;

/**
 * @param requester router UUID of the pipe that receives the result
 * @param craftOnly the requested item itself must be crafted, not taken from stock (its ingredients still may be)
 */
@Desugar
public record PlanRequest(ResourceMatcher target, long qty, UUID requester, boolean craftOnly) {

    public PlanRequest(ResourceMatcher target, long qty, UUID requester) {
        this(target, qty, requester, false);
    }
}
