package logisticspipes.crafting.requesttable;

import java.util.Arrays;

import net.minecraft.nbt.NBTTagCompound;

import lombok.Value;
import lombok.With;

/**
 * Immutable sort, filter and behaviour state for one player at one request table.
 */
@Value
@With
public class RequestTableDisplaySettings {

    public static final RequestTableDisplaySettings DEFAULT = new RequestTableDisplaySettings(
            SortMode.NAME,
            SortDirection.ASCENDING,
            FilterMode.BOTH,
            TypeFilter.ALL,
            FluidUnit.MILLIBUCKETS,
            ClickMode.TAKE,
            DropMode.TABLE,
            DeliveryMode.TABLE,
            SearchMode.STANDARD,
            SearchMemory.FORGET,
            PopupMessages.ALWAYS,
            ChatMessages.OFF,
            "");
    public static final int MAX_SEARCH_LENGTH = 64;
    public static final int ORDINAL_COUNT = 12;
    private static final String NBT_SORT_MODE = "sortMode";
    private static final String NBT_SORT_DIRECTION = "sortDirection";
    private static final String NBT_FILTER_MODE = "filterMode";
    private static final String NBT_TYPE_FILTER = "typeFilter";
    private static final String NBT_FLUID_UNIT = "fluidUnit";
    private static final String NBT_CLICK_MODE = "clickMode";
    private static final String NBT_DROP_MODE = "dropMode";
    private static final String NBT_DELIVERY_MODE = "deliveryMode";
    private static final String NBT_SEARCH_MODE = "searchMode";
    private static final String NBT_SEARCH_MEMORY = "searchMemory";
    private static final String NBT_POPUP_MESSAGES = "popupMessages";
    private static final String NBT_CHAT_MESSAGES = "chatMessages";
    private static final String NBT_SAVED_SEARCH = "savedSearch";
    SortMode sortMode;
    SortDirection sortDirection;
    FilterMode filterMode;
    TypeFilter typeFilter;
    FluidUnit fluidUnit;
    ClickMode clickMode;
    DropMode dropMode;
    DeliveryMode deliveryMode;
    SearchMode searchMode;
    SearchMemory searchMemory;
    PopupMessages popupMessages;
    ChatMessages chatMessages;
    /** what the search field held last, kept only with {@link SearchMemory#REMEMBER} */
    String savedSearch;

    /**
     * Tags written before the newer settings existed read them as 0, their default.
     */
    public static RequestTableDisplaySettings readFromNBT(NBTTagCompound tag) {
        return fromOrdinals(
                tag.getInteger(NBT_SORT_MODE),
                tag.getInteger(NBT_SORT_DIRECTION),
                tag.getInteger(NBT_FILTER_MODE),
                tag.getInteger(NBT_TYPE_FILTER),
                tag.getInteger(NBT_FLUID_UNIT),
                tag.getInteger(NBT_CLICK_MODE),
                tag.getInteger(NBT_DROP_MODE),
                tag.getInteger(NBT_DELIVERY_MODE),
                tag.getInteger(NBT_SEARCH_MODE),
                tag.getInteger(NBT_SEARCH_MEMORY),
                tag.getInteger(NBT_POPUP_MESSAGES),
                tag.getInteger(NBT_CHAT_MESSAGES)).withSavedSearch(limitSearch(tag.getString(NBT_SAVED_SEARCH)));
    }

    public static String limitSearch(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > MAX_SEARCH_LENGTH ? text.substring(0, MAX_SEARCH_LENGTH) : text;
    }

    /**
     * The settings of the legacy GUI, the others stay at their default.
     */
    public static RequestTableDisplaySettings fromOrdinals(int sortMode, int sortDirection, int filterMode) {
        return fromOrdinals(new int[] { sortMode, sortDirection, filterMode });
    }

    /**
     * @param ordinals the ordinals of the settings in the order of {@link #toOrdinals}, missing ones are 0; the search
     *                 text isn't one of them and stays empty
     */
    public static RequestTableDisplaySettings fromOrdinals(int[] ordinals) {
        int[] o = Arrays.copyOf(ordinals, ORDINAL_COUNT);
        return fromOrdinals(o[0], o[1], o[2], o[3], o[4], o[5], o[6], o[7], o[8], o[9], o[10], o[11]);
    }

