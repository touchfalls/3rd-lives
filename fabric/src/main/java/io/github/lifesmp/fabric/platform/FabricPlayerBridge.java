// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) <FILL IN: year> <FILL IN: author>

package io.github.lifesmp.fabric.platform;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Platform bridge for dispatching text messages, action bars, kicks,
 * and visual updates to Fabric ServerPlayerEntity instances.
 */
public interface FabricPlayerBridge {

    /**
     * Sends a chat or action bar message to a player.
     *
     * @param playerUuid target player UUID
     * @param rawText    plain or formatted text content
     * @param actionBar  true if sent to action bar, false if chat
     */
    void sendMessage(@NotNull UUID playerUuid, @NotNull String rawText, boolean actionBar);

    /**
     * Broadcasts a message to all online players.
     *
     * @param rawText text to broadcast
     */
    void broadcast(@NotNull String rawText);

    /**
     * Kicks a player with a specified reason.
     *
     * @param playerUuid target player UUID
     * @param reason     kick reason text
     */
    void kickPlayer(@NotNull UUID playerUuid, @NotNull String reason);

    /**
     * Updates display name and tab list for a player based on their life count.
     *
     * @param playerUuid target player UUID
     * @param lives      current lives count
     */
    void updateVisuals(@NotNull UUID playerUuid, int lives);
}
