// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.paper.task;

import io.github.lifesmp.core.storage.LifeProfileCache;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Background task running periodically to persist dirty profiles into SQLite.
 */
public final class AutosaveTask implements Runnable {

    private final LifeProfileCache profileCache;

    public AutosaveTask(@NotNull LifeProfileCache profileCache) {
        this.profileCache = Objects.requireNonNull(profileCache, "profileCache cannot be null");
    }

    @Override
    public void run() {
        profileCache.flushDirtyProfiles();
    }
}
