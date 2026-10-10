package logisticspipes.routing.astar;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Worker threads of the junction router (graph writer and background route refresh). {@code MainProxy} treats them as
 * server threads.
 */
public final class JunctionRoutingThread extends Thread {

    private JunctionRoutingThread(Runnable target, String name, int priority) {
        super(target, name);
        setDaemon(true);
        setPriority(priority);
    }

    public static ThreadFactory factory(String prefix, int priority) {
        AtomicInteger counter = new AtomicInteger();
        return r -> new JunctionRoutingThread(r, prefix + " #" + counter.incrementAndGet(), priority);
    }
}