    private static RequestTableDisplaySettings fromOrdinals(int sortMode, int sortDirection, int filterMode,
            int typeFilter, int fluidUnit, int clickMode, int dropMode, int deliveryMode, int searchMode,
            int searchMemory, int popupMessages, int chatMessages) {
        return new RequestTableDisplaySettings(
                valueOrDefault(SortMode.values(), sortMode, DEFAULT.sortMode),
                valueOrDefault(SortDirection.values(), sortDirection, DEFAULT.sortDirection),
                valueOrDefault(FilterMode.values(), filterMode, DEFAULT.filterMode),
                valueOrDefault(TypeFilter.values(), typeFilter, DEFAULT.typeFilter),
                valueOrDefault(FluidUnit.values(), fluidUnit, DEFAULT.fluidUnit),
                valueOrDefault(ClickMode.values(), clickMode, DEFAULT.clickMode),
                valueOrDefault(DropMode.values(), dropMode, DEFAULT.dropMode),
                valueOrDefault(DeliveryMode.values(), deliveryMode, DEFAULT.deliveryMode),
                valueOrDefault(SearchMode.values(), searchMode, DEFAULT.searchMode),
                valueOrDefault(SearchMemory.values(), searchMemory, DEFAULT.searchMemory),
                valueOrDefault(PopupMessages.values(), popupMessages, DEFAULT.popupMessages),
                valueOrDefault(ChatMessages.values(), chatMessages, DEFAULT.chatMessages),
                "");
    }

    /**
     * @return the ordinals of all settings but the search text, {@link #ORDINAL_COUNT} of them
     */
    public int[] toOrdinals() {
        return new int[] { sortMode.ordinal(), sortDirection.ordinal(), filterMode.ordinal(), typeFilter.ordinal(),
                fluidUnit.ordinal(), clickMode.ordinal(), dropMode.ordinal(), deliveryMode.ordinal(),
                searchMode.ordinal(), searchMemory.ordinal(), popupMessages.ordinal(), chatMessages.ordinal() };
    }

