// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.paper.listener;

import io.github.lifesmp.core.domain.service.RegenerationService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/**
 * Event listener tracking player movements to update AFK activity state.
 */
public final class PlayerMoveListener implements Listener {

    private final RegenerationService regenerationService;

    public PlayerMoveListener(@NotNull RegenerationService regenerationService) {
        this.regenerationService = Objects.requireNonNull(regenerationService, "regenerationService cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) {
            return;
        }

        Player player = event.getPlayer();
        Location to = event.getTo();
        Instant now = Instant.now();

        regenerationService.updatePlayerPosition(
                player.getUniqueId(),
                to.getWorld().getName(),
                to.getX(),
                to.getY(),
                to.getZ(),
                now
        );
    }
}
