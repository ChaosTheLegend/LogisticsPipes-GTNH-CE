package logisticspipes.crafting.v3.model;

import java.util.Objects;

/**
 * What a pattern input accepts: a canonical key plus a match rule. Demand is aggregated per matcher, so two inputs
 * asking for the same key under the same rule share stock, surplus and crafts.
 */
public final class ResourceMatcher {

    private final ResourceKey key;
    private final MatchRule rule;
    private final String oreName;
    private final int hash;

    private ResourceMatcher(ResourceKey key, MatchRule rule, String oreName) {
        this.key = Objects.requireNonNull(key, "key");
        this.rule = rule == null ? MatchRule.EXACT : rule;
        this.oreName = this.rule == MatchRule.ORE_DICT ? oreName : null;
        this.hash = Objects.hash(key, this.rule, this.oreName);
    }

    public static ResourceMatcher exact(ResourceKey key) {
        return new ResourceMatcher(key, MatchRule.EXACT, null);
    }

    public static ResourceMatcher of(ResourceKey key, MatchRule rule, String oreName) {
        return new ResourceMatcher(key, rule, oreName);
    }

    public ResourceKey key() {
        return key;
    }

    public MatchRule rule() {
        return rule;
    }

    public String oreName() {
        return oreName;
    }

    public boolean isExact() {
        return rule == MatchRule.EXACT;
    }

    public boolean matches(ResourceKey other) {
        if (other == null || other.isFluid() != key.isFluid()) {
            return false;
        }
        if (rule == MatchRule.EXACT) {
            return key.equals(other);
        }
        return key.equals(other) || key.matches(other, rule, oreName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceMatcher)) return false;
        ResourceMatcher that = (ResourceMatcher) o;
        return hash == that.hash && rule == that.rule && key.equals(that.key) && Objects.equals(oreName, that.oreName);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return rule == MatchRule.EXACT ? key.displayName()
                : key.displayName() + "[" + (oreName != null ? "ore:" + oreName : rule.name().toLowerCase()) + "]";
    }
}
