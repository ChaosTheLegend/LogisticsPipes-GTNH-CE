package logisticspipes.routing.astar;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * The values of {@link ItemCategory item categories} a router is interested in, like "mod gregtech" or "holds fluid".
 * Immutable: a module can hand out the same instance as long as its filter doesn't change, and the router then sees
 * nothing changed by comparing the reference.
 */
public final class CategoryInterests {

    public static final CategoryInterests EMPTY = new CategoryInterests(new EnumMap<>(ItemCategory.class));

    private final EnumMap<ItemCategory, Set<Object>> keys;

    private CategoryInterests(EnumMap<ItemCategory, Set<Object>> keys) {
        this.keys = keys;
    }

    /**
     * @return interest in the items of a category with a single value, like {@link ItemCategory#FLUID_HOLDING}
     */
    public static CategoryInterests flag(ItemCategory category) {
        return of(category, Collections.singleton(Boolean.TRUE));
    }

    /**
     * @return interest in the items that have one of the values of the category, EMPTY if there are none
     */
    public static CategoryInterests of(ItemCategory category, Collection<?> values) {
        if (values.isEmpty()) {
            return EMPTY;
        }
        EnumMap<ItemCategory, Set<Object>> keys = new EnumMap<>(ItemCategory.class);
        keys.put(category, Collections.unmodifiableSet(new HashSet<>(values)));
        return new CategoryInterests(keys);
    }

    /**
     * @return the interests of both; one of the two itself when the other is empty, so the reference stays the same
     */
    public CategoryInterests merge(CategoryInterests other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        EnumMap<ItemCategory, Set<Object>> merged = new EnumMap<>(keys);
        for (Map.Entry<ItemCategory, Set<Object>> entry : other.keys.entrySet()) {
            Set<Object> mine = merged.get(entry.getKey());
            if (mine == null) {
                merged.put(entry.getKey(), entry.getValue());
            } else {
                Set<Object> both = new HashSet<>(mine);
                both.addAll(entry.getValue());
                merged.put(entry.getKey(), Collections.unmodifiableSet(both));
            }
        }
        return new CategoryInterests(merged);
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    public boolean contains(ItemCategory category, Object value) {
        Set<Object> values = keys.get(category);
        return values != null && values.contains(value);
    }

    public void forEach(BiConsumer<ItemCategory, Object> action) {
        for (Map.Entry<ItemCategory, Set<Object>> entry : keys.entrySet()) {
            for (Object value : entry.getValue()) {
                action.accept(entry.getKey(), value);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof CategoryInterests other && keys.equals(other.keys));
    }

    @Override
    public int hashCode() {
        return keys.hashCode();
    }

    @Override
    public String toString() {
        return keys.toString();
    }
}
