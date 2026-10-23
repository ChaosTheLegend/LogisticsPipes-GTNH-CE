package logisticspipes.crafting.v3.plan;

import java.util.UUID;

import logisticspipes.crafting.v3.model.ResourceKey;

/**
 * Per-item reachability between pipes (one-way pipes, filters and firewalls included). Locations are router UUIDs.
 */
public interface RouteOracle {

    /** Whether {@code key} can be routed from the pipe at {@code from} to the pipe at {@code to}. */
    boolean canReach(UUID from, UUID to, ResourceKey key);

    /** Whether the location is a loaded, known pipe. Unloaded locations count as unreachable. */
    boolean isLoaded(UUID location);
}
