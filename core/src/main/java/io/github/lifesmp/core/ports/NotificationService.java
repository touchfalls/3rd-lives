// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.ports;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Port interface for delivering notifications, action bar alerts, broadcasts,
 * kick enforcement, and visual name updates across platform adapters.
 */
public interface NotificationService {

    /**
     * Sends a chat message to a specific player.
     *
     * @param targetUuid recipient player UUID
     * @param rawMessage formatted message string
     */
    void sendMessage(@NotNull UUID targetUuid, @NotNull String rawMessage);

    /**
     * Displays an action bar message to a specific player.
     *
     * @param targetUuid recipient player UUID
     * @param rawMessage formatted message string
     */
    void sendActionBar(@NotNull UUID targetUuid, @NotNull String rawMessage);

    /**
     * Broadcasts a message to all online players.
     *
     * @param rawMessage formatted message string
     */
    void broadcast(@NotNull String rawMessage);

    /**
     * Disconnects/kicks a player with a specified reason.
     *
     * @param targetUuid player UUID to disconnect
     * @param reason     kick message
     */
    void kickPlayer(@NotNull UUID targetUuid, @NotNull String reason);

    /**
     * Refreshes a player's display name, tab list, and scoreboard team styling
     * according to their current life count.
     *
     * @param playerUuid target player UUID
     * @param lives      current remaining lives
     */
    void updateDisplayNameAndTab(@NotNull UUID playerUuid, int lives);
}
