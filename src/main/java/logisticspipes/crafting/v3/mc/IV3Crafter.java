package logisticspipes.crafting.v3.mc;

import java.util.Map;

import logisticspipes.crafting.v3.exec.CrafterPort;
import logisticspipes.crafting.v3.model.CrafterInfo;
import logisticspipes.crafting.v3.model.Pattern;

/** Something on the network that crafts for the v3 driver: a pattern crafting pipe, or a legacy crafter wrapper. */
public interface IV3Crafter {

    CrafterInfo crafterInfo();

    /** Patterns by pattern slot. Only patterns the crafter can run right now. */
    Map<Integer, Pattern> v3Patterns();

    CrafterPort port();
}
