package logisticspipes.crafting.v3.exec;

import java.util.List;
import java.util.UUID;

import logisticspipes.crafting.v3.model.ResourceKey;
import logisticspipes.crafting.v3.model.ResourceMatcher;
import logisticspipes.crafting.v3.plan.RouteOracle;
import logisticspipes.crafting.v3.plan.StockView;

/** Everything the driver needs from the world. The Minecraft implementation lives in {@code crafting.v3.mc}. */
public interface DriverWorld {

    long now();

    RouteOracle routes();

    /** Provider stock matching {@code matcher} on the network of {@code near}, minus every reservation. */
    List<StockView.Entry> availableStock(ResourceMatcher matcher, UUID near);

    /** Unreserved amount of {@code key} at one provider. */
    long availableAt(UUID provider, ResourceKey key);

    /** Physical amount of {@code key} at one provider, reservations ignored. -1 if the provider isn't loaded. */
    long physicalAt(UUID provider, ResourceKey key);

    /**
     * Extracts up to {@code qty} of {@code key} from the provider and routes it to {@code destination} carrying
     * {@code info}. Returns the amount that left.
     */
    long sendFromProvider(UUID provider, ResourceKey key, long qty, UUID destination, CraftDelivery info);

    /** The crafter at {@code location}, or null when it isn't loaded or no longer exists. */
    CrafterPort crafter(UUID location);

    /** Another crafter holding the pattern, for when a crafter was removed; null if none. */
    UUID findCrafter(String patternId, UUID excluding);

    void message(UUID player, String text);
}
