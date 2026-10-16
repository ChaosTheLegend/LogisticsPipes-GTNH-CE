package logisticspipes.crafting.requesttable.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import logisticspipes.crafting.requesttable.network.RequestTableNetworkEntry;
import logisticspipes.crafting.requesttable.settings.RequestTableDisplaySettings;
import logisticspipes.proxy.SimpleServiceLocator;

/**
 * Cached presentation model for the request table's network entries.
 * <p>
 * Localized names are resolved once per received entry. Sorting is repeated only when entries or sort settings change,
 * while filtering is repeated only when entries, filter settings or the search query change.
 */
public class RequestTableNetworkList {

    private final List<CachedEntry> sourceEntries = new ArrayList<>();
    private final List<CachedEntry> sortedEntries = new ArrayList<>();
    private final List<RequestTableNetworkEntry> visibleEntries = new ArrayList<>();
    private final List<RequestTableNetworkEntry> visibleEntriesView = Collections.unmodifiableList(visibleEntries);

    private RequestTableDisplaySettings settings = RequestTableDisplaySettings.DEFAULT;
    private String rawSearch = "";
    private String search = "";
    private String[] searchTokens = new String[0];
    private boolean fluidsEnabled;

    public boolean setFluidsEnabled(boolean enabled) {
        if (fluidsEnabled == enabled) {
            return false;
        }
        fluidsEnabled = enabled;
        rebuildVisibleEntries();
        return true;
    }

    public void setEntries(List<RequestTableNetworkEntry> entries) {
        sourceEntries.clear();
        for (RequestTableNetworkEntry entry : entries) {
            sourceEntries.add(new CachedEntry(entry));
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
        boolean filterChanged = settings.getFilterMode() != newSettings.getFilterMode()
            || settings.isShowItems() != newSettings.isShowItems()
            || settings.isShowFluids() != newSettings.isShowFluids();
        settings = newSettings;
        if (!sortingChanged && !filterChanged) {
            return false;
        }
        if (sortingChanged) {
            rebuildSortedEntries();
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
        String normalized = nonNullSearch.trim().toLowerCase(Locale.ROOT);
        if (search.equals(normalized)) {
            return false;
        }
        search = normalized;
        searchTokens = search.isEmpty() ? new String[0] : search.split(" +");
        rebuildVisibleEntries();
        return true;
    }

    public List<RequestTableNetworkEntry> getVisibleEntries() {
        return visibleEntriesView;
    }

    private void rebuildSortedEntries() {
        sortedEntries.clear();
        sortedEntries.addAll(sourceEntries);
        Comparator<CachedEntry> nameComparator = Comparator.comparing(entry -> entry.sortName);
        Comparator<CachedEntry> comparator = switch (settings.getSortMode()) {
            case NAME -> nameComparator;
            case AMOUNT -> Comparator.comparingInt((CachedEntry entry) -> entry.entry.getTotalAmount())
                .thenComparing(nameComparator);
        };
        comparator = comparator.thenComparing(entry -> entry.entry);
        if (settings.getSortDirection() == RequestTableDisplaySettings.SortDirection.DESCENDING) {
            comparator = comparator.reversed();
        }
        sortedEntries.sort(comparator);
    }

    private void rebuildVisibleEntries() {
        visibleEntries.clear();
        for (CachedEntry entry : sortedEntries) {
            if (isVisibleForFilter(entry.entry) && entry.matches(searchTokens)) {
                visibleEntries.add(entry.entry);
            }
        }
    }

    private boolean isVisibleForFilter(RequestTableNetworkEntry entry) {
        if (entry.fluid() ? !fluidsEnabled || !settings.isShowFluids() : !settings.isShowItems()) {
            return false;
        }
        return switch (settings.getFilterMode()) {
            case STORED -> entry.isStored();
            case CRAFTABLE -> entry.craftable();
            case BOTH -> true;
        };
    }

    private static final class CachedEntry {

        private final RequestTableNetworkEntry entry;
        private final String sortName;
        private final String searchableName;

        private CachedEntry(RequestTableNetworkEntry entry) {
            this.entry = entry;
            ItemStack stack = entry.stack().unsafeMakeNormalStack();
            String friendlyName = entry.stack().getItem().getFriendlyName();
            String itemName;
            try {
                itemName = stack.getDisplayName();
            } catch (RuntimeException ignored) {
                // Some modded variants have broken name providers (for example GregTech facades).
                itemName = friendlyName;
            }
            String displayName = itemName;
            if (entry.fluid()) {
                FluidStack fluid = SimpleServiceLocator.logisticsFluidManager.getFluidFromContainer(entry.stack());
                if (fluid != null) {
                    displayName = fluid.getLocalizedName();
                }
            }
            sortName = displayName.toLowerCase(Locale.ROOT);
            searchableName = (displayName + " " + itemName + " " + friendlyName).toLowerCase(Locale.ROOT);
        }

        private boolean matches(String[] tokens) {
            for (String token : tokens) {
                if (!searchableName.contains(token)) {
                    return false;
                }
            }
            return true;
        }
    }
}
