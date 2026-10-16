package logisticspipes.proxy.buildcraft.subproxies;

import logisticspipes.pipes.basic.LogisticsTileGenericPipe;

public interface IBCRenderTESR {

    void renderWires(LogisticsTileGenericPipe pipe, double x, double y, double z);

    void dynamicRenderPluggables(LogisticsTileGenericPipe pipe, double x, double y, double z);

    /** Whether the pipe has wires or dynamically rendered pluggables to draw. */
    boolean hasDynamicContent(LogisticsTileGenericPipe pipe);
}
