// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.paper.listener;

import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.model.LifeProfile;
import io.github.lifesmp.core.domain.service.LifeEngine;
import io.github.lifesmp.paper.scoreboard.PaperScoreboardManager;
import io.github.lifesmp.paper.util.AttackerAttributor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Event listener managing player connection authentication, death ban enforcement,
 * visual updates on join, and clean disconnection handling.
 */
public final class PlayerJoinQuitListener implements Listener {

    private final LifeEngine lifeEngine;
    private final PaperScoreboardManager scoreboardManager;
    private final AttackerAttributor attackerAttributor;
    private final Supplier<LifeConfig> configSupplier;

    public PlayerJoinQuitListener(
            @NotNull LifeEngine lifeEngine,
            @NotNull PaperScoreboardManager scoreboardManager,
            @NotNull AttackerAttributor attackerAttributor,
            @NotNull Supplier<LifeConfig> configSupplier
    ) {
        this.lifeEngine = Objects.requireNonNull(lifeEngine, "lifeEngine cannot be null");
        this.scoreboardManager = Objects.requireNonNull(scoreboardManager, "scoreboardManager cannot be null");
        this.attackerAttributor = Objects.requireNonNull(attackerAttributor, "attackerAttributor cannot be null");
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        Instant now = Instant.now();
        LifeConfig config = configSupplier.get();

        LifeEngine.JoinEvaluation evaluation = lifeEngine.handlePlayerJoin(uuid, event.getName(), now, config).join();
        if (!evaluation.allowed()) {
            String reasonStr = evaluation.kickReason() != null ? evaluation.kickReason() : config.getRawMessage("death.ban-kick");
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    MiniMessage.miniMessage().deserialize(reasonStr)
            );
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        LifeProfile profile = lifeEngine.getProfileCache()
                .getCachedProfile(player.getUniqueId())
                .orElse(null);

        int lives = profile != null ? profile.getLives() : configSupplier.get().getMaxLives();
        scoreboardManager.updatePlayerVisuals(player, lives);
        if (lifeEngine.getNotificationService() instanceof io.github.lifesmp.paper.platform.PaperNotificationService paperNotif) {
            paperNotif.deliverPendingMessages(player);
        }

        if (lifeEngine.getDeadTabService() != null) {
            lifeEngine.getDeadTabService().onPlayerJoin(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Instant now = Instant.now();

        // Safe disconnect: combat logging carries no death penalty
        lifeEngine.handlePlayerQuit(uuid, now);
        scoreboardManager.removeFromAllTeams(player.getName());
        attackerAttributor.clearRecentCombat(uuid);
    }
}
