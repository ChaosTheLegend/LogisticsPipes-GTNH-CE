package logisticspipes.gui.modularUI.pipes.requesttable;

import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import logisticspipes.crafting.requesttable.RequestTableInteractions;
import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.crafting.requesttable.RequestTableView;
import lombok.Getter;

/**
 * Container of the ModularUI request table.
 * <p>
 * Table storage is not made of slots: compressed stacks exceed what vanilla slot packets can carry. Shift-clicks from
 * the player inventory are therefore routed into the table storage here, following the active view, while the upgrade
 * view uses the normal slot-group routing into its material slots.
 */
public class RequestTableMuiContainer extends ModularContainer {

    @Getter
    private final RequestTablePipe table;
    private final Supplier<RequestTableView> view;

    public RequestTableMuiContainer(RequestTablePipe table, Supplier<RequestTableView> view) {
        this.table = table;
        this.view = view;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        ModularSlot slot = getModularSlot(index);
        RequestTableView current = view.get();
        if (ModularSlot.getPlayerSlotPlayer(slot) == null || current == RequestTableView.UPGRADES) {
            return super.transferStackInSlot(player, index);
        }
        // The server owns the table storage; the client waits for the resulting slot updates.
        if (isClient() || !slot.getHasStack() || !slot.canTakeStack(player)) {
            return null;
        }
        return RequestTableInteractions.transferPlayerStack(table, player, slot, current);
    }
}
