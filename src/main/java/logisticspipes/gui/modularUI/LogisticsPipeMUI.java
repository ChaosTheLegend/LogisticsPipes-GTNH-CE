package logisticspipes.gui.modularUI;

import logisticspipes.pipes.basic.CoreRoutedPipe;

public abstract class LogisticsPipeMUI extends LogisticsModularUI {

    protected final CoreRoutedPipe pipe;

    public CoreRoutedPipe getPipe() {
        return pipe;
    }

    public LogisticsPipeMUI(CoreRoutedPipe pipe, String prefix) {
        super(prefix);
        this.pipe = pipe;
    }

    public LogisticsPipeMUI(CoreRoutedPipe pipe) {
        this(pipe, "");
    }
}
