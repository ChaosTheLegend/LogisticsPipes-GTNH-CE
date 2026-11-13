package logisticspipes.gui.modularUI;

import java.util.function.Supplier;

import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;

import logisticspipes.modules.abstractmodules.LogisticsModule;

public abstract class GenericModuleMUI<T extends LogisticsModule> extends LogisticsModularUI {

    protected final T module;

    public GenericModuleMUI(T module) {
        this(module, "");
    }

    public GenericModuleMUI(T module, String prefix) {
        super(prefix);
        this.module = module;
    }

    /**
     * A sync handler registered with the window under its key (the one already there after a chassis page rebuild), or
     * a plain one when there is no sync manager.
     *
     * @param key the full key, unique for the window
     */
    public static <S extends SyncHandler<?>> S register(PanelSyncManager syncManager, String key, int index,
            Class<S> type, Supplier<S> create) {
        if (syncManager == null) {
            return create.get();
        }
        return syncManager.getOrCreateSyncHandler(key, index, type, create);
    }

    /**
     * {@link #register} under this window's full id followed by the key.
     */
    protected <S extends SyncHandler<?>> S sync(PanelSyncManager syncManager, String key, int index, Class<S> type,
            Supplier<S> create) {
        return register(syncManager, getFullId() + key, index, type, create);
    }

    protected <S extends SyncHandler<?>> S sync(PanelSyncManager syncManager, String key, Class<S> type,
            Supplier<S> create) {
        return sync(syncManager, key, 0, type, create);
    }
}
