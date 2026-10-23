package logisticspipes.crafting.v3.plan;

import java.util.List;
import java.util.UUID;

import com.github.bsideup.jabel.Desugar;

import logisticspipes.crafting.v3.model.ResourceKey;
import logisticspipes.crafting.v3.model.ResourceMatcher;

/** Provider contents minus reservations. The planner gets an immutable snapshot; commit uses a live view. */
public interface StockView {

    /** Every provider location holding stock that matches {@code matcher}, with the unreserved amount. */
    List<Entry> find(ResourceMatcher matcher);

    /**
     * @param location router UUID of the providing pipe
     */
    @Desugar
    record Entry(UUID location, ResourceKey key, long available) {}
}
