package logisticspipes.crafting.v3.model;

/** How an input slot accepts items other than the exact one in the pattern. */
public enum MatchRule {

    EXACT,
    IGNORE_NBT,
    IGNORE_DAMAGE,
    ORE_DICT;

    /** Lower is stricter. Arrivals fill strict needs before fuzzy ones (plan section 6.3). */
    public int strictness() {
        return ordinal();
    }
}
