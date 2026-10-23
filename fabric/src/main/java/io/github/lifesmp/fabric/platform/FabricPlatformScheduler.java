// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) <FILL IN: year> <FILL IN: author>

package io.github.lifesmp.fabric.platform;

import io.github.lifesmp.core.ports.PlatformScheduler;
import io.github.lifesmp.core.util.NamedThreadFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Fabric implementation of {@link PlatformScheduler} scheduling sync tasks on the main
 * server thread via an {@link Executor} supplier and async tasks on isolated worker pools.
 */
public final class FabricPlatformScheduler implements PlatformScheduler, AutoCloseable {

    private final Supplier<Executor> serverExecutorSupplier;
    private final ExecutorService asyncExecutor;
    private final ScheduledExecutorService scheduledExecutor;

    public FabricPlatformScheduler(@NotNull Supplier<Executor> serverExecutorSupplier) {
        this.serverExecutorSupplier = Objects.requireNonNull(serverExecutorSupplier, "serverExecutorSupplier cannot be null");
        this.asyncExecutor = Executors.newFixedThreadPool(3, new NamedThreadFactory("lifesmp-fabric-async"));
        this.scheduledExecutor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("lifesmp-fabric-timer"));
    }

    @Override
    public void runSync(@NotNull Runnable task) {
        Executor serverExecutor = serverExecutorSupplier.get();
        if (serverExecutor != null) {
            serverExecutor.execute(task);
        } else {
            task.run();
        }
    }

    @Override
    public void runAsync(@NotNull Runnable task) {
        asyncExecutor.submit(task);
    }

    @Override
    public CancellableTask runRepeatingSync(@NotNull Runnable task, long delayTicks, long periodTicks) {
        long delayMs = Math.max(0L, delayTicks * 50L);
        long periodMs = Math.max(50L, periodTicks * 50L);

        ScheduledFuture<?> future = scheduledExecutor.scheduleAtFixedRate(() -> {
            Executor serverExecutor = serverExecutorSupplier.get();
            if (serverExecutor != null) {
                serverExecutor.execute(task);
            }
        }, delayMs, periodMs, TimeUnit.MILLISECONDS);

        return () -> future.cancel(false);
    }

    @Override
    public CancellableTask runRepeatingAsync(@NotNull Runnable task, long delayTicks, long periodTicks) {
        long delayMs = Math.max(0L, delayTicks * 50L);
        long periodMs = Math.max(50L, periodTicks * 50L);

        ScheduledFuture<?> future = scheduledExecutor.scheduleAtFixedRate(
                task, delayMs, periodMs, TimeUnit.MILLISECONDS
        );

        return () -> future.cancel(false);
    }

    @Override
    public void close() {
        scheduledExecutor.shutdown();
        asyncExecutor.shutdown();
    }
}
