package logisticspipes.crafting.v3.plan;

import java.util.List;

import com.github.bsideup.jabel.Desugar;

import logisticspipes.crafting.v3.model.CrafterInfo;
import logisticspipes.crafting.v3.model.Pattern;
import logisticspipes.crafting.v3.model.ResourceMatcher;

/** Every pattern on the network, by what it makes. Planner input; must be safe to read off-thread. */
public interface PatternRegistry {

    /**
     * Patterns with an output matching {@code matcher}, best first (highest crafter priority, then registration order).
     * Each candidate lists the crafters holding the pattern, best first.
     */
    List<Candidate> candidatesFor(ResourceMatcher matcher);

    @Desugar
    record Candidate(Pattern pattern, List<CrafterSlot> crafters) {}

    /** A crafter and the pattern slot it holds the pattern in. */
    @Desugar
    record CrafterSlot(CrafterInfo crafter, int patternSlot) {}
}
