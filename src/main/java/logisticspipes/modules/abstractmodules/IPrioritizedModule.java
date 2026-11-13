package logisticspipes.modules.abstractmodules;

/**
 * A module with a player-set priority (-10 to 10, see {@link LogisticsModule#getPriority()}): sinks of the same kind
 * are chosen by it before distance, providers asked by it first. Its window shows the priority.
 */
public interface IPrioritizedModule {

    int getPriority();

    void setPriority(int priority);
}