    private static <T> T valueOrDefault(T[] values, int ordinal, T defaultValue) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : defaultValue;
    }

    private static <T extends Enum<T>> T next(T[] values, T current) {
        return values[(current.ordinal() + 1) % values.length];
    }

    public RequestTableDisplaySettings nextSortMode() {
        return withSortMode(next(SortMode.values(), sortMode));
    }

    public RequestTableDisplaySettings nextSortDirection() {
        return withSortDirection(next(SortDirection.values(), sortDirection));
    }

    public RequestTableDisplaySettings nextFilterMode() {
        return withFilterMode(next(FilterMode.values(), filterMode));
    }

    public RequestTableDisplaySettings nextTypeFilter() {
        return withTypeFilter(next(TypeFilter.values(), typeFilter));
    }

    public RequestTableDisplaySettings nextFluidUnit() {
        return withFluidUnit(next(FluidUnit.values(), fluidUnit));
    }

    public RequestTableDisplaySettings nextClickMode() {
        return withClickMode(next(ClickMode.values(), clickMode));
    }

    public RequestTableDisplaySettings nextDropMode() {
        return withDropMode(next(DropMode.values(), dropMode));
    }

    public RequestTableDisplaySettings nextDeliveryMode() {
        return withDeliveryMode(next(DeliveryMode.values(), deliveryMode));
    }

    public RequestTableDisplaySettings nextSearchMode() {
        return withSearchMode(next(SearchMode.values(), searchMode));
    }

    public RequestTableDisplaySettings nextSearchMemory() {
        return withSearchMemory(next(SearchMemory.values(), searchMemory));
    }

    public RequestTableDisplaySettings nextPopupMessages() {
        return withPopupMessages(next(PopupMessages.values(), popupMessages));
    }

    public RequestTableDisplaySettings nextChatMessages() {
        return withChatMessages(next(ChatMessages.values(), chatMessages));
    }

    /**
     * @return whether the result of a request is reported to the player anywhere
     */
    public boolean reportsRequests() {
        return popupMessages != PopupMessages.OFF || chatMessages != ChatMessages.OFF;
    }

    public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger(NBT_SORT_MODE, sortMode.ordinal());
        tag.setInteger(NBT_SORT_DIRECTION, sortDirection.ordinal());
        tag.setInteger(NBT_FILTER_MODE, filterMode.ordinal());
        tag.setInteger(NBT_TYPE_FILTER, typeFilter.ordinal());
        tag.setInteger(NBT_FLUID_UNIT, fluidUnit.ordinal());
        tag.setInteger(NBT_CLICK_MODE, clickMode.ordinal());
        tag.setInteger(NBT_DROP_MODE, dropMode.ordinal());
        tag.setInteger(NBT_DELIVERY_MODE, deliveryMode.ordinal());
        tag.setInteger(NBT_SEARCH_MODE, searchMode.ordinal());
        tag.setInteger(NBT_SEARCH_MEMORY, searchMemory.ordinal());
        tag.setInteger(NBT_POPUP_MESSAGES, popupMessages.ordinal());
        tag.setInteger(NBT_CHAT_MESSAGES, chatMessages.ordinal());
        tag.setString(NBT_SAVED_SEARCH, savedSearch);
    }

    public enum SortMode {
        NAME,
        AMOUNT,
        MOD
    }

    public enum SortDirection {
        ASCENDING,
        DESCENDING
    }

    public enum FilterMode {
        BOTH,
        STORED,
        CRAFTABLE
    }

    /**
     * Which kind of entries the list shows.
     */
    public enum TypeFilter {
        ALL,
        ITEMS,
        FLUIDS
    }

    /**
     * The unit fluid amounts are shown in.
     */
    public enum FluidUnit {
        MILLIBUCKETS,
        BUCKETS
    }

    /**
     * What a click on an entry does while the cursor is empty.
     */
    public enum ClickMode {
        /** take the item out of the storage of the table */
        TAKE,
        /** request the item from the network */
        REQUEST
    }

    /**
     * Where an item goes that the player drops on the list or shift clicks in the inventory.
     */
    public enum DropMode {
        /** into the storage of the table */
        TABLE,
        /** straight into the network, into the table only if the network doesn't take it */
        NETWORK
    }

    /**
     * Where the items go that the player requested.
     */
    public enum DeliveryMode {
        /** into the storage of the table */
        TABLE,
        /** into the inventory of the player, as long as the GUI of the table is open */
        PLAYER
    }

    /**
     * How the search field behaves.
     */
    public enum SearchMode {

        /** takes the keyboard when it is clicked */
        STANDARD(false, false),
        /** takes the keyboard as soon as the window opens */
        AUTO(true, false),
        /** like {@link #AUTO}, and what is typed is searched in NEI too */
        NEI_AUTO(true, true),
        /** like {@link #STANDARD}, and what is typed is searched in NEI too */
        NEI_STANDARD(false, true);

        public final boolean autoFocus;
        public final boolean neiSynced;

        SearchMode(boolean autoFocus, boolean neiSynced) {
            this.autoFocus = autoFocus;
            this.neiSynced = neiSynced;
        }
    }

    /**
     * Whether the search text is still there when the window is opened again.
     */
    public enum SearchMemory {
        FORGET,
        REMEMBER
    }

    /**
     * Whether a small window over the table reports the result of a request.
     */
    public enum PopupMessages {

        ALWAYS,
        /** only when something is missing or the network has no place for something */
        PROBLEMS,
        OFF;

        public boolean shows(boolean problem) {
            return this == ALWAYS || (this == PROBLEMS && problem);
        }
    }

    /**
     * Whether the chat reports the result of a request.
     */
    public enum ChatMessages {

        OFF,
        ALWAYS,
        /** only when something is missing or the network has no place for something */
        PROBLEMS;

        public boolean shows(boolean problem) {
            return this == ALWAYS || (this == PROBLEMS && problem);
        }
    }
}
