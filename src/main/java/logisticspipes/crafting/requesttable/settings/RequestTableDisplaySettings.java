package logisticspipes.crafting.requesttable.settings;

import net.minecraft.nbt.NBTTagCompound;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;
import lombok.With;

/**
 * Immutable terminal preferences for one player, combining table-local display and player-wide order settings.
 */
@Value
@AllArgsConstructor
@With
public class RequestTableDisplaySettings {

    public static final RequestTableDisplaySettings DEFAULT = new RequestTableDisplaySettings(
            SortMode.NAME,
            SortDirection.ASCENDING,
            FilterMode.BOTH);
    private static final String NBT_SORT_MODE = "sortMode";
    private static final String NBT_SORT_DIRECTION = "sortDirection";
    private static final String NBT_FILTER_MODE = "filterMode";
    private static final String NBT_REQUEST_MESSAGES = "requestMessages";
    private static final String NBT_SEARCH_BOX_MODE = "searchBoxMode";
    private static final String NBT_SAVE_SEARCH = "saveSearch";
    private static final String NBT_SAVED_SEARCH = "savedSearch";
    private static final String NBT_TERMINAL_STYLE = "terminalStyle";
    private static final String NBT_SHOW_ITEMS = "showItems";
    private static final String NBT_SHOW_FLUIDS = "showFluids";
    private static final String NBT_ORDER_SETTINGS = "orderSettings";
    private static final int MAX_SEARCH_LENGTH = 256;
    SortMode sortMode;
    SortDirection sortDirection;
    FilterMode filterMode;
    boolean requestMessagesEnabled;
    SearchBoxMode searchBoxMode;
    boolean saveSearch;
    String savedSearchText;
    TerminalStyle terminalStyle;
    boolean showItems;
    boolean showFluids;
    RequestTableOrderSettings orderSettings;

    public RequestTableDisplaySettings(SortMode sortMode, SortDirection sortDirection, FilterMode filterMode) {
        this(sortMode, sortDirection, filterMode, true);
    }

    public RequestTableDisplaySettings(SortMode sortMode, SortDirection sortDirection, FilterMode filterMode,
            boolean requestMessagesEnabled) {
        this(
                sortMode,
                sortDirection,
                filterMode,
                requestMessagesEnabled,
                SearchBoxMode.STANDARD,
                false,
                "",
                TerminalStyle.SMALL,
                true,
                true,
                RequestTableOrderSettings.DEFAULT);
    }

    public static RequestTableDisplaySettings readFromNBT(NBTTagCompound tag) {
        if (tag == null) {
            return DEFAULT;
        }
        RequestTableDisplaySettings settings = fromOrdinals(
                tag.getInteger(NBT_SORT_MODE),
                tag.getInteger(NBT_SORT_DIRECTION),
                tag.getInteger(NBT_FILTER_MODE),
                !tag.hasKey(NBT_REQUEST_MESSAGES) || tag.getBoolean(NBT_REQUEST_MESSAGES))
                        .withSearchBoxMode(
                                valueOrDefault(
                                        SearchBoxMode.values(),
                                        tag.getInteger(NBT_SEARCH_BOX_MODE),
                                        DEFAULT.searchBoxMode))
                        .withSaveSearch(tag.getBoolean(NBT_SAVE_SEARCH))
                        .withTerminalStyle(
                                valueOrDefault(
                                        TerminalStyle.values(),
                                        tag.getInteger(NBT_TERMINAL_STYLE),
                                        DEFAULT.terminalStyle))
                        .withShowItems(!tag.hasKey(NBT_SHOW_ITEMS) || tag.getBoolean(NBT_SHOW_ITEMS))
                        .withShowFluids(!tag.hasKey(NBT_SHOW_FLUIDS) || tag.getBoolean(NBT_SHOW_FLUIDS))
                        .withOrderSettings(
                                RequestTableOrderSettings.readFromNBT(tag.getCompoundTag(NBT_ORDER_SETTINGS)));
        return settings.rememberSearch(tag.getString(NBT_SAVED_SEARCH));
    }

