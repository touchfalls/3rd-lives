// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.ports;

import io.github.lifesmp.core.domain.model.Bounty;
import io.github.lifesmp.core.domain.model.KillStreak;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Persistent storage of bounties, wanted players and hunter cooldowns.
 */
public interface BountyRepository {

    /** Everything stored, loaded once at startup. Cooldown keys are {@code hunter + ":" + target}. */
    record Snapshot(
            @NotNull List<Bounty> bounties,
            @NotNull Map<UUID, Long> wantedSeconds,
            @NotNull Map<String, Instant> cooldowns,
            @NotNull Map<UUID, KillStreak> streaks
    ) {}

    CompletableFuture<Snapshot> loadAll();

    CompletableFuture<Void> saveBounty(@NotNull Bounty bounty);

    CompletableFuture<Void> deleteBounty(@NotNull UUID target);

    CompletableFuture<Void> saveWanted(@NotNull UUID player, long survivedSeconds);

    CompletableFuture<Void> deleteWanted(@NotNull UUID player);

    CompletableFuture<Void> saveCooldown(@NotNull UUID hunter, @NotNull UUID target, @NotNull Instant until);

    CompletableFuture<Void> saveStreak(@NotNull UUID player, @NotNull KillStreak streak);

    CompletableFuture<Void> deleteStreak(@NotNull UUID player);

    /** Completes when every write submitted so far has finished. */
    CompletableFuture<Void> flush();
}
