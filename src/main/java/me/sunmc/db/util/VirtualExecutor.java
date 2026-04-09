package me.sunmc.db.util;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Java 21 virtual-thread executor — each DB task gets its own lightweight thread.
 * IO blocking is transparent; the carrier thread is never pinned.
 */
public final class VirtualExecutor {

    private static final Executor INSTANCE =
            Executors.newVirtualThreadPerTaskExecutor();

    public static Executor get() {
        return INSTANCE;
    }

    private VirtualExecutor() {
    }
}