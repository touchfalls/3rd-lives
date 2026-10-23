// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) <FILL IN: year> <FILL IN: author>

package io.github.lifesmp.core.ports;

import io.github.lifesmp.core.domain.model.Arena;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Port interface for persistent storage and retrieval of {@link Arena} configurations.
 */
public interface ArenaRepository {

    /**
     * Loads all configured arenas from persistent storage.
     *
     * @return CompletableFuture completing with the list of loaded arenas
     */
    CompletableFuture<List<Arena>> loadAllArenas();

    /**
     * Persists or updates an arena.
     *
     * @param arena arena to persist
     * @return CompletableFuture completing when the operation finishes
     */
    CompletableFuture<Void> saveArena(@NotNull Arena arena);

    /**
     * Deletes an arena by its unique name.
     *
     * @param name unique name of the arena to delete
     * @return CompletableFuture completing when the deletion finishes
     */
    CompletableFuture<Void> deleteArena(@NotNull String name);
}
