package logisticspipes.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * Remembers how much room a tank has for a fluid, so the searches for a fluid destination don't ask the tank every
 * time.
 */
public class FluidRoomCache {

    /**
     * How long an answer is trusted. Matches the router's interest refresh, so a full sink drops out of the interest
     * registry on the next refresh and comes back once room frees up.
     */
    public static final int ROOM_CACHE_TICKS = 20;

    private final Map<FluidIdentifier, Long> room = new HashMap<>();
    private long expiry = 0;

    /**
     * @param now     the world time
     * @param compute asks the tank
     * @return the room for that fluid
     */
    public long getRoom(FluidIdentifier fluid, long now, ToLongFunction<FluidIdentifier> compute) {
        if (now >= expiry) {
            room.clear();
            expiry = now + ROOM_CACHE_TICKS;
        }
        Long cached = room.get(fluid);
        if (cached == null) {
            cached = compute.applyAsLong(fluid);
            room.put(fluid, cached);
        }
        return cached;
    }

    /**
     * Forgets the answers, the tank content was changed by the owner.
     */
    public void clear() {
        room.clear();
    }
}
