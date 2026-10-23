package logisticspipes.crafting.v3.plan;

import com.github.bsideup.jabel.Desugar;

import logisticspipes.crafting.v3.model.ResourceKey;

/**
 * Who receives part of a node's output.
 *
 * @param consumer node id, or {@link #REQUESTER}
 */
@Desugar
public record Allocation(int consumer, ResourceKey key, long qty) {

    public static final int REQUESTER = -1;

    public boolean toRequester() {
        return consumer == REQUESTER;
    }
}
