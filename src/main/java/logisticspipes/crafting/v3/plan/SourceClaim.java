package logisticspipes.crafting.v3.plan;

import java.util.UUID;

import com.github.bsideup.jabel.Desugar;

import logisticspipes.crafting.v3.model.ResourceKey;

/** Where part of a node's input comes from (plan section 2.3). */
public interface SourceClaim {

    ResourceKey key();

    long qty();

    /** Stock in a provider. */
    @Desugar
    record Provider(UUID provider, ResourceKey key, long qty) implements SourceClaim {}

    /** Leftover output of another node. */
    @Desugar
    record Surplus(int producer, ResourceKey key, long qty) implements SourceClaim {}

    /** Crafted by a child node for this consumer. */
    @Desugar
    record Craft(int child, ResourceKey key, long qty) implements SourceClaim {}

    /** No source: the items don't exist ({@code unreachable == false}) or exist but can't be routed here. */
    @Desugar
    record Unresolved(ResourceKey key, long qty, boolean unreachable) implements SourceClaim {}
}
