package logisticspipes.modules;

import logisticspipes.utils.FluidIdentifier;

/**
 * A fluid module that promises fluid of the tank to requests. The modules of one chassis share the tank, so each of
 * them offers only what the others haven't promised yet.
 */
public interface IFluidReserver {

    /**
     * @return the amount of that fluid in the tank this module has promised and not handed out yet
     */
    int getReservedAmount(FluidIdentifier fluid);
}
