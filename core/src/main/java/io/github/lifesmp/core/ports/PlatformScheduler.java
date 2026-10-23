// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) <FILL IN: year> <FILL IN: author>

package io.github.lifesmp.core.ports;

import org.jetbrains.annotations.NotNull;

/**
 * Port interface for scheduling asynchronous and synchronous tasks on the platform.
 */
public interface PlatformScheduler {

    /**
     * Executes a task synchronously on the platform's main server thread.
     *
     * @param task runnable task to execute
     */
    void runSync(@NotNull Runnable task);

    /**
     * Executes a task asynchronously off the main thread.
     *
     * @param task runnable task to execute
     */
    void runAsync(@NotNull Runnable task);

    /**
     * Schedules a task to execute repeatedly on the platform's main thread.
     *
     * @param task        task to run
     * @param delayTicks  delay before first execution in Minecraft ticks (20 ticks = 1 second)
     * @param periodTicks interval between executions in Minecraft ticks
     * @return cancellable task handle
     */
    CancellableTask runRepeatingSync(@NotNull Runnable task, long delayTicks, long periodTicks);

    /**
     * Schedules a task to execute repeatedly on an asynchronous pool.
     *
     * @param task        task to run
     * @param delayTicks  delay before first execution in Minecraft ticks
     * @param periodTicks interval between executions in Minecraft ticks
     * @return cancellable task handle
     */
    CancellableTask runRepeatingAsync(@NotNull Runnable task, long delayTicks, long periodTicks);

    /**
     * Represents a scheduled repeating task that can be cancelled.
     */
    @FunctionalInterface
    interface CancellableTask {
        /**
         * Cancels the scheduled task.
         */
        void cancel();
    }
}
