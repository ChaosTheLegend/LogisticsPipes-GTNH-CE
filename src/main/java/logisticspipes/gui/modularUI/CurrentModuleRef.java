package logisticspipes.gui.modularUI;

import java.util.function.Function;

import net.minecraft.inventory.IInventory;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;

import logisticspipes.modules.abstractmodules.LogisticsModule;

/**
 * The module a module window's sync handlers work on, registered with them under the window's key; the latest build of
 * the window sets it.
 * <p>
 * A chassis rebuilds a module page when the module in its slot changes, and MUI hands the rebuilt page the sync
 * handlers registered for the old page (same keys). Handlers that kept the old module would show and change that one;
 * handlers that ask this reference follow the module that is there now.
 */
public final class CurrentModuleRef<T extends LogisticsModule> extends SyncHandler<CurrentModuleRef<T>> {

    private T module;

    /**
     * @param syncManager the window's sync manager, null for a window without one (a plain reference then)
     * @param key         unique for the window, e.g. its full id
     * @return the reference registered under the key, now holding the module
     */
    @SuppressWarnings("unchecked")
    public static <T extends LogisticsModule> CurrentModuleRef<T> of(PanelSyncManager syncManager, String key,
            T module) {
        CurrentModuleRef<T> ref = syncManager == null ? new CurrentModuleRef<>()
                : syncManager.getOrCreateSyncHandler(key + "_module", CurrentModuleRef.class, CurrentModuleRef::new);
        ref.module = module;
        return ref;
    }

    public T get() {
        return module;
    }

    /**
     * @return an inventory wrapper that always works on that inventory of the module held now
     */
    public InvWrapper inventory(Function<T, IInventory> inventory) {
        return new InvWrapper(inventory.apply(module)) {

            @Override
            public IInventory getInv() {
                return inventory.apply(module);
            }
        };
    }

    @Override
    public void readOnClient(int id, PacketBuffer buf) {}

    @Override
    public void readOnServer(int id, PacketBuffer buf) {}
}
