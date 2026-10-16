package logisticspipes.gui.modularUI.requesttable;

import codechicken.nei.LayoutManager;

/**
 * Reaches the search field of NEI for the search field of the request table. Only to be touched while NEI is loaded.
 */
final class RequestTableNeiSearch {

    private RequestTableNeiSearch() {}

    /**
     * Lets NEI search for the text. It goes one way only: what is typed into NEI doesn't come back.
     */
    static void setText(String text) {
        if (LayoutManager.searchField != null && !text.equals(LayoutManager.searchField.text())) {
            LayoutManager.searchField.setText(text);
        }
    }

    static void unfocus() {
        if (LayoutManager.searchField != null) {
            LayoutManager.searchField.setFocus(false);
        }
    }
}
