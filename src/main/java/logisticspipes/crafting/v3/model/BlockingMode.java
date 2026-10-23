package logisticspipes.crafting.v3.model;

/** When a crafter may insert the next committed set (plan section 6.4). */
public enum BlockingMode {
    /** Insert as soon as the set fits. Only safe when the machine runs a single recipe. */
    NONE,
    /** Insert only into an empty machine (ignoring non-consumed items). */
    BLOCKING,
    /** Like BLOCKING, but identical sets of the pattern that is running may stack. */
    SMART
}
