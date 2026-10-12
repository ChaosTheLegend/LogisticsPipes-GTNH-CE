package logisticspipes.crafting.requesttable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.utils.item.ItemIdentifier;

/**
 * Cached presentation model for the request table's network entries.
 * <p>
 * Localized names are resolved once per received entry. Sorting is repeated only when entries or sort settings change,
 * while filtering is repeated only when entries, filter settings or the search query change.
 */
public class RequestTableNetworkList {

    private final List<CachedEntry> sourceEntries = new ArrayList<>();
    private final List<CachedEntry> sortedEntries = new ArrayList<>();
    private final Map<ItemIdentifier, RequestTableNetworkEntry> knownItems = new HashMap<>();
    // sort name, mod name, searchable name and searchable mod of the items seen so far; working them out is slow for some items
    private final Map<ItemIdentifier, String[]> names = new HashMap<>();
    private static final int MAX_CACHED_NAMES = 50000;
    // a word of the search: it ends at a space, but not at one between quotes. The last quote may be left open.
    public static final Pattern SEARCH_WORD = Pattern.compile("(?:\"[^\"]*\"?|[^ \"])+");
    private final List<RequestTableNetworkEntry> visibleEntries = new ArrayList<>();
    private final List<RequestTableNetworkEntry> visibleEntriesView = Collections.unmodifiableList(visibleEntries);

    private RequestTableDisplaySettings settings = RequestTableDisplaySettings.DEFAULT;
    private String rawSearch = "";
    private String search = "";
    private String[] searchTokens = new String[0];
    // the words of the search that start with @, they search the mod
    private String[] modTokens = new String[0];

    public void setEntries(List<RequestTableNetworkEntry> entries) {
        sourceEntries.clear();
        knownItems.clear();
        if (names.size() > MAX_CACHED_NAMES) {
            names.clear();
        }
        for (RequestTableNetworkEntry entry : entries) {
            sourceEntries.add(
                    new CachedEntry(
                            entry,
                            names.computeIfAbsent(
                                    entry.getStack().getItem(),
                                    item -> { return CachedEntry.resolveNames(entry); })));
            if (!entry.isFluid()) {
                knownItems.put(entry.getStack().getItem(), entry);
            }
        }
        rebuildSortedEntries();
        rebuildVisibleEntries();
    }

    public boolean setDisplaySettings(RequestTableDisplaySettings newSettings) {
        if (settings.equals(newSettings)) {
            return false;
        }
        boolean sortingChanged = settings.getSortMode() != newSettings.getSortMode()
                || settings.getSortDirection() != newSettings.getSortDirection();
        boolean listChanged = sortingChanged || settings.getFilterMode() != newSettings.getFilterMode()
                || settings.getTypeFilter() != newSettings.getTypeFilter();
        settings = newSettings;
        if (sortingChanged) {
            rebuildSortedEntries();
        }
        if (!listChanged) {
            // only the look or the click behaviour changed, the list stays
            return false;
        }
        rebuildVisibleEntries();
        return true;
    }

    public boolean setSearch(String newSearch) {
        String nonNullSearch = newSearch == null ? "" : newSearch;
        if (Objects.equals(rawSearch, nonNullSearch)) {
            return false;
        }
        rawSearch = nonNullSearch;
        String normalized = nonNullSearch.trim().toLowerCase(Locale.US);
        if (search.equals(normalized)) {
            return false;
        }
        search = normalized;
        parseSearch(search);
        rebuildVisibleEntries();
        return true;
    }

    /**
     * Splits the search text into words. A word that starts with {@code @} searches the mod of an entry, the others
     * its name. Quotes keep the spaces between them in the word: {@code @"applied energistics"}.
     */
    private void parseSearch(String text) {
        List<String> nameWords = new ArrayList<>();
        List<String> modWords = new ArrayList<>();
        Matcher words = SEARCH_WORD.matcher(text);
        while (words.find()) {
            String word = words.group();
            boolean mod = word.startsWith("@");
            word = word.substring(mod ? 1 : 0).replace("\"", "");
            if (!word.isEmpty()) {
                (mod ? modWords : nameWords).add(word);
            }
        }
        searchTokens = nameWords.toArray(new String[0]);
        modTokens = modWords.toArray(new String[0]);
    }

