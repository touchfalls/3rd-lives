// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.ports;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Platform port that hands bounty stakes back to players. Called on the server thread.
 */
public interface RewardGateway {

    /**
     * Gives items and XP to an online player. Items that do not fit are dropped at the player's feet.
     *
     * @param player       recipient
     * @param items        items serialized by the same platform, or null
     * @param itemPercent  share of every stack to give, 100 for all of it
     * @param xpLevels     XP levels to add
     * @return false if the player is offline and nothing was given
     */
    boolean give(@NotNull UUID player, byte @Nullable [] items, int itemPercent, int xpLevels);
}
