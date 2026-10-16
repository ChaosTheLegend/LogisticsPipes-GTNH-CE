package logisticspipes.crafting;

import logisticspipes.pipes.ISatellitePipe;

/**
 * A satellite that pattern crafting pipes can target; it carries a player-defined name next to its numeric id.
 */
public interface IPatternSatellitePipe extends ISatellitePipe {

    /**
     * Returns the player-defined label without falling back to the internal numeric satellite id.
     */
    String getSatelliteName();

    /**
     * Sets the label; the satellite may append a suffix to keep it unique in its network.
     */
    void setSatelliteName(String satelliteName);
}
