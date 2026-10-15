package logisticspipes.crafting.requesttable;

import net.minecraft.nbt.NBTTagCompound;

import lombok.Value;

/**
 * Immutable sort and filter state for one player at one request table.
 */
@Value
public class RequestTableDisplaySettings {

    public static final RequestTableDisplaySettings DEFAULT = new RequestTableDisplaySettings(
            SortMode.NAME,
            SortDirection.ASCENDING,
            FilterMode.BOTH);
    private static final String NBT_SORT_MODE = "sortMode";
    private static final String NBT_SORT_DIRECTION = "sortDirection";
    private static final String NBT_FILTER_MODE = "filterMode";
    SortMode sortMode;
    SortDirection sortDirection;
    FilterMode filterMode;

    public static RequestTableDisplaySettings readFromNBT(NBTTagCompound tag) {
        return fromOrdinals(
                tag.getInteger(NBT_SORT_MODE),
                tag.getInteger(NBT_SORT_DIRECTION),
                tag.getInteger(NBT_FILTER_MODE));
    }

    public static RequestTableDisplaySettings fromOrdinals(int sortMode, int sortDirection, int filterMode) {
        return new RequestTableDisplaySettings(
                valueOrDefault(SortMode.values(), sortMode, DEFAULT.sortMode),
                valueOrDefault(SortDirection.values(), sortDirection, DEFAULT.sortDirection),
                valueOrDefault(FilterMode.values(), filterMode, DEFAULT.filterMode));
    }

    private static <T> T valueOrDefault(T[] values, int ordinal, T defaultValue) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : defaultValue;
    }

    public RequestTableDisplaySettings nextSortMode() {
        return new RequestTableDisplaySettings(sortMode.next(), sortDirection, filterMode);
    }

    public RequestTableDisplaySettings nextSortDirection() {
        return new RequestTableDisplaySettings(sortMode, sortDirection.next(), filterMode);
    }

    public RequestTableDisplaySettings nextFilterMode() {
        return new RequestTableDisplaySettings(sortMode, sortDirection, filterMode.next());
    }

    public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger(NBT_SORT_MODE, sortMode.ordinal());
        tag.setInteger(NBT_SORT_DIRECTION, sortDirection.ordinal());
        tag.setInteger(NBT_FILTER_MODE, filterMode.ordinal());
    }

    public enum SortMode {

        NAME,
        AMOUNT;

        public SortMode next() {
            return this == NAME ? AMOUNT : NAME;
        }
    }

    public enum SortDirection {

        ASCENDING,
        DESCENDING;

        public SortDirection next() {
            return this == ASCENDING ? DESCENDING : ASCENDING;
        }
    }

    public enum FilterMode {

        BOTH,
        STORED,
        CRAFTABLE;

        public FilterMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }
}
