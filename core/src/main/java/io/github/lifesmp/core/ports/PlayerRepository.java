// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) <FILL IN: year> <FILL IN: author>

package io.github.lifesmp.core.ports;

import io.github.lifesmp.core.domain.model.LifeProfile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Port interface for persistent storage and retrieval of {@link LifeProfile} records.
 * All I/O operations must be non-blocking and return {@link CompletableFuture}.
 */
public interface PlayerRepository {

    /**
     * Loads a player's life profile by their unique UUID.
     *
     * @param uuid unique identifier of the player
     * @return CompletableFuture completing with the optional profile, or empty if not found
     */
    CompletableFuture<Optional<LifeProfile>> loadPlayer(@NotNull UUID uuid);

    /**
     * Persists or updates a single player's life profile.
     *
     * @param profile profile to persist
     * @return CompletableFuture completing when the write finishes
     */
    CompletableFuture<Void> savePlayer(@NotNull LifeProfile profile);

    /**
     * Persists or updates a batch collection of player profiles in a single transactional batch.
     *
     * @param profiles collection of profiles to persist
     * @return CompletableFuture completing when the batch write finishes
     */
    CompletableFuture<Void> saveAll(@NotNull Collection<LifeProfile> profiles);

    /**
     * Queries UUIDs of all players who are currently banned (banned_until &gt; nowEpochSecond).
     *
     * @param nowEpochSecond current timestamp in epoch seconds
     * @return CompletableFuture completing with list of banned player UUIDs
     */
    default CompletableFuture<List<UUID>> getBannedPlayerUuids(long nowEpochSecond) {
        return CompletableFuture.completedFuture(java.util.Collections.emptyList());
    }

    /**
     * Finds a player's UUID by their last known username (case-insensitive).
     *
     * @param lastName username to search for
     * @return CompletableFuture completing with optional UUID
     */
    default CompletableFuture<Optional<UUID>> findUuidByLastName(@NotNull String lastName) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    /**
     * Loads all active dead player entries (lives == 0, banned_until &gt; now, last_name not null).
     *
     * @param nowEpochSecond current timestamp in epoch seconds
     * @return CompletableFuture completing with list of DeadEntry objects
     */
    default CompletableFuture<List<io.github.lifesmp.core.domain.model.DeadEntry>> loadActiveDeadEntries(long nowEpochSecond) {
        return CompletableFuture.completedFuture(java.util.Collections.emptyList());
    }
}
