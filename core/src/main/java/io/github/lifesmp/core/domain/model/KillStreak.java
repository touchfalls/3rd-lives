// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.domain.model;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * PvP kills of a player since their last death.
 *
 * @param kills     kills in the streak
 * @param startedAt time of the first kill; the streak starts over a configured time after it
 */
public record KillStreak(int kills, @NotNull Instant startedAt) {

    public boolean isExpired(@NotNull Instant now, long resetSeconds) {
        return !now.isBefore(startedAt.plusSeconds(resetSeconds));
    }
}
