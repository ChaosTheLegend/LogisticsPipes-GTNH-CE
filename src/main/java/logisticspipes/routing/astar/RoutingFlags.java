package logisticspipes.routing.astar;

import java.util.EnumSet;
import java.util.Set;

import logisticspipes.routing.PipeRoutingConnectionType;

/**
 * Bit-mask form of {@link PipeRoutingConnectionType}. The core graph and search work on {@code int} masks so the hot
 * path never allocates {@link EnumSet}s. Bit {@code i} is the enum constant with ordinal {@code i}.
 */
public final class RoutingFlags {

    public static final int CAN_ROUTE_TO = 1 << PipeRoutingConnectionType.canRouteTo.ordinal();
    public static final int CAN_REQUEST_FROM = 1 << PipeRoutingConnectionType.canRequestFrom.ordinal();
    public static final int CAN_POWER_FROM = 1 << PipeRoutingConnectionType.canPowerFrom.ordinal();
    public static final int CAN_POWER_SUB_SYSTEM_FROM = 1 << PipeRoutingConnectionType.canPowerSubSystemFrom.ordinal();
    public static final int ALL = CAN_ROUTE_TO | CAN_REQUEST_FROM | CAN_POWER_FROM | CAN_POWER_SUB_SYSTEM_FROM;
    public static final int FLAG_COUNT = 4;

    private RoutingFlags() {}

    public static int toMask(Set<PipeRoutingConnectionType> flags) {
        int mask = 0;
        for (PipeRoutingConnectionType type : flags) {
            mask |= 1 << type.ordinal();
        }
        return mask;
    }

    public static EnumSet<PipeRoutingConnectionType> toEnumSet(int mask) {
        EnumSet<PipeRoutingConnectionType> set = EnumSet.noneOf(PipeRoutingConnectionType.class);
        for (PipeRoutingConnectionType type : PipeRoutingConnectionType.values) {
            if ((mask & (1 << type.ordinal())) != 0) {
                set.add(type);
            }
        }
        return set;
    }

    public static boolean has(int mask, int flag) {
        return (mask & flag) != 0;
    }
}
