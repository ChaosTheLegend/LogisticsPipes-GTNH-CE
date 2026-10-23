package logisticspipes.crafting.v3.exec;

/** What the job view shows per ticket (plan section 2.6). Colors are always paired with an icon and a label. */
public enum SetState {

    WAITING("Waiting", 0xFF9E9E9E, "[..]"),
    READY("Ready", 0xFF4A90D9, "[>]"),
    DISPATCHED("Running", 0xFF4A90D9, "[*]"),
    DONE("Done", 0xFF4CAF50, "[ok]"),
    UNREACHABLE("Unreachable", 0xFFFF9800, "[!]"),
    MISSING("Missing", 0xFFE53935, "[x]"),
    WRONG_CRAFT("Wrong craft", 0xFF9C27B0, "[?]");

    public final String label;
    public final int color;
    public final String icon;

    SetState(String label, int color, String icon) {
        this.label = label;
        this.color = color;
        this.icon = icon;
    }

    public boolean isStalled() {
        return this == UNREACHABLE || this == MISSING || this == WRONG_CRAFT;
    }
}
