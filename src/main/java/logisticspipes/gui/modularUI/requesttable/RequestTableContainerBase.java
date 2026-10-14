package logisticspipes.gui.modularUI.requesttable;

import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import logisticspipes.crafting.requesttable.RequestTableDisplaySettings;
import logisticspipes.crafting.requesttable.RequestTableDisplaySettings.ContainerMode;
import logisticspipes.crafting.requesttable.RequestTableDisplaySettings.DropMode;
import logisticspipes.crafting.requesttable.RequestTablePipe;
import logisticspipes.crafting.requesttable.RequestTableView;

/**
 * Container of the request table GUI on both sides. It sends a shift clicked stack of the player straight into the
 * network if the player chose that.
 */
public class RequestTableContainerBase extends ModularContainer {

    protected final RequestTableSyncHandler handler;
    private final RequestTablePipe table;

    public RequestTableContainerBase(RequestTableSyncHandler handler, RequestTablePipe table) {
        this.handler = handler;
        this.table = table;
    }

    /**
     * Container factory for {@code UISettings#customContainer}.
     */
    public static Supplier<ModularContainer> supplier(RequestTableSyncHandler handler, RequestTablePipe table) {
        return () -> new RequestTableContainerBase(handler, table);
    }

    /**
     * A shift click on containers with fluid in them empties them into the fluid storage, unless the player chose to
     * treat them as items. Each storage tab works only with its own storage: the fluid storage empties the containers
     * and leaves every other item where it is, the item storage puts the containers away like any other item.
     */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        ModularSlot slot = getModularSlot(index);
        RequestTableDisplaySettings settings = isClient() ? handler.getSettings() : table.getDisplaySettings(player);
        RequestTableView view = handler.getShownView();
        boolean fluidShown = view == RequestTableView.FLUID_STORAGE;
        boolean empty = fluidShown
                || (view != RequestTableView.ITEM_STORAGE && settings.getContainerMode() == ContainerMode.EMPTY);
        if (ModularSlot.isPlayerSlot(slot) && empty
                && slot.getSlotIndex() < player.inventory.mainInventory.length
                && table.isFilledFluidContainer(slot.getStack())) {
            if (!isClient()) {
                // changes the inventory of the player itself, the slots are reported back
                table.emptyFluidContainers(player, slot.getSlotIndex());
            }
            return null;
        }
        if (ModularSlot.isPlayerSlot(slot) && fluidShown) {
            // the fluid storage has no place for items, nor sends them into the network
            return null;
        }
        return super.transferStackInSlot(player, index);
    }

    @Override
    protected ItemStack transferItem(ModularSlot fromSlot, ItemStack fromStack) {
        if (!ModularSlot.isPlayerSlot(fromSlot)) {
            return super.transferItem(fromSlot, fromStack);
        }
        // with the item storage in view a shift click puts the item there, whatever the drop mode is
        boolean storageShown = handler.getShownView() == RequestTableView.ITEM_STORAGE;
        if (isClient()) {
            if (!storageShown && handler.getSettings().getDropMode() == DropMode.NETWORK) {
                // the server decides how much the network takes and reports the slots back
                return fromStack;
            }
            return super.transferItem(fromSlot, fromStack);
        }
        EntityPlayer player = getPlayer();
        RequestTableDisplaySettings settings = table.getDisplaySettings(player);
        if (!storageShown && settings.getDropMode() == DropMode.NETWORK) {
            fromStack.stackSize -= table.sendStackToNetwork(fromStack);
            if (fromStack.stackSize <= 0) {
                fromStack.stackSize = 0;
                return fromStack;
            }
            // the rest goes into the table, the player is told that the network didn't want it
            handler.reportUnsent(fromStack);
        }
        return super.transferItem(fromSlot, fromStack);
    }
}
