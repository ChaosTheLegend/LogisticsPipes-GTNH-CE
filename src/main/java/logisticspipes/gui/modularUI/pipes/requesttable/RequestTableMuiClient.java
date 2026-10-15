package logisticspipes.gui.modularUI.pipes.requesttable;

import cpw.mods.fml.common.Loader;

/** NEI search hooks of the ModularUI request table; only called on the client. */
final class RequestTableMuiClient {

    private RequestTableMuiClient() {}

    private static boolean hasNei() {
        return Loader.isModLoaded("NotEnoughItems");
    }

    /** Keeps NEI's search field from taking the keyboard when the terminal search gets focus. */
    static void unfocusNeiSearch() {
        if (hasNei()) Nei.unfocus();
    }

    /** One-way search synchronization from the terminal to NEI. */
    static void setNeiSearch(String text) {
        if (hasNei()) Nei.setText(text);
    }

    private static final class Nei {

        private static void unfocus() {
            if (codechicken.nei.LayoutManager.searchField != null) {
                codechicken.nei.LayoutManager.searchField.setFocus(false);
            }
        }

        private static void setText(String text) {
            var field = codechicken.nei.LayoutManager.searchField;
            if (field != null && !text.equals(field.text())) field.setText(text);
        }
    }
}
