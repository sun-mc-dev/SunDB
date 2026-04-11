package me.sunmc.db.util;

import java.util.concurrent.Executor;

/**
 * @deprecated Use {@link PriorityExecutor} directly.
 * This class is kept for backward compatibility and delegates to
 * {@link PriorityExecutor#get(PriorityExecutor.Priority) NORMAL} priority.
 */
@Deprecated(since = "0.2-alpha", forRemoval = true)
public final class VirtualExecutor {

    private VirtualExecutor() {
    }

    /**
     * @deprecated Use {@link PriorityExecutor#get()}
     */
    @Deprecated
    public static Executor get() {
        return PriorityExecutor.get(PriorityExecutor.Priority.NORMAL);
    }
}