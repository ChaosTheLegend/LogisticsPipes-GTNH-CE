package logisticspipes.interfaces;

import logisticspipes.utils.FluidFilter;

/**
 * What a player sets on a fluid sink, the basic fluid pipe and the fluid sink module have the same page for it.
 */
public interface IFluidSinkSettings {

    FluidFilter getFilter();

    /**
     * @return true if the sink also takes the fluids that are not in its filter, after every sink that asks for them
     */
    boolean isDefaultRoute();

    void setDefaultRoute(boolean defaultRoute);

    /**
     * Sets the filter to the fluids that are in the tank.
     */
    void importFromTank();
}
