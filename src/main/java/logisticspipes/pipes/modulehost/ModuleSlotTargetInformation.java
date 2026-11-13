package logisticspipes.pipes.modulehost;

import logisticspipes.interfaces.routing.IAdditionalTargetInformation;

import lombok.Getter;

/**
 * Names the module slot an item sent to a module host is for. Never saved: after a world load items arrive without it
 * and the host's resource managers pick the module.
 */
public class ModuleSlotTargetInformation implements IAdditionalTargetInformation {

    @Getter
    private final int moduleSlot;

    public ModuleSlotTargetInformation(int slot) {
        moduleSlot = slot;
    }
}
