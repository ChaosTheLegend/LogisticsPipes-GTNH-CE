package logisticspipes.crafting.v3.model;

/**
 * Exact identity of something the crafting system plans and moves: an item (item, damage, NBT) or a fluid.
 * <p>
 * Implementations are value objects: {@code equals}/{@code hashCode} compare the exact identity. The planner and the
 * driver never look inside a key; Minecraft-specific behaviour (ore dictionary, damage, NBT) is behind
 * {@link #matches(ResourceKey, MatchRule, String)}, so the planner can be tested with fake keys.
 */
public interface ResourceKey {

    boolean isFluid();

    /** Largest amount that fits in one inventory slot. Fluids return {@link Integer#MAX_VALUE}. */
    int maxStackSize();

    String displayName();

    /**
     * Whether {@code other} is accepted where this key is asked for under {@code rule}. {@link MatchRule#EXACT} must be
     * plain equality.
     */
    default boolean matches(ResourceKey other, MatchRule rule, String oreName) {
        return equals(other);
    }
}
