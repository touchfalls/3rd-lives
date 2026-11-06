// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.domain.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * A bounty placed by one player on another. The stake is held until the bounty is claimed,
 * survived, bought out or cancelled.
 *
 * @param target          player with the price on their head
 * @param issuer          player who placed the bounty
 * @param issuerName      issuer name at placement time, shown in /bounty info
 * @param items           stake items serialized by the platform, or null when no items were staked
 * @param xpLevels        staked XP levels, after tax
 * @param lives           staked lives
 * @param buyoutLevels    XP levels the target pays to buy the bounty out
 * @param survivedSeconds online seconds the target survived while the bounty was active
 * @param createdAt       placement time
 */
public record Bounty(
        @NotNull UUID target,
        @NotNull UUID issuer,
        @NotNull String issuerName,
        byte @Nullable [] items,
        int xpLevels,
        int lives,
        int buyoutLevels,
        long survivedSeconds,
        @NotNull Instant createdAt
) {
    public Bounty withSurvivedSeconds(long seconds) {
        return new Bounty(target, issuer, issuerName, items, xpLevels, lives, buyoutLevels, seconds, createdAt);
    }
}
