// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.paper.listener;

import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.service.LifeEngine;
import io.github.lifesmp.paper.util.AttackerAttributor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Event listener handling player deaths, arena deaths, and life deduction.
 */
public final class PlayerDeathListener implements Listener {

    private final LifeEngine lifeEngine;
    private final AttackerAttributor attackerAttributor;
    private final Supplier<LifeConfig> configSupplier;

    public PlayerDeathListener(
            @NotNull LifeEngine lifeEngine,
            @NotNull AttackerAttributor attackerAttributor,
            @NotNull Supplier<LifeConfig> configSupplier
    ) {
        this.lifeEngine = Objects.requireNonNull(lifeEngine, "lifeEngine cannot be null");
        this.attackerAttributor = Objects.requireNonNull(attackerAttributor, "attackerAttributor cannot be null");
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        Location location = player.getLocation();
        Instant now = Instant.now();
        LifeConfig config = configSupplier.get();

        // Resolve killer UUID
        UUID killerUuid = null;
        Player directKiller = player.getKiller();
        if (directKiller != null) {
            killerUuid = directKiller.getUniqueId();
        } else {
            killerUuid = attackerAttributor.getRecentAttacker(player.getUniqueId());
        }

        lifeEngine.handlePlayerDeath(
                player.getUniqueId(),
                player.getName(),
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                killerUuid,
                now,
                config
        );

        attackerAttributor.clearRecentCombat(player.getUniqueId());
    }
}
