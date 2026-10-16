package logisticspipes.gui.modularUI;

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
}