    /**
     * @return true if the item is in the list, whatever the filters show: stored in the network or the table, or
     *         craftable
     */
    public boolean isKnown(ItemIdentifier item) {
        return knownItems.containsKey(item);
    }

    /**
     * @return the entry of that item with its amounts, or null if the list doesn't have it
     */
    public RequestTableNetworkEntry getEntry(ItemIdentifier item) {
        return knownItems.get(item);
    }

    public List<RequestTableNetworkEntry> getVisibleEntries() {
        return visibleEntriesView;
    }

    private void rebuildSortedEntries() {
        sortedEntries.clear();
        sortedEntries.addAll(sourceEntries);
        Comparator<CachedEntry> nameComparator = Comparator.comparing(entry -> entry.sortName);
        Comparator<CachedEntry> comparator = nameComparator;
        if (settings.getSortMode() == RequestTableDisplaySettings.SortMode.AMOUNT) {
            comparator = Comparator.comparingInt((CachedEntry entry) -> entry.entry.getTotalAmount())
                    .thenComparing(nameComparator);
        } else if (settings.getSortMode() == RequestTableDisplaySettings.SortMode.MOD) {
            comparator = Comparator.comparing((CachedEntry entry) -> entry.modName).thenComparing(nameComparator);
        }
        comparator = comparator.thenComparing(entry -> entry.entry);
        if (settings.getSortDirection() == RequestTableDisplaySettings.SortDirection.DESCENDING) {
            comparator = comparator.reversed();
        }
        sortedEntries.sort(comparator);
    }

    private void rebuildVisibleEntries() {
        visibleEntries.clear();
        for (CachedEntry entry : sortedEntries) {
            if (isVisibleForFilter(entry.entry) && entry.matches(searchTokens, modTokens)) {
                visibleEntries.add(entry.entry);
            }
        }
    }

    private boolean isVisibleForFilter(RequestTableNetworkEntry entry) {
        switch (settings.getTypeFilter()) {
            case ITEMS:
                if (entry.isFluid()) {
                    return false;
                }
                break;
            case FLUIDS:
                if (!entry.isFluid()) {
                    return false;
                }
                break;
            default:
                break;
        }
        switch (settings.getFilterMode()) {
            case STORED:
                return entry.isStored();
            case CRAFTABLE:
                return entry.isCraftable();
            case BOTH:
            default:
                return true;
        }
    }

    private static class CachedEntry {

        private final RequestTableNetworkEntry entry;
        private final String sortName;
        private final String modName;
        private final String searchableName;
        // the id and the name of the mod
        private final String searchableMod;

        private CachedEntry(RequestTableNetworkEntry entry, String[] names) {
            this.entry = entry;
            sortName = names[0];
            modName = names[1];
            searchableName = names[2];
            searchableMod = names[3];
        }

        /**
         * @return sort name, mod name, searchable name and searchable mod of the entry
         */
        private static String[] resolveNames(RequestTableNetworkEntry entry) {
            ItemStack stack = entry.getStack().unsafeMakeNormalStack();
            String displayName = stack.getDisplayName();
            if (entry.isFluid()) {
                FluidStack fluid = SimpleServiceLocator.logisticsFluidManager.getFluidFromContainer(entry.getStack());
                if (fluid != null) {
                    displayName = fluid.getLocalizedName();
                }
            }
            String sortName = displayName.toLowerCase(Locale.US);
            // fluids go behind the items of all mods
            String modName = entry.isFluid() ? "\uffff"
                    : entry.getStack().getItem().getModName().toLowerCase(Locale.US);
            String searchableName = (displayName + " "
                    + stack.getDisplayName()
                    + " "
                    + entry.getStack().getItem().getFriendlyName()).toLowerCase(Locale.US);
            // a fluid belongs to no mod the search could find it by
            String searchableMod = "";
            if (!entry.isFluid()) {
                String modId = entry.getStack().getItem().getModName();
                ModContainer mod = Loader.instance().getIndexedModList().get(modId);
                searchableMod = (mod == null ? modId : modId + " " + mod.getName()).toLowerCase(Locale.US);
            }
            return new String[] { sortName, modName, searchableName, searchableMod };
        }

        private boolean matches(String[] tokens, String[] modTokens) {
            for (String token : tokens) {
                if (!searchableName.contains(token)) {
                    return false;
                }
            }
            for (String token : modTokens) {
                if (!searchableMod.contains(token)) {
                    return false;
                }
            }
            return true;
        }
    }
}
