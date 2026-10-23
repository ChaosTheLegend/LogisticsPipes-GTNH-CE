package logisticspipes.crafting.v3.mc;

import java.util.Map;

import logisticspipes.crafting.v3.exec.CraftDelivery;
import logisticspipes.crafting.v3.model.ResourceKey;

/**
 * A provider pipe or module the crafting driver takes stock from (plan section 4: provider claims). The location of the
 * stock is the router of the pipe holding it.
 * <p>
 * Counts are what the source could hand out right now: its contents minus its own pending orders. Crafting reservations
 * are subtracted by {@link CraftingService}, never by the source.
 */
public interface ICraftingStockSource {

    /** Adds everything this source holds to {@code into} (merging with what is there). */
    void listCraftingStock(Map<ResourceKey, Long> into);

    long craftingStockCount(ResourceKey key);

    /**
     * Extracts up to {@code qty} of {@code key} and routes it to {@code destination} with {@code delivery} as its
     * target information. Returns the amount that left.
     */
    long sendForCrafting(ResourceKey key, long qty, int destination, CraftDelivery delivery);
}
