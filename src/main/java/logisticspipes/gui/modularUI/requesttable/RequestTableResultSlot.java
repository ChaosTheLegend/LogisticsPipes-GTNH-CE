package logisticspipes.gui.modularUI.requesttable;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;

/**
 * Shows what the crafting grid of the request table makes. A click crafts it from the storage of the table and the
 * inventory of the player; nothing can be put into or taken out of the slot itself.
 */
public class RequestTableResultSlot extends ItemSlot {

    private final RequestTableSyncHandler handler;

    public RequestTableResultSlot(RequestTableSyncHandler handler) {
        this.handler = handler;
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        if (mouseButton == 0 || mouseButton == 1) {
            handler.craft(Interactable.hasShiftDown());
        }
        return Result.SUCCESS;
    }

    @Override
    public boolean onMouseRelease(int mouseButton) {
        return true;
    }

    @Override
    public void onMouseDrag(int mouseButton, long timeSinceClick) {}
}
