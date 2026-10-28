package logisticspipes.routing.astar;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Keeps the {@link CategoryInterests} of a module that takes items by the values of one category, like the mods of a
 * mod based sink. The router asks every second; the interests are built again only when the values differ from the ones
 * they were built from. The values are compared, not watched, so a filter changed by any path is seen.
 */
public final class CategoryInterestsCache {

    private final ItemCategory category;
    private Set<Object> builtFrom;
    private CategoryInterests interests = CategoryInterests.EMPTY;

    public CategoryInterestsCache(ItemCategory category) {
        this.category = category;
    }

    /**
     * @param values the values the module decides by in its sinksItem; a few, they are compared on every call
     */
    public CategoryInterests get(Collection<?> values) {
        if (builtFrom == null || !builtFrom.containsAll(values) || !values.containsAll(builtFrom)) {
            builtFrom = new HashSet<>(values);
            interests = CategoryInterests.of(category, builtFrom);
        }
        return interests;
    }
}