    public static RequestTableDisplaySettings fromOrdinals(int sortMode, int sortDirection, int filterMode,
            boolean requestMessagesEnabled) {
        return new RequestTableDisplaySettings(
                valueOrDefault(SortMode.values(), sortMode, DEFAULT.sortMode),
                valueOrDefault(SortDirection.values(), sortDirection, DEFAULT.sortDirection),
                valueOrDefault(FilterMode.values(), filterMode, DEFAULT.filterMode),
                requestMessagesEnabled);
    }

    private static <T> T valueOrDefault(T[] values, int ordinal, T defaultValue) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : defaultValue;
    }

    public RequestTableDisplaySettings nextSortMode() {
        return withSortMode(sortMode.next());
    }

    public RequestTableDisplaySettings nextSortDirection() {
        return withSortDirection(sortDirection.next());
    }

    public RequestTableDisplaySettings nextFilterMode() {
        return withFilterMode(filterMode.next());
    }

    public RequestTableDisplaySettings toggleRequestMessages() {
        return withRequestMessagesEnabled(!requestMessagesEnabled);
    }

    public RequestTableDisplaySettings nextSearchBoxMode() {
        return withSearchBoxMode(searchBoxMode.next());
    }

    public RequestTableDisplaySettings toggleSaveSearch() {
        return withSaveSearch(!saveSearch);
    }

    public RequestTableDisplaySettings nextTerminalStyle() {
        return withTerminalStyle(terminalStyle.next());
    }

    public RequestTableDisplaySettings toggleItems() {
        return withShowItems(!showItems);
    }

    public RequestTableDisplaySettings toggleFluids() {
        return withShowFluids(!showFluids);
    }

    /** Saves at most one NEI search field's worth of text, only when remembering search is enabled. */
    public RequestTableDisplaySettings rememberSearch(String text) {
        String saved = saveSearch && text != null ? text.substring(0, Math.min(MAX_SEARCH_LENGTH, text.length())) : "";
        return withSavedSearchText(saved);
    }

    public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger(NBT_SORT_MODE, sortMode.ordinal());
        tag.setInteger(NBT_SORT_DIRECTION, sortDirection.ordinal());
        tag.setInteger(NBT_FILTER_MODE, filterMode.ordinal());
        tag.setBoolean(NBT_REQUEST_MESSAGES, requestMessagesEnabled);
        tag.setInteger(NBT_SEARCH_BOX_MODE, searchBoxMode.ordinal());
        tag.setBoolean(NBT_SAVE_SEARCH, saveSearch);
        tag.setString(NBT_SAVED_SEARCH, savedSearchText);
        tag.setInteger(NBT_TERMINAL_STYLE, terminalStyle.ordinal());
        tag.setBoolean(NBT_SHOW_ITEMS, showItems);
        tag.setBoolean(NBT_SHOW_FLUIDS, showFluids);
        NBTTagCompound orderTag = new NBTTagCompound();
        orderSettings.writeToNBT(orderTag);
        tag.setTag(NBT_ORDER_SETTINGS, orderTag);
    }

    @Getter
    public enum SearchBoxMode {

        STANDARD("Standard", false, false),
        AUTO("Auto", true, false),
        NEI_SYNC_AUTO("NEI synced auto", true, true),
        NEI_SYNC_STANDARD("NEI synced standard", false, true);

        private final String label;
        private final boolean autoFocus;
        private final boolean neiSynced;

        SearchBoxMode(String label, boolean autoFocus, boolean neiSynced) {
            this.label = label;
            this.autoFocus = autoFocus;
            this.neiSynced = neiSynced;
        }

        public SearchBoxMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public enum TerminalStyle {

        SMALL,
        TALL;

        public TerminalStyle next() {
            return this == SMALL ? TALL : SMALL;
        }
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
